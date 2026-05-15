from __future__ import annotations

import csv
import logging
from datetime import datetime
from pathlib import Path

from quant_road.db import batch_execute, fetch_one

logger = logging.getLogger(__name__)
REQUIRED_IMPORT_COLUMNS = ("stock_code", "side", "quantity", "price", "trade_date")


def _normalize_code(stock_code: str) -> str:
    return str(stock_code).zfill(6)


def _strategy_exists(strategy_id: int) -> bool:
    row = fetch_one("SELECT id FROM strategy_config WHERE id = %s", (strategy_id,))
    return row is not None


def _stock_exists(stock_code: str) -> bool:
    row = fetch_one("SELECT stock_code FROM stock_basic WHERE stock_code = %s", (stock_code,))
    return row is not None


def _signal_exists(signal_id: int) -> bool:
    row = fetch_one("SELECT id FROM trade_signal WHERE id = %s", (signal_id,))
    return row is not None


def _resolve_signal_id(stock_code: str, side: str, strategy_id: int, signal_id: int | None) -> int | None:
    if signal_id is not None:
        if _signal_exists(signal_id):
            return signal_id
        logger.warning(
            "Signal id %s not found for stock=%s side=%s strategy=%s; fallback to auto resolve.",
            signal_id,
            stock_code,
            side,
            strategy_id,
        )
    row = fetch_one(
        """
        SELECT id
        FROM trade_signal
        WHERE stock_code = %s
          AND signal_type = %s
          AND strategy_id = %s
          AND is_execute = 0
        ORDER BY signal_date DESC, id DESC
        LIMIT 1
        """,
        (stock_code, side, strategy_id),
    )
    return None if row is None else int(row[0])


def _duplicate_execution_exists(
    stock_code: str,
    side: str,
    quantity: int,
    price: float,
    trade_date: str,
    strategy_id: int,
    external_order_id: str | None,
) -> bool:
    if external_order_id:
        row = fetch_one(
            "SELECT id FROM execution_record WHERE external_order_id = %s LIMIT 1",
            (external_order_id,),
        )
        if row is not None:
            return True
    row = fetch_one(
        """
        SELECT id
        FROM execution_record
        WHERE stock_code = %s
          AND side = %s
          AND quantity = %s
          AND price = %s
          AND trade_date = %s
          AND strategy_id = %s
        LIMIT 1
        """,
        (stock_code, side, quantity, price, trade_date, strategy_id),
    )
    return row is not None


def _parse_positive_int(raw: str | None, field_name: str) -> int:
    try:
        value = int(str(raw).strip())
    except Exception as exc:  # noqa: BLE001
        raise ValueError(f"{field_name} must be integer.") from exc
    if value <= 0:
        raise ValueError(f"{field_name} must be positive.")
    return value


def _parse_non_negative_float(raw: str | None, field_name: str) -> float:
    try:
        value = float(str(raw).strip())
    except Exception as exc:  # noqa: BLE001
        raise ValueError(f"{field_name} must be number.") from exc
    if value < 0:
        raise ValueError(f"{field_name} must be non-negative.")
    return value


def _parse_positive_float(raw: str | None, field_name: str) -> float:
    value = _parse_non_negative_float(raw, field_name)
    if value <= 0:
        raise ValueError(f"{field_name} must be positive.")
    return value


def _build_preview_row(
    row_no: int,
    raw_row: dict,
    status: str,
    message: str,
    normalized: dict | None = None,
    signal_id: int | None = None,
) -> dict:
    action_meta = _preview_action_meta(status)
    return {
        "rowNo": row_no,
        "status": status,
        "message": message,
        "stockCode": (normalized or {}).get("stock_code") or raw_row.get("stock_code"),
        "side": (normalized or {}).get("side") or raw_row.get("side"),
        "quantity": (normalized or {}).get("quantity"),
        "price": (normalized or {}).get("price"),
        "tradeDate": (normalized or {}).get("trade_date") or raw_row.get("trade_date"),
        "strategyId": (normalized or {}).get("strategy_id"),
        "signalId": signal_id,
        "externalOrderId": (normalized or {}).get("external_order_id"),
        "recommendedAction": action_meta["recommendedAction"],
        "actionLabel": action_meta["actionLabel"],
        "actionTarget": action_meta["actionTarget"],
    }


