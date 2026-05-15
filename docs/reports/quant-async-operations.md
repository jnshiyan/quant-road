# Quant Async Operations

## Overview

The quant async lane now uses:

1. PostgreSQL for `quant_async_job`, shard, attempt, summary, and result persistence.
2. Redis for shard queueing and lease coordination.
3. Python worker commands:
   - `PYTHONPATH=src python -m quant_road run-async-worker --worker-id worker-01 --once`
   - `PYTHONPATH=src python -m quant_road recover-async-shards --limit 100`

## Core Checks

1. Submit a job and record the returned `jobId`.
2. Poll `GET /quant/jobs/status/{jobId}` until the job reaches a terminal state.
3. Inspect:
   - `GET /quant/data/asyncJobShards?jobId={jobId}`
   - `GET /quant/data/asyncJobResults?jobId={jobId}&limit=200`
4. If a worker dies mid-run:
   - run `PYTHONPATH=src python -m quant_road recover-async-shards --limit 100`
   - or use `POST /quant/jobs/retryFailedShards/{jobId}`

## Verification Snapshot

1. `scripts/api-smoke.ps1` passed against `http://localhost:8080` on the current mainline API checklist.
2. `scripts/quant-async-benchmark.ps1` submitted a real async job and observed persisted `QUEUED` status with `plannedShardCount = 56`.
3. A focused real planner/worker closure also passed:
   - submit `POST /quant/jobs/runStrategy` with `requestedMode=async`, `strategyId=1`, `symbols=["000001"]`
   - run `PYTHONPATH=src python -m quant_road run-async-worker --worker-id verifier --once`
   - verify `GET /quant/jobs/status/{jobId}` returned `SUCCESS`
   - verify `GET /quant/data/asyncJobResults?jobId={jobId}` returned the persisted result row

## Quartz Notes

`sql/ruoyi_quant_jobs.sql` now points the daily scheduler at `quantRoadTask.fullDailyAsync()`, which submits the async quant job contract instead of blocking inside the scheduler thread.
