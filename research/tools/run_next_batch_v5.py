# -*- coding: utf-8 -*-
# tools/run_next_batch_v5.py — 在用户有效新授权内跑 BATCH-V5-CTX-20261025 真实 48 次对照。
# 单一入口:加载已冻结计划(reference_product_v1 vs v5_purpose_aligned / E007·E008·E009)+授权单,
# 走 execute_plan(mode=live):逐 slot 交错、整批 ensure_batch(48) 首发预检、fail-fast、无重试/补配对。
# **未获新授权不得运行**;授权单 authorized!=true/status!=ACTIVE 或身份不符时在派发前拒绝(零网络)。
import json, os, sys, traceback

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep

REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "v5_next_batch_20261025")
PLAN = os.path.join(REFS, "INTERLEAVE_PLAN.json")
AUTH = os.path.join(REFS, "AUTHORIZATION.json")   # 批准后由用户/受托落签(authorized=true/ACTIVE)
LIVE = os.path.join(ROOT, "logs", "v5_ctx_live_20261025")
REPORTS = os.path.join(ROOT, "logs", "v5_ctx_live_20261025_reports")
MARK = os.path.join(ROOT, "logs", "v5_ctx_live_20261025.DONE.json")


def main():
    plan = json.load(open(PLAN, encoding="utf-8"))
    auth = json.load(open(AUTH, encoding="utf-8"))
    if not os.path.isabs(auth["budget_ledger"]):
        auth["budget_ledger"] = os.path.join(ROOT, auth["budget_ledger"])
    assert auth.get("authorized") is True and auth.get("status") == "ACTIVE", \
        "未获有效新授权(authorized!=true/ status!=ACTIVE)——不得发起真实调用"
    # 运行前一次核对:授权绑定身份必须等于当前冻结计划身份(勿拿旧代码冒充)
    fz = plan["frozen"]["identity_closure"]
    assert auth["frozen_bindings"]["parent_identity_sha256"] == fz["parent"]["identity_sha256"], "原版身份不符"
    assert auth["frozen_bindings"]["candidate_identity_sha256"] == fz["candidate"]["identity_sha256"], \
        "v5 候选身份不符(授权绑 %s != 计划 %s)" % (auth["frozen_bindings"]["candidate_identity_sha256"],
                                                  fz["candidate"]["identity_sha256"])
    assert auth["real_endpoint"]["max_attempts"] == plan["total_attempts"] == 48, "额度/计划次数不一致"
    print("LIVE start: batch=%s attempts=%d endpoint=%s model=%s temp=%s cand_id=%s" % (
        plan["batch"]["batch_id"], plan["total_attempts"],
        auth["real_endpoint"]["api_base"], auth["real_endpoint"]["model"],
        auth["real_endpoint"]["temperature"], fz["candidate"]["identity_sha256"][:12]))
    sys.stdout.flush()
    res = ep.execute_plan(plan, mode="live", authorization=auth, out_base=LIVE)
    json.dump(res, open(os.path.join(ROOT, "logs", "v5_ctx_live_20261025.RESULT.json"),
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
        print("LIVE ERROR:", err["error"])
        sys.exit(1)
