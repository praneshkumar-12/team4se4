# Sprint 9 — Trading UI (Angular): who builds what

Five independent Claude Code sessions, five branches, one merge order. This
file is the map; `person-1-prompt.md` … `person-5-prompt.md` are what you
paste into each person's Claude Code session (as the first message, in a
fresh session, on their own branch).

## The split

| Person | Tickets | Branch | Bases off |
|---|---|---|---|
| **1 — Foundation** | SEC4-633 (workspace), SEC4-634 (generated clients) | `sprint-09-person-1` | `sprint-09` (cut from `sprint-08`) |
| **2 — Auth + Guards** | SEC4-635 (sign-in), SEC4-637 (route guards) | `sprint-09-person-2` | `sprint-09` *after Person 1 merges* |
| **3 — Interceptor + Errors** | SEC4-636 (bearer interceptor), SEC4-639 (error catalogues) | `sprint-09-person-3` | `sprint-09` *after Person 1 merges* |
| **4 — Order Ticket** | SEC4-638 (order ticket) | `sprint-09-person-4` | `sprint-09` *after Person 1 merges* |
| **5 — Blotter + Playwright + Secret Check** | SEC4-640 (blotter), SEC4-641 (Playwright), SEC4-642 (secret-free bundle) | `sprint-09-person-5` | `sprint-09` *after 2, 3 and 4 all merge* |

This matches the dependency graph exactly: Person 1 is the only hard
prerequisite for 2/3/4, who then run **in parallel** on their own branches
without touching each other's files, and Person 5 closes the sprint because
Playwright needs sign-in *and* order-ticket working end to end, and the
secret scan needs the final production bundle.

## Why Person 1's scope is slightly bigger than "workspace + clients"

SEC4-633/634 only ask for the workspace and the generated clients. But for
2, 3 and 4 to truly work **in parallel without merge conflicts or blocking
on each other**, they need a few tiny shared seams to already exist and
have a stable public shape. So Person 1 also lays down (all thin, all
replaceable by later tickets without changing their public API):

- `core/auth/token-store.ts` — where the token lives (Person 2 writes to
  it, Person 3's interceptor and Person 4's read-only account field read
  from it).
- `core/auth/account-context.ts` — decodes the current account off the
  token (Person 4 needs this and shouldn't have to wait on Person 2's
  sign-in screen).
- `core/errors/error-message.service.ts` — a working but minimal
  placeholder with the method signature Person 3's SEC4-639 will fill in
  and Person 4's SEC4-638 will call. Same class, same method name — Person
  3's merge is a pure enhancement, not a breaking change for Person 4.
- `core/config/routes.const.ts` — the route paths everyone (guard,
  redirects, nav) agrees on, so nobody invents `/login` while someone else
  wrote `/sign-in`.
- Empty standalone placeholder routes/components for `sign-in`,
  `order-ticket` and `blotter` under `features/`, wired into
  `app.routes.ts`, so the app builds and runs before any feature exists.

None of this reaches into any ticket's actual acceptance criteria — it's
the scaffolding that makes "5 people, 5 branches, one merge" actually
possible. Each person's prompt says exactly which of these files they may
extend vs. must not touch.

## Sequencing (matches the sprint's 4 taught days)

1. **Day 1:** Person 1 only. Merge to `sprint-09` before anyone else
   starts — this is the "minimum working foundation."
2. **Day 2–3:** Persons 2, 3, 4 in parallel, each rebasing onto `sprint-09`
   as they finish, merging back as soon as their own build/tests are
   green. Configure Playwright (Person 5's setup step) can start as soon
   as Person 2's sign-in is merged, per the ticket notes.
3. **Day 4:** Person 5, based on `sprint-09` once 2/3/4 are all in.

## Before anyone starts

Person 1's prompt includes locating the two OpenAPI contracts (Auth API,
Trade API) from the requirements repo — **this repo's `contracts/` folder
does not currently contain them**, only `analytics-schema.sql` and
`kafka-topics.md`. If they aren't findable there either, Person 1 stops and
asks rather than inventing a contract shape (per `CLAUDE.MD` rule 4).

Person 1's prompt also covers a real, checked-for-real blocker: neither
`sprint-06-trade-api` (port `8085`) nor `sprint-08-auth-service` (port
`3000`) has any CORS configuration today (confirmed by grepping both
source trees), which will block every browser call from `ng serve`'s
origin regardless of how correct the Angular code is. The fix stays
inside this workspace — an Angular dev-server proxy — rather than editing
either backend service, which is outside this sprint's folder.

