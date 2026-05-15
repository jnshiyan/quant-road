from __future__ import annotations

import logging
import os
from contextlib import contextmanager
from datetime import datetime

import akshare as ak
import pandas as pd

from quant_road.config import settings
from quant_road.db import batch_execute, fetch_all

logger = logging.getLogger(__name__)
_PROXY_ENV_KEYS = ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy")


@contextmanager
def _without_proxy_env():
    removed: dict[str, str] = {}
    for key in _PROXY_ENV_KEYS:
        value = os.environ.pop(key, None)
        if value is not None:
            removed[key] = value
    try:
        yield
    finally:
        os.environ.update(removed)


def _call_akshare(fn, *args, **kwargs):
    with _without_proxy_env():
        return fn(*args, **kwargs)


def _normalize_symbol(code: str) -> str:
    return str(code).zfill(6)


def _resolve_symbols(symbols: list[str] | None) -> list[str]:
    if symbols:
        return [_normalize_symbol(symbol) for symbol in symbols]
    rows = fetch_all(
        """
        SELECT stock_code
        FROM stock_basic
        WHERE COALESCE(is_st, 0) = 0
        ORDER BY stock_code
        """
    )
    if rows:
        return [_normalize_symbol(row[0]) for row in rows]
    if settings.target_stocks:
        return [_normalize_symbol(symbol) for symbol in settings.target_stocks]
    return [_normalize_symbol(row[0]) for row in rows]


def _upsert_stock_basic(rows: list[tuple[str, str | None, str | None, int, datetime.date | None]]) -> None:
    if not rows:
        return
    sql = """
        INSERT INTO stock_basic (stock_code, stock_name, industry, is_st, list_date)
        VALUES (%s, %s, %s, %s, %s)
        ON CONFLICT (stock_code) DO UPDATE SET
            stock_name = COALESCE(EXCLUDED.stock_name, stock_basic.stock_name),
            industry = COALESCE(EXCLUDED.industry, stock_basic.industry),
            is_st = EXCLUDED.is_st,
            list_date = COALESCE(EXCLUDED.list_date, stock_basic.list_date)
    """
    batch_execute(sql, rows)


def _load_etf_name_map(symbols: list[str]) -> dict[str, str]:
    symbol_set = {str(symbol).zfill(6) for symbol in symbols}
    if not symbol_set:
        return {}

    try:
        df = _call_akshare(ak.fund_etf_spot_em)
    except Exception as ex:
        logger.warning("Failed to load ETF basic snapshot from AKShare: %s", ex)
        return {}
    if df.empty or "代码" not in df.columns or "名称" not in df.columns:
        return {}

    matched = df[df["代码"].astype(str).str.zfill(6).isin(symbol_set)]
    payload: dict[str, str] = {}
    for _, row in matched.iterrows():
        code = _normalize_symbol(row["代码"])
        name = str(row["名称"]).strip()
        if name:
            payload[code] = name
    return payload


def _load_daily_frame(
    symbol: str,
    start_date: str,
    end_date: str,
    adjust: str,
) -> tuple[pd.DataFrame, str]:
    df = _call_akshare(
        ak.stock_zh_a_hist,
        symbol=symbol,
        period="daily",
        start_date=start_date,
        end_date=end_date,
        adjust=adjust,
    )
    if not df.empty:
        return df, "stock"

    df = _call_akshare(
        ak.fund_etf_hist_em,
        symbol=symbol,
        period="daily",
        start_date=start_date,
        end_date=end_date,
        adjust=adjust,
    )
    if not df.empty:
        return df, "etf"
    return pd.DataFrame(), "unknown"


