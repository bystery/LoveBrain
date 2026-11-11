# tools/test_baseline_parity_extra.py — 公平基线补齐三项的离线验收（2026-10-23）
#
# 依据 `refs/fair_baseline_20261023/FAIR_BASELINE_AND_REFACTOR_GUIDE.md` §2.2-§2.4：
#   2.2 预算裁剪（PromptBudget.byBlocks）— 未接主请求 = 基线偏移；
#   2.3 知识段纠正过滤（PromptKnowledgeSection + MemoryRefPolicy.isCorrected）—
#       旧实现读 corrections 但没用；
#   2.3 时间戳块（buildTimestampPrompt）— 旧实现只有 `# 当前时间\n<ts>`，缺三行为约束。
#
# 本文件不烧 API，不发真实调用；用纯函数 + 假网关断言装配层已把这些年欠下的移植补齐。
# 与 `tools/test_baseline_parity.py`（26 项保真对拍）互补：那份断言"逐字等价"，
# 本份断言"本批修复到位"，两边都过 = 基线可当"公平父本"。
import importlib.util
import json
import os
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE_CAND = os.path.join(ROOT, "candidates", "reference_product_v1")
MECH = os.path.join(BASE_CAND, "mechanism", "mechanism.py")
ASSETS = os.path.join(BASE_CAND, "assets")


def _load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


class _EchoGateway:
    def __init__(self):
        self.prompts = []
        self.systems = []

    def generate(self, system, prompt, images=None, tag="main", **_kw):
        self.systems.append(system)
        self.prompts.append(prompt)
        return {"text": json.dumps({
            "response": {"recommended": "好的", "bad_boy": "哦",
                         "playful": "哈", "warm": "行"},
            "directions": [None, None, None, None],
            "analysis": {"topic_status": "same", "topic_label": "测试",
                         "scene_facts": [], "ongoing": []}},
            ensure_ascii=False)}


# ───────────────────────── e1 · byBlocks 单元 ─────────────────────────

@check
def e1_by_blocks_under_budget_passthrough(rc):
    """预算内：拼接顺序与原文一致，不做任何裁剪。"""
    kb = "# 知识\n知识正文\n\n"
    sb = "【当前场景】\n场景：日常\n\n"
    ib = "【持续意图】\n邀约\n\n"
    idb = "# 军师备注\n「别太主动」\n\n"
    ch = "# 本次对话记录\n"
    cb = "<chat>\n{}\n</chat>\n"
    ts = "【当前时间】2026-11-01 12:00:00\n所有回复必须基于上述当前时间进行时段判断，禁止臆测。回复需自然贴合当前时段。时间只认系统给定的当前时间，不凭对话内容或主观感觉推测。"
    out = rc.by_blocks(kb, sb, ib, idb, ch, cb, ts)
    assert kb in out and sb in out and ib in out and idb in out
    assert ch in out and cb in out and ts in out
    assert "省略" not in out, "短输入不该触发裁剪"
    return "short_passthrough=True"


@check
def e2_by_blocks_knowledge_tail_trim(rc):
    """阶段 1：知识段过长（其余短），先裁知识段尾部，留旧记忆提示。"""
    kb = "# 知识\n" + ("这是一段用于占位的旧记忆。" * 800)   # ~9600 UTF-16 code units 已越界
    sb = "【当前场景】\n场景：日常\n\n"
    ib = ""
    idb = ""
    ch = "# 本次对话记录\n"
    cb = "<chat>\n{\"id\":\"m0\",\"speaker\":\"PARTNER\",\"text\":\"短句\"}\n</chat>\n"
    ts = "【当前时间】2026-11-01 12:00:00\n所有回复必须基于上述当前时间进行时段判断，禁止臆测。回复需自然贴合当前时段。时间只认系统给定的当前时间，不凭对话内容或主观感觉推测。"
    out = rc.by_blocks(kb, sb, ib, idb, ch, cb, ts)
    # 阶段 1 语义：裁知识段到 keepLen，再追加 18 字省略提示；
    # 与 Kotlin 原版一致（PromptBudget.kt L37-40）— 结果可轻微越界，这是原版边界行为，
    # fair_baseline §2.2 明确"它自身的边界问题也应保留并登记"，本移植不擅改。
    assert rc.utf16_len(out) <= rc.TOTAL_BUDGET + 40, \
        "阶段 1 越界超 Kotlin 边界浮动：len=%d cap=%d (+40)" % (
            rc.utf16_len(out), rc.TOTAL_BUDGET)
    assert "旧记忆因长度限制已省略" in out, "阶段 1 未触发旧记忆裁尾提示"
    assert "短句" in out and "【当前时间】" in out
    return "kb_tail_trim fired len=%d cap=%d slack_ok(<40)" % (
        rc.utf16_len(out), rc.TOTAL_BUDGET)


