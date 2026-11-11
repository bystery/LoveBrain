# -*- coding: utf-8 -*-
"""RH-B-18「自精炼+模态选择」（Self-Refine + ModeX Selection）— LOOP-10。

研究假设（文献驱动，核心法 §-1② 合规）：
  b15 池天花板 +0.32 是**选择层**极限——四路候选里挑一条，天花板就这么高。
  要达到核心法 ≥50% 绝对提升（DIRECT 4.69→~7.0），必须进**生成层**改进。

文献锚：
  - Self-Refine（2303.17651, NeurIPS 2023, Madaan et al.）：同一模型生成→自反馈→重写，
    七任务平均 +20% 绝对提升，无需训练/reward model/RL。对话任务在内。
  - ModeX（2601.02535, ACL 2026 Main, Choi & Li）：无验证器 BoN——候选间语义相似度图
    →谱聚类→选最大簇中心代表。零额外模型调用。解决 b15b 选择器的 +1 调用瓶颈。
  - Score Before You Speak（2508.06886）：对话回复质量预评分驱动人格一致性。

机制（两阶段，2 次调用）：
  第 1 次调用：同 rh_b15 四路广撒网（natural/playful/warm/direct），验证器 v0 过滤。
  第 1.5 步（零调用）：ModeX 选择——通过验证的候选做 bigram Jaccard 相似度图，
    贪心聚类找最大簇，选簇中与其他成员平均相似度最高的那条（模态中心）。
    若仅 1 条通过→直接用它。若全部相似度 <0.1→语义发散，回退 natural-first。
  第 2 次调用：Self-Refine——把 ModeX 选中的候选 + 完整上下文给模型，
    要求"在不改变事实的前提下，让这条回复更自然、更像真人随手打的"，
    输出精炼后的最终回复。temp 0.7（保持创造力）。

与 b15/b15b 的本质差别：
  - b15/b15b = 选择层（从池里挑，不改候选内容）→天花板 +0.32
  - b18 = 选择 + 生成层改进（选一条→重写得更好）→目标是突破选择层天花板

成本：2 次调用/轮（b15b 也是 2 次，但 b15b 第 2 次只选不改；b18 第 2 次改写=生成层收益）。

预登记判据（smoke_24，对照 rh_b15b 同位，五维目标 ~7.0 = +50%）：
  ①五维均值 ≥ b15b（4.95→目标 7.0 是整个循环目标，不是单轮判据；单轮 ≥5.1=+0.15 即有价值信号）；
  ②severe ≤ b15b；③tokens/位 ≤ b15b×1.3（Self-Refine 多一次调用，但第 2 次输入小）；
  ④亲读：精炼后回复是否确实比精炼前更好（A/B 对照同位）；
  ⑤ModeX 选择命中率（与 b15b 选择器选择一致性 >70% 说明 ModeX 可靠替代）。
"""
import json
import os
import re

_ASSET_ORDER = ("core.md", "redline.md", "naturalness_check.md", "format.md")
STYLE_ORDER = ("natural", "playful", "warm", "direct")
MAX_TEXT_CHARS = 150      # 放宽到 150（Self-Refine 可能微增长度）
MIN_TEXT_CHARS = 4
REFINE_MAX_CHARS = 160

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
    """第 1 次调用 prompt：同 b15 四路广撒网。"""
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


def _bigrams(s):
    """字符 bigram 集合（用于 ModeX 语义相似度近似）。"""
    s = re.sub(r"[^\u4e00-\u9fff0-9a-zA-Z]", "", s or "")
    toks = set()
    han = re.findall(r"[\u4e00-\u9fff]", s)
    for i in range(len(han) - 1):
        toks.add(han[i] + han[i + 1])
    toks.update(re.findall(r"[0-9a-zA-Z]{2,}", s))
    return toks


def _jaccard(a, b):
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def _modex_select(passing):
    """ModeX 模态选择（零调用）：候选间 bigram Jaccard → 贪心聚类 → 选最大簇中心。

    ModeX 原文用谱聚类+语义嵌入；我们用 bigram Jaccard 近似（零依赖、确定性、毫秒级）。
    返回 (chosen_text, chosen_style, method)。
    """
    if len(passing) == 1:
        return passing[0][1], passing[0][0], "single_pass"

    # 构建相似度矩阵
    bgs = [_bigrams(t) for _, t in passing]
    n = len(passing)

    # 贪心聚类：每条候选与其他候选的平均 Jaccard
    avg_sims = []
    for i in range(n):
        sims = [_jaccard(bgs[i], bgs[j]) for j in range(n) if j != i]
        avg_sims.append(sum(sims) / len(sims) if sims else 0.0)

    # 最大平均相似度 = 模态中心（最多其他候选与它语义相近）
    best_idx = max(range(n), key=lambda i: avg_sims[i])

    # 如果最大平均相似度 <0.1 → 候选语义发散，回退 natural-first（诚实降级）
    if avg_sims[best_idx] < 0.10:
        return passing[0][1], passing[0][0], "divergent_fallback_natural"

    return passing[best_idx][1], passing[0][0] if best_idx == 0 else passing[best_idx][0], \
        "modex_cluster_center(sim=%.2f)" % avg_sims[best_idx]


