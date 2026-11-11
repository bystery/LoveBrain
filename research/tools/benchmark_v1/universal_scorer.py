# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 3 · 通用评分层（universal_scorer v1，2026-11-09）。

指导书 P3-3.1/P3-3.2 执行件。修复 Pilot 暴露的评分口径不公平：
  - lb_scorer 的 PROTOCOL_TOP_KEYS 不含轻量臂 `recommended` 键 → DIRECT/COT veto 全量误触发
    （Pilot: DIRECT veto=544/552 vs CURRENT 2/552；score10 0.14 vs 9.96 是结构假象，非质量差）。
  - gold_diag v0 的关键词共现判分在否定/疑问/假设/引用语境产生假阳性
    （"你爸妈知道吗？"不得坐实"爸妈知情"）。

本工具分两层，**不改 arena/lb_scorer.py（产品契约层对 harness 组原样保留）**：
  通用层（四组同权，参与跨组质量对照）：
    - primary_text 抽取 = normalize_v1 固定规则（不挑备选）；
    - action 判定（reply/no_reply/缺失）；
    - gold 断言确定性检查 = gold_diag v0 逻辑 + **语义守卫 v1**：
      疑问句守卫（命中子句为疑问 → 不算坐实，记 question_mention）；
      否定守卫（同子句命中位前有否定词 → 不算坐实，记 negated_mention）；
      条件守卫（如果/要是/假如/万一/就算 → hypothetical_mention）；
      引用守卫（命中位于引号内 = 转述提及 → quoted_mention）；
    - 严重错误：parse/empty/contract（各臂自有契约层定义）+ cancel_affirm 违例。
  产品契约层（只对适用组统计，不参与跨组质量对照）：
    - LEGACY/CURRENT：lb_scorer scoring.json 的 veto/score10 原样透传（即产品 JSON 契约）；
    - DIRECT/COT：normalize_v1 的 light 契约（parse_ok/contract_ok）；scoring.json 的
      harness 形状 veto 记 NOT_APPLICABLE（原因随行）。

