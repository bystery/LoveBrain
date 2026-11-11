# -*- coding: utf-8 -*-
r"""tools/annotate_knowledge_probe_grades.py — 把分析人对 24 份正文的逐格语义判定写回结果账（零调用）。

脚本的字面命中只是下界：用到/归属/污染三判必须读原文。这里逐格给出判定与引文，
并给出分母=实际适用机会的六维账；不报聚合平均分，不改任何原始答卷。
"""
import argparse
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "final_mainline_review_20261029")

# (probe, arm, rep) -> 语义判定
GRADES = {
    ("KP-A1", "on", 1): {
        "use": "PASS", "attribution": "CORRECT", "contamination": "NONE",
        "quote": "受不了，豆豆怕电钻。周末哪天开钻？定下来我提前把它接走",
        "note": "主体落在豆豆；顺带用上另一条跨窗事实『你妈对猫毛过敏，那就只剩一个选项了——周末送我这』"},
    ("KP-A1", "on", 2): {
        "use": "PASS", "attribution": "CORRECT", "contamination": "NONE",
        "quote": "它怕电钻这事我记着…豆豆怕电钻，我记得。周末我过来接它去我那儿待一天",
        "note": "两个槽都正确归属并据此安排"},
    ("KP-A1", "off", 1): {
        "use": "ABSTAIN_OR_MISS", "attribution": "UNSPECIFIED", "contamination": "NONE",
        "quote": "猫怕的是一阵一阵的突发声，不是一直响",
        "note": "只用通用知识，未断言豆豆怕电钻；未编造具体值"},
    ("KP-A1", "off", 2): {
        "use": "ABSTAIN_OR_MISS", "attribution": "UNSPECIFIED", "contamination": "NONE",
        "quote": "电钻那动静猫是真受不了，何况豆豆今天刚回家",
        "note": "『豆豆今天刚回家』来自窗口内真实消息(uid70)，非凭空"},
    ("KP-A2", "on", 1): {
        "use": "PASS", "attribution": "CORRECT", "contamination": "NONE",
        "quote": "你不是怕猫毛吗，穿件不显毛的就行，粘毛纸我这边带",
        "note": "没写“掉毛”二字但据此安排（深色衣服/粘毛纸），语义用到"},
    ("KP-A2", "on", 2): {
        "use": "PASS", "attribution": "CORRECT", "contamination": "NONE",
        "quote": "她家猫掉毛那事你还怕吗，去她家别穿深色了",
        "note": "主槽反而说“猫我最近也没见着，得问阿岚一声”——同一份交付里既有正确召回也有保守表述，按整份判 PASS"},
    ("KP-A2", "off", 1): {
        "use": "ABSTAIN_OR_MISS", "attribution": "UNSPECIFIED", "contamination": "NONE",
        "quote": "她家那只你先说是猫还是狗，我一直没搞清",
        "note": "连物种都不知道——off 侧干净地体现“窗口里没有这条”"},
    ("KP-A2", "off", 2): {
        "use": "ABSTAIN_OR_MISS", "attribution": "UNSPECIFIED", "contamination": "NONE",
        "quote": "她家那只是猫还是狗？我到时候别认错",
        "note": "同上，未编造"},
    ("KP-C1", "on", 1): {
        "use": "PASS", "update": "CORRECT_NEW", "contamination": "NONE",
        "quote": "说过，11月10号。你自己还差点记成8号。不过都住完了…退款这事早过去了",
        "note": "新值+把旧值明确归为她的误记+时间状态正确（已过期）；字面 stale 命中不是污染"},
    ("KP-C1", "on", 2): {
        "use": "PASS", "update": "CORRECT_NEW", "contamination": "NONE",
        "quote": "说过，你后来改成11月10号了…退款这事儿已经过期作废了",
        "note": "同上"},
    ("KP-C1", "off", 1): {
        "use": "ABSTAIN_OR_MISS", "update": "ABSTAINED", "contamination": "NONE",
        "quote": "没说过，我这边也翻不到。明早我打给客栈问清楚退款截止到哪天",
        "note": "off 侧如实承认不知道并给核实动作"},
    ("KP-C1", "off", 2): {
        "use": "ABSTAIN_OR_MISS", "update": "ABSTAINED", "contamination": "NONE",
        "quote": "关于退款截止日期那条是空的",
        "note": "同上"},
    ("KP-C2", "on", 1): {
        "use": "FAIL", "update": "WRONG_VALUE_ASSERTED",
        "contamination": "ASSERTED_UNSUPPORTED",
        "quote": "瓷砖不是28号就到了吗",
        "note": "把投影里另一行 f_0055『她那边瓷砖到货了（事件@2026-05-28）』的日期接到到货日上，"
               "既非新值11月8号也非旧值11月5号，且以既定语气说出"},
    ("KP-C2", "on", 2): {
        "use": "PASS", "update": "CORRECT_NEW", "contamination": "NONE",
        "quote": "记的是11月8号，就是厂家说5号来不及的那次。不过你上个月28号就说瓷砖到了，现在要重新约铺砖，是还有一批没铺？",
        "note": "同一格重复里给对了新值，并把 28号 的冲突摊开反问——正是应有的处理；说明 r1 是可复现性 1/2 的合并错误"},
    ("KP-C2", "off", 1): {
        "use": "ABSTAIN_OR_MISS", "update": "ABSTAINED", "contamination": "NONE",
        "quote": "我这儿没记到瓷砖改期的日子，之前改的只有橱柜测量到11号",
        "note": "off 侧不但没编，还提醒别把两个日期混用"},
    ("KP-C2", "off", 2): {
        "use": "ABSTAIN_OR_MISS", "update": "ABSTAINED", "contamination": "NONE",
        "quote": "这个日期我是真没有…别按我随口说的去约",
        "note": "同上"},
    ("KP-T1", "on", 1): {
        "use": "PASS", "contamination": "NONE",
        "quote": "11月1号就开售了，那趟早坐完了",
        "note": "值+时间状态都对（当前时钟 2026-11-17）"},
    ("KP-T1", "on", 2): {
        "use": "PASS", "contamination": "NONE",
        "quote": "11月1号开的售，票都陪我们走完一圈了",
        "note": "同上"},
    ("KP-T1", "off", 1): {
        "use": "ABSTAIN_OR_MISS", "contamination": "NONE",
        "quote": "日期我不敢凭记忆报…我现在开12306查一下",
        "note": "off 拒绝猜"},
    ("KP-T1", "off", 2): {
        "use": "ABSTAIN_OR_MISS", "contamination": "NONE",
        "quote": "正常是提前15天开售，我按你那趟车再核一遍",
        "note": "用通用规则但不报具体日期，未编造"},
    ("KP-T2", "on", 1): {
        "use": "PASS", "contamination": "NONE",
        "quote": "11月7号。写下来吧，别只靠脑子记",
        "note": "直接给出跨窗日期"},
    ("KP-T2", "on", 2): {
        "use": "PASS", "contamination": "NONE",
        "quote": "11月7号，周六。写下来吧",
        "note": "2026-11-07 实测确为周六（推导正确，非无据添加）"},
    ("KP-T2", "off", 1): {
        "use": "ABSTAIN_OR_MISS", "contamination": "NONE",
        "quote": "我这边没有能确定的日期，不敢随便报",
        "note": "off 拒绝猜"},
    ("KP-T2", "off", 2): {
        "use": "ABSTAIN_OR_MISS", "contamination": "NONE",
        "quote": "日子我真不瞎给——通知还在的话发我",
        "note": "同上"},
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--result", default=os.path.join(OUT, "F_B_KNOWLEDGE_RESULT.json"))
    ap.add_argument("--batch", default=os.path.join(
        ROOT, "logs", "kb_probe_live_20261029_164332", "KB_PROBE_RESULT.json"))
    a = ap.parse_args()
    doc = json.load(open(a.result, encoding="utf-8"))
    batch = json.load(open(a.batch, encoding="utf-8"))

    tok = {"on": {"prompt": 0, "completion": 0, "total": 0, "cells": 0},
           "off": {"prompt": 0, "completion": 0, "total": 0, "cells": 0}}
    for r in batch["rows"]:
        u = r.get("usage") or {}
        t = tok[r["arm"]]
        t["prompt"] += int(u.get("prompt_tokens") or 0)
        t["completion"] += int(u.get("completion_tokens") or 0)
        t["total"] += int(u.get("total_tokens") or 0)
        t["cells"] += 1

    used = {}
    for pid, d in doc["probes"].items():
        for key, c in d["cells"].items():
            arm, rep = key.split(":")
            g = GRADES.get((pid, arm, int(rep[1:])))
            if g is None:
                raise SystemExit("missing hand grade for %s %s r%s" % (pid, arm, rep[1:]))
            c["hand"] = g
        on = [v for k, v in d["cells"].items() if k.startswith("on")]
        off = [v for k, v in d["cells"].items() if k.startswith("off")]
        d["Use"] = {
            "on_pass": sum(1 for v in on if v["hand"]["use"] == "PASS"),
            "on_opportunities": len(on),
            "on_fail": [k for k, v in d["cells"].items()
                        if k.startswith("on") and v["hand"]["use"] == "FAIL"],
            "off_pass": sum(1 for v in off if v["hand"]["use"] == "PASS"),
            "off_opportunities": len(off),
            "denominator_note": "分母=该探针实际派发的重复次数（2），不是全部 72 位点",
        }
        used[pid] = d["Use"]["on_pass"]
        upd = [v for v in on if "update" in v["hand"]]
        if upd:
            d.setdefault("Update", {"note": "仅更正类探针适用"})
            d["Update"]["hand"] = {
                "correct_new": sum(1 for v in upd if v["hand"]["update"] == "CORRECT_NEW"),
                "wrong_value": sum(1 for v in upd if v["hand"]["update"] == "WRONG_VALUE_ASSERTED"),
                "opportunities": len(upd),
                "note": "旧值被当误记提及不算污染；把非记录值说成既定算 wrong_value"}
        att = [v for v in on if "attribution" in v["hand"]]
        if att:
            d.setdefault("Attribution", {"note": "字面主体命中；归属是否真错仍需读原文"})
            d["Attribution"]["hand"] = {
                "correct": sum(1 for v in att if v["hand"]["attribution"] == "CORRECT"),
                "opportunities": len(att)}
        d["Contamination"]["hand"] = {
            "asserted_unsupported_on": sum(1 for v in on
                                           if v["hand"]["contamination"] != "NONE"),
            "asserted_unsupported_off": sum(1 for v in off
                                            if v["hand"]["contamination"] != "NONE"),
            "cases": [v["hand"].get("quote") for v in on
                      if v["hand"]["contamination"] != "NONE"]}

    doc["hand_grading"] = {
        "grader": "分析人逐格读正文（引文已附），脚本字面命中仅作下界",
        "use_on_total": "%d/%d" % (sum(used.values()), 2 * len(used)),
        "use_off_total": "%d/%d" % (
            sum(doc["probes"][p]["Use"]["off_pass"] for p in doc["probes"]), 2 * len(used)),
        "update_correct_new": sum(d["Update"]["hand"]["correct_new"]
                                  for d in doc["probes"].values() if "Update" in d),
        "update_wrong_value": sum(d["Update"]["hand"]["wrong_value"]
                                  for d in doc["probes"].values() if "Update" in d),
        "attribution_correct": sum(d["Attribution"]["hand"]["correct"]
                                   for d in doc["probes"].values() if "Attribution" in d),
        "contamination_cells": sum(
            d["Contamination"]["hand"]["asserted_unsupported_on"]
            + d["Contamination"]["hand"]["asserted_unsupported_off"]
            for d in doc["probes"].values()),
        "per_probe": used,
    }
    doc["resource"] = {
        "by_arm": tok,
        "physical_requests": sum(t["cells"] for t in tok.values()),
        "unknown_usage_cells": doc["token_usage"]["unknown_cells"],
        "tokens_per_usable_on_result": (
            round(tok["on"]["total"] / max(1, sum(used.values())), 1)),
        "money": None,
        "money_note": "无单价账本依据，金额保持 null",
        "authorization": {"id": batch["authorization_id"],
                          "consumed": 24, "closed": True,
                          "ledger": "logs/auth_budget/%s.jsonl" % batch["authorization_id"],
                          "events": {"attempt_started": 24, "attempt_completed": 24,
                                     "authorization_closed": 1}},
    }
    lat_p = os.path.join(os.path.dirname(a.batch), "GATEWAY_EVENTS.jsonl")
    lat = {"n": 0, "source": os.path.relpath(lat_p, ROOT).replace("\\", "/")}
    if os.path.isfile(lat_p):
        vals = sorted(int(json.loads(l)["latency_ms"])
                      for l in io.open(lat_p, encoding="utf-8") if l.strip()
                      and json.loads(l).get("kind") == "attempt_completed"
                      and json.loads(l).get("latency_ms"))
        if vals:
            lat = {"n": len(vals), "median_ms": vals[len(vals) // 2],
                   "min_ms": vals[0], "max_ms": vals[-1], "sum_ms": sum(vals),
                   "dispatch": "串行并发1（授权单 max_concurrency=1）",
                   "source": lat["source"]}
    doc["resource"]["latency"] = lat

    doc["material_defect"] = {
        "id": "E-MAT-B",
        "what": "K02 素材内部日期冲突：消息时间戳 2026-05-28 出现『瓷砖到了』，"
                "而内容日期写作『瓷砖改到11月8号到』（uid15）；"
                "记忆库如实写回了两条（f_0011 与 f_0055），投影同时给出。",
        "effect": "KP-C2 on:r1 把 05-28 当成到货日说成『28号就到了』——"
                  "既有素材缺陷，也是把外部日期接错主体的无据确定化；"
                  "同探针 on:r2 给对了 11月8号 并主动反问冲突，可复现性 1/2。",
        "handling": "素材缺陷单列，不计入候选胜负；但该格的 wrong-value 断言仍计入污染计数（属机制侧真实行为）。",
    }
    json.dump(doc, open(a.result, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    hg = doc["hand_grading"]
    lines = ["", "## 逐格语义判定（分析人读正文，脚本字面命中仅作下界）", "",
             "用到：on %s，off %s（off 为对照，不是机会）；"
             "更正：新值正确 %d / 说成别的既定值 %d；归属正确 %d；污染格 %d。" % (
                 hg["use_on_total"], hg["use_off_total"],
                 hg["update_correct_new"], hg["update_wrong_value"],
                 hg["attribution_correct"], hg["contamination_cells"]),
             "",
             "| 探针 | 类别 | on 用到 | off 用到 | 语义判定要点 |",
             "|---|---|---|---|---|"]
    for pid in sorted(doc["probes"]):
        d = doc["probes"][pid]
        pts = []
        for k in sorted(d["cells"]):
            c = d["cells"][k]
            if k.startswith("on") and c["hand"]["contamination"] != "NONE":
                pts.append("污染：%s" % c["hand"]["quote"][:24])
            elif k.startswith("on") and c["hand"]["use"] == "PASS" and k.endswith("r1"):
                pts.append(c["hand"]["quote"][:24])
        lines.append("| %s | %s | %d/%d | %d/%d | %s |" % (
            pid, d["class"], d["Use"]["on_pass"], d["Use"]["on_opportunities"],
            d["Use"]["off_pass"], d["Use"]["off_opportunities"], "；".join(pts) or "—"))
    lines += ["", "## 资源", "",
              "on：%s ｜ off：%s ｜ 物理请求 %d ｜ token 未知格 %d ｜ "
              "每“一次成功跨窗使用”的 on token=%s ｜ 金额 null（无单价依据）" % (
                  json.dumps(doc["resource"]["by_arm"]["on"]),
                  json.dumps(doc["resource"]["by_arm"]["off"]),
                  doc["resource"]["physical_requests"],
                  len(doc["resource"]["unknown_usage_cells"]),
                  doc["resource"]["tokens_per_usable_on_result"]),
              "",
              "## 素材缺陷单列（不计候选胜负）", "",
              "%s：%s" % (doc["material_defect"]["id"], doc["material_defect"]["what"]),
              doc["material_defect"]["effect"], doc["material_defect"]["handling"], ""]
    rp = os.path.join(OUT, "F_B_KNOWLEDGE_REPORT.md")
    body = open(rp, encoding="utf-8").read()
    appended = False
    if "逐格语义判定" not in body:
        open(rp, "w", encoding="utf-8").write(body + "\n".join(lines))
        appended = True
    print("use_on=%s use_off=%s update_ok=%s update_bad=%s attr_ok=%s contamination=%d" % (
        hg["use_on_total"], hg["use_off_total"], hg["update_correct_new"],
        hg["update_wrong_value"], hg["attribution_correct"], hg["contamination_cells"]))
    print("per_probe", json.dumps(hg["per_probe"], ensure_ascii=False))
    print("token on/off", json.dumps(doc["resource"]["by_arm"]))
    print("%s %s" % ("APPENDED" if appended else
                     "REPORT_ALREADY_HAS_GRADES(no rewrite)",
                     os.path.relpath(rp, ROOT).replace("\\", "/")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
