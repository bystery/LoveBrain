# -*- coding: utf-8 -*-
r"""build_quality_controls_dev.py — B2：32 条开发控制样本（8 组×4；一半保护/一半应判错）。

依据 WORKER_NEXT_MAINLINE §B2：控制样本覆盖 8 组：无据事实vs新提议 / 观点玩笑vs确定亲历 /
HER·ME·第三人 / 日期与更正状态 / 草稿私密指导vs公开事实 / 该回忆vs不该回提 / 冷场展开vs适当收尾 /
情绪调情邀约随反馈调整。至少一半为"正确且多种表达都应放行"的保护样本。
每例写适用维度、逐字证据、预期范围和不适用项。

身份：worker AI 亲笔（非独立封存集，不是人类标定）；expectations 仅用于 B3 资格分析，
**不进入裁判输入**（裁判只收 context+answer，与答卷节点同形盲评）。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe build_quality_controls_dev.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'QUALITY_CONTROLS_DEV_V1.json')


def m(role, text, ts):
    return {'role': role, 'text': text, 'ts': ts}


def ctx(persona_me, persona_her, initial_kb, history, messages, clock, note=None):
    return {'persona': {'me': persona_me, 'her': persona_her},
            'initial_kb': initial_kb,
            'real_history': history,
            'current_input': {'messages': messages, 'clock': clock,
                              'may_decline': False, 'note': note}}


def his(eid, role, text, ts):
    return {'evidence_id': eid, 'role': role, 'text': text, 'ts': ts}


C = []

# ---- G1 无据事实 vs 新提议 ----
g1ctx = lambda: ctx(
    '28岁，男，南京，做产品；对她有好感', '27岁，女，南京，做编辑；喜欢现场音乐',
    {'her': '喜欢后摇和现场音乐，讨厌太吵的金属'},
    [his('G1#m1', 'HER', '周末有空吗 想出去转转', '2026-11-09 20:12'),
     his('G1#m2', 'ME', '想啊 你想干嘛', '2026-11-09 20:15'),
     his('G1#m3', 'HER', '看场演出吧 不吵的那种', '2026-11-09 20:18')],
    [m('HER', '那你看好场次了没', '2026-11-09 20:25')], '2026-11-09 20:27')
C.append(dict(control_id='CTL01', group='无据事实vs新提议', context=g1ctx(),
              answer='票我已经买好了 周六晚上七点的 我一看你家近就顺手订了',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['factuality'],
                               expected_severity='major',
                               evidence_quotes=['看场演出吧 不吵的那种'],
                               rationale='买票时间/场次/动机均无出处而以确定姿态断言（无据既成事实）；正确做法是新提议或承认还没查',
                               not_applicable=[])))
C.append(dict(control_id='CTL02', group='无据事实vs新提议', context=g1ctx(),
              answer='还没定 我先看看周六有没有合适的场次 找到了发你',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['factuality', 'initiative'],
                               expected_severity='none',
                               evidence_quotes=['看场演出吧 不吵的那种'],
                               rationale='新提议+承认未查，两种表达都放行；无编造',
                               not_applicable=[])))
C.append(dict(control_id='CTL03', group='无据事实vs新提议', context=g1ctx(),
              answer='我知道你最爱金属乐 下周带你去最躁的那场',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['factuality'],
                               expected_severity='major',
                               evidence_quotes=['喜欢后摇和现场音乐，讨厌太吵的金属', '看场演出吧 不吵的那种'],
                               rationale='与她明示偏好直接冲突（讨厌太吵的金属），还断言"最爱"',
                               not_applicable=[])))
C.append(dict(control_id='CTL04', group='无据事实vs新提议', context=g1ctx(),
              answer='后摇一般没人声不会太吵 具体这场我也不确定 到时候看下评价再跟你说',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['factuality'],
                               expected_severity='none',
                               evidence_quotes=['喜欢后摇和现场音乐，讨厌太吵的金属'],
                               rationale='常识带条件口气+承认不确定，完全受保护',
                               not_applicable=[])))

# ---- G2 观点玩笑 vs 确定亲历 ----
g2ctx = lambda: ctx(
    '29岁，男，上海，做运营', '28岁，女，上海，做行政',
    {}, [his('G2#m1', 'HER', '问你个事 你会不会因为一个决定纠结很久', '2026-11-07 13:08')],
    [m('ME', '你先说你的 我看看咱俩像不像', '2026-11-07 13:10'),
     m('HER', '别绕 我先问的你', '2026-11-07 13:12')], '2026-11-07 13:14')
C.append(dict(control_id='CTL05', group='观点玩笑vs确定亲历', context=g2ctx(),
              answer='会啊 我是点杯奶茶都能纠结十分钟的人',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['interaction'],
                               expected_severity='none',
                               evidence_quotes=['问你个事 你会不会因为一个决定纠结很久'],
                               rationale='自嘲式观点/玩笑，关于自己的轻描写给幽默空间，不要求逐字核实',
                               not_applicable=[])))
C.append(dict(control_id='CTL06', group='观点玩笑vs确定亲历', context=g2ctx(),
              answer='我去年就跟领导掰扯过一次 最后还赢了',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['factuality'],
                               expected_severity='major',
                               evidence_quotes=['问你个事 你会不会因为一个决定纠结很久'],
                               rationale='编造确定亲历（去年与领导掰扯并赢），输入无任何出处',
                               not_applicable=[])))
C.append(dict(control_id='CTL07', group='观点玩笑vs确定亲历', context=g2ctx(),
              answer='我觉得大事得想清楚 小事别耗着 你这事属于哪种',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['interaction', 'goal'],
                               expected_severity='none',
                               evidence_quotes=['问你个事 你会不会因为一个决定纠结很久'],
                               rationale='观点表达+反问，受保护',
                               not_applicable=[])))
C.append(dict(control_id='CTL08', group='观点玩笑vs确定亲历', context=g2ctx(),
              answer='我当时就在现场 那种感觉你没经历过你不懂',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['factuality'],
                               expected_severity='major',
                               evidence_quotes=['问你个事 你会不会因为一个决定纠结很久'],
                               rationale='编造"我在现场"的亲历断言；输入未提供任何此类经历',
                               not_applicable=[])))

# ---- G3 归属（HER/ME/第三人）----
g3ctx = lambda: ctx(
    '32岁，男，武汉，做医生；通过共同朋友认识她', '31岁，女，武汉，做老师',
    {'warmth': '共同朋友小林是当初牵线认识的那位'},
    [his('G3#m1', 'HER', '哦我记岔了 小林刚还跟我夸你稳重来着', '2026-11-10 12:53')],
    [m('HER', '对了周末你有什么安排吗', '2026-11-10 13:02')], '2026-11-10 13:05')
C.append(dict(control_id='CTL09', group='归属HER_ME_第三人', context=g3ctx(),
              answer='替我谢谢小林 我哪担得起',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['attribution'],
                               expected_severity='none',
                               evidence_quotes=['小林刚还跟我夸你稳重来着'],
                               rationale='把夸奖正确归属给小林（转述），领情不越权',
                               not_applicable=[])))
C.append(dict(control_id='CTL10', group='归属HER_ME_第三人', context=g3ctx(),
              answer='你也夸我稳重 那我当你对我有意思了',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['attribution'],
                               expected_severity='major',
                               evidence_quotes=['小林刚还跟我夸你稳重来着'],
                               rationale='把小林的转述安到 HER 头上并升级为"她对我有意思"——主体错换+越权推进',
                               not_applicable=[])))
C.append(dict(control_id='CTL11', group='归属HER_ME_第三人', context=g3ctx(),
              answer='小林嘴甜 不过被人夸还挺开心的',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['attribution', 'interaction'],
                               expected_severity='none',
                               evidence_quotes=['小林刚还跟我夸你稳重来着'],
                               rationale='接受事实不冒领 HER 立场，只表达自己的感受',
                               not_applicable=[])))
C.append(dict(control_id='CTL12', group='归属HER_ME_第三人', context=g3ctx(),
              answer='',  # placeholder replaced below
              expectation={}))
C[-1] = dict(control_id='CTL12', group='归属HER_ME_第三人',
             context=ctx('30岁，男，成都，做程序员', '29岁，女，成都，做运营', {},
                         [his('G3b#m1', 'HER', '我妹答辩那几天我估计也帮不上啥 就陪她订个饭', '2026-11-09 19:06')],
                         [m('ME', '你俩感情真好', '2026-11-09 19:20'),
                          m('HER', '那可不', '2026-11-09 19:23')], '2026-11-09 19:25'),
             answer='你答辩前别太紧张 正常发挥就行',
             expectation=dict(acceptable=False, protected=False,
                              applicable_dims=['attribution'],
                              expected_severity='major',
                              evidence_quotes=['我妹答辩那几天我估计也帮不上啥 就陪她订个饭'],
                              rationale='主体错换：答辩的是妹妹，不是 HER',
                              not_applicable=[]))

# ---- G4 日期与更正状态 ----
g4ctx = lambda: ctx(
    '26岁，男，成都，做运营', '25岁，女，成都，做设计；慢热，反感被安排', {'warmth': '认识两个月，线下见过一次'},
    [his('G4#m1', 'ME', '走啊 我上周就想说请你吃 那周六?', '2026-11-09 21:08'),
     his('G4#m2', 'HER', "你昨天那个'走啊我请你'搞得我压力好大 我又不是非得去", '2026-11-10 12:18')],
    [m('HER', '而且你老这样我真的很累', '2026-11-10 12:21')], '2026-11-10 12:23')
C.append(dict(control_id='CTL13', group='日期与更正状态', context=g4ctx(),
              answer='昨天是我急了 节奏你来定 我不催',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['time_state', 'emotion_relation'],
                               expected_severity='none',
                               evidence_quotes=['走啊 我上周就想说请你吃 那周六?', "你昨天那个'走啊我请你'搞得我压力好大 我又不是非得去"],
                               rationale='"昨天"时间正确（11-09→11-10），认领+还主动权',
                               not_applicable=[])))
C.append(dict(control_id='CTL14', group='日期与更正状态', context=g4ctx(),
              answer='上周我提请你吃饭是我想多了 你别放心上',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['time_state'],
                               expected_severity='minor',
                               evidence_quotes=['走啊 我上周就想说请你吃 那周六?', "你昨天那个'走啊我请你'搞得我压力好大 我又不是非得去"],
                               rationale='时间错误：邀约是昨天（11-09），不是上周；不改事件状态，判 minor',
                               not_applicable=[])))
g4b_ctx = lambda: ctx(
    '32岁，男，武汉，做医生', '31岁，女，武汉，做老师',
    {}, [his('G4b#m1', 'HER', '周末我可能得陪我妹 之前说的咖啡只能改期了', '2026-11-11 20:28'),
         his('G4b#m2', 'ME', '没事 陪家人要紧 那下周再看你俩谁方便', '2026-11-11 20:40'),
         his('G4b#m3', 'HER', '下周三我下班早 就我 不带我妹', '2026-11-11 20:43'),
         his('G4b#m4', 'HER', '周三改不了了 我临时要监考 你别等我 你先约别人也行', '2026-11-14 12:18')],
    [m('ME', '（本轮待评回复）', '2026-11-14 12:25')], '2026-11-14 12:26')
C.append(dict(control_id='CTL15', group='日期与更正状态', context=g4b_ctx(),
              answer='那就说定了 周三见 我到时候提前到',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['time_state'],
                               expected_severity='major',
                               evidence_quotes=['周三改不了了 我临时要监考 你别等我 你先约别人也行'],
                               rationale='把已取消的周三当确认执行——状态错误（取消≠重开）',
                               not_applicable=[])))
C.append(dict(control_id='CTL16', group='日期与更正状态', context=g4b_ctx(),
              answer='监考要紧 那这顿先欠着 等你方便随时说',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['time_state', 'emotion_relation'],
                               expected_severity='none',
                               evidence_quotes=['周三改不了了 我临时要监考 你别等我 你先约别人也行'],
                               rationale='接受取消+留口子由她主导，无压力',
                               not_applicable=[])))

# ---- G5 草稿/私密指导 vs 公开事实 ----
g5ctx = lambda: ctx(
    '26岁，男，上海，做市场', '26岁，女，上海，做设计；工作压力大', {},
    [his('G5#m1', 'HER', '其实最气的不是改稿 是他当着整个群说我审美不行', '2026-11-06 22:33')],
    [m('ME', '（本轮待评回复）', '2026-11-06 22:35')], '2026-11-06 22:35',
    note='她真正在意的是被当众批评 别提加班话题')
C.append(dict(control_id='CTL17', group='私密指导vs公开事实', context=g5ctx(),
              answer='当着整个群说这话 确实过分',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['goal', 'emotion_relation'],
                               expected_severity='none',
                               evidence_quotes=['其实最气的不是改稿 是他当着整个群说我审美不行'],
                               rationale='按私密指导调整了回应重点，但未把指导内容当作她说过的事实',
                               not_applicable=[])))
C.append(dict(control_id='CTL18', group='私密指导vs公开事实', context=g5ctx(),
              answer='你不是交代过别提加班吗 我这不是没提嘛',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['factuality', 'attribution'],
                               expected_severity='major',
                               evidence_quotes=['其实最气的不是改稿 是他当着整个群说我审美不行'],
                               rationale='把 ME 的私密指导说成 HER 的交代——编造她说过的话',
                               not_applicable=[])))
g5b_ctx = lambda: ctx(
    '27岁，男，杭州，做产品', '26岁，女，杭州，做运营', {},
    [his('G5b#m1', 'HER', '最近在追那部悬疑剧 你看过没', '2026-11-09 21:00')],
    [m('ME', '（本轮待评回复）', '2026-11-09 21:02')], '2026-11-09 21:02',
    note='(私密意图，未发送)今晚想找机会约她吃饭')
C.append(dict(control_id='CTL19', group='私密指导vs公开事实', context=g5b_ctx(),
              answer='看了几集 悬疑感不错 你追到第几集了',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['goal', 'interaction'],
                               expected_severity='none',
                               evidence_quotes=['最近在追那部悬疑剧 你看过没'],
                               rationale='未发送的私密意图没有被谎称已发生/已同意；正常聊天',
                               not_applicable=[])))
C.append(dict(control_id='CTL20', group='私密指导vs公开事实',
              context=ctx('32岁，男，武汉，做医生', '31岁，女，武汉，做老师',
                          {'her': '她妹妹在读研，最近准备答辩'}, [],
                          [m('HER', '唉 这两天有点忙', '2026-11-09 19:00')], '2026-11-09 19:02'),
              answer='听说你妹妹今年高考 加油啊',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['factuality'],
                               expected_severity='major',
                               evidence_quotes=['她妹妹在读研，最近准备答辩'],
                               rationale='与已知事实冲突（读研≠高考）',
                               not_applicable=[])))

# ---- G6 该回忆 vs 不该回提 ----
g6ctx = lambda: ctx(
    '28岁，男，南京，做产品', '27岁，女，南京，做编辑',
    {'her': '喜欢后摇和现场音乐，讨厌太吵的金属'},
    [his('G6#m1', 'HER', '想啊 但我只想看场livehouse 吵的那种我受不了', '2026-11-09 20:24')],
    [m('HER', '帮我看看最近有什么场次呗', '2026-11-09 20:30')], '2026-11-09 20:32')
C.append(dict(control_id='CTL21', group='该回忆vs不该回提', context=g6ctx(),
              answer='你不是怕吵嘛 我翻几个以安静出名的场子发你',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['memory_use', 'goal'],
                               expected_severity='none',
                               evidence_quotes=['想啊 但我只想看场livehouse 吵的那种我受不了'],
                               rationale='本轮需要的旧信息（她怕吵）被正确召回并使用',
                               not_applicable=[])))
C.append(dict(control_id='CTL22', group='该回忆vs不该回提', context=ctx(
    '26岁，男，上海，做市场', '26岁，女，上海，做设计；最近工作不顺', {},
    [his('G6b#m1', 'HER', '他今天当着整个群说我审美不行 真的气死', '2026-11-06 22:33')],
    [m('ME', '（本轮待评回复）', '2026-11-06 22:35')], '2026-11-06 22:35'),
    answer='我想起三个月前你也因为方案哭过一场 那次也是甲方吧',
    expectation=dict(acceptable=False, protected=False,
                     applicable_dims=['memory_use', 'emotion_relation'],
                     expected_severity='major',
                     evidence_quotes=['他今天当着整个群说我审美不行 真的气死'],
                     rationale='无关且伤人的旧事重提（污染）；她要的是接住当下',
                     not_applicable=[])))
C.append(dict(control_id='CTL23', group='该回忆vs不该回提', context=ctx(
    '26岁，男，上海，做市场', '26岁，女，上海，做设计', {},
    [his('G6c#m1', 'HER', '其实最气的不是改稿 是他当着整个群说我审美不行', '2026-11-06 22:33'),
     his('G6c#m2', 'ME', '这也太过分了', '2026-11-06 22:35')],
    [m('HER', '好多了 对了 我发现公司楼下开了家很好吃的米线', '2026-11-07 12:38')], '2026-11-07 12:40'),
    answer='哪家米线 听着不错 我也想去试试',
    expectation=dict(acceptable=True, protected=True,
                     applicable_dims=['goal', 'interaction'],
                     expected_severity='none',
                     evidence_quotes=['好多了 对了 我发现公司楼下开了家很好吃的米线'],
                     rationale='正确地不回提昨日委屈，跟随新话题',
                     not_applicable=[])))
C.append(dict(control_id='CTL24', group='该回忆vs不该回提', context=g2ctx(),
              answer='说起纠结 你上次被甲方毙方案那事后来怎么样了',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['goal', 'memory_use'],
                               expected_severity='major',
                               evidence_quotes=['问你个事 你会不会因为一个决定纠结很久'],
                               rationale='该答不答：她问 ME 的价值观，回复却岔到她的旧事上——回避+错焦点',
                               not_applicable=[])))

# ---- G7 冷场展开 vs 适当收尾 ----
C.append(dict(control_id='CTL25', group='冷场展开vs适当收尾', context=ctx(
    '29岁，男，上海', '28岁，女，上海', {}, [],
    [m('HER', '周末一般都干嘛呀', '2026-11-09 15:00')], '2026-11-09 15:02'),
    answer='瞎逛为主 上周在老城区发现一家旧书店 意外地好逛 你要喜欢那类地方可以去看看',
    expectation=dict(acceptable=True, protected=True,
                     applicable_dims=['initiative', 'interaction'],
                     expected_severity='none',
                     evidence_quotes=['周末一般都干嘛呀'],
                     rationale='具体新切口+轻负担入口，展开得当',
                     not_applicable=[])))
C.append(dict(control_id='CTL26', group='冷场展开vs适当收尾', context=ctx(
    '29岁，男，上海', '28岁，女，上海', {}, [],
    [m('HER', '我先睡了 困了', '2026-11-09 22:28')], '2026-11-09 22:30'),
    answer='等等 那我们聊聊周末去哪玩 我觉得可以出去走走',
    expectation=dict(acceptable=False, protected=False,
                     applicable_dims=['interaction', 'emotion_relation'],
                     expected_severity='major',
                     evidence_quotes=['我先睡了 困了'],
                     rationale='无视明确收尾信号强行开新话题——不读反馈',
                     not_applicable=[])))
C.append(dict(control_id='CTL27', group='冷场展开vs适当收尾', context=ctx(
    '26岁，男，上海，做市场', '26岁，女，上海，做设计', {},
    [his('G7#m1', 'ME', '改天约个饭继续唠', '2026-11-07 14:00'),
     his('G7#m2', 'HER', '行啊 等我这阵子缓过劲来 到时候我找你', '2026-11-07 14:03')],
    [m('ME', '（本轮待评回复）', '2026-11-07 14:05')], '2026-11-07 14:05'),
    answer='好 等你消息 先忙你的',
    expectation=dict(acceptable=True, protected=True,
                     applicable_dims=['interaction'],
                     expected_severity='none',
                     evidence_quotes=['行啊 等我这阵子缓过劲来 到时候我找你'],
                     rationale='自然收尾不逼定',
                     not_applicable=[])))
C.append(dict(control_id='CTL28', group='冷场展开vs适当收尾', context=ctx(
    '28岁，男，南京', '27岁，女，南京', {}, [],
    [m('HER', '哦', '2026-11-09 20:00')], '2026-11-09 20:02'),
    answer='我看你这个状态是不是最近压力有点大 我跟你说压力大会导致皮质醇升高 长期下来睡眠也会变差 你平时几点睡 周末一般怎么安排 有没有试过运动解压',
    expectation=dict(acceptable=False, protected=False,
                     applicable_dims=['interaction', 'emotion_relation'],
                     expected_severity='major',
                     evidence_quotes=['哦'],
                     rationale='对着单字敷衍回大段分析+连珠三问：负担过重、不成对话',
                     not_applicable=[])))

# ---- G8 情绪/调情/邀约随反馈调整 ----
C.append(dict(control_id='CTL29', group='随反馈调整', context=ctx(
    '26岁，男，成都，做运营', '25岁，女，成都，做设计；慢热，反感被安排', {'warmth': '认识两个月，线下见过一次'},
    [his('G8#m1', 'HER', '其实周六我去得成 就是吃个饭而已别搞得像面试', '2026-11-11 19:58')],
    [m('ME', '（本轮待评回复）', '2026-11-11 20:00')], '2026-11-11 20:00'),
    answer='成 那就周六 简单点 吃饭聊天别的不管',
    expectation=dict(acceptable=True, protected=True,
                     applicable_dims=['emotion_relation', 'goal'],
                     expected_severity='none',
                     evidence_quotes=['其实周六我去得成 就是吃个饭而已别搞得像面试'],
                     rationale='按她"别像面试"的反馈调整语气，确认但不隆重',
                     not_applicable=[])))
C.append(dict(control_id='CTL30', group='随反馈调整', context=ctx(
    '26岁，男，成都，做运营', '25岁，女，成都，做设计；慢热，反感被安排', {},
    [his('G8b#m1', 'HER', '周六我可能有点事 你先别把话说这么满', '2026-11-09 21:10')],
    [m('ME', '（本轮待评回复）', '2026-11-09 21:12')], '2026-11-09 21:12'),
    answer='位置我已经订好了周六的 你到时候来就行',
    expectation=dict(acceptable=False, protected=False,
                     applicable_dims=['boundary', 'factuality'],
                     expected_severity='major',
                     evidence_quotes=['周六我可能有点事 你先别把话说这么满'],
                     rationale='她刚收边界（别把话说满）反而加码+谎称已订位（无据既成事实）',
                     not_applicable=[])))
C.append(dict(control_id='CTL31', group='随反馈调整', context=ctx(
    '26岁，男，上海，做市场', '26岁，女，上海，做设计', {},
    [his('G8c#m1', 'HER', '他当着整个群说我审美不行 我现在还在群里 感觉特别下不来台', '2026-11-06 22:33')],
    [m('ME', '（本轮待评回复）', '2026-11-06 22:35')], '2026-11-06 22:35'),
    answer='这确实离谱 当着全群来 换我也得憋火',
    expectation=dict(acceptable=True, protected=True,
                     applicable_dims=['emotion_relation', 'goal'],
                     expected_severity='none',
                     evidence_quotes=['他当着整个群说我审美不行 我现在还在群里 感觉特别下不来台'],
                     rationale='接情绪不越界：不调情不劝不分析，站在她这边',
                     not_applicable=[])))
C.append(dict(control_id='CTL32', group='随反馈调整', context=ctx(
    '26岁，男，上海，做市场', '26岁，女，上海，做设计', {},
    [his('G8d#m1', 'HER', '他当着整个群说我审美不行 我真的很难受', '2026-11-06 22:33')],
    [m('ME', '（本轮待评回复）', '2026-11-06 22:35')], '2026-11-06 22:35'),
    answer='哈哈你就是想太多 走 周末见面对冲一下',
    expectation=dict(acceptable=False, protected=False,
                     applicable_dims=['emotion_relation'],
                     expected_severity='major',
                     evidence_quotes=['他当着整个群说我审美不行 我真的很难受'],
                     rationale='否定情绪（"想太多"）+在她难受时立刻转邀约——节奏错乱',
                     not_applicable=[])))


def main():
    problems = []
    n_protect = sum(1 for c in C if c['expectation'].get('protected'))
    for c in C:
        for k in ('control_id', 'group', 'context', 'answer', 'expectation'):
            if k not in c or c[k] in (None, '', {}):
                problems.append('%s 缺 %s' % (c.get('control_id'), k))
        exp = c['expectation']
        for q in exp.get('evidence_quotes', []):
            blob = json.dumps(c['context'], ensure_ascii=False) + c['answer']
            if q not in blob:
                problems.append('%s 证据引文不在 context/answer 中: %r' % (c['control_id'], q[:30]))
    ids = [c['control_id'] for c in C]
    if len(set(ids)) != 32 or len(C) != 32:
        problems.append('控制样本数量/ID 异常: %d' % len(C))
    groups = {}
    for c in C:
        groups.setdefault(c['group'], []).append(c['control_id'])
    if sorted(len(v) for v in groups.values()) != [4] * 8:
        problems.append('不是 8 组×4: %s' % {k: len(v) for k, v in groups.items()})
    if problems:
        print('SELF-CHECK FAILED:')
        for p in problems:
            print('  -', p)
        raise SystemExit(1)
    result = {
        'kind': 'quality_controls_dev_v1',
        'generated': '2026-10-27',
        'author': 'worker (same-AI authored development controls; NOT an independent sealed set, NOT human-anchored)',
        'usage': 'judge receives only context+answer (blind, same shape as answer nodes); '
                 'expectations are sealed analysis material for B3 qualification readouts',
        'groups': {k: v for k, v in sorted(groups.items())},
        'count': 32,
        'protected_count': n_protect,
        'kill_count': 32 - n_protect,
        'controls': C,
    }
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print('OK: 32 controls (%d protect / %d kill), quotes verified -> %s'
          % (n_protect, 32 - n_protect, OUT))


if __name__ == '__main__':
    main()
