#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_posttest_event_pairs.py — 第八波判决 §3 验收：后测身份验证 = 真实配对拒绝条件。

反例（此前全部被错误标成 verified，现必须拒绝并落 rejection.json）：
  N1 空事件日志            → 拒绝（coverage 缺失）
  N2 只有 started 无终态    → 拒绝（配对不完整）
  N3 事件全部属于错误 case  → 拒绝（覆盖缺失 + 范围外组）
  N4 restore-only（事件全属旧 run_id，manifest 正确）→ 拒绝（不得拿旧记录补足）
  N5 配对终态=failed        → 拒绝且失败样本保留于 rejection.json（不删除、不悄悄重试）
  N6 manifest.audit_errors 非空 → 拒绝（审计写失败不得当作已验证成功）
  N7 多余组（t5/t6 之外多出其他 case 的本 run 组）→ 拒绝
正例：
  P1 两节点四条事件（t5 started/completed + t6 started/completed）→ 通过，返回 2。
全部离线（构造假分支目录，无任何真实模型调用）。
"""
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
os.chdir(ROOT)

import l3b_frozen_fork as fork  # noqa: E402

RUN = "l3bfork_test/A_full"
TS0 = "2026-10-20 10:00:00"


def _ev(kind, aid, case, ts, run_id=RUN):
    return {"kind": kind, "attempt_id": aid, "run_id": run_id, "case_id": case,
            "pid": 123, "ts": ts}


def _manifest(td, run_id=RUN, audit_errors=None, gateway_calls=1):
    os.makedirs(td, exist_ok=True)
    with open(os.path.join(td, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump({"case_id": os.path.basename(td), "run_id": run_id,
                   "audit_errors": audit_errors or [],
                   "gateway_calls": gateway_calls}, f)


def _branch(tmp, name, events, run_id=RUN, audit_errors=None, gateway_calls=1):
    """构造假分支目录：t5/t6 manifest + gateway_events.jsonl。"""
    out_b = os.path.join(tmp, name)
    for i, cid in ((5, "L3B_t05"), (6, "L3B_t06")):
        _manifest(os.path.join(out_b, "turn_%02d_%s" % (i, cid)),
                  run_id=run_id, audit_errors=audit_errors,
                  gateway_calls=gateway_calls)
    if events is not None:
        with open(os.path.join(out_b, "gateway_events.jsonl"), "w",
                  encoding="utf-8") as f:
            for e in events:
                f.write(json.dumps(e, ensure_ascii=False) + "\n")
    return out_b


def _branch_events_rerun(out_b, old_run_id):
    """把已建分支的事件日志改写为旧 run_id（模拟 restore-only：无本 run 事件）。"""
    p = os.path.join(out_b, "gateway_events.jsonl")
    evs = [json.loads(ln) for ln in open(p, encoding="utf-8") if ln.strip()]
    with open(p, "w", encoding="utf-8") as f:
        for e in evs:
            e["run_id"] = old_run_id
            f.write(json.dumps(e, ensure_ascii=False) + "\n")
    return out_b


def _positive_events():
    return [
        _ev("attempt_started", 1, "L3B_t05", TS0),
        _ev("attempt_completed", 1, "L3B_t05", "2026-10-20 10:00:01"),
        _ev("attempt_started", 2, "L3B_t06", "2026-10-20 10:01:00"),
        _ev("attempt_completed", 2, "L3B_t06", "2026-10-20 10:01:02"),
    ]


def main():
    fails = []
    tmp = tempfile.mkdtemp(prefix="lb_posttest_pairs_")

    def expect_reject(tag, out_b, want_substr):
        run_dir = os.path.join(tmp, "run_" + tag)
        os.makedirs(run_dir, exist_ok=True)
        log = fork.ForkLog(os.path.join(run_dir, "fork_log.jsonl"))
        try:
            fork._verify_posttest_identity(out_b, "X_full", RUN, log, run_dir)
            fails.append("%s: 未拒绝（应 SystemExit）" % tag)
            return None
        except SystemExit:
            pass
        rj_p = os.path.join(run_dir, "rejection.json")
        if not os.path.isfile(rj_p):
            fails.append("%s: 未落 rejection.json" % tag)
            return None
        rj = json.load(open(rj_p, encoding="utf-8"))
        if not rj.get("rejected"):
            fails.append("%s: rejection.json rejected≠True" % tag)
        if want_substr and want_substr not in json.dumps(rj, ensure_ascii=False):
            fails.append("%s: rejection 未含关键原因 %r: %s"
                         % (tag, want_substr, rj.get("reason", "")[:200]))
        return rj

    # ---- N1 空事件日志 ----
    out_b = _branch(tmp, "n1_empty", [])
    expect_reject("N1", out_b, "attempt 组数=0")

    # ---- N2 只有 started，无终态 ----
    out_b = _branch(tmp, "n2_orphan", [
        _ev("attempt_started", 1, "L3B_t05", TS0),
        _ev("attempt_started", 2, "L3B_t06", "2026-10-20 10:01:00")])
    expect_reject("N2", out_b, "应恰 1 started + 1 终态")

    # ---- N3 事件全部属于错误 case（run_id 正确） ----
    out_b = _branch(tmp, "n3_othercase", [
        _ev("attempt_started", 1, "OTHER", TS0),
        _ev("attempt_completed", 1, "OTHER", "2026-10-20 10:00:01"),
        _ev("attempt_started", 2, "OTHER", "2026-10-20 10:01:00"),
        _ev("attempt_completed", 2, "OTHER", "2026-10-20 10:01:02")])
    expect_reject("N3", out_b, "后测范围外")

    # ---- N4 restore-only：manifest 冒充本次（run_id 正确），事件全属旧 run_id ----
    out_b = _branch(tmp, "n4_restore", _positive_events(),
                    run_id=RUN)
    out_b = _branch_events_rerun(out_b, "old_run/prehistory")
    expect_reject("N4", out_b, "attempt 组数=0")

    # ---- N5 配对终态=failed（失败样本保留于 rejection.json） ----
    out_b = _branch(tmp, "n5_failed", [
        _ev("attempt_started", 1, "L3B_t05", TS0),
        _ev("attempt_failed", 1, "L3B_t05", "2026-10-20 10:00:05"),
        _ev("attempt_started", 2, "L3B_t06", "2026-10-20 10:01:00"),
        _ev("attempt_completed", 2, "L3B_t06", "2026-10-20 10:01:02")])
    rj = expect_reject("N5", out_b, "失败样本")
    if rj is not None:
        raw = json.dumps(rj.get("checks", {}).get("raw_attempt_events", []),
                         ensure_ascii=False)
        if "attempt_failed" not in raw:
            fails.append("N5: 失败事件未保留于 rejection.json（不得删除/悄悄重试）")

    # ---- N6 manifest.audit_errors 非空 ----
    out_b = _branch(tmp, "n6_audit", _positive_events(),
                    audit_errors=["OSError: disk full"])
    expect_reject("N6", out_b, "审计事件写入失败")

    # ---- N7 多余组：t5/t6 各一组之外多出其他 case 的本 run 组 ----
    out_b = _branch(tmp, "n7_stray", _positive_events() + [
        _ev("attempt_started", 3, "L3B_t07", "2026-10-20 10:02:00"),
        _ev("attempt_completed", 3, "L3B_t07", "2026-10-20 10:02:01")])
    expect_reject("N7", out_b, "后测范围外")

    # ---- P1 合法正例：两节点四条事件 ----
    out_b = _branch(tmp, "p1_ok", _positive_events())
    run_dir = os.path.join(tmp, "run_P1")
    os.makedirs(run_dir, exist_ok=True)
    log = fork.ForkLog(os.path.join(run_dir, "fork_log.jsonl"))
    try:
        n = fork._verify_posttest_identity(out_b, "A_full_p1", RUN, log, run_dir)
    except SystemExit as e:
        fails.append("P1: 合法正例被拒绝: %s" % e)
    else:
        if n != 2:
            fails.append("P1: 返回 %r ≠ 2" % n)
        if os.path.isfile(os.path.join(run_dir, "rejection.json")):
            fails.append("P1: 正例误落 rejection.json")

    # ---- 跨 case 配对（started t5 / completed 挂 t6 的 case_id）----
    out_b = _branch(tmp, "n8_crosscase", [
        _ev("attempt_started", 1, "L3B_t05", TS0),
        _ev("attempt_completed", 1, "L3B_t06", "2026-10-20 10:00:01"),
        _ev("attempt_started", 2, "L3B_t06", "2026-10-20 10:01:00"),
        _ev("attempt_completed", 2, "L3B_t05", "2026-10-20 10:01:02")])
    expect_reject("N8", out_b, "跨 case 配对")

    if fails:
        print("FAIL %d 项：" % len(fails))
        for f in fails:
            print("  -", f)
        raise SystemExit(1)
    print("PASS：后测事件配对真实拒绝条件——空日志/孤立started/错误case/restore-only/"
          "failed对(保留)/audit_errors/多余组/跨case 全拒绝并落 rejection.json，"
          "合法两节点四事件正例通过")


if __name__ == "__main__":
    main()
