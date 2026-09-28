You are working in the repo at the root `G:\team4se4` (or wherever it's
checked out). Read `CLAUDE.MD` at the repo root fully before doing anything
else and follow it: scope discipline, ask-don't-assume, TDD, the commit
message format, and the branching rules.

# Who you are

Person 4 of 5 on Sprint 9 (Team 4 — Trading UI, Angular). **Your ticket:
SEC4-638 only.** Do not touch sign-in, guards, the interceptor, the error
catalogue, the blotter or Playwright config — those are other people's
tickets, landing on parallel branches.

**Prerequisite:** `sprint-09-person-1` must already be merged into
`sprint-09` — confirm `sprint-09-trading-ui/` exists with `core/auth/
account-context.ts`, `core/errors/error-message.service.ts` and
`generated/trade-api/`. If it isn't there yet, stop and tell me.

**Branch:** create `sprint-09-person-4` from `sprint-09` (after Person 1's
merge). When done, rebase onto the latest `sprint-09` and merge back.
Persons 2 and 3 are working in parallel on different files — you should
have close to zero merge conflicts with them; if you hit one, it means
someone touched a file outside their stated scope, so flag it rather than
silently resolving it in a way that guesses at their intent.

## What you're building on

- `generated/trade-api/` has the typed client for the Trade REST API
  (order placement, and its request/response types) — use the generated
  types for instrument, side and status, don't redeclare your own enums
  that can drift from the contract.
- `core/auth/account-context.ts` exposes the current account (decoded from
  the token) as a signal — use it for the read-only account field. Don't
  read the token yourself.
- `core/errors/error-message.service.ts` exists today as Person 1's
  placeholder and will be replaced by Person 3's real implementation
  (SEC4-639) after you've both merged — **you don't need to wait for
  that.** Call `getMessage(errorCode, httpStatus)` now; when Person 3's
  branch merges, your calls automatically start returning the real
  catalogue messages with no change on your side, because the method
  signature doesn't change.
- ZardUI (https://zardui.com/) is set up and compulsory — see `docs/
  prompts/README.md`'s design-system section. Build the ticket from its
  `Select` (instrument, side), `Input` (quantity, price, with visible
  validation-error state), `Card`, `Button` and `Alert`/`Badge` (outcome
  message) components — not raw form elements. Use `Skeleton` (not a
  blank flash or a spinner-only state) while a submission is in flight.
  The outcome message is a plain sentence, not a checkmark-bullet list of
  what happened, and the submit button stays a plain button — no
  gradient, no animated/bouncing arrow, no hover-scale flourish on it.
  Add any missing component with `npx zard-cli@latest add <name>` and
  commit it under `src/app/shared/components/`.

# SEC4-638 — Order Ticket with Client-Side Validation

**Acceptance criteria:**
- The order ticket validates quantity, price and symbol before
  submission and renders the account read-only.
- A submitted order shows the outcome the API returned, including an
  order that comes back at `NEW`.

**Tasks:**
- Build the ticket in `features/order-ticket/` with instrument, side,
  quantity and price inputs. Validate all three before submission: an
  instrument (symbol) and a side must actually be selected — the submit
  control stays disabled/blocked on an empty selection, not just on a bad
  number — a whole quantity above zero, and a price above zero with at
  most two decimal places (client-side, before the request goes out —
  reactive forms with a custom validator for the decimal-places rule is
  the natural fit).
- Populate the instrument and side inputs from the generated Trade API
  client's own enum/type for them — don't hand-type a list of symbols or
  sides that can drift from the contract. (This is also what the epic-
  level acceptance criterion "renders every code in both catalogues"
  means for this screen: every valid instrument/side the contract defines
  should be selectable here.)
- Render the account the token says this session may trade as **read-
  only** (from `AccountContext`), because an account field the user can
  edit is an authorisation decision moved into the browser. Don't make it
  an editable input that happens to be disabled by default — it shouldn't
  be submittable as a different value under any code path.
- Show a success or error message from the response, via
  `ErrorMessageService.getMessage()` for the error path — don't render the
  raw contract error code or the raw HTTP body to the trader.
- An order can legitimately come back at `NEW` (execution is
  asynchronous since Sprint 7) — this is normal, not a defect in the
  executor, and your success path must render it as a real outcome
  ("order submitted, working"), not treat anything other than `FILLED` as
  an error.

**Unit Test Execution Paths:**
- a valid order submits and shows the returned status
- an invalid quantity is blocked before submission
- a business-rule rejection shows the mapped message

**Notes:**
- Client-side validation is not enforcement. The business rules live in
  the Trade REST API and stay there; this form exists so the obvious
  mistakes never reach the wire — don't try to replicate the API's full
  business-rule set here, that's explicitly out of scope.

# Definition of done

- `npm run build && npm test` green on your branch.
- Every Unit Test Execution Path above exists as a named test, including
  one asserting a `NEW` response renders as success, not an error.
- The account field is genuinely non-submittable-as-edited, not just
  visually disabled.
- Instrument and side options come from the generated client's types, not
  a hand-written list.
- Commit(s) on `sprint-09-person-4`, message format `SEC4-638 <title>` +
  description, per `CLAUDE.MD`.
- Rebase onto latest `sprint-09` and merge back.
