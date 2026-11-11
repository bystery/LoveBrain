# candidates/phase3_facts — 事实出处与纠正状态候选（父版本 = phase2_expr / 检索 base = phase1_retr_idf）
#
# 唯一主变量 = 事实出处与纠正状态（v12 复核 §6-C 立项；v16 复核退修后本版为第 3 稿）。
#
# v16 复核（WAVE16_INDEPENDENT_REVIEW_20261021）四项退修与本版修复：
#   P1-类型门互斥（§2）：旧版完整 HH:MM 验证通过后又被拆成散数字重审且时间戳被排除，
#      149 条旧真实摘要回放中 66 条合法时刻被系统性误删（83 拒）。本版：验证分工——
#      a) 完整时刻逐字在场（原文或时间戳）；b) 其余 token 原文池校验前先剥离已验证
#      时刻跨度。无出处钟点仍被拒（20:29 反例保持），时刻保护不撤。
#   P1-纠正跨人物（§3）：旧版"旧值 token 相交即作废"波及无关主体（HER 改签作废 ME
#      同车次）。本版：硬作废限定同主体（旧事实来源说话人 ∩ 更正说话人）；跨主体共享
#      值只留痕 uncertain + 注入警示，不抹库；历史标记/未点名规则保持。
#   P1-状态自证（§4）：旧版 joined=摘要+原文，摘要自写"已经"即可 event，"已经约定"
#      绕过未确认保护，"我没空一起看"被接受子串误判 confirmed。本版：过去/完成标记
#      必须来自所引原文；共同活动 event 须原文同时含共同词+共同完成词（绑定活动）；
#      ME 接受检查先过拒绝/推脱标记；证据不支持 → unconfirmed（不当事实背书）。
#   P1-ongoing 旁路（§5）：旧版 ongoing 只复用状态函数不复用出处边界，无出处车次
#      经进行中事项裸注入。本版：_ongoing_support 实体支持检查（时刻逐字+token 原文池），
#      不可核验 → 隔离为"未核验摘要（不得当事实）"并持久化，渲染即带警示。
#
# v13 复核（WAVE15_V13_INDEPENDENT_REVIEW_20261021）确认的四族缺陷与本版修复：
#   P1-来源错号（§2）：旧 _src_lookup 枚举全部消息并把非 HER 都标成 ME，IDEA 既冒充
#      真实来源又挤占编号 → 合法 HER 事实被误拒。本版：编号规则与 lb_assembly.render_chat
#      严格一致——仅 HER/ME 按序占号 m0..，IDEA 不占号、不进映射（统一过滤/编号/角色）。
#   P1-纠正互杀（§3）：旧实现按"实体交集"作废，且一边追加新事实一边打击——摘要排列顺序
#      决定幸存者、新值同轮被自己作废、共享编号的历史被误杀、更正事件还随摘要拒绝而漏记。
#      本版：纠正按"轮"处理——先固定上一轮库（prev_facts 冻结快照），再对同一更正事件的
#      旧值建立"有方向"的更正（旧值=更正证据中被否定标记点名的值；新值=更正标记后的值），
#      最后才统一提交本轮新事实（同轮追加永不互杀）；含历史时间标记（去年/曾经/之前…）的
#      记录不因共享编号自动作废（标不确定）；旧值未被点名时只记更正事件并注入警示，不抹库。
#      更正事件独立于摘要是否被接收，一律落盘（kv_corrections.jsonl）。
#   P1-状态误标与 ongoing 绕过（§4）：旧版把"已确认/已发生"都标 offer，且 ongoing 原样
#      写回全量注入（"约定一起看"绕过事实门进入请求）。本版：状态推导改为
#      已发生（过去/完成标记，最高优先）→ confirmed（ME 真实消息在场且含接受标记）→
#      offer（约定词 + 证据为提议/问句且无 ME 接受）→ intent（未来标记）→ event；
#      ongoing 写回走同一状态推导，offer 一律改存"提议未确认（不可当已约定）"，
#      该标注随存储持久化并随注入渲染——作为事实消费的记忆通道全部覆盖。
#   P1-英数门误放误拒（§5）：旧版把时间戳整体当 token 池（日期组件被重拼成无出处钟点
#      "20:29"），又把"两盒→2盒"等价改写误拒。本版：时间戳只是证据元数据——复合时刻
#      HH:MM 必须逐字出现在所引原文或时间戳中（不可由日期组件重拼）；纯英数 token 只能
#      来自所引原文池（含中文数词等价归一：两/二→2…），不再从时间戳取数。
#
# 不变量（复核 §6-C 约束，逐条保持）：
#   - 检索排名算法零改动：base._retrieve 原函数调用，无副本；
#   - 温度/网关/输出槽位/评分协议/params 契约不变；
#   - 检索 base 与父本加载为原模块（无副本）；
#   - expression.md 注入与生成后四族词面审计继承自 A3（评估专用，不改写回复）。
# 已知边界（登记，不装作已修）：
#   - CJK 语义级编造（无中生有的地点/事件）机制层不判，靠生成侧提示 + 审计；
#   - HER/ME 归属检索层不可分（继承 base 登记）。

