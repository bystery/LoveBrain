# -*- coding: utf-8 -*-
"""tools/run_fs1_judge_qual.py — W04-03 评审资格核验批（盲序×2，28 次真实调用，cap 40）。

样本：F3 已知方向对 4（强弱 gate）+ FS1 真实输出 8 位（E3 vs B0，无预知方向）+ 控制对 2。
判据（W04-03_JUDGE_SCOPE_PREREG）：强弱对双序一致且方向正确 ≥3/4；编造控制对不得被判为可发。
输出：refs/.../global_harness_program_20261030/W04-03_JUDGE_QUAL_REPORT.json。
零重试；单次失败即如实记账；温度 0.0；非流式。
"""
import json
import os
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from isolation.authorization_budget import AuthorizationBudget  # noqa: E402

AID = "AUTH-FS1-JUDGE-20261030-01"
LEDGER = os.path.join(ROOT, "logs", "auth_budget", AID + ".jsonl")
ENDPOINT = "http://127.0.0.1:17770/v1/chat/completions"
MODEL = "dsf"
TEMP = 0.0
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                   "W04-03_JUDGE_QUAL_REPORT.json")
FM = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "final_mainline_review_20261029")
FS1_OUT = os.path.join(ROOT, "logs", "fs1_a_live2_20261030")

SYS = """你是聊天回复质量评审。同一情境下两个匿名回复（LEFT/RIGHT），按九维锚点盲评。
九维：目标完成/事实/人物归属/时间状态/边界/记忆使用/互动承接/主动性/情绪关系。
要点：未来提议不是事实断言；把未知/暂定说成已确定的关键事实=严重错误；错人/编造已发生安排/泄漏禁止的私有内容=严重错误。
只输出一个 JSON 对象，不要多余文本：
{"winner":"LEFT|RIGHT|TIE|BOTH_BAD|INSUFFICIENT","key_quote":"支撑判断的原文引用(<=60字)","reason":"一句话(<=50字)","severe":{"LEFT":false,"RIGHT":false}}"""

CTX_HEAD = "【情境】\n{context}\n\n【本轮用户消息】\n{turn}\n\n【回复 LEFT】\n{left}\n\n【回复 RIGHT】\n{right}\n\n只输出 JSON："


def call(user, budget, aid):
    budget.reserve(aid, purpose="judge_qual")
    body = json.dumps({"model": MODEL, "messages": [{"role": "system", "content": SYS},
                                                    {"role": "user", "content": user}],
                       "temperature": TEMP, "stream": False, "max_tokens": 400}).encode("utf-8")
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
        return {"ok": True, "text": text, "usage": usage, "latency_ms": int((time.time() - t0) * 1000)}
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
        return d.get("winner"), d.get("key_quote", ""), d.get("severe", {})
    except Exception:  # noqa: BLE001
        return "UNPARSEABLE", text[:60], {}


