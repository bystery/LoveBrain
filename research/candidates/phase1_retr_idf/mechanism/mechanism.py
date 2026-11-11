# candidates/phase1_retr_idf — 检索改进候选（父版本 = phase0_beta_single）
# 唯一主变量 = 检索策略：原始字符 LCS≥2 → 混合 IDF 余弦 + 窗口激活门（v2.1）。
# 其余（存储形态、写回策略、单阶段编排、assets、params 契约、memory-off 语义）与父版本
# 逐字节一致——assets/params sha256 相同，机制代码除检索与追踪外逐行相同。
#
# 策略版本史（通用 IR 做法，领域无关——不含任何针对猫/过敏/原题的词表）：
#   v1（第八波）：内容字 IDF 余弦，阈值 0.15。修复 LCS 漏检/误取；
#     独立复核发现成族回归：英数实体（iPhone/MU5123）整段被删、单字歧义
#     （"猫眼"误取"猫毛过敏"）（第九波判决 §5.2）。
#   v2.1（第九波判决 §8-C1）：
#     1) 混合表示：CJK 内容字 + 英数 token（小写整段，实体标识全序列重合=独立证据）；
#     2) 窗口级激活门：窗口与任一库存事实共享 ≥1 个原始 CJK bigram → 字级共享
#        计分；未激活时单字证据不足不计分。
#   v2.2（独立复核 §3 + 本批合并执行令 §6-1）：
#     1) 条目级归因门（_entry_gate）：每条事实的字级证据只由其自身与窗口的
#        可解释关联决定（bigram / 内容字≥2 / 英数 token 三通道），
#        禁止窗口级 any() 跨条目授权——否则一条"退票规则"背景记录即替
#        整库放行，"猫眼能退票吗"重新误取猫过敏（复核 §3.1 实测复现）；
#     2) bigram 分段修复：在任何非 CJK 字符处切断，不再跨标点伪造
#        "退，票→退票""猫。眼→猫眼"（复核 §3.3 实测复现）；
#     3) 权衡显式登记并配对回归断言：相关短问单字证据（"我能抱一下猫吗"×
#        猫毛过敏、"火锅很辣"×不吃辣）被拒——与"猫眼"单字歧义在字面上
#        不可区分（复核 §3.2），语义层缺失，不装作已修、不藏进总 PASS。
#     已知权衡（登记）：零字面重叠纯同义改述不可召回；HER/ME 归属检索层
#     不可分。开发节点观测见 refs/L3B_RETR_DEV_NODES_20261020.md。
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
import protocol_public            # noqa: E402

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


def _alnum_tokens(text):
    """英数字母数字连续序列 token（小写）——设备型号/航班号/编号等实体标识
    （第九波判决 §5.2：iPhone 15 Pro / MU5123 被字级规则整段删除造成漏检回归）。
    与 CJK 内容字并列参与 IDF 与匹配；停用字表不适用于 token。"""
    out, cur = [], ""
    for ch in text:
        if ch.isascii() and ch.isalnum():
            cur += ch.lower()
        elif cur:
            out.append(cur)
            cur = ""
    if cur:
        out.append(cur)
    return out


def _cjk_bigrams(text):
    """按标点/非 CJK 分隔符分段的原始 CJK bigram（不停用字剥离——实体连续性优先：
    停用字"都"会切断"成都"这类实体，第九波执行期 B5 反例实测）。
    v2.2 修复（独立复核 §3.3）：分段在任何非 CJK 字符处切断，先删分隔符再拼接会
    伪造"退，票→退票""猫。眼→猫眼"这类跨标点假 bigram。
    仅用于条目级激活门与追踪证据，字级计分集合仍用 _content_chars。"""
    segs, cur = [], []
    for ch in text:
        if "\u4e00" <= ch <= "\u9fff":
            cur.append(ch)
        elif cur:
            segs.append("".join(cur))
            cur = []
    if cur:
        segs.append("".join(cur))
    return {s[i] + s[i + 1] for s in segs for i in range(len(s) - 1)}


