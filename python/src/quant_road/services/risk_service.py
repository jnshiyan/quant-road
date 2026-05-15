from __future__ import annotations

import json
import logging
from datetime import datetime, timedelta
from functools import lru_cache

import akshare as ak
import pandas as pd

from quant_road.config import settings
from quant_road.db import batch_execute, fetch_all, fetch_one
from quant_road.services.market_service import latest_close_map
from quant_road.strategies.backtest_engine import BacktestMetrics

logger = logging.getLogger(__name__)


def _save_stop_loss_signal(stock_code: str, latest_price: float, strategy_id: int) -> None:
    sql = """
        INSERT INTO trade_signal (
            stock_code, stock_name, signal_type, suggest_price, signal_date, strategy_id
        )
        VALUES (
            %s,
            (SELECT stock_name FROM stock_basic WHERE stock_code = %s),
            'SELL',
            %s,
            CURRENT_DATE,
            %s
        )
        ON CONFLICT (stock_code, signal_type, signal_date, strategy_id) DO UPDATE SET
            suggest_price = EXCLUDED.suggest_price
    """
    batch_execute(sql, [(stock_code, stock_code, latest_price, strategy_id)])


def _load_stop_loss_rate(strategy_id: int) -> float:
    row = fetch_one("SELECT params FROM strategy_config WHERE id = %s", (strategy_id,))
    if row is None or row[0] is None:
        return settings.stop_loss_rate
    params = dict(row[0]) if isinstance(row[0], dict) else json.loads(row[0])
    return float(params.get("stop_loss_rate", settings.stop_loss_rate))


def update_position_risk(strategy_id: int | None = None) -> int:
    strategy_id = strategy_id or settings.strategy_id
    stop_loss_rate = _load_stop_loss_rate(strategy_id)
    positions = fetch_all(
        """
        SELECT stock_code, quantity, cost_price
        FROM position
        ORDER BY stock_code
        """
    )
    if not positions:
        logger.info("No positions found, skip stop-loss evaluation.")
        return 0

    close_map = latest_close_map()
    rows = []
    for stock_code, quantity, cost_price in positions:
        latest_price = close_map.get(str(stock_code).zfill(6))
        if latest_price is None:
            continue
        float_profit = ((latest_price - float(cost_price)) / float(cost_price)) * 100
        loss_warning = 1 if float_profit <= (-stop_loss_rate * 100) else 0
        rows.append((latest_price, round(float_profit, 4), loss_warning, stock_code))
        if loss_warning == 1:
            _save_stop_loss_signal(str(stock_code).zfill(6), latest_price, strategy_id)

    sql = """
        UPDATE position
        SET current_price = %s,
            float_profit = %s,
            loss_warning = %s,
            update_time = NOW()
        WHERE stock_code = %s
    """
    batch_execute(sql, rows)
    logger.info("Updated %s position risk snapshots.", len(rows))
    return len(rows)


def _normalize_benchmark_frame(df: pd.DataFrame) -> pd.DataFrame:
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
    return normalized.dropna(subset=["trade_date", "close"]).sort_values("trade_date")


def _load_benchmark_frame_from_db(start_date: str, end_date: str) -> pd.DataFrame:
    rows = fetch_all(
        """
        SELECT trade_date, close
        FROM stock_daily
        WHERE stock_code = %s
          AND trade_date BETWEEN %s AND %s
        ORDER BY trade_date
        """,
        ("000300", start_date, end_date),
    )
    if not rows:
        return pd.DataFrame(columns=["trade_date", "close"])
    return _normalize_benchmark_frame(pd.DataFrame(rows, columns=["trade_date", "close"]))


