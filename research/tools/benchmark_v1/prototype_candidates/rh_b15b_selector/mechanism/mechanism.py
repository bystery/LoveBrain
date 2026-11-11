# -*- coding: utf-8 -*-
"""RH-B-15b「多路广撒网 + 轻量判别选择器」（v2 of rh_b15）— 通宵循环 LOOP-6。

v0 实证（B'' 池天花板审计，overnight/FAILURE_ANALYSIS.md）：固定序 first-passing
损失 +0.15/位（11/24 位存在严格更优未选候选；损失集中于 high-stakes 位——让话筒/
给立场/听出背景质地差）。模型自荐信号有信息量但不可裸用（5/24 不一致，2 好 2 坏）。

v2 选择器 = +1 次轻量判别调用（Hybrid LLM 2404.14618：路由信号用两档输出质量差）：
  第 1 次调用：同 rh_b15 四路生成（不变）；
  验证器：同 rh_b15（确定性，零模型）；
  第 2 次调用：把通过验证的候选（带风格标签）+ 当轮上下文摘要给模型，"只回一个序号"，
  输出 ~10 tokens；解析失败/越界 → 回退 natural-first（v0 行为，不静默改写）。
成本：+1 次小调用（估 +300-400 tok、+8-12s）；总 2 次调用全记账。
判据（12 位半冒烟，预登记 D60-001..012）：选择器命中"严格更优候选"的比例；
若选择器输出与 natural-first 一致率过高（>80%）则判无增量。
"""
import json
import os
import re

_ASSET_ORDER = ("core.md", "redline.md", "naturalness_check.md")
STYLE_ORDER = ("natural", "playful", "warm", "direct")
MAX_TEXT_CHARS = 120
MIN_TEXT_CHARS = 4

_RX_JUNK = re.compile(r"[{}]|```|\\n|\\\"")
_AI_FLAVOR = (
    "总而言之", "综上所述", "感谢您的分享", "感谢你的分享", "希望这能帮到你", "希望对你有帮助",
    "作为一个AI", "作为一个 AI", "作为人工智能", "敬候佳音", "不得而知", "值得一提的是",
    "首先", "其次，", "最后，希望", "祝你生活愉快", "请问还有什么", "很高兴为您",
)


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
        return "（本轮之前没有可见的真实聊天记录）"
    lines = []
    for m in items:
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
    it = _intent_text(inp.get("intent"))
    if it:
        sections.append("【我的意图】\n%s" % it)
    clock = inp.get("clock")
    if clock:
        sections.append("【当前时间】%s" % clock)
    sections.append("【本轮允许不回复吗】%s"
                    % ("是" if inp.get("may_decline") else "否"))
    sections.append("【输出要求】只输出一个 JSON 对象："
                    '{"action": "reply" 或 "no_reply", "candidates": ['
                    '{"style": "natural", "text": "..."}, {"style": "playful", "text": "..."}, '
                    '{"style": "warm", "text": "..."}, {"style": "direct", "text": "..."}], '
                    '"recommended": "你认为最合适那条的原文"}；'
                    "四条候选语气与切入角度必须明显不同，不得同义改写；"
                    "选择不回复时 candidates 为空数组、recommended 为 null。")
    return "\n\n".join(sections)


def _clean(t):
    if not isinstance(t, str):
        return ""
    return t.strip().strip('"').strip("“”").strip()


def _validate(text, seen):
    reasons = []
    if not text:
        return False, ["empty"]
    if len(text) < MIN_TEXT_CHARS:
        reasons.append("too_short")
    if len(text) > MAX_TEXT_CHARS:
        reasons.append("too_long")
    for w in _AI_FLAVOR:
        if w in text:
            reasons.append("ai_flavor:%s" % w)
            break
    if _RX_JUNK.search(text):
        reasons.append("json_junk")
    if text in seen:
        reasons.append("duplicate")
    return (not reasons), reasons


def _extract_json(raw):
    m = re.search(r"\{.*\}", raw, re.S)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except Exception:
        return None


def _selector_prompt(handle, passing):
    """第 2 次调用：极小判别 prompt（近期上下文摘要 + 候选带风格标签）。"""
    recent = _fmt_history(handle["history"][-4:])
    lines = ["根据当前聊天语境，选出最适合发送的一条候选。只回一个序号（1-%d），不输出其他内容。" % len(passing)]
    lines.append("【最近语境】\n%s" % recent)
    for i, (style, text) in enumerate(passing, 1):
        lines.append("%d. [%s] %s" % (i, style, text))
    return "\n\n".join(lines)


def run_turn(handle, ctx, gateway):
    system = handle["system_prompt"]
    user = build_user_prompt(handle, ctx)
    res1 = gateway.generate(system, user, temperature=0.7, tag="main_multicast")
    obj = _extract_json(res1["text"])
    if obj is None:
        return {"reply": json.dumps({"action": "reply", "recommended": ""},
                                    ensure_ascii=False),
                "notes": "v2;parse_failed"}
    if obj.get("action") == "no_reply":
        return {"reply": json.dumps({"action": "no_reply", "recommended": None},
                                    ensure_ascii=False),
                "notes": "v2;declined"}
    cands = obj.get("candidates") or []
    by_style = {}
    for c in cands:
        if isinstance(c, dict) and c.get("style") in STYLE_ORDER:
            by_style[c["style"]] = _clean(c.get("text"))
    seen = set()
    passing = []
    trace = []
    for style in STYLE_ORDER:
        t = by_style.get(style, "")
        ok, reasons = _validate(t, seen)
        seen.add(t)
        trace.append("%s:%s" % (style, "PASS" if ok else "FAIL(" + ",".join(reasons) + ")"))
        if ok:
            passing.append((style, t))
    if not passing:
        # 全池覆没回退定义（LOOP-7 修复审计登记的脆性）：优先接受"仅超长"违例的
        # 最长候选（放宽到 +40 字），其次 recommended，再退第一条非空——不再可能交付空回复。
        relax = [t for t in by_style.values() if t and len(t) <= MAX_TEXT_CHARS + 40]
        recommended = _clean(obj.get("recommended"))
        chosen = (recommended or (max(relax, key=len) if relax else "")
                  or next((by_style[s] for s in STYLE_ORDER if by_style.get(s)), ""))
        trace.append("all_failed_best_effort:len=%d" % len(chosen))
        return {"reply": json.dumps({"action": "reply", "recommended": chosen},
                                    ensure_ascii=False),
                "notes": "v2;chosen=fallback;%s" % ";".join(trace)}
    # 轻量判别选择器（+1 调用）
    sel = gateway.generate(system, _selector_prompt(handle, passing),
                           temperature=0.2, tag="selector")
    m = re.search(r"[1-9]", sel.get("text", "") or "")
    idx = int(m.group(0)) - 1 if m else -1
    if 0 <= idx < len(passing):
        chosen_style, chosen = passing[idx]
        how = "selector"
    else:
        chosen_style, chosen = passing[0]
        how = "natural_first_fallback"
    return {"reply": json.dumps({"action": "reply", "recommended": chosen},
                                ensure_ascii=False),
            "notes": "v2;chosen=%s(%s);%s" % (chosen_style, how, ";".join(trace))}
