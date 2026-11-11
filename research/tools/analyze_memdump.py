# -*- coding: utf-8 -*-
"""tools/analyze_memdump.py — W05-02 内容级 Fidelity 分析（零模型调用）。

输入：F1 批 e3_memdump 臂的 mechanism_notes.txt（memdump=<json> 每轮快照）+ stage4_src 事实表。
输出：per-fact capture/recall/latency、precision 分类（gt/seed/unmatched）、更正 supersession 核验。
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUNCT = set("，。！？、；：""''「」（）()《》<> \t\r\n-—…·")


def norm(t):
    return "".join(ch for ch in (t or "") if ch not in PUNCT)


def grams(t, n=4):
    t = norm(t)
    return {t[i:i + n] for i in range(max(1, len(t) - n + 1))} if len(t) >= n else {t} if t else set()


def overlap(a, b, n=4):
    ga, gb = grams(a, n), grams(b, n)
    if not ga or not gb:
        return 0.0
    return len(ga & gb) / min(len(ga), len(gb))


def load_dumps(batch_root, arm, story):
    """按轮序返回 [{turn, dump}]。"""
    rows = []
    for d in sorted(glob.glob(os.path.join(batch_root, "%s_%s" % (arm, story), "out", "turn_*")),
                    key=lambda p: int(re.search(r"turn_(\d+)", p).group(1)) if re.search(r"turn_(\d+)", p) else 0):
        p = os.path.join(d, "mechanism_notes.txt")
        if not os.path.exists(p):
            continue
        txt = open(p, encoding="utf-8").read()
        pos = txt.find("memdump=")
        if pos < 0:
            continue
        try:
            dump, _ = json.JSONDecoder().raw_decode(txt[pos + 8:])
        except Exception:
            continue
        t = int(re.search(r"turn_(\d+)", d).group(1))
        rows.append({"turn": t, "dump": dump})
    return rows


def load_gt(story):
    fn = {"E061": "E061_thyroid_followup.json", "E067": "E067_job_offer.json"}.get(story)
    p = os.path.join(ROOT, "benchmark", "stories_v2", "stage4_src", fn)
    j = json.load(open(p, encoding="utf-8"))
    st = j["stories"][0]
    gt = []
    for f in (st.get("facts") or []):
        src = f.get("source") or {}
        if src.get("kind") != "message":
            continue
        m = re.search(r"T(\d+)$", str(src.get("case_id", "")))
        gt.append({"fid": f.get("id"), "turn": int(m.group(1)) if m else 0,
                   "value": f.get("value", ""), "relation": f.get("relation", ""),
                   "is_correction": "更正" in str(f.get("relation", ""))})
    seeds = []
    for key, txt in (st.get("initial_kb") or {}).items():
        if isinstance(txt, str) and len(txt) > 6:
            seeds.append({"key": key, "text": txt})
    for key, txt in (st.get("persona") or {}).items():
        if isinstance(txt, str) and len(txt) > 6:
            seeds.append({"key": "persona." + key, "text": txt})
    return gt, seeds


def analyze(story, batch_root, arm="candidate"):
    dumps = load_dumps(batch_root, arm, story)
    if not dumps:
        return {"story": story, "error": "no memdump found"}
    gt, seeds = load_gt(story)
    final = dumps[-1]["dump"]
    facts_final = final.get("facts") or {}
    updates = final.get("updates") or []

    # per-gt-fact capture: first turn where a stored fact matches value (overlap>=0.6)
    capture = []
    for g in gt:
        first_turn, best = None, 0.0
        for row in dumps:
            for fid, f in (row["dump"].get("facts") or {}).items():
                ov = overlap(g["value"], f.get("t", ""))
                if ov >= 0.6 and (first_turn is None or row["turn"] < first_turn):
                    first_turn = row["turn"]
                    best = max(best, ov)
        capture.append({"fid": g["fid"], "turn": g["turn"], "relation": g["relation"],
                        "captured": first_turn is not None,
                        "latency_turns": (first_turn - g["turn"]) if first_turn is not None else None,
                        "best_overlap": round(best, 2)})

    # precision classification of final facts
    cls = {"gt": 0, "seed": 0, "unmatched": 0}
    unmatched_list = []
    for fid, f in facts_final.items():
        s = (overlap(f.get("t", ""), "", ), )
        if any(overlap(f.get("t", ""), g["value"]) >= 0.6 for g in gt):
            cls["gt"] += 1
        elif any(overlap(f.get("t", ""), sd["text"]) >= 0.5 for sd in seeds):
            cls["seed"] += 1
        else:
            cls["unmatched"] += 1
            if len(unmatched_list) < 12:
                unmatched_list.append({"fid": fid, "text": f.get("t", ""), "st": f.get("st")})

    # update supersession: corrections should appear in updates list with matching new_text
    corr = [g for g in gt if g["is_correction"]]
    sup = []
    for g in corr:
        hit = any(overlap(g["value"], u.get("t", "")) >= 0.5 for u in updates)
        sup.append({"fid": g["fid"], "turn": g["turn"], "value": g["value"][:40],
                    "update_recorded": hit})

    n_gt = len(gt)
    cap_n = sum(1 for c in capture if c["captured"])
    return {"story": story, "arm": arm, "turns_dumped": len(dumps),
            "facts_final_n": len(facts_final), "gt_n": n_gt,
            "capture": "%d/%d" % (cap_n, n_gt),
            "capture_rate": round(cap_n / n_gt, 2) if n_gt else None,
            "capture_detail": capture,
            "latency_turns_list": [c["latency_turns"] for c in capture if c["latency_turns"] is not None],
            "precision_class": cls,
            "unmatched_sample": unmatched_list,
            "corrections_superseded": "%d/%d" % (sum(1 for s in sup if s["update_recorded"]), len(sup)) if sup else "n/a",
            "corrections_detail": sup}


if __name__ == "__main__":
    story = sys.argv[1] if len(sys.argv) > 1 else "E061"
    broot = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "logs", "longprobe_f1_live_20261030")
    r = analyze(story, broot)
    print(json.dumps(r, ensure_ascii=False, indent=1)[:4000])
