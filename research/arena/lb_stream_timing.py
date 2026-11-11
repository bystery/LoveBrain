# -*- coding: utf-8 -*-
"""arena/lb_stream_timing.py — 端到端分段计时与"首个可发送回复"探测（能力件，零网络）。

宪法口径（AGENTS 完整性能与花费）：
  首个内容到达 ≠ 首选已可发送；没有分段证据不猜排队/推理/传输成因。
本模块只负责**测量**，不改任何提示词、不改评分、不改出站白名单。

分段的定义（全部用单调时钟 perf_counter，不用秒级日志相减）：
  turn_start_ms      由调用方给（harness 取状态/装配之前的起点）
  pre_request_ms     turn_start → request_start（装配、序列化、必要 IPC；冷启动单列）
  headers_ms         request_start → 收到 HTTP 头（不是首 token）
  first_content_ms   request_start → 第一个**非空文本 delta**（role 空 delta、心跳、注释行不算）
  first_preview_ms   request_start → 缓冲区里第一次出现完整 recommended 字符串字面量
  first_usable_ms    request_start → 该预览文本经消费校验判为可发送
  last_content_ms    request_start → 最后一个内容 delta
  completion_ms      request_start → 响应读完
  all_ready_ms       所有可发选项解析+规范化完成
  persisted_ms       必要状态与审计落盘完成（与可显示解耦时两个都记）
  requires_full_payload  若无法从部分 JSON 判定，first_usable 就等于 completion，并如实标注

出站边界：请求体一律经 api_candidate.build_request_body（白名单，含 stream 开关）。
SSE 里要 usage 必须多发一个 `stream_options` 键，而它**不在白名单内**——
是否扩白名单属"实际 API 配置"变更，需人类明确批准；未批准时本模块照白名单走，
并把 usage_source 记为 unavailable_in_stream，绝不冒充有 usage、也不许把这种批次
当成费用可比的正式对照批。
"""
import json
import time


# ── 部分 JSON 里的首选文本扫描（增量、可重复调用）────────────────────────
def scan_string_value(buf, key):
    """在可能不完整的 JSON 文本里找 "key":"...."，返回 (value, closed) 。

    closed=False 表示字符串还没收尾（后续 delta 可能继续）。只做字面量级解析，
    不 eval、不修补、不猜缺失括号；转义按 JSON 规则还原。
    """
    k = json.dumps(key)                    # 形如 "recommended"
    i = buf.find(k)
    if i < 0:
        return None, False
    j = i + len(k)
    while j < len(buf) and buf[j] in " \t\r\n":
        j += 1
    if j >= len(buf) or buf[j] != ":":
        return None, False
    j += 1
    while j < len(buf) and buf[j] in " \t\r\n":
        j += 1
    if j >= len(buf) or buf[j] != '"':
        return None, False
    j += 1
    out, esc = [], False
    while j < len(buf):
        ch = buf[j]
        if esc:
            mapping = {'"': '"', "\\": "\\", "/": "/", "b": "\b", "f": "\f",
                       "n": "\n", "r": "\r", "t": "\t"}
            if ch == "u":
                if j + 4 >= len(buf):
                    return "".join(out), False
                try:
                    out.append(chr(int(buf[j + 1:j + 5], 16)))
                except ValueError:
                    return "".join(out), False
                j += 5
                esc = False
                continue
            out.append(mapping.get(ch, ch))
            esc = False
        elif ch == "\\":
            esc = True
        elif ch == '"':
            return "".join(out), True
        else:
            out.append(ch)
        j += 1
    return "".join(out), False


def scan_recommended(buf):
    """定位 response.recommended 的字符串值（只认 "response" 之后出现的 recommended）。

    返回 (value, closed)；closed=False 表示还没收尾。不完整 JSON 不算已可发送。
    """
    r = buf.find('"response"')
    if r < 0:
        return None, False
    return scan_string_value(buf[r + len('"response"'):], "recommended")


