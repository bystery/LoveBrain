# reference_chain.py — 冻结原产品（D:/LoveBrain）主回复链路 Python 参考移植
# ARENA_V1_CLOSEOUT 工作包 B（v0.13 指导书 §4-B2）。
#
# 定位：这是**比较基准**，不是被测候选——逐项引用 Kotlin 原实现行为，
# 预期值来自原仓库源码与其自带单元测试（基线保真 = 与原产品相符，
# 不是基线能通过全部能力题）。每个函数头注明原文件与行为依据。
#
# 范围（§4-B1）：HER/ME/IDEA 装配、持续意图、军师备注、场景判定、
# 记忆纠正、ongoing 选择、时效常量。不纳入：谈心、主动开场、悬浮窗、
# 复制发送 UI、完整发行、Kotlin 移植候选。
#
# 冻结来源版本：D:/LoveBrain 工作树 2026-10-21（散列见
# refs/arena_v1/BASELINE_PARITY.md §1）。

import json
import re

# ─────────────────────────── 常量（原 AppConfig.kt） ───────────────────────────

# AppConfig.kt L61-62, L70（原文件行号，冻结）
SCENE_CHAIN_MAX_HOURS = 2      # 超过 2h 的状态条目移入状态暂存
SCENE_CHAIN_MAX_ENTRIES = 2    # scene.md 最多保留 2 条
REPLY_MAX_MESSAGES = 60        # 回复 prompt 超过 60 条消息时掐尾保留最近

# OngoingContextSelector.kt L407-413 companion object
ONGOING_COOLDOWN_TURNS = 3     # 最近 N 轮已注入且无新证据 → 禁止再注入
ONGOING_DORMANT_TURNS = 5      # 连续 N 轮无新证据 → DORMANT


# ─────────────────────── 意图与军师备注（IntentIdeaBlock.kt） ───────────────────────

def is_intent_effective(config: dict) -> bool:
    """Models.kt L446-447：enabled && text.isNotBlank() && status == ACTIVE。
    新语义无期限；旧终态（COMPLETED/EXPIRED/PAUSED）由读口归一化为关闭后
    status != ACTIVE，在此防线处仍注不进提示词。"""
    return bool(config.get("enabled")) and bool((config.get("text") or "").strip()) \
        and config.get("status", "ACTIVE") == "ACTIVE"


def build_intent_block(config: dict) -> str:
    """IntentIdeaBlock.kt L30-33（逐字）。判据只有 isIntentEffective 一颗。"""
    if not is_intent_effective(config):
        return ""
    return "【持续意图】\n%s\n\n" % config["text"].strip()


# IntentIdeaBlock.kt L36（常量逐字）
NOTE_BLOCK_HEADER = "# 军师备注（用户补充，不是要发出去的话术）"


def build_advisor_note_block(note: str) -> str:
    """IntentIdeaBlock.kt L44-54（逐字）：正文 trim 后非空才出现；
    空正文给空串，prompt 里不留空标题。"""
    text = (note or "").strip()
    if not text:
        return ""
    return (NOTE_BLOCK_HEADER + "\n"
            + "「" + text + "」\n"
            + "这是用户给你的背景、限制、目的或提醒，只用来约束你这一轮怎么想、怎么回；"
            + "它不是要原样发给对方的消息，也不要当成用户已经说过的话来复述。\n"
            + "方案数量与输出格式按本任务既有的格式要求，不因为这段补充而改变。\n\n")


# ─────────────────── 对话记录围栏（ChatTranscriptBlock.kt L42-87） ───────────────────

_CHAT_HEADER = ("# 本次对话记录\n"
                "（按时间顺序。speaker 定义：PARTNER=对方，USER=用户本人。"
                "每条消息的 id 为来源ID，模型在 scene_facts 的 source_ids 中使用。）\n\n")


