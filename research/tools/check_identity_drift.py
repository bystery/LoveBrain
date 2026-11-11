# -*- coding: utf-8 -*-
r"""tools/check_identity_drift.py — 身份漂移探测器（零调用）。

用途：对指定候选重算 R5.1 闭包身份，并与某份冻结记录逐文件比对。
F5 的教训是：E3 被直接写进了已冻结的 action_policy_r4_guard_final 目录，
事后才还原并另建 action_policy_r4_guard_e3。任何 in-place 改动本该在写之前
就被这个脚本挡住，所以它必须能在 CI/手工两条路径上跑。

退出码：0 = 一致；1 = 有漂移（列出差异文件）；2 = 记录里根本没有这个候选。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/check_identity_drift.py \
     --candidate action_policy_r4_guard_final \
     --freeze refs/experiment_protocol_20261023/final_mainline_review_20261029/FINAL_CANDIDATE_FREEZE_20261029.json
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/check_identity_drift.py \
     --candidate action_policy_r4_guard_e3 \
     --freeze refs/experiment_protocol_20261023/final_mainline_review_20261029/F5_CANDIDATE_FREEZE_20261029.json \
     --flat       # F5 记录是单候选扁平结构
"""
import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import api_trial                                          # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--candidate", required=True)
    ap.add_argument("--freeze", required=True)
    ap.add_argument("--flat", action="store_true",
                    help="冻结记录是 {identity_sha256, files} 扁平结构（F5 格式）")
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    rec = json.load(open(a.freeze, encoding="utf-8"))
    if a.flat:
        want = rec
    else:
        want = (rec.get("candidates") or {}).get(a.candidate)
        if want is None:
            print("NO_RECORD for %s in %s" % (a.candidate, a.freeze))
            return 2

    cur = api_trial.harness_identity(a.candidate)
    added = sorted(set(cur["files"]) - set(want["files"]))
    removed = sorted(set(want["files"]) - set(cur["files"]))
    changed = sorted(k for k in set(cur["files"]) & set(want["files"])
                     if cur["files"][k] != want["files"][k])
    report = {
        "kind": "identity_drift_check",
        "candidate": a.candidate,
        "freeze_file": os.path.relpath(a.freeze, ROOT).replace("\\", "/"),
        "frozen_identity_sha256": want["identity_sha256"],
        "current_identity_sha256": cur["identity_sha256"],
        "identical": (cur["identity_sha256"] == want["identity_sha256"]
                      and not added and not removed and not changed),
        "files_added": added, "files_removed": removed,
        "files_changed": [{"file": k, "frozen": want["files"][k],
                            "current": cur["files"][k]} for k in changed],
    }
    print(json.dumps(report, ensure_ascii=False, indent=1))
    return 0 if report["identical"] else 1


if __name__ == "__main__":
    sys.exit(main())
