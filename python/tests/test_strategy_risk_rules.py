import unittest

import pandas as pd

from quant_road.services.risk_service import evaluate_strategy_invalid
from quant_road.strategies.backtest_engine import BacktestMetrics


def _metrics_with(
    consecutive_losses: int,
    strategy_returns: list[float],
    equity_curve: list[float],
    monthly_returns: list[float],
) -> BacktestMetrics:
    index = pd.date_range("2026-01-01", periods=len(strategy_returns), freq="D")
    strategy_series = pd.Series(strategy_returns, index=index)
    equity_series = pd.Series(equity_curve, index=index)
    month_index = pd.date_range("2026-01-31", periods=len(monthly_returns), freq="ME")
    month_series = pd.Series(monthly_returns, index=month_index)
    return BacktestMetrics(
        annual_return=0.0,
        max_drawdown=0.0,
        win_rate=0.0,
        total_profit=0.0,
        consecutive_losses=consecutive_losses,
        monthly_returns=month_series,
        equity_curve=equity_series,
        strategy_returns=strategy_series,
        total_cost=0.0,
        trade_count=0,
    )


class StrategyRiskRulesTest(unittest.TestCase):
    def test_default_rules_mark_invalid_when_two_criteria_hit(self) -> None:
        metrics = _metrics_with(
            consecutive_losses=6,
            strategy_returns=[-0.01] * 10,
            equity_curve=[1.0, 0.99, 0.98, 0.92, 0.89, 0.88, 0.87, 0.86, 0.86, 0.85],
            monthly_returns=[-0.02, -0.01, -0.03],
        )
        benchmark = pd.Series(
            [0.01, 0.01, 0.01],
            index=pd.date_range("2026-01-31", periods=3, freq="ME"),
        )

        is_invalid, remark = evaluate_strategy_invalid(metrics, benchmark_monthly_returns=benchmark)
        self.assertTrue(is_invalid)
        self.assertIn("consecutive_losses_over_limit", remark)

    def test_invalid_trigger_count_override(self) -> None:
        metrics = _metrics_with(
            consecutive_losses=6,
            strategy_returns=[-0.01] * 10,
            equity_curve=[1.0, 0.99, 0.98, 0.97, 0.96, 0.95, 0.95, 0.95, 0.95, 0.95],
            monthly_returns=[-0.02, 0.01, 0.01],
        )
        benchmark = pd.Series(
            [0.0, 0.0, 0.0],
            index=pd.date_range("2026-01-31", periods=3, freq="ME"),
        )

        is_invalid, remark = evaluate_strategy_invalid(
            metrics,
            benchmark_monthly_returns=benchmark,
            rules={"invalid_trigger_count": 3},
        )
        self.assertFalse(is_invalid)
        self.assertIn("warning_only", remark)

    def test_strategy_specific_thresholds_can_trigger_single_rule(self) -> None:
        metrics = _metrics_with(
            consecutive_losses=2,
            strategy_returns=[0.01] * 10,
            equity_curve=[1.0, 1.01, 1.02, 1.01, 1.00, 0.99, 0.98, 0.97, 0.98, 0.99],
            monthly_returns=[0.01, 0.0, 0.0],
        )
        benchmark = pd.Series(
            [0.0, 0.0, 0.0],
            index=pd.date_range("2026-01-31", periods=3, freq="ME"),
        )

        is_invalid, remark = evaluate_strategy_invalid(
            metrics,
            benchmark_monthly_returns=benchmark,
            rules={
                "monthly_max_drawdown_limit_pct": 2,
                "invalid_trigger_count": 1,
            },
        )
        self.assertTrue(is_invalid)
        self.assertIn("monthly_max_drawdown_over_limit", remark)


if __name__ == "__main__":
    unittest.main()
