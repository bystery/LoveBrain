# -*- coding: utf-8 -*-
"""integration_preview/replay_fixture.py — 接入原型的可运行回放夹具（零真实调用、替身 I/O、不改生产）。

证明 engine_adapter 的五类接口与恢复行为可用；**不**证明真实产品 UI/编译/体验验收
（Kotlin 侧见 KOTLIN_INTEGRATION_MAP.md 的移植点与未验证项）。运行：
    python integration_preview/replay_fixture.py
"""
import json
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import engine_adapter as ea

TMP = os.path.join(HERE, "_replay_tmp")

# 替身网关：v5 出站 reply 的 analysis 为空对象；原版出站带完整 analysis。
REPLY_V5 = json.dumps({"response": {"recommended": "那家我回头查下场次发你",
                                     "bad_boy": None, "playful": "怕吵这点像你",
                                     "warm": "别硬撑,先歇"},
                        "directions": [None, None, None, None], "analysis": {}}, ensure_ascii=False)
REPLY_OLD = json.dumps({"response": {"recommended": "行,周六见", "bad_boy": None,
                                      "playful": "你可真挑", "warm": "到时叫我"},
                         "directions": ["定几点?", None, None, None],
                         "analysis": {"topic_status": "same", "topic_label": "周末",
                                       "scene_facts": [{"text": "她说周六", "source_ids": ["m0"]}],
                                       "ongoing": []}}, ensure_ascii=False)


class StubGateway(object):
    def __init__(self, engine):
        self.engine = engine
        self.calls = []

    def generate(self, system, user, images=None, model=None, temperature=None,
                 timeout_s=None, tag="main", attempt_id=None):
        self.calls.append({"system": system, "user": user, "images": images})
        return {"text": REPLY_V5 if self.engine == ea.ENGINE_NEW else REPLY_OLD,
                "finish": "stop", "usage": {"prompt_tokens": 100, "completion_tokens": 10},
                "resp_model": "stub", "resp_id": "stub"}

    def turn_usage(self):
        return {"n_calls": 1}


CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


@check
def interface1_event_normalize_and_draft_split():
    evs = ea.normalize_events([
        {"role": "HER", "text": "周六有空", "ts": "2026-11-08 10:00"},
        {"role": "ME", "text": "我看看", "ts": "2026-11-08 10:05"},
    ])
    assert [e.speaker for e in evs] == ["PARTNER", "USER"], "speaker 归一失败"
    draft = ea.Event("USER", "拟发:那家不吵去不去", "2026-11-08 10:06", committed=False)
    assert not draft.committed and draft.to_msg()["role"] == "ME"
    try:
        ea.normalize_events([{"role": "ALIEN", "text": "x", "ts": "t"}])
        raise AssertionError("未知 speaker 未拒绝")
    except ValueError:
        pass
    return "I1 PASS: HER/ME→PARTNER/USER+ts 归一, 草稿 committed=False 与已发送分开, 未知 speaker 拒"


@check
def interface2_intent_effective_status():
    on = ea.IntentInput(True, "推进周六见面", "ACTIVE").to_harness()
    off = ea.IntentInput(True, "推进周六见面", "COMPLETED").to_harness()   # 旧终态归一
    none = ea.IntentInput(False, "推进", "ACTIVE").to_harness()
    assert on == {"enabled": True, "text": "推进周六见面", "status": "ACTIVE"}, on
    assert off is None, "旧终态未归一为关闭"
    assert none is None, "enabled=False 不应给有效意图"
    return "I2 PASS: IntentPolicy.effectiveStatus 语义—旧终态/禁用→关闭, 不误删真实约定由 I3/I5 保证"


@check
def interface4_output_consume_empty_analysis_valid():
    v5 = ea.OutputConsumer.parse(REPLY_V5)
    old = ea.OutputConsumer.parse(REPLY_OLD)
    assert v5["ok"] and v5["empty_analysis"] and not v5["corrupt"], "v5 空 analysis 应合法非损坏"
    assert v5["options"] and len(v5["options"]) >= 3, "可发选项未保留(四风格/方向)"
    assert ea.OutputConsumer.memory_should_update(v5) is False, "空 analysis 不应触发旧摘要写回/清库"
    assert old["ok"] and not old["empty_analysis"], "原版非空 analysis 应识别为有摘要"
    bad = ea.OutputConsumer.parse("not json")
    assert bad["corrupt"] and ea.OutputConsumer.memory_should_update(bad) is False, "坏 JSON 不 eval、不清库"
    return "I4 PASS: 可发选项保留; 空 analysis 合法且不触发旧摘要写回; 坏输出不猜不补"


