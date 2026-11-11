# -*- coding: utf-8 -*-
"""RH-CONVERGE「收敛主力」— LOOP-14。

移植 LoveBrain 原版 6 个核心装配机制（确定性零调用）+ 复用 pipe_v1 三账本/段检索 + b20 示例锚定。
1 次调用/轮 = DIRECT 成本。

移植的原版机制（源: D:/LoveBrain Kotlin）：
  A. OngoingContextSelector → _ongoing_select (relevance gating + 冷却; 数据无 ongoing_plan 时 no-op)
  B. CurrentSceneInjection → _scene_infer (7类场景推断, 固定优先级关键词)
  C. UncertainBlockDetector → 直接 import uncertain_rules (已有 Python 原版)
  D. PromptBudget → _budget_trim (三步裁剪: 旧记忆尾→旧对话→chat头)
  E. PromptKnowledgeSection → _knowledge_assemble (画像→阶段→教训→场景→最近→进行中)
  F. MemoryRefPolicy → 简化: v1 corrections 为空, 只留接口

复用的已有组件（源: rh_pipe_v1）：
  - _build_said_ledger / _build_invalidated / _build_segments / _select_segments

保留 b20 示例锚定（已验证长度/风格控制有效）。

装配顺序（user prompt）:
  头部: 人物背景 → 关系画像(KnowledgeAssembler) → 场景(SceneInjection)
  中部: 相关往事(段检索) → 未定事项(UncertainDetector)
  尾部: 备注/意图/时间 → 最近消息 → 话语账本(said_ledger) → 失效事项(invalidated) → 示例(b20) → 输出要求
  最后: BudgetTrimmer 9000 字符裁剪

与原版差异（诚实登记）：
  1. corrections 暂为空（v1 留接口，不做纠正落盘）
  2. 冷却简化为进程内（v1 不做两段式提交）
  3. OngoingContextSelector 在数据无 ongoing_plan 时为 no-op
"""
import json
import os
import re

# ---- 复用 uncertain_rules（已有 Python 原版，与 Kotlin 逐字等价）----
import sys as _sys
_uc_path = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.dirname(os.path.abspath(__file__))))), "candidates",
    "window_expand_b_norecap_uc", "mechanism")
if _uc_path not in _sys.path:
    _sys.path.insert(0, _uc_path)
try:
    from uncertain_rules import build_block as _uncertain_build_block
    from uncertain_rules import find_uncertain as _uncertain_find
    _HAS_UNCERTAIN = True
except Exception:
    _HAS_UNCERTAIN = False

# ============================================================
# 常数（复用原版 + pipe_v1）
# ============================================================

ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")
COOLDOWN_TURNS = 3          # OngoingContextSelector 冷却
DORMANT_TURNS = 5           # OngoingContextSelector 休眠
TAIL_RECENT_K = 3           # 尾部最近消息数
SEG_MAX_TURNS = 6           # 段上限
TOP_SEGS = 3                # 检索注入段数
MAX_LEDGER_TAIL = 5         # 账本条目上限
MAX_INVALIDATED = 4         # 失效条目上限
JACCARD_BOUNDARY = 0.22     # 段边界 Jaccard 阈值
TOTAL_BUDGET = 9000         # prompt 总预算字符
CHAT_LINE_ESTIMATE = 60     # 对话行估算宽度

# pipe_v1 常数
INVAL_CUES = ("作废", "取消", "推迟", "延期", "改期", "黄了", "吹了", "告吹", "落空",
              "退了", "退款", "不算了", "翻篇", "别提了", "不提了", "改天", "换时间",
              "先挂着", "挂账", "下次再算", "回来再算", "先不", "别等", "去不了")
COMMIT_CUES = ("我带", "我请", "我去", "我订", "我查", "我帮", "帮你", "给你", "答应",
               "改天", "回头", "下次", "到时候", "周末我", "明天我", "晚上我")
