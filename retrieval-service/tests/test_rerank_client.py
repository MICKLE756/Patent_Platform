import httpx
import pytest

from app.clients import rerank_client
from app.core import config_manager as cfg
from app.core.exceptions import RerankError


@pytest.fixture
def anyio_backend():
    return "asyncio"


class FakeAsyncClient:
    last_post = {}
    response_json = {"scores": [0.2, 0.9]}
    status_code = 200

    def __init__(self, timeout):
        self.timeout = timeout

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False

    async def post(self, endpoint, json, headers):
        FakeAsyncClient.last_post = {
            "endpoint": endpoint,
            "json": json,
            "headers": headers,
            "timeout": self.timeout,
        }
        return httpx.Response(
            FakeAsyncClient.status_code,
            json=FakeAsyncClient.response_json,
            request=httpx.Request("POST", endpoint),
        )

    async def get(self, endpoint, headers):
        return httpx.Response(
            FakeAsyncClient.status_code,
            json={"status": "ok"},
            request=httpx.Request("GET", endpoint),
        )


def _set_remote_rerank_config(monkeypatch):
    monkeypatch.setitem(cfg._config, "rerank", {
        "provider": "remote",
        "endpoint": "http://rerank.example/v1/rerank",
        "health_endpoint": "http://rerank.example/health",
        "model": "bge-reranker-v2-m3",
        "timeout": 12,
        "token_env": "RERANK_API_TOKEN",
        "request_format": "strings",
    })


@pytest.mark.anyio
async def test_remote_rerank_client_calls_endpoint_with_strings(monkeypatch):
    _set_remote_rerank_config(monkeypatch)
    monkeypatch.setenv("RERANK_API_TOKEN", "secret-token")
    monkeypatch.setattr(rerank_client.httpx, "AsyncClient", FakeAsyncClient)
    FakeAsyncClient.status_code = 200
    FakeAsyncClient.response_json = {"scores": [0.2, 0.9]}

    client = rerank_client.RemoteRerankClient()
    results = await client.rerank(
        "耐高温涂层",
        [
            {"patent_id": "A", "text": "doc A"},
            {"patent_id": "B", "text": "doc B"},
        ],
        top_k=2,
    )

    assert [r["patent_id"] for r in results] == ["B", "A"]
    assert FakeAsyncClient.last_post["endpoint"] == "http://rerank.example/v1/rerank"
    assert FakeAsyncClient.last_post["json"] == {
        "model": "bge-reranker-v2-m3",
        "query": "耐高温涂层",
        "documents": ["doc A", "doc B"],
        "top_k": 2,
    }
    assert FakeAsyncClient.last_post["headers"] == {
        "Authorization": "Bearer secret-token",
    }
    assert FakeAsyncClient.last_post["timeout"] == 12


@pytest.mark.anyio
async def test_remote_rerank_client_parses_results_by_index(monkeypatch):
    _set_remote_rerank_config(monkeypatch)
    monkeypatch.setattr(rerank_client.httpx, "AsyncClient", FakeAsyncClient)
    FakeAsyncClient.status_code = 200
    FakeAsyncClient.response_json = {
        "results": [
            {"index": 1, "relevance_score": 5.0},
            {"index": 0, "relevance_score": 1.0},
        ]
    }

    client = rerank_client.RemoteRerankClient()
    results = await client.rerank(
        "汽车检测",
        [
            {"patent_id": "A", "text": "doc A"},
            {"patent_id": "B", "text": "doc B"},
        ],
        top_k=2,
    )

    assert results == [
        {"patent_id": "B", "rerank_score": 5.0},
        {"patent_id": "A", "rerank_score": 1.0},
    ]


@pytest.mark.anyio
async def test_remote_rerank_client_parses_results_by_patent_id(monkeypatch):
    _set_remote_rerank_config(monkeypatch)
    monkeypatch.setattr(rerank_client.httpx, "AsyncClient", FakeAsyncClient)
    FakeAsyncClient.status_code = 200
    FakeAsyncClient.response_json = {
        "results": [
            {"patent_id": "A", "rerank_score": 0.4},
            {"id": "B", "score": 0.8},
        ]
    }

    client = rerank_client.RemoteRerankClient()
    results = await client.rerank(
        "汽车检测",
        [
            {"patent_id": "A", "text": "doc A"},
            {"patent_id": "B", "text": "doc B"},
        ],
        top_k=2,
    )

    assert [r["patent_id"] for r in results] == ["B", "A"]


@pytest.mark.anyio
async def test_remote_rerank_client_raises_on_http_error(monkeypatch):
    _set_remote_rerank_config(monkeypatch)
    monkeypatch.setattr(rerank_client.httpx, "AsyncClient", FakeAsyncClient)
    FakeAsyncClient.status_code = 500
    FakeAsyncClient.response_json = {"error": "boom"}

    client = rerank_client.RemoteRerankClient()

    with pytest.raises(RerankError):
        await client.rerank("query", [{"patent_id": "A", "text": "doc A"}], top_k=1)


@pytest.mark.anyio
async def test_remote_rerank_client_raises_on_invalid_response(monkeypatch):
    _set_remote_rerank_config(monkeypatch)
    monkeypatch.setattr(rerank_client.httpx, "AsyncClient", FakeAsyncClient)
    FakeAsyncClient.status_code = 200
    FakeAsyncClient.response_json = {"unexpected": []}

    client = rerank_client.RemoteRerankClient()

    with pytest.raises(RerankError):
        await client.rerank("query", [{"patent_id": "A", "text": "doc A"}], top_k=1)
