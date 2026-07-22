import httpx
import pytest

from app.clients import embedding_client
from app.core import config_manager as cfg


@pytest.fixture
def anyio_backend():
    return "asyncio"


class FakeAsyncClient:
    last_post = {}

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
            200,
            json={"dim": 3, "count": 1, "vectors": [[0.1, 0.2, 0.3]]},
            request=httpx.Request("POST", endpoint),
        )


@pytest.mark.anyio
async def test_remote_embedding_client_calls_autodl_embed(monkeypatch):
    monkeypatch.setenv("EMBEDDING_API_TOKEN", "secret-token")
    monkeypatch.setitem(cfg._config, "embedding", {
        "provider": "remote",
        "endpoint": "http://autodl.example:6006/embed",
        "model": "Qwen3-Embedding-8B",
        "dimension": 3,
        "timeout": 60,
        "token_env": "EMBEDDING_API_TOKEN",
        "is_query": True,
        "normalize": True,
    })
    monkeypatch.setattr(embedding_client.httpx, "AsyncClient", FakeAsyncClient)

    client = embedding_client.RemoteEmbeddingClient()
    vectors = await client.embed(["耐高温涂层"])

    assert vectors == [[0.1, 0.2, 0.3]]
    assert FakeAsyncClient.last_post["endpoint"] == "http://autodl.example:6006/embed"
    assert FakeAsyncClient.last_post["json"] == {
        "texts": ["耐高温涂层"],
        "is_query": True,
        "normalize": True,
    }
    assert FakeAsyncClient.last_post["headers"] == {
        "Authorization": "Bearer secret-token",
    }
    assert FakeAsyncClient.last_post["timeout"] == 60
