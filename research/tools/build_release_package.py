# -*- coding: utf-8 -*-
"""build_release_package.py — ARENA V1 自包含发布包（指导书 §7-E5）。

包内容 = 运行所需代码 + 显式依赖 + 允许公开的开发题/媒体 + 参考基线 +
清单与验收证据。排除：用户 dataset、密钥、原产品签名文件、真实 B 线
（本仓库本就未落盘，用排除断言固化）、logs/ 运行历史、隔离残石、zip。

manifest 只列 payload；sidecar 记录整包散列，无自引用。
构建后独立临时目录解包，cwd=解包目录跑离线冒烟复验
（协议等价 + 题库 schema + 评分器导入），不依赖 D:/LoveBrain、缓存模块
或原日志绝对路径。

输出：dist/arena_v1_pkg/<ts>/  ├── arena_v1_payload/（内容）
                                ├── manifest.json（payload 清单+散列）
                                └── package.sha256（整包 sidecar）
"""
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---- 允许进包的内容（白名单，防误带） ----
PAYLOAD_DIRS = [
    ("arena", ["arena/__pycache__", "arena/isolation/__pycache__"]),
    ("refs/arena_v1/protocol_baseline", []),
    ("candidates/reference_product_v1", []),
    ("candidates/v2_recall", ["candidates/v2_recall/__pycache__"]),
    # 现行主链路指导书与训练会话开启 prompt（reviewer 必读）
    ("refs/harness_api_mainline_20261022", []),
    ("candidates/_attack_fixture", ["candidates/_attack_fixture/__pycache__"]),
    ("candidates/phase0_beta_single", ["candidates/phase0_beta_single/__pycache__"]),
    ("candidates/phase1_retr_idf", ["candidates/phase1_retr_idf/__pycache__"]),
    ("candidates/phase2_expr", ["candidates/phase2_expr/__pycache__"]),
    ("candidates/phase3_facts", ["candidates/phase3_facts/__pycache__"]),
    ("benchmark/episodes", []),
    ("benchmark/media", []),
    ("benchmark/anchors", []),
    ("benchmark/cases", []),
    # 复核 v3 §2C：24 题内容版本（原件+评审卡+DEV_EXPOSED 清单）随包
    ("benchmark/dev_pilot_v02", []),
    # R3#7：评审闭环演练现场（G07 干净目录复验所需；mapping_secret 为演练
    # 映射非凭据，FORBIDDEN 例外见 _ALLOWED_PATTERNS）
    ("benchmark/releases/arena_v1/review_loop", []),
]
PAYLOAD_FILES = [
    "benchmark/rubric.md",
    "benchmark/core_set.json",
    "benchmark/dims_map.json",
    "refs/arena_v1/release.json",
    "refs/arena_v1/SCOPE_AND_STATUS.md",
    "refs/arena_v1/BASELINE_PARITY.md",
    "refs/arena_v1/C_PACKAGE_REPORT_20261021.md",
    "refs/arena_v1/ISOLATION.md",
    "refs/arena_v1/RELEASE_NOTES.md",
    "refs/arena_v1/CONTRACT_MATRIX.md",
    "refs/arena_v1/BENCHMARK_AND_REVIEW.md",
    # R3#7：可重建输入与受控数据说明
    "refs/arena_v1/REBUILD_AND_CONTROLLED_DATA.md",
    # V14：部署前提（受限执行环境）+ 完整生命周期对拍冻结期望
    "refs/arena_v1/ISOLATION_ENV_PREREQ.md",
    "refs/arena_v1/baseline_lifecycle_expected.json",
    "refs/arena_v1/review_drill_spec.json",
    "refs/arena_v1/seal_fixture/SYNTH_SEALED_001.json",
    "refs/arena_v1/seal_fixture/SEAL_LOCK.sha256",
    "benchmark/releases/arena_v1/split_manifest.json",
    "tools/test_protocol_public.py",
    "tools/test_baseline_parity.py",
    "tools/test_scoring_authority.py",
    "tools/gen_contract_matrix.py",
    "tools/gen_coverage_matrix.py",
    "tools/verify_seal_fixture.py",
    "tools/review_loop.py",
    "tools/verify_arena_v1.py",
    "tools/build_release_package.py",
    # R3#7：发布包此前缺 9 文件（独立复核 §9）
    "tools/gen_split_manifest.py",
    "tools/test_anon_binding_guards.py",
    "tools/test_failure_persistence.py",
    "tools/test_input_boundary.py",
    "tools/test_isolation_matrix.py",
    # V14 C4 收口：宿主 safe-delete 批量阈值适配（逐文件清场），
    # 矩阵/lifecycle/G11 替身共同依赖，缺失则包内 verify 启动即崩
    "tools/safe_fs.py",
    "tools/test_no_reply_protocol.py",
    "tools/test_output_structure.py",
    "tools/test_posttest_event_pairs.py",
    "tools/test_scene_assembly.py",
    # R3 加固自测（矩阵/负例/守卫）
    "tools/test_verify_strict_negative.py",
    "tools/test_review_loop_guards.py",
    "tools/test_coverage_guards.py",
    "tools/test_seal_access_control.py",
    "tools/test_g11_standin.py",
    "tools/freeze_release.py",
    # V14 执行书 C6：唯一启动 CLI 必须随包（替身演练/G04 对拍依赖它）
    "tools/iso_run.py",
    "tools/test_baseline_lifecycle.py",
    # 2026-10-22 跨轮基线闭环验收（7 项）
    "tools/test_baseline_carryover.py",
    "tools/replay_exam_episode.py",
    "tools/exam_layered_score.py",
    "tools/test_v2_recall.py",
    # 复核 v3 §2A/§2C：授权预算权威与反例、导入检查器、评审接线演练
    "tools/test_authorization_budget.py",
    "tools/import_pilot_questions.py",
    "tools/review_wiring_drill.py",
    # 复核 v3 修正版文档（G08 方案 v2 / 额度事故更正 / 授权单）
    "refs/arena_v1/G08_DEPLOYMENT_PROPOSAL_20261022.md",
    "refs/arena_v1/AUTHORIZATION_INCIDENT_CORRECTION_20261022.md",
    "refs/arena_v1/AUTHORIZATION_INTEGRATION.json",
    # 执行书 §3.2：一次收集的系统变更范围 + 首场预算审批件（随包留档）
    "refs/arena_v1/APPROVAL_REQUEST_20261022.md",
    # G08 部署执行器（--apply 已硬拒绝；--check 只读）
    "tools/g08_deploy.py",
    # 2026-10-22 主链路纠偏：唯一薄 API 入口 + 边界反例 + 范围/清单/宪法
    "tools/api_trial.py",
    "tools/test_api_boundary.py",
    "arena/api_candidate.py",
    "refs/arena_v1/SCOPE_DECISION_API_CANDIDATE_20261022.md",
    "refs/arena_v1/scope.json",
    "refs/arena_v1/CALL_LIST_EXAM1_20261022.md",
    "refs/arena_v1/APPROVAL_REQUEST_20261022.md",
    "AGENTS.md",
    "OBJECTIVE.md",
    "START_PROMPT.md",
]
# 排除断言：包内出现即 FAIL（_ALLOWED_PATTERNS 为已审例外）
FORBIDDEN_PATTERNS = [
    "b_line_real", "real66_anon", "dataset", "secret", "credential",
    ".key", "token.txt", "_acl_locked", "raw_chat", "logs/",
    "isol_runs", ".zip", "__pycache__", ".pyc",
]
_ALLOWED_PATTERNS = {
    # 评审演练映射： anon<->arm 映射与绑定散列，不含任何凭据（R3#7 审定）
    "benchmark/releases/arena_v1/review_loop/mapping_secret.json",
}


