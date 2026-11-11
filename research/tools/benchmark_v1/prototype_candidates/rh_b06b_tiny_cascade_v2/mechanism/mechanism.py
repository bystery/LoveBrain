# -*- coding: utf-8 -*-
"""RH-B-06b「小步快答·两档级联 v2」（REWORK of rh_b06，修复盲答 Bug + 升级门收敛）。

v1 的两个实证问题（通宵循环 LOOP-1/LOOP-2，2026-11-11）：
  A 类实现 Bug：v1 的 run_turn 从不把 ctx.input.messages append 进 handle["history"]，
  当轮 HER 消息从不进入任何一档 prompt → 全部回复为"盲答"。其 severe 2/14 的优势
  是盲答不引用事实的假象（required_hits 2/8 全场最低、回复普遍答非所问）。
  成本结构问题：升级率 45.8% 超预登记 35%，主因"intent/note 存在即升级"。

v2 修复：
  1) run_turn 首先把当轮消息 append 进 history（与 direct_baseline/b04/b09 同口径），
     第一档"最近 3 条"因此包含当轮消息——盲答消除；
  2) note 与 intent 文本直接进第一档（消费信号，不再作为升级触发）；
  3) 升级门只看草稿内容信号：数字（金额/日期）／记忆引用词／邀约推进词——
     目标升级率 <35%，实测回收；
两次调用全记账，机制假设与支撑论文不变（FrugalGPT/Adaptive-RAG/Shi，见
literature/MECHANISM_HYPOTHESES.md RH-B-06）。
"""
import json
import os
import re

_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")
TIER1_RECENT_K = 3

_RX_DIGIT = re.compile(r"[0-9０-９]+")
_RX_MEMREF = re.compile(r"上次|之前|记得|说过|答应|昨天|那天|前天|上周")
_RX_ADVANCE = re.compile(r"约|一起|见面|周末|改天|下次|有空")


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


def _append_turn_messages(handle, ctx):
    inp = ctx.get("input") or {}
    for m in inp.get("messages") or []:
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"):
            handle["history"].append({"role": m["role"], "text": m.get("text", "")})


def _fmt_history(history):
    if not history:
        return "（本轮之前没有可见的真实聊天记录）"
    lines = []
    for m in history:
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


def _tier1_prompt(handle, ctx):
    """第一档：人设 + 最近 3 条（含当轮）+ note/intent 原文（小字段直接消费）
    + 时间 + 允许不回 + 输出要求。刻意不含 kb 与更早历史。"""
    inp = ctx.get("input") or {}
    persona = handle.get("persona") or {}
    me_p = persona.get("me")
    her_p = persona.get("her")
    head = ""
    if me_p or her_p:
        head = "我：%s\n对方：%s\n" % (me_p or "（未提供）", her_p or "（未提供）")
    recent = handle["history"][-TIER1_RECENT_K:]
    parts = [head + "【最近消息】\n" + _fmt_history(recent)]
    note = (inp.get("note") or "").strip()
    if note:
        parts.append("【我的备注】\n%s" % note)
    it = _intent_text(inp.get("intent"))
    if it:
        parts.append("【我的意图】\n%s" % it)
    clock = inp.get("clock")
    if clock:
        parts.append("【当前时间】%s" % clock)
    parts.append("【本轮允许不回复吗】%s" % ("是" if inp.get("may_decline") else "否"))
    parts.append("【输出要求】只输出一个 JSON 对象："
                 '{"action": "reply" 或 "no_reply", "recommended": "可直接发送的回复"}；'
                 "选择不回复时 recommended 为 null。")
    return "\n".join(p for p in parts if p).strip()


def _full_prompt(handle, ctx):
    """第二档全装配：与 direct_baseline 逐段同构（history 已含当轮）。"""
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
    inp = ctx.get("input") or {}
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
    sections.append("【输出要求】只输出一个 JSON 对象："
                    '{"action": "reply" 或 "no_reply", "recommended": "可直接发送的回复"}；'
                    '选择不回复时 recommended 为 null。')
    return "\n\n".join(sections)


def _escalation_signals(draft_text):
    """确定性升级门 v2：只看草稿内容信号（note/intent 已在第一档消费，不再触发）。"""
    signals = []
    if _RX_DIGIT.search(draft_text):
        signals.append("digit")
    if _RX_MEMREF.search(draft_text):
        signals.append("mem_ref")
    if _RX_ADVANCE.search(draft_text):
        signals.append("advance")
    return signals


def run_turn(handle, ctx, gateway):
    _append_turn_messages(handle, ctx)          # v2 修复：当轮消息进历史
    system = handle["system_prompt"]
    res1 = gateway.generate(system, _tier1_prompt(handle, ctx), temperature=0.7, tag="tier1_draft")
    draft = res1["text"]
    signals = _escalation_signals(draft)
    if not signals:
        return {"reply": draft,
                "notes": "v2;escalated=false;signals=[]"}
    res2 = gateway.generate(system, _full_prompt(handle, ctx), temperature=0.7, tag="tier2_full")
    return {"reply": res2["text"],
            "notes": "v2;escalated=true;signals=%s" % json.dumps(signals, ensure_ascii=False)}
