# -*- coding: utf-8 -*-
"""g08_deploy.py — G08 受限执行环境部署（方案 v2 的可执行落地，需管理员）。

用法：
  python -B -X utf8 tools/g08_deploy.py --check      # 只读预检（非管理员也可跑）
  python -B -X utf8 tools/g08_deploy.py --apply      # 部署（必须管理员）
  python -B -X utf8 tools/g08_deploy.py --rollback   # 回滚（必须管理员）
  python -B -X utf8 tools/g08_deploy.py --verify     # 只读核验部署终态

纪律（对应 refs/arena_v1/G08_DEPLOYMENT_PROPOSAL_20261022.md）：
  - 存量修改只有 2 项：主仓根目录 1 条 DENY ACE（M1）、禁止该账户交互/远程
    登录（M2）。其他 DACL/防火墙/账户一律不动。
  - 部署前留快照：icacls /save、secedit /export、netsh advfirewall export。
  - 停止条件：任一前置缺失或冒烟不过 → 停，不试错、不扩大范围。
  - 不复用 CodexSandboxOffline/Online（执行书 §2B 明令）。
"""
import argparse
import ctypes
import datetime
import json
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "arena"))
from isolation import win_runas, wincred  # noqa: E402

ACCOUNT = "ArenaHostSandbox"
SANDBOX = r"D:\arena-sandbox"
RUNTIME = os.path.join(SANDBOX, "runtime")
CRED = os.path.join(SANDBOX, "cred", "sandbox.cred")
FW_OUT = "ArenaSandbox-Block-Outbound"
FW_DNS = "ArenaSandbox-Block-DNS"
REPO = r"D:\lovebrain-arena"


def ts():
    return datetime.datetime.now().strftime("%Y%m%d_%H%M%S")


def is_admin():
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def run(argv, timeout=300, check=False):
    p = subprocess.run(argv, capture_output=True, text=True, encoding="utf-8",
                       errors="replace", timeout=timeout,
                       shell=isinstance(argv, str))
    if check and p.returncode != 0:
        raise RuntimeError("命令失败 rc=%d: %s\n%s\n%s"
                           % (p.returncode, argv, p.stdout[-800:],
                              p.stderr[-800:]))
    return p


def acct_exists():
    p = run(["powershell", "-NoProfile", "-Command",
             "(Get-LocalUser -Name '%s' -ErrorAction SilentlyContinue) -ne $null"
             % ACCOUNT])
    return p.stdout.strip().lower() == "true"


def fw_rule_exists(name):
    p = run(["powershell", "-NoProfile", "-Command",
             "(Get-NetFirewallRule -DisplayName '%s' -ErrorAction "
             "SilentlyContinue) -ne $null" % name])
    return p.stdout.strip().lower() == "true"


def root_acl_has_deny():
    p = run(["icacls", REPO])
    for line in (p.stdout or "").splitlines():
        if ACCOUNT in line and "(DENY)" in line:
            return True
    return False


def sandbox_acl_ok():
    if not os.path.isdir(SANDBOX):
        return False
    p = run(["icacls", SANDBOX])
    txt = p.stdout or ""
    return ACCOUNT in txt and "(OI)(CI)(M)" in txt.replace(" ", "")


def check():
    """只读预检：返回 (ok, 明细列表)。"""
    items = []
    items.append(("admin", is_admin(), "创建本地账户/防火墙/安全策略必须管理员"))
    items.append(("account", acct_exists(), "本地账户 %s" % ACCOUNT))
    items.append(("sandbox_root", os.path.isdir(SANDBOX), SANDBOX))
    items.append(("runtime", os.path.isfile(os.path.join(RUNTIME, "python.exe")),
                  "运行时拷贝"))
    items.append(("cred", os.path.isfile(CRED), "DPAPI 凭据文件"))
    items.append(("fw_out", fw_rule_exists(FW_OUT), FW_OUT))
    items.append(("fw_dns", fw_rule_exists(FW_DNS), FW_DNS))
    items.append(("repo_deny_ace", root_acl_has_deny(), "主仓根 DENY ACE"))
    items.append(("sandbox_acl", sandbox_acl_ok(), "沙箱根 ACL"))
    deployed = all(v for k, v, _ in items if k != "admin")
    ok = all(v for k, v, _ in items)
    print("G08 DEPLOY CHECK（只读）  admin=%s" % is_admin())
    for k, v, d in items:
        print("  [%s] %-14s %s" % ("OK" if v else "--", k, d))
    print("  deployed=%s" % deployed)
    return ok, items, deployed