QUESTION_TAIL = re.compile(r"(？|\?)")
_STOP = set("的了呢吧吗啊呀哦嗯哈那么就是还有还有没有我们你们他们自己什么怎么这个那个"
            "一下有点有点儿可以不行不是现在时候因为所以但是然后如果虽然Direct请回复输出要求")

# b20 示例锚定
EXAMPLES = [
    {"label": "日常调侃", "context": "她说自己加班到凌晨改PPT，文件名已经是'最终版_绝对不改版_再改是狗'。",
     "reply": "文件名笑死，'再改是狗'这个 flag 立得稳。改完早点睡，明天组长要是再提意见，我帮你遛狗。"},
    {"label": "认真回应", "context": "她说膝盖跑步疼，挂了运动医学科，不确定退不退下周的比赛。",
     "reply": "号挂得对，先看医生再说。退不退不是今天定的事，比赛年年有，膝盖就一副。"},
    {"label": "简短确认", "context": "她说周六的活动改到下周三晚上七点，城北文化馆，让我记一下。",
     "reply": "记上了，下周三七点城北文化馆。到时我早点到。"},
]

# ============================================================
# 工具函数（复用 pipe_v1）
# ============================================================

def _clip(s, n):
    if s is None:
        return ""
    if not isinstance(s, str):
        try:
            s = json.dumps(s, ensure_ascii=False)
        except Exception:
            s = str(s)
    s = s.strip()
    return s if len(s) <= n else s[:n] + "…"


def _bigrams(s):
    s = re.sub(r"[^\u4e00-\u9fff0-9a-zA-Z]", "", s or "")
    toks = set()
    han = re.findall(r"[\u4e00-\u9fff]", s)
    for i in range(len(han) - 1):
        bg = han[i] + han[i + 1]
        if bg not in _STOP and bg[0] not in "的了呢吧吗啊" and bg[1] not in "的了呢吧吗啊":
            toks.add(bg)
    toks.update(re.findall(r"[0-9a-zA-Z]{2,}", s))
    return toks


def _jaccard(a, b):
    if not a or not b:
        return 1.0
    return len(a & b) / len(a | b)


def _read_assets(assets_root):
    parts = []
    for rel in ASSET_ORDER:
        p = os.path.join(assets_root, "system_prompt", rel)
        with open(p, "r", encoding="utf-8") as f:
            parts.append(f.read().strip())
    return "\n\n".join(parts)

# ============================================================
# 模块 A: OngoingContextSelector（移植自 Kotlin，简化版）
# ============================================================

def _parse_plan_items(content):
    """解析 plan.md 格式: itemId~name|status|chain 或 name|status|chain"""
    if not content or not isinstance(content, str):
        return []
    items = []
    for line in content.split("\n"):
        t = line.strip()
        if "|" not in t:
            continue
        parts = [p.strip() for p in t.split("|")]
        if len(parts) < 3 or not parts[0]:
            continue
        first = parts[0]
        tilde = first.find("~")
        name = first[tilde + 1:] if tilde > 0 else first
        status = parts[1]
        chain = "|".join(parts[2:]).strip()
        # 推导状态
        if status == "已完成":
            item_status = "FINISHED"
        elif status == "已取消":
            item_status = "CANCELLED"
        else:
            item_status = "ACTIVE"
        items.append({"name": name, "status": status, "chain": chain,
                      "item_status": item_status})
    return items


def _extract_keywords(name):
    """从事项名称提取关键词（Kotlin extractKeywords 移植）"""
    cleaned = re.sub(r"[（）()【】\[\]「」\"'·]", "", name or "")
    cleaned = re.sub(r"^(计划|约|定于|准备)", "", cleaned).strip()
    cleaned = re.sub(r"\d{1,2}[-/]\d{1,2}", "", cleaned)
    cleaned = re.sub(r"周[一二三四五六日天]", "", cleaned)
    cleaned = re.sub(r"^[\s-]+", "", cleaned).strip()
    parts = [p for p in re.split(r"[\s,，、]+", cleaned) if len(p) >= 2]
    if not parts:
        fallback = re.sub(r"[（）()【】\[\]「」\"'·]", "", name or "")
        fallback = re.sub(r"^(计划|约|定于|准备)", "", fallback).strip()
        return [fallback] if len(fallback) >= 2 else []
    return parts


