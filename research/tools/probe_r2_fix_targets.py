# -*- coding: utf-8 -*-
r"""tools/probe_r2_fix_targets.py — action_policy_r2 三项修复的零调用前置取证。

目的：在花掉任何真实请求之前，用**已经付费的原始答卷**证明
  (1) F4（合法不回被判格式损坏）的根因确实只是"缺 action 表达"：
      同一份 AK09/AK12 原文，注入 "action":"no_reply"+"decline_reason" 后
      评分器不再 veto —— 说明评分器一直认这个写法，是候选没教模型写；
  (2) F3（directions 后提前闭合 }）确为可恢复结构错位：
      AK02 原文按 raw_decode 断点重拼后评分器不 veto，四风格正文完好；
  (3) r2 的提示改动真的进入出站请求：同一 case 分别用 r1/r2 机制装配，
      比对 user prompt 与 system prompt 的文本差异（不回表达/单 JSON 硬约束/回扣有据），
      并核对 r2 没有把 may_decline=false 的轮次变成"可以不回"。

全程零网络：评分器只读产物；机制侧用替身 gateway（不落任何真实请求、不写真实账本）。
输出：UTF-8 文件（Windows 控制台 GBK 打不出 CJK）。
"""
import importlib.util
import io
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "arena"))
import lb_runner                                                    # noqa: E402
import lb_scorer                                                    # noqa: E402

BATCH = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "s1_action_live_20261028")
SKEL = os.path.join(BATCH, "S2_SCORE_SKELETON.json")
OUT = os.path.join(BATCH, "PROBE_R2_FIX_TARGETS_20261028.json")

DEC = json.JSONDecoder()


def load_mech(name):
    d = os.path.join(ROOT, "candidates", name, "mechanism", "mechanism.py")
    spec = importlib.util.spec_from_file_location("mech_" + name, d)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


EP_DIR = os.path.join(ROOT, "benchmark", "stories_v2", "anchors_20261028")


def case_of(cid):
    """评审用 case = 锚点题面原样（input+gold），与实跑装配同源。"""
    sid = cid.split("_")[0]
    ep = json.load(io.open(os.path.join(EP_DIR, sid, "episode.json"), encoding="utf-8"))
    turn = [t for t in ep["turns"] if t["case_id"] == cid][0]
    return ep, turn


class StubGW(object):
    """替身网关：只记录出站文本，返回固定答卷；绝不联网。"""

    def __init__(self, reply):
        self.calls = []
        self.reply = reply

    def generate(self, system, prompt, images=None, tag=None):
        self.calls.append({"system": system, "user": prompt, "tag": tag,
                           "images": bool(images)})
        return {"text": self.reply, "usage": None}


NO_REPLY_CANNED = json.dumps({
    "action": "no_reply", "decline_reason": "她已明确收尾，本轮不需要我再发一条",
    "response": {"recommended": "", "bad_boy": "", "playful": "", "warm": ""},
    "directions": [None, None, None, None],
    "analysis": {"signal": "收尾", "goal_category": "收线", "action_taken": "收尾",
                 "anchor": "", "hook": {"kind": "无", "text": ""},
                 "facts": [], "tentatives": [], "updates": []}}, ensure_ascii=False)


def score(case, raw, contract=None):
    r = lb_scorer.run_case_scoring(case, raw, {}, contract)
    return {"score10": r.get("score10"), "veto": r.get("veto"),
            "decline_path": bool([c for c in r["checks"]
                                  if c["id"] == "decline_appropriateness"])}


