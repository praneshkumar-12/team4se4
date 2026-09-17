"""Environment configuration. Reads the DB vars already declared in .env.example."""

import os


def postgres_dsn() -> dict:
    return {
        "host": os.environ.get("POSTGRES_HOST", "localhost"),
        "port": int(os.environ.get("POSTGRES_PORT", "5432")),
        "dbname": os.environ.get("POSTGRES_DB", "trade_db"),
        "user": os.environ.get("POSTGRES_USER", "postgres"),
        "password": os.environ.get("POSTGRES_PASSWORD", "postgres"),
    }


def duckdb_path() -> str:
    return os.environ.get("DUCKDB_PATH", "data/warehouse.duckdb")
