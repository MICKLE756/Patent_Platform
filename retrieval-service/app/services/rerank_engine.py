"""Rerank 模块 — 封装统一调用入口，支持失败降级"""
from __future__ import annotations
from app.clients.rerank_client import BaseRerankClient
from app.core import config_manager as cfg
from app.core.models import DegradeInfo
from app.core.logging import get_logger

logger = get_logger("rerank_engine")


async def rerank(
    query: str,
    items: list[dict],
    rerank_client: BaseRerankClient,
    top_k: int = 10,
    request_id: str = "-",
    session_id: str = "-",
    degrade_info: DegradeInfo | None = None,
) -> list[dict]:
    """
    调用 Rerank 服务。异常时不中断主流程，跳过 Rerank 并记录降级信息。
    """
    extra = {"request_id": request_id, "session_id": session_id}
    if degrade_info is None:
        degrade_info = DegradeInfo()

    if not items:
        return items

    try:
        candidate_top_k = _positive_int(cfg.get("rerank.candidate_top_k", 50), 50)
        candidate_limit = min(len(items), candidate_top_k)
        candidates = items[:candidate_limit]
        docs = [_build_document(it) for it in candidates]
        rerank_results = await rerank_client.rerank(query, docs, candidate_limit)
        rerank_results = _normalize_scores(rerank_results)

        score_map = {r["patent_id"]: r["rerank_score"] for r in rerank_results}
        for it in items:
            it["rerank_score"] = score_map.get(it["patent_id"], 0.0)

        logger.info("rerank_done candidates=%d scores=%d", len(candidates), len(rerank_results),
                    extra=extra)

    except Exception as e:
        logger.warning("rerank_failed err=%s, degrading", str(e), extra=extra)
        degrade_info.rerank_skipped = True
        degrade_info.reason = f"Rerank 异常已降级: {e}"
        for it in items:
            it["rerank_score"] = 0.0

    return items


def _build_document(item: dict) -> dict:
    max_chars = _positive_int(cfg.get("rerank.max_document_chars", 1500), 1500)
    text = "\n".join([
        f"标题: {item.get('title', '')}",
        f"技术领域: {item.get('tech_field', '')}",
        f"公开日期: {item.get('publish_date', '')}",
        f"元数据匹配分: {item.get('metadata_match_score', 0.0)}",
        f"内容: {item.get('chunk_text', '')}",
    ]).strip()
    if len(text) > max_chars:
        text = text[:max_chars]
    return {
        "patent_id": str(item.get("patent_id", "")),
        "text": text,
        "title": item.get("title", ""),
    }


def _normalize_scores(results: list[dict]) -> list[dict]:
    if not results:
        return []

    scores = [float(r.get("rerank_score", 0.0)) for r in results]
    if all(0.0 <= score <= 1.0 for score in scores):
        return [
            {"patent_id": r["patent_id"], "rerank_score": round(float(r["rerank_score"]), 4)}
            for r in results
        ]

    low = min(scores)
    high = max(scores)
    if high == low:
        return [{"patent_id": r["patent_id"], "rerank_score": 1.0} for r in results]

    normalized = []
    for r in results:
        value = (float(r.get("rerank_score", 0.0)) - low) / (high - low)
        normalized.append({"patent_id": r["patent_id"], "rerank_score": round(value, 4)})
    return normalized


def _positive_int(value: object, default: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return parsed if parsed > 0 else default
