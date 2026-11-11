# product_memory.py — 原产品「本轮提交 → 下一轮读取」写回移植（跨轮基线补齐）
#
# 定位：这不是候选创新，是**参考基线补齐原产品本来就有的跨轮记忆闭环**。
# 依据：D:/LoveBrain 工作树 2026-10-22，逐函数对照下列源码（行号冻结）：
#   - LoveBrainViewModel.kt nextRound L1403-1469：本轮冻结消息 + analysis
#     （topic_status / topic_label / scene_facts / ongoing）→ topicRecorder.record；
#     selectedScheme 恒为 null（点赞≠发送），likedSchemes 只记偏好；
#     userHint 传空串（军师备注不进 recent）。
#   - TopicRecorder.kt record L101-173 / applyRound L188-247：六个投影
#     （话题归档 / 话题标签 / recent / scene / plan / 轮次计数）。
#   - RecentRoundStore.kt L39-117：轮次块格式、marker 去重、最近 MAX_TOPIC_TURNS 轮、
#     溢出 → memory/raw_chat.md。
#   - SceneChainStore.kt L85-330：来源校验（只认冻结快照里的 HER/ME）、
#     同来源新文本替换、幂等、TTL 2h、最多 2 条、溢出 → memory/raw_scene.md。
#   - OngoingPlanStore.kt L47-230：itemId 稳定身份、状态链合并、幂等、
#     终态不复活（除非带新证据）、链截断 10 条、双分区渲染。
#   - KnowledgeTopicTextService.kt L32-90：moment/topic.md 的读法/写法/话题龄、
#     plan.md 只取「## 进行中」。
#   - SceneChainInjection.kt transform L30-80：注入前龄标注 + 精确去重 + 过期过滤。
#
# 如实登记的移植差异（都不放宽原产品已有能力，也不新增它没有的能力）：
#   1. 不搬 RoundCommitJournal（WAL/崩溃恢复）：考场单进程顺序写，每份文件
#      各自原子替换（tmp + os.replace）；跨文件半写不做恢复重放。
#   2. 不搬 FactSubjectResolver 三层主体解析：事实只存 `src=`/`spk=`，
#      不写 `subj=`。影响面仅"同来源不同主体"的替换判据——本考场题面里
#      speaker 单一时不产生差异；读侧注入会先剥掉这些标记（与产品一致）。
#   3. itemId 用 name 的稳定散列（产品用 Kotlin String.hashCode）：身份只要求
#      跨轮稳定，不要求与 Kotlin 数值相同。
#   4. 轮次计数落在 `memory/kb/.meta.json`（产品在 KB 元数据 JSON 里）——同一语义，
#      不同落点，读写都在本模块内闭环。
#   5. 话题轮转只做「旧话题归档到 memory/raw_topic.md + 重置此刻层」，
#      不搬 archive 四步事务与知识库重估触发（那是 Android 侧的异步链路）。
#   6. 时间源 = 题面 `clock`（冻结），产品用系统时钟。槽位内的相对时序
#      （同小时/跨小时、TTL 2h、话题龄）由冻结时钟驱动，因此可复现；
#      产品在真实时间下跑，这是**考场化**而不是能力差异。
import json
import os
import re

# ── AppConfig.kt L60-62（逐字） ──
MAX_TOPIC_TURNS = 2          # recent.md 只保留最近 2 轮
SCENE_CHAIN_MAX_HOURS = 2    # 超过 2h 的状态条目移入状态暂存
SCENE_CHAIN_MAX_ENTRIES = 2  # scene.md 最多保留 2 条
MAX_CHAIN_STATES = 10        # OngoingPlanStore.MAX_CHAIN_STATES

# RoundCommitJournal.kt L58（逐字）
WRITER_MARKER_PREFIX = "<!-- round:msgIds:"
# ChatMessage.kt L12-16（逐字）
ROLE_LABEL = {"HER": "她", "ME": "我", "IDEA": "想法"}
# KbTextOps.kt L33-39（逐字）
TOPIC_MARKER = "正在聊："
TOPIC_KEY_SUFFIX = " | key："
TOPIC_INITIAL_LABEL = "（等待第一次对话）"
# OngoingPlanStore.kt companion L215-226（逐字）
FILLER_STATES = {
    "本轮未提及", "本轮无进展",
    "本轮未提及，持续推进", "本轮无进展，持续推进",
    "本轮未提及，事项持续推进中", "本轮无进展，事项持续推进中",
}

