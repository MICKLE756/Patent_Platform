"""RecommendationService 与 /internal/v1/recommendations 端点单测

覆盖：空画像、纯关键词、纯聊天、混合、检索全空、检索故障降级、
top_k 边界、浏览过的专利排除、端点鉴权。
"""

import pytest
from fastapi.testclient import TestClient

import app as app_module
import config
from recommendation_service import RecommendationService


class FakeSearchService:
    """按 query/tech_domain 返回预置结果的检索桩。"""

    def __init__(self, results=None, error=False):
        self.results = results or {}
        self.error = error
        self.calls = []

    def search(self, tech_domain="", core_problem="", constraints=None,
               query="", session_id="", top_k=10, **kwargs):
        self.calls.append({
            "tech_domain": tech_domain, "core_problem": core_problem,
            "query": query, "top_k": top_k,
        })
        if self.error:
            raise RuntimeError("retrieval down")
        key = query or f"{tech_domain} {core_problem}".strip()
        return self.results.get(key, [])


def _patent(pid, score=0.5, **extra):
    return {"patent_id": pid, "title": f"专利{pid}", "tech_field": "涂层材料",
            "final_score": score, **extra}


# ==================== 空画像 ====================

def test_empty_profile_returns_degraded():
    service = RecommendationService(FakeSearchService())
    result = service.recommend({})
    assert result == {"items": [], "degraded": True}


def test_none_profile_returns_degraded():
    service = RecommendationService(FakeSearchService())
    result = service.recommend(None)
    assert result == {"items": [], "degraded": True}


# ==================== 纯关键词 ====================

def test_keyword_only_profile():
    fake = FakeSearchService({"耐高温涂层": [_patent("CN1", 0.9)]})
    service = RecommendationService(fake)
    result = service.recommend({
        "keywords": [{"keyword": "耐高温涂层", "weight": 2.0}],
    })
    assert not result["degraded"]
    assert len(result["items"]) == 1
    item = result["items"][0]
    assert item["source"] == "keyword"
    assert "耐高温涂层" in item["reason"]
    assert "_reason_weight" not in item


def test_keywords_take_top2_by_weight():
    fake = FakeSearchService()
    service = RecommendationService(fake)
    service.recommend({
        "keywords": [
            {"keyword": "A", "weight": 1.0},
            {"keyword": "B", "weight": 3.0},
            {"keyword": "C", "weight": 2.0},
        ],
    })
    queried = [c["query"] for c in fake.calls]
    assert queried == ["B", "C"]


# ==================== 纯聊天 ====================

def test_chat_only_profile():
    fake = FakeSearchService({"我需要一种耐高温且环保的涂层材料": [_patent("CN2", 0.8)]})
    service = RecommendationService(fake)
    result = service.recommend({
        "chat_queries": ["我需要一种耐高温且环保的涂层材料"],
    })
    item = result["items"][0]
    assert item["source"] == "chat"
    assert item["reason"].startswith("根据您与 AI 的对话「")


def test_slots_query_first_priority():
    fake = FakeSearchService({"涂层材料 提高耐高温性能": [_patent("CN3", 0.7)]})
    service = RecommendationService(fake)
    result = service.recommend({
        "slots": {"tech_field": "涂层材料", "core_problem": "提高耐高温性能",
                  "constraints": {"time_range": "近3年"}},
    })
    assert fake.calls[0]["tech_domain"] == "涂层材料"
    assert fake.calls[0]["core_problem"] == "提高耐高温性能"
    assert result["items"][0]["source"] == "chat"


def test_chat_reason_truncated_to_30_chars():
    long_query = "这" * 40
    fake = FakeSearchService({long_query: [_patent("CN4")]})
    service = RecommendationService(fake)
    result = service.recommend({"chat_queries": [long_query]})
    reason = result["items"][0]["reason"]
    assert "这" * 30 + "…" in reason
    assert "这" * 31 not in reason


# ==================== 混合 ====================

