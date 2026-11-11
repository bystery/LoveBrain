# -*- coding: utf-8 -*-
"""RH-B-17「关系状态卡 + 已回应事项」（Relation State Card + Answered-Set）— LOOP-8。

研究假设（literature RH-B-17 + B' 审计结构发现）：①kb 以原始 JSON 倾倒进提示，
关键关系状态（温度/阶段/进行中/教训/边界）淹没在转义文本里；②所有臂都存在
"重复提问/查户口"（E312_T12 重答 T1 问题为证）——历史里明明有"她问→我已答"对，
模型没被显式提醒。

机制（v0，双组件，零新增调用）：
  组件 A 关系状态卡：把 initial_kb 蒸馏为固定 schema 的有界卡片（关系温度/阶段/
    进行中/教训/边界，每节截断），**替换**原始 kb JSON 倾倒段（可读性+有界性）；
  组件 B 已回应事项：确定性配对历史中"HER 带问号的子句 → 其后存在的 [我] 消息"，
    生成"她问 X→已答"清单（≤3 条，截断），注入尾部【已回应事项】，硬约束
    "以下问题已回应过，不得原样再问"。
  其余装配与 rh_b04 逐字节同构（锚定排布）。

可证伪实验（smoke_24，对照 rh_b04 同位既有输出）：
  ①重复提问率（确定性度量：回复中的问句与"已回应事项"清单的历史问句重叠数）不高于 b04；
  ②required/守护事件不降；③tokens 增量 ≤150/位；④亲读卡片与已回应清单是否自然生效。
"""
import json
import os
import re

TAIL_RECENT_K = 3
MAX_ANSWERED = 3
MAX_CARD_CHARS = 700
_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")
_QUESTION_TAIL = re.compile(r"(？|\?)")


def _read_assets(assets_root):
    parts = []
    for rel in _ASSET_ORDER:
        p = os.path.join(assets_root, "system_prompt", rel)
        with open(p, "r", encoding="utf-8") as f:
            parts.append(f.read().strip())
    return "\n\n".join(parts)


def _clip(s, n):
    if s is None:
        return ""
    if not isinstance(s, str):
        # wave3+ dict 型 persona/kb 值（如 her:{name,boundaries}）：可读序列化后再截断
        try:
            s = json.dumps(s, ensure_ascii=False)
        except Exception:
            s = str(s)
    s = s.strip()
    return s if len(s) <= n else s[:n] + "…"


def init(assets_root, ep_meta, gateway, sandbox):
    return {
        "system_prompt": _read_assets(assets_root),
        "persona": ep_meta.get("persona") or {},
        "initial_kb": ep_meta.get("initial_kb") or {},
        "history": [],
    }


def set_memory_access(handle, enabled):
    """无记忆机制可关：显式 no-op。"""


def _state_card(handle):
    """组件 A：kb → 固定 schema 有界卡片（替换原始 JSON 倾倒）。"""
    kb = handle.get("initial_kb") or {}
    persona = handle.get("persona") or {}
    me_p = persona.get("me") or kb.get("me") or ""
    her_p = persona.get("her") or kb.get("her") or ""
    lines = ["我：%s" % _clip(me_p, 60),
             "对方：%s" % _clip(her_p, 60)]
    if kb.get("warmth"):
        lines.append("关系温度：%s" % _clip(kb["warmth"], 80))
    if kb.get("stage"):
        lines.append("阶段：%s" % _clip(kb["stage"], 40))
    if kb.get("style"):
        lines.append("对方风格：%s" % _clip(kb["style"], 60))
    if kb.get("ongoing_plan"):
        lines.append("进行中：%s" % _clip(str(kb["ongoing_plan"]), 120))
    if kb.get("lessons"):
        lines.append("教训：%s" % _clip(str(kb["lessons"]), 100))
    if kb.get("topic"):
        lines.append("当前话题：%s" % _clip(str(kb["topic"]), 60))
    bounds = kb.get("her")
    if isinstance(bounds, dict) and bounds.get("boundaries"):
        lines.append("对方边界：%s" % _clip(bounds["boundaries"], 100))
    card = "\n".join(lines)
    return card[:MAX_CARD_CHARS]


