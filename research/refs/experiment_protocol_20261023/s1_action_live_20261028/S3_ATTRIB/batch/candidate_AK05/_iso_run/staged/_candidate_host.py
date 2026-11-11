# -*- coding: utf-8 -*-
"""_candidate_host.py — 隔离宿主进程入口（被控制器 spawn，staged 运行）。

用法：python -I -B _candidate_host.py --staged-root <dir> --candidate <name>
候选按参数加载（无硬编码）；模型调用经 IpcGateway 同步帧到控制器代理。
"""
import argparse
import json
import os
import sys

sys.stdin.reconfigure(encoding="utf-8", errors="replace")
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# staged 根 = repo 镜像：candidates/…、arena/协议依赖 都在其下
# host_runtime 本身由控制器拷入 staged 根，故 sys.path 首位即 staged 根
_HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--staged-root", required=True)
    ap.add_argument("--candidate", required=True)
    args = ap.parse_args()
    sys.path.insert(0, _HERE)
    try:
        from host_runtime import HostServer
        srv = HostServer(args.staged_root, args.candidate)
    except Exception as e:
        import traceback
        print(json.dumps({"hello": "candidate_host", "error": "INIT_FAIL",
                          "detail": "%s: %s" % (type(e).__name__, str(e)[:400]),
                          "trace_tail": traceback.format_exc()[-500:]},
                         ensure_ascii=False), flush=True)
        return 1
    srv.serve()
    return 0


if __name__ == "__main__":
    sys.exit(main())
