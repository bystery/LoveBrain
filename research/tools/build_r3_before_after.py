# -*- coding: utf-8 -*-
r"""tools/build_r3_before_after.py — FIX-9 修复前后逐位对照（零调用，只读已付费轨迹）。

产出三张表：
  A. R3A（同计划配对：父臂=action_policy_r2=修复前，候选臂=action_policy_r3=修复后）
     逐位：机制自报菜单（caps/hint/may_decline）→ 是否提供不回 → 实际是否不回 →
     卡面是否许可不回 → 判定（over_decline / legal_silence / replied / protocol_fail）。
  B. R3B（原版 vs r3，D03 定向复验）：同结构，另记原版是否回。
  C. 三分对照：受影响格在 D1 批（r2 旧答卷）/ R3A(r2 新答卷) / R3A(r3) 三列的"回/不回"。

判定口径（写在前面，避免事后解释）：
  · silence_offered = 机制自报 caps ∩ 该版不回动作名（r2: 收尾|停；r3: 停）
  · silence_chosen  = reply_raw 顶层 "action":"no_reply" 或 analysis.action_taken=="停"
    （r2 里 action_taken=收尾 但写了正文的，不算不回——已付费答卷里 2 处这种自报漂移）
  · card_sanctions  = 该位点卡 current_action/acceptable_actions 里出现"不回/静默"类字样
  · verdict:
      protocol_fail   —— 无正文且未写 action（零可发）
      over_decline    —— 不回 且 卡面未许可
      legal_silence   —— 不回 且 卡面许可
      replied         —— 有正文
输出 UTF-8 JSON；stdout 打 ASCII 摘要。
"""
import glob
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

RUNS = {
    "R3A": {"dir": "logs/p08_r3a_live_20261028", "arms": {"parent": "action_policy_r2",
                                                         "candidate": "action_policy_r3"},
            "silence_actions": {"parent": ("收尾", "停"), "candidate": ("停",)}},
    "R3B": {"dir": "logs/p08_r3b_live_20261028", "arms": {"parent": "reference_product_v1",
                                                         "candidate": "action_policy_r3"},
            "silence_actions": {"parent": ("收尾", "停"), "candidate": ("停",)}},
}
CARDS = "benchmark/stories_v2/stageD/GRADING_CARDS_R3_DECLINE.json"
SILENCE_CARD_WORDS = ("不回", "不回复", "静默", "不必回")
KV = re.compile(r"([a-z_0-9]+)=([^ \]]*)")


def load(p):
    return json.load(io.open(os.path.join(ROOT, p), encoding="utf-8"))


def cards_map():
    d = load(CARDS)
    return {c["case_id"]: c for c in d.get("cards") or []}


def parse_notes(txt):
    return {k: v for k, v in KV.findall(txt or "")}


def parse_reply(raw):
    """尽量把模型原始回复解析成对象：整段 JSON → 首个 {...} → 失败记 broken。"""
    if not raw or not raw.strip():
        return None, "empty"
    try:
        o = json.loads(raw)
        return (o if isinstance(o, dict) else None), ("ok" if isinstance(o, dict) else "not_dict")
    except Exception:                                       # noqa: BLE001
        m = re.search(r"\{.*\}", raw, re.S)
        if not m:
            return None, "no_json"
        try:
            o = json.loads(m.group(0))
            return (o if isinstance(o, dict) else None), ("recovered" if isinstance(o, dict) else "not_dict")
        except Exception:                                   # noqa: BLE001
            return None, "broken"


def body_text(o):
    if not o:
        return ""
    r = o.get("response") or {}
    parts = [r.get(k) for k in ("recommended", "bad_boy", "playful", "warm")]
    parts += [x for x in (o.get("directions") or []) if isinstance(x, str)]
    return " ".join(p.strip() for p in parts if isinstance(p, str) and p.strip())