def _refine_prompt(handle, chosen_text):
    """第 2 次调用 prompt：Self-Refine 精炼。

    Self-Refine 原文：同一模型给反馈再重写。我们合并为一步——
    给候选 + 上下文摘要，要求"直接输出精炼后的版本"（省掉中间反馈 token）。
    """
    recent = _fmt_history(handle["history"][-4:])
    persona = handle.get("persona") or {}
    me_p = persona.get("me") or "我"
    her_p = persona.get("her") or "对方"
    lines = [
        "你刚才帮%s草拟了一条发给%s的回复。请在不改变任何事实信息的前提下，"
        "把它改得更自然——更像真人随手打出来的微信消息。" % (me_p, her_p),
        "只输出精炼后的回复文本，不要输出 JSON、不要解释、不要加引号。",
        "",
        "【最近语境】",
        recent,
        "",
        "【草拟的回复】",
        chosen_text,
        "",
        "【精炼要求】",
        "- 保持原意和事实不变（人名/时间/地点/承诺不得篡改）",
        "- 更口语化、更短句、更像日常聊天",
        "- 去掉任何书面腔/客服腔/AI味",
        "- 可以调整语气但不要改变核心意思",
        "- 直接输出一条消息，不要编号不要解释",
    ]
    return "\n".join(lines)


def run_turn(handle, ctx, gateway):
    system = handle["system_prompt"]
    user = build_user_prompt(handle, ctx)

    # ---- 第 1 次调用：四路广撒网 ----
    res1 = gateway.generate(system, user, temperature=0.7, tag="main_multicast")
    obj = _extract_json(res1["text"])
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

    # ---- 验证器 v0（同 b15，确定性，零调用）----
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
        # 全池覆没回退（同 b15b LOOP-7 修复）
        relax = [t for t in by_style.values() if t and len(t) <= MAX_TEXT_CHARS + 40]
        recommended = _clean(obj.get("recommended"))
        chosen = (recommended or (max(relax, key=len) if relax else "")
                  or next((by_style[s] for s in STYLE_ORDER if by_style.get(s)), ""))
        trace.append("all_failed_best_effort:len=%d" % len(chosen))
        # 即使回退也要尝试精炼
        passing = [("fallback", chosen)] if chosen else []

    if not passing:
        return {"reply": json.dumps({"action": "reply", "recommended": ""},
                                    ensure_ascii=False),
                "notes": "v1;all_empty;%s" % ";".join(trace)}

    # ---- 第 1.5 步：ModeX 模态选择（零调用）----
    chosen_text, chosen_style, sel_method = _modex_select(passing)

    # ---- 第 2 次调用：Self-Refine 精炼 ----
    refine_sys = system  # 复用同一 system（含红线/自然度约束）
    refine_user = _refine_prompt(handle, chosen_text)
    res2 = gateway.generate(refine_sys, refine_user, temperature=0.7, tag="self_refine")

    refined = res2.get("text", "").strip().strip('"').strip("""""").strip()
    # 安全检查：精炼结果不能比原候选差（长度/空/AI味）
    if not refined or len(refined) < MIN_TEXT_CHARS:
        refined = chosen_text  # 降级为未精炼
        trace.append("refine_empty_fallback")
    elif len(refined) > REFINE_MAX_CHARS:
        refined = refined[:REFINE_MAX_CHARS]  # 截断超长
        trace.append("refine_truncated")
    elif any(w in refined for w in _AI_FLAVOR):
        refined = chosen_text  # 精炼引入了 AI 味→降级
        trace.append("refine_ai_flavor_fallback")
    elif _RX_JUNK.search(refined):
        refined = chosen_text  # 精炼引入了 JSON 垃圾→降级
        trace.append("refine_junk_fallback")

    refined_changed = (refined != chosen_text)

    return {"reply": json.dumps({"action": "reply", "recommended": refined},
                                ensure_ascii=False),
            "notes": "v1;sel=%s;refine_changed=%s;pre_len=%d;post_len=%d;%s" % (
                sel_method, refined_changed, len(chosen_text), len(refined),
                ";".join(trace))}
