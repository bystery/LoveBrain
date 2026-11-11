# -*- coding: utf-8 -*-
"""probe2 — 受限令牌全链路验证（修正 SidsToRestrict=裸数组指针）。

验证矩阵：
  A. CreateRestrictedToken(2 SID) + CreateProcessAsUserW 成功启动
  B. 受限子进程读 python 自身（须成功 → 系统目录对 restricting 检查可通过）
  C. 受限子进程读未授 marker 的仓库文件（须被拒）
  D. 受限子进程读授了 marker ACE 的文件（须成功）
子进程结果写到一个 marker 可写的探针输出文件，父进程回收。
"""
import base64
import ctypes
import ctypes.wintypes as wt
import os
import shutil
import subprocess
import sys

k32 = ctypes.WinDLL("kernel32", use_last_error=True)
a32 = ctypes.WinDLL("advapi32", use_last_error=True)
k32.GetCurrentProcess.restype = ctypes.c_void_p
a32.OpenProcessToken.argtypes = [ctypes.c_void_p, wt.DWORD, ctypes.POINTER(wt.HANDLE)]
a32.OpenProcessToken.restype = wt.BOOL
a32.ConvertStringSidToSidW.argtypes = [wt.LPCWSTR, ctypes.POINTER(ctypes.c_void_p)]
a32.ConvertStringSidToSidW.restype = wt.BOOL


class SAA(ctypes.Structure):
    _fields_ = [("Sid", ctypes.c_void_p), ("Attributes", ctypes.c_ulong)]


a32.CreateRestrictedToken.argtypes = [
    ctypes.c_void_p, wt.DWORD, wt.DWORD, ctypes.c_void_p,
    wt.DWORD, ctypes.c_void_p, wt.DWORD, ctypes.POINTER(SAA),
    ctypes.POINTER(wt.HANDLE)]
a32.CreateRestrictedToken.restype = wt.BOOL


class SI(ctypes.Structure):
    _fields_ = [
        ("cb", wt.DWORD), ("lpReserved", wt.LPWSTR), ("lpDesktop", wt.LPWSTR),
        ("lpTitle", wt.LPWSTR), ("dwX", wt.DWORD), ("dwY", wt.DWORD),
        ("dwXSize", wt.DWORD), ("dwYSize", wt.DWORD),
        ("dwXCountChars", wt.DWORD), ("dwYCountChars", wt.DWORD),
        ("dwFillAttribute", wt.DWORD), ("dwFlags", wt.DWORD),
        ("wShowWindow", wt.WORD), ("cbReserved2", wt.WORD),
        ("lpReserved2", ctypes.c_void_p), ("hStdInput", ctypes.c_void_p),
        ("hStdOutput", ctypes.c_void_p), ("hStdError", ctypes.c_void_p)]


class PI(ctypes.Structure):
    _fields_ = [("hProcess", ctypes.c_void_p), ("hThread", ctypes.c_void_p),
                ("dwProcessId", wt.DWORD), ("dwThreadId", wt.DWORD)]


a32.CreateProcessAsUserW.argtypes = [
    ctypes.c_void_p, wt.LPCWSTR, wt.LPWSTR, ctypes.c_void_p, ctypes.c_void_p,
    wt.BOOL, wt.DWORD, ctypes.c_void_p, wt.LPCWSTR,
    ctypes.POINTER(SI), ctypes.POINTER(PI)]
a32.CreateProcessAsUserW.restype = wt.BOOL
k32.WaitForSingleObject.argtypes = [ctypes.c_void_p, wt.DWORD]
k32.GetExitCodeProcess.argtypes = [ctypes.c_void_p, ctypes.POINTER(wt.DWORD)]

MARKER = "S-1-5-99-777000001"


def str_sid(s):
    p = ctypes.c_void_p()
    ok = a32.ConvertStringSidToSidW(s, ctypes.byref(p))
    assert ok, "ConvertStringSidToSidW fail %d" % k32.GetLastError()
    return p


