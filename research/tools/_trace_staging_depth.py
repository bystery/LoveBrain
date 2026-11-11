# -*- coding: utf-8 -*-
"""tools/_trace_staging_depth.py — 复算 trusted_controller._stage_candidate_closure 的依赖深度（只读）。"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D1 = re.compile(r'"candidates",\s*"([A-Za-z0-9_]+)"')
D2 = re.compile(r"candidates[/\\\\]+([A-Za-z0-9_]+)")


def deps(name):
    p = os.path.join(ROOT, "candidates", name, "mechanism", "mechanism.py")
    if not os.path.isfile(p):
        return set()
    src = open(p, encoding="utf-8").read()
    d = set(D1.findall(src)) | set(D2.findall(src))
    return set(x for x in d if x != name and os.path.isdir(os.path.join(ROOT, "candidates", x)))


def walk(name, depth, chain):
    mark = "  <== 触发深度上限" if depth > 4 else ""
    print("%s%s -> %s%s" % ("  " * depth, name, ",".join(sorted(deps(name))) or "-", mark))
    if depth > 6:
        return
    for c in sorted(deps(name)):
        if c in chain:
            print("%s%s (cycle, 已 staged 直接返回)" % ("  " * (depth + 1), c))
            continue
        walk(c, depth + 1, chain | {c})


if __name__ == "__main__":
    walk(sys.argv[1] if len(sys.argv) > 1 else "action_policy_r2", 0, set())
