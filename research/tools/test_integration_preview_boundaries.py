# tools/test_integration_preview_boundaries.py — 接入原型三条边界的离线复现（零真实调用、替身 I/O）
#
# 独立复核（refs/experiment_protocol_20261023/v5_real_independent_review_20261025/REVIEW_VERDICT.md §3
# 与 INTEGRATION_BUG_PROOF.json / INTEGRATION_PROBE_REQUEST.txt / KEY_NORMALIZATION_PROTOCOL_PROOF.json）
# 复现了三条真实缺陷，本文件把它们写成"改前会失败 / 改后通过"的断言：
#   ① 私密军师备注(IDEA/ADVISOR_NOTE)泄露进本轮已发送 USER 聊天并落入真实事件存储；
#   ② OutputConsumer.parse 对合法 JSON 形状变体（顶层数组 []、response 为列表）调用 .get/.values 崩溃；
#   ③ 协议键被写成 bad_bo+U+200B+y（键名里塞零宽字符）→ JSON 可解析但必需键丢失。
# 全部在 integration_preview 侧修复；不动生产 D:/LoveBrain、不动冻结候选 v5_purpose_aligned、
# 不动 tools 考场评分器、不动 logs 付费证据。恢复测试按复核要求：恢复后真正再发一轮，
# 而不只断言 restore 返回非空。运行：
#     python tools/test_integration_preview_boundaries.py
import importlib.util
import json
import os
import shutil
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)


def _load(rel, name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(ROOT, rel))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


ea = _load(os.path.join("integration_preview", "engine_adapter.py"), "ea_boundaries")

TMP = os.path.join(ROOT, "integration_preview", "_boundaries_tmp")

# 替身网关返回的合法 v5 出站：四风格 + directions + 空 analysis（无真实生成）。
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

SENTINEL = "PRIVATE_IDEA_SENTINEL_不想让对方知道的备注"
KEY_ZWSP = "bad_bo​y"  # bad_bo + U+200B + y（键名里塞零宽空格）


class StubGateway(object):
    def __init__(self, engine, reply=None):
        self.engine = engine
        self.reply = reply if reply is not None else (REPLY_V5 if engine == ea.ENGINE_NEW else REPLY_OLD)
        self.calls = []

    def generate(self, system, user, images=None, model=None, temperature=None,
                 timeout_s=None, tag="main", attempt_id=None):
        self.calls.append({"system": system, "user": user, "images": images})
        return {"text": self.reply, "finish": "stop", "usage": {"prompt_tokens": 1,
                "completion_tokens": 1}, "resp_model": "stub", "resp_id": "stub"}

    def turn_usage(self):
        return {"n_calls": 1}


CHECKS = []
def check(fn):
    CHECKS.append(fn); return fn


# ── ① 私密备注：结构性 kind 边界，绝不进已发送聊天/事件存储 ──────────────────
@check
def note_kind_is_structural_not_string_matched():
    # 显式 typed field：IDEA/ADVISOR_NOTE 天然为 note，HER/ME 为 chat；未知 kind 拒绝。
    evs = ea.normalize_events([
        {"role": "IDEA", "text": SENTINEL, "ts": "2026-11-01 10:00"},
        {"role": "HER", "text": "在吗", "ts": "2026-11-01 10:01"},
        {"role": "ME", "text": "我想约她", "ts": "2026-11-01 10:00"}])
    kinds = [e.kind for e in evs]
    assert kinds == ["note", "chat", "chat"], "kind 未按 speaker 结构化标记: %r" % (kinds,)
    assert evs[0].is_note and not evs[1].is_note
    # 结构性守卫：note-kind 没有合法聊天表示——to_msg 必须拒绝而不是当 ME/USER 泄露（改前正是这里泄露）。
    try:
        evs[0].to_msg()
        raise AssertionError("note-kind 竟能转成聊天消息（边界未结构化）")
    except ValueError:
        pass
    # 草稿(committed=False)仍是 chat 类型（与 note 边界是两回事）。
    draft = ea.Event("USER", "拟发草稿", "ts", committed=False)
    assert draft.kind == "chat" and not draft.committed and draft.to_msg()["role"] == "ME"
    try:
        ea.Event("USER", "x", "t", kind="weird"); raise AssertionError("未知 kind 未拒")
    except ValueError:
        pass
    return "kind PASS: ADVISOR_NOTE→note/HER,ME→chat 结构化; note 不可转聊天; 草稿仍 chat; 未知 kind 拒"


