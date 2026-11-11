# -*- coding: utf-8 -*-
r"""tools/build_anchor_set_20261028.py — S2 反馈锚点集（12 个单位置锚点，零调用）。

为什么需要（不是再扩题库）：目标层/反馈适应必须在"当前目标真实生效机会"上单独测。
固定连续故事里只有少数位点带 ACTIVE 目标，均分会把机制效果摊薄（V4 §3C 明确要求
锚点单列统计）。本集=4 个生活领域 × 3 类反馈（积极 / 含糊 / 明确忙或疲惫）=12 锚点，
每个 1 个请求位、含充分历史与当前有效目标。

诚实边界（写进产物，不靠嘴说）：
  · 本集由**同一 worker 撰写**（AI 合成、无真实用户素材、作者已读过），
    是"公开开发锚点集"，**不是**未见独立集，也不声称独立封存。
  · 反馈三类不都写成"退让不回"：积极锚点要求正向推进被承接，含糊锚点允许继续试探
    但不把害羞当同意，忙/疲惫锚点里只有 2 个设 may_decline=true（她已明确收尾），
    其余仍期待自然关心或改期——避免把"情绪让位"训成一律沉默。
  · 素材与 S1 使用的 E024/E027/E031/E034 不重叠（夜跑复健/开放麦/备考/猫手术，
    人物、时间、事件均另建），不复刻原对话。

产物：benchmark/stories_v2/anchors_20261028/AKxx/{episode,reference,timeline,exposure}.json
      + audit.md + ANCHORS_MANIFEST.json，并把 episode 逐字拷到 benchmark/episodes/AKxx.json。
校验：D:\anaconda\python.exe tools\validate_story_v2.py --stage anchors_20261028 \
      --turns 1 --count 12 --manifest-name ANCHORS_MANIFEST.json --out-name ANCHORS_VALIDATION.json
"""
import datetime
import hashlib
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "benchmark", "stories_v2", "anchors_20261028")
EPDIR = os.path.join(ROOT, "benchmark", "episodes")
AUTHOR = "ZCode main worker-AI (synthetic)"
NOW = "2026-10-28"


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def dump(p, obj):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)


def M(role, ts, text):
    return {"role": role, "text": text, "ts": ts}


def atom(aid, subject, relation, value, status, source, valid_time=None,
         negation=False, required=False):
    return {"atom_id": aid, "subject": subject, "relation": relation,
            "value": value, "valid_time": valid_time, "status": status,
            "negation": negation, "source": source, "required_this_turn": required}


def msg_src(case_id, idx, quote):
    return {"kind": "message", "case_id": case_id, "message_id": "m%d" % idx,
            "quote": quote}


def kb_src(key, quote):
    return {"kind": "initial_kb", "key": key, "quote": quote}


# ── 12 锚点 ─────────────────────────────────────────────────────────────────
A = []

# D1 夜跑复健（她上周崴脚；我同事午饭认识）
D1_KB = {
    "me": "互联网公司做后端，去年体检血脂高，晚点跑三步一喘；同事午饭时认识她。",
    "her": "社区卫生服务中心做护士，夜班多；上周下坡崴了右脚，肿了三天。",
    "warmth": "午饭搭子三个月，聊得来；她说过一起夜跑，我一直没开口。",
    "style": "短句、实事；她懂医，别装内行教她养伤。",
    "stage": "认识三个月的午饭搭子，没单独约过。",
    "lessons": "她脚伤是硬约束；时间地点要落到具体，别停在'改天'。",
}
A.append({
    "sid": "AK01", "domain": "夜跑复健", "feedback": "positive", "kb": D1_KB,
    "persona": {"me": "31岁，后端工程师，血脂偏高，想运动但没长性。",
                "her": "29岁，社区护士，夜班多，下坡崴过右脚。"},
    "clock": "2026-12-03 21:12",
    "msgs": [M("ME", "2026-12-01 12:40", "你脚还肿不 我这周三晚点有空 想试试慢的"),
             M("HER", "2026-12-01 12:48", "消差不多了 医生说可以快走 先别跑"),
             M("ME", "2026-12-01 12:52", "那就快走 绕小区外圈两圈 不追速度"),
             M("HER", "2026-12-03 21:10", "周五晚上我下班早 六点行不 我从医院那边过来")],
    "may_decline": False,
    "intent": {"text": "把周五晚的复健快走落成具体时间地点", "status": "ACTIVE",
               "enabled": True},
    "note": "",
    "card": {
        "current_action": "她给了具体日子（周五六点）还说要从医院那边过来——把时间和集合点当场定死，别再'到时候再说'",
        "acceptable_actions": ["确认周五六点+定集合点", "确认+问她要不要先慢走一圈再决定距离",
                               "确认+提自己带水或计时"],
        "applicable_dimensions": ["goal", "time_state", "initiative", "interaction"],
        "atoms": [atom("AK01_A01", "她", "约束", "医生说只能快走还不能跑", "asserted",
                       msg_src("AK01_T01", 1, "医生说可以快走 先别跑"), "2026-12-01 12:48"),
                  atom("AK01_A02", "她", "时间", "周五晚六点下班早，可从医院那边过来",
                       "asserted", msg_src("AK01_T01", 3, "周五晚上我下班早 六点行不"),
                       "2026-12-03 21:10")],
        "private_guidance": "她在主动把自己的行程交出来；这一步的要害是确定性，不是热情。",
        "unknowns": ["集合点定在哪一侧", "她六点下班后多久能到"],
        "protected": ["周五晚上我下班早"],
        "forbidden": ["不要把距离或配速说成已经定好", "不要提'跑步'当作已经安排（医生只允许快走）"],
    }})
