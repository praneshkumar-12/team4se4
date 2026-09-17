from etl.dims import load_dim_instrument


def _instrument_row(symbol="TCS", name="Tata Consultancy Services", tradable=True):
    return {
        "symbol": symbol,
        "name": name,
        "asset_class": "EQUITY",
        "currency": "INR",
        "exchange": "NSE",
        "tradable": tradable,
    }


def test_new_instrument_is_inserted(con):
    load_dim_instrument(con, [_instrument_row()])

    row = con.execute(
        "SELECT symbol, name, tradable FROM dim_instrument WHERE symbol = 'TCS'"
    ).fetchone()
    assert row == ("TCS", "Tata Consultancy Services", True)


def test_reload_of_same_symbol_overwrites_in_place_not_duplicates(con):
    load_dim_instrument(con, [_instrument_row(name="Tata Consultancy Services")])
    load_dim_instrument(con, [_instrument_row(name="TCS Renamed", tradable=False)])

    rows = con.execute("SELECT name, tradable FROM dim_instrument WHERE symbol = 'TCS'").fetchall()
    assert len(rows) == 1
    assert rows[0] == ("TCS Renamed", False)


def test_reload_of_same_symbol_keeps_same_surrogate_key(con):
    load_dim_instrument(con, [_instrument_row()])
    first_key = con.execute(
        "SELECT instrument_key FROM dim_instrument WHERE symbol = 'TCS'"
    ).fetchone()[0]

    load_dim_instrument(con, [_instrument_row(tradable=False)])
    second_key = con.execute(
        "SELECT instrument_key FROM dim_instrument WHERE symbol = 'TCS'"
    ).fetchone()[0]

    assert first_key == second_key