def _idf_table(fact_texts):
    """混合 IDF：CJK 内容字 + 英数 token（键 "T:<tok>"）。ln(1 + N/df)，库上现算。"""
    n = max(1, len(fact_texts))
    df = {}
    for t in fact_texts:
        for c in set(_content_chars(t)):
            df[c] = df.get(c, 0) + 1
        for tk in set(_alnum_tokens(t)):
            df["T:" + tk] = df.get("T:" + tk, 0) + 1
    return {c: math.log(1 + n / d) for c, d in df.items()}, n


def _cosine(w_chars, f_chars, idf, n):
    inter = set(w_chars) & set(f_chars)
    if not inter:
        return 0.0, ()
    num = sum(idf.get(c, math.log(1 + n)) for c in inter)
    den = math.sqrt(sum(idf.get(c, math.log(1 + n)) for c in set(w_chars))
                    * sum(idf.get(c, math.log(1 + n)) for c in set(f_chars)))
    return (num / den if den else 0.0), tuple(sorted(inter))


def _strip_time(text):
    for w in _TIME_WORDS:
        text = text.replace(w, " ")
    return text


def _entry_gate(w_bigrams, f_bigrams, char_share, tok_share):
    """条目级激活门 v2.2（独立复核 §3.1/§3.3）：一条事实的字级证据是否可采信，
    只由【该事实自身】与窗口的可解释关联决定，禁止任何其他库存条目替它放行
    （窗口级 any() 授权会让"退票规则"背景记录重新放行"猫过敏"，§3.1 实测）。
    三条独立通道（满足其一即放行）：
      a) bigram：该事实与窗口共享 ≥1 个分段保留的原始 CJK bigram（实体连续性）；
      b) 双字：内容字共享 ≥2 个（单字证据歧义不可信——"猫眼"≠"猫毛"；
         双字如"对猫/退票"提供最低主题一致性）；
      c) 英数 token 共享（实体标识全序列重合，独立证据，不受字级门约束）。
    单字且无 bigram 的事实被拒（登记权衡：相关短问如"我能抱一下猫吗"与
    "猫眼能退票吗"在字面上不可区分，见 §3.2；不得以语义黑名单修补）。
    返回 (admitted, reason)。"""
    if tok_share:
        return True, "token:" + ",".join(sorted(tok_share)[:3])
    shared_bg = w_bigrams & f_bigrams
    if shared_bg:
        return True, "bigram:" + ",".join(sorted(shared_bg)[:3])
    if len(char_share) >= 2:
        return True, "chars2:" + "".join(sorted(char_share)[:6])
    return False, ("denied:single_char_only" if len(char_share) == 1
                   else "denied:no_overlap")


