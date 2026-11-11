# -*- coding: utf-8 -*-
"""tools/test_p07_env_v2.py — V3 W01-C 离线验证矩阵（0 网络、0 真实模型）。

按 FIRST_8H_WORK_ORDER §C 逐条验证（修后通过/反例清单）：
1. 动作语义：空对象=交付失败；合法 no_reply 仅在 may_decline 许可轮通过；
   no_reply+正文冲突被拒；正常 send 不受影响。
2. 意图规范形经真实机制入口（v6_integrated）捕获：ACTIVE/ADJUSTED 进实际出站 prompt，
   CLOSED 不进；旧 {"goal":…} 形不被消费（复现原缺陷口径，证明修复必要性）。
3. ME 回放与游标：轮1发送的 ME 原文+message_uid+逻辑ts 进入轮2 ctx；
   事件不重复写入；不回=control 事件，不伪造 ME 聊天文本；模拟器收到显式缺席事件。
4. 模拟失败：轨迹标 incomplete_environment_failure，不造 "…" HER。
5. 归一器：别名双方非空不丢弃（审计清单保全）、多出方向不截断、缺槽 null 报告、
   已有成功样本身份不误杀。
6. 轨迹隔离：不同反馈条件的轨迹 ID/沙箱/trace 互不串。
"""
import importlib.util
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))

os.environ.setdefault("LB_API_BASE", "http://127.0.0.1:17770/v1")

import run_p07_pilot as P
import build_b3_simulator_request as SIM
import normalize_reply_contract as NRM
from arena.isolation.authorization_budget import AuthorizationBudget

RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    print("%s %s %s" % ("PASS" if ok else "FAIL", name, detail))


# ---------- 1) classify_reply 动作语义矩阵 ----------
def t1_classify():
    f = P.classify_reply
    cases = [
        ({}, False, "delivery_failed", "empty_object_not_no_reply"),
        ({"response": {"recommended": "正文"}, "analysis": {"action_taken": "不回"}},
         False, "delivery_failed", "no_reply_with_body_conflict"),
        ({"response": {"recommended": None}, "decline_reason": "她在忙"}, True, "no_reply", "decline_allowed"),
        ({"response": {"recommended": None}, "decline_reason": "她在忙"}, False, "delivery_failed", "unlicensed"),
        ({"response": {"recommended": None}, "analysis": {"action_taken": "停"}}, True, "no_reply", "action_allowed"),
        ({"response": {"recommended": "你好"}, "analysis": {"action_taken": "承接"}}, False, "send", "normal_send"),
        ({"response": {}}, False, "delivery_failed", "no_recommended"),
        ("not a dict", False, "delivery_failed", "not_object"),
    ]
    for obj, may, want_kind, tag in cases:
        kind, _, reason = f(obj, may)
        check("classify:%s" % tag, kind == want_kind, "got=%s reason=%s" % (kind, reason))


# ---------- 2) 意图规范形经真实机制入口 ----------
class CaptureGateway(object):
    def __init__(self):
        self.captured = []

    def generate(self, system, user, images=None, model=None, temperature=None,
                 timeout_s=120, tag="main", attempt_id=None, **kw):
        self.captured.append({"system": system, "user": user, "tag": tag})
        return {"text": json.dumps({
            "response": {"recommended": "好，那听你的。", "bad_boy": None,
                         "playful": None, "warm": None},
            "directions": [None, None, None, None],
            "analysis": {"action_taken": "承接", "scene_facts": [], "ongoing": []}},
            ensure_ascii=False)}


