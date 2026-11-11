# -*- coding: utf-8 -*-
"""BATCH-V5-CTX-20261025 收口：额度耗尽后关闭授权（唯一合法途径=既有
AuthorizationBudget.close，追加 authorization_closed；不删账本、不改历史行）。

前置（脚本内硬校验，任一不满足即拒关）：
  1) 账本 attempt_started == attempt_completed == 48 且 failed == 0；
  2) DONE 标记存在且 started/completed/failed 与账本一致；
  3) 尚未存在 authorization_closed（幂等：已关则只报告不重写）。
关闭后把 AUTHORIZATION.json 的 status 标为 CLOSED 并留原因与证据路径
（authorized 保持原批准原文不动，由 status + 账本关闭态共同阻止复用）。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
from arena.isolation.authorization_budget import AuthorizationBudget  # noqa: E402

AID = "AUTH-V5-CTX-20261025-01"
LEDGER = os.path.join(ROOT, "logs", "auth_budget", AID + ".jsonl")
DONE = os.path.join(ROOT, "logs", "v5_ctx_live_20261025.DONE.json")
AUTHDOC = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                       "v5_next_batch_20261025", "AUTHORIZATION.json")
CAP = 48


def read_jsonl(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                out.append(json.loads(line))
    return out


def main():
    recs = read_jsonl(LEDGER)
    kinds = {}
    for r in recs:
        kinds[r.get("kind")] = kinds.get(r.get("kind"), 0) + 1
    started = kinds.get("attempt_started", 0)
    completed = kinds.get("attempt_completed", 0)
    failed = kinds.get("attempt_failed", 0)

    done = json.load(open(DONE, encoding="utf-8"))
    trio_done = (done.get("attempt_started"), done.get("attempt_completed"),
                 done.get("attempt_failed"))
    if (started, completed, failed) != trio_done:
        raise SystemExit("拒绝关闭：账本 %s 与 DONE %s 不一致" % (
            (started, completed, failed), trio_done))
    if done.get("real_model_calls") != CAP or done.get("stopped") is not None \
            or done.get("boundary_problems"):
        raise SystemExit("拒绝关闭：DONE 非正常收口 %s" % json.dumps(
            {k: done.get(k) for k in ("real_model_calls", "stopped",
                                       "boundary_problems")},
            ensure_ascii=False))
    if (started, completed, failed) != (CAP, CAP, 0):
        raise SystemExit("拒绝关闭：账本不是 %d/%d/0失败，实为 %s" % (
            CAP, CAP, (started, completed, failed)))

    b = AuthorizationBudget(LEDGER, AID, CAP)
    if kinds.get("authorization_closed"):
        print("ALREADY_CLOSED summary=%s" % json.dumps(b.summary(),
                                                       ensure_ascii=False))
    else:
        reason = (
            "batch BATCH-V5-CTX-20261025 closed by trusted batch收口: "
            "48/48 attempts consumed (%d started/%d completed/%d failed), "
            "candidate=v5_purpose_aligned(身份 ef855510) vs "
            "reference_product_v1(6e65b6bc); 独立复算 verify_real_batch_v5.py "
            "failed_checks=[] (总token -57.65%%, completion -21.17%%, "
            "p50 -17.65%% 但 p95 +26.95%%, 累计等待 +0.26%%; "
            "空analysis遵守率 24/24); 额度耗尽,不得复活/覆盖/补跑。"
            % (started, completed, failed))
        b.close(reason)
        print("CLOSED summary=%s" % json.dumps(b.summary(), ensure_ascii=False))

    # 授权单状态标注（保留用户批准原文，只改状态字段并加收口证据）
    doc = json.load(open(AUTHDOC, encoding="utf-8"))
    doc["status"] = "CLOSED"
    doc["closed_reason"] = (
        "48/48 真实生成额度耗尽且 0 传输失败；批次已由 execute_plan 正常收口"
        "（logs/v5_ctx_live_20261025.DONE.json），按执行书 §6.1 关闭本授权。"
        "账本已追加 authorization_closed，本额度不可复活/挪用/补跑。")
    doc["closure_evidence"] = {
        "ledger": "logs/auth_budget/%s.jsonl" % AID,
        "done_marker": "logs/v5_ctx_live_20261025.DONE.json",
        "independent_recompute":
            "refs/experiment_protocol_20261023/v5_next_batch_20261025/"
            "V5_INDEPENDENT_RESULTS.json",
        "verdict":
            "refs/experiment_protocol_20261023/v5_next_batch_20261025/"
            "V5_VERDICT.md",
    }
    tmp = AUTHDOC + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, AUTHDOC)
    print("AUTHORIZATION.json status -> CLOSED (authorized 原文未改)")

    after = read_jsonl(LEDGER)
    tail = [r for r in after if r.get("kind") == "authorization_closed"]
    print("authorization_closed rows=%d" % len(tail))
    print(json.dumps(tail[-1], ensure_ascii=False)[:600])


if __name__ == "__main__":
    main()
