-- ============================================================
-- SEED DATA
-- 001_seed.sql
--
-- Covers:
--   1. ACTIVE account
--   2. SUSPENDED account
--   3. CLOSED account
--   4. Account with almost no cash
--   5. Active instrument
--   6. Inactive instrument
--   7. NEW order
--   8. FILLED order
--   9. REJECTED order
--  10. CANCELLED order
--  11. Filled trade
--  12. Holdings reconciled with filled order
-- ============================================================


-- ============================================================
-- 1. CLIENTS
-- ============================================================

INSERT INTO clients (
    first_name,
    last_name,
    email,
    phone,
    dob,
    risk_profile
)
VALUES
(
    'Arun',
    'Kumar',
    'arun.kumar@example.com',
    '+919800000001',
    '1995-04-15',
    'MEDIUM'
),
(
    'Priya',
    'Sharma',
    'priya.sharma@example.com',
    '+919800000002',
    '1992-08-21',
    'LOW'
),
(
    'Rahul',
    'Menon',
    'rahul.menon@example.com',
    '+919800000003',
    '1988-11-10',
    'HIGH'
);


-- ============================================================
-- 2. ACCOUNTS
-- ============================================================

-- Client 1:
-- ACTIVE account with normal cash balance

INSERT INTO accounts (
    account_reference,
    client_id,
    currency,
    account_status,
    cash_balance,
    version
)
SELECT
    'ACC-10001',
    client_id,
    'INR',
    'ACTIVE',
    100000.00,
    0
FROM clients
WHERE email = 'arun.kumar@example.com';


-- Client 2:
-- SUSPENDED account with almost no cash

INSERT INTO accounts (
    account_reference,
    client_id,
    currency,
    account_status,
    cash_balance,
    version
)
SELECT
    'ACC-10002',
    client_id,
    'INR',
    'SUSPENDED',
    0.50,
    0
FROM clients
WHERE email = 'priya.sharma@example.com';


-- Client 3:
-- CLOSED account

INSERT INTO accounts (
    account_reference,
    client_id,
    currency,
    account_status,
    cash_balance,
    version
)
SELECT
    'ACC-10003',
    client_id,
    'INR',
    'CLOSED',
    2500.00,
    3
FROM clients
WHERE email = 'rahul.menon@example.com';


-- ============================================================
-- 3. INSTRUMENTS
-- ============================================================

-- Active instrument

INSERT INTO instruments (
    isin,
    ticker,
    name,
    type,
    exchange,
    is_active,
    currency
)
VALUES
(
    'INE467B01029',
    'TCS',
    'Tata Consultancy Services',
    'EQUITY',
    'NSE',
    TRUE,
    'INR'
);


-- Another active instrument

INSERT INTO instruments (
    isin,
    ticker,
    name,
    type,
    exchange,
    is_active,
    currency
)
VALUES
(
    'INE009A01021',
    'INFY',
    'Infosys Limited',
    'EQUITY',
    'NSE',
    TRUE,
    'INR'
);


-- Inactive instrument
-- Row remains in the database so old orders can still resolve.

INSERT INTO instruments (
    isin,
    ticker,
    name,
    type,
    exchange,
    is_active,
    currency
)
VALUES
(
    'INE999Z01010',
    'OLDCO',
    'Old Company Limited',
    'EQUITY',
    'NSE',
    FALSE,
    'INR'
);


-- ============================================================
-- 4. ORDERS
-- ============================================================

-- ------------------------------------------------------------
-- 4.1 NEW ORDER
-- ------------------------------------------------------------
-- This remains open.

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
    'SEED-ORDER-NEW-001',
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


-- ------------------------------------------------------------
-- 4.2 FILLED ORDER
-- ------------------------------------------------------------
-- This order will have a matching trade and holding.

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
    'SEED-ORDER-FILLED-001',
    10,
    a.account_id,
    i.instrument_id,
    3400.00,
    'NEW',
    'BUY',
    'LIMIT'
FROM accounts a
JOIN instruments i
    ON i.ticker = 'TCS'
WHERE a.account_reference = 'ACC-10001';


-- Change NEW -> FILLED

