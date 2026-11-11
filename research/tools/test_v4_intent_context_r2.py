# tools/test_v4_intent_context_r2.py — v4_intent_context_r2 离线夹具（零真实调用）
#
# 只验证本次 r2 改动 + 常用正反例（REVIEW §五.5 / WORKER_NEXT_BATCH_REVISED §3：离线只验
# 本次改动与常用链路，不重开平台/管理员/全隔离矩阵）。证明输入装配行为与边界，不证明质量。
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


r2 = _load("candidates/v4_intent_context_r2/mechanism/mechanism.py", "r2mech")
v3 = _load("candidates/v3_system_slim/mechanism/mechanism.py", "v3mech_r2t")
ref = _load("candidates/reference_product_v1/mechanism/mechanism.py", "refmech_r2t")
rc = r2.rc

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
        self.calls.append({"system": system, "user": user, "images": images})
        return {"text": REPLY, "finish": "stop", "usage": None,
                "resp_model": "mock", "resp_id": "mock"}

    def turn_usage(self):
        return {"n_calls": 1}


def _mk(seq, role, text, ts=None, note="", intent=None, clock=None, image=None):
    msg = {"role": role, "text": text,
           "ts": ts or "2026-11-0%d 10:0%d" % ((seq % 9) + 1, seq % 9)}
    if image:
        msg["image"] = image
    inp = {"messages": [msg], "note": note,
           "clock": clock or msg["ts"], "may_decline": False}
    if intent is not None:
        inp["intent"] = intent
    return {"case_id": "T%02d" % seq, "turn_type": "reply", "turn_index": seq,
            "input": inp}


def _h():
    return r2.init(os.path.join(ROOT, "candidates/v4_intent_context_r2/assets"),
                   {"episode_id": "X", "persona": {"me": "本人", "her": "对方"},
                    "initial_kb": {"ongoing_plan": [{"name": "周六livehouse", "status": "暂定"}]}},
                   None, os.path.join(ROOT, "logs", "_v4r2tmp", "x"))


CHECKS = []
def check(fn):
    CHECKS.append(fn); return fn


@check
def g0_system_equals_v3():
    a = r2.build_system_prompt(os.path.join(ROOT, "candidates/v4_intent_context_r2/assets"))
    b = v3.build_system_prompt(os.path.join(ROOT, "candidates/v3_system_slim/assets"))
    assert a == b, "r2 与 v3 system 资产漂移"
    return "system PASS: r2 build_system_prompt 与 v3 逐字节相等(%d 码元)" % rc.utf16_len(a)


@check
def r2_media_honest_when_no_image():
    gw = Gw(); h = _h()
    r2.run_turn(h, _mk(1, "HER", "我周末自己去看了那个展 随手拍了几张发给你"), gw)
    u = gw.calls[-1]["user"]
    assert "无附带图片" in u and "禁止评价构图" in u, "无图却未下媒体诚实护栏"
    assert gw.calls[-1]["images"] is None, "无图却传了 images"
    return "media PASS: 仅文字分享→声明无画面且禁止评价构图"


@check
def r2_media_with_real_image():
    gw = Gw(); h = _h()
    r2.run_turn(h, _mk(1, "HER", "给你看这张", image="data:image/png;base64,AAAA"), gw)
    u = gw.calls[-1]["user"]
    assert "实际附带 1 张图片" in u, "有图未按真实张数表达"
    assert gw.calls[-1]["images"] == ["data:image/png;base64,AAAA"], "图片未随请求附带"
    return "media PASS: 有图→按真实张数表达并附带"


@check
def r2_source_rule_lists_only_current_ids():
    gw = Gw(); h = _h()
    # 先积累一轮旧史，制造 m_src[uid] 历史，再跑当前轮
    r2.run_turn(h, _mk(1, "HER", "旧消息占位", ts="2026-11-02 10:00"), gw)
    r2.run_turn(h, _mk(2, "HER", "本轮新问", ts="2026-11-03 11:00"), gw)
    u = gw.calls[-1]["user"]
    assert "source_ids 只能引用下方 <chat> 里的 id(m0)" in u, "来源规则未限定当前窗口 id"
    assert "m_src[..] 仅用于理解与相关回扣" in u, "未声明历史 m_src 不可作本轮来源"
    return "source PASS: analysis 来源限定当前 id、历史仅供回扣（治 E006_T02）"


@check
def r2_time_scope_guard_present():
    gw = Gw(); h = _h()
    r2.run_turn(h, _mk(1, "HER", "明天有空吗", clock="2026-11-09 12:00"), gw)
    u = gw.calls[-1]["user"]
    assert "不得把某一日期的空闲或安排搬到另一个日期" in u and "职业或身份不证明" in u, "时间/亲历作用域护栏缺失"
    assert "【当前时间】2026-11-09 12:00" in u, "当前时间未进请求"
    return "time PASS: 不搬日期、职业不证明亲历（治 E005_T06）"


