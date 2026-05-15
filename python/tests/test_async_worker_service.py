import unittest
from unittest.mock import Mock, patch


class AsyncWorkerServiceTestCase(unittest.TestCase):
    @patch("quant_road.services.async_worker_service.run_strategy_batch")
    @patch("quant_road.services.async_worker_service.batch_execute")
    @patch("quant_road.services.async_worker_service.fetch_one")
    def test_run_worker_once_persists_results_and_completes_shard(
        self,
        mock_fetch_one,
        mock_batch_execute,
        mock_run_strategy_batch,
    ) -> None:
        from quant_road.services.async_worker_service import run_worker_once

        mock_redis = Mock()
        mock_redis.lpop.return_value = "job-1001-shard-0"
        mock_fetch_one.side_effect = [
            (
                11,
                1001,
                1,
                0,
                '{"symbols":["000001"],"start_date":"2023-01-01","actor":"worker-test"}',
                0,
            ),
            (1, 0, 1),
        ]
        mock_run_strategy_batch.return_value = {
            "000001": {
                "signal": "BUY",
                "annual_return": 12.3,
                "max_drawdown": 4.5,
                "win_rate": 55.0,
                "total_profit": 1234.5,
                "trade_count": 6,
                "total_cost": 12.5,
                "is_invalid": False,
                "remark": "ok",
                "execution_remark": "ok",
            }
        }

        payload = run_worker_once(worker_id="worker-1", redis_client=mock_redis)

        self.assertEqual(payload["status"], "SUCCESS")
        self.assertEqual(payload["job_id"], 1001)
        self.assertEqual(payload["shard_id"], 11)
        self.assertGreaterEqual(mock_batch_execute.call_count, 3)
        sql_texts = [call.args[0] for call in mock_batch_execute.call_args_list]
        self.assertTrue(any("UPDATE quant_async_job_attempt" in sql for sql in sql_texts))
        mock_redis.delete.assert_called()

    @patch("quant_road.services.async_worker_service.run_strategy_batch")
    @patch("quant_road.services.async_worker_service.batch_execute")
    @patch("quant_road.services.async_worker_service.fetch_one")
    def test_run_worker_once_marks_failed_shard_and_job_when_strategy_batch_raises(
        self,
        mock_fetch_one,
        mock_batch_execute,
        mock_run_strategy_batch,
    ) -> None:
        from quant_road.services.async_worker_service import run_worker_once

        mock_redis = Mock()
        mock_redis.lpop.return_value = "job-1002-shard-0"
        mock_fetch_one.side_effect = [
            (
                12,
                1002,
                1,
                0,
                '{"symbols":["000001"],"start_date":"2023-01-01","actor":"worker-test"}',
                0,
            ),
            (0, 1, 1),
        ]
        mock_run_strategy_batch.side_effect = RuntimeError("upstream boom")

        payload = run_worker_once(worker_id="worker-2", redis_client=mock_redis)

        self.assertEqual(payload["status"], "FAILED")
        self.assertEqual(payload["job_id"], 1002)
        self.assertEqual(payload["shard_id"], 12)
        sql_texts = [call.args[0] for call in mock_batch_execute.call_args_list]
        self.assertTrue(
            any("UPDATE quant_async_job_shard" in sql and "last_error = %s" in sql for sql in sql_texts)
        )
        self.assertTrue(
            any("UPDATE quant_async_job_attempt" in sql and "error_message = %s" in sql for sql in sql_texts)
        )
        self.assertTrue(
            any("UPDATE quant_async_job" in sql and "failed_shard_count = %s" in sql for sql in sql_texts)
        )
        persisted_values = " ".join(str(call.args[1]) for call in mock_batch_execute.call_args_list if len(call.args) > 1)
        self.assertIn("upstream boom", persisted_values)
        mock_redis.delete.assert_called()

    @patch("quant_road.services.async_worker_service.execute_execution_plan")
    @patch("quant_road.services.async_worker_service.run_strategy_batch")
    @patch("quant_road.services.async_worker_service.batch_execute")
    @patch("quant_road.services.async_worker_service.fetch_one")
    def test_run_worker_once_executes_unified_plan_payload(
        self,
        mock_fetch_one,
        mock_batch_execute,
        mock_run_strategy_batch,
        mock_execute_execution_plan,
    ) -> None:
        from quant_road.services.async_worker_service import run_worker_once

        mock_redis = Mock()
        mock_redis.lpop.return_value = "job-1003-shard-0"
        mock_fetch_one.side_effect = [
            (
                13,
                1003,
                None,
                0,
                '{"job_type":"execute-plan","execution_plan":{"resolved_symbols":["510300"],"steps":[{"step_name":"run-strategy"}]},"request":{"strategy_id":1,"strategy_backtest_start_date":"2024-01-01","actor":"worker-test"}}',
                0,
            ),
            (1, 0, 1),
        ]
        mock_execute_execution_plan.return_value = {
            "status": "SUCCESS",
            "batch_id": 321,
            "executed_steps": ["run-strategy"],
        }

        payload = run_worker_once(worker_id="worker-3", redis_client=mock_redis)

        self.assertEqual(payload["status"], "SUCCESS")
        self.assertEqual(payload["job_id"], 1003)
        self.assertEqual(payload["shard_id"], 13)
        mock_execute_execution_plan.assert_called_once()
        mock_run_strategy_batch.assert_not_called()
        sql_texts = [call.args[0] for call in mock_batch_execute.call_args_list]
        self.assertTrue(any("UPDATE quant_async_job_shard" in sql for sql in sql_texts))
        mock_redis.delete.assert_called()


if __name__ == "__main__":
    unittest.main()
