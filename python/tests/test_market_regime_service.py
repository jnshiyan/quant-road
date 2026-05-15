import unittest
from datetime import datetime
from unittest.mock import patch


class MarketRegimeServiceTestCase(unittest.TestCase):
    def test_classify_market_status(self) -> None:
        from quant_road.services.market_regime_service import classify_market_status

        self.assertEqual(classify_market_status(True, True, 0.75, 4200, 4100), "bull")
        self.assertEqual(classify_market_status(False, False, 0.10, 3900, 4000), "panic")
        self.assertEqual(classify_market_status(False, False, 0.35, 3950, 4000), "bear")
        self.assertEqual(classify_market_status(True, True, 0.45, 4020, 4010), "volatile")

    def test_apply_status_hysteresis(self) -> None:
        from quant_road.services.market_regime_service import apply_status_hysteresis

        # Single contrary reading should not immediately flip from volatile to bull
        self.assertEqual(
            apply_status_hysteresis(candidate="bull", history=["volatile"], hold_days=2),
            "volatile",
        )
        # Panic should always take effect immediately
        self.assertEqual(
            apply_status_hysteresis(candidate="panic", history=["bull", "bull"], hold_days=2),
            "panic",
        )
        # Same as latest should keep latest
        self.assertEqual(
            apply_status_hysteresis(candidate="bear", history=["bear", "bear"], hold_days=2),
            "bear",
        )

    @patch("quant_road.services.market_regime_service.save_market_status")
    @patch("quant_road.services.market_regime_service.fetch_latest_market_status")
    @patch("quant_road.services.market_regime_service.evaluate_market_status")
    def test_evaluate_and_save_market_status_fallback_to_latest(
        self,
        mock_evaluate_market_status,
        mock_fetch_latest_market_status,
        mock_save_market_status,
    ) -> None:
        from quant_road.services.market_regime_service import evaluate_and_save_market_status

        mock_evaluate_market_status.side_effect = RuntimeError("akshare_unavailable")
        mock_fetch_latest_market_status.return_value = {
            "status": "volatile",
            "raw_status": "bear",
            "hs300_close": 3900.12,
            "hs300_ma20": 3950.34,
            "hs300_ma60": 4020.56,
            "hs300_above_ma20": False,
            "hs300_above_ma60": False,
            "up_ratio": 0.31,
        }

        payload = evaluate_and_save_market_status(hold_days=2)

        self.assertEqual(payload["status"], "volatile")
        self.assertEqual(payload["raw_status"], "bear")
        self.assertEqual(payload["trade_date"], datetime.now().strftime("%Y-%m-%d"))
        mock_save_market_status.assert_called_once()
        saved_snapshot = mock_save_market_status.call_args.args[0]
        self.assertEqual(saved_snapshot.status, "volatile")
        self.assertEqual(saved_snapshot.raw_status, "bear")
        self.assertIn("fallback_last_status", saved_snapshot.remark)

    @patch("quant_road.services.market_regime_service.save_market_status")
    @patch("quant_road.services.market_regime_service.fetch_latest_market_status")
    @patch("quant_road.services.market_regime_service.evaluate_market_status")
    def test_evaluate_and_save_market_status_raise_when_no_history(
        self,
        mock_evaluate_market_status,
        mock_fetch_latest_market_status,
        mock_save_market_status,
    ) -> None:
        from quant_road.services.market_regime_service import evaluate_and_save_market_status

        mock_evaluate_market_status.side_effect = RuntimeError("source_down")
        mock_fetch_latest_market_status.return_value = None

        with self.assertRaises(RuntimeError):
            evaluate_and_save_market_status(hold_days=2)
        mock_save_market_status.assert_not_called()


if __name__ == "__main__":
    unittest.main()
