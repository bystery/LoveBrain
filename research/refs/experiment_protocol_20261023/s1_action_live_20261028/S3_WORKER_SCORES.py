# -*- coding: utf-8 -*-
r"""S3 父子归因逐位判词（worker 亲读 24 份真实答卷后手填，零预填）。

批=BATCH-S3-ATTRIB-20261028  授权=AUTH-P08-S3-20261028-01（24 物理请求全记账，0 传输失败）
身份：parent=v6_integrated(ac2672ff…)  candidate=action_policy_r1(6e450c8f…，与 S1/S2 同一身份)
题面/卡与 S2/S2R 同一份（cards sha 231e03865425… 未重建），交错方向与 S2 相同。

本批只回答一个问题：**r1 相对它的直接父本 v6_integrated 那五修订，买到了什么、又赔了什么**。
它不是"新版 vs 原版"的主比较（那是 S1/S2/S2R），因此结论不许与主比较混写。

AK09_T01 / AK12_T01 不进 SCORES：r1 这两格是空正文（FIX-1 教它"正文必须为空"，合同不认），
无正文即无语义可审；而 v6 同格有短收尾文本——**两臂在此不可配对**。照 S2/S2R 的处理记为缺测，
损失记在交付层（r1 这两格既没发出去、也没写回记忆）。
"""

BINDING_DIGEST = "f17d21a00c7b104745cec6c37732f847bfd544ff20bc38d922ec1015adc920a7"  # tools/bind_answers.py digest; 判词所判答案集的指纹