@lru_cache(maxsize=8)
def fetch_benchmark_monthly_returns(months: int = 3, end_date_iso: str | None = None) -> pd.Series:
    # Cache benchmark by month window and end date to avoid repeated external calls in one run.
    # end_date_iso format: YYYY-MM-DD
    if end_date_iso:
        end_dt = datetime.strptime(end_date_iso, "%Y-%m-%d")
    else:
        end_dt = datetime.now()
    start_date = (end_dt - timedelta(days=months * 35)).strftime("%Y%m%d")
    end_date = end_dt.strftime("%Y%m%d")
    try:
        df = ak.index_zh_a_hist(symbol="000300", period="daily", start_date=start_date, end_date=end_date)
        data = _normalize_benchmark_frame(df)
    except Exception as ex:
        logger.warning("Failed to load HS300 benchmark from AKShare, fallback to PostgreSQL stock_daily: %s", ex)
        data = _load_benchmark_frame_from_db(
            start_date=datetime.strptime(start_date, "%Y%m%d").strftime("%Y-%m-%d"),
            end_date=datetime.strptime(end_date, "%Y%m%d").strftime("%Y-%m-%d"),
        )
    if data.empty:
        return pd.Series(dtype=float)
    data = data.set_index("trade_date")
    return data["close"].resample("ME").last().pct_change().dropna()


def _monthly_max_drawdown(equity_curve: pd.Series) -> float:
    if equity_curve.empty:
        return 0.0
    monthly_max_drawdowns: list[float] = []
    for _, month_curve in equity_curve.groupby(pd.Grouper(freq="ME")):
        if len(month_curve) < 2:
            continue
        drawdown = ((month_curve / month_curve.cummax()) - 1.0).min() * 100
        monthly_max_drawdowns.append(float(drawdown))
    if not monthly_max_drawdowns:
        return 0.0
    return min(monthly_max_drawdowns)


def _latest_month_win_rate(strategy_returns: pd.Series) -> float | None:
    if strategy_returns.empty:
        return None
    monthly_win_rate = strategy_returns.groupby(pd.Grouper(freq="ME")).apply(
        lambda values: float((values > 0).mean() * 100) if len(values) > 0 else None
    ).dropna()
    if monthly_win_rate.empty:
        return None
    return float(monthly_win_rate.iloc[-1])


def evaluate_strategy_invalid(
    metrics: BacktestMetrics,
    benchmark_monthly_returns: pd.Series | None = None,
    rules: dict | None = None,
) -> tuple[bool, str]:
    resolved_rules = {
        "monthly_max_drawdown_limit_pct": 12.0,
        "latest_month_win_rate_min_pct": 40.0,
        "max_consecutive_losses": 6,
        "underperform_months": 2,
        "invalid_trigger_count": 2,
    }
    if rules:
        resolved_rules.update(rules)

    monthly_max_drawdown_limit_pct = float(resolved_rules["monthly_max_drawdown_limit_pct"])
    latest_month_win_rate_min_pct = float(resolved_rules["latest_month_win_rate_min_pct"])
    max_consecutive_losses = int(resolved_rules["max_consecutive_losses"])
    underperform_months = max(1, int(resolved_rules["underperform_months"]))
    invalid_trigger_count = max(1, int(resolved_rules["invalid_trigger_count"]))

    criteria: list[str] = []

    monthly_max_drawdown = _monthly_max_drawdown(metrics.equity_curve)
    if abs(monthly_max_drawdown) > monthly_max_drawdown_limit_pct:
        criteria.append("monthly_max_drawdown_over_limit")
    latest_month_win_rate = _latest_month_win_rate(metrics.strategy_returns)
    if latest_month_win_rate is not None and latest_month_win_rate < latest_month_win_rate_min_pct:
        criteria.append("latest_month_win_rate_below_limit")
    if metrics.consecutive_losses >= max_consecutive_losses:
        criteria.append("consecutive_losses_over_limit")

    benchmark_monthly = benchmark_monthly_returns if benchmark_monthly_returns is not None else fetch_benchmark_monthly_returns()
    strategy_monthly = metrics.monthly_returns
    if len(benchmark_monthly) >= underperform_months and len(strategy_monthly) >= underperform_months:
        aligned = pd.concat(
            [strategy_monthly.rename("strategy"), benchmark_monthly.rename("benchmark")],
            axis=1,
            join="inner",
        ).dropna()
        if len(aligned) >= underperform_months and (
            aligned["strategy"].tail(underperform_months) < aligned["benchmark"].tail(underperform_months)
        ).all():
            criteria.append("underperform_hs300_for_recent_months")

    if len(criteria) >= invalid_trigger_count:
        return True, ", ".join(criteria)
    if not criteria:
        return False, "healthy"
    return False, f"warning_only: {', '.join(criteria)}"