def _preview_action_meta(status: str) -> dict:
    normalized = str(status or "").strip().upper()
    if normalized == "VALID":
        return {
            "recommendedAction": "NO_ACTION",
            "actionLabel": "无需处理",
            "actionTarget": "import",
        }
    if normalized == "DUPLICATE":
        return {
            "recommendedAction": "REVIEW_DUPLICATE_EXECUTION",
            "actionLabel": "核对重复成交",
            "actionTarget": "records",
        }
    if normalized == "UNMATCHED_SIGNAL":
        return {
            "recommendedAction": "CHECK_SIGNAL_MATCH",
            "actionLabel": "核对信号匹配",
            "actionTarget": "signals",
        }
    return {
        "recommendedAction": "FIX_SOURCE_FILE",
        "actionLabel": "修正源文件",
        "actionTarget": "import",
    }


def validate_execution_import(file_path: str, default_strategy_id: int | None = None) -> dict:
    path = Path(file_path)
    if not path.exists():
        raise FileNotFoundError(f"Execution file not found: {file_path}")

    preview_rows: list[dict] = []
    duplicate_keys: set[str] = set()
    valid_rows = 0
    invalid_rows = 0
    duplicate_rows = 0
    unmatched_signal_rows = 0

    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames is None:
            raise ValueError("CSV header is required.")
        missing_columns = [column for column in REQUIRED_IMPORT_COLUMNS if column not in reader.fieldnames]
        if missing_columns:
            raise ValueError(f"Missing required columns: {','.join(missing_columns)}")

        for row_no, row in enumerate(reader, start=2):
            try:
                stock_code = _normalize_code((row.get("stock_code") or "").strip())
                if not stock_code:
                    raise ValueError("stock_code is required.")
                side = (row.get("side") or "").strip().upper()
                if side not in {"BUY", "SELL"}:
                    raise ValueError("side must be BUY or SELL.")
                quantity = _parse_positive_int(row.get("quantity"), "quantity")
                price = _parse_positive_float(row.get("price"), "price")
                trade_date = (row.get("trade_date") or "").strip()
                datetime.strptime(trade_date, "%Y-%m-%d")
                strategy_id = _parse_positive_int(row.get("strategy_id") or default_strategy_id, "strategy_id")
                commission = _parse_non_negative_float(row.get("commission") or 0, "commission")
                tax = _parse_non_negative_float(row.get("tax") or 0, "tax")
                slippage = _parse_non_negative_float(row.get("slippage") or 0, "slippage")
                signal_id = int(row["signal_id"]) if row.get("signal_id") else None
                external_order_id = (row.get("external_order_id") or "").strip() or None

                if not _strategy_exists(strategy_id):
                    raise ValueError(f"strategy_id {strategy_id} not found.")
                if not _stock_exists(stock_code):
                    raise ValueError(f"stock_code {stock_code} not found.")
                if side == "SELL":
                    existing_position = _load_position(stock_code)
                    if existing_position is None:
                        raise ValueError(f"SELL {stock_code} has no current position.")
                    if quantity > existing_position[0]:
                        raise ValueError(f"SELL {stock_code} quantity {quantity} exceeds position {existing_position[0]}.")

                duplicate_key = external_order_id or f"{stock_code}|{side}|{quantity}|{price}|{trade_date}|{strategy_id}"
                normalized = {
                    "stock_code": stock_code,
                    "side": side,
                    "quantity": quantity,
                    "price": price,
                    "trade_date": trade_date,
                    "strategy_id": strategy_id,
                    "commission": commission,
                    "tax": tax,
                    "slippage": slippage,
                    "signal_id": signal_id,
                    "external_order_id": external_order_id,
                }
                if duplicate_key in duplicate_keys or _duplicate_execution_exists(
                    stock_code=stock_code,
                    side=side,
                    quantity=quantity,
                    price=price,
                    trade_date=trade_date,
                    strategy_id=strategy_id,
                    external_order_id=external_order_id,
                ):
                    duplicate_rows += 1
                    preview_rows.append(_build_preview_row(row_no, row, "DUPLICATE", "检测到重复成交记录。", normalized, signal_id))
                    continue
                duplicate_keys.add(duplicate_key)

                resolved_signal_id = _resolve_signal_id(stock_code, side, strategy_id, signal_id)
                if resolved_signal_id is None:
                    unmatched_signal_rows += 1
                    preview_rows.append(
                        _build_preview_row(row_no, row, "UNMATCHED_SIGNAL", "未找到可自动关联的信号，导入后将保留为未匹配成交。", normalized, None)
                    )
                    continue

                valid_rows += 1
                preview_rows.append(_build_preview_row(row_no, row, "VALID", "校验通过。", normalized, resolved_signal_id))
            except Exception as exc:  # noqa: BLE001
                invalid_rows += 1
                preview_rows.append(_build_preview_row(row_no, row, "INVALID", str(exc)))

    total_rows = len(preview_rows)
    error_rows = [row for row in preview_rows if row["status"] in {"INVALID", "DUPLICATE"}]
    return {
        "file": str(path),
        "totalRows": total_rows,
        "validRows": valid_rows,
        "invalidRows": invalid_rows,
        "duplicateRows": duplicate_rows,
        "unmatchedSignalRows": unmatched_signal_rows,
        "errorRows": error_rows,
        "previewRows": preview_rows,
        "canImport": total_rows > 0 and invalid_rows == 0 and duplicate_rows == 0,
    }


