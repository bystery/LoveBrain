# -*- coding: utf-8 -*-
"""tools/run_fs1_semantic_review.py — W04-03 FS1 九维全量评审（预注册 §B）。

样本：FS1-A（E3 vs B0）48 位 + FS1-B（Bmin vs B0）48 位，首选槽全审；
双序子集 16 位（每故事 T05/T09）；备选风险：卡面禁断事实关键词初筛 + 命中送判（≤8）。
预算：AUTH-FS1-JUDGE-20261030-02，cap 120（112 计划 + 8 备选裁决储备）。
量尺：W04-01_RUBRIC_ANCHORS_V1 九维 0–3；输出 W04-03_FS1_NINEDIM_REVIEW.json。
盲序：LEFT/RIGHT 对半互换归一；温度 0.0 非流式；零重试。
"""
import json
import os
import re
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
from isolation.authorization_budget import AuthorizationBudget  # noqa: E402

AID = "AUTH-FS1-JUDGE-20261030-02"
LEDGER = os.path.join(ROOT, "logs", "auth_budget", AID + ".jsonl")
ENDPOINT = "http://127.0.0.1:17770/v1/chat/completions"
MODEL = "dsf"
TEMP = 0.0
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                   "W04-03_FS1_NINEDIM_REVIEW.json")
STORIES = ["E021", "E026", "E032", "E033"]
BATCHES = {"A": ("E3", os.path.join(ROOT, "logs", "fs1_a_live2_20261030")),
           "B": ("Bmin", os.path.join(ROOT, "logs", "fs1_b_live2_20261030"))}
SWAP_SUBSET = {"T05", "T09"}  # 每故事 2 位双序
CARDS = os.path.join(ROOT, "benchmark", "stories_v2", "stage2",
                     "GRADING_CARDS_E021_E026_E032_E033.json")

SYS = """你是聊天回复质量评审。同一轮的两个匿名回复（LEFT/RIGHT），按九维锚点各打0-3分并判严重错误。
九维：goal目标完成/fact事实/attrib人物归属/time时间与事项状态/boundary边界/memory记忆使用/interact互动承接/init主动性/emotion情绪关系。
分档：3=实质增益且有具体下一步；2=合理完成；1=关键不足（表面承接/含糊/该回扣没回扣）；0=反向或严重错。
全局纪律：未来提议/询问不是事实断言；输入明示"未定/暂定/可能改"的事项被当确定复述=无据确定化（严重）；严重错误=错人/编造已发生安排/泄漏明确禁止的私有内容/完成·取消事项复活/无据确定化，任一出现该侧severe=true且相关维度=0；维度不适用填null（不硬凑）；不得凭长度或提问数量加分；不确定性该标就标。
只输出一个JSON对象，不要多余文本：
{"L":{"goal":0,"fact":0,"attrib":0,"time":0,"boundary":0,"memory":0,"interact":0,"init":0,"emotion":0},"R":{同九键},"severe":{"L":false,"R":false,"note":""},"winner":"LEFT|RIGHT|TIE|BOTH_BAD|INSUFFICIENT","key_quote":"支撑引用<=60字"}"""

DIMS = ["goal", "fact", "attrib", "time", "boundary", "memory", "interact", "init", "emotion"]


def call(user, budget, aid):
    budget.reserve(aid, purpose="fs1_ninedim_review")
    body = json.dumps({"model": MODEL, "messages": [{"role": "system", "content": SYS},
                                                    {"role": "user", "content": user}],
                       "temperature": TEMP, "stream": False, "max_tokens": 600}).encode("utf-8")
    req = urllib.request.Request(ENDPOINT, data=body,
                                 headers={"Content-Type": "application/json",
                                          "Authorization": "Bearer local"})
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=240) as r:
            d = json.loads(r.read().decode("utf-8", "replace"))
            text = (d.get("choices") or [{}])[0].get("message", {}).get("content", "")
            usage = d.get("usage") or {}
        budget.record("attempt_completed", aid, latency_ms=int((time.time() - t0) * 1000))
        return {"ok": True, "text": text, "usage": usage}
    except Exception as e:  # noqa: BLE001
        try:
            detail = e.read().decode("utf-8", "replace")[:300]
        except Exception:  # noqa: BLE001
            detail = str(e)
        budget.record("attempt_failed", aid, error=detail)
        return {"ok": False, "error": detail}


