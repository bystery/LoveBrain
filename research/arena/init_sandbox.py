#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
斗兽场沙箱初始化器（确定性工具，非循环驱动）
用法:
  python arena/init_sandbox.py --task benchmark/tasks/T01_RECALL.json --out sandboxes/round_1_T01_RECALL
作用: 读取题目 JSON 的 sandbox_seed，把种子文件铺到一个一次性沙箱目录，并建 _arena/ 目录。
安全: 拒绝覆盖已存在的输出目录（防误覆盖历史沙箱）。
"""
import argparse
import json
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--task", required=True, help="题目 JSON 路径")
    ap.add_argument("--out", required=True, help="沙箱输出目录")
    args = ap.parse_args()

    task = json.loads(Path(args.task).read_text(encoding="utf-8"))
    out = Path(args.out).resolve()

    if out.exists() and any(out.iterdir()):
        print(f"[ABORT] 沙箱目录已存在且非空: {out}\n"
              f"       如需重跑请换目录名（如 _retry 后缀），禁止覆盖历史沙箱。")
        sys.exit(2)

    out.mkdir(parents=True, exist_ok=True)

    files = task.get("sandbox_seed", {}).get("files", {})
    for rel, content in files.items():
        fpath = out / rel
        fpath.parent.mkdir(parents=True, exist_ok=True)
        fpath.write_text(content, encoding="utf-8")

    (out / "_arena").mkdir(exist_ok=True)

    n = len(files)
    print(f"[OK] 沙箱就绪: {out}")
    print(f"     题目: {task['id']} ({task['name']}) | 种子文件 {n} 个 | 输入轮数 {task.get('rounds', 1)}")
    print(f"     下一步: 按 arena/harness_loader.md 孵化被测 subagent，回复存 {out / '_arena' / 'reply_round1.txt'}")


if __name__ == "__main__":
    main()
