from __future__ import annotations

from quant_road.db import batch_execute
from quant_road.services.market_regime_service import fetch_latest_market_status
from quant_road.services.report_service import fetch_shadow_compare_payload


def _resolve_recommendation(summary: dict) -> tuple[str, str]:
    comparable = int(summary.get("comparable_months", 0))
    if comparable <= 0:
        return "insufficient_data", "no comparable months"

    better_annual = int(summary.get("candidate_better_annual_months", 0))
    lower_drawdown = int(summary.get("candidate_lower_drawdown_months", 0))
    higher_win_rate = int(summary.get("candidate_higher_win_rate_months", 0))
    lower_invalid_rate = int(summary.get("candidate_lower_invalid_rate_months", 0))

    annual_ratio = better_annual / comparable
    drawdown_ratio = lower_drawdown / comparable
    win_ratio = higher_win_rate / comparable
    invalid_ratio = lower_invalid_rate / comparable
    score = annual_ratio + drawdown_ratio + win_ratio + invalid_ratio

    if annual_ratio >= 0.60 and drawdown_ratio >= 0.50 and invalid_ratio >= 0.50:
        return "promote_candidate", f"score={score:.2f} annual={annual_ratio:.2f} drawdown={drawdown_ratio:.2f}"
    if annual_ratio <= 0.40 and drawdown_ratio <= 0.40 and invalid_ratio <= 0.40:
        return "keep_baseline", f"score={score:.2f} annual={annual_ratio:.2f} drawdown={drawdown_ratio:.2f}"
    return "observe", f"score={score:.2f} annual={annual_ratio:.2f} drawdown={drawdown_ratio:.2f}"


def evaluate_canary(
    baseline_strategy_id: int,
    candidate_strategy_id: int,
    months: int = 6,
) -> dict:
    payload = fetch_shadow_compare_payload(
        baseline_strategy_id=baseline_strategy_id,
        candidate_strategy_id=candidate_strategy_id,
        months=max(1, int(months)),
    )
    summary = payload["summary"]
    recommendation, remark = _resolve_recommendation(summary)
    market_payload = fetch_latest_market_status() or {}
    market_status = market_payload.get("status")

    row = (
        payload["generated_at"],
        int(payload["baseline"]["strategy_id"]),
        int(payload["candidate"]["strategy_id"]),
        int(payload["months"]),
        int(summary.get("comparable_months", 0)),
        int(summary.get("candidate_better_annual_months", 0)),
        int(summary.get("candidate_lower_drawdown_months", 0)),
        int(summary.get("candidate_higher_win_rate_months", 0)),
        int(summary.get("candidate_lower_invalid_rate_months", 0)),
        market_status,
        recommendation,
        remark,
    )
    batch_execute(
        """
        INSERT INTO canary_run_log (
            run_date,
            baseline_strategy_id,
            candidate_strategy_id,
            months,
            comparable_months,
            candidate_better_annual_months,
            candidate_lower_drawdown_months,
            candidate_higher_win_rate_months,
            candidate_lower_invalid_rate_months,
            market_status,
            recommendation,
            remark,
            create_time
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
        ON CONFLICT (run_date, baseline_strategy_id, candidate_strategy_id, months) DO UPDATE SET
            comparable_months = EXCLUDED.comparable_months,
            candidate_better_annual_months = EXCLUDED.candidate_better_annual_months,
            candidate_lower_drawdown_months = EXCLUDED.candidate_lower_drawdown_months,
            candidate_higher_win_rate_months = EXCLUDED.candidate_higher_win_rate_months,
            candidate_lower_invalid_rate_months = EXCLUDED.candidate_lower_invalid_rate_months,
            market_status = EXCLUDED.market_status,
            recommendation = EXCLUDED.recommendation,
            remark = EXCLUDED.remark,
            create_time = NOW()
        """,
        [row],
    )

    return {
        "generated_at": payload["generated_at"],
        "baseline_strategy_id": payload["baseline"]["strategy_id"],
        "candidate_strategy_id": payload["candidate"]["strategy_id"],
        "months": payload["months"],
        "summary": summary,
        "market_status": market_status,
        "recommendation": recommendation,
        "remark": remark,
    }
