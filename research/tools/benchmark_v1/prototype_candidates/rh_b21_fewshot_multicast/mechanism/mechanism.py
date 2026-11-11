# -*- coding: utf-8 -*-
"""RH-B-21「示例锚定多路广播」（Few-Shot Anchored Multicast）— LOOP-13。

组合 b20（示例锚定单次，五维 4.62 追平 DIRECT）+ b15（多路广播池天花板 +0.32）。
单次调用生成 4 路候选 + 示例锚定长度/风格 + 确定性验证器 + 池选优。

文献锚：
  - Few-Shot Bot (2110.08118)：示例注入锚定风格（b20 已验证 -1.4% 追平 DIRECT）
  - The Role of Diversity in ICL (2505.19426)：多样化示例提升 ICL 性能
  - Large Language Monkeys (2407.21787)：多候选 coverage 随 N 陡增，瓶颈在选择器

机制（单次调用，= b15/b20 成本）：
  第 1 次调用：同 b15 四路广撒网（natural/playful/warm/direct），但 prompt 注入 b20 的示例
  验证器 v0（零调用）：同 b15 确定性过滤
  选择：验证器通过的第一条（natural-first，简单可靠——b15b 选择器只 +0.12，不值得 +1 调用）

与 b15 的差别：b15 无示例锚定 → 候选可能过长/过短/AI 味；b21 有示例锚定 → 候选长度和风格锚定
与 b20 的差别：b20 单路单选；b21 四路广撒网 → 池天花板 +0.32 的收益
"""
import json
import os
import re

_ASSET_ORDER = ("core.md", "redline.md", "naturalness_check.md", "format.md")
STYLE_ORDER = ("natural", "playful", "warm", "direct")
MAX_TEXT_CHARS = 120
MIN_TEXT_CHARS = 4

_RX_JUNK = re.compile(r"[{}]|```|\\n|\\\"")
_AI_FLAVOR = (
    "总而言之", "综上所述", "感谢您的分享", "感谢你的分享", "希望这能帮到你", "希望对你有帮助",
    "作为一个AI", "作为一个 AI", "作为人工智能", "敬候佳音", "不得而知", "值得一提的是",
    "首先", "其次，", "最后，希望", "祝你生活愉快", "请问还有什么", "很高兴为您",
)

EXAMPLES = [
    {"label": "日常调侃", "context": "她说自己加班到凌晨改PPT，文件名已经是'最终版_绝对不改版_再改是狗'。",
     "reply": "文件名笑死，'再改是狗'这个 flag 立得稳。改完早点睡，明天组长要是再提意见，我帮你遛狗。"},
    {"label": "认真回应", "context": "她说膝盖跑步疼，挂了运动医学科，不确定退不退下周的比赛。",
     "reply": "号挂得对，先看医生再说。退不退不是今天定的事，比赛年年有，膝盖就一副。"},
    {"label": "简短确认", "context": "她说周六的活动改到下周三晚上七点，城北文化馆，让我记一下。",
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


def _build_examples_block():
    lines = ["【参考示例】（以下是我之前回复得不错的几条，参考这个风格、长度和密度）"]
    for ex in EXAMPLES:
        lines.append("")
        lines.append("（%s）" % ex["label"])
        lines.append("对方：%s" % ex["context"])
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
    # ---- RH-B-21 增量：示例锚定（b20 验证有效）----
    sections.append(_build_examples_block())
    sections.append("【输出要求】只输出一个 JSON 对象："
                    '{"action": "reply" 或 "no_reply", "candidates": ['
                    '{"style": "natural", "text": "..."}, {"style": "playful", "text": "..."}, '
                    '{"style": "warm", "text": "..."}, {"style": "direct", "text": "..."}], '
                    '"recommended": "你认为最合适那条的原文"}；'
                    "四条候选语气与切入角度必须明显不同，不得同义改写；"
                    "每条回复长度参考上面示例（20-60字），像真人随手打的消息；"
                    "选择不回复时 candidates 为空数组、recommended 为 null。")
    return "\n\n".join(sections)


def _clean(t):
    if not isinstance(t, str):
        return ""
    return t.strip().strip('"').strip("""""").strip()


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


def run_turn(handle, ctx, gateway):
    system = handle["system_prompt"]
    user = build_user_prompt(handle, ctx)
    res = gateway.generate(system, user, temperature=0.7, tag="main_multicast")
    obj = _extract_json(res["text"])
    if obj is None:
        return {"reply": json.dumps({"action": "reply", "recommended": ""},
                                    ensure_ascii=False),
                "notes": "v1;parse_failed"}
    if obj.get("action") == "no_reply":
        return {"reply": json.dumps({"action": "no_reply", "recommended": None},
                                    ensure_ascii=False),
                "notes": "v1;declined"}

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
        relax = [t for t in by_style.values() if t and len(t) <= MAX_TEXT_CHARS + 40]
        recommended = _clean(obj.get("recommended"))
        chosen = (recommended or (max(relax, key=len) if relax else "")
                  or next((by_style[s] for s in STYLE_ORDER if by_style.get(s)), ""))
        trace.append("all_failed_best_effort:len=%d" % len(chosen))
        return {"reply": json.dumps({"action": "reply", "recommended": chosen},
                                    ensure_ascii=False),
                "notes": "v1;chosen=fallback;%s" % ";".join(trace)}

    # Natural-first selection (simple, reliable — b15b selector only +0.12, not worth +1 call)
    chosen_style, chosen = passing[0]
    pool_best = max((t for _, t in passing), key=len) if len(set(t for _, t in passing)) > 1 else chosen

    return {"reply": json.dumps({"action": "reply", "recommended": chosen},
                                ensure_ascii=False),
            "notes": "v1;chosen=%s(natural_first);pool=%d;%s" % (chosen_style, len(passing), ";".join(trace))}
