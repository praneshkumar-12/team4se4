from __future__ import annotations

import argparse
import logging
from pathlib import Path

import pandas as pd

from  analytics_etl.extract import extract_candles
from  analytics_etl.load import load_to_duckdb
from  analytics_etl.transform import transform_candles


log = logging.getLogger(__name__)

DEFAULT_SYMBOLS = [
    "INFY.NS", "RELIANCE.NS", "HDFCBANK.NS"
]


def run(
    symbols: list[str],
    cache_dir: str | Path = ".cache",
    db_path: str | Path = "artefacts/analytics.duckdb",
):

    frames = []

    for symbol in symbols:
        try:
            print(f"Processing {symbol}")

            # Extract
            raw = extract_candles(
                symbol,
                cache_dir=cache_dir
            )

            # Transform
            clean = transform_candles(
                raw,
                symbol=symbol
            )

            if not clean.empty:
                frames.append(clean)

            print(
                f"{symbol}: {len(clean)} rows processed"
            )

        except Exception as e:
            print(
                f"{symbol} failed: {e}"
            )


    if not frames:
        raise RuntimeError(
            "No data processed"
        )


    # Combine all symbols
    result = pd.concat(
        frames,
        ignore_index=True
    )


    # Load
    load_to_duckdb(
        result,
        db_path
    )

    print(
        f"Loaded {len(result)} rows into DuckDB"
    )


def main():

    parser = argparse.ArgumentParser(
        description="Run analytics ETL"
    )

    parser.add_argument(
        "--symbols",
        nargs="+",
        default=DEFAULT_SYMBOLS
    )


    args = parser.parse_args()


    run(
        args.symbols
    )


if __name__ == "__main__":
    main()