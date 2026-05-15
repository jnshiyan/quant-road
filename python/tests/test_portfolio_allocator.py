import unittest
from unittest.mock import patch


class PortfolioAllocatorTestCase(unittest.TestCase):
    @patch("quant_road.services.strategy_service.run_strategy")
    @patch("quant_road.services.strategy_service._latest_market_status_value")
    @patch("quant_road.services.strategy_service._load_active_strategies")
    def test_run_portfolio_allocates_capital_by_weight_and_regime(
        self,
        mock_load_active,
        mock_market_status,
        mock_run_strategy,
    ) -> None:
        from quant_road.services.strategy_service import run_portfolio

        mock_load_active.return_value = [
            {
                "strategy_id": 1,
                "strategy_type": "MA",
                "params": {
                    "allocator_base_weight": 0.6,
                    "regime_budget_weights": {"bull": 1.0, "default": 1.0},
                },
            },
            {
                "strategy_id": 2,
                "strategy_type": "MA_DUAL",
                "params": {
                    "allocator_base_weight": 0.4,
                    "regime_budget_weights": {"bull": 1.5, "default": 1.0},
                },
            },
        ]
        mock_market_status.return_value = "bull"
        mock_run_strategy.side_effect = [{"000001": {"signal": "BUY"}}, {"000002": {"signal": "SELL"}}]

        payload = run_portfolio(start_date="2023-01-01", total_capital=100000, actor="tester")

        self.assertEqual(payload["market_status"], "bull")
        self.assertEqual(payload["executed_strategy_count"], 2)
        self.assertEqual(len(payload["strategies"]), 2)
        first = payload["strategies"][0]
        second = payload["strategies"][1]
        self.assertEqual(first["allocated_capital"], 50000.0)
        self.assertEqual(second["allocated_capital"], 50000.0)
        self.assertEqual(mock_run_strategy.call_count, 2)

    @patch("quant_road.services.strategy_service._latest_market_status_value")
    @patch("quant_road.services.strategy_service._load_active_strategies")
    def test_run_portfolio_skips_when_all_weights_zero(self, mock_load_active, mock_market_status) -> None:
        from quant_road.services.strategy_service import run_portfolio

        mock_load_active.return_value = [
            {
                "strategy_id": 1,
                "strategy_type": "MA",
                "params": {"allocator_base_weight": 0, "regime_budget_weights": {"default": 0}},
            }
        ]
        mock_market_status.return_value = "bear"

        payload = run_portfolio(total_capital=100000)
        self.assertEqual(payload["executed_strategy_count"], 0)
        self.assertTrue(payload["strategies"][0]["skipped"])


if __name__ == "__main__":
    unittest.main()
