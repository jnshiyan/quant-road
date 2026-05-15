from __future__ import annotations

import logging
import re
from datetime import datetime

import akshare as ak
import pandas as pd

from quant_road.db import batch_execute, fetch_all

logger = logging.getLogger(__name__)

DEFAULT_INDEX_CODES: tuple[str, ...] = ("000300", "000905", "000852", "399006")


def normalize_index_code(raw_code: str | None) -> str:
    if raw_code is None:
        return ""
    digits = re.sub(r"\D", "", str(raw_code))
    if len(digits) >= 6:
        return digits[-6:]
    return digits.zfill(6) if digits else ""


def compute_percentile(value: float | None, history: list[float]) -> float | None:
    if value is None:
        return None
    cleaned = [float(item) for item in history if item is not None]
    if not cleaned:
        return None
    lower_or_equal = sum(1 for item in cleaned if item <= value)
    return round((lower_or_equal / len(cleaned)) * 100, 2)


def _pick_first(row: pd.Series, candidates: tuple[str, ...]) -> object:
    for column in candidates:
        if column in row and row[column] is not None and str(row[column]).strip() != "":
            return row[column]
    return None


def _safe_float(value: object) -> float | None:
    if value is None:
        return None
    text = str(value).strip().replace("%", "")
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def _fetch_raw_valuation_frame() -> pd.DataFrame:
    try:
        if hasattr(ak, "index_value_name_funddb"):
            return ak.index_value_name_funddb()
        logger.warning("AKShare has no index_value_name_funddb; fallback to alternative valuation providers.")
        return pd.DataFrame()
    except Exception:
        logger.exception("Failed to fetch valuation frame from AKShare.")
        return pd.DataFrame()


def _latest_non_null(df: pd.DataFrame, date_col: str, value_col: str) -> float | None:
    if df.empty or date_col not in df.columns or value_col not in df.columns:
        return None
    temp = df[[date_col, value_col]].copy()
    temp[date_col] = pd.to_datetime(temp[date_col], errors="coerce")
    temp[value_col] = pd.to_numeric(temp[value_col], errors="coerce")
    temp = temp.dropna(subset=[date_col, value_col]).sort_values(date_col)
    if temp.empty:
        return None
    return float(temp.iloc[-1][value_col])


def _fallback_quotes_from_lg() -> dict[str, dict]:
    try:
        pe_df = ak.stock_index_pe_lg()
        pb_df = ak.stock_index_pb_lg()
    except Exception:
        logger.exception("Failed to fetch valuation fallback from stock_index_pe_lg/stock_index_pb_lg.")
        return {}

    pe = _latest_non_null(pe_df, "日期", "滚动市盈率")
    if pe is None:
        pe = _latest_non_null(pe_df, "日期", "静态市盈率")
    pb = _latest_non_null(pb_df, "日期", "市净率")
    if pe is None and pb is None:
        return {}
    return {
        "000300": {
            "index_code": "000300",
            "index_name": "沪深300",
            "pe": pe,
            "pb": pb,
            "source": "akshare:stock_index_pe_pb_lg",
        }
    }


def _fallback_quotes_from_csindex(index_codes: list[str]) -> dict[str, dict]:
    try:
        frame = ak.stock_zh_index_value_csindex()
    except Exception:
        logger.exception("Failed to fetch valuation fallback from stock_zh_index_value_csindex.")
        return {}
    if frame is None or frame.empty:
        return {}

    renamed = frame.rename(
        columns={
            "指数代码": "index_code",
            "指数中文简称": "index_name",
            "市盈率1": "pe",
            "市净率": "pb",
            "日期": "update_date",
        }
    )
    required = {"index_code", "index_name", "pe"}
    if not required.issubset(set(renamed.columns)):
        return {}
    renamed["index_code"] = renamed["index_code"].apply(normalize_index_code)
    renamed["update_date"] = pd.to_datetime(renamed.get("update_date"), errors="coerce")
    renamed = renamed.sort_values("update_date")

    target_set = {normalize_index_code(code) for code in index_codes}
    payload: dict[str, dict] = {}
    for code in target_set:
        subset = renamed[renamed["index_code"] == code]
        if subset.empty:
            continue
        last = subset.iloc[-1]
        payload[code] = {
            "index_code": code,
            "index_name": str(last.get("index_name") or code),
            "pe": _safe_float(last.get("pe")),
            "pb": _safe_float(last.get("pb")),
            "source": "akshare:stock_zh_index_value_csindex",
        }
    return payload


