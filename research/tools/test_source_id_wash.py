#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_source_id_wash.py — W19-02 守卫侧 source_ids 清洗单测（零模型调用）。

覆盖：
  正例：短格式 m2（历史第 2 条，0 计）→ 改写 m_src[2]；正文逐字节不变；
        合法 m0（本轮 canonical）/ m_src[1] 逐字节不动。
  反例：m_src[999] 越界 → 移除并登记（不猜）；"1,1" 非 id 噪声 → 移除并登记；
        短格式越界（m8 无 canonical 且超历史范围）→ 移除。
  歧义：mN 同时在本轮 canonical 合法集内 → 按字面保留 canonical（不改写）。
  通用性：规则只按格式与计数判定，无任何题号特判；递归覆盖 analysis 内
        多个 scene_facts 的 source_ids；不可解析文本原样返回。
  集成：W17c E164_T04 实证片段（m0=本轮 + m2=历史 T03）清洗后 source_ids
        合法、response/directions 逐字段不变。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "arena"))

from source_id_wash import (  # noqa: E402
    M_SHORT_TO_HIST, REMOVE_NOISE, REMOVE_OOB_SHORT, REMOVE_OOB_SRC,
    body_intact_before_after, wash_reply_text, wash_source_id_list)

FAILS = []


def check(name, cond, detail=""):
    if cond:
        print("[PASS] %s" % name)
    else:
        FAILS.append(name)
        print("[FAIL] %s  %s" % (name, detail))


def _reply(source_ids_by_fact):
    facts = [{"text": "事实%d" % i, "source_ids": ids}
             for i, ids in enumerate(source_ids_by_fact)]
    return json.dumps({
        "response": {"recommended": "正文A", "bad_boy": "正文B",
                     "playful": "正文C", "warm": "正文D"},
        "directions": ["钩子1", "钩子2", "钩子3", "钩子4"],
        "analysis": {"topic_status": "same", "topic_label": "比价",
                     "scene_facts": facts, "ongoing": []}},
        ensure_ascii=False)


def test_positive_m2_to_m_src():
    raw = _reply([["m0"], ["m2"]])
    washed, meta = wash_reply_text(raw, {"m0"}, 3)
    check("m2 改写触发 changed", meta["changed"] is True, meta)
    check("m2 规则登记", M_SHORT_TO_HIST in meta["rules"], meta["rules"])
    e = [x for x in meta["edits"] if x["action"] == M_SHORT_TO_HIST]
    check("m2 编辑记录", len(e) == 1 and e[0]["replacement"] == "m_src[2]"
          and e[0]["token"] == "m2", meta["edits"])
    obj = json.loads(washed)
    check("清洗为 m_src[2]",
          obj["analysis"]["scene_facts"][1]["source_ids"] == ["m_src[2]"],
          obj["analysis"]["scene_facts"][1])
    check("m0 canonical 不动",
          obj["analysis"]["scene_facts"][0]["source_ids"] == ["m0"])
    # 正文逐字节不变：response/directions 字符串与原文完全一致
    orig = json.loads(raw)
    check("response 逐字节不变", obj["response"] == orig["response"])
    check("directions 逐字节不变", obj["directions"] == orig["directions"])
    check("body_intact 自检", meta["body_intact"] is True
          and body_intact_before_after(orig, obj))
    # 除 source_ids 外整体无差异（topic_label/ongoing 等全保持）
    check("除 source_ids 外逐字段不变", body_intact_before_after(orig, obj))


def test_legal_ids_untouched():
    for ids, canon, hn, tag in [(["m0"], {"m0"}, 3, "canonical m0"),
                                (["m_src[1]"], {"m0"}, 3, "m_src[1] 合法")]:
        raw = _reply([ids])
        washed, meta = wash_reply_text(raw, canon, hn)
        check("合法 %s 逐字节不动" % tag, washed == raw and
              meta["changed"] is False and meta["edits"] == [], (washed, meta))


def test_oob_m_src_removed():
    raw = _reply([["m_src[999]"]])
    washed, meta = wash_reply_text(raw, {"m0"}, 3)
    obj = json.loads(washed)
    check("m_src[999] 移除",
          obj["analysis"]["scene_facts"][0]["source_ids"] == [], obj)
    acts = [e["action"] for e in meta["edits"]]
    check("m_src[999] 登记为越界移除", acts == [REMOVE_OOB_SRC], meta["edits"])


def test_noise_removed():
    raw = _reply([["1,1"], ["m0"]])
    washed, meta = wash_reply_text(raw, {"m0"}, 3)
    obj = json.loads(washed)
    check("噪声 1,1 移除",
          obj["analysis"]["scene_facts"][0]["source_ids"] == [], obj)
    acts = [e["action"] for e in meta["edits"]]
    check("噪声登记", acts == [REMOVE_NOISE], meta["edits"])
    check("合法 m0 同批保留",
          obj["analysis"]["scene_facts"][1]["source_ids"] == ["m0"])


