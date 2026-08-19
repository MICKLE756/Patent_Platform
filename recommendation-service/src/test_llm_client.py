"""LLMClient 与触达文案 LLM 润色单测（模拟数据，不发真实请求）

覆盖：
    1. 未配置 key/base_url/model 时 LLM 关闭，polish 返回 None。
    2. 配置齐备时调用 chat/completions 成功，返回润色文案。
    3. HTTP 错误 / 超时 / 响应结构异常时静默失败返回 None。
    4. ProactiveService 三类触达文案的 LLM 来源与模板回退标记。
"""

import httpx
import pytest

from llm_client import LLMClient
from proactive_service import ProactiveService
from test_proactive_service import COATING_PATENT, COATING_USER, DEMAND, FakeSearchService


def make_client(**kwargs) -> LLMClient:
    defaults = {
        "api_key": "test-key",
        "base_url": "https://llm.example.com/v1",
        "model": "test-model",
        "timeout": 5.0,
    }
    defaults.update(kwargs)
    return LLMClient(**defaults)


# ==================== 1. 启用条件 ====================

def test_disabled_without_config(monkeypatch):
    import config

    monkeypatch.setattr(config, "LLM_API_KEY", "")
    monkeypatch.setattr(config, "LLM_BASE_URL", "")
    monkeypatch.setattr(config, "LLM_MODEL_NAME", "")
    client = LLMClient()
    assert client.enabled is False
    assert client.polish("任意提示") is None


@pytest.mark.parametrize("missing", ["api_key", "base_url", "model"])
def test_disabled_when_any_field_missing(missing, monkeypatch):
    import config

    # 显式清空 config 兜底，模拟该字段既未传参也未配置
    monkeypatch.setattr(config, "LLM_API_KEY", "")
    monkeypatch.setattr(config, "LLM_BASE_URL", "")
    monkeypatch.setattr(config, "LLM_MODEL_NAME", "")
    client = make_client(**{missing: ""})
    assert client.enabled is False


# ==================== 2. 成功调用 ====================

def test_polish_success(monkeypatch):
    captured = {}

    def fake_post(url, headers=None, json=None, timeout=None):
        captured["url"] = url
        captured["headers"] = headers
        captured["json"] = json
        return httpx.Response(200, json={
            "choices": [{"message": {"content": " 「润色后的触达文案」 "}}],
        }, request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    client = make_client()
    text = client.polish("触达场景：测试")
    assert text == "「润色后的触达文案」"
    assert captured["url"] == "https://llm.example.com/v1/chat/completions"
    assert captured["headers"]["Authorization"] == "Bearer test-key"
    assert captured["json"]["model"] == "test-model"
    assert captured["json"]["messages"][1]["content"] == "触达场景：测试"


# ==================== 3. 失败静默回退 ====================

def test_polish_http_error_returns_none(monkeypatch):
    def fake_post(url, **kwargs):
        return httpx.Response(500, json={"error": "boom"},
                              request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    assert make_client().polish("提示") is None


def test_polish_timeout_returns_none(monkeypatch):
    def fake_post(url, **kwargs):
        raise httpx.TimeoutException("timed out")

    monkeypatch.setattr(httpx, "post", fake_post)
    assert make_client().polish("提示") is None


def test_polish_malformed_response_returns_none(monkeypatch):
    def fake_post(url, **kwargs):
        return httpx.Response(200, json={"unexpected": True},
                              request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    assert make_client().polish("提示") is None


def test_polish_empty_content_returns_none(monkeypatch):
    def fake_post(url, **kwargs):
        return httpx.Response(200, json={
            "choices": [{"message": {"content": "  "}}],
        }, request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    assert make_client().polish("提示") is None


# ==================== 4. 触达文案来源标记 ====================

class StubLLM:
    def __init__(self, reply):
        self.reply = reply
        self.prompts = []

    def polish(self, prompt):
        self.prompts.append(prompt)
        return self.reply


def test_conversation_message_source_llm():
    llm = StubLLM("您好，这是 LLM 生成的开场白。")
    service = ProactiveService(FakeSearchService(), llm=llm)
    result = service.initiate_conversations(
        users=[COATING_USER], new_patents=[COATING_PATENT])
    conv = result["conversations"][0]
    assert conv["message_source"] == "llm"
    assert conv["opening_message"] == "您好，这是 LLM 生成的开场白。"
    # prompt 中带有场景与上下文
    assert "触达场景" in llm.prompts[0]
    assert "参考模板" in llm.prompts[0]


def test_conversation_falls_back_to_template():
    service = ProactiveService(FakeSearchService(), llm=StubLLM(None))
    result = service.initiate_conversations(
        users=[COATING_USER], new_patents=[COATING_PATENT])
    conv = result["conversations"][0]
    assert conv["message_source"] == "template"
    assert "耐高温环保涂层" in conv["opening_message"]


def test_hot_push_message_source():
    llm = StubLLM("LLM 推送语")
    service = ProactiveService(FakeSearchService(), llm=llm)
    result = service.push_hot_patents(
        users=[COATING_USER],
        patent_stats=[{"patent_id": "CN100003", "view_count": 500}],
    )
    push = result["pushes"][0]
    assert push["message_source"] == "llm"
    assert push["push_message"] == "LLM 推送语"


def test_promotion_message_source_fallback():
    fake = FakeSearchService(results={
        "耐高温涂层": [{**COATING_PATENT, "final_score": 0.9}],
    })
    service = ProactiveService(fake, llm=StubLLM(None))
    result = service.promote_to_owners(demands=[DEMAND])
    promo = result["promotions"][0]
    assert promo["message_source"] == "template"
    assert "长风汽车制造有限公司" in promo["message"]
