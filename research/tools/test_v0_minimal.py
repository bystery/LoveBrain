# tools/test_v0_minimal.py — C：v0_minimal_events 离线夹具验收（零真实调用）
#
# 这些夹具证明"可运行 + 记忆边界正确 + 单位声明"，不证明质量。断言：
#  g) 防漂移：v0 与 v3 的 build_system_prompt 逐字节相等（同一份精简资产）。
#  1) 故事内真实消息保存且下一轮读到；
#  2) 故事隔离（两个 handle 事件不串）；
#  3) snapshot/restore 往返一致，恢复后继续同状态；
#  4) 模型回复=草稿，不写进真实事件；
#  5) 关闭/缺省意图不作为目标（v0 只取当轮 note，空 note 轮不含上轮目标）；
#  6) 当前输入不被旧历史挤掉（长历史下当前消息仍在 prompt，历史段按码元预算裁）；
#  7) 长度用声明单位 UTF-16 码元（history_cu ≤ MAX_EVENT_HISTORY_CU）。
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


v0 = _load("candidates/v0_minimal_events/mechanism/mechanism.py", "v0mech")
v3 = _load("candidates/v3_system_slim/mechanism/mechanism.py", "v3mech")
rc = v0.rc

REPLY = json.dumps({"response": {"recommended": "嗯我在", "bad_boy": None,
                                 "playful": "又想到我啦", "warm": "辛苦啦"},
                    "directions": [None, None, None, None],
                    "analysis": {"topic_status": "same", "topic_label": "t",
                                 "scene_facts": [], "ongoing": []}},
                   ensure_ascii=False)


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


def _mk(seq, role, text, intent=None):
    inp = {"messages": [{"role": role, "text": text,
                         "ts": "2026-11-0%d 10:0%d" % ((seq % 9) + 1, seq % 9)}],
           "note": "", "clock": "2026-11-0%d 10:0%d" % ((seq % 9) + 1, seq % 9)}
    if intent is not None:
        inp["intent"] = intent
    return {"case_id": "T%02d" % seq, "turn_type": "reply", "turn_index": seq,
            "input": inp}


CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


@check
def g_no_system_drift():
    a0 = v0.build_system_prompt(os.path.join(ROOT, "candidates/v0_minimal_events/assets"))
    a3 = v3.build_system_prompt(os.path.join(ROOT, "candidates/v3_system_slim/assets"))
    assert a0 == a3, "v0 与 v3 精简 system 不一致（资产漂移）"
    return "guard PASS: v0/v3 build_system_prompt 逐字节相等(%d 码元)" % rc.utf16_len(a0)


@check
def c1_write_then_read():
    h = v0.init(os.path.join(ROOT, "candidates/v0_minimal_events/assets"),
                {"episode_id": "X1", "persona": {"who": "朋友"}}, None,
                os.path.join(ROOT, "logs", "_v0tmp", "x1"))
    gw = Gw()
    v0.run_turn(h, _mk(1, "HER", "在忙吗"), gw)
    v0.run_turn(h, _mk(2, "ME", "刚下班"), gw)
    texts = [e["text"] for e in h["events"]]
    assert "在忙吗" in texts and "刚下班" in texts, "真实消息未存/未读:%s" % texts
    # 第 3 轮历史里应含前两轮的 HER/ME
    v0.run_turn(h, _mk(3, "HER", "吃了吗"), gw)
    assert "在忙吗" in gw.calls[-1]["user"], "第3轮 user 未含更早真实历史"
    return "story-internal PASS: 真实消息写入且下一轮读到"


@check
def c2_story_isolation():
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    ha = v0.init(base, {"episode_id": "A", "persona": {}}, None, os.path.join(ROOT, "logs", "_v0tmp", "a"))
    hb = v0.init(base, {"episode_id": "B", "persona": {}}, None, os.path.join(ROOT, "logs", "_v0tmp", "b"))
    v0.run_turn(ha, _mk(1, "HER", "故事A独有句"), gw)
    assert all(e["text"] != "故事A独有句" for e in hb["events"]), "两故事事件串了"
    return "isolation PASS: 故事 B 未见故事 A 的消息"


@check
def c3_snapshot_restore():
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    h = v0.init(base, {"episode_id": "S", "persona": {"p": 1}}, None, os.path.join(ROOT, "logs", "_v0tmp", "s"))
    v0.run_turn(h, _mk(1, "HER", "还原前一句"), gw)
    v0.run_turn(h, _mk(2, "ME", "还原前二句"), gw)
    snap = v0.snapshot(h)
    h2 = v0.restore(os.path.join(ROOT, "logs", "_v0tmp", "s2"), snap)
    assert h2["events"] == h["events"], "restore 后事件列表不一致"
    assert h2["turn_count"] == h["turn_count"] and h2["persona"] == h["persona"]
    v0.run_turn(h2, _mk(3, "HER", "恢复后第三轮"), gw)
    assert "还原前一句" in gw.calls[-1]["user"] and "恢复后第三轮" in [e["text"] for e in h2["events"]]
    return "restore PASS: 往返一致，恢复后继续同状态"


