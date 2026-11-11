# -*- coding: utf-8 -*-
r"""tools/audit_silence_menu_20261028.py — 零调用审计：已付费答卷里"不回"何时被提供、被选、被许可。

动机（不是重复劳动，是同类半成品排查）：FIX-6/FIX-9 改的是**菜单里何时提供不回动作**。
读代码即知父本 v6 的 _capabilities 在 hint=忙碌/暂缓/拒绝 也把 收尾/停 放进菜单，而 r1/r2 的
"不回剔除"条件只盯 hint==收尾 一种；r1/r2 又把 收尾 的语义改写成"本轮不发任何消息"。
两者叠加 → 在 may_decline=false 的 忙碌/暂缓/拒绝 轮，菜单可能提供一个"选了就可能被
评分器判 unlicensed_no_reply"的动作。这类缺口不该靠推测，本工具用**已付费的真实轨迹**逐位核：

  1) 机制自报的 signal_hint / caps / action（turn_*/mechanism_notes.txt，真实运行日志）；
  2) 该位点是否 may_decline（该批冻结卡文件里的 may_decline 字段）；
  3) 模型是否真的走了不回（reply_raw.txt 的 action 键 或 analysis.action_taken）；
  4) 评分器判定（manifest.json 的 veto/score10）；
  5) 卡面是否把"不回"写成可接受（current_action/acceptable_actions 里出现"不回/静默"）。

输出 UTF-8 JSON；stdout 只打 ASCII 摘要（Windows 控制台 GBK 打不了 CJK）。
用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/audit_silence_menu_20261028.py
"""
import glob
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

RUNS = [
    ("BATCH-S1-ACTION-20261028", "logs/p08_s1_live_20261028",
     "benchmark/stories_v2/stage2/GRADING_CARDS_E024_E027_E031_E034.json"),
    ("BATCH-S2-ANCHORS-20261028", "logs/p08_s2_live_20261028",
     "benchmark/stories_v2/anchors_20261028/GRADING_CARDS_AK01_AK12.json"),
    ("BATCH-S2R-FIX-20261028", "logs/p08_s2r_live_20261028",
     "benchmark/stories_v2/anchors_20261028/GRADING_CARDS_AK01_AK12.json"),
    ("BATCH-S3-ATTRIB-20261028", "logs/p08_s3_live_20261028",
     "benchmark/stories_v2/anchors_20261028/GRADING_CARDS_AK01_AK12.json"),
    ("BATCH-D1-GENERAL-20261028", "logs/p08_d1_live_20261028",
     "benchmark/stories_v2/stageD/GRADING_CARDS_D01_D02.json"),
]

SILENCE = ("收尾", "停")
KV = re.compile(r"([a-z_0-9]+)=([^ \]]*)")
SILENCE_OK_WORDS = ("不回", "不回复", "静默", "不必回")


def _load(p):
    fp = os.path.join(ROOT, p)
    if not os.path.isfile(fp):
        return None
    return json.load(io.open(fp, encoding="utf-8"))


def parse_notes(txt):
    d = {}
    for k, v in KV.findall(txt or ""):
        d[k] = v
    return d


def cards_by_case(path):
    dd = _load(path) or {}
    return {c["case_id"]: c for c in dd.get("cards") or []}


