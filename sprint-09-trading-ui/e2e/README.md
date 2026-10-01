# Playwright journeys (SEC4-641)

Two journeys, each in its own file, each starting from a fresh signed-out
(or freshly signed-in) browser context — nothing here depends on another
test's order or leftover state:

- `sign-in.spec.ts` — guard redirect, a refused sign-in, a successful
  sign-in, and the return-address behaviour (SEC4-637).
- `place-order.spec.ts` — the read-only account field, a rejection the
  client blocks before it reaches the wire, and a placed order showing
  whichever of `NEW` / `FILLED` / `REJECTED` actually came back (execution
  has been asynchronous since Sprint 7 — this never asserts a specific
  status).

## Running them

These run against the real stack — Auth service, Trade REST API, and this
app's dev server, all actually up. Nothing here mocks the network.

1. **Bring up the backends.** Either:
   - `./run-local.sh start` from the repo root (needs `run-local.env` filled
     in with real VM/DB credentials — see `run-local-steps.md`), or
   - `docker compose up -d postgres kafka trade-api auth-service` from the
     repo root, with a root `.env` (copy `.env.example`, fill in a real
     `JWT_SECRET`) — the fully-local alternative, no remote VM needed.

   Either way you end up with the Trade REST API on `:8085` and the Auth
   service on `:3000`.

2. **Serve this app**, proxied to those backends:
   ```bash
   npm start   # ng serve, proxy.conf.json forwards /auth-api and /trade-api
   ```
   Playwright runs against the dev server, not the production bundle — the
   dev proxy is what avoids the CORS gap (neither backend has CORS
   configured; see the root `docs/prompts/person-1-prompt.md`). Running
   these against `ng build`'s output would hit real cross-origin failures
   unrelated to anything these journeys are testing.

3. **Configure the journeys:**
   ```bash
   cp .env.example .env   # fill in E2E_BASE_URL / E2E_AUTH_EMAIL / E2E_AUTH_PASSWORD / E2E_AUTH_INVALID_PASSWORD
   npm run e2e:install    # once, downloads the Chromium build Playwright drives
   ```

4. **Run them:**
   ```bash
   npm run e2e
   ```

`E2E_AUTH_EMAIL`/`E2E_AUTH_PASSWORD` default (in `.env.example`) to the
demo seed user `sprint-06-trade-api`'s `003-demo-seed.sql` creates —
`arun.kumar@example.com`, account 1 / `ACC-10001`, the only seeded account
that's `ACTIVE`. `place-order.spec.ts` places a real order against that
account for `TCS` (also demo-seeded, active and tradable) at a price well
inside its seeded cash balance.

## Status as of this commit

Configured and written against the real test identifiers (`sign-in`'s
README, `order-ticket-page.html`), and `npx playwright test --list` loads
both files cleanly (7 tests, no syntax/config errors). They have **not**
been executed against a live stack in this environment — no `run-local.env`
was available and bringing up Docker Desktop's full stack (image builds
included) was out of scope for this pass. Run them for real per the steps
above before treating SEC4-641 as fully done; this is flagged rather than
silently claimed.
