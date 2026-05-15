import unittest
from unittest.mock import patch


class ExecutionServiceTest(unittest.TestCase):
    @patch("quant_road.services.execution_service.fetch_one")
    def test_validate_execution_import_summarizes_invalid_duplicate_and_unmatched_rows(
        self,
        mock_fetch_one,
    ) -> None:
        from pathlib import Path
        import tempfile

        from quant_road.services.execution_service import validate_execution_import

        temp_dir = tempfile.TemporaryDirectory()
        csv_path = Path(temp_dir.name) / "executions.csv"
        csv_path.write_text(
            "\n".join(
                [
                    "stock_code,side,quantity,price,trade_date,strategy_id,external_order_id",
                    "000001,BUY,100,10,2026-05-06,1,ext-001",
                    "000001,BUY,100,10,2026-05-06,1,ext-001",
                    "000002,SELL,0,9,2026-05-06,1,",
                    "000003,BUY,100,8,2026-05-06,1,",
                ]
            ),
            encoding="utf-8",
        )

        mock_fetch_one.side_effect = [
            (1,),  # row1 strategy
            (1,),  # row1 stock
            None,  # row1 duplicate external id
            None,  # row1 no duplicate composite
            (123,),  # row1 auto match signal
            (1,),  # row2 strategy
            (1,),  # row2 stock
            (1,),  # row4 strategy
            (1,),  # row4 stock
            None,  # row4 no duplicate composite
            None,  # row4 no signal match
        ]

        payload = validate_execution_import(str(csv_path), default_strategy_id=1)

        self.assertEqual(4, payload["totalRows"])
        self.assertEqual(1, payload["validRows"])
        self.assertEqual(1, payload["duplicateRows"])
        self.assertEqual(1, payload["invalidRows"])
        self.assertEqual(1, payload["unmatchedSignalRows"])
        self.assertFalse(payload["canImport"])
        self.assertEqual("VALID", payload["previewRows"][0]["status"])
        self.assertEqual("DUPLICATE", payload["previewRows"][1]["status"])
        self.assertEqual("INVALID", payload["previewRows"][2]["status"])
        self.assertEqual("UNMATCHED_SIGNAL", payload["previewRows"][3]["status"])
        self.assertEqual("NO_ACTION", payload["previewRows"][0]["recommendedAction"])
        self.assertEqual("REVIEW_DUPLICATE_EXECUTION", payload["previewRows"][1]["recommendedAction"])
        self.assertEqual("FIX_SOURCE_FILE", payload["previewRows"][2]["recommendedAction"])
        self.assertEqual("CHECK_SIGNAL_MATCH", payload["previewRows"][3]["recommendedAction"])
        self.assertEqual("records", payload["previewRows"][1]["actionTarget"])
        self.assertEqual("import", payload["previewRows"][2]["actionTarget"])
        self.assertEqual("signals", payload["previewRows"][3]["actionTarget"])

    @patch("quant_road.services.execution_service.batch_execute")
    @patch("quant_road.services.execution_service.fetch_one")
    def test_buy_execution_updates_position_and_marks_signal(
        self,
        mock_fetch_one,
        mock_batch_execute,
    ) -> None:
        from quant_road.services.execution_service import apply_execution

        # signal_id query -> existing position query
        mock_fetch_one.side_effect = [(123,), None]

        apply_execution(
            stock_code="000001",
            side="BUY",
            quantity=100,
            price=10.0,
            trade_date="2026-05-04",
            strategy_id=1,
            commission=1.0,
            tax=0.0,
            slippage=0.2,
        )

        # 1) insert execution 2) upsert position 3) mark signal executed
        self.assertGreaterEqual(mock_batch_execute.call_count, 3)

    @patch("quant_road.services.execution_service.batch_execute")
    @patch("quant_road.services.execution_service.fetch_one")
    def test_invalid_signal_id_falls_back_to_none_without_marking_signal(
        self,
        mock_fetch_one,
        mock_batch_execute,
    ) -> None:
        from quant_road.services.execution_service import apply_execution

        # 1) explicit signal_id existence check -> not found
        # 2) auto-resolve latest signal -> not found
        # 3) position lookup -> no existing position
        mock_fetch_one.side_effect = [None, None, None]

        result = apply_execution(
            stock_code="000001",
            side="BUY",
            quantity=100,
            price=10.0,
            trade_date="2026-05-04",
            strategy_id=1,
            signal_id=1001,
        )

        self.assertIsNone(result["signal_id"])
        self.assertEqual(mock_batch_execute.call_count, 2)

        insert_rows = mock_batch_execute.call_args_list[0].args[1]
        self.assertIsNone(insert_rows[0][6])

    @patch("quant_road.services.execution_service.apply_execution")
    @patch("quant_road.services.execution_service.validate_execution_import")
    def test_import_executions_returns_structured_follow_up_payload(
        self,
        mock_validate_execution_import,
        mock_apply_execution,
    ) -> None:
        from pathlib import Path
        import tempfile

        from quant_road.services.execution_service import import_executions_from_csv

        temp_dir = tempfile.TemporaryDirectory()
        csv_path = Path(temp_dir.name) / "executions.csv"
        csv_path.write_text(
            "\n".join(
                [
                    "stock_code,side,quantity,price,trade_date,strategy_id",
                    "000001,BUY,100,10,2026-05-06,1",
                    "000002,BUY,200,11,2026-05-06,1",
                ]
            ),
            encoding="utf-8",
        )
        mock_validate_execution_import.return_value = {
            "file": str(csv_path),
            "totalRows": 2,
            "validRows": 1,
            "invalidRows": 0,
            "duplicateRows": 0,
            "unmatchedSignalRows": 1,
            "previewRows": [
                {
                    "rowNo": 2,
                    "status": "VALID",
                    "stockCode": "000001",
                    "side": "BUY",
                    "quantity": 100,
                    "price": 10.0,
                    "tradeDate": "2026-05-06",
                    "strategyId": 1,
                    "signalId": 5001,
                },
                {
                    "rowNo": 3,
                    "status": "UNMATCHED_SIGNAL",
                    "stockCode": "000002",
                    "side": "BUY",
                    "quantity": 200,
                    "price": 11.0,
                    "tradeDate": "2026-05-06",
                    "strategyId": 1,
                    "signalId": None,
                }
            ],
            "canImport": True,
        }

        payload = import_executions_from_csv(str(csv_path), default_strategy_id=1)

        self.assertEqual(2, payload["appliedRows"])
        self.assertEqual(1, payload["importedUnmatchedRows"])
        self.assertTrue(payload["needsManualMatch"])
        self.assertEqual(1, len(payload["unmatchedPreviewRows"]))
        self.assertEqual("000002", payload["unmatchedPreviewRows"][0]["stockCode"])
        self.assertEqual(2, mock_apply_execution.call_count)


if __name__ == "__main__":
    unittest.main()
