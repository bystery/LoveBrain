# -*- coding: utf-8 -*-
r"""tools/build_d_human_pairs.py — 收口单 D：人工意愿标定材料（≤12 对，零调用）。

规矩（按用户既有口径，写死在这里）：
  - 匿名成对：只显示 甲/乙，不显示臂名、身份、成本、自动评分；
  - 只让本人判"愿不愿发"，不让他核事实：不给卡片、不给 forbidden 项、不给出入事实的提示；
  - 每对只展示主槽正文（真正会发出去的那一句）+ 她最近的话（含你已发出的上一句，用于读懂语境）；
  - 选项：愿发甲 / 愿发乙 / 都行 / 都不想 / 说不清；原因一句话，可留空；
  - 留空不代填：没有真实人工评分，W 就是未知，AI 不得代投。

选样规则（在派发材料前写死，不看完结果再挑）：
  先取 9 个已被判为 major 的位点（候选 5 + 原版 5，去重后 9），
  再从"两臂都无 major 且双方都有正文"的位点里按 unit_id 字典序取 3 个补齐 12。
甲乙顺序 = sha256(SEED + unit_id) 的奇偶，只写进 binding 文件，工作表里不出现。
"""
import argparse
import hashlib
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "final_mainline_review_20261029")
SEED = "kbD-20261029"
MAJOR_UNITS = {  # 来自 F3_REPORT.md / F_C_MAJOR_RECLASS_20261029.json
    "F3:K01_T06": "原版 major（单方变更客栈日期）",
    "F3:K02_T14": "原版 major（未召回更正后的到货日）",
    "F3:K02_T16": "两侧同位点均有 major（预算算术）",
    "F3:K02_T18": "原版 major（未召回两万到账条件）",
    "F3:K02_T21": "原版 major（替她决定退不退）",
    "F3:N01_T10": "候选 major（孩子配合度乐观）",
    "F3:N02_T03": "候选 major（暂定与第三方规则说成既定）",
    "F3:K01_T19": "候选 major（未记录日期说成既定）",
    "F3:K01_T20": "候选 major（未记录入住日说成既定）",
}


def coin(unit_id):
    return int(hashlib.sha256((SEED + unit_id).encode("utf-8")).hexdigest(), 16) % 2


def primary_of(ans):
    if not isinstance(ans, dict):
        return ""
    if ans.get("kind") == "no_reply":
        return "（这一版选择不回：{}）".format(ans.get("decline_reason") or "没给理由")
    return ans.get("primary") or ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--materials", default=os.path.join(OUT, "F3_MATERIALS.jsonl"))
    ap.add_argument("--bindings", default=os.path.join(OUT, "F3_PRIVATE_BINDINGS.json"))
    ap.add_argument("--analysis", default=os.path.join(OUT, "F3_ANALYSIS.json"))
    a = ap.parse_args()
    items = [json.loads(l) for l in open(a.materials, encoding="utf-8") if l.strip()]
    priv = json.load(open(a.bindings, encoding="utf-8"))
    by_uid = {it["payload"]["unit_id"]: it for it in items}

    # 两臂都无 major 且都有正文的位点（用于补齐 3 个）
    clean = []
    if os.path.isfile(a.analysis):
        an = json.load(open(a.analysis, encoding="utf-8"))
        majors = set()
        for row in an.get("rows_detail") or []:
            me = row.get("major_errors") or {}
            if isinstance(me, dict):
                if any(me.get("parent") or []) or any(me.get("candidate") or []):
                    majors.add(row.get("unit_id"))
            elif me:
                majors.add(row.get("unit_id"))
        for uid, it in by_uid.items():
            if uid in majors:
                continue
            aa, bb = it["payload"]["answers"]["A"], it["payload"]["answers"]["B"]
            if aa.get("kind") == "reply" and bb.get("kind") == "reply":
                clean.append(uid)
        clean.sort()
    chosen = [u for u in MAJOR_UNITS if u in by_uid] + clean[:max(0, 12 - len(MAJOR_UNITS))]
    chosen = chosen[:12]

    sheet, binding = [], {}
    for i, uid in enumerate(chosen, start=1):
        it = by_uid[uid]
        p = it["payload"]
        pv = priv[uid]
        swap = coin(uid) == 1
        jia = "A" if not swap else "B"
        yi = "B" if not swap else "A"
        cur = p["context"]["current_input"]["messages"]
        her = " / ".join(m["text"] for m in cur if m.get("role") == "HER")
        me = " / ".join(m["text"] for m in cur if m.get("role") == "ME")
        sheet.append({
            "n": i, "unit": uid,
            "her": her, "me_already_sent": me,
            "jia": primary_of(p["answers"][jia]),
            "yi": primary_of(p["answers"][yi]),
        })
        binding[uid] = {
            "display_no": i, "jia_arm": pv["a_arm"] if not swap else pv["b_arm"],
            "yi_arm": pv["b_arm"] if not swap else pv["a_arm"],
            "jia_side_raw": jia, "yi_side_raw": yi,
            "story": pv["story"], "case_id": pv["case_id"],
            "inclusion_reason": MAJOR_UNITS.get(uid, "两臂均无 major 的补齐项"),
            "answer_sha256": pv["answer_sha256"],
        }

    md = ["# 人工意愿标定表（12 对，2026-10-29）", "",
          "只判你想不想发，不核事实。每对选一个：愿发甲 / 愿发乙 / 都行 / 都不想 / 说不清；"
          "原因一句话，可留空。", ""]
    for s in sheet:
        md.append("## %02d" % s["n"])
        if s["me_already_sent"]:
            md.append("你已发出：%s" % s["me_already_sent"])
        md.append("她：%s" % s["her"])
        md.append("")
        md.append("甲：%s" % s["jia"])
        md.append("乙：%s" % s["yi"])
        md.append("")
        md.append("选择：☐愿发甲 ☐愿发乙 ☐都行 ☐都不想 ☐说不清　原因：＿＿＿＿＿＿")
        md.append("")
    ws = os.path.join(OUT, "F_D_HUMAN_PAIRS_WORKSHEET.md")
    open(ws, "w", encoding="utf-8").write("\n".join(md))
    body = json.dumps({"kind": "d_human_pairs_binding", "seed": SEED,
                       "pairs": len(chosen), "binding": binding,
                       "worksheet_sha256": hashlib.sha256(
                           "\n".join(md).encode("utf-8")).hexdigest(),
                       "rule": "留空不代填；没有人工评分则 W 未知"},
                      ensure_ascii=False, indent=1)
    bp = os.path.join(OUT, "F_D_HUMAN_PAIRS_BINDING.json")
    open(bp, "w", encoding="utf-8").write(body + "\n")
    print("pairs=%d (major=%d, clean_fill=%d)" % (
        len(chosen), sum(1 for u in chosen if u in MAJOR_UNITS),
        sum(1 for u in chosen if u not in MAJOR_UNITS)))
    print("WROTE", os.path.relpath(ws, ROOT).replace("\\", "/"))
    print("WROTE", os.path.relpath(bp, ROOT).replace("\\", "/"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
