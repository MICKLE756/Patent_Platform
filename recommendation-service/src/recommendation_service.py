"""专利智能推荐服务（agent 侧核心逻辑）

输入后端聚合好的用户画像（profile），输出结构化推荐结果：
    1. 合成候选查询（≤3 条）：槽位结构化查询 > 高权重关键词 > 最新对话原文
    2. 逐条检索：复用 ``PatentSearchService.search``（retrieval 调用 + 本地回退）
    3. 合并去重打分：以 patent_id 聚合，final_score 取多条查询命中的最高分
    4. 生成可溯源的推荐理由（源于哪条关键词 / 哪段对话）
    5. 降级：画像为空或检索全空 → items 为空且 degraded=True

规则优先，与 ``query_history_memory.py`` 的路线一致，不依赖 LLM。
agent 保持无状态：只认后端传入的画像，不做用户维度存储。
"""

import logging

import config
from patent_search import PatentSearchService

logger = logging.getLogger(__name__)

# 对话原文在理由文案中的截断长度
CHAT_REASON_MAX_LEN = 30

# 查询来源枚举
SOURCE_KEYWORD = "keyword"
SOURCE_CHAT = "chat"
SOURCE_HYBRID = "hybrid"


class RecommendationService:
    """基于用户画像的专利推荐。"""

    def __init__(self, search_service: PatentSearchService | None = None) -> None:
        self.search_service = search_service or PatentSearchService()

    # ==================== 对外入口 ====================

    def recommend(self, profile: dict, top_k: int = 0, session_id: str = "") -> dict:
        """输入用户画像，输出结构化推荐。

        返回 ``{"items": [...], "degraded": bool}``。
        画像全空时仍正常返回 ``{"items": [], "degraded": True}``，
        由后端走热门专利冷启动兜底。
        """
        top_k = self._clamp_top_k(top_k)
        profile = profile or {}
        queries = self._build_queries(profile)
        if not queries:
            return {"items": [], "degraded": True}

        viewed_ids = set(profile.get("viewed_patent_ids") or [])
        candidates: dict[str, dict] = {}
        degraded = False

        for query in queries:
            try:
                patents = self.search_service.search(
                    tech_domain=query.get("tech_domain", ""),
                    core_problem=query.get("core_problem", ""),
                    constraints=query.get("constraints") or {},
                    query=query.get("query", ""),
                    session_id=session_id,
                    top_k=config.PER_QUERY_TOP_K,
                )
            except Exception:
                logger.exception("[推荐服务] 检索失败，跳过该条查询: %s", query)
                degraded = True
                continue
            self._merge(candidates, patents, query, viewed_ids)

        items = sorted(
            candidates.values(), key=lambda x: x["final_score"], reverse=True
        )[:top_k]
        for item in items:
            item.pop("_reason_weight", None)
        if not items:
            degraded = True
        return {"items": items, "degraded": degraded}

    # ==================== 查询合成 ====================

    def _build_queries(self, profile: dict) -> list[dict]:
        """由画像合成候选查询（≤ MAX_QUERIES 条），按优先级排列并去重。

        每条查询: {source, reason, weight, query/tech_domain/core_problem/constraints}
        """
        queries: list[dict] = []
        seen: set[str] = set()

        def _add(query: dict, key: str) -> None:
            key = key.strip()
            if not key or key in seen or len(queries) >= config.MAX_QUERIES:
                return
            seen.add(key)
            queries.append(query)

        # 1. 槽位结构化查询（最像智能助手会话，放第一位）
        slots = profile.get("slots") or {}
        tech_field = (slots.get("tech_field") or "").strip()
        core_problem = (slots.get("core_problem") or "").strip()
        if tech_field or core_problem:
            _add({
                "source": SOURCE_CHAT,
                "reason": self._chat_reason(core_problem or tech_field),
                "weight": 3.0,
                "tech_domain": tech_field,
                "core_problem": core_problem,
                "constraints": slots.get("constraints") or {},
            }, f"{tech_field} {core_problem}")

        # 2. 权重最高的 2 条检索关键词
        keywords = sorted(
            (kw for kw in (profile.get("keywords") or []) if kw.get("keyword")),
            key=lambda k: float(k.get("weight", 0.0)),
            reverse=True,
        )
        for kw in keywords[:2]:
            keyword = str(kw["keyword"]).strip()
            _add({
                "source": SOURCE_KEYWORD,
                "reason": f"基于您检索过的「{keyword}」",
                "weight": 2.0 + float(kw.get("weight", 0.0)),
                "query": keyword,
            }, keyword)

        # 3. 最新一条对话原文
        chat_queries = [q for q in (profile.get("chat_queries") or []) if q and q.strip()]
        if chat_queries:
            latest = chat_queries[0].strip()
            _add({
                "source": SOURCE_CHAT,
                "reason": self._chat_reason(latest),
                "weight": 1.0,
                "query": latest,
            }, latest)

        return queries

    @staticmethod
    def _chat_reason(chat_query: str) -> str:
        text = chat_query[:CHAT_REASON_MAX_LEN]
        if len(chat_query) > CHAT_REASON_MAX_LEN:
            text += "…"
        return f"根据您与 AI 的对话「{text}」"

    # ==================== 合并去重打分 ====================

    @staticmethod
    def _merge(
        candidates: dict,
        patents: list,
        query: dict,
        viewed_ids: set,
    ) -> None:
        """以 patent_id 聚合命中结果。

        同一专利被多条查询命中时 final_score 取最高分（而非加权和，
        避免多查询同义重复导致分数虚高）；source 按命中来源标记；
        理由取权重最高的那条查询的理由。
        """
        for patent in patents:
            pid = patent.get("patent_id", "")
            if not pid or pid in viewed_ids:
                continue
            score = float(patent.get("final_score", 0.0) or 0.0)
            entry = candidates.get(pid)
            if entry is None:
                candidates[pid] = {
                    "patent_id": pid,
                    "title": patent.get("title", ""),
                    "tech_field": patent.get("tech_field", ""),
                    "applicant": patent.get("applicant", ""),
                    "legal_status": patent.get("legal_status", ""),
                    "validity": patent.get("validity", ""),
                    "abstract": patent.get("abstract", ""),
                    "final_score": score,
                    "reason": query["reason"],
                    "source": query["source"],
                    "_reason_weight": query["weight"],
                }
                continue
            entry["final_score"] = max(entry["final_score"], score)
            if entry["source"] != query["source"]:
                entry["source"] = SOURCE_HYBRID
                entry["reason"] = "综合您的检索与对话历史"
            elif query["weight"] > entry["_reason_weight"]:
                entry["reason"] = query["reason"]
                entry["_reason_weight"] = query["weight"]

    # ==================== 工具 ====================

    @staticmethod
    def _clamp_top_k(top_k: int) -> int:
        if top_k <= 0:
            return config.DEFAULT_TOP_K
        return min(top_k, config.MAX_TOP_K)
