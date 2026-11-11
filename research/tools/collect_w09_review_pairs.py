#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""W09 评审配对收集器：扫描 phase 输出目录，按（场景,反馈）配对双臂 complete 轨迹，
输出可执行的评审调用清单（双序）与状态报告。0 API。"""
import argparse
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dirs", nargs="+", required=True, help="W09 phase 输出目录（相对仓库根或绝对）")
    ap.add_argument("--arms", nargs="+",
                    default=["reference_product_v1", "v6_integrated"])
    ap.add_argument("--out", default=None, help="清单输出 json 路径（默认 stdout）")
    a = ap.parse_args()

    traces = {}
    for d in a.dirs:
        dd = d if os.path.isabs(d) else os.path.join(ROOT, d)
        for f in glob.glob(os.path.join(dd, "TRACE_*.json")):
            try:
                t = json.load(open(f, encoding="utf-8"))
            except Exception as e:                       # noqa: BLE001
                print("WARN unreadable %s: %s" % (f, e), file=sys.stderr)
                continue
            tid = t.get("trajectory_id") or os.path.basename(f)[6:-5]
            traces[tid] = {"path": os.path.relpath(f, ROOT).replace("\\", "/"),
                           "status": t.get("status"), "arm": t.get("arm"),
                           "scenario": t.get("scenario"), "feedback": t.get("feedback")}

    cells = {}
    for tid, t in traces.items():
        key = (t["scenario"], t["feedback"])
        cells.setdefault(key, {})[t["arm"]] = tid

    pairs, incomplete = [], []
    for (sid, fb), arms in sorted(cells.items()):
        ra, rb = a.arms[0], a.arms[1]
        if ra in arms and rb in arms:
            ta, tb = traces[arms[ra]], traces[arms[rb]]
            if ta["status"] == "complete" and tb["status"] == "complete":
                pairs.append({"pair_id": "%s_%s_pair" % (sid, fb),
                              "trace_a": ta["path"], "trace_b": tb["path"],
                              "arm_a": ra, "arm_b": rb})
            else:
                incomplete.append({"cell": "%s/%s" % (sid, fb),
                                   "status": {ra: ta["status"], rb: tb["status"]}})
        else:
            missing = [x for x in (ra, rb) if x not in arms]
            incomplete.append({"cell": "%s/%s" % (sid, fb), "missing_arms": missing,
                               "present": sorted(arms)})

    report = {"scanned_dirs": a.dirs, "trajectories": len(traces),
              "pairs_ready": pairs, "incomplete_cells": incomplete,
              "review_calls_planned": len(pairs) * 2}
    out = json.dumps(report, ensure_ascii=False, indent=1)
    if a.out:
        p = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out)
        with open(p, "w", encoding="utf-8") as f:
            f.write(out + "\n")
        print("WROTE", p)
    print("trajectories=%d pairs_ready=%d incomplete_cells=%d review_calls=%d"
          % (len(traces), len(pairs), len(incomplete), len(pairs) * 2))
    for c in incomplete:
        print("  INCOMPLETE", c)
    return 0


if __name__ == "__main__":
    sys.exit(main())
