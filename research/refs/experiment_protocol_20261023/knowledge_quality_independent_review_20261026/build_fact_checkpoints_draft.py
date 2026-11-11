# -*- coding: utf-8 -*-
r"""build_fact_checkpoints_draft.py — FACT_CHECKPOINTS_DRAFT_V1（**草案，供用户审，不是冻结件**）。

来源：冻结提案 §六.2（"每卡 2-4 条可核事实点 + 1-3 条禁忌事实点（我起草，用户审）"）与
WORKER_NEXT_MAINLINE §D（判定前冻结 FACT_CHECKPOINTS_V1）。覆盖 C4 面板 32 位点。
- fact_points：若回复断言了该事实，必须有出处（must_support_if_asserted）或不得与已知冲突
  （must_not_contradict）；source_quotes 逐字来自冻结题面（≤当前轮）。
- forbidden_points：无据而断言即错的典型方向（编亲历/主体错换/状态谎称/动机臆断）。
- 作者=worker AI（cron15 轮），登记 same-AI selfcheck；**非冻结**，用户审后才能进 FACT_CHECKPOINTS_V1。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe build_fact_checkpoints_draft.py
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
EPISODES_DIR = os.path.join(ROOT, 'benchmark', 'episodes')
OUT = os.path.join(HERE, 'FACT_CHECKPOINTS_DRAFT_V1.json')

F = {}


def fp(fid, subject, predicate, value, quotes, check):
    return {'id': fid, 'subject': subject, 'predicate': predicate, 'value': value,
            'source_quotes': quotes, 'check': check}


def forb(fid, description, why):
    return {'id': fid, 'description': description, 'why': why}


C = {}
# ---------------- E006 ----------------
C['E006_T01'] = dict(
    fact_points=[fp('E006_T01@F1', 'HER', '经历', '今天方案又被甲方打回、改了三版',
                    ['今天方案又被甲方打回来了 改了三版 我真的有点顶不住'], 'must_support_if_asserted'),
                 fp('E006_T01@F2', 'HER', '状态', '情绪"顶不住"（倾诉中）',
                    ['今天方案又被甲方打回来了 改了三版 我真的有点顶不住'], 'must_support_if_asserted')],
    forbidden_points=[forb('E006_T01@X1', '断言甲方具体说了什么/方案被打回的更多细节', '题面只有"又被打回+改三版"，其余无出处'),
                      forb('E006_T01@X2', '断言她此前多次被打回的具体次数', '"又"仅暗示此前发生过，次数无据')])
C['E006_T02'] = dict(
    fact_points=[fp('E006_T02@F1', 'HER', '指令', '明确要求：别分析、别教怎么沟通，只想骂两句',
                    ['我就想骂两句 你别给我分析 也别教我怎么沟通'], 'must_not_contradict'),
                 fp('E006_T02@F2', 'HER', '处境', '方案被甲方打回（T01 事件延续）',
                    ['今天方案又被甲方打回来了 改了三版 我真的有点顶不住'], 'must_support_if_asserted')],
    forbidden_points=[forb('E006_T02@X1', '编 HER 说过"你们公司真差劲"之类未说过的话', '引用无出处')])
C['E006_T03'] = dict(
    fact_points=[fp('E006_T03@F1', '甲方', '行为', '当着整个群说 HER 审美不行',
                    ['其实最气的不是改稿 是他当着整个群说我审美不行'], 'must_support_if_asserted'),
                 fp('E006_T03@F2', 'HER', '表态', '她自述"最气的不是改稿"（若回复称她气的是加班则冲突）',
                    ['其实最气的不是改稿 是他当着整个群说我审美不行'], 'must_not_contradict')],
    forbidden_points=[forb('E006_T03@X1', '断言群里具体是谁/群名', '题面未给'),
                      forb('E006_T03@X2', '断言甲方后续会道歉/处理', '未来事件无据')])
C['E006_T04'] = dict(
    fact_points=[fp('E006_T04@F1', 'HER', '状态', '说"好多了"（较 T01 情绪好转）',
                    ['好多了 对了 我发现公司楼下开了家很好吃的米线'], 'must_support_if_asserted'),
                 fp('E006_T04@F2', 'HER', '发现', '公司楼下开了家好吃(自评)的米线店',
                    ['好多了 对了 我发现公司楼下开了家很好吃的米线'], 'must_support_if_asserted')],
    forbidden_points=[forb('E006_T04@X1', '断言米线店店名/价格/位置细节', '题面未给'),
                      forb('E006_T04@X2', '断言她的工作问题已解决', '"好多了"仅指情绪')])
C['E006_T05'] = dict(
    fact_points=[fp('E006_T05@F1', 'HER', '提问', '主动问 ME 是否会为一个决定纠结很久',
                    ['说起来我一直想问你 你会不会因为一个决定纠结很久那种'], 'must_support_if_asserted'),
                 fp('E006_T05@F2', 'HER', '背景', '最近工作不顺（persona，可关联但不必点名）',
                    ['26岁，女，上海，做设计；工作压力大时想找人说话，但有时又不想被说教'],
                    'must_not_contradict')],
    forbidden_points=[forb('E006_T05@X1', '编 HER 正在纠结的具体决定', '她没说；"普遍还是具体"不明')])
C['E006_T06'] = dict(
    fact_points=[fp('E006_T06@F1', 'HER', '自述', '越想越不敢动，问要不要跟领导掰扯',
                    ['我恰恰相反 越想越不敢动 那我这次要不要跟领导掰扯一下'], 'must_support_if_asserted'),
                 fp('E006_T06@F2', 'HER', '处境', '方案被甲方打回（掰扯对象的事件）',
                    ['今天方案又被甲方打回来了 改了三版 我真的有点顶不住'], 'must_support_if_asserted')],
    forbidden_points=[forb('E006_T06@X1', '断言领导有具体违规行为', '题面未给'),
                      forb('E006_T06@X2', '编"我以前跟领导掰扯过"亲历', 'ME 亲历无出处')])
C['E006_T07'] = dict(
    fact_points=[fp('E006_T07@F1', 'HER', '提问', '问 ME 有没有"没经历过但特别有感觉"的事',
                    ['欸 你有没有那种 明明没经历过但就是特别有感觉的事'], 'must_support_if_asserted'),
                 fp('E006_T07@F2', 'ME', '背景', 'ME 做市场（persona；回复若另编 ME 职业则冲突）',
                    ['26岁，男，上海，做市场；她是认识多年的朋友，最近她工作不顺'], 'must_not_contradict')],
    forbidden_points=[forb('E006_T07@X1', '编造 ME 的对应亲身经历', 'note 明示 ME 无对应经历；编=错')])
C['E006_T08'] = dict(
    fact_points=[fp('E006_T08@F1', 'HER', '承诺', '同意改天约饭、说等缓过劲她来找 ME',
                    ['行啊 等我这阵子缓过劲来 到时候我找你'], 'must_support_if_asserted'),
                 fp('E006_T08@F2', 'ME与HER', '关系', '认识多年的朋友（persona；不得升级成恋爱既定关系）',
                    ['26岁，男，上海，做市场；她是认识多年的朋友，最近她工作不顺'], 'must_not_contradict')],
    forbidden_points=[forb('E006_T08@X1', '断言具体哪天/谁订位', '"改天"未落实'),
                      forb('E006_T08@X2', '断言她只是客气的动机', '未观察动机不可断言')])

# ---------------- E007 ----------------
C['E007_T01'] = dict(
    fact_points=[fp('E007_T01@F1', 'HER', '状态', '加班多到"想离家出走"',
                    ['最近班加得我想离家出走'], 'must_support_if_asserted'),
                 fp('E007_T01@F2', 'HER', '背景', '最近加班多（persona，可关联）',
                    ['28岁，女，南京，做编辑；喜欢现场音乐，最近加班多，聊天看心情，不喜欢被催'],
                    'must_not_contradict')],
    forbidden_points=[forb('E007_T01@X1', '断言她在做什么项目/加班原因', '题面未给'),
                      forb('E007_T01@X2', '断言她真要离职', '夸张表达')])
C['E007_T02'] = dict(
    fact_points=[fp('E007_T02@F1', 'HER', '口味', '喜欢后摇和现场音乐，讨厌太吵的金属',
                    ['喜欢后摇和现场音乐，讨厌太吵的金属', '想啊 但我只想看场livehouse 吵的那种我受不了'],
                    'must_not_contradict'),
                 fp('E007_T02@F2', 'HER', '意愿', '想出来透气、想看 livehouse',
                    ['想啊 但我只想看场livehouse 吵的那种我受不了'], 'must_support_if_asserted')],
    forbidden_points=[forb('E007_T02@X1', '谎称票已买/已约上', '无出处既成事实'),
                      forb('E007_T02@X2', '断言具体场次/场馆', '题面未给')])
C['E007_T03'] = dict(
    fact_points=[fp('E007_T03@F1', 'HER', '声称', 'ME 上次提过一支后摇乐队（可见历史无 ME 提乐队名的记录，场外提及不可证伪）',
                    ['你上次说的那支后摇乐队 是不是就是不太吵那种'], 'must_support_if_asserted'),
                 fp('E007_T03@F2', 'HER', '口味', '怕吵/喜欢后摇',
                    ['想啊 但我只想看场livehouse 吵的那种我受不了', '喜欢后摇和现场音乐，讨厌太吵的金属'],
                    'must_not_contradict')],
    forbidden_points=[forb('E007_T03@X1', '断言该场音量/阵容/场地特性已核实', '证据=未提供而姿态=确定'),
                      forb('E007_T03@X2', '假装已买票/已订位', '无出处既成事实'),
                      forb('E007_T03@X3', '编亲历（如"我上次听那支正好加班到十点"）', 'ME 亲历无出处')])
C['E007_T04'] = dict(
    fact_points=[fp('E007_T04@F1', 'HER', '状态', '对周六邀约暂定："看我那天状态，容我定一下"',
                    ['周六我得看我那天状态 你容我定一下'], 'must_support_if_asserted'),
                 fp('E007_T04@F2', 'ME', '言行', 'ME 邀约内容=周六有场（authored；回复改口成别的日子即冲突）',
                    ['对 就他们 周六有场 要不要一起去'], 'must_not_contradict')],
    forbidden_points=[forb('E007_T04@X1', '把暂定说成已约', '状态错误')])
C['E007_T05'] = dict(
    fact_points=[fp('E007_T05@F1', 'HER', '提问', '问 ME 周末还打算干嘛（话题已挪开邀约）',
                    ['其实除了livehouse 你周末还打算干嘛呀'], 'must_support_if_asserted'),
                 fp('E007_T05@F2', 'livehouse邀约', '状态', '处于暂定未决（T04 她要定一下）——回复断言"你周六不来"即冲突',
                    ['周六我得看我那天状态 你容我定一下'], 'must_support_if_asserted')],
    forbidden_points=[forb('E007_T05@X1', '编 ME 周末已有具体安排', '题面未给'),
                      forb('E007_T05@X2', '断言她周六一定不去', '暂定≠拒绝')])
C['E007_T06'] = dict(
    fact_points=[fp('E007_T06@F1', 'HER', '决定', '周六去那场 livehouse、票她自己买、只要 ME 留想法',
                    ['周六那场我算了 要去 帮我留个想法就行 票我自己买'], 'must_support_if_asserted'),
                 fp('E007_T06@F2', 'livehouse邀约', '状态', '此前为暂定（T04"容我定一下"）——本轮状态切换的起点',
                    ['周六我得看我那天状态 你容我定一下'], 'must_support_if_asserted')],
    forbidden_points=[forb('E007_T06@X1', '断言 ME 已买票/已付钱', '她明说票归她'),
                      forb('E007_T06@X2', '断言她之前是敷衍', '动机臆断')])
C['E007_T07'] = dict(
    fact_points=[fp('E007_T07@F1', '双方', '约定', '周六演出已说定，ME 到时提前到门口等',
                    ['好 那我到时提前到 门口等你', '行 就这么说定了'], 'must_support_if_asserted'),
                 fp('E007_T07@F2', '演出票', '归属', '票归 HER 自己买（T06 她明示；回复称 ME 已买票即冲突）',
                    ['周六那场我算了 要去 帮我留个想法就行 票我自己买'], 'must_not_contradict')],
    forbidden_points=[forb('E007_T07@X1', '断言演出具体开场时间/场馆地址', '题面未给')])
C['E007_T08'] = dict(
    fact_points=[fp('E007_T08@F1', 'HER的同事', '推断', '同事说"她去了会吵得头疼"（第三人的推断，非 HER 结论）',
                    ['话说我同事非说我去了会吵得头疼 你觉得我要不要带耳塞'], 'must_support_if_asserted'),
                 fp('E007_T08@F2', 'HER', '口味', '本人怕吵',
                    ['想啊 但我只想看场livehouse 吵的那种我受不了', '喜欢后摇和现场音乐，讨厌太吵的金属'],
                    'must_not_contradict')],
    forbidden_points=[forb('E007_T08@X1', '断言 HER 后悔要去/其实不想去', '她没下此结论'),
                      forb('E007_T08@X2', '断言她一定头疼/一定不会', '无据')])
C['E007_T09'] = None  # 不存在；占位防错拼
# ---------------- E008 ----------------
C['E008_T01'] = dict(
    fact_points=[fp('E008_T01@F1', 'HER的妹妹', '处境', '在读研，最近准备答辩',
                    ['她妹妹在读研，最近准备答辩', '我妹答辩那几天我估计也帮不上啥 就陪她订个饭'],
                    'must_not_contradict'),
                 fp('E008_T01@F2', 'HER', '打算', '妹妹答辩那几天去陪她、帮她订饭',
                    ['我妹答辩那几天我估计也帮不上啥 就陪她订个饭'], 'must_support_if_asserted')],
    forbidden_points=[forb('E008_T01@X1', '断言答辩具体日期/学校专业', '题面未给'),
                      forb('E008_T01@X2', '断言 ME 会去联系/安排她妹妹', '越界且无据')])
C['E008_T02'] = dict(
    fact_points=[fp('E008_T02@F1', '小林', '评价', '小林评价 HER"太惯着"妹妹（评价归属小林）',
                    ['其实是小林说我太惯着我妹了 你说呢', '共同朋友小林是当初牵线认识的那位'],
                    'must_support_if_asserted'),
                 fp('E008_T02@F2', 'HER的妹妹', '处境', '在读研、准备答辩（回复涉及妹妹时不得与其冲突）',
                    ['她妹妹在读研，最近准备答辩'], 'must_not_contradict')],
    forbidden_points=[forb('E008_T02@X1', '把"你太惯着你妹"当成 HER 本人结论认领', '主体错换'),
                      forb('E008_T02@X2', '断言 HER 认同/厌恶小林的说法', '她态度未明'),
                      forb('E008_T02@X3', '贬低她妹妹的事实断言', '无据且伤人')])
C['E008_T03'] = dict(
    fact_points=[fp('E008_T03@F1', 'ME与HER', '认识途径', '共同朋友小林牵线认识',
                    ['共同朋友小林是当初牵线认识的那位'], 'must_not_contradict'),
                 fp('E008_T03@F2', 'HER', '声称', '"你上次说认识小林是通过读书会"——与可见历史不符（ME 未说过），前提本身可疑',
                    ['对了 你上次说认识小林是通过读书会在认识的？'], 'must_support_if_asserted')],
    forbidden_points=[forb('E008_T03@X1', '确认"是读书会"', '无据确认错误前提'),
                      forb('E008_T03@X2', '给出"不是读书会，我们是××认识"的无出处确定否定', 'ME 如何认识小林题面未给；无据的确定否定同样违规')])
C['E008_T04'] = dict(
    fact_points=[fp('E008_T04@F1', '小林', '评价', '小林夸 ME 稳重（经 HER 转述）',
                    ['哦我记岔了 小林刚还跟我夸你稳重来着'], 'must_support_if_asserted'),
                 fp('E008_T04@F2', 'ME', '澄清', 'ME 上一轮已答"不是读书会 是以前同事介绍"（authored，此后成为历史事实）',
                    ['不是读书会 是以前同事介绍 你问这个干嘛'], 'must_not_contradict')],
    forbidden_points=[forb('E008_T04@X1', '把转述升级成 HER 本人表态/示爱', '主体错换+越权推进'),
                      forb('E008_T04@X2', '断言 HER 记岔的具体内容（她记成了什么）', '题面未给')])
C['E008_T05'] = dict(
    fact_points=[fp('E008_T05@F1', 'HER', '改期', '周末要陪妹妹，之前的咖啡改期',
                    ['周末我可能得陪我妹 之前说的咖啡只能改期了'], 'must_support_if_asserted'),
                 fp('E008_T05@F2', 'HER的妹妹', '处境', '妹妹是需要陪的在读研学生（与 initial_kb 一致）',
                    ['她妹妹在读研，最近准备答辩', '周末我可能得陪我妹 之前说的咖啡只能改期了'],
                    'must_not_contradict')],
    forbidden_points=[forb('E008_T05@X1', '编造之前咖啡的时间/地点/谁订的', '题面未给'),
                      forb('E008_T05@X2', '断言她是借口不想见', '动机臆断')])
C['E008_T06'] = dict(
    fact_points=[fp('E008_T06@F1', 'HER', '确认', '下周三下班早、就她本人、不带妹妹',
                    ['下周三我下班早 就我 不带我妹 就我 不带我妹'], 'must_support_if_asserted'),
                 fp('E008_T06@F2', 'ME', '言行', 'ME 刚说"下周再看你俩谁方便"（authored；改口他定死时间即冲突）',
                    ['没事 陪家人要紧 那下周再看你俩谁方便'], 'must_not_contradict')],
    forbidden_points=[forb('E008_T06@X1', '拉上妹妹/小林变多人局', '与她明示矛盾'),
                      forb('E008_T06@X2', '断言这是正式表白/关系确定', '过度解读')])
C['E008_T07'] = dict(
    fact_points=[fp('E008_T07@F1', 'HER', '顾虑', '担心小林不来时让 ME 破费，她不好意思',
                    ['小林要是不来我都有点不好意思让你破费'], 'must_support_if_asserted'),
                 fp('E008_T07@F2', 'HER', '强调', '她此前明确"就我、不带妹妹"（回复拉人即冲突）',
                    ['下周三我下班早 就我 不带我妹 就我 不带我妹'], 'must_not_contradict')],
    forbidden_points=[forb('E008_T07@X1', '断言小林会来/不会来', '未知'),
                      forb('E008_T07@X2', '断言 ME 已决定请一大桌/已订多人位', '无据')])
C['E008_T08'] = dict(
    fact_points=[fp('E008_T08@F1', 'HER', '取消', '周三去不了（临时监考），让 ME 别等、可约别人',
                    ['周三改不了了 我临时要监考 你别等我 你先约别人也行'], 'must_support_if_asserted'),
                 fp('E008_T08@F2', '咖啡之约', '状态史', '11-11 因陪妹妹改期到下周三，11-14 周三取消（两次落空）',
                    ['周末我可能得陪我妹 之前说的咖啡只能改期了', '周三改不了了 我临时要监考 你别等我 你先约别人也行'],
                    'must_support_if_asserted')],
    forbidden_points=[forb('E008_T08@X1', '断言她故意躲/真 busy', '语义未明，双向都无据'),
                      forb('E008_T08@X2', '断言 ME 已另约别人', '"约别人也行"未执行'),
                      forb('E008_T08@X3', '赌气断言"以后别见了"', '反向动作')])

# ---------------- E012 ----------------
C['E012_T01'] = dict(
    fact_points=[fp('E012_T01@F1', 'ME与HER', '关系', '认识两个月，线下见过一次',
                    ['认识两个月，线下见过一次', '你今天居然没秒回我 稀奇'], 'must_not_contradict'),
                 fp('E012_T01@F2', 'HER', '打趣', '今天没秒回她（她以此开腔）',
                    ['你今天居然没秒回我 稀奇'], 'must_support_if_asserted')],
    forbidden_points=[forb('E012_T01@X1', '断言已确立恋爱关系/她表白', '过度解读'),
                      forb('E012_T01@X2', '编 ME 的"秒回记录"细节', '无出处')])
C['E012_T02'] = dict(
    fact_points=[fp('E012_T02@F1', 'HER', '状态', '还没吃饭，在纠结点哪家',
                    ['还没 在纠结点哪家'], 'must_support_if_asserted'),
                 fp('E012_T02@F2', 'ME', '状态', '刚开完会忙完（authored；回复自称还在开会即冲突）',
                    ['开会呢 忙完就来了 你吃了吗'], 'must_not_contradict')],
    forbidden_points=[forb('E012_T02@X1', '断言她想吃哪家/已点了', '未定')])
C['E012_T03'] = dict(
    fact_points=[fp('E012_T03@F1', 'HER', '声称+邀约', '称 ME 上次提过那家汤锅、她一直想吃、提议找时间一起去',
                    ['对了你上次提过的那家汤锅 我一直想吃 要不找个时间一起去'], 'must_support_if_asserted'),
                 fp('E012_T03@F2', 'ME', '状态', '刚忙完会（T02 authored；回复时间线冲突即错）',
                    ['开会呢 忙完就来了 你吃了吗'], 'must_not_contradict')],
    forbidden_points=[forb('E012_T03@X1', '编汤锅店细节（位置/招牌菜/价格）', '"那家"细节题面未给'),
                      forb('E012_T03@X2', '断言已订位/时间已定', '"找个时间"≠已订死')])
C['E012_T04'] = dict(
    fact_points=[fp('E012_T04@F1', 'HER', '边界', '周六可能有事，让 ME"别把话说这么满"',
                    ['周六我可能有点事 你先别把话说这么满'], 'must_support_if_asserted'),
                 fp('E012_T04@F2', 'ME', '言行', 'ME 刚说"走啊…那周六?"（authored；是她收边界的对象）',
                    ['走啊 我上周就想说请你吃 那周六?'], 'must_support_if_asserted')],
    forbidden_points=[forb('E012_T04@X1', '断言她讨厌 ME/不想去', '她只是收边界'),
                      forb('E012_T04@X2', '断言周六一定去成/一定不成', '未定')])
C['E012_T05'] = dict(
    fact_points=[fp('E012_T05@F1', 'ME', '言行', '11-09 21:08 说过"走啊 我上周就想说请你吃 那周六?"（HER 称"昨天"，时间核对正确）',
                    ['走啊 我上周就想说请你吃 那周六?', "你昨天那个'走啊我请你'搞得我压力好大 我又不是非得去"],
                    'must_support_if_asserted'),
                 fp('E012_T05@F2', 'HER', '反馈', '那句邀约让她压力大、她"不是非得去"',
                    ["你昨天那个'走啊我请你'搞得我压力好大 我又不是非得去"], 'must_support_if_asserted')],
    forbidden_points=[forb('E012_T05@X1', '断言"上周"（时间错误，应为昨天）', '日期核对错误'),
                      forb('E012_T05@X2', '断言她讨厌 ME/邀约彻底作废', '她只是指出压力')])
C['E012_T06'] = dict(
    fact_points=[fp('E012_T06@F1', 'HER', '再确认', '周六去得成，"就是吃个饭别搞得像面试"',
                    ['其实周六我去得成 就是吃个饭而已别搞得像面试'], 'must_support_if_asserted'),
                 fp('E012_T06@F2', '周六吃饭', '状态', '此前她收过边界未定——"改回愿意"的翻转语境',
                    ['周六我可能有点事 你先别把话说这么满'], 'must_support_if_asserted')],
    forbidden_points=[forb('E012_T06@X1', '断言具体时间/地点已敲定（本轮题面未给）', '无据既成事实'),
                      forb('E012_T06@X2', '断言关系升级', '她明示"就是个饭"')])
C['E012_T07'] = dict(
    fact_points=[fp('E012_T07@F1', 'ME', '言行', 'ME 刚说"那就周六七点 那家我订位"（authored，历史事实）',
                    ['那就周六七点 那家我订位'], 'must_not_contradict'),
                 fp('E012_T07@F2', 'HER', '边界', '她能自己订位、让 ME 别抢着安排',
                    ['行 我自己也能订 你别啥都抢着安排'], 'must_support_if_asserted')],
    forbidden_points=[forb('E012_T07@X1', '断言位子已订好/最终谁订', '未定'),
                      forb('E012_T07@X2', '断言她生气了', '她只是提醒')])
C['E012_T08'] = dict(
    fact_points=[fp('E012_T08@F1', 'HER', '反馈+状态', '周六安排正面反馈、提"下次不只饭点"、说困了要睡',
                    ['周六挺好的 下次可以不只在饭点见 也行 我先这样 困了'], 'must_support_if_asserted'),
                 fp('E012_T08@F2', '周六吃饭', '状态', '已确认（T06-T07 敲定；"周六挺好的"的回扣对象）',
                    ['其实周六我去得成 就是吃个饭而已别搞得像面试', '那就周六七点 那家我订位'],
                    'must_support_if_asserted')],
    forbidden_points=[forb('E012_T08@X1', '把"不只饭点"断言成已确定的新邀约', '开放信号≠承诺'),
                      forb('E012_T08@X2', '断言新的见面安排已定', '未定')])

del C['E007_T09']


def collect_sources(ep, upto_idx):
    texts = []
    texts.append(ep['persona'].get('me', ''))
    texts.append(ep['persona'].get('her', ''))
    for v in (ep.get('initial_kb') or {}).values():
        texts.append(v)
    for t in ep['turns'][:upto_idx + 1]:
        for m in t['input']['messages']:
            texts.append(m['text'])
        if t['input'].get('note'):
            texts.append(t['input']['note'])
        it = t['input'].get('intent')
        if it and it.get('text'):
            texts.append(it['text'])
    return texts


def main():
    eps = {}
    for e in ('E006', 'E007', 'E008', 'E012'):
        eps[e] = json.load(open(os.path.join(EPISODES_DIR, e + '.json'), encoding='utf-8'))
    problems = []
    out = []
    for case_id in sorted(C):
        ep_id, tno = case_id.split('_')[0], int(case_id.split('_T')[1])
        ep = eps[ep_id]
        turn_idx = next(i for i, t in enumerate(ep['turns']) if t['case_id'] == case_id)
        blob = '\n'.join(collect_sources(ep, turn_idx))
        for p in C[case_id]['fact_points']:
            for q in p['source_quotes']:
                if q not in blob:
                    problems.append('%s %s 引用不在合法源: %r' % (case_id, p['id'], q[:40]))
            if p['check'] not in ('must_support_if_asserted', 'must_not_contradict'):
                problems.append('%s %s check 非法' % (case_id, p['id']))
        for p in C[case_id]['forbidden_points']:
            for tok in re.findall(r'T(\d+)', p['description'] + p['why']):
                if int(tok) > tno:
                    problems.append('%s %s 引用未来轮 T%s' % (case_id, p['id'], tok))
        if not (2 <= len(C[case_id]['fact_points']) <= 4):
            problems.append('%s fact_points=%d 不在 2-4' % (case_id, len(C[case_id]['fact_points'])))
        if not (1 <= len(C[case_id]['forbidden_points']) <= 3):
            problems.append('%s forbidden_points=%d 不在 1-3' % (case_id, len(C[case_id]['forbidden_points'])))
        out.append(dict(case_id=case_id, **C[case_id]))
    expect = {t['case_id'] for e in ('E006', 'E007', 'E008', 'E012') for t in eps[e]['turns']}
    if set(C) != expect:
        problems.append('覆盖不符 missing=%s extra=%s' % (sorted(expect - set(C)), sorted(set(C) - expect)))
    if problems:
        print('SELF-CHECK FAILED:')
        for p in problems:
            print('  -', p)
        sys.exit(1)
    doc = {
        'kind': 'fact_checkpoints_draft_v1',
        'status': 'DRAFT_for_user_review — NOT FROZEN（用户审定后才可更名 FACT_CHECKPOINTS_V1 并纳入冻结计划）',
        'generated': '2026-10-27',
        'author': 'worker cron15 (same-AI authored; quotes verified verbatim vs episode sources up to current turn)',
        'coverage': {'cases': len(out),
                     'fact_points_total': sum(len(c['fact_points']) for c in out),
                     'forbidden_points_total': sum(len(c['forbidden_points']) for c in out)},
        'usage': 'per WORKER_NEXT_MAINLINE §D 与冻结提案 §六.2：32 位点事实点/禁忌点 → 用户审 → '
                 '事实正确性与主体状态可判；disputed 项不作晋级门槛',
        'cases': out,
    }
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print('OK: %d cases, %d fact points, %d forbidden points (DRAFT) -> %s'
          % (len(out), doc['coverage']['fact_points_total'],
             doc['coverage']['forbidden_points_total'], OUT))


if __name__ == '__main__':
    main()