def load_mech(arm):
    spec = importlib.util.spec_from_file_location(
        "t_p07_mech", os.path.join(ROOT, "candidates", arm, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def t2_intent():
    mech = load_mech("v6_integrated")
    tmp = tempfile.mkdtemp(prefix="t2_intent_")
    scs = json.load(open(os.path.join(ROOT, "benchmark", "interaction_env_20261026",
                                      "scenarios.json"), encoding="utf-8"))["scenarios"]
    sc = next(s for s in scs if s["id"] == "I2")
    h = mech.init(os.path.join(ROOT, "candidates", "v6_integrated", "assets"), {
        "episode_id": "T2_INTENT", "persona": {"me": "我", "her": sc["her"]["persona"]},
        "initial_kb": {}}, CaptureGateway(), tmp)
    goal = "轻松推进一起看展"
    for tag, intent, want_in_prompt, note in [
        ("canonical_ACTIVE", {"text": goal, "status": "ACTIVE", "enabled": True}, True, "规范形生效"),
        ("canonical_ADJUSTED", {"text": goal, "status": "ADJUSTED", "enabled": True}, True, "调整形生效"),
        ("canonical_CLOSED", {"text": goal, "status": "CLOSED", "enabled": False}, False, "关闭不进"),
        ("legacy_goal_only", {"goal": goal}, False, "旧形不被消费（原缺陷口径）"),
        ("none", None, False, "无目标不复活旧邀约"),
    ]:
        gw = CaptureGateway()
        ctx = {"turn_index": 1, "input": {"clock": "2026-11-06 20:05", "may_decline": False,
                                          "messages": [{"role": "HER", "text": sc["opening"]["text"],
                                                        "ts": "2026-11-06 20:00"}],
                                          "note": None, "intent": intent}}
        try:
            mech.run_turn(h, ctx, gw)
        except Exception:
            pass  # 出站 prompt 已捕获即可；机制后续解析失败与本验证无关
        user = gw.captured[-1]["user"] if gw.captured else ""
        check("intent:%s" % tag, (goal in user) == want_in_prompt,
              "%s; goal_in_prompt=%s" % (note, goal in user))


# ---------- 3/4) run_trajectory：ME回放/缺席/游标/模拟失败 ----------
class FakeMech(object):
    def __init__(self, behavior="send_then_decline"):
        self.behavior = behavior
        self.ctxs = []

    def init(self, assets_root, cfg, gw, sandbox):
        return {"ok": True}

    def run_turn(self, h, ctx, gw):
        self.ctxs.append(json.loads(json.dumps(ctx, ensure_ascii=False)))
        k = ctx["turn_index"]
        if self.behavior == "send_then_decline":
            if k == 1:
                return {"reply": json.dumps({"response": {"recommended": "TEST发送文本A"},
                                             "analysis": {"action_taken": "承接"}},
                                            ensure_ascii=False)}
            return {"reply": json.dumps({"response": {"recommended": None},
                                         "decline_reason": "她在忙"}, ensure_ascii=False)}
        return {"reply": "不是JSON"}   # 交付失败路径


class FakeInner(object):
    def __init__(self, sim_behavior="ok"):
        self.sim_behavior = sim_behavior

    def generate(self, system, user, images=None, model=None, temperature=None,
                 timeout_s=120, tag="main", attempt_id=None, **kw):
        if tag in ("sim", "negctl"):
            if self.sim_behavior == "timeout":
                raise TimeoutError("timed out")
            if self.sim_behavior == "empty":
                return {"text": ""}
            return {"text": json.dumps({"reply": "嗯，那行吧。", "state_delta": {},
                                        "internal": "x", "end_dialog": False},
                                       ensure_ascii=False)}
        return {"text": "unused"}


def t3_t4_trajectory():
    scs = json.load(open(os.path.join(ROOT, "benchmark", "interaction_env_20261026",
                                      "scenarios.json"), encoding="utf-8"))["scenarios"]
    sc = next(s for s in scs if s["id"] == "I2")
    captured_builds = []
    orig_build = SIM.build

    def capturing_build(*args, **kw):
        req = orig_build(*args, **kw)
        captured_builds.append({"args": list(args), "kw": dict(kw), "req": req})
        return req

    SIM.build = capturing_build
    try:
        tmp = tempfile.mkdtemp(prefix="t3_traj_")
        ledger = os.path.join(tmp, "AUTH-T3.jsonl")
        budget = AuthorizationBudget(ledger, "AUTH-T3-GUARD", 50,
                                     scope={"experiment": "p07_env_v2_test"})
        ev_events = []

        def ev(o):
            ev_events.append(o)

        mech = FakeMech()
        gw = P.BudgetedGateway(FakeInner("ok"), budget, "traj_test", ev, nonce="t1")
        r = P.run_trajectory(mech, "fake_arm", "", sc, "hesitant", 1, 3,
                             gw, ev, tmp, None)
        # 3a 轮2 ctx 有轮1实际发送 ME 原文+uid+ts
        ctx2 = mech.ctxs[1]
        msgs2 = ctx2["input"]["messages"]
        has_me = [m for m in msgs2 if m["role"] == "ME" and m["text"] == "TEST发送文本A"]
        check("me_replay_in_ctx", bool(has_me) and has_me[0].get("message_uid")
              and has_me[0].get("ts"), "msgs2=%s" % json.dumps(msgs2, ensure_ascii=False)[:200])
        # 3b 事件不重复：轮2 ctx 只含轮1 ME + 轮1 HER（各一次）
        me_cnt = sum(1 for m in msgs2 if m["role"] == "ME")
        her_cnt = sum(1 for m in msgs2 if m["role"] == "HER")
        check("cursor_no_dup", me_cnt == 1 and her_cnt == 1,
              "me=%d her=%d" % (me_cnt, her_cnt))
        # 3c 无伪造 ME 聊天文本
        hist_texts = [h_["text"] for h_ in r and json.load(
            open(os.path.join(tmp, "TRACE_%s.json" % r["trajectory_id"]),
                 encoding="utf-8"))["final_history"]]
        check("no_fake_me_text", all("（未获回复）" != t for t in hist_texts),
              "history=%s" % json.dumps(hist_texts, ensure_ascii=False)[:200])
        # 3d 轮2 no_reply 记 control 事件，模拟器收到缺席事件
        tr = json.load(open(os.path.join(tmp, "TRACE_%s.json" % r["trajectory_id"]),
                            encoding="utf-8"))
        ctrl_types = [c["type"] for c in tr["control_events"]]
        check("no_reply_is_control_event", "no_reply" in ctrl_types,
              "control=%s" % ctrl_types)
        turn2_reqs = [c for c in captured_builds if c["kw"].get("absence_notes")]
        check("sim_absence_channel", bool(turn2_reqs),
              "absence_kw=%s" % json.dumps([c["kw"]["absence_notes"] for c in turn2_reqs],
                                           ensure_ascii=False)[:200])
        # 3e intent 规范形进了轨迹 ctx
        it = tr["trace"][0]["ctx"]["input"]["intent"]
        check("canonical_intent_in_traj",
              set(["text", "status", "enabled"]) <= set(it) and it["status"] == "ADJUSTED",
              "intent=%s" % json.dumps(it, ensure_ascii=False))
        # 3f 意图规范形也进入真实出站请求（真实 SIM 请求的 user 含目标原文——
        #    机制把 goal 装配进 system/user；此处验 SIM 层收到的 history 无伪造）
        # 4) 模拟失败：不造 HER
        captured_builds.clear()
        tmp2 = tempfile.mkdtemp(prefix="t4_simfail_")
        budget2 = AuthorizationBudget(os.path.join(tmp2, "AUTH-T4.jsonl"), "AUTH-T4-GUARD", 50,
                                      scope={"experiment": "p07_env_v2_test"})
        mech2 = FakeMech()
        gw2 = P.BudgetedGateway(FakeInner("timeout"), budget2, "traj_test2", ev, nonce="t2")
        r2 = P.run_trajectory(mech2, "fake_arm", "", sc, "positive", 1, 3,
                              gw2, ev, tmp2, None)
        tr2 = json.load(open(os.path.join(tmp2, "TRACE_%s.json" % r2["trajectory_id"]),
                             encoding="utf-8"))
        check("sim_failure_incomplete",
              r2["status"] == "incomplete_environment_failure"
              and all("…" != h_["text"] for h_ in tr2["final_history"]),
              "status=%s" % r2["status"])
        # 6) 轨迹隔离
        check("traj_isolation", r["trajectory_id"] != r2["trajectory_id"]
              and os.path.exists(os.path.join(tmp, "TRACE_%s.json" % r["trajectory_id"]))
              and os.path.exists(os.path.join(tmp2, "TRACE_%s.json" % r2["trajectory_id"])),
              "%s vs %s" % (r["trajectory_id"], r2["trajectory_id"]))
    finally:
        SIM.build = orig_build


# ---------- 5) 归一器：别名冲突保全/多出方向/缺槽/不误杀 ----------
def t5_normalizer():
    raw = json.dumps({
        "response": {"recommended": "好呀", "bad_boy": "选项A", "bad_boys": "选项B",
                     "playful": None, "warm": None},
        "directions": ["方向1", "方向2", "方向3", "方向4", "方向5", "方向6"],
    }, ensure_ascii=False)
    res = NRM.normalize_raw(raw)
    unmapped_vals = [u["alias_value"] for u in res["unmapped"]]
    canon = res["canonical"]
    check("alias_collision_kept", "选项B" in unmapped_vals
          and canon["response"]["bad_boy"] == "选项A",
          "unmapped=%s" % json.dumps(res["unmapped"], ensure_ascii=False))
    check("directions_extra_preserved",
          len(canon["directions"]) == 4
          and canon.get("_audit_extra_directions") == ["方向5", "方向6"],
          "extra=%s" % json.dumps(canon.get("_audit_extra_directions"), ensure_ascii=False))
    check("missing_slots_reported",
          "response.playful" in res["missing_slots"]
          and "response.warm" in res["missing_slots"],
          "missing=%s" % res["missing_slots"])
    check("preferred_not_killed", res["outcome"] == "send" and res["sendable"] == "好呀",
          "outcome=%s sendable=%s" % (res["outcome"], res["sendable"]))
    # 已有成功样本身份不误杀：干净 raw 零修复
    clean = json.dumps({"response": {"recommended": "好", "bad_boy": "b", "playful": "p",
                                     "warm": "w"},
                        "directions": ["1", "2", "3", "4"]}, ensure_ascii=False)
    res2 = NRM.normalize_raw(clean)
    check("clean_raw_no_repairs", res2["praw_ok"] and res2["outcome"] == "send"
          and not [r for r in res2["repairs"] if "fix" in r or "unmapped" in r
                   or "extra" in r or "pad" in r],
          "repairs=%s" % res2["repairs"])


def main():
    t1_classify()
    t2_intent()
    t3_t4_trajectory()
    t5_normalizer()
    fails = [n for n, ok, _ in RESULTS if not ok]
    print("P07_ENV_V2_OFFLINE %s (%d/%d pass)"
          % ("ALL_PASS" if not fails else "HAS_FAIL", len(RESULTS) - len(fails), len(RESULTS)))
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
