# -*- coding: utf-8 -*-
"""tools/reconcile_call_accounting.py — W01-1A 恢复批调用对账与旧单收口（MAX-288 V3 FIRST_8H §A）。

只读分析两份原始事件源（预算账本 AUTH-P06-RECOVERY-20261027-01 + 网关事件
logs/p06_recovery_review_20261027/REVIEW_GATEWAY.jsonl），产出
CALL_ACCOUNTING_RECONCILIATION.json；随后按 FIRST_8H_WORK_ORDER §A 收口旧恢复单，
并在账本锁内追加 accounting_reconciliation 补充记录（逐事件行号+sha256，
不伪造任何历史事前预留）。

口径纪律：**物理开始数 = 网关 attempt_started 逐事件计数**（同一 attempt_id
多次开始各算一次）；distinct attempt_id 数只用于与预算预留对齐，不冒充物理计数。
脚本幂等可重跑：对账记录已存在且口径一致则不重复追加；此前若存在口径错误的
对账/收口记录，追加 accounting_reconciliation_correction 声明取代（append-only，
不删改任何历史行）。总管理账按物理开始数计（27），差额为同一预留内隐式重试
漏计，不退款、不补跑、已成功响应保留探索用。
"""
import hashlib
import json
import os
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "arena"))
from arena.isolation.authorization_budget import _FileLock

AID = "AUTH-P06-RECOVERY-20261027-01"
LEDGER = os.path.join(ROOT, "logs", "auth_budget", AID + ".jsonl")
GW = os.path.join(ROOT, "logs", "p06_recovery_review_20261027", "REVIEW_GATEWAY.jsonl")
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "max288_v3_20261028",
                   "CALL_ACCOUNTING_RECONCILIATION.json")


def load_events(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f, 1):
            s = line.strip()
            if not s:
                continue
            out.append({"line": i,
                        "sha256_16": hashlib.sha256(s.encode("utf-8")).hexdigest()[:16],
                        "event": json.loads(s)})
    return out


def kind_counts(events):
    c = {}
    for e in events:
        k = e["event"].get("kind")
        c[k] = c.get(k, 0) + 1
    return c


def started_by_id(events):
    d = {}
    for e in events:
        aid_ = e["event"].get("attempt_id")
        if aid_ and e["event"].get("kind") == "attempt_started":
            d.setdefault(aid_, []).append(e)
    return d


def all_by_id(events):
    d = {}
    for e in events:
        aid_ = e["event"].get("attempt_id")
        if aid_:
            d.setdefault(aid_, []).append(e)
    return d


def brief(events):
    return [{"line": e["line"], "sha256_16": e["sha256_16"],
             "kind": e["event"].get("kind"), "ts": e["event"].get("ts"),
             "pid": e["event"].get("pid")} for e in events]


