import unittest
from unittest.mock import patch

import pandas as pd


class RiskServiceTestCase(unittest.TestCase):
    @patch("quant_road.services.risk_service.fetch_all", return_value=[])
    @patch("quant_road.services.risk_service.ak.index_zh_a_hist")
    def test_fetch_benchmark_monthly_returns_returns_empty_series_when_sources_unavailable(
        self,
        mock_index_hist,
        mock_fetch_all,
    ) -> None:
        from quant_road.services.risk_service import fetch_benchmark_monthly_returns

        fetch_benchmark_monthly_returns.cache_clear()
        mock_index_hist.side_effect = RuntimeError("source_down")

        result = fetch_benchmark_monthly_returns(months=3, end_date_iso="2026-05-06")

        self.assertIsInstance(result, pd.Series)
        self.assertTrue(result.empty)
        mock_fetch_all.assert_called_once()


if __name__ == "__main__":
    unittest.main()