def render_chat_transcript(messages: list) -> dict:
    """ChatTranscriptBlock.kt render()（L42-87）语义：
    - IDEA 不进围栏、不占编号（L45：filter role != IDEA）；
    - 超过 REPLY_MAX_MESSAGES=60 条掐尾保留最近（L46-50），正文顶部留注记（L66-68），
      编号掐尾后从 m0 重新起（L51-63）；
    - 每条消息一行 JSON：{"id":"m<i>","speaker":"PARTNER|USER","text":<json 转义正文>}（L69-84）；
    - sourceAliasMap 只登记最终进入围栏的消息（L52-63）。
    messages 元素：{"id","role"("HER"|"ME"|"IDEA"),"content"}。"""
    chat_messages = [m for m in messages if m["role"] != "IDEA"]
    effective = (chat_messages[-REPLY_MAX_MESSAGES:]
                 if len(chat_messages) > REPLY_MAX_MESSAGES else chat_messages)
    source_alias_map = {}
    alias_by_id = {}
    body_lines = []
    for i, msg in enumerate(effective):
        alias = "m%d" % i
        source_alias_map[alias] = msg["id"]
        alias_by_id[msg["id"]] = alias
        speaker = "PARTNER" if msg["role"] == "HER" else "USER"
        body_lines.append(json.dumps({"id": alias, "speaker": speaker,
                                      "text": msg["content"]},
                                     ensure_ascii=False))
    body = "<chat>\n"
    if len(chat_messages) > REPLY_MAX_MESSAGES:
        body += ("（注：对话记录超过 %d 条，仅保留最近 %d 条）\n\n"
                 % (REPLY_MAX_MESSAGES, REPLY_MAX_MESSAGES))
    body += "".join(ln + "\n" for ln in body_lines) + "</chat>\n"
    return {"header": _CHAT_HEADER, "body": body, "sourceAliasMap": source_alias_map}


# ─────────────────── 场景判定（CurrentSceneInjection.kt L29-68） ───────────────────
# 优先级即语义（L16-18）：收尾 > 争执 > 解释或认错 > 主动邀约 > 认真沟通 >
# 轻松互逗 > 日常分享。词表为 Kotlin 源冻结逐字，不得"顺手排更合理的序"。

_CLOSING_KW = ["睡了", "睡觉", "晚安", "先忙", "去忙", "不聊了", "下次再聊",
               "明天再说", "去洗澡", "去洗漱", "先走了", "去吃饭"]
_CONFLICT_KW = ["生气", "烦死", "不想理", "随便你", "你总是", "你每次", "又来",
                "有意思吗", "懒得说", "你能不能", "为什么总是", "你到底",
                "不是你的错难道是我的错"]
_APOLOGY_KW = ["对不起", "抱歉", "我的错", "我错了", "原谅", "不应该",
               "是我不好", "是我没做好"]
_INVITE_KW = ["见面", "约", "一起", "出来", "去吃", "去看", "周末", "有空吗",
              "能不能", "方便吗"]
_SERIOUS_KW = ["难过", "不开心", "压力大", "焦虑", "想哭", "委屈",
               "不知道怎么办", "纠结", "在想", "其实我", "说实话", "心里"]
_PLAYFUL_KW = ["哈哈", "笑死", "233", "狗子", "笨蛋", "讨厌", "哼", "略略",
               "😏", "😂", "嘻"]


def infer_scene(messages: list) -> str:
    """CurrentSceneInjection.kt infer()（L29-68）逐语义移植：
    - 只看 HER/ME；无真实消息返回空串（L30-33）；
    - 收尾只看最后一条 HER（L34, L38-42）；
    - 争执/认真/互逗看全部真实消息（L35, L45-46, L60-65）；
    - 认错只看用户本人（ME）说的话（L19, L49-51）——对方说对不起不算；
    - 邀约只看最后一条 HER（L54-57）。"""
    real = [m for m in messages if m["role"] in ("HER", "ME")]
    if not real:
        return ""
    her_list = [m for m in real if m["role"] == "HER"]
    last_her = her_list[-1]["content"].lower() if her_list else None
    all_text = " ".join(m["content"] for m in real).lower()
    me_text = " ".join(m["content"] for m in real
                       if m["role"] == "ME").lower()
    if last_her and any(kw in last_her for kw in _CLOSING_KW):
        return "收尾"
    if any(kw in all_text for kw in _CONFLICT_KW):
        return "争执"
    if any(kw in me_text for kw in _APOLOGY_KW):
        return "解释或认错"
    if last_her and any(kw in last_her for kw in _INVITE_KW):
        return "主动邀约"
    if any(kw in all_text for kw in _SERIOUS_KW):
        return "认真沟通"
    if any(kw in all_text for kw in _PLAYFUL_KW):
        return "轻松互逗"
    return "日常分享"


def scene_block(scene: str) -> str:
    """CurrentSceneInjection.kt block()（L75-78，逐字）。"""
    if not scene or not scene.strip():
        return ""
    return "# 【当前场景】（本轮属性，不改变长期阶段档案）\n场景：%s\n\n" % scene


# ────────────── 记忆纠正（MemoryCorrectionPolicy.kt + KnowledgeMemoryStore.kt） ──────────────

