# -*- coding: utf-8 -*-
"""test_failure_persistence.py — 第五波判决 §9 验收：失败日志做成持久证据。

受控异常（零真实外部网络）验证：
  V1 网关级：本地假服务端 accept 后不响应 → 真实超时异常 →
     事件日志 JSONL 立即有失败记录（不等 runner 成功路径）、calls 内存账有错误记录；
  V2 runner 级：同一受控超时下跑 episode → 异常向上抛之前
     turn 目录已落盘 calls.json + error.json（旧实现两者全丢）；
  V3 usage 容错：失败 usage（无 prompt/completion 键）汇总不抛 KeyError，
     标注 has_unknown_usage（第五波判决 §9：未知≠0 成本）；
  V4 judge 重试证据：首次坏 JSON + 第二次成功 → evidence.attempts 两条
     （旧实现只留最后一次，首次坏响应丢失）。"""
import json
import os
import socket
import sys
import tempfile
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "arena"))
import lb_gateway  # noqa: E402
import lb_runner   # noqa: E402
import lb_judge_rerank  # noqa: E402


def _dead_server():
    """本地受控异常源：accept 后永不响应（真实读超时）。返回 (port, stop_fn)。"""
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.bind(("127.0.0.1", 0))
    srv.listen(4)
    port = srv.getsockname()[1]
    stop = threading.Event()

    def loop():
        srv.settimeout(0.2)
        while not stop.is_set():
            try:
                conn, _ = srv.accept()
                # 收到请求后故意不响应，握着连接直到客户端超时
                time.sleep(5)
                try:
                    conn.close()
                except OSError:
                    pass
            except socket.timeout:
                continue
            except OSError:
                break
        srv.close()

    t = threading.Thread(target=loop, daemon=True)
    t.start()
    return port, stop.set


