# -*- coding: utf-8 -*-
"""tools/run_longprobe_review.py — 长剧探针批九维评审（48 主判 + 8 双序 = 56 次）。

复用 run_fs1_semantic_review 的评审器（SYS/DIMS/call/装配），批对象换成 E061/E062。
授权：AUTH-LONGPROBE-JUDGE-20261030-01，cap 60。温度 0.0 非流式；零重试。
fact/time 维按资格批处置降辅助（B3 亲读为准）。
"""
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
spec = importlib.util.spec_from_file_location(
    "rev", os.path.join(ROOT, "tools", "run_fs1_semantic_review.py"))
rev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rev)

AID = "AUTH-LONGPROBE-JUDGE-20261030-01"
LEDGER = os.path.join(ROOT, "logs", "auth_budget", AID + ".jsonl")
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030",
                   "LONGPROBE_NINEDIM_REVIEW.json")
STORIES = ["E061", "E062"]
BATCHES = {"A": ("E3", os.path.join(ROOT, "logs", "longprobe_a_live_20261030"))}
SWAP_SUBSET = {"T08", "T16", "T20", "T24"}
CARDS = os.path.join(ROOT, "benchmark", "stories_v2", "stage4", "GRADING_CARDS_STAGE4.json")


def main():
    from isolation.authorization_budget import AuthorizationBudget  # noqa: E402
    # 覆写 rev 模块全局（load_context/load_cards 读的是 rev 自己的 STORIES/CARDS）
    rev.STORIES = STORIES
    rev.BATCHES = BATCHES
    rev.SWAP_SUBSET = SWAP_SUBSET
    rev.CARDS = CARDS
    rev.OUT = OUT
    scope = {"scope_id": "longprobe_ninedim_review_v1",
             "purpose": "W03-04 long probe nine-dim review: 48 primary blind pairs (E3 vs B0) + 8 swap subset = 56 attempts; dsf temp0.0 non-stream; fact/time dims assistive per judge qualification",
             "automatic_candidate_generations": False,
             "endpoint": "http://127.0.0.1:17770/v1", "model_alias": "dsf"}
    budget = AuthorizationBudget(LEDGER, AID, 60, status="ACTIVE", scope=scope)
    budget.ensure_batch(56, purpose="longprobe_review_full")
    print("budget:", budget.summary())

    ctx = rev.load_context()
    cards = rev.load_cards()
    verdicts = []
    for bname, (cand_name, broot) in BATCHES.items():
        for sid in STORIES:
            for row in ctx[sid]["rows"]:
                cid = row["cid"]
                card = cards.get(cid, {})
                cand = rev.reply_of(broot, "candidate", sid, row["idx"], cid)
                par = rev.reply_of(broot, "parent", sid, row["idx"], cid)
                swap = cid[-3:] in SWAP_SUBSET
                left, right = (par, cand) if swap else (cand, par)
                user = rev.build_user(sid, row, card, left, right, ctx[sid])
                pair_id = "LP:%s:%s" % (sid, cid)
                aid = "lpr:%s:%s" % (pair_id, "swap" if swap else "main")
                r = rev.call(user, budget, aid)
                v = rev.parse_verdict(r["text"]) if r.get("ok") else {"winner": "CALL_FAIL"}

                def norm(side):
                    if side == "LEFT":
                        return "parent" if swap else "candidate"
                    if side == "RIGHT":
                        return "candidate" if swap else "parent"
                    return side
                dims_n = {"candidate": {}, "parent": {}}
                if v.get("winner") not in ("UNPARSEABLE", "CALL_FAIL"):
                    for k in ("L", "R"):
                        arm = "parent" if (k == "L") == swap else "candidate"
                        dims_n[arm] = {dd: (v.get(k) or {}).get(dd) for dd in rev.DIMS}
                sev = v.get("severe") or {}
                verdicts.append({
                    "pair_id": pair_id, "batch": bname, "candidate_arm": cand_name,
                    "swap": swap, "order": "swap" if swap else "main",
                    "winner_cand_view": norm(v.get("winner")) if v.get("winner") in ("LEFT", "RIGHT") else v.get("winner"),
                    "dims": dims_n,
                    "severe": {"candidate": sev.get("L" if not swap else "R"),
                               "parent": sev.get("R" if not swap else "L"),
                               "note": sev.get("note", "")},
                    "key_quote": v.get("key_quote", ""), "raw": v.get("raw", "")})
                print("%s %s -> %s" % (pair_id, "swap" if swap else "main", verdicts[-1]["winner_cand_view"]))

    def dim_stats():
        rows = [v for v in verdicts if v["order"] == "main"
                and v["winner_cand_view"] not in ("UNPARSEABLE", "CALL_FAIL")]
        out = {}
        for dd in rev.DIMS:
            diffs = [v["dims"]["candidate"][dd] - v["dims"]["parent"][dd]
                     for v in rows
                     if v["dims"]["candidate"].get(dd) is not None
                     and v["dims"]["parent"].get(dd) is not None]
            n = len(diffs)
            out[dd] = {"n": n,
                       "cand_mean": round(sum(v["dims"]["candidate"].get(dd) or 0 for v in rows) / max(1, len([v for v in rows if v["dims"]["candidate"].get(dd) is not None])), 3) if n else None,
                       "parent_mean": round(sum(v["dims"]["parent"].get(dd) or 0 for v in rows) / max(1, len([v for v in rows if v["dims"]["parent"].get(dd) is not None])), 3) if n else None,
                       "paired_diff_mean": round(sum(diffs) / n, 3) if n else None,
                       "cand_wins": sum(1 for x in diffs if x > 0),
                       "parent_wins": sum(1 for x in diffs if x < 0)}
        return out
    sev_c = sum(1 for v in verdicts if v["severe"]["candidate"])
    sev_p = sum(1 for v in verdicts if v["severe"]["parent"])
    main_by_pair = {v["pair_id"]: v["winner_cand_view"] for v in verdicts if v["order"] == "main"}
    swaps = [v for v in verdicts if v["order"] == "swap"]
    disagreements = [{"pair_id": v["pair_id"], "main": main_by_pair.get(v["pair_id"]),
                      "swapped": v["winner_cand_view"]}
                     for v in swaps
                     if v["winner_cand_view"] not in ("UNPARSEABLE", "CALL_FAIL")
                     and v["winner_cand_view"] != main_by_pair.get(v["pair_id"])]
    report = {"kind": "longprobe_ninedim_review", "date": "2026-10-30",
              "authorization": AID, "anchors": "W04-01_RUBRIC_ANCHORS_V1",
              "n_main": len(verdicts) - len(swaps), "n_swap": len(swaps),
              "dims": dim_stats(),
              "severe": {"candidate": sev_c, "parent": sev_p},
              "swap_disagreements": disagreements,
              "verdicts": verdicts,
              "note": "E062_T24 探针存在素材缺陷（快门数品类错误）已按 E-MAT 登记；故事级=探索性读数（n=2）"}
    json.dump(report, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(OUT, encoding="utf-8"))
    for dd, st in report["dims"].items():
        print("  %-9s n=%s diff=%s (cand %s vs %s)" % (dd, st["n"], st["paired_diff_mean"],
                                                       st["cand_mean"], st["parent_mean"]))
    print("severe cand/par:", sev_c, "/", sev_p, "| swap disagreements:", len(disagreements))
    print("report:", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
