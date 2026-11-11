# candidates/fact_ledger_v2/mechanism/ledger_rules.py
#
# 事实账本的规则化抽取与更正判定（纯函数、零模型调用）。
# 规格来源：refs/experiment_protocol_20261023/global_harness_program_20261030/
#           W12_FACT_LEDGER_DESIGN_20261101.md §2.4–§2.6。
# v2 delta（FLREG_VERDICT_20261101 归因 H-主观句错账）：新增 R0 主观句排除层，
# 在 R1–R6 之前：无硬锚（数字/日期/时点/金额均无）且命中推测/情绪宣泄/纯意愿
# 语言学信号的 clause 不入账。判断依据是语言学信号，不是内容审查——含硬锚的
# 句子即使带情绪词也保留（如"气死了 约的不是九点"）。
# supersede 判定纯函数（_SUPERSEDE_CUES/_norm_cn/_bigram_ov/_digits/_digit_conflict）
# 逐字移植自 candidates/action_policy_r7_supersede/mechanism/mechanism.py
# （r7 依赖 r2/r1 全链，不可整体 import，故按设计 §2.6 复制并注明出处）。
# EXTERNAL_PARTY 词表逐字复制自 arena/lb_assembly.py L141——值复制而非 import：
# 候选宿主 staged 树不保证 arena 在 sys.path（隔离边界见 arena/isolation/candidate_host.py）。
import re

EXTERNAL_PARTY = ("领导", "老板", "公司", "同事", "项目", "客户", "甲方", "部门", "hr")

CLAUSE_SPLIT_RE = re.compile(r"[，。！？；\n]")
CLAUSE_MAX_CU = 60          # 设计 §2.4：clause ≤60 UTF-16 码元，超长弃

_CN_NUM = "零一二两三四五六七八九十百千"
_NUM = r"(?:[0-9]+(?:\.[0-9]+)?|[%s]+)" % _CN_NUM

# R1 数字+单位：数字后 2 字内出现量词/单位
_R1_RE = re.compile(_NUM + r".{0,2}(箱|元|块|米|号|层|分钟|人|件|栋|单元)")
# R2 日期/时点（设计词表 + 明早/今晚/凌晨等常用口语变体，量词 点 与 R1 的量词按 R2 先判）
_R2_DAY_RE = re.compile(r"今天|明天|后天|大后天|明早|明晚|今晚|昨晚|"
                        r"周[一二三四五六日天]|星期[一二三四五六日天]|"
                        r"[0-9]{1,2}月[0-9]{1,2}[日号]?")
_R2_CLOCK_RE = re.compile(r"[0-9]{1,2}点半?|[%s]+点半?|上午|下午|晚上|凌晨|早上|中午" % _CN_NUM)
# R3 承诺/约定/分工（设计词表 + LRE048 团购/退款域高频词）
_R3_RE = re.compile(r"说好|答应|约定|负责|记得|一定|保证|赔|违约金|合同|钥匙|押金|"
                    r"退|差价|规矩|立了|先在|归我|归你|归还")
# R4 变更/作废
_R4_RE = re.compile(r"改到|改成|改为|换到|换成|延期|延至|作废|取消|不[^，。！？；\n]{0,6}了")
_R4_CANCEL_RE = re.compile(r"作废|取消|不用了|不需要了|不用来了|不来了|作罢")

# R6 事件/状态动词表（覆盖审计迭代补模板：设计 §2.4 "覆盖率 <80% 先离线补模板"）。
# 取自 LRE060/LRE048 未覆盖 GT 的可泛化事件类（到家/到货/付款/退单/分工/群规等）；
# 纯主观感受与比喻类（"心里有点虚"/"阳台成园"）仍不入账，按设计 §6 诚实报未覆盖。
_R6_RE = re.compile(
    r"到家|到货|开机|开团|开课|付款|付钱|付了|退款|转账|下单|关了|失踪|找到了|"
    r"记岔|记错|公示|发群里|群公告|白纸黑字|新规|写明白|写明|保密|账单|外卖|"
    r"晚饭|午饭|早饭|吃饭|请客|饭局|掌勺|打下手|总指挥|账房先生|审计|入伙|验收|"
    r"邻居|拼桌|图书馆|押题|客服|研友|团长|辛苦费|代付|截图|对账|券限|看到券|"
    r"布置|提醒|压在|贴了|等我|等等|等他|等她|不回我|回我了|介绍给|打个分|"
    r"不确定|花土|姓")

_SHARED_RE = re.compile(r"我们|一起|咱")
_Q_END_RE = re.compile(r"[?？]\s*$")

