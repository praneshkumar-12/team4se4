"""Three commands: dims (date+instrument+account), fact (fact_trades only),
full (all four, dependency order). See etl/README.md for exact invocations."""

import argparse
import sys
from datetime import date

from etl import db
from etl.config import duckdb_path
from etl.dims import load_dim_account, load_dim_date, load_dim_instrument
from etl.fact import load_fact_trades
from etl.warehouse import connect

DIM_DATE_START = date(2020, 1, 1)
DIM_DATE_END = date(2029, 12, 31)


def run_dims(con) -> None:
    pg = db.get_connection()
    try:
        n_dates = load_dim_date(con, DIM_DATE_START, DIM_DATE_END)
        print(f"dim_date: {n_dates} day(s) ensured present")

        instruments = db.fetch_instruments(pg)
        n_instruments = load_dim_instrument(con, instruments)
        print(f"dim_instrument: {n_instruments} row(s) upserted")

        accounts = db.fetch_accounts(pg)
        n_accounts = load_dim_account(con, accounts)
        print(f"dim_account: {n_accounts} new version(s) written")
    finally:
        pg.close()


def run_fact(con) -> None:
    pg = db.get_connection()
    try:
        result = load_fact_trades(con, fetch_orders=lambda wm: db.fetch_orders_since(pg, wm))
        print(
            f"fact_trades: batch {result.batch_id} — "
            f"{result.loaded} loaded, {result.dead_lettered} dead-lettered"
        )
    finally:
        pg.close()


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="etl", description="Analytics ETL: Postgres -> DuckDB FACT_TRADES")
    parser.add_argument("command", choices=["dims", "fact", "full"])
    args = parser.parse_args(argv)

    con = connect(duckdb_path())
    try:
        if args.command == "dims":
            run_dims(con)
        elif args.command == "fact":
            run_fact(con)
        elif args.command == "full":
            run_dims(con)
            run_fact(con)
    finally:
        con.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