class StreamTimer:
    """记录一次请求的分段。所有 *_ms 相对 request_start；pre_request/turn 级相对 turn_start。"""

    FIELDS = ("turn_start_ms", "pre_request_ms", "headers_ms", "first_content_ms",
              "first_preview_ms", "first_usable_ms", "last_content_ms", "completion_ms",
              "all_ready_ms", "persisted_ms", "requires_full_payload",
              "usage_source", "stream_requested", "stream_options_sent",
              "preview_key", "chunk_count", "content_delta_count", "bytes_total")

    def __init__(self, turn_start_perf=None, preview_key="recommended"):
        self._t = time.perf_counter
        self.turn_start = turn_start_perf if turn_start_perf is not None else self._t()
        self.request_start = None
        self.preview_key = preview_key
        self.marks = {}
        self.buffer = ""
        self.chunk_count = 0
        self.content_delta_count = 0
        self.bytes_total = 0
        self.preview_checked = False
        self.preview_text = None
        self.usable_verified_at = None
        self.usable_reason = "not_verified"
        self.requires_full_payload = True
        self.stream_requested = False
        self.stream_options_sent = False
        self.usage_source = "none"
        self.error = None

    # — 请求生命周期 —
    def start_request(self):
        self.request_start = self._t()
        self.marks["pre_request_ms"] = round((self.request_start - self.turn_start) * 1000, 1)
        return self.request_start

    def on_headers(self):
        self._mark("headers_ms")

    def on_bytes(self, n):
        self.bytes_total += int(n or 0)

    def _rel(self):
        return round((self._t() - (self.request_start or self.turn_start)) * 1000, 1)

    def _mark(self, field, overwrite=False):
        if overwrite or field not in self.marks:
            self.marks[field] = self._rel()

    # — 流内容 —
    def on_delta(self, text):
        """只有非空文本 delta 才算首内容（role-only delta / 心跳 / 注释行不算）。"""
        if not isinstance(text, str) or not text:
            return
        self.content_delta_count += 1
        self.buffer += text
        self._mark("first_content_ms")
        self.marks["last_content_ms"] = self._rel()
        if self.preview_checked:
            return
        val, closed = (scan_recommended(self.buffer) if self.preview_key == "recommended"
                       else scan_string_value(self.buffer, self.preview_key))
        if closed and val and val.strip():
            self._mark("first_preview_ms")
            self.preview_checked = True
            self.preview_text = val

    def on_chunk(self, chunk_obj):
        self.chunk_count += 1

    def finish(self, error=None):
        self.marks["completion_ms"] = self._rel()
        self.error = error
        return self

    # — 消费判定：只在真实验证完成的那一刻打点，绝不回填 —
    def mark_usable(self, valid, reason=None):
        """调用方在**真正执行完消费校验后**调用本方法。

        valid=True  ⇒ first_usable_ms = 现在（真实验证完成时刻），不是预览时刻；
        valid=False ⇒ first_usable_ms = None 并记原因（没有有效消费结果就不谎称可发）。
        预览（first_preview_ms）单独保留，用于回答"能提前看到文字"，不代表最终契约通过。
        """
        self.usable_verified_at = self._rel() if valid else None
        self.requires_full_payload = not valid
        self.usable_reason = None if valid else (reason or "consumer_returned_no_valid_primary")
        return self

    def mark_usable_unverified(self, reason="no_consumer_verifier"):
        """没有验证器时诚实留空：不拿 completion 冒充可用时刻。"""
        self.usable_verified_at = None
        self.requires_full_payload = True
        self.usable_reason = reason
        return self

    def on_all_ready(self, n_options):
        self.marks["all_ready_ms"] = self._rel()
        self.marks["options_ready_count"] = int(n_options)
        return self

    def on_persisted(self):
        self.marks["persisted_ms"] = self._rel()
        return self

    def as_dict(self):
        d = dict(self.marks)
        # 诚实：没有真实验证完成时刻就不谎称"已可发送"；也不回填成 completion/预览时刻。
        d["first_usable_ms"] = self.usable_verified_at
        for k, v in (("usable_reason", getattr(self, "usable_reason", None)),
                     ("preview_contract_status",
                      ("preview_only_unverified" if self.usable_verified_at is None
                       and self.marks.get("first_preview_ms") is not None
                       else ("usable_verified" if self.usable_verified_at is not None
                             else "none"))),
                     ("requires_full_payload", self.requires_full_payload),
                     ("usage_source", self.usage_source),
                     ("stream_requested", self.stream_requested),
                     ("stream_options_sent", self.stream_options_sent),
                     ("preview_key", self.preview_key),
                     ("chunk_count", self.chunk_count),
                     ("content_delta_count", self.content_delta_count),
                     ("bytes_total", self.bytes_total)):
            d[k] = v
        if self.error:
            d["error"] = self.error
        return d

    @staticmethod
    def sanity(d):
        """分段一致性检查（不靠断言外部世界，只查内部单调性）。返回问题列表。

        关键反回填约束：可用时刻必然在收完并验证之后，所以 first_usable_ms 必须
        ≥ completion_ms；小于 completion 或小于 first_content 都视为造假信号。
        """
        problems = []
        chain = [("headers_ms", "first_content_ms"), ("first_content_ms", "first_preview_ms"),
                 ("first_preview_ms", "completion_ms"), ("completion_ms", "all_ready_ms"),
                 ("all_ready_ms", "persisted_ms")]
        for a, b in chain:
            if a in d and b in d and d[b] is not None and d[a] is not None \
                    and d[b] < d[a] - 0.05:
                problems.append("%s(%s) < %s(%s)" % (b, d[b], a, d[a]))
        fu = d.get("first_usable_ms")
        if fu is not None:
            if d.get("completion_ms") is not None and fu < d["completion_ms"] - 0.05:
                problems.append("backfilled first_usable(%s) < completion(%s)"
                                % (fu, d["completion_ms"]))
            if d.get("first_content_ms") is not None and fu < d["first_content_ms"] - 0.05:
                problems.append("first_usable(%s) < first_content(%s)"
                                % (fu, d["first_content_ms"]))
        for k, v in d.items():
            if isinstance(v, (int, float)) and k.endswith("_ms") and v < 0:
                problems.append("negative segment %s=%s" % (k, v))
        return problems


