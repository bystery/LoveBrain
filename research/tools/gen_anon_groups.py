# -*- coding: utf-8 -*-
"""gen_anon_groups.py（v3，2026-10-21 v13 复核 §7 整改）— 匿名对照材料生成器。

v1 缺陷（已被独立复核实锤）：当前消息/上下文为手写字符串，与冻结 episode 和真实
请求不一致（G3 凭空"不黄了！"、G8 凭空"今早刚做的"、G5 误标"明确不发"）。

v2 修正：题面全部从冻结 episode 提取 + <chat> 栅栏逐条绑定断言（text+ts）。

v3 修正（v13 复核 §7：内容正确但防复发断言不完整）：
  1. 绑定断言升级为 id + speaker + text + ts 四元组（HER→PARTNER / ME→USER），
     改动 episode 说话人不再放行；
  2. 意图解析匹配真实请求的实际标题（【持续意图】等任意【…意图…】头），
     并与 episode intent 双向断言——意图缺失即 FAIL，不再静默跳过；
  3. 时钟缺失即 FAIL（不再 `if clock` 跳过）；
  4. 回复直接从原始 logs/.../turn_*/reply_raw.txt 解析绑定，
     并与派生 all_recommended.json 交叉核对一致——两侧任一不一致即停。

用法：python tools/gen_anon_groups.py
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "arena"))
import lb_scorer  # noqa: E402  # extract_json_block 与评分器同源

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN_ROOT = os.path.join(ROOT, "logs", "l3b_66_real66_v1", "runs")
_ROLE2SPK = {"HER": "PARTNER", "ME": "USER"}

recs = {(r["slot"].split("|")[0], r["slot"].split("|")[1], r["slot"].split("|")[2],
         r["slot"].split("|")[3]): r["rec"]
        for r in json.load(open(os.path.join(ROOT, "logs/real66_v1_all_recommended.json"),
                                encoding="utf-8"))}


def R(arm, ep, rep, case):
    return recs[(arm, ep, rep, case)]


def _raw_recommended(arm, ep_id, rep, case_id):
    """原始回复绑定：直接从 run 目录 reply_raw.txt 解析首选正文（不经派生账）。"""
    p = os.path.join(RUN_ROOT, arm, ep_id, rep,
                     "turn_%02d_%s" % (int(case_id.split("_t")[1]), case_id),
                     "reply_raw.txt")
    raw = open(p, encoding="utf-8").read()
    obj = lb_scorer.extract_json_block(raw)
    if not obj or not isinstance(obj.get("response"), dict):
        raise SystemExit("reply_raw 解析失败: %s" % p)
    rec = obj["response"].get("recommended")
    if not isinstance(rec, str) or not rec.strip():
        raise SystemExit("reply_raw 无 recommended: %s" % p)
    # 交叉核对：原始解析必须与派生 all_recommended 一致（账本双侧闭合）
    if rec != R(arm, ep_id[:3], rep, case_id):
        raise SystemExit("回复绑定不一致(%s/%s/%s/%s): 原始 reply_raw ≠ 派生账"
                         % (arm, ep_id, rep, case_id))
    return rec


# 组定义：只声明 组号 / case / 标题 / 臂序。题面一律从冻结 episode 提取。
GROUPS = [
    ("G1", "NCA_t01", "日常分享", [("A1", "r1"), ("A2", "r1"), ("A3", "r1")]),
    ("G2", "NCA_t03", "工作消息", [("A2", "r1"), ("A3", "r1"), ("A1", "r1")]),
    ("G3", "NCA_t04", "工作消息更正", [("A3", "r1"), ("A1", "r1"), ("A2", "r1")]),
    ("G4", "NCA_t05", "组会与新组碰面", [("A1", "r1"), ("A2", "r1"), ("A3", "r1")]),
    ("G5", "NCA_t06", "赶车收尾", [("A2", "r1"), ("A3", "r1"), ("A1", "r1")]),
    ("G6", "NCB_t01", "车次请求", [("A3", "r1"), ("A2", "r1"), ("A1", "r1")]),
    ("G7", "NCB_t03", "送糕交接", [("A1", "r1"), ("A3", "r1"), ("A2", "r1")]),
    ("G8", "NCB_t04", "收到糕之后", [("A2", "r1"), ("A1", "r1"), ("A3", "r1")]),
]

EP_FILES = {
    "NCA": os.path.join(ROOT, "benchmark/episodes/NCA_CORR_INTENT_v1.json"),
    "NCB": os.path.join(ROOT, "benchmark/episodes/NCB_ENTITY_DENSE_v1.json"),
}
EPISODES = {k: json.load(open(v, encoding="utf-8")) for k, v in EP_FILES.items()}


def _turn(ep_key, case_id):
    for t in EPISODES[ep_key]["turns"]:
        if t["case_id"] == case_id:
            return t
    raise SystemExit("case 未找到: %s" % case_id)


def _parse_calls_chat(arm, ep_id, rep, case_id):
    """解析真实请求 → [(id, speaker, text, ts)]、【当前时间】、意图头文本。
    意图头匹配真实标题样式（【持续意图】等任意【…意图…】）。"""
    p = os.path.join(RUN_ROOT, arm, ep_id, rep,
                     "turn_%02d_%s" % (int(case_id.split("_t")[1]), case_id),
                     "calls.json")
    call = json.load(open(p, encoding="utf-8"))[0]
    user = call["user"]
    m = re.search(r"<chat>\r?\n(.*?)\r?\n</chat>", user, re.S)
    if not m:
        raise SystemExit("calls 无 <chat> 栅栏: %s" % p)
    lines = []
    for ln in m.group(1).splitlines():
        ln = ln.strip()
        if ln:
            o = json.loads(ln)
            lines.append((o["id"], o["speaker"], o["text"], o["ts"]))
    mc = re.search(r"【当前时间】([0-9\- :]+)", user)
    mi = re.search(r"【[^】]*意图[^】]*】\r?\n([^\r\n]+)", user)
    md = "【本轮允许不回复】" in user
    return lines, (mc.group(1).strip() if mc else None), \
        (mi.group(1).strip() if mi else None), md


def _assert_binding(ep_key, case_id, arm, rep):
    """冻结 episode 轮次 ↔ 真实请求 逐条断言：id+speaker+text+ts+clock+intent+may_decline。"""
    turn = _turn(ep_key, case_id)
    msgs = turn["input"]["messages"]
    chat, clock, intent, md_req = _parse_calls_chat(arm, EPISODES[ep_key]["episode_id"], rep, case_id)
    if len(chat) != len(msgs):
        raise SystemExit("绑定断言失败(%s/%s/%s/%s): 条数 %d≠%d"
                         % (arm, ep_key, rep, case_id, len(chat), len(msgs)))
    for i, ((cid, spk, text, ts), m) in enumerate(zip(chat, msgs)):
        if cid != "m%d" % i:
            raise SystemExit("绑定断言失败(%s/%s/%s/%s): 第%d条 id=%r ≠ m%d"
                             % (arm, ep_key, rep, case_id, i, cid, i))
        if spk != _ROLE2SPK.get(m["role"]):
            raise SystemExit("绑定断言失败(%s/%s/%s/%s): 第%d条 speaker %r ≠ episode %r"
                             % (arm, ep_key, rep, case_id, i, spk, m["role"]))
        if text != m["text"] or ts != m["ts"]:
            raise SystemExit("绑定断言失败(%s/%s/%s/%s): %r vs %r"
                             % (arm, ep_key, rep, case_id, (text, ts), (m["text"], m["ts"])))
    # v13 复核 §7：clock / intent 缺失即 FAIL，不再静默跳过
    if not clock:
        raise SystemExit("绑定断言失败(%s/%s/%s): 真实请求无【当前时间】" % (arm, rep, case_id))
    if clock != turn["input"]["clock"]:
        raise SystemExit("clock 不一致(%s/%s/%s): %s vs %s"
                         % (arm, rep, case_id, turn["input"]["clock"], clock))
    it = turn["input"].get("intent") or {}
    enabled = bool(it.get("enabled"))
    if enabled and not intent:
        raise SystemExit("intent 缺失(%s/%s/%s): episode 启用意图但请求无意图头"
                         % (arm, rep, case_id))
    if not enabled and intent:
        raise SystemExit("intent 多余(%s/%s/%s): episode 未启用意图但请求有意图头 %r"
                         % (arm, rep, case_id, intent))
    if enabled and it.get("text") != intent:
        raise SystemExit("intent 不一致(%s/%s/%s): %r vs %r"
                         % (arm, rep, case_id, it.get("text"), intent))
    # v16 复核 §6：may_decline 许可绑定——episode 许可与请求条款必须一致
    md_ep = bool((turn["input"] or {}).get("may_decline"))
    if md_ep != md_req:
        raise SystemExit("may_decline 不一致(%s/%s/%s): episode=%s 但请求条款=%s"
                         % (arm, rep, case_id, md_ep, md_req))
    return turn


def _msg_lines(msgs):
    return "\n".join("> %s %s：%s" % (m["ts"], m["role"], m["text"]) for m in msgs)


def _prior_history(ep_key, idx):
    """先前固定聊天记录：第 idx 轮之前的全部固定剧本消息（不含任何模型草稿）。"""
    out = []
    for t in EPISODES[ep_key]["turns"][:idx]:
        out.append(t["input"]["messages"])
    return [m for chunk in out for m in chunk]


def main():
    md = ["# 实跑66：原始题面绑定的8组匿名比较（v3 重生成）", "",
          "本版由 tools/gen_anon_groups.py v3 生成：当前消息/时间/意图/允许不回/先前历史/画像",
          "全部取自冻结 episode，并对每个 (臂,重复) 槽完成与真实请求 calls.json <chat> 栅栏的",
          "id+speaker+text+ts+clock+intent 逐项绑定断言；回复从原始 reply_raw.txt 解析并与",
          "派生账交叉核对（v1 手写上下文已被 v12 独立复核 §4 推翻并撤换；v3 补齐 v13 复核",
          "§7 指出的说话人/意图/缺失跳过/回复绑定四项缺口）。",
          "回复原文不改，甲乙丙顺序沿用 v1。仅对读者隐藏臂名；诊断选例不构成无偏胜率样本。",
          "正文存在且契约通过不等于个人愿发。", ""]
    # 共同画像（按题材）
    for ep_key in ("NCA", "NCB"):
        ep = EPISODES[ep_key]
        md.append("### %s_CORR_INTENT_v1" % ep_key if ep_key == "NCA"
                  else "### %s_ENTITY_DENSE_v1" % ep_key)
        md.append("")
        md.append("```json")
        md.append(json.dumps({"persona": ep["persona"], "initial_kb": ep["initial_kb"]},
                             ensure_ascii=False, indent=2))
        md.append("```")
        md.append("")

    bindings = 0
    for gid, case_id, title, arms in GROUPS:
        ep_key = case_id.split("_")[0]
        idx = [i for i, t in enumerate(EPISODES[ep_key]["turns"]) if t["case_id"] == case_id][0]
        # 绑定断言：组内三臂 r1 全部 + NCA_t05 的 r2
        for arm, rep in arms:
            _assert_binding(ep_key, case_id, arm, rep)
            bindings += 1
        if case_id == "NCA_t05":
            for arm, _rep in arms:
                _assert_binding(ep_key, case_id, arm, "r2")
                bindings += 1
        turn = _turn(ep_key, case_id)
        inp = turn["input"]
        md.append("---")
        md.append("")
        md.append("## %s · %s（%s）" % (gid, title, case_id))
        md.append("")
        md.append("**实际请求时间**：%s" % inp["clock"])
        it = inp.get("intent") or {}
        md.append("**当轮意图原文**：%s" % (it.get("text", "（无）") if it.get("enabled") else "（未启用）"))
        md.append("**允许不回**：%s（%s）。" % (inp["may_decline"],
                                              "允许不等于必须" if inp["may_decline"] else "允许不等于必须"))
        prior = _prior_history(ep_key, idx)
        if prior:
            md.append("")
            md.append("<details>")
            md.append("<summary>展开先前固定聊天记录（不含任何模型草稿）</summary>")
            md.append("")
            md.append(_msg_lines(prior))
            md.append("")
            md.append("</details>")
        md.append("")
        md.append("**本轮真实消息**：")
        md.append("")
        md.append(_msg_lines(inp["messages"]))
        md.append("")
        for lab, (arm, rep) in zip(["甲", "乙", "丙"], arms):
            rec = _raw_recommended(arm, EPISODES[ep_key]["episode_id"], rep, case_id)
            md.append("**%s**：%s" % (lab, rec))
            md.append("")
        if case_id == "NCA_t05":
            md.append("**同标签的第二次重复（r2）**：")
            md.append("")
            for lab, (arm, _rep) in zip(["甲", "乙", "丙"], arms):
                rec = _raw_recommended(arm, EPISODES[ep_key]["episode_id"], "r2", case_id)
                md.append("**%s（r2）**：%s" % (lab, rec))
                md.append("")
        md.append("判定：甲 □原样愿发 □改后愿发 □不想发；乙 □原样愿发 □改后愿发 □不想发；丙 □原样愿发 □改后愿发 □不想发。")
        md.append("总选：□甲 □乙 □丙 □都不想发 □不回更合适。想改的一句：________。")
        md.append("")

    out = os.path.join(ROOT, "refs", "REAL66_ANON_GROUPS_V2_20261021.md")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(md))
    print("绑定断言 PASS：共 %d 个 (臂,重复) 槽题面与真实请求逐条一致" % bindings)
    print("产出 →", out)


if __name__ == "__main__":
    main()
