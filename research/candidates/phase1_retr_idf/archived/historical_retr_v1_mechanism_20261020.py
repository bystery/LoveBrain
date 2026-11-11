# candidates/phase1_retr_idf — 检索改进候选（父版本 = phase0_beta_single）
# 唯一主变量 = 检索策略：原始字符 LCS≥2 → 内容字 IDF 加权余弦（阈值 0.10）。
# 其余（存储形态、写回策略、单阶段编排、assets、params 契约、memory-off 语义）与父版本
# 逐字节一致——assets/params sha256 相同，机制代码除检索与追踪外逐行相同。
#
# 改进依据（第八波复核 §2/§8-2，2026-10-20）：
#   旧策略缺陷（冻结点 11 条 facts 实测）：
#   - 相关漏检：2 条明确过敏事实与 t5 窗口 LCS=1（唯一桥梁是单字"猫"），阈值 2 永远取不回；
#   - 无关误取：t6 食堂窗口凭与旧过敏事实的公共子串"今天"（长度 2）取回 4/4。
#   根因：原始 LCS 长度阈值对通用词（今天/室友）过低、对单字关键实体（猫）过高。
#   新策略（通用 IR 做法，领域无关——不含任何针对猫/过敏/原题的词表）：
#   1) 通用语用剥离：时间指示词整词（今天/明天/昨天/…）与数字/标点不参与匹配；
#   2) 通用停用字过滤（的了着呢吧…功能/话语标记，~60 字，与话题无关）；
#   3) 内容字集合 IDF 加权余弦（IDF 在本轮 facts 库上现算，库外字取最大权重）；
#   4) score >= RETR_MIN(0.15) 入选，按分数降序取 topk，平分按 clock 稳定排序。
#   阈值 0.15 在冻结案例（t5/t6）与锁定离线回归集（tools/test_retr_idf.py）上校准：
#   t5 期望取回最低分 0.2031 与 t6 噪声最高分 0.1069 之间，两侧均有余量；
#   校准先于任何真实调用，登记于 refs/L3B_RETR_CONTRAST_PROTOCOL_20261020.md。
#
# 来源追踪（第八波复核 §8-2/§3，评估专用，不注入候选请求、不含 gold）：
#   每轮追加 sandbox/retrieval_trace.jsonl 一行：
#   facts_stored（已存储）/ facts_scored（被检索：score、selected、filtered 原因）/
#   injected（进入请求：scene_chain 事实 id、ongoing 全量、topic）。
#   "被回复采用"由离线检查器（tools/retr_report.py，预登记 bigram 规则）对 reply 复算，
#   机制自身不感知。四态区分：已存储 → 被检索 → 进入请求 → 被回复采用。

import json
import math
import os
import sys

_ARENA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "arena")
sys.path.insert(0, _ARENA)
import lb_assembly          # noqa: E402
import lb_scorer            # noqa: E402

FACTS_FILE = "kv_facts.jsonl"
ONGOING_FILE = "kv_ongoing.json"
RETRIEVE_TOPK = 6
RETR_MIN = 0.15             # 内容字 IDF 余弦阈值（冻结案例+锁定回归集校准，先于真实调用锁定；
                            # 间隔依据：t5 期望取回最低分 0.2031 与 t6 噪声最高分 0.1069 之间）

# 通用停用字：功能/话语标记/代词/程度副词等（领域无关；不含任何话题实体字）
_STOP_CHARS = set(
    "的了着过呢吧啊呀哦嘛嗯唉嗨呀哇嘿哈哦么吗之乎者"
    "是我你您他她它咱们这个那哪个些"
    "也都很就被和跟与或但而且还再就想去要说把给让从向往在有没别不又只挺更太最"
    "点事儿请会能可以下上里外中时后先自各每某另一关于由于所以因此可是不过"
)
# 通用时间指示词（整词剥离；相对时间在历史事实中是陈旧指称，不构成内容相关性）
_TIME_WORDS = ("今天", "明天", "昨天", "后天", "今晚", "昨晚", "今早", "明早",
               "上午", "下午", "傍晚", "中午", "刚才", "现在", "目前", "最近",
               "周末", "改天", "下次", "上次", "这周", "下周", "上周", "这个月")


