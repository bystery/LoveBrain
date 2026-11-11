# tools/audit_fact_coverage.py — W12 上线前置（设计 §2.4）：账本抽取器对 LRE060/LRE048
# required_memory_atoms[].source.quote（message 源）的逐字覆盖率离线审计。
# 零模型调用、零网络；只读 benchmark/stories_v2/wave1_ext/，写 FACT_COVERAGE_AUDIT.json。
# quote 含于某条被抽取器入账的 clause（_norm_cn 归一后子串）即算覆盖；
# GT quote 自身可能内含逗号（多 clause 引述），此时按同一切分规则拆 fragment，
# 每个 fragment 逐一对入账 clause 判定——与账本 clause 级存储粒度一致。
# 未覆盖 fragment 按结构性原因标注类型（overlong_clause / no_rule_signal /
# question_excluded / not_in_message / other），不冒充可召回。
import importlib.util
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "benchmark", "stories_v2", "wave1_ext",
                   "FACT_COVERAGE_AUDIT.json")
STORIES = ("LRE060", "LRE048")

_spec = importlib.util.spec_from_file_location(
    "fact_ledger_rules_audit",
    os.path.join(ROOT, "candidates", "fact_ledger_v1", "mechanism", "ledger_rules.py"))
lr = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(lr)


def _norm(s):
    return lr._norm_cn(s)


def audit_story(sid):
    ep = json.load(open(os.path.join(ROOT, "benchmark", "stories_v2", "wave1_ext",
                                     sid, "episode.json"), encoding="utf-8"))
    ref = json.load(open(os.path.join(ROOT, "benchmark", "stories_v2", "wave1_ext",
                                      sid, "reference.json"), encoding="utf-8"))
    msgs = {t["case_id"]: t["input"]["messages"] for t in ep["turns"]}
    total = covered = 0
    total_frag_miss = 0
    uncovered = []
    for cid, t in ref["turns"].items():
        for a in t.get("required_memory_atoms") or []:
            s = a.get("source") or {}
            if s.get("kind") != "message":
                continue
            total += 1
            q = s.get("quote") or ""
            m = msgs[s["case_id"]][int(s["message_id"][1:])]
            msg_txt = m.get("text") or ""
            # 只对【真正入账】的 clause 判覆盖（信号 + 长度过滤后的 entries），非全部短 clause
            entries, _ = lr.extract_facts(msg_txt, m.get("role") or "", m.get("ts") or "",
                                          0, 0, 1)
            booked = [e["text"] for e in entries]
            captured = [c for c in booked if _norm(q) in _norm(c)]   # 整 quote 单 clause 命中
            frags = [f.strip() for f in lr.CLAUSE_SPLIT_RE.split(q) if f.strip()]
            if captured or not frags:
                covered += 1
                continue
            miss = []
            for f in frags:
                if any(_norm(f) in _norm(c) for c in booked):
                    continue
                raw = [c for c in lr.CLAUSE_SPLIT_RE.split(msg_txt)
                       if _norm(f) in _norm(c)]
                if not raw:
                    reason = "not_in_message"
                elif lr.utf16_len(raw[0].strip()) > lr.CLAUSE_MAX_CU:
                    reason = "overlong_clause"
                elif lr._Q_END_RE.search(raw[0].strip()) and not lr._R3_RE.search(raw[0]):
                    reason = "question_excluded"
                elif not lr.classify_clause(raw[0].strip())[0]:
                    reason = "no_rule_signal"
                else:
                    reason = "other"
                miss.append({"fragment": f, "reason": reason})
            if not miss:
                covered += 1       # 全部 fragment 各自入账（多 clause quote 整体覆盖）
                continue
            total_frag_miss += len(miss)
            uncovered.append({"atom_id": a.get("atom_id"), "case_id": s["case_id"],
                              "relation": a.get("relation"), "status": a.get("status"),
                              "quote": q, "missed_fragments": miss})
    return {"atoms_total": total, "covered": covered,
            "coverage": round(covered / total, 4) if total else None,
            "missed_fragments": total_frag_miss,
            "uncovered": uncovered}


def main():
    stories = {sid: audit_story(sid) for sid in STORIES}
    tot = sum(s["atoms_total"] for s in stories.values())
    cov = sum(s["covered"] for s in stories.values())
    types = {}
    for sid, s in stories.items():
        for u in s["uncovered"]:
            for mf in u["missed_fragments"]:
                types[mf["reason"]] = types.get(mf["reason"], 0) + 1
    doc = {"stories": stories,
           "merged": {"atoms_total": tot, "covered": cov,
                      "coverage": round(cov / tot, 4) if tot else None,
                      "uncovered_types": types},
           "extractor": "candidates/fact_ledger_v1/mechanism/ledger_rules.py",
           "note": "设计期校准专用（设计 §2.4 前置）；候选运行时不可见 reference。"}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump(doc, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("coverage: " + "  ".join(
        "%s=%d/%d(%.1f%%)" % (sid, s["covered"], s["atoms_total"],
                              100 * (s["coverage"] or 0)) for sid, s in stories.items()))
    print("merged=%d/%d(%.1f%%) uncovered_types=%s -> %s"
          % (cov, tot, 100 * (doc["merged"]["coverage"] or 0), types, OUT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
