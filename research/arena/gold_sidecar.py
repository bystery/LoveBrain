# gold_sidecar.py — GOLD_SIDECAR 加载与 gold 映射适配器（离线、零模型调用）。
# 配套 benchmark/stories_v2/GOLD_SCHEMA.md v1.0（schema 权威）；rubric.md §2/§10 为判定权威。
# 职责：
#   1) find_turn_gold(sidecar_dir, episode_id, case_id) —— 三种文件布局探测；
#   2) apply_sidecar(case, turn_gold) —— 把 sidecar 字段映射进既有 rubric gold 键
#      （C3/C6/C8/C10/C11/C12/v0.5 静默断言全部复用既有检查器），并产出新增
#      must-not-affirm 检查所需的 assertion_checks / unknown_must_not_affirm 两个新 gold 键
#      （对应 lb_scorer C14/C15；老批次无这两个键 → 行为与历史逐字节一致）。
#   3) 诚实登记：confirmed 原子与无 affirm_markers 的 must-not-affirm 原子不判分，
#      记入 meta["unrated_assertions"]；gold_empty=true 时完全不映射。
# 本模块不做任何语义推理；断言矛盾判定归层 2（人工/校准 judge）。
import copy
import json
import os


SIDECAR_SCHEMA_VERSION = "1.0"

# must-not-affirm 状态族：事实已不成立/已作废/被取代，回复不得再当真坐实。
# （cancelled/canceled=取消；superseded/retracted/revised 被更正取代；voided/revoked/
#   invalidated/negated/erroneous=作废否定。suspended/paused/stalled 等只是暂停，
#   不属"已不成立"，不进本族——保守口径，语义归层 2。）
MUST_NOT_AFFIRM_STATUS = ("cancelled", "canceled", "superseded", "retracted",
                          "voided", "revoked", "invalidated", "negated",
                          "erroneous")

# GOLD_SIDECAR_v1 波次文件布局（tools/build_gold_sidecar.py 产物）缓存：
# {abs_sidecar_dir: {case_id: turn_gold}}
_WAVE_INDEX_CACHE = {}


def _wave_index(sidecar_dir):
    """布局 4：GOLD_<wave>.json（GOLD_SIDECAR_v1），turns 为 list，按 case_id 建索引。
    只读文件、零模型调用；首次访问后按目录缓存。"""
    key = os.path.abspath(sidecar_dir)
    idx = _WAVE_INDEX_CACHE.get(key)
    if idx is not None:
        return idx
    idx = {}
    try:
        names = sorted(os.listdir(sidecar_dir))
    except OSError:
        names = []
    for nm in names:
        if not (nm.startswith("GOLD_") and nm.endswith(".json")):
            continue
        if "INDEX" in nm or "QC_SAMPLE" in nm:
            continue
        data = _load(os.path.join(sidecar_dir, nm))
        if not isinstance(data, dict) or not isinstance(data.get("turns"), list):
            continue
        for t in data["turns"]:
            if isinstance(t, dict) and t.get("case_id"):
                idx[t["case_id"]] = t          # 先到先得；跨 wave 冲突由 gold 侧登记
    _WAVE_INDEX_CACHE[key] = idx
    return idx


def find_turn_gold(sidecar_dir, episode_id, case_id):
    """按四种布局探测，返回 turn 级 gold dict 或 None。
    1) <EP>.gold.json / <EP>.json（含 turns map）；2) 扁平单篇；
    3) <case_id>.gold.json / <case_id>.json；4) GOLD_SIDECAR_v1 波次文件
    （GOLD_<wave>.json，turns 为 list，按 case_id 索引）。"""
    if not sidecar_dir or not os.path.isdir(sidecar_dir):
        return None
    ep_names = ["%s.gold.json" % episode_id, "%s.json" % episode_id]
    turn_names = ["%s.gold.json" % case_id, "%s.json" % case_id]
    # 布局 3：每轮一文件
    for nm in turn_names:
        p = os.path.join(sidecar_dir, nm)
        if os.path.isfile(p):
            data = _load(p)
            if isinstance(data, dict):
                inner = data.get("turns", {}).get(case_id) if "turns" in data else data
                if isinstance(inner, dict):
                    return inner
    # 布局 2：扁平单篇（单轮 episode）
    for nm in ep_names:
        p = os.path.join(sidecar_dir, nm)
        if os.path.isfile(p):
            data = _load(p)
            if not isinstance(data, dict):
                continue
            if "turns" in data:
                inner = data.get("turns", {}).get(case_id)
                if isinstance(inner, dict):
                    return inner
            else:
                return data
    # 布局 4：GOLD_SIDECAR_v1 波次文件（GOLD_<wave>.json，turns 为 list）
    return _wave_index(sidecar_dir).get(case_id)


def _load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def _group_hit(texts, groups):
    """affirm_markers 词组判定：组内全部词出现在同一可发送文本 = 该组命中；
    任一组命中 = 坐实。texts: [str]"""
    for group in groups or []:
        words = [w for w in (group if isinstance(group, list) else [group]) if w]
        if words and any(all(w in t for w in words) for t in texts):
            return group
    return None


