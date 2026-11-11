# -*- coding: utf-8 -*-
"""tools/run_w20_03_judge_cal.py — W20-03 judge 锚点校准批执行器。

形态：与九维真实评审完全同构（import run_fs1_semantic_review 复用 SYS/parse；
temp 0.0 非流式 dsf；配对盲评）。不修改任何既有评审工具。

计划（跑前冻结，见 W20_03_JUDGE_CAL_DESIGN_20261103.md §4）：
  A) 36 个构造对（好臂=锚点 worker 参考回复 × 坏臂=neg_* 真跑回复）× 双序 = 72
  B) 历史复读 12 位（LP_W19CROSS 已判位抽样）× 双序 = 24
  C) 同文 sanity（好臂×好臂，AK01–AK06 单序）= 6
  合计 102 计划调用；有界重试 1（仅传输失败/UNPARSEABLE）；账本
  AUTH-W20-JUDGE-CAL-20261103-01 cap 160（含重试）；并发 6。
"""
import concurrent.futures
import importlib.util
import json
import os
import sys
import threading

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
spec = importlib.util.spec_from_file_location(
    "rev", os.path.join(ROOT, "tools", "run_fs1_semantic_review.py"))
rev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rev)

from isolation.authorization_budget import AuthorizationBudget, BudgetRefused  # noqa: E402

REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030")
AID = "AUTH-W20-JUDGE-CAL-20261103-01"
CAP = 160
OUT_RAW = os.path.join(REFS, "W20_03_JUDGE_CAL_RAW.jsonl")
ANCHOR_DIR = os.path.join(ROOT, "benchmark", "stories_v2", "anchors_20261028")
ANCHOR_CARDS = os.path.join(ANCHOR_DIR, "GRADING_CARDS_AK01_AK12.json")
GOOD = json.load(open(os.path.join(REFS, "W20_03_ANCHOR_REPLIES.json"), encoding="utf-8"))["replies"]
NEG_ROOT = os.path.join(ROOT, "logs", "w20_03_neggen_20261103")
NEGS = ["neg_a_amnesia", "neg_b_dumper", "neg_c_service"]
SIDS = ["AK%02d" % i for i in range(1, 13)]

W19_STORIES = ["E157", "E164", "E170", "E183", "E191", "E212",
               "LRE117", "LRE121", "LRE138", "LRE146"]
W19_ROOTS = {"E157": "w19_cross_live_20261103", "E164": "w19_cross_live_20261103",
             "E170": "w19_cross_live_20261103", "E183": "w19_cross_live_20261103",
             "E191": "w19_cross_live_20261103", "E212": "w19_cross_live_20261103",
             "LRE117": "w19b_cross_live_20261103", "LRE121": "w19b_cross_live_20261103",
             "LRE138": "w19b_cross_live_20261103", "LRE146": "w19b_cross_live_20261103"}
W19_SAMPLE = ["LRE117:LRE117_T08", "LRE121:LRE121_T08", "LRE146:LRE146_T08",
              "LRE138:LRE138_T08", "E157:E157_T08", "E164:E164_T08",
              "LRE117:LRE117_T01", "LRE121:LRE121_T01", "LRE146:LRE146_T01",
              "LRE138:LRE138_T01", "E157:E157_T05", "E183:E183_T05"]
W19_CARDS = os.path.join(ROOT, "benchmark", "stories_v2", "wave3", "GRADING_CARDS_W19_CROSS.json")

_print_lock = threading.Lock()


def log(msg):
    with _print_lock:
        print(msg)
        sys.stdout.flush()


# ---------- 素材装载 ----------

def load_anchor_ctx():
    ctx = {}
    cards_raw = {c["case_id"]: c for c in
                 json.load(open(ANCHOR_CARDS, encoding="utf-8"))["cards"]}
    for sid in SIDS:
        ep = json.load(open(os.path.join(ANCHOR_DIR, sid, "episode.json"), encoding="utf-8"))
        t = ep["turns"][0]
        ctx[sid] = {"persona": ep["persona"], "kb": ep["initial_kb"],
                    "rows": [{"idx": 1, "cid": t["case_id"], "msgs": t["input"]["messages"],
                              "prior": [], "intent": t["input"].get("intent"),
                              "may_decline": t["input"].get("may_decline", False)}],
                    "card": cards_raw[t["case_id"]]}
    return ctx


def neg_reply(cand, sid):
    p = os.path.join(NEG_ROOT, "%s_%s" % (cand, sid), "turn_01_%s_T01" % sid, "reply_raw.txt")
    raw = open(p, encoding="utf-8").read().strip()
    try:
        d = json.loads(raw)
        rec = d.get("response", {}).get("recommended")
        return rec if isinstance(rec, str) and rec.strip() else raw
    except Exception:
        return raw


def load_w19():
    rev.STORIES = W19_STORIES
    rev.CARDS = W19_CARDS
    ctx = rev.load_context()
    cards = rev.load_cards()
    stored = {}
    rev_json = json.load(open(os.path.join(REFS, "LP_W19CROSS_NINEDIM_REVIEW.json"),
                              encoding="utf-8"))
    for v in rev_json["verdicts"]:
        stored[v["pair_id"]] = v
    return ctx, cards, stored


