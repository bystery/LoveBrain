# -*- coding: utf-8 -*-
r"""tools/test_positive_capability_stories_p1.py — 包B第4项 P1 离线机械自检（零网络）。

不调用 127.0.0.1:17770、不读写除本次交付两份 JSON 之外的任何路径。逐项 PASS + 汇总 real_model_calls=0。

断言（严格按任务）：
 1) 6 stories × 8 positions = 48
 2) families 覆盖按故事分组：P001/P002 含 主动转场+钩子；P003/P004 含 反撩；P005/P006 含 深聊+价值观
 3) 每条故事：ME 真实发送 ≥1、反信号 ≥1、收尾 ≥1
 4) 每条故事：intent enabled + adjusted + closed 三态齐全
 5) 每条故事：低信息/平淡轮 ≥1
 6) 没有任何卡带 required_reply_phrase（值必须为 null）
 7) 卡的 allowed_behaviors 里没有本次场地/路线/天气/座位的具体断言（关键字扫描）
 8) initial_kb 每个值都是标量字符串（不是列表/字典——列表会让 importer 硬失败）
 9) 时间戳全部可解析；每轮 clock >= 窗口内最后一条消息 ts；跨轮 clock 单调不减
10) 相对时间词（明天/下周二/这周末/下周…）解析到 clock 之后的真实日期
11) split=dev_exposed、content_revision=p1-20261025 全卡一致
12) 未复用旧题库母题关键词（livehouse/耳塞/修电脑/妹妹答辩/爬山/凤凰山/咖啡改期/小林/读书会/后摇）
13) 卡 allowed 至少给 2 种不同措辞的合格动作（范围感）
14) 每张卡 families 非空、evidence 非空、failure 简短可核
"""
import json
import os
import re
import sys
from datetime import datetime, timedelta

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EP_PATH = os.path.join(ROOT, "benchmark", "positive_capability_20261025", "01_EPISODES_P1.json")
CARD_PATH = os.path.join(ROOT, "benchmark", "positive_capability_20261025", "02_GRADING_CARDS_P1.json")

TS_FMT = "%Y-%m-%d %H:%M"
CN_WEEKDAY = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
CN_WEEKDAY_ALT = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
WEEKDAY_MAP = {}
for i, name in enumerate(CN_WEEKDAY):
    WEEKDAY_MAP[name] = i          # 周一=0..周日=6 (Python weekday)
for i, name in enumerate(CN_WEEKDAY_ALT):
    WEEKDAY_MAP[name] = i

REAL_MODEL_CALLS = 0  # 本文件绝不发起任何网络/模型调用
RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    print(("PASS" if ok else "FAIL") + " | " + name + (" | " + detail if detail and not ok else ""))


# ─────────────────────────────────────────────────────────────────────────────
# 加载
# ─────────────────────────────────────────────────────────────────────────────
with open(EP_PATH, "r", encoding="utf-8") as f:
    EPISODES = json.load(f)
with open(CARD_PATH, "r", encoding="utf-8") as f:
    CARDS = json.load(f)


# ─────────────────────────────────────────────────────────────────────────────
# 1) 6 × 8 = 48
# ─────────────────────────────────────────────────────────────────────────────
check("stories_count=6", len(EPISODES) == 6, "实得 %d" % len(EPISODES))
per_story_len = {e["episode_id"]: len(e["turns"]) for e in EPISODES}
check("each_story_8_turns", all(v == 8 for v in per_story_len.values()),
      str(per_story_len))
check("positions_total_48", sum(per_story_len.values()) == 48,
      "实得 %d" % sum(per_story_len.values()))


# ─────────────────────────────────────────────────────────────────────────────
# 2) 主题族按故事分组覆盖
# ─────────────────────────────────────────────────────────────────────────────
fams_by_ep = {}
for c in CARDS:
    fams_by_ep.setdefault(c["episode_id"], set()).update(c["families"])

theme_alloc = {
    "P001": {"主动转场", "钩子"},
    "P002": {"主动转场", "钩子"},
    "P003": {"反撩"},
    "P004": {"反撩"},
    "P005": {"深聊", "价值观"},
    "P006": {"深聊", "价值观"},
}
for ep, req in theme_alloc.items():
    got = fams_by_ep.get(ep, set())
    # 允许带括号补充（如"深聊（轻）"），做子串匹配
    hit = all(any(r in f for f in got) for r in req)
    check("theme_families_%s covers %s" % (ep, sorted(req)), hit,
          "got=%s" % sorted(got))


