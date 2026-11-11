# candidates/fact_ledger_v1/mechanism/mechanism.py
#
# FK-LR2-01 修复候选 A（设计：refs/experiment_protocol_20261023/global_harness_program_20261030/
# W12_FACT_LEDGER_DESIGN_20261101.md §2）。基座 = v0_minimal_events：importlib 加载其模块，
# 直接复用 init/snapshot/restore 骨架与 _uid_for/_budget_history 等纯函数（不复制漂移、
# 不 monkeypatch）；run_turn 的 parts 装配【显式复制】v0 mechanism.py L130–203 的顺序，
# 在画像种子块之后、历史消息段之前插入账本段（父本声明见 IDENTITY.md）。
# 运行时依赖基座候选（staging 闭包触发）：candidates/v0_minimal_events
#
# 本代单变量：M1（账本存储结构）+ M2（检索注入）。system 资产沿用 v0 自带精简资产不改。
# 抽取/注入/更正全部规则化（ledger_rules.py），每轮仍恰好 1 次 gw.generate(tag="main")。
import importlib.util
import json
import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)                       # candidates/fact_ledger_v1
_ROOT = os.path.dirname(_CAND)                       # candidates/
_V0 = os.path.join(_ROOT, "v0_minimal_events")


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


v0 = _load("v0_minimal_events_mech_for_fact_ledger_v1",
           os.path.join(_V0, "mechanism", "mechanism.py"))
lr = _load("fact_ledger_rules_v1", os.path.join(_HERE, "ledger_rules.py"))

rc = v0.rc                          # 同一 UTF-16 计量 / 语义块拼接（不漂移）
build_system_prompt = v0.build_system_prompt
_uid_for = v0._uid_for
_budget_history = v0._budget_history
_intent_cfg_from_input = v0._intent_cfg_from_input

LEDGER_TITLE = "# 【事实账本（逐字原文，非本库内容勿虚构）】"
INJECT_K = 12                       # 设计 §2.5：每轮 top-k 行
LEDGER_BUDGET_CU = 800              # 账本段 UTF-16 码元上限（含标题）
TAU = 16.0                          # 时间衰减轮数
PIN_CAP = 6                         # valid_time 未到期的 promise/date 强制入注上限
LEDGER_CAP = 120                    # 条目容量帽
_W_TYPE = {"promise": 1.0, "date": 0.9, "time": 0.9, "number": 0.9, "amount": 0.9,
           "event": 0.6, "other": 0.4}
_KIND_CN = {"promise": "承诺", "date": "日期", "time": "时点", "number": "数字",
            "amount": "金额", "event": "事件", "other": "其他"}


def init(assets_root, episode_meta, gateway, sandbox):
    h = v0.init(assets_root, episode_meta, gateway, sandbox)
    h["ledger"] = []                # 账本条目（schema 见设计 §2.2）
    h["updates"] = []               # supersede/cancel 日志（F3.2/F3.3 回流审计）
    h["fact_seq"] = 0
    return h


def _capture_turn_msgs(h, cur_msgs, seq):
    """对当前轮真实 HER/ME 消息逐条跑规则抽取入账；草稿永不入账（v0 边界继承）。"""
    new_facts = []
    for m in cur_msgs:
        if m["role"] not in ("HER", "ME"):
            continue
        entries, h["fact_seq"] = lr.extract_facts(
            m["text"], m["role"], m["ts"], seq, m["uid"], h["fact_seq"])
        new_facts.extend(entries)
    return new_facts


def _enforce_capacity(ledger):
    """条目 >120 时淘汰：kind=other 最旧优先；promise/date/time/number/amount 的
    asserted 永不淘汰；若只剩受保护条目则如实超帽（不删保护项）。"""
    while len(ledger) > LEDGER_CAP:
        idx = next((i for i, f in enumerate(ledger) if f["kind"] == "other"), None)
        if idx is None:
            idx = next((i for i, f in enumerate(ledger)
                        if f["status"] in ("superseded", "cancelled")), None)
        if idx is None:
            return
        del ledger[idx]


