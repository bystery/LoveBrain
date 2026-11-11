# -*- coding: utf-8 -*-
"""tools/build_gold_sidecar.py — 全故事库 gold 断言 sidecar 生成器（离线零模型调用）。

目的（用户最高指令"修好评测基准"）：
  episode.json 已 SEALED（STORY_CUSTODY_LEDGER.json 记 hash），gold 字段全空（0/3102 turn）。
  本工具**只读** episode + 证据卡，在 benchmark/stories_v2/GOLD_SIDECAR/ 生成独立 sidecar，
  绝不改动任何 episode.json / manifest。

Schema（与 benchmark/rubric.md §2 / arena/lb_scorer.run_case_scoring_safe 的 gold 消费对齐）：
  每 turn 一条记录：
    case_id / wave / episode_id / turn_index
    gold_empty        — 卡缺失或四类断言字段全空时 true（如实登记，不伪造）
    assertions        — required_memory_atoms 逐字拷贝（subject/relation/value/status/
                        negation/valid_time/source.quote+case_id/message_id，不改一字）
    intent_contract   — {intent:{enabled,status,text}|null, intent_source, may_decline,
                        may_decline_source, acceptable_actions}
    forbidden         — {forbidden_factual_claims, unknowns}（逐字拷贝）
    protected_expressions / card_extras（current_action / private_guidance /
                        applicable_dimensions，评卡上下文，非候选可见）
    scorer_gold       — 当前 lb_scorer 已消费的 gold 键的忠实子集。事实断言入口
                        （rubric §2 C6/C7/C8 的语义对应物）在 lb_scorer 尚无 keyword 之外
                        的消费路径，本工具不伪造关键词，故此对象默认 {}；接线由并行
                        agent 按 rubric §2 以 assertions 为事实检查入口实现。
    provenance        — 卡来源文件+sha256、episode 文件+sha256、多源冲突登记
    qc                — source.quote 逐字命中自检（strict=指定消息内 / lenient=全 episode）

用法：
  python tools/build_gold_sidecar.py            # 全量构建 + QC + 账本 append 事件
  python tools/build_gold_sidecar.py --dry-run  # 只统计不落盘
"""
import argparse
import copy
import datetime
import glob
import hashlib
import json
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORIES = os.path.join(ROOT, "benchmark", "stories_v2")
OUTDIR = os.path.join(STORIES, "GOLD_SIDECAR")
# v2（GOLD_SCHEMA.md §8 enrichment）：在 v1 逐字内容全部保留的前提下，新增四类
# 确定性判定信号字段（affirm_markers / item_markers / unknowns{topic,affirm_markers} /
# intent_contract.keywords_any），启用 lb_scorer C14/C15 与 C10 事实判分。
SCHEMA_VERSION = "GOLD_SIDECAR_v2"

WAVES = ["stage1", "stage2", "stage3", "stage4", "stageD",
         "wave1", "wave2", "wave3", "wave3_ext", "wave1_ext", "wave4_dev",
         "wave5_selection"]

# wave3/W19_CROSS 是跨 wave 汇编卡且为最新修订版（mtime 晚于 LD1/W14，
# 修复了旧卡漏登记 protected_expressions 与 may_decline），优先于本地旧卡。
EXTRA_CARD_FILES_FIRST = {
    "wave3_ext": [os.path.join(STORIES, "wave3", "GRADING_CARDS_W19_CROSS.json")],
}

# 账本（tools/story_custody.py）：只 append 事件，不动 stories 条目
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "tools"))
import story_custody  # noqa: E402


def sha256_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def load_card_file(path):
    """GRADING_CARDS*.json -> {case_id: card}；兼容 list / {cards:[...]} / 索引 dict。"""
    data = json.load(open(path, encoding="utf-8"))
    if isinstance(data, list):
        items = data
    elif isinstance(data, dict) and isinstance(data.get("cards"), list):
        items = data["cards"]
    elif isinstance(data, dict):
        items = [v for v in data.values()
                 if isinstance(v, dict) and v.get("case_id")]
    else:
        items = []
    return {it["case_id"]: it for it in items
            if isinstance(it, dict) and it.get("case_id")}


def wave_card_sources(wave):
    """按优先级排好的卡来源列表 [(relpath, {case_id: card})]。
    跨 wave 汇编修订卡（若配置）最先；再本 wave 卡文件按文件名序。"""
    out = []
    for p in EXTRA_CARD_FILES_FIRST.get(wave, []):
        out.append((os.path.relpath(p, ROOT), load_card_file(p)))
    local = sorted(glob.glob(os.path.join(STORIES, wave, "GRADING_CARDS*.json")))
    for p in local:
        if "PROVENANCE" in os.path.basename(p):
            continue
        out.append((os.path.relpath(p, ROOT), load_card_file(p)))
    return out


