from etl.fact import load_fact_trades


def test_rerun_with_no_new_source_data_adds_no_rows(seeded_con, order_row):
    rows = [order_row(order_id=1), order_row(order_id=2)]

    first = load_fact_trades(seeded_con, fetch_orders=lambda wm: rows)
    assert first.loaded == 2

    # Second run: watermark has advanced past both orders, so the (fake)
    # Postgres extract legitimately returns nothing new.
    second = load_fact_trades(seeded_con, fetch_orders=lambda wm: [])

    assert second.loaded == 0
    assert second.dead_lettered == 0
    count = seeded_con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    assert count == 2


def test_rerun_of_same_batch_does_not_duplicate_rows(seeded_con, order_row):
    """Even if the extract re-reads an already-loaded order (a rare re-read
    the watermark didn't prevent), the merge on source_order_id must not
    duplicate it."""
    rows = [order_row(order_id=1)]

    load_fact_trades(seeded_con, fetch_orders=lambda wm: rows)
    load_fact_trades(seeded_con, fetch_orders=lambda wm: rows)

    count = seeded_con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    assert count == 1
