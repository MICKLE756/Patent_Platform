"""专利智能推荐 FastAPI 服务

对内提供 ``POST /internal/v1/recommendations``：
后端聚合当前登录用户的画像（检索关键词 / 对话历史 / 槽位 / 浏览记录）后
调用本服务，由 agent 侧合成查询、检索并返回带理由的推荐结果（snake_case）。

鉴权：请求头 ``x-service-token``，与后端约定的内部服务令牌一致。
agent 保持无状态：只认后端传入的 user_id 与画像，不做用户维度存储。
"""

import logging

import uvicorn
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

import config
from recommendation_service import RecommendationService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)

app = FastAPI(title="Patent Recommendation Service")

_recommendation_service = RecommendationService()


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


if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=8090, reload=True)
