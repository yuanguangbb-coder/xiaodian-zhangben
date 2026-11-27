#!/usr/bin/env python3
"""校验 Room 迁移脚本和导出的 schema 是否一致（不需要手机/模拟器）。

原理：
  1. 从 app/schemas/.../<version>.json 读出 Room 期望的最终表结构（字段、索引）。
  2. 在内存 SQLite 里建一个 v1 老库，按 AppDatabase.kt 里的迁移 SQL 一步步升到最新版本。
  3. 比对列定义和索引；不一致就退出码 1。

⚠️ 加了新的 MIGRATION_X_Y 时，记得同步更新下面的 MIGRATIONS 列表。

用法：python scripts/verify_migrations.py
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_DIR = ROOT / "app" / "schemas" / "com.example.ledger.data.db.AppDatabase"

# v1 的建表语句（最初版本，没有 createdAtMillis / productId / quantity / deletedAtMillis，
# 也没有 customers / debts / products 表）
V1_SQL = [
    """
    CREATE TABLE transactions (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        isIncome INTEGER NOT NULL,
        amountCents INTEGER NOT NULL,
        categoryId INTEGER NOT NULL,
        accountId INTEGER NOT NULL,
        timeMillis INTEGER NOT NULL,
        note TEXT NOT NULL,
        receiptPath TEXT
    )
    """,
    "CREATE INDEX index_transactions_timeMillis ON transactions (timeMillis)",
    "CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, isIncome INTEGER NOT NULL)",
    "CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)",
    "INSERT INTO categories(name, isIncome) VALUES ('销售收入', 1)",
    "INSERT INTO accounts(name) VALUES ('微信')",
    "INSERT INTO transactions(isIncome, amountCents, categoryId, accountId, timeMillis, note)"
    " VALUES (0, 1234, 1, 1, 1600000000000, 'old-bill')",
]

# 和 AppDatabase.kt 的 MIGRATION_* 一一对应（SQL 保持一致）
MIGRATIONS = {
    2: [
        "ALTER TABLE transactions ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0",
        "UPDATE transactions SET createdAtMillis = timeMillis WHERE createdAtMillis = 0",
    ],
    3: [
        "CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_products_name ON products (name)",
        "ALTER TABLE transactions ADD COLUMN productId INTEGER",
        "ALTER TABLE transactions ADD COLUMN quantity INTEGER",
    ],
    4: [
        "ALTER TABLE transactions ADD COLUMN deletedAtMillis INTEGER",
        "CREATE TABLE IF NOT EXISTS customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
        "name TEXT NOT NULL, phone TEXT NOT NULL, createdAtMillis INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_customers_name ON customers (name)",
        "CREATE TABLE IF NOT EXISTS debts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
        "customerId INTEGER NOT NULL, amountCents INTEGER NOT NULL, timeMillis INTEGER NOT NULL, "
        "note TEXT NOT NULL, settledAtMillis INTEGER)",
        "CREATE INDEX IF NOT EXISTS index_debts_customerId ON debts (customerId)",
        "CREATE INDEX IF NOT EXISTS index_debts_timeMillis ON debts (timeMillis)",
    ],
    5: [
        "ALTER TABLE debts ADD COLUMN incomeTransactionId INTEGER",
    ],
}


def load_target_schema():
    files = sorted(SCHEMA_DIR.glob("*.json"), key=lambda p: int(p.stem))
    if not files:
        sys.exit("no exported schema found under " + str(SCHEMA_DIR))
    latest = files[-1]
    data = json.loads(latest.read_text(encoding="utf-8"))
    db = data["database"]
    version = db["version"]
    if int(latest.stem) != version:
        sys.exit(f"schema file {latest.name} says version {version}")
    entities = {}
    for entity in db["entities"]:
        # Room 把主键写在 entity["primaryKey"]["columnNames"]，字段里没有 pk 位置
        pk_columns = set((entity.get("primaryKey") or {}).get("columnNames") or [])
        columns = {}
        for f in entity["fields"]:
            columns[f["columnName"]] = (
                f["affinity"].upper(),
                bool(f["notNull"]),
                1 if f["columnName"] in pk_columns else 0,
            )
        indices = {}
        for idx in entity.get("indices", []):
            indices[idx["name"]] = (bool(idx["unique"]), tuple(idx["columnNames"]))
        entities[entity["tableName"]] = {"columns": columns, "indices": indices}
    return version, entities


def migrated_tables():
    conn = sqlite3.connect(":memory:")
    for sql in V1_SQL:
        conn.execute(sql)
    for version in sorted(MIGRATIONS):
        for sql in MIGRATIONS[version]:
            conn.execute(sql)
    conn.commit()
    return conn


def actual_columns(conn, table):
    return {
        row[1]: (row[2].upper(), bool(row[3]), int(row[5]))
        for row in conn.execute(f"PRAGMA table_info(`{table}`)")
    }


def actual_indices(conn, table):
    result = {}
    for row in conn.execute(f"PRAGMA index_list(`{table}`)"):
        name, unique = row[1], bool(row[2])
        cols = tuple(r[2] for r in conn.execute(f"PRAGMA index_info(`{name}`)"))
        result[name] = (unique, cols)
    return result


def main():
    version, expected = load_target_schema()
    if max(MIGRATIONS) != version:
        sys.exit(f"schema version {version} has no migration entry in MIGRATIONS (max={max(MIGRATIONS)})")

    conn = migrated_tables()
    ok = True
    for table, spec in expected.items():
        cols = actual_columns(conn, table)
        if cols != spec["columns"]:
            ok = False
            print(f"[FAIL] {table} 列不匹配")
            for name in sorted(set(cols) | set(spec["columns"])):
                if cols.get(name) != spec["columns"].get(name):
                    print(f"        {name}: db={cols.get(name)} room={spec['columns'].get(name)}")
        idx = actual_indices(conn, table)
        if idx != spec["indices"]:
            ok = False
            print(f"[FAIL] {table} 索引不匹配")
            for name in sorted(set(idx) | set(spec["indices"])):
                if idx.get(name) != spec["indices"].get(name):
                    print(f"        {name}: db={idx.get(name)} room={spec['indices'].get(name)}")

    # 老数据要能顺利升上来
    row = conn.execute("SELECT timeMillis, createdAtMillis FROM transactions").fetchone()
    if row is None or row[0] != row[1]:
        ok = False
        print("[FAIL] v1 老账单在迁移后丢失，或 createdAtMillis 没回填")

    print(f"schema v{version}: " + ("ALL OK" if ok else "FAILED"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())