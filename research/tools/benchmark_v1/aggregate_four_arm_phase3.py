# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 3 · 四组结果聚合器（aggregate_four_arm_phase3 v1，2026-11-09）。

输入 run 目录（<arm>/<episode>/<turn>/ 结构），产出宽表：
  每行 = 一个 (episode, case) 生成位；列 = 四组各自的
  primary_text / action / parse_ok / pcontract_ok / severe_error / generic_cancel_affirm /
  generic_required_hits/total / tokens / latency_ms；
  外加 topic / lineage_group / planned_split（从分派表 V3 join）。
用途：FOUR_ARM_RESULTS.csv（指导书 §P3-11）与专题切片、配对统计的输入。
只读 run 目录；零模型调用。

用法：
  python -I tools/benchmark_v1/aggregate_four_arm_phase3.py \
      --run-dir runs/<run> --out refs/benchmark_v1_20261107/phase3/FOUR_ARM_RESULTS.csv
"""
import argparse
import csv
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
import universal_scorer as us  # noqa: E402
import phase3_runner as p3  # noqa: E402

FIELDS_PER_ARM = ("action", "parse_ok", "pcontract_ok", "severe_error",
                  "generic_cancel_affirm", "generic_required_hits",
                  "generic_required_total", "tokens", "latency_ms")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-dir", required=True)
    ap.add_argument("--arms", default="DIRECT,COT,LEGACY,CURRENT")
    ap.add_argument("--allocation", default=os.path.join(
        os.path.dirname(_HERE), "..", "refs", "benchmark_v1_20261107", "phase3",
        "DATASET_V1_ALLOCATION_V3.csv"))
    ap.add_argument("--out", required=True)
    ap.add_argument("--summary", default=None)
    args = ap.parse_args()
    arms = tuple(a.strip().upper() for a in args.arms.split(",") if a.strip())
    rows, summary = us.score_run(args.run_dir, arms=arms)
    sm = p3.load_split_map(os.path.abspath(args.allocation)) if os.path.isfile(
        os.path.abspath(args.allocation)) else {}
    by_pos = {}
    for r in rows:
        key = (r["episode_id"], r["case_id"])
        by_pos.setdefault(key, {"episode_id": r["episode_id"], "case_id": r["case_id"]})
        arm = r["arm_id"]
        for f in FIELDS_PER_ARM:
            by_pos[key]["%s.%s" % (arm, f)] = r.get(f)
        by_pos[key]["%s.primary_text" % arm] = r.get("primary_text")
    header = ["episode_id", "case_id", "topic", "lineage_group_id", "planned_split"]
    for arm in arms:
        header += ["%s.%s" % (arm, f) for f in FIELDS_PER_ARM] + ["%s.primary_text" % arm]
    with open(args.out, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=header, extrasaction="ignore")
        w.writeheader()
        for key in sorted(by_pos):
            row = by_pos[key]
            meta = sm.get(row["episode_id"], {})
            row["topic"] = meta.get("topic", "")
            row["lineage_group_id"] = meta.get("lineage", "")
            row["planned_split"] = meta.get("planned_split", "")
            w.writerow(row)
    if args.summary:
        with open(args.summary, "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=1)
    print("rows=%d arms=%s -> %s" % (len(by_pos), arms, args.out))
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
