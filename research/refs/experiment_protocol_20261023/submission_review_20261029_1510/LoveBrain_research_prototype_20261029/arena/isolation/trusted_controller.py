# -*- coding: utf-8 -*-
"""trusted_controller.py — 可信控制器（V14 收口版，2026-10-22）。

V14 收口（对照 ARENA_V14_INDEPENDENT_REVIEW §4/§5 与 CLOSEOUT 执行书 A/B 组）：
  - 控制器零候选执行：本文件绝不 import/exec 候选代码（旧版
    run_episode 调 lb_runner.load_mechanism 已移除）。候选机制只在不可信
    宿主进程内由 host_runtime 导入；版本/来源经宿主握手（hello）获得。
  - 模型代理授权面：媒体引用必须命中该轮可信登记表（realpath→散列+大小），
    data:/http(s):/file: URL 与未登记路径一律拒绝；model/temperature 以
    可信配置为准，候选覆盖被拒绝并记事件。
  - 额度持久：used 从 control 账本已落盘 attempt_started 重建，重建代理/
    resume 不重置；attempt_id 用 run_id（跨 resume 唯一）+ 单调序号。
  - 账本硬边界：started 写失败零派发（LedgerWriteError）；完成/失败账写
    失败 → pending_settlement，代理停批，manifest 如实标注。
  - new/resume 分离：new 拒绝已有证据目录；resume 不删 staged/out/control，
    校验候选/题面身份后从检查点恢复。
  - deny 纪律：失败即抛（不再继续运行）；/save 留存原 DACL；close 先停
    宿主、再 /reset+/restore 精确还原并验证无残留 DENY ACE。
"""
import copy
import datetime
import hashlib
import json
import os
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
import uuid

_HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(_HERE))
sys.path.insert(0, os.path.join(ROOT, "arena"))

import lb_assembly          # noqa: E402  (评分账口径)
import lb_runner            # noqa: E402  静态工具复用（消毒/媒体校验/评分入参）
import lb_scorer            # noqa: E402  评分权威
import api_candidate        # noqa: E402  考生边界（回复只当数据）
from lb_gateway import (ADAPTERS, OFFLINE_ADAPTERS, ModelGateway,
                        LedgerWriteError)  # noqa: E402
from isolation import sandbox_mode, win_runas  # noqa: E402

MAX_FRAME = 16_000_000
READ_TIMEOUT = 600.0

HOST_ENTRY = os.path.join(_HERE, "_candidate_host.py")
HOST_RUNTIME = os.path.join(_HERE, "host_runtime.py")
ARENA_STAGED = ("lb_assembly.py", "protocol_public.py")


