# -*- coding: utf-8 -*-
"""tools/run_lp_review_generic.py — 长剧批通用九维评审驱动。

用法：python tools/run_lp_review_generic.py --batch-dir logs/longprobe_b1_live_20261030 \
  --stories E063,E064,E065 --auth AUTH-LONGPROBE-B1-JUDGE-20261030-01 --tag B1
结构：72 位 → main 判 60（swap 位 T08/T16/T20/T24 以 swap 顺序单判 12）；
另含真双序补判 12（同批内完成，attempt id 分离）。cap = 72+2。
"""
import argparse
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

SWAP_SUBSET = {"T08", "T16", "T20", "T24"}
DIMS = rev.DIMS


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--batch-dir", required=True)
    ap.add_argument("--stories", required=True)
    ap.add_argument("--auth", required=True)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--cand-name", default="E3")
    ap.add_argument("--cards", default=os.path.join(
        ROOT, "benchmark", "stories_v2", "stage4", "GRADING_CARDS_STAGE4.json"),
        help="评审卡路径（LR2 等 wave1_ext 批用 GRADING_CARDS_W12.json）")
    ap.add_argument("--cap", type=int, default=None,
                    help="授权额度（须≥账本绑定值；缺省=自算 n_total+n_sup+重试预留+2）")
    ap.add_argument("--quote-check-dirs", default=None,
                    help="引文逐字硬校验语料批目录（逗号分隔，按序探测；缺省=batch-dir）")
    a = ap.parse_args()
    stories = a.stories.split(",")
    broot = os.path.join(ROOT, a.batch_dir)
    out = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030",
                       "LP_%s_NINEDIM_REVIEW.json" % a.tag)
    cards_path = a.cards
    rev.STORIES = stories
    rev.CARDS = cards_path
    from isolation.authorization_budget import AuthorizationBudget, BudgetRefused  # noqa: E402
    from b3_queue import build_queue  # noqa: E402  # W16-05#3: force-B3 queue per review
    ctx = rev.load_context()
    cards = rev.load_cards()
    aid = a.auth
    ledger = os.path.join(ROOT, "logs", "auth_budget", aid + ".jsonl")
    scope = {"scope_id": "lp_review_%s" % a.tag,
             "purpose": "long drama review %s: main-order judgments (swap-subset positions judged once in swap order) + true double-order supplement; dsf temp0.0; fact/time assistive per qualification" % a.tag,
             "automatic_candidate_generations": False,
             "endpoint": "http://127.0.0.1:17770/v1", "model_alias": "dsf"}
    n_total = sum(len(ctx[sid]["rows"]) for sid in stories)
    n_swap = sum(1 for sid in stories for row in ctx[sid]["rows"]
                 if row["cid"][-3:] in SWAP_SUBSET)
    n_sup = n_swap  # one true-double-order supplement per swap position
    # 2026-11-02（LD1 评审 32 次 CALL_FAIL 教训）：重试额度预登记 ≤20%，超此不重试
    retry_headroom = max(2, (n_total + n_sup) * 2 // 10)
    cap_needed = n_total + n_sup + retry_headroom + 2
    cap = a.cap if a.cap else cap_needed
    if cap < cap_needed:
        raise SystemExit("--cap %d < 自算最低 %d（重试预留会无额度）" % (cap, cap_needed))
    # 断点续跑：每个判定落一行 sidecar，崩溃/换授权后只补未判位，不重复烧调用
    partial_path = out.replace("_NINEDIM_REVIEW.json", "_PARTIAL.jsonl")
    done_main, done_sup = {}, {}
    if os.path.exists(partial_path):
        for line in open(partial_path, encoding="utf-8"):
            try:
                rec = json.loads(line)
            except Exception:
                continue
            if rec.get("tag") != a.tag:
                continue
            if rec.get("kind") == "supplement":
                done_sup[rec["pair_id"]] = rec
            elif rec.get("kind") == "verdict":
                done_main[(rec["pair_id"], rec.get("order"))] = rec["verdict"]
        if done_main or done_sup:
            print("resume: 已有判定 main=%d sup=%d（跳过重判）" % (len(done_main), len(done_sup)))

    def _persist(rec):
        with open(partial_path, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(rec, ensure_ascii=False) + "\n")

    budget = AuthorizationBudget(ledger, aid, cap, status="ACTIVE", scope=scope)
    budget.ensure_batch(n_total, purpose="lp_review_%s" % a.tag)
    print("budget:", budget.summary())

    def call_with_retry(user, attempt_id, tries=2):
        """评审侧有界重试：单次调用失败或无法解析时重试一次；仍失败保持 CALL_FAIL。
        预算耗尽（BudgetRefused）不再重试，直接返回失败占位，保证报告仍能落盘。"""
        try:
            r = rev.call(user, budget, attempt_id)
        except BudgetRefused:
            return {"ok": False, "text": "", "budget_refused": True}, 1
        if r.get("ok") and rev.parse_verdict(r["text"]).get("winner") not in ("UNPARSEABLE", "CALL_FAIL"):
            return r, 1
        for i in range(1, tries):
            try:
                r2 = rev.call(user, budget, "%s-r%d" % (attempt_id, i))
            except BudgetRefused:
                break
            if r2.get("ok") and rev.parse_verdict(r2["text"]).get("winner") not in ("UNPARSEABLE", "CALL_FAIL"):
                return r2, i + 1
        return r, tries

    verdicts = []
    for sid in stories:
        for row in ctx[sid]["rows"]:
            cid = row["cid"]
            card = cards.get(cid, {})
            cand = rev.reply_of(broot, "candidate", sid, row["idx"], cid)
            par = rev.reply_of(broot, "parent", sid, row["idx"], cid)
            swap = cid[-3:] in SWAP_SUBSET
            left, right = (par, cand) if swap else (cand, par)
            user = rev.build_user(sid, row, card, left, right, ctx[sid])
            pair_id = "LP:%s:%s" % (sid, cid)
            order_key = "swap" if swap else "main"
            prior = done_main.get((pair_id, order_key))
            if prior and prior.get("winner_cand_view") not in ("CALL_FAIL",):
                verdicts.append(prior)
                print("%s %s -> %s (resumed)" % (pair_id, order_key, prior["winner_cand_view"]))
                continue
            r, tries_used = call_with_retry(user, "lpr:%s:%s" % (pair_id, order_key))
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
                    dims_n[arm] = {dd: (v.get(k) or {}).get(dd) for dd in DIMS}
            sev = v.get("severe") or {}
            verdicts.append({"pair_id": pair_id, "candidate_arm": a.cand_name, "swap": swap,
                             "order": "swap" if swap else "main",
                             "winner_cand_view": norm(v.get("winner")) if v.get("winner") in ("LEFT", "RIGHT") else v.get("winner"),
                             "dims": dims_n,
                             "severe": {"candidate": sev.get("L" if not swap else "R"),
                                        "parent": sev.get("R" if not swap else "L"),
                                        "note": sev.get("note", "")},
                             "key_quote": v.get("key_quote", ""), "raw": v.get("raw", ""),
                             "attempts_used": tries_used})
            _persist({"kind": "verdict", "tag": a.tag, "pair_id": pair_id,
                      "order": verdicts[-1]["order"], "verdict": verdicts[-1]})
            print("%s %s -> %s" % (pair_id, verdicts[-1]["order"], verdicts[-1]["winner_cand_view"]))
    # 真双序补判：swap 位以 main 顺序再判
    by_pair = {v["pair_id"]: v for v in verdicts}
    supplements = []
    for sid in stories:
        for row in ctx[sid]["rows"]:
            if row["cid"][-3:] not in SWAP_SUBSET:
                continue
            pair_id = "LP:%s:%s" % (sid, row["cid"])
            if pair_id in done_sup and done_sup[pair_id].get("main_winner") not in ("CALL_FAIL",):
                supplements.append(done_sup[pair_id])
                print("%s true2nd (resumed)" % pair_id)
                continue
            cand = rev.reply_of(broot, "candidate", sid, row["idx"], row["cid"])
            par = rev.reply_of(broot, "parent", sid, row["idx"], row["cid"])
            user = rev.build_user(sid, row, cards.get(row["cid"], {}), cand, par, ctx[sid])
            r, tries_used = call_with_retry(user, "lpr2:%s" % pair_id)
            v = rev.parse_verdict(r["text"]) if r.get("ok") else {"winner": "CALL_FAIL"}
            w = {"LEFT": "candidate", "RIGHT": "parent"}.get(v.get("winner"), v.get("winner"))
            w_swap = by_pair[pair_id]["winner_cand_view"]
            supplements.append({"pair_id": pair_id, "main_winner": w, "swap_winner": w_swap,
                                "agree": w == w_swap, "quote": v.get("key_quote", ""),
                                "budget_refused": bool(r.get("budget_refused"))})
            _persist({"kind": "supplement", "tag": a.tag, **supplements[-1]})
            print("%s true2nd main->%s swap->%s agree=%s" % (pair_id, w, w_swap, w == w_swap))
    # 聚合（main 顺序集合；swap 位的 main 顺序读数取补判）
    main_view = {}
    for v in verdicts:
        if v["order"] == "main":
            main_view[v["pair_id"]] = v
    for s in supplements:
        if s["main_winner"] not in ("UNPARSEABLE", "CALL_FAIL"):
            if s["pair_id"] in by_pair:
                sv = dict(by_pair[s["pair_id"]])
                sv["winner_cand_view"] = s["main_winner"]
                main_view[s["pair_id"]] = sv
    rows = [v for v in main_view.values() if v["winner_cand_view"] not in ("UNPARSEABLE", "CALL_FAIL")]
    dims = {}
    for dd in DIMS:
        diffs = [v["dims"]["candidate"][dd] - v["dims"]["parent"][dd] for v in rows
                 if v["dims"]["candidate"].get(dd) is not None and v["dims"]["parent"].get(dd) is not None]
        n = len(diffs)
        cv = [v["dims"]["candidate"][dd] for v in rows if v["dims"]["candidate"].get(dd) is not None]
        pv = [v["dims"]["parent"][dd] for v in rows if v["dims"]["parent"].get(dd) is not None]
        dims[dd] = {"n": n,
                    "cand_mean": round(sum(cv) / len(cv), 3) if cv else None,
                    "parent_mean": round(sum(pv) / len(pv), 3) if pv else None,
                    "paired_diff_mean": round(sum(diffs) / n, 3) if n else None,
                    "cand_wins": sum(1 for x in diffs if x > 0),
                    "parent_wins": sum(1 for x in diffs if x < 0)}
    sev_c = sum(1 for v in verdicts if v["severe"]["candidate"])
    sev_p = sum(1 for v in verdicts if v["severe"]["parent"])
    # 评审质量旗标：重试后仍有 CALL_FAIL/UNPARSEABLE → 失败率入报告（b3_queue 已按 QUOTE-RISK
    # 记 pending_personal_read；失败率 >10% 时下游 severe 账须降权并标注）
    n_fail = sum(1 for v in verdicts if v["winner_cand_view"] in ("UNPARSEABLE", "CALL_FAIL"))
    fail_rate = round(n_fail / max(1, n_total), 3)
    disagree = [s for s in supplements if not s["agree"]
                and s["main_winner"] not in ("UNPARSEABLE", "CALL_FAIL")
                and s["swap_winner"] not in ("UNPARSEABLE", "CALL_FAIL")]
    report = {"kind": "lp_ninedim_review", "tag": a.tag, "date": "2026-10-30",
              "authorization": aid, "anchors": "W04-01_RUBRIC_ANCHORS_V1",
              "stories": stories, "n_positions": n_total,
              "dims": dims, "severe": {"candidate": sev_c, "parent": sev_p},
              "review_call_quality": {"n_call_fail": n_fail, "fail_rate": fail_rate,
                                      "fail_rate_high": fail_rate > 0.10,
                                      "policy": "单次失败重试1次；重试额度预登记≤20%"},
              "swap_agreement": "%d/%d" % (len(supplements) - len(disagree), len(supplements)),
              "swap_disagreements_true": disagree,
              "verdicts": verdicts, "swap_supplement": supplements,
              "note": "故事级=探索性读数；fact/time 维 assistive（资格批处置）；安抚压力位 severe 需逐位 B3 复核"}
    json.dump(report, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(out, encoding="utf-8"))
    # W20-03 §4-1 接线：引文逐字硬校验（确定性、零调用；severe 引文不过→severe_unverified 强制 B3）
    try:
        from quote_hard_check import run_check  # noqa: E402
        qc_out = out.replace("_NINEDIM_REVIEW.json", "_QUOTE_CHECK.json")
        qc_dirs = (a.quote_check_dirs or a.batch_dir).split(",")
        qc = run_check(out, qc_dirs, cards_path, qc_out)
        print("quote_hard_check:", qc_out, "| strict %s/%s" % (
            qc["summary"]["strict_hit"], qc["summary"]["n"]),
            "| severe_unverified:", qc["summary"]["severe_unverified"])
    except Exception as _e:  # noqa: BLE001 — 硬校验是附注器，绝不阻断评审落盘
        print("quote_hard_check: skipped (%s)" % _e)
    for dd, st in dims.items():
        print("  %-9s n=%s diff=%s" % (dd, st["n"], st["paired_diff_mean"]))
    print("severe cand/par:", sev_c, "/", sev_p, "| swap agree:", report["swap_agreement"])
    print("report:", out)
    try:
        bq = build_queue(out, broot,
                         os.path.join(ROOT, "benchmark", "stories_v2", "stage4", "GRADING_CARDS_STAGE4.json"),
                         material_cells=("E021_T07",))
        qpath = out.replace("_NINEDIM_REVIEW.json", "_B3_QUEUE.json")
        json.dump(bq, open(qpath, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
        print("b3_queue:", qpath, "(%d positions, P1=%d)" % (
            bq["n_queued"], sum(1 for e in bq["queue"] if e["priority"] == 1)))
    except Exception as _e:  # noqa: BLE001 — queue is advisory, never block the report
        print("b3_queue: skipped (%s)" % _e)
    return 0


if __name__ == "__main__":
    sys.exit(main())
