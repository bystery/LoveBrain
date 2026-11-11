# -*- coding: utf-8 -*-
"""probe_restricted_token.py — 探测：无管理员下受限令牌宿主进程可行性。

机制：CreateRestrictedToken（自有主令牌派生，不需特权）+ CreateProcessAsUser。
限制 SID = Everyone + 自定义标记 SID（marker）。
  受限检查 = 仅用 restricting SIDs 评估 DACL：
  - 系统目录/python 目录若对 Everyone/AuthenticatedUsers 可读 → 通过
  - 仓库受保护文件只授了用户 ACE → 受限检查失败 → open 被拒
  - 给沙盒需要的目录显式授 marker ACE → 受限检查通过
验证点：
  1. 子进程能否启动并打印
  2. 子进程能否读 python 目录（必须成功）
  3. 子进程能否读一个未授 marker 的受保护文件（必须被拒）
  4. 子进程能否读一个授了 marker 的沙盒文件（必须成功）
"""
import ctypes
import ctypes.wintypes as wt
import os
import subprocess
import sys

k32 = ctypes.WinDLL("kernel32", use_last_error=True)
a32 = ctypes.WinDLL("advapi32", use_last_error=True)

SID_AND_ATTRIBUTES = wt.SID_AND_ATTRIBUTES if hasattr(wt, "SID_AND_ATTRIBUTES") else None
if SID_AND_ATTRIBUTES is None:
    class SID_AND_ATTRIBUTES(ctypes.Structure):
        _fields_ = [("Sid", ctypes.c_void_p), ("Attributes", ctypes.c_ulong)]


class TOKEN_GROUPS(ctypes.Structure):
    _fields_ = [("GroupCount", wt.DWORD),
                ("Groups", SID_AND_ATTRIBUTES * 8)]


class STARTUPINFOW(ctypes.Structure):
    _fields_ = [(f, getattr(ctypes, t)) for f, t in [
        ("cb", "c_ulong"), ("lpReserved", "c_wchar_p"),
        ("lpDesktop", "c_wchar_p"), ("lpTitle", "c_wchar_p"),
        ("dwX", "c_ulong"), ("dwY", "c_ulong"),
        ("dwXSize", "c_ulong"), ("dwYSize", "c_ulong"),
        ("dwXCountChars", "c_ulong"), ("dwYCountChars", "c_ulong"),
        ("dwFillAttribute", "c_ulong"), ("dwFlags", "c_ulong"),
        ("wShowWindow", "c_ushort"), ("cbReserved2", "c_ushort"),
        ("lpReserved2", "c_void_p"), ("hStdInput", "c_void_p"),
        ("hStdOutput", "c_void_p"), ("hStdError", "c_void_p")]]


class PROCESS_INFORMATION(ctypes.Structure):
    _fields_ = [("hProcess", ctypes.c_void_p), ("hThread", ctypes.c_void_p),
                ("dwProcessId", wt.DWORD), ("dwThreadId", wt.DWORD)]


a32.OpenProcessToken.argtypes = [ctypes.c_void_p, wt.DWORD,
                                 ctypes.POINTER(wt.HANDLE)]
a32.OpenProcessToken.restype = wt.BOOL
a32.ConvertStringSidToSidW.argtypes = [wt.LPCWSTR, ctypes.POINTER(ctypes.c_void_p)]
a32.ConvertStringSidToSidW.restype = wt.BOOL
a32.CreateRestrictedToken.argtypes = [ctypes.c_void_p, wt.DWORD, wt.DWORD,
                                      ctypes.c_void_p, wt.DWORD, ctypes.c_void_p,
                                      wt.DWORD,
                                      ctypes.POINTER(TOKEN_GROUPS),
                                      ctypes.POINTER(wt.HANDLE)]
a32.CreateRestrictedToken.restype = wt.BOOL
a32.CreateProcessAsUserW.argtypes = [ctypes.c_void_p, wt.LPCWSTR, wt.LPWSTR,
                                     ctypes.c_void_p, ctypes.c_void_p, wt.BOOL,
                                     wt.DWORD, ctypes.c_void_p, wt.LPCWSTR,
                                     ctypes.POINTER(STARTUPINFOW),
                                     ctypes.POINTER(PROCESS_INFORMATION)]