UPDATE orders
SET status = 'FILLED'
WHERE idempotency_key = 'SEED-ORDER-FILLED-001';


-- ------------------------------------------------------------
-- 4.3 REJECTED ORDER
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
    'SEED-ORDER-REJECTED-001',
    5,
    a.account_id,
    i.instrument_id,
    1800.00,
    'NEW',
    'BUY',
    'LIMIT'
FROM accounts a
JOIN instruments i
    ON i.ticker = 'INFY'
WHERE a.account_reference = 'ACC-10001';


-- Change NEW -> REJECTED

UPDATE orders
SET status = 'REJECTED'
WHERE idempotency_key = 'SEED-ORDER-REJECTED-001';


-- ------------------------------------------------------------
-- 4.4 CANCELLED ORDER
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
    'SEED-ORDER-CANCELLED-001',
    8,
    a.account_id,
    i.instrument_id,
    3600.00,
    'NEW',
    'BUY',
    'LIMIT'
FROM accounts a
JOIN instruments i
    ON i.ticker = 'TCS'
WHERE a.account_reference = 'ACC-10001';


-- Change NEW -> CANCELLED

UPDATE orders
SET status = 'CANCELLED'
WHERE idempotency_key = 'SEED-ORDER-CANCELLED-001';


-- ============================================================
-- 5. TRADE FOR FILLED ORDER
-- ============================================================

INSERT INTO trades (
    order_id,
    executed_price,
    executed_quantity,
    executed_at,
    fee
)
SELECT
    order_id,
    3400.00,
    10,
    CURRENT_TIMESTAMP - INTERVAL '2 days',
    34.00
FROM orders
WHERE idempotency_key = 'SEED-ORDER-FILLED-001';


-- ============================================================
-- 6. TRANSACTION HISTORY
-- ============================================================

-- Initial deposit

INSERT INTO transaction_history (
    account_id,
    amount,
    txn_type,
    currency
)
SELECT
    account_id,
    100000.00,
    'DEPOSIT',
    'INR'
FROM accounts
WHERE account_reference = 'ACC-10001';


-- Trade purchase

INSERT INTO transaction_history (
    account_id,
    amount,
    txn_type,
    currency
)
SELECT
    account_id,
    34000.00,
    'TRADE_BUY',
    'INR'
FROM accounts
WHERE account_reference = 'ACC-10001';


-- ============================================================
-- 7. CLIENT HOLDINGS
-- ============================================================
-- This reconciles with the filled TCS order:
--
-- Filled order:
--     10 TCS @ 3400
--
-- Holding:
--     10 TCS
--     average_cost = 3400
--
-- Therefore:
--     quantity       = executed_quantity
--     average_cost   = executed_price
-- ============================================================

INSERT INTO client_holdings (
    account_id,
    instrument_id,
    quantity,
    average_cost
)
SELECT
    a.account_id,
    i.instrument_id,
    10,
    3400.00
FROM accounts a
JOIN instruments i
    ON i.ticker = 'TCS'
WHERE a.account_reference = 'ACC-10001';


-- ============================================================
-- 8. VALIDATION QUERIES (Extra)
-- ============================================================

-- Check account states

SELECT
    account_reference,
    account_status,
    cash_balance
FROM accounts
ORDER BY account_reference;


-- Check instrument states

SELECT
    ticker,
    name,
    is_active
FROM instruments
ORDER BY ticker;


-- Check order lifecycle states

SELECT
    order_id,
    idempotency_key,
    status,
    side,
    quantity,
    limit_price
FROM orders
ORDER BY order_id;


-- Check filled orders and their trades

SELECT
    o.order_id,
    o.idempotency_key,
    o.quantity AS ordered_quantity,
    t.executed_quantity,
    t.executed_price
FROM orders o
JOIN trades t
    ON t.order_id = o.order_id
WHERE o.status = 'FILLED';


-- Check holdings

SELECT
    a.account_reference,
    i.ticker,
    h.quantity,
    h.average_cost
FROM client_holdings h
JOIN accounts a
    ON a.account_id = h.account_id
JOIN instruments i
    ON i.instrument_id = h.instrument_id;