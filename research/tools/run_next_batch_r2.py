# -*- coding: utf-8 -*-
# tools/run_next_batch_r2.py — 在用户有效新授权内跑 BATCH-R2-CTX-20261024 真实 48 次对照。
# 单一入口：加载已冻结计划(reference_product_v1 vs v4_intent_context_r2 / E007·E010·E012) + 授权单，
# 走 execute_plan(mode=live)：逐 slot 交错、整批 ensure_batch(48) 首发预检、fail-fast、无重试/补配对；
# 落原始答卷 + 两臂分层 + 配对(真实 token/时延)。**未获新授权不得运行**；授权单 authorized=false 时被拒。
import json, os, sys, traceback

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep

REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "r2_next_batch_20261024")
PLAN = os.path.join(REFS, "INTERLEAVE_PLAN.json")
AUTH = os.path.join(REFS, "AUTHORIZATION.json")   # 用户批准后落签的授权单（authorized=true/ACTIVE）
LIVE = os.path.join(ROOT, "logs", "r2_ctx_live_20261024")
REPORTS = os.path.join(ROOT, "logs", "r2_ctx_live_20261024_reports")
MARK = os.path.join(ROOT, "logs", "r2_ctx_live_20261024.DONE.json")


def main():
    plan = json.load(open(PLAN, encoding="utf-8"))
    auth = json.load(open(AUTH, encoding="utf-8"))
    if not os.path.isabs(auth["budget_ledger"]):
        auth["budget_ledger"] = os.path.join(ROOT, auth["budget_ledger"])
    assert auth.get("authorized") is True and auth.get("status") == "ACTIVE", \
        "未获有效新授权(authorized!=true/ status!=ACTIVE)——不得发起真实调用"
    # §5 运行前一次核对：授权记录的实际修订后候选身份必须等于当前冻结计划身份
    frozen_cand = plan["frozen"]["identity_closure"]["candidate"]["identity_sha256"]
    bound = auth.get("frozen_bindings", {}).get("candidate_identity_sha256")
    assert bound == frozen_cand, \
        "授权绑定候选身份 %s != 当前冻结计划 %s（勿拿旧身份冒充修订后代码）" % (bound, frozen_cand)
    assert auth["real_endpoint"]["max_attempts"] == plan["total_attempts"] == 48, \
        "额度/计划次数不一致"
    print("LIVE start: batch=%s attempts=%d endpoint=%s model=%s temp=%s cand_id=%s" % (
        plan["batch"]["batch_id"], plan["total_attempts"],
        auth["real_endpoint"]["api_base"], auth["real_endpoint"]["model"],
        auth["real_endpoint"]["temperature"], frozen_cand[:12]))
    sys.stdout.flush()
    res = ep.execute_plan(plan, mode="live", authorization=auth, out_base=LIVE)
    json.dump(res, open(os.path.join(ROOT, "logs", "r2_ctx_live_20261024.RESULT.json"),
                        "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    rep = ep.reports_from_batch(res, plan, LIVE, out_dir=REPORTS)
    done = {
        "batch_id": plan["batch"]["batch_id"],
        "authorization_id": res.get("authorization_id"),
        "arm_mapping": plan["arm_mapping"],
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
    print("LIVE done:", json.dumps(done, ensure_ascii=False)[:500])


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        err = {"error": "%s: %s" % (type(e).__name__, e), "trace": traceback.format_exc()}
        json.dump(err, open(MARK, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print("LIVE ERROR:", err["error"])
        sys.exit(1)
