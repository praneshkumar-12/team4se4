# Person 3 — Authentication Guard & Refresh Token Lifecycle

**Tickets:** SEC4-626 (Guard and Token Verification on the Protected
Route), SEC4-627 (Refresh Token Issuance and Rotation)
**Branch:** `sprint-08-person-3`, based on `sprint-08` after Person 2's
`TokenService` merged (the guard doesn't depend on it directly, but shares
its `JWT_SECRET`/`JWT_ISSUER` config contract)
**Commits:** `git log --oneline sprint-08 ^sprint-07 --grep='Person 3\]'`
(4 for SEC4-626, 5 for SEC4-627, plus 3 more added in a later pass - see
"Schema moved to Liquibase" below)

## SEC4-626: the guard

Requirement: the protected route is guarded; an expired, tampered or
malformed token is refused with AUTH-401; Jest covers the guard including
the expired-token and wrong-signature paths specifically, each built the
right way (a genuine token with a past `exp`, not a corrupted payload; a
genuine unexpired token signed with a *different* key, not a mutated
signature).

| File | Role |
|---|---|
| `src/auth/guards/jwt-auth.guard.ts` | `canActivate()`: parses the `Authorization` header, calls `jsonwebtoken.verify(token, secret, { algorithms: ["HS256"], issuer })` — signature, algorithm and expiry are all checked before any claim is read — and attaches `{ sub, accountId, roles }` to the request. Anything that fails throws Nest's `UnauthorizedException` (mapped to AUTH-401 by Person 1's filter, added later) |
| `src/auth/decorators/current-user.decorator.ts` | `@CurrentUser()` reads the request property the guard set — never a client-supplied parameter |
| `src/auth/guards/jwt-auth.guard.spec.ts` | All four named paths: valid token accepted; expired token refused (signed with a past `exp`, not corrupted); wrong-signature token refused (signed with a different key, not a mutated signature — this is the test that would catch a verifier that decodes before it verifies); malformed header refused (missing, empty, scheme-only, wrong scheme, non-JWT value) |

## SEC4-627: refresh issuance and rotation

Requirement: store a hash of the refresh token, not the token; every
refresh issues a new one; the team decided to **build** revocation of the
presented token (recorded in the security review) — a second presentation
of a token already exchanged answers AUTH-401.