def snapshot():
    d = os.path.join(ROOT, "logs", "g08_deploy_%s" % ts())
    os.makedirs(d, exist_ok=True)
    run(["icacls", REPO, "/save", os.path.join(d, "root.acl")], check=True)
    for sub in ("benchmark", "arena", "tools", "refs"):
        p = os.path.join(REPO, sub)
        if os.path.isdir(p):
            run(["icacls", p, "/save", os.path.join(d, "%s.acl" % sub)],
                check=True)
    run(["secedit", "/export", "/cfg", os.path.join(d, "secpol_before.inf")],
        check=True)
    run(["netsh", "advfirewall", "export", os.path.join(d, "fw_before.wfw")],
        check=True)
    run(["powershell", "-NoProfile", "-Command",
         "Get-LocalUser | Select-Object Name,Enabled,LastLogon | "
         "ConvertTo-Json"], timeout=120)
    meta = {"ts": ts(), "account": ACCOUNT, "sandbox": SANDBOX,
            "repo": REPO, "python_src": os.path.dirname(sys.executable)}
    with open(os.path.join(d, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
    print("SNAPSHOT -> %s" % d)
    return d


def create_account(snap):
    if acct_exists():
        print("ACCOUNT exists — skip create")
        return None
    pwd = "Aa1!" + os.urandom(18).hex()
    run(["net", "user", ACCOUNT, pwd, "/add",
         "/comment:G08 sandbox host account (arena)", "/expires:never",
         "/passwordchg:no"], check=True)
    run(["powershell", "-NoProfile", "-Command",
         "Set-LocalUser -Name '%s' -PasswordNeverExpires $true" % ACCOUNT],
        check=True)
    wincred.write_cred(CRED, ACCOUNT, pwd)
    run(["icacls", os.path.dirname(CRED), "/inheritance:r",
         "/grant", "abyss:(OI)(CI)F", "/grant", "SYSTEM:(OI)(CI)F"],
        check=True)
    print("ACCOUNT created; credential -> %s (DPAPI, abyss-only)" % CRED)
    with open(os.path.join(snap, "account_created.json"), "w",
              encoding="utf-8") as f:
        json.dump({"account": ACCOUNT, "created": ts(),
                   "cred_path": CRED}, f, ensure_ascii=False, indent=1)
    return pwd


def make_dirs():
    for d in (SANDBOX, RUNTIME, os.path.join(SANDBOX, "cred"),
              os.path.join(SANDBOX, "staged")):
        os.makedirs(d, exist_ok=True)
    run(["icacls", SANDBOX, "/inheritance:r",
         "/grant", "%s:(OI)(CI)M" % ACCOUNT,
         "/grant", "Administrators:(OI)(CI)F",
         "/grant", "SYSTEM:(OI)(CI)F",
         "/grant", "abyss:(OI)(CI)F"], check=True)
    print("DIRS ok (%s)" % SANDBOX)


def copy_runtime():
    src = os.path.dirname(sys.executable)
    dst_exe = os.path.join(RUNTIME, "python.exe")
    if os.path.isfile(dst_exe):
        print("RUNTIME exists — skip copy")
        return
    p = run(["robocopy", src, RUNTIME, "/E", "/NFL", "/NDL", "/NJH", "/NJS",
             "/R:1", "/W:1"], timeout=600)
    if p.returncode >= 8:
        raise RuntimeError("robocopy 失败 rc=%d\n%s" % (p.returncode,
                                                       p.stdout[-600:]))
    if not os.path.isfile(dst_exe):
        raise RuntimeError("运行时拷贝后缺 python.exe")
    print("RUNTIME copied -> %s" % RUNTIME)


def apply_repo_deny():
    if root_acl_has_deny():
        print("DENY ACE exists — skip")
        return
    run(["icacls", REPO, "/deny", "%s:(OI)(CI)F" % ACCOUNT], check=True)
    if not root_acl_has_deny():
        raise RuntimeError("DENY ACE 写入后未生效")
    print("M1 DENY ACE applied on %s" % REPO)


def _sid():
    p = run(["powershell", "-NoProfile", "-Command",
             "(Get-LocalUser -Name '%s').SID.Value" % ACCOUNT], check=True)
    return p.stdout.strip()


def _secpol_export(path):
    run(["secedit", "/export", "/cfg", path], check=True)
    return path


def _read_inf(path):
    """secedit 导出文件是 UTF-16LE+BOM；兼容 UTF-8。"""
    raw = open(path, "rb").read()
    if raw[:2] in (b"\xff\xfe", b"\xfe\xff"):
        return raw.decode("utf-16", errors="replace")
    return raw.decode("utf-8-sig", errors="replace")


def deny_logon_rights(snap):
    sid = _sid()
    cur = os.path.join(snap, "secpol_now.inf")
    _secpol_export(cur)
    txt = _read_inf(cur)
    keys = ("SeDenyInteractiveLogonRight", "SeDenyRemoteInteractiveLogonRight")
    out = []
    section = None
    seen = set()
    for line in txt.splitlines():
        if line.startswith("["):
            if section == "Privilege Rights" and seen != set(keys):
                for k in keys:
                    if k not in seen:
                        out.append("%s = *%s" % (k, sid))
            section = line.strip("[]")
            out.append(line)
            continue
        if section == "Privilege Rights" and "=" in line:
            k, v = [x.strip() for x in line.split("=", 1)]
            if k in keys:
                vals = [x for x in v.split(",") if x.strip()]
                if "*" + sid not in [x.strip() for x in vals]:
                    vals.append("*" + sid)
                line = "%s = %s" % (k, ",".join(vals))
                seen.add(k)
        out.append(line)
    if section == "Privilege Rights" and seen != set(keys):
        for k in keys:
            if k not in seen:
                out.append("%s = *%s" % (k, sid))
    new_inf = os.path.join(snap, "secpol_after.inf")
    with open(new_inf, "w", encoding="utf-8") as f:
        f.write("\r\n".join(out) + "\r\n")
    db = os.path.join(snap, "secpol.sdb")
    run(["secedit", "/configure", "/db", db, "/cfg", new_inf,
         "/areas", "USER_RIGHTS", "/quiet"], check=True)
    print("M2 deny interactive/remote logon for %s (SID %s)" % (ACCOUNT, sid))


def firewall():
    py = os.path.join(RUNTIME, "python.exe")
    for name, extra in ((FW_OUT, []),
                        (FW_DNS, ["-Protocol", "UDP", "-RemotePort", "53"])):
        if fw_rule_exists(name):
            print("FW %s exists — skip" % name)
            continue
        cmd = ["powershell", "-NoProfile", "-Command",
               "New-NetFirewallRule -DisplayName '%s' -Direction Outbound "
               "-Action Block -Program '%s' -Profile Any -RemoteAddress Any %s"
               % (name, py, " ".join(extra))]
        run(cmd, check=True)
    print("N4 firewall block rules applied (program=%s)" % py)
    run(["powershell", "-NoProfile", "-Command",
         "Get-NetFirewallRule -DisplayName 'ArenaSandbox-*' | "
         "Select-Object DisplayName,Enabled,Direction,Action | ConvertTo-Json"],
        timeout=120)


def smoke():
    cred = wincred.read_cred(CRED)
    tmp = os.path.join(SANDBOX, "staged", "tmp")
    os.makedirs(tmp, exist_ok=True)
    ok, detail = win_runas.smoke(cred["username"], cred["password"],
                                 os.path.join(RUNTIME, "python.exe"),
                                 cwd=tmp,
                                 env=win_runas.build_env(tmp))
    print("SMOKE ok=%s %s" % (ok, json.dumps(detail, ensure_ascii=False)))
    if not ok:
        raise RuntimeError("跨用户冒烟失败——停止，不进入验收/不追加变更")
    return detail


def verify():
    ok, items, deployed = check()
    cred = {}
    if os.path.isfile(CRED):
        try:
            cred = wincred.read_cred(CRED)
        except Exception as e:
            print("CRED unreadable: %s" % e)
    if cred:
        try:
            s_ok, s_detail = win_runas.smoke(
                cred["username"], cred["password"],
                os.path.join(RUNTIME, "python.exe"),
                cwd=os.path.join(SANDBOX, "staged"), env=win_runas.build_env(
                    os.path.join(SANDBOX, "staged")))
            print("SMOKE ok=%s %s" % (s_ok, json.dumps(s_detail,
                                                       ensure_ascii=False)))
        except Exception as e:
            print("SMOKE error: %s" % e)
    print("VERIFY deployed=%s" % deployed)
    return 0 if deployed else 1


def apply():
    if not is_admin():
        print(json.dumps({"error": "requires administrator",
                          "detail": "创建本地账户/防火墙/安全策略需要提权；"
                                    "本会话 admin=False，未做任何变更"},
                         ensure_ascii=False))
        return 2
    ok, items, deployed = check()
    snap = snapshot()
    create_account(snap)
    if not os.path.isfile(CRED):
        raise RuntimeError(
            "账户已存在但凭据文件缺失（无法取回口令）——请先 --rollback 后重跑，"
            "或手工重置该账户口令并重建 %s" % CRED)
    make_dirs()
    copy_runtime()
    apply_repo_deny()
    deny_logon_rights(snap)
    firewall()
    smoke()
    print("APPLY DONE — snapshot at %s" % snap)
    return 0


def rollback():
    if not is_admin():
        print(json.dumps({"error": "requires administrator"}, ensure_ascii=False))
        return 2
    for name in (FW_OUT, FW_DNS):
        if fw_rule_exists(name):
            run(["powershell", "-NoProfile", "-Command",
                 "Remove-NetFirewallRule -DisplayName '%s'" % name], check=True)
            print("FW removed: %s" % name)
    if root_acl_has_deny():
        run(["icacls", REPO, "/remove:d", ACCOUNT], check=True)
        print("M1 DENY ACE removed")
    if acct_exists():
        sid = _sid()
        snap = os.path.join(ROOT, "logs", "g08_rollback_%s" % ts())
        os.makedirs(snap, exist_ok=True)
        cur = _secpol_export(os.path.join(snap, "secpol_before_restore.inf"))
        txt = _read_inf(cur)
        out = []
        for line in txt.splitlines():
            if "=" in line and line.split("=", 1)[0].strip() in (
                    "SeDenyInteractiveLogonRight",
                    "SeDenyRemoteInteractiveLogonRight"):
                k, v = [x.strip() for x in line.split("=", 1)]
                vals = [x for x in v.split(",") if x.strip() and
                        x.strip() != "*" + sid]
                line = "%s = %s" % (k, ",".join(vals)) if vals else \
                    "%s =" % k
            out.append(line)
        new_inf = os.path.join(snap, "secpol_restore.inf")
        with open(new_inf, "w", encoding="utf-8") as f:
            f.write("\r\n".join(out) + "\r\n")
        run(["secedit", "/configure", "/db", os.path.join(snap, "s.sdb"),
             "/cfg", new_inf, "/areas", "USER_RIGHTS", "/quiet"], check=True)
        run(["net", "user", ACCOUNT, "/delete"], check=True)
        print("M2 restored; account deleted")
    if os.path.isdir(os.path.join(SANDBOX, "staged")):
        shutil.rmtree(os.path.join(SANDBOX, "staged"), ignore_errors=True)
    if os.path.isdir(SANDBOX):
        p = run(["icacls", SANDBOX, "/remove:d", ACCOUNT])
        print("sandbox dir retained (manual delete after evidence): %s" % SANDBOX)
    print("ROLLBACK DONE")
    return 0


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--check", action="store_true")
    g.add_argument("--apply", action="store_true")
    g.add_argument("--rollback", action="store_true")
    g.add_argument("--verify", action="store_true")
    a = ap.parse_args()
    if a.check:
        ok, _items, _d = check()
        return 0 if ok else 1
    if a.apply:
        print(json.dumps({
            "status": "WITHDRAWN_BY_SCOPE",
            "detail": "考生是 API（refs/arena_v1/SCOPE_DECISION_API_CANDIDATE_"
                      "20261022.md）：账户/防火墙/主仓 ACL 部署已撤回，"
                      "不需要管理员权限。本脚本不再执行任何系统变更。",
            "allowed": ["--check（只读说明）"]}, ensure_ascii=False, indent=1))
        return 3
    if a.rollback:
        print(json.dumps({
            "status": "NOTHING_TO_ROLLBACK",
            "detail": "本范围内从未执行部署；如需核对残留只读检查用 --check"},
            ensure_ascii=False, indent=1))
        return 3
    return verify()


if __name__ == "__main__":
    sys.exit(main())
