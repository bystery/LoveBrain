# lb_gateway.py — 考场统一模型网关（Phase 0 v2，refs/REFACTOR_PLAN_PHASE0.md §2）
# 宪法地位：候选调模型的唯一通道。候选通过网关自主决定调用次数与编排（多阶段推理），
# 每次调用强制记账（tag/延迟/字符或 token），runner 汇总进评分账。
# 隔离纪律：网关持有 adapter 与 per-turn directive（mock/echo 测试替身的台本，由 runner 设置），
# 候选拿不到 case、gold、未来事件——只能拿到当轮 turn_ctx。
#
# 适配器统一签名（v2）：generate(system, user, payload) -> {"text", "finish", "usage"}
#   payload = {"images": [ref...], "model": str|None, "temperature": float|None,
#              "timeout_s": int, "directive": dict}   # directive 仅测试替身使用

import datetime
import json
import os

import lb_assembly
import api_candidate

# 流式测量开关（A 方案）：默认沿用历史非流式配置，开启即产生**新的实际 API 配置身份**，
# 必须与历史完成耗时结果分开报告，不与旧批拼接。
STREAM_CONFIG_IDENTITY = {
    "non_stream": "api_cfg:openai_compat_stream_false_v1",
    "stream": "api_cfg:openai_compat_stream_true_v1(include_usage gated)",
}


def stream_policy():
    """返回 (stream: bool, include_usage: bool)。默认 (False, False)=历史非流式配置。"""
    s = os.environ.get("LB_STREAM_TIMING", "0").strip().lower() in ("1", "true", "yes", "on")
    u = os.environ.get("LB_STREAM_USAGE", "1").strip().lower() in ("1", "true", "yes", "on")
    return s, (s and u)


# ---------- 适配器 ----------

class MockAdapter:
    """确定性假适配器：返回 directive.mock_response 或内置合法 JSON。"""
    name = "mock"

    def generate(self, system: str, user: str, payload: dict) -> dict:
        d = payload.get("directive") or {}
        text = d.get("mock_response") or json.dumps({
            "response": {"recommended": "我一般挑悬疑片，看到最后还得回想一下。你呢",
                         "bad_boy": "你猜我看哪种，猜对有奖",
                         "playful": "悬疑片重度用户，要不要交换片单",
                         "warm": "看得挺杂的，你平时爱看哪种呀"},
            "directions": ["你呢，平时看哪种", None, "最近有部悬疑不错", None],
            "analysis": {"topic_status": "new", "topic_label": "电影",
                         "scene_facts": [{"text": "她今晚要加班", "source_ids": ["m0"]},
                                         {"text": "她问我周末看什么类型电影", "source_ids": ["m2"]}],
                         "ongoing": []}
        }, ensure_ascii=False)
        return {"text": text, "finish": "stop", "usage": None}