import json
import os
import re
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))          # .../phase3_facts/mechanism
_ROOT = os.path.join(_HERE, "..", "..", "..")               # repo root
_SYS = os.path.join(_ROOT, "arena")
if _SYS not in sys.path:
    sys.path.insert(0, _SYS)

import lb_assembly          # noqa: E402
import protocol_public            # noqa: E402

# ---- 加载检索 base（phase1_retr_idf，检索/存储唯一事实来源，无副本） ----
import importlib.util as _ilu
_BASE_PATH = os.path.join(_ROOT, "candidates", "phase1_retr_idf",
                          "mechanism", "mechanism.py")
_spec = _ilu.spec_from_file_location("phase1_retr_idf_base", _BASE_PATH)
base = _ilu.module_from_spec(_spec)
_spec.loader.exec_module(base)

# ---- 加载父本 A3（expression 审计四族复用，无副本） ----
_P2_PATH = os.path.join(_ROOT, "candidates", "phase2_expr",
                        "mechanism", "mechanism.py")
_spec2 = _ilu.spec_from_file_location("phase2_expr_parent", _P2_PATH)
parent = _ilu.module_from_spec(_spec2)
_spec2.loader.exec_module(parent)

_EXPR_ASSET = "system_prompt/expression.md"
_FACTS_ASSET = "system_prompt/facts_evidence.md"
_FACTS_FILE = base.FACTS_FILE            # kv_facts.jsonl（记录结构扩展，文件名不变）
ONGOING_FILE = base.ONGOING_FILE
CORRECTIONS_FILE = "kv_corrections.jsonl"   # 真实更正事件（独立于摘要是否被接收）

# ---- 通用确定性标记（领域无关；无任何题面实体词） ----
_CORRECTION_MARKS = ("改签", "更正", "改成", "换成", "改成了", "不是这趟", "不是这班", "改乘")
_JOINT_MARKS = ("一起", "约好", "约定", "说好了", "约了", "商量好")
_OFFER_MARKS = ("要不要", "好吗", "行吗", "怎么样", "愿不愿意")
_FUTURE_MARKS = ("打算", "准备", "计划", "想要", "下次", "回头", "到时候", "要去看", "要一起")
_PAST_MARKS = ("已经", "看完", "看过了", "吃过了", "做完了", "去了", "过完", "完成",
               "昨天", "昨晚", "前天", "上周", "去年", "上个月", "上次", "当天", "那时")
_HISTORICAL_MARKS = ("去年", "前年", "曾经", "当时", "以前", "之前", "上次", "上个月", "上周", "当年")
_ME_ACCEPT_MARKS = ("一起看", "一起吃", "一起去", "好啊", "好呀", "好的", "行啊", "行呀",
                    "嗯", "没问题", "走起", "来吧", "可以呀", "可以", "安排", "遵命",
                    "收到", "有空", "等你", "走起", "就今晚", "就这周", "走一个")
# ME 拒绝/推脱标记：接受子串（如"一起看"）出现在拒绝句里不算接受
_ME_REJECT_MARKS = ("没空", "不去", "不行", "不能", "没时间", "不方便", "去不了",
                    "不想", "下次吧", "再说吧", "改天吧", "看情况", "算了吧",
                    "去不成", "来不了", "有事先")
# 共同活动完成词（窄表，仅与共同词同时在场才构成共同完成证据；"找到了"等他事完成不算）
_JOINT_DONE_MARKS = ("看完", "看过了", "吃完了", "吃过了", "去过了", "去了", "办完了",
                     "办完", "干完", "过完了", "玩完了", "弄完了", "搞完了")
