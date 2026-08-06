# 专利智能推荐服务（recommendation-service）

基于用户历史行为画像的专利推荐 agent 服务（FastAPI）。为主页「为您推荐」板块提供
个性化推荐能力：后端聚合当前登录用户的**检索关键词**、**AI 对话历史**、**意图槽位**
与**浏览记录**后调用本服务，agent 侧合成查询、检索专利并返回带可溯源理由的推荐结果。

## 架构定位

```
小程序 index 页「为您推荐」
   │ GET /api/v1/recommendations
   ▼
SpringBoot 后端（画像聚合 / Redis 缓存 / 热门兜底）
   │ POST /internal/v1/recommendations  (x-service-token)
   ▼
recommendation-service（本服务）
   │ 画像 → 合成查询(≤3条) → PatentSearchService.search
   ▼
retrieval-service /retrieve/search（失败回退本地 milvus.json）
```

关键约定：
- agent **无状态**：只认后端传入的 `user_id` 与画像，不做用户维度存储。
- 画像为空（新用户）时后端**不调本服务**，直接走热门专利兜底；即便调了，
  本服务也会正常返回 `{"items": [], "degraded": true}`。
- 推荐理由在 agent 侧生成，后端只透传。

## 推荐流程

1. **合成候选查询（≤3 条）**，优先级从高到低：
   - `profile.slots`（tech_field / core_problem / constraints）→ 结构化查询，放第一位；
   - `profile.keywords` 权重最高的 2 条；
   - `profile.chat_queries` 最新一条原文。
2. **逐条检索**：复用 `PatentSearchService.search`（retrieval 调用 + 本地回退）。
3. **合并去重打分**：以 `patent_id` 聚合；同一专利被多条查询命中时
   `final_score` 取最高分；`source` 标记 `keyword` / `chat` / `hybrid`。
4. **生成理由**：`基于您检索过的「…」` / `根据您与 AI 的对话「…」` / `综合您的检索与对话历史`。
5. **降级**：检索全空或全部失败 → `{"items": [], "degraded": true}`。

## 接口

### `POST /internal/v1/recommendations`

鉴权：请求头 `x-service-token`（配置 `SERVICE_TOKEN` 后生效）。

请求体（snake_case）：

```json
{
  "user_id": "12",
  "profile": {
    "keywords": [{ "keyword": "耐高温涂层", "weight": 2.0 }],
    "chat_queries": ["我需要一种耐高温且环保的涂层材料"],
    "slots": {
      "tech_field": "涂层材料",
      "core_problem": "提高耐高温性能并兼顾环保",
      "constraints": { "time_range": "近3年" }
    },
    "viewed_patent_ids": ["CN202520842474.4"]
  },
  "top_k": 6
}
```

响应体：

```json
{
  "user_id": "12",
  "items": [
    {
      "patent_id": "CN123456",
      "title": "一种耐高温环保涂层",
      "tech_field": "涂层材料",
      "applicant": "某某公司",
      "legal_status": "授权",
      "validity": "有效",
      "abstract": "……",
      "final_score": 0.87,
      "reason": "基于您检索过的「耐高温涂层」",
      "source": "keyword"
    }
  ],
  "degraded": false
}
```

### `GET /health`

存活探针，返回 `{"status": "ok"}`。

## 运行

```bash
cd recommendation-service
pip install -r requirements.txt
python src/app.py            # 默认 0.0.0.0:8090
```

环境变量（可放 `.env`）：

| 变量 | 说明 | 默认 |
|---|---|---|
| `SERVICE_TOKEN` | 内部服务令牌，空则跳过鉴权（本地开发） | 空 |
| `RETRIEVAL_SERVICE_URL` | retrieval-service 地址，空则用本地 milvus.json | 空 |
| `RETRIEVAL_SERVICE_TIMEOUT` | retrieval 调用超时（秒） | 30 |
| `PATENT_DATA_PATH` | 本地兜底专利数据路径 | `../agent-service/milvus.json` |
| `RECOMMEND_DEFAULT_TOP_K` | 默认返回条数 | 6 |
| `RECOMMEND_MAX_TOP_K` | top_k 上限 | 10 |

## 测试

```bash
cd recommendation-service/src
python -m pytest test_recommendation_service.py -v
```

覆盖：空画像、纯关键词、纯聊天、混合、检索全空、检索故障降级、
top_k 边界、已浏览专利排除、端点鉴权。
