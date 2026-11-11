# candidates/phase0_beta — Harness 候选 β（Phase 0 验收 V1 之二）
# 架构：文件型 KV 存储（sandbox 内 jsonl/json 文件）+ 关键词检索式记忆注入 + 两阶段网关编排
#（记忆管理调用 + 主回复调用， demonstrate 多阶段推理 = 审计能力 3）。
# 记忆策略：检索式——每轮只把与当前窗口 LCS≥2 的历史事实注入 scene_chain（相关则记，无关则隐），
# 预期通过 stale 抑制探针；召回探针依赖检索命中。
# 存储形态：磁盘文件；快照 = 文件字节拼接，restore 在 sandbox 重建文件。

import json
import os
import sys

_ARENA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "arena")
sys.path.insert(0, _ARENA)
import lb_assembly          # noqa: E402
import protocol_public            # noqa: E402

FACTS_FILE = "kv_facts.jsonl"
ONGOING_FILE = "kv_ongoing.json"
RETRIEVE_TOPK = 6
LCS_MIN = 2


# ---------- 文件型 KV 存储 ----------

def _facts_path(h):
    return os.path.join(h["sandbox"], FACTS_FILE)


def _ongoing_path(h):
    return os.path.join(h["sandbox"], ONGOING_FILE)


def _load_facts(h):
    p = _facts_path(h)
    if not os.path.exists(p):
        return []
    out = []
    with open(p, "r", encoding="utf-8") as f:
        for ln in f:
            ln = ln.strip()
            if not ln:
                continue
            try:
                out.append(json.loads(ln))
            except json.JSONDecodeError:
                continue   # 崩溃残留的半行：跳过不致命（Phase 0.5 杀进程实测教训）
    return out


def _append_fact(h, fact):
    with open(_facts_path(h), "a", encoding="utf-8") as f:
        f.write(json.dumps(fact, ensure_ascii=False) + "\n")


def _load_ongoing(h):
    p = _ongoing_path(h)
    if not os.path.exists(p):
        return []
    try:
        with open(p, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, ValueError):
        return []   # 强杀部分写入的残缺文件：按空恢复（原子写已把窗口缩到最小）


def _save_ongoing(h, ongoing):
    # 原子写：先落 tmp 再 os.replace，杜绝"写一半被杀"的永久残缺
    tmp = _ongoing_path(h) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(ongoing, f, ensure_ascii=False)
    os.replace(tmp, _ongoing_path(h))


# ---------- 检索（确定性：最长公共子串 ≥ LCS_MIN 视为相关） ----------

def _lcs_len(a, b):
    if not a or not b:
        return 0
    prev = [0] * (len(b) + 1)
    best = 0
    for i in range(1, len(a) + 1):
        cur = [0] * (len(b) + 1)
        ai = a[i - 1]
        for j in range(1, len(b) + 1):
            if ai == b[j - 1]:
                cur[j] = prev[j - 1] + 1
                if cur[j] > best:
                    best = cur[j]
        prev = cur
    return best


def _retrieve(window_text, facts, topk=RETRIEVE_TOPK):
    scored = []
    for f in facts:
        s = _lcs_len(window_text, f["text"])
        if s >= LCS_MIN:
            scored.append((s, f))
    scored.sort(key=lambda x: (-x[0], x[1].get("clock", "")))
    return [f for _, f in scored[:topk]]


# ---------- v2 生命周期 ----------

def init(assets_root, episode_meta, gateway, sandbox):
    # 2026-10-18 判决令 §2.2：消费白名单下发的 persona / initial_kb（此前直接忽略，
    # 人物背景写在题面却从未进入本机制的模型请求）。
    return {"assets_root": assets_root, "sandbox": sandbox,
            "turn": 0, "topic": "", "topic_age": 99,
            "persona": dict(episode_meta.get("persona") or {}),
            "initial_kb": dict(episode_meta.get("initial_kb") or {}),
            # 第五波判决 §11-4：eval 侧历史信息访问开关（缺省=全开）
            "memory_access": bool(episode_meta.get("_eval_memory_access", True)),
            "last_clock": None, "topic_started_at": None}


def set_memory_access(h, enabled: bool):
    """L3B 冻结分叉：eval 侧关闭/恢复全部历史信息访问渠道（facts/ongoing/topic 摘要/
    检索缓存/last_clock/initial_kb）。只关读取，不销毁存储（状态文件保留可审计）。"""
    h["memory_access"] = bool(enabled)


def _hours_between(ts_a, ts_b):
    import datetime as _dt
    try:
        a = _dt.datetime.strptime(ts_a, "%Y-%m-%d %H:%M")
        b = _dt.datetime.strptime(ts_b, "%Y-%m-%d %H:%M")
        return round((b - a).total_seconds() / 3600.0, 2)
    except Exception:
        return None


def run_turn(h, ctx, gw):
    h["turn"] += 1
    ai = ctx["input"]
    window = "".join(m.get("text", "") for m in ai.get("messages", [])
                     if isinstance(m, dict))
    mem_on = bool(h.get("memory_access", True))
    # 第五波判决 §11-4：memory_access=False = 关闭全部历史信息访问渠道——
    # kv_facts / kv_ongoing / topic 摘要 / 检索缓存 / last_clock / initial_kb 一律不进输入；
    # 共同画像（persona）与当前轮输入保留。存储文件不销毁（差异清单可审计）。
    facts = _load_facts(h) if mem_on else []
    ongoing = _load_ongoing(h) if mem_on else []
    retrieved = _retrieve(window, facts) if mem_on else []

    # 真实时间差（判决令 §2.3：替代固定 +3 的轮数近似）
    # 关记忆分支不读 last_clock（跨轮时钟状态本身是历史信息渠道）
    clock = ai.get("clock", "")
    if mem_on and clock and h.get("last_clock"):
        dh = _hours_between(h["last_clock"], clock)
        if dh is not None:
            h["hours_since_last_exchange"] = max(0.0, dh)
    if clock:
        h["last_clock"] = clock

    # 预算对照版：无记忆管理调用（单阶段）。检索照做，注入照做——
    # 与 β 唯一差异 = 少一次模型调用，用于区分"检索收益"与"额外推理收益"（Phase 0.5）。
    kb = {"scene_chain": "\n".join("- [%s] %s" % (f["clock"], f["text"])
                                   for f in retrieved),
          "ongoing_plan": "\n".join("%s | %s" % (o["item"], o["status"])
                                    for o in ongoing),
          "topic": h.get("topic", "") if mem_on else ""}
    if mem_on and h.get("topic_started_at") and clock:
        dh2 = _hours_between(h["topic_started_at"], clock)
        kb["topic_age_hours"] = max(0.0, dh2) if dh2 is not None else 0
    elif mem_on and h.get("topic"):
        kb["topic_age_hours"] = 0
    if mem_on and h.get("hours_since_last_exchange") is not None:
        kb["hours_since_last_exchange"] = h["hours_since_last_exchange"]
    # 初始 KB（第五波判决 §8：预置资源实际消费——此前只缓存进 kb["initial_facts"]
    # 未被装配读取，属"写入新字段、读侧没接"。现按 initial_kb 的 kb 形态字段
    # （me/her/warmth/style/stage/lessons/scene_chain/ongoing_plan…）作为首轮默认知识；
    # 机制运行期状态（topic/检索结果）照常覆盖。关记忆分支不消费（initial_kb=历史渠道）。
    if mem_on and h.get("initial_kb"):
        ik = h["initial_kb"]
        for k in ("me", "her", "warmth", "style", "stage", "topic",
                  "topic_age_hours", "lessons", "recent", "scene_chain",
                  "ongoing_plan"):
            if ik.get(k) and not kb.get(k):
                kb[k] = ik[k]
    ai2 = dict(ai)
    ai2["kb"] = kb
    if h.get("persona"):
        ai2.setdefault("persona", h["persona"])
    assets_root = ctx.get("assets_root") or h["assets_root"]   # resume 后以 ctx 为准
    assembled = lb_assembly.build_reply_prompts(assets_root, ai2)
    images = [m["image"] for m in ai.get("messages", [])
              if isinstance(m, dict) and m.get("image")]
    res = gw.generate(assembled["system"], assembled["user"],
                      images=images or None, tag="main")

    # 写回：本轮 analysis 的事实/计划入文件存储
    # （关记忆分支保留写入——只关"访问读取"，写入不影响本分支输入，状态可审计）
    # 第七波判决 §3：写回与评分共用同一份合格结构——analysis 先过
    # protocol_public.analysis_type_errors，坏嵌套不消费、不写入、留证 notes（不崩轮）。
    parsed = protocol_public.extract_json_block(res["text"])
    wb_skip = None
    if parsed and isinstance(parsed.get("analysis"), dict):
        ana = parsed["analysis"]
        errs = protocol_public.analysis_type_errors(ana)
        if errs:
            wb_skip = "nested_type_violation:" + ",".join(errs[:6])
        else:
            for f in ana.get("scene_facts") or []:
                t = (f.get("text") or "").strip() if isinstance(f, dict) else ""
                if t:
                    _append_fact(h, {"clock": clock, "text": t})
            changed = False
            for o in ana.get("ongoing") or []:
                item = (o.get("item") or "").strip() if isinstance(o, dict) else ""
                if item:
                    ongoing = [x for x in ongoing if x["item"] != item]
                    ongoing.append({"item": item, "status": o.get("status", "")})
                    changed = True
            if changed:
                _save_ongoing(h, ongoing)
            label = (ana.get("topic_label") or "").strip()
            if label and label != h.get("topic"):
                h["topic"] = label
                h["topic_started_at"] = clock

    notes = "beta: file-KV storage, LCS retrieval, single-stage"
    if wb_skip:
        # 第七波判决 §3-3：格式失败留证（mechanism_notes.txt 可审计），不提交非法记忆
        notes += " | writeback_skipped(%s)" % wb_skip
    if not mem_on:
        notes += (" | memory_access=False: kv_facts/kv_ongoing/topic/检索/"
                  "last_clock/initial_kb 已关闭（共同画像 persona 保留）")
    return {"reply": res["text"], "notes": notes,
            "id_map": assembled["id_map"]}