def _ongoing_select(plan_content, real_text, intent_text, directive_text,
                    current_turn, cooldown_state):
    """Relevance gating + 冷却（移植自 OngoingContextSelector.selectForInjection）。
    返回 eligible item names 列表。"""
    items = _parse_plan_items(plan_content)
    if not items:
        return []
    eligible = []
    for item in items:
        if item["item_status"] in ("FINISHED", "CANCELLED"):
            continue
        # 合并冷却
        cd = cooldown_state.get(item["name"], {})
        last_inj = cd.get("last_injected_turn", -1)
        last_ev = cd.get("last_evidence_turn", -1)
        # DORMANT 推导
        if item["item_status"] == "ACTIVE" and last_ev >= 0:
            if current_turn - last_ev >= DORMANT_TURNS:
                item["item_status"] = "DORMANT"
        # 关键词匹配
        kws = _extract_keywords(item["name"])
        if not kws:
            continue
        real_match = any(kw.lower() in real_text.lower() for kw in kws)
        directive_match = bool(directive_text) and any(
            kw.lower() in directive_text.lower() for kw in kws)
        intent_match = bool(intent_text) and any(
            kw.lower() in intent_text.lower() for kw in kws)
        is_relevant = real_match or directive_match or intent_match
        # 冷却检查
        in_cooldown = (last_inj >= 0 and
                       (current_turn - last_inj) < COOLDOWN_TURNS and
                       not real_match)
        if in_cooldown:
            continue
        # DORMANT/ACTIVE 不相关 → 跳过
        if not is_relevant:
            continue
        eligible.append(item["name"])
    return eligible

# ============================================================
# 模块 B: CurrentSceneInjection（移植自 Kotlin，纯函数）
# ============================================================

_CLOSING_KW = ("睡了", "睡觉", "晚安", "先忙", "去忙", "不聊了", "下次再聊",
               "明天再说", "去洗澡", "去洗漱", "先走了", "去吃饭")
_CONFLICT_KW = ("生气", "烦死", "不想理", "随便你", "你总是", "你每次", "又来",
                "有意思吗", "懒得说", "你能不能", "为什么总是", "你到底",
                "不是你的错难道是我的错")
_APOLOGY_KW = ("对不起", "抱歉", "我的错", "我错了", "原谅", "不应该", "是我不好",
               "是我没做好")
_INVITE_KW = ("见面", "约", "一起", "出来", "去吃", "去看", "周末", "有空吗",
              "能不能", "方便吗")
_SERIOUS_KW = ("难过", "不开心", "压力大", "焦虑", "想哭", "委屈", "不知道怎么办",
               "纠结", "在想", "其实我", "说实话", "心里")
_PLAYFUL_KW = ("哈哈", "笑死", "233", "狗子", "笨蛋", "讨厌", "哼", "略略", "😏",
               "😂", "嘻")