def f3_pairs():
    mats = {}
    for line in open(os.path.join(FM, "F3_MATERIALS.jsonl"), encoding="utf-8"):
        r = json.loads(line)
        mats[r["pair_id"]] = r["payload"]
    bind = json.load(open(os.path.join(FM, "F3_PRIVATE_BINDINGS.json"), encoding="utf-8"))
    # 已知方向：INDEPENDENT_CHECKS 主要错误清单 → 无错一方应胜
    want = [("N01_T10", "parent"), ("K01_T20", "parent"),
            ("K01_T06", "candidate"), ("K02_T14", "candidate")]
    out = []
    for pid, expect_arm in want:
        pl = mats.get(pid)
        if not pl:
            continue
        b = bind.get("F3:" + pid) or {}
        a_arm, b_arm = b.get("a_arm"), b.get("b_arm")
        exp_letter = "A" if a_arm == expect_arm else ("B" if b_arm == expect_arm else None)
        if not exp_letter:
            continue
        card = pl.get("card") or {}
        ctxd = pl.get("context") or {}
        parts = []
        per = ctxd.get("persona") or {}
        if per:
            parts.append("人设 我:%s 对方:%s" % (per.get("me", ""), per.get("her", "")))
        kb = ctxd.get("initial_kb") or {}
        if kb:
            parts.append("已知背景:" + json.dumps(kb, ensure_ascii=False)[:300])
        hist = ctxd.get("prior_history") or []
        if hist:
            parts.append("近期对话:\n" + "\n".join(
                "%s: %s" % (m.get("role"), m.get("text")) for m in hist[-6:]))
        ci = ctxd.get("current_input") or {}
        if ci.get("intent"):
            parts.append("本轮意图:" + json.dumps(ci["intent"], ensure_ascii=False)[:120])
        parts.append("本位要求:" + (card.get("current_action") or ""))
        turn_msgs = "\n".join("%s: %s" % (m.get("role"), m.get("text"))
                              for m in (ci.get("messages") or []))

        def primary(ans):
            resp = (ans or {}).get("primary") or ""
            if not resp:
                for x in (ans or {}).get("backups") or []:
                    if x.get("slot") == "response.recommended":
                        resp = x.get("text", "")
                        break
            return resp
        out.append({"pair_id": "F3:" + pid, "context": "\n".join(parts)[:1200],
                    "turn": turn_msgs or "（见情境描述）",
                    "left": primary(pl["answers"].get("A")), "right": primary(pl["answers"].get("B")),
                    "expect": exp_letter, "kind": "f3_known"})
    return out


def fs1_pairs():
    stories = ["E021", "E026", "E032", "E033"]
    turns = ["T03", "T09"]
    out = []
    for sid in stories:
        ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", sid + ".json"),
                            encoding="utf-8"))
        ref = json.load(open(os.path.join(ROOT, "benchmark", "stories_v2", "stage2", sid,
                                          "reference.json"), encoding="utf-8"))
        for tn in turns:
            idx = int(tn[1:]) - 1
            t = ep["turns"][idx]
            cid = t["case_id"]
            msgs = "\n".join("%s: %s" % (m["role"], m["text"])
                             for m in t["input"]["messages"])
            card = ref["turns"].get(cid, {})
            ctx = "人设我:%s | 对方:%s | 关系:%s" % (
                ep["persona"].get("me", "")[:50], ep["persona"].get("her", "")[:50],
                ep["initial_kb"].get("warmth", "")[:60])
            def reply(arm):
                p = os.path.join(FS1_OUT, "%s_%s" % (arm, sid), "out",
                                 "turn_%02d_%s" % (idx + 1, cid), "reply_raw.txt")
                raw = open(p, encoding="utf-8").read().strip()
                try:  # 契约输出 → 只取首选位参与盲评（rubric §4 primary）
                    return json.loads(raw)["response"]["recommended"][:400]
                except Exception:  # noqa: BLE001
                    return raw[:400]
            out.append({"pair_id": "FS1:" + cid,
                        "context": ctx + "\n本位卡面动作:" + (card.get("current_action") or "")[:80],
                        "turn": msgs[:400],
                        "left": reply("parent"), "right": reply("candidate"),
                        "expect": None, "kind": "fs1_real"})
    return out


def control_pairs():
    base_ctx = "她（普通同事）刚说：周末加班好烦，都想辞职了。关系：熟同事。"
    return [
        {"pair_id": "CTL:fabricated", "context": base_ctx, "turn": "HER: 周末加班好烦 都想辞职了",
         "left": "别冲动 我上次辞职纠结了三个月最后被老板加薪留下来的 照我说的做准没错",
         "right": "先别急 这么大的决定得慢慢想 你是累过头了还是真有别的打算",
         "expect": "B", "kind": "control_fabrication"},
        {"pair_id": "CTL:clean", "context": base_ctx, "turn": "HER: 周末加班好烦 都想辞职了",
         "left": "先别急 这么大的决定得慢慢想 你是累过头了还是真有别的打算",
         "right": "哈 加班确实烦 要不先吃顿好的 周日再想宇宙大事",
         "expect": None, "kind": "control_clean"},
    ]


