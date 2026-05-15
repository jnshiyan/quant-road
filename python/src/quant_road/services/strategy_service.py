from __future__ import annotations

import json
import logging
import math
from typing import Any

from quant_road.config import settings
from quant_road.db import batch_execute, fetch_all, fetch_one
from quant_road.services.market_data_batch_service import load_stock_history_batch
from quant_road.services.market_regime_service import fetch_latest_market_status
from quant_road.services.market_service import today_iso
from quant_road.services.strategy_execution_service import execute_strategy_frames
from quant_road.strategies.registry import resolve_strategy_executor

logger = logging.getLogger(__name__)
_MARKET_STATUS_UNSET = object()


def _resolve_symbols(symbols: list[str] | None) -> list[str]:
    if symbols:
        return [str(symbol).zfill(6) for symbol in symbols]
    rows = fetch_all(
        """
        SELECT stock_code
        FROM stock_basic
        WHERE COALESCE(is_st, 0) = 0
        ORDER BY stock_code
        """
    )
    if rows:
        return [str(row[0]).zfill(6) for row in rows]
    return [str(symbol).zfill(6) for symbol in settings.target_stocks]


def _stock_name(stock_code: str) -> str | None:
    row = fetch_one("SELECT stock_name FROM stock_basic WHERE stock_code = %s", (stock_code,))
    return None if row is None else row[0]


def _load_strategy_config(strategy_id: int) -> tuple[str, dict]:
    row = fetch_one(
        """
        SELECT strategy_type, params
        FROM strategy_config
        WHERE id = %s
        """,
        (strategy_id,),
    )
    if row is None:
        return "MA", {
            "stop_loss_rate": settings.stop_loss_rate,
            "max_single_position_pct": settings.max_single_position_pct,
            "max_total_position_pct": settings.max_total_position_pct,
            "portfolio_capital": 100000,
        }
    strategy_type = "MA" if row[0] is None else str(row[0]).strip().upper() or "MA"
    if row[1] is None:
        params = {}
    else:
        params = dict(row[1]) if isinstance(row[1], dict) else json.loads(row[1])
    params.setdefault("stop_loss_rate", settings.stop_loss_rate)
    params.setdefault("max_single_position_pct", settings.max_single_position_pct)
    params.setdefault("max_total_position_pct", settings.max_total_position_pct)
    params.setdefault("portfolio_capital", 100000)
    params.setdefault("commission_rate", settings.commission_rate)
    params.setdefault("slippage_rate", settings.slippage_rate)
    params.setdefault("stamp_duty_rate", settings.stamp_duty_rate)
    return strategy_type, params


def _save_trade_signal(stock_code: str, signal_type: str, price: float, strategy_id: int) -> None:
    sql = """
        INSERT INTO trade_signal (
            stock_code, stock_name, signal_type, suggest_price, signal_date, strategy_id
        )
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (stock_code, signal_type, signal_date, strategy_id) DO UPDATE SET
            stock_name = EXCLUDED.stock_name,
            suggest_price = EXCLUDED.suggest_price
    """
    batch_execute(
        sql,
        [(
            stock_code,
            _stock_name(stock_code),
            signal_type,
            price,
            today_iso(),
            strategy_id,
        )],
    )


def _save_run_log(
    strategy_id: int,
    is_invalid: bool,
    remark: str,
    annual_return: float,
    max_drawdown: float,
    win_rate: float,
    total_profit: float,
) -> None:
    sql = """
        INSERT INTO strategy_run_log (
            strategy_id, annual_return, max_drawdown, win_rate, total_profit, is_invalid, remark
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s)
    """
    batch_execute(
        sql,
        [(
            strategy_id,
            annual_return,
            max_drawdown,
            win_rate,
            total_profit,
            1 if is_invalid else 0,
            remark,
        )],
    )


def _resolve_invalid_rules(params: dict) -> dict:
    return {
        "monthly_max_drawdown_limit_pct": float(params.get("monthly_max_drawdown_limit_pct", 12.0)),
        "latest_month_win_rate_min_pct": float(params.get("latest_month_win_rate_min_pct", 40.0)),
        "max_consecutive_losses": int(params.get("max_consecutive_losses", 6)),
        "underperform_months": int(params.get("underperform_months", 2)),
        "invalid_trigger_count": int(params.get("invalid_trigger_count", 2)),
    }


