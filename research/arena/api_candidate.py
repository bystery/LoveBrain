# -*- coding: utf-8 -*-
"""api_candidate.py — 模型返回物边界（回复只当数据，不执行）。

权威口径：refs/arena_v1/SCOPE_DECISION_API_CANDIDATE_20261022.md（v2）
注意：harness 是参赛对象、可变；这里约束的只是"API 返回的文本/JSON 不得被执行"，
不冻结 harness，也不影响 worker 修改自己编写的本地机制代码。

三条不可协商的边界，本模块是它们**唯一的实现点**：

1. **出站白名单**：请求体只允许 model / messages / temperature / stream。
   任何 tool 通道（tools / functions / tool_choice / parallel_tool_calls）
   一律抛 BoundaryViolation —— 不是"不发"，是"发不出去"。
2. **入站纯数据**：回复只经 json.loads 解析成 dict/list/str/数字，永远不
   eval/exec/compile/import，也不把回复里的路径当路径用。JSON 解析失败不是
   错误，是"答非 JSON 的文本"，原样留档。
3. **原文留档**：reply_raw.txt 逐字保存（含超长、乱码、围栏、非 JSON），
   另存 reply_ingest.json 记录分类/长度/散列/顶层键，供复核。

本模块不 import 候选目录下的代码，也不 spawn 进程。
"""
import hashlib
import json
import re

# ---------- 出站 ----------

REQUEST_BODY_ALLOWED_KEYS = ("model", "messages", "temperature", "stream")
# tool 通道键：出现即失败（不是过滤掉，是拒绝构造）
TOOL_CHANNEL_KEYS = ("tools", "functions", "tool_choice", "tool_calls",
                     "parallel_tool_calls")
# API 考生没有文件/命令能力——这些字段一旦出现，说明有人试图开放能力面
CAPABILITY_KEYS = ("file_search", "code_interpreter", "computer_use",
                   "mcp_servers", "web_search_options")

DEFAULT_TEMPERATURE = 0.7
MAX_REPLY_CHARS = 4_000_000


class BoundaryViolation(RuntimeError):
    """构造了越界请求：调用方必须修代码，不得降级重试。"""


def check_request_body(body):
    """请求体白名单核验。返回 body（原对象）；越界抛 BoundaryViolation。"""
    keys = set(body)
    bad_tool = sorted(keys & set(TOOL_CHANNEL_KEYS))
    if bad_tool:
        raise BoundaryViolation(
            "tool channel forbidden for api candidate: %s" % bad_tool)
    bad_cap = sorted(keys & set(CAPABILITY_KEYS))
    if bad_cap:
        raise BoundaryViolation(
            "capability channel forbidden for api candidate: %s" % bad_cap)
    # 受控例外（2026-10-25 性能阶段 A 方案）：stream_options 仅允许在 stream=true 下，
    # 且值必须恰为 {"include_usage": <bool>}，用途仅限于取流式 usage。
    # 不借此开放任何 tool/function/capability 通道，其余白名单照旧。
    allowed_extra = set()
    if "stream_options" in keys:
        if not body.get("stream"):
            raise BoundaryViolation(
                "stream_options only allowed when stream=true (got stream=%r)"
                % body.get("stream"))
        so = body.get("stream_options")
        if not isinstance(so, dict) or set(so) != {"include_usage"} \
                or not isinstance(so.get("include_usage"), bool):
            raise BoundaryViolation(
                "stream_options must be exactly {'include_usage': <bool>}, got %r" % (so,))
        allowed_extra = {"stream_options"}
    foreign = sorted(keys - set(REQUEST_BODY_ALLOWED_KEYS) - allowed_extra)
    if foreign:
        raise BoundaryViolation(
            "request body keys outside allowlist: %s (allowed=%s)"
            % (foreign, list(REQUEST_BODY_ALLOWED_KEYS)))
    if not isinstance(body.get("messages"), list) or not body["messages"]:
        raise BoundaryViolation("messages must be a non-empty list")
    for m in body["messages"]:
        if not isinstance(m, dict) or m.get("role") not in ("system", "user"):
            raise BoundaryViolation("only system/user messages are sent")
        if set(m) - {"role", "content"}:
            raise BoundaryViolation("message keys outside allowlist: %s"
                                    % sorted(set(m) - {"role", "content"}))
    temp = body.get("temperature")
    if temp is not None and not isinstance(temp, (int, float)):
        raise BoundaryViolation("temperature must be numeric")
    return body


