from __future__ import annotations

import pandas as pd

from quant_road.strategies.backtest_engine import BacktestMetrics, run_backtest_from_signals


def _prepare(df: pd.DataFrame, short_ma_period: int, long_ma_period: int) -> tuple[pd.DataFrame, pd.Series, pd.Series]:
    data = df.copy().sort_index()
    data["short_ma"] = data["close"].rolling(short_ma_period).mean()
    data["long_ma"] = data["close"].rolling(long_ma_period).mean()
    signal = (data["short_ma"] > data["long_ma"]).astype(int)
    valid_mask = data["short_ma"].notna() & data["long_ma"].notna()
    return data, signal, valid_mask


def run_backtest(
    df: pd.DataFrame,
    short_ma_period: int,
    long_ma_period: int,
    commission_rate: float = 0.0001,
    slippage_rate: float = 0.0005,
    stamp_duty_rate: float = 0.001,
) -> BacktestMetrics:
    data, signal, valid_mask = _prepare(df, short_ma_period, long_ma_period)
    return run_backtest_from_signals(
        data,
        signal=signal,
        valid_mask=valid_mask,
        commission_rate=commission_rate,
        slippage_rate=slippage_rate,
        stamp_duty_rate=stamp_duty_rate,
    )


def generate_latest_signal(
    df: pd.DataFrame,
    short_ma_period: int,
    long_ma_period: int,
) -> tuple[str | None, float | None]:
    data = df.copy().sort_index()
    data["short_ma"] = data["close"].rolling(short_ma_period).mean()
    data["long_ma"] = data["close"].rolling(long_ma_period).mean()
    data = data.dropna(subset=["short_ma", "long_ma"])
    if len(data) < 2:
        return None, None

    previous = data.iloc[-2]
    current = data.iloc[-1]

    if previous["short_ma"] <= previous["long_ma"] and current["short_ma"] > current["long_ma"]:
        return "BUY", round(float(current["close"]), 2)
    if previous["short_ma"] >= previous["long_ma"] and current["short_ma"] < current["long_ma"]:
        return "SELL", round(float(current["close"]), 2)
    return None, round(float(current["close"]), 2)
