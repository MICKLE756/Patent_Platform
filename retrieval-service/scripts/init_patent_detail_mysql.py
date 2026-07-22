"""Create the patent detail MySQL table and optionally import JSON details."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from urllib.parse import quote_plus

import yaml
from sqlalchemy import create_engine, text


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="config.yaml")
    parser.add_argument("--json", default="data/专利详情2000条.json")
    parser.add_argument("--no-import", action="store_true")
    args = parser.parse_args()

    config = _load_config(args.config)
    mysql_cfg = config.get("mysql", {})
    database = mysql_cfg.get("database", "retrieval_service")
    table = mysql_cfg.get("patent_detail_table", "patent_details")

    server_engine = _create_engine(mysql_cfg, database=None)
    with server_engine.begin() as conn:
        conn.execute(text(
            f"CREATE DATABASE IF NOT EXISTS `{database}` "
            "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        ))

    engine = _create_engine(mysql_cfg, database=database)
    with engine.begin() as conn:
        conn.execute(text(f"""
            CREATE TABLE IF NOT EXISTS `{table}` (
                patent_id VARCHAR(128) NOT NULL PRIMARY KEY,
                publication_no VARCHAR(128) NULL,
                title VARCHAR(512) NULL,
                applicant VARCHAR(512) NULL,
                current_owner VARCHAR(512) NULL,
                publication_date DATE NULL,
                detail_json JSON NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                KEY idx_publication_no (publication_no),
                KEY idx_publication_date (publication_date)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """))

    if args.no_import:
        print(f"created database={database} table={table}")
        return

    json_path = Path(args.json)
    if not json_path.exists():
        print(f"created database={database} table={table}; json not found: {json_path}")
        return

    rows = json.loads(json_path.read_text(encoding="utf-8"))
    if not isinstance(rows, list):
        raise ValueError("JSON detail file must contain a list of patent objects")

    stmt = text(f"""
        INSERT INTO `{table}` (
            patent_id, publication_no, title, applicant, current_owner,
            publication_date, detail_json
        )
        VALUES (
            :patent_id, :publication_no, :title, :applicant, :current_owner,
            :publication_date, :detail_json
        )
        ON DUPLICATE KEY UPDATE
            publication_no = VALUES(publication_no),
            title = VALUES(title),
            applicant = VALUES(applicant),
            current_owner = VALUES(current_owner),
            publication_date = VALUES(publication_date),
            detail_json = VALUES(detail_json)
    """)

    payload = []
    for row in rows:
        if not isinstance(row, dict) or not row.get("patent_id"):
            continue
        payload.append({
            "patent_id": str(row.get("patent_id", "")),
            "publication_no": _optional_str(row.get("publication_no")),
            "title": _optional_str(row.get("title")),
            "applicant": _optional_str(row.get("applicant")),
            "current_owner": _optional_str(row.get("current_owner")),
            "publication_date": _optional_date(row.get("publication_date")),
            "detail_json": json.dumps(row, ensure_ascii=False),
        })

    with engine.begin() as conn:
        conn.execute(stmt, payload)

    print(f"created database={database} table={table}; imported={len(payload)}")


def _load_config(path: str) -> dict:
    config_path = Path(path)
    if not config_path.exists():
        return {}
    return yaml.safe_load(config_path.read_text(encoding="utf-8")) or {}


def _create_engine(mysql_cfg: dict, database: str | None):
    host = mysql_cfg.get("host", "127.0.0.1")
    port = int(mysql_cfg.get("port", 3306))
    user = mysql_cfg.get("user", "root")
    password = _mysql_password(mysql_cfg)
    charset = mysql_cfg.get("charset", "utf8mb4")
    db_part = f"/{database}" if database else ""
    url = (
        f"mysql+pymysql://{quote_plus(str(user))}:{quote_plus(str(password))}"
        f"@{host}:{port}{db_part}?charset={charset}"
    )
    return create_engine(url, pool_pre_ping=True)


def _mysql_password(mysql_cfg: dict) -> str:
    password_env = mysql_cfg.get("password_env", "MYSQL_PASSWORD")
    if password_env:
        value = os.getenv(str(password_env))
        if value:
            return value
    return str(mysql_cfg.get("password", ""))


def _optional_str(value: object) -> str | None:
    if value is None:
        return None
    text_value = str(value)
    return text_value or None


def _optional_date(value: object) -> str | None:
    if value is None:
        return None
    text_value = str(value).strip()
    if not text_value:
        return None
    return text_value[:10]


if __name__ == "__main__":
    main()
