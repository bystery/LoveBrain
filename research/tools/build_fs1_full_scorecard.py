# -*- coding: utf-8 -*-
"""tools/build_fs1_full_scorecard.py — W04-04 FULL_SCORECARD（复用现输出，零生成调用）。

T=契约/解析  P=程序红线  C=语义契约（九维+资格降级处置）  S=严重错误  W=愿发（未知，不代填）。
输入：两批 out 目录 scoring.json / reply_raw.txt + W04-03_FS1_NINEDIM_REVIEW.json + DONE.json。
输出：W04-04_FS1_FULL_SCORECARD.json / .md。任何部分缺输入 → 如实标 MISSING，不补 0。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GHP = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030")
STORIES = ["E021", "E026", "E032", "E033"]
BATCHES = {"FS1_A": ("E3", os.path.join(ROOT, "logs", "fs1_a_live2_20261030")),
           "FS1_B": ("Bmin", os.path.join(ROOT, "logs", "fs1_b_live2_20261030"))}
NINEDIM = os.path.join(GHP, "W04-03_FS1_NINEDIM_REVIEW.json")


def load_scoring(batch_root, arm, sid, idx, cid):
    p = os.path.join(batch_root, "%s_%s" % (arm, sid), "out", "turn_%02d_%s" % (idx, cid),
                     "scoring.json")
    try:
        return json.load(open(p, encoding="utf-8"))
    except Exception:  # noqa: BLE001
        return None


def main():
    nine = None
    if os.path.isfile(NINEDIM):
        nine = json.load(open(NINEDIM, encoding="utf-8"))
    scorecard = {"kind": "w04_04_fs1_full_scorecard", "date": "2026-10-30",
                 "ninedim_included": nine is not None, "batches": {}}
    for bkey, (cand_name, broot) in BATCHES.items():
        done = json.load(open(broot + ".DONE.json", encoding="utf-8"))
        rows = []
        for sid in STORIES:
            for idx in range(1, 13):
                cid = "%s_T%02d" % (sid, idx)
                for arm, arm_name in (("parent", "B0"), ("candidate", cand_name)):
                    sc = load_scoring(broot, arm, sid, idx, cid)
                    rp = os.path.join(broot, "%s_%s" % (arm, sid), "out",
                                      "turn_%02d_%s" % (idx, cid), "reply_raw.txt")
                    parsed = False
                    if os.path.isfile(rp):
                        try:
                            json.loads(open(rp, encoding="utf-8").read())
                            parsed = True
                        except Exception:  # noqa: BLE001
                            pass
                    fails = []
                    veto = False
                    if sc:
                        veto = bool(sc.get("veto"))
                        for c in sc.get("checks", []):
                            if c.get("pts", 0) < c.get("pts_max", c.get("pts", 0)):
                                fails.append(c.get("id", "?"))
                    rows.append({"case_id": cid, "arm": arm_name,
                                 "T_parse_ok": parsed,
                                 "P_veto": veto,
                                 "program_score10": sc.get("score10") if sc else None,
                                 "check_failures": fails})
        sev = {}
        if nine:
            for v in nine["verdicts"]:
                if v["batch"] == bkey[-1]:
                    sev[v["pair_id"]] = v["severe"]
        batch_out = {
            "candidate": cand_name,
            "cost": {"parent": done.get("cost_parent"), "candidate": done.get("cost_candidate")},
            "program_mean10": done.get("program_mean10"),
            "n_positions": len(rows) // 2,
            "T_parse_fail": sum(1 for r in rows if not r["T_parse_ok"]),
            "P_veto_count": sum(1 for r in rows if r["P_veto"]),
            "S_severe": {"candidate": sum(1 for v in sev.values() if v.get("candidate")),
                         "parent": sum(1 for v in sev.values() if v.get("parent")),
                         "note": "judge 旗标；fact/time 维以 B3 亲读为准（资格降级）"},
            "W_willingness": "UNKNOWN（12 对人工样本待用户，AI 不代投）",
            "rows": rows,
            "errata_entries": sorted({f for r in rows for f in r["check_failures"]}),
        }
        if nine:
            s = nine["summary"][bkey[-1]]
            batch_out["C_ninedim"] = {d: st for d, st in s["dims"].items()}
            batch_out["backup_prescreen_flagged"] = nine.get("backup_prescreen_flagged")
            batch_out["backup_adjudicated_severe"] = nine.get("backup_adjudicated_severe")
        scorecard["batches"][bkey] = batch_out

    jp = os.path.join(GHP, "W04-04_FS1_FULL_SCORECARD.json")
    json.dump(scorecard, open(jp, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(jp, encoding="utf-8"))
    md = ["# W04-04 FS1 FULL_SCORECARD（2026-10-30）", "",
          "| 批 | 候选 | 有效配对 | T解析失败 | P veto | C九维(候选vs B0，均值差) | S严重(候选/parent) | W愿发 |",
          "|---|---|---|---|---|---|---|---|"]
    for bkey, b in scorecard["batches"].items():
        c9 = ""
        if "C_ninedim" in b:
            c9 = "; ".join("%s %s" % (d, st["paired_diff_mean"]) for d, st in
                           b["C_ninedim"].items() if st["paired_diff_mean"] is not None)
        md.append("| %s | %s | %d | %d | %d | %s | %s | %s |" % (
            bkey, b["candidate"], b["n_positions"], b["T_parse_fail"], b["P_veto_count"],
            c9 or "MISSING(九维未落)",
            "%s / %s" % (b["S_severe"]["candidate"], b["S_severe"]["parent"]),
            b["W_willingness"]))
    md += ["", "程序分: " + json.dumps({k: v["program_mean10"] for k, v in
                                        scorecard["batches"].items()}, ensure_ascii=False),
           "prompt token: " + json.dumps({k: {a: c["prompt"] for a, c in v["cost"].items()}
                                          for k, v in scorecard["batches"].items()},
                                          ensure_ascii=False),
           "", "资格降级处置：fact/time 维结论以 B3 亲读+程序层红旗为准（W04-03_JUDGE_QUAL_REPORT PARTIAL_QUALIFIED）"]
    open(jp.replace(".json", ".md"), "w", encoding="utf-8").write("\n".join(md))
    print("scorecard:", jp)
    print("\n".join(md[:10]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
