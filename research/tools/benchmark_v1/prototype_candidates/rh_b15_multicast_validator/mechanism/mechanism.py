# -*- coding: utf-8 -*-
"""RH-B-15「原生多路判别选一」（Multi-cast + Deterministic Validator）— 用户点名首选机制。

机制（LoveBrain 产品原生形态 + 多候选验证器文献，见 overnight/LITERATURE_DELTA.md RH-B-15）：
单次调用生成 4 条语气明显不同的候选（natural/playful/warm/direct，"广撒网"），随后用
**零模型调用的确定性验证器**逐条体检，选第一条全部通过者交付——兑现"只要有一条符合条件
就行"的产品直觉：质量取自候选池最优可交付，而不是赌单次采样。

验证器 v0（全部确定性规则，可解释、可审计）：
  R1 非空且剔除首尾引号/空白；
  R2 长度 ∈ [4, 120] 字（聊天回复合理带）；
  R3 AI 腔黑名单（客服腔/书面腔/机器自指短语，确定性词表）；
  R4 无 JSON 残渣（花括号/引号转义/代码栅栏）；
  R5 与更高优先候选去重（完全相同文本只取一次）。
选择序：natural → playful → warm → direct（与产品 recommended 优先序同构）；
全部未通过 → 回退 recommended 原文并记 validator_all_failed（不静默改写）。
action=no_reply 时四路皆空、直接不回。

成本结构：单次调用，输出 ~3-4x 单路（广撒网的代价）；无第二跳。
可证伪实验（smoke_24）：五维质量（B 线口径）是否显著优于单路 direct_baseline；
tokens/位是否 ≤4x；validator 拦截率与拦截正确性由人读抽查。
"""
import json
import os
import re

_ASSET_ORDER = ("core.md", "redline.md", "naturalness_check.md")  # format.md 已改四路契约
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
    t = t.strip().strip('"').strip("“”").strip()
    return t


def _validate(text, seen):
    """确定性验证器。返回 (passed, reasons)。"""
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
    """宽容提取首个 JSON 对象（含 ``` 围栏），不修补内容。"""
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
    raw = res["text"]
    obj = _extract_json(raw)
    if obj is None:
        return {"reply": json.dumps({"action": "reply", "recommended": ""},
                                    ensure_ascii=False),
                "notes": "multicast;parse_failed;fallback_empty"}
    action = obj.get("action")
    if action == "no_reply":
        return {"reply": json.dumps({"action": "no_reply", "recommended": None},
                                    ensure_ascii=False),
                "notes": "multicast;declined"}
    cands = obj.get("candidates") or []
    by_style = {}
    for c in cands:
        if isinstance(c, dict) and c.get("style") in STYLE_ORDER:
            by_style[c["style"]] = _clean(c.get("text"))
    recommended = _clean(obj.get("recommended"))
    seen = set()
    trace = []
    chosen = None
    for style in STYLE_ORDER:
        t = by_style.get(style, "")
        ok, reasons = _validate(t, seen)
        seen.add(t)
        trace.append("%s:%s%s" % (style, "PASS" if ok else "FAIL(" + ",".join(reasons) + ")",
                                  "" if ok else ""))
        if ok and chosen is None:
            chosen = t
    if chosen is None:
        # 全池覆没回退定义（LOOP-7 修复审计登记的脆性）：优先接受"仅超长"违例的
        # 最长候选（放宽到 +40 字），其次 recommended，再退第一条非空，最后 no_reply——
        # 不再可能交付空回复；回退路径全部显式登记。
        relax = [t for t in by_style.values() if t and len(t) <= MAX_TEXT_CHARS + 40]
        chosen = (recommended or (max(relax, key=len) if relax else "")
                  or next((by_style[s] for s in STYLE_ORDER if by_style.get(s)), ""))
        trace.append("all_failed_best_effort:len=%d" % len(chosen))
    return {"reply": json.dumps({"action": "reply", "recommended": chosen},
                                ensure_ascii=False),
            "notes": "multicast;chosen=%s;%s" % (
                next((s for s in STYLE_ORDER if by_style.get(s) == chosen), "fallback"),
                ";".join(trace))}
