import unittest

import pandas as pd

from quant_road.strategies.ma_dual import generate_latest_signal, run_backtest


class DualMAStrategyTest(unittest.TestCase):
    def test_generate_latest_signal_returns_buy_on_golden_cross(self) -> None:
        index = pd.date_range("2024-01-01", periods=8, freq="D")
        close = [10, 10, 10, 10, 10, 9, 10, 11]
        df = pd.DataFrame(
            {
                "open": close,
                "high": close,
                "low": close,
                "close": close,
                "volume": [1000] * 8,
            },
            index=index,
        )

        signal, price = generate_latest_signal(df, short_ma_period=2, long_ma_period=5)
        self.assertEqual(signal, "BUY")
        self.assertEqual(price, 11.0)

    def test_backtest_costs_reduce_profit(self) -> None:
        index = pd.date_range("2024-01-01", periods=30, freq="D")
        close = [10, 11, 12, 11, 10, 9, 8, 9, 10, 11, 12, 13, 12, 11, 10, 9, 8, 9, 10, 11, 12, 13, 14, 13, 12, 11, 10, 11, 12, 13]
        df = pd.DataFrame(
            {
                "open": close,
                "high": close,
                "low": close,
                "close": close,
                "volume": [1000] * len(close),
            },
            index=index,
        )

        no_cost = run_backtest(
            df,
            short_ma_period=3,
            long_ma_period=7,
            commission_rate=0.0,
            slippage_rate=0.0,
            stamp_duty_rate=0.0,
        )
        with_cost = run_backtest(
            df,
            short_ma_period=3,
            long_ma_period=7,
            commission_rate=0.0003,
            slippage_rate=0.0005,
            stamp_duty_rate=0.001,
        )

        self.assertLess(with_cost.total_profit, no_cost.total_profit)


if __name__ == "__main__":
    unittest.main()