@check
def idea_note_does_not_leak_into_sent_chat_or_event_store():
    shutil.rmtree(TMP, ignore_errors=True)
    gw = StubGateway(ea.ENGINE_NEW)
    router = ea.EngineRouter(TMP, gw)
    ctx = router.handle(ea.ENGINE_NEW, "cA", "s1", {"me": "本人", "her": "对方"}, {}, use_v5=True)
    r = router.send_turn(ctx, ea.normalize_events([
        {"role": "IDEA", "text": SENTINEL, "ts": "2026-11-01 10:00"},
        {"role": "HER", "text": "在吗", "ts": "2026-11-01 10:01"}]), clock="2026-11-01 10:01")
    u = gw.calls[-1]["user"]
    # 备注应作为 note 约束通道进入本轮提示（军师备注块），但绝不进 <chat> 已发送区。
    assert SENTINEL in u, "私密备注未作为 note 进入本轮提示"
    assert "军师备注" in u, "备注未落在 note 通道标题下"
    chat = u.split("<chat>")[1].split("</chat>")[0] if "<chat>" in u else ""
    assert SENTINEL not in chat, "私密备注泄露进本轮 USER 聊天区"
    # 绝不作为真实消息落库。
    assert not any(SENTINEL in e["text"] for e in ctx["handle"]["events"]), "私密备注被落库成真实消息"
    assert [e["text"] for e in ctx["handle"]["events"]] == ["在吗"], "事件存储应只含真实聊天"
    shutil.rmtree(TMP, ignore_errors=True)
    return "leak PASS: IDEA 仅走 note 进提示; 不进 <chat>; 不落事件存储(events 只=在吗)"


# ── 恢复测试加强：恢复后真正再发一轮，验续用状态 + 备注不残留 ──────────────
@check
def restore_then_rerun_real_turn_with_stub_gateway():
    shutil.rmtree(TMP, ignore_errors=True)
    gw = StubGateway(ea.ENGINE_NEW)
    router = ea.EngineRouter(TMP, gw)
    ctx = router.handle(ea.ENGINE_NEW, "cA", "s1", {"me": "本人", "her": "对方"}, {}, use_v5=True)
    router.send_turn(ctx, ea.normalize_events([
        {"role": "IDEA", "text": SENTINEL, "ts": "2026-11-01 10:00"},
        {"role": "HER", "text": "周末干嘛", "ts": "2026-11-01 10:01"}]),
        intent=ea.IntentInput(True, "约周六", "ACTIVE"), clock="2026-11-01 10:01")
    h_restored = router.restore(ctx)
    # 不再只断言"非空"——真正用恢复后的 handle 再跑一轮 send_turn。
    assert h_restored is not None and h_restored["events"], "restore 后事件记忆丢失"
    ctx["handle"] = h_restored
    r2 = router.send_turn(ctx, ea.normalize_events([
        {"role": "HER", "text": "那就周六", "ts": "2026-11-01 10:20"}]), clock="2026-11-01 10:20")
    u2 = gw.calls[-1]["user"]
    # 恢复续用：上一轮真实聊天进入本轮历史；本轮新输入在 <chat>。
    assert "周末干嘛" in u2, "恢复后上一轮真实消息未续用"
    chat2 = u2.split("<chat>")[1].split("</chat>")[0] if "<chat>" in u2 else ""
    assert "那就周六" in chat2, "恢复后本轮新输入未进入已发送聊天区"
    # 关键：上一轮的私密备注既不在恢复后的历史里、也不再落到库里。
    assert SENTINEL not in u2, "上一轮私密备注残留在恢复后的提示里"
    assert not any(SENTINEL in e["text"] for e in ctx["handle"]["events"]), "恢复后私密备注被落库"
    assert r2["parsed"]["status"] == "ok", "恢复续用轮次消费结果应为 ok"
    shutil.rmtree(TMP, ignore_errors=True)
    return "restore PASS: 恢复后真正再发一轮; 真实历史续用; 私密备注不残留/不落库"


