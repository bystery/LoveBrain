# tools/test_v2_recall.py — v2 主变量（回忆型检索）的离线验收
#
# 不打网络、不发 API、不计额度：把首场已录制的原始回复当固定输入重放，
# 只看**输入侧**变没变。能证明的：回忆轮 prompt 里终于出现了那条事实；
# 不能证明的：回复会变好（那要新授权实跑，见 EXAM_REPORT §8）。
import argparse
import importlib.util
import json
import os
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE_CAND = os.path.join(ROOT, "candidates", "reference_product_v1")
V2_CAND = os.path.join(ROOT, "candidates", "v2_recall")
V2_MECH = os.path.join(V2_CAND, "mechanism", "mechanism.py")
ASSETS = os.path.join(V2_CAND, "assets")
EXAM = os.path.join(ROOT, "logs", "exam1_20261023")


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class ReplayGateway:
    def __init__(self, texts):
        self.texts = list(texts)
        self.prompts = []

    def generate(self, system, prompt, images=None, tag="main", **_kw):
        self.prompts.append(prompt)
        return {"text": self.texts.pop(0) if self.texts else "{}"}


def recorded_texts(ep_id, turns):
    out = []
    for i, t in enumerate(turns, 1):
        d = os.path.join(EXAM, ep_id, "turn_%02d_%s" % (i, t["case_id"]))
        calls = json.load(open(os.path.join(d, "calls.json"), encoding="utf-8"))
        main = [c for c in calls if c.get("tag", "main") == "main"][0]
        out.append(main.get("raw_text") or "")
    return out


def run(mod, ep, texts, tmp):
    sandbox = os.path.join(tmp, "sandbox")
    gw = ReplayGateway(texts)
    h = mod.init(ASSETS, ep, gw, sandbox)
    outs = []
    for i, t in enumerate(ep["turns"], 1):
        t2 = dict(t)
        t2["turn_index"] = i
        outs.append(mod.run_turn(h, t2, gw))
    return h, gw, outs


CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


