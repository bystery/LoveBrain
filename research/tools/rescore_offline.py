# tools/rescore_offline.py — 用当前 lb_scorer 重评已有答卷（协议 §8 版本纪律）
#
# rubric v0.5 改了 note_lcs 判据（max(len) 分母、0.6 阈值、<10 字豁免），
# 未重跑模型；本工具只读原始 raw_text + 原题 case JSON + 装配期 id_map，
# 调新 scorer 落 `scoring_v05.json` 到每 turn 目录，另写一份 `RESCORE_SUMMARY.json`
# 汇总 diff。**协议变更 ≠ 候选收益**，本工具不算新分数、不改原 scoring.json。
import argparse
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import lb_scorer  # noqa: E402


def _case_path(case_id):
    """优先 dev_pilot_v02；未命中回退 benchmark/episodes。"""
    ep = case_id.split("_")[0]
    for base in ("dev_pilot_v02", "episodes"):
        p = os.path.join(ROOT, "benchmark", base, ep + ".json")
        if os.path.isfile(p):
            return p, base
    return None, None


def _load_case(case_id):
    p, _ = _case_path(case_id)
    if not p:
        return None
    eps = json.load(open(p, encoding="utf-8"))
    for t in eps.get("turns", []):
        if t.get("case_id") == case_id:
            return t
    return None


def _build_id_map(case):
    """装配期 id_map：m0..mN → 消息。legacy _normalize_messages 每轮从 m0 起编，
    与 scorer 的 source_ids 校验对齐。"""
    msgs = (case or {}).get("input", {}).get("messages") or []
    return {"m%d" % i: m for i, m in enumerate(msgs)}


def rescore_exam(exam_dir, out_name="scoring_v05.json", contract=None):
    turns = []
    for ep_dir in sorted(d for d in glob.glob(os.path.join(exam_dir, "E0*"))
                          if os.path.isdir(d)):
        for turn_dir in sorted(glob.glob(os.path.join(ep_dir, "turn_*"))):
            mf_path = os.path.join(turn_dir, "manifest.json")
            if not os.path.isfile(mf_path):
                continue
            mf = json.load(open(mf_path, encoding="utf-8"))
            case_id = mf["case_id"]
            calls_path = os.path.join(turn_dir, "calls.json")
            if not os.path.isfile(calls_path):
                continue
            calls = json.load(open(calls_path, encoding="utf-8"))
            mains = [c for c in calls if c.get("tag", "main") == "main"]
            raw = (mains[0].get("raw_text") or mains[0].get("reply") or "") if mains else ""
            case = _load_case(case_id)
            if case is None:
                turns.append({"case_id": case_id, "skipped": "no_case"})
                continue
            id_map = _build_id_map(case)
            res = lb_scorer.run_case_scoring_safe(case, raw, id_map, contract=contract)
            old_path = os.path.join(turn_dir, "scoring.json")
            old = json.load(open(old_path, encoding="utf-8")) if os.path.isfile(old_path) else {}
            diff = {
                "case_id": case_id,
                "old_score10": old.get("score10"),
                "new_score10": res.get("score10"),
                "old_veto": old.get("veto"),
                "new_veto": res.get("veto"),
                "old_failed": [c["id"] for c in old.get("checks", []) if not c["pass"]],
                "new_failed": [c["id"] for c in res.get("checks", []) if not c["pass"]],
            }
            with open(os.path.join(turn_dir, out_name), "w", encoding="utf-8") as f:
                json.dump(res, f, ensure_ascii=False, indent=1)
            turns.append(diff)
    return turns


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--exam", required=True)
    ap.add_argument("--out-name", default="scoring_v05.json")
    ap.add_argument("--summary", default=None)
    args = ap.parse_args(argv)
    exam_dir = args.exam if os.path.isabs(args.exam) else os.path.join(ROOT, args.exam)
    turns = rescore_exam(exam_dir, out_name=args.out_name)
    moved = [t for t in turns if t.get("old_score10") != t.get("new_score10")]
    print("re-scored %d turns, %d score changed" % (len(turns), len(moved)))
    for t in moved:
        print("  %s  %.2f -> %.2f  old_fail=%s new_fail=%s" % (
            t["case_id"], t.get("old_score10") or 0, t.get("new_score10") or 0,
            t.get("old_failed"), t.get("new_failed")))
    if args.summary:
        dst = args.summary if os.path.isabs(args.summary) else os.path.join(ROOT, args.summary)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        json.dump({"exam_dir": os.path.relpath(exam_dir, ROOT),
                   "out_name": args.out_name,
                   "turns": turns,
                   "changed": moved,
                   "protocol_change": "rubric v0.5 note_lcs max(len)+0.6+min10"},
                  open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print("summary ->", os.path.relpath(dst, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
