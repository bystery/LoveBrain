# tools/test_identity_closure.py — R5.1 反例：
#   换父本字节 → v2 identity 必须变；
#   只改 v2 目录 → v2 identity 也要变（旧行为保留）；
#   closure 序列 = [v2, parent]；
#   v2 files ⊇ parent files。
import hashlib
import importlib.util
import json
import os
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))


def _load_api_trial():
    os.environ.setdefault("DSF_API_KEY", "")
    spec = importlib.util.spec_from_file_location(
        "api_trial_idtest", os.path.join(ROOT, "tools", "api_trial.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


@check
def r51_closure_orders_self_then_parent():
    at = _load_api_trial()
    info = at.harness_identity("v2_recall")
    assert info["closure"] == ["v2_recall", "reference_product_v1"], \
        "R5.1 违约：closure 应 [self, parent]，实得 %s" % info["closure"]
    return "R51.closure_order PASS %s" % info["closure"]


@check
def r51_parent_files_included_in_v2_manifest():
    at = _load_api_trial()
    v2 = at.harness_identity("v2_recall")["files"]
    par = at.harness_identity("reference_product_v1")["files"]
    missing = [k for k in par if k not in v2]
    assert not missing, "R5.1 违约：v2 files 未包含父本文件 %s" % missing[:3]
    # 父本文件散列必须在 v2 里一致
    mismatch = [k for k in par if par[k] != v2[k]]
    assert not mismatch, "R5.1 违约：父本文件在两个 bundle 里散列不一致 %s" % mismatch[:3]
    return "R51.parent_in_closure PASS (parent=%d merged=%d)" % (len(par), len(v2))


@check
def r51_tampering_parent_flips_v2_identity():
    """把父本 mechanism.py 临时追加一行注释 → v2 identity 必须变。
    这条是 reviewer R5.1 判决的核心反例：v2 目录里的 mechanism.py
    只是 `import base`，父本任何改动都应被 v2 identity 感知。"""
    at = _load_api_trial()
    target = os.path.join(ROOT, "candidates", "reference_product_v1",
                          "mechanism", "mechanism.py")
    original = open(target, "rb").read()
    before = at.harness_identity("v2_recall")["identity_sha256"]
    try:
        with open(target, "ab") as f:
            f.write(b"\n# R5.1 identity-closure probe; will be reverted\n")
        after = at.harness_identity("v2_recall")["identity_sha256"]
    finally:
        with open(target, "wb") as f:
            f.write(original)
    assert before != after, \
        "R5.1 违约：父本字节变了但 v2 identity 没变（旧 bug 未修）"
    # 恢复后必须回到原值
    restored = at.harness_identity("v2_recall")["identity_sha256"]
    assert restored == before, \
        "R5.1 恢复不到位：restored=%s before=%s" % (restored[:12], before[:12])
    return "R51.parent_tamper_flips_identity PASS (before=%s… after=%s…)" % (
        before[:12], after[:12])


@check
def r51_tampering_child_flips_own_identity():
    at = _load_api_trial()
    target = os.path.join(ROOT, "candidates", "v2_recall",
                          "mechanism", "mechanism.py")
    original = open(target, "rb").read()
    before = at.harness_identity("v2_recall")["identity_sha256"]
    try:
        with open(target, "ab") as f:
            f.write(b"\n# R5.1 child tamper\n")
        after = at.harness_identity("v2_recall")["identity_sha256"]
    finally:
        with open(target, "wb") as f:
            f.write(original)
    assert before != after, "改候选自身 identity 竟不变"
    return "R51.child_tamper PASS"


@check
def r51_v2_params_declares_role_candidate_with_parent():
    p = os.path.join(ROOT, "candidates", "v2_recall", "params.json")
    import json
    doc = json.load(open(p, encoding="utf-8"))
    assert doc.get("role") == "candidate", \
        "R5.1 违约：v2 params.role 应为 candidate，实得 %s" % doc.get("role")
    assert doc.get("parent") == "reference_product_v1", \
        "R5.1 违约：v2 params.parent 应指向 reference_product_v1"
    return "R51.params PASS role=%s parent=%s" % (doc["role"], doc["parent"])


@check
def r51_baseline_self_closure():
    """reference_product_v1 自身 params.role=reference_baseline 无 parent → closure=[self]"""
    at = _load_api_trial()
    info = at.harness_identity("reference_product_v1")
    assert info["closure"] == ["reference_product_v1"], \
        "父本 closure 不应含其他候选：%s" % info["closure"]
    return "R51.baseline_self_only PASS"


def main():
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-45s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-45s %s: %s" % (fn.__name__, type(e).__name__, e))
    out = os.path.join(ROOT, "logs", "identity_closure_result.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump({"checks": results, "failed": failed},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("IDENTITY CLOSURE RESULT: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