ACTIONS = ("WRONG", "FINISHED", "MUTED", "WRONG_PERSON")

# MemoryCorrectionPolicy.kt L41-46（逐字）
_APPLIED_NOTICES = {
    "WRONG": "已标记为错误，下次生成将过滤此条记忆",
    "FINISHED": "已标记为结束，不再作为活跃事项",
    "MUTED": "已暂停提及，下次生成将过滤此条记忆",
    "WRONG_PERSON": "已隔离，不再注入此条记忆",
}
APPLY_FAILED = "纠正保存失败，请重试"          # L48
UNDONE = "已撤销纠正，该记忆恢复可信注入"       # L50
UNDO_FAILED = "撤销失败，请重试"               # L53
ROUND_MUTE_APPLIED = "已暂停本轮提及，下次生成将过滤此条记忆"   # L36
ROUND_MUTE_UNDONE = "已撤销本轮暂停，该记忆恢复注入"           # L38


def is_transient_round_mute(action: str, duration: str) -> bool:
    """MemoryCorrectionPolicy.kt L22-23：只有 MUTED + THIS_ROUND 走瞬时路径；
    其余纠正一律持久化（重启后"已纠正的记忆又回来"防线）。"""
    return action == "MUTED" and duration == "THIS_ROUND"


class RoundCorrectionStore:
    """MemoryCorrectionPolicy.kt L62-81：本轮瞬时 mute 存储；
    snapshot() 必须是副本（L66-67：调用方改不动存储本体）。"""

    def __init__(self):
        self._entries = {}

    def snapshot(self) -> dict:
        return dict(self._entries)          # 副本语义（L67）

    def put(self, memory_id, correction):
        self._entries[memory_id] = correction   # L69-71 覆盖同 id

    def remove(self, memory_id) -> bool:
        return self._entries.pop(memory_id, None) is not None   # L74

    def clear(self):
        self._entries.clear()

    def is_empty(self) -> bool:
        return not self._entries


class ReferenceMemoryStore:
    """KnowledgeMemoryStore.kt L53-141 移植：corrections/save/undo +
    库级 revision 单调性（L39-51）。
    storage 由测试注入（in-memory），实现 read/kbExists/timestamp/writeTransaction/
    decode/encode 六能力（L20-36），锁与路径由外层负责（L50-52）——移植保持同界。"""

    CORRECTIONS_FILE = "memory/corrections.json"
    MEMORY_REVISION_FILE = "memory/.revision"

    def __init__(self, storage):
        self._storage = storage

    def corrections(self, kb) -> dict:
        raw = self._storage.read(kb, self.CORRECTIONS_FILE)
        if not raw.strip():
            return {}
        try:
            recs = json.loads(raw)
        except json.JSONDecodeError:
            return {}                       # L133：JSON 坏 → 空表不异常
        return {r["memoryId"]: r for r in recs}

    def revision_of(self, kb) -> int:
        raw = self._storage.read(kb, self.MEMORY_REVISION_FILE).strip()
        try:
            return int(raw)
        except ValueError:
            return 0                        # L64-65：无标记文件为 0

    def save(self, kb, memory_id, action, replacement_text="",
             target_kb_id="", mute_duration="UNTIL_RESTORE") -> bool:
        if not self._storage.kb_exists(kb):
            return False                    # L80
        next_rev = self.revision_of(kb) + 1  # L81：读持久化标记，非剩余 max
        now = self._storage.timestamp()
        correction = {"memoryId": memory_id, "action": action,
                      "replacementText": replacement_text,
                      "targetKbId": target_kb_id, "revision": next_rev,
                      "updatedAt": now,
                      "muteDuration": mute_duration,
                      "muteTimestamp": now if action == "MUTED" else ""}  # L91
        updated = dict(self.corrections(kb))
        updated[memory_id] = correction     # L93
        return self._persist(kb, list(updated.values()), next_rev)

    def undo(self, kb, memory_id) -> bool:
        if not self._storage.kb_exists(kb):
            return False                    # L104
        current = self.corrections(kb)
        if memory_id not in current:
            return False                    # L106
        next_rev = self.revision_of(kb) + 1  # 撤销也递增（L100-102：0→1→0 防线）
        updated = dict(current)
        del updated[memory_id]
        return self._persist(kb, list(updated.values()), next_rev)

    def _persist(self, kb, records, revision) -> bool:
        # L118-128：纠正文件与 revision 同一写事务；纠正写失败绝不写 revision
        written = False
        self._storage.write_transaction(kb, records, revision, self)
        written = self._storage.last_tx_written
        if not written:
            self._storage.note("memory write refused or failed: kb=%s revision=%d"
                               % (kb, revision))
        return written


