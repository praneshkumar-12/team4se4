"""Postgres extract layer. Reads the operational schema (accounts, instruments,
orders, trades) read-only; never writes back to Postgres."""

import psycopg2
import psycopg2.extras

from etl.config import postgres_dsn


def get_connection():
    return psycopg2.connect(**postgres_dsn())


def fetch_accounts(conn) -> list[dict]:
    sql = """
        SELECT a.account_id      AS source_id,
               a.account_reference AS account_id,
               a.account_status  AS status,
               c.first_name || ' ' || c.last_name AS holder_name
          FROM accounts a
          JOIN clients c ON c.client_id = a.client_id
         ORDER BY a.account_id
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql)
        return [dict(row) for row in cur.fetchall()]


def fetch_instruments(conn) -> list[dict]:
    sql = """
        SELECT ticker      AS symbol,
               name,
               type         AS asset_class,
               currency,
               exchange,
               is_active     AS tradable
          FROM instruments
         ORDER BY instrument_id
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql)
        return [dict(row) for row in cur.fetchall()]


def fetch_orders_since(conn, watermark) -> list[dict]:
    """Every order (any status) created after `watermark`, joined to its
    account/instrument natural keys and to its trade fill if one exists."""
    sql = """
        SELECT o.order_id,
               o.quantity,
               o.limit_price,
               o.status,
               o.side,
               o.created_at,
               a.account_reference AS account_id,
               i.ticker            AS symbol,
               t.executed_price,
               t.executed_quantity
          FROM orders o
          JOIN accounts a    ON a.account_id = o.account_id
          JOIN instruments i ON i.instrument_id = o.instrument_id
          LEFT JOIN trades t ON t.order_id = o.order_id
         WHERE o.created_at > %(watermark)s
         ORDER BY o.created_at
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql, {"watermark": watermark})
        return [dict(row) for row in cur.fetchall()]
