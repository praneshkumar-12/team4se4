# Configuration map: every env file, Dockerfile, compose file and config file

One place to answer "which file do I edit to change X, and does that affect
my local run, my Docker run, or both." Everything below was read from the
actual files in this repo and, where a port or connection is claimed, tested
against a real running stack (see "Verified" notes) — nothing here is
guessed.

## The one-paragraph mental model

There are **two runtimes** in this repo: services that run **in Docker**
(`docker compose up`, reading `docker-compose.yml`) and the **executor**,
which is never containerized and is always run by hand, reading environment
variables you `export` yourself. Every other service can run either way.
`.env` files only feed `docker-compose.yml` and, for the auth service, a
standalone `npm run start:dev`. Application code (`application.yml`,
NestJS's `ConfigService`, the executor's `System.getenv`) never reads a
`.env` file directly — Spring Boot and the Node process see plain
environment variables, however those got set (by Docker Compose, or by you
in your shell). **Ports come in pairs**: a fixed *container-internal* port
(baked into each app's own config, never changes) and a *host-published*
port (the left side of `"host:container"` in `docker-compose.yml`, the only
part you'd ever change for a local conflict).

## Every `.env` file

| File | Committed? | Read by | Effect |
|---|---|---|---|
| `.env.example` (repo root) | Yes | Nobody directly — it's the template | Copy to `.env` and fill in the values marked `replace`. Documents every variable the platform uses. |
| `.env` (repo root) | **No** (git-ignored) | `docker-compose.yml` only, via `${VAR}` / `${VAR:-default}` substitution | This is the file that actually matters for `docker compose up`. Editing it and re-running `docker compose up` changes every container's environment on next recreate — **no code change, no rebuild needed** for a plain env value (a rebuild is only needed if you changed source code). |
| `sprint-08-auth-service/.env.example` | Yes | Nobody directly — template | For running the auth service **standalone**, outside Docker (`npm run start:dev` from inside `sprint-08-auth-service/`). |
| `sprint-08-auth-service/.env` | **No** (git-ignored) | Only the auth service's own `npm run start:dev` process (NestJS's `ConfigModule.forRoot()` loads `.env` from the process's current working directory) | **Not read by Docker at all.** When `auth-service` runs as a container, its environment comes entirely from `docker-compose.yml`'s `environment:` block, which itself is fed by the *root* `.env` — this file plays no part in a Docker run. |

**The two `.env.example` files intentionally hold different `JWT_SECRET`
placeholder values.** They're for different runtimes. If you run `trade-api`
in Docker (root `.env`'s secret) and `auth-service` standalone
(`sprint-08-auth-service/.env`'s secret) at the same time, their secrets
won't match and every token will fail signature verification. Either run
both the same way (both in Docker, or both standalone against the same
exported `JWT_SECRET`), or manually copy one secret into the other file.

**`sprint-06-trade-api` and `executor` have no `.env` file of their own.**
Spring Boot and the executor's plain `System.getenv()` both read real
process environment variables — when running locally (not in Docker), you
`export` them yourself (see "Running locally" below); no `.env` loader is
involved for either.

## Every Dockerfile

| File | Builds | Exposes (container-internal port) | Only affects |
|---|---|---|---|
| `sprint-06-trade-api/Dockerfile` | The Trade REST API image (multi-stage: `maven:3.9-eclipse-temurin-21` build stage, `eclipse-temurin:21-jre` runtime stage). Build context is the **repo root** (`context: .` in `docker-compose.yml`), because it also builds the sibling `sprint-05-domain-engine` Maven module first. | `8085` | Docker runs only. A local `mvn spring-boot:run` never touches this file. |
| `sprint-08-auth-service/Dockerfile` | The auth service image (multi-stage: `node:20-alpine` build stage, `node:20-alpine` runtime stage, runs as the non-root `node` user). Build context is `sprint-08-auth-service/` itself (not the repo root — this service has no sibling module to pull in). | `3000` | Docker runs only. A local `npm run start:dev` never touches this file. |

There is **no Dockerfile for `executor`** and **no Dockerfile for
`sprint-05-domain-engine`** — neither is ever containerized (see "The
executor is special," below).

## `docker-compose.yml` — the one orchestrator

There is exactly one compose file (no `docker-compose.override.yml`, no
per-environment variants). It defines six services: `postgres`, `kafka`,
`trade-api`, `auth-service-migrate`, `auth-service`, `sonarqube`.

### Port map (verified against a running stack, not just read from the file)

| Service | Container-internal port | Host-published port | Reach it from your machine at |
|---|---|---|---|
| `postgres` | `5432` (Postgres's own default — fixed) | `8091` | `localhost:8091` |
| `kafka` | `9092` (the `PLAINTEXT_HOST` listener) | `8090` | `localhost:8090` |
| `trade-api` | `8085` (`server.port` in `application.yml`) | `8085` | `localhost:8085` |
| `auth-service` | `3000` (`PORT` env var; the port the Sprint 8 requirements repo's `infra/README.md` reserves for it — that file isn't part of this repo, only referenced by it) | `3000` | `localhost:3000` |
| `sonarqube` | `9000` | `9000` | `localhost:9000` |
| `auth-service-migrate` | — (a one-shot job, not a listening service; it runs `liquibase update` and exits) | — | — |

**Inside** the Docker network, containers reach each other by *service
name* and *container-internal* port, never the host-published one:
`trade-api` connects to Postgres at `postgres:5432`, `auth-service`
connects to Postgres at `postgres:5432` too (`DB_HOST=postgres,
DB_PORT=5432` in its `environment:` block), and both read Kafka at
`kafka:29092` (a *third* Kafka listener, `PLAINTEXT`, used only
container-to-container — not the same port as the host-published one).

**To change a port a browser or `curl` on your machine hits:** change only
the *first* number in that service's `ports:` entry in `docker-compose.yml`
(e.g. `"3000:3000"` → `"3001:3000"` makes it reachable at
`localhost:3001`). Never change the second number unless you also change
that app's own internal config (`server.port` for trade-api, `PORT` for
auth-service) — they have to match.

### Where each container's environment variables come from

| Service | Env vars | Source |
|---|---|---|
| `postgres` | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Root `.env`, with defaults in `docker-compose.yml` if unset |
| `kafka` | All `KAFKA_*` listener/cluster config | Hardcoded in `docker-compose.yml` — not from `.env` at all |
| `trade-api` | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_ISSUER`, `LIQUIBASE_CONTEXTS`, `KAFKA_BOOTSTRAP_SERVERS` | Root `.env` (`JWT_SECRET` has **no default** — `docker compose up` refuses to start `trade-api` if it's unset) |
| `auth-service-migrate` | `LIQUIBASE_COMMAND_URL/USERNAME/PASSWORD/CHANGELOG_FILE`, `LIQUIBASE_SEARCH_PATH` | Root `.env` for the DB credentials; the rest hardcoded |
| `auth-service` | `PORT`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_ISSUER` | Root `.env` (`JWT_SECRET` has **no default** here either) |
| `sonarqube` | none from `.env` | N/A |

### Startup order (`depends_on`, verified end to end)

```
postgres, kafka  (both must be "healthy")
      │
      ▼
   trade-api  (builds the Sprint 3 schema itself, via its own
   │  │        embedded Liquibase run, before it reports healthy)
   │  ▼
   │  auth-service-migrate  (applies sprint-08-auth-service's own
   │  │                      Liquibase changelog, then exits)
   │  ▼
   │  auth-service
   ▼
(sonarqube has no dependencies — starts independently)
```

`scripts/kafka-init.sh` is a manual step, **not** run automatically by
compose: after `kafka` is up, run it once before `trade-api` starts, or
`trade-api`'s Kafka publishes fail against topics that don't exist
(`KAFKA_AUTO_CREATE_TOPICS_ENABLE` is deliberately `false`).

## `application.yml` / `application-test.yml` (`sprint-06-trade-api`)

| File | Used when | What it does |
|---|---|---|
| `src/main/resources/application.yml` | Every real run of trade-api — `mvn spring-boot:run` locally **and** the packaged jar inside Docker (it's baked into the jar at build time) | Doesn't hold values itself — it's a map from Spring's config keys (`jwt.secret`, `server.port`, `spring.datasource.url`, ...) to environment variable names (`${JWT_SECRET}`, `${SERVER_PORT:8085}`, `${DB_URL:...}`). Editing *this file* changes behaviour in **both** local and Docker runs, because it's compiled into the artifact either way. Editing an *environment variable* (root `.env`, or your shell's `export`) changes behaviour **without touching this file**. |
| `src/test/resources/application-test.yml` | Only `mvn test` (Spring's `test` profile) | Fully self-contained: H2 in-memory database, Liquibase turned off, schema/seed loaded by `spring.sql.init` instead, and a hardcoded `jwt.secret` (`test-secret-value-...`). Completely isolated from every other file on this page — changing your `.env` or the port mappings has zero effect on `mvn test`. |

**The default-port trap, verified on this machine:** `application.yml`
falls back to `jdbc:postgresql://localhost:5432/trade_db` when `DB_URL`
isn't set. `docker-compose.yml` publishes Postgres on **8091**, not 5432.
If you run trade-api locally (`mvn spring-boot:run`) without exporting
`DB_URL`, it will *not* fail loudly — it will silently try to connect to
whatever else is listening on port 5432 on your machine. On the machine
this was written on, **something else actually was** (a pre-existing local
Postgres unrelated to this project answered on 5432). Always export
`DB_URL=jdbc:postgresql://localhost:8091/trade_db` when running trade-api
locally against the Dockerized database.

## The auth service has no `application.yml` equivalent file

NestJS doesn't use one. `sprint-08-auth-service/src/main.ts` and its
modules read `ConfigService.get(...)` calls directly — the *names* of the
environment variables it expects (`PORT`, `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_ISSUER`) live in the source
code (`src/db/pool.provider.ts`, `src/tokens/token.service.ts`,
`src/auth/guards/jwt-auth.guard.ts`), not in a separate config file. The
closest equivalent to `application.yml` here is simply
`sprint-08-auth-service/.env.example`, documented above.

## The auth service's schema: Liquibase, not a config file

`sprint-08-auth-service/db/changelog/db.changelog-master.xml` (+
`changes/*.sql`) defines the `users` and `refresh_tokens` tables, applied
by the `auth-service-migrate` job above. This isn't environment
configuration, but it's another file people go looking for when
`auth-service` won't start against a database it expects tables in — see
`sprint-08-auth-service/README.md`'s "Schema changes" section.

## The executor is special: no Dockerfile, no compose entry, no `.env`

`executor/` (the Sprint 7 Kafka consumer + market-data poller) is **not**
part of `docker-compose.yml` at all and has no `Dockerfile`. It's a plain
Java `main()` (no Spring Boot) that reads every value with
`System.getenv(...)` directly — there is no config file to edit. Run it
locally, in a shell where you've exported:

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:8090   # the HOST-published Kafka port (see port map above)
export DB_URL=jdbc:postgresql://localhost:8091/trade_db   # the HOST-published Postgres port
export DB_USER=postgres
export DB_PASSWORD=postgres_dev_password        # must match your .env's POSTGRES_PASSWORD
export FAUXNANCE_BASE_URL=https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1
export FAUXNANCE_API_KEY=<your key, or leave any placeholder to run without real fills>
export POLL_INTERVAL_SECONDS=60                 # optional, has a built-in default
```

then `mvn -f executor/pom.xml exec:java` (or run the built jar). Postgres
and Kafka still need to be up via `docker compose up -d postgres kafka` (and
`scripts/kafka-init.sh` run once) — only the executor process itself stays
outside Docker.

**A note on `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`:** if you find that
value written down anywhere else in this repo (an older local runbook, a
teammate's notes), it's stale against the current `docker-compose.yml` —
verified on this machine, Kafka's host-published port is **8090**, not
9092. `9092` is the container-internal port number, never reachable
directly from your host.

## Quick answers

**"I just want everything running, no local installs beyond Docker":**

```bash
cp .env.example .env    # fill in JWT_SECRET (32+ chars) and FAUXNANCE_API_KEY
docker compose up -d postgres kafka
scripts/kafka-init.sh
docker compose up -d --build trade-api auth-service-migrate auth-service
```

Everything reachable at the "Reach it from your machine at" column above.
The executor still needs to be started by hand (see above) — it's the one
piece Docker never runs for you.

**"I want to change what port auth-service answers on":** edit
`"3000:3000"` in `docker-compose.yml`'s `auth-service` block — change only
the left `3000`. Also update anything hardcoding `http://localhost:3000`:
`sprint-08-auth-service/integration-test.sh`'s default argument, and the
port-3000 comments in `docker-compose.yml` and
`sprint-08-auth-service/README.md`.

**"I want to run trade-api locally but everything else in Docker":**

```bash
docker compose up -d postgres kafka
scripts/kafka-init.sh
export DB_URL=jdbc:postgresql://localhost:8091/trade_db
export DB_USER=postgres
export DB_PASSWORD=<matches your .env's POSTGRES_PASSWORD>
export JWT_SECRET=<matches your .env's JWT_SECRET>
export KAFKA_BOOTSTRAP_SERVERS=localhost:8090
cd sprint-06-trade-api && mvn spring-boot:run
```

**"Login/token verification fails between two services I'm running
differently (one in Docker, one local)":** their `JWT_SECRET` values don't
match. Every service that verifies or signs a token needs the *same*
`JWT_SECRET` value, however each is run — that's the one variable to check
first whenever authentication fails for no obvious reason.