@check
def c1_recall_fact_reaches_prompt(mech):
    """E001_T06：她问"上回怎么说的"，prompt 必须含"杯底/刻字/不刻"。"""
    ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", "E001.json"),
                        encoding="utf-8"))
    tmp = tempfile.mkdtemp(prefix="v2_c1_")
    try:
        _h, gw, _o = run(mech, ep, recorded_texts("E001", ep["turns"]), tmp)
        p6 = gw.prompts[5]
        for kw in ("杯底", "刻字", "不刻"):
            assert kw in p6, "T06 prompt 仍缺 %s（回忆事实没被取回）" % kw
        assert "【回忆】" in p6, "T06 没挂【回忆】段"
        return "t06_prompt_has=杯底/刻字/不刻+【回忆】"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c2_no_recall_false_positive(mech):
    """非回忆轮不许塞历史：T01/T02/T03/T05/T07/T08 都不得出现【回忆】段。"""
    ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", "E001.json"),
                        encoding="utf-8"))
    tmp = tempfile.mkdtemp(prefix="v2_c2_")
    try:
        _h, gw, _o = run(mech, ep, recorded_texts("E001", ep["turns"]), tmp)
        bad = [i + 1 for i, p in enumerate(gw.prompts) if "【回忆】" in p]
        assert bad == [6], "只有回忆轮该注入，实际注入轮=%s" % bad
        return "recall_only_on_turn=6"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c3_zero_overlap_no_injection(mech):
    """回忆标记命中但归档里没有相关块 → 不注入（宁可漏，不许灌）。"""
    ep = {"episode_id": "T_ZERO", "persona": {"me": "我", "her": "她"},
          "initial_kb": {}, "turns": []}
    tmp = tempfile.mkdtemp(prefix="v2_c3_")
    try:
        gw = ReplayGateway([json.dumps({
            "response": {"recommended": "嗯", "bad_boy": "哦",
                         "playful": "哈", "warm": "好"},
            "directions": [None, None, None, None],
            "analysis": {"topic_status": "new", "topic_label": "杯子",
                         "scene_facts": [{"text": "她杯子烧好了",
                                          "source_ids": ["m0"]}],
                         "ongoing": []}}, ensure_ascii=False),
            json.dumps({
                "response": {"recommended": "嗯", "bad_boy": "哦",
                             "playful": "哈", "warm": "好"},
                "directions": [None, None, None, None],
                "analysis": {"topic_status": "new", "topic_label": "报销",
                             "scene_facts": [], "ongoing": []}},
                ensure_ascii=False)])
        h = mech.init(ASSETS, ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        mech.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": "杯子烧好了"}],
            "clock": "2026-11-02 19:43"}}, gw)
        mech.run_turn(h, {"turn_index": 2, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": "上次那个报销单你交了吗"}],
            "clock": "2026-11-02 23:43"}}, gw)
        p2 = gw.prompts[1]
        assert "【回忆】" not in p2, "零重合仍注入了历史"
        return "zero_overlap_skipped"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c4_recall_bypass_injects_ongoing(mech):
    """回忆轮：进行中事项不受关键词门限制（否则"陶瓷杯"永远对不上"杯底"）。"""
    ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", "E001.json"),
                        encoding="utf-8"))
    tmp = tempfile.mkdtemp(prefix="v2_c4_")
    try:
        _h, gw, _o = run(mech, ep, recorded_texts("E001", ep["turns"]), tmp)
        base_mech = load(os.path.join(BASE_CAND, "mechanism", "mechanism.py"),
                         "base_mech_c4")
        tmp2 = tempfile.mkdtemp(prefix="v2_c4b_")
        try:
            _h2, gw2, _o2 = run(base_mech, ep, recorded_texts("E001", ep["turns"]), tmp2)
            base_has = "【进行中事项】" in gw2.prompts[5]
            v2_has = "【进行中事项】" in gw.prompts[5]
            assert v2_has and not base_has, \
                "对照失败：父本=%s v2=%s（这条旁路本该只在 v2 生效）" % (base_has, v2_has)
            return "v2_ongoing_injected=True base=False"
        finally:
            shutil.rmtree(tmp2, ignore_errors=True)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c5_e003_untouched(mech):
    """E003 没有回忆型提问 → v2 与父本逐轮 prompt 必须完全一致（不改无关轮）。"""
    ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", "E003.json"),
                        encoding="utf-8"))
    a, b = tempfile.mkdtemp(prefix="v2_c5a_"), tempfile.mkdtemp(prefix="v2_c5b_")
    try:
        base_mech = load(os.path.join(BASE_CAND, "mechanism", "mechanism.py"),
                         "base_mech_c5")
        _h1, g1, _ = run(base_mech, ep, recorded_texts("E003", ep["turns"]), a)
        _h2, g2, _ = run(mech, ep, recorded_texts("E003", ep["turns"]), b)
        diff = [i + 1 for i, (x, y) in enumerate(zip(g1.prompts, g2.prompts))
                if x != y]
        assert not diff, "E003 出现了非预期差异，轮次=%s" % diff
        return "e003_prompts_identical(%d 轮)" % len(g1.prompts)
    finally:
        shutil.rmtree(a, ignore_errors=True)
        shutil.rmtree(b, ignore_errors=True)


class _NullGateway:
    def generate(self, system, prompt, images=None, tag="main", **_kw):
        return {"text": "{}"}


class _RecordingGateway:
    def __init__(self):
        self.prompts = []

    def generate(self, system, prompt, images=None, tag="main", **_kw):
        self.prompts.append(prompt)
        return {"text": json.dumps({
            "response": {"recommended": "嗯", "bad_boy": "哦",
                         "playful": "哈", "warm": "好"},
            "directions": [None, None, None, None],
            "analysis": {"topic_status": "same", "topic_label": "杯子",
                         "scene_facts": [], "ongoing": []}},
            ensure_ascii=False)}


class _SeqGateway:
    def __init__(self, texts):
        self.texts = list(texts)
        self.prompts = []

    def generate(self, system, prompt, images=None, tag="main", **_kw):
        self.prompts.append(prompt)
        return {"text": self.texts.pop(0) if self.texts else "{}"}


