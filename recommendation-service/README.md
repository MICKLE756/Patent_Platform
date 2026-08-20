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

在此之上还接入了 **agent（LLM）文案生成** 与 **静态触发点 → 多方消息 → 推送**
能力（`llm_client.py` / `trigger_center.py`）：

- **LLM 文案（可选）**：配置 `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `MODEL_NAME`
  （与 agent-service 约定一致，OpenAI 兼容接口）后，三类触达文案由 LLM 基于
  给定专利/需求信息润色生成；未配置或调用失败时**自动回退规则模板**，服务
  功能不受影响。触达结果带 `message_source`（`llm` / `template`）标记来源。
- **静态触发点**：注册三个业务事件触发点（新专利收录 / 浏览量激增 /
  企业需求发布），后端在事件发生时调用统一分发接口，本服务完成匹配并
  生成**多方消息**（用户 / 专利权人 / 企业，多方共享 `thread_id` 便于拉进
  同一对接会话），可选通过 Webhook 推送到后端消息网关。

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

### `GET /internal/v1/triggers`

枚举静态触发点（后端据此在业务事件处接入 dispatch）：

```json
{
  "triggers": {
    "new_patent_published":      { "description": "平台新收录专利，匹配兴趣用户后由 AI 主动发起对话", "parties": ["user"], "channel": "ai_chat" },
    "patent_view_surge":         { "description": "专利浏览量达到热点阈值，向兴趣相关用户推送", "parties": ["user"], "channel": "notification" },
    "enterprise_demand_created": { "description": "企业发布技术需求，向匹配的专利权人推广并回执企业", "parties": ["patent_owner", "enterprise"], "channel": "notification" }
  }
}
```

### `POST /internal/v1/triggers/dispatch`

触发点统一分发：匹配 → 生成多方消息 → 推送（Webhook 可选）。
`payload` 结构与对应 proactive 接口的请求体一致：

```json
{
  "events": [
    { "trigger": "new_patent_published",      "payload": { "users": [], "new_patents": [], "top_k": 3 } },
    { "trigger": "patent_view_surge",         "payload": { "users": [], "patent_stats": [], "top_k": 5 } },
    { "trigger": "enterprise_demand_created", "payload": { "demands": [], "top_k": 5 } }
  ]
}
```

响应：

```json
{
  "results": [{ "trigger": "enterprise_demand_created", "status": "matched", "message_count": 3 }],
  "messages": [
    {
      "message_id": "…", "trigger": "enterprise_demand_created", "thread_id": "…",
      "recipient_type": "patent_owner", "recipient_id": "胡拥军", "recipient_name": "胡拥军",
      "channel": "notification", "title": "企业技术需求与您的专利匹配",
      "content": "您好！企业「…」正在寻找…", "patents": [ … ]
    },
    {
      "message_id": "…", "trigger": "enterprise_demand_created", "thread_id": "同上",
      "recipient_type": "enterprise", "recipient_id": "e100", "recipient_name": "长风汽车制造有限公司",
      "channel": "notification", "title": "您的技术需求已匹配到专利权人",
      "content": "您发布的需求「…」已匹配到 N 位专利权人…", "patents": [ … ]
    }
  ],
  "delivery": { "mode": "returned", "pushed": 0, "failed": 0 }
}
```

多方沟通约定：同一事件产生的消息共享 `thread_id`（如企业需求下的
权利人推广消息与企业回执），后端可据此把相关方拉进同一对接会话。

推送模式（`delivery.mode`）：

- `returned`：未配置 `PUSH_WEBHOOK_URL`，消息仅随响应返回，由调用方投递；
- `pushed` / `partial` / `failed`：配置 Webhook 后逐条 POST 的结果；投递失败
  不丢消息（仍在 `messages` 中），调用方可重试。

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
cp .env.example .env         # 按需填入 OPENAI_API_KEY / OPENAI_BASE_URL / MODEL_NAME 等
python app.py                # 推荐：默认 0.0.0.0:8090
# 或 python src/app.py       # 直接运行 FastAPI 源文件
```

不要把 `src/app.py` 单独复制到其他目录运行。它依赖同目录的
`config.py`、`proactive_service.py`、`recommendation_service.py`、
`patent_search.py` 和 `trigger_center.py`；只复制一个文件会出现
`ModuleNotFoundError: No module named 'config'`。推荐从
`recommendation-service` 目录运行上面的 `app.py` 启动器。

环境变量（模板见 `.env.example`，复制为 `.env` 后填写；`.env` 不入库）：

| 变量 | 说明 | 默认 |
|---|---|---|
| `OPENAI_API_KEY` | LLM API Key（OpenAI 兼容），空则关闭 LLM 用模板文案 | 空 |
| `OPENAI_BASE_URL` | LLM API base_url（如 `https://api.deepseek.com/v1`） | 空 |
| `MODEL_NAME` | LLM 模型名 | 空 |
| `LLM_TIMEOUT` | LLM 调用超时（秒） | 60 |
| `PUSH_WEBHOOK_URL` | 消息推送 Webhook（后端消息网关），空则消息仅随响应返回 | 空 |
| `PUSH_WEBHOOK_TOKEN` | 推送鉴权令牌（`x-service-token` 请求头） | 空 |
| `PUSH_TIMEOUT` | 推送超时（秒） | 10 |
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

## 模拟数据（mock_data/）

`mock_data/` 提供成套虚拟数据，开箱即可对全部接口做手工/联调测试：

| 文件 | 内容 |
|---|---|
| `mock_patents.json` | 20 件模拟专利（涂层/电池/检测/视觉/机器人/光伏/基因/无人机/医疗等，含新收录与历史专利、不同权利人） |
| `mock_users.json` | 10 个模拟用户画像（不同兴趣领域、纯对话用户、已浏览用户、空画像新用户） |
| `mock_patent_stats.json` | 专利浏览量统计（含超过/低于热点阈值的对照数据） |
| `mock_enterprise_demands.json` | 6 条企业技术需求（检测/散热/涂层/光伏/骨科/农业） |
| `send_mock_requests.py` | 用以上数据组装请求并调用全部接口的脚本 |
| `requests/*.json` | 组装好的四个接口现成请求体（可直接粘到 `/demo` 页或 Postman） |

使用方式：

```bash
# 1.（可选）让本地检索也走模拟专利库，构成全模拟环境
# （路径相对启动目录 src/，也可写绝对路径）
echo "PATENT_DATA_PATH=../mock_data/mock_patents.json" >> recommendation-service/.env

# 2. 启动服务
cd recommendation-service/src && python app.py

# 3. 另开终端，一键调用全部接口并打印结果摘要
cd recommendation-service/mock_data && python send_mock_requests.py

# 或只生成请求体文件（粘贴到 /demo 页面用）
python send_mock_requests.py --save
```

模拟数据设计了正反对照：空画像用户不触达、已浏览专利不重复推送、
低于浏览量阈值的专利不进热点池、企业需求按权利人聚合并生成企业回执。
