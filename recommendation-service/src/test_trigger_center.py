"""TriggerCenter 与 /internal/v1/triggers/* 端点单测（模拟数据）

覆盖：
    1. 静态触发点分发：三类事件 → 多方消息；未知触发点标记。
    2. 多方沟通：企业需求下权利人消息与企业回执共享 thread_id。
    3. 推送：未配置 Webhook 仅返回；配置后 POST 成功 / 部分失败 / 全部失败。
    4. FastAPI 端点与鉴权。
"""

import httpx
import pytest
from fastapi.testclient import TestClient

import app as app_module
import config
from proactive_service import ProactiveService
from test_proactive_service import (
    COATING_PATENT,
    COATING_USER,
    DEMAND,
    FakeSearchService,
    NO_APPLICANT_PATENT,
)
from trigger_center import TRIGGER_POINTS, TriggerCenter


@pytest.fixture
def center():
    fake = FakeSearchService(results={
        "耐高温涂层": [
            {**COATING_PATENT, "final_score": 0.9},
            {**NO_APPLICANT_PATENT, "final_score": 0.7},
        ],
    })
    return TriggerCenter(proactive=ProactiveService(fake))


# ==================== 1. 触发点分发 ====================

def test_trigger_points_registered():
    assert set(TRIGGER_POINTS) == {
        "new_patent_published", "patent_view_surge", "enterprise_demand_created",
    }
    for meta in TRIGGER_POINTS.values():
        assert meta["description"] and meta["parties"] and meta["channel"]


def test_dispatch_new_patent_published(center):
    result = center.dispatch(events=[{
        "trigger": "new_patent_published",
        "payload": {"users": [COATING_USER], "new_patents": [COATING_PATENT]},
    }])
    assert result["results"][0]["status"] == "matched"
    msg = result["messages"][0]
    assert msg["trigger"] == "new_patent_published"
    assert msg["recipient_type"] == "user"
    assert msg["recipient_id"] == "u1"
    assert msg["channel"] == "ai_chat"
    assert msg["message_id"] and msg["thread_id"]
    assert msg["content"] and msg["patents"]
    assert result["delivery"]["mode"] == "returned"


def test_dispatch_patent_view_surge(center):
    result = center.dispatch(events=[{
        "trigger": "patent_view_surge",
        "payload": {
            "users": [COATING_USER],
            "patent_stats": [{"patent_id": "CN100003", "view_count": 500}],
        },
    }])
    msg = result["messages"][0]
    assert msg["trigger"] == "patent_view_surge"
    assert msg["channel"] == "notification"
    assert msg["recipient_id"] == "u1"


def test_dispatch_enterprise_demand_multiparty(center):
    """一条企业需求 → 每位权利人一条推广 + 企业一条回执，共享 thread_id。"""
    result = center.dispatch(events=[{
        "trigger": "enterprise_demand_created",
        "payload": {"demands": [DEMAND]},
    }])
    messages = result["messages"]
    owners = [m for m in messages if m["recipient_type"] == "patent_owner"]
    enterprises = [m for m in messages if m["recipient_type"] == "enterprise"]
    assert len(owners) == 2  # 华涂新材料 + 钱七（发明人回退）
    assert len(enterprises) == 1
    receipt = enterprises[0]
    assert receipt["recipient_id"] == "e1"
    assert receipt["recipient_name"] == "长风汽车制造有限公司"
    assert "2 位专利权人" in receipt["content"]
    # 多方共享同一 thread
    thread_ids = {m["thread_id"] for m in messages}
    assert len(thread_ids) == 1


def test_dispatch_unknown_trigger(center):
    result = center.dispatch(events=[{"trigger": "no_such_trigger"}])
    assert result["results"][0]["status"] == "unknown_trigger"
    assert result["messages"] == []
    assert result["delivery"]["mode"] == "none"


def test_dispatch_no_match(center):
    result = center.dispatch(events=[{
        "trigger": "new_patent_published",
        "payload": {"users": [], "new_patents": [COATING_PATENT]},
    }])
    assert result["results"][0]["status"] == "no_match"
    assert result["delivery"]["mode"] == "none"


