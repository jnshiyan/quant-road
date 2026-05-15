from __future__ import annotations

import os
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path


def _load_dotenv() -> None:
    module_root = Path(__file__).resolve().parents[2]
    repo_root = module_root.parent
    candidates = [Path(".env"), module_root / ".env", repo_root / ".env"]
    for env_path in candidates:
        if not env_path.exists():
            continue
        for line in env_path.read_text(encoding="utf-8").splitlines():
            raw = line.strip()
            if not raw or raw.startswith("#") or "=" not in raw:
                continue
            key, value = raw.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip())


_load_dotenv()


def _get_env(name: str, default: str | None = None) -> str:
    value = os.getenv(name, default)
    if value is None:
        raise ValueError(f"Missing required environment variable: {name}")
    return value


def _parse_csv(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


_LEGACY_DEFAULT_START_DATE = "20230101"
_LEGACY_BACKTEST_START_DATE = "2023-01-01"


def _rolling_start_date(years: int, dashed: bool) -> str:
    today = date.today()
    try:
        target = today.replace(year=today.year - years)
    except ValueError:
        target = today.replace(month=2, day=28, year=today.year - years)
    return target.strftime("%Y-%m-%d" if dashed else "%Y%m%d")


def _resolve_default_start_date(raw: str | None) -> str:
    text = "" if raw is None else raw.strip()
    if not text or text == _LEGACY_DEFAULT_START_DATE:
        return _rolling_start_date(5, dashed=False)
    return text


def _resolve_strategy_backtest_start_date(raw: str | None) -> str:
    text = "" if raw is None else raw.strip()
    if not text or text == _LEGACY_BACKTEST_START_DATE:
        return _rolling_start_date(5, dashed=True)
    return text


@dataclass(frozen=True)
class Settings:
    pg_host: str = _get_env("PG_HOST", "localhost")
    pg_port: int = int(_get_env("PG_PORT", "5432"))
    pg_user: str = _get_env("PG_USER", "postgres")
    pg_password: str = _get_env("PG_PASSWORD", "replace_me")
    pg_database: str = _get_env("PG_DATABASE", "db-quant")
    pg_pool_size: int = int(_get_env("PG_POOL_SIZE", "8"))
    target_stocks: list[str] = field(default_factory=lambda: _parse_csv(os.getenv("TARGET_STOCKS", "")))
    ma_period: int = int(_get_env("MA_PERIOD", "20"))
    stop_loss_rate: float = float(_get_env("STOP_LOSS_RATE", "0.08"))
    commission_rate: float = float(_get_env("COMMISSION_RATE", "0.0001"))
    slippage_rate: float = float(_get_env("SLIPPAGE_RATE", "0.0005"))
    stamp_duty_rate: float = float(_get_env("STAMP_DUTY_RATE", "0.001"))
    max_single_position_pct: float = float(_get_env("MAX_SINGLE_POSITION_PCT", "0.15"))
    max_total_position_pct: float = float(_get_env("MAX_TOTAL_POSITION_PCT", "0.80"))
    strategy_id: int = int(_get_env("STRATEGY_ID", "1"))
    portfolio_total_capital: float = float(_get_env("PORTFOLIO_TOTAL_CAPITAL", "100000"))
    default_start_date: str = field(default_factory=lambda: _resolve_default_start_date(os.getenv("DEFAULT_START_DATE")))
    strategy_backtest_start_date: str = field(
        default_factory=lambda: _resolve_strategy_backtest_start_date(os.getenv("STRATEGY_BACKTEST_START_DATE"))
    )
    pipeline_step_retry: int = int(_get_env("PIPELINE_STEP_RETRY", "1"))
    adaptive_hold_days: int = int(_get_env("ADAPTIVE_HOLD_DAYS", "2"))
    valuation_index_codes: list[str] = field(
        default_factory=lambda: _parse_csv(os.getenv("VALUATION_INDEX_CODES", "000300,000905,000852,399006"))
    )
    canary_enabled: bool = _get_env("CANARY_ENABLED", "false").lower() == "true"
    canary_baseline_strategy_id: int = int(_get_env("CANARY_BASELINE_STRATEGY_ID", "1"))
    canary_candidate_strategy_id: int = int(_get_env("CANARY_CANDIDATE_STRATEGY_ID", "0"))
    canary_months: int = int(_get_env("CANARY_MONTHS", "6"))
    notify_webhook: str = os.getenv("NOTIFY_WEBHOOK", "").strip()
    notify_secret: str = os.getenv("NOTIFY_SECRET", "").strip()
    notify_type: str = os.getenv("NOTIFY_TYPE", "dingding").strip().lower()
    notify_enabled: bool = _get_env("NOTIFY_ENABLED", "false").lower() == "true"
    redis_host: str = _get_env("REDIS_HOST", "localhost")
    redis_port: int = int(_get_env("REDIS_PORT", "6379"))
    redis_db: int = int(_get_env("REDIS_DB", "0"))
    redis_password: str = os.getenv("REDIS_PASSWORD", "").strip()
    async_requested_mode_default: str = _get_env("ASYNC_REQUESTED_MODE_DEFAULT", "auto")
    async_shard_symbol_chunk_size: int = int(_get_env("ASYNC_SHARD_SYMBOL_CHUNK_SIZE", "200"))
    async_worker_lease_seconds: int = int(_get_env("ASYNC_WORKER_LEASE_SECONDS", "60"))
    async_worker_heartbeat_seconds: int = int(_get_env("ASYNC_WORKER_HEARTBEAT_SECONDS", "10"))
    async_max_shard_retries: int = int(_get_env("ASYNC_MAX_SHARD_RETRIES", "3"))
    async_redis_queue_key: str = _get_env("ASYNC_REDIS_QUEUE_KEY", "quant:jobs:queue")
    async_redis_lease_prefix: str = _get_env("ASYNC_REDIS_LEASE_PREFIX", "quant:jobs:lease:")


settings = Settings()
