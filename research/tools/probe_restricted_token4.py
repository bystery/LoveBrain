# -*- coding: utf-8 -*-
"""probe4 — 受限令牌 + 私有窗口站/桌面：解决 0xC0000142。"""
import base64
import ctypes
import ctypes.wintypes as wt
import os
import shutil
import subprocess
import sys

k32 = ctypes.WinDLL("kernel32", use_last_error=True)
u32 = ctypes.WinDLL("user32", use_last_error=True)
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
k32.CloseHandle.argtypes = [ctypes.c_void_p]
u32.CreateWindowStationW.argtypes = [wt.LPCWSTR, wt.DWORD, wt.DWORD, ctypes.c_void_p]
u32.CreateWindowStationW.restype = ctypes.c_void_p
u32.CreateDesktopW.argtypes = [wt.LPCWSTR, wt.LPCWSTR, wt.LPWSTR, wt.DWORD,
                               wt.DWORD, ctypes.c_void_p]
u32.CreateDesktopW.restype = ctypes.c_void_p

MARKER = "S-1-5-99-777000001"
GENERIC_ALL = 0x10000000
WINSTA_ALL = 0x37F
DESKTOP_ALL = 0x01FF


class SA(ctypes.Structure):
    _fields_ = [("nLength", wt.DWORD), ("lpSecurityDescriptor", ctypes.c_void_p),
                ("bInheritHandle", wt.BOOL)]


def str_sid(s):
    p = ctypes.c_void_p()
    ok = a32.ConvertStringSidToSidW(s, ctypes.byref(p))
    assert ok, "ConvertStringSidToSidW fail %d" % k32.GetLastError()
    return p


def build_sa_with_marker():
    """构造 DACL：Everyone/Users/marker 全允许（用于窗口站/桌面）。"""
    # 用 ConvertStringSecurityDescriptorToSecurityDescriptorW SDDL 更简单
    sddl = ("D:(A;;0x000F03FF;;;WD)(A;;0x000F03FF;;;BU)"
            "(A;;0x000F03FF;;;%s)" % MARKER.replace("-", ""))
    # SDDL 里自定义 SID 用字符串形式 S-1-5-99-777000001 → SDDL: (A;;FA;;;S-1-5-99-777000001)
    sddl = ("D:(A;;GA;;;WD)(A;;GA;;;BU)(A;;GA;;;S-1-5-99-777000001)")
    a32.ConvertStringSecurityDescriptorToSecurityDescriptorW.argtypes = [
        wt.LPCWSTR, wt.DWORD, ctypes.POINTER(ctypes.c_void_p),
        ctypes.POINTER(wt.ULONG)]
    a32.ConvertStringSecurityDescriptorToSecurityDescriptorW.restype = wt.BOOL
    psd = ctypes.c_void_p()
    ln = wt.ULONG()
    ok = a32.ConvertStringSecurityDescriptorToSecurityDescriptorW(
        sddl, 1, ctypes.byref(psd), ctypes.byref(ln))
    assert ok, "SDDL fail %d" % k32.GetLastError()
    sa = SA()
    sa.nLength = ctypes.sizeof(SA)
    sa.lpSecurityDescriptor = psd.value
    sa.bInheritHandle = False
    return sa


def create_restricted_token():
    h = wt.HANDLE()
    assert a32.OpenProcessToken(ctypes.c_void_p(k32.GetCurrentProcess()),
                                0x1 | 0x2 | 0x8, ctypes.byref(h))
    sids = ["S-1-1-0", "S-1-5-32-545", MARKER]
    arr = (SAA * len(sids))()
    for i, s in enumerate(sids):
        arr[i].Sid, arr[i].Attributes = str_sid(s), 0
    hr = wt.HANDLE()
    if not a32.CreateRestrictedToken(ctypes.c_void_p(h.value), 0, 0, None,
                                     0, None, len(sids), arr, ctypes.byref(hr)):
        return None
    return hr.value


