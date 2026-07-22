import os
from pathlib import Path

import yaml
from pymilvus import MilvusClient, DataType


CONFIG_PATH = Path(__file__).resolve().parents[1] / "config.yaml"


def load_config() -> dict:
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


cfg = load_config()
embedding_cfg = cfg.get("embedding", {})
milvus_cfg = cfg.get("milvus", {})

MILVUS_URI = os.getenv(
    "MILVUS_URI",
    f"http://{milvus_cfg.get('host', 'localhost')}:{milvus_cfg.get('port', 19530)}",
)
COLLECTION_NAME = os.getenv(
    "MILVUS_COLLECTION",
    milvus_cfg.get("collection", "patent_chunks"),
)
DIMENSION = int(os.getenv(
    "EMBEDDING_DIMENSION",
    str(embedding_cfg.get("dimension", 4096)),
))

client = MilvusClient(uri=MILVUS_URI)

if client.has_collection(COLLECTION_NAME):
    client.drop_collection(COLLECTION_NAME)
    print(f"dropped existing collection: {COLLECTION_NAME}")

schema = MilvusClient.create_schema(
    auto_id=True,
    enable_dynamic_field=False,
)

schema.add_field("id", DataType.INT64, is_primary=True)
schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=DIMENSION)
schema.add_field("tech_field_embedding", DataType.FLOAT_VECTOR, dim=DIMENSION)
schema.add_field("patent_id", DataType.VARCHAR, max_length=64)
schema.add_field("chunk_id", DataType.VARCHAR, max_length=128)
schema.add_field("title", DataType.VARCHAR, max_length=512)
schema.add_field("inventor", DataType.VARCHAR, max_length=128)
schema.add_field("chunk_text", DataType.VARCHAR, max_length=8192)
schema.add_field("tech_field", DataType.VARCHAR, max_length=4096)
schema.add_field("publish_date", DataType.VARCHAR, max_length=32)

index_params = client.prepare_index_params()
index_params.add_index(
    field_name="embedding",
    index_type="AUTOINDEX",
    metric_type="COSINE",
)

client.create_collection(
    collection_name=COLLECTION_NAME,
    schema=schema,
    index_params=index_params,
)

print(f"created collection: {COLLECTION_NAME}")
print(f"dimension: {DIMENSION}")
print("collections:", client.list_collections())