def parse_verdict(text):
    try:
        s = text[text.index("{"):text.rindex("}") + 1]
        d = json.loads(s)
        return d
    except Exception:  # noqa: BLE001
        return {"winner": "UNPARSEABLE", "raw": text[:80]}


def load_context():
    """{sid: [(turn_idx, msgs, prior_msgs, persona, kb)]} 从冻结题面构建。"""
    out = {}
    for sid in STORIES:
        ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", sid + ".json"),
                            encoding="utf-8"))
        seen = []
        rows = []
        for i, t in enumerate(ep["turns"]):
            rows.append({"idx": i + 1, "cid": t["case_id"],
                         "msgs": t["input"]["messages"], "prior": list(seen),
                         "intent": t["input"].get("intent"),
                         "may_decline": t["input"].get("may_decline", False)})
            seen.extend(t["input"]["messages"])
        out[sid] = {"persona": ep["persona"], "kb": ep["initial_kb"], "rows": rows}
    return out


def load_cards():
    cards = json.load(open(CARDS, encoding="utf-8"))
    arr = cards.get("cards") or cards
    return {c["case_id"]: c for c in arr}


def reply_of(batch_root, arm, sid, idx, cid):
    p = os.path.join(batch_root, "%s_%s" % (arm, sid), "out", "turn_%02d_%s" % (idx, cid),
                     "reply_raw.txt")
    raw = open(p, encoding="utf-8").read().strip()
    try:
        return json.loads(raw)["response"]["recommended"]
    except Exception:  # noqa: BLE001
        return raw


def backups_of(batch_root, arm, sid, idx, cid):
    """全部非空备选槽文本。"""
    p = os.path.join(batch_root, "%s_%s" % (arm, sid), "out", "turn_%02d_%s" % (idx, cid),
                     "reply_raw.txt")
    raw = open(p, encoding="utf-8").read().strip()
    out = []
    try:
        d = json.loads(raw)
    except Exception:  # noqa: BLE001
        return out
    resp = d.get("response") or {}
    for k, v in resp.items():
        if k != "recommended" and isinstance(v, str) and v.strip():
            out.append({"slot": "response." + k, "text": v})
    for i, v in enumerate(d.get("directions") or []):
        if isinstance(v, str) and v.strip():
            out.append({"slot": "directions[%d]" % i, "text": v})
    return out


def build_user(sid, row, card, left, right, ctxd):
    per, kb = ctxd["persona"], ctxd["kb"]
    prior = "\n".join("%s: %s" % (m["role"], m["text"]) for m in row["prior"][-8:])
    cur = "\n".join("%s: %s" % (m["role"], m["text"]) for m in row["msgs"])
    parts = ["【人设】我：%s\n她：%s" % (per.get("me", ""), per.get("her", "")),
             "【已知背景】" + json.dumps(kb, ensure_ascii=False)[:400]]
    if prior:
        parts.append("【此前对话】\n" + prior)
    parts.append("【本轮消息】\n" + cur)
    parts.append("【卡面】本位要求:%s\n可接受动作:%s\n未知项:%s" % (
        card.get("current_action", ""), json.dumps(card.get("acceptable_actions", []),
                                                   ensure_ascii=False)[:200],
        json.dumps(card.get("unknowns", []), ensure_ascii=False)[:120]))
    if row.get("intent"):
        parts.append("【本轮意图】" + json.dumps(row["intent"], ensure_ascii=False)[:120])
    parts.append("【回复 LEFT】\n%s\n\n【回复 RIGHT】\n%s" % (left, right))
    parts.append("只输出JSON：")
    return "\n\n".join(parts)


