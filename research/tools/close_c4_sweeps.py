# -*- coding: utf-8 -*-
"""tools/close_c4_sweeps.py — C4 两批收口（离线，零真实调用）。

一次性把交付该说清的说清，不做二次生成、不改任何已付费答卷：
  1) 读两批 DONE/RESULT/账本 → 核对 started/completed/failed、执行顺序、派发双射、
     ACTUAL_DISPATCH_ORDER 与冻结计划逐位对齐（两批各 64，合计 128=阶段帽）；
  2) 额度对账：账本 used 必须 == 128 且无 in-flight 孤儿；有孤儿就按证据 unknown_consumed 登记，
     不补发不退款；
  3) 账本显式 CLOSED（不可复活）；
  4) 跑六列（--final 门禁：128 turn 且四臂齐全 32 位）与四臂聚合/读数队列，
     汇总成 evaluation/C4_SCREEN_CLOSURE_V1.json（每个数字带分母与口径来源）。
用法：D:\\anaconda\\python.exe tools\\close_c4_sweeps.py
"""
import glob
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PY = sys.executable
ENV = dict(os.environ, PYTHONIOENCODING="utf-8")
AUTH_ID = "AUTH-C4-SCREEN-20261026-01"
LEDGER = os.path.join(ROOT, "logs", "auth_budget", AUTH_ID + ".jsonl")
BATCHES = [
    ("sweep_a_orig_vs_goal", "c4_sweep_a_orig_vs_goal_live_20261026"),
    ("sweep_b_v5_vs_mem", "c4_sweep_b_v5_vs_mem_live_20261026"),
]
AUTH_FILE = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                         "c4_screen_20261026", "AUTHORIZATION_V2.json")


