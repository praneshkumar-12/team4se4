You are working in the repo at the root `G:\team4se4` (or wherever it's
checked out). Read `CLAUDE.MD` at the repo root fully before doing anything
else and follow it: scope discipline, ask-don't-assume, TDD, the commit
message format, and the branching rules.

# Who you are

Person 1 of 5 on Sprint 9 (Team 4 — Trading UI, Angular). You are the
**foundation**. Nobody else on the team can start until your branch merges,
so the acceptance bar here is not just "my two tickets pass" but "the
workspace actually builds, runs and routes, so four other people can build
independent features on top of it without touching my files again."

**Your tickets: SEC4-633 and SEC4-634 only.** Do not touch auth, guards,
the interceptor, the order ticket, the blotter or Playwright — those are
other people's tickets, arriving as parallel branches after yours merges.

**Branch:** create `sprint-09` from the current `sprint-08` branch (do not
branch from `main` — `sprint-08` has 160 commits `main` doesn't yet, and is
where this team's work actually lives). Then create `sprint-09-person-1`
from `sprint-09`. All your commits go on `sprint-09-person-1`; when done,
merge it into `sprint-09` (fast-forward or a merge commit — either is fine,
this is an internal integration branch, not `main`) so Persons 2–4 have
something to branch from.

## Step 0 — a real blocker to know about: neither backend has CORS configured

`sprint-06-trade-api` (port `8085`, confirmed in the repo root
`docker-compose.yml`) and `sprint-08-auth-service` (port `3000`, same
file) currently have **no CORS configuration** — a grep of both source
trees for `cors`/`Cors`/`origin` turns up nothing relevant. A browser
calling either from `ng serve`'s origin (`localhost:4200` by default) will
be blocked by the browser itself, no matter how correct your Angular code
is — this is the literal "a cross-origin rule that does not allow your
development server" scenario SEC4-639 names.

Fix it **inside this workspace, not by touching those services** —
sprint boundaries mean editing `sprint-06-trade-api` or
`sprint-08-auth-service` isn't yours to do without asking, and it isn't
needed here anyway: set up an Angular dev-server proxy
(`proxy.conf.json`, wired as the default in `angular.json`'s `serve`
options so `npm start` / `ng serve` just uses it) that forwards e.g.
`/auth/*` → `http://localhost:3000` and `/trade/*` (or whatever prefix
you pick) → `http://localhost:8085`. Point the generated clients'
`basePath`/`Configuration` at those same relative prefixes via
`environment.apiBaseUrls`, so in dev the browser only ever sees same-
origin requests. This doesn't change what the SEC4-636 interceptor is
actually deciding — its allow-list unit tests construct requests directly
and aren't affected by how the browser reaches a server in practice.

If this turns out not to cover every case you hit (for instance, if
Playwright ends up running against a built production bundle served
outside `ng serve`, where the proxy doesn't apply), stop and ask before
adding CORS headers to either backend service yourself — that's a
cross-sprint change and worth a real decision, not an assumption.

Bring the real backends up with the repo's own tooling rather than
reinventing it: `./run-local.sh start` from the repo root (see
`run-local-steps.md`) brings up Postgres, `trade-api` and `auth-service`
locally. Use this — and its `status`/`stop` companions — for your own
verification and tell Persons 2 and 5 to do the same.

## Step 1 — find the contracts before doing anything else

This repo's root `contracts/` folder currently holds only
`analytics-schema.sql` and `kafka-topics.md` — **the Auth API and Trade API
OpenAPI contracts are not in it.** `CLAUDE.MD` says the requirements repo
(`https://github.com/Neueda-Learning/leap-capstone-india`) is the source of
truth and to check its `contracts/` folder. Find the Auth API contract and
the Trade REST API contract there (a shallow clone or browsing the GitHub
tree both work), copy them into this repo's root `contracts/` (e.g.
`contracts/auth-api.yaml`, `contracts/trade-api.yaml` — use whatever names
and formats they actually ship as), and commit that as its own small first
commit. **If you cannot find both contracts there, stop and ask me rather
than inventing endpoints, schemas or error codes.** Everyone downstream
(typed clients, error catalogues, the order ticket) is only correct if
these are the real contracts.

# SEC4-633 — Angular Workspace and the Engineering Contract

**Acceptance criteria:**
- `npm ci`, `npm run build` and `npm test` all succeed on a machine that
  has never seen your code, from a committed lock file.
- The workspace uses standalone components and signals, with specs beside
  the code they cover.

**Tasks:**
- Set one Angular 21 workspace up in `sprint-09-trading-ui/` (sibling to
  `sprint-06-trade-api`, `sprint-08-auth-service`) on a supported, pinned
  Node version. Record the Node version in `package.json`'s `engines`
  field and in the sprint README.
- Decide the feature tree yourself and record its shape in
  `sprint-09-trading-ui/README.md`, so a reviewer can find a feature
  without searching the workspace. Use (or adapt and document if you
  genuinely think better of it — then say why in the README):

  ```
  sprint-09-trading-ui/
    src/app/
      core/
        config/          # environment + route-path constants
        auth/            # token store, account context (this ticket)
        errors/          # error-message service placeholder (this ticket)
        http/             # (Person 3 adds the interceptor here)
      generated/
        auth-api/        # SEC4-634 output, committed
        trade-api/       # SEC4-634 output, committed
      shared/
        components/      # ZardUI-generated components (`zard-cli add`), committed
        utils/            # ZardUI's cn() helper etc.
        core/              # ZardUI's own directives/providers, per its setup
      features/
        sign-in/          # placeholder now, Person 2 fills in
        order-ticket/      # placeholder now, Person 4 fills in
        blotter/           # placeholder now, Person 5 fills in
      app.routes.ts
      app.config.ts
    docs/prompts/          # these five prompt files — keep them
  ```
- No `NgModule` anywhere. Observables stop at `HttpClient` — don't design
  anything that forces a later feature to hand-roll a subscription in a
  component when a signal would do.
- Verify the clean-machine claim for real: `rm -rf node_modules dist`,
  then `npm ci && npm run build && npm test`.

## Install and configure ZardUI (compulsory for this whole app — see
`docs/prompts/README.md`'s design-system section for why)

ZardUI (https://zardui.com/) is an Angular-native, shadcn-style component
library — Signals + Tailwind CSS v4, components generated into your own
repo via a CLI rather than pulled in as an opaque dependency. Set it up as
part of the workspace, not as an afterthought:

```bash
ng new sprint-09-trading-ui --style=tailwind   # scaffold with Tailwind from the start
cd sprint-09-trading-ui
npx zard-cli@latest init                        # pick "Angular" when prompted
```

If the interactive init doesn't behave in this environment, do it by hand:
install `@angular/cdk class-variance-authority clsx tailwind-merge
@ng-icons/core` (+ an icon set — see below) and dev deps `tailwindcss
@tailwindcss/postcss postcss tailwindcss-animate`; add a `.postcssrc.json`
pointing at `@tailwindcss/postcss`; add the `@/*` → `./src/app/*` path
alias to `tsconfig.json`; add a `components.json` at the workspace root
(`$schema: https://zardui.com/schema.json`, `style: "css"`,
`projectType: "angular"`, `appConfigFile: "src/app/app.config.ts"`,
`baseUrl: "src/app"`, aliases pointing `components` at
`@/shared/components` and `utils` at `@/shared/utils`); wire
`provideZard()` into `app.config.ts`'s providers.

**Icons:** keep ZardUI's default — `@ng-icons/lucide`,
`components.json`'s `"icons"` field left at `"lucide"`. Don't swap it.
What matters is scale and restraint, not the set: set up one fixed
icon-size scale tied to context (e.g. 16px inline with body text, 20px
inside a button/input, 24px standalone — as Tailwind utility classes or a
tiny wrapper component, whichever is less friction), consistent stroke
width, and use icons only where they label a real action or state (sort
direction, a dropdown's open/closed state, a connection/loading state) —
never as decoration bolted onto a card or heading. Record the size scale
in the sprint README so Persons 2–5 apply it consistently instead of each
picking their own `[iconSize]` per usage.

Add a sane baseline of components everyone will need —
`npx zard-cli@latest add button input card badge table dialog select tabs
checkbox skeleton spinner separator alert` (drop any that error because
the library doesn't have them yet, add more later as needed) — and commit
the generated files under `src/app/shared/components/`.

**Design tokens — decide these and record them in
`sprint-09-trading-ui/README.md` so nobody downstream re-decides per
screen (see `docs/prompts/README.md` for what to avoid and why):**
- **Palette:** neutral grays, not pure white (`#fff`); one accent that
  isn't purple; a colour-blind-safe red/green pair for gains/losses and
  order status (badges still carry the word too, per SEC4-640). Set these
  as CSS variables ZardUI's `style: "css"` components already read from —
  don't hand-roll a second theming mechanism.
- **Fonts:** something other than Inter, Geist or Space Grotesk. A
  monospace/tabular-numeral face for price and quantity columns (so
  decimals align in the blotter and order ticket) plus a distinct sans
  for UI chrome is a good, genuinely-motivated pick here.
- **Corner radius:** pick one scale and apply it consistently via the
  theme tokens, not Tailwind's default `rounded-2xl`-everywhere look.
- **Shadows:** prefer borders over drop shadows for separating surfaces.

# SEC4-634 — Typed Clients Generated from the Contracts

**Acceptance criteria:**
- Clients for both contracts are generated rather than hand-written, with
  the generator and its version pinned in the declared configuration.
- The generated output is committed, regenerated on every contract
  change, and contains no file the generator did not write.
- Something outside the generated tree imports the clients.

**Tasks:**
- Use `@openapitools/openapi-generator-cli` with the `typescript-angular`
  generator (produces `HttpClient`-based services returning `Observable`,
  which fits "observables stop at `HttpClient`" — later code converts at
  the edge). Check `npm view @openapitools/openapi-generator-cli version`
  and pin the **exact** resolved version (no `^`/`~`) in `package.json`,
  and pin the underlying generator's version too, in whichever config file
  the CLI reads (`openapitools.json` is the standard place). If Java isn't
  available in this environment for the JAR-based generator, fall back to
  `ng-openapi-gen` (pure TypeScript, no JVM) instead and say so in the
  README — don't silently fail or hand-write the clients.
- Declare one generation entry per contract, each writing into its own
  subdirectory: `src/app/generated/auth-api/` and
  `src/app/generated/trade-api/`. Add both as `npm run generate:clients`
  (or two scripts) in `package.json`.
- Commit the generated output. Do **not** add the generated tree to
  `.gitignore` — the ticket requires it committed — but do note in
  `.gitignore` (with a comment) that it's generator output, regenerated on
  every contract change, so nobody hand-edits it later.
- Document the exact generation command in
  `sprint-09-trading-ui/README.md` (or a small
  `sprint-09-trading-ui/docs/generated-clients.md` if that reads better).
- If a contract fails strict generator validation for a reason you've
  checked is a real issue in the contract text (not a mistake on your
  side), you may switch that specific validation off — but say exactly
  which flag and why in the README. The contracts are correct and not
  yours to edit.
- Something outside `generated/` must import the clients — a thin
  `core/http/` re-export or a one-line smoke usage in `app.config.ts` is
  enough to satisfy this without doing feature work that isn't yours.
- **Never edit a file inside `generated/`, ever, including to fix an
  awkward shape.** If the generator produces something painful to consume
  (an overly generic response type, a method signature that doesn't fit
  Angular idiom), wrap it in a thin service of your own outside the tree
  instead — that wrapper is what the rest of the app imports.

# The shared foundation (small, deliberate scope creep — see the sprint
README at `docs/prompts/README.md` for why)

Build these six small pieces so 2/3/4 can start in parallel without
touching your files again. Keep every one of them genuinely small — a full
feature implementation is the next person's ticket, not yours.

1. **`core/config/routes.const.ts`** — exported `ROUTE_PATHS` object,
   e.g. `{ signIn: '/sign-in', orderTicket: '/orders/new', blotter:
   '/blotter' }`. This is the one place route strings are allowed to be
   literals; everyone else imports this constant.
2. **`core/config/environment.ts`** (+ `.production.ts` if the CLI scaffold
   wants both) — `apiBaseUrls: { auth: ..., trade: ... }` and
   `allowedOrigins: string[]` (the origins the bearer-token interceptor,
   Person 3's ticket, is allowed to attach the token to — you're only
   declaring the config surface here, not the interceptor itself).
   **No secrets, no API keys, no Fauxnance host in this file or anywhere
   else** — that's SEC4-642's check but don't hand Person 5 an easy fail.
3. **`core/auth/token-store.ts`** — a real, working `@Injectable`
   `TokenStore`: a `WritableSignal<string | null>` for the token, plus
   `isAuthenticated` as a `computed` signal, `setToken(token: string):
   void` and `clearToken(): void`. In-memory is fine (Person 2's sign-in
   ticket owns *when* to call these, not where the token physically
   lives — but say in a comment that an in-memory-only store means a page
   refresh signs the user out, and that's an intentional simplicity call
   unless Person 2 tells you otherwise).
4. **`core/auth/account-context.ts`** — `AccountContext` `@Injectable`
   exposing a `computed` signal for the current account id/claims, derived
   from `TokenStore`'s token by decoding the JWT payload (base64, no
   signature verification — the browser can't verify it anyway, and
   verification isn't its job; the Trade API verifies on every call, per
   the epic's security note). Returns `null` when there's no token.
5. **`core/errors/error-message.service.ts`** — `ErrorMessageService`
   `@Injectable` with **exactly this public shape**, since Person 3
   replaces the internals in SEC4-639 without changing callers:
   `getMessage(errorCode: string | null, httpStatus: number): string`.
   Placeholder implementation: return a generic readable sentence (e.g.
   "Something went wrong talking to the trading platform. Try again.") for
   anything, and a distinct sentence for `httpStatus === 0` (service
   unreachable / CORS — SEC4-639 calls this out explicitly). Write one
   spec proving the placeholder never throws and always returns a
   non-empty string, so Person 4 can build against it today.
6. **Placeholder routes/components** — one minimal standalone component
   each for `features/sign-in`, `features/order-ticket`, `features/
   blotter` (a ZardUI `Card` with the feature's name is enough — this
   also doubles as your first proof that ZardUI is actually wired up
   correctly), wired into `app.routes.ts` under the paths from
   `routes.const.ts`, lazy-loaded. This is what makes `npm start` show a
   working, navigable, empty app — the thing Persons 2–4 branch from and
   fill in one route at a time.

# Definition of done

- `rm -rf node_modules dist && npm ci && npm run build && npm test` all
  green.
- `npm start` serves the app; all three placeholder routes render and are
  reachable.
- `sprint-09-trading-ui/README.md` documents: the feature tree, the Node
  version, the generation command, the six shared-foundation pieces
  above (one line each, pointing at the file), and the design tokens
  (palette, fonts, radius, the Lucide icon-size scale) so Persons 2–5 know
  what exists without reading your diff.
- The three placeholder routes visibly render as ZardUI components (not
  bare HTML) — this is your own smoke test that the library is correctly
  wired before four other people start depending on it.
- Commit(s) on `sprint-09-person-1`, message format `SEC4-633/SEC4-634
  <title>` + description, per `CLAUDE.MD`.
- Merge `sprint-09-person-1` into `sprint-09` and confirm the merged branch
  still builds/tests clean. Tell me when this is done — the other four
  people are waiting on it.
