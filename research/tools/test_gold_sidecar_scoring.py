# -*- coding: utf-8 -*-
"""test_gold_sidecar_scoring.py — GOLD_SIDECAR v1.0 消费检查点单测（零模型调用）。

覆盖（GOLD_SCHEMA.md v1.0 + rubric §2/§10）：
  1. confirmed 原子只作证据：不强迫提及、措辞不同语义一致 → 无新检查、pass；
  2. negation=true 原子被坐实（affirm_markers 词组命中）→ assertion_* fail；
     词组语义：组内全词同文本出现才算坐实；跨文本分散不算；
  3. status=cancelled 原子被坐实 + ongoing 复活 → assertion fail + cancelled fail；
  4. 意图轮行为相悖（intent_contract.must_not_keywords 命中）→ intent_forbid fail；
     behavior_direction=decline → expect_decline 映射生效；
  5. unknown 话题被坐实 → unknown_* fail；
  6. 回归：无 sidecar / gold_empty → gold 不被改动，评分结果与历史口径完全一致
     （仅 4 个契约检查，pts_total=6）；must-not-affirm 原子无 affirm_markers → 不实例化。
"""
import copy
import json
import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO_ROOT, "arena"))

import lb_scorer          # noqa: E402
import gold_sidecar       # noqa: E402


def _base_case(intent=None):
    return {
        "input": {
            "messages": [
                {"id": "m0", "role": "user", "text": "周五晚上我要和小林看电影"},
            ],
            "note": "",
            "intent": intent,
        },
        "gold": {},
    }


def _reply(text):
    return json.dumps({
        "action": "reply",
        "response": {"recommended": text},
        "directions": [None, None, None, None],
        "analysis": {"scene_facts": [], "ongoing": [], "topic_status": "",
                     "topic_label": ""},
    }, ensure_ascii=False)


def _id_map():
    return {"m0": "user"}


def _check(result, cid):
    for c in result["checks"]:
        if c["id"] == cid:
            return c
    return None


def _ids(result):
    return [c["id"] for c in result["checks"]]


# ---------- 1. confirmed 原子：只作证据，不强迫提及 ----------

def test_confirmed_assertion_evidence_only():
    case = _base_case()
    sidecar = {"gold_empty": False, "assertions": [{
        "id": "A01", "subject": "user", "relation": "has_plan",
        "value": "周五晚上和小林看电影", "status": "confirmed", "negation": False,
        "valid_time": "2026-11-06", "source": {"message_id": "m0", "quote": "同左"},
        "affirm_markers": [["小林", "看电影"]]}]}
    new_case, meta = gold_sidecar.apply_sidecar(case, sidecar)
    assert meta["sidecar_applied"] is True
    assert meta["unrated_assertions"], "confirmed 原子必须记 unrated（不强迫提及）"
    assert "assertion_checks" not in new_case["gold"], "confirmed 不得产生判分检查"
    # 参考措辞不同但语义一致：回复换个说法承接 → 不因"未复述 value"而 fail
    r = lb_scorer.run_case_scoring(new_case, _reply("那周五的安排别忘啦，跟小林的那场我记着呢"),
                                   _id_map())
    assert r["veto"] is None
    assert r["score10"] == 10.0
    # 完全不提也不 fail（合法自由度）：只是少了一条证据
    r2 = lb_scorer.run_case_scoring(new_case, _reply("嗯嗯，到时候见"), _id_map())
    assert r2["score10"] == 10.0


# ---------- 2. negation=true 被坐实 → fail；词组语义 ----------

def test_negated_assertion_affirmed_fails():
    case = _base_case()
    sidecar = {"gold_empty": False, "assertions": [{
        "id": "A02", "subject": "user", "relation": "has_plan",
        "value": "周五晚上和小林看电影", "status": "confirmed", "negation": True,
        "source": {"message_id": "m0", "quote": ""},
        "affirm_markers": [["小林", "看电影"]]}]}
    new_case, _ = gold_sidecar.apply_sidecar(case, sidecar)
    c = _check(lb_scorer.run_case_scoring(new_case, _reply("和小林看电影开心吗"), _id_map()),
               "assertion_0")
    assert c is not None and c["pass"] is False
    # 词组内全词必须出现在同一文本：分散在两个槽位不算坐实
    multi = json.dumps({
        "action": "reply",
        "response": {"recommended": "小林最近怎么样", "bad_boy": "看电影去不去"},
        "directions": [None, None, None, None],
        "analysis": {"scene_facts": [], "ongoing": []}}, ensure_ascii=False)
    c2 = _check(lb_scorer.run_case_scoring(new_case, multi, _id_map()), "assertion_0")
    assert c2 is not None and c2["pass"] is True
    # 换个说法没坐实词组 → 不判 fail（不逐字匹配 value 原文）
    r3 = lb_scorer.run_case_scoring(new_case, _reply("那周五的安排黄了？"), _id_map())
    assert _check(r3, "assertion_0")["pass"] is True


