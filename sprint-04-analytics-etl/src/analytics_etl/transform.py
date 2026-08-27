# src/analytics_etl/transform.py

import pandas as pd


def transform_candles(data, symbol):
    """Clean and transform candle data using vectorized Pandas operations."""

    # ============================================================
    # LOAD API DATA
    # ============================================================

    candles = data["data"]["candles"]

    # Create DataFrame directly from API response
    df = pd.DataFrame(candles)

    if df.empty:
        return df


    # ============================================================
    # REQUIRED COLUMNS
    # ============================================================

    required = [
        "date",
        "open",
        "high",
        "low",
        "close",
        "volume",
    ]

    # Keep only rows containing all required fields
    df = df.dropna(subset=required)


    # ============================================================
    # REMOVE DUPLICATES
    # ============================================================

    # Remove completely duplicated records
    df = df.drop_duplicates()


    # ============================================================
    # CONVERT NUMERIC COLUMNS
    # ============================================================

    numeric_columns = [
        "open",
        "high",
        "low",
        "close",
        "volume",
    ]

    df[numeric_columns] = df[numeric_columns].apply(
        pd.to_numeric,
        errors="coerce"
    )

    # Remove rows where numeric conversion failed
    df = df.dropna(subset=numeric_columns)


    # ============================================================
    # VALIDATE PRICE DATA
    # ============================================================

    valid_prices = (
        (df["high"] >= df["low"]) &
        (df["open"].between(df["low"], df["high"])) &
        (df["close"].between(df["low"], df["high"])) &
        (df["volume"] >= 0)
    )

    df = df[valid_prices].copy()


    # ============================================================
    # ADD SYMBOL
    # ============================================================

    df["symbol"] = symbol


    # ============================================================
    # CONVERT DATE
    # ============================================================

    df["date"] = pd.to_datetime(
        df["date"],
        format="%Y-%m-%d",
        errors="coerce"
    )

    # Remove invalid dates
    df = df.dropna(subset=["date"])


    # ============================================================
    # REMOVE DUPLICATE DATES
    # ============================================================

    # Keep only one candle for each trading date
    df = df.drop_duplicates(
        subset=["symbol", "date"],
        keep="first"
    )


    # ============================================================
    # SORT BY DATE
    # ============================================================

    df = df.sort_values("date")


    # ============================================================
    # DAILY RETURN
    # ============================================================

    df["daily_return"] = df["close"].pct_change()


    # ============================================================
    # DAILY PRICE RANGE %
    # ============================================================

    df["range_pct"] = (
        (df["high"] - df["low"])
        / df["close"]
        * 100
    )


    # ============================================================
    # TURNOVER
    # ============================================================

    df["turnover"] = (
        df["close"] * df["volume"]
    )


    # ============================================================
    # WEEK
    # ============================================================

    df["week"] = (
        df["date"]
        .dt.to_period("W")
        .dt.start_time
    )


    # ============================================================
    # RESET INDEX
    # ============================================================

    df = df.reset_index(drop=True)


    # ============================================================
    # RETURN CLEAN DATA
    # ============================================================

    return df