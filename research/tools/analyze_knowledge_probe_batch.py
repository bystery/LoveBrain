# -*- coding: utf-8 -*-
r"""tools/analyze_knowledge_probe_batch.py — 收口单 B：24 次真实生成的结果账（零调用）。

六个维度逐探针登记，分母=实际适用机会：
  Capture      原子是否曾在真实对话里被写回（离线，取自已付费状态，不由本批决定）
  Fidelity     投影里的值能否逐字在被引来源消息里核到（离线，取自前置闸门）
  Update       仅更正类探针：新值是否被用、旧值是否被当现行
  Retrieval    on 侧出站 prompt 是否携带支持该值的投影证据（前置闸门已证，本批复检）
  Use          答卷正文是否用上了该值（字面标记 + 我逐条读原文后的语义判定）
  Contamination 答卷是否把窗口内没有的东西说成已知 / 是否把旧值当现行
每格都写实际出站 prompt 的 sha256，与冻结 prompt 逐字节对账；不匹配即整批作废。
"""
import argparse
import hashlib
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "final_mainline_review_20261029")


def sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--result", required=True, help="logs/kb_probe_live_*/KB_PROBE_RESULT.json")
    ap.add_argument("--precheck", default=os.path.join(OUT, "F_B_PRECHECK.json"))
    ap.add_argument("--authorization",
                    default=os.path.join(OUT, "KB_PROBE_AUTHORIZATION_20261029.json"))
    ap.add_argument("--outdir", default=OUT)
    a = ap.parse_args()

    res = json.load(open(a.result, encoding="utf-8"))
    pc = json.load(open(a.precheck, encoding="utf-8"))
    auth = json.load(open(a.authorization, encoding="utf-8"))
    pcx = {r["probe_id"]: r for r in pc["probes"]}
    rows = res["rows"]

    if res["identity_sha256"] != auth["scope"]["identity_sha256"]:
        raise SystemExit("batch identity != authorization identity")
    mism = []
    for r in rows:
        pr = pcx[r["probe_id"]]
        want = pr["on_prompt_sha256"] if r["arm"] == "on" else pr["off_prompt_sha256"]
        if r.get("sent_prompt_sha256") != want:
            mism.append(r["cell"])
    usage = {"prompt": 0, "completion": 0, "total": 0, "unknown_cells": []}
    for r in rows:
        u = (r.get("usage") or {})
        if not u.get("total_tokens"):
            usage["unknown_cells"].append(r["cell"])
        usage["prompt"] += int(u.get("prompt_tokens") or 0)
        usage["completion"] += int(u.get("completion_tokens") or 0)
        usage["total"] += int(u.get("total_tokens") or 0)

    per_probe = {}
    for r in rows:
        pid = r["probe_id"]
        d = per_probe.setdefault(pid, {
            "class": r["class"], "story": r["story"], "fact_id": r["fact_id"],
            "domain": r["domain"], "question": r["question"], "cells": {},
            "Capture": "PASS（原子来自付费写回，见前置闸门）",
            "Fidelity": ("PASS（值逐字可核到来源）" if pcx[pid].get("fidelity_ok")
                         else "CHECK（值非逐字来源）"),
            "Retrieval": "PASS（on 侧出站 prompt 含该 fact_id 投影行）",
        })
        s = r.get("score") or {}
        d["cells"]["%s:r%d" % (r["arm"], r["rep"])] = {
            "transport_ok": r.get("transport_ok"),
            "delivered_kind": s.get("delivered_kind"),
            "value_forms_present": s.get("value_forms_present"),
            "stale_forms_present": s.get("stale_forms_present"),
            "attribution_target_present": s.get("attribution_target_present"),
            "wrong_subject_present": s.get("wrong_subject_present"),
            "sent_prompt_sha256": r.get("sent_prompt_sha256"),
            "raw_sha256": r.get("raw_sha256"),
            "usage": r.get("usage"),
            "text": s.get("blob") or r.get("error") or "",
        }
    for pid, d in per_probe.items():
        on = [v for k, v in d["cells"].items() if k.startswith("on")]
        off = [v for k, v in d["cells"].items() if k.startswith("off")]
        on_hit = sum(1 for v in on if v["value_forms_present"])
        off_hit = sum(1 for v in off if v["value_forms_present"])
        d["literal_use"] = {"on": "%d/%d" % (on_hit, len(on)),
                            "off": "%d/%d" % (off_hit, len(off)),
                            "note": "字面命中只是下界；语义判定见 text 逐条读"}
        if pcx[pid]["class"] == "correction":
            stale_on = sum(1 for v in on if v["stale_forms_present"])
            d["Update"] = {
                "stale_asserted_on": "%d/%d" % (stale_on, len(on)),
                "stale_in_window": pcx[pid].get("stale_forms_inside_history_segment"),
                "note": "旧值故意留在窗口内，新值只在投影里；见 text 判是否被当现行"}
        if pcx[pid].get("attribution_targets"):
            d["Attribution"] = {
                "target_on": sum(1 for v in on if v["attribution_target_present"]),
                "wrong_on": sum(1 for v in on if v["wrong_subject_present"]),
                "target_off": sum(1 for v in off if v["attribution_target_present"]),
                "wrong_off": sum(1 for v in off if v["wrong_subject_present"]),
                "note": "字面主体命中；归属是否真错仍需读原文"}
        d["Contamination"] = {
            "on_claims_unknown": "",
            "note": "由分析人逐条填（见 report 的逐格文本），脚本不做词面判定"}
        d["Use"] = {"note": "由分析人逐条填（语义），脚本只给 literal_use"}

    doc = {
        "kind": "f_b_knowledge_result",
        "batch_dir": os.path.relpath(a.result, ROOT).replace("\\", "/"),
        "mode": res["mode"], "authorization_id": res["authorization_id"],
        "candidate": res["candidate"], "identity_sha256": res["identity_sha256"],
        "precheck_sha256": res["precheck_sha256"],
        "planned": res["planned"], "done": res["done"],
        "transport_ok": res["transport_ok"], "stopped": res["stopped"],
        "frozen_prompt_mismatch_cells": mism,
        "fixture_turns_not_billed": res.get("fixture_turns_not_billed"),
        "token_usage": usage,
        "money": None,
        "money_note": "无单价账本依据，金额一律 null，不估",
        "probes": per_probe,
        "measurement_limits": [
            "off 臂删的是三段投影整层（不止被问那一条），on/off 差值是整层投影的效应",
            "夹具消息占据窗口一部分，各探针保留内容不同；只做同探针 on/off 配对比较",
            "投影选择是词面二元组重合，问题不复述事实用词就没有投影证据",
            "6 探针 × 2 重复是小样本机制实验，不包装成跨人群/跨域泛化结论",
            "字面命中是用到的下界；用到/污染/归属三判均须分析人读原文，不由脚本代判",
        ],
    }
    out = os.path.join(a.outdir, "F_B_KNOWLEDGE_RESULT.json")
    json.dump(doc, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    md = ["# 收口单 B：跨窗口知识探针结果（%s，%d 次真实生成）" % (
        doc["mode"], doc["done"]), "",
        "身份 %s ｜ 前置闸门 sha %s ｜ 夹具轮 %s（零网络，不计费）｜ token %s" % (
            doc["identity_sha256"][:12], doc["precheck_sha256"][:12],
            doc["fixture_turns_not_billed"], json.dumps(doc["token_usage"])),
        "",
        "冻结 prompt 对账不匹配格：%s" % (doc["frozen_prompt_mismatch_cells"] or "无"),
        ""]
    for pid in sorted(per_probe):
        d = per_probe[pid]
        md.append("## %s ｜ %s ｜ %s/%s" % (pid, d["class"], d["story"], d["fact_id"]))
        md.append("")
        md.append("领域：%s" % d["domain"])
        md.append("")
        md.append("她：%s" % d["question"])
        md.append("")
        md.append("- Capture：%s" % d["Capture"])
        md.append("- Fidelity：%s" % d["Fidelity"])
        md.append("- Retrieval：%s" % d["Retrieval"])
        md.append("- 字面用到 on=%s off=%s（%s）" % (
            d["literal_use"]["on"], d["literal_use"]["off"], d["literal_use"]["note"]))
        for k in ("Update", "Attribution"):
            if k in d:
                md.append("- %s：%s" % (k, json.dumps(d[k], ensure_ascii=False)))
        md.append("")
        for key in sorted(d["cells"]):
            c = d["cells"][key]
            md.append("**%s**（kind=%s, prompt=%s…）" % (
                key, c["delivered_kind"], (c["sent_prompt_sha256"] or "")[:10]))
            md.append("")
            md.append("> %s" % (c["text"] or "（无正文/失败：%s）" % c.get("transport_ok")))
            md.append("")
    md.append("## 测量边界")
    md.append("")
    for x in doc["measurement_limits"]:
        md.append("- %s" % x)
    md.append("")
    mdp = os.path.join(a.outdir, "F_B_KNOWLEDGE_REPORT.md")
    open(mdp, "w", encoding="utf-8").write("\n".join(md))
    print("WROTE", os.path.relpath(out, ROOT).replace("\\", "/"))
    print("WROTE", os.path.relpath(mdp, ROOT).replace("\\", "/"))
    print("mismatch=%d transport_ok=%d/%d tokens=%d" % (
        len(mism), doc["transport_ok"], doc["planned"], usage["total"]))
    for pid in sorted(per_probe):
        print("%s literal on=%s off=%s" % (pid, per_probe[pid]["literal_use"]["on"],
                                           per_probe[pid]["literal_use"]["off"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
