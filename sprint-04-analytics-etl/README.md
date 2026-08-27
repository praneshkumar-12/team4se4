# Sprint 4 — Analytics ETL

A small, repeatable ETL pipeline that extracts Fauxnance end-of-day candles,
transforms and validates them, loads them into DuckDB, and writes an offline
Plotly dashboard.

## Scope

- `INFY.NS` — Infosys
- `RELIANCE.NS` — Reliance Industries
- `AAPL` — Apple

The pipeline is deliberately source-independent after extraction: in Sprint 7
the source can be replaced with platform trades while the transform/load shape
and tests remain usable.

## Setup

execute in cmd

Navigate to  sprint-04-analytics-etl 

```bash
python -m venv .venv
.venv\Scripts\activate.bat
pip install -e .
cp sprint-04-analytics-etl/.env.example sprint-04-analytics-etl/.env
# Put the real key in .env
```

#Backup if installation not worked 
```bash 
.venv/bin/python -m pip install -e 'sprint-04-analytics-etl[dev]'
```

Run tests:

```bash
.venv/bin/python -m pytest sprint-04-analytics-etl
```

Run the pipeline:

```bash
cd sprint-04-analytics-etl
../.venv/bin/analytics-etl --start 2025-01-01 --end 2025-03-31
```

The raw API responses are cached under `.cache/`; the analytical database is
`artefacts/analytics.duckdb`; the self-contained dashboard is
`artefacts/report.html`.

The pipeline never logs the API key.
