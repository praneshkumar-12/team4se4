# src/analytics_etl/transform.py

import pandas as pd


def transform_candles(data, symbol):
    """Clean candle data and return a pandas DataFrame."""

    # Get candles from the API response
    candles = data["data"]["candles"]

    clean = []

    for candle in candles:

        # Required fields
        required = [
            "date",
            "open",
            "high",
            "low",
            "close",
            "volume",
        ]

        # Skip candle if a field is missing
        if not all(field in candle for field in required):
            continue

        # Convert values to numbers
        try:
            open_price = float(candle["open"])
            high = float(candle["high"])
            low = float(candle["low"])
            close = float(candle["close"])
            volume = float(candle["volume"])
        except (ValueError, TypeError):
            continue

        # Reject impossible prices
        if high < low:
            continue

        if not low <= open_price <= high:
            continue

        if not low <= close <= high:
            continue

        # Reject negative volume
        if volume < 0:
            continue

        # Add valid candle
        clean.append({
            "symbol": symbol,
            "date": candle["date"],
            "open": open_price,
            "high": high,
            "low": low,
            "close": close,
            "volume": volume,
        })

    # Convert clean data to DataFrame
    df = pd.DataFrame(clean)

    # Return empty DataFrame if there are no valid candles
    if df.empty:
        return df

    # Convert date to datetime
    df["date"] = pd.to_datetime(df["date"])

    # Sort by date
    df = df.sort_values("date")

    # Calculate daily return
    df["daily_return"] = df["close"].pct_change()

    # Calculate daily price range as a percentage
    df["range_pct"] = (
        (df["high"] - df["low"])
        / df["close"]
        * 100
    )

    # Calculate turnover
    df["turnover"] = df["close"] * df["volume"]

    # Get the week
    df["week"] = df["date"].dt.to_period("W").dt.start_time

    # Reset row numbers
    df = df.reset_index(drop=True)

    return df