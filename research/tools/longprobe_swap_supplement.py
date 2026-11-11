# -*- coding: utf-8 -*-
"""tools/longprobe_swap_supplement.py — 长剧评审真双序补判（8 判，AUTH-...-02 cap 10）。

主批 56 判中 T08/T16/T20/T24 位为 swap 单判（无 main 对照）；本工具同对以 main 顺序再判，
得真双序 disagreement。复用 rev 评审器。
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

AID = "AUTH-LONGPROBE-JUDGE-20261030-02"
LEDGER = os.path.join(ROOT, "logs", "auth_budget", AID + ".jsonl")
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030",
                   "LONGPROBE_NINEDIM_REVIEW.json")
STORIES = ["E061", "E062"]
SWAP_SUBSET = {"T08", "T16", "T20", "T24"}
CARDS = os.path.join(ROOT, "benchmark", "stories_v2", "stage4", "GRADING_CARDS_STAGE4.json")
BROOT = os.path.join(ROOT, "logs", "longprobe_a_live_20261030")


def main():
    from isolation.authorization_budget import AuthorizationBudget  # noqa: E402
    rev.STORIES = STORIES
    rev.CARDS = CARDS
    scope = {"scope_id": "longprobe_swap_supplement_v1",
             "purpose": "true double-order supplement: 8 swap-judged pairs re-judged in main order; dsf temp0.0 non-stream",
             "automatic_candidate_generations": False,
             "endpoint": "http://127.0.0.1:17770/v1", "model_alias": "dsf"}
    budget = AuthorizationBudget(LEDGER, AID, 10, status="ACTIVE", scope=scope)
    budget.ensure_batch(8, purpose="longprobe_swap_supplement")
    report = json.load(open(OUT, encoding="utf-8"))
    ctx = rev.load_context()
    cards = rev.load_cards()
    swaps = {v["pair_id"]: v for v in report["verdicts"] if v["order"] == "swap"}
    supplements = []
    for sid in STORIES:
        for row in ctx[sid]["rows"]:
            if row["cid"][-3:] not in SWAP_SUBSET:
                continue
            pair_id = "LP:%s:%s" % (sid, row["cid"])
            if pair_id not in swaps:
                continue
            cand = rev.reply_of(BROOT, "candidate", sid, row["idx"], row["cid"])
            par = rev.reply_of(BROOT, "parent", sid, row["idx"], row["cid"])
            user = rev.build_user(sid, row, cards.get(row["cid"], {}), cand, par, ctx[sid])
            aid = "lpr2:%s" % pair_id
            r = rev.call(user, budget, aid)
            v = rev.parse_verdict(r["text"]) if r.get("ok") else {"winner": "CALL_FAIL"}
            w = v.get("winner")
            w_norm = {"LEFT": "candidate", "RIGHT": "parent"}.get(w, w)
            w_swap = swaps[pair_id]["winner_cand_view"]
            agree = (w_norm == w_swap)
            supplements.append({"pair_id": pair_id, "main_winner": w_norm,
                                "swap_winner": w_swap, "agree": agree,
                                "quote": v.get("key_quote", "")})
            print("%s main->%s swap->%s agree=%s" % (pair_id, w_norm, w_swap, agree))
    report["true_swap_supplement"] = supplements
    report["swap_note"] = ("主批 swap 位无 main 对照（8/8 伪计数已废弃）；真双序对比见 true_swap_supplement")
    agree_n = sum(1 for s in supplements if s["agree"])
    report["swap_disagreements_true"] = [s for s in supplements if not s["agree"]]
    json.dump(report, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(OUT, encoding="utf-8"))
    print("true swap agreement: %d/%d" % (agree_n, len(supplements)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
