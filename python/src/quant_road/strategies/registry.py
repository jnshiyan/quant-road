from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

import pandas as pd

from quant_road.config import settings
from quant_road.strategies.backtest_engine import BacktestMetrics
from quant_road.strategies.ma20 import generate_latest_signal as ma_generate_latest_signal
from quant_road.strategies.ma20 import run_backtest as ma_run_backtest
from quant_road.strategies.ma_dual import generate_latest_signal as dual_generate_latest_signal
from quant_road.strategies.ma_dual import run_backtest as dual_run_backtest


@dataclass(frozen=True)
class StrategyExecutor:
    strategy_type: str
    description: str
    normalize_params: Callable[[dict], dict]
    run_backtest: Callable[[pd.DataFrame, dict, float, float, float], BacktestMetrics]
    generate_signal: Callable[[pd.DataFrame, dict], tuple[str | None, float | None]]
    required_params: tuple[str, ...]
    optional_params: tuple[str, ...]
    sample_params: dict[str, object]


def _normalize_ma_params(params: dict) -> dict:
    normalized = dict(params)
    ma_period = int(normalized.get("ma_period", settings.ma_period))
    if ma_period < 2:
        raise ValueError("ma_period must be >= 2")
    normalized["ma_period"] = ma_period
    return normalized


def _normalize_dual_ma_params(params: dict) -> dict:
    normalized = dict(params)
    short_ma_period = int(normalized.get("short_ma_period", 5))
    long_ma_period = int(normalized.get("long_ma_period", 20))
    if short_ma_period < 2:
        raise ValueError("short_ma_period must be >= 2")
    if long_ma_period < 3:
        raise ValueError("long_ma_period must be >= 3")
    if short_ma_period >= long_ma_period:
        raise ValueError("short_ma_period must be less than long_ma_period")
    normalized["short_ma_period"] = short_ma_period
    normalized["long_ma_period"] = long_ma_period
    return normalized


def _run_ma(df: pd.DataFrame, params: dict, commission_rate: float, slippage_rate: float, stamp_duty_rate: float) -> BacktestMetrics:
    return ma_run_backtest(
        df,
        ma_period=int(params["ma_period"]),
        commission_rate=commission_rate,
        slippage_rate=slippage_rate,
        stamp_duty_rate=stamp_duty_rate,
    )


def _signal_ma(df: pd.DataFrame, params: dict) -> tuple[str | None, float | None]:
    return ma_generate_latest_signal(df, ma_period=int(params["ma_period"]))


def _run_dual_ma(
    df: pd.DataFrame,
    params: dict,
    commission_rate: float,
    slippage_rate: float,
    stamp_duty_rate: float,
) -> BacktestMetrics:
    return dual_run_backtest(
        df,
        short_ma_period=int(params["short_ma_period"]),
        long_ma_period=int(params["long_ma_period"]),
        commission_rate=commission_rate,
        slippage_rate=slippage_rate,
        stamp_duty_rate=stamp_duty_rate,
    )


def _signal_dual_ma(df: pd.DataFrame, params: dict) -> tuple[str | None, float | None]:
    return dual_generate_latest_signal(
        df,
        short_ma_period=int(params["short_ma_period"]),
        long_ma_period=int(params["long_ma_period"]),
    )