def _content_chars(text):
    """内容字序列：剥时间指示词 → 只留 CJK → 去通用停用字。确定性、无外部依赖。"""
    for w in _TIME_WORDS:
        text = text.replace(w, " ")
    return [ch for ch in text
            if "\u4e00" <= ch <= "\u9fff" and ch not in _STOP_CHARS]


def _idf_table(fact_texts):
    """内容字 IDF：ln(1 + N/df)，在当前 facts 库上现算（库小、确定性、可复算）。"""
    n = max(1, len(fact_texts))
    df = {}
    for t in fact_texts:
        for c in set(_content_chars(t)):
            df[c] = df.get(c, 0) + 1
    return {c: math.log(1 + n / d) for c, d in df.items()}, n


def _cosine(w_chars, f_chars, idf, n):
    inter = set(w_chars) & set(f_chars)
    if not inter:
        return 0.0, ()
    num = sum(idf.get(c, math.log(1 + n)) for c in inter)
    den = math.sqrt(sum(idf.get(c, math.log(1 + n)) for c in set(w_chars))
                    * sum(idf.get(c, math.log(1 + n)) for c in set(f_chars)))
    return (num / den if den else 0.0), tuple(sorted(inter))


# ---------- 文件型 KV 存储（与父版本逐行一致） ----------

def _facts_path(h):
    return os.path.join(h["sandbox"], FACTS_FILE)


def _ongoing_path(h):
    return os.path.join(h["sandbox"], ONGOING_FILE)


def _trace_path(h):
    return os.path.join(h["sandbox"], "retrieval_trace.jsonl")


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


# ---------- 检索 v2（本候选唯一主变量）+ 来源追踪 ----------

def _retrieve(window_text, facts, trace=None, case_id=""):
    """内容字 IDF 余弦检索。返回入选 [(fact_id, fact)]；trace 非 None 时写来源追踪。"""
    texts = [f.get("text", "") for f in facts]
    idf, n = _idf_table(texts)
    w_chars = _content_chars(window_text)
    scored = []
    for i, f in enumerate(facts):
        s, inter = _cosine(w_chars, _content_chars(texts[i]), idf, n)
        scored.append((s, i, f, inter))
    scored.sort(key=lambda x: (-x[0], x[2].get("clock", ""), x[1]))
    selected_pairs = [(i, f) for s, i, f, _ in scored if s >= RETR_MIN][:RETRIEVE_TOPK]
    if trace is not None:
        sel_ids = {i for i, _ in selected_pairs}
        topk_cut = {i for i, _ in selected_pairs}   # 入选者
        trace["retrieval"] = {
            "strategy": "content-char-idf-cosine v1",
            "threshold": RETR_MIN, "topk": RETRIEVE_TOPK,
            "window_content_chars": "".join(w_chars)[:80],
            "facts_scored": [
                {"fact_id": i, "clock": f.get("clock", ""),
                 "text": f.get("text", ""), "score": round(s, 4),
                 "shared_content_chars": "".join(inter),
                 "selected": i in sel_ids,
                 "filtered_reason": (None if i in topk_cut
                                     else ("score<%s" % RETR_MIN) if s < RETR_MIN
                                     else "rank>topk")}
                for s, i, f, inter in scored]}
    return selected_pairs


def _write_trace(h, trace):
    """评估专用来源追踪（不含 gold/目标事实清单；不注入模型请求）。"""
    try:
        with open(_trace_path(h), "a", encoding="utf-8") as f:
            f.write(json.dumps(trace, ensure_ascii=False, default=str) + "\n")
    except OSError:
        pass   # 追踪写失败不阻断对话（与网关事件日志不同：追踪非账本，仅为评估辅助）


