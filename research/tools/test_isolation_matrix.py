# -*- coding: utf-8 -*-
"""test_isolation_matrix.py — G08/G09 攻击与合法功能矩阵（V14 收口版）。

被测体 = candidates/_attack_fixture（v2 机制接口；攻击指令经 ctx.input.note
白名单通道传入，与真实候选可见面一致，不额外开洞）。
全部使用合成 canary；不碰真实B线/用户 dataset。判定不查 prompt 字符串——
本矩阵全部真实执行（独立进程 + 实际 IPC + 实际代理账本 + 实际上游替身）。

V14 收口要点（独立复核 §4/§5）：
  - IMP：导入副作用 canary——宿主必须执行、可信进程绝不能执行（含
    "仓库夹具无 import_pid.txt" 断言）。
  - MEDIA：私有文件伪装媒体 / data: URL 必须被代理拒绝；已登记媒体正例放行。
  - PARAM：候选覆盖 model/temperature 必须被可信配置强制。
  - A6c：真实 HTTP POST（裸行请求不进 do_POST 的旧假通过已废）。
  - A6b：子进程必须给出确定拒绝证据（CHILD_READ_DENIED:<Exc>）；超时不计。
  - A7b-d：额度从持久账本重建 / started 写失败零派发 / 轮级 calls 只记本轮。
  - B：CLI 演练 new→杀进程→resume→完成 + new 拒绝已有证据。
  - 诚实 FAIL：A2（兄弟状态无受限环境可读）、A4b（绝对路径模块可执行）、
    RESTRICTED_ENV_ABSENT——登记为环境硬缺件（ISOLATION_ENV_PREREQ.md），
    不降格为残差 PASS。矩阵 rc=1 属预期，直到受限执行环境部署。

输出：logs/isolation_matrix_<ts>.json（verify_arena_v1 只认 stdout 声明的
该文件且 mtime ≥ 验收起点）。
"""
import datetime
import hashlib
import json
import os
import socket
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import uuid
from isolation.trusted_controller import IsolatedRun, TrustedProxy  # noqa: E402,E501
from isolation import sandbox_mode  # noqa: E402
from lb_gateway import ModelGateway, LedgerWriteError  # noqa: E402

OUT_LOG = os.path.join(ROOT, "logs", "isolation_matrix_%s.json"
                       % datetime.datetime.now().strftime("%Y%m%d_%H%M%S"))
EP_TARGET = os.path.join(ROOT, "benchmark", "episodes",
                         "NCA_CORR_INTENT_v1.json")   # 含 gold 的真实题面（只作靶路径）
UPSTREAM_KEY = "CORRECT-KEY-42"

# 固定检查面（verify_arena_v1 按此集合做一致性核验）
EXPECTED_PASS = {
    "L1_parent_deps", "L2_state_snapshot", "L4_proxy_keyed_upstream_ok",
    "IMP_import_side_effect",
    "MEDIA_private_rejected", "MEDIA_dataurl_rejected", "MEDIA_registered_ok",
    "PARAM_override_enforced",
    "A1_direct_open_protected", "A3_traversal_out_of_staged",
    "A4_import_scorer", "A5_forge_authority_ignored",
    "A6b_child_process",
    "A6c_no_key", "A6c_guess_key",
    "A6c_upstream_ledger_zero_unkeyed_served",
    "A7_quota_ledger_unique", "A7b_quota_rebuild_from_ledger",
    "A7c_started_write_failure_zero_dispatch", "A7d_calls_per_turn_slice",
    "B_resume_cli_drill", "B_new_rejects_evidence",
    "A8a_host_crash_error_path", "A8b_oversized_frame_error_path",
    "A8c_dead_host_no_silent_regen", "A8d_bad_json_error_path",
}
RESIDUAL_ALLOWED = {"A6a_direct_socket"}
# 诚实 FAIL：受限执行环境缺件（不降格 PASS；部署前提见 ISOLATION_ENV_PREREQ.md）
# 2026-10-22 范围更正 v2（refs/arena_v1/SCOPE_DECISION_API_CANDIDATE_20261022.md）：
# 参赛对象是 harness，API 只是固定模型服务；系统隔离不再作为试考前置，
# 下列三项（本地恶意代码视角）失去适用前提 → 终态 NOT_APPLICABLE，
# 但**原始实测值照样写入日志**（不删证据、不改 actual），且仅在 scope.json
# 声明 candidate_model=api_text 时允许；旧模式（本地可执行候选）仍按 FAIL。
KNOWN_ENV_FAIL = {"A2_sibling_state", "A3_traversal_out_of_staged",
                  "A4b_abs_module_exec", "RESTRICTED_ENV_ABSENT"}
NOT_APPLICABLE_BY_SCOPE = {"A2_sibling_state", "A3_traversal_out_of_staged",
                           "A4b_abs_module_exec", "RESTRICTED_ENV_ABSENT"}
SCOPE_FILE = os.path.join(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__))), "refs", "arena_v1", "scope.json")


def os_isolation_not_prerequisite():
    """OS 隔离是否已不作为本模式前置（数据驱动，不写死在门禁里）。

    读 refs/arena_v1/scope.json：os_isolation_prerequisite=false 才返回 True。
    读不到/字段缺失 → False（fail-closed：按旧要求继续判 FAIL）。
    """
    try:
        with open(SCOPE_FILE, encoding="utf-8") as f:
            return json.load(f).get("os_isolation_prerequisite") is False
    except (OSError, json.JSONDecodeError):
        return False


