# -*- coding: utf-8 -*-
"""tools/fs1_collect_check.py — FS1 首筛两批收口核对（W04-03 §C 核对单的自动化）。

用法：python tools/fs1_collect_check.py
只读核对，零调用；输出 refs/.../global_harness_program_20261030/FS1_COLLECTION_REPORT.json。
任何一项 FAIL → 先修再评（不得带病进九维判读）。
"""
import glob, hashlib, json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "fs1_first_screening_20261030")
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                   "FS1_COLLECTION_REPORT.json")
BATCHES = {
    "FS1_A": ("BATCH-FS1-A-E3VSREF-20261030", os.path.join(ROOT, "logs", "fs1_a_live2_20261030")),
    "FS1_B": ("BATCH-FS1-B-BMINVSREF-20261030", os.path.join(ROOT, "logs", "fs1_b_live2_20261030")),
}
LEDGER = os.path.join(ROOT, "logs", "auth_budget", "AUTH-FS1-FIRSTSCREEN-20261030-02.jsonl")


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def main():
    rep = {"kind": "fs1_collection_report", "checks": []}

    led = {"started": 0, "completed": 0, "failed": 0}
    for line in open(LEDGER, encoding="utf-8"):
        e = json.loads(line)
        k = e.get("kind", "")
        if k in ("attempt_started", "attempt_completed", "attempt_failed"):
            led[k.replace("attempt_", "")] += 1
    rep["ledger"] = led

    for key, (bid, out) in BATCHES.items():
        done_json = None
        # DONE 摘要与 out 目录同级：<out>.DONE.json（run_max288_batch.py 落盘约定），优先且权威
        done_path = out + ".DONE.json"
        if os.path.isfile(done_path):
            try:
                d = json.load(open(done_path, encoding="utf-8"))
                if d.get("batch_id") == bid and d.get("mode") == "live":
                    done_json = d
            except Exception:
                done_json = None
        if done_json is None:  # 回退：目录内扫描
            for f in glob.glob(os.path.join(out, "_reports", "*.json")) + \
                    glob.glob(os.path.join(out, "*.json")):
                try:
                    d = json.load(open(f, encoding="utf-8"))
                except Exception:
                    continue
                if d.get("batch_id") == bid and d.get("mode") == "live":
                    done_json = d
        if done_json is None:
            rep["checks"].append({"batch": key, "check": "done_json", "verdict": "NOT_DONE_YET"})
            continue
        ok = done_json.get("completed", 0) == done_json.get("attempts_planned") and not done_json.get("failed")
        rep["checks"].append({"batch": key, "check": "completeness",
                              "verdict": "PASS" if ok else "FAIL",
                              "planned": done_json.get("attempts_planned"),
                              "completed": done_json.get("completed"),
                              "failed": done_json.get("failed"),
                              "stopped": done_json.get("stopped")})
        rep["checks"].append({"batch": key, "check": "order_verified", "verdict": "PASS" if done_json.get("order_verified") else "FAIL"})
        rep["checks"].append({"batch": key, "check": "dispatch_bijection", "verdict": "PASS" if done_json.get("dispatch_bijection") else "FAIL"})

        turn_dirs = [d for d in os.listdir(out) if d.endswith(("_E021", "_E026", "_E032", "_E033")) or "E0" in d]
        missing_scoring = []
        scoring_count = 0
        for base_d, _ds, files in os.walk(out):
            for f in files:
                if f == "scoring.json":
                    scoring_count += 1
                    try:
                        json.load(open(os.path.join(base_d, f), encoding="utf-8"))
                    except Exception as e:
                        missing_scoring.append(os.path.relpath(os.path.join(base_d, f), out) + " unparseable")
        rep["checks"].append({"batch": key, "check": "scoring_json_count", "verdict": "PASS" if scoring_count >= 2 else "FAIL",
                              "count": scoring_count, "problems": missing_scoring[:5]})
        calls_ok = any(f == "calls.json" for _r, _d, fs in os.walk(out) for f in fs)
        rep["checks"].append({"batch": key, "check": "calls_ledger_present", "verdict": "PASS" if calls_ok else "FAIL"})

    total_ok = all(c.get("verdict") in ("PASS", "NOT_DONE_YET") for c in rep["checks"])
    rep["overall"] = "ALL_PASS" if total_ok else "ATTENTION_NEEDED"
    json.dump(rep, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(OUT, encoding="utf-8"))
    print(json.dumps({"overall": rep["overall"], "ledger": led,
                      "checks": [(c.get("batch", "") + ":" + c["check"] + "=" + c.get("verdict", "")) for c in rep["checks"]]},
                     ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
