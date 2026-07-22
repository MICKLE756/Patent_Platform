# 检索召回与重排序服务 (retrieval-service)

成员4 负责模块 — 基于大模型与意图识别的智能专利对接系统

## 快速开始

```bash
# 1. 创建 conda 环境
conda create -n retrieval-service python=3.11 -y

# 2. 激活环境
conda activate retrieval-service

# 3. 安装依赖
pip install -r requirements.txt

# 启动milvus，启动容器组milvus-etcd milvus-minio milvus-standalone
docker start milvus-etcd milvus-minio milvus-standalone

# 启动milvus终端命令（可在docker app直接点击启动，名字叫“attu-local”）
docker run -d --name attu-local -p 8000:3000 zilliz/attu

# 4. 启动服务
uvicorn app.main:app --host 0.0.0.0 --port 8100 --reload

# 5. 运行测试
pytest tests/ -v

# 向Milvus数据库写入2000条数据
python3 scripts/import_milvus_json_to_collection.py --data data/milvus3.json --collection patent_chunks_v2 --limit 2000 --batch-size 32



```

## 项目结构

```
retrieval-service/
├── app/
│   ├── main.py                  # FastAPI 入口, 中间件, 异常处理注册
│   ├── api/
│   │   └── routes.py            # 接口路由: /retrieve/search, /health, /rerank, /evaluate
│   ├── services/
│   │   ├── query_normalizer.py  # Query 规范化 (去前缀/单位统一/同义词/字段拼接)
│   │   ├── vector_retriever.py  # Embedding + Milvus 召回 + 专利聚合
│   │   ├── filter_engine.py     # 字段过滤 (骨架, 预留放宽策略)
│   │   ├── rerank_engine.py     # Rerank 调用 + 失败降级
│   │   └── ranker.py            # 综合排序 (semantic/rerank/freshness/metadata → final)
│   ├── clients/
│   │   ├── embedding_client.py  # Embedding 抽象 + Mock 实现
│   │   ├── milvus_client.py     # Milvus 抽象 + Mock 实现
│   │   └── rerank_client.py     # Rerank 抽象 + Mock 实现
│   ├── core/
│   │   ├── config_manager.py    # YAML 配置加载
│   │   ├── models.py            # Pydantic 请求/响应模型
│   │   ├── logging.py           # 结构化日志 (request_id, session_id)
│   │   └── exceptions.py        # 统一异常处理
│   ├── evaluation/
│   │   └── evaluator.py         # 评测框架 (Recall@K, Precision@K, MRR, NDCG)
│   └── offline/
│       └── indexer.py           # 离线入库预留骨架
├── tests/
│   └── test_retrieval.py        # 13 个测试用例
├── config.yaml                  # 基础配置
├── requirements.txt             # Python 依赖
├── environment.yml              # conda 环境定义
└── pyproject.toml               # pytest 配置
```

## 接口说明

| 方法 | 路径 | 状态 |
|------|------|------|
| POST | `/retrieve/search` | 已实现 (mock 依赖) |
| GET  | `/retrieve/health` | 已实现 |
| POST | `/retrieve/rerank` | 占位 |
| POST | `/retrieve/evaluate` | 占位 |

## 在线检索链路

```
请求 → Query 规范化 → Embedding → Milvus Top100 片段召回 → 专利聚合 → 过滤 → Rerank → 综合排序 → 返回 Top-K
```

## 上游依赖

### 成员3 (意图识别与交互编排) 提供
- query, tech_field, core_problem, constraints, filters, sort_preference

### 成员2 (数据治理与知识库) 提供
- patent_id, title, abstract, claim_summary, publish_date, inventor, 切分入库数据

### 成员5 (专利画像与标签提取) 提供
- tech_tags, maturity, application_scene

### 模型接入层 (model-gateway) 提供
- Embedding 接口 (POST /v1/embeddings)
- Rerank 接口 (POST /v1/rerank)

### AutoDL Qwen3 Embedding 接入

项目已支持 `tests/embedding_server.py` 这种接口：