_CARD_CACHE = {}


def all_card_sources():
    if not _CARD_CACHE:
        for wave in WAVES:
            _CARD_CACHE[wave] = wave_card_sources(wave)
    return _CARD_CACHE


def norm_decline(v):
    """null 与 False 在"是否允许不回"上语义等价（默认不允许）。"""
    return False if v is None else bool(v)


def card_key_fields(card):
    """冲突比较用的语义字段（规范化 may_decline）。"""
    return {
        "required_memory_atoms": card.get("required_memory_atoms"),
        "forbidden_factual_claims": card.get("forbidden_factual_claims"),
        "unknowns": card.get("unknowns"),
        "intent": card.get("intent"),
        "may_decline": norm_decline(card.get("may_decline")),
        "acceptable_actions": card.get("acceptable_actions"),
        "protected_expressions": card.get("protected_expressions"),
    }


def resolve_card(wave, case_id):
    """返回 (card|None, sources:[(relpath, sha256)], conflicts:[...])。
    多源同名卡：内容一致→取优先级最高者并登记全部来源；内容冲突→取最高者并记冲突。"""
    sources = []
    conflicts = []
    cards = []
    for relpath, m in all_card_sources()[wave]:
        if case_id in m:
            sha = _CARD_HASH.get(relpath)
            if sha is None:
                sha = _CARD_HASH[relpath] = sha256_file(os.path.join(ROOT, relpath))
            sources.append({"file": relpath, "sha256": sha})
            cards.append(m[case_id])
    if not cards:
        return None, sources, conflicts
    base = card_key_fields(cards[0])
    for j, c in enumerate(cards[1:], start=1):
        if card_key_fields(c) != base:
            conflicts.append({"file": sources[j]["file"],
                              "differs_from_first": True})
    return cards[0], sources, conflicts


_CARD_HASH = {}


def episode_text_corpus(ep):
    """整 episode 的逐字语料：全部消息文本 + note + initial_kb 序列化。"""
    parts = []
    kb = ep.get("initial_kb")
    if kb is not None:
        parts.append(json.dumps(kb, ensure_ascii=False))
    for t in ep.get("turns", []):
        inp = t.get("input") or {}
        for ms in inp.get("messages") or []:
            parts.append(ms.get("text") or "")
        note = inp.get("note")
        if note:
            parts.append(note)
    return parts


def quote_check(atom, ep, turns_by_id, global_turn_index=None):
    """strict：source 指定的那条消息 / initial_kb 内逐字命中；
    lenient：全 episode 语料逐字命中；若 source.case_id 指向其它 episode（跨集连续
    剧情），用 global_turn_index 定位后再判 strict，lenient 也查对应 episode。"""
    src = atom.get("source") or {}
    quote = (src.get("quote") or "").strip()
    if not quote:
        return {"strict": None, "lenient": None}  # 无 quote 不参与命中率
    kind = src.get("kind")
    src_cid = src.get("case_id")
    # 跨 episode 引用：定位源 turn 所在 episode
    src_ep, src_turn = ep, turns_by_id.get(src_cid)
    if src_cid and src_turn is None and global_turn_index is not None \
            and src_cid in global_turn_index:
        w2, e2 = global_turn_index[src_cid]
        try:
            src_ep = json.load(open(os.path.join(STORIES, w2, e2, "episode.json"),
                                    encoding="utf-8"))
            src_turn = next((t for t in src_ep.get("turns", [])
                             if t.get("case_id") == src_cid), None)
        except OSError:
            src_ep, src_turn = ep, None
    strict = False
    if kind == "message" and src_turn is not None:
        msgs = (src_turn.get("input") or {}).get("messages") or []
        mid = src.get("message_id")
        idx = None
        if isinstance(mid, str) and mid.startswith("m") and mid[1:].isdigit():
            idx = int(mid[1:])
        elif isinstance(mid, int):
            idx = mid
        if idx is not None and 0 <= idx < len(msgs):
            strict = quote in (msgs[idx].get("text") or "")
        else:  # message_id 无法定位 → 退回该 turn 全部消息
            strict = any(quote in (ms.get("text") or "") for ms in msgs)
    elif kind == "initial_kb":
        kb = ep.get("initial_kb")
        strict = bool(kb is not None and quote in json.dumps(kb, ensure_ascii=False))
    lenient = any(quote in part for part in episode_text_corpus(ep))
    if not lenient and src_ep is not ep:
        lenient = any(quote in part for part in episode_text_corpus(src_ep))
    return {"strict": strict, "lenient": lenient}


# ============================================================================
# v2 enrichment（GOLD_SCHEMA.md §8 四类字段；离线、确定性、零模型调用）
# 铁律：所有 markers 逐字来自题面（消息/备注语料）与卡原子 value/quote 的交集，
#       不造语料外关键词；无法确定性提取的一律保持原形态（诚实 unrated）。
# ============================================================================

# 取消/更正族（与 arena/gold_sidecar.MUST_NOT_AFFIRM_STATUS 逐字一致，§8 #1）
CANCEL_FAMILY = ("cancelled", "canceled", "superseded", "retracted", "voided",
                 "revoked", "invalidated", "negated", "erroneous")
# status=cancelled/canceled 且属事项/计划类 → 额外 item_markers（§8 #2，复用 C8）
ITEM_MARKER_STATUSES = ("cancelled", "canceled")

# 更正/取消线索词表（§8 #1 "实际是/看错/改成/取消/延期/作废/改到 等"；
# 只作门控与溯源登记，markers 本身仍逐字取自题面，不出自本表）
CUE_WORDS = ["取消", "作废", "更正", "改成", "改到", "改为", "换成", "改约",
             "延期", "推迟", "看错", "记错", "说错", "算错", "弄错", "写错",
             "听错", "实际是", "其实是", "黄了", "落空", "放弃", "打消",
             "不等", "不去", "算了", "撤销", "撤回", "收回", "失效",
             "不支持", "否定", "白高兴"]

_PUNCT = "，。！？、；：；\"'“”‘’（)()【】[]《》<>…—·,.!?;: \t\n\r"
# 边缘虚词（提及≠坐实，进一步收紧 marker 精度；只剥边缘不拆词）
_PARTICLE = "的了是在把被和与或了吗呢吧啊呀嘛哦噢"

# 单短语 AND 组的最短长度：低于此长度的短语单独成组会把"提及"误判为"坐实"
# （如取消计划叫"季卡"，回复"季卡那单就不下了"并非复活）→ 宁可 unrated（§8 诚实登记）
MIN_SINGLE_PHRASE_LEN = 4


def _strip_edges(s):
    while s and (s[0] in _PUNCT or s[0] in _PARTICLE):
        s = s[1:]
    while s and (s[-1] in _PUNCT or s[-1] in _PARTICLE):
        s = s[:-1]
    return s


def max_common_phrases(text, corpus, min_len=2, cap=4):
    """text 中逐字出现在 corpus 里的最长不重叠子串（去标点边缘，maximal）。
    确定性：从长到短、从左到右扫描；已在选中短语内/包含选中短语的一律跳过。"""
    if not text or not corpus:
        return []
    picked = []          # [(phrase, pos_in_text)]
    L = len(text)
    while L >= min_len and len(picked) < cap:
        i = 0
        while i + L <= len(text) and len(picked) < cap:
            raw = text[i:i + L]
            s = _strip_edges(raw)
            if len(s) >= min_len and s in corpus and \
                    not any(s in p or p in s for p, _ in picked):
                picked.append((s, i))
                i += max(L, 1)
            else:
                i += 1
        L -= 1
    picked.sort(key=lambda t: (-len(t[0]), t[1]))
    return [p for p, _ in picked[:cap]]


def episode_corpus_str(ep):
    return "\n".join(episode_text_corpus(ep))


def turn_texts(turn):
    inp = turn.get("input") or {}
    out = [(ms.get("text") or "") for ms in (inp.get("messages") or [])]
    if inp.get("note"):
        out.append(inp["note"])
    return [t for t in out if t]


def source_message_texts(atom, ep, turns_by_id, global_turn_index):
    """atom.source 指向的消息文本列表（支持跨 episode；与 quote_check 同定位规则）。"""
    src = atom.get("source") or {}
    cid = src.get("case_id")
    turn = turns_by_id.get(cid)
    if cid and turn is None and global_turn_index and cid in global_turn_index:
        w2, e2 = global_turn_index[cid]
        try:
            src_ep = json.load(open(os.path.join(STORIES, w2, e2,
                                                 "episode.json"), encoding="utf-8"))
            turn = next((t for t in src_ep.get("turns", [])
                         if t.get("case_id") == cid), None)
        except OSError:
            turn = None
    if turn is None:
        return []
    msgs = (turn.get("input") or {}).get("messages") or []
    mid = src.get("message_id")
    idx = None
    if isinstance(mid, str) and mid.startswith("m") and mid[1:].isdigit():
        idx = int(mid[1:])
    elif isinstance(mid, int):
        idx = mid
    if idx is not None and 0 <= idx < len(msgs):
        return [msgs[idx].get("text") or ""]
    return [(ms.get("text") or "") for ms in msgs]


