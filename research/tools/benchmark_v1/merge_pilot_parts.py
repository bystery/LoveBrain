# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 2 · pilot 分片合并汇总（merge_pilot_parts）。

背景：PILOT2 批因 API 限速拆三进程（part_b / part_c / oldT09），各自落盘
PILOT_RESULTS.csv + run_records.json。本工具做确定性合并：
  1. 行级合并三份 PILOT_RESULTS.csv（列并集，缺列留空）；
  2. 重算合并口径的 per-arm 汇总（parse/contract/error/latency p50/p95/tokens/
     diag 命中率），并给出 DIRECT vs CURRENT 的并排对照；
  3. 合并 run_records.json（记账：attempts/turns_done/status）；
  4. 输出 merge_summary.json + 合并后的 PILOT_RESULTS_MERGED.csv 供
     PHASE2_PILOT_REPORT.md 引用。

不做任何语义判断（差异归因/判别力结论归 Worker 主会话）。

用法：
  python -I tools/benchmark_v1/merge_pilot_parts.py \
    --parts runs/pilot2_wave4_live_b runs/pilot2_wave4_live_c runs/pilot2_oldT09_live \
    --out-dir refs/benchmark_v1_20261107/phase2/pilot2_merged
"""
import argparse
import csv
import glob
import io
import json
import os
import statistics


def load_rows(run_dir):
    p = os.path.join(run_dir, "PILOT_RESULTS.csv")
    if not os.path.isfile(p):
        return [], None
    with io.open(p, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    return rows, p


def agg(rows):
    arms = {}
    for r in rows:
        arms.setdefault(r.get("arm_id") or r.get("arm") or "?", []).append(r)
    out = {}
    for arm, rs in sorted(arms.items()):
        lat = sorted(float(r["latency_ms"]) for r in rs if r.get("latency_ms"))
        n = len(rs)
        tp = sum(int(r["tokens_prompt"] or 0) for r in rs)
        tc = sum(int(r["tokens_completion"] or 0) for r in rs)
        diag_req_hit = sum(int(r.get("diag_required_hits") or 0) for r in rs)
        diag_req_tot = sum(int(r.get("diag_required_total") or 0) for r in rs)
        out[arm] = {
            "rows": n,
            "parse_ok": sum(1 for r in rs if r.get("parse_ok") == "True"),
            "contract_ok": sum(1 for r in rs if r.get("contract_ok") == "True"),
            "error_types": dict(sorted(
                __import__("collections").Counter(
                    r.get("error_type") or "" for r in rs if r.get("error_type")
                ).items())),
            "latency_ms_p50": (statistics.median(lat) if lat else None),
            "latency_ms_p95": (lat[max(0, int(len(lat) * 0.95) - 1)] if lat else None),
            "tokens_prompt_total": tp,
            "tokens_completion_total": tc,
            "diag_required_hits": diag_req_hit,
            "diag_required_total": diag_req_tot,
            "diag_required_hit_rate": (round(diag_req_hit / diag_req_tot, 4)
                                       if diag_req_tot else None),
            "veto": sum(1 for r in rs if (r.get("veto") or "").strip() not in ("", "False", "false", "None")),
        }
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--parts", nargs="+", required=True)
    ap.add_argument("--out-dir", required=True)
    a = ap.parse_args()
    os.makedirs(a.out_dir, exist_ok=True)

    all_rows, header, sources = [], None, {}
    for d in a.parts:
        rows, src = load_rows(d)
        sources[d] = len(rows)
        for r in rows:
            r["_source_part"] = os.path.basename(d.rstrip("/\\"))
        all_rows += rows
        if rows and header is None:
            header = list(rows[0].keys())
        elif rows:
            for k in rows[0].keys():
                if k not in header:
                    header.append(k)

    merged_csv = os.path.join(a.out_dir, "PILOT_RESULTS_MERGED.csv")
    if header:
        with io.open(merged_csv, "w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=header, extrasaction="ignore")
            w.writeheader()
            for r in all_rows:
                w.writerow({k: r.get(k, "") for k in header})
    else:
        print("WARN: 所有 parts 均无 PILOT_RESULTS.csv（aggregate_pilot 尚未对 parts 运行？）")

    records = []
    for d in a.parts:
        rp = os.path.join(d, "run_records.json")
        if os.path.isfile(rp):
            for rec in json.load(io.open(rp, encoding="utf-8")):
                rec["_source_part"] = os.path.basename(d.rstrip("/\\"))
                records.append(rec)

    attempts_total = 0
    for d in a.parts:
        for ev in glob.glob(os.path.join(d, "*", "*", "gateway_events.jsonl")):
            with io.open(ev, encoding="utf-8", errors="replace") as f:
                attempts_total += sum(1 for line in f if '"attempt_started"' in line)

    summary = {
        "kind": "pilot2_merged_summary",
        "parts": sources,
        "total_rows": len(all_rows),
        "total_records": len(records),
        "total_api_attempts_all_parts": attempts_total,
        "cap": 1500,
        "cap_remaining": 1500 - attempts_total,
        "records_status": {
            "ok": sum(1 for r in records if r.get("status") == "ok"),
            "error": sum(1 for r in records if r.get("status") != "ok"),
            "error_detail": [{"arm": r.get("arm"), "episode": r.get("episode"),
                              "error": str(r.get("error"))[:120]}
                             for r in records if r.get("status") != "ok"],
        },
        "per_arm": agg(all_rows),
        "arm_pair_note": "DIRECT vs CURRENT 并排对照见 per_arm；差异归因与判别力结论由 Worker 另行分析",
    }
    with io.open(os.path.join(a.out_dir, "merge_summary.json"), "w",
                 encoding="utf-8", newline="\n") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("merged rows=%d records=%d attempts=%d/%d -> %s"
          % (len(all_rows), len(records), attempts_total, 1500, a.out_dir))
    for arm, s in sorted(summary["per_arm"].items()):
        print("  %s: rows=%d parse=%d contract=%d p50=%s veto=%d"
              % (arm, s["rows"], s["parse_ok"], s["contract_ok"],
                 s["latency_ms_p50"], s["veto"]))


if __name__ == "__main__":
    main()