def test_short_oob_removed():
    raw = _reply([["m8"]])
    washed, meta = wash_reply_text(raw, {"m0"}, 3)
    obj = json.loads(washed)
    check("短格式越界 m8 移除",
          obj["analysis"]["scene_facts"][0]["source_ids"] == [], obj)
    check("m8 登记为短格式越界",
          [e["action"] for e in meta["edits"]] == [REMOVE_OOB_SHORT],
          meta["edits"])


def test_canonical_priority_ambiguity():
    # m2 同时是本轮 canonical 合法 id 且落在历史范围内 → 按字面保留 canonical
    raw = _reply([["m2"]])
    washed, meta = wash_reply_text(raw, {"m0", "m1", "m2"}, 4)
    check("歧义 m2 保留 canonical", washed == raw and meta["changed"] is False,
          (washed, meta))


def test_empty_and_unparseable():
    raw = _reply([[]])
    washed, meta = wash_reply_text(raw, {"m0"}, 3)
    check("空 source_ids 不动", washed == raw)
    for bad in ["not json {", "[1,2,3]", "42"]:
        washed, meta = wash_reply_text(bad, {"m0"}, 3)
        check("不可解析原样返回(%r)" % bad, washed == bad and
              meta["unparseable"] is True and meta["changed"] is False, meta)


def test_recursive_multi_facts():
    raw = _reply([["m0"], ["m3", "m_src[2]"], ["m1"]])
    washed, meta = wash_reply_text(raw, {"m0"}, 3)
    obj = json.loads(washed)
    sf = obj["analysis"]["scene_facts"]
    check("递归清洗多个 fact",
          sf[0]["source_ids"] == ["m0"] and
          sf[1]["source_ids"] == ["m_src[2]"] and
          sf[2]["source_ids"] == ["m_src[1]"], sf)
    check("混合编辑登记齐全",
          {e["action"] for e in meta["edits"]} ==
          {M_SHORT_TO_HIST, REMOVE_OOB_SHORT}, meta["edits"])


def test_wash_source_id_list_direct():
    # hist_n=2：m2 越界（历史只有第 0/1 条）→ 移除；" m0 " strip 后 canonical
    new, edits = wash_source_id_list(["m2", " m0 ", 7, "m_src[0]", "x"],
                                     {"m0"}, 2)
    check("列表直洗结果", new == ["m0", "m_src[0]"], (new, edits))
    check("列表直洗编辑数", len(edits) == 4, edits)


def test_e164_t04_evidence_integration():
    # W17c E164_T04 实证片段：m0=本轮 T04 消息（canonical），m2=T03 历史消息
    raw = json.dumps({
        "response": {"recommended": "算你纯泳池那部分：899 ÷ 72.5 = 12.4",
                     "bad_boy": "赠品里最能用的那两次次卡留下了",
                     "playful": "你算账算到小数点后", "warm": "你先别急着办"},
        "directions": ["你出差哪天回？", "能不能写进单子", "体验课当没有",
                       "出差回来再看价格"],
        "analysis": {"topic_status": "same",
                     "topic_label": "泳池季卡续费比价与赠品核价",
                     "scene_facts": [
                         {"text": "今天09:30何佳宁说前台称双十一当天办季卡送两次次卡加一节私教体验课",
                          "source_ids": ["m0"]},
                         {"text": "昨天21:05她说纯次卡是1450元，单次72.5元",
                          "source_ids": ["m2"]}],
                     "ongoing": [{"item": "2026-11-11 泳池季卡续费与赠品核价",
                                  "status": "进行中", "state": "她报双十一减后899元"}]}},
        ensure_ascii=False)
    washed, meta = wash_reply_text(raw, {"m0"}, 3)
    obj = json.loads(washed)
    orig = json.loads(raw)
    sf = obj["analysis"]["scene_facts"]
    check("E164_T04 实证：m0 保留", sf[0]["source_ids"] == ["m0"])
    check("E164_T04 实证：m2→m_src[2]", sf[1]["source_ids"] == ["m_src[2]"])
    check("E164_T04 实证：正文逐字节不变",
          obj["response"] == orig["response"] and
          obj["directions"] == orig["directions"] and
          obj["analysis"]["ongoing"] == orig["analysis"]["ongoing"])
    check("E164_T04 实证：body_intact", meta["body_intact"] is True)
    # 清洗后 id_map 含 m_src[2] → 评分合法集命中（smoke，直接比对集合）
    legal = {"m0": "HER"}
    legal.update({"m_src[%d]" % k: "HIST" for k in range(3)})
    check("清洗后全部 source_ids 进入合法集",
          all(sid in legal for f in sf for sid in f["source_ids"]), legal)


def main():
    test_positive_m2_to_m_src()
    test_legal_ids_untouched()
    test_oob_m_src_removed()
    test_noise_removed()
    test_short_oob_removed()
    test_canonical_priority_ambiguity()
    test_empty_and_unparseable()
    test_recursive_multi_facts()
    test_wash_source_id_list_direct()
    test_e164_t04_evidence_integration()
    print()
    if FAILS:
        print("FAILED: %d -> %s" % (len(FAILS), FAILS))
        return 1
    print("ALL PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
