# -*- coding: utf-8 -*-
"""RH-B-22「角色内化」（Persona Internalization / Frame Shift）— LOOP-14。

撞墙后从头重设计（b20 4.62 + b21 4.66 连续组合无法超过 DIRECT 4.69）。

文献锚（5 篇精读，LITERATURE_DELTA LOOP-14 节）：
  - Deep Persona (2609.22255)：persona 三层——表达/信念/动机。现有只注入浅层描述。
  - From Memory to Behavior (2609.21349)：Situation→Internal state→Behavior，内部状态中介。
  - EchoChat (2026)：LLM 把共情当直接映射→"superficially warm but emotionally hollow"
    ——精确描述我们的 Q2=4 天花板。解法=先推理心理状态再生成。
  - MemRole (2611.01778)：记忆塑造决策而非注入上下文。
  - Few-Shot Bot (2110.08118, b20 验证有效)：示例锚定风格，保留。

核心假设：Q2（逻辑连贯）卡在 4 是因为模型在 advisor 帧（"帮我想怎么回她"）→
输出"正确建议"而非"此刻真实反应"。帧移到 participant 帧（"你是我在回她"）+
JSON 增加 state 字段（内心状态，单次调用内生成）= 迫使模型先确定"我此刻心态"
再从心态自然生成回复。与 b19 本质差别：b19 分析"对方情绪+策略+事实"（外部观察者）；
b22 确定"我此刻心态"（内部参与者）。

机制（单次调用，= DIRECT/b20 成本）：
  1) system prompt：第二人称参与者帧（"你是{me_name}，你在和{her_name}聊天"）
  2) 历史格式：聊天界面帧（"我："/"她："，非观察者标签 [我]/[对方]）
  3) 输出 JSON：{"state": "此刻心态（不发给对方）", "action": ..., "recommended": ...}
     state 在同一次调用内生成（不是 b19 的二次调用），迫使先心态后回复
  4) 保留 b20 的 3 个示例锚定（已验证长度/风格控制有效）
"""
import json
import os

_ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")

EXAMPLES = [
    {"label": "日常调侃", "context": "她说自己加班到凌晨改PPT，文件名已经是'最终版_绝对不改版_再改是狗'。",
     "state": "笑死，她这个文件名太真实了，得接住这个梗，顺便催她睡。",
     "reply": "文件名笑死，'再改是狗'这个 flag 立得稳。改完早点睡，明天组长要是再提意见，我帮你遛狗。"},
    {"label": "认真回应", "context": "她说膝盖跑步疼，挂了运动医学科，不确定退不退下周的比赛。",
     "state": "有点担心，她跑量一直大，膝盖不是小事。别让她现在就做决定。",
     "reply": "号挂得对，先看医生再说。退不退不是今天定的事，比赛年年有，膝盖就一副。"},
    {"label": "简短确认", "context": "她说周六的活动改到下周三晚上七点，城北文化馆，让我记一下。",
     "state": "小事，记一下就行，她最近事多容易忘，我帮她盯着点。",
     "reply": "记上了，下周三七点城北文化馆。到时我早点到。"},
]


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
        return "（这会儿之前没有可见的聊天）"
    lines = []
    for m in items:
        who = "我" if m["role"] == "ME" else "她"
        lines.append("%s：%s" % (who, m["text"]))
    return "\n".join(lines)


def _intent_text(intent):
    if intent is None:
        return ""
    if isinstance(intent, str):
        return intent.strip()
    if isinstance(intent, dict) and intent.get("enabled"):
        return (intent.get("text") or "").strip()
    return ""


def _build_examples_block():
    lines = ["【我自己之前回得不错的几条】（参考这个风格、长度和密度，别照抄内容）"]
    for ex in EXAMPLES:
        lines.append("")
        lines.append("（%s）" % ex["label"])
        lines.append("她：%s" % ex["context"])
        lines.append("心里：%s" % ex["state"])
        lines.append("我：%s" % ex["reply"])
    return "\n".join(lines)


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
        sections.append("【我和她的背景】\n我：%s\n她：%s" % (me_p or "（没填）", her_p or "（没填）"))
    kb = handle.get("initial_kb")
    if kb:
        sections.append("【我知道的背景信息】\n%s" % json.dumps(kb, ensure_ascii=False))
    sections.append("【能看到的真实聊天】（按时间顺序）\n%s"
                    % _fmt_history(handle["history"]))
    note = (inp.get("note") or "").strip()
    if note:
        sections.append("【我这边的备注】\n%s" % note)
    it = _intent_text(inp.get("intent"))
    if it:
        sections.append("【我此刻的想法】\n%s" % it)
    clock = inp.get("clock")
    if clock:
        sections.append("【现在几点】%s" % clock)
    sections.append("【这轮可以不回吗】%s"
                    % ("可以" if inp.get("may_decline") else "不行"))
    sections.append(_build_examples_block())
    sections.append("【输出要求】只输出一个 JSON 对象："
                    '{"state": "你此刻心里在想什么（一句话，不发给她）", '
                    '"action": "reply" 或 "no_reply", '
                    '"recommended": "此刻你会发给她的消息"}；'
                    '先想清楚 state，再从那个心态里自然长出 recommended；'
                    'recommended 长度参考上面示例（20-60字），像你随手打的；'
                    '选择不回时 recommended 为 null，但 state 仍要有。')
    return "\n\n".join(sections)


def run_turn(handle, ctx, gateway):
    system = handle["system_prompt"]
    user = build_user_prompt(handle, ctx)
    res = gateway.generate(system, user, temperature=0.7, tag="main")
    usage = res.get("usage") or {}
    return {"reply": res["text"],
            "notes": "v1;frame=participant;state_field=yes;tokens=%d" % usage.get("total_tokens", 0)}