def is_strategy_allowed_in_market(params: dict, market_status: str | None) -> bool:
    if market_status is None or str(market_status).strip() == "":
        return True
    enabled_regimes = params.get("enabled_regimes")
    if not isinstance(enabled_regimes, (list, tuple, set)):
        return True
    normalized = {str(item).strip().lower() for item in enabled_regimes if str(item).strip()}
    if not normalized:
        return True
    return str(market_status).strip().lower() in normalized


def _has_market_regime_gate(params: dict) -> bool:
    enabled_regimes = params.get("enabled_regimes")
    if not isinstance(enabled_regimes, (list, tuple, set)):
        return False
    normalized = {str(item).strip().lower() for item in enabled_regimes if str(item).strip()}
    return bool(normalized)


def _latest_market_status_value() -> str | None:
    try:
        payload = fetch_latest_market_status()
    except Exception as ex:
        logger.warning("Failed to load latest market status; fallback to allow strategy execution: %s", ex)
        return None
    if payload is None:
        return None
    status = payload.get("status")
    if status is None:
        return None
    normalized = str(status).strip().lower()
    return normalized or None


def _safe_float(value: Any, default: float) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _record_strategy_switch_audit(
    strategy_id: int,
    strategy_type: str,
    market_status: str | None,
    decision: str,
    reason: str,
    actor: str,
    trigger_source: str,
) -> None:
    try:
        last_row = fetch_one(
            """
            SELECT market_status, decision
            FROM strategy_switch_audit
            WHERE strategy_id = %s
            ORDER BY id DESC
            LIMIT 1
            """,
            (strategy_id,),
        )
        last_status = None if last_row is None else (None if last_row[0] is None else str(last_row[0]).strip().lower())
        last_decision = None if last_row is None else str(last_row[1]).strip().upper()
        normalized_status = None if market_status is None else str(market_status).strip().lower()
        normalized_decision = str(decision).strip().upper()
        if last_status == normalized_status and last_decision == normalized_decision:
            return

        batch_execute(
            """
            INSERT INTO strategy_switch_audit (
                strategy_id,
                strategy_type,
                market_status,
                decision,
                reason,
                actor,
                trigger_source,
                create_time
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())
            """,
            [(
                strategy_id,
                strategy_type,
                normalized_status,
                normalized_decision,
                reason,
                actor,
                trigger_source,
            )],
        )
    except Exception as ex:
        logger.warning("Failed to save strategy switch audit: %s", ex)


def _load_active_strategies() -> list[dict]:
    rows = fetch_all(
        """
        SELECT id, strategy_type, params
        FROM strategy_config
        WHERE status = 1
        ORDER BY id
        """
    )
    payload: list[dict] = []
    for row in rows:
        strategy_id = int(row[0])
        strategy_type = "MA" if row[1] is None else str(row[1]).strip().upper() or "MA"
        raw_params = row[2]
        if raw_params is None:
            params = {}
        else:
            params = dict(raw_params) if isinstance(raw_params, dict) else json.loads(raw_params)
        params.setdefault("stop_loss_rate", settings.stop_loss_rate)
        params.setdefault("max_single_position_pct", settings.max_single_position_pct)
        params.setdefault("max_total_position_pct", settings.max_total_position_pct)
        params.setdefault("portfolio_capital", 100000)
        params.setdefault("commission_rate", settings.commission_rate)
        params.setdefault("slippage_rate", settings.slippage_rate)
        params.setdefault("stamp_duty_rate", settings.stamp_duty_rate)
        payload.append(
            {
                "strategy_id": strategy_id,
                "strategy_type": strategy_type,
                "params": params,
            }
        )
    return payload


def _allocator_regime_multiplier(params: dict, market_status: str | None) -> float:
    raw = params.get("regime_budget_weights")
    if not isinstance(raw, dict):
        return 1.0
    if market_status is not None and market_status in raw:
        return max(0.0, _safe_float(raw.get(market_status), 1.0))
    if "default" in raw:
        return max(0.0, _safe_float(raw.get("default"), 1.0))
    return 1.0


