"""Embedding 客户端抽象、Mock 实现与远程 Qwen 接入"""
from __future__ import annotations
import abc
import os
from typing import Any

import httpx
import numpy as np
from app.core import config_manager as cfg
from app.core.exceptions import EmbeddingError
from app.core.logging import get_logger

logger = get_logger("embedding_client")


class BaseEmbeddingClient(abc.ABC):
    @abc.abstractmethod
    async def embed(self, texts: list[str]) -> list[list[float]]:
        ...

    @abc.abstractmethod
    async def health(self) -> bool:
        ...


class MockEmbeddingClient(BaseEmbeddingClient):
    def __init__(self):
        self.dim = cfg.get("embedding.dimension", 1024)

    async def embed(self, texts: list[str]) -> list[list[float]]:
        logger.info("mock_embed count=%d", len(texts), extra={"request_id": "-", "session_id": "-"})
        rng = np.random.default_rng(42)
        vecs = rng.random((len(texts), self.dim)).astype(float)
        norms = np.linalg.norm(vecs, axis=1, keepdims=True)
        vecs = vecs / norms
        return vecs.tolist()

    async def health(self) -> bool:
        return True


class RemoteEmbeddingClient(BaseEmbeddingClient):
    def __init__(self):
        self.endpoint = cfg.get("embedding.endpoint", "")
        self.model = cfg.get("embedding.model", "")
        self.timeout = cfg.get("embedding.timeout", 10)
        self.dimension = cfg.get("embedding.dimension", 1024)
        self.is_query = cfg.get("embedding.is_query", True)
        self.normalize = cfg.get("embedding.normalize", True)
        token_env = cfg.get("embedding.token_env", "EMBEDDING_API_TOKEN")
        self.api_token = os.getenv(token_env) or cfg.get("embedding.token", "")

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not self.endpoint:
            raise EmbeddingError("Embedding endpoint 未配置")
        if not texts:
            return []

        body = {
            "texts": texts,
            "is_query": self.is_query,
            "normalize": self.normalize,
        }
        headers = {}
        if self.api_token:
            headers["Authorization"] = f"Bearer {self.api_token}"

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.post(self.endpoint, json=body, headers=headers)
                resp.raise_for_status()
                data = resp.json()
        except httpx.HTTPStatusError as exc:
            logger.warning("remote_embedding_http_error status=%s", exc.response.status_code,
                           extra={"request_id": "-", "session_id": "-"})
            raise EmbeddingError(f"Embedding 服务返回 HTTP {exc.response.status_code}") from exc
        except (httpx.HTTPError, ValueError) as exc:
            logger.warning("remote_embedding_failed err=%s", str(exc),
                           extra={"request_id": "-", "session_id": "-"})
            raise EmbeddingError("Embedding 服务调用失败") from exc

        vectors = _extract_vectors(data)
        if len(vectors) != len(texts):
            raise EmbeddingError(f"Embedding 返回数量不匹配: expect={len(texts)} actual={len(vectors)}")
        if vectors and len(vectors[0]) != self.dimension:
            raise EmbeddingError(
                f"Embedding 维度不匹配: config={self.dimension} actual={len(vectors[0])}"
            )

        logger.info("remote_embed count=%d dim=%d model=%s", len(vectors), len(vectors[0]), self.model,
                    extra={"request_id": "-", "session_id": "-"})
        return vectors

    async def health(self) -> bool:
        if not self.endpoint:
            return False
        health_endpoint = cfg.get("embedding.health_endpoint", "")
        if not health_endpoint:
            health_endpoint = self.endpoint.rstrip("/").removesuffix("/embed") + "/health"
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.get(health_endpoint)
            return 200 <= resp.status_code < 300
        except httpx.HTTPError as e:
            logger.warning("remote_embedding_health_failed err=%s", str(e),
                           extra={"request_id": "-", "session_id": "-"})
            return False


def _extract_vectors(data: dict[str, Any]) -> list[list[float]]:
    """兼容 AutoDL /embed 和 OpenAI 风格 embedding 响应。"""
    if "vectors" in data:
        vectors = data["vectors"]
    elif "data" in data:
        vectors = [item["embedding"] for item in data["data"]]
    else:
        raise EmbeddingError("Embedding 响应缺少 vectors")

    if not isinstance(vectors, list) or not all(isinstance(v, list) for v in vectors):
        raise EmbeddingError("Embedding 响应 vectors 格式非法")
    return vectors


def create_embedding_client() -> BaseEmbeddingClient:
    provider = cfg.get("embedding.provider", "mock")
    if provider == "mock":
        return MockEmbeddingClient()
    return RemoteEmbeddingClient()