# ──────────── ongoing 选择（OngoingContextSelector.kt L35-414） ────────────

def parse_plan_items(content: str) -> list:
    """OngoingContextSelector.kt parsePlanItems()（L300-321）：
    兼容 itemId~name|status|chain 与 name|status|chain；无 | 行跳过。"""
    items = []
    for line in (content or "").splitlines():
        t = line.strip()
        if "|" not in t:
            continue
        parts = [p.strip() for p in t.split("|")]
        if len(parts) < 3 or not parts[0]:
            continue
        first = parts[0]
        idx = first.find("~")
        name = first[idx + 1:] if idx > 0 else first
        status = parts[1]
        chain = "|".join(parts[2:]).strip()
        items.append({"name": name, "status": status, "chain": chain,
                      "itemStatus": derive_item_status(status),
                      "eventDate": extract_event_date(name),
                      "lastInjectedTurn": -1, "lastEvidenceTurn": -1})
    return items


def derive_item_status(status: str) -> str:
    """OngoingContextSelector.kt deriveItemStatus()（L325-333）：
    已完成→FINISHED / 已取消→CANCELLED / 进行中|新出现→ACTIVE / 其他→ACTIVE。
    DORMANT 不在此推导（由冷却+轮差在 select 时推导，L324）。"""
    s = (status or "").strip()
    if s == "已完成":
        return "FINISHED"
    if s == "已取消":
        return "CANCELLED"
    return "ACTIVE"                          # 进行中/新出现/其他


def extract_keywords(name: str) -> list:
    """OngoingContextSelector.kt extractKeywords()（L266-294）逐语义移植。"""
    cleaned = re.sub(r"[（）()【】\[\]「」\"'·]", "", name or "")
    cleaned = re.sub(r"^(计划|约|定于|准备)", "", cleaned).strip()
    cleaned = re.sub(r"\d{1,2}[-/]\d{1,2}", "", cleaned)
    cleaned = re.sub(r"周[一二三四五六日天]", "", cleaned)
    cleaned = re.sub(r"^[\s-]+", "", cleaned).strip()
    parts = [p for p in re.split(r"[\s,，、]+", cleaned) if len(p) >= 2]
    keywords = parts if parts else [cleaned for _ in [0] if len(cleaned) >= 2]
    if not keywords:
        fallback = re.sub(r"^(计划|约|定于|准备)", "",
                          re.sub(r"[（）()【】\[\]「」\"'·]", "", name or "")).strip()
        keywords = [fallback] if len(fallback) >= 2 else []
    return keywords


def extract_event_date(name: str) -> str:
    """OngoingContextSelector.kt extractEventDate()（L337-347）：
    仅从事项名提取（9-21 → 当年-09-21），不从状态链提取（状态写入时间≠事件时间）。"""
    m = re.search(r"(\d{1,2})-(\d{1,2})", name or "")
    if m:
        return "%s-%02d-%02d" % ("2026", int(m.group(1)), int(m.group(2)))
    return None


def compute_relevance_signals(item, real_text, intent_text, directive_text) -> dict:
    """OngoingContextSelector.kt computeRelevanceSignals()（L221-262）：
    三信号独立；只有真实消息证据刷新 lastEvidenceTurn（L77, L206-208）；
    匹配词表只用 extractKeywords 的明确词——二字滑动窗口整个撤掉（L234-239，
    "周末还要加班"不许召回"周末见面"）。"""
    item_name = (item["name"] or "").strip()
    if not item_name:
        return {"realMessageEvidence": False, "replyDirectiveRelevant": False,
                "persistentIntentRelevant": False}
    kws = extract_keywords(item_name)
    if not kws:
        return {"realMessageEvidence": False, "replyDirectiveRelevant": False,
                "persistentIntentRelevant": False}
    low_real = (real_text or "").lower()
    low_dir = (directive_text or "").lower()
    low_intent = (intent_text or "").lower()
    return {
        "realMessageEvidence": any(kw.lower() in low_real for kw in kws),
        "replyDirectiveRelevant": bool(low_dir) and any(kw.lower() in low_dir for kw in kws),
        "persistentIntentRelevant": bool(low_intent) and any(kw.lower() in low_intent for kw in kws),
    }


