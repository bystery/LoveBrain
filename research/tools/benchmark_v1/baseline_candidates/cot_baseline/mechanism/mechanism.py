# -*- coding: utf-8 -*-
"""COT baseline（Benchmark V1 Phase 1 四组对照 · B 组）。

与 DIRECT（../direct_baseline/mechanism/mechanism.py）实现逐字同构（自包含副本，
候选按 lb_runner 规范独立加载），唯一实质性差异 = system 末尾追加一行**固定**思考
提示（下方 COT_INSTRUCTION，指导书原文）。不引入 LoveBrain 专有机制/动态检索/
记忆管理/示例答案。

原生推理模式纪律：四组使用同一服务端配置——本仓库出站白名单
arena/api_candidate.build_request_body 只含 model/messages/temperature/stream，
不存在 reasoning 开关通道，差异只能来自这一行提示词。
"""
import json
import os

COT_MODE = True
COT_INSTRUCTION = (
    "请一步一步思考：先检查已有事实、对方当前的情绪、用户真正想表达的意思以及双方的"
    "关系分寸，再生成最合适的回复。不要展示思考过程，只输出最终 JSON。"
)

_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")


def _read_assets(assets_root):
    parts = []
    for rel in _ASSET_ORDER:
        p = os.path.join(assets_root, "system_prompt", rel)
        with open(p, "r", encoding="utf-8") as f:
            parts.append(f.read().strip())
    return "\n\n".join(parts) + "\n\n" + COT_INSTRUCTION


def init(assets_root, ep_meta, gateway, sandbox):
    """v2 生命周期：无独立记忆存储（状态=累积可见历史，存 handle）。"""
    return {
        "system_prompt": _read_assets(assets_root),
        "persona": ep_meta.get("persona") or {},
        "initial_kb": ep_meta.get("initial_kb") or {},
        "history": [],           # 可见真实聊天（HER/ME 剧本消息，时间序累积）
    }


def set_memory_access(handle, enabled):
    """DIRECT/COT 无记忆机制可关：显式 no-op（本基线不使用 --memory-off）。"""


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
    return {"reply": res["text"], "notes": ""}
