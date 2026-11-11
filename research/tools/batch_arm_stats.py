"""tools/batch_arm_stats.py — 批级机械读数（只读，零模型调用）。

对 <batch-dir>/{parent,candidate}_<EP>/out/turn_*/ 汇总冻结门需要的机械量：
- 契约：scoring.json 中 json_schema_valid 失败数（即输出契约违规位数）
- 空回复：reply_raw 可解析且 response 四规范槽全空/缺失（显式 action=no_reply 不算）
- 机械分：score10 均值；T07–T12 均值与后半段保持率（candidate/parent）
- 成本：calls.json usage.prompt 合计、调用数、逐轮等待 p50/p95
口径与 W17/W17b 判决所用口径一致；只读批次目录，不调用模型、不改任何证据。

用法：
  python tools/batch_arm_stats.py --batch-dir logs/w18_ucsupp_live_20261103 \
      --episodes LRE117,LRE121,LRE146,LRE138 --out logs/_w18_stats.json
"""
import argparse
import glob
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CANON = ("recommended", "bad_boy", "playful", "warm")


def _slot_empty(v):
    return v is None or (isinstance(v, str) and not v.strip())


def turn_no(case_id):
    try:
        return int(case_id.rsplit("_T", 1)[1])
    except (IndexError, ValueError):
        return 0


def read_turn(tdir):
    man = json.load(open(os.path.join(tdir, "manifest.json"), encoding="utf-8"))
    out = {"case_id": man.get("case_id"), "score10": man.get("score10"),
           "latency_ms": man.get("latency_ms"), "arm": None,
           "schema_ok": None, "empty": False, "no_reply": False,
           "parse_ok": True, "prompt": 0, "calls": 0}
    try:
        sc = json.load(open(os.path.join(tdir, "scoring.json"), encoding="utf-8"))
        for c in sc.get("checks") or []:
            if c.get("id") == "json_schema_valid":
                out["schema_ok"] = bool(c.get("pass"))
    except FileNotFoundError:
        pass
    try:
        ing = json.load(open(os.path.join(tdir, "reply_ingest.json"), encoding="utf-8"))
        out["parse_ok"] = ing.get("kind") == "json"
    except FileNotFoundError:
        pass
    raw = ""
    try:
        raw = open(os.path.join(tdir, "reply_raw.txt"), encoding="utf-8").read()
        obj = json.loads(raw)
        if isinstance(obj, dict):
            out["no_reply"] = obj.get("action") == "no_reply"
            resp = obj.get("response")
            if isinstance(resp, dict):
                out["empty"] = all(_slot_empty(resp.get(k)) for k in CANON)
            elif resp is None:
                out["empty"] = not out["no_reply"]
    except (FileNotFoundError, ValueError):
        out["parse_ok"] = False
    try:
        calls = json.load(open(os.path.join(tdir, "calls.json"), encoding="utf-8"))
        out["calls"] = len(calls)
        out["prompt"] = sum((c.get("usage") or {}).get("prompt") or 0 for c in calls)
    except FileNotFoundError:
        pass
    return out


def pct(vals, q):
    if not vals:
        return None
    s = sorted(vals)
    i = min(len(s) - 1, max(0, int(round(q * (len(s) - 1)))))
    return s[i]


def mean(vals):
    vals = [v for v in vals if isinstance(v, (int, float))]
    return sum(vals) / len(vals) if vals else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--batch-dir", required=True)
    ap.add_argument("--episodes", required=True)
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    eps = [e.strip() for e in a.episodes.split(",") if e.strip()]
    report = {"batch_dir": a.batch_dir, "episodes": {}}
    for ep in eps:
        entry = {}
        for arm in ("parent", "candidate"):
            tdirs = sorted(glob.glob(os.path.join(ROOT, a.batch_dir, "%s_%s" % (arm, ep),
                                                  "out", "turn_*")), key=lambda p: turn_no(p))
            rows = [read_turn(t) for t in tdirs]
            for r in rows:
                r["arm"] = arm
            entry[arm] = {
                "turns": len(rows),
                "schema_fail": sum(1 for r in rows if r["schema_ok"] is False),
                "schema_missing": sum(1 for r in rows if r["schema_ok"] is None),
                "empty": sum(1 for r in rows if r["empty"] and not r["no_reply"]),
                "parse_fail": sum(1 for r in rows if not r["parse_ok"]),
                "score_mean": mean([r["score10"] for r in rows]),
                "score_t07_12": mean([r["score10"] for r in rows if turn_no(r["case_id"]) >= 7]),
                "prompt_sum": sum(r["prompt"] for r in rows),
                "calls": sum(r["calls"] for r in rows),
                "lat_p50": pct([r["latency_ms"] for r in rows], 0.5),
                "lat_p95": pct([r["latency_ms"] for r in rows], 0.95),
                "rows": rows,
            }
        par, cand = entry["parent"], entry["candidate"]
        if par["score_t07_12"] and cand["score_t07_12"]:
            entry["keep_t07_12"] = cand["score_t07_12"] / par["score_t07_12"]
        if par["prompt_sum"]:
            entry["prompt_delta_pct"] = 100.0 * (cand["prompt_sum"] / par["prompt_sum"] - 1)
        report["episodes"][ep] = entry

    tot = {}
    for arm in ("parent", "candidate"):
        acc = [ep[arm] for ep in report["episodes"].values()]
        tot[arm] = {
            "turns": sum(x["turns"] for x in acc),
            "schema_fail": sum(x["schema_fail"] for x in acc),
            "empty": sum(x["empty"] for x in acc),
            "parse_fail": sum(x["parse_fail"] for x in acc),
            "score_mean": mean([x["score_mean"] for x in acc if x["score_mean"] is not None]),
            "score_t07_12": mean([x["score_t07_12"] for x in acc if x["score_t07_12"] is not None]),
            "prompt_sum": sum(x["prompt_sum"] for x in acc),
            "calls": sum(x["calls"] for x in acc),
        }
    report["totals"] = tot
    if tot["parent"]["score_t07_12"] and tot["candidate"]["score_t07_12"]:
        report["totals"]["keep_t07_12"] = \
            tot["candidate"]["score_t07_12"] / tot["parent"]["score_t07_12"]
    if tot["parent"]["prompt_sum"]:
        report["totals"]["prompt_delta_pct"] = \
            100.0 * (tot["candidate"]["prompt_sum"] / tot["parent"]["prompt_sum"] - 1)

    print("%-8s %-9s turns schema_fail empty parse_fail score T07-12 prompt calls p50 p95"
          % ("ep", "arm"))
    for ep, entry in report["episodes"].items():
        for arm in ("parent", "candidate"):
            x = entry[arm]
            print("%-8s %-9s %5d %11d %5d %10d %5.2f %5.2f %7d %5d %5s %5s" % (
                ep, arm, x["turns"], x["schema_fail"], x["empty"], x["parse_fail"],
                x["score_mean"] or 0, x["score_t07_12"] or 0, x["prompt_sum"],
                x["calls"], x["lat_p50"], x["lat_p95"]))
        if "keep_t07_12" in entry:
            print("  keep_t07_12=%.4f prompt_delta=%+.2f%%"
                  % (entry["keep_t07_12"], entry.get("prompt_delta_pct") or 0))
    if a.out:
        for ep in report["episodes"].values():
            for arm in ("parent", "candidate"):
                ep[arm].pop("rows", None)
        with open(os.path.join(ROOT, a.out), "w", encoding="utf-8") as fh:
            json.dump(report, fh, ensure_ascii=False, indent=1)
        print("wrote", a.out)


if __name__ == "__main__":
    main()
