import json

from analytics_etl.transform import transform_candles


# Open the raw API response
with open(r"C:\Users\nitik\Downloads\sprint-04-analytics-etl\sprint-04-analytics-etl\.cache\AAPL_None_None.json") as file:
    data = json.load(file)


# Transform the data
df = transform_candles(data, "AAPL")


# Show the result
print("Transformation successful!")
print()
print("Number of rows:", len(df))
print()
print(df)