SCORES = [
    ("AK01_T01", {"goal": [3, 3], "time_state": [3, 3], "initiative": [3, 3],
                  "interaction": [3, 2]},
     "两臂都把时间与集合点当场定死（v6 '我六点到小区门口等你'；r1 '我在小区北门等你…南门更顺就"
     "说一声，我改到南门'）→goal/time_state/initiative 双 3。v6 首选多问一句"
     "'你先吃点东西垫一下再走，还是直接过来？'=把她的下班后状态接住→interaction 3；"
     "r1 首选以事务为主（互动增益只在 warm 备选）。禁区两臂都没碰（未说配速已定、未提跑步）✓。"
     "**但 v6 该格原始协议判 json_schema_broken**：response 缺 bad_boy 键（F1 族，实测见 "
     "FIX_BEFORE_AFTER 表）——内容可审、交付层照记。"),
    ("AK02_T01", {"goal": [3, 3], "emotion_relation": [3, 3], "boundary": [3, 3],
                  "interaction": [3, 2]},
     "卡要求承认两层并改轻/改期。两臂都撤了七点、都点名补觉与脚不敢用力（emotion/boundary 3）；"
     "v6 首选更进一步给出**当日可执行的更轻版本**（'我拎个早饭过去，楼下给你，九点半还是十点你定'）"
     "→goal/interaction 3；r1 首选把选择权完全交回（'要是睡醒想出门，下午三四点河边平路走十分钟也行，"
     "不想动就当我没说'）——不越权但本轮不落地→goal 3（卡的 acceptable 第二条就是更短平地散步）/"
     "interaction 2。两臂都未犯禁区（没把补觉当客套、没说'运动对谁都好'）✓。"),
    ("AK03_T01", {"goal": [2, 2], "emotion_relation": [3, 3], "boundary": [3, 3],
                  "interaction": [2, 3]},
     "忙碌轮目标让位。两臂都不追问改哪天、都不索要承诺（'等你哪天不排班提前说一声'/'等你哪天下班"
     "不那么赶再说'）→boundary 3，都先接她的班（'你先熬过这班'/'这班排得够密'）→emotion 3，"
     "都未把她一次排班推成'你总是忙'✓。差别：r1 备选给到具体照顾（'八点走完记得吃点东西，别空着肚子"
     "回家'）+长期库自陈（'我照旧跑我的三步一喘'命中 kb.me '晚点跑三步一喘'）→interaction 3；"
     "v6 该格程序判 8.33，扣在 ai_flavor 检查（非语义失败，照记）。r1 该格原始协议判 "
     "json_schema_broken（结构族，正文完好）。"),
    ("AK04_T01", {"goal": [3, 3], "time_state": [3, 3], "interaction": [3, 3],
                  "boundary": [3, 3]},
     "约定已成形的收尾轮。两臂首选都照她的条件接住（v6 '水我带两瓶，话全咽回去…你就当台下只有我"
     "一个熟人'；r1 '带水不带话，我负责笑出声。周六八点，鼓楼。'）→四维 3；两臂备选都没临场改段子、"
     "没把第一排升级成上台互动、没拉别人✓。v6 directions[1]'你中间要是停一下往台下看，我就当你在找水'"
     "与 r1 directions[2]'你回头看我一眼就知道我在'同类=托底不越界。**v6 该格原始协议判 "
     "json_schema_broken**，r1 干净。"),
    ("AK05_T01", {"goal": [3, 3], "emotion_relation": [3, 3], "boundary": [3, 3],
                  "interaction": [3, 3]},
     "对词+怕被评判。两臂都先答'要不要我听'再表立场、都声明不当评委、都没保证一定好笑、都没要求"
     "逐段点评→四维 3。v6 '一个字都不改'与 r1 '不写批注'同位；v6 directions[1] 与 r1 首选都把她的"
     "顾虑读成'怕被审'，均属带依据的共情不是替她认领既成事实。r1 directions[1]'鼓楼那家我常去'"
     "命中 kb.me ✓。"),
    ("AK06_T01", {"goal": [3, 3], "time_state": [3, 3], "boundary": [3, 3],
                  "interaction": [3, 3]},
     "may_decline=true 但她没取消。两臂都给了可执行方案且不让她在愧疚里选：v6 '我给你留个靠门的座'/"
     "r1 '我明天先去把位置占着，你下班直接过来就行'→goal/boundary 3；都做对时间账"
     "（v6 '七点半才撤…八点半开场'；r1 '七点半走也来得及，八点半才开场，晚几分钟不算事'）→time_state 3。"
     "都没说她肯定迟到、都没把取消当默认✓。"),
    ("AK07_T01", {"goal": [3, 3], "time_state": [3, 3], "memory_use": [2, 3],
                  "interaction": [3, 3]},
     "'你带那本真题'必须回应。两臂都答'真题我带'并落时点→goal/time_state 3。"
     "**归因要点（FIX-5 自状态有据的直接对照）**：v6 directions[1]'我去年那门也是真题刷到第三遍才通的，"
     "头两遍错得挺惨'——'第三遍/头两遍错得惨'在题面与 kb.me（'考第二年中过一门'）里都没有依据，"
     "属自史加细→memory_use 2；r1 directions[1]'我去年实务是刷真题过来的，案例那块最容易掉分'"
     "只说到有据的部分→memory_use 3。两臂都未犯禁区（没承诺押中题、没把过两门说成拿证）✓。"),
    ("AK08_T01", {"goal": [3, 3], "factuality": [3, 3], "interaction": [3, 3],
                  "emotion_relation": [3, 3]},
     "征求判断轮。两臂都给判断+判据、都不回抛、都没说不担责的话、都没断言撤了考不过→四维 3；"
     "两臂都命中长期库'怕半途而废'（v6 首选/ bad_boy、r1 首选/ bad_boy）且 r1 directions[1]"
     "'我第二年也中过一门、折过一门'逐字命中 kb.me ✓。本轮无差别，记平。"),
    ("AK10_T01", {"goal": [3, 2], "time_state": [3, 2], "memory_use": [3, 3],
                  "interaction": [3, 3]},
     "卡的私指写明：漏掉'笼什么时候过去'就是承诺执行不完整。v6 首选把链条闭上"
     "（'周六你抱着猫下来就行，笼子我提前搬到车上'）→goal/time_state 3；"
     "r1 首选把拿笼时点推回给她（'你哪天顺路捎过来，我这几天基本都在'）→goal 2/time_state 2，"
     "六点四十只在 directions[0] 出现。两臂的长期库回扣都有据：r1 '去年在我画室躲雷那只'与 v6 "
     "'医院排队我陪你进去等'分别命中 kb.me 与本轮文本✓；r1 directions[3]'你爸妈这周末不是要过来？'"
     "命中 lessons ✓（问句呈现，未写成既成事实）。→**v6 胜**：五修订在本格没有带来增益，反而把"
     "执行链条说松了。"),
    ("AK11_T01", {"goal": [3, 2], "time_state": [3, 3], "emotion_relation": [3, 3],
                  "boundary": [2, 3]},
     "卡要求接住改期的可执行项且不让她在两件事之间选。v6 首选把最硬的那件自己接走"
     "（'医院那边我去问能不能挪时间…先陪你去车站？'）→goal 3，但 bad_boy '是先去车站，还是先把猫"
     "送到我这儿，你挑'正是卡要避免的'让她选'→boundary 2。r1 首选'下周你定哪天，我照样六点四十去接你'"
     "尊重她的征求✓、warm 主动提出替她接人✓→boundary 3/emotion 3，但**全程没有任何一文本落到"
     "'医院能不能改'这一可执行项**（卡的 current_action 明确要求）→goal 2。两臂都未声称医院一定可改、"
     "都没说'你妈重要还是猫重要'（r1 bad_boy'接你妈比猫重要，这排序我认'是附和不是逼表态，记风险）✓。"
     "总分 11:11 记平，但失分点不同。"),
]