A.append({
    "sid": "AK02", "domain": "夜跑复健", "feedback": "hesitant", "kb": D1_KB,
    "persona": {"me": "31岁，后端工程师，血脂偏高，想运动但没长性。",
                "her": "29岁，社区护士，夜班多，下坡崴过右脚。"},
    "clock": "2026-12-05 13:06",
    "msgs": [M("ME", "2026-12-05 12:58", "周六早上七点 河边走一圈 不上强度"),
             M("HER", "2026-12-05 13:04", "周六我本来想补觉…而且脚还是不太敢用力")],
    "may_decline": False,
    "intent": {"text": "把周六早上的复健快走落成具体时间地点", "status": "ACTIVE",
               "enabled": True},
    "note": "她夜班后睡眠是刚需；之前也提过一次'先别跑'",
    "card": {
        "current_action": "她的犹豫是两层（要补觉+不敢用力）——承认这两层并把目标改成更轻或改期，不是继续说服",
        "acceptable_actions": ["改到午后或周一再约", "改成更短的平地散步并说随时停",
                               "让她先休息，本周取消并留明确下一步"],
        "applicable_dimensions": ["goal", "emotion_relation", "boundary", "interaction"],
        "atoms": [atom("AK02_A01", "她", "顾虑", "周六想补觉且脚还不敢用力", "asserted",
                       msg_src("AK02_T01", 1, "周六我本来想补觉"), "2026-12-05 13:04"),
                  atom("AK02_A02", "她", "约束", "医生说只能快走不能跑", "asserted",
                       kb_src("her", "上周下坡崴了右脚"), None),
                  atom("AK02_A03", "她", "既往", "此前已说过先别跑", "asserted",
                       kb_src("lessons", "她脚伤是硬约束"), None)],
        "private_guidance": "含糊不是拒绝，但把'补觉+不敢用力'推平为'那就周六七点别迟到'是越权。",
        "unknowns": ["她到底想改期还是想减量", "她想睡到几点"],
        "protected": ["脚还是不太敢用力"],
        "forbidden": ["不要把她说的'想补觉'当成客套继续推进",
                      "不要说'运动对谁都好'这类替她下结论的话"],
    }})
A.append({
    "sid": "AK03", "domain": "夜跑复健", "feedback": "busy", "kb": D1_KB,
    "persona": {"me": "31岁，后端工程师，血脂偏高，想运动但没长性。",
                "her": "29岁，社区护士，夜班多，下坡崴过右脚。"},
    "clock": "2026-12-07 19:22",
    "msgs": [M("ME", "2026-12-07 19:10", "明晚六点老地方 我先绕一圈等你"),
             M("HER", "2026-12-07 19:20", "今晚院里临时加疫苗门诊 我八点才走 明天也排了班")],
    "may_decline": False,
    "intent": {"text": "明晚六点复健快走", "status": "ACTIVE", "enabled": True},
    "note": "",
    "card": {
        "current_action": "她连今晚都被占用——目标当轮让位：不追问改哪天、不施压，先让她顾上班里的事，留一个不逼人的开口",
        "acceptable_actions": ["让她先忙+说我这周都空随时讲", "只回一句轻的关心",
                               "取消并明确不用她回复"],
        "applicable_dimensions": ["goal", "emotion_relation", "boundary", "interaction"],
        "atoms": [atom("AK03_A01", "她", "状态", "今晚临时加疫苗门诊八点才走，明天也排班",
                       "asserted", msg_src("AK03_T01", 1, "今晚院里临时加疫苗门诊"),
                       "2026-12-07 19:20"),
                  atom("AK03_A02", "双方", "原定计划", "明晚六点老地方复健快走", "asserted",
                       msg_src("AK03_T01", 0, "明晚六点老地方"), "2026-12-07 19:10")],
        "private_guidance": "她的忙碌是排班不是态度；此时最忌讳追问'那到底哪天有空'。",
        "unknowns": ["她这周还有没有空档"],
        "protected": ["今晚院里临时加疫苗门诊"],
        "forbidden": ["不要说她总是忙（把一次排班推成人品结论）",
                      "不要把'等你有空'写成需要她给承诺"],
    }})

