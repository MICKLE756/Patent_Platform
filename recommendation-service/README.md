# 专利智能推荐服务（recommendation-service）

基于用户历史行为画像的专利推荐 agent 服务（FastAPI）。为主页「为您推荐」板块提供
个性化推荐能力：后端聚合当前登录用户的**检索关键词**、**AI 对话历史**、**意图槽位**
与**浏览记录**后调用本服务，agent 侧合成查询、检索专利并返回带可溯源理由的推荐结果。

在被动推荐之外，还提供三类**主动触达**能力（`proactive_service.py`）：

1. **主动发起对话**：根据用户对话历史/画像，发现新出现的、用户可能感兴趣的
   专利时，生成引用对话原文的开场白与推荐卡片，由后端投递到用户的 AI 会话。
2. **热点专利推送**：对新收录 / 热度高（浏览量高）的专利，匹配兴趣相关的用户
   并生成推送文案；兴趣不相关的用户不推送，避免打扰。
3. **企业需求推广**：对企业提出的技术需求检索匹配的专利，按专利权人聚合，
   生成向专利权人主动推广的触达文案，促成技术转化。

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

### `POST /internal/v1/proactive/conversations`

根据对话历史主动发起对话。后端传入用户画像列表与新收录专利（可缺省，
缺省时按 `publish_date` 在本地数据里兜底判定）：

```json
{
  "users": [{ "user_id": "12", "profile": { "keywords": [], "chat_queries": ["…"], "slots": {}, "viewed_patent_ids": [] } }],
  "new_patents": [],
  "top_k": 3
}
```

响应：每个命中用户至多一条主动对话（含开场白 + 专利卡片）：

```json
{
  "conversations": [
    {
      "user_id": "12",
      "trigger": "new_patent",
      "opening_message": "您好！结合您最近与我聊到的「…」，平台新出现了 3 件您可能感兴趣的专利…",
      "patents": [{ "patent_id": "CN…", "title": "…", "reason": "与您检索过的「…」相关", "match_score": 6.0 }]
    }
  ],
  "degraded": false
}
```

### `POST /internal/v1/proactive/hot-patents`

新收录 / 热度高的专利主动向相关用户推送。`patent_stats` 来自后端
`patent_statistics`（浏览量）：

```json
{
  "users": [{ "user_id": "12", "profile": { … } }],
  "patent_stats": [{ "patent_id": "CN…", "view_count": 320 }],
  "top_k": 5
}
```

响应：`pushes` 按用户聚合，每条 item 带 `push_type`（`hot`/`new`）与可溯源理由
（如 `技术热点 · 320 次浏览，与您检索过的「…」相关`）。

### `POST /internal/v1/proactive/enterprise-demands`

企业需求检索匹配专利，按专利权人（applicant，缺省回退第一发明人）聚合：

```json
{
  "demands": [
    {
      "enterprise_id": "e100",
      "enterprise_name": "长风汽车制造有限公司",
      "demand": { "tech_field": "汽车检测", "core_problem": "仪表板零件检测精度", "keywords": ["仪表板 检测"], "constraints": {} }
    }
  ],
  "top_k": 5
}
```

响应：`promotions` 每条为「一位专利权人 × 一条企业需求」，含其名下命中专利与
推广文案（`您名下的专利《…》等 N 件专利与该需求高度匹配，建议主动对接推广`）。

### `GET /health`

存活探针，返回 `{"status": "ok"}`。

### `GET /demo`

三个主动触达接口的可视化演示页（本地开发用）。服务启动后浏览器打开
`http://localhost:8090/demo`，三个 Tab 分别对应主动对话 / 热点推送 /
企业需求推广：左侧可编辑请求 JSON 并发送，右侧以卡片形式展示开场白、
推送理由、推广文案与专利卡片，可展开查看原始 JSON 响应。配置了
`SERVICE_TOKEN` 时在页面顶部填入 `x-service-token` 即可。

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
| `PROACTIVE_NEW_PATENT_DAYS` | 「新收录专利」判定窗口（天） | 30 |
| `PROACTIVE_HOT_VIEW_THRESHOLD` | 浏览量达到该阈值判定为热点 | 100 |
| `PROACTIVE_HOT_POOL_SIZE` | 热点候选池大小 | 50 |
| `PROACTIVE_MIN_MATCH_SCORE` | 主动触达的最低匹配分 | 1.0 |
| `PROACTIVE_CONVERSATION_TOP_K` | 主动对话携带的专利条数 | 3 |
| `PROACTIVE_MAX_PUSH_PER_USER` | 热点推送单用户条数上限 | 5 |
| `PROACTIVE_PROMOTE_TOP_K` | 每条企业需求检索的专利条数 | 5 |

## 测试

```bash
cd recommendation-service/src
python -m pytest test_recommendation_service.py test_proactive_service.py -v
```

覆盖：空画像、纯关键词、纯聊天、混合、检索全空、检索故障降级、
top_k 边界、已浏览专利排除、端点鉴权；主动触达三类场景（主动对话 /
热点推送 / 企业需求推广）的命中、排除、降级与鉴权。
