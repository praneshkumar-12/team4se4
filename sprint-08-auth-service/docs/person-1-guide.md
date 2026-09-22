# Person 1 — User & Credential Management

**Tickets:** SEC4-623 (Implement the Provided Auth Contract), SEC4-624
(Credential Store and Password Hashing)
**Branch:** `sprint-08-person-1`, based on `sprint-08` after Person 2's
`TokenService` and Person 3's guard/`RefreshTokenService` were already
merged
**Commits:** 7 — `git log --oneline sprint-08 ^sprint-07 --grep='Person 1\]'`

## What SEC4-623 required and what was built

The ticket: implement registration, login, refresh and `/auth/me` at the
paths, verbs, status codes and bodies `contracts/auth-api.yaml` fixes,
with the documented failures, all in the platform error envelope. No
tokens on registration, no trading-account creation on registration.
Validate with class-validator/class-transformer, VAL-422 on a field
failure.

Built as `src/auth/`:

| File | Role |
|---|---|
| `auth.controller.ts` | `POST /auth/register` (201), `POST /auth/login` (200), `POST /auth/refresh` (200), `GET /auth/me` (200, guarded) |
| `auth.service.ts` | Orchestrates registration/login/refresh/me against `UsersRepository` (Person 1), `PasswordHasher` (Person 1), `TokenService` (Person 2) and `RefreshTokenService` (Person 3) |
| `auth.module.ts` | Wires the above together |
| `dto/register.dto.ts`, `login.dto.ts`, `refresh.dto.ts`, `token-response.dto.ts`, `user-response.dto.ts`, `role.ts` | Match the contract's schemas field for field: lengths, the username pattern, `minLength: 12` on the password |
| `auth.service.spec.ts` | Unit tests: no tokens on registration, a duplicate username → AUTH-409, an unknown `accountId` → VAL-422, correct/wrong login, refresh, `/auth/me` |
| `src/common/platform-exception.filter.ts` (+ spec) | Maps HTTP status → the contract's `{errorCode, message}` envelope (401→AUTH-401, 409→AUTH-409, 422→VAL-422), registered globally in `app.module.ts` |
| `src/main.ts` (edit) | `ValidationPipe` set to `errorHttpStatusCode: 422` so a validation failure lands on the contract's code, not Nest's 400 default |

**A decision worth flagging for review:** `RegisterDto` *accepts* the
contract's optional `roles` field (so a conforming client sending it
doesn't get rejected by `forbidNonWhitelisted`) but `AuthService.register`
never reads `dto.roles` — it hard-codes `["CUSTOMER"]`. The contract's own
notes call honouring a self-declared role on a public route a
privilege-escalation bug. Covered by a test in `auth.service.spec.ts`.

## What SEC4-624 required and what was built

The ticket: store passwords with argon2id or bcrypt ≥12, never log a
password, defend the cost factor, build the store as a migration or
bootstrap plus a repository, log through one logger that redacts by key
name at any depth.

Built as `src/users/` and `src/common/logger.ts`:

| File | Role |
|---|---|
| `password-hasher.ts` | argon2id, m=64 MiB, t=3, p=1 (~110ms measured locally — see the file's comment for the OWASP basis and why parallelism is pinned to 1, not OWASP's p=4). `getDummyPasswordHash()` precomputes a fixed hash for Person 4's SEC4-628 uniform-failure path |
| `password-hasher.spec.ts` | The three named paths: correct password verifies, incorrect fails, and no general-purpose digest is used (asserted via the `$argon2id$` prefix, not a bare hex digest) |
| `users.repository.ts` | Parameterised Postgres queries only; exposes Postgres error codes (23505/23503) for the controller layer to map, without the repository itself knowing about HTTP |
| `users.module.ts` | Exports the repository and hasher |
| `db/changelog/changes/001-users.sql` (this file's original version, since replaced — see below) | Added the `users` table: `account_id` FK to the Sprint 3 `accounts` table this service never writes to, `ON DELETE RESTRICT` |
| `src/common/logger.ts` (+ spec) | `RedactingLogger`: redacts `password`/`passwordHash`/`accessToken`/`refreshToken`/`authorization`/`token` at any depth, case-insensitive, including inside a serialised `Error`'s own properties — the indirect route the ticket names |

## Files touched (from `git diff --name-status`)

```
A  sprint-08-auth-service/src/auth/auth.controller.ts
A  sprint-08-auth-service/src/auth/auth.module.ts
A  sprint-08-auth-service/src/auth/auth.service.ts
A  sprint-08-auth-service/src/auth/auth.service.spec.ts
A  sprint-08-auth-service/src/auth/dto/login.dto.ts
A  sprint-08-auth-service/src/auth/dto/refresh.dto.ts
A  sprint-08-auth-service/src/auth/dto/register.dto.ts
A  sprint-08-auth-service/src/auth/dto/role.ts
A  sprint-08-auth-service/src/auth/dto/token-response.dto.ts
A  sprint-08-auth-service/src/auth/dto/user-response.dto.ts
A  sprint-08-auth-service/src/common/logger.ts
A  sprint-08-auth-service/src/common/logger.spec.ts
A  sprint-08-auth-service/src/common/platform-exception.filter.ts
A  sprint-08-auth-service/src/common/platform-exception.filter.spec.ts
A  sprint-08-auth-service/src/users/password-hasher.ts
A  sprint-08-auth-service/src/users/password-hasher.spec.ts
A  sprint-08-auth-service/src/users/users.module.ts
A  sprint-08-auth-service/src/users/users.repository.ts
M  sprint-08-auth-service/src/app.module.ts       (register AuthModule + APP_FILTER)
M  sprint-08-auth-service/src/db/schema.ts         (add the users table; superseded - see note below)
M  sprint-08-auth-service/src/main.ts              (422 validation status, RedactingLogger)
```

**Note:** the `users` table's DDL originally lived in `src/db/schema.ts`
(a TypeScript array run at app startup) as this listing shows. It was
later moved to a proper Liquibase changelog
(`db/changelog/changes/001-users.sql`) in a follow-up pass on
`sprint-08-person-3` - see `person-3-guide.md` and `RUNBOOK.md`'s "A
seventh pass" section. `schema.ts` no longer exists on `sprint-08`.

## Verifying this work independently

```bash
cd sprint-08-auth-service
npx jest auth.service password-hasher logger platform-exception
```

Or exercise it live (needs Postgres reachable and `JWT_SECRET` set — see
the repo root `RUNBOOK.md` for the full stack):

```bash
curl -X POST http://localhost:3000/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"priya.menon","password":"correct horse battery staple","accountId":1}'
```
