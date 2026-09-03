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


            