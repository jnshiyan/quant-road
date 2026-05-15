from __future__ import annotations

from datetime import datetime

import pandas as pd

from quant_road.db import query_dataframe
from quant_road.services.market_data_batch_service import load_stock_history_batch


from quant_road.config import settings


def load_stock_history(stock_code: str, start_date: str | None = None) -> pd.DataFrame:
    resolved_start_date = start_date or settings.strategy_backtest_start_date
    return load_stock_history_batch([stock_code], start_date=resolved_start_date).get(str(stock_code).zfill(6), pd.DataFrame())


def latest_close_map() -> dict[str, float]:
    sql = """
        SELECT DISTINCT ON (stock_code) stock_code, close
        FROM stock_daily
        ORDER BY stock_code, trade_date DESC
    """
    rows = query_dataframe(sql)
    if rows.empty:
        return {}
    return {str(row["stock_code"]).zfill(6): float(row["close"]) for _, row in rows.iterrows()}


def today_iso() -> str:
    return datetime.now().strftime("%Y-%m-%d")