def main():
    out_dir = os.path.abspath("logs/_rt_probe4")
    shutil.rmtree(out_dir, ignore_errors=True)
    os.makedirs(out_dir)
    out_file = os.path.join(out_dir, "result.txt")
    protected = os.path.abspath("benchmark/episodes/NCA_CORR_INTENT_v1.json")
    r = subprocess.run(["icacls", out_dir, "/grant", "*%s:(OI)(CI)F" % MARKER],
                       capture_output=True, text=True, encoding="gbk",
                       errors="replace")
    print("grant out_dir rc=", r.returncode, r.stdout.strip()[-60:])
    r2 = subprocess.run(["icacls", os.path.dirname(sys.executable), "/grant",
                         "*%s:(OI)(CI)RX" % MARKER], capture_output=True,
                        text=True, encoding="gbk", errors="replace")
    print("grant pydir rc=", r2.returncode, r2.stdout.strip()[-60:] if r2.stdout else r2.stderr.strip()[-80:])
    # 验证授权是否真的落上
    r3 = subprocess.run(["icacls", out_dir], capture_output=True, text=True,
                        encoding="gbk", errors="replace")
    print("out_dir ACL has marker:", "777000001" in r3.stdout)

    # 私有窗口站+桌面
    sa = build_sa_with_marker()
    hw = u32.CreateWindowStationW("arena_iso_ws", 0, WINSTA_ALL, ctypes.byref(sa))
    print("CreateWindowStation:", bool(hw), "err=", k32.GetLastError())
    if not hw:
        return 1
    u32.SetProcessWindowStation.argtypes = [ctypes.c_void_p]
    u32.SetProcessWindowStation.restype = wt.BOOL
    prev = u32.SetProcessWindowStation(hw)
    hd = u32.CreateDesktopW("arena_iso_dt", None, None, 0, DESKTOP_ALL,
                            ctypes.byref(sa))
    print("CreateDesktop:", bool(hd), "err=", k32.GetLastError())

    checks = [
        ("py_read", "open(r'%s', 'rb').read(4)" % sys.executable),
        ("sys32_read", "open(r'C:/Windows/System32/kernel32.dll', 'rb').read(4)"),
        ("protected_read", "open(r'%s', encoding='utf-8').read()" % protected),
        ("marker_write", "open(r'%s', 'w').write('W_OK')" % out_file),
    ]
    setup = ("CHECKS = %r\nRESULT = r'%s'\n" % (checks, out_file))
    body = ("import sys\n"
            "res = {}\n"
            "for k, code in CHECKS:\n"
            "    try:\n"
            "        exec(code); res[k] = 'OK'\n"
            "    except Exception as e:\n"
            "        res[k] = 'BLOCKED:' + type(e).__name__\n"
            "open(RESULT, 'w').write(repr(res))\n")
    b64 = base64.b64encode((setup + body).encode()).decode()
    cmdline = '"%s" -I -B -c "import base64;exec(base64.b64decode(\'%s\').decode())"' % (
        sys.executable, b64)

    tok = create_restricted_token()
    print("token:", bool(tok))
    si = SI()
    si.cb = ctypes.sizeof(si)
    si.lpDesktop = "arena_iso_ws\\arena_iso_dt"
    pi = PI()
    exe = sys.executable
    ok = a32.CreateProcessAsUserW(
        ctypes.c_void_p(tok), exe, ctypes.create_unicode_buffer(cmdline),
        None, None, False, 0x08000000, None, None, ctypes.byref(si), ctypes.byref(pi))
    print("CPAU:", bool(ok), "pid=", pi.dwProcessId, "err=", k32.GetLastError())
    if not ok:
        return 2
    k32.WaitForSingleObject(ctypes.c_void_p(pi.hProcess), 30000)
    code = wt.DWORD()
    k32.GetExitCodeProcess(ctypes.c_void_p(pi.hProcess), ctypes.byref(code))
    print("child exit:", code.value, hex(code.value & 0xFFFFFFFF))
    try:
        print("results:", open(out_file).read())
    except Exception as e:
        print("no result:", e)
    return 0


if __name__ == "__main__":
    sys.exit(main())
