"""离线入库模块 — 预留骨架

未来接入:
- 成员2 提供的清洗后专利数据 (patent_id, title, abstract, claim_summary, publish_date, inventor)
- 字段字典、切分规则、索引入库字段
- 成员5 提供的画像字段 (tech_tags, maturity, application_scene)

后续目标:
- 同步写入 Milvus
- 同步写入 ES/OpenSearch
- 记录索引版本号
- 记录入库日志
"""
from app.core.logging import get_logger

logger = get_logger("offline.indexer")


async def index_patents(patents: list[dict], version: str = "v0.0.1") -> dict:
    """
    占位入口。后续实现:
    1. 读取清洗后专利数据
    2. 按切分规则切分文本
    3. 调 Embedding 生成向量
    4. 写入 Milvus
    5. 写入 ES/OpenSearch
    6. 记录索引版本号和入库日志
    """
    logger.info("index_patents count=%d version=%s (stub)",
                len(patents), version,
                extra={"request_id": "-", "session_id": "-"})
    return {"status": "stub", "count": len(patents), "version": version}