```bash
# AutoDL 容器内启动
EMBEDDING_API_TOKEN="2026zxcvbnm." uvicorn embedding_server:app --host 0.0.0.0 --port 6006

# retrieval-service 本机启动前设置同一个 token
export EMBEDDING_API_TOKEN="2026zxcvbnm."
```

然后修改 `config.yaml`：

```yaml
embedding:
  provider: "remote"
  endpoint: "http://<your-autodl-host>:6006/embed"
  health_endpoint: "http://<your-autodl-host>:6006/health"
  model: "Qwen3-Embedding-8B"
  dimension: 4096
  timeout: 60
  token: "2026zxcvbnm."
  token_env: "EMBEDDING_API_TOKEN"
  is_query: true
  normalize: true
```

注意：`embedding.dimension` 必须和 Qwen 服务实际返回维度、Milvus collection 建表维度、入库向量维度一致。如果使用 `Qwen3-Embedding-8B` 返回 4096 维，需要把 `scripts/create_test_milvus_collection.py` 和入库脚本里的维度也改成 4096，并重新建表/重新入库。

### AutoDL Qwen3 Rerank 接入

先把项目里的 `documents/rerank_server.py` 放到 AutoDL 的 `/root/autodl-tmp/rerank_server.py`。

在 AutoDL 容器内执行：

```bash
pip install -U "transformers>=4.51.0" sentence-transformers fastapi uvicorn accelerate

cd /root/autodl-tmp
export RERANK_MODEL_PATH=/root/autodl-tmp/models/Qwen3-Reranker-4B
uvicorn rerank_server:app --host 0.0.0.0 --port 6008
```

```bash
curl http://127.0.0.1:8200/health
curl -X POST http://127.0.0.1:8200/v1/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "query": "建筑外墙保温材料的阻燃改性技术",
    "documents": [
      "一种阻燃人造革及其制备方法",
      "一种建筑外墙保温阻燃聚合物材料",
      "一种汽车检测装置"
    ],
    "top_k": 3
  }'
```

retrieval-service 的 `config.yaml`：

```yaml
rerank:
  provider: "remote"
  endpoint: "http://<your-autodl-host>:8200/v1/rerank"
  health_endpoint: "http://<your-autodl-host>:8200/health"
  model: "Qwen3-Reranker-4B"
  timeout: 60
  token: ""
  token_env: "RERANK_API_TOKEN"
  candidate_top_k: 50
  max_document_chars: 1500
  request_format: "strings"

ranking:
  use_rerank: true
  weights:
    semantic: 0.7
    rerank: 0.3
    freshness: 0.0
    metadata: 0.0
```

## 真实实现 vs Mock/Stub

| 模块 | 状态 | 说明 |
|------|------|------|
| query_normalizer | 真实实现 | 去前缀、单位统一、同义词归一、字段拼接 |
| vector_retriever | 真实逻辑 + Mock 客户端 | 聚合逻辑真实，Embedding/Milvus 为 Mock |
| filter_engine | 骨架 | 预留过滤入口，当前透传 |
| rerank_engine | 真实逻辑 + Mock/远程客户端 | 支持候选截断、文本拼接、Rerank 调用与失败降级 |
| llm query normalize | 可选远程接入 | 默认关闭，开启后调用 model-gateway 做 Query 规范化，失败自动降级规则结果 |
| ranker | 真实实现 | 加权综合排序 |
| evaluator | 骨架 + 指标函数真实 | 指标计算函数可用，评测入口为占位 |
| embedding_client | Mock/远程 | 默认返回随机归一化向量；可切到 AutoDL Qwen3 `/embed` 服务 |
| milvus_client | Mock | 返回 8 个模拟专利的随机片段 |
| rerank_client | Mock/远程 | 可调用远程 `/v1/rerank`，也可使用 Mock/None 降级配置 |
| offline/indexer | 骨架 | 预留入库入口 |

## 配置

编辑 `config.yaml`，将 `provider` 从 `mock` 改为 `remote` 即可切换到真实服务。

如需启用 LLM Query 规范化，将 `llm.provider` 改为 `remote`，并将
`llm.query_normalization_enabled` 改为 `true`。默认关闭，关闭时使用本地规则规范化。
