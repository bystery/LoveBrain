# -*- coding: utf-8 -*-
"""probe3 — 受限令牌定位：分别测 cmd / python；核对 marker 授权是否成功。"""
import ctypes
import ctypes.wintypes as wt
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from probe_restricted_token2 import create_restricted_token, spawn_as_user

k32 = ctypes.WinDLL("kernel32", use_last_error=True)
k32.WaitForSingleObject.argtypes = [ctypes.c_void_p, wt.DWORD]
k32.GetExitCodeProcess.argtypes = [ctypes.c_void_p, ctypes.POINTER(wt.DWORD)]
k32.TerminateProcess.argtypes = [ctypes.c_void_p, wt.UINT]
k32.CloseHandle.argtypes = [ctypes.c_void_p]


def run_and_code(cmdline, wait_ms=15000):
    tok = create_restricted_token()
    if not tok:
        return None, "token fail"
    ok, pi = spawn_as_user(tok, cmdline)
    if not ok:
        return None, "spawn err=%d" % k32.GetLastError()
    k32.WaitForSingleObject(ctypes.c_void_p(pi.hProcess), wait_ms)
    code = wt.DWORD()
    k32.GetExitCodeProcess(ctypes.c_void_p(pi.hProcess), ctypes.byref(code))
    if code.value == 259:  # STILL_ACTIVE → 超时杀掉
        k32.TerminateProcess(ctypes.c_void_p(pi.hProcess), 99)
        return "TIMEOUT", pi.dwProcessId
    return code.value, pi.dwProcessId


if __name__ == "__main__":
    pydir = os.path.dirname(sys.executable)
    print("== python dir ACL:")
    r = subprocess.run(["icacls", pydir], capture_output=True, text=True,
                       encoding="gbk", errors="replace")
    for ln in r.stdout.splitlines()[:8]:
        print("   ", ln.strip())

    print("== cmd.exe /c exit 7 (受限):")
    print("   ", run_and_code("C:\\Windows\\System32\\cmd.exe /c exit 7"))

    print("== python -I -B -c exit 9 (受限):")
    cmdline = '"%s" -I -B -c "exit(9)"' % sys.executable
    print("   ", run_and_code(cmdline))
