from __future__ import annotations

from collections import Counter
from pathlib import Path

from quant_road.db import fetch_all
from quant_road.services.market_service import today_iso


def fetch_today_signals() -> list[dict]:
    rows = fetch_all(
        """
        SELECT stock_code, COALESCE(stock_name, ''), signal_type, suggest_price
        FROM trade_signal
        WHERE signal_date = %s
        ORDER BY signal_type, stock_code
        """,
        (today_iso(),),
    )
    return [
        {
            "stock_code": row[0],
            "stock_name": row[1],
            "signal_type": row[2],
            "suggest_price": float(row[3]) if row[3] is not None else None,
        }
        for row in rows
    ]


def fetch_loss_warnings() -> list[dict]:
    rows = fetch_all(
        """
        SELECT stock_code, COALESCE(stock_name, ''), current_price, float_profit
        FROM position
        WHERE loss_warning = 1
        ORDER BY stock_code
        """
    )
    return [
        {
            "stock_code": row[0],
            "stock_name": row[1],
            "current_price": float(row[2]) if row[2] is not None else None,
            "float_profit": float(row[3]) if row[3] is not None else None,
        }
        for row in rows
    ]


def fetch_latest_invalid_logs(limit: int = 5) -> list[dict]:
    rows = fetch_all(
        """
        SELECT run_time, remark
        FROM strategy_run_log
        WHERE is_invalid = 1
        ORDER BY run_time DESC
        LIMIT %s
        """,
        (limit,),
    )
    return [{"run_time": row[0], "remark": row[1]} for row in rows]


def build_daily_summary() -> str:
    signals = fetch_today_signals()
    warnings = fetch_loss_warnings()
    invalid_logs = fetch_latest_invalid_logs()
    signal_counter = Counter(item["signal_type"] for item in signals)

    lines: list[str] = []
    lines.append(f"Quant Road 盘后摘要 {today_iso()}")
    lines.append(f"今日信号数: {len(signals)}")
    if signals:
        lines.append(f"BUY={signal_counter.get('BUY', 0)}, SELL={signal_counter.get('SELL', 0)}")
        for item in signals[:10]:
            lines.append(
                f"{item['signal_type']} {item['stock_code']} {item['stock_name']} @ {item['suggest_price']}"
            )
        if len(signals) > 10:
            lines.append(f"... 其余 {len(signals) - 10} 条见数据库 trade_signal")
    else:
        lines.append("今日无新增交易信号")

    lines.append(f"止损预警数: {len(warnings)}")
    for item in warnings[:10]:
        lines.append(
            f"RISK {item['stock_code']} {item['stock_name']} pnl={item['float_profit']}% price={item['current_price']}"
        )
    if len(warnings) > 10:
        lines.append(f"... 其余 {len(warnings) - 10} 条见数据库 position")

    if invalid_logs:
        lines.append("最近策略失效记录:")
        for item in invalid_logs[:3]:
            lines.append(f"{item['run_time']}: {item['remark']}")
    else:
        lines.append("最近无策略失效记录")

    return "\n".join(lines)


def fetch_monthly_strategy_kpis(months: int = 6) -> list[dict]:
    safe_months = max(1, int(months))
    rows = fetch_all(
        """
        SELECT
            TO_CHAR(DATE_TRUNC('month', run_time), 'YYYY-MM') AS month,
            strategy_id,
            COUNT(1) AS run_count,
            ROUND(AVG(annual_return)::numeric, 2) AS avg_annual_return,
            ROUND(AVG(max_drawdown)::numeric, 2) AS avg_max_drawdown,
            ROUND(AVG(win_rate)::numeric, 2) AS avg_win_rate,
            ROUND(AVG(total_profit)::numeric, 2) AS avg_total_profit,
            SUM(CASE WHEN is_invalid = 1 THEN 1 ELSE 0 END) AS invalid_count
        FROM strategy_run_log
        WHERE run_time >= (CURRENT_DATE - (%s * INTERVAL '31 day'))
        GROUP BY DATE_TRUNC('month', run_time), strategy_id
        ORDER BY DATE_TRUNC('month', run_time) DESC, strategy_id ASC
        """,
        (safe_months,),
    )
    return [
        {
            "month": row[0],
            "strategy_id": int(row[1]),
            "run_count": int(row[2]),
            "avg_annual_return": float(row[3]) if row[3] is not None else None,
            "avg_max_drawdown": float(row[4]) if row[4] is not None else None,
            "avg_win_rate": float(row[5]) if row[5] is not None else None,
            "avg_total_profit": float(row[6]) if row[6] is not None else None,
            "invalid_count": int(row[7]),
        }
        for row in rows
    ]


