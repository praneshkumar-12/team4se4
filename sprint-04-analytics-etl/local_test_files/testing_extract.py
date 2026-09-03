# import json

# import requests

# from analytics_etl.extract import extract_candles


# def test_extract_uses_cache(tmp_path, monkeypatch):
#     """Extract should use cached data instead of calling the API."""

#     cached_data = {
#         "data": [
#             {
#                 "date": "2025-01-02",
#                 "open": 100,
#                 "high": 105,
#                 "low": 98,
#                 "close": 103,
#                 "volume": 1000,
#             }
#         ]
#     }

#     cache_file = tmp_path / "INFY.NS_2025-01-01_2025-01-03.json"
#     cache_file.write_text(json.dumps(cached_data))

#     def fake_get(*args, **kwargs):
#         raise AssertionError("API should not have been called")

#     monkeypatch.setattr(requests, "get", fake_get)

#     result = extract_candles(
#         "INFY.NS",
#         "2025-01-01",
#         "2025-01-03",
#         cache_dir=tmp_path,
#     )

#     assert result == cached_data

from analytics_etl.extract import extract_candles

data = extract_candles("HDFCBANK.NS")

print(data)