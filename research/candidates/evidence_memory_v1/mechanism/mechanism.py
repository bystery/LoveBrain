# candidates/evidence_memory_v1/mechanism/mechanism.py
#
# evidence_memory_v1 — C3 证据记忆与上下文（设计稿 §C3）。
# 基座：v5_purpose_aligned 闭包（四 system 资产逐字节继承；差异全在 user 侧记忆层）。
# 五分离存储（events/facts/tentatives/goals/notes，带出处）+ 预算化投影
# （当前窗>events近窗>facts相关>tentatives>goal；当前窗永不挤掉；关目标不删真实约定）。
# facts/tentatives 提取：模型每轮 analysis 报告 + 机制校验（src_uids∈本轮、哈希去重）；本地零额外调用。
import hashlib
import importlib.util
import json
import os
import sys
import time

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)
_ROOT = os.path.dirname(_CAND)
_V5 = os.path.join(_ROOT, "v5_purpose_aligned")  # staging trigger: candidates/v5_purpose_aligned


def _load_v5():
    spec = importlib.util.spec_from_file_location(
        "v5_mechanism_for_evidence_memory",
        os.path.join(_V5, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


v5 = _load_v5()
base = v5.base
rc = base.rc
build_system_prompt = base.build_system_prompt

BUDGET_FACTS_CU = 500
BUDGET_TENTATIVES_CU = 300
RELEVANCE_FLOOR = 0.12          # 字符二元组重合率下限（通用 n-gram，无领域词）
SRC_SUPPORT_FLOOR = 0.15        # v1.1：报告事实与其所引消息的最小支持度（子串命中或二元组重合率）


def _alias_to_uid(s, alias_map):
    """模型报告的 src 可能是本轮别名(m0)或历史 uid(int)；别名→uid 映射，认不出的拒绝。"""
    if alias_map and s in alias_map:
        return alias_map[s]
    if isinstance(s, int):
        return s
    return None


def _norm(text):
    return "".join((text or "").split()).lower()


def _bigrams(text):
    t = _norm(text)
    if len(t) < 2:
        return {t} if t else set()
    return {t[i:i + 2] for i in range(len(t) - 1)}


def _fact_key(text):
    return hashlib.sha256(_norm(text).encode("utf-8")).hexdigest()[:16]


def _relevance(item_text, signal_bigrams):
    bg = _bigrams(item_text)
    if not bg or not signal_bigrams:
        return 0.0
    return len(bg & signal_bigrams) / len(bg)


def _seed_static_facts(h, initial_kb, persona):
    """v1.2（P04-03/F3 收尾，2026-10-27）：静态知识（initial_kb 标量与 persona.me/her）以带 src 标签的
    种子事实入库（origin=kbin:<key> / persona:<key>，status=confirmed_seed）。
    收益：S1（可恢复）/S2（保真）对 C3 从 NR 变为可测（此前只有 ongoing_plan 入库，warmth/her/persona
    无存储态——E008_T03"小林=牵线人"类库内事实无库可查）。
    投影边界（防重复注入）：origin 以 kbin:/persona: 开头的静态种子**不参与** _facts_projection——
    它们本就经 seed 块每轮全量注入；行为零变化。窗外召回的行为开关留待 M2 长故事实测后再开。"""
    for key, val in (initial_kb or {}).items():
        t = (val or "").strip() if isinstance(val, str) else ""
        if not t:
            continue
        h["mem"]["facts"]["kbin:%s" % key] = {
            "text": t, "src_uids": [], "ts": "", "status": "confirmed_seed",
            "origin": "kbin:%s" % key, "plan_status": ""}
    for key in ("me", "her"):
        t = ((persona or {}).get(key) or "").strip()
        if t:
            h["mem"]["facts"]["persona:%s" % key] = {
                "text": t, "src_uids": [], "ts": "", "status": "confirmed_seed",
                "origin": "persona:%s" % key, "plan_status": ""}


def init(assets_root, episode_meta, gateway, sandbox):
    h = v5.init(assets_root, episode_meta, gateway, sandbox)
    meta = episode_meta or {}
    plan = (meta.get("initial_kb") or {}).get("ongoing_plan") or []
    facts = {}
    for it in plan if isinstance(plan, list) else []:
        if isinstance(it, dict):
            name = (it.get("name") or it.get("item") or "").strip()
            if not name:
                continue
        facts[_fact_key(name)] = {
            "text": name, "src_uids": [], "ts": it.get("ts") or "",
            "status": "confirmed_seed", "origin": "initial_kb",
            "plan_status": it.get("status") or ""}
    h["mem"] = {"facts": facts, "tentatives": {}, "goal_history": []}
    _seed_static_facts(h, meta.get("initial_kb") or {}, h.get("persona") or {})
    return h


def _src_supported(text, src_texts):
    """v1.1：所引消息是否支持该事实文本——规范化子串命中，或二元组重合率达 SRC_SUPPORT_FLOOR。
    引用存在≠内容受支持（P04-02：独立复核确认 empty/999 均曾入 confirmed 库）。"""
    if not src_texts:
        return False
    nt = _norm(text)
    for st in src_texts:
        nst = _norm(st or "")
        if nt and nt in nst:
            return True
        if _relevance(text, _bigrams(st or "")) >= SRC_SUPPORT_FLOOR:
            return True
    return False


def _merge_reported(h, analysis, cur_uids, clock, alias_map=None, cur_texts=None):
    """模型报告的 facts/tentatives → 存储。

    v1.1（P04-02 最小修复，2026-10-27）：
      ① 成员硬验证：src 必须非空且全部 ∈ cur_uids（别名映射不出的、裸 int 不在当前轮的、空列表一律拒绝）；
        此前 cur_uids 形参从未使用——任意 int/空 src 均曾以 confirmed 入库。
      ② 引用支持度：事实文本须被所引消息支持（子串或二元组≥SRC_SUPPORT_FLOOR），
        不足者存为 status=unverified_src（只留审计、不参与投影、不计 confirmed）。
      ③ 已知边界（如实登记）：主体错换（如"我怎么认识小林"被存成"我和她怎么认识"）在所引消息
        与事实文本字面高度重合时仍可能通过 ②；语义主体校验交给评审层（FACT_CHECKPOINTS/裁判），
        本机制不做不可靠的名字特判。
    """
    cur_texts = cur_texts or {}
    added_f = added_t = invalid_src = unsupported_src = 0
    if not isinstance(analysis, dict):
        return added_f, added_t, invalid_src, unsupported_src
    for f in analysis.get("facts") or []:
        if not isinstance(f, dict):
            continue
        text = (f.get("text") or "").strip()
        src = [_alias_to_uid(s, alias_map) for s in (f.get("src_ids") or [])]
        if not text:
            continue
        if any(s is None for s in src):
            invalid_src += 1
            continue
        src = [s for s in src if s is not None]
        if not src or any(s not in cur_uids for s in src):
            invalid_src += 1                          # v1.1：空 src / 越权 uid（含裸 int 999）拒绝
            continue
        k = _fact_key(text)
        src_texts = [cur_texts.get(s, "") for s in src]
        supported = _src_supported(text, src_texts)
        cur = h["mem"]["facts"].get(k)
        if cur:
            if supported:
                cur["status"] = "confirmed"           # 再次确认且有据 → 状态刷新
        elif supported:
            h["mem"]["facts"][k] = {"text": text, "src_uids": list(src), "ts": clock or "",
                                    "status": "confirmed", "origin": "reported"}
            added_f += 1
        else:
            h["mem"]["facts"][k] = {"text": text, "src_uids": list(src), "ts": clock or "",
                                    "status": "unverified_src", "origin": "reported"}
            unsupported_src += 1
    for t in analysis.get("tentatives") or []:
        if not isinstance(t, dict):
            continue
        text = (t.get("text") or "").strip()
        src = [_alias_to_uid(s, alias_map) for s in (t.get("src_ids") or [])]
        if not text:
            continue
        if any(s is None for s in src):
            invalid_src += 1
            continue
        src = [s for s in src if s is not None]
        if not src or any(s not in cur_uids for s in src):
            invalid_src += 1
            continue
        k = _fact_key(text)
        if k not in h["mem"]["tentatives"]:
            h["mem"]["tentatives"][k] = {"text": text, "src_uids": list(src),
                                         "proposed_ts": clock or "", "status": "open",
                                         "origin": "reported"}
            added_t += 1
    return added_f, added_t, invalid_src, unsupported_src


def _facts_projection(h, signal_text, budget):
    """相关 facts：与信号(当前消息+目标)的二元组重合率排序；无信号按新近度兜底；
    confirmed/confirmed_seed 状态才入选；origin 以 kbin:/persona: 开头的静态种子排除
    （v1.2：它们经 seed 块每轮全量注入，重复投影会双份占预算）；返回(正文行, 使用CU, 命中数, 被滤数)。"""
    items = [f for f in h["mem"]["facts"].values()
             if str(f.get("status", "")).startswith("confirmed")
             and not str(f.get("origin", "")).startswith(("kbin:", "persona:"))]
    sig = _bigrams(signal_text)
    t0 = time.time()
    if sig:
        scored = sorted((( _relevance(f["text"], sig), f.get("ts") or "", f) for f in items),
                        key=lambda x: (-x[0], x[1]), reverse=False)
        scored.sort(key=lambda x: (-x[0], x[1]))
    else:
        scored = [(1.0, f.get("ts") or "", f) for f in items]
        scored.sort(key=lambda x: x[1], reverse=True)
    lines, used, hits, filtered = [], 0, 0, 0
    for rel, _ts, f in scored:
        if sig and rel < RELEVANCE_FLOOR:
            filtered += 1
            continue
        src = ("src=%s" % ",".join("m_src[%d]" % u for u in f["src_uids"])) if f["src_uids"] else "src=seed"
        line = "· %s（%s%s）" % (f["text"], src, (" @%s" % f["ts"]) if f["ts"] else "")
        cu = rc.utf16_len(line) + 1
        if used + cu > budget:
            filtered += 1
            continue
        lines.append(line)
        used += cu
        hits += 1
    body = "\n".join(lines)
    return body, used, hits, filtered, round((time.time() - t0) * 1000, 2)


def _tentatives_projection(h, budget):
    items = sorted(h["mem"]["tentatives"].values(),
                   key=lambda x: x.get("proposed_ts") or "", reverse=True)
    lines, used = [], 0
    for t in items:
        if t.get("status") != "open":
            continue
        line = "· %s（提出于%s）" % (t["text"], t.get("proposed_ts") or "未知时间")
        cu = rc.utf16_len(line) + 1
        if used + cu > budget:
            break
        lines.append(line)
        used += cu
    return "\n".join(lines), used, len(lines)


ANALYSIS_CONTRACT = (
    "# 记忆提取（写进 analysis，不另起调用）：本轮聊天里【双方都确认过的具体事实/约定】"
    "与【暂定事项（'再看/再说/看状态'类）】分别报告："
    '"analysis":{"facts":[{"text":"一句话事实","src_ids":["m0"]}],"tentatives":[{"text":"…","src_ids":["m0"]}]}；'
    "src_ids 只能填本轮 <chat> 里真实出现的 id；没提取就给空数组；只有对方单方面说的意向不算已确认事实，"
    "归 tentatives。正文四风格与方向仍按原格式。")


def run_turn(h, ctx, gw):
    inp = ctx.get("input") or {}
    clock = (inp.get("clock") or "").strip()
    seq = ctx.get("turn_index") or (h["turn_count"] + 1)
    raw_msgs = [m for m in (inp.get("messages") or []) if isinstance(m, dict)]

    cur = []
    for m in raw_msgs:
        uid = v5._uid_for(h, m.get("role"), m.get("text"), m.get("ts"))
        cur.append({"uid": uid, "role": m.get("role") or "",
                    "text": m.get("text") or "", "ts": m.get("ts") or ""})
    cur_uids = set(c["uid"] for c in cur)
    cur_texts = {c["uid"]: c["text"] for c in cur}
    prior_events = [e for e in h["events"] if e["uid"] not in cur_uids]

    intent_cfg = v5._intent_cfg_from_input(inp)
    goal_block = v5._goal_block(intent_cfg)

    parts = []
    seed = {}
    if h["persona"]:
        seed["persona"] = h["persona"]
    kb = {k: v for k, v in (h["initial_kb"] or {}).items() if k != "ongoing_plan"}
    if kb:
        seed["initial_kb"] = kb
    if seed:
        parts.append("【画像/初始事实(种子，可公开使用)】\n" + json.dumps(seed, ensure_ascii=False))

    if goal_block:
        parts.append(goal_block)
    if h["plans"]:
        parts.append("【已确认/进行中事项(独立于当前目标，勿当新目标自动重启)】\n"
                     + "\n".join(h["plans"]))

    note = (inp.get("note") or "").strip()
    if note:
        parts.append(
            rc.NOTE_BLOCK_HEADER.strip() + "\n「" + note + "」\n"
            "四类分清：①公开事实(位置/日程/共同偏好)可直接带入正文；"
            "②私密情绪/动机只用来调口吻，不原样外发；③拟发草稿未发出前不算既成约定；"
            "④推测/读心不作成对方已确认。缺信息时承认为'不足'，不臆造。")

    # ── C3 层 1：facts 相关投影（窗外事实召回主通道；当前窗与目标作相关性信号）──
    signal_text = " ".join([c["text"] for c in cur]) + " " + (intent_cfg.get("text") or "")
    f_body, f_cu, f_hits, f_filtered, f_ms = _facts_projection(h, signal_text, BUDGET_FACTS_CU)
    if f_body:
        parts.append("【已确认事实/约定(带出处，可回扣；窗口外旧事实亦在此)】\n" + f_body)

    # ── C3 层 2：tentatives 投影 ──
    t_body, t_cu, t_n = _tentatives_projection(h, BUDGET_TENTATIVES_CU)
    if t_body:
        parts.append("【暂定事项(未敲定，别当已约定催办)】\n" + t_body)

    parts.append(ANALYSIS_CONTRACT)

    hist, hist_cu = v5._budget_history(prior_events, v5.MAX_EVENT_HISTORY_CU)
    parts.append("# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + hist)

    cur_lines, alias_map, cur_aliases = [], {}, []
    for i, m in enumerate(cur):
        alias = "m%d" % i
        cur_aliases.append(alias)
        alias_map[alias] = m["uid"]
        speaker = "PARTNER" if m["role"] == "HER" else "USER"
        cur_lines.append(json.dumps({"id": alias, "source_id": m["uid"],
                                     "speaker": speaker, "ts": m["ts"],
                                     "text": m["text"]}, ensure_ascii=False))
    parts.append("# 本轮输入(最高优先，整条不被裁；id=来源ID，PARTNER=对方/USER=本人)\n"
                 "<chat>\n" + "\n".join(cur_lines) + "\n</chat>")

    parts.append(v5._source_rule(cur_aliases))
    images = [m["image"] for m in raw_msgs if m.get("image")]
    _share_kw = ("拍", "照片", "图", "发你", "发过去", "构图", "看看这")
    has_text_share = not images and any(any(k in (c["text"] or "") for k in _share_kw)
                                        for c in cur)
    parts.append(v5._media_line(images, has_text_share))
    if clock:
        parts.append(rc.build_timestamp_prompt(clock).strip())
    parts.append(v5._time_scope_rule())
    parts.append(v5._fact_action_rule())

    prompt = "\n\n".join(parts)
    res = gw.generate(h["system_prompt"], prompt, images=images or None, tag="main")
    reply = res["text"]
    h["turn_count"] = seq

    # 消费模型报告的 facts/tentatives（校验+去重入库）
    try:
        obj = json.loads(reply)
        analysis = obj.get("analysis") if isinstance(obj, dict) else None
    except Exception:                                     # noqa: BLE001
        analysis = None
    added_f, added_t, invalid_src, unsupported_src = _merge_reported(
        h, analysis, cur_uids, clock, alias_map, cur_texts)

    notes = ("evidence_memory_v1[goal_active=%d facts_store=%d tent_store=%d "
             "facts_proj=%d/%dCU filtered=%d tent_proj=%d rel_ms=%s added_f=%d "
             "added_t=%d invalid_src=%d unsupported_src=%d prompt_cu=%d history_cu=%d images=%d "
             "current_never_squeezed=1 reply_not_committed=1]"
             % (1 if rc.is_intent_effective(intent_cfg) else 0,
                len(h["mem"]["facts"]), len(h["mem"]["tentatives"]),
                f_hits, f_cu, f_filtered, t_n, f_ms, added_f, added_t, invalid_src,
                unsupported_src,
                rc.utf16_len(prompt), hist_cu, len(images)))
    return {"reply": reply, "notes": notes,
            "memory_projection": {"facts_hits": f_hits, "facts_cu": f_cu,
                                  "facts_filtered": f_filtered, "tentatives": t_n,
                                  "store_facts": len(h["mem"]["facts"]),
                                  "store_tentatives": len(h["mem"]["tentatives"])}}


def snapshot(h):
    d = json.loads(v5.snapshot(h).decode("utf-8"))
    d["mem"] = h["mem"]
    return json.dumps(d, ensure_ascii=False).encode("utf-8")


def restore(sandbox, snap):
    h = v5.restore(sandbox, snap)
    d = json.loads(snap.decode("utf-8"))
    h["mem"] = d.get("mem") or {"facts": {}, "tentatives": {}, "goal_history": []}
    return h
