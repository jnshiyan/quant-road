import unittest
from unittest.mock import patch


class StrategyMarketRegimeTestCase(unittest.TestCase):
    def test_is_strategy_allowed_in_market(self) -> None:
        from quant_road.services.strategy_service import is_strategy_allowed_in_market

        # No configuration means allow all.
        self.assertTrue(is_strategy_allowed_in_market({}, "volatile"))
        # Explicit allow list
        self.assertTrue(is_strategy_allowed_in_market({"enabled_regimes": ["bull", "volatile"]}, "volatile"))
        self.assertFalse(is_strategy_allowed_in_market({"enabled_regimes": ["bull"]}, "bear"))
        # Non-string / invalid config falls back to allow-all
        self.assertTrue(is_strategy_allowed_in_market({"enabled_regimes": 123}, "bear"))
        # Missing market status should not block execution.
        self.assertTrue(is_strategy_allowed_in_market({"enabled_regimes": ["bull"]}, None))

    def test_has_market_regime_gate(self) -> None:
        from quant_road.services.strategy_service import _has_market_regime_gate

        self.assertFalse(_has_market_regime_gate({}))
        self.assertFalse(_has_market_regime_gate({"enabled_regimes": []}))
        self.assertFalse(_has_market_regime_gate({"enabled_regimes": ["", "   "]}))
        self.assertFalse(_has_market_regime_gate({"enabled_regimes": "bull"}))
        self.assertTrue(_has_market_regime_gate({"enabled_regimes": ["bull"]}))
        self.assertTrue(_has_market_regime_gate({"enabled_regimes": [" bull ", "volatile"]}))

    @patch("quant_road.services.strategy_service._save_run_log")
    @patch("quant_road.services.strategy_service._record_strategy_switch_audit")
    @patch("quant_road.services.strategy_service._latest_market_status_value")
    @patch("quant_road.services.strategy_service.resolve_strategy_executor")
    @patch("quant_road.services.strategy_service._load_strategy_config")
    def test_run_strategy_records_block_audit(
        self,
        mock_load_strategy_config,
        mock_resolve_executor,
        mock_latest_market_status,
        mock_record_switch_audit,
        mock_save_run_log,
    ) -> None:
        from quant_road.services.strategy_service import run_strategy

        class StubExecutor:
            @staticmethod
            def normalize_params(params):
                return dict(params)

        mock_load_strategy_config.return_value = ("MA", {"enabled_regimes": ["bull"]})
        mock_resolve_executor.return_value = StubExecutor()
        mock_latest_market_status.return_value = "bear"

        payload = run_strategy(strategy_id=1, actor="unit-test", trigger_source="test")
        self.assertEqual(payload, {})
        mock_record_switch_audit.assert_called_once()
        kwargs = mock_record_switch_audit.call_args.kwargs
        self.assertEqual(kwargs["decision"], "BLOCK")
        self.assertEqual(kwargs["market_status"], "bear")
        mock_save_run_log.assert_called_once()


if __name__ == "__main__":
    unittest.main()
