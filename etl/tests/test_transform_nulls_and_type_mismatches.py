from etl.fact import transform_and_load_batch


def test_null_quantity_is_dead_lettered_not_crashed(seeded_con, order_row):
    rows = [order_row(order_id=1, quantity=None)]

    result = transform_and_load_batch(seeded_con, rows)

    assert result.loaded == 0
    assert result.dead_lettered == 1


def test_null_created_at_is_dead_lettered_not_crashed(seeded_con, order_row):
    rows = [order_row(order_id=1, created_at=None)]

    result = transform_and_load_batch(seeded_con, rows)

    assert result.loaded == 0
    assert result.dead_lettered == 1


def test_wrong_type_quantity_is_dead_lettered_not_crashed(seeded_con, order_row):
    rows = [order_row(order_id=1, quantity="ten")]

    result = transform_and_load_batch(seeded_con, rows)

    assert result.loaded == 0
    assert result.dead_lettered == 1


def test_unfilled_market_order_with_no_price_is_dead_lettered_not_crashed(seeded_con, order_row):
    """A MARKET order that hasn't filled has neither a limit_price nor an
    executed_price in Postgres — the price validation check catches it."""
    rows = [order_row(order_id=1, status="NEW", limit_price=None, executed_price=None)]

    result = transform_and_load_batch(seeded_con, rows)

    assert result.loaded == 0
    assert result.dead_lettered == 1


def test_one_bad_row_does_not_stop_the_batch(seeded_con, order_row):
    rows = [
        order_row(order_id=1, quantity=None),
        order_row(order_id=2),
    ]

    result = transform_and_load_batch(seeded_con, rows)

    assert result.dead_lettered == 1
    assert result.loaded == 1