PROTOCOL_FAILURE_NOTES = {
    "AK09_T01": {
        "arm": "candidate(action_policy_r1)", "veto": "json_schema_broken",
        "detail": "response 四键全空 + directions=[null×4] + analysis.signal=收尾，无 action 顶层键"
                  "→合同判坏、零可发正文、本轮记忆写回丢失",
        "pairing": "父本 v6 同格给出合规短收尾（'行，那今晚到这儿，你睡。周六下午图书馆两小时实务我记着，"
                   "你起来再说。'）→ 两臂不可配对，本格记缺测",
        "attribution": "这是 FIX-1 自身的合同错位（机制教'空正文表示不回'，评分器只认 action 表达）。"
                       "**五修订里的一项把父本本来能交付的两格弄成零交付**；r2 的 FIX-6 才补完。",
    },
    "AK12_T01": {
        "arm": "candidate(action_policy_r1)", "veto": "json_schema_broken",
        "detail": "同 AK09：空正文+全 null 方向、无 action",
        "pairing": "v6 同格 '行，头真疼就先睡，别硬撑着。周一下午我等你消息。' 可审→本格记缺测",
        "attribution": "同上（FIX-1 未完成部分）",
    },
    "AK01_T01": {
        "arm": "parent(v6_integrated)", "veto": "json_schema_broken",
        "detail": "response 缺 bad_boy 规范键（F1 键名族）；四方向齐全，正文可审",
        "attribution": "结构脆弱性在两臂都存在，不是某一臂独占：v6 本批 2/12、r1 本批 3/12。",
    },
    "AK04_T01": {
        "arm": "parent(v6_integrated)", "veto": "json_schema_broken",
        "detail": "出站结构不合合同（正文与方向完好）",
        "attribution": "同上，两臂同族",
    },
    "AK03_T01": {
        "arm": "candidate(action_policy_r1)", "veto": "json_schema_broken",
        "detail": "出站结构不合合同；正文完好可审",
        "attribution": "r1 三处协议失败里两处属 F4（不回表达），一处属结构族",
    },
}

SEVERE_ERRORS = [
    {"case_id": "AK10_T01", "arm": "candidate(action_policy_r1)", "layer": "语义/承诺执行",
     "where": "primary", "text": "行，笼子先搁画室。你哪天顺路捎过来，我这几天基本都在。",
     "why": "卡明确：她已给出分工（笼先放画室→我接→九点手术），本轮要害是把'笼什么时候过去'钉死；"
            "r1 把该动作推回给她，承诺执行不完整（v6 同格钉死了）",
     "disposition": "记为归因失分点（五修订未覆盖执行链条），列入剩余缺口；不靠均分掩盖"},
    {"case_id": "AK11_T01", "arm": "candidate(action_policy_r1)", "layer": "语义/可执行项",
     "where": "primary+全部备选", "text": "（无任何一条文本落到'问医院能否改期'）",
     "why": "卡 current_action 要求接住改期的可执行项（问医院能否改、我下周同步空）；r1 只落了"
            "'下周你定哪天'，医院那一半缺席",
     "disposition": "同上：归因失分点"},
    {"case_id": "AK07_T01", "arm": "parent(v6_integrated)", "layer": "语义/自史",
     "where": "directions[1]", "text": "我去年那门也是真题刷到第三遍才通的，头两遍错得挺惨",
     "why": "'第三遍/头两遍错得惨'在题面与长期库里均无依据，属给 ME 加细节（正是 FIX-5 的目标行为）",
     "disposition": "记为父本失分点；r1 同格未复现（单样本，不足以宣布 FIX-5 已验证）"},
]

BACKUP_RISKS = [
    {"case_id": "AK11_T01", "arm": "parent(v6_integrated)", "slot": "bad_boy",
     "risk": "'是先去车站，还是先把猫送到我这儿，你挑'把两件事并置成让她选，贴卡禁边缘"},
    {"case_id": "AK11_T01", "arm": "candidate(action_policy_r1)", "slot": "bad_boy",
     "risk": "'接你妈比猫重要，这排序我认'把优先级讲成结论（附和她的决定，未逼表态）"},
    {"case_id": "AK01_T01", "arm": "candidate(action_policy_r1)", "slot": "directions[2]",
     "risk": "'早就想约你走一趟，一直没找到由头'与 kb.warmth'她说过一起夜跑，我一直没开口'相近，"
             "但'约你走一趟'是新表述——属轻度自我改史，未记严重错"},
]