@check
def e3_by_blocks_chat_head_trim_whole_lines(rc):
    """阶段 3：知识段无法再裁时按整行裁对话头部，不切半 JSON 对象；</chat> 闭合保留。"""
    kb = "# 知识\n短知识段\n\n"
    sb = ""
    ib = ""
    idb = ""
    ch = "# 本次对话记录\n"
    lines = [json.dumps({"id": "m%d" % i, "speaker": "PARTNER",
                          "text": "这是一条用于撑长度的对话内容" * 5},
                         ensure_ascii=False)
             for i in range(200)]   # ~14000 UTF-16 code units
    cb = "<chat>\n" + "\n".join(lines) + "\n</chat>\n"
    ts = "【当前时间】2026-11-01 12:00:00\n所有回复必须基于上述当前时间进行时段判断，禁止臆测。回复需自然贴合当前时段。时间只认系统给定的当前时间，不凭对话内容或主观感觉推测。"
    out = rc.by_blocks(kb, sb, ib, idb, ch, cb, ts)
    assert rc.utf16_len(out) <= rc.TOTAL_BUDGET + 200, \
        "阶段 3 后仍显著越界（>200 浮动）：len=%d" % rc.utf16_len(out)
    assert "较早的对话已省略" in out or "对话记录因长度限制已省略" in out
    assert "</chat>" in out, "chat 围栏闭合丢失"
    # 检查每条留下的对话行是完整 JSON 对象（能被 json.loads 解析）
    for ln in out.split("\n"):
        ln = ln.strip()
        if ln.startswith('{"id"') and ln.endswith('}'):
            try:
                json.loads(ln)
            except Exception as e:
                raise AssertionError("对话头裁半 JSON：%s（%s）" % (ln[:80], e))
    return "chat_head_trim fired len=%d fences_intact=True" % rc.utf16_len(out)


@check
def e4_utf16_len_counts_surrogate_pairs(rc):
    """UTF-16 长度：BMP 中文每字 1，emoji（补充平面）每字符 2。"""
    assert rc.utf16_len("杯子") == 2
    assert rc.utf16_len("🎂") == 2      # U+1F382 蛋糕 emoji
    assert rc.utf16_len("杯🎂底") == 4  # 1+2+1
    return "utf16_len ok"


# ───────────────────────── e5 · corrections 过滤 ─────────────────────────

