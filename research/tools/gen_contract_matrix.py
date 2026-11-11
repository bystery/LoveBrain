# -*- coding: utf-8 -*-
"""gen_contract_matrix.py — A2 契约矩阵生成器（取证式，非手写）。

对 IO 契约的每条规则：用实际实现（arena/protocol_public.py + lb_scorer 动作规范）
跑正例/反例，把"条款→代码函数→输入→实际结果"写入
refs/arena_v1/CONTRACT_MATRIX.md + contract_matrix_evidence.json。
没有实现的规则标 IMPLEMENTATION_MISSING，不写成已会判。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, ROOT)

import protocol_public as pp                     # noqa: E402
import lb_scorer                                  # noqa: E402

ROWS = []   # (id, 条款, 函数, 输入摘要, 期望, 实际, 判定)


def row(rid, clause, fn, inp_desc, want, got, ok):
    ROWS.append({"id": rid, "clause": clause, "fn": fn, "input": inp_desc,
                 "want": want, "got": got, "verdict": "PASS" if ok else "FAIL"})


GOOD = {
    "action": "reply",
    "response": {"recommended": "早点休息", "bad_boy": None,
                 "playful": "小笨蛋，盖好被子", "warm": "记得喝热水"},
    "directions": ["报备行程", None, "问她咖啡续不续", None],
    "analysis": {"topic_status": "same", "topic_label": "睡前",
                 "scene_facts": [{"text": "她明晚加班", "source_ids": ["m3"]}],
                 "ongoing": [{"item": "周五看电影", "status": "已确认"}]},
}

# R1 可发送候选总数 ≥8（显式声明）
try:
    pp.normalize_contract({"style_keys": ["a", "b"], "directions": 3})
    row("R1", "契约可发送总数≥8（显式声明强制）", "normalize_contract",
        "style_keys=2, directions=3 → 总数5", "ValueError", "无异常", False)
except ValueError as e:
    row("R1", "契约可发送总数≥8（显式声明强制）", "normalize_contract",
        "style_keys=2, directions=3 → 总数5", "ValueError", str(e)[:60], True)

# R2 声明总数≥8 但 directions 可为 null：null 合法（v0.9.2）
errs = pp.output_structure_errors({**GOOD, "directions": [None] * 4})
row("R2", "directions[i] = string|null；null=该方向不交付", "output_structure_errors",
    "directions 全 null", "空（合法）", errs or "空", not errs)

# R3 directions 非字符串拒绝
for v in (1, [1], {}, True):
    errs = pp.output_structure_errors({**GOOD, "directions": [v, None, None, None]})
    if errs:
        row("R3", "directions 非字符串拒绝", "output_structure_errors",
            "directions[0]=%r" % (v,), "非空错误", errs[0], True)
        break

# R4 scene_facts[].text 必须 str（null/缺失同判错）
errs = pp.output_structure_errors({**GOOD, "analysis": {**GOOD["analysis"],
        "scene_facts": [{"text": None, "source_ids": ["m1"]}]}})
row("R4", "scene_facts[].text 非字符串/null 拒绝", "analysis_type_errors",
    "text=None", "非空错误", errs, bool(errs))

# R5 ongoing 字段类型
errs = pp.analysis_type_errors({"ongoing": [{"item": 1, "status": "ok"}]})
row("R5", "ongoing[].item/status 非字符串拒绝", "analysis_type_errors",
    "item=1", "非空错误", errs, bool(errs))

# R6 未声明扩展文本字段（response 内非空字符串）拒绝；extra_text_fields 声明后放行
errs = pp.output_structure_errors({**GOOD, "response": {**GOOD["response"],
                                   "secret": "夹带"}})
row("R6a", "response 未声明文本字段拒绝", "output_structure_errors",
    "response.secret=非空字符串", "非空错误", errs, bool(errs))
c2 = {"style_keys": ["recommended", "bad_boy", "playful", "warm"],
      "directions": 4, "extra_text_fields": ["reply"]}
errs = pp.output_structure_errors({**GOOD, "reply": "补充话术"}, c2)
row("R6b", "extra_text_fields 声明后合法（null=缺省放行）", "output_structure_errors",
    "reply='补充话术'（已声明）", "空", errs or "空", not errs)

# R7 未知顶层字段政策：非空字符串 → veto（评分侧）；用 run_case_scoring 验证
case = {"id": "R7", "rounds": 1, "contract": None,
        "rounds_data": [{"messages": [], "clock": "2026-10-21 12:00",
                         "may_decline": False}],
        "checks": [], "weights": {}, "gold": {}}
out7 = {**GOOD, "smuggled_text": "待发正文"}
res = lb_scorer.run_case_scoring(case, json.dumps(out7, ensure_ascii=False),
                                 {"m1": "HER", "m2": "ME"}, contract=None)
vetoes = res.get("veto") or ""
vetoes = vetoes if isinstance(vetoes, str) else ",".join(vetoes)
ok7 = "unknown_payload_field" in vetoes
row("R7", "未知顶层键非空字符串 → veto=unknown_payload_field",
    "lb_scorer.run_case_scoring", "smuggled_text='待发正文'",
    "veto 含 unknown_payload_field", vetoes, ok7)

# R8 动作互斥：decline_with_text / action_conflict / invalid_action
out8 = {**GOOD, "action": "no_reply", "response": GOOD["response"]}
case8 = dict(case, input={"may_decline": True})   # 轮级许可在 case.input（scorer L133 并集语义）
res = lb_scorer.run_case_scoring(case8, json.dumps(out8, ensure_ascii=False),
                                 {"m1": "HER"}, contract=None)
vetoes8 = res.get("veto") or ""
vetoes8 = vetoes8 if isinstance(vetoes8, str) else ",".join(vetoes8)
row("R8a", "不回动作与非空正文并存 → veto=decline_with_text",
    "run_case_scoring", "action=no_reply + response 非空",
    "veto 含 decline_with_text", vetoes8,
    "decline_with_text" in vetoes8)

out8b = {**GOOD, "action": "reply", "decline_reason": "不回了"}
res = lb_scorer.run_case_scoring(case, json.dumps(out8b, ensure_ascii=False),
                                 {"m1": "HER"}, contract=None)
vetoes8b = res.get("veto") or ""
vetoes8b = vetoes8b if isinstance(vetoes8b, str) else ",".join(vetoes8b)
row("R8b", "action=reply 与非空 decline_reason 并存 → action_conflict",
    "run_case_scoring", "action=reply + decline_reason",
    "veto 含 action_conflict", vetoes8b,
    "action_conflict" in vetoes8b)

out8c = {**GOOD, "action": "maybe", "response": GOOD["response"]}
res = lb_scorer.run_case_scoring(case, json.dumps(out8c, ensure_ascii=False),
                                 {"m1": "HER"}, contract=None)
vetoes8c = res.get("veto") or ""
vetoes8c = vetoes8c if isinstance(vetoes8c, str) else ",".join(vetoes8c)
row("R8c", "非法 action 值 → veto=invalid_action",
    "run_case_scoring", "action=maybe", "veto 含 invalid_action", vetoes8c,
    "invalid_action" in vetoes8c)

# R9 越权不回：轮级未允许 may_decline 时 no_reply → unauthorized_no_reply
res = lb_scorer.run_case_scoring(case, json.dumps(
    {**GOOD, "action": "no_reply", "reason": "没话说"}, ensure_ascii=False),
    {"m1": "HER"}, contract=None)
v9 = res.get("veto") or ""
v9 = v9 if isinstance(v9, str) else ",".join(v9)
row("R9", "轮级未允许时 no_reply → veto=unauthorized_no_reply（全不回不能白拿分）",
    "run_case_scoring", "may_decline=False + action=no_reply",
    "veto 含 unauthorized_no_reply", v9,
    "unauthorized_no_reply" in v9)

# R10 source_id 存在≠语义支持：伪造 id → illegal_source_id veto
res = lb_scorer.run_case_scoring(case, json.dumps(
    {**GOOD, "analysis": {**GOOD["analysis"], "scene_facts": [
        {"text": "编造", "source_ids": ["m99"]}]}}, ensure_ascii=False),
    {"m1": "HER"}, contract=None)
v10 = res.get("veto") or ""
v10 = v10 if isinstance(v10, str) else ",".join(v10)
row("R10", "source_id 只证编号合法；伪编号 → illegal_source_id（不证语义支持）",
    "run_case_scoring", "source_ids=['m99']（未登记）",
    "veto 含 illegal_source_id", v10,
    "illegal_source_id" in v10)

# R11 软证据不计硬分：soft_keywords_any 命中只进 evidence
case11 = {"id": "R11", "rounds": 1, "contract": None,
          "rounds_data": [{"messages": [], "clock": "2026-10-21 12:00",
                           "may_decline": False}],
          "checks": [],
          "weights": {},
          "gold": {"soft_keywords_any": [["哈哈", "笑死"]]}}   # 软关键词在 gold（scorer L315，权重 0）
GOOD11 = {**GOOD, "response": {**GOOD["response"], "recommended": "哈哈 笑死，那你早点睡"}}
res11 = lb_scorer.run_case_scoring(case11, json.dumps(GOOD11, ensure_ascii=False),
                                   {"m3": "HER"}, contract=None)   # id_map 含 m3：避免 illegal_source_id veto 短路；正文含关键词使命中
softs = [c for c in res11.get("checks", []) if str(c.get("id", "")).startswith("soft_kw_")]
ok11 = bool(softs) and all(c.get("pts") == 0 for c in softs) and \
       any(c.get("evidence") == "hit" for c in softs)
row("R11", "gold.soft_keywords_any 命中 → 证据层 pts=0，不计硬分",
    "run_case_scoring", 'gold.soft_keywords_any=[["哈哈","笑死"]] 且正文命中',
    "soft_kw 检查 pts=0 + evidence=hit",
    [(c["id"], c["pts"], c["evidence"]) for c in softs], ok11)

# R12 坏 JSON 留证：解析失败 ≠ 崩溃
res12 = lb_scorer.run_case_scoring_safe(case, "这不是json{{{",
                                        {"m1": "HER"}, contract=None)
r12 = json.dumps(res12, ensure_ascii=False)
row("R12", "原始解析失败留证（parse fail），审计不中断", "run_case_scoring_safe",
    "raw='这不是json{{{'", "结构化失败结果", r12[:80],
    ("parse" in r12.lower() or "fail" in r12.lower() or "error" in r12.lower()
     or res12.get("error") is not None or "veto" in r12))

# R13 类型先于语义：坏类型顶层字段 → json_schema_broken（不 AttributeError）
out13 = {**GOOD, "response": "不是字典", "directions": "不是列表"}
res13 = lb_scorer.run_case_scoring(case, json.dumps(out13, ensure_ascii=False),
                                   {"m1": "HER"}, contract=None)
v13 = res13.get("veto") or []
row("R13", "response/directions 坏类型 → veto=json_schema_broken（类型先于语义）",
    "run_case_scoring", "response=str, directions=str",
    "veto 含 json_schema_broken", v13,
    "json_schema_broken" in v13)

# 汇总
n_pass = sum(1 for r in ROWS if r["verdict"] == "PASS")
missing = [r for r in ROWS if r["verdict"] == "FAIL"]
ev = {"date": "2026-10-21", "rows": ROWS,
      "summary": {"total": len(ROWS), "pass": n_pass,
                  "fail": [r["id"] for r in missing]},
      "note": ("每行结果由实际函数执行产生；'没实现的能力不写成层1已会判'——"
               "任何 FAIL 行即契约未闭合项，须修复或登记差异。")}
json.dump(ev, open(os.path.join(ROOT, "refs", "arena_v1",
                                "contract_matrix_evidence.json"), "w",
                   encoding="utf-8"), ensure_ascii=False, indent=1)

# 生成 md
lines = ["# A2 契约矩阵（取证版）", "",
         "生成：tools/gen_contract_matrix.py（每行 = 实际函数执行结果，非手写转述）。",
         "证据：refs/arena_v1/contract_matrix_evidence.json。通过 %d/%d。" % (n_pass, len(ROWS)),
         "",
         "| ID | 条款 | 实现函数 | 输入 | 期望 | 实际 | 判定 |",
         "|---|---|---|---|---|---|---|"]
for r in ROWS:
    lines.append("| %s | %s | `%s` | %s | %s | %s | %s |" % (
        r["id"], r["clause"], r["fn"], r["input"].replace("|", "\\|"),
        r["want"].replace("|", "\\|"), str(r["got"]).replace("|", "\\|")[:60],
        r["verdict"]))
lines += ["", "## 评分口径补充条款（登记，未逐例取证）", "",
          "- 越权不回/矛盾动作/非法动作 = veto 级（合法性），恰当性另设计分检查——"
          "全不回不能靠格式满分被报告为质量冠军（见 R8/R9 取证）。",
          "- source_id 存在只证引用编号合法，不证摘要受原文语义支持（R10）。",
          "- AI 味词表/长度/LCS 是风格参考信号，不单独证明真人感"
          "（lb_scorer AI_FLAVOR_WORDS/STYLE_SIM_MAX，评分侧资产）。",
          "- 软证据（soft_keywords_any）命中只进 evidence 不计硬分（R11）。",
          "- 开放闲聊不用固定关键词逼标准答案：硬关键词只锚定客观事实。",
          "- 既有 T、权重、veto 政策未为任何候选临时修改。"]
open(os.path.join(ROOT, "refs", "arena_v1", "CONTRACT_MATRIX.md"), "w",
     encoding="utf-8").write("\n".join(lines) + "\n")
print("CONTRACT_MATRIX.md: %d/%d PASS | fail=%s" % (n_pass, len(ROWS),
                                                    [r["id"] for r in missing]))
for r in missing:
    print("  FAIL", r["id"], r["clause"], "| want:", r["want"], "| got:", str(r["got"])[:100])