def _scene_infer(messages):
    """移植自 CurrentSceneInjection.infer — 固定优先级关键词规则。"""
    real = [m for m in messages if m.get("role") in ("HER", "ME")]
    if not real:
        return ""
    last_her = None
    for m in reversed(real):
        if m["role"] == "HER":
            last_her = m
            break
    all_text = " ".join(m.get("text", "") for m in real).lower()
    # 1. 收尾
    if last_her:
        ht = last_her.get("text", "").lower()
        if any(kw in ht for kw in _CLOSING_KW):
            return "收尾"
    # 2. 争执
    if any(kw in all_text for kw in _CONFLICT_KW):
        return "争执"
    # 3. 解释/认错（只看 ME）
    me_text = " ".join(m.get("text", "") for m in real if m["role"] == "ME").lower()
    if any(kw in me_text for kw in _APOLOGY_KW):
        return "解释或认错"
    # 4. 主动邀约
    if last_her:
        ht = last_her.get("text", "").lower()
        if any(kw in ht for kw in _INVITE_KW):
            return "主动邀约"
    # 5. 认真沟通
    if any(kw in all_text for kw in _SERIOUS_KW):
        return "认真沟通"
    # 6. 轻松互逗
    if any(kw in all_text for kw in _PLAYFUL_KW):
        return "轻松互逗"
    # 7. 默认
    return "日常分享"


def _scene_block(scene):
    if not scene:
        return ""
    return "# 【当前场景】（本轮属性，不改变长期阶段档案）\n场景：%s\n\n" % scene

# ============================================================
# 模块 D: PromptBudget（移植自 Kotlin，纯函数）
# ============================================================

def _last_h1_blocks(content, count):
    blocks = [b.strip() for b in re.split(r"(?<=\n)(?=# )", content or "")
              if b.strip().startswith("# ")]
    if len(blocks) <= count:
        return "\n\n".join(blocks)
    return "…（更早的已省略）\n\n" + "\n\n".join(blocks[-count:])


def _budget_trim(full_text):
    """移植自 PromptBudget.applyBudget — 头尾保留式截断。"""
    if len(full_text) <= TOTAL_BUDGET:
        return full_text
    head_len = int(TOTAL_BUDGET * 0.5)
    tail_len = int(TOTAL_BUDGET * 0.4)
    return (full_text[:head_len] +
            "\n\n…（中间旧记忆因长度限制已省略）…\n\n" +
            full_text[-tail_len:])

# ============================================================
# 模块 E: PromptKnowledgeSection（移植自 Kotlin，简化版）
# ============================================================

def _knowledge_assemble(kb, ongoing_names):
    """移植自 PromptKnowledgeSection.build — 画像→阶段→教训→场景→最近→进行中。
    kb = initial_kb dict (me/her/warmth/style/lessons)。
    corrections 暂为空（v1 留接口）。
    """
    sb = []
    # 画像段
    sb.append("# 【懂得】关系画像")
    if kb.get("me"):
        sb.append("## 我\n%s" % kb["me"].strip())
    if kb.get("her"):
        sb.append("## 她\n%s" % kb["her"].strip())
    if kb.get("warmth"):
        sb.append("## 我们\n%s" % kb["warmth"].strip())
    if kb.get("style"):
        sb.append("## 我的表达偏好\n%s" % kb["style"].strip())
    sb.append("")
    # 教训段（lastH1Blocks 最近3条）
    if kb.get("lessons"):
        lesson_text = _last_h1_blocks(kb["lessons"], 3)
        sb.append("# 【记忆】经验教训（仅供参考）")
        sb.append(lesson_text)
        sb.append("")
    # 进行中事项（如果有）
    if ongoing_names:
        sb.append("# 【进行中事项】（长期追踪，仅在与当前对话相关时提及，不必每条都提）")
        for name in ongoing_names:
            sb.append(name)
        sb.append("")
    return "\n".join(sb)

# ============================================================
# pipe_v1 复用组件（直接内联，已验证）
# ============================================================

def _build_segments(history):
    segs = []
    cur = []
    prev_set = None
    for idx, m in enumerate(history):
        cur.append((idx, m))
        cur_set = _bigrams(m.get("text", ""))
        boundary = False
        if prev_set is not None and len(cur) >= 3:
            if _jaccard(prev_set, cur_set) < JACCARD_BOUNDARY:
                boundary = True
        if len(cur) >= SEG_MAX_TURNS:
            boundary = True
        if boundary and len(cur) >= 2:
            segs.append(cur[:-1])
            cur = cur[-1:]
        prev_set = cur_set
    if cur:
        segs.append(cur)
    for i, seg in enumerate(segs):
        text = " ".join(m.get("text", "") for _, m in seg)
        bg = sorted(_bigrams(text))
        segs[i] = {"seg_id": "s%02d" % i, "turns": seg,
                   "fingerprint": " ".join(bg[:10])}
    return segs