def _answered_pairs(history):
    """组件 B：确定性配对"HER 问句 → 其后 [我] 消息"。返回 [(her_q, my_a)]。"""
    pairs = []
    pending_q = None
    for m in history:
        if m["role"] == "HER":
            for clause in re.split(r"(?<=[。！？；!?;\n])", m.get("text", "")):
                if _QUESTION_TAIL.search(clause) and len(clause.strip()) >= 6:
                    pending_q = clause.strip()
        else:
            if pending_q:
                pairs.append((pending_q, m.get("text", "")))
                pending_q = None
    return pairs


def _fmt_history(history):
    if not history:
        return "（本轮之前没有可见的真实聊天记录）"
    lines = []
    for m in history:
        who = "对方" if m["role"] == "HER" else "我"
        lines.append("[%s] %s" % (who, m["text"]))
    return "\n".join(lines)


def _intent_block(intent):
    if intent is None:
        return ""
    if isinstance(intent, str):
        text = intent.strip()
        return ("\n【我的意图】\n%s" % text) if text else ""
    if isinstance(intent, dict):
        if not intent.get("enabled"):
            return ""
        text = (intent.get("text") or "").strip()
        return ("\n【我的意图】\n%s" % text) if text else ""
    return ""


def build_user_prompt(handle, ctx):
    inp = ctx.get("input") or {}
    for m in inp.get("messages") or []:
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"):
            handle["history"].append({"role": m["role"], "text": m.get("text", "")})
    persona = handle.get("persona") or {}
    sections = []
    if persona.get("me") or persona.get("her"):
        sections.append("【人物背景】\n我：%s\n对方：%s" % (persona.get("me") or "（未提供）",
                                                          persona.get("her") or "（未提供）"))
    # ---- 组件 A：状态卡替换 kb 倾倒（RH-B-17 唯一增量之一）----
    sections.append("【关系状态卡】\n%s" % _state_card(handle))
    history = handle["history"]
    older = history[:-TAIL_RECENT_K] if len(history) > TAIL_RECENT_K else []
    if older:
        sections.append("【可见聊天记录】（按时间顺序，只含真实发生过的消息）\n%s"
                        % _fmt_history(older))
    note = (inp.get("note") or "").strip()
    if note:
        sections.append("【我的备注】\n%s" % note)
    ib = _intent_block(inp.get("intent"))
    if ib:
        sections.append(ib.strip())
    clock = inp.get("clock")
    if clock:
        sections.append("【当前时间】%s" % clock)
    recent = history[-TAIL_RECENT_K:]
    if recent:
        sections.append("【最近消息】（本轮对话直接依据）\n%s" % _fmt_history(recent))
    # ---- 组件 B：已回应事项（防重复提问）----
    pairs = _answered_pairs(history[:-len(recent)] if len(recent) else history)
    if pairs:
        shown = pairs[-MAX_ANSWERED:]
        sections.append("【已回应事项】（历史中她问过、我已答过的，不得原样再问）\n%s"
                        % "\n".join("- 她问：%s → 我已答：%s" % (_clip(q, 50), _clip(a, 50))
                                    for q, a in shown))
    sections.append("【本轮允许不回复吗】%s"
                    % ("是" if inp.get("may_decline") else "否"))
    sections.append("【输出要求】只输出一个 JSON 对象："
                    '{"action": "reply" 或 "no_reply", "recommended": "可直接发送的回复"}；'
                    '选择不回复时 recommended 为 null。')
    return "\n\n".join(sections)


def run_turn(handle, ctx, gateway):
    system = handle["system_prompt"]
    user = build_user_prompt(handle, ctx)
    res = gateway.generate(system, user, temperature=0.7, tag="main")
    npairs = len(_answered_pairs(handle["history"]))
    return {"reply": res["text"], "notes": "answered_pairs=%d" % npairs}
