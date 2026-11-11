# -*- coding: utf-8 -*-
"""RH-PIPE-V1「记忆/状态管线 v1」— A2 规格实现（LITERATURE_DELTA.md §五），LOOP-9。

一句话架构：rh_b04 锚定装配骨架之上，历史以"话题段"为检索/注入单元（RH-B-13），
写路径零调用维护三张持久账本——said_ledger（自话语：问句/承诺/更正，⊃rh_b17 组件 B）、
invalidated（失效登记+确定性传播，rh_b12 v0 ⊕传播步）、state_card（rh_b17 组件 A 原样）——
读路径"账本硬约束（尾部）+ 段检索原文（中部）"双通道注入。基线形态零新增模型调用。
全部模块每轮从全量历史确定性重算（毫秒级，12 轮规模无状态负担）。

文献锚：ES-Mem 2601.07582（分层边界/摘要/原文，q=0.35/τc=0.75 先验）、SuperDialseg
2305.08371（段粒度 4.25 段/13 轮→段上限 6 轮；跨域 0.66 F1→边界质量门必须有）、
Selfmem 2305.02437（自我输出回注范式）、LSP 2505.10571（状态必须显式外置）、
NCP-Bench 2608.08160（fact ledger+commitments 双账本同构）、MapDia 2503.05150
（反查户口=一阻一引）、MMI 1510.03055/R2 2310.10226（API-only 反重复杠杆=上下文侧+选择侧）。

前置门（RH-B-13 门）：M-A 分段人工抽检 20 段合理率 <70% → 回炉，不得带病上线。
预登记判据（smoke_24，对照 rh_b04 同位既有输出，任一击穿即淘汰或回炉）：
  ①重复提问率 ≤ C 臂；②承诺矛盾 = 0 目标（>C 即败）；③required 不降；
  ④cancel 守护事件 ≤ C（对称登记误禁言）；⑤注入增量 ≤500 字/位。
"""
import json
import os
import re

TAIL_RECENT_K = 3
SEG_MAX_TURNS = 6
TOP_SEGS = 3
MAX_LEDGER_TAIL = 5
MAX_INVALIDATED = 4
JACCARD_BOUNDARY = 0.22          # 相邻轮实体 bigram 集 Jaccard 低于此 → 候选边界
ASSET_ORDER = ("core.md", "format.md", "redline.md", "naturalness_check.md")
INVAL_CUES = ("作废", "取消", "推迟", "延期", "改期", "黄了", "吹了", "告吹", "落空",
              "退了", "退款", "不算了", "翻篇", "别提了", "不提了", "改天", "换时间",
              "先挂着", "挂账", "下次再算", "回来再算", "先不", "别等", "去不了")
COMMIT_CUES = ("我带", "我请", "我去", "我订", "我查", "我帮", "帮你", "给你", "答应",
               "改天", "回头", "下次", "到时候", "周末我", "明天我", "晚上我")
QUESTION_TAIL = re.compile(r"(？|\?)")
_STOP = set("的了呢吧吗啊呀哦嗯哈那么就是还有还有没有我们你们他们自己什么怎么这个那个"
            "一下有点有点儿可以不行不是现在时候因为所以但是然后如果虽然Direct请回复输出要求")


def _read_assets(assets_root):
    parts = []
    for rel in ASSET_ORDER:
        p = os.path.join(assets_root, "system_prompt", rel)
        with open(p, "r", encoding="utf-8") as f:
            parts.append(f.read().strip())
    return "\n\n".join(parts)


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


# ---------- M-A 段索引器（确定性） ----------

def _build_segments(history):
    """history: [{role,text}]。返回 segments=[{turns:[(idx,msg)], fingerprint}]。
    边界信号：相邻轮 bigram Jaccard 骤降；段上限 6 轮。"""
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


# ---------- M-B 自话语账本（确定性抽取） ----------

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


# ---------- M-C 失效账本 + 确定性传播 ----------

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
    # 传播步：失效事件与 open 承诺的 bigram 重叠 → 标 invalidated
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


# ---------- M-D 段检索注入 ----------

def _select_segments(segments, query_set, recent_turn_ids):
    scored = []
    for seg in segments:
        turn_ids = [i for i, _ in seg["turns"]]
        if any(i >= recent_turn_ids for i in turn_ids):
            continue                      # 最近段由尾部锚定，不重复注入
        fp = set(seg["fingerprint"].split())
        ov = len(fp & query_set)
        recency = 1.0 + 0.1 * (turn_ids[-1] if turn_ids else 0) / 12.0
        scored.append((ov * recency, seg))
    scored.sort(key=lambda x: -x[0])
    picked = [seg for sc, seg in scored if sc > 0][:TOP_SEGS]
    return picked


