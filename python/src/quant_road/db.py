from __future__ import annotations

from contextlib import contextmanager
from decimal import Decimal
from pathlib import Path
from typing import Iterable, Sequence

import pandas as pd
import psycopg2
from psycopg2 import extras
from psycopg2.pool import ThreadedConnectionPool

from quant_road.config import settings


_POOL: ThreadedConnectionPool | None = None


def _connect():
    return _pool().getconn()


def _pool() -> ThreadedConnectionPool:
    global _POOL
    if _POOL is None:
        _POOL = ThreadedConnectionPool(
            minconn=1,
            maxconn=max(1, settings.pg_pool_size),
            host=settings.pg_host,
            port=settings.pg_port,
            user=settings.pg_user,
            password=settings.pg_password,
            dbname=settings.pg_database,
        )
    return _POOL


@contextmanager
def get_connection():
    conn = _connect()
    try:
        yield conn
    finally:
        _pool().putconn(conn)


def run_sql_file(sql_file: str | Path) -> None:
    sql_text = Path(sql_file).read_text(encoding="utf-8")
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql_text)
        conn.commit()


def batch_execute(sql: str, rows: Iterable[Sequence], page_size: int = 200) -> None:
    rows = list(rows)
    if not rows:
        return
    with get_connection() as conn:
        with conn.cursor() as cur:
            extras.execute_batch(cur, sql, rows, page_size=page_size)
        conn.commit()


def execute(sql: str, params: Sequence | None = None) -> None:
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
        conn.commit()


def fetch_all(sql: str, params: Sequence | None = None) -> list[tuple]:
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            return cur.fetchall()


def fetch_one(sql: str, params: Sequence | None = None) -> tuple | None:
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            return cur.fetchone()


def _normalize_dataframe(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty:
        return df
    normalized = df.copy()
    for column in normalized.columns:
        series = normalized[column]
        if series.dtype == object:
            normalized[column] = series.map(lambda value: float(value) if isinstance(value, Decimal) else value)
    return normalized


def query_dataframe(sql: str, params: Sequence | None = None) -> pd.DataFrame:
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()
            columns = [desc[0] for desc in cur.description] if cur.description else []
        return _normalize_dataframe(pd.DataFrame(rows, columns=columns))