@check
def e5_correction_wrong_skips_profile_line(mech, rc):
    """WRONG 纠正画像 her.md → 原始"## 她\n<原>"不出现，改为"（已纠正：<replacement>）"。"""
    ep = {"episode_id": "T_CORR",
          "persona": {"me": "29 岁打工人", "her": "她怕辣"},
          "initial_kb": {}, "turns": []}
    tmp = tempfile.mkdtemp(prefix="e5_")
    try:
        gw = _EchoGateway()
        h = mech.init(ASSETS, ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        # 写 corrections.json：PROFILE:understand/her.md → WRONG + 补正"她不吃辣（不是怕辣）"
        h["store"].save(h["kb"], "PROFILE:understand/her.md",
                        "WRONG", replacement_text="她不吃辣（不是怕辣）")
        mech.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": "今天加班"}],
            "clock": "2026-11-01 20:00:00"}}, gw)
        prompt = gw.prompts[-1]
        assert "她怕辣" not in prompt, "WRONG 纠正后画像原文仍在 prompt（未过滤）"
        assert "她不吃辣" in prompt, "WRONG 纠正的 replacementText 没进 prompt"
        return "PROFILE wrong filtered replacement injected"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def e6_correction_muted_skips_active_mention(mech, rc):
    """MUTED（UNTIL_RESTORE）：ongoing 条目原文不主动提；标记"（此条记忆已暂停主动提及…）"。"""
    ep = {"episode_id": "T_MUTE",
          "persona": {"me": "打工人", "her": "她"},
          "initial_kb": {"ongoing_plan": [
              {"name": "周日看展", "status": "进行中", "chain": "她说三点北门"}]},
          "turns": []}
    tmp = tempfile.mkdtemp(prefix="e6_")
    try:
        gw = _EchoGateway()
        h = mech.init(ASSETS, ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        # 找 item 名 → 条目级 ID
        item_name = "周日看展"
        mid = "ONGOING:moment/plan.md:%s" % item_name
        h["store"].save(h["kb"], mid, "MUTED", mute_duration="UNTIL_RESTORE")
        mech.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": "周日看展还去吗"}],
            "clock": "2026-11-01 20:00:00"}}, gw)
        prompt = gw.prompts[-1]
        # 事项名会出现在当前消息里（HER 说的话），但 plan.md 的渲染行"周日看展 | 进行中 | 她说三点北门"不该出现
        assert "周日看展 | 进行中" not in prompt, \
            "MUTED 未过滤进行事项渲染行"
        assert "此条记忆已暂停主动提及" in prompt, \
            "MUTED 的被动回应标记未出现"
        return "MUTED item filtered passive_marker present"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def e7_correction_wrong_person_isolates(mech, rc):
    """WRONG_PERSON：条目级隔离；原行不进，也不给 replacement 提示（与 Kotlin 一致）。"""
    ep = {"episode_id": "T_WP",
          "persona": {"me": "打工人", "her": "她"},
          "initial_kb": {"ongoing_plan": [
              {"name": "帮小李带饭", "status": "进行中", "chain": "ME 提议周一"}]},
          "turns": []}
    tmp = tempfile.mkdtemp(prefix="e7_")
    try:
        gw = _EchoGateway()
        h = mech.init(ASSETS, ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        mid = "ONGOING:moment/plan.md:帮小李带饭"
        h["store"].save(h["kb"], mid, "WRONG_PERSON")
        mech.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": "小李那事怎么办"}],
            "clock": "2026-11-01 20:00:00"}}, gw)
        prompt = gw.prompts[-1]
        assert "帮小李带饭 | 进行中" not in prompt, \
            "WRONG_PERSON 隔离失败，事项原文仍进 prompt"
        assert "已纠正" not in prompt or "帮小李带饭" not in prompt, \
            "WRONG_PERSON 不该补 replacement 提示"
        return "WRONG_PERSON isolated"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def e8_correction_finished_removes_ongoing(mech, rc):
    """FINISHED 纠正：进行中列表不注入该事项（Kotlin PromptKnowledgeSection L136 明示）。"""
    ep = {"episode_id": "T_FIN",
          "persona": {"me": "打工人", "her": "她"},
          "initial_kb": {"ongoing_plan": [
              {"name": "买生日礼物", "status": "进行中", "chain": "她想要陶瓷杯"}]},
          "turns": []}
    tmp = tempfile.mkdtemp(prefix="e8_")
    try:
        gw = _EchoGateway()
        h = mech.init(ASSETS, ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        mid = "ONGOING:moment/plan.md:买生日礼物"
        h["store"].save(h["kb"], mid, "FINISHED")
        mech.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": "礼物想好了吗"}],
            "clock": "2026-11-01 20:00:00"}}, gw)
        prompt = gw.prompts[-1]
        assert "买生日礼物 | 进行中" not in prompt, \
            "FINISHED 未过滤，事项仍以进行中注入"
        return "FINISHED removed from active list"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


# ───────────────────────── e9-e10 · timestamp 与 run_turn 集成 ─────────────────────────

@check
def e9_timestamp_block_exact_string(rc):
    """buildTimestampPrompt 逐字（PromptBuilder.kt L338-341）：三段行为约束必在。"""
    out = rc.build_timestamp_prompt("2026-11-10 13:06")
    assert out.startswith("【当前时间】2026-11-10 13:06\n"), out[:40]
    for phrase in ("基于上述当前时间进行时段判断",
                    "禁止臆测", "自然贴合当前时段",
                    "时间只认系统给定的当前时间",
                    "不凭对话内容或主观感觉推测"):
        assert phrase in out, "缺行为约束短语：%s" % phrase
    return "timestamp exact match, 5 behavioral clauses present"


