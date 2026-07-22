"""Milvus 客户端抽象、Mock 实现与本地 Milvus 实现"""
from __future__ import annotations
import abc
import random
from dataclasses import dataclass
from typing import Any
from app.core import config_manager as cfg
from app.core.logging import get_logger

logger = get_logger("milvus_client")


@dataclass
class ChunkHit:
    patent_id: str
    chunk_id: str
    title: str
    inventor: str
    chunk_text: str
    tech_field: str
    publish_date: str
    score: float
    tech_field_embedding: list[float] | None = None


class BaseMilvusClient(abc.ABC):
    @abc.abstractmethod
    async def search(self, vector: list[float], top_k: int) -> list[ChunkHit]:
        ...

    @abc.abstractmethod
    async def health(self) -> bool:
        ...


_MOCK_PATENTS = [
    ("PAT-2024-001", "高温耐磨涂层材料及其制备方法", "张三"),
    ("PAT-2024-002", "基于石墨烯的导热复合材料", "李四"),
    ("PAT-2024-003", "新型氟聚合物改性不粘涂层", "王五"),
    ("PAT-2024-004", "纳米陶瓷增强耐高温涂料", "赵六"),
    ("PAT-2024-005", "低摩擦系数自润滑涂层技术", "孙七"),
    ("PAT-2024-006", "等离子喷涂耐腐蚀涂层工艺", "周八"),
    ("PAT-2024-007", "有机硅改性耐候防护涂层", "吴九"),
    ("PAT-2024-008", "碳纤维增强高强度复合材料", "郑十"),
]


class MockMilvusClient(BaseMilvusClient):
    async def search(self, vector: list[float], top_k: int) -> list[ChunkHit]:
        logger.info("mock_milvus_search top_k=%d", top_k,
                     extra={"request_id": "-", "session_id": "-"})
        hits = []
        for pid, title, inv in _MOCK_PATENTS:
            for chunk_idx in range(random.randint(2, 5)):
                hits.append(ChunkHit(
                    patent_id=pid,
                    chunk_id=f"{pid}-chunk-{chunk_idx}",
                    title=title,
                    inventor=inv,
                    chunk_text="",
                    tech_field="",
                    publish_date="",
                    score=round(random.uniform(0.55, 0.98), 4),
                ))
        hits.sort(key=lambda h: h.score, reverse=True)
        return hits[:top_k]

    async def health(self) -> bool:
        return True


class RemoteMilvusClient(BaseMilvusClient):
    def __init__(self):
        from pymilvus import MilvusClient

        host = cfg.get("milvus.host", "localhost")
        port = cfg.get("milvus.port", 19530)
        self.collection = cfg.get("milvus.collection", "patent_chunks")
        self.client = MilvusClient(uri=f"http://{host}:{port}")

    async def search(self, vector: list[float], top_k: int) -> list[ChunkHit]:
        logger.info("remote_milvus_search collection=%s top_k=%d", self.collection, top_k,
                    extra={"request_id": "-", "session_id": "-"})
        output_fields = [
            "patent_id",
            "chunk_id",
            "title",
            "inventor",
            "chunk_text",
            "tech_field",
            "publish_date",
            "tech_field_embedding",
        ]
        try:
            results = self._search(vector, top_k, output_fields)
        except Exception as exc:
            if "tech_field_embedding" not in str(exc):
                raise
            logger.warning(
                "remote_milvus_missing_tech_field_embedding, fallback_without_vector",
                extra={"request_id": "-", "session_id": "-"},
            )
            results = self._search(vector, top_k, output_fields[:-1])

        hits: list[ChunkHit] = []
        for hit in results[0] if results else []:
            entity = _hit_value(hit, "entity", {}) or {}
            hits.append(ChunkHit(
                patent_id=str(_entity_value(entity, "patent_id", "")),
                chunk_id=str(_entity_value(entity, "chunk_id", "")),
                title=str(_entity_value(entity, "title", "")),
                inventor=str(_entity_value(entity, "inventor", "")),
                chunk_text=str(_entity_value(entity, "chunk_text", "")),
                tech_field=str(_entity_value(entity, "tech_field", "")),
                publish_date=str(_entity_value(entity, "publish_date", "")),
                score=float(_hit_value(hit, "distance", 0.0) or 0.0),
                tech_field_embedding=_vector_value(
                    _entity_value(entity, "tech_field_embedding", None)
                ),
            ))
        return hits

    def _search(
        self,
        vector: list[float],
        top_k: int,
        output_fields: list[str],
    ):
        return self.client.search(
            collection_name=self.collection,
            data=[vector],
            anns_field="embedding",
            limit=top_k,
            output_fields=output_fields,
        )

    async def health(self) -> bool:
        try:
            return bool(self.client.has_collection(self.collection))
        except Exception as e:
            logger.warning("remote_milvus_health_failed err=%s", str(e),
                           extra={"request_id": "-", "session_id": "-"})
            return False


def _hit_value(hit: Any, key: str, default: Any = None) -> Any:
    if isinstance(hit, dict):
        return hit.get(key, default)
    return getattr(hit, key, default)


def _entity_value(entity: Any, key: str, default: Any = None) -> Any:
    if isinstance(entity, dict):
        return entity.get(key, default)
    return getattr(entity, key, default)


def _vector_value(value: Any) -> list[float] | None:
    if value is None:
        return None
    if not isinstance(value, list):
        return None
    try:
        return [float(v) for v in value]
    except (TypeError, ValueError):
        return None


def create_milvus_client() -> BaseMilvusClient:
    provider = cfg.get("milvus.provider", "mock")
    if provider == "mock":
        return MockMilvusClient()
    return RemoteMilvusClient()
