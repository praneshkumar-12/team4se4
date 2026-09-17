"""DuckDB connection and schema. The four star-schema tables mirror
contracts/analytics-schema.sql column-for-column; do not let this drift from
that file. `etl_watermark` and `dead_letter` are ETL bookkeeping tables, not
part of the analytics contract."""

import os

import duckdb

_STAR_SCHEMA_DDL = """
CREATE TABLE IF NOT EXISTS dim_date (
    date_key    INTEGER     NOT NULL,
    full_date   DATE        NOT NULL,
    day         INTEGER     NOT NULL,
    month       INTEGER     NOT NULL,
    year        INTEGER     NOT NULL,
    quarter     INTEGER     NOT NULL,
    day_of_week INTEGER     NOT NULL,
    day_name    VARCHAR(9)  NOT NULL,
    month_name  VARCHAR(9)  NOT NULL,
    is_weekday  BOOLEAN     NOT NULL,
    CONSTRAINT pk_dim_date PRIMARY KEY (date_key),
    CONSTRAINT uq_dim_date_full_date UNIQUE (full_date)
);

CREATE TABLE IF NOT EXISTS dim_instrument (
    instrument_key  BIGINT       NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    asset_class     VARCHAR(20)  NOT NULL,
    currency        CHAR(3)      NOT NULL,
    exchange        VARCHAR(20),
    tradable        BOOLEAN      NOT NULL,
    loaded_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_dim_instrument PRIMARY KEY (instrument_key),
    CONSTRAINT uq_dim_instrument_symbol UNIQUE (symbol)
);

CREATE TABLE IF NOT EXISTS dim_account (
    account_key     BIGINT       NOT NULL,
    account_id      VARCHAR(32)  NOT NULL,
    holder_name     VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    effective_date  DATE         NOT NULL,
    end_date        DATE,
    is_current      BOOLEAN      NOT NULL,
    source_id       BIGINT       NOT NULL,
    loaded_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_dim_account PRIMARY KEY (account_key)
);

CREATE TABLE IF NOT EXISTS fact_trades (
    trade_key       BIGINT        NOT NULL,
    account_key     BIGINT        NOT NULL,
    instrument_key  BIGINT        NOT NULL,
    date_key        INTEGER       NOT NULL,
    side            VARCHAR(4)    NOT NULL,
    quantity        INTEGER       NOT NULL,
    price           DECIMAL(18,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    executed_price  DECIMAL(18,2),
    trade_value     DECIMAL(18,2) NOT NULL,
    source_order_id VARCHAR(36)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    loaded_at       TIMESTAMP     NOT NULL,
    CONSTRAINT pk_fact_trades PRIMARY KEY (trade_key),
    CONSTRAINT uq_fact_trades_source UNIQUE (source_order_id),
    CONSTRAINT fk_fact_trades_account
        FOREIGN KEY (account_key)    REFERENCES dim_account (account_key),
    CONSTRAINT fk_fact_trades_instrument
        FOREIGN KEY (instrument_key) REFERENCES dim_instrument (instrument_key),
    CONSTRAINT fk_fact_trades_date
        FOREIGN KEY (date_key)       REFERENCES dim_date (date_key)
);
"""

_BOOKKEEPING_DDL = """
CREATE TABLE IF NOT EXISTS etl_watermark (
    name            VARCHAR   NOT NULL,
    last_loaded_at  TIMESTAMP NOT NULL,
    CONSTRAINT pk_etl_watermark PRIMARY KEY (name)
);

CREATE TABLE IF NOT EXISTS dead_letter (
    id              BIGINT    NOT NULL,
    source_order_id VARCHAR,
    reason          VARCHAR   NOT NULL,
    batch_id        VARCHAR   NOT NULL,
    payload         VARCHAR,
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT pk_dead_letter PRIMARY KEY (id)
);
"""


def _run_script(con, script: str) -> None:
    for statement in script.split(";"):
        statement = statement.strip()
        if statement:
            con.execute(statement)


def connect(path: str):
    if path != ":memory:":
        parent = os.path.dirname(path)
        if parent:
            os.makedirs(parent, exist_ok=True)
    con = duckdb.connect(path)
    _run_script(con, _STAR_SCHEMA_DDL)
    _run_script(con, _BOOKKEEPING_DDL)
    return con


def next_surrogate_key(con, table: str, key_column: str) -> int:
    row = con.execute(f"SELECT COALESCE(MAX({key_column}), 0) FROM {table}").fetchone()
    return row[0] + 1
