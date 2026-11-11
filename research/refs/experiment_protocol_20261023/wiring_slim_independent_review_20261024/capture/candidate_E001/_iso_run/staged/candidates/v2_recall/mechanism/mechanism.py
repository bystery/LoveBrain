# candidates/v2_recall/mechanism/mechanism.py
# 父本 = candidates/reference_product_v1（原版 LoveBrain 保真等价实现，也是首场基线）
#
# 唯一主变量 = M2「记忆检索与注入」：**回忆型提问要能取到历史**。
#
# 立项依据（logs/exam1_20261023/EXAM_REPORT.md §5-F1，全部来自首场 24 位真实答卷）：
#   E001_T06 她问「店里又来问杯底怎么弄，我上回怎么跟你说的来着？」
#   → 基线回复「上回那句我没记住」。复盘查出：
#     ① 事实在 moment/plan.md 里（"她决定杯底不刻字"）；
#     ② 但 OngoingContextSelector 默认拒绝注入，且只看 item 名与真实消息的**关键词**重合，
#        "陶瓷杯"对不上"杯底/店里"，该事项被过滤 → prompt 里"刻字/杯底/留空/陶瓷"出现 0 次；
#     ③ 模型把这一轮判成新话题 → 轮转清空此刻层 → 下一轮更没上下文（级联失忆）。
#   即：**不是模型不记得，是检索没有把记忆递过去**。
#
# 本版只做检索/注入这一件事（不动存储结构、不动输出契约、不动措辞资产）：
#   1. 回忆意图识别：**本轮最后一条真实消息**（HER 或 ME）命中回忆标记。
#   2. ongoing 相关性旁路：回忆轮下用归档召回正文当扩展 clue 再走父本 selector
#      的 ACTIVE/DORMANT/relevance 判定；无关/休眠/终态事项仍不注入。
#   3. 归档召回：从 memory/raw_topic.md 的历史归档里取最相关的 1–2 个话题块，
#      以「# 【回忆】」段注入，并明确标注"不是本轮输入、可引用但别照抄"。
#   4. 非回忆轮完全不注入（宁可漏，不许每轮塞历史把 prompt 变成回忆录）。
#
# 四组保护反例（refs/exam1_independent_review_20261023/REVIEW.md §2 独立复现，
# tools/test_v2_recall.py c6/c7/c8/c9 验证）：
#   P1  旧词不误触发当轮 —— 同 send 里旧句含"之前"+新句问"午饭"→ 不激活；
#   P2  休眠/无关事项不无差别灌入 —— 30 条无关 DORMANT 项，回忆轮不整库倾倒；
#   P3  新增上下文有总预算     —— recall 段 + ongoing 旁路合并 ≤ MAX_NEW_CONTEXT_CHARS；
#   P4  snapshot→restore 一致 —— 恢复后候选 RecallSelector 仍生效，不降级父本行为。
#
# 明确不做的（对照宪法 §2.1 单一主变量）：
#   - 不修 infer_scene 的「又来」误判（那是第二个变量，留给下一轮，见 EXAM_REPORT §6）；
#   - 不修 JSON 容错（同上，且属输出契约层，不是检索层）；
#   - 不换模型、不改温度、不改资产措辞。
import importlib.util
import os
import re
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)                       # candidates/v2_recall
_BASE = os.path.join(os.path.dirname(_CAND), "reference_product_v1")
sys.path.insert(0, _BASE)


