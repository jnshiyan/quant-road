from __future__ import annotations

from dataclasses import dataclass

import pandas as pd


@dataclass
class BacktestMetrics:
    annual_return: float
    max_drawdown: float
    win_rate: float
    total_profit: float
    consecutive_losses: int
    monthly_returns: pd.Series
    equity_curve: pd.Series
    strategy_returns: pd.Series
    total_cost: float
    trade_count: int


def _empty_metrics() -> BacktestMetrics:
    return BacktestMetrics(
        annual_return=0.0,
        max_drawdown=0.0,
        win_rate=0.0,
        total_profit=0.0,
        consecutive_losses=0,
        monthly_returns=pd.Series(dtype=float),
        equity_curve=pd.Series(dtype=float),
        strategy_returns=pd.Series(dtype=float),
        total_cost=0.0,
        trade_count=0,
    )


def _trade_returns(data: pd.DataFrame) -> list[float]:
    trade_returns: list[float] = []
    in_position = False
    entry_price = 0.0

    for row in data.itertuples():
        if not in_position and row.signal == 1 and getattr(row, "position") == 0:
            entry_price = float(row.close)
            in_position = True
        elif in_position and row.signal == 0 and getattr(row, "position") == 1:
            trade_returns.append((float(row.close) - entry_price) / entry_price)
            in_position = False

    if in_position:
        trade_returns.append((float(data["close"].iloc[-1]) - entry_price) / entry_price)
    return trade_returns


def _consecutive_losses(trade_returns: list[float]) -> int:
    current = 0
    maximum = 0
    for value in trade_returns:
        if value < 0:
            current += 1
            maximum = max(maximum, current)
        else:
            current = 0
    return maximum


def run_backtest_from_signals(
    df: pd.DataFrame,
    signal: pd.Series,
    valid_mask: pd.Series,
    commission_rate: float = 0.0001,
    slippage_rate: float = 0.0005,
    stamp_duty_rate: float = 0.001,
) -> BacktestMetrics:
    data = df.copy().sort_index()
    if data.empty:
        return _empty_metrics()

    signal_aligned = signal.reindex(data.index).fillna(0).astype(int).clip(0, 1)
    valid_aligned = valid_mask.reindex(data.index).fillna(False)

    data["signal"] = signal_aligned
    data = data[valid_aligned]
    if data.empty:
        return _empty_metrics()

    data["position"] = data["signal"].shift(1).fillna(0)
    data["position_change"] = data["position"].diff().fillna(data["position"])
    data["daily_return"] = data["close"].pct_change().fillna(0.0)

    buy_turnover = data["position_change"].clip(lower=0.0)
    sell_turnover = (-data["position_change"]).clip(lower=0.0)
    data["trade_cost"] = buy_turnover * (commission_rate + slippage_rate) + sell_turnover * (
        commission_rate + slippage_rate + stamp_duty_rate
    )
    data["strategy_return"] = data["position"] * data["daily_return"] - data["trade_cost"]
    data["equity_curve"] = (1.0 + data["strategy_return"]).cumprod()

    trade_returns = _trade_returns(data)
    win_rate = 0.0
    if trade_returns:
        win_rate = sum(1 for item in trade_returns if item > 0) / len(trade_returns) * 100

    equity_curve = data["equity_curve"]
    max_drawdown = ((equity_curve / equity_curve.cummax()) - 1.0).min() * 100
    total_profit = (equity_curve.iloc[-1] - 1.0) * 100
    annual_return = (equity_curve.iloc[-1] ** (252 / len(data)) - 1.0) * 100 if len(data) > 0 else 0.0
    monthly_returns = equity_curve.resample("ME").last().pct_change().dropna()
    total_cost = float(data["trade_cost"].sum()) * 100
    trade_count = int((data["position_change"] != 0).sum())

    return BacktestMetrics(
        annual_return=round(float(annual_return), 2),
        max_drawdown=round(float(max_drawdown), 2),
        win_rate=round(float(win_rate), 2),
        total_profit=round(float(total_profit), 2),
        consecutive_losses=_consecutive_losses(trade_returns),
        monthly_returns=monthly_returns,
        equity_curve=equity_curve,
        strategy_returns=data["strategy_return"],
        total_cost=round(total_cost, 4),
        trade_count=trade_count,
    )
