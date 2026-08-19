"""大模型文案生成客户端（OpenAI 兼容，可选）

通过 ``OPENAI_API_KEY`` / ``OPENAI_BASE_URL`` / ``MODEL_NAME``（与 agent-service
的 .env 约定一致）接入任意 OpenAI 兼容服务（OpenAI / DeepSeek / vLLM / Ollama…），
用于润色主动触达文案（开场白 / 推送语 / 推广信）。

设计约定：
    - 未配置 key 或 base_url 时 ``enabled`` 为 False，调用方直接使用模板文案；
    - 任何网络/接口异常都不抛出，返回 None 由调用方回退模板，保证主动触达
      流程不因 LLM 故障而失败；
    - 复用 httpx 直连 ``{base_url}/chat/completions``，不引入 openai SDK 依赖。
"""

import logging

import httpx

import config

logger = logging.getLogger(__name__)

# 文案生成温度：需要一定多样性但不能跑偏
DEFAULT_TEMPERATURE = 0.5

SYSTEM_PROMPT = """\
你是专利技术转化平台的触达文案助手。根据给定的触达场景与专利信息，
改写出一条自然、友好、专业的中文触达消息。

要求：
1. 只依据给定信息，不得编造专利号、数字或事实；
2. 保留场景中的关键信息（用户兴趣来源 / 专利标题 / 企业名与需求）；
3. 一段话完成，不超过 120 字，不使用 Markdown，不加称谓抬头以外的客套；
4. 直接输出文案本身，不要任何解释或引号包裹。
"""


class LLMClient:
    """OpenAI 兼容 chat/completions 封装（无状态、失败静默）。"""

    def __init__(
        self,
        api_key: str = "",
        base_url: str = "",
        model: str = "",
        timeout: float = 0.0,
    ) -> None:
        self.api_key = api_key or config.LLM_API_KEY
        self.base_url = (base_url or config.LLM_BASE_URL).rstrip("/")
        self.model = model or config.LLM_MODEL_NAME
        self.timeout = timeout or config.LLM_TIMEOUT

    @property
    def enabled(self) -> bool:
        return bool(self.api_key and self.base_url and self.model)

    def polish(self, prompt: str) -> str | None:
        """按触达场景生成/润色文案；未启用或失败时返回 None（回退模板）。"""
        if not self.enabled:
            return None
        try:
            resp = httpx.post(
                f"{self.base_url}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json={
                    "model": self.model,
                    "messages": [
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": prompt},
                    ],
                    "temperature": DEFAULT_TEMPERATURE,
                },
                timeout=self.timeout,
            )
            resp.raise_for_status()
            text = resp.json()["choices"][0]["message"]["content"]
            text = (text or "").strip().strip('"').strip()
            return text or None
        except Exception as e:
            logger.warning("[LLM] 文案生成失败，回退模板: %s", e)
            return None