def create_restricted_token():
    h = wt.HANDLE()
    assert a32.OpenProcessToken(ctypes.c_void_p(k32.GetCurrentProcess()),
                                0x1 | 0x2 | 0x8, ctypes.byref(h))
    sids = [("S-1-1-0"), ("S-1-5-32-545"), MARKER]   # Everyone, Users, marker
    arr = (SAA * len(sids))()
    for i, s in enumerate(sids):
        arr[i].Sid, arr[i].Attributes = str_sid(s), 0
    hr = wt.HANDLE()
    ok = a32.CreateRestrictedToken(ctypes.c_void_p(h.value), 0, 0, None,
                                   0, None, 2, arr, ctypes.byref(hr))
    if not ok:
        return None
    return hr.value


def spawn_as_user(token, cmdline):
    si = SI()
    si.cb = ctypes.sizeof(si)
    pi = PI()
    exe = cmdline.split(" ", 1)[0].strip('"')
    ok = a32.CreateProcessAsUserW(
        ctypes.c_void_p(token), exe,
        ctypes.create_unicode_buffer(cmdline), None, None, False,
        0x08000000, None, None, ctypes.byref(si), ctypes.byref(pi))
    return ok, pi


def icacls(args):
    return subprocess.run(["icacls"] + args, capture_output=True,
                          text=True, encoding="gbk", errors="replace")


def main():
    out_dir = os.path.abspath("logs/_rt_probe")
    shutil.rmtree(out_dir, ignore_errors=True)
    os.makedirs(out_dir)
    out_file = os.path.join(out_dir, "result.txt")
    protected = os.path.abspath("benchmark/episodes/NCA_CORR_INTENT_v1.json")

    # D: 探针目录授 marker ACE（子进程要写结果文件）
    icacls([out_dir, "/grant", "%s:(OI)(CI)F" % MARKER])
    # python 目录授 marker RX（自有目录，免管理员）
    icacls([os.path.dirname(sys.executable), "/grant",
            "%s:(OI)(CI)RX" % MARKER])

    checks = [
        ("py_read", "open(r'%s', encoding='utf-8').read()" % sys.executable),
        ("protected_read", "open(r'%s', encoding='utf-8').read()" % protected),
        ("marker_write", "open(r'%s', 'w').write('W_OK')" % out_file),
    ]
    body = ("import sys\n"
            "res = {}\n"
            "for k, code in CHECKS:\n"
            "    try:\n"
            "        exec(code)\n"
            "        res[k] = 'OK'\n"
            "    except Exception as e:\n"
            "        res[k] = 'BLOCKED:' + type(e).__name__\n"
            "open(r'%s', 'w').write(repr(res))\n")
    setup = ("CHECKS = %r\nRESULT = r'%s'\n" % (checks, out_file))
    src = setup + body % out_file
    b64 = base64.b64encode(src.encode()).decode()
    cmdline = '"%s" -I -B -c "import base64;exec(base64.b64decode(\'%s\').decode())"' % (
        sys.executable, b64)

    tok = create_restricted_token()
    print("CreateRestrictedToken:", "OK" if tok else "FAIL")
    if not tok:
        return 1
    ok, pi = spawn_as_user(tok, cmdline)
    print("CreateProcessAsUserW:", bool(ok), "pid=", pi.dwProcessId,
          "err=", k32.GetLastError())
    if not ok:
        return 2
    k32.WaitForSingleObject(ctypes.c_void_p(pi.hProcess), 30000)
    code = wt.DWORD()
    k32.GetExitCodeProcess(ctypes.c_void_p(pi.hProcess), ctypes.byref(code))
    print("child exit:", code.value)
    try:
        print("results:", open(out_file).read())
    except Exception as e:
        print("no result file:", e)
    return 0


if __name__ == "__main__":
    sys.exit(main())
