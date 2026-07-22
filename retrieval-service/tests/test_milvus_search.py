import numpy as np
import pytest
from pymilvus import MilvusClient
from pymilvus.exceptions import MilvusException

from app.core import config_manager as cfg


def test_configured_milvus_collection_accepts_configured_embedding_dimension():
    host = cfg.get("milvus.host", "localhost")
    port = cfg.get("milvus.port", 19530)
    collection = cfg.get("milvus.collection", "patent_chunks_v2")
    dimension = cfg.get("embedding.dimension", 4096)

    client = MilvusClient(uri=f"http://{host}:{port}")
    try:
        if not client.has_collection(collection):
            pytest.skip(f"Milvus collection does not exist: {collection}")
    except Exception as exc:
        pytest.skip(f"Milvus is not available at {host}:{port}: {exc}")

    rng = np.random.default_rng(42)
    vector = rng.random(int(dimension)).astype(float)
    vector = vector / np.linalg.norm(vector)

    try:
        results = client.search(
            collection_name=collection,
            data=[vector.tolist()],
            anns_field="embedding",
            limit=3,
            output_fields=[
                "patent_id",
                "chunk_id",
                "title",
                "inventor",
                "tech_field",
                "publish_date",
            ],
        )
    except MilvusException as exc:
        pytest.fail(
            f"Milvus search failed for collection={collection}, "
            f"dimension={dimension}: {exc}"
        )

    assert isinstance(results, list)
    assert len(results) == 1
