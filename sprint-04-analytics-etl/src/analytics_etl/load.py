# from __future__ import annotations

# from pathlib import Path

# import duckdb
# import pandas as pd


# def load_to_duckdb(
#     df: pd.DataFrame,
#     db_path: str | Path = "artefacts/analytics.duckdb",
# ) -> None:
#     """Load transformed candles into the analytical DuckDB store."""
#     db_path = Path(db_path)
#     db_path.parent.mkdir(parents=True, exist_ok=True)

#     con = duckdb.connect(str(db_path))
#     try:
#         con.execute("""
#             CREATE TABLE IF NOT EXISTS fact_candles (
#                 symbol VARCHAR,
#                 date DATE,
#                 open DOUBLE,
#                 high DOUBLE,
#                 low DOUBLE,
#                 close DOUBLE,
#                 volume DOUBLE,
#                 daily_return DOUBLE,
#                 range_pct DOUBLE,
#                 turnover DOUBLE,
#                 week DATE,
#                 PRIMARY KEY (symbol, date)
#             )
#         """)
#         if not df.empty:
#             con.register("incoming", df)
#             con.execute("""
#                 INSERT OR REPLACE INTO fact_candles
#                 SELECT symbol, date::DATE, open, high, low, close, volume,
#                        daily_return, range_pct, turnover, week::DATE
#                 FROM incoming
#             """)
#             con.unregister("incoming")
#     finally:
#         con.close()

from pathlib import Path

import duckdb
import pandas as pd


def load_to_duckdb(
    df: pd.DataFrame,
    db_path: str | Path = "artefacts/analytics.duckdb",
) -> None:
    """Load transformed candles into DuckDB."""

    db_path = Path(db_path)
    db_path.parent.mkdir(parents=True, exist_ok=True)

    with duckdb.connect(str(db_path)) as con:
        con.execute("""
            CREATE TABLE IF NOT EXISTS fact_candles AS
            SELECT * FROM df LIMIT 0
        """)

        if not df.empty:
            con.execute("""
                DELETE FROM fact_candles
            """)

            con.execute("""
                INSERT INTO fact_candles
                SELECT * FROM df
            """)