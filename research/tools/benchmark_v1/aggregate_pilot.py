# -*- coding: utf-8 -*-
"""Benchmark V1 · Pilot 聚合（指导书 §6.4 四类指标）。

只做指标计算与汇总，**不做正式冠军判决**；程序层（契约/断言）结果不冒充回复质量。
输出：PILOT_RESULTS.csv、pilot_summary.json、PILOT_EXECUTION_REPORT.md。
"""
import csv
import datetime
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
import normalize_output  # noqa: E402

ARMS = ("DIRECT", "COT", "LEGACY", "CURRENT")
METRIC_VERSION = "pilot_metrics_v0"


def _pctl(xs, q):
    if not xs:
        return None
    xs = sorted(xs)
    k = max(0, min(len(xs) - 1, int(round(q / 100.0 * (len(xs) - 1)))))
    return xs[k]


def _turn_index(case_id):
    try:
        return int(case_id.rsplit("_T", 1)[-1])
    except Exception:
        return None


def collect(run_dir):
    rows = list(normalize_output.iter_run_dir(run_dir, ARMS))
    import gold_diag
    gold_diag.annotate_rows(rows)
    for r in rows:
        r["turn_index"] = _turn_index(r["case_id"])
        toks = r.get("tokens") or {}
        r["tokens_prompt"] = toks.get("prompt")
        r["tokens_completion"] = toks.get("completion")
        r["tokens_source"] = toks.get("source")
    rows.sort(key=lambda r: (ARMS.index(r["arm_id"]), r["episode_id"],
                             r.get("turn_index") or 0))
    return rows


def summarize(rows):
    out = {"metric_version": METRIC_VERSION,
           "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
           "arms": {}}
    for arm in ARMS:
        rs = [r for r in rows if r["arm_id"] == arm]
        if not rs:
            out["arms"][arm] = {"positions": 0}
            continue
        errs = {}
        for r in rs:
            e = r.get("error_type")
            if e:
                errs[e] = errs.get(e, 0) + 1
        lat = [r["latency_ms"] for r in rs if isinstance(r.get("latency_ms"), int)]
        usage = [r for r in rs if r.get("tokens_source") == "api_usage"]
        char_usage = [r for r in rs if r.get("tokens_source") not in ("api_usage", None)]
        diag_ok = [r for r in rs if r.get("diag_status") == "OK"]
        req = sum(r.get("diag_required_total") or 0 for r in diag_ok)
        hits = sum(r.get("diag_required_hits") or 0 for r in diag_ok)
        out["arms"][arm] = {
            "positions": len(rs),
            "completed_reply": sum(1 for r in rs if r["action"] == "reply"),
            "no_reply": sum(1 for r in rs if r["action"] == "no_reply"),
            "parse_ok_rate": round(sum(1 for r in rs if r["parse_ok"]) / len(rs), 4),
            "contract_ok_rate": round(sum(1 for r in rs if r["contract_ok"]) / len(rs), 4),
            "empty_or_error": errs,
            "diag_na": len(rs) - len(diag_ok),
            "diag_cancel_affirm_violations": sum(r.get("diag_cancel_affirm") or 0
                                                 for r in diag_ok),
            "diag_required_total": req, "diag_required_hits": hits,
            "diag_required_affirm_rate": (round(hits / req, 4) if req else None),
            "latency_ms_p50": _pctl(lat, 50), "latency_ms_p95": _pctl(lat, 95),
            "api_usage_rows": len(usage),
            "prompt_tokens_sum": sum(r["tokens_prompt"] or 0 for r in usage),
            "completion_tokens_sum": sum(r["tokens_completion"] or 0 for r in usage),
            "non_api_usage_rows": len(char_usage),
            "program_score10_mean": (round(sum(r["score10"] for r in rs
                                               if isinstance(r.get("score10"), (int, float)))
                                           / max(1, sum(1 for r in rs
                                                        if isinstance(r.get("score10"),
                                                                      (int, float)))), 3)),
            "note": "score10=程序层(契约/断言)读数，不冒充回复质量；开放式质量=盲评包+带资格限"
                    "制的 judge 诊断，另行记录",
        }
    return out


