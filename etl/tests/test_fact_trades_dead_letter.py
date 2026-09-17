from etl.fact import transform_and_load_batch


def test_invalid_row_is_dead_lettered_with_reason_and_batch(seeded_con, order_row):
    rows = [order_row(order_id=1, side="BOTH")]

    result = transform_and_load_batch(seeded_con, rows, batch_id="batch-123")

    assert result.dead_lettered == 1
    dl = seeded_con.execute(
        "SELECT source_order_id, reason, batch_id FROM dead_letter"
    ).fetchone()
    assert dl[0] == "1"
    assert "side" in dl[1]
    assert dl[2] == "batch-123"


def test_dead_lettered_row_is_not_dropped_other_valid_rows_still_load(seeded_con, order_row):
    rows = [
        order_row(order_id=1, side="BOTH"),  # invalid
        order_row(order_id=2),               # valid
        order_row(order_id=3),               # valid
    ]

    result = transform_and_load_batch(seeded_con, rows, batch_id="batch-456")

    assert result.dead_lettered == 1
    assert result.loaded == 2
    fact_count = seeded_con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    assert fact_count == 2
    dl_count = seeded_con.execute("SELECT COUNT(*) FROM dead_letter").fetchone()[0]
    assert dl_count == 1


def test_unresolved_dimension_key_is_dead_lettered_not_faked_with_placeholder(seeded_con, order_row):
    rows = [order_row(order_id=1, symbol="NOSUCHSYMBOL")]

    result = transform_and_load_batch(seeded_con, rows)

    assert result.dead_lettered == 1
    assert result.loaded == 0
    reason = seeded_con.execute("SELECT reason FROM dead_letter").fetchone()[0]
    assert "instrument_key" in reason