def _load_position(stock_code: str) -> tuple[int, float] | None:
    row = fetch_one(
        """
        SELECT quantity, cost_price
        FROM position
        WHERE stock_code = %s
        """,
        (stock_code,),
    )
    if row is None:
        return None
    return int(row[0]), float(row[1])


def _upsert_position_buy(stock_code: str, quantity: int, price: float, total_fee: float) -> None:
    existing = _load_position(stock_code)
    if existing is None:
        total_cost = quantity * price + total_fee
        cost_price = total_cost / quantity
        sql = """
            INSERT INTO position (stock_code, stock_name, quantity, cost_price, current_price, float_profit, loss_warning, update_time)
            VALUES (
                %s,
                (SELECT stock_name FROM stock_basic WHERE stock_code = %s),
                %s,
                %s,
                %s,
                0,
                0,
                NOW()
            )
            ON CONFLICT (stock_code) DO UPDATE SET
                quantity = EXCLUDED.quantity,
                cost_price = EXCLUDED.cost_price,
                current_price = EXCLUDED.current_price,
                update_time = NOW()
        """
        batch_execute(sql, [(stock_code, stock_code, quantity, round(cost_price, 4), price)])
        return

    old_qty, old_cost_price = existing
    new_qty = old_qty + quantity
    total_cost = old_qty * old_cost_price + quantity * price + total_fee
    new_cost_price = total_cost / new_qty
    sql = """
        UPDATE position
        SET quantity = %s,
            cost_price = %s,
            current_price = %s,
            update_time = NOW()
        WHERE stock_code = %s
    """
    batch_execute(sql, [(new_qty, round(new_cost_price, 4), price, stock_code)])


def _update_position_sell(stock_code: str, quantity: int, price: float) -> None:
    existing = _load_position(stock_code)
    if existing is None:
        raise ValueError(f"Cannot SELL {stock_code}: no existing position.")
    old_qty, old_cost_price = existing
    if quantity > old_qty:
        raise ValueError(f"Cannot SELL {stock_code}: quantity {quantity} exceeds position {old_qty}.")

    remaining = old_qty - quantity
    if remaining == 0:
        batch_execute("DELETE FROM position WHERE stock_code = %s", [(stock_code,)])
        return
    sql = """
        UPDATE position
        SET quantity = %s,
            cost_price = %s,
            current_price = %s,
            update_time = NOW()
        WHERE stock_code = %s
    """
    batch_execute(sql, [(remaining, old_cost_price, price, stock_code)])


