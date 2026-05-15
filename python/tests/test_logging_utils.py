import logging
import os
import tempfile
import unittest
from datetime import date
from pathlib import Path
from unittest.mock import patch


class LoggingUtilsTestCase(unittest.TestCase):
    def tearDown(self) -> None:
        root_logger = logging.getLogger()
        for handler in list(root_logger.handlers):
            handler.close()
            root_logger.removeHandler(handler)

    def test_configure_logging_uses_date_stamped_file_name(self) -> None:
        from quant_road.logging_utils import configure_logging

        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"QUANT_ROAD_LOG_DIR": tmpdir}, clear=False):
                configure_logging()
                try:
                    logging.getLogger("quant_road.tests.logging").info("hello from logging test")
                    file_handlers = [
                        handler for handler in logging.getLogger().handlers if isinstance(handler, logging.FileHandler)
                    ]
                    self.assertEqual(len(file_handlers), 1)
                    log_path = Path(file_handlers[0].baseFilename)
                    self.assertRegex(log_path.name, r"^python\.\d{4}-\d{2}-\d{2}\.log$")
                    self.assertEqual(log_path.parent, Path(tmpdir))
                    self.assertTrue(log_path.exists())
                finally:
                    for handler in list(logging.getLogger().handlers):
                        handler.close()
                        logging.getLogger().removeHandler(handler)

    def test_daily_file_handler_switches_files_when_day_changes(self) -> None:
        from quant_road.logging_utils import configure_logging

        mocked_dates = [
            date(2026, 5, 9),
            date(2026, 5, 9),
            date(2026, 5, 10),
        ]

        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"QUANT_ROAD_LOG_DIR": tmpdir}, clear=False):
                with patch("quant_road.logging_utils._current_log_date", side_effect=mocked_dates):
                    configure_logging()
                    try:
                        logger = logging.getLogger("quant_road.tests.logging")
                        logger.info("first day message")
                        logger.info("second day message")
                    finally:
                        for handler in list(logging.getLogger().handlers):
                            handler.close()
                            logging.getLogger().removeHandler(handler)
                first_day_log = Path(tmpdir) / "python.2026-05-09.log"
                second_day_log = Path(tmpdir) / "python.2026-05-10.log"
                self.assertTrue(first_day_log.exists())
                self.assertTrue(second_day_log.exists())
                self.assertIn("first day message", first_day_log.read_text(encoding="utf-8"))
                self.assertIn("second day message", second_day_log.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
