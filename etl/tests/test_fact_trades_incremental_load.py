from etl.fact import load_fact_trades


def test_incremental_load_populates_fact_trades(seeded_con, order_row):
    rows = [order_row(order_id=1), order_row(order_id=2)]

    result = load_fact_trades(seeded_con, fetch_orders=lambda wm: rows)

    assert result.loaded == 2
    assert result.dead_lettered == 0
    count = seeded_con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    assert count == 2
