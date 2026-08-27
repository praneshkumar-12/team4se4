import json
from pathlib import Path

import pandas as pd
import pytest

from analytics_etl.transform import transform_candles


FIXTURES = Path(__file__).parent / "fixtures"


@pytest.fixture
def infy_payload():
    with open(FIXTURES / "candles-infy-ns-2026-07.json") as file:
        return json.load(file)


@pytest.fixture
def malformed_payload():
    with open(FIXTURES / "candles-malformed.json") as file:
        return json.load(file)


@pytest.fixture
def reliance_payload():
    with open(FIXTURES / "candles-reliance-ns-2026-07.json") as file:
        return json.load(file)



def test_transform_returns_clean_dataframe(infy_payload):
    df = transform_candles(infy_payload, "INFY.NS")

    assert isinstance(df, pd.DataFrame)

    assert len(df) == 7

    assert list(df.columns) == [
        "symbol",
        "date",
        "open",
        "high",
        "low",
        "close",
        "volume",
        "daily_return",
        "range_pct",
        "turnover",
        "week",
    ]

    assert df.iloc[0]["symbol"] == "INFY.NS"
    assert df.iloc[0]["date"] == pd.Timestamp("2026-07-01")



def test_transform_calculates_turnover_and_range(infy_payload):
    df = transform_candles(infy_payload, "INFY.NS")

    first_row = df.iloc[0]

    expected_turnover = (
        first_row["close"] *
        first_row["volume"]
    )

    assert first_row["turnover"] == expected_turnover

    expected_range = (
        (first_row["high"] - first_row["low"])
        / first_row["close"]
        * 100
    )

    assert first_row["range_pct"] == expected_range



def test_transform_sorts_rows_by_date(reliance_payload):
    df = transform_candles(reliance_payload, "RELIANCE.NS")

    dates = df["date"].tolist()

    assert dates == sorted(dates)



def test_rejects_non_numeric_prices(malformed_payload):
    df = transform_candles(
        malformed_payload,
        "TATASTEEL.BO"
    )

    # 2026-07-06 has open="n/a"
    assert pd.Timestamp("2026-07-06") not in df["date"].values



def test_rejects_a_high_below_a_low(malformed_payload):
    df = transform_candles(
        malformed_payload,
        "TATASTEEL.BO"
    )

    # 2026-07-07 has:
    # high = 168.1
    # low = 175.85
    assert pd.Timestamp("2026-07-07") not in df["date"].values



def test_rejects_negative_volume(malformed_payload):
    df = transform_candles(
        malformed_payload,
        "TATASTEEL.BO"
    )

    # 2026-07-08 has volume=-1
    assert pd.Timestamp("2026-07-08") not in df["date"].values



def test_keeps_duplicate_dates_as_current_behavior(malformed_payload):
    df = transform_candles(
        malformed_payload,
        "TATASTEEL.BO"
    )

    duplicate_dates = (
        df["date"]
        .value_counts()
    )

    assert duplicate_dates[pd.Timestamp("2026-07-01")] == 2