# -*- coding: utf-8 -*-
"""tools/c4_four_arm_analysis.py — C4 四臂程序层聚合与读数队列（离线，零真实调用，零语义判定）。

它只做三件事，且都写清分母：
  1) 每臂程序列聚合（T/P/C/K/full_slot/key_norm/no_reply）+ token 合计与 ÷C + 等待分位，
     【全部 32 位】与【目标块真生效 7 位】两个口径并列——因为 C2 的动作层只在后者被 exercise，
     单看聚合会把"层没被触发"读成"层没收益"（见 evaluation/C4_INTENT_LAYER_COVERAGE_V1.json）；
  2) 配对差（原版↔C2、v5↔C3、原版↔v5、C2↔v5）只报程序与资源差，不报"谁更会聊"；
  3) 生成"读数队列"：四臂程序列不一致、或某臂缺首选/部分槽/键名归一/veto 的位点清单，
     供逐位原文初审优先看（初审结论另 file，本脚本不写结论）。

用法：D:\\anaconda\\python.exe tools\\c4_four_arm_analysis.py
"""
import json
import os
import sys
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "evaluation", "C4_FOUR_ARM_REVIEW_PACK_V1.json")
COV = os.path.join(ROOT, "evaluation", "C4_INTENT_LAYER_COVERAGE_V1.json")
OUT = os.path.join(ROOT, "evaluation", "C4_FOUR_ARM_PROGRAM_AND_QUEUE_V1.json")
ARMS = ["reference_product_v1", "v5_purpose_aligned", "goal_action_v1", "evidence_memory_v1"]


def pct(vals, q):
    if not vals:
        return None
    s = sorted(vals)
    return s[min(len(s) - 1, int(len(s) * q))]


def agg(rows):
    a = defaultdict(lambda: {"n": 0, "T": 0, "P": 0, "C": 0, "K": 0, "full": 0,
                             "key_norm": 0, "no_reply": 0, "prompt": 0, "completion": 0,
                             "lat": [], "fc": [], "fu_nonnull": 0})
    for r in rows:
        for arm, d in r.items():
            if not d:
                continue
            s = a[arm]
            s["n"] += 1
            for k in ("T", "P", "C", "K"):
                s[k] += 1 if d[k] else 0
            s["full"] += 1 if d["full_slot"] else 0
            s["key_norm"] += d["key_norm_events"]
            s["no_reply"] += 1 if d["no_reply"] else 0
            s["prompt"] += int(d["prompt_tokens"] or 0)
            s["completion"] += int(d["completion_tokens"] or 0)
            if isinstance(d["latency_ms"], (int, float)):
                s["lat"].append(d["latency_ms"])
            if isinstance(d["first_content_ms"], (int, float)):
                s["fc"].append(d["first_content_ms"])
            if d["first_usable_ms"] is not None:
                s["fu_nonnull"] += 1
    out = {}
    for arm, s in a.items():
        n = s["n"] or 1
        total = s["prompt"] + s["completion"]
        out[arm] = {
            "n": s["n"],
            "T_P_C_K": [s["T"], s["P"], s["C"], s["K"]],
            "rates_pct": {k: round(s[k] / n * 100, 2) for k in ("T", "P", "C", "K")},
            "full_slot": s["full"], "key_norm_events": s["key_norm"],
            "no_reply_turns": s["no_reply"],
            "tokens": {"prompt": s["prompt"], "completion": s["completion"], "total": total},
            "token_per_extractable_primary": round(total / s["C"], 1) if s["C"] else None,
            "token_per_program_not_vetoed": round(total / s["K"], 1) if s["K"] else None,
            "latency_ms_p50_p95_max": [pct(s["lat"], .5), pct(s["lat"], .95),
                                       max(s["lat"]) if s["lat"] else None],
            "first_content_ms_p50_p95": [pct(s["fc"], .5), pct(s["fc"], .95)],
            "first_usable_nonnull": s["fu_nonnull"],
        }
    return out


