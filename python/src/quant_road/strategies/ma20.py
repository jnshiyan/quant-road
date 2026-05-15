from __future__ import annotations

import pandas as pd

from quant_road.strategies.backtest_engine import BacktestMetrics, run_backtest_from_signals


def _prepare(df: pd.DataFrame, ma_period: int) -> tuple[pd.DataFrame, pd.Series, pd.Series]:
    data = df.copy().sort_index()
    data["ma"] = data["close"].rolling(ma_period).mean()
    signal = (data["close"] > data["ma"]).astype(int)
    valid_mask = data["ma"].notna()
    return data, signal, valid_mask


def run_backtest(
    df: pd.DataFrame,
    ma_period: int,
    commission_rate: float = 0.0001,
    slippage_rate: float = 0.0005,
    stamp_duty_rate: float = 0.001,
) -> BacktestMetrics:
    data, signal, valid_mask = _prepare(df, ma_period)
    return run_backtest_from_signals(
        data,
        signal=signal,
        valid_mask=valid_mask,
        commission_rate=commission_rate,
        slippage_rate=slippage_rate,
        stamp_duty_rate=stamp_duty_rate,
    )


def generate_latest_signal(df: pd.DataFrame, ma_period: int) -> tuple[str | None, float | None]:
    data = df.copy().sort_index()
    data["ma"] = data["close"].rolling(ma_period).mean()
    data = data.dropna(subset=["ma"])
    if len(data) < 2:
        return None, None

    previous = data.iloc[-2]
    current = data.iloc[-1]

    if previous["close"] <= previous["ma"] and current["close"] > current["ma"]:
        return "BUY", round(float(current["close"]), 2)
    if previous["close"] >= previous["ma"] and current["close"] < current["ma"]:
        return "SELL", round(float(current["close"]), 2)
    return None, round(float(current["close"]), 2)