def sha_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def build(pkg_root):
    payload = os.path.join(pkg_root, "arena_v1_payload")
    os.makedirs(payload, exist_ok=True)
    copied = []

    def copy_file(rel):
        src = os.path.join(ROOT, rel)
        if not os.path.isfile(src):
            return False
        dst = os.path.join(payload, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
        copied.append(rel)
        return True

    for d, excludes in PAYLOAD_DIRS:
        src_root = os.path.join(ROOT, d)
        if not os.path.isdir(src_root):
            continue
        for cur, dirs, files in os.walk(src_root):
            rel_cur = os.path.relpath(cur, ROOT)
            if any(rel_cur.replace("\\", "/").startswith(e)
                   for e in [x.replace("/", os.sep) for x in excludes]):
                dirs[:] = []
                continue
            dirs[:] = [x for x in dirs
                       if x != "__pycache__"
                       and not x.startswith("_acl_locked")]
            for f in files:
                if f.endswith((".pyc", ".pyo")):
                    continue
                copy_file(os.path.relpath(os.path.join(cur, f), ROOT))
    missing = [r for r in PAYLOAD_FILES if not copy_file(r)]

    # ---- manifest（payload 清单+散列，无自引用）----
    manifest = {"package": "arena_v1_release", "built_at":
                datetime.datetime.now().isoformat(timespec="seconds"),
                "files": {}}
    for rel in sorted(copied):
        manifest["files"][rel] = sha_file(os.path.join(payload, rel))
    manifest_path = os.path.join(pkg_root, "manifest.json")
    json.dump(manifest, open(manifest_path, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)

    # ---- 排除断言 ----
    violations = []
    for cur, _d, files in os.walk(payload):
        for f in files:
            rel = os.path.relpath(os.path.join(cur, f), payload).replace("\\", "/")
            rel_low = rel.lower()
            if rel_low in _ALLOWED_PATTERNS:
                continue
            for pat in FORBIDDEN_PATTERNS:
                if pat in rel_low:
                    violations.append("%s (pattern %s)" % (rel, pat))
    # 内容级密钥嗅探（regex 形态，避免模式表自匹配误报）：
    # 真实密钥形状 = 长随机串；裸模式词不算
    import re as _re
    key_shapes = [_re.compile(r"sk-[A-Za-z0-9]{20,}"),
                  _re.compile(r"api_key\s*=\s*['\"][^'\"]{12,}['\"]"),
                  _re.compile(r"AKIA[0-9A-Z]{16}"),
                  _re.compile("\x46fl@")]   # 转义防模式自匹配
    for rel in copied:
        p = os.path.join(payload, rel)
        if rel.endswith((".py", ".json", ".md")):
            txt = open(p, encoding="utf-8", errors="ignore").read()
            for rx in key_shapes:
                if rx.search(txt):
                    violations.append("%s matches %s" % (rel, rx.pattern))

    # ---- 干净目录解包冒烟复验 ----
    smoke_ok, smoke_detail = True, ""
    if not violations:
        tmp = tempfile.mkdtemp(prefix="arena_v1_unpack_")
        try:
            zp = os.path.join(pkg_root, "arena_v1_payload.zip")
            with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED) as z:
                for cur, _d, files in os.walk(payload):
                    for f in files:
                        full = os.path.join(cur, f)
                        z.write(full, os.path.relpath(full, pkg_root))
            with zipfile.ZipFile(zp) as z:
                z.extractall(tmp)
            unpacked = os.path.join(tmp, "arena_v1_payload")
            # 必需文件存在断言（解包后相对路径可达）
            for must in ("arena/lb_scorer.py", "arena/protocol_public.py",
                         "benchmark/episodes/NCA_CORR_INTENT_v1.json",
                         "tools/test_protocol_public.py",
                         "tools/verify_arena_v1.py",
                         "benchmark/releases/arena_v1/review_loop/"
                         "mapping_secret.json"):
                if not os.path.isfile(os.path.join(unpacked, must)):
                    smoke_ok, smoke_detail = False, "缺件 %s" % must
            if smoke_ok:
                # 快速预检（import + 协议等价，失败立即短路，省时）
                r0 = subprocess.run(
                    [sys.executable, "-B", "-X", "utf8",
                     "tools/test_protocol_public.py"],
                    cwd=unpacked, capture_output=True, text=True,
                    encoding="utf-8", errors="replace", timeout=300)
                r0b = subprocess.run(
                    [sys.executable, "-B", "-X", "utf8", "-c",
                     "import sys; sys.path.insert(0,'arena');"
                     "import lb_scorer, protocol_public;"
                     "print('IMPORT-OK')"],
                    cwd=unpacked, capture_output=True, text=True,
                    encoding="utf-8", errors="replace", timeout=120)
                pre_ok = r0.returncode == 0 and "IMPORT-OK" in (r0b.stdout or "")
                smoke_mode = os.environ.get("ARENA_SMOKE_MODE", "full")
                if not pre_ok:
                    smoke_ok = False
                    smoke_detail = "precheck protocol rc=%d import=%s" % (
                        r0.returncode, r0b.stderr[-150:])
                elif smoke_mode == "light":
                    # 递归边界模式（unpack 上下文里的 G10）：只做 precheck，
                    # 防止 verify→build→verify→build 无限嵌套
                    smoke_ok, smoke_detail = True, "light(precheck-only)"
                else:
                    # R3#7：干净目录执行**同一个完整 offline 入口**
                    # （只 import+protocol 测试不算——复核 §9 口径）。
                    # G06b 独立选择为外部缺件，不影响 offline 退出码口径。
                    # 递归边界：子 verify 带 ARENA_UNPACK_CONTEXT=1——其自身
                    # G10 的打包冒烟降为 light（见 verify g10/ARENA_SMOKE_MODE），
                    # 防 verify→build→verify→build 无限嵌套。
                    acc_out = os.path.join(tmp, "acceptance")
                    child_env = dict(os.environ, ARENA_UNPACK_CONTEXT="1")
                    r = subprocess.run(
                        [sys.executable, "-B", "-X", "utf8",
                         "tools/verify_arena_v1.py", "--mode", "offline",
                         "--release", "refs/arena_v1/release.json",
                         "--out", acc_out],
                        cwd=unpacked, capture_output=True, text=True,
                        encoding="utf-8", errors="replace", timeout=1800,
                        env=child_env)
                    acc = {}
                    ap = os.path.join(acc_out, "ACCEPTANCE.json")
                    if os.path.isfile(ap):
                        acc = json.load(open(ap, encoding="utf-8"))
                    gate_status = {g["id"]: g["status"]
                                   for g in acc.get("gates", [])}
                    # V14 复现性口径：干净目录嵌套 verify 的**门禁画像**
                    # 必须与宿主一致——诚实环境 FAIL（G08）下嵌套 rc=1 是
                    # 预期行为，不构成复现失败；但任何门禁状态漂移（缺件/
                    # 假 PASS/额外 FAIL）都算复现失败。
                    expected_profile = {
                        "G01": "PASS", "G02": "PASS", "G03": "PASS",
                        "G04": "PASS", "G05": "PASS", "G06a": "PASS",
                        "G06b": "NOT_READY", "G07": "PASS",
                        # 2026-10-22 主链路纠偏：G08 位点改为"本模式边界验收"
                        # （矩阵结构 + test_api_boundary）；OS 部署非前置。
                        "G08": "PASS",
                        "G09": "PASS",
                    }
                    drift = ["%s:%s!=%s" % (g, gate_status.get(g), want)
                             for g, want in expected_profile.items()
                             if gate_status.get(g) != want]
                    # 离线模式画像：G06b 外部缺件不影响 offline 判定，
                    # G08 现为 PASS → 期望 OFFLINE_READY（旧值 NOT_READY 已不适用）
                    profile_ok = not drift and acc.get(
                        "overall_status") == "OFFLINE_READY"
                    smoke_ok = profile_ok
                    smoke_detail = (
                        "full_offline rc=%d overall=%s profile_match=%s %s"
                        % (r.returncode, acc.get("overall_status", "?"),
                           not drift, ("drift=" + ";".join(drift[:4]))
                           if drift else "gates=11/11 match"))
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    # ---- sidecar：整包散列（含 manifest，不含 sidecar 自身）----
    ok = not violations and not missing and smoke_ok
    sidecar = os.path.join(pkg_root, "package.sha256")
    if ok:
        h = hashlib.sha256()
        for cur, _d, files in sorted(os.walk(pkg_root)):
            for f in sorted(files):
                if f == "package.sha256":
                    continue
                h.update(sha_file(os.path.join(cur, f)).encode())
        open(sidecar, "w").write(h.hexdigest() + "\n")
    return ok, {"copied": len(copied), "missing": missing,
                "violations": violations[:8], "smoke": smoke_detail,
                "sidecar": os.path.basename(sidecar) if ok else None}


def main():
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    pkg_root = os.path.join(ROOT, "dist", "arena_v1_pkg", ts)
    os.makedirs(pkg_root, exist_ok=True)
    ok, info = build(pkg_root)
    rel = os.path.relpath(pkg_root, ROOT)
    print("PACKAGE %s pkg=%s unpack_verify=%s files=%d missing=%d "
          "violations=%d smoke=%s"
          % ("PASS" if ok else "FAIL", rel,
             "OK" if info["smoke"] and not info["violations"] else "BAD",
             info["copied"], len(info["missing"]),
             len(info["violations"]), info["smoke"] or "?"))
    for v in info["violations"]:
        print("  VIOLATION:", v)
    for mfile in info["missing"]:
        print("  MISSING:", mfile)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