| File | Role |
|---|---|
| `src/db/pool.provider.ts`, `src/db/db.module.ts`, `src/db/schema.ts` (`schema.ts` since removed — see below) | `pg.Pool` from `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` (no defaults that would let it start against the wrong database). Originally also ran an ordered, idempotent bootstrap (`CREATE ... IF NOT EXISTS`) on module init, starting with just `refresh_tokens` — Person 1's SEC4-624 extended this same file with `users`. That bootstrap mechanism no longer exists; see "Schema moved to Liquibase" below |
| `src/tokens/refresh-token.repository.ts` | Parameterised queries only; stores `token_hash`, never the token |
| `src/tokens/refresh-token.service.ts` | `issue(userId)` (called on login) and `rotate(presentedToken)` (called on refresh): consumes the presented token, revokes it, issues a new one. If the presented token was **already** revoked, treats it as theft — revokes every live token for that user — before answering AUTH-401. SHA-256 for the token hash, not a slow KDF (see the file's own comment: a refresh token is 256 bits of random data, not a low-entropy password) |
| `src/tokens/refresh-token.service.spec.ts` | The three named paths: refresh returns a new, different token; the newly issued token works; a token already exchanged is refused and takes every other live token for that user down with it |

## Schema moved to Liquibase (SEC4-624/SEC4-627, a later pass)

After every original branch had merged, SEC4-624's `users` table and this
ticket's `refresh_tokens` table were reworked from the TypeScript
bootstrap above into a real Liquibase changelog — `db/changelog/`,
applied by a new `auth-service-migrate` job in `docker-compose.yml`. Done
on this branch (synced back up to `sprint-08`'s tip first) since it's the
branch that owns `src/db/`. See `RUNBOOK.md`'s "A seventh pass" section
for the full reasoning and verification.

| File | Role |
|---|---|
| `db/changelog/db.changelog-master.xml` | Two precondition-guarded changesets, `001-users` then `002-refresh-tokens` (order matters: the second's FK depends on the first's table) |
| `db/changelog/changes/001-users.sql` | The `users` table, now with a named FK to `accounts` and two CHECK constraints on `roles` (non-empty, only `CUSTOMER`/`ADMIN`) enforced at the database layer |
| `db/changelog/changes/002-refresh-tokens.sql` | The `refresh_tokens` table, now with a real FK to `users(id)` (`ON DELETE CASCADE`) - the original bootstrap deliberately left this out because it had no cross-statement ordering guarantee; Liquibase's changeset order removes that excuse |
| `src/db/db.module.ts` (edit) | No longer implements `OnModuleInit` - just the pool provider |
| `src/db/schema.ts` | Deleted |
| `docker-compose.yml` (edit) | Adds `auth-service-migrate` (the official `liquibase/liquibase` image, changelog volume-mounted in, run as a one-shot `update` job, depends on `trade-api` healthy); `auth-service` now depends on `auth-service-migrate: condition: service_completed_successfully` instead of on `trade-api` directly |

**Verified against a real, completely empty Postgres volume before
committing** (not just written): dropped `docker compose up` on a fresh
`postgres-data` volume and watched all four containers come up healthy in
order; confirmed the CHECK/FK constraints with direct `psql` inserts (a
bad role, an empty roles array and an unknown `accountId` were all
rejected; `ADMIN` was accepted); confirmed re-running the migration is a
genuine no-op; re-ran the full SEC4-629 integration test against tables
this job created.

## Files touched

Original SEC4-626/SEC4-627 pass:

```
A  sprint-08-auth-service/src/auth/decorators/current-user.decorator.ts
A  sprint-08-auth-service/src/auth/guards/jwt-auth.guard.ts
A  sprint-08-auth-service/src/auth/guards/jwt-auth.guard.spec.ts
A  sprint-08-auth-service/src/db/db.module.ts
A  sprint-08-auth-service/src/db/pool.provider.ts
A  sprint-08-auth-service/src/db/schema.ts
A  sprint-08-auth-service/src/tokens/refresh-token.repository.ts
A  sprint-08-auth-service/src/tokens/refresh-token.service.ts
A  sprint-08-auth-service/src/tokens/refresh-token.service.spec.ts
M  sprint-08-auth-service/src/tokens/tokens.module.ts   (export RefreshTokenService)
M  sprint-08-auth-service/src/app.module.ts             (register DbModule)
M  sprint-08-auth-service/package.json, package-lock.json  (@types/express, for the guard's request typing)
```

Later "schema moved to Liquibase" pass:

```
A  sprint-08-auth-service/db/changelog/db.changelog-master.xml
A  sprint-08-auth-service/db/changelog/changes/001-users.sql
A  sprint-08-auth-service/db/changelog/changes/002-refresh-tokens.sql
M  sprint-08-auth-service/src/db/db.module.ts   (drop OnModuleInit)
D  sprint-08-auth-service/src/db/schema.ts
M  docker-compose.yml                          (auth-service-migrate job)
```

## Verifying this work independently

```bash
cd sprint-08-auth-service
npx jest jwt-auth.guard refresh-token.service
```

Both suites run with no database and no HTTP server — the guard against
hand-built JWTs, the refresh service against an in-memory fake repository.

For the Liquibase changelog, against a real Postgres (see the repo root
`RUNBOOK.md` for bringing up the full stack):

```bash
docker compose run --rm auth-service-migrate status   # what's pending
docker compose run --rm auth-service-migrate update    # apply
```
