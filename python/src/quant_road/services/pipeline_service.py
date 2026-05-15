from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime
from typing import Callable

from quant_road.db import batch_execute, fetch_all, fetch_one

logger = logging.getLogger(__name__)


def _format_step_detail(payload) -> str | None:
    if payload is None:
        return None
    if isinstance(payload, str):
        return payload
    try:
        return json.dumps(payload, ensure_ascii=False)
    except TypeError:
        return str(payload)


class PipelineRunner:
    def __init__(self, pipeline_name: str, params: dict, batch_id: int | None = None):
        self.pipeline_name = pipeline_name
        self.params = params
        self.batch_id = batch_id

    def start_batch(self) -> int:
        if self.batch_id is not None:
            return self.batch_id
        batch_key = str(uuid.uuid4())
        insert_sql = """
            INSERT INTO job_run_batch (pipeline_name, status, params, batch_key, start_time)
            VALUES (%s, 'RUNNING', %s::jsonb, %s, NOW())
        """
        batch_execute(insert_sql, [(self.pipeline_name, json.dumps(self.params, ensure_ascii=False), batch_key)])
        row = fetch_one("SELECT id FROM job_run_batch WHERE batch_key = %s", (batch_key,))
        if row is None:
            raise RuntimeError("Failed to create pipeline batch record.")
        self.batch_id = int(row[0])
        logger.info("Pipeline batch started: id=%s name=%s", self.batch_id, self.pipeline_name)
        return self.batch_id

    def run_step(self, step_name: str, fn: Callable[[], object], retry: int = 0) -> None:
        if self.batch_id is None:
            self.start_batch()
        attempts = 0
        max_attempts = max(1, int(retry) + 1)

        while attempts < max_attempts:
            attempts += 1
            self._upsert_step(step_name, status="RUNNING", retries=attempts - 1, error_message=None)
            try:
                payload = fn()
                detail = _format_step_detail(payload)
                self._upsert_step(step_name, status="SUCCESS", retries=attempts - 1, error_message=detail)
                logger.info("Pipeline step success: batch=%s step=%s attempts=%s", self.batch_id, step_name, attempts)
                return
            except Exception as exc:
                error_message = str(exc)
                if attempts < max_attempts:
                    self._upsert_step(step_name, status="RETRYING", retries=attempts, error_message=error_message)
                    logger.warning(
                        "Pipeline step retry: batch=%s step=%s attempt=%s/%s error=%s",
                        self.batch_id,
                        step_name,
                        attempts,
                        max_attempts,
                        error_message,
                    )
                    continue
                self._upsert_step(step_name, status="FAILED", retries=attempts - 1, error_message=error_message)
                logger.exception("Pipeline step failed: batch=%s step=%s", self.batch_id, step_name)
                raise

    def finalize(self, success: bool, error_message: str | None = None) -> None:
        if self.batch_id is None:
            return
        sql = """
            UPDATE job_run_batch
            SET status = %s,
                end_time = NOW(),
                error_message = %s
            WHERE id = %s
        """
        batch_execute(sql, [("SUCCESS" if success else "FAILED", error_message, self.batch_id)])

    def _upsert_step(self, step_name: str, status: str, retries: int, error_message: str | None) -> None:
        if self.batch_id is None:
            raise RuntimeError("Pipeline batch is not started.")
        sql = """
            INSERT INTO job_run_step (batch_id, step_name, status, start_time, end_time, retries, error_message)
            VALUES (
                %s,
                %s,
                %s,
                NOW(),
                CASE WHEN %s IN ('SUCCESS', 'FAILED') THEN NOW() ELSE NULL END,
                %s,
                %s
            )
            ON CONFLICT (batch_id, step_name) DO UPDATE SET
                status = EXCLUDED.status,
                end_time = EXCLUDED.end_time,
                retries = EXCLUDED.retries,
                error_message = EXCLUDED.error_message
        """
        batch_execute(sql, [(self.batch_id, step_name, status, status, retries, error_message)])


def load_batch_params(batch_id: int) -> dict:
    row = fetch_one("SELECT params FROM job_run_batch WHERE id = %s", (batch_id,))
    if row is None or row[0] is None:
        return {}
    if isinstance(row[0], dict):
        return row[0]
    return json.loads(row[0])


def failed_steps(batch_id: int) -> list[str]:
    rows = fetch_all(
        """
        SELECT step_name
        FROM job_run_step
        WHERE batch_id = %s AND status = 'FAILED'
        ORDER BY id
        """,
        (batch_id,),
    )
    return [str(row[0]) for row in rows]


def now_iso() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
