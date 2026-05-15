import unittest
from datetime import date
from unittest.mock import patch


class ExecutionFeedbackServiceTestCase(unittest.TestCase):
    @patch("quant_road.services.execution_feedback_service.batch_execute")
    @patch("quant_road.services.execution_feedback_service.fetch_all")
    def test_evaluate_execution_feedback_builds_summary(self, mock_fetch_all, mock_batch_execute) -> None:
        from quant_road.services.execution_feedback_service import evaluate_execution_feedback

        mock_fetch_all.return_value = [
            (11, "000001", "BUY", date(2026, 5, 2), 1, 0, 0, None),  # should be MISSED on 2026-05-04 with grace=1
            (12, "000002", "BUY", date(2026, 5, 4), 1, 0, 0, None),  # should be PENDING
            (13, "000003", "SELL", date(2026, 5, 3), 1, 1, 100, date(2026, 5, 4)),  # should be EXECUTED
        ]

        payload = evaluate_execution_feedback(as_of_date="2026-05-04", grace_days=1)

        self.assertEqual(payload["total_signals"], 3)
        self.assertEqual(payload["executed"], 1)
        self.assertEqual(payload["missed"], 1)
        self.assertEqual(payload["pending"], 1)
        mock_batch_execute.assert_called_once()


if __name__ == "__main__":
    unittest.main()
