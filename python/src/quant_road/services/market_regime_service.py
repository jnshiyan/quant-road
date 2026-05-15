from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime, timedelta

import akshare as ak
import pandas as pd

from quant_road.db import batch_execute, fetch_all, fetch_one

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class MarketSnapshot:
    trade_date: str
    hs300_close: float
    hs300_ma20: float
    hs300_ma60: float
    hs300_above_ma20: bool
    hs300_above_ma60: bool
    up_ratio: float
    raw_status: str
    status: str
    remark: str


def classify_market_status(
    hs300_above_ma20: bool,
    hs300_above_ma60: bool,
    up_ratio: float,
    hs300_ma20: float | None,
    hs300_ma60: float | None,
) -> str:
    ratio = max(0.0, min(1.0, float(up_ratio)))
    if hs300_above_ma60 and ratio >= 0.70:
        return "bull"
    if (not hs300_above_ma60) and ratio <= 0.20:
        return "panic"
    if (not hs300_above_ma60) and hs300_ma20 is not None and hs300_ma60 is not None and hs300_ma20 <= hs300_ma60:
        return "bear"
    return "volatile"


def apply_status_hysteresis(candidate: str, history: list[str], hold_days: int = 2) -> str:
    normalized_candidate = str(candidate).strip().lower()
    normalized_history = [str(item).strip().lower() for item in history if str(item).strip()]
    if normalized_candidate == "panic":
        return "panic"
    if not normalized_history:
        return normalized_candidate
    latest = normalized_history[0]
    if latest == normalized_candidate:
        return latest
    if hold_days <= 1:
        return normalized_candidate
    needed = max(1, hold_days - 1)
    if len(normalized_history) >= needed and all(item == normalized_candidate for item in normalized_history[:needed]):
        return normalized_candidate
    return latest


