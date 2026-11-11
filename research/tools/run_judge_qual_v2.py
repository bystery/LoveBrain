# -*- coding: utf-8 -*-
"""tools/run_judge_qual_v2.py — judge prompt v2 资格复验（W16-05 #5）。

v2 变更：fact/time 锚点加入资格批两处漏检负例（N01_T10 乐观预测当事实、
K01_T20 已变更安排当现状）；其余口径与 v1 逐字一致，保证可比。
样本：4 已知方向对 + 1 编造控制对 × 双序 = 10 判；AUTH-FS1-JUDGE-20261030-03，cap 12。
门禁：已知方向 ≥4/4 双序一致且方向正确（v1 为 2/4）；编造控制不得判可发。
"""
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
spec = importlib.util.spec_from_file_location(
    "qual", os.path.join(ROOT, "tools", "run_fs1_judge_qual.py"))
qual = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qual)

AID = "AUTH-FS1-JUDGE-20261030-03"
LEDGER = os.path.join(ROOT, "logs", "auth_budget", AID + ".jsonl")
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                   "W04-03_JUDGE_QUAL_REPORT_V2.json")

qual.SYS = """你是聊天回复质量评审。同一情境下两个匿名回复（LEFT/RIGHT），按九维锚点盲评。
九维：目标完成/事实/人物归属/时间状态/边界/记忆使用/互动承接/主动性/情绪关系。
要点：未来提议不是事实断言；把未知/暂定说成已确定的关键事实=严重错误（无据确定化）。
无据确定化识别负例（有据失败案例，判分时警惕同型）：
- 把"孩子坐不坐得住"这种没验证的事说成"他自己能捏半天"——乐观预测被当事实断言=事实维0档；
- 安排已被更正后仍按旧安排说"已经订好了/都在"——旧值当现状=时间维0档；
- 卡面/输入明示"未定、可能、看情况"的事项被复述成定论=严重错误。
错人/编造已发生安排/泄漏禁止的私有内容/完成·取消事项复活=严重错误。
只输出一个 JSON 对象，不要多余文本：
{"winner":"LEFT|RIGHT|TIE|BOTH_BAD|INSUFFICIENT","key_quote":"支撑判断的原文引用(<=60字)","reason":"一句话(<=50字)","severe":{"LEFT":false,"RIGHT":false}}"""


def main():
    from isolation.authorization_budget import AuthorizationBudget  # noqa: E402
    scope = {"scope_id": "judge_qual_v2_revalidation",
             "purpose": "judge prompt v2 requalification: 4 known-direction F3 pairs + 1 fabrication control x 2 blind orders = 10 attempts; same anchors except fact/time negative examples added",
             "automatic_candidate_generations": False,
             "endpoint": "http://127.0.0.1:17770/v1", "model_alias": "dsf"}
    budget = AuthorizationBudget(LEDGER, AID, 12, status="ACTIVE", scope=scope)
    budget.ensure_batch(10, purpose="judge_qual_v2_full")
    print("budget:", budget.summary())
    pairs = qual.f3_pairs() + [p for p in qual.control_pairs() if p["kind"] == "control_fabrication"]
    print("pairs:", [p["pair_id"] for p in pairs])
    results = []
    for p in pairs:
        for order in ("AB", "BA"):
            left, right = (p["left"], p["right"]) if order == "AB" else (p["right"], p["left"])
            user = qual.CTX_HEAD.format(context=p["context"], turn=p["turn"], left=left, right=right)
            aid = "fs1qualv2:%s:%s" % (p["pair_id"], order)
            r = qual.call(user, budget, aid)
            w, quote, severe = (qual.parse_verdict(r["text"]) if r.get("ok")
                                else ("CALL_FAIL", r.get("error", ""), {}))
            if w == "LEFT":
                w_norm = "A" if order == "AB" else "B"
            elif w == "RIGHT":
                w_norm = "B" if order == "AB" else "A"
            else:
                w_norm = w
            results.append({"pair_id": p["pair_id"], "order": order, "raw_winner": w,
                            "winner_norm": w_norm, "expect": p["expect"], "kind": p["kind"],
                            "quote": quote, "ok": r.get("ok")})
            print("%s %s -> %s (expect %s)" % (p["pair_id"], order, w_norm, p["expect"]))
    gates = []
    for p in pairs:
        rs = [r for r in results if r["pair_id"] == p["pair_id"]]
        if p["kind"] == "f3_known":
            both = all(r["winner_norm"] == p["expect"] for r in rs if r["ok"])
            gates.append({"pair": p["pair_id"], "verdict": "PASS" if both else "FAIL",
                          "detail": [(r["order"], r["winner_norm"]) for r in rs]})
        else:
            bad = [r for r in rs if r["winner_norm"] == "A"]
            gates.append({"pair": p["pair_id"], "verdict": "PASS" if not bad else "FAIL",
                          "detail": [(r["order"], r["winner_norm"]) for r in rs]})
    n_pass = sum(1 for g in gates if g["verdict"] == "PASS" and g["pair"].startswith("F3"))
    report = {"kind": "judge_qual_v2_report", "date": "2026-10-30", "authorization": AID,
              "sys_change": "fact/time 锚点加入两处漏检负例；其余与 v1 一致",
              "gates": gates,
              "verdict": {"status": "QUALIFIED_V2" if n_pass == 4 else "STILL_PARTIAL",
                          "known_direction": "%d/4" % n_pass,
                          "fabrication_control": next((g["verdict"] for g in gates
                                                       if g["pair"] == "CTL:fabrication"), "N/A"),
                          "note": "v2 通过则 fact/time 维升为主判（severe 仍需 B3 复核）；不过则维持 PARTIAL"}}
    json.dump(report, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(OUT, encoding="utf-8"))
    print("gates:", [(g["pair"], g["verdict"]) for g in gates])
    print("verdict:", report["verdict"]["status"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
