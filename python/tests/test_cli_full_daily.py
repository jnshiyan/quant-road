import unittest
import sys
from pathlib import Path
from io import StringIO
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from quant_road.cli import build_parser, main


class FullDailyCliTest(unittest.TestCase):
    def test_full_daily_accepts_scope_metadata_arguments(self) -> None:
        parser = build_parser()

        args = parser.parse_args(
            [
                "full-daily",
                "--symbols",
                "510300,510500",
                "--scope-type",
                "etf_pool",
                "--scope-pool-code",
                "ETF_CORE",
            ]
        )

        self.assertEqual(args.command, "full-daily")
        self.assertEqual(args.scope_type, "etf_pool")
        self.assertEqual(args.scope_pool_code, "ETF_CORE")

    @patch("quant_road.cli.configure_logging")
    @patch("quant_road.services.unified_execution_service.execute_execution_plan")
    def test_execute_task_accepts_plan_json_and_prints_result(self, mock_execute, _mock_configure_logging) -> None:
        mock_execute.return_value = {"status": "SUCCESS", "batch_id": 88}

        with patch.object(
            sys,
            "argv",
            [
                "quant-road",
                "execute-task",
                "--plan-json",
                '{"resolved_symbols":["510300"],"steps":[{"step_name":"run-strategy"}]}',
            ],
        ):
            with patch("sys.stdout", new_callable=StringIO) as stdout:
                main()

        self.assertIn('"status": "SUCCESS"', stdout.getvalue())
        self.assertIn('"batch_id": 88', stdout.getvalue())


if __name__ == "__main__":
    unittest.main()
