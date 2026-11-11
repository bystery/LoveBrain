# -*- coding: utf-8 -*-
"""win_runas.py — 以另一个本地账户拉起子进程并接管 stdio（Windows，无第三方依赖）。

为什么不用 subprocess：Windows 上 Popen(user=...) 不支持；跨用户必须
CreateProcessWithLogonW（不要求调用者持有 SeAssignPrimaryToken/SeImpersonate，
只需知道该账户口令）。G08 部署方案 N1/C1 的落地实现。

暴露：
  spawn_as_user(username, password, argv, cwd, env=None)
      -> _Child（.stdin/.stdout/.stderr 文本流、.pid、.poll()、.wait()、.kill()）

安全口径：
  - 口令只作为 API 参数传入内存，不拼进命令行/环境块/日志；
  - 环境块显式构造（不继承调用者环境，避免把凭据类变量带进沙箱）；
  - 子进程 stdio 用匿名管道，句柄仅在父子之间继承。
"""
import ctypes
import ctypes.wintypes as wt
import os
import subprocess
import time

CREATE_NO_WINDOW = 0x08000000
CREATE_UNICODE_ENVIRONMENT = 0x00000400
LOGON_WITH_PROFILE = 0x00000001
STARTF_USESTDHANDLES = 0x00000100
HANDLE_FLAG_INHERIT = 0x00000001
INFINITE = 0xFFFFFFFF
STILL_ACTIVE = 259
WAIT_TIMEOUT = 0x00000102


class _STARTUPINFOW(ctypes.Structure):
    _fields_ = [("cb", wt.DWORD), ("lpReserved", wt.LPWSTR),
                ("lpDesktop", wt.LPWSTR), ("lpTitle", wt.LPWSTR),
                ("dwX", wt.DWORD), ("dwY", wt.DWORD),
                ("dwXSize", wt.DWORD), ("dwYSize", wt.DWORD),
                ("dwXCountChars", wt.DWORD), ("dwYCountChars", wt.DWORD),
                ("dwFillAttribute", wt.DWORD), ("dwFlags", wt.DWORD),
                ("wShowWindow", wt.WORD), ("cbReserved2", wt.WORD),
                ("lpReserved2", ctypes.POINTER(ctypes.c_byte)),
                ("hStdInput", wt.HANDLE), ("hStdOutput", wt.HANDLE),
                ("hStdError", wt.HANDLE)]


class _PROCESS_INFORMATION(ctypes.Structure):
    _fields_ = [("hProcess", wt.HANDLE), ("hThread", wt.HANDLE),
                ("dwProcessId", wt.DWORD), ("dwThreadId", wt.DWORD)]


_k32 = ctypes.WinDLL("kernel32", use_last_error=True)
_adv = ctypes.WinDLL("advapi32", use_last_error=True)

_adv.CreateProcessWithLogonW.restype = wt.BOOL
_adv.CreateProcessWithLogonW.argtypes = [
    wt.LPCWSTR, wt.LPCWSTR, wt.LPCWSTR, wt.DWORD, wt.LPCWSTR, wt.LPWSTR,
    wt.DWORD, ctypes.c_void_p, wt.LPCWSTR,
    ctypes.POINTER(_STARTUPINFOW), ctypes.POINTER(_PROCESS_INFORMATION)]
_k32.WaitForSingleObject.argtypes = [wt.HANDLE, wt.DWORD]
_k32.GetExitCodeProcess.argtypes = [wt.HANDLE, ctypes.POINTER(wt.DWORD)]
_k32.TerminateProcess.argtypes = [wt.HANDLE, wt.UINT]
_k32.CloseHandle.argtypes = [wt.HANDLE]


def _quote(arg):
    """MSVCRT 命令行转义（与 subprocess.list2cmdline 同规则）。"""
    return subprocess.list2cmdline([arg])


def _env_block(env):
    parts = ["%s=%s" % (k, v) for k, v in sorted(env.items()) if v is not None]
    return ctypes.create_unicode_buffer("\0".join(parts) + "\0\0")


class _Child(object):
    """Popen 兼容子集：stdin/stdout/stderr 文本流 + poll/wait/kill/pid。"""

    def __init__(self, pi, files):
        self._pi = pi
        self._files = files
        self.stdin, self.stdout, self.stderr = files
        self.pid = int(pi.dwProcessId)
        self.returncode = None

    def _close_handles(self):
        for h in (self._pi.hThread,):
            try:
                _k32.CloseHandle(h)
            except Exception:
                pass

    def poll(self):
        if self.returncode is not None:
            return self.returncode
        r = _k32.WaitForSingleObject(self._pi.hProcess, 0)
        if r == WAIT_TIMEOUT:
            return None
        code = wt.DWORD(0)
        _k32.GetExitCodeProcess(self._pi.hProcess, ctypes.byref(code))
        self.returncode = int(code.value)
        return self.returncode

    def wait(self, timeout=None):
        ms = INFINITE if timeout is None else int(timeout * 1000)
        _k32.WaitForSingleObject(self._pi.hProcess, ms)
        return self.poll()

    def kill(self):
        _k32.TerminateProcess(self._pi.hProcess, 1)

    def close(self):
        for f in self._files:
            try:
                f.close()
            except Exception:
                pass
        self._close_handles()
        try:
            _k32.CloseHandle(self._pi.hProcess)
        except Exception:
            pass