def row_of(batch, arm, arm_dir, card):
    mf = json.load(io.open(os.path.join(arm_dir, "manifest.json"), encoding="utf-8"))
    notes = parse_notes(io.open(os.path.join(arm_dir, "mechanism_notes.txt"),
                                encoding="utf-8").read()) if os.path.isfile(
        os.path.join(arm_dir, "mechanism_notes.txt")) else {}
    raw = io.open(os.path.join(arm_dir, "reply_raw.txt"), encoding="utf-8").read() \
        if os.path.isfile(os.path.join(arm_dir, "reply_raw.txt")) else ""
    o, parse_status = parse_reply(raw)
    analysis = (o or {}).get("analysis") or {}
    explicit = (o or {}).get("action")
    at = analysis.get("action_taken")
    body = body_text(o)
    silence_actions = RUNS[batch]["silence_actions"][arm]
    caps = [c for c in (notes.get("caps") or "").split(",") if c]
    offered = any(a in caps for a in silence_actions)
    chosen = (explicit == "no_reply") or (at == "停" and not body)
    sanction = None
    if card:
        blob = (card.get("current_action") or "") + " " + " ".join(card.get("acceptable_actions") or [])
        sanction = any(w in blob for w in SILENCE_CARD_WORDS)
    if not body.strip() and explicit != "no_reply":
        verdict = "protocol_fail"
    elif chosen:
        verdict = "legal_silence" if sanction else "over_decline"
    else:
        verdict = "replied"
    return {
        "case_id": mf.get("case_id"), "batch": batch, "arm": arm,
        "candidate": mf.get("candidate"), "hint": notes.get("signal_hint"),
        "may_decline": (card or {}).get("may_decline"),
        "caps": caps, "silence_offered": offered, "silence_chosen": chosen,
        "card_sanctions_silence": sanction, "verdict": verdict,
        "action_taken": at, "explicit_action": explicit, "parse": parse_status,
        "body_len": len(body), "recommended": (o or {}).get("response", {}).get("recommended") if o else None,
        "directions": (o or {}).get("directions"), "score10": mf.get("score10"),
        "veto": mf.get("veto"), "tokens": mf.get("tokens"), "latency_ms": mf.get("latency_ms"),
        "decline_reason": (o or {}).get("decline_reason"),
    }


def main():
    cmap = cards_map()
    out = {"kind": "r3_before_after_v1", "cards": CARDS, "batches": {}}
    for batch, spec in RUNS.items():
        rows = []
        for arm, cand in spec["arms"].items():
            for d in sorted(glob.glob(os.path.join(ROOT, spec["dir"], "%s_*" % arm, "out", "turn_*"))):
                cid = os.path.basename(d).split("_", 2)[-1]
                r = row_of(batch, arm, d, cmap.get(cid))
                r["episode"] = os.path.basename(os.path.dirname(os.path.dirname(d))).split("_", 1)[1]
                rows.append(r)
        out["batches"][batch] = rows
        print("%s rows=%d" % (batch, len(rows)))

    # 三分对照的说明（D1 旧答卷只作描述，不进入本批配对）
    out["three_way_note"] = ("三分对照只取描述统计：D1 批（r2，10-28 17:5x）与 R3A(r2) 是两次独立生成，"
                             "不是同一份答卷；R3A 内部才是同计划配对。")
    dst = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "deadline_quality_20261028",
                       "R3_BEFORE_AFTER_20261028.json")
    json.dump(out, io.open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("WROTE", os.path.relpath(dst, ROOT))
    for batch, rows in out["batches"].items():
        for r in rows:
            if r["verdict"] != "replied" or r["silence_offered"]:
                print("%s %-9s %-6s hint=%-4s md=%s caps=%-12s offered=%-5s chosen=%-5s verdict=%-9s score=%s"
                      % (batch, r["case_id"], r["arm"], r["hint"], r["may_decline"],
                         ",".join(r["caps"]), r["silence_offered"], r["silence_chosen"],
                         r["verdict"], r["score10"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