class OpenAICompatAdapter:
    """OpenAI 兼容适配器（本地 dsf / gpt-oss-120b / DeepSeek 官方等）。
    LB_API_BASE / LB_API_KEY / LB_MODEL 环境变量；usage 存在时用真实 token。
    视觉：payload.images 里的 ref（data:/http: 直用；本地路径读文件转 base64）。"""
    name = "openai_compat"

    def __init__(self):
        self.base = os.environ.get("LB_API_BASE", "").rstrip("/")
        self.key = os.environ.get("LB_API_KEY", "")
        self.model = os.environ.get("LB_MODEL", "gpt-oss-120b")
        if not self.base:
            raise RuntimeError("LB_API_BASE 未设置")

    @staticmethod
    def _image_url(ref: str) -> str:
        if ref.startswith(("data:", "http://", "https://")):
            return ref
        import base64
        import mimetypes
        mime = mimetypes.guess_type(ref)[0] or "image/png"
        with open(ref, "rb") as f:
            return "data:%s;base64,%s" % (mime, base64.b64encode(f.read()).decode())

    def generate(self, system: str, user: str, payload: dict) -> dict:
        import urllib.request
        from lb_stream_timing import StreamTimer, consume_sse_lines
        content = [{"type": "text", "text": user}]
        for ref in payload.get("images") or []:
            content.append({"type": "image_url", "image_url": {"url": self._image_url(ref)}})
        if len(content) == 1:
            user_msg = {"role": "user", "content": user}
        else:
            user_msg = {"role": "user", "content": content}
        # 2026-10-18 修复：temperature=None 时旧写法 payload.get("temperature",0.7)
        # 因键存在返回 None，出站 JSON 实为 "temperature": null（服务端默认温度，未知值）。
        # 现显式归一：None → 0.7，且 CallLog 记录实际出站值（判决令：记录实际请求与返回）。
        _t = payload.get("temperature")
        stream = bool(payload.get("stream"))
        stream_options = payload.get("stream_options") if stream else None
        # 模型服务边界（refs/arena_v1/SCOPE_DECISION_API_CANDIDATE_20261022.md）：
        # 出站请求体经唯一白名单构造——tools/functions/tool_choice 等通道
        # 在这里就构造不出来（BoundaryViolation），不是"记得别发"。
        body_dict = api_candidate.build_request_body(
            [{"role": "system", "content": system}, user_msg],
            model=payload.get("model") or self.model,
            temperature=_t, stream=stream)
        if stream_options is not None:
            body_dict["stream_options"] = stream_options
            api_candidate.check_request_body(body_dict)      # 仍过白名单核验
        body = json.dumps(body_dict).encode("utf-8")
        req = urllib.request.Request(
            self.base + "/chat/completions", data=body,
            headers={"Content-Type": "application/json",
                     "Authorization": "Bearer " + self.key})
        timeout = payload.get("timeout_s", 120)
        timer = StreamTimer()
        timer.stream_requested = stream
        timer.stream_options_sent = stream_options is not None
        timer.start_request()
        try:
            for _attempt in range(4):  # 429 retry with exponential backoff
                try:
                    with urllib.request.urlopen(
                            req, timeout=timeout if isinstance(timeout, (int, float)) else 120) as r:
                        timer.on_headers()
                        if not stream:
                            raw_bytes = r.read()
                            timer.on_bytes(len(raw_bytes))
                            data = json.loads(raw_bytes.decode("utf-8"))
                            timer.finish()
                            choice = data["choices"][0]
                            out = {"text": choice["message"]["content"],
                                   "finish": choice.get("finish_reason", ""),
                                   "usage": data.get("usage"),
                                   "resp_model": data.get("model"), "resp_id": data.get("id")}
                            timer.usage_source = "api_usage" if data.get("usage") else "absent"
                            out["timing"] = dict(timer.as_dict(),
                                                 first_content_ms=None,
                                                 first_content_reason="non_stream_single_shot")
                            return out
                        ans = consume_sse_lines(r, timer, usage_authorized=stream_options is not None)
                        timer.finish()
                    break  # success, exit retry loop
                except urllib.error.HTTPError as he:
                    if he.code == 429 and _attempt < 3:
                        import time as _time
                        wait = 5 * (2 ** _attempt)  # 10s, 20s, 40s
                        timer.finish(error="429 retry %d/%d, waiting %ds" % (_attempt + 1, 3, wait))
                        _time.sleep(wait)
                        # Rebuild request (connection may be stale)
                        req = urllib.request.Request(self.base + "/chat/completions", data=body,
                                                     headers={"Content-Type": "application/json",
                                                              "Authorization": "Bearer " + self.key})
                        continue
                    raise
        except Exception as e:
            timer.finish(error="%s: %s" % (type(e).__name__, e))
            raise
        out = dict(ans)
        out["timing"] = timer.as_dict()
        return out


