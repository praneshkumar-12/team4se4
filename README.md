# Sprint 6 — Trade REST API

The platform's front door. One HTTP service that accepts an order, verifies the
caller's token, applies the Sprint 5 domain rules, writes the result to the
Sprint 3 schema, and answers in the shape `contracts/trade-api.yaml` fixes.

It decides nothing about whether a trade is allowed and it prices nothing. It
owns transport and persistence only.

## Build

The Sprint 5 domain engine is a separate Maven module, resolved from the local
repository by the coordinates in `manifest.env`. On a clean machine:

```bash
(cd ../sprint-05-domain-engine && mvn install)   # publishes org.leap:sprint-05-domain
(cd ../sprint-06-trade-api    && mvn clean verify)
```

Re-run the first line whenever the domain changes.

Requires Java 21 and Maven 3.9+. `JWT_SECRET` must be set in the environment;
it has no default and appears in no committed file.

## Run

```bash
cp ../.env.example ../.env      # then set JWT_SECRET
docker compose -f ../docker-compose.yml up --build
```

The service listens on `:8085` and answers `GET /actuator/health`. On startup
Liquibase builds the database: against an empty Postgres it creates the full
Sprint 3 schema (tables, constraints, trigger functions, indexes), adds the
Sprint 6 `orders.public_id` column, and — when `LIQUIBASE_CONTEXTS` includes
`demo` — loads the walkthrough seed. Every changeset is precondition-guarded, so
running against a database the Sprint 3 scripts already built is a no-op.

## Layout

| Package | Role | May not |
|---|---|---|
| `com.leap.tradeapi.controller` | HTTP, DTOs, validation, status codes | hold SQL, open a transaction, carry a business rule, import the mapper package |
| `com.leap.tradeapi.service` | the domain package, mappers, one transaction | take a servlet type, request object or status code |
| `com.leap.tradeapi.mapper` | parameterised SQL, result mapping | decide anything, reach back into HTTP |
| `com.leap.tradeapi.auth` | JWT verification, caller identity | — |
| `org.leap.*` (Sprint 5 dependency) | every trading rule | carry any HTTP, Spring or MyBatis type |

`LayeringRulesTest` (ArchUnit) fails the build if any of these boundaries break.

## The six operations

| Verb | Path | |
|---|---|---|
| POST | `/api/v1/orders` | place an order (synchronous fill in Sprint 6) |
| DELETE | `/api/v1/orders/{id}` | cancel a `NEW` order, guarded transition |
| GET | `/api/v1/accounts/{id}` | account details |
| GET | `/api/v1/accounts/{id}/balance` | cash balance |
| GET | `/api/v1/accounts/{id}/positions` | net holdings |
| GET | `/api/v1/accounts/{id}/orders` | order history, newest first |

