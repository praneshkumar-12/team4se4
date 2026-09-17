# Sprint 7 "Event Backbone" — Master Plan (5-person parallel split)

SEC4-611 → SEC4-620, split 2 tickets per person across 5 people, each on their
own branch off `sprint-06`, merged into `sprint-07-event-backbone` by one
integration master at the end.

This master plan is the shared source of truth all 5 prompt files are built
from. Read it once; it explains *why* the prompts say what they say,
especially the shared-contract section, which exists so five people working
blind to each other's code still produce mergeable code.

---

## 1. Assignment

| Member | Branch | Tickets | Owns |
|---|---|---|---|
| M1 | `sprint-07-m1-kafka-rest` | SEC4-611, SEC4-613 | Kafka topics + envelope, Trade API async publish |
| M2 | `sprint-07-m2-executor-core` | SEC4-612, SEC4-614 | Characterisation tests, Trade Executor core (consume/price/decide) |
| M3 | `sprint-07-m3-settlement` | SEC4-615, SEC4-616 | Settlement transaction + guards, duplicate-delivery demo |
| M4 | `sprint-07-m4-reliability` | SEC4-617, SEC4-618 | Dead letters/retry, market-data poller |
| M5 | `sprint-07-m5-etl-quality` | SEC4-619, SEC4-620 | FACT_TRADES incremental ETL, SonarQube/DevSecOps gate |

One of these five (or a 6th person) is the **integration master**: they don't
own tickets, they own `sprint-07-event-backbone` and the merge below.

All five branches are cut from the current tip of `sprint-06`, **today**, and
worked on in parallel with no expectation of waiting on each other. Conflicts
are resolved at merge time, not avoided up front — that's what Section 4 is
for.

---

## 2. Why this split works (and where it doesn't, cleanly)

- **M1 (611+613) and M2 (612+614) both touch `sprint-05-domain-engine`**, but
  in different packages (`org.leap.domain` vs `org.leap.events`/`org.leap.pricing`)
  — low conflict.
- **M2's SEC4-612 must never touch `OrderLogic.java`/`OrderService.java`** —
  it only adds new test files. M1's SEC4-613 is the one that changes those
  two files. This keeps them file-disjoint, but there's a *git-history*
  requirement (not just a file requirement): SEC4-612's acceptance criterion
  is "the first commit adding a characterisation test file is a proper
  ancestor of the first commit that changes Sprint 6 sources." Two people
  branching independently off `sprint-06` cannot satisfy this by accident —
  it's fixed at merge time by **rebasing M1's branch onto the post-M2 base**
  before merging (Section 4, step 2). This is the single most important
  integration step in this plan — don't skip it.
- **M2, M3, M4 all add code to the same new `executor/` Maven module.**
  M2 creates it from scratch (SEC4-614). M3 (SEC4-615) and M4 (SEC4-617/618)
  need it to exist to add their pieces, but they're starting in parallel
  before M2's module exists in their own branch. Section 3 gives everyone
  the *exact same* interface signatures to code against, so:
  - M3 and M4 each scaffold their own minimal `executor/pom.xml` + package
    skeleton if theirs doesn't exist yet (duplicated, thrown away at merge —
    their real deliverable is the classes, not the pom.xml).
  - Classes that only one member owns (e.g. `SettlementService` impl =
    M3, `DeadLetterPublisher` impl = M4) won't conflict.
  - Classes more than one member touches (`OrderEventConsumer`,
    `AccountRepository`) are flagged explicitly in Section 4 as expected
    small conflicts — the integration master combines the method additions
    by hand. This is normal at this scale; don't try to engineer it away.
- **M5 (619+620) is the most independent** — `etl/` is a separate Python
  project nobody else touches, and 620's Sonar config touches
  `executor/pom.xml` and a new `etl/sonar-project.properties`, so it merges
  last, after everyone else's code exists to scan.

---

## 3. Shared contract — use these exact signatures

Every prompt below embeds the parts of this it needs. It's collected here
once so there's one place to check if something drifts. **Do not improvise
different field names/types for these — if two people's independent copies
of the same class don't match byte-for-byte, that's a real merge conflict
instead of a trivial one.**

### 3.1 Kafka topics (from `contracts/kafka-topics.md`, external requirements repo)

| Topic | Key | Partitions | Retention |
|---|---|---|---|
| `orders` | `accountId` | 3 | 7 days |
| `trade-events` | `accountId` | 3 | 30 days |
| `market-data` | `symbol` | 6 | 1 day |
| `orders.DLT`, `trade-events.DLT`, `market-data.DLT` | same as parent | same as parent | same as parent |

Envelope (every message, all 3 topics): `eventId` (UUID, idempotency key),
`eventType`, `eventTime` (RFC3339 UTC), `source` (`trade-api` \|
`trade-executor` \| `market-poller`), `schemaVersion` (int, starts at 1).

