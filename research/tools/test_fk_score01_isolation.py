#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_fk_score01_isolation.py — FK-SCORE-01 回归：隔离评分路径的历史引用合法集。

故障：主 runner（lb_runner._exec_turn_v2 W12-02 判决）在评分前把机制渲染的历史
标签 m_src[i]（i < prior_real_n，从评估侧原题真实计数）纳入 id_map 合法引用集；
隔离路径 trusted_controller.IsolatedRun.step_one_turn 漏了这一步，导致候选合法
引用历史消息（m_src[0]）被误 veto=illegal_source_id。

本测试（零模型调用，直接构造 turn dict 调 lb_scorer.run_case_scoring_safe）：
  正例：ep 第 0 轮有 HER/ME 真实消息，第 1 轮回复引用 m_src[0]
        → 修复前（无扩展）必须 veto=illegal_source_id:m_src[0]；
          修复后（trusted_controller.prior_hist_id_map 扩展）必须 pass；
  反例：伪造越界引用 m_src[999] → 修复后仍必须 veto；
  等价：prior_real_hist_n(ep, i) 与 lb_runner 的累计口径（逐轮 +=
        当前轮 HER/ME 计数）逐轮一致。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
sys.path.insert(0, os.path.join(ROOT, "arena"))

import lb_runner   # noqa: E402
import lb_scorer   # noqa: E402
from isolation.trusted_controller import prior_hist_id_map, prior_real_hist_n  # noqa: E402

# 评估侧原题：第 0 轮 2 条真实消息；第 1 轮 1 条真实消息。
# 第 1 轮评分时 prior_real_n=2（合法 m_src[0]/m_src[1]），第 2 轮时 =3。
EP = {
    "episode_id": "T_FK_SCORE01",
    "turns": [
        {"case_id": "FK01_t01", "turn_type": "reply",
         "input": {"messages": [
             {"role": "HER", "text": "周六有空吗", "ts": "2026-10-19 09:58"},
             {"role": "ME", "text": "有空，怎么了", "ts": "2026-10-19 09:59"}]}},
        {"case_id": "FK01_t02", "turn_type": "reply",
         "input": {"messages": [
             {"role": "HER", "text": "想约你看展", "ts": "2026-10-19 10:30"}]}},
        {"case_id": "FK01_t03", "turn_type": "reply",
         "input": {"messages": []}},
    ],
}


def _reply(source_id):
    return json.dumps({
        "response": {"recommended": "可以呀，我也想去", "bad_boy": None,
                     "playful": None, "warm": None},
        "directions": ["几点开馆", "在哪个馆", "要不要一起吃饭", "我查下路线"],
        "analysis": {"topic_status": "new", "topic_label": "看展",
                     "scene_facts": [{"text": "她想约看展",
                                      "source_ids": [source_id]}],
                     "ongoing": []}}, ensure_ascii=False)


def _current_turn_id_map(turn_index):
    """隔离路径评分入参构造（与 step_one_turn 同口径）。"""
    return lb_runner.canonical_id_map(
        (EP["turns"][turn_index].get("input") or {}).get("messages"))


def test_positive_legal_hist_reference():
    """正例：第 1 轮引用 m_src[0]（i=1 < prior_real_n=2）——
    修复前 veto、修复后 pass。"""
    id_map = _current_turn_id_map(1)
    # 修复前行为：无 m_src 扩展 → 合法历史引用被误 veto
    before = lb_scorer.run_case_scoring_safe(EP["turns"][1], _reply("m_src[0]"),
                                             dict(id_map), None)
    assert before["veto"] == "illegal_source_id:m_src[0]", \
        "前置假设失效：无扩展时应 veto illegal_source_id:m_src[0]，got %r" % before["veto"]
    assert before["score10"] == 0.0
    # 修复后行为：trusted_controller.prior_hist_id_map 扩展 → pass
    fixed_map = dict(id_map)
    fixed_map.update(prior_hist_id_map(EP, 1))
    after = lb_scorer.run_case_scoring_safe(EP["turns"][1], _reply("m_src[0]"),
                                            fixed_map, None)
    assert after["veto"] is None, "修复后合法历史引用不得 veto，got %r" % after["veto"]
    check = [c for c in after["checks"] if c["id"] == "source_ids_exist"]
    assert check and check[0]["pass"], "source_ids_exist 必须 pass：%r" % check


def test_out_of_range_still_veto():
    """反例：m_src[999] 越界引用——修复后仍必须 veto（合法集不得无限扩大）。"""
    fixed_map = _current_turn_id_map(1)
    fixed_map.update(prior_hist_id_map(EP, 1))
    s = lb_scorer.run_case_scoring_safe(EP["turns"][1], _reply("m_src[999]"),
                                        fixed_map, None)
    assert s["veto"] == "illegal_source_id:m_src[999]", \
        "越界引用必须仍 veto，got %r" % s["veto"]
    assert s["score10"] == 0.0


def test_counter_matches_lb_runner_accumulation():
    """prior_real_hist_n(ep, i) 与主 runner 逐轮累计口径逐轮一致。"""
    acc = 0
    for i, t in enumerate(EP["turns"]):
        assert prior_real_hist_n(EP, i) == acc, \
            "第 %d 轮 prior_real_n 不一致：%d != %d" % (
                i, prior_real_hist_n(EP, i), acc)
        acc += sum(1 for m in (t.get("input") or {}).get("messages") or []
                   if isinstance(m, dict) and m.get("role") in ("HER", "ME"))


def test_no_candidate_reply_leakage():
    """候选回报不参与计数：helper 只读 ep 原题，输入塞入伪造回报不改变结果。"""
    fake_reply = {"messages": [{"role": "HER", "text": "伪造"}] * 50}
    assert prior_real_hist_n(EP, 1) == 2
    assert prior_hist_id_map(EP, 1) == {"m_src[0]": "HIST", "m_src[1]": "HIST"}
    # ep 的 turn 结构里不存在候选回报字段；伪造输入经其他通道也无法进入 helper
    assert prior_real_hist_n({"turns": EP["turns"][:1]}, 1) == 2


if __name__ == "__main__":
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for fn in fns:
        fn()
        print("PASS %s" % fn.__name__)
    print("ALL %d TESTS PASSED" % len(fns))
