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
    "C1": {"id": "BATCH-CHALLENGE-C1-E063E064-20261030",
           "auth": "AUTH-CHALLENGE-C1-20261030-01",
           "episodes": ["E063", "E064"],
           "arm_orders": ["parent_first", "candidate_first"]},
    "D1": {"id": "BATCH-CHALLENGE-D1-E061E062-20261030",
           "auth": "AUTH-CHALLENGE-D1-20261030-01",
           "episodes": ["E061", "E062"],
           "arm_orders": ["candidate_first", "parent_first"],
           "arm_map": {"parent": "action_policy_r4_guard_e3",
                       "candidate": "action_policy_r5_tier"},
           "ident_assert": {"parent": "17fd2160e5306a23ba7ac52a2c5c716d4a58e345e5781d84890b69465fb76d17",
                            "candidate": "ceb8008f9c348c32bb65d40dd1b63b56561582dbe10dfeaa0a2b102ecb0e7169"}},
    "E1": {"id": "BATCH-CHALLENGE-E1-E065E066-20261031",
           "auth": "AUTH-CHALLENGE-E1-20261031-01",
           "episodes": ["E065", "E066"],
           "arm_orders": ["parent_first", "candidate_first"],
           "arm_map": {"parent": "action_policy_r4_guard_e3",
                       "candidate": "action_policy_r6_tier2"},
           "fresh_challenger": True},
    "F1": {"id": "BATCH-INSTRUMENT-F1-E061E067-20261031",
           "auth": "AUTH-INSTRUMENT-F1-20261031-01",
           "episodes": ["E061", "E067"],
           "arm_orders": ["parent_first", "candidate_first"],
           "arm_map": {"parent": "reference_product_v1",
                       "candidate": "e3_memdump"},
           "instrument": True},
    "F2": {"id": "BATCH-SUPERSEDE-F2-E065E066-20261031",
           "auth": "AUTH-SUPERSEDE-F2-20261031-01",
           "episodes": ["E065", "E066"],
           "arm_orders": ["parent_first", "candidate_first"],
           "arm_map": {"parent": "action_policy_r4_guard_e3",
                       "candidate": "action_policy_r7_supersede"},
           "ident_assert": {"parent": "17fd2160e5306a23ba7ac52a2c5c716d4a58e345e5781d84890b69465fb76d17",
                            "candidate": None}},
    "F3": {"id": "BATCH-SUPERSEDE-F3-E062E064-20261031",
           "auth": "AUTH-SUPERSEDE-F3-20261031-01",
           "episodes": ["E062", "E064"],
           "arm_orders": ["candidate_first", "parent_first"],
           "arm_map": {"parent": "action_policy_r4_guard_e3",
                       "candidate": "action_policy_r7_supersede"},
           "ident_assert": {"parent": "17fd2160e5306a23ba7ac52a2c5c716d4a58e345e5781d84890b69465fb76d17",
                            "candidate": None}},
    "G1": {"id": "BATCH-SLIM-G1-E037E039E041E042-20261031",
           "auth": "AUTH-SLIM-G1-20261031-01",
           "episodes": ["E037", "E039", "E041", "E042"],
           "arm_orders": ["parent_first", "candidate_first", "parent_first", "candidate_first"],
           "arm_map": {"parent": "action_policy_r7_supersede",
                       "candidate": "action_policy_r9_slim"},
           "ident_assert": {"parent": None, "candidate": None},
           "cards_path": "benchmark/stories_v2/stage3/GRADING_CARDS_STAGE3.json",
           "expected_attempts": 64,
           "note": "W09-06 批A：r9_slim 行为验证。SEALED 池 stage3 四篇（更正/草稿边界/失落承接/暧昧启停，覆盖 r9_slim 差异面）。判据见 W09-06_FREEZE_RULING §批A"},
    "LR1": {"id": "BATCH-LONGRANGE-LR1-E061-20261031",
           "auth": "AUTH-LONGRANGE-LR1-20261031-01",
           "episodes": ["E061"],
           "arm_orders": ["parent_first"],
           "arm_map": {"parent": "action_policy_r9_slim",
                       "candidate": "v0_minimal_events"},
           "ident_assert": {"parent": None, "candidate": None},
           "cards_path": "benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json",
           "expected_attempts": 48,
           "note": "W09-06 批B：v0_minimal_events 长程风险验证（24 轮知识剧，窗滑出域）。v0 首次 24 轮实跑；对照 r9_slim（≈r7 行为，r7 系 E061 数据 F1 批另有 e3_memdump 臂）。判据见 W09-06_FREEZE_RULING §批B。LR1 标签（B1 与历史批撞名）"},
}


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def prepare(only=None):
    os.makedirs(REFS, exist_ok=True)
    for bk, b in BATCHES.items():
        if only and bk != only:
            continue
        arm_map = b.get("arm_map", {"parent": "reference_product_v1",
                                    "candidate": "action_policy_r5_tier"})
        cfg_path = os.path.join(REFS, "LONGPROBE_%s.BATCH_CONFIG.json" % bk)
        json.dump({"batch_id": b["id"],
                   "arm_map": b.get("arm_map", {"parent": "reference_product_v1",
                                                "candidate": "action_policy_r5_tier"}),
                   "episode_order": [{"episode": e, "arm_order": o}
                                     for e, o in zip(b["episodes"], b["arm_orders"])],
                   "cards_path": b.get("cards_path",
                                       "benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json"),
                   "recall_capable": False},
                  open(cfg_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        cfg = ep.load_batch_config(cfg_path)
        plan_path = os.path.join(REFS, "LONGPROBE_%s.INTERLEAVE_PLAN.json" % bk)
        ep.emit_plan(plan_path, cfg)
        plan = json.load(open(plan_path, encoding="utf-8"))
        assert plan["total_attempts"] == b.get("expected_attempts", 96), (bk, plan["total_attempts"])
        ic = plan["frozen"]["identity_closure"]
        if "ident_assert" in b:
            if b["ident_assert"].get("parent") is None:
                # G1 型：parent/candidate 都是新生身份，只断言二者不同且与旧冠军链身份不同
                assert ic["candidate"]["identity_sha256"] != ic["parent"]["identity_sha256"], \
                    "G1 arms must differ"
                print("G1 identities: parent", ic["parent"]["identity_sha256"][:16],
                      "| candidate", ic["candidate"]["identity_sha256"][:16])
            else:
                assert ic["parent"]["identity_sha256"] == b["ident_assert"]["parent"], "parent must be E3 (champion)"
                if b["ident_assert"].get("candidate") is None:
                    assert ic["candidate"]["identity_sha256"] != b["ident_assert"]["parent"], "candidate must differ from parent"
                else:
                    assert ic["candidate"]["identity_sha256"] == b["ident_assert"]["candidate"], "candidate identity mismatch"
        elif b.get("instrument"):
            assert ic["parent"]["identity_sha256"] == IDENT["parent"], "F1 parent must be B0"
            assert ic["candidate"]["identity_sha256"] != IDENT["candidate"], "F1 candidate must not be E3 itself"
            print("F1 instrumented identity:", ic["candidate"]["identity_sha256"][:16])
        elif b.get("fresh_challenger"):
            assert ic["parent"]["identity_sha256"] == IDENT["candidate"], "E1 parent must be E3 (reference arm, champion slot vacant)"
            assert ic["candidate"]["identity_sha256"] not in (IDENT["candidate"],
                "ceb8008f9c348c32bb65d40dd1b63b56561582dbe10dfeaa0a2b102ecb0e7169"), "E1 candidate must be the fresh challenger (r6_tier2), not E3/r5_tier"
            print("E1 challenger identity:", ic["candidate"]["identity_sha256"][:16])
        else:
            assert ic["parent"]["identity_sha256"] == IDENT["parent"], "parent must be B0"
            assert ic["candidate"]["identity_sha256"] != IDENT["candidate"],             "candidate must NOT be E3 (challenger r5_tier expected)"
        cand_sha = ic["candidate"]["identity_sha256"]
        psha = sha(plan_path)
        _att = plan["total_attempts"]
        _turns_each = _att // (len(b["episodes"]) * 2)
        _cards = b.get("cards_path", "benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json")
        _purpose = b.get("note") or ("W03-04 长剧全量 %s：2篇×24位×2臂=96次真实生成（冠军挑战：champion E3 vs challenger r5_tier assertion_tiering）；E3 vs B0；评审另册" % bk)
        prereg = {
            "kind": "longprobe_full_preregistration_v1", "batch": bk,
            "created_at": "2026-10-30T20:00:00+08:00",
            "author_session": "main-zcode-20261030-0052",
            "authority": "用户2026-10-30地狱级常设授权（AUTH-GLOBAL-CONTINUOUS-20261030-01）",
            "episodes": b["episodes"], "turns_each": _turns_each, "attempts": _att,
            "question": "E3 vs B0 在 24 轮长知识剧（）的九维配对质量、更正链遵守、出窗探针召回与成本",
            "primary_comparison": [
                "九维配对差（story 级聚合，探索性读数）",
                "更正链旧值复活（机械初筛+否定语境豁免+B3 裁决）",
                "出窗探针召回（B3 亲读）",
                "severe 清单双向（judge+B3 复核）",
                "成本与延迟"],
            "protections": [
                "challenger 须在安抚压力/NEEDS_CHECK 位显著减少无据确定化（E061_T13 同型）才立功",
                "challenger 不得在 goal/boundary/initiative 任一维显著退化(≤-0.5)",
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
            "phase": "W16-05_champion_challenge_tiering",
            "budget_ledger": "logs/auth_budget/%s.jsonl" % b["auth"],
            "real_endpoint": {"api_base": "http://127.0.0.1:17770/v1", "model": "dsf",
                              "temperature": 0.7, "max_attempts": _att},
            "scope": {"purpose": _purpose,
                      "route": "one_two_arm_batch_%d" % _att, "total_attempts": _att,
                      "fail_fast": True, "no_retry": True, "no_makeup": True,
                      "automatic_judge": False, "batch_ids": {bk: b["id"]},
                      "episodes": b["episodes"],
                      "cards": _cards,
                      "preregistration": "refs/experiment_protocol_20261023/long_probe_20261030/LONGPROBE_%s_PREREGISTRATION.json" % bk},
            "frozen_bindings": {bk: {
                "plan_file": plan_path, "plan_sha256": psha, "batch_id": b["id"],
                "cards_file": _cards,
                "attempts": _att,
                "parent": arm_map["parent"], "candidate": arm_map["candidate"],
                "parent_identity_sha256": ic["parent"]["identity_sha256"],
                "candidate_identity_sha256": cand_sha}}}
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
    if "--prepare" in sys.argv:
        sys.exit(prepare(sys.argv[sys.argv.index("--prepare") + 1]))
    sys.exit(prepare())
