# -*- coding: utf-8 -*-
"""host_runtime.py — 隔离宿主运行时（被 staged 进隔离运行目录，由宿主进程执行）。

职责（对接 ARENA_V1_REPAIR_HANDOFF 工作组 1）：
  - 按【候选目录参数】加载真实机制（mechanism/mechanism.py，v1/v2 契约判定与
    arena/lb_runner.load_mechanism 一致）——不硬编码任何合成候选；
  - 驱动真实 init / run_turn / snapshot / restore 生命周期；
  - IpcGateway：lb_gateway.ModelGateway 的同签名替换，generate() 以同步
    请求/响应帧向控制器代理发模型请求，模型原始结果在 run_turn 返回【之前】
    交给候选（多阶段生成的真实链路，不是先回常量再把日志附上）。

纪律：
  - 本文件不 import arena/lb_scorer（评分与 gold 留在可信侧）；
  - 只读 staged 树（candidates/…、arena/协议依赖）；只写自身 state/sandbox；
  - stdout 仅协议帧；诊断走 stderr。
"""
import base64
import importlib.util
import json
import os
import sys
import queue
import threading


class IpcGateway:
    """lb_gateway.ModelGateway 同签名替换：generate → IPC 同步请求。

    模型结果（含 usage/finish/raw 文本）由控制器真实适配器产生后原样回传，
    候选在 run_turn 内同步拿到——这是多阶段推理的真实路径。
    directive（测试替身台本）永远只在控制器侧合并，不跨 IPC（候选不可见）。"""

    def __init__(self, send_frame, wait_result):
        self._send = send_frame
        self._wait = wait_result
        self.calls = []
        self.directive = {}          # 占位：宿主侧恒空（真实台本在可信侧）
        self.run_id = ""
        self.case_id = ""
        self.event_log_errors = []
        self._seq = 0

    def set_run_context(self, run_id, case_id):
        self.run_id, self.case_id = run_id, case_id

    @property
    def adapter_name(self):
        return "ipc_proxy"

    def generate(self, system, user, images=None, model=None, temperature=None,
                 timeout_s=120, tag="main"):
        self._seq += 1
        rid = "g%d" % self._seq
        self._send({"op": "gateway_generate", "req_id": rid,
                    "req": {"system": system, "user": user,
                            "images": list(images or []),
                            "model": model, "temperature": temperature,
                            "timeout_s": timeout_s, "tag": tag}})
        res = self._wait(rid)      # 阻塞直到控制器回包（同步）
        self.calls.append({"tag": tag, "system": system, "user": user,
                           "images": list(images or []),
                           "temperature": (0.7 if temperature is None
                                           else temperature),
                           "ok": bool(res.get("ok")),
                           "error": res.get("error"),
                           "reply_chars": len((res.get("body") or {})
                                              .get("text", "")
                                              if res.get("ok") else "")})
        if not res.get("ok"):
            raise RuntimeError("gateway generate failed: %s"
                               % str(res.get("error"))[:300])
        body = res.get("body") or {}
        return {"text": body.get("text", ""),
                "finish": body.get("finish", ""),
                "usage": body.get("usage"),
                "resp_model": body.get("resp_model"),
                "resp_id": body.get("resp_id")}

    def turn_usage(self):
        if not self.calls:
            return {"prompt": 0, "completion": 0, "cache_hit": None,
                    "cache_miss": None, "source": "none", "n_calls": 0,
                    "has_unknown_usage": False}
        return {"n_calls": len(self.calls),
                "source": "ipc_proxy_controller_side_ledger"}


