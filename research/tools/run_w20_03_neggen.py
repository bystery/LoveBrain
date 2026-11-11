# -*- coding: utf-8 -*-
"""tools/run_w20_03_neggen.py — W20-03 负对照生成（判别力测试专用，禁入晋级）。

neg_a_amnesia / neg_b_dumper / neg_c_service × AK01–AK12（单位置集），
lb_runner openai_compat 真实跑出，36 次模型调用（每轮 1 次），
账本 AUTH-W20-NEGGEN-20261103-01（cap 40）。超 1 次/轮的溢出调用事后同账补记。
并发 6。fail 不重试（no_retry/no_makeup），失败位如实留空。
"""
import concurrent.futures
import glob
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
from isolation.authorization_budget import AuthorizationBudget, BudgetRefused  # noqa: E402

REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030")
AUTH_DOC = os.path.join(REFS, "AUTHORIZATION_W20_NEGGEN.json")
AID = "AUTH-W20-NEGGEN-20261103-01"
CAP = 40
OUT = os.path.join(ROOT, "logs", "w20_03_neggen_20261103")
NEG = ["neg_a_amnesia", "neg_b_dumper", "neg_c_service"]
SIDS = ["AK%02d" % i for i in range(1, 13)]
ANCHOR_DIR = os.path.join(ROOT, "benchmark", "stories_v2", "anchors_20261028")


def main():
    doc = json.load(open(AUTH_DOC, encoding="utf-8"))
    scope = doc["scope"]
    budget = AuthorizationBudget(os.path.join(ROOT, "logs", "auth_budget", AID + ".jsonl"),
                                 AID, CAP, status="ACTIVE", scope=scope)
    budget.ensure_batch(36, purpose="w20_03_neggen")
    print("budget:", budget.summary())

    jobs = [(c, s) for c in NEG for s in SIDS]

    def run_one(job):
        cand, sid = job
        attempt = "negrun:%s:%s" % (cand, sid)
        try:
            budget.reserve(attempt, purpose="w20_03_neggen")
        except BudgetRefused as e:
            return {"job": list(job), "status": "BUDGET_REFUSED", "error": str(e)}
        ep_path = os.path.join(ANCHOR_DIR, sid, "episode.json")
        out_dir = os.path.join(OUT, "%s_%s" % (cand, sid))
        env = dict(os.environ, LB_API_BASE="http://127.0.0.1:17770/v1",
                   LB_MODEL="dsf", LB_API_KEY="local",
                   PYTHONIOENCODING="utf-8", PYTHONUNBUFFERED="1")
        p = subprocess.run([sys.executable, os.path.join(ROOT, "arena", "lb_runner.py"),
                            "--episode", ep_path, "--candidate", cand,
                            "--out", out_dir, "--adapter", "openai_compat"],
                           capture_output=True, text=True, encoding="utf-8",
                           errors="replace", timeout=600, env=env, cwd=ROOT)
        # 对账：实际模型调用数（calls.json）
        calls_files = glob.glob(os.path.join(out_dir, "**", "calls.json"), recursive=True)
        n_calls = 0
        for cf in calls_files:
            try:
                n_calls += len(json.load(open(cf, encoding="utf-8")).get("calls", []))
            except Exception:
                pass
        for i in range(1, max(0, n_calls - 1) + 1):  # 溢出调用同账补记（首调用已占）
            try:
                budget.reserve("%s:x%d" % (attempt, i), purpose="w20_03_neggen_overflow")
                budget.record("attempt_completed", "%s:x%d" % (attempt, i))
            except BudgetRefused:
                break
        ok = p.returncode == 0
        (budget.record if ok else budget.record)(
            "attempt_completed" if ok else "attempt_failed", attempt,
            **({} if ok else {"error": (p.stderr or p.stdout)[-300:]}))
        return {"job": list(job), "status": "OK" if ok else "RUN_FAIL",
                "n_calls": n_calls, "rc": p.returncode}

    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as ex:
        for r in ex.map(run_one, jobs):
            print(r)
            results.append(r)
    json.dump({"kind": "w20_03_neggen_result", "authorization": AID, "results": results,
               "budget": budget.summary()},
              open(os.path.join(REFS, "W20_03_NEGGEN_RESULT.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    n_ok = sum(1 for r in results if r["status"] == "OK")
    print("done: %d/36 OK" % n_ok)


if __name__ == "__main__":
    main()
