# Quant Road Runbook

## 1. Daily Checklist

1. Confirm `fullDailyAsync` submission has fired in RuoYi as scheduled.
2. Check latest async job in `quant_async_job` and confirm `status` moves from `QUEUED/RUNNING` to terminal state.
3. Check latest batch in `job_run_batch` and step statuses in `job_run_step`.
3. Review `trade_signal` count for today.
4. Review `position.loss_warning = 1` rows.
5. Review latest market regime:
   - `SELECT trade_date, status, raw_status, up_ratio FROM market_status ORDER BY trade_date DESC LIMIT 1;`
6. Review latest valuation snapshots:
   - `SELECT index_code, pe, pe_percentile, update_date FROM index_valuation ORDER BY update_date DESC, index_code LIMIT 20;`
7. Review strategy switch audit:
   - `SELECT strategy_id, market_status, decision, actor, create_time FROM strategy_switch_audit ORDER BY create_time DESC LIMIT 20;`
8. Review T+1 execution feedback:
   - `SELECT status, COUNT(1) FROM signal_execution_feedback GROUP BY status;`
9. Review latest canary snapshot:
   - `SELECT run_date, baseline_strategy_id, candidate_strategy_id, recommendation FROM canary_run_log ORDER BY run_date DESC LIMIT 1;`

## 1.1 One-time Bootstrap

1. For a fresh database, initialize the full mainline schema/data in one command:
   - `python scripts/init-ruoyi-fresh.py --host localhost --port 5432 --user postgres --password 123456 --database db-quant`
2. For an existing database that only needs quant menus/jobs refreshed:
   - `python scripts/apply-quant-bootstrap.py --host localhost --port 5432 --user postgres --password 123456 --database db-quant`
3. Alternative manual SQL execution for fresh initialization:
   - `psql -h localhost -U postgres -d db-quant -f sql/ruoyi_pg_init.sql`
   - `psql -h localhost -U postgres -d db-quant -f sql/init.sql`
   - `psql -h localhost -U postgres -d db-quant -f sql/ruoyi_quant_menu.sql`
   - `psql -h localhost -U postgres -d db-quant -f sql/ruoyi_quant_jobs.sql`
4. Re-login admin user and confirm sidebar contains:
   - 当前实现：`量化分析 -> 量化看板`
   - 当前实现：`量化分析 -> 影子对比`
   - 当前实现：`量化分析 -> 调度中心`
   - 当前实现：`量化分析 -> 运维中心`
   - 当前实现：`量化分析 -> 执行回写`
   - 当前实现默认不展示：`首页`、`monitor/*`、`tool/*`、`若依官网`
   - 若后续落地 `docs/quant-system/13-量化模块页面改版实施方案（研发版）.md`，菜单会重组为 `量化运营 / 策略治理`
5. Start the system after initialization:
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restart-all-services.ps1`
   - UI entry: `http://localhost:8081`
   - API entry: `http://localhost:8080`
6. Ensure Python schema migration is applied (idempotent):
   - `cd python`
   - `PYTHONPATH=src python -m quant_road init-db`

Execution import operation notes:

1. 当前 UI 路径 `量化分析 -> 执行回写` supports both:
   - browser upload import (`POST /quant/jobs/importExecutionsUpload`)
   - server-path import (`POST /quant/jobs/importExecutions?file=...`)
2. Use built-in template download:
   - `/templates/execution-import-template.csv`

## 2. Failure Handling

### 2.1 Full Pipeline Failed

1. Query failed steps:
   - `SELECT step_name, error_message FROM job_run_step WHERE batch_id = ? AND status = 'FAILED';`
2. Query failed async shards:
   - `SELECT shard_key, status, last_error FROM quant_async_job_shard WHERE job_id = ? ORDER BY shard_index;`
3. Retry failed async shards:
   - `POST /quant/jobs/retryFailedShards/{jobId}`
   - or in UI: `量化分析 -> 任务中心 -> 重试失败分片`
4. Retry:
   - `cd python`
   - `PYTHONPATH=src python -m quant_road full-daily --resume-batch-id <batch_id> --step-retry 2`
5. If failure is in adaptive steps:
   - valuation only retry: `PYTHONPATH=src python -m quant_road sync-valuation`
   - market status only retry: `PYTHONPATH=src python -m quant_road evaluate-market --hold-days 2`
   - execution feedback retry: `PYTHONPATH=src python -m quant_road evaluate-execution-feedback --grace-days 1`
   - canary retry: `PYTHONPATH=src python -m quant_road canary-evaluate --baseline-strategy-id 1 --candidate-strategy-id 2 --months 6`
   - if valuation source mismatch occurs (AKShare version lacks `index_value_name_funddb`), service auto-falls back to `stock_index_pe_lg/stock_index_pb_lg`.
