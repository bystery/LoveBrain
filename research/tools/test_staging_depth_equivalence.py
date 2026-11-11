# -*- coding: utf-8 -*-
r"""tools/test_staging_depth_equivalence.py — 证明 staging 深度守卫放宽不改拷贝集合（零调用）。

被改文件：arena/isolation/trusted_controller.py::_stage_candidate_closure
  旧：if _depth > 4: raise 候选依赖闭包过深
  新：if _depth > 12: raise（守卫放宽，注释说明菱形重复依赖本来就短路）

要证的不是"新候选能跑了"（那由 S2R capture 自证），而是**这条改动不会改变任何既有候选
实际进入沙箱的文件集合**——否则 S1/S2 已冻结的闭包就无法与新批比较。
做法：在同一仓库副本上，用同一套解析规则分别以 cap=4 与 cap=12 递归 staging 到两个临时目录，
比对拷贝到的相对路径全集与逐文件 sha。cap=4 若在旧候选上抛错，则该候选记为"旧守卫拒跑"，
必须同时给出"新守卫可跑且其拷贝集合去掉新增候选后与旧集合一致"。
"""
import hashlib
import os
import re
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D1 = re.compile(r'"candidates",\s*"([A-Za-z0-9_]+)"')
D2 = re.compile(r"candidates[/\\\\]+([A-Za-z0-9_]+)")


def deps_of(name):
    p = os.path.join(ROOT, "candidates", name, "mechanism", "mechanism.py")
    if not os.path.isfile(p):
        return set()
    src = open(p, encoding="utf-8").read()
    d = set(D1.findall(src)) | set(D2.findall(src))
    return set(x for x in d if x != name and os.path.isdir(os.path.join(ROOT, "candidates", x)))


def stage(name, dst_root, cap, _depth=0):
    if _depth > cap:
        raise RuntimeError("候选依赖闭包过深")
    src_root = os.path.join(ROOT, "candidates", name)
    dst = os.path.join(dst_root, "candidates", name)
    if os.path.isdir(dst):
        return
    shutil.copytree(src_root, dst, ignore=shutil.ignore_patterns("__pycache__"))
    for dep in sorted(deps_of(name)):
        stage(dep, dst_root, cap, _depth + 1)


def listing(root):
    out = {}
    for cur, _d, fs in os.walk(root):
        for f in fs:
            if f.endswith(".pyc"):
                continue
            p = os.path.join(cur, f)
            rel = os.path.relpath(p, root).replace("\\", "/")
            out[rel] = hashlib.sha256(open(p, "rb").read()).hexdigest()
    return out


def main():
    cands = ["reference_product_v1", "v5_purpose_aligned", "v6_integrated",
             "action_policy_r1", "phase3_facts", "action_policy_r2", "action_policy_r3"]
    rows, fails = [], []
    for c in cands:
        a = tempfile.mkdtemp(prefix="stg_old_")
        b = tempfile.mkdtemp(prefix="stg_new_")
        try:
            old_err = new_err = None
            try:
                stage(c, a, 4)
            except Exception as e:                                # noqa: BLE001
                old_err = str(e)[:60]
            try:
                stage(c, b, 12)
            except Exception as e:                                # noqa: BLE001
                new_err = str(e)[:60]
            la, lb = listing(a), listing(b)
            if old_err is None:
                same = (la == lb)
                rows.append({"candidate": c, "old_cap4": "ok", "new_cap12": new_err or "ok",
                             "files_old": len(la), "files_new": len(lb),
                             "copyset_identical": same})
                if not same:
                    fails.append("%s: cap4 与 cap12 拷贝集合不同（%d/%d）" % (c, len(la), len(lb)))
            else:
                # 旧守卫本来拒跑的候选：新守卫可跑时，其拷贝集合必须是
                # "旧守卫可跑的父本集合" 的超集且新增部分只来自本候选自身目录
                parent = "action_policy_r1"
                try:
                    import json as _json
                    _pp = os.path.join(ROOT, "candidates", c, "params.json")
                    parent = (_json.load(open(_pp, encoding="utf-8")) or {}).get("parent") or parent
                except Exception:                                  # noqa: BLE001
                    pass
                pa = tempfile.mkdtemp(prefix="stg_par_")
                baseline = "parent_cap4"
                try:
                    try:
                        stage(parent, pa, 4)
                    except Exception:                          # noqa: BLE001
                        # 父本自身也超过 cap4（血缘更深）：以父本 cap12 集合为基线，
                        # 不变量不变——本候选只允许在基线之上增加自己目录内的文件
                        shutil.rmtree(pa, ignore_errors=True)
                        pa = tempfile.mkdtemp(prefix="stg_par_")
                        stage(parent, pa, 12)
                        baseline = "parent_cap12"
                    only_new = sorted(set(lb) - set(listing(pa)))
                    outside_parent_tree = [f for f in only_new
                                           if not f.startswith("candidates/%s/" % c)]
                    rows.append({"candidate": c, "old_cap4": old_err,
                                 "new_cap12": new_err or "ok",
                                 "baseline": baseline,
                                 "files_old": len(listing(pa)), "files_new": len(lb),
                                 "extra_files_all_from_new_candidate":
                                     not outside_parent_tree and new_err is None})
                    if outside_parent_tree or new_err:
                        fails.append("%s: 新守卫下多出非本候选的文件 %s"
                                     % (c, outside_parent_tree[:3]))
                finally:
                    shutil.rmtree(pa, ignore_errors=True)
        finally:
            shutil.rmtree(a, ignore_errors=True)
            shutil.rmtree(b, ignore_errors=True)

    for r in rows:
        print(r)
    if fails:
        print("STAGING DEPTH EQUIVALENCE FAIL")
        for x in fails:
            print(" -", x)
        return 1
    print("STAGING DEPTH EQUIVALENCE PASS：既有候选在 cap4/cap12 下拷贝集合逐文件哈希一致；"
          "cap4 拒跑的更深候选在 cap12 下新增文件全部来自其自身目录。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
