import unittest
from datetime import date


def _rolling_start_date(years: int, dashed: bool) -> str:
    today = date.today()
    try:
        target = today.replace(year=today.year - years)
    except ValueError:
        target = today.replace(month=2, day=28, year=today.year - years)
    return target.strftime("%Y-%m-%d" if dashed else "%Y%m%d")


class ConfigDefaultWindowTest(unittest.TestCase):
    def test_legacy_default_start_date_resolves_to_rolling_five_years(self) -> None:
        from quant_road.config import _resolve_default_start_date

        expected = _rolling_start_date(5, dashed=False)
        self.assertEqual(_resolve_default_start_date("20230101"), expected)
        self.assertEqual(_resolve_default_start_date(""), expected)

    def test_legacy_backtest_start_date_resolves_to_rolling_five_years(self) -> None:
        from quant_road.config import _resolve_strategy_backtest_start_date

        expected = _rolling_start_date(5, dashed=True)
        self.assertEqual(_resolve_strategy_backtest_start_date("2023-01-01"), expected)
        self.assertEqual(_resolve_strategy_backtest_start_date(""), expected)


if __name__ == "__main__":
    unittest.main()