# D2 开放麦（她写的段子第一次上台）
D2_KB = {
    "me": "做消防设备的，平时爱去鼓楼那家小酒吧看开放麦；不会写东西但敢起哄。",
    "her": "幼儿园保育员，晚上写段子；第一次上台排在下周六，怕冷场。",
    "warmth": "楼上楼下邻居两年，互相见过家长；她紧张时会来敲我门。",
    "style": "损但托底；不问她段子写没写好，除非她主动讲。",
    "stage": "熟到能互相损的邻居，没单独看过演出。",
    "lessons": "她要的是观众不是评委；别提'你应该怎么改'。",
}
A.append({
    "sid": "AK04", "domain": "开放麦", "feedback": "positive", "kb": D2_KB,
    "persona": {"me": "33岁，消防设备维保，爱看开放麦。",
                "her": "28岁，幼儿园保育员，周六第一次上台。"},
    "clock": "2026-12-09 22:34",
    "msgs": [M("HER", "2026-12-09 22:20", "老板给我排进周六了 五分钟 我有点飘"),
             M("ME", "2026-12-09 22:24", "该飘 那张卡我帮你留着 到时候我坐第一排"),
             M("HER", "2026-12-09 22:32", "你能来我就不飘了 周六八点 你带水别带话")],
    "may_decline": False,
    "intent": {"text": "周六八点去鼓楼看她第一次开放麦", "status": "ACTIVE", "enabled": True},
    "note": "",
    "card": {
        "current_action": "约定已经成形（周六八点）——按她的条件接住：带水别带话，别临时加建议或拉别人",
        "acceptable_actions": ["确认周六八点+只带水", "确认+说坐第一排不互动",
                               "确认+问她要不要留票给同事"],
        "applicable_dimensions": ["goal", "time_state", "interaction", "boundary"],
        "atoms": [atom("AK04_A01", "她", "事件", "周六被排进开放麦，五分钟首台", "asserted",
                       msg_src("AK04_T01", 0, "老板给我排进周六了"), "2026-12-09 22:20"),
                  atom("AK04_A02", "她", "条件", "要我带水别带话", "asserted",
                       msg_src("AK04_T01", 2, "你带水别带话"), "2026-12-09 22:32"),
                  atom("AK04_A03", "双方", "约定", "周六八点鼓楼", "confirmed",
                       msg_src("AK04_T01", 2, "周六八点"), "2026-12-09 22:32")],
        "private_guidance": "'带水别带话'是她把需求说清楚了；照着做就是接住，讲道理是越界。",
        "unknowns": ["是否要提前到场", "她同事去不去"],
        "protected": ["你带水别带话"],
        "forbidden": ["不要临场提出帮她改段子", "不要把'坐第一排'升级成要上台互动"],
    }})
A.append({
    "sid": "AK05", "domain": "开放麦", "feedback": "hesitant", "kb": D2_KB,
    "persona": {"me": "33岁，消防设备维保，爱看开放麦。",
                "her": "28岁，幼儿园保育员，周六第一次上台。"},
    "clock": "2026-12-11 23:02",
    "msgs": [M("ME", "2026-12-11 22:50", "明晚八点我票都取好了"),
             M("HER", "2026-12-11 23:00", "我明天上台前想找人过一遍 但又怕你听完觉得不好笑")],
    "may_decline": False,
    "intent": {"text": "周六八点到场看她首台并坐第一排", "status": "ACTIVE", "enabled": True},
    "note": "她怕冷场，也怕被熟人评判",
    "card": {
        "current_action": "她把需求（想对词）和顾虑（怕我觉得不好笑）同时摆出来——先答她想不想我听，再讲自己立场，不空夸也不催",
        "acceptable_actions": ["答应听并声明不点评", "问她想要哪种反馈再决定",
                               "答应+说听完只回一句有没有笑点"],
        "applicable_dimensions": ["goal", "emotion_relation", "boundary", "interaction"],
        "atoms": [atom("AK05_A01", "她", "意愿", "上台前想找人对一遍", "asserted",
                       msg_src("AK05_T01", 1, "我明天上台前想找人过一遍"), "2026-12-11 23:00"),
                  atom("AK05_A02", "她", "顾虑", "怕我听完觉得不好笑", "asserted",
                       msg_src("AK05_T01", 1, "怕你听完觉得不好笑"), "2026-12-11 23:00"),
                  atom("AK05_A03", "双方", "既定", "周六八点我已取票", "confirmed",
                       msg_src("AK05_T01", 0, "明晚八点我票都取好了"), "2026-12-11 22:50")],
        "private_guidance": "含糊处在于她要的是安全感的确认；把'那我不听'或'肯定好笑'任一极端甩回去都算失职。",
        "unknowns": ["她想要的是彩排还是打气"],
        "protected": ["又怕你听完觉得不好笑"],
        "forbidden": ["不要保证'一定好笑'", "不要主动要求逐段点评"],
    }})
