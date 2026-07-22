import yaml
from pathlib import Path
from typing import Any

_config: dict = {}


def load_config(path: str = "config.yaml") -> dict:
    global _config
    p = Path(path)
    if p.exists():
        with open(p, "r", encoding="utf-8") as f:
            _config = yaml.safe_load(f) or {}
    return _config


def get(key: str, default: Any = None) -> Any:
    keys = key.split(".")
    val = _config
    for k in keys:
        if isinstance(val, dict):
            val = val.get(k)
        else:
            return default
        if val is None:
            return default
    return val


load_config()
