# -*- coding: utf-8 -*-
"""wincred.py — 沙箱账户凭据的落盘与取用（Windows DPAPI，user scope）。

为什么需要它：跨用户拉起宿主（CreateProcessWithLogonW）必须拿到明文口令，
但计划承诺"口令不落命令行/环境变量/日志"。折中且可审计的做法：
  口令只在部署进程内生成一次 → 用 CryptProtectData（CurrentUser 作用域）
  加密后落 D:\\arena-sandbox\\cred\\sandbox.cred（ACL 仅 abyss 可读）→
  运行时由同一账户（abyss）解密使用。
安全性等价于"abyss 账户可读"，不比该账户自身的文件权限更强；但**不是明文**，
且无法被其他账户（含沙箱账户本身）解密。

纯 ctypes，无第三方依赖；DPAPI 往返可在当前（非管理员）会话直接自测。
"""
import ctypes
import ctypes.wintypes as wt
import json
import os

CRYPTPROTECT_UI_FORBIDDEN = 0x1


class _DATA_BLOB(ctypes.Structure):
    _fields_ = [("cbData", wt.DWORD),
                ("pbData", ctypes.POINTER(ctypes.c_char))]


def _blob(data):
    buf = ctypes.create_string_buffer(data, len(data))
    return _DATA_BLOB(len(data), ctypes.cast(buf, ctypes.POINTER(ctypes.c_char))), buf


def _blob_bytes(blob):
    return ctypes.string_at(blob.pbData, blob.cbData)


def _call(fn, data, desc):
    inb, _keep = _blob(data)
    out = _DATA_BLOB()
    ok = fn(ctypes.byref(inb), desc, None, None, None,
            CRYPTPROTECT_UI_FORBIDDEN, ctypes.byref(out))
    if not ok:
        raise OSError(ctypes.get_last_error(),
                      "%s failed (WinError %d)" % (fn.__name__,
                                                   ctypes.get_last_error()))
    try:
        return _blob_bytes(out)
    finally:
        ctypes.windll.kernel32.LocalFree(out.pbData)


def protect(plain):
    return _call(ctypes.windll.crypt32.CryptProtectData,
                 plain.encode("utf-8") if isinstance(plain, str) else plain,
                 "arena-sandbox-cred")


def unprotect(cipher):
    return _call(ctypes.windll.crypt32.CryptUnprotectData, cipher,
                 "arena-sandbox-cred").decode("utf-8")


def write_cred(path, username, password):
    """写加密凭据文件（调用方负责目录 ACL）。返回写入字节数。"""
    d = os.path.dirname(path)
    if d:
        os.makedirs(d, exist_ok=True)
    blob = protect(json.dumps({"username": username, "password": password},
                              ensure_ascii=False))
    tmp = path + ".tmp"
    with open(tmp, "wb") as f:
        f.write(blob)
    os.replace(tmp, path)
    return len(blob)


def read_cred(path):
    """读回 {"username","password"}；文件缺失/解密失败 → RuntimeError（fail-closed）。"""
    if not os.path.isfile(path):
        raise RuntimeError("凭据文件不存在: %s" % path)
    raw = open(path, "rb").read()
    try:
        return json.loads(unprotect(raw))
    except OSError as e:
        raise RuntimeError("凭据解密失败（非本账户/本机加密，拒绝使用）: %s" % e)


if __name__ == "__main__":
    import tempfile
    import sys
    t = os.path.join(tempfile.mkdtemp(), "c.cred")
    pw = "Zx" + os.urandom(12).hex()
    n = write_cred(t, "ArenaHostSandbox", pw)
    back = read_cred(t)
    raw = open(t, "rb").read()
    ok = back["password"] == pw and pw.encode() not in raw
    print("DPAPI roundtrip bytes=%d match=%s plaintext_leak=%s"
          % (n, back["username"] == "ArenaHostSandbox" and ok,
             pw.encode() in raw))
    sys.exit(0 if ok else 1)