def test_dispatch_multiple_events(center):
    result = center.dispatch(events=[
        {"trigger": "new_patent_published",
         "payload": {"users": [COATING_USER], "new_patents": [COATING_PATENT]}},
        {"trigger": "enterprise_demand_created",
         "payload": {"demands": [DEMAND]}},
    ])
    assert [r["status"] for r in result["results"]] == ["matched", "matched"]
    triggers = {m["trigger"] for m in result["messages"]}
    assert triggers == {"new_patent_published", "enterprise_demand_created"}


# ==================== 2. Webhook 推送 ====================

EVENT = {
    "trigger": "new_patent_published",
    "payload": {"users": [COATING_USER], "new_patents": [COATING_PATENT]},
}


def test_webhook_pushed(center, monkeypatch):
    monkeypatch.setattr(config, "PUSH_WEBHOOK_URL", "https://backend.example.com/hook")
    monkeypatch.setattr(config, "PUSH_WEBHOOK_TOKEN", "hook-token")
    sent = []

    def fake_post(url, json=None, headers=None, timeout=None):
        sent.append({"url": url, "json": json, "headers": headers})
        return httpx.Response(200, request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    result = center.dispatch(events=[EVENT])
    assert result["delivery"] == {"mode": "pushed", "pushed": 1, "failed": 0}
    assert sent[0]["url"] == "https://backend.example.com/hook"
    assert sent[0]["headers"]["x-service-token"] == "hook-token"
    assert sent[0]["json"]["recipient_id"] == "u1"


def test_webhook_failure_keeps_messages(center, monkeypatch):
    monkeypatch.setattr(config, "PUSH_WEBHOOK_URL", "https://backend.example.com/hook")

    def fake_post(url, **kwargs):
        raise httpx.ConnectError("connection refused")

    monkeypatch.setattr(httpx, "post", fake_post)
    result = center.dispatch(events=[EVENT])
    assert result["delivery"]["mode"] == "failed"
    assert result["messages"]  # 投递失败不丢消息，调用方可重试


def test_webhook_partial(center, monkeypatch):
    monkeypatch.setattr(config, "PUSH_WEBHOOK_URL", "https://backend.example.com/hook")
    calls = {"n": 0}

    def fake_post(url, **kwargs):
        calls["n"] += 1
        if calls["n"] == 1:
            return httpx.Response(200, request=httpx.Request("POST", url))
        raise httpx.ConnectError("connection refused")

    monkeypatch.setattr(httpx, "post", fake_post)
    result = center.dispatch(events=[{
        "trigger": "enterprise_demand_created",
        "payload": {"demands": [DEMAND]},
    }])
    assert result["delivery"]["mode"] == "partial"
    assert result["delivery"]["pushed"] == 1
    assert result["delivery"]["failed"] == 2


# ==================== 3. FastAPI 端点与鉴权 ====================

@pytest.fixture
def client(monkeypatch):
    fake = FakeSearchService(results={
        "耐高温涂层": [{**COATING_PATENT, "final_score": 0.9}],
    })
    proactive = ProactiveService(fake)
    monkeypatch.setattr(app_module, "_trigger_center", TriggerCenter(proactive))
    return TestClient(app_module.app)


def test_endpoint_list_triggers(client):
    resp = client.get("/internal/v1/triggers")
    assert resp.status_code == 200
    assert set(resp.json()["triggers"]) == set(TRIGGER_POINTS)


def test_endpoint_dispatch(client):
    resp = client.post("/internal/v1/triggers/dispatch", json={
        "events": [{
            "trigger": "enterprise_demand_created",
            "payload": {"demands": [DEMAND]},
        }],
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["results"][0]["status"] == "matched"
    types = {m["recipient_type"] for m in data["messages"]}
    assert types == {"patent_owner", "enterprise"}
    assert data["delivery"]["mode"] == "returned"


def test_trigger_endpoints_require_token(client, monkeypatch):
    monkeypatch.setattr(config, "SERVICE_TOKEN", "secret")
    resp = client.get("/internal/v1/triggers")
    assert resp.status_code == 401
    resp = client.post("/internal/v1/triggers/dispatch", json={"events": []})
    assert resp.status_code == 401
    resp = client.get("/internal/v1/triggers",
                      headers={"x-service-token": "secret"})
    assert resp.status_code == 200