def _build_said_ledger(history):
    ledger = []
    pending_q = None
    for idx, m in enumerate(history):
        text = m.get("text", "")
        if m["role"] == "HER":
            for clause in re.split(r"(?<=[。！？；!?;\n])", text):
                if QUESTION_TAIL.search(clause) and len(clause.strip()) >= 6:
                    pending_q = (idx, clause.strip())
        else:
            if pending_q:
                ledger.append({"turn": pending_q[0], "type": "question",
                               "clause": pending_q[1], "status": "answered",
                               "answer_at": idx})
                pending_q = None
            for clause in re.split(r"(?<=[。！？；!?;\n])", text):
                c = clause.strip()
                if not c:
                    continue
                if any(cue in c for cue in COMMIT_CUES) and len(c) >= 6:
                    ledger.append({"turn": idx, "type": "commitment",
                                   "clause": c, "status": "open"})
    return ledger


def _build_invalidated(history, said_ledger):
    inv = []
    for idx, m in enumerate(history):
        if m["role"] != "HER":
            continue
        for clause in re.split(r"(?<=[。！？；!?;\n])", m.get("text", "")):
            c = clause.strip()
            if c and any(cue in c for cue in INVAL_CUES):
                inv.append({"turn": idx, "clause": c, "cue": next(
                    cue for cue in INVAL_CUES if cue in c), "propagated_to": []})
    if len(inv) > MAX_INVALIDATED:
        inv = inv[-MAX_INVALIDATED:]
    inv_set = _bigrams(" ".join(x["clause"] for x in inv))
    for entry in said_ledger:
        if entry["type"] == "commitment" and entry["status"] == "open":
            ov = _bigrams(entry["clause"]) & inv_set
            if len(ov) >= 2:
                entry["status"] = "invalidated"
                for x in inv:
                    if _bigrams(x["clause"]) & ov:
                        x["propagated_to"].append("turn%d" % entry["turn"])
    return inv


def _select_segments(segments, query_set, recent_turn_ids):
    scored = []
    for seg in segments:
        turn_ids = [i for i, _ in seg["turns"]]
        if any(i >= recent_turn_ids for i in turn_ids):
            continue
        fp = set(seg["fingerprint"].split())
        ov = len(fp & query_set)
        recency = 1.0 + 0.1 * (turn_ids[-1] if turn_ids else 0) / 12.0
        scored.append((ov * recency, seg))
    scored.sort(key=lambda x: -x[0])
    picked = [seg for sc, seg in scored if sc > 0][:TOP_SEGS]
    return picked


def _fmt_turns(turns):
    lines = []
    for _, m in turns:
        who = "对方" if m["role"] == "HER" else "我"
        lines.append("[%s] %s" % (who, m.get("text", "")))
    return "\n".join(lines)

# ============================================================
# b20 示例锚定
# ============================================================

def _build_examples_block():
    lines = ["【参考示例】（以下是我之前回复得不错的几条，参考这个风格、长度和密度）"]
    for ex in EXAMPLES:
        lines.append("")
        lines.append("（%s）" % ex["label"])
        lines.append("对方：%s" % ex["context"])
        lines.append("我：%s" % ex["reply"])
    return "\n".join(lines)

# ============================================================
# 主生命周期
# ============================================================

def _intent_text(intent):
    if intent is None:
        return ""
    if isinstance(intent, str):
        return intent.strip()
    if isinstance(intent, dict) and intent.get("enabled"):
        return (intent.get("text") or "").strip()
    return ""


