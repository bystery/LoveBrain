# -*- coding: utf-8 -*-
"""replay_149_facts.py — v16 复核 §7-1 执行令：类型门分工修复后的旧真实摘要回放。

把 66 次真实调用（l3b_66_real66_v1）产生的全部 scene_facts 原样送入当前写回门
（零生成调用，离线反事实），逐条报告接受/拒绝/归因：
  - accepted            当前门接受
  - valid_hm_conflict   仅因"完整时刻验证通过后又被拆字拒绝"（v16 §2 的误删类，
                        修复后应清零）
  - rejected_other      其余拒绝（含真实无出处实体——这类拒绝是门的正当职责）
  - parse_fail          旧回复无合法 analysis 块（写回本来就会跳过，不计入门）

验证点（复核点名）：A3/NCB/r1/t02 的新车次事实
"她今天21:30说她现在坐的是D3145，让我帮看这趟车"必须被接受（21:30 合法时刻
不得再连坐删除新车次）。

用法：python tools/replay_149_facts.py
输出：logs/replay149_gate_v17_20261021.json + 控制台摘要。
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path[:0] = [os.path.join(ROOT, "arena"), os.path.join(ROOT, "tools")]
sys.path.insert(0, os.path.join(ROOT, "candidates", "phase3_facts", "mechanism"))

import importlib.util as ilu
import lb_scorer

spec = ilu.spec_from_file_location(
    "w17_mech", os.path.join(ROOT, "candidates/phase3_facts/mechanism/mechanism.py"))
M = ilu.module_from_spec(spec)
spec.loader.exec_module(M)

RUN_ROOT = os.path.join(ROOT, "logs", "l3b_66_real66_v1", "runs")
ARMS = ("A1", "A2", "A3")
EPS = {"NCA": "NCA_CORR_INTENT_v1", "NCB": "NCB_ENTITY_DENSE_v1"}
N_TURNS = {"NCA": 6, "NCB": 5}
HM_RE = re.compile(r"\d{1,2}:\d{2}")
D3145_PROBE = "A3|NCB|r1|NCB_t02"   # 复核 §2 点名：A3/NCB/r1/t02 新车次事实
import datetime as _dt


def parse_fence(user):
    """真实请求 <chat> 栅栏 → lookup（与 _src_lookup 同口径：仅 m 编号条目）。"""
    m = re.search(r"<chat>\r?\n(.*?)\r?\n</chat>", user, re.S)
    if not m:
        raise SystemExit("calls 无 <chat> 栅栏")
    lookup = {}
    for ln in m.group(1).splitlines():
        ln = ln.strip()
        if not ln:
            continue
        o = json.loads(ln)
        if re.fullmatch(r"m\d+", str(o.get("id", ""))):
            lookup[o["id"]] = {"speaker": o["speaker"], "text": o["text"],
                               "ts": str(o.get("ts") or "")}
    return lookup


def classify_reject(reason, fact_text, srcs):
    """拒绝归因：是否仅因合法完整时刻被拆字重审（修复后应为 0 条）。"""
    m = re.match(r"gate_reject:entity_not_in_evidence:(.+)", reason or "")
    if not m:
        return "rejected_other"
    missing = set(m.group(1).split(","))
    # 该事实中的完整时刻若已逐字验证在场，则这些数字不应再出现在缺失集
    for hm in HM_RE.findall(fact_text):
        if any(hm in r["text"] or hm in r["ts"] for r in srcs):
            hm_digits = set(hm.split(":"))
            if missing and missing.issubset(hm_digits):
                return "valid_hm_conflict"
    return "rejected_other"


def subclassify_rejected(rows, clock):
    """rejected_other 三分类（复核 §7-1：正确拒绝/未确定分列，不混为质量指标）：
    - correct_fabrication_guard：缺失实体在整个可见消息窗口都不存在（无中生有）；
    - uncertain_clock_derived：缺失 token 是时钟邻近日期的组成部分（模型由当前时间
      推导的日期注释，非凭空编造，但协议要求逐字在场）；
    - uncertain_wrong_attribution：实体在窗口其他消息里存在，只是所引消息不含
      （模型归因偏差，事实本身可能有据）。"""
    out = []
    window_pool = set()
    for r in rows.get("_window_texts", []):
        window_pool |= set(M.base._alnum_tokens(M._norm_cjk_nums(r)))
    clock_digits = set()
    if clock:
        for off in (-1, 0, 1):
            try:
                d = _dt.datetime.strptime(clock.split(" ")[0], "%Y-%m-%d") + _dt.timedelta(days=off)
                clock_digits |= set(M.base._alnum_tokens(d.strftime("%Y-%m-%d")))
                clock_digits |= set(M.base._alnum_tokens(d.strftime("%m-%d")))
            except ValueError:
                pass
    for row in rows["rejected"]:
        missing = set((row.get("reason") or "").split(":")[-1].split(","))
        if missing and missing.issubset(clock_digits):
            row["subclass"] = "uncertain_clock_derived"
        elif missing and missing.issubset(window_pool):
            row["subclass"] = "uncertain_wrong_attribution"
        else:
            row["subclass"] = "correct_fabrication_guard"
        out.append(row)
    return out


def main():
    rows = []
    stats = {"total_facts": 0, "accepted": 0, "valid_hm_conflict": 0,
             "rejected_other": 0, "parse_fail": 0}
    subclasses = {"correct_fabrication_guard": 0, "uncertain_clock_derived": 0,
                  "uncertain_wrong_attribution": 0}
    by_arm = {a: dict.fromkeys(stats, 0) for a in ARMS}
    for arm in ARMS:
        for ek, ep in EPS.items():
            for rep in ("r1", "r2"):
                d = os.path.join(RUN_ROOT, arm, ep, rep)
                if not os.path.isdir(d):
                    continue
                for t in sorted(os.listdir(d)):
                    if not t.startswith("turn_"):
                        continue
                    case_id = t.split("_", 2)[2]
                    slot = "%s|%s|%s|%s" % (arm, ek, rep, case_id)
                    cj = json.load(open(os.path.join(d, t, "calls.json"),
                                        encoding="utf-8"))
                    call = cj[0]
                    manifest = json.load(open(os.path.join(d, t, "manifest.json"),
                                              encoding="utf-8"))
                    clock = manifest.get("clock_injected") or ""
                    lookup = parse_fence(call["user"])
                    window_pool = set()
                    for r in lookup.values():
                        window_pool |= set(M.base._alnum_tokens(M._norm_cjk_nums(r["text"])))
                    parsed = lb_scorer.extract_json_block(call.get("raw_text") or "")
                    ana = parsed.get("analysis") if parsed else None
                    facts = (ana or {}).get("scene_facts") if isinstance(ana, dict) else None
                    if not isinstance(facts, list):
                        stats["parse_fail"] += 1
                        by_arm[arm]["parse_fail"] += 1
                        continue
                    for f in facts:
                        if not isinstance(f, dict) or not (f.get("text") or "").strip():
                            continue
                        stats["total_facts"] += 1
                        by_arm[arm]["total_facts"] += 1
                        srcs, reason = M._gate_fact(f, lookup, case_id)
                        row = {"slot": slot, "text": f.get("text"),
                               "source_ids": f.get("source_ids")}
                        if reason is None:
                            stats["accepted"] += 1
                            by_arm[arm]["accepted"] += 1
                            row["result"] = "accepted"
                        else:
                            kind = classify_reject(reason, f.get("text"), srcs or [])
                            stats[kind] += 1
                            by_arm[arm][kind] += 1
                            row["result"] = kind
                            row["reason"] = reason
                            if kind == "rejected_other":
                                missing = set(reason.split(":")[-1].split(","))
                                if missing and missing.issubset(clock_digits(clock)):
                                    row["subclass"] = "uncertain_clock_derived"
                                elif missing and missing.issubset(window_pool):
                                    row["subclass"] = "uncertain_wrong_attribution"
                                else:
                                    row["subclass"] = "correct_fabrication_guard"
                                subclasses[row["subclass"]] += 1
                        rows.append(row)
    # 复核点名验证：新车次 D3145 不得因合法 21:30 被删
    probe = [r for r in rows if r["slot"] == D3145_PROBE]
    d3145 = [r for r in probe if "D3145" in (r.get("text") or "")]
    d3145_ok = bool(d3145) and all(r["result"] == "accepted" for r in d3145)
    out = {"date": "2026-10-21", "gate_version": "phase3_facts v17 (hm span-strip)",
           "stats": stats, "by_arm": by_arm, "rejected_subclasses": subclasses,
           "d3145_probe": {"slot": D3145_PROBE, "rows": d3145, "accepted": d3145_ok},
           "rows": rows}
    out_p = os.path.join(ROOT, "logs", "replay149_gate_v17_20261021.json")
    with open(out_p, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(json.dumps({"stats": stats, "by_arm": by_arm,
                      "rejected_subclasses": subclasses,
                      "d3145_accepted": d3145_ok,
                      "output": os.path.relpath(out_p, ROOT)},
                     ensure_ascii=False, indent=1))
    # 修复验收口径：valid_hm_conflict 必须清零，D3145 必须在库
    assert stats["valid_hm_conflict"] == 0, "仍有合法完整时刻被拆字误拒"
    assert d3145_ok, "新车次 D3145 事实未通过门（复核 §2 点名反例）"
    print("ASSERT PASS: valid_hm_conflict=0 且 D3145 新车次在库")


def clock_digits(clock):
    """时钟及其 ±1 日的全部数字成分（判定"由当前时间推导的日期注释"）。"""
    out = set()
    if not clock:
        return out
    for off in (-1, 0, 1):
        try:
            d = _dt.datetime.strptime(clock.split(" ")[0], "%Y-%m-%d") + _dt.timedelta(days=off)
        except ValueError:
            continue
        out |= set(M.base._alnum_tokens(d.strftime("%Y-%m-%d")))
        out |= set(M.base._alnum_tokens(d.strftime("%m-%d")))
    return out


if __name__ == "__main__":
    main()
