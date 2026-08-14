"""专利主动推荐服务（agent 侧核心逻辑）

在被动推荐（主页「为您推荐」）之外，提供三类**主动触达**能力：
    1. 主动发起对话：根据用户对话历史/画像，发现新出现的、用户可能感兴趣的
       专利时，生成开场白与推荐卡片，由后端投递到用户的 AI 会话中。
    2. 热点专利推送：对新收录 / 热度高（浏览量高）的专利，匹配兴趣相关的
       用户并生成推送文案。
    3. 企业需求推广：对企业提出的技术需求，检索匹配的专利并按专利权人聚合，
       生成向专利权人推广的触达文案。

与被动推荐一致的约定：
    - agent 保持无状态：画像、专利统计、企业需求均由后端聚合后传入，
      本服务只做匹配、打分与文案生成，不做用户维度存储。
    - 返回 snake_case，理由/文案可读、可溯源（源于哪条关键词 / 哪段对话 /
      哪条企业需求）。
    - 规则优先，不依赖 LLM。
"""

import logging
import re
from datetime import datetime
from typing import Optional

import config
from patent_search import PatentSearchService

logger = logging.getLogger(__name__)

# 文案中对话原文 / 需求描述的截断长度
REASON_MAX_LEN = 30

# 触达类型枚举
TRIGGER_NEW_PATENT = "new_patent"        # 新收录专利命中用户兴趣
TRIGGER_HOT_PATENT = "hot_patent"        # 高热度专利命中用户兴趣
TRIGGER_ENTERPRISE_DEMAND = "enterprise_demand"  # 企业需求命中专利权人

# 中文分词兜底：按标点/空白切开后再取 2~8 字的片段
_SPLIT_PATTERN = re.compile(r"[，。！？、；：,.!?;:\s]+")


def _truncate(text: str, max_len: int = REASON_MAX_LEN) -> str:
    text = (text or "").strip()
    if len(text) <= max_len:
        return text
    return text[:max_len] + "…"


def _patent_text_pool(patent: dict) -> str:
    """拼接专利可匹配文本（标题 + 领域 + 摘要/正文）。"""
    detail = patent.get("detail") or {}
    return " ".join((
        patent.get("title", ""),
        patent.get("tech_field", ""),
        patent.get("abstract", "") or detail.get("abstract", ""),
        patent.get("chunk_text", ""),
    ))


def _patent_card(patent: dict) -> dict:
    """统一的专利卡片字段（与被动推荐 items 对齐）。"""
    detail = patent.get("detail") or {}
    return {
        "patent_id": patent.get("patent_id", ""),
        "title": patent.get("title", ""),
        "tech_field": patent.get("tech_field", ""),
        "applicant": patent.get("applicant", "") or detail.get("applicant", ""),
        "legal_status": patent.get("legal_status", "") or detail.get("legal_status", ""),
        "validity": patent.get("validity", "") or detail.get("validity", ""),
        "abstract": patent.get("abstract", "") or detail.get("abstract", ""),
        "publish_date": patent.get("publish_date", ""),
    }


def _publish_date(patent: dict) -> Optional[datetime]:
    raw = (patent.get("publish_date") or "").strip()
    for fmt in ("%Y-%m-%d", "%Y/%m/%d", "%Y%m%d"):
        try:
            return datetime.strptime(raw, fmt)
        except ValueError:
            continue
    return None


