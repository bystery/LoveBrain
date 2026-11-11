# -*- coding: utf-8 -*-
"""Benchmark V1 · 跨组客观诊断层（benchmark_v1_gold_diag v0）。

指导书 §6.4 二「客观质量」的统一实现：对**四组同等**施加 gold sidecar 已实例化断言的
确定性检查（零模型调用）：
  - must-not-affirm（取消/否定族原子）：primary_text 命中该原子的 affirm_markers 或
    原子 value 逐字（≥4 字）→ diag_cancel_affirm 违例；
  - required_this_turn 且 active 族原子：marker/value 命中 → hit；否则 miss；
  - gold 空轮 / 无断言轮 → N/A（不得自动算正确）。
定位=**诊断指标**，不替代权威评分器（harness 组的契约计分仍以 lb_scorer 为准），
不做冠军判决；无可靠 gold 处如实 N/A。
"""
import glob
import json
import os

GOLD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                        "benchmark", "stories_v2", "GOLD_SIDECAR")
DIAG_VERSION = "benchmark_v1_gold_diag_v0"

CANCEL_FAMILY = {"cancelled", "canceled", "superseded", "retracted", "voided",
                 "revoked", "invalidated", "negated", "erroneous"}
ACTIVE_FAMILY = {"asserted", "confirmed", "active", "fulfilled", "resolved"}


def load_gold_index():
    """{case_id: turn_gold_dict}（全部 GOLD_*.json 波文件）。"""
    out = {}
    for p in sorted(glob.glob(os.path.join(GOLD_DIR, "GOLD_*.json"))):
        base = os.path.basename(p)
        if base in ("GOLD_INDEX.json", "GOLD_QC_SAMPLE.json"):
            continue
        try:
            with open(p, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            continue
        for t in data.get("turns") or []:
            if isinstance(t, dict) and t.get("case_id"):
                out[t["case_id"]] = t
    return out


def _markers(atom):
    m = atom.get("affirm_markers")
    if m is None:
        m = (atom.get("enrichment") or {}).get("affirm_markers")
    return [x for x in (m or []) if isinstance(x, str) and x.strip()]


def _value_hit(atom, text):
    v = atom.get("value")
    return isinstance(v, str) and len(v.strip()) >= 4 and v.strip() in text


def diag_turn(case_id, primary_text, gold_index):
    """返回 {diag_status, diag_cancel_affirm, diag_required_hits,
    diag_required_misses, diag_required_total}。"""
    t = gold_index.get(case_id)
    empty = {"diag_status": "N/A_no_gold", "diag_cancel_affirm": None,
             "diag_required_hits": None, "diag_required_misses": None,
             "diag_required_total": None, "diag_version": DIAG_VERSION}
    if not t or t.get("gold_empty"):
        return empty
    atoms = t.get("assertions") or []
    if not atoms:
        return empty
    text = primary_text or ""
    cancel_affirm = 0
    hits = misses = required = 0
    for a in atoms:
        if not isinstance(a, dict):
            continue
        status = (a.get("status") or "").strip().lower()
        if status in CANCEL_FAMILY or a.get("negation") is True:
            mk = any(m in text for m in _markers(a)) or _value_hit(a, text)
            if mk:
                cancel_affirm += 1
        elif a.get("required_this_turn") and status in ACTIVE_FAMILY:
            required += 1
            if any(m in text for m in _markers(a)) or _value_hit(a, text):
                hits += 1
            else:
                misses += 1
    if cancel_affirm == 0 and required == 0:
        return {"diag_status": "N/A_no_applicable_assertion",
                "diag_cancel_affirm": 0, "diag_required_hits": 0,
                "diag_required_misses": 0, "diag_required_total": 0,
                "diag_version": DIAG_VERSION}
    return {"diag_status": "OK", "diag_cancel_affirm": cancel_affirm,
            "diag_required_hits": hits, "diag_required_misses": misses,
            "diag_required_total": required, "diag_version": DIAG_VERSION}


def annotate_rows(rows):
    gi = load_gold_index()
    for r in rows:
        r.update(diag_turn(r.get("case_id"), r.get("primary_text"), gi))
    return rows
