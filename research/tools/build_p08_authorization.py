# -*- coding: utf-8 -*-
r"""tools/build_p08_authorization.py — P08（截止日主轨）逐阶段有限授权登记（零调用）。

目的分账：S1 固定故事两臂对照、S2 反馈锚点两臂对照各建一份授权与账本，
互不挪用额度；S3 父子归因、D 新材料复验各自再单独登记。
不复活任何 CLOSED 单，不新增全局管理帽（16,384 仍为本周期管理上限，非支出目标）。

哈希一律机器现算（计划文件、两臂身份、题面/卡/执行器闭包、执行入口自身），
不手填、不留 null：run_max288_batch.py 的 G1/G2/G4/G7 守卫会现场重算比对。

用法：
  set PYTHONIOENCODING=utf-8
  D:\anaconda\python.exe tools\build_p08_authorization.py --stage S1 \
      --plan refs/experiment_protocol_20261023/s1_action_live_20261028/S1_ACTION.INTERLEAVE_PLAN.json
"""
import argparse
import datetime
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "s1_action_live_20261028")
RUNNER = os.path.join(ROOT, "tools", "run_max288_batch.py")
ENDPOINT = "http://127.0.0.1:17770/v1"
MODEL = "dsf"
TEMPERATURE = 0.7

PURPOSE = {
    "S1": ("固定连续故事两臂真实对照：reference_product_v1 vs action_policy_r1，"
           "4 故事(E024/E027/E031/E034)×12 位×2 臂=96 次真实生成。测五项目标行动/合同"
           "修订是否带来真实质量收益（目标让位与推进、接钩子、失败修复、无据编造、"
           "合法不回）。不是资格探针，不用于裁判调用。"),
    "S2": ("目标反馈锚点两臂真实对照：12 个独立锚点×2 臂=24 次真实生成。每个锚点都是"
           "当前目标真实生效/调整/让位/关闭的机会，正向试探受保护、明确拒绝不硬推。"
           "单列统计，不并进 S1 故事均分。"),
    "S2R": ("故障族修复后真实复验（截止执行单 §C）：同一 12 锚点×2 臂=24 次新生成。"
            "candidate=action_policy_r2（新身份，含 FIX-6 不回表达/FIX-7 单 JSON 硬约束/"
            "FIX-8 回扣有据）；parent=reference_product_v1 在本批同条件重跑，保证配对不是"
            "跨批拼接。只回答两件事：(1) 已定位故障族（AK02 提前闭合、AK09/AK12 不回无法表达、"
            "键名写坏、directions 超槽）是否消失；(2) 9 个非收尾位点是否因'允许不回'而退化"
            "（积极互动不得被保守化换掉）。S2 的已付费答卷只作修复前对照分列，不套用为本批成绩。"),
    "S3": ("父子归因：v6_integrated vs action_policy_r1 在同 12 锚点上的新增 24 次生成，"
           "用来区分『五修订的增量』与『v6 本身相对原版的增量』。action 侧 12 次是新生成、"
           "重新计费，不假装复用 S2 答卷。"),
    "D1": ("候选冻结后的新材料前瞻复验：2 新故事×8 位×2 臂=32 次真实生成；出题方看不到"
           "候选资产与故障原句，审题卡在运行前冻结。只能标注为前瞻/候选盲化，不声称独立封存。"),
    "R3A": ("过度沉默修复（FIX-9）的同一计划内修复前后对照：parent=action_policy_r2（修复前）、"
            "candidate=action_policy_r3（新身份）。D01/D02 全 16 位 + D03 定向复验 6 位 + "
            "AK09/AK12 合法不回对照 2 位 = 24 位×2 臂=48 次真实生成。只回答三件事："
            "(1) 她报成果/问主意/受了委屈的格子是否不再不回；(2) 她明确'别等/结果出来我再说'"
            "的格子是否仍允许不回；(3) 其余位点是否因收窄而不回语义被破坏、或退化。"
            "父臂不是复用 D1 旧答卷，而是本计划内新生成，确保同条件配对。"),
    "R3B": ("D03 定向复验材料上的两臂真实对照：reference_product_v1 vs action_policy_r3，"
            "6 位×2 臂=12 次新生成。只回答一件事：在新题面上，r3 与忠实原版谁更符合卡面"
            "（含 3 个必须接住格与 2 个可以不回格）。D03 由实施 worker 撰写、作者知道答案，"
            "只能证伪'FIX-9 只在原三格成立'，不构成泛化样本。"),
}