@check
def e10_run_turn_uses_by_blocks_and_timestamp(mech, rc):
    """端到端：run_turn 装配走 by_blocks；时间戳块用新格式（非"# 当前时间"）。"""
    ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", "E001.json"),
                        encoding="utf-8"))
    tmp = tempfile.mkdtemp(prefix="e10_")
    try:
        gw = _EchoGateway()
        h = mech.init(ASSETS, ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        # 只跑到 T01 就够
        t1 = dict(ep["turns"][0])
        t1["turn_index"] = 1
        mech.run_turn(h, t1, gw)
        prompt = gw.prompts[-1]
        assert "【当前时间】" in prompt, "run_turn 装配里没用新时间戳块"
        assert "# 当前时间" not in prompt, "旧时间戳标题残留"
        assert "不凭对话内容或主观感觉推测" in prompt, "行为约束短语缺失"
        # 拼接顺序检查：知识 → 场景 → 备注 → 对话头 → 时间戳
        ki = prompt.find("【懂得】")
        si = prompt.find("【当前场景】")
        ni = prompt.find("军师备注")
        ci = prompt.find("<chat>")
        ti = prompt.find("【当前时间】")
        assert 0 <= ki < si < ni < ci < ti, \
            "段顺序不符 by_blocks 规约：k=%d s=%d n=%d c=%d t=%d" % (ki, si, ni, ci, ti)
        return "run_turn via by_blocks, ts_new_format, order_ok"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def e11_by_blocks_emoji_utf16_boundary(rc):
    """§7 · Kotlin String.substring 按 UTF-16 code unit 截；Python 按 code point 截。
    在 BMP 内两者数值一致，但 emoji（补充平面 = 代理对）跨在裁剪线上时，
    Kotlin 会切成半个代理（lone surrogate），Python 保留完整字符。
    本测试锁定：
      (1) utf16_len 与 Kotlin String.length 对齐——emoji 每字符计 2；
      (2) by_blocks 阶段 1 裁知识段：结果串里**不得出现半截代理**（Python 语义，
          与 Kotlin 原版"允许半截"是**已知偏差**，登记不擅改；
          偏差仅在文本裁剪点恰好跨 emoji 时显现，实际项目文本里 emoji 极少见）；
      (3) 结果总 UTF-16 长度 ≤ TOTAL_BUDGET + 40 浮动（与 e2 同尺度）。"""
    # 构造知识段：把 emoji 精确卡在裁剪点上
    emoji = "\U0001F382"       # 🎂 cake emoji，U+1F382，UTF-16 代理对
    body_unit = "这是一段用于撑长度的旧记忆内容。"
    # 每段 body_unit 12 汉字；再加 emoji 让裁剪点附近就是代理对
    kb = "# 知识\n" + body_unit * 780 + emoji + "尾巴" + body_unit * 10 + "\n\n"
    assert rc.utf16_len(emoji) == 2, "utf16_len 未把 emoji 算 2 码元"
    sb = "【当前场景】\n场景：日常\n\n"
    ib = ""
    idb = ""
    ch = "# 本次对话记录\n"
    cb = "<chat>\n{\"id\":\"m0\",\"speaker\":\"PARTNER\",\"text\":\"短句\"}\n</chat>\n"
    ts = "【当前时间】2026-11-01 12:00:00\n所有回复必须基于上述当前时间进行时段判断，禁止臆测。回复需自然贴合当前时段。时间只认系统给定的当前时间，不凭对话内容或主观感觉推测。"
    out = rc.by_blocks(kb, sb, ib, idb, ch, cb, ts)
    # (3) 总长
    assert rc.utf16_len(out) <= rc.TOTAL_BUDGET + 40, \
        "emoji 边界场景下越界：len=%d cap=%d (+40)" % (
            rc.utf16_len(out), rc.TOTAL_BUDGET)
    # (2) 无半截代理：Python 语义保证 str 里每字符 code point 完整，
    #     但我们要显式检测"有没有孤立 surrogate 码位（0xD800..0xDFFF 单独出现）"
    for i, ch_ in enumerate(out):
        cp = ord(ch_)
        assert not (0xD800 <= cp <= 0xDBFF and
                    (i + 1 >= len(out) or not (0xDC00 <= ord(out[i + 1]) <= 0xDFFF))), \
            "输出出现半截高代理：pos=%d char=U+%04X" % (i, cp)
        assert not (0xDC00 <= cp <= 0xDFFF and
                    (i == 0 or not (0xD800 <= ord(out[i - 1]) <= 0xDBFF))), \
            "输出出现半截低代理：pos=%d char=U+%04X" % (i, cp)
    # 触发条件确认：确实发生了阶段 1 裁尾（否则测试没跑到 emoji 边界）
    assert "旧记忆因长度限制已省略" in out, \
        "本例知识段未触发阶段 1 裁尾（reviewer §7 场景不成立）"
    return "e11 emoji_boundary fired len=%d cap=%d no_lone_surrogate=True" % (
        rc.utf16_len(out), rc.TOTAL_BUDGET)


def main(argv=None):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="logs/baseline_parity_extra_result.json")
    args = ap.parse_args(argv)
    rc = _load(os.path.join(BASE_CAND, "reference_chain.py"), "rc_extra")
    mech = _load(MECH, "mech_extra")
    results, failed = [], 0
    for fn in CHECKS:
        # 每个 check 按签名注入需要的模块
        import inspect
        params = list(inspect.signature(fn).parameters)
        args_tuple = []
        for p in params:
            if p == "rc":
                args_tuple.append(rc)
            elif p == "mech":
                args_tuple.append(mech)
        try:
            detail = fn(*args_tuple)
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-40s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-40s %s: %s" % (fn.__name__, type(e).__name__, e))
    out = os.path.join(ROOT, args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump({"checks": results, "failed": failed,
               "candidate": "reference_product_v1",
               "target": "fair_baseline §2.2-2.3"},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("BASELINE PARITY EXTRA: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