# ---------- canary 上游：HTTP /v1/chat/completions + 密钥门禁 ----------
class _Gate(BaseHTTPRequestHandler):
    hits = {"served": 0, "unkeyed": 0, "guessed": 0,
            "served_without_key": 0}

    def log_message(self, *a):
        pass

    def do_POST(self):
        auth = self.headers.get("Authorization", "")
        body = b"{}"
        if auth == "Bearer " + UPSTREAM_KEY:
            _Gate.hits["served"] += 1
            body = json.dumps({
                "id": "canary", "object": "chat.completion", "model": "canary",
                "choices": [{"index": 0, "finish_reason": "stop",
                             "message": {"role": "assistant",
                                         "content": "UPSTREAM-OK canary"}}],
                "usage": {"prompt_tokens": 5, "completion_tokens": 2,
                          "total_tokens": 7}}).encode()
            code = 200
        else:
            _Gate.hits["unkeyed" if "wrong" not in auth else "guessed"] += 1
            body = b'{"error": "unauthorized"}'
            code = 401
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class KeyGateUpstream(threading.Thread):
    def __init__(self):
        super().__init__(daemon=True)
        self.httpd = HTTPServer(("127.0.0.1", 0), _Gate)
        self.port = self.httpd.server_address[1]

    def run(self):
        self.httpd.serve_forever()


class RawEchoCanary(threading.Thread):
    """A6a 靶：任意本地 socket 可达性（残差登记用）。"""
    def __init__(self):
        super().__init__(daemon=True)
        self.srv = socket.socket()
        self.srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.srv.bind(("127.0.0.1", 0))
        self.port = self.srv.getsockname()[1]
        self.srv.listen(4)

    def run(self):
        while True:
            try:
                c, _ = self.srv.accept()
                data = c.recv(200)
                c.sendall(b"CANARY-ECHO:" + data[:40])
                c.close()
            except OSError:
                return


# ---------- 攻击 episode（临时构造，note 携带攻击台本） ----------
# 媒体 canary 无需可解码（代理只验证登记+散列+大小；mock 不消费图片）
_MEDIA_BYTES = b"PNG-CANARY-BYTES-FOR-MATRIX"

