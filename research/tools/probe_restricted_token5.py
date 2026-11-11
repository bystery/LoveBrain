# -*- coding: utf-8 -*-
"""probe5 — 最后定位：SetNamedSecurityInfoW 授 marker ACE + CreateWindowStation 默认 SA。"""
import ctypes
import ctypes.wintypes as wt
import os
import shutil
import sys

k32 = ctypes.WinDLL("kernel32", use_last_error=True)
a32 = ctypes.WinDLL("advapi32", use_last_error=True)
u32 = ctypes.WinDLL("user32", use_last_error=True)
k32.GetCurrentProcess.restype = ctypes.c_void_p
a32.ConvertStringSidToSidW.argtypes = [wt.LPCWSTR, ctypes.POINTER(ctypes.c_void_p)]
a32.ConvertStringSidToSidW.restype = wt.BOOL
# SetNamedSecurityInfoW(str path, SE_FILE_OBJECT=1, DACL_SECURITY_INFORMATION=4, ..., PACL)
a32.SetNamedSecurityInfoW.argtypes = [wt.LPWSTR, wt.DWORD, wt.DWORD,
                                      ctypes.c_void_p, ctypes.c_void_p,
                                      ctypes.c_void_p, ctypes.c_void_p]
a32.SetNamedSecurityInfoW.restype = wt.DWORD
a32.SetEntriesInAclW.argtypes = [wt.ULONG, ctypes.c_void_p, ctypes.c_void_p,
                                 ctypes.POINTER(ctypes.c_void_p)]
a32.SetEntriesInAclW.restype = wt.DWORD



class _TRUSTEE_W(ctypes.Structure):
    _fields_ = [("pMultipleTrustee", ctypes.c_void_p),
                ("MultipleTrusteeOperation", wt.DWORD),
                ("TrusteeForm", wt.DWORD),      # TRUSTEE_IS_SID=0
                ("TrusteeType", wt.DWORD),      # TRUSTEE_IS_UNKNOWN=0
                ("ptstrName", ctypes.c_void_p)]


class EXPLICIT_ACCESS_W(ctypes.Structure):
    _fields_ = [("grfAccessPermissions", wt.DWORD),
                ("grfAccessMode", wt.DWORD),
                ("grfInheritance", wt.DWORD),
                ("Trustee", _TRUSTEE_W)]


MARKER = "S-1-5-99-777000001"
SUB_CONTAINERS_AND_OBJECTS = 0x3   # SE_DACL_AUTO_INHERIT? no: SUB_CONTAINERS(2)|SUB_OBJECTS(1)


def grant_marker_ace(path, perms):
    """对 path 授 marker ACE（对象+子树继承）。返回 (ok, detail)。"""
    psid = ctypes.c_void_p()
    if not a32.ConvertStringSidToSidW(MARKER, ctypes.byref(psid)):
        return False, "sid fail %d" % k32.GetLastError()
    ea = EXPLICIT_ACCESS_W()
    ea.grfAccessPermissions = perms
    ea.grfAccessMode = 2          # SET_ACCESS
    ea.grfInheritance = SUB_CONTAINERS_AND_OBJECTS
    ea.Trustee.TrusteeForm = 0    # TRUSTEE_IS_SID
    ea.Trustee.TrusteeType = 0    # TRUSTEE_IS_UNKNOWN
    ea.Trustee.ptstrName = psid.value
    new_dacl = ctypes.c_void_p()
    rc = a32.SetEntriesInAclW(1, ctypes.byref(ea), None, ctypes.byref(new_dacl))
    if rc:
        return False, "SetEntriesInAcl rc=%d" % rc
    rc = a32.SetNamedSecurityInfoW(path, 1, 4, None, None, new_dacl.value, None)
    return rc == 0, "SetNamedSecurityInfo rc=%d" % rc


def main():
    d = os.path.abspath("logs/_rt_probe5")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    ok, detail = grant_marker_ace(d, 0x1F01FF)   # FILE_ALL
    print("grant marker on out_dir:", ok, detail)
    ok2, d2 = grant_marker_ace(os.path.dirname(sys.executable), 0x2000B9)  # RX+X
    print("grant marker on pydir:", ok2, d2)
    # 验证
    import subprocess
    r = subprocess.run(["icacls", d], capture_output=True, text=True,
                       encoding="gbk", errors="replace")
    print("ACL has marker:", "777000001" in r.stdout)
    # 窗口站：默认 SA
    u32.CreateWindowStationW.argtypes = [wt.LPCWSTR, wt.DWORD, wt.DWORD, ctypes.c_void_p]
    u32.CreateWindowStationW.restype = ctypes.c_void_p
    hw = u32.CreateWindowStationW("arena_iso_ws5", 0, 0x37F, None)
    print("CreateWindowStation(default SA):", bool(hw), "err=", k32.GetLastError())
    return 0


if __name__ == "__main__":
    sys.exit(main())
