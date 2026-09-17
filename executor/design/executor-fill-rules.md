# Executor fill rules — design notes (SEC4-614)

Answers to the four questions the ticket says to be ready to defend.

## 1. Rounding

The quote side used for a decision (`ask` for BUY, `bid` for SELL) is rounded
to the column's scale — `NUMERIC(20,8)`, `HALF_UP` — **before** it is compared
against the order's limit price, and that same rounded value is what
`FillDecision.executionPrice()` returns. See `FillRule.decide()` in
`sprint-05-domain-engine/src/main/java/org/leap/pricing/FillRule.java`.

Rounding before comparing and before returning means there is exactly one
number in play: the price the fill decision was made against and the price
that eventually lands in `trades.executed_price` are always the same value,
never silently different because rounding happened at two different points
(e.g. compare against the raw quote, then round only when persisting).

## 2. Which Sprint 5 rules re-run at execution time

`ExecutionService` re-runs two of the checks `OrderLogic` already performs at
acceptance:

- **Account active** — `Account.isActive()`, reused unchanged from
  `sprint-05-domain-engine`. Re-run because time has passed since
  acceptance and the account may have been suspended in the meantime.
- **BUY affordability** — `Account.canAfford()`, same reasoning: the balance
  may have been spent by another order that accepted (and filled) in the
  interim.

It also re-checks **instrument tradability**, via a fresh DB read
(`InstrumentRepository.findById`) rather than the Sprint 5 domain method
directly, since an instrument can be delisted between acceptance and
execution.

**Idempotency is not re-checked.** It is already guaranteed by the
`orders.idempotency_key` unique constraint at acceptance time, and that
guarantee can't un-happen between acceptance and execution — there's nothing
new to verify. (The *separate* duplicate-delivery defense in
`ExecutionService.execute()` — reject anything that isn't still `NEW` — is a
different concern: it guards against the same Kafka message being delivered
and processed twice, not against two different orders sharing a key.)

## 3. Suspended after acceptance

Caught by the account-active re-check (question 2, step 3 of the flow):
`ExecutionService` loads the account fresh via `AccountRepository.findById()`
and checks `isActive()` again. If the account was suspended after the order
was accepted but before this message was processed, the order is rejected
with `ACCOUNT_SUSPENDED` and resolved through `SettlementService` — never
silently left at `NEW` and never silently filled.

## 4. No price available

A Fauxnance outage or an exhausted daily budget is not something the caller
retries — `FauxnanceHttpClient` already retries internally (3 attempts, short
backoff) against its own bounded budget before giving up. Once it gives up,
it throws `FauxnanceException`, which `ExecutionService` catches and turns
into `FillDecision.reject("NO_PRICE_AVAILABLE")`, handed to
`SettlementService` like any other decision.

This is the ticket's other headline acceptance criterion: an order that
cannot be priced is **resolved**, not left at `NEW` forever.
