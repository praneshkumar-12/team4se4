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
    existing = dict(con.execute("SELECT symbol, instrument_key FROM dim_instrument").fetchall())
    next_key = next_surrogate_key(con, "dim_instrument", "instrument_key")
    now = datetime.utcnow()

    upsert_rows = []
    for row in rows:
        symbol = row["symbol"]
        key = existing.get(symbol)
        if key is None:
            key = next_key
            next_key += 1
        upsert_rows.append((
            key,
            symbol,
            row["name"],
            row["asset_class"],
            row["currency"],
            row.get("exchange"),
            bool(row["tradable"]),
            now,
        ))

    # dim_instrument has two UNIQUE constraints (instrument_key, symbol), so
    # DuckDB's "INSERT OR REPLACE" can't infer a single conflict target.
    # Delete-then-insert by the already-resolved key instead.
    keys = [row[0] for row in upsert_rows]
    if keys:
        con.executemany("DELETE FROM dim_instrument WHERE instrument_key = ?", [(k,) for k in keys])
    con.executemany(
        """
        INSERT INTO dim_instrument
            (instrument_key, symbol, name, asset_class, currency, exchange, tradable, loaded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        upsert_rows,
    )
    return len(upsert_rows)


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
