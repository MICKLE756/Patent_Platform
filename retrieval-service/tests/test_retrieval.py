"""检索服务测试套件"""
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.api import routes
from app.clients.embedding_client import MockEmbeddingClient
from app.clients.milvus_client import ChunkHit, MockMilvusClient
from app.clients.rerank_client import MockRerankClient
from app.clients.patent_detail_client import NullPatentDetailClient
from app.core import config_manager as cfg
from app.services import query_normalizer
from app.services.filter_engine import apply_filters
from app.services.ranker import compute_final_scores
from app.evaluation.evaluator import recall_at_k, precision_at_k, mrr, ndcg_at_k
from app.core.models import SearchFilters


@pytest.fixture
def anyio_backend():
    return "asyncio"


@pytest.fixture
async def client(monkeypatch):
    monkeypatch.setattr(routes, "_embedding_client", MockEmbeddingClient())
    monkeypatch.setattr(routes, "_milvus_client", MockMilvusClient())
    monkeypatch.setattr(routes, "_rerank_client", MockRerankClient())
    monkeypatch.setattr(routes, "_patent_detail_client", NullPatentDetailClient())
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c


# ── 1. 检索成功场景 ──

@pytest.mark.anyio
async def test_search_success(client):
    resp = await client.post("/retrieve/search", json={"query": "耐高温涂层材料"})
    assert resp.status_code == 200
    data = resp.json()
    assert "request_id" in data
    assert "normalized_query" in data
    assert "items" in data
    assert data["returned_count"] > 0
    assert data["latency_ms"] > 0
    for item in data["items"]:
        assert set(item.keys()) == {
            "patent_id",
            "title",
            "inventor",
            "tech_field",
            "publish_date",
            "final_score",
            "detail",
        }
        assert isinstance(item["final_score"], (int, float))