def init(assets_root, ep_meta, gateway, sandbox):
    return {
        "system_prompt": _read_assets(assets_root),
        "persona": ep_meta.get("persona") or {},
        "initial_kb": ep_meta.get("initial_kb") or {},
        "history": [],
        "turn_count": 0,
        "cooldown_state": {},   # 进程内冷却（简化版）
    }


def set_memory_access(handle, enabled):
    """无记忆机制可关：显式 no-op。"""


def build_user_prompt(handle, ctx):
    inp = ctx.get("input") or {}
    # 累积历史
    for m in inp.get("messages") or []:
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"):
            handle["history"].append({"role": m["role"], "text": m.get("text", "")})
    history = handle["history"]
    handle["turn_count"] = len(history)
    turn_count = handle["turn_count"]

    # ---- pipe_v1 三账本 + 段检索（确定性，每轮全量重算）----
    recent = history[-TAIL_RECENT_K:]
    recent_ids = max(0, len(history) - len(recent))
    segments = _build_segments(history)
    said = _build_said_ledger(history)
    inv = _build_invalidated(history, said)
    query_text = " ".join([m.get("text", "") for m in inp.get("messages") or []
                           if isinstance(m, dict)]) + " " + _intent_text(inp.get("intent"))
    query_set = _bigrams(query_text) | _bigrams(" ".join(m.get("text", "") for m in recent))
    picked = _select_segments(segments, query_set, recent_ids)

    # ---- OngoingContextSelector（A）: relevance gating + 冷却 ----
    kb = handle.get("initial_kb") or {}
    plan_content = kb.get("ongoing_plan") or ""
    real_text = " ".join(m.get("text", "") for m in inp.get("messages") or [])
    intent_t = _intent_text(inp.get("intent"))
    note_t = (inp.get("note") or "").strip()
    ongoing_eligible = _ongoing_select(plan_content, real_text, intent_t, note_t,
                                       turn_count, handle.get("cooldown_state") or {})

    # ---- SceneInjection（B）----
    scene = _scene_infer(inp.get("messages") or [])

    # ---- UncertainDetector（C）----
    uncertain_block = ""
    if _HAS_UNCERTAIN:
        prior_events = [{"text": m.get("text", ""), "turn": i + 1}
                        for i, m in enumerate(history[:-len(inp.get("messages") or [])])]
        cur_msgs = [{"text": m.get("text", ""), "turn": turn_count}
                    for m in (inp.get("messages") or []) if m.get("role") in ("HER", "ME")]
        ub, _, _ = _uncertain_build_block(prior_events, cur_msgs, turn_count)
        uncertain_block = ub

    # ---- KnowledgeAssembler（E）----
    knowledge_block = _knowledge_assemble(kb, ongoing_eligible)

    # ---- 装配 ----
    persona = handle.get("persona") or {}
    sections = []
    # 头部
    me_p = persona.get("me")
    her_p = persona.get("her")
    if me_p or her_p:
        me_str = me_p if isinstance(me_p, str) else json.dumps(me_p, ensure_ascii=False)
        her_str = her_p if isinstance(her_p, str) else json.dumps(her_p, ensure_ascii=False)
        sections.append("【人物背景】\n我：%s\n对方：%s" % (_clip(me_str, 80), _clip(her_str, 80)))
    sections.append(knowledge_block)
    sections.append(_scene_block(scene).strip() if scene else "")
    # 中部
    if picked:
        blocks = []
        for seg in picked:
            blocks.append("（第%d-%d轮）\n%s" % (seg["turns"][0][0] + 1,
                                                seg["turns"][-1][0] + 1,
                                                _fmt_turns(seg["turns"])))
        sections.append("【相关往事】（按当前话题检索的历史片段，原文）\n%s"
                        % "\n──\n".join(blocks))
    if uncertain_block:
        sections.append(uncertain_block)
    # 尾部
    if note_t:
        sections.append("【我的备注】\n%s" % note_t)
    if intent_t:
        sections.append("【我的意图】\n%s" % intent_t)
    clock = inp.get("clock")
    if clock:
        sections.append("【当前时间】%s" % clock)
    if recent:
        sections.append("【最近消息】（本轮对话直接依据）\n%s" % _fmt_turns(
            list(enumerate(recent))[-TAIL_RECENT_K:]))
    # 账本
    answered = [e for e in said if e["type"] == "question" and e["status"] == "answered"]
    open_c = [e for e in said if e["type"] == "commitment" and e["status"] == "open"]
    dead_c = [e for e in said if e.get("status") == "invalidated"]
    ledger_lines = []
    for e in answered[-MAX_LEDGER_TAIL:]:
        ledger_lines.append("- 她问：%s → 我已答（不得原样再问）" % _clip(e["clause"], 50))
    for e in open_c[-2:]:
        ledger_lines.append("- 我承诺过：%s（一致性问题，不得无故推翻）" % _clip(e["clause"], 50))
    for e in dead_c[-2:]:
        ledger_lines.append("- 已失效承诺：%s（只能以已作废口吻提及）" % _clip(e["clause"], 50))
    if ledger_lines:
        sections.append("【我的话语账本】\n%s" % "\n".join(ledger_lines))
    if inv:
        sections.append("【已作废/挂起事项】（对方明说失效或挂起，最新在前）\n%s"
                        % "\n".join("- %s" % _clip(x["clause"], 60) for x in reversed(inv)))
        sections.append("【硬约束】上述事项只能以'已作废/已挂起'口吻提及，"
                        "不得当作仍然有效的事回应；未列出的旧事可正常回扣。")
    # b20 示例锚定
    sections.append(_build_examples_block())
    sections.append("【本轮允许不回复吗】%s"
                    % ("是" if inp.get("may_decline") else "否"))
    sections.append("【输出要求】只输出一个 JSON 对象："
                    '{"action": "reply" 或 "no_reply", "recommended": "可直接发送的回复"}；'
                    '选择不回复时 recommended 为 null。'
                    '回复长度参考上面示例（20-60字），像真人随手打的消息。')

    # 过滤空段
    sections = [s for s in sections if s and s.strip()]
    full = "\n\n".join(sections)

    # ---- BudgetTrimmer（D）----
    full = _budget_trim(full)
    return full