def _find_cues(corpus_str):
    return [w for w in CUE_WORDS if w in corpus_str]


def enrich_cancel_atom(atom, ep, turns_by_id, corpus_str, global_turn_index):
    """§8 #1/#2：为取消族（status∈CANCEL_FAMILY 或 negation=true）原子生成
    affirm_markers（+ cancelled/canceled 事项原子的 item_markers）。
    返回 (markers|None, item_markers|None, prov dict)。提不出逐字信号 → (None,...)，
    该原子保持 unrated（诚实登记，不猜关键词）。"""
    value = atom.get("value") or ""
    quote = (atom.get("source") or {}).get("quote") or ""
    src_texts = source_message_texts(atom, ep, turns_by_id, global_turn_index)
    phrases = []
    src_used = None
    for t in src_texts:                                  # 1) value ∩ 源消息
        phrases = max_common_phrases(value, t, 2, 4)
        if phrases:
            src_used = "value@source_message"
            break
    if not phrases and value:                            # 2) value ∩ 全 episode 语料
        phrases = max_common_phrases(value, corpus_str, 2, 4)
        src_used = "value@episode_corpus" if phrases else None
    if not phrases:
        for t in src_texts:                              # 3) quote ∩ 源消息
            phrases = max_common_phrases(quote, t, 2, 4)
            if phrases:
                src_used = "quote@source_message"
                break
    if not phrases and quote:                            # 4) quote ∩ 全 episode 语料
        phrases = max_common_phrases(quote, corpus_str, 2, 4)
        src_used = "quote@episode_corpus" if phrases else None
    if not phrases:
        return None, None, {"reason": "no_verbatim_signal"}
    cues = _find_cues(corpus_str)
    # 精度门：单短语组必须 ≥MIN_SINGLE_PHRASE_LEN（提及≠坐实）；多短语 AND 组
    # （组内全词同现才算坐实）自带约束，保留 ≥2 字短语。
    if len(phrases) == 1 and len(phrases[0]) < MIN_SINGLE_PHRASE_LEN:
        return None, None, {"reason": "single_phrase_too_short",
                            "phrases": phrases}
    markers = [phrases[:2]]                              # 单组 AND（§8 例：组内全词同现）
    item_markers = None
    if atom.get("status") in ITEM_MARKER_STATUSES and len(phrases[0]) >= 4:
        item_markers = [phrases[0]]
    prov = {"cue_words_found": cues, "marker_source": src_used,
            "phrases": phrases}
    return markers, item_markers, prov


def enrich_unknown(topic, texts_of_turn):
    """§8 #3：unknown 字符串 → {topic, affirm_markers}（顶层并存）。
    锚点 = topic 与本轮题面的最长逐字公共子串（≥4 字；提及≠坐实，短锚点一律
    不升级）；层 1 无法区分提问与坐实，故只取最长、最具体的单一锚点，宁缺毋滥。
    无锚点 → 返回 None，保持原字符串（诚实 unrated，不猜关键词）。"""
    anchors = max_common_phrases(topic, "\n".join(texts_of_turn), 4, 3)
    if not anchors:
        return None
    return {"topic": topic, "affirm_markers": [[anchors[0]]]}


def enrich_intent_keywords(intent, acceptable_actions, texts_of_turn):
    """§8 #4：C10 keywords_any。信号词 = 意图文本/acceptable_actions 与本轮题面
    （消息+备注）的最长逐字公共子串（≥2 字，去重去包含，≤6 组，每组单词任一命中）。
    must_not_keywords 无确定性来源 → 留空（诚实，不造禁区词）。"""
    mian = "\n".join(texts_of_turn)
    sources = []
    if isinstance(intent, dict):
        intent = intent.get("text")
    if isinstance(intent, str) and intent.strip():
        sources.append(intent)
    for a in acceptable_actions or []:
        if isinstance(a, str) and a:
            sources.append(a)
    kws = []
    for s in sources:
        for p in max_common_phrases(s, mian, 2, 6):
            if not any(p in k or k in p for k in kws):
                kws.append(p)
    kws.sort(key=len, reverse=True)
    return [[k] for k in kws[:6]]


def is_must_not_affirm(atom):
    return (atom.get("status") in CANCEL_FAMILY) or bool(atom.get("negation"))