# ---------- v2 生命周期（与父版本一致；仅 notes 与追踪差异） ----------

def init(assets_root, episode_meta, gateway, sandbox):
    # 2026-10-18 判决令 §2.2：消费白名单下发的 persona / initial_kb。
    return {"assets_root": assets_root, "sandbox": sandbox,
            "turn": 0, "topic": "", "topic_age": 99,
            "persona": dict(episode_meta.get("persona") or {}),
            "initial_kb": dict(episode_meta.get("initial_kb") or {}),
            # 第五波判决 §11-4：eval 侧历史信息访问开关（缺省=全开）
            "memory_access": bool(episode_meta.get("_eval_memory_access", True)),
            "last_clock": None, "topic_started_at": None}


def set_memory_access(h, enabled: bool):
    """L3B 冻结分叉：eval 侧关闭/恢复全部历史信息访问渠道。只关读取，不销毁存储。"""
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
    clock = ai.get("clock", "")
    trace = {"turn": h["turn"], "case_id": ctx.get("case_id") or ai.get("case_id", ""),
             "clock": clock, "memory_access": mem_on,
             "strategy_note": "唯一主变量=检索策略；ongoing 全量注入与写回与父版本一致"}
    facts = _load_facts(h) if mem_on else []
    ongoing = _load_ongoing(h) if mem_on else []
    trace["facts_stored"] = [{"fact_id": i, "clock": f.get("clock", ""),
                              "text": f.get("text", "")}
                             for i, f in enumerate(facts)]
    trace["ongoing_stored"] = ongoing
    retrieved_pairs = _retrieve(window, facts, trace=trace) if mem_on else []
    trace["injected"] = {
        "scene_chain_fact_ids": [i for i, _ in retrieved_pairs],
        "ongoing_all": [o.get("item", "") for o in ongoing],
        "topic": h.get("topic", "") if mem_on else "",
        "ongoing_injection_mode": "full（与父版本一致，不经过检索）"}

    # 真实时间差（判决令 §2.3）
    if mem_on and clock and h.get("last_clock"):
        dh = _hours_between(h["last_clock"], clock)
        if dh is not None:
            h["hours_since_last_exchange"] = max(0.0, dh)
    if clock:
        h["last_clock"] = clock

    kb = {"scene_chain": "\n".join("- [%s] %s" % (f["clock"], f["text"])
                                   for _, f in retrieved_pairs),
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

    # 写回（与父版本逐行一致：analysis 先过 analysis_type_errors，坏结构不消费不中止）
    parsed = lb_scorer.extract_json_block(res["text"])
    wb_skip = None
    if parsed and isinstance(parsed.get("analysis"), dict):
        ana = parsed["analysis"]
        errs = lb_scorer.analysis_type_errors(ana)
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

    trace["writeback"] = {"facts_appended": None if wb_skip else
                          [f.get("text", "") for f in
                           (parsed.get("analysis", {}).get("scene_facts") or [])
                           if isinstance(f, dict) and (f.get("text") or "").strip()]
                          if parsed else None,
                          "skipped": wb_skip}
    _write_trace(h, trace)

    notes = "beta+retr-idf: file-KV storage, content-IDF-cosine retrieval(thr=%.2f), single-stage; trace=retrieval_trace.jsonl" % RETR_MIN
    if wb_skip:
        notes += " | writeback_skipped(%s)" % wb_skip
    if not mem_on:
        notes += (" | memory_access=False: kv_facts/kv_ongoing/topic/检索/"
                  "last_clock/initial_kb 已关闭（共同画像 persona 保留）")
    return {"reply": res["text"], "notes": notes,
            "id_map": assembled["id_map"]}


def snapshot(h):
    # 与父版本一致（retrieval_trace.jsonl 不入快照——追踪是分支本地评估产物，
    # 不改变快照字节；branch_done 的 sandbox_tree 散列仍覆盖它，可审计）
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
