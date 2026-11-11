#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_output_structure.py — 第七波判决 §3/§5 验收：完整输出结构与写回边界。

覆盖：
  A. 嵌套反例（scene_facts[0].text=对象）两轮 mock 回放——
     基线(v1)与 β_single(v2)：首轮判格式失败（不再 10 分）、无非法记忆提交、
     第二轮照常执行可审计、生命周期时钟照常前进、真正机制错误仍独立记录；
  B. directions 内对象元素 → 格式失败（第七波判决 §3 独立评分反例）；
  C. 已声明 extra_text_fields 正例不再被 unknown_payload_field 误杀，
     且参与 no_reply 正文互斥；未声明 text 反例维持拒绝。
  D. 事实 text 显式 null / 缺失键 = 格式失败（第八波判决 §4）；
     扩展字段对象值拒绝、显式 null 空值放行、合法字符串保持通过。
  E. text=null 两轮端到端回放：基线不再 .strip() 崩溃，两候选 0 分格式失败、
     第二轮照常执行、非法 null 记忆不提交且跳过留证。
  F. directions 显式 null = 必填槽位未交付 → 格式失败（v0.9.1，对照执行期初审发现）；
     4 字符串 directions 正例通过。
全部离线（mock 适配器，无任何真实模型调用）。
"""
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
os.chdir(ROOT)

import lb_runner  # noqa: E402
import lb_scorer  # noqa: E402

BAD_NESTED = json.dumps({
    "response": {"recommended": "爬山记得穿防滑的", "bad_boy": "你这体力我劝你缆车",
                 "playful": "行 下次摔了别哭", "warm": "注意安全 到了说一声"},
    "directions": ["带水了吗", None, "穿防滑鞋", None],
    "analysis": {"topic_status": "same", "topic_label": "爬山",
                 "scene_facts": [{"text": {"unexpected": "object"}, "source_ids": ["m0"]}],
                 "ongoing": []}}, ensure_ascii=False)

BAD_DIRECTION = json.dumps({
    "response": {"recommended": "好啊 周末走起", "bad_boy": "行 你别半路喊累",
                 "playful": "爬到山顶请你喝水", "warm": "早点出发 避开日头"},
    "directions": ["几点出发", {"sneaky": "object"}, None, "带件外套"],
    "analysis": {"topic_status": "same", "topic_label": "爬山", "scene_facts": [],
                 "ongoing": []}}, ensure_ascii=False)

GOOD = json.dumps({
    "response": {"recommended": "记得带水", "bad_boy": "你行你上",
                 "playful": "摔了我笑一年", "warm": "路上慢点"},
    "directions": ["带水", "查天气", "防滑鞋", "早点出发"],
    "analysis": {"topic_status": "same", "topic_label": "爬山",
                 "scene_facts": [{"text": "她周末去爬山", "source_ids": ["m0"]}],
                 "ongoing": [{"item": "周末爬山", "status": "进行中"}]}},
    ensure_ascii=False)

# F/G 组（v0.9.2 契约统一）：directions=string|null；动作×文本形态矩阵
# （对照执行期初审发现：A1-t5 directions[2]=null 仍得 8.75，与第八波
#   scene_facts.text null 同款 "is not None" 短路漏洞）
NULL_DIRECTION = json.dumps({
    "response": {"recommended": "记得带水", "bad_boy": "你行你上",
                 "playful": "摔了我笑一年", "warm": "路上慢点"},
    "directions": ["带水", "查天气", None, "早点出发"],
    "analysis": {"topic_status": "same", "topic_label": "爬山",
                 "scene_facts": [{"text": "她周末去爬山", "source_ids": ["m0"]}],
                 "ongoing": []}},
    ensure_ascii=False)


def _ep(path, first_raw, second_raw):
    ep = {
        "episode_id": "TEST_OUTSTRUCT",
        "turns": [
            {"case_id": "OS_t01", "mock_response": first_raw,
             "input": {"clock": "2026-10-19 10:00",
                       "messages": [{"role": "HER", "text": "我周末想去爬山",
                                     "ts": "2026-10-19 09:58"}]}},
            {"case_id": "OS_t02", "mock_response": second_raw,
             "input": {"clock": "2026-10-19 20:00",
                       "messages": [{"role": "HER", "text": "装备看好了",
                                     "ts": "2026-10-19 19:55"}]}},
        ]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(ep, f, ensure_ascii=False)
    return path


def _read(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def main():
    fails = []
    tmp = tempfile.mkdtemp(prefix="lb_outstruct_")

    # ---- 单元级：嵌套校验器 ----
    for name, raw in (("scene_facts.text=对象", BAD_NESTED),
                      ("directions 含对象", BAD_DIRECTION)):
        out = lb_scorer.extract_json_block(raw)
        if not lb_scorer.output_structure_errors(out):
            fails.append("单元/%s: output_structure_errors 未报错" % name)
        r = lb_scorer.run_case_scoring({"input": {}, "gold": {}}, raw,
                                       {"m0": "HER"}, None)
        if r["veto"] != "json_schema_broken":
            fails.append("单元/%s: veto=%s（应 json_schema_broken）" % (name, r["veto"]))
        elif "nested_type_violation" not in json.dumps(r["checks"], ensure_ascii=False):
            fails.append("单元/%s: evidence 未标 nested_type_violation" % name)

    # ---- C 单元：契约扩展字段 ----
    contract = {"style_keys": ["recommended", "bad_boy", "playful", "warm"],
                "directions": 4, "min_nonempty": 1,
                "extra_text_fields": ["alternate"], "may_decline": True}
    ext_out = json.loads(GOOD)
    ext_out["alternate"] = "备选：那就改天"
    r = lb_scorer.run_case_scoring({"input": {}, "gold": {}},
                                   json.dumps(ext_out, ensure_ascii=False),
                                   {"m0": "HER"}, contract)
    if r["veto"]:
        fails.append("C1/已声明 extra_text_fields 被误杀: %s" % r["veto"])
    names = [n for n, _ in lb_scorer.all_reply_texts(ext_out, contract)]
    if "alternate" not in names:
        fails.append("C1/all_reply_texts 未收集 alternate")
    # 扩展文本参与 no_reply 互斥
    ext_nr = {"action": "no_reply", "alternate": "别发 这条不适合回"}
    r = lb_scorer.run_case_scoring({"input": {"may_decline": True}, "gold": {}},
                                   json.dumps(ext_nr, ensure_ascii=False),
                                   {"m0": "HER"}, contract)
    if r["veto"] != "decline_with_text":
        fails.append("C2/no_reply+扩展正文未被互斥拦截: %s" % r["veto"])
    # 未声明 text 维持拒绝
    r = lb_scorer.run_case_scoring({"input": {"may_decline": True}, "gold": {}},
                                   '{"action":"no_reply","text":"夹带"}',
                                   {"m0": "HER"}, contract)
    if r["veto"] != "unknown_payload_field":
        fails.append("C3/未声明 text 未拒绝: %s" % r["veto"])
    # C4（第八波判决 §4）：扩展字段自身值类型——对象拒绝，合法字符串保持通过
    ext_bad = json.loads(GOOD)
    ext_bad["alternate"] = {"text": "对象型文本"}
    errs = lb_scorer.output_structure_errors(ext_bad, contract)
    if not errs or "alternate:dict" not in ",".join(errs):
        fails.append("C4/扩展字段对象值未被结构门拒绝: %r" % errs)
    r = lb_scorer.run_case_scoring({"input": {}, "gold": {}},
                                   json.dumps(ext_bad, ensure_ascii=False),
                                   {"m0": "HER"}, contract)
    if r["veto"] != "json_schema_broken":
        fails.append("C4/扩展字段对象值评分未判格式失败: %s" % r["veto"])
    ext_none = json.loads(GOOD)
    ext_none["alternate"] = None
    if lb_scorer.output_structure_errors(ext_none, contract):
        fails.append("C4/扩展字段显式 null（契约允许空值）被误杀")

    # ---- D 单元（第八波判决 §4）：事实 text 显式 null / 缺失键 = 格式失败 ----
    for tag, fact in (("text=null", {"text": None, "source_ids": ["m0"]}),
                      ("text缺失", {"source_ids": ["m0"]})):
        raw = json.dumps({
            "response": {"recommended": "嗯嗯", "bad_boy": "行", "playful": "好",
                         "warm": "早歇"},
            "directions": ["嗯", None, "好", None],
            "analysis": {"topic_status": "same", "topic_label": "爬山",
                         "scene_facts": [fact], "ongoing": []}},
            ensure_ascii=False)
        out = lb_scorer.extract_json_block(raw)
        errs = lb_scorer.output_structure_errors(out)
        if not errs or "scene_facts[0].text" not in ",".join(errs):
            fails.append("D1/%s: 结构门未拒绝: %r" % (tag, errs))
        r = lb_scorer.run_case_scoring({"input": {}, "gold": {}}, raw,
                                       {"m0": "HER"}, None)
        if r["score10"] != 0 or r["veto"] != "json_schema_broken":
            fails.append("D2/%s: 得分=%s veto=%s（应 0 分格式失败）"
                         % (tag, r["score10"], r["veto"]))

    # ---- A 端到端：基线 v1（默认机制）两轮回放 ----
    ep = _ep(os.path.join(tmp, "ep_base.json"), BAD_NESTED, GOOD)
    out_base = os.path.join(tmp, "run_base")
    m = lb_runner.run_episode(ep, "candidates/round_0_baseline", out_base, "mock")
    if not m["complete"]:
        fails.append("A1/基线: 第二轮未执行（complete=False）——中止缺陷复现")
    sc1 = _read(os.path.join(out_base, "turn_01_OS_t01", "scoring.json"))
    if sc1["veto"] != "json_schema_broken":
        fails.append("A2/基线 t01: veto=%s（应格式失败）" % sc1["veto"])
    mf1 = _read(os.path.join(out_base, "turn_01_OS_t01", "manifest.json"))
    if mf1.get("mechanism_error"):
        fails.append("A3/基线 t01: mechanism_error=%s（写回崩溃未挡住）" % mf1["mechanism_error"])
    st1 = _read(os.path.join(out_base, "turn_01_OS_t01", "state_after.json"))
    if (st1.get("last_writeback_skipped") or {}).get("reason") != "nested_type_violation":
        fails.append("A4/基线 t01: 写回跳过未留证: %s" % st1.get("last_writeback_skipped"))
    if st1.get("last_clock") != "2026-10-19 10:00":
        fails.append("A5/基线 t01: 生命周期时钟未前进: %s" % st1.get("last_clock"))
    chain = (st1.get("kb") or {}).get("scene_chain") or ""
    if "她周末去爬山" in chain:
        fails.append("A6/基线 t01: 非法记忆被提交（scene_chain 含 t02 事实=顺序错或污染）")
    st2 = _read(os.path.join(out_base, "turn_02_OS_t02", "state_after.json"))
    chain2 = (st2.get("kb") or {}).get("scene_chain") or ""
    if "她周末去爬山" not in chain2:
        fails.append("A7/基线 t02: 合法输出正例未写回（修一处破一处）: %r" % chain2)
    if "unexpected" in chain2:
        fails.append("A8/基线: 坏事实对象串进了记忆")

    # ---- A 端到端：β_single v2 两轮回放 ----
    ep = _ep(os.path.join(tmp, "ep_beta.json"), BAD_NESTED, GOOD)
    out_beta = os.path.join(tmp, "run_beta")
    m = lb_runner.run_episode(ep, "candidates/phase0_beta_single", out_beta, "mock")
    if not m["complete"]:
        fails.append("B1/β_single: 第二轮未执行（complete=False）")
    notes1 = open(os.path.join(out_beta, "turn_01_OS_t01", "mechanism_notes.txt"),
                  encoding="utf-8").read()
    if "writeback_skipped" not in notes1:
        fails.append("B2/β_single t01: 写回跳过未留证 notes: %r" % notes1[:120])
    facts_p = os.path.join(out_beta, "_sandbox", "kv_facts.jsonl")
    facts = open(facts_p, encoding="utf-8").read() if os.path.isfile(facts_p) else ""
    if "unexpected" in facts:
        fails.append("B3/β_single: 坏事实对象写入 kv_facts")
    if "她周末去爬山" not in facts:
        fails.append("B4/β_single t02: 合法事实未写入（正例破坏）: %r" % facts[:200])

    # ---- E 端到端（第八波判决 §4）：text=null 两轮回放——0 分格式失败、
    #      基线不再 .strip() 崩溃、两候选第二轮继续、非法 null 记忆不提交 ----
    NULL_FACT = json.dumps({
        "response": {"recommended": "嗯", "bad_boy": "哦", "playful": "嘿", "warm": "好"},
        "directions": ["嗯", None, "好", None],
        "analysis": {"topic_status": "same", "topic_label": "爬山",
                     "scene_facts": [{"text": None, "source_ids": ["m0"]}],
                     "ongoing": []}}, ensure_ascii=False)
    for tag, cand in (("基线", "candidates/round_0_baseline"),
                      ("β_single", "candidates/phase0_beta_single")):
        ep = _ep(os.path.join(tmp, "ep_null_%s.json" % tag), NULL_FACT, GOOD)
        out_n = os.path.join(tmp, "run_null_%s" % tag)
        m = lb_runner.run_episode(ep, cand, out_n, "mock")
        if not m["complete"]:
            fails.append("E1/%s: 第二轮未执行（complete=False）" % tag)
        sc1 = _read(os.path.join(out_n, "turn_01_OS_t01", "scoring.json"))
        if sc1["score10"] != 0 or sc1["veto"] != "json_schema_broken":
            fails.append("E2/%s t01: score=%s veto=%s（应 0 分格式失败，不得 10 分）"
                         % (tag, sc1["score10"], sc1["veto"]))
        mf1 = _read(os.path.join(out_n, "turn_01_OS_t01", "manifest.json"))
        if tag == "基线" and mf1.get("mechanism_error"):
            fails.append("E3/基线 t01: mechanism_error=%s（null.strip 崩溃未挡住）"
                         % mf1["mechanism_error"])
        sc2 = _read(os.path.join(out_n, "turn_02_OS_t02", "scoring.json"))
        if sc2["score10"] <= 0:
            fails.append("E4/%s t02: 第二轮正例未正常得分: %s" % (tag, sc2["score10"]))
        if tag == "基线":
            st1 = _read(os.path.join(out_n, "turn_01_OS_t01", "state_after.json"))
            if st1.get("last_writeback_skipped", {}).get("reason") != "nested_type_violation":
                fails.append("E5/基线 t01: null 写回跳过未留证: %r"
                             % st1.get("last_writeback_skipped"))
        else:
            n1 = open(os.path.join(out_n, "turn_01_OS_t01", "mechanism_notes.txt"),
                      encoding="utf-8").read()
            if "writeback_skipped" not in n1:
                fails.append("E5/β_single t01: null 写回跳过未留证 notes: %r" % n1[:120])

    # ---- F 单元（v0.9.2 契约统一）：directions 元素 = string|null（提示契约）----
    # null = 该方向本轮不交付，合法；非字符串（数字/对象/列表）拒绝。
    out = lb_scorer.extract_json_block(NULL_DIRECTION)
    errs = lb_scorer.output_structure_errors(out)
    if errs:
        fails.append("F1/directions null 被误拒（违反 string|null 出站契约）: %r" % errs)
    r = lb_scorer.run_case_scoring({"input": {}, "gold": {}}, NULL_DIRECTION,
                                   {"m0": "HER"}, None)
    if r["veto"]:
        fails.append("F2/directions null: score=%s veto=%s（按提示契约应合法得分）"
                     % (r["score10"], r["veto"]))
    r = lb_scorer.run_case_scoring({"input": {}, "gold": {}}, GOOD,
                                   {"m0": "HER"}, None)
    if r["veto"]:
        fails.append("F3/4 字符串 directions 正例被误杀: %s" % r["veto"])

    # ---- G 单元（v0.9.2 第九波判决 §8-A2）：动作 × 文本形态矩阵 ----
    # G1 四个 null directions + 正常 response：合法（提示允许全 null）
    g1 = json.loads(GOOD); g1["directions"] = [None] * 4
    r = lb_scorer.run_case_scoring({"input": {}, "gold": {}},
                                   json.dumps(g1, ensure_ascii=False),
                                   {"m0": "HER"}, None)
    if r["veto"]:
        fails.append("G1/四 null+正常response 被拒: %s" % r["veto"])
    # G2 四 null + 显式 action=reply：同样合法（与缺省一致）
    g2 = dict(g1); g2["action"] = "reply"
    r = lb_scorer.run_case_scoring({"input": {}, "gold": {}},
                                   json.dumps(g2, ensure_ascii=False),
                                   {"m0": "HER"}, None)
    if r["veto"]:
        fails.append("G2/四null+显式reply 被拒: %s" % r["veto"])
    # G3 directions 元素非字符串：数字/对象/列表 一律拒绝
    for bad in (3, {"t": "x"}, ["a"]):
        g3 = json.loads(GOOD); g3["directions"][1] = bad
        r = lb_scorer.run_case_scoring({"input": {}, "gold": {}},
                                       json.dumps(g3, ensure_ascii=False),
                                       {"m0": "HER"}, None)
        if r["veto"] != "json_schema_broken":
            fails.append("G3/directions 元素=%s 未拒绝: %s" % (type(bad).__name__, r["veto"]))
    # G4 no_reply + 有可发送正文 → decline_with_text（互斥，回归确认）
    g4 = json.loads(GOOD); g4["action"] = "no_reply"
    r = lb_scorer.run_case_scoring({"input": {"may_decline": True}, "gold": {}},
                                   json.dumps(g4, ensure_ascii=False),
                                   {"m0": "HER"}, None)
    if r["veto"] != "decline_with_text":
        fails.append("G4/no_reply+正文 未互斥拦截: %s" % r["veto"])
    # G5 合法 no_reply（无正文、directions 缺省/null）→ 通过
    g5 = {"action": "no_reply", "decline_reason": "对方收尾",
          "directions": [None] * 4,
          "analysis": {"topic_status": "same", "topic_label": "",
                       "scene_facts": [], "ongoing": []}}
    r = lb_scorer.run_case_scoring({"input": {"may_decline": True}, "gold": {}},
                                   json.dumps(g5, ensure_ascii=False),
                                   {"m0": "HER"}, None)
    if r["veto"]:
        fails.append("G5/合法no_reply 被拒: %s" % r["veto"])
    # G6 四 null + response 风格键全空 → min_nonempty 不满足 → 格式失败
    g6 = {"directions": [None] * 4,
          "response": {"recommended": None, "bad_boy": None,
                       "playful": None, "warm": None},
          "analysis": {"topic_status": "same", "topic_label": "",
                       "scene_facts": [], "ongoing": []}}
    r = lb_scorer.run_case_scoring({"input": {}, "gold": {}},
                                   json.dumps(g6, ensure_ascii=False),
                                   {"m0": "HER"}, None)
    if r["veto"] != "json_schema_broken":
        fails.append("G6/全空输出未按 min_nonempty 拒绝: %s" % r["veto"])
    # G7 all_reply_texts 唯一权威：null 方向不进文本池
    names = [n for n, _ in lb_scorer.all_reply_texts(g1, None)]
    if any(n.startswith("directions[") for n in names):
        fails.append("G7/null 方向混入文本池: %r" % names)

    if fails:
        print("FAIL %d 项：" % len(fails))
        for f in fails:
            print("  -", f)
        raise SystemExit(1)
    print("PASS：嵌套反例格式失败+写回拦截+第二轮可审计（基线/β）+时钟前进+扩展契约正例+未声明拒绝")


if __name__ == "__main__":
    main()
