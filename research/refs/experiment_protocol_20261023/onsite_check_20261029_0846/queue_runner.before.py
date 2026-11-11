#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""queue_runner.py — 把持久队列接进真实派工循环（崩溃可续 / 不重复提交）。

C 线集成包。队列底座复用 max-v2/c/persistent_queue.py（只读引用，不修改），
派工/状态/事件/ack 走 worker_adapter.mjs（CLI），编排与独立检查逻辑在本文件。

核心不变量：
  1. 同一 orderId 至多产生一个 adapter run（派发前查证据 + adapter list 双保险）。
  2. 崩溃恢复时先在队列里查 runId：有 runId 先问 adapter status，绝不盲重派。
  3. 只有「独立检查产物通过」才 ack + accepted（不盲 ack）。

v4 审计视图：
  订单 payload 可带 "auditEnvelopePath" 字段指向 v4 统一信封 JSON；
  run_order() 完成后在证据里写入 auditView + auditViewStatus 字段。
  auditViewStatus ∈ "ok" | "absent" | "malformed"（缺文件 / 坏 JSON 均如实标注，不猜 0）。

用法见同目录 README.md。
"""
import argparse
import hashlib
import importlib.util
import json
import os
import shlex
import subprocess
import sys
import time

# v4 审计视图（本目录 audit_view.py，加载失败时降级为 None）
_audit_view_mod = None
try:
    import importlib.util as _ilu
    _av_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "audit_view.py")
    if os.path.isfile(_av_path):
        _av_spec = _ilu.spec_from_file_location("audit_view", _av_path)
        _audit_view_mod = _ilu.module_from_spec(_av_spec)
        _av_spec.loader.exec_module(_audit_view_mod)
except Exception:
    _audit_view_mod = None

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_QUEUE_MODULE = os.path.normpath(os.path.join(HERE, "..", "c", "persistent_queue.py"))
DEFAULT_ADAPTER_CMD = os.environ.get(
    "BUTLER_WORKER_ADAPTER",
    "D:/butler_acceptance/runs/fullflow-20261020-02/workspaces/butler-fix/fix-n3a/scripts/worker_adapter.mjs",
)
DEFAULT_NODE_BIN = os.environ.get("BUTLER_NODE_BIN", "node")

# status 归一化取值
ST_FINISHED = "finished"
ST_FAILED = "failed"
ST_UNKNOWN = "unknown"
ST_TIMEOUT = "timeout"
NON_TERMINAL = ("running", "pending", "queued", "starting", "dispatched", "working")
TERMINAL_BAD = ("failed", "cancelled", "error", "aborted")

# ---- Step 5 (288h 2026-10-28): honest execution classification ----------------
# campaign_orders typed orders carry an explicit payload.actionType + needsModel.
# The live tick must NOT (a) fabricate "accepted" for an offline order it cannot
# actually perform, nor (b) mislabel a model order that lacks a concrete promptFile
# as "failed" (it is merely not-yet-enriched). Legacy orders (no actionType) keep the
# original adapter-dispatch path verbatim for backward compatibility.
MODEL_REQUIRED_ACTION_TYPES = ("model_inference", "live_acceptance")
# An offline action the headless tick can honestly complete ONLY if it carries a
# verifiable local judgement (a check to run) or a local command to execute.
OFFLINE_EXECUTABLE_KEYS = ("check", "localCommand")


def classify_execution(order):
    """Return (kind, reason) for a ready order.

    kind ∈ {"dispatch", "offline_verify", "backlog", "unenriched_model"}:
      dispatch        — legacy order (no declared actionType) OR an enriched model
                        order (model actionType + needsModel + existing promptFile):
                        send through the adapter (real routing, real dispatch).
      offline_verify  — non-model actionType carrying a real local `check`/
                        `localCommand`: complete locally, accept ONLY if the check
                        genuinely passes (never a blind accept).
      backlog         — non-model actionType with no encoded executable: this is
                        visible ready work the tick cannot autonomously perform.
                        Leave it ready; do not fake completion, do not fail it.
      unenriched_model— model_inference/live_acceptance missing promptFile: a real
                        model order that has not yet been given a concrete prompt.
                        Leave it ready; report it as awaiting enrichment.
    """
    payload = order.get("payload") or {}
    if not isinstance(payload, dict):
        payload = {}
    action_type = payload.get("actionType")
    if not action_type:
        return "dispatch", "legacy order (no actionType) — adapter path"
    if action_type in MODEL_REQUIRED_ACTION_TYPES:
        pf = payload.get("promptFile")
        if pf and os.path.exists(pf):
            return "dispatch", "enriched model order — promptFile present"
        return "unenriched_model", "model actionType '{}' has no existing promptFile".format(action_type)
    # non-model actionType
    if any(k in payload and payload[k] for k in OFFLINE_EXECUTABLE_KEYS):
        return "offline_verify", "offline action '{}' carries a verifiable local executable".format(action_type)
    return "backlog", "offline action '{}' has no encoded executable (visible backlog, not faked)".format(action_type)


# ----------------------------------------------------------------------
# 队列模块加载（外部文件，只读）
# ----------------------------------------------------------------------
def load_queue_module(path):
    path = os.path.abspath(path)
    if not os.path.exists(path):
        raise SystemExit("queue module not found: {}".format(path))
    spec = importlib.util.spec_from_file_location("persistent_queue_impl", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def make_queue_class(mod):
    """在 PersistentQueue 上加两个本 runner 必需的操作：attach_run / requeue。"""
    base = mod.PersistentQueue

    class DispatchQueue(base):
        def get_order(self, order_id):
            with self._lock:
                o = self._find_by_order(order_id)
                return dict(o) if o is not None else None

        def all_orders(self):
            with self._lock:
                return [dict(o) for o in self.orders]

        def running_orders(self):
            with self._lock:
                return [dict(o) for o in self.orders if o["state"] == mod.STATE_RUNNING]

        def attach_run(self, order_id, run_id, turn_id=None):
            """把 adapter 派发得到的 runId/turnId 落进队列记录（崩溃续跑依据）。"""
            with self._lock:
                o = self._find_by_order(order_id)
                if o is None:
                    raise ValueError("orderId '{}' not found".format(order_id))
                o["runId"] = run_id
                o["turnId"] = turn_id
                o["updatedAt"] = mod._now_iso()
                self._save()
                return dict(o)

        def requeue(self, order_id, note=None):
            """回到 ready（不是新提交）：崩溃发生在派发前 / 失败重试时使用。"""
            with self._lock:
                o = self._find_by_order(order_id)
                if o is None:
                    raise ValueError("orderId '{}' not found".format(order_id))
                o["state"] = mod.STATE_READY
                if note is not None:
                    o["lastError"] = note
                o["updatedAt"] = mod._now_iso()
                self._save()
                return dict(o)

        def park(self, order_id, note=None):
            """ parked 到 pending：依赖未就绪的订单不参与本轮消费。"""
            with self._lock:
                o = self._find_by_order(order_id)
                if o is None:
                    raise ValueError("orderId '{}' not found".format(order_id))
                o["state"] = mod.STATE_PENDING
                if note is not None:
                    o["lastError"] = note
                o["updatedAt"] = mod._now_iso()
                self._save()
                return dict(o)

    return DispatchQueue


# ----------------------------------------------------------------------
# 证据文件（原子写）
# ----------------------------------------------------------------------
class Evidence(object):
    def __init__(self, path):
        self.path = os.path.abspath(path)
        self.data = {"orders": {}}
        if os.path.exists(self.path):
            try:
                with open(self.path, "r", encoding="utf-8") as f:
                    loaded = json.load(f)
                if isinstance(loaded, dict):
                    self.data = loaded
                    self.data.setdefault("orders", {})
            except (ValueError, OSError):
                pass

    def save(self):
        tmp = self.path + ".tmp.{}.{}".format(os.getpid(), int(time.time() * 1000))
        d = os.path.dirname(self.path)
        if d and not os.path.isdir(d):
            os.makedirs(d, exist_ok=True)
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self.data, f, ensure_ascii=False, indent=2)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, self.path)

    def entry(self, order_id):
        return self.data["orders"].setdefault(order_id, {})

    def dispatched_run(self, order_id):
        """该 orderId 是否已经派发过（幂等锁）。"""
        return self.data["orders"].get(order_id, {}).get("runId")

    def record(self, order_id, **fields):
        e = self.entry(order_id)
        e.update(fields)
        self.save()
        return e


# ----------------------------------------------------------------------
# 独立检查
# ----------------------------------------------------------------------
def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def check_artifact(check, project):
    """payload.check 指定的产物：存在 + 至少一条内容判据 + 可选变更绑定的。

    check = {
      "file": <abs|相对 project>,
      "contains": [str] | str,
      "equals": str,
      "sha256": str,
      "baselineSha256": str,      # 本轮之前的 hash；给了就必须"变了"
      "expectNoChange": bool,     # 合法幂等结果：显式声明才允许 hash 不变
    }

    F-WAVE-03：一个早就存在的文件本身不证明本轮工作发生过。
    因此「只给 file、没有任何判定项」直接失败；mtime 一律不作证据。
    """
    if not isinstance(check, dict) or not check.get("file"):
        return False, "no-check-target", None
    path = check["file"]
    if not os.path.isabs(path):
        path = os.path.join(project, path)
    if not os.path.exists(path):
        return False, "file missing: {}".format(path), None

    predicates = []
    if check.get("equals") is not None:
        predicates.append("equals")
    if check.get("contains"):
        predicates.append("contains")
    if check.get("sha256"):
        predicates.append("sha256")
    if check.get("baselineSha256"):
        predicates.append("baselineSha256")
    if not predicates:
        # 旧行为（只给 file 且文件存在就 True）就是复核指出的假通过来源。
        return False, "no-predicate: file exists but nothing was verified", None

    sha = sha256_file(path)
    text = None

    def read_text():
        nonlocal text
        if text is None:
            with open(path, encoding="utf-8", errors="replace") as f:
                text = f.read()
        return text

    if check.get("equals") is not None and read_text() != check["equals"]:
        return False, "content not verbatim-equal", sha
    contains = check.get("contains")
    if contains:
        if isinstance(contains, str):
            contains = [contains]
        body = read_text()
        for needle in contains:
            if needle not in body:
                return False, "missing verbatim content: {!r}".format(needle), sha
    if check.get("sha256") and not sha.lower().startswith(str(check["sha256"]).lower()):
        return False, "sha256 mismatch want~{} got {}".format(check["sha256"], sha[:12]), sha
    if check.get("baselineSha256"):
        base = str(check["baselineSha256"]).lower()
        if sha.lower() == base and not check.get("expectNoChange"):
            return False, "artifact unchanged from baseline ({}): this round cannot be " \
                          "attributed to it".format(sha[:12]), sha
        if sha.lower() != base and check.get("expectNoChange"):
            return False, "artifact changed although expectNoChange was declared", sha
    return True, "checked[{}] sha256[:12]={}".format(",".join(predicates), sha[:12]), sha


# ----------------------------------------------------------------------
# adapter（真：worker_adapter.mjs；测：注入的假 adapter）
# ----------------------------------------------------------------------
class DispatchError(Exception):
    pass


def _first(obj, keys):
    if not isinstance(obj, dict):
        return None
    for k in keys:
        v = obj.get(k)
        if v:
            return v
    return None


class AdapterClient(object):
    """worker_adapter.mjs 的薄封装，方法签名与 FakeAdapter 一致。"""

    def __init__(self, cmd, project, controller, backend, timeout=60):
        self.cmd = list(cmd)
        self.project = project
        self.controller = controller
        self.backend = backend
        self.timeout = timeout

    def for_order(self, order):
        """R04/C01 (2026-10-28): route per-order backend/project instead of the fixed
        global default. Reuses the SAME cmd+controller (one manager, never a fork) and
        only clones when the order actually declares a different route."""
        payload = (order or {}).get("payload") or {}
        if not isinstance(payload, dict):
            payload = {}
        ob = payload.get("backend") or (order or {}).get("backend") or self.backend
        op = payload.get("project") or (order or {}).get("project") or self.project
        if str(ob).endswith("_gui"):
            # GUI 后端的真正执行者是外部的 r10c 驱动，daemon 的 start 只会登记
            # 一个 Noop worker（不执行）。必须经 butler_dispatch 派工，否则订单会
            # 永远 running 直到 revision_required。查询/收结果/ack 仍走同一 adapter。
            client = GuiDispatchClient(self.cmd, op, self.controller, ob, self.timeout,
                                       dispatch_script=self._dispatch_script())
            client.order_payload = payload
            return client
        same = (ob == self.backend and
                os.path.abspath(op or ".") == os.path.abspath(self.project or "."))
        if same:
            return self
        return AdapterClient(self.cmd, op, self.controller, ob, self.timeout)

    def _dispatch_script(self):
        """butler_dispatch.mjs 与 worker_adapter.mjs 同目录（同一 scripts 目录部署）。"""
        for part in self.cmd:
            if str(part).endswith("worker_adapter.mjs"):
                return os.path.join(os.path.dirname(part), "butler_dispatch.mjs")
        return None

    def _invoke(self, args, timeout=None):
        full = self.cmd + list(args)
        try:
            proc = subprocess.run(
                full, capture_output=True, timeout=timeout or self.timeout, cwd=self.project
            )
        except (OSError, subprocess.SubprocessError) as exc:
            return 1, None, "", "invoke failed: {}".format(exc)
        out = (proc.stdout or b"").decode("utf-8", errors="replace")
        err = (proc.stderr or b"").decode("utf-8", errors="replace")
        obj = None
        try:
            obj = json.loads(out.strip())
        except ValueError:
            pass
        return proc.returncode, obj, out, err

    def start(self, request_id, prompt_file, model=None):
        args = [
            "start", "--type", self.backend, "--cwd", self.project,
            "--prompt-file", prompt_file, "--request-id", request_id,
            "--controller-session", self.controller,
        ]
        if model:
            args += ["--model", model]
        rc, obj, out, err = self._invoke(args)
        if rc != 0 or obj is None:
            raise DispatchError("adapter start rc={} out={} err={}".format(rc, out[:200], err[:200]))
        run_id = _first(obj, ("runId", "runID", "id")) or _first(obj.get("run"), ("runId", "id"))
        turn_id = _first(obj, ("turnId", "turnID")) or _first(obj.get("run"), ("turnId",))
        if not run_id:
            raise DispatchError("adapter start returned no runId: {}".format(out[:200]))
        return run_id, turn_id

    def status(self, run_id):
        args = ["status", run_id, "--cwd", self.project, "--controller-session", self.controller]
        rc, obj, out, err = self._invoke(args, timeout=30)
        if obj is None:
            return ST_UNKNOWN, obj, "unparsable status output rc={} out={} err={}".format(rc, out[:200], err[:200])
        if isinstance(obj, dict) and obj.get("error"):
            return ST_UNKNOWN, obj, "adapter error: {}".format(obj.get("error"))
        raw = _first(obj, ("status", "state", "phase"))
        if raw is None and isinstance(obj.get("run"), dict):
            raw = _first(obj["run"], ("status", "state", "phase"))
        raw = str(raw).lower() if raw else ST_UNKNOWN
        if raw in NON_TERMINAL:
            return "running", obj, "in progress ({})".format(raw)
        if raw in (ST_FINISHED,) or raw in TERMINAL_BAD:
            return raw, obj, "terminal ({})".format(raw)
        return ST_UNKNOWN, obj, "unrecognised status {!r}".format(raw)

    def events(self):
        """返回 (ok, events, detail)。ok=False 表示查询不可用 ≠ 没有事件。"""
        args = ["events", "--cwd", self.project, "--controller-session", self.controller]
        rc, obj, out, err = self._invoke(args, timeout=30)
        if rc != 0 and obj is None:
            return False, [], "events query failed rc={} err={}".format(rc, err[:160])
        if isinstance(obj, dict):
            if obj.get("error"):
                return False, [], "events error: {}".format(obj.get("error"))
            obj = obj.get("events") or obj.get("items") or []
        if not isinstance(obj, list):
            return False, [], "events returned unparsable payload: {}".format(out[:160])
        return True, [e for e in obj if isinstance(e, dict)], "ok"

    def ack(self, event_id):
        args = ["ack", "--cwd", self.project, "--controller-session", self.controller, event_id]
        rc, obj, out, err = self._invoke(args, timeout=30)
        return rc, obj if obj is not None else {"raw": out[:200], "err": err[:160]}

    def result(self, run_id, turn_id=None):
        """取整轮完整回复与身份（F-WAVE-03：验收前必须拿到完整 reply）。

        返回 (state, payload, detail)，state ∈ ok|empty|unavailable。
        """
        args = ["result", run_id, "--cwd", self.project, "--controller-session", self.controller]
        if turn_id:
            args += ["--turn", turn_id]
        rc, obj, out, err = self._invoke(args, timeout=45)
        if obj is None:
            return "unavailable", None, "result unparsable rc={} out={} err={}".format(
                rc, out[:160], err[:160])
        if isinstance(obj, dict) and obj.get("error"):
            return "unavailable", obj, "result error: {}".format(obj.get("error"))
        reply = obj.get("reply") if isinstance(obj, dict) else None
        if reply is None:
            return "empty", obj, "result carried no reply field"
        return "ok", obj, "reply {} chars".format(len(reply))

    def list_runs(self):
        """真实协议返回 {workers:[{runId,cwd,status,currentTurnId,...}]}，行内无 requestId。"""
        args = ["list", "--cwd", self.project, "--controller-session", self.controller]
        rc, obj, out, err = self._invoke(args, timeout=30)
        if isinstance(obj, dict):
            obj = (obj.get("workers") or obj.get("runs") or obj.get("items")
                   or obj.get("records") or [])
        if not isinstance(obj, list):
            return []
        return [r for r in obj if isinstance(r, dict)]

    def find_run_by_request(self, request_id):
        """重复提交护栏：adapter 侧是否已有同 requestId 的 run。"""
        try:
            runs = self.list_runs()
        except Exception:
            return None
        for r in runs:
            if str(r.get("requestId") or r.get("request_id") or "") == str(request_id):
                return r.get("runId") or r.get("id")
        return None

    def reconcile_request(self, request_id, known_run_id=None):
        """F-WAVE-03：`list` 行里没有 requestId，不能凭它反查。

        返回 (run_id, how, note)：
          - 证据里已有 runId → adopt（唯一可靠绑定）
          - 行里真带 requestId（协议升级后）→ 按 requestId 命中
          - 否则 unknown：调用方必须走 status/对账，绝不清 runId 重派。
        """
        if known_run_id:
            return known_run_id, "evidence", "adopted runId already bound in evidence"
        runs = []
        try:
            runs = self.list_runs()
        except Exception as exc:
            return None, "unavailable", "list failed: {}".format(exc)
        for r in runs:
            rid = str(r.get("requestId") or r.get("request_id") or "")
            if rid and rid == str(request_id):
                return (r.get("runId") or r.get("id")), "requestId", \
                    "matched run by requestId from list"
        return None, "unknown", \
            "adapter list rows carry no requestId; cannot resolve request {} externally".format(request_id)


class GuiDispatchClient(AdapterClient):
    """GUI 订单的真实派工：经 butler_dispatch.mjs 启动外部 r10c 驱动。

    daemon 对 *_gui 的 start 只登记 Noop worker（不执行），因此 GUI 订单不能用
    AdapterClient.start。其余动作（status/result/events/ack）与 CLI 订单一致，
    继续走 worker_adapter，保证同一套对账/精确 ack 语义。
    """

    def __init__(self, cmd, project, controller, backend, timeout=60, dispatch_script=None):
        super().__init__(cmd, project, controller, backend, timeout)
        self.dispatch_script = dispatch_script
        self.order_payload = {}

    def start(self, request_id, prompt_file, model=None):
        if not self.dispatch_script or not os.path.exists(self.dispatch_script):
            raise DispatchError("gui dispatch script not found: {}".format(self.dispatch_script))
        node = self.cmd[0] if self.cmd else "node"
        args = [
            self.dispatch_script, "dispatch",
            "--project", self.project,
            "--task-file", prompt_file,
            "--controller", self.controller,
            "--backend", self.backend,
            "--request-id", request_id,
            "--new",
        ]
        if model or self.order_payload.get("model"):
            args += ["--model", model or self.order_payload.get("model")]
        for art in (self.order_payload.get("artifacts") or []):
            args += ["--artifact", art]
        try:
            proc = subprocess.run([node] + args, capture_output=True, timeout=self.timeout,
                                  cwd=self.project)
        except (OSError, subprocess.SubprocessError) as exc:
            raise DispatchError("gui dispatch invoke failed: {}".format(exc))
        out = (proc.stdout or b"").decode("utf-8", errors="replace")
        err = (proc.stderr or b"").decode("utf-8", errors="replace")
        obj = None
        try:
            obj = json.loads(out.strip())
        except ValueError:
            pass
        if proc.returncode != 0 or not isinstance(obj, dict) or not obj.get("ok"):
            raise DispatchError("gui dispatch rc={} out={} err={}".format(
                proc.returncode, out[-300:], err[-200:]))
        run_id = obj.get("runId")
        turn_id = obj.get("turnId")
        if not run_id:
            raise DispatchError("gui dispatch returned no runId: {}".format(out[:200]))
        return run_id, turn_id


# ----------------------------------------------------------------------
# 单实例执行锁（每个队列一把，按 pid 存活判定，不按文件年龄抢占）
# ----------------------------------------------------------------------
def _pid_alive(pid):
    try:
        pid = int(pid)
    except (TypeError, ValueError):
        return False
    if pid <= 0:
        return False
    if os.name == "nt":
        import ctypes
        PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
        STILL_ACTIVE = 259
        kernel32 = ctypes.windll.kernel32
        handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
        if not handle:
            return False
        try:
            code = ctypes.c_ulong()
            if kernel32.GetExitCodeProcess(handle, ctypes.byref(code)):
                return code.value == STILL_ACTIVE
            return True
        finally:
            kernel32.CloseHandle(handle)
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def lock_path_for(queue_path):
    return os.path.abspath(queue_path) + ".runner.lock"


def acquire_queue_lock(queue_path):
    """拿到锁返回锁路径；已有活着的 runner 持有时返回 None（绝不并发跑同一队列）。"""
    path = lock_path_for(queue_path)
    if os.path.exists(path):
        holder = None
        try:
            with open(path, "r", encoding="utf-8") as f:
                holder = json.load(f)
        except (ValueError, OSError):
            holder = None
        pid = (holder or {}).get("pid")
        if pid and _pid_alive(pid):
            return None
        # 持锁进程已死：回收陈旧锁（不是抢占活锁）
        try:
            os.remove(path)
        except OSError:
            pass
    d = os.path.dirname(path)
    if d and not os.path.isdir(d):
        os.makedirs(d, exist_ok=True)
    tmp = path + ".tmp.{}".format(os.getpid())
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump({"pid": os.getpid(), "queue": os.path.abspath(queue_path),
                   "acquiredAt": time.strftime("%Y-%m-%dT%H:%M:%S")}, f, ensure_ascii=False)
    try:
        os.replace(tmp, path)
    except OSError:
        if os.path.exists(path):
            return None
        raise
    return path


def release_queue_lock(path):
    if not path:
        return
    try:
        with open(path, "r", encoding="utf-8") as f:
            holder = json.load(f)
        if holder.get("pid") == os.getpid():
            os.remove(path)
    except (ValueError, OSError):
        pass


# ----------------------------------------------------------------------
# 编排
# ----------------------------------------------------------------------
def poll_status(adapter, run_id, timeout_s, interval_s, log):
    deadline = time.time() + max(0.0, timeout_s)
    while True:
        status, obj, detail = adapter.status(run_id)
        if status in (ST_FINISHED,) or status in TERMINAL_BAD or status == ST_UNKNOWN:
            return status, detail
        if timeout_s <= 0:
            # R04 tick 单发模式：不等待。非终态如实原样返回（调用方按"仍在跑"处理，
            # 不得把它当成 ST_TIMEOUT 升级成 revision_required）。
            return status, detail
        if time.time() >= deadline:
            return ST_TIMEOUT, "poll timeout after {:.0f}s".format(timeout_s)
        time.sleep(max(0.0, interval_s))


def fetch_full_result(adapter, run_id, turn_id, expect_request_id=None, expect_controller=None):
    """验收前取完整模型回复并核身份（F-WAVE-03：不能只看 status 文件）。

    返回 (ok, payload, detail)。身份任一维对不上即不合格，绝不降级成警告。
    """
    if not hasattr(adapter, "result"):
        return False, None, "adapter has no result() — full reply cannot be verified"
    state, payload, detail = adapter.result(run_id, turn_id)
    if state != "ok" or not isinstance(payload, dict):
        return False, payload, "result {}: {}".format(state, detail)
    reply = payload.get("reply")
    if not isinstance(reply, str) or not reply.strip():
        return False, payload, "result reply is empty"
    got_run = payload.get("runId")
    if got_run and str(got_run) != str(run_id):
        return False, payload, "result runId mismatch: {} != {}".format(got_run, run_id)
    got_turn = payload.get("turnId")
    if turn_id and got_turn and str(got_turn) != str(turn_id):
        return False, payload, "result turnId mismatch: {} != {}".format(got_turn, turn_id)
    got_req = payload.get("requestId")
    if expect_request_id and got_req and str(got_req) != str(expect_request_id):
        return False, payload, "result requestId mismatch: {} != {}".format(
            got_req, expect_request_id)
    got_ctrl = payload.get("controllerSessionId")
    if expect_controller and got_ctrl and str(got_ctrl) != str(expect_controller):
        return False, payload, "result controller mismatch: {} != {}".format(
            got_ctrl, expect_controller)
    if not (payload.get("replySha256") or payload.get("replySha256Full")):
        return False, payload, "result has no reply hash to bind the artifact to"
    return True, payload, "reply {} chars, sha={}".format(
        len(reply), str(payload.get("replySha256") or "")[:12])


ACKABLE_TYPES = ("turn_completed", "turn_recovered")


def ack_turn_completed(adapter, run_id, turn_id, log, expect_request_id=None,
                       expect_controller=None, want_types=ACKABLE_TYPES):
    """精确 ack：run + turn（+ request/controller 若已知）全部一致才算确认。

    F-WAVE-03 复核点：
      * 事件 turnId=None 过去被当成通配 → 会 ack 到别人的轮次，现在直接判不匹配；
      * events 查询失败 ≠ 没有事件，必须回报“未确认”而不是成功；
      * ack 返回 0 项只有在盘上真的存在已 ack 同 tuple 事件时才幂等成功。
    """
    if not turn_id:
        return False, None, "strict ack needs a turnId (refusing to match by runId alone)"

    ok_q, evs, qdetail = adapter.events()
    if not ok_q:
        return False, None, "events query unavailable — ack state UNKNOWN, not failed nor done: {}".format(qdetail)

    def matches(e):
        if e.get("type") not in want_types:
            return False
        if str(e.get("runId") or "") != str(run_id):
            return False
        if str(e.get("turnId") or "") != str(turn_id):   # None/'' 都不算匹配
            return False
        if expect_request_id and e.get("requestId") and str(e["requestId"]) != str(expect_request_id):
            return False
        if expect_controller and e.get("controllerSessionId") and \
                str(e["controllerSessionId"]) != str(expect_controller):
            return False
        return True

    matched = [e for e in evs if matches(e)]
    if not matched:
        return False, None, "no {} event for run {} turn {} (events={})".format(
            "/".join(want_types), run_id, turn_id, len(evs))

    pending = [e for e in matched if e.get("acked") is not True]
    acked = [e for e in matched if e.get("acked") is True]
    if not pending:
        if not acked:
            return False, None, "internal: matched events with neither pending nor acked state"
        return True, acked[0].get("eventId"), "already acked {} (idempotent re-run)".format(
            acked[0].get("eventId"))
    if len(pending) > 1:
        return False, None, "multiple pending {} events for one turn ({}) — no blind ack".format(
            "/".join(want_types), len(pending))

    target = pending[0].get("eventId")
    if not target:
        return False, None, "pending event has no eventId; cannot ack precisely"
    rc, obj = adapter.ack(target)
    if rc != 0:
        return False, target, "adapter ack rc={} obj={}".format(rc, obj)

    # adapter.events() is the PENDING (unacked) view — a successful ack REMOVES the
    # event from it. So after ack rc==0: success iff the target is no longer pending.
    # (Before 2026-10-28 this demanded an `acked:true` flag that the pending-only view
    # never returns, so a genuinely-successful ack was mislabeled unverified -> failed.)
    ok_q2, evs2, qdetail2 = adapter.events()
    if not ok_q2:
        return False, target, "ack issued but re-query unavailable ({}): NOT confirmed".format(qdetail2)
    still_pending = [e for e in evs2 if e.get("eventId") == target and e.get("acked") is not True]
    if still_pending:
        return False, target, "ack not persisted — event {} still pending".format(target)
    # present with acked:true OR absent (removed from pending) are both proof of ack.
    return True, target, "acked {} ({})".format(target, pending[0].get("type"))


def _order_scope(order):
    """订单的业务作用域：归一化的 (project, controller)。未声明返回 None。"""
    p = (order or {}).get("payload") or {}
    proj = p.get("project") or (order or {}).get("project")
    ctrl = p.get("controller") or (order or {}).get("controller")
    return (os.path.abspath(proj) if proj else None,
            str(ctrl) if ctrl else None)


def _declares_explicit_new_turn(payload):
    """显式新轮意图（原合同：同正文+显式新request 是合法新轮）。

    submissionIntent="new_turn" 或携带 retryOf/continuationOf 血缘才算显式声明；
    缺省（legacy/意外重复）保持保守拦截。allowDuplicateContent 仍是 owner 通道。
    """
    if not isinstance(payload, dict):
        return False
    if str(payload.get("submissionIntent") or "").strip().lower() == "new_turn":
        return True
    return bool(payload.get("retryOf") or payload.get("continuationOf"))


def _same_scope(scope_a, scope_b):
    """双方都声明的维度不同 → 不同作用域；任一方未声明 → 保守按同域。"""
    for a, b in zip(scope_a, scope_b):
        if a and b and a != b:
            return False
    return True


def find_duplicate_content(q, order_id, content_hash, new_payload=None):
    """重复投喂闸（2026-10-28 用户审计；2026-10-29 独立复核修正作用域语义）。

    只拦「同一业务作用域内、原轮未决期间的意外再投喂」：
      - 另一单同 contentHash 处于 ready/running（业务轮未完成）→ 拦（发送前挡下）。
      - 另一单已 accepted（原轮完成）→ 仅当新单声明显式新轮意图
        （submissionIntent="new_turn" 或 retryOf/continuationOf）才放行——
        原合同「完全同prompt、新request」是合法新轮；未声明意图的重复仍保守拦。
      - 双方声明了不同 project/controller → 互不误伤；任一方未声明 → 保守按同域。
    contentHash 是风险线索，不是业务身份（A03：身份=requestId+内容摘要）。
    """
    if not content_hash:
        return None
    try:
        orders = q.all_orders()
    except Exception:
        return None
    new_scope = _order_scope({"payload": new_payload or {}})
    for o in orders:
        if o.get("orderId") == order_id:
            continue
        payload = o.get("payload") or {}
        if payload.get("allowDuplicateContent"):
            continue
        if payload.get("contentHash") != content_hash:
            continue
        if o.get("state") not in ("ready", "running", "accepted"):
            continue
        if not _same_scope(new_scope, _order_scope(o)):
            continue
        if o.get("state") == "accepted" and _declares_explicit_new_turn(new_payload or {}):
            continue
        return o.get("orderId")
    return None


def find_completed_duplicate(q, order_id, content_hash, new_payload=None):
    """闸放行后的事实核对（仅证据透明用，不参与判定）：
    同作用域内同 contentHash 的已完成先前单；无则 None。"""
    if not content_hash:
        return None
    try:
        orders = q.all_orders()
    except Exception:
        return None
    new_scope = _order_scope({"payload": new_payload or {}})
    for o in orders:
        if o.get("orderId") == order_id:
            continue
        payload = o.get("payload") or {}
        if payload.get("contentHash") != content_hash or o.get("state") != "accepted":
            continue
        if not _same_scope(new_scope, _order_scope(o)):
            continue
        return o.get("orderId")
    return None


def safe_mark_result(q, order_id, state, note, log):
    cur = q.get_order(order_id)
    if cur is None:
        return None
    if cur["state"] != "running":
        log("  [{0}] skip mark_result({1}): state is {2}".format(order_id, state, cur["state"]))
        return cur
    q.mark_result(order_id, state, note)
    return q.get_order(order_id)


PROVE_PRESEND_REJECT = (
    "missing --", "missing run-id", "invalid run-id", "prompt file not found",
    "prompt-file missing", "invalid --type", "usage:", "unknown command",
    # A02 (288h 2026-10-28)：daemon 登记期费用/工作区拒绝 = 证明未发送（worker 根本没建）。
    # 实证：r04-smoke2 tick 冒烟——zcode 经闸口 E_SURFACE_UNSUPPORTED 拒绝后曾被误判
    # "dispatch-uncertain"→revision_required；闸口拒绝发生在注册阶段，语义上是可证明的未发送。
    "e_surface_unsupported", "e_model_not_resolved", "e_price_unknown",
    "e_paid_model_not_approved", "e_entitlement_expired", "e_entitlement_stale",
    "e_no_free_verified_candidate", "e_model_not_in_surface", "e_workspace_conflict",
    "e_cost_probe_forbidden", "e_effort_unsupported", "e_paid_fallback_forbidden",
)


def start_rejected_before_send(exc):
    """只有能证明请求根本没被接受时，才允许当成“未发送”。"""
    msg = str(exc).lower()
    return any(marker in msg for marker in PROVE_PRESEND_REJECT)


# v3-integrated main.py 路径（只读调用，不修改）
_MAIN_PY = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)),
                 "..", "v3-integrated", "main.py")
)

# 绝对禁止写入的路径后缀（防误覆盖产品发布清单）
_FORBIDDEN_SUFFIXES = ("manifest.json", "rc1-audit-manifest.json")


def run_delivery_audit(project_dir, evidence_dir, run_id, turn_id, log,
                       python_bin=None, timeout=60):
    """对 project_dir 执行一次只读 delivery audit，信封写到 evidence_dir。

    信封文件名带有 runId+turnId，确保绑定本轮结果；不写 <root>/manifest.json。
    返回 (envelope_path, status, view_dict):
      status ∈ "ok" | "absent" | "malformed" | "error"
    订单业务结论不受本函数失败影响（调用方捕获并降级）。
    """
    if _audit_view_mod is None:
        log("  [audit] audit_view module unavailable; skip delivery audit")
        return None, "absent", None

    # 路径安全检查
    if not os.path.isdir(project_dir):
        log("  [audit] project_dir not a directory: {}".format(project_dir))
        return None, "absent", None
    if not os.path.isfile(_MAIN_PY):
        log("  [audit] main.py not found: {}".format(_MAIN_PY))
        return None, "absent", None

    os.makedirs(evidence_dir, exist_ok=True)

    # 信封文件名：<runId>-<turnId>-audit.json，全部安全化为文件名
    safe_run = str(run_id).replace("/", "_").replace("\\", "_")[:40]
    safe_turn = str(turn_id).replace("/", "_").replace("\\", "_")[:40]
    envelope_name = "{}-{}-audit.json".format(safe_run, safe_turn)
    envelope_path = os.path.join(evidence_dir, envelope_name)

    # 安全检查：绝不写禁止路径
    for suffix in _FORBIDDEN_SUFFIXES:
        if envelope_path.endswith(suffix):
            log("  [audit] envelope path conflicts with forbidden suffix: {}".format(envelope_path))
            return None, "error", None

    py = python_bin or sys.executable
    cmd = [py, _MAIN_PY, "audit", os.path.abspath(project_dir),
           "--out-manifest", envelope_path]
    log("  [audit] running: {}".format(" ".join(cmd)))
    try:
        proc = subprocess.run(
            cmd, capture_output=True, timeout=timeout,
            cwd=os.path.dirname(_MAIN_PY)
        )
        stdout = (proc.stdout or b"").decode("utf-8", errors="replace")
        stderr = (proc.stderr or b"").decode("utf-8", errors="replace")
        log("  [audit] rc={} stdout={} stderr={}".format(
            proc.returncode, stdout[:200], stderr[:200]))
    except subprocess.TimeoutExpired:
        log("  [audit] delivery audit timed out after {}s".format(timeout))
        return None, "error", None
    except OSError as exc:
        log("  [audit] delivery audit OSError: {!r}".format(exc))
        return None, "absent", None

    if proc.returncode != 0 and not os.path.isfile(envelope_path):
        log("  [audit] audit rc={} and envelope not written".format(proc.returncode))
        return None, "error", None

    if not os.path.isfile(envelope_path):
        log("  [audit] envelope not written (no error reported)")
        return None, "absent", None

    # 读取 audit stdout JSON（main.py audit 把结果打印到 stdout）并注入绑定字段
    stdout_obj = None
    try:
        stdout_obj = json.loads(stdout.strip())
    except ValueError:
        pass

    # 读信封文件（main.py --out-manifest 写的是清单 JSON，不是信封）
    # 把 stdout 结果封装成 v4 信封格式写进 evidence
    try:
        with open(envelope_path, "r", encoding="utf-8") as f:
            manifest_data = json.load(f)
    except (OSError, ValueError) as exc:
        log("  [audit] cannot read manifest file: {!r}".format(exc))
        return envelope_path, "malformed", None

    # 构造 v4 信封（command=audit，包含 stdout 里的 count/total_bytes/zip，
    #              绑定字段用于证明本轮生成）
    envelope = {
        "schema": "butler-delivery-audit/4",
        "command": "audit",
        "ok": (stdout_obj.get("zip", {}).get("ok", True)
               if isinstance(stdout_obj, dict) else True)
               if proc.returncode == 0 else False,
        "errors": [],
        "count": (manifest_data.get("count", "unknown")
                  if isinstance(manifest_data, dict) else "unknown"),
        "total_bytes": (manifest_data.get("total_bytes", "unknown")
                        if isinstance(manifest_data, dict) else "unknown"),
        "manifest": envelope_path,
        "zip": (stdout_obj.get("zip") if isinstance(stdout_obj, dict) else None),
        # 绑定字段：证明这份信封属于本轮 run
        "_binding": {
            "runId": run_id,
            "turnId": turn_id,
            "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "auditRc": proc.returncode,
        },
    }
    # 把信封写回（覆盖 --out-manifest 写的清单，在 evidence 目录里）
    tmp = envelope_path + ".tmp.{}".format(os.getpid())
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(envelope, f, ensure_ascii=False, indent=2)
        os.replace(tmp, envelope_path)
    except OSError as exc:
        log("  [audit] cannot write envelope: {!r}".format(exc))
        return envelope_path, "error", None

    # 解析视图
    view, vstatus = _audit_view_mod.load_envelope_file(envelope_path)
    log("  [audit] envelope={} sha256[:12]={} vstatus={}".format(
        os.path.basename(envelope_path),
        sha256_file(envelope_path)[:12],
        vstatus))
    return envelope_path, vstatus, view


def _attach_audit_view(ev, order_id, payload, log,
                       run_id=None, turn_id=None, evidence_dir=None,
                       project_dir=None, python_bin=None):
    """生成 v4 审计信封（若 project_dir 已知），再把紧凑视图写进证据。

    优先级：
    1. 若 project_dir 已知 且 run_id/turn_id 已知 → 调 run_delivery_audit 生成信封，
       信封写到 evidence_dir（绑定本轮 run）。
    2. 若 payload 里有 auditEnvelopePath → 直接读（兼容外部预置）。
    3. 否则 → absent。
    绑定验证：信封文件名含 runId/turnId，调用方负责传入正确值；
    文件存在但 runId/turnId 对不上时写 auditViewStatus="absent"。
    不阻塞业务、不修改 payload、不重发；任何失败不影响订单状态机结论。
    """
    if _audit_view_mod is None:
        ev.record(order_id, auditViewStatus="absent", auditView=None)
        return

    # 路径 1：本轮生成
    if project_dir and run_id and turn_id and evidence_dir:
        try:
            env_path, status, view = run_delivery_audit(
                project_dir, evidence_dir, run_id, turn_id, log,
                python_bin=python_bin)
        except Exception as exc:
            log("  [{}] run_delivery_audit exception: {!r}".format(order_id, exc))
            status, view = "error", None
            env_path = None
        ev.record(order_id, auditViewStatus=status, auditView=view,
                  auditEnvelopePath=env_path)
        log("  [{}] auditViewStatus={} (generated) command={} ok={} "
            "entry_count={} zip_ok={}".format(
                order_id, status,
                (view or {}).get("command"),
                (view or {}).get("ok"),
                (view or {}).get("entry_count"),
                (view or {}).get("zip_ok")))
        return

    # 路径 2：payload 里有预置路径
    path = (payload or {}).get("auditEnvelopePath") if isinstance(payload, dict) else None
    if path:
        # 绑定验证：若 run_id/turn_id 已知，检查文件名是否含它们（松校验）
        if run_id and turn_id:
            basename = os.path.basename(path)
            safe_run = str(run_id).replace("/", "_").replace("\\", "_")[:40]
            safe_turn = str(turn_id).replace("/", "_").replace("\\", "_")[:40]
            if safe_run not in basename or safe_turn not in basename:
                log("  [{}] auditEnvelopePath binding mismatch: "
                    "run={} turn={} basename={}".format(order_id, run_id, turn_id, basename))
                ev.record(order_id, auditViewStatus="absent",
                          auditView=None, auditEnvelopePath=path)
                return
        try:
            view, status = _audit_view_mod.load_envelope_file(path)
        except Exception as exc:
            log("  [{}] audit_view load error: {!r}".format(order_id, exc))
            ev.record(order_id, auditViewStatus="malformed", auditView=None)
            return
        ev.record(order_id, auditViewStatus=status, auditView=view,
                  auditEnvelopePath=path)
        return

    # 路径 3：absent
    ev.record(order_id, auditViewStatus="absent", auditView=None)


def run_order(q, ev, adapter, order, cfg, log, wait=True):
    """处理一单直到终态（含重试）。返回 (outcome, detail)。
    wait=False（R04 tick）：派发绑定身份后立即返回 "dispatched"，不在订单上轮询等待——
    外部执行继续，结果由后续 tick 的 reconcile_running 收取。"""
    oid = order["orderId"]
    payload = order.get("payload") or {}
    if not isinstance(payload, dict):
        payload = {}
    check = payload.get("check")
    prompt_file = payload.get("promptFile")
    model = payload.get("model")
    controller = cfg.get("controller")

    while True:
        cur = q.get_order(oid)
        attempts = int(cur.get("attempts") or 0)
        if attempts >= cfg["max_attempts"]:
            return "skipped", "attempts {} >= max_attempts {}".format(attempts, cfg["max_attempts"])
        if cur["state"] == "ready":
            q.mark_running(oid)
            cur = q.get_order(oid)

        # ---- 派发（幂等锁：证据里已有 runId 就不许再派） ----
        run_id = ev.dispatched_run(oid) or cur.get("runId")
        turn_id = (ev.entry(oid).get("turnId") or cur.get("turnId"))
        if not run_id:
            adopted, how, note = adapter.reconcile_request(oid, None)
            if adopted:
                run_id = adopted
                log("  [{0}] adopt existing run {1} via {2} (no new dispatch)".format(oid, run_id, how))
            elif how == "unknown":
                # 复核点：没有 runId 不代表没发过——manager 可能已接受而回执没回来。
                log("  [{0}] cannot resolve request externally: {1}".format(oid, note))
        if not run_id:
            if not prompt_file or not os.path.exists(prompt_file):
                safe_mark_result(q, oid, "failed", "prompt-file missing: {}".format(prompt_file), log)
                return "failed", "prompt-file missing: {}".format(prompt_file)
            dup = find_duplicate_content(q, oid, payload.get("contentHash"), new_payload=payload)
            if dup:
                note = ("duplicate_content_blocked: same business content already carried by {} "
                        "(sha12={}); refusing a second feed — owner override required "
                        "(payload.allowDuplicateContent=true)".format(
                            dup, str(payload.get("contentHash"))[:12]))
                safe_mark_result(q, oid, "revision_required", note, log)
                ev.record(oid, state="revision_required", receiptState="duplicate_content_blocked",
                          duplicateOf=dup, contentHash=payload.get("contentHash"), note=note)
                log("  [{0}] {1}".format(oid, note))
                return "revision_required", note
            cleared_dup = find_completed_duplicate(q, oid, payload.get("contentHash"), payload)
            q.mark_running(oid)
            # 派发意图先落盘：之后任何异常都按“已发出但未确认”处理，不重发。
            ev.record(oid, requestId=oid, controller=controller,
                      dispatchIntentAt=time.strftime("%Y-%m-%dT%H:%M:%S"),
                      submissionIntent=payload.get("submissionIntent"),
                      duplicateContentRiskCleared=cleared_dup,
                      receiptState="intent_persisted")
            try:
                run_id, turn_id = adapter.start(oid, prompt_file, model)
            except Exception as exc:
                detail = "dispatch-uncertain: {}".format(exc)[:400]
                if start_rejected_before_send(exc):
                    log("  [{0}] dispatch rejected pre-send (provably not submitted): {1}".format(oid, exc))
                    safe_mark_result(q, oid, "failed", detail, log)
                    ev.record(oid, receiptState="rejected_pre_send", note=detail)
                    cur = q.get_order(oid)
                    if int(cur.get("attempts") or 0) >= cfg["max_attempts"]:
                        return "failed", detail
                    q.requeue(oid, detail)
                    if not wait:
                        # R04 tick：拒绝已入账（requeued），不在本 tick 内重试——下次 tick 再取
                        return "rejected_pre_send", detail
                    continue
                # 无法证明未发送 → 保留身份，交对账，绝不自动重发
                safe_mark_result(q, oid, "revision_required", detail, log)
                ev.record(oid, state="revision_required", receiptState="unconfirmed", note=detail)
                return "revision_required", "dispatch_unconfirmed (identity kept, no auto retry)"
            q.attach_run(oid, run_id, turn_id)
            ev.record(oid, runId=run_id, turnId=turn_id, dispatchedAt=time.strftime("%Y-%m-%dT%H:%M:%S"),
                      receiptState="run_bound")
            log("  [{0}] dispatched runId={1} turnId={2}".format(oid, run_id, turn_id))
        else:
            log("  [{0}] reuse recorded runId={1} (no duplicate dispatch)".format(oid, run_id))

        if not wait:
            # R04 tick：身份已绑定（本次派发或复用既有 runId）——立即返回，绝不在本单上等待。
            return "dispatched", run_id

        if not turn_id:
            note = "no turnId bound for run {} — strict ack is impossible".format(run_id)
            safe_mark_result(q, oid, "revision_required", note, log)
            ev.record(oid, state="revision_required", observationState="turn_unbound", note=note)
            return "revision_required", note

        # ---- 轮询到终态 ----
        status, detail = poll_status(adapter, run_id, cfg["timeout"], cfg["poll_interval"], log)
        log("  [{0}] status={1} ({2})".format(oid, status, detail))
        ev.record(oid, businessState=status, observationState="polled"
                  if status not in (ST_UNKNOWN, ST_TIMEOUT) else "observation_" + status)

        if status == ST_UNKNOWN or status == ST_TIMEOUT:
            note = "requires_reconcile: adapter status {} ({})".format(status, detail)
            safe_mark_result(q, oid, "revision_required", note[:400], log)
            ev.record(oid, state="revision_required", note=note)
            return "revision_required", note

        if status in TERMINAL_BAD:
            note = "turn {}: {}".format(status, detail)
            safe_mark_result(q, oid, "failed", note[:400], log)
            ev.record(oid, state="failed", note=note)
            cur = q.get_order(oid)
            if int(cur.get("attempts") or 0) >= cfg["max_attempts"]:
                return "failed", note
            log("  [{0}] requeue for retry (attempt {1})".format(oid, cur.get("attempts")))
            q.requeue(oid, note)
            continue

        # ---- 完整结果（验收前必做：拿全 reply + 核身份） ----
        ok_res, res_payload, rdetail = fetch_full_result(adapter, run_id, turn_id, oid, controller)
        if not ok_res:
            note = "full-result-failed: {}".format(rdetail)
            safe_mark_result(q, oid, "failed", note[:400], log)
            ev.record(oid, state="failed", note=note, receiptState="result_unverified")
            return "failed", note
        ev.record(oid,
                  replyChars=len(res_payload.get("reply") or ""),
                  replySha256=res_payload.get("replySha256") or res_payload.get("replySha256Full"),
                  replyKind=res_payload.get("replyKind"),
                  nativeSessionId=res_payload.get("nativeSessionId"),
                  receiptState="result_verified")
        log("  [{0}] full result OK: {1}".format(oid, rdetail))

        # ---- 独立检查（先检查，后 ack） ----
        ok, cdetail, sha = check_artifact(check, cfg["project"])
        ev.record(oid, artifactSha256=sha, check=cdetail)
        if not ok:
            note = "independent-check-failed: {}".format(cdetail)
            log("  [{0}] {1} — event left unacked".format(oid, note))
            safe_mark_result(q, oid, "failed", note[:400], log)
            ev.record(oid, state="failed", note=note)
            cur = q.get_order(oid)
            if int(cur.get("attempts") or 0) >= cfg["max_attempts"]:
                return "failed", note
            q.requeue(oid, note)
            continue
        log("  [{0}] independent check OK: {1}".format(oid, cdetail))

        # ---- 精确 ack（turn_completed / turn_recovered 都可承接） ----
        ok_ack, event_id, adetail = ack_turn_completed(
            adapter, run_id, turn_id, log, expect_request_id=oid, expect_controller=controller)
        if not ok_ack:
            note = "ack-failed: {}".format(adetail)
            safe_mark_result(q, oid, "failed", note[:400], log)
            ev.record(oid, state="failed", note=note, receiptState="ack_unconfirmed")
            return "failed", note

        safe_mark_result(q, oid, "accepted", None, log)
        try:
            q.ack(oid)
        except ValueError:
            pass
        ev.record(
            oid, state="accepted", runId=run_id, turnId=turn_id,
            eventId=event_id, artifactSha256=sha, check=cdetail,
            receiptState="acked_and_accepted",
            acceptanceHash=payload.get("acceptanceHash"),
            actionClass=payload.get("actionClass"),
            finishedAt=time.strftime("%Y-%m-%dT%H:%M:%S"),
        )
        log("  [{0}] ACCEPTED runId={1} turnId={2} eventId={3} sha={4}".format(
            oid, run_id, turn_id, event_id, (sha or "")[:12]))
        # ---- v4 审计视图（在 accepted 终态挂接，不阻塞业务） ----
        _attach_audit_view(ev, oid, payload, log,
                           run_id=run_id, turn_id=turn_id,
                           evidence_dir=cfg.get("evidence_dir"),
                           project_dir=cfg.get("project"),
                           python_bin=cfg.get("python_bin"))
        return "accepted", "ok"


def run_offline_verify(q, ev, order, cfg, log):
    """Step 5: honestly complete a non-model offline order that carries a real,
    verifiable local executable (`check` artifact judgement or `localCommand`).

    This NEVER touches the adapter/daemon (campaign_orders 硬约束2) and NEVER
    blind-accepts: acceptance requires the check to genuinely pass, so there is
    always real evidence behind an "accepted" offline order.
    """
    oid = order["orderId"]
    payload = order.get("payload") or {}
    check = payload.get("check")
    cmd = payload.get("localCommand")
    q.mark_running(oid)
    ev.record(oid, requestId=oid, controller=cfg.get("controller"),
              executionClass="offline_verify", adapterCalled=False,
              dispatchedAt=time.strftime("%Y-%m-%dT%H:%M:%S"))
    # 重复投喂闸对离线路径同样生效：同一业务正文已在别的 ready/running/accepted
    # 订单上出现时，绝不第二次执行（owner 显式 allowDuplicateContent 才放行）。
    dup_hash = payload.get("contentHash")
    if dup_hash and payload.get("allowDuplicateContent") is not True:
        dup = find_duplicate_content(q, oid, dup_hash, new_payload=payload)
        if dup:
            note = ("duplicate_content_blocked: same business content already carried by {} "
                    "(sha12={}); refusing a second feed — owner override required "
                    "(payload.allowDuplicateContent=true)").format(dup, str(dup_hash)[:12])
            safe_mark_result(q, oid, "revision_required", note, log)
            ev.record(oid, state="revision_required", receiptState="duplicate_content_blocked",
                      duplicateOf=dup, contentHash=dup_hash, note=note)
            log("  [{0}] {1}".format(oid, note))
            return "revision_required", note
    if cmd and not check:
        # 本地验收命令不是"推理轮询"：不能复用 cfg['timeout']（tick 默认只给 5s，
        # 会把真实测试套件误杀）。离线命令默认 600s，可被 payload.localTimeout 覆盖。
        local_timeout = float(payload.get("localTimeout") or 600.0)
        # 验收套件必须hermetic：托管链路的 daemon 环境变量会漏进套件子进程，
        # 让"隔离自测"实际连到在跑的 DEV/生产 daemon 而误红（C02 实测）。
        local_env = dict(os.environ)
        leaked = [k for k in ("BUTLER_DATA_DIR", "BUTLER_PIPE_PATH", "ZCODE_PLUGIN_DATA",
                               "BUTLER_DAEMON_STATE", "BUTLER_CONTROLLER_SESSION")
                  if local_env.pop(k, None) is not None]
        if leaked:
            log("  [{0}] scrubbed daemon env from acceptance subprocess: {1}".format(oid, ",".join(leaked)))
        try:
            r = subprocess.run(cmd if isinstance(cmd, list) else shlex.split(cmd),
                               capture_output=True, text=True, timeout=local_timeout, env=local_env)
        except Exception as exc:
            note = "offline localCommand error: {!r}".format(exc)[:400]
            safe_mark_result(q, oid, "failed", note, log)
            ev.record(oid, state="failed", note=note)
            return "failed", note
        if r.returncode != 0:
            note = "offline localCommand exit {} : {}".format(r.returncode, (r.stderr or r.stdout)[:200])[:400]
            safe_mark_result(q, oid, "failed", note, log)
            ev.record(oid, state="failed", note=note)
            return "failed", note
        detail = "offline localCommand OK (exit 0)"
        safe_mark_result(q, oid, "accepted", detail, log)
        # 回执必须能自证：命令、真实输出尾部、原验收 hash 与动作类别一并落盘。
        # 复核缺口#3（2026-10-29）：回执只证明本套件/命令真跑过，不冒充整张原验收——
        # payload.criterionCoverage（逐criterion covered/not_run）与 satisfiesDependencies
        # 原样透传进回执，下游按覆盖范围判定，不按 exit0 扩大。
        ev.record(oid, state="accepted", receiptState="offline_verified", note=detail,
                  command=cmd if isinstance(cmd, list) else [cmd],
                  outputTail=((r.stdout or "") + (r.stderr or ""))[-600:],
                  acceptanceHash=payload.get("acceptanceHash"),
                  actionClass=payload.get("actionClass"),
                  acceptanceSuite=payload.get("acceptanceSuite"),
                  criterionCoverage=payload.get("criterionCoverage"),
                  acceptanceState=payload.get("acceptanceState"),
                  satisfiesDependencies=payload.get("satisfiesDependencies"),
                  acceptanceVerifiedByThisReceipt=True,
                  acceptanceHashScope=("suite_check" if payload.get("acceptanceSuite") else "local_command"),
                  finishedAt=time.strftime("%Y-%m-%dT%H:%M:%S"))
        return "accepted", detail
    # check-based judgement
    ok, cdetail, sha = check_artifact(check, cfg.get("project"))
    ev.record(oid, artifactSha256=sha, check=cdetail)
    if not ok:
        note = "offline-check-failed: {}".format(cdetail)[:400]
        safe_mark_result(q, oid, "failed", note, log)
        ev.record(oid, state="failed", receiptState="offline_check_failed", note=note)
        return "failed", note
    detail = "offline-check-OK: {}".format(cdetail)
    safe_mark_result(q, oid, "accepted", detail, log)
    ev.record(oid, state="accepted", receiptState="offline_verified", eventId=None,
              note=detail + " (no adapter run — ack not applicable)",
              acceptanceHash=payload.get("acceptanceHash"),
              actionClass=payload.get("actionClass"),
              acceptanceSuite=payload.get("acceptanceSuite"),
              acceptanceVerifiedByThisReceipt=False,
              acceptanceHashScope="static_contains_only",
              finishedAt=time.strftime("%Y-%m-%dT%H:%M:%S"))
    log("  [{0}] offline accepted on real check: {1}".format(oid, cdetail))
    return "accepted", detail


def reconcile_running(q, ev, adapter, cfg, log):
    """启动时的崩溃续跑：扫描 state=running 的订单。"""
    outcomes = []
    controller = cfg.get("controller")
    for order in q.running_orders():
        oid = order["orderId"]
        # R04/C01: collect on the SAME route that dispatched this order (per-order
        # backend/project), never the fixed global default.
        oa = adapter.for_order(order) if hasattr(adapter, "for_order") else adapter
        order_project = getattr(oa, "project", None) or cfg.get("project")
        run_id = order.get("runId") or ev.entry(oid).get("runId")
        turn_id = order.get("turnId") or ev.entry(oid).get("turnId")
        entry = ev.entry(oid)
        if not run_id:
            # 先按 request 对账：manager 可能已接受，只是回执没落盘
            adopted, how, note = oa.reconcile_request(oid, None)
            if adopted:
                q.attach_run(oid, adopted, turn_id)
                ev.record(oid, runId=adopted, adoptedAt=time.strftime("%Y-%m-%dT%H:%M:%S"),
                          receiptState="adopted_on_resume")
                log("  [{0}] resume: adopted existing run {1} via {2}".format(oid, adopted, how))
                outcomes.append((oid, "adopted"))
                continue
            if entry.get("dispatchIntentAt"):
                # 复核点：有派发意图但没有 runId ⇒ 不能断定未发送。保留身份交对账。
                detail = "dispatch intent recorded but no runId (reconcile how={})".format(how)
                safe_mark_result(q, oid, "revision_required", detail, log)
                ev.record(oid, state="revision_required", receiptState="intent_without_runid",
                          note=detail, resumeAction="reconcile")
                log("  [{0}] resume: intent without runId -> revision_required ({1})".format(oid, note))
                outcomes.append((oid, "revision_required"))
                continue
            q.requeue(oid, "pre-dispatch crash, no intent record -> provably not submitted")
            ev.record(oid, resumeAction="requeued_no_intent", receiptState="never_submitted")
            log("  [{0}] resume: running without runId and without intent -> ready".format(oid))
            outcomes.append((oid, "requeued"))
            continue

        # (a) 有 runId —— 先问 adapter status，绝不盲重派
        status, detail = poll_status(oa, run_id, cfg["resume_timeout"], cfg["poll_interval"], log)
        log("  [{0}] resume: runId={1} status={2} ({3})".format(oid, run_id, status, detail))
        ev.record(oid, businessState=status, observationState="resume_polled")
        if status == ST_UNKNOWN or status == ST_TIMEOUT:
            note = "requires_reconcile: status {} after crash ({})".format(status, detail)
            safe_mark_result(q, oid, "revision_required", note[:400], log)
            ev.record(oid, state="revision_required", note=note, resumeAction="reconcile")
            outcomes.append((oid, "revision_required"))
            continue
        if status in TERMINAL_BAD:
            note = "resumed turn {}: {}".format(status, detail)
            safe_mark_result(q, oid, "failed", note[:400], log)
            ev.record(oid, state="failed", note=note, resumeAction="failed")
            cur = q.get_order(oid)
            if int(cur.get("attempts") or 0) >= cfg["max_attempts"]:
                outcomes.append((oid, "failed"))
            else:
                q.requeue(oid, note)
                outcomes.append((oid, "requeued"))
            continue

        if status != ST_FINISHED:
            # R04 tick（resume_timeout<=0）：健康在跑——保留 running 身份，下一次 tick 再收。
            # 不判失败、不重派、不降级 revision_required（观察未知≠业务失败）。
            outcomes.append((oid, "still_running"))
            continue

        if not turn_id:
            note = "resume: no turnId bound for run {} — strict ack impossible".format(run_id)
            safe_mark_result(q, oid, "revision_required", note, log)
            ev.record(oid, state="revision_required", observationState="turn_unbound",
                      note=note, resumeAction="reconcile")
            outcomes.append((oid, "revision_required"))
            continue

        # finished：完整结果 → 独立检查 → ack（不重派）
        ok_res, res_payload, rdetail = fetch_full_result(oa, run_id, turn_id, oid, controller)
        if not ok_res:
            note = "resume full-result-failed: {}".format(rdetail)
            safe_mark_result(q, oid, "failed", note[:400], log)
            ev.record(oid, state="failed", note=note, receiptState="result_unverified",
                      resumeAction="result-failed")
            outcomes.append((oid, "failed"))
            continue
        ev.record(oid, replyChars=len(res_payload.get("reply") or ""),
                  replySha256=res_payload.get("replySha256") or res_payload.get("replySha256Full"),
                  replyKind=res_payload.get("replyKind"), receiptState="result_verified")

        ok, cdetail, sha = check_artifact((order.get("payload") or {}).get("check"), order_project)
        ev.record(oid, artifactSha256=sha, check=cdetail)
        if not ok:
            note = "resume independent-check-failed: {}".format(cdetail)
            safe_mark_result(q, oid, "failed", note[:400], log)
            ev.record(oid, state="failed", note=note, resumeAction="check-failed")
            cur = q.get_order(oid)
            if int(cur.get("attempts") or 0) >= cfg["max_attempts"]:
                outcomes.append((oid, "failed"))
            else:
                q.requeue(oid, note)
                outcomes.append((oid, "requeued"))
            continue
        ok_ack, event_id, adetail = ack_turn_completed(
            oa, run_id, turn_id, log, expect_request_id=oid, expect_controller=controller)
        if not ok_ack:
            note = "resume ack-failed: {}".format(adetail)
            safe_mark_result(q, oid, "failed", note[:400], log)
            ev.record(oid, state="failed", note=note, receiptState="ack_unconfirmed",
                      resumeAction="ack-failed")
            outcomes.append((oid, "failed"))
            continue
        safe_mark_result(q, oid, "accepted", None, log)
        try:
            q.ack(oid)
        except ValueError:
            pass
        ev.record(
            oid, state="accepted", runId=run_id, turnId=turn_id,
            eventId=event_id, artifactSha256=sha,
            receiptState="acked_and_accepted", resumeAction="acked_without_redispatch",
            acceptanceHash=(order.get("payload") or {}).get("acceptanceHash"),
            actionClass=(order.get("payload") or {}).get("actionClass"),
            finishedAt=time.strftime("%Y-%m-%dT%H:%M:%S"),
        )
        log("  [{0}] resume: ACCEPTED without re-dispatch (eventId={1})".format(oid, event_id))
        outcomes.append((oid, "accepted"))
    return outcomes


def run_queue(args, adapter=None, log=print):
    mod = load_queue_module(args.queue_module)
    DispatchQueue = make_queue_class(mod)
    if adapter is None:
        cmd = shlex.split(args.adapter_cmd) if args.adapter_cmd else [DEFAULT_NODE_BIN, DEFAULT_ADAPTER_CMD]
        adapter = AdapterClient(cmd, args.project, args.controller, args.backend)

    cfg = {
        "project": args.project,
        "controller": args.controller,
        "max_attempts": args.max_attempts,
        "timeout": args.timeout,
        "resume_timeout": args.resume_timeout,
        "poll_interval": args.poll_interval,
        # v4 审计视图：evidence 目录 + python 解释器
        "evidence_dir": None,   # 在 ev 初始化后填入
        "python_bin": getattr(args, "python_bin", None),
    }

    lock = acquire_queue_lock(args.queue)
    if lock is None:
        # F-WAVE-03：定时器唤醒与人工启动可能同时打同一个队列。
        log("[queue] ANOTHER RUNNER HOLDS THIS QUEUE ({}); exiting without touching state".format(
            os.path.abspath(args.queue)))
        return {"queue": os.path.abspath(args.queue), "locked": True, "stats": {}, "resumed": [],
                "processed": [], "evidence": None}

    try:
        q = DispatchQueue(args.queue)
        ev = Evidence(args.evidence) if args.evidence else Evidence(
            os.path.join(os.path.dirname(os.path.abspath(args.queue)),
                         "evidence-{}.json".format(os.path.splitext(os.path.basename(args.queue))[0]))
        )
        ev.data.setdefault("queue", os.path.abspath(args.queue))
        ev.data.setdefault("project", args.project)
        ev.data.setdefault("controller", args.controller)
        ev.data.setdefault("backend", args.backend)
        ev.data["lastRunAt"] = time.strftime("%Y-%m-%dT%H:%M:%S")
        ev.data["runnerPid"] = os.getpid()
        ev.save()
        # evidence_dir = 证据文件所在目录，信封写到子目录 audits/
        cfg["evidence_dir"] = os.path.join(os.path.dirname(ev.path), "audits")

        log("[queue] file={} project={} controller={} backend={} pid={} stats={}".format(
            args.queue, args.project, args.controller, args.backend, os.getpid(),
            json.dumps(q.stats(), ensure_ascii=False)))

        resumed = reconcile_running(q, ev, adapter, cfg, log)
        if resumed:
            log("[queue] resume summary: {}".format(json.dumps(resumed, ensure_ascii=False)))

        deps = {}
        for o in q.all_orders():
            p = o.get("payload") or {}
            d = p.get("dependsOn") if isinstance(p, dict) else None
            deps[o["orderId"]] = [x for x in (d or []) if isinstance(x, str)]

        def ancestors_blocked(oid, seen=None):
            """本单的上游（含传递）里是否有已失败/待对账的单。"""
            seen = seen or set()
            bad = []
            for parent in deps.get(oid, []):
                if parent in seen:
                    continue
                seen.add(parent)
                st = (q.get_order(parent) or {}).get("state")
                if st in ("failed", "revision_required"):
                    bad.append(parent)
                bad.extend(ancestors_blocked(parent, seen))
            return bad

        processed, skipped = [], set()
        blocked = set()
        while args.max_orders is None or len(processed) < args.max_orders:
            order = q.next_ready()
            if order is None:
                break
            oid = order["orderId"]
            if oid in skipped or oid in blocked:
                break
            culprits = ancestors_blocked(oid) if args.fail_scope == "deps" else []
            if culprits:
                # 只阻塞依赖链，不冻结全队列（复核点：一单 revision_required 不该停掉无关订单）
                culprits = sorted(set(culprits))
                note = "blocked by upstream {}".format(",".join(culprits))
                q.park(oid, note)
                ev.record(oid, blockedBy=culprits, note=note, businessState="pending_dependency")
                blocked.add(oid)
                processed.append({"orderId": oid, "outcome": "blocked", "detail": note})
                log("[queue] order {} {} (independent orders keep running)".format(oid, note))
                continue
            # Step 5 honest classification (same rules as tick). Legacy orders (no
            # actionType) classify as "dispatch" so run_queue behavior is unchanged.
            kind, reason = classify_execution(order)
            if kind in ("backlog", "unenriched_model"):
                # leave ready; do not fake-accept, do not burn an attempt as "failed"
                ev.record(oid, executionClass=kind, note=reason,
                          businessState="ready_backlog" if kind == "backlog" else "awaiting_prompt")
                skipped.add(oid)
                processed.append({"orderId": oid, "outcome": kind, "detail": reason})
                log("[queue] order {} {} (left ready, not faked)".format(oid, kind))
                continue
            if kind == "offline_verify":
                order_cfg = dict(cfg)
                outcome, detail = run_offline_verify(q, ev, order, order_cfg, log)
                processed.append({"orderId": oid, "outcome": outcome, "detail": str(detail)[:200]})
                if outcome == "failed":
                    cur = q.get_order(oid)
                    if int(cur.get("attempts") or 0) >= cfg["max_attempts"]:
                        break
                    q.requeue(oid, detail)
                continue
            log("[queue] processing order {} (attempt {})".format(oid, q.get_order(oid).get("attempts")))
            try:
                # R04/C01: per-order backend/project routing (same manager).
                order_adapter = adapter.for_order(order) if hasattr(adapter, "for_order") else adapter
                order_cfg = dict(cfg)
                order_cfg["project"] = getattr(order_adapter, "project", cfg.get("project"))
                outcome, detail = run_order(q, ev, order_adapter, order, order_cfg, log)
            except Exception as exc:  # 未预期异常：留在 running，等下一次续跑
                log("[queue] order {} aborted by exception: {!r}".format(oid, exc))
                ev.record(oid, note="exception: {!r}".format(exc)[:400])
                raise
            # ---- 把 v4 审计视图字段挂进 processed 条目（展示用，不影响状态机） ----
            # run_order() 已在 accepted 分支调用了 _attach_audit_view（含 run_id/turn_id）；
            # 这里只读取证据里已有的值，不再覆盖——避免二次调用（无 run_id）把 ok 覆成 absent。
            # 对于非 accepted 出口（failed/revision_required），run_order 不调 _attach_audit_view，
            # 此时 ev_entry 里没有 auditViewStatus，保持 None 即可。
            ev_entry = ev.data.get("orders", {}).get(oid, {})
            entry = {
                "orderId": oid,
                "outcome": outcome,
                "detail": str(detail)[:200],
                "auditView": ev_entry.get("auditView"),
                "auditViewStatus": ev_entry.get("auditViewStatus"),
            }
            processed.append(entry)
            if outcome == "skipped":
                skipped.add(oid)
            if outcome in ("failed", "revision_required") and args.fail_scope == "run":
                log("[queue] fail-scope=run: aborting at {}".format(oid))
                break

        summary = {
            "queue": os.path.abspath(args.queue),
            "runnerPid": os.getpid(),
            "stats": q.stats(),
            "resumed": resumed,
            "processed": processed,
            "evidence": ev.path,
        }
        log("[queue] DONE stats={} processed={}".format(
            json.dumps(q.stats(), ensure_ascii=False), json.dumps(processed, ensure_ascii=False)))
        log("[queue] evidence: {}".format(ev.path))
        return summary
    finally:
        release_queue_lock(lock)


def _code_identity_snapshot():
    """C04/Step5 (2026-10-28): 读"本 runner 实际驱动的 daemon"的运行侧代码身份收据。

    磁盘 hash 相同不等于运行加载相同（用户铁律）。旧实现硬编码 ~/.zcode/butler（生产数据根），
    而 campaign runner 通过 BUTLER_DATA_DIR/BUTLER_PIPE_PATH 驱动 DEV daemon——两者错配会让
    checkpoint 谎报"生产身份"却调用 DEV。修法：收据路径优先取本进程实际使用的 BUTLER_DATA_DIR
    （由 campaign_tick 的 DEV_ENV 注入），未设时才回落生产默认，并把来源数据根一起记进快照，
    DEV/prod 归属永远显式可辨；读不到如实返回 None，绝不脑补。
    """
    env_root = os.environ.get("BUTLER_DATA_DIR")
    from_prod_default = False
    if env_root:
        state_path = os.path.join(env_root, "daemon-state.json")
    else:
        state_path = os.path.join(os.path.expanduser("~"), ".zcode", "butler", "daemon-state.json")
        from_prod_default = True
    try:
        with open(state_path, "r", encoding="utf-8") as f:
            st = json.load(f)
        ident = st.get("loadedCodeIdentity") or {}
        h = ident.get("hashes") or {}
        if not h:
            return None
        return {
            "daemonPid": st.get("pid"),
            "daemonStartedAt": st.get("startedAt"),
            "worker_daemon": h.get("worker_daemon.mjs"),
            "model_economics": h.get("model_economics.mjs"),
            "dataRootSource": ("env:BUTLER_DATA_DIR" if not from_prod_default else "fallback:prod-default"),
            "dataRoot": os.path.dirname(state_path),
        }
    except (OSError, ValueError):
        return None


def tick(args, adapter=None, log=print):
    """R04 (288h 2026-10-28): 非阻塞单步 tick——lease→对账收取→依赖泊车→派发→原子 checkpoint。

    与 run_queue 的区别：绝不在任何订单上轮询等待。派发绑定身份后立即返回（wait=False），
    外部执行继续；结果由后续 tick 的 reconcile_running 收取（resume_timeout=0 单发 status，
    在跑订单保留 running 身份，不判失败不重派）。主控/进程退出后下一个 tick 天然恢复——
    队列与证据就是 checkpoint，无额外会话状态。
    """
    mod = load_queue_module(args.queue_module)
    DispatchQueue = make_queue_class(mod)
    if adapter is None:
        cmd = shlex.split(args.adapter_cmd) if args.adapter_cmd else [DEFAULT_NODE_BIN, DEFAULT_ADAPTER_CMD]
        adapter = AdapterClient(cmd, args.project, args.controller, args.backend)

    cfg = {
        "project": args.project,
        "controller": args.controller,
        "max_attempts": args.max_attempts,
        "timeout": args.timeout,
        # tick：单发 status（poll_status timeout<=0 不等待），在跑订单如实保留 running
        "resume_timeout": 0.0,
        "poll_interval": args.poll_interval,
        "evidence_dir": None,
        "python_bin": getattr(args, "python_bin", None),
    }

    lock = acquire_queue_lock(args.queue)
    if lock is None:
        # 与 run_queue 同一防撞锁（F-WAVE-03）：定时器 tick 与人工 run_queue 互斥
        log("[tick] another runner holds this queue; exiting without touching state")
        return {"queue": os.path.abspath(args.queue), "locked": True, "stats": q_stats_safe(args.queue, DispatchQueue)}

    try:
        q = DispatchQueue(args.queue)
        ev = Evidence(args.evidence) if args.evidence else Evidence(
            os.path.join(os.path.dirname(os.path.abspath(args.queue)),
                         "evidence-{}.json".format(os.path.splitext(os.path.basename(args.queue))[0])))
        ev.data.setdefault("queue", os.path.abspath(args.queue))
        ev.data.setdefault("project", args.project)
        ev.data.setdefault("controller", args.controller)
        ev.data["lastTickAt"] = time.strftime("%Y-%m-%dT%H:%M:%S")
        ev.data["tickPid"] = os.getpid()
        ev.save()
        cfg["evidence_dir"] = os.path.join(os.path.dirname(ev.path), "audits")

        log("[tick] file={} project={} controller={} pid={} stats={}".format(
            args.queue, args.project, args.controller, os.getpid(),
            json.dumps(q.stats(), ensure_ascii=False)))

        # 1) 对账/收取：running 订单单发 status；finished→结果→独立检查→精确 ack
        resumed = reconcile_running(q, ev, adapter, cfg, log)
        if resumed:
            log("[tick] reconcile summary: {}".format(json.dumps(resumed, ensure_ascii=False)))

        # 2) 依赖泊车 + 派发（不等待）
        deps = {o["orderId"]: [x for x in ((o.get("payload") or {}).get("dependsOn") or [])
                                         if isinstance(x, str)]
                for o in q.all_orders()}

        def ancestors_blocked(oid, seen=None):
            seen = seen or set()
            bad = []
            for parent in deps.get(oid, []):
                if parent in seen:
                    continue
                seen.add(parent)
                st = (q.get_order(parent) or {}).get("state")
                if st in ("failed", "revision_required"):
                    bad.append(parent)
                bad.extend(ancestors_blocked(parent, seen))
            return bad

        dispatched, parked, backlog, unenriched = [], [], [], []
        deferred = set()  # ready orders we deliberately do not consume this tick
        while args.max_orders is None or len(dispatched) < args.max_orders:
            order = q.next_ready()
            if order is None:
                break
            oid = order["orderId"]
            if oid in deferred:
                break  # full lap reached: only non-consumable ready orders remain
            culprits = sorted(set(ancestors_blocked(oid))) if getattr(args, "fail_scope", "deps") == "deps" else []
            if culprits:
                note = "blocked by upstream {}".format(",".join(culprits))
                q.park(oid, note)
                ev.record(oid, blockedBy=culprits, note=note, businessState="pending_dependency")
                parked.append({"orderId": oid, "note": note})
                log("[tick] order {} {} (independent orders keep running)".format(oid, note))
                continue
            kind, reason = classify_execution(order)
            if kind == "backlog":
                # honest enumeration: visible ready work the tick cannot perform.
                # Leave it ready (no fake accept, no mislabel failed).
                ev.record(oid, executionClass="backlog", note=reason, businessState="ready_backlog")
                backlog.append({"orderId": oid, "actionType": (order.get("payload") or {}).get("actionType"), "reason": reason})
                deferred.add(oid)
                log("[tick] order {} backlog (not faked): {}".format(oid, reason))
                continue
            if kind == "unenriched_model":
                ev.record(oid, executionClass="unenriched_model", note=reason, businessState="awaiting_prompt")
                unenriched.append({"orderId": oid, "actionType": (order.get("payload") or {}).get("actionType"), "reason": reason})
                deferred.add(oid)
                log("[tick] order {} model order awaiting promptFile: {}".format(oid, reason))
                continue
            try:
                if kind == "offline_verify":
                    order_cfg = dict(cfg)
                    outcome, detail = run_offline_verify(q, ev, order, order_cfg, log)
                    dispatched.append({"orderId": oid, "outcome": outcome, "detail": str(detail)[:120], "kind": "offline_verify"})
                    if outcome != "accepted":
                        break  # an offline failure is a real, honest terminal this tick
                    continue
                # kind == "dispatch": legacy order OR enriched model order.
                # R04/C01: per-order backend/project — never the fixed global default.
                order_adapter = adapter.for_order(order) if hasattr(adapter, "for_order") else adapter
                order_cfg = dict(cfg)
                order_cfg["project"] = getattr(order_adapter, "project", cfg.get("project"))
                outcome, detail = run_order(q, ev, order_adapter, order, order_cfg, log, wait=False)
            except Exception as exc:
                # 单单异常：留在现场，checkpoint 后返回——绝不升级为整 campaign 退出
                log("[tick] order {} aborted by exception: {!r}".format(oid, exc))
                ev.record(oid, note="tick exception: {!r}".format(exc)[:400])
                break
            dispatched.append({"orderId": oid, "outcome": outcome, "detail": str(detail)[:120], "kind": kind})
            if outcome != "dispatched":
                break  # 保守：一次 tick 推进到第一个非派发出口即止


        # 3) campaign checkpoint（原子替换写，与队列同目录）
        inflight = [{"orderId": o["orderId"], "runId": o.get("runId"), "turnId": o.get("turnId"),
                     "state": o.get("state")} for o in q.running_orders()]
        # C04（288h 2026-10-28）：接替上下文持久化——新 controller 只靠 checkpoint 即可
        # 接续，不依赖人工粘全文。codeIdentity 读生产 daemon 运行收据（读不到如实 null）；
        # costValidUntil 故意不缓存：权益有效期由闸口每次派发实时裁决（A01 TTL 教训——
        # 缓存的过期证据会被复用放行），checkpoint 只记这个设计决定本身。
        code_identity = _code_identity_snapshot()
        if parked:
            next_action = "resolve_parked_or_wait_capacity"
        elif dispatched:
            next_action = "supervise_inflight_then_collect"
        elif inflight:
            next_action = "supervise_collect_on_next_wake"
        elif unenriched:
            next_action = "attach_promptFile_to_model_orders"
        elif backlog:
            next_action = "execute_or_enrich_offline_backlog"
        elif (q.stats() or {}).get("ready"):
            next_action = "dispatch_ready_orders"
        else:
            next_action = "wait_capacity_or_new_orders"
        ckpt = {
            "schema": "butler-campaign/1",
            "lastSuccessfulTickAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "tickPid": os.getpid(),
            "controllerSessionId": args.controller,
            "queuePath": os.path.abspath(args.queue),
            "evidencePath": getattr(args, "evidence", None),
            "nextAction": next_action,
            "codeIdentity": code_identity,
            "costValidUntil": None,
            "costValidUntilNote": ("not cached by design: cost gate re-validates "
                                   "entitlement freshness at every dispatch (A01 TTL)"),
            "inflight": inflight,
            "resumed": resumed,
            "dispatched": dispatched,
            "parked": parked,
            # Step 5 honest enumeration: real typed backlog that the tick did NOT fake-complete
            "backlog": backlog,
            "unenrichedModel": unenriched,
            "stats": q.stats(),
        }
        ckpt_path = os.path.join(os.path.dirname(os.path.abspath(args.queue)), "campaign-checkpoint.json")
        tmp = ckpt_path + ".tmp-{}".format(os.getpid())
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(ckpt, f, ensure_ascii=False, indent=1)
        os.replace(tmp, ckpt_path)
        log("[tick] checkpoint -> {} (inflight={} backlog={} unenriched={})".format(
            ckpt_path, len(inflight), len(backlog), len(unenriched)))
        return {"queue": os.path.abspath(args.queue), "stats": q.stats(), "resumed": resumed,
                "dispatched": dispatched, "parked": parked, "inflight": inflight,
                "backlog": backlog, "unenrichedModel": unenriched,
                "checkpoint": ckpt_path}
    finally:
        release_queue_lock(lock)


def q_stats_safe(queue_path, DispatchQueue):
    try:
        return DispatchQueue(queue_path).stats()
    except Exception:
        return {}


def build_parser():
    p = argparse.ArgumentParser(description="持久队列 → 真实派工循环（崩溃可续 / 不重复提交）")
    p.add_argument("--queue", required=True, help="队列 JSON 路径")
    p.add_argument("--project", required=True, help="绝对 cwd（产品项目目录）")
    p.add_argument("--controller", default="butler-v2-main", help="controller session id")
    p.add_argument("--backend", default="workbuddy", help="adapter --type（workbuddy/zcode/antigravity/qoder）")
    p.add_argument("--max-orders", type=int, default=None, help="本轮最多处理多少单")
    p.add_argument("--max-attempts", type=int, default=3, help="单订单最大尝试次数")
    p.add_argument("--timeout", type=float, default=900.0, help="单 run 轮询超时（秒）")
    p.add_argument("--resume-timeout", type=float, default=60.0, help="续跑时 status 查询轮询超时（秒）")
    p.add_argument("--poll-interval", type=float, default=5.0, help="轮询间隔（秒）")
    p.add_argument("--evidence", default=None, help="证据 JSON 路径（默认与队列同目录）")
    p.add_argument("--queue-module", default=DEFAULT_QUEUE_MODULE, help="persistent_queue.py 路径")
    p.add_argument("--adapter-cmd", default=None, help="adapter 命令，如 \"node D:/.../worker_adapter.mjs\"")
    p.add_argument("--tick", action="store_true", help="R04 非阻塞单步：对账收取+派发+checkpoint，绝不在订单上等待")
    p.add_argument("--fail-scope", choices=("deps", "run"), default="deps",
                   help="失败时只阻塞依赖链(deps) 还是中止整轮(run)")
    p.add_argument("--stop-on-fail", dest="fail_scope", action="store_const", const="run",
                   help="deprecated 别名，等价 --fail-scope run")
    p.add_argument("--no-stop-on-fail", dest="fail_scope", action="store_const", const="deps",
                   help="deprecated 别名，等价 --fail-scope deps")
    p.add_argument("--json", action="store_true", help="最后打印 summary JSON")
    return p


def main(argv=None, adapter=None):
    args = build_parser().parse_args(argv)
    if getattr(args, "tick", False):
        summary = tick(args, adapter=adapter)
        if args.json:
            print(json.dumps(summary, ensure_ascii=False, indent=2))
        # tick 是节律步进：退出码恒 0（失败在队列/证据里如实记账，不靠进程退出码）
        return 0
    summary = run_queue(args, adapter=adapter)
    if args.json:
        print(json.dumps(summary, ensure_ascii=False, indent=2))
    bad = summary["stats"].get("failed", 0) + summary["stats"].get("revision_required", 0)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