# ---------- 3. cancelled 被坐实 + ongoing 复活 ----------

def test_cancelled_assertion_affirm_and_revive():
    case = _base_case()
    sidecar = {"gold_empty": False, "assertions": [{
        "id": "A03", "subject": "user", "relation": "has_plan",
        "value": "周五晚上和小林看电影", "status": "cancelled", "negation": False,
        "source": {"message_id": "m0", "quote": ""},
        "affirm_markers": [["小林", "看电影"]], "item_markers": ["和小林看电影"]}]}
    new_case, _ = gold_sidecar.apply_sidecar(case, sidecar)
    # 复用 C8：cancelled_items 被映射
    assert "和小林看电影" in new_case["gold"]["cancelled_items"]
    # 回复坐实 + analysis.ongoing 复活 → assertion 与 cancelled 双 fail
    raw = json.dumps({
        "action": "reply",
        "response": {"recommended": "那就周五和小林看电影吧"},
        "directions": [None, None, None, None],
        "analysis": {"scene_facts": [], "ongoing": [{"item": "和小林看电影",
                                                     "status": "进行中"}]}},
        ensure_ascii=False)
    r = lb_scorer.run_case_scoring(new_case, raw, _id_map())
    assert _check(r, "assertion_0")["pass"] is False
    assert _check(r, "cancelled_0")["pass"] is False
    # 老实回复 + ongoing 记已取消 → 双 pass
    raw2 = json.dumps({
        "action": "reply",
        "response": {"recommended": "那我们改天再约"},
        "directions": [None, None, None, None],
        "analysis": {"scene_facts": [], "ongoing": [{"item": "和小林看电影",
                                                     "status": "已取消"}]}},
        ensure_ascii=False)
    r2 = lb_scorer.run_case_scoring(new_case, raw2, _id_map())
    assert _check(r2, "assertion_0")["pass"] is True
    assert _check(r2, "cancelled_0")["pass"] is True


# ---------- 4. 意图轮行为相悖 ----------

def test_intent_contract_mapping():
    case = _base_case(intent="邀约")
    sidecar = {"gold_empty": False, "intent_contract": {
        "intent": "邀约", "behavior_direction": "counter_propose",
        "keywords_any": [["改天"], ["周末"]],
        "must_not_keywords": ["下次一定"]}}
    new_case, meta = gold_sidecar.apply_sidecar(case, sidecar)
    assert "intent_keywords_any" in new_case["gold"]
    assert "intent_must_not_keywords" in new_case["gold"]
    assert meta["mapped_keys"]
    # 行为相悖：回复"下次一定"踩禁区 → intent_forbid_0 fail
    r = lb_scorer.run_case_scoring(new_case, _reply("哈哈下次一定"), _id_map())
    assert _check(r, "intent_forbid_0")["pass"] is False
    assert _check(r, "intent_kw_0")["pass"] is False     # 未提"改天/周末"，方向未承接
    r2 = lb_scorer.run_case_scoring(new_case, _reply("周末不行，改天呢"), _id_map())
    assert _check(r2, "intent_kw_0")["pass"] is True
    assert _check(r2, "intent_forbid_0")["pass"] is True

def test_intent_contract_decline_direction():
    case = _base_case(intent="邀约")
    case["input"]["may_decline"] = True     # 本轮允许不回（轮级 may_decline）
    sidecar = {"gold_empty": False, "intent_contract": {
        "intent": "邀约", "behavior_direction": "decline"}}
    new_case, _ = gold_sidecar.apply_sidecar(case, sidecar)
    assert new_case["gold"]["expect_decline"] is True
    raw = json.dumps({"action": "no_reply", "decline_reason": "这轮不适合接邀约"},
                     ensure_ascii=False)
    r = lb_scorer.run_case_scoring(new_case, raw, _id_map())
    c = _check(r, "decline_expected")
    assert c is not None and c["pass"] is True


# ---------- 5. unknown 被坐实 → fail ----------

def test_unknown_affirmed_fails():
    case = _base_case()
    sidecar = {"gold_empty": False, "unknowns": [
        {"topic": "用户是否加班", "affirm_markers": [["你加班"]]}]}
    new_case, meta = gold_sidecar.apply_sidecar(case, sidecar)
    assert meta["unknown_checks"] == 1
    r = lb_scorer.run_case_scoring(new_case, _reply("你加班辛苦了，早点休息"), _id_map())
    assert _check(r, "unknown_0")["pass"] is False
    r2 = lb_scorer.run_case_scoring(new_case, _reply("今天怎么样呀"), _id_map())
    assert _check(r2, "unknown_0")["pass"] is True


# ---------- 6. 回归：无 sidecar / gold_empty / 无 markers ----------

def test_no_sidecar_is_byte_identical_legacy():
    case = _base_case()
    raw = _reply("嗯嗯，到时候见")
    base = lb_scorer.run_case_scoring(case, raw, _id_map())
    # sidecar=None 与 gold_empty=True：gold 不被改动、结果与直接评分完全一致
    for tg in (None, {"gold_empty": True, "assertions": []}):
        new_case, meta = gold_sidecar.apply_sidecar(case, tg)
        assert new_case["gold"] == {} and meta["sidecar_applied"] is False
        r = lb_scorer.run_case_scoring(new_case, raw, _id_map())
        assert r == base
    assert _ids(base) == ["json_schema_valid", "source_ids_exist",
                          "ai_flavor", "style_diversity"]
    assert base["pts_total"] == 6 and base["score10"] == 10.0


def test_must_not_affirm_without_markers_unrated():
    case = _base_case()
    sidecar = {"gold_empty": False, "assertions": [{
        "id": "A09", "subject": "user", "relation": "has_plan",
        "value": "周五晚上和小林看电影", "status": "cancelled", "negation": False,
        "source": {"message_id": "m0", "quote": ""}}]}
    new_case, meta = gold_sidecar.apply_sidecar(case, sidecar)
    assert "assertion_checks" not in new_case["gold"]
    assert any(u["reason"] == "no_affirm_markers" for u in meta["unrated_assertions"])
    r = lb_scorer.run_case_scoring(new_case, _reply("和小林看电影呀"), _id_map())
    assert not [c for c in r["checks"] if c["id"].startswith("assertion_")]


def test_group_hit_helper():
    assert gold_sidecar.group_hit(["abc 和小林 看电影"], [["小林", "看电影"]]) is True
    assert gold_sidecar.group_hit(["小林", "看电影"], [["小林", "看电影"]]) is False
    assert gold_sidecar.group_hit(["看电影"], [["小林", "看电影"], ["场次"]]) is False


# ---------- 7. GOLD_SIDECAR_v1 波次文件布局（真 sidecar，GOLD_<wave>.json turns=list） ----------

def test_wave_file_layout(tmp_path):
    wave = {"schema_version": "GOLD_SIDECAR_v1", "wave": "w1", "turns": [
        {"case_id": "W1_T01", "episode_id": "W1", "gold_empty": False,
         "assertions": [{"atom_id": "N1_F01", "subject": "她", "relation": "事件",
                         "value": "杯子底裂了", "status": "asserted", "negation": False,
                         "source": {"message_id": "m0", "quote": "杯子底裂了"},
                         "required_this_turn": False}],
         "forbidden": {"forbidden_factual_claims": ["不要说她做得很好"],
                       "unknowns": ["老师具体说了什么"]},
         "intent_contract": {"intent": None, "may_decline": False,
                             "acceptable_actions": ["接住情绪"]}},
        {"case_id": "W1_T02", "episode_id": "W1", "gold_empty": True,
         "assertions": []}]}
    (tmp_path / "GOLD_w1.json").write_text(json.dumps(wave, ensure_ascii=False),
                                           encoding="utf-8")
    tg = gold_sidecar.find_turn_gold(str(tmp_path), "W1", "W1_T01")
    assert tg and tg["case_id"] == "W1_T01"
    new_case, meta = gold_sidecar.apply_sidecar(_base_case(), tg)
    assert meta["sidecar_applied"] is True
    # status=asserted（非取消族）→ 只作证据不判分
    assert "assertion_checks" not in new_case["gold"]
    # 自然语言禁令与字符串 unknown 均无判定信号 → 诚实登记不实例化
    assert "unknown_must_not_affirm" not in new_case["gold"]
    assert meta["unrated_unknowns"] == 1
    assert any("forbidden_factual_claims" in n for n in meta["notes"])
    # 取消族 + 无 markers → unrated；gold_empty 轮 → 不映射
    tg["assertions"][0]["status"] = "cancelled"
    new_case2, meta2 = gold_sidecar.apply_sidecar(_base_case(), tg)
    assert "assertion_checks" not in new_case2["gold"]
    assert meta2["unrated_assertions"][0]["reason"] == "no_affirm_markers"
    tg2 = gold_sidecar.find_turn_gold(str(tmp_path), "W1", "W1_T02")
    _, meta3 = gold_sidecar.apply_sidecar(_base_case(), tg2)
    assert meta3["sidecar_applied"] is False
