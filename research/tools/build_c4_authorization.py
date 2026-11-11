# -*- coding: utf-8 -*-
"""tools/build_c4_authorization.py — 生成 C4 机制筛选授权 V2（机器填哈希，不手抄）。

为什么另起 V2 而不改旧单：旧 `AUTHORIZATION.json`（侧线 07:51 登记）从未被消费（无账本、零调用），
但它的 `real_endpoint` 写成字符串，`validate_authorization_doc` 要求对象——照原样派发会在
零派发处被拒；且它登记的「v5 为锚、原版臂缺席」公式偏差其实不必要：执行书 §4 原式
「4 臂×4 故事×8 位=128」用两个互不重叠的两臂批即可精确凑出（A=原版 vs C2，B=v5 vs C3，
四臂在同一 32 位点各跑一次）。旧单原样保留作历史，本脚本产出 AUTHORIZATION_V2.json 为权威。

脚本做三件事并在任何不一致时拒绝出文件：
  1) 逐批校验计划自身冻结身份 vs 当下重算（verify_frozen_identity，含新的缺失冻结硬闸）；
  2) 计算计划文件 sha256 + 四臂目录文件哈希集，写入 frozen_bindings / arm_identities_frozen；
  3) 额度对账：两批 attempts 之和必须恰等于 max_attempts（不超发、不为跑满放行）。
真实模型调用 = 0。
"""
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep                                    # noqa: E402

C4 = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "c4_screen_20261026")
BATCHES = {
    "sweep_a_orig_vs_goal": "sweep_a_orig_vs_goal.INTERLEAVE_PLAN.json",
    "sweep_b_v5_vs_mem": "sweep_b_v5_vs_mem.INTERLEAVE_PLAN.json",
}
ARMS = ("reference_product_v1", "v5_purpose_aligned", "goal_action_v1", "evidence_memory_v1")
OUT = os.path.join(C4, "AUTHORIZATION_V2.json")
LEDGER = "logs/auth_budget/AUTH-C4-SCREEN-20261026-01.jsonl"


