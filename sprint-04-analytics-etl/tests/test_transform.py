import json
from pathlib import Path

from analytics_etl.transform import transform_candles

FIXTURES = Path(__file__).parent / "fixtures"


def load(name):
    return json.loads((FIXTURES / name).read_text())


def test_transforms_valid_candles():
    df = transform_candles(load("candles_good.json"), symbol="INFY.NS")
    assert len(df) == 3
    assert list(df["symbol"].unique()) == ["INFY.NS"]
    assert round(df.loc[1, "daily_return"], 6) == round(107 / 103 - 1, 6)
    assert round(df.loc[0, "turnover"], 2) == 103000


def test_rejects_a_high_below_a_low():
    quarantine = []
    df = transform_candles(
        load("candles_malformed.json"),
        symbol="INFY.NS",
        quarantine=quarantine,
    )
    assert len(df) == 2
    reasons = {item["reason"] for item in quarantine}
    assert "high below low" in reasons


def test_quarantines_non_numeric_and_missing_fields():
    quarantine = []
    df = transform_candles(
        load("candles_malformed.json"),
        symbol="INFY.NS",
        quarantine=quarantine,
    )
    assert len(quarantine) == 4
    assert any("missing fields" in item["reason"] for item in quarantine)
    assert "non-numeric OHLCV value" in {x["reason"] for x in quarantine}
    assert "negative volume" in {x["reason"] for x in quarantine}
