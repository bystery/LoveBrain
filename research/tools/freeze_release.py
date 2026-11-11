#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""freeze_release.py — 冻结 refs/arena_v1/release.json（R3#7）。

纪律（收口指导书 §E5 + 陷阱清单）：release.json 必须在代码定稿之后冻结，
否则散列漂移 → G01 FAIL。本工具与 tools/build_release_package.py 的白名单
**同源**（直接 import 其 PAYLOAD_DIRS/PAYLOAD_FILES），保证 G01 审计集 =
发布包 payload 集，干净目录复验时逐文件散列核对必然可达。

release.json 不列自身（自引用散列不可能）。
用法：python -B -X utf8 tools/freeze_release.py [--check]
  --check  只核对不写（退出码 0=与现物一致）。
"""
import argparse
import datetime
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
sys.path.insert(0, HERE)
import build_release_package as bp  # noqa: E402  白名单同源

RELEASE = os.path.join(ROOT, "refs", "arena_v1", "release.json")


def sha_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def enumerate_scope():
    """枚举 payload 白名单内的全部文件（相对路径，posix 分隔）。"""
    files = []

    def add(rel):
        # 修复（v5 总验收发现）：绝对路径必须强制相对化——否则 release.json
        # 带绝对路径，干净目录复验时 G01 会审计到本机原文件而非包内容
        if os.path.isabs(rel):
            rel = os.path.relpath(rel, ROOT)
        rel = rel.replace("\\", "/")
        if rel == "refs/arena_v1/release.json":   # 不自列
            return
        if os.path.isfile(os.path.join(ROOT, rel)):
            files.append(rel)

    for d, excludes in bp.PAYLOAD_DIRS:
        src_root = os.path.join(ROOT, d)
        if not os.path.isdir(src_root):
            continue
        for cur, dirs, fnames in os.walk(src_root):
            rel_cur = os.path.relpath(cur, ROOT).replace("\\", "/")
            if any(rel_cur.startswith(e.replace("\\", "/"))
                   for e in [x.replace("/", os.sep) for x in excludes]):
                dirs[:] = []
                continue
            dirs[:] = [x for x in dirs
                       if x != "__pycache__" and not x.startswith("_acl_locked")]
            for f in fnames:
                if f.endswith((".pyc", ".pyo")):
                    continue
                add(os.path.join(cur, f))
    for rel in bp.PAYLOAD_FILES:
        add(rel)
    return sorted(set(files))


def build_record():
    files = {}
    missing = []
    for rel in enumerate_scope():
        p = os.path.join(ROOT, rel)
        if not os.path.isfile(p):
            missing.append(rel)
            continue
        files[rel] = {"sha256": sha_file(p)}
    return {
        "date": datetime.date.today().isoformat(),
        "release_id": "arena_v1",
        "evaluation_mode": "human_supervised",
        "judge_state": "DISABLED（judge 永不进红线）",
        "python": "%d.%d.%d" % sys.version_info[:3],
        "platform": sys.platform,
        "files": files,
        "missing": missing,
        "updated_at": datetime.datetime.now().isoformat(timespec="seconds"),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    rec = build_record()
    if a.check:
        cur = json.load(open(RELEASE, encoding="utf-8"))
        same = cur.get("files") == rec["files"]
        print("RELEASE CHECK %s files=%d missing=%d"
              % ("MATCH" if same else "DRIFT", len(rec["files"]),
                 len(rec["missing"])))
        if not same:
            old, new = set(cur.get("files", {})), set(rec["files"])
            for k in sorted(new - old)[:8]:
                print("  +", k)
            for k in sorted(old - new)[:8]:
                print("  -", k)
            for k in sorted(old & new)[:8]:
                if cur["files"][k].get("sha256") != rec["files"][k]["sha256"]:
                    print("  ~", k)
        return 0 if same and not rec["missing"] else 1
    json.dump(rec, open(RELEASE, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("RELEASE FROZEN files=%d missing=%d -> %s"
          % (len(rec["files"]), len(rec["missing"]),
             os.path.relpath(RELEASE, ROOT)))
    return 0 if not rec["missing"] else 1


if __name__ == "__main__":
    sys.exit(main())