def stream_body(messages, model, temperature=None):
    """出站体仍走白名单构造；stream=True 是白名单内字段。
    需要 SSE 尾部 usage 时要多发一个 stream_options —— 它【不在】白名单，
    是否扩白名单属实际 API 配置变更，必须人类明确批准；本函数默认不发。"""
    import api_candidate
    return api_candidate.build_request_body(messages, model=model,
                                            temperature=temperature, stream=True)


def consume_sse_lines(lines, timer, usage_authorized=False):
    """把 SSE 行迭代器（urllib 响应对象或替身列表皆可）喂给 StreamTimer，拼回完整答卷。

    诚实规则：role-only delta、心跳（":"开头）、空行都不算首内容；
    请求了 stream 却拿不到 data: 行视为传输错误抛出；
    未获 stream_options 授权时 usage_source=unavailable_in_stream，不冒充有 token 用量。
    """
    text, finish, usage, resp_model, resp_id = "", None, None, None, None
    for raw_line in lines:
        line = raw_line.decode("utf-8") if isinstance(raw_line, bytes) else raw_line
        timer.on_bytes(len(line))
        line = line.strip()
        if not line or line.startswith(":"):
            continue
        if not line.startswith("data:"):
            raise ValueError("请求 stream 但响应行不是 data: %s" % line[:80])
        payload = line[5:].strip()
        if payload == "[DONE]":
            break
        chunk = json.loads(payload)
        timer.on_chunk(chunk)
        resp_model = chunk.get("model", resp_model)
        resp_id = chunk.get("id", resp_id)
        if chunk.get("usage"):
            usage = chunk["usage"]
            timer.usage_source = "api_usage"
        for choice in chunk.get("choices") or []:
            delta = choice.get("delta") or {}
            content = delta.get("content")
            if isinstance(content, str) and content:
                timer.on_delta(content)
                text += content
            if choice.get("finish_reason") is not None:
                finish = choice["finish_reason"]
    timer.stream_requested = True
    timer.stream_options_sent = bool(usage_authorized)
    if usage is None:
        timer.usage_source = ("unavailable_in_stream" if not usage_authorized
                              else "absent_in_stream")
    return {"text": text, "finish": finish, "usage": usage,
            "resp_model": resp_model, "resp_id": resp_id}