class ReferenceOngoingSelector:
    """OngoingContextSelector.kt selectForInjection()（L103-213）移植。
    默认拒绝注入；fail closed（L20-21, L100-102）。冷却状态由 repo 注入
    （read_cooldown/write_cooldown），对应 moment/ongoing_cooldown.json（L349-405）。"""

    def __init__(self, repo, clock=None):
        self._repo = repo                    # 提供 read_plan_active/read_file/write_file
        self._clock = clock or (lambda: "2026-10-21 12:00:00")

    def read_cooldown(self, kb) -> dict:
        content = self._repo.read_file(kb, "moment/ongoing_cooldown.json")
        if not content.strip():
            return {}
        try:
            arr = json.loads(content)
            return {e["name"]: e for e in arr}
        except (json.JSONDecodeError, KeyError, TypeError):
            return {}                        # L362-367：坏文件 → 空（读侧宽容）

    def select_for_injection(self, kb, messages, current_turn, reply_directive=None,
                             effective_intent=None):
        plan_content = self._repo.read_plan_active(kb)
        all_items = parse_plan_items(plan_content)
        if not all_items:
            return {"eligibleItems": [], "allItems": [], "injectedItemNames": set()}
        cooldown = self.read_cooldown(kb)
        items = []
        for it in all_items:
            cd = cooldown.get(it["name"])
            it = dict(it)
            it["lastInjectedTurn"] = cd["lastInjectedTurn"] if cd else -1
            it["lastEvidenceTurn"] = cd.get("lastEvidenceTurn", -1) if cd else -1
            items.append(it)
        # DORMANT 推导（L128-139）：基于 lastEvidenceTurn 而非 lastInjectedTurn
        for it in items:
            if it["itemStatus"] == "ACTIVE" and it["lastEvidenceTurn"] >= 0:
                if current_turn - it["lastEvidenceTurn"] >= ONGOING_DORMANT_TURNS:
                    it["itemStatus"] = "DORMANT"
        real_text = " ".join(m["content"] for m in messages
                             if m["role"] in ("HER", "ME"))
        intent_cfg = effective_intent or {}
        intent_text = intent_cfg.get("text", "") if is_intent_effective(intent_cfg) else ""
        directive_text = (reply_directive or {}).get("text", "") or ""
        eligible, injected_names, real_evidence_names = [], set(), set()
        for it in items:
            if it["itemStatus"] in ("FINISHED", "CANCELLED"):
                continue                     # L162-164：完成/取消永不注入
            signals = compute_relevance_signals(it, real_text, intent_text, directive_text)
            if signals["realMessageEvidence"]:
                real_evidence_names.add(it["name"])
            relevant = signals["realMessageEvidence"] or signals["replyDirectiveRelevant"] \
                or signals["persistentIntentRelevant"]
            cd = cooldown.get(it["name"])
            in_cooldown = cd is not None \
                and (current_turn - cd["lastInjectedTurn"]) < ONGOING_COOLDOWN_TURNS \
                and not relevant            # L177-181
            if in_cooldown:
                continue
            if it["itemStatus"] == "DORMANT" and not relevant:
                continue                    # L190-192
            if it["itemStatus"] == "ACTIVE" and not relevant:
                continue                    # L195-197
            eligible.append(it)
            injected_names.add(it["name"])
        if injected_names or real_evidence_names:
            self._update_cooldown(kb, injected_names, real_evidence_names, current_turn)
        return {"eligibleItems": eligible, "allItems": items,
                "injectedItemNames": injected_names}

    def _update_cooldown(self, kb, injected, evidence, current_turn):
        """L379-405：lastInjectedTurn 只在注入时更新；lastEvidenceTurn 只在真实证据时
        更新（意图/想法相关不刷新证据，L77-79）；只保留最近 20 条（L398-399）。"""
        existing = self.read_cooldown(kb)
        now = self._clock()
        for name in injected:
            e = existing.get(name) or {"name": name, "lastInjectedTurn": -1,
                                       "lastInjectedTime": now, "lastEvidenceTurn": -1}
            e["lastInjectedTurn"] = current_turn
            e["lastInjectedTime"] = now
            existing[name] = e
        for name in evidence:
            e = existing.get(name) or {"name": name, "lastInjectedTurn": -1,
                                       "lastInjectedTime": now, "lastEvidenceTurn": -1}
            e["lastEvidenceTurn"] = current_turn
            existing[name] = e
        keep = sorted(existing.values(),
                      key=lambda e: -max(e["lastInjectedTurn"], e["lastEvidenceTurn"]))[:20]
        self._repo.write_file(kb, "moment/ongoing_cooldown.json",
                              json.dumps(keep, ensure_ascii=False))