a32.CreateProcessAsUserW.restype = wt.BOOL
k32.GetCurrentProcess.restype = ctypes.c_void_p
k32.WaitForSingleObject.argtypes = [ctypes.c_void_p, wt.DWORD]
k32.GetExitCodeProcess.argtypes = [ctypes.c_void_p, ctypes.POINTER(wt.DWORD)]

TOKEN_ASSIGN_PRIMARY = 0x0001
TOKEN_DUPLICATE = 0x0002
TOKEN_IMPERSONATE = 0x0004
TOKEN_QUERY = 0x0008
TOKEN_ADJUST_PRIVILEGES = 0x0020
DUPLICATE_SAME_ACCESS = 0x0


SECURITY_ATTRIBUTES = ctypes.Structure
# SECURITY_ATTRIBUTES 简化（不用继承句柄）
class _SA(ctypes.Structure):
    _fields_ = [("nLength", wt.DWORD), ("lpSecurityDescriptor", ctypes.c_void_p),
                ("bInheritHandle", wt.BOOL)]


def str_to_sid(s):
    a32.ConvertStringSidToSidW.restype = ctypes.c_void_p
    p = ctypes.c_void_p()
    ok = a32.ConvertStringSidToSidW(s, ctypes.byref(p))
    if not ok:
        raise OSError("ConvertStringSidToSidW failed %d for %s"
                      % (k32.GetLastError(), s))
    return p


def main():
    # 1) 打开自有主令牌
    h = wt.HANDLE()
    k32.GetCurrentProcess.restype = ctypes.c_void_p
    pseudo = k32.GetCurrentProcess()
    ok = a32.OpenProcessToken(ctypes.c_void_p(pseudo),
                              TOKEN_ASSIGN_PRIMARY | TOKEN_DUPLICATE | TOKEN_QUERY,
                              ctypes.byref(h))
    print("OpenProcessToken:", bool(ok), "err=", k32.GetLastError())
    if not ok:
        return 1

    # 2) 构造 restricting SIDs = [Everyone, marker]
    sids = [str_to_sid("S-1-1-0"), str_to_sid("S-1-5-99-777000001")]
    groups = TOKEN_GROUPS()
    groups.GroupCount = len(sids)
    for i, p in enumerate(sids):
        groups.Groups[i].Sid = p
        groups.Groups[i].Attributes = 0

    h_restricted = wt.HANDLE()
    ok = a32.CreateRestrictedToken(
        h, 0,                    # flags
        0, None,                 # SIDsToDisable
        0, None,                 # PrivilegesToDelete
        2, ctypes.byref(groups),  # RestrictingSIDs
        ctypes.byref(h_restricted))
    print("CreateRestrictedToken:", bool(ok), "err=", k32.GetLastError())
    if not ok:
        return 1

    # 3) CreateProcessAsUser 无管理员尝试
    exe = sys.executable
    cmd = ctypes.create_unicode_buffer(
        '"%s" -I -B -c "print(\'RESTRICTED_ALIVE\')"' % exe)
    si = STARTUPINFOW()
    si.cb = ctypes.sizeof(si)
    pi = PROCESS_INFORMATION()
    ok = a32.CreateProcessAsUserW(
        h_restricted, exe, cmd, None, None, False,
        0x08000000,   # CREATE_NO_WINDOW
        None, None, ctypes.byref(si), ctypes.byref(pi))
    print("CreateProcessAsUserW:", bool(ok), "err=", k32.GetLastError())
    if not ok:
        return 2
    k32.WaitForSingleObject(ctypes.c_void_p(pi.hProcess), 15000)
    code = wt.DWORD()
    k32.GetExitCodeProcess(ctypes.c_void_p(pi.hProcess), ctypes.byref(code))
    print("child exit code:", code.value)
    return 0


if __name__ == "__main__":
    sys.exit(main())
