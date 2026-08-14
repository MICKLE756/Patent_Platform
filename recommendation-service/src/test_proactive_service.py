"""ProactiveService 与 /internal/v1/proactive/* 端点单测（模拟数据）

覆盖三类主动触达：
    1. 主动发起对话：对话历史命中新专利 / 画像为空 / 无匹配 / 浏览排除 /
       new_patents 缺省时按 publish_date 兜底。
    2. 热点专利推送：高浏览量判定 / 新收录判定 / 兴趣不相关不推送 /
       单用户条数上限 / 无热点降级。
    3. 企业需求推广：按专利权人聚合 / applicant 缺省回退第一发明人 /
       检索故障降级 / 空需求跳过。
    4. FastAPI 端点与鉴权。
"""

from datetime import datetime

import pytest
from fastapi.testclient import TestClient

import app as app_module
import config
from proactive_service import ProactiveService

NOW = datetime(2026, 4, 15)

# ==================== 模拟专利数据 ====================

COATING_PATENT = {
    "patent_id": "CN100001",
    "title": "一种耐高温环保涂层材料",
    "tech_field": "涂层材料",
    "chunk_text": "本发明涉及耐高温涂层，兼顾环保性能",
    "applicant": "华涂新材料股份有限公司",
    "inventor": "张三；李四",
    "publish_date": "2026-04-01",
}

BATTERY_PATENT = {
    "patent_id": "CN100002",
    "title": "一种锂电池热管理系统",
    "tech_field": "电池技术",
    "chunk_text": "本发明涉及锂电池散热与热管理",
    "applicant": "储能动力科技有限公司",
    "inventor": "王五",
    "publish_date": "2026-04-10",
}

OLD_PATENT = {
    "patent_id": "CN100003",
    "title": "一种老式涂层生产设备",
    "tech_field": "涂层材料",
    "chunk_text": "涂层生产线设备",
    "applicant": "旧机装备厂",
    "inventor": "赵六",
    "publish_date": "2020-01-01",
}

NO_APPLICANT_PATENT = {
    "patent_id": "CN100004",
    "title": "一种环保涂层喷涂工艺",
    "tech_field": "涂层材料",
    "chunk_text": "环保涂层喷涂",
    "inventor": "钱七；孙八",
    "publish_date": "2026-03-20",
}

ALL_PATENTS = [COATING_PATENT, BATTERY_PATENT, OLD_PATENT, NO_APPLICANT_PATENT]

# ==================== 模拟用户画像 ====================

COATING_USER = {
    "user_id": "u1",
    "profile": {
        "keywords": [{"keyword": "涂层", "weight": 2.0}],
        "chat_queries": ["我需要一种耐高温且环保的涂层材料"],
        "slots": {"tech_field": "涂层材料", "core_problem": "提高耐高温性能"},
        "viewed_patent_ids": [],
    },
}

BATTERY_USER = {
    "user_id": "u2",
    "profile": {
        "keywords": [{"keyword": "锂电池", "weight": 3.0}],
        "chat_queries": ["帮我找锂电池热管理相关的专利"],
        "slots": {},
        "viewed_patent_ids": [],
    },
}

EMPTY_USER = {"user_id": "u3", "profile": {}}


class FakeSearchService:
    """带本地专利数据与预置检索结果的检索桩。"""

    def __init__(self, patents=None, results=None, error=False):
        self.patents = patents if patents is not None else ALL_PATENTS
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


@pytest.fixture
def service():
    return ProactiveService(FakeSearchService())


# ==================== 1. 主动发起对话 ====================

def test_conversation_initiated_for_interested_user(service):
    result = service.initiate_conversations(
        users=[COATING_USER], new_patents=[COATING_PATENT, BATTERY_PATENT])
    assert not result["degraded"]
    assert len(result["conversations"]) == 1
    conv = result["conversations"][0]
    assert conv["user_id"] == "u1"
    assert conv["trigger"] == "new_patent"
    assert "我需要一种耐高温且环保的涂层材料" in conv["opening_message"]
    assert "耐高温环保涂层" in conv["opening_message"]
    ids = [p["patent_id"] for p in conv["patents"]]
    assert "CN100001" in ids
    assert all(p["reason"] for p in conv["patents"])