@pytest.mark.anyio
async def test_search_with_fields(client):
    resp = await client.post("/retrieve/search", json={
        "query": "不粘锅材料",
        "tech_field": "新材料",
        "core_problem": "耐高温",
        "top_k": 5,
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["returned_count"] <= 5


# ── 2. 规范化场景 ──

def test_normalize_remove_prefix():
    result = query_normalizer.normalize("我想找一种耐高温材料")
    assert "我想找" not in result
    assert "高温耐受" in result  # 同义词归一


def test_normalize_unit():
    result = query_normalizer.normalize("温度超过400摄氏度")
    assert "℃" in result


def test_normalize_synonym():
    result = query_normalizer.normalize("不粘锅涂层")
    assert "不粘涂层" in result


def test_normalize_with_structured_fields():
    result = query_normalizer.normalize(
        "涂层", tech_field="新材料", core_problem="耐高温",
        constraints={"温度": ">400℃"}
    )
    assert "技术领域:新材料" in result
    assert "核心问题:耐高温" in result


class FakeLLMClient:
    async def normalize_query(self, payload):
        return "新材料 高温耐受 不粘涂层 餐饮厨具"


class FailingLLMClient:
    async def normalize_query(self, payload):
        raise RuntimeError("llm unavailable")


class FixedEmbeddingClient:
    def __init__(self):
        self.calls = []

    async def embed(self, texts):
        self.calls.append(texts)
        return [[1.0, 0.0, 0.0] for _ in texts]

    async def health(self):
        return True


class FixedMilvusClient:
    async def search(self, vector, top_k):
        return [
            ChunkHit(
                patent_id="A",
                chunk_id="A-1",
                title="语义分更高的专利",
                inventor="张三",
                chunk_text="新能源汽车热管理结构",
                tech_field="新能源汽车",
                publish_date="2026-01-01",
                score=0.95,
                tech_field_embedding=[1.0, 0.0, 0.0],
            ),
            ChunkHit(
                patent_id="B",
                chunk_id="B-1",
                title="Rerank 更相关的专利",
                inventor="李四",
                chunk_text="电池模块热失控排气通风安全隔离结构",
                tech_field="新能源汽车",
                publish_date="2026-01-01",
                score=0.75,
                tech_field_embedding=[1.0, 0.0, 0.0],
            ),
        ]

    async def health(self):
        return True


class DeterministicRerankClient:
    async def rerank(self, query, documents, top_k):
        return [
            {"patent_id": "B", "rerank_score": 1.0},
            {"patent_id": "A", "rerank_score": 0.0},
        ]

    async def health(self):
        return True


class FailingRerankClient:
    async def rerank(self, query, documents, top_k):
        raise RuntimeError("rerank unavailable")

    async def health(self):
        return False


class FixedPatentDetailClient:
    async def get_details(self, patent_ids):
        return {
            "A": {
                "applicant": "测试申请人",
                "abstract": "测试摘要",
            }
        }

    async def health(self):
        return True


@pytest.mark.anyio
async def test_normalize_with_llm_enabled(monkeypatch):
    monkeypatch.setitem(cfg._config, "llm", {"query_normalization_enabled": True})
    result = await query_normalizer.normalize_with_llm(
        "我想找不粘锅材料",
        tech_field="新材料",
        llm_client=FakeLLMClient(),
    )
    assert result == "新材料 高温耐受 不粘涂层 餐饮厨具"


@pytest.mark.anyio
async def test_normalize_with_llm_fallback(monkeypatch):
    monkeypatch.setitem(cfg._config, "llm", {"query_normalization_enabled": True})
    result = await query_normalizer.normalize_with_llm(
        "我想找不粘锅材料",
        tech_field="新材料",
        llm_client=FailingLLMClient(),
    )
    assert "不粘涂层" in result
    assert "技术领域:新材料" in result


# ── 3. 向量召回聚合场景 ──

@pytest.mark.anyio
async def test_recall_aggregation(client):
    resp = await client.post("/retrieve/search", json={"query": "石墨烯导热"})
    data = resp.json()
    patent_ids = [it["patent_id"] for it in data["items"]]
    assert len(patent_ids) == len(set(patent_ids)), "结果应聚合到 patent_id 维度"


# ── 4. 异常场景 ──

@pytest.mark.anyio
async def test_search_empty_query(client):
    resp = await client.post("/retrieve/search", json={"query": ""})
    assert resp.status_code == 422  # Pydantic validation


@pytest.mark.anyio
async def test_search_top_k_exceeds_max(client):
    resp = await client.post("/retrieve/search", json={"query": "test", "top_k": 50})
    assert resp.status_code == 422


# ── 5. 接口规范场景 (snake_case) ──

@pytest.mark.anyio
async def test_response_snake_case(client):
    resp = await client.post("/retrieve/search", json={"query": "涂层"})
    data = resp.json()
    for key in ["request_id", "normalized_query", "recall_count",
                "filtered_count", "returned_count", "degrade_info",
                "items", "latency_ms"]:
        assert key in data, f"缺少字段: {key}"
    if data["items"]:
        item = data["items"][0]
        for key in ["patent_id", "title", "inventor", "tech_field", "publish_date", "final_score", "detail"]:
            assert key in item, f"item 缺少字段: {key}"
        assert set(item.keys()) == {
            "patent_id",
            "title",
            "inventor",
            "tech_field",
            "publish_date",
            "final_score",
            "detail",
        }


def test_apply_filters_by_metadata():
    items = [
        {
            "patent_id": "A",
            "title": "电池模块排气结构",
            "chunk_text": "新能源汽车电池模块热失控防护和排气通风",
            "tech_field": "新能源汽车；电气设备制造",
            "publish_date": "2026-03-31",
        },
        {
            "patent_id": "B",
            "title": "电子器件散热材料",
            "chunk_text": "电子器件热管理",
            "tech_field": "先进材料",
            "publish_date": "2022-01-01",
        },
    ]

    filtered, degrade = apply_filters(
        items,
        SearchFilters(
            tech_field="新能源汽车",
            publish_year_from=2024,
            application_scene="电池模块",
        ),
    )

    assert not degrade.filter_relaxed
    assert [item["patent_id"] for item in filtered] == ["A"]


def test_apply_filters_relaxes_when_empty():
    items = [
        {
            "patent_id": "A",
            "title": "电池模块排气结构",
            "chunk_text": "新能源汽车电池模块热失控防护和排气通风",
            "tech_field": "新能源汽车",
            "publish_date": "2026-03-31",
        }
    ]

    filtered, degrade = apply_filters(
        items,
        SearchFilters(tech_field="生物医用材料"),
    )

    assert degrade.filter_relaxed
    assert filtered == items


def test_apply_filters_soft_matches_building_material_aliases():
    items = [
        {
            "patent_id": "A",
            "title": "一种建筑外墙保温阻燃聚合物材料",
            "chunk_text": "用于外墙保温系统的隔热阻燃材料",
            "tech_field": "先进无机非金属材料；先进石化化工新材料",
            "publish_date": "2026-03-31",
            "semantic_score": 0.8,
        },
        {
            "patent_id": "B",
            "title": "一种汽车检测装置",
            "chunk_text": "汽车零部件尺寸检测",
            "tech_field": "仪器仪表设备制造",
            "publish_date": "2026-03-31",
            "semantic_score": 0.9,
        },
    ]

    filtered, degrade = apply_filters(
        items,
        SearchFilters(
            tech_field="建筑材料",
            publish_year_from=2018,
            application_scene="外墙保温",
        ),
    )

    assert not degrade.filter_relaxed
    assert [item["patent_id"] for item in filtered] == ["A"]
    assert filtered[0]["metadata_match_score"] > 0


def test_apply_filters_ignores_application_scene_for_now():
    items = [
        {
            "patent_id": "A",
            "title": "温室智能灌溉控制方法",
            "chunk_text": "根据土壤湿度自动控制灌溉",
            "tech_field": "智慧农业",
            "publish_date": "2021-06-01",
            "semantic_score": 0.8,
        }
    ]

    filtered, degrade = apply_filters(
        items,
        SearchFilters(
            tech_field="智慧农业",
            publish_year_from=2019,
            application_scene="完全不匹配的场景",
            maturity="未落库的成熟度",
        ),
    )

    assert not degrade.filter_relaxed
    assert [item["patent_id"] for item in filtered] == ["A"]


def test_apply_filters_uses_tech_field_embedding_similarity():
    items = [
        {
            "patent_id": "A",
            "title": "语义相关技术领域",
            "chunk_text": "",
            "tech_field": "不包含目标词的分类名称",
            "tech_field_embedding": [1.0, 0.0, 0.0],
            "publish_date": "2026-03-31",
            "semantic_score": 0.7,
        },
        {
            "patent_id": "B",
            "title": "语义不相关技术领域",
            "chunk_text": "",
            "tech_field": "另一类分类名称",
            "tech_field_embedding": [-1.0, 0.0, 0.0],
            "publish_date": "2026-03-31",
            "semantic_score": 0.9,
        },
    ]

    filtered, degrade = apply_filters(
        items,
        SearchFilters(tech_field="智慧农业"),
        tech_field_filter_embedding=[1.0, 0.0, 0.0],
    )

    assert not degrade.filter_relaxed
    assert [item["patent_id"] for item in filtered] == ["A"]
    assert filtered[0]["metadata_match_score"] > 0


# ── 6. 健康检查场景 ──

@pytest.mark.anyio
async def test_health(client):
    resp = await client.get("/retrieve/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] in ("ok", "degraded")
    assert "embedding" in data
    assert "vector_store" in data
    assert "rerank" in data
    assert "index_version" in data


# ── 7. 排序模块 ──

def test_ranker_final_score():
    items = [
        {"semantic_score": 0.9, "rerank_score": 0.8, "freshness_score": 0.5, "metadata_match_score": 0.3},
        {"semantic_score": 0.7, "rerank_score": 0.9, "freshness_score": 0.6, "metadata_match_score": 0.4},
    ]
    ranked = compute_final_scores(items)
    assert all("final_score" in it for it in ranked)
    assert ranked[0]["final_score"] >= ranked[1]["final_score"]


def test_ranker_uses_rerank_when_enabled(monkeypatch):
    monkeypatch.setitem(cfg._config, "ranking", {
        "use_rerank": True,
        "weights": {"semantic": 0.7, "rerank": 0.3},
    })
    items = [
        {"patent_id": "A", "semantic_score": 0.95, "rerank_score": 0.0},
        {"patent_id": "B", "semantic_score": 0.75, "rerank_score": 1.0},
    ]

    ranked = compute_final_scores(items, use_rerank=True)

    assert [it["patent_id"] for it in ranked] == ["B", "A"]


def test_ranker_uses_metadata_match_when_configured(monkeypatch):
    monkeypatch.setitem(cfg._config, "ranking", {
        "use_rerank": True,
        "weights": {"semantic": 0.45, "rerank": 0.25, "metadata": 0.3},
    })
    items = [
        {"patent_id": "A", "semantic_score": 0.82, "rerank_score": 0.4, "metadata_match_score": 0.0},
        {"patent_id": "B", "semantic_score": 0.72, "rerank_score": 0.4, "metadata_match_score": 1.0},
    ]

    ranked = compute_final_scores(items, use_rerank=True)

    assert [it["patent_id"] for it in ranked] == ["B", "A"]


@pytest.mark.anyio
async def test_search_rerank_disabled_keeps_semantic_order(client, monkeypatch):
    monkeypatch.setitem(cfg._config, "ranking", {
        "use_rerank": False,
        "weights": {"semantic": 0.7, "rerank": 0.3},
    })
    monkeypatch.setattr(routes, "_embedding_client", FixedEmbeddingClient())
    monkeypatch.setattr(routes, "_milvus_client", FixedMilvusClient())
    monkeypatch.setattr(routes, "_rerank_client", DeterministicRerankClient())

    resp = await client.post("/retrieve/search", json={"query": "电池热失控", "top_k": 2})

    assert resp.status_code == 200
    data = resp.json()
    assert [item["patent_id"] for item in data["items"]] == ["A", "B"]


@pytest.mark.anyio
async def test_search_embeds_filter_tech_field_once(client, monkeypatch):
    embedding_client = FixedEmbeddingClient()
    monkeypatch.setitem(cfg._config, "ranking", {
        "use_rerank": False,
        "weights": {"semantic": 1.0},
    })
    monkeypatch.setattr(routes, "_embedding_client", embedding_client)
    monkeypatch.setattr(routes, "_milvus_client", FixedMilvusClient())

    resp = await client.post("/retrieve/search", json={
        "query": "电池热失控",
        "filters": {"tech_field": "新能源汽车"},
        "top_k": 2,
    })

    assert resp.status_code == 200
    assert len(embedding_client.calls) == 2
    assert "储能电池热失控" in embedding_client.calls[0][0]
    assert embedding_client.calls[1] == ["新能源汽车"]


@pytest.mark.anyio
async def test_search_rerank_enabled_changes_order(client, monkeypatch):
    monkeypatch.setitem(cfg._config, "ranking", {
        "use_rerank": True,
        "weights": {"semantic": 0.7, "rerank": 0.3},
    })
    monkeypatch.setattr(routes, "_embedding_client", FixedEmbeddingClient())
    monkeypatch.setattr(routes, "_milvus_client", FixedMilvusClient())
    monkeypatch.setattr(routes, "_rerank_client", DeterministicRerankClient())

    resp = await client.post("/retrieve/search", json={"query": "电池热失控", "top_k": 2})

    assert resp.status_code == 200
    data = resp.json()
    assert [item["patent_id"] for item in data["items"]] == ["B", "A"]
    assert not data["degrade_info"]["rerank_skipped"]


@pytest.mark.anyio
async def test_search_rerank_failure_degrades(client, monkeypatch):
    monkeypatch.setitem(cfg._config, "ranking", {
        "use_rerank": True,
        "weights": {"semantic": 0.7, "rerank": 0.3},
    })
    monkeypatch.setattr(routes, "_embedding_client", FixedEmbeddingClient())
    monkeypatch.setattr(routes, "_milvus_client", FixedMilvusClient())
    monkeypatch.setattr(routes, "_rerank_client", FailingRerankClient())

    resp = await client.post("/retrieve/search", json={"query": "电池热失控", "top_k": 2})

    assert resp.status_code == 200
    data = resp.json()
    assert data["degrade_info"]["rerank_skipped"]
    assert [item["patent_id"] for item in data["items"]] == ["A", "B"]


@pytest.mark.anyio
async def test_search_attaches_patent_detail(client, monkeypatch):
    monkeypatch.setitem(cfg._config, "ranking", {
        "use_rerank": False,
        "weights": {"semantic": 1.0},
    })
    monkeypatch.setattr(routes, "_embedding_client", FixedEmbeddingClient())
    monkeypatch.setattr(routes, "_milvus_client", FixedMilvusClient())
    monkeypatch.setattr(routes, "_patent_detail_client", FixedPatentDetailClient())

    resp = await client.post("/retrieve/search", json={"query": "电池热失控", "top_k": 2})

    assert resp.status_code == 200
    data = resp.json()
    item_by_id = {item["patent_id"]: item for item in data["items"]}
    assert item_by_id["A"]["detail"]["applicant"] == "测试申请人"
    assert item_by_id["B"]["detail"] == {}


@pytest.mark.anyio
async def test_patent_detail_endpoint(client, monkeypatch):
    monkeypatch.setattr(routes, "_patent_detail_client", FixedPatentDetailClient())

    resp = await client.get("/retrieve/patents/A")

    assert resp.status_code == 200
    data = resp.json()
    assert data["patent_id"] == "A"
    assert data["found"]
    assert data["detail"]["abstract"] == "测试摘要"


@pytest.mark.anyio
async def test_patent_detail_endpoint_not_found(client, monkeypatch):
    monkeypatch.setattr(routes, "_patent_detail_client", FixedPatentDetailClient())

    resp = await client.get("/retrieve/patents/UNKNOWN")

    assert resp.status_code == 200
    data = resp.json()
    assert data["patent_id"] == "UNKNOWN"
    assert not data["found"]
    assert data["detail"] == {}


# ── 8. 评测指标 ──

def test_evaluator_metrics():
    relevant = {"A", "B", "C"}
    retrieved = ["A", "D", "B", "E", "F"]
    assert recall_at_k(relevant, retrieved, 20) == pytest.approx(2 / 3)
    assert precision_at_k(relevant, retrieved, 5) == pytest.approx(2 / 5)
    assert mrr(relevant, retrieved) == pytest.approx(1.0)
    assert ndcg_at_k(relevant, retrieved, 5) > 0