A.append({
    "sid": "AK06", "domain": "开放麦", "feedback": "busy", "kb": D2_KB,
    "persona": {"me": "33岁，消防设备维保，爱看开放麦。",
                "her": "28岁，幼儿园保育员，周六第一次上台。"},
    "clock": "2026-12-12 18:12",
    "msgs": [M("ME", "2026-12-12 18:00", "明晚八点半我到 你几点到"),
             M("HER", "2026-12-12 18:10", "班里临时让我顶晚托 我得七点半才走 可能赶不上")],
    "may_decline": True,
    "intent": {"text": "周六晚到场看她首台", "status": "ACTIVE", "enabled": True},
    "note": "",
    "card": {
        "current_action": "她在算时间会不会错过开场——给可执行的方案（我去占座、她到场直接上台前热身），不让她在愧疚里做选择",
        "acceptable_actions": ["说我去占座她按自己时间到", "问要不要我帮她跟老板打招呼晚一分钟",
                               "只确认不用她管我"],
        "applicable_dimensions": ["goal", "time_state", "boundary", "interaction"],
        "atoms": [atom("AK06_A01", "她", "约束", "顶晚托七点半才能走，可能赶不上开场",
                       "asserted", msg_src("AK06_T01", 1, "我得七点半才走"), "2026-12-12 18:10"),
                  atom("AK06_A02", "双方", "既定", "明晚八点半演出", "confirmed",
                       msg_src("AK06_T01", 0, "明晚八点半我到"), "2026-12-12 18:00")],
        "private_guidance": "may_decline=true：她已经给出信息不需我做决定，只回一句'不用管我'也合法；但她没说取消，别擅自改成'那算了'。",
        "unknowns": ["她能否赶上自己的场次"],
        "protected": ["班里临时让我顶晚托"],
        "forbidden": ["不要说她肯定迟到", "不要把演出取消当成默认方案"],
    }})

# D3 备考（她在考社会工作者，报了两门）
D3_KB = {
    "me": "做社区网格员的，考第二年中过一门；习惯把她当同学而不是学生。",
    "her": "物业客服，报了两门证考，第一次模考三门里过了两门，怕半途而废。",
    "warmth": "球场边认识半年，一起背过题；她容易自责。",
    "style": "具体的事配具体的时间；不安慰式空话。",
    "stage": "一起备考的球友。",
    "lessons": "她说'可能太多'时是真在算时间，不是撒娇。",
}
A.append({
    "sid": "AK07", "domain": "备考", "feedback": "positive", "kb": D3_KB,
    "persona": {"me": "30岁，社区网格员，第二年考证。",
                "her": "27岁，物业客服，报了两门证考。"},
    "clock": "2026-12-14 21:46",
    "msgs": [M("HER", "2026-12-14 21:30", "模考过了两门 剩那门实务我最没底"),
             M("ME", "2026-12-14 21:36", "那门我去年也是靠刷真题 周六图书馆一起弄两小时"),
             M("HER", "2026-12-14 21:44", "行 周六下午我去 你带那本真题")],
    "may_decline": False,
    "intent": {"text": "周六下午图书馆一起攻实务那门两小时", "status": "ACTIVE",
               "enabled": True},
    "note": "",
    "card": {
        "current_action": "她答应并指定我带真题——把下午的具体时点、楼层座位和带什么讲清楚",
        "acceptable_actions": ["定下午两点+说带哪本", "定时间+问她要不要先看错题",
                               "确认+说两小时后散"],
        "applicable_dimensions": ["goal", "time_state", "memory_use", "interaction"],
        "atoms": [atom("AK07_A01", "她", "状态", "模考三门过两门，实务最没底", "asserted",
                       msg_src("AK07_T01", 0, "模考过了两门"), "2026-12-14 21:30"),
                  atom("AK07_A02", "她", "请求", "要我带那本真题", "asserted",
                       msg_src("AK07_T01", 2, "你带那本真题"), "2026-12-14 21:44"),
                  atom("AK07_A03", "我", "依据", "去年过实务靠刷真题", "asserted",
                       kb_src("me", "考第二年中过一门"), None)],
        "private_guidance": "'你带那本真题'是任务指派，必须回应在不在手上；把约定停在'周六见'就漏了。",
        "unknowns": ["真题册在不在家", "图书馆周六是否开放"],
        "protected": ["你带那本真题"],
        "forbidden": ["不要承诺真题一定能押中题", "不要把她'过两门'说成一定拿证"],
    }})
