#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""make_review_package.py — 审稿包白名单打包 v2（第七波判决 §7：自包含 + 无自引用 manifest）。

v2 修正（第七波判决）：
  - **manifest 生成顺序改为无自引用**：先生成列举 payload 的包内 manifest
    （排除自身与 sidecar）→ 打包 → sidecar 记录整包 sha256（完整 64 位）。
    此前"先打包后写 sidecar"导致包内 manifest 指向上一轮旧包；
  - 补两个关键复核依赖：benchmark/rubric.md（评分权威/开放契约定义）、
    logs/phase05/judge_calib.json（首轮 Judge 校准原始结果）；
  - 逐文件 sha256 改完整 64 位（便于后续身份核对）；
  - 排除断言维持：dataset/_archive_v1/pycache/.git 零成员；封存 B 线不涉及
    （白名单机制下天然排除，未列入即不进包）。

用法：python tools/make_review_package.py
产出：<ROOT>/lovebrain_review_v5_<ts>.zip + <ROOT>/refs/REVIEW_PACKAGE_MANIFEST.json（sidecar）
"""
import argparse
import hashlib
import json
import os
import time
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 白名单：审稿必需的目录（相对 ROOT）；未列出一律不进包
WHITELIST_DIRS = ("arena", "candidates", "tools", "refs",
                  "benchmark/episodes", "benchmark/episodes/archive",
                  "benchmark/media", "benchmark/anchors", "benchmark/cases")
# 第七波判决 §7：关键单文件依赖（评分权威 + 首轮 Judge 校准原始结果）
# v8 复核 §5.1 修正：声称交付的 logs 根 JSON 必须在白名单内（v8 曾因缺此项漏包）
WHITELIST_FILES = (
    "benchmark/rubric.md", "logs/phase05/judge_calib.json",
    "logs/rereval_v092_unified_20261020.json",
    "logs/retr_dev_nodes_obs_20261020.json",
    "logs/expr_audit_baseline_20261020.json",
    "logs/defect_quotes_20261020.json",
    "logs/nca_ncb_mock_replay_20261020.json",
    # real66 v1（66 次真实调用）初审证据（v12）
    "logs/real66_v1_all_recommended.json",
    "logs/real66_v1_all_alternatives.json",
    "logs/real66_v1_expr_audit.json",
    "logs/real66_v1_stdout.jsonl",
)
# 声称交付断言：打包后逐项核验包内存在（缺失即 FAIL，不再静默漏包）
# v13：新增 Wave15 整改产物断言（复核报告归档/方案 v4/phase3 候选/52 槽执行器/开发题面/匿名材料 v2）
V13_MUST = (
    "refs/PHASE1B_WAVE15_V12_REMEDIATION_20261021.md",
    "refs/L3B_PARENT_CHILD_PLAN_20261021.md",
    "refs/REAL66_ANON_GROUPS_V2_20261021.md",
    "refs/REAL66_ANON_CORRECTED_20261021.md",
    "refs/REAL66_V12_INDEPENDENT_REVIEW_20261021/REAL66_V12_INDEPENDENT_REVIEW_20261021.md",
    "refs/REAL66_V12_INDEPENDENT_REVIEW_20261021/NODE_REVIEW.md",
    "refs/REAL66_V12_INDEPENDENT_REVIEW_20261021/REAL66_ANON_CORRECTED.md",
    "candidates/phase3_facts/mechanism/mechanism.py",
    "candidates/phase3_facts/assets/system_prompt/facts_evidence.md",
    "candidates/phase3_facts/IDENTITY.md",
    "candidates/phase3_facts/MECHANISM.md",
    "tools/l3b_parent_child_executor.py",
    "tools/test_parent_child_executor.py",
    "tools/test_scene_assembly.py",
    "tools/test_phase3_facts.py",
    "tools/gen_anon_groups.py",
    "benchmark/episodes/dev/DEV_MEETING_FACTS_v1.json",
    "benchmark/episodes/dev/DEV_TWO_BOXES_v1.json",
    "benchmark/episodes/dev/DEV_NEW_STATION_v1.json",
    "benchmark/episodes/dev/DEV_ROOMMATE_ADVICE_v1.json",
)
CLAIMED_PRESENT = WHITELIST_FILES[2:] + V13_MUST
# 明确排除（即使落在白名单目录内）
EXCLUDE_PARTS = ("dataset", "_archive_v1", "__pycache__", ".git")
EXCLUDE_EXT = (".pyc", ".zip")
# v12：匿名对照映射表（KEY）不入审稿包——纪律：大牛先裁决、后看 KEY
# （组文件 refs/REAL66_ANON_GROUPS_20261021.md 照常进包，KEY 在包外单独移交）
EXCLUDE_FILES = ("refs/REAL66_ANON_KEY_20261021.md",
                 "refs/REAL66_ANON_KEY_20261021.json")

# 历史大日志目录：只带关键证据目录
LOG_INCLUDE = ("logs/pilot_r3", "logs/l3b_fork", "logs/l3b_mini",
               "logs/phase1a", "logs/pilot_r1_bare_candidate_bug",
               "logs/pilot_r2_image_ref_bug",
               "logs/l3b_66_dryrun_v2", "logs/l3b_66_drill_v2",
               "logs/l3b_66_dryrun_v3", "logs/l3b_66_drill_v3",
               "logs/l3b_66_dryrun_v4",
               "logs/l3b_66_real66_v1")

IN_PKG_MANIFEST = "REVIEW_PACKAGE_MANIFEST.json"
SIDECAR = os.path.join("refs", "REVIEW_PACKAGE_MANIFEST.json")


def _allowed(rel_path: str) -> bool:
    parts = rel_path.replace("\\", "/").split("/")
    if any(p in EXCLUDE_PARTS for p in parts):
        return False
    if rel_path.endswith(EXCLUDE_EXT):
        return False
    rel = rel_path.replace("\\", "/")
    if rel in EXCLUDE_FILES:
        return False
    if rel in WHITELIST_FILES or rel == SIDECAR.replace("\\", "/"):
        # sidecar 不进包（无自引用）；白名单文件进包
        return rel in WHITELIST_FILES
    if rel.startswith(tuple(d + "/" for d in WHITELIST_DIRS)):
        return True
    if rel.startswith(tuple(d + "/" for d in LOG_INCLUDE)):
        return True
    return os.path.dirname(rel) == "" and rel.endswith((".md", ".txt"))   # 根部说明文件


def _sha256_full(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=None)
    args = ap.parse_args()
    out = args.out or os.path.join(
        ROOT, "lovebrain_review_v13_%s.zip" % time.strftime("%Y%m%d_%H%M%S"))
    out = os.path.abspath(out)
    out_name = os.path.basename(out)

    members = []
    for root, dirs, files in os.walk(ROOT):
        rel_dir = os.path.relpath(root, ROOT).replace("\\", "/")
        if rel_dir == ".":
            rel_dir = ""
        for fn in files:
            rel = (rel_dir + "/" + fn).lstrip("/")
            if _allowed(rel):
                members.append(rel)
    members.sort()

    # 0a) 声称交付断言：白名单声称文件必须真实存在（缺文件即停，不允许静默漏）
    missing_claims = [rel for rel in CLAIMED_PRESENT
                      if not os.path.isfile(os.path.join(ROOT, rel))]
    if missing_claims:
        raise SystemExit("CLAIMED_MISSING: %s" % missing_claims)
    # 0b) 包内存在断言：声称文件必须被白名单实际收纳（缺失即停）
    not_collected = [rel for rel in CLAIMED_PRESENT if rel not in members]
    if not_collected:
        raise SystemExit("NOT_IN_PACKAGE: %s" % not_collected)

    # 1) 包内 manifest：只列举 payload（排除自身），写临时文件随包打入
    entries = []
    for rel in members:
        p = os.path.join(ROOT, rel)
        entries.append({"path": rel, "bytes": os.path.getsize(p),
                        "sha256": _sha256_full(p)})
    violations = [e["path"] for e in entries
                  if any(p in e["path"].split("/") for p in ("dataset", "_archive_v1"))]
    in_pkg_manifest = {
        "package": out_name,
        "built_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "n_files": len(entries),
        "whitelist_dirs": list(WHITELIST_DIRS),
        "whitelist_files": list(WHITELIST_FILES),
        "log_include": list(LOG_INCLUDE),
        "excluded_parts": list(EXCLUDE_PARTS),
        "excluded_files": list(EXCLUDE_FILES),
        "exclusion_assertion": "PASS" if not violations else "FAIL: %s" % violations[:10],
        "declaration": ("审稿白名单包（自包含）：dataset/（用户真实素材）、_archive_v1/、"
                        "pycache 已排除；含评分权威 benchmark/rubric.md 与首轮 Judge 校准 "
                        "logs/phase05/judge_calib.json；含 66 次真实调用（l3b_66_real66_v1）"
                        "全量初审证据、v12 复核报告归档（REAL66_V12_INDEPENDENT_REVIEW_20261021/）、"
                        "Wave15 整改产物（WAVE15 报告/方案 v4/phase3_facts 候选/52 槽执行器/"
                        "开发正反例题面/匿名组 v2 与审查方修正版）；匿名映射表（KEY）按纪律不入包，"
                        "裁决完成后另行移交。本 manifest 只列 payload（排除自身），"
                        "整包散列见仓库 sidecar refs/REVIEW_PACKAGE_MANIFEST.json。"),
        "files": entries,
    }
    tmp_manifest = out + ".manifest.tmp"
    with open(tmp_manifest, "w", encoding="utf-8", newline="") as f:
        json.dump(in_pkg_manifest, f, ensure_ascii=False, indent=1)

    # 2) 打包（payload + 包内 manifest）
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for rel in members:
            z.write(os.path.join(ROOT, rel), rel)
        z.write(tmp_manifest, IN_PKG_MANIFEST)
    os.remove(tmp_manifest)

    # 3) sidecar：整包完整 sha256（不重复逐文件清单，包内 manifest 承担）
    zip_sha = _sha256_full(out)
    sidecar = {
        "package": out_name,
        "built_at": in_pkg_manifest["built_at"],
        "zip_sha256_full": zip_sha,
        "n_payload_files": len(entries),
        "in_package_manifest": IN_PKG_MANIFEST,
        "exclusion_assertion": in_pkg_manifest["exclusion_assertion"],
        "note": ("sidecar 在包外（refs/），包内无自引用。核包步骤："
                 "① 对 zip 算 sha256 与本文件 zip_sha256_full 比对；"
                 "② 解包后 REVIEW_PACKAGE_MANIFEST.json 逐文件 sha256 可复核。"),
    }
    mpath = os.path.join(ROOT, SIDECAR)
    with open(mpath, "w", encoding="utf-8") as f:
        json.dump(sidecar, f, ensure_ascii=False, indent=1)
    print("files=%d size=%.1fMB exclusion_assertion=%s zip_sha256=%s"
          % (len(entries), os.path.getsize(out) / 1e6,
             in_pkg_manifest["exclusion_assertion"], zip_sha[:16]))
    print("sidecar →", mpath)
    # 自检：包内 manifest 的 package 名 = 本次包名（防旧 manifest 混入）
    with zipfile.ZipFile(out) as z:
        names = z.namelist()
        assert IN_PKG_MANIFEST in names
        got = json.loads(z.read(IN_PKG_MANIFEST).decode("utf-8"))
    leaked = [rel for rel in EXCLUDE_FILES if rel in names]
    if leaked:
        raise SystemExit("排除文件泄漏进包: %s" % leaked)
    if got["package"] != out_name:
        raise SystemExit("包内 manifest 指向旧包（%s ≠ %s）" % (got["package"], out_name))
    if violations:
        raise SystemExit("排除断言失败")
    print("自检 PASS：包内 manifest 属本次包；sidecar 无自引用。")


if __name__ == "__main__":
    main()
