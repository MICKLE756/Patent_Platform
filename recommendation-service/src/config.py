import json
import os
from pathlib import Path

from dotenv import load_dotenv

# ==================== 项目根目录 ====================
BASE_DIR = Path(__file__).parent.parent

# override=True: 以项目 .env 为准，避免被系统全局环境变量覆盖
load_dotenv(BASE_DIR / ".env", override=True)

# ==================== 内部服务鉴权 ====================
# 后端调用 /internal/v1/recommendations 时需携带 x-service-token 请求头
SERVICE_TOKEN = os.getenv("SERVICE_TOKEN", "")

# ==================== 检索服务配置 (可选) ====================
# 配置后，专利检索优先调用 retrieval-service；为空时回退到本地 milvus.json。
RETRIEVAL_SERVICE_URL = os.getenv("RETRIEVAL_SERVICE_URL", "").rstrip("/")
RETRIEVAL_SERVICE_TIMEOUT = float(os.getenv("RETRIEVAL_SERVICE_TIMEOUT", "30"))

# ==================== 推荐参数 ====================
DEFAULT_TOP_K = int(os.getenv("RECOMMEND_DEFAULT_TOP_K", "6"))
MAX_TOP_K = int(os.getenv("RECOMMEND_MAX_TOP_K", "10"))
# 每条合成查询的召回条数
PER_QUERY_TOP_K = int(os.getenv("RECOMMEND_PER_QUERY_TOP_K", "10"))
# 合成候选查询上限
MAX_QUERIES = int(os.getenv("RECOMMEND_MAX_QUERIES", "3"))

# ==================== 专利数据 (本地兜底) ====================
# 默认复用 agent-service 的 milvus.json，可通过 PATENT_DATA_PATH 覆盖
_DEFAULT_DATA_PATH = BASE_DIR.parent / "agent-service" / "milvus.json"
_PATENT_DATA_PATH = Path(os.getenv("PATENT_DATA_PATH", str(_DEFAULT_DATA_PATH)))


def _load_patents() -> list[dict]:
    """加载本地专利数据"""
    if _PATENT_DATA_PATH.exists():
        with open(_PATENT_DATA_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return []


PATENT_DATA: list[dict] = _load_patents()