def _score_fact(f, cur_text, cur_digits, seq):
    rel = lr._bigram_ov(f["text"], cur_text)
    if cur_digits and set(f.get("digits") or []) & cur_digits:
        rel += 0.3                  # 数字加成：E 段探针主攻数字/日期
    w = _W_TYPE.get(f["kind"], 0.4)
    return w + 0.8 * rel + 0.6 * math.exp(-(seq - f["capture_turn"]) / TAU)


def _select_inject(h, seq, clock, cur_text):
    """设计 §2.5：候选池=asserted 且 capture_turn<当前轮（本轮消息已在当前窗口）。
    pin=valid_time 非空且 ≥ 当前日期的 promise/date/time 条目（上限 6）；
    其余按 score 填满 k=12；同义去重（_norm_cn 相同 → 单行）；
    预算 800 CU，超预算按 (pinned, score) 从低往高丢，pinned 最后丢。"""
    cur_digits = lr._digits(cur_text)
    today = (clock or "")[:10]
    pool, seen = [], set()
    for f in h["ledger"]:
        if f["status"] != "asserted" or f["capture_turn"] >= seq:
            continue
        key = lr._norm_cn(f["text"])
        if key in seen:
            continue
        seen.add(key)
        score = _score_fact(f, cur_text, cur_digits, seq)
        pinned = bool(f.get("valid_time") and today
                      and f["valid_time"] >= today
                      and f["kind"] in ("promise", "date", "time"))
        pool.append((pinned, score, f))
    pool.sort(key=lambda x: (x[0], x[1]), reverse=True)
    # 两遍选：先 pin（score 序，≤6），再用 score 填满 k=12（总量含 pin ≤ k）。
    # 贪心加行超预算即丢——pool 已按 (pinned, score) 降序，被丢的是低分非 pin 行，
    # pinned 最后丢（设计 §2.5）。
    pinned_sel = [(s, f) for p, s, f in pool if p][:PIN_CAP]
    nonpin_sel = [(s, f) for p, s, f in pool if not p][:INJECT_K - len(pinned_sel)]
    selected = pinned_sel + nonpin_sel
    lines, used, dropped = [], 0, 0
    for score, f in selected:
        date_part = (f.get("ts") or "")[5:10]
        line = "- [%s|%s|T%02d|%s] %s（%s）" % (
            f["fact_id"], _KIND_CN.get(f["kind"], f["kind"]),
            f["capture_turn"], date_part, f["text"], f["subject"])
        cost = rc.utf16_len(line) + 1
        if used + cost > LEDGER_BUDGET_CU - rc.utf16_len(LEDGER_TITLE) - 1:
            dropped += 1            # 按 (pinned, score) 升序方向丢：pinned 最后丢
            continue
        lines.append(line)
        used += cost
    return "\n".join(lines), used + rc.utf16_len(LEDGER_TITLE) + 1, len(selected) - dropped


