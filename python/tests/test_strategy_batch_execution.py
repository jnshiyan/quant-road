import unittest
from unittest.mock import patch

import pandas as pd


def _sample_frame() -> pd.DataFrame:
    index = pd.date_range("2024-01-01", periods=3, freq="D")
    return pd.DataFrame(
        {
            "open": [10.0, 10.2, 10.4],
            "high": [10.1, 10.3, 10.5],
            "low": [9.9, 10.1, 10.2],
            "close": [10.0, 10.2, 10.4],
            "volume": [1000, 1200, 1400],
        },
        index=index,
    )


class StrategyBatchExecutionTestCase(unittest.TestCase):
    @patch("quant_road.services.strategy_service.run_strategy")
    def test_run_strategy_batch_returns_same_keys_as_symbol_list(self, mock_run_strategy) -> None:
        from quant_road.services.strategy_service import run_strategy_batch

        mock_run_strategy.return_value = {
            "000001": {"annual_return": 1.0},
            "000002": {"annual_return": 2.0},
        }

        payload = run_strategy_batch(
            symbols=["000001", "000002"],
            start_date="2023-01-01",
            strategy_id=1,
            actor="batch-test",
        )
        self.assertEqual(set(payload.keys()), {"000001", "000002"})

    @patch("quant_road.services.strategy_execution_service._save_trade_signal")
    @patch("quant_road.services.strategy_execution_service._save_run_log")
    @patch("quant_road.services.strategy_execution_service._suggest_buy_quantity", return_value=(100, "ok"))
    @patch("quant_road.services.strategy_execution_service.evaluate_strategy_invalid", return_value=(False, "healthy"))
    @patch("quant_road.services.strategy_execution_service.fetch_benchmark_monthly_returns", return_value=pd.Series(dtype=float))
    def test_execute_strategy_frames_preserves_symbol_keys(
        self,
        _mock_benchmark,
        _mock_invalid,
        _mock_quantity,
        _mock_run_log,
        _mock_trade_signal,
    ) -> None:
        from quant_road.services.strategy_execution_service import execute_strategy_frames

        class StubExecutor:
            def run_backtest(self, df, params, commission_rate, slippage_rate, stamp_duty_rate):
                from quant_road.strategies.backtest_engine import BacktestMetrics

                series = pd.Series([0.0] * len(df), index=df.index)
                return BacktestMetrics(
                    annual_return=1.23,
                    max_drawdown=2.34,
                    win_rate=55.0,
                    total_profit=123.0,
                    consecutive_losses=1,
                    monthly_returns=pd.Series(dtype=float),
                    equity_curve=series,
                    strategy_returns=series,
                    total_cost=0.12,
                    trade_count=1,
                )

            def generate_signal(self, df, params):
                return "BUY", float(df["close"].iloc[-1])

        payload = execute_strategy_frames(
            grouped_frames={"000001": _sample_frame(), "000002": _sample_frame()},
            executor=StubExecutor(),
            params={"portfolio_capital": 100000},
            strategy_id=1,
            actor="batch-test",
            strategy_type="MA",
            market_status=None,
        )

        self.assertEqual(set(payload.keys()), {"000001", "000002"})


if __name__ == "__main__":
    unittest.main()