Every failure leaves as `{"errorCode": "...", "message": "..."}` — mapped in
`GlobalExceptionHandler`, keyed by exception type (`ORD-409` is returned at both
404 and 409, as the contract's DELETE examples fix).

## Notes

- `AccountResponse.accountId` is the string business reference
  (`ACCOUNTS.account_reference`); every other endpoint's `accountId` is the
  numeric `ACCOUNTS.id`.
- Optimistic locking: the balance update names the version the row held when it
  was read and increments it. Zero rows affected is refused with `ORD-409`.
- Idempotency (rule 8) is enforced by the unique constraint on
  `orders.idempotency_key`, caught and returned as `ORD-409`.
- `TestTokens` (`src/test/java`) is a team-owned test fixture that follows
  `contracts/auth-api.yaml`; it is scoped to this module's own test suite and
  never deployed. Sprint 8 (`sprint-08-auth-service/`) is the real, running
  auth service the deployed platform authenticates against - adopted as a
  configuration change only (`JWT_SECRET`/`JWT_ISSUER`, both already read
  from the environment here), no Java file in this module changed. See
  `sprint-08-auth-service/RUNBOOK.md` for how the two fit together.
- Tests run without a container: unit (Mockito), MyBatis slices and a full
  `@SpringBootTest` all use H2 in place of Postgres.

## Full Local Stack Script (remote Kafka VM)

Use `run-local.sh` at the repo root to start this branch end-to-end with:

- Kafka running in Docker on a remote Linux VM over SSH.
- Trade API, Executor, and (optionally) Auth Service running on your local machine.
- PostgreSQL running locally on your machine (not Dockerized by this script).

### Architecture

```text
Local machine:
  - sprint-06-trade-api (Spring Boot)
  - executor (Java process)
  - sprint-08-auth-service (Node/Nest, optional)
  - PostgreSQL

Remote Linux VM:
  - Docker + Docker Compose
  - Kafka only
```

### Configure values at top of script

Edit these near the top of `run-local.sh` before first run:

- `JAVA_HOME` (single hardcoded JDK path, must point to Java 21)
- `VM_HOST`, `VM_USER`, `VM_PASSWORD`, `VM_SSH_PORT`
- `KAFKA_VM_PORT` (must stay in `8081-8100`)
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`, `JWT_ISSUER`, `LIQUIBASE_CONTEXTS`
- `FAUXNANCE_BASE_URL`, `FAUXNANCE_API_KEY`, `POLL_INTERVAL_SECONDS`
- `RUN_AUTH_SERVICE` (`true` or `false`)

Kafka bootstrap for local services is automatically set to:

```text
KAFKA_BOOTSTRAP_SERVERS=<VM_HOST>:<KAFKA_VM_PORT>
```

### Fresh machine requirements

The script validates dependencies and stops early with a clear message if any
required tool is missing.

- Java 21 JDK
- Maven 3.9+
- Git
- SSH + SCP
- sshpass (for password-based SSH automation)
- PostgreSQL client (`psql`) and a reachable local PostgreSQL server
- curl
- If `RUN_AUTH_SERVICE=true`: Node.js 20+, npm, Liquibase CLI

### Run

```bash
./run-local.sh
```

This flow performs:

1. Local dependency and Java validation
2. SSH and remote Docker validation
3. Copies `docker-compose.yml`, `scripts/kafka-init.sh`, and a generated Kafka override file to the VM
4. Starts Kafka remotely with Docker Compose
5. Waits for broker readiness and creates all required topics (including DLTs) via `scripts/kafka-init.sh`
6. Verifies Kafka reachability from local machine on `<VM_HOST>:<KAFKA_VM_PORT>`
7. Validates local PostgreSQL and creates `DB_NAME` if missing
8. Builds Maven modules in dependency order:
   - `sprint-05-domain-engine`
   - `sprint-06-trade-api`
   - `executor`
9. Builds auth service when enabled
10. Starts local services with logs and PID tracking

### Stop / status / restart

```bash
./run-local.sh stop
./run-local.sh kafka-stop
./run-local.sh status
./run-local.sh restart
```

- Local logs are written under `logs/`.
- Process IDs are tracked in `.run-local/pids.env`.

### VM-exposed Kafka ports

This setup is constrained to VM ports `8081-8100`. Configure `KAFKA_VM_PORT`
within that range. The script rewrites Kafka advertised listener host/port for
the remote deployment so local services can connect through that reachable VM
endpoint.

### Troubleshooting

- `Java check failed`: set `JAVA_HOME` to a real Java 21 JDK path.
- `Missing dependency: <tool>`: install the named tool and rerun.
- `SSH connection failed`: verify VM host/user/password/port and network reachability.
- `Remote Docker unavailable`: ensure Docker engine is running on the VM and user has access.
- `Kafka failed to start`: check remote container logs printed by the script.
- `Kafka port unreachable`: confirm VM firewall/network allows chosen `KAFKA_VM_PORT`.
- `Database unavailable`: start local PostgreSQL and verify `DB_*` values.
- `Service failed to start`: inspect `logs/trade-api.log`, `logs/auth-service.log`, or `logs/executor.log`.