def prescreen_backups(card, bks):
    """卡面禁断事实 → 显著词初筛（数字/4字以上片段）。返回命中的备选。"""
    claims = card.get("forbidden_factual_claims") or []
    if not isinstance(claims, list):
        claims = [claims]
    hits = []
    for c in claims:
        ct = c if isinstance(c, str) else json.dumps(c, ensure_ascii=False)
        toks = set(re.findall(r"\d+(?:\.\d+)?|[一-龥]{4,}", ct))
        toks = {t for t in toks if t not in ("什么", "自己", "没有", "不能", "不要", "如何")}
        if not toks:
            continue
        for b in bks:
            hit = [t for t in toks if t in b["text"]]
            if hit:
                hits.append({"backup": b, "claim": ct[:120], "hit_tokens": hit[:6]})
    return hits


def main():
    scope = {"scope_id": "fs1_ninedim_full_review_v1",
             "purpose": "W04-03 FS1 nine-dim review per prereg section B: 96 primary-slot blind pairs + 16 swap subset + <=8 backup adjudication; dsf temp0.0 non-stream; fail-fast no retry",
             "automatic_candidate_generations": False,
             "endpoint": "http://127.0.0.1:17770/v1", "model_alias": MODEL}
    budget = AuthorizationBudget(LEDGER, AID, 120, status="ACTIVE", scope=scope)
    budget.ensure_batch(112, purpose="fs1_ninedim_primary_plus_swap")
    print("budget:", budget.summary())

    ctxd = load_context()
    cards = load_cards()
    verdicts, seq = [], 0

    def judge(pair_id, user):
        nonlocal seq
        seq += 1
        aid = "nind:%s" % pair_id
        r = call(user, budget, aid)
        v = parse_verdict(r["text"]) if r.get("ok") else {"winner": "CALL_FAIL", "raw": r.get("error", "")}
        print("%3d %s -> %s" % (seq, pair_id, v.get("winner")))
        return v

    # 1) 主审：96 位。LEFT=candidate, RIGHT=parent（judge 内互换由 SWAP 决定）
    for bname, (cand_name, broot) in BATCHES.items():
        for sid in STORIES:
            for row in ctxd[sid]["rows"]:
                cid = row["cid"]
                card = cards.get(cid, {})
                cand = reply_of(broot, "candidate", sid, row["idx"], cid)
                par = reply_of(broot, "parent", sid, row["idx"], cid)
                swap = cid[-3:] in SWAP_SUBSET
                left, right = (par, cand) if swap else (cand, par)
                v = judge("NIND:%s:%s:%s" % (bname, sid, cid),
                          build_user(sid, row, card, left, right, ctxd[sid]))
                # 归一：L/R → cand/parent
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
                        dims_n[arm] = {d: (v.get(k) or {}).get(d) for d in DIMS}
                sev = v.get("severe") or {}
                verdicts.append({
                    "pair_id": "NIND:%s:%s:%s" % (bname, sid, cid), "batch": bname,
                    "candidate_arm": cand_name, "swap": swap,
                    "winner_cand_view": (norm(v.get("winner")) if v.get("winner") in (
                        "LEFT", "RIGHT") else v.get("winner")),
                    "dims": dims_n,
                    "severe": {"candidate": sev.get("L" if not swap else "R"),
                               "parent": sev.get("R" if not swap else "L"),
                               "note": sev.get("note", "")},
                    "key_quote": v.get("key_quote", ""), "raw": v.get("raw", "")})

    # 2) 备选风险初筛 + 命中裁决（≤8 判）
    bk_flagged = []
    for bname, (cand_name, broot) in BATCHES.items():
        for sid in STORIES:
            for row in ctxd[sid]["rows"]:
                cid = row["idx"]
                card = cards.get(row["cid"], {})
                for arm, arm_name in (("candidate", cand_name), ("parent", "B0")):
                    bks = backups_of(broot, arm, sid, row["idx"], row["cid"])
                    for h in prescreen_backups(card, bks):
                        bk_flagged.append({"pair_id": "NIND:%s:%s" % (bname, row["cid"]),
                                           "arm": arm_name, **h})
    adjudicated = []
    for h in bk_flagged[:8]:
        aid = "nindbk:%s:%s:%s" % (h["pair_id"], h["arm"], h["backup"]["slot"])
        user = ("判断以下备选回复是否包含严重错误（错人/编造已发生安排/泄漏禁止私有内容/"
                "完成·取消事项复活/把未知当确定的关键事实）。\n卡面禁断事实:%s\n回复:%s\n"
                "只输出JSON:{\"severe\":true/false,\"reason\":\"一句话\"}" % (
                    h["claim"], h["backup"]["text"]))
        v = call(user, budget, aid)
        d = parse_verdict(v["text"]) if v.get("ok") else {"severe": None}
        adjudicated.append({k: h.get(k) for k in ("pair_id", "arm", "backup", "claim", "hit_tokens")}
                           | {"judge": d})
    n_bk = sum(1 for a in adjudicated if a["judge"].get("severe") is True)

    # 3) 聚合
    def dim_stats(batch, arm):
        rows = [v for v in verdicts if v["batch"] == batch and v["winner_cand_view"] not in
                ("UNPARSEABLE", "CALL_FAIL")]
        out = {}
        for d in DIMS:
            diffs, cand_vals, par_vals = [], [], []
            for v in rows:
                c, p = v["dims"]["candidate"].get(d), v["dims"]["parent"].get(d)
                if c is None or p is None:
                    continue
                cand_vals.append(c)
                par_vals.append(p)
                diffs.append(c - p)
            n = len(diffs)
            out[d] = {"n": n,
                      "cand_mean": round(sum(cand_vals) / n, 3) if n else None,
                      "parent_mean": round(sum(par_vals) / n, 3) if n else None,
                      "paired_diff_mean": round(sum(diffs) / n, 3) if n else None,
                      "cand_wins": sum(1 for x in diffs if x > 0),
                      "parent_wins": sum(1 for x in diffs if x < 0)}
        return out

    summary = {}
    for bname, (cand_name, _r) in BATCHES.items():
        sev_c = sum(1 for v in verdicts if v["batch"] == bname and v["severe"]["candidate"])
        sev_p = sum(1 for v in verdicts if v["batch"] == bname and v["severe"]["parent"])
        summary[bname] = {"candidate": cand_name, "dims": dim_stats(bname, cand_name),
                          "severe_primary_candidate": sev_c, "severe_primary_parent": sev_p,
                          "n_positions": sum(1 for v in verdicts if v["batch"] == bname)}
    report = {"kind": "w04_03_fs1_ninedim_review", "date": "2026-10-30",
              "authorization": AID, "anchors": "W04-01_RUBRIC_ANCHORS_V1",
              "n_primary": sum(1 for v in verdicts),
              "swap_subset": sorted(SWAP_SUBSET),
              "backup_prescreen_flagged": len(bk_flagged),
              "backup_adjudicated_severe": n_bk,
              "summary": summary, "verdicts": verdicts,
              "note": "故事级=探索性读数（rubric §3.4 统计纪律）；E3 单维显著退化即阻断（W10-01 保护项）；Bmin 允许 C2 维如实失败"}
    json.dump(report, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(OUT, encoding="utf-8"))
    for b, s in summary.items():
        print(b, s["candidate"], "sev_cand", s["severe_primary_candidate"],
              "sev_par", s["severe_primary_parent"])
        for d, st in s["dims"].items():
            print("  %-9s n=%s diff=%s cand=%s par=%s" % (
                d, st["n"], st["paired_diff_mean"], st["cand_mean"], st["parent_mean"]))
    print("report:", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
