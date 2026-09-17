"""Dimension loaders. dim_date and dim_instrument are Type-1 (overwrite in
place); dim_account is Type-2 (close-and-insert on a status change)."""

from datetime import date, datetime, timedelta

from etl.warehouse import next_surrogate_key

_MONTH_NAMES = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
]
_DAY_NAMES = [
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
]


def load_dim_date(con, start: date, end: date) -> int:
    """Pre-populates dim_date for [start, end] inclusive. Idempotent: an
    already-present date_key is left untouched."""
    rows = []
    current = start
    while current <= end:
        quarter = (current.month - 1) // 3 + 1
        weekday = current.weekday()  # Monday = 0 .. Sunday = 6
        rows.append((
            int(current.strftime("%Y%m%d")),
            current,
            current.day,
            current.month,
            current.year,
            quarter,
            weekday,
            _DAY_NAMES[weekday],
            _MONTH_NAMES[current.month - 1],
            weekday < 5,
        ))
        current += timedelta(days=1)

    con.executemany(
        """
        INSERT OR IGNORE INTO dim_date
            (date_key, full_date, day, month, year, quarter,
             day_of_week, day_name, month_name, is_weekday)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        rows,
    )
    return len(rows)


def load_dim_instrument(con, rows: list[dict]) -> int:
    """Type-1 upsert keyed by symbol: attributes overwrite in place."""
    existing = {
        r[0]: r[1:]
        for r in con.execute(
            "SELECT symbol, instrument_key, name, asset_class, currency, exchange, tradable "
            "FROM dim_instrument"
        ).fetchall()
    }
    next_key = next_surrogate_key(con, "dim_instrument", "instrument_key")
    now = datetime.utcnow()

    insert_rows = []
    update_rows = []
    for row in rows:
        symbol = row["symbol"]
        business_attributes = (
            row["name"], row["asset_class"], row["currency"], row.get("exchange"), bool(row["tradable"]),
        )
        current = existing.get(symbol)
        if current is None:
            key = next_key
            next_key += 1
            insert_rows.append((key, symbol, *business_attributes, now))
        elif current[1:] != business_attributes:
            # A row already loaded may be referenced by fact_trades. DuckDB's FK
            # check fires even on an UPDATE that leaves instrument_key alone, so
            # this only runs for rows whose attributes actually changed - an
            # unchanged reload (the common idempotent-rerun case) never touches
            # an already-referenced row at all.
            key = current[0]
            update_rows.append((symbol, *business_attributes, now, key))

    if update_rows:
        con.executemany(
            """
            UPDATE dim_instrument
            SET symbol = ?, name = ?, asset_class = ?, currency = ?, exchange = ?,
                tradable = ?, loaded_at = ?
            WHERE instrument_key = ?
            """,
            update_rows,
        )
    if insert_rows:
        con.executemany(
            """
            INSERT INTO dim_instrument
                (instrument_key, symbol, name, asset_class, currency, exchange, tradable, loaded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            insert_rows,
        )
    return len(insert_rows) + len(update_rows)


def load_dim_account(con, rows: list[dict]) -> int:
    """Type-2 SCD merge keyed by account_id (the accounts.account_reference
    natural key). A status change closes the current version and inserts a
    new one; an unchanged status is a no-op for that account."""
    current_versions = {
        r[0]: (r[1], r[2])
        for r in con.execute(
            "SELECT account_id, account_key, status FROM dim_account WHERE is_current = TRUE"
        ).fetchall()
    }
    next_key = next_surrogate_key(con, "dim_account", "account_key")
    today = date.today()
    now = datetime.utcnow()

    changed = 0
    for row in rows:
        account_id = row["account_id"]
        status = row["status"]
        current = current_versions.get(account_id)

        if current is None:
            con.execute(
                """
                INSERT INTO dim_account
                    (account_key, account_id, holder_name, status, effective_date,
                     end_date, is_current, source_id, loaded_at)
                VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, ?)
                """,
                [next_key, account_id, row["holder_name"], status, today, row["source_id"], now],
            )
            next_key += 1
            changed += 1
            continue

        current_key, current_status = current
        if current_status == status:
            continue

        con.execute(
            "UPDATE dim_account SET end_date = ?, is_current = FALSE WHERE account_key = ?",
            [today, current_key],
        )
        con.execute(
            """
            INSERT INTO dim_account
                (account_key, account_id, holder_name, status, effective_date,
                 end_date, is_current, source_id, loaded_at)
            VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, ?)
            """,
            [next_key, account_id, row["holder_name"], status, today, row["source_id"], now],
        )
        next_key += 1
        changed += 1

    return changed
