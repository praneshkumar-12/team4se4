from pathlib import Path

import duckdb
import pandas as pd
import plotly.express as px


# ============================================================
# CONFIGURATION
# ============================================================

DB_PATH = "artefacts/analytics.duckdb"
OUTPUT = "artefacts/report.html"

SYMBOLS = ["INFY.NS", "RELIANCE.NS", "AAPL"]


# ============================================================
# LOAD DATA
# ============================================================

def load_data(db_path):

    con = duckdb.connect(db_path, read_only=True)

    df = con.execute("""
        SELECT *
        FROM fact_candles
        ORDER BY date
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
    # Basic cleaning
    # --------------------------------------------------------

    df["date"] = pd.to_datetime(df["date"])

    df = df[
        df["symbol"].isin(SYMBOLS)
    ].copy()

    df = df.sort_values(
        ["symbol", "date"]
    )

    # --------------------------------------------------------
    # Daily return
    # --------------------------------------------------------

    df["daily_return"] = (
        df.groupby("symbol")["close"]
        .pct_change()
        * 100
    )

    # --------------------------------------------------------
    # Normalized price
    # --------------------------------------------------------

    df["normalized_price"] = (
        df["close"]
        / df.groupby("symbol")["close"].transform("first")
        * 100
    )

    # --------------------------------------------------------
    # Rolling peak
    # --------------------------------------------------------

    df["rolling_peak"] = (
        df.groupby("symbol")["close"]
        .cummax()
    )

    # --------------------------------------------------------
    # Drawdown
    # --------------------------------------------------------

    df["drawdown"] = (
        (
            df["close"] /
            df["rolling_peak"]
        ) - 1
    ) * 100


   

    # ========================================================
    # CHART 4
    # NORMALIZED PRICE
    # ========================================================

    fig4 = px.line(
        df,
        x="date",
        y="normalized_price",
        color="symbol",
        title="Normalized Price Performance",
        labels={
            "date": "Date",
            "normalized_price": "Normalized Price (Start = 100)",
            "symbol": "Instrument"
        }
    )

    fig4.add_hline(
        y=100,
        line_dash="dash"
    )


   
    # ========================================================
    # CHART 8
    # DAILY RETURN DISTRIBUTION
    # ========================================================

    fig8 = px.histogram(
        df,
        x="daily_return",
        color="symbol",
        nbins=60,
        opacity=0.6,
        title="Distribution of Daily Returns",
        labels={
            "daily_return": "Daily Return (%)",
            "symbol": "Instrument"
        }
    )


    



    # ========================================================
    # CHART 11
    # ANNUAL RETURNS
    # ========================================================

    df["year"] = df["date"].dt.year

    annual = (
        df.groupby(
            ["symbol", "year"]
        )["close"]
        .agg(["first", "last"])
        .reset_index()
    )

    annual["annual_return"] = (
        (
            annual["last"] /
            annual["first"]
        ) - 1
    ) * 100

    fig11 = px.bar(
        annual,
        x="year",
        y="annual_return",
        color="symbol",
        barmode="group",
        title="Annual Returns",
        labels={
            "year": "Year",
            "annual_return": "Return (%)",
            "symbol": "Instrument"
        }
    )

    fig11.add_hline(
        y=0,
        line_dash="dash"
    )


   

    # ========================================================
    # BUILD HTML
    # ========================================================

    output = Path(OUTPUT)
    output.parent.mkdir(
        parents=True,
        exist_ok=True
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

            table {{
                border-collapse: collapse;
                width: 100%;
            }}

            th, td {{
                padding: 10px;
                border-bottom: 1px solid #ddd;
                text-align: right;
            }}

            th {{
                text-align: center;
                background-color: #eee;
            }}

        </style>

    </head>


    <body>

        <h1>Market Analytics Dashboard</h1>

        <div class="subtitle">
            Historical analysis of INFY, RELIANCE and AAPL
        </div>

        <div class="card">

            <h2>Normalized Price Performance</h2>

            <p>
                Compares the three instruments from a common
                starting value of 100.
            </p>

            {fig4.to_html(
                full_html=False,
                include_plotlyjs=True
            )}

        </div>

        <div class="card">

            <h2>Annual Returns</h2>

            {fig11.to_html(
                full_html=False,
                include_plotlyjs=False
            )}

        </div>

        <div class="card">

            <h2>Daily Return Distribution</h2>

            {fig8.to_html(
                full_html=False,
                include_plotlyjs=False
            )}

        </div>


    </body>

    </html>
    """


    # ========================================================
    # SAVE
    # ========================================================

    output.write_text(
        html,
        encoding="utf-8"
    )

    print(
        f"Dashboard created: {OUTPUT}"
    )


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":
    create_dashboard()