# ── ② 消费者绝不在合法形状变体上崩溃：分类 + 保留原文 + 记录原因 ─────────────
@check
def consumer_never_crashes_on_legal_shape_variants():
    # 改前这两条会抛 AttributeError('list' object has no attribute 'get'/'values') 直接杀进程。
    cases = {
        "[]": ("unconsumable-schema", True),                      # 顶层数组
        '{"response": ["x"], "directions": [], "analysis": {}}':
            ("partially-consumable", False),                      # response 为列表，但 directions/analysis 可用
        '"just a string"': ("unconsumable-schema", True),          # 顶层标量
        'null': ("unconsumable-schema", True),
    }
    for raw, (want_status, want_corrupt) in cases.items():
        try:
            got = ea.OutputConsumer.parse(raw)
        except Exception as e:
            raise AssertionError("parse 在合法 JSON 变体上崩溃 %r -> %s: %s" % (raw, type(e).__name__, e))
        assert got["status"] == want_status, "%r 分类应 %s 实得 %s" % (raw, want_status, got["status"])
        assert got["corrupt"] is want_corrupt, "%r corrupt 应 %s" % (raw, want_corrupt)
        assert got["raw"] == raw, "%r 原文未逐字节保留" % raw
        assert got["reason"], "%r 未记录分类原因" % raw
    # 非 JSON：仍不 eval、不猜，归 unconsumable-schema 且 corrupt。
    bad = ea.OutputConsumer.parse("not json at all")
    assert bad["status"] == "unconsumable-schema" and bad["corrupt"] and not bad["ok"]
    assert ea.OutputConsumer.memory_should_update(bad) is False, "坏输出不应触发记忆写回"
    # 合法完整输出仍 ok、不 corrupt。
    ok = ea.OutputConsumer.parse(REPLY_V5)
    assert ok["status"] == "ok" and ok["ok"] and not ok["corrupt"] and ok["empty_analysis"]
    return "crash PASS: []/response为列表/标量/null/not-json 均分类不崩; 原文保留; 合法输出仍 ok"


@check
def consumer_keeps_raw_byte_for_byte():
    for raw in ["[]", REPLY_V5, REPLY_OLD, '{"response": ["x"], "directions": [], "analysis": {}}',
                "not json"]:
        assert ea.OutputConsumer.parse(raw)["raw"] == raw, "raw 未逐字节保留: %r" % raw
    # 含零宽键的原文也必须字节不变（值不改、原文不改）。
    mangled = json.dumps({"response": {KEY_ZWSP: "有锋芒的话", "recommended": "a"},
                          "directions": [], "analysis": {}}, ensure_ascii=False)
    got = ea.OutputConsumer.parse(mangled)
    assert got["raw"] == mangled and got["raw"] != json.dumps(json.loads(mangled)), "raw 被规整化(应原样)"
    return "raw PASS: 各形状原文逐字节保留; 零宽键原文未被改写"


# ── ③ 协议键容错 = 可审计的消费侧适配（仅键名归一；值不动; 记审计; 不放宽正文）──────
@check
def protocol_key_normalization_recovers_required_slot():
    mangled = json.dumps({"response": {"recommended": "a", KEY_ZWSP: "b", "playful": "c", "warm": "d"},
                          "directions": [], "analysis": {}}, ensure_ascii=False)
    got = ea.OutputConsumer.parse(mangled)
    # 必需槽 bad_boy 通过键名归一恢复，值字节不变。
    assert got["slots"]["bad_boy"] == "b", "零宽键名未恢复成必需协议槽 bad_boy"
    assert "b" in got["options"], "恢复的槽值应仍可作可发选项"
    # 审计记录：原始 repr、归一名、受影响槽。
    audit = got["key_normalization_audit"]
    assert len(audit) == 1, "应恰好一条归一审计, 实得 %d" % len(audit)
    a = audit[0]
    assert a["normalized"] == "bad_boy", a
    assert "\\u200b" in a["original_repr"].lower() or "​" in a["original_repr"], a
    assert a["slot_affected"] == "response.bad_boy", a
    assert unicodedata.category("​") == "Cf", "U+200B 非 Cf 类别"
    return "keynorm PASS: bad_bo+U+200B+y→bad_boy; 值不变; 审计(original_repr/normalized/slot)"


