import unittest
from unittest.mock import patch

import pandas as pd


def _sample_daily_frame() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "日期": ["2026-05-01", "2026-05-02"],
            "开盘": [4.8, 4.9],
            "收盘": [4.85, 4.95],
            "最高": [4.9, 5.0],
            "最低": [4.75, 4.85],
            "成交量": [1000, 1200],
        }
    )


class AkshareCollectorTest(unittest.TestCase):
    @patch("quant_road.collectors.akshare_collector._upsert_stock_basic")
    @patch("quant_road.collectors.akshare_collector.ak.stock_zh_a_spot_em")
    def test_sync_stock_basic_temporarily_disables_proxy_env(
        self,
        mock_spot,
        mock_upsert,
    ) -> None:
        from quant_road.collectors.akshare_collector import sync_stock_basic

        def _fake_spot():
            self.assertNotIn("HTTP_PROXY", __import__("os").environ)
            self.assertNotIn("HTTPS_PROXY", __import__("os").environ)
            return pd.DataFrame(
                {
                    "代码": ["000001"],
                    "名称": ["平安银行"],
                    "行业": ["银行"],
                    "上市时间": ["19910403"],
                }
            )

        mock_spot.side_effect = _fake_spot

        with patch.dict("os.environ", {"HTTP_PROXY": "http://broken-proxy", "HTTPS_PROXY": "http://broken-proxy"}, clear=False):
            summary = sync_stock_basic()

        self.assertEqual(summary["source"], "akshare")
        self.assertFalse(summary["usedFallback"])
        self.assertEqual(summary["totalCount"], 1)
        mock_upsert.assert_called_once()

    @patch("quant_road.collectors.akshare_collector.fetch_all")
    @patch("quant_road.collectors.akshare_collector._upsert_stock_basic")
    @patch("quant_road.collectors.akshare_collector.ak.stock_zh_a_spot_em")
    def test_sync_stock_basic_falls_back_to_existing_db_rows_when_akshare_unavailable(
        self,
        mock_spot,
        mock_upsert,
        mock_fetch_all,
    ) -> None:
        from quant_road.collectors.akshare_collector import sync_stock_basic

        mock_spot.side_effect = RuntimeError("proxy_unavailable")
        mock_fetch_all.return_value = [("000001",), ("600519",)]

        summary = sync_stock_basic()

        self.assertEqual(summary["source"], "stock_basic_fallback")
        self.assertTrue(summary["usedFallback"])
        self.assertEqual(summary["totalCount"], 2)
        mock_upsert.assert_not_called()
        mock_fetch_all.assert_called_once()

    @patch("quant_road.collectors.akshare_collector.batch_execute")
    @patch("quant_road.collectors.akshare_collector.ak.fund_etf_spot_em")
    @patch("quant_road.collectors.akshare_collector.ak.fund_etf_hist_em")
    @patch("quant_road.collectors.akshare_collector.ak.stock_zh_a_hist")
    def test_sync_stock_daily_falls_back_to_etf_history_and_upserts_basic(
        self,
        mock_stock_hist,
        mock_etf_hist,
        mock_etf_spot,
        mock_batch_execute,
    ) -> None:
        from quant_road.collectors.akshare_collector import sync_stock_daily

        mock_stock_hist.return_value = pd.DataFrame()
        mock_etf_hist.return_value = _sample_daily_frame()
        mock_etf_spot.return_value = pd.DataFrame(
            {
                "代码": ["510300"],
                "名称": ["沪深300ETF华泰柏瑞"],
            }
        )

        summary = sync_stock_daily(
            symbols=["510300"],
            start_date="20230505",
            end_date="20260505",
        )

        self.assertEqual(summary["upsertedRows"], 2)
        self.assertEqual(summary["successCount"], 1)
        self.assertEqual(summary["failedCount"], 0)
        self.assertEqual(summary["skippedSymbols"], [])
        mock_stock_hist.assert_called_once_with(
            symbol="510300",
            period="daily",
            start_date="20230505",
            end_date="20260505",
            adjust="qfq",
        )
        mock_etf_hist.assert_called_once_with(
            symbol="510300",
            period="daily",
            start_date="20230505",
            end_date="20260505",
            adjust="qfq",
        )
        self.assertEqual(mock_batch_execute.call_count, 2)

        basic_sql, basic_rows = mock_batch_execute.call_args_list[0].args[:2]
        self.assertIn("INSERT INTO stock_basic", basic_sql)
        self.assertEqual(basic_rows, [("510300", "沪深300ETF华泰柏瑞", "ETF", 0, None)])

        daily_sql, daily_rows = mock_batch_execute.call_args_list[1].args[:2]
        self.assertIn("INSERT INTO stock_daily", daily_sql)
        self.assertEqual(len(daily_rows), 2)
        self.assertTrue(all(row[0] == "510300" for row in daily_rows))

    @patch("quant_road.collectors.akshare_collector.batch_execute")
    @patch("quant_road.collectors.akshare_collector._load_etf_name_map")
    @patch("quant_road.collectors.akshare_collector._load_daily_frame")
    def test_sync_stock_daily_skips_failed_symbol_and_continues(
        self,
        mock_load_daily_frame,
        mock_load_etf_name_map,
        mock_batch_execute,
    ) -> None:
        from quant_road.collectors.akshare_collector import sync_stock_daily

        def _side_effect(symbol, start_date, end_date, adjust):
            if symbol == "000002":
                raise RuntimeError("remote_disconnected")
            return _sample_daily_frame(), "stock"

        mock_load_daily_frame.side_effect = _side_effect
        mock_load_etf_name_map.return_value = {}

        summary = sync_stock_daily(
            symbols=["000001", "000002", "000003"],
            start_date="20230505",
            end_date="20260505",
        )

        self.assertEqual(summary["upsertedRows"], 4)
        self.assertEqual(summary["successCount"], 2)
        self.assertEqual(summary["failedCount"], 1)
        self.assertEqual(summary["failedSymbols"], ["000002"])
        self.assertEqual(mock_batch_execute.call_count, 2)
        inserted_symbols = []
        for call in mock_batch_execute.call_args_list:
            sql, rows = call.args[:2]
            self.assertIn("INSERT INTO stock_daily", sql)
            inserted_symbols.extend({row[0] for row in rows})
        self.assertEqual(set(inserted_symbols), {"000001", "000003"})


if __name__ == "__main__":
    unittest.main()
