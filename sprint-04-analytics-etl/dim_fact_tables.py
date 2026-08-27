from __future__ import annotations

from pathlib import Path

import duckdb
import pandas as pd


def load_to_duckdb(
    dim_account_df: pd.DataFrame,
    dim_instrument_df: pd.DataFrame,
    dim_date_df: pd.DataFrame,
    fact_trades_df: pd.DataFrame,
    db_path: str | Path = "artefacts/analytics.duckdb",
) -> None:
    """Create the analytical DuckDB schema and load transformed data."""

    db_path = Path(db_path)
    db_path.parent.mkdir(parents=True, exist_ok=True)

    con = duckdb.connect(str(db_path))

    try:
        # ---------------------------------------------------------
        # DIM_ACCOUNT
        # ---------------------------------------------------------
        con.execute("""
            CREATE TABLE IF NOT EXISTS dim_account (
                account_key BIGINT NOT NULL,
                account_id VARCHAR(32) NOT NULL,
                holder_name VARCHAR(255) NOT NULL,
                status VARCHAR(20) NOT NULL,
                effective_date DATE NOT NULL,
                end_date DATE,
                is_current BOOLEAN NOT NULL,
                source_id BIGINT NOT NULL,
                loaded_at TIMESTAMP NOT NULL,

                CONSTRAINT pk_dim_account
                    PRIMARY KEY (account_key)
            )
        """)

        # ---------------------------------------------------------
        # DIM_INSTRUMENT
        # ---------------------------------------------------------
        con.execute("""
            CREATE TABLE IF NOT EXISTS dim_instrument (
                instrument_key BIGINT NOT NULL,
                symbol VARCHAR(20) NOT NULL,
                name VARCHAR(255) NOT NULL,
                asset_class VARCHAR(20) NOT NULL,
                currency CHAR(3) NOT NULL,
                exchange VARCHAR(20),
                tradable BOOLEAN NOT NULL,
                loaded_at TIMESTAMP NOT NULL,

                CONSTRAINT pk_dim_instrument
                    PRIMARY KEY (instrument_key),

                CONSTRAINT uq_dim_instrument_symbol
                    UNIQUE (symbol)
            )
        """)

        # ---------------------------------------------------------
        # DIM_DATE
        # ---------------------------------------------------------
        con.execute("""
            CREATE TABLE IF NOT EXISTS dim_date (
                date_key INTEGER NOT NULL,
                full_date DATE NOT NULL,
                day INTEGER NOT NULL,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL,
                quarter INTEGER NOT NULL,
                day_of_week INTEGER NOT NULL,
                day_name VARCHAR(9) NOT NULL,
                month_name VARCHAR(9) NOT NULL,
                is_weekday BOOLEAN NOT NULL,

                CONSTRAINT pk_dim_date
                    PRIMARY KEY (date_key),

                CONSTRAINT uq_dim_date_full_date
                    UNIQUE (full_date)
            )
        """)

        # ---------------------------------------------------------
        # FACT_TRADES
        # ---------------------------------------------------------
        con.execute("""
            CREATE TABLE IF NOT EXISTS fact_trades (
                trade_key BIGINT NOT NULL,
                account_key BIGINT NOT NULL,
                instrument_key BIGINT NOT NULL,
                date_key INTEGER NOT NULL,
                side VARCHAR(4) NOT NULL,
                quantity INTEGER NOT NULL,
                price DECIMAL(18,2) NOT NULL,
                status VARCHAR(20) NOT NULL,
                executed_price DECIMAL(18,2),
                trade_value DECIMAL(18,2) NOT NULL,
                source_order_id VARCHAR(36) NOT NULL,
                created_at TIMESTAMP NOT NULL,
                loaded_at TIMESTAMP NOT NULL,

                CONSTRAINT pk_fact_trades
                    PRIMARY KEY (trade_key),

                CONSTRAINT uq_fact_trades_source
                    UNIQUE (source_order_id),

                CONSTRAINT fk_fact_trades_account
                    FOREIGN KEY (account_key)
                    REFERENCES dim_account (account_key),

                CONSTRAINT fk_fact_trades_instrument
                    FOREIGN KEY (instrument_key)
                    REFERENCES dim_instrument (instrument_key),

                CONSTRAINT fk_fact_trades_date
                    FOREIGN KEY (date_key)
                    REFERENCES dim_date (date_key)
            )
        """)

        # ---------------------------------------------------------
        # LOAD DIM_ACCOUNT
        # ---------------------------------------------------------
        if not dim_account_df.empty:
            con.register("incoming_dim_account", dim_account_df)

            con.execute("""
                INSERT OR REPLACE INTO dim_account
                SELECT
                    account_key,
                    account_id,
                    holder_name,
                    status,
                    effective_date::DATE,
                    end_date::DATE,
                    is_current,
                    source_id,
                    loaded_at::TIMESTAMP
                FROM incoming_dim_account
            """)

            con.unregister("incoming_dim_account")

        # ---------------------------------------------------------
        # LOAD DIM_INSTRUMENT
        # ---------------------------------------------------------
        if not dim_instrument_df.empty:
            con.register("incoming_dim_instrument", dim_instrument_df)

            con.execute("""
                INSERT OR REPLACE INTO dim_instrument
                SELECT
                    instrument_key,
                    symbol,
                    name,
                    asset_class,
                    currency,
                    exchange,
                    tradable,
                    loaded_at::TIMESTAMP
                FROM incoming_dim_instrument
            """)

            con.unregister("incoming_dim_instrument")

        # ---------------------------------------------------------
        # LOAD DIM_DATE
        # ---------------------------------------------------------
        if not dim_date_df.empty:
            con.register("incoming_dim_date", dim_date_df)

            con.execute("""
                INSERT OR REPLACE INTO dim_date
                SELECT
                    date_key,
                    full_date::DATE,
                    day,
                    month,
                    year,
                    quarter,
                    day_of_week,
                    day_name,
                    month_name,
                    is_weekday
                FROM incoming_dim_date
            """)

            con.unregister("incoming_dim_date")

        # ---------------------------------------------------------
        # LOAD FACT_TRADES
        # ---------------------------------------------------------
        if not fact_trades_df.empty:
            con.register("incoming_fact_trades", fact_trades_df)

            con.execute("""
                INSERT OR REPLACE INTO fact_trades
                SELECT
                    trade_key,
                    account_key,
                    instrument_key,
                    date_key,
                    side,
                    quantity,
                    price,
                    status,
                    executed_price,
                    trade_value,
                    source_order_id,
                    created_at::TIMESTAMP,
                    loaded_at::TIMESTAMP
                FROM incoming_fact_trades
            """)

            con.unregister("incoming_fact_trades")

    finally:
        con.close()