@check
def c6_no_cross_message_recall_activation(mech):
    """P1：本轮 send 里"旧句含回忆词 + 新句问别的事" → 不应激活回忆。
    reviewer 反例：HER"之前那件事已经结束了"＋ HER"今天午饭吃什么"。
    父本 is_recall_turn 扫整轮消息命中；v2 只看最后一条真实消息。"""
    msgs = [
        {"id": "t1_0", "role": "HER", "content": "之前那件事已经结束了"},
        {"id": "t1_1", "role": "HER", "content": "今天午饭吃什么"},
    ]
    assert mech.is_recall_turn(msgs) is False, "旧句『之前』误触发当轮回忆（P1 反例失败）"
    msgs2 = [
        {"id": "t2_0", "role": "HER", "content": "最近有点忙"},
        {"id": "t2_1", "role": "HER", "content": "上回那个杯子后来怎么样了？"},
    ]
    assert mech.is_recall_turn(msgs2) is True, "末条真实消息含回忆标记应命中"
    return "P1 false_positive_blocked=True last_marker_still_hits=True"


@check
def c7_no_dormant_whole_dump(mech):
    """P2：30 条无关 DORMANT/ACTIVE 项 + 1 条相关 ACTIVE 项 → 回忆轮 extras 只保留相关的。"""
    ep = {"episode_id": "T_DUMP", "persona": {"me": "我", "her": "她"},
          "initial_kb": {}, "turns": []}
    tmp = tempfile.mkdtemp(prefix="v2_c7_")
    try:
        h = mech.init(os.path.join(ROOT, "candidates", "v2_recall", "assets"),
                      ep, _NullGateway(), sandbox=os.path.join(tmp, "sandbox"))
        lines = ["item_%02d~会议议题%d | 进行中 | 每周同步" % (i, i) for i in range(30)]
        lines.append("item_cup~陶瓷杯烧制取回 | 进行中 | 她决定杯底不刻字")
        # read_plan_active 要求 H2 "## 进行中"；条目行按 `itemId~name|status|chain` 解析
        h["storage"].write_file(h["kb"], "moment/plan.md",
                                "## 进行中\n" + "\n".join(lines))
        h["storage"].write_file(h["kb"], "memory/raw_topic.md",
                                "# [2026-11-02 19:43] 杯子\n"
                                "## [2026-11-02 19:43] 状态变化\n"
                                "### [2026-11-02 19:43] 对话记录\n"
                                "她：店里问要不要刻字，我想杯底留空，不刻了\n")
        recall_msgs = [{"id": "t_x", "role": "HER",
                        "content": "上回那个杯子到底怎么弄来着？"}]
        body = mech.recall_body(h, recall_msgs, topic_label="杯子")
        res = h["ongoing"].select_for_injection(
            h["kb"], recall_msgs, current_turn=5,
            reply_directive=None, effective_intent={}, recall_clue=body)
        extras = res.get("v2RecallExtras") or []
        names = sorted({it["name"] for it in extras})
        assert len(extras) <= 2, \
            "30 条无关 DORMANT/ACTIVE 项被整库灌入：extras=%d names=%s" % (len(extras), names)
        assert "陶瓷杯烧制取回" in names, \
            "唯一相关事项反而没被旁路：extras=%s" % names
        return "P2 extras=%d cap=2 relevant_included=True" % len(extras)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


# ───────── 新增段测量助手（C：看最终请求贡献，单位统一 UTF-16） ─────────
# by_blocks 把 knowledge 段（含父本 ongoing + v2 追加【回忆】）拼在 scene/intent/
# note/chat 之前，【当前时间】在最后。"本轮相对父本新增"的实际内容 = v2 extras 行 +
# 【回忆】段。recall 段在 knowledge 尾部，紧接其后的是 scene_block（若空则由
# intent/对话头/时间块接手）。用下列边界串定位 recall 段终点。
_NEWSEG_STOPS = ("【当前场景】", "【持续意图】", "# 本次对话记录",
                 "【当前时间】", "# 【懂得】", "# 【记忆】", "# 【此刻】")