A.append({
    "sid": "AK08", "domain": "备考", "feedback": "hesitant", "kb": D3_KB,
    "persona": {"me": "30岁，社区网格员，第二年考证。",
                "her": "27岁，物业客服，报了两门证考。"},
    "clock": "2026-12-16 22:30",
    "msgs": [M("ME", "2026-12-16 22:18", "周六下午两小时 我给你留了座"),
             M("HER", "2026-12-16 22:28", "我想撤一门 报了俩根本啃不完 又怕撤了更不学")],
    "may_decline": False,
    "intent": {"text": "周六下午图书馆两小时攻实务", "status": "ACTIVE", "enabled": True},
    "note": "她容易自责，之前说过怕半途而废",
    "card": {
        "current_action": "她在认真算负荷并征求判断——给出我的实际看法+她需要权衡的具体变量，同时保留目标不空转；不能只说'你觉得呢'回抛",
        "acceptable_actions": ["给出自己怎么看（撤/不撤的理由）+问每周可投入几小时",
                               "建议先按一门排两周再看", "承认两门确实超量并改小目标"],
        "applicable_dimensions": ["goal", "factuality", "interaction", "emotion_relation"],
        "atoms": [atom("AK08_A01", "她", "倾向", "想撤一门但怕更不学", "asserted",
                       msg_src("AK08_T01", 1, "我想撤一门"), "2026-12-16 22:28"),
                  atom("AK08_A02", "她", "既往", "容易自责、怕半途而废", "asserted",
                       kb_src("lessons", "她说'可能太多'时是真在算时间"), None),
                  atom("AK08_A03", "双方", "原定", "周六下午两小时", "asserted",
                       msg_src("AK08_T01", 0, "周六下午两小时"), "2026-12-16 22:18")],
        "private_guidance": "'含糊'不等于不表态：她有真实问题要答案。回避给判断、把选择全推回给她，是没接住。",
        "unknowns": ["她每周实际能挤出几小时", "报名费是否可退"],
        "protected": ["报了俩根本啃不完"],
        "forbidden": ["不要说'你开心就好'这类不担责的话",
                      "不要替她断言'撤了肯定考不过'"],
    }})
A.append({
    "sid": "AK09", "domain": "备考", "feedback": "busy", "kb": D3_KB,
    "persona": {"me": "30岁，社区网格员，第二年考证。",
                "her": "27岁，物业客服，报了两门证考。"},
    "clock": "2026-12-19 23:58",
    "msgs": [M("ME", "2026-12-19 22:40", "还有两小时题 你要现在弄完还是留着明早"),
             M("HER", "2026-12-19 23:56", "我刚背到十二点 明天六点半起 先睡了 你别等")],
    "may_decline": True,
    "intent": {"text": "周六下午图书馆两小时攻实务", "status": "ACTIVE", "enabled": True},
    "note": "她说明天六点半起",
    "card": {
        "current_action": "她明确收尾（'先睡了 你别等'）——合法不回；若回就一句不要求应答的短收尾，别把两小时安排继续摊给她",
        "acceptable_actions": ["不回（她已收尾，合法）", "一句'睡吧 明早的题我留着'不再问",
                               "只把材料放好不说时间"],
        "applicable_dimensions": ["boundary", "interaction", "emotion_relation", "time_state"],
        "atoms": [atom("AK09_A01", "她", "状态", "背到十二点、明早六点半起", "asserted",
                       msg_src("AK09_T01", 1, "明天六点半起"), "2026-12-19 23:56"),
                  atom("AK09_A02", "她", "收尾", "先睡了，别等", "asserted",
                       msg_src("AK09_T01", 1, "你别等"), "2026-12-19 23:56")],
        "private_guidance": "此处不回不是冷淡，是尊重她已给出的收尾；但要避免把'不回'写成对所有疲惫场景的默认。",
        "unknowns": ["她要不要我把题留着"],
        "protected": ["先睡了 你别等"],
        "forbidden": ["不要追问'那明天几点开始'", "不要把她早睡说成不自律"],
    }})

