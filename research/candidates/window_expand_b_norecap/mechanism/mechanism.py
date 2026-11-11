# candidates/window_expand_b_norecap/mechanism/mechanism.py
#
# W15 消融候选（W15_NORECAP_ABLATION_DESIGN_20261102.md）：window_expand_b 去掉 delta ②
# （确定性摘取式 recap），保留 delta ①（3600 CU 扩窗）。单变量删除实验。
# 动机（离线证据，LD1 归档四篇实测）：recap 在冠军胜局里近乎死重——LRE147 全程 0 次，
# LRE117/125/141 仅 T34+ 出线且 ≤199 CU（≤400 预算一半不到）；T25-32 判决段 recap 全空，
# 即该段本候选与父本 prompt 逐字节相同。若消融批证实无质量损失，冠军简化（少一段装配、
# 少一份 ledger_rules 依赖）。
# 除 recap 相关三处（_recap_block 函数、run_turn 4b 段、notes 的 recap 字段）外逐字=父本。
#
# ---- 以下为 window_expand_b 原注释 ----
#
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
    hist, hist_cu, _dropped = _budget_history(prior_events, MAX_EVENT_HISTORY_CU)
    parts.append("# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + hist)

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
    notes = ("window_expand_b_norecap[events=%d prompt_cu=%d history_cu=%d "
             "hist_budget_cu=%d "
             "current_ids=%s canonical_id_map=%s "
             "intent_active=%d reply_not_committed=1]"
             % (len(h["events"]), rc.utf16_len(prompt), hist_cu,
                MAX_EVENT_HISTORY_CU,
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