def main():
    rows, missing = [], []
    for batch, run, cards_path in RUNS:
        cmap = cards_by_case(cards_path)
        if not cmap:
            missing.append(cards_path)
        root = os.path.join(ROOT, run)
        if not os.path.isdir(root):
            missing.append(run)
            continue
        for od in sorted(glob.glob(os.path.join(root, "*", "out", "turn_*"))):
            mf_p = os.path.join(od, "manifest.json")
            nt_p = os.path.join(od, "mechanism_notes.txt")
            if not (os.path.isfile(mf_p) and os.path.isfile(nt_p)):
                continue
            mf = json.load(io.open(mf_p, encoding="utf-8"))
            notes = parse_notes(io.open(nt_p, encoding="utf-8").read())
            cid = mf.get("case_id")
            arm_dir = os.path.basename(os.path.dirname(os.path.dirname(od)))
            arm = "parent" if arm_dir.startswith("parent") else "candidate"
            caps = [c for c in (notes.get("caps") or "").split(",") if c]
            hint = notes.get("signal_hint")
            action = notes.get("action")
            card = cmap.get(cid) or {}
            may_decline = card.get("may_decline")
            raw_p = os.path.join(od, "reply_raw.txt")
            raw = io.open(raw_p, encoding="utf-8").read() if os.path.isfile(raw_p) else ""
            explicit_nr = '"no_reply"' in raw.replace(" ", "").replace("'", "")
            offered = any(a in caps for a in SILENCE)
            chose = (action in SILENCE) or explicit_nr
            card_sanction = any(w in (card.get("current_action") or "") +
                                 " ".join(card.get("acceptable_actions") or [])
                                 for w in SILENCE_OK_WORDS)
            rows.append({
                "batch": batch, "arm": arm, "case_id": cid, "candidate": mf.get("candidate"),
                "hint": hint, "may_decline": may_decline, "caps": caps,
                "silence_offered": offered, "action_taken": action,
                "explicit_no_reply": explicit_nr, "silence_chosen": chose,
                "card_sanctions_silence": card_sanction if card else None,
                "veto": mf.get("veto"), "score10": mf.get("score10"),
            })

    def cnt(f):
        return sum(1 for r in rows if f(r))

    hazard = [r for r in rows if r["silence_offered"] and r["may_decline"] is False]
    hazard_hint = [r for r in hazard if r["hint"] not in ("收尾",)]
    chosen_no_sanction = [r for r in rows if r["silence_chosen"]
                          and r["card_sanctions_silence"] is False]
    nr_veto = [r for r in rows if (r["veto"] or "") in ("unlicensed_no_reply", "action_conflict")]
    summary = {
        "turn_records": len(rows),
        "batches_missing": missing,
        "silence_offered": cnt(lambda r: r["silence_offered"]),
        "silence_chosen": cnt(lambda r: r["silence_chosen"]),
        "offered_at_may_decline_false": len(hazard),
        "offered_at_non_closing_hint_and_may_decline_false": len(hazard_hint),
        "hints_in_hazard": sorted({str(r["hint"]) for r in hazard_hint}),
        "chosen_without_card_sanction": len(chosen_no_sanction),
        "no_reply_related_vetoes": len(nr_veto),
        "by_arm": {a: {
            "records": cnt(lambda r, a=a: r["arm"] == a),
            "offered": cnt(lambda r, a=a: r["arm"] == a and r["silence_offered"]),
            "chosen": cnt(lambda r, a=a: r["arm"] == a and r["silence_chosen"]),
            "hazard": cnt(lambda r, a=a: r["arm"] == a and r in hazard),
        } for a in ("parent", "candidate")},
    }
    out = {"kind": "silence_menu_audit_v1",
           "generated_by": "tools/audit_silence_menu_20261028.py",
           "source_of_truth": ("mechanism_notes.txt(机制自报 caps/hint/action) + "
                               "reply_raw.txt(是否显式 no_reply) + manifest.json(veto) + "
                               "该批冻结卡 may_decline/acceptable_actions"),
           "summary": summary,
           "hazard_positions": hazard,
           "chosen_without_card_sanction": chosen_no_sanction,
           "rows": rows}
    dst = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                       "deadline_quality_20261028", "SILENCE_MENU_AUDIT_20261028.json")
    json.dump(out, io.open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("WROTE", dst.encode("ascii", "backslashreplace").decode())
    print(json.dumps(summary, sort_keys=True).encode("ascii", "backslashreplace").decode())
    for r in hazard_hint[:25]:
        print("HAZARD %s %s %s hint=%s md=%s caps=%s action=%s veto=%s"
              % (r["batch"], r["arm"], r["case_id"], r["hint"], r["may_decline"],
                 ",".join(r["caps"]), r["action_taken"], r["veto"]))
    for r in chosen_no_sanction[:25]:
        print("CHOSEN_NO_SANCTION %s %s %s hint=%s md=%s action=%s explicit_nr=%s veto=%s"
              % (r["batch"], r["arm"], r["case_id"], r["hint"], r["may_decline"],
                 r["action_taken"], r["explicit_no_reply"], r["veto"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
