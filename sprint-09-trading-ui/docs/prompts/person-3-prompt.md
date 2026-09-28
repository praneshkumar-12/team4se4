You are working in the repo at the root `G:\team4se4` (or wherever it's
checked out). Read `CLAUDE.MD` at the repo root fully before doing anything
else and follow it: scope discipline, ask-don't-assume, TDD, the commit
message format, and the branching rules.

# Who you are

Person 3 of 5 on Sprint 9 (Team 4 — Trading UI, Angular). **Your tickets:
SEC4-636 and SEC4-639 only.** Do not touch sign-in, guards, the order
ticket, the blotter or Playwright config — those are other people's
tickets, landing on parallel branches. Your two tickets are the sprint's
security-focused ones — the epic names them explicitly (bearer token
scoping by origin, an allow list rather than a deny list — OWASP A01/A03/
A05) — so treat the notes below as load-bearing, not decoration.

**Prerequisite:** `sprint-09-person-1` must already be merged into
`sprint-09` — confirm `sprint-09-trading-ui/` exists with `core/config/
environment.ts` (has `allowedOrigins`), `core/errors/
error-message.service.ts` (placeholder) and both generated clients under
`generated/`. If it isn't there yet, stop and tell me.

**Branch:** create `sprint-09-person-3` from `sprint-09` (after Person 1's
merge). When done, rebase onto the latest `sprint-09` and merge back.

## What you're building on

- `core/config/environment.ts` exports `allowedOrigins: string[]` — this
  is your allow list's source; don't hard-code origins a second time
  inside the interceptor.
- `core/errors/error-message.service.ts` already exists with the public
  method `getMessage(errorCode: string | null, httpStatus: number):
  string` and a placeholder body. **Keep that exact method name and
  signature** — Person 4 (order ticket) and Person 5 (blotter) are already
  calling it. Replace the *implementation*, not the *shape*.
- `generated/auth-api/` and `generated/trade-api/` hold the typed clients
  — read the error codes for your catalogue mapping out of these (or out
  of the source contract files in `contracts/`, whichever has the
  authoritative list of codes — check both, they should agree).
- ZardUI (https://zardui.com/) is set up and compulsory — see `docs/
  prompts/README.md`'s design-system section. Wherever the mapped message
  actually surfaces in the UI (this ticket owns the mapping function, not
  necessarily every place it's displayed — coordinate with Persons 4/5 on
  where they render it), prefer ZardUI's `Alert` or `Sonner` (toast) over
  a hand-rolled error box — no emoji or sparkle icon on it, no
  colored-left-stripe treatment layered on top of ZardUI's own styling,
  and write the sentence itself plainly (no em dash, no "it is not X, it
  is Y" construction). If you add a component, use `npx zard-cli@latest
  add <name>` and commit it under `src/app/shared/components/`.

# SEC4-636 — The Bearer Token Interceptor

**Acceptance criteria:**
- One functional interceptor, registered once, is the only place in the
  application that sets an authorisation header.
- The token is attached to platform API calls and to nothing else, decided
  by comparing the outgoing URL against the origins you configured.
- Two named unit tests cover the attach case and the do-not-attach case.

**Tasks:**
- Build `core/http/bearer-token.interceptor.ts` as a functional
  interceptor (`HttpInterceptorFn`). Register it **once**, in
  `app.config.ts`, via `provideHttpClient(withInterceptors([...]))` — this
  is the one root file you're expected to edit; keep the diff to that one
  line/import. Grep the rest of the codebase before you finish to confirm
  no service sets an `Authorization` header anywhere else.
- Attach the token to the Trade REST API and the protected Auth route
  (`/auth/me` or equivalent), and *not* to the unauthenticated auth routes
  (register/login/refresh), which take no header.
- Use `environment.allowedOrigins` as an **allow list** — compare the
  outgoing request's origin against it and only attach the header on a
  match. Do not write a deny list of hosts you happened to think of.

**Unit Test Execution Paths:**
- a request to a platform API carries the bearer token
- a request to a third-party origin does not carry the bearer token
- the unauthenticated auth routes are called without a header

**Notes:**
- An interceptor that adds the header to every outbound request hands a
  live session token to whatever host that request was going to, where it
  lands in an access log, an analytics pipeline and an error tracker. This
  is the actual failure mode the allow-list requirement exists to prevent
  — test it with a request to something that isn't in the allow list, not
  just "a request to Google."
- An allow list fails closed when somebody adds a new third party. A deny
  list fails open, silently. If you're tempted to write `!isKnownBadHost`
  instead of `isKnownGoodOrigin`, that's the ticket telling you to stop.

# SEC4-639 — Render Every Code in the Error Catalogues

**Acceptance criteria:**
- Every error code in both contracts renders as a readable message, and
  the application branches on the code rather than on the message.
- A response the browser never received and a code the application has
  never seen both render a sentence rather than a blank panel.

**Tasks:**
- Read the error codes out of both contracts (`contracts/auth-api.yaml`
  and `contracts/trade-api.yaml`, or whatever Person 1 named them — check
  their README) and build the mapping as **a mapping with a completeness
  property**, not a switch statement extended reactively later. In
  practice: derive (or hand-maintain, checked against the contract) a
  union type of every error code, and write the map as
  `const MESSAGES: Record<ErrorCode, string> = { ... }` so TypeScript
  itself refuses to compile if a code is missing — that's the
  "completeness property," not a runtime test alone.
- Write sentences a trader can act on, not the developer-facing message
  from the contract (e.g. not `"INSUFFICIENT_BALANCE"` verbatim — say what
  that means for placing this order).
- Handle `httpStatus === 0` explicitly — that's usually the service being
  down or a CORS rule that doesn't allow the dev server; say that, not
  "Unknown error."
- Wire this into `ErrorMessageService.getMessage()` (the file Person 1
  scaffolded) — replace its placeholder body, keep its signature.

**Unit Test Execution Paths:**
- every code in both catalogues maps to a message
- an unrecognised code falls back to a readable sentence
- a request that never reached a service renders a readable sentence

# Definition of done

- `npm run build && npm test` green on your branch.
- The two named interceptor tests (attach / do-not-attach) plus the
  unauthenticated-routes test all exist and pass.
- Every error code in both contracts has a message, and the compiler (not
  just a test) would catch a missing one.
- Grep confirms `Authorization` (or whatever header name you used) is set
  in exactly one file in the app.
- Commit(s) on `sprint-09-person-3`, message format `SEC4-636/SEC4-639
  <title>` + description, per `CLAUDE.MD`.
- Rebase onto latest `sprint-09` and merge back.
