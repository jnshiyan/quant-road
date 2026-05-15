from __future__ import annotations

from typing import Any, Callable

from quant_road.collectors.akshare_collector import sync_stock_basic, sync_stock_daily
from quant_road.config import settings
from quant_road.services.canary_service import evaluate_canary
from quant_road.services.execution_feedback_service import evaluate_execution_feedback
from quant_road.services.market_regime_service import evaluate_and_save_market_status
from quant_road.services.notification_service import notify_daily_summary
from quant_road.services.pipeline_service import PipelineRunner
from quant_road.services.risk_service import update_position_risk
from quant_road.services.strategy_service import run_portfolio, run_strategy
from quant_road.services.valuation_service import sync_index_valuation


def _pick(payload: dict[str, Any] | None, *keys: str, default: Any = None) -> Any:
    source = payload or {}
    for key in keys:
        if key in source and source[key] is not None:
            return source[key]
    return default


def _normalize_payload(payload: dict[str, Any] | None) -> tuple[dict[str, Any], dict[str, Any]]:
    source = payload or {}
    if "plan" in source or "request" in source:
        return dict(source.get("plan") or {}), dict(source.get("request") or {})
    if "execution_plan" in source or "execution_request" in source or "executionPlan" in source or "executionRequest" in source:
        return (
            dict(source.get("execution_plan") or source.get("executionPlan") or {}),
            dict(source.get("execution_request") or source.get("executionRequest") or source.get("request") or {}),
        )
    if "steps" in source or "resolved_symbols" in source or "resolvedSymbols" in source:
        return dict(source), {}
    return {}, dict(source)


def _step_name(step: dict[str, Any]) -> str:
    name = _pick(step, "step_name", "stepName", default="")
    return str(name or "").strip()


def _symbols(plan: dict[str, Any], request: dict[str, Any]) -> list[str]:
    resolved = _pick(plan, "resolved_symbols", "resolvedSymbols")
    if resolved:
        return [str(item).strip() for item in resolved if str(item).strip()]
    raw_symbols = _pick(request, "symbols", default=[])
    return [str(item).strip() for item in (raw_symbols or []) if str(item).strip()]


def _request_context(plan: dict[str, Any], request: dict[str, Any]) -> dict[str, Any]:
    return {
        "symbols": _symbols(plan, request),
        "start_date": _pick(request, "start_date", "startDate"),
        "end_date": _pick(request, "end_date", "endDate"),
        "strategy_start_date": _pick(
            request,
            "strategy_backtest_start_date",
            "strategyBacktestStartDate",
            default=settings.strategy_backtest_start_date,
        ),
        "strategy_id": _pick(request, "strategy_id", "strategyId"),
        "portfolio_total_capital": _pick(request, "portfolio_total_capital", "portfolioTotalCapital"),
        "params_override": _pick(request, "params_override", "paramsOverride"),
        "actor": _pick(request, "actor", default="system"),
        "notify": bool(_pick(request, "notify", default=False)),
        "use_portfolio": bool(_pick(request, "use_portfolio", "usePortfolio", default=False)),
    }


def _build_step_handler(step_name: str, context: dict[str, Any]) -> Callable[[], Any]:
    if step_name == "sync-basic":
        return sync_stock_basic
    if step_name == "sync-daily":
        return lambda: sync_stock_daily(
            symbols=context["symbols"] or None,
            start_date=context["start_date"],
            end_date=context["end_date"],
        )
    if step_name == "sync-valuation":
        return lambda: sync_index_valuation(index_codes=settings.valuation_index_codes or None)
    if step_name == "evaluate-market":
        return lambda: evaluate_and_save_market_status(hold_days=max(1, settings.adaptive_hold_days))
    if step_name == "run-portfolio":
        return lambda: run_portfolio(
            symbols=context["symbols"] or None,
            start_date=context["strategy_start_date"],
            total_capital=context["portfolio_total_capital"],
            actor=context["actor"] or "system",
        )
    if step_name == "run-strategy":
        if context["use_portfolio"]:
            return lambda: run_portfolio(
                symbols=context["symbols"] or None,
                start_date=context["strategy_start_date"],
                total_capital=context["portfolio_total_capital"],
                actor=context["actor"] or "system",
            )
        return lambda: run_strategy(
            symbols=context["symbols"] or None,
            start_date=context["strategy_start_date"],
            strategy_id=context["strategy_id"],
            params_override=context["params_override"],
            actor=context["actor"] or "system",
        )
    if step_name == "evaluate-risk":
        return lambda: update_position_risk(strategy_id=context["strategy_id"])
    if step_name == "evaluate-execution-feedback":
        return lambda: evaluate_execution_feedback(grace_days=1)
    if step_name == "canary-evaluate":
        return lambda: evaluate_canary(
            baseline_strategy_id=settings.canary_baseline_strategy_id,
            candidate_strategy_id=settings.canary_candidate_strategy_id,
            months=max(1, settings.canary_months),
        )
    if step_name == "notify-signals":
        return notify_daily_summary
    raise ValueError(f"Unsupported execution plan step: {step_name}")


def execute_execution_plan(payload: dict[str, Any] | None) -> dict[str, Any]:
    plan, request = _normalize_payload(payload)
    runner = PipelineRunner("execute-task", {"plan": plan, "request": request})
    runner.start_batch()
    executed_steps: list[str] = []
    context = _request_context(plan, request)

    try:
        for raw_step in _pick(plan, "steps", default=[]) or []:
            step = raw_step or {}
            step_name = _step_name(step)
            if not step_name:
                continue
            handler = _build_step_handler(step_name, context)
            runner.run_step(step_name, handler, retry=max(0, settings.pipeline_step_retry))
            executed_steps.append(step_name)
        runner.finalize(success=True)
        return {
            "status": "SUCCESS",
            "batch_id": runner.batch_id,
            "executed_steps": executed_steps,
            "plan_summary": _pick(plan, "plan_summary", "planSummary"),
        }
    except Exception as exc:
        runner.finalize(success=False, error_message=str(exc))
        raise
