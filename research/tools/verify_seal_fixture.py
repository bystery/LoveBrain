# -*- coding: utf-8 -*-
"""verify_seal_fixture.py — 合成封存夹具启封接口验证（G06a/G10）。

验证：①LOCK 散列与 payload 一致（完整性）；
     ②访问控制语义：夹具标 synthetic_canary，允许测试进程读取；
     ③R3#6：封存访问控制实测（tools/test_seal_access_control.py）——
       合成回路上证明"deny 后候选读被阻断/授权启封后可读"；子测试失败即本验证失败。
真实B线：不在本仓库范围（这是范围声明，不是隔离证据——隔离证据=③的实测回路）。
"""
import hashlib
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIX = os.path.join(ROOT, "refs", "arena_v1", "seal_fixture")


def main():
    ok = True
    lock_path = os.path.join(FIX, "SEAL_LOCK.sha256")
    payload = os.path.join(FIX, "SYNTH_SEALED_001.json")
    for p in (lock_path, payload):
        if not os.path.isfile(p):
            print("FAIL 缺件:", p)
            return 2
    lock = open(lock_path, encoding="utf-8").read().split()[0]
    actual = hashlib.sha256(open(payload, "rb").read()).hexdigest()
    ok &= lock == actual
    print(("PASS " if lock == actual else "FAIL ") + "封存完整性 LOCK==payload")
    data = json.load(open(payload, encoding="utf-8"))
    ok &= data.get("kind") == "synthetic_canary"
    print(("PASS " if data.get("kind") == "synthetic_canary" else "FAIL ")
          + "夹具身份 synthetic_canary（非真实题面）")
    # R3#6：访问控制实测（合成回路；子测试失败立即本验证失败）
    r = subprocess.run([sys.executable, "-B", "-X", "utf8",
                        os.path.join(ROOT, "tools", "test_seal_access_control.py")],
                       capture_output=True, text=True, encoding="utf-8",
                       errors="replace", timeout=300, cwd=ROOT)
    tail = ((r.stdout or "") + (r.stderr or "")).strip().splitlines()
    print("ACCESS_CONTROL rc=%d %s" % (r.returncode,
                                       tail[-1][:120] if tail else "(no output)"))
    ok &= r.returncode == 0 and "SEAL ACCESS CONTROL PASS" in (r.stdout or "")
    # 真实B线：范围声明（不得当作隔离证据）
    bl = os.path.join(ROOT, "benchmark", "b_line")
    print("SCOPE 真实B线目录%s（范围声明；隔离证据=上述访问控制实测）"
          % ("不在仓库内" if not os.path.isdir(bl) else "存在但未触碰"))
    print("SEAL FIXTURE %s" % ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