Consumer group for every Trade Executor instance: **`trade-executor`**
(fixed by the contract — do not make this configurable per-instance).

```java
// sprint-05-domain-engine/src/main/java/org/leap/events/EventEnvelope.java
package org.leap.events;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        Instant eventTime,
        String source,
        int schemaVersion,
        T payload
) {
    public static <T> EventEnvelope<T> of(String eventType, String source, T payload) {
        return new EventEnvelope<>(UUID.randomUUID().toString(), eventType, Instant.now(), source, 1, payload);
    }
}
```

```java
// sprint-05-domain-engine/src/main/java/org/leap/events/Topics.java
package org.leap.events;

public final class Topics {
    public static final String ORDERS = "orders";
    public static final String TRADE_EVENTS = "trade-events";
    public static final String MARKET_DATA = "market-data";
    public static final String ORDERS_DLT = "orders.DLT";
    public static final String TRADE_EVENTS_DLT = "trade-events.DLT";
    public static final String MARKET_DATA_DLT = "market-data.DLT";
    public static final String EXECUTOR_CONSUMER_GROUP = "trade-executor";
    private Topics() {}
}
```

### 3.2 Pricing types (org.leap.pricing, sprint-05-domain-engine)

```java
// Quote.java — used by both the executor and the poller
package org.leap.pricing;

import java.math.BigDecimal;

public record Quote(String symbol, BigDecimal bid, BigDecimal ask, BigDecimal price) {}
```

```java
// FillDecision.java — SEC4-614 owns building the logic that produces this;
// the record shape itself is shared so SettlementService (M3) can consume it.
package org.leap.pricing;

import java.math.BigDecimal;

public record FillDecision(boolean fill, BigDecimal executionPrice, String rejectionReason) {
    public static FillDecision fillAt(BigDecimal price) { return new FillDecision(true, price, null); }
    public static FillDecision reject(String reason) { return new FillDecision(false, null, reason); }
}
```

Rejection reason strings to use consistently (SEC4-614/615/617 all reference
these): `NOT_MARKETABLE`, `ACCOUNT_SUSPENDED`, `INSUFFICIENT_FUNDS`,
`INSTRUMENT_NOT_TRADABLE`, `NO_PRICE_AVAILABLE`.

### 3.3 Executor module interfaces (org.leap.executor, new `executor/` project)

```java
package org.leap.executor.exec;

import org.leap.pricing.FillDecision;

public interface SettlementService {
    /** Applies decision to order {@code orderId} in one transaction; returns what happened. */
    SettlementOutcome settle(long orderId, FillDecision decision);
}
```

```java
package org.leap.executor.exec;

/** applied=false + status="DUPLICATE" means guard 1 found the order already terminal (not NEW). */
public record SettlementOutcome(boolean applied, String status, String reason) {}
```

```java
package org.leap.executor.fauxnance;

import org.leap.pricing.Quote;
import java.util.List;
import java.util.Map;

public interface FauxnanceClient {
    Quote getQuote(String symbol);
    Map<String, Quote> getQuotes(List<String> symbols); // internally chunks to <=25/request
    int getRemainingDailyBudget();
}
```

```java
package org.leap.executor.kafka;

public interface DeadLetterPublisher {
    void sendToDlt(String originalTopic, String key, byte[] originalMessageValue, String failureReason);
}
```

### 3.4 Database

- Existing schema (Sprint 3, already merged into `sprint-06`) already has
  everything a **fill** needs: `trades.executed_price/executed_quantity/executed_at/fee`
  (`NUMERIC(20,8)`), `accounts.version` (optimistic lock), `orders.idempotency_key`
  (unique — the de-dup mechanism SEC4-616 demonstrates).
- **New column** (M3 adds this as the only Liquibase changeset in this
  sprint, in `sprint-06-trade-api/src/main/resources/db/changelog/db.changelog-master.xml`,
  same inline-changeset style as the existing `2026-09-10-01-orders-public-id`
  entry): `orders.rejection_reason VARCHAR(255)` nullable, populated only
  when status becomes `REJECTED`.
- Guarded-update pattern to copy (already exists, `OrderMapper.cancelIfNew`):
  `UPDATE orders SET status = ? WHERE order_id = ? AND status = 'NEW'` — 0
  rows affected means someone else already resolved it.
- `orders.status` enum stays exactly `NEW | FILLED | CANCELLED | REJECTED` —
  **never add a PARTIALLY_FILLED or IN_PROGRESS state.**

### 3.5 Environment variables (read via `System.getenv()` in `executor/`, no properties/YAML/constants there — this rule is specific to the new framework-free executor module, not `trade-api`, which keeps its existing `application.yml ${VAR}` convention)

`KAFKA_BOOTSTRAP_SERVERS`, `FAUXNANCE_BASE_URL`, `FAUXNANCE_API_KEY`,
`POLL_INTERVAL_SECONDS`, plus the existing DB vars already in `.env.example`.

### 3.6 OrderLogic (M1 builds this, everyone else just needs to know it exists)

`org.leap.domain.OrderLogic.acceptOrder(OrderRequest request) -> Order`
replaces `placeOrder()` (deleted). Runs the same rules 1–8 (account
exists/active, instrument tradable, qty>0, price>0, BUY afford-check, SELL
holdings-check, idempotency) but returns an `Order` at status `NEW` — no
`account.debit/credit`, no `position.buy/sell`, no `order.fill()`.

---

## 4. Integration order (for the integration master, after all 5 branches have work on them)

1. **Merge M2's branch first** (`sprint-07-m2-executor-core`, tickets 612+614)
   into `sprint-07-event-backbone`. Straightforward merge — 612 only adds
   new test files, 614 only adds a new `executor/` module. No expected file
   overlap with a clean `sprint-06` base.
2. **Rebase M1's branch onto the updated `sprint-07-event-backbone`, then
   merge.** This is the step that satisfies SEC4-612's ancestor requirement:
   after the rebase, M1's commit that changes `OrderLogic.java`/`OrderService.java`
   has M2's characterisation-test commit as an ancestor. If M1 also
   independently created `EventEnvelope.java`/`Quote.java` (likely, since
   M1 needed them before M2 merged), this is where that trivial duplicate
   surfaces — keep one copy (they should be identical if both followed
   Section 3 verbatim).
3. **Rebase M3's branch onto the updated base, then merge.** M3's
   `SettlementService` implementation slots into the `executor/` module M2
   already merged; M3's `executor/pom.xml`/skeleton (if they made their own)
   gets discarded in favor of M2's. M3's Liquibase changeset is the only one
   this sprint, so no changelog conflict expected.
4. **Rebase M4's branch onto the updated base, then merge.** Same pattern —
   M4's `DeadLetterPublisher`/retry logic wraps M2's `OrderEventConsumer`
   (expect a small hand-mergeable conflict in that one file), and M4's
   poller reuses M2's `FauxnanceClient` implementation.
5. **Rebase M5's branch onto the updated base, then merge last.** `etl/` is
   untouched by everyone else, so this is closer to conflict-free; SonarQube
   config changes to `executor/pom.xml` land against the now-complete file.
6. Run the full verification checklist in Section 5 once, on
   `sprint-07-event-backbone`, before calling the sprint done.

**Expected small manual-merge points** (tell whoever's merging to expect
these, not be alarmed by them): `docker-compose.yml` (M1 adds `kafka`
service, M2/M4 add `executor` service env vars, M5 adds `sonarqube` service
— all additive), `.env.example` (same pattern), `executor/pom.xml` (M2's
canonical version wins over M3/M4's scaffolds), `executor/.../kafka/OrderEventConsumer.java`
(M2's base + M4's retry/DLT wrapping), `executor/.../db/AccountRepository.java`
(M2's read-only `findById` + M3's `updateBalanceWithVersion`).

---

## 5. Verification (run once on `sprint-07-event-backbone` after all merges)

- `mvn test` passes in `sprint-05-domain-engine`, `sprint-06-trade-api`, and
  `executor`; `pytest` passes in `etl`.
- `docker-compose up -d postgres kafka trade-api executor`; place an order
  via the Postman collection or `curl`; confirm it lands at `NEW`, then
  transitions to `FILLED`/`REJECTED`, with `trades`/`positions`/`accounts.cash_balance`
  all consistent.
- Run the SEC4-616 duplicate-delivery demo for real (console consumer/producer
  replay), not just its unit test.
- Run `etl full` twice against the same Postgres data; confirm the second
  run adds zero new `fact_trades` rows.
- Run the local SonarQube scan against the merged `executor/` and `etl/` and
  confirm the gate passes on the dashboard.

---

## 6. Files in this drop

- `00-MASTER-PLAN-sprint7.md` — this file.
- `01-PROMPT-member1-kafka-rest.md` — SEC4-611 + SEC4-613.
- `02-PROMPT-member2-executor-core.md` — SEC4-612 + SEC4-614.
- `03-PROMPT-member3-settlement.md` — SEC4-615 + SEC4-616.
- `04-PROMPT-member4-reliability.md` — SEC4-617 + SEC4-618.
- `05-PROMPT-member5-etl-quality.md` — SEC4-619 + SEC4-620.

Each prompt file is meant to be pasted as the **first message** into a fresh
Claude Code session, run from `G:\team4se4`, by that team member. Each is
self-contained (repeats the parts of this master plan it needs) so no one
has to read this whole document first — but it's here if a conflict or
ambiguity comes up that isn't covered in their own prompt.
