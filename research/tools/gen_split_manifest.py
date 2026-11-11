# -*- coding: utf-8 -*-
"""gen_split_manifest.py — C3 数据划分/暴露登记（G06a）。

逐故事家族登记：用途、来源、创建/冻结时间、是否曾参与调参、谁可见、版本散列。
内容不进入候选运行包。B线在本仓库无封存目录 → 如实登记 NOT_CREATED，
封存接口用合成密封夹具验证（不读真实B线——本仓库也没有真实B线内容）。
"""
import hashlib
import json
import os
import re
import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def fam(name, use, origin, exposure, visible, files, notes="", unshipped=None):
    out_files = {}
    for f in files:
        p = os.path.join(ROOT, f)
        if os.path.isfile(p):
            out_files[f] = sha(p)
        else:
            out_files[f] = "MISSING"
    # R3 收口（G06a 干净目录复验发现）：logs/ 下历史证据只作溯源登记，
    # 不随包交付（FORBIDDEN logs/）——审计只强制随包文件。
    if unshipped is None:
        unshipped = [f for f in files if f.startswith("logs/")]
    return {"family": name, "role": use, "origin": origin,
            "created_or_frozen": "2026-10-21（本轮登记；历史创建见 origin）",
            "tuning_exposure": exposure, "visible_to": visible,
            "files": out_files, "unshipped_files": unshipped, "notes": notes}


# dev 集（exposed：参与过校准/调参/迭代，可持续开发诊断）
dev_eps = ["NCA_CORR_INTENT_v1.json", "NCB_ENTITY_DENSE_v1.json",
           "PILOT_L1_v1.json", "PILOT_L2_v1.json",
           "PHASE05_INTENT_BASE.json", "PHASE05_INTENT_flirt.json",
           "PHASE05_INTENT_friend.json", "PHASE05_INTENT_none.json",
           "PHASE05_INTENT_restraint.json", "PHASE05_MEM.json",
           "PHASE0_GEN_220.json", "L3B_MINI_CTRL.json", "L3B_MINI_FULL.json",
           "SMOKE_EP_001.json", "A_ch01_sample_v01.json"]
dev_files = ["benchmark/episodes/" + e for e in dev_eps]
dev_files += ["benchmark/episodes/dev/" + f for f in
              os.listdir(os.path.join(ROOT, "benchmark/episodes/dev"))]
dev_files += ["benchmark/cases/SMOKE_NOTE_TRAP_001.json",
              "benchmark/cases/SMOKE_NOTE_TRAP_002.json",
              "benchmark/rubric.md"]

# 锚点集（SPLIT_v1.json 已裁定：dev14=开发集；val6=留出回归子集，非独立隐藏集）
split = json.load(open(os.path.join(ROOT, "benchmark/anchors/SPLIT_v1.json"),
                       encoding="utf-8"))
anchor_files = ["benchmark/anchors/SPLIT_v1.json",
                "benchmark/anchors/ANCHORS_20.md", "benchmark/anchors/ANCHORS_20_v2.md",
                "benchmark/anchors/anchors_key.json", "benchmark/anchors/anchors_key_v2.json"]

# 历史真实调用证据（exposed：用于回放/诊断）
real66_files = ["logs/real66_v1_all_recommended.json", "logs/real66_v1_all_alternatives.json",
                "logs/real66_v1_expr_audit.json", "logs/replay149_gate_v17_20261021.json"]

families = [
    fam("dev_stories", "dev", "Pilot/Phase0/Phase05/L3B-mini/NCA/NCB 开发迭代",
        "exposed_full（参加校准/重排/检索调参/表达迭代）",
        "worker+reviewer+user", dev_files,
        "可持续开发诊断；不得用于宣称独立比较"),
    fam("anchors_split", "dev(val6=regression_holdout_not_independent)",
        "2026-10-17 独立评审降级裁定（SPLIT_v1.json 原文）",
        "exposed_full（原20题全部参加校准/重排/分诊，标签与成绩已暴露）",
        "worker+reviewer+user", anchor_files,
        "val6 只能称历史题库留出回归子集；正式验收集须另行制作+真人独立标注+全程隔离"),
    fam("real66_evidence", "dev_diagnostic", "2026-10-21 66 次真实实跑（已授权批次）",
        "exposed_full（已用于初审/复核/回放）",
        "worker+reviewer+user", real66_files,
        "只读历史证据；不覆盖、不加工（§2.1）"),
    fam("selection_set", "selection", "未建立（无独立出题/保管身份）",
        "EXPOSED / NOT_READY",
        "worker（当前无独立保管方——worker 看过的题不得打'未见验证'标签）",
        [],
        "缺口：①无独立出题/保管身份 ②无隔离冻结手续。最小执行方案（需用户确认，"
        "不自动替代原选择40口径）：≥2 条不同故事 × 每条 8–12 请求位，由非调参身份制作并封存"),
    fam("b_line_sealed", "final_sealed", "未在本仓库创建（原 B线80 口径未落盘）",
        "NOT_CREATED",
        "无（封存接口用合成密封夹具验证：refs/arena_v1/seal_fixture/）",
        [],
        "本仓库不存在真实B线内容，故无'读取'风险；启封接口由合成夹具验证"),
]

# 合成密封夹具（验证封存访问控制与启封接口，不碰真实内容）
seal_dir = os.path.join(ROOT, "refs/arena_v1/seal_fixture")
os.makedirs(seal_dir, exist_ok=True)
synthetic = {"id": "SYNTH_SEALED_001", "kind": "synthetic_canary",
             "note": "合成封存夹具——仅供访问控制/启封接口测试，非真实题面",
             "payload": "SEALED-CANARY-7f3a"}
open(os.path.join(seal_dir, "SYNTH_SEALED_001.json"), "w",
     encoding="utf-8").write(json.dumps(synthetic, ensure_ascii=False, indent=1))
seal_hash = sha(os.path.join(seal_dir, "SYNTH_SEALED_001.json"))
open(os.path.join(seal_dir, "SEAL_LOCK.sha256"), "w", encoding="utf-8").write(
    seal_hash + "  SYNTH_SEALED_001.json\n")

manifest = {
    "date": "2026-10-21",
    "release": "arena_v1",
    "rule": ("内容不进入候选运行包；同一故事改写/同一素材变体不得跨划分；"
             "冻结后不得根据结果调本批候选再声称独立比较"),
    "families": families,
    "seal_fixture": {"dir": "refs/arena_v1/seal_fixture",
                     "lock": "refs/arena_v1/seal_fixture/SEAL_LOCK.sha256",
                     "payload_sha256": seal_hash,
                     "verify_command": "python tools/verify_seal_fixture.py"},
    "gates": {"G06a": "candidate-PASS（待 verify_arena_v1 实测）",
              "G06b": "NOT_READY（缺独立出题/保管身份，缺口见 selection_set.notes）"},
}
out = os.path.join(ROOT, "benchmark/releases/arena_v1")
os.makedirs(out, exist_ok=True)
json.dump(manifest, open(os.path.join(out, "split_manifest.json"), "w",
                         encoding="utf-8"), ensure_ascii=False, indent=1)
n_missing = sum(1 for f in families for v in f["files"].values() if v == "MISSING")
print("split_manifest.json: %d families | files %d | missing %d"
      % (len(families), sum(len(f["files"]) for f in families), n_missing))
