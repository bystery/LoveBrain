# -*- coding: utf-8 -*-
"""integration_preview/engine_adapter.py — LoveBrain 新旧引擎切换适配原型（arena 侧，零真实调用、不改生产）。

作用：让 v5 真实对照结束后不必从零读产品接口。它把本考场 harness 的机制接口
(init/run_turn/snapshot/restore) 适配成 D:/LoveBrain 产品侧那 5 类职责的形状，
并用**录制/替身 I/O**证明接口与恢复行为。这是可复用接入积木，不是产品体验验收，
也不是把整座考场搬进产品（依 v5_independent_review_20261025 §4）。

对齐到的真实产品入口（只读定位，不假定等价）：
  domain/PromptBuilder.kt        buildReplyUserPromptWithRefs / buildSystemPrompt / buildTimestampPrompt
  domain/IntentPolicy.kt         effectiveStatus(expiry,status)（旧终态归一化为非 ACTIVE）
  data/DeepSeekRepository.kt     generateRaw / generateRawWithMetadata（AiGateway）
  data/KnowledgeRepository.kt    画像/知识读写、incrementTurnCount
  data/KnowledgeMemoryStore.kt   MemoryStorage.read/writeTransaction/corrections

五个接口对应下方五个类，逐个用替身 I/O 在 replay_fixture 里跑通。
"""
import importlib.util
import json
import os
import unicodedata

ARENA = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CANDS = os.path.join(ARENA, "candidates")

ENGINE_NEW = "v5_purpose_aligned"        # 新引擎：简单事件记忆 + 目标作用域 + 空 analysis
ENGINE_OLD = "reference_product_v1"      # 原版引擎：可回退
_ROLE2SPK = {"HER": "PARTNER", "ME": "USER", "IDEA": "ADVISOR_NOTE"}

# v5 出站协议四个 response 风格槽（format.md）。仅用于消费端的"键名匹配"，不改写值。
RESPONSE_STYLE_SLOTS = ("recommended", "bad_boy", "playful", "warm")


def _norm_key(name):
    """协议键【名称】容错：去掉不可见/仅格式字符（Unicode 类别 Cf，如 U+200B/U+200C/U+FEFF）
    与首尾空白，只用于匹配必需键名，绝不改动值——对齐"API 响应只是数据、不 eval、不补问修 JSON、
    不臆造默认"（AGENTS §6）。真实付费证据里 `bad_bo`+U+200B+`y` 让 JSON 可解析但必需键丢失；
    这是消费侧的【可审计适配】，每处归一都由 _normalize_key_names 记进审计，而非静默修正。"""
    if not isinstance(name, str):
        return name
    kept = "".join(ch for ch in name if unicodedata.category(ch) != "Cf")
    return kept.strip()


def _normalize_key_names(container, slot_prefix, audit):
    """把一层 dict 的键名按 _norm_key 归一后返回 {归一名: 原值}；值字节保持不变。
    每个需要归一的键写入审计(original_repr / normalized / slot_affected)。归一后撞名时
    记录冲突、不静默合并也不丢原值（调用方仍从原始 container 取全部选项）。"""
    out = {}
    if not isinstance(container, dict):
        return out
    for k, v in container.items():
        nk = _norm_key(k)
        if nk != k:
            audit.append({"original_repr": repr(k), "normalized": nk,
                          "slot_affected": "%s%s" % (slot_prefix, nk),
                          "action": "key-name normalized (values untouched)"})
        if nk in out:
            # 归一后重名：登记、保留首个，不臆造合并；原始值仍由上层从原对象完整取回。
            audit.append({"original_repr": repr(k), "normalized": nk,
                          "slot_affected": "%s%s" % (slot_prefix, nk),
                          "action": "collision after normalization (kept first; not merged)"})
            continue
        out[nk] = v
    return out


