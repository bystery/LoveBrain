# -*- coding: utf-8 -*-
"""test_verify_strict_negative.py — verify_arena_v1 严格证据绑定的负例自测。

R3#2（复核 §4 五反例全过）+ 正例对照。全部在临时沙箱副本上运行，
不触碰真实仓库工件、不触发 ACL、不调用任何子工具（run_tool 打桩）。

反例（全部必须被拒）：
  N1 隔离子工具 rc=9、不生成新日志，目录留旧成功日志 → G08/G09 败
  N2 子工具 rc=0 但最新日志 verdicts=[] → G08 败（空检查结果）
  N3 release 清单 files={} → G01 败（空发布证明）
  N4 独立选择只填 status=READY（无内容/无保管独立性证据）→ G06b 不就绪
  N5 日志 mtime 早于本次验收起点（旧证据）→ G08 败
  N6 矩阵缺必要检查项 → G08 败
  N7 非终态判定（NOT_RUN）→ G08 败
正例对照（必须通过）：
  P1 完整 23 项、全部终态、mtime 新鲜、ledger 非空 → G08/G09 PASS 且 A4 可用

退出码：0 = 全部负例被拒且正例通过；1 = 有反例漏网（验收器仍可被欺骗）。
"""
import hashlib
import json
import os
import shutil
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import verify_arena_v1 as V  # noqa: E402

MATRIX_CHECKS_PASS = sorted(V.MATRIX_EXPECTED_PASS)
FULL_CHECKS = ([{"check": c, "verdict": "PASS"} for c in MATRIX_CHECKS_PASS]
               + [{"check": "A6a_direct_socket",
                   "verdict": "RESIDUAL_DOCUMENTED"}])
LEDGER = {"attempts": 5, "unkeyed_refused": 3}


def make_log(sb, name, verdicts, ledger=LEDGER, attacker=None, age=None,
             ledger_evidence=None):
    d = os.path.join(sb, "logs")
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, name)
    doc = {"verdicts": verdicts, "upstream_ledger": ledger,
           "ledger_attacker": attacker if attacker is not None
           else {"attempt": 1}}
    if ledger_evidence:
        doc["ledger_evidence"] = ledger_evidence
    json.dump(doc, open(p, "w", encoding="utf-8"), ensure_ascii=False)
    if age is not None:
        old = time.time() - age
        os.utime(p, (old, old))
    return "logs/" + name


def make_real_ledger(sb):
    """V14 G09：合规场景需要真实账本文件（started==unique==completed+failed）。"""
    ctl = os.path.join(sb, "runs", "_atk2_network", "out", "_control")
    os.makedirs(ctl, exist_ok=True)
    lp = os.path.join(ctl, "attempt_ledger.jsonl")
    with open(lp, "w", encoding="utf-8") as f:
        for i in range(1, 4):
            f.write(json.dumps({"kind": "attempt_started",
                                "attempt_id": "iso_x_g%04d" % i,
                                "run_id": "iso_x"}) + "\n")
            f.write(json.dumps({"kind": "attempt_completed",
                                "attempt_id": "iso_x_g%04d" % i,
                                "run_id": "iso_x"}) + "\n")
    sha = hashlib.sha256(open(lp, "rb").read()).hexdigest()
    return {"path": os.path.relpath(lp, sb).replace("\\", "/"),
            "sha256": sha, "events": 6, "started": 3, "completed": 3,
            "failed": 0, "unique_attempt_ids": 3}


def new_verify(sb):
    v = V.Verify("offline", os.path.join(sb, "refs/arena_v1/release.json"),
                 os.path.join(sb, "out"))
    return v


def stub_tool(rc=0, stdout=""):
    # verify_arena_v1.run_tool 的真实签名带 env=（G08/G09 沙箱矩阵会传），
    # 桩不接就 TypeError → 整组负向门禁静默不跑，等于没测。
    return lambda cmd, timeout=600, env=None: (rc, stdout)


