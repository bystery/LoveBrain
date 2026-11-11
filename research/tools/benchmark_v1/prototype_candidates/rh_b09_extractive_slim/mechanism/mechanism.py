# -*- coding: utf-8 -*-
"""RH-B-09 「确定性抽取瘦身」（Deterministic Extractive History Slimming）— RESEARCH-P1 首批原型。

研究假设（literature/MECHANISM_HYPOTHESES.md RH-B-09，支撑论文：
RECOMP arXiv:2310.04408 abstract-only + LLMLingua-2 arXiv:2403.12968 full-read）：
不训练压缩器、不用生成式摘要，用确定性"选句/选消息"（只删不改，忠实性结构上保证）
即可砍历史窗口 30-60%，质量损失风险集中在"氛围信息"——因此只瘦身较旧的历史段，
最近 K 条原文永不压缩（保留语气与最新状态）。

机制（v0 消息级选择，比句级更保守——只删"无实质标记"的整条旧消息）：
  保留规则（确定性，无随机）：较旧消息（除最近 K=3 条外）凡含以下任一实质标记则保留：
    数字（金额/日期/号码）、「」/"" 引语、问号（？/?）、否定词（不/没/别/无）、
    时间词（昨天/今天/明天/晚上/周末/点/号）、约定动词（答应/说好/约/计划/决定）；
    一条都不含的旧消息（纯寒暄/客套/语气词）丢弃。
  与 direct_baseline 的唯一差异 = 【可见聊天记录】段的内容（其余逐段同构）；
  淘汰率与保留明细写进 mechanism_notes，供 C7 失败分类与"氛围损失"检验。
"""
import json
import os
import re

_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")
KEEP_RECENT_K = 3

_RX_SUBSTANCE = re.compile(
    r"[0-9０-９]|「|」|“|”|\"|'|？|\?|不|没|别|无|昨天|今天|明天|晚上|周末|点|号|"
    r"答应|说好|约|计划|决定|想|要|去")


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


def _slim(history):
    """确定性抽取：最近 K 条全保；更旧的仅保留含实质标记者。
    返回 (kept, n_dropped)。"""
    if len(history) <= KEEP_RECENT_K:
        return list(history), 0
    older = history[:-KEEP_RECENT_K]
    recent = history[-KEEP_RECENT_K:]
    kept = [m for m in older if _RX_SUBSTANCE.search(m.get("text", ""))]
    return kept + recent, len(older) - len(kept)


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
    kept, n_dropped = _slim(handle["history"])
    handle["_last_dropped"] = n_dropped
    if kept:
        sections.append("【可见聊天记录】（按时间顺序，只含真实发生过的消息；"
                        "已确定性省略无实质内容的旧寒暄）\n%s" % _fmt_history(kept))
    note = (inp.get("note") or "").strip()
    if note:
        sections.append("【我的备注】\n%s" % note)
    ib = _intent_block(inp.get("intent"))
    if ib:
        sections.append(ib.strip())
    clock = inp.get("clock")
    if clock:
        sections.append("【当前时间】%s" % clock)
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
    return {"reply": res["text"],
            "notes": "slim=on;dropped_older_msgs=%s;history_len=%s"
                     % (handle.get("_last_dropped"), len(handle["history"]))}