One deliberate deviation from the ticket text worth naming: SEC4-641's own
notes describe a *linear* build order ("stand the workspace up and
generate the clients first, then sign-in, then the order ticket, then the
blotter") written for one person working through the tickets in sequence.
This split instead runs 2/3/4 **in parallel** once Person 1's foundation
is in, per the dependency graph this split is built from — a deliberate,
faster reading of the same requirement for a team of five rather than one.

## Common to every prompt

Each person's session should also be pointed at the repo's `CLAUDE.MD` —
scope discipline, TDD, commit message format (`<TICKET-ID> <Title>` +
description line), and "ask, don't assume." Angular 21, standalone
components, signals throughout, no `NgModule`, observables stop at
`HttpClient` (don't leak raw `Observable` subscriptions into components —
convert with `toSignal` or `rxResource` at the edge).

## Design system: ZardUI is compulsory, and the site must not look "vibecoded"

**ZardUI (https://zardui.com/) is the component library for this whole
app — no exceptions.** It's an Angular-native, shadcn-style library
(Signals + Tailwind CSS v4; components are generated into your own repo
via a CLI, not pulled in as an opaque npm dependency). Person 1 installs
and configures it as part of SEC4-633 (see their prompt for the exact
commands); everyone else **uses ZardUI's components for every applicable
UI element** — buttons, inputs, selects, tables, badges, dialogs, tabs,
alerts, skeletons — and adds any component the library has that a screen
needs with `npx zard-cli@latest add <name>`, committing the generated
files. Don't hand-roll a raw `<button>`/`<table>`/`<select>` when a ZardUI
equivalent exists.

**Icons: Lucide, via ZardUI's own `@ng-icons/lucide` setup — used
properly, not just dropped in.** ZardUI's default icon set is Lucide;
keep it, don't fight it. "Used properly" means: one fixed icon-size scale
tied to the text it sits next to (e.g. 16px inline with body text, 20px in
a button/input, 24px standalone — not whatever size an icon happens to
render at by default), consistent stroke width across the app, and every
icon used because it labels a real action or state (a sort direction, a
dropdown's open/closed state, a status) — never a purely decorative icon
bolted onto a card or heading for visual filler. Person 1 sets the size
scale up once (e.g. as Tailwind utility classes or a small wrapper) and
records it in the README so Persons 2–5 apply it consistently rather than
each picking their own icon size.

**The person who gave us this brief also gave us a list of things that
make a site look AI-generated ("vibecoded") and asked us to avoid them.**
This sprint's whole deliverable *is* UI — sign-in, the order ticket, the
blotter — so almost the entire list is a live risk on a real screen
someone is about to build this week, not a hypothetical to wave off.
Only a handful of items are genuinely inapplicable, because they're
marketing-page furniture this app has no page for at all — 3 feature
cards in a row, 3 pricing tiers, fake testimonials, bento grids, a fake
terminal-window mockup — and nobody should add one of those "to make it
feel complete." Everything else below applies directly to the three
screens being built, and each person's own prompt calls out where it
bites on their screen specifically:

- **No pure white background, no purple-and-black, no neon, no weak
  pastel.** Pick a deliberate, domain-appropriate palette: neutral
  grays (not `#fff`) plus one accent that isn't purple, plus a
  colour-blind-safe red/green pairing for gains/losses and order status
  (SEC4-640 already requires the *word* on every badge, not colour alone
  — lean into that rather than fighting it).
- **No Inter/Geist/Space Grotesk.** Pick something else on purpose — a
  trading UI genuinely benefits from tabular/monospaced numerals for
  price and quantity columns (so decimal points align in the blotter and
  order ticket), so a monospace pairing for numeric data plus a distinct
  sans for UI chrome is a legitimate, non-arbitrary choice here, not just
  "something different from the AI-slop trio."
- **No drop shadows, no soft/oversized corner radius, no liquid glass,
  no radial orbs, no dot grids, no colored left stripe, no animated
  arrows, no gratuitous hover animations, no sparkle icons, no emojis.**
  Prefer borders over shadows for separation; pick one deliberate radius
  scale and stick to it; keep motion functional (a state change, a
  loading transition), never decorative.
- **Use real loading states, not a blank flash.** ZardUI's `Skeleton`
  component exists for exactly this — use it while the blotter or order
  ticket is waiting on a response, instead of nothing or a generic
  spinner-only flash.
- **In copy:** no em dashes, no "it is not X, it is Y" construction, no
  checkmark-bullet feature lists, write plainly.

Person 1 records the concrete decisions (font, palette, radius scale,
icon-set resolution) in the sprint README as design tokens; everyone else
consumes them rather than re-deciding per screen.
