from datetime import date, datetime

import pytest

from etl.warehouse import connect


@pytest.fixture
def con():
    connection = connect(":memory:")
    yield connection
    connection.close()


@pytest.fixture
def seeded_con(con):
    """A warehouse with one current account, one instrument and dim_date
    covering 2026, ready for fact_trades rows to resolve against."""
    con.execute(
        """
        INSERT INTO dim_account
            (account_key, account_id, holder_name, status, effective_date,
             end_date, is_current, source_id, loaded_at)
        VALUES (1, 'ACC-10001', 'Arun Kumar', 'ACTIVE', ?, NULL, TRUE, 1, ?)
        """,
        [date(2026, 1, 1), datetime.utcnow()],
    )
    con.execute(
        """
        INSERT INTO dim_instrument
            (instrument_key, symbol, name, asset_class, currency, exchange, tradable, loaded_at)
        VALUES (1, 'TCS', 'Tata Consultancy Services', 'EQUITY', 'INR', 'NSE', TRUE, ?)
        """,
        [datetime.utcnow()],
    )
    for day in range(1, 32):
        d = date(2026, 1, day)
        con.execute(
            """
            INSERT INTO dim_date
                (date_key, full_date, day, month, year, quarter,
                 day_of_week, day_name, month_name, is_weekday)
            VALUES (?, ?, ?, 1, 2026, 1, ?, 'Monday', 'January', TRUE)
            """,
            [int(d.strftime("%Y%m%d")), d, d.day, d.weekday()],
        )
    return con


_UNSET = object()


def _make_order_row(
    order_id=1,
    account_id="ACC-10001",
    symbol="TCS",
    side="BUY",
    status="FILLED",
    quantity=10,
    limit_price=3400.00,
    executed_price=_UNSET,
    executed_quantity=10,
    created_at=_UNSET,
):
    if created_at is _UNSET:
        created_at = datetime(2026, 1, 15, 10, 0, 0)
    if executed_price is _UNSET:
        executed_price = 3400.00 if status == "FILLED" else None
    return {
        "order_id": order_id,
        "quantity": quantity,
        "limit_price": limit_price,
        "status": status,
        "side": side,
        "created_at": created_at,
        "account_id": account_id,
        "symbol": symbol,
        "executed_price": executed_price,
        "executed_quantity": executed_quantity if status == "FILLED" else None,
    }


@pytest.fixture
def order_row():
    """Factory fixture: order_row(order_id=1, status='FILLED', ...) -> dict,
    shaped like etl.db.fetch_orders_since's output rows."""
    return _make_order_row