class EchoAdapter:
    """回声适配器（Phase 0 协议测试替身）：验证"机制组织逻辑"而非模型智商。
    行为（全部确定性）：
      - directive.keywords：每个关键词，若出现在 user prompt 中 → 进入 recommended/directions
        （考"机制有没有把该记忆注入 prompt"——注入了才会被回声吐回来）；
      - directive.decline_when：任一关键词在 prompt 中 → 输出 decline JSON（静默轮协议）；
      - directive.facts / directive.ongoing：作为 analysis 写回素材（模拟"正确抽取的模型"），
        走机制 update_state 入记忆——考"存储→检索→再注入"全链路。
    这是测试替身：台本由 runner 设置，候选不可见。真实模型质量评估归后续阶段。"""
    name = "echo"

    def generate(self, system: str, user: str, payload: dict) -> dict:
        d = payload.get("directive") or {}
        decline_hits = [kw for kw in (d.get("decline_when") or []) if kw in user]
        if decline_hits:
            out = {"response": {"recommended": None, "bad_boy": None,
                                 "playful": None, "warm": None},
                   "decline_reason": d.get("decline_reason")
                   or ("不建议发送：命中静默条件 " + ";".join(decline_hits)),
                   "directions": [None] * 4,
                   "analysis": {"topic_status": "end", "topic_label": "收尾",
                                "scene_facts": d.get("facts") or [],
                                "ongoing": d.get("ongoing") or []}}
            return {"text": json.dumps(out, ensure_ascii=False), "finish": "stop", "usage": None}

        kws = [kw for kw in (d.get("keywords") or []) if kw in user]
        if kws:
            rec = "记得：" + "、".join(kws)
            resp = {"recommended": rec, "bad_boy": "我记性可好了",
                    "playful": "考考你我还记得啥", "warm": "都记着呢"}
            dirs = [rec, None, "你还记得吗", None]
        else:
            rec = "没什么特别要提的"
            resp = {"recommended": rec, "bad_boy": "忘了也没事",
                    "playful": "随便聊聊", "warm": "慢慢聊"}
            dirs = [rec, None, "想起来再说", None]
        out = {"response": resp, "directions": dirs,
               "analysis": {"topic_status": "same", "topic_label": "echo",
                            "scene_facts": d.get("facts") or [],
                            "ongoing": d.get("ongoing") or []}}
        return {"text": json.dumps(out, ensure_ascii=False), "finish": "stop", "usage": None}


ADAPTERS = {"mock": MockAdapter, "openai_compat": OpenAICompatAdapter, "echo": EchoAdapter}

# 离线适配器白名单（零真实上游、零成本、零外部调用）：mock=固定打桩，
# echo=确定性协议替身。只有它们可以在没有授权级预算账本的情况下派发；
# 任何其他适配器（含未来新增）默认必须挂预算权威——这是 fail-closed 的
# 允许清单，不是需要逐项登记的黑名单。
OFFLINE_ADAPTERS = frozenset({"mock", "echo"})


# ---------- 网关 ----------

class LedgerWriteError(RuntimeError):
    """attempt_started 落盘失败——硬边界：此异常必须在任何上游派发之前抛出
    （V14 独立复核 §5.3：写账失败零请求派发）。"""


def append_jsonl_checked(path, obj):
    """追加一行并确认已持久化；任何写失败向上抛（调用方决定停批）。"""
    d = os.path.dirname(path)
    if d:
        os.makedirs(d, exist_ok=True)
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(obj, ensure_ascii=False, default=str) + "\n")
        f.flush()
        os.fsync(f.fileno())