TOPIC_FILE = "moment/topic.md"
RECENT_FILE = "moment/recent.md"
SCENE_FILE = "moment/scene.md"
PLAN_FILE = "moment/plan.md"
META_FILE = ".meta.json"
RAW_CHAT = "memory/raw_chat.md"
RAW_SCENE = "memory/raw_scene.md"
RAW_TOPIC = "memory/raw_topic.md"

_ROUND_TS = re.compile(r"^- \[\d{4}-\d{2}-\d{2} \d{2}:\d{2}]")
_ENTRY_TS = re.compile(r"^- \[(\d{4}-\d{2}-\d{2} \d{2}:\d{2})]\s*(.*)$")
_TIME_RE = re.compile(r"(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?")


# ─────────────────────────── 时间 ───────────────────────────
def parse_wall(text):
    """TimeFmt.parse 的等价：'YYYY-MM-DD HH:MM[:SS]' → 本地 naive 毫秒。

    只用差值（话题龄、TTL、距今），不做时区换算，因此不引入夏令时/时区漂移。
    """
    m = _TIME_RE.search(text or "")
    if not m:
        return 0
    y, mo, d, h, mi, s = (int(x) if x else 0 for x in m.groups())
    # 自 1970-01-01 起的"平地时间"（civil days），避免依赖 datetime 的时区库
    import datetime
    try:
        return int(datetime.datetime(y, mo, d, h, mi, s).timestamp() * 1000)
    except ValueError:
        return 0


def clock_of(ctx_input, default="2026-10-22 12:00:00"):
    """考场时钟：题面 clock（冻结）优先；无 clock 时用固定默认（确定性对拍）。"""
    t = ((ctx_input or {}).get("clock") or "").strip()
    return t or default


def wall_minute(text):
    """写盘用的分钟精度时间串（产品落盘即 'yyyy-MM-dd HH:mm'）。"""
    m = _TIME_RE.search(text or "")
    if not m:
        return "1970-01-01 00:00"
    y, mo, d, h, mi = m.groups()[:5]
    return "%s-%s-%s %s:%s" % (y, mo, d, h, mi)


# ─────────────────────────── 文件读写 ───────────────────────────
def _read(storage, kb, rel):
    try:
        return storage.read(kb, rel) or ""
    except OSError:
        return ""


def _write(storage, kb, rel, text):
    storage.write_file(kb, rel, text)


def _append(storage, kb, rel, text):
    old = _read(storage, kb, rel)
    storage.write_file(kb, rel, old + text)


def _atomic_write(storage, kb, rel, text):
    # 产品走 atomicWriteText；考场侧同一语义：先写临时文件再替换
    storage.write_file(kb, rel + ".tmp", text)
    p = storage.path(kb, rel)
    p_tmp = storage.path(kb, rel + ".tmp")
    os.replace(p_tmp, p)


def read_all(storage, kb, now_ms):
    """一次读出下一轮装配要用的全部状态（顺序与产品 buildKnowledgeSection 对齐）。"""
    meta = _read_meta(storage, kb)
    topic_content = _read(storage, kb, TOPIC_FILE)
    return {
        "topic": topic_label(topic_content),
        "topic_age_h": topic_age_hours(topic_content, now_ms),
        "scene_raw": _read(storage, kb, SCENE_FILE),
        "recent": _read(storage, kb, RECENT_FILE),
        "plan_active": read_plan_active(_read(storage, kb, PLAN_FILE)),
        "turn_count": int(meta.get("turnCount") or 0),
    }


def _read_meta(storage, kb):
    raw = _read(storage, kb, META_FILE)
    if not raw.strip():
        return {}
    try:
        obj = json.loads(raw)
        return obj if isinstance(obj, dict) else {}
    except json.JSONDecodeError:
        return {}                       # 坏文件按 0 轮起步（读侧宽容，产品同语义）


def _write_meta(storage, kb, meta):
    storage.write_file(kb, META_FILE,
                       json.dumps(meta, ensure_ascii=False, sort_keys=True))


# ─────────────────────────── 话题（topic.md） ───────────────────────────
def topic_label(content):
    """KbTextOps.topicLabel L53-56（含既存怪癖：无标记时整行返回）。"""
    first = (content or "").splitlines()[0].strip() if (content or "").strip() else ""
    raw = first.split(TOPIC_MARKER, 1)[1] if TOPIC_MARKER in first else first
    return raw.split(TOPIC_KEY_SUFFIX)[0].strip()


def topic_line(time_str, label):
    """KbTextOps.topicLine L42（逐字）。"""
    return "- [%s] %s%s" % (time_str, TOPIC_MARKER, label)


def topic_age_hours(content, now_ms):
    """KnowledgeTopicTextService.topicAgeHours L73-79：
    取话题行时间戳与"现在"的差；解析不出 → 99（=不显示龄）。"""
    m = re.search(r"\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2})]", content or "")
    if not m:
        return 99
    ts = parse_wall(m.group(1))
    if ts <= 0 or not now_ms:
        return 99
    return int((now_ms - ts) / 3600000)


def _set_topic(storage, kb, time_str, label):
    _atomic_write(storage, kb, TOPIC_FILE, topic_line(time_str, label) + "\n")


def _rotate_topic(storage, kb, old_label, time_str):
    """rotateTopic 的考场等价：旧话题归档 + 清空此刻层（recent/scene）。

    产品的 rotateTopic 做四步（归档标题/正文、清 recent、清 scene、重置话题行），
    这里保留"归档 + 清空"两件会改变下一轮读到的内容的事，异步重估不搬。
    """
    if old_label and old_label != TOPIC_INITIAL_LABEL:
        _append(storage, kb, RAW_TOPIC,
                "## [%s] %s\n" % (time_str, old_label))
    _atomic_write(storage, kb, RECENT_FILE, "")
    _atomic_write(storage, kb, SCENE_FILE, "")


# ─────────────────────────── 投影 3：recent.md ───────────────────────────
def build_round_entry(time_str, round_msg_ids, messages, user_hint="",
                      scheme=None, liked=()):
    """RecentRoundStore.buildRoundEntry L39-73（逐字结构）。

    scheme 恒为 None：用户没有"确认发送"，候选回复不写入"实际对话"段。
    liked 为空 → 不写偏好块（本考场没有点赞动作）。
    """
    out = ["- [%s]" % time_str,
           "%s%s -->" % (WRITER_MARKER_PREFIX, round_msg_ids)]
    for m in messages:
        if m["role"] not in ("HER", "ME"):
            continue
        out.append("%s：%s" % (ROLE_LABEL[m["role"]], m["content"]))
    if (user_hint or "").strip():
        out.append("我的想法：%s" % user_hint.strip())
    if scheme is not None:
        out.append("我（最终回复：%s）：%s" % (scheme.get("label", ""),
                                            scheme.get("reply", "")))
    if liked and scheme is None:
        out.append("（用户偏好，未确认发送：")
        for s in liked:
            out.append("  %s：%s" % (s.get("label", ""), s.get("reply", "")))
        out.append("）")
    return "\n".join(out)


def write_recent(storage, kb, entry, round_msg_ids):
    """RecentRoundStore.writeRecent L78-111（逐语义）：marker 去重、
    只留最近 MAX_TOPIC_TURNS 轮、溢出 → memory/raw_chat.md、未识别内容留在头部。"""
    marker = "%s%s -->" % (WRITER_MARKER_PREFIX, round_msg_ids)
    if marker in _read(storage, kb, RECENT_FILE):
        return False
    existing = _read(storage, kb, RECENT_FILE)
    if not existing.strip():
        _atomic_write(storage, kb, RECENT_FILE, entry + "\n")
        return True
    parts = [p.strip() for p in re.split(r"(?m)^(?=- \[)", existing)]
    valid = [p for p in parts if p and _ROUND_TS.match(p.splitlines()[0].strip())]
    unrecognized = [p for p in parts if p and not _ROUND_TS.match(p.splitlines()[0].strip())]
    keep_n = max(MAX_TOPIC_TURNS - 1, 0)
    kept = valid[-keep_n:] if keep_n else []
    overflow = valid[:-keep_n] if keep_n else valid
    if overflow:
        _append(storage, kb, RAW_CHAT, "\n" + "\n\n".join(overflow) + "\n")
    chunks = []
    for p in unrecognized:
        chunks.append(p + "\n\n")
    for p in kept:
        chunks.append(p + "\n\n")
    chunks.append(entry + "\n")
    _atomic_write(storage, kb, RECENT_FILE, "".join(chunks))
    return True