def main():
    pack = json.load(open(PACK, encoding="utf-8"))
    cov = json.load(open(COV, encoding="utf-8"))
    live = set(cov["loci_goal_live"])
    loci = pack["loci"]
    all_rows, live_rows = [], []
    for r in loci:
        arms = {a: r["arms"].get(a) for a in ARMS}
        all_rows.append(arms)
        if r["case_id"] in live:
            live_rows.append(arms)

    queue = []
    for r in loci:
        present = {a: d for a, d in r["arms"].items() if d}
        if not present:
            continue
        flags = []
        pk = {a: (d["P"], d["C"], d["K"]) for a, d in present.items()}
        if len(set(pk.values())) > 1:
            flags.append("程序列不一致 %s" % {a: "".join("1" if x else "0" for x in v)
                                            for a, v in pk.items()})
        for a, d in present.items():
            if not d["full_slot"]:
                flags.append("%s 部分槽：%s" % (a, d["partial_reason"]))
            if d["key_norm_events"]:
                flags.append("%s 键名归一 %d 次" % (a, d["key_norm_events"]))
            if d["veto"]:
                flags.append("%s veto=%s" % (a, d["veto"]))
            if not d["C"]:
                flags.append("%s 无可提取首选(no_reply=%s)" % (a, d["no_reply"]))
        if flags:
            queue.append({"case_id": r["case_id"], "goal_live": r["case_id"] in live,
                          "reasons": flags})

    pair_defs = [("reference_product_v1", "goal_action_v1"),
                 ("v5_purpose_aligned", "evidence_memory_v1"),
                 ("reference_product_v1", "v5_purpose_aligned"),
                 ("goal_action_v1", "v5_purpose_aligned")]
    pairs = {}
    for x, y in pair_defs:
        both = [r for r in loci if r["arms"].get(x) and r["arms"].get(y)]
        if not both:
            pairs["%s|%s" % (x, y)] = {"n": 0, "note": "两臂未在同一批同时出现（等批B）"}
            continue
        tx = sum(int(r["arms"][x]["prompt_tokens"] or 0) + int(r["arms"][x]["completion_tokens"] or 0)
                 for r in both)
        ty = sum(int(r["arms"][y]["prompt_tokens"] or 0) + int(r["arms"][y]["completion_tokens"] or 0)
                 for r in both)
        cx = sum(1 for r in both if r["arms"][x]["C"])
        cy = sum(1 for r in both if r["arms"][y]["C"])
        pairs["%s|%s" % (x, y)] = {
            "n": len(both),
            "tokens_total": [tx, ty],
            "token_per_C": [round(tx / cx, 1) if cx else None, round(ty / cy, 1) if cy else None],
            "C": [cx, cy],
            "P": [sum(1 for r in both if r["arms"][x]["P"]),
                  sum(1 for r in both if r["arms"][y]["P"])],
            "K": [sum(1 for r in both if r["arms"][x]["K"]),
                  sum(1 for r in both if r["arms"][y]["K"])],
            "full_slot": [sum(1 for r in both if r["arms"][x]["full_slot"]),
                          sum(1 for r in both if r["arms"][y]["full_slot"])],
        }

    doc = {
        "kind": "c4_four_arm_program_and_queue",
        "source_pack": "evaluation/C4_FOUR_ARM_REVIEW_PACK_V1.json",
        "coverage": {"positions_total": cov["positions_total"],
                     "positions_goal_live": cov["positions_goal_live"],
                     "loci_goal_live": cov["loci_goal_live"]},
        "aggregate_all_positions": agg(all_rows),
        "aggregate_goal_live_only": agg(live_rows),
        "pairs_program_and_resource_only": pairs,
        "reading_queue": sorted(queue, key=lambda q: (not q["goal_live"], q["case_id"])),
        "reading_queue_n": len(queue),
        "not_claimed": ("本文件不含语义/口味判定：Q=unknown、W=unknown。程序列一致不代表聊得对，"
                        "差异位点必须读原文再判（队列已给）。"),
        "real_model_calls": 0,
    }
    json.dump(doc, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps({"loci_in_pack": len(loci),
                      "goal_live_rows": len(live_rows),
                      "queue_n": len(queue),
                      "aggregate_all_positions": {
                          a: {"n": v["n"], "T_P_C_K": v["T_P_C_K"],
                              "tok_per_C": v["token_per_extractable_primary"],
                              "lat_p50": v["latency_ms_p50_p95_max"][0],
                              "first_usable_nonnull": v["first_usable_nonnull"]}
                          for a, v in doc["aggregate_all_positions"].items()}},
                     ensure_ascii=False, indent=1))
    print("wrote", OUT.replace("\\", "/"))


if __name__ == "__main__":
    main()
