# -*- coding: utf-8 -*-
"""W18 组合候选 —— S 段（被取代/已作废口径压制）规则与渲染（纯函数，零模型调用）。

规格：refs/experiment_protocol_20261023/global_harness_program_20261030/
      W18_UNC_SUPPRESS_DESIGN_20261102.md §2.3/§2.5/§2.7（判据跑前冻结）。

- 只扫 prior_events（本轮消息不入段；当轮更正已在当前输入逐字可见，次轮才入段）；
- a/b 行复用 ledger_rules.extract_facts + update_pass 逐字不动（b2 判定，不改）；
- c 行（已声明作废 / 现行口径）过 R-W18-1 口径锚点门：clause 必须含 strong_values，
  否则不入段（排除"变回一个人""反手连练两周""六人满编""顺一点"这类非口径用法）；
- R-W18-2 同锚点去重：strong_values 相交时丢更旧行；严格更旧才丢（同一轮内都保留）；
- R-W18-3 T 标注与排序用真实 turn（不再用 b2 的 uid+1）；
- R-W18-4 预算（含标题）≤240 CU，从新到旧装入、放不下即停，展示按旧→新。

段标题与行格式逐字沿用 b2（设计 §2.3）；无条目 → 空串，调用方不得输出该段。
"""
import os
import re

_HERE = os.path.dirname(os.path.abspath(__file__))


def _load(name, path):
    import importlib.util
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


lr = _load("wb_norecap_uc_supp_ledger", os.path.join(_HERE, "ledger_rules.py"))

SUPPRESSION_TITLE = "# 【口径基线：以下旧说法已作废，勿再沿用】"
SUPPRESSION_BUDGET_CU = 240
TOTAL_BUDGET_CU = 360                      # S ≤240 + U ≤120 的硬上界（设计 §2.5）

# 含未定线索的 clause 一律不入段（沿用 b2 规则，防坐实未定）
_UNCERTAIN_RE = re.compile(r"如果|要是|还不确定|再看")
# 回归现行线索：消息表明"回到之前的口径"，其口径值 clause 以现行口径注入
_REVERT_RE = re.compile(r"变回|还按|恢复|回到|回归")

# R-W18-1 口径锚点门（设计 §2.3 逐字；实现冻结在代码与本模块单测里）
_NUM = r"(?:[零一二两三四五六七八九十百千]|\d+)"
_STRONG_RE = re.compile(
    r"\d{2,}"
    r"|[零一二两三四五六七八九十百千]{2,}"
    r"|" + _NUM + r"(?=块|元|角|折|号|日|月|岁|分钟|小时|公里|米|斤|公斤)"
    r"|" + _NUM + r"点(?=半|整|到|前|出发|集合|见|上课|上班|下班|$)"
)
_TOD_RE = re.compile(r"早上|上午|中午|下午|晚上|凌晨|今晚|明早|昨天|今天|明天|后天|"
                     r"周[一二三四五六日天]|星期[一二三四五六日天]")
_CLOCK_RE = re.compile(_NUM + r"点")

utf16_len = lr.utf16_len


def strong_values(clause):
    """clause 的"口径值"集合（R-W18-1）；空集 = 非口径句，不入 c 类行。

    strong_values = _digits(每个 STRONG 命中) 的并集；若为空且含 TOD，
    再取 (NUM)点 的命中（如"下午四点档"）；仍为空 → 不入段。"""
    vals = set()
    for m in _STRONG_RE.finditer(clause or ""):
        vals |= lr._digits(m.group(0))
    if not vals and _TOD_RE.search(clause or ""):
        for m in _CLOCK_RE.finditer(clause or ""):
            vals |= lr._digits(m.group(0))
    return vals


def _collect_rows(prior_events):
    """对全部旧史跑 b2 的 extract_facts + update_pass（逐字不动），产出候选行。

    每行：{turn, line, values}。values 供 R-W18-2 同锚点去重（a/b 行取旧事实文本，
    c 行取 clause）；a/b 行不过 R-W18-1（其判定由 update_pass 的线索+重合+数字冲突
    给出），c 行必须过 strong_values 门。"""
    ledger, updates, n = [], [], 0
    for e in prior_events:
        entries, n = lr.extract_facts(e.get("text") or "", e.get("role") or "",
                                      e.get("ts") or "", e.get("turn") or 0,
                                      e.get("uid") or 0, n)
        lr.update_pass(ledger, entries, e.get("ts") or "", updates)
        ledger.extend(entries)
    rows, seen = [], set()

    def _add(turn, line, values):
        if line in seen:
            return
        seen.add(line)
        rows.append({"turn": turn, "line": line, "values": set(values or ())})

    by_id = {f.get("fact_id"): f for f in ledger}
    for of in ledger:                                   # a) b) update_pass 结果
        st = of.get("status")
        if st not in ("superseded", "cancelled"):
            continue
        old = of.get("text") or ""
        if _UNCERTAIN_RE.search(old):
            continue
        turn = of.get("capture_turn") or 0            # R-W18-3：真实 turn
        values = strong_values(old)
        if st == "superseded":
            nf = by_id.get(of.get("superseded_by") or "")
            new = (nf.get("text") or "") if nf else ""
            if not new or _UNCERTAIN_RE.search(new):
                continue
            _add(turn, "- 「%s」已作废（T%d改）→ 现行：「%s」"
                 % (old[:24], turn, new[:24]), values)
        else:
            _add(turn, "- 「%s」已作废（T%d改）→ 现行：（已取消）"
                 % (old[:24], turn), values)

    for e in prior_events:                              # c) 值名取消 / 回归现行
        et = e.get("text") or ""
        turn = e.get("turn") or 0                     # R-W18-3：真实 turn
        msg_revert = bool(_REVERT_RE.search(et))
        for c in lr.split_clauses(et):
            if _UNCERTAIN_RE.search(c):
                continue
            values = strong_values(c)
            if not values:                             # R-W18-1 口径锚点门
                continue
            if lr._R4_CANCEL_RE.search(c):
                _add(turn, "- 已声明作废：「%s」（T%d）" % (c[:24], turn), values)
            elif msg_revert:
                _add(turn, "- 现行口径以「%s」（T%d）为准" % (c[:24], turn), values)
    return rows


