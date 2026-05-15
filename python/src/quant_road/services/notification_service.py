from __future__ import annotations

import json
import logging
from urllib import request

from quant_road.config import settings
from quant_road.services.report_service import build_daily_summary

logger = logging.getLogger(__name__)


def _build_payload(message: str) -> dict:
    notify_type = settings.notify_type or "dingding"
    if notify_type not in {"dingding", "wechat"}:
        raise ValueError(f"Unsupported NOTIFY_TYPE: {notify_type}")
    return {"msgtype": "text", "text": {"content": message}}


def notify_text(message: str) -> bool:
    if not settings.notify_enabled:
        logger.info("Notification skipped because NOTIFY_ENABLED is false.")
        return False
    if not settings.notify_webhook:
        logger.warning("Notification skipped because NOTIFY_WEBHOOK is empty.")
        return False

    payload = json.dumps(_build_payload(message)).encode("utf-8")
    req = request.Request(
        url=settings.notify_webhook,
        data=payload,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    with request.urlopen(req, timeout=10) as resp:
        logger.info("Notification sent, status=%s", getattr(resp, "status", "unknown"))
    return True


def notify_daily_summary() -> bool:
    return notify_text(build_daily_summary())