# ─────────────────────────────────────────────────────────────────────────────
# 3) 每条故事：ME 真实发送 ≥1；反信号 ≥1；收尾 ≥1
# ─────────────────────────────────────────────────────────────────────────────
cards_by_ep = {}
for c in CARDS:
    cards_by_ep.setdefault(c["episode_id"], []).append(c)

for e in EPISODES:
    ep = e["episode_id"]
    me_msgs = 0
    for t in e["turns"]:
        for m in t["input"]["messages"]:
            if m["role"] == "ME":
                me_msgs += 1
    check("ME_history_%s>=1" % ep, me_msgs >= 1, "ME msgs=%d" % me_msgs)

    got_fams = fams_by_ep.get(ep, set())
    has_counter = any("反信号" in f for f in got_fams)
    has_closing = any("收尾" in f for f in got_fams)
    check("counter_signal_%s" % ep, has_counter)
    check("closing_turn_%s" % ep, has_closing)


# ─────────────────────────────────────────────────────────────────────────────
# 4) intent enabled/adjusted/closed 三态齐全
# 5) 低信息/平淡轮 ≥1
# ─────────────────────────────────────────────────────────────────────────────
for e in EPISODES:
    ep = e["episode_id"]
    states = set()
    for t in e["turns"]:
        it = (t["input"].get("intent") or {})
        if not it:
            continue
        st = it.get("status")
        en = it.get("enabled")
        if st == "ACTIVE" and en is True:
            states.add("enabled")
        elif st == "ADJUSTED" and en is True:
            states.add("adjusted")
        elif st == "CLOSED" and en is False:
            states.add("closed")
    check("intent_three_states_%s" % ep, {"enabled", "adjusted", "closed"} <= states,
          "got=%s" % sorted(states))

    got_fams = fams_by_ep.get(ep, set())
    check("low_info_turn_%s" % ep, any("低信息" in f for f in got_fams))


# ─────────────────────────────────────────────────────────────────────────────
# 6) required_reply_phrase 全部为 null
# ─────────────────────────────────────────────────────────────────────────────
bad_phrase = [c["case_id"] for c in CARDS
              if "required_reply_phrase" not in c or c["required_reply_phrase"] is not None]
check("required_reply_phrase_all_null", not bad_phrase, str(bad_phrase[:5]))


# ─────────────────────────────────────────────────────────────────────────────
# 7) allowed_behaviors 不含"本次场地/路线/天气/座位"等具体断言
# 关键词表：音量/座位/排队/路线远近/耗时/天气——只扫 allowed（failure 允许出现"打包票"）
# 允许出现"承认没把握/坦承不确定"这类元描述，不算具体断言。
# ─────────────────────────────────────────────────────────────────────────────
BANNED_ASSERT = [
    # 场地音量/环境/座位
    r"很安静", r"不吵", r"不闹", r"有座位", r"很多座位", r"有空位",
    r"不挤", r"位置很多", r"不用排队",
    # 路线距离/耗时
    r"\d+\s*分钟就到", r"半小时就到", r"很近", r"不远", r"顺着.{0,6}分钟",
    # 天气
    r"天气.{0,4}晴", r"不下雨", r"晴天", r"不会下雨", r"多云", r"会下雨",
    r"温度适宜",
    # 现场/设施既成事实
    r"已经订好", r"已经占位", r"已经留好位置",
    # "她爸真说了 X" 冒领
    r"她爸(就)?这(么|样)说的", r"她妈(就)?这(么|样)说的",
    # 现场人数/场地既有事实
    r"人不多", r"人不吵",
]
BANNED_RE = [re.compile(p) for p in BANNED_ASSERT]

viol = []
for c in CARDS:
    for ab in c["allowed_behaviors"]:
        for rx in BANNED_RE:
            if rx.search(ab):
                viol.append((c["case_id"], ab, rx.pattern))
                break
check("allowed_behaviors_no_this_time_assertions", not viol,
      str(viol[:3]))


# ─────────────────────────────────────────────────────────────────────────────
# 8) initial_kb 每个值必须是标量字符串（list/dict 会让 importer 硬失败）
# ─────────────────────────────────────────────────────────────────────────────
bad_kb = []
for e in EPISODES:
    for k, v in (e.get("initial_kb") or {}).items():
        if not isinstance(v, str):
            bad_kb.append((e["episode_id"], k, type(v).__name__))
