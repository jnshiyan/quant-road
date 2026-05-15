import unittest
from unittest.mock import patch
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


class PipelineBatchTest(unittest.TestCase):
    @patch("quant_road.services.pipeline_service.batch_execute")
    @patch("quant_road.services.pipeline_service.fetch_one")
    def test_pipeline_step_success_persists_result_summary(self, mock_fetch_one, mock_batch_execute) -> None:
        from quant_road.services.pipeline_service import PipelineRunner

        mock_fetch_one.return_value = (1,)
        runner = PipelineRunner("full-daily", {"strategy_id": 1})
        runner.start_batch()

        def ok() -> dict:
            return {"upsertedRows": 10, "skippedSymbols": ["000002"], "failedCount": 1}

        runner.run_step("sync-daily", ok, retry=0)

        persisted_values = [call.args[1] for call in mock_batch_execute.call_args_list if len(call.args) > 1]
        self.assertTrue(any("upsertedRows" in str(item) for item in persisted_values))
        self.assertTrue(any("000002" in str(item) for item in persisted_values))

    @patch("quant_road.services.pipeline_service.batch_execute")
    @patch("quant_road.services.pipeline_service.fetch_one")
    def test_pipeline_step_failure_marks_batch_failed(self, mock_fetch_one, mock_batch_execute) -> None:
        from quant_road.services.pipeline_service import PipelineRunner

        # start_batch returns batch_id
        mock_fetch_one.return_value = (1,)
        runner = PipelineRunner("full-daily", {"strategy_id": 1})
        runner.start_batch()

        def broken() -> None:
            raise RuntimeError("boom")

        with self.assertRaises(RuntimeError):
            runner.run_step("sync-daily", broken, retry=0)

        runner.finalize(success=False, error_message="boom")
        self.assertGreaterEqual(mock_batch_execute.call_count, 3)

    def test_init_sql_contains_quant_async_job_tables(self) -> None:
        text = (REPO_ROOT / "sql" / "init.sql").read_text(encoding="utf-8")
        required_tables = {
            "quant_async_job",
            "quant_async_job_shard",
            "quant_async_job_attempt",
            "quant_async_job_summary",
            "quant_async_job_result",
        }
        for table_name in required_tables:
            self.assertIn(table_name, text)

    def test_scheduler_target_uses_async_submission_contract(self) -> None:
        text = (REPO_ROOT / "sql" / "ruoyi_quant_jobs.sql").read_text(encoding="utf-8")
        self.assertIn("quantRoadTask.fullDailyAsync()", text)


if __name__ == "__main__":
    unittest.main()