def test_hybrid_source_when_hit_by_keyword_and_chat():
    fake = FakeSearchService({
        "涂层": [_patent("CN5", 0.6)],
        "帮我找环保涂层": [_patent("CN5", 0.9)],
    })
    service = RecommendationService(fake)
    result = service.recommend({
        "keywords": [{"keyword": "涂层", "weight": 2.0}],
        "chat_queries": ["帮我找环保涂层"],
    })
    item = result["items"][0]
    assert item["source"] == "hybrid"
    assert item["reason"] == "综合您的检索与对话历史"
    assert item["final_score"] == 0.9  # 取最高分


def test_max_three_queries():
    fake = FakeSearchService()
    service = RecommendationService(fake)
    service.recommend({
        "slots": {"tech_field": "涂层材料", "core_problem": "耐高温"},
        "keywords": [{"keyword": "A", "weight": 2.0}, {"keyword": "B", "weight": 1.0}],
        "chat_queries": ["帮我找环保涂层"],
    })
    assert len(fake.calls) == 3


# ==================== 降级 ====================

def test_all_queries_empty_results_degraded():
    fake = FakeSearchService()
    service = RecommendationService(fake)
    result = service.recommend({"keywords": [{"keyword": "不存在", "weight": 1.0}]})
    assert result == {"items": [], "degraded": True}


def test_search_error_degrades_gracefully():
    fake = FakeSearchService(error=True)
    service = RecommendationService(fake)
    result = service.recommend({"keywords": [{"keyword": "涂层", "weight": 1.0}]})
    assert result["items"] == []
    assert result["degraded"] is True


# ==================== top_k 边界与排除 ====================

def test_top_k_limits_results():
    patents = [_patent(f"CN{i}", score=1.0 - i * 0.1) for i in range(10)]
    fake = FakeSearchService({"涂层": patents})
    service = RecommendationService(fake)
    result = service.recommend(
        {"keywords": [{"keyword": "涂层", "weight": 1.0}]}, top_k=3)
    assert len(result["items"]) == 3
    scores = [i["final_score"] for i in result["items"]]
    assert scores == sorted(scores, reverse=True)


def test_top_k_defaults_and_clamps():
    assert RecommendationService._clamp_top_k(0) == config.DEFAULT_TOP_K
    assert RecommendationService._clamp_top_k(-1) == config.DEFAULT_TOP_K
    assert RecommendationService._clamp_top_k(999) == config.MAX_TOP_K
    assert RecommendationService._clamp_top_k(1) == 1


def test_viewed_patents_excluded():
    fake = FakeSearchService({"涂层": [_patent("CN1"), _patent("CN2")]})
    service = RecommendationService(fake)
    result = service.recommend({
        "keywords": [{"keyword": "涂层", "weight": 1.0}],
        "viewed_patent_ids": ["CN1"],
    })
    ids = [i["patent_id"] for i in result["items"]]
    assert ids == ["CN2"]


# ==================== FastAPI 端点 ====================

@pytest.fixture
def client(monkeypatch):
    fake = FakeSearchService({"涂层": [_patent("CN1", 0.9)]})
    monkeypatch.setattr(
        app_module, "_recommendation_service", RecommendationService(fake))
    return TestClient(app_module.app)


def test_endpoint_returns_items(client):
    resp = client.post("/internal/v1/recommendations", json={
        "user_id": "12",
        "profile": {"keywords": [{"keyword": "涂层", "weight": 1.0}]},
        "top_k": 6,
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["user_id"] == "12"
    assert data["degraded"] is False
    assert data["items"][0]["patent_id"] == "CN1"


def test_endpoint_empty_profile_degraded(client):
    resp = client.post("/internal/v1/recommendations", json={"user_id": "12"})
    assert resp.status_code == 200
    assert resp.json() == {"user_id": "12", "items": [], "degraded": True}


def test_endpoint_top_k_out_of_range_rejected(client):
    resp = client.post("/internal/v1/recommendations", json={
        "user_id": "12", "top_k": 11,
    })
    assert resp.status_code == 422


def test_endpoint_requires_service_token(client, monkeypatch):
    monkeypatch.setattr(config, "SERVICE_TOKEN", "secret")
    resp = client.post("/internal/v1/recommendations", json={"user_id": "12"})
    assert resp.status_code == 401
    resp = client.post(
        "/internal/v1/recommendations",
        json={"user_id": "12"},
        headers={"x-service-token": "secret"},
    )
    assert resp.status_code == 200
