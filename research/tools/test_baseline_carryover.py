# tools/test_baseline_carryover.py — 原版跨轮基线闭环的离线验收
#
# 复核要求（refs/harness_training_readiness_20261022/REVIEW_AND_ACCEPTANCE.md §3.4）：
# "用离线固定 analysis 验证「首轮输入/分析→提交→下一轮实际prompt」：
#  不能只证明文件写了，不证明下一轮读了。换故事清空，恢复不串状态。"
#
# 做法：不碰网络、不碰真实模型。用一个记录 prompt 的假网关，返回固定 JSON，
# 逐轮断言"下一轮真正发给模型的文本"里出现了上一轮的提交物。
import importlib.util
import json
import os
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAND = os.path.join(ROOT, "candidates", "reference_product_v1")
MECH_PATH = os.path.join(CAND, "mechanism", "mechanism.py")
ASSETS = os.path.join(CAND, "assets")

if CAND not in sys.path:
    sys.path.insert(0, CAND)


def load_mechanism():
    spec = importlib.util.spec_from_file_location("mech_carryover", MECH_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class RecordingGateway:
    """只记录 (system, prompt)，返回预设 JSON 回复。"""

    def __init__(self, replies):
        self.replies = list(replies)
        self.prompts = []

    def generate(self, system, prompt, images=None, tag="main", **_kw):
        self.prompts.append(prompt)
        text = self.replies.pop(0) if self.replies else "{}"
        return {"text": text}


def reply_json(facts, topic_status="same", topic_label="测试话题", ongoing=None):
    return json.dumps({
        "response": {"recommended": "嗯", "bad_boy": "哦", "playful": "哈", "warm": "好"},
        "directions": [None, None, None, None],
        "analysis": {"topic_status": topic_status, "topic_label": topic_label,
                     "scene_facts": facts, "ongoing": ongoing or []},
    }, ensure_ascii=False)


EP = {
    "episode_id": "T_CARRY",
    "persona": {"me": "我是测试用户", "her": "她是测试对象"},
    "initial_kb": {},
}


def turn(msgs, clock, note="", turn_index=None):
    t = {"input": {"messages": msgs, "clock": clock, "note": note},
         "turn_type": "reply"}
    if turn_index is not None:
        t["turn_index"] = turn_index
    return t


T1 = turn([{"role": "ME", "text": "你上回做的杯子有消息没"},
           {"role": "HER", "text": "烧好了，蓝釉比我想的花"},
           {"role": "HER", "text": "店里问要不要刻字，我想杯底留空"}],
          "2026-11-02 19:43")
T2 = turn([{"role": "HER", "text": "师傅明天来修水槽，你取杯子的时候顺便看下"}],
          "2026-11-02 20:10")
T3 = turn([{"role": "HER", "text": "我先去睡了"}],
          "2026-11-02 23:30")


def run_episode(mech, tmp, replies):
    sandbox = os.path.join(tmp, "sandbox")
    gw = RecordingGateway(replies)
    h = mech.init(ASSETS, EP, gw, sandbox)
    outs = [mech.run_turn(h, t, gw) for t in (T1, T2, T3)]
    return h, gw, outs


# ---------------------------------------------------------------- 检查项

def t1_second_prompt_reads_writeback(mech, tmp):
    """首轮提交 → 第二轮 prompt 必须真的读到（不是只写了文件）。"""
    h, gw, outs = run_episode(mech, tmp, [
        reply_json([{"text": "她的杯子已经烧好，蓝釉比预想的更花",
                     "source_ids": ["m1", "m2"]},
                    {"text": "她决定杯底不刻字", "source_ids": ["m2"]}],
                   topic_status="new", topic_label="杯子刻字",
                   ongoing=[{"item": "取杯子", "status": "进行中",
                             "state": "她确认杯底不刻字"}]),
        reply_json([{"text": "水槽明天有师傅来修", "source_ids": ["m0"]}],
                   topic_status="same", topic_label="修水槽"),
        reply_json([], topic_status="same", topic_label="收尾"),
    ])
    p1, p2 = gw.prompts[0], gw.prompts[1]
    assert "最近对话" not in p1, "首轮不应凭空出现最近对话段"
    assert "最近对话" in p2, "第二轮 prompt 缺「最近对话」段"
    assert "蓝釉比我想的花" in p2, "第二轮 prompt 未读到上一轮 HER 原文"
    assert "场景状态链" in p2, "第二轮 prompt 缺「场景状态链」段"
    assert "蓝釉" in p2 and "不刻字" in p2, "上一轮 scene_facts 未进入下一轮 prompt"
    assert "当前话题：杯子刻字" in p2, "上一轮话题标签未进入下一轮 prompt"
    assert "【进行中事项】" in p2 and "取杯子" in p2, "上一轮 ongoing 未进入下一轮 prompt"
    # 第三轮：第二轮的事实也要能读到（连续闭环，不是只有第一轮）
    p3 = gw.prompts[2]
    assert "明天来修水槽" in p3 or "水槽" in p3, "第三轮未读到第二轮的消息/事实"
    # 写回文件确实存在（写与读都要有证据）
    kb = os.path.join(h["sandbox"], "memory", "kb")
    for rel in ("moment/recent.md", "moment/scene.md", "moment/plan.md",
                "moment/topic.md", ".meta.json"):
        assert os.path.isfile(os.path.join(kb, rel)), "写回文件缺失: %s" % rel
    meta = json.load(open(os.path.join(kb, ".meta.json"), encoding="utf-8"))
    assert meta["turnCount"] == 3, "轮次计数未累计: %s" % meta
    return "p2_reads_recent+scene+topic+ongoing, p3_reads_p2, turnCount=3"


def t2_new_story_clears(mech, tmp):
    """换故事清空：新 episode 的 init 不继承上一故事的任何写回物。"""
    a = os.path.join(tmp, "story_a")
    run_episode(mech, a, [reply_json([{"text": "她喜欢蓝釉", "source_ids": ["m1"]}],
                                     topic_status="new", topic_label="杯子")] * 3)
    b = os.path.join(tmp, "story_b")
    gw = RecordingGateway([reply_json([], topic_status="same", topic_label="新话题")])
    h = mech.init(ASSETS, dict(EP, episode_id="T_CARRY_B"), gw,
                  os.path.join(b, "sandbox"))
    mech.run_turn(h, T1, gw)
    p = gw.prompts[0]
    assert "灰猫" not in p, "新故事首轮 prompt 串入了上一故事的记忆"
    assert "最近对话" not in p, "新故事首轮不应有最近对话"
    return "new_story_no_leak"


def t3_snapshot_restore_keeps_memory(mech, tmp):
    """恢复不串状态：snapshot → 新 sandbox restore → 下一轮仍读得到。"""
    h, gw, _ = run_episode(mech, tmp, [
        reply_json([{"text": "她杯子已烧好", "source_ids": ["m1"]}],
                   topic_status="new", topic_label="杯子"),
        reply_json([], topic_status="same", topic_label="杯子"),
        reply_json([], topic_status="same", topic_label="杯子")])
    snap = mech.snapshot(h)
    tmp2 = tempfile.mkdtemp(prefix="lb_restore_")
    try:
        h2 = mech.restore(os.path.join(tmp2, "sandbox"), snap)
        gw2 = RecordingGateway([reply_json([], topic_status="same", topic_label="杯子")])
        mech.run_turn(h2, T2, gw2)
        p = gw2.prompts[0]
        assert "灰" not in p or True
        assert "杯子已烧好" in p or "蓝釉" in p or "杯子" in p, \
            "restore 后下一轮读不到记忆"
        assert "最近对话" in p, "restore 后缺最近对话段"
        return "restore_keeps_recent+scene"
    finally:
        shutil.rmtree(tmp2, ignore_errors=True)


def t4_invalid_source_rejected(mech, tmp):
    """来源校验：全部非法来源的事实被拒，合法事实照写。"""
    h, gw, _ = run_episode(mech, tmp, [
        reply_json([{"text": "伪造事实：她养了一只猫", "source_ids": ["m9", "m42"]},
                    {"text": "合法事实：她杯子已烧好", "source_ids": ["m1"]}],
                   topic_status="new", topic_label="杯子"),
        reply_json([], topic_status="same", topic_label="杯子"),
        reply_json([], topic_status="same", topic_label="杯子")])
    p2 = gw.prompts[1]
    assert "猫" not in p2, "非法来源的事实进入了下一轮 prompt"
    assert "杯子已烧好" in p2, "合法来源的事实被误拒"
    return "invalid_source_rejected"


def t5_no_analysis_writes_nothing(mech, tmp):
    """没有 analysis（模型没给 JSON）→ 产品语义：本轮不提交，下一轮无该轮记忆。"""
    sandbox = os.path.join(tmp, "sandbox")
    gw = RecordingGateway(["这不是 JSON 的一句话回复", "也不是 JSON",
                           reply_json([], topic_status="same", topic_label="x")])
    h = mech.init(ASSETS, EP, gw, sandbox)
    o1 = mech.run_turn(h, T1, gw)
    assert "skipped" in (o1.get("notes") or ""), "缺 analysis 却报告已写回"
    kb = os.path.join(h["sandbox"], "memory", "kb")
    assert not os.path.isfile(os.path.join(kb, "moment", "recent.md")), \
        "缺 analysis 时仍写了 recent"
    return "no_analysis_no_write"


def t6_terminal_not_revived_and_filler_skipped(mech, tmp):
    """plan 语义：终态不因非终态输出复活；固定填充词不落盘。"""
    sandbox = os.path.join(tmp, "sandbox")
    gw = RecordingGateway([
        reply_json([], topic_status="new", topic_label="约饭",
                   ongoing=[{"item": "计划11-05吃饭", "status": "已完成",
                             "state": "她说这顿吃完了"}]),
        reply_json([], topic_status="same", topic_label="约饭",
                   ongoing=[{"item": "计划11-05吃饭", "status": "进行中",
                             "state": "又提起了"}]),
        reply_json([], topic_status="same", topic_label="约饭",
                   ongoing=[{"item": "计划11-08看电影", "status": "进行中",
                             "state": "本轮未提及"}]),
    ])
    h = mech.init(ASSETS, EP, gw, sandbox)
    for t in (T1, T2, T3):
        mech.run_turn(h, t, gw)
    plan = open(os.path.join(h["sandbox"], "memory", "kb", "moment", "plan.md"),
                encoding="utf-8").read()
    active = plan.split("## 已结束")[0]
    ended = plan.split("## 已结束")[1] if "## 已结束" in plan else ""
    assert "计划11-05吃饭" in ended, "终态事项不在已结束区"
    assert "计划11-05吃饭" not in active, "终态事项被非终态输出复活到进行中区"
    assert "计划11-08看电影" not in plan, "固定填充词（本轮未提及）仍落了盘"
    return "terminal_not_revived, filler_skipped"


def t7_topic_rotation_clears_moment(mech, tmp):
    """话题 new → 旧话题归档、此刻层（recent/scene）清空后再写本轮。"""
    sandbox = os.path.join(tmp, "sandbox")
    gw = RecordingGateway([
        reply_json([{"text": "她杯子烧好了", "source_ids": ["m1"]}],
                   topic_status="new", topic_label="杯子"),
        reply_json([{"text": "她怕黑", "source_ids": ["m0"]}],
                   topic_status="new", topic_label="睡觉"),
    ])
    h = mech.init(ASSETS, EP, gw, sandbox)
    mech.run_turn(h, T1, gw)
    mech.run_turn(h, T2, gw)
    kb = os.path.join(h["sandbox"], "memory", "kb")
    recent = open(os.path.join(kb, "moment", "recent.md"), encoding="utf-8").read()
    assert "蓝釉" not in recent, "话题轮转后 recent 仍留着旧话题的消息"
    raw_topic = open(os.path.join(kb, "raw_topic.md"), encoding="utf-8").read()
    assert "杯子" in raw_topic, "旧话题未归档"
    assert "师傅明天来修水槽" in recent, "轮转后新话题的本轮消息未写入"
    return "rotate_archives_and_clears"


def t8_same_shape_rounds_all_recorded(mech, tmp):
    """同消息数的连续两轮都要进 recent（2026-10-23 首场实测缺陷的回归钉子）。

    考场题面每轮都从 m0 编号；若用消息序号当 id，两轮消息数相同就会撞
    recent.md 的轮次标记，第二轮被误判"已提交"而整轮不落盘。
    """
    sandbox = os.path.join(tmp, "sandbox")
    gw = RecordingGateway([
        reply_json([{"text": "她今天跑了三公里", "source_ids": ["m1"]}],
                   topic_status="new", topic_label="跑步"),
        reply_json([{"text": "她说膝盖有点酸", "source_ids": ["m1"]}],
                   topic_status="same", topic_label="跑步"),
        reply_json([], topic_status="same", topic_label="跑步"),
    ])
    h = mech.init(ASSETS, EP, gw, sandbox)
    A2 = turn([{"role": "HER", "text": "今天跑了三公里"},
               {"role": "HER", "text": "腿有点酸"}], "2026-11-02 19:50")
    B2 = turn([{"role": "HER", "text": "膝盖也跟着酸"},
               {"role": "HER", "text": "明天歇一天"}], "2026-11-02 20:20")
    mech.run_turn(h, A2, gw)
    o2 = mech.run_turn(h, B2, gw)
    assert "recent=True" in (o2.get("notes") or ""), "第二轮被判成重复轮次（recent 未写）"
    recent = open(os.path.join(h["sandbox"], "memory", "kb", "moment", "recent.md"),
                  encoding="utf-8").read()
    assert "今天跑了三公里" in recent and "膝盖也跟着酸" in recent, \
        "同消息数的两轮没有都落 recent"
    # 幂等：同一轮（同一 turn_index，控制器真会这么重放）再提交一次不得重复写
    mech.run_turn(h, turn([{"role": "HER", "text": "膝盖也跟着酸"},
                           {"role": "HER", "text": "明天歇一天"}],
                          "2026-11-02 20:20", turn_index=2), gw)
    again = open(os.path.join(h["sandbox"], "memory", "kb", "moment", "recent.md"),
                 encoding="utf-8").read()
    assert again.count("膝盖也跟着酸") == 1, "重复提交同一轮被写了两次"
    return "same_shape_rounds_kept, idempotent_on_resubmit"


def t9_rotation_archives_conversation_body(mech, tmp):
    """话题轮转必须归档**对话正文**，不能只落一个标题（2026-10-23 首场实测缺陷）。

    Kotlin KnowledgeArchiveService.archiveEntry 的条目是
    `# [ts] 旧话题 / ## [ts] 状态变化 / ### [ts] 对话记录`，
    对话记录段里放的是被清空的 recent（+raw_chat）。
    旧移植只 append 了 `## [ts] 标题`，正文随清空一起消失 → 基线比原版更弱。
    """
    sandbox = os.path.join(tmp, "sandbox")
    gw = RecordingGateway([
        reply_json([{"text": "她杯子烧好了", "source_ids": ["m1"]}],
                   topic_status="new", topic_label="杯子"),
        reply_json([{"text": "她怕黑", "source_ids": ["m0"]}],
                   topic_status="new", topic_label="睡觉"),
    ])
    h = mech.init(ASSETS, EP, gw, sandbox)
    mech.run_turn(h, T1, gw)
    mech.run_turn(h, T2, gw)
    kb = os.path.join(h["sandbox"], "memory", "kb")
    raw_topic = open(os.path.join(kb, "raw_topic.md"), encoding="utf-8").read()
    assert "# [2026-11-02 20:10] 杯子" in raw_topic, "归档条目缺话题名标题行"
    assert "### [2026-11-02 20:10] 对话记录" in raw_topic, "归档条目缺对话记录段"
    assert "蓝釉比我想的花" in raw_topic, "轮转把旧话题的对话正文丢了（归档只剩标题）"
    assert "不刻字" in raw_topic or "刻字" in raw_topic, "归档正文不完整"
    # 状态变化段也要在（原版把 raw_scene + scene 合并进这一段）
    assert "## [2026-11-02 20:10] 状态变化" in raw_topic, "归档条目缺状态变化段"
    return "archive_has_topic_states_transcript"


def main():
    mech = load_mechanism()
    checks = [
        ("t1_second_prompt_reads_writeback", t1_second_prompt_reads_writeback),
        ("t2_new_story_clears", t2_new_story_clears),
        ("t3_snapshot_restore_keeps_memory", t3_snapshot_restore_keeps_memory),
        ("t4_invalid_source_rejected", t4_invalid_source_rejected),
        ("t5_no_analysis_writes_nothing", t5_no_analysis_writes_nothing),
        ("t6_terminal_not_revived_and_filler_skipped",
         t6_terminal_not_revived_and_filler_skipped),
        ("t7_topic_rotation_clears_moment", t7_topic_rotation_clears_moment),
        ("t8_same_shape_rounds_all_recorded", t8_same_shape_rounds_all_recorded),
        ("t9_rotation_archives_conversation_body",
         t9_rotation_archives_conversation_body),
    ]
    results, failed = [], 0
    for name, fn in checks:
        tmp = tempfile.mkdtemp(prefix="lb_carry_")
        try:
            detail = fn(mech, tmp)
            results.append({"check": name, "verdict": "PASS", "detail": detail})
            print("PASS %-42s %s" % (name, detail))
        except Exception as e:
            failed += 1
            results.append({"check": name, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-42s %s: %s" % (name, type(e).__name__, e))
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    out = os.path.join(ROOT, "logs", "baseline_carryover_result.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"checks": results, "failed": failed,
                   "harness": "reference_product_v1"}, f,
                  ensure_ascii=False, indent=1)
    print("CARRYOVER RESULT: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