def main():
    fails = []
    tmp = tempfile.mkdtemp(prefix="lb_failpersist_")
    port, stop_server = _dead_server()
    try:
        base = "http://127.0.0.1:%d" % port
        # ---- V1 网关级：超时异常即刻进事件日志 ----
        evlog = os.path.join(tmp, "gateway_events.jsonl")
        os.environ["LB_API_BASE"] = base
        os.environ.setdefault("LB_API_KEY", "test-key")
        os.environ["LB_MODEL"] = "test-model"
        from lb_gateway import ADAPTERS
        gw = lb_gateway.ModelGateway(ADAPTERS["openai_compat"](), event_log_path=evlog)
        raised = False
        try:
            gw.generate("sys", "user", timeout_s=1, tag="main")
        except Exception as e:
            raised = True
            etype = type(e).__name__
        if not raised:
            fails.append("V1: 受控超时未触发异常")
        if not os.path.isfile(evlog):
            fails.append("V1: 网关事件日志未落盘")
        else:
            evs = [json.loads(ln) for ln in open(evlog, encoding="utf-8") if ln.strip()]
            err_evs = [e for e in evs if e.get("kind") == "attempt_failed"]
            if not err_evs:
                fails.append("V1: 事件日志无失败记录")
            elif "timeout" not in (err_evs[0].get("error") or "").lower():
                fails.append("V1: 失败记录无错误类型: %s" % err_evs[0].get("error"))
            if err_evs and not err_evs[0].get("user"):
                fails.append("V1: 失败记录缺完整请求（user）")
            started = [e for e in evs if e.get("kind") == "attempt_started"]
            if not started:
                fails.append("V1: 事件日志无 attempt_started（第六波 §6.3）")
        if not any("error" in (c.get("usage") or {}).get("source", "")
                   or c.get("error") for c in gw.calls):
            fails.append("V1: calls 内存账无错误记录")

        # ---- V2 runner 级：异常路径 calls.json + error.json 落盘 ----
        ep = {"episode_id": "FAILPERSIST_TEST", "persona": {"关系": "测试"},
              "initial_kb": {}, "gaps": [],
              "turns": [{"case_id": "FP_t01",
                         "input": {"clock": "2026-10-18 12:00",
                                   "messages": [{"role": "HER", "text": "在吗",
                                                 "ts": "2026-10-18 11:58"}]}}]}
        ep_path = os.path.join(tmp, "ep.json")
        with open(ep_path, "w", encoding="utf-8") as f:
            json.dump(ep, f, ensure_ascii=False)
        out = os.path.join(tmp, "run")
        runner_raised = False
        try:
            lb_runner.run_episode(ep_path, "candidates/round_0_baseline", out,
                                  "openai_compat")
        except Exception:
            runner_raised = True
        if not runner_raised:
            fails.append("V2: runner 未向上抛异常（应 fail-fast）")
        turn_dir = os.path.join(out, "turn_01_FP_t01")
        if not os.path.isfile(os.path.join(turn_dir, "calls.json")):
            fails.append("V2: 异常路径 calls.json 未落盘（失败账丢失复现）")
        else:
            calls = json.load(open(os.path.join(turn_dir, "calls.json"), encoding="utf-8"))
            if not calls or not any(c.get("error") for c in calls):
                fails.append("V2: calls.json 无错误记录")
        if not os.path.isfile(os.path.join(turn_dir, "error.json")):
            fails.append("V2: 异常路径 error.json 未落盘")
        if not os.path.isfile(os.path.join(out, "gateway_events.jsonl")):
            fails.append("V2: runner 网关事件日志未落盘")

        # ---- V5（第六波判决 §6.3）：单题 run_case 路径与 episode 共用异常持久化 ----
        case = {"case_id": "FP_case01", "turn_type": "reply",
                "input": {"clock": "2026-10-18 12:00",
                          "messages": [{"role": "HER", "text": "在吗",
                                        "ts": "2026-10-18 11:58"}]}}
        case_path = os.path.join(tmp, "case.json")
        with open(case_path, "w", encoding="utf-8") as f:
            json.dump(case, f, ensure_ascii=False)
        out_c = os.path.join(tmp, "run_case")
        case_raised = False
        try:
            lb_runner.run_case(case_path, "candidates/round_0_baseline", out_c,
                               "openai_compat")
        except Exception:
            case_raised = True
        if not case_raised:
            fails.append("V5: run_case 未向上抛异常")
        for fn in ("calls.json", "error.json", "gateway_events.jsonl"):
            if not os.path.isfile(os.path.join(out_c, fn)):
                fails.append("V5: run_case 异常路径 %s 未落盘" % fn)
        # ---- V6（第六波判决 §6.3）：attempt_started 先于 attempt_failed，带 run_id/case_id ----
        ev_path = os.path.join(out_c, "gateway_events.jsonl")
        evs = [json.loads(ln) for ln in open(ev_path, encoding="utf-8") if ln.strip()]
        kinds = [e["kind"] for e in evs]
        if "attempt_started" not in kinds or "attempt_failed" not in kinds:
            fails.append("V6: 事件流缺 attempt_started/attempt_failed（kinds=%s）" % kinds)
        elif kinds.index("attempt_started") > kinds.index("attempt_failed"):
            fails.append("V6: attempt_started 未先于 attempt_failed")
        if evs and not evs[0].get("run_id"):
            fails.append("V6: 事件缺 run_id")
        if evs and not evs[0].get("case_id"):
            fails.append("V6: 事件缺 case_id")
        if evs and not evs[0].get("pid"):
            fails.append("V6: 事件缺 pid")
    finally:
        stop_server()
        os.environ.pop("LB_API_BASE", None)

    # ---- V3 usage 容错 ----
    gw3 = lb_gateway.ModelGateway(lb_gateway.MockAdapter())
    gw3.calls = [{"tag": "main", "usage": {"source": "error"}, "error": "TimeoutError: x"},
                 {"tag": "main", "usage": {"source": "api_usage", "prompt": 10,
                                           "completion": 5}}]
    u = gw3.turn_usage()
    if u.get("prompt") != 10 or u.get("completion") != 5:
        fails.append("V3: usage 汇总数值错误: %s" % u)
    if not u.get("has_unknown_usage"):
        fails.append("V3: 未知 usage 未标注 has_unknown_usage")

    # ---- V4 judge 重试证据 ----
    class FakeGW:
        def __init__(self):
            self.calls = []
            self.n = 0

        def generate(self, system, user, **kw):
            self.n += 1
            if self.n == 1:
                text = "这不是JSON"
            else:
                text = json.dumps({"ok": ["①"], "bad": ["②"], "bad_reason": {}},
                                  ensure_ascii=False)
            self.calls.append({"raw_text": text, "temperature": 0.7,
                               "resp_model": "fake", "resp_id": "r%d" % self.n,
                               "finish": "stop"})
            return {"text": text, "finish": "stop", "usage": None}

    fgw = FakeGW()
    q = {"qid": "T1", "context": "测试", "options": ["a", "b"]}
    out_json, res, attempt = lb_judge_rerank.call_judge(fgw, q)
    ev = (res or {}).get("evidence") or {}
    atts = ev.get("attempts") or []
    if len(atts) != 2:
        fails.append("V4: 重试证据应含 2 次尝试，实得 %d" % len(atts))
    elif not atts[0].get("raw_text"):
        fails.append("V4: 首次坏响应未留证")
    if attempt != 1:
        fails.append("V4: final_attempt 应为 1，实得 %s" % attempt)

    if fails:
        print("FAILURE PERSISTENCE TEST FAIL")
        for x in fails:
            print(" -", x)
        sys.exit(1)
    print("FAILURE PERSISTENCE TEST PASS：受控超时下网关事件日志/calls/error.json 全部落盘；"
          "usage 未知容错；judge 重试全尝试留证。")


if __name__ == "__main__":
    main()
