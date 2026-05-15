from __future__ import annotations

import argparse
import json
from pathlib import Path

from quant_road.config import settings
from quant_road.logging_utils import configure_logging

DEFAULT_SQL_FILE = Path(__file__).resolve().parents[3] / "sql" / "init.sql"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Quant Road low-frequency quant MVP")
    subparsers = parser.add_subparsers(dest="command", required=True)

    init_db = subparsers.add_parser("init-db", help="Initialize PostgreSQL schema")
    init_db.add_argument("--sql-file", default=str(DEFAULT_SQL_FILE))

    subparsers.add_parser("sync-basic", help="Sync stock basic info")

    sync_daily = subparsers.add_parser("sync-daily", help="Sync stock daily data")
    sync_daily.add_argument("--symbols", help="Comma-separated stock codes")
    sync_daily.add_argument("--start-date", help="YYYYMMDD")
    sync_daily.add_argument("--end-date", help="YYYYMMDD")

    sync_valuation = subparsers.add_parser("sync-valuation", help="Sync index valuation snapshot")
    sync_valuation.add_argument("--index-codes", help="Comma-separated index codes, default uses built-in set")
    sync_valuation.add_argument("--update-date", help="YYYY-MM-DD")

    evaluate_market = subparsers.add_parser("evaluate-market", help="Evaluate and persist market regime")
    evaluate_market.add_argument(
        "--hold-days",
        type=int,
        default=settings.adaptive_hold_days,
        help="Status switch confirmation days",
    )

    run_strategy_parser = subparsers.add_parser("run-strategy", help="Backtest and generate signals")
    run_strategy_parser.add_argument("--symbols", help="Comma-separated stock codes")
    run_strategy_parser.add_argument("--start-date", default=settings.strategy_backtest_start_date, help="YYYY-MM-DD")
    run_strategy_parser.add_argument("--strategy-id", type=int, help="Strategy config id")
    run_strategy_parser.add_argument("--portfolio-capital", type=float, help="Override strategy portfolio capital")
    run_strategy_parser.add_argument("--actor", default="system", help="Run actor for audit trace")

    run_portfolio_parser = subparsers.add_parser("run-portfolio", help="Run all active strategies with allocator")
    run_portfolio_parser.add_argument("--symbols", help="Comma-separated stock codes")
    run_portfolio_parser.add_argument("--start-date", default=settings.strategy_backtest_start_date, help="YYYY-MM-DD")
    run_portfolio_parser.add_argument("--total-capital", type=float, help="Total portfolio capital")
    run_portfolio_parser.add_argument("--actor", default="system", help="Run actor for audit trace")

    plan_async_job = subparsers.add_parser("plan-async-job", help="Persist async quant job and enqueue shards")
    plan_async_job.add_argument("--job-type", required=True, choices=["run-strategy", "run-portfolio"])
    plan_async_job.add_argument("--strategy-id", type=int)
    plan_async_job.add_argument("--strategy-ids", help="Comma-separated strategy ids")
    plan_async_job.add_argument("--symbols", help="Comma-separated stock codes")
    plan_async_job.add_argument("--start-date", default=settings.strategy_backtest_start_date, help="YYYY-MM-DD")
    plan_async_job.add_argument("--end-date", help="YYYY-MM-DD")
    plan_async_job.add_argument("--actor", default="system")

    worker_parser = subparsers.add_parser("run-async-worker", help="Run quant async shard worker")
    worker_parser.add_argument("--worker-id", required=True)
    worker_parser.add_argument("--once", action="store_true")

    execute_task = subparsers.add_parser("execute-task", help="Execute a unified execution plan")
    execute_task.add_argument("--plan-json", required=True, help="Execution plan payload JSON")

    recover_parser = subparsers.add_parser("recover-async-shards", help="Recover expired async shards")
    recover_parser.add_argument("--limit", type=int, default=100)

    evaluate_risk = subparsers.add_parser("evaluate-risk", help="Update positions and evaluate risk")
    evaluate_risk.add_argument("--strategy-id", type=int, help="Strategy config id")
    subparsers.add_parser("notify-signals", help="Send today's signal and risk summary to webhook")

    record_execution = subparsers.add_parser("record-execution", help="Record execution and update position")
    record_execution.add_argument("--stock-code", required=True, help="6-digit stock code")
    record_execution.add_argument("--side", required=True, choices=["BUY", "SELL"], help="Execution side")
    record_execution.add_argument("--quantity", required=True, type=int, help="Execution quantity")
    record_execution.add_argument("--price", required=True, type=float, help="Execution price")
    record_execution.add_argument("--trade-date", required=True, help="YYYY-MM-DD")
    record_execution.add_argument("--strategy-id", type=int, required=True, help="Strategy config id")
    record_execution.add_argument("--signal-id", type=int, help="Optional signal id")
    record_execution.add_argument("--commission", type=float, default=0.0)
    record_execution.add_argument("--tax", type=float, default=0.0)
    record_execution.add_argument("--slippage", type=float, default=0.0)
    record_execution.add_argument("--external-order-id", help="Optional external order id")

    import_executions = subparsers.add_parser("import-executions", help="Import executions from CSV")
    import_executions.add_argument("--file", required=True, help="CSV file path")
    import_executions.add_argument("--strategy-id", type=int, help="Default strategy id when CSV column is empty")

    validate_execution_import = subparsers.add_parser("validate-execution-import", help="Validate execution CSV before import")
    validate_execution_import.add_argument("--file", required=True, help="CSV file path")
    validate_execution_import.add_argument("--strategy-id", type=int, help="Default strategy id when CSV column is empty")

    strategy_capabilities = subparsers.add_parser("strategy-capabilities", help="List supported strategy types")
    strategy_capabilities.add_argument("--format", choices=["json", "text"], default="json")

    monthly_report = subparsers.add_parser("monthly-report", help="Build monthly strategy summary")
    monthly_report.add_argument("--months", type=int, default=6, help="Recent N months")
    monthly_report.add_argument("--output", help="Optional report output path")

    shadow_compare = subparsers.add_parser("shadow-compare", help="Compare baseline and candidate strategy")
    shadow_compare.add_argument("--baseline-strategy-id", type=int, default=1)
    shadow_compare.add_argument("--candidate-strategy-id", type=int, required=True)
    shadow_compare.add_argument("--months", type=int, default=6, help="Recent N months")
    shadow_compare.add_argument("--format", choices=["json", "text"], default="json")
    shadow_compare.add_argument("--output", help="Optional report output path")

    execution_feedback = subparsers.add_parser("evaluate-execution-feedback", help="Evaluate T+1 execution feedback")
    execution_feedback.add_argument("--as-of-date", help="YYYY-MM-DD")
    execution_feedback.add_argument("--grace-days", type=int, default=1, help="Allowed lag days before missed")

    canary_evaluate = subparsers.add_parser("canary-evaluate", help="Evaluate canary performance")
    canary_evaluate.add_argument("--baseline-strategy-id", type=int, default=settings.canary_baseline_strategy_id)
    canary_evaluate.add_argument("--candidate-strategy-id", type=int, required=True)
    canary_evaluate.add_argument("--months", type=int, default=settings.canary_months)

    full_daily = subparsers.add_parser("full-daily", help="Run the full after-market pipeline")
    full_daily.add_argument("--symbols", help="Comma-separated stock codes")
    full_daily.add_argument("--start-date", help="YYYYMMDD for data sync")
    full_daily.add_argument("--strategy-start-date", default=settings.strategy_backtest_start_date, help="YYYY-MM-DD for backtest")
    full_daily.add_argument("--strategy-id", type=int, help="Strategy config id")
    full_daily.add_argument("--end-date", help="YYYYMMDD")
    full_daily.add_argument("--notify", action="store_true", help="Send daily summary after pipeline")
    full_daily.add_argument("--use-portfolio", action="store_true", help="Run all active strategies with allocator")
    full_daily.add_argument("--portfolio-total-capital", type=float, help="Override total capital for allocator")
    full_daily.add_argument("--actor", default="system", help="Run actor for audit trace")
    full_daily.add_argument("--scope-type", help="Scope type for audit trace")
    full_daily.add_argument("--scope-pool-code", help="Scope pool code for audit trace")
    full_daily.add_argument("--resume-batch-id", type=int, help="Resume failed steps for batch id")
    full_daily.add_argument("--step-retry", type=int, help="Retry count for each step")
    return parser


