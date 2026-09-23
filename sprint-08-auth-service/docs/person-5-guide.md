# Person 5 — Infrastructure, Trade API Integration & Docs

**Tickets:** SEC4-622 (NestJS Project and the Engineering Contract),
SEC4-629 (Adopt the Real Auth Service with a Configuration Change Only),
SEC4-630 (OpenAPI Served by the Running Service)
**Branch:** `sprint-08-person-5`, used twice — first for SEC4-622 (before
anything else exists), merged, then synced back up to date with
`sprint-08` (`git merge sprint-08`) and reused for SEC4-629/630 once
everyone else's work existed to integrate against
**Commits:** 10 — `git log --oneline sprint-08 ^sprint-07 --grep='Person 5\]'`
(3 for SEC4-622, 4 for SEC4-629, 3 for SEC4-630)

## SEC4-622: project scaffold and the engineering contract

Requirement: Node 20+ TypeScript, `npm ci && npm run build && npm test`
green on a clean machine, a multi-stage Dockerfile, joined to the team's
orchestration on port 3000.

| File | Role |
|---|---|
| `package.json`, `package-lock.json`, `tsconfig.json`, `tsconfig.build.json`, `jest.config.js`, `.gitignore` | The project itself. `npm ci` was run against a **deleted** `node_modules` to confirm the lock file is really sufficient, not just "worked once" |
| `src/main.ts`, `src/app.module.ts` | Bootstrap, global `ValidationPipe` (`whitelist`, `forbidNonWhitelisted`) wired here so every later route inherits it |
| `src/health/health.controller.ts` (+ spec) | `/health`, excluded from the OpenAPI document — container healthcheck plumbing, not contract surface |
| `Dockerfile`, `.dockerignore` | Multi-stage: build stage has the compiler and dev deps, runtime stage has neither |
| `docker-compose.yml` (root, edit) | Adds the `auth-service` block on port 3000, `depends_on: trade-api` (not just `postgres`) since the credential store's FK needs the Sprint 3 schema to exist first |
| `README.md`, `.env.example` | This service's own docs and standalone-dev env template |

## SEC4-629: adopt the real service — configuration only

Requirement: join local orchestration; read the same `JWT_SECRET` the
Trade REST API verifies with; point its issuer setting at this service's
issuer, if it pins one; decide keep-vs-rotate on the published dev
`JWT_SECRET`; integration test against the running stack; the Trade REST
API needs no Java change.

**A real bug was found and fixed here, not just a feature added:** the
original `docker-compose.yml` entry (SEC4-622) used the repo root as the
build context, matching `trade-api`'s pattern — but `trade-api` needs that
because it also builds `sprint-05-domain-engine`; this service has no such
sibling and its `Dockerfile`'s `COPY` paths assume its own directory as
context. `docker compose build` failed with `"package-lock.json": not
found` until this was corrected.

| File | Role |
|---|---|
| `docker-compose.yml` (edit) | Fixes the build context (see above) |
| `.env.example` (root, edit) | **Decision: rotate**, not keep, the dev `JWT_SECRET` — it had been sitting in this repo's history since before a real issuer existed to sign with it. Reasoning recorded in both the comment here and the security review |
| `integration-test.sh` | Registers a user, logs in, calls a protected Trade REST API route with the resulting token, confirms 200; confirms 401 with no token and 401 with a token signed by an untrusted key |
| `security-review/team4-auth-service-review.md` (edit) | Fills in the A04 cell Person 4 left open, adds the integration evidence rows |

**This was run for real, not just written:** `docker compose up -d
--build postgres kafka trade-api auth-service`, then the script above.
Registered a user against account 1, logged in, called `GET
/api/v1/accounts/1` on the **unmodified** Java Trade REST API with the
NestJS-issued token — got back the real seeded account row
(`ACC-10001`, `Arun Kumar`, 200). No token → 401. Token signed with a key
the platform shouldn't trust → 401.
`git diff --name-only sprint-07 -- 'sprint-06-trade-api/**/*.java'` came
back empty, confirming the "no Java changed" acceptance criterion
directly rather than by inspection.

## SEC4-630: OpenAPI served by the running service

Requirement: generate the document from the code; serve a human page and
the JSON at two named, recorded paths; this is evidence the code matches
the contract, not a replacement for it.

| File | Role |
|---|---|
| `src/main.ts` (edit) | `SwaggerModule` wired from `AuthController`'s and the DTOs' decorators (all written by Person 1 in SEC4-623). `/docs` (human page), `/docs/json` (JSON, via `jsonDocumentUrl`) |
| `src/openapi.spec.ts` | Builds the document against a minimal testing module and asserts all four contract paths and verbs are present, without a database or HTTP server |
| `README.md` (edit) | Records both paths |

**Verified against the running process:** booted the built service
against a real (temporary) Postgres, fetched `GET /docs/json`, confirmed
it's valid OpenAPI 3.0 describing exactly `/auth/register`, `/auth/login`,
`/auth/refresh`, `/auth/me` — then exercised all four routes end to end
against that same running instance (register, duplicate username → 409,
unknown account → 422, login, wrong password/unknown user → matching 401s,
`/auth/me` with a valid/missing/wrong-key token, refresh, replayed refresh
token → 401).

## Files touched

```
SEC4-622:
A  sprint-08-auth-service/.dockerignore
A  sprint-08-auth-service/.env.example
A  sprint-08-auth-service/.gitignore
A  sprint-08-auth-service/Dockerfile
A  sprint-08-auth-service/README.md
A  sprint-08-auth-service/jest.config.js
A  sprint-08-auth-service/package-lock.json
A  sprint-08-auth-service/package.json
A  sprint-08-auth-service/src/app.module.ts
A  sprint-08-auth-service/src/health/health.controller.ts
A  sprint-08-auth-service/src/health/health.controller.spec.ts
A  sprint-08-auth-service/src/main.ts
A  sprint-08-auth-service/tsconfig.build.json
A  sprint-08-auth-service/tsconfig.json
M  docker-compose.yml   (add auth-service)

SEC4-629:
M  docker-compose.yml   (fix build context)
M  .env.example         (rotate JWT_SECRET)
A  sprint-08-auth-service/integration-test.sh
M  sprint-08-auth-service/security-review/team4-auth-service-review.md
M  sprint-08-auth-service/README.md

SEC4-630:
M  sprint-08-auth-service/src/main.ts
A  sprint-08-auth-service/src/openapi.spec.ts
M  sprint-08-auth-service/README.md
```

## Verifying this work independently

```bash
cd sprint-08-auth-service && rm -rf node_modules dist && npm ci && npm run build && npm test
cd .. && docker compose up -d --build postgres kafka trade-api auth-service
sprint-08-auth-service/integration-test.sh
curl -s http://localhost:3000/docs/json | python3 -m json.tool | head -20
```
