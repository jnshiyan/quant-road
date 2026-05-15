import unittest
from unittest.mock import patch


class CanaryServiceTestCase(unittest.TestCase):
    @patch("quant_road.services.canary_service.batch_execute")
    @patch("quant_road.services.canary_service.fetch_latest_market_status")
    @patch("quant_road.services.canary_service.fetch_shadow_compare_payload")
    def test_evaluate_canary_persists_recommendation(
        self,
        mock_shadow_payload,
        mock_market_status,
        mock_batch_execute,
    ) -> None:
        from quant_road.services.canary_service import evaluate_canary

        mock_shadow_payload.return_value = {
            "generated_at": "2026-05-04",
            "months": 6,
            "baseline": {"strategy_id": 1},
            "candidate": {"strategy_id": 2},
            "summary": {
                "comparable_months": 5,
                "candidate_better_annual_months": 4,
                "candidate_lower_drawdown_months": 3,
                "candidate_higher_win_rate_months": 3,
                "candidate_lower_invalid_rate_months": 3,
            },
        }
        mock_market_status.return_value = {"status": "bull"}

        payload = evaluate_canary(1, 2, months=6)

        self.assertEqual(payload["recommendation"], "promote_candidate")
        self.assertEqual(payload["market_status"], "bull")
        mock_batch_execute.assert_called_once()


if __name__ == "__main__":
    unittest.main()
