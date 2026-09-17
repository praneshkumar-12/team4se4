# SEC4-616 - Demonstrating no double debit on a replayed order message

Script for proving that a duplicate delivery of the
same `orders` message does not double-debit an account and does not produce
a second `trade-events` message. It relies entirely on SEC4-615's guard 1
(the guarded `orders.status` transition in `JdbcSettlementService`) — there
is no new production code here beyond that guard's distinctive log line.

## Prerequisites

- `docker-compose up -d postgres kafka trade-api executor` running, with at
  least one order already placed and executed through the Trade Executor
  (i.e. already sitting at `FILLED` or `REJECTED` in `orders.status`).
- `psql` access to the trading database.
- A shell on a machine that can reach the Kafka broker (`kafka-console-consumer`
  / `kafka-console-producer` on the `KAFKA_BOOTSTRAP_SERVERS` broker).


## Steps

### 1. Capture the original message

Consume the one order message that has already been through the executor
once, capturing both its key and its value:

```bash
kafka-console-consumer \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --topic orders \
  --property print.key=true \
  --from-beginning \
  --max-messages 1 > captured-order.txt

cat captured-order.txt
```

Note the `account_id` embedded in the message (it is also the partition
key) — it is needed for the balance query in step 3.

### 2. Record the account's balance before the replay

```sql
SELECT account_id, cash_balance, version FROM accounts WHERE account_id = :account_id;
```

### 3. Replay the exact same message

Split `captured-order.txt` into its key and value (everything before the
first tab is the key, everything after is the value), then produce it back
onto `orders`, preserving the key exactly:

```bash
kafka-console-producer \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --topic orders \
  --property "parse.key=true" \
  --property "key.separator=:" < captured-order.txt
```

### 4. Compare the balance after the replay

```sql
SELECT account_id, cash_balance, version FROM accounts WHERE account_id = :account_id;
```

`cash_balance` (and `version`) must be byte-for-byte identical to step 2 —
the replay must not have touched the account at all.

### 5. Point at the guard-1 log line

Grep the executor's logs for the distinctive guard-1 duplicate line emitted
by `JdbcSettlementService`:

```bash
docker logs executor 2>&1 | grep "SETTLEMENT_DUPLICATE_DELIVERY"
```

Expect exactly one line for this order's `order_id`, e.g.:

```
SETTLEMENT_DUPLICATE_DELIVERY order_id=123 attempted_status=FILLED - order was not NEW, guard 1 affected 0 rows; skipping cash, position and publish
```

This is the guard recognizing the order was already terminal (not `NEW`)
and returning immediately without touching cash, position, or publishing —
before any of those statements even run.

## What this proves

- Guard 1 (`UPDATE orders SET status = ?, rejection_reason = ? WHERE
  order_id = ? AND status = 'NEW'`) is the first write in the settlement
  transaction. On the replay, it affects zero rows because the order is no
  longer `NEW`, so `JdbcSettlementService.settle(...)` returns
  `SettlementOutcome(false, "DUPLICATE", null)` immediately — no cash
  movement, no position write, no publish.
- The account's `cash_balance` and `version` are unchanged by the replay
  (step 4).
- `trade-events` carries exactly one message for the order (step 6).