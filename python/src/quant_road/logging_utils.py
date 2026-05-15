from __future__ import annotations

import logging
import os
from datetime import date, datetime
from pathlib import Path


def _current_log_date() -> date:
    return datetime.now().date()


def _build_daily_log_path(log_dir: Path, current_date: date) -> Path:
    return log_dir / f"python.{current_date.isoformat()}.log"


class DailyFileHandler(logging.FileHandler):
    def __init__(self, log_dir: Path, encoding: str = "utf-8") -> None:
        self._log_dir = Path(log_dir)
        self._current_date = _current_log_date()
        super().__init__(_build_daily_log_path(self._log_dir, self._current_date), encoding=encoding)

    def emit(self, record: logging.LogRecord) -> None:
        self._rotate_if_needed()
        super().emit(record)

    def _rotate_if_needed(self) -> None:
        next_date = _current_log_date()
        if next_date == self._current_date:
            return

        self.acquire()
        try:
            if next_date == self._current_date:
                return
            if self.stream:
                self.stream.close()
                self.stream = None
            self._current_date = next_date
            self.baseFilename = os.fspath(_build_daily_log_path(self._log_dir, self._current_date))
            self.stream = self._open()
        finally:
            self.release()


def configure_logging() -> None:
    log_dir = Path(
        os.getenv("QUANT_ROAD_LOG_DIR")
        or (Path(__file__).resolve().parents[3] / "runtime-logs" / "quant-road")
    )
    log_dir.mkdir(parents=True, exist_ok=True)

    formatter = logging.Formatter("%(asctime)s | %(levelname)s | %(name)s | %(message)s")
    root_logger = logging.getLogger()
    for handler in list(root_logger.handlers):
        root_logger.removeHandler(handler)
        handler.close()
    root_logger.setLevel(logging.INFO)

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)

    file_handler = DailyFileHandler(log_dir=log_dir, encoding="utf-8")
    file_handler.setFormatter(formatter)

    root_logger.addHandler(console_handler)
    root_logger.addHandler(file_handler)