@check
def r2_time_event_date_not_message_date():
    """B包消歧三类输入（REVIEW §4）：真实原文+时间完整进入请求，非只查规则串出现。
    (a) 发言日≠事件日：11-08 说"下周二三点前有空"→事件在 11-10，消息 ts 只是锚点；
    (b) 换日无确认：11-09 新的一天，旧空闲不自动继承，历史原文/日期仍在请求里可判断；
    (c) 已明确未来安排续用：双方确认的"后天见"作为真实历史进入请求，不要求重确认。"""
    gw = Gw(); h = _h()
    # (a) 11-08 ME 给出"下周二(11-10)三点前有空"
    r2.run_turn(h, _mk(1, "ME", "下周二我三点前都有空", ts="2026-11-08 09:00",
                       clock="2026-11-08 09:00"), gw)
    u1 = gw.calls[-1]["user"]
    assert "下周二我三点前都有空" in u1 and "2026-11-08 09:00" in u1, "发言原文/发言时间未完整进入请求"
    assert "不等于事件本身发生的日期" in u1, "缺'消息时间≠事件日期'消歧"
    assert "已经明确给出的未来安排" in u1 and "不要求每轮重新确认" in u1, "缺'已明确未来安排续用'"
    # (b) 11-09 换日，HER 提"后天"；把 11-08 那条作为旧史保留，供正确判断而非迁移
    r2.run_turn(h, _mk(2, "HER", "要不后天一起", ts="2026-11-09 20:00",
                       clock="2026-11-09 20:00"), gw)
    u2 = gw.calls[-1]["user"]
    assert "下周二我三点前都有空" in u2, "旧史原文丢失，无法据事件日判断→易误迁"
    assert "2026-11-09 20:00" in u2 and "【当前时间】2026-11-09 20:00" in u2, "换日后当前时钟未更新进请求"
    # (c) 双方确认"后天见"作为已发生真实历史续用（非 intent 清空即删）
    r2.run_turn(h, _mk(3, "HER", "那就后天见 说定了", ts="2026-11-09 20:10",
                       clock="2026-11-09 20:10"), gw)
    assert "说定了" in gw.calls[-1]["user"], "已确认真实约定未进入请求续用"
    return "time PASS: 事件日≠发言日消歧 + 换日时钟更新 + 已确认约定续用（真实原文/时间入请求）"


@check
def carry_goal_on_off_replace():
    gw = Gw(); h = _h()
    r2.run_turn(h, _mk(1, "HER", "在吗", intent={"enabled": True,
                "text": "想约她看周六那场", "status": "ACTIVE"}), gw)
    assert "【当前目标" in gw.calls[-1]["user"] and "想约她看周六那场" in gw.calls[-1]["user"]
    r2.run_turn(h, _mk(2, "HER", "换个话题"), gw)
    assert "【当前目标" not in gw.calls[-1]["user"], "关闭后旧目标残留"
    r2.run_turn(h, _mk(3, "HER", "再说", intent={"enabled": True,
                "text": "改天再约", "status": "ACTIVE"}), gw)
    u = gw.calls[-1]["user"]
    assert "改天再约" in u and "想约她看周六那场" not in u, "替换后旧目标残留"
    return "goal PASS: 启用进请求、关闭清空、替换不留旧目标"


@check
def carry_plans_persist_and_draft_not_committed():
    gw = Gw(); h = _h()
    r2.run_turn(h, _mk(1, "HER", "那事定了没", intent={"enabled": True,
                "text": "推进周六livehouse", "status": "ACTIVE"}), gw)
    assert "周六livehouse" in gw.calls[-1]["user"], "已确认事项未进请求"
    r2.run_turn(h, _mk(2, "HER", "先聊别的"), gw)
    u = gw.calls[-1]["user"]
    assert "周六livehouse" in u and "【当前目标" not in u, "关闭目标误删已确认事项"
    assert not any(("嗯我在" in e["text"] or "辛苦啦" in e["text"]) for e in h["events"]), \
        "模型草稿被写回真实事件"
    return "boundary PASS: 关闭目标≠删已确认事项; 草稿不写回"


def main():
    import shutil
    tmp = os.path.join(ROOT, "logs", "_v4r2tmp")
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-40s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-40s %s: %s" % (fn.__name__, type(e).__name__, e))
    shutil.rmtree(tmp, ignore_errors=True)
    out = os.path.join(ROOT, "logs", "v4_intent_context_r2_result.json")
    json.dump({"checks": results, "failed": failed, "real_model_calls": 0,
               "candidate": "v4_intent_context_r2",
               "note": "r2 离线夹具=输入装配与边界(来源/媒体/时间作用域护栏 + 目标/事项/草稿沿用)，非质量；未实跑"},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("V4_INTENT_CONTEXT_R2: %s (%d checks, failed=%d, real_model_calls=0)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