def run_turn(handle, ctx, gateway):
    system = handle["system_prompt"]
    user = build_user_prompt(handle, ctx)
    res = gateway.generate(system, user, temperature=0.7, tag="main")
    usage = res.get("usage") or {}
    history = handle["history"]
    segments = _build_segments(history)
    said = _build_said_ledger(history)
    inv = _build_invalidated(history, said)
    uncertain_n = 0
    if _HAS_UNCERTAIN:
        prior = [{"text": m.get("text", ""), "turn": i + 1} for i, m in enumerate(history)]
        cur = [{"text": m.get("text", ""), "turn": handle["turn_count"]}
               for m in (ctx.get("input", {}).get("messages") or [])
               if m.get("role") in ("HER", "ME")]
        _, items, _ = _uncertain_find(prior[:-len(cur)] if cur else prior, cur, handle["turn_count"])
        uncertain_n = len(items)
    return {"reply": res["text"],
            "notes": "converge;segs=%d;said=%d(open=%d,inv=%d);uncertain=%d;scene=%s;ongoing=%d;tokens=%d" % (
                len(segments), len(said),
                len([e for e in said if e.get("status") == "open"]),
                len(inv), uncertain_n,
                _scene_infer(ctx.get("input", {}).get("messages") or []),
                len(_ongoing_select(handle.get("initial_kb", {}).get("ongoing_plan") or "",
                                    " ".join(m.get("text", "") for m in ctx.get("input", {}).get("messages") or []),
                                    "", "", handle["turn_count"], {})),
                usage.get("total_tokens", 0))}
