from __future__ import annotations
from typing import Optional
from pydantic import BaseModel, Field


# ── Request Models ──

class SearchFilters(BaseModel):
    tech_field: Optional[str] = None
    maturity: Optional[str] = None
    publish_year_from: Optional[int] = None
    application_scene: Optional[str] = None


class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, description="检索 query")
    session_id: Optional[str] = None
    tech_field: Optional[str] = None
    core_problem: Optional[str] = None
    constraints: Optional[dict] = None
    filters: Optional[SearchFilters] = None
    sort_preference: Optional[str] = None
    top_k: int = Field(default=10, ge=1, le=20)


class RerankRequest(BaseModel):
    query: str
    patent_ids: list[str]
    top_k: int = 10


class EvaluateRequest(BaseModel):
    dataset_path: str
    metrics: list[str] = Field(default=["recall@20", "precision@5", "mrr", "ndcg@10"])


# ── Response Models ──

class PatentItem(BaseModel):
    patent_id: str
    title: str
    inventor: str
    tech_field: str = ""
    publish_date: str = ""
    final_score: float
    detail: dict = Field(default_factory=dict)


class DegradeInfo(BaseModel):
    rerank_skipped: bool = False
    filter_relaxed: bool = False
    keyword_fallback: bool = False
    reason: str = ""


class SearchResponse(BaseModel):
    request_id: str
    normalized_query: str
    recall_count: int = 0
    filtered_count: int = 0
    returned_count: int = 0
    degrade_info: DegradeInfo = DegradeInfo()
    items: list[PatentItem] = []
    latency_ms: float = 0.0


class PatentDetailResponse(BaseModel):
    patent_id: str
    found: bool
    detail: dict = Field(default_factory=dict)


class HealthDependency(BaseModel):
    status: str
    detail: str = ""


class HealthResponse(BaseModel):
    status: str
    embedding: HealthDependency
    vector_store: HealthDependency
    rerank: HealthDependency
    index_version: str
