# -*- coding: utf-8 -*-
"""RH-B-19「策略驱动生成」（Strategy-Driven Generation）— LOOP-11。

研究假设（文献驱动，撞墙后从头重设计）：
  LOOP-10 的 b18（Self-Refine 生成后精炼）五维 4.40 = -6.2% vs DIRECT。
  失败原因：事后修改是微调，不是质变；要 +50% 必须在**生成前**就规划好策略。

文献锚：
  - APTNESS（2407.21048, Sabour et al.）：认知共情→情绪支持策略→生成。
    框架：情绪识别→评估理论分解→策略数据库检索→策略驱动生成。
    证据：增强 LLM 认知+情感双重共情能力。
  - EmoLLM（2603.16553）：认知-情感协同推理——IQ+EQ 双通道。
    在事实可靠+情感恰当的双重约束下生成回复。

机制（两阶段，2 次调用，与 b18 同成本但机制本质不同）：
  第 1 次调用（"想"，temp 0.3 低温）：给定完整上下文，要求模型输出一个 JSON：
    {emotion: 对方当前情绪, stage: 对话阶段, strategy: 回复策略, key_facts: 必须引用的事实}
    strategy ∈ {acknowledge, comfort, tease, advance, deflect, confirm, decline}
  第 2 次调用（"写"，temp 0.7）：把第 1 次的策略分析 + 完整上下文给模型，
    要求"按照上述策略生成一条回复"——策略作为生成约束注入。

与 b18/b15 的本质差别：
  - b15/b18 = 先生成再选/改（事后）→ 选择/精炼天花板低
  - b19 = 先分析再生成（事前）→ 生成时就带着策略意图，不是事后修补

成本：2 次调用/轮（= b18/b15b）；第 1 次输出 ~50 tokens（策略 JSON），第 2 次正常生成。
"""
import json
import os
import re

_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")
STRATEGIES = ("acknowledge", "comfort", "tease", "advance", "deflect", "confirm", "decline")
STRATEGY_CN = {
    "acknowledge": "接住对方说的话，让对方感到被听到",
    "comfort": "安抚/关心，对方情绪低落或受挫时",
    "tease": "调侃/开玩笑，气氛轻松时拉近距离",
    "advance": "推进话题/邀约/给钩子，关系在升温时",
    "deflect": "转移话题或缓和，对方追问敏感或气氛紧张时",
    "confirm": "确认事实/承诺/安排，对方在敲定细节时",
    "decline": "拒绝/说不，但要顾全关系分寸",
}


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


def _build_context(handle, ctx):
    """构建共用的上下文（两次调用都用）。"""
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
    return "\n\n".join(sections), inp


def _analysis_prompt(context_block, inp):
    """第 1 次调用：策略分析（低温，只输出 JSON）。"""
    strategy_list = "\n".join("- %s: %s" % (s, STRATEGY_CN[s]) for s in STRATEGIES)
    return "\n\n".join([
        context_block,
        "【任务】分析对方当前状态，选择最佳回复策略。只输出一个 JSON 对象，不要输出其他内容。",
        "【可选策略】",
        strategy_list,
        "【输出格式】",
        '```json',
        '{"emotion": "对方当前情绪（一句话）", "stage": "对话阶段（如：升温/磨合/日常/冲突）", '
        '"strategy": "策略名（从上面选一个）", "key_facts": ["必须引用的事实1", "事实2"], '
        '"avoid": ["要避免的（如：别说教/别重复问/别太快推进）"]}',
        '```',
    ])


def _generation_prompt(system, context_block, analysis_text, inp):
    """第 2 次调用：策略驱动生成（高温，输出回复）。"""
    # Parse analysis if possible, else use raw text
    analysis = analysis_text
    try:
        m = re.search(r'\{.*\}', analysis_text, re.S)
        if m:
            analysis = json.loads(m.group(0))
    except:
        pass

    if isinstance(analysis, dict):
        strat = analysis.get("strategy", "acknowledge")
        strat_desc = STRATEGY_CN.get(strat, STRATEGY_CN["acknowledge"])
        emotion = analysis.get("emotion", "")
        key_facts = analysis.get("key_facts", [])
        avoid = analysis.get("avoid", [])
        analysis_block = "\n".join([
            "【策略分析（已由你完成，请遵循）】",
            "对方情绪：%s" % emotion,
            "回复策略：%s（%s）" % (strat, strat_desc),
            "必须引用的事实：%s" % ("、".join(key_facts) if key_facts else "无特殊要求"),
            "要避免：%s" % ("、".join(avoid) if avoid else "无特殊要求"),
        ])
    else:
        analysis_block = "【策略分析（已由你完成）】\n%s" % analysis_text[:300]

    return "\n\n".join([
        context_block,
        analysis_block,
        "【生成要求】",
        "按照上述策略分析，生成一条此刻可以直接发送给对方的回复。",
        "- 回复必须体现所选策略的意图",
        "- 必须引用上面列出的必须引用的事实（如有）",
        "- 必须避免上面列出的要避免的内容（如有）",
        "- 自然口语，像真人随手打的消息",
        '- 只输出一个 JSON 对象：{"action": "reply", "recommended": "回复内容"}',
        '- 若策略分析认为不该回复，输出 {"action": "no_reply", "recommended": null}',
    ])


def run_turn(handle, ctx, gateway):
    system = handle["system_prompt"]
    context_block, inp = _build_context(handle, ctx)

    # ---- 第 1 次调用：策略分析（低温）----
    analysis_prompt = _analysis_prompt(context_block, inp)
    res1 = gateway.generate(system, analysis_prompt, temperature=0.3, tag="strategy_analysis")
    analysis_text = res1.get("text", "")

    # ---- 第 2 次调用：策略驱动生成（高温）----
    gen_prompt = _generation_prompt(system, context_block, analysis_text, inp)
    res2 = gateway.generate(system, gen_prompt, temperature=0.7, tag="strategy_generate")

    reply_raw = res2.get("text", "").strip()
    # Parse final reply
    try:
        m = re.search(r'\{.*\}', reply_raw, re.S)
        if m:
            obj = json.loads(m.group(0))
            reply = obj.get("recommended", "")
            action = obj.get("action", "reply")
        else:
            reply = reply_raw
            action = "reply"
    except:
        reply = reply_raw[:200]
        action = "reply"

    # Extract strategy from analysis for notes
    strategy_used = "?"
    try:
        m = re.search(r'"strategy"\s*:\s*"(\w+)"', analysis_text)
        if m:
            strategy_used = m.group(1)
    except:
        pass

    return {
        "reply": json.dumps({"action": action, "recommended": reply}, ensure_ascii=False),
        "notes": "v1;strategy=%s;analysis_tokens=%d;gen_tokens=%d" % (
            strategy_used,
            (res1.get("usage") or {}).get("total_tokens", 0),
            (res2.get("usage") or {}).get("total_tokens", 0)),
    }
