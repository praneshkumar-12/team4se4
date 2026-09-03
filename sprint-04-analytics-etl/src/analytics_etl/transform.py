import pandas as pd


def transform_candles(data, symbol):
    """
    Clean, validate and enrich raw market candle data.

    Transformations:
    - Select required fields
    - Remove incomplete/duplicate records
    - Convert data types
    - Validate OHLCV values
    - Add symbol
    - Calculate daily returns
    - Calculate daily price range %
    - Calculate turnover
    - Calculate normalized price
    - Add week and year dimensions
    """

    # ============================================================
    # 1. LOAD API DATA
    # ============================================================

    candles = data["data"]["candles"]

    df = pd.DataFrame(candles)

    if df.empty:
        return df

    # ============================================================
    # 2. REQUIRED COLUMNS
    # ============================================================

    required_columns = [
        "date",
        "open",
        "high",
        "low",
        "close",
        "volume",
    ]

    # Keep only required columns
    df = df[required_columns].copy()

    # Remove rows with missing required values
    df = df.dropna(
        subset=required_columns
    )

    # ============================================================
    # 3. CONVERT DATA TYPES
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

    df["date"] = pd.to_datetime(
        df["date"],
        format="%Y-%m-%d",
        errors="coerce"
    )

    # Remove rows where conversion failed
    df = df.dropna(
        subset=["date"] + numeric_columns
    )

    # ============================================================
    # 4. REMOVE DUPLICATES
    # ============================================================

    # Remove completely duplicated records
    df = df.drop_duplicates()

    # Add instrument identifier
    df["symbol"] = symbol

    # Keep only one candle per instrument/date
    df = df.drop_duplicates(
        subset=["symbol", "date"],
        keep="first"
    )

    # ============================================================
    # 5. VALIDATE PRICE DATA
    # ============================================================

    valid_prices = (
        (df["open"] > 0) &
        (df["high"] > 0) &
        (df["low"] > 0) &
        (df["close"] > 0) &
        (df["high"] >= df["low"]) &
        (df["open"] >= df["low"]) &
        (df["open"] <= df["high"]) &
        (df["close"] >= df["low"]) &
        (df["close"] <= df["high"]) &
        (df["volume"] >= 0)
    )

    df = df[valid_prices].copy()

    # ============================================================
    # 6. SORT BY SYMBOL AND DATE
    # ============================================================

    df = df.sort_values(
        ["symbol", "date"]
    ).reset_index(drop=True)

    # ============================================================
    # 7. DAILY RETURN
    # ============================================================

    # Percentage change from the previous trading day.
    df["daily_return"] = (
        df.groupby("symbol")["close"]
        .pct_change()
        * 100
    )

    # ============================================================
    # 8. DAILY PRICE RANGE %
    # ============================================================

    # Measures the day's high-low movement
    # relative to the closing price.
    df["range_pct"] = (
        (df["high"] - df["low"])
        / df["close"]
        * 100
    )

    # ============================================================
    # 9. TURNOVER
    # ============================================================

    # Approximate value traded during the day.
    df["turnover"] = (
        df["close"] * df["volume"]
    )

    # ============================================================
    # 10. NORMALIZED PRICE
    # ============================================================

    # Sets the first observed closing price for each
    # instrument to 100, allowing comparison between
    # instruments with different price levels.
    first_price = (
        df.groupby("symbol")["close"]
        .transform("first")
    )

    df["normalized_price"] = (
        df["close"]
        / first_price
        * 100
    )

    # ============================================================
    # 11. WEEK
    # ============================================================

    # Creates a weekly time bucket for aggregation.
    df["week"] = (
        df["date"]
        .dt.to_period("W")
        .dt.start_time
    )

    # ============================================================
    # 12. YEAR
    # ============================================================

    # Used for annual performance analysis.
    df["year"] = df["date"].dt.year

    # ============================================================
    # 13. FINAL COLUMN ORDER
    # ============================================================

    df = df[
        [
            "date",
            "symbol",
            "open",
            "high",
            "low",
            "close",
            "volume",
            "daily_return",
            "range_pct",
            "turnover",
            "normalized_price",
            "week",
            "year",
        ]
    ]

    # ============================================================
    # 14. RESET INDEX
    # ============================================================

    df = df.reset_index(drop=True)

    return df

