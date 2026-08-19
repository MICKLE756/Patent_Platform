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

# ==================== LLM 配置 (可选，OpenAI 兼容) ====================
# 与 agent-service 约定一致：OPENAI_API_KEY / OPENAI_BASE_URL / MODEL_NAME。
# 三者齐备时启用 LLM 润色触达文案；否则使用规则模板文案。
LLM_API_KEY = os.getenv("OPENAI_API_KEY", "")
LLM_BASE_URL = os.getenv("OPENAI_BASE_URL", "")
LLM_MODEL_NAME = os.getenv("MODEL_NAME", "")
LLM_TIMEOUT = float(os.getenv("LLM_TIMEOUT", "60"))

# ==================== 消息推送 (可选) ====================
# 配置后，触发点匹配产生的多方消息会 POST 到该 Webhook（如后端消息网关）；
# 为空时消息仅随接口返回（delivery=returned），由调用方自行投递。
PUSH_WEBHOOK_URL = os.getenv("PUSH_WEBHOOK_URL", "").rstrip("/")
PUSH_WEBHOOK_TOKEN = os.getenv("PUSH_WEBHOOK_TOKEN", "")
PUSH_TIMEOUT = float(os.getenv("PUSH_TIMEOUT", "10"))

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

# ==================== 主动推荐参数 ====================
# 「新收录专利」判定窗口（天），主动对话与热点推送共用
PROACTIVE_NEW_PATENT_DAYS = int(os.getenv("PROACTIVE_NEW_PATENT_DAYS", "30"))
# 浏览量达到该阈值判定为「热度高」
PROACTIVE_HOT_VIEW_THRESHOLD = int(os.getenv("PROACTIVE_HOT_VIEW_THRESHOLD", "100"))
# 热点候选池大小（先取全平台 Top-N 热点，再与用户兴趣匹配）
PROACTIVE_HOT_POOL_SIZE = int(os.getenv("PROACTIVE_HOT_POOL_SIZE", "50"))
# 画像与专利的最低匹配分（低于该分不触达，避免打扰）
PROACTIVE_MIN_MATCH_SCORE = float(os.getenv("PROACTIVE_MIN_MATCH_SCORE", "1.0"))
# 主动对话每次携带的专利条数
PROACTIVE_CONVERSATION_TOP_K = int(os.getenv("PROACTIVE_CONVERSATION_TOP_K", "3"))
# 热点推送单用户单次条数上限
PROACTIVE_MAX_PUSH_PER_USER = int(os.getenv("PROACTIVE_MAX_PUSH_PER_USER", "5"))
# 企业需求推广：每条需求检索的专利条数
PROACTIVE_PROMOTE_TOP_K = int(os.getenv("PROACTIVE_PROMOTE_TOP_K", "5"))

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