# D4 猫手术（她的猫周六要做绝育，答应陪去）
D4_KB = {
    "me": "自由插画师，在家办公；她猫去年在我画室躲过雷。",
    "her": "做宠物食品电商，独自租房；猫两岁公的，医院约在周六上午九点。",
    "warmth": "画室邻居一年多，互相代取快递；她怕去医院排队。",
    "style": "少说大道理，做具体那件事。",
    "stage": "能互相帮忙搬东西的邻居。",
    "lessons": "她爸妈周末会从老家来，时间容易冲突。",
}
A.append({
    "sid": "AK10", "domain": "猫手术", "feedback": "positive", "kb": D4_KB,
    "persona": {"me": "34岁，自由插画师，在家办公。",
                "her": "28岁，宠物食品电商，独自租房养一只两岁公猫。"},
    "clock": "2026-12-21 20:40",
    "msgs": [M("HER", "2026-12-21 20:22", "周六九点手术确认了 我抱猫笼挤地铁真的不行"),
             M("ME", "2026-12-21 20:28", "那我去吾悦门口接你 六点四十我车在楼下"),
             M("HER", "2026-12-21 20:38", "可以 我就把猫笼先放你画室")],
    "may_decline": False,
    "intent": {"text": "周六送她带猫去医院并接她回家", "status": "ACTIVE", "enabled": True},
    "note": "",
    "card": {
        "current_action": "链条已成形（笼先放画室→我接→九点手术）——把周六早上谁几点拿笼、停车点讲清楚",
        "acceptable_actions": ["确认+约定拿笼时间", "确认+说提前十分钟到",
                               "确认+问她要不要留她备用钥匙"],
        "applicable_dimensions": ["goal", "time_state", "memory_use", "interaction"],
        "atoms": [atom("AK10_A01", "她", "既定", "手术周六九点已确认", "confirmed",
                       msg_src("AK10_T01", 0, "周六九点手术确认了"), "2026-12-21 20:22"),
                  atom("AK10_A02", "她", "限制", "抱猫笼无法挤地铁", "asserted",
                       msg_src("AK10_T01", 0, "我抱猫笼挤地铁真的不行"), "2026-12-21 20:22"),
                  atom("AK10_A03", "双方", "安排", "猫笼先放我画室", "confirmed",
                       msg_src("AK10_T01", 2, "我就把猫笼先放你画室"), "2026-12-21 20:38")],
        "private_guidance": "她给了具体的分工；漏掉'笼什么时候过去'就是承诺执行不完整。",
        "unknowns": ["术后要不要直接回家还是去她租处", "医院停车点"],
        "protected": ["我就把猫笼先放你画室"],
        "forbidden": ["不要说'猫没事放心吧'这类替医生做的保证",
                      "不要把接人时间说成已经确定（六点四十是我提议）"],
    }})
A.append({
    "sid": "AK11", "domain": "猫手术", "feedback": "hesitant", "kb": D4_KB,
    "persona": {"me": "34岁，自由插画师，在家办公。",
                "her": "28岁，宠物食品电商，独自租房养一只两岁公猫。"},
    "clock": "2026-12-23 21:14",
    "msgs": [M("ME", "2026-12-23 21:02", "明早六点四十吾悦门口接你"),
             M("HER", "2026-12-23 21:12", "我妈说明天要来 我得先去车站 手术要不改到下周")],
    "may_decline": False,
    "intent": {"text": "周六上午陪她带猫做手术", "status": "ACTIVE", "enabled": True},
    "note": "她妈周末来是提前说过的事",
    "card": {
        "current_action": "她提出改期但没确定——先把改期的可执行项接住（问医院能否改、我下周同步空），不让她在两件重要的事之间选一个",
        "acceptable_actions": ["答应改期+提醒尽快打医院电话", "问她要不要我先接她去车站再回来处理猫",
                               "确认下周某天并说明我那天也行"],
        "applicable_dimensions": ["goal", "time_state", "emotion_relation", "boundary"],
        "atoms": [atom("AK11_A01", "第三人", "事件", "她妈明天要来，她得先去车站", "asserted",
                       msg_src("AK11_T01", 1, "我妈说明天要来"), "2026-12-23 21:12"),
                  atom("AK11_A02", "双方", "原定", "明早六点四十吾悦接她做手术", "asserted",
                       msg_src("AK11_T01", 0, "明早六点四十吾悦门口接你"), "2026-12-23 21:02"),
                  atom("AK11_A03", "第三人", "既往", "她父母周末从老家来是常事", "asserted",
                       kb_src("lessons", "她爸妈周末会从老家来"), None)],
        "private_guidance": "'要不改到下周'是征求不是通知；直接改时间或坚持原计划，两种都算没问。",
        "unknowns": ["下周医院是否约得上", "她妈待几天"],
        "protected": ["手术要不改到下周"],
        "forbidden": ["不要说'你妈重要还是猫重要'这类逼她表态的话",
                      "不要声称医院一定能改到下周"],
    }})
