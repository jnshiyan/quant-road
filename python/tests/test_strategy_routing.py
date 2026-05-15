import unittest
from unittest.mock import patch

import pandas as pd

from quant_road.strategies.backtest_engine import BacktestMetrics


def _sample_df() -> pd.DataFrame:
    index = pd.date_range("2024-01-01", periods=10, freq="D")
    return pd.DataFrame(
        {
            "open": [10, 10.1, 10.2, 10.3, 10.2, 10.4, 10.5, 10.6, 10.7, 10.8],
            "high": [10.1, 10.2, 10.3, 10.4, 10.3, 10.5, 10.6, 10.7, 10.8, 10.9],
            "low": [9.9, 10.0, 10.1, 10.2, 10.1, 10.3, 10.4, 10.5, 10.6, 10.7],
            "close": [10, 10.1, 10.2, 10.3, 10.2, 10.4, 10.5, 10.6, 10.7, 10.8],
            "volume": [1000] * 10,
        },
        index=index,
    )


def _sample_metrics() -> BacktestMetrics:
    index = pd.date_range("2024-01-01", periods=10, freq="D")
    series = pd.Series([0.0] * 10, index=index)
    return BacktestMetrics(
        annual_return=1.23,
        max_drawdown=-2.34,
        win_rate=55.0,
        total_profit=3.21,
        consecutive_losses=1,
        monthly_returns=pd.Series(dtype=float),
        equity_curve=series,
        strategy_returns=series,
        total_cost=0.1234,
        trade_count=2,
    )


class StrategyRoutingTest(unittest.TestCase):
    def test_run_strategy_uses_strategy_type_executor(self) -> None:
        class StubExecutor:
            def __init__(self) -> None:
                self.backtest_called = False
                self.signal_called = False

            def normalize_params(self, params):
                normalized = dict(params)
                normalized["ma_period"] = int(normalized.get("ma_period", 20))
                return normalized

            def run_backtest(self, df, params, commission_rate, slippage_rate, stamp_duty_rate):
                self.backtest_called = True
                return _sample_metrics()

            def generate_signal(self, df, params):
                self.signal_called = True
                return "BUY", 10.8

        stub = StubExecutor()
        params = {
            "ma_period": 20,
            "commission_rate": 0.0001,
            "slippage_rate": 0.0005,
            "stamp_duty_rate": 0.001,
            "max_single_position_pct": 0.15,
            "max_total_position_pct": 0.80,
            "portfolio_capital": 100000,
        }

        with patch("quant_road.services.strategy_service._load_strategy_config", return_value=("MA", params)), \
            patch("quant_road.services.strategy_service.resolve_strategy_executor", return_value=stub) as mock_resolver, \
            patch("quant_road.services.strategy_service._resolve_symbols", return_value=["000001"]), \
            patch("quant_road.services.strategy_service.load_stock_history_batch", return_value={"000001": _sample_df()}), \
            patch("quant_road.services.strategy_execution_service.fetch_benchmark_monthly_returns", return_value=pd.Series(dtype=float)), \
            patch("quant_road.services.strategy_execution_service.evaluate_strategy_invalid", return_value=(False, "healthy")), \
            patch("quant_road.services.strategy_execution_service._suggest_buy_quantity", return_value=(100, "ok")), \
            patch("quant_road.services.strategy_execution_service._save_run_log"), \
            patch("quant_road.services.strategy_execution_service._save_trade_signal"):
            from quant_road.services.strategy_service import run_strategy

            result = run_strategy(strategy_id=1)

        mock_resolver.assert_called_once_with("MA")
        self.assertTrue(stub.backtest_called)
        self.assertTrue(stub.signal_called)
        self.assertEqual(result["000001"]["strategy_type"], "MA")
        self.assertEqual(result["000001"]["strategy_params"]["ma_period"], 20)
        self.assertEqual(result["000001"]["signal"], "BUY")
        self.assertEqual(result["000001"]["suggest_quantity"], 100)

    def test_run_strategy_raises_for_unsupported_strategy_type(self) -> None:
        params = {
            "ma_period": 20,
            "commission_rate": 0.0001,
            "slippage_rate": 0.0005,
            "stamp_duty_rate": 0.001,
        }
        with patch("quant_road.services.strategy_service._load_strategy_config", return_value=("FOO", params)):
            from quant_road.services.strategy_service import run_strategy

            with self.assertRaises(ValueError):
                run_strategy(strategy_id=1)


if __name__ == "__main__":
    unittest.main()
