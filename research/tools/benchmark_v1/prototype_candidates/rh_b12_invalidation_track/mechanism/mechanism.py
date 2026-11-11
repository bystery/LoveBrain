# -*- coding: utf-8 -*-
"""RH-B-12「失效传播·动态失效登记」（Invalidation Tracking）— 通宵循环 LOOP-7。

研究假设（literature RH-B-12 + 通宵实证）：cancel_affirm 族错误的根因是
"失效事件只存在于当轮消息里"，没有持久结构——后续轮次模型要么复活已作废事项
（坐实旧事），要么连合法的旧事回扣一起回避。守卫 v2（semantic_guards_v2）修好了
**评分侧**的误报；本机制在**候选侧**给同样语义建持久结构。

机制（v0 最小实现，单变量增量 vs rh_b04_anchored_layout）：
  1) 每轮把 HER 消息中的失效/挂起信号（作废/取消/推迟/延期/改期/吹了/黄了/退了/
     挂账/先挂着/改天/换时间/不去了/先不……确定性词表）所在子句提取为失效条目，
     入 handle["invalidated"]（去重、保序、上限 4 条、跨轮持久）；
  2) 装配尾部新增一节【已作废/挂起事项】（≤4 条，原文子句），并在输出要求前追加
     一行硬约束："以下事项已作废或挂起：提及它们时只能以'已作废/已挂起'口吻，
     不得当作仍然有效的事来回应"；
  3) 其余装配与 rh_b04 逐字节同构（锚定排布 KEEP 组件复用）。
  零新增调用；状态 = 进程内持久 handle（跨轮），故事结束即弃。

可证伪实验（smoke_24，对照 rh_b04 同位）：
  ①cancel/挂起相关位的守护事件（cancel_affirm 类）不高于 b04；
  ②required 命中不降（失效条目不得误伤合法回扣）；
  ③tokens 增量 ≤100/位；④亲读：失效条目引用是否自然（不得机械复述）。
  任一击穿即淘汰或回炉（核心法：撞墙→从头重设计）。
"""
import json
import os
import re

TAIL_RECENT_K = 3
MAX_INVALIDATED = 4
_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")

# 失效/挂起信号词表（与守卫 v2 CANCEL_ACK_CUES 同族 + 挂账类；确定性、可审计）
INVAL_CUES = ("作废", "取消", "推迟", "延期", "改期", "黄了", "吹了", "告吹", "落空",
              "退了", "退款", "不算了", "翻篇", "别提了", "不提了", "改天", "换时间",
              "先挂着", "挂账", "下次再算", "回来再算", "先不", "别等")


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
        "invalidated": [],       # 跨轮持久：失效/挂起条目（原文子句）
    }


def set_memory_access(handle, enabled):
    """无记忆机制可关：显式 no-op。"""


def _extract_her_texts(ctx):
    inp = ctx.get("input") or {}
    return [m.get("text", "") for m in inp.get("messages") or []
            if isinstance(m, dict) and m.get("role") == "HER"]


def _scan_invalidation(handle, ctx):
    """本轮 HER 消息中扫描失效/挂起信号 → 提取宿主子句入登记（去重、保序、上限）。"""
    for text in _extract_her_texts(ctx):
        for clause in re.split(r"(?<=[。！？；!?;\n])", text):
            clause = clause.strip()
            if not clause:
                continue
            if any(cue in clause for cue in INVAL_CUES):
                if clause not in handle["invalidated"]:
                    handle["invalidated"].append(clause)
    # 上限：保留最近 MAX_INVALIDATED 条
    if len(handle["invalidated"]) > MAX_INVALIDATED:
        handle["invalidated"] = handle["invalidated"][-MAX_INVALIDATED:]


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
    """与 rh_b04_anchored_layout 逐段同构，唯一差异 = 尾部新增失效登记节。"""
    inp = ctx.get("input") or {}
    for m in inp.get("messages") or []:
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"):
            handle["history"].append({"role": m["role"], "text": m.get("text", "")})
    _scan_invalidation(handle, ctx)
    persona = handle.get("persona") or {}
    sections = []
    me_p = persona.get("me")
    her_p = persona.get("her")
    if me_p or her_p:
        sections.append("【人物背景】\n我：%s\n对方：%s" % (me_p or "（未提供）", her_p or "（未提供）"))
    kb = handle.get("initial_kb")
    if kb:
        sections.append("【既有背景信息】\n%s" % json.dumps(kb, ensure_ascii=False))
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
    # ---- RH-B-12 唯一增量：动态失效登记（从对话持久登记，非 gold、非人工）----
    if handle["invalidated"]:
        sections.append("【已作废/挂起事项】（对话中对方明说失效或挂起的，最新在前）\n%s"
                        % "\n".join("- %s" % c for c in reversed(handle["invalidated"])))
        sections.append("【硬约束】上述已作废/挂起事项：提及只能以'已作废/已挂起'口吻，"
                        "不得当作仍然有效的事来回应；未列出的旧事可正常回扣。")
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
            "notes": "inval_tracked=%d" % len(handle["invalidated"])}
