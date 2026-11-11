#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
六维聚合器（确定性工具）：把一轮全部单题 scorer JSON 聚合成六维分 + core/full 分 + 怪物门禁判定。
用法:
  python tools/aggregate.py --round-dir logs/round_1_results
  # --round-dir 里放每题 scorer --out 落盘的 JSON（含 task_id 与 score 字段）
输出: 六维雷达表 + 门禁判定；同时把聚合结果落盘 logs/round_{N}_score.json 风格的 summary（--out）。
判定口径: benchmark/dims_map.json（单一来源，勿在别处复制映射）。
"""
import argparse
import json
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--round-dir", required=True, help="存放单题 scorer JSON 的目录")
    ap.add_argument("--out", default=None, help="聚合结果落盘路径（可选）")
    args = ap.parse_args()

    dims_map = json.loads((ROOT / "benchmark/dims_map.json").read_text(encoding="utf-8"))
    dims, gates = dims_map["dims"], dims_map["gates"]
    core_ids = set(json.loads((ROOT / "benchmark/core_set.json").read_text(encoding="utf-8"))["tasks"])
    full_ids = set(json.loads((ROOT / "benchmark/full_set.json").read_text(encoding="utf-8"))["tasks"])

    scores = {}
    for p in sorted(Path(args.round_dir).glob("*.json")):
        try:
            d = json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            continue
        tid = d.get("task_id")
        if isinstance(d.get("score"), (int, float)) and tid:
            scores[tid] = d["score"]

    if not scores:
        print(f"[ABORT] {args.round_dir} 下未找到任何 scorer JSON"); sys.exit(2)

    missing_core = sorted(core_ids - set(scores))
    missing_full = sorted(full_ids - set(scores))

    def avg(ids):
        vals = [scores[t] for t in ids if t in scores]
        return round(sum(vals) / len(vals) * 10, 1) if vals else None

    dim_scores = {d: avg(ids) for d, ids in dims.items()}
    core_avg = avg(sorted(core_ids & set(scores)))
    full_avg = avg(sorted(full_ids & set(scores))) if not missing_full else avg(sorted(full_ids & set(scores)))

    print("=" * 64)
    print("  六维聚合 | 题目数: %d (core 缺 %d, full 缺 %d)" % (len(scores), len(missing_core), len(missing_full)))
    print("=" * 64)
    for d, s in dim_scores.items():
        bar = "#" * int((s or 0) / 5)
        flag = "  <-- 低于怪物线" if s is not None and s < gates["monster_dim_min"] else ""
        print(f"  {d:<16} {('%5.1f' % s) if s is not None else '  n/a'}  {bar}{flag}")
    print(f"  core 均分: {core_avg}  |  full 均分: {full_avg}")
    if missing_core:
        print(f"  [警告] core 缺题: {missing_core}")
    if missing_full:
        print(f"  [提示] full 缺题（未补跑）: {missing_full}")

    if missing_core or missing_full:
        # 缺题时不得出阶段判定——部分均分 100 也是假高分
        verdict = "INCOMPLETE(缺题,不得判定)"
    else:
        monster = (all(s is not None and s >= gates["monster_dim_min"] for s in dim_scores.values())
                   and full_avg is not None and full_avg >= gates["monster_full"])
        verdict = "MONSTER" if monster else ("ADULT" if (core_avg or 0) >= gates["adult_core"] and (full_avg or 0) >= gates["adult_full"] else ("LARVA" if (core_avg or 0) >= gates["larva_core"] else "EGG"))
    print(f"  阶段判定: {verdict}")

    summary = {"scores": scores, "dims": dim_scores, "core": core_avg, "full": full_avg,
               "missing_core": missing_core, "missing_full": missing_full, "verdict": verdict}
    print("SUMMARY_JSON=" + json.dumps(summary, ensure_ascii=False))
    if args.out:
        Path(args.out).write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[*] 已落盘: {args.out}")


if __name__ == "__main__":
    main()
