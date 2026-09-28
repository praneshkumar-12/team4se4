You are working in the repo at the root `G:\team4se4` (or wherever it's
checked out). Read `CLAUDE.MD` at the repo root fully before doing anything
else and follow it: scope discipline, ask-don't-assume, TDD, the commit
message format, and the branching rules.

# Who you are

Person 5 of 5 on Sprint 9 (Team 4 — Trading UI, Angular). **Your tickets:
SEC4-640, SEC4-641 and SEC4-642.** You close the sprint — Playwright needs
sign-in and the order ticket actually working end to end, and the secret
scan needs the real production bundle, so your work is sequenced last on
purpose.

**Prerequisite:** `sprint-09-person-1` (foundation), `sprint-09-person-2`
(sign-in + guards) and `sprint-09-person-4` (order ticket) must all be
merged into `sprint-09` before you write the Playwright journeys — they
exercise those screens by the stable test identifiers Person 2 documented.
`sprint-09-person-3` (interceptor + error catalogue) should ideally be
merged too, but the blotter itself only needs Person 1's foundation and
the generated Trade API client, so you can start that part earlier if 2/3/4
are still finishing. Confirm what's actually merged into `sprint-09` before
you start each ticket rather than assuming the table in
`docs/prompts/README.md` is still accurate.

**Branch:** create `sprint-09-person-5` from `sprint-09` once the
prerequisites above are in. When done, merge back into `sprint-09` — this
is the sprint's last merge.

## What you're building on

- `generated/trade-api/` has the order-history endpoint's typed client.
- `core/errors/error-message.service.ts` (Person 3) for any error surface
  in the blotter.
- Person 2's sign-in screen test identifiers (documented in
  `features/sign-in/`) and Person 4's order-ticket screen, for your
  Playwright journeys.
- `core/config/environment.ts` for allowed origins / API base URLs —
  useful when the secret scan needs to confirm nothing else snuck in
  beside it.
