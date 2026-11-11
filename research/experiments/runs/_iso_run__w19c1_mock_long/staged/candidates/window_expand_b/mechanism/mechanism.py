# candidates/window_expand_b/mechanism/mechanism.py
#
# W12 对照臂 B（扩窗+摘要混合，设计 W12_FACT_LEDGER_DESIGN_20261101.md §3）。
# 全文复制父本 v0_minimal_events/mechanism/mechanism.py（身份闭包内共享依赖不变），
# 仅两处 delta：
#   ① MAX_EVENT_HISTORY_CU 1400 → 3600（≈覆盖 32–40 轮全部源消息）；
#   ② 确定性摘取式 recap：历史预算裁掉的消息，每轮保留首个含 R1–R4 信号的 clause
#      成一行（零模型调用、逐字原文），拼在历史段尾，recap 段 ≤400 CU。
# 角色：检验"容量本身够不够"——若 B 与 fact_ledger_v1 同分，账本排序/相关度无独立贡献。
#
# ---- 以下为 v0_minimal_events 原注释 ----
#
# 最小 harness 对照（§C / 复核 §4 收口）：检验"原版复杂度是否值得"。
# 与 candidates/reference_product_v1 / candidates/v3_system_slim 分开：
#   - 记忆=每故事一份简单、有序的【真实消息事件】记录，只存实际发送的 HER/ME；
#   - 所有模型生成回复都是草稿，绝不自动写回成真实 ME 历史；
#   - 不做多层 topic/ongoing/scene 摘要、不自动画像重估/反思、不新增辅助生成。
#
# 公平性收口（复核 §4：允许换记忆实现，【不允许】丢掉同批用户给的目标/知识/时间）：
#   1) 当前有效 intent 的文本/状态/启停语义进入请求（复用父本 build_intent_block，
#      逐字同格式）；关闭/替换后只取当轮 → 不留旧目标；note 继续与 intent 区分
#      （复用父本 build_advisor_note_block）。
#   2) 传入同一允许的 initial_kb（平面种子事实）+ persona + 当前 clock
#      （复用父本 build_timestamp_prompt）；不注入 gold 或作者卡。
#   3) 当前窗口每条消息给【稳定、逐条】的 source alias（m0,m1,…）并与 canonical_id_map
#      对齐；历史来源与当前来源分开，不用轮序号冒充消息 ID（轮序号无法区分同轮各条）。
#   4) 整段历史预算即使第一条（最近一条旧史）单独超预算也严格 ≤ 上限：此时省略该旧史
#      （不切代理对、不发明压缩系统），当前输入仍在独立段落完整保留、不被挤掉。
#
# system 复用经 B 修订的精简资产（本候选自带同一份），使
#   "精简 system + 原记忆"(v3)  vs  "精简 system + 简单事件记忆"(v0)  成为可解释对照；
#   对外信息（意图/知识/时间/来源）已对齐后，才谈内部组织差异。
# 共享依赖 rc / build_system_prompt 来自 reference_product_v1（进入身份闭包，不复制漂移）。
import importlib.util
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)                       # candidates/window_expand_b
_ROOT = os.path.dirname(_CAND)                       # candidates/
_BASE = os.path.join(_ROOT, "reference_product_v1")
sys.path.insert(0, _BASE)


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _load_base():
    spec = importlib.util.spec_from_file_location(
        "ref_product_v1_mechanism_wb",
        os.path.join(_BASE, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


base = _load_base()
rc = base.rc                     # 共享 UTF-16 计量 / 各语义块拼接（同一实现，不漂移）
build_system_prompt = base.build_system_prompt
lr = _load("fact_ledger_rules_wb", os.path.join(_HERE, "ledger_rules.py"))

# delta ①：历史真实消息段的 UTF-16 码元预算 1400 → 3600（设计 §3：≈覆盖 32–40 轮源消息）
MAX_EVENT_HISTORY_CU = 3600
RECAP_TITLE = "# 【更早轮次摘录】"
RECAP_BUDGET_CU = 400            # delta ②：recap 段上限
RECAP_RULES = ("R1", "R2", "R3", "R4")   # 摘取信号=ledger_rules 的 R1–R4（设计 §3）


def _u16_trunc_ok(s, n):
    """按 UTF-16 码元计长的整条保留判断（不切代理对，只用于估算）。"""
    return rc.utf16_len(s) <= n


def _intent_cfg_from_input(inp):
    it = inp.get("intent")
    if it is None:
        return {}
    if isinstance(it, str):
        return {"enabled": True, "text": it, "status": "ACTIVE"} if it.strip() else {}
    if isinstance(it, dict):
        cfg = dict(it)
        cfg.setdefault("status", "ACTIVE")
        return cfg
    return {}


def init(assets_root, episode_meta, gateway, sandbox):
    os.makedirs(sandbox, exist_ok=True)
    meta = episode_meta or {}
    return {
        "assets_root": assets_root,
        "sandbox": sandbox,
        "persona": meta.get("persona") or {},
        "initial_kb": meta.get("initial_kb") or {},       # 种子事实（平面标量），公平传入
        "system_prompt": base.build_system_prompt(assets_root),
        "events": [],            # 有序真实消息 [{uid, role, text, ts}]
        "uid_by_key": {},        # 去重+稳定 ID：key(role|text|ts) -> uid
        "uid_seq": 0,            # 下一条消息的整型 uid
        "turn_count": 0,
    }


def _key_of(role, text, ts):
    return "%s|%s|%s" % (role, text, ts)


def _uid_for(h, role, text, ts):
    """给一条真实消息分配【稳定】uid（同内容+时间戳复用同一 id，跨轮不重复）。"""
    key = _key_of(role, text, ts)
    uid = h["uid_by_key"].get(key)
    if uid is None:
        uid = h["uid_seq"]
        h["uid_seq"] += 1
        h["uid_by_key"][key] = uid
        h["events"].append({"uid": uid, "role": role or "", "text": text or "",
                            "ts": ts or ""})
    return uid


def _line(m):
    return "m_src[%d] | %s | %s | %s" % (m["uid"], m.get("ts", ""),
                                         "PARTNER" if m["role"] == "HER" else "USER",
                                         m["text"])


def _budget_history(events, budget):
    """整条消息为单位，从最新往旧累加；越界即停。
    连最近一条都放不下时，省略整段旧史（不切代理对、不压缩），
    保证 history 段严格 ≤ budget；当前输入另有独立整段、永远完整保留。
    delta ②：额外返回被裁掉的消息列表（供确定性摘取式 recap 用）。"""
    lines, used, omitted = [], 0, 0
    kept_uids = set()
    for m in reversed(events):
        line = _line(m)
        add = rc.utf16_len(line) + 1
        if used + add > budget:
            if not lines:
                omitted = 1          # 单条旧史即超上限：省略之（当前输入不受影响）
            break
        lines.append(line)
        kept_uids.add(m["uid"])
        used += add
    body = "\n".join(reversed(lines))
    if omitted and not body:
        body = "（最近一条旧史单独超出历史预算，本段省略；当前输入在下方完整保留）"
    dropped = [m for m in events if m["uid"] not in kept_uids]
    return body, used, dropped


def _recap_block(dropped):
    """delta ②：被裁消息各保留首个含 R1–R4 信号的 clause（逐字原文，零模型调用），
    从距窗口最近者往旧填，recap 段（含标题）≤ RECAP_BUDGET_CU。"""
    lines, used = [], rc.utf16_len(RECAP_TITLE) + 1
    for m in reversed(dropped):            # events 旧→新；倒转后从距窗口最近者往旧填
        for c in lr.split_clauses(m.get("text") or ""):
            kind, rule = lr.classify_clause(c)
            if kind is None or rule not in RECAP_RULES:
                continue
            line = "m_src[%d] | %s" % (m["uid"], c)
            add = rc.utf16_len(line) + 1
            if used + add > RECAP_BUDGET_CU:
                return "\n".join([RECAP_TITLE] + lines), used
            lines.append(line)
            used += add
            break                    # 每条被裁消息只取首个命中信号 clause
    if not lines:
        return "", 0
    return "\n".join([RECAP_TITLE] + lines), used


def run_turn(h, ctx, gw):
    inp = ctx.get("input") or {}
    clock = (inp.get("clock") or "").strip()
    seq = ctx.get("turn_index") or (h["turn_count"] + 1)
    raw_msgs = [m for m in (inp.get("messages") or []) if isinstance(m, dict)]

    # 当前窗口：每条分配稳定 uid，并把本轮真实消息入库（下一轮才成为旧史）
    cur = []
    for m in raw_msgs:
        uid = _uid_for(h, m.get("role"), m.get("text"), m.get("ts"))
        cur.append({"uid": uid, "role": m.get("role") or "",
                    "text": m.get("text") or "", "ts": m.get("ts") or ""})
    cur_uids = [c["uid"] for c in cur]
    # 历史 = 除本轮外的既有事件（当前消息已在独立段完整呈现，不在历史里重复/挤占）
    prior_events = [e for e in h["events"] if e["uid"] not in set(cur_uids)]

    parts = []
    # 1) 画像 + 初始知识（同一允许的 seed，两臂都可用的可公开事实）
    seed = {}
    if h["persona"]:
        seed["persona"] = h["persona"]
    if h.get("initial_kb"):
        seed["initial_kb"] = h["initial_kb"]
    if seed:
        parts.append("【画像/初始事实(种子，可公开使用)】\n"
                     + json.dumps(seed, ensure_ascii=False))

    # 2) 当前有效 intent（复用父本块，逐字同格式；关闭/缺省 → 空，不留旧目标）
    intent_cfg = _intent_cfg_from_input(inp)
    intent_block = rc.build_intent_block(intent_cfg)
    if intent_block.strip():
        parts.append(intent_block.strip())

    # 3) note（军师备注，与 intent 区分：只约束这一轮怎么想/怎么回，不是已说过的话）
    note_block = rc.build_advisor_note_block(inp.get("note", ""))
    if note_block.strip():
        parts.append(note_block.strip())

    # 4) 历史真实消息（整段预算裁剪；含每条来源 uid 与 ts，来源可区分）
    hist, hist_cu, dropped = _budget_history(prior_events, MAX_EVENT_HISTORY_CU)
    parts.append("# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + hist)

    # 4b) delta ②：更早轮次摘录（被裁消息的首个 R1–R4 信号 clause，逐字原文）
    recap, recap_cu = _recap_block(dropped)
    if recap:
        parts.append(recap)

    # 5) 当前窗口（最高优先，整条不被裁）：逐条 m-alias + speaker + ts + 文本，
    #    source_alias_map 与 canonical_id_map 对齐（alias m<i> -> 稳定来源 id）
    cur_lines, source_alias_map = [], {}
    for i, m in enumerate(cur):
        alias = "m%d" % i
        source_alias_map[alias] = m["uid"]
        speaker = "PARTNER" if m["role"] == "HER" else "USER"
        cur_lines.append(json.dumps({"id": alias, "source_id": m["uid"],
                                     "speaker": speaker, "ts": m["ts"],
                                     "text": m["text"]}, ensure_ascii=False))
    parts.append("# 本轮输入(最高优先，整条不被裁；id=来源ID，PARTNER=对方/USER=本人)\n"
                 "<chat>\n" + "\n".join(cur_lines) + "\n</chat>")

    # 6) 当前时间（复用父本块，逐字同格式；两臂都拿到同一 clock）
    if clock:
        parts.append(rc.build_timestamp_prompt(clock).strip())

    prompt = "\n\n".join(parts)

    images = [m["image"] for m in raw_msgs if m.get("image")]
    res = gw.generate(h["system_prompt"], prompt, images=images or None, tag="main")
    reply = res["text"]
    h["turn_count"] = seq
    # 关键边界：模型回复【不】写入 h["events"]——它是草稿，不是已发生的 ME 消息。
    notes = ("window_expand_b[events=%d prompt_cu=%d history_cu=%d "
             "hist_budget_cu=%d recap_cu=%d recap_lines=%d "
             "current_ids=%s canonical_id_map=%s "
             "intent_active=%d reply_not_committed=1]"
             % (len(h["events"]), rc.utf16_len(prompt), hist_cu,
                MAX_EVENT_HISTORY_CU, recap_cu,
                recap.count("\n") if recap else 0,
                cur_uids,
                {k: v for k, v in source_alias_map.items()},
                1 if rc.is_intent_effective(intent_cfg) else 0))
    return {"reply": reply, "notes": notes}


def snapshot(h):
    return json.dumps({
        "persona": h["persona"], "initial_kb": h.get("initial_kb", {}),
        "assets_root": h["assets_root"], "events": h["events"],
        "uid_by_key": h["uid_by_key"], "uid_seq": h["uid_seq"],
        "turn_count": h["turn_count"],
    }, ensure_ascii=False).encode("utf-8")


def restore(sandbox, snap):
    d = json.loads(snap.decode("utf-8"))
    os.makedirs(sandbox, exist_ok=True)
    ar = d.get("assets_root")
    return {
        "assets_root": ar, "sandbox": sandbox,
        "persona": d.get("persona") or {},
        "initial_kb": d.get("initial_kb") or {},
        "system_prompt": base.build_system_prompt(ar) if ar else "",
        "events": d.get("events") or [], "uid_by_key": d.get("uid_by_key") or {},
        "uid_seq": d.get("uid_seq", 0), "turn_count": d.get("turn_count", 0),
    }
