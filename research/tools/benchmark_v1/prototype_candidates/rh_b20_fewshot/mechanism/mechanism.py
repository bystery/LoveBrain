# -*- coding: utf-8 -*-
"""RH-B-20「示例驱动单次生成」（Few-Shot Demonstration Injection）— LOOP-12。

研究假设（撞墙后从头重设计，文献驱动）：
  b18（生成→精炼，2 调用）-6.2% + b19（策略→生成，2 调用）-7.2% = 两次调用改进连续失败。
  瓶颈不是调用次数不够，而是**两次调用引入损失**（Self-Refine 无正确性锚点→over-refinement；
  策略分析太详细→长度膨胀）。
  
  解法：回到单次调用，但在 prompt 中注入**优质回复示例**（few-shot demonstrations）。
  模型从示例中学习"好回复长什么样"——长度、语气、密度——不需要第二次调用。

文献锚：
  - Few-Shot Bot（2110.08118, Yoshimi et al.）：少量对话示例注入 prompt，零训练零微调，
    接近全微调水平。5-10 个示例即可。
  - APTNESS（2407.21048）：策略以数据库示例形式注入，不是让模型自己想。
  - Self-Refine 失败条件（2303.17651 重读）：对话任务无正确性信号→自反馈无锚→over-refinement。
    避免自反馈 = 避免over-refinement。

机制（单次调用，零额外调用）：
  1) 示例库：3 个预置优质回复示例（覆盖日常调侃/认真回应/简短确认三类），
     每个示例 = [对方说的] → [我的好回复]。
  2) 示例注入：在 prompt 尾部、输出要求之前，注入"以下是我之前回复得不错的几条，参考这个风格和长度"。
  3) 单次调用：模型看到示例后生成，长度和语气自然锚定到示例的密度。

与 b18/b19 的本质差别：
  - b18 = 生成→改（第 2 次调用改写，over-refinement）
  - b19 = 分析→生成（第 1 次调用分析，策略太详细→长度膨胀）
  - b20 = 示例注入→单次生成（零额外调用，示例锚定风格和长度）

成本：1 次调用/轮（= DIRECT；比 b18/b19 省 50%）。
"""
import json
import os

_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")

# 3 个预置优质回复示例（覆盖三类情境，长度 20-50 字——锚定"真人随手打"的密度）
EXAMPLES = [
    {
        "label": "日常调侃",
        "context": "她说自己加班到凌晨改PPT，文件名已经是'最终版_绝对不改版_再改是狗'。",
        "reply": "文件名笑死，'再改是狗'这个 flag 立得稳。改完早点睡，明天组长要是再提意见，我帮你遛狗。",
    },
    {
        "label": "认真回应",
        "context": "她说膝盖跑步疼，挂了运动医学科，不确定退不退下周的比赛。",
        "reply": "号挂得对，先看医生再说。退不退不是今天定的事，比赛年年有，膝盖就一副。",
    },
    {
        "label": "简短确认",
        "context": "她说周六的活动改到下周三晚上七点，城北文化馆，让我记一下。",
        "reply": "记上了，下周三七点城北文化馆。到时我早点到。",
    },
]


def _read_assets(assets_root):
    parts = []
    for rel in _ASSET_ORDER:
        p = os.path.join(assets_root, "system_prompt", rel)
        with open(p, "r", encoding="utf-8") as f:
            parts.append(f.read().strip())
    return "\n\n".join(parts)


def init(assets_root, ep_meta, gateway, sandbox):
    return {
        "system_prompt": _read_assets(assets_root),
        "persona": ep_meta.get("persona") or {},
        "initial_kb": ep_meta.get("initial_kb") or {},
        "history": [],
    }


def set_memory_access(handle, enabled):
    """无记忆机制可关：显式 no-op。"""


def _fmt_history(history, k=None):
    items = history if k is None else history[-k:]
    if not items:
        return "（本轮之前没有可见的真实聊天记录）"
    lines = []
    for m in items:
        who = "对方" if m["role"] == "HER" else "我"
        lines.append("[%s] %s" % (who, m["text"]))
    return "\n".join(lines)


def _intent_text(intent):
    if intent is None:
        return ""
    if isinstance(intent, str):
        return intent.strip()
    if isinstance(intent, dict) and intent.get("enabled"):
        return (intent.get("text") or "").strip()
    return ""


def _build_examples_block():
    """注入 3 个预置优质回复示例。"""
    lines = ["【参考示例】（以下是我之前回复得不错的几条，参考这个风格、长度和密度）"]
    for ex in EXAMPLES:
        lines.append("")
        lines.append("（%s）" % ex["label"])
        lines.append("对方：%s" % ex["context"])
        lines.append("我：%s" % ex["reply"])
    return "\n".join(lines)


def build_user_prompt(handle, ctx):
    inp = ctx.get("input") or {}
    for m in inp.get("messages") or []:
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"):
            handle["history"].append({"role": m["role"], "text": m.get("text", "")})
    persona = handle.get("persona") or {}
    sections = []
    me_p = persona.get("me")
    her_p = persona.get("her")
    if me_p or her_p:
        sections.append("【人物背景】\n我：%s\n对方：%s" % (me_p or "（未提供）", her_p or "（未提供）"))
    kb = handle.get("initial_kb")
    if kb:
        sections.append("【既有背景信息】\n%s" % json.dumps(kb, ensure_ascii=False))
    sections.append("【可见聊天记录】（按时间顺序，只含真实发生过的消息）\n%s"
                    % _fmt_history(handle["history"]))
    note = (inp.get("note") or "").strip()
    if note:
        sections.append("【我的备注】\n%s" % note)
    it = _intent_text(inp.get("intent"))
    if it:
        sections.append("【我的意图】\n%s" % it)
    clock = inp.get("clock")
    if clock:
        sections.append("【当前时间】%s" % clock)
    sections.append("【本轮允许不回复吗】%s"
                    % ("是" if inp.get("may_decline") else "否"))
    # ---- RH-B-20 唯一增量：示例注入（在输出要求之前）----
    sections.append(_build_examples_block())
    sections.append("【输出要求】只输出一个 JSON 对象："
                    '{"action": "reply" 或 "no_reply", "recommended": "可直接发送的回复"}；'
                    '选择不回复时 recommended 为 null。'
                    '回复长度参考上面示例（20-60字），像真人随手打的消息。')
    return "\n\n".join(sections)


def run_turn(handle, ctx, gateway):
    system = handle["system_prompt"]
    user = build_user_prompt(handle, ctx)
    res = gateway.generate(system, user, temperature=0.7, tag="main")
    usage = res.get("usage") or {}
    return {"reply": res["text"],
            "notes": "v1;fewshot_examples=%d;tokens=%d" % (len(EXAMPLES), usage.get("total_tokens", 0))}
