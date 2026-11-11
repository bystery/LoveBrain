# -*- coding: utf-8 -*-
"""V3 W01-1A 单次派发护栏离线证明（0 网络、0 真实模型）。

按 FIRST_8H_WORK_ORDER §A 末条一次验证三条常用链路：
1. 假网关超时 → 实际只发一次、预算记一次（无隐式重试；超时也消耗）；
2. 同 scope 重复派发同一逻辑单元被拒（filter_units：已开始单元不重派；
   同批重复单元直接拒绝；nonce 不构成重派许可）；
3. 已有成功样本身份不误杀（正常响应 → attempt_completed，validate 通过，
   raw 完整保存 + raw_display 截断展示）。
假适配器记录实际调用次数，不以返回 ok 作证。
"""
import json
import os
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import run_p06_recovery_review as drv
from arena.isolation.authorization_budget import AuthorizationBudget

# ModelGateway 构造需要该环境变量；本测试随后用 FakeGateway 替换 inner，0 真实网络
os.environ.setdefault("LB_API_BASE", "http://127.0.0.1:17770/v1")

SCOPE = {"experiment": "single_dispatch_guard_test"}


class FakeGateway(object):
    """假适配器：记录每次实际 generate 调用；不以返回 ok 作证。"""

    def __init__(self, behavior):
        self.behavior = behavior  # "timeout" | "ok"
        self.calls = []

    def generate(self, sys_p, usr, model=None, temperature=None, timeout_s=None,
                 tag=None, attempt_id=None):
        self.calls.append({"attempt_id": attempt_id, "model": model,
                           "t": time.time()})
        if self.behavior == "timeout":
            raise TimeoutError("timed out")
        u = json.loads(usr)
        return {"text": json.dumps({"item_id": u["item_id"],
                                    "left": [3] * len(u["dimensions"]),
                                    "right": [3] * len(u["dimensions"]),
                                    "issues": [], "na": []}, ensure_ascii=False),
                "usage": {"prompt_tokens": 1, "completion_tokens": 1,
                          "total_tokens": 2}}


def make_unit(item_id="TST1-facts"):
    return {"item_id": item_id, "profile": "facts",
            "dimensions": ["factuality", "attribution"],
            "instruction": "x", "evidence": {},
            "answers": {"left": "a", "right": "b"}, "model": "dsf"}


def ledger_kinds(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                out.append(json.loads(line)["kind"])
    return out


def main():
    results = []
    tmp = tempfile.mkdtemp(prefix="w01a_guard_")
    ledger = os.path.join(tmp, "AUTH-TEST-GUARD.jsonl")

    # ---- 1) 超时：实际只发一次、预算记一次 ----
    budget = AuthorizationBudget(ledger, "AUTH-TEST-GUARD", 10, scope=SCOPE)
    rev = drv.Reviewer(budget, os.path.join(tmp, "gw.jsonl"), "ep1")
    rev.inner = FakeGateway("timeout")
    r1 = rev.run(make_unit(), timeout_s=1)
    kinds1 = ledger_kinds(ledger)
    ok1 = (len(rev.inner.calls) == 1 and r1["ok"] is False
           and kinds1.count("attempt_started") == 1
           and kinds1.count("attempt_failed") == 1
           and kinds1.count("attempt_completed") == 0)
    results.append(("timeout_single_dispatch", ok1, {
        "gateway_calls": len(rev.inner.calls), "result_ok": r1["ok"],
        "ledger_kinds": kinds1}))

    # ---- 2) 重复派发被拒 ----
    budget2 = AuthorizationBudget(ledger, "AUTH-TEST-GUARD", 10, scope=SCOPE)
    rev2 = drv.Reviewer(budget2, os.path.join(tmp, "gw.jsonl"), "ep2")
    rev2.inner = FakeGateway("ok")
    r2 = rev2.run(make_unit(), timeout_s=1)  # 同一单元在新 attempt 下成功一次
    dup_rejected = False
    try:
        drv.filter_units([make_unit(), make_unit()], ledger, "AUTH-TEST-GUARD")
    except SystemExit:
        dup_rejected = True
    dispatch, consumed = drv.filter_units([make_unit()], ledger, "AUTH-TEST-GUARD")
    ok2 = dup_rejected and len(dispatch) == 0 and len(consumed) == 1 \
        and consumed[0]["unknown_consumed"] is True
    results.append(("duplicate_unit_refused", ok2, {
        "dup_batch_rejected": dup_rejected, "dispatch_after_success": len(dispatch),
        "consumed": consumed}))

    # ---- 3) 成功样本身份不误杀 ----
    kinds3 = ledger_kinds(ledger)
    ok3 = (r2["ok"] is True and r2.get("why") == "ok"
           and len(rev2.inner.calls) == 1
           and kinds3.count("attempt_completed") == 1
           and isinstance(r2.get("raw"), str) and r2["raw"].strip()
           and len(r2["raw_display"]) <= 1500
           and r2["raw"].startswith(r2["raw_display"][:50][:len(r2["raw_display"])])
           if len(r2["raw"]) > 50 else r2["raw"] == r2["raw_display"])
    results.append(("valid_response_preserved", bool(ok3), {
        "ok": r2["ok"], "why": r2.get("why"), "gateway_calls": len(rev2.inner.calls),
        "raw_len": len(r2.get("raw") or ""), "has_raw_display": "raw_display" in r2}))

    # ---- 汇总 ----
    all_ok = all(ok for _, ok, _ in results)
    for name, ok, detail in results:
        print("%s %s %s" % ("PASS" if ok else "FAIL", name,
                            json.dumps(detail, ensure_ascii=False)))
    print("SINGLE_DISPATCH_GUARD", "ALL_PASS" if all_ok else "HAS_FAIL")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
