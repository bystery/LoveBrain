# -*- coding: utf-8 -*-
"""RH-B-06 「小步快答·两档级联」（Tiny-Context Draft with Escalation Gate）— RESEARCH-P1 首批原型。

研究假设（literature/MECHANISM_HYPOTHESES.md RH-B-06，支撑论文：
FrugalGPT arXiv:2305.05176 abstract-verified + Adaptive-RAG arXiv:2403.14403 full-read
+ Shi arXiv:2302.00093 abstract-only）：陪伴聊天轮次难度分布重度右偏，默认走极小上下文直答
（≈DIRECT 成本），仅当确定性升级门发现"本回复依赖记忆/有事实风险"时升级全装配。

机制（v0 最小原型，B5 预登记的"宁可错杀"白名单门）：
  第一档 = 人设 + 最近 3 条消息 + 当前时间 + 允许不回 + 输出要求（刻意贫瘠：
           无 kb、无更早历史、无 note/意图）
  升级门 = 确定性规则（零训练），命中任一即升级：
           a) 草稿含数字（金额/日期/号码类具体值）
           b) 草稿含记忆引用词（上次/之前/记得/说过/答应/昨天/那天/上次说）
           c) 草稿含邀约推进词（约/一起/见面/周末/改天/下次）
           d) 本轮存在未加工信号（note 非空 或 intent 启用）——第一档没有消费它们
  第二档 = 全装配（与 direct_baseline 逐字节同构的分段顺序）重新生成，以第二档为最终输出。

两次调用都走网关（完整记账：token/时延含两档）。草稿只作升级触发信号，不作输出；
未升级时草稿即最终回复。升级率与触发信号分布写进 mechanism_notes，供 C7 失败分类。
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
    inp = ctx.get("input") or {}
    persona = handle.get("persona") or {}
    me_p = persona.get("me")
    her_p = persona.get("her")
    head = ""
    if me_p or her_p:
        head = "我：%s\n对方：%s\n" % (me_p or "（未提供）", her_p or "（未提供）")
    recent = handle["history"][-TIER1_RECENT_K:]
    clock = inp.get("clock")
    return ("%s【最近消息】\n%s\n%s【本轮允许不回复吗】%s\n【输出要求】只输出一个 JSON 对象："
            '{"action": "reply" 或 "no_reply", "recommended": "可直接发送的回复"}；'
            "选择不回复时 recommended 为 null。"
            % (head, _fmt_history(recent),
               ("【当前时间】%s\n" % clock) if clock else "",
               "是" if inp.get("may_decline") else "否")).strip()


def _full_prompt(handle, ctx):
    """第二档全装配：与 direct_baseline build_user_prompt 逐段同构。"""
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


def _escalation_signals(draft_text, ctx):
    """确定性升级门（v0 白名单逻辑：宁可错杀）。返回触发信号列表。"""
    signals = []
    if _RX_DIGIT.search(draft_text):
        signals.append("digit")
    if _RX_MEMREF.search(draft_text):
        signals.append("mem_ref")
    if _RX_ADVANCE.search(draft_text):
        signals.append("advance")
    inp = ctx.get("input") or {}
    if (inp.get("note") or "").strip():
        signals.append("note_unprocessed")
    if _intent_text(inp.get("intent")):
        signals.append("intent_unprocessed")
    return signals


def run_turn(handle, ctx, gateway):
    system = handle["system_prompt"]
    # 第一档：极小上下文直答
    res1 = gateway.generate(system, _tier1_prompt(handle, ctx), temperature=0.7, tag="tier1_draft")
    draft = res1["text"]
    signals = _escalation_signals(draft, ctx)
    if not signals:
        return {"reply": draft,
                "notes": "escalated=false;tier=1;signals=[]"}
    # 第二档：全装配重新生成（草稿只作触发信号，不作输出）
    res2 = gateway.generate(system, _full_prompt(handle, ctx), temperature=0.7, tag="tier2_full")
    return {"reply": res2["text"],
            "notes": "escalated=true;tier=2;signals=%s" % json.dumps(signals, ensure_ascii=False)}