def _load_base():
    spec = importlib.util.spec_from_file_location(
        "ref_product_v1_mechanism", os.path.join(_BASE, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


base = _load_base()
pm = base.pm
rc = base.rc

# 回忆标记：她（或我）在要求"把之前说过的调出来"。
# 只收明确指向过去的词组，不收"刚才/现在"这类当轮词（否则每轮都命中，等于没有触发条件）。
RECALL_MARKERS = (
    "上回", "上次", "上上回", "之前", "前阵子", "那天", "你说过", "我说过",
    "我跟你说", "你跟我说的", "怎么说的", "怎么跟你说的", "怎么跟我说的",
    "还记得", "记不记得", "记不记得住", "忘了没", "忘记了", "再说一遍",
    "那个事", "那件事", "后来那个", "你记得",
)

_RECALL_HEADER_LINE = "# 【回忆】相关历史（来自本地归档，不是本轮输入）"
_RECALL_HEADER_HINT = (
    "下面是她/你以前说过的原话与当时记下的事实，用来回答本轮"
    "「她问的那件事」。可以据实回答、也可以引用其中细节，但不要整段照抄，"
    "不要把它当成她这一轮刚说的话。")
RECALL_HEADER = _RECALL_HEADER_LINE + "\n" + _RECALL_HEADER_HINT + "\n"

MAX_RECALL_BLOCKS = 2
MAX_RECALL_CHARS = 1200          # 归档召回段单独上限（UTF-16 码元）
MAX_NEW_CONTEXT_CHARS = 1800     # 本轮【新增】上下文总预算（UTF-16 码元，含段头/分隔）
MAX_ONGOING_EXTRA_LINES = 4      # 相关 extras 数量硬顶（防"20 项都相关"撑爆）
MAX_EXTRA_CHAIN_CHARS = 120      # 单条 extras chain 上限（UTF-16 码元）

# 进行中事项段头（父本 _knowledge_block 用同一字面串；v2 计入新增预算）
_ONGOING_HEADER_LITERAL = "# 【进行中事项】（长期追踪，仅在与当前对话相关时提及，不必每条都提）"
# 回忆段前后分隔（_wrap_recall_into_knowledge 用 "\n\n" + section + "\n"）
_RECALL_SEP_U = 3                # 前导 "\n\n"(2) + 段尾 "\n"(1)

# ───────────────── C 收口：UTF-16 码元安全的截断 ─────────────────
# 旧实现用 Python 码点切片 [:n] / [-n:] 但按 rc.utf16_len 计长度；emoji 等补充平面
# 字符占 2 码元却只算 1 码点 → "裁到 1200" 实际留 1200 码点 = 最多 2400 码元，越预算。
# 下列两个截断按 UTF-16 码元计量、且只在码点边界落脚（绝不切断代理对），
# 让"选择→截断→渲染"三处单位一致。
def _cu(ch: str) -> int:
    return 2 if ord(ch) > 0xFFFF else 1


def utf16_truncate_tail(s, cap):
    """保留末尾 ≤ cap 个 UTF-16 码元，按码点边界回退，不切断代理对。"""
    if not s:
        return ""
    if rc.utf16_len(s) <= cap:
        return s
    total, start = 0, len(s)
    for i in range(len(s) - 1, -1, -1):
        c = _cu(s[i])
        if total + c > cap:
            break
        total += c
        start = i
    return s[start:]


def utf16_truncate_head(s, cap):
    """保留开头 ≤ cap 个 UTF-16 码元，按码点边界回退，不切断代理对。"""
    if not s:
        return ""
    if rc.utf16_len(s) <= cap:
        return s
    total = 0
    for i, ch in enumerate(s):
        c = _cu(ch)
        if total + c > cap:
            return s[:i]
        total += c
    return s


_ARCHIVE_SPLIT = re.compile(r"(?m)^# \[")


def _real_messages(messages):
    return [m for m in messages if m.get("role") in ("HER", "ME")]


def real_text(messages):
    return " ".join(m.get("content", "") for m in _real_messages(messages))


def last_real_text(messages):
    """P1：回忆意图只看本轮最后一条真实消息（HER 或 ME）。
    多句 send 里旧句含回忆词 + 新句问别的事 → 不该误触发。
    E001_T06 实测 HER 单句"店里又来问杯底怎么弄，我上回怎么跟你说的来着？"
    最后一条即含回忆标记，仍命中。"""
    real = _real_messages(messages)
    if not real:
        return ""
    return (real[-1].get("content") or "")


def is_recall_turn(messages):
    """本条真实消息是否在要求回忆。**只看最后一条 HER/ME 消息**，
    不看 IDEA 备注，不看更早消息里的旧回忆词（避免 reviewer 反例误伤）。"""
    text = last_real_text(messages)
    if not text:
        return False
    return any(k in text for k in RECALL_MARKERS)


def parse_archive(raw):
    """raw_topic.md → [{"title","body"}]。

    归档条目形状（product_memory.archive_entry）：
        # [ts] 旧话题
        ## [ts] 状态变化 …
        ### [ts] 对话记录 …
    分割用行首 `# [`，块内保留原样。坏文件返回空表（读侧宽容）。
    """
    if not (raw or "").strip():
        return []
    parts = _ARCHIVE_SPLIT.split(raw)
    blocks = []
    for p in parts:
        p = p.strip()
        if not p:
            continue
        # split 把 "# [" 吃掉了，补回来再取首行标题
        p = "# [" + p
        first = p.splitlines()[0].strip()
        title = first.split("]", 1)[-1].strip() if "]" in first else first
        blocks.append({"title": title, "body": p})
    return blocks


def _score(block, clue):
    """粗相关度：标题命中权重 3，正文命中权重 1；用 2-gram 字符重合，不依赖分词。"""
    def grams(s):
        s = re.sub(r"\s+", "", s or "")
        return {s[i:i + 2] for i in range(len(s) - 1)} if len(s) > 1 else {s}

    clue_g = grams(clue)
    if not clue_g:
        return 0
    t = grams(block["title"])
    b = grams(block["body"][:600])
    return 3 * len(clue_g & t) + len(clue_g & b)


def _strip_markers(text):
    """"上回/上次…"这些标记本身也是归档正文里常见的词（"你上回做的那个杯子"），
    拿它们当线索会命中任意历史块。做相关度前先剥掉，只留"问的是什么"。"""
    out = text
    for k in sorted(RECALL_MARKERS, key=len, reverse=True):
        out = out.replace(k, " ")
    return out


def recall_body(h, messages, topic_label=""):
    """回忆意图下从归档挑最相关的 1–2 块，返回 body 字符串（不含 header）。
    非回忆轮 / 零重合 → 返回空串。"""
    if not is_recall_turn(messages):
        return ""
    raw = h["storage"].read(h["kb"], "memory/raw_topic.md")
    blocks = parse_archive(raw)
    if not blocks:
        return ""
    clue = _strip_markers(real_text(messages)) + " " + (topic_label or "")
    ranked = sorted(blocks, key=lambda b: -_score(b, clue))
    picked = [b for b in ranked[:MAX_RECALL_BLOCKS] if _score(b, clue) > 0]
    if not picked:
        return ""
    body = "\n\n".join(b["body"] for b in reversed(picked))   # 旧→新
    if rc.utf16_len(body) > MAX_RECALL_CHARS:
        # C：按 UTF-16 码元裁尾，不切断 emoji 代理对；裁后尽量回到归档块起点
        body = utf16_truncate_tail(body, MAX_RECALL_CHARS)
        if "# [" in body:
            body = body[body.find("# ["):]
    return body


def _bigrams(s: str) -> set:
    s = re.sub(r"\s+", "", s or "")
    if len(s) < 2:
        return {s} if s else set()
    return {s[i:i + 2] for i in range(len(s) - 1)}


# 常见停用字（2-gram 里出现太广，无区分力）；仅作减法，不影响候选判定
_RECALL_STOPGRAMS = frozenset(["我来", "你是", "我的", "我们", "什么", "怎么",
                                "这个", "那个", "现在", "一下"])
_MIN_BIGRAM_OVERLAP = 2      # 至少共享 2 个 bigram 才算相关（P2 严格）


def _relevant_for_recall_clue(item: dict, low_clue_bigrams: set) -> bool:
    """回忆轮旁路的**相关性门**：item 的 (name + chain) bigram 与 recall_clue bigram
    至少交集 ≥ _MIN_BIGRAM_OVERLAP 才算"这条事项与召回话题相关"。
    无关 DORMANT 项即使状态可复活也不注入（P2 防线，reviewer 30 项灌入反例）。"""
    if not low_clue_bigrams:
        return False
    hay = (item.get("name") or "") + " " + (item.get("chain") or "")
    grams = _bigrams(hay) - _RECALL_STOPGRAMS
    if not grams:
        return False
    return len(grams & low_clue_bigrams) >= _MIN_BIGRAM_OVERLAP


class RecallSelector:
    """给基线 selector 加一条**相关性扩展**：回忆轮下，把归档召回正文当补充 clue，
    让原本被父本关键词门挡掉的 ACTIVE/DORMANT 项获得匹配机会；
    无关 DORMANT/已完成/已取消仍走父本原判定，杜绝全量灌入（P2）。
    父本 extract_keywords 只出整块 name，短中文消息与长事项名不 substring 匹配；
    旁路用 bigram 交集判相关性。"""

    def __init__(self, inner):
        self._inner = inner

    def select_for_injection(self, kb, messages, current_turn,
                             reply_directive=None, effective_intent=None,
                             recall_clue: str = ""):
        res = self._inner.select_for_injection(
            kb, messages, current_turn, reply_directive=reply_directive,
            effective_intent=effective_intent)
        if not is_recall_turn(messages):
            return res
        # 回忆轮：仅在拿到非空归档召回正文时，用它做二次相关性判定；
        # clue 空 → 保持父本行为（不主动灌入）。
        if not (recall_clue or "").strip():
            return res
        have = set(res.get("injectedItemNames") or ())
        clue_bigrams = _bigrams(recall_clue) - _RECALL_STOPGRAMS
        if not clue_bigrams:
            return res
        extras = []
        for it in (res.get("allItems") or []):
            name = it.get("name") or ""
            if not name or name in have:
                continue
            status = it.get("itemStatus")
            # 完成/取消永不复活（与父本 L162-164 一致）
            if status in ("FINISHED", "CANCELLED"):
                continue
            # P2：bigram 相关性门；无关 DORMANT/ACTIVE 不灌入
            if not _relevant_for_recall_clue(it, clue_bigrams):
                continue
            extras.append(it)
        if not extras:
            return res
        out = dict(res)
        out["eligibleItems"] = list(res.get("eligibleItems") or []) + extras
        out["injectedItemNames"] = have | {it["name"] for it in extras}
        out["v2RecallExtras"] = extras
        return out

    def __getattr__(self, name):          # read_cooldown / _repo 等透传
        return getattr(self._inner, name)


# ---------------------------------------------------------------- 接口
def init(assets_root, episode_meta, gateway, sandbox):
    h = base.init(assets_root, episode_meta, gateway, sandbox)
    h["ongoing"] = RecallSelector(h["ongoing"])
    return h


def _wrap_recall_into_knowledge(parent_text, recall_body_text):
    """把【回忆】段追加到父本知识段之后。父本已渲染 ongoing，这里只补 recall。"""
    if not recall_body_text:
        return parent_text, False
    section = RECALL_HEADER + recall_body_text + "\n"
    return parent_text.rstrip() + "\n\n" + section + "\n", True


def _truncate_chain_at_boundary(chain, cap):
    """单条 extras 的 chain 裁到 UTF-16 码元上限，落在句号/逗号/分号/顿号边界，
    不半截切断事实。C：先用 utf16_truncate_head 按码元裁（不切代理对），
    再回退到最近的中文分隔。"""
    if chain is None:
        return ""
    if rc.utf16_len(chain) <= cap:
        return chain
    cut = utf16_truncate_head(chain, cap)
    # 回退到最近的中文分隔（。；，、. ; ,），保留完整语义片段
    for sep in ("。", "；", "，", "、", ". ", "; ", ", "):
        i = cut.rfind(sep)
        if i >= 0 and rc.utf16_len(cut[:i]) >= cap // 2:
            return cut[:i + len(sep)]
    return cut.rstrip()


def _score_extra_relevance(item, clue_bigrams):
    if not clue_bigrams:
        return 0
    hay = (item.get("name") or "") + " " + (item.get("chain") or "")
    grams = _bigrams(hay) - _RECALL_STOPGRAMS
    return len(grams & clue_bigrams)


def _fit_new_context(sel, recall_body, clue_bigrams):
    """R3+C 联合预算分配：extras 与 recall_body 的**最终渲染新增段**
    （含【进行中事项】段头 + 【回忆】段头/提示语 + 分隔）合计 ≤ MAX_NEW_CONTEXT_CHARS
    码元——单位统一 UTF-16，截断用 utf16_truncate_* 不切代理对。
    步骤：
      1) 先算固定开销：body 非空 → 回忆段头 RECALL_HEADER + 分隔；
         extras 存在 → 进行中事项段头。这些先占预算，剩余给正文。
      2) extras 按相关性 desc 排序，数量硬顶 MAX_ONGOING_EXTRA_LINES，
         单条 chain ≤ MAX_EXTRA_CHAIN_CHARS（UTF-16 码元），逐条累加不越界才保留。
      3) recall body 用剩余预算 utf16_truncate_tail（保最新块尾）。
      4) 返回 (裁剪后 sel, body, extras_kept, extras_dropped,
              body_len_final, extras_rendered_len, new_segment_est_utf16)。
    new_segment_est_utf16 是"本轮相对父本新增"的渲染段估算（段头+分隔+body+extras 行），
    run_turn 落进 notes，测试据它对齐 MAX_NEW_CONTEXT_CHARS（不再混用 2200 容差）。"""
    extras = list(sel.get("v2RecallExtras") or [])
    if not extras and not recall_body:
        return sel, "", 0, 0, 0, 0, 0
    # 1) 固定段头开销（UTF-16 码元）
    recall_overhead = (rc.utf16_len(RECALL_HEADER) + _RECALL_SEP_U) if recall_body else 0
    ongoing_overhead = (rc.utf16_len(_ONGOING_HEADER_LITERAL) + 1) if extras else 0
    content_budget = max(0, MAX_NEW_CONTEXT_CHARS - recall_overhead - ongoing_overhead)
    # 2) extras：相关性排序 + 数量硬顶 + 逐条 utf16 计量
    scored = sorted(
        [(it, _score_extra_relevance(it, clue_bigrams)) for it in extras],
        key=lambda kv: (-kv[1], kv[0].get("name", "")))
    scored = scored[:MAX_ONGOING_EXTRA_LINES]
    kept, kept_lines_len = [], 0
    dropped = len(extras) - len(scored)
    for it, _score in scored:
        chain = _truncate_chain_at_boundary(it.get("chain", ""), MAX_EXTRA_CHAIN_CHARS)
        line = "%s | %s | %s" % (it.get("name", ""), it.get("status", ""), chain)
        add = rc.utf16_len(line) + 1                   # 换行分隔
        if kept_lines_len + add > content_budget:
            dropped += 1
            continue
        kept.append({**it, "chain": chain})
        kept_lines_len += add
    # 3) recall body 用剩余预算，UTF-16 码元裁尾（不切代理对）
    body_budget = max(0, content_budget - kept_lines_len)
    body = recall_body or ""
    if rc.utf16_len(body) > body_budget:
        cut = utf16_truncate_tail(body, body_budget) if body_budget > 0 else ""
        if cut and "# [" in cut:
            cut = cut[cut.find("# ["):]
        body = cut
    body_len_final = rc.utf16_len(body)
    # 4) 构造新 sel（不影响父本 ongoing 已渲染的 items）
    new_sel = dict(sel)
    if kept or dropped:
        kept_names = {it["name"] for it in kept}
        parent_eligibles = [it for it in (sel.get("eligibleItems") or [])
                            if it not in extras]
        new_eligibles = parent_eligibles + kept
        parent_names = {it["name"] for it in parent_eligibles}
        new_sel["eligibleItems"] = new_eligibles
        new_sel["injectedItemNames"] = parent_names | kept_names
        new_sel["v2RecallExtras"] = kept
    # 若 body 被裁空但 recall_overhead 已预留，实际渲染段头也随之省去（无 body 不挂段）
    new_seg_est = (
        (recall_overhead if body else 0)
        + (ongoing_overhead if kept else 0)
        + body_len_final + kept_lines_len)
    return (new_sel, body, len(kept), dropped,
            body_len_final, kept_lines_len, new_seg_est)


def run_turn(h, ctx, gw):
    """v2 主链：
    1) 只在本轮为回忆轮且有归档召回时追加【回忆】段；其余完全走父本逻辑。
    2) ongoing 旁路在 selector 内以 recall_clue 相关性判定（不整库灌入）。
    3) R3 联合预算 `_fit_new_context` 分配 extras + recall body ≤ MAX_NEW_CONTEXT_CHARS；
       含段头开销预留、chain 单条上限、extras 数量硬顶、body 保最新块。
    4) restore 后若 wrapper 缺失则兜底重装（P4 防线，正常路径 restore 已显式重装）。"""
    inp = ctx.get("input") or {}
    clock = pm.clock_of(inp)
    now_ms = pm.parse_wall(clock)
    state = pm.read_all(h["storage"], h["kb"], now_ms)
    h["turn_count"] = state["turn_count"]
    seq = ctx.get("turn_index") or (state["turn_count"] + 1)
    messages = base._normalize_messages(inp, seq)
    scene = rc.infer_scene(messages)
    scene_block = rc.scene_block(scene)
    intent_cfg = base._intent_cfg_from_input(inp)
    intent_block = rc.build_intent_block(intent_cfg)
    note_block = rc.build_advisor_note_block(inp.get("note", ""))
    chat = rc.render_chat_transcript(messages)

    # 1. 归档召回（非回忆轮返回空）
    body = recall_body(h, messages, topic_label=state.get("topic") or "")

    # 2. ongoing 选择：把 recall_body 当 clue 传给候选 selector
    if not isinstance(h["ongoing"], RecallSelector):
        h["ongoing"] = RecallSelector(h["ongoing"])          # P4 兜底
    sel = h["ongoing"].select_for_injection(
        h["kb"], messages, h["turn_count"],
        reply_directive=None, effective_intent=intent_cfg,
        recall_clue=body)

    # 3. R3+C 联合预算分配（UTF-16 码元统一；extras 与 recall body 同时裁，不切代理对）
    clue_bigrams = _bigrams(body) - _RECALL_STOPGRAMS if body else set()
    sel, body, extras_kept, extras_dropped, body_len_final, extras_render_len, \
        new_seg_est = _fit_new_context(sel, body, clue_bigrams)

    # 4. 装配知识段（父本按 sel 渲染 ongoing，v2 补 recall 段）
    knowledge = base._knowledge_block(h, state, now_ms, messages, sel)
    knowledge, recall_fired = _wrap_recall_into_knowledge(knowledge, body)

    ts_block = base._timestamp_block(inp.get("clock"))
    prompt = rc.by_blocks(knowledge, scene_block, intent_block, note_block,
                          chat["header"], chat["body"], ts_block)

    images = [m["image"] for m in (inp.get("messages") or [])
              if isinstance(m, dict) and m.get("image")]
    res = gw.generate(h["system_prompt"], prompt, images=images or None, tag="main")
    reply = res["text"]

    # 5. 本轮提交（复用父本逻辑；不改写回）
    analysis = base._extract_analysis(reply) \
        if ctx.get("turn_type", "reply") == "reply" else None
    if analysis is None:
        note = "writeback=skipped(no_analysis)"
    else:
        done = pm.record_round(
            h["storage"], h["kb"],
            time_str=clock, now_ms=now_ms,
            frozen_messages=messages,
            alias_map=chat["sourceAliasMap"],
            analysis=analysis)
        h["turn_count"] = int(done.get("turn_count") or (h["turn_count"] + 1))
        note = ("writeback=ok recent=%s scene_facts=%s ongoing=%s "
                "topic=%s turn=%d"
                % (done.get("recent"), done.get("scene_facts"),
                   done.get("ongoing"), done.get("topic_set"), h["turn_count"]))
    marks = (("profile", "【懂得】"), ("lessons", "【记忆】"),
             ("moment", "【此刻】"), ("recent", "# 最近对话"),
             ("ongoing", "【进行中事项】"), ("scene", "【当前场景】"),
             ("chain", "场景状态链"), ("ts", "【当前时间】"))
    note += " prompt_has=" + ",".join(k for k, v in marks if v in prompt)
    note += " recall=" + ("injected" if recall_fired else "none")
    # new_seg_est = 本轮相对父本新增段的 UTF-16 码元估算（段头+分隔+body+extras 行），
    # 与 cap 同单位（1800），供测试对齐真实最终请求新增段（不再用 2200 混称）。
    note += (" newctx[kept=%d dropped=%d body=%d extras=%d new_seg_est=%d cap=%d]"
             % (extras_kept, extras_dropped, body_len_final, extras_render_len,
                new_seg_est, MAX_NEW_CONTEXT_CHARS))
    return {"reply": reply, "notes": note}


def snapshot(h):
    return base.snapshot(h)


def restore(sandbox, snap):
    """P4：base.restore 返回的 h 里 ongoing 是父本 ReferenceOngoingSelector；
    显式重装 RecallSelector 保证候选行为跨 snapshot→restore 一致。"""
    h = base.restore(sandbox, snap)
    if not isinstance(h.get("ongoing"), RecallSelector):
        h["ongoing"] = RecallSelector(h["ongoing"])
    return h


def set_memory_access(h, enabled):
    return base.set_memory_access(h, enabled)


def init_state(assets_root, episode_meta):
    return base.init_state(assets_root, episode_meta)


def build_prompts(assets_root, case_input, state):
    return base.build_prompts(assets_root, case_input, state)


def update_state(state, case_input, parsed_output, raw_reply):
    return base.update_state(state, case_input, parsed_output, raw_reply)
