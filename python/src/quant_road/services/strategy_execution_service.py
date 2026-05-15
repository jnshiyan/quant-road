from __future__ import annotations

import math
from typing import Any

import pandas as pd

from quant_road.config import settings
from quant_road.db import batch_execute, fetch_one
from quant_road.services.market_service import today_iso
from quant_road.services.risk_service import evaluate_strategy_invalid, fetch_benchmark_monthly_returns


def _stock_name(stock_code: str) -> str | None:
    row = fetch_one("SELECT stock_name FROM stock_basic WHERE stock_code = %s", (stock_code,))
    return None if row is None else row[0]


def _is_etf_symbol(stock_code: str) -> bool:
    row = fetch_one(
        """
        SELECT industry, stock_name
        FROM stock_basic
        WHERE stock_code = %s
        """,
        (stock_code,),
    )
    if row is None:
        return False
    industry = "" if row[0] is None else str(row[0]).strip().upper()
    stock_name = "" if row[1] is None else str(row[1]).strip().upper()
    return industry == "ETF" or "ETF" in stock_name


def _resolve_trade_cost_params(stock_code: str, params: dict) -> dict:
    resolved = dict(params)
    resolved.setdefault("commission_rate", settings.commission_rate)
    resolved.setdefault("slippage_rate", settings.slippage_rate)
    resolved.setdefault("stamp_duty_rate", settings.stamp_duty_rate)
    if _is_etf_symbol(stock_code):
        resolved["stamp_duty_rate"] = 0.0
    return resolved


def _save_trade_signal(stock_code: str, signal_type: str, price: float, strategy_id: int) -> None:
    sql = """
        INSERT INTO trade_signal (
            stock_code, stock_name, signal_type, suggest_price, signal_date, strategy_id
        )
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (stock_code, signal_type, signal_date, strategy_id) DO UPDATE SET
            stock_name = EXCLUDED.stock_name,
            suggest_price = EXCLUDED.suggest_price
    """
    batch_execute(
        sql,
        [(
            stock_code,
            _stock_name(stock_code),
            signal_type,
            price,
            today_iso(),
            strategy_id,
        )],
    )


def _save_run_log(
    strategy_id: int,
    is_invalid: bool,
    remark: str,
    annual_return: float,
    max_drawdown: float,
    win_rate: float,
    total_profit: float,
) -> None:
    sql = """
        INSERT INTO strategy_run_log (
            strategy_id, annual_return, max_drawdown, win_rate, total_profit, is_invalid, remark
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s)
    """
    batch_execute(
        sql,
        [(
            strategy_id,
            annual_return,
            max_drawdown,
            win_rate,
            total_profit,
            1 if is_invalid else 0,
            remark,
        )],
    )


def _safe_float(value: Any, default: float) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _resolve_invalid_rules(params: dict) -> dict:
    return {
        "monthly_max_drawdown_limit_pct": float(params.get("monthly_max_drawdown_limit_pct", 12.0)),
        "latest_month_win_rate_min_pct": float(params.get("latest_month_win_rate_min_pct", 40.0)),
        "max_consecutive_losses": int(params.get("max_consecutive_losses", 6)),
        "underperform_months": int(params.get("underperform_months", 2)),
        "invalid_trigger_count": int(params.get("invalid_trigger_count", 2)),
    }


