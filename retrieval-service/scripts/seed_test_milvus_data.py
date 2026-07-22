import os
from pathlib import Path

import httpx
import yaml
from pymilvus import MilvusClient


CONFIG_PATH = Path(__file__).resolve().parents[1] / "config.yaml"


def load_config() -> dict:
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


cfg = load_config()
embedding_cfg = cfg.get("embedding", {})
milvus_cfg = cfg.get("milvus", {})

EMBEDDING_ENDPOINT = os.getenv(
    "EMBEDDING_ENDPOINT",
    embedding_cfg.get("endpoint", "http://127.0.0.1:6006/embed"),
)
EMBEDDING_TOKEN = (
    os.getenv(embedding_cfg.get("token_env", "EMBEDDING_API_TOKEN"))
    or embedding_cfg.get("token", "")
)
EMBEDDING_DIMENSION = int(os.getenv(
    "EMBEDDING_DIMENSION",
    str(embedding_cfg.get("dimension", 4096)),
))
EMBEDDING_TIMEOUT = float(embedding_cfg.get("timeout", 60))

MILVUS_URI = os.getenv(
    "MILVUS_URI",
    f"http://{milvus_cfg.get('host', 'localhost')}:{milvus_cfg.get('port', 19530)}",
)
COLLECTION_NAME = os.getenv(
    "MILVUS_COLLECTION",
    milvus_cfg.get("collection", "patent_chunks"),
)


TEST_PATENTS = [
    {
        "patent_id": "PAT-QWEN-001",
        "direction": "高温防护涂层",
        "title": "高温耐磨陶瓷复合涂层材料及其制备方法",
        "inventor": "张三",
        "text": "一种用于航空发动机和高温模具表面的陶瓷复合涂层，包含氧化铝、碳化硅和稀土改性粘结相，具有耐高温、耐磨损和抗热震性能。",
    },
    {
        "patent_id": "PAT-QWEN-002",
        "direction": "高温防护涂层",
        "title": "耐盐雾海洋防腐重防护涂料",
        "inventor": "李四",
        "text": "本发明涉及海洋工程钢结构防腐涂料，采用片状锌粉、环氧树脂和屏蔽填料形成长效防护层，提高耐盐雾、耐腐蚀和耐冲刷性能。",
    },
    {
        "patent_id": "PAT-QWEN-003",
        "direction": "热管理导热材料",
        "title": "基于石墨烯的高导热复合散热材料",
        "inventor": "王五",
        "text": "本发明公开一种石墨烯增强聚合物复合材料，通过片层取向和界面改性提升热传导效率，适用于电子器件热管理和电池散热结构。",
    },
    {
        "patent_id": "PAT-QWEN-004",
        "direction": "热管理导热材料",
        "title": "柔性透明导电散热薄膜及触控器件",
        "inventor": "赵六",
        "text": "一种银纳米线与导电聚合物复合的柔性透明薄膜，兼具低方阻、高透光率、反复弯折稳定性和面内导热能力，适用于柔性触控屏与可穿戴散热器件。",
    },
    {
        "patent_id": "PAT-QWEN-005",
        "direction": "锂电池材料",
        "title": "锂电池硅碳负极材料的预锂化处理工艺",
        "inventor": "孙七",
        "text": "本技术通过可控预锂化和弹性包覆层改善硅碳负极首次库伦效率，缓解循环膨胀，提升动力电池能量密度和寿命。",
    },
    {
        "patent_id": "PAT-QWEN-006",
        "direction": "锂电池材料",
        "title": "动力电池用阻燃凝胶电解质及制备方法",
        "inventor": "周八",
        "text": "一种含磷阻燃剂和聚合物网络的凝胶电解质，用于锂离子动力电池，能够提升离子电导率、抑制热失控并改善高温循环安全性。",
    },
    {
        "patent_id": "PAT-QWEN-007",
        "direction": "环保降解与碳捕集",
        "title": "可降解聚乳酸增韧改性母粒",
        "inventor": "吴九",
        "text": "通过生物基弹性体和相容剂对聚乳酸进行增韧改性，改善薄膜和注塑制品的抗冲击性能，同时保持可降解特性。",
    },
    {
        "patent_id": "PAT-QWEN-008",
        "direction": "环保降解与碳捕集",
        "title": "高选择性二氧化碳吸附分离膜材料",
        "inventor": "郑十",
        "text": "一种含胺基功能化微孔聚合物膜，用于烟气中二氧化碳捕集，具有高选择性、高通量和抗水汽干扰能力，适合工业尾气减排。",
    },
    {
        "patent_id": "PAT-QWEN-009",
        "direction": "生物医用抗菌材料",
        "title": "抗菌医用水凝胶敷料及其制备方法",
        "inventor": "钱一",
        "text": "一种含银离子缓释体系和多糖网络的医用水凝胶敷料，具有吸湿保湿、抗菌、促进创面修复和良好生物相容性。",
    },
    {
        "patent_id": "PAT-QWEN-010",
        "direction": "生物医用抗菌材料",
        "title": "可注射抗菌骨修复复合水凝胶",
        "inventor": "冯二",
        "text": "一种用于骨缺损填充的可注射复合水凝胶，含羟基磷灰石、壳聚糖和抗菌肽，具备原位成胶、抗菌、促成骨和良好生物相容性。",
    },
]