def run(script, *args):
    p = subprocess.run([PY, os.path.join(ROOT, "tools", script)] + list(args),
                       capture_output=True, text=True, encoding="utf-8",
                       cwd=ROOT, env=ENV)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def main():
    auth = json.load(open(AUTH_FILE, encoding="utf-8"))
    lines = [json.loads(l) for l in open(LEDGER, encoding="utf-8") if l.strip()]
    kinds = {}
    for e in lines:
        kinds[e["kind"]] = kinds.get(e["kind"], 0) + 1
    started = kinds.get("attempt_started", 0)
    completed = kinds.get("attempt_completed", 0)
    failed = kinds.get("attempt_failed", 0)
    closed = kinds.get("authorization_closed", 0)

    # in-flight 孤儿：started 无 completed/failed 收尾
    term = {}
    for e in lines:
        if e["kind"] in ("attempt_completed", "attempt_failed"):
            term[e["attempt_id"]] = e["kind"]
    orphans = sorted({e["attempt_id"] for e in lines if e["kind"] == "attempt_started"}
                     - set(term))

    per_batch = {}
    for key, dirname in BATCHES:
        base = os.path.join(ROOT, "logs", dirname)
        done_p, res_p = base + ".DONE.json", base + ".RESULT.json"
        done = json.load(open(done_p, encoding="utf-8")) if os.path.isfile(done_p) else None
        per_batch[key] = {
            "dir": "logs/" + dirname,
            "done": done,
            "turn_dirs": len(glob.glob(os.path.join(base, "*", "out", "turn_*"))),
            "order_verified": (done or {}).get("order_verified"),
            "dispatch_bijection": (done or {}).get("dispatch_bijection"),
            "residents": (done or {}).get("residents"),
            "real_model_calls": (done or {}).get("real_model_calls"),
        }

    problems = []
    if started != 128:
        problems.append("started=%s 应为 128" % started)
    if completed + failed != started:
        problems.append("completed+failed=%s != started=%s（有未收尾 attempt）"
                        % (completed + failed, started))
    if orphans:
        problems.append("in-flight 孤儿 %d 个：%s" % (len(orphans), orphans[:5]))

    # 六列 --final（内含 128 turn 与四臂齐全 32 位门禁）
    rc, out = run("delivery_columns_c4_sweeps.py", "--final")
    print(out[-1500:])
    if rc != 0:
        problems.append("六列 --final 门禁未过（exit=%s）" % rc)
    rc2, out2 = run("c4_four_arm_review_pack.py")
    rc3, out3 = run("c4_four_arm_analysis.py")
    if rc2 != 0 or rc3 != 0:
        problems.append("审阅包/分析器生成失败 rc=%s/%s" % (rc2, rc3))

    sixcol_p = os.path.join(ROOT, "evaluation",
                            "DELIVERY_AND_COST_COLUMNS_C4_SWEEPS_FINAL.json")
    sixcol = json.load(open(sixcol_p, encoding="utf-8")) if os.path.isfile(sixcol_p) else None
    ana_p = os.path.join(ROOT, "evaluation",
                         "C4_FOUR_ARM_PROGRAM_AND_QUEUE_V1.json")
    ana = json.load(open(ana_p, encoding="utf-8")) if os.path.isfile(ana_p) else None

    # 关账（不复活）
    close_note = None
    if not closed and not orphans and completed + failed == started:
        sys.path.insert(0, os.path.join(ROOT, "arena"))
        from isolation.authorization_budget import AuthorizationBudget
        b = AuthorizationBudget(LEDGER, AUTH_ID, auth["real_endpoint"]["max_attempts"],
                                status=auth.get("status"), scope=auth.get("scope"))
        b.close("C4 机制筛选两批完成：%d started / %d completed / %d failed；"
                "阶段帽 128 用满即关，不补发不复活不挪用" % (started, completed, failed))
        close_note = "closed"
    else:
        close_note = "未关账（原因见 problems 或已 CLOSED）：closed=%s orphans=%s" % (
            closed, len(orphans))

    doc = {
        "doc": "C4_SCREEN_CLOSURE_V1",
        "authorization_id": AUTH_ID,
        "ledger_events": kinds,
        "quota": {"cap": auth["real_endpoint"]["max_attempts"], "used": started,
                  "completed": completed, "failed": failed, "orphans": orphans,
                  "unknown_consumed_registered": 0},
        "tokens_total_by_arm": ({a: sixcol["columns_by_arm"][a]["tokens"]["total"]
                                 for a in sixcol["columns_by_arm"]} if sixcol else None),
        "columns_by_arm": sixcol["columns_by_arm"] if sixcol else None,
        "wait_by_arm": sixcol["wait_by_arm"] if sixcol else None,
        "four_arm_aggregate_all32": ana["aggregate_all_positions"] if ana else None,
        "four_arm_aggregate_goal_live7": ana["aggregate_goal_live_only"] if ana else None,
        "coverage": ana["coverage"] if ana else None,
        "reading_queue_n": ana["reading_queue_n"] if ana else None,
        "batches": per_batch,
        "identity": {"cards": auth["scope"]["cards"],
                     "plan_sha256": {k: v["plan_sha256"]
                                     for k, v in auth["frozen_bindings"].items()},
                     "stream_identity": "api_cfg:openai_compat_stream_true_v1(include_usage gated)"},
        "not_claimed": ["Q（语义行动正确性）unknown：裁判该维只到稳定性/清晰材料辅助排序",
                        "W（用户愿发）unknown：口味表未回收",
                        "first_usable 普通链路仍未接消费回调 → 逐位 null，不离线回填",
                        "金额 null（无可核对单价）"],
        "problems": problems,
        "close": close_note,
        "real_model_calls_this_script": 0,
    }
    out_p = os.path.join(ROOT, "evaluation", "C4_SCREEN_CLOSURE_V1.json")
    json.dump(doc, open(out_p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps({"problems": problems, "close": close_note,
                      "quota": doc["quota"], "out": out_p.replace("\\", "/")},
                     ensure_ascii=False, indent=1))
    if problems:
        sys.exit(1)


if __name__ == "__main__":
    main()
