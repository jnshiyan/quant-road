import unittest
import warnings
from decimal import Decimal
from unittest.mock import patch


class _FakeCursor:
    def __init__(self) -> None:
        self.description = [("stock_code",), ("close",)]
        self.executed = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, sql, params=None) -> None:
        self.executed.append((sql, params))

    def fetchall(self):
        return [("510300", Decimal("4.82")), ("510500", Decimal("8.43"))]

    def close(self) -> None:
        return None


class _FakeConnection:
    def __init__(self) -> None:
        self.cursor_obj = _FakeCursor()

    def cursor(self):
        return self.cursor_obj


class DbQueryDataFrameTest(unittest.TestCase):
    def test_query_dataframe_returns_dataframe_without_pandas_sql_warning(self) -> None:
        from quant_road.db import query_dataframe

        fake_conn = _FakeConnection()

        @patch("quant_road.db.get_connection")
        def run_test(mock_get_connection) -> None:
            mock_get_connection.return_value.__enter__.return_value = fake_conn
            mock_get_connection.return_value.__exit__.return_value = False

            with warnings.catch_warnings(record=True) as captured:
                warnings.simplefilter("always")
                df = query_dataframe("SELECT stock_code, close FROM stock_daily WHERE stock_code = %s", ("510300",))

            self.assertEqual(list(df.columns), ["stock_code", "close"])
            self.assertEqual(df.to_dict("records"), [{"stock_code": "510300", "close": 4.82}, {"stock_code": "510500", "close": 8.43}])
            self.assertEqual(fake_conn.cursor_obj.executed, [("SELECT stock_code, close FROM stock_daily WHERE stock_code = %s", ("510300",))])
            self.assertFalse(any("pandas only supports SQLAlchemy" in str(item.message) for item in captured))

        run_test()


if __name__ == "__main__":
    unittest.main()
