#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
题库封板锁（确定性工具）：对 benchmark/ 全部文件算 sha256 并封存/校验。
用法:
  python tools/seal_benchmark.py --seal     # 封板：生成 benchmark/LOCK.sha256
  python tools/seal_benchmark.py --verify   # 校验：漂移即列清单并以退出码 1 报警
铁律: 封板后改动 benchmark/ = 改考试题，仅用户可授权；授权后须重新 --seal 并在 logs/decisions.md 留痕。
"""
import argparse
import hashlib
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent
BENCH = ROOT / "benchmark"
LOCK = BENCH / "LOCK.sha256"


def manifest():
    entries = {}
    for p in sorted(BENCH.rglob("*")):
        if not p.is_file() or p.name == "LOCK.sha256":
            continue
        rel = p.relative_to(BENCH).as_posix()
        entries[rel] = hashlib.sha256(p.read_bytes()).hexdigest()
    return entries


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--seal", action="store_true")
    g.add_argument("--verify", action="store_true")
    args = ap.parse_args()

    cur = manifest()
    if args.seal:
        lines = [f"{h}  {rel}" for rel, h in sorted(cur.items())]
        LOCK.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"[SEALED] {len(cur)} 个文件已封存 -> {LOCK}")
        return

    if not LOCK.exists():
        print("[FAIL] LOCK.sha256 不存在，题库从未封板"); sys.exit(1)
    old = {}
    for line in LOCK.read_text(encoding="utf-8").splitlines():
        if line.strip():
            h, rel = line.split(None, 1)
            old[rel.strip()] = h
    drift = []
    for rel in sorted(set(old) | set(cur)):
        if rel not in old:
            drift.append(f"新增: {rel}")
        elif rel not in cur:
            drift.append(f"删除: {rel}")
        elif old[rel] != cur[rel]:
            drift.append(f"改动: {rel}")
    if drift:
        print("[DRIFT] 题库与封板不一致:")
        for d in drift:
            print("  " + d)
        sys.exit(1)
    print(f"[OK] 题库与封板一致（{len(cur)} 个文件）")


if __name__ == "__main__":
    main()
