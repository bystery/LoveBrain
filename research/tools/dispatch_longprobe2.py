# -*- coding: utf-8 -*-
"""tools/dispatch_longprobe2.py — W16-05 #9 长剧全量生成批（E063–E068，288 位）。

E3 vs B0（冻结身份不变，公平对照）；两批拆分：B1=E063/E064/E065（144 位），
B2=E066/E067/E068（144 位）——单批 288 超出此前批次惯例且故障半径大，拆两批各自 scope。
先 --prepare 落盘配置/计划/预注册/授权；--run B1 / --run B2 派发。fail_fast 零重试。
"""
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep  # noqa: E402

REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030")
IDENT = {"parent": "501bc426977b2e9bcee3a6d83b917bb670642f3d9d4c2e79154634f64cd65b67",
         "candidate": "17fd2160e5306a23ba7ac52a2c5c716d4a58e345e5781d84890b69465fb76d17"}
BATCHES = {
    "B1": {"id": "BATCH-LONGPROBE-B1-E063E065-20261030",
           "auth": "AUTH-LONGPROBE-B1-20261030-01",
           "episodes": ["E063", "E064", "E065"],
           "arm_orders": ["parent_first", "candidate_first", "parent_first"]},
    "B2": {"id": "BATCH-LONGPROBE-B2-E066E068-20261030",
           "auth": "AUTH-LONGPROBE-B2-20261030-01",
           "episodes": ["E066", "E067", "E068"],
           "arm_orders": ["candidate_first", "parent_first", "candidate_first"]},
}


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def prepare():
    os.makedirs(REFS, exist_ok=True)
    for bk, b in BATCHES.items():
        cfg_path = os.path.join(REFS, "LONGPROBE_%s.BATCH_CONFIG.json" % bk)
        json.dump({"batch_id": b["id"],
                   "arm_map": {"parent": "reference_product_v1",
                               "candidate": "action_policy_r4_guard_e3"},
                   "episode_order": [{"episode": e, "arm_order": o}
                                     for e, o in zip(b["episodes"], b["arm_orders"])],
                   "cards_path": "benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json",
                   "recall_capable": False},
                  open(cfg_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        cfg = ep.load_batch_config(cfg_path)
        plan_path = os.path.join(REFS, "LONGPROBE_%s.INTERLEAVE_PLAN.json" % bk)
        ep.emit_plan(plan_path, cfg)
        plan = json.load(open(plan_path, encoding="utf-8"))
        assert plan["total_attempts"] == 144, (bk, plan["total_attempts"])
        ic = plan["frozen"]["identity_closure"]
        for arm in ("parent", "candidate"):
            assert ic[arm]["identity_sha256"] == IDENT[arm], "%s identity drift" % arm
        psha = sha(plan_path)
        prereg = {
            "kind": "longprobe_full_preregistration_v1", "batch": bk,
            "created_at": "2026-10-30T20:00:00+08:00",
            "author_session": "main-zcode-20261030-0052",
            "authority": "用户2026-10-30地狱级常设授权（AUTH-GLOBAL-CONTINUOUS-20261030-01）",
            "episodes": b["episodes"], "turns_each": 24, "attempts": 144,
            "question": "E3 vs B0 在 24 轮长知识剧（E063-E068）的九维配对质量、更正链遵守、出窗探针召回与成本",
            "primary_comparison": [
                "九维配对差（story 级聚合，探索性读数）",
                "更正链旧值复活（机械初筛+否定语境豁免+B3 裁决）",
                "出窗探针召回（B3 亲读）",
                "severe 清单双向（judge+B3 复核）",
                "成本与延迟"],
            "protections": [
                "E3 不得在 goal/boundary/initiative 任一维显著退化(≤-0.5)而仅靠均分晋级",
                "severe 双臂同位无差异不阻断；显著增多=阻断",
                "安抚压力位的无据确定化双杀族逐位登记（E061_T13 先例）",
                "素材缺陷按 E-MAT 单列不计胜负"],
            "judge_budget": "评审另册（48主判+8双序×2批≈112），生成收口后登记",
            "frozen_plan_sha256": psha}
        json.dump(prereg, open(os.path.join(REFS, "LONGPROBE_%s_PREREGISTRATION.json" % bk),
                               "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        auth = {
            "kind": "authorization_registration", "authorization_id": b["auth"],
            "authorized": True, "status": "ACTIVE",
            "granted_by": "用户2026-10-30地狱级常设授权（AUTH-GLOBAL-CONTINUOUS-20261030-01 登记件）；管理帽16384不变",
            "phase": "W03-04_long_drama_full",
            "budget_ledger": "logs/auth_budget/%s.jsonl" % b["auth"],
            "real_endpoint": {"api_base": "http://127.0.0.1:17770/v1", "model": "dsf",
                              "temperature": 0.7, "max_attempts": 144},
            "scope": {"purpose": "W03-04 长剧全量 %s：3篇×24位×2臂=144次真实生成；E3 vs B0；评审另册" % bk,
                      "route": "one_two_arm_batch_144", "total_attempts": 144,
                      "fail_fast": True, "no_retry": True, "no_makeup": True,
                      "automatic_judge": False, "batch_ids": {bk: b["id"]},
                      "episodes": b["episodes"],
                      "cards": "benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json",
                      "preregistration": "refs/experiment_protocol_20261023/long_probe_20261030/LONGPROBE_%s_PREREGISTRATION.json" % bk},
            "frozen_bindings": {bk: {
                "plan_file": plan_path, "plan_sha256": psha, "batch_id": b["id"],
                "cards_file": "benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json",
                "attempts": 144,
                "parent": "reference_product_v1", "candidate": "action_policy_r4_guard_e3",
                "parent_identity_sha256": IDENT["parent"],
                "candidate_identity_sha256": IDENT["candidate"]}}}
        json.dump(auth, open(os.path.join(REFS, "AUTHORIZATION_%s.json" % bk), "w",
                             encoding="utf-8"), ensure_ascii=False, indent=1)
        json.load(open(os.path.join(REFS, "AUTHORIZATION_%s.json" % bk), encoding="utf-8"))
        print(bk, "prepared | plan sha", psha[:16], "| attempts", plan["total_attempts"])
    return 0


def run(bk):
    b = BATCHES[bk]
    plan = os.path.join(REFS, "LONGPROBE_%s.INTERLEAVE_PLAN.json" % bk)
    auth = os.path.join(REFS, "AUTHORIZATION_%s.json" % bk)
    out = os.path.join(ROOT, "logs", "longprobe_%s_live_20261030" % bk.lower())
    import subprocess
    cmd = [sys.executable, os.path.join(ROOT, "tools", "run_max288_batch.py"),
           "--plan", plan, "--authorization", auth, "--mode", "live", "--out", out]
    print("dispatch:", " ".join(cmd))
    return subprocess.call(cmd)


if __name__ == "__main__":
    if "--run" in sys.argv:
        sys.exit(run(sys.argv[sys.argv.index("--run") + 1]))
    sys.exit(prepare())
