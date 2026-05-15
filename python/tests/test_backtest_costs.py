import unittest

import pandas as pd

from quant_road.strategies.ma20 import run_backtest


class BacktestCostTest(unittest.TestCase):
    def test_costs_reduce_strategy_profit(self) -> None:
        index = pd.date_range("2024-01-01", periods=12, freq="D")
        df = pd.DataFrame(
            {
                "open": [10, 11, 9, 12, 8, 13, 7, 14, 8, 15, 9, 16],
                "high": [10, 11, 9, 12, 8, 13, 7, 14, 8, 15, 9, 16],
                "low": [10, 11, 9, 12, 8, 13, 7, 14, 8, 15, 9, 16],
                "close": [10, 11, 9, 12, 8, 13, 7, 14, 8, 15, 9, 16],
                "volume": [1000] * 12,
            },
            index=index,
        )

        no_cost = run_backtest(
            df,
            ma_period=2,
            commission_rate=0.0,
            slippage_rate=0.0,
            stamp_duty_rate=0.0,
        )
        with_cost = run_backtest(
            df,
            ma_period=2,
            commission_rate=0.0003,
            slippage_rate=0.0005,
            stamp_duty_rate=0.001,
        )

        self.assertLess(with_cost.total_profit, no_cost.total_profit)


if __name__ == "__main__":
    unittest.main()
