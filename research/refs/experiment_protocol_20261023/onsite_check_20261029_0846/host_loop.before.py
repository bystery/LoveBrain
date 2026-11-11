# -*- coding: utf-8 -*-
"""host_loop.py — 本机常驻托管循环（G08 实际托管的最小实现）。

每 BUTLER_HOST_INTERVAL 秒调用一次 campaign_tick.py；tick 负责对账收取、按闸口
派发与写 checkpoint。本循环只做托管：写 heartbeat.json + host.log，出错如实记录，
不伪造进展。停止：在 live-campaign/host/ 下创建 STOP 文件。

Run: start /b python host_loop.py  (或 PowerShell Start-Process 隐藏启动)
"""
import json
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
CAMPAIGN = os.path.join(HERE, "live-campaign")
HOST = os.path.join(CAMPAIGN, "host")
TICK = os.path.join(HERE, "campaign_tick.py")
HEARTBEAT = os.path.join(HOST, "heartbeat.json")
LOG = os.path.join(HOST, "host.log")
STOP = os.path.join(HOST, "STOP")
INTERVAL = int(os.environ.get("BUTLER_HOST_INTERVAL", "15"))

ENV = dict(os.environ)
ENV["PYTHONIOENCODING"] = "utf-8"
ENV["PYTHONUTF8"] = "1"


def write_atomic(path, obj):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    os.replace(tmp, path)


def main():
    os.makedirs(HOST, exist_ok=True)
    if os.path.exists(STOP):
        os.remove(STOP)
    state = {"pid": os.getpid(), "startedAt": time.strftime("%Y-%m-%dT%H:%M:%S+08:00"),
             "intervalSeconds": INTERVAL, "ticks": 0, "errors": 0,
             "lastTickAt": None, "lastSummary": None, "lastError": None}
    write_atomic(HEARTBEAT, state)
    with open(LOG, "a", encoding="utf-8") as log:
        log.write("[host] start pid=%d interval=%ds %s\n" % (os.getpid(), INTERVAL, state["startedAt"]))
        log.flush()
        while not os.path.exists(STOP):
            started = time.time()
            try:
                r = subprocess.run([sys.executable, TICK], capture_output=True, timeout=180, env=ENV)
                out = (r.stdout or b"").decode("utf-8", "replace").strip().splitlines()
                last = out[-1] if out else ""
                summary = None
                if last.startswith("{"):
                    try:
                        e = json.loads(last)
                        summary = {"consumed": e.get("consumed"), "resumed": e.get("resumed"),
                                   "noChange": e.get("noChange"), "backlog": e.get("backlog"),
                                   "unenrichedModel": e.get("unenrichedModel"),
                                   "poolAction": (e.get("poolPlan") or {}).get("action"),
                                   "stats": e.get("stats")}
                    except ValueError:
                        summary = {"raw": last[:300]}
                state["ticks"] += 1
                state["lastTickAt"] = time.strftime("%Y-%m-%dT%H:%M:%S+08:00")
                state["lastSummary"] = summary
                state["lastError"] = None if r.returncode == 0 else "tick exit %d" % r.returncode
                if r.returncode != 0:
                    state["errors"] += 1
                write_atomic(HEARTBEAT, state)
                log.write("[host] tick %d rc=%d %s\n" % (state["ticks"], r.returncode,
                                                          json.dumps(summary, ensure_ascii=False)[:500]))
                log.flush()
            except Exception as exc:  # keep hosting; record failure honestly
                state["ticks"] += 1
                state["errors"] += 1
                state["lastError"] = repr(exc)
                state["lastTickAt"] = time.strftime("%Y-%m-%dT%H:%M:%S+08:00")
                write_atomic(HEARTBEAT, state)
                log.write("[host] tick %d ERROR %r\n" % (state["ticks"], exc))
                log.flush()
            elapsed = time.time() - started
            time.sleep(max(1.0, INTERVAL - elapsed))
        log.write("[host] stop by STOP file %s\n" % time.strftime("%Y-%m-%dT%H:%M:%S+08:00"))
        log.flush()
    state["stoppedAt"] = time.strftime("%Y-%m-%dT%H:%M:%S+08:00")
    write_atomic(HEARTBEAT, state)
    return 0


if __name__ == "__main__":
    sys.exit(main())
