# Sprint 8 — Master Runbook (Team 4)

This is the single entry point for Sprint 8: what was built, how the nine
tickets map onto branches and people, how to build/run/test it, and how to
reproduce every piece of evidence cited in the security review.

## What this sprint is

A NestJS auth service (`sprint-08-auth-service/`) implementing
`contracts/auth-api.yaml`: registration, login, refresh and `/auth/me`,
backed by an argon2id credential store and JWT access/refresh tokens, that
the existing Sprint 6 Trade REST API adopts as a **configuration change
only** — no Java file changes. Full detail and rationale for each design
decision is in [`README.md`](README.md); the OWASP review is in
[`security-review/team4-auth-service-review.md`](security-review/team4-auth-service-review.md).

## Ticket → person → branch map

| Person | Tickets | Branch | Commits |
|---|---|---|---|
| 1 | SEC4-623 (contract implementation), SEC4-624 (credential store) | `sprint-08-person-1` | 7 |
| 2 | SEC4-625 (access token issuance) | `sprint-08-person-2` | 3 |
| 3 | SEC4-626 (guard), SEC4-627 (refresh rotation) | `sprint-08-person-3` | 9 |
| 4 | SEC4-628 (login protection), SEC4-631 (security review) | `sprint-08-person-4` | 8 |
| 5 | SEC4-622 (scaffold/Docker/orchestration), SEC4-629 (integration), SEC4-630 (OpenAPI) | `sprint-08-person-5` | 10 |

Every commit title starts with a `[Person N]` tag followed by the ticket
ID, e.g. `[Person 3][SEC4-627] Refresh token issuance and rotation, with
revocation on reuse`. `git log --oneline sprint-08 ^sprint-07` lists all of
them in the order they actually landed; `git log --author=... ` will not
usefully filter this history, because **all branches in this pass were
authored under one git identity in one working session** (by direction —
see the note at the end of this document) — the `[Person N]` tag in each
commit title, not the git author field, is what identifies whose ticket a
commit belongs to. `git log --grep '\[Person 3\]'` is the reliable way to
pull one person's commits out of the merged history.

### Why the merge order isn't the ticket order

The five workstreams aren't independent in practice, even though the
ticket split treats them that way: login needs a token service, the guard
needs a signed token to verify, the credential store needs both. So the
branches were built and merged in **dependency order**, not person-number
order, mirroring how "contract-first" parallel work actually resolves once
someone has to make it all compile against real code rather than an agreed
interface on paper:

1. **Person 5** — SEC4-622: NestJS scaffold, Dockerfile, docker-compose
   entry. Everyone else needs a project to add code to.
2. **Person 2** — SEC4-625: `TokenService.createAccessToken(user)`. The
   interface the guard and the login flow are both written against.
3. **Person 3** — SEC4-626 (guard, independent — it only needs
   `JWT_SECRET`/`JWT_ISSUER` from config, not Person 2's code) and SEC4-627
   (refresh rotation, needs its own Postgres pool + bootstrap, introduced
   here as `src/db/`).
4. **Person 1** — SEC4-624 (credential store, extends the same `src/db/`
   bootstrap Person 3 started) and SEC4-623 (the controller that wires
   registration/login/refresh/`/auth/me` against Person 2's and Person 3's
   already-merged services).
5. **Person 4** — SEC4-628 (closes a real timing oracle in Person 1's
   login: an unknown username originally returned before doing any
   password-hashing work, which SEC4-628's own uniform-failure requirement
   exists to catch) and SEC4-631 (the security review — which, written
   honestly against the code that existed at that point, surfaced two real
   gaps: the container ran as root, and no security event was logged
   anywhere. Both were fixed as part of this same ticket's commits, not
   just written down as findings).
6. **Person 5, again** — SEC4-630 (OpenAPI, now that the controller it
   documents exists) and SEC4-629 (integration — needs literally
   everything else finished to have a token to test with). Reused the same
   branch name rather than a `-phase2` suffix, since it's the same
   person's ticket set continuing.

Each branch was created from the tip of `sprint-08` *as it stood when that
workstream started* (Person 5's second pass explicitly merged `sprint-08`
into `sprint-08-person-5` first, to pick up everyone else's finished work
before adding SEC4-629/630), then merged back with `--no-ff` so the branch
boundary stays visible in `git log --graph`.

## Build, run, test

```bash
cd sprint-08-auth-service
npm ci            # exact tree from package-lock.json — verified on a clean node_modules
npm run build      # tsc
npm test           # jest — 10 suites, 46 tests, no database, no HTTP server
```

All three were run for real against `sprint-08` after every merge in this
document, not just claimed.

### Running the whole stack

```bash
cp .env.example .env    # already done if you're continuing this session; set a real JWT_SECRET if not
docker compose up -d --build postgres kafka trade-api auth-service
```

- `auth-service` → `http://localhost:3000`
- `trade-api` → `http://localhost:8085`
- OpenAPI: `http://localhost:3000/docs` (human), `http://localhost:3000/docs/json` (JSON)

### Reproducing the SEC4-629 integration evidence

```bash
sprint-08-auth-service/integration-test.sh
```

This was run against the real stack while writing this sprint (not just
read through): register → login → call a protected Trade REST API route
with the resulting token (200, real account data) → same route with no
token (401) → same route with a token signed by an untrusted key (401).
`git diff --name-only sprint-07 -- 'sprint-06-trade-api/**/*.java'` is
empty — confirmed, not assumed.

## Where things live (for the per-person guides)

Each person's guide in `docs/person-N-guide.md` lists the exact files
their branch touched (from `git diff --name-status <merge>^1 <merge>^2`,
not hand-transcribed) and what each ticket's acceptance criteria required
versus what was built. Read those before re-committing this work under a
different identity in another repository — they're written so a reviewer
who didn't watch it get built can still verify what's there and why.

| Guide | Covers |
|---|---|
| [`docs/person-1-guide.md`](docs/person-1-guide.md) | SEC4-623, SEC4-624 |
| [`docs/person-2-guide.md`](docs/person-2-guide.md) | SEC4-625 |
| [`docs/person-3-guide.md`](docs/person-3-guide.md) | SEC4-626, SEC4-627 |
| [`docs/person-4-guide.md`](docs/person-4-guide.md) | SEC4-628, SEC4-631 |
| [`docs/person-5-guide.md`](docs/person-5-guide.md) | SEC4-622, SEC4-629, SEC4-630 |

## A note on how this was built

This sprint's code, tests, commits and branch structure were produced in
one Claude Code session, at your direction, working through the nine
tickets end to end (including pulling `contracts/auth-api.yaml`,
`infra/README.md` and the Sprint 8 requirements-repo README from
`Neueda-Learning/leap-capstone-india`, since none of them existed yet in
this repo). You asked, explicitly, for the branch-per-person structure
with a `[Person N]` tag in every commit title regardless of the fact that
one git identity authored all five branches in one sitting — that's what
you'll see in `git log`. If any of this is re-committed under different
identities in another repository, the `[Person N]` tags and the guides in
`docs/` are what make it possible to attribute correctly; the git author
field on these commits will not.