def sync_stock_basic() -> dict[str, object]:
    try:
        df = _call_akshare(ak.stock_zh_a_spot_em)
    except Exception as ex:
        fallback_rows = fetch_all(
            """
            SELECT stock_code
            FROM stock_basic
            ORDER BY stock_code
            """
        )
        if fallback_rows:
            logger.warning(
                "Failed to sync stock basic from AKShare, fallback to existing stock_basic rows: %s",
                ex,
            )
            return {
                "source": "stock_basic_fallback",
                "usedFallback": True,
                "totalCount": len(fallback_rows),
            }
        raise
    if df.empty:
        logger.warning("No stock basic data returned from AKShare.")
        return {
            "source": "akshare",
            "usedFallback": False,
            "totalCount": 0,
        }

    industry_column = "行业" if "行业" in df.columns else None
    list_date_column = "上市时间" if "上市时间" in df.columns else None
    rows = []
    for _, row in df.iterrows():
        stock_code = _normalize_symbol(row["代码"])
        stock_name = str(row["名称"]).strip()
        industry = str(row[industry_column]).strip() if industry_column and pd.notna(row[industry_column]) else None
        raw_list_date = row[list_date_column] if list_date_column else None
        list_date = None
        if raw_list_date is not None and pd.notna(raw_list_date):
            text = str(raw_list_date).strip()
            if text.isdigit() and len(text) == 8:
                list_date = datetime.strptime(text, "%Y%m%d").date()
        is_st = 1 if "ST" in stock_name.upper() else 0
        rows.append((stock_code, stock_name, industry, is_st, list_date))

    _upsert_stock_basic(rows)
    logger.info("Synced %s stock basic records.", len(rows))
    return {
        "source": "akshare",
        "usedFallback": False,
        "totalCount": len(rows),
    }


def sync_stock_daily(
    symbols: list[str] | None = None,
    start_date: str | None = None,
    end_date: str | None = None,
    adjust: str = "qfq",
) -> dict[str, object]:
    resolved_symbols = _resolve_symbols(symbols)
    if not resolved_symbols:
        logger.warning("No symbols resolved for daily sync.")
        return {
            "requestedSymbols": [],
            "successSymbols": [],
            "skippedSymbols": [],
            "failedSymbols": [],
            "requestedCount": 0,
            "successCount": 0,
            "skippedCount": 0,
            "failedCount": 0,
            "upsertedRows": 0,
        }

    start_date = start_date or settings.default_start_date
    end_date = end_date or datetime.now().strftime("%Y%m%d")
    total_rows = 0
    etf_name_map = _load_etf_name_map(resolved_symbols)
    success_symbols: list[str] = []
    skipped_symbols: list[str] = []
    failed_symbols: list[str] = []

    for symbol in resolved_symbols:
        try:
            df, source = _load_daily_frame(
                symbol=symbol,
                start_date=start_date,
                end_date=end_date,
                adjust=adjust,
            )
        except Exception as ex:
            logger.warning("Failed to sync daily data for %s, skip current symbol: %s", symbol, ex)
            failed_symbols.append(symbol)
            continue
        if df.empty:
            logger.warning("No daily data returned for %s.", symbol)
            skipped_symbols.append(symbol)
            continue

        standardized = df[["日期", "开盘", "收盘", "最高", "最低", "成交量"]].copy()
        standardized.columns = ["trade_date", "open", "close", "high", "low", "volume"]
        standardized["trade_date"] = pd.to_datetime(standardized["trade_date"])
        standardized["ma20"] = standardized["close"].rolling(20).mean().round(2)
        standardized["ma60"] = standardized["close"].rolling(60).mean().round(2)
        standardized["stock_code"] = symbol
        standardized = standardized.dropna(subset=["trade_date"])

        rows = [
            (
                row.stock_code,
                row.trade_date.date(),
                float(row.open),
                float(row.close),
                float(row.high),
                float(row.low),
                int(row.volume),
                float(row.ma20) if pd.notna(row.ma20) else None,
                float(row.ma60) if pd.notna(row.ma60) else None,
            )
            for row in standardized.itertuples(index=False)
        ]
        if source == "etf":
            _upsert_stock_basic(
                [(
                    symbol,
                    etf_name_map.get(symbol),
                    "ETF",
                    0,
                    None,
                )]
            )
        sql = """
            INSERT INTO stock_daily (
                stock_code, trade_date, open, close, high, low, volume, ma20, ma60
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (stock_code, trade_date) DO UPDATE SET
                open = EXCLUDED.open,
                close = EXCLUDED.close,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                volume = EXCLUDED.volume,
                ma20 = EXCLUDED.ma20,
                ma60 = EXCLUDED.ma60
        """
        batch_execute(sql, rows)
        total_rows += len(rows)
        success_symbols.append(symbol)
        logger.info("Synced %s daily rows for %s.", len(rows), symbol)

    logger.info("Daily sync completed, total rows upserted: %s.", total_rows)
    return {
        "requestedSymbols": resolved_symbols,
        "successSymbols": success_symbols,
        "skippedSymbols": skipped_symbols,
        "failedSymbols": failed_symbols,
        "requestedCount": len(resolved_symbols),
        "successCount": len(success_symbols),
        "skippedCount": len(skipped_symbols),
        "failedCount": len(failed_symbols),
        "upsertedRows": total_rows,
    }
