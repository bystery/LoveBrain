# -*- coding: utf-8 -*-
"""tools/fs1_swap_supplement.py — 双序子集补判（预注册 §B 实现偏差修正）。

主审 96 判中 T05/T09 位实现为"左右轮换单判"（parent 在左），非同对双判。
本工具对同 16 位以相反顺序（candidate 在左）再判一次，得真·双序 disagreement。
复用 -02 授权（账本余 24 ≥ 16）；结果并入 NINEDIM_REVIEW.json；不重算 dims。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import importlib.util

spec = importlib.util.spec_from_file_location(
    "rev", os.path.join(ROOT, "tools", "run_fs1_semantic_review.py"))
rev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rev)

STORIES = rev.STORIES


def main():
    # scope 原文从账本读回，保证逐字匹配
    scope = None
    for line in open(rev.LEDGER, encoding="utf-8"):
        e = json.loads(line)
        if e.get("kind") == "authorization_scope":
            scope = e["scope"]
    if scope is None:
        print("no scope record; abort")
        return 1
    budget = rev.AuthorizationBudget(rev.LEDGER, rev.AID, 120, scope=scope)
    budget.ensure_batch(16, purpose="fs1_ninedim_swap_supplement")
    print("budget:", budget.summary())

    ctx = rev.load_context()
    cards = rev.load_cards()
    report = json.load(open(rev.OUT, encoding="utf-8"))
    main_by_pair = {v["pair_id"]: v for v in report["verdicts"]}
    supplements, disagreements = [], []
    n = 0
    for bname, (cand_name, broot) in rev.BATCHES.items():
        for sid in STORIES:
            for row in ctx[sid]["rows"]:
                if row["cid"][-3:] not in rev.SWAP_SUBSET:
                    continue
                card = cards.get(row["cid"], {})
                cand = rev.reply_of(broot, "candidate", sid, row["idx"], row["cid"])
                par = rev.reply_of(broot, "parent", sid, row["idx"], row["cid"])
                user = rev.build_user(sid, row, card, cand, par, ctx[sid])  # 相反顺序：cand 在左
                pair_id = "NIND:%s:%s:%s" % (bname, sid, row["cid"])
                n += 1
                aid = "nindswap:%s" % pair_id
                r = rev.call(user, budget, aid)
                v = rev.parse_verdict(r["text"]) if r.get("ok") else {"winner": "CALL_FAIL"}
                w = v.get("winner")
                w_norm = {"LEFT": "candidate", "RIGHT": "parent"}.get(w, w)
                w_main = main_by_pair[pair_id]["winner_cand_view"]
                agree = (w_norm == w_main)
                if not agree and w_norm not in ("UNPARSEABLE", "CALL_FAIL"):
                    disagreements.append({"pair_id": pair_id, "main": w_main, "swapped": w_norm})
                supplements.append({"pair_id": pair_id, "swapped_winner": w_norm,
                                    "main_winner": w_main, "agree": agree,
                                    "severe": v.get("severe", {}), "quote": v.get("key_quote", "")})
                print("%2d %s swapped->%s (main %s, agree=%s)" % (n, pair_id, w_norm, w_main, agree))

    report["swap_supplement"] = supplements
    report["swap_disagreements_true"] = disagreements
    report["implementation_note"] = ("主审96判中T05/T09为左右轮换单判；双序补判16位见 swap_supplement"
                                     "（2026-10-30 补齐，同一授权账本）")
    json.dump(report, open(rev.OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(rev.OUT, encoding="utf-8"))
    ok = sum(1 for s in supplements if s["swapped_winner"] not in ("UNPARSEABLE", "CALL_FAIL"))
    print("supplement: %d judged, %d ok, disagreements=%d" % (n, ok, len(disagreements)))
    print("report:", rev.OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