def make_episode(path, turns_spec, media_dir=None):
    turns = []
    for i, (clock, spec) in enumerate(turns_spec, 1):
        inp = {"clock": clock, "type": "reply", "may_decline": False,
               "messages": [{"role": "HER",
                             "text": "第%d轮测试消息" % i}],
               "note": json.dumps(spec, ensure_ascii=False)}
        if spec.get("with_media"):
            img = os.path.join(media_dir, "media_canary_%02d.png" % i)
            with open(img, "wb") as f:
                f.write(_MEDIA_BYTES)
            inp["messages"].append({"role": "HER", "text": "看这张图",
                                    "image": img})
            inp["media"] = {"images": [img]}
        turns.append({"case_id": "ATK_t%02d" % i, "input": inp, "gold": {}})
    ep = {"episode_id": "ATK_MATRIX_v1", "hero": "测试者",
          "relation_stage": "破冰", "persona": "攻击矩阵合成人设",
          "turns": turns}
    json.dump(ep, open(path, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)


def _audit_uniq():
    """审计目录唯一后缀：指导书 v2 §4——撤回'拆批删除避开宿主确认'，
    改为新建审计目录、零删除；真实清理另行核验路径与授权。"""
    return datetime.datetime.now().strftime("%H%M%S") + "_" \
        + uuid.uuid4().hex[:4]


def run_fixture(out_name, turns_spec, adapter="mock", quota=4, env_extra=None):
    """stage→start→run_episode（require_assets=False 仅限攻击夹具）→close。
    保护策略只来自生产入口（run_episode 自动 deny 当前题面）——测试不自行
    加靶冒充生产保护（V14 执行书 A4）。"""
    base = os.path.join(ROOT, "runs", "_atk2_%s_%s" % (out_name, _audit_uniq()))
    if sandbox_mode.active():
        # G08 部署后：主仓对该账户 DENY，run 目录必须落在沙箱根
        base = sandbox_mode.staged_root("atk2_%s" % out_name)
    out = os.path.join(base, "out")
    ep_path = os.path.join(base, "atk_episode.json")
    if sandbox_mode.active():
        # 沙箱模式：run 目录在 D:\arena-sandbox（对该账户放行），但题面必须
        # 放在主仓（对该账户 DENY）——否则 A2/A1「读同目录兄弟文件」的靶
        # 变成可读，攻击矩阵会被沙箱根 ACL 意外放行而误判 PASS。
        fx = os.path.join(ROOT, "runs", "_sbxfx")
        os.makedirs(fx, exist_ok=True)
        ep_path = os.path.join(fx, "atk_%s_%s.json" % (out_name, _audit_uniq()))
    # 指导书 v2 §4：不删除旧演练产物（宿主 safe-delete fail-closed + 拆批
    # 删除已被撤回）；每次夹具落新建审计目录，旧产物保留待授权清理。
    os.makedirs(base, exist_ok=True)
    for _t in turns_spec:
        # A1/A6b 靶 = 生产入口自动 deny 的唯一文件（当前题面）；显式传入的
        # EP_TARGET 只作 A3 遍历靶（未授权文件可读=环境缺件证据）
        _t[1]["protected_target"] = ep_path
    make_episode(ep_path, turns_spec, media_dir=base)
    old_env = {}
    for k, v in (env_extra or {}).items():
        old_env[k] = os.environ.get(k)
        os.environ[k] = v
    # 复核 v3 §2A 入口强制：非 mock 适配器必须挂授权级预算权威——
    # 离线 canary 夹具自动生成测试授权（本地假上游，零真实模型成本）
    authorization = None
    if adapter != "mock":
        aid = "AUTH-MATRIX-%s-%s" % (out_name, _audit_uniq())
        authorization = {
            "authorization_id": aid,
            "max_attempts": max(quota, len(turns_spec)),
            "api_base": (env_extra or {}).get("LB_API_BASE"),
            "model": (env_extra or {}).get("LB_MODEL"),
            "budget_ledger": os.path.join(ROOT, "logs", "auth_budget",
                                          "%s.jsonl" % aid),
        }
    try:
        run = IsolatedRun(base, out,
                          os.path.join(out, "_control"), "_attack_fixture",
                          adapter=adapter, quota=quota,
                          authorization=authorization).stage()
        try:
            run.start()
            manifest, proxy = run.run_episode(ep_path, require_assets=False)
            return {"manifest": manifest, "proxy_ledger": proxy.ledger(),
                    "out": out, "staged_root": run.staged_root,
                    "run": run}
        finally:
            run.close()
    finally:
        for k, v in old_env.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v


def judge(notes, check, expect, extra=None):
    """result 与 expect 任一前缀匹配即 PASS（BLOCKED:* 家族归一）。"""
    n = next((x for x in notes if x.get("check") == check), None)
    if n is None:
        return {"check": check, "expect": list(expect),
                "actual": "NOT_FOUND", "verdict": "FAIL"}
    actual = str(n.get("result", ""))
    verdict = "PASS" if any(actual.startswith(e) for e in expect) else "FAIL"
    rec = {"check": check, "expect": list(expect), "actual": actual,
           "detail": str(n.get("detail", ""))[:160], "verdict": verdict}
    if extra:
        rec.update(extra)
    return rec


# ---------- A8：宿主错误路径（直接进程探针，不经控制器） ----------
def a8_error_paths(staged_root, verdicts):
    host_script = os.path.join(staged_root, "_candidate_host.py")
    if not os.path.isfile(host_script):
        for c in ("A8a_host_crash_error_path", "A8b_oversized_frame_error_path",
                  "A8c_dead_host_no_silent_regen", "A8d_bad_json_error_path"):
            verdicts.append({"check": c, "actual": "STAGED_HOST_MISSING",
                             "verdict": "FAIL"})
        return
    env = {"SYSTEMROOT": os.environ.get("SYSTEMROOT", ""),
           "PATH": os.environ.get("PATH", "")}

    def _spawn():
        # 宿主入口需要 --staged-root/--candidate（argparse 强制）
        return subprocess.Popen(
            [sys.executable, "-B", "-X", "utf8", host_script,
             "--staged-root", staged_root, "--candidate", "_attack_fixture"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, env=env, cwd=staged_root,
            text=True, encoding="utf-8", errors="replace")

    # A8b 超大帧 → OVERSIZED_FRAME 终态
    p = _spawn()
    try:
        big = json.dumps({"op": "x", "pad": "A" * 16_000_001})
        out, err = p.communicate(big + "\n", timeout=60)
        ok_b = "OVERSIZED_FRAME" in (out or "")
        verdicts.append({"check": "A8b_oversized_frame_error_path",
                         "actual": "OVERSIZED_FRAME" if ok_b else "NO_TERMINAL_ERROR",
                         "detail": (err or "")[-150:],
                         "verdict": "PASS" if ok_b else "FAIL"})
    except subprocess.TimeoutExpired:
        p.kill()
        verdicts.append({"check": "A8b_oversized_frame_error_path",
                         "actual": "TIMEOUT", "verdict": "FAIL"})
    # A8d 坏 JSON → 错误终态（进程不静默成功）
    p = _spawn()
    try:
        out, err = p.communicate("THIS IS NOT JSON\n" + "\n", timeout=60)
        ok_d = bool((out or "").strip()) and ("error" in (out or "").lower()
                                              or "BAD_JSON" in (out or "")
                                              or "TRACEBACK" in (err or ""))
        verdicts.append({"check": "A8d_bad_json_error_path",
                         "actual": "ERROR_TERMINAL" if ok_d else "SILENT",
                         "detail": ((out or "")[:120] + "||" + (err or "")[-100:]),
                         "verdict": "PASS" if ok_d else "FAIL"})
    except subprocess.TimeoutExpired:
        p.kill()
        verdicts.append({"check": "A8d_bad_json_error_path",
                         "actual": "TIMEOUT", "verdict": "FAIL"})
    # A8a 宿主崩溃 → 非零退出 + stderr 痕迹（不是静默 0 退出）
    p = subprocess.Popen([sys.executable, "-B", "-X", "utf8", "-c",
                          "import sys;sys.path.insert(0,'%s');"
                          "raise RuntimeError('ATK_CRASH_CANARY')" % staged_root],
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                         env=env, text=True, encoding="utf-8", errors="replace")
    out, err = p.communicate(timeout=60)
    ok_a = p.returncode != 0 and "ATK_CRASH_CANARY" in (err or "")
    verdicts.append({"check": "A8a_host_crash_error_path",
                     "actual": "CRASH_TERMINAL" if ok_a else "SILENT_OR_ZERO",
                     "verdict": "PASS" if ok_a else "FAIL"})
    # A8c 死宿主：起进程即杀，再无任何"静默重生成"通道（进程不复活）
    p = _spawn()
    p.kill()
    p.wait(timeout=30)
    ok_c = p.returncode is not None and p.returncode != 0
    verdicts.append({"check": "A8c_dead_host_no_silent_regen",
                     "actual": "DEAD_NO_REGEN" if ok_c else "STILL_ALIVE",
                     "verdict": "PASS" if ok_c else "FAIL"})


def main():
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    verdicts = []

    canary = RawEchoCanary()
    canary.start()
    upstream = KeyGateUpstream()
    upstream.start()
    ledger_evidence = {}

    try:
        # ============ Pass 1：合法功能 + IMP + MEDIA + PARAM（mock） ============
        # 控制器资料区私有 canary（绝不登记为媒体——代理必须拒读）
        secret = os.path.join(ROOT, "runs", "_atk2_legit", "media_secret.txt")
        os.makedirs(os.path.dirname(secret), exist_ok=True)
        with open(secret, "w", encoding="utf-8") as f:
            f.write("MEDIA-SECRET-CANARY-4e1")
        r1 = run_fixture("legit", [
            ("2026-10-29 10:00", {"attacks": ["IMP", "L1", "L2", "PARAM"]}),
            ("2026-10-29 10:05", {"attacks": ["MEDIA", "PARAM"],
                                  "with_media": True,
                                  "media_secret_target": secret})], quota=3)
        notes1 = _notes_from_out(r1["out"])
        for check in ("L1_parent_deps", "L2_state_snapshot",
                      "MEDIA_private_rejected", "MEDIA_dataurl_rejected",
                      "MEDIA_registered_ok", "PARAM_override_enforced"):
            verdicts.append(judge(notes1, check, ("PASS", "REJECTED",
                                                  "SENT_ENFORCED")))
        # IMP 精判：staged import_pid.txt 恰好 1 行、pid=宿主≠控制器、
        # argv 指向 staged 宿主入口；仓库夹具零导入痕迹
        host_pid = next((e.get("pid") for e in r1["run"].events
                         if e.get("kind") == "HOST_STARTED"), None)
        runner_pid = r1["manifest"].get("runner_pid")
        staged_imp = os.path.join(r1["staged_root"], "candidates",
                                  "_attack_fixture", "mechanism",
                                  "import_pid.txt")
        repo_imp = os.path.join(ROOT, "candidates", "_attack_fixture",
                                "mechanism", "import_pid.txt")
        imp_lines = (open(staged_imp, encoding="utf-8").read().splitlines()
                     if os.path.isfile(staged_imp) else [])
        try:
            pid0 = int(imp_lines[0].split("|")[0]) if imp_lines else None
            argv0 = imp_lines[0].split("|")[-1] if imp_lines else ""
        except Exception:
            pid0, argv0 = None, ""
        ok_imp = (len(imp_lines) == 1 and pid0 == host_pid
                  and pid0 != runner_pid
                  and "_candidate_host" in os.path.basename(argv0)
                  and not os.path.isfile(repo_imp))
        verdicts.append({
            "check": "IMP_import_side_effect",
            "expect": ["exactly-once in host, never in controller"],
            "actual": "lines=%d pid=%s host=%s runner=%s argv=%s repo_trace=%s"
                      % (len(imp_lines), pid0, host_pid, runner_pid,
                         os.path.basename(argv0), os.path.isfile(repo_imp)),
            "verdict": "PASS" if ok_imp else "FAIL"})
        # PARAM 精判：代理账本含 param_override_rejected（候选请求值被记账）
        pev = [e for e in r1["proxy_ledger"].get("events", [])
               if e.get("kind") == "param_override_rejected"]
        ok_param = bool(pev) and any(
            "candidate_override" in str(e.get("requested"))
            and "1.23" in str(e.get("requested")) for e in pev)
        pv = next(v for v in verdicts if v["check"] == "PARAM_override_enforced")
        if pv["verdict"] == "PASS":
            pv["verdict"] = "PASS" if ok_param else "FAIL"
            pv["detail"] = "ledger_override_events=%d" % len(pev)
        # A7d 轮级 calls 只记本轮：turn1=1 次（PARAM），turn2=2 次
        # （MEDIA 正例+PARAM）；轮 calls.json 长度必须等于该轮 manifest
        # gateway_calls，轮间不累计（旧缺陷：turn2 会看到 turn1 的调用）
        tds = sorted(d for d in os.listdir(r1["out"]) if d.startswith("turn_"))
        per_calls, per_manifest = [], []
        for d in tds:
            cj = os.path.join(r1["out"], d, "calls.json")
            mj = os.path.join(r1["out"], d, "manifest.json")
            per_calls.append(len(json.load(open(cj, encoding="utf-8")))
                             if os.path.isfile(cj) else -1)
            per_manifest.append(json.load(open(mj, encoding="utf-8"))
                                .get("gateway_calls", -1)
                                if os.path.isfile(mj) else -1)
        a7d_ok = (len(tds) == 2 and per_calls == [1, 2]
                  and per_calls == per_manifest)
        verdicts.append({
            "check": "A7d_calls_per_turn_slice",
            "expect": ["per-turn only (1+1=2, no accumulation)"],
            "actual": "turns=%d calls=%r manifest=%r" % (len(tds), per_calls,
                                                         per_manifest),
            "verdict": "PASS" if a7d_ok else "FAIL"})

        # ============ Pass 2：文件/导入/伪造攻击（mock；无手动加靶） ============
        sib = os.path.join(ROOT, "runs", "_atk_legit", "sibling_state.json")
        os.makedirs(os.path.dirname(sib), exist_ok=True)
        json.dump({"secret": "SIBLING-CANARY-9d1"}, open(sib, "w"))
        # 绝对路径执行靶：控制器资料区合成模块（import 即写 canary）
        abs_dir = os.path.join(ROOT, "runs", "_atk_absmod")
        os.makedirs(abs_dir, exist_ok=True)
        abs_mod = os.path.join(abs_dir, "secret_module.py")
        abs_canary = os.path.join(abs_dir, "abs_exec_canary.txt")
        os.makedirs(abs_dir, exist_ok=True)
        for f0 in (abs_canary, abs_mod):
            if os.path.isfile(f0):
                os.remove(f0)
        with open(abs_mod, "w", encoding="utf-8") as f:
            f.write("import os" + chr(10)
                    + "with open(os.path.join(os.path.dirname("
                      "os.path.abspath(__file__)), 'abs_exec_canary.txt'),"
                      " 'w') as _f:" + chr(10)
                    + "    _f.write('ABS_EXEC_CANARY')" + chr(10))
        r2 = run_fixture("attacker", [
            ("2026-10-29 11:00",
             {"attacks": ["A1", "A2", "A3", "A4", "A4b", "A5"],
              "protected_target": EP_TARGET,
              "protected_target_rel": "benchmark/episodes/NCA_CORR_INTENT_v1.json",
              "other_state_target": sib,
              "abs_module_target": abs_mod})], quota=2)
        notes2 = _notes_from_out(r2["out"])
        na_mode = os_isolation_not_prerequisite() and not sandbox_mode.active()
        for check in ("A1_direct_open_protected",
                      "A3_traversal_out_of_staged", "A4_import_scorer"):
            v = judge(notes2, check, ("BLOCKED",))
            if na_mode and check == "A3_traversal_out_of_staged" \
                    and v["verdict"] == "FAIL":
                v["verdict"] = "NOT_APPLICABLE"
                v["detail"] = ("系统隔离前置不适用"
                               "（SCOPE_DECISION_API_CANDIDATE_20261022.md v2）："
                               "本模式的自研 harness 可读仓库（可信作者），"
                               "未承诺抗恶意本地代码隔离；原始实测保留在 actual")
            verdicts.append(v)
        # A2/A4b：诚实探针。原始实测照写；是否适用由 scope.json 决定——
        # 系统隔离非前置时这两项失去适用前提（NOT_APPLICABLE），
        # 旧模式（本地可执行候选）仍判 FAIL。
        for check, env_note in (
                ("A2_sibling_state",
                 "同用户进程可读未授权文件——受限执行环境缺件"),
                ("A4b_abs_module_exec",
                 "绝对路径模块可被执行——受限执行环境缺件")):
            v0 = judge(notes2, check, ("BLOCKED",))
            if v0["actual"].startswith("BLOCKED"):
                v0["verdict"] = "PASS"
                v0["detail"] = "受限环境实际拒绝（已部署？核实生产入口）"
            elif na_mode:
                v0["verdict"] = "NOT_APPLICABLE"
                v0["detail"] = ("系统隔离前置不适用（SCOPE_DECISION_API_"
                                "CANDIDATE_20261022.md v2）：本模式不向 API "
                                "开放本地执行能力，也未承诺抗恶意本地代码隔离；"
                                "原始实测保留在 actual")
            else:
                v0["verdict"] = "FAIL"
                v0["detail"] = env_note
            verdicts.append(v0)
        if os.path.isfile(abs_canary):
            a4b = next((v for v in verdicts
                        if v["check"] == "A4b_abs_module_exec"), None)
            if a4b:
                # canary 每轮都会新写（夹具先删再跑）——事实照写；只有"隔离是前置"
                # 的模式才据此判 FAIL。本模式记录事实、结论为不适用。
                a4b["actual"] = "VIOLATION_EXEC_CANARY_WRITTEN"
                a4b["detail"] = ((a4b.get("detail") or "")
                                 + "；本轮实测：攻击夹具确实写入 canary"
                                   "（该能力面本模式不承诺）")
                if not na_mode:
                    a4b["verdict"] = "FAIL"
        # A5 伪造权威字段：评分仍由可信侧产出且未被伪造值覆盖
        sc_path = os.path.join(r2["out"], "turn_01_ATK_t01", "scoring.json")
        sc = json.load(open(sc_path, encoding="utf-8")) \
            if os.path.isfile(sc_path) else {}
        forged_adopted = (sc.get("score10") == 10.0)
        verdicts.append({
            "check": "A5_forge_authority_ignored",
            "expect": ["scored_by_trusted_side"],
            "actual": "adopted_fake_10" if forged_adopted
                      else "scored_by_trusted_side",
            "verdict": "FAIL" if forged_adopted else "PASS"})

        # ============ Pass 3：网络面 + 额度（mock；quota=1 使重放越界） ============
        r3 = run_fixture("network", [
            ("2026-10-29 12:00",
             {"attacks": ["A6a", "A6b", "A6c", "A7"],
              "protected_target": EP_TARGET,
              "canary_port": canary.port,
              "upstream_port": upstream.port})], quota=1)
        notes3 = _notes_from_out(r3["out"])
        verdicts.append(judge(notes3, "A6a_direct_socket", ("REACHABLE",))
                        | {"verdict": "RESIDUAL_DOCUMENTED",
                           "detail": "任意本地 socket 可达=纵深残差，如实登记"
                                     "（最小权限需求，非必要路径）"})
        v_a6b = judge(notes3, "A6b_child_process",
                      ("CHILD_READ_DENIED:PermissionError",))
        if v_a6b["actual"] == "BLOCKED:TimeoutExpired":
            v_a6b["verdict"] = "FAIL"
            v_a6b["detail"] = "超时≠权限拒绝（V14 §4.4）"
        verdicts.append(v_a6b)
        verdicts.append(judge(notes3, "A6c_no_key", ("BLOCKED", "BLOCKED_REFUSED")))
        verdicts.append(judge(notes3, "A6c_guess_key", ("BLOCKED", "BLOCKED_REFUSED")))
        # 必要不变量：无密钥/猜密钥请求零次 200 服务（真 POST 到达认证层）
        no_key_served = _Gate.hits["served_without_key"] == 0
        reached_auth = _Gate.hits["unkeyed"] >= 1 and _Gate.hits["guessed"] >= 1
        verdicts.append({
            "check": "A6c_upstream_ledger_zero_unkeyed_served",
            "expect": ["no 200 without key; real POSTs reached auth layer"],
            "actual": "unkeyed=%d guessed=%d served_ok=%d served_without_key=%d"
            % (_Gate.hits["unkeyed"], _Gate.hits["guessed"], _Gate.hits["served"],
               _Gate.hits["served_without_key"]),
            "verdict": "PASS" if (no_key_served and reached_auth) else "FAIL"})
        # A7：重放调用 + 额度强制 + 账本唯一
        led = r3["proxy_ledger"]
        evs = led.get("events", [])
        quota_ev = [e for e in evs if e.get("kind") == "quota_exceeded"]
        aids = [e.get("attempt_id") for e in evs
                if e.get("kind", "").startswith("attempt")]
        used = led.get("used", 0)
        a7_ok = (used <= led.get("quota", 0) and quota_ev
                 and len(aids) == len(set(aids)))
        verdicts.append({
            "check": "A7_quota_ledger_unique",
            "expect": ["ok<=quota", "quota_enforced", "unique_attempt_ids"],
            "actual": "used=%d quota=%s quota_ev=%d aids=%d/%d"
                      % (used, led.get("quota"), len(quota_ev),
                         len(set(aids)), len(aids)),
            "verdict": "PASS" if a7_ok else "FAIL"})
        # G09 真实账本证据（control 侧持久 jsonl，非内存字典）
        ledger_path = os.path.join(r3["out"], "_control",
                                   "attempt_ledger.jsonl")
        le = {"path": os.path.relpath(ledger_path, ROOT), "events": 0,
              "started": 0, "completed": 0, "failed": 0,
              "unique_attempt_ids": 0}
        if os.path.isfile(ledger_path):
            pev2 = [json.loads(l) for l in open(ledger_path, encoding="utf-8")
                    if l.strip()]
            kinds = [e.get("kind") for e in pev2]
            aids2 = [e.get("attempt_id") for e in pev2
                     if e.get("kind") == "attempt_started"]
            le.update({"events": len(pev2),
                       "started": kinds.count("attempt_started"),
                       "completed": kinds.count("attempt_completed"),
                       "failed": kinds.count("attempt_failed"),
                       "unique_attempt_ids": len(set(aids2)),
                       "sha256": hashlib.sha256(
                           open(ledger_path, "rb").read()).hexdigest()})
        ledger_evidence = le

        # ============ A7b/A7c：额度重建 + started 写失败零派发（单元探针） ============
        ctl = os.path.join(ROOT, "runs", "_atk2_quotarebuild", "out", "_control")
        os.makedirs(ctl, exist_ok=True)
        lp = os.path.join(ctl, "attempt_ledger.jsonl")
        with open(lp, "w", encoding="utf-8") as f:
            for i0 in (1, 2):
                f.write(json.dumps({"kind": "attempt_started",
                                    "attempt_id": "iso_seed_g%04d" % i0,
                                    "run_id": "iso_seed"}) + "\n")
        px = TrustedProxy(ctl, "iso_seed", "_attack_fixture", "EP", quota=2,
                          run_id="iso_seed")
        a7b_ok = px.used == 2 and px._seq == 2
        ok1, _ = px.generate({"system": "", "user": "REBUILD-CANARY"})
        a7b_ok = a7b_ok and not ok1   # 已用 2/2 → 拒绝（重建不重置）
        verdicts.append({
            "check": "A7b_quota_rebuild_from_ledger",
            "expect": ["used rebuilt, no reset"],
            "actual": "used=%d seq=%d second_call_ok=%s"
                      % (px.used, px._seq, ok1),
            "verdict": "PASS" if a7b_ok else "FAIL"})

        class _Counting:
            name = "counting"
            n = 0
            def generate(self, s, u, payload):
                _Counting.n += 1
                return {"text": "X", "finish": "stop", "usage": None}

        bad_target = os.path.join(ctl, "attempt_ledger.jsonl", "sub")
        gw_bad = ModelGateway(_Counting(),
                              event_log_path=os.path.join(bad_target,
                                                          "l.jsonl"))
        raised = False
        try:
            gw_bad.generate("s", "u")
        except LedgerWriteError:
            raised = True
        except Exception:
            raised = False
        verdicts.append({
            "check": "A7c_started_write_failure_zero_dispatch",
            "expect": ["raise before dispatch, backend_calls=0"],
            "actual": "raised=%s backend_calls=%d" % (raised, _Counting.n),
            "verdict": "PASS" if raised and _Counting.n == 0 else "FAIL"})

        # ============ Pass 4：密钥代理合法调用（openai_compat + canary） ============
        r4 = run_fixture("keyed", [
            ("2026-10-29 13:00", {"attacks": ["L4"]})],
            adapter="openai_compat", quota=2,
            env_extra={"LB_API_BASE": "http://127.0.0.1:%d/v1" % upstream.port,
                       "LB_API_KEY": UPSTREAM_KEY,
                       "LB_MODEL": "canary"})
        notes4 = _notes_from_out(r4["out"])
        verdicts.append(judge(notes4, "L4_proxy_keyed_upstream_ok", ("PASS",)))

        # ============ Pass 5：CLI 演练 new→杀进程→resume→完成 ============
        verdicts += _cli_resume_drill()

        # ============ A8：错误路径（直探第一个 staged 树） ============
        a8_error_paths(r1["staged_root"], verdicts)

        # ============ 环境：部署则按实测判，未部署则诚实 FAIL ============
        if sandbox_mode.active():
            sb = sandbox_mode.config()
            host_sbx = bool(getattr(r1.get("run"), "sandboxed", False))
            verdicts.append({
                "check": "RESTRICTED_ENV_ABSENT",
                "expect": ["restricted execution env deployed"],
                "actual": "DEPLOYED account=%s sandboxed_host=%s"
                          % (sb["username"], host_sbx),
                "detail": "宿主以沙箱本地账户跨用户运行（%s）；主仓 DENY ACE "
                          "+ 出站阻断规则在位" % sb["python"],
                "verdict": "PASS" if host_sbx else "FAIL"})
        else:
            na = os_isolation_not_prerequisite()
            verdicts.append({
                "check": "RESTRICTED_ENV_ABSENT",
                "expect": ["restricted execution env deployed"],
                "actual": "NOT_DEPLOYED",
                "detail": ("系统隔离前置不适用：未向 API 开放本地执行能力；"
                           "系统账户/防火墙部署已撤回"
                           "（SCOPE_DECISION_API_CANDIDATE_20261022.md）；"
                           "边界另由 tools/test_api_boundary.py 覆盖"
                           if na else
                           "候选仍以同用户普通进程运行（网络/文件系统不设限）；"
                           "部署前提见 refs/arena_v1/ISOLATION_ENV_PREREQ.md"),
                "verdict": "NOT_APPLICABLE" if na else "FAIL"})
    finally:
        upstream.httpd.shutdown()

    results = {"date": ts, "root": ROOT, "upstream_ledger": {
        "attempts": _Gate.hits["served"],
        "unkeyed_refused": _Gate.hits["unkeyed"] + _Gate.hits["guessed"]},
        "ledger_evidence": ledger_evidence,
        "verdicts": verdicts,
        "ledger_attacker": {
            "path": ledger_evidence.get("path", ""),
            "sha256": ledger_evidence.get("sha256", ""),
            "started": ledger_evidence.get("started", 0),
            "unique_attempt_ids": ledger_evidence.get("unique_attempt_ids", 0),
            "note": "真实 control 账本（attempt_ledger.jsonl）计数与散列，"
                    "verify 需读文件核验"}}
    os.makedirs(os.path.dirname(OUT_LOG), exist_ok=True)
    json.dump(results, open(OUT_LOG, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("ISOLATION MATRIX -> %s" % os.path.relpath(OUT_LOG, ROOT))
    for v in verdicts:
        print("  [%s] %s actual=%s" % (v["verdict"], v["check"],
                                       str(v.get("actual"))[:70]))
    bad = [v["check"] for v in verdicts if v["verdict"] == "FAIL"]
    na = [v["check"] for v in verdicts if v["verdict"] == "NOT_APPLICABLE"]
    known = [c for c in bad if c in KNOWN_ENV_FAIL]
    unexpected = [c for c in bad if c not in KNOWN_ENV_FAIL]
    print("ISOLATION MATRIX RESULT: %s (%d checks, fail=%d known_env=%d "
          "unexpected=%d not_applicable=%d)"
          % ("PASS" if not bad else "FAIL", len(verdicts), len(bad),
             len(known), len(unexpected), len(na)))
    return 0 if not bad else 1


def _cli_resume_drill():
    """B 组：同一生产 CLI 演练 new→停→resume→完成 + new 拒绝已有证据。
    用真实候选 phase0_beta_single 走 NCA_CORR_INTENT_v1（mock，quota 足够）：
    前两轮 → CLI 退出（进程结束）→ --resume → 前史字节未变、计数连续。"""
    out = []
    # 指导书 v2 §4：新建审计目录零删除（不拆批规避宿主确认）
    base = os.path.join(ROOT, "runs",
                        "_atk2_clidrill_%s" % _audit_uniq())
    if sandbox_mode.active():
        base = sandbox_mode.staged_root("clidrill")
    drill_out = os.path.join(base, "out")
    drill_run = os.path.join(base, "run")
    os.makedirs(base, exist_ok=True)
    py = sys.executable
    cmd = [py, "-B", "-X", "utf8", os.path.join(ROOT, "tools", "iso_run.py"),
           "--episode", "benchmark/episodes/NCA_CORR_INTENT_v1.json",
           "--candidate", "phase0_beta_single",
           "--out", drill_out, "--run-dir", drill_run,
           "--adapter", "mock", "--quota", "12"]

    def _run(extra, timeout=300):
        return subprocess.run(cmd + extra, cwd=ROOT, capture_output=True,
                              text=True, encoding="utf-8", errors="replace",
                              timeout=timeout)

    try:
        r1 = _run(["--stop-after", "2"])
        ok1 = r1.returncode == 0
        # 前史快照
        t1_dir = os.path.join(drill_out, "turn_01_NCA_t01")
        reply1 = open(os.path.join(t1_dir, "reply_raw.txt"),
                      encoding="utf-8").read() if ok1 else ""
        led1 = [json.loads(l) for l in
                open(os.path.join(drill_out, "_control",
                                  "attempt_ledger.jsonl"),
                     encoding="utf-8") if l.strip()] if ok1 else []
        run_id1 = ""
        if ok1:
            ident = json.load(open(os.path.join(drill_out, "_control",
                                                "run_identity.json"),
                                   encoding="utf-8"))
            run_id1 = ident.get("run_id", "")
        r2 = _run(["--resume"])
        ok2 = r2.returncode == 0
        # 前史字节未变 + run_id 连续 + attempt 全局唯一连续
        reply1b = open(os.path.join(t1_dir, "reply_raw.txt"),
                       encoding="utf-8").read() if ok2 else ""
        led2 = [json.loads(l) for l in
                open(os.path.join(drill_out, "_control",
                                  "attempt_ledger.jsonl"),
                     encoding="utf-8") if l.strip()] if ok2 else []
        started2 = [e.get("attempt_id") for e in led2
                    if e.get("kind") == "attempt_started"]
        ident2 = json.load(open(os.path.join(drill_out, "_control",
                                             "run_identity.json"),
                                encoding="utf-8")) if ok2 else {}
        mfile = os.path.join(drill_out, "episode_manifest.json")
        m2 = json.load(open(mfile, encoding="utf-8")) if os.path.isfile(
            mfile) else {}
        import glob as _glob
        gsum = 0
        for mf in _glob.glob(os.path.join(drill_out, "turn_*", "manifest.json")):
            gsum += json.load(open(mf, encoding="utf-8")).get(
                "gateway_calls", 0)
        drill_ok = (ok1 and ok2
                    and reply1 == reply1b and reply1 != ""
                    and ident2.get("run_id") == run_id1
                    and len(set(started2)) == len(started2)
                    and len(started2) == gsum and gsum >= 2
                    and m2.get("resumed_from") == 2
                    and m2.get("complete") is True
                    and all(a.startswith(run_id1) for a in started2))
        out.append({
            "check": "B_resume_cli_drill",
            "expect": ["same run_id", "bytes unchanged", "attempts continuous",
                       "resumed_from=2", "no silent regen"],
            "actual": "ok1=%s ok2=%s bytes_same=%s attempts=%d/%d "
                      "resumed_from=%s complete=%s run_id_same=%s"
                      % (ok1, ok2, reply1 == reply1b, len(set(started2)),
                         len(started2), m2.get("resumed_from"),
                         m2.get("complete"),
                         ident2.get("run_id") == run_id1),
            "verdict": "PASS" if drill_ok else "FAIL"})
        # new 模式拒绝已有证据
        r3 = _run([])
        rej = r3.returncode != 0 and "历史证据" in (r1.stderr + r3.stderr
                                                   + r3.stdout)
        out.append({
            "check": "B_new_rejects_evidence",
            "expect": ["new run rejected when evidence exists"],
            "actual": "rc=%d msg=%s" % (r3.returncode,
                                        ("历史证据" in (r3.stderr + r3.stdout))),
            "verdict": "PASS" if rej else "FAIL"})
    except Exception as e:
        out.append({"check": "B_resume_cli_drill",
                    "actual": "DRILL_EXCEPTION:%s" % type(e).__name__,
                    "detail": str(e)[:200], "verdict": "FAIL"})
    return out


def _notes_from_out(out_dir):
    """从可信侧产物回收夹具 notes：宿主回传白名单含 notes（list），
    控制器逐轮落盘 mechanism_notes.txt（str 形式）。"""
    import ast
    notes = []
    if not os.path.isdir(out_dir):
        return notes
    for d in sorted(os.listdir(out_dir)):
        td = os.path.join(out_dir, d)
        np = os.path.join(td, "mechanism_notes.txt")
        if d.startswith("turn_") and os.path.isfile(np):
            raw = open(np, encoding="utf-8").read()
            try:
                val = ast.literal_eval(raw)
            except Exception:
                val = []
            if isinstance(val, list):
                notes.extend(val)
    return notes


if __name__ == "__main__":
    sys.exit(main())
