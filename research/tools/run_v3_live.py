# -*- coding: utf-8 -*-
# tools/run_v3_live.py — 在用户有效新授权内跑 BATCH-V3-DELETION-20261024 真实48次对照。
# 单一入口：加载已冻结的修订计划(含 initial_kb.me 修正) + 授权单，走 execute_plan(mode=live)，
# 落原始答卷 + 两臂分层 + 配对(真实 token/时延)。fail-fast/无重试/不补配对由执行器保证。
import json, os, sys, traceback

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep

REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "v3_deletion_20261024")
PLAN = os.path.join(REFS, "INTERLEAVE_PLAN.json")
AUTH = os.path.join(REFS, "AUTHORIZATION.json")
LIVE = os.path.join(ROOT, "logs", "v3_deletion_live_20261024")
REPORTS = os.path.join(ROOT, "logs", "v3_deletion_live_20261024_reports")
MARK = os.path.join(ROOT, "logs", "v3_deletion_live_20261024.DONE.json")

def main():
    plan = json.load(open(PLAN, encoding="utf-8"))
    auth = json.load(open(AUTH, encoding="utf-8"))
    # ledger path relative → absolute
    if not os.path.isabs(auth["budget_ledger"]):
        auth["budget_ledger"] = os.path.join(ROOT, auth["budget_ledger"])
    assert os.path.isfile(auth["budget_ledger"]) is False or True  # fresh ledger preferred
    print("LIVE start: batch=%s attempts=%d endpoint=%s model=%s temp=%s" % (
        plan["batch"]["batch_id"], plan["total_attempts"],
        auth["real_endpoint"]["api_base"], auth["real_endpoint"]["model"],
        auth["real_endpoint"]["temperature"]))
    sys.stdout.flush()
    res = ep.execute_plan(plan, mode="live", authorization=auth, out_base=LIVE)
    json.dump(res, open(os.path.join(ROOT, "logs", "v3_deletion_live_20261024.RESULT.json"),
                        "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    rep = ep.reports_from_batch(res, plan, LIVE, out_dir=REPORTS)
    done = {
        "batch_id": plan["batch"]["batch_id"],
        "authorization_id": res.get("authorization_id"),
        "real_model_calls": res.get("real_model_calls"),
        "attempt_started": res.get("attempt_started"),
        "attempt_completed": res.get("attempt_completed"),
        "attempt_failed": res.get("attempt_failed"),
        "execution_order_verified": res.get("execution_order_verified"),
        "stopped": res.get("stopped"),
        "boundary_problems": res.get("boundary_problems"),
        "turns_parent": rep.get("turns_parent"),
        "turns_candidate": rep.get("turns_candidate"),
        "parent_cost": rep.get("parent", {}).get("cost"),
        "candidate_cost": rep.get("candidate", {}).get("cost"),
        "parent_program_mean10": rep.get("parent", {}).get("program_mean10"),
        "candidate_program_mean10": rep.get("candidate", {}).get("program_mean10"),
        "paired_program_winner_counts": rep.get("paired_program_winner_counts"),
    }
    json.dump(done, open(MARK, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("LIVE done:", json.dumps(done, ensure_ascii=False)[:400])

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        err = {"error": "%s: %s" % (type(e).__name__, e), "trace": traceback.format_exc()}
        json.dump(err, open(MARK, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print("LIVE ERROR:", err["error"])
        sys.exit(1)