def _extract_recall_section(prompt):
    i = prompt.find("【回忆】")
    if i < 0:
        return ""
    ends = [prompt.find(m, i + 3) for m in _NEWSEG_STOPS]
    ends = [e for e in ends if e > i]
    j = min(ends) if ends else len(prompt)
    return prompt[i:j].strip()


def _parse_newctx(notes):
    # notes: ... newctx[kept=K dropped=D body=B extras=E new_seg_est=S cap=CAP]
    import re as _re
    m = _re.search(r"newctx\[kept=(\d+) dropped=(\d+) body=(\d+) extras=(\d+) "
                   r"new_seg_est=(\d+) cap=(\d+)\]", notes or "")
    if not m:
        return None
    return {"kept": int(m.group(1)), "dropped": int(m.group(2)),
            "body": int(m.group(3)), "extras": int(m.group(4)),
            "new_seg_est": int(m.group(5)), "cap": int(m.group(6))}


@check
def c8_total_new_injection_budget(mech):
    """P3+C：归档 body 远超上限时，最终请求里的新增段（【回忆】段 + v2 extras 行）
    按 UTF-16 码元 ≤ MAX_NEW_CONTEXT_CHARS；不再用 +400/+2200 混称。"""
    ep = {"episode_id": "T_BUD", "persona": {"me": "我", "her": "她"},
          "initial_kb": {}, "turns": []}
    tmp = tempfile.mkdtemp(prefix="v2_c8_")
    try:
        gw = _RecordingGateway()
        h = mech.init(os.path.join(ROOT, "candidates", "v2_recall", "assets"),
                      ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        big = ("# [2026-11-02] 长归档\n"
               + ("她：陶瓷杯烧制取回细节描述一遍，杯底要留空。\n" * 60))
        h["storage"].write_file(h["kb"], "memory/raw_topic.md", big)
        h["storage"].write_file(h["kb"], "moment/plan.md", "## 进行中\n")
        out = mech.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": "上回那个陶瓷杯烧制的细节你还有印象吗"}],
            "clock": "2026-11-10 12:00"}}, gw)
        prompt = gw.prompts[-1]
        assert "【回忆】" in prompt, \
            "P3 反例未触发 recall（消息与归档 bigram 不重合），无法验证预算"
        nc = _parse_newctx(out["notes"])
        assert nc is not None, "notes 缺 newctx 字段：%s" % out["notes"]
        seg = _extract_recall_section(prompt)
        seg_len = mech.rc.utf16_len(seg)
        cap = mech.MAX_NEW_CONTEXT_CHARS      # 1800，无 +400
        assert nc["new_seg_est"] <= cap, \
            "新增段估算越界：new_seg_est=%d cap=%d" % (nc["new_seg_est"], cap)
        assert seg_len <= cap, \
            "最终请求 recall 段实码越界：seg_utf16=%d cap=%d" % (seg_len, cap)
        return "P3 recall_seg_utf16=%d new_seg_est=%d cap=%d" % (
            seg_len, nc["new_seg_est"], cap)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c9_restore_keeps_recall_wrapper(mech):
    """P4：snapshot → restore 后 h['ongoing'] 仍是 RecallSelector；
    恢复后回忆轮仍走旁路，不降级父本行为。"""
    ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", "E001.json"),
                        encoding="utf-8"))
    tmp = tempfile.mkdtemp(prefix="v2_c9_")
    try:
        texts = recorded_texts("E001", ep["turns"])   # 完整 8 段（编号 1..8 与 turn_XX 对齐）
        gw = _SeqGateway(texts)
        h = mech.init(ASSETS, ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        # 先跑 T01-T05，snapshot
        for i, t in enumerate(ep["turns"][:5], 1):
            t2 = dict(t)
            t2["turn_index"] = i
            mech.run_turn(h, t2, gw)
        snap = mech.snapshot(h)
        tmp2 = tempfile.mkdtemp(prefix="v2_c9b_")
        try:
            # 恢复用新 sandbox，剩余 texts 从 T06 起（前 5 个已被 gw 消耗掉）
            h2 = mech.restore(os.path.join(tmp2, "sandbox"), snap)
            assert isinstance(h2["ongoing"], mech.RecallSelector), \
                "restore 后 ongoing 不再是 RecallSelector（P4 失败）"
            # 用同一个 gw（其剩余 texts 是 T06/T07/T08）继续跑 T06
            t6 = dict(ep["turns"][5])
            t6["turn_index"] = 6
            out = mech.run_turn(h2, t6, gw)
            prompt = gw.prompts[-1]
            assert "【回忆】" in prompt, "恢复后回忆段丢失"
            assert "recall=injected" in (out.get("notes") or ""), \
                "恢复后 recall 未触发：notes=%s" % out.get("notes")
            return "P4 restore_wrapper_active=True recall_still_fires=True"
        finally:
            shutil.rmtree(tmp2, ignore_errors=True)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c10_positive_photo_exhibit_domain(mech):
    """三领域正向之一：摄影/看展。归档一条展拍相关，回忆轮末条含"上回"。
    验证 P1/P2 修法没把有效召回一起砍掉，也不按 E001 杯子专名特判。"""
    ep = {"episode_id": "T_PHOTO", "persona": {"me": "我", "her": "她"},
          "initial_kb": {}, "turns": []}
    tmp = tempfile.mkdtemp(prefix="v2_c10_")
    try:
        h = mech.init(os.path.join(ROOT, "candidates", "v2_recall", "assets"),
                      ep, _NullGateway(), sandbox=os.path.join(tmp, "sandbox"))
        h["storage"].write_file(h["kb"], "memory/raw_topic.md",
                                "# [2026-11-08 21:30] 看展\n"
                                "## [2026-11-08 21:30] 状态变化\n"
                                "### [2026-11-08 21:30] 对话记录\n"
                                "她：河边那张照片我改了曝光，暗部回来了\n")
        h["storage"].write_file(h["kb"], "moment/plan.md",
                                "## 进行中\nitem_ph~河边照片后期 | 进行中 | 她说要把暗部提亮\n")
        msgs = [{"role": "HER", "content": "上回河边那张照片你建议怎么调来着"}]
        body = mech.recall_body(h, msgs, topic_label="看展")
        assert "河边" in body and "曝光" in body, \
            "摄影领域回忆段没召回原话：body=%s" % body[:80]
        res = h["ongoing"].select_for_injection(
            h["kb"], msgs, current_turn=3, reply_directive=None,
            effective_intent={}, recall_clue=body)
        extras = res.get("v2RecallExtras") or []
        assert any("河边" in it["name"] or "河边" in it.get("chain", "") for it in extras), \
            "摄影领域 ongoing 旁路没接上：extras=%s" % [it["name"] for it in extras]
        return "P_positive[photo] recall_body_ok extras=%d" % len(extras)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c11_positive_business_trip_domain(mech):
    """三领域正向之一：出差/行程。归档一条酒店入住相关，回忆轮末条含"上次"。"""
    ep = {"episode_id": "T_TRIP", "persona": {"me": "我", "her": "她"},
          "initial_kb": {}, "turns": []}
    tmp = tempfile.mkdtemp(prefix="v2_c11_")
    try:
        h = mech.init(os.path.join(ROOT, "candidates", "v2_recall", "assets"),
                      ep, _NullGateway(), sandbox=os.path.join(tmp, "sandbox"))
        h["storage"].write_file(h["kb"], "memory/raw_topic.md",
                                "# [2026-11-14 09:00] 武汉出差\n"
                                "## [2026-11-14 09:00] 状态变化\n"
                                "### [2026-11-14 09:00] 对话记录\n"
                                "她：酒店订在光谷附近，明早九点的高铁，记得提前到\n")
        h["storage"].write_file(h["kb"], "moment/plan.md",
                                "## 进行中\nitem_wu~武汉出差住宿 | 进行中 | 光谷附近酒店明早九点车\n")
        msgs = [{"role": "HER", "content": "上次说武汉那趟酒店你记不记得叫什么"}]
        body = mech.recall_body(h, msgs, topic_label="武汉出差")
        assert "光谷" in body, "出差领域回忆段没召回：body=%s" % body[:80]
        res = h["ongoing"].select_for_injection(
            h["kb"], msgs, current_turn=4, reply_directive=None,
            effective_intent={}, recall_clue=body)
        extras = res.get("v2RecallExtras") or []
        assert any("武汉" in it["name"] or "光谷" in it.get("chain", "") for it in extras), \
            "出差领域 ongoing 旁路没接上：extras=%s" % [it["name"] for it in extras]
        return "P_positive[business-trip] recall_body_ok extras=%d" % len(extras)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c12_reviewer_20_extras_probe(mech):
    """R3 复核：完全复刻 reviewer 独立评审里的 20-extras 越界反例。
    20 条 name 含'事务编号Axx'、chain 重复'舞蹈练习安排是周三晚上…' 四遍、
    召回 clue 为'上回舞蹈练习是怎么安排的来着？'。
    修复前：extras_in_final_prompt=20，extra_render_utf16=2419 超 1800 上限；
    修复后：extras_in_final_prompt ≤ MAX_ONGOING_EXTRA_LINES，
    且最终请求新增段（recall 段 + v2 extras 行）按 UTF-16 码元 ≤ MAX_NEW_CONTEXT_CHARS。"""
    ep = {"episode_id": "REVIEW_BUDGET", "persona": {"me": "我", "her": "她"},
          "initial_kb": {}, "turns": []}
    tmp = tempfile.mkdtemp(prefix="v2_c12_")
    try:
        gw = _RecordingGateway()
        h = mech.init(os.path.join(ROOT, "candidates", "v2_recall", "assets"),
                      ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        chain = "舞蹈练习安排是周三晚上，提前热身，按原来的节奏练习。" * 4
        rows = ["id%d~事务编号A%02d | 进行中 | %s" % (i, i, chain) for i in range(20)]
        h["storage"].write_file(h["kb"], "moment/plan.md", "## 进行中\n" + "\n".join(rows))
        h["storage"].write_file(h["kb"], "memory/raw_topic.md",
                                "# [2026-11-02 12:00] 舞蹈练习\n"
                                "她：舞蹈练习安排是周三晚上，提前热身，按原来的节奏练习。\n")
        messages = [{"role": "HER", "content": "上回舞蹈练习是怎么安排的来着？"}]
        body = mech.recall_body(h, messages, topic_label="")
        sel = h["ongoing"].select_for_injection(
            "kb", messages, 1, effective_intent={}, recall_clue=body)
        extras_before = sel.get("v2RecallExtras") or []
        # 走一遍 run_turn 让 _fit_new_context 生效，捕获最终 prompt
        out = mech.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": messages[0]["content"]}],
            "clock": "2026-11-02 12:00"}}, gw)
        prompt = gw.prompts[-1]
        held_count = sum(("事务编号A%02d" % i) in prompt for i in range(20))
        assert held_count <= mech.MAX_ONGOING_EXTRA_LINES, \
            "R3 越界未修：final_prompt_extras=%d > cap=%d" % (
                held_count, mech.MAX_ONGOING_EXTRA_LINES)
        nc = _parse_newctx(out["notes"])
        assert nc and nc["new_seg_est"] <= mech.MAX_NEW_CONTEXT_CHARS, \
            "R3 新增段估算越界：new_seg_est=%s cap=%d" % (
                nc["new_seg_est"] if nc else None, mech.MAX_NEW_CONTEXT_CHARS)
        # 最终请求 recall 段实码 ≤ 1800（单位一致，不看局部串）
        seg = _extract_recall_section(prompt)
        seg_len = mech.rc.utf16_len(seg)
        assert seg_len <= mech.MAX_NEW_CONTEXT_CHARS, \
            "R3 最终请求 recall 段越界：seg_utf16=%d cap=%d" % (
                seg_len, mech.MAX_NEW_CONTEXT_CHARS)
        return ("R3 reviewer_probe extras_selector=%d extras_in_prompt=%d "
                "recall_seg_utf16=%d new_seg_est=%d cap=%d" % (
                    len(extras_before), held_count, seg_len,
                    nc["new_seg_est"], mech.MAX_NEW_CONTEXT_CHARS))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c13_utf16_truncate_unit(mech):
    """C 单位一致单测：utf16_truncate_tail/head 按码元裁、不切代理对。
    纯 emoji 串裁到 N 码元 → 实际留 N/2 个 emoji（每个 2 码元），无半截代理。"""
    cake = "\U0001F382"    # 🎂 U+1F382 补充平面 = 代理对
    s = cake * 2000                       # 2000 emoji = 4000 码元
    tail = mech.utf16_truncate_tail(s, 1200)
    assert mech.rc.utf16_len(tail) <= 1200, \
        "tail 仍按码点裁：utf16_len=%d > 1200" % mech.rc.utf16_len(tail)
    assert tail.count(cake) == 600, \
        "1200 码元应留 600 个 emoji，实得 %d" % tail.count(cake)
    head = mech.utf16_truncate_head(s, 1200)
    assert mech.rc.utf16_len(head) <= 1200 and head.count(cake) == 600
    # BMP 边界：纯中文每字 1 码元，裁 100 → 100 字
    zh = "杯" * 500
    assert mech.rc.utf16_len(mech.utf16_truncate_tail(zh, 100)) == 100
    # 混合：确保代理对不被从中间劈开（尾字符不可能是孤立高代理）
    mixed = "abc" + cake * 10
    t2 = mech.utf16_truncate_head(mixed, 4)       # a b c + 半截 emoji? 3+2=5>4 → 只留 abc
    assert mech.rc.utf16_len(t2) <= 4
    last = ord(t2[-1]) if t2 else 0
    assert not (0xD800 <= last <= 0xDBFF), "尾部留下孤立高代理（切了半截 emoji）"
    return "C unit tail=head=600 emoji @1200码元, BMP 100字, 无半截代理"