class ModelGateway:
    """考场统一模型网关。候选唯一调模型通道；强制记账。
    v3（2026-10-18 第五波判决 §9）：event_log_path 非空时，每次调用尝试（成功或失败）
    即刻追加 JSONL 事件——此前失败只进内存、异常向上抛、runner 未及落盘 = 失败账丢失。"""

    def __init__(self, adapter, event_log_path=None):
        self._adapter = adapter
        self.calls = []          # 本轮 CallLog（runner 每轮重置）
        self.directive = {}      # 测试替身台本（runner 设置；真实适配器忽略）
        self.event_log_path = event_log_path
        self._attempt_seq = 0
        # 第六波判决 §6.3：审计上下文（runner 每轮设置；事件带 run_id/case_id 可配对）
        self.run_id = ""
        self.case_id = ""
        self.event_log_errors = []   # 事件日志写失败不静默（汇总进 manifest 可见）
        # V14 §5.3：完成/失败事件写失败 → 未决状态；代理据此停批（不得当成功）
        self.pending_settlement = False

    def set_run_context(self, run_id: str, case_id: str):
        self.run_id = run_id
        self.case_id = case_id

    @property
    def adapter_name(self):
        return self._adapter.name

    def _log_event(self, rec, hard=False):
        """hard=True：attempt_started——写失败必须抛 LedgerWriteError（零派发）。
        hard=False：完成/失败账——写失败记 pending_settlement + 错误清单。"""
        if not self.event_log_path:
            return
        try:
            append_jsonl_checked(self.event_log_path, rec)
        except OSError as e:
            msg = "%s: %s" % (type(e).__name__, e)
            self.event_log_errors.append(msg)
            if hard:
                raise LedgerWriteError("attempt_started 落盘失败，拒绝派发: %s" % msg)
            self.pending_settlement = True

    def _audit_base(self, attempt_id, kind):
        return {"kind": kind, "attempt_id": attempt_id,
                "run_id": self.run_id, "case_id": self.case_id,
                "pid": os.getpid(),
                "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")}

    def generate(self, system: str, user: str, images=None, model=None,
                 temperature=None, timeout_s=None, tag="main",
                 attempt_id=None) -> dict:
        # LB_API_TIMEOUT_S 环境覆盖（默认 120 不变）：长上下文 live 跑批的运维旋钮，
        # 只影响读超时，不改模型/采样/协议。
        if timeout_s is None:
            timeout_s = int(os.environ.get("LB_API_TIMEOUT_S", "120"))
        """attempt_id：可信代理传入的全局唯一 ID（跨 resume 连续）；
        缺省时退化为进程内序号（仅离线单元探针使用）。"""
        payload = {"images": images or [], "model": model,
                   "temperature": temperature, "timeout_s": timeout_s,
                   "directive": self.directive}
        # 测量配置身份（2026-10-25 A 方案）：流式由网关环境开关决定，候选机制与冻结身份不动。
        stream_on, with_usage = stream_policy()
        payload["stream"] = stream_on
        payload["stream_options"] = ({"include_usage": True}
                                     if (stream_on and with_usage) else None)
        self._attempt_seq += 1
        if attempt_id is None:
            attempt_id = self._attempt_seq
        t0 = datetime.datetime.now()
        # 第六波判决 §6.3 + V14 §5.3：HTTP 开始前先记 attempt_started；
        # 写失败=LedgerWriteError（硬边界：零请求派发），不进 try 块。
        self._log_event(dict(self._audit_base(attempt_id, "attempt_started"),
                             adapter=self._adapter.name, tag=tag,
                             model=model or os.environ.get("LB_MODEL", "") or "mock",
                             temperature=(0.7 if temperature is None else temperature)),
                        hard=True)
        try:
            res = self._adapter.generate(system, user, payload)
        except Exception as e:
            # 判决令 §10.1：失败/重试也要留账（此前适配器抛错时 calls 未 append，失败账丢失）
            lat = int((datetime.datetime.now() - t0).total_seconds() * 1000)
            rec = {
                "tag": tag, "ts": t0.strftime("%H:%M:%S"), "latency_ms": lat,
                "prompt_chars": 0, "reply_chars": 0, "usage": {"source": "error"},
                "system": system, "user": user,
                "temperature": (0.7 if temperature is None else temperature),
                "images": list(images or []),
                "error": "%s: %s" % (type(e).__name__, e)}
            self.calls.append(rec)
            # 第五波判决 §9：失败事件即刻持久化（不等 runner 成功路径）
            self._log_event(dict(self._audit_base(attempt_id, "attempt_failed"),
                                 adapter=self._adapter.name,
                                 tag=tag, latency_ms=lat,
                                 temperature=rec["temperature"],
                                 images=rec["images"],
                                 system=system, user=user,
                                 error=rec["error"]))
            raise
        lat = int((datetime.datetime.now() - t0).total_seconds() * 1000)
        usage = res.get("usage")
        if usage:
            # 宪章 C3（RESEARCH-P1）：缓存命中必须单独记账。不同端点字段名不同：
            # DeepSeek 系 = prompt_tokens_details.cached_tokens；旧口径 = prompt_cache_hit_tokens。
            _cache_hit = usage.get("prompt_cache_hit_tokens")
            if _cache_hit is None:
                _ptd = usage.get("prompt_tokens_details") or {}
                _cache_hit = _ptd.get("cached_tokens")
            u = {"prompt": usage.get("prompt_tokens"),
                 "completion": usage.get("completion_tokens"),
                 "cache_hit": _cache_hit,
                 "cache_miss": usage.get("prompt_cache_miss_tokens"),
                 "source": "api_usage"}
        else:
            u = {"prompt": lb_assembly.kotlin_len(system) + lb_assembly.kotlin_len(user),
                 "completion": lb_assembly.kotlin_len(res["text"]),
                 "cache_hit": None, "cache_miss": None, "source": "kotlin_len_chars"}
        self.calls.append({
            "tag": tag, "ts": t0.strftime("%H:%M:%S"), "latency_ms": lat,
            "prompt_chars": u["prompt"], "reply_chars": u["completion"], "usage": u,
            # V6 证据链：完整入参落盘（验收断言 gold 不泄漏、回复不冒充已发送）
            "system": system, "user": user,
            # 判决令 2026-10-18：实际出站参数与原始响应落盘（区分参数未生效/服务端非确定性）
            "temperature": (0.7 if temperature is None else temperature),
            "images": list(images or []),
            "resp_model": res.get("resp_model"), "resp_id": res.get("resp_id"),
            "finish": res.get("finish", ""),
            # 分段计时（能力件）：流式适配器给出 headers/first_content/first_usable 等
            "timing": res.get("timing"),
            "raw_text": res.get("text", "")})
        self._log_event(dict(self._audit_base(attempt_id, "attempt_completed"),
                             adapter=self._adapter.name,
                             tag=tag, latency_ms=lat,
                             temperature=self.calls[-1]["temperature"],
                             images=self.calls[-1]["images"],
                             system=system, user=user,
                             resp_model=res.get("resp_model"),
                             resp_id=res.get("resp_id"),
                             finish=res.get("finish", ""),
                             # R3#8（G11）：完整 usage 三件套入账
                             usage=res.get("usage"),
                             raw_text=res.get("text", "")))
        return res

    def turn_usage(self) -> dict:
        """汇总本轮全部调用（多阶段编排 = 多条 CallLog）。
        第五波判决 §9：失败调用 usage 无 prompt/completion 键（source=error），
        汇总必须容错——未知记 None 并标注 has_unknown_usage，不抛 KeyError、不冒充 0 成本。"""
        if not self.calls:
            return {"prompt": 0, "completion": 0, "cache_hit": None,
                    "cache_miss": None, "source": "none", "n_calls": 0,
                    "has_unknown_usage": False}

        def _num(c, k):
            v = (c.get("usage") or {}).get(k)
            return v if isinstance(v, (int, float)) else None

        prompts = [_num(c, "prompt") for c in self.calls]
        comps = [_num(c, "completion") for c in self.calls]
        unknown = any(v is None for v in prompts + comps)
        srcs = sorted({str((c.get("usage") or {}).get("source")) for c in self.calls})
        return {"prompt": sum(v for v in prompts if v is not None),
                "completion": sum(v for v in comps if v is not None),
                "cache_hit": None, "cache_miss": None,
                "source": srcs[0] if len(srcs) == 1 else "mixed:" + "+".join(srcs),
                "n_calls": len(self.calls),
                "has_unknown_usage": unknown}
