# -*- coding: utf-8 -*-
# tools/run_newstory96.py — BATCH-NEWSTORY-96-20261026 真实 96 次独立新故事检验（流式共同身份）。
# 复用 exam_interleave_plan.execute_plan 正式执行链（逐 slot 交错、整批 ensure_batch 首发预检、
# fail-fast、无重试/补配对）。与 v5 批唯一差异：两臂共同流式配置身份
# （LB_STREAM_TIMING=1 LB_STREAM_USAGE=1，D5 已验证的测量身份），结果不与历史非流式批合并。
# 授权：refs/experiment_protocol_20261023/newstory_96_20261026/AUTHORIZATION.json（落实既有持续授权）。
import json, os, sys, traceback

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep

REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "newstory_96_20261026")
PLAN = os.path.join(REFS, "INTERLEAVE_PLAN.json")
AUTH = os.path.join(REFS, "AUTHORIZATION.json")
LIVE = os.path.join(ROOT, "logs", "newstory96_live_20261026")
REPORTS = os.path.join(ROOT, "logs", "newstory96_live_20261026_reports")
MARK = os.path.join(ROOT, "logs", "newstory96_live_20261026.DONE.json")


def main():
    plan = json.load(open(PLAN, encoding="utf-8"))
    auth = json.load(open(AUTH, encoding="utf-8"))
    if not os.path.isabs(auth["budget_ledger"]):
        auth["budget_ledger"] = os.path.join(ROOT, auth["budget_ledger"])
    assert auth.get("authorized") is True and auth.get("status") == "ACTIVE", \
        "未获有效授权(authorized!=true/status!=ACTIVE)——不得发起真实调用"
    fz = plan["frozen"]["identity_closure"]
    assert auth["frozen_bindings"]["parent_identity_sha256"] == fz["parent"]["identity_sha256"], "原版身份不符"
    assert auth["frozen_bindings"]["candidate_identity_sha256"] == fz["candidate"]["identity_sha256"], \
        "候选身份不符(授权绑 %s != 计划 %s)" % (auth["frozen_bindings"]["candidate_identity_sha256"],
                                              fz["candidate"]["identity_sha256"])
    assert auth["real_endpoint"]["max_attempts"] == plan["total_attempts"] == 96, "额度/计划次数不一致"
    # 流式共同身份（全批一致，勿中途改）
    os.environ["LB_STREAM_TIMING"] = "1"
    os.environ["LB_STREAM_USAGE"] = "1"
    print("LIVE start: batch=%s attempts=%d endpoint=%s model=%s temp=%s stream=on(+usage) cand_id=%s" % (
        plan["batch"]["batch_id"], plan["total_attempts"],
        auth["real_endpoint"]["api_base"], auth["real_endpoint"]["model"],
        auth["real_endpoint"]["temperature"], fz["candidate"]["identity_sha256"][:12]))
    sys.stdout.flush()
    res = ep.execute_plan(plan, mode="live", authorization=auth, out_base=LIVE)
    json.dump(res, open(os.path.join(ROOT, "logs", "newstory96_live_20261026.RESULT.json"),
                        "w", encoding="utf-8"), ensure_ascii=False, indent=1, default=str)
    rep = ep.reports_from_batch(res, plan, LIVE, out_dir=REPORTS)
    done = {
        "batch_id": plan["batch"]["batch_id"],
        "authorization_id": res.get("authorization_id"),
        "arm_mapping": plan["arm_mapping"],
        "api_config_identity": "api_cfg:openai_compat_stream_true_v1(include_usage gated)",
        "real_model_calls": res.get("real_model_calls"),
        "attempt_started": res.get("attempt_started"),
        "attempt_completed": res.get("attempt_completed"),
        "attempt_failed": res.get("attempt_failed"),
        "execution_order_verified": res.get("execution_order_verified"),
        "stopped": res.get("stopped"),
        "boundary_problems": res.get("boundary_problems"),
        "parent_cost": rep.get("parent", {}).get("cost"),
        "candidate_cost": rep.get("candidate", {}).get("cost"),
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