def build_turn_record(wave, episode_id, turn_index, turn, ep,
                      episode_sha, card, card_sources, card_conflicts,
                      global_turn_index):
    cid = turn.get("case_id")
    inp = turn.get("input") or {}
    atoms = copy.deepcopy((card or {}).get("required_memory_atoms") or [])
    forbidden = (card or {}).get("forbidden_factual_claims") or []
    unknowns = (card or {}).get("unknowns") or []
    card_intent = (card or {}).get("intent") if card else None
    ep_intent = inp.get("intent")

    if card_intent:
        intent, intent_source = card_intent, "card"
    elif ep_intent:
        intent, intent_source = ep_intent, "episode_input"
    else:
        intent, intent_source = None, None

    # may_decline 以 SEALED episode 的 input.may_decline 为准（评分器实际消费
    # case.input.may_decline）；卡值仅作 fallback，并登记与 episode 的分歧。
    ep_md = inp.get("may_decline")
    card_md = (card or {}).get("may_decline")
    if ep_md is not None:
        may_decline, md_source = bool(ep_md), "episode_input"
    elif card_md is not None:
        may_decline, md_source = bool(card_md), "card"
    else:
        may_decline, md_source = None, None
    md_conflict = (card_md is not None and ep_md is not None
                   and bool(card_md) != bool(ep_md))

    gold_empty = (card is None) or not (atoms or forbidden or unknowns or intent)

    qc = {"n_quotes": 0, "strict_hits": 0, "lenient_hits": 0, "failures": []}
    turns_by_id = {t.get("case_id"): t for t in ep.get("turns", [])}
    for a in atoms:
        r = quote_check(a, ep, turns_by_id, global_turn_index)
        if r["strict"] is None and r["lenient"] is None:
            continue
        qc["n_quotes"] += 1
        if r["strict"]:
            qc["strict_hits"] += 1
        if r["lenient"]:
            qc["lenient_hits"] += 1
        if not r["lenient"]:
            qc["failures"].append(a.get("atom_id"))

    record = {
        "schema_version": SCHEMA_VERSION,
        "case_id": cid,
        "wave": wave,
        "episode_id": episode_id,
        "turn_index": turn_index,
        "turn_type": turn.get("turn_type"),
        "gold_empty": bool(gold_empty),
        "assertions": atoms,                      # 逐字拷贝（v2 仅新增判定信号字段）
        "intent_contract": {
            "intent": intent,                     # {enabled,status,text} 逐字
            "intent_source": intent_source,
            "may_decline": may_decline,
            "may_decline_source": md_source,
            "acceptable_actions": (card or {}).get("acceptable_actions"),
        },
        "forbidden": {
            "forbidden_factual_claims": forbidden,
            "unknowns": unknowns,                 # v1 原样保留（字符串形态不动）
        },
        "protected_expressions": (card or {}).get("protected_expressions"),
        "card_extras": {
            "current_action": (card or {}).get("current_action"),
            "private_guidance": (card or {}).get("private_guidance"),
            "applicable_dimensions": (card or {}).get("applicable_dimensions"),
        },
        "scorer_gold": {},  # 忠实可接线子集；事实断言入口=assertions，见模块 docstring
        "provenance": {
            "card_found": card is not None,
            "card_sources": card_sources,
            "card_conflicts": card_conflicts,
            "may_decline_conflict_with_card": md_conflict,
            "episode_file": os.path.join("benchmark", "stories_v2", wave,
                                         episode_id, "episode.json").replace("\\", "/"),
            "episode_sha256": episode_sha,
        },
        "qc": qc,
    }

    # ---- v2 enrichment（GOLD_SCHEMA.md §8；只新增字段，既有字段逐字节不动） ----
    turns_by_id = {t.get("case_id"): t for t in ep.get("turns", [])}
    corpus_str = episode_corpus_str(ep)
    this_texts = turn_texts(turn)
    enr = {"markers_added": 0, "markers_skipped_no_signal": 0,
           "item_markers_added": 0, "unknowns_upgraded": 0,
           "unknowns_string_kept": 0, "intent_keywords_groups": 0,
           "marker_provenance": {}}
    for a in atoms:
        if not is_must_not_affirm(a):
            continue
        markers, item_markers, prov = enrich_cancel_atom(
            a, ep, turns_by_id, corpus_str, global_turn_index)
        if markers:
            a["affirm_markers"] = markers
            enr["markers_added"] += 1
        else:
            enr["markers_skipped_no_signal"] += 1
        if item_markers:
            a["item_markers"] = item_markers
            enr["item_markers_added"] += 1
        if markers or item_markers:
            enr["marker_provenance"][a.get("atom_id") or a.get("id")] = prov
    # §8 #3：顶层并存 unknowns（升级形态 {topic, affirm_markers} + 原字符串保留）
    unknowns_v2 = []
    for u in unknowns:
        if isinstance(u, str):
            obj = enrich_unknown(u, this_texts)
            if obj is not None:
                unknowns_v2.append(obj)
                enr["unknowns_upgraded"] += 1
            else:
                unknowns_v2.append(u)
                enr["unknowns_string_kept"] += 1
        else:
            unknowns_v2.append(u)
    if unknowns_v2:
        record["unknowns"] = unknowns_v2
    # §8 #4：intent_contract.keywords_any（must_not_keywords 无确定性来源 → 留空登记）
    if intent:
        ka = enrich_intent_keywords(intent,
                                    (card or {}).get("acceptable_actions"),
                                    this_texts)
        record["intent_contract"]["keywords_any"] = ka
        record["intent_contract"]["must_not_keywords"] = []
        enr["intent_keywords_groups"] = len(ka)
    record["enrichment"] = enr
    return record


