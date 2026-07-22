"""LLM 客户端抽象与远程实现"""
from __future__ import annotations
import abc
import json
import httpx
from app.core import config_manager as cfg
from app.core.logging import get_logger

logger = get_logger("llm_client")


class BaseLLMClient(abc.ABC):
    @abc.abstractmethod
    async def normalize_query(self, payload: dict) -> str:
        ...

    @abc.abstractmethod
    async def health(self) -> bool:
        ...


class DisabledLLMClient(BaseLLMClient):
    async def normalize_query(self, payload: dict) -> str:
        raise RuntimeError("LLM Query 规范化未启用")

    async def health(self) -> bool:
        return True


class RemoteLLMClient(BaseLLMClient):
    def __init__(self):
        self.endpoint = cfg.get("llm.endpoint", "")
        self.model = cfg.get("llm.model", "")
        self.timeout = cfg.get("llm.timeout", 10)

    async def normalize_query(self, payload: dict) -> str:
        messages = [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
        ]
        body = {
            "model": self.model,
            "messages": messages,
            "temperature": 0,
            "response_format": {"type": "json_object"},
        }
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            resp = await client.post(self.endpoint, json=body)
            resp.raise_for_status()
            data = resp.json()

        content = _extract_content(data)
        try:
            result = json.loads(content)
        except json.JSONDecodeError as exc:
            raise ValueError(f"LLM 返回非 JSON 内容: {content[:120]}") from exc

        normalized_query = result.get("normalized_query", "")
        if not isinstance(normalized_query, str) or not normalized_query.strip():
            raise ValueError("LLM 返回缺少 normalized_query")
        return normalized_query.strip()

    async def health(self) -> bool:
        return bool(self.endpoint and self.model)


def _extract_content(data: dict) -> str:
    if "choices" in data:
        return data["choices"][0]["message"]["content"]
    if "normalized_query" in data:
        return json.dumps(data, ensure_ascii=False)
    if "content" in data:
        return data["content"]
    raise ValueError("LLM 响应格式不支持")


def create_llm_client() -> BaseLLMClient:
    provider = cfg.get("llm.provider", "disabled")
    if provider == "remote":
        return RemoteLLMClient()
    return DisabledLLMClient()


_SYSTEM_PROMPT = """
你是智能专利对接系统的 Query 规范化模块。请把用户检索请求改写成适合专利向量检索的中文检索文本。

要求：
1. 只返回 JSON，不要解释。
2. JSON 格式必须是 {"normalized_query":"..."}。
3. 优先基于 rule_based_query 继续优化，不要忽略本地规则已提取出的技术词、约束条件和结构化字段。
4. 保留关键技术领域、核心问题、性能约束、材料偏好、应用场景、成熟度和年份条件。
5. 去掉“我想找、有没有、请帮我”等低信息表达。
6. 适度扩展同义技术词，但不要编造具体专利号、团队名或不存在的指标。
7. 优先使用专利检索常用表达，例如“耐高温、耐腐蚀、热传导、复合材料、规模化生产”。
""".strip()