版本纪律：universal_scorer v1 为新工具（不改旧评分器）；gold_diag.py 原样保留可复现 v0 口径；
语义守卫改动必须升版本 + 对照控制集离线重评（P3-3.2）。
纯标准库；零模型调用；只读输入，输出写 --out 指定路径。
"""
import argparse
import csv
import json
import os
import re
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
from normalize_output import iter_run_dir, NORMALIZE_VERSION  # noqa: E402
import gold_diag  # noqa: E402

UNIVERSAL_VERSION = "universal_scorer_v2"
GUARDS_VERSION = "semantic_guards_v2"

# ---------- 语义守卫 v2（2026-11-11 RESEARCH-P1 通宵修复，单独版本化）----------
# v1 实证缺陷（B 线审计，10/10 误报）：对"正确确认更正/作废"的回复判 affirm——
# "赌约作废就作废" 无否定词 → 落入 affirm，而 gold 指南认定该行为正确。
# v2 新增 CANCEL_ACK_CUES：子句含作废/取消/推迟类确认词时返回新类别 cancel_ack
# （提及≠复活，与 past_mention 同层），不再计为 affirm。
# 回归：SEMANTIC_GUARD_CONTROLS_V2.json（v1 105 例 + 12 例 cancel_ack 新类）。

NEG_CUES = ("不", "没", "别", "未", "并无", "并非", "不是", "没有", "没提", "不能再", "别再")
COND_CUES = ("如果", "要是", "假如", "万一", "就算", "若是", "假如说", "哪怕", "倘若")
CANCEL_ACK_CUES = ("作废", "取消", "推迟", "延期", "改期", "黄了", "吹了", "告吹", "落空",
                   "退了", "退款", "不算了", "翻篇", "别提了", "不提了", "改天", "换时间")
PAST_MENTION_CUES = ("上次", "之前", "以前", "当时", "本来", "原定", "原本", "取消的",
                     "取消了", "改期的", "改期了", "缓的", "延的", "上周", "上月", "那天说")
QUOTES = ("「", "」", "“", "”", "\"", "\'", '“', '”')
_CLAUSE_SPLIT = re.compile(r"([^。！？；\n!?;]*[。！？；\n!?;]?)")
_QUESTION_TAIL = re.compile(r"(？|\?|吗[。！\s]?$|呢[。！\s]?$|么[。！\s]?$)")


def split_clauses(text):
    """按句末标点切子句（保留标点：疑问/感叹语义归属宿主句）。"""
    if not text:
        return []
    parts = []
    for chunk in _CLAUSE_SPLIT.findall(text):
        c = chunk.strip()
        if c:
            parts.append(c)
    return parts


def clause_of(text, marker):
    """marker 在 text 中的宿主子句；（子句, 起始位置, 结束位置）；找不到 → (None,...)"""
    if not marker:
        return None
    idx = text.find(marker)
    if idx < 0:
        return None
    for c in split_clauses(text):
        pos = text.find(c)
        if pos <= idx < pos + len(c):
            return c
    return text  # fallback：整段


def is_question_clause(clause):
    if not clause:
        return False
    return bool(_QUESTION_TAIL.search(clause.strip()))


def negated_before(clause, marker):
    """宿主子句含否定词（命中位前后都算：中文否定常后置，如"爸妈还不知道"）。"""
    if not clause or not marker:
        return False
    return any(cue in clause for cue in NEG_CUES)


def conditional_frame(clauses, idx):
    """宿主子句或其前一句含条件词。"""
    if idx > 0 and any(c in clauses[idx - 1] for c in COND_CUES):
        return True
    return any(c in clauses[idx] for c in COND_CUES)


def inside_quotes(text, marker):
    """命中位在引号内（转述提及，不构成第一人称坐实）。"""
    if not marker:
        return False
    idx = text.find(marker)
    if idx < 0:
        return False
    for op, cl in (("「", "」"), ("“", "”"), ('"', '"')):
        for m in re.finditer(re.escape(op) + r"(.*?)" + re.escape(cl), text, re.DOTALL):
            if m.start() <= idx < m.end():
                return True
    return False


def guarded_hit(text, marker):
    """命中分类：affirm（坐实嫌疑）/ question_mention / negated_mention /
    hypothetical_mention / quoted_mention / cancel_ack（v2：确认作废/取消/推迟，
    提及≠复活）/ past_mention（取消事项的过去式提及，提及≠复活）/ none。"""
    clause = clause_of(text, marker)
    if clause is None:
        return "none", None
    if inside_quotes(text, marker):
        return "quoted_mention", clause
    clauses = split_clauses(text)
    idx = next((i for i, c in enumerate(clauses) if c == clause), 0)
    if is_question_clause(clause):
        return "question_mention", clause
    if negated_before(clause, marker):
        return "negated_mention", clause
    if conditional_frame(clauses, idx):
        return "hypothetical_mention", clause
    if any(cue in clause for cue in CANCEL_ACK_CUES):
        return "cancel_ack", clause
    head = clause.split(marker)[0] if marker in clause else clause
    if any(cue in head for cue in PAST_MENTION_CUES):
        return "past_mention", clause
    return "affirm", clause


# ---------- 通用层诊断（gold_diag v0 逻辑 + 守卫） ----------

def flatten_markers(atom):
    """原子 affirm_markers 展平。盘上两种形状：flat 字符串列表（未来批）与
    嵌套组列表（stageD：[[alt,alt],...]）；gold_diag._markers 对嵌套形状返回空
    （全库 7,953 原子中 92% 无 markers、666 个嵌套组全部取空 = marker 通道空转）。
    本工具修复展平：组间并集（任一备选命中即算命中，与 gold_diag any 语义一致）。"""
    m = atom.get("affirm_markers")
    if m is None:
        m = (atom.get("enrichment") or {}).get("affirm_markers")
    out = []
    if not m:
        return out
    for g in m:
        if isinstance(g, str):
            out.append(g)
        elif isinstance(g, (list, tuple)):
            out.extend(x for x in g if isinstance(x, str))
    return out


def diag_turn_v1(case_id, primary_text, gold_index):
    t = gold_index.get(case_id)
    empty = {"diag_status": "N/A_no_gold", "diag_cancel_affirm": None,
             "diag_required_hits": None, "diag_required_misses": None,
             "diag_required_total": None, "guard_events": [],
             "diag_version": UNIVERSAL_VERSION, "guards_version": GUARDS_VERSION}
    if not t or t.get("gold_empty"):
        return empty
    atoms = t.get("assertions") or []
    if not atoms:
        return empty
    text = primary_text or ""
    cancel_affirm = 0
    hits = misses = required = 0
    guard_events = []
    for a in atoms:
        if not isinstance(a, dict):
            continue
        status = (a.get("status") or "").strip().lower()
        markers = [m for m in flatten_markers(a) if m.strip()]
        value = a.get("value") if isinstance(a.get("value"), str) else None
        value_hit = value is not None and len(value.strip()) >= 4 and value.strip() in text
        if status in gold_diag.CANCEL_FAMILY or a.get("negation") is True:
            kinds = []
            for m in markers:
                k, cl = guarded_hit(text, m)
                if k != "none":
                    kinds.append((m, k, cl))
            if value_hit:
                k, cl = guarded_hit(text, value.strip())
                kinds.append((value.strip()[:20], k, cl))
            affirm = [x for x in kinds if x[1] == "affirm"]
            if affirm:
                cancel_affirm += 1
                guard_events.append({"atom": a.get("atom_id"), "kind": "cancel_affirm",
                                     "marker": affirm[0][0][:30]})
            else:
                for m, k, cl in kinds:
                    guard_events.append({"atom": a.get("atom_id"), "kind": k,
                                         "marker": m[:30]})
        elif a.get("required_this_turn") and status in gold_diag.ACTIVE_FAMILY:
            required += 1
            if any(m in text for m in markers) or value_hit:
                hits += 1
            else:
                misses += 1
    if cancel_affirm == 0 and required == 0:
        return {"diag_status": "N/A_no_applicable_assertion", "diag_cancel_affirm": 0,
                "diag_required_hits": 0, "diag_required_misses": 0,
                "diag_required_total": 0, "guard_events": guard_events,
                "diag_version": UNIVERSAL_VERSION, "guards_version": GUARDS_VERSION}
    return {"diag_status": "OK", "diag_cancel_affirm": cancel_affirm,
            "diag_required_hits": hits, "diag_required_misses": misses,
            "diag_required_total": required, "guard_events": guard_events,
            "diag_version": UNIVERSAL_VERSION, "guards_version": GUARDS_VERSION}


# ---------- 产品契约层（分臂） ----------

def product_contract_fields(arm_id, row):
    """返回 (pcontract_ok, pcontract_note, score10, veto)。harness 组=veto/score10
    原样；轻量组=light 契约，harness veto 记 NOT_APPLICABLE。"""
    if arm_id in ("LEGACY", "CURRENT", "LEGACY_COMPAT_R9"):
        veto = row.get("veto")
        ok = row["contract_ok"] and not veto
        return ok, "harness_lb_scorer", row.get("score10"), veto
    # DIRECT/COT：light 契约（normalize 已判）；scoring.json 的 harness veto 不适用
    ok = row["contract_ok"]
    note = "light_contract(normalize_v1); harness_score10/veto=NOT_APPLICABLE(键形状)"
    return ok, note, None, None


# ---------- 组合与汇总 ----------

def score_run(run_dir, arms=("DIRECT", "COT", "LEGACY", "CURRENT"), out_prefix=None):
    gi = gold_diag.load_gold_index()
    rows = []
    for r in iter_run_dir(run_dir, arms=arms):
        arm = r["arm_id"]
        d1 = diag_turn_v1(r["case_id"], r.get("primary_text"), gi)
        p_ok, p_note, s10, veto = product_contract_fields(arm, r)
        severe = (r["error_type"] is not None) or (d1["diag_cancel_affirm"] or 0) > 0
        row = {
            "universal_version": UNIVERSAL_VERSION, "guards_version": GUARDS_VERSION,
            "normalize_version": NORMALIZE_VERSION,
            "arm_id": arm, "episode_id": r["episode_id"], "case_id": r["case_id"],
            "action": r["action"], "parse_ok": r["parse_ok"], "contract_ok": r["contract_ok"],
            "error_type": r["error_type"], "primary_text": r.get("primary_text"),
            "secondary_channels": r["secondary_channels"],
            "generic_cancel_affirm": d1["diag_cancel_affirm"],
            "generic_required_hits": d1["diag_required_hits"],
            "generic_required_misses": d1["diag_required_misses"],
            "generic_required_total": d1["diag_required_total"],
            "generic_status": d1["diag_status"],
            "guard_events": json.dumps(d1["guard_events"], ensure_ascii=False),
            "pcontract_ok": p_ok, "pcontract_layer": p_note,
            "product_score10": s10, "product_veto": veto,
            "severe_error": severe,
            "latency_ms": r["latency_ms"], "tokens": r["tokens"],
            "model": r["model"], "turn_dir": r["turn_dir"],
        }
        rows.append(row)
    if out_prefix:
        csv_path = out_prefix + "_universal_scores.csv"
        with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0].keys()) if rows else ["arm_id"],
                               extrasaction="ignore")
            w.writeheader()
            for row in rows:
                w.writerow({k: row[k] for k in w.fieldnames})
        summary = summarize(rows)
        with open(out_prefix + "_universal_summary.json", "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=1)
        return rows, summary
    return rows, summarize(rows)


def summarize(rows):
    out = {"universal_version": UNIVERSAL_VERSION, "guards_version": GUARDS_VERSION,
           "per_arm": {}}
    by_arm = {}
    for r in rows:
        by_arm.setdefault(r["arm_id"], []).append(r)
    for arm, rs in sorted(by_arm.items()):
        n = len(rs)
        valid = [r for r in rs if r["primary_text"]]
        cancel = sum(1 for r in rs if (r["generic_cancel_affirm"] or 0) > 0)
        req_t = sum(r["generic_required_total"] or 0 for r in rs)
        req_h = sum(r["generic_required_hits"] or 0 for r in rs)
        na = sum(1 for r in rs if str(r["generic_status"]).startswith("N/A"))
        out["per_arm"][arm] = {
            "n_turns": n,
            "parse_ok_rate": _rate(rs, "parse_ok"),
            "contract_ok_rate_own_layer": _rate(rs, "pcontract_ok"),
            "valid_reply_rate": round(len(valid) / n, 4) if n else None,
            "no_reply_rate": _rate_eq(rs, "action", "no_reply"),
            "severe_error_rate": _rate(rs, "severe_error"),
            "cancel_affirm_violation_turns": cancel,
            "required_hits": req_h, "required_total": req_t,
            "required_NA_turns": na,
            "tokens_total": _tokens_total(rs),
        }
    return out


def _rate(rows, key):
    n = len(rows)
    return round(sum(1 for r in rows if r.get(key)) / n, 4) if n else None


def _rate_eq(rows, key, val):
    n = len(rows)
    return round(sum(1 for r in rows if r.get(key) == val) / n, 4) if n else None


def _tokens_total(rows):
    tot = 0
    for r in rows:
        t = r.get("tokens")
        if isinstance(t, dict):
            tot += sum(v for v in t.values() if isinstance(v, (int, float)))
        elif isinstance(t, (int, float)):
            tot += t
    return tot


# ---------- 控制集评估（P3-3.2：≥100 正负控制） ----------

def eval_controls(path):
    """控制集 JSON：{"cases":[{"text":..., "expect":"affirm"|"guard", "type":..., "note":...}]}
    expect=affirm → guarded_hit 应判 affirm（真坐实）；expect=guard → 应判四种 guard 之一。
    返回每类 TP/FP/漏检统计。"""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    stats = {}
    details = []
    for c in data.get("cases", []):
        markers = c.get("markers") or []
        kinds = []
        for m in markers:
            k, _ = guarded_hit(c["text"], m)
            kinds.append(k)
        if c.get("expect") == "affirm":
            ok = all(k == "affirm" for k in kinds) and kinds
        else:
            ok = kinds and all(k != "affirm" for k in kinds)
        t = c.get("type", "unknown")
        s = stats.setdefault(t, {"n": 0, "ok": 0})
        s["n"] += 1
        s["ok"] += 1 if ok else 0
        details.append({"type": t, "expect": c.get("expect"), "verdict_kinds": kinds,
                        "ok": ok, "note": c.get("note")})
    return {"controls_version": data.get("version"), "guards_version": GUARDS_VERSION,
            "stats": stats, "n_total": len(details),
            "accuracy": round(sum(s["ok"] for s in stats.values()) /
                              max(1, sum(s["n"] for s in stats.values())), 4),
            "details": details}


def main():
    ap = argparse.ArgumentParser(description="Benchmark V1 通用评分层（四组同权）")
    ap.add_argument("--run-dir", help="待评 run 目录（<arm>/<episode>/<turn>/ 结构）")
    ap.add_argument("--arms", default="DIRECT,COT,LEGACY,LEGACY_COMPAT_R9,CURRENT")
    ap.add_argument("--out-prefix", help="输出前缀：*_universal_scores.csv / *_summary.json")
    ap.add_argument("--eval-controls", help="控制集 JSON 路径（P3-3.2）")
    args = ap.parse_args()
    if args.eval_controls:
        rep = eval_controls(args.eval_controls)
        print(json.dumps({k: v for k, v in rep.items() if k != "details"},
                         ensure_ascii=False, indent=1))
        if args.out_prefix:
            with open(args.out_prefix + "_controls_report.json", "w", encoding="utf-8") as f:
                json.dump(rep, f, ensure_ascii=False, indent=1)
        return 0
    if not args.run_dir:
        ap.error("--run-dir 或 --eval-controls 必选其一")
    arms = tuple(a.strip().upper() for a in args.arms.split(",") if a.strip())
    rows, summary = score_run(args.run_dir, arms=arms, out_prefix=args.out_prefix)
    print(json.dumps(summary, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
