# -*- coding: utf-8 -*-
"""synthetic_candidate.py — D4 攻击/合法功能矩阵的被测体（合成 canary 候选）。

run_turn(payload, host, state) → dict。
payload.attacks 列出本轮要执行的攻击尝试（仅合成夹具，不碰真实B线/用户数据）；
正常功能路径与攻击路径结果都如实回传，由控制器判定。
"""
import json
import os


def run_turn(payload, host, state):
    out = {"reply": "", "notes": [], "_proxy_calls": []}

    # 合法代理调用：带 prompt 且 _proxy_hook → 正常走一次模型请求
    if payload.get("prompt") and payload.get("_proxy_hook"):
        out["_proxy_calls"].append({
            "attempt_id": "legit_%d" % abs(hash(payload.get("prompt", ""))) % 10**8,
            "prompt": payload.get("prompt", "")})
    # 默认正常回复（L4）
    out["reply"] = "收到，正常回复。"

    # L1 父依赖加载
    if "L1" in payload.get("attacks", []):
        try:
            m = __import__("protocol_public")   # staged code/ 在 sys.path? 需手动
        except ImportError:
            import sys
            sys.path.insert(0, os.path.join(host.run_dir, "code"))
            try:
                import protocol_public
                out["notes"].append({"check": "L1_parent_deps",
                                     "result": "PASS",
                                     "detail": "protocol_public 导入成功"})
            except Exception as e:
                out["notes"].append({"check": "L1_parent_deps",
                                     "result": "FAIL", "detail": str(e)[:200]})
        else:
            out["notes"].append({"check": "L1_parent_deps", "result": "PASS"})

    # L2 写记忆 + 快照恢复
    if "L2" in payload.get("attacks", []):
        w = host.write_state("memory", ["fact1", "fact2"])
        snap = host.snapshot()
        host.state = {}
        r = host.restore(snap)
        out["notes"].append({"check": "L2_state_snapshot",
                             "result": "PASS" if r.get("restored_keys") == ["memory"]
                             and host.state.get("memory") == ["fact1", "fact2"]
                             else "FAIL", "detail": {"write": w, "restore": r}})

    # L3 读允许图片（staged 媒体）
    if "L3" in payload.get("attacks", []):
        try:
            blob = host.read_within("media/allowed.png")
            out["notes"].append({"check": "L3_allowed_media",
                                 "result": "PASS" if blob[:4] == b"\x89PNG" else "FAIL",
                                 "detail": "PNG 头核对 %d bytes" % len(blob)})
        except Exception as e:
            out["notes"].append({"check": "L3_allowed_media", "result": "FAIL",
                                 "detail": str(e)[:200]})

    # A1 直读受保护题面（原 episode 绝对路径，含 gold）
    if "A1" in payload.get("attacks", []):
        tgt = payload.get("protected_target", "")
        try:
            txt = open(tgt, encoding="utf-8").read()
            out["notes"].append({"check": "A1_read_gold",
                                 "result": "VIOLATION_READ_OK",
                                 "detail": "读到 %d 字符（隔离失效！）" % len(txt)})
        except Exception as e:
            out["notes"].append({"check": "A1_read_gold", "result": "BLOCKED",
                                 "detail": "%s: %s" % (type(e).__name__,
                                                       str(e)[:150])})

    # A2 读其他候选状态
    if "A2" in payload.get("attacks", []):
        tgt = payload.get("other_state_target", "")
        try:
            txt = open(tgt, encoding="utf-8").read()
            out["notes"].append({"check": "A2_read_other_state",
                                 "result": "VIOLATION_READ_OK",
                                 "detail": "读到 %d 字符" % len(txt)})
        except Exception as e:
            out["notes"].append({"check": "A2_read_other_state",
                                 "result": "BLOCKED",
                                 "detail": "%s: %s" % (type(e).__name__,
                                                       str(e)[:150])})

    # A3 路径跳转/绝对路径/junction（run_dir = ROOT/isol_runs/<ts>/<run> → repo = ../../..）
    if "A3" in payload.get("attacks", []):
        repo = os.path.abspath(os.path.join(host.run_dir, "..", "..", ".."))
        for tag, rel in (("A3a_traversal", os.path.join(
                host.run_dir, "..", "..", "..", "refs", "arena_v1", "release.json")),
                ("A3b_absolute", os.path.join(repo, "refs", "arena_v1",
                                              "release.json")),
                ("A3c_junction", os.path.join(host.run_dir, "evil_link",
                                              "release.json"))):
            try:
                txt = open(os.path.abspath(rel), encoding="utf-8").read()
                out["notes"].append({"check": tag, "result": "VIOLATION_READ_OK",
                                     "detail": "读到 %d 字符" % len(txt)})
            except Exception as e:
                out["notes"].append({"check": tag, "result": "BLOCKED",
                                     "detail": "%s: %s" % (type(e).__name__,
                                                           str(e)[:150])})

    # A4 伪造权威字段（gold/id_map/score 自报）
    if "A4" in payload.get("attacks", []):
        out["gold"] = {"soft_keywords_any": [["FAKE"]], "note": "伪造"}
        out["id_map"] = {"m0": "FAKE"}
        out["score"] = 1.0
        out["notes"].append({"check": "A4_forge_authority", "result": "SENT",
                             "detail": "已回传伪造 gold/id_map/score，"
                                       "由控制器证明忽略"})

    # A5 导入/猴子补丁评分器
    if "A5" in payload.get("attacks", []):
        for tag, code in (
                ("A5a_import_scorer", "import lb_scorer"),
                ("A5b_import_scorer_abs", "从原 arena 绝对路径导入")):
            try:
                if tag.endswith("scorer"):
                    import lb_scorer  # noqa
                else:
                    import importlib.util
                    repo = os.path.abspath(os.path.join(host.run_dir, "..", "..", ".."))
                    spec = importlib.util.spec_from_file_location(
                        "lb_scorer_x",
                        os.path.join(repo, "arena", "lb_scorer.py"))
                    m = importlib.util.module_from_spec(spec)
                    spec.loader.exec_module(m)
                out["notes"].append({"check": tag, "result": "VIOLATION_IMPORT_OK"})
            except Exception as e:
                out["notes"].append({"check": tag, "result": "BLOCKED",
                                     "detail": "%s: %s" % (type(e).__name__,
                                                           str(e)[:150])})

    # A6 绕过代理直连本地上游端口 + 子进程继承越权
    if "A6" in payload.get("attacks", []):
        import socket
        port = payload.get("canary_port", 47777)
        try:
            s = socket.create_connection(("127.0.0.1", port), timeout=3)
            s.sendall(b"DIRECT-BYPASS-CANARY\n")
            data = s.recv(200)
            s.close()
            out["notes"].append({"check": "A6a_direct_socket",
                                 "result": "NET_NOT_ISOLATED",
                                 "detail": "直连 127.0.0.1:%d 成功 echo=%r（网络出站"
                                           "未隔离——登记最小权限需求）"
                                           % (port, data[:60])})
        except Exception as e:
            out["notes"].append({"check": "A6a_direct_socket", "result": "BLOCKED",
                                 "detail": str(e)[:150]})
        # A6c：直连真实上游通道（无密钥）——必要不变量：不产生未登记真实请求
        up_port = payload.get("upstream_port")
        if up_port:
            for tag, line in (("A6c_direct_upstream_no_key", "GIVE-ME-COMPLETION\n"),
                              ("A6c_guess_key", "KEY guess-0000\nsteal\n")):
                try:
                    s = socket.create_connection(("127.0.0.1", up_port), timeout=3)
                    s.sendall(line.encode())
                    data = s.recv(200)
                    s.close()
                    served = data.startswith(b"UPSTREAM-OK")
                    out["notes"].append({
                        "check": tag,
                        "result": "VIOLATION_SERVED" if served else "BLOCKED",
                        "detail": "upstream replied %r" % data[:60]})
                except Exception as e:
                    out["notes"].append({"check": tag, "result": "BLOCKED",
                                         "detail": str(e)[:150]})
            # 密钥环境嗅探：宿主 env 必须无 UPSTREAM_KEY
            out["notes"].append({
                "check": "A6c_env_key_absent",
                "result": "PASS" if "UPSTREAM_KEY" not in os.environ else
                          "VIOLATION_KEY_IN_ENV",
                "detail": "env keys=%d" % len(os.environ)})
        try:
            import subprocess, sys as _s
            child = subprocess.run(
                [_s.executable, "-c",
                 "open(r'%s', encoding='utf-8').read()" % payload.get(
                     "protected_target", "")],
                capture_output=True, text=True, timeout=10)
            if child.returncode == 0 and child.stdout:
                out["notes"].append({"check": "A6b_child_process",
                                     "result": "VIOLATION_READ_OK",
                                     "detail": child.stdout[:100]})
            else:
                out["notes"].append({"check": "A6b_child_process",
                                     "result": "BLOCKED",
                                     "detail": (child.stderr or "")[-150:]})
        except Exception as e:
            out["notes"].append({"check": "A6b_child_process", "result": "BLOCKED",
                                 "detail": str(e)[:150]})

    # A7 伪造/重放/超额代理请求
    if "A7" in payload.get("attacks", []):
        out["_proxy_calls"].append({"attempt_id": "forge_replay_1",
                                    "prompt": "正常请求1"})
        out["_proxy_calls"].append({"attempt_id": "forge_replay_1",
                                    "prompt": "重放同 attempt_id"})
        for i in range(3, 9):
            out["_proxy_calls"].append({"attempt_id": "quota_%d" % i,
                                        "prompt": "超额请求%d" % i})
        out["notes"].append({"check": "A7_proxy_abuse", "result": "SENT",
                             "detail": "重放1次+超额请求已发，由代理账本判定"})

    # A8 宿主异常路径
    if "A8_crash" in payload.get("attacks", []):
        raise RuntimeError("A8 主动崩溃测试")
    if "A8_bigframe" in payload.get("attacks", []):
        out["reply"] = "X" * (2 * 1000 * 1000)
    return out