def main():
    # 沙箱必须与真实仓库同盘（os.path.relpath 跨盘符会抛 ValueError）
    base = os.path.join(V.ROOT, "logs")
    os.makedirs(base, exist_ok=True)
    sb = tempfile.mkdtemp(prefix="_verify_neg_", dir=base)
    real_root = V.ROOT
    V.ROOT = sb                      # 门禁内的相对路径全部解析进沙箱
    real_run_tool = V.run_tool
    os.makedirs(os.path.join(sb, "refs/arena_v1"), exist_ok=True)
    os.makedirs(os.path.join(sb, "benchmark/releases/arena_v1"), exist_ok=True)
    fails = []
    try:
        # ---------- N3: release files={} ----------
        json.dump({"files": {}}, open(
            os.path.join(sb, "refs/arena_v1/release.json"), "w",
            encoding="utf-8"))
        v = new_verify(sb)
        if v.g01_release_audit():
            fails.append("N3: files={} 仍 PASS")
        # 正常 release（供其余用例）
        json.dump({"files": {"a.txt": "x"}},
                  open(os.path.join(sb, "refs/arena_v1/release.json"), "w",
                       encoding="utf-8"))

        # ---------- N4: selection 只填 READY ----------
        json.dump({"families": [{"files": {"a.txt": "x"}}],
                   "selection_set": {"status": "READY"}},
                  open(os.path.join(sb, "benchmark/releases/arena_v1/split_manifest.json"),
                       "w", encoding="utf-8"))
        v = new_verify(sb)
        if v.g06b_selection():
            fails.append("N4: 空 selection 只填 READY 仍 PASS")

        # ---------- N1: rc=9 + 目录留旧成功日志 ----------
        v = new_verify(sb)
        old_log = make_log(sb, "isolation_matrix_OLD.json", FULL_CHECKS, age=3600)
        V.run_tool = stub_tool(rc=9, stdout="ISOLATION MATRIX -> %s" % old_log)
        if v.g08_g09_matrix():
            fails.append("N1: 子工具 rc=9 仍 PASS")

        # ---------- N5: rc=0 但日志是旧的 ----------
        v = new_verify(sb)
        V.run_tool = stub_tool(rc=0, stdout="ISOLATION MATRIX -> %s" % old_log)
        if v.g08_g09_matrix():
            fails.append("N5: 旧日志（mtime 早于起点）仍 PASS")

        # ---------- N2: 新日志但 verdicts=[] ----------
        v = new_verify(sb)
        empty_log = make_log(sb, "isolation_matrix_EMPTY.json", [])
        V.run_tool = stub_tool(rc=0, stdout="ISOLATION MATRIX -> %s" % empty_log)
        if v.g08_g09_matrix():
            fails.append("N2: verdicts=[] 仍 PASS")

        # ---------- N6: 缺必要检查项 ----------
        partial = [c for c in FULL_CHECKS if c["check"] != "A7_quota_ledger_unique"]
        v = new_verify(sb)
        part_log = make_log(sb, "isolation_matrix_PART.json", partial)
        V.run_tool = stub_tool(rc=0, stdout="ISOLATION MATRIX -> %s" % part_log)
        if v.g08_g09_matrix():
            fails.append("N6: 缺 A7 检查项仍 PASS")

        # ---------- N7: 非终态判定 ----------
        nt = FULL_CHECKS + [{"check": "EXTRA", "verdict": "NOT_RUN"}]
        v = new_verify(sb)
        nt_log = make_log(sb, "isolation_matrix_NT.json", nt)
        V.run_tool = stub_tool(rc=0, stdout="ISOLATION MATRIX -> %s" % nt_log)
        if v.g08_g09_matrix():
            fails.append("N7: NOT_RUN 非终态仍 PASS")

        # ---------- P1: 完整新鲜日志 → PASS 且 A4 可用 ----------
        v = new_verify(sb)
        le = make_real_ledger(sb)
        good_log = make_log(sb, "isolation_matrix_GOOD.json", FULL_CHECKS,
                            ledger_evidence=le)
        V.run_tool = stub_tool(rc=0, stdout="ISOLATION MATRIX -> %s" % good_log)
        if not v.g08_g09_matrix():
            fails.append("P1: 完整合规日志未通过（误杀）")
        else:
            if not v._matrix_a4():
                fails.append("P1a: A4 未从本次 run 日志取到")
            # N5'（G03 绑定）：无本次日志 → A4 必须不可用
            v2 = new_verify(sb)
            if v2._matrix_a4():
                fails.append("P1b: 未跑矩阵时 _matrix_a4 应为 False（反例⑤）")
            # 篡改：A4 变 FAIL → G03 权威判定须受影响
            bad_a4 = [dict(c) if c["check"] != "A5_forge_authority_ignored"
                      else {"check": "A5_forge_authority_ignored",
                            "verdict": "FAIL"} for c in FULL_CHECKS]
            bad_log = make_log(sb, "isolation_matrix_BADA4.json", bad_a4,
                               ledger_evidence=le)
            v3 = new_verify(sb)
            V.run_tool = stub_tool(rc=0, stdout="ISOLATION MATRIX -> %s" % bad_log)
            if v3.g08_g09_matrix():
                fails.append("P1c: A4=FAIL 仍 G08 PASS")
            # V14：G09 账本散列不符 → 必须失败
            le_bad = dict(le, sha256="0" * 64)
            bad_ledger = make_log(sb, "isolation_matrix_BADLEDGER.json",
                                  FULL_CHECKS, ledger_evidence=le_bad)
            v4 = new_verify(sb)
            V.run_tool = stub_tool(rc=0,
                                   stdout="ISOLATION MATRIX -> %s" % bad_ledger)
            g08, _ = v4.g08_g09_matrix(), None
            g9 = v4.gates.get("G09", {}).get("status")
            if g9 == "PASS":
                fails.append("P1d: 账本散列不符仍 G09 PASS")
    finally:
        V.ROOT = real_root
        V.run_tool = real_run_tool
        shutil.rmtree(sb, ignore_errors=True)

    if fails:
        print("NEGATIVE SELF-TEST FAIL (%d):" % len(fails))
        for f in fails:
            print("  -", f)
        return 1
    print("VERIFY STRICT NEGATIVE SELF-TEST PASS (7 反例全拒 + 正例对照通过)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
