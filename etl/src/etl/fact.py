"""Incremental fact_trades load: watermark -> extract -> validate -> merge.

A row that fails validation is dead-lettered with its reason and batch id;
the load continues past it. No placeholder dimension rows are ever inserted
to force a fact row past an unresolved key.
"""

import json
import uuid
from datetime import datetime, timezone
from decimal import Decimal

from etl.warehouse import next_surrogate_key

EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)

VALID_SIDES = {"BUY", "SELL"}
VALID_STATUSES = {"NEW", "FILLED", "CANCELLED", "REJECTED"}


class LoadResult:
    def __init__(self, loaded: int, dead_lettered: int, batch_id: str):
        self.loaded = loaded
        self.dead_lettered = dead_lettered
        self.batch_id = batch_id


def get_watermark(con) -> datetime:
    row = con.execute(
        "SELECT last_loaded_at FROM etl_watermark WHERE name = 'fact_trades'"
    ).fetchone()
    return row[0] if row else EPOCH


def set_watermark(con, ts: datetime) -> None:
    con.execute(
        """
        INSERT OR REPLACE INTO etl_watermark (name, last_loaded_at)
        VALUES ('fact_trades', ?)
        """,
        [ts],
    )


def _resolve_price(row: dict):
    if row["status"] == "FILLED" and row.get("executed_price") is not None:
        return row["executed_price"]
    return row.get("limit_price")


def _validate(row: dict, con) -> tuple[bool, str | None, dict | None]:
    """Returns (is_valid, dead_letter_reason, fact_row_dict)."""
    side = row.get("side")
    status = row.get("status")
    quantity = row.get("quantity")
    account_id = row.get("account_id")
    symbol = row.get("symbol")
    created_at = row.get("created_at")

    if side not in VALID_SIDES:
        return False, f"invalid side: {side!r}", None
    if status not in VALID_STATUSES:
        return False, f"invalid status: {status!r}", None
    if quantity is None or not isinstance(quantity, (int, float, Decimal)) or quantity <= 0:
        return False, f"invalid quantity: {quantity!r}", None
    if created_at is None:
        return False, "missing created_at", None

    price = _resolve_price(row)
    if price is None or not isinstance(price, (int, float, Decimal)) or price <= 0:
        return False, f"invalid price: {price!r}", None

    account_key_row = con.execute(
        "SELECT account_key FROM dim_account WHERE account_id = ? AND is_current = TRUE",
        [account_id],
    ).fetchone()
    if account_key_row is None:
        return False, f"unresolved account_key for account_id: {account_id!r}", None

    instrument_key_row = con.execute(
        "SELECT instrument_key FROM dim_instrument WHERE symbol = ?", [symbol]
    ).fetchone()
    if instrument_key_row is None:
        return False, f"unresolved instrument_key for symbol: {symbol!r}", None

    date_key = int(created_at.strftime("%Y%m%d"))
    date_key_row = con.execute(
        "SELECT date_key FROM dim_date WHERE date_key = ?", [date_key]
    ).fetchone()
    if date_key_row is None:
        return False, f"unresolved date_key: {date_key!r}", None

    executed_price = row.get("executed_price") if status == "FILLED" else None
    trade_value = Decimal(str(quantity)) * Decimal(str(price))

    fact_row = {
        "account_key": account_key_row[0],
        "instrument_key": instrument_key_row[0],
        "date_key": date_key,
        "side": side,
        "quantity": int(quantity),
        "price": price,
        "status": status,
        "executed_price": executed_price,
        "trade_value": trade_value,
        "source_order_id": str(row["order_id"]),
        "created_at": created_at,
    }
    return True, None, fact_row


def _dead_letter(con, source_order_id, reason: str, batch_id: str, raw_row: dict) -> None:
    next_id = next_surrogate_key(con, "dead_letter", "id")
    con.execute(
        """
        INSERT INTO dead_letter (id, source_order_id, reason, batch_id, payload, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        [
            next_id,
            str(source_order_id) if source_order_id is not None else None,
            reason,
            batch_id,
            json.dumps(raw_row, default=str),
            datetime.utcnow(),
        ],
    )


def _merge_fact_row(con, fact_row: dict) -> None:
    existing = con.execute(
        "SELECT trade_key FROM fact_trades WHERE source_order_id = ?",
        [fact_row["source_order_id"]],
    ).fetchone()
    trade_key = existing[0] if existing else next_surrogate_key(con, "fact_trades", "trade_key")
    now = datetime.utcnow()

    # fact_trades has two UNIQUE constraints (trade_key, source_order_id), so
    # DuckDB's "INSERT OR REPLACE" can't infer a single conflict target.
    # Delete-then-insert by the already-resolved key instead.
    con.execute("DELETE FROM fact_trades WHERE trade_key = ?", [trade_key])
    con.execute(
        """
        INSERT INTO fact_trades
            (trade_key, account_key, instrument_key, date_key, side, quantity,
             price, status, executed_price, trade_value, source_order_id,
             created_at, loaded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            trade_key,
            fact_row["account_key"],
            fact_row["instrument_key"],
            fact_row["date_key"],
            fact_row["side"],
            fact_row["quantity"],
            fact_row["price"],
            fact_row["status"],
            fact_row["executed_price"],
            fact_row["trade_value"],
            fact_row["source_order_id"],
            fact_row["created_at"],
            now,
        ],
    )


def transform_and_load_batch(con, rows: list[dict], batch_id: str | None = None) -> LoadResult:
    """Validates and merges a batch of extracted order rows. Used directly by
    tests, and by load_fact_trades for a real incremental run."""
    batch_id = batch_id or str(uuid.uuid4())
    loaded = 0
    dead_lettered = 0

    for row in rows:
        is_valid, reason, fact_row = _validate(row, con)
        if not is_valid:
            _dead_letter(con, row.get("order_id"), reason, batch_id, row)
            dead_lettered += 1
            continue
        _merge_fact_row(con, fact_row)
        loaded += 1

    return LoadResult(loaded=loaded, dead_lettered=dead_lettered, batch_id=batch_id)


def load_fact_trades(con, fetch_orders) -> LoadResult:
    """fetch_orders: callable(watermark) -> list[dict], the Postgres extract.
    Injected so this is testable without a live Postgres connection."""
    watermark = get_watermark(con)
    rows = fetch_orders(watermark)

    result = transform_and_load_batch(con, rows)

    if rows:
        newest = max(row["created_at"] for row in rows)
        set_watermark(con, newest)

    return result