def sha(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--stage", required=True, choices=sorted(PURPOSE))
    ap.add_argument("--plan", required=True)
    ap.add_argument("--seq", default="01")
    a = ap.parse_args(argv)

    plan_path = os.path.abspath(a.plan)
    plan = json.load(open(plan_path, encoding="utf-8"))
    attempts = int(plan["total_attempts"])
    batch_id = plan["batch"]["batch_id"]
    aid = "AUTH-P08-%s-20261028-%s" % (a.stage, a.seq)
    rel_plan = os.path.relpath(plan_path, ROOT).replace("\\", "/")
    fz = plan["frozen"]["identity_closure"]

    doc = {
        "kind": "authorization_registration",
        "authorization_id": aid,
        "authorized": True,
        "status": "ACTIVE",
        "granted_by": ("用户持续执行授权（AGENTS.md §0：修复后推进主线，不每 48 次索取口令）"
                       "＋ MAX-288 本周期 16,384 管理帽；本单是截止日主轨的有限目的登记，"
                       "不是新增总帽，也不是支出目标。"),
        "phase": "P08-%s" % a.stage,
        "budget_ledger": "logs/auth_budget/%s.jsonl" % aid,
        "real_endpoint": {"api_base": ENDPOINT, "model": MODEL,
                          "temperature": TEMPERATURE, "max_attempts": attempts},
        "api_config": ("api_cfg:openai_compat_stream_true_v1(include_usage gated)；"
                       "本批两臂同端点/model/温度/流式配置；不与任何旧非流式表拼接。"),
        "scope": {
            "purpose": PURPOSE[a.stage],
            "stage": a.stage,
            "experiment": "P08_harness_action_policy_real_compare",
            "allowed_models": [MODEL],
            "forbidden_models": ["gemini-3.8-flash", "hy4-preview"],
            "endpoint": ENDPOINT,
            "positions_per_arm": attempts // 2,
            "total_attempts": attempts,
            "batch_ids": [batch_id],
            "episodes": [e["episode"] for e in plan["episode_order"]],
            "episode_turns": plan["batch"].get("episode_turns"),
            "cards": plan["batch"]["cards_path"],
            "arms": [plan["arm_mapping"]["parent"], plan["arm_mapping"]["candidate"]],
            "fail_fast": True,
            "no_retry": True,
            "no_makeup": True,
            "automatic_judge": False,
            "judge_separate": ("质量评审另建 judge scope；本单只覆盖 harness 生成，"
                               "不自动触发裁判调用。"),
            "closed_history_not_reused": ("AUTH-P06-*、AUTH-P07-V2/W09/W03VAL、"
                                          "AUTH-EXAM1/AUTH-V5/AUTH-R2/AUTH-C4 等旧单不复活、"
                                          "不挪用、不抵扣。"),
            "new_cycle_cap": 16384,
            "phase_cap": attempts,
            "not_a_spending_target": True,
        },
        "frozen_bindings": {
            a.stage: {
                "plan_file": rel_plan,
                "plan_sha256": sha(plan_path),
                "batch_id": batch_id,
                "cards_file": plan["batch"]["cards_path"],
                "cards_sha256": plan["frozen"]["config_files"].get(
                    plan["batch"]["cards_path"]),
                "attempts": attempts,
                "parent": plan["arm_mapping"]["parent"],
                "candidate": plan["arm_mapping"]["candidate"],
                "parent_identity_sha256": fz["parent"]["identity_sha256"],
                "candidate_identity_sha256": fz["candidate"]["identity_sha256"],
            }
        },
        "execution_entry": "tools/run_max288_batch.py",
        "execution_entry_sha256": sha(RUNNER),
        "notes": ("一物理请求一 attempt、事前占额，失败/空返/超时照样记账；"
                  "不补配对、不改 nonce 重跑；live 输出目录已有 turn_* 即拒绝覆盖；"
                  "先 precheck 后 capture 后 live。"),
        "registered_at": datetime.datetime.now().isoformat(timespec="seconds"),
    }
    os.makedirs(DIR, exist_ok=True)
    out = os.path.join(DIR, "AUTHORIZATION_P08_%s.json" % a.stage)
    json.dump(doc, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("wrote", os.path.relpath(out, ROOT).replace("\\", "/"))
    print("  ledger=%s cap=%d plan_sha=%s" % (doc["budget_ledger"], attempts,
                                              doc["frozen_bindings"][a.stage]
                                              ["plan_sha256"][:16]))
    print("  parent=%s %s" % (plan["arm_mapping"]["parent"],
                              fz["parent"]["identity_sha256"][:16]))
    print("  candidate=%s %s" % (plan["arm_mapping"]["candidate"],
                                 fz["candidate"]["identity_sha256"][:16]))
    print("  runner_sha=%s" % doc["execution_entry_sha256"][:16])
    return 0


if __name__ == "__main__":
    sys.exit(main())