def w19_reply(sid, idx, cid, arm):
    broot = os.path.join(ROOT, "logs", W19_ROOTS[sid])
    return rev.reply_of(broot, arm, sid, idx, cid)


# ---------- 调用 ----------

def judge_call(pair_id, user, budget):
    """一次判定（含 1 次有界重试）。返回 (verdict, attempts)。"""
    for attempt_i in range(2):
        attempt_id = "w20cal:%s%s" % (pair_id, "" if attempt_i == 0 else ":r1")
        try:
            r = rev.call(user, budget, attempt_id)
        except BudgetRefused:
            return {"winner": "BUDGET_REFUSED"}, attempt_i + 1
        if r.get("ok"):
            v = rev.parse_verdict(r["text"])
            if v.get("winner") not in ("UNPARSEABLE", "CALL_FAIL"):
                v["_raw"] = r["text"]
                v["_usage"] = r.get("usage", {})
                return v, attempt_i + 1
            last = {"winner": v.get("winner"), "_raw": r.get("text", "")}
        else:
            last = {"winner": "CALL_FAIL", "_raw": r.get("error", "")}
    return last, 2


def main():
    auth_doc = json.load(open(os.path.join(REFS, "AUTHORIZATION_W20_JUDGECAL.json"),
                              encoding="utf-8"))
    budget = AuthorizationBudget(
        os.path.join(ROOT, "logs", "auth_budget", AID + ".jsonl"),
        AID, CAP, status="ACTIVE", scope=auth_doc["scope"])
    budget.ensure_batch(102, purpose="w20_03_judge_cal")
    print("budget:", budget.summary())

    acts = {}
    w19_ctx, w19_cards, w19_stored = load_w19()
    actx = load_anchor_ctx()

    # ---- 任务清单（先冻结全部 user prompt，再并发派发）----
    tasks = []  # (pair_id, kind, order, left, right, user)

    # A) 锚点构造对 × 双序
    for sid in SIDS:
        row = actx[sid]["rows"][0]
        card = actx[sid]["card"]
        ctxd = {"persona": actx[sid]["persona"], "kb": actx[sid]["kb"]}
        good = GOOD[sid]["text"]
        for neg in NEGS:
            bad = neg_reply(neg, sid)
            pid = "W20CAL:%s:%s" % (sid, neg)
            for order in ("A_good_left", "B_good_right"):
                left, right = (good, bad) if order == "A_good_left" else (bad, good)
                user = rev.build_user(sid, row, card, left, right, ctxd)
                tasks.append((pid, "constructed", order, left, right, user))

    # B) 历史复读 × 双序
    for key in W19_SAMPLE:
        sid, cid = key.split(":")
        rows = [r for r in w19_ctx[sid]["rows"] if r["cid"] == cid]
        if not rows:
            raise SystemExit("历史位缺行: %s" % key)
        row = rows[0]
        card = w19_cards.get(cid, {})
        cand = w19_reply(sid, row["idx"], cid, "candidate")
        par = w19_reply(sid, row["idx"], cid, "parent")
        pid = "W20RE:%s:%s" % (sid, cid)
        for order in ("A_cand_left", "B_cand_right"):
            left, right = (cand, par) if order == "A_cand_left" else (par, cand)
            user = rev.build_user(sid, row, card, left, right, w19_ctx[sid])
            tasks.append((pid, "historical", order, left, right, user))

    # C) 同文 sanity
    for sid in SIDS[:6]:
        row = actx[sid]["rows"][0]
        card = actx[sid]["card"]
        ctxd = {"persona": actx[sid]["persona"], "kb": actx[sid]["kb"]}
        g = GOOD[sid]["text"]
        pid = "W20SAN:%s:self" % sid
        tasks.append((pid, "sanity", "A_same", g, g,
                      rev.build_user(sid, row, card, g, g, ctxd)))

    n_planned = len(tasks)
    log("planned tasks: %d (A=72 B=24 C=6 expected)" % n_planned)
    assert n_planned == 102, n_planned

    done = {}

    def work(t):
        pid, kind, order, left, right, user = t
        v, n_att = judge_call("%s:%s" % (pid, order[:1]), user, budget)
        rec = {"pair_id": pid, "kind": kind, "order": order,
               "attempts": n_att, "verdict": v,
               "left": left, "right": right}
        log("%-42s %s -> %s (att=%d)" % (pid, order, v.get("winner"), n_att))
        return rec

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as ex:
        for rec in ex.map(work, tasks):
            done[(rec["pair_id"], rec["order"])] = rec
            with open(OUT_RAW, "a", encoding="utf-8") as fh:
                fh.write(json.dumps(rec, ensure_ascii=False) + "\n")

    out = {"kind": "w20_03_judge_cal_raw", "date": "2026-11-03",
           "authorization": AID,
           "n_planned": n_planned,
           "n_completed": len(done),
           "budget": budget.summary(),
           "records": [done[k] for k in sorted(done, key=str)]}
    json.dump(out, open(os.path.join(REFS, "W20_03_JUDGE_CAL_RAW.json"), "w",
                        encoding="utf-8"), ensure_ascii=False, indent=1)
    print("raw saved; budget:", budget.summary())


if __name__ == "__main__":
    main()