def _normalize_hs300_frame(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty:
        return pd.DataFrame(columns=["trade_date", "close"])
    renamed = df.rename(
        columns={
            "日期": "trade_date",
            "date": "trade_date",
            "收盘": "close",
            "close": "close",
        }
    )
    if "trade_date" not in renamed.columns or "close" not in renamed.columns:
        return pd.DataFrame(columns=["trade_date", "close"])
    normalized = renamed[["trade_date", "close"]].copy()
    normalized["trade_date"] = pd.to_datetime(normalized["trade_date"])
    normalized["close"] = pd.to_numeric(normalized["close"], errors="coerce")
    normalized = normalized.dropna(subset=["trade_date", "close"]).sort_values("trade_date")
    return normalized


def _load_hs300_from_ak() -> pd.DataFrame:
    end = datetime.now()
    start = end - timedelta(days=240)
    try:
        df = ak.index_zh_a_hist(
            symbol="000300",
            period="daily",
            start_date=start.strftime("%Y%m%d"),
            end_date=end.strftime("%Y%m%d"),
        )
        normalized = _normalize_hs300_frame(df)
        if not normalized.empty:
            return normalized
    except Exception:
        logger.exception("Failed to load HS300 from AKShare.")
    return pd.DataFrame(columns=["trade_date", "close"])


def _load_hs300_from_db() -> pd.DataFrame:
    rows = fetch_all(
        """
        SELECT trade_date, close
        FROM stock_daily
        WHERE stock_code = %s
        ORDER BY trade_date
        """,
        ("000300",),
    )
    if not rows:
        return pd.DataFrame(columns=["trade_date", "close"])
    frame = pd.DataFrame(rows, columns=["trade_date", "close"])
    return _normalize_hs300_frame(frame)


def _load_hs300_series() -> pd.DataFrame:
    from_ak = _load_hs300_from_ak()
    if not from_ak.empty:
        return from_ak
    from_db = _load_hs300_from_db()
    if not from_db.empty:
        return from_db
    raise RuntimeError("Unable to load HS300 series from AKShare or PostgreSQL stock_daily.")


def _calc_up_ratio(reference_trade_date: str) -> float:
    row = fetch_one(
        """
        WITH ranked AS (
            SELECT
                stock_code,
                trade_date,
                close,
                LAG(close) OVER (PARTITION BY stock_code ORDER BY trade_date) AS prev_close
            FROM stock_daily
            WHERE trade_date <= %s
        ),
        latest AS (
            SELECT DISTINCT ON (stock_code) stock_code, close, prev_close
            FROM ranked
            WHERE prev_close IS NOT NULL
            ORDER BY stock_code, trade_date DESC
        )
        SELECT
            CASE
                WHEN COUNT(1) = 0 THEN NULL
                ELSE SUM(CASE WHEN close > prev_close THEN 1 ELSE 0 END)::numeric / COUNT(1)
            END AS up_ratio
        FROM latest
        """,
        (reference_trade_date,),
    )
    if row is None or row[0] is None:
        return 0.50
    value = float(row[0])
    return max(0.0, min(1.0, value))


def _raw_status_history(limit: int = 5) -> list[str]:
    rows = fetch_all(
        """
        SELECT COALESCE(raw_status, status)
        FROM market_status
        ORDER BY trade_date DESC, id DESC
        LIMIT %s
        """,
        (limit,),
    )
    return [str(row[0]).strip().lower() for row in rows if row and row[0] is not None]


def evaluate_market_status(hold_days: int = 2) -> MarketSnapshot:
    hs300 = _load_hs300_series().copy()
    hs300["ma20"] = hs300["close"].rolling(20).mean()
    hs300["ma60"] = hs300["close"].rolling(60).mean()
    latest = hs300.dropna(subset=["ma20", "ma60"]).iloc[-1]

    trade_date = latest["trade_date"].strftime("%Y-%m-%d")
    hs300_close = float(latest["close"])
    hs300_ma20 = float(latest["ma20"])
    hs300_ma60 = float(latest["ma60"])
    hs300_above_ma20 = hs300_close > hs300_ma20
    hs300_above_ma60 = hs300_close > hs300_ma60
    up_ratio = _calc_up_ratio(trade_date)

    raw_status = classify_market_status(
        hs300_above_ma20=hs300_above_ma20,
        hs300_above_ma60=hs300_above_ma60,
        up_ratio=up_ratio,
        hs300_ma20=hs300_ma20,
        hs300_ma60=hs300_ma60,
    )
    status = apply_status_hysteresis(raw_status, _raw_status_history(limit=max(hold_days, 5)), hold_days=hold_days)
    remark = (
        f"raw={raw_status}, hs300_close={hs300_close:.2f}, ma20={hs300_ma20:.2f}, "
        f"ma60={hs300_ma60:.2f}, up_ratio={up_ratio:.2%}"
    )
    return MarketSnapshot(
        trade_date=trade_date,
        hs300_close=hs300_close,
        hs300_ma20=hs300_ma20,
        hs300_ma60=hs300_ma60,
        hs300_above_ma20=hs300_above_ma20,
        hs300_above_ma60=hs300_above_ma60,
        up_ratio=up_ratio,
        raw_status=raw_status,
        status=status,
        remark=remark,
    )


def save_market_status(snapshot: MarketSnapshot) -> None:
    sql = """
        INSERT INTO market_status (
            trade_date,
            status,
            raw_status,
            hs300_close,
            hs300_ma20,
            hs300_ma60,
            hs300_above_ma20,
            hs300_above_ma60,
            up_ratio,
            remark,
            update_time
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
        ON CONFLICT (trade_date) DO UPDATE SET
            status = EXCLUDED.status,
            raw_status = EXCLUDED.raw_status,
            hs300_close = EXCLUDED.hs300_close,
            hs300_ma20 = EXCLUDED.hs300_ma20,
            hs300_ma60 = EXCLUDED.hs300_ma60,
            hs300_above_ma20 = EXCLUDED.hs300_above_ma20,
            hs300_above_ma60 = EXCLUDED.hs300_above_ma60,
            up_ratio = EXCLUDED.up_ratio,
            remark = EXCLUDED.remark,
            update_time = NOW()
    """
    batch_execute(
        sql,
        [(
            snapshot.trade_date,
            snapshot.status,
            snapshot.raw_status,
            snapshot.hs300_close,
            snapshot.hs300_ma20,
            snapshot.hs300_ma60,
            1 if snapshot.hs300_above_ma20 else 0,
            1 if snapshot.hs300_above_ma60 else 0,
            round(snapshot.up_ratio, 6),
            snapshot.remark,
        )],
    )


def evaluate_and_save_market_status(hold_days: int = 2) -> dict:
    try:
        snapshot = evaluate_market_status(hold_days=hold_days)
    except Exception as ex:
        logger.warning("Evaluate market status failed, fallback to previous persisted status: %s", ex)
        latest = fetch_latest_market_status()
        if latest is None:
            raise
        today = datetime.now().strftime("%Y-%m-%d")
        fallback_status = str(latest.get("status") or "volatile").strip().lower() or "volatile"
        fallback_raw_status = str(latest.get("raw_status") or fallback_status).strip().lower() or fallback_status
        snapshot = MarketSnapshot(
            trade_date=today,
            hs300_close=float(latest.get("hs300_close") or 0.0),
            hs300_ma20=float(latest.get("hs300_ma20") or 0.0),
            hs300_ma60=float(latest.get("hs300_ma60") or 0.0),
            hs300_above_ma20=bool(latest.get("hs300_above_ma20")),
            hs300_above_ma60=bool(latest.get("hs300_above_ma60")),
            up_ratio=float(latest.get("up_ratio") or 0.0),
            raw_status=fallback_raw_status,
            status=fallback_status,
            remark=f"fallback_last_status due_to_error={ex}",
        )
    save_market_status(snapshot)
    return {
        "trade_date": snapshot.trade_date,
        "status": snapshot.status,
        "raw_status": snapshot.raw_status,
        "hs300_close": snapshot.hs300_close,
        "hs300_ma20": snapshot.hs300_ma20,
        "hs300_ma60": snapshot.hs300_ma60,
        "hs300_above_ma20": snapshot.hs300_above_ma20,
        "hs300_above_ma60": snapshot.hs300_above_ma60,
        "up_ratio": snapshot.up_ratio,
        "remark": snapshot.remark,
    }


def fetch_latest_market_status() -> dict | None:
    row = fetch_one(
        """
        SELECT
            trade_date,
            status,
            raw_status,
            hs300_close,
            hs300_ma20,
            hs300_ma60,
            hs300_above_ma20,
            hs300_above_ma60,
            up_ratio,
            remark,
            update_time
        FROM market_status
        ORDER BY trade_date DESC, id DESC
        LIMIT 1
        """
    )
    if row is None:
        return None
    return {
        "trade_date": row[0].isoformat() if row[0] is not None else None,
        "status": row[1],
        "raw_status": row[2],
        "hs300_close": float(row[3]) if row[3] is not None else None,
        "hs300_ma20": float(row[4]) if row[4] is not None else None,
        "hs300_ma60": float(row[5]) if row[5] is not None else None,
        "hs300_above_ma20": bool(row[6]) if row[6] is not None else None,
        "hs300_above_ma60": bool(row[7]) if row[7] is not None else None,
        "up_ratio": float(row[8]) if row[8] is not None else None,
        "remark": row[9],
        "update_time": row[10].isoformat(sep=" ") if row[10] is not None else None,
    }