def test_no_conversation_for_empty_profile(service):
    result = service.initiate_conversations(
        users=[EMPTY_USER], new_patents=[COATING_PATENT])
    assert result["conversations"] == []


def test_no_conversation_when_nothing_matches(service):
    result = service.initiate_conversations(
        users=[BATTERY_USER], new_patents=[COATING_PATENT])
    assert result["conversations"] == []


def test_conversation_excludes_viewed_patents(service):
    user = {
        "user_id": "u1",
        "profile": {**COATING_USER["profile"], "viewed_patent_ids": ["CN100001"]},
    }
    result = service.initiate_conversations(
        users=[user], new_patents=[COATING_PATENT])
    assert result["conversations"] == []


def test_new_patents_fallback_by_publish_date(service):
    """未传 new_patents 时，按 publish_date 在本地数据里判定新专利。"""
    result = service.initiate_conversations(users=[COATING_USER], now=NOW)
    assert not result["degraded"]
    conv = result["conversations"][0]
    ids = [p["patent_id"] for p in conv["patents"]]
    assert "CN100001" in ids
    assert "CN100003" not in ids  # 2020 年的老专利不算「新出现」


def test_each_user_gets_own_conversation(service):
    result = service.initiate_conversations(
        users=[COATING_USER, BATTERY_USER, EMPTY_USER],
        new_patents=[COATING_PATENT, BATTERY_PATENT],
    )
    by_user = {c["user_id"]: c for c in result["conversations"]}
    assert set(by_user) == {"u1", "u2"}
    assert by_user["u2"]["patents"][0]["patent_id"] == "CN100002"


# ==================== 2. 热点专利推送 ====================

def test_hot_patent_pushed_to_relevant_user(service):
    result = service.push_hot_patents(
        users=[COATING_USER],
        patent_stats=[{"patent_id": "CN100003", "view_count": 500}],
        now=NOW,
    )
    push = result["pushes"][0]
    assert push["user_id"] == "u1"
    assert push["trigger"] == "hot_patent"
    hot_items = [i for i in push["items"] if i["push_type"] == "hot"]
    assert hot_items and hot_items[0]["patent_id"] == "CN100003"
    assert "500 次浏览" in hot_items[0]["reason"]


def test_new_patent_pushed_without_stats(service):
    result = service.push_hot_patents(users=[COATING_USER], now=NOW)
    push = result["pushes"][0]
    assert all(i["push_type"] == "new" for i in push["items"])
    ids = [i["patent_id"] for i in push["items"]]
    assert "CN100001" in ids
    assert "CN100003" not in ids


def test_irrelevant_user_not_pushed(service):
    """兴趣不相关的用户不收到推送（避免打扰）。"""
    result = service.push_hot_patents(
        users=[BATTERY_USER],
        patent_stats=[{"patent_id": "CN100003", "view_count": 500}],
        now=datetime(2030, 1, 1),  # 让所有专利都不算「新收录」
    )
    assert result["pushes"] == []


def test_push_respects_per_user_top_k(service):
    result = service.push_hot_patents(users=[COATING_USER], top_k=1, now=NOW)
    assert len(result["pushes"][0]["items"]) == 1


def test_no_hot_patents_degraded():
    service = ProactiveService(FakeSearchService(patents=[OLD_PATENT]))
    result = service.push_hot_patents(users=[COATING_USER], now=NOW)
    assert result["pushes"] == []
    assert result["degraded"] is True


# ==================== 3. 企业需求 → 专利权人推广 ====================

DEMAND = {
    "enterprise_id": "e1",
    "enterprise_name": "长风汽车制造有限公司",
    "demand": {
        "tech_field": "涂层材料",
        "core_problem": "提高车身涂层耐高温性能",
        "keywords": ["耐高温涂层"],
    },
}


