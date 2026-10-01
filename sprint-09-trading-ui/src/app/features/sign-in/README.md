# sign-in (SEC4-635)

## Test identifiers (for Person 5's Playwright journeys, SEC4-641)

| Element | `data-testid` |
|---|---|
| Email input | `sign-in-email` |
| Password input | `sign-in-password` |
| Submit button | `sign-in-submit` |

The sign-out control lives in the app shell (`app.ts`), not this feature —
`data-testid="sign-out"`, only rendered while signed in.

## Login field: `email`, not `username` — a known contract/service mismatch

`contracts/auth-api.yaml`'s `LoginRequest` (and the generated
`auth-api` client's `LoginRequest` TypeScript type) says `{ username,
password }`. The real running `sprint-08-auth-service` requires `{ email,
password }` (`src/auth/dto/login.dto.ts`, `@IsEmail()`) and its global
`ValidationPipe` has `forbidNonWhitelisted: true`, so sending the
contract's own shape gets a hard `422`. This is a genuine Sprint 8 change
(commit `7a95af5`, "Register by email instead of accountId") that was
never back-ported into the contract.

Per team decision, `sign-in-page.ts` still calls the generated
`AuthService.login()` (as required), but builds the request body as
`{ email, password }` — cast past the generated type — since that's the
shape the real service actually accepts. The form field is labelled
"Email" accordingly. Fixing `contracts/auth-api.yaml` and regenerating the
client is a follow-up outside SEC4-635/SEC4-637's scope.

## Return address safety (SEC4-637)

A `returnUrl` query param is validated with `core/auth/return-url.ts`'s
`isSafeReturnUrl` before ever being passed to `Router.navigateByUrl` — it
must be a same-origin relative path (starts with `/`, not `//`, no
backslash). An unsafe or missing `returnUrl` falls back to
`ROUTE_PATHS.blotter`.

## Running the integration test

`sign-in.integration.spec.ts` hits the real running auth-service directly
(`http://localhost:3000`, bypassing the dev-server proxy since Vitest runs
outside `ng serve`). It's excluded from `npm test` (see `angular.json`'s
`test.options.exclude`) since it needs a real service up. Bring the stack
up first, then run it separately:

```bash
./run-local.sh start   # from the repo root
npm run test:integration
```

It registers (or reuses, on a 409) the seeded demo user
`arun.kumar@example.com` / `Correct-Horse-Battery-9` (account 1,
ACC-10001 — the only seeded account that's ACTIVE) before logging in.
