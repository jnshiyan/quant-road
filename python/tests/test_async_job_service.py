import unittest
from unittest.mock import patch


class AsyncJobServiceTestCase(unittest.TestCase):
    @patch("quant_road.services.async_job_service.push_shards_to_queue")
    @patch("quant_road.services.async_job_service.batch_execute")
    @patch("quant_road.services.async_job_service.fetch_one")
    @patch("quant_road.services.async_job_service._load_active_strategies")
    @patch("quant_road.services.async_job_service._resolve_symbols")
    def test_plan_run_portfolio_job_creates_strategy_chunks(
        self,
        mock_resolve_symbols,
        mock_load_active_strategies,
        mock_fetch_one,
        mock_batch_execute,
        mock_push_shards_to_queue,
    ) -> None:
        from quant_road.services.async_job_service import plan_async_job

        mock_resolve_symbols.return_value = ["000001", "000002", "000003"]
        mock_load_active_strategies.return_value = [
            {"strategy_id": 1, "strategy_type": "MA", "params": {}},
            {"strategy_id": 2, "strategy_type": "MA_DUAL", "params": {}},
        ]
        mock_fetch_one.return_value = (1001,)

        job = plan_async_job(
            job_type="run-portfolio",
            payload={
                "strategy_start_date": "2023-01-01",
                "requested_mode": "async",
                "actor": "planner-test",
            },
            chunk_size=2,
        )

        self.assertEqual(job["job_id"], 1001)
        self.assertEqual(job["planned_shard_count"], 4)
        self.assertTrue(all("symbols" in shard["payload"] for shard in job["shards"]))
        self.assertEqual(mock_batch_execute.call_count, 3)
        mock_push_shards_to_queue.assert_called_once()


if __name__ == "__main__":
    unittest.main()
