# -*- coding: utf-8 -*-
r"""tools/prove_prompt_identity.py — 证明跨批条件没有漂移（零调用）。

用途：R3A 的父臂是 action_policy_r2，而 D1 批的候选臂也是 action_policy_r2，且两批用**同一份
题面与卡**。因此对同一 case_id，两批的出站 user 提示应当逐字节相同——如果相同，就证明
"R3A 的修复前对照"与"D1 的已付费答卷"处在同一条件下，跨批引用才站得住；如果不同，说明
有装配漂移，必须查清再说话。

同时比对：system 提示（候选侧资产）、prompt_chars、以及 manifest 里的 case_input_sha256。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/prove_prompt_identity.py \
    --a logs/p08_d1_live_20261028/candidate_D01 --b logs/p08_r3a_live_20261028/parent_D01 \
    [--a logs/p08_s2r_live_20261028/candidate_AK09 --b logs/p08_r3a_live_20261028/parent_AK09 ...]
本工具按 `--pair A_dir=B_dir` 可多次传入；每个目录下应有 out/turn_*/calls.json。
输出 JSON；stdout 只打 ASCII 摘要。
"""
import argparse
import hashlib
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def sha(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def load_call(turn_dir):
    p = os.path.join(turn_dir, "calls.json")
    if not os.path.isfile(p):
        return None
    c = json.load(io.open(p, encoding="utf-8"))
    m = next((x for x in c if (x.get("tag") or "main") == "main"), c[0] if c else None)
    return m


def index(d):
    out = {}
    for name in sorted(os.listdir(os.path.join(d, "out")) if os.path.isdir(os.path.join(d, "out")) else []):
        if not name.startswith("turn_"):
            continue
        cid = name.split("_", 2)[-1]
        call = load_call(os.path.join(d, "out", name))
        mf_p = os.path.join(d, "out", name, "manifest.json")
        mf = json.load(io.open(mf_p, encoding="utf-8")) if os.path.isfile(mf_p) else {}
        if call is None:
            continue
        out[cid] = {"user_sha": sha(call.get("user") or ""),
                    "system_sha": sha(call.get("system") or ""),
                    "user_chars": len(call.get("user") or ""),
                    "prompt_chars": (mf.get("prompt_chars") or {}).get("prompt_sum"),
                    "case_input_sha256": mf.get("case_input_sha256")}
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pair", action="append", required=True, help="A目录=B目录（可多次）")
    ap.add_argument("--out", default=os.path.join(
        ROOT, "refs", "experiment_protocol_20261023", "deadline_quality_20261028",
        "PROMPT_IDENTITY_R3A_20261028.json"))
    a = ap.parse_args()

    report = {"kind": "prompt_identity_v1", "pairs": [], "all_identical": True}
    for spec in a.pair:
        left, right = spec.split("=", 1)
        A, B = index(os.path.join(ROOT, left)), index(os.path.join(ROOT, right))
        rows = []
        for cid in sorted(set(A) & set(B)):
            same_u = A[cid]["user_sha"] == B[cid]["user_sha"]
            same_s = A[cid]["system_sha"] == B[cid]["system_sha"]
            same_ci = A[cid]["case_input_sha256"] == B[cid]["case_input_sha256"]
            rows.append({"case_id": cid, "user_identical": same_u, "system_identical": same_s,
                         "case_input_identical": same_ci,
                         "chars": [A[cid]["user_chars"], B[cid]["user_chars"]]})
            report["all_identical"] &= bool(same_u and same_s and same_ci)
        report["pairs"].append({"a": left, "b": right, "positions": len(rows),
                                "only_a": sorted(set(A) - set(B)),
                                "only_b": sorted(set(B) - set(A)), "rows": rows})
        print("%s vs %s: positions=%d identical=%s"
              % (left, right, len(rows),
                 all(r["user_identical"] and r["system_identical"] and r["case_input_identical"]
                     for r in rows)))
    json.dump(report, io.open(a.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("WROTE", os.path.relpath(a.out, ROOT))
    print("ALL_IDENTICAL", report["all_identical"])
    return 0 if report["all_identical"] else 1


if __name__ == "__main__":
    sys.exit(main())