_GLOBAL_TURN_INDEX = {}


def get_global_turn_index():
    """case_id -> (wave, episode_id)，支持跨 episode 连续剧情的 source 引用。"""
    if not _GLOBAL_TURN_INDEX:
        for wave in WAVES:
            wdir = os.path.join(STORIES, wave)
            for d in os.listdir(wdir):
                if os.path.isfile(os.path.join(wdir, d, "episode.json")):
                    ep = json.load(open(os.path.join(wdir, d, "episode.json"),
                                        encoding="utf-8"))
                    for t in ep.get("turns", []):
                        _GLOBAL_TURN_INDEX[t.get("case_id")] = (wave, d)
    return _GLOBAL_TURN_INDEX


def build_wave(wave):
    wdir = os.path.join(STORIES, wave)
    episodes = sorted(d for d in os.listdir(wdir)
                      if os.path.isfile(os.path.join(wdir, d, "episode.json")))
    records = []
    stats = {"episodes": len(episodes), "turns": 0, "with_assertions": 0,
             "empty_gold": 0, "no_card": 0, "atoms": 0, "forbidden": 0,
             "unknowns": 0, "intent_contracts": 0, "quote_strict_hits": 0,
             "quote_lenient_hits": 0, "quotes_total": 0, "source_conflicts": 0,
             "must_not_affirm_atoms": 0, "atoms_with_affirm_markers": 0,
             "atoms_affirm_no_signal": 0, "atoms_with_item_markers": 0,
             "unknowns_upgraded": 0, "unknowns_string_kept": 0,
             "intent_turns_with_keywords": 0, "intent_keywords_groups": 0}
    per_episode = {}
    for e in episodes:
        ep_path = os.path.join(wdir, e, "episode.json")
        ep_sha = sha256_file(ep_path)
        ep = json.load(open(ep_path, encoding="utf-8"))
        ref_path = os.path.join(wdir, e, "reference.json")
        ref_turns = {}
        if os.path.isfile(ref_path):
            ref = json.load(open(ref_path, encoding="utf-8"))
            ref_turns = ref.get("turns", {}) if isinstance(ref, dict) else {}
        est = {"turns": 0, "with_assertions": 0, "empty_gold": 0}
        for i, t in enumerate(ep.get("turns", [])):
            cid = t.get("case_id")
            card, sources, conflicts = resolve_card(wave, cid)
            if card is None and cid in ref_turns:
                card = ref_turns[cid]
                sha = sha256_file(ref_path)
                sources = [{"file": os.path.relpath(ref_path, ROOT), "sha256": sha}]
            rec = build_turn_record(wave, e, i, t, ep, ep_sha,
                                    card, sources, conflicts,
                                    get_global_turn_index())
            records.append(rec)
            est["turns"] += 1
            stats["turns"] += 1
            if rec["assertions"]:
                stats["with_assertions"] += 1
                est["with_assertions"] += 1
            if rec["gold_empty"]:
                stats["empty_gold"] += 1
                est["empty_gold"] += 1
            if card is None:
                stats["no_card"] += 1
            stats["atoms"] += len(rec["assertions"])
            stats["forbidden"] += len(rec["forbidden"]["forbidden_factual_claims"])
            stats["unknowns"] += len(rec["forbidden"]["unknowns"])
            if rec["intent_contract"]["intent"]:
                stats["intent_contracts"] += 1
            stats["quotes_total"] += rec["qc"]["n_quotes"]
            stats["quote_strict_hits"] += rec["qc"]["strict_hits"]
            stats["quote_lenient_hits"] += rec["qc"]["lenient_hits"]
            if conflicts:
                stats["source_conflicts"] += 1
            enr = rec["enrichment"]
            for a in rec["assertions"]:
                if is_must_not_affirm(a):
                    stats["must_not_affirm_atoms"] += 1
                    if a.get("affirm_markers"):
                        stats["atoms_with_affirm_markers"] += 1
                    elif not a.get("item_markers"):
                        stats["atoms_affirm_no_signal"] += 1
            stats["atoms_with_item_markers"] += enr["item_markers_added"]
            stats["unknowns_upgraded"] += enr["unknowns_upgraded"]
            stats["unknowns_string_kept"] += enr["unknowns_string_kept"]
            if rec["intent_contract"].get("keywords_any"):
                stats["intent_turns_with_keywords"] += 1
                stats["intent_keywords_groups"] += enr["intent_keywords_groups"]
        per_episode[e] = est
    return {"records": records, "stats": stats, "per_episode": per_episode}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--seed", type=int, default=20261101)
    ap.add_argument("--qc-sample", type=int, default=40)
    args = ap.parse_args()

    all_out = {}
    total_stats = {}
    for wave in WAVES:
        res = build_wave(wave)
        all_out[wave] = res
        total_stats[wave] = res["stats"]
        s = res["stats"]
        print("%-10s turns=%5d with_assert=%5d empty=%4d no_card=%d "
              "atoms=%5d quotes=%5d strict_hit=%5d conflicts=%d"
              % (wave, s["turns"], s["with_assertions"], s["empty_gold"],
                 s["no_card"], s["atoms"], s["quotes_total"],
                 s["quote_strict_hits"], s["source_conflicts"]))

    # ---- QC：随机抽 40 turn 复核 quote 逐字命中 ----
    rng = random.Random(args.seed)
    pool = [(w, r) for w in WAVES for r in all_out[w]["records"]
            if r["qc"]["n_quotes"] > 0]
    sample = rng.sample(pool, min(args.qc_sample, len(pool)))
    qc_rows = []
    n_strict = n_lenient = 0
    for wave, r in sample:
        ep = json.load(open(os.path.join(ROOT, r["provenance"]["episode_file"]),
                            encoding="utf-8"))
        ok_s = ok_l = True
        fails = []
        for a in r["assertions"]:
            rr = quote_check(a, ep, {t.get("case_id"): t for t in ep["turns"]},
                             get_global_turn_index())
            if rr["strict"] is False:
                ok_s = False
            if rr["lenient"] is False:
                ok_l = False
                fails.append(a.get("atom_id"))
        n_strict += ok_s
        n_lenient += ok_l
        qc_rows.append({"case_id": r["case_id"], "wave": wave,
                        "strict_all_pass": ok_s, "lenient_all_pass": ok_l,
                        "failed_atoms": fails})
    print("QC sample n=%d strict_all_pass=%d lenient_all_pass=%d"
          % (len(sample), n_strict, n_lenient))

    # ---- v2 QC：全部 markers 逐字回验（必须 100% 出现在题面语料） ----
    n_markers = n_marker_bad = 0
    bad_examples = []
    for wave in WAVES:
        for r in all_out[wave]["records"]:
            ep = json.load(open(os.path.join(ROOT, r["provenance"]["episode_file"]),
                                encoding="utf-8"))
            corpus = episode_corpus_str(ep)
            turns_by_id = {t.get("case_id"): t for t in ep.get("turns", [])}
            for a in r["assertions"]:
                words = []
                for g in a.get("affirm_markers") or []:
                    words += [w for w in (g if isinstance(g, list) else [g]) if w]
                words += list(a.get("item_markers") or [])
                for w in words:
                    n_markers += 1
                    in_corpus = w in corpus
                    in_src = any(w in t for t in source_message_texts(
                        a, ep, turns_by_id, get_global_turn_index()))
                    if not (in_corpus or in_src):
                        n_marker_bad += 1
                        if len(bad_examples) < 5:
                            bad_examples.append({"case_id": r["case_id"],
                                                 "atom_id": a.get("atom_id"),
                                                 "word": w})
    print("Marker QC: n=%d verbatim_violations=%d %s"
          % (n_markers, n_marker_bad, json.dumps(bad_examples, ensure_ascii=False)
             if bad_examples else ""))

    if args.dry_run:
        return 0

    os.makedirs(OUTDIR, exist_ok=True)
    file_hashes = {}
    for wave in WAVES:
        fp = os.path.join(OUTDIR, "GOLD_%s.json" % wave)
        payload = {
            "schema_version": SCHEMA_VERSION,
            "wave": wave,
            "generated": datetime.datetime.now().isoformat(timespec="seconds"),
            "stats": all_out[wave]["stats"],
            "per_episode": all_out[wave]["per_episode"],
            "turns": all_out[wave]["records"],
        }
        with open(fp, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=1)
        file_hashes[os.path.basename(fp)] = sha256_file(fp)

    gtot = {k: sum(v[k] for v in total_stats.values())
            for k in next(iter(total_stats.values()))}
    index = {
        "schema_version": SCHEMA_VERSION,
        "generated": datetime.datetime.now().isoformat(timespec="seconds"),
        "tool": "tools/build_gold_sidecar.py",
        "source_policy": (
            "卡优先级：跨 wave 汇编修订卡(wave3/GRADING_CARDS_W19_CROSS.json，仅 "
            "wave3_ext，mtime 最新)→ 本 wave GRADING_CARDS*.json（文件名序，PROVENANCE "
            "除外）→ 该 episode reference.json.turns。may_decline 以 SEALED episode 的 "
            "input.may_decline 为准（与评分器消费口径一致）。多源内容一致时取最高优先级"
            "并登记全部来源；内容冲突登记 provenance.card_conflicts。"
            "episode.json/manifest 只读未动。"),
        "enrichment_policy": (
            "v2 按 GOLD_SCHEMA.md §8 新增四类判定信号，既有内容逐字节保留："
            "①取消族（status∈cancelled/canceled/superseded/retracted/voided/revoked/"
            "invalidated/negated/erroneous 或 negation=true）原子加 affirm_markers（单组 "
            "AND，短语=value/quote 与题面语料的最长逐字公共子串，优先源消息；需 episode "
            "语料中出现更正/取消线索词，词表见 tools/build_gold_sidecar.py CUE_WORDS，仅"
            "门控不直接做 marker；单短语组 ≥4 字才实例化——提及≠坐实，宁 unrated 不误判）；"
            "②status=cancelled/canceled 事项原子加 item_markers"
            "（复用 C8）；③unknown 字符串升级为 {topic, affirm_markers} 并存于顶层 "
            "unknowns（锚点=topic 与本轮题面 ≥4 字最长逐字公共子串，无锚点保留原字符串，"
            "诚实 unrated 不猜关键词）；④intent_contract 加 keywords_any（意图文本/"
            "acceptable_actions 与本轮题面的 ≥2 字逐字公共子串，≤6 组）；"
            "must_not_keywords 无确定性来源一律留空不造禁区词。全部 markers 经逐字"
            "回验 QC（marker_qc.verbatim_violations 必须为 0）。"),
        "marker_qc": {"n_markers": n_markers, "verbatim_violations": n_marker_bad},
        "totals": gtot,
        "per_wave": total_stats,
        "qc_sample": {
            "seed": args.seed, "n": len(sample),
            "strict_all_pass": n_strict, "lenient_all_pass": n_lenient},
        "files_sha256": file_hashes,
    }
    idx_path = os.path.join(OUTDIR, "GOLD_INDEX.json")
    with open(idx_path, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
    file_hashes["GOLD_INDEX.json"] = sha256_file(idx_path)

    qc_path = os.path.join(OUTDIR, "GOLD_QC_SAMPLE.json")
    with open(qc_path, "w", encoding="utf-8") as f:
        json.dump({"seed": args.seed, "rows": qc_rows}, f,
                  ensure_ascii=False, indent=1)
    file_hashes["GOLD_QC_SAMPLE.json"] = sha256_file(qc_path)

    # ---- 账本：append-only 事件（不动 stories 条目） ----
    ledger = story_custody.load()
    ledger.setdefault("events", []).append({
        "kind": "gold_sidecar_build",
        "date": datetime.date.today().isoformat(),
        "batch": "GOLD_SIDECAR_v2",
        "arms": ["offline_build_zero_model_calls"],
        "tool": "tools/build_gold_sidecar.py",
        "waves": WAVES,
        "totals": gtot,
        "marker_qc": {"n_markers": n_markers, "verbatim_violations": n_marker_bad},
        "index_sha256": file_hashes["GOLD_INDEX.json"],
        "note": ("离线 gold sidecar 生成（v2：GOLD_SCHEMA.md §8 四类判定信号 enrichment，"
                 "v1 断言体逐字节保留）；episode 全部 SEALED 未改动；"
                 "本事件只登记产物 hash，不构成任何故事对候选暴露。"),
    })
    story_custody.save(ledger)
    print("ledger events appended ->", story_custody.LEDGER)
    print("GOLD_INDEX totals:", json.dumps(gtot, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
