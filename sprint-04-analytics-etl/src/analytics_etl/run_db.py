import duckdb

con = duckdb.connect("artefacts/analytics.duckdb")

print(con.execute(
    "SELECT * FROM fact_candles"
).df())

con.close()