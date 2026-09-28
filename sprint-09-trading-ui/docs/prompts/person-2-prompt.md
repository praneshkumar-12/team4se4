You are working in the repo at the root `G:\team4se4` (or wherever it's
checked out). Read `CLAUDE.MD` at the repo root fully before doing anything
else and follow it: scope discipline, ask-don't-assume, TDD, the commit
message format, and the branching rules.

# Who you are

Person 2 of 5 on Sprint 9 (Team 4 — Trading UI, Angular). **Your tickets:
SEC4-635 and SEC4-637 only.** Do not touch the interceptor, the error
catalogue, the order ticket, the blotter or Playwright config — those are
other people's tickets, landing on parallel branches.

**Prerequisite:** `sprint-09-person-1` must already be merged into
`sprint-09` — confirm `sprint-09-trading-ui/` exists with `core/config/
routes.const.ts`, `core/auth/token-store.ts` and the placeholder
`features/sign-in/` route before you start. If it isn't there yet, stop
and tell me rather than scaffolding your own copy.

**Branch:** create `sprint-09-person-2` from `sprint-09` (after Person 1's
merge). When done, rebase onto the latest `sprint-09` (Person 1's branch is
the only thing that should have changed there while you worked — if 3 or 4
have also merged, that's fine, just rebase past them) and merge back into
`sprint-09`.

## What you're building on

- `core/config/routes.const.ts` exports `ROUTE_PATHS` — use
  `ROUTE_PATHS.signIn` etc., never a literal string.
- `core/auth/token-store.ts` exports `TokenStore`: inject it, call
  `setToken(token)` on a successful login and `clearToken()` on sign-out.
  Don't invent a second place the token lives.
- `generated/auth-api/` has the typed client for the Auth API (Person 1's
  SEC4-634). Call the real generated login/register/etc. methods — don't
  hand-write an `HttpClient` call around the auth endpoints.
- The app already runs and routes; you're filling in the `sign-in` feature
  and adding the guard, not scaffolding the app.
- ZardUI (https://zardui.com/) is set up and compulsory — see `docs/
  prompts/README.md`'s design-system section. Build the sign-in form out
  of its `Input`, `Button`, `Card` and `Alert`/`Field` (for the readable
  error and the empty-field validation state) components, not raw
  `<input>`/`<button>` — if a component you need isn't installed yet, add
  it with `npx zard-cli@latest add <name>` and commit the generated files
  under `src/app/shared/components/`. `data-testid` goes on the ZardUI
  component's host element or its native input, whichever Playwright can
  actually select. A sign-in screen is also the single easiest place on
  this app to slide into "vibecoded" territory (see `docs/prompts/
  README.md`) — no radial-orb or dot-grid decorative background behind
  the form, no glass/blur card, no gradient on the submit button, no
  bouncing/animated arrow icon on it, no colored-left-stripe treatment on
  the invalid-credentials alert (use ZardUI's own `Alert` styling as-is).
  It's a form, not a hero section — keep it one.

# SEC4-635 — Sign-In Flow against the Real Auth Service

**Acceptance criteria:**
- A customer signs in against the running Auth service, the token is
  stored, and a failed sign-in shows a readable error.
- Signing out clears the session.

**Tasks:**
- Build the sign-in screen with username and password inputs and a submit
  control.
- Give the username field, the password field and the submit control
  **stable test identifiers** (e.g. `data-testid="sign-in-username"`,
  `sign-in-password`, `sign-in-submit`) — Person 5's Playwright journeys
  select them by identifier, not by label text. Pick identifiers and note
  them in this feature's own short README or top-of-file comment so
  Person 5 doesn't have to read your component to find them.
- Store the token via `TokenStore.setToken()` and clear it via
  `TokenStore.clearToken()` on sign-out. Build the sign-out control
  wherever makes sense in the shell (Person 1 didn't build app-shell nav —
  add a minimal one if none exists yet, or ask if you're unsure it's in
  scope here).

**Unit Test Execution Paths (write these, TDD-style, before/alongside the
code):**
- valid credentials sign in and redirect
- invalid credentials show a readable error
- empty fields are blocked by validation
- signing out clears the session

**Integration Test:**
- Sign in against the real running Auth service end to end (this is
  separate from — and in addition to — Person 5's Playwright journey; a
  narrower integration test here, e.g. hitting the real service from a
  test runner, is what the ticket is asking for). Bring the real service
  up with the repo's own tooling — `./run-local.sh start` from the repo
  root (see `run-local-steps.md`) — rather than a one-off `docker run`;
  Person 1 should already have a dev proxy in place so the browser/test
  runner reaches it without a CORS error (see Person 1's prompt if that
  isn't working yet — ask rather than working around it).

# SEC4-637 — Route Guards and the Return Address

**Acceptance criteria:**
- Every route but sign-in runs a guard, and a signed-out visitor is
  redirected to sign-in carrying where they were going.
- A return address is accepted only if it is a path on this origin.

**Tasks:**
- Implement the guard (`core/auth/auth.guard.ts`, a functional
  `CanActivateFn` reading `TokenStore.isAuthenticated`) and the redirect;
  land the user where they were going after signing in (pass the intended
  URL through, e.g. as a query param, and have the sign-in screen navigate
  there on success instead of a hard-coded home route).
- Reject a return address that is not a path on this origin — validate it
  parses as a relative path (starts with `/`, not `//`, no scheme/host
  embedded) before ever calling `router.navigateByUrl` with it. This is
  the actual security requirement here (see notes below) — don't skip it
  because "it's just a redirect."
- Do not render an empty screen instead of redirecting — the guard should
  synchronously send the user to sign-in, not leave a blank route while
  something resolves.
- Apply the guard to every route except `ROUTE_PATHS.signIn` in
  `app.routes.ts` (Person 1 left routes unguarded — this is the one root
  file you're expected to edit; keep the diff to just adding
  `canActivate`).

**Unit Test Execution Paths:**
- the guard blocks unauthenticated navigation and redirects
- the guard allows authenticated navigation
- an off-origin return address is refused

**Notes (from the ticket — worth keeping in mind while building):**
- The guard is a usability control, not a security control. The bundle is
  public and authorisation is the Trade REST API's decision, taken on
  every call. Don't over-invest here at the expense of the redirect-safety
  test above, which is the part that's actually load-bearing.
- An open redirect on a trading sign-in page is a phishing kit somebody
  else assembles for free — this is why the origin check is an acceptance
  criterion, not a nice-to-have.

# Definition of done

- `npm run build && npm test` green on your branch.
- Every Unit Test Execution Path above exists as a named test.
- The stable test identifiers exist and are documented for Person 5.
- Sign in manually against the real running Auth service once, end to end
  (not just mocked), before calling this done.
- Commit(s) on `sprint-09-person-2`, message format `SEC4-635/SEC4-637
  <title>` + description, per `CLAUDE.MD`.
- Rebase onto latest `sprint-09` and merge back.