def _load_engine(name):
    spec = importlib.util.spec_from_file_location(
        "engine_%s" % name, os.path.join(CANDS, name, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


# ── 接口①：真实事件归一 + 草稿与已发送分开 + 私密备注结构性隔离 ──────────
class Event(object):
    # kind 是结构边界（不靠下游字符串匹配）：
    #   "chat" = 真实聊天消息，可进入本轮已发送历史与事件存储；
    #   "note" = 军师私密备注，仅本轮约束通道，永不作为聊天消息、永不落库。
    __slots__ = ("speaker", "text", "ts", "committed", "kind")

    def __init__(self, speaker, text, ts, committed=True, kind=None):
        self.speaker, self.text, self.ts, self.committed = speaker, text, ts, committed
        if kind is None:
            # 未显式给 kind：ADVISOR_NOTE 天然为 note；其余为 chat（草稿 committed=False 仍是
            # chat 类型，只是本轮不作为已发送历史——与 note 边界是两回事）。
            kind = "note" if speaker == "ADVISOR_NOTE" else "chat"
        if kind not in ("chat", "note"):
            raise ValueError("未知 event kind: %r" % (kind,))
        self.kind = kind

    @property
    def is_note(self):
        return self.kind == "note"

    def to_msg(self):
        # 只有真实聊天事件才还原成 harness 输入（HER/ME）。note 类型没有合法的聊天表示——
        # 拒绝而不是把它当 ME/USER 泄露（结构性边界）；committed=False 的草稿不作为已发送历史。
        if self.kind == "note":
            raise ValueError("note-kind 事件不能作为聊天消息（私密备注只走 note 通道）")
        role = "HER" if self.speaker == "PARTNER" else "ME"
        return {"role": role, "text": self.text, "ts": self.ts}

    def to_note(self):
        return self.text


def normalize_events(raw):
    """产品聊天消息 → Event 归一；未知 speaker 拒绝而不猜。
    kind 由 speaker 显式决定：IDEA/ADVISOR_NOTE→note（仅本轮约束），HER/ME→chat（真实历史）。"""
    out = []
    for m in raw:
        spk = _ROLE2SPK.get(m.get("role"))
        if spk is None:
            raise ValueError("未知 speaker: %r" % m.get("role"))
        kind = "note" if spk == "ADVISOR_NOTE" else "chat"
        out.append(Event(spk, m.get("text", ""), m.get("ts", ""),
                         committed=m.get("committed", True), kind=kind))
    return out


# ── 接口②：当前意图/note/画像输入；关闭≠删真实约定 ──────────────────────
class IntentInput(object):
    def __init__(self, enabled=False, text="", status="ACTIVE"):
        self.enabled, self.text, self.status = enabled, text, status

    @staticmethod
    def effective_status(status):
        # 对齐 IntentPolicy.effectiveStatus：旧终态(COMPLETED/EXPIRED/PAUSED)归一为非 ACTIVE
        return "ACTIVE" if status in ("ACTIVE", None, "") else "OFF"

    def to_harness(self):
        if not self.enabled or not (self.text or "").strip():
            return None
        status = self.effective_status(self.status)
        # 对齐 IntentPolicy：旧终态(COMPLETED/EXPIRED/PAUSED)归一为非 ACTIVE → 当前目标关闭，
        # 有效意图只在 ACTIVE 时进入请求；关闭/替换不误删真实约定由 EventStore 的历史续用保证。
        if status != "ACTIVE":
            return None
        return {"enabled": True, "text": self.text, "status": status}


# ── 接口③：事件存储、故事/联系人隔离、snapshot/restore、新旧互不覆盖 ──────
class EventStore(object):
    """按 (engine, contact_id) 隔离存储；新旧引擎目录不同，互不覆盖。"""

    def __init__(self, root):
        self.root = root

    def _dir(self, engine, contact_id, story):
        return os.path.join(self.root, engine, "%s__%s" % (contact_id, story))

    def snapshot_path(self, engine, contact_id, story):
        return os.path.join(self._dir(engine, contact_id, story), "state.bin")

    def save(self, engine, contact_id, story, blob):
        p = self.snapshot_path(engine, contact_id, story)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        tmp = p + ".tmp"
        with open(tmp, "wb") as f:
            f.write(blob)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, p)
        return p

    def load(self, engine, contact_id, story):
        p = self.snapshot_path(engine, contact_id, story)
        return open(p, "rb").read() if os.path.isfile(p) else None

    def exists_other_engine(self, engine, contact_id, story):
        """新旧互不覆盖：检查另一引擎是否已有该联系人/故事状态（不共用同一份）。"""
        other = ENGINE_OLD if engine == ENGINE_NEW else ENGINE_NEW
        return os.path.isfile(self.snapshot_path(other, contact_id, story))


# ── 接口④：单次请求装配 + 返回文本消费；空 analysis 合法、不触发旧摘要写回/清库 ──
class OutputConsumer(object):
    """消费一次生成：解析可发选项（response 四风格 / directions）；
    新引擎 analysis 为空对象——据 §4.4，空 analysis 不得被当损坏去清记忆或触发旧摘要更新。"""

    @staticmethod
    def parse(reply_text):
        """消费一次生成：绝不因合法 JSON 的形状变体而崩溃；分类 ok / partially-consumable /
        unconsumable-schema；原始文本字节不变（raw）；只在匹配必需协议【键名】时容忍不可见/首尾
        空白字符（值不动），并把每处归一写进 key_normalization_audit。对齐宪法：API 响应只是数据——
        只 json.loads、不 eval、不补问让模型修 JSON、不臆造默认值。"""
        raw = reply_text  # 原始模型文本，逐字节保留
        audit = []
        try:
            obj = json.loads(reply_text)
        except (json.JSONDecodeError, TypeError, ValueError):
            # 严格 json.loads；坏则不 eval、不猜、不补问（§6 API 只当数据）。
            return {"ok": False, "status": "unconsumable-schema", "raw": raw,
                    "options": [], "analysis": None, "empty_analysis": False,
                    "corrupt": True, "reason": "not valid JSON",
                    "schema_notes": ["json decode error"], "slots": {},
                    "key_normalization_audit": audit}

        if not isinstance(obj, dict):
            # 合法 JSON 但不是对象形状（如顶层数组 [] 或标量）：无法读任何协议槽——原始文本仍保留。
            return {"ok": False, "status": "unconsumable-schema", "raw": raw,
                    "options": [], "analysis": None, "empty_analysis": False,
                    "corrupt": True,
                    "reason": "top-level JSON is %s, not object" % type(obj).__name__,
                    "schema_notes": ["top-level is %s, not object" % type(obj).__name__],
                    "slots": {}, "key_normalization_audit": audit}

        # 归一顶层协议键名（response/directions/analysis），只匹配名称、不改值，逐处记审计。
        top = _normalize_key_names(obj, "", audit)
        schema_notes = []
        options = []

        resp = top.get("response")
        if resp is None:
            resp = {}
        if isinstance(resp, dict):
            # 四个 response 风格槽：用归一键名匹配（治 `bad_bo`+U+200B+`y` 丢槽），值字节不动。
            slots_norm = _normalize_key_names(resp, "response.", audit)
            slots = {k: slots_norm.get(k) for k in RESPONSE_STYLE_SLOTS}
            # 选项仍从【原始】对象完整取，绝不因归一/撞名丢内容，也不新增校验放宽正文。
            options += [v for v in resp.values() if isinstance(v, str) and v.strip()]
        else:
            slots = {}
            schema_notes.append("response is %s, not object — style slots unreadable"
                                % type(resp).__name__)

        dirs = top.get("directions")
        if dirs is None:
            dirs = []
        if isinstance(dirs, list):
            options += [v for v in dirs if isinstance(v, str) and v.strip()]
        else:
            schema_notes.append("directions is %s, not list — direction slots skipped"
                                % type(dirs).__name__)

        analysis = top.get("analysis")
        # 空 analysis（{} 或 None）：合法，仅表示"本轮不消费内部摘要"，非损坏。
        empty_analysis = (analysis is None) or (isinstance(analysis, dict) and not analysis)

        if not schema_notes:
            status, ok, corrupt = "ok", True, False
            reason = ""
        elif options or isinstance(analysis, (dict, type(None))) or dirs == []:
            # 对象形状基本在，只是某个容器类型不对——能取的取，取不了的登记原因。
            status, ok, corrupt = "partially-consumable", False, False
            reason = "; ".join(schema_notes)
        else:
            status, ok, corrupt = "unconsumable-schema", False, True
            reason = "; ".join(schema_notes)

        return {"ok": ok, "status": status, "raw": raw, "options": options,
                "analysis": analysis, "empty_analysis": empty_analysis, "corrupt": corrupt,
                "reason": reason, "schema_notes": schema_notes, "slots": slots,
                "key_normalization_audit": audit}

    @staticmethod
    def memory_should_update(parsed):
        """是否应触发记忆写回：新引擎空 analysis→不写回；真实事件仍由 harness 保存，
        因此这里只关乎"旧摘要管线"，返回 False 表示不清库、不跑旧摘要更新。"""
        if not parsed.get("ok"):
            return False
        return not parsed.get("empty_analysis")


# ── 接口⑤：实验开关与回退边界 ─────────────────────────────────────────
class EngineRouter(object):
    def __init__(self, store_root, gateway):
        self.store = EventStore(store_root)
        self.gateway = gateway
        self._engines = {}

    def _engine(self, name):
        if name not in self._engines:
            self._engines[name] = _load_engine(name)
        return self._engines[name]

    def handle(self, engine, contact_id, story, persona, initial_kb, use_v5=True):
        """use_v5=False → 回退原版引擎（不切生产默认时用它）。"""
        chosen = engine if use_v5 else ENGINE_OLD
        mod = self._engine(chosen)
        h = mod.init(os.path.join(CANDS, chosen, "assets"),
                     {"episode_id": story, "persona": persona, "initial_kb": initial_kb},
                     None, self.store._dir(chosen, contact_id, story))
        return {"engine": chosen, "handle": h, "mod": mod,
                "contact_id": contact_id, "story": story}

    def send_turn(self, ctx, events, intent=None, note="", clock=None, may_decline=False):
        mod, h = ctx["mod"], ctx["handle"]
        # 结构边界：只有 committed 的 chat 事件进入本轮已发送历史（run_turn 会把它落进事件存储）。
        # note 类型（IDEA/ADVISOR_NOTE）永不进 sent/messages，因此永不被当作聊天历史、永不落库。
        chat = [e for e in events if e.kind == "chat"]
        note_events = [e for e in events if e.kind == "note"]
        sent = [e.to_msg() for e in chat if e.committed]     # 草稿(committed=False)与备注都不在此列
        # 私密军师备注=仅本轮约束通道：并入 run_turn 消费的 note 字段（临时、不写回真实消息、
        # 不进入下一轮历史）。显式 note 与 note-kind 事件文本按顺序合并。
        note_chunks = ([note] if note else []) + [e.to_note() for e in note_events]
        merged_note = "\n".join([c for c in note_chunks if c])
        tctx = {"case_id": "t", "turn_type": "reply",
                "turn_index": int(h.get("turn_count", 0)) + 1,
                "input": {"messages": sent, "clock": clock or (sent[-1]["ts"] if sent else ""),
                          "may_decline": may_decline,
                          "note": merged_note, "intent": intent.to_harness() if intent else None}}
        out = mod.run_turn(h, tctx, self.gateway)
        # 持久化快照（引擎自管 memory；EventStore 另存 harness 快照供跨进程/重启恢复）
        self.store.save(ctx["engine"], ctx["contact_id"], ctx["story"], mod.snapshot(h))
        parsed = OutputConsumer.parse(out["reply"])
        return {"reply_raw": out["reply"], "parsed": parsed,
                "memory_should_update": OutputConsumer.memory_should_update(parsed),
                "key_normalization_audit": parsed["key_normalization_audit"],
                "notes": out.get("notes", "")}

    def restore(self, ctx):
        blob = self.store.load(ctx["engine"], ctx["contact_id"], ctx["story"])
        if blob is None:
            return None
        return ctx["mod"].restore(self.store._dir(ctx["engine"], ctx["contact_id"], ctx["story"]), blob)