def load_module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def load_mechanism(staged_root, candidate_name):
    """与 arena/lb_runner.load_mechanism 同契约：mechanism/mechanism.py，
    v2（init+run_turn）/ v1（init_state+build_prompts+update_state）。"""
    cand_dir = os.path.join(staged_root, "candidates", candidate_name)
    mech_path = os.path.join(cand_dir, "mechanism", "mechanism.py")
    if not os.path.isfile(mech_path):
        raise RuntimeError("候选机制不存在: %s" % mech_path)
    mod_name = "iso_mech_%s_%d" % (candidate_name, id(cand_dir))
    mod = load_module(mech_path, mod_name)
    if callable(getattr(mod, "run_turn", None)) and callable(getattr(mod, "init", None)):
        return mod, "candidate:%s" % os.path.relpath(
            mech_path, cand_dir).replace("\\", "/"), "v2"
    for fn in ("init_state", "build_prompts", "update_state"):
        if not callable(getattr(mod, fn, None)):
            raise RuntimeError("候选 mechanism 缺少接口函数 %s（v1/v2 契约）" % fn)
    return mod, "candidate:%s" % os.path.relpath(
        mech_path, cand_dir).replace("\\", "/"), "v1"


class HostRuntime:
    """宿主运行时：真实候选生命周期驱动。"""

    def __init__(self, staged_root, candidate_name):
        self.root = os.path.abspath(staged_root)
        self.candidate_name = candidate_name
        self.mech, self.origin, self.version = load_mechanism(
            self.root, candidate_name)
        self.handle = None
        self.sandbox = os.path.join(self.root, "sandbox")
        os.makedirs(self.sandbox, exist_ok=True)

    def attach_gateway(self, gw):
        self.gw = gw

    # ---------- 生命周期 ----------
    def op_init(self, msg):
        assets_root = os.path.join(self.root, "candidates",
                                   self.candidate_name, "assets")
        ep_meta = msg.get("ep_meta") or {}
        if self.version == "v2":
            self.handle = self.mech.init(assets_root, ep_meta, self.gw,
                                         self.sandbox)
            return {"ok": True, "version": "v2", "mechanism": self.origin}
        self.handle = self.mech.init_state(assets_root, ep_meta)
        return {"ok": True, "version": "v1", "mechanism": self.origin}

    def op_turn(self, msg):
        if self.handle is None:
            raise RuntimeError("turn before init")
        ctx = msg["ctx"]
        if self.version == "v2":
            out = self.mech.run_turn(self.handle, ctx, self.gw)
            reply = out["reply"]
            notes = out.get("notes", "")
        else:
            assets_root = os.path.join(self.root, "candidates",
                                       self.candidate_name, "assets")
            assembled = self.mech.build_prompts(assets_root,
                                                ctx.get("input") or {},
                                                self.handle)
            images = [m["image"] for m in (ctx.get("input") or {})
                      .get("messages", [])
                      if isinstance(m, dict) and m.get("image")]
            gen = self.gw.generate(assembled["system"], assembled["user"],
                                   images=images or None,
                                   model=ctx.get("model"),
                                   temperature=ctx.get("temperature"),
                                   timeout_s=ctx.get("timeout_s", 120),
                                   tag="main")
            reply = gen["text"]
            notes = assembled.get("notes", "")
            parsed_err = None
            try:
                parsed = self._safe_extract(reply)
                self.mech.update_state(self.handle, ctx.get("input") or {},
                                       parsed, reply)
            except Exception as e:
                parsed_err = "%s: %s" % (type(e).__name__, e)
            out = {"reply": reply, "notes": notes}
            if parsed_err:
                out["mech_error"] = parsed_err
        # 白名单回传：候选自报的 score/gold/id_map 不在清单内 → 可信侧不采信
        payload = {"reply_raw": reply, "notes": notes}
        if isinstance(out, dict):
            if out.get("mech_error"):
                payload["mech_error"] = out["mech_error"]
        return payload

    @staticmethod
    def _safe_extract(text):
        """v1 update_state 的输入解析（与 lb_scorer.extract_json_block 同形态，
        宿主侧仅用于机制状态更新，不用于评分）。"""
        import re
        m = re.search(r"\{.*\}", text, re.S)
        if not m:
            raise ValueError("no JSON block in reply")
        return json.loads(m.group(0))

    def op_snapshot(self, msg):
        if self.version == "v2" and callable(getattr(self.mech, "snapshot", None)):
            snap = self.mech.snapshot(self.handle)
            if not isinstance(snap, bytes):
                snap = json.dumps(snap, ensure_ascii=False, default=str).encode("utf-8")
        else:
            snap = json.dumps(self.handle, ensure_ascii=False,
                              default=str).encode("utf-8")
        return {"ok": True, "snapshot_b64": base64.b64encode(snap).decode()}

    def op_restore(self, msg):
        snap = base64.b64decode(msg["snapshot_b64"])
        if self.version == "v2" and callable(getattr(self.mech, "restore", None)):
            self.handle = self.mech.restore(self.sandbox, snap)
        else:
            self.handle = json.loads(snap.decode("utf-8"))
        return {"ok": True, "restored": True}


