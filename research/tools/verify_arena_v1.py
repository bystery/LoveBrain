# -*- coding: utf-8 -*-
"""verify_arena_v1.py — ARENA V1 考场总验收入口（指导书 §7-E1）。

只组织确定性检查、固定回放和经明确授权的集成测试：
不选择变异、不训练、不自动开新批次绕预算。

用法：
  python -B -X utf8 tools/verify_arena_v1.py --mode offline \
      --release refs/arena_v1/release.json --out logs/arena_v1_acceptance/<run_id>

模式：
  offline      默认。禁用真实上游适配器；隔离矩阵走密钥门禁本地替身上游；
               全部检查在确定性环境中执行。
  integration  只验证新隔离路径下的真实调用。无授权记录
               （refs/arena_v1/AUTHORIZATION_INTEGRATION.json authorized=true）
               时拒绝运行。

退出码：0 = 必要门禁集合（G01-G05、G06a、G07-G10）全 PASS；
        非 0 = 存在 FAIL/NOT_RUN（G06b 外部缺件不影响 offline 判定）。
状态口径（验收模板 §readiness_rules）：
  OFFLINE_READY = G01-G05、G06a、G07-G10 全 PASS；
  ENGINEERING_READY = + G11；ARENA_V1_READY_HUMAN = 再 + G06b。
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REQUIRED_OFFLINE = ["G01", "G02", "G03", "G04", "G05", "G06a", "G07", "G08", "G09", "G10"]
AUTH_FILE = os.path.join(ROOT, "refs", "arena_v1", "AUTHORIZATION_INTEGRATION.json")

# R3#1：固定预期测试集合（独立复核 §4——"证据必须属于本次 run、相同 release、
# 明确的预期测试集合"）。矩阵日志必须覆盖全部 ID；终态只允许 PASS/FAIL/
# RESIDUAL_DOCUMENTED；REQUIRED_PASS 必须逐项 PASS；RESIDUAL 仅允许白名单。
# V14：集合与 KNOWN_ENV_FAIL 从矩阵源导入（单一事实来源，防两处漂移）。
sys.path.insert(0, os.path.join(ROOT, "tools"))
from test_isolation_matrix import (  # noqa: E402
    EXPECTED_PASS as MATRIX_EXPECTED_PASS,
    RESIDUAL_ALLOWED as MATRIX_RESIDUAL_ALLOWED,
    KNOWN_ENV_FAIL as MATRIX_KNOWN_ENV_FAIL,
    NOT_APPLICABLE_BY_SCOPE as MATRIX_NOT_APPLICABLE,
    os_isolation_not_prerequisite)
# 2026-10-21 矩阵随新隔离架构重写（旧 ID 集含 L3/A3a-c/A5a-b/A6c_env_key_absent
# 已废弃：L3 由 coverage 的 message.image 承担；env key 白名单为宿主 env 结构保证）
# V14 执行书 C1：结果集合必须与冻结方案一致——任何实际 FAIL（含已知环境缺件）
# 都不得被吞；未知检查项/重复 ID 一律失败。G08 在受限执行环境部署前保持
# FAIL（部署前提 refs/arena_v1/ISOLATION_ENV_PREREQ.md）。


def sha_file(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def run_tool(cmd, timeout=600, env=None):
    """cwd=ROOT 跑子工具，返回 (exit_code, stdout_tail)。"""
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout,
                       env=env)
    out = (r.stdout or "") + (("\n[stderr] " + r.stderr[-400:]) if r.stderr.strip() else "")
    return r.returncode, out.strip()


class Verify:
    def __init__(self, mode, release_path, out_dir, sandbox_matrix=False):
        self.mode = mode
        self.release_path = os.path.join(ROOT, release_path) if not os.path.isabs(release_path) else release_path
        self.out = out_dir
        os.makedirs(self.out, exist_ok=True)
        self.gates = {}
        self.run_started = time.time()      # R3#1：证据时间锚（本次 run 起点）
        self.matrix_log_rel = None          # R3#1：本次 run 实际产出的矩阵日志
        # G08 部署后：隔离矩阵以沙箱账户跑（只有矩阵子进程带 LB_SANDBOX=1，
        # 其余门禁仍按当前用户运行——打包冒烟等不适配外部 run 目录）
        self.sandbox_matrix = sandbox_matrix

    def record(self, gid, status, evidence, command=None, observed=None, note=None):
        self.gates[gid] = {"id": gid, "status": status, "evidence": evidence,
                           "executed_command": command, "observed_result": observed,
                           "note": note}
        print("  [%s] %s %s" % (status, gid, (note or observed or "")[:110]))

    # ---------- G01 范围/版本/依赖冻结 ----------
    def g01_release_audit(self):
        rel = json.load(open(self.release_path, encoding="utf-8"))
        bad, missing = [], []
        files = rel.get("files", {})
        # R3#1 反例③：files={} 是空发布证明，不是 PASS
        if not files:
            self.record("G01", "FAIL",
                        [os.path.relpath(self.release_path, ROOT)],
                        observed="files=0（空 release 清单不得放行）",
                        note="release.json 无 files 或 files 为空")
            return False
        for f, meta in files.items():
            p = os.path.join(ROOT, f)
            if not os.path.isfile(p):
                missing.append(f)
                continue
            want = meta["sha256"] if isinstance(meta, dict) else meta
            if sha_file(p) != want:
                bad.append(f)
        ok = not bad and not missing
        self.record("G01", "PASS" if ok else "FAIL",
                    [os.path.relpath(self.release_path, ROOT)],
                    observed="files=%d missing=%d mismatch=%d"
                             % (len(rel.get("files", {})), len(missing), len(bad)),
                    note="release.json 逐文件散列核对"
                         + ("；漂移=%s" % (bad + missing)[:3] if (bad or missing) else ""))
        return ok

    # ---------- G02 契约矩阵 + 协议等价 ----------
    def g02_contract(self):
        rc1, out1 = run_tool([sys.executable, "-B", "-X", "utf8",
                              "tools/gen_contract_matrix.py"])
        m = re.search(r"CONTRACT_MATRIX\.md: (\d+)/(\d+) PASS", out1)
        n, total = (int(m.group(1)), int(m.group(2))) if m else (0, 0)
        rc2, out2 = run_tool([sys.executable, "-B", "-X", "utf8",
                              "tools/test_protocol_public.py"])
        ok = rc1 == 0 and rc2 == 0 and n == total and total >= 16
        self.record("G02", "PASS" if ok else "FAIL",
                    ["refs/arena_v1/CONTRACT_MATRIX.md"],
                    command="gen_contract_matrix + test_protocol_public",
                    observed="contract %d/%d (rc1=%d) protocol_equiv rc=%d"
                             % (n, total, rc1, rc2))
        return ok

    # ---------- G03 评分权威/原题快照/伪造隔离 ----------
    def g03_scoring(self):
        rc, out = run_tool([sys.executable, "-B", "-X", "utf8",
                            "tools/test_scoring_authority.py"], timeout=300)
        auth_ok = rc == 0 and "SCORING AUTHORITY PASS" in out
        # 新隔离路径下的伪造字段隔离（共享矩阵结果的 A4 判定）
        a4 = self._matrix_a4()
        ok = auth_ok and a4
        self.record("G03", "PASS" if ok else "FAIL",
                    ["tools/test_scoring_authority.py",
                     "logs/isolation_matrix_*.json"],
                    command="test_scoring_authority + isolation A4",
                    observed="authority rc=%d（评分与原题权威评分逐项全量比对）；"
                             "isolated_forge_ignored=%s" % (rc, a4))
        return ok

    def _matrix_a4(self):
        """R3#1 反例⑤：A4 证据必须来自本次 run 的矩阵日志（g08 先行产出），
        禁止读取历史日志的旧标记。"""
        rel = self.matrix_log_rel
        if not rel:
            return False
        p = os.path.join(ROOT, rel)
        if not os.path.isfile(p) or os.path.getmtime(p) < self.run_started:
            return False
        res = json.load(open(p, encoding="utf-8"))
        vs = res.get("verdicts", [])
        if not vs:
            return False
        return any(v.get("check") == "A5_forge_authority_ignored"
                   and v.get("verdict") == "PASS" for v in vs)

    # ---------- G04 基线对拍（helper 夹具 + 完整生命周期主链路） ----------
    def g04_baseline(self):
        rc, out = run_tool([sys.executable, "-B", "-X", "utf8",
                            "tools/test_baseline_parity.py"], timeout=300)
        ok1 = rc == 0 and "BASELINE PARITY PASS" in out and "FAIL: none" in out
        # V14 §6.4：补完整生命周期→下一轮请求/恢复的确定性对拍
        # （机制入口损坏必须使本测试失败——26 个 helper 夹具不覆盖）
        rc2, out2 = run_tool([sys.executable, "-B", "-X", "utf8",
                              "tools/test_baseline_lifecycle.py"], timeout=900)
        ok2 = rc2 == 0 and "BASELINE LIFECYCLE PASS" in out2
        m = re.search(r"pairs=(\d+) fails=(\d+)", out2)
        # 2026-10-22 跨轮闭环：本轮 analysis → 六投影写回 → 下一轮装配真读。
        # 复核 §3.4 要求"不能只证明文件写了，要证明下一轮读了"，故进门禁。
        rc3, out3 = run_tool([sys.executable, "-B", "-X", "utf8",
                              "tools/test_baseline_carryover.py"], timeout=600)
        ok3 = rc3 == 0 and "CARRYOVER RESULT: PASS" in out3
        mc = re.search(r"\((\d+) checks, failed=(\d+)\)", out3)
        ok = ok1 and ok2 and ok3
        self.record("G04", "PASS" if ok else "FAIL",
                    ["refs/arena_v1/BASELINE_PARITY.md",
                     "refs/arena_v1/baseline_lifecycle_expected.json",
                     "logs/baseline_carryover_result.json"],
                    command=("test_baseline_parity + test_baseline_lifecycle"
                             " + test_baseline_carryover"),
                    observed="parity rc=%d（26 夹具）；lifecycle rc=%d "
                             "pairs=%s fails=%s（11 轮冻结对拍+破坏注入）；"
                             "carryover rc=%d（%s 项跨轮闭环，失败 %s）"
                             % (rc, rc2, m.group(1) if m else "?",
                                m.group(2) if m else "?", rc3,
                                mc.group(1) if mc else "?",
                                mc.group(2) if mc else "?"),
                    note=None if ok else (out3[-300:] or out2[-300:]
                                          or out[-200:]))
        return ok

    # ---------- G05 覆盖矩阵 ----------
    def g05_coverage(self):
        rc, out = run_tool([sys.executable, "-B", "-X", "utf8",
                            "tools/gen_coverage_matrix.py"], timeout=300)
        m = re.search(r"families=(\d+) points=(\d+) missing=(\d+) field_issues=(\d+)", out)
        ok = rc == 0 and m and m.group(3) == "0" and m.group(4) == "0"
        self.record("G05", "PASS" if ok else "FAIL",
                    ["benchmark/releases/arena_v1/coverage_matrix.json"],
                    command="gen_coverage_matrix",
                    observed=out.splitlines()[0][:140] if out else "no output")
        return ok

    # ---------- G06a 划分/封存接口 ----------
    def g06a_split_seal(self):
        rc, out = run_tool([sys.executable, "-B", "-X", "utf8",
                            "tools/verify_seal_fixture.py"])
        # split_manifest 散列对账（只强制随包交付文件；unshipped_files=
        # 历史溯源登记如 logs/ 证据，不进发布包——R3 收口口径）
        mf = os.path.join(ROOT, "benchmark/releases/arena_v1/split_manifest.json")
        m = json.load(open(mf, encoding="utf-8"))
        ref = {f: h for fam in m.get("families", [])
               for f, h in fam.get("files", {}).items()
               if h != "MISSING"
               and f not in set(fam.get("unshipped_files") or [])}
        # R3#1 反例②同类：空 manifest 不是通过证明
        if not ref:
            self.record("G06a", "FAIL",
                        ["benchmark/releases/arena_v1/split_manifest.json"],
                        command="verify_seal_fixture + manifest hash audit",
                        observed="manifest files=0（空划分清单不得放行）")
            return False
        bad = [f for f, h in ref.items()
               if not os.path.isfile(os.path.join(ROOT, f))
               or sha_file(os.path.join(ROOT, f)) != h]
        ok = rc == 0 and not bad
        self.record("G06a", "PASS" if ok else "FAIL",
                    ["benchmark/releases/arena_v1/split_manifest.json"],
                    command="verify_seal_fixture + manifest hash audit",
                    observed="seal rc=%d; manifest files=%d bad=%d"
                             % (rc, len(ref), len(bad)))
        return ok

    # ---------- G06b 独立选择（外部缺件，如实报告） ----------
    def g06b_selection(self):
        mf = os.path.join(ROOT, "benchmark/releases/arena_v1/split_manifest.json")
        m = json.load(open(mf, encoding="utf-8"))
        sel = m.get("selection_set", {}) or {}
        status = sel.get("status", "NOT_READY")
        # R3#1 反例④：只填 status=READY 而无实际内容/保管与独立性证据 → 不得就绪
        has_content = bool(sel.get("case_refs") or sel.get("files")
                           or sel.get("families"))
        has_custody = bool(sel.get("custodian")) and bool(sel.get("independent"))
        if status == "READY" and not (has_content and has_custody):
            status = "NOT_READY"
        self.record("G06b", "NOT_READY" if status != "READY" else "PASS",
                    [os.path.relpath(mf, ROOT)],
                    observed="selection_set.status=%s content=%s custody=%s"
                             % (sel.get("status"), has_content, has_custody),
                    note="独立出题/保管身份未指定（外部缺件）——"
                         "不阻塞离线工程门禁，报告首页如实写明"
                         if status != "READY" else None)
        return status == "READY"

    # ---------- G07 评审闭环（V14：直接调用 rv2 内容验证器） ----------
    def g07_review_loop(self):
        W = os.path.join(ROOT, "benchmark/releases/arena_v1/review_loop")
        try:
            mp = json.load(open(os.path.join(W, "mapping_secret.json"),
                                encoding="utf-8"))
            spec = json.load(open(os.path.join(
                ROOT, "refs/arena_v1/review_drill_spec.json"),
                encoding="utf-8"))
        except Exception as e:
            self.record("G07", "FAIL", [], observed="missing artifacts: %s" % e)
            return False
        bad = []
        ver = mp.get("material_version") or spec.get("material_version", "rv1")
        mapping = mp.get("mapping", {})
        # V14 §6.2：不再采信自报 ingest_result 计数——调用同一内容验证器
        # （材料内容 vs 映射绑定、entry 复核、计数独立重算）。
        sys.path.insert(0, os.path.join(ROOT, "tools"))
        import review_loop  # noqa: E402
        vres = review_loop.verify_workdir(W)
        bad.extend(vres["issues"])
        # 篡改探针：把一份材料实际展示原文改一个字符再验——验证器必须报
        # 不一致（探针在临时副本上做，不动真实材料；漏报则 G07 FAIL）。
        tamper_detected = True
        mids = sorted(mapping)
        if mids:
            mid = mids[0]
            matp = os.path.join(W, "materials", mid + ".json")
            try:
                orig = open(matp, encoding="utf-8").read()
                mat = json.loads(orig)
                outs = mat.get("原样输出") or {}
                k0 = sorted(outs)[0] if outs else None
                if k0 and isinstance(outs[k0], str):
                    mat["原样输出"][k0] += "‖"
                    tampered = json.dumps(mat, ensure_ascii=False, indent=1)
                    import tempfile
                    with tempfile.TemporaryDirectory() as td:
                        tW = os.path.join(td, "review_loop")
                        shutil.copytree(W, tW)
                        open(os.path.join(tW, "materials", mid + ".json"),
                             "w", encoding="utf-8").write(tampered)
                        tver = review_loop.verify_workdir(tW)
                        tamper_detected = not tver["ok"]
                else:
                    tamper_detected = False
                    bad.append("tamper probe skipped: 材料结构不含 原样输出")
            except Exception as e:
                tamper_detected = False
                bad.append("tamper probe error: %s" % e)
        if not tamper_detected:
            bad.append("篡改探针未被检出——内容验证器失效")
        ok = not bad and vres.get("accepted", 0) >= 11 \
            and vres.get("rejected", 0) >= 1
        self.record("G07", "PASS" if ok else "FAIL",
                    ["refs/arena_v1/review_drill_spec.json",
                     "benchmark/releases/arena_v1/review_loop"],
                    command="review_loop.verify_workdir（内容绑定+计数重算）"
                            "+ 篡改探针",
                    observed="materials=%d ver=%s accepted=%d rejected=%d "
                             "tamper_detected=%s bad=%s"
                             % (len(mapping), ver, vres.get("accepted"),
                                vres.get("rejected"), tamper_detected,
                                bad[:3] or "none"))
        return ok

    # ---------- G08/G09 隔离矩阵（一次运行，两门禁共享；V14 严格判决） ----------
    def g08_g09_matrix(self):
        menv = None
        if self.sandbox_matrix:
            menv = dict(os.environ)
            menv["LB_SANDBOX"] = "1"
        rc, out = run_tool([sys.executable, "-B", "-X", "utf8",
                            "tools/test_isolation_matrix.py"],
                           timeout=1500, env=menv)
        # 证据绑定：只认子工具本次 stdout 声明的日志，且 mtime ≥ 本次 run 起点
        mm = re.search(r"ISOLATION MATRIX -> (\S+)", out)
        rel = mm.group(1).replace("\\", "/") if mm else None
        res = None
        if rel:
            p = os.path.join(ROOT, rel)
            if os.path.isfile(p) and os.path.getmtime(p) >= self.run_started:
                res = json.load(open(p, encoding="utf-8"))
        if res is None:
            tail = (out or "")[-300:].replace("\n", " | ")
            self.record("G08", "FAIL", [], command="test_isolation_matrix",
                        observed="无有效矩阵日志（rc=%d）tail=%s" % (rc, tail))
            self.record("G09", "FAIL", [], command="test_isolation_matrix",
                        observed="无有效矩阵日志（rc=%d）" % rc)
            return False
        self.matrix_log_rel = rel
        vs = res.get("verdicts", [])
        if not vs:
            self.record("G08", "FAIL", [rel], command="test_isolation_matrix",
                        observed="verdicts=[]（空检查结果不得放行）")
            self.record("G09", "FAIL", [rel], command="test_isolation_matrix",
                        observed="verdicts=[]（空检查结果不得放行）")
            return False
        # V14 执行书 C1：集合一致性 + 唯一性 + 任何 FAIL 不被吞
        # 2026-10-22 范围更正 v2：系统隔离非前置时，与"本地恶意代码"绑定的三项
        # （A2/A4b/RESTRICTED_ENV_ABSENT）允许终态 NOT_APPLICABLE；白名单之外
        # 的 NOT_APPLICABLE 仍是结构错误（防止有人把 FAIL 改成 N/A 蒙过去）。
        na_allowed = MATRIX_NOT_APPLICABLE if os_isolation_not_prerequisite() else set()
        by_id, dup = {}, []
        nonterminal = []
        for v in vs:
            cid = v.get("check")
            if cid in by_id:
                dup.append(cid)
            by_id[cid] = v.get("verdict")
            vd = v.get("verdict")
            if vd in ("PASS", "FAIL", "RESIDUAL_DOCUMENTED"):
                continue
            if vd == "NOT_APPLICABLE" and cid in na_allowed:
                continue
            nonterminal.append("%s:%s" % (cid, vd))
        expected_ids = (MATRIX_EXPECTED_PASS | MATRIX_RESIDUAL_ALLOWED
                        | MATRIX_KNOWN_ENV_FAIL)
        unknown = sorted(set(by_id) - expected_ids)
        missing = sorted(MATRIX_EXPECTED_PASS - set(by_id))
        fails_all = sorted(c for c, vd in by_id.items() if vd == "FAIL")
        known_env = sorted(c for c in fails_all if c in MATRIX_KNOWN_ENV_FAIL)
        unexpected_fail = sorted(c for c in fails_all
                                 if c not in MATRIX_KNOWN_ENV_FAIL)
        bad_residual = sorted(c for c, vd in by_id.items()
                              if vd == "RESIDUAL_DOCUMENTED"
                              and c not in MATRIX_RESIDUAL_ALLOWED)
        structural_bad = (dup or unknown or missing or nonterminal
                          or unexpected_fail or bad_residual)
        # 本模式边界（G08 新语义）：harness 可变、API 固定；回复只当数据、出站无工具通道。
        # 这一组不过，G08 不得 PASS（与矩阵结构并列的第二判据）。
        rc_b, out_b = run_tool([sys.executable, "-B", "-X", "utf8",
                                "tools/test_api_boundary.py"], timeout=900)
        m_b = re.search(r"api_candidate_boundary: (\d+)/(\d+) PASS", out_b)
        boundary_ok = bool(rc_b == 0 and m_b and m_b.group(1) == m_b.group(2))
        boundary_detail = ("%s/%s" % (m_b.group(1), m_b.group(2))
                           if m_b else "no-summary rc=%d" % rc_b)
        g08_ok = (rc == 0 and not structural_bad and boundary_ok)
        na_hit = sorted(c for c, vd in by_id.items() if vd == "NOT_APPLICABLE")
        self.record("G08", "PASS" if g08_ok else "FAIL",
                    [rel, "refs/arena_v1/SCOPE_DECISION_API_CANDIDATE_20261022.md",
                     "refs/arena_v1/scope.json", "tools/test_api_boundary.py"],
                    command="test_isolation_matrix + test_api_boundary",
                    observed="checks=%d expected=%d missing=%s unknown=%s "
                             "dup=%s unexpected_fail=%s not_applicable=%s "
                             "bad_residual=%s boundary=%s"
                             % (len(vs), len(MATRIX_EXPECTED_PASS),
                                missing[:3] or "none", unknown[:3] or "none",
                                dup[:3] or "none", unexpected_fail or "none",
                                na_hit or "none", bad_residual or "none",
                                boundary_detail),
                    note=("本模式：harness 可变、API 固定；OS 账户/防火墙部署"
                          "已撤回（非前置），边界判据=矩阵结构 + test_api_boundary"
                          if os_isolation_not_prerequisite() else
                          "旧模式（本地可执行候选）：矩阵须逐项 PASS"))
        # G09：A7/A8 子集 + 真实账本核验（V14：不能只查非空 note 字典）
        g9_ids = [c for c in by_id if c.startswith(("A7", "A8"))]
        g9_checks_ok = (g9_ids and all(by_id[c] == "PASS" for c in g9_ids)
                        and not dup)
        le = res.get("ledger_evidence") or {}
        ledger_path = os.path.join(ROOT, le.get("path", ""))
        ledger_ok = False
        ledger_detail = "absent"
        if le.get("path") and os.path.isfile(ledger_path):
            actual_sha = hashlib.sha256(open(ledger_path, "rb").read()) \
                .hexdigest()
            events = [json.loads(l) for l in open(ledger_path,
                                                  encoding="utf-8")
                      if l.strip()]
            kinds = [e.get("kind") for e in events]
            aids = [e.get("attempt_id") for e in events
                    if e.get("kind") == "attempt_started"]
            ledger_ok = (actual_sha == le.get("sha256")
                         and kinds.count("attempt_started")
                         == le.get("started")
                         and len(set(aids)) == len(aids)
                         and kinds.count("attempt_started")
                         == le.get("unique_attempt_ids"))
            ledger_detail = ("path=%s started=%d unique=%d sha_ok=%s"
                             % (le.get("path"), le.get("started"),
                                le.get("unique_attempt_ids"),
                                actual_sha == le.get("sha256")))
        g09_ok = g9_checks_ok and ledger_ok
        self.record("G09", "PASS" if g09_ok else "FAIL",
                    [le.get("path") or rel],
                    command="test_isolation_matrix (A7/A8) + 真实账本核验",
                    observed="A7/A8 all_pass=%s; ledger %s"
                             % (g9_checks_ok, ledger_detail))
        return g08_ok and g09_ok

    # ---------- G10 可复现发布与历史保护 ----------
    def g10_release_package(self):
        # R3#7 后：干净目录复验 = 完整 offline 入口（含嵌套矩阵+嵌套打包），
        # 600s 不够——放宽到 2400s。
        # 递归边界（R3 收口）：unpack 冒烟里跑的 verify 自身再调 G10 时，
        # 其打包冒烟降为 light（protocol+import），防止 verify→build→verify→build 无限嵌套。
        env = None
        if os.environ.get("ARENA_UNPACK_CONTEXT"):
            env = dict(os.environ, ARENA_SMOKE_MODE="light")
        rc, out = run_tool([sys.executable, "-B", "-X", "utf8",
                            "tools/build_release_package.py"], timeout=2400,
                           env=env)
        m = re.search(r"PACKAGE (PASS|FAIL) pkg=(\S+) unpack_verify=(\w+)", out)
        if rc == 0 and m and m.group(1) == "PASS" and m.group(3) == "OK":
            self.record("G10", "PASS", [m.group(2)],
                        command="build_release_package + clean-dir offline smoke",
                        observed=out.splitlines()[0][:140])
            return True
        self.record("G10", "FAIL", [], command="build_release_package",
                    observed=(out or "no output")[-200:])
        return False

    # ---------- 主流程 ----------
    def run_offline(self):
        print("ARENA V1 OFFLINE VERIFY — %s" % datetime.datetime.now().isoformat())
        print("mode=offline（真实上游适配器禁用；隔离矩阵走本地替身上游）")
        # 意外真实访问检测：若环境注入了真实端点则拒绝
        for ev in ("LOVEBRAIN_UPSTREAM", "OPENAI_API_KEY", "ANTHROPIC_API_KEY"):
            if os.environ.get(ev):
                self.record("G01", "FAIL", [],
                            observed="env %s present — 拒绝在含真实端点的环境离线验证" % ev)
                return False
        # R3#1：矩阵先跑——G03 的 A4 证据必须取自本次 run 的矩阵日志（反例⑤）
        self.g08_g09_matrix()
        self.g01_release_audit()
        self.g02_contract()
        self.g03_scoring()
        self.g04_baseline()
        self.g05_coverage()
        self.g06a_split_seal()
        self.g06b_selection()
        self.g07_review_loop()
        # G10 最后：打包并干净目录复验
        self.g10_release_package()
        return self.finalize()

    def run_integration(self):
        """V14 §6.3：本地替身演练单列 G11_STANDIN（不得冒充真实集成）。
        G11 只认可【当前冻结版本 + 已授权真实上游】的有限集成证据；
        没有真实执行不得 PASS；退出码与总状态必须一致。"""
        print("ARENA V1 INTEGRATION VERIFY — %s" % datetime.datetime.now().isoformat())
        auth = {}
        if os.path.isfile(AUTH_FILE):
            try:
                auth = json.load(open(AUTH_FILE, encoding="utf-8"))
            except Exception:
                auth = {}
        # ① G11_STANDIN：本地 OpenAI 兼容替身完整演练（离线工程证据）
        rc, out = run_tool([sys.executable, "-B", "-X", "utf8",
                            "tools/test_g11_standin.py"], timeout=1200)
        drill_ok = rc == 0 and "G11 STANDIN PASS" in out
        # 替身结果必须绑定当前 release（旧散列不能充当本版本证据）
        rel_sha = sha_file(self.release_path)
        bind_ok = False
        bind_detail = "result file absent"
        rp = os.path.join(ROOT, "logs", "g11_standin_result.json")
        if os.path.isfile(rp):
            try:
                rj = json.load(open(rp, encoding="utf-8"))
                bound = str(rj.get("release_sha256", ""))
                bind_ok = bound == rel_sha
                bind_detail = "result_sha=%s current=%s match=%s" % (
                    bound[:16], rel_sha[:16], bind_ok)
            except Exception as e:
                bind_detail = "unreadable: %s" % e
        standin_ok = drill_ok and bind_ok
        self.record("G11_STANDIN", "PASS" if standin_ok else "FAIL",
                    ["logs/g11_standin_result.json"],
                    command="test_g11_standin（本地替身，独立于 G11）",
                    observed="drill rc=%d; binding: %s" % (rc, bind_detail),
                    note="替身演练=工程通路证据；不构成真实集成")
        # ② G11：真实集成。授权（authorized+real_endpoint.authorized）缺失
        # 或无当前版本证据 → NOT_RUN（不是 PASS 也不是 FAIL 假象）
        real = auth.get("real_endpoint") or {}
        if not auth.get("authorized") or not real.get("authorized"):
            self.record("G11", "NOT_RUN", [os.path.relpath(AUTH_FILE, ROOT)],
                        observed="真实集成未获授权（authorized 缺失/false）",
                        note="真实批次只申请一次 ≤8 attempts；未授权前"
                             " G11 恒 NOT_RUN")
        else:
            # 有授权也必须有当前版本的真实执行证据文件，否则仍 NOT_RUN
            ev = real.get("evidence_file") or ""
            ev_ok = False
            if ev and os.path.isfile(os.path.join(ROOT, ev)):
                try:
                    evj = json.load(open(os.path.join(ROOT, ev),
                                         encoding="utf-8"))
                    ev_ok = str(evj.get("release_sha16", "")).startswith(
                        rel_sha[:16]) and int(evj.get("attempts", 0)) > 0
                except Exception:
                    ev_ok = False
            if ev_ok:
                self.record("G11", "PASS", [ev],
                            observed="当前版本真实集成证据在位")
            else:
                self.record("G11", "NOT_RUN", [ev or AUTH_FILE],
                            observed="授权在位但无当前版本真实执行证据",
                            note="批准后须实际执行并落盘证据（含 release 绑定）")
        self.finalize()
        g11 = self.gates.get("G11", {}).get("status")
        g11s = self.gates.get("G11_STANDIN", {}).get("status")
        overall = "ENGINEERING_READY" if (g11 == "PASS"
                                          and g11s == "PASS") else "NOT_READY"
        # 退出码一致性（V14 §6.3）：ENGINEERING_READY 才 0
        return 0 if overall == "ENGINEERING_READY" else 1

    def finalize(self):
        # 总状态判定（验收模板口径）
        offline_all = all(self.gates.get(g, {}).get("status") == "PASS"
                          for g in REQUIRED_OFFLINE)
        g11 = self.gates.get("G11", {}).get("status")
        g06b = self.gates.get("G06b", {}).get("status")
        if offline_all and g11 == "PASS" and g06b == "PASS":
            overall = "ARENA_V1_READY_HUMAN"
        elif offline_all and g11 == "PASS":
            overall = "ENGINEERING_READY"
        elif offline_all:
            overall = "OFFLINE_READY"
        else:
            overall = "NOT_READY"
        acceptance = {
            "document_type": "arena_v1_acceptance",
            "is_template": False,
            "run_id": os.path.basename(self.out),
            "generated_at": datetime.datetime.now().isoformat(),
            "mode": self.mode,
            "overall_status": overall,
            "evaluation_mode": "human_supervised",
            "separate_flags": {"AUTO_JUDGE_READY": False,
                               "PROMOTION_AUTHORIZED": False,
                               "MONSTER_VALIDATED": False},
            "gates": [self.gates[k] for k in sorted(self.gates)],
        }
        p = os.path.join(self.out, "ACCEPTANCE.json")
        json.dump(acceptance, open(p, "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
        self.write_report(overall)
        print("OVERALL: %s -> %s" % (overall, os.path.relpath(self.out, ROOT)))
        # 退出码：必要门禁集合全 PASS 才 0
        ok = all(self.gates.get(g, {}).get("status") == "PASS"
                 for g in REQUIRED_OFFLINE)
        return 0 if ok else 1

    def write_report(self, overall):
        lines = ["# ARENA V1 验收报告（%s）" % os.path.basename(self.out), ""]
        lines.append("模式：%s ｜ 生成时间：%s" % (
            self.mode, datetime.datetime.now().isoformat(timespec="seconds")))
        lines.append("")
        if overall in ("OFFLINE_READY",) or (
                overall in ("NOT_READY",) and
                self.gates.get("G06b", {}).get("status") != "PASS"):
            lines.append("> **独立选择未就绪（G06b）**：独立出题/保管身份未指定，"
                         "不得简称“完整考场建成”。")
            lines.append("")
        lines.append("总状态：**%s**" % overall)
        lines.append("")
        lines.append("| 门禁 | 状态 | 说明 |")
        lines.append("|---|---|---|")
        for k in sorted(self.gates):
            g = self.gates[k]
            lines.append("| %s | %s | %s |" % (
                k, g["status"], (g.get("note") or g.get("observed_result") or "")
                .replace("|", "/")[:120]))
        lines.append("")
        lines.append("口径：OFFLINE_READY = G01-G05、G06a、G07-G10 全 PASS；"
                     "ENGINEERING_READY = + G11；ARENA_V1_READY_HUMAN = 再 + G06b。")
        lines.append("Judge 自动裁判未启用；真实 B 线未开启；52 次候选对照另列未授权。")
        open(os.path.join(self.out, "ACCEPTANCE_REPORT.md"), "w",
             encoding="utf-8").write("\n".join(lines))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["offline", "integration"], default="offline")
    ap.add_argument("--release", default="refs/arena_v1/release.json")
    ap.add_argument("--out", default=None)
    ap.add_argument("--sandbox-matrix", action="store_true",
                    help="G08 部署后启用：隔离矩阵子进程带 LB_SANDBOX=1 "
                         "（宿主以沙箱账户运行）；其余门禁不受影响")
    a = ap.parse_args()
    out = a.out or os.path.join(
        ROOT, "logs", "arena_v1_acceptance",
        datetime.datetime.now().strftime("%Y%m%d_%H%M%S"))
    v = Verify(a.mode, a.release, out, sandbox_matrix=a.sandbox_matrix)
    if a.mode == "integration":
        return v.run_integration()
    return v.run_offline()


if __name__ == "__main__":
    sys.exit(main())