def _dedup_anchor(rows):
    """R-W18-2：按 strong_values 分组，新行与严格更旧（turn 更小）的行共享任一值
    时丢旧行；同一轮内都保留。返回按 turn 升序（稳定）的行。"""
    rows = sorted(rows, key=lambda r: r["turn"])
    kept = []
    for r in rows:
        kept = [k for k in kept
                if not (k["turn"] < r["turn"] and (k["values"] & r["values"]))]
        kept.append(r)
    return kept


def _used_of(lines):
    """b2 口径的段用量：标题 + 1 + Σ(行 + 1)（与 b2 返回值逐字同口径）。"""
    return utf16_len(SUPPRESSION_TITLE) + 1 + sum(utf16_len(x) + 1 for x in lines)


def render_lines(lines):
    """按设计 §2.3 格式渲染若干行（含标题）；空行表 → ("", 0)。"""
    lines = [x for x in (lines or []) if x]
    if not lines:
        return "", 0
    block = "\n".join([SUPPRESSION_TITLE] + lines)
    return block, _used_of(lines)


def suppression_block(prior_events):
    """返回 (block, used_cu, n_lines)；无条目 → ("", 0, 0)。

    used_cu 与 b2 的 supp_cu 同口径（含标题换行的 +1 记账），便于与设计 §2.7 对账。
    预算从最新往旧装入，遇到放不下的更旧行即停（b2 行为）；展示按旧→新。"""
    rows = _dedup_anchor(_collect_rows(prior_events))
    if not rows:
        return "", 0, 0
    kept, used = [], utf16_len(SUPPRESSION_TITLE) + 1
    for r in reversed(rows):
        add = utf16_len(r["line"]) + 1
        if used + add > SUPPRESSION_BUDGET_CU:
            break
        kept.append(r["line"])
        used += add
    if not kept:
        return "", 0, 0
    kept.reverse()
    return render_lines(kept)[0], used, len(kept)


# ---- U 段渲染与跨段去重（U 规则本身在 uncertain_rules.py，逐字不改） ----

_QUOTE_RE = re.compile(r"[「“‘']([^」”’']{2,})[」”’']")
# 与 uncertain_rules._PUNCT_SET 逐字同集（本模块自足，避免 import 私有名）
_PUNCT_SET = set("，。！？；：、“”「」\"'（）()《》<> \t\n\r-—…·+")


def _norm_punct(t):
    return "".join(ch for ch in (t or "") if ch not in _PUNCT_SET)


def build_u_block(items, title, footer, budget_cu):
    """与 uncertain_rules.build_block 的渲染/预算逐字同构（items 可先经跨段去重）。
    返回 (block, kept_items, cu)；无条目 → ("", [], 0)。"""
    if not items:
        return "", [], 0
    used = utf16_len(title) + utf16_len(footer) + 2
    lines = []
    for it in items:
        line = "- 「%s」（%s）" % (it["text"], it["where"])
        add = utf16_len(line) + 1
        if used + add > budget_cu:
            break
        lines.append(line)
        used += add
    if not lines:
        return "", [], 0
    block = title + "\n" + "\n".join(lines) + "\n" + footer
    return block, items[:len(lines)], utf16_len(block)


def cross_dedup(s_block, u_items):
    """跨段去重（设计 §2.5）：S 行内「」clause 与 U 条目做标点归一化包含比较；
    命中则保留 S 行、丢弃 U 条目。返回 (kept_items, xdedup)。"""
    if not s_block or not u_items:
        return list(u_items or []), 0
    clauses = [c for c in (_norm_punct(m.group(1)) for m in _QUOTE_RE.finditer(s_block)) if c]
    kept, xdedup = [], 0
    for it in u_items:
        nt = _norm_punct(it.get("text") or "")
        hit = bool(nt) and any(nt in c or c in nt for c in clauses)
        if hit:
            xdedup += 1
        else:
            kept.append(it)
    return kept, xdedup


def enforce_total_budget(s_lines, uc_cu, cap=TOTAL_BUDGET_CU):
    """总预算护栏（设计 §2.5）：若 S+U 超过 cap，先丢 S 最旧行直到 ≤cap。
    返回 (s_lines, s_cu, comb_overflow)。正常构造性 ≤360，不应触发。"""
    lines = list(s_lines or [])
    s_cu = _used_of(lines) if lines else 0
    overflow = 0
    while lines and s_cu + (uc_cu or 0) > cap:
        lines.pop(0)                                 # 丢最旧
        s_cu = _used_of(lines)
        overflow = 1
    if not lines:
        s_cu = 0
    return lines, s_cu, overflow