def _suggest_buy_quantity(stock_code: str, latest_price: float, params: dict) -> tuple[int, str]:
    if latest_price <= 0:
        return 0, "invalid_price"

    portfolio_capital = float(params.get("portfolio_capital", 100000))
    max_single_position_pct = float(params.get("max_single_position_pct", settings.max_single_position_pct))
    max_total_position_pct = float(params.get("max_total_position_pct", settings.max_total_position_pct))
    if portfolio_capital <= 0:
        return 0, "invalid_portfolio_capital"

    total_row = fetch_one(
        """
        SELECT COALESCE(SUM(COALESCE(current_price, cost_price) * quantity), 0)
        FROM position
        """
    )
    total_market_value = float(total_row[0] or 0)
    max_total_market_value = portfolio_capital * max_total_position_pct
    remaining_total_budget = max_total_market_value - total_market_value
    if remaining_total_budget <= 0:
        return 0, "total_position_limit_reached"

    existing_row = fetch_one(
        """
        SELECT quantity, COALESCE(current_price, cost_price)
        FROM position
        WHERE stock_code = %s
        """,
        (stock_code,),
    )
    existing_market_value = 0.0
    if existing_row is not None:
        existing_market_value = float(existing_row[0]) * float(existing_row[1])

    max_single_market_value = portfolio_capital * max_single_position_pct
    remaining_single_budget = max_single_market_value - existing_market_value
    if remaining_single_budget <= 0:
        return 0, "single_position_limit_reached"

    available_budget = min(remaining_total_budget, remaining_single_budget)
    raw_quantity = math.floor(available_budget / latest_price / 100) * 100
    if raw_quantity <= 0:
        return 0, "insufficient_budget_for_one_lot"
    return int(raw_quantity), "ok"


def execute_strategy_frames(
    grouped_frames: dict[str, pd.DataFrame],
    executor,
    params: dict,
    strategy_id: int,
    actor: str,
    strategy_type: str,
    market_status: str | None,
) -> dict[str, dict]:
    invalid_rules = _resolve_invalid_rules(params)
    benchmark_monthly_returns = fetch_benchmark_monthly_returns(
        months=max(3, int(invalid_rules.get("underperform_months", 2)) + 1)
    )

    results: dict[str, dict] = {}
    for stock_code, df in grouped_frames.items():
        if df.empty:
            continue
        symbol_params = _resolve_trade_cost_params(stock_code, params)
        commission_rate = float(symbol_params.get("commission_rate", settings.commission_rate))
        slippage_rate = float(symbol_params.get("slippage_rate", settings.slippage_rate))
        stamp_duty_rate = float(symbol_params.get("stamp_duty_rate", settings.stamp_duty_rate))
        metrics = executor.run_backtest(
            df,
            symbol_params,
            commission_rate=commission_rate,
            slippage_rate=slippage_rate,
            stamp_duty_rate=stamp_duty_rate,
        )
        signal_type, price = executor.generate_signal(df, params)
        is_invalid, remark = evaluate_strategy_invalid(
            metrics,
            benchmark_monthly_returns=benchmark_monthly_returns,
            rules=invalid_rules,
        )
        _save_run_log(
            strategy_id=strategy_id,
            is_invalid=is_invalid,
            remark=f"{stock_code}: {remark}; trade_count={metrics.trade_count}; total_cost={metrics.total_cost}",
            annual_return=metrics.annual_return,
            max_drawdown=metrics.max_drawdown,
            win_rate=metrics.win_rate,
            total_profit=metrics.total_profit,
        )
        execution_remark = "no_signal"
        suggest_quantity = 0
        if signal_type and price is not None:
            if signal_type == "BUY":
                suggest_quantity, execution_remark = _suggest_buy_quantity(stock_code, price, params)
                if suggest_quantity > 0:
                    _save_trade_signal(stock_code, signal_type, price, strategy_id)
                else:
                    signal_type = None
            else:
                execution_remark = "sell_signal_saved"
                _save_trade_signal(stock_code, signal_type, price, strategy_id)

        results[stock_code] = {
            "strategy_type": strategy_type,
            "strategy_params": symbol_params,
            "market_status": market_status,
            "signal": signal_type,
            "price": price,
            "suggest_quantity": suggest_quantity,
            "annual_return": metrics.annual_return,
            "max_drawdown": metrics.max_drawdown,
            "win_rate": metrics.win_rate,
            "total_profit": metrics.total_profit,
            "is_invalid": is_invalid,
            "remark": remark,
            "execution_remark": execution_remark,
            "trade_count": metrics.trade_count,
            "total_cost": metrics.total_cost,
            "actor": actor,
        }
    return results
