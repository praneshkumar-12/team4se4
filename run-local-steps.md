# What `run-local.sh` actually does

`run-local.sh` brings up the whole trading platform with Kafka running on a
remote VM and everything else (Postgres, trade-api, auth-service, executor)
running locally. This document walks through exactly what each command does,
in the order it does it. See `runbook.md` for the older, fully-manual
docker-compose walkthrough this script now replaces for day-to-day local dev.

## Commands

```bash
./run-local.sh              # same as `start`
./run-local.sh start        # bring up everything (idempotent, safe to re-run)
./run-local.sh stop         # stop the local processes (trade-api, auth-service, executor)
./run-local.sh kafka-stop   # stop the Kafka container on the remote VM
./run-local.sh status       # print current status of every component
./run-local.sh restart      # stop local services, then run `start` again
```

## `start` — step by step

### 0. One-time setup: `run-local.env`
Secrets and machine-specific values are not in the script. Copy the template
and fill in your own values (the file is git-ignored — never commit it):

```bash
cp run-local.env.example run-local.env
```

Required: `VM_HOST`, `VM_USER`, `VM_PASSWORD`, `DB_PASSWORD` (your local
Postgres password), `JWT_SECRET` (32+ chars, e.g. `openssl rand -hex 32`),
`FAUXNANCE_API_KEY`. Optional overrides (`JAVA_HOME`, `DB_USER`, `DB_PORT`,
`KAFKA_VM_PORT`, `RUN_AUTH_SERVICE`, …) are listed at the bottom of the
template. Format is plain `KEY=VALUE`; values are read literally, so `!`, `$`
and spaces in a password are fine (no shell quoting needed).

### 1. Validate configuration
Loads `run-local.env`, then checks that every required value is set and not
left as a `CHANGE_ME…` placeholder, and that `KAFKA_VM_PORT` is a number in
`8081–8100`. A missing or unknown key fails with a message naming the key
(never the value).

### 2. Resolve Java 21
- If the `JAVA_HOME` set at the top of the script doesn't point at a valid
  `java`/`javac`, the script searches common Windows JDK install
  directories (Eclipse Adoptium, Java, Microsoft, Zulu, AdoptOpenJDK,
  BellSoft) for any Java 21 install and uses that instead.
- If nothing is found, it tries `choco install temurin21 -y` (Windows only)
  and searches again.
- Once resolved, `JAVA_HOME` is exported and prepended to `PATH`, and
  `java -version` / `javac -version` are checked to confirm they report
  major version `21`.

### 3. Check local dependencies
Confirms these are on `PATH`: `git`, `mvn`, `ssh`, `scp`, `curl`, `psql`,
and — since `RUN_AUTH_SERVICE=true` — `node`, `npm`, `liquibase`. Also
requires `sshpass` when `VM_PASSWORD` is set (password-based SSH).

If `sshpass` or `liquibase` are missing on Windows, the script attempts to
auto-install them (`winget` for `sshpass`, `choco` for `liquibase`) before
giving up with an install hint. Also verifies Maven is `3.9+` and Node.js
is `20+`.

### 4. Set up SSH
Builds the `ssh`/`scp` command wrappers used for every remote step —
wrapped in `sshpass -e` when `VM_PASSWORD` is set, so the password doesn't
need to be typed interactively. Remote commands are piped over SSH's stdin
rather than passed as a quoted argument, to avoid Windows/sshpass argument
re-quoting issues with complex commands.

### 5. Verify the remote VM
- Confirms SSH connectivity to `VM_USER@VM_HOST:VM_SSH_PORT`.
- Confirms Docker is available on the VM (`docker info`).
- Detects whether the VM has `docker compose` (v2 plugin) or `docker-compose`
  (v1 binary) and remembers which one to use for every later compose call.

### 6. Prepare the remote Kafka workspace
- Writes a local override file
  (`.run-local/docker-compose.kafka-remote.override.yml`) that points
  Kafka's advertised listener at the VM's public host/port
  (`VM_HOST:KAFKA_VM_PORT`) so it's reachable from this machine.
- Writes a local `.env` file (`.run-local/docker-compose.kafka-remote.env`)
  containing `JWT_SECRET`. This is needed because `docker-compose.yml`
  declares `JWT_SECRET` as a hard-required variable on the `trade-api` and
  `auth-service` blocks, and Compose validates env interpolation for the
  *whole* file even when only the `kafka` service is targeted.
- Creates `REMOTE_APP_DIR` (`/opt/team4se4-kafka` by default) on the VM. If
  the directory can't be created directly (e.g. `/opt` is root-owned), it
  falls back to `sudo mkdir` + `sudo chown` so the VM user owns it.