def _retrieve(window_text, facts, trace=None, case_id=""):
    """v2.2 检索：混合 IDF 余弦 + 条目级归因激活门（第九波判决 §8-C1 +
    独立复核 §3 合并执行令 §6-1 收口）。

    规则（通用，无话题词表；阈值 0.15 沿用既有校准）：
    1) 混合表示：CJK 内容字（剥时间词/停用字）+ 英数 token（小写整段）。
    2) 条目级激活门（_entry_gate）：每条事实独立判定字级证据可采性——
       bigram / 内容字≥2 / 英数 token 三通道之一；任一其他条目不得替本条放行。
    3) score >= RETR_MIN 入选，按分数降序取 topk，平分按 clock 稳定排序。
    已知权衡（登记，配对回归显式断言）：
      - 相关短问单字证据（"我能抱一下猫吗"×猫毛过敏 / "火锅很辣"×不吃辣）
        被拒——与"猫眼"单字歧义在字面上不可区分，语义层缺失，不装作已修；
      - 零字面重叠纯同义改述不可召回（v1 已登记）；HER/ME 归属检索层不可分。
    """
    texts = [f.get("text", "") for f in facts]
    idf, n = _idf_table(texts)
    w_chars = _content_chars(window_text)
    w_bigrams = _cjk_bigrams(_strip_time(window_text))
    w_tokens = set(_alnum_tokens(window_text))
    log1n = math.log(1 + n)
    scored = []
    for i, f in enumerate(facts):
        f_chars = _content_chars(texts[i])
        f_bigrams = _cjk_bigrams(_strip_time(texts[i]))
        f_tokens = set(_alnum_tokens(texts[i]))
        tok_share = w_tokens & f_tokens
        char_share = set(w_chars) & set(f_chars)
        admitted, gate_reason = _entry_gate(w_bigrams, f_bigrams,
                                            char_share, tok_share)
        used_chars = char_share if admitted else set()
        num = (sum(idf.get(c, log1n) for c in used_chars)
               + sum(idf.get("T:" + tk, log1n) for tk in tok_share))
        if num <= 0:
            scored.append((0.0, i, f, (), (), gate_reason))
            continue
        den_w = (sum(idf.get(c, log1n) for c in set(w_chars))
                 + sum(idf.get("T:" + tk, log1n) for tk in w_tokens))
        den_f = (sum(idf.get(c, log1n) for c in set(f_chars))
                 + sum(idf.get("T:" + tk, log1n) for tk in f_tokens))
        den = math.sqrt(den_w * den_f) if den_w and den_f else 0.0
        s = num / den if den else 0.0
        scored.append((s, i, f, tuple(sorted(used_chars)),
                       tuple(sorted(tok_share)), gate_reason))
    scored.sort(key=lambda x: (-x[0], x[2].get("clock", ""), x[1]))
    selected_pairs = [(i, f) for s, i, f, _, _, _ in scored if s >= RETR_MIN][:RETRIEVE_TOPK]
    if trace is not None:
        sel_ids = {i for i, _ in selected_pairs}
        trace["retrieval"] = {
            "strategy": "hybrid-char-alnum-idf-cosine v2.2 (entry-level attribution gate)",
            "threshold": RETR_MIN, "topk": RETRIEVE_TOPK,
            "gate_policy": ("per-fact: bigram | content-chars>=2 | alnum-token; "
                            "no cross-entry authorization"),
            "window_content_chars": "".join(w_chars)[:80],
            "facts_scored": [
                {"fact_id": i, "clock": f.get("clock", ""),
                 "text": f.get("text", ""), "score": round(s, 4),
                 "shared_content_chars": "".join(inter),
                 "shared_alnum_tokens": list(tok),
                 "gate": gate,
                 "selected": i in sel_ids,
                 "filtered_reason": (None if i in sel_ids
                                     else "rank>topk" if s >= RETR_MIN
                                     else gate if str(gate).startswith("denied")
                                     else "score<%s" % RETR_MIN)}
                for s, i, f, inter, tok, gate in scored]}
    return selected_pairs


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
# （v2 实现见上方 _retrieve / _alnum_tokens / _cjk_bigrams；旧 v1 已删除，
#   防止同名后定义覆盖——第九波执行期自查发现并即时修正）


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
    # v0.9.2 追踪修复（第九波判决 §4）：追踪只消费【已验证且规范化的安全表示】——
    # analysis=null/list/string 时 parsed.get("analysis", {}) 不会替换为 dict
    # （键存在时默认值不生效），旧代码在其上继续 .get() 即 AttributeError 中断整条
    # episode。现以 isinstance 门为唯一安全入口；非 dict analysis 显式登记于
    # trace.writeback.skipped，不静默、不崩溃、第二轮照常执行。
    parsed = protocol_public.extract_json_block(res["text"])
    wb_skip = None
    ana_safe = None
    if parsed:
        raw_ana = parsed.get("analysis")
        if isinstance(raw_ana, dict):
            ana = raw_ana
            errs = protocol_public.analysis_type_errors(ana)
            if errs:
                wb_skip = "nested_type_violation:" + ",".join(errs[:6])
            else:
                ana_safe = ana    # 已过类型门，追踪/写回共用此规范化对象
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
        else:
            # analysis 缺失/为 null/list/string：写回政策=合法 analysis 独立写回
            # （MECHANISM.md 登记），非 dict 无可消费内容，显式留痕不中止。
            wb_skip = "analysis_not_dict:%s" % type(raw_ana).__name__

    trace["writeback"] = {"facts_appended":
                          [f.get("text", "") for f in
                           ((ana_safe or {}).get("scene_facts") or [])
                           if isinstance(f, dict) and (f.get("text") or "").strip()]
                          if ana_safe else None,
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
