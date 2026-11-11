# -*- coding: utf-8 -*-
"""probe6 — 给本会话 WinSta0\Default 补 marker ACE（避免建新窗口站）。"""
import ctypes
import ctypes.wintypes as wt
import os
import subprocess
import sys

k32 = ctypes.WinDLL("kernel32", use_last_error=True)
u32 = ctypes.WinDLL("user32", use_last_error=True)
a32 = ctypes.WinDLL("advapi32", use_last_error=True)
k32.GetCurrentProcess.restype = ctypes.c_void_p
a32.ConvertStringSidToSidW.argtypes = [wt.LPCWSTR, ctypes.POINTER(ctypes.c_void_p)]
a32.ConvertStringSidToSidW.restype = wt.BOOL
a32.SetEntriesInAclW.argtypes = [wt.ULONG, ctypes.c_void_p, ctypes.c_void_p,
                                 ctypes.POINTER(ctypes.c_void_p)]
a32.SetEntriesInAclW.restype = wt.DWORD
a32.GetSecurityInfo.argtypes = [ctypes.c_void_p, wt.DWORD, wt.DWORD,
                                ctypes.c_void_p, ctypes.c_void_p,
                                ctypes.POINTER(ctypes.c_void_p),
                                ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p)]
a32.GetSecurityInfo.restype = wt.DWORD
a32.SetSecurityInfo.argtypes = [ctypes.c_void_p, wt.DWORD, wt.DWORD,
                                ctypes.c_void_p, ctypes.c_void_p,
                                ctypes.c_void_p, ctypes.c_void_p]
a32.SetSecurityInfo.restype = wt.DWORD
u32.OpenWindowStationW.argtypes = [wt.LPCWSTR, wt.BOOL, wt.DWORD]
u32.OpenWindowStationW.restype = ctypes.c_void_p
u32.OpenDesktopW.argtypes = [wt.LPCWSTR, wt.DWORD, wt.BOOL, wt.DWORD]
u32.OpenDesktopW.restype = ctypes.c_void_p

MARKER = "S-1-5-99-777000001"
SE_WINDOW_OBJECT = 5
DACL_SECURITY_INFORMATION = 4


class _T(ctypes.Structure):
    _fields_ = [("pMultipleTrustee", ctypes.c_void_p),
                ("MultipleTrusteeOperation", wt.DWORD),
                ("TrusteeForm", wt.DWORD),
                ("TrusteeType", wt.DWORD),
                ("ptstrName", ctypes.c_void_p)]


class EA(ctypes.Structure):
    _fields_ = [("grfAccessPermissions", wt.DWORD),
                ("grfAccessMode", wt.DWORD),
                ("grfInheritance", wt.DWORD),
                ("Trustee", _T)]


def add_marker_ace_to_handle(h, obj_type, perms):
    old = ctypes.c_void_p()
    rc = a32.GetSecurityInfo(h, obj_type, DACL_SECURITY_INFORMATION,
                             None, None, ctypes.byref(old), None, None)
    if rc:
        return False, "GetSecurityInfo rc=%d" % rc
    psid = ctypes.c_void_p()
    if not a32.ConvertStringSidToSidW(MARKER, ctypes.byref(psid)):
        return False, "sid fail"
    ea = EA()
    ea.grfAccessPermissions = perms
    ea.grfAccessMode = 2
    ea.grfInheritance = 0
    ea.Trustee.TrusteeForm = 0
    ea.Trustee.TrusteeType = 0
    ea.Trustee.ptstrName = psid.value
    new = ctypes.c_void_p()
    rc = a32.SetEntriesInAclW(1, ctypes.byref(ea), old, ctypes.byref(new))
    if rc:
        return False, "SetEntriesInAcl rc=%d" % rc
    rc = a32.SetSecurityInfo(h, obj_type, DACL_SECURITY_INFORMATION,
                             None, None, new, None)
    return rc == 0, "SetSecurityInfo rc=%d" % rc