A.append({
    "sid": "AK12", "domain": "猫手术", "feedback": "busy", "kb": D4_KB,
    "persona": {"me": "34岁，自由插画师，在家办公。",
                "her": "28岁，宠物食品电商，独自租房养一只两岁公猫。"},
    "clock": "2026-12-28 22:50",
    "msgs": [M("HER", "2026-12-28 22:26", "手术拖到周一下午了 我还得请假 现在头都是疼的"),
             M("ME", "2026-12-28 22:32", "请假要写什么理由不 需要我帮你顶半天"),
             M("HER", "2026-12-28 22:48", "先这样吧 结果出来我再说 谢了")],
    "may_decline": True,
    "intent": {"text": "陪她带猫完成手术并接她回家", "status": "ACTIVE", "enabled": True},
    "note": "她已经明确说结果出来再说",
    "card": {
        "current_action": "她明确收尾并设了下次通知点（结果出来再说）——合法不回或一句不再要求应答的收尾；不要继续追问手术细节或再提帮请假",
        "acceptable_actions": ["不回（她已收尾，合法）", "一句'好 我周一下午空着'不再问",
                               "只说一句不用她回"],
        "applicable_dimensions": ["boundary", "emotion_relation", "interaction", "time_state"],
        "atoms": [atom("AK12_A01", "她", "状态", "手术改到周一下午、要请假、头疼", "asserted",
                       msg_src("AK12_T01", 0, "手术拖到周一下午了"), "2026-12-28 22:26"),
                  atom("AK12_A02", "她", "边界", "结果出来再说，先这样", "asserted",
                       msg_src("AK12_T01", 2, "结果出来我再说"), "2026-12-28 22:48"),
                  atom("AK12_A03", "我", "提议", "提出帮她顶半天假", "asserted",
                       msg_src("AK12_T01", 1, "需要我帮你顶半天"), "2026-12-28 22:32")],
        "private_guidance": "'谢了'加'先这样吧'是收尾信号；此时追问细节就是把互动继续占住。不回在此合法，但不能推论成'她烦我'。",
        "unknowns": ["周一下午是否还需要接送"],
        "protected": ["结果出来我再说"],
        "forbidden": ["不要说她不想聊了", "不要重复追问请假或手术细节"],
    }})


