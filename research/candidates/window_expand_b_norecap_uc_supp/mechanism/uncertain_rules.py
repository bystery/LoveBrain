# -*- coding: utf-8 -*-
"""未定事项检测与注入块（W16_UNCERTAIN_DESIGN_20261102.md §3 + 修订 R1/R2，纯函数零模型调用）。

三类结构信号（不按故事词表枚举）：
  A 转述/推测型：hedge 语气 + 实体锚点或第三方主体；
  B 未确认问句：问号 + 世界状态动词 + 实体锚点或第三方主体；
  C 引号转述：消息里「」/“”/"" 内的第三方原话，且含世界状态动词或实体锚点
    （对应"我舅说'网上买的不保险'""王阿姨传话'库存货'"这类，引号本身即"这是转述不是事实"的结构标记）。
三条都必须"未被后续确认事件关闭"才算未定（确认线索 + 同锚点或二元组重合 ≥0.34）。

注入块 ≤ UNCERTAIN_BUDGET_CU，只列逐字原文，不改写；无未定项返回空串（prompt 与父本逐字节相同）。"""
import re

UNCERTAIN_TITLE = "# 【未定/待确认事项：尚未核实，不得当作已发生】"
UNCERTAIN_FOOTER = ("口径：以上事项只能用\"还不确定/等你确认\"表述；不得替对方或第三方补全金额、"
                    "时间、行为，也不得据此改写已定条款。")
UNCERTAIN_BUDGET_CU = 120
MAX_ITEMS = 3

_HEDGE_RE = re.compile(r"如果|要是|没准|说不定|可能|好像|听说|据说|让我问|回头问|再看|"
                       r"还不确定|先别|等着瞧|有没有|能不能")
_WORLD_RE = re.compile(r"转(账)?|收到|到货|到家|到了|买|下单|定(下来|好)?|答应|同意|说好|"
                       r"弄好|装好|回(你|我|复)|答复|去|来|完成|退|换|修|寄|发|登记|付款|付钱|交")
_ANCHOR_RE = re.compile(r"\d|[零一二两三四五六七八九十百千万]+|今天|明天|后天|周末|"
                        r"周[一二三四五六日天]|[0-9]{1,2}月[0-9]{1,2}")
_THIRD_RE = re.compile(r"他|她|对方|同事|朋友|领导|老板|家人|亲戚|师傅|客服|店员|店长|邻居|同学|室友|"
                       r"表弟|表妹|表哥|表姐|舅舅|伯|姨|叔|妈|爸|舅|外婆|姥姥|爷爷|奶奶")
_CONFIRM_RE = re.compile(r"定了|确认|已经|收到|装好|到了|改好|答复|说好|登记|当场|敲定|定下来|"
                         r"办好了|完成|同意|付了|转了|买了")
_QUOTE_RE = re.compile(r"[「“‘']([^」”’']{2,})[」”’']")
_ASK_RE = re.compile(r"[?？]|是不是|该不该|要不要|退不退|行不行|能不能|你看|你说|帮我|让我|怎么弄|咋办")
_ANSWER_RE = re.compile(r"老板说|客服说|他说|她说|答复|证实|查了|问了|结果|后来")
_DECISION_RE = re.compile(r"^(不|别|就|必须|一定|不用|不要)|不退|不要了|不必|甭")
_CLAUSE_SEP = "，。！？；\n"
_PUNCT_SET = set("，。！？；：、“”「」\"'（）()《》<> \t\n\r-—…·+")


def utf16_len(s):
    return len((s or "").encode("utf-16-le")) // 2


def _norm(t):
    return "".join(ch for ch in (t or "") if ch not in _PUNCT_SET)


def _bigrams(t):
    n = _norm(t)
    return {n[i:i + 2] for i in range(len(n) - 1)} if len(n) >= 2 else set()


def _overlap(a, b):
    A, B = _bigrams(a), _bigrams(b)
    if not A or not B:
        return 0.0
    return len(A & B) / max(1, len(A))


def _overlap_cont(a, b):
    """containment 口径：交集 / 较短一方，适合"长条目 vs 短问句"的相关性判断。"""
    A, B = _bigrams(a), _bigrams(b)
    if not A or not B:
        return 0.0
    return len(A & B) / max(1, min(len(A), len(B)))


def _anchors(t):
    return set(_ANCHOR_RE.findall(t or ""))


def split_clauses(text):
    """按中文标点切 clause，返回 [(clause, is_question)]。"""
    out, buf = [], ""
    for ch in text or "":
        if ch in _CLAUSE_SEP:
            if buf.strip():
                out.append((buf.strip(), ch in "？?"))
            buf = ""
        else:
            buf += ch
    if buf.strip():
        out.append((buf.strip(), False))
    return out


_JUDGE_RE = re.compile(r"觉得|认为|说|讲|嫌|担心|怕|听说|抱怨")


