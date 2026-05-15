#!/usr/bin/env python
"""
Initialize a fresh PostgreSQL database for the mainline RuoYi + Quant Road stack.

This script is intentionally destructive for the base RuoYi tables because
`sql/ruoyi_pg_init.sql` contains `drop table if exists` statements. Use it only
for a fresh database or when you explicitly want to rebuild the schema/data.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import psycopg2


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Initialize a fresh PostgreSQL database for RuoYi + Quant Road."
    )
    parser.add_argument("--host", default="localhost", help="PostgreSQL host, default: localhost")
    parser.add_argument("--port", type=int, default=5432, help="PostgreSQL port, default: 5432")
    parser.add_argument("--user", default="postgres", help="PostgreSQL user, default: postgres")
    parser.add_argument("--password", default="123456", help="PostgreSQL password")
    parser.add_argument("--database", default="db-quant", help="Database name, default: db-quant")
    return parser.parse_args()


def read_sql(path: Path) -> str:
    if not path.exists():
        raise FileNotFoundError(f"SQL file not found: {path}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    args = parse_args()
    repo_root = Path(__file__).resolve().parent.parent
    sql_files = [
        repo_root / "sql" / "ruoyi_pg_init.sql",
        repo_root / "sql" / "init.sql",
        repo_root / "sql" / "ruoyi_quant_menu.sql",
        repo_root / "sql" / "ruoyi_quant_jobs.sql",
    ]

    print("Fresh initialization will rebuild the base RuoYi tables before applying Quant Road schema.")
    print(f"Connecting PostgreSQL {args.host}:{args.port}/{args.database} ...")
    conn = psycopg2.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        dbname=args.database,
    )
    conn.autocommit = True
    try:
        with conn.cursor() as cursor:
            for sql_file in sql_files:
                print(f"Applying: {sql_file.relative_to(repo_root)}")
                cursor.execute(read_sql(sql_file))
        print("Fresh RuoYi + Quant Road initialization completed successfully.")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # pragma: no cover - CLI error path
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
