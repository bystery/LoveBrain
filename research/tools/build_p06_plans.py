# -*- coding: utf-8 -*-
r"""tools/build_p06_plans.py — MAX-288 V2 P06 三个两臂配对批的冻结计划生成（零调用）。

路线（派发前登记）：4 故事×8 位×2 臂=64/批，三批共 192 次真实生成；
  A: reference_product_v1(parent) vs v5_purpose_aligned(candidate)
  B: reference_product_v1(parent) vs v6_goal_context(candidate)
  C: reference_product_v1(parent) vs v6_integrated(candidate)
三批共享同一基线身份，用于各自严格配对；A/B/C 跨批横比只作探索（不同 resident/时段）。
不是四臂 128；不伪报同一随机实验。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\build_p06_plans.py
"""
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep  # noqa: E402

OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "p06_20261027")
CARDS = "benchmark/c4_screen_20261026/GRADING_CARDS_C4_E006_E007_E008_E012_R2.json"
EPISODES = [("E006", "parent_first"), ("E007", "candidate_first"),
            ("E008", "candidate_first"), ("E012", "parent_first")]
BASE = "reference_product_v1"
BATCHES = {
    "A_BASE_VS_V5": {
        "batch_id": "BATCH-P06-A-BASE-VS-V5-20261027",
        "arm_map": {"parent": BASE, "candidate": "v5_purpose_aligned"},
        "plan_file": "P06_A_BASE_VS_V5.INTERLEAVE_PLAN.json",
    },
    "B_BASE_VS_V6G": {
        "batch_id": "BATCH-P06-B-BASE-VS-V6G-20261027",
        "arm_map": {"parent": BASE, "candidate": "v6_goal_context"},
        "plan_file": "P06_B_BASE_VS_V6G.INTERLEAVE_PLAN.json",
    },
    "C_BASE_VS_V6I": {
        "batch_id": "BATCH-P06-C-BASE-VS-V6I-20261027",
        "arm_map": {"parent": BASE, "candidate": "v6_integrated"},
        "plan_file": "P06_C_BASE_VS_V6I.INTERLEAVE_PLAN.json",
    },
}


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def main():
    os.makedirs(OUT, exist_ok=True)
    out = {"route": "three_two_arm_batches_192", "cards_path": CARDS,
           "episode_order": [{"episode": e, "arm_order": o} for e, o in EPISODES],
           "baseline": BASE, "batches": {}}
    for key, b in BATCHES.items():
        raw = {"batch_id": b["batch_id"], "arm_map": b["arm_map"],
               "episode_order": [{"episode": e, "arm_order": o} for e, o in EPISODES],
               "cards_path": CARDS, "recall_capable": False}
        cfg_path = os.path.join(OUT, b["plan_file"].replace(".INTERLEAVE_PLAN.json",
                                                           ".BATCH_CONFIG.json"))
        json.dump(raw, open(cfg_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        plan_path = os.path.join(OUT, b["plan_file"])
        cfg = ep.load_batch_config(cfg_path)
        doc = ep.emit_plan(plan_path, cfg)
        fz = doc["frozen"]["identity_closure"]
        info = {"batch_id": b["batch_id"], "plan_file": b["plan_file"],
                "plan_sha256": sha(plan_path), "attempts": doc["total_attempts"],
                "arm_map": b["arm_map"],
                "parent_identity_sha256": fz["parent"]["identity_sha256"],
                "candidate_identity_sha256": fz["candidate"]["identity_sha256"],
                "parent_closure": fz["parent"]["closure"],
                "candidate_closure": fz["candidate"]["closure"],
                "config_file": os.path.relpath(cfg_path, ROOT).replace("\\", "/"),
                "config_files_count": len(doc["frozen"]["config_files"])}
        out["batches"][key] = info
        print("%s plan=%s attempts=%d parent=%s cand=%s sha=%s" % (
            key, b["plan_file"], doc["total_attempts"],
            info["parent_identity_sha256"][:16], info["candidate_identity_sha256"][:16],
            info["plan_sha256"][:16]))
    json.dump(out, open(os.path.join(OUT, "P06_PLAN_SUMMARY.json"), "w",
                        encoding="utf-8"), ensure_ascii=False, indent=1)
    print("summary →", os.path.join(OUT, "P06_PLAN_SUMMARY.json"))


if __name__ == "__main__":
    main()