# 更正消息中的旧值/新值方向模式（确定性，正则抽取，非题面词表；允许值前有空格/标点）
_RE_OLD_VALUE = re.compile(r"(?:不是|不坐|不乘|不再坐|不改坐)\s*[,，。]?\s*([A-Za-z][A-Za-z0-9\-]*|\d[\d\-]*)")
_RE_NEW_VALUE = re.compile(r"(?:改签成|改成|换成|改乘|改坐|现在坐|现在乘|坐|乘)\s*[,，。]?\s*(?:的是\s*)?([A-Za-z][A-Za-z0-9\-]*|\d[\d\-]*)")
_TIME_HM_RE = re.compile(r"\d{1,2}:\d{2}")
_CJK_NUM = {"零": "0", "一": "1", "两": "2", "二": "2", "三": "3", "四": "4",
            "五": "5", "六": "6", "七": "7", "八": "8", "九": "9"}
_CORRECTION_NOTE = "⚠其后存在更正记录：相关值可能已失效，以最新真实消息为准"
_UNVERIFIED_LABEL = "未核验摘要（待确认，不得当事实）"


def _norm_cjk_nums(text):
    """中文数词→阿拉伯等价（等价归一，只影响证据池，不改写存储文本）。"""
    return "".join(_CJK_NUM.get(ch, ch) for ch in text)


# ---------- 来源映射（与 render_chat 编号严格一致；v13 复核 §2 修复） ----------

def _src_lookup(ctx):
    """m-id → (speaker, text, ts)。与 lb_assembly.render_chat 编号规则一致：
    仅 HER/ME 消息按序编号 m0..，IDEA 不占号、不进映射——IDEA 不能冒充真实来源，
    也不会挤占编号导致合法 HER 事实错配。模型草稿不在输入消息里，结构上不可能被引用。"""
    msgs = [m for m in ctx["input"].get("messages", []) if isinstance(m, dict)]
    lookup = {}
    n = 0
    for m in msgs:
        role = m.get("role")
        if role not in ("HER", "ME"):
            continue
        lookup["m%d" % n] = {"speaker": role,
                             "text": m.get("text", ""),
                             "ts": str(m.get("ts") or "")}
        n += 1
    return lookup


# ---------- 证据支持门（机制主变量核心；v13 复核 §5 修复） ----------

def _gate_fact(fact, lookup, case_id):
    """证据支持门。返回 (src_records|None, reject_reason|None)。
    拒绝策略：拒绝即不入库（不复述、不降级），理由与原文留痕于追踪。
    类型化检查（不把时间戳当任意取数池）——两类检查分工，不互相否决：
      a) 复合时刻 HH:MM：必须逐字出现在所引原文或时间戳（日期组件不可重拼成新钟点）。
         **通过 a 的完整时刻跨度在 b 中先剥离，不再作为散数字重审**——v16 复核 §2：
         旧版 149 条旧摘要回放中 66 条因"完整时刻验证通过后又被拆字拒绝"被系统性误删；
      b) 其余英数 token（车次/编号/数量）：只能来自所引原文池（含数词等价归一），
         时间戳不进 token 池。未经 a 验证的时刻到不了 b（已在 a 拒绝）。"""
    sids = fact.get("source_ids") or []
    if not isinstance(sids, list) or not sids:
        return None, "gate_reject:source_ids_empty"
    srcs = []
    for sid in sids:
        rec = lookup.get(str(sid).strip())
        if rec is None:
            return None, "gate_reject:source_id_unknown:%s" % sid
        srcs.append(dict(rec, key="%s:%s" % (case_id, sid)))
    text = fact.get("text") or ""
    # a) 复合时刻逐字检查
    for hm in _TIME_HM_RE.findall(text):
        if not any(hm in r["text"] or hm in r["ts"] for r in srcs):
            return None, "gate_reject:clock_not_in_evidence:" + hm
    # b) 英数 token：先剥离已通过 a 的完整时刻跨度（验证职责归 a），再取原文池
    text_for_toks = _TIME_HM_RE.sub(" ", text)
    toks = set(base._alnum_tokens(text_for_toks))
    if toks:
        pool = set()
        for r in srcs:
            pool |= set(base._alnum_tokens(r["text"]))
            pool |= set(base._alnum_tokens(_norm_cjk_nums(r["text"])))
        missing = toks - pool
        if missing:
            return None, "gate_reject:entity_not_in_evidence:" + ",".join(sorted(missing)[:4])
    return srcs, None


# ---------- 状态推导（v13 复核 §4 修复：已发生 > 已确认 > 提议 > 意向 > 事件） ----------

# ---------- ongoing 支持门（v16 复核 §5：状态标注≠内容证据支持） ----------

