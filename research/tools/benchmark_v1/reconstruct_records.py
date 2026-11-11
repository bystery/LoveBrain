# -*- coding: utf-8 -*-
"""从 checkpoint 目录重建 run_records.json（reconstruct_records_v1）。

背景：多轮 sweep 用 --arms 子集调用 four_arm_runner，每次进程重写 run_records.json，
导致早前臂的记录被覆盖丢失。目录证据（checkpoint.json 的 turns_done/candidate）仍在，
本工具按目录事实重建完整 records 列表——目录是唯一事实源，records 是可再生的派生物。

重建规则（诚实口径）：
- checkpoint 存在且 turns_done == 该篇 turn_count → status=ok, turns_done=N；
- checkpoint 存在但 turns_done < turn_count → status=error（该篇自失败轮起缺失），
  turns_done_before_error=N；
- 无 checkpoint 目录 → 整篇未运行，跳过（不计入 records）；
- candidate 取 checkpoint 的 candidate 字段；candidate_dir 由 ARM_REGISTRY 身份回填
  需要运行器上下文，此处留 arm 级标注（候选名已在 checkpoint 内）。

用法：
  python -I tools/benchmark_v1/reconstruct_records.py --run-dir runs/pilot2_wave4_live_b \
      --turn-counts E301=12,E302=12,...
"""
import argparse
import glob
import json
import os


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-dir", required=True)
    ap.add_argument("--turn-counts", default=None,
                    help="逗号分隔 E3xx=轮数（或用 --plan 自动读）")
    ap.add_argument("--plan", default=None, help="从 plan.json 的 episodes.turn_count 读取")
    ap.add_argument("--apply", action="store_true", help="默认 dry-run")
    a = ap.parse_args()
    tc = {}
    if a.plan:
        plan = json.load(open(a.plan, encoding="utf-8"))
        for eid, m in (plan.get("episodes") or {}).items():
            tc[eid] = m["turn_count"]
    elif a.turn_counts:
        for kv in a.turn_counts.split(","):
            k, v = kv.split("=")
            tc[k.strip()] = int(v)
    else:
        raise SystemExit("--turn-counts 与 --plan 至少给一个")
    records = []
    for ck_path in sorted(glob.glob(os.path.join(a.run_dir, "*", "*", "checkpoint.json"))):
        arm, eid = os.path.relpath(os.path.dirname(ck_path), a.run_dir).replace(os.sep, "/").split("/")
        ck = json.load(open(ck_path, encoding="utf-8"))
        n_full = tc.get(eid)
        done = int(ck.get("turns_done") or 0)
        rec = {"arm": arm, "episode": eid, "mode": "live", "candidate": ck.get("candidate"),
               "_reconstructed": True}
        if n_full is not None and done >= n_full:
            rec.update({"status": "ok", "turns_done": done})
        else:
            rec.update({"status": "error", "turns_done_before_error": done,
                        "error": "incomplete (checkpoint turns_done=%s/%s)" % (done, n_full),
                        "note": "该故事自失败轮起缺失，不伪造完整结果（目录重建）"})
        records.append(rec)
    out = os.path.join(a.run_dir, "run_records.json")
    print("reconstructed %d records -> %s%s" % (len(records), out, " (APPLY)" if a.apply else " (dry-run)"))
    from collections import Counter
    print(dict(Counter((r["arm"], r["status"]) for r in records)))
    if a.apply:
        with open(out, "w", encoding="utf-8", newline="\n") as f:
            json.dump(records, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
