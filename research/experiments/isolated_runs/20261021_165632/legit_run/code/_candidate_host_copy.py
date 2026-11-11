# -*- coding: utf-8 -*-
"""candidate_host.py — 受限候选宿主（独立进程，被控制器 spawn）。

纪律：
  - 只读 staged 代码（code/）、允许媒体（media/）；只写自身 state/；
  - 无 arena/、refs/、logs/、benchmark/ 可读性（ACL Deny 由控制器施加）；
  - 不持有 lb_scorer；评分/账本/gold 全在可信侧；
  - 模型调用只能经 IPC 请求控制器代理（本轮一次性收集 _proxy_calls 回传）；
  - stdout 仅协议帧；诊断走 stderr。
"""
import argparse
import base64
import importlib.util
import json
import os
import sys
import traceback

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

MAX_FRAME = 1_000_000


def load_module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class Host:
    def __init__(self, run_dir):
        self.run_dir = run_dir
        self.state = {}
        self.mech = load_module(os.path.join(run_dir, "code",
                                             "synthetic_candidate.py"),
                                 "iso_candidate")

    # ---------- 受限文件访问 ----------
    def read_within(self, rel):
        """只允许 code/ 与 media/ 只读访问；其余一律拒绝（架构级白名单）。"""
        base = os.path.abspath(self.run_dir)
        p = os.path.abspath(os.path.join(base, rel))
        allowed = (os.path.join(base, "code") + os.sep,
                   os.path.join(base, "media") + os.sep)
        if not (p.startswith(allowed[0]) or p.startswith(allowed[1])):
            raise PermissionError("ARCH_DENY: %s 不在白名单" % rel)
        return open(p, "rb").read()

    def write_state(self, key, value):
        p = os.path.join(self.run_dir, "state", "state.json")
        self.state[key] = value
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump(self.state, open(p, "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
        return {"written": key}

    def snapshot(self):
        return base64.b64encode(json.dumps(
            self.state, ensure_ascii=False, sort_keys=True).encode()).decode()

    def restore(self, b64):
        self.state = json.loads(base64.b64decode(b64).decode())
        return {"restored_keys": sorted(self.state)}

    # ---------- 代理请求（本轮一次性收集） ----------
    def proxy_call(self, prompt):
        return {"attempt_id": "host_%d" % next(self._aid), "prompt": prompt}

    _aid = iter(range(1, 10 ** 6))

    # ---------- 主循环 ----------
    def handle(self, msg):
        op = msg.get("op")
        if op == "turn":
            payload = msg.get("payload", {})
            out = self.mech.run_turn(payload, self, self.state)
            out.setdefault("state_b64", self.snapshot())
            return out
        if op == "ping":
            return {"pong": True}
        return {"error": "UNKNOWN_OP", "op": op}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-dir", required=True)
    args = ap.parse_args()
    try:
        host = Host(args.run_dir)
        print(json.dumps({"hello": "candidate_host"}, ensure_ascii=False),
              flush=True)
    except Exception as e:
        print(json.dumps({"error": "INIT_FAIL", "detail": str(e)[:300]}),
              flush=True)
        return 1
    for line in sys.stdin:
        if len(line) > MAX_FRAME:
            print(json.dumps({"error": "OVERSIZED_FRAME"}), flush=True)
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError as e:
            print(json.dumps({"error": "BAD_JSON", "detail": str(e)[:150]}),
                  flush=True)
            continue
        try:
            resp = host.handle(msg)
        except PermissionError as e:
            resp = {"error": "PERMISSION_DENIED", "detail": str(e)[:300]}
        except Exception as e:
            resp = {"error": "HOST_EXCEPTION",
                    "detail": "%s: %s" % (type(e).__name__, str(e)[:300]),
                    "trace_tail": traceback.format_exc()[-300:]}
        out = json.dumps(resp, ensure_ascii=False)
        print(out[:MAX_FRAME], flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