def main():
    sk = json.load(io.open(SKEL, encoding="utf-8"))
    idx = {p["case_id"]: p for p in sk["positions"]}
    rep = {"zero_network": True, "checks": []}

    # ---------- (1) F4：同一份付费原文，只补 action 表达 ----------
    for cid in ("AK09_T01", "AK12_T01"):
        raw = idx[cid]["arms"]["action_policy_r1"]["raw_text"]
        _ep, turn = case_of(cid)
        case = {"case_id": cid, "turn_type": turn.get("turn_type", "reply"),
                "input": turn["input"], "gold": turn.get("gold") or {}}
        as_is = score(case, raw)
        obj = json.loads(raw)
        obj2 = dict(obj)
        obj2["action"] = "no_reply"
        obj2["decline_reason"] = "她已明确收尾，本轮不需要我再发一条"
        injected = score(case, json.dumps(obj2, ensure_ascii=False))
        rep["checks"].append({
            "case_id": cid, "family": "F4_no_reply_expression",
            "as_is": as_is, "with_action_key": injected,
            "verdict": ("缺口成立：同文本加 action 即脱离 veto"
                        if as_is["veto"] == "json_schema_broken" and not injected["veto"]
                        else "缺口判定不成立，须重查根因")})

    # ---------- (2) F3：AK02 提前闭合 } 的可恢复性 ----------
    raw = idx["AK02_T01"]["arms"]["action_policy_r1"]["raw_text"]
    _ep2, _t2 = case_of("AK02_T01")
    head, end = DEC.raw_decode(raw)
    tail = raw[end:]
    if tail.startswith(","):
        # 模型在 directions 之后多写了一个顶层 }（末尾那个才是真正的收尾）：
        # 删掉第一段末尾的提前右花括号，再把剩余 ,"analysis":{...} 原样接回去。
        repaired_str = raw[:end - 1] + tail
        repaired, _e2 = DEC.raw_decode(repaired_str)
        assert _e2 == len(repaired_str), "重拼后仍有残留数据"
        repaired = json.dumps(repaired, ensure_ascii=False)
    else:
        repaired = raw
    case02 = {"case_id": "AK02_T01", "turn_type": _t2.get("turn_type", "reply"),
              "input": _t2["input"], "gold": _t2.get("gold") or {}}
    as_is = score(case02, raw)
    fixed = score(case02, repaired)
    obj = json.loads(repaired)
    rep["checks"].append({
        "case_id": "AK02_T01", "family": "F3_premature_top_level_brace",
        "as_is": as_is, "after_rejoin": fixed,
        "sendable_texts_recoverable": sum(1 for k, v in (obj.get("response") or {}).items()
                                          if isinstance(v, str) and v.strip()),
        "analysis_recoverable": isinstance(obj.get("analysis"), dict),
        "verdict": ("结构错位可无损重拼（正文与 analysis 都在）→ 提示层硬约束有意义；"
                    "但原始协议层照常记 veto，修复只作用于出站提示与消费侧留损"
                    if as_is["veto"] == "json_schema_broken" and not fixed["veto"]
                    else "重拼后仍非零 veto，须回看是否另有语义缺件")})

    # ---------- (3) r1 vs r2 出站提示差异（同一 case，替身网关） ----------
    sandbox = tempfile.mkdtemp(prefix="r2probe_")
    try:
        for cid, cand, expect_nr in (("AK09_T01", "action_policy_r1", False),
                                     ("AK09_T01", "action_policy_r2", True),
                                     ("AK01_T01", "action_policy_r2", None),
                                     ("AK03_T01", "action_policy_r2", None)):
            ep, turn = case_of(cid)
            gw = StubGW(NO_REPLY_CANNED if expect_nr else json.dumps(
                {"response": {"recommended": "行，就这么定", "bad_boy": "成",
                              "playful": "行吧", "warm": "好"},
                 "directions": ["一", "二", None, None],
                 "analysis": {"signal": "中性", "goal_category": "推进见面",
                              "action_taken": "承接", "anchor": "m0",
                              "hook": {"kind": "无", "text": ""},
                              "facts": [], "tentatives": [], "updates": []}},
                ensure_ascii=False))
            m = load_mech(cand)
            assets = os.path.join(ROOT, "candidates", cand, "assets")
            h = m.init(assets, {"mode": "single"}, gw, sandbox)
            ctx = lb_runner.sanitize_turn(turn)
            ctx["assets_root"] = assets
            ctx["sandbox"] = sandbox
            ctx["turn_type"] = turn.get("turn_type", "reply")
            ctx["turn_index"] = 1
            r = m.run_turn(h, ctx, gw)
            call = gw.calls[-1]
            user = call["user"]
            sig = {"不回怎么写": "【不回怎么写】" in user,
                   "单JSON硬约束": "【单 JSON 硬约束】" in user,
                   "回扣有据": "【回扣有据】" in user,
                   "旧的空正文合同": "最终正文（response.recommended）必须完全为空" in user,
                   "format.md 不回schema": "JSON Schema（不回）" in call["system"],
                   "format.md 键名照抄": "四个键名逐字照抄" in call["system"],
                   "core.md 回扣自检": "回扣旧事的自检" in call["system"]}
            caps_line = [l for l in user.splitlines() if "本轮可用能力" in l or "收尾" in l]
            may_decline = bool(turn["input"].get("may_decline"))
            nr_offered = ("收尾" in user and "不回怎么写" in user)
            rep["checks"].append({
                "case_id": cid, "candidate": cand, "may_decline": may_decline,
                "prompt_flags": sig,
                "nr_expression_offered": nr_offered,
                "caps_excerpt": caps_line[:3],
                "analysis_parse": (r.get("action_self_assessment") or {}).get("parse"),
                "reply_returned": (r.get("reply") or "")[:40],
                "expect": ("r1 不应出现【不回怎么写】且旧空正文合同在" if expect_nr is False else
                           "r2 收尾轮必须出现【不回怎么写】且旧合同文本已被替换"
                           if expect_nr is True else
                           "非不回轮不得出现【不回怎么写】，但硬约束与回扣判据必须在"),
                "ok": (
                    (not sig["不回怎么写"] and sig["旧的空正文合同"]) if expect_nr is False else
                    (sig["不回怎么写"] and not sig["旧的空正文合同"]
                     and sig["单JSON硬约束"] and sig["回扣有据"]) if expect_nr is True else
                    (not sig["不回怎么写"] and sig["单JSON硬约束"] and sig["回扣有据"]))
            })
    finally:
        shutil.rmtree(sandbox, ignore_errors=True)

    def _root_ok(c):
        if c["family"] == "F4_no_reply_expression":
            return (c["as_is"]["veto"] == "json_schema_broken"
                    and not c["with_action_key"]["veto"])
        if c["family"] == "F3_premature_top_level_brace":
            return (c["as_is"]["veto"] == "json_schema_broken"
                    and not c["after_rejoin"]["veto"]
                    and c["sendable_texts_recoverable"] == 4 and c["analysis_recoverable"])
        return False

    rep["all_ok"] = all(c["ok"] if "ok" in c else _root_ok(c) for c in rep["checks"])
    json.dump(rep, io.open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("PROBE R2 WRITTEN", OUT, "all_ok=", rep["all_ok"], "checks=", len(rep["checks"]))
    return 0 if rep["all_ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
