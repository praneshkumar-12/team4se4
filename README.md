# Sprint 8 — Auth Service (Team 4)

One NestJS project, rooted in this folder, that owns registration, login,
password hashing and the JWT lifecycle for the Enterprise Trading Platform.
It is the only process that ever sees a credential; every other service
(Sprint 6's Trade REST API among them) verifies a token signature instead of
holding a password. See `contracts/auth-api.yaml` at the repo root for the
binding API contract.

## Build

Node 20+, TypeScript. `npm ci` installs exactly the tree `package-lock.json`
names, so this succeeds on a machine that has never seen the project:

```bash
cd sprint-08-auth-service
npm ci
npm run build
npm test
```

## Run

The service is part of the repo's local orchestration and answers on port
3000, the port `infra/README.md` (Sprint 8 requirements repo) reserves for
it:

```bash
cp ../.env.example ../.env      # then set JWT_SECRET
docker compose -f ../docker-compose.yml up --build auth-service
```

`JWT_SECRET`, the database connection and every value that differs between a
laptop and a container are read from the environment at runtime and appear
in no committed file. Locally, outside Docker: copy `.env.example` in this
folder to `.env` and run `npm run start:dev`.

`docker compose up auth-service` brings up its full dependency chain
automatically: `postgres` → `trade-api` (builds the Sprint 3 schema via
its own Liquibase run) → `auth-service-migrate` (builds `users` and
`refresh_tokens` on top of it) → `auth-service`.

## Schema changes (SEC4-624's "your migration or bootstrap")

This service's schema lives in `db/changelog/db.changelog-master.xml`,
applied with [Liquibase](https://www.liquibase.org/) - the same tool the
Trade REST API has used for its own schema since Sprint 3, but run here as
a one-shot job (`auth-service-migrate` in the root `docker-compose.yml`,
the official `liquibase/liquibase` image) rather than as a library the app
boots with, since this service isn't a JVM process. This is a deliberate
choice over TypeScript code running DDL at startup: one real migration
history (`DATABASECHANGELOG`), one tool, changeset ordering Liquibase
itself enforces rather than an array a developer has to order by hand.

**Adding a new schema change:** add a new `.sql` file under
`db/changelog/changes/`, and a new `<changeSet>` in
`db.changelog-master.xml` referencing it, precondition-guarded the same
way the existing two are. Never edit an already-applied changeset in
place - Liquibase checksums each one and will refuse to proceed if a
previously-run changeset's content has changed underneath it.

**Running it by hand**, against whatever `docker compose up` already
started:

```bash
docker compose run --rm auth-service-migrate update    # apply
docker compose run --rm auth-service-migrate status     # see what's pending
```

## Integration test: the Trade REST API needs no code change (SEC4-629)

`auth-service` joins the same local orchestration as `trade-api` (root
`docker-compose.yml`), reads the same `JWT_SECRET` `trade-api` verifies
with, and both point `JWT_ISSUER` at the same value (`auth-service`,
each service's own default - no consumer hard-requires a particular
issuer). Adopting this service is a configuration change only: `git diff
sprint-07 -- 'sprint-06-trade-api/**/*.java'` is empty.

```bash
docker compose up -d --build postgres kafka trade-api auth-service
sprint-08-auth-service/integration-test.sh
```

The script registers a user, logs in against the running auth service,
calls a protected Trade REST API route with the resulting access token
(expects 200), calls the same route with no token (expects 401), and
calls it again with a token signed by a key the platform should not trust
(expects 401). Results are also recorded in
`security-review/team4-auth-service-review.md`'s evidence table.

## API documentation (SEC4-630)

Generated from the running code (`@ApiTags`/`@ApiOperation`/`@ApiResponse`
on `AuthController`, `@ApiProperty` on the DTOs), not maintained by hand:

| Path | What it serves |
|---|---|
| `/docs` | Swagger UI - the human-readable page |
| `/docs/json` | the OpenAPI 3.0 document itself, as JSON |

This is evidence that the running service still matches
`contracts/auth-api.yaml`, not a replacement for it.

## Layout

| Path | Role |
|---|---|
| `src/main.ts` | bootstrap, global validation pipe, redacting logger |
| `src/app.module.ts` | module wiring, global exception filter |
| `src/health/` | container healthcheck (excluded from the OpenAPI document) |
| `src/auth/` | controller, service, DTOs, guard, throttle - the four contract routes |
| `src/users/` | credential store: repository, argon2id hasher |
| `src/tokens/` | access token issuance, refresh token issuance/rotation |
| `src/db/` | just the Postgres pool - the schema itself is `db/changelog/`, applied by Liquibase, not this code |
| `db/changelog/` | this service's own Liquibase changelog (users, refresh_tokens) - see "Schema changes" below |
| `src/common/` | the platform error envelope filter, the redacting logger |

Specs sit beside the code they cover, as `*.spec.ts`.

## Security notes

**Password hashing.** argon2id, m=64 MiB, t=3, p=1 - see the comment on
`src/users/password-hasher.ts` for why, and why parallelism is pinned to 1.
Measured ~110ms per verification on the team's development hardware.

**Login throttle (SEC4-628).** `src/auth/login-throttle.service.ts`, keyed
by caller IP: **5 failed attempts per 5-minute window**, in-memory and
per-instance. A caller over the limit is refused with the same AUTH-401 as
any other login failure - the throttle does not add a new status or a new
message, so it isn't itself a second oracle.

**Uniform login failure (SEC4-628).** An unknown username and a wrong
password return the same status, the same body, and do the same
argon2id work (a real verification for a known user, a verification
against a fixed dummy hash for an unknown one), so the response time
doesn't disclose which half of the credential pair was wrong.

**Refresh rotation (SEC4-627).** Every refresh issues a new refresh token
and revokes the one presented. A second presentation of an already-rotated
token is treated as theft: every live refresh token for that user is
revoked and the presentation answers AUTH-401.

See [`security-review/team4-auth-service-review.md`](security-review/team4-auth-service-review.md)
for the full OWASP write-up (SEC4-631), filled in as the service was built.