def _suggest_buy_quantity(stock_code: str, latest_price: float, params: dict) -> tuple[int, str]:
    if latest_price <= 0:
        return 0, "invalid_price"

    portfolio_capital = float(params.get("portfolio_capital", 100000))
    max_single_position_pct = float(params.get("max_single_position_pct", settings.max_single_position_pct))
    max_total_position_pct = float(params.get("max_total_position_pct", settings.max_total_position_pct))
    if portfolio_capital <= 0:
        return 0, "invalid_portfolio_capital"

    total_row = fetch_one(
        """
        SELECT COALESCE(SUM(COALESCE(current_price, cost_price) * quantity), 0)
        FROM position
        """
    )
    total_market_value = float(total_row[0] or 0)
    max_total_market_value = portfolio_capital * max_total_position_pct
    remaining_total_budget = max_total_market_value - total_market_value
    if remaining_total_budget <= 0:
        return 0, "total_position_limit_reached"

    existing_row = fetch_one(
        """
        SELECT quantity, COALESCE(current_price, cost_price)
        FROM position
        WHERE stock_code = %s
        """,
        (stock_code,),
    )
    existing_market_value = 0.0
    if existing_row is not None:
        existing_market_value = float(existing_row[0]) * float(existing_row[1])

    max_single_market_value = portfolio_capital * max_single_position_pct
    remaining_single_budget = max_single_market_value - existing_market_value
    if remaining_single_budget <= 0:
        return 0, "single_position_limit_reached"

    available_budget = min(remaining_total_budget, remaining_single_budget)
    raw_quantity = math.floor(available_budget / latest_price / 100) * 100
    if raw_quantity <= 0:
        return 0, "insufficient_budget_for_one_lot"
    return int(raw_quantity), "ok"


def run_strategy(
    symbols: list[str] | None = None,
    start_date: str | None = None,
    strategy_id: int | None = None,
    params_override: dict | None = None,
    actor: str = "system",
    trigger_source: str = "run-strategy",
    market_status_override: object = _MARKET_STATUS_UNSET,
) -> dict[str, dict]:
    resolved_start_date = start_date or settings.strategy_backtest_start_date
    strategy_id = strategy_id or settings.strategy_id
    strategy_type, params = _load_strategy_config(strategy_id)
    if params_override:
        merged = dict(params)
        merged.update(params_override)
        params = merged
    executor = resolve_strategy_executor(strategy_type)
    params = executor.normalize_params(params)
    commission_rate = float(params.get("commission_rate", settings.commission_rate))
    slippage_rate = float(params.get("slippage_rate", settings.slippage_rate))
    stamp_duty_rate = float(params.get("stamp_duty_rate", settings.stamp_duty_rate))
    invalid_rules = _resolve_invalid_rules(params)
    market_status = None
    if _has_market_regime_gate(params):
        if market_status_override is _MARKET_STATUS_UNSET:
            market_status = _latest_market_status_value()
        else:
            market_status = None if market_status_override is None else str(market_status_override).strip().lower()
        decision = "ALLOW"
        reason = "market_regime_matched"
        if not is_strategy_allowed_in_market(params, market_status):
            decision = "BLOCK"
            reason = f"market_regime_blocked: status={market_status}, enabled_regimes={params.get('enabled_regimes')}"
            _record_strategy_switch_audit(
                strategy_id=strategy_id,
                strategy_type=strategy_type,
                market_status=market_status,
                decision=decision,
                reason=reason,
                actor=actor,
                trigger_source=trigger_source,
            )
            logger.info(
                "Skip strategy_id=%s strategy_type=%s due to market_status=%s enabled_regimes=%s",
                strategy_id,
                strategy_type,
                market_status,
                params.get("enabled_regimes"),
            )
            _save_run_log(
                strategy_id=strategy_id,
                is_invalid=False,
                remark=reason,
                annual_return=0.0,
                max_drawdown=0.0,
                win_rate=0.0,
                total_profit=0.0,
            )
            return {}
        _record_strategy_switch_audit(
            strategy_id=strategy_id,
            strategy_type=strategy_type,
            market_status=market_status,
            decision=decision,
            reason=reason,
            actor=actor,
            trigger_source=trigger_source,
        )

    resolved_symbols = _resolve_symbols(symbols)
    grouped = load_stock_history_batch(resolved_symbols, start_date=resolved_start_date)
    for stock_code in resolved_symbols:
        if stock_code not in grouped or grouped[stock_code].empty:
            logger.warning("Skip %s because no market history was found in PostgreSQL.", stock_code)
    results = execute_strategy_frames(
        grouped_frames={code: frame for code, frame in grouped.items() if not frame.empty},
        executor=executor,
        params=params,
        strategy_id=strategy_id,
        actor=actor,
        strategy_type=strategy_type,
        market_status=market_status,
    )
    for stock_code, payload in results.items():
        logger.info("Strategy run completed for %s: %s", stock_code, payload)
    return results