def _extract_quotes(raw: pd.DataFrame) -> dict[str, dict]:
    if raw.empty:
        return {}
    quotes: dict[str, dict] = {}
    for _, row in raw.iterrows():
        code = normalize_index_code(
            _pick_first(row, ("指数代码", "代码", "index_code", "symbol", "基金代码", "标的代码"))
        )
        if not code:
            continue
        name_value = _pick_first(row, ("指数名称", "名称", "index_name", "name", "基金名称", "标的名称"))
        pe_value = _pick_first(row, ("市盈率", "PE", "pe", "滚动市盈率"))
        pb_value = _pick_first(row, ("市净率", "PB", "pb", "滚动市净率"))
        quotes[code] = {
            "index_code": code,
            "index_name": None if name_value is None else str(name_value).strip(),
            "pe": _safe_float(pe_value),
            "pb": _safe_float(pb_value),
            "source": "akshare:index_value_name_funddb",
        }
    return quotes


def _metric_history(index_code: str, metric_column: str, lookback_days: int = 3650) -> list[float]:
    rows = fetch_all(
        f"""
        SELECT {metric_column}
        FROM index_valuation
        WHERE index_code = %s
          AND update_date >= (CURRENT_DATE - (%s * INTERVAL '1 day'))
          AND {metric_column} IS NOT NULL
        ORDER BY update_date
        """,
        (index_code, lookback_days),
    )
    return [float(row[0]) for row in rows if row and row[0] is not None]


def sync_index_valuation(index_codes: list[str] | None = None, update_date: str | None = None) -> list[dict]:
    targets = [normalize_index_code(code) for code in (index_codes or list(DEFAULT_INDEX_CODES))]
    targets = [code for code in targets if code]
    if not targets:
        return []

    quotes = _extract_quotes(_fetch_raw_valuation_frame())
    if not quotes:
        quotes = _fallback_quotes_from_lg()
    if not quotes:
        quotes = _fallback_quotes_from_csindex(targets)
    if not quotes:
        logger.warning("No valuation quotes fetched from upstream.")
        return []

    date_str = update_date or datetime.now().strftime("%Y-%m-%d")
    rows: list[tuple] = []
    payload: list[dict] = []
    for code in targets:
        quote = quotes.get(code)
        if quote is None:
            continue
        pe = quote.get("pe")
        pb = quote.get("pb")
        if pe is None and pb is None:
            continue
        pe_history = _metric_history(code, "pe")
        pb_history = _metric_history(code, "pb")
        pe_percentile = compute_percentile(pe, pe_history + ([pe] if pe is not None else []))
        pb_percentile = compute_percentile(pb, pb_history + ([pb] if pb is not None else []))
        rows.append((
            code,
            quote.get("index_name"),
            pe,
            pb,
            pe_percentile,
            pb_percentile,
            quote.get("source"),
            date_str,
        ))
        payload.append(
            {
                "index_code": code,
                "index_name": quote.get("index_name"),
                "pe": pe,
                "pb": pb,
                "pe_percentile": pe_percentile,
                "pb_percentile": pb_percentile,
                "source": quote.get("source"),
                "update_date": date_str,
            }
        )

    if not rows:
        return []

    sql = """
        INSERT INTO index_valuation (
            index_code,
            index_name,
            pe,
            pb,
            pe_percentile,
            pb_percentile,
            source,
            update_date,
            update_time
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW())
        ON CONFLICT (index_code, update_date) DO UPDATE SET
            index_name = EXCLUDED.index_name,
            pe = EXCLUDED.pe,
            pb = EXCLUDED.pb,
            pe_percentile = EXCLUDED.pe_percentile,
            pb_percentile = EXCLUDED.pb_percentile,
            source = EXCLUDED.source,
            update_time = NOW()
    """
    batch_execute(sql, rows)
    return payload


def fetch_latest_index_valuations(limit: int = 20) -> list[dict]:
    safe_limit = max(1, min(int(limit), 200))
    rows = fetch_all(
        """
        SELECT *
        FROM (
            SELECT DISTINCT ON (index_code)
                index_code,
                index_name,
                pe,
                pb,
                pe_percentile,
                pb_percentile,
                source,
                update_date,
                update_time
            FROM index_valuation
            ORDER BY index_code, update_date DESC, id DESC
        ) latest
        ORDER BY pe_percentile ASC NULLS LAST, index_code
        LIMIT %s
        """,
        (safe_limit,),
    )
    return [
        {
            "index_code": row[0],
            "index_name": row[1],
            "pe": float(row[2]) if row[2] is not None else None,
            "pb": float(row[3]) if row[3] is not None else None,
            "pe_percentile": float(row[4]) if row[4] is not None else None,
            "pb_percentile": float(row[5]) if row[5] is not None else None,
            "source": row[6],
            "update_date": row[7].isoformat() if row[7] is not None else None,
            "update_time": row[8].isoformat(sep=" ") if row[8] is not None else None,
        }
        for row in rows
    ]
