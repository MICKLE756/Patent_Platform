"""专利智能推荐 FastAPI 服务

对内提供：
    - ``POST /internal/v1/recommendations``：主页「为您推荐」被动推荐。
      后端聚合当前登录用户的画像（检索关键词 / 对话历史 / 槽位 / 浏览记录）后
      调用本服务，由 agent 侧合成查询、检索并返回带理由的推荐结果（snake_case）。
    - ``POST /internal/v1/proactive/conversations``：根据对话历史主动发起对话
      （新出现用户可能感兴趣的专利时，生成开场白与推荐卡片）。
    - ``POST /internal/v1/proactive/hot-patents``：新收录/热度高的专利，
      匹配兴趣相关用户并生成推送。
    - ``POST /internal/v1/proactive/enterprise-demands``：企业需求检索匹配专利，
      按专利权人聚合生成推广触达。

鉴权：请求头 ``x-service-token``，与后端约定的内部服务令牌一致。
agent 保持无状态：只认后端传入的 user_id 与画像，不做用户维度存储。
"""

import logging
from pathlib import Path

import uvicorn
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

import config
from proactive_service import ProactiveService
from recommendation_service import RecommendationService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)

app = FastAPI(title="Patent Recommendation Service")

_recommendation_service = RecommendationService()
_proactive_service = ProactiveService()


_STATIC_DIR = Path(__file__).parent / "static"


@app.get("/demo", include_in_schema=False)
async def demo_page():
    """三个主动触达接口的可视化演示页（本地开发用）。"""
    return FileResponse(_STATIC_DIR / "demo.html", media_type="text/html")


def _check_service_token(token: str | None) -> None:
    """校验内部服务令牌；未配置 SERVICE_TOKEN 时跳过（本地开发）。"""
    if config.SERVICE_TOKEN and token != config.SERVICE_TOKEN:
        raise HTTPException(status_code=401, detail="invalid service token")


class KeywordItem(BaseModel):
    keyword: str
    weight: float = 1.0


class Profile(BaseModel):
    keywords: list[KeywordItem] = Field(default_factory=list)
    chat_queries: list[str] = Field(default_factory=list)
    slots: dict = Field(default_factory=dict)
    viewed_patent_ids: list[str] = Field(default_factory=list)


class RecommendRequest(BaseModel):
    user_id: str
    profile: Profile = Field(default_factory=Profile)
    top_k: int = Field(default=config.DEFAULT_TOP_K, ge=1, le=config.MAX_TOP_K)


class UserProfileItem(BaseModel):
    user_id: str
    profile: Profile = Field(default_factory=Profile)


class ProactiveConversationRequest(BaseModel):
    users: list[UserProfileItem] = Field(default_factory=list)
    # 后端聚合的新收录专利；缺省时由本服务按 publish_date 兜底判定
    new_patents: list[dict] = Field(default_factory=list)
    top_k: int = Field(default=0, ge=0, le=config.MAX_TOP_K)


class PatentStatItem(BaseModel):
    patent_id: str
    view_count: int = 0


class HotPushRequest(BaseModel):
    users: list[UserProfileItem] = Field(default_factory=list)
    patent_stats: list[PatentStatItem] = Field(default_factory=list)
    top_k: int = Field(default=0, ge=0, le=config.MAX_TOP_K)


class EnterpriseDemand(BaseModel):
    tech_field: str = ""
    core_problem: str = ""
    keywords: list[str] = Field(default_factory=list)
    constraints: dict = Field(default_factory=dict)


class EnterpriseDemandItem(BaseModel):
    enterprise_id: str
    enterprise_name: str = ""
    demand: EnterpriseDemand = Field(default_factory=EnterpriseDemand)


class PromoteRequest(BaseModel):
    demands: list[EnterpriseDemandItem] = Field(default_factory=list)
    top_k: int = Field(default=0, ge=0, le=config.MAX_TOP_K)


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/internal/v1/recommendations")
async def recommendations(
    req: RecommendRequest,
    x_service_token: str | None = Header(default=None),
):
    _check_service_token(x_service_token)
    result = _recommendation_service.recommend(
        profile=req.profile.model_dump(),
        top_k=req.top_k,
        session_id=f"recommend-{req.user_id}",
    )
    return {
        "user_id": req.user_id,
        "items": result["items"],
        "degraded": result["degraded"],
    }


@app.post("/internal/v1/proactive/conversations")
async def proactive_conversations(
    req: ProactiveConversationRequest,
    x_service_token: str | None = Header(default=None),
):
    """根据对话历史主动发起对话：新出现用户可能感兴趣的专利时生成开场白。"""
    _check_service_token(x_service_token)
    return _proactive_service.initiate_conversations(
        users=[u.model_dump() for u in req.users],
        new_patents=req.new_patents or None,
        top_k=req.top_k,
    )


@app.post("/internal/v1/proactive/hot-patents")
async def proactive_hot_patents(
    req: HotPushRequest,
    x_service_token: str | None = Header(default=None),
):
    """新收录/热度高的专利，主动向兴趣相关用户推送。"""
    _check_service_token(x_service_token)
    return _proactive_service.push_hot_patents(
        users=[u.model_dump() for u in req.users],
        patent_stats=[s.model_dump() for s in req.patent_stats],
        top_k=req.top_k,
    )


@app.post("/internal/v1/proactive/enterprise-demands")
async def proactive_enterprise_demands(
    req: PromoteRequest,
    x_service_token: str | None = Header(default=None),
):
    """企业需求检索匹配专利，按专利权人聚合生成推广触达。"""
    _check_service_token(x_service_token)
    return _proactive_service.promote_to_owners(
        demands=[d.model_dump() for d in req.demands],
        top_k=req.top_k,
    )


if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=8090, reload=True)
