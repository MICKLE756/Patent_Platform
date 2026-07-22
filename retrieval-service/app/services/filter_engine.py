"""字段过滤模块"""
from __future__ import annotations
from datetime import datetime
import math
import re

from app.core import config_manager as cfg
from app.core.models import SearchFilters, DegradeInfo
from app.core.logging import get_logger

logger = get_logger("filter_engine")


TERM_ALIASES = {
    "建筑材料": (
        "建筑材料", "建筑", "建材", "墙体", "墙面", "外墙", "保温", "隔热", "绝热",
        "无机非金属材料", "非金属材料", "聚合物材料",
    ),
    "外墙保温": (
        "外墙保温", "外墙", "墙体", "墙面", "建筑", "保温", "隔热", "绝热",
    ),
    "新材料": (
        "新材料", "先进材料", "前沿新材料", "先进无机非金属材料",
        "先进石化化工新材料", "材料科学", "高分子",
    ),
    "新能源汽车": (
        "新能源汽车", "新能源车", "电池", "电池包", "电池模块", "热失控",
        "动力电池", "车用",
    ),
}


def apply_filters(
    items: list[dict],
    filters: SearchFilters | None,
    request_id: str = "-",
    session_id: str = "-",
    tech_field_filter_embedding: list[float] | None = None,
) -> tuple[list[dict], DegradeInfo]:
    """
    对召回结果做字段过滤。
    当前仅使用 Milvus 已稳定落库的 publish_date 与 tech_field：
    - publish_year_from：按 publish_date 做年份硬过滤
    - tech_field：按规则匹配 + Embedding 相似度做软匹配，并生成 metadata_match_score
    maturity 与 application_scene 暂不参与过滤，避免因字段未稳定落库误删结果。
    """
    extra = {"request_id": request_id, "session_id": session_id}
    degrade = DegradeInfo()

    if not filters:
        return items, degrade

    hard_filtered = [item for item in items if _matches_hard_filters(item, filters)]
    soft_filter_enabled = bool(filters.tech_field)

    filtered = []
    for item in hard_filtered:
        metadata_score = _metadata_match_score(
            item, filters, tech_field_filter_embedding=tech_field_filter_embedding
        )
        item["metadata_match_score"] = max(
            float(item.get("metadata_match_score", 0.0) or 0.0),
            metadata_score,
        )
        if not soft_filter_enabled or metadata_score > 0.0:
            filtered.append(item)

    if soft_filter_enabled:
        filtered.sort(
            key=lambda x: (
                float(x.get("metadata_match_score", 0.0) or 0.0),
                float(x.get("semantic_score", 0.0) or 0.0),
            ),
            reverse=True,
        )

    # 过滤放宽策略：如果过滤后结果过少，放宽条件
    if len(filtered) == 0 and len(items) > 0:
        logger.warning("filter_relaxed all_filtered, returning original", extra=extra)
        degrade.filter_relaxed = True
        if hard_filtered:
            degrade.reason = "元数据条件未命中，已放宽返回时间过滤后的结果"
            filtered = hard_filtered
        else:
            degrade.reason = "过滤条件过严，已放宽返回原始结果"
            filtered = items

    logger.info("filter_done before=%d after=%d", len(items), len(filtered), extra=extra)
    return filtered, degrade


def _matches_hard_filters(item: dict, filters: SearchFilters) -> bool:
    if filters.publish_year_from is not None:
        publish_year = _parse_year(item.get("publish_date", ""))
        if publish_year is not None and publish_year < filters.publish_year_from:
            return False

    # maturity/application_scene 当前暂不参与过滤，避免因字段未稳定落库误删结果。
    return True


def _metadata_match_score(
    item: dict,
    filters: SearchFilters,
    tech_field_filter_embedding: list[float] | None = None,
) -> float:
    scores = []
    if filters.tech_field:
        scores.append(_tech_field_match_score(
            item,
            filters.tech_field,
            tech_field_filter_embedding,
        ))

    if not scores:
        return 1.0

    return round(sum(scores) / len(scores), 4)


def _tech_field_match_score(
    item: dict,
    expected: str,
    filter_embedding: list[float] | None = None,
) -> float:
    rule_score = _term_match_score(item.get("tech_field", ""), expected)
    embedding_score = _embedding_match_score(
        filter_embedding,
        item.get("tech_field_embedding"),
    )
    if embedding_score is None:
        return rule_score

    rule_weight = _float_cfg("filter.tech_field_rule_weight", 0.4)
    embedding_weight = _float_cfg("filter.tech_field_embedding_weight", 0.6)
    total = rule_weight + embedding_weight
    if total <= 0:
        return embedding_score

    score = (rule_weight * rule_score + embedding_weight * embedding_score) / total
    return round(max(0.0, min(1.0, score)), 4)


def _embedding_match_score(
    filter_embedding: list[float] | None,
    candidate_embedding: object,
) -> float | None:
    candidate = _as_float_vector(candidate_embedding)
    if not filter_embedding or not candidate:
        return None

    cosine = _cosine_similarity(filter_embedding, candidate)
    if cosine is None:
        return None

    # Normalized embeddings usually produce 0..1 for related text, but map defensively.
    return round(max(0.0, min(1.0, (cosine + 1.0) / 2.0)), 4)


def _cosine_similarity(left: list[float], right: list[float]) -> float | None:
    if len(left) != len(right):
        return None

    dot = sum(a * b for a, b in zip(left, right, strict=True))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm == 0.0 or right_norm == 0.0:
        return None
    return dot / (left_norm * right_norm)


def _as_float_vector(value: object) -> list[float] | None:
    if not isinstance(value, list):
        return None
    try:
        return [float(v) for v in value]
    except (TypeError, ValueError):
        return None


def _term_match_score(actual: str, expected: str) -> float:
    actual_text = _normalize_text(actual)
    terms = _expand_terms(expected)
    if not terms:
        return 1.0
    if not actual_text:
        return 0.0

    expected_text = _normalize_text(expected)
    if expected_text and expected_text in actual_text:
        return 1.0

    matched = [term for term in terms if term in actual_text]
    if not matched:
        return 0.0

    target = min(2, len(terms))
    return round(min(1.0, len(matched) / target), 4)


def _expand_terms(expected: str) -> list[str]:
    text = _normalize_text(expected)
    if not text:
        return []

    terms = {text}
    for token in re.split(r"[;；,，、\s/]+", text):
        if token:
            terms.add(token)

    for key, aliases in TERM_ALIASES.items():
        key_text = _normalize_text(key)
        if key_text in text or text in key_text:
            terms.update(_normalize_text(alias) for alias in aliases)

    return sorted((term for term in terms if term), key=len, reverse=True)


def _normalize_text(value: str) -> str:
    return str(value or "").strip().lower()


def _parse_year(value: str) -> int | None:
    text = str(value or "").strip()
    if len(text) < 4:
        return None
    try:
        return datetime.fromisoformat(text).year
    except ValueError:
        pass
    try:
        return int(text[:4])
    except ValueError:
        return None


def _float_cfg(key: str, default: float) -> float:
    try:
        return float(cfg.get(key, default))
    except (TypeError, ValueError):
        return default
