# -*- coding: utf-8 -*-
"""tools/test_stream_timing_v2.py — 计时诚实性 + 真实适配器流式路径（零网络，替换 v1 断言口径）。

v1 保留作历史。本版按独立复核 REVIEW §5 的三条反例来测：
  ① 预览早到、验证晚做 ⇒ first_usable 必须是**验证完成时刻**，不得回填成预览时刻；
  ② 坏整包/无有效消费结果 ⇒ first_usable=None + 原因，不得用 completion 冒充；
  ③ 正式 OpenAICompatAdapter 在替换 HTTP 下真的发 stream、真的解析 usage、分段落盘；
  ④ 非流式仍按历史配置工作，first_content=null 且给出原因（不假装测得到首字）。
"""
import io
import json
import os
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path[:0] = [os.path.join(ROOT, 'arena')]

import api_candidate                        # noqa: E402
import lb_gateway                           # noqa: E402
from lb_stream_timing import (StreamTimer, consume_sse_lines, scan_recommended)  # noqa: E402

BODY = ('{"response":{"recommended":"好，那周六见，我提前到门口等你",'
        '"bad_boy":"就这么定了","playful":"x","warm":"y"},'
        '"directions":["a","b",null,"d"],"analysis":{}}')


def sse_lines(pieces, usage=None, role_only=False):
    out = [": ping\n", "\n"]
    if role_only:
        out.append("data: %s\n\n" % json.dumps(
            {"id": "c1", "model": "dsf",
             "choices": [{"index": 0, "delta": {"role": "assistant"}}]}))
    for p in pieces:
        out.append("data: %s\n\n" % json.dumps(
            {"id": "c1", "model": "dsf",
             "choices": [{"index": 0, "delta": {"content": p}}]}))
    out.append("data: %s\n\n" % json.dumps(
        {"id": "c1", "model": "dsf", "choices": [{"index": 0, "finish_reason": "stop"}]}))
    if usage:
        out.append("data: %s\n\n" % json.dumps({"id": "c1", "model": "dsf", "usage": usage}))
    out.append("data: [DONE]\n\n")
    return out


ok = []


def check(name, cond, detail=""):
    print("%-58s %s %s" % (name, "PASS" if cond else "FAIL", detail))
    ok.append(bool(cond))


