# Person 3 — Authentication Guard & Refresh Token Lifecycle

**Tickets:** SEC4-626 (Guard and Token Verification on the Protected
Route), SEC4-627 (Refresh Token Issuance and Rotation)
**Branch:** `sprint-08-person-3`, based on `sprint-08` after Person 2's
`TokenService` merged (the guard doesn't depend on it directly, but shares
its `JWT_SECRET`/`JWT_ISSUER` config contract)
**Commits:** 9 — `git log --oneline sprint-08 ^sprint-07 --grep='Person 3\]'`
(4 for SEC4-626, 5 for SEC4-627)

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
| `src/db/pool.provider.ts`, `src/db/db.module.ts`, `src/db/schema.ts` | `pg.Pool` from `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` (no defaults that would let it start against the wrong database); an ordered, idempotent bootstrap (`CREATE ... IF NOT EXISTS`) run on module init. Starts with just the `refresh_tokens` table — Person 1's SEC4-624 later extends this same file with `users` |
| `src/tokens/refresh-token.repository.ts` | Parameterised queries only; stores `token_hash`, never the token |
| `src/tokens/refresh-token.service.ts` | `issue(userId)` (called on login) and `rotate(presentedToken)` (called on refresh): consumes the presented token, revokes it, issues a new one. If the presented token was **already** revoked, treats it as theft — revokes every live token for that user — before answering AUTH-401. SHA-256 for the token hash, not a slow KDF (see the file's own comment: a refresh token is 256 bits of random data, not a low-entropy password) |
| `src/tokens/refresh-token.service.spec.ts` | The three named paths: refresh returns a new, different token; the newly issued token works; a token already exchanged is refused and takes every other live token for that user down with it |

## Files touched

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

## Verifying this work independently

```bash
cd sprint-08-auth-service
npx jest jwt-auth.guard refresh-token.service
```

Both suites run with no database and no HTTP server — the guard against
hand-built JWTs, the refresh service against an in-memory fake repository.
