"""静态触发点 + 多方消息中心

把三类主动触达统一到「触发点 → 匹配 → 多方消息 → 推送」的事件模型：

    静态触发点（TRIGGER_POINTS，代码内注册、可枚举）：
        - new_patent_published   平台新收录专利      → 触达用户（AI 主动对话）
        - patent_view_surge      专利浏览量达到阈值  → 触达用户（热点推送）
        - enterprise_demand_created 企业发布技术需求 → 触达专利权人 + 回执企业（多方）

    后端在业务事件发生时调用 ``POST /internal/v1/triggers/dispatch``，本模块
    按触发点调用 ProactiveService 完成匹配，并把结果展开为统一的多方消息：

        {"message_id", "trigger", "thread_id",
         "recipient_type": "user" | "patent_owner" | "enterprise",
         "recipient_id", "recipient_name", "channel", "title", "content",
         "patents": [...]}

    同一事件产生的多方消息共享 ``thread_id``，便于后端把相关方拉进同一
    会话（如企业与专利权人的对接沟通）。

    投递：配置 ``PUSH_WEBHOOK_URL`` 时逐条 POST 到后端消息网关
    （delivery=pushed / partial / failed）；未配置时随响应返回
    （delivery=returned），由调用方自行投递。
"""

import logging
import uuid

import httpx

import config
from proactive_service import ProactiveService

logger = logging.getLogger(__name__)

# 消息渠道
CHANNEL_AI_CHAT = "ai_chat"            # 投递到用户与 AI 的会话（主动对话）
CHANNEL_NOTIFICATION = "notification"  # 站内信 / 推送通知

# 静态触发点注册表：trigger → 描述与涉及的相关方（可通过 GET /triggers 枚举）
TRIGGER_POINTS: dict[str, dict] = {
    "new_patent_published": {
        "description": "平台新收录专利，匹配兴趣用户后由 AI 主动发起对话",
        "parties": ["user"],
        "channel": CHANNEL_AI_CHAT,
    },
    "patent_view_surge": {
        "description": "专利浏览量达到热点阈值，向兴趣相关用户推送",
        "parties": ["user"],
        "channel": CHANNEL_NOTIFICATION,
    },
    "enterprise_demand_created": {
        "description": "企业发布技术需求，向匹配的专利权人推广并回执企业",
        "parties": ["patent_owner", "enterprise"],
        "channel": CHANNEL_NOTIFICATION,
    },
}


def _message(
    trigger: str,
    thread_id: str,
    recipient_type: str,
    recipient_id: str,
    title: str,
    content: str,
    patents: list[dict],
    channel: str,
    recipient_name: str = "",
) -> dict:
    return {
        "message_id": uuid.uuid4().hex,
        "trigger": trigger,
        "thread_id": thread_id,
        "recipient_type": recipient_type,
        "recipient_id": recipient_id,
        "recipient_name": recipient_name or recipient_id,
        "channel": channel,
        "title": title,
        "content": content,
        "patents": patents,
    }


