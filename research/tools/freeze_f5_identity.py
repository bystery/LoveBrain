# -*- coding: utf-8 -*-
r"""tools/freeze_f5_identity.py — 冻结 F5 修复身份 action_policy_r4_guard_e3。

落盘内容：
  - 新身份 identity_sha256 与逐文件散列（R5.1 闭包口径）；
  - 冻结基线 action_policy_r4_guard_final 已逐字节还原的校验（重算身份必须相等）；
  - 与基线的**唯一代码差异**（vendored output_repair.py 的 E3）；
  - 代码来源证明：新身份的代码/资产文件与 F5 实跑时的 staged 副本**逐字节相同**
    （所以那 12 次付费复测的证据可以直接归属到这个新身份）；
  - 修复生效与无回归的离线/在线证据路径。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/freeze_f5_identity.py \
      --staged logs/f5_recheck_live2_20261029_133653/_iso_run_candidate_N01/staged
"""
import argparse
import datetime as dt
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import api_trial                                          # noqa: E402

BASE = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                    "final_mainline_review_20261029")
OLD = "action_policy_r4_guard_final"
NEW = "action_policy_r4_guard_e3"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--staged", required=True,
                    help="F5 实跑的 _iso_run_*/staged 目录（内含 candidates/...）")
    ap.add_argument("--out", default=os.path.join(BASE, "F5_CANDIDATE_FREEZE_20261029.json"))
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    frz = json.load(open(os.path.join(BASE, "FINAL_CANDIDATE_FREEZE_20261029.json"),
                         encoding="utf-8"))
    frozen_old = frz["candidates"][OLD]
    old_now = api_trial.harness_identity(OLD)
    if old_now["identity_sha256"] != frozen_old["identity_sha256"]:
        raise SystemExit("REFUSE: 冻结基线 %s 未还原到 9b8f4e…，不得发布新身份"
                         % OLD)
    new = api_trial.harness_identity(NEW)

    staged_root = a.staged if os.path.isabs(a.staged) else os.path.join(ROOT, a.staged)
    prefix = "candidates/" + NEW + "/"
    code_rows = []
    for rel, h in sorted(new["files"].items()):
        if not rel.startswith(prefix):
            continue                      # 只核本候选自己的文件（祖先文件另由身份闭包保证）
        if "/mechanism/" not in rel and "/assets/" not in rel:
            continue
        sub = rel[len(prefix):]
        staged_path = os.path.join(staged_root, "candidates", OLD,
                                   sub.replace("/", os.sep))
        sh = hashlib.sha256(open(staged_path, "rb").read()).hexdigest() \
            if os.path.isfile(staged_path) else None
        code_rows.append({"file": "candidates/%s/%s" % (NEW, sub),
                          "e3_sha256": h,
                          "f5_live_recheck_staged_sha256": sh,
                          "identical": sh == h})
    all_identical = bool(code_rows) and all(r["identical"] for r in code_rows)

    assets_rel = [k for k in new["files"]
                  if k.startswith("candidates/" + NEW + "/assets/")]
    assets_same = bool(assets_rel) and all(
        new["files"][k] == frozen_old["files"][k.replace(NEW, OLD)]
        for k in assets_rel)

    diff = {}
    for k, v in sorted(new["files"].items()):
        if not k.startswith(prefix):
            continue
        ok_old = k.replace(NEW, OLD)
        if ok_old in frozen_old["files"] and frozen_old["files"][ok_old] != v:
            diff[k] = {"frozen_base_sha256": frozen_old["files"][ok_old],
                       "e3_sha256": v}
        elif ok_old not in frozen_old["files"]:
            diff[k] = {"frozen_base_sha256": None, "e3_sha256": v,
                       "note": "基线身份中没有这个路径（新候选新增文件）"}

    doc = {
        "kind": "f5_candidate_identity_freeze",
        "generated_at": dt.datetime.now().strftime("%Y%m%d_%H%M%S"),
        "candidate": NEW,
        "identity_sha256": new["identity_sha256"],
        "closure": new["closure"],
        "file_count": len(new["files"]),
        "files": new["files"],
        "frozen_base_identity_sha256": frozen_old["identity_sha256"],
        "frozen_base_recomputed_ok": True,
        "frozen_base_note": "candidates/%s 已按 FINAL_CANDIDATE_FREEZE_20261029.json "
                            "逐字节还原，重算身份与记录一致。" % OLD,
        "delta_from_frozen_base": diff,
        "prompt_assets_identical_to_base": assets_same,
        "code_byte_provenance": {
            "f5_live_recheck_staging": os.path.relpath(staged_root, ROOT).replace("\\", "/"),
            "files_checked": len(code_rows),
            "all_identical": all_identical,
            "detail": code_rows,
            "meaning": "新身份的机制/资产文件与 F5 那 12 次付费复测实际加载的副本逐字节相同，"
                       "因此复测证据可直接归属到本身份。",
        },
        "evidence_of_effect": {
            "offline_replay": "F5_REPLAY.json（72 份已付费候选答卷重放：E3 命中 1、"
                              "harness 文本改动 1、非 E3 改动 0、修复后仍不可解析 0）",
            "historical_parity": "tools/test_output_repair_parity.py：260 份历史 raw，"
                                 "old_sendable 246 / new_sendable 247 / regressions 0",
            "guard_unit_test": "tools/test_final_guard_candidate.py：FINAL_GUARD_PASS 9/9",
            "live_recheck": "logs/f5_recheck_live2_20261029_133653（N01 全 12 轮真实重跑，"
                            "12/12 协议可交付，额度 12/12）",
            "blind_recheck": "logs/f5_judge_20261029_134800（12 对同位重抽盲评，schema 12/12）",
        },
        "caution": "同臂重抽盲评只回答“换身份重跑有没有系统性变差”，不是组件收益；"
                   "两侧本是同一 harness 的两次独立抽样，且 hy3 自动分不作最终裁决。",
    }
    json.dump(doc, open(a.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("WROTE", os.path.relpath(a.out, ROOT).replace("\\", "/"))
    print("e3 identity       =", doc["identity_sha256"])
    print("frozen base ok    =", doc["frozen_base_recomputed_ok"])
    print("prompt assets same=", assets_same)
    print("code provenance   =", len(code_rows), "files, all_identical =", all_identical)
    print("delta from base   =", list(diff))
    return 0 if all_identical and assets_same else 1


if __name__ == "__main__":
    sys.exit(main())
