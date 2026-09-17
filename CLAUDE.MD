# SE Class 4 — Team 4 (SEC4 / SE04T04) — Trading Platform Capstone

## What this project is
A clone of a trading platform, built incrementally sprint by sprint as part of a
software engineering capstone (LEAP Capstone India). Each sprint has its own
Jira tickets and acceptance criteria; later sprints build on earlier ones.

## Reference repositories — always consult these
- **Requirements repo** (source of truth for every sprint's specs, contracts,
  and folder READMEs): https://github.com/Neueda-Learning/leap-capstone-india/tree/main
  - When any ticket detail is unclear, check that sprint's folder README in
    this repo *before* improvising or asking — but still ask if it remains
    ambiguous after checking.
  - Also check for a `contracts/` folder (schemas, topic names, partition
    counts, API contracts) — tickets frequently say "the contract fixes X."
- **Teaching reference repo** (coding style/concepts taught for that sprint's
  tech stack): `https://github.com/Neueda-Technologies/leap-sprint{N}`
  - e.g. Sprint 6 → https://github.com/Neueda-Technologies/leap-sprint6
  - Match this repo's conventions and idioms rather than inventing a
    different style.

## Hard rules — non-negotiable
1. **Scope discipline.** If I give you one Jira ticket, implement *only* that
   ticket, referencing the requirements repo for supporting detail. Do not
   implement other tickets, other sprints, or "obviously needed later" work
   unless I explicitly ask for it.
2. **Acceptance criteria first.** Meeting the stated acceptance criteria is
   priority #1, always. Innovative/above-and-beyond features are welcome
   *only after* acceptance criteria are fully met, and should be called out
   separately as optional additions (quality over quantity).
3. **Keep it simple.** Do not overcomplicate or over-engineer. Prefer the
   simplest design that satisfies the acceptance criteria and is easy to
   explain to a reviewer.
4. **Ask, don't assume.** Any time there's a decision point or ambiguity
   (naming, schema shape, where a component lives, which of two valid
   readings of a requirement applies, etc.), stop and ask me before writing
   code. Do not silently pick an interpretation.
5. **Tests are part of the ticket, not an afterthought.** Write tests
   TDD-style (test describing behavior exists before/alongside the code that
   satisfies it). Cover every bullet under a ticket's "Unit Test Execution
   Paths" explicitly, and add integration tests where the ticket asks for
   them.

## Branching
- One new branch per sprint, created fresh for that sprint's work.
- A sprint branch contains **only** that sprint's code — no folders/files
  from other sprints living alongside it.
- **Never** commit or push to `main`.
- **Never** push to a branch belonging to a different sprint.
- Merging everything into `main` is a final, separate integration step — not
  something to do proactively without being asked.

## Commit message format
Two Jira numbers, then title, then a short description line(s):
```
<TICKET-ID-1>/<TICKET-ID-2> <Title>
<short description of what changed>
```
Example:
```
ETLEAPCP-1558/SEC4-299 SpringBoot Project and the Engineering Contract
Added springboot files
```

## Domain rules that hold across all sprints
- One order → at most one trade. An order only becomes a trade when it is
  executed (filled).
- No partial fills exist anywhere in the model — there is no intermediate
  "partially filled" state between placement and a terminal status.
- The **executed price lives on the Trade**, not on the Order.
- (Extend this list as new cross-cutting rules surface in later sprints.)

## Sprint history (for context — do not rebuild these, only reference them)
| Sprint | Focus |
|---|---|
| 3 | Database design — ER diagram, schema, migrations, constraints |
| 4 | Analytics ETL pipeline (Python + DuckDB) |
| 5 | Domain model & order placement (Java / Maven) |
| 6 | Order processing & settlement engine |
| Current | Kafka-based trade executor, settlement guards, market-data poller, incremental FACT_TRADES ETL, quality gates (see active ticket set, e.g. SEC4-611 → SEC4-620) |

## Before starting any ticket, walk this checklist
1. Read the full ticket: acceptance criteria, tasks, unit test execution
   paths, integration tests, and notes.
2. Cross-reference the requirements repo's relevant sprint README and any
   `contracts/` files it points to.
3. Check the matching `leap-sprint{N}` teaching repo for expected style.
4. Confirm scope: this ticket only, nothing else.
5. List out anything ambiguous and ask before writing code.

## Testing expectations
- Unit tests should map one-to-one (and be named so it's obvious) to each
  "Unit Test Execution Paths" bullet in the ticket.
- Integration tests are implemented wherever the ticket calls for one (e.g.
  against a mocked external dependency).
- Tests are committed as part of the same change they verify, except where a
  ticket explicitly requires tests to precede a later change (e.g.
  characterisation tests that must be an ancestor commit of the change they
  pin).

## Quality & security (recurring theme — apply proactively even when not
the active ticket)
- Keep code passing lint/static-analysis cleanly; don't introduce new
  blocker/critical issues or unreviewed security hotspots.
- Never commit secrets/tokens — read them from the environment.
- Treat "silencing" a finding (suppressing, marking won't-fix) as not the
  same as fixing it.