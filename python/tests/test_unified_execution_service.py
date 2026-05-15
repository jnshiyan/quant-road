import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class UnifiedExecutionServiceTest(unittest.TestCase):
    @patch("quant_road.services.unified_execution_service.update_position_risk")
    @patch("quant_road.services.unified_execution_service.run_strategy")
    @patch("quant_road.services.unified_execution_service.sync_stock_daily")
    def test_execute_task_prunes_global_steps_for_small_scope(
        self,
        mock_sync_stock_daily,
        mock_run_strategy,
        mock_update_position_risk,
    ) -> None:
        from quant_road.services.unified_execution_service import execute_execution_plan

        execute_execution_plan(
            {
                "plan": {
                    "resolved_symbols": ["510300", "510500"],
                    "steps": [
                        {"step_name": "sync-daily", "step_scope": "scoped"},
                        {"step_name": "run-strategy", "step_scope": "scoped"},
                        {"step_name": "evaluate-risk", "step_scope": "scoped"},
                    ],
                    "resolved_execution_mode": "sync",
                },
                "request": {
                    "strategy_id": 7,
                    "strategy_backtest_start_date": "2024-01-01",
                    "actor": "unit-test",
                },
            }
        )

        mock_sync_stock_daily.assert_called_once()
        mock_run_strategy.assert_called_once()
        mock_update_position_risk.assert_called_once()

    @patch("quant_road.services.unified_execution_service.PipelineRunner")
    @patch("quant_road.services.unified_execution_service.update_position_risk")
    @patch("quant_road.services.unified_execution_service.run_strategy")
    @patch("quant_road.services.unified_execution_service.sync_stock_daily")
    def test_execute_execution_plan_runs_steps_in_order(
        self,
        mock_sync_stock_daily,
        mock_run_strategy,
        mock_update_position_risk,
        mock_pipeline_runner,
    ) -> None:
        from quant_road.services.unified_execution_service import execute_execution_plan

        executed_steps = []

        class FakeRunner:
            def __init__(self, pipeline_name, params, batch_id=None):
                self.pipeline_name = pipeline_name
                self.params = params
                self.batch_id = 77

            def start_batch(self):
                return self.batch_id

            def run_step(self, step_name, fn, retry=0):
                executed_steps.append(step_name)
                fn()

            def finalize(self, success, error_message=None):
                self.finalized = (success, error_message)

        mock_pipeline_runner.side_effect = FakeRunner
        mock_sync_stock_daily.return_value = {"status": "ok"}
        mock_run_strategy.return_value = {"status": "ok"}
        mock_update_position_risk.return_value = {"status": "ok"}

        result = execute_execution_plan(
            {
                "plan": {
                    "resolved_symbols": ["510300", "510500"],
                    "steps": [
                        {"step_name": "sync-daily"},
                        {"step_name": "run-strategy"},
                        {"step_name": "evaluate-risk"},
                    ],
                },
                "request": {
                    "strategy_id": 7,
                    "strategy_backtest_start_date": "2024-01-01",
                    "actor": "unit-test",
                },
            }
        )

        self.assertEqual(["sync-daily", "run-strategy", "evaluate-risk"], executed_steps)
        self.assertEqual("SUCCESS", result["status"])
        self.assertEqual(77, result["batch_id"])
        mock_sync_stock_daily.assert_called_once_with(symbols=["510300", "510500"], start_date=None, end_date=None)
        mock_run_strategy.assert_called_once_with(
            symbols=["510300", "510500"],
            start_date="2024-01-01",
            strategy_id=7,
            params_override=None,
            actor="unit-test",
        )
        mock_update_position_risk.assert_called_once_with(strategy_id=7)


if __name__ == "__main__":
    unittest.main()
