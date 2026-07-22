"""Patent detail storage client backed by local MySQL."""
from __future__ import annotations

import abc
import json
import os
from functools import lru_cache
from typing import Any
from urllib.parse import quote_plus

from app.core import config_manager as cfg
from app.core.logging import get_logger

logger = get_logger("patent_detail_client")


class BasePatentDetailClient(abc.ABC):
    @abc.abstractmethod
    async def get_details(self, patent_ids: list[str]) -> dict[str, dict[str, Any]]:
        """Return patent details keyed by patent_id."""
        ...

    @abc.abstractmethod
    async def health(self) -> bool:
        ...


class NullPatentDetailClient(BasePatentDetailClient):
    async def get_details(self, patent_ids: list[str]) -> dict[str, dict[str, Any]]:
        return {}

    async def health(self) -> bool:
        return True


class MySQLPatentDetailClient(BasePatentDetailClient):
    def __init__(self):
        self.table = cfg.get("mysql.patent_detail_table", "patent_details")
        self._engine = _create_engine()

    async def get_details(self, patent_ids: list[str]) -> dict[str, dict[str, Any]]:
        ids = [str(pid) for pid in dict.fromkeys(patent_ids) if pid]
        if not ids:
            return {}

        try:
            from sqlalchemy import bindparam, text

            stmt = (
                text(
                    f"""
                    SELECT patent_id, detail_json
                    FROM `{self.table}`
                    WHERE patent_id IN :patent_ids
                    """
                )
                .bindparams(bindparam("patent_ids", expanding=True))
            )
            with self._engine.connect() as conn:
                rows = conn.execute(stmt, {"patent_ids": ids}).mappings().all()
        except Exception as exc:
            logger.warning("mysql_patent_detail_query_failed err=%s", str(exc),
                           extra={"request_id": "-", "session_id": "-"})
            return {}

        details: dict[str, dict[str, Any]] = {}
        for row in rows:
            patent_id = str(row["patent_id"])
            detail = _parse_detail_json(row.get("detail_json"))
            details[patent_id] = detail
        return details

    async def health(self) -> bool:
        try:
            from sqlalchemy import text

            with self._engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            return True
        except Exception as exc:
            logger.warning("mysql_patent_detail_health_failed err=%s", str(exc),
                           extra={"request_id": "-", "session_id": "-"})
            return False


def create_patent_detail_client() -> BasePatentDetailClient:
    if not bool(cfg.get("mysql.enabled", False)):
        return NullPatentDetailClient()
    try:
        return MySQLPatentDetailClient()
    except Exception as exc:
        logger.warning("mysql_patent_detail_client_disabled err=%s", str(exc),
                       extra={"request_id": "-", "session_id": "-"})
        return NullPatentDetailClient()


@lru_cache(maxsize=1)
def _create_engine():
    from sqlalchemy import create_engine

    host = cfg.get("mysql.host", "127.0.0.1")
    port = int(cfg.get("mysql.port", 3306))
    user = cfg.get("mysql.user", "root")
    password = _mysql_password()
    database = cfg.get("mysql.database", "retrieval_service")
    charset = cfg.get("mysql.charset", "utf8mb4")

    url = (
        f"mysql+pymysql://{quote_plus(str(user))}:{quote_plus(str(password))}"
        f"@{host}:{port}/{database}?charset={charset}"
    )
    return create_engine(url, pool_pre_ping=True, pool_recycle=1800)


def _mysql_password() -> str:
    password_env = cfg.get("mysql.password_env", "MYSQL_PASSWORD")
    if password_env:
        value = os.getenv(str(password_env))
        if value:
            return value
    return str(cfg.get("mysql.password", ""))


def _parse_detail_json(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if value is None:
        return {}
    if isinstance(value, (bytes, bytearray)):
        value = value.decode("utf-8")
    try:
        parsed = json.loads(str(value))
    except (TypeError, json.JSONDecodeError):
        return {}
    return parsed if isinstance(parsed, dict) else {}