def build():
    manifest = []
    for a in A:
        sid = a["sid"]
        cid = "%s_T01" % sid
        ep = {"episode_id": sid, "persona": a["persona"], "initial_kb": a["kb"],
              "turns": [{"case_id": cid, "turn_type": "reply",
                         "input": {"messages": a["msgs"], "clock": a["clock"],
                                   "may_decline": a["may_decline"],
                                   "intent": a["intent"]},
                         "gold": {}}]}
        if a.get("note"):
            ep["turns"][0]["input"]["note"] = a["note"]
        ref = {"kind": "story_reference_v2", "story_id": sid,
               "turns": {cid: {
                   "case_id": cid,
                   "current_action": a["card"]["current_action"],
                   "acceptable_actions": a["card"]["acceptable_actions"],
                   "applicable_dimensions": a["card"]["applicable_dimensions"],
                   "required_memory_atoms": a["card"]["atoms"],
                   "private_guidance": a["card"]["private_guidance"],
                   "unknowns": a["card"]["unknowns"],
                   "protected_expressions": a["card"]["protected"],
                   "forbidden_factual_claims": a["card"]["forbidden"],
                   "may_decline": a["may_decline"],
                   "intent": a["intent"]}}}
        tl = {"kind": "story_timeline_v2", "story_id": sid,
              "turn_clocks": [{"case_id": cid, "clock": a["clock"]}],
              "message_index": [{"case_id": cid, "message_id": "m%d" % i,
                                 "speaker": m["role"], "quote": m["text"],
                                 "ts": m["ts"]} for i, m in enumerate(a["msgs"])],
              "events": [{"event_id": "%s_E1" % sid, "case_id": cid,
                          "summary": "%s｜反馈=%s｜意图=%s" % (
                              a["domain"], a["feedback"], a["intent"]["text"]),
                          "source_turn": cid}]}
        sdir = os.path.join(BASE, sid)
        dump(os.path.join(sdir, "episode.json"), ep)
        dump(os.path.join(sdir, "reference.json"), ref)
        dump(os.path.join(sdir, "timeline.json"), tl)
        runner_rel = "benchmark/episodes/%s.json" % sid
        shutil.copyfile(os.path.join(sdir, "episode.json"),
                        os.path.join(EPDIR, sid + ".json"))
        files = {"episode.json": os.path.join(sdir, "episode.json"),
                 "reference.json": os.path.join(sdir, "reference.json"),
                 "timeline.json": os.path.join(sdir, "timeline.json"),
                 "audit.md": os.path.join(sdir, "audit.md")}
        with open(files["audit.md"], "w", encoding="utf-8") as f:
            f.write("\n".join([
                "# %s 审计（%s｜反馈=%s）" % (sid, a["domain"], a["feedback"]),
                "",
                "## 0. 性质与暴露",
                "AI 合成虚构人物，无真实用户素材；作者即本 worker，dev_exposed=true；",
                "不是独立封存集，不用于宣称泛化。",
                "",
                "## 1. 生活动机",
                "领域=%s；本轮她给的是%s反馈；当前有效目标=%s" % (
                    a["domain"], a["feedback"], a["intent"]["text"]),
                "",
                "## 2. 连续性与回扣",
                "锚点为单位置（1 turn）；连续性由本回合内的历史消息承担，",
                "前史见 message_index，不引入外部轮次。",
                "",
                "## 3. ME 历史与边界",
                "ME 文本来自作者冻结的历史脚本，非候选生成；候选输出不写回后续轮（Phase0 边界）。",
                "",
                "## 4. no_reply / 媒体局限",
                "may_decline=%s；无媒体输入；无多模态假设。" % a["may_decline"],
                "",
                "## 5. 参考卡质量",
                "current_action 指明本轮要害；acceptable_actions 给%s个可接受方向（允许多答案）；"
                "原子%s条，逐条回指消息或 initial_kb 原文；protected/forbidden 各给边界。" % (
                    len(a["card"]["acceptable_actions"]), len(a["card"]["atoms"])),
                "",
                "## 6. 已知风险",
                "同作者撰写，存在作者偏好；程序分覆盖不到语义；",
                "本锚点只测反馈/目标一类机会，不代表全部能力。",
                "",
                "## 7. 出口检查",
                "机械验证通过（tools/validate_story_v2.py --stage anchors_20261028 "
                "--turns 1 --count 12）；语义审查由 worker 逐位复核。", ""]))
        exp = {"kind": "anchor_exposure_v1", "story_id": sid,
               "title": "%s·%s" % (a["domain"], a["feedback"]),
               "themes": [a["domain"], a["feedback"], "当前目标反馈适应"],
               "author": AUTHOR, "author_type": "AI", "real_user_material": False,
               "source_notes": "虚构人物与对话；不含真实用户素材；不复刻 S1 四故事素材。",
               "readers": ["author"], "independent_review": False,
               "review_note": "同 worker 自检；公开开发锚点，不称独立封存。",
               "created_at": NOW, "frozen_at": None,
               "status": "dev_candidate_anchor_20261028", "experiments_used": [],
               "dev_exposed": True, "validation_claim": False, "turns": 1,
               "may_decline_turns": [cid] if a["may_decline"] else [],
               "hashes": {n: sha(p) for n, p in files.items() if os.path.isfile(p)},
               "runner_copy": runner_rel,
               "notes": "S2 反馈锚点集（4 领域×积极/含糊/忙或疲惫）；单位置。"}
        dump(os.path.join(sdir, "exposure.json"), exp)
        manifest.append({"story_id": sid,
                         "files": {n: sha(p) for n, p in files.items()
                                   if os.path.isfile(p)}})
    doc = {"kind": "anchors_manifest_v1", "stage": "anchors_20261028",
           "count": len(manifest), "turn_per_story": 1,
           "domains": sorted({a["domain"] for a in A}),
           "feedback_types": sorted({a["feedback"] for a in A}),
           "may_decline_count": sum(1 for a in A if a["may_decline"]),
           "intent_all_active": all(a["intent"]["status"] == "ACTIVE" and
                                    a["intent"]["enabled"] for a in A),
           "built_at": datetime.datetime.now().isoformat(timespec="seconds"),
           "independence": "同作者公开开发集，非独立封存",
           "stories": manifest}
    dump(os.path.join(BASE, "ANCHORS_MANIFEST.json"), doc)
    print("anchors=%d may_decline=%d domains=%s feedback=%s" % (
        len(manifest), doc["may_decline_count"], doc["domains"],
        doc["feedback_types"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(build())
