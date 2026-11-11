#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Judge 多数投票离线模拟（判决令 2026-10-18 §一.3）。

用现有 logs/phase1a 日志离线模拟两种投票协议的一致性收益，零新模型调用：
  A. 三票多数（fixed_t0 / fixed_t07 各 3 次同序重复）
  B. 五位置票（rot0-4，每选项恰历 5 位置各 1 票）
只回答"投票值不值得额外调用"，不宣布协议定稿（判决令：不直接批准多数投票必须采用）。
"""
import json, os, itertools, sys

L = "logs/phase1a"
CIRC = "①②③④⑤"
OUT = os.path.join(L, "vote_sim.json")


def load(name):
    recs = json.load(open(os.path.join(L, "judge_2x2_%s.json" % name),
                          encoding="utf-8"))["records"]
    # rep -> {qid: rec}
    by_rep = {}
    for r in recs:
        by_rep.setdefault(r["rep"], {})[r["qid"]] = r
    return by_rep


def option_verdicts(by_rep):
    """{(qid,opt): [每次评审 verdict('k'杀/'kpt'留')]}，只覆盖有标签的选项。"""
    votes = {}
    qids = sorted(by_rep[0].keys())
    for qid in qids:
        r0 = by_rep[0][qid]
        labeled = [(s, "trap" if s in r0["exp_trap"] else "ok")
                   for s in CIRC if s in r0["exp_trap"] or s in r0["exp_ok"]]
        for s, kind in labeled:
            vs = []
            for rep in sorted(by_rep):
                d = by_rep[rep][qid]
                vs.append("k" if s in d["judge_bad"] else "kpt")
            votes[(qid, s, kind)] = vs
    return votes


def flip_stats(votes):
    n = sum(1 for vs in votes.values() if len(set(vs)) > 1)
    return n, len(votes)


def majority(votes, need, total):
    """逐选项多数票聚合 → (合格保留率, 陷阱识别率, 逐项明细)"""
    ok_k = ok_t = tr_k = tr_t = 0
    detail = []
    for (qid, s, kind), vs in votes.items():
        k = vs.count("k")
        killed = k >= need
        detail.append({"qid": qid, "opt": s, "kind": kind, "killed": k,
                       "agg_killed": killed})
        if kind == "ok":
            ok_t += 1
            ok_k += killed
        else:
            tr_t += 1
            tr_k += killed
    return ok_k, ok_t, tr_k, tr_t, detail


def disagree_with_majority(votes, agg):
    """多数票结果与单次评审结果的分歧选项数（按次）。"""
    amap = {(d["qid"], d["opt"]): d["agg_killed"] for d in agg}
    per_rep = {}
    reps = sorted({i for vs in votes.values() for i, _ in enumerate(vs)}) or [0]
    n_reps = max(len(vs) for vs in votes.values())
    out = []
    for i in range(n_reps):
        dis = sum(1 for (qid, s, kind), vs in votes.items()
                  if (vs[i] == "k") != amap[(qid, s)])
        out.append(dis)
    return out


def main():
    res = {}
    for name in ("fixed_t0", "fixed_t07"):
        by_rep = load(name)
        votes = option_verdicts(by_rep)
        fl, tot = flip_stats(votes)
        ok_k, ok_t, tr_k, tr_t, agg = majority(votes, need=2, total=3)
        dis = disagree_with_majority(votes, agg)
        # 单次评审保留率（每次）
        n_reps = max(len(vs) for vs in votes.values())
        singles = []
        for i in range(n_reps):
            okk = sum(1 for (q, s, kind), vs in votes.items()
                      if kind == "ok" and vs[i] == "kpt")
            okt = sum(1 for (q, s, kind), vs in votes.items() if kind == "ok")
            singles.append("%.1f%%" % (100 * okk / okt))
        res[name] = {
            "n_reps": n_reps, "flip_options": "%d/%d" % (fl, tot),
            "single_retention": singles,
            "majority3_retention": "%.1f%%" % (100 * (ok_t - ok_k) / ok_t),
            "majority3_trap": "%d/%d" % (tr_k, tr_t),
            "majority_vs_single_disagree_options": dis,
        }
    # 五位置票（temp0，rot0-4，每选项每位置恰 1 票 = 5 票）
    rots = [load("rot%d" % k) for k in range(5)]
    votes5 = {}
    for qid in sorted(rots[0][0].keys()):
        r0 = rots[0][0][qid]
        for s in CIRC:
            kind = "trap" if s in r0["exp_trap"] else \
                   "ok" if s in r0["exp_ok"] else None
            if not kind:
                continue
            vs = []
            for k in range(5):
                d = rots[k][0][qid]
                # 该轮该选项所处位置：perm[p]=原始index（0-based）→ 原始圈号 s
                perm = d["perm"]
                vs.append("k" if s in d["judge_bad"] else "kpt")
            votes5[(qid, s, kind)] = vs
    fl5, tot5 = flip_stats(votes5)
    ok_k, ok_t, tr_k, tr_t, agg5 = majority(votes5, need=3, total=5)
    singles5 = []
    for i in range(5):
        okk = sum(1 for (q, s, kind), vs in votes5.items()
                  if kind == "ok" and vs[i] == "kpt")
        okt = sum(1 for (q, s, kind), vs in votes5.items() if kind == "ok")
        singles5.append("%.1f%%" % (100 * okk / okt))
    dis5 = []
    amap5 = {(d["qid"], d["opt"]): d["agg_killed"] for d in agg5}
    for i in range(5):
        dis5.append(sum(1 for (qid, s, kind), vs in votes5.items()
                        if (vs[i] == "k") != amap5[(qid, s)]))
    res["rot5_position_vote"] = {
        "n_votes": 5, "flip_options": "%d/%d" % (fl5, tot5),
        "single_retention": singles5,
        "majority5_retention": "%.1f%%" % (100 * (ok_t - ok_k) / ok_t),
        "majority5_trap": "%d/%d" % (tr_k, tr_t),
        "majority_vs_single_disagree_options": dis5,
    }
    json.dump(res, open(OUT, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    for k, v in res.items():
        print("==", k)
        for kk, vv in v.items():
            print("  %s: %s" % (kk, vv))


if __name__ == "__main__":
    main()