# R0 主观句信号词表（v2 新增，逐项对应 FLREG 归因三类）。
# a 推测/不确定语气；b 情绪宣泄主导；c 纯意愿/假设（如果…就 按同 clause 内搭配判）。
_R0_SPECULATION = ("可能", "大概", "也许", "好像", "是不是", "能不能", "要不要",
                   "说不定", "万一", "应该吧", "吧？", "觉得", "感觉", "我猜")
_R0_EMOTION = ("气死", "烦死", "火大", "崩溃", "麻了", "无语", "裂开", "好家伙", "离谱")
_R0_WISH_RE = re.compile(r"要是|真想|恨不得|如果[^，。！？；\n]{0,20}就")


def _has_hard_anchor(c):
    """硬锚：数字（含中文数字）/日期/时点/金额任一存在（R1/R2/R5 判定素材）。"""
    return bool(re.search(_NUM, c) or _R2_DAY_RE.search(c) or _R2_CLOCK_RE.search(c))


def is_subjective_excluded(c):
    """R0 排除层（v2）：仅当 clause 无硬锚时生效；命中推测/情绪/意愿信号 → 不入账。"""
    if _has_hard_anchor(c):
        return False
    if any(w in c for w in _R0_SPECULATION):
        return True
    if any(w in c for w in _R0_EMOTION):
        return True
    return bool(_R0_WISH_RE.search(c))

# 移植自 r7（逐字）
_SUPERSEDE_CUES = ("实际是", "其实", "看错", "看岔", "改成", "更正", "换成", "改为",
                   "延期", "延至", "作废", "改到", "改为", "不对", "写错", "记错")

_PUNCT_SET = set(chr(65292) + chr(12290) + chr(65281) + chr(65311) + chr(12289)
                 + chr(65307) + chr(65306) + chr(8220) + chr(8221) + chr(12300)
                 + chr(12301) + chr(65288) + chr(65289) + chr(40) + chr(41)
                 + chr(12298) + chr(12299) + chr(60) + chr(62) + chr(32) + chr(9)
                 + chr(10) + chr(13) + chr(45) + chr(8212) + chr(8230) + chr(183) + chr(43))


def _norm_cn(t):                      # 移植自 r7
    return "".join(ch for ch in (t or "") if ch not in _PUNCT_SET)


def _bigram_ov(a, b):                 # 移植自 r7
    an, bn = _norm_cn(a), _norm_cn(b)
    if len(an) < 2 or len(bn) < 2:
        return 0.0
    ag = {an[i:i + 2] for i in range(len(an) - 1)}
    bg = {bn[i:i + 2] for i in range(len(bn) - 1)}
    return len(ag & bg) / max(1, len(ag))


def _digits(t):                       # 移植自 r7
    return set(re.findall(r"\d+(?:\.\d+)?", re.sub(r"\s+", "", t or "")))


def _digit_conflict(old_t, new_t):    # 移植自 r7
    od, nd = _digits(old_t), _digits(new_t)
    if not od:
        return False
    return not od.issubset(nd)


def utf16_len(s):
    return len((s or "").encode("utf-16-le")) // 2


def split_clauses(text):
    """设计 §2.4：按 [，。！？；\\n] 切 clause，去首尾空白，≤60 码元以内才返回。"""
    out = []
    for c in CLAUSE_SPLIT_RE.split(text or ""):
        c = c.strip()
        if c and utf16_len(c) <= CLAUSE_MAX_CU:
            out.append(c)
    return out


def classify_clause(c):
    """规则优先级 R4 > R3 > R2 > R1 > R6 > R5 → (kind, rule)；无信号返回 (None, None)。"""
    if _R4_RE.search(c):
        return "event", "R4"
    if _R3_RE.search(c):
        return "promise", "R3"
    if _R2_DAY_RE.search(c):
        return "date", "R2"
    if _R2_CLOCK_RE.search(c):
        return "time", "R2"
    m = _R1_RE.search(c)
    if m:
        return ("amount" if m.group(1) in ("元", "块") else "number"), "R1"
    if _R6_RE.search(c):
        return "event", "R6"
    if re.search(_NUM, c):
        return "other", "R5"
    return None, None


def _subject_of(text, role, kind):
    if kind == "promise" and _SHARED_RE.search(text):
        return "SHARED"
    if any(w in (text or "").lower() for w in EXTERNAL_PARTY):
        return "THIRD"
    if role == "HER":
        return "HER"
    if role == "ME":
        return "ME"
    return "UNKNOWN"


