import unittest
from unittest.mock import patch

import pandas as pd


class AsyncRegressionEquivalenceTestCase(unittest.TestCase):
    @patch("quant_road.services.strategy_service.execute_strategy_frames")
    @patch("quant_road.services.strategy_service.load_stock_history_batch")
    @patch("quant_road.services.strategy_service.resolve_strategy_executor")
    @patch("quant_road.services.strategy_service._load_strategy_config")
    def test_async_batch_matches_sync_strategy_metrics(
        self,
        mock_load_strategy_config,
        mock_resolve_executor,
        mock_load_batch,
        mock_execute_frames,
    ) -> None:
        from quant_road.services.strategy_service import run_strategy_batch

        expected = {
            "000001": {
                "annual_return": 12.5,
                "max_drawdown": 3.1,
                "win_rate": 56.0,
            }
        }
        mock_load_strategy_config.return_value = ("MA", {"portfolio_capital": 100000})
        mock_resolve_executor.return_value.normalize_params.side_effect = lambda params: params
        mock_load_batch.return_value = {"000001": pd.DataFrame({"close": [10.0]})}
        mock_execute_frames.return_value = expected

        sync_payload = expected
        async_payload = run_strategy_batch(symbols=["000001"], start_date="2023-01-01", strategy_id=1, actor="async")

        self.assertEqual(async_payload["000001"]["annual_return"], sync_payload["000001"]["annual_return"])


if __name__ == "__main__":
    unittest.main()
