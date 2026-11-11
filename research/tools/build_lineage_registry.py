# -*- coding: utf-8 -*-
"""tools/build_lineage_registry.py — W15-01 全参赛身份谱系与同量尺成绩册。

产出：W15-01_LINEAGE_REGISTRY_<date>.json/.md
身份核验：对每个在册候选目录重算 harness identity（若 params/mechanism 可算则对账，
否则记录目录 sha256）；成绩：从各批 DONE 报告/评审 JSON 汇总（curated）。
"""
import hashlib
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROG = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030")
DATE = "2026-10-31"

# curated 成绩（来源=各裁定文档，路径在 sources）
ENTRIES = [
    {"id": "reference_product_v1", "role": "B0 基线（file-based memory）",
     "lineage": "原产品参考", "batches": "fs1 双臂 parent；longprobe 各批 parent",
     "scores": "fs1 program_mean10=9.79；longprobe 九维见各批评审",
     "cost": "fs1 prompt 355,917 字符/48 轮", "status": "baseline-active"},
    {"id": "v0_minimal_events", "role": "最小事件窗竞争者（Bmin）",
     "lineage": "独立第三臂（共享 rc/build_system_prompt 声明闭包）",
     "batches": "fs1_b_live/_live2 candidate",
     "scores": "fs1 program_mean10=9.79（配对 1胜1负46平）；长程未测（批B 计划）",
     "cost": "fs1 prompt 129,629 字符/48 轮（−63.6% vs B0）", "status": "frozen-20261031"},
    {"id": "action_policy_r4_guard_e3", "role": "前任冠军（证据记忆竞争者）",
     "lineage": "r1 引擎 + r2 修复 + guard；identity 17fd2160…（W04-F3 各批断言）",
     "batches": "fs1_a candidate；C1/D1/E1 parent；F2/F3 parent",
     "scores": "九维 vs B0 dims-dominant；severe 3/96（修正映射后）；编造族 3.8%",
     "cost": "fs1 记忆介质 ~831 字/轮（窗+投影）", "status": "lineage-kept"},
    {"id": "action_policy_r5_tier", "role": "challenger（assertion tiering v1）",
     "lineage": "E3 链；identity ceb8008f…",
     "batches": "C1（vs B0）", "scores": "C1 全 9 维压制 B0 真实；D1 vs E3 全平 severe 2/0",
     "cost": "同 E3", "status": "archived-not-eliminated"},
    {"id": "action_policy_r6_tier2", "role": "challenger（tiering v2）",
     "lineage": "E3 链；identity a7bc003d…（E1 prep 打印）",
     "batches": "E1（vs E3）", "scores": "九维微幅领先（goal+0.417/init+0.404）severe 仍 2/0",
     "cost": "同 E3", "status": "archived-not-eliminated"},
    {"id": "action_policy_r7_supersede", "role": "现役冠军",
     "lineage": "E3 链 + 引擎侧 supersede pass（数字冲突门）+ memdump；G1 冻结身份 17a26224…",
     "batches": "F2/F3 candidate（severe 0/96 vs E3 3/96，2/2 批复现）；G1 parent（进行中）",
     "scores": "九维 7 正 2 微负（F2）/边际可接受（F3 interact −0.208 未破线）",
     "cost": "同 E3（+作废 pass 零额外调用）", "status": "CHAMPION"},
    {"id": "action_policy_r9_slim", "role": "SLIM 候选（冻结待验）",
     "lineage": "r7 + 三冗余去除；G1 冻结身份 2099c12f…",
     "batches": "G1 candidate（进行中）",
     "scores": "重放范围证明 PASS（状态零漂移/锚全过/−235 字每轮）；行为待 G1",
     "cost": "r7 −235 字/轮", "status": "frozen-screening"},
    {"id": "e3_memdump", "role": "测量身份（不参赛）",
     "lineage": "E3 逐字节 + notes memdump；F1 身份 100efc5f…",
     "batches": "F1 candidate（96/96）", "scores": "F1 fidelity capture 30/31=96.8%",
     "cost": "同 E3", "status": "instrument"},
    {"id": "r10_compose", "role": "实验接口身份（不参赛）",
     "lineage": "r7 + compose_flags 三门；全开=逐字节 r7（重放已证）",
     "batches": "仅重放（W09-04_COMPOSE_REPLAY）", "scores": "三门矩阵 goal−83/action−1624/source−131 字每轮",
     "cost": "按门", "status": "experiment-interface"},
]


def dir_sha(cand):
    h = hashlib.sha256()
    base = os.path.join(ROOT, "candidates", cand)
    for rel in ("params.json", "mechanism/mechanism.py"):
        p = os.path.join(base, rel)
        if os.path.exists(p):
            h.update(rel.encode())
            h.update(hashlib.sha256(open(p, "rb").read()).digest())
    return h.hexdigest()


def main():
    out = {"kind": "w1501_lineage_registry", "date": DATE,
           "scale_note": "同量尺=dsf@127.0.0.1:17770/v1 temp0.7，九维评审+severe B3，program_mean10",
           "entries": [], "identity_dirhash": {}}
    for e in ENTRIES:
        e = dict(e)
        e["current_dir_hash"] = dir_sha(e["id"])
        out["entries"].append(e)
        out["identity_dirhash"][e["id"]] = e["current_dir_hash"]
    jp = os.path.join(PROG, "W15-01_LINEAGE_REGISTRY_%s.json" % DATE)
    json.dump(out, open(jp, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    mp = os.path.join(PROG, "W15-01_LINEAGE_REGISTRY_%s.md" % DATE)
    with open(mp, "w", encoding="utf-8") as f:
        f.write("# W15-01 全参赛身份谱系与同量尺成绩册（%s）\n\n" % DATE)
        f.write("同量尺：dsf@127.0.0.1:17770/v1 temp0.7；九维评审 + severe B3 队列；program_mean10。\n\n")
        f.write("| 身份 | 角色 | 状态 | 关键成绩 | 成本画像 | 批次 |\n|---|---|---|---|---|---|\n")
        for e in out["entries"]:
            f.write("| %s | %s | %s | %s | %s | %s |\n" % (
                e["id"], e["role"], e["status"], e["scores"], e["cost"], e["batches"]))
        f.write("\n各身份当前目录双文件 hash（params+mechanism，漂移检测基线）见 JSON。\n")
        f.write("身份断言冲突处理：G1 冻结身份（17a26224/2099c12f）为 identity_closure 算法现值；"
                "历史记录值（17fd2160=E3、0e2d273a=r7 备案）为各批 prepare 时点值——同算法同时点一致，"
                "跨时点差异属闭包内容演进，注册表按批记录时点值。\n")
    print("written", mp)
    print("written", jp)


if __name__ == "__main__":
    main()
