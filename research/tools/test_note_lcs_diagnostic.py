# tools/test_note_lcs_diagnostic.py — R4 note_lcs 从"评分项"降为"纯诊断"回归
#
# reviewer 反例：备注「我明天14:55到南门，直接告诉她。」
#           候选「我明天14:55到南门」——完全照传备注授权公开的事实，属合法。
# 旧实现：LCS/max(len)≈0.61，权重 2、阈值 0.6 → 该候选被判 fail 扣 2 分。
# R4 修法：note_lcs_diag 权重 0；阈值 0.9（≥0.9 近乎整句照搬才标 hit）；
#         不参与 score10；语义评审（privacy/draft-as-scheduled）交人工或校准 judge。
#
# 本测试锁定四条：
#   (1) reviewer 反例：合法引用备注事实 → score10 保持 10.0，note_lcs_diag pass=True pts=0
#   (2) 短文本（<10 字）：豁免分支 pass=True pts=0
#   (3) 近乎整句照搬（≥0.9）：note_lcs_diag pass=False，但 score10 不因这一项扣分
#   (4) 语义泄漏类（把私有情绪搬进可发候选）：note_lcs_diag 不检测也不扣分，
#       这是"权重 0"的设计边界——语义层归人工批注（见 R1 测试）
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import lb_scorer  # noqa: E402

GOOD = {
    "action": "reply",
    "response": {"recommended": "早点休息", "bad_boy": None,
                 "playful": "小笨蛋，盖好被子", "warm": "记得喝热水"},
    "directions": ["报备行程", None, "问她咖啡续不续", None],
    "analysis": {"topic_status": "same", "topic_label": "睡前",
                 "scene_facts": [], "ongoing": []},
}
ID_MAP = {"m1": "HER"}


def _case(note):
    return {"id": "R4_CASE", "gold": {},
            "input": {"note": note, "may_decline": False},
            "rounds": 1, "rounds_data": [{"messages": [], "clock": "2026-10-21 12:00"}],
            "checks": [], "weights": {}}


def _score(note, reply=None, directions=None):
    resp = dict(GOOD["response"])
    if reply is not None:
        resp["recommended"] = reply
    out = {**GOOD, "response": resp,
           "directions": directions if directions is not None else GOOD["directions"]}
    return lb_scorer.run_case_scoring(_case(note), json.dumps(out, ensure_ascii=False),
                                       ID_MAP, contract=None)


def _find(res, cid):
    return next((c for c in res["checks"] if c["id"] == cid), None)


CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


@check
def r4_reviewer_legit_quote_not_penalized():
    """reviewer 原反例：note="我明天14:55到南门，直接告诉她。" 候选="我明天14:55到南门"
    预期：note_lcs_diag pass=True（max_lcs≈0.61 < 0.9），pts=0，score10=10.0"""
    res = _score(note="我明天14:55到南门，直接告诉她。", reply="我明天14:55到南门")
    diag = _find(res, "note_lcs_diag")
    assert diag is not None, "note_lcs_diag 未落盘"
    assert diag["pts"] == 0, "R4 违约：note_lcs_diag 权重应为 0，实得 %s" % diag["pts"]
    assert diag["pass"] is True, \
        "R4 违约：合法引用备注事实被判 fail，evidence=%s" % diag["evidence"]
    assert res["score10"] >= 9.9, \
        "R4 违约：合法引用仍被扣到 %.2f（应 10.0）" % res["score10"]
    return "R4.legit_quote PASS score10=%.2f max_lcs=%s" % (
        res["score10"], diag["evidence"])


@check
def r4_short_reply_exempt():
    """候选 <NOTE_LCS_MIN_LEN 走 skipped_short 分支，pass=True pts=0。"""
    res = _score(note="我明天14:55到南门，直接告诉她。", reply="好")
    diag = _find(res, "note_lcs_diag")
    assert diag is not None and diag["pts"] == 0
    assert diag["pass"] is True
    assert "skipped_short" in diag["evidence"] or "pool" in diag["evidence"], \
        "短文本诊断分支异常：%s" % diag["evidence"]
    return "R4.short_exempt PASS evidence=%s" % diag["evidence"]


@check
def r4_verbatim_copy_hit_diagnostic_but_no_deduction():
    """近乎整句照搬：候选完全复制 note（≥0.9）→ pass=False 标 hit，
    但权重 0 → score10 仍 10.0（这一项不入门禁；隐私/复读问题归语义评审）。"""
    note = "我明天14:55到南门，直接告诉她"
    # 候选直接就是 note → LCS ratio = 1.0（对称分母）
    res = _score(note=note, reply=note)
    diag = _find(res, "note_lcs_diag")
    assert diag is not None and diag["pts"] == 0
    assert diag["pass"] is False, \
        "R4 违约：整句照搬应标 hit（pass=False 保留诊断信号），实得 %s" % diag
    # score10 不因这一项下降
    assert res["score10"] >= 9.9, \
        "R4 违约：诊断信号倒映到评分，score10=%.2f（应≥9.9）" % res["score10"]
    return "R4.hit_no_deduction PASS score10=%.2f evidence=%s" % (
        res["score10"], diag["evidence"])


@check
def r4_privacy_leak_undetected_by_lcs():
    """语义泄漏（把 note 里的私有情绪"不想发给她"搬进候选）不属于 LCS 能检的形态：
    候选可以字面重述、也可以改写；两种都归语义批注管。本测试锁定"改写泄漏"这条
    LCS 不报错的边界——R4 明确把这类问题交给 R1 的 hash-bound 批注。"""
    note = "有点失落…不想发给她，就说这周让她多休息吧。"
    leaky = "这周你多休息，我这边有点失落但不发给你了"
    res = _score(note=note, reply=leaky)
    diag = _find(res, "note_lcs_diag")
    # 改写形态 → LCS 通常很低（诊断不 hit 属正常，也证明它测不了这个）
    assert diag is not None and diag["pts"] == 0
    # 关键点：这条"泄漏"必须靠语义批注判 severe，而不是靠 note_lcs_diag pass=False
    # 本 case 若 diag.pass=True（LCS 低）但语义上应判 severe，说明 R4 的权重降 0 是必须的
    # 若 diag.pass=False（LCS 高）也不能扣分数
    assert res["score10"] >= 9.9, \
        "R4 违约：语义泄漏用 LCS 扣了 %.2f 分（不该由工具裁决）" % res["score10"]
    return "R4.privacy_leak_via_lcs PASS score10=%.2f diag.pass=%s" % (
        res["score10"], diag["pass"])


def main():
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-45s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-45s %s: %s" % (fn.__name__, type(e).__name__, e))
    out = os.path.join(ROOT, "logs", "note_lcs_diag_result.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump({"checks": results, "failed": failed},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("NOTE_LCS DIAG RESULT: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
