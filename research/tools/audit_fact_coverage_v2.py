"""tools/audit_fact_coverage_v2.py — fact_ledger_v2 覆盖率审计（tools/audit_fact_coverage.py
的参数化副本：--rules 指向候选抽取器，--out 指定输出；零模型调用、零网络）。
判定口径与原版逐字一致；新增 R0 排除归因：被 fact_ledger_v2 R0 层排除的 fragment
单列 reason="r0_subjective_excluded"，并输出 r0_excluded_quote 列表供主会话裁定
（GT quote 含推测/情绪句被排除是否属合理排除）。"""
import argparse
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORIES = ("LRE060", "LRE048")


def load_rules(path):
    spec = importlib.util.spec_from_file_location("fact_ledger_rules_audit_v2", path)
    lr = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(lr)
    return lr


def audit_story(lr, sid):
    ep = json.load(open(os.path.join(ROOT, "benchmark", "stories_v2", "wave1_ext",
                                     sid, "episode.json"), encoding="utf-8"))
    ref = json.load(open(os.path.join(ROOT, "benchmark", "stories_v2", "wave1_ext",
                                      sid, "reference.json"), encoding="utf-8"))
    msgs = {t["case_id"]: t["input"]["messages"] for t in ep["turns"]}
    total = covered = 0
    total_frag_miss = 0
    uncovered = []
    r0_excluded = []
    for cid, t in ref["turns"].items():
        for a in t.get("required_memory_atoms") or []:
            s = a.get("source") or {}
            if s.get("kind") != "message":
                continue
            total += 1
            q = s.get("quote") or ""
            m = msgs[s["case_id"]][int(s["message_id"][1:])]
            msg_txt = m.get("text") or ""
            entries, _ = lr.extract_facts(msg_txt, m.get("role") or "", m.get("ts") or "",
                                          0, 0, 1)
            booked = [e["text"] for e in entries]
            captured = [c for c in booked if lr._norm_cn(q) in lr._norm_cn(c)]
            frags = [f.strip() for f in lr.CLAUSE_SPLIT_RE.split(q) if f.strip()]
            if captured or not frags:
                covered += 1
                continue
            miss = []
            for f in frags:
                if any(lr._norm_cn(f) in lr._norm_cn(c) for c in booked):
                    continue
                raw = [c for c in lr.CLAUSE_SPLIT_RE.split(msg_txt)
                       if lr._norm_cn(f) in lr._norm_cn(c)]
                if not raw:
                    reason = "not_in_message"
                elif lr.utf16_len(raw[0].strip()) > lr.CLAUSE_MAX_CU:
                    reason = "overlong_clause"
                elif lr._Q_END_RE.search(raw[0].strip()) and not lr._R3_RE.search(raw[0]):
                    reason = "question_excluded"
                elif lr.is_subjective_excluded(raw[0].strip()):
                    reason = "r0_subjective_excluded"
                elif not lr.classify_clause(raw[0].strip())[0]:
                    reason = "no_rule_signal"
                else:
                    reason = "other"
                miss.append({"fragment": f, "reason": reason})
            if not miss:
                covered += 1
                continue
            total_frag_miss += len(miss)
            if any(mf["reason"] == "r0_subjective_excluded" for mf in miss):
                r0_excluded.append({"atom_id": a.get("atom_id"), "case_id": s["case_id"],
                                    "relation": a.get("relation"), "status": a.get("status"),
                                    "quote": q,
                                    "r0_fragments": [mf["fragment"] for mf in miss
                                                     if mf["reason"] == "r0_subjective_excluded"]})
            uncovered.append({"atom_id": a.get("atom_id"), "case_id": s["case_id"],
                              "relation": a.get("relation"), "status": a.get("status"),
                              "quote": q, "missed_fragments": miss})
    return {"atoms_total": total, "covered": covered,
            "coverage": round(covered / total, 4) if total else None,
            "missed_fragments": total_frag_miss,
            "uncovered": uncovered, "r0_excluded": r0_excluded}


def main():
    ap = argparse.ArgumentParser(description="fact ledger coverage audit (rules-parametrized)")
    ap.add_argument("--rules", default=os.path.join(
        ROOT, "candidates", "fact_ledger_v2", "mechanism", "ledger_rules.py"))
    ap.add_argument("--out", default=os.path.join(
        ROOT, "benchmark", "stories_v2", "wave1_ext", "FACT_COVERAGE_AUDIT_V2.json"))
    args = ap.parse_args()
    lr = load_rules(args.rules)
    stories = {sid: audit_story(lr, sid) for sid in STORIES}
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
           "extractor": args.rules,
           "note": "fact_ledger_v2 上线前置审计；候选运行时不可见 reference。"}
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    json.dump(doc, open(args.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("coverage: " + "  ".join(
        "%s=%d/%d(%.1f%%)" % (sid, s["covered"], s["atoms_total"],
                              100 * (s["coverage"] or 0)) for sid, s in stories.items()))
    print("merged=%d/%d(%.1f%%) uncovered_types=%s -> %s"
          % (cov, tot, 100 * (doc["merged"]["coverage"] or 0), types, args.out))
    for sid, s in stories.items():
        for r0 in s["r0_excluded"]:
            print("R0_EXCLUDED[%s] %s %s quote=%r r0_fragments=%s"
                  % (sid, r0["case_id"], r0["atom_id"], r0["quote"], r0["r0_fragments"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