def snapshot(h):
    parts = []
    for fn in (FACTS_FILE, ONGOING_FILE):
        p = os.path.join(h["sandbox"], fn)
        data = open(p, "rb").read() if os.path.exists(p) else b""
        parts.append(fn.encode("utf-8") + b"\n" + data + b"\n--SNAP--\n")
    meta = json.dumps({"turn": h["turn"], "topic": h["topic"],
                       "last_clock": h.get("last_clock"),
                       "topic_started_at": h.get("topic_started_at"),
                       "hours_since_last_exchange": h.get("hours_since_last_exchange"),
                       "persona": h.get("persona") or {},
                       "initial_kb": h.get("initial_kb") or {},
                       "memory_access": bool(h.get("memory_access", True))},
                      ensure_ascii=False).encode("utf-8")
    return b"".join(parts) + b"META\n" + meta


def restore(sandbox, snap):
    h = {"assets_root": None, "sandbox": sandbox, "turn": 0,
         "topic": "", "topic_age": 99}
    chunks = snap.split(b"META\n")
    body, meta = chunks[0], chunks[1]
    for block in body.split(b"\n--SNAP--\n"):
        if not block.strip():
            continue
        nl = block.index(b"\n")
        fn = block[:nl].decode("utf-8")
        data = block[nl + 1:]
        with open(os.path.join(sandbox, fn), "wb") as f:
            f.write(data)
    m = json.loads(meta.decode("utf-8"))
    h.update(m)
    return h
