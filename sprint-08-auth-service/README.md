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

## Layout

| Path | Role |
|---|---|
| `src/main.ts` | bootstrap, global validation pipe |
| `src/app.module.ts` | module wiring |
| `src/health/` | container healthcheck (excluded from the OpenAPI document) |

This section grows as later tickets (SEC4-623 through SEC4-631) add the
auth, users, tokens and guard modules. Specs sit beside the code they cover,
as `*.spec.ts`.