# ─────────────────────────── 投影 4：scene.md ───────────────────────────
def clean_fact(fact_text):
    """SceneChainInjection.cleanFact L82-95（逐字）：剥掉来源/说话人标记。"""
    t = fact_text or ""
    if "|src=" in t or "|spk=" in t or "|subj=" in t:
        return t.split("|")[0].strip()
    m = re.search(r"(.*)⟨.+⟩$", t)
    if m:
        return m.group(1).strip()
    return t.strip()


def _parse_fact(fact_text, time_ms):
    if "|src=" in fact_text or "|spk=" in fact_text or "|subj=" in fact_text:
        parts = [p.strip() for p in fact_text.split("|")]
        text = parts[0] if parts else ""
        src = []
        spk = ""
        for p in parts[1:]:
            if p.startswith("src="):
                src = [x.strip() for x in p[4:].split(",") if x.strip()]
            elif p.startswith("spk="):
                spk = p[4:].strip()
        return {"text": text, "src": src, "spk": spk, "ts": time_ms}
    m = re.search(r"(.*)⟨(.+)⟩$", fact_text)
    if m:
        return {"text": m.group(1).strip(),
                "src": [x.strip() for x in m.group(2).split(",") if x.strip()],
                "spk": "", "ts": time_ms}
    return {"text": fact_text.strip(), "src": [], "spk": "", "ts": time_ms}


def parse_scene(content):
    """SceneChainStore.parseSceneEntries L283-320 的等价解析。"""
    entries = []
    for line in (content or "").splitlines():
        m = _ENTRY_TS.match(line.strip())
        if not m:
            continue
        time_str, rest = m.group(1), m.group(2)
        ts = parse_wall(time_str)
        idx = rest.find("：")
        label, facts_raw = (rest[:idx].strip(), rest[idx + 1:]) if idx >= 0 else ("", rest)
        facts = [_parse_fact(f.strip(), ts)
                 for f in re.split(r"[；;]", facts_raw) if f.strip()]
        if facts:
            entries.append({"time": time_str, "ts": ts, "label": label,
                            "facts": facts})
    return entries


def _render_fact(f):
    bits = [f["text"]]
    if f["src"]:
        bits.append("src=" + ",".join(f["src"]))
    if f.get("spk"):
        bits.append("spk=" + f["spk"])
    return "|".join(bits)


def _render_entries(entries):
    return "".join(
        "- [%s] %s：%s\n" % (e["time"], e["label"],
                            "；".join(_render_fact(f) for f in e["facts"]))
        for e in entries)


def _speaker_of(ids, role_by_id):
    roles = {role_by_id.get(i) for i in ids if role_by_id.get(i)}
    if roles == {"HER"}:
        return "HER"
    if roles == {"ME"}:
        return "ME"
    return ""            # 混合/未知 → 不写 spk（产品此时靠主体解析，这里如实省略）