class InterestProfile:
    """从后端传入的用户画像提取兴趣词（带权重），用于与专利文本匹配。"""

    def __init__(self, profile: dict) -> None:
        profile = profile or {}
        self.terms: list[tuple[str, float, str]] = []  # (词, 权重, 溯源文案)
        self.chat_queries = [
            q.strip() for q in (profile.get("chat_queries") or []) if q and q.strip()
        ]
        self.viewed_patent_ids = set(profile.get("viewed_patent_ids") or [])

        # 1. 检索关键词（权重最高：显式意图）
        for kw in profile.get("keywords") or []:
            keyword = str(kw.get("keyword", "")).strip()
            if keyword:
                weight = 2.0 + float(kw.get("weight", 0.0))
                self.terms.append(
                    (keyword, weight, f"与您检索过的「{keyword}」相关"))

        # 2. 对话槽位（tech_field / core_problem 拆词）
        slots = profile.get("slots") or {}
        for field, base in (("tech_field", 2.0), ("core_problem", 1.5)):
            value = (slots.get(field) or "").strip()
            for token in self._tokenize(value):
                self.terms.append((
                    token, base,
                    f"与您和 AI 对话中提到的「{_truncate(value)}」相关",
                ))

        # 3. 对话原文拆词（权重最低：泛化意图）
        for query in self.chat_queries[:3]:
            for token in self._tokenize(query):
                self.terms.append((
                    token, 1.0,
                    f"与您和 AI 的对话「{_truncate(query)}」相关",
                ))

    @property
    def empty(self) -> bool:
        return not self.terms

    @staticmethod
    def _tokenize(text: str) -> list[str]:
        """按标点切分并保留 2~8 字片段（无分词依赖的轻量方案）。"""
        tokens = []
        for part in _SPLIT_PATTERN.split(text or ""):
            part = part.strip()
            if 2 <= len(part) <= 8:
                tokens.append(part)
        return tokens

    def match(self, patent: dict) -> tuple[float, str]:
        """画像与单件专利的匹配分与溯源理由。

        分数 = 命中兴趣词的权重之和；理由取权重最高的那条命中词的溯源文案。
        """
        pool = _patent_text_pool(patent)
        score = 0.0
        best: tuple[float, str] = (0.0, "")
        seen: set[str] = set()
        for term, weight, reason in self.terms:
            if term in seen or term not in pool:
                continue
            seen.add(term)
            score += weight
            if weight > best[0]:
                best = (weight, reason)
        return score, best[1]


