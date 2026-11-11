# -*- coding: utf-8 -*-
"""tools/dispatch_longprobe.py — W03-04 长剧探针批（E3 vs B0，2篇×24位×2臂=96）。

复用 exam_interleave_plan 正式链：配置→emit_plan（含冻结身份闭包）→预注册→授权→派发。
先 --prepare 落盘全部文件，--run 才发起真实调用（fail-fast，零重试）。
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
AID = "AUTH-LONGPROBE-20261030-01"
BATCH_ID = "BATCH-LONGPROBE-E3VSREF-20261030"
IDENT = {"parent": "501bc426977b2e9bcee3a6d83b917bb670642f3d9d4c2e79154634f64cd65b67",
         "candidate": "17fd2160e5306a23ba7ac52a2c5c716d4a58e345e5781d84890b69465fb76d17"}


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def prepare():
    os.makedirs(REFS, exist_ok=True)
    cfg_path = os.path.join(REFS, "LONGPROBE.BATCH_CONFIG.json")
    json.dump({"batch_id": BATCH_ID,
               "arm_map": {"parent": "reference_product_v1",
                           "candidate": "action_policy_r4_guard_e3"},
               "episode_order": [{"episode": "E061", "arm_order": "parent_first"},
                                 {"episode": "E062", "arm_order": "candidate_first"}],
               "cards_path": "benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json",
               "recall_capable": False},
              open(cfg_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    cfg = ep.load_batch_config(cfg_path)
    plan_path = os.path.join(REFS, "LONGPROBE.INTERLEAVE_PLAN.json")
    ep.emit_plan(plan_path, cfg)
    plan = json.load(open(plan_path, encoding="utf-8"))
    assert plan["total_attempts"] == 96, plan["total_attempts"]
    ic = plan["frozen"]["identity_closure"]
    for arm in ("parent", "candidate"):
        assert ic[arm]["identity_sha256"] == IDENT[arm], \
            "%s identity drift: %s" % (arm, ic[arm]["identity_sha256"][:16])
    # 预注册（先于任何真实调用）
    prereg = {
        "kind": "long_probe_preregistration_v1", "task": "W03-04 probe",
        "created_at": "2026-10-30T15:20:00+08:00", "author_session": "main-zcode-20261030-0052",
        "authority": "用户2026-10-30地狱级常设授权（AUTH-GLOBAL-CONTINUOUS-20261030-01）",
        "stories": [
            {"id": "E061", "title": "甲状腺随访三个月", "turns": 24,
             "keep_atoms": 19, "updates": 4,
             "probes": [{"turn": "E061_T20", "recall": "抽血空腹要求(T03)"},
                        {"turn": "E061_T22", "recall": "不忌碘口径(T17)"},
                        {"turn": "E061_T23", "recall": "8月6日3个月间隔(T15)"},
                        {"turn": "E061_T24", "recall": "生日晚餐安排(T18)"}]},
            {"id": "E062", "title": "一支镜头的转手全流程", "turns": 24,
             "keep_atoms": 20, "updates": 3,
             "probes": [{"turn": "E062_T19", "recall": "参数转述不走样(f4/转接环)(T03/T04)"},
                        {"turn": "E062_T24", "recall": "快门数8200(T05，此后17轮未重现)"}]}],
        "primary_comparison": [
            "出窗探针召回率（探针位要求原子的正确使用率，双臂对照）",
            "更正链遵守度（更正后旧值复活数：E061 旧日期5月20/半年口径；E062 f2.8/周六/4600/4300单列）",
            "九维质量与 severe（沿用 W04-01 锚点；judge PARTIAL_QUALIFIED：fact/time 以 B3 亲读为准）",
            "每 episode 成本与 p50/p95 延迟"],
        "protections": [
            "E3 不得在 goal/boundary/initiative 任一维显著退化(≤-0.5)而仅靠均分晋级",
            "旧值复活逐例登记，不算 judge 误杀",
            "程序分只证契约；语义失败/严重污染/成本显著上升任一即阻断",
            "故事级=探索性读数（n=2）"],
        "judge_budget": "评审另册，生成收口后按探针+更新链位点计",
        "frozen_plan_sha256": sha(plan_path)}
    json.dump(prereg, open(os.path.join(REFS, "LONGPROBE_PREREGISTRATION.json"), "w",
                           encoding="utf-8"), ensure_ascii=False, indent=1)
    auth = {
        "kind": "authorization_registration", "authorization_id": AID,
        "authorized": True, "status": "ACTIVE",
        "granted_by": "用户2026-10-30地狱级常设授权（AUTH-GLOBAL-CONTINUOUS-20261030-01 登记件）；管理帽16384不变",
        "phase": "W03-04_long_drama_probe",
        "budget_ledger": "logs/auth_budget/%s.jsonl" % AID,
        "real_endpoint": {"api_base": "http://127.0.0.1:17770/v1", "model": "dsf",
                          "temperature": 0.7, "max_attempts": 96},
        "scope": {"purpose": "W03-04 长剧探针：2篇×24位×2臂=96次真实生成；E3 vs B0；出窗探针召回+更正链遵守为主比较；评审另册",
                  "route": "one_two_arm_batch_96", "total_attempts": 96,
                  "fail_fast": True, "no_retry": True, "no_makeup": True,
                  "automatic_judge": False, "batch_ids": {"LONGPROBE_A": BATCH_ID},
                  "episodes": ["E061", "E062"],
                  "cards": "benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json",
                  "preregistration": "refs/experiment_protocol_20261023/long_probe_20261030/LONGPROBE_PREREGISTRATION.json"},
        "frozen_bindings": {"LONGPROBE_A": {
            "plan_file": plan_path, "plan_sha256": sha(plan_path),
            "parent_identity_sha256": IDENT["parent"],
            "candidate_identity_sha256": IDENT["candidate"]}}}
    json.dump(auth, open(os.path.join(REFS, "AUTHORIZATION_LONGPROBE.json"), "w",
                         encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(os.path.join(REFS, "AUTHORIZATION_LONGPROBE.json"), encoding="utf-8"))
    print("plan sha:", sha(plan_path)[:16], "| attempts:", plan["total_attempts"])
    print("prepared:", REFS)
    return 0


def run():
    plan = os.path.join(REFS, "LONGPROBE.INTERLEAVE_PLAN.json")
    auth = os.path.join(REFS, "AUTHORIZATION_LONGPROBE.json")
    out = os.path.join(ROOT, "logs", "longprobe_a_live_20261030")
    import subprocess
    cmd = [sys.executable, os.path.join(ROOT, "tools", "run_max288_batch.py"),
           "--plan", plan, "--authorization", auth, "--mode", "live", "--out", out]
    print("dispatch:", " ".join(cmd))
    return subprocess.call(cmd)


if __name__ == "__main__":
    if "--run" in sys.argv:
        sys.exit(run())
    sys.exit(prepare())