def update_scene_chain(storage, kb, topic_label_, scene_facts,
                       frozen_messages, alias_map, now_ms, time_str):
    """SceneChainStore.updateSceneChain L85-262（逐语义，主体解析除外）。

    规则：
      1. 来源校验：source_ids 先过别名表（m0→真实 id），再必须属于冻结快照 HER/ME；
         全部非法的单条事实被拒，不影响合法事实。
      2. 无来源 → 未核实：追加、不刷新时间（evidenceTime=0）。
      3. 同来源 + 同文本 → 幂等跳过；同来源 + 新文本 → 同一事项新状态，原地替换。
      4. TTL 2h 与最多 2 条：超出的条目进 memory/raw_scene.md（过期≠事件结束）。
    """
    role_by_id = {m["id"]: m["role"] for m in frozen_messages
                  if m["role"] in ("HER", "ME")}
    validated = []
    for sf in scene_facts or []:
        if not isinstance(sf, dict):
            continue
        text = (sf.get("text") or "").strip()
        if not text:
            continue
        raw_ids = sf.get("source_ids") or sf.get("sourceIds") or []
        if not isinstance(raw_ids, list):
            raw_ids = [raw_ids]
        resolved = [alias_map.get(str(i), str(i)) for i in raw_ids]
        valid = [i for i in resolved if i in role_by_id]
        if raw_ids and not valid:
            continue                    # 全部来源非法 → 拒这条事实
        validated.append({"text": text,
                          "src": valid,
                          "spk": _speaker_of(valid, role_by_id) if valid else "",
                          "ts": now_ms if valid else 0})
    if not validated:
        return 0

    entries = parse_scene(_read(storage, kb, SCENE_FILE))
    existing = [(f, e) for e in entries for f in e["facts"]]
    to_add, to_replace = [], {}
    for nf in validated:
        if not nf["src"]:
            to_add.append(dict(nf, ts=0))          # 未核实：不刷新时间
            continue
        same = next((f for f, _e in existing
                     if f["src"] and set(f["src"]) == set(nf["src"])), None)
        if same is None:
            to_add.append(nf)
        elif same["text"] != nf["text"]:
            to_replace[id(same)] = dict(nf, ts=same["ts"])   # 保留原证据时间
        # 文本相同 → 幂等跳过

    changed = 0
    if to_add or to_replace:
        for e in entries:
            new_facts, seen = [], set()
            for f in e["facts"]:
                nf = to_replace.get(id(f), f)
                # 被替换的旧版本不保留（同事项新状态）
                if id(f) in to_replace and nf is not f:
                    pass
                key = (nf.get("spk") or "", nf["text"], tuple(sorted(nf["src"])))
                if key in seen:
                    continue
                seen.add(key)
                new_facts.append(nf)
            e["facts"] = new_facts
        entries = [e for e in entries if e["facts"]]
        changed = len(to_add) + len(to_replace)
        if to_add:
            entries.insert(0, {"time": wall_minute(time_str), "ts": now_ms,
                               "label": topic_label_ or "日常",
                               "facts": to_add})

    # TTL + 条数上限（即便本轮全幂等，也要做一次归档——产品 L215-241 同语义）
    fresh, expired = [], []
    max_age = SCENE_CHAIN_MAX_HOURS * 3600000
    for e in entries:
        if e["ts"] > 0 and (now_ms - e["ts"]) > max_age:
            expired.append(e)
        else:
            fresh.append(e)
    kept = fresh[:SCENE_CHAIN_MAX_ENTRIES]
    expired.extend(fresh[SCENE_CHAIN_MAX_ENTRIES:])
    _atomic_write(storage, kb, SCENE_FILE, _render_entries(kept))
    if expired:
        _append(storage, kb, RAW_SCENE, _render_entries(expired))
    return changed


def scene_chain_injected(content, now_ms, today=None):
    """SceneChainInjection.transform L30-80（逐语义）：
    超龄过滤 → 龄标注（不到1小时前 / N小时前 / 月-日 N小时前）→ 精确文本去重（新→旧）。"""
    max_age = SCENE_CHAIN_MAX_HOURS * 3600000
    entries = parse_scene(content)
    if not entries:
        return ""
    fresh = [e for e in entries if e["ts"] <= 0 or (now_ms - e["ts"]) <= max_age]
    if not fresh:
        return ""
    seen, out = set(), []
    for e in fresh:
        age_h = int((now_ms - e["ts"]) / 3600000) if e["ts"] > 0 else 0
        if e["ts"] <= 0:
            age_label = "时间未知"
        elif age_h < 1:
            age_label = "不到1小时前"
        elif today and e["time"][:10] != today:
            age_label = "%s %d小时前" % (e["time"][5:10], age_h)
        else:
            age_label = "%d小时前" % age_h
        kept = []
        for f in e["facts"]:
            txt = clean_fact(_render_fact(f))
            if txt and txt not in seen:
                seen.add(txt)
                kept.append(txt)
        if kept:
            out.append("- [%s] %s：%s" % (age_label, e["label"], "；".join(kept)))
    return "\n".join(out).strip()


# ─────────────────────────── 投影 5：plan.md ───────────────────────────
def read_plan_active(content):
    """KnowledgeTopicTextService.readPlanActive L41-60：只取「## 进行中」，
    注释块里的格式说明不注入，`格式`/`示例` 开头的裸说明行剔除。"""
    out, in_active, in_comment = [], False, False
    for line in (content or "").splitlines():
        t = line.strip()
        if in_comment:
            if "-->" in t:
                in_comment = False
            continue
        if t.startswith("<!--"):
            if "-->" not in t:
                in_comment = True
            continue
        if t.startswith("## 进行中"):
            in_active = True
        elif t.startswith("##"):
            in_active = False
        elif t.startswith("格式") or t.startswith("示例"):
            continue
        elif in_active and "|" in t:
            out.append(t)
    return "\n".join(out).strip()