def _ongoing_support(item, lookup):
    """ongoing item 的可追溯支持检查：item 中每个复合时刻必须逐字在场，
    其余英数 token 必须出现在当前轮真实消息原文池（含数词归一）。
    返回 True=可核验；False=存在无出处实体（隔离为未核验摘要，禁止当事实消费）。"""
    reals = list(lookup.values())
    for hm in _TIME_HM_RE.findall(item):
        if not any(hm in r["text"] or hm in r["ts"] for r in reals):
            return False
    toks = set(base._alnum_tokens(_TIME_HM_RE.sub(" ", item)))
    if not toks:
        return True
    pool = set()
    for r in reals:
        pool |= set(base._alnum_tokens(r["text"]))
        pool |= set(base._alnum_tokens(_norm_cjk_nums(r["text"])))
    return not (toks - pool)


def _derive_status(fact_text, srcs):
    """确定性状态推导（不要求模型新增字段）。v16 复核 §4 第 3 稿：
    状态以真实证据对该活动的支持为依据——摘要自写的过去/完成词不作数；
    另一动作的完成词（"纪录片已经找到了"）不能替共同活动背书。

    共同活动 claim（事实含共同词）：
      confirmed   = ME 真实消息在场、含接受标记、且不含拒绝/推脱标记
                    （明确拒绝不得被接受子串覆盖，如"我没空一起看"）；
      event       = 所引原文同时含共同词与共同完成词（完成词绑定共同活动）；
      offer       = 所引证据为提议/问句（邀请≠约定）；
      unconfirmed = 其余（摘要自称"已约定"但证据不支持 → 不确定，不当事实背书）。
    非共同 claim：
      event       = 所引原文含过去/完成标记（摘要自证不作数，v16 复核 §4）；
      intent      = 未来标记；
      event(默认) = 其余。"""
    txt = fact_text or ""
    src_join = "".join(r["text"] for r in srcs)
    if any(w in txt for w in _JOINT_MARKS):
        me_srcs = [r for r in srcs if r.get("speaker") == "ME"]
        for r in me_srcs:
            t = r["text"]
            if any(w in t for w in _ME_REJECT_MARKS):
                continue                      # 拒绝/推脱句不是接受
            if any(w in t for w in _ME_ACCEPT_MARKS):
                return "confirmed"            # ME 真实接受在场
        if (any(w in src_join for w in _JOINT_MARKS)
                and any(w in src_join for w in _JOINT_DONE_MARKS)):
            return "event"                    # 共同完成证据（原文绑定共同活动）
        if (any(w in src_join for w in _OFFER_MARKS)
                or any(r["text"].rstrip().endswith(("吗", "？", "?")) for r in srcs)):
            return "offer"                    # 仅提议/问句在场 → 未确认
        return "unconfirmed"                  # 摘要自称已发生但证据不支持
    if any(w in src_join for w in _PAST_MARKS):
        return "event"                        # 过去/完成标记须来自原文
    low = txt.lower()
    if any(w in low for w in _FUTURE_MARKS) or any(w in src_join for w in _FUTURE_MARKS):
        return "intent"
    return "event"


def _is_correction(srcs):
    return any(any(w in r["text"] for w in _CORRECTION_MARKS) for r in srcs)


# ---------- 真实更正事件（v13 复核 §3 修复：按轮、按事件、有方向） ----------

def _detect_correction_event(lookup):
    """当前轮真实消息中的更正事件。返回 dict|None。
    old_values = 被否定标记点名的值（有方向）；new_values = 更正/现在值标记后的值；
    values = 更正窗口全部英数 token（用于保守注入警示）；resolved = 旧值是否被点名；
    speakers = 发出更正标记的真实消息说话人（v16 复核 §3：硬作废限定同主体）。"""
    real = list(lookup.values())
    corr_speakers = set()
    for r in real:
        if any(w in r["text"] for w in _CORRECTION_MARKS):
            corr_speakers.add(r["speaker"])
    if not corr_speakers:
        return None
    old_values, new_values = set(), set()
    for r in real:
        old_values |= set(v.lower() for v in _RE_OLD_VALUE.findall(r["text"]))
        new_values |= set(v.lower() for v in _RE_NEW_VALUE.findall(r["text"]))
    new_values -= old_values                    # "不是G7812了 我改签成D3145"：旧不占新
    values = set()
    for r in real:
        values |= set(t.lower() for t in base._alnum_tokens(r["text"]))
    return {"old_values": sorted(old_values), "new_values": sorted(new_values),
            "values": sorted(values), "resolved": bool(old_values),
            "speakers": sorted(corr_speakers)}