@check
def c4_draft_not_committed():
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    h = v0.init(base, {"episode_id": "D", "persona": {}}, None, os.path.join(ROOT, "logs", "_v0tmp", "d"))
    v0.run_turn(h, _mk(1, "HER", "真实提问"), gw)   # REPLY 含"嗯我在"等草稿句
    assert not any(("嗯我在" in e["text"] or "辛苦啦" in e["text"]) for e in h["events"]), \
        "生成回复被当真实历史写回了"
    return "draft PASS: 模型回复(草稿)未进入真实事件历史"


@check
def c5_real_intent_not_persisted():
    """复核 §4：关闭意图必须测【真 intent】而非只测 note。"""
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    h = v0.init(base, {"episode_id": "I", "persona": {}}, None, os.path.join(ROOT, "logs", "_v0tmp", "i"))
    t1 = _mk(1, "HER", "在吗", intent={"enabled": True, "text": "私密意图哨兵III想约见面",
                                      "status": "ACTIVE"})
    t1["input"]["note"] = "备注哨兵NNN别声张"
    v0.run_turn(h, t1, gw)
    u1 = gw.calls[-1]["user"]
    assert "私密意图哨兵III" in u1, "本轮有效 intent 未进入请求"
    assert "【持续意图】" in u1, "intent 未按父本块格式出现"
    assert "备注哨兵NNN" in u1, "note 未进入请求"
    assert "军师备注" in u1, "note 未按父本块与 intent 区分"
    v0.run_turn(h, _mk(2, "HER", "换个话题"), gw)   # 无 intent
    u2 = gw.calls[-1]["user"]
    assert "私密意图哨兵III" not in u2, "上一轮 intent 被当长期目标续存"
    assert "备注哨兵NNN" not in u2, "上一轮 note 被续存"
    v0.run_turn(h, _mk(3, "HER", "再说", intent={"enabled": True, "text": "新意图YYY",
                                                 "status": "ACTIVE"}), gw)
    u3 = gw.calls[-1]["user"]
    assert "新意图YYY" in u3 and "私密意图哨兵III" not in u3, "intent 替换后旧目标残留"
    return "intent PASS: 真 intent 只取当轮，关闭/替换后不留旧目标；note 与 intent 分块"


@check
def c6_current_input_not_evicted():
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    h = v0.init(base, {"episode_id": "L", "persona": {}}, None, os.path.join(ROOT, "logs", "_v0tmp", "l"))
    for i in range(1, 41):                            # 灌 40 条长历史，逼出裁剪
        v0.run_turn(h, _mk(i, "HER", "很长的旧历史句子%02d%s" % (i, "呀" * 30)), gw)
    cur = _mk(41, "HER", "唯一当前输入哨兵句QQQ")
    v0.run_turn(h, cur, gw)
    user = gw.calls[-1]["user"]
    assert "唯一当前输入哨兵句QQQ" in user, "当前输入被旧历史挤掉了"
    hist = user.split("真实历史消息")[-1].split("# 本轮输入")[0]
    assert rc.utf16_len(hist) <= v0.MAX_EVENT_HISTORY_CU + 40, \
        "历史段未按声明码元预算裁剪: %d" % rc.utf16_len(hist)
    return "budget PASS: 长历史下当前输入保留、历史段≤MAX_EVENT_HISTORY_CU(%d)" % v0.MAX_EVENT_HISTORY_CU


@check
def c8_seed_kb_and_persona_reach_prompt():
    """复核 §4-2：同一允许的 initial_kb/persona 必须进入请求（父本种子事实两臂都可用）。"""
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    h = v0.init(base, {"episode_id": "K", "persona": {"me": "爱烧脑悬疑"},
                       "initial_kb": {"口味": "甜剧很少看"}}, None,
                os.path.join(ROOT, "logs", "_v0tmp", "k"))
    v0.run_turn(h, _mk(1, "HER", "你平时爱看啥"), gw)
    user = gw.calls[-1]["user"]
    assert "甜剧很少看" in user, "initial_kb 种子事实未进入请求"
    assert "爱烧脑悬疑" in user, "persona 种子未进入请求"
    assert "【画像/初始事实" in user, "缺少种子事实块"
    return "seed PASS: initial_kb + persona 种子进入请求"


