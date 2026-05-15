from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import UTC, datetime
from uuid import uuid4

from quant_road.config import settings
from quant_road.db import batch_execute, fetch_one
from quant_road.services.strategy_service import _load_active_strategies, _resolve_symbols

try:
    import redis
except ImportError:  # pragma: no cover - guarded by dependency management
    redis = None


@dataclass(frozen=True)
class AsyncShardPlan:
    shard_key: str
    shard_index: int
    strategy_id: int
    symbols: list[str]
    payload: dict


def get_redis_client():
    if redis is None:
        raise RuntimeError("redis package is required for async job queueing")
    return redis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        db=settings.redis_db,
        password=settings.redis_password or None,
        decode_responses=True,
    )


def build_strategy_symbol_shards(
    strategy_ids: list[int],
    symbols: list[str],
    chunk_size: int,
    payload: dict,
    job_key: str,
) -> list[AsyncShardPlan]:
    shards: list[AsyncShardPlan] = []
    safe_chunk_size = max(1, chunk_size)
    shard_index = 0
    for strategy_id in strategy_ids:
        for start in range(0, len(symbols), safe_chunk_size):
            chunk = symbols[start:start + safe_chunk_size]
            shard_payload = dict(payload)
            shard_payload["strategy_id"] = strategy_id
            shard_payload["symbols"] = chunk
            shards.append(
                AsyncShardPlan(
                    shard_key=f"{job_key}-shard-{shard_index}",
                    shard_index=shard_index,
                    strategy_id=strategy_id,
                    symbols=chunk,
                    payload=shard_payload,
                )
            )
            shard_index += 1
    return shards


def push_shards_to_queue(shards: list[dict], redis_client=None) -> None:
    client = redis_client or get_redis_client()
    for shard in shards:
        client.rpush(settings.async_redis_queue_key, shard["shard_key"])


def _resolve_strategy_ids(job_type: str, payload: dict) -> list[int]:
    explicit = payload.get("strategy_ids")
    if explicit:
        return [int(item) for item in explicit]
    if payload.get("strategy_id") is not None:
        return [int(payload["strategy_id"])]
    if job_type == "run-portfolio":
        return [int(item["strategy_id"]) for item in _load_active_strategies()]
    return [settings.strategy_id]


def plan_async_job(
    job_type: str,
    payload: dict,
    chunk_size: int | None = None,
    redis_client=None,
) -> dict:
    now = datetime.now(UTC).isoformat()
    job_key = f"quant-job-{uuid4().hex[:16]}"
    actor = str(payload.get("actor") or "system").strip() or "system"
    normalized_payload = {
        "job_type": job_type,
        "start_date": payload.get("start_date") or payload.get("strategy_start_date") or settings.strategy_backtest_start_date,
        "end_date": payload.get("end_date"),
        "requested_mode": payload.get("requested_mode") or settings.async_requested_mode_default,
        "actor": actor,
        "params_override": payload.get("params_override") or {},
    }
    symbols = _resolve_symbols(payload.get("symbols"))
    strategy_ids = _resolve_strategy_ids(job_type, payload)
    shards = build_strategy_symbol_shards(
        strategy_ids=strategy_ids,
        symbols=symbols,
        chunk_size=chunk_size or settings.async_shard_symbol_chunk_size,
        payload=normalized_payload,
        job_key=job_key,
    )
    estimate_payload = {
        "strategyCount": len(strategy_ids),
        "symbolCount": len(symbols),
        "estimatedAt": now,
    }
    job_id = fetch_one(
        """
        INSERT INTO quant_async_job (
            job_key,
            job_type,
            requested_mode,
            resolved_mode,
            status,
            actor,
            request_payload,
            normalized_payload,
            cost_estimate,
            planned_shard_count,
            completed_shard_count,
            failed_shard_count
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s::jsonb, %s::jsonb, %s::jsonb, %s, 0, 0)
        RETURNING id
        """,
        (
            job_key,
            job_type,
            normalized_payload["requested_mode"],
            "async",
            "QUEUED",
            actor,
            json.dumps(payload, ensure_ascii=False),
            json.dumps(normalized_payload, ensure_ascii=False),
            json.dumps(estimate_payload, ensure_ascii=False),
            len(shards),
        ),
    )
    job_id_value = int(job_id[0])
    batch_execute(
        """
        INSERT INTO quant_async_job_summary (
            job_id,
            total_symbols,
            processed_symbols,
            skipped_symbols,
            total_strategies,
            signal_count,
            invalid_count,
            runtime_ms,
            payload,
            update_time
        )
        VALUES (%s, %s, 0, 0, %s, 0, 0, 0, %s::jsonb, NOW())
        ON CONFLICT (job_id) DO UPDATE
        SET total_symbols = EXCLUDED.total_symbols,
            total_strategies = EXCLUDED.total_strategies,
            payload = EXCLUDED.payload,
            update_time = NOW()
        """,
        [(
            job_id_value,
            len(symbols),
            len(strategy_ids),
            json.dumps({"jobKey": job_key, "createdAt": now}, ensure_ascii=False),
        )],
    )
    batch_execute(
        """
        INSERT INTO quant_async_job_shard (
            job_id,
            shard_key,
            strategy_id,
            shard_index,
            status,
            symbol_count,
            payload,
            attempt_count,
            create_time
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s::jsonb, 0, NOW())
        """,
        [
            (
                job_id_value,
                shard.shard_key,
                shard.strategy_id,
                shard.shard_index,
                "QUEUED",
                len(shard.symbols),
                json.dumps(shard.payload, ensure_ascii=False),
            )
            for shard in shards
        ],
    )
    batch_execute(
        """
        UPDATE quant_async_job
        SET planned_shard_count = %s,
            status = %s
        WHERE id = %s
        """,
        [(len(shards), "QUEUED", job_id_value)],
    )
    shard_rows = [
        {
            "shard_key": shard.shard_key,
            "shard_index": shard.shard_index,
            "strategy_id": shard.strategy_id,
            "payload": shard.payload,
        }
        for shard in shards
    ]
    push_shards_to_queue(shard_rows, redis_client=redis_client)
    return {
        "job_id": job_id_value,
        "job_key": job_key,
        "job_type": job_type,
        "planned_shard_count": len(shards),
        "shards": shard_rows,
        "status": "QUEUED",
    }