def _derive_item_id(name):
    import hashlib
    cleaned = re.sub(r"\s+", "", (name or "").strip().lower())
    return "item_" + hashlib.sha1(cleaned.encode("utf-8")).hexdigest()[:8]


def _last_state(chain):
    i = chain.rfind("]")
    tail = chain[i + 1:] if i >= 0 else chain
    return tail.replace("（当前）", "").strip()


def _truncate_chain(chain):
    parts = [p for p in chain.split("→") if p.strip()]
    return chain if len(parts) <= MAX_CHAIN_STATES else "→".join(parts[-MAX_CHAIN_STATES:])


def _parse_plan(content):
    active, ended, section = [], [], "active"
    for line in (content or "").splitlines():
        t = line.strip()
        if t.startswith("## 进行中"):
            section = "active"
            continue
        if t.startswith("## 已结束"):
            section = "ended"
            continue
        if t.startswith("#") or t.startswith("<!--") or "|" not in t:
            continue
        parts = [p.strip() for p in t.split("|")]
        if len(parts) < 3 or not parts[0]:
            continue
        first = parts[0]
        tilde = first.find("~")
        item_id, name = ((first[:tilde], first[tilde + 1:]) if tilde > 0
                         else ("", first))
        chain = "|".join(parts[2:]).strip()
        row = {"name": name, "status": parts[1], "chain": chain,
               "itemId": item_id, "lastState": _last_state(chain)}
        (active if section == "active" else ended).append(row)
    return active, ended


def _render_plan(active, ended):
    out = ["# 事项计划", "", "## 进行中"]
    for it in active:
        prefix = (it["itemId"] + "~") if it["itemId"] else ""
        out.append("%s%s | %s | %s" % (prefix, it["name"], it["status"], it["chain"]))
    out.append("")
    out.append("## 已结束")
    for it in ended:
        prefix = (it["itemId"] + "~") if it["itemId"] else ""
        out.append("%s%s | %s | %s" % (prefix, it["name"], it["status"], it["chain"]))
    return "\n".join(out) + "\n"


def seed_plan(storage, kb, items):
    """新建库的 plan.md 种子（产品 seed 是 schema 模板；考场等价输入 =
    initial_kb.ongoing_plan，按 `name|status|chain` 原样落盘）。"""
    active = []
    for it in items or []:
        if isinstance(it, dict):
            active.append({"name": it.get("name", ""),
                           "status": it.get("status", "进行中"),
                           "chain": it.get("chain", ""),
                           "itemId": it.get("itemId") or _derive_item_id(it.get("name", "")),
                           "lastState": _last_state(it.get("chain", ""))})
        elif isinstance(it, str) and it.strip():
            active.append({"name": it.strip(), "status": "进行中", "chain": "",
                           "itemId": _derive_item_id(it),
                           "lastState": ""})
    _atomic_write(storage, kb, PLAN_FILE, _render_plan(active, []))