def run_turn(h, ctx, gw):
    inp = ctx.get("input") or {}
    clock = (inp.get("clock") or "").strip()
    seq = ctx.get("turn_index") or (h["turn_count"] + 1)
    raw_msgs = [m for m in (inp.get("messages") or []) if isinstance(m, dict)]

    # 当前窗口：稳定 uid + 入 events（复用 v0），随后【在组装 parts 之前】对新增
    # HER/ME 消息逐条跑账本抽取与更正 pass（设计 §2.3：capture 在入 events 后）。
    cur = []
    for m in raw_msgs:
        uid = _uid_for(h, m.get("role"), m.get("text"), m.get("ts"))
        cur.append({"uid": uid, "role": m.get("role") or "",
                    "text": m.get("text") or "", "ts": m.get("ts") or ""})
    cur_uids = [c["uid"] for c in cur]
    prior_events = [e for e in h["events"] if e["uid"] not in set(cur_uids)]

    new_facts = _capture_turn_msgs(h, cur, seq)
    h["ledger"].extend(new_facts)
    n_sup, n_can = lr.update_pass(h["ledger"], new_facts, clock, h["updates"])
    _enforce_capacity(h["ledger"])

    # 相关度输入 = 本轮 HER/ME 文本 + note + intent 拼接（设计 §2.5）
    intent_cfg = _intent_cfg_from_input(inp)
    cur_text = "\n".join([c["text"] for c in cur if c["text"]]
                         + [str(inp.get("note") or ""),
                            str(intent_cfg.get("text") or "")])

    parts = []
    # 1) 画像 + 初始知识（与 v0 逐字同段）
    seed = {}
    if h["persona"]:
        seed["persona"] = h["persona"]
    if h.get("initial_kb"):
        seed["initial_kb"] = h["initial_kb"]
    if seed:
        parts.append("【画像/初始事实(种子，可公开使用)】\n"
                     + json.dumps(seed, ensure_ascii=False))

    # 2) intent（复用父本块，逐字同格式）
    intent_block = rc.build_intent_block(intent_cfg)
    if intent_block.strip():
        parts.append(intent_block.strip())

    # 3) note（军师备注，与 intent 区分）
    note_block = rc.build_advisor_note_block(inp.get("note", ""))
    if note_block.strip():
        parts.append(note_block.strip())

    # 4) 事实账本段（本代新增：种子块之后、历史消息段之前，防 Lost-in-the-Middle）
    ledger_body, ledger_cu, n_inj = _select_inject(h, seq, clock, cur_text)
    if ledger_body:
        parts.append(LEDGER_TITLE + "\n" + ledger_body)

    # 5) 历史真实消息（v0 同段同预算，账本不占 1400 CU）
    hist, hist_cu = _budget_history(prior_events, v0.MAX_EVENT_HISTORY_CU)
    parts.append("# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + hist)

    # 6) 当前窗口（与 v0 逐字同段）
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

    # 7) 当前时间（复用父本块）
    if clock:
        parts.append(rc.build_timestamp_prompt(clock).strip())

    prompt = "\n\n".join(parts)

    images = [m["image"] for m in raw_msgs if m.get("image")]
    # 每轮恰好一次模型调用：抽取/注入/更正全为规则，零额外 generate
    res = gw.generate(h["system_prompt"], prompt, images=images or None, tag="main")
    reply = res["text"]
    h["turn_count"] = seq
    # 模型草稿不入账本（同 v0 不入 events 的边界）
    notes = ("fact_ledger_v1[events=%d ledger=%d prompt_cu=%d history_cu=%d "
             "hist_budget_cu=%d ledger_injected=%d ledger_cu=%d "
             "superseded_this_turn=%d cancelled_this_turn=%d "
             "current_ids=%s canonical_id_map=%s intent_active=%d reply_not_committed=1]"
             % (len(h["events"]), len(h["ledger"]), rc.utf16_len(prompt), hist_cu,
                v0.MAX_EVENT_HISTORY_CU, n_inj, ledger_cu, n_sup, n_can, cur_uids,
                {k: v for k, v in source_alias_map.items()},
                1 if rc.is_intent_effective(intent_cfg) else 0))
    return {"reply": reply, "notes": notes}


def snapshot(h):
    return json.dumps({
        "persona": h["persona"], "initial_kb": h.get("initial_kb", {}),
        "assets_root": h["assets_root"], "events": h["events"],
        "uid_by_key": h["uid_by_key"], "uid_seq": h["uid_seq"],
        "turn_count": h["turn_count"],
        "ledger": h["ledger"], "updates": h["updates"],
        "fact_seq": h["fact_seq"],
    }, ensure_ascii=False).encode("utf-8")


def restore(sandbox, snap):
    d = json.loads(snap.decode("utf-8"))
    h = v0.restore(sandbox, snap)
    h["ledger"] = d.get("ledger") or []
    h["updates"] = d.get("updates") or []
    h["fact_seq"] = d.get("fact_seq", 0)
    return h