def write_outputs(rows, summary, deliver_dir):
    os.makedirs(deliver_dir, exist_ok=True)
    cols = ["arm_id", "episode_id", "case_id", "turn_index", "action", "primary_text",
            "primary_source", "parse_ok", "contract_ok", "error_type", "score10", "veto",
            "latency_ms", "tokens_prompt", "tokens_completion", "tokens_source",
            "gateway_calls", "model", "adapter", "normalize_version",
            "diag_status", "diag_cancel_affirm", "diag_required_hits",
            "diag_required_misses", "diag_required_total", "diag_version"]
    with open(os.path.join(deliver_dir, "PILOT_RESULTS.csv"), "w",
              encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow(r)
    with open(os.path.join(deliver_dir, "pilot_summary.json"), "w",
              encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)


def report_markdown(rows, summary, run_dir):
    L = ["# Pilot 执行报告（%s）" % summary["generated_at"], "",
         "> 指标版本 `%s`；本报告只验证指标能否正确计算，**不做冠军判决**。" % METRIC_VERSION,
         "> 程序分/契约读数 ≠ 回复质量；开放式质量见盲评包（NOT_COLLECTED 项如实标注）。",
         "> 客观诊断层 `%s`：跨四组统一的 gold 断言确定性检查（诊断用，非判决）。"
         % "benchmark_v1_gold_diag_v0", "",
         "**mock/capture 模式注记**：harness 组（LEGACY/CURRENT）的 "
         "`illegal_source_id` veto 来自 MockAdapter 台本引用了不存在的消息源（m0/m2），"
         "属离线伪影；DIRECT/COT 轻量契约 `{action, recommended}` 不在 harness 评分器"
         "协议键内（其 veto 为预期伪影，判据以归一化 parse/contract 为准）。"
         "真实行为读数仅来自 --live。", ""]
    L.append("| 组 | 轮位 | 可用回复 | 合法不回 | parse_ok | contract_ok | 主要错误 | "
             "p50/p95 时延(ms) | prompt tok | completion tok | 程序分均值 |")
    L.append("|---|---|---|---|---|---|---|---|---|---|---|")
    for arm in ARMS:
        a = summary["arms"].get(arm, {})
        if not a.get("positions"):
            L.append("| %s | 0 | - | - | - | - | - | - | - | - | - |" % arm)
            continue
        errs = a.get("empty_or_error") or {}
        errs_s = ", ".join("%s×%d" % (k, v) for k, v in sorted(errs.items())) or "无"
        L.append("| %s | %d | %d | %d | %.3f | %.3f | %s | %s/%s | %s | %s | %s |" % (
            arm, a["positions"], a["completed_reply"], a["no_reply"],
            a["parse_ok_rate"], a["contract_ok_rate"], errs_s,
            a.get("latency_ms_p50"), a.get("latency_ms_p95"),
            a.get("prompt_tokens_sum"), a.get("completion_tokens_sum"),
            a.get("program_score10_mean")))
    L.append("")
    L.append("## 客观诊断（gold_diag_v0，跨四组统一）")
    L.append("")
    L.append("| 组 | 可判轮 | N/A 轮 | 取消族坐实违例 | required 命中/总数 | 命中率 |")
    L.append("|---|---|---|---|---|---|")
    for arm in ARMS:
        a = summary["arms"].get(arm, {})
        if not a.get("positions"):
            continue
        L.append("| %s | %d | %d | %d | %s/%s | %s |" % (
            arm, a["positions"] - a.get("diag_na", 0), a.get("diag_na", 0),
            a.get("diag_cancel_affirm_violations", 0),
            a.get("diag_required_hits"), a.get("diag_required_total"),
            a.get("diag_required_affirm_rate") if a.get("diag_required_affirm_rate")
            is not None else "N/A"))
    L.append("")
    fails = [r for r in rows if r.get("error_type")]
    if fails:
        L.append("## 失败/异常轮位明细（如实保留，不伪造完整结果）")
        L.append("")
        L.append("| 组 | case | 错误 |")
        L.append("|---|---|---|")
        for r in fails[:40]:
            L.append("| %s | %s | %s |" % (r["arm_id"], r["case_id"], r["error_type"]))
    run_sum = os.path.join(run_dir, "run_summary.json")
    if os.path.isfile(run_sum):
        with open(run_sum, "r", encoding="utf-8") as f:
            rs = json.load(f)
        L.append("")
        L.append("## 运行记录")
        L.append("")
        for rec in rs.get("records", []):
            L.append("- %s/%s [%s] %s %s" % (
                rec["arm"], rec["episode"], rec["mode"], rec.get("status"),
                ("turns_done=%s" % rec.get("turns_done_before_error"))
                if rec.get("status") == "error" else ""))
        blocked = os.path.join(run_dir, "LIVE_BLOCKED.json")
        if os.path.isfile(blocked):
            with open(blocked, "r", encoding="utf-8") as f:
                b = json.load(f)
            L.append("")
            L.append("**LIVE BLOCKED**: %s %s" % (b.get("reason"), b.get("problems")))
    path = os.path.join(run_dir, "PILOT_EXECUTION_REPORT.md")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L) + "\n")
    return path


def main():
    run_dir, deliver_dir = sys.argv[1], sys.argv[2]
    rows = collect(run_dir)
    summary = summarize(rows)
    write_outputs(rows, summary, deliver_dir)
    path = report_markdown(rows, summary, run_dir)
    print("rows=%d -> %s" % (len(rows), path))


if __name__ == "__main__":
    main()
