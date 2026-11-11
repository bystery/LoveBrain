# tools/freeze_identity.py — R5.1 身份闭包冻结
#
# 语义：一次实跑之前，把两臂（reference_product_v1 与 v2_recall）
#       各自的 identity_sha256 连同**闭包展开的完整文件散列**落盘，
#       作为"这条结果对应这份代码 + 这份资产 + 这份 params"的凭证。
# 反作弊：v2 若把父本 mechanism.py 换掉但候选目录内文件字节不变，
#         旧 identity 不会变（旧 bug）；R5.1 修完后 v2 identity 里
#         必然包含 reference_product_v1/** 的每一项散列 → 换父本自动改 identity。
#
# 用法：
#   python tools/freeze_identity.py [--out logs/identity_freeze_<ts>.json]
#       [--candidates reference_product_v1,v2_recall]
import argparse
import datetime as _dt
import hashlib
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))


def _load_api_trial():
    spec = importlib.util.spec_from_file_location(
        "api_trial_ice", os.path.join(ROOT, "tools", "api_trial.py"))
    mod = importlib.util.module_from_spec(spec)
    # api_trial 顶层可能有副作用（读 env），此处不 exec_module 到 main，
    # 只借用 harness_identity；先 stub 掉敏感 env 让 import 稳定
    os.environ.setdefault("DSF_API_KEY", "")
    spec.loader.exec_module(mod)
    return mod


def _sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def _config_files():
    """本批实际用到的题面/评分卡/评分器/执行器/工具/配置全文件散列（reviewer A：
    不能只存候选身份却声称"所有工具已冻结"）。单一来源=exam_interleave_plan.FROZEN_EXTRA_FILES。"""
    spec = importlib.util.spec_from_file_location(
        "eip_for_freeze", os.path.join(ROOT, "tools", "exam_interleave_plan.py"))
    eip = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(eip)
        names = list(eip.FROZEN_EXTRA_FILES)
    except Exception:
        names = []
    out = {}
    for rel in names:
        p = os.path.join(ROOT, rel.replace("/", os.sep))
        out[rel] = hashlib.sha256(open(p, "rb").read()).hexdigest() \
            if os.path.isfile(p) else None
    return out


def freeze(candidates):
    at = _load_api_trial()
    per = {}
    for c in candidates:
        info = at.harness_identity(c)
        per[c] = {
            "identity_sha256": info["identity_sha256"],
            "closure": info["closure"],
            "file_count": len(info["files"]),
            "files": info["files"],
        }
    # 交叉校验：v2 的 files 必须覆盖父本 files
    probe_pairs = []
    for c in candidates:
        params = per[c]["closure"]
        if len(params) < 2:
            continue
        child = c
        parent = params[1]
        child_files = set(per[child]["files"])
        parent_files = set(per[parent]["files"])
        probe_pairs.append({
            "child": child, "parent": parent,
            "parent_files_subset_of_child": parent_files.issubset(child_files),
            "missing_from_child": sorted(parent_files - child_files)[:5],
        })
    ts = _dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    return {"generated_at": ts, "candidates": per,
            "config_files": _config_files(),
            "closure_probes": probe_pairs}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--candidates",
                    default="reference_product_v1,v2_recall")
    ap.add_argument("--out", default=None)
    args = ap.parse_args(argv)
    cands = [c.strip() for c in args.candidates.split(",") if c.strip()]
    doc = freeze(cands)
    if args.out:
        path = args.out if os.path.isabs(args.out) else os.path.join(ROOT, args.out)
    else:
        path = os.path.join(ROOT, "logs",
                            "identity_freeze_%s.json" % doc["generated_at"])
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print("identity freeze: %s" % path)
    for c, v in doc["candidates"].items():
        print("  %-24s identity=%s closure=%s files=%d" % (
            c, v["identity_sha256"][:16] + "…", v["closure"], v["file_count"]))
    for p in doc["closure_probes"]:
        print("  probe %s superset-of %s -> %s (missing=%s)" % (
            p["child"], p["parent"],
            p["parent_files_subset_of_child"], p["missing_from_child"]))
    bad = [p for p in doc["closure_probes"] if not p["parent_files_subset_of_child"]]
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
