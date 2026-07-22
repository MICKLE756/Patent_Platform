"""Import prepared patent JSON rows into a new Milvus collection.

The script reads rows shaped like data/milvus3.json, embeds each row's
chunk_text as document text, and stores both vectors and display/filter fields
in a fresh Milvus collection.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any

import httpx
import yaml
from pymilvus import DataType, MilvusClient


PROJECT_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = PROJECT_ROOT / "config.yaml"
DEFAULT_DATA_PATH = PROJECT_ROOT / "data" / "milvus3.json"
DEFAULT_COLLECTION = "patent_chunks_v2"
REQUIRED_FIELDS = (
    "patent_id",
    "title",
    "inventor",
    "chunk_text",
    "tech_field",
    "publish_date",
)


def load_config() -> dict[str, Any]:
    if not CONFIG_PATH.exists():
        return {}
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create a new Milvus collection and import embedded patent JSON rows.",
    )
    parser.add_argument(
        "--data",
        type=Path,
        default=DEFAULT_DATA_PATH,
        help="Path to JSON data file. Default: data/milvus3.json",
    )
    parser.add_argument(
        "--collection",
        default=DEFAULT_COLLECTION,
        help=f"Target Milvus collection name. Default: {DEFAULT_COLLECTION}",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=2000,
        help="Maximum rows to import from the JSON file. Default: 2000",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=32,
        help="Embedding and insert batch size. Default: 32",
    )
    return parser.parse_args()


def resolve_settings(config: dict[str, Any]) -> dict[str, Any]:
    embedding_cfg = config.get("embedding", {})
    milvus_cfg = config.get("milvus", {})

    token_env = embedding_cfg.get("token_env", "EMBEDDING_API_TOKEN")
    return {
        "milvus_uri": os.getenv(
            "MILVUS_URI",
            f"http://{milvus_cfg.get('host', 'localhost')}:{milvus_cfg.get('port', 19530)}",
        ),
        "embedding_endpoint": os.getenv(
            "EMBEDDING_ENDPOINT",
            embedding_cfg.get("endpoint", ""),
        ),
        "embedding_token": os.getenv(token_env) or embedding_cfg.get("token", ""),
        "embedding_dimension": int(
            os.getenv("EMBEDDING_DIMENSION", str(embedding_cfg.get("dimension", 4096)))
        ),
        "embedding_timeout": float(embedding_cfg.get("timeout", 60)),
    }


def load_records(data_path: Path, limit: int) -> list[dict[str, Any]]:
    if limit <= 0:
        raise ValueError("--limit must be greater than 0")

    path = data_path if data_path.is_absolute() else PROJECT_ROOT / data_path
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    if not isinstance(data, list):
        raise ValueError(f"JSON root must be a list: {path}")

    records = data[:limit]
    validate_records(records)
    return records


def validate_records(records: list[dict[str, Any]]) -> None:
    for index, record in enumerate(records, start=1):
        if not isinstance(record, dict):
            raise ValueError(f"Row {index} must be an object")

        missing = [field for field in REQUIRED_FIELDS if field not in record]
        if missing:
            patent_id = record.get("patent_id", "<unknown>")
            raise ValueError(f"Row {index} patent_id={patent_id} missing fields: {missing}")


def create_collection(client: MilvusClient, collection_name: str, dimension: int) -> None:
    if client.has_collection(collection_name):
        raise RuntimeError(
            f"Milvus collection already exists: {collection_name}. "
            "Use a different --collection name; this script will not drop existing data."
        )

    schema = MilvusClient.create_schema(
        auto_id=True,
        enable_dynamic_field=False,
    )
    schema.add_field("id", DataType.INT64, is_primary=True)
    schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=dimension)
    schema.add_field("patent_id", DataType.VARCHAR, max_length=64)
    schema.add_field("chunk_id", DataType.VARCHAR, max_length=128)
    schema.add_field("title", DataType.VARCHAR, max_length=512)
    schema.add_field("inventor", DataType.VARCHAR, max_length=2048)
    schema.add_field("chunk_text", DataType.VARCHAR, max_length=8192)
    schema.add_field("tech_field", DataType.VARCHAR, max_length=4096)
    schema.add_field("tech_field_embedding", DataType.FLOAT_VECTOR, dim=dimension)
    schema.add_field("publish_date", DataType.VARCHAR, max_length=32)

    index_params = client.prepare_index_params()
    index_params.add_index(
        field_name="embedding",
        index_type="AUTOINDEX",
        metric_type="COSINE",
    )

    client.create_collection(
        collection_name=collection_name,
        schema=schema,
        index_params=index_params,
    )


def embed_texts(
    texts: list[str],
    endpoint: str,
    token: str,
    timeout: float,
    dimension: int,
) -> list[list[float]]:
    if not endpoint:
        raise RuntimeError("Embedding endpoint is not configured")

    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    response = httpx.post(
        endpoint,
        json={
            "texts": texts,
            "is_query": False,
            "normalize": True,
        },
        headers=headers,
        timeout=timeout,
    )
    response.raise_for_status()
    data = response.json()

    vectors = extract_vectors(data)
    if len(vectors) != len(texts):
        raise RuntimeError(f"Embedding count mismatch: expect={len(texts)} actual={len(vectors)}")
    if vectors and len(vectors[0]) != dimension:
        raise RuntimeError(
            f"Embedding dimension mismatch: config={dimension} actual={len(vectors[0])}"
        )
    return vectors


def extract_vectors(data: dict[str, Any]) -> list[list[float]]:
    if "vectors" in data:
        vectors = data["vectors"]
    elif "data" in data:
        vectors = [item["embedding"] for item in data["data"]]
    else:
        raise RuntimeError("Embedding response missing vectors")

    if not isinstance(vectors, list) or not all(isinstance(vector, list) for vector in vectors):
        raise RuntimeError("Embedding response vectors must be a list of lists")
    return vectors


def build_rows(
    records: list[dict[str, Any]],
    vectors: list[list[float]],
    tech_field_vectors: dict[str, list[float]],
) -> list[dict[str, Any]]:
    rows = []
    for record, vector in zip(records, vectors, strict=True):
        patent_id = str(record["patent_id"])
        tech_field = str(record["tech_field"] or "")
        rows.append({
            "embedding": vector,
            "patent_id": patent_id,
            "chunk_id": f"{patent_id}-chunk-0",
            "title": str(record["title"]),
            "inventor": str(record["inventor"] or ""),
            "chunk_text": str(record["chunk_text"]),
            "tech_field": tech_field,
            "tech_field_embedding": tech_field_vectors[tech_field],
            "publish_date": str(record["publish_date"]),
        })
    return rows


def iter_batches(records: list[dict[str, Any]], batch_size: int):
    if batch_size <= 0:
        raise ValueError("--batch-size must be greater than 0")
    for start in range(0, len(records), batch_size):
        yield start, records[start:start + batch_size]


def print_sample_rows(client: MilvusClient, collection_name: str) -> None:
    sample = client.query(
        collection_name=collection_name,
        filter="",
        output_fields=[
            "patent_id",
            "chunk_id",
            "title",
            "inventor",
            "tech_field",
            "publish_date",
        ],
        limit=5,
    )
    print("sample rows:")
    for row in sample:
        print(row)


def main() -> None:
    args = parse_args()
    config = load_config()
    settings = resolve_settings(config)
    records = load_records(args.data, args.limit)

    print(f"loaded rows: {len(records)} from {args.data}")
    print(f"target collection: {args.collection}")
    print(f"milvus uri: {settings['milvus_uri']}")
    print(f"embedding dimension: {settings['embedding_dimension']}")

    client = MilvusClient(uri=settings["milvus_uri"])
    create_collection(client, args.collection, settings["embedding_dimension"])
    print(f"created collection: {args.collection}")

    inserted = 0
    tech_field_vector_cache: dict[str, list[float]] = {}
    for start, batch in iter_batches(records, args.batch_size):
        texts = [str(record["chunk_text"]) for record in batch]
        vectors = embed_texts(
            texts=texts,
            endpoint=settings["embedding_endpoint"],
            token=settings["embedding_token"],
            timeout=settings["embedding_timeout"],
            dimension=settings["embedding_dimension"],
        )
        missing_tech_fields = [
            tech_field
            for tech_field in dict.fromkeys(str(record["tech_field"] or "") for record in batch)
            if tech_field not in tech_field_vector_cache
        ]
        if missing_tech_fields:
            tech_vectors = embed_texts(
                texts=missing_tech_fields,
                endpoint=settings["embedding_endpoint"],
                token=settings["embedding_token"],
                timeout=settings["embedding_timeout"],
                dimension=settings["embedding_dimension"],
            )
            tech_field_vector_cache.update(zip(missing_tech_fields, tech_vectors, strict=True))

        rows = build_rows(batch, vectors, tech_field_vector_cache)
        client.insert(collection_name=args.collection, data=rows)
        inserted += len(rows)
        print(f"inserted batch rows {start + 1}-{start + len(rows)} / {len(records)}")

    client.flush(collection_name=args.collection)
    stats = client.get_collection_stats(args.collection)
    print(f"import complete: inserted {inserted} rows into {args.collection}")
    print(f"collection stats: {stats}")
    print_sample_rows(client, args.collection)


if __name__ == "__main__":
    main()