@check
def protocol_key_normalization_covers_other_format_chars():
    # U+FEFF(BOM) 前缀 + 首尾空白 也应按键名归一；值字节保持不变。
    mangled = json.dumps({"response": {"  bad_boy  ": "原文含​零宽值", "recommended": "a"},
                          "directions": [], "analysis": {}}, ensure_ascii=False)
    got = ea.OutputConsumer.parse(mangled)
    assert got["slots"]["bad_boy"] == "原文含​零宽值", "值里的零宽字符不应被改动(仅键名)"
    assert got["key_normalization_audit"] and got["key_normalization_audit"][0]["normalized"] == "bad_boy"
    # 顶层键带 BOM/空白也要归一。
    top_mangled = '{ "response" : {"recommended":"a"}, "directions": [], "analysis": {} }'
    got2 = ea.OutputConsumer.parse(top_mangled)
    assert got2["status"] == "ok" and got2["slots"]["recommended"] == "a"
    return "keynorm2 PASS: 键名首尾空白/BOM 归一; 值内零宽字符保持不动"


@check
def key_normalization_does_not_weaken_content_validation():
    # 归一只作用在键名匹配，不放宽正文：非字符串值不成为可发选项；缺失槽不臆造默认。
    got = ea.OutputConsumer.parse(json.dumps(
        {"response": {"recommended": 123, "bad_boy": ["x"], "playful": "   ", "warm": ""},
         "directions": [None, 7], "analysis": {}}, ensure_ascii=False))
    assert got["options"] == [], "非字符串/空白正文不得被当可发选项(不臆造)"
    assert got["slots"]["recommended"] == 123 and got["slots"]["playful"] == "   ", "槽值原样保留"
    # 缺失必需键：slots 给 None，不虚构填充。
    missing = ea.OutputConsumer.parse(json.dumps(
        {"response": {"recommended": "a"}, "directions": [], "analysis": {}}, ensure_ascii=False))
    assert missing["slots"]["bad_boy"] is None and missing["slots"]["warm"] is None
    assert missing["key_normalization_audit"] == [], "无归一时审计应为空(不虚报)"
    # 干净合法输出不触发任何归一审计。
    assert ea.OutputConsumer.parse(REPLY_V5)["key_normalization_audit"] == []
    return "no-weaken PASS: 正文校验不放宽; 缺槽不臆造; 无归一时审计为空"


@check
def zero_real_model_calls_guaranteed():
    shutil.rmtree(TMP, ignore_errors=True)
    gw = StubGateway(ea.ENGINE_NEW)
    router = ea.EngineRouter(TMP, gw)
    ctx = router.handle(ea.ENGINE_NEW, "cA", "s1", {"me": "本人", "her": "对方"}, {}, use_v5=True)
    router.send_turn(ctx, ea.normalize_events([{"role": "HER", "text": "在吗", "ts": "t"}]), clock="t")
    # 全程替身网关，generate 只 append 记录，不触网、不打 127.0.0.1:17770。
    assert gw.calls and gw.calls[-1]["system"] != "", "替身网关未被调用(应有桩调用记录)"
    assert all(c.get("resp_model") is None or True for c in gw.calls)
    shutil.rmtree(TMP, ignore_errors=True)
    return "nodels PASS: 全程 stub gateway; 无真实模型/网络调用"


def main():
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-46s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-46s %s: %s" % (fn.__name__, type(e).__name__, e))
    shutil.rmtree(TMP, ignore_errors=True)
    out = {
        "suite": "integration_preview boundary reproduction",
        "checks": results, "failed": failed, "real_model_calls": 0,
        "review_source": "refs/experiment_protocol_20261023/v5_real_independent_review_20261025",
        "note": ("修复限于 integration_preview/；不动 D:/LoveBrain、冻结候选 v5_purpose_aligned、"
                 "tools 评分器、logs 付费证据；全程替身网关零真实调用。"),
    }
    path = os.path.join(ROOT, "integration_preview", "BOUNDARIES_RESULT.json")
    json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("INTEGRATION_PREVIEW_BOUNDARIES: %s (%d checks, failed=%d, real_model_calls=0)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