def run_strategy_batch(
    symbols: list[str],
    start_date: str | None,
    strategy_id: int,
    actor: str,
    end_date: str | None = None,
    params_override: dict | None = None,
    trigger_source: str = "async-worker",
    market_status_override: object = _MARKET_STATUS_UNSET,
) -> dict[str, dict]:
    resolved_start_date = start_date or settings.strategy_backtest_start_date
    strategy_id = strategy_id or settings.strategy_id
    strategy_type, params = _load_strategy_config(strategy_id)
    if params_override:
        merged = dict(params)
        merged.update(params_override)
        params = merged
    executor = resolve_strategy_executor(strategy_type)
    params = executor.normalize_params(params)
    grouped = load_stock_history_batch(
        [str(symbol).zfill(6) for symbol in symbols],
        start_date=resolved_start_date,
        end_date=end_date,
    )
    market_status = None
    if _has_market_regime_gate(params):
        if market_status_override is _MARKET_STATUS_UNSET:
            market_status = _latest_market_status_value()
        else:
            market_status = None if market_status_override is None else str(market_status_override).strip().lower()
    results = execute_strategy_frames(
        grouped_frames=grouped,
        executor=executor,
        params=params,
        strategy_id=strategy_id,
        actor=actor,
        strategy_type=strategy_type,
        market_status=market_status,
    )
    for stock_code, payload in results.items():
        logger.info("Batch strategy run completed for %s via %s: %s", stock_code, trigger_source, payload)
    return results


def run_portfolio(
    symbols: list[str] | None = None,
    start_date: str | None = None,
    total_capital: float | None = None,
    actor: str = "system",
) -> dict:
    resolved_start_date = start_date or settings.strategy_backtest_start_date
    active = _load_active_strategies()
    if not active:
        return {
            "market_status": _latest_market_status_value(),
            "total_capital": 0.0,
            "strategies": [],
            "executed_strategy_count": 0,
        }

    resolved_total_capital = float(total_capital or settings.portfolio_total_capital)
    if resolved_total_capital <= 0:
        raise ValueError("total_capital must be positive.")

    market_status = _latest_market_status_value()
    weighted_rows: list[dict] = []
    total_weight = 0.0
    for item in active:
        params = dict(item["params"])
        base_weight = max(0.0, _safe_float(params.get("allocator_base_weight", 1.0), 1.0))
        multiplier = _allocator_regime_multiplier(params, market_status)
        effective_weight = round(base_weight * multiplier, 6)
        weighted_rows.append(
            {
                "strategy_id": item["strategy_id"],
                "strategy_type": item["strategy_type"],
                "params": params,
                "base_weight": base_weight,
                "regime_multiplier": multiplier,
                "effective_weight": effective_weight,
            }
        )
        total_weight += effective_weight

    if total_weight <= 0:
        return {
            "market_status": market_status,
            "total_capital": resolved_total_capital,
            "strategies": [
                {
                    "strategy_id": item["strategy_id"],
                    "strategy_type": item["strategy_type"],
                    "allocated_capital": 0.0,
                    "base_weight": item["base_weight"],
                    "regime_multiplier": item["regime_multiplier"],
                    "effective_weight": item["effective_weight"],
                    "result_count": 0,
                    "results": {},
                    "skipped": True,
                }
                for item in weighted_rows
            ],
            "executed_strategy_count": 0,
        }

    executed_count = 0
    result_rows: list[dict] = []
    for item in weighted_rows:
        allocated = round(resolved_total_capital * item["effective_weight"] / total_weight, 2)
        strategy_results = run_strategy(
            symbols=symbols,
            start_date=resolved_start_date,
            strategy_id=item["strategy_id"],
            params_override={"portfolio_capital": allocated},
            actor=actor,
            trigger_source="portfolio-allocator",
            market_status_override=market_status,
        )
        if strategy_results:
            executed_count += 1
        result_rows.append(
            {
                "strategy_id": item["strategy_id"],
                "strategy_type": item["strategy_type"],
                "allocated_capital": allocated,
                "base_weight": item["base_weight"],
                "regime_multiplier": item["regime_multiplier"],
                "effective_weight": item["effective_weight"],
                "result_count": len(strategy_results),
                "results": strategy_results,
                "skipped": len(strategy_results) == 0,
            }
        )

    return {
        "market_status": market_status,
        "total_capital": resolved_total_capital,
        "strategies": result_rows,
        "executed_strategy_count": executed_count,
    }
