#!/usr/bin/env python3
"""
Lumina Qdrant tenant_id 回填脚本（一次性运维工具）

背景：
    修复向量层多租户隔离缺陷前的存量数据，Qdrant point payload 里没有 tenant_id，
    导致 doSearch 的 filter.must[].tenant_id 无法命中。本脚本从 MySQL
    lumina_knowledge_chunk（已是 tenant 真值来源）JOIN chunk_id，把 tenant_id/kb_id
    写回 Qdrant payload。

使用：
    pip install mysql-connector-python requests
    python scripts/backfill-qdrant-tenant.py \
        --mysql-host localhost --mysql-port 3306 --mysql-user root \
        --mysql-password 123456 --mysql-db lumina_dev \
        --qdrant-url http://localhost:6333 --collection lumina_knowledge \
        [--dry-run] [--batch-size 200]

注意：
    1. Qdrant point id 必须等于 lumina_knowledge_chunk.chunk_id（V28 起保证）
    2. chunk_id 不在 MySQL 的孤儿向量无法回填，建议回填后用 verify 模式核对
    3. 回填是幂等的：set payload 重复执行不会产生重复数据
    4. 干跑模式（--dry-run）只打印影响范围，不写 Qdrant
"""
import argparse
import json
import sys
from typing import List, Dict, Tuple

import mysql.connector
import requests


def fetch_chunks(mysql_cfg: Dict) -> List[Tuple[str, int, int]]:
    """从 MySQL 拉所有 chunk 的 (chunk_id, tenant_id, kb_id)"""
    conn = mysql.connector.connect(**mysql_cfg)
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT chunk_id, tenant_id, IFNULL(kb_id, 0) "
            "FROM lumina_knowledge_chunk WHERE deleted = 0"
        )
        rows = cur.fetchall()
        return [(r[0], int(r[1]), int(r[2])) for r in rows]
    finally:
        conn.close()


def qdrant_set_payload(qdrant_url: str, collection: str,
                       chunk_id: str, tenant_id: int, kb_id: int) -> bool:
    """调用 Qdrant POST /collections/{name}/points/payload 给单个 point 打 payload"""
    url = f"{qdrant_url.rstrip('/')}/collections/{collection}/points/payload"
    body = {
        "payload": {"tenant_id": tenant_id, "kb_id": kb_id},
        "points": [chunk_id],
    }
    resp = requests.post(url, json=body, timeout=30)
    if resp.status_code != 200:
        print(f"[WARN] Qdrant set payload 失败: chunk_id={chunk_id}, "
              f"status={resp.status_code}, body={resp.text[:200]}")
        return False
    return True


def verify_payload(qdrant_url: str, collection: str, chunk_id: str) -> Dict:
    """读取单个 point 的 payload 用于核对"""
    url = f"{qdrant_url.rstrip('/')}/collections/{collection}/points/{chunk_id}"
    resp = requests.get(url, timeout=10)
    if resp.status_code != 200:
        return {}
    data = resp.json().get("result", {})
    return data.get("payload", {}) if isinstance(data, dict) else {}


def main():
    ap = argparse.ArgumentParser(description="回填 Qdrant tenant_id")
    ap.add_argument("--mysql-host", default="localhost")
    ap.add_argument("--mysql-port", type=int, default=3306)
    ap.add_argument("--mysql-user", default="root")
    ap.add_argument("--mysql-password", default="123456")
    ap.add_argument("--mysql-db", default="lumina_dev")
    ap.add_argument("--qdrant-url", default="http://localhost:6333")
    ap.add_argument("--collection", default="lumina_knowledge")
    ap.add_argument("--batch-size", type=int, default=200)
    ap.add_argument("--dry-run", action="store_true", help="只打印影响范围，不写 Qdrant")
    ap.add_argument("--verify", action="store_true", help="回填后抽样核对 payload")
    args = ap.parse_args()

    mysql_cfg = {
        "host": args.mysql_host, "port": args.mysql_port,
        "user": args.mysql_user, "password": args.mysql_password,
        "database": args.mysql_db,
    }

    print(f"[1/3] 从 MySQL {args.mysql_db} 拉取 chunk 列表...")
    chunks = fetch_chunks(mysql_cfg)
    print(f"      共 {len(chunks)} 条 chunk 记录")

    if not chunks:
        print("[done] 无数据可回填")
        return

    # 按 tenant_id 分布统计
    tenant_dist: Dict[int, int] = {}
    for _, tid, _ in chunks:
        tenant_dist[tid] = tenant_dist.get(tid, 0) + 1
    print(f"      租户分布: {tenant_dist}")

    if args.dry_run:
        print(f"[dry-run] 模式：不写 Qdrant。回填将影响 {len(chunks)} 个 point。")
        return

    print(f"[2/3] 向 Qdrant {args.qdrant_url} collection={args.collection} 回填 payload...")
    ok, fail = 0, 0
    for i, (chunk_id, tenant_id, kb_id) in enumerate(chunks, 1):
        success = qdrant_set_payload(
            args.qdrant_url, args.collection, chunk_id, tenant_id, kb_id
        )
        if success:
            ok += 1
        else:
            fail += 1
        if i % args.batch_size == 0:
            print(f"      进度 {i}/{len(chunks)} (ok={ok}, fail={fail})")
    print(f"      完成: ok={ok}, fail={fail}, total={len(chunks)}")

    if args.verify and ok > 0:
        print("[3/3] 抽样核对（前 5 个）...")
        for chunk_id, tenant_id, kb_id in chunks[:5]:
            payload = verify_payload(args.qdrant_url, args.collection, chunk_id)
            actual_tid = payload.get("tenant_id")
            match = "OK" if actual_tid == tenant_id else "MISMATCH"
            print(f"      chunk_id={chunk_id} expected_tid={tenant_id} "
                  f"actual_tid={actual_tid} [{match}]")

    print("[done] 回填完成。如有 fail，检查 Qdrant 是否有对应 point（孤儿向量）。")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[ERROR] {e}", file=sys.stderr)
        sys.exit(1)
