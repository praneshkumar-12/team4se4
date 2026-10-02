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

Executed against a real local stack (auth-service, trade-api, and `ng
serve`, all actually up) — all 7 tests pass. Doing this surfaced three real
bugs, now fixed:

- `proxy.conf.json`'s `/auth-api/*` and `/trade-api/*` patterns only match
  one path segment after the prefix, so multi-segment real endpoints
  (`/auth-api/auth/login`, `/trade-api/api/v1/accounts/{id}/orders`, …)
  silently 404'd under the dev server's actual (Vite/picomatch) glob
  matching. Fixed to `/**`.
- `ZardInputComponent.writeValue()` (`shared/components/input`) coerced a
  `null` initial value to `''`, which permanently broke the numeric
  round-trip for any `type="number"` control that starts out empty
  (`quantity`, `price`) - every keystroke came back as a string, so the
  business-rule validators (which require a real `number`) could never
  pass and no order could ever be submitted. Fixed to preserve `null`.
- `place-order.spec.ts`'s `beforeEach` called `signIn()` then
  `page.goto('/orders/new')` - a hard navigation, which drops the
  deliberately in-memory-only bearer token (`TokenStore`) before the route
  guard runs. Replaced with `signInAndGoTo()`, which reaches the page
  through the app's own guard-redirect instead.

One caveat specific to *this* environment, not the code: with no network
route to the Kafka VM (`run-local.sh`'s SSH check times out here) and no
Docker available, `trade-api`'s order-placement call blocks for Kafka's
default `max.block.ms` (60s) before falling through to its documented
at-least-`NEW` behavior - confirmed directly with `curl` (60.4s, then a
normal `200`/`NEW` response) and by raising the third test's assertion
timeout for one run (passed in ~1 minute). With a reachable Kafka broker
this is instant, as the other two sprints' authors intended; no code
change was made to work around it.
