"""Query 规范化模块"""
import re
from app.clients.llm_client import BaseLLMClient
from app.core import config_manager as cfg
from app.core.logging import get_logger

logger = get_logger("query_normalizer")

_LOW_INFO_PREFIXES = [
    r"^我想(找|要|了解|知道)",
    r"^请(帮我|帮忙)?(找|推荐|搜索)",
    r"^有没有",
    r"^能不能(帮我)?",
    r"^帮我(找|搜|查)",
    r"^麻烦(帮我|帮忙)?(找|推荐|搜索|查)",
    r"^想(找|要|了解|知道)",
    r"^需要(找|搜索|查询|了解)?",
    r"^有没有什么",
    r"^是否有",
]

_LOW_INFO_PHRASES = [
    "相关的", "相关", "有没有", "请问", "麻烦", "帮我", "帮忙",
    "我想", "想要", "想找", "最好是", "有没有比较好的", "推荐一下",
]

_PUNCTUATION_MAP = {
    "，": " ", "。": " ", "、": " ", "；": " ", "：": ":",
    "？": " ", "！": " ", "（": " ", "）": " ", "【": " ", "】": " ",
    "“": " ", "”": " ", "‘": " ", "’": " ", ",": " ", ";": " ",
    "?": " ", "!": " ", "(": " ", ")": " ", "[": " ", "]": " ",
}

_UNIT_MAP = {
    "摄氏度": "℃", "华氏度": "℉", "度": "℃", "°C": "℃", "℃C": "℃",
    "千克": "kg", "公斤": "kg", "克": "g", "吨": "t",
    "毫米": "mm", "厘米": "cm", "微米": "μm", "纳米": "nm", "米": "m",
    "兆帕": "MPa", "千帕": "kPa", "帕": "Pa",
    "瓦每米开尔文": "W/m·K", "瓦/米·开": "W/m·K",
    "小时": "h", "分钟": "min", "秒": "s",
    "转每分钟": "rpm", "转/分钟": "rpm",
}

_SYNONYM_MAP = {
    "不粘锅": "不粘涂层",
    "耐高温": "高温耐受",
    "抗高温": "高温耐受",
    "高温稳定": "高温稳定性",
    "防腐蚀": "耐腐蚀",
    "抗腐蚀": "耐腐蚀",
    "防锈": "耐腐蚀",
    "导热": "热传导",
    "散热": "热管理",
    "轻量化": "轻量",
    "耐磨损": "耐磨",
    "抗磨损": "耐磨",
    "防水": "疏水",
    "拒水": "疏水",
    "防油": "疏油",
    "抗菌": "抑菌",
    "阻燃": "防火阻燃",
    "隔热": "热绝缘",
    "保温": "热绝缘",
    "自清洁": "易清洁",
    "环保": "低污染",
    "绿色": "低污染",
    "量产": "规模化生产",
    "大规模生产": "规模化生产",
    "产业化": "规模化生产",
    "成本低": "低成本",
    "便宜": "低成本",
    "寿命长": "长寿命",
    "稳定": "稳定性",
    "可靠": "可靠性",
    "粘接": "附着力",
    "粘附": "附着力",
    "强度高": "高强度",
    "柔性": "柔韧性",
    "透明": "透光性",
    "电池": "储能电池",
    "锂电": "锂离子电池",
    "光伏": "太阳能光伏",
    "芯片": "半导体芯片",
    "传感器": "传感检测",
}

_CONSTRAINT_KEY_MAP = {
    "temperature": "温度",
    "temp": "温度",
    "application": "应用场景",
    "scene": "应用场景",
    "material": "材料",
    "maturity": "成熟度",
    "year": "年份",
    "cost": "成本",
    "strength": "强度",
    "weight": "重量",
    "conductivity": "导电性",
    "thermal_conductivity": "热传导",
}

_EXPANSION_MAP = {
    "高温耐受": ["耐热", "热稳定性"],
    "耐腐蚀": ["防腐", "化学稳定性"],
    "热传导": ["导热系数", "热管理"],
    "不粘涂层": ["低表面能", "耐磨涂层"],
    "轻量": ["低密度", "高比强度"],
    "储能电池": ["电极材料", "电解液"],
    "半导体芯片": ["封装", "制程"],
}


def _normalize_punctuation(text: str) -> str:
    for src, dst in _PUNCTUATION_MAP.items():
        text = text.replace(src, dst)
    return text


def _remove_low_info_phrases(text: str) -> str:
    for phrase in _LOW_INFO_PHRASES:
        text = text.replace(phrase, " ")
    return text


def _normalize_units(text: str) -> str:
    for src, dst in _UNIT_MAP.items():
        text = text.replace(src, dst)
    text = re.sub(r"(\d+(?:\.\d+)?)\s*(℃|℉|kg|g|t|mm|cm|μm|nm|m|MPa|kPa|Pa|h|min|s|rpm)", r"\1\2", text)
    return text


def _normalize_synonyms(text: str) -> str:
    for src, dst in _SYNONYM_MAP.items():
        text = text.replace(src, dst)
    return text


def _append_expansions(parts: list[str], text: str) -> None:
    expansions = []
    for keyword, related_terms in _EXPANSION_MAP.items():
        if keyword in text:
            expansions.extend(related_terms)
    if expansions:
        parts.append("扩展词:" + " ".join(dict.fromkeys(expansions)))


def _format_constraint_key(key: str) -> str:
    return _CONSTRAINT_KEY_MAP.get(key, key)


def _compact_spaces(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def normalize(query: str, tech_field: str | None = None,
              core_problem: str | None = None,
              constraints: dict | None = None) -> str:
    text = query.strip()

    # 去除低信息量前缀
    for pat in _LOW_INFO_PREFIXES:
        text = re.sub(pat, "", text)
    text = text.strip()

    # 标点和低信息短语清理
    text = _normalize_punctuation(text)
    text = _remove_low_info_phrases(text)

    # 单位统一
    text = _normalize_units(text)

    # 同义词归一
    text = _normalize_synonyms(text)
    text = _compact_spaces(text)

    # 拼接结构化字段
    parts = [text]
    if tech_field:
        parts.append(f"技术领域:{tech_field}")
    if core_problem:
        parts.append(f"核心问题:{core_problem}")
    if constraints:
        for k, v in constraints.items():
            parts.append(f"{_format_constraint_key(k)}:{v}")

    _append_expansions(parts, text)

    return _compact_spaces(" ".join(parts))


async def normalize_with_llm(
    query: str,
    tech_field: str | None = None,
    core_problem: str | None = None,
    constraints: dict | None = None,
    llm_client: BaseLLMClient | None = None,
    request_id: str = "-",
    session_id: str = "-",
) -> str:
    """
    Query 规范化增强入口。
    默认先执行本地规则；当 llm.query_normalization_enabled=true 且传入 LLM 客户端时，
    使用 LLM 进一步改写。LLM 异常时自动降级为本地规则结果。
    """
    rule_based = normalize(query, tech_field, core_problem, constraints)
    if not cfg.get("llm.query_normalization_enabled", False) or llm_client is None:
        return rule_based

    payload = {
        "query": query,
        "rule_based_query": rule_based,
        "tech_field": tech_field,
        "core_problem": core_problem,
        "constraints": constraints or {},
    }
    extra = {"request_id": request_id, "session_id": session_id}

    try:
        normalized = await llm_client.normalize_query(payload)
        logger.info("llm_normalize_done nq=%s", normalized, extra=extra)
        return normalized
    except Exception as e:
        logger.warning("llm_normalize_failed err=%s, fallback_rule_based", str(e), extra=extra)
        return rule_based