def sha_file(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def sha_dir(d):
    out = {}
    for root, _ds, files in os.walk(d):
        for f in files:
            if f.endswith(".pyc"):
                continue
            p = os.path.join(root, f)
            rel = "candidates/%s/%s" % (os.path.basename(d),
                                        os.path.relpath(p, d).replace("\\", "/"))
            out[rel] = sha_file(p)
    return out


def main():
    errs, bindings, arm_sets = [], {}, {}
    total = 0
    for key, fname in BATCHES.items():
        path = os.path.join(C4, fname)
        plan = json.load(open(path, encoding="utf-8"))
        try:
            ep.verify_frozen_identity(plan)                   # 含 missing_files 硬闸
        except ep.PlanError as e:
            errs.append("%s 计划冻结校验失败：%s" % (key, str(e)[:200]))
            continue
        fz = plan["frozen"]["identity_closure"]
        n = plan["total_attempts"]
        total += n
        bindings[key] = {
            "plan_file": "refs/experiment_protocol_20261023/c4_screen_20261026/%s" % fname,
            "plan_sha256": sha_file(path),
            "batch_id": plan["batch"]["batch_id"],
            "cards_file": plan["batch"]["cards_path"],
            "attempts": n,
            "parent": plan["arm_mapping"]["parent"],
            "candidate": plan["arm_mapping"]["candidate"],
            "parent_identity_sha256": fz["parent"]["identity_sha256"],
            "candidate_identity_sha256": fz["candidate"]["identity_sha256"],
            "api_config_identity": plan["frozen"]["config_files"].get(
                "arena/api_candidate.py"),
        }
        # 计划声明的臂必须是本批四臂之一，且目录现状必须能重算出同一身份
        for arm in ("parent", "candidate"):
            name = plan["arm_mapping"][arm]
            if name not in ARMS:
                errs.append("%s %s 臂 %s 不在四臂范围" % (key, arm, name))

    for cand in ARMS:
        d = os.path.join(ROOT, "candidates", cand)
        if not os.path.isdir(d):
            errs.append("臂目录缺失：%s" % cand)
            continue
        arm_sets[cand] = sorted(sha_dir(d).values())

    if total != 128:
        errs.append("两批 attempts 之和=%s，必须恰为阶段帽 128" % total)

    if errs:
        print("C4 授权 V2 生成失败：")
        for m in errs:
            print("  -", m)
        sys.exit(1)

    doc = {
        "kind": "authorization_registration",
        "authorization_id": "AUTH-C4-SCREEN-20261026-01",
        "authorized": True,
        "status": "ACTIVE",
        "supersedes": {
            "file": "refs/experiment_protocol_20261023/c4_screen_20261026/AUTHORIZATION.json",
            "why": ("旧单从未消费（无账本、零真实调用）且 real_endpoint 为字符串，"
                    "validate_authorization_doc 要求 {api_base,model,temperature,max_attempts} 对象；"
                    "其登记的『v5 为锚、原版臂缺席』公式偏差已由两批互不重叠臂形消除。"
                    "旧文件原样保留作历史，不再作派发依据。"),
        },
        "granted_by": ("持续执行授权（AGENTS.md §0 用户 2026-10-25 原话）+ "
                       "WORKER_MARATHON_EXECUTION.md §0 阶段帽『机制筛选 128』；"
                       "本文件是对既有授权的落地登记，不是新索取"),
        "budget_ledger": LEDGER,
        "real_endpoint": {
            "api_base": "http://127.0.0.1:17770/v1",
            "model": "dsf",
            "temperature": 0.7,
            "max_attempts": 128,
        },
        "api_config": ("api_cfg:openai_compat_stream_true_v1(include_usage gated)——与 96 批/D5 "
                       "同一共同流式身份；数字不与历史非流式批合并"),
        "scope": {
            "purpose": ("C4 机制筛选：四臂（原版/v5/目标动作候选/证据记忆候选）在 4 条开发故事"
                        "×8 位的同一 32 个位点上各跑一次，共 128 次；与执行书 §4 公式逐字一致，"
                        "无臂缺席、无偏差"),
            "stage_cap_reference": "WORKER_MARATHON_EXECUTION.md §0 表『机制筛选 128』",
            "arms": list(ARMS),
            "episodes": ["E006", "E007", "E008", "E012"],
            "positions_per_arm": 32,
            "batches": list(BATCHES.keys()),
            "arm_pairing": {
                "sweep_a_orig_vs_goal": "reference_product_v1(parent) vs goal_action_v1(candidate)",
                "sweep_b_v5_vs_mem": "v5_purpose_aligned(parent) vs evidence_memory_v1(candidate)",
            },
            "comparability_note": ("每臂状态只由自身历史演进、两批不共享 resident，故位点级四臂可比；"
                                   "批内交错为逐 slot parent_first/candidate_first 交替（E006/E012 父先，"
                                   "E008/E007 候选先）"),
            "cards": ("benchmark/c4_screen_20261026/GRADING_CARDS_C4_E006_E007_E008_E012_R2.json"
                      "（32 张新建合并卡：03/04 选取 + 05 r2 覆盖 E007_T03/E008_T03；"
                      "逐字回核与未改写断言见 .PROVENANCE.json；卡 split=dev_exposed、"
                      "review_status 原样，不冒充人工认证）"),
            "fail_fast": True,
            "no_retry": True,
            "no_makeup": True,
            "third_arm": False,
            "automatic_judge": False,
            "interleaving": "逐 slot 交错、两臂独立 resident 状态、整批预检后原子占额",
            "stream_identity_separate_from": ("AUTH-V5/AUTH-R2/AUTH-V3/AUTH-NEWSTORY-96/"
                                              "AUTH-STREAM-INT/AUTH-EVAL-JUDGE/AUTH-A3-JUDGE "
                                              "均已 CLOSED，不得复用额度或冒充同一配置身份"),
            "perf_ledger_untouched": ("AUTH-HARNESS-PERF-PHASE1-20261025-01（used 4/256）本批不写入；"
                                      "考生运行桶与评估桶分账"),
        },
        "frozen_bindings": bindings,
        "arm_identities_frozen": arm_sets,
        "identity_freeze_note": ("重冻于派发前（本脚本产出）：臂源码在侧线 07:5x 之后未再变动；"
                                 "四臂目录现状哈希集与两批计划的 harness 身份由脚本重算而非手抄"),
        "approval_basis": ("M0 收口完成（STATE_MAP 06:41/06:50 段 + "
                           "refs/experiment_protocol_20261023/mainline_review_20261026/REVIEW.md）；"
                           "C2/C3 候选已实现（candidates/goal_action_v1、candidates/evidence_memory_v1）；"
                           "capture 预检两批 64/64 零网络通过（logs/c4_prep_a_20261026、"
                           "logs/c4_prep_b_20261026）"),
        "real_model_calls_before_this": 0,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print(json.dumps({
        "out": os.path.relpath(OUT, ROOT).replace("\\", "/"),
        "sha256": sha_file(OUT),
        "max_attempts": 128,
        "batches": {k: v["attempts"] for k, v in bindings.items()},
        "plan_shas": {k: v["plan_sha256"][:16] for k, v in bindings.items()},
        "arm_file_counts": {k: len(v) for k, v in arm_sets.items()},
        "real_model_calls": 0,
        "result": "PASS"}, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
