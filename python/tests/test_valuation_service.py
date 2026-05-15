import unittest
from unittest.mock import patch

import pandas as pd


class ValuationServiceTestCase(unittest.TestCase):
    def test_compute_percentile(self) -> None:
        from quant_road.services.valuation_service import compute_percentile

        self.assertEqual(compute_percentile(10, [10, 20, 30, 40]), 25.0)
        self.assertEqual(compute_percentile(40, [10, 20, 30, 40]), 100.0)
        self.assertIsNone(compute_percentile(None, [10, 20]))
        self.assertIsNone(compute_percentile(10, []))

    def test_normalize_index_code(self) -> None:
        from quant_road.services.valuation_service import normalize_index_code

        self.assertEqual(normalize_index_code("000300.SH"), "000300")
        self.assertEqual(normalize_index_code("sh000905"), "000905")
        self.assertEqual(normalize_index_code("399006"), "399006")

    @patch("quant_road.services.valuation_service.ak.stock_index_pb_lg")
    @patch("quant_road.services.valuation_service.ak.stock_index_pe_lg")
    def test_fallback_quotes_from_lg(self, mock_pe, mock_pb) -> None:
        from quant_road.services.valuation_service import _fallback_quotes_from_lg

        mock_pe.return_value = pd.DataFrame(
            {
                "日期": ["2026-05-01", "2026-05-02"],
                "滚动市盈率": [12.3, 13.5],
            }
        )
        mock_pb.return_value = pd.DataFrame(
            {
                "日期": ["2026-05-01", "2026-05-02"],
                "市净率": [1.23, 1.31],
            }
        )

        quotes = _fallback_quotes_from_lg()
        self.assertIn("000300", quotes)
        self.assertEqual(quotes["000300"]["pe"], 13.5)
        self.assertEqual(quotes["000300"]["pb"], 1.31)


if __name__ == "__main__":
    unittest.main()
