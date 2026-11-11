# -*- coding: utf-8 -*-
"""sandbox_mode.py — 受限执行环境（G08）的运行时开关。

设计原则：**默认关闭**。离线门禁（G01–G07/G09/G10）仍以当前用户运行，
只有显式开启时才以沙箱账户拉起宿主：

  LB_SANDBOX=1                 开启（缺凭据/缺运行时 → 立即失败，不静默回退）
  LB_SANDBOX_USER=名字         默认 ArenaHostSandbox
  LB_SANDBOX_CRED=路径         默认 D:\\arena-sandbox\\cred\\sandbox.cred
  LB_SANDBOX_PYTHON=路径       默认 D:\\arena-sandbox\\runtime\\python.exe
  LB_SANDBOX_ROOT=目录         默认 D:\\arena-sandbox

静默回退会等于"以为隔离了其实没隔离"，所以任何缺件都抛错。
"""
import os

ACCOUNT = "ArenaHostSandbox"
ROOT = r"D:\arena-sandbox"
CRED = os.path.join(ROOT, "cred", "sandbox.cred")
PYTHON = os.path.join(ROOT, "runtime", "python.exe")


def active():
    return os.environ.get("LB_SANDBOX", "") in ("1", "true", "True", "yes")


def config():
    """返回 {username,password,python,root}；未开启 → None；开启但缺件 → 抛错。"""
    if not active():
        return None
    from isolation import wincred
    user = os.environ.get("LB_SANDBOX_USER") or ACCOUNT
    cred_path = os.environ.get("LB_SANDBOX_CRED") or CRED
    python = os.environ.get("LB_SANDBOX_PYTHON") or PYTHON
    root = os.environ.get("LB_SANDBOX_ROOT") or ROOT
    if not os.path.isfile(cred_path):
        raise RuntimeError("LB_SANDBOX=1 但凭据文件缺失: %s" % cred_path)
    if not os.path.isfile(python):
        raise RuntimeError("LB_SANDBOX=1 但沙箱运行时缺失: %s" % python)
    if not os.path.isdir(root):
        raise RuntimeError("LB_SANDBOX=1 但沙箱根缺失: %s" % root)
    c = wincred.read_cred(cred_path)
    if c.get("username") != user:
        raise RuntimeError("凭据文件账户不符: %s != %s"
                           % (c.get("username"), user))
    return {"username": user, "password": c["password"],
            "python": python, "root": root}


def staged_root(name):
    """沙箱模式下 run 目录必须落在沙箱根（主仓对该账户 DENY）。"""
    import datetime
    import uuid
    return os.path.join(ROOT, "staged", "%s_%s_%s" % (
        name, datetime.datetime.now().strftime("%H%M%S"),
        uuid.uuid4().hex[:4]))


def require_under_root(path):
    cfg = config()
    if cfg is None:
        return
    real = os.path.realpath(path)
    root = os.path.realpath(cfg["root"])
    if not real.startswith(root + os.sep):
        raise RuntimeError(
            "沙箱模式要求 run 目录在 %s 之下（主仓对该账户 DENY）：%s"
            % (root, real))