@check
def c9_current_clock_reaches_prompt():
    """复核 §4-2：当前 clock 必须进入请求（父本 build_timestamp_prompt 块）。"""
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    h = v0.init(base, {"episode_id": "C", "persona": {}}, None, os.path.join(ROOT, "logs", "_v0tmp", "c"))
    t = _mk(1, "HER", "在吗")
    t["input"]["clock"] = "2026-11-09 15:30"
    v0.run_turn(h, t, gw)
    user = gw.calls[-1]["user"]
    assert "【当前时间】" in user and "2026-11-09 15:30" in user, "当前 clock 未进入请求"
    return "clock PASS: 【当前时间】进入请求"


@check
def c10_message_ts_and_source_alias():
    """复核 §4-3：当前消息逐条 source alias(m0/m1) + 稳定来源 ID + ts；不用轮序号冒充 ID。"""
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    h = v0.init(base, {"episode_id": "A", "persona": {}}, None, os.path.join(ROOT, "logs", "_v0tmp", "a"))
    ts1, ts2 = "2026-11-09 14:00", "2026-11-09 14:05"
    t = {"case_id": "T1", "turn_type": "reply", "turn_index": 1,
         "input": {"messages": [{"role": "ME", "text": "同一轮的本人句", "ts": ts1},
                                {"role": "HER", "text": "同一轮的对方句", "ts": ts2}],
                   "note": "", "clock": "2026-11-09 14:10", "may_decline": False}}
    out = v0.run_turn(h, t, gw)
    user = gw.calls[-1]["user"]
    assert '"id": "m0"' in user and '"id": "m1"' in user, "当前消息未逐条编号(m0/m1)"
    assert '"source_id"' in user, "缺 canonical source_id"
    assert ts1 in user and ts2 in user, "消息 ts 渲染时消失"
    assert "canonical_id_map" in out["notes"], "notes 未回传 id_map"
    # 同一轮两条消息必须拿到【不同】来源 ID（轮序号做不到区分同轮各条）
    import re as _re
    ids = _re.findall(r'"source_id": (\d+)', user)
    assert len(set(ids)) >= 2, "同轮消息来源 ID 未区分：%s" % ids
    return "alias PASS: 当前消息 m0/m1 逐条编号 + 稳定 source_id + ts 均在请求内"


@check
def c11_oversize_history_respects_cap():
    """复核 §4-4：连最近一条旧史单独超预算时也必须 ≤ 上限；当前输入仍完整保留。"""
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    h = v0.init(base, {"episode_id": "O", "persona": {}}, None, os.path.join(ROOT, "logs", "_v0tmp", "o"))
    big = "长" * 2000                              # 单条约 2000+ 码元 > 1400
    v0.run_turn(h, _mk(1, "HER", big), gw)         # 成为旧史
    t2 = _mk(2, "HER", "本轮短输入哨兵SSS")
    out = v0.run_turn(h, t2, gw)
    user = gw.calls[-1]["user"]
    assert "本轮短输入哨兵SSS" in user, "当前输入被超预算旧史挤掉"
    hist = user.split("真实历史消息")[-1].split("# 本轮输入")[0]
    assert rc.utf16_len(hist) <= v0.MAX_EVENT_HISTORY_CU, \
        "超预算旧史未被省略，历史段越界: %d" % rc.utf16_len(hist)
    return "oversize PASS: 单条旧史超上限时省略之，历史段≤%d，当前输入完整" % v0.MAX_EVENT_HISTORY_CU


@check
def c7_declared_units():
    base = os.path.join(ROOT, "candidates/v0_minimal_events/assets")
    gw = Gw()
    h = v0.init(base, {"episode_id": "U", "persona": {}}, None, os.path.join(ROOT, "logs", "_v0tmp", "u"))
    out = v0.run_turn(h, _mk(1, "HER", "单位测试"), gw)
    assert "prompt_cu=" in out["notes"] and "history_cu=" in out["notes"] \
        and "hist_budget_cu=%d" % v0.MAX_EVENT_HISTORY_CU in out["notes"], out["notes"]
    return "units PASS: notes 报 UTF-16 码元 (%s)" % out["notes"][:60]


def main():
    import shutil
    tmp_root = os.path.join(ROOT, "logs", "_v0tmp")
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-32s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-32s %s: %s" % (fn.__name__, type(e).__name__, e))
    shutil.rmtree(tmp_root, ignore_errors=True)
    out = os.path.join(ROOT, "logs", "v0_minimal_result.json")
    json.dump({"checks": results, "failed": failed, "real_model_calls": 0,
               "note": "离线夹具=可运行与边界，非质量；允许真实对照失败"},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("V0 MINIMAL RESULT: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