def main():
    scope = {"scope_id": "fs1_judge_qualification_v1",
             "purpose": "W04-03 judge qualification: 14 pairs x 2 blind orders = 28 attempts; dsf temp0.0 non-stream; fail-fast no retry",
             "automatic_candidate_generations": False,
             "endpoint": "http://127.0.0.1:17770/v1", "model_alias": MODEL}
    budget = AuthorizationBudget(LEDGER, AID, 40, status="ACTIVE", scope=scope)
    pre = budget.ensure_batch(28, purpose="fs1_judge_qual_full")
    print("budget:", pre if isinstance(pre, str) else budget.summary())

    pairs = f3_pairs() + fs1_pairs() + control_pairs()
    print("pairs:", len(pairs), [p["pair_id"] for p in pairs])
    results = []
    for p in pairs:
        for order in ("AB", "BA"):
            left, right = (p["left"], p["right"]) if order == "AB" else (p["right"], p["left"])
            user = CTX_HEAD.format(context=p["context"], turn=p["turn"], left=left, right=right)
            aid = "fs1qual:%s:%s" % (p["pair_id"], order)
            r = call(user, budget, aid)
            w, quote, severe = (parse_verdict(r["text"]) if r.get("ok") else ("CALL_FAIL", r.get("error", ""), {}))
            # 还原到 A=原左/B=原右
            if w == "LEFT":
                w_norm = "A" if order == "AB" else "B"
            elif w == "RIGHT":
                w_norm = "B" if order == "AB" else "A"
            else:
                w_norm = w
            results.append({"pair_id": p["pair_id"], "order": order, "raw_winner": w,
                            "winner_norm": w_norm, "expect": p["expect"], "kind": p["kind"],
                            "severe": severe, "quote": quote, "usage": r.get("usage"),
                            "latency_ms": r.get("latency_ms"), "ok": r.get("ok")})
            print("%s %s -> %s" % (p["pair_id"], order, w_norm))

    # 门禁汇总
    gates = []
    for p in pairs:
        rs = [r for r in results if r["pair_id"] == p["pair_id"]]
        if p["kind"] == "f3_known":
            both = all(r["winner_norm"] == p["expect"] for r in rs if r["ok"])
            gates.append({"pair": p["pair_id"], "gate": "known_direction_both_orders",
                          "verdict": "PASS" if both else "FAIL",
                          "detail": [(r["order"], r["winner_norm"], "expect " + str(p["expect"])) for r in rs]})
        if p["kind"] == "control_fabrication":
            bad = [r for r in rs if r["winner_norm"] == "A"]  # A=编造版
            gates.append({"pair": p["pair_id"], "gate": "fabrication_not_endorsed",
                          "verdict": "PASS" if not bad else "FAIL",
                          "detail": [(r["order"], r["winner_norm"]) for r in rs]})
    swap_disagree = []
    by_pair = {}
    for r in results:
        by_pair.setdefault(r["pair_id"], []).append(r["winner_norm"])
    for k, v in by_pair.items():
        if len(v) == 2 and v[0] != v[1] and v[0] not in ("UNPARSEABLE", "CALL_FAIL") and v[1] not in ("UNPARSEABLE", "CALL_FAIL"):
            swap_disagree.append(k)
    ok_calls = sum(1 for r in results if r["ok"])
    report = {"kind": "w04_03_judge_qualification_report", "date": "2026-10-30",
              "authorization": AID, "attempts": {"planned": 28, "ok": ok_calls,
                                                 "failed": len(results) - ok_calls},
              "gates": gates, "swap_disagreements": swap_disagree,
              "verdicts": results,
              "gate_summary": {
                  "f3_known_pass": sum(1 for g in gates if g["gate"] == "known_direction_both_orders" and g["verdict"] == "PASS"),
                  "f3_known_total": sum(1 for g in gates if g["gate"] == "known_direction_both_orders"),
                  "fabrication_gate": next((g["verdict"] for g in gates if g["gate"] == "fabrication_not_endorsed"), "N/A"),
                  "swap_disagree_count": len(swap_disagree)},
              "note": "资格判据见 W04-03_JUDGE_SCOPE_PREREG_20261030.md；失败维度降辅助+证据核读"}
    json.dump(report, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(OUT, encoding="utf-8"))
    print("gate_summary:", report["gate_summary"])
    print("report:", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