6. Market status fallback behavior:
   - if `evaluate-market` cannot load upstream data, service will reuse latest persisted status and write today's snapshot;
   - check `market_status.remark` for `fallback_last_status` to confirm fallback path was used;
   - if no historical `market_status` exists, command fails and should be retried after data source recovery.

### 2.2 Python Command Not Found

1. Verify `quant-road.python.executable` in:
   - `ruoyi-admin/src/main/resources/application.yml`
2. Verify Python env in server path and dependencies:
   - `powershell scripts/install_python_deps.ps1`

### 2.3 Missing Module `quant_road`

1. Verify `quant-road.python.workdir` equals `../python`.
2. Verify `PYTHONPATH` includes `src` in Java runner service.

## 3. Weekly and Monthly Tasks

1. Weekly:
   - Review `strategy_run_log` and top invalid remarks.
2. Monthly:
   - Generate report:
   - `cd python`
   - `PYTHONPATH=src python -m quant_road monthly-report --months 6`
   - Compare baseline/candidate:
   - `PYTHONPATH=src python -m quant_road shadow-compare --baseline-strategy-id 1 --candidate-strategy-id 2 --months 6 --format text`
   - Or use current RuoYi UI page: `量化分析 -> 影子对比` (`/quant/shadow`)
   - Archive generated report in `docs/reports/`.

## 3.1 Menu Naming Note

Current operational documents still use the shipped menu path:

1. `量化分析 -> 量化看板 / 影子对比 / 任务中心 / 执行回写`

If the page restructuring in `docs/quant-system/13-量化模块页面改版实施方案（研发版）.md` is implemented later, the target grouping will become:

1. `量化运营 -> 量化看板 / 任务中心 / 执行回写`
2. `策略治理 -> 复盘分析 / 影子对比`

## 4. Regression Checklist (Before Delivery)

### 4.0 Worktree 收口到 Main

如果开发是在某个 `worktree` 里完成的，代码不会自动进入根工作区的 `main`。标准收口流程如下：

1. 在 feature worktree 内完成提交。
2. 显式把该分支内容合入 `main`：
   - 可以用 `git merge`
   - 也可以用 `git cherry-pick`
   - 不要假设 worktree 会自动同步到 `main`
3. 把根工作区切到 `main`：
   - `git checkout main`
4. 在根工作区执行收口检查：
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-main-for-push.ps1`
5. 需要自动化验证时执行：
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-main-for-push.ps1 -RunVerification`
6. 需要直接推送时执行：
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-main-for-push.ps1 -RunVerification -Push`

说明：

1. `prepare-main-for-push.ps1` 不会替你自动合并 feature 分支。
2. 该脚本会检查根工作区是否在 `main`、是否干净、以及是否还有本地分支未合入 `main`。
3. 只有这些检查通过后，才适合把根工作区当成“可上传主线”。

One-command full regression:

1. `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/full-regression.ps1`

This command will run Java, Python, frontend build, Playwright, and API smoke checks end-to-end.

1. Java compile/package:
   - `mvn -pl ruoyi-admin -am -DskipTests compile`
   - `mvn -pl ruoyi-admin -am -DskipTests package`
2. Python unit tests:
   - `cd python`
   - `PYTHONPATH=src python -m unittest discover -s tests -p "test_*.py"`
3. Frontend production build:
   - `cd ruoyi-ui`
   - `npm run build:prod`
4. Browser E2E smoke (Playwright):
   - headless: `npm run smoke:e2e`
   - headed: `npm run smoke:e2e:headed`
5. Backend API smoke:
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/api-smoke.ps1`
6. Async benchmark:
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/quant-async-benchmark.ps1`
7. Execution reconciliation benchmark:
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/execution-reconciliation-benchmark.ps1`

### 4.1 Notes

1. 当前默认回归主链为 `ruoyi-admin + ruoyi-ui + python`；不要把 `admin/` 作为默认验证入口。
2. `admin/` 仅用于历史兼容排查，默认监听 `18080`。
3. `ruoyi-generator/` 已移出主链构建；若要恢复代码生成能力，需要单独重新接入。
4. Playwright smoke 当前只覆盖主链页面，不再覆盖 `monitor/*` 与 `tool/*`。
5. If `mvn ... package` fails with jar rename/lock error, check whether `ruoyi-admin/target/ruoyi-admin.jar` is being used by a running process and stop that process before retrying.
6. Do not use `-Dspring-boot.repackage.skip=true` as a fallback for `ruoyi-admin` packaging. That path leaves a plain non-executable jar on disk, which later causes missing-class failures under `java -jar`.
7. A valid packaged admin jar must contain `BOOT-INF/` entries. The regression script now asserts this automatically.
8. Manual Python CLI commands should also export `PYTHONPATH=src`; Java already injects this for subprocess execution, but an interactive shell usually does not.
9. Playwright reports are written to:
   - `runtime-logs/playwright-smoke-report.json`
   - `runtime-logs/playwright-pages/`
10. Full regression report is written to:
   - `runtime-logs/full-regression-report.json`
