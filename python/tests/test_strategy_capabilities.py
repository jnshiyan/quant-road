import unittest

from quant_road.strategies.registry import list_strategy_capabilities


class StrategyCapabilitiesTest(unittest.TestCase):
    def test_capabilities_include_dual_ma(self) -> None:
        capabilities = list_strategy_capabilities()
        by_type = {item["strategy_type"]: item for item in capabilities}

        self.assertIn("MA_DUAL", by_type)
        dual = by_type["MA_DUAL"]
        self.assertIn("short_ma_period", dual["required_params"])
        self.assertIn("long_ma_period", dual["required_params"])
        self.assertEqual(dual["sample_params"]["short_ma_period"], 5)
        self.assertEqual(dual["sample_params"]["long_ma_period"], 20)

    def test_capabilities_include_single_ma(self) -> None:
        capabilities = list_strategy_capabilities()
        by_type = {item["strategy_type"]: item for item in capabilities}

        self.assertIn("MA", by_type)
        self.assertIn("ma_period", by_type["MA"]["required_params"])


if __name__ == "__main__":
    unittest.main()
