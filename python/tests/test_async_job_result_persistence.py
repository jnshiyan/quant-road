import unittest
from unittest.mock import patch


class AsyncJobResultPersistenceTestCase(unittest.TestCase):
    @patch("quant_road.services.async_worker_service.batch_execute")
    def test_persist_shard_outputs_upserts_result_rows(self, mock_batch_execute) -> None:
        from quant_road.services.async_worker_service import persist_shard_outputs

        persist_shard_outputs(
            job_id=1001,
            strategy_id=2,
            rows=[
                (
                    "000001",
                    {
                        "signal": "BUY",
                        "annual_return": 10.1,
                        "max_drawdown": 2.2,
                        "win_rate": 60.0,
                        "total_profit": 100.0,
                        "trade_count": 3,
                        "total_cost": 1.2,
                        "is_invalid": False,
                        "remark": "ok",
                    },
                )
            ],
        )

        mock_batch_execute.assert_called_once()
        args = mock_batch_execute.call_args[0]
        self.assertIn("quant_async_job_result", args[0])
        self.assertEqual(args[1][0][0], 1001)


if __name__ == "__main__":
    unittest.main()