def build_request_body(messages, model, temperature=None, stream=False):
    """唯一出站构造入口。temperature=None → 0.7（显式值，不出站 null）。"""
    body = {"model": model,
            "messages": messages,
            "temperature": DEFAULT_TEMPERATURE if temperature is None
            else temperature,
            "stream": bool(stream)}
    return check_request_body(body)


# ---------- 入站 ----------

_FENCE_RE = re.compile(r"```(?:json)?\s*(\{.*?\})\s*```", re.DOTALL)


def _first_object(text):
    start = text.find("{")
    while start != -1:
        depth, in_str, esc = 0, False, False
        for i in range(start, len(text)):
            ch = text[i]
            if in_str:
                if esc:
                    esc = False
                elif ch == "\\":
                    esc = True
                elif ch == '"':
                    in_str = False
                continue
            if ch == '"':
                in_str = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return text[start:i + 1]
        start = text.find("{", start + 1)
    return None


def ingest_reply(raw):
    """把原始回复分类成数据。只做 json.loads（纯数据，无求值）。

    返回：{"kind": "json"|"text"|"too_large"|"non_text",
           "parsed": obj|None, "chars": int, "sha256": str,
           "top_level_keys": [str]|None, "note": str}
    """
    if not isinstance(raw, str):
        return {"kind": "non_text", "parsed": None, "chars": 0,
                "sha256": "", "top_level_keys": None,
                "note": "adapter returned %s, not str" % type(raw).__name__}
    data = raw.encode("utf-8", "replace")
    sha = hashlib.sha256(data).hexdigest()
    if len(raw) > MAX_REPLY_CHARS:
        return {"kind": "too_large", "parsed": None, "chars": len(raw),
                "sha256": sha, "top_level_keys": None,
                "note": "reply exceeds MAX_REPLY_CHARS=%d; kept verbatim, "
                        "not parsed" % MAX_REPLY_CHARS}
    candidates = [raw.strip()]
    m = _FENCE_RE.search(raw)
    if m:
        candidates.append(m.group(1))
    obj_block = _first_object(raw)
    if obj_block:
        candidates.append(obj_block)
    for c in candidates:
        if not c:
            continue
        try:
            parsed = json.loads(c)
        except (json.JSONDecodeError, ValueError):
            continue
        keys = list(parsed.keys())[:24] if isinstance(parsed, dict) else None
        return {"kind": "json", "parsed": parsed, "chars": len(raw),
                "sha256": sha, "top_level_keys": keys, "note": ""}
    return {"kind": "text", "parsed": None, "chars": len(raw), "sha256": sha,
            "top_level_keys": None,
            "note": "no JSON object found; answer kept as plain text"}


def store_reply(turn_dir, raw):
    """逐字落 reply_raw.txt + 分类落 reply_ingest.json（确定内容，无时间戳）。"""
    import os
    os.makedirs(turn_dir, exist_ok=True)
    text = raw if isinstance(raw, str) else ""
    p = os.path.join(turn_dir, "reply_raw.txt")
    with open(p, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    info = ingest_reply(raw)
    info.pop("parsed", None)                      # 解析结果不进证据文件（体积/语义）
    with open(os.path.join(turn_dir, "reply_ingest.json"), "w",
              encoding="utf-8") as f:
        json.dump(info, f, ensure_ascii=False, indent=1)
    return info