def apply_sidecar(case, turn_gold):
    """turn_gold（单轮 sidecar dict）映射进 case 的 gold；返回 (new_case, meta)。
    不修改入参；turn_gold 为 None 或 gold_empty 时原样返回（新副本）+ meta。"""
    new_case = copy.deepcopy(case)
    gold = new_case.setdefault("gold", {})
    meta = {"sidecar_applied": False, "gold_empty": True,
            "mapped_keys": [], "unrated_assertions": [],
            "assertion_checks": 0, "unknown_checks": 0}
    if not isinstance(turn_gold, dict) or turn_gold.get("gold_empty"):
        return new_case, meta
    meta["sidecar_applied"] = True
    meta["gold_empty"] = False

    # ---- 直通字段（语义与既有检查器一致，rubric C11/C12/静默断言） ----
    for key in ("stale_fact_must_not", "geo_impossible_must_not",
                "geo_reason", "expect_decline",
                "expected_topic_status", "expected_topic_label_contains"):
        if turn_gold.get(key) is not None:
            gold[key] = turn_gold[key]
            meta["mapped_keys"].append(key)

    # ---- forbidden（C3/C6 复用） ----
    forbidden = turn_gold.get("forbidden") or {}
    mnk = forbidden.get("must_not_keywords") or turn_gold.get("must_not_keywords") or []
    if mnk:
        gold["must_not_keywords"] = list(gold.get("must_not_keywords") or []) + list(mnk)
        meta["mapped_keys"].append("forbidden.must_not_keywords")
    fnf = forbidden.get("forbidden_new_facts") or []
    if fnf:
        gold["forbidden_new_facts"] = list(gold.get("forbidden_new_facts") or []) + list(fnf)
        meta["mapped_keys"].append("forbidden.forbidden_new_facts")

    # ---- intent_contract（C10 复用 + decline 方向映射） ----
    ic = turn_gold.get("intent_contract") or {}
    if isinstance(ic, dict) and ic:
        has_input_intent = bool((new_case.get("input") or {}).get("intent"))
        if has_input_intent:
            if ic.get("behavior_direction") == "decline":
                gold["expect_decline"] = True
                meta["mapped_keys"].append("intent_contract.behavior_direction=decline")
            ka = ic.get("keywords_any") or []
            if ka:
                gold["intent_keywords_any"] = list(gold.get("intent_keywords_any") or []) + list(ka)
                meta["mapped_keys"].append("intent_contract.keywords_any")
            mk = ic.get("must_not_keywords") or []
            if mk:
                gold["intent_must_not_keywords"] = list(gold.get("intent_must_not_keywords") or []) + list(mk)
                meta["mapped_keys"].append("intent_contract.must_not_keywords")

    # ---- assertions（C8 复用 + 新 C14 must-not-affirm） ----
    assertion_checks = []
    for a in turn_gold.get("assertions") or []:
        if not isinstance(a, dict):
            continue
        aid = a.get("id") or a.get("atom_id")     # GOLD_SIDECAR_v1 用 atom_id
        status = a.get("status")
        negation = bool(a.get("negation"))
        must_not_affirm = (status in MUST_NOT_AFFIRM_STATUS) or negation
        markers = a.get("affirm_markers") or []
        item_markers = a.get("item_markers") or []
        if not must_not_affirm:
            # confirmed/tentative：只作证据，不判分、不强迫提及（rubric §10）
            meta["unrated_assertions"].append({
                "id": aid, "value": a.get("value"),
                "status": status, "reason": "confirmed_evidence_only"})
            continue
        entry = {"id": aid, "value": a.get("value"),
                 "status": status, "negation": negation,
                 "affirm_markers": markers}
        checkable = False
        if markers:
            assertion_checks.append(entry)
            checkable = True
        if status == "cancelled" and item_markers:
            gold["cancelled_items"] = list(gold.get("cancelled_items") or []) + list(item_markers)
            meta["mapped_keys"].append("assertions.cancelled_items(%s)" % aid)
            checkable = True
        if not checkable:
            meta["unrated_assertions"].append({
                "id": aid, "value": a.get("value"),
                "status": status, "reason": "no_affirm_markers"})
    if assertion_checks:
        gold["assertion_checks"] = assertion_checks
        meta["assertion_checks"] = len(assertion_checks)

    # ---- unknowns（新 C15：不确定不当确定） ----
    # GOLD_SIDECAR_v1 的 unknowns 是纯字符串（无判定信号）→ 诚实记 unrated，
    # 不猜关键词；补齐 {topic, affirm_markers} 形态（GOLD_SCHEMA.md §5）后才实例化。
    unknown_checks = []
    unknowns_raw = (turn_gold.get("unknowns")
                    or (turn_gold.get("forbidden") or {}).get("unknowns") or [])
    for u in unknowns_raw:
        if isinstance(u, dict) and u.get("affirm_markers"):
            unknown_checks.append({"topic": u.get("topic"),
                                   "affirm_markers": u["affirm_markers"]})
        else:
            meta["unrated_unknowns"] = meta.get("unrated_unknowns", 0) + 1
    if unknown_checks:
        gold["unknown_must_not_affirm"] = unknown_checks
        meta["unknown_checks"] = len(unknown_checks)

    # ---- GOLD_SIDECAR_v1 特有字段（不进层 1 判分的诚实登记） ----
    v1_forbidden = turn_gold.get("forbidden") or {}
    claims = v1_forbidden.get("forbidden_factual_claims") or []
    if claims:
        meta.setdefault("notes", []).append(
            "forbidden_factual_claims=%d 条为自然语言禁令，层 1 无子串判定信号，"
            "归层 2（如需 C6 判分请按 GOLD_SCHEMA.md 提供 forbidden_new_facts 子串）"
            % len(claims))
    meta["n_assertions"] = len(turn_gold.get("assertions") or [])

    return new_case, meta


def sendable_texts_of(out, contract):
    """取候选可发送文本（供外部演示/校验复用；与 lb_scorer 同源）。"""
    try:
        from protocol_public import all_reply_texts
    except ImportError:                     # 包路径导入时兜底
        from arena.protocol_public import all_reply_texts
    return [t for _, t in all_reply_texts(out, contract or {}) if isinstance(t, str)]


def group_hit(texts, groups):
    """暴露词组判定（单测与诊断用）。texts: [str]"""
    return _group_hit(texts, groups) is not None