def build_env(sandbox_tmp, extra=None):
    """显式环境块：只给最小可运行集，不继承调用者环境。"""
    env = {
        "SYSTEMROOT": os.environ.get("SYSTEMROOT", r"C:\Windows"),
        "WINDIR": os.environ.get("WINDIR", r"C:\Windows"),
        "PATH": os.environ.get("SYSTEMROOT", r"C:\Windows") + r"\system32",
        "TEMP": sandbox_tmp, "TMP": sandbox_tmp,
        "PYTHONHASHSEED": "0", "PYTHONIOENCODING": "utf-8",
        "PYTHONUTF8": "1", "PYTHONDONTWRITEBYTECODE": "1",
    }
    if extra:
        env.update({k: v for k, v in extra.items() if v is not None})
    return env


def spawn_as_user(username, password, argv, cwd, env=None, domain=None):
    """以 username 身份拉起 argv（不经过 shell），返回 _Child。

    失败一律抛 OSError（含 WinError），调用方按"冒烟不过即停止"处理。
    """
    if not isinstance(argv, (list, tuple)) or not argv:
        raise ValueError("argv 必须是非空序列")
    cmdline = ctypes.create_unicode_buffer(subprocess.list2cmdline(list(argv)))
    app = ctypes.create_unicode_buffer(argv[0])

    # 三条匿名管道：child_* 交给子进程，parent_* 留在控制器侧
    r_in, w_in = os.pipe()      # 我们写 w_in，子进程读 r_in
    r_out, w_out = os.pipe()    # 子进程写 w_out，我们读 r_out
    r_err, w_err = os.pipe()    # 子进程写 w_err，我们读 r_err
    h_in, h_out, h_err = (_msvcrt_handle(r_in), _msvcrt_handle(w_out),
                          _msvcrt_handle(w_err))

    si = _STARTUPINFOW()
    si.cb = ctypes.sizeof(_STARTUPINFOW)
    si.dwFlags = STARTF_USESTDHANDLES
    si.hStdInput = h_in
    si.hStdOutput = h_out
    si.hStdError = h_err
    si.lpDesktop = None

    envb = _env_block(env) if env is not None else None
    pi = _PROCESS_INFORMATION()
    ok = _adv.CreateProcessWithLogonW(
        username, domain, password, LOGON_WITH_PROFILE,
        ctypes.cast(app, wt.LPCWSTR), ctypes.cast(cmdline, wt.LPWSTR),
        CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT,
        ctypes.cast(envb, ctypes.c_void_p) if envb is not None else None,
        cwd, ctypes.byref(si), ctypes.byref(pi))
    if not ok:
        err = ctypes.get_last_error()
        for fd in (r_in, w_in, r_out, w_out, r_err, w_err):
            try:
                os.close(fd)
            except OSError:
                pass
        raise OSError(err, "CreateProcessWithLogonW failed (WinError %d)%s"
                      % (err, _hint(err)))

    # 父侧关掉子侧端点，避免 EOF 永远不到
    for fd in (r_in, w_out, w_err):
        try:
            os.close(fd)
        except OSError:
            pass
    files = (os.fdopen(w_in, "w", encoding="utf-8", newline="\n", buffering=1),
             os.fdopen(r_out, "r", encoding="utf-8", newline="\n"),
             os.fdopen(r_err, "r", encoding="utf-8", newline="\n", errors="replace"))
    return _Child(pi, files)


def _msvcrt_handle(fd):
    import msvcrt
    h = msvcrt.get_osfhandle(fd)
    if h == -1:
        raise OSError(ctypes.get_last_error(), "get_osfhandle failed")
    _k32.SetHandleInformation(wt.HANDLE(h), HANDLE_FLAG_INHERIT,
                              HANDLE_FLAG_INHERIT)
    return wt.HANDLE(h)


def _hint(err):
    if err == 1326:
        return "（用户名或口令错误）"
    if err == 1385:
        return "（该账户无此登录类型权限——检查 SeDenyInteractiveLogonRight " \
               "只应禁交互/远程，不应禁 batch/service）"
    if err == 1053:
        return "（Secondary Logon 服务未运行）"
    if err == 5:
        return "（拒绝访问：目标程序/目录 ACL 不含该账户）"
    return ""


def smoke(username, password, python_exe, cwd, env=None, timeout=60):
    """连通性冒烟：拉起 `python -c print(hello)`，返回 (ok, 详情)。"""
    ch = spawn_as_user(username, password, [python_exe, "-I", "-B", "-c",
                                            "print('SANDBOX_HELLO')"],
                       cwd=cwd, env=env)
    out, err = "", ""
    try:
        out = ch.stdout.read()
        err = ch.stderr.read()
        rc = ch.wait(timeout)
    finally:
        ch.close()
    ok = (rc == 0 and "SANDBOX_HELLO" in out)
    return ok, {"rc": rc, "stdout": out.strip()[:200],
                "stderr": err.strip()[-400:], "pid": ch.pid}
