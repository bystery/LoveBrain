# -*- coding: utf-8 -*-
"""tools/c4_primaries_view.py — 四臂首选原文的紧凑阅读视图（离线，零真实调用，零判定）。

只为初审时"一次看全四位同题首选"服务；判定写在 evaluation/C4_FIRST_ROUND_DISPOSITION_V1.md，
本脚本不产出任何结论。用法：
  python tools/c4_primaries_view.py            # 全部 32 位
  python tools/c4_primaries_view.py E006 E012   # 指定故事
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "evaluation", "C4_FOUR_ARM_REVIEW_PACK_V1.json")
ARMS = ["reference_product_v1", "v5_purpose_aligned", "goal_action_v1", "evidence_memory_v1"]
SHORT = {"reference_product_v1": "原版", "v5_purpose_aligned": "v5",
         "goal_action_v1": "C2目标", "evidence_memory_v1": "C3记忆"}


def main():
    doc = json.load(open(PACK, encoding="utf-8"))
    filt = sys.argv[1:]
    n = 0
    for r in doc["loci"]:
        if filt and r["episode"] not in filt:
            continue
        n += 1
        c = r["card"] or {}
        her = " ‖ ".join("%s" % m.get("text", "") for m in (r["her_messages"] or []))
        print("── %s [goal_live=%s] HER: %s" % (r["case_id"], r["goal_live"], her[:120]))
        print("   note: %s" % (r.get("note") or "（无）"))
        print("   卡 allowed: %s" % json.dumps(c.get("allowed_behaviors"), ensure_ascii=False)[:230])
        print("   卡 failure: %s" % json.dumps(c.get("failure_conditions"), ensure_ascii=False)[:230])
        for a in ARMS:
            d = r["arms"].get(a)
            if not d:
                print("   %-6s —" % SHORT[a])
                continue
            marks = "" if (d["P"] and d["K"] and d["full_slot"]) else \
                " ⟨%s%s%s⟩" % ("" if d["P"] else "P✗ ", "" if d["K"] else "K✗ ",
                               "" if d["full_slot"] else "部分槽:" + str(d["partial_reason"]))
            print("   %-6s %s%s" % (SHORT[a],
                                    json.dumps(d["primary"], ensure_ascii=False)
                                    if d["primary"] else "（无首选 no_reply=%s）" % d["no_reply"],
                                    marks))
        print()
    print("loci=%d" % n)


if __name__ == "__main__":
    main()