if __name__ == "__main__":
    # 1) WinSta0
    wrights = 0x100 | 0x2 | 0x4 | 0x8 | 0x10 | 0x20   # incl WINSTA_WRITEATTRIBUTES...
    hw = u32.OpenWindowStationW("WinSta0", False, 0x2000000)  # WRITE_DAC
    print("OpenWindowStation(WinSta0, WRITE_DAC):", bool(hw), "err=", k32.GetLastError())
    if hw:
        ok, d = add_marker_ace_to_handle(hw, SE_WINDOW_OBJECT, 0x37F)
        print("  add marker ACE to WinSta0:", ok, d)
        u32.CloseWindowStation.argtypes = [ctypes.c_void_p]
        u32.CloseWindowStation(hw)
    # 2) Default 桌面
    hd = u32.OpenDesktopW("Default", 0, False, 0x02000000)  # DESKTOP_WRITEOBJECTS? WRITE_DAC=0x40000
    print("OpenDesktop(Default):", bool(hd), "err=", k32.GetLastError())
    if hd:
        ok, d = add_marker_ace_to_handle(hd, SE_WINDOW_OBJECT, 0x01FF)
        print("  add marker ACE to Default:", ok, d)
        u32.CloseDesktop.argtypes = [ctypes.c_void_p]
        u32.CloseDesktop(hd)

    # 3) 全链路：受限令牌 + 默认桌面 + 探针
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from probe_restricted_token4 import create_restricted_token
    tok = create_restricted_token()
    print("token:", bool(tok))

    import base64, shutil
    d = os.path.abspath("logs/_rt_probe6")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    # 授权 out_dir + pydir（SetNamedSecurityInfoW 路径）
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from probe_restricted_token5 import grant_marker_ace
    print("grant out_dir:", grant_marker_ace(d, 0x1F01FF))
    print("grant pydir:", grant_marker_ace(os.path.dirname(sys.executable), 0x2000B9))

    out_file = os.path.join(d, "result.txt")
    protected = os.path.abspath("benchmark/episodes/NCA_CORR_INTENT_v1.json")
    checks = [
        ("py_read", "open(r'%s', 'rb').read(4)" % sys.executable),
        ("protected_read", "open(r'%s', encoding='utf-8').read()" % protected),
        ("marker_write", "open(r'%s', 'w').write('W_OK')" % out_file),
        ("other_state_read", "open(r'%s', encoding='utf-8').read()" % os.path.abspath("logs/aggregate_test.json")),
    ]
    setup = "CHECKS = %r\nRESULT = r'%s'\n" % (checks, out_file)
    body = ("import sys\nres = {}\n"
            "for k, code in CHECKS:\n"
            "    try:\n        exec(code); res[k] = 'OK'\n"
            "    except Exception as e:\n"
            "        res[k] = 'BLOCKED:' + type(e).__name__\n"
            "open(RESULT, 'w').write(repr(res))\n")
    b64 = base64.b64encode((setup + body).encode()).decode()
    cmdline = '"%s" -I -B -c "import base64;exec(base64.b64decode(\'%s\').decode())"' % (
        sys.executable, b64)

    a32.CreateProcessAsUserW.argtypes = [
        ctypes.c_void_p, wt.LPCWSTR, wt.LPWSTR, ctypes.c_void_p, ctypes.c_void_p,
        wt.BOOL, wt.DWORD, ctypes.c_void_p, wt.LPCWSTR, ctypes.c_void_p,
        ctypes.c_void_p]
    a32.CreateProcessAsUserW.restype = wt.BOOL

    class SI2(ctypes.Structure):
        _fields_ = [("cb", wt.DWORD)] + [
            (f, getattr(ctypes, t)) for f, t in
            [("lpReserved", wt.LPWSTR), ("lpDesktop", wt.LPWSTR),
             ("lpTitle", wt.LPWSTR), ("dwX", wt.DWORD), ("dwY", wt.DWORD),
             ("dwXSize", wt.DWORD), ("dwYSize", wt.DWORD),
             ("dwXCountChars", wt.DWORD), ("dwYCountChars", wt.DWORD),
             ("dwFillAttribute", wt.DWORD), ("dwFlags", wt.DWORD),
             ("wShowWindow", wt.WORD), ("cbReserved2", wt.WORD),
             ("lpReserved2", ctypes.c_void_p), ("hStdInput", ctypes.c_void_p),
             ("hStdOutput", ctypes.c_void_p), ("hStdError", ctypes.c_void_p)]]

    class PI2(ctypes.Structure):
        _fields_ = [("hProcess", ctypes.c_void_p), ("hThread", ctypes.c_void_p),
                    ("dwProcessId", wt.DWORD), ("dwThreadId", wt.DWORD)]

    si = SI2()
    si.cb = ctypes.sizeof(si)
    pi = PI2()
    ok = a32.CreateProcessAsUserW(
        ctypes.c_void_p(tok), sys.executable,
        ctypes.create_unicode_buffer(cmdline), None, None, False,
        0x08000000, None, None, ctypes.byref(si), ctypes.byref(pi))
    print("CPAU:", bool(ok), "pid=", pi.dwProcessId, "err=", k32.GetLastError())
    if ok:
        k32.WaitForSingleObject.argtypes = [ctypes.c_void_p, wt.DWORD]
        k32.GetExitCodeProcess.argtypes = [ctypes.c_void_p, ctypes.POINTER(wt.DWORD)]
        k32.WaitForSingleObject(ctypes.c_void_p(pi.hProcess), 30000)
        code = wt.DWORD()
        k32.GetExitCodeProcess(ctypes.c_void_p(pi.hProcess), ctypes.byref(code))
        print("child exit:", code.value, hex(code.value & 0xFFFFFFFF))
        try:
            print("results:", open(out_file).read())
        except Exception as e:
            print("no result:", e)
