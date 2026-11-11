# -*- coding: utf-8 -*-
"""tools/analyze_w20_03_judge_cal.py — W20-03 judge 校准指标计算（判据按设计冻结）。

输入：W20_03_JUDGE_CAL_RAW.jsonl、W20_03_GT_LABELS.json、锚点/历史素材原文。
输出：W20_03_JUDGE_CAL_METRICS.json（逐维资格表、引文校验率、severe P/R、顺序一致率）。
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030")
RAW = os.path.join(REFS, "W20_03_JUDGE_CAL_RAW.jsonl")
GT = os.path.join(REFS, "W20_03_GT_LABELS.json")
OUT = os.path.join(REFS, "W20_03_JUDGE_CAL_METRICS.json")
DIMS = ["goal", "fact", "attrib", "time", "boundary", "memory", "interact", "init", "emotion"]
NAMES = {"goal": "goal目标", "fact": "fact事实", "attrib": "attrib归属", "time": "time时间状态",
         "boundary": "boundary边界", "memory": "memory记忆", "interact": "interact互动",
         "init": "init主动", "emotion": "emotion情绪关系"}


def norm(s):
    return re.sub(r"\s+", "", s or "")


def load_raw():
    recs = []
    for line in open(RAW, encoding="utf-8"):
        line = line.strip()
        if line:
            recs.append(json.loads(line))
    return recs


def anchor_corpus(sid):
    base = os.path.join(ROOT, "benchmark", "stories_v2", "anchors_20261028", sid)
    ep = json.load(open(os.path.join(base, "episode.json"), encoding="utf-8"))
    t = ep["turns"][0]
    parts = [ep["persona"].get("me", ""), ep["persona"].get("her", "")]
    for v in (ep["initial_kb"] or {}).values():
        parts.append(str(v))
    for m in t["input"]["messages"]:
        parts.append(m["text"])
    gc = json.load(open(os.path.join(ROOT, "benchmark", "stories_v2", "anchors_20261028",
                                     "GRADING_CARDS_AK01_AK12.json"), encoding="utf-8"))["cards"]
    card = [c for c in gc if c["case_id"] == t["case_id"]][0]
    for k in ("current_action", "private_guidance"):
        parts.append(str(card.get(k, "")))
    for k in ("acceptable_actions", "unknowns", "protected_expressions",
              "forbidden_factual_claims", "applicable_dimensions"):
        for v in card.get(k, []) or []:
            parts.append(str(v))
    return parts


def quote_check(rec, corpus_parts):
    q = (rec.get("verdict") or {}).get("key_quote") or ""
    if not q.strip():
        return {"quote": "", "exact": False, "relaxed": False, "fragment": None, "empty": True}
    exact = any(q in p for p in corpus_parts)
    relaxed = any(norm(q) in norm(p) for p in corpus_parts)
    # 片段覆盖率：按标点切分后逐段（≥4字）在语料中命中
    frags = [norm(f) for f in re.split(r"[。！？；;，,/\s]+", q) if len(norm(f)) >= 4]
    frag_hit = sum(1 for f in frags if any(f in norm(p) for p in corpus_parts))
    frag = round(frag_hit / len(frags), 2) if frags else None
    return {"quote": q[:80], "exact": exact, "relaxed": relaxed, "fragment": frag,
            "empty": False}


def hist_corpus(sid, cid):
    """历史复读位语料：episode 消息 + kb + W19 卡面字段。"""
    ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", sid + ".json"),
                        encoding="utf-8"))
    parts = []
    for v in (ep.get("persona") or {}).values():
        parts.append(str(v))
    for v in (ep.get("initial_kb") or {}).values():
        parts.append(str(v))
    card = None
    for t in ep.get("turns", []):
        if t.get("case_id") != cid:
            continue
        for m in t["input"]["messages"]:
            parts.append(m["text"])
        parts.append(json.dumps(t["input"].get("intent"), ensure_ascii=False))
    gc = json.load(open(os.path.join(ROOT, "benchmark", "stories_v2", "wave3",
                                     "GRADING_CARDS_W19_CROSS.json"),
                        encoding="utf-8"))["cards"]
    for c in gc:
        if c["case_id"] == cid:
            card = c
            break
    if card:
        for k in ("current_action", "private_guidance"):
            parts.append(str(card.get(k, "")))
        for k in ("acceptable_actions", "unknowns", "protected_expressions",
                  "forbidden_factual_claims", "applicable_dimensions"):
            for v in card.get(k, []) or []:
                parts.append(str(v))
    return parts


def main():
    recs = load_raw()
    gt = {l["reply_id"]: l for l in json.load(open(GT, encoding="utf-8"))["labels"]}
    by_pair = {}
    for r in recs:
        by_pair.setdefault((r["pair_id"], r["kind"]), {})[r["order"]] = r

    stats = {"quote": {"n": 0, "exact": 0, "relaxed": 0, "empty": 0, "fails": []},
             "severe": {}, "sanity": [], "constructed": {}, "historical": []}
    # 逐维累加器
    fk = {d: [0, 0] for d in DIMS}          # 误杀 [hit, denom]（构造对，双序合并）
    ms = {d: [0, 0] for d in DIMS}          # 漏检
    win_pairs = [0, 0]                       # 顺序一致 [same, total]
    dim_stab = {d: [0, 0, 0] for d in DIMS}  # [完全一致, ±1内, total]
    good_scores = {d: [] for d in DIMS}
    hist = {"win_same": 0, "win_total": 0, "dim_exact": 0, "dim_pm1": 0, "dim_total": 0,
            "rows": []}

    sev_flags = []  # (reply_id, gt, orderA_flag, orderB_flag)

    for (pid, kind), orders in sorted(by_pair.items()):
        A = orders.get("A_good_left") or orders.get("A_cand_left") or orders.get("A_same")
        B = orders.get("B_good_right") or orders.get("B_cand_right")
        for r in (A, B):
            if not r:
                continue
            v = r.get("verdict") or {}
            if v.get("winner") in ("UNPARSEABLE", "CALL_FAIL", "BUDGET_REFUSED"):
                continue
            corpus = []
            if kind in ("constructed", "sanity"):
                sid = pid.split(":")[1]
                corpus = anchor_corpus(sid) + [r["left"], r["right"]]
            elif kind == "historical":
                corpus = hist_corpus(pid.split(":")[1], pid.split(":")[2]) + [r["left"], r["right"]]
            qc = quote_check(r, corpus)
            if qc["fragment"] is not None:
                stats["quote"].setdefault("frag_sum", 0)
                stats["quote"].setdefault("frag_n", 0)
                stats["quote"]["frag_sum"] += qc["fragment"]
                stats["quote"]["frag_n"] += 1
            st = stats["quote"]
            st["n"] += 1
            st["exact"] += 1 if qc["exact"] else 0
            st["relaxed"] += 1 if qc["relaxed"] else 0
            st["empty"] += 1 if qc["empty"] else 0
            if not qc["exact"] and not qc["empty"]:
                st["fails"].append({"pair_id": pid, "order": r["order"],
                                    "quote": qc["quote"], "relaxed": qc["relaxed"]})

        if kind == "sanity":
            w = (A.get("verdict") or {}).get("winner") if A else None
            stats["sanity"].append({"pair_id": pid, "winner": w})
            continue

        if kind == "constructed" and A and B:
            vA, vB = A.get("verdict") or {}, B.get("verdict") or {}
            if vA.get("winner") in ("UNPARSEABLE", "CALL_FAIL", "BUDGET_REFUSED") or \
               vB.get("winner") in ("UNPARSEABLE", "CALL_FAIL", "BUDGET_REFUSED"):
                continue
            # winner 归一到 good 臂
            wA = {"LEFT": "GOOD", "RIGHT": "NEG"}.get(vA.get("winner"), vA.get("winner"))
            wB = {"RIGHT": "GOOD", "LEFT": "NEG"}.get(vB.get("winner"), vB.get("winner"))
            win_pairs[1] += 1
            win_pairs[0] += 1 if wA == wB else 0
            neg_name = pid.split(":", 2)[2]
            sid = pid.split(":")[1]
            g_id, n_id = "good:%s" % sid, "%s:%s" % (neg_name, sid)
            # 逐维
            dA = {s: (vA.get(s) or {}) for s in ("L", "R")}
            dB = {s: (vB.get(s) or {}) for s in ("L", "R")}
            gA, gB = dA["L"], dB["R"]        # good 臂分（A 序 L / B 序 R）
            nA, nB = dA["R"], dB["L"]        # neg 臂分
            gtl, gtn = gt[g_id], gt[n_id]
            for d in DIMS:
                gv, nv = gA.get(d), nA.get(d)
                gv2, nv2 = gB.get(d), nB.get(d)
                for x in (gv, gv2):
                    if isinstance(x, int):
                        good_scores[d].append(x)
                if d in gtl["expected_ok_dims"]:
                    for x in (gv, gv2):
                        if isinstance(x, int):
                            fk[d][1] += 1
                            if x <= 1:
                                fk[d][0] += 1
                if d in gtn["targeted_dims"]:
                    for x in (nv, nv2):
                        if isinstance(x, int):
                            ms[d][1] += 1
                            if x >= 2:
                                ms[d][0] += 1
                if isinstance(gv, int) and isinstance(gv2, int):
                    diff = abs(gv - gv2)
                    dim_stab[d][2] += 1
                    dim_stab[d][0] += 1 if diff == 0 else 0
                    dim_stab[d][1] += 1 if diff <= 1 else 0
            # severe（GT 对照，主口径 = A 序旗标；B 序旗标单列）
            sevA = (vA.get("severe") or {})
            sevB = (vB.get("severe") or {})
            sev_flags.append({
                "pair_id": pid, "gt_good": gtl["severe_gt"], "gt_neg": gtn["severe_gt"],
                "A_good": sevA.get("L"), "A_neg": sevA.get("R"),
                "B_good": sevB.get("R"), "B_neg": sevB.get("L"),
                "note": (sevA.get("note") or "")[:160]})

        if kind == "historical" and A:
            sid, cid = pid.split(":")[1], pid.split(":")[2]
            stored_key = "LP:%s:%s" % (sid, cid)
            # stored 在 LP_W19CROSS_NINEDIM_REVIEW.json
            vA = A.get("verdict") or {}
            if vA.get("winner") in ("UNPARSEABLE", "CALL_FAIL", "BUDGET_REFUSED"):
                continue
            row = {"pair_id": pid}
            dA = vA.get("L") or {}
            winA = {"LEFT": "cand", "RIGHT": "par"}.get(vA.get("winner"), vA.get("winner"))
            if B:
                vB = B.get("verdict") or {}
                winB = {"RIGHT": "cand", "LEFT": "par"}.get(vB.get("winner"), vB.get("winner"))
                row["rerun_win_A"] = winA
                row["rerun_win_B"] = winB
                row["order_agree"] = (winA == winB)
                dB = vB.get("R") or {}
                for d in DIMS:
                    x, y = dA.get(d), dB.get(d)
                    if isinstance(x, int) and isinstance(y, int):
                        hist["dim_total"] += 1
                        hist["dim_exact"] += 1 if x == y else 0
                        hist["dim_pm1"] += 1 if abs(x - y) <= 1 else 0
            hist["rows"].append(row)

    # historical 对比 stored 判决
    rev_json = json.load(open(os.path.join(REFS, "LP_W19CROSS_NINEDIM_REVIEW.json"),
                              encoding="utf-8"))
    stored = {v["pair_id"]: v for v in rev_json["verdicts"]}
    for row in hist["rows"]:
        sid, cid = row["pair_id"].split(":")[1], row["pair_id"].split(":")[2]
        sv = stored.get("LP:%s:%s" % (sid, cid))
        if sv:
            row["stored_win_cand_view"] = sv.get("winner_cand_view")
            row["rerun_matches_stored"] = (
                {"cand": "candidate", "par": "parent"}.get(row.get("rerun_win_A"))
                == sv.get("winner_cand_view"))
            hist["win_total"] += 1
            hist["win_same"] += 1 if row.get("rerun_matches_stored") else 0

    # severe P/R（A 序主口径，NEG+GOOD 两侧都算）
    def pr(sel):
        tp = fp = fn = tn = 0
        for f in sev_flags:
            for arm, gt_key, flag_key in (("neg", "gt_neg", sel + "_neg"),
                                          ("good", "gt_good", sel + "_good")):
                g, p = f[gt_key], f[flag_key]
                if g and p:
                    tp += 1
                elif p and not g:
                    fp += 1
                elif g and not p:
                    fn += 1
                else:
                    tn += 1
        prec = tp / (tp + fp) if (tp + fp) else None
        rec_ = tp / (tp + fn) if (tp + fn) else None
        return {"tp": tp, "fp": fp, "fn": fn, "tn": tn,
                "precision": round(prec, 3) if prec is not None else None,
                "recall": round(rec_, 3) if rec_ is not None else None}

    # 逐维资格
    def qualify(d):
        fkr = fk[d][0] / fk[d][1] if fk[d][1] else None
        msr = ms[d][0] / ms[d][1] if ms[d][1] else None
        stab = (dim_stab[d][0] / dim_stab[d][2]) if dim_stab[d][2] else None
        win = (win_pairs[0] / win_pairs[1]) if win_pairs[1] else None
        if fkr is None and msr is None:
            return {"dim": d, "fk_rate": None, "miss_rate": None, "dim_stab": stab,
                    "verdict": "not-testable(this material)"}
        ok_full = ((fkr is None or fkr <= 0.10) and (msr is None or msr <= 0.20)
                   and (win is not None and win >= 0.80))
        ok_assist = ((fkr is None or fkr <= 0.25) and (msr is None or msr <= 0.40)
                     and (win is not None and win >= 0.65))
        v = "full-qualified" if ok_full else ("assistive-only" if ok_assist else "disqualified")
        return {"dim": d, "name": NAMES[d], "fk_hits": fk[d][0], "fk_denom": fk[d][1],
                "fk_rate": round(fkr, 3) if fkr is not None else None,
                "miss_hits": ms[d][0], "miss_denom": ms[d][1],
                "miss_rate": round(msr, 3) if msr is not None else None,
                "dim_stab_exact": dim_stab[d][0], "dim_stab_pm1": dim_stab[d][1],
                "dim_stab_total": dim_stab[d][2],
                "dim_order_stab": round(stab, 3) if stab is not None else None,
                "verdict": v}

    metrics = {
        "kind": "w20_03_judge_cal_metrics", "date": "2026-11-03",
        "n_records": len(recs),
        "quote_check": {"n": stats["quote"]["n"], "exact_pass": stats["quote"]["exact"],
                        "relaxed_pass": stats["quote"]["relaxed"],
                        "empty_quote": stats["quote"]["empty"],
                        "exact_pass_rate": round(stats["quote"]["exact"] / stats["quote"]["n"], 3)
                        if stats["quote"]["n"] else None,
                        "fragment_mean_cover": round(stats["quote"]["frag_sum"] /
                                                     stats["quote"]["frag_n"], 3)
                        if stats["quote"].get("frag_n") else None,
                        "fails": stats["quote"]["fails"][:40]},
        "order_winner_agreement_constructed": {"same": win_pairs[0], "total": win_pairs[1],
                                               "rate": round(win_pairs[0] / win_pairs[1], 3)
                                               if win_pairs[1] else None},
        "good_dim_scores_mean": {d: round(sum(v) / len(v), 3) if v else None
                                 for d, v in good_scores.items()},
        "per_dim": [qualify(d) for d in DIMS],
        "severe": {"primary_orderA": pr("A"), "orderB": pr("B"),
                   "n_pairs_with_flags": len(sev_flags), "flags": sev_flags},
        "historical_recheck": {"win_match_stored": hist["win_same"],
                               "win_total": hist["win_total"],
                               "order_agree": None, "dim_exact_rate": None,
                               "rows": hist["rows"]},
        "sanity_identical_text": stats["sanity"],
    }
    # historical order agree 汇总
    oa = [r for r in hist["rows"] if r.get("order_agree") is not None]
    if oa:
        metrics["historical_recheck"]["order_agree"] = round(
            sum(1 for r in oa if r["order_agree"]) / len(oa), 3)
    if hist["dim_total"]:
        metrics["historical_recheck"]["dim_exact_rate"] = round(hist["dim_exact"] / hist["dim_total"], 3)
        metrics["historical_recheck"]["dim_pm1_rate"] = round(hist["dim_pm1"] / hist["dim_total"], 3)
    json.dump(metrics, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("metrics saved:", OUT)
    for p in metrics["per_dim"]:
        print(p)
    print("quote:", metrics["quote_check"]["exact_pass"], "/", metrics["quote_check"]["n"],
          "severe A:", metrics["severe"]["primary_orderA"])
    print("order agree (constructed):", metrics["order_winner_agreement_constructed"])


if __name__ == "__main__":
    main()
