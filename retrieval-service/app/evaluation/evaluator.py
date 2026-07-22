"""评测模块骨架"""
from __future__ import annotations
import json
import csv
import math
from pathlib import Path
from app.core.logging import get_logger

logger = get_logger("evaluator")


def load_dataset(path: str) -> list[dict]:
    p = Path(path)
    if p.suffix == ".json":
        with open(p, "r", encoding="utf-8") as f:
            return json.load(f)
    elif p.suffix == ".csv":
        with open(p, "r", encoding="utf-8") as f:
            return list(csv.DictReader(f))
    raise ValueError(f"不支持的评测集格式: {p.suffix}")


def recall_at_k(relevant: set[str], retrieved: list[str], k: int = 20) -> float:
    if not relevant:
        return 0.0
    return len(relevant & set(retrieved[:k])) / len(relevant)


def precision_at_k(relevant: set[str], retrieved: list[str], k: int = 5) -> float:
    if k == 0:
        return 0.0
    return len(relevant & set(retrieved[:k])) / k


def mrr(relevant: set[str], retrieved: list[str]) -> float:
    for i, pid in enumerate(retrieved):
        if pid in relevant:
            return 1.0 / (i + 1)
    return 0.0


def dcg_at_k(relevant: set[str], retrieved: list[str], k: int = 10) -> float:
    score = 0.0
    for i, pid in enumerate(retrieved[:k]):
        if pid in relevant:
            score += 1.0 / math.log2(i + 2)
    return score


def ndcg_at_k(relevant: set[str], retrieved: list[str], k: int = 10) -> float:
    actual = dcg_at_k(relevant, retrieved, k)
    ideal_list = list(relevant)[:k]
    ideal = dcg_at_k(relevant, ideal_list, k)
    return actual / ideal if ideal > 0 else 0.0


def top_k_hit_rate(relevant: set[str], retrieved: list[str], k: int = 5) -> float:
    return 1.0 if relevant & set(retrieved[:k]) else 0.0


def evaluate(dataset_path: str, search_fn=None) -> dict:
    """
    占位评测入口。search_fn 为实际检索函数，后续接入。
    """
    logger.info("evaluate dataset=%s", dataset_path,
                extra={"request_id": "-", "session_id": "-"})
    return {
        "recall@20": 0.0,
        "precision@5": 0.0,
        "mrr": 0.0,
        "ndcg@10": 0.0,
        "top5_hit_rate": 0.0,
        "note": "评测功能占位，需接入真实检索函数",
    }
