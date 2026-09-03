from pathlib import Path

import duckdb
import pandas as pd
import plotly.express as px

# ============================================================
# CONFIGURATION
# ============================================================

DB_PATH = "artefacts/analytics.duckdb"
OUTPUT = "artefacts/report.html"

# One representative stock per sector, chosen for cross-sector comparison.
SYMBOLS = ["INFY.NS", "RELIANCE.NS", "HDFCBANK.NS"]

SECTORS = {
    "INFY.NS": "IT",
    "RELIANCE.NS": "conglomerate",
    "HDFCBANK.NS": "banking",
}

# Claims are reviewed against artefacts/report.html per claims.md; see that
# file for the queries used to derive these numbers.
CLAIMS = {
    "normalized-price-performance": (
        "All three stocks ended the period below their starting level. "
        "Infosys saw the sharpest decline compared to HDFC Bank and Reliance, "
        "where Reliance was relatively stronger."
    ),
    "annual-returns": (
        "Reliance recorded the highest return among the three stocks in 2025. "
        "In 2026, all three stocks declined, but Reliance had the smallest fall."
    ),
    "daily-return-distribution": (
        " Infosys experienced greater short-term price fluctuations during the period "
        "when compared to Reliance and HDFC Bank having lower average daily price ranges."
    ),
}


# ============================================================
# LOAD DATA
# ============================================================


def load_data(db_path):
    con = duckdb.connect(db_path, read_only=True)

    df = con.execute("""
        SELECT
            date,
            symbol,
            open,
            high,
            low,
            close,
            volume,
            daily_return,
            range_pct,
            turnover,
            normalized_price,
            week,
            year
        FROM fact_candles
        ORDER BY symbol, date
    """).df()

    con.close()

    return df


# ============================================================
# CREATE DASHBOARD
# ============================================================


def create_dashboard():

    df = load_data(DB_PATH)

    if df.empty:
        raise RuntimeError("No data found in DuckDB")

    # --------------------------------------------------------
    # Filter instruments
    # --------------------------------------------------------

    df["date"] = pd.to_datetime(df["date"])

    df = df[df["symbol"].isin(SYMBOLS)].copy()

    df = df.sort_values(["symbol", "date"])

    # ========================================================
    # CHART 1
    # NORMALIZED PRICE PERFORMANCE
    # ========================================================

    fig1 = px.line(
        df,
        x="date",
        y="normalized_price",
        color="symbol",
        title="Normalized Price Performance",
        labels={
            "date": "Date",
            "normalized_price": "Normalized Price (Start = 100)",
            "symbol": "Instrument",
        },
    )

    fig1.add_hline(
        y=100,
        line_dash="dash",
    )

    # ========================================================
    # CHART 2
    # DAILY RETURN DISTRIBUTION
    # ========================================================

    return_data = df.dropna(subset=["daily_return"])

    fig2 = px.histogram(
        return_data,
        x="daily_return",
        color="symbol",
        nbins=60,
        opacity=0.6,
        title="Distribution of Daily Returns",
        labels={
            "daily_return": "Daily Return (%)",
            "symbol": "Instrument",
        },
    )

    # ========================================================
    # CHART 3
    # ANNUAL RETURNS
    # ========================================================

    annual = (
        df.groupby(["symbol", "year"])["close"].agg(["first", "last"]).reset_index()
    )

    annual["annual_return"] = ((annual["last"] / annual["first"]) - 1) * 100

    fig3 = px.bar(
        annual,
        x="year",
        y="annual_return",
        color="symbol",
        barmode="group",
        title="Annual Returns",
        labels={
            "year": "Year",
            "annual_return": "Return (%)",
            "symbol": "Instrument",
        },
    )

    fig3.add_hline(
        y=0,
        line_dash="dash",
    )

    # ========================================================
    # BUILD HTML
    # ========================================================

    output = Path(OUTPUT)

    output.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    html = f"""
    <html>

    <head>

        <title>Market Analytics Dashboard</title>

        <style>

            body {{
                font-family: Arial, sans-serif;
                margin: 40px;
                background-color: #f7f7f7;
            }}

            h1 {{
                margin-bottom: 5px;
            }}

            h2 {{
                margin-top: 40px;
            }}

            .subtitle {{
                color: #666;
                margin-bottom: 30px;
            }}

            .card {{
                background: white;
                padding: 20px;
                margin-bottom: 30px;
                border-radius: 8px;
            }}

            .claim {{
                background: #eef4ff;
                border-left: 4px solid #4361ee;
                padding: 12px 16px;
                margin: 10px 0 20px;
                font-style: italic;
            }}

        </style>

    </head>

    <body>

        <h1>Market Analytics Dashboard</h1>

        <div class="subtitle">
            Historical analysis of {", ".join(
                f"{symbol} ({SECTORS[symbol]})" for symbol in SYMBOLS
            )}
        </div>


        <div class="card" id="normalized-price-performance">

            <h2>Normalized Price Performance</h2>

            <p>
                Compares the three instruments from a common
                starting value of 100.
            </p>

            <p class="claim">
                Claim: {CLAIMS["normalized-price-performance"]}
            </p>

            {fig1.to_html(
                full_html=False,
                include_plotlyjs="cdn"
            )}

        </div>


        <div class="card" id="annual-returns">

            <h2>Annual Returns</h2>

            <p>
                Shows the annual percentage return for each
                instrument.
            </p>

            <p class="claim">
                Claim: {CLAIMS["annual-returns"]}
            </p>

            {fig3.to_html(
                full_html=False,
                include_plotlyjs=F"cdn"
            )}

        </div>


        <div class="card" id="daily-return-distribution">

            <h2>Daily Return Distribution</h2>

            <p>
                Shows the distribution of daily percentage
                returns for each instrument.
            </p>

            <p class="claim">
                Claim: {CLAIMS["daily-return-distribution"]}
            </p>

            {fig2.to_html(
                full_html=False,
                include_plotlyjs=F"cdn"
            )}

        </div>

    </body>

    </html>
    """

    # ========================================================
    # SAVE DASHBOARD
    # ========================================================

    output.write_text(
        html,
        encoding="utf-8",
    )

    print(f"Dashboard created: {OUTPUT}")


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":
    create_dashboard()
