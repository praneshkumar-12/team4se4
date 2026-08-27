-- ============================================================
-- The six named queries, run with EXPLAIN ANALYZE
-- before/after the indexes in migrations/008 exist.
-- ============================================================

-- ------------------------------------------------------------
-- Query 1: All open orders for one account, newest first
-- Who runs it: the blotter, on every dashboard load
-- Served by: idx_orders_open_account_created
-- ------------------------------------------------------------

EXPLAIN ANALYZE
SELECT
    order_id,
    idempotency_key,
    quantity,
    limit_price,
    side,
    order_type,
    created_at
FROM orders
WHERE account_id = :account_id
  AND status = 'NEW'
ORDER BY created_at DESC;


-- ------------------------------------------------------------
-- Query 2: The last 50 orders for one account, any state, newest first
-- Who runs it: the order history screen
-- Served by: idx_orders_account_created
-- ------------------------------------------------------------

EXPLAIN ANALYZE
SELECT
    order_id,
    idempotency_key,
    quantity,
    limit_price,
    status,
    side,
    order_type,
    created_at
FROM orders
WHERE account_id = :account_id
ORDER BY created_at DESC
LIMIT 50;


-- ------------------------------------------------------------
-- Query 3: Everything one account currently holds
-- Who runs it: the portfolio panel, on every dashboard load
-- Served by: idx_client_holdings_account
--            (also reachable via uq_client_holdings_account_instrument)
-- ------------------------------------------------------------

EXPLAIN ANALYZE
SELECT
    i.ticker,
    i.name,
    h.quantity,
    h.average_cost
FROM client_holdings h
JOIN instruments i
    ON i.instrument_id = h.instrument_id
WHERE h.account_id = :account_id;


-- ------------------------------------------------------------
-- Query 4: Every order created since a given timestamp, all accounts
-- Who runs it: the nightly extract into the analytical store (Sprint 7)
-- Served by: idx_orders_created_at
-- ------------------------------------------------------------

EXPLAIN ANALYZE
SELECT
    order_id,
    account_id,
    instrument_id,
    idempotency_key,
    status,
    side,
    quantity,
    limit_price,
    created_at
FROM orders
WHERE created_at >= :since_timestamp
ORDER BY created_at;


-- ------------------------------------------------------------
-- Query 5: Resolve an account from the customer-facing reference
-- Who runs it: support, and the auth service at sign-in
-- Served by: uq_accounts_account_reference (no new index needed)
-- ------------------------------------------------------------

EXPLAIN ANALYZE
SELECT
    account_id,
    client_id,
    currency,
    account_status,
    cash_balance
FROM accounts
WHERE account_reference = :account_reference;


-- ------------------------------------------------------------
-- Query 6: For one account, every filled order oldest first, with
-- running total of cash committed and rank by value within instrument
-- Who runs it: the monthly statement
-- Unindexed
-- ------------------------------------------------------------

EXPLAIN ANALYZE
SELECT
    o.order_id,
    o.instrument_id,
    t.executed_at,
    t.executed_quantity,
    t.executed_price,
    (t.executed_quantity * t.executed_price) AS trade_value,
    SUM(t.executed_quantity * t.executed_price) OVER (
        PARTITION BY o.account_id
        ORDER BY t.executed_at
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_cash_committed,
    RANK() OVER (
        PARTITION BY o.instrument_id
        ORDER BY (t.executed_quantity * t.executed_price) DESC
    ) AS rank_by_value_within_instrument
FROM orders o
JOIN trades t
    ON t.order_id = o.order_id
WHERE o.account_id = :account_id
  AND o.status = 'FILLED'
ORDER BY t.executed_at ASC;