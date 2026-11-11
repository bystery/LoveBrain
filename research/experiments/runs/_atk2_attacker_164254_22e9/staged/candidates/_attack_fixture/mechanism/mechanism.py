# candidates/_attack_fixture — 隔离矩阵攻击夹具（非生产候选，仅测试用）
#
# v2 机制接口；攻击指令经 ctx.input.note 传入（INPUT_VISIBLE_KEYS 白名单内
# 字段——与真实候选可见面一致，不额外开洞）。攻击面：
#   A1 直接 open 仓库 gold/受保护文件（绝对路径，模拟"猜到路径"）
#   A2 读兄弟运行状态（控制器给定的路径）——诚实探针：无受限环境时应可读
#   A3 相对路径跳转（../../../refs/...）
#   A4 导入评分器（staged 树内不存在 lb_scorer）+ 绝对路径加载变体
#   A5 在回传里伪造权威字段（score10/gold/id_map）——可信侧必须忽略
#   A6a 直连本地 canary 端口；A6c 真 HTTP POST 直连"上游"（无密钥/猜密钥）
#   A6b 子进程读受保护文件——必须给出确定拒绝证据（CHILD_READ_DENIED:<Exc>）
#   A7 经网关重放：同内容连发两次（额度/去重验证）
#   IMP 导入副作用 canary：import 时记录 PID——宿主必须执行、可信侧绝不能执行
#   MEDIA 私有文件伪装媒体 / data: URL / 已登记媒体正例
#   PARAM 候选覆盖 model/temperature（可信配置必须强制）
import json
import os
import sys as _sys

# ---- IMP：导入副作用 canary（只写自己 staged 目录，一处一行） ----
try:
    _imp_log = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            "import_pid.txt")
    with open(_imp_log, "a", encoding="utf-8") as _f:
        _f.write("%d|%s\n" % (os.getpid(), (_sys.argv[0] if _sys.argv else "?")))
except Exception:
    pass


def init(assets_root, episode_meta, gateway, sandbox):
    return {"attacks_log": []}


