from __future__ import annotations

import argparse
import logging
from pathlib import Path

import duckdb
import pandas as pd
import plotly.express as px

from .extract import QuotaExhausted, SymbolExtractionError, extract_candles
from .load import load_to_duckdb
from .transform import transform_candles

log = logging.getLogger(__name__)

DEFAULT_SYMBOLS = ["INFY.NS", "RELIANCE.NS", "AAPL"]


def _make_dashboard(db_path: str | Path, output: str | Path) -> None:
    con = duckdb.connect(str(db_path), read_only=True)
    try:
        weekly = con.execute("""
            SELECT symbol, week,
                   AVG(turnover) AS avg_daily_turnover,
                   AVG(range_pct) AS avg_daily_range_pct
            FROM fact_candles
            GROUP BY symbol, week
            ORDER BY week, symbol
        """).df()
    finally:
        con.close()

    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)

    if weekly.empty:
        raise RuntimeError("No transformed data available for dashboard")

    fig1 = px.line(
        weekly,
        x="week",
        y="avg_daily_turnover",
        color="symbol",
        title="Weekly average daily turnover by instrument",
        labels={"week": "Week", "avg_daily_turnover": "Average daily turnover (price × volume)",
                "symbol": "Instrument"},
    )
    fig1.update_layout(xaxis_title="Week", yaxis_title="Average daily turnover (price × volume)")

    fig2 = px.line(
        weekly,
        x="week",
        y="avg_daily_range_pct",
        color="symbol",
        title="Weekly average daily price range by instrument",
        labels={"week": "Week", "avg_daily_range_pct": "Average daily high-low range (%)",
                "symbol": "Instrument"},
    )
    fig2.update_layout(xaxis_title="Week", yaxis_title="Average daily high-low range (%)")

    weekly_return = weekly.copy()
    # Re-query a weekly return directly for a meaningful movement chart.
    con = duckdb.connect(str(db_path), read_only=True)
    try:
        moves = con.execute("""
            WITH weekly_close AS (
                SELECT symbol, week, LAST(close ORDER BY date) AS close
                FROM fact_candles
                GROUP BY symbol, week
            )
            SELECT symbol, week, close,
                   (close / LAG(close) OVER (PARTITION BY symbol ORDER BY week) - 1) * 100
                   AS weekly_return_pct
            FROM weekly_close
            ORDER BY week, symbol
        """).df()
    finally:
        con.close()

    fig3 = px.line(
        moves,
        x="week",
        y="weekly_return_pct",
        color="symbol",
        title="Weekly price returns reveal the largest gains and losses",
        labels={"week": "Week", "weekly_return_pct": "Weekly return (%)",
                "symbol": "Instrument"},
    )
    fig3.update_layout(xaxis_title="Week", yaxis_title="Weekly return (%)")

    # One offline HTML containing all three charts, with stable fragments.
    html = f"""<!doctype html>
<html><head><meta charset="utf-8"><title>Sprint 4 Market Analytics</title></head>
<body>
<h1>Sprint 4 Market Analytics</h1>
<section id="weekly-turnover"><h2>Weekly turnover</h2>{fig1.to_html(full_html=False, include_plotlyjs=True)}</section>
<section id="weekly-range"><h2>Weekly price range</h2>{fig2.to_html(full_html=False, include_plotlyjs=False)}</section>
<section id="weekly-returns"><h2>Weekly returns</h2>{fig3.to_html(full_html=False, include_plotlyjs=False)}</section>
</body></html>"""
    output.write_text(html)


def run(
    symbols: list[str],
    start: str,
    end: str,
    *,
    cache_dir: str | Path = ".cache",
    db_path: str | Path = "artefacts/analytics.duckdb",
    report_path: str | Path = "artefacts/report.html",
) -> None:
    all_frames: list[pd.DataFrame] = []
    for symbol in symbols:
        try:
            raw = extract_candles(symbol, start, end, cache_dir=cache_dir)
            quarantine: list[dict] = []
            frame = transform_candles(raw, symbol=symbol, quarantine=quarantine)
            log.info("%s: accepted=%d rejected=%d", symbol, len(frame), len(quarantine))
            if not frame.empty:
                all_frames.append(frame)
        except QuotaExhausted:
            raise
        except SymbolExtractionError as exc:
            log.error("%s failed without retry: %s", symbol, exc)
            continue
        except Exception as exc:
            log.error("%s failed: %s", symbol, exc)
            continue

    if not all_frames:
        raise RuntimeError("No symbols produced usable data")

    combined = pd.concat(all_frames, ignore_index=True)
    load_to_duckdb(combined, db_path)
    _make_dashboard(db_path, report_path)
    log.info("Loaded %d rows; dashboard written to %s", len(combined), report_path)


def main() -> None:
    parser = argparse.ArgumentParser(description="Run Sprint 4 analytics ETL")
    parser.add_argument("--start", required=True, help="YYYY-MM-DD")
    parser.add_argument("--end", required=True, help="YYYY-MM-DD")
    parser.add_argument("--symbols", nargs="+", default=DEFAULT_SYMBOLS)
    parser.add_argument("--cache-dir", default=".cache")
    parser.add_argument("--db", default="artefacts/analytics.duckdb")
    parser.add_argument("--report", default="artefacts/report.html")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    run(args.symbols, args.start, args.end,
        cache_dir=args.cache_dir, db_path=args.db, report_path=args.report)


if __name__ == "__main__":
    main()
