# -*- coding: utf-8 -*-
r"""tools/build_p06_authorization.py — P06 三批两臂 192 次的有限新授权（机器填哈希，零调用）。

本文件只新建 P06 授权登记，不复活任何 CLOSED 单，不把旧 173/128/96 预算重复登记为新增。
运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\build_p06_authorization.py
"""
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
P06 = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "p06_20261027")
SUMMARY = os.path.join(P06, "P06_PLAN_SUMMARY.json")
RUNNER = os.path.join(HERE, "run_max288_batch.py")
AUTH_ID = "AUTH-P06-MAX288V2-20261027-01"
LEDGER = "logs/auth_budget/%s.jsonl" % AUTH_ID
OUT = os.path.join(P06, "AUTHORIZATION_P06.json")


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def main():
    s = json.load(open(SUMMARY, encoding="utf-8"))
    frozen = {}
    for key, b in s["batches"].items():
        frozen[key] = {
            "plan_file": "refs/experiment_protocol_20261023/p06_20261027/" + b["plan_file"],
            "plan_sha256": b["plan_sha256"],
            "batch_id": b["batch_id"],
            "cards_file": s["cards_path"],
            "attempts": b["attempts"],
            "parent": b["arm_map"]["parent"],
            "candidate": b["arm_map"]["candidate"],
            "parent_identity_sha256": b["parent_identity_sha256"],
            "candidate_identity_sha256": b["candidate_identity_sha256"],
        }
    doc = {
        "kind": "authorization_registration",
        "authorization_id": AUTH_ID,
        "authorized": True,
        "status": "ACTIVE",
        "granted_by": ("用户持续执行授权（AGENTS.md §0：额度很多、修复后推进主线，"
                       "不再每48次索取口令）+ MAX-288 V2 §0/§13.4 的 16,384 新周期管理帽；"
                       "本单是 P06 阶段的有限登记，不是把阶段总帽当无身份钱包。"),
        "phase": "P06",
        "budget_ledger": LEDGER,
        "real_endpoint": {
            "api_base": "http://127.0.0.1:17770/v1",
            "model": "dsf",
            "temperature": 0.7,
            "max_attempts": 192,
        },
        "api_config": ("api_cfg:openai_compat_stream_true_v1(include_usage gated)；"
                       "三批同端点/model/温度/流式配置；不与任何旧非流式表拼接。"),
        "scope": {
            "purpose": ("P06 首轮真实新机制对照：三批两臂×4故事×8位=192 次真实生成；"
                        "不是四臂 128 随机实验，不伪报同一随机比较。"),
            "route": "three_two_arm_batches_192",
            "positions_per_arm_per_batch": 32,
            "total_attempts": 192,
            "baseline_repeat": ("同一 reference_product_v1 身份在三批各出现一次，"
                                "用于各自严格配对；A/B/C 跨批横比只作探索。"),
            "batches": list(frozen.keys()),
            "batch_ids": {k: v["batch_id"] for k, v in frozen.items()},
            "arms": ["reference_product_v1", "v5_purpose_aligned",
                     "v6_goal_context", "v6_integrated"],
            "episodes": ["E006", "E007", "E008", "E012"],
            "cards": s["cards_path"],
            "fail_fast": True,
            "no_retry": True,
            "no_makeup": True,
            "automatic_judge": False,
            "judge_separate": ("P06 质量评审另建 judge scope 的有限授权；本单只覆盖 harness 生成，"
                               "不自动触发裁判。"),
            "stream_identity_separate_from": ("AUTH-V5-CTX / AUTH-R2-CTX / AUTH-V3-DELETION / "
                                              "AUTH-NEWSTORY-96 / AUTH-STREAM-INT / AUTH-C4-SCREEN / "
                                              "AUTH-QUALITY-PANEL / AUTH-A3-JUDGE / AUTH-EVAL-JUDGE "
                                              "均 CLOSED，不复活、不复用。"),
            "perf_ledger_untouched": ("AUTH-HARNESS-PERF-PHASE1-20261025-01 used 4/256 不动；"
                                      "P06 生成另账，不拿旧性能余额抵扣。"),
            "new_cycle_cap": 16384,
            "phase_p06_harness_ceiling": 192,
            "not_a_spending_target": True,
        },
        "frozen_bindings": frozen,
        "execution_entry": "tools/run_max288_batch.py",
        "execution_entry_sha256": sha(RUNNER),
        "notes": ("旧 CLOSED 单不复活；失败消耗不退款；不自动重试/补配对；"
                  "每批先 precheck 再 capture 再 live；live 目录已存在 turn_* 即拒绝覆盖。"),
    }
    json.dump(doc, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("wrote", OUT)
    for k, v in frozen.items():
        print(" ", k, v["batch_id"], v["plan_sha256"][:16],
              v["parent_identity_sha256"][:16], v["candidate_identity_sha256"][:16])
    print("runner sha", doc["execution_entry_sha256"][:16], "ledger", LEDGER)


if __name__ == "__main__":
    main()
