"""模拟数据请求脚本：用 mock_data 组装请求并调用本服务各接口。

用法（先启动服务 `python src/app.py`，默认 http://localhost:8090）：

    cd recommendation-service/mock_data
    python send_mock_requests.py                 # 依次调用全部接口并打印结果摘要
    python send_mock_requests.py --save          # 只把组装好的请求体写到 requests/ 目录
                                                 # （可直接粘贴到 /demo 页面或 Postman）
    python send_mock_requests.py --base http://localhost:8090 --token xxx

说明：
    - conversations / hot-patents / enterprise-demands 三个接口各发一组请求；
    - triggers/dispatch 把三类事件合成一批，演示「静态触发点 → 多方消息 → 推送」；
    - 模拟数据中 publish_date 较新的专利作为「新收录专利」传入。
"""

import argparse
import json
from pathlib import Path

import httpx

HERE = Path(__file__).parent

NEW_PATENT_SINCE = "2026-07"  # publish_date 以此为界视为「新收录」


def load(name: str):
    return json.loads((HERE / name).read_text(encoding="utf-8"))


def build_requests() -> dict[str, dict]:
    users = [{"user_id": u["user_id"], "profile": u["profile"]}
             for u in load("mock_users.json")]
    patents = load("mock_patents.json")
    stats = [{"patent_id": s["patent_id"], "view_count": s["view_count"]}
             for s in load("mock_patent_stats.json")]
    demands = load("mock_enterprise_demands.json")
    new_patents = [p for p in patents
                   if p.get("publish_date", "") >= NEW_PATENT_SINCE]

    return {
        "/internal/v1/proactive/conversations": {
            "users": users,
            "new_patents": new_patents,
            "top_k": 3,
        },
        "/internal/v1/proactive/hot-patents": {
            "users": users,
            "patent_stats": stats,
            "top_k": 5,
        },
        "/internal/v1/proactive/enterprise-demands": {
            "demands": demands,
            "top_k": 5,
        },
        "/internal/v1/triggers/dispatch": {
            "events": [
                {"trigger": "new_patent_published",
                 "payload": {"users": users, "new_patents": new_patents, "top_k": 3}},
                {"trigger": "patent_view_surge",
                 "payload": {"users": users, "patent_stats": stats, "top_k": 5}},
                {"trigger": "enterprise_demand_created",
                 "payload": {"demands": demands, "top_k": 5}},
            ],
        },
    }


def summarize(path: str, data: dict) -> str:
    if "conversations" in data:
        lines = [f"主动对话 {len(data['conversations'])} 条："]
        for c in data["conversations"]:
            lines.append(f"  - {c['user_id']}（{c['message_source']}）：{c['opening_message'][:60]}…")
        return "\n".join(lines)
    if "pushes" in data:
        lines = [f"热点推送 {len(data['pushes'])} 条："]
        for p in data["pushes"]:
            lines.append(f"  - {p['user_id']}（{len(p['items'])} 件）：{p['push_message'][:60]}…")
        return "\n".join(lines)
    if "promotions" in data:
        lines = [f"企业推广 {len(data['promotions'])} 条："]
        for p in data["promotions"]:
            lines.append(f"  - {p['enterprise_name']} → {p['owner']}（{len(p['patents'])} 件专利）")
        return "\n".join(lines)
    if "messages" in data:
        lines = [f"触发点分发：{[r['status'] for r in data['results']]}，"
                 f"多方消息 {len(data['messages'])} 条，投递 {data['delivery']['mode']}："]
        for m in data["messages"]:
            lines.append(f"  - [{m['trigger']}] → {m['recipient_type']}:{m['recipient_name']}"
                         f"（thread={m['thread_id'][:8]}）{m['title']}")
        return "\n".join(lines)
    return json.dumps(data, ensure_ascii=False)[:200]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8090")
    parser.add_argument("--token", default="", help="SERVICE_TOKEN（配置了才需要）")
    parser.add_argument("--save", action="store_true",
                        help="只把请求体写到 requests/ 目录，不发请求")
    args = parser.parse_args()

    requests_map = build_requests()

    if args.save:
        out_dir = HERE / "requests"
        out_dir.mkdir(exist_ok=True)
        for path, body in requests_map.items():
            name = path.rsplit("/", 1)[-1].replace("-", "_") + ".json"
            (out_dir / name).write_text(
                json.dumps(body, ensure_ascii=False, indent=2), encoding="utf-8")
            print(f"已写入 {out_dir / name}")
        return

    headers = {"x-service-token": args.token} if args.token else {}
    for path, body in requests_map.items():
        print(f"\n===== POST {path} =====")
        resp = httpx.post(f"{args.base}{path}", json=body,
                          headers=headers, timeout=120)
        resp.raise_for_status()
        print(summarize(path, resp.json()))


if __name__ == "__main__":
    main()
