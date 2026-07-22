"""检索服务 API 路由"""
from __future__ import annotations
import time
import uuid
from fastapi import APIRouter, Request

from app.core.models import (
    SearchRequest, SearchResponse, PatentItem, DegradeInfo,
    RerankRequest, EvaluateRequest,
    HealthResponse, HealthDependency, PatentDetailResponse,
)
from app.core import config_manager as cfg
from app.core.logging import get_logger
from app.core.exceptions import VectorStoreError
from app.services import query_normalizer, vector_retriever, filter_engine, rerank_engine, ranker
from app.evaluation import evaluator
from app.clients.embedding_client import create_embedding_client
from app.clients.milvus_client import create_milvus_client
from app.clients.rerank_client import create_rerank_client
from app.clients.llm_client import create_llm_client
from app.clients.patent_detail_client import create_patent_detail_client

logger = get_logger("api.routes")
router = APIRouter(prefix="/retrieve")

_embedding_client = create_embedding_client()
_milvus_client = create_milvus_client()
_rerank_client = create_rerank_client()
_llm_client = create_llm_client()
_patent_detail_client = create_patent_detail_client()


@router.post("/search", response_model=SearchResponse)
async def search(req: SearchRequest, http_req: Request):
    start = time.time()
    request_id = getattr(http_req.state, "request_id", str(uuid.uuid4()))
    session_id = req.session_id or "-"
    extra = {"request_id": request_id, "session_id": session_id}

    # 1. Query 规范化
    normalized = await query_normalizer.normalize_with_llm(
        req.query, req.tech_field, req.core_problem, req.constraints,
        _llm_client, request_id=request_id, session_id=session_id,
    )
    logger.info("query_normalized q=%s nq=%s", req.query, normalized, extra=extra)

    # 2-3. Embedding + Milvus 召回
    try:
        recall_items = await vector_retriever.retrieve(
            normalized, _embedding_client, _milvus_client,
            request_id=request_id, session_id=session_id,
        )
    except Exception as e:
        logger.error("vector_retrieve_failed err=%s", str(e), extra=extra)
        raise VectorStoreError(str(e))

    recall_count = len(recall_items)

    # 4. 字段过滤
    tech_field_filter_embedding = await _embed_filter_tech_field(
        req, request_id=request_id, session_id=session_id
    )
    filtered_items, degrade = filter_engine.apply_filters(
        recall_items,
        req.filters,
        request_id,
        session_id,
        tech_field_filter_embedding=tech_field_filter_embedding,
    )
    filtered_count = len(filtered_items)

    # 5. Rerank（异常不中断）
    filtered_items = await rerank_engine.rerank(
        normalized, filtered_items, _rerank_client,
        top_k=req.top_k, request_id=request_id,
        session_id=session_id, degrade_info=degrade,
    )

    # 6. 综合排序
    for it in filtered_items:
        it.setdefault("rerank_score", 0.0)
        it.setdefault("freshness_score", 0.0)
        it.setdefault("metadata_match_score", 0.0)
    use_rerank = bool(cfg.get("ranking.use_rerank", False)) and not degrade.rerank_skipped
    ranked = ranker.compute_final_scores(filtered_items, use_rerank=use_rerank)

    # 7. 截取 top_k
    top_items = ranked[: req.top_k]
    detail_map = await _patent_detail_client.get_details(
        [it["patent_id"] for it in top_items]
    )

    # 构建响应
    items = [
        PatentItem(
            patent_id=it["patent_id"],
            title=it["title"],
            inventor=it.get("inventor", ""),
            tech_field=it.get("tech_field", ""),
            publish_date=it.get("publish_date", ""),
            final_score=it["final_score"],
            detail=detail_map.get(it["patent_id"], {}),
        )
        for it in top_items
    ]

    latency = round((time.time() - start) * 1000, 2)

    logger.info(
        "search_done recall=%d filtered=%d returned=%d latency=%.1fms degrade=%s",
        recall_count, filtered_count, len(items), latency,
        degrade.model_dump_json() if degrade.rerank_skipped or degrade.filter_relaxed else "none",
        extra=extra,
    )

    return SearchResponse(
        request_id=request_id,
        normalized_query=normalized,
        recall_count=recall_count,
        filtered_count=filtered_count,
        returned_count=len(items),
        degrade_info=degrade,
        items=items,
        latency_ms=latency,
    )


async def _embed_filter_tech_field(
    req: SearchRequest,
    request_id: str = "-",
    session_id: str = "-",
) -> list[float] | None:
    if not req.filters or not req.filters.tech_field:
        return None

    extra = {"request_id": request_id, "session_id": session_id}
    try:
        vectors = await _embedding_client.embed([req.filters.tech_field])
    except Exception as exc:
        logger.warning("filter_tech_field_embedding_failed err=%s", str(exc), extra=extra)
        return None

    if not vectors:
        return None

    logger.info("filter_tech_field_embedding_done dim=%d", len(vectors[0]), extra=extra)
    return vectors[0]


@router.get("/patents/{patent_id}", response_model=PatentDetailResponse)
async def patent_detail(patent_id: str):
    details = await _patent_detail_client.get_details([patent_id])
    detail = details.get(patent_id, {})
    return PatentDetailResponse(
        patent_id=patent_id,
        found=bool(detail),
        detail=detail,
    )


@router.get("/health", response_model=HealthResponse)
async def health():
    emb_ok = await _embedding_client.health()
    mil_ok = await _milvus_client.health()
    rer_ok = await _rerank_client.health()
    all_ok = emb_ok and mil_ok and rer_ok

    return HealthResponse(
        status="ok" if all_ok else "degraded",
        embedding=HealthDependency(status="ok" if emb_ok else "unavailable"),
        vector_store=HealthDependency(status="ok" if mil_ok else "unavailable"),
        rerank=HealthDependency(status="ok" if rer_ok else "unavailable"),
        index_version=cfg.get("index.version", "unknown"),
    )


@router.post("/rerank")
async def rerank_endpoint(req: RerankRequest):
    """占位接口 — 后续实现独立 Rerank 调用"""
    return {"status": "stub", "message": "Rerank 独立接口待实现"}


@router.post("/evaluate")
async def evaluate_endpoint(req: EvaluateRequest):
    """占位接口 — 后续接入评测逻辑"""
    result = evaluator.evaluate(req.dataset_path)
    return result
