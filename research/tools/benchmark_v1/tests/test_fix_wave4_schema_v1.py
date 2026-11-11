# -*- coding: utf-8 -*-
"""fix_wave4_schema_v1 的合成夹具测试：在临时目录构造 1 篇迷你 episode，
跑 phase a --apply，断言六类迁移全部发生且 episode 正文 message 未被改动。"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "..", "fix_wave4_schema_v1.py")

EPISODE = {
    "episode_id": "E999",
    "persona": {"her": {"name": "测试", "traits": [], "boundaries": []},
                "me": {"name": "测试乙", "traits": []},
                "relationship_stage": "朋友"},
    "initial_kb": {},
    "turns": [
        {"case_id": "E999_T01", "turn_type": "chat", "gold": {}, "feedback": {},
         "input": {"clock": "2026-11-08 10:00", "may_decline": False,
                   "messages": [{"role": "HER", "text": "周六拼车还空位吧，油费八十。", "ts": "2026-11-08 09:55"}]}},
        {"case_id": "E999_T02", "turn_type": "chat", "gold": {}, "feedback": {},
         "input": {"clock": "2026-11-08 12:00", "may_decline": True,
                   "intent": {"enabled": True, "text": "确认周六安排"},
                   "messages": [{"role": "ME", "text": "行，周六老时间。", "ts": "2026-11-08 11:50"},
                                {"role": "HER", "text": "好嘞，那说定了。", "ts": "2026-11-08 11:58"}]}},
    ],
}
REFERENCE = {
    "story_id": "E999",
    "turns": {
        "E999_T01": {
            "case_id": "E999_T01", "current_action": "她发出周六拼车邀约",
            "acceptable_actions": ["自然确认周六拼车并接住油费话题", "先简短应下再补一句安排"],
            "applicable_dimensions": ["goal", "factuality", "interaction"],
            "required_memory_atoms": [
                {"atom_id": "E999_T01_A01", "subject": "HER", "relation": "乘车计划",
                 "value": "HER希望周六乘坐ME的车", "valid_time": "2026-11-08",
                 "status": "tentative", "negation": False,
                 "source": {"kind": "message", "case_id": "E999_T01", "message_id": "m0",
                            "quote": "周六拼车还空位吧"},
                 "required_this_turn": True},
            ],
            "private_guidance": "", "unknowns": [
                {"topic": "她周六是否带行李", "note": "正文未说明", "markers": [["行李"], ["包"]]},
            ],
            "protected_expressions": ["周六拼车还空位吧"], "forbidden_factual_claims": [],
            "may_decline": False,
        },
        "E999_T02": {
            "case_id": "E999_T02", "current_action": "ME 确认，她敲定",
            "acceptable_actions": ["简短确认", "顺带回扣油费"],
            "applicable_dimensions": ["goal", "time_state", "memory_use"],
            "required_memory_atoms": [
                {"atom_id": "E999_T02_A01", "subject": "SHARED", "relation": "约定",
                 "value": "双方敲定周六拼车", "valid_time": "2026-11-08",
                 "status": "confirmed", "negation": False,
                 "source": {"kind": "message", "case_id": "E999_T02", "message_id": "m0",
                            "quote": "好嘞，那说定了"},
                 "required_this_turn": False},
            ],
            "private_guidance": "", "unknowns": [],
            "protected_expressions": [], "forbidden_factual_claims": [],
            "may_decline": True,
        },
    },
}
TIMELINE = {
    "episode_id": "E999",
    "events": [{"turn": "E999_T01", "ts": "2026-11-08 09:55",
                "event": "HER 提出周六拼车"}],
}
EXPOSURE = {
    "status": "annotated_pending_audit", "author": "ZCode bm1-wave4 author-A1 (AI)",
    "author_type": "AI", "created_at": "2026-11-08", "dev_exposed": False,
    "experiments_used": [], "may_decline_turns": ["E999_T02"],
    "real_user_material": False, "title": "测试篇", "themes": ["T01"],
}


def _run(args, cwd):
    subprocess.check_call([sys.executable, "-I", TOOL] + args, cwd=cwd)


def test_phase_a_migrates_and_preserves_messages():
    tmp = tempfile.mkdtemp(prefix="w4fix_")
    try:
        d = os.path.join(tmp, "E999")
        os.makedirs(d)
        ep_before = json.dumps(EPISODE["turns"][1]["input"]["messages"], ensure_ascii=False)
        json.dump(EPISODE, open(os.path.join(d, "episode.json"), "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
        json.dump(REFERENCE, open(os.path.join(d, "reference.json"), "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
        json.dump(TIMELINE, open(os.path.join(d, "timeline.json"), "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
        json.dump(EXPOSURE, open(os.path.join(d, "exposure.json"), "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)

        _run(["--base", tmp, "--phase", "a", "--apply"], cwd=HERE)

        ep = json.load(open(os.path.join(d, "episode.json"), encoding="utf-8"))
        # 正文 message 未被改动
        assert json.dumps(ep["turns"][1]["input"]["messages"], ensure_ascii=False) == ep_before
        # A1 intent.status 补 explicit
        assert ep["turns"][1]["input"]["intent"]["status"] == "explicit"
        ref = json.load(open(os.path.join(d, "reference.json"), encoding="utf-8"))
        # A8 unknowns 键名对齐
        u0 = ref["turns"]["E999_T01"]["unknowns"][0]
        assert u0["affirm_markers"] == [["行李"], ["包"]] and u0["markers"] == [["行李"], ["包"]]
        # A2 required 翻转（source 轮 == 当前轮）
        a01 = ref["turns"]["E999_T01"]["required_memory_atoms"][0]
        assert a01["required_this_turn"] is False and a01["source"]["quote"]
        # A3 message_id 重算（T02 引用第二轮 m1 消息）
        a02 = ref["turns"]["E999_T02"]["required_memory_atoms"][0]
        assert a02["source"]["message_id"] == "m1"
        tl = json.load(open(os.path.join(d, "timeline.json"), encoding="utf-8"))
        # A5 timeline 迁移
        assert tl["story_id"] == "E999" and tl["events"][0]["source_turn"] == "E999_T01"
        assert tl["events"][0]["description"] and tl["events"][0]["event_id"] == "EV01"
        assert tl["turn_clocks"] == ["2026-11-08 10:00", "2026-11-08 12:00"]
        assert len(tl["message_index"]) == 3 and tl["message_index"][0]["speaker"] == "HER"
        assert tl["revisions"] == []
        ex = json.load(open(os.path.join(d, "exposure.json"), encoding="utf-8"))
        # A7 exposure 字段
        assert ex["readers"] == ["author", "annotator"] and ex["kind"] == "standard_2t"
        assert ex["batch"] == "W4-DEV-20261108" and ex["session"] == "W4-DEV-A1"
        # A6 runner 副本
        with open(os.path.join(d, "episode.json"), "rb") as f1, \
                open(os.path.join(d, "runner_episode.json"), "rb") as f2:
            assert f1.read() == f2.read()
        assert ex["runner_copy"].endswith("E999/runner_episode.json")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def test_phase_b_hashes():
    tmp = tempfile.mkdtemp(prefix="w4fix_")
    try:
        d = os.path.join(tmp, "E999")
        os.makedirs(d)
        for fn, obj in (("episode.json", EPISODE), ("reference.json", REFERENCE),
                        ("timeline.json", TIMELINE), ("exposure.json", EXPOSURE)):
            json.dump(obj, open(os.path.join(d, fn), "w", encoding="utf-8"),
                      ensure_ascii=False, indent=1)
        open(os.path.join(d, "audit.md"), "w", encoding="utf-8").write("# x")
        _run(["--base", tmp, "--phase", "b", "--apply"], cwd=HERE)
        ex = json.load(open(os.path.join(d, "exposure.json"), encoding="utf-8"))
        assert set(ex["hashes"]) == {"episode.json", "reference.json", "timeline.json", "audit.md"}
        import hashlib
        h = hashlib.sha256(open(os.path.join(d, "episode.json"), "rb").read()).hexdigest()
        assert ex["hashes"]["episode.json"] == h
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
