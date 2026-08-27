-- ============================================================
-- Constraint tests — run against the loaded (migrated + seeded)
-- ============================================================

-- ------------------------------------------------------------
-- 1. Duplicate idempotency key -> SQLSTATE 23505 (unique_violation)
--
-- The seed data already inserts an order with idempotency_key
-- 'SEED-ORDER-NEW-001' against ACC-10001 / TCS. Re-inserting an
-- order with the same key must be refused by uq_orders_idempotency_key
-- ------------------------------------------------------------

INSERT INTO orders (
    idempotency_key,
    quantity,
    account_id,
    instrument_id,
    limit_price,
    status,
    side,
    order_type
)
SELECT
    'SEED-ORDER-NEW-001',   -- same key as the existing seeded order
    10,
    a.account_id,
    i.instrument_id,
    3500.00,
    'NEW',
    'BUY',
    'LIMIT'
FROM accounts a
JOIN instruments i
    ON i.ticker = 'TCS'
WHERE a.account_reference = 'ACC-10001';

-- Expected:
-- ERROR:  duplicate key value violates unique constraint "uq_orders_idempotency_key"
-- SQLSTATE 23505


-- ------------------------------------------------------------
-- 2. Order referencing a non-existent account -> SQLSTATE 23503
-- (foreign_key_violation)
--
-- account_id 999999999 does not exist in accounts. fk_orders_account
-- must refuse the insert.
-- ------------------------------------------------------------

INSERT INTO orders (
    idempotency_key,
    quantity,
    account_id,
    instrument_id,
    limit_price,
    status,
    side,
    order_type
)
SELECT
    'CONSTRAINT-TEST-FK-001',
    1,
    999999999,             -- account_id that does not exist
    i.instrument_id,
    100.00,
    'NEW',
    'BUY',
    'LIMIT'
FROM instruments i
WHERE i.ticker = 'TCS';

-- Expected:
-- ERROR:  insert or update on table "orders" violates foreign key
--         constraint "fk_orders_account"
-- SQLSTATE 23503