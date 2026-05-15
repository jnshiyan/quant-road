from __future__ import annotations

import pandas as pd

from quant_road.db import query_dataframe


def load_stock_history_batch(
    symbols: list[str],
    start_date: str,
    end_date: str | None = None,
) -> dict[str, pd.DataFrame]:
    if not symbols:
        return {}
    sql = """
        SELECT stock_code, trade_date, open, high, low, close, volume
        FROM stock_daily
        WHERE stock_code = ANY(%s)
          AND trade_date >= %s
          AND (%s IS NULL OR trade_date <= %s)
        ORDER BY stock_code, trade_date
    """
    df = query_dataframe(sql, (symbols, start_date, end_date, end_date))
    if df.empty:
        return {}

    grouped: dict[str, pd.DataFrame] = {}
    for stock_code, item in df.groupby("stock_code", sort=False):
        frame = item.drop(columns=["stock_code"]).copy()
        frame["trade_date"] = pd.to_datetime(frame["trade_date"])
        grouped[str(stock_code).zfill(6)] = frame.set_index("trade_date")
    return grouped