def _append_correction(h, ev, clock, case_id, src_keys):
    rec = {"kind": "correction_event", "clock": clock, "case_id": case_id,
           "src_keys": src_keys, "old_values": ev["old_values"],
           "new_values": ev["new_values"], "values": ev["values"],
           "resolved": ev["resolved"]}
    with open(os.path.join(h["sandbox"], CORRECTIONS_FILE), "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    return rec


def _load_corrections(h):
    p = os.path.join(h["sandbox"], CORRECTIONS_FILE)
    if not os.path.isfile(p):
        return []
    out = []
    with open(p, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    out.append(json.loads(line))
                except ValueError:
                    continue
    return out


def _apply_correction_to_prev(h, prev_facts, ev, corr_key):
    """对上一轮冻结库施加有方向的更正（本轮回追加前调用，同轮新事实不受影响）。
    v16 复核 §3：硬作废限定**同主体**——旧事实的来源说话人须与更正说话人相交
    （说话人是主体证据，但不等同于所有被提及主体）：
    - 旧值被点名（resolved）且同主体、无历史时间标记 → superseded；
    - 共享旧值但**跨主体**（如 HER 改签不影响 ME 自己的车次）→ 不抹库，留痕 uncertain；
    - 含历史时间标记 → 不作废（共享编号≠同一事件），留痕 uncertain；
    - 旧值未点名：不抹任何记录（拿不准关联只标不确定）。"""
    corrections, uncertain = [], []
    if not ev["resolved"]:
        return corrections, uncertain
    olds = set(ev["old_values"])
    speakers = set(ev.get("speakers") or [])
    for old in prev_facts:
        if old.get("superseded"):
            continue
        ot = set(t.lower() for t in (old.get("entities") or
                                     base._alnum_tokens(old.get("text", ""))))
        if not (ot & olds):
            continue
        if any(w in old.get("text", "") for w in _HISTORICAL_MARKS):
            uncertain.append({"text": old.get("text", ""),
                              "note": "shared_value_with_history_mark"})
            continue
        old_speakers = set(r.get("speaker") for r in (old.get("src") or []))
        if speakers and old_speakers and not (speakers & old_speakers):
            # 跨主体共享值：共享编号不构成同一事件，不硬作废（v16 复核 §3 反例：
            # HER 改签 D3145 不得作废 ME 仍有效的同车次事实）
            uncertain.append({"text": old.get("text", ""),
                              "note": "cross_subject_shared_value"})
            continue
        old["superseded"] = True
        old["superseded_by"] = corr_key
        corrections.append({"superseded_text": old.get("text", ""), "by": corr_key})
    if corrections:
        p = os.path.join(h["sandbox"], _FACTS_FILE)
        tmp = p + ".tmp"
        with open(tmp, "w", encoding="utf-8") as tf:
            for old in prev_facts:
                tf.write(json.dumps(old, ensure_ascii=False) + "\n")
        os.replace(tmp, p)
    return corrections, uncertain


# ---------- 存储（文件名/读写函数与 base 一致，记录结构扩展） ----------

def _append_fact(h, fact):
    with open(os.path.join(h["sandbox"], _FACTS_FILE), "a", encoding="utf-8") as f:
        f.write(json.dumps(fact, ensure_ascii=False) + "\n")


def _load_facts(h):
    return base._load_facts(h)


def _load_ongoing(h):
    return base._load_ongoing(h)


def _save_ongoing(h, ongoing):
    base._save_ongoing(h, ongoing)


def _write_trace(h, trace):
    base._write_trace(h, trace)


def _hours_between(a, b):
    return base._hours_between(a, b)


# ---------- 提示装配：expression.md（继承 A3） + facts_evidence.md（本候选） ----------
# 直接调用式组装（本候选自持 run_turn，无需作用域替换全局函数）。

_raw_build = getattr(lb_assembly.build_reply_prompts, "_expr_orig",
                     lb_assembly.build_reply_prompts)


def _build_with_assets(assets_root, case_input):
    out = _raw_build(assets_root, case_input)
    for rel in (_EXPR_ASSET, _FACTS_ASSET):
        guard = lb_assembly.read_asset(assets_root, rel)
        if guard:
            out["system"] = out["system"] + "\n\n---\n\n" + guard
    return out


# ---------- 注入渲染（排序不变，仅标注） ----------

def _render_scene_chain(selected, corr_ctx):
    """corr_ctx: False/None=无更正；True=legacy（库内存在更正事件，按实体标注）；
    dict={"values":[...], "new_values":[...], "unresolved_clocks":[...]}。"""
    lines = []
    corr_values, new_values, unresolved_clocks = set(), set(), []
    if corr_ctx is True:
        pass                                   # legacy 布尔：下方按实体标注
    elif corr_ctx:
        corr_values = set(corr_ctx.get("values") or [])
        new_values = set(corr_ctx.get("new_values") or [])
        unresolved_clocks = sorted(set(corr_ctx.get("unresolved_clocks") or []))
    for _, f in selected:
        note = ""
        ents = set(t.lower() for t in (f.get("entities") or []))
        status = f.get("status", "event")
        if f.get("superseded"):
            continue   # 已被有方向更正的旧值整条不入请求
        if status == "offer":
            note += "｜⚠提议未确认，不当已约定"
        elif status == "confirmed":
            note += "｜已确认（ME 已答复）"
        elif status == "intent":
            note += "｜⚠意向未发生"
        elif status == "unconfirmed":
            note += "｜⚠未确认（证据不足，不当事实背书）"
        if corr_ctx is True:
            if ents:
                note += "｜" + _CORRECTION_NOTE
        else:
            # 命中更正窗口值的 → 精确警示；未点名旧值的更正事件在场时，
            # 对早于该事件且不含新值的带实体事实做保守警示（拿不准就标注，不抹库）
            if ents & corr_values:
                note += "｜" + _CORRECTION_NOTE
            else:
                for uc in unresolved_clocks:
                    if ents and ents <= new_values:
                        break
                    if (f.get("clock") or "") < uc:
                        note += "｜" + _CORRECTION_NOTE
                        break
        # 相对时间锚定：事实文本含相对时间词 → 附出处日期锚
        anchor = f.get("anchor_ts") or ""
        text = f.get("text", "")
        if anchor and any(w in text for w in base._TIME_WORDS):
            note += "｜相对时间以出处消息日 %s 为锚" % anchor.split(" ")[0]
        src_keys = ",".join(r["key"] for r in (f.get("src") or []))
        lines.append("- [%s] %s%s%s" % (f.get("clock", ""), text,
                                        ("｜src=" + src_keys) if src_keys else "",
                                        note))
    return "\n".join(lines)


# ---------- 生命周期 ----------

def init(assets_root, episode_meta, gateway, sandbox):
    return base.init(assets_root, episode_meta, gateway, sandbox)


def set_memory_access(h, enabled):
    base.set_memory_access(h, enabled)


def run_turn(h, ctx, gw):
    h["turn"] += 1
    ai = ctx["input"]
    window = "".join(m.get("text", "") for m in ai.get("messages", [])
                     if isinstance(m, dict))
    mem_on = bool(h.get("memory_access", True))
    clock = ai.get("clock", "")
    case_id = ctx.get("case_id") or ai.get("case_id", "")
    trace = {"turn": h["turn"], "case_id": case_id, "clock": clock,
             "memory_access": mem_on,
             "strategy_note": ("唯一主变量=事实出处与纠正状态；检索=base._retrieve 原函数；"
                               "写回=来源映射(render_chat对齐)+类型化证据门+按轮有方向纠正"
                               "+已发生/已确认/提议/意向分态+ongoing同门")}
    facts = _load_facts(h) if mem_on else []
    ongoing = _load_ongoing(h) if mem_on else []
    prev_facts = [dict(f) for f in facts]     # 上一轮冻结快照（纠正只作用于它）
    corrections_hist = _load_corrections(h) if mem_on else []
    trace["facts_stored"] = [{"fact_id": i, "clock": f.get("clock", ""),
                              "text": f.get("text", ""), "status": f.get("status", ""),
                              "superseded": bool(f.get("superseded"))}
                             for i, f in enumerate(facts)]
    trace["ongoing_stored"] = ongoing
    retrieved_pairs = base._retrieve(window, facts, trace=trace) if mem_on else []
    corr_ctx = None
    if mem_on:
        hist_values, hist_new, hist_unres = set(), set(), []
        for c in corrections_hist:
            hist_values |= set(c.get("values") or [])
            hist_new |= set(c.get("new_values") or [])
            if not c.get("resolved") and c.get("clock"):
                hist_unres.append(c["clock"])
        if hist_values or hist_unres:
            corr_ctx = {"values": sorted(hist_values), "new_values": sorted(hist_new),
                        "unresolved_clocks": sorted(set(hist_unres))}
    selected = [(i, f) for i, f in retrieved_pairs if not f.get("superseded")]
    dropped_superseded = [i for i, f in retrieved_pairs if f.get("superseded")]
    trace["injected"] = {
        "scene_chain_fact_ids": [i for i, _ in selected],
        "superseded_dropped": dropped_superseded,
        "ongoing_all": [o.get("item", "") for o in ongoing],
        "topic": h.get("topic", "") if mem_on else "",
        "ongoing_injection_mode": "full（与父版本一致，不经过检索）"}

    if mem_on and clock and h.get("last_clock"):
        dh = _hours_between(h["last_clock"], clock)
        if dh is not None:
            h["hours_since_last_exchange"] = max(0.0, dh)
    if clock:
        h["last_clock"] = clock

    kb = {"scene_chain": _render_scene_chain(selected, corr_ctx),
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
    assets_root = ctx.get("assets_root") or h["assets_root"]
    assembled = _build_with_assets(assets_root, ai2)
    images = [m["image"] for m in ai.get("messages", [])
              if isinstance(m, dict) and m.get("image")]
    res = gw.generate(assembled["system"], assembled["user"],
                      images=images or None, tag="main")

    # ---- 写回（本候选主变量）----
    # 顺序（v13 复核 §3）：① 检测当前轮真实更正事件并落盘（独立于摘要是否被接收）
    # → ② 对上一轮冻结库施加有方向更正 → ③ 统一提交本轮新事实（同轮永不互杀）
    # → ④ ongoing 走同一状态推导（offer 不可当已约定）。
    parsed = protocol_public.extract_json_block(res["text"])
    wb_skip = None
    ana_safe = None
    gate_acc, gate_rej, corrections, uncertain = [], [], [], []
    lookup = _src_lookup(ctx)
    corr_ev = _detect_correction_event(lookup) if mem_on else None
    if corr_ev is not None:
        src_keys = ["%s:%s" % (case_id, k) for k in
                    [sid for sid, r in lookup.items()
                     if any(w in r["text"] for w in _CORRECTION_MARKS)]]
        rec = _append_correction(h, corr_ev, clock, case_id, src_keys)
        if mem_on:
            c1, c2 = _apply_correction_to_prev(h, prev_facts, corr_ev,
                                               rec["src_keys"][0] if rec["src_keys"] else case_id)
            corrections.extend(c1)
            uncertain.extend(c2)
            # 更正事件即时进入注入警示上下文（本轮库读取已发生，下一轮起持续警示）
            corr_ctx = corr_ctx or {"values": [], "new_values": [],
                                    "unresolved_clocks": []}
            corr_ctx = {
                "values": sorted(set(corr_ctx.get("values") or []) | set(corr_ev["values"])),
                "new_values": sorted(set(corr_ctx.get("new_values") or [])
                                     | set(corr_ev["new_values"])),
                "unresolved_clocks": sorted(
                    set(corr_ctx.get("unresolved_clocks") or [])
                    | ({clock} if not corr_ev["resolved"] and clock else set()))}
            facts = [f for f in facts]   # prev_facts 中的 superseded 标记同步回工作集
            for pf, wf in zip(prev_facts, facts):
                if pf.get("superseded"):
                    wf["superseded"] = True
                    wf["superseded_by"] = pf.get("superseded_by")
    if parsed:
        raw_ana = parsed.get("analysis")
        if isinstance(raw_ana, dict):
            ana = raw_ana
            errs = protocol_public.analysis_type_errors(ana)
            if errs:
                wb_skip = "nested_type_violation:" + ",".join(errs[:6])
            else:
                ana_safe = ana
                # memory_access=False：写回整体关闭（与 base notes 声明语义一致）
                for f in (ana.get("scene_facts") or []) if mem_on else []:
                    if not isinstance(f, dict):
                        continue
                    t = (f.get("text") or "").strip()
                    if not t:
                        continue
                    srcs, reason = _gate_fact(f, lookup, case_id) if mem_on else ([], None)
                    if reason:
                        gate_rej.append({"text": t, "reason": reason,
                                         "source_ids": f.get("source_ids")})
                        continue
                    status = _derive_status(t, srcs)
                    ents = sorted(set(base._alnum_tokens(t)))
                    rec = {"clock": clock, "text": t,
                           "src": srcs, "entities": ents, "status": status,
                           "anchor_ts": (srcs[0]["ts"] if srcs else clock),
                           "correction": bool(_is_correction(srcs)) if srcs else False,
                           "superseded": False, "superseded_by": None}
                    _append_fact(h, rec)
                    facts.append(rec)
                    gate_acc.append({"text": t, "status": status,
                                     "src": [r["key"] for r in srcs]})
                changed = False
                for o in (ana.get("ongoing") or []) if mem_on else []:
                    item = (o.get("item") or "").strip() if isinstance(o, dict) else ""
                    if item:
                        # ongoing 与事实同门（v16 复核 §5 第 2 稿）：状态推导之外，
                        # 还须通过实体支持检查——无出处实体不得作为事实持久化注入
                        if not _ongoing_support(item, lookup):
                            o_status = _UNVERIFIED_LABEL
                        else:
                            o_status = _derive_status(item, list(lookup.values()))
                            if o_status == "offer":
                                o_status = "提议未确认（不可当已约定）"
                            elif o_status == "confirmed":
                                o_status = "已确认（ME 已答复）"
                            elif o_status == "unconfirmed":
                                o_status = "未确认（不当事实背书）"
                        ongoing = [x for x in ongoing if x["item"] != item]
                        ongoing.append({"item": item, "status": o_status,
                                        "verified": o_status != _UNVERIFIED_LABEL})
                        changed = True
                if changed:
                    _save_ongoing(h, ongoing)
                label = (ana.get("topic_label") or "").strip()
                if label and label != h.get("topic"):
                    h["topic"] = label
                    h["topic_started_at"] = clock
        else:
            wb_skip = "analysis_not_dict:%s" % type(raw_ana).__name__

    trace["writeback"] = {"facts_appended":
                          [g["text"] for g in gate_acc] if ana_safe else None,
                          "skipped": wb_skip}
    trace["fact_gate"] = {"accepted": gate_acc, "rejected": gate_rej,
                          "corrections": corrections, "uncertain": uncertain,
                          "correction_event": bool(corr_ev),
                          "policy": ("typed evidence support (HH:MM verbatim then "
                                     "span-stripped from token check; tokens from cited "
                                     "text only with numeral equivalence); render_chat-"
                                     "aligned source ids; turn-scoped subject-scoped "
                                     "directional correction (same-speaker hard "
                                     "supersede, cross-subject uncertain only); status "
                                     "from cited evidence not summary self-certainty; "
                                     "ongoing entity-support gated (unverified isolated)")}
    _write_trace(h, trace)

    notes = "phase3_facts: evidence-gated writeback(typed gate+render_chat-aligned src+turn-scoped correction+status); ongoing same-gated"
    if corr_ev is not None:
        notes += " | correction_event=1(resolved=%s)" % corr_ev["resolved"]
    if corrections:
        notes += " | superseded=%d" % len(corrections)
    if gate_rej:
        notes += " | gate_rejected=%d" % len(gate_rej)
    if wb_skip:
        notes += " | writeback_skipped(%s)" % wb_skip
    if not mem_on:
        notes += (" | memory_access=False: kv_facts/kv_ongoing/topic/检索/"
                  "last_clock/initial_kb 已关闭（共同画像 persona 保留）")
    out = {"reply": res["text"], "notes": notes,
           "id_map": assembled["id_map"]}

    # ---- 生成后四族词面审计（继承 A3：评估专用，不改写回复、不参与控制流） ----
    parsed2 = protocol_public.extract_json_block(out.get("reply") or "")
    findings, parse_ok = [], parsed2 is not None
    if parse_ok:
        import re as _re
        _FAMILIES = parent._load_families()
        resp = parsed2.get("response")
        texts = []
        if isinstance(resp, dict):
            for k in ("recommended", "bad_boy", "playful", "warm"):
                v = resp.get(k)
                if isinstance(v, str) and v.strip():
                    texts.append(("response." + k, v))
        dirs = parsed2.get("directions")
        if isinstance(dirs, list):
            for i, v in enumerate(dirs):
                if isinstance(v, str) and v.strip():
                    texts.append(("directions[%d]" % i, v))
        for field, text in texts:
            for fam, pat, label in _FAMILIES:
                m = _re.search(pat, text)
                if m:
                    findings.append({"family": fam, "field": field,
                                     "match": m.group(0), "label": label})
    try:
        with open(os.path.join(h["sandbox"], "expr_trace.jsonl"), "a", encoding="utf-8") as f:
            f.write(json.dumps({"turn": h.get("turn"), "case_id": case_id,
                                "parse_ok": parse_ok,
                                "expr_policy": "expression.md injected",
                                "findings": findings,
                                "n_findings": len(findings)},
                               ensure_ascii=False, default=str) + "\n")
    except OSError:
        pass
    return out


def snapshot(h):
    # 与 base 一致（retrieval_trace/expr_trace 不入快照）；含更正事件文件
    parts = []
    for fn in (_FACTS_FILE, ONGOING_FILE, CORRECTIONS_FILE):
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
