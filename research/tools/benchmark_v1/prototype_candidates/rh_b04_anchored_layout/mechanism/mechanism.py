# -*- coding: utf-8 -*-
"""RH-B-04 「证据锚定排布」（Evidence Anchored Layout）— RESEARCH-P1 首批原型。

研究假设（literature/MECHANISM_HYPOTHESES.md RH-B-04，支撑论文：
Lost in the Middle arXiv:2307.03172 full-read + PaRT arXiv:2504.20624 full-read）：
LLM 对提示首尾信息的利用率显著高于中部（U 型曲线）。把"本轮必答/意图/时间/最近消息"
锚定在提示尾部贴近生成点，历史与背景置中，可在零新增调用下改善长程逐字保持与约束遵循。

机制层唯一变化 = 装配顺序（对 direct_baseline 的逐段对照）：
  头部 = 人物背景（身份/关系）
  中部 = 既有背景信息 kb + 可见聊天记录（历史部分：去掉最近 K 条）
  尾部 = 我的备注 + 我的意图 + 当前时间 + 最近 K 条原文（贴近生成点）+ 允许不回 + 输出要求

其余与 direct_baseline 逐字节同构：同一资产四件套、同一轻量契约 {"action","recommended"}、
同一 v2 生命周期（init/run_turn）、同一消毒白名单可见面、零记忆机制。
K 固定为 3，确定性，无随机。
"""
import json
import os

COT_MODE = False
COT_INSTRUCTION = ""

TAIL_RECENT_K = 3          # 锚定到尾部的最近消息条数（P1-06 预登记，不在运行中改）
_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")


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
        "history": [],           # 可见真实聊天（HER/ME 剧本消息，时间序累积）
    }


def set_memory_access(handle, enabled):
    """无记忆机制可关：显式 no-op（与 direct_baseline 同口径）。"""


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
    """与 direct_baseline 唯一差异：分段顺序与最近消息的锚定位置。
    逐段文本内容与 direct_baseline 完全一致（隔离"排布"单一变量）。"""
    inp = ctx.get("input") or {}
    for m in inp.get("messages") or []:
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"):
            handle["history"].append({"role": m["role"], "text": m.get("text", "")})
    persona = handle.get("persona") or {}
    sections = []
    # ---- 头部：身份与关系 ----
    me_p = persona.get("me")
    her_p = persona.get("her")
    if me_p or her_p:
        sections.append("【人物背景】\n我：%s\n对方：%s" % (me_p or "（未提供）", her_p or "（未提供）"))
    # ---- 中部：背景信息 + 历史（去掉最近 K 条） ----
    kb = handle.get("initial_kb")
    if kb:
        sections.append("【既有背景信息】\n%s" % json.dumps(kb, ensure_ascii=False))
    history = handle["history"]
    older = history[:-TAIL_RECENT_K] if len(history) > TAIL_RECENT_K else []
    if older:
        sections.append("【可见聊天记录】（按时间顺序，只含真实发生过的消息）\n%s"
                        % _fmt_history(older))
    # ---- 尾部：本轮任务要素 + 最近 K 条原文（贴近生成点） ----
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
            "notes": "layout=anchored_tail_k%d" % TAIL_RECENT_K}
