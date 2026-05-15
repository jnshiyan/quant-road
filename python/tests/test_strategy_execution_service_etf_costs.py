import unittest
from unittest.mock import patch

import pandas as pd


def _sample_frame() -> pd.DataFrame:
    index = pd.date_range("2024-01-01", periods=5, freq="D")
    return pd.DataFrame(
        {
            "open": [4.8, 4.85, 4.9, 4.95, 5.0],
            "high": [4.82, 4.87, 4.92, 4.97, 5.02],
            "low": [4.78, 4.83, 4.88, 4.93, 4.98],
            "close": [4.8, 4.85, 4.9, 4.95, 5.0],
            "volume": [1000, 1000, 1000, 1000, 1000],
        },
        index=index,
    )


class StrategyExecutionEtfCostTest(unittest.TestCase):
    @patch("quant_road.services.strategy_execution_service._save_run_log")
    @patch("quant_road.services.strategy_execution_service.evaluate_strategy_invalid", return_value=(False, "healthy"))
    @patch("quant_road.services.strategy_execution_service.fetch_benchmark_monthly_returns", return_value=pd.Series(dtype=float))
    def test_execute_strategy_frames_uses_zero_stamp_duty_for_etf(
        self,
        _mock_benchmark,
        _mock_invalid,
        _mock_run_log,
    ) -> None:
        from quant_road.services.strategy_execution_service import execute_strategy_frames
        from quant_road.strategies.backtest_engine import BacktestMetrics

        class StubExecutor:
            def __init__(self) -> None:
                self.last_stamp_duty_rate = None

            def run_backtest(self, df, params, commission_rate, slippage_rate, stamp_duty_rate):
                self.last_stamp_duty_rate = stamp_duty_rate
                series = pd.Series([0.0] * len(df), index=df.index)
                return BacktestMetrics(
                    annual_return=1.0,
                    max_drawdown=-1.0,
                    win_rate=50.0,
                    total_profit=2.0,
                    consecutive_losses=1,
                    monthly_returns=pd.Series(dtype=float),
                    equity_curve=series,
                    strategy_returns=series,
                    total_cost=0.1,
                    trade_count=1,
                )

            def generate_signal(self, df, params):
                return None, float(df["close"].iloc[-1])

        stub = StubExecutor()

        with patch(
            "quant_road.services.strategy_execution_service.fetch_one",
            return_value=("ETF", "沪深300ETF华泰柏瑞"),
        ):
            execute_strategy_frames(
                grouped_frames={"510300": _sample_frame()},
                executor=stub,
                params={
                    "portfolio_capital": 100000,
                    "commission_rate": 0.0001,
                    "slippage_rate": 0.0005,
                    "stamp_duty_rate": 0.001,
                },
                strategy_id=1,
                actor="unit-test",
                strategy_type="MA",
                market_status=None,
            )

        self.assertEqual(stub.last_stamp_duty_rate, 0.0)


if __name__ == "__main__":
    unittest.main()
