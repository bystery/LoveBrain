# tools/test_v4_intent_context.py — v4 初稿离线夹具（零真实调用；只验输入行为，不证质量）
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


v4 = _load("candidates/v4_intent_context/mechanism/mechanism.py", "v4mech")
v3 = _load("candidates/v3_system_slim/mechanism/mechanism.py", "v3mech")
rc = v4.rc

REPLY = json.dumps({"response": {"recommended": "嗯我在", "bad_boy": None,
                                 "playful": "又想到我啦", "warm": "辛苦啦"},
                    "directions": [None, None, None, None],
                    "analysis": {"topic_status": "same", "topic_label": "t",
                                 "scene_facts": [], "ongoing": []}}, ensure_ascii=False)


class Gw:
    def __init__(self):
        self.calls = []

    def generate(self, system, user, images=None, model=None, temperature=None,
                 timeout_s=None, tag="main", attempt_id=None):
        self.calls.append({"system": system, "user": user})
        return {"text": REPLY, "finish": "stop", "usage": None,
                "resp_model": "mock", "resp_id": "mock"}

    def turn_usage(self):
        return {"n_calls": 1}


def _mk(seq, role, text, note="", intent=None, clock=None):
    inp = {"messages": [{"role": role, "text": text,
                         "ts": "2026-11-0%d 10:0%d" % ((seq % 9) + 1, seq % 9)}],
           "note": note, "clock": clock or "2026-11-0%d 10:0%d" % ((seq % 9) + 1, seq % 9),
           "may_decline": False}
    if intent is not None:
        inp["intent"] = intent
    return {"case_id": "T%02d" % seq, "turn_type": "reply", "turn_index": seq,
            "input": inp}


CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


def _h(gw=None):
    return v4.init(os.path.join(ROOT, "candidates/v4_intent_context/assets"),
                   {"episode_id": "X", "persona": {"me": "本人", "her": "对方"},
                    "initial_kb": {"ongoing_plan": [{"name": "周六livehouse", "status": "暂定"}]}},
                   None, os.path.join(ROOT, "logs", "_v4tmp", "x"))


@check
def g_system_equals_v3():
    a4 = v4.build_system_prompt(os.path.join(ROOT, "candidates/v4_intent_context/assets"))
    a3 = v3.build_system_prompt(os.path.join(ROOT, "candidates/v3_system_slim/assets"))
    assert a4 == a3, "v4 与 v3 system 资产漂移"
    return "guard PASS: v4/v3 build_system_prompt 逐字节相等(%d 码元)" % rc.utf16_len(a4)


@check
def t1_goal_enters_and_leaves():
    gw = Gw(); h = _h()
    v4.run_turn(h, _mk(1, "HER", "在吗", intent={"enabled": True,
                "text": "想约她看周六那场", "status": "ACTIVE"}), gw)
    assert "【当前目标" in gw.calls[-1]["user"] and "想约她看周六那场" in gw.calls[-1]["user"], "有效目标未进入请求"
    v4.run_turn(h, _mk(2, "HER", "换个话题"), gw)   # 无 intent
    assert "【当前目标" not in gw.calls[-1]["user"], "关闭后旧目标残留"
    v4.run_turn(h, _mk(3, "HER", "再说", intent={"enabled": True, "text": "改天再约", "status": "ACTIVE"}), gw)
    u = gw.calls[-1]["user"]
    assert "改天再约" in u and "想约她看周六那场" not in u, "目标替换后旧目标残留"
    return "goal PASS: 启用进请求、关闭清空、替换不留旧目标"


@check
def t2_goal_drives_action_with_redlines():
    gw = Gw(); h = _h()
    v4.run_turn(h, _mk(1, "HER", "在吗", intent={"enabled": True,
                "text": "想自然创造一起吃饭的机会", "status": "ACTIVE"}), gw)
    u = gw.calls[-1]["user"]
    assert "可据此主动创造机会" in u or "可据此" in u, "目标→行动指令缺失"
    assert "不得把新提议说成对方已同意" in u, "未声明'不谎称已同意'红线"
    assert "不得把目标原文复述" in u or "不得把目标原文复述成要发的话" in u, "未声明不复述红线"
    return "action PASS: 目标可指导行动,同时约束不复述/不谎称已同意"