def main():
    # ① 预览早到、验证晚做：first_usable 必须是验证时刻，不得回填
    t = StreamTimer()
    t.start_request()
    t.on_headers()
    consume_sse_lines(sse_lines([BODY[:30], BODY[30:]]), t)
    t.finish()
    preview_at = t.as_dict().get("first_preview_ms")
    time.sleep(0.12)                       # 模拟"10 秒后才做消费验证"
    t.mark_usable(True)
    d = t.as_dict()
    check("usable_time_is_verification_time_not_preview",
          d["first_usable_ms"] is not None and d["first_usable_ms"] > d["completion_ms"]
          and abs(d["first_usable_ms"] - preview_at) > 50,
          "preview=%s completion=%s usable=%s" % (preview_at, d["completion_ms"], d["first_usable_ms"]))
    check("no_backfill_detected_by_sanity", not t.sanity(d), "problems=%s" % t.sanity(d))
    fake = {"first_content_ms": 100.0, "first_preview_ms": 200.0, "completion_ms": 1000.0,
            "first_usable_ms": 200.0}      # 手工构造回填样本（可用时刻不可能早于收完）
    check("sanity_catches_backfilled_usable", bool(StreamTimer.sanity(fake)),
          "problems=%s" % StreamTimer.sanity(fake)[:1])
    honest = dict(fake, first_usable_ms=1010.0)
    check("sanity_accepts_post_completion_usable", not StreamTimer.sanity(honest),
          "problems=%s" % StreamTimer.sanity(honest))

    # ② 坏整包 / 没验证器：可用时刻为 None + 原因，绝不等于 completion
    t2 = StreamTimer()
    t2.start_request()
    t2.on_delta("not json at all")
    t2.finish()
    t2.mark_usable(False, reason="unconsumable_schema")
    d2 = t2.as_dict()
    check("bad_package_first_usable_is_null",
          d2["first_usable_ms"] is None and d2["usable_reason"] == "unconsumable_schema"
          and d2["completion_ms"] is not None, "usable=%s reason=%s" % (d2["first_usable_ms"], d2["usable_reason"]))
    t3 = StreamTimer()
    t3.start_request()
    t3.on_delta(BODY)
    t3.finish()                            # 不调用 mark_usable
    d3 = t3.as_dict()
    check("unverified_leaves_usable_null_with_preview_separate",
          d3["first_usable_ms"] is None and d3["first_preview_ms"] is not None
          and d3["preview_contract_status"] == "preview_only_unverified",
          "status=%s" % d3["preview_contract_status"])

    # ③ 预览定位：只有 response.recommended 才算，同名字符串在别处不触发
    t4 = StreamTimer()
    t4.start_request()
    t4.on_delta('{"meta":{"recommended":"这条不是首选正文"},"response":{')
    t4.on_delta('"recommended":"这才是首选"}}')
    d4 = t4.as_dict()
    check("preview_locates_response_recommended_only",
          d4.get("first_preview_ms") is not None and t4.preview_text == "这才是首选",
          "preview_text=%r" % t4.preview_text)
    check("scan_recommended_ignores_other_key", scan_recommended('{"a":1}') == (None, False))

    # ④ 真实适配器：替换 HTTP，证明出站 stream/usage、SSE 解析、分段落盘
    sent = {}

    class FakeResp:
        def __init__(self, lines):
            self._lines = lines

        def __iter__(self):
            return iter(self._lines)

        def read(self):
            return b""

        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

    def fake_urlopen(req, timeout=None):
        sent["body"] = json.loads(req.data.decode("utf-8"))
        sent["url"] = req.full_url
        return FakeResp(sse_lines([BODY[:40], BODY[40:]],
                                  usage={"prompt_tokens": 11, "completion_tokens": 22}))

    real = urllib_open = None
    import urllib.request
    real = urllib.request.urlopen
    urllib.request.urlopen = fake_urlopen
    os.environ["LB_API_BASE"] = "http://127.0.0.1:17770/v1"
    os.environ["LB_STREAM_TIMING"] = "1"
    os.environ["LB_STREAM_USAGE"] = "1"
    try:
        ad = lb_gateway.OpenAICompatAdapter()
        res = ad.generate("s", "u", {"images": [], "model": "dsf", "temperature": 0.7,
                                     "timeout_s": 30, "directive": {},
                                     "stream": True, "stream_options": {"include_usage": True}})
    finally:
        urllib.request.urlopen = real
    tm = res.get("timing") or {}
    check("real_adapter_sends_stream_with_usage_flag",
          sent.get("body", {}).get("stream") is True
          and sent["body"].get("stream_options") == {"include_usage": True},
          "body keys=%s" % sorted(sent.get("body", {})))
    check("real_adapter_parses_sse_and_usage",
          res["text"] == BODY and res["usage"] == {"prompt_tokens": 11, "completion_tokens": 22}
          and res["resp_id"] == "c1", "usage=%s" % res["usage"])
    check("real_adapter_segments_landed",
          tm.get("first_content_ms") is not None and tm.get("completion_ms") is not None
          and tm.get("stream_requested") is True and tm.get("usage_source") == "api_usage",
          "keys=%s" % sorted(tm)[:6])
    check("stream_options_still_boundary_checked",
          _boundary_rejects(sent.get("body", {})))

    # ⑤ 非流式保持历史配置：first_content 明确 null + 原因，不冒充
    def fake_urlopen_ns(req, timeout=None):
        sent["body2"] = json.loads(req.data.decode("utf-8"))

        class R:
            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

            def read(self):
                return json.dumps({"id": "n1", "model": "dsf", "usage": {"prompt_tokens": 3,
                                              "completion_tokens": 4},
                                   "choices": [{"message": {"content": BODY},
                                                "finish_reason": "stop"}]}).encode()
        return R()
    urllib.request.urlopen = fake_urlopen_ns
    os.environ["LB_STREAM_TIMING"] = "0"
    try:
        res2 = lb_gateway.OpenAICompatAdapter().generate(
            "s", "u", {"images": [], "model": "dsf", "temperature": 0.7, "timeout_s": 30,
                       "directive": {}, "stream": False, "stream_options": None})
    finally:
        urllib.request.urlopen = real
    tm2 = res2["timing"]
    check("non_stream_keeps_historical_request_shape",
          sent["body2"]["stream"] is False and "stream_options" not in sent["body2"],
          "keys=%s" % sorted(sent["body2"]))
    check("non_stream_does_not_fake_first_content",
          tm2["first_content_ms"] is None and tm2.get("first_content_reason") == "non_stream_single_shot"
          and tm2["completion_ms"] is not None, "reason=%s" % tm2.get("first_content_reason"))

    # ⑥ 网关策略：默认非流式；开关才切；配置身份分别命名
    os.environ.pop("LB_STREAM_TIMING", None)
    check("gateway_policy_defaults_to_historical_non_stream", lb_gateway.stream_policy() == (False, False))
    os.environ["LB_STREAM_TIMING"] = "1"
    check("gateway_policy_stream_implies_usage_gate", lb_gateway.stream_policy() == (True, True))
    check("config_identity_declared_separately",
          lb_gateway.STREAM_CONFIG_IDENTITY["stream"] != lb_gateway.STREAM_CONFIG_IDENTITY["non_stream"])

    print("\nSTREAM_TIMING_V2: %s (%d checks, failed=%d, real_model_calls=0, network=blocked)"
          % ("PASS" if all(ok) else "FAIL", len(ok), len(ok) - sum(ok)))
    sys.exit(0 if all(ok) else 1)


def _boundary_rejects(body):
    """白名单仍然拦越界通道：把 stream_options 换成 tools 必须当场拒绝。"""
    bad = dict(body)
    bad["tools"] = [{"type": "function"}]
    try:
        api_candidate.check_request_body(bad)
        return False
    except api_candidate.BoundaryViolation:
        return True


if __name__ == "__main__":
    main()
