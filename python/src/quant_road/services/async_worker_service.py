from __future__ import annotations

import json
from datetime import UTC, datetime

from quant_road.config import settings
from quant_road.db import batch_execute, fetch_one
from quant_road.services.async_job_service import get_redis_client
from quant_road.services.strategy_service import run_strategy_batch
from quant_road.services.unified_execution_service import execute_execution_plan


def _lease_key(shard_key: str) -> str:
    return f"{settings.async_redis_lease_prefix}{shard_key}"


def _root_cause_message(ex: Exception) -> str:
    current: BaseException = ex
    while current.__cause__ is not None:
        current = current.__cause__
    message = str(current).strip()
    return message or current.__class__.__name__


def _refresh_job_status(job_id: int, error_message: str | None = None) -> str:
    counts = fetch_one(
        """
        SELECT
            COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0),
            COUNT(1)
        FROM quant_async_job_shard
        WHERE job_id = %s
        """,
        (job_id,),
    )
    completed_count = 0 if counts is None else int(counts[0] or 0)
    failed_count = 0 if counts is None else int(counts[1] or 0)
    planned_count = 0 if counts is None else int(counts[2] or 0)

    if failed_count > 0 and completed_count == 0 and failed_count >= max(1, planned_count):
        final_status = "FAILED"
    elif completed_count + failed_count >= max(1, planned_count):
        final_status = "PARTIAL_FAILED" if failed_count > 0 else "SUCCESS"
    else:
        final_status = "RUNNING"

    batch_execute(
        """
        UPDATE quant_async_job
        SET completed_shard_count = %s,
            failed_shard_count = %s,
            status = %s,
            error_message = %s,
            start_time = COALESCE(start_time, NOW()),
            end_time = CASE WHEN %s IN ('SUCCESS', 'FAILED', 'PARTIAL_FAILED', 'CANCELLED') THEN NOW() ELSE end_time END
        WHERE id = %s
        """,
        [(
            completed_count,
            failed_count,
            final_status,
            error_message if failed_count > 0 else None,
            final_status,
            job_id,
        )],
    )
    return final_status


def persist_shard_outputs(job_id: int, strategy_id: int, rows: list[dict]) -> None:
    prepared_rows = []
    for stock_code, row in rows:
        prepared_rows.append(
            (
                job_id,
                strategy_id,
                stock_code,
                row.get("signal"),
                row.get("annual_return"),
                row.get("max_drawdown"),
                row.get("win_rate"),
                row.get("total_profit"),
                int(row.get("trade_count") or 0),
                float(row.get("total_cost") or 0),
                1 if row.get("is_invalid") else 0,
                row.get("remark"),
                json.dumps(row, ensure_ascii=False),
            )
        )
    batch_execute(
        """
        INSERT INTO quant_async_job_result (
            job_id,
            strategy_id,
            stock_code,
            signal_type,
            annual_return,
            max_drawdown,
            win_rate,
            total_profit,
            trade_count,
            total_cost,
            is_invalid,
            remark,
            payload
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb)
        ON CONFLICT (job_id, strategy_id, stock_code) DO UPDATE
        SET signal_type = EXCLUDED.signal_type,
            annual_return = EXCLUDED.annual_return,
            max_drawdown = EXCLUDED.max_drawdown,
            win_rate = EXCLUDED.win_rate,
            total_profit = EXCLUDED.total_profit,
            trade_count = EXCLUDED.trade_count,
            total_cost = EXCLUDED.total_cost,
            is_invalid = EXCLUDED.is_invalid,
            remark = EXCLUDED.remark,
            payload = EXCLUDED.payload
        """,
        prepared_rows,
    )