- ZardUI (https://zardui.com/) is set up and compulsory — see `docs/
  prompts/README.md`'s design-system section. Build the blotter from its
  `Table`, `Badge` (status — word and colour, per SEC4-640, using the
  palette Person 1 recorded — no neon and no weak-pastel status colours,
  no rainbow-per-status colouring beyond the deliberate red/green/neutral
  set) and `Button` (manual refresh) components, and use `Skeleton` for
  the loading state instead of a blank flash. No drop shadow on table
  rows/cards, no hover-scale animation on rows — a status change or a new
  row is the only thing that should visibly move. Add any missing
  component with `npx zard-cli@latest add <name>` and commit it under
  `src/app/shared/components/`. For Playwright, select elements by
  `data-testid`
  regardless of which component renders them — put the attribute on
  whichever element the ZardUI component actually forwards it to (check
  its source if unclear; it's your own repo's copy of the component, not
  a black box).

# SEC4-640 — Blotter with Status Badges and the NEW Path

**Acceptance criteria:**
- The blotter lists every order for the account newest first, rejections
  included, with a badge per status carrying the word as well as the
  colour.
- An order sitting at `NEW` is shown as still working and is brought up
  to date by a bounded re-read.

**Tasks:**
- Render the order table in `features/blotter/` from the order-history
  endpoint, with formatted dates and values.
- Badge `NEW`, `FILLED`, `REJECTED` and `CANCELLED`, carrying the word as
  well as the colour — roughly one man in twelve cannot separate red from
  green, so colour alone is not an acceptable status signal here.
- Re-read order history on a bounded interval **while anything is at
  `NEW`**, offer a refresh the user can press, and **stop polling when
  nothing is working** — an interval that never stops is a request every
  couple of seconds for as long as the tab is open, which is a load test
  you didn't mean to run. Use Angular's `interval`/`timer` composed with a
  signal/effect that tears down the subscription once no order is `NEW`,
  not a bare `setInterval` left running.
- Never re-post the order: the same idempotency key returns a conflict and
  a new key places a second order — this only matters if the blotter (or
  a retry path near it) ever re-submits; if it doesn't, say in a comment
  why the idempotency key concern doesn't apply here rather than silently
  ignoring the task.

**Unit Test Execution Paths:**
- order history renders newest first including rejections
- an order at `NEW` is shown as still working
- the re-read stops when nothing is at `NEW`
- empty history is handled gracefully

**Notes:**
- A blotter that hides rejected orders is worse than no blotter — the
  rejection is the record that the desk tried and was refused. Don't
  filter `REJECTED` out anywhere in the pipeline.

# SEC4-641 — Playwright: Sign In and Place an Order

**Acceptance criteria:**
- Two Playwright journeys run against the real running stack: signing in
  and placing an order.
- Each journey stands on its own, in its own process, leaving nothing
  behind that another journey needs.
- Every address and credential is read from the environment under
  declared names.

**Tasks:**
- Configure Playwright in `sprint-09-trading-ui/` (it needs the whole
  stack up — Auth service, Trade API, this app served — to run at all).
  Bring the backends up with `./run-local.sh start` from the repo root
  (see `run-local-steps.md`) rather than reinventing that; serve this app
  with whatever Person 1 wired `npm start`/`ng serve` to (including the
  dev proxy that avoids the CORS gap — see Person 1's prompt). If
  Playwright is meant to run against the built production bundle instead
  of the dev server, the proxy won't apply and you'll hit real CORS
  errors from the backends — stop and ask rather than adding CORS headers
  to `sprint-06-trade-api`/`sprint-08-auth-service` yourself.
- **Sign-in journey:** cover the guard redirect (visiting a protected
  route signed out lands on sign-in), a refused sign-in (wrong
  credentials → readable error, per Person 2's SEC4-635), a successful
  sign-in, and arriving back where you were going (the return-address
  behaviour from SEC4-637) — select elements by the `data-testid`s Person
  2 documented, not by label text.
- **Place-order journey:** cover the read-only account field (assert it
  can't be edited/submitted differently), a rejection before submission
  (an invalid quantity/price never reaches the wire), and a placed order
  showing whatever status actually came back.
- **Accept `NEW`, `FILLED` or `REJECTED`** on a placed order — a spec that
  asserts `FILLED` fails the week the executor is switched on, since
  execution has been asynchronous since Sprint 7. Assert "one of these
  three, and the UI shows it," not a specific one.
- Read every base URL and every credential from environment variables
  under names you declare in a `.env.example` (or the repo's existing
  `run-local.env.example` pattern — check it first) — no hard-coded
  `localhost:4200`/test user baked into the spec files.
- **Isolation:** no token stashed in a module-level variable shared across
  specs, no order one spec places that another expects to see. Each
  journey signs in fresh (or starts signed-out, as the journey requires)
  and doesn't depend on ordering or on data another test file created.

**Notes:**
- Two journeys is the assessed set for this cohort. A third over the
  blotter is worth adding only if there's time left after everything else
  here is solid — don't let it displace the two required ones.

# SEC4-642 — Secret-Free Production Bundle

The epic names this one explicitly too — "nothing secret in a public
bundle," OWASP A01/A03/A05 — so treat it as a real gate, not a courtesy
check.

**Acceptance criteria:**
- The production build contains no API key, no signing secret and no
  market-data host.
- The application never calls the Fauxnance API; prices reach the browser
  through a service of yours that holds the key server-side (i.e. this
  Angular app talks only to the Trade REST API, never to Fauxnance
  directly, at any layer — check this holds, don't just check the bundle).

**Tasks:**
- Build the production bundle (`ng build` / `npm run build`) and search it
  for key/secret patterns and for the market-data host. Concretely, grep
  `dist/` for at least: `FAUXNANCE`, the literal Fauxnance base host
  (`y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com` — check the repo root
  `.env.example` in case it's rotated by the time you run this), `API_KEY`,
  `SECRET`, and the literal value of `JWT_SECRET` from the root
  `.env.example`. Do this as a real command you run against the built
  output, not a manual skim — consider wiring it as an `npm run
  check:bundle` script so it's repeatable and so CI (if there is one)
  could run it later.
- Confirm nothing under a different name carries the literal value of a
  key or secret — grepping for the name `FAUXNANCE_API_KEY` isn't enough
  if someone renamed the variable but kept the value; grep for the value
  itself where you have one to check against.
- If you find a real key in a build artifact anywhere in this repo's
  history while doing this (not just the current bundle), treat it as
  published: say so and recommend revoking it, don't just delete the
  line — a deleted line doesn't unpublish a key that already reached a
  built bundle or a commit.
- Remember: minification is not obfuscation, and a source map is the
  source again — if you generate source maps for the production build,
  the scan has to run against what actually ships (whether or not that
  includes maps), not just the minified JS.

# Definition of done

- `npm run build && npm test` green (unit tests) on your branch.
- Blotter's four Unit Test Execution Paths all exist and pass.
- The two Playwright journeys pass against the real running stack (Auth
  service, Trade API, and this app all up) — actually run them, don't just
  write them.
- The bundle scan is a real command with real output showing nothing
  found, attached to your PR/commit message or the README.
- Commit(s) on `sprint-09-person-5`, message format `SEC4-640/SEC4-641/
  SEC4-642 <title>` + description, per `CLAUDE.MD`.
- Merge into `sprint-09` — this closes the sprint. Merging `sprint-09`
  into `main` is a separate, later step and not yours to do without being
  asked (per `CLAUDE.MD`).
