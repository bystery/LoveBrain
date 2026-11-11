# -*- coding: utf-8 -*-
"""tools/fs1_first_loop_verdict.py — 首环 KEEP/REWORK/ELIMINATE 判定（机械执行已登记阈值）。

阈值权威：W04-03_VERDICT_THRESHOLDS_PREREG.json（九维结果落地前登记）。
输入：W04-03_FS1_NINEDIM_REVIEW.json + 两批 DONE.json + FULL_SCORECARD。
输出：W04-03_FIRST_LOOP_VERDICT.json + 控制台摘要。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GHP = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030")
NINE = os.path.join(GHP, "W04-03_FS1_NINEDIM_REVIEW.json")
TH = os.path.join(GHP, "W04-03_VERDICT_THRESHOLDS_PREREG.json")
BATCHES = {"A": ("E3", os.path.join(ROOT, "logs", "fs1_a_live2_20261030")),
           "B": ("Bmin", os.path.join(ROOT, "logs", "fs1_b_live2_20261030"))}


def main():
    nine = json.load(open(NINE, encoding="utf-8"))
    th = json.load(open(TH, encoding="utf-8"))
    MATERIAL_DEFECT_CELLS = {"E021_T07"}  # E-MAT-E021-T07：时钟×卡面矛盾，双口径聚合
    verdicts = {}
    for bk, (cand, broot) in BATCHES.items():
        done = json.load(open(broot + ".DONE.json", encoding="utf-8"))
        s = nine["summary"][bk]
        dims = s["dims"]
        # 双口径：material_defect 位剔除后重算配对差（逐 verdict 过滤）
        rows_all = [v for v in nine["verdicts"] if v["batch"] == bk]
        rows_ex = [v for v in rows_all if v["pair_id"].split(":")[-1] not in MATERIAL_DEFECT_CELLS]

        def dims_from(rows):
            out = {}
            for d in ["goal", "fact", "attrib", "time", "boundary", "memory",
                      "interact", "init", "emotion"]:
                diffs = [v["dims"]["candidate"][d] - v["dims"]["parent"][d]
                         for v in rows
                         if v.get("dims", {}).get("candidate", {}).get(d) is not None
                         and v.get("dims", {}).get("parent", {}).get(d) is not None]
                out[d] = (sum(diffs) / len(diffs)) if diffs else None
            return out
        dims_ex = dims_from(rows_ex)
        notes, blockers = [], []
        sev_c, sev_p = s["severe_primary_candidate"], s["severe_primary_parent"]
        if sev_c > sev_p:
            blockers.append("severe 增多: cand %d > parent %d" % (sev_c, sev_p))
        else:
            notes.append("severe 不增（cand %d / parent %d）" % (sev_c, sev_p))
        reg = th["thresholds"]
        if bk == "A":
            # E3 保护维（主口径=剔除素材缺陷位）+ 成本
            for d in reg["protected_dims_E3"]:
                st = dims_ex.get(d)
                pd = st
                st_all = (dims.get(d) or {})
                if pd is not None and pd <= -0.5 and st_all.get("parent_wins", 0) > st_all.get("cand_wins", 0):
                    blockers.append("保护维退化 %s: diff=%.2f（剔除素材缺陷位口径）" % (d, pd))
            cp, cc = done["cost_parent"], done["cost_candidate"]
            for k in ("prompt", "completion"):
                if cc[k] > cp[k] * 1.10:
                    notes.append("成本交易: candidate %s +%.1f%%（需报告）" % (k, (cc[k] / cp[k] - 1) * 100))
            notes.append("prompt %.1f%%, p50 %.1f%%" % (
                (cc["prompt"] / cp["prompt"] - 1) * 100, (cc["latency_p50_ms"] / cp["latency_p50_ms"] - 1) * 100))
            notes.append("双口径保护维（含/不含素材缺陷位）: " + "; ".join(
                "%s %.2f/%.2f" % (d, (dims.get(d) or {}).get("paired_diff_mean") or 0,
                                  dims_ex[d] or 0) for d in reg["protected_dims_E3"]))
            verdict = "KEEP" if not blockers else ("ELIMINATE" if any("severe 增多" in b for b in blockers) else "REWORK")
        else:
            # Bmin C1/C2/C3 逐条（主口径=剔除素材缺陷位）
            def diff(d):
                return dims_ex.get(d)
            c1 = all(diff(d) is not None and abs(diff(d)) <= 0.3 for d in ("interact", "emotion"))
            c2_dims = [d for d in ("goal", "memory", "time")
                       if diff(d) is not None and diff(d) <= -0.5]
            c2 = len(c2_dims) >= 2
            c3 = done["cost_candidate"]["prompt"] < done["cost_parent"]["prompt"]
            cp, cc = done["cost_parent"], done["cost_candidate"]
            c3_pct = (cc["prompt"] / cp["prompt"] - 1) * 100
            notes.append("C1 日常承接接近=%s（interact %.2f / emotion %.2f）" % (
                c1, diff("interact") or 0, diff("emotion") or 0))
            notes.append("C2 跨话题/意图弱证实=%s（弱维: %s）" % (c2, c2_dims or "无"))
            notes.append("C3 prompt 更短=%s（%.1f%%）" % (c3, c3_pct))
            if blockers:
                verdict = "ELIMINATE"
            elif c2:
                verdict = "REWORK"  # 失败即答案：C2 证实=整体不晋级
                notes.append("预登记处置：C2 证实，整体不晋级；C3 若显著可记'低复杂度位专用'用途定位")
            elif c1 and c3:
                verdict = "KEEP"
            else:
                verdict = "REWORK"
        verdicts[bk] = {"candidate": cand, "verdict": verdict, "blockers": blockers,
                        "notes": notes, "severe": {"candidate": sev_c, "parent": sev_p}}
    out = {"kind": "fs1_first_loop_verdict", "date": "2026-10-30",
           "thresholds": TH, "judge_status": "PARTIAL_QUALIFIED(fact/time→B3亲读)",
           "verdicts": verdicts,
           "w_note": "W 愿发未知不阻塞不代填；跨批横比探索性不进判决"}
    jp = os.path.join(GHP, "W04-03_FIRST_LOOP_VERDICT.json")
    json.dump(out, open(jp, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(jp, encoding="utf-8"))
    for bk, v in verdicts.items():
        print("FS1_%s %s -> %s" % (bk, v["candidate"], v["verdict"]))
        for n in v["blockers"]:
            print("  [blocker]", n)
        for n in v["notes"]:
            print("  -", n)
    print("verdict:", jp)
    return 0


if __name__ == "__main__":
    sys.exit(main())