- Copies `docker-compose.yml`, `scripts/kafka-init.sh`, the override file,
  and the `.env` file up to that directory, and makes `kafka-init.sh`
  executable.

### 7. Start Kafka on the VM
- Runs `docker compose up -d kafka` (or `docker-compose`, per step 5) on
  the VM.
- Polls (up to 40 × 3s) until the broker responds to
  `kafka-broker-api-versions.sh`. On failure, prints the container status
  and last 200 log lines before failing.
- Runs `scripts/kafka-init.sh` on the VM to create the required topics
  (`orders`, `orders.DLT`, `trade-events`, `trade-events.DLT`,
  `market-data`, `market-data.DLT`) — safe to re-run.
- Confirms the Kafka port (`VM_HOST:KAFKA_VM_PORT`) is reachable from this
  machine over TCP (up to 20 × 2s).

### 8. Validate the local database
Connects to local Postgres as `DB_USER`/`DB_PASSWORD` and creates `DB_NAME`
if it doesn't already exist.

### 9. Build everything
In order:
1. `sprint-05-domain-engine` → `mvn install` (installed to the local `.m2`
   repo so the other modules can depend on it)
2. `sprint-06-trade-api` → `mvn clean package`
3. `executor` → `mvn clean package`
4. `sprint-08-auth-service` → `npm ci` then `npm run build` (TypeScript
   compile)

Tests are skipped in all Maven builds (`-DskipTests`).

### 10. Stop any previously-running local services
Reads `.run-local/pids.env` and kills any trade-api / auth-service /
executor process left running from a previous `start`.

### 11. Start trade-api
Launches `mvn spring-boot:run` in the background (`nohup`, logs to
`logs/trade-api.log`) with DB, JWT, and Kafka connection details injected
as environment variables — pointed at the **remote** Kafka
(`VM_HOST:KAFKA_VM_PORT`). Waits (up to 90 × 2s) for
`GET /actuator/health` to report `"status":"UP"`. On failure, prints the
last 120 log lines before failing.

### 12. Run auth-service's Liquibase migration
Runs `liquibase update` against the local Postgres database using
auth-service's changelog. Two Windows-specific fixes are baked into this
step:
- `PATH` is temporarily reordered to put `C:\Windows\System32` first, so
  `liquibase.bat`'s internal call to Windows' `find /i` isn't shadowed by
  Git Bash's GNU `find` (which doesn't understand `/i` and breaks the
  launcher).
- `--changelog-file` is passed as a path relative to `--search-path`
  (rather than a full absolute path), which newer Liquibase versions
  require when both flags are given together.

### 13. Start auth-service
Launches `npm run start` in the background (logs to
`logs/auth-service.log`) with DB/JWT env vars injected. Waits (up to
60 × 2s) for `GET /health` to report `"status":"up"`.

### 14. Start the executor
Launches `mvn exec:java` running `org.leap.executor.Main` in the background
(logs to `logs/executor.log`), pointed at the remote Kafka and the
Fauxnance market-data API. Since it has no HTTP health endpoint, the script
just waits 8s and checks the process is still alive.

### 15. Print the summary
Queries and prints the live status of every component (Kafka on the VM,
local DB, trade-api, auth-service, executor) plus the follow-up commands.

## `stop`
Kills any locally-running trade-api / auth-service / executor process
(tracked via `.run-local/pids.env`), gracefully (`SIGTERM`, then `SIGKILL`
after a 2s grace period if still alive). Does **not** touch remote Kafka.

## `kafka-stop`
Validates config, resolves Java, checks dependencies, re-establishes the
SSH connection, and runs `docker compose stop kafka` on the VM.

## `status`
Validates config and resolves Java, then prints the same summary as the
end of `start` (steps 15) without doing any building or starting.

## `restart`
Runs `stop` followed by `start`.

## Where things run

| Component  | Where           |
|------------|-----------------|
| Kafka      | remote VM (Docker) |
| Postgres   | local machine (must already be running) |
| trade-api  | local machine (Maven/Spring Boot process) |
| auth-service | local machine (Node process) |
| executor   | local machine (Maven/Java process) |

## State files

All under `.run-local/` (git-ignored, created on demand):
- `pids.env` — PIDs of the locally-started processes, used by `stop`/`restart`
- `docker-compose.kafka-remote.override.yml` — Kafka listener/port override, copied to the VM
- `docker-compose.kafka-remote.env` — `.env` copied to the VM alongside `docker-compose.yml`

Logs go to `logs/trade-api.log`, `logs/auth-service.log`, `logs/executor.log`.