def test_promotion_grouped_by_owner():
    fake = FakeSearchService(results={
        "耐高温涂层": [
            {**COATING_PATENT, "final_score": 0.9},
            {**NO_APPLICANT_PATENT, "final_score": 0.7},
        ],
    })
    service = ProactiveService(fake)
    result = service.promote_to_owners(demands=[DEMAND])
    assert not result["degraded"]
    owners = {p["owner"]: p for p in result["promotions"]}
    assert "华涂新材料股份有限公司" in owners
    assert "钱七" in owners  # 无 applicant 时回退第一发明人
    promo = owners["华涂新材料股份有限公司"]
    assert promo["trigger"] == "enterprise_demand"
    assert promo["enterprise_id"] == "e1"
    assert "长风汽车制造有限公司" in promo["message"]
    assert "耐高温环保涂层" in promo["message"]
    assert promo["patents"][0]["match_score"] == 0.9


def test_promotion_search_error_degrades():
    service = ProactiveService(FakeSearchService(error=True))
    result = service.promote_to_owners(demands=[DEMAND])
    assert result["promotions"] == []
    assert result["degraded"] is True


def test_promotion_empty_demand_skipped(service):
    result = service.promote_to_owners(demands=[
        {"enterprise_id": "e2", "demand": {}},
    ])
    assert result["promotions"] == []
    assert result["degraded"] is True


def test_promotion_no_results_degraded(service):
    result = service.promote_to_owners(demands=[DEMAND])
    assert result["promotions"] == []
    assert result["degraded"] is True


def test_promotion_skips_zero_score_patents():
    """本地检索无命中退回的候选（无 final_score）不向专利权人推广。"""
    fake = FakeSearchService(results={
        "耐高温涂层": [
            {**COATING_PATENT, "final_score": 0.9},
            BATTERY_PATENT,  # 退回候选，无 final_score
        ],
    })
    service = ProactiveService(fake)
    result = service.promote_to_owners(demands=[DEMAND])
    owners = [p["owner"] for p in result["promotions"]]
    assert owners == ["华涂新材料股份有限公司"]


def test_local_search_uses_query_keywords_and_scores():
    """_local_search 应使用 query 关键词评分并附带归一化 final_score。"""
    from patent_search import PatentSearchService

    service = PatentSearchService(patents=ALL_PATENTS)
    results = service.search(query="涂层 环保", top_k=3)
    assert results  # query 命中
    assert results[0]["patent_id"] == "CN100001"  # 两个词都命中，排最前
    assert results[0]["final_score"] == 1.0
    scores = [r["final_score"] for r in results]
    assert scores == sorted(scores, reverse=True)


# ==================== 4. FastAPI 端点与鉴权 ====================

@pytest.fixture
def client(monkeypatch):
    fake = FakeSearchService(results={
        "耐高温涂层": [{**COATING_PATENT, "final_score": 0.9}],
    })
    monkeypatch.setattr(app_module, "_proactive_service", ProactiveService(fake))
    return TestClient(app_module.app)


def test_endpoint_conversations(client):
    resp = client.post("/internal/v1/proactive/conversations", json={
        "users": [COATING_USER],
        "new_patents": [COATING_PATENT],
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["conversations"][0]["user_id"] == "u1"
    assert data["conversations"][0]["opening_message"]


def test_endpoint_hot_patents(client):
    resp = client.post("/internal/v1/proactive/hot-patents", json={
        "users": [COATING_USER],
        "patent_stats": [{"patent_id": "CN100003", "view_count": 500}],
    })
    assert resp.status_code == 200
    pushes = resp.json()["pushes"]
    assert pushes and pushes[0]["user_id"] == "u1"


def test_endpoint_enterprise_demands(client):
    resp = client.post("/internal/v1/proactive/enterprise-demands", json={
        "demands": [DEMAND],
    })
    assert resp.status_code == 200
    promotions = resp.json()["promotions"]
    assert promotions[0]["owner"] == "华涂新材料股份有限公司"


def test_endpoints_require_service_token(client, monkeypatch):
    monkeypatch.setattr(config, "SERVICE_TOKEN", "secret")
    for path in (
        "/internal/v1/proactive/conversations",
        "/internal/v1/proactive/hot-patents",
        "/internal/v1/proactive/enterprise-demands",
    ):
        resp = client.post(path, json={})
        assert resp.status_code == 401
        resp = client.post(path, json={}, headers={"x-service-token": "secret"})
        assert resp.status_code == 200