_EXECUTORS: dict[str, StrategyExecutor] = {
    "MA": StrategyExecutor(
        strategy_type="MA",
        description="Single moving-average crossover strategy.",
        normalize_params=_normalize_ma_params,
        run_backtest=_run_ma,
        generate_signal=_signal_ma,
        required_params=("ma_period",),
        optional_params=(
            "stop_loss_rate",
            "max_single_position_pct",
            "max_total_position_pct",
            "portfolio_capital",
            "enabled_regimes",
            "allocator_base_weight",
            "regime_budget_weights",
            "commission_rate",
            "slippage_rate",
            "stamp_duty_rate",
            "monthly_max_drawdown_limit_pct",
            "latest_month_win_rate_min_pct",
            "max_consecutive_losses",
            "underperform_months",
            "invalid_trigger_count",
        ),
        sample_params={
            "ma_period": 20,
            "stop_loss_rate": 0.08,
            "max_single_position_pct": 0.15,
            "max_total_position_pct": 0.80,
            "portfolio_capital": 100000,
            "enabled_regimes": ["bull", "volatile", "bear"],
            "allocator_base_weight": 0.6,
            "regime_budget_weights": {"bull": 1.0, "volatile": 0.8, "bear": 0.5, "panic": 0.0, "default": 0.8},
            "commission_rate": 0.0001,
            "slippage_rate": 0.0005,
            "stamp_duty_rate": 0.001,
            "monthly_max_drawdown_limit_pct": 12,
            "latest_month_win_rate_min_pct": 40,
            "max_consecutive_losses": 6,
            "underperform_months": 2,
            "invalid_trigger_count": 2,
        },
    ),
    "MA20_CROSS": StrategyExecutor(
        strategy_type="MA20_CROSS",
        description="Alias of MA strategy with default 20-day moving average.",
        normalize_params=_normalize_ma_params,
        run_backtest=_run_ma,
        generate_signal=_signal_ma,
        required_params=("ma_period",),
        optional_params=(
            "stop_loss_rate",
            "max_single_position_pct",
            "max_total_position_pct",
            "portfolio_capital",
            "enabled_regimes",
            "allocator_base_weight",
            "regime_budget_weights",
            "commission_rate",
            "slippage_rate",
            "stamp_duty_rate",
            "monthly_max_drawdown_limit_pct",
            "latest_month_win_rate_min_pct",
            "max_consecutive_losses",
            "underperform_months",
            "invalid_trigger_count",
        ),
        sample_params={
            "ma_period": 20,
            "stop_loss_rate": 0.08,
            "max_single_position_pct": 0.15,
            "max_total_position_pct": 0.80,
            "portfolio_capital": 100000,
            "enabled_regimes": ["bull", "volatile", "bear"],
            "allocator_base_weight": 0.6,
            "regime_budget_weights": {"bull": 1.0, "volatile": 0.8, "bear": 0.5, "panic": 0.0, "default": 0.8},
            "commission_rate": 0.0001,
            "slippage_rate": 0.0005,
            "stamp_duty_rate": 0.001,
            "monthly_max_drawdown_limit_pct": 12,
            "latest_month_win_rate_min_pct": 40,
            "max_consecutive_losses": 6,
            "underperform_months": 2,
            "invalid_trigger_count": 2,
        },
    ),
    "MA_DUAL": StrategyExecutor(
        strategy_type="MA_DUAL",
        description="Dual moving-average crossover strategy (short vs long).",
        normalize_params=_normalize_dual_ma_params,
        run_backtest=_run_dual_ma,
        generate_signal=_signal_dual_ma,
        required_params=("short_ma_period", "long_ma_period"),
        optional_params=(
            "stop_loss_rate",
            "max_single_position_pct",
            "max_total_position_pct",
            "portfolio_capital",
            "enabled_regimes",
            "allocator_base_weight",
            "regime_budget_weights",
            "commission_rate",
            "slippage_rate",
            "stamp_duty_rate",
            "monthly_max_drawdown_limit_pct",
            "latest_month_win_rate_min_pct",
            "max_consecutive_losses",
            "underperform_months",
            "invalid_trigger_count",
        ),
        sample_params={
            "short_ma_period": 5,
            "long_ma_period": 20,
            "stop_loss_rate": 0.08,
            "max_single_position_pct": 0.12,
            "max_total_position_pct": 0.75,
            "portfolio_capital": 100000,
            "enabled_regimes": ["bull", "volatile"],
            "allocator_base_weight": 0.4,
            "regime_budget_weights": {"bull": 1.1, "volatile": 0.9, "bear": 0.4, "panic": 0.0, "default": 0.8},
            "commission_rate": 0.0001,
            "slippage_rate": 0.0005,
            "stamp_duty_rate": 0.001,
            "monthly_max_drawdown_limit_pct": 12,
            "latest_month_win_rate_min_pct": 40,
            "max_consecutive_losses": 6,
            "underperform_months": 2,
            "invalid_trigger_count": 2,
        },
    ),
    "MA_DUAL_CROSS": StrategyExecutor(
        strategy_type="MA_DUAL_CROSS",
        description="Alias of MA_DUAL strategy.",
        normalize_params=_normalize_dual_ma_params,
        run_backtest=_run_dual_ma,
        generate_signal=_signal_dual_ma,
        required_params=("short_ma_period", "long_ma_period"),
        optional_params=(
            "stop_loss_rate",
            "max_single_position_pct",
            "max_total_position_pct",
            "portfolio_capital",
            "enabled_regimes",
            "allocator_base_weight",
            "regime_budget_weights",
            "commission_rate",
            "slippage_rate",
            "stamp_duty_rate",
            "monthly_max_drawdown_limit_pct",
            "latest_month_win_rate_min_pct",
            "max_consecutive_losses",
            "underperform_months",
            "invalid_trigger_count",
        ),
        sample_params={
            "short_ma_period": 5,
            "long_ma_period": 20,
            "stop_loss_rate": 0.08,
            "max_single_position_pct": 0.12,
            "max_total_position_pct": 0.75,
            "portfolio_capital": 100000,
            "enabled_regimes": ["bull", "volatile"],
            "allocator_base_weight": 0.4,
            "regime_budget_weights": {"bull": 1.1, "volatile": 0.9, "bear": 0.4, "panic": 0.0, "default": 0.8},
            "commission_rate": 0.0001,
            "slippage_rate": 0.0005,
            "stamp_duty_rate": 0.001,
            "monthly_max_drawdown_limit_pct": 12,
            "latest_month_win_rate_min_pct": 40,
            "max_consecutive_losses": 6,
            "underperform_months": 2,
            "invalid_trigger_count": 2,
        },
    ),
}


def normalize_strategy_type(strategy_type: str | None) -> str:
    if strategy_type is None:
        return "MA"
    normalized = str(strategy_type).strip().upper()
    return normalized or "MA"


def resolve_strategy_executor(strategy_type: str | None) -> StrategyExecutor:
    normalized = normalize_strategy_type(strategy_type)
    executor = _EXECUTORS.get(normalized)
    if executor is None:
        supported = ", ".join(sorted(_EXECUTORS.keys()))
        raise ValueError(f"Unsupported strategy_type={normalized}. Supported: {supported}")
    return executor


def list_strategy_capabilities() -> list[dict]:
    capabilities: list[dict] = []
    for strategy_type in sorted(_EXECUTORS.keys()):
        executor = _EXECUTORS[strategy_type]
        capabilities.append(
            {
                "strategy_type": executor.strategy_type,
                "description": executor.description,
                "required_params": list(executor.required_params),
                "optional_params": list(executor.optional_params),
                "sample_params": dict(executor.sample_params),
            }
        )
    return capabilities