def run_worker_once(worker_id: str, redis_client=None) -> dict:
    client = redis_client or get_redis_client()
    shard_key = client.lpop(settings.async_redis_queue_key)
    if not shard_key:
        return {"status": "IDLE", "worker_id": worker_id}

    shard_row = fetch_one(
        """
        SELECT id, job_id, strategy_id, shard_index, payload::text, attempt_count
        FROM quant_async_job_shard
        WHERE shard_key = %s
        """,
        (shard_key,),
    )
    if shard_row is None:
        client.delete(_lease_key(shard_key))
        return {"status": "MISSING", "worker_id": worker_id, "shard_key": shard_key}

    shard_id = int(shard_row[0])
    job_id = int(shard_row[1])
    strategy_id = None if shard_row[2] is None else int(shard_row[2])
    payload = json.loads(shard_row[4])
    next_attempt = int(shard_row[5] or 0) + 1
    lease_seconds = max(1, settings.async_worker_lease_seconds)

    batch_execute(
        """
        UPDATE quant_async_job_shard
        SET status = %s,
            attempt_count = %s,
            lease_owner = %s,
            lease_expires_at = NOW() + (%s || ' seconds')::interval,
            heartbeat_at = NOW(),
            start_time = COALESCE(start_time, NOW())
        WHERE id = %s
        """,
        [("RUNNING", next_attempt, worker_id, lease_seconds, shard_id)],
    )
    batch_execute(
        """
        INSERT INTO quant_async_job_attempt (
            job_id,
            shard_id,
            attempt_no,
            worker_id,
            status,
            start_time,
            create_time
        )
        VALUES (%s, %s, %s, %s, %s, NOW(), NOW())
        ON CONFLICT (shard_id, attempt_no) DO UPDATE
        SET worker_id = EXCLUDED.worker_id,
            status = EXCLUDED.status,
            start_time = EXCLUDED.start_time
        """,
        [(job_id, shard_id, next_attempt, worker_id, "RUNNING")],
    )
    client.setex(_lease_key(shard_key), lease_seconds, worker_id)

    try:
        if payload.get("job_type") == "execute-plan" or payload.get("jobType") == "execute-plan" or payload.get("execution_plan") is not None or payload.get("executionPlan") is not None:
            results = execute_execution_plan(payload)
        else:
            results = run_strategy_batch(
                symbols=payload.get("symbols") or [],
                start_date=payload.get("start_date") or settings.strategy_backtest_start_date,
                strategy_id=strategy_id,
                actor=payload.get("actor") or worker_id,
                end_date=payload.get("end_date"),
                params_override=payload.get("params_override"),
                trigger_source="async-worker",
            )
            persist_shard_outputs(job_id=job_id, strategy_id=strategy_id, rows=list(results.items()))
        batch_execute(
            """
            UPDATE quant_async_job_shard
            SET status = %s,
                lease_owner = NULL,
                lease_expires_at = NULL,
                heartbeat_at = NOW(),
                end_time = NOW(),
                last_error = NULL
            WHERE id = %s
            """,
            [("SUCCESS", shard_id)],
        )
        batch_execute(
            """
            UPDATE quant_async_job_attempt
            SET status = %s,
                error_class = NULL,
                error_message = NULL,
                end_time = NOW()
            WHERE shard_id = %s
              AND attempt_no = %s
            """,
            [("SUCCESS", shard_id, next_attempt)],
        )
        final_status = _refresh_job_status(job_id=job_id)
        return {
            "status": final_status,
            "job_id": job_id,
            "shard_id": shard_id,
            "worker_id": worker_id,
            "result_count": len(results) if hasattr(results, "__len__") else 0,
        }
    except Exception as ex:
        error_message = _root_cause_message(ex)
        batch_execute(
            """
            UPDATE quant_async_job_shard
            SET status = %s,
                lease_owner = NULL,
                lease_expires_at = NULL,
                heartbeat_at = NOW(),
                end_time = NOW(),
                last_error = %s
            WHERE id = %s
            """,
            [("FAILED", error_message, shard_id)],
        )
        batch_execute(
            """
            UPDATE quant_async_job_attempt
            SET status = %s,
                error_class = %s,
                error_message = %s,
                end_time = NOW()
            WHERE shard_id = %s
              AND attempt_no = %s
            """,
            [("FAILED", ex.__class__.__name__, error_message, shard_id, next_attempt)],
        )
        final_status = _refresh_job_status(job_id=job_id, error_message=error_message)
        return {
            "status": final_status,
            "job_id": job_id,
            "shard_id": shard_id,
            "worker_id": worker_id,
            "error": error_message,
        }
    finally:
        client.delete(_lease_key(shard_key))


def run_worker_loop(worker_id: str, once: bool = False, redis_client=None) -> dict:
    last_payload = {"status": "IDLE", "worker_id": worker_id}
    client = redis_client or get_redis_client()
    while True:
        last_payload = run_worker_once(worker_id=worker_id, redis_client=client)
        if once or last_payload["status"] in {"IDLE", "MISSING"}:
            return last_payload


def recover_expired_shards(limit: int = 100, redis_client=None) -> dict:
    client = redis_client or get_redis_client()
    rows = []
    for _ in range(max(1, limit)):
        row = fetch_one(
            """
            SELECT id, shard_key
            FROM quant_async_job_shard
            WHERE status = 'RUNNING'
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at < NOW()
            ORDER BY lease_expires_at ASC, id ASC
            LIMIT 1
            """
        )
        if row is None:
            break
        shard_id = int(row[0])
        shard_key = str(row[1])
        batch_execute(
            """
            UPDATE quant_async_job_shard
            SET status = 'QUEUED',
                lease_owner = NULL,
                lease_expires_at = NULL
            WHERE id = %s
            """,
            [(shard_id,)],
        )
        client.rpush(settings.async_redis_queue_key, shard_key)
        rows.append({"shard_id": shard_id, "shard_key": shard_key, "recovered_at": datetime.now(UTC).isoformat()})
    return {"status": "OK", "recovered": rows, "count": len(rows)}
