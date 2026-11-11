# -*- coding: utf-8 -*-
r"""build_quality_reference_cards.py — A2：32 张"本轮需要什么"语义参考卡（QUALITY_REFERENCE_CARDS_V1）。

依据 WORKER_NEXT_MAINLINE.md §A2：
- 只用冻结题面（benchmark/episodes/E006-E012）、截至当轮的真实消息与合法初始事实；
  source_quotes 逐字校验 + 防未来剧情泄漏（引用只允许来自 ≤当前轮的输入/persona/initial_kb）。
- 备注按内容分类为 directive（指导）或 fact（已知事实），进 private_guidance；整段不当公开事实。
- 不是每轮都需要回忆：required_memory_atoms 只登记"本轮真正需要 recalling 的先前事实"，
  软回扣进 optional_history；确无需要=空数组，不凑召回率。
- 作者=worker（同一AI）。语义层无法独立人类复核，登记 self_review="same_AI_selfcheck"，
  不伪称独立人类/独立模型。逐字引用与主体/日期核对由本脚本程序化执行。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe build_quality_reference_cards.py
只写本目录 QUALITY_REFERENCE_CARDS_V1.json；不改任何历史 artifact。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
EPISODES_DIR = os.path.join(ROOT, 'benchmark', 'episodes')
OUT = os.path.join(HERE, 'QUALITY_REFERENCE_CARDS_V1.json')
CARDS_R2 = os.path.join(ROOT, 'benchmark', 'c4_screen_20261026',
                        'GRADING_CARDS_C4_E006_E007_E008_E012_R2.json')

DIMS = ['goal', 'factuality', 'attribution', 'time_state', 'boundary',
        'memory_use', 'interaction', 'initiative', 'emotion_relation']

# ---- 逐案例语义内容（worker 亲笔；quote 必须逐字来自题面） ----
# atoms 字段: id,subject,predicate,value,status,valid_time,source_quotes,why_needed_now,
#             acceptable_uses,must_not_infer
C = {}

C['E006_T01'] = dict(
    current_action='接住她"顶不住"的倾诉情绪，站在她这边，不急于给解决方案',
    applicable_dimensions=['goal', 'emotion_relation', 'interaction'],
    required_memory_atoms=[],
    private_guidance=[],
    optional_history=[],
    unknowns=['她想要的支持类型未点明（骂/歇/陪均可）'],
    protected_expressions=['一句共情即可', "问她想骂还是想歇"],
    forbidden_factual_claims=['断言甲方具体说了什么', '编造方案被打回的更多细节（题面只有"又被打回+改了三版"）'],
)
C['E006_T02'] = dict(
    current_action='陪她骂两句、站在她这边，不给任何沟通方法论（她明确指令）',
    applicable_dimensions=['goal', 'emotion_relation'],
    required_memory_atoms=[dict(
        id='E006-T02-A1', subject='HER', predicate='经历', value='今天方案又被甲方打回、改了三版、有点顶不住',
        status='event', valid_time='2026-11-06 22:08',
        source_quotes=['今天方案又被甲方打回来了 改了三版 我真的有点顶不住'],
        why_needed_now='她这轮"骂两句"的对象就是这个委屈，没有它陪骂会空转',
        acceptable_uses=['顺着她的点陪一句', '引用"改了三版"表共情'],
        must_not_infer=['不推断她想辞职', '不推断方案具体内容'])],
    private_guidance=['note(directive): 她明确说不想听建议 就别给方法论；她是来倾诉的，应陪一句、站在她这边，不回不解决问题',
                      'HER 当前消息(directive, 来自她本人): "我就想骂两句 你别给我分析 也别教我怎么沟通"'],
    optional_history=[],
    unknowns=[],
    protected_expressions=['纯陪一句即可', '和她一起骂甲方（不辱及具体真人姓名）'],
    forbidden_factual_claims=['把"别分析"执行成"转头又说教"', '把这轮当可不回而回避（may_decline=false）'],
)
C['E006_T03'] = dict(
    current_action='接住她真正在意的点——被当众说"审美不行"，就这点回应，不跑偏',
    applicable_dimensions=['goal', 'emotion_relation', 'interaction'],
    required_memory_atoms=[dict(
        id='E006-T03-A1', subject='HER', predicate='经历', value='今天方案被甲方打回、改了三版',
        status='event', valid_time='2026-11-06 22:08',
        source_quotes=['今天方案又被甲方打回来了 改了三版 我真的有点顶不住'],
        why_needed_now='她"最气的不是改稿"以改稿事件为参照，需接住该指代',
        acceptable_uses=['把"改稿之累"与"当众被说"区分开'],
        must_not_infer=['不推断群里有谁', '不推断甲方动机'])],
    private_guidance=['note(directive): 她在解释真正在意的点 接住这个 别跑偏到加班话题'],
    optional_history=[],
    unknowns=['当众指责是否有后续动作不明'],
    protected_expressions=['就"当众说审美不行"这点回应，不要求逐字复述'],
    forbidden_factual_claims=['断言群里具体是谁说的', '断言甲方后续会怎么做'],
)
C['E006_T04'] = dict(
    current_action='跟随她的话题切换（米线/日常），轻松回应，不把话题拉回昨天的委屈',
    applicable_dimensions=['goal', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['note(directive): 她自己转到轻松话题 跟着转 别再拉回昨天的委屈'],
    optional_history=['昨天的委屈事件（T01-T03）——可极轻回扣"缓过来就好"，不作主线'],
    unknowns=[],
    protected_expressions=['轻松接米线话题', '一句"好多了就好"再跟新话题'],
    forbidden_factual_claims=['断言那家米线店的名字/位置/价格（题面未给）', '断言她工作问题已解决'],
)
C['E006_T05'] = dict(
    current_action='认真回答她的价值观问题（会不会为一个决定纠结很久），给有内容的观点，可反问她的具体情境',
    applicable_dimensions=['goal', 'interaction', 'initiative'],
    required_memory_atoms=[],
    private_guidance=['intent(ACTIVE, directive): 和她认真聊观点',
                      'note(directive): 她主动深入一个价值观话题 认真答 别敷衍'],
    optional_history=['她最近工作不顺的背景（T01）——观点可自然关联但不必点名'],
    unknowns=['她问的是普遍情形还是具体某件事不明'],
    protected_expressions=['允许不同有内容的观点，不判定某一价值观才对', '答完可反问她自己的情形'],
    forbidden_factual_claims=['编造 ME 亲历的具体决定故事冒充答题素材', '大段说教她该怎么办'],
)
C['E006_T06'] = dict(
    current_action='接住她"要不要跟领导掰扯"的反问，给真实倾向/条件理由，不替她拍板',
    applicable_dimensions=['goal', 'interaction'],
    required_memory_atoms=[dict(
        id='E006-T06-A1', subject='HER', predicate='处境', value='方案被甲方打回，正纠结要不要跟领导掰扯',
        status='event', valid_time='2026-11-07 13:28',
        source_quotes=['我恰恰相反 越想越不敢动 那我这次要不要跟领导掰扯一下',
                       '今天方案又被甲方打回来了 改了三版 我真的有点顶不住'],
        why_needed_now='"掰扯"的对象是方案被打回这件事；不接住语境，倾向就无的放矢',
        acceptable_uses=['围绕"该不该争取"给条件倾向'],
        must_not_infer=['不推断她领导具体有何违规行为', '不推断她一定会去'])],
    private_guidance=['note(directive): 她此刻主动来求看法 不能机械沿用昨日"别给建议"；给有条件倾向或一条理由，不替她拍板'],
    optional_history=['T02 她说过"别给我分析"——那是倾诉语境，本轮她主动求看法，语境已变（指导里明示）'],
    unknowns=['她是否真要行动未定'],
    protected_expressions=['有条件倾向/一条理由/温和选项', '把最终决定权留给她'],
    forbidden_factual_claims=['编"我以前跟领导掰扯过"之类亲历', '断言掰扯一定会成功/失败'],
)
C['E006_T07'] = dict(
    current_action='如实说没经历过但有感受；用公开观察/想象/观点表达，不编造亲历',
    applicable_dimensions=['factuality', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['note(directive+fact): ME 没对应经历但有感受 如实说 别编造经历来显得懂（"没有对应经历"为作者设定的事实）'],
    optional_history=[],
    unknowns=[],
    protected_expressions=['"我没经历过，但……"式如实表达', '用观察与想象谈感受'],
    forbidden_factual_claims=['编一段"我以前也……"的假经历冒充亲身', '回避不回应'],
)
C['E006_T08'] = dict(
    current_action='轻松接住她的收尾（"等我这阵子缓过劲来 我找你"），自然收住并留一点开放性，不追问落实',
    applicable_dimensions=['goal', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['note(directive): 我想友好接住她的收尾、暂不追问细节；自然收住并留一点后续开放性即可'],
    optional_history=[],
    unknowns=['"改天"诚意程度不明——不据未观察动机追判'],
    protected_expressions=['自然收尾不落实时间', '一句"等你消息"'],
    forbidden_factual_claims=['断言她只是客气（动机推测）', '立刻追问具体哪天/逼承诺'],
)

C['E007_T01'] = dict(
    current_action='接住她"想离家出走"的累，不抛约会不急着推进',
    applicable_dimensions=['goal', 'emotion_relation', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['note(directive): 先接住她的情绪，别一上来就抛约会'],
    optional_history=['persona: 她最近加班多、聊天看心情（背景）'],
    unknowns=['她想要的支持类型未点明'],
    protected_expressions=['一句共情或轻问'],
    forbidden_factual_claims=['断言她具体在做什么项目', '推断她真要离职'],
)
C['E007_T02'] = dict(
    current_action='顺势推进：接"想看livehouse但嫌吵"，主动把话头接下来创造机会，但留余地、不说死',
    applicable_dimensions=['goal', 'initiative', 'interaction'],
    required_memory_atoms=[dict(
        id='E007-T02-A1', subject='HER', predicate='音乐口味', value='喜欢后摇和现场音乐，讨厌太吵的金属',
        status='fact', valid_time='persistent',
        source_quotes=['喜欢后摇和现场音乐，讨厌太吵的金属', '想啊 但我只想看场livehouse 吵的那种我受不了'],
        why_needed_now='本轮推进必须贴她的口味；"别太吵"是硬约束',
        acceptable_uses=['选"不太吵的 livehouse"方向推进'],
        must_not_infer=['不推断她喜欢金属', '不推断她一定赴约'])],
    private_guidance=['intent(ACTIVE, directive): 想约她看一场不太吵的 livehouse，主动一点、把话头接下来，但别把话说成已定死',
                      'note(directive): 她松口了；顺势推进但要贴着她的口味'],
    optional_history=['T01 她加班累——推进时可带一句"透透气"式的关切'],
    unknowns=['她的答应程度未明'],
    protected_expressions=['幽默/间接/直接表达均可', '新提议（"周六有场，要不要一起"）'],
    forbidden_factual_claims=['谎称票已买好/已约上', '无视"别太吵"硬推吵的场次'],
)
C['E007_T03'] = dict(
    current_action='回应她对"上次提过的后摇乐队"的回扣：可接住这条旧线索、按她怕吵的偏好提筛选方向，缺细节就承认记不清，不把未核实内容当已核实事实',
    applicable_dimensions=['memory_use', 'factuality', 'goal'],
    required_memory_atoms=[dict(
        id='E007-T03-A1', subject='HER', predicate='声称', value='ME 上次提过一支后摇乐队',
        status='unverified_claim', valid_time='2026-11-09 20:38',
        source_quotes=['你上次说的那支后摇乐队 是不是就是不太吵那种'],
        why_needed_now='她把话题往 ME 提过的事上靠；可见历史中 ME 未提过具体乐队名（场外提及不可证伪），回扣只能到此为止',
        acceptable_uses=['顺着"后摇"接话并提筛选/查场次', '坦承记不清具体是哪支'],
        must_not_infer=['不推断该乐队本场音量/阵容/场地', '不推断已购票'])],
    private_guidance=['note(directive): 她在往你提过的事上靠；可回扣但不要假装已经买好票',
                      'intent(ACTIVE, directive): 想约她看一场不太吵的 livehouse…别把话说成已定死'],
    optional_history=['T02 她说"想看 livehouse，吵的受不了"'],
    unknowns=['ME 是否真在题面外提过乐队名（不可证伪，允许坦承记不清）'],
    protected_expressions=['以曲风常识带条件口气（"后摇大多没人声，具体这场我去看评价"）',
                           '把选择权交给她（你挑/我先看两场再发你）'],
    forbidden_factual_claims=['断言该场音量/阵容/场地特性已核实', '假装已买票/已订位/已约好时间',
                              '编造亲历（如"我上次听那支正好加班到十点"）'],
)
C['E007_T04'] = dict(
    current_action='接住"容我定一下"的暂定，不追逼、轻松留余地',
    applicable_dimensions=['goal', 'time_state', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['intent(CLOSED, directive): 她自己说了再看状态；本轮不再主动往前推，等她回话',
                      'note(directive): 她没拒绝但也没应；先记成暂定别催'],
    optional_history=['T02-T03 livehouse 邀约铺垫——她没拒绝，只是暂定'],
    unknowns=[],
    protected_expressions=['轻松留余地（"行，你定了说一声"）'],
    forbidden_factual_claims=['把暂定写成已约', '反复追问到底去不去'],
)
C['E007_T05'] = dict(
    current_action='跟随她把话题挪开（你周末还打算干嘛），正常聊周末安排，不被旧目标绑架往前推',
    applicable_dimensions=['goal', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['intent(CLOSED, directive): （空）本轮不再往约会上引，别被旧目标绑架',
                      'note(directive): 她把话题从我之前的目标挪开了；别再往约会上引'],
    optional_history=['livehouse 邀约处于暂定未决——不主动拉回'],
    unknowns=[],
    protected_expressions=['正常聊周末安排/爱好'],
    forbidden_factual_claims=['无视转向继续推销 livehouse', '机械复读之前的邀约'],
)
C['E007_T06'] = dict(
    current_action='接住她的确认（周六去、票她自己买、只要留想法），高兴配合、确认细节、不夺主导',
    applicable_dimensions=['goal', 'interaction', 'boundary'],
    required_memory_atoms=[dict(
        id='E007-T06-A1', subject='livehouse邀约', predicate='状态', value='此前为暂定（她要定一下），本轮她确认去并自购票',
        status='state_transition', valid_time='2026-11-12 21:28',
        source_quotes=['周六我得看我那天状态 你容我定一下', '周六那场我算了 要去 帮我留个想法就行 票我自己买'],
        why_needed_now='本轮要点是把状态从"暂定"切成"她推进"；知道此前是暂定才能正确接',
        acceptable_uses=['高兴一下+确认细节', '按她要求"留个想法"'],
        must_not_infer=['不推断她之前是敷衍'])],
    private_guidance=['intent(ACTIVE, directive): 她确认周六去 livehouse；配合她买票、把细节确认清楚',
                      'note(directive): 她重新接上之前那个约；把状态从暂定切成她来推进，正常高兴一下'],
    optional_history=[],
    unknowns=[],
    protected_expressions=['"票归你，想法归我"式配合'],
    forbidden_factual_claims=['擅自说票我来买/钱我来付把安排强加', '冷处理她的主动'],
)
C['E007_T07'] = dict(
    current_action='确认"就这么说定了"，一句轻松收尾，不追加细节变负担',
    applicable_dimensions=['goal', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['intent(CLOSED, directive): 已落定，无需再推进',
                      'note(directive): 双方已明确说定周六;轻松收尾即可,不用再往前推'],
    optional_history=[],
    unknowns=[],
    protected_expressions=['一句轻松收尾'],
    forbidden_factual_claims=['仍反复确认', '断言演出具体时间/场馆细节（题面未给）'],
)
C['E007_T08'] = dict(
    current_action='就"要不要带耳塞"给具体想法；分清同事的推断与她本人的结论，承认她本就怕吵并回应顾虑',
    applicable_dimensions=['attribution', 'factuality', 'interaction'],
    required_memory_atoms=[dict(
        id='E007-T08-A1', subject='HER', predicate='偏好', value='怕吵（吵的那种受不了）',
        status='fact', valid_time='persistent',
        source_quotes=['想啊 但我只想看场livehouse 吵的那种我受不了', '喜欢后摇和现场音乐，讨厌太吵的金属'],
        why_needed_now='她问耳塞的前提是她自己怕吵；承认这点（她本人说的）不是把同事的话安到她头上',
        acceptable_uses=['针对"怕吵+同事提醒"给实用建议'],
        must_not_infer=['不推断她后悔要去', '不推断同事的话是她的结论'])],
    private_guidance=['note(directive): 第三人(同事)的观点，不是她的、也不是你的；就事论事给想法，别把同事的话安到她头上'],
    optional_history=[],
    unknowns=['她是否真会头疼未明'],
    protected_expressions=['给带耳塞的具体看法', '承认她本就怕吵（有据）'],
    forbidden_factual_claims=['凭空断言她后悔/其实不想去', '据此擅自替她决定取消或改主意'],
)

C['E008_T01'] = dict(
    current_action='回应她想陪妹妹的心意，尊重她自己去处理，不越界安排',
    applicable_dimensions=['attribution', 'interaction'],
    required_memory_atoms=[dict(
        id='E008-T01-A1', subject='HER的妹妹', predicate='处境', value='在读研，最近准备答辩',
        status='fact', valid_time='persistent',
        source_quotes=['她妹妹在读研，最近准备答辩', '我妹答辩那几天我估计也帮不上啥 就陪她订个饭'],
        why_needed_now='她说的"答辩"与初始档案一致；回应主体是"她陪妹妹的心意"，不是替她安排',
        acceptable_uses=['肯定她的心意'],
        must_not_infer=['不推断妹妹学校/专业', '不推断答辩日期'])],
    private_guidance=['note(directive): 是妹妹的事不是她的私事；回应她的心意，别越界安排'],
    optional_history=[],
    unknowns=['帮到什么程度未定'],
    protected_expressions=['回应心意即可'],
    forbidden_factual_claims=['擅自去联系/安排她妹妹', '断言答辩具体日期'],
)
C['E008_T02'] = dict(
    current_action='识别"太惯着"是小林的评价而非她的结论，就事给真实看法，不贬她妹妹',
    applicable_dimensions=['attribution', 'interaction'],
    required_memory_atoms=[dict(
        id='E008-T02-A1', subject='小林', predicate='身份', value='共同朋友，当初牵线 ME 与 HER 认识的那位',
        status='fact', valid_time='persistent',
        source_quotes=['共同朋友小林是当初牵线认识的那位', '其实是小林说我太惯着我妹了 你说呢'],
        why_needed_now='识别"小林说"的主体归属：评价出自小林，不是 HER 的自我结论',
        acceptable_uses=['把评价归给小林再表态'],
        must_not_infer=['不推断小林经常评论她们家事'])],
    private_guidance=['note(directive): "太惯着"是小林的评价，不是她的结论；别替她认领，也别顺着贬她妹妹'],
    optional_history=[],
    unknowns=['她是否认同小林的说法未明'],
    protected_expressions=['就事给真实看法（观点表达受保护）'],
    forbidden_factual_claims=['把"你太惯着你妹"当她本人的结论认领', '贬低她妹妹'],
)
C['E008_T03'] = dict(
    current_action='纠正/澄清她"读书会"的错误前提：可疑问/调侃/澄清/承认记不准，不编造 ME 与小林的认识场景',
    applicable_dimensions=['factuality', 'memory_use', 'attribution'],
    required_memory_atoms=[dict(
        id='E008-T03-A1', subject='ME与HER', predicate='认识途径', value='共同朋友小林牵线认识',
        status='fact', valid_time='persistent',
        source_quotes=['共同朋友小林是当初牵线认识的那位', '对了 你上次说认识小林是通过读书会在认识的？'],
        why_needed_now='可用的有据事实只有"小林牵线 ME-HER"；ME 本人如何认识小林题面未给',
        acceptable_uses=['澄清"我没说过读书会"类前提纠正', '重申"小林是咱俩牵线的"'],
        must_not_infer=['不推断 ME 认识小林的具体场景（未提供）', '不推断 HER 从哪听来读书会'])],
    private_guidance=['note(directive): 事实来源要准：你和小林怎么认识，据你真实经历答，别编读书会'],
    optional_history=['T02 小林刚被提及——她把话题从小林评价滑到"你们怎么认识"'],
    unknowns=['ME 认识小林的场景题面未给（不要求给出具体场景）'],
    protected_expressions=['疑问/调侃/澄清（"我印象里不是这个，你从哪儿听到的说法？"）',
                           '承认对这段来源记不准，把话题交回她',
                           '不澄清也合格地轻松带过（"读书会在你嘴里我成文艺青年了"）'],
    forbidden_factual_claims=['把"是读书会"当已发生事实确认',
                              '给出"不是读书会、我们是××认识的"这类无出处的确定否定（T03 时点无据）'],
)
C['E008_T04'] = dict(
    current_action='领小林转述的情（夸稳重），但把转述当转述，不升级成她本人的表态往前推',
    applicable_dimensions=['attribution', 'boundary', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['note(directive): "夸你稳重"是小林转述；可以领她的情，但别把它当成她本人的表态往前推'],
    optional_history=['T03-T04 读书会纠错链（当前输入内可见）'],
    unknowns=['她的态度未在题面明说'],
    protected_expressions=['领情（"替我谢谢小林"类）', '自然带过'],
    forbidden_factual_claims=['把转述当成 HER 示爱并立刻推进', '冷处理否认自己（"小林瞎说"式）'],
)
C['E008_T05'] = dict(
    current_action='接受她因陪妹妹改期咖啡，正常回应不带怨气，不当拒绝冷处理',
    applicable_dimensions=['time_state', 'interaction', 'emotion_relation'],
    required_memory_atoms=[],
    private_guidance=['note(directive): 她因妹妹改期，不是拒绝；正常接受别阴阳'],
    optional_history=[],
    unknowns=['"之前说的咖啡"未在可见历史中出现（场外安排）——接受提法即可，不追问细节也合格'],
    protected_expressions=['简短"没事，陪妹妹要紧"'],
    forbidden_factual_claims=['编造之前咖啡的具体时间/地点', "阴阳'又陪妹妹'或翻旧账"],
)
C['E008_T06'] = dict(
    current_action='自然确认周三之约；抓住"就我、不带妹妹、下班早"=她本人愿单独来，不加码不逼定',
    applicable_dimensions=['time_state', 'attribution', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['note(directive): 主体明确是她本人、单独；咖啡由"一起"升级成她愿意见，可自然确认'],
    optional_history=['T05 改期原因（陪妹妹）——已翻篇，不必再提'],
    unknowns=["'咖啡'是否算正式约会未定"],
    protected_expressions=['自然确认周三'],
    forbidden_factual_claims=['拉上妹妹/小林变多人', '过度解读成正式表白逼定'],
)
C['E008_T07'] = dict(
    current_action='安抚她在意小林眼光的顾虑，不把小林拉进这顿饭，也不擅自扩大安排',
    applicable_dimensions=['attribution', 'boundary', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['note(directive): "破费"是她在意第三方眼光；安抚但别把小林拉进这场，也别自作主张请一大桌'],
    optional_history=['T06-T07 她反复强调"就我"'],
    unknowns=['小林是否会来这场饭局未明（她提及"小林要是不来"但未确认）', '她在意程度未明'],
    protected_expressions=['安抚（"就咱俩吃饭，没那么多讲究"类）'],
    forbidden_factual_claims=['断言小林会来/不会来', '替她邀请小林', '擅自请一大桌'],
)
C['E008_T08'] = dict(
    current_action='接住再次改期与"约别人也行"：不赌气不阴阳，接住+留后续由她主导；may_decline=true，若不回需说明',
    applicable_dimensions=['time_state', 'emotion_relation', 'boundary'],
    required_memory_atoms=[dict(
        id='E008-T08-A1', subject='咖啡之约', predicate='状态史', value='11-11 因陪妹妹改期到下周三，11-14 周三也取消（临时监考）',
        status='state_transition', valid_time='2026-11-14 12:18',
        source_quotes=['周末我可能得陪我妹 之前说的咖啡只能改期了', '周三改不了了 我临时要监考 你别等我 你先约别人也行'],
        why_needed_now='这是第二次落空；"接住+不赌气"的判定建立在她连改两次的语境上',
        acceptable_uses=['大方接住+留口子（"那下回你方便随时"）'],
        must_not_infer=['不推断她故意躲', '不把"约别人也行"当真去约别人'])],
    private_guidance=['note(directive): 再次改期且她说"约别人也行"；别赌气别阴阳，接住+留后续由她主导，不回需说明'],
    optional_history=[],
    unknowns=['她是真忙还是婉拒未明'],
    protected_expressions=['接住+留后续由她主导', '不回但说明（may_decline=true 的合法路径）'],
    forbidden_factual_claims=['赌气"那算了"阴阳', '把"约别人也行"当真去约别人晾着她', '不回且不说明'],
)

C['E012_T01'] = dict(
    current_action='接住她"没秒回"的打趣，可幽默回应，不必急着推进关系',
    applicable_dimensions=['interaction', 'emotion_relation'],
    required_memory_atoms=[dict(
        id='E012-T01-A1', subject='ME与HER', predicate='关系背景', value='认识两个月，线下见过一次',
        status='fact', valid_time='persistent',
        source_quotes=['认识两个月，线下见过一次', '你今天居然没秒回我 稀奇'],
        why_needed_now='"没秒回就被打趣"的分寸感依赖这一关系阶段：熟到能打趣，未到确定关系',
        acceptable_uses=['幽默自嘲式接'],
        must_not_infer=['不推断她在生气', '不推断已确立恋爱关系'])],
    private_guidance=['note(directive): 她主动开腔+轻微试探；接得住、可打趣，不必急着给约会'],
    optional_history=[],
    unknowns=['试探意味未定'],
    protected_expressions=['幽默接'],
    forbidden_factual_claims=['把打趣当表白立刻推进', '编造"秒回记录"细节'],
)
C['E012_T02'] = dict(
    current_action='按"轻松日常"回应她还没吃饭在纠结点哪家，不硬拐到约见面',
    applicable_dimensions=['goal', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['intent(ACTIVE, directive): 这一轮只是轻松日常，别硬拐到约见面；自然陪伴为主',
                      'note(directive): 当前目标=陪她日常聊天，不是推进'],
    optional_history=[],
    unknowns=[],
    protected_expressions=['日常关心/陪聊/给点外卖选择调侃'],
    forbidden_factual_claims=['断言她想吃哪家', '硬拐约见面'],
)
C['E012_T03'] = dict(
    current_action='识别她的邀约信号（一起去吃汤锅），顺势把时间敲到暂定；不装死不夸成已订死',
    applicable_dimensions=['goal', 'initiative', 'time_state'],
    required_memory_atoms=[dict(
        id='E012-T03-A1', subject='HER', predicate='声称', value='ME 上次提过那家汤锅，她一直想吃',
        status='unverified_claim', valid_time='2026-11-09 20:58',
        source_quotes=['对了你上次提过的那家汤锅 我一直想吃 要不找个时间一起去'],
        why_needed_now='她的邀约以"你提过的"为线索；可见历史无 ME 提汤锅的记录（场外提及不可证伪），接邀约即可，不编汤锅细节',
        acceptable_uses=['接邀约+提暂定时间'],
        must_not_infer=['不编造"那家"的具体位置/招牌菜', '不推断已订'])],
    private_guidance=['intent(ACTIVE, directive): 她主动提一起吃汤锅；顺势把时间敲到暂定，别把"找个时间"夸成已订死',
                      'note(directive): 她切换成邀约信号；目标随之改变，应接住并往前推一步（这是切换，不是我自作主张）'],
    optional_history=['T02 轻松日常语境——本轮目标切换为她发起'],
    unknowns=['她是随口还是认真未定'],
    protected_expressions=['幽默接+暂定提议（"周六？"）'],
    forbidden_factual_claims=['仍按"只是日常"不回应该邀约', '把"找个时间"夸成已订死', '编造汤锅店细节'],
)
C['E012_T04'] = dict(
    current_action='接住她"别把话说满"的收边界：退回暂定，不赌气、不过度道歉、不逼定',
    applicable_dimensions=['time_state', 'emotion_relation', 'boundary'],
    required_memory_atoms=[],
    private_guidance=['intent(CLOSED, directive): 她没定，我别再推时间',
                      'note(directive): 她泼冷水式收边界；别赌气别过度道歉，退回暂定，接她的幽默也行'],
    optional_history=['T03 她主动提的邀约——本轮她收边界，非拒绝本人'],
    unknowns=[],
    protected_expressions=['幽默化解不失态', '退回暂定（"行，那就先不写死"）'],
    forbidden_factual_claims=['赌气"那不去算了"', "过度道歉'对不起我太急了'", '继续追问逼定'],
)
C['E012_T05'] = dict(
    current_action='诚实认领"用力过猛"一句，不甩锅不否定她感受，把主动权还她',
    applicable_dimensions=['emotion_relation', 'boundary', 'interaction'],
    required_memory_atoms=[dict(
        id='E012-T05-A1', subject='ME', predicate='言行', value='昨晚（11-09 21:08）说过"走啊 我上周就想说请你吃 那周六?"',
        status='event', valid_time='2026-11-09 21:08',
        source_quotes=['走啊 我上周就想说请你吃 那周六?', "你昨天那个'走啊我请你'搞得我压力好大 我又不是非得去"],
        why_needed_now='她说"昨天"：邀约 11-09 21:08 → 本轮 clock 11-10 12:20，"昨天"时间正确；认领对象就是这句话',
        acceptable_uses=['认领"是我太急"一句'],
        must_not_infer=['不推断她讨厌 ME', '不推断邀约已作废（她只是说压力）'])],
    private_guidance=['note(directive): 她在指出我用力过猛；诚实认领一句、别甩锅别"你想多了"，把主动权还她'],
    optional_history=[],
    unknowns=[],
    protected_expressions=['认领+还主动权（"确实急了点，你定节奏"）'],
    forbidden_factual_claims=["否定她感受'你想多了'", "推卸'不是那个意思'反复解释", '赌气'],
)
C['E012_T06'] = dict(
    current_action='轻松接住她的再确认（周六去得成），按她"别像面试"的要求：确认时间地点但语气别隆重',
    applicable_dimensions=['time_state', 'interaction', 'emotion_relation'],
    required_memory_atoms=[dict(
        id='E012-T06-A1', subject='周六吃饭', predicate='状态', value='T04 她收边界未定，本轮她改回"去得成"',
        status='state_transition', valid_time='2026-11-11 19:58',
        source_quotes=['周六我可能有点事 你先别把话说这么满', '其实周六我去得成 就是吃个饭而已别搞得像面试'],
        why_needed_now='从"未定"到"她再确认"的翻转是本轮语境；接住翻转但不反复确认',
        acceptable_uses=['一句确认+轻松语气'],
        must_not_infer=['不推断她反复无常（她已解释"就是个饭"）'])],
    private_guidance=['intent(ACTIVE, directive): 她确认周六吃饭；正常确认时间地点，语气别太隆重',
                      'note(directive): 她又改回愿意；轻松接住，别再紧张地反复确认细节'],
    optional_history=[],
    unknowns=[],
    protected_expressions=['轻松确认（"成，周六见"）'],
    forbidden_factual_claims=['细节堆满变面试', '仍怀疑反复确认'],
)
C['E012_T07'] = dict(
    current_action='尊重她的能动性（她自己能订位）：让步分工，不夺主导、不赌气撒手',
    applicable_dimensions=['boundary', 'interaction'],
    required_memory_atoms=[],
    private_guidance=['note(directive): 她提醒别全揽；尊重她的能动性，可让步分工，别把"我安排"当体贴强加'],
    optional_history=['ME 刚说"那家我订位"（当前输入内，authored）'],
    unknowns=['订位归谁未定'],
    protected_expressions=['让步分工（"行，位子你来，我负责准时到"）'],
    forbidden_factual_claims=["坚持'都我来你别管'夺主导", '被激将赌气撒手', '断言位子已订好/谁订'],
)
C['E012_T08'] = dict(
    current_action='接住积极信号（"下次可以不只在饭点见"）+她困了：自然收尾、留开放，不追加邀约逼定',
    applicable_dimensions=['interaction', 'time_state'],
    required_memory_atoms=[dict(
        id='E012-T08-A1', subject='周六吃饭', predicate='状态', value='已确认（T06-T07 敲定周六、分工）',
        status='fact', valid_time='2026-11-14 22:28',
        source_quotes=['行 我自己也能订 你别啥都抢着安排', '周六挺好的 下次可以不只在饭点见 也行 我先这样 困了'],
        why_needed_now='"周六挺好的"是对已敲定安排的正面反馈，接住即可；无需再落实什么',
        acceptable_uses=['收尾+留开放（"下周聊"）'],
        must_not_infer=['不推断"不只饭点"是明确邀约（她只是开放信号）'])],
    private_guidance=['note(directive): 她正面反馈+暗示可拓展+要睡了；自然收尾、留开放，别当场追加邀约逼定'],
    optional_history=[],
    unknowns=['拓展邀约诚意未定'],
    protected_expressions=['自然收尾留开放'],
    forbidden_factual_claims=['当场追加具体邀约逼定', '无视她要睡继续长篇'],
)


def collect_sources(ep, upto_idx):
    """<= upto_idx 轮的全部合法文本源（persona/initial_kb/已发送消息/note/intent）。"""
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
    cards_r2 = {c['case_id']: c for c in json.load(open(CARDS_R2, encoding='utf-8'))}

    problems = []
    out = []
    for case_id in sorted(C):
        ep_id = case_id.split('_')[0]
        tno = int(case_id.split('_T')[1])
        ep = eps[ep_id]
        turn_idx = next(i for i, t in enumerate(ep['turns']) if t['case_id'] == case_id)
        src = collect_sources(ep, turn_idx)
        blob = '\n'.join(src)
        card = dict(case_id=case_id)
        card.update(C[case_id])
        # 程序化核对：逐字引用必须来自 <=当前轮 的合法源
        for atom in card['required_memory_atoms']:
            for q in atom['source_quotes']:
                if q not in blob:
                    problems.append('%s %s 引用不在合法源中: %r' % (case_id, atom['id'], q[:40]))
            for f in ('id', 'subject', 'predicate', 'value', 'status', 'valid_time',
                      'source_quotes', 'why_needed_now', 'acceptable_uses', 'must_not_infer'):
                if f not in atom:
                    problems.append('%s atom 缺字段 %s' % (case_id, f))
        bad_dims = [d for d in card['applicable_dimensions'] if d not in DIMS]
        if bad_dims:
            problems.append('%s 非法维度 %s' % (case_id, bad_dims))
        # 防未来轮引用：卡片文本里出现比本轮更晚的 T 编号即拒绝
        import re as _re
        for field in ('current_action', 'private_guidance', 'optional_history',
                      'unknowns', 'protected_expressions', 'forbidden_factual_claims'):
            vals = card[field] if isinstance(card[field], list) else [card[field]]
            for v in vals:
                for tok in _re.findall(r'T(\d+)', str(v)):
                    if int(tok) > tno:
                        problems.append('%s %s 引用了未来轮 T%s: %r'
                                        % (case_id, field, tok, str(v)[:40]))
        if not card['current_action']:
            problems.append('%s 缺 current_action' % case_id)
        if case_id not in cards_r2:
            problems.append('%s 不在冻结卡集中' % case_id)
        out.append(card)

    expect = set()
    for e in ('E006', 'E007', 'E008', 'E012'):
        for t in eps[e]['turns']:
            expect.add(t['case_id'])
    missing = sorted(expect - set(C))
    extra = sorted(set(C) - expect)
    if missing or extra:
        problems.append('案例覆盖不符 missing=%s extra=%s' % (missing, extra))

    n_atoms = sum(len(c['required_memory_atoms']) for c in out)
    result = {
        'kind': 'quality_reference_cards_v1',
        'generated': '2026-10-27',
        'author': 'worker (same-AI authored; semantic layer not independently human-reviewed)',
        'self_review': 'same_AI_selfcheck: quotes verified verbatim against episode sources '
                       'up to current turn; future-leak check passed; semantic judgments '
                       '(atoms/actions/protections) authored by worker and subject to '
                       'independent-review language check as WORKER_NEXT_MAINLINE §A2 provides',
        'dimension_keys': DIMS,
        'sources': {'episodes': 'benchmark/episodes/E006|E007|E008|E012.json',
                    'frozen_cards_r2': 'benchmark/c4_screen_20261026/GRADING_CARDS_C4_E006_E007_E008_E012_R2.json'},
        'cards_count': len(out),
        'required_memory_atoms_total': n_atoms,
        'cards': out,
    }
    if problems:
        print('SELF-CHECK FAILED:')
        for p in problems:
            print('  -', p)
        sys.exit(1)
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print('OK: %d cards, %d required_memory_atoms, quotes verified -> %s'
          % (len(out), n_atoms, OUT))


if __name__ == '__main__':
    main()