def _parse_symbols(raw: str | None) -> list[str] | None:
    if not raw:
        return None
    return [item.strip() for item in raw.split(",") if item.strip()]


def main() -> None:
    configure_logging()
    parser = build_parser()
    args = parser.parse_args()

    if args.command == "init-db":
        from quant_road.db import run_sql_file

        run_sql_file(args.sql_file)
        return

    if args.command == "sync-basic":
        from quant_road.collectors.akshare_collector import sync_stock_basic

        sync_stock_basic()
        return

    if args.command == "sync-daily":
        from quant_road.collectors.akshare_collector import sync_stock_daily

        sync_stock_daily(
            symbols=_parse_symbols(args.symbols),
            start_date=args.start_date,
            end_date=args.end_date,
        )
        return

    if args.command == "sync-valuation":
        from quant_road.services.valuation_service import sync_index_valuation

        payload = sync_index_valuation(
            index_codes=_parse_symbols(args.index_codes),
            update_date=args.update_date,
        )
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "evaluate-market":
        from quant_road.services.market_regime_service import evaluate_and_save_market_status

        payload = evaluate_and_save_market_status(hold_days=max(1, args.hold_days))
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "run-strategy":
        from quant_road.services.strategy_service import run_strategy

        payload = run_strategy(
            symbols=_parse_symbols(args.symbols),
            start_date=args.start_date,
            strategy_id=args.strategy_id,
            params_override={"portfolio_capital": args.portfolio_capital} if args.portfolio_capital else None,
            actor=args.actor,
        )
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "run-portfolio":
        from quant_road.services.strategy_service import run_portfolio

        payload = run_portfolio(
            symbols=_parse_symbols(args.symbols),
            start_date=args.start_date,
            total_capital=args.total_capital,
            actor=args.actor,
        )
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "plan-async-job":
        from quant_road.services.async_job_service import plan_async_job

        strategy_ids = None
        if args.strategy_ids:
            strategy_ids = [int(item.strip()) for item in args.strategy_ids.split(",") if item.strip()]
        payload = plan_async_job(
            job_type=args.job_type,
            payload={
                "strategy_id": args.strategy_id,
                "strategy_ids": strategy_ids,
                "symbols": _parse_symbols(args.symbols),
                "start_date": args.start_date,
                "end_date": args.end_date,
                "requested_mode": "async",
                "actor": args.actor,
            },
        )
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "run-async-worker":
        from quant_road.services.async_worker_service import run_worker_loop

        payload = run_worker_loop(worker_id=args.worker_id, once=bool(args.once))
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "execute-task":
        from quant_road.services.unified_execution_service import execute_execution_plan

        payload = execute_execution_plan(json.loads(args.plan_json))
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "recover-async-shards":
        from quant_road.services.async_worker_service import recover_expired_shards

        payload = recover_expired_shards(limit=max(1, args.limit))
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "evaluate-risk":
        from quant_road.services.risk_service import update_position_risk

        update_position_risk(strategy_id=args.strategy_id)
        return

    if args.command == "notify-signals":
        from quant_road.services.notification_service import notify_daily_summary

        notify_daily_summary()
        return

    if args.command == "record-execution":
        from quant_road.services.execution_service import apply_execution

        apply_execution(
            stock_code=args.stock_code,
            side=args.side,
            quantity=args.quantity,
            price=args.price,
            trade_date=args.trade_date,
            strategy_id=args.strategy_id,
            signal_id=args.signal_id,
            commission=args.commission,
            tax=args.tax,
            slippage=args.slippage,
            external_order_id=args.external_order_id,
        )
        return

    if args.command == "import-executions":
        from quant_road.services.execution_service import import_executions_from_csv

        payload = import_executions_from_csv(file_path=args.file, default_strategy_id=args.strategy_id)
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "validate-execution-import":
        from quant_road.services.execution_service import validate_execution_import

        payload = validate_execution_import(file_path=args.file, default_strategy_id=args.strategy_id)
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "strategy-capabilities":
        from quant_road.strategies.registry import list_strategy_capabilities

        capabilities = list_strategy_capabilities()
        if args.format == "json":
            print(json.dumps(capabilities, ensure_ascii=False))
        else:
            for item in capabilities:
                print(f"{item['strategy_type']}: {item['description']}")
                print(f"  required={','.join(item['required_params'])}")
                print(f"  optional={','.join(item['optional_params'])}")
        return

    if args.command == "monthly-report":
        from quant_road.services.report_service import build_monthly_summary, write_monthly_summary

        summary = build_monthly_summary(months=args.months)
        target = write_monthly_summary(summary=summary, output_path=args.output)
        print(summary)
        print(f"\nreport_saved_to={target}")
        return

    if args.command == "shadow-compare":
        from quant_road.services.report_service import (
            build_shadow_compare_summary,
            fetch_shadow_compare_payload,
            write_shadow_compare_summary,
        )

        payload = fetch_shadow_compare_payload(
            baseline_strategy_id=args.baseline_strategy_id,
            candidate_strategy_id=args.candidate_strategy_id,
            months=args.months,
        )
        if args.format == "json":
            print(json.dumps(payload, ensure_ascii=False))
        else:
            print(build_shadow_compare_summary(payload))
        if args.output:
            summary = build_shadow_compare_summary(payload)
            target = write_shadow_compare_summary(
                summary=summary,
                baseline_strategy_id=args.baseline_strategy_id,
                candidate_strategy_id=args.candidate_strategy_id,
                output_path=args.output,
            )
            print(f"\nreport_saved_to={target}")
        return

    if args.command == "evaluate-execution-feedback":
        from quant_road.services.execution_feedback_service import evaluate_execution_feedback

        payload = evaluate_execution_feedback(as_of_date=args.as_of_date, grace_days=max(0, args.grace_days))
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "canary-evaluate":
        from quant_road.services.canary_service import evaluate_canary

        payload = evaluate_canary(
            baseline_strategy_id=args.baseline_strategy_id,
            candidate_strategy_id=args.candidate_strategy_id,
            months=max(1, args.months),
        )
        print(json.dumps(payload, ensure_ascii=False))
        return

    if args.command == "full-daily":
        from quant_road.collectors.akshare_collector import sync_stock_basic, sync_stock_daily
        from quant_road.services.canary_service import evaluate_canary
        from quant_road.services.execution_feedback_service import evaluate_execution_feedback
        from quant_road.services.market_regime_service import evaluate_and_save_market_status
        from quant_road.services.notification_service import notify_daily_summary
        from quant_road.services.pipeline_service import PipelineRunner, failed_steps, load_batch_params
        from quant_road.services.risk_service import update_position_risk
        from quant_road.services.strategy_service import run_portfolio, run_strategy
        from quant_road.services.valuation_service import sync_index_valuation

        resolved_params = {
            "symbols": _parse_symbols(args.symbols),
            "start_date": args.start_date,
            "end_date": args.end_date,
            "index_codes": settings.valuation_index_codes or None,
            "strategy_start_date": args.strategy_start_date,
            "strategy_id": args.strategy_id,
            "use_portfolio": bool(args.use_portfolio),
            "portfolio_total_capital": args.portfolio_total_capital,
            "actor": args.actor,
            "notify": bool(args.notify),
            "scope_type": args.scope_type,
            "scope_pool_code": args.scope_pool_code,
        }
        resume_failed_only: set[str] = set()
        if args.resume_batch_id is not None:
            history_params = load_batch_params(args.resume_batch_id)
            for key in resolved_params.keys():
                if resolved_params[key] in (None, False):
                    resolved_params[key] = history_params.get(key, resolved_params[key])
            resume_failed_only = set(failed_steps(args.resume_batch_id))

        runner = PipelineRunner("full-daily", resolved_params, batch_id=args.resume_batch_id)
        step_retry = settings.pipeline_step_retry if args.step_retry is None else max(0, args.step_retry)
        runner.start_batch()

        try:
            if not resume_failed_only or "sync-basic" in resume_failed_only:
                runner.run_step("sync-basic", sync_stock_basic, retry=step_retry)
            if not resume_failed_only or "sync-daily" in resume_failed_only:
                runner.run_step(
                    "sync-daily",
                    lambda: sync_stock_daily(
                        symbols=resolved_params["symbols"],
                        start_date=resolved_params["start_date"],
                        end_date=resolved_params["end_date"],
                    ),
                    retry=step_retry,
                )
            if not resume_failed_only or "sync-valuation" in resume_failed_only:
                runner.run_step(
                    "sync-valuation",
                    lambda: sync_index_valuation(index_codes=resolved_params["index_codes"]),
                    retry=step_retry,
                )
            if not resume_failed_only or "evaluate-market" in resume_failed_only:
                runner.run_step(
                    "evaluate-market",
                    lambda: evaluate_and_save_market_status(hold_days=max(1, settings.adaptive_hold_days)),
                    retry=step_retry,
                )
            if not resume_failed_only or "run-strategy" in resume_failed_only:
                if resolved_params["use_portfolio"]:
                    runner.run_step(
                        "run-strategy",
                        lambda: run_portfolio(
                            symbols=resolved_params["symbols"],
                            start_date=resolved_params["strategy_start_date"] or settings.strategy_backtest_start_date,
                            total_capital=resolved_params["portfolio_total_capital"],
                            actor=resolved_params["actor"] or "system",
                        ),
                        retry=step_retry,
                    )
                else:
                    runner.run_step(
                        "run-strategy",
                        lambda: run_strategy(
                            symbols=resolved_params["symbols"],
                            start_date=resolved_params["strategy_start_date"] or settings.strategy_backtest_start_date,
                            strategy_id=resolved_params["strategy_id"],
                            actor=resolved_params["actor"] or "system",
                        ),
                        retry=step_retry,
                    )
            if not resume_failed_only or "evaluate-risk" in resume_failed_only:
                runner.run_step(
                    "evaluate-risk",
                    lambda: update_position_risk(strategy_id=resolved_params["strategy_id"]),
                    retry=step_retry,
                )
            if not resume_failed_only or "evaluate-execution-feedback" in resume_failed_only:
                runner.run_step(
                    "evaluate-execution-feedback",
                    lambda: evaluate_execution_feedback(grace_days=1),
                    retry=step_retry,
                )
            if settings.canary_enabled and settings.canary_candidate_strategy_id > 0 and (
                not resume_failed_only or "canary-evaluate" in resume_failed_only
            ):
                runner.run_step(
                    "canary-evaluate",
                    lambda: evaluate_canary(
                        baseline_strategy_id=settings.canary_baseline_strategy_id,
                        candidate_strategy_id=settings.canary_candidate_strategy_id,
                        months=max(1, settings.canary_months),
                    ),
                    retry=step_retry,
                )
            if resolved_params["notify"] and (not resume_failed_only or "notify-signals" in resume_failed_only):
                runner.run_step("notify-signals", notify_daily_summary, retry=step_retry)
            runner.finalize(success=True)
        except Exception as exc:
            runner.finalize(success=False, error_message=str(exc))
            raise
        return

    parser.error(f"Unsupported command: {args.command}")
