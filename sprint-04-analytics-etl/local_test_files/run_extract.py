
from analytics_etl.extract import extract_candles


data = extract_candles(
    symbol="HDFCBANK.NS"
)

print("Extraction successful!")
print("Number of candles:", len(data["data"]))
print("First candle:")
print(data["data"])