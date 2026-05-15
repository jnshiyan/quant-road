import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


class ShadowCompareTest(unittest.TestCase):
    @patch("quant_road.services.report_service.fetch_all")
    def test_fetch_shadow_compare_payload_has_deltas(self, mock_fetch_all) -> None:
        mock_fetch_all.side_effect = [
            [
                (1, "MA20_CROSS", "MA"),
                (2, "MA_DUAL_CROSS_5_20", "MA_DUAL"),
            ],
            [
                ("2026-05", 1, 10, 8.0, -6.0, 55.0, 4.0, 1),
                ("2026-05", 2, 10, 10.0, -5.0, 60.0, 6.0, 0),
            ],
        ]
        from quant_road.services.report_service import fetch_shadow_compare_payload

        payload = fetch_shadow_compare_payload(1, 2, months=6)
        self.assertEqual(payload["summary"]["comparable_months"], 1)
        month = payload["months_data"][0]
        self.assertEqual(month["delta"]["avg_annual_return"], 2.0)
        self.assertEqual(month["delta"]["avg_max_drawdown"], 1.0)
        self.assertEqual(month["delta"]["avg_win_rate"], 5.0)
        self.assertEqual(month["delta"]["invalid_rate"], -10.0)

    @patch("quant_road.services.report_service.fetch_all")
    def test_build_shadow_compare_summary_contains_strategy_names(self, mock_fetch_all) -> None:
        mock_fetch_all.side_effect = [
            [
                (1, "MA20_CROSS", "MA"),
                (2, "MA_DUAL_CROSS_5_20", "MA_DUAL"),
            ],
            [
                ("2026-05", 1, 10, 8.0, -6.0, 55.0, 4.0, 1),
                ("2026-05", 2, 10, 10.0, -5.0, 60.0, 6.0, 0),
            ],
        ]
        from quant_road.services.report_service import build_shadow_compare_summary, fetch_shadow_compare_payload

        payload = fetch_shadow_compare_payload(1, 2, months=6)
        summary = build_shadow_compare_summary(payload)
        self.assertIn("MA20_CROSS", summary)
        self.assertIn("MA_DUAL_CROSS_5_20", summary)
        self.assertIn("candidate年化更优=1", summary)

    def test_write_shadow_compare_summary(self) -> None:
        from quant_road.services.report_service import write_shadow_compare_summary

        with tempfile.TemporaryDirectory() as tmpdir:
            target = Path(tmpdir) / "shadow-report.md"
            written = write_shadow_compare_summary(
                summary="shadow report content",
                baseline_strategy_id=1,
                candidate_strategy_id=2,
                output_path=str(target),
            )
            self.assertEqual(written, target)
            self.assertTrue(target.exists())
            self.assertIn("shadow report content", target.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
