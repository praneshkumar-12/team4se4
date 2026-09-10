-- H2 rendering of the Sprint 3 schema, for tests that run without a container.
-- The production database is the real Sprint 3 Postgres schema plus the additive
-- orders.public_id column from db/changelog.

DROP TABLE IF EXISTS trades CASCADE;
DROP TABLE IF EXISTS client_holdings CASCADE;
DROP TABLE IF EXISTS transaction_history CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS instruments CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;
DROP TABLE IF EXISTS clients CASCADE;

CREATE TABLE clients (
    client_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    phone       VARCHAR(30),
    dob         DATE,
    risk_profile VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    account_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_reference VARCHAR(50) NOT NULL UNIQUE,
    client_id         BIGINT NOT NULL REFERENCES clients(client_id),
    currency          CHAR(3) NOT NULL,
    account_status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    cash_balance      NUMERIC(20,8) NOT NULL DEFAULT 0,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_accounts_cash_balance CHECK (cash_balance >= 0),
    CONSTRAINT chk_accounts_version CHECK (version >= 0)
);

CREATE TABLE instruments (
    instrument_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    isin          VARCHAR(12) NOT NULL UNIQUE,
    ticker        VARCHAR(20) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    type          VARCHAR(30) NOT NULL,
    exchange      VARCHAR(50) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    currency      CHAR(3) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_instruments_exchange_ticker UNIQUE (exchange, ticker)
);

CREATE TABLE orders (
    order_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       VARCHAR(36) UNIQUE,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    quantity        NUMERIC(20,8) NOT NULL,
    account_id      BIGINT NOT NULL REFERENCES accounts(account_id),
    instrument_id   BIGINT NOT NULL REFERENCES instruments(instrument_id),
    limit_price     NUMERIC(20,8),
    status          VARCHAR(20) NOT NULL DEFAULT 'NEW',
    side            VARCHAR(10) NOT NULL,
    order_type      VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_orders_quantity CHECK (quantity > 0),
    CONSTRAINT chk_orders_side CHECK (side IN ('BUY','SELL')),
    CONSTRAINT chk_orders_status CHECK (status IN ('NEW','FILLED','CANCELLED','REJECTED'))
);

CREATE TABLE trades (
    trade_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id          BIGINT NOT NULL UNIQUE REFERENCES orders(order_id),
    executed_price    NUMERIC(20,8) NOT NULL,
    executed_quantity NUMERIC(20,8) NOT NULL,
    executed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    fee               NUMERIC(20,8) NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trades_price CHECK (executed_price > 0)
);

CREATE TABLE client_holdings (
    holding_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id    BIGINT NOT NULL REFERENCES accounts(account_id),
    instrument_id BIGINT NOT NULL REFERENCES instruments(instrument_id),
    quantity      NUMERIC(20,8) NOT NULL DEFAULT 0,
    average_cost  NUMERIC(20,8) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_client_holdings_account_instrument UNIQUE (account_id, instrument_id),
    CONSTRAINT chk_client_holdings_quantity CHECK (quantity >= 0)
);
