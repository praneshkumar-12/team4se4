-- ============================================================
-- Create clients table
-- ============================================================

CREATE TABLE clients (
    client_id BIGINT GENERATED ALWAYS AS IDENTITY,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(30),
    dob DATE,
    risk_profile VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_clients
        PRIMARY KEY (client_id),

    CONSTRAINT uq_clients_email
        UNIQUE (email),

    CONSTRAINT chk_clients_risk_profile
        CHECK (
            risk_profile IN ('LOW', 'MEDIUM', 'HIGH')
        ),

    CONSTRAINT chk_clients_dob
        CHECK (
            dob IS NULL OR dob <= CURRENT_DATE
        )
);

-- ============================================================
-- Create accounts table
-- ============================================================

CREATE TABLE accounts (
    account_id BIGINT GENERATED ALWAYS AS IDENTITY,

    account_reference VARCHAR(50) NOT NULL,
    client_id BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,

    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    cash_balance NUMERIC(20,8) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_accounts
        PRIMARY KEY (account_id),

    CONSTRAINT uq_accounts_client_id
        UNIQUE (client_id),

    CONSTRAINT fk_accounts_client
        FOREIGN KEY (client_id)
        REFERENCES clients(client_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_accounts_status
        CHECK (
            account_status IN (
                'ACTIVE',
                'SUSPENDED',
                'CLOSED'
            )
        ),

    CONSTRAINT chk_accounts_currency
        CHECK (
            currency ~ '^[A-Z]{3}$'
        ),

    CONSTRAINT chk_accounts_cash_balance
        CHECK (
            cash_balance >= 0
        ),

    CONSTRAINT chk_accounts_version
        CHECK (
            version >= 0
        ),
    
    CONSTRAINT uq_accounts_account_reference
    UNIQUE (account_reference)
);

-- ============================================================
-- Create instruments table
-- ============================================================

CREATE TABLE instruments (
    instrument_id BIGINT GENERATED ALWAYS AS IDENTITY,

    isin VARCHAR(12) NOT NULL,
    ticker VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    exchange VARCHAR(50) NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    currency CHAR(3) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_instruments
        PRIMARY KEY (instrument_id),

    CONSTRAINT uq_instruments_isin
        UNIQUE (isin),

    CONSTRAINT uq_instruments_exchange_ticker
        UNIQUE (exchange, ticker),

    CONSTRAINT chk_instruments_isin
        CHECK (
            LENGTH(isin) = 12
        ),

    CONSTRAINT chk_instruments_type
        CHECK (
            type IN (
                'EQUITY',
                'ETF',
                'CURRENCY_PAIR',
                'CRYPTO_PAIR'
            )
        ),

    CONSTRAINT chk_instruments_currency
        CHECK (
            currency ~ '^[A-Z]{3}$'
        )
);

-- ============================================================
-- Create orders table
-- ============================================================

CREATE TABLE orders (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY,

    idempotency_key VARCHAR(255) NOT NULL,

    quantity NUMERIC(20,8) NOT NULL,

    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    limit_price NUMERIC(20,8),

    status VARCHAR(20) NOT NULL DEFAULT 'NEW',

    side VARCHAR(10) NOT NULL,
    order_type VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_orders
        PRIMARY KEY (order_id),

    CONSTRAINT uq_orders_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT fk_orders_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_orders_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_orders_quantity
        CHECK (
            quantity > 0
        ),

    CONSTRAINT chk_orders_side
        CHECK (
            side IN ('BUY', 'SELL')
        ),

    CONSTRAINT chk_orders_type
        CHECK (
            order_type IN (
                'MARKET',
                'LIMIT'
            )
        ),

    CONSTRAINT chk_orders_status
        CHECK (
            status IN (
                'NEW',
                'FILLED',
                'CANCELLED',
                'REJECTED'
            )
        ),

    CONSTRAINT chk_orders_limit_price
        CHECK (
            (
                order_type = 'MARKET'
                AND limit_price IS NULL
            )
            OR
            (
                order_type = 'LIMIT'
                AND limit_price IS NOT NULL
                AND limit_price > 0
            )
        )
);

-- ============================================================
-- Create transaction history table
-- ============================================================

CREATE TABLE transaction_history (
    txn_id BIGINT GENERATED ALWAYS AS IDENTITY,

    account_id BIGINT NOT NULL,

    amount NUMERIC(20,8) NOT NULL,
    txn_type VARCHAR(30) NOT NULL,
    currency CHAR(3) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_transaction_history
        PRIMARY KEY (txn_id),

    CONSTRAINT fk_transaction_history_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_transaction_history_amount
        CHECK (
            amount > 0
        ),

    CONSTRAINT chk_transaction_history_type
        CHECK (
            txn_type IN (
                'DEPOSIT',
                'WITHDRAWAL',
                'TRADE_BUY',
                'TRADE_SELL'
            )
        ),

    CONSTRAINT chk_transaction_history_currency
        CHECK (
            currency ~ '^[A-Z]{3}$'
        )
);

-- ============================================================
-- Create trades table
-- ============================================================

CREATE TABLE trades (
    trade_id BIGINT GENERATED ALWAYS AS IDENTITY,

    order_id BIGINT NOT NULL,

    executed_price NUMERIC(20,8) NOT NULL,
    executed_quantity NUMERIC(20,8) NOT NULL,

    executed_at TIMESTAMPTZ NOT NULL,

    fee NUMERIC(20,8) NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_trades
        PRIMARY KEY (trade_id),

    CONSTRAINT uq_trades_order_id
        UNIQUE (order_id),

    CONSTRAINT fk_trades_order
        FOREIGN KEY (order_id)
        REFERENCES orders(order_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_trades_price
        CHECK (
            executed_price > 0
        ),

    CONSTRAINT chk_trades_quantity
        CHECK (
            executed_quantity > 0
        ),

    CONSTRAINT chk_trades_fee
        CHECK (
            fee >= 0
        )
);

-- ============================================================
-- Create client holdings table
-- ============================================================

CREATE TABLE client_holdings (
    holding_id BIGINT GENERATED ALWAYS AS IDENTITY,

    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    quantity NUMERIC(20,8) NOT NULL DEFAULT 0,
    average_cost NUMERIC(20,8) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_client_holdings
        PRIMARY KEY (holding_id),

    CONSTRAINT fk_client_holdings_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_client_holdings_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT uq_client_holdings_account_instrument
        UNIQUE (account_id, instrument_id),

    CONSTRAINT chk_client_holdings_quantity
        CHECK (
            quantity >= 0
        ),

    CONSTRAINT chk_client_holdings_avg_cost
        CHECK (
            average_cost >= 0
        )
);