def embed_texts(texts: list[str]) -> list[list[float]]:
    if "<your-autodl-host>" in EMBEDDING_ENDPOINT:
        raise RuntimeError("请先在 config.yaml 或 EMBEDDING_ENDPOINT 中配置真实 AutoDL embedding 地址")
    if not EMBEDDING_TOKEN:
        raise RuntimeError("请先设置 EMBEDDING_API_TOKEN 环境变量")

    headers = {"Authorization": f"Bearer {EMBEDDING_TOKEN}"}
    payload = {
        "texts": texts,
        "is_query": False,
        "normalize": True,
    }
    resp = httpx.post(
        EMBEDDING_ENDPOINT,
        json=payload,
        headers=headers,
        timeout=EMBEDDING_TIMEOUT,
    )
    resp.raise_for_status()
    data = resp.json()
    vectors = data.get("vectors")
    if not isinstance(vectors, list) or len(vectors) != len(texts):
        raise RuntimeError(f"Embedding 返回数量异常: {data}")
    if vectors and len(vectors[0]) != EMBEDDING_DIMENSION:
        raise RuntimeError(
            f"Embedding 维度不匹配: config={EMBEDDING_DIMENSION}, actual={len(vectors[0])}"
        )
    return vectors


def main() -> None:
    texts = [f"{item['title']}。{item['text']}" for item in TEST_PATENTS]
    vectors = embed_texts(texts)
    tech_fields = [item["direction"] for item in TEST_PATENTS]
    tech_field_vectors = embed_texts(tech_fields)

    rows = []
    for item, vector, tech_field_vector in zip(TEST_PATENTS, vectors, tech_field_vectors, strict=True):
        rows.append({
            "embedding": vector,
            "tech_field_embedding": tech_field_vector,
            "patent_id": item["patent_id"],
            "chunk_id": f"{item['patent_id']}-chunk-0",
            "title": item["title"],
            "inventor": item["inventor"],
            "chunk_text": item["text"],
            "tech_field": item["direction"],
            "publish_date": "2026-01-01",
        })

    client = MilvusClient(uri=MILVUS_URI)
    if not client.has_collection(COLLECTION_NAME):
        raise RuntimeError(f"Milvus collection 不存在: {COLLECTION_NAME}")

    result = client.insert(collection_name=COLLECTION_NAME, data=rows)
    client.flush(collection_name=COLLECTION_NAME)
    print(f"inserted {len(rows)} rows into {COLLECTION_NAME}: {result}")

    print("seed directions:")
    for item in TEST_PATENTS:
        print(f"{item['direction']} | {item['patent_id']} | {item['title']}")

    sample = client.query(
        collection_name=COLLECTION_NAME,
        filter="",
        output_fields=["patent_id", "chunk_id", "title", "inventor", "tech_field", "publish_date"],
        limit=10,
    )
    print("sample rows:")
    for row in sample:
        print(row)


if __name__ == "__main__":
    main()