@check
def c14_reviewer_emoji_run_turn_budget(mech):
    """C：完全复刻 reviewer 的 emoji run_turn 反例——归档标题命中回忆、正文 1200 个 🎂。
    修复前：最终请求保留 1200 emoji = 2400 码元，仅 emoji 就超 1800；
    修复后：recall 段按码元裁，最终请求 recall 段 ≤ 1800，emoji 载荷 ≤ 预算，
    且没有半截代理。"""
    ep = {"episode_id": "REVIEW_EMOJI", "persona": {"me": "我", "her": "她"},
          "initial_kb": {}, "turns": []}
    tmp = tempfile.mkdtemp(prefix="v2_c14_")
    try:
        cake = "\U0001F382"
        gw = _RecordingGateway()
        h = mech.init(os.path.join(ROOT, "candidates", "v2_recall", "assets"),
                      ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        h["storage"].write_file(h["kb"], "memory/raw_topic.md",
                                "# [2026-11-02 12:00] 陶瓷杯纪念日 🎂\n"
                                "她：我们把那天记下来吧 " + cake * 1200 + "\n")
        h["storage"].write_file(h["kb"], "moment/plan.md", "## 进行中\n")
        mech.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": "上回那个陶瓷杯纪念日你是怎么记的"}],
            "clock": "2026-11-02 12:30"}}, gw)
        prompt = gw.prompts[-1]
        assert "【回忆】" in prompt, "emoji 反例未触发 recall，无法验预算"
        seg = _extract_recall_section(prompt)
        emoji_in_prompt = prompt.count(cake)
        emoji_payload = emoji_in_prompt * 2
        cap = mech.MAX_NEW_CONTEXT_CHARS
        assert mech.rc.utf16_len(seg) <= cap, \
            "C 越界未修：recall 段 utf16=%d > cap=%d（emoji 载荷 %d）" % (
                mech.rc.utf16_len(seg), cap, emoji_payload)
        # 单靠 emoji 也不得顶破预算（reviewer 的精确口径）
        assert emoji_payload <= cap, \
            "仅 emoji 载荷就 %d > 声明上限 %d" % (emoji_payload, cap)
        # 无半截代理
        for i, ch_ in enumerate(prompt):
            cp = ord(ch_)
            if 0xD800 <= cp <= 0xDBFF:
                nxt = ord(prompt[i + 1]) if i + 1 < len(prompt) else 0
                assert 0xDC00 <= nxt <= 0xDFFF, "最终请求出现孤立高代理 @%d" % i
        return "C emoji recall_seg_utf16=%d emoji_payload=%d cap=%d obeyed" % (
            mech.rc.utf16_len(seg), emoji_payload, cap)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def c15_joint_squeeze_bmp_plus_emoji(mech):
    """C：归档（BMP 长正文）+ 多条含 emoji chain 的进行中事项共同挤占预算。
    断言新增段合计（recall + extras 行）≤ MAX_NEW_CONTEXT_CHARS，
    extras 数 ≤ MAX_ONGOING_EXTRA_LINES，且最终请求无半截代理。"""
    ep = {"episode_id": "REVIEW_JOINT", "persona": {"me": "我", "her": "她"},
          "initial_kb": {}, "turns": []}
    tmp = tempfile.mkdtemp(prefix="v2_c15_")
    try:
        cake = "\U0001F382"
        gw = _RecordingGateway()
        h = mech.init(os.path.join(ROOT, "candidates", "v2_recall", "assets"),
                      ep, gw, sandbox=os.path.join(tmp, "sandbox"))
        # 归档：BMP 长正文命中"陶瓷杯"
        h["storage"].write_file(h["kb"], "memory/raw_topic.md",
                                "# [2026-11-02 12:00] 陶瓷杯\n"
                                + ("她：陶瓷杯烧制细节记录，杯底留空。\n" * 400))
        # 进行中事项：很多条，chain 夹 emoji，全部与"陶瓷杯"相关
        rows = []
        for i in range(30):
            rows.append("id%d~陶瓷杯事项%02d | 进行中 | %s" % (
                i, i, ("陶瓷杯相关安排" + cake * 30) * 4))
        h["storage"].write_file(h["kb"], "moment/plan.md", "## 进行中\n" + "\n".join(rows))
        out = mech.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
            "messages": [{"role": "HER", "text": "上回那个陶瓷杯的细节你还记得吗"}],
            "clock": "2026-11-02 12:30"}}, gw)
        prompt = gw.prompts[-1]
        nc = _parse_newctx(out["notes"])
        assert nc and nc["new_seg_est"] <= mech.MAX_NEW_CONTEXT_CHARS, \
            "共同挤占下新增段估算越界：new_seg_est=%s cap=%d" % (
                nc["new_seg_est"] if nc else None, mech.MAX_NEW_CONTEXT_CHARS)
        held = sum(("陶瓷杯事项%02d" % i) in prompt for i in range(30))
        assert held <= mech.MAX_ONGOING_EXTRA_LINES, \
            "extras 数量硬顶失效：held=%d" % held
        seg = _extract_recall_section(prompt)
        assert mech.rc.utf16_len(seg) <= mech.MAX_NEW_CONTEXT_CHARS
        for i, ch_ in enumerate(prompt):
            cp = ord(ch_)
            if 0xD800 <= cp <= 0xDBFF:
                nxt = ord(prompt[i + 1]) if i + 1 < len(prompt) else 0
                assert 0xDC00 <= nxt <= 0xDFFF, "共同挤占下出现孤立高代理 @%d" % i
        return "C joint held=%d/%d new_seg_est=%d recall_seg=%d cap=%d" % (
            held, mech.MAX_ONGOING_EXTRA_LINES, nc["new_seg_est"],
            mech.rc.utf16_len(seg), mech.MAX_NEW_CONTEXT_CHARS)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="logs/v2_recall_result.json")
    args = ap.parse_args(argv)
    mech = load(V2_MECH, "v2_recall_mech")
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn(mech)
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-38s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-38s %s: %s" % (fn.__name__, type(e).__name__, e))
    out = os.path.join(ROOT, args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump({"checks": results, "failed": failed,
               "candidate": "v2_recall",
               "parent": "reference_product_v1",
               "main_variable": "M2 回忆型检索"},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("V2 RECALL RESULT: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