def append_locked(rec):
    """锁内 append-only 追加一条记录（fsync），不触碰任何已有行。"""
    with _FileLock(LEDGER + ".lock"):
        with open(LEDGER, "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False, sort_keys=True) + "\n")
            f.flush()
            os.fsync(f.fileno())


def main():
    budget_ev = load_events(LEDGER)
    gw_ev = load_events(GW)

    b_started = started_by_id(budget_ev)
    g_started = started_by_id(gw_ev)
    b_all = all_by_id(budget_ev)
    g_all = all_by_id(gw_ev)

    reused = {}
    for aid_ in sorted(g_started):
        n = len(g_started[aid_])
        if n > 1:
            reused[aid_] = {"gateway_physical_starts": n,
                            "budget_reservations": len(b_started.get(aid_, [])),
                            "undercount_for_this_unit": n - len(b_started.get(aid_, [])),
                            "gateway_events": brief(g_all[aid_]),
                            "budget_events": brief(b_all.get(aid_, []))}
    gw_only = sorted(set(g_started) - set(b_started))
    budget_only = sorted(set(b_started) - set(g_started))
    # 物理 vs 逻辑：物理开始数=逐事件计数；distinct 数只用于对齐预留。
    physical_starts = sum(len(v) for v in g_started.values())
    logical_starts = len(b_started)
    extra = physical_starts - logical_starts

    recon = {
        "kind": "call_accounting_reconciliation",
        "date": "2026-10-28",
        "authorization_id": AID,
        "generated_by": "main-20261028-002629 (MAX-288 V3 FIRST_8H_WORK_ORDER §A)",
        "counting_rule": "物理开始数=网关 attempt_started 逐事件计数；distinct attempt_id "
                         "数只用于对齐预算预留（首版脚本曾把 distinct 当物理数，已在账本以 "
                         "correction 事件更正）",
        "sources": {
            "budget_ledger": LEDGER,
            "budget_ledger_lines": len(budget_ev),
            "gateway_events": GW,
            "gateway_event_lines": len(gw_ev),
        },
        "budget_logical_reservations": logical_starts,
        "budget_event_kinds": kind_counts(budget_ev),
        "gateway_physical_starts": physical_starts,
        "gateway_distinct_attempt_ids_with_starts": len(g_started),
        "gateway_event_kinds": kind_counts(gw_ev),
        "undercount_physical_vs_logical": extra,
        "reused_attempt_ids": reused,
        "gateway_attempt_ids_missing_from_budget": gw_only,
        "budget_attempt_ids_missing_from_gateway": budget_only,
        "management_account_physical_total": physical_starts,
        "disposition": {
            "old_scope": "停止派发；authorization_closed 收口退休，剩余额度不再使用",
            "no_fabrication": "不回填任何伪造的'请求前已预留'历史事件；漏计以 "
                              "accounting_reconciliation(_correction) 补充记录登记",
            "successful_responses": "已成功响应保留探索用，不因记账错误删除",
            "no_refund_no_makeup": "差额 %d 次按已消耗计（含超时），不退款、不换 ID 补跑" % extra,
        },
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(recon, f, ensure_ascii=False, indent=1)

    # ---- 账本补充/更正（append-only，幂等）----
    prior = [e for e in budget_ev
             if e["event"].get("kind") in ("accounting_reconciliation",
                                           "accounting_reconciliation_correction")]
    base = {"ts": time.strftime("%Y-%m-%d %H:%M:%S"),
            "authorization_id": AID,
            "budget_logical_reservations": logical_starts,
            "gateway_physical_starts": physical_starts,
            "gateway_distinct_attempt_ids": len(g_started),
            "undercount": extra,
            "reused_attempt_ids": sorted(reused),
            "source_json": os.path.relpath(OUT, ROOT).replace("\\", "/"),
            "session": "main-20261028-002629"}
    wrong_prior = [p for p in prior
                   if p["event"].get("kind") == "accounting_reconciliation"
                   and p["event"].get("undercount") != extra]
    if wrong_prior:
        rec = dict(base, kind="accounting_reconciliation_correction",
                   supersedes=[{"line": p["line"], "ts": p["event"].get("ts"),
                                "wrong_gateway_physical_starts":
                                    p["event"].get("gateway_physical_starts"),
                                "wrong_undercount": p["event"].get("undercount")}
                               for p in wrong_prior],
                   note="首版对账把 distinct attempt_id 数(%d)误当物理开始数写入 "
                        "accounting_reconciliation 与 authorization_closed 的 reason；"
                        "权威口径=物理 %d 次、漏计 %d（见本记录与 "
                        "CALL_ACCOUNTING_RECONCILIATION.json）。closed 状态有效，"
                        "额度按物理计。" % (len(g_started), physical_starts, extra))
        append_locked(rec)
        appended = "correction(取代 %d 条错误口径记录)" % len(wrong_prior)
    elif not prior:
        rec = dict(base, kind="accounting_reconciliation",
                   evidence="每事件行号+sha256_16 见 CALL_ACCOUNTING_RECONCILIATION.json；"
                            "本记录为补充登记，不伪造历史事前预留")
        append_locked(rec)
        appended = "reconciliation"
    else:
        appended = "already-correct(未重复追加)"

    # ---- 收口（幂等：已有 authorization_closed 则不再追加）----
    closed = [e for e in budget_ev if e["event"].get("kind") == "authorization_closed"]
    close_note = "already-closed(保持原事件；口径以 correction 为准)" if closed else None
    if not closed:
        crec = {"kind": "authorization_closed", "ts": time.strftime("%Y-%m-%d %H:%M:%S"),
                "authorization_id": AID,
                "reason": "W01-A 收口（V3 FIRST_8H §A）：预算 %d 逻辑预留 / 网关 %d 物理"
                          "开始（漏计 %d，%d 个 attempt_id 复用）；总管理账按物理 %d 计，"
                          "旧单退休不复用" % (logical_starts, physical_starts, extra,
                                             len(reused), physical_starts)}
        append_locked(crec)
        close_note = "closed(本次追加)"

    print("RECONCILIATION ok ->", os.path.relpath(OUT, ROOT))
    print("logical=%d physical=%d distinct=%d undercount=%d reused_ids=%d"
          % (logical_starts, physical_starts, len(g_started), extra, len(reused)))
    print("ledger_append:", appended, "| close:", close_note)


if __name__ == "__main__":
    main()