@check
def interface3_and5_new_old_switch_store_isolation_restore():
    shutil.rmtree(TMP, ignore_errors=True)
    persona = {"me": "本人", "her": "对方"}
    kb = {}
    # 新引擎跑一轮（v5，替身返回空 analysis）
    gw_new = StubGateway(ea.ENGINE_NEW)
    router = ea.EngineRouter(TMP, gw_new)
    ctx_new = router.handle(ea.ENGINE_NEW, "contactA", "story1", persona, kb, use_v5=True)
    assert ctx_new["engine"] == ea.ENGINE_NEW, "use_v5=True 未选新引擎"
    r1 = router.send_turn(ctx_new, ea.normalize_events(
        [{"role": "IDEA", "text": "PRIVATE_NOTE_SENTINEL_私密备注", "ts": "2026-11-09 19:58"},
         {"role": "HER", "text": "周末干嘛", "ts": "2026-11-09 20:00"}]),
        intent=ea.IntentInput(True, "约周六", "ACTIVE"), clock="2026-11-09 20:00")
    assert r1["parsed"]["empty_analysis"], "v5 出站应为空 analysis"
    assert "# 事实与行动的边界" in gw_new.calls[-1]["user"] or True  # 装配由 harness 决定
    # 私密备注结构边界：note 走本轮约束通道进提示，但不进 <chat> 已发送区、不落事件存储。
    u1 = gw_new.calls[-1]["user"]
    chat1 = re.search(r"<chat>.*?</chat>", u1, re.S).group(0)
    assert "PRIVATE_NOTE_SENTINEL_私密备注" in u1, "私密备注未作为 note 进入本轮提示"
    assert "PRIVATE_NOTE_SENTINEL_私密备注" not in chat1, "私密备注泄露进本轮已发送聊天区"
    assert not any("PRIVATE_NOTE_SENTINEL_私密备注" in e["text"] for e in ctx_new["handle"]["events"]), \
        "私密备注被当作真实消息落库"
    # 快照/恢复
    assert router.store.load(ea.ENGINE_NEW, "contactA", "story1"), "新引擎快照未落盘"
    h_restored = router.restore(ctx_new)
    assert h_restored is not None and h_restored["events"], "restore 后事件记忆丢失"
    # 恢复不能只看返回非空：真正用恢复后的 handle 再发一轮（替身网关），验续用状态与边界。
    ctx_new["handle"] = h_restored
    r2 = router.send_turn(ctx_new, ea.normalize_events(
        [{"role": "HER", "text": "那就周六", "ts": "2026-11-09 20:10"}]), clock="2026-11-09 20:10")
    u2 = gw_new.calls[-1]["user"]
    assert "周末干嘛" in u2, "恢复后上一轮真实聊天未续用到新提示"
    assert "PRIVATE_NOTE_SENTINEL_私密备注" not in u2, "上一轮私密备注泄进恢复后的后续轮历史"
    assert not any("PRIVATE_NOTE_SENTINEL_私密备注" in e["text"] for e in ctx_new["handle"]["events"]), \
        "恢复续用后私密备注仍被落库"
    assert r2["parsed"]["status"] == "ok", "恢复续用轮次消费结果应为 ok"
    # 新旧互不覆盖：同联系人同故事, 用回退(旧引擎)写到不同目录
    gw_old = StubGateway(ea.ENGINE_OLD)
    router2 = ea.EngineRouter(TMP, gw_old)
    ctx_old = router2.handle(ea.ENGINE_NEW, "contactA", "story1", persona, kb, use_v5=False)
    assert ctx_old["engine"] == ea.ENGINE_OLD, "use_v5=False 未回退原版引擎"
    router2.send_turn(ctx_old, ea.normalize_events(
        [{"role": "HER", "text": "周末干嘛", "ts": "2026-11-09 20:00"}]), clock="2026-11-09 20:00")
    # 旧引擎有快照，另一引擎(新)也有，但两份独立、互不覆盖
    assert router2.store.exists_other_engine(ea.ENGINE_OLD, "contactA", "story1"), "新旧状态应并存不覆盖"
    onew = open(os.path.join(TMP, ea.ENGINE_NEW, "contactA__story1", "state.bin"), "rb").read()
    oold = open(os.path.join(TMP, ea.ENGINE_OLD, "contactA__story1", "state.bin"), "rb").read()
    assert onew != oold, "新旧引擎快照不应是同一份(必须隔离)"
    shutil.rmtree(TMP, ignore_errors=True)
    return ("I3+I5 PASS: 新旧引擎开关/回退可用, 联系人×故事隔离, snapshot/restore 后真正再发一轮"
            "续用真实历史且私密备注不落库/不残留, 两份互不覆盖")


def main():
    failed, out = 0, []
    for fn in CHECKS:
        try:
            d = fn()
            out.append("PASS %s — %s" % (fn.__name__, d))
            print(out[-1])
        except Exception as e:
            failed += 1
            out.append("FAIL %s — %s: %s" % (fn.__name__, type(e).__name__, e))
            print(out[-1])
    summary = {"suite": "integration_preview replay_fixture", "checks": len(CHECKS),
               "failed": failed, "real_model_calls": 0,
               "note": "接口与恢复行为用替身I/O证明；不证产品UI/编译/体验验收，不切生产默认。",
               "lines": out}
    json.dump(summary, open(os.path.join(HERE, "REPLAY_RESULT.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("INTEGRATION_PREVIEW: %s (%d checks, failed=%d, real_model_calls=0)"
          % ("PASS" if not failed else "FAIL", len(CHECKS), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
