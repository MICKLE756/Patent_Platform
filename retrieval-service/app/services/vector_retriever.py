"""向量召回 + 专利聚合模块"""
from __future__ import annotations
from collections import defaultdict
from app.clients.embedding_client import BaseEmbeddingClient
from app.clients.milvus_client import BaseMilvusClient, ChunkHit
from app.core.logging import get_logger
from app.core import config_manager as cfg

logger = get_logger("vector_retriever")


async def retrieve(
    query: str,
    embedding_client: BaseEmbeddingClient,
    milvus_client: BaseMilvusClient,
    request_id: str = "-",
    session_id: str = "-",
) -> list[dict]:
    """
    1. 生成 embedding
    2. 调 Milvus 做 Top-K 片段召回
    3. 聚合为专利级结果，计算 semantic_score
    """
    top_k = cfg.get("search.recall_top_k", 100)
    extra = {"request_id": request_id, "session_id": session_id}

    # embedding
    vectors = await embedding_client.embed([query])
    vector = vectors[0]
    logger.info("embedding_done dim=%d", len(vector), extra=extra)

    # milvus search
    hits: list[ChunkHit] = await milvus_client.search(vector, top_k)
    logger.info("milvus_recall chunks=%d", len(hits), extra=extra)

    # 聚合到 patent_id 维度
    patent_chunks: dict[str, list[ChunkHit]] = defaultdict(list)
    for h in hits:
        patent_chunks[h.patent_id].append(h)

    results = []
    for pid, chunks in patent_chunks.items():
        best = max(chunks, key=lambda c: c.score)
        avg_score = sum(c.score for c in chunks) / len(chunks)
        # semantic_score = 0.6 * max + 0.4 * avg
        semantic_score = round(0.6 * best.score + 0.4 * avg_score, 4)
        results.append({
            "patent_id": pid,
            "title": best.title,
            "inventor": best.inventor,
            "chunk_text": best.chunk_text,
            "tech_field": best.tech_field,
            "tech_field_embedding": best.tech_field_embedding,
            "publish_date": best.publish_date,
            "tech_tags": [],
            "semantic_score": semantic_score,
            "chunk_count": len(chunks),
        })

    results.sort(key=lambda r: r["semantic_score"], reverse=True)
    logger.info("aggregated patents=%d", len(results), extra=extra)
    return results
