# -*- coding: utf-8 -*-
"""tools/test_stream_timing.py — 分段计时能力件的离线验证（零网络、零真实模型调用）。

覆盖：部分 JSON 首选扫描、SSE 消费口径（role-only/心跳不算首内容）、
首预览<完成、无验证器时诚实退回 completion、usage 未授权不冒充、
出站白名单仍然拦 tool 通道、网关把 timing 落进 call log。
"""
import json
import os
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path[:0] = [os.path.join(ROOT, 'arena')]

import api_candidate                      # noqa: E402
import lb_gateway                          # noqa: E402
from lb_stream_timing import (StreamTimer, consume_sse_lines, scan_string_value,  # noqa: E402
                              stream_body)

REAL_CALLS = 0


def sse(chunks, usage=None, role_only_first=True):
    """造 SSE 行序列；chunks 里每项是一段文本 delta。"""
    lines = [": ping\n", "\n"]
    if role_only_first:
        lines.append("data: %s\n\n" % json.dumps(
            {"id": "c1", "model": "dsf",
             "choices": [{"index": 0, "delta": {"role": "assistant"}, "finish_reason": None}]}))
    for c in chunks:
        lines.append("data: %s\n\n" % json.dumps(
            {"id": "c1", "model": "dsf",
             "choices": [{"index": 0, "delta": {"content": c}, "finish_reason": None}]}))
    lines.append("data: %s\n\n" % json.dumps(
        {"id": "c1", "model": "dsf",
         "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]}))
    if usage:
        lines.append("data: %s\n\n" % json.dumps(
            {"id": "c1", "model": "dsf", "choices": [], "usage": usage}))
    lines.append("data: [DONE]\n\n")
    return lines


def sleep_chunks(n, secs):
    out = []
    for i in range(n):
        out.append(secs)
    return out


def check(name, cond, detail=""):
    print("%-58s %s %s" % (name, "PASS" if cond else "FAIL", detail))
    return bool(cond)


def _stream_options_gate_behaviour():
    """A 方案后的白名单口径：只放行 {"include_usage": bool} 且必须 stream=true。"""
    msgs = [{"role": "system", "content": "s"}, {"role": "user", "content": "u"}]
    good = api_candidate.build_request_body(msgs, model="dsf", temperature=.7, stream=True)
    good["stream_options"] = {"include_usage": True}
    try:
        api_candidate.check_request_body(good)
    except api_candidate.BoundaryViolation:
        return False
    cases = []
    ns = {"model": "dsf", "messages": msgs, "temperature": .7, "stream": False,
          "stream_options": {"include_usage": True}}
    cases.append(ns)
    cases.append({"model": "dsf", "messages": msgs, "temperature": .7, "stream": True,
                  "stream_options": {"include_usage": "yes"}})
    cases.append({"model": "dsf", "messages": msgs, "temperature": .7, "stream": True,
                  "stream_options": {"include_usage": True, "everything": True}})
    cases.append({"model": "dsf", "messages": msgs, "temperature": .7, "stream": True,
                  "tools": [{"type": "function"}]})
    cases.append({"model": "dsf", "messages": msgs, "temperature": .7, "stream": True,
                  "stream_options": ["include_usage"]})
    return all(_rejects(c) for c in cases)


def _rejects(body):
    try:
        api_candidate.check_request_body(body)
        return False
    except api_candidate.BoundaryViolation:
        return True


def main():
    ok = []
    body = ('{"response":{"recommended":"好，那周六见，我提前到门口等你",'
            '"bad_boy":"就这么定了","playful":"x","warm":"y"},'
            '"directions":["a","b",null,"d"],"analysis":{}}')
    # 1. 部分 JSON 扫描
    cut = body.index("好") + 4          # 落在字符串内部（收尾引号还没出现）
    v, closed = scan_string_value(body[:cut], "recommended")
    ok.append(check("scan_partial_string_open_until_quote_closed",
                    v is not None and closed is False and v == "好，那周",
                    "截断处返回未收尾值且 closed=False：%r" % v))
    v2, closed2 = scan_string_value(body, "recommended")
    ok.append(check("scan_closed_string_exact_and_unescaped",
                    closed2 and v2 == "好，那周六见，我提前到门口等你", repr(v2)[:40]))
    esc = '{"response":{"recommended":"行\\n周末\\u4e01见"}}'
    v3, c3 = scan_string_value(esc, "recommended")
    ok.append(check("scan_handles_json_escapes", c3 and v3 == "行\n周末丁见", repr(v3)))
    ok.append(check("scan_missing_key_returns_none", scan_string_value('{"a":1}', "recommended") == (None, False)))

    # 2. 流式：role-only 与心跳不算首内容；首预览早于完成
    tnull = StreamTimer()
    tnull.start_request()
    consume_sse_lines([l for l in sse([], role_only_first=True) if l.strip()], tnull)
    tnull.finish()
    ok.append(check("heartbeat_and_role_only_delta_are_not_first_content",
                    tnull.as_dict().get("first_content_ms") is None
                    and tnull.content_delta_count == 0 and tnull.chunk_count >= 1,
                    "chunks=%d deltas=%d（首内容未记）"
                    % (tnull.chunk_count, tnull.content_delta_count)))
    timer = StreamTimer(turn_start_perf=time.perf_counter() - 0.05)
    timer.start_request()
    timer.on_headers()
    pieces, delays = [], sleep_chunks(0, 0)
    step = len(body) // 12
    for i in range(0, len(body), step):
        pieces.append(body[i:i + step])
    for p in pieces:
        time.sleep(0.004)
        consume_sse_lines([l for l in sse([p], role_only_first=False) if l.strip()], timer)
    timer.finish()
    d0 = timer.as_dict()
    ok.append(check("first_content_ignores_role_only_and_heartbeat",
                    d0.get("first_content_ms") is not None
                    and timer.content_delta_count == len(pieces),
                    "deltas=%d chunks=%d" % (timer.content_delta_count, timer.chunk_count)))
    ok.append(check("first_preview_recorded_mid_stream",
                    d0.get("first_preview_ms") is not None
                    and d0["first_preview_ms"] <= d0["completion_ms"],
                    "preview=%s completion=%s" % (d0.get("first_preview_ms"), d0["completion_ms"])))
    time.sleep(0.02)
    timer.mark_usable(True)                # 真实验证完成时刻打点（不回填到预览）
    d = timer.as_dict()
    ok.append(check("first_usable_is_post_verification_stamp",
                    d["first_usable_ms"] is not None
                    and d["first_usable_ms"] >= d["completion_ms"]
                    and d["first_usable_ms"] >= d["first_preview_ms"],
                    "preview=%s completion=%s usable=%s"
                    % (d["first_preview_ms"], d["completion_ms"], d["first_usable_ms"])))
    timer.on_all_ready(4).on_persisted()
    d = timer.as_dict()
    ok.append(check("segments_monotonic_and_no_negative", not timer.sanity(d),
                    "problems=%s" % timer.sanity(d)))
    ok.append(check("pre_request_segment_separate_from_http",
                    d.get("pre_request_ms") is not None and d["pre_request_ms"] > 0,
                    "pre=%s headers=%s" % (d["pre_request_ms"], d["headers_ms"])))

    # 3. 没有验证器时诚实留空：first_usable=null + 原因，不拿 completion 冒充
    t2 = StreamTimer()
    t2.start_request()
    consume_sse_lines(sse([body]), t2)
    t2.finish().mark_usable_unverified()
    d2 = t2.as_dict()
    ok.append(check("no_verifier_leaves_usable_unknown",
                    d2["requires_full_payload"] is True and d2["first_usable_ms"] is None
                    and d2["usable_reason"] == "no_consumer_verifier",
                    "usable=None reason=%s" % d2["usable_reason"]))

    # 4. usage 记账诚实：没收到 usage 又不许发 stream_options → 记 unavailable，不填 0
    t3 = StreamTimer()
    t3.start_request()
    res3 = consume_sse_lines(sse([body]), t3)          # 无 usage 分片
    t3.finish()
    d3 = t3.as_dict()
    ok.append(check("missing_usage_recorded_as_unavailable_not_zero",
                    res3["usage"] is None and d3["usage_source"] == "unavailable_in_stream"
                    and d3["stream_options_sent"] is False,
                    "usage_source=%s" % d3["usage_source"]))
    t3b = StreamTimer()
    t3b.start_request()
    res3b = consume_sse_lines(sse([body], usage={"prompt_tokens": 5, "completion_tokens": 6}), t3b)
    t3b.finish()
    d3b = t3b.as_dict()
    ok.append(check("unprompted_usage_kept_as_api_usage_with_flag_false",
                    res3b["usage"] is not None and d3b["usage_source"] == "api_usage"
                    and d3b["stream_options_sent"] is False,
                    "服务端主动给 usage 时也如实标注配置"))

    # 5. 出站白名单：stream 与正形状 stream_options 允许；坏形状/非流式/tools 仍拒
    b = stream_body([{"role": "system", "content": "s"}, {"role": "user", "content": "u"}],
                    model="dsf", temperature=0.7)
    ok.append(check("stream_options_now_gated_not_blanket_blocked",
                    _stream_options_gate_behaviour(),
                    "正形状放行；非流式/坏形状/tools 全部仍拒"))

    # 6. 网关把 timing 落进 call log（替身适配器，零网络）
    class StubAdapter:
        name = "stub_stream"

        def generate(self, system, user, payload):
            return {"text": body, "finish": "stop", "usage": None,
                    "resp_model": "dsf", "resp_id": "stub1",
                    "timing": {"first_content_ms": 120.0, "first_preview_ms": 900.0,
                               "first_usable_ms": 900.0, "completion_ms": 1200.0,
                               "requires_full_payload": False}}

    gw = lb_gateway.ModelGateway(StubAdapter())
    gw.set_run_context("unit_stream_timing", "probe")
    gw.generate("s", "u", model="dsf", temperature=0.7)
    rec = gw.calls[-1]
    ok.append(check("gateway_persists_timing_into_call_log",
                    isinstance(rec.get("timing"), dict)
                    and rec["timing"]["first_usable_ms"] == 900.0,
                    "keys=%s" % sorted((rec.get("timing") or {}).keys())))

    print("\nSTREAM_TIMING: %s (%d checks, failed=%d, real_model_calls=%d)"
          % ("PASS" if all(ok) else "FAIL", len(ok), len(ok) - sum(ok), REAL_CALLS))
    sys.exit(0 if all(ok) else 1)


if __name__ == "__main__":
    main()
