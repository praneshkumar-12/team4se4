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
| `src/db/` | Postgres pool and this service's own bootstrap (`schema.ts`) |
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
