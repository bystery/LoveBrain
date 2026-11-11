# -*- coding: utf-8 -*-
"""tools/w1402_timing_compare.py — W14-02 同配置 p50/p95/超时/恢复对比（消费 G1 timing，零新调用）。

臂间同量尺：同批同模型同温度，唯一差异=候选机制。输出每臂：
  attempts, p50/p95 headers_ms, p50/p95 first_content_ms, p50/p95 completion_ms,
  超时/故障计数（finish != stop 或 timing 缺失），每语义可用结果成本（usage 汇总）。
"""
import glob
import json
import os
import statistics

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BATCH = os.path.join(ROOT, "logs", "longprobe_g1_live_20261030")
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                   "W14-02_TIMING_COMPARE_20261031.json")


def pct(vals, q):
    if not vals:
        return None
    s = sorted(vals)
    i = min(len(s) - 1, int(round(q * (len(s) - 1))))
    return round(s[i], 1)


def collect(arm_prefix):
    rows = []
    for d in glob.glob(os.path.join(BATCH, "%s_*" % arm_prefix, "out", "turn_*")):
        cj = os.path.join(d, "calls.json")
        if not os.path.exists(cj):
            continue
        j = json.load(open(cj, encoding="utf-8"))
        main = next((c for c in j if c.get("tag") == "main"), None)
        if main is None:
            continue
        t = main.get("timing") or {}
        u = main.get("usage") or {}
        rows.append({
            "dir": os.path.basename(os.path.dirname(d)),
            "story": os.path.basename(d).split("_")[-1] if "_" in os.path.basename(d) else "",
            "ok": main.get("finish") == "stop",
            "headers_ms": t.get("headers_ms"),
            "first_content_ms": t.get("first_content_ms"),
            "completion_ms": t.get("completion_ms"),
            "prompt_tokens": (u or {}).get("prompt"),
            "completion_tokens": (u or {}).get("completion"),
            "latency_ms": main.get("latency_ms")})
    return rows


def summarize(rows, label):
    ok_rows = [r for r in rows if r["ok"] and r["completion_ms"] is not None]
    fails = [r for r in rows if not r["ok"]]
    s = {"arm": label, "attempts": len(rows), "failures": len(fails),
         "failure_dirs": [r["dir"] for r in fails],
         "p50_headers_ms": pct([r["headers_ms"] for r in ok_rows], 0.5),
         "p95_headers_ms": pct([r["headers_ms"] for r in ok_rows], 0.95),
         "p50_first_content_ms": pct([r["first_content_ms"] for r in ok_rows], 0.5),
         "p95_first_content_ms": pct([r["first_content_ms"] for r in ok_rows], 0.95),
         "p50_completion_ms": pct([r["completion_ms"] for r in ok_rows], 0.5),
         "p95_completion_ms": pct([r["completion_ms"] for r in ok_rows], 0.95),
         "prompt_tokens_sum": sum(r["prompt_tokens"] or 0 for r in rows),
         "completion_tokens_sum": sum(r["completion_tokens"] or 0 for r in rows)}
    s["tokens_per_ok_result"] = round(
        (s["prompt_tokens_sum"] + s["completion_tokens_sum"]) / max(1, len(ok_rows)), 1)
    return s


def main():
    res = {"kind": "w1402_timing_compare", "date": "2026-10-31", "batch": os.path.basename(BATCH),
           "arms": {}}
    for prefix, label in (("parent", "r7_supersede"), ("candidate", "r9_slim")):
        rows = collect(prefix)
        if not rows:
            print("no rows for", prefix)
            continue
        res["arms"][label] = summarize(rows, label)
        print("%s: attempts=%d fail=%d p50_completion=%s p95_completion=%s tok/ok=%s" % (
            label, res["arms"][label]["attempts"], res["arms"][label]["failures"],
            res["arms"][label]["p50_completion_ms"], res["arms"][label]["p95_completion_ms"],
            res["arms"][label]["tokens_per_ok_result"]))
    json.dump(res, open(OUT, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("written", OUT)


if __name__ == "__main__":
    main()