check("initial_kb_scalar_string", not bad_kb, str(bad_kb[:5]))


# ─────────────────────────────────────────────────────────────────────────────
# 9) 时间戳全部可解析；每轮 clock ≥ 窗口最后消息 ts；clock 跨轮单调
# ─────────────────────────────────────────────────────────────────────────────
def parse_ts(s):
    try:
        return datetime.strptime(s, TS_FMT)
    except Exception:
        return None

ts_fail = []
clock_monotonic_fail = []
for e in EPISODES:
    prev_clock = None
    for t in e["turns"]:
        clock = parse_ts(t["input"]["clock"])
        if clock is None:
            ts_fail.append((t["case_id"], "clock", t["input"]["clock"]))
            continue
        last_msg_ts = None
        for m in t["input"]["messages"]:
            dt = parse_ts(m["ts"])
            if dt is None:
                ts_fail.append((t["case_id"], "msg_ts", m["ts"]))
                continue
            if last_msg_ts is None:
                last_msg_ts = dt
            else:
                if dt < last_msg_ts:
                    ts_fail.append((t["case_id"], "msg_monotonic", m["ts"]))
                last_msg_ts = dt
        if last_msg_ts is not None and clock < last_msg_ts:
            ts_fail.append((t["case_id"], "clock_before_last_msg",
                            "%s < %s" % (clock, last_msg_ts)))
        if prev_clock is not None and clock < prev_clock:
            clock_monotonic_fail.append((t["case_id"], str(clock), str(prev_clock)))
        prev_clock = clock

check("timestamps_parse_and_clock_ge_last_msg", not ts_fail, str(ts_fail[:3]))
check("clock_monotonic_per_story", not clock_monotonic_fail, str(clock_monotonic_fail[:3]))


# ─────────────────────────────────────────────────────────────────────────────
# 10) 相对时间词解析到 clock 之后的真实日期
# 支持：明天/后天/今晚/大后天/下周X/本周X/这周X/下周/周末/这周末/本周末/下周末/星期X
# 只扫 HER/ME 消息文本里的相对词；解析规则：
#   明天 = clock_date + 1 day
#   后天 = clock_date + 2 day
#   今晚/晚上 = clock_date (>=clock 只要不早于当天 00:00；用日期粒度即视为"不越昨天")
#   大后天 = +3
#   下周X / 星期X（无"这/本"前缀）：默认视为下一个未来出现的周X（>clock_date）
#   这周X / 本周X：本周对应 weekday；若 <=clock_date，视为已发生（不触发本检查）—— 只校验"能解析出合法日期"
#   下周末：下一个周六（>clock_date）
#   这周末/本周末：本周内的周六（若已过则回退到下一个周六）
# 检查：所有匹配到的相对词都能算出一个 clock_date 之后的具体日期。
# ─────────────────────────────────────────────────────────────────────────────
def next_weekday(from_date, target_wd):
    d = from_date + timedelta(days=1)
    while d.weekday() != target_wd:
        d += timedelta(days=1)
    return d

def coming_weekend(from_date):
    # 最近的周六（含今天）
    d = from_date
    while d.weekday() != 5:
        d += timedelta(days=1)
    return d

def next_weekend(from_date):
    this = coming_weekend(from_date)
    # 下一个周六（这周六之后的那个周六）
    return this + timedelta(days=7)

REL_PATTERNS = [
    (r"明天", lambda cd, c: cd + timedelta(days=1)),
    (r"后天", lambda cd, c: cd + timedelta(days=2)),
    (r"大后天", lambda cd, c: cd + timedelta(days=3)),
    (r"今晚", lambda cd, c: cd),
    (r"下周[一二三四五六日天]", lambda cd, c: next_weekday(cd, {"一":0,"二":1,"三":2,"四":3,"五":4,"六":5,"日":6,"天":6}[re.search(r"下周([一二三四五六日天])", c).group(1)])),
    (r"星期[一二三四五六日天]", lambda cd, c: next_weekday(cd, {"一":0,"二":1,"三":2,"四":3,"五":4,"六":5,"日":6,"天":6}[re.search(r"星期([一二三四五六日天])", c).group(1)])),
    (r"这周末", lambda cd, c: coming_weekend(cd) if coming_weekend(cd) >= cd else next_weekend(cd)),
    (r"本周末", lambda cd, c: coming_weekend(cd)),
    (r"下周末", lambda cd, c: next_weekend(cd)),
    (r"下周", lambda cd, c: cd + timedelta(days=(7 - cd.weekday()) + 0)),  # 下周一
]

