from __future__ import annotations

from datetime import datetime, timedelta

from quant_road.db import batch_execute, fetch_all


def _parse_date(raw: str | None) -> datetime.date:
    if raw is None or str(raw).strip() == "":
        return datetime.now().date()
    return datetime.strptime(raw, "%Y-%m-%d").date()


def evaluate_execution_feedback(as_of_date: str | None = None, grace_days: int = 1) -> dict:
    safe_grace_days = max(0, int(grace_days))
    as_of = _parse_date(as_of_date)
    rows = fetch_all(
        """
        SELECT
            ts.id,
            ts.stock_code,
            ts.signal_type,
            ts.signal_date,
            ts.strategy_id,
            COALESCE(ts.is_execute, 0) AS is_execute,
            COALESCE(SUM(er.quantity), 0) AS executed_quantity,
            MAX(er.trade_date) AS last_trade_date
        FROM trade_signal ts
        LEFT JOIN execution_record er ON er.signal_id = ts.id
        WHERE ts.signal_date <= %s
        GROUP BY ts.id, ts.stock_code, ts.signal_type, ts.signal_date, ts.strategy_id, ts.is_execute
        ORDER BY ts.signal_date DESC, ts.id DESC
        """,
        (as_of,),
    )
    upsert_rows: list[tuple] = []
    summary = {"EXECUTED": 0, "MISSED": 0, "PENDING": 0}
    for row in rows:
        signal_id = int(row[0])
        signal_date = row[3]
        due_date = signal_date + timedelta(days=safe_grace_days)
        executed_quantity = int(row[6] or 0)
        last_trade_date = row[7]
        is_execute = int(row[5] or 0) == 1
        overdue_days = max(0, (as_of - due_date).days)

        status = "PENDING"
        remark = f"waiting_execution_until={due_date.isoformat()}"
        if is_execute or executed_quantity > 0:
            status = "EXECUTED"
            remark = f"executed_quantity={executed_quantity}"
        elif as_of > due_date:
            status = "MISSED"
            remark = f"t_plus_{safe_grace_days}_timeout"

        summary[status] += 1
        upsert_rows.append(
            (
                signal_id,
                signal_date,
                due_date,
                as_of,
                status,
                executed_quantity,
                last_trade_date,
                overdue_days,
                remark,
            )
        )

    if upsert_rows:
        batch_execute(
            """
            INSERT INTO signal_execution_feedback (
                signal_id,
                signal_date,
                due_date,
                check_date,
                status,
                executed_quantity,
                last_trade_date,
                overdue_days,
                remark,
                update_time
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
            ON CONFLICT (signal_id) DO UPDATE SET
                signal_date = EXCLUDED.signal_date,
                due_date = EXCLUDED.due_date,
                check_date = EXCLUDED.check_date,
                status = EXCLUDED.status,
                executed_quantity = EXCLUDED.executed_quantity,
                last_trade_date = EXCLUDED.last_trade_date,
                overdue_days = EXCLUDED.overdue_days,
                remark = EXCLUDED.remark,
                update_time = NOW()
            """,
            upsert_rows,
        )

    return {
        "as_of_date": as_of.isoformat(),
        "grace_days": safe_grace_days,
        "total_signals": len(rows),
        "executed": summary["EXECUTED"],
        "missed": summary["MISSED"],
        "pending": summary["PENDING"],
    }
