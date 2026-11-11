# -*- coding: utf-8 -*-
"""tools/verify_repro_package.py — W15-04 最小可跑包核验（清单存在性 + hash 清单）。

最小可跑集 = 引擎四件 + 候选链 + 基线 + 素材 + 派发器。
产出：refs/experiment_protocol_20261023/global_harness_program_20261030/W15-04_REPRO_MANIFEST.json
"""
import hashlib
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROG = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030")

FILES = {
    "engine": ["arena/lb_runner.py", "arena/lb_gateway.py", "arena/lb_assembly.py",
               "arena/lb_mechanism.py", "arena/api_candidate.py", "arena/lb_stream_timing.py"],
    "dispatch": ["tools/run_max288_batch.py", "tools/dispatch_challenge_tier.py",
                 "tools/story_custody.py"],
    "champion_chain": [
        "candidates/action_policy_r9_slim/params.json", "candidates/action_policy_r9_slim/mechanism/mechanism.py",
        "candidates/action_policy_r9_slim/assets/system_prompt/core.md",
        "candidates/action_policy_r9_slim/assets/system_prompt/format.md",
        "candidates/action_policy_r9_slim/assets/system_prompt/redline.md",
        "candidates/action_policy_r9_slim/assets/system_prompt/naturalness_check.md",
        "candidates/action_policy_r7_supersede/mechanism/mechanism.py",
        "candidates/action_policy_r7_supersede/params.json",
        "candidates/action_policy_r2/mechanism/mechanism.py",
        "candidates/action_policy_r1/mechanism/mechanism.py",
        "candidates/v6_goal_context/mechanism/mechanism.py",
        "candidates/v5_purpose_aligned/mechanism/mechanism.py"],
    "baseline": ["candidates/reference_product_v1/params.json"],
    "materials": [
        "benchmark/stories_v2/stage2/STAGE2_MANIFEST.json",
        "benchmark/stories_v2/stage2/E021/episode.json",
        "benchmark/stories_v2/stage2/E021/reference.json",
        "benchmark/stories_v2/stage3/STAGE3_MANIFEST.json",
        "benchmark/stories_v2/stage3/GRADING_CARDS_STAGE3.json",
        "benchmark/stories_v2/stage4/STAGE4_MANIFEST.json",
        "benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json",
        "benchmark/stories_v2/stage4_src/E061_thyroid_followup.json"],
    "custody": ["refs/experiment_protocol_20261023/global_harness_program_20261030/STORY_CUSTODY_LEDGER.json"],
}

REPRO_COMMANDS = [
    "# 1. 探网关", "curl -s http://127.0.0.1:17770/v1/models",
    "# 2. 派发两臂批（示例：G1 冻结计划）",
    "python tools/dispatch_challenge_tier.py --prepare G1",
    "python tools/dispatch_challenge_tier.py --run G1",
    "# 3. 评审（judge 另册）",
    "python tools/run_lp_review_generic.py --batch-dir logs/longprobe_g1_live_20261030 "
    "--stories E037,E039,E041,E042 --auth <REVIEW_AUTH> --tag G1 --cand-name r9_slim",
    "# 4. 记忆 KPI（零调用）",
    "python tools/measure_memory_kpis.py",
    "# 5. 暴露记账",
    "python tools/story_custody.py expose --story E0xx --batch <批名> --arms <臂>",
]


def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for c in iter(lambda: f.read(65536), b""):
            h.update(c)
    return h.hexdigest()


def main():
    out = {"kind": "w1504_repro_manifest", "date": "2026-10-31", "sections": {}}
    missing = []
    for sec, files in FILES.items():
        rows = []
        for rel in files:
            p = os.path.join(ROOT, rel)
            if os.path.exists(p):
                rows.append({"file": rel, "sha256": sha(p)})
            else:
                rows.append({"file": rel, "missing": True})
                missing.append(rel)
        out["sections"][sec] = rows
    out["missing"] = missing
    out["repro_commands"] = REPRO_COMMANDS
    out["env_note"] = ("固定服务 dsf@http://127.0.0.1:17770/v1 temp0.7；辅助模型白名单 "
                       "hy3/dsf/qwen3.8-flash/gemini-3.8-flash 每 alias ≤3，hy4-preview 硬禁；"
                       "预算管理帽 16384；Python 3.11+ 无三方依赖（标准库实现）。")
    jp = os.path.join(PROG, "W15-04_REPRO_MANIFEST.json")
    json.dump(out, open(jp, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("missing=%d" % len(missing))
    for m in missing:
        print("  MISSING:", m)
    print("written", jp)


if __name__ == "__main__":
    main()