def merge_ongoing(storage, kb, items, time_str):
    """OngoingPlanStore.mergeOngoing L47-200（逐语义）。

    规则：空名/空状态丢弃；固定填充词（且非终态）不落盘；itemId 优先、name 回退；
    终态事项不因非终态输出复活（除非携带新证据 source_ids）；状态实质变化才追加
    `→[time]新状态（当前）`；链截断 10 条；终态移入「## 已结束」。
    """
    active, ended = _parse_plan(_read(storage, kb, PLAN_FILE))
    touched = 0
    for it in items or []:
        if not isinstance(it, dict):
            continue
        name = (it.get("item") or it.get("name") or "").strip()
        state = (it.get("state") or "").strip()
        if not name or not state:
            continue
        status = (it.get("status") or "").strip() or "进行中"
        normalized = state.rstrip(",，。！?")
        if status not in ("已完成", "已取消") and normalized in FILLER_STATES:
            continue
        item_id = (it.get("itemId") or "").strip() or _derive_item_id(name)
        src_ids = it.get("source_ids") or it.get("sourceIds") or []
        found, from_ended = None, False
        for row in active:
            if row["itemId"] and row["itemId"] == item_id:
                found = row
                break
        if found is None:
            for row in active:
                if not row["itemId"] and row["name"] == name:
                    found = row
                    break
        if found is None:
            for row in ended:
                if row["itemId"] and row["itemId"] == item_id:
                    found, from_ended = row, True
                    break
        if found is None:
            for row in ended:
                if not row["itemId"] and row["name"] == name:
                    found, from_ended = row, True
                    break

        if found is not None:
            if from_ended and status not in ("已完成", "已取消") and not src_ids:
                continue                                     # 不复活（无新证据）
            if found["lastState"] == state:
                if found["status"] != status:
                    found["status"] = status
                    if status in ("已完成", "已取消"):
                        ended.remove(found)
                        if found in active:
                            active.remove(found)
                        ended.append(found)
                continue                                     # 幂等
            found["status"] = status
            old_chain = found["chain"].replace("（当前）", "").rstrip("→ ")
            new_chain = "%s→[%s]%s（当前）" % (old_chain, wall_minute(time_str), state) \
                if old_chain else "[%s]%s（当前）" % (wall_minute(time_str), state)
            found["chain"] = _truncate_chain(new_chain)
            found["lastState"] = state
            if from_ended and status not in ("已完成", "已取消"):
                ended.remove(found)
                active.append(found)
            touched += 1
        else:
            active.append({"name": name, "status": status,
                           "chain": "[%s]%s（当前）" % (wall_minute(time_str), state),
                           "itemId": item_id, "lastState": state})
            touched += 1

    still = []
    for row in active:
        if row["status"] in ("已完成", "已取消"):
            if not any((e["itemId"] and e["itemId"] == row["itemId"]) or
                       (not e["itemId"] and e["name"] == row["name"]) for e in ended):
                ended.append(row)
        else:
            still.append(row)
    _atomic_write(storage, kb, PLAN_FILE, _render_plan(still, ended))
    return touched


# ─────────────────────────── 一轮提交（六投影） ───────────────────────────
def record_round(storage, kb, *, time_str, now_ms, frozen_messages,
                 alias_map, analysis):
    """TopicRecorder.record + applyRound L101-247 的等价提交（六投影）。

    analysis 为本轮回复里的 `analysis` 对象；缺失（模型没给 JSON）→ 产品此时
    根本没有 GenerateResult.Success，nextRound 直接 return，**什么都不写**，
    这里保持一致：调用方应先判定 analysis 是否为 None。

    返回逐投影执行结果，供 mechanism notes 逐字登记。
    """
    status = (analysis.get("topic_status") or "").strip()
    label = (analysis.get("topic_label") or "").strip()
    scene_facts = analysis.get("scene_facts") or []
    ongoing = analysis.get("ongoing") or []
    if not isinstance(scene_facts, list):
        scene_facts = []
    if not isinstance(ongoing, list):
        ongoing = []

    conversational = [m for m in frozen_messages if m["role"] in ("HER", "ME")]
    round_msg_ids = ",".join(sorted(m["id"] for m in conversational))
    entry = build_round_entry(wall_minute(time_str), round_msg_ids, conversational)

    cur = topic_label(_read(storage, kb, TOPIC_FILE))
    had_topic = bool(cur.strip()) and cur != TOPIC_INITIAL_LABEL
    should_rotate = bool(label) and status == "new"

    done = {}
    # 投影 1：话题归档（仅 status=new 且有有效旧话题）
    if should_rotate and had_topic:
        _rotate_topic(storage, kb, cur, wall_minute(time_str))
        done["topic_rotate"] = True
    # 投影 2：话题标签
    new_label = None
    if should_rotate:
        new_label = label
    elif status == "drift" and label:
        new_label = label
    elif not had_topic:
        new_label = label or "日常对话"
    if new_label is not None:
        _set_topic(storage, kb, wall_minute(time_str), new_label)
        done["topic_set"] = new_label
    # 投影 3：recent.md
    done["recent"] = write_recent(storage, kb, entry, round_msg_ids)
    # 投影 4：scene.md
    done["scene_facts"] = update_scene_chain(
        storage, kb, label, scene_facts, conversational, alias_map,
        now_ms, time_str)
    # 投影 5：plan.md
    done["ongoing"] = merge_ongoing(storage, kb, ongoing, time_str)
    # 投影 6：轮次计数
    meta = _read_meta(storage, kb)
    meta["turnCount"] = int(meta.get("turnCount") or 0) + 1
    meta["now"] = time_str
    _write_meta(storage, kb, meta)
    done["turn_count"] = meta["turnCount"]
    return done
