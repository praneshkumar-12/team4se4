# Analytics ETL

Incremental load from the Postgres operational schema into a DuckDB star
schema (`dim_date`, `dim_instrument`, `dim_account`, `fact_trades`) matching
`contracts/analytics-schema.sql` at the repo root. One DuckDB file, no
server.

## Setup

```bash
cd etl
python -m venv .venv
.venv/Scripts/activate        # .venv/bin/activate on macOS/Linux
pip install -e ".[dev]"
```

Reads Postgres connection info from the environment — the same
`POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` already in the repo
root's `.env.example`, plus `POSTGRES_HOST` / `POSTGRES_PORT` (default
`localhost:5432`, matching the port `docker-compose.yml` exposes). The
DuckDB file location is `DUCKDB_PATH` (default `data/warehouse.duckdb`,
relative to `etl/`).

## Commands

Run from `etl/` with the venv active:

```bash
python -m etl.cli dims   # dim_date (full range) + dim_instrument + dim_account
python -m etl.cli fact   # fact_trades only, incremental off the watermark
python -m etl.cli full   # all four, in dependency order (dims, then fact)
```

`dims` must be run at least once before `fact` — `fact_trades` rows resolve
their account/instrument/date keys by lookup and are dead-lettered, not
faked with a placeholder row, if a dimension hasn't been loaded yet.

## Idempotency

Re-running `full` (or `fact`) twice against unchanged Postgres data adds no
new rows: the watermark (`etl_watermark` table, on `orders.created_at`)
stops re-reading old orders, and the merge on `fact_trades.source_order_id`
stops a rare re-read from duplicating a row.

## Dead letters

A row that fails validation (unresolved dimension key, non-positive
quantity/price, invalid `side`/`status`) is written to the `dead_letter`
table with its reason and the batch id it came from, and the load continues
past it — nothing is silently dropped.

## Tests

```bash
pytest
```