@check
def t3_plans_persist_across_goal_close():
    gw = Gw(); h = _h()
    v4.run_turn(h, _mk(1, "HER", "那事定了没", intent={"enabled": True,
                "text": "推进周六livehouse", "status": "ACTIVE"}), gw)
    assert "周六livehouse" in gw.calls[-1]["user"], "已确认/进行中事项未进入请求"
    v4.run_turn(h, _mk(2, "HER", "先聊别的"), gw)   # 关闭目标
    u = gw.calls[-1]["user"]
    assert "周六livehouse" in u, "关闭目标把已确认事项也删了"
    assert "【当前目标" not in u, "关闭后目标区仍在"
    return "plan PASS: 关闭当前目标≠删除已确认事项,二者分区"


@check
def t4_note_four_way_classification():
    gw = Gw(); h = _h()
    v4.run_turn(h, _mk(1, "HER", "随便聊聊", note="公开:她说过想看展; 私密:我其实想多独处"), gw)
    u = gw.calls[-1]["user"]
    for k in ("公开事实", "私密情绪", "拟发草稿", "推测"):
        assert k in u, "note 四类提示缺：%s" % k
    return "note PASS: 四类区分提示进入请求"


@check
def t5_source_alias_ts_clock():
    gw = Gw(); h = _h()
    v4.run_turn(h, _mk(1, "HER", "m0测试", clock="2026-11-09 15:30"), gw)
    u = gw.calls[-1]["user"]
    assert '"id": "m0"' in u and "source_id" in u, "当前消息缺逐条 alias/source_id"
    assert "【当前时间】" in u and "2026-11-09 15:30" in u, "clock 未进入请求"
    return "alias PASS: source alias + ts + clock 进入请求"


@check
def t6_draft_not_committed_and_units():
    gw = Gw(); h = _h()
    out = v4.run_turn(h, _mk(1, "HER", "真实提问"), gw)
    assert not any(("嗯我在" in e["text"] or "辛苦啦" in e["text"]) for e in h["events"]), "草稿被写回真实事件"
    assert "goal_active=1" in out["notes"] or "goal_active=0" in out["notes"]
    assert "reply_not_committed=1" in out["notes"] and "prompt_cu=" in out["notes"]
    return "boundary PASS: 回复不写回; notes 报码元/目标态"


@check
def t7_history_cap_and_current_kept():
    gw = Gw(); h = _h()
    for i in range(1, 41):
        v4.run_turn(h, _mk(i, "HER", "很长的旧历史句子%02d%s" % (i, "呀" * 30)), gw)
    v4.run_turn(h, _mk(41, "HER", "唯一当前输入哨兵QQQ"), gw)
    u = gw.calls[-1]["user"]
    assert "唯一当前输入哨兵QQQ" in u, "当前输入被挤掉"
    hist = u.split("真实历史消息")[-1].split("# 本轮输入")[0]
    assert rc.utf16_len(hist) <= v4.MAX_EVENT_HISTORY_CU + 40, "历史段越界 %d" % rc.utf16_len(hist)
    return "budget PASS: 历史≤%d 且当前输入保留" % v4.MAX_EVENT_HISTORY_CU


def main():
    import shutil
    tmp = os.path.join(ROOT, "logs", "_v4tmp")
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-34s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-34s %s: %s" % (fn.__name__, type(e).__name__, e))
    shutil.rmtree(tmp, ignore_errors=True)
    out = os.path.join(ROOT, "logs", "v4_intent_context_result.json")
    json.dump({"checks": results, "failed": failed, "real_model_calls": 0,
               "note": "v4 初稿夹具=输入行为与边界，非质量；未实跑"},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("V4 INTENT CONTEXT: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