class ProactiveService:
    """三类主动触达：主动对话 / 热点推送 / 企业需求推广。"""

    def __init__(self, search_service: PatentSearchService | None = None) -> None:
        self.search_service = search_service or PatentSearchService()

    # ==================== 1. 主动发起对话 ====================

    def initiate_conversations(
        self,
        users: list[dict],
        new_patents: Optional[list[dict]] = None,
        top_k: int = 0,
        now: Optional[datetime] = None,
    ) -> dict:
        """根据对话历史/画像，为每个用户判断是否值得主动发起对话。

        ``users``: ``[{"user_id": str, "profile": {...}}]``，画像结构与被动推荐一致。
        ``new_patents``: 后端聚合的新收录专利；缺省时从本地/检索数据里按
        ``publish_date`` 取最近 ``PROACTIVE_NEW_PATENT_DAYS`` 天的专利。

        返回 ``{"conversations": [...], "degraded": bool}``；
        每个用户至多一条主动对话（含开场白 + 命中专利卡片）。
        """
        top_k = top_k or config.PROACTIVE_CONVERSATION_TOP_K
        candidates, degraded = self._resolve_new_patents(new_patents, now)
        conversations: list[dict] = []

        for user in users or []:
            user_id = str(user.get("user_id", "")).strip()
            profile = InterestProfile(user.get("profile") or {})
            if not user_id or profile.empty:
                continue
            matched = self._match_patents(profile, candidates, top_k)
            if not matched:
                continue
            conversations.append({
                "user_id": user_id,
                "trigger": TRIGGER_NEW_PATENT,
                "opening_message": self._opening_message(profile, matched),
                "patents": matched,
            })

        return {"conversations": conversations, "degraded": degraded}

    def _resolve_new_patents(
        self, new_patents: Optional[list[dict]], now: Optional[datetime]
    ) -> tuple[list[dict], bool]:
        """确定候选新专利集合；后端未传入时按发布日期从本地数据兜底。"""
        if new_patents:
            return list(new_patents), False
        now = now or datetime.now()
        recent = []
        for patent in self.search_service.patents:
            published = _publish_date(patent)
            if published is None:
                continue
            if abs((now - published).days) <= config.PROACTIVE_NEW_PATENT_DAYS:
                recent.append(patent)
        return recent, not recent

    @staticmethod
    def _opening_message(profile: InterestProfile, matched: list[dict]) -> str:
        """生成主动对话开场白，尽量引用用户最近的对话原文。"""
        first = matched[0]
        count = len(matched)
        if profile.chat_queries:
            context = f"结合您最近与我聊到的「{_truncate(profile.chat_queries[0])}」"
        else:
            context = "根据您近期在平台上的检索与浏览"
        return (
            f"您好！{context}，平台新出现了 {count} 件您可能感兴趣的专利，"
            f"例如《{first.get('title', '')}》。需要我为您详细介绍或对比分析吗？"
        )

    # ==================== 2. 热点专利推送 ====================

    def push_hot_patents(
        self,
        users: list[dict],
        patent_stats: Optional[list[dict]] = None,
        top_k: int = 0,
        now: Optional[datetime] = None,
    ) -> dict:
        """对新出现 / 热度高的专利，匹配兴趣相关用户并生成推送。

        ``patent_stats``: ``[{"patent_id", "view_count", ...}]``，来自后端
        ``patent_statistics``；据此判定「热度高」。缺省时仅按「新收录」判定。

        返回 ``{"pushes": [{"user_id", "items": [...]}], "degraded": bool}``。
        """
        top_k = top_k or config.PROACTIVE_MAX_PUSH_PER_USER
        now = now or datetime.now()
        stats = {
            str(s.get("patent_id", "")): int(s.get("view_count", 0) or 0)
            for s in (patent_stats or [])
        }

        hot_patents: list[tuple[dict, str, int]] = []  # (patent, push_type, view_count)
        for patent in self.search_service.patents:
            pid = patent.get("patent_id", "")
            view_count = stats.get(pid, 0)
            published = _publish_date(patent)
            is_new = (
                published is not None
                and abs((now - published).days) <= config.PROACTIVE_NEW_PATENT_DAYS
            )
            if view_count >= config.PROACTIVE_HOT_VIEW_THRESHOLD:
                hot_patents.append((patent, "hot", view_count))
            elif is_new:
                hot_patents.append((patent, "new", view_count))
        # 热度优先、同热度下新收录优先
        hot_patents.sort(key=lambda x: (x[2], x[1] == "new"), reverse=True)
        hot_patents = hot_patents[: config.PROACTIVE_HOT_POOL_SIZE]

        pushes: list[dict] = []
        for user in users or []:
            user_id = str(user.get("user_id", "")).strip()
            profile = InterestProfile(user.get("profile") or {})
            if not user_id or profile.empty:
                continue
            items: list[dict] = []
            for patent, push_type, view_count in hot_patents:
                pid = patent.get("patent_id", "")
                if pid in profile.viewed_patent_ids:
                    continue
                score, reason = profile.match(patent)
                if score < config.PROACTIVE_MIN_MATCH_SCORE:
                    continue
                card = _patent_card(patent)
                if push_type == "hot":
                    card["reason"] = f"技术热点 · {view_count} 次浏览，{reason}"
                else:
                    card["reason"] = f"新收录专利，{reason}"
                card["push_type"] = push_type
                card["match_score"] = round(score, 3)
                items.append(card)
                if len(items) >= top_k:
                    break
            if items:
                pushes.append({
                    "user_id": user_id,
                    "trigger": TRIGGER_HOT_PATENT,
                    "items": items,
                })

        return {"pushes": pushes, "degraded": not hot_patents}

    # ==================== 3. 企业需求 → 专利权人推广 ====================

    def promote_to_owners(
        self,
        demands: list[dict],
        top_k: int = 0,
        session_id: str = "",
    ) -> dict:
        """对企业需求检索匹配专利，按专利权人聚合生成推广触达。

        ``demands``: ``[{"enterprise_id", "enterprise_name", "demand": {
            "tech_field", "core_problem", "keywords", "constraints"}}]``。

        返回 ``{"promotions": [...], "degraded": bool}``；每条 promotion 为
        「一位专利权人 × 一条企业需求」，含其名下命中的专利与推广文案。
        """
        top_k = top_k or config.PROACTIVE_PROMOTE_TOP_K
        promotions: list[dict] = []
        degraded = False

        for demand_item in demands or []:
            enterprise_id = str(demand_item.get("enterprise_id", "")).strip()
            enterprise_name = (demand_item.get("enterprise_name") or "").strip()
            demand = demand_item.get("demand") or {}
            tech_field = (demand.get("tech_field") or "").strip()
            core_problem = (demand.get("core_problem") or "").strip()
            keywords = [
                str(k).strip() for k in (demand.get("keywords") or []) if str(k).strip()
            ]
            query = " ".join(keywords)
            if not (tech_field or core_problem or query):
                continue

            try:
                patents = self.search_service.search(
                    tech_domain=tech_field,
                    core_problem=core_problem,
                    constraints=demand.get("constraints") or {},
                    query=query,
                    session_id=session_id or f"promote-{enterprise_id}",
                    top_k=top_k,
                )
            except Exception:
                logger.exception("[主动推广] 检索失败，跳过该条需求: %s", demand_item)
                degraded = True
                continue
            if not patents:
                degraded = True
                continue

            demand_text = _truncate(core_problem or tech_field or query)
            by_owner: dict[str, list[dict]] = {}
            for patent in patents:
                owner = self._patent_owner(patent)
                if not owner:
                    continue
                score = round(float(patent.get("final_score", 0.0) or 0.0), 3)
                # 零分即与需求无关（本地检索无命中时的退回候选），不向权利人推广
                if score <= 0:
                    continue
                card = _patent_card(patent)
                card["match_score"] = score
                by_owner.setdefault(owner, []).append(card)

            for owner, cards in by_owner.items():
                first = cards[0]
                promotions.append({
                    "owner": owner,
                    "enterprise_id": enterprise_id,
                    "enterprise_name": enterprise_name,
                    "trigger": TRIGGER_ENTERPRISE_DEMAND,
                    "demand_summary": demand_text,
                    "patents": cards,
                    "message": (
                        f"您好！企业{('「' + enterprise_name + '」') if enterprise_name else ''}"
                        f"正在寻找「{demand_text}」相关技术，您名下的专利"
                        f"《{first.get('title', '')}》等 {len(cards)} 件专利与该需求高度匹配，"
                        f"建议主动对接推广，促成技术转化。"
                    ),
                })

        if not promotions:
            degraded = True
        return {"promotions": promotions, "degraded": degraded}

    @staticmethod
    def _patent_owner(patent: dict) -> str:
        """解析专利权人：优先申请人（applicant），退回第一发明人。"""
        detail = patent.get("detail") or {}
        applicant = (patent.get("applicant") or detail.get("applicant") or "").strip()
        if applicant:
            return applicant
        inventor = (patent.get("inventor") or "").strip()
        if inventor:
            return re.split(r"[；;，,]", inventor)[0].strip()
        return ""

    # ==================== 公共匹配 ====================

    @staticmethod
    def _match_patents(
        profile: InterestProfile, candidates: list[dict], top_k: int
    ) -> list[dict]:
        """画像 × 候选专利匹配，返回带理由的 Top-K 卡片。"""
        scored: list[tuple[float, dict]] = []
        for patent in candidates:
            pid = patent.get("patent_id", "")
            if not pid or pid in profile.viewed_patent_ids:
                continue
            score, reason = profile.match(patent)
            if score < config.PROACTIVE_MIN_MATCH_SCORE:
                continue
            card = _patent_card(patent)
            card["reason"] = reason
            card["match_score"] = round(score, 3)
            scored.append((score, card))
        scored.sort(key=lambda x: x[0], reverse=True)
        return [card for _, card in scored[:top_k]]