def _is_candidate(clause, is_question):
    # hedge 也须有"实体锚点"或"第三方+世界/判断动词"，否则"你要是去""回头问你"这类
    # 纯条件/客套会被误当未定事项（设计修订 R3）。
    if _HEDGE_RE.search(clause) and (
            _ANCHOR_RE.search(clause)
            or (_THIRD_RE.search(clause) and (_WORLD_RE.search(clause) or _JUDGE_RE.search(clause)))):
        return "hedge"
    if is_question and _WORLD_RE.search(clause) and (_ANCHOR_RE.search(clause) or _THIRD_RE.search(clause)):
        return "question"
    return None


def _closed(clause, later_texts):
    """后续事件含确认线索，且同锚点（金额/日期）或二元组重合 ≥0.34 → 已关闭。

    无实体连续性的"确认"不算关闭（保守：宁可多注入，也不把没确认的事当已确认）。"""
    for t in later_texts:
        if not _CONFIRM_RE.search(t):
            continue
        if _anchors(clause) & _anchors(t):
            return True
        if _overlap(clause, t) >= 0.34:
            return True
    return False


def _relevant(item_text, cur_texts):
    """未定项与本轮是否"在场上"：同锚点（金额/日期）、同第三方、或二元组重合 ≥0.34。
    不在场上就不注入（防止未定项滚存成常驻噪音；对应设计修订 R3）。"""
    for t in cur_texts:
        if _anchors(item_text) & _anchors(t):
            return True
        third = set(_THIRD_RE.findall(item_text)) & set(_THIRD_RE.findall(t))
        if third:
            return True
        if _overlap_cont(item_text, t) >= 0.50:
            return True
    return False


def _items_of_message(text, kind_where, later_texts, ask_now=False):
    """一条消息 → 候选条目；kind_where 为 '本轮' 或 'T<turn>'。"""
    out = []
    for c, q in split_clauses(text):
        kind = _is_candidate(c, q)
        if kind and not _closed(c, later_texts):
            out.append({"text": c, "where": kind_where, "kind": kind})
    # C 引号转述：仅当同一消息在"征询判断/决定"（问句或该不该/退不退…），
    # 且引号之后没有当场答复（老板说/答复/查了…）时才注入——避免把赞美、已定决定、
    # 已核实的条款当成未定（设计修订 R4）。
    if _ASK_RE.search(text or "") or ask_now:
        for m in _QUOTE_RE.finditer(text or ""):
            span = m.group(1)
            if not (_WORLD_RE.search(span) or _ANCHOR_RE.search(span)):
                continue
            if _DECISION_RE.search(span.strip()):
                continue          # 引号内是"已定决定/指令"，不是未核实的外部事实
            if _ANSWER_RE.search((text or "")[m.end():]):
                continue
            if _closed(span, later_texts):
                continue
            # 长引语按 clause 拆分，只取含世界动词/锚点的短句（保证 ≤120 CU 内仍能入场）
            parts = [c for c, _q in split_clauses(span)
                     if _WORLD_RE.search(c) or _ANCHOR_RE.search(c)]
            for c in (parts[:2] or [span.strip()]):
                out.append({"text": c.strip(), "where": kind_where, "kind": "quote"})
    return out


def find_uncertain(prior_events, cur_msgs, cur_turn):
    """返回未定条目（新→旧，最多 MAX_ITEMS）：{text, where, kind, seq}。"""
    items = []
    cur_texts_all = " ".join((m.get("text") or "") for m in cur_msgs)
    ask_now = bool(_ASK_RE.search(cur_texts_all))
    for m in cur_msgs:
        for it in _items_of_message(m.get("text") or "", "本轮", [], ask_now):
            it["seq"] = len(prior_events) + 1
            items.append(it)
    for i, m in enumerate(prior_events):
        later = [(x.get("text") or "") for x in prior_events[i + 1:]]
        later += [(x.get("text") or "") for x in cur_msgs]
        for it in _items_of_message(m.get("text") or "", "T%s" % (m.get("turn") or "?"), later, ask_now):
            it["seq"] = i + 1
            items.append(it)
    cur_texts = [(m.get("text") or "") for m in cur_msgs]
    seen, out = set(), []
    for it in sorted(items, key=lambda x: x["seq"], reverse=True):
        key = _norm(it["text"])
        if key in seen:
            continue
        seen.add(key)
        # R3 相关性过滤：本轮条目恒在场上；历史条目须与本轮同锚点/同第三方/高重合
        if it["where"] != "本轮" and not _relevant(it["text"], cur_texts):
            continue
        out.append(it)
        if len(out) >= MAX_ITEMS:
            break
    return out


def build_block(prior_events, cur_msgs, cur_turn):
    """返回 (block, items, cu)；无未定项 → ("", [], 0)。"""
    items = find_uncertain(prior_events, cur_msgs, cur_turn)
    if not items:
        return "", [], 0
    head, foot = UNCERTAIN_TITLE, UNCERTAIN_FOOTER
    used = utf16_len(head) + utf16_len(foot) + 2
    lines = []
    for it in items:
        line = "- 「%s」（%s）" % (it["text"], it["where"])
        add = utf16_len(line) + 1
        if used + add > UNCERTAIN_BUDGET_CU:
            break
        lines.append(line)
        used += add
    if not lines:
        return "", [], 0
    block = head + "\n" + "\n".join(lines) + "\n" + foot
    return block, items[:len(lines)], utf16_len(block)