def build_monthly_summary(months: int = 6) -> str:
    safe_months = max(1, int(months))
    kpis = fetch_monthly_strategy_kpis(safe_months)
    lines: list[str] = []
    lines.append(f"Quant Road 月度策略评估 {today_iso()}")
    lines.append(f"统计窗口: 最近 {safe_months} 个月")
    if not kpis:
        lines.append("暂无 strategy_run_log 数据，无法生成月度评估。")
        return "\n".join(lines)

    grouped: dict[str, list[dict]] = {}
    for item in kpis:
        grouped.setdefault(item["month"], []).append(item)

    for month in sorted(grouped.keys(), reverse=True):
        lines.append(f"\n[{month}]")
        for item in grouped[month]:
            lines.append(
                "strategy={strategy_id} runs={run_count} invalid={invalid_count} "
                "avg_annual={avg_annual_return}% avg_drawdown={avg_max_drawdown}% "
                "avg_win={avg_win_rate}% avg_profit={avg_total_profit}%".format(**item)
            )
    return "\n".join(lines)


def write_monthly_summary(summary: str, output_path: str | None = None) -> Path:
    if output_path:
        target = Path(output_path)
    else:
        repo_root = Path(__file__).resolve().parents[4]
        month_tag = today_iso()[:7]
        target = repo_root / "docs" / "reports" / f"monthly-{month_tag}.md"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(summary + "\n", encoding="utf-8")
    return target


def _fetch_strategy_meta(strategy_ids: tuple[int, int]) -> dict[int, dict]:
    rows = fetch_all(
        """
        SELECT id, strategy_name, strategy_type
        FROM strategy_config
        WHERE id IN (%s, %s)
        """,
        strategy_ids,
    )
    return {
        int(row[0]): {
            "strategy_id": int(row[0]),
            "strategy_name": str(row[1]),
            "strategy_type": str(row[2]) if row[2] is not None else "UNKNOWN",
        }
        for row in rows
    }


def _safe_invalid_rate(run_count: int, invalid_count: int) -> float:
    if run_count <= 0:
        return 0.0
    return round((invalid_count / run_count) * 100, 2)


def _delta(left: float | None, right: float | None) -> float | None:
    if left is None or right is None:
        return None
    return round(left - right, 2)