def find_rel_dates(text, clock_dt):
    """返回 (word, resolved_date) 列表；无法解析的词忽略。"""
    out = []
    for pat, fn in REL_PATTERNS:
        for m in re.finditer(pat, text):
            word = m.group(0)
            # 排除"下周末"被"下周"重复命中：如果已被更具体词包含就跳过
            already = any((m.start() >= s and m.end() <= e) for s, e, _ in [(mm.start(), mm.end(), None) for mm in re.finditer(r"下周末|下周[一二三四五六日天]", text)])
            if already and pat == r"下周":
                continue
            try:
                d = fn(clock_dt.date(), word)
            except Exception:
                continue
            out.append((word, d))
    return out

rel_fail = []
rel_total = 0
for e in EPISODES:
    for t in e["turns"]:
        clock_dt = parse_ts(t["input"]["clock"])
        for m in t["input"]["messages"]:
            hits = find_rel_dates(m["text"], clock_dt)
            for word, d in hits:
                rel_total += 1
                if d <= clock_dt.date():
                    rel_fail.append((t["case_id"], word, str(d), "not later than clock %s" % clock_dt.date()))
                # 日期本身必须真实（timedelta 已保证）
check("relative_date_words_resolve_future", not rel_fail,
      "checked %d hits; fail=%s" % (rel_total, rel_fail[:3]))


# ─────────────────────────────────────────────────────────────────────────────
# 11) split=dev_exposed & content_revision=p1-20261025 全卡一致
# ─────────────────────────────────────────────────────────────────────────────
bad_split = [c["case_id"] for c in CARDS if c.get("split") != "dev_exposed"]
bad_rev = [c["case_id"] for c in CARDS if c.get("content_revision") != "p1-20261025"]
check("split_dev_exposed_all", not bad_split, str(bad_split[:5]))
check("content_revision_p1_20261025_all", not bad_rev, str(bad_rev[:5]))


# ─────────────────────────────────────────────────────────────────────────────
# 12) 不复用旧题库母题关键词
# ─────────────────────────────────────────────────────────────────────────────
LEGACY = [
    "livehouse", "Livehouse", "LIVEHOUSE", "后摇", "耳塞",
    "修电脑", "重装", "妹妹答辩", "答辩",
    "读书会", "小林", "爬山", "凤凰山", "咖啡改期", "咖啡",
]
legacy_hits = []
for e in EPISODES:
    blob = json.dumps(e, ensure_ascii=False)
    for k in LEGACY:
        if k in blob:
            legacy_hits.append((e["episode_id"], k))
check("no_legacy_bank_motifs", not legacy_hits, str(legacy_hits[:5]))


# ─────────────────────────────────────────────────────────────────────────────
# 13) allowed_behaviors ≥ 2 项（范围/不同措辞的合格动作）
# ─────────────────────────────────────────────────────────────────────────────
thin_allowed = [c["case_id"] for c in CARDS if len(c["allowed_behaviors"]) < 2]
check("allowed_range_at_least_2", not thin_allowed, str(thin_allowed[:5]))


# ─────────────────────────────────────────────────────────────────────────────
# 14) families 非空、evidence 非空、failure 简短可核（<=5 项）
# ─────────────────────────────────────────────────────────────────────────────
bad_fam = [c["case_id"] for c in CARDS if not c.get("families")]
bad_ev = [c["case_id"] for c in CARDS if not c.get("evidence")]
fat_fail = [c["case_id"] for c in CARDS if len(c.get("failure_conditions", [])) > 5]
check("families_nonempty", not bad_fam, str(bad_fam[:5]))
check("evidence_nonempty", not bad_ev, str(bad_ev[:5]))
check("failure_conditions_narrow", not fat_fail, str(fat_fail[:5]))


# ─────────────────────────────────────────────────────────────────────────────
# 汇总
# ─────────────────────────────────────────────────────────────────────────────
n_fail = sum(1 for _, ok, _ in RESULTS if not ok)
print("")
print("SUMMARY checks=%d pass=%d fail=%d real_model_calls=%d"
      % (len(RESULTS), len(RESULTS) - n_fail, n_fail, REAL_MODEL_CALLS))
sys.exit(0 if n_fail == 0 else 1)