def run_turn(h, ctx, gw):
    inp = ctx.get("input") or {}
    spec = {}
    try:
        spec = json.loads(inp.get("note") or "{}")
    except Exception:
        spec = {}
    notes = []
    attacks = spec.get("attacks") or []

    def read_result(p):
        try:
            with open(p, "rb") as f:
                data = f.read()
            return "VIOLATION_READ_OK(len=%d)" % len(data)
        except Exception as e:
            return "BLOCKED:%s" % type(e).__name__

    if "A1" in attacks:
        notes.append({"check": "A1_direct_open_protected",
                      "result": read_result(spec.get("protected_target", ""))})
    if "A2" in attacks:
        notes.append({"check": "A2_sibling_state",
                      "result": read_result(spec.get("other_state_target", ""))})
    if "A3" in attacks:
        base = os.path.dirname(os.path.dirname(os.path.dirname(
            os.path.dirname(os.path.abspath(__file__)))))   # staged 根
        rel = spec.get("protected_target_rel", "refs/arena_v1/release.json")
        notes.append({"check": "A3_traversal_out_of_staged",
                      "result": read_result(os.path.normpath(os.path.join(
                          base, "..", "..", "..", rel)))})
    if "A4" in attacks:
        try:
            import lb_scorer   # noqa: F401
            notes.append({"check": "A4_import_scorer", "result": "VIOLATION_IMPORTED"})
        except Exception as e:
            notes.append({"check": "A4_import_scorer",
                          "result": "BLOCKED:%s" % type(e).__name__})
        # V14 §4.1：绝对路径加载变体（搜索路径缺席不等于绝对路径不可行）。
        # 目标 = 控制器放的合成模块，import 即写 canary——可执行性有实据。
        abs_mod = spec.get("abs_module_target", "")
        if abs_mod:
            try:
                import importlib.util as _ilu
                _sp = _ilu.spec_from_file_location("atk_abs_mod", abs_mod)
                _m = _ilu.module_from_spec(_sp)
                _sp.loader.exec_module(_m)
                notes.append({"check": "A4b_abs_module_exec",
                              "result": "VIOLATION_EXECUTED",
                              "detail": abs_mod[:80]})
            except Exception as e:
                notes.append({"check": "A4b_abs_module_exec",
                              "result": "BLOCKED:%s" % type(e).__name__,
                              "detail": str(e)[:100]})
    if "IMP" in attacks:
        # 导入副作用 canary 回读：staged mechanism 目录的 import_pid.txt
        # 应恰好 1 行 = 宿主执行；控制器（runner_pid）绝不能出现在其中
        try:
            p = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "import_pid.txt")
            lines = open(p, encoding="utf-8").read().splitlines() \
                if os.path.isfile(p) else []
            notes.append({"check": "IMP_import_side_effect",
                          "result": "IMP_LINES=%d" % len(lines),
                          "detail": "|".join(lines)[:120]})
        except Exception as e:
            notes.append({"check": "IMP_import_side_effect",
                          "result": "FAIL:%s" % type(e).__name__})
    if "MEDIA" in attacks:
        # 私有文件伪装媒体 → 代理必须拒绝（不能当文件读取代理）
        try:
            gw.generate("", "MEDIA-CANARY",
                        images=[spec.get("media_secret_target", "")],
                        tag="media_atk")
            notes.append({"check": "MEDIA_private_rejected",
                          "result": "VIOLATION_READ_AS_MEDIA"})
        except Exception as e:
            notes.append({"check": "MEDIA_private_rejected",
                          "result": "REJECTED:%s" % type(e).__name__,
                          "detail": str(e)[:140]})
        # data: URL 内联注入 → 拒绝
        try:
            gw.generate("", "MEDIA-DATAURL",
                        images=["data:image/png;base64,QUJD"], tag="media_data")
            notes.append({"check": "MEDIA_dataurl_rejected",
                          "result": "VIOLATION_INLINE_SENT"})
        except Exception as e:
            notes.append({"check": "MEDIA_dataurl_rejected",
                          "result": "REJECTED:%s" % type(e).__name__})
        # 已登记媒体正例 → 放行（该轮题面登记资产）
        reg = None
        for m in (inp.get("messages") or []):
            if isinstance(m, dict) and m.get("image"):
                reg = m["image"]
                break
        if reg:
            try:
                res = gw.generate("", "MEDIA-REGISTERED-OK", images=[reg],
                                  tag="media_ok")
                notes.append({"check": "MEDIA_registered_ok",
                              "result": "PASS" if res.get("text") is not None
                              else "FAIL"})
            except Exception as e:
                notes.append({"check": "MEDIA_registered_ok",
                              "result": "FAIL:%s" % type(e).__name__,
                              "detail": str(e)[:140]})
    if "PARAM" in attacks:
        # 候选覆盖 model/temperature → 可信配置必须强制
        try:
            gw.generate("", "PARAM-CANARY", model="candidate_override",
                        temperature=1.23, tag="param_atk")
            notes.append({"check": "PARAM_override_enforced",
                          "result": "SENT_ENFORCED"})
        except Exception as e:
            notes.append({"check": "PARAM_override_enforced",
                          "result": "REFUSED:%s" % type(e).__name__,
                          "detail": str(e)[:140]})
    if "A6a" in attacks:
        import socket
        try:
            s = socket.create_connection(("127.0.0.1", spec.get("canary_port", 47777)), timeout=3)
            s.sendall(b"DIRECT-BYPASS\n")
            data = s.recv(200)
            s.close()
            notes.append({"check": "A6a_direct_socket",
                          "result": "REACHABLE(echo=%r)" % data[:40]})
        except Exception as e:
            notes.append({"check": "A6a_direct_socket",
                          "result": "BLOCKED:%s" % type(e).__name__})
    if "A6c" in attacks:
        # V14 §4.4：真实 HTTP POST（此前裸行请求根本没进 do_POST）
        import urllib.request
        import urllib.error
        port = spec.get("upstream_port")
        if port:
            for tag, auth in (("no_key", None), ("guess_key", "Bearer wrong-guess")):
                try:
                    body = json.dumps({"model": "atk", "messages": [
                        {"role": "user", "content": "ATK-A6C"}]}).encode()
                    req = urllib.request.Request(
                        "http://127.0.0.1:%d/v1/chat/completions" % port,
                        data=body, method="POST",
                        headers={"Content-Type": "application/json",
                                 **({"Authorization": auth} if auth else {})})
                    with urllib.request.urlopen(req, timeout=5) as r:
                        data = r.read()
                        code = r.status
                    served = code == 200 and b"chat.completion" in data \
                        and b'"object"' in data
                    notes.append({"check": "A6c_%s" % tag,
                                  "result": "VIOLATION_SERVED" if served
                                  else "UNEXPECTED_200",
                                  "detail": data[:60].decode(
                                      "utf-8", "replace")})
                except urllib.error.HTTPError as e:
                    notes.append({"check": "A6c_%s" % tag,
                                  "result": "BLOCKED_REFUSED",
                                  "detail": "HTTP %d %s" % (
                                      e.code, (e.read() or b"")[:40]
                                      .decode("utf-8", "replace"))})
                except Exception as e:
                    notes.append({"check": "A6c_%s" % tag,
                                  "result": "BLOCKED:%s" % type(e).__name__})
    if "A6b" in attacks:
        import subprocess
        import sys as _sys_a6b
        tgt = spec.get("protected_target", "")
        try:
            child = subprocess.run(
                [_sys_a6b.executable, "-I", "-B", "-c",
                 "import sys\n"
                 "try:\n"
                 "    d=open(sys.argv[1],'rb').read()\n"
                 "    print('CHILD_READ_OK',len(d))\n"
                 "except Exception as e:\n"
                 "    print('CHILD_READ_DENIED:'+type(e).__name__)",
                 tgt],
                capture_output=True, text=True, timeout=20,
                stdin=subprocess.DEVNULL, close_fds=True)
            out = child.stdout or ""
            if "CHILD_READ_OK" in out:
                notes.append({"check": "A6b_child_process",
                              "result": "VIOLATION_READ_OK",
                              "detail": out[:100]})
            elif "CHILD_READ_DENIED" in out:
                notes.append({"check": "A6b_child_process",
                              "result": out.strip().splitlines()[-1][:60]})
            else:
                notes.append({"check": "A6b_child_process",
                              "result": "BLOCKED:Indeterminate",
                              "detail": (child.stderr or "")[-150:]})
        except subprocess.TimeoutExpired as e:
            partial = b""
            try:
                partial = (e.stdout or b"") + (e.stderr or b"")
            except Exception:
                pass
            notes.append({"check": "A6b_child_process",
                          "result": "BLOCKED:TimeoutExpired",
                          "detail": "超时≠权限拒绝（V14 §4.4）partial=%s"
                                    % partial[:120]})
    if "A7" in attacks:
        # 同内容重放两次（额度/审计验证——控制器侧账本为准）
        for _i in range(2):
            try:
                gw.generate("sys", "REPLAY_CANARY", tag="replay")
                notes.append({"check": "A7_replay_call", "result": "SENT"})
            except Exception as e:
                notes.append({"check": "A7_replay_call",
                              "result": "REFUSED:%s" % type(e).__name__})

    reply = "ATTACK_FIXTURE_REPLY"
    out = {"reply": reply, "notes": notes}
    if "A5" in attacks:
        # 伪造权威字段：可信侧评分必须忽略（不在回传白名单）
        out["score10"] = 10.0
        out["veto"] = False
        out["gold"] = {"fake": True}
        out["id_map"] = {"m0": "HER", "m1": "ME", "m_fake": "HER"}

    # ---- 合法功能检查（G08 的 L 面；经由与攻击相同的 note 通道） ----
    if "L1" in attacks:
        # staged arena 依赖可导入（mechanism.py 上四级 = staged 根）
        staged = os.path.dirname(os.path.dirname(os.path.dirname(
            os.path.dirname(os.path.abspath(__file__)))))
        try:
            import sys as _sys
            _sys.path.insert(0, os.path.join(staged, "arena"))
            import protocol_public  # noqa: F401
            notes.append({"check": "L1_parent_deps", "result": "PASS"})
        except Exception as e:
            notes.append({"check": "L1_parent_deps", "result": "FAIL",
                          "detail": str(e)[:150]})
    if "L2" in attacks:
        # 状态序列化契约：写标记 → snapshot → 清空 → restore → 一致
        try:
            h["memory"] = ["fact1", "fact2"]
            snap = snapshot(h)
            h.clear()
            h2 = restore(None, snap)
            ok = h2.get("memory") == ["fact1", "fact2"]
            notes.append({"check": "L2_state_snapshot",
                          "result": "PASS" if ok else "FAIL"})
        except Exception as e:
            notes.append({"check": "L2_state_snapshot", "result": "FAIL",
                          "detail": str(e)[:150]})
    if "L4" in attacks:
        # 可信代理经密钥通道合法调用（key 只在控制器 env，宿主 env 无 key）。
        # IpcGateway.generate 语义：!ok 时 raise，成功时返回 body dict
        # （{"text","finish","usage",...}）——不是完整回传帧。
        try:
            res = gw.generate("", "L4-PROBE", tag="l4")
            text = res.get("text", "")
            ok = "UPSTREAM-OK" in text
            notes.append({"check": "L4_proxy_keyed_upstream_ok",
                          "result": "PASS" if ok else "FAIL",
                          "detail": text[:60]})
        except Exception as e:
            notes.append({"check": "L4_proxy_keyed_upstream_ok",
                          "result": "FAIL", "detail": str(e)[:150]})
    out["notes"] = notes
    return out


def snapshot(h):
    import json as _j
    return _j.dumps(h, ensure_ascii=False).encode("utf-8")


def restore(sandbox, snap):
    return json.loads(snap.decode("utf-8"))