class HostServer:
    """帧循环（避免死锁的关键）：
      - 主线程：读 stdin，gateway_generate_result 即时 resolve pending；
        命令帧投递给工作线程队列；
      - 工作线程：顺序执行命令（init/turn/snapshot/restore）；执行中发起的
        gateway_generate 经 _send 写帧并阻塞等待主线程 resolve。
    单工作线程保证命令顺序与回包顺序。"""

    def __init__(self, staged_root, candidate_name):
        self.rt = HostRuntime(staged_root, candidate_name)
        self._pending = {}
        self._lock = threading.Lock()
        self._cmd_q = queue.Queue()
        self._wlock = threading.Lock()

    def _send(self, obj):
        with self._wlock:
            sys.stdout.write(json.dumps(obj, ensure_ascii=True) + "\n")
            sys.stdout.flush()

    def _wait_result(self, req_id, timeout=600):
        # 竞态修复（交接文档 §4.3）：保留本地 entry 引用，wait 成功后不再回字典 pop。
        # 旧版在 ev.set() 后执行 self._pending.pop(req_id)，而 _resolve_result 已先 pop 走该条目 → KeyError。
        # _resolve_result 语义不变（pop + 写 res + set ev），本函数通过共享 entry 对象读取结果。
        ev = threading.Event()
        entry = {"ev": ev, "res": None}
        with self._lock:
            self._pending[req_id] = entry
        if not ev.wait(timeout):
            with self._lock:
                if self._pending.get(req_id) is entry:
                    del self._pending[req_id]
            raise RuntimeError("gateway result timeout: %s" % req_id)
        return entry["res"]

    def _resolve_result(self, msg):
        with self._lock:
            p = self._pending.pop(msg.get("req_id"), None)
        if p:
            p["res"] = msg
            p["ev"].set()

    def _worker(self):
        while True:
            msg = self._cmd_q.get()
            if msg is None:
                return
            try:
                resp = self.handle_cmd(msg)
            except Exception as e:
                import traceback
                resp = {"error": "HOST_EXCEPTION",
                        "detail": "%s: %s" % (type(e).__name__, str(e)[:400]),
                        "trace_tail": traceback.format_exc()[-500:]}
            self._send(resp)

    def handle_cmd(self, msg):
        op = msg.get("op")
        if op == "ping":
            return {"pong": True}
        if op == "init":
            return self.rt.op_init(msg)
        if op == "turn":
            return self.rt.op_turn(msg)
        if op == "snapshot":
            return self.rt.op_snapshot(msg)
        if op == "restore":
            return self.rt.op_restore(msg)
        return {"error": "UNKNOWN_OP", "op": op}

    def serve(self):
        gw = IpcGateway(self._send, self._wait_result)
        self.rt.attach_gateway(gw)
        worker = threading.Thread(target=self._worker, daemon=True)
        worker.start()
        self._send({"hello": "candidate_host",
                    "candidate": self.rt.candidate_name,
                    "mechanism": self.rt.origin,
                    "lifecycle": self.rt.version})
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue
            if len(line) > 16_000_000:
                self._send({"error": "OVERSIZED_FRAME", "len": len(line)})
                continue
            try:
                msg = json.loads(line)
            except json.JSONDecodeError as e:
                self._send({"error": "BAD_JSON", "detail": str(e)[:150],
                            "bad_head": line[:300]})
                continue
            if msg.get("op") == "gateway_generate_result":
                self._resolve_result(msg)
                continue
            self._cmd_q.put(msg)
        self._cmd_q.put(None)