def fetch_shadow_compare_payload(
    baseline_strategy_id: int,
    candidate_strategy_id: int,
    months: int = 6,
) -> dict:
    if baseline_strategy_id == candidate_strategy_id:
        raise ValueError("baseline_strategy_id and candidate_strategy_id must be different.")

    safe_months = max(1, int(months))
    meta = _fetch_strategy_meta((baseline_strategy_id, candidate_strategy_id))
    baseline_meta = meta.get(
        baseline_strategy_id,
        {
            "strategy_id": baseline_strategy_id,
            "strategy_name": f"strategy-{baseline_strategy_id}",
            "strategy_type": "UNKNOWN",
        },
    )
    candidate_meta = meta.get(
        candidate_strategy_id,
        {
            "strategy_id": candidate_strategy_id,
            "strategy_name": f"strategy-{candidate_strategy_id}",
            "strategy_type": "UNKNOWN",
        },
    )

    rows = fetch_all(
        """
        SELECT
            TO_CHAR(DATE_TRUNC('month', run_time), 'YYYY-MM') AS month,
            strategy_id,
            COUNT(1) AS run_count,
            ROUND(AVG(annual_return)::numeric, 2) AS avg_annual_return,
            ROUND(AVG(max_drawdown)::numeric, 2) AS avg_max_drawdown,
            ROUND(AVG(win_rate)::numeric, 2) AS avg_win_rate,
            ROUND(AVG(total_profit)::numeric, 2) AS avg_total_profit,
            SUM(CASE WHEN is_invalid = 1 THEN 1 ELSE 0 END) AS invalid_count
        FROM strategy_run_log
        WHERE strategy_id IN (%s, %s)
          AND run_time >= (CURRENT_DATE - (%s * INTERVAL '31 day'))
        GROUP BY DATE_TRUNC('month', run_time), strategy_id
        ORDER BY DATE_TRUNC('month', run_time) DESC, strategy_id ASC
        """,
        (baseline_strategy_id, candidate_strategy_id, safe_months),
    )
    monthly: dict[str, dict[int, dict]] = {}
    for row in rows:
        month = str(row[0])
        strategy_id = int(row[1])
        run_count = int(row[2])
        invalid_count = int(row[7])
        monthly.setdefault(month, {})[strategy_id] = {
            "run_count": run_count,
            "avg_annual_return": float(row[3]) if row[3] is not None else None,
            "avg_max_drawdown": float(row[4]) if row[4] is not None else None,
            "avg_win_rate": float(row[5]) if row[5] is not None else None,
            "avg_total_profit": float(row[6]) if row[6] is not None else None,
            "invalid_count": invalid_count,
            "invalid_rate": _safe_invalid_rate(run_count, invalid_count),
        }

    months_data: list[dict] = []
    better_annual = 0
    lower_drawdown = 0
    higher_win_rate = 0
    lower_invalid_rate = 0
    comparable_months = 0

    for month in sorted(monthly.keys(), reverse=True):
        base_item = monthly[month].get(baseline_strategy_id)
        cand_item = monthly[month].get(candidate_strategy_id)
        delta = None
        if base_item and cand_item:
            comparable_months += 1
            delta = {
                "avg_annual_return": _delta(cand_item["avg_annual_return"], base_item["avg_annual_return"]),
                "avg_max_drawdown": _delta(cand_item["avg_max_drawdown"], base_item["avg_max_drawdown"]),
                "avg_win_rate": _delta(cand_item["avg_win_rate"], base_item["avg_win_rate"]),
                "avg_total_profit": _delta(cand_item["avg_total_profit"], base_item["avg_total_profit"]),
                "invalid_rate": _delta(cand_item["invalid_rate"], base_item["invalid_rate"]),
            }
            if delta["avg_annual_return"] is not None and delta["avg_annual_return"] > 0:
                better_annual += 1
            if delta["avg_max_drawdown"] is not None and delta["avg_max_drawdown"] > 0:
                lower_drawdown += 1
            if delta["avg_win_rate"] is not None and delta["avg_win_rate"] > 0:
                higher_win_rate += 1
            if delta["invalid_rate"] is not None and delta["invalid_rate"] < 0:
                lower_invalid_rate += 1

        months_data.append(
            {
                "month": month,
                "baseline": base_item,
                "candidate": cand_item,
                "delta": delta,
            }
        )

    return {
        "generated_at": today_iso(),
        "months": safe_months,
        "baseline": baseline_meta,
        "candidate": candidate_meta,
        "months_data": months_data,
        "summary": {
            "comparable_months": comparable_months,
            "candidate_better_annual_months": better_annual,
            "candidate_lower_drawdown_months": lower_drawdown,
            "candidate_higher_win_rate_months": higher_win_rate,
            "candidate_lower_invalid_rate_months": lower_invalid_rate,
        },
    }


def build_shadow_compare_summary(payload: dict) -> str:
    baseline = payload["baseline"]
    candidate = payload["candidate"]
    summary = payload["summary"]
    lines: list[str] = []
    lines.append(f"Quant Road Shadow Compare {payload['generated_at']}")
    lines.append(f"baseline: {baseline['strategy_id']} {baseline['strategy_name']} ({baseline['strategy_type']})")
    lines.append(f"candidate: {candidate['strategy_id']} {candidate['strategy_name']} ({candidate['strategy_type']})")
    lines.append(f"统计窗口: 最近 {payload['months']} 个月")
    lines.append(
        "可比月份={comparable_months}, candidate年化更优={candidate_better_annual_months}, "
        "candidate回撤更低={candidate_lower_drawdown_months}, candidate胜率更高={candidate_higher_win_rate_months}, "
        "candidate失效率更低={candidate_lower_invalid_rate_months}".format(**summary)
    )
    for item in payload["months_data"]:
        lines.append(f"\n[{item['month']}]")
        lines.append(f"baseline={item['baseline']}")
        lines.append(f"candidate={item['candidate']}")
        lines.append(f"delta={item['delta']}")
    return "\n".join(lines)


def write_shadow_compare_summary(
    summary: str,
    baseline_strategy_id: int,
    candidate_strategy_id: int,
    output_path: str | None = None,
) -> Path:
    if output_path:
        target = Path(output_path)
    else:
        repo_root = Path(__file__).resolve().parents[4]
        month_tag = today_iso()[:7]
        target = repo_root / "docs" / "reports" / (
            f"shadow-compare-{month_tag}-{baseline_strategy_id}-vs-{candidate_strategy_id}.md"
        )
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(summary + "\n", encoding="utf-8")
    return target
