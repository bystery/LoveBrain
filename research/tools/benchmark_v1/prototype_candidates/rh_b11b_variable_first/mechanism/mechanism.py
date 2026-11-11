# -*- coding: utf-8 -*-
"""RH-B-11b「易变前缀排布」（对照对 B 臂）— 与 rh_b11a 完全相同的内容要素，
仅装配顺序把易变块放最前（history→note→intent→clock→persona→kb→…），
使同 episode 多次调用的请求前缀互不相同 → 端点前缀缓存应不命中。
与 A 臂成对测量 cached_tokens/时延差异；质量必须等价（同题同内容）。
"""
import json
import os

COT_MODE = False
COT_INSTRUCTION = ""

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
    # ---- 易变块在前（B 臂）：history → note → intent → clock ----
    sections.append("【可见聊天记录】（按时间顺序，只含真实发生过的消息）\n%s"
                    % _fmt_history(handle["history"]))
    note = (inp.get("note") or "").strip()
    if note:
        sections.append("【我的备注】\n%s" % note)
    ib = _intent_block(inp.get("intent"))
    if ib:
        sections.append(ib.strip())
    clock = inp.get("clock")
    if clock:
        sections.append("【当前时间】%s" % clock)
    # ---- 稳定块在后 ----
    me_p = persona.get("me")
    her_p = persona.get("her")
    if me_p or her_p:
        sections.append("【人物背景】\n我：%s\n对方：%s" % (me_p or "（未提供）", her_p or "（未提供）"))
    kb = handle.get("initial_kb")
    if kb:
        sections.append("【既有背景信息】\n%s" % json.dumps(kb, ensure_ascii=False))
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
    return {"reply": res["text"], "notes": "order=variable_first"}
