import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


class MonthlyReportTest(unittest.TestCase):
    @patch("quant_road.services.report_service.fetch_all")
    def test_build_monthly_summary_includes_grouped_metrics(self, mock_fetch_all) -> None:
        mock_fetch_all.return_value = [
            ("2026-05", 1, 12, 8.21, -4.56, 58.33, 6.78, 1),
            ("2026-04", 1, 10, 6.11, -5.12, 52.20, 4.30, 0),
        ]
        from quant_road.services.report_service import build_monthly_summary

        summary = build_monthly_summary(months=6)

        self.assertIn("Quant Road 月度策略评估", summary)
        self.assertIn("[2026-05]", summary)
        self.assertIn("strategy=1 runs=12 invalid=1", summary)
        self.assertIn("avg_annual=8.21%", summary)

    def test_write_monthly_summary_writes_target_file(self) -> None:
        from quant_road.services.report_service import write_monthly_summary

        with tempfile.TemporaryDirectory() as tmpdir:
            target = Path(tmpdir) / "report.md"
            written = write_monthly_summary("hello report", output_path=str(target))

            self.assertEqual(written, target)
            self.assertTrue(target.exists())
            self.assertIn("hello report", target.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
