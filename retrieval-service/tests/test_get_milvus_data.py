from pymilvus import MilvusClient

client = MilvusClient(uri="http://localhost:19530")

rows = client.query(
    collection_name="patent_chunks",
    filter="",
    output_fields=["patent_id", "chunk_id", "title", "inventor"],
    limit=20,
)

for row in rows:
    print(row)

client.get_collection_stats("patent_chunks")
