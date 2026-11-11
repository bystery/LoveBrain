#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_seal_access_control.py — R3#6 封存访问控制实测（G06a 加强）。

独立复核 §9 反例：此前把"B 线目录不存在"当作隔离通过——目录不存在只能证明
"没有东西可读"，不能证明"候选不能读/只能授权启封"的访问控制语义。

本测试用**合成封存夹具**（临时目录，不触碰真实 B 线）实测完整语义回路：
  1. 封存：payload + SEAL_LOCK（sha256 完整性）；
  2. 施加文件级 deny（icacls (RD)——(R) 会连 icacls 自己锁死，禁用）；
  3. "候选"子进程尝试读取 → 必须 BLOCKED（正向证据：子进程打印
     BLOCKED:<异常类型>，父进程要求该前缀存在——空 stdout 不算阻断证据）；
  4. 授权启封：父进程校验授权令牌（合成 AUTH_TOKEN 内容匹配）→ icacls /reset
     → 子进程再次读取 → 必须 READ_OK 且 sha256 与锁一致；
  5. 还原：finally 恒 /reset，临时目录清理。
真实 B 线：本测试不打开、不引用其内容；其存在与否仅作范围声明，不作隔离证据。
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))

CHILD = r'''
import sys, hashlib
p, mode, want = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    data = open(p, "rb").read()
except OSError as e:
    print("BLOCKED:%s:%s" % (type(e).__name__, e.errno))
    sys.exit(0)
h = hashlib.sha256(data).hexdigest()
if mode == "expect":
    print("READ_OK:%s" % ("MATCH" if h == want else "MISMATCH:" + h[:16]))
else:
    print("READ_UNEXPECTED:%s" % h[:16])
'''


def icacls(args):
    r = subprocess.run(["icacls"] + args, capture_output=True)
    return r.returncode, (r.stderr or r.stdout or b"").decode("utf-8", "replace")[:200]


def main():
    fails = []
    tmp = tempfile.mkdtemp(prefix="lb_seal_")
    payload = os.path.join(tmp, "SYN_SEALED_001.json")
    body = json.dumps({"kind": "synthetic_canary",
                       "note": "R3#6 access-control drill payload"}).encode("utf-8")
    open(payload, "wb").write(body)
    want_sha = hashlib.sha256(body).hexdigest()

    # 1) 封存完整性（合成 LOCK）
    open(os.path.join(tmp, "SEAL_LOCK.sha256"), "w").write(want_sha)
    if hashlib.sha256(open(payload, "rb").read()).hexdigest() != want_sha:
        fails.append("1: 合成封存 LOCK 与 payload 不一致")

    # 2) 文件级 deny（(RD)，绝不用 (R)——READ_CONTROL 锁死教训）
    rc, err = icacls([payload, "/deny", "*S-1-1-0:(RD)"])
    if rc != 0:
        print("SKIP: 本环境无法施加 ACL deny（rc=%d %s）——如实报告，不谎称 PASS" % (rc, err))
        shutil.rmtree(tmp, ignore_errors=True)
        return 3
    denied = True
    try:
        # 3) 候选子进程读取 → 必须 BLOCKED（正向证据前缀）
        r = subprocess.run([sys.executable, "-c", CHILD, payload, "deny", "-"],
                           capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=60)
        out = (r.stdout or "").strip()
        if r.returncode != 0 or not out.startswith("BLOCKED:"):
            fails.append("3: 子进程读取未被证明阻断（rc=%d stdout=%r）"
                         % (r.returncode, out[:80]))
        # 同进程对照：读也应失败
        try:
            open(payload, "rb").read()
            fails.append("3b: 父进程读未被拒（deny 未生效）")
        except OSError:
            pass

        # 4) 授权启封：令牌校验 → /reset → 再读 → 散列一致
        tok = os.path.join(tmp, "AUTH_TOKEN")
        open(tok, "w").write(want_sha)
        if open(tok, encoding="utf-8").read().strip() != want_sha:
            fails.append("4a: 授权令牌校验失败（不应启封）")
        else:
            rc, err = icacls([payload, "/reset"])
            if rc != 0:
                fails.append("4b: /reset 失败 rc=%d %s" % (rc, err))
            else:
                r = subprocess.run([sys.executable, "-c", CHILD, payload,
                                    "expect", want_sha],
                                   capture_output=True, text=True,
                                   encoding="utf-8", errors="replace", timeout=60)
                out = (r.stdout or "").strip()
                if out != "READ_OK:MATCH":
                    fails.append("4c: 授权启封后未证明读到一致内容（stdout=%r）"
                                 % out[:80])
    finally:
        if denied:
            icacls([payload, "/reset"])
        shutil.rmtree(tmp, ignore_errors=True)

    # 5) 真实 B 线：范围声明（非隔离证据），绝不打开
    bl = os.path.join(ROOT, "benchmark", "b_line")
    print("SCOPE: 真实B线目录%s（本测试未打开、未引用其内容；"
          "'目录不存在'只作范围声明，不作隔离证据——隔离证据=上述合成回路）"
          % ("不在仓库内" if not os.path.isdir(bl) else "存在（未触碰）"))

    if fails:
        print("SEAL ACCESS CONTROL FAIL")
        for x in fails:
            print(" -", x)
        sys.exit(1)
    print("SEAL ACCESS CONTROL PASS：deny 后候选进程读取被阻断（正向证据 "
          "BLOCKED:<异常类型>）；授权校验+/reset 后读到内容且 sha256 与锁一致；"
          "真实 B 线未触碰。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
