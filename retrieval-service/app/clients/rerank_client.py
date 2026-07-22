"""Rerank 客户端抽象与 Mock 实现"""
from __future__ import annotations
import abc
import os
import random
from typing import Any

import httpx

from app.core import config_manager as cfg
from app.core.exceptions import RerankError
from app.core.logging import get_logger

logger = get_logger("rerank_client")


class BaseRerankClient(abc.ABC):
    @abc.abstractmethod
    async def rerank(self, query: str, documents: list[dict], top_k: int) -> list[dict]:
        """返回 [{"patent_id": ..., "rerank_score": ...}, ...]"""
        ...

    @abc.abstractmethod
    async def health(self) -> bool:
        ...


class MockRerankClient(BaseRerankClient):
    async def rerank(self, query: str, documents: list[dict], top_k: int) -> list[dict]:
        logger.info("mock_rerank docs=%d", len(documents),
                     extra={"request_id": "-", "session_id": "-"})
        results = []
        for doc in documents:
            results.append({
                "patent_id": doc["patent_id"],
                "rerank_score": round(random.uniform(0.4, 0.95), 4),
            })
        results.sort(key=lambda x: x["rerank_score"], reverse=True)
        return results[:top_k]

    async def health(self) -> bool:
        return True


class NullRerankClient(BaseRerankClient):
    async def rerank(self, query: str, documents: list[dict], top_k: int) -> list[dict]:
        logger.info("rerank_disabled docs=%d", len(documents),
                    extra={"request_id": "-", "session_id": "-"})
        return []

    async def health(self) -> bool:
        return True


class RemoteRerankClient(BaseRerankClient):
    def __init__(self):
        self.endpoint = cfg.get("rerank.endpoint", "")
        self.health_endpoint = cfg.get("rerank.health_endpoint", "")
        self.model = cfg.get("rerank.model", "")
        self.timeout = cfg.get("rerank.timeout", 10)
        self.request_format = cfg.get("rerank.request_format", "strings")
        token_env = cfg.get("rerank.token_env", "RERANK_API_TOKEN")
        self.api_token = os.getenv(token_env) or cfg.get("rerank.token", "")

    async def rerank(self, query: str, documents: list[dict], top_k: int) -> list[dict]:
        if not self.endpoint:
            raise RerankError("Rerank endpoint 未配置")
        if not documents:
            return []

        body = {
            "query": query,
            "documents": _format_documents(documents, self.request_format),
            "top_k": top_k,
        }
        if self.model:
            body["model"] = self.model

        headers = {}
        if self.api_token:
            headers["Authorization"] = f"Bearer {self.api_token}"

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.post(self.endpoint, json=body, headers=headers)
                resp.raise_for_status()
                data = resp.json()
        except httpx.HTTPStatusError as exc:
            logger.warning("remote_rerank_http_error status=%s", exc.response.status_code,
                           extra={"request_id": "-", "session_id": "-"})
            raise RerankError(f"Rerank 服务返回 HTTP {exc.response.status_code}") from exc
        except (httpx.HTTPError, ValueError) as exc:
            logger.warning("remote_rerank_failed err=%s", str(exc),
                           extra={"request_id": "-", "session_id": "-"})
            raise RerankError("Rerank 服务调用失败") from exc

        results = _extract_rerank_results(data, documents)
        if not results:
            raise RerankError("Rerank 响应缺少有效分数")

        results.sort(key=lambda x: x["rerank_score"], reverse=True)
        logger.info("remote_rerank docs=%d results=%d model=%s",
                    len(documents), len(results), self.model,
                    extra={"request_id": "-", "session_id": "-"})
        return results[:top_k]

    async def health(self) -> bool:
        health_endpoint = self.health_endpoint or _default_health_endpoint(self.endpoint)
        if not health_endpoint:
            return False

        headers = {}
        if self.api_token:
            headers["Authorization"] = f"Bearer {self.api_token}"

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.get(health_endpoint, headers=headers)
            return 200 <= resp.status_code < 300
        except httpx.HTTPError as e:
            logger.warning("remote_rerank_health_failed err=%s", str(e),
                           extra={"request_id": "-", "session_id": "-"})
            return False


def _format_documents(documents: list[dict], request_format: str) -> list[Any]:
    if request_format == "objects":
        return [
            {
                "id": str(doc.get("patent_id", "")),
                "text": str(doc.get("text") or doc.get("title", "")),
            }
            for doc in documents
        ]
    return [str(doc.get("text") or doc.get("title", "")) for doc in documents]


def _extract_rerank_results(data: dict[str, Any], documents: list[dict]) -> list[dict]:
    id_by_index = [str(doc.get("patent_id", "")) for doc in documents]

    if isinstance(data.get("scores"), list):
        return _results_from_scores(data["scores"], id_by_index)

    rows = data.get("results")
    if rows is None:
        rows = data.get("data")
    if isinstance(rows, list):
        return _results_from_rows(rows, id_by_index)

    raise RerankError("Rerank 响应缺少 scores/results/data")


def _results_from_scores(scores: list[Any], id_by_index: list[str]) -> list[dict]:
    results = []
    for idx, score in enumerate(scores):
        if idx >= len(id_by_index):
            break
        value = _to_float(score)
        if value is None:
            continue
        results.append({"patent_id": id_by_index[idx], "rerank_score": value})
    return results


def _results_from_rows(rows: list[Any], id_by_index: list[str]) -> list[dict]:
    results = []
    for row in rows:
        if not isinstance(row, dict):
            continue

        score = _first_float(row, ("rerank_score", "relevance_score", "score"))
        index = _to_int(row.get("index"))
        patent_id = _first_str(row, ("patent_id", "id", "document_id"))

        document = row.get("document")
        if not patent_id and isinstance(document, dict):
            patent_id = _first_str(document, ("patent_id", "id", "document_id"))

        if not patent_id and index is not None and 0 <= index < len(id_by_index):
            patent_id = id_by_index[index]

        if patent_id and score is not None:
            results.append({"patent_id": patent_id, "rerank_score": score})
    return results


def _first_str(row: dict[str, Any], keys: tuple[str, ...]) -> str:
    for key in keys:
        value = row.get(key)
        if value is not None:
            return str(value)
    return ""


def _first_float(row: dict[str, Any], keys: tuple[str, ...]) -> float | None:
    for key in keys:
        value = _to_float(row.get(key))
        if value is not None:
            return value
    return None


def _to_float(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _to_int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _default_health_endpoint(endpoint: str) -> str:
    base = endpoint.rstrip("/")
    if not base:
        return ""
    if base.endswith("/v1/rerank"):
        return base.removesuffix("/v1/rerank") + "/health"
    if base.endswith("/rerank"):
        return base.removesuffix("/rerank") + "/health"
    return base + "/health"


def create_rerank_client() -> BaseRerankClient:
    provider = cfg.get("rerank.provider", "mock")
    if provider == "mock":
        return MockRerankClient()
    if provider == "none":
        return NullRerankClient()
    return RemoteRerankClient()