def _sha_file(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def _sha_str(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


class MediaUnauthorized(RuntimeError):
    """候选请求了未授权媒体引用——代理拒绝派发。"""


class PendingSettlementError(RuntimeError):
    """完成/失败账未决（写失败）——代理停批，不再派发新请求。"""


class TrustedProxy:
    """可信模型代理：复用实际 lb_gateway 适配器与事件账。

    - adapter_name ∈ ADAPTERS（mock/echo 离线；openai_compat 仅在明确授权后
      由调用方传入 LB_API_BASE 等环境时启用）。
    - 事件账 event_log = control_dir/attempt_ledger.jsonl：attempt_started
      在适配器发送【之前】落盘，写失败零派发（LedgerWriteError）；失败不退款。
    - 媒体授权：generate 只接受命中该轮登记表（realpath→sha/size）的引用；
      data:/http(s):/file: 及未登记路径一律拒绝（V14 §4.3）。
    - 参数权威：model/temperature 以可信配置为准；候选覆盖被拒绝并记账
      （V14 §4.3——防止候选改变配对实验条件）。
    - 额度持久：used 从已落盘 attempt_started 重建（V14 §5.2）；完成/失败
      账未决（pending_settlement）时停批。
    - directive（测试替身台本）只在控制器侧合并，不跨 IPC。
    """

    def __init__(self, control_dir, batch_id, candidate, case_gid, quota,
                 adapter_name="mock", run_id="", fixed_model=None,
                 fixed_temperature=0.7, budget=None):
        self.control_dir = control_dir
        self.batch_id, self.candidate, self.case_gid = batch_id, candidate, case_gid
        self.quota = quota
        self.budget = budget      # AuthorizationBudget（授权级权威，可 None）
        self.run_id = run_id
        self.fixed_model = fixed_model      # None = 用适配器默认（可信配置）
        self.fixed_temperature = fixed_temperature
        self._media = {}                    # realpath -> {"sha256_16","size"}
        self._events = []           # 内存账（含拒绝事件）
        self._stopped = False       # pending/未决 → 停批
        self._backend = ModelGateway(ADAPTERS[adapter_name](),
                                     event_log_path=os.path.join(
                                         control_dir, "attempt_ledger.jsonl"))
        self._backend.run_id = run_id
        self._backend.case_id = case_gid
        # V14 §5.2：额度从持久账本重建（resume/重建代理不重置）
        ledger_path = os.path.join(control_dir, "attempt_ledger.jsonl")
        started_ids = []
        if os.path.isfile(ledger_path):
            for line in open(ledger_path, encoding="utf-8"):
                line = line.strip()
                if not line:
                    continue
                try:
                    ev = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if ev.get("kind") == "attempt_started" and ev.get("attempt_id"):
                    started_ids.append(ev["attempt_id"])
        self.used = len(started_ids)
        seq = 0
        for aid in started_ids:
            m = re.search(r"_g(\d+)$", str(aid))
            if m:
                seq = max(seq, int(m.group(1)))
        self._seq = seq
        self.log_rebuild = {"rebuilt_used": self.used, "seq": self._seq,
                            "source": os.path.basename(ledger_path)
                            if os.path.isfile(ledger_path) else "absent"}

    # ---------- 每轮上下文 ----------
    def begin_turn(self):
        """每轮开始调用：轮级 calls 只记本轮（V14 §5.4：1+1=2）。"""
        self._backend.calls = []

    def set_media_allowlist(self, entries):
        """该轮可信登记媒体：[{path, sha256_16, size}]（由控制器从冻结题面构建）。"""
        self._media = {os.path.realpath(e["path"]): e for e in (entries or [])}

    def set_directive(self, d):
        self._directive = d if isinstance(d, dict) else {}
        self._backend.directive = self._directive

    def _log(self, rec):
        rec = dict(rec, t=datetime.datetime.now().strftime("%H:%M:%S"))
        self._events.append(rec)
        return rec

    def _authorize_media(self, images):
        """返回授权后的本地路径列表；未授权引用抛 MediaUnauthorized。"""
        out = []
        for ref in images or []:
            if not isinstance(ref, str) or not ref:
                raise MediaUnauthorized("非法媒体引用类型: %r" % (ref,))
            if re.match(r"^(data:|https?:|file:)", ref):
                raise MediaUnauthorized(
                    "远程/内联媒体引用被拒（仅允许该轮登记的本地资产）: %s"
                    % ref[:60])
            rp = os.path.realpath(ref)
            ent = self._media.get(rp)
            if ent is None:
                raise MediaUnauthorized(
                    "媒体未登记于本轮可信清单: %s" % os.path.basename(rp))
            if not os.path.isfile(rp):
                raise MediaUnauthorized("登记媒体文件缺失: %s" % rp)
            if os.path.getsize(rp) != ent.get("size"):
                raise MediaUnauthorized("登记媒体大小不符: %s" % rp)
            if _sha_file(rp)[:16] != ent.get("sha256_16"):
                raise MediaUnauthorized("登记媒体散列不符: %s" % rp)
            out.append(rp)
        return out

    def generate(self, req):
        """req 来自宿主网关帧：{system,user,images,model,temperature,timeout_s,tag}。
        返回 (ok, body) 回传宿主。额度在【派发时】计；上游失败不退。"""
        if self._stopped or self._backend.pending_settlement:
            self._log({"kind": "dispatch_refused_pending",
                       "reason": "settlement pending / stopped"})
            self._stopped = True
            return False, {"error": "pending settlement — dispatch stopped"}
        if self.used >= self.quota:
            self._log({"kind": "quota_exceeded",
                       "attempt_id": "%s_g%04d" % (self.run_id, self._seq + 1),
                       "used": self.used, "quota": self.quota})
            return False, {"error": "quota exceeded", "used": self.used,
                           "quota": self.quota}
        # V14 §4.3：候选请求媒体必须全部命中本轮授权登记
        try:
            images = self._authorize_media(req.get("images") or [])
        except MediaUnauthorized as e:
            self._log({"kind": "media_unauthorized",
                       "error": str(e)[:200]})
            return False, {"error": "media unauthorized: %s" % str(e)[:180]}
        # V14 §4.3：model/temperature 以可信配置为准；候选覆盖拒绝并记账
        overrides = []
        model = self.fixed_model
        if req.get("model") not in (None, self.fixed_model):
            overrides.append("model=%s" % req.get("model"))
        temperature = self.fixed_temperature
        if req.get("temperature") is not None \
                and abs(float(req["temperature"]) - self.fixed_temperature) > 1e-9:
            overrides.append("temperature=%s" % req["temperature"])
        if overrides:
            self._log({"kind": "param_override_rejected",
                       "requested": overrides,
                       "enforced": {"model": self.fixed_model,
                                    "temperature": self.fixed_temperature}})
        self._seq += 1
        aid = "%s_g%04d" % (self.run_id, self._seq)
        # V14.1 §2 + 复核 v3 §2A：授权级预算权威是派发前置条件——
        # 非离线适配器无 budget 一律拒绝（入口强制，无预算代理不能派发）。
        # 离线替身（mock/echo）零真实上游、零消耗，故不进预算面；判定用
        # 允许清单 OFFLINE_ADAPTERS（新增适配器默认被视为需预算，fail-closed）。
        if self.budget is None:
            if self.adapter_name not in OFFLINE_ADAPTERS:
                self._log({"kind": "dispatch_refused_no_budget",
                           "attempt_id": aid,
                           "adapter": self.adapter_name,
                           "reason": "非离线适配器必须挂授权级预算权威"})
                return False, {"error": "budget authority required "
                                        "(online adapter without "
                                        "authorization budget)"}
        else:
            try:
                self.budget.reserve(aid)
            except Exception as e:
                self._log({"kind": "dispatch_refused_budget",
                           "attempt_id": aid, "reason": str(e)[:200]})
                return False, {"error": "budget refused: %s" % str(e)[:180]}
        try:
            res = self._backend.generate(
                req.get("system", ""), req.get("user", ""),
                images=images or None,
                model=model,
                temperature=temperature,
                timeout_s=req.get("timeout_s", 120) or 120,
                tag=req.get("tag", "main"),
                attempt_id=aid)
            self.used += 1
        except LedgerWriteError:
            # started 落盘失败 = 零派发（硬边界），代理永久停批；
            # 授权账已占额 → 记 failed 配对（消耗不退款）
            self._stopped = True
            if self.budget is not None:
                try:
                    self.budget.record("attempt_failed", aid,
                                       reason="proxy_ledger_write_failed")
                except Exception:
                    pass
            self._log({"kind": "dispatch_refused_ledger_write_failed"})
            return False, {"error": "ledger write failed — not dispatched"}
        except Exception as e:
            self.used += 1
            # attempt_failed 已由 ModelGateway 落盘；不退还 used（已发送尝试）
            if self.budget is not None:
                try:
                    self.budget.record("attempt_failed", aid,
                                       reason="%s: %s"
                                              % (type(e).__name__, e))
                except Exception:
                    pass
            self._log({"kind": "attempt_failed", "attempt_id": aid,
                       "error": "%s: %s" % (type(e).__name__, e),
                       "quota_refunded": False})
            return False, {"error": "upstream failed: %s" % str(e)[:200]}
        if self._backend.pending_settlement:
            # V14 §5.3：完成/失败账写失败 → 未决，停批（不当正常成功）
            self._stopped = True
            self._log({"kind": "settlement_pending_stop",
                       "audit_errors": self._backend.event_log_errors[-3:]})
        # R3#8：完整 usage/响应标识入账（G11 演练验收口径）
        self._log({"kind": "attempt_completed", "attempt_id": aid,
                   "tag": req.get("tag", "main"),
                   "prompt_sha16": hashlib.sha256(
                       (req.get("user") or "").encode("utf-8"))
                   .hexdigest()[:16],
                   "usage": res.get("usage"),
                   "finish": res.get("finish"),
                   "resp_model": res.get("resp_model"),
                   "resp_id": res.get("resp_id")})
        # 授权账完成配对（复核 v3 §2A：记录失败/完成配对）
        if self.budget is not None:
            try:
                self.budget.record("attempt_completed", aid)
            except Exception:
                pass
        return True, {"text": res.get("text", ""),
                      "finish": res.get("finish", ""),
                      "usage": res.get("usage"),
                      "resp_model": res.get("resp_model"),
                      "resp_id": res.get("resp_id")}

    def turn_usage(self):
        return self._backend.turn_usage()

    @property
    def adapter_name(self):
        return self._backend.adapter_name

    @property
    def audit_errors(self):
        return self._backend.event_log_errors

    @property
    def settlement_pending(self):
        return bool(self._backend.pending_settlement)

    def backend_calls(self):
        return self._backend.calls

    def ledger(self):
        return {"batch_id": self.batch_id, "candidate": self.candidate,
                "case_gid": self.case_gid, "quota": self.quota,
                "used": self.used, "adapter": self.adapter_name,
                "rebuild": self.log_rebuild,
                "events": list(self._events)}


def _latency_summary(values):
    """真分位数汇总（p50/p95/max/n）。

    线性插值（numpy 默认口径）：idx=(n-1)q，两侧线性插值。n=1 时 p50=p95=max。
    **不再把 max 标成 p95**——8 个样本的 p95 与 max 接近是样本量的事实，
    不是算法伪装，所以同时给出 n 与 max，读数时不会被单点误导。
    """
    xs = sorted(v for v in values if isinstance(v, (int, float)))
    n = len(xs)
    if not n:
        return {"p50": 0, "p95": 0, "max": 0, "n": 0}

    def pct(q):
        pos = (n - 1) * q
        lo = int(pos)
        hi = min(lo + 1, n - 1)
        frac = pos - lo
        return int(round(xs[lo] + (xs[hi] - xs[lo]) * frac))

    return {"p50": pct(0.5), "p95": pct(0.95), "max": xs[-1], "n": n}


class IsolatedRun:
    """一次隔离运行：staging + 宿主进程 + IPC + 可信侧评分/产物/账本。

    V14：new/resume 分离——new 模式发现已有证据目录立即拒绝；resume 模式
    不删除 staged/out/control，校验 run_identity 后复用原 run_id。"""

    def __init__(self, run_dir, out_dir, control_dir, candidate,
                 adapter="mock", quota=8, run_id=None, resume=False,
                 authorization=None):
        self.run_dir = os.path.abspath(run_dir)
        self.out_dir = os.path.abspath(out_dir)
        self.control_dir = os.path.abspath(control_dir)
        self.candidate = candidate
        self.adapter = adapter
        self.quota = quota
        self.resume = bool(resume)
        # V14.1 §2：授权级预算（键=authorization_id，账本在授权层，
        # 独立于 run 目录——新 run/重启/恢复/换版本都不重置）
        self.authorization = authorization or None
        self.budget = None
        identity_path = os.path.join(self.control_dir, "run_identity.json")
        if self.resume and os.path.isfile(identity_path):
            ident = json.load(open(identity_path, encoding="utf-8"))
            if ident.get("candidate") != candidate:
                raise RuntimeError(
                    "resume 身份不符: run 属于候选 %s，请求 %s"
                    % (ident.get("candidate"), candidate))
            self.run_id = ident.get("run_id") or (run_id or self._new_run_id())
        else:
            self.run_id = run_id or self._new_run_id()
        self.staged = {}
        self.events = []
        self.proc = None
        self._denied = []       # [{path, acl_file}]（close() 精确还原）
        self._q = queue.Queue()
        self._reader = None

    @staticmethod
    def _new_run_id():
        return "iso_%s_%s" % (datetime.datetime.now().strftime("%Y%m%d_%H%M%S"),
                              uuid.uuid4().hex[:6])

    # ---------- staging ----------
    def stage(self):
        self.staged_root = os.path.join(self.run_dir, "staged")
        if self.resume:
            # V14 §5.1：resume 不删除任何已有证据；staged 缺件才补
            if not os.path.isdir(self.staged_root):
                raise RuntimeError("resume 目标无 staged 树: %s" % self.staged_root)
        else:
            # new：out/control 已有证据（旧检查点/manifest/轮目录）→ 拒绝
            evidence = []
            if os.path.isdir(self.out_dir):
                for n in os.listdir(self.out_dir):
                    if n.startswith("turn_") or n in (
                            "episode_manifest.json", "checkpoint.json",
                            "checkpoint.bin"):
                        evidence.append(n)
            if evidence:
                raise RuntimeError(
                    "new 模式拒绝：输出目录已有历史证据 %s（恢复请用 --resume）"
                    % evidence[:3])
            for d in (self.staged_root, self.out_dir, self.control_dir):
                shutil.rmtree(d, ignore_errors=True)
                os.makedirs(d, exist_ok=True)
        os.makedirs(self.out_dir, exist_ok=True)
        os.makedirs(self.control_dir, exist_ok=True)
        if not self.resume:
            json.dump({"run_id": self.run_id, "candidate": self.candidate,
                       "adapter": self.adapter, "quota": self.quota,
                       "created": datetime.datetime.now().strftime(
                           "%Y-%m-%d %H:%M:%S")},
                      open(os.path.join(self.control_dir, "run_identity.json"),
                           "w", encoding="utf-8"),
                      ensure_ascii=False, indent=1)
        self._stage_candidate_closure(self.candidate)
        for rel in ARENA_STAGED:
            src = os.path.join(ROOT, "arena", rel)
            dst = os.path.join(self.staged_root, "arena", rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
            self.staged["arena/" + rel] = _sha_file(src)
        for fn, src in (("_candidate_host.py", HOST_ENTRY),
                        ("host_runtime.py", HOST_RUNTIME)):
            dst = os.path.join(self.staged_root, fn)
            shutil.copy2(src, dst)
            self.staged[fn] = _sha_file(src)
        self.log("STAGED", candidate=self.candidate, files=len(self.staged),
                 resume=self.resume)
        return self

    def _stage_candidate_closure(self, name, _depth=0):
        """拷贝候选整树；解析 mechanism 源码里引用的候选依赖并递归拷贝
        （如 phase3_facts 引用 candidates/phase1_retr_idf 与 phase2_expr）。"""
        # 深度上限 2026-10-28 由 4 放宽到 12（只放宽守卫，不改变拷贝集合）：
        # 转发型候选链本身就有 6 层
        #   action_policy_r2 → action_policy_r1 → v6_goal_context → v5_purpose_aligned
        #   → v4_intent_context → v3_system_slim → reference_product_v1
        # 菱形重复依赖虽被下面 dst 存在性短路（不做二次拷贝、不递归），
        # 但仍要进入本函数、因此照样计深；旧上限 4 让"比 r1 再深一层"的候选
        # 无法 staging（实测 RuntimeError: 候选依赖闭包过深）。
        # 无限递归的真实保护是 dst_root 存在性短路，本计数只作失控兜底。
        if _depth > 12:
            raise RuntimeError("候选依赖闭包过深")
        src_root = os.path.join(ROOT, "candidates", name)
        if not os.path.isdir(src_root):
            raise RuntimeError("候选目录不存在: %s" % src_root)
        dst_root = os.path.join(self.staged_root, "candidates", name)
        if os.path.isdir(dst_root):
            return set()
        shutil.copytree(src_root, dst_root)
        for cur, _d, fs in os.walk(src_root):
            for f in fs:
                p = os.path.join(cur, f)
                self.staged["candidates/%s/%s" % (
                    name, os.path.relpath(p, src_root).replace("\\", "/"))] = \
                    _sha_file(p)
        mech = os.path.join(src_root, "mechanism", "mechanism.py")
        deps = set()
        if os.path.isfile(mech):
            src = open(mech, encoding="utf-8").read()
            for m in re.finditer(r'"candidates",\s*"([A-Za-z0-9_]+)"', src):
                deps.add(m.group(1))
            for m in re.finditer(r"candidates[/\\\\]+([A-Za-z0-9_]+)", src):
                deps.add(m.group(1))
        deps.discard(name)
        for dep in sorted(deps):
            if os.path.isdir(os.path.join(ROOT, "candidates", dep)):
                self._stage_candidate_closure(dep, _depth + 1)
        return deps

    # ---------- 进程与 IPC ----------
    def start(self):
        """拉起候选宿主。

        LB_SANDBOX=1（G08 部署后）→ 以沙箱本地账户跨用户拉起，
        run 目录必须在 D:\\arena-sandbox 之下（主仓对该账户 DENY）；
        未开启 → 现有行为（当前用户），离线门禁不受影响。
        """
        argv = [None, "-I", "-B",
                os.path.join(self.staged_root, "_candidate_host.py"),
                "--staged-root", self.staged_root,
                "--candidate", self.candidate]
        sbx = sandbox_mode.config()
        if sbx is None:
            env = {"SYSTEMROOT": os.environ.get("SYSTEMROOT", ""),
                   "PATH": os.path.dirname(sys.executable),
                   "PYTHONHASHSEED": "0"}
            self.proc = subprocess.Popen(
                [sys.executable] + argv[1:],
                cwd=self.staged_root, env=env,
                stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                stderr=subprocess.PIPE, text=True, encoding="utf-8")
            self.sandboxed = False
        else:
            sandbox_mode.require_under_root(self.staged_root)
            tmp = os.path.join(self.staged_root, "sandbox")
            os.makedirs(tmp, exist_ok=True)
            argv[0] = sbx["python"]
            self.proc = win_runas.spawn_as_user(
                sbx["username"], sbx["password"], argv, cwd=self.staged_root,
                env=win_runas.build_env(tmp))
            self.sandboxed = True
            self.log("SANDBOX_HOST", user=sbx["username"],
                     python=sbx["python"])
        self._reader = threading.Thread(target=self._read_loop, daemon=True)
        self._reader.start()
        self.log("HOST_STARTED", pid=self.proc.pid, sandboxed=self.sandboxed)
        hello = self.recv(READ_TIMEOUT)
        if not hello or hello.get("hello") != "candidate_host":
            raise RuntimeError("宿主握手失败: %r" % hello)
        if hello.get("error"):
            raise RuntimeError("宿主初始化失败: %r" % hello)
        self.mech_origin = hello.get("mechanism", "?")
        self.lifecycle = hello.get("lifecycle", "?")
        self.log("HOST_HELLO", mechanism=self.mech_origin,
                 lifecycle=self.lifecycle)
        return self

    def _read_loop(self):
        for line in self.proc.stdout:
            self._q.put(line.rstrip("\n"))
        self._q.put(None)

    def log(self, kind, **kw):
        self.events.append({"kind": kind, "t": time.time(), **kw})

    def send(self, obj):
        self.proc.stdin.write(json.dumps(obj, ensure_ascii=True) + "\n")
        self.proc.stdin.flush()

    def recv(self, timeout=READ_TIMEOUT):
        try:
            line = self._q.get(timeout=timeout)
        except queue.Empty:
            return {"error": "TIMEOUT"}
        if line is None:
            err = ""
            if self.proc.poll() is not None:
                try:
                    err = self.proc.stderr.read()[-600:]
                except Exception:
                    pass
            return {"error": "HOST_EXITED", "code": self.proc.poll(),
                    "stderr_tail": err}
        if len(line) > MAX_FRAME:
            return {"error": "OVERSIZED_FRAME", "len": len(line)}
        try:
            return json.loads(line)
        except json.JSONDecodeError as e:
            return {"error": "BAD_JSON", "detail": str(e)[:200],
                    "line_head": line[:260]}

    def request(self, obj, timeout=READ_TIMEOUT):
        """发送命令并等待回包；期间到达的 gateway_generate 帧即时分派
        （同步代理：模型结果在候选 run_turn 返回之前交给候选）。"""
        self.send(obj)
        deadline = time.time() + timeout
        while True:
            remain = max(1.0, deadline - time.time())
            r = self.recv(remain)
            if isinstance(r, dict) and r.get("op") == "gateway_generate":
                ok, body = self._proxy.generate(r.get("req") or {})
                self.send({"op": "gateway_generate_result",
                           "req_id": r.get("req_id"),
                           "ok": ok, "body": body,
                           "error": None if ok else body.get("error")})
                continue
            return r

    # ---------- 生命周期（可信侧编排，评分权威在此） ----------
    # V15 §3.1：run_episode 拆成 begin_run / step_one_turn / finish_run 三个
    # 可复用原语——execute_plan 逐 slot 推进（一个 slot 只跑那一轮、派发前
    # 核对精确前驱），从而真正按冻结计划交错，而不牺牲两臂各自的连续状态。
    # run_episode 保持外部行为不变（编排壳：begin→逐轮 step→finish）。
    def begin_run(self, episode_path, resume=False, require_assets=True):
        """加载题面→deny→init/restore→proxy→授权级预算→已有轮 manifest 汇集，
        返回常驻"运行态" dict（供 step_one_turn/finish_run 使用）。

        镜像 arena/lb_runner.run_episode 的轮次编排前半；评分/产物/账本全在
        可信侧完成（候选只见消毒后的 ctx）。require_assets=False 仅限
        非生产夹具（攻击矩阵），生产路径一律 fail-fast。

        V14 §4.1：零候选代码执行——不 import/exec 机制；版本与来源取自宿主
        握手（start() 时的 hello 帧）。"""
        with open(episode_path, encoding="utf-8") as f:
            ep = json.load(f)
        cand_dir = os.path.join(ROOT, "candidates", self.candidate)
        # integrity 先算（deny 之后控制器自己也读不了题面文件）
        integrity_pre = lb_runner.integrity_manifest(episode_path, cand_dir)
        # gold 保护：题面（含 gold）加载进内存后立即对源文件 deny——
        # 候选宿主进程从此读不到原文件；还原在 close()（宿主停止之后）。
        # 执行指纹的题面散列必须在 deny 之前计算（deny 后不可读）。
        self._ep_source = os.path.abspath(episode_path)
        self._ep_sha16 = _sha_file(episode_path)[:16]
        self.deny_files([episode_path])
        if require_assets:
            lb_runner.require_candidate_assets(cand_dir)
        # V14 §4.1：控制器不加载候选。版本/来源=宿主握手结果；
        # load_contract 仅读静态 JSON 声明文件（非代码执行）。
        contract = lb_runner.load_contract(cand_dir)
        ep_meta = {k: v for k, v in ep.items() if k in lb_runner.EP_META_VISIBLE_KEYS}
        os.makedirs(self.out_dir, exist_ok=True)

        # init（或恢复）
        if resume and os.path.isfile(os.path.join(self.out_dir, "checkpoint.json")):
            start_idx = self._resume_from_checkpoint(ep)
        elif resume:
            raise RuntimeError("resume 指定但无检查点（不回退 init；"
                               "V14 §5.1 禁止静默从头生成）")
        else:
            start_idx = 0
            r = self.request({"op": "init", "ep_meta": ep_meta})
            if r.get("error"):
                raise RuntimeError("宿主 init 失败: %r" % r)
            self.log("INIT_DONE", version=r.get("version"),
                     mechanism=r.get("mechanism"))

        proxy = TrustedProxy(self.control_dir, self.run_id, self.candidate,
                             ep.get("episode_id", ""), self.quota,
                             adapter_name=self.adapter, run_id=self.run_id,
                             fixed_model=os.environ.get("LB_MODEL", "") or None,
                             fixed_temperature=0.7)
        self._proxy = proxy
        remaining_turns = len(ep["turns"]) - start_idx
        if self.authorization:
            from isolation.authorization_budget import (
                AuthorizationBudget, BudgetRefused, validate_authorization_doc)
            auth = self.authorization
            # 复核 v3 §2A 接线问题 1：出站配置由授权单绑定——非 mock 适配器
            # 必须以实际 env 出站配置对账（端点/model/temperature 逐字一致）
            env_api = os.environ.get("LB_API_BASE", "") or None
            env_model = os.environ.get("LB_MODEL", "") or None
            if self.adapter != "mock":
                norm = validate_authorization_doc(
                    {"authorization_id": auth["authorization_id"],
                     "authorized": True, "status": auth.get("status"),
                     "real_endpoint": {
                         "api_base": auth.get("api_base"),
                         "model": auth.get("model"),
                         "temperature": auth.get("temperature"),
                         "max_attempts": auth["max_attempts"]}},
                    expected_endpoint=env_api, expected_model=env_model)
                norm["budget_ledger"] = auth["budget_ledger"]
                norm["scope"] = auth.get("scope")
                auth = norm
            self.budget = AuthorizationBudget(
                auth["budget_ledger"], auth["authorization_id"],
                auth["max_attempts"], status=auth.get("status"),
                scope=auth.get("scope"))
            proxy.budget = self.budget
            # 整批预检：覆盖实际批需（默认=剩余轮次；有辅助调用时由调用方
            # 经 batch_need 声明实际次数——不能把题位数当请求次数）
            batch_need = self.authorization.get("batch_need") or remaining_turns
            try:
                self.budget.ensure_batch(batch_need,
                                         purpose="episode:%s"
                                         % ep.get("episode_id", ""))
            except BudgetRefused as e:
                self.log("BATCH_REJECTED", reason=str(e)[:200])
                raise
            self.log("BUDGET_OK", **self.budget.summary())
        if proxy.used > 0:
            self.log("QUOTA_REBUILT", **proxy.log_rebuild)
        turns_manifest = []
        for i in range(start_idx):
            td = _turn_dir(self.out_dir, i, ep["turns"][i])
            with open(os.path.join(td, "manifest.json"), encoding="utf-8") as f:
                turns_manifest.append(json.load(f))

        return {
            "ep": ep, "episode_path": episode_path, "cand_dir": cand_dir,
            "integrity_pre": integrity_pre, "contract": contract,
            "proxy": proxy, "start_idx": start_idx, "next_index": start_idx,
            "turns_manifest": turns_manifest,
            "settlement_pending_turns": [], "finished": False,
        }

    def step_one_turn(self, state, expect_turn=None):
        """执行且仅执行 state['next_index'] 对应的那一轮，落 manifest/评分/
        检查点并把 next_index 前进一步。

        expect_turn（1-based）非空 → 派发前核对精确前驱：与下一个应跑轮次
        不符（缺失/落后/超前）一律在【零派发、零记账】前提下拒绝，防恢复时
        暗中补跑历史轮（V15 §3.1）。"""
        if state["finished"]:
            raise RuntimeError("运行态已收口（finish_run 已调用），不能再派发轮次")
        ep = state["ep"]
        proxy = state["proxy"]
        contract = state["contract"]
        i = state["next_index"]
        if expect_turn is not None and expect_turn != i + 1:
            raise RuntimeError(
                "前驱不符：该运行态下一个轮=%d，派发请求轮=%d（不补跑/不跳轮，"
                "派发前拒绝）" % (i + 1, expect_turn))
        if i >= len(ep["turns"]):
            raise RuntimeError("轮次越界：next_index=%d 已到 %d 轮末尾"
                               % (i, len(ep["turns"])))
        turn = ep["turns"][i]
        turn_eval = copy.deepcopy(turn)     # 候选从未接触的原题快照
        lb_runner.validate_media_mapping(turn.get("input") or {},
                                         turn.get("case_id", ""))
        ctx = lb_runner.sanitize_turn(turn)
        ctx["assets_root"] = os.path.join(
            self.staged_root, "candidates", self.candidate, "assets")
        ctx["sandbox"] = os.path.join(self.staged_root, "sandbox")
        ctx["turn_type"] = turn.get("turn_type", "reply")
        ctx["turn_index"] = i + 1
        proxy.set_directive(lb_runner._turn_directive(turn_eval))
        proxy._backend.set_run_context(self.run_id,
                                       turn.get("case_id", ""))
        # V14 §4.3：该轮可信媒体登记表（路径→散列+大小）——
        # 候选仅能引用题面登记的媒体资产
        proxy.set_media_allowlist(self._turn_media_entries(turn_eval))
        proxy.begin_turn()          # 轮级 calls 只记本轮（1+1=2）
        td = _turn_dir(self.out_dir, i, turn)
        t0 = time.time()
        err = None
        try:
            r = self.request({"op": "turn", "ctx": ctx})
        except Exception as e:
            r = {"error": "IPC_FAIL", "detail": str(e)[:300]}
        latency_ms = int((time.time() - t0) * 1000)
        self._dump_calls(td, proxy)
        if r.get("error"):
            err = "%s: %s | frame=%s" % (r["error"],
                                         str(r.get("detail", ""))[:200],
                                         str(r)[:400])
            _w(td, "error.json", json.dumps(
                {"case_id": turn.get("case_id"), "turn_index": i + 1,
                 "error": err,
                 "adapter": proxy.adapter_name,
                 "ts": datetime.datetime.now().strftime(
                     "%Y-%m-%d %H:%M:%S")},
                ensure_ascii=False, indent=2))
            self.log("TURN_ERROR", turn=i + 1, error=err)
            raise RuntimeError("第 %d 轮执行失败: %s" % (i + 1, err))
        reply_raw = r.get("reply_raw", "")
        # 模型返回物只当数据——逐字留档 + 分类（json.loads，无求值）
        ingest = api_candidate.store_reply(td, reply_raw)
        # ---------- 评分权威（可信侧，原题快照 + 独立 id_map） ----------
        id_map = lb_runner.canonical_id_map(
            (turn_eval.get("input") or {}).get("messages"))
        scoring = lb_scorer.run_case_scoring_safe(turn_eval, reply_raw,
                                                  id_map, contract)
        usage = proxy.turn_usage()
        manifest = {
            "case_id": turn.get("case_id"),
            "turn_type": turn.get("turn_type", "reply"),
            "candidate": self.candidate,
            "adapter": proxy.adapter_name,
            "model": os.environ.get("LB_MODEL", "") or "mock",
            "lifecycle": self.lifecycle, "mechanism": self.mech_origin,
            "contract": contract or "legacy",
            "run_id": self.run_id, "runner_pid": os.getpid(),
            "audit_errors": proxy.audit_errors,
            "settlement_pending": proxy.settlement_pending,
            "clock_injected": (turn.get("input") or {}).get("clock"),
            # A5 运行快照：生成当时消费的 case input 内容散列，评审据此绑定，
            # 不再回读工作树题面（与 exam_layered_score._case_turn_input_sha 同口径）
            "case_input_sha256": _sha_str(json.dumps(
                (turn_eval.get("input") or {}), ensure_ascii=False,
                sort_keys=True)),
            "media_refs": lb_runner._collect_images(
                turn.get("input") or {}),
            "tokens": usage,
            "gateway_calls": usage.get("n_calls", 0),
            "latency_ms": latency_ms,
            "score10": scoring["score10"], "veto": scoring["veto"],
            "reply_ingest": ingest["kind"],
            "mechanism_error": r.get("mech_error"),
            "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "prompt_chars": {
                "prompt_sum": usage.get("prompt"),
                "reply_sum": usage.get("completion")},
            "isolated": True,
        }
        _w(td, "scoring.json", json.dumps(scoring, ensure_ascii=False,
                                          indent=2))
        _w(td, "manifest.json", json.dumps(manifest, ensure_ascii=False,
                                           indent=2))
        if r.get("notes"):
            _w(td, "mechanism_notes.txt", str(r["notes"]))
        state["turns_manifest"].append(manifest)
        if proxy.settlement_pending:
            state["settlement_pending_turns"].append(i + 1)
        self.log("TURN_DONE", turn=i + 1, score10=manifest["score10"],
                 veto=manifest["veto"])
        # checkpoint（可信侧保存；宿主仅按需回传快照字节）
        snap = self.request({"op": "snapshot"})
        if snap.get("ok"):
            snap_b = _b64decode(snap["snapshot_b64"])
            _wb(self.out_dir, "checkpoint.bin", snap_b)
            _w(self.out_dir, "checkpoint.json", json.dumps(
                {"turns_done": i + 1,
                 "episode_id": ep.get("episode_id"),
                 "candidate": self.candidate, "lifecycle": self.lifecycle,
                 "ts": datetime.datetime.now().strftime(
                     "%Y-%m-%d %H:%M:%S")},
                ensure_ascii=False, indent=2))
        state["next_index"] = i + 1
        return manifest

    def finish_run(self, state):
        """写 episode_manifest + flush_proxy + settlement 检查；返回
        (ep_manifest, proxy)。settlement 未决 → 抛（证据已留盘，不计成功）。"""
        ep = state["ep"]
        proxy = state["proxy"]
        contract = state["contract"]
        turns_manifest = state["turns_manifest"]
        start_idx = state["start_idx"]
        settlement_pending_turns = state["settlement_pending_turns"]
        integrity_pre = state["integrity_pre"]
        ep_manifest = {
            "episode_id": ep.get("episode_id"),
            "candidate": self.candidate,
            "adapter": proxy.adapter_name, "mechanism": self.mech_origin,
            "lifecycle": self.lifecycle, "contract": contract or "legacy",
            "run_id": self.run_id, "runner_pid": os.getpid(),
            "turns": len(turns_manifest),
            "turns_total": len(ep["turns"]),
            "complete": len(turns_manifest) == len(ep["turns"]),
            "resumed_from": start_idx if start_idx else None,
            "mean_score10": round(sum(m["score10"] for m in turns_manifest)
                                  / len(turns_manifest), 2)
            if turns_manifest else 0.0,
            "veto_turns": sum(1 for m in turns_manifest if m["veto"]),
            "first_pass_rate": round(
                sum(1 for m in turns_manifest if not m["veto"])
                / len(turns_manifest), 4) if turns_manifest else 0.0,
            "tokens_total": {
                "prompt": sum((m["tokens"].get("prompt") or 0)
                              for m in turns_manifest),
                "completion": sum((m["tokens"].get("completion") or 0)
                                  for m in turns_manifest)},
            # 复核 v4 §7：p95 之前实际取的是 max（误标）。这里给真分位数
            # （线性插值，n<2 时退化到该值本身），并同时保留 max 与样本数。
            "latency_ms": _latency_summary(
                [m["latency_ms"] for m in turns_manifest]),
            "integrity": integrity_pre,
            "isolated": True,
            "settlement_pending_turns": settlement_pending_turns,
            # V14.1 §3：执行指纹（影响执行的输入）与报告元数据分层
            "execution_fingerprint": {
                "episode_id": ep.get("episode_id"),
                "episode_sha256_16": getattr(self, "_ep_sha16", None),
                "candidate": self.candidate,
                "adapter": proxy.adapter_name,
                "model": os.environ.get("LB_MODEL", "") or "mock",
                "temperature": 0.7,
            },
            "report_metadata": {
                "authorization_id": (self.authorization or {}).get(
                    "authorization_id"),
                "budget": self.budget.summary() if self.budget else None,
                "evidence_paths": "报告/路径元数据不参与执行指纹",
            },
            "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }
        _w(self.out_dir, "episode_manifest.json",
           json.dumps(ep_manifest, ensure_ascii=False, indent=2))
        self.flush_proxy(proxy)
        state["finished"] = True
        if settlement_pending_turns:
            # V14 §5.3：完成/失败账写失败 → 不作为成功收口（证据已留盘）
            raise RuntimeError(
                "账本未决（attempt 完成/失败落账写失败）turns=%s — "
                "本运行不得计入成功" % settlement_pending_turns)
        # 复核 v3 §2A：episode 结束【不】关闭整个授权——授权覆盖已批准的
        # 运行清单（多故事/跨 run 累计），由可信批次收口 close_authorization()
        # 显式关闭；失败按批准的失败策略终止，不复活、不补跑。
        return ep_manifest, proxy

    def run_episode(self, episode_path, stop_after=None, resume=False,
                    require_assets=True):
        """保持外部行为不变的编排壳：begin_run → 逐轮 step_one_turn
        （尊重 stop_after）→ finish_run。execute_plan 交错路径直接复用三个
        原语逐 slot 推进，不再经此整条循环。"""
        state = self.begin_run(episode_path, resume=resume,
                               require_assets=require_assets)
        while state["next_index"] < len(state["ep"]["turns"]):
            if stop_after is not None and (state["next_index"] + 1) > stop_after:
                break
            self.step_one_turn(state)
        return self.finish_run(state)

    def close_authorization(self, reason):
        """可信批次收口：显式关闭整个授权（仅批准的运行清单全部结束后调用）。
        幂等；授权不存在/未挂载时为 no-op。"""
        if self.budget is not None:
            self.budget.close(reason)
            self.log("AUTHORIZATION_CLOSED",
                     **{k: v for k, v in self.budget.summary().items()})
            return self.budget.summary()
        return None

    @staticmethod
    def _turn_media_entries(turn_eval):
        """从冻结题面收集该轮授权媒体：绝对路径→{sha256_16, size}。"""
        entries = {}
        inp = turn_eval.get("input") or {}
        media = inp.get("media") or {}
        refs = list(media.get("images") or [])
        for m in inp.get("messages", []):
            if isinstance(m, dict) and m.get("image"):
                refs.append(m["image"])
        for ref in refs:
            if not isinstance(ref, str) or not ref:
                continue
            p = os.path.realpath(ref)
            if os.path.isfile(p) and p not in entries:
                entries[p] = {"path": p,
                              "sha256_16": _sha_file(p)[:16],
                              "size": os.path.getsize(p)}
        return list(entries.values())

    def _resume_from_checkpoint(self, ep):
        """V14 §5.1：恢复前校验检查点身份（候选/题面/版本绑定），
        不匹配即拒绝——不静默回退 init。"""
        with open(os.path.join(self.out_dir, "checkpoint.json"),
                  encoding="utf-8") as f:
            ck = json.load(f)
        if ck.get("candidate") != self.candidate:
            raise RuntimeError("检查点候选不符: %s != %s"
                               % (ck.get("candidate"), self.candidate))
        if ck.get("episode_id") and ep.get("episode_id") \
                and ck["episode_id"] != ep["episode_id"]:
            raise RuntimeError("检查点题面不符: %s != %s"
                               % (ck["episode_id"], ep["episode_id"]))
        start_idx = int(ck["turns_done"])
        snap = open(os.path.join(self.out_dir, "checkpoint.bin"), "rb").read()
        r = self.request({"op": "restore",
                          "snapshot_b64": _b64encode(snap)})
        if r.get("error"):
            raise RuntimeError("宿主 restore 失败: %r" % r)
        self.log("RESTORED", resumed_from=start_idx)
        return start_idx

    def _dump_calls(self, td, proxy):
        _w(td, "calls.json", json.dumps(proxy.backend_calls(),
                                        ensure_ascii=False, indent=2))

    # ---------- 账本 ----------
    def flush_proxy(self, proxy):
        ledger = {"run_dir": os.path.basename(self.run_dir),
                  "candidate": self.candidate,
                  "controller_events": self.events,
                  "proxy": proxy.ledger(),
                  "staged_hashes": self.staged}
        blob = json.dumps(ledger, ensure_ascii=False, sort_keys=True)
        p = os.path.join(self.control_dir, "ledger.json")
        open(p, "w", encoding="utf-8").write(blob)
        side = hashlib.sha256(blob.encode("utf-8")).hexdigest()
        open(p + ".sha256", "w").write(side + "\n")
        return {"ledger": p, "sidecar_sha256": side}

    # ---------- 受保护文件 deny（生产入口自动；失败即停） ----------
    def deny_files(self, paths):
        """对受保护文件拒绝读取内容（Windows ACL，文件级；仅生产入口自动
        登记的当前题面——测试不得自行加靶冒充生产保护，V14 执行书 A4）。
        纪律一：只对文件用——目录级 deny 曾造成锁死事故，禁用。
        纪律二：deny 掩码必须是 (RD)（仅拒文件数据读取），绝不能用 (R)——
        (R) 含 READ_CONTROL，会把 icacls 自己也锁死（无法 /reset，
        2026-10-21 事故实证）。
        纪律三（V14）：deny 前用 /save 留存原 DACL；失败即抛（宿主不再
        继续运行，绝不 FILE_DENY_FAIL 后照常跑）。"""
        for p in paths:
            p = os.path.abspath(p)
            if not os.path.isfile(p):
                continue
            os.makedirs(self.control_dir, exist_ok=True)
            acl_bak = os.path.join(
                self.control_dir, "dacl_%s.bak"
                % hashlib.sha256(p.encode("utf-8")).hexdigest()[:12])
            # 原始 DACL 留底：/save 挂在文件上（首行=文件名，还原时对父目录
            # /restore 即精确还原该文件 DACL——语义已实测验证）；留底文件
            # 放 control_dir（不污染题库/仓库目录）
            r0 = subprocess.run(["icacls", p, "/save", acl_bak],
                                capture_output=True)
            if r0.returncode != 0:
                raise RuntimeError(
                    "deny 前提失败（无法留存原 DACL）: %s — %s"
                    % (p, (r0.stderr or r0.stdout or b"").decode(
                        "utf-8", "replace")[:120]))
            # icacls 输出为系统 ANSI（GBK），不用 text=True 读
            r = subprocess.run(["icacls", p, "/deny", "*S-1-1-0:(RD)"],
                               capture_output=True)
            if r.returncode == 0:
                self._denied.append({"path": p, "acl_file": acl_bak})
                try:
                    disp = os.path.relpath(p, ROOT)
                except ValueError:      # 跨盘（如 C: 临时题面副本 vs D: 仓库）
                    disp = p
                self.log("FILE_DENY", path=disp)
            else:
                try:
                    os.remove(acl_bak)
                except OSError:
                    pass
                raise RuntimeError(
                    "deny 失败，运行中止: %s — %s"
                    % (p, (r.stderr or r.stdout or b"").decode(
                        "utf-8", "replace")[:120]))
        return self

    def _deny_ace_absent(self, p):
        """验证文件 DACL 已无 DENY ACE（icacls 输出探测）。"""
        r = subprocess.run(["icacls", p], capture_output=True)
        if r.returncode != 0:
            return False
        return "(DENY" not in r.stdout.decode("utf-8", "replace")

    def undeny_files(self):
        """精确还原：/reset 恢复继承链；若留底 DACL 含显式（非继承）ACE
        则另试 /restore；随后验证无残留 DENY ACE。任何未验证通过 → 抛错
        （V14 执行书 A4：失败即停止，不得静默继续；备份验证通过才清理）。"""
        errors = []
        for ent in list(self._denied):
            p, acl = ent["path"], ent["acl_file"]
            try:
                r1 = subprocess.run(["icacls", p, "/reset"], capture_output=True)
                ok = r1.returncode == 0
                if not ok:
                    errors.append("%s reset rc=%d" % (
                        os.path.basename(p), r1.returncode))
                else:
                    # 原始 DACL 是否全为继承 ACE（SDDL：无 P 保护标志且每个
                    # ACE 都带 ;ID;）。是 → /reset 即精确还原原继承链。
                    all_inherited = False
                    if os.path.isfile(acl):
                        raw = open(acl, "rb").read()
                        saved = None
                        for enc in ("utf-16", "mbcs"):
                            try:
                                saved = raw.decode(enc)
                                if "D:" in saved:
                                    break
                            except (UnicodeDecodeError, LookupError):
                                continue
                        saved = saved or raw.decode("utf-8", "replace")
                        m = re.search(r"D:([A-Za-z]*)", saved)
                        flags = m.group(1) if m else ""
                        aces = re.findall(r"\([^()]*\)", saved)
                        all_inherited = ("P" not in flags and bool(aces)
                                         and all(";ID;" in a for a in aces))
                    if not all_inherited and os.path.isfile(acl):
                        r2 = subprocess.run(
                            ["icacls", os.path.dirname(p), "/restore", acl],
                            capture_output=True)
                        if r2.returncode != 0:
                            errors.append(
                                "%s 含显式 ACE 且 /restore 失败 rc=%d（需要 "
                                "SeRestorePrivilege）— 不得宣称已精确还原"
                                % (os.path.basename(p), r2.returncode))
                            continue
                    if not self._deny_ace_absent(p):
                        errors.append("%s 残留 DENY ACE" % os.path.basename(p))
                        continue
                    if not os.access(p, os.R_OK):
                        errors.append("%s 还原后仍不可读" % os.path.basename(p))
                        continue
                    try:
                        disp = os.path.relpath(p, ROOT)
                    except ValueError:      # 跨盘（C: 临时题面副本 vs D: 仓库）
                        disp = p
                    self.log("FILE_UNDENY_VERIFIED",
                             path=disp,
                             all_inherited_dacl=all_inherited)
                    if os.path.isfile(acl):
                        try:
                            os.remove(acl)
                        except OSError:
                            pass
            except Exception as e:
                errors.append("%s: %s" % (p, str(e)[:120]))
        self._denied.clear()
        if errors:
            raise RuntimeError("undeny/还原未通过: %s" % "; ".join(errors))

    def close(self):
        # V14 §4.4：先停宿主、后解除保护（次序窗口消除）
        if self.proc and self.proc.poll() is None:
            try:
                self.proc.stdin.close()
                self.proc.wait(timeout=8)
            except Exception:
                self.proc.kill()
                try:
                    self.proc.wait(timeout=8)
                except Exception:
                    pass
        try:
            self.undeny_files()
        except RuntimeError as e:
            self.log("FILE_UNDENY_FAILED_TERMINAL", error=str(e)[:200])
            raise
        self.log("HOST_CLOSED", code=self.proc.poll() if self.proc else None)


def _b64encode(b):
    import base64
    return base64.b64encode(b).decode()


def _b64decode(s):
    import base64
    return base64.b64decode(s)


def _turn_dir(out_dir, i, turn):
    return os.path.join(out_dir, "turn_%02d_%s"
                        % (i + 1, turn.get("case_id", "case")))


def _w(out_dir, name, content):
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, name), "w", encoding="utf-8",
              newline="") as f:
        f.write(content)


def _wb(out_dir, name, content):
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, name), "wb") as f:
        f.write(content)
