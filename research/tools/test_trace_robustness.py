# -*- coding: utf-8 -*-
"""追踪健壮性回归（第九波判决 §8-B1）。

覆盖：
  T1 analysis=null / list / string 三反例——追踪不崩溃（v0.9.2 前直接
     AttributeError 中断整条 episode），trace.writeback.skipped 显式登记
     analysis_not_dict:<type>，不静默。
  T2 两轮继续执行：第一轮坏 analysis，第二轮正常输出照常处理、时钟前进、
     facts 正常入库（与父版本行为对齐）。
  T3 词面重合提示回归（v2 retr_report）："过敏" bigram 不再被停用字剥离
     消失；通道重叠标"通道不可辨识"；不再使用"采用"措辞。

全部离线（StubGW，无真实模型调用）。
"""
import importlib.util
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))


def _load_mech():
    spec = importlib.util.spec_from_file_location(
        "mech_new", os.path.join(ROOT, "candidates/phase1_retr_idf/mechanism/mechanism.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


class StubGW:
    name = "stub"

    def __init__(self, text):
        self.text = text

    def generate(self, system, user, images=None, model=None,
                 temperature=None, timeout_s=120, tag="main"):
        return {"text": self.text, "finish": "stop", "usage": None}


def _out(analysis):
    return json.dumps({
        "response": {"recommended": "那就改到成都见面", "bad_boy": "行",
                     "playful": "好", "warm": "到了说一声"},
        "directions": ["下次去成都", "你请客？", "地铁方便", "周五如何"],
        "analysis": analysis},
        ensure_ascii=False)


GOOD_ANALYSIS = {"topic_status": "same", "topic_label": "成都",
                 "scene_facts": [{"text": "她下月去成都出差", "source_ids": ["m0"]}],
                 "ongoing": [{"item": "成都见面计划", "status": "进行中"}]}


def _run_turn(mech, h, ctx, reply_text):
    return mech.run_turn(h, ctx, StubGW(reply_text))


def _new_h(tmp, name, turn=0):
    sb = os.path.join(tmp, name)
    os.makedirs(sb, exist_ok=True)
    return {"assets_root": os.path.join(ROOT, "candidates/phase0_beta_single/assets"),
            "sandbox": sb, "turn": turn, "topic": "", "topic_age": 99,
            "persona": {}, "initial_kb": {}, "memory_access": True,
            "last_clock": None, "topic_started_at": None}, sb


def _ctx(clock):
    return {"case_id": "TR", "input": {"clock": clock, "messages": [
        {"role": "HER", "text": "我下月要去成都出差", "ts": clock}]}}


def main():
    fails = []
    mech = _load_mech()
    tmp = tempfile.mkdtemp(prefix="lb_trace_")

    # ---- T1：analysis=null / list / string 不崩溃 + 显式登记 ----
    for i, bad in enumerate([None, ["x"], "not-a-dict"]):
        h, sb = _new_h(tmp, "t1_%d" % i)
        try:
            out = _run_turn(mech, h, _ctx("2026-10-20 10:0%d" % (i + 1)), _out(bad))
        except Exception as e:
            fails.append("T1%d/analysis=%s 崩溃: %s: %s"
                         % (i + 1, type(bad).__name__, type(e).__name__, e))
            continue
        tr_p = os.path.join(sb, "retrieval_trace.jsonl")
        if not os.path.isfile(tr_p):
            fails.append("T1%d/trace 未产出" % (i + 1))
            continue
        trs = [json.loads(l) for l in open(tr_p, encoding="utf-8") if l.strip()]
        wb = (trs[-1].get("writeback") or {})
        if wb.get("skipped") != "analysis_not_dict:%s" % type(bad).__name__:
            fails.append("T1%d/skipped 未登记 analysis_not_dict: %r" % (i + 1, wb.get("skipped")))
        if wb.get("facts_appended") is not None:
            fails.append("T1%d/坏 analysis 不应产出 facts_appended: %r"
                         % (i + 1, wb.get("facts_appended")))

    # ---- T2：两轮继续（第一轮坏，第二轮好）+ 时钟前进 + facts 入库 ----
    h, sb = _new_h(tmp, "t2")
    _run_turn(mech, h, _ctx("2026-10-20 11:00"), _out(None))     # 轮1 坏 analysis
    _run_turn(mech, h, _ctx("2026-10-20 12:00"), _out(GOOD_ANALYSIS))  # 轮2 正常
    fp = os.path.join(sb, "kv_facts.jsonl")
    facts = [json.loads(l) for l in open(fp, encoding="utf-8") if l.strip()]
    if not any("成都出差" in f.get("text", "") for f in facts):
        fails.append("T2/第二轮合法事实未入库: %r" % [f.get("text") for f in facts])
    if facts and facts[-1].get("clock") != "2026-10-20 12:00":
        fails.append("T2/时钟未前进到第二轮: %r" % facts[-1].get("clock"))
    trs = [json.loads(l) for l in open(os.path.join(sb, "retrieval_trace.jsonl"),
                                       encoding="utf-8") if l.strip()]
    if len(trs) != 2:
        fails.append("T2/trace 应 2 轮: %d" % len(trs))
    if (trs[0].get("writeback") or {}).get("skipped") != "analysis_not_dict:NoneType":
        fails.append("T2/轮1 skipped 未留痕: %r" % trs[0].get("writeback"))
    if (trs[1].get("writeback") or {}).get("facts_appended") is None:
        fails.append("T2/轮2 facts_appended 缺失")

    # ---- T3：词面重合 v2（"过敏"保真 + 通道不可辨识） ----
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    import retr_report
    # 3a: "过敏"不被剥离
    bg = retr_report._raw_bigrams("对猫毛过敏，不能靠近")
    if "过敏" not in bg or "猫毛" not in bg:
        fails.append("T3a/原文 bigram 缺失真实词: %r" % sorted(bg))
    # 3b: 不产生跨字伪组合（"毛敏"在原文中不相邻）
    if "毛敏" in bg:
        fails.append("T3b/出现跨字伪组合: %r" % sorted(bg))
    # 3c: 通道重叠 → 不可辨识
    label, hits = retr_report._overlap_label(
        "她对猫毛过敏", "你不是过敏吗", ["室友养猫与她过敏的冲突"])
    if label != "重合(通道不可辨识)":
        fails.append("T3c/通道重叠未标不可辨识: %r %r" % (label, hits))
    # 3d: 仅 facts 通道在场 → 普通"重合"
    label, hits = retr_report._overlap_label(
        "她对猫毛过敏", "你不是过敏吗", [])
    if label != "重合" or "过敏" not in hits:
        fails.append("T3d/词面重合提示异常: %r %r" % (label, hits))

    if fails:
        print("FAIL %d 项：" % len(fails))
        for f in fails:
            print("  -", f)
        raise SystemExit(1)
    print("PASS：追踪三反例不崩溃+留痕、两轮继续+时钟前进、词面重合保真+通道不可辨识")


if __name__ == "__main__":
    main()
