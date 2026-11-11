# tools/test_v5_purpose_aligned.py — v5 离线夹具（零真实调用；只验装配与协议路径，不证质量）
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))


def _load(rel, name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(ROOT, rel))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


v5 = _load("candidates/v5_purpose_aligned/mechanism/mechanism.py", "v5mech")
r2 = _load("candidates/v4_intent_context_r2/mechanism/mechanism.py", "r2mech_v5t")
rc = v5.rc

ASSETS = os.path.join(ROOT, "candidates/v5_purpose_aligned/assets")

# 空 analysis 的合法 JSON（v5 输出协议：response 四风格 + directions 四方向 + analysis:{}）
REPLY_EMPTY_ANALYSIS = json.dumps({
    "response": {"recommended": "嗯我在", "bad_boy": None, "playful": "又想到我啦", "warm": "辛苦啦"},
    "directions": [None, None, None, None],
    "analysis": {}}, ensure_ascii=False)


class Gw:
    def __init__(self):
        self.calls = []

    def generate(self, system, user, images=None, model=None, temperature=None,
                 timeout_s=None, tag="main", attempt_id=None):
        self.calls.append({"system": system, "user": user, "images": images})
        return {"text": REPLY_EMPTY_ANALYSIS, "finish": "stop", "usage": None,
                "resp_model": "mock", "resp_id": "mock"}

    def turn_usage(self):
        return {"n_calls": 1}


def _mk(seq, role, text, ts=None, note="", intent=None, clock=None, image=None):
    msg = {"role": role, "text": text,
           "ts": ts or "2026-11-0%d 10:0%d" % ((seq % 9) + 1, seq % 9)}
    if image:
        msg["image"] = image
    inp = {"messages": [msg], "note": note, "clock": clock or msg["ts"], "may_decline": False}
    if intent is not None:
        inp["intent"] = intent
    return {"case_id": "T%02d" % seq, "turn_type": "reply", "turn_index": seq, "input": inp}


def _h():
    return v5.init(ASSETS, {"episode_id": "X", "persona": {"me": "本人", "her": "对方"},
                            "initial_kb": {"ongoing_plan": [{"name": "周六livehouse", "status": "暂定"}]}},
                   None, os.path.join(ROOT, "logs", "_v5tmp", "x"))


CHECKS = []
def check(fn):
    CHECKS.append(fn); return fn


@check
def v5_system_drops_analysis_generation_requirement():
    sysp = v5.build_system_prompt(ASSETS)
    # v5 的 format 要求 analysis 固定空对象，且不再要求生成 scene_facts/ongoing 摘要内容
    assert "固定输出空对象" in sysp and "不消费" in sysp, "v5 system 未声明 analysis 空对象/不消费"
    # 与 r2 的 system 应不同（format.md 改了）——这是设计变量非漂移
    r2sys = r2.build_system_prompt(os.path.join(ROOT, "candidates/v4_intent_context_r2/assets"))
    assert sysp != r2sys, "v5 system 未反映 analysis 协议改动（不应与 r2 逐字节相同）"
    return "protocol PASS: v5 system 要求 analysis={}、与 r2 system 有意不同"


@check
def v5_fact_action_rule_enters_request():
    gw = Gw(); h = _h()
    v5.run_turn(h, _mk(1, "HER", "周末去哪玩好呀", clock="2026-11-09 12:00"), gw)
    u = gw.calls[-1]["user"]
    assert "# 事实与行动的边界" in u, "事实/行动边界规则未进请求"
    assert "查场次" in u and "还没查" in u and "已经查过" in u, "缺'可查而不谎称已查明'"
    assert "我包里已经有" in u and "需要真实依据" in u, "缺'拥有/亲历需真实依据'"
    assert "可直接发送的" in u and "你可以这样发" in u, "缺'正文须可发非操作指令'"
    return "fact_action PASS: 通用事实/行动边界进入请求"


@check
def v5_keeps_r2_guards():
    gw = Gw(); h = _h()
    v5.run_turn(h, _mk(1, "HER", "随手拍了几张发给你看看构图"), gw)   # 无图文字分享
    u = gw.calls[-1]["user"]
    assert "# 来源与回扣" in u, "来源护栏缺失"
    assert "无附带图片" in u and "禁止评价构图" in u, "媒体诚实护栏缺失"
    assert "# 时间与亲历边界" in u, "时间作用域护栏缺失"
    return "guards PASS: 来源/媒体/时间三护栏从 r2 继承进入请求"


@check
def v5_goal_plans_boundary_carryover():
    gw = Gw(); h = _h()
    v5.run_turn(h, _mk(1, "HER", "那事定了没", intent={"enabled": True,
                "text": "推进周六livehouse", "status": "ACTIVE"}), gw)
    u1 = gw.calls[-1]["user"]
    assert "【当前目标" in u1 and "周六livehouse" in u1, "目标/已确认事项未进请求"
    v5.run_turn(h, _mk(2, "HER", "先聊别的"), gw)
    u2 = gw.calls[-1]["user"]
    assert "周六livehouse" in u2 and "【当前目标" not in u2, "关闭目标误删已确认事项或旧目标残留"
    assert not any(("嗯我在" in e["text"] or "辛苦啦" in e["text"]) for e in h["events"]), \
        "空 analysis 草稿被写回真实事件"
    return "carry PASS: 目标启停/已确认事项持续/草稿不写回（analysis 空仍不写回）"


@check
def v5_snapshot_restore_state_isolated():
    gw = Gw(); h = _h()
    v5.run_turn(h, _mk(1, "HER", "带时间的事件A", ts="2026-11-05 09:00"), gw)
    snap = v5.snapshot(h)
    h2 = v5.restore(os.path.join(ROOT, "logs", "_v5tmp", "y"), snap)
    v5.run_turn(h2, _mk(2, "HER", "带时间的事件B", ts="2026-11-06 10:00"), gw)
    u = gw.calls[-1]["user"]
    assert "带时间的事件A" in u and "2026-11-05 09:00" in u, "恢复后旧史原文/时间未保留"
    return "restore PASS: snapshot/restore 后真实事件+时间续用"


@check
def v5_notes_prefix_and_flags():
    gw = Gw(); h = _h()
    out = v5.run_turn(h, _mk(1, "HER", "哨兵QQQ", clock="2026-11-09 12:00"), gw)
    n = out["notes"]
    assert n.startswith("v5_purpose_aligned["), "notes 前缀未更新为 v5"
    for tok in ("fact_action=1", "analysis_empty_protocol=1", "media_honest=1", "reply_not_committed=1"):
        assert tok in n, "notes 缺标志 %s" % tok
    return "notes PASS: v5 身份前缀与护栏标志齐全"


def main():
    import shutil
    tmp = os.path.join(ROOT, "logs", "_v5tmp")
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-40s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL", "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-40s %s: %s" % (fn.__name__, type(e).__name__, e))
    shutil.rmtree(tmp, ignore_errors=True)
    out = os.path.join(ROOT, "logs", "v5_purpose_aligned_result.json")
    json.dump({"checks": results, "failed": failed, "real_model_calls": 0,
               "candidate": "v5_purpose_aligned",
               "note": "v5 离线夹具=装配+analysis空协议+事实行动规则+继承护栏；非质量；未实跑"},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("V5_PURPOSE_ALIGNED: %s (%d checks, failed=%d, real_model_calls=0)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
