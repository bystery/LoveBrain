# -*- coding: utf-8 -*-
"""DIRECT baseline（Benchmark V1 Phase 1 四组对照 · A 组）。

最小可工作直接提示基线：
- v2 机制契约（init / run_turn），复用 arena/lb_runner.run_episode 全生命周期
  （消毒白名单、网关记账、calls.json、逐轮 manifest、检查点）；
- 可见输入 = lb_runner sanitize_turn 白名单面（messages/clock/intent/may_decline/note/kb
  + persona/initial_kb），不接触 gold/reference/grading cards/未来轮次；
- 自累积"可见真实聊天历史"（HER/ME 剧本消息），不获取未发送的候选回复、不虚构历史；
- 单次模型调用；输出轻量契约 {"action","recommended"}，不带 LoveBrain 记忆策略/阶段机制。
COT 变体仅多一行固定思考提示（见 cot_baseline），其余逐字节同构——输入等价测试据此断言。
"""
import json
import os

# COT 变体置 True 并由 cot_baseline 的 mechanism.py 覆盖指令文本。
COT_MODE = False
COT_INSTRUCTION = ""

_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")


def _read_assets(assets_root):
    parts = []
    for rel in _ASSET_ORDER:
        p = os.path.join(assets_root, "system_prompt", rel)
        with open(p, "r", encoding="utf-8") as f:
            parts.append(f.read().strip())
    system = "\n\n".join(parts)
    if COT_MODE and COT_INSTRUCTION:
        system = system + "\n\n" + COT_INSTRUCTION
    return system


def init(assets_root, ep_meta, gateway, sandbox):
    """v2 生命周期：无独立记忆存储（状态=累积可见历史，存 handle）。"""
    return {
        "system_prompt": _read_assets(assets_root),
        "persona": ep_meta.get("persona") or {},
        "initial_kb": ep_meta.get("initial_kb") or {},
        "history": [],           # 可见真实聊天（HER/ME 剧本消息，时间序累积）
    }


def set_memory_access(handle, enabled):
    """DIRECT/COT 无记忆机制可关：显式 no-op（lb_runner 仅在 --memory-off 时调用，
    本基线不使用该模式）。"""


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
