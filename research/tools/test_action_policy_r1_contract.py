#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""action_policy_r1 离线合同验证（0 API）。

验证点：
  T1 分支 mechanism.py 可被 runner 同款加载器加载（模块级执行无错）。
  T2 FIX-1a：may_decline=True 收尾轮 → caps 含 收尾/停，动作块含发送合同空正文要求。
  T3 FIX-1b：may_decline=False 收尾轮 → caps 剔除 收尾/停，动作块含"不允许不回"合同行。
  T4 FIX-1c：CAP_RULES["收尾"] 已替换为空正文语义，且父本矛盾行被替换。
  T5 FIX-2：任意轮动作块含【目标让位】块。
  T6 消融锚：两 flag 全关的临时 params → 收尾轮动作块不含发送合同/目标让位
     （与 v6_integrated 渲染逐字节一致）。
  T7 公共消费者语义锚（import runner 的 classify_reply）：收尾+空正文+may_decline=True
     → no_reply；收尾+正文 → delivery_failed(action_conflict)；收尾+空正文+may_decline=False
     → delivery_failed(unlicensed)——机制补丁正是为对齐这三条。
"""
import importlib.util
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BRANCH = os.path.join(ROOT, "candidates", "action_policy_r1")


def load_branch():
    path = os.path.join(BRANCH, "mechanism", "mechanism.py")
    spec = importlib.util.spec_from_file_location("apr_test_mech", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def load_classifier():
    path = os.path.join(ROOT, "tools", "run_p07_pilot.py")
    spec = importlib.util.spec_from_file_location("p07_pilot_test", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


SIG = dict(hint="收尾", closing=True, refusal=False, busy=False, defer=False,
           positive=False, ambiguous=False, last_her="先这样吧，我去忙了")
GS = {"effective": True, "closed": False, "status": "ACTIVE", "text": "推进看展"}


def main():
    fails = []
    mech = load_branch()                      # T1 加载即执行无错
    v6g = mech.v6g

    # T2 may_decline=True 收尾轮
    sig = dict(SIG, may_decline=True)
    caps = v6g._capabilities(sig, True)
    block = v6g._action_block(sig, caps, GS)
    if "收尾" not in caps or "停" not in caps:
        fails.append("T2 caps 应含 收尾/停: %s" % caps)
    if "发送合同" not in block or "必须完全为空" not in block:
        fails.append("T2 动作块缺发送合同空正文要求")
    if "【目标让位】" not in block:
        fails.append("T2 缺目标让位块")
    if "一句轻总结" in block:
        fails.append("T2 父本矛盾行未被替换")

    # T3 may_decline=False 收尾轮
    sig2 = dict(SIG, may_decline=False)
    caps2 = v6g._capabilities(sig2, True)
    block2 = v6g._action_block(sig2, caps2, GS)
    if "收尾" in caps2 or "停" in caps2:
        fails.append("T3 caps 应剔除 收尾/停: %s" % caps2)
    if "不允许不回" not in block2 or "正文不能为空" not in block2:
        fails.append("T3 缺不允许不回合同行")

    # T4 CAP_RULES 替换
    if "最终正文必须完全为空" not in v6g.CAP_RULES["收尾"]:
        fails.append("T4 CAP_RULES[收尾] 未替换: %s" % v6g.CAP_RULES["收尾"])

    # T6 消融锚：两 flag 全关 = 父本渲染
    p = os.path.join(BRANCH, "params.json")
    bak = open(p, encoding="utf-8").read()
    try:
        d = json.loads(bak)
        d["mechanism_flags"]["action_contract_fix"] = False
        d["mechanism_flags"]["goal_yield_fix"] = False
        json.dump(d, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        mech_off = load_branch()
        v6g_off = mech_off.v6g
        caps_o = v6g_off._capabilities(dict(SIG, may_decline=True), True)
        block_o = v6g_off._action_block(dict(SIG, may_decline=True), caps_o, GS)
        if "发送合同" in block_o or "【目标让位】" in block_o:
            fails.append("T6 全关后仍含补丁文本")
        if "一句轻总结" not in block_o:
            fails.append("T6 全关后应回到父本原文")
    finally:
        open(p, "w", encoding="utf-8").write(bak)

    # T7 公共消费者语义锚
    pcl = load_classifier()
    cases = [
        ({"response": {"recommended": ""}, "analysis": {"action_taken": "收尾"}}, True,
         "no_reply"),
        ({"response": {"recommended": "行，那先这样。"}, "analysis": {"action_taken": "收尾"}},
         True, "delivery_failed"),
        ({"response": {"recommended": ""}, "analysis": {"action_taken": "收尾"}}, False,
         "delivery_failed"),
    ]
    for i, (obj, md, want) in enumerate(cases):
        kind, _, reason = pcl.classify_reply(obj, md)
        if kind != want:
            fails.append("T7 case%d got %s(%s) want %s" % (i, kind, reason, want))

    # params.json 恢复校验（T6 的 finally 已恢复，再验一次内容）
    d = json.loads(open(p, encoding="utf-8").read())
    if not (d["mechanism_flags"]["action_contract_fix"] and
            d["mechanism_flags"]["goal_yield_fix"]):
        fails.append("params.json 未恢复为开启态")

    # T8 FIX-3/FIX-5：动作块含钩子承接与自我状态有据
    block3 = v6g._action_block(dict(SIG, may_decline=True),
                               v6g._capabilities(dict(SIG, may_decline=True), True), GS)
    if "【钩子承接】" not in block3:
        fails.append("T8 缺钩子承接块")
    if "【自我状态有据】" not in block3:
        fails.append("T8 缺自我状态有据块")

    # T9 FIX-4：run_turn 包装把 own_events 渲染成 note 系统事件（不调网关，
    # 用假 mech 状态与假 gw 直接验证 ctx 改写后传给父 run_turn）
    captured = {}

    def fake_parent(h, ctx, gw):
        captured["note"] = ((ctx or {}).get("input") or {}).get("note")
        return {"reply": "{}"}

    mech._apr_parent_run_turn = fake_parent
    ctx9 = {"turn_index": 2, "input": {"clock": "2026-11-06 20:05", "may_decline": True,
                                       "messages": [], "note": None, "intent": {},
                                       "own_events": [{"kind": "delivery_failed",
                                                       "turn": 1,
                                                       "reason": "action_conflict"}]}}
    mech.run_turn({}, ctx9, None)
    n9 = captured.get("note") or ""
    if "【系统事件·非聊天】" not in n9 or "第1轮" not in n9 or "action_conflict" not in n9:
        fails.append("T9 own_events 未渲染进 note: %r" % n9[:120])

    # T10 无 own_events 时 note 不被污染
    ctx10 = {"turn_index": 1, "input": {"clock": "x", "may_decline": True,
                                        "messages": [], "note": "原始note", "intent": {}}}
    mech.run_turn({}, ctx10, None)
    if captured.get("note") != "原始note":
        fails.append("T10 无事件轮 note 被污染: %r" % captured.get("note"))

    if fails:
        print("FAIL %d:" % len(fails))
        for f in fails:
            print("  -", f)
        return 1
    print("APR_CONTRACT_PASS 10/10 (T1 load, T2 contract+yield, T3 may_decline=False, "
          "T4 cap_rules, T5 in T2, T6 ablation anchor, T7 consumer anchors, "
          "T8 hook+grounding, T9 own_events render, T10 note untouched)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