# ---------- M-E 装配 ----------

def _state_card(handle):
    kb = handle.get("initial_kb") or {}
    persona = handle.get("persona") or {}
    lines = ["我：%s" % _clip(persona.get("me") or kb.get("me"), 60),
             "对方：%s" % _clip(persona.get("her") or kb.get("her"), 60)]
    for key, label, n in (("warmth", "关系温度", 80), ("stage", "阶段", 40),
                          ("style", "对方风格", 60), ("lessons", "教训", 100)):
        if kb.get(key):
            lines.append("%s：%s" % (label, _clip(kb[key], n)))
    if kb.get("ongoing_plan"):
        lines.append("进行中：%s" % _clip(kb["ongoing_plan"], 120))
    her = kb.get("her")
    if isinstance(her, dict) and her.get("boundaries"):
        lines.append("对方边界：%s" % _clip(her["boundaries"], 100))
    return "\n".join(lines)[:700]


def _fmt_turns(turns):
    lines = []
    for _, m in turns:
        who = "对方" if m["role"] == "HER" else "我"
        lines.append("[%s] %s" % (who, m.get("text", "")))
    return "\n".join(lines)


def _fmt_history(history):
    if not history:
        return "（本轮之前没有可见的真实聊天记录）"
    return _fmt_turns(list(enumerate(history)))


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
    }


def set_memory_access(handle, enabled):
    """无记忆机制可关：显式 no-op。"""


def build_user_prompt(handle, ctx):
    inp = ctx.get("input") or {}
    for m in inp.get("messages") or []:
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"):
            handle["history"].append({"role": m["role"], "text": m.get("text", "")})
    history = handle["history"]
    recent = history[-TAIL_RECENT_K:]
    recent_ids = len(history) - len(recent)
    segments = _build_segments(history)
    said = _build_said_ledger(history)
    inv = _build_invalidated(history, said)
    query_text = " ".join([m.get("text", "") for m in inp.get("messages") or []
                           if isinstance(m, dict)]) + " " + _intent_text(inp.get("intent"))
    query_set = _bigrams(query_text) | _bigrams(_fmt_history(recent))
    picked = _select_segments(segments, query_set, recent_ids)
    persona = handle.get("persona") or {}
    sections = []
    me_p = persona.get("me")
    her_p = persona.get("her")
    if me_p or her_p:
        sections.append("【人物背景】\n我：%s\n对方：%s" % (_clip(me_p, 60) or "（未提供）",
                                                          _clip(her_p, 60) or "（未提供）"))
    # ---- 状态卡（替换 kb JSON 倾倒，rh_b17 组件 A 原样）----
    sections.append("【关系状态卡】\n%s" % _state_card(handle))
    # ---- 中部：段检索注入原文（替代全量历史流；RH-B-13）----
    if picked:
        blocks = []
        for seg in picked:
            blocks.append("（第%d-%d轮）\n%s" % (seg["turns"][0][0] + 1,
                                                seg["turns"][-1][0] + 1,
                                                _fmt_turns(seg["turns"])))
        sections.append("【相关往事】（按当前话题检索的历史片段，原文）\n%s"
                        % "\n──\n".join(blocks))
    note = (inp.get("note") or "").strip()
    if note:
        sections.append("【我的备注】\n%s" % note)
    it = _intent_text(inp.get("intent"))
    if it:
        sections.append("【我的意图】\n%s" % it)
    clock = inp.get("clock")
    if clock:
        sections.append("【当前时间】%s" % clock)
    if recent:
        sections.append("【最近消息】（本轮对话直接依据）\n%s" % _fmt_turns(
            list(enumerate(recent))[-TAIL_RECENT_K:]))
    # ---- 尾部账本硬约束（said_ledger ⊃ 已回应事项；invalidated）----
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
    history = handle["history"]
    said = _build_said_ledger(history)
    inv = _build_invalidated(history, said)
    return {"reply": res["text"],
            "notes": "pipe_v1;segs=%d;said=%d(open=%d);inv=%d;propagated=%d" % (
                len(_build_segments(history)), len(said),
                len([e for e in said if e.get("status") == "open"]), len(inv),
                sum(len(x.get("propagated_to") or []) for x in inv))}