class TriggerCenter:
    """触发点分发：匹配 → 生成多方消息 → 推送（Webhook 可选）。"""

    def __init__(self, proactive: ProactiveService | None = None) -> None:
        self.proactive = proactive or ProactiveService()

    # ==================== 事件分发 ====================

    def dispatch(self, events: list[dict]) -> dict:
        """处理一批触发事件，返回多方消息与投递状态。

        ``events``: ``[{"trigger": str, "payload": {...}}]``；payload 结构与
        对应 proactive 接口的请求体一致。
        """
        messages: list[dict] = []
        results: list[dict] = []
        degraded = False

        for event in events or []:
            trigger = str(event.get("trigger", "")).strip()
            payload = event.get("payload") or {}
            if trigger not in TRIGGER_POINTS:
                results.append({"trigger": trigger, "status": "unknown_trigger"})
                continue
            handler = getattr(self, f"_handle_{trigger}")
            event_messages, event_degraded = handler(payload)
            degraded = degraded or event_degraded
            messages.extend(event_messages)
            results.append({
                "trigger": trigger,
                "status": "matched" if event_messages else "no_match",
                "message_count": len(event_messages),
            })

        delivery = self._deliver(messages)
        return {
            "results": results,
            "messages": messages,
            "delivery": delivery,
            "degraded": degraded,
        }

    # ==================== 各触发点处理 ====================

    def _handle_new_patent_published(self, payload: dict) -> tuple[list[dict], bool]:
        result = self.proactive.initiate_conversations(
            users=payload.get("users") or [],
            new_patents=payload.get("new_patents") or None,
            top_k=int(payload.get("top_k", 0) or 0),
        )
        messages = [
            _message(
                trigger="new_patent_published",
                thread_id=uuid.uuid4().hex,
                recipient_type="user",
                recipient_id=conv["user_id"],
                title="发现您可能感兴趣的新专利",
                content=conv["opening_message"],
                patents=conv["patents"],
                channel=CHANNEL_AI_CHAT,
            )
            for conv in result["conversations"]
        ]
        return messages, result["degraded"]

    def _handle_patent_view_surge(self, payload: dict) -> tuple[list[dict], bool]:
        result = self.proactive.push_hot_patents(
            users=payload.get("users") or [],
            patent_stats=payload.get("patent_stats") or [],
            top_k=int(payload.get("top_k", 0) or 0),
        )
        messages = [
            _message(
                trigger="patent_view_surge",
                thread_id=uuid.uuid4().hex,
                recipient_type="user",
                recipient_id=push["user_id"],
                title="热点/新收录专利推送",
                content=push["push_message"],
                patents=push["items"],
                channel=CHANNEL_NOTIFICATION,
            )
            for push in result["pushes"]
        ]
        return messages, result["degraded"]

    def _handle_enterprise_demand_created(
        self, payload: dict
    ) -> tuple[list[dict], bool]:
        """企业需求触发：向每位专利权人推广，并给企业一条回执（多方沟通）。

        同一条需求的权利人消息与企业回执共享 thread_id，后端可据此把
        企业与各权利人拉进同一对接会话。
        """
        result = self.proactive.promote_to_owners(
            demands=payload.get("demands") or [],
            top_k=int(payload.get("top_k", 0) or 0),
        )
        messages: list[dict] = []
        # 按企业需求分组：一条需求一个 thread
        threads: dict[tuple[str, str], str] = {}
        owners_by_thread: dict[str, list[dict]] = {}

        for promo in result["promotions"]:
            key = (promo["enterprise_id"], promo["demand_summary"])
            thread_id = threads.setdefault(key, uuid.uuid4().hex)
            messages.append(
                _message(
                    trigger="enterprise_demand_created",
                    thread_id=thread_id,
                    recipient_type="patent_owner",
                    recipient_id=promo["owner"],
                    title="企业技术需求与您的专利匹配",
                    content=promo["message"],
                    patents=promo["patents"],
                    channel=CHANNEL_NOTIFICATION,
                )
            )
            owners_by_thread.setdefault(thread_id, []).append(promo)

        # 企业回执：告知已触达哪些权利人
        for (enterprise_id, demand_summary), thread_id in threads.items():
            promos = owners_by_thread.get(thread_id, [])
            if not promos:
                continue
            enterprise_name = promos[0]["enterprise_name"]
            owners = [p["owner"] for p in promos]
            messages.append(
                _message(
                    trigger="enterprise_demand_created",
                    thread_id=thread_id,
                    recipient_type="enterprise",
                    recipient_id=enterprise_id,
                    recipient_name=enterprise_name,
                    title="您的技术需求已匹配到专利权人",
                    content=(
                        f"您发布的需求「{demand_summary}」已匹配到 "
                        f"{len(owners)} 位专利权人（{'、'.join(owners[:5])}"
                        f"{'…' if len(owners) > 5 else ''}），"
                        f"平台已向对方发出推广触达，可在本会话中与其进一步沟通。"
                    ),
                    patents=[c for p in promos for c in p["patents"]],
                    channel=CHANNEL_NOTIFICATION,
                )
            )

        return messages, result["degraded"]

    # ==================== 投递 ====================

    @staticmethod
    def _deliver(messages: list[dict]) -> dict:
        """推送消息：配置了 PUSH_WEBHOOK_URL 时 POST 到消息网关，否则仅返回。"""
        if not messages:
            return {"mode": "none", "pushed": 0, "failed": 0}
        if not config.PUSH_WEBHOOK_URL:
            return {"mode": "returned", "pushed": 0, "failed": 0}

        headers = {}
        if config.PUSH_WEBHOOK_TOKEN:
            headers["x-service-token"] = config.PUSH_WEBHOOK_TOKEN
        pushed = failed = 0
        for msg in messages:
            try:
                resp = httpx.post(
                    config.PUSH_WEBHOOK_URL,
                    json=msg,
                    headers=headers,
                    timeout=config.PUSH_TIMEOUT,
                )
                resp.raise_for_status()
                pushed += 1
            except Exception as e:
                logger.warning("[推送] 消息投递失败 %s: %s", msg["message_id"], e)
                failed += 1
        if failed == 0:
            mode = "pushed"
        elif pushed == 0:
            mode = "failed"
        else:
            mode = "partial"
        return {"mode": mode, "pushed": pushed, "failed": failed}
