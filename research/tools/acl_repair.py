# -*- coding: utf-8 -*-
"""tools/acl_repair.py — Deny(R,W) 锁死后用 WRITE_DAC 重建干净 DACL。

原理：Deny(R) 拒绝 READ_CONTROL → icacls 需要"读 ACL 再改"因此失败；
但 owner 对自有对象始终保有隐式 WRITE_DAC（显式 deny(R/W) 不含它）。
故用 CreateFileW(WRITE_DAC) + SetSecurityInfo(从零构造的 DACL) 恢复，
全程不需要读取文件内容或原 ACL。

用法：python tools/acl_repair.py <dir1> [dir2 ...]   # 递归处理树内全部对象
"""
import ctypes
import os
import sys
import uuid

k32 = ctypes.WinDLL("kernel32", use_last_error=True)
a32 = ctypes.WinDLL("advapi32", use_last_error=True)

WRITE_DAC = 0x00040000
FILE_SHARE_ALL = 0x7
OPEN_EXISTING = 3
FILE_FLAG_BACKUP_SEMANTICS = 0x02000000
SE_FILE_OBJECT = 1
DACL_SECURITY_INFORMATION = 4
DACL_PROTECTED = 0x1000  # 含 PROTECTED_DACL 在 SetSecurityInfo 的 SecurityInfo 里
ACL_REVISION2 = 2
ACCESS_ALLOWED_ACE_TYPE = 0
OI, CI = 0x1, 0x2

FILE_ALL = 0x1F01FF   # F
FILE_MOD = 0x1301BF   # M
FILE_RX = 0x1200A9    # RX

SIDS = {
    "BA": "S-1-5-32-544",   # BUILTIN\Administrators
    "SY": "S-1-5-18",       # SYSTEM
    "AU": "S-1-5-11",       # Authenticated Users
    "BU": "S-1-5-32-545",   # BUILTIN\Users
}


def strsid(sid_str):
    out = ctypes.c_void_p()
    a32.ConvertStringSidToSidW(ctypes.c_wchar_p(sid_str), ctypes.byref(out))
    return out


def psid(sid_str):
    s = strsid(sid_str)
    return ctypes.cast(s, ctypes.c_void_p)


def build_dacl(is_dir):
    """重建原版干净 DACL：BA:F / SY:F / AU:M / BU:RX（目录带 OI CI）。"""
    from ctypes import wintypes
    acl_size = wintypes.DWORD(4096)
    acl = ctypes.create_string_buffer(acl_size.value)
    if not a32.InitializeAcl(acl, acl_size, ACL_REVISION2):
        raise OSError("InitializeAcl")
    entries = [("BA", FILE_ALL), ("SY", FILE_ALL), ("AU", FILE_MOD),
               ("BU", FILE_RX)]
    for name, mask in entries:
        flags = (OI | CI) if is_dir else 0
        sid = psid(SIDS[name])
        if not a32.AddAccessAllowedAceEx(acl, ACL_REVISION2, flags, mask, sid):
            raise OSError("AddAccessAllowedAceEx %s" % name)
    return acl


def repair(path):
    is_dir = os.path.isdir(path)
    h = k32.CreateFileW(ctypes.c_wchar_p(path), WRITE_DAC, FILE_SHARE_ALL,
                        None, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, None)
    if h == -1 or h == 0xFFFFFFFFFFFFFFFF:
        return False, "CreateFileW err=%d" % k32.GetLastError()
    try:
        acl = build_dacl(is_dir)
        si_flags = DACL_SECURITY_INFORMATION | 0x1000  # + PROTECTED_DACL
        rc = a32.SetSecurityInfo(h, SE_FILE_OBJECT, si_flags, None, None, acl, None)
        return (rc == 0), "SetSecurityInfo rc=%d" % rc
    finally:
        k32.CloseHandle(h)


def main():
    n_ok = n_fail = 0
    for root in sys.argv[1:]:
        if not os.path.exists(root):
            print("skip(不存在):", root)
            continue
        for cur, dirs, files in os.walk(root, topdown=False, onerror=lambda e: None):
            for name in files + dirs:
                p = os.path.join(cur, name)
                ok, why = repair(p)
                n_ok += ok
                n_fail += (not ok)
                if not ok:
                    print("FAIL", p, why)
        ok, why = repair(root)
        n_ok += ok
        n_fail += (not ok)
        if not ok:
            print("FAIL", root, why)
    print("repair done: ok=%d fail=%d" % (n_ok, n_fail))
    return 0 if n_fail == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
