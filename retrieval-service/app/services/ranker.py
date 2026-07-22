"""综合排序模块"""
from __future__ import annotations
from app.core import config_manager as cfg
from app.core.logging import get_logger

logger = get_logger("ranker")


def compute_final_scores(items: list[dict], use_rerank: bool | None = None) -> list[dict]:
    """
    计算 final_score 并排序。
    预留字段: semantic_score, rerank_score, freshness_score, metadata_match_score
    """
    weights = _ranking_weights(use_rerank)
    for it in items:
        semantic = it.get("semantic_score", 0.0)
        rerank = it.get("rerank_score", 0.0)
        freshness = it.get("freshness_score", 0.0)
        metadata = it.get("metadata_match_score", 0.0)

        it["final_score"] = round(
            weights["semantic"] * semantic
            + weights["rerank"] * rerank
            + weights["freshness"] * freshness
            + weights["metadata"] * metadata,
            4,
        )

    items.sort(key=lambda x: x["final_score"], reverse=True)
    return items


def _ranking_weights(use_rerank: bool | None = None) -> dict[str, float]:
    if use_rerank is None:
        use_rerank = bool(cfg.get("ranking.use_rerank", False))

    if not use_rerank:
        return {
            "semantic": 1.0,
            "rerank": 0.0,
            "freshness": 0.0,
            "metadata": 0.0,
        }

    return {
        "semantic": _float_cfg("ranking.weights.semantic", 0.7),
        "rerank": _float_cfg("ranking.weights.rerank", 0.3),
        "freshness": _float_cfg("ranking.weights.freshness", 0.0),
        "metadata": _float_cfg("ranking.weights.metadata", 0.0),
    }


def _float_cfg(key: str, default: float) -> float:
    try:
        return float(cfg.get(key, default))
    except (TypeError, ValueError):
        return default