_WEEKDAY = {"一": 0, "二": 1, "三": 2, "四": 3, "五": 4, "六": 5, "日": 6, "天": 6}
_REL_DAYS = (("大后天", 3), ("后天", 2), ("明天", 1), ("明早", 1), ("明晚", 1),
             ("今晚", 0), ("今天", 0))


def resolve_valid_time(text, ts):
    """R2/R4 命中时从消息 ts + 相对词解析目标日期（YYYY-MM-DD）；解析不出留 ""。"""
    try:
        import datetime
        base = datetime.datetime.strptime((ts or "")[:10], "%Y-%m-%d").date()
    except Exception:
        return ""
    for w, d in _REL_DAYS:
        if w in text:
            return (base + datetime.timedelta(days=d)).isoformat()
    m = re.search(r"周([一二三四五六日天])|星期([一二三四五六日天])", text)
    if m:
        wd = _WEEKDAY[m.group(1) or m.group(2)]
        delta = (wd - base.weekday()) % 7
        return (base + datetime.timedelta(days=delta)).isoformat()
    m = re.search(r"([0-9]{1,2})月([0-9]{1,2})[日号]?", text)
    if m:
        try:
            cand = base.replace(month=int(m.group(1)), day=int(m.group(2)))
        except Exception:
            return ""
        return cand.isoformat() if cand >= base else ""
    return ""          # 仅时钟时点（X点半）无日词 → 日期不可解析


def extract_facts(text, role, ts, capture_turn, uid, start_id):
    """对一条真实消息跑规则抽取，返回 (entries, next_id)。

    边界（设计 §6）：subject 判不准 → UNKNOWN；valid_time 解析不出 → ""。
    fact 文本一律 clause 级逐字原文，不概括不改写。"""
    entries, n = [], start_id
    seen = set()
    for c in split_clauses(text):
        if is_subjective_excluded(c):
            continue                       # R0：无硬锚的主观句不入账（v2）
        kind, rule = classify_clause(c)
        if kind is None:
            continue
        if _Q_END_RE.search(c) and not _R3_RE.search(c):
            continue                       # 问句且无 R3 信号不入账
        key = _norm_cn(c)
        if key in seen:
            continue
        seen.add(key)
        n += 1
        entries.append({
            "fact_id": "F%03d" % n,
            "text": c,                     # 逐字原文
            "subject": _subject_of(c, role, kind),
            "kind": kind,
            "digits": sorted(_digits(c)),
            "capture_turn": capture_turn,
            "ts": ts or "",
            "valid_time": resolve_valid_time(c, ts) if kind in ("date", "time", "event") else "",
            "src_uids": [uid],
            "status": "asserted",
            "superseded_by": "",
            "origin": "message",
            "_rule": rule,
        })
    return entries, n


def update_pass(ledger, new_entries, clock, updates):
    """设计 §2.6：新 asserted 条目更正/作废旧条目。返回 (n_superseded, n_cancelled)。

    superseded：新条目命中更正线索 + overlap ≥0.42（同主体 ≥0.30）+ 数字冲突；
    cancelled：R4 取消线索 + overlap；seed 条目（origin=seed）永不降级；
    superseded/cancelled 条目保留在账本内可审计，仅不再注入。"""
    n_sup = n_can = 0
    for nf in new_entries:
        nt = nf.get("text") or ""
        cancel = bool(_R4_CANCEL_RE.search(nt))
        if not cancel and not any(c in nt for c in _SUPERSEDE_CUES):
            continue
        for of in ledger:
            if of is nf or of.get("status") != "asserted" or of.get("origin") == "seed":
                continue
            if (of.get("capture_turn") or 0) >= (nf.get("capture_turn") or 0):
                continue
            ov = _bigram_ov(nt, of.get("text") or "")
            same_subject = (nf.get("subject") == of.get("subject")
                            and (of.get("subject") or "UNKNOWN") != "UNKNOWN")
            if not (ov >= 0.42 or (ov >= 0.30 and same_subject)):
                continue
            if cancel:
                of["status"] = "cancelled"
                action = "cancel"
                n_can += 1
            else:
                if not _digit_conflict(of.get("text") or "", nt):
                    continue
                of["status"] = "superseded"
                of["superseded_by"] = nf.get("fact_id") or ""
                action = "auto_supersede"
                n_sup += 1
            updates.append({"fact_id": of.get("fact_id"), "action": action,
                            "text": nt, "old_text": of.get("text"), "ts": clock})
            if len(updates) > 100:
                del updates[:-100]
            break                          # 一条新事实只降级一条旧事实（继承 r7）
    return n_sup, n_can