def apply_execution(
    stock_code: str,
    side: str,
    quantity: int,
    price: float,
    trade_date: str,
    strategy_id: int,
    commission: float = 0.0,
    tax: float = 0.0,
    slippage: float = 0.0,
    signal_id: int | None = None,
    external_order_id: str | None = None,
) -> dict:
    side = side.upper().strip()
    if side not in {"BUY", "SELL"}:
        raise ValueError("side must be BUY or SELL.")
    if quantity <= 0:
        raise ValueError("quantity must be positive.")
    if price <= 0:
        raise ValueError("price must be positive.")
    try:
        datetime.strptime(trade_date, "%Y-%m-%d")
    except ValueError as exc:
        raise ValueError("trade_date must be in YYYY-MM-DD format.") from exc

    stock_code = _normalize_code(stock_code)
    signal_id = _resolve_signal_id(stock_code, side, strategy_id, signal_id)
    fee_total = float(commission) + float(tax) + float(slippage)
    gross_amount = float(quantity) * float(price)
    net_amount = gross_amount + fee_total if side == "BUY" else gross_amount - fee_total

    insert_sql = """
        INSERT INTO execution_record (
            stock_code, side, quantity, price, trade_date, strategy_id, signal_id,
            commission, tax, slippage, gross_amount, net_amount, external_order_id
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    batch_execute(
        insert_sql,
        [(
            stock_code,
            side,
            quantity,
            price,
            trade_date,
            strategy_id,
            signal_id,
            commission,
            tax,
            slippage,
            round(gross_amount, 4),
            round(net_amount, 4),
            external_order_id,
        )],
    )

    if side == "BUY":
        _upsert_position_buy(stock_code, quantity, price, fee_total)
    else:
        _update_position_sell(stock_code, quantity, price)

    if signal_id is not None:
        update_signal_sql = """
            UPDATE trade_signal
            SET is_execute = 1
            WHERE id = %s
        """
        batch_execute(update_signal_sql, [(signal_id,)])

    return {
        "stock_code": stock_code,
        "side": side,
        "quantity": quantity,
        "price": price,
        "trade_date": trade_date,
        "strategy_id": strategy_id,
        "signal_id": signal_id,
        "fee_total": round(fee_total, 4),
    }


def import_executions_from_csv(file_path: str, default_strategy_id: int | None = None) -> dict:
    validation = validate_execution_import(file_path=file_path, default_strategy_id=default_strategy_id)
    if not validation["canImport"]:
        raise ValueError(
            "Execution import validation failed: "
            f"invalid={validation['invalidRows']}, duplicate={validation['duplicateRows']}"
        )

    path = Path(file_path)
    preview_by_row = {item["rowNo"]: item for item in validation["previewRows"]}
    applied = 0
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row_no, row in enumerate(reader, start=2):
            preview = preview_by_row.get(row_no) or {}
            if preview.get("status") not in {"VALID", "UNMATCHED_SIGNAL"}:
                continue
            apply_execution(
                stock_code=row["stock_code"],
                side=row["side"],
                quantity=int(row["quantity"]),
                price=float(row["price"]),
                trade_date=row["trade_date"],
                strategy_id=int(row.get("strategy_id") or default_strategy_id or 1),
                commission=float(row.get("commission") or 0),
                tax=float(row.get("tax") or 0),
                slippage=float(row.get("slippage") or 0),
                signal_id=int(row["signal_id"]) if row.get("signal_id") else None,
                external_order_id=row.get("external_order_id") or None,
            )
            applied += 1
    unmatched_preview_rows = [row for row in validation["previewRows"] if row.get("status") == "UNMATCHED_SIGNAL"]
    return {
        **validation,
        "appliedRows": applied,
        "importedUnmatchedRows": len(unmatched_preview_rows),
        "unmatchedPreviewRows": unmatched_preview_rows,
        "needsManualMatch": len(unmatched_preview_rows) > 0,
        "message": (
            f"Imported {applied} rows."
            if not unmatched_preview_rows
            else f"Imported {applied} rows, with {len(unmatched_preview_rows)} unmatched executions awaiting manual match."
        ),
    }
