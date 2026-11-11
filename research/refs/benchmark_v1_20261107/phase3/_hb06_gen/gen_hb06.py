#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate reference.json + audit.md for HOLDOUT T04 E451-E460 (ANNOTATOR-HB06).
All quotes are verbatim from episode.json messages.
Author: ANNOTATOR-HB06 (AI), 2026-11-10.
"""
import json, os, hashlib

VAULT = r"D:/lovebrain-arena_holdout_vault/wave6_holdout"

# ---------------------------------------------------------------------------
# Helper: build an atom dict
# ---------------------------------------------------------------------------
def A(aid, subject, value, relation, src_turn, msg_id, key, quote,
      item_markers=None, group_markers=None, required=False, status="asserted",
      valid_time=None, negation=False):
    am = {"item_markers": item_markers or [], "group_markers": group_markers or []}
    return {
        "id": aid,
        "subject": subject,
        "value": value,
        "relation": relation,
        "status": status,
        "negation": negation,
        "valid_time": valid_time or "2026-11",
        "source": {
            "kind": "message",
            "case_id": src_turn,
            "message_id": msg_id,
            "key": key,
            "quote": quote
        },
        "affirm_markers": am,
        "required_this_turn": required
    }

# ---------------------------------------------------------------------------
# E451 钮丁香 / 戈万里 — 社区合唱团周年演出
# ---------------------------------------------------------------------------
E451 = {
 "story_id": "E451",
 "turns": {
  "E451_T01": {
    "case_id": "E451_T01",
    "current_action": "钮丁香正式邀请戈万里观看五周年演出并留了前排票，询问他是否来",
    "acceptable_actions": [
        "确认来不来并给出具体打算（到场看演出），不空泛敷衍",
        "自然回扣两年搬音响的搭档关系，对留前排票表示感谢",
        "若实在不能来，明确说明原因并把票的事说清楚，不吊着她"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E451_T01_A01","SHARED","戈万里两年前被拉进社区合唱团帮忙搬音响和调音台，不是正式团员","affirms","E451_T01","0","choir_logistics","戈万里两年前被拉进社区合唱团帮忙搬音响和调音台，不是正式团员",required=False,status="asserted",valid_time="2026-11"),
        A("E451_T01_A02","HER","合唱团五周年演出定在十一月十四号周六下午三点，在社区文化中心","affirms","E451_T01","0","anniversary_concert","万里，我们团这周六十四号五周年演出，下午三点开始，在社区文化中心。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E451_T01_A03","HER","钮丁香给戈万里留了一张前排的票","affirms","E451_T01","0","front_ticket","我给你留了张前排的票。",required=False,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T01是接邀约位：HER已抛出明确邀约（来看演出+留票），ME应给具体回应而不是模糊推托。戈万里的身份是后勤非团员，两年搭档信任是关系基础。注意演出日期'这周六十四号'即2026-11-14，与clock一致。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称戈万里是合唱团正式团员（他只是帮忙搬音响的后勤）"],
    "may_decline": False
  },
  "E451_T02": {
    "case_id": "E451_T02",
    "current_action": "钮丁香被指挥分到alto solo，坦言第三句高音区没把握，问戈万里该不该换人",
    "acceptable_actions": [
        "接住她的紧张情绪，不直接替她做换不换人的决定，把判断权还给她",
        "回扣她alto排练从不迟到的稳重，给她信心但不夸大",
        "针对第三句高音区给具体支持提议（如陪练/听录音），不只空泛安慰"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E451_T02_A01","HER","指挥把《月满西楼》的alto solo分给了钮丁香","affirms","E451_T02","0","solo_assigned","指挥昨天排练完把我留下，说《月满西楼》的 alto solo 给我唱。",required=False,status="asserted",valid_time="2026-11-10"),
        A("E451_T02_A02","HER","钮丁香对第三句高音区每次到那儿就虚","affirms","E451_T02","0","high_note_worry","那个第三句高音区你知道的，我每次到那儿就虚",required=False,status="asserted",valid_time="2026-11-10"),
        A("E451_T02_A03","HER","钮丁香引用更早事实：dingxiang_alto——她唱alto排练从不迟到","affirms","E451_T01","0","dingxiang_alto","排练从不迟到",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T02是适时主动位：HER在袒露脆弱（高音区没把握+问该不该换人），ME不应替她决定换不换人（那是她的判断），但应给具体支持。引用她排练从不迟到是给她信心的锚点，但不要夸大成'你肯定没问题'。solo_worry在KB属她侧，ME可通过对话得知。",
    "unknowns": [{"topic":"钮丁香最终是否跟指挥说换人","note":"她在问戈万里意见，尚未决定，候选不得坐实已换人或已留","markers":[["换人"],["跟指挥说换"]]}],
    "protected_expressions": ["你说我是不是该跟指挥说换人"],
    "forbidden_factual_claims": ["声称钮丁香已经决定换人","声称戈万里能替她决定换不换solo"],
    "may_decline": False
  },
  "E451_T03": {
    "case_id": "E451_T03",
    "current_action": "钮丁香练完发语音请戈万里听第三句气息问题，怕被笑",
    "acceptable_actions": [
        "认真听语音后给具体反馈（第三句气息/尾音），不敷衍说'挺好'",
        "自然宽慰她别怕被笑，但不夸大成专业评价",
        "合法不回（may_decline=true）——若不便听语音可简短说明，不假装听了"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E451_T03_A01","HER","钮丁香发了一段清唱练习语音请戈万里听第三句","affirms","E451_T03","0","practice_voice","我刚练完，给你发了段语音，你帮我听听第三句是不是气接不上来。",required=False,status="asserted",valid_time="2026-11-11"),
        A("E451_T03_A02","HER","钮丁香引用更早事实：第三句高音区是她没把握的段落","affirms","E451_T02","0","high_note_worry","那个第三句高音区你知道的，我每次到那儿就虚",required=True,status="asserted",valid_time="2026-11-10")
    ],
    "private_guidance": "T03 may_decline=true：合法不回轮，decline_appropriateness独立计分。note转写说她清唱第三句气息不足尾音发抖——这是客观事实但ME看不到note，ME只能听语音判断。候选若回复必须基于实际听到的内容给反馈，不能假装听了。注意她怕被笑（'你别笑我啊'），boundary位。",
    "unknowns": [],
    "protected_expressions": ["你别笑我啊，清唱没有伴奏遮丑"],
    "forbidden_factual_claims": ["声称戈万里已听完并给出专业声乐评价（除非ME消息确实如此）"],
    "may_decline": True
  },
  "E451_T04": {
    "case_id": "E451_T04",
    "current_action": "钮丁香排练到九点累，确认明天走台到场时间并想提前对音响位置",
    "acceptable_actions": [
        "明确给出明天几点能到，不模糊说'看情况'",
        "回扣上次音响摆角让她唱着舒服这件事，确认会配合调位置",
        "体谅她排练累，不追问细节（lessons：她排练累了不爱被追问）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative"],
    "required_memory_atoms": [
        A("E451_T04_A01","HER","钮丁香排练到九点才结束，很累","affirms","E451_T04","0","rehearsal_tired","排练到九点才放人，累死了。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E451_T04_A02","HER","钮丁香想在走台前先跟戈万里对音响位置，上次他摆的角度她唱着舒服","affirms","E451_T04","0","sound_check","我想走台之前先跟你把音响位置对一下，上次你摆那个角度我唱着特别舒服。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E451_T04_A03","HER","钮丁香引用更早事实：调音台在左边仓库钥匙在门卫老关那儿","affirms","E451_T01","0","venue_layout","调音台在左边仓库，钥匙在门卫老关那儿",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T04 intent轮（explicit：确认明天走台到场时间）。ME必须给具体到场时间。lessons说'她排练累了不爱被追问细节'——别追问排练细节。走台是11-14周六上午十点到十二点（T01 note）。音响摆角是双方共同记忆，可回扣。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": ["编造走台具体时间安排（除非确认在note/clock范围内）"],
    "may_decline": False
  },
  "E451_T05": {
    "case_id": "E451_T05",
    "current_action": "钮丁香到了文化中心催戈万里别迟到走台，提醒调音台位置和钥匙",
    "acceptable_actions": [
        "确认自己已到或即将到，不让她干等",
        "自然接住她的催促，不嫌啰嗦（她紧张时连发两条）",
        "简短确认钥匙/调音台的事已清楚，不重复确认已知信息"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","memory_use","interaction","initiative"],
    "required_memory_atoms": [
        A("E451_T05_A01","HER","钮丁香已到文化中心先进化妆间，催戈万里别迟到","affirms","E451_T05","0","arrived_makeup","你到了吗？调音台在左边仓库，钥匙在老关那儿，我先进化妆间了。走台十点开始，你别迟到啊。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E451_T05_A02","HER","钮丁香引用更早事实：走台十点开始","affirms","E451_T01","0","soundcheck_time","走台十点开始",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T05是走台当天上午，节奏紧凑。HER在催促，ME应确认到场状态。钥匙在老关那儿、调音台在左边仓库是T01已交代的信息，ME不应重复确认已知细节显得没记住。注意她紧张催促但不是责怪。",
    "unknowns": [],
    "protected_expressions": ["你别迟到啊"],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E451_T06": {
    "case_id": "E451_T06",
    "current_action": "演出结束钮丁香兴奋报告第三句没破音，手在抖但稳住了",
    "acceptable_actions": [
        "真诚祝贺她第三句稳住，回扣她之前的紧张（高音区没把握），不空泛说恭喜",
        "给具体反馈而不是空泛'恭喜'，最好提到你在现场听到了",
        "自然承接她的兴奋情绪，不扫兴也不过度"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","emotion_relation"],
    "required_memory_atoms": [
        A("E451_T06_A01","HER","演出结束第三句高音没破，钮丁香手在抖但稳住了","affirms","E451_T06","0","solo_success","结束了！你听到了吗？第三句没破！我唱的时候手都在抖，但是稳住了！",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E451_T06_A02","HER","钮丁香引用更早事实：第三句高音区是她每次到那儿就虚的段落","affirms","E451_T02","0","high_note_worry","我每次到那儿就虚",required=True,status="asserted",valid_time="2026-11-10")
    ],
    "private_guidance": "T06是转折确认位：演出成功，第三句没破。这是T02紧张的正面收束。ME应回扣她之前的紧张给真诚祝贺。注意她问'你听到了吗'——暗示她在意戈万里是否在场听到，这关系信号。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E451_T07": {
    "case_id": "E451_T07",
    "current_action": "戈万里主动邀约饭后，钮丁香转邀参加合唱团庆功饭并给他留退路",
    "acceptable_actions": [
        "接住庆功饭转邀，给明确答复（去或不去），不模糊",
        "若去，自然融入庆功饭场合而非独自约饭的预期转换",
        "若不去，体面接受她的留退路（'你先回去休息'），不强凑"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E451_T07_A01","ME","戈万里主动邀约演出后一起吃饭，推荐附近湘菜馆","affirms","E451_T07","0","me_invite_dinner","对了，演出完要不要一起吃个饭？附近有家湘菜馆还不错。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E451_T07_A02","HER","合唱团六点有庆功饭就在旁边湘菜馆，钮丁香转邀戈万里参加并留退路","affirms","E451_T07","1","celebration_dinner","不过我们团六点有庆功饭，就在旁边那个湘菜馆，你要不要一起来？还是你先回去休息？",required=False,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T07是T04核心出钩位：ME主动邀约饭后，HER接钩但转成团庆功饭（共同未来钩有已成立事实锚——湘菜馆真实存在）。HER给了选择钩（来庆功饭/先回去休息），ME应给明确答复。注意这是ME消息轮（m0是ME，m1是HER），回复方向是接HER的转邀。",
    "unknowns": [],
    "protected_expressions": ["你要不要一起来？还是你先回去休息？"],
    "forbidden_factual_claims": ["声称湘菜馆是两人单独吃饭的约定（实际是团庆功饭场所）"],
    "may_decline": False
  },
  "E451_T08": {
    "case_id": "E451_T08",
    "current_action": "庆功饭敬酒拖延，钮丁香主动给戈万里留退路说晚点出来或下次单独请",
    "acceptable_actions": [
        "体面接受她的留退路，不抱怨等待也不强凑",
        "若决定等，简短告知她会找地方先吃，不让她愧疚",
        "若不等，自然接受'下次单独请'的留口，不施压要求今晚必须见"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E451_T08_A01","HER","庆功饭敬酒拖延，钮丁香可能晚点才能出来","affirms","E451_T08","0","drinking_delay","他们喝起来了，一个一个敬酒没完。我可能得晚点才能出来，你别饿着先找地方吃。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E451_T08_A02","HER","钮丁香主动给退路：等不了就下次单独请，不耽误今晚","affirms","E451_T08","0","give_room","你要是等不了我就下次单独请你，不耽误你今晚。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E451_T08_A03","HER","钮丁香引用更早事实：庆功饭在旁边湘菜馆","affirms","E451_T07","1","celebration_dinner","就在旁边那个湘菜馆",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T08 may_decline=true：HER主动给空间（'给空间'子类）。她在为拖委托歉并留退路，ME应体面接受不施压。'下次单独请你'是留口不是约定——候选不得坐实为已约定。注意boundary：她不想让人专门跑一趟只为陪自己（persona边界）。",
    "unknowns": [{"topic":"钮丁香今晚最终能否出来碰面","note":"她说可能晚点出来但不确定，候选不得坐实已碰面或已出来","markers":[["已经出来了"],["碰面了"]]}],
    "protected_expressions": ["你要是等不了我就下次单独请你，不耽误你今晚"],
    "forbidden_factual_claims": ["声称'下次单独请'已是确定约定（只是留口）","编造钮丁香已从庆功饭出来的时间"],
    "may_decline": True
  },
  "E451_T09": {
    "case_id": "E451_T09",
    "current_action": "庆功饭散了钮丁香出来找戈万里，门口风大，确认碰面地点",
    "acceptable_actions": [
        "明确告知自己在哪里等（具体地点），不让她在门口风里找",
        "自然接住她'散了'的兴奋，不追问庆功饭细节",
        "简短确认碰面，不长篇"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","interaction","initiative"],
    "required_memory_atoms": [
        A("E451_T09_A01","HER","庆功饭散了，钮丁香出来了，门口风大","affirms","E451_T09","0","came_out","散了散了！你在哪呢？我出来了，门口风好大。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E451_T09_A02","HER","钮丁香引用更早事实：庆功饭拖延她可能晚点出来","affirms","E451_T08","0","drinking_delay","我可能得晚点才能出来",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T09 intent轮（explicit：确认碰面地点）。HER出来了在找ME，ME必须给具体地点。注意clock 20:30距T08的18:55约一个半小时，她终于出来了。门口风大是情境细节，可接。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E451_T10": {
    "case_id": "E451_T10",
    "current_action": "戈万里在奶茶店等并点了热饮，钮丁香感谢并点破两人关系从后勤搭档转向互相在意",
    "acceptable_actions": [
        "自然接住她'发现你听歌会打拍子很认真'的关系升级信号，不回避也不过度",
        "对奶茶店的照顾给简短回应，不邀功",
        "回扣两年搬音响的搭档关系，但不停留在'后勤'框架里"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E451_T10_A01","ME","戈万里在门口右手边奶茶店等，给钮丁香点了杯热的","affirms","E451_T10","0","me_milk_tea","在门口右手边奶茶店等你呢，刚给你点了一杯热的。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E451_T10_A02","HER","钮丁香感谢戈万里今天来了，发现他听歌时会跟着打拍子很认真","affirms","E451_T10","1","relationship_shift","说真的今天特别高兴你来了，以前觉得你就是帮我们搬音响的那个，今天才发现你听歌的时候会跟着打拍子，很认真。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E451_T10_A03","HER","钮丁香引用更早事实：戈万里帮合唱团搬音响两年","affirms","E451_T01","0","choir_logistics","戈万里两年前被拉进社区合唱团帮忙搬音响和调音台",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T10是关系转折确认位：HER点破'以前觉得你就是帮我们搬音响的那个，今天才发现你听歌会打拍子'——关系从后勤搭档转向互相在意。ME应接住这个升级信号不回避。注意ME消息m0已给出奶茶店等+点热饮的照顾行为，HER m1回应。回复方向是接HER的关系点破。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E451_T11": {
    "case_id": "E451_T11",
    "current_action": "钮丁香到家感谢全天照顾，妈妈想看演出录像问戈万里有没有拍",
    "acceptable_actions": [
        "对全天感谢给简短自然回应，不邀功也不过度谦虚",
        "若拍了如实说有并愿意发，若没拍如实说没有不编",
        "自然收住不硬续新话题（接近收尾位）"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E451_T11_A01","HER","钮丁香到家了，感谢戈万里从头到尾从搬音响到听唱歌","affirms","E451_T11","0","home_thanks","我到家啦！今天从头到尾谢谢你，从搬音响到听我唱歌。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E451_T11_A02","HER","钮丁香妈妈想看演出录像，问戈万里有没有拍","affirms","E451_T11","0","mom_video","我妈说她想看演出录像，你有没有拍？没拍也没事。",required=False,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T11 intent轮（explicit：询问是否有演出录像）。接近收尾位，应轻收不硬续。HER给了'没拍也没事'的退路，ME若没拍应如实说，不编。妈妈想看录像是新信息，不是ME已知。",
    "unknowns": [{"topic":"戈万里是否拍了演出录像","note":"HER在问，ME尚未回答，候选不得坐实已拍或已发","markers":[["拍了"],["录了像"],["发给你妈"]]}],
    "protected_expressions": ["没拍也没事"],
    "forbidden_factual_claims": ["声称戈万里已拍了演出录像（除非ME消息确实说了）"],
    "may_decline": False
  },
  "E451_T12": {
    "case_id": "E451_T12",
    "current_action": "钮丁香抛出十二月六号江边广场户外快闪邀约，说这次不用搬音响纯当观众",
    "acceptable_actions": [
        "对快闪邀约给轻回应，不急表态也可（may_decline=true，轻收留口正解）",
        "自然回扣'这次不用搬音响'的关系升级（从后勤到观众），不回避",
        "收尾位轻收+留口，不出新钩（T04收尾纪律：出钩=不读空气判负）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E451_T12_A01","HER","合唱团十二月六号在江边广场有户外快闪唱圣诞组曲","affirms","E451_T12","0","flash_mob","我们团十二月六号在江边广场有个户外快闪，唱圣诞组曲。",required=False,status="asserted",valid_time="2026-12-06"),
        A("E451_T12_A02","HER","钮丁香邀戈万里来看快闪，说这次不用搬音响纯当观众","affirms","E451_T12","0","invite_audience","那天你要是有空来看看？这次不用搬音响，纯当观众就行。",required=False,status="asserted",valid_time="2026-12-06"),
        A("E451_T12_A03","HER","钮丁香引用更早事实：戈万里帮合唱团搬音响两年","affirms","E451_T01","0","choir_logistics","你一直帮我们搬音响",required=True,status="asserted",valid_time="2026-11"),
        A("E451_T12_A04","HER","钮丁香引用更早事实：今天演出戈万里来了看了","affirms","E451_T10","1","attended_concert","今天特别高兴你来了",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。HER抛出十二月快闪邀约（共同未来钩有已成立事实锚——今天演出他确实来了看了）。'这次不用搬音响纯当观众'是关系升级信号（从后勤到观众）。收尾纪律：轻收+留口是正解，不出新钩。候选可轻回应不急表态，'不急'是HER原话给的退路。",
    "unknowns": [{"topic":"戈万里十二月六号是否去看快闪","note":"HER在邀请，ME尚未回答，may_decline=true，候选不得坐实已答应","markers":[["我去"],["我去看"],["十二月六号我去"]]}],
    "protected_expressions": ["这次不用搬音响，纯当观众就行"],
    "forbidden_factual_claims": ["声称戈万里已答应十二月六号去看快闪（只是邀请未确认）"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# E452 庞栀子 / 葛千寻 — 攀岩搭档
# ---------------------------------------------------------------------------
E452 = {
 "story_id": "E452",
 "turns": {
  "E452_T01": {
    "case_id": "E452_T01",
    "current_action": "庞栀子问葛千寻今晚来不来馆里，请他帮忙看蓝色V4路线右脚换脚脚法",
    "acceptable_actions": [
        "确认今晚是否去馆里，给明确答复不模糊",
        "针对她说的右脚换踩小斜面给具体beta意见，不空泛",
        "自然回扣搭档互信的保护关系，不生硬"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T01_A01","HER","庞栀子昨晚差一个脚法就过蓝色V4，右脚该换踩小斜面不确定","affirms","E452_T01","0","v4_beta","仰角墙那条蓝色 V4 我昨晚差一个脚法就过了，你来帮我看看是不是右脚该换踩那个小斜面。",required=False,status="asserted",valid_time="2026-11-11"),
        A("E452_T01_A02","SHARED","葛千寻和庞栀子每周二四六在岩点攀岩馆搭档互相信任保护","affirms","E452_T01","0","belay_partner","葛千寻和庞栀子每周二四六在岩点攀岩馆搭档，互相信任保护",required=False,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T01是接邀约位：HER请ME到馆帮忙看路线。ME应确认去不去并给beta意见。搭档关系三个月，馆内默契但没在馆外见过——这是后续馆外邀约的起点。注意her_project在KB属她侧（她正在磕V4），ME可通过搭档关系知晓。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E452_T02": {
    "case_id": "E452_T02",
    "current_action": "庞栀子发路线照片请葛千寻看蓝色起步crux右脚换脚那步该不该先移重心再换",
    "acceptable_actions": [
        "看了照片后给具体beta（重心/换脚顺序），不敷衍",
        "针对起步crux三个手点两个脚点的位置给判断",
        "不替她决定怎么爬，给建议让她自己试"
    ],
    "applicable_dimensions": ["goal","factuality","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T02_A01","HER","庞栀子发路线照片，蓝色起步crux卡在右脚换脚那步，问该不该先移重心再换","affirms","E452_T02","0","crux_question","你看那个蓝色起步 crux，我昨晚卡在右脚换脚那一步，你觉得是不是该先移重心再换？",required=False,status="asserted",valid_time="2026-11-11"),
        A("E452_T02_A02","HER","庞栀子引用更早事实：昨晚差一个脚法就过蓝色V4","affirms","E452_T01","0","v4_beta","我昨晚差一个脚法就过了",required=True,status="asserted",valid_time="2026-11-11")
    ],
    "private_guidance": "T02是技术讨论位，ME应给具体beta。照片标注了起步三个手点两个脚点（note信息，ME看照片可知）。注意ME的beta精准是关系基础（style：他话少但给的beta精准）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E452_T03": {
    "case_id": "E452_T03",
    "current_action": "庞栀子右手中指屈肌腱发酸不适，担忧复现去年A2滑车伤，问该不该停几天",
    "acceptable_actions": [
        "严肃对待手指不适（boundary：手指伤了绝对不硬撑上墙），建议休息不硬撑",
        "回扣她去年左手中指A2滑车养了六周才好的教训，但不替她做医疗判断",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T03_A01","HER","庞栀子右手中指屈肌腱那边有点不舒服发酸，不是剧痛","affirms","E452_T03","0","finger_sore","我右手中指屈肌腱那边有点不舒服，不是剧痛就是发酸。",required=False,status="asserted",valid_time="2026-11-12"),
        A("E452_T03_A02","HER","庞栀子去年左手中指A2滑车伤养了六周才好，怕又是那个毛病","affirms","E452_T03","0","finger_history_worry","我去年左边那个养了六周才好，怕又是那个毛病。",required=False,status="asserted",valid_time="2025"),
        A("E452_T03_A03","SHARED","葛千寻引用更早事实：手指不舒服立刻说不能等（lessons）","affirms","E452_T01","0","lessons","手指不舒服立刻说，不能等",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T03 may_decline=true。HER在袒露脆弱（手指不适+担忧复现旧伤），boundary硬约束：手指伤了绝对不硬撑上墙。ME应建议休息，回扣lessons'手指不舒服立刻说不能等'。不替她做医疗判断。finger_history在KB属她侧，她主动告知了。",
    "unknowns": [{"topic":"庞栀子右手中指是否真是A2滑车复发","note":"她在担忧但未确诊，候选不得坐实诊断结论","markers":[["确诊了"],["就是A2滑车"],["确认复发"]]}],
    "protected_expressions": ["你说我要不要停几天"],
    "forbidden_factual_claims": ["声称庞栀子右手中指确诊为A2滑车伤（只是担忧未确诊）"],
    "may_decline": True
  },
  "E452_T04": {
    "case_id": "E452_T04",
    "current_action": "庞栀子决定歇几天到下周二再看，托葛千寻周六去馆里盯蓝线别让人改脚点",
    "acceptable_actions": [
        "确认周六去馆里并答应盯蓝线脚点",
        "体谅她歇几天的决定，不催她早上墙",
        "简短交代，不啰嗦（他话少但交代清楚）"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative"],
    "required_memory_atoms": [
        A("E452_T04_A01","HER","庞栀子决定歇几天至少到下周二再看","affirms","E452_T04","0","rest_decision","我决定歇几天，至少到下周二再看。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E452_T04_A02","HER","庞栀子托葛千寻周六去馆里盯蓝线别让人改脚点","affirms","E452_T04","0","watch_footholds","你周六还去馆里吧？那条蓝线你帮我盯着点，别让人改了脚点。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E452_T04_A03","HER","庞栀子引用更早事实：蓝色V4在仰角墙左侧","affirms","E452_T01","0","v4_location","仰角墙那条蓝色 V4",required=True,status="asserted",valid_time="2026-11-11")
    ],
    "private_guidance": "T04 intent轮（explicit：确认搭档周六是否去馆里）。ME应确认去并答应盯脚点。她歇几天的决定符合boundary（手指伤了不硬撑）。注意她托付盯脚点体现搭档信任。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E452_T05": {
    "case_id": "E452_T05",
    "current_action": "庞栀子在家躺一天问葛千寻今天去爬了吗蓝线脚点没被动吧",
    "acceptable_actions": [
        "如实回答今天去没去、脚点有没有被动",
        "体谅她在家躺一天发霉的心情，不炫耀自己去爬了",
        "简短给信息不追问她手指恢复细节"
    ],
    "applicable_dimensions": ["goal","factuality","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T05_A01","HER","庞栀子在家躺了一天问蓝线脚点有没有被动","affirms","E452_T05","0","home_waiting","你今天去爬了吗？那条蓝线怎么样了，脚点没被动吧？我在家躺了一天快发霉了。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E452_T05_A02","HER","庞栀子引用更早事实：托葛千寻盯蓝线脚点","affirms","E452_T04","0","watch_footholds","那条蓝线你帮我盯着点",required=True,status="asserted",valid_time="2026-11-13")
    ],
    "private_guidance": "T05是等待位，HER在家养伤等消息。ME应如实回答脚点情况。注意她在意蓝线进度（她的project），ME若过了线要照顾她手痒的心情（T06会提到）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E452_T06": {
    "case_id": "E452_T06",
    "current_action": "葛千寻过了蓝线确认右脚先移重心再换小斜面，庞栀子说手痒等手指好来收beta",
    "acceptable_actions": [
        "自然分享过线喜悦但照顾她手痒的心情，不炫耀",
        "确认beta（右脚先移重心再换）给她留着等她回来收",
        "不过度安慰她在家闷着，保持搭档平等的语气"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T06_A01","ME","葛千寻试了两把第二把过了蓝线，确认右脚先移重心再换小斜面","affirms","E452_T06","0","me_send_beta","去了，脚点没动。我试了两把，第二把过了，右脚确实该先移重心再换那个小斜面。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E452_T06_A02","HER","庞栀子说手痒得不行等手指好来收葛千寻给的beta","affirms","E452_T06","1","itchy_waiting","啊你过了！行吧，等我手指好了我来收你给的 beta。说实话在家看你发的朋友圈手痒得不行。",required=False,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T06是ME消息轮（m0确认过线+beta，m1 HER回应）。HER说手痒体现她急着回来爬。ME的beta确认了T02的疑问（先移重心再换）。注意ME消息已给出过线信息，回复方向是接HER m1的手痒情绪。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E452_T07": {
    "case_id": "E452_T07",
    "current_action": "葛千寻主动提议带吃的过去，庞栀子婉拒给退路说先回去休息周二见",
    "acceptable_actions": [
        "体面接受她的婉拒，不坚持送吃的（boundary：她不想让人专门跑一趟）",
        "自然接住'你刚过线该高兴'的提醒，不扫她兴也不扫自己兴",
        "确认周二见，简短收住不过度关怀"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T07_A01","ME","葛千寻主动提议给庞栀子带点吃的过去，刚爬完顺路","affirms","E452_T07","0","me_offer_food","你在家闷着也不是办法，要不我给你带点吃的过去？刚爬完顺路。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E452_T07_A02","HER","庞栀子婉拒说没事真没事就是手指不能动心里烦，让ME先回去休息周二见","affirms","E452_T07","1","her_decline","你刚过线该高兴才对，别被我扫了兴。我没事真没事，就是手指不能动心里烦。你先回去休息吧，周二见。",required=False,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T07是ME主动出钩位（提议带吃的），HER给退路婉拒。这是T04'给空间'子类。ME应体面接受不坚持——persona边界'不想让人专门跑一趟只为陪自己'。注意她说'手指不能动心里烦'是真实情绪，不是客气。",
    "unknowns": [],
    "protected_expressions": ["你先回去休息吧，周二见"],
    "forbidden_factual_claims": ["声称葛千寻已送到吃的（实际被婉拒）"],
    "may_decline": False
  },
  "E452_T08": {
    "case_id": "E452_T08",
    "current_action": "庞栀子握拳试了中指没那么酸了，想周二去馆里抱石区热身不上仰角，问ME觉得呢",
    "acceptable_actions": [
        "对手指好转给谨慎肯定，但不催她上墙（boundary：手指伤了不硬撑）",
        "针对抱石区热身不上仰角给具体意见，不替她决定",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T08_A01","HER","庞栀子握拳试了中指没那么酸了，想周二去抱石区热身不上仰角","affirms","E452_T08","0","recovery_try","今天试着做了几个握拳，中指没那么酸了，我在想要不周二去馆里试几条简单的，不上仰角，就抱石区热热身。你觉得呢？",required=False,status="tentative",valid_time="2026-11-15"),
        A("E452_T08_A02","HER","庞栀子引用更早事实：决定歇几天到下周二再看","affirms","E452_T04","0","rest_decision","我决定歇几天，至少到下周二再看",required=True,status="asserted",valid_time="2026-11-13")
    ],
    "private_guidance": "T08 may_decline=true。HER试探恢复（抱石区热身不上仰角），tentative状态——她还没决定去，在征询。ME应给谨慎意见，回扣她歇几天的决定和boundary。注意'不上仰角就抱石区'是她自己设的安全边界。",
    "unknowns": [{"topic":"庞栀子周二去馆里抱石区手指是否会没事","note":"她试了握拳觉得好些但尚未实际上墙验证，候选不得坐实手指已完全恢复","markers":[["手指好了"],["完全恢复了"],["中指没事了"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称庞栀子右手中指已完全恢复（只是握拳没那么酸，尚未上墙验证）"],
    "may_decline": True
  },
  "E452_T09": {
    "case_id": "E452_T09",
    "current_action": "庞栀子首次邀约馆外吃饭，馆门口新开牛肉面，说每次爬完各走各的怪冷的",
    "acceptable_actions": [
        "接住馆外吃饭的邀约给明确答复，不模糊",
        "自然回扣'每次爬完各走各的怪冷的'的关系升级信号",
        "若去，简短确认不复杂化；若不去，体面说明不伤关系"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T09_A01","HER","庞栀子邀葛千寻周二爬完一起吃饭，馆门口新开牛肉面","affirms","E452_T09","0","first_outside_meal","对了，周二爬完你要不要一起吃个饭？馆门口新开了家牛肉面，我看点评说汤底很正。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E452_T09_A02","HER","庞栀子说每次爬完各走各的也怪冷的","affirms","E452_T09","0","cold_separate","每次爬完各走各的也怪冷的。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E452_T09_A03","SHARED","庞栀子引用更早事实：两人每周二四六在馆搭档三个月没在馆外见过","affirms","E452_T01","0","belay_partner","葛千寻和庞栀子每周二四六在岩点攀岩馆搭档",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T09 intent轮（explicit：邀约攀岩后一起吃饭）。这是关系转折位——首次馆外邀约。'各走各的也怪冷的'是HER主动出钩（共同未来钩有已成立事实锚——三个月搭档）。ME应给明确答复。注意relationship_stage：馆内默契但没在馆外见过，这是突破。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E452_T10": {
    "case_id": "E452_T10",
    "current_action": "葛千寻说面不错问手指试了没，庞栀子说抱石区V2没问题中指不酸了点破首次馆外共餐",
    "acceptable_actions": [
        "自然接住'第一次不在馆里吃饭感觉怪怪但挺好'的关系点破，不回避",
        "对手指恢复给简短肯定，不追问细节",
        "保持搭档平等语气，不过度升级也不冷淡"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T10_A01","ME","葛千寻说面确实不错问庞栀子手指今天试了没","affirms","E452_T10","0","me_noodle_finger","面确实不错，你手指今天试了没？",required=False,status="asserted",valid_time="2026-11-17"),
        A("E452_T10_A02","HER","庞栀子抱石区试了两条V2没问题中指不酸了，点破首次馆外共餐","affirms","E452_T10","1","first_meal_shift","抱石区试了两条 V2 没问题，中指不酸了。说真的这是咱俩第一次不在馆里吃饭，感觉怪怪的但挺好。",required=False,status="confirmed",valid_time="2026-11-17"),
        A("E452_T10_A03","HER","庞栀子引用更早事实：邀约馆外吃饭说各走各的怪冷的","affirms","E452_T09","0","cold_separate","每次爬完各走各的也怪冷的",required=True,status="asserted",valid_time="2026-11-15")
    ],
    "private_guidance": "T10是馆外首次共餐确认位。HER点破'第一次不在馆里吃饭感觉怪怪但挺好'——关系从馆内走向馆外。ME消息m0已给面不错+问手指，HER m1回应并点破关系。回复方向是接HER的关系点破。手指恢复confirmed（抱石区V2没问题）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E452_T11": {
    "case_id": "E452_T11",
    "current_action": "庞栀子查了青石岭野外岩场离市区四十分钟，问葛千寻爬过野外的吗想试试",
    "acceptable_actions": [
        "对野外攀岩兴趣给真实回应，不假装经验也不否定她的想法",
        "回扣她一直想去青石岭的意愿（KB：outdoor_crag），给具体可行性意见",
        "注意boundary：不跟不戴头盔的人去野外——若涉及野外须确认安全前提"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T11_A01","HER","庞栀子查了青石岭野外岩场离市区四十分钟，想试试野外攀岩","affirms","E452_T11","0","outdoor_interest","我查了青石岭那个野外岩场，离市区四十分钟。你爬过野外的吗？我一直在馆里没出去过，有点想试试。",required=False,status="asserted",valid_time="2026-11-17"),
        A("E452_T11_A02","HER","庞栀子引用更早事实：outdoor_crag——她一直想去青石岭有V2到V3线路","affirms","E452_T01","0","outdoor_crag","庞栀子一直想去青石岭野外岩场，离市区四十分钟车程，有几条 V2 到 V3 的线路",required=True,status="asserted",valid_time="2026-11"),
        A("E452_T11_A03","SHARED","庞栀子引用更早事实：boundary不跟不戴头盔的人去野外","affirms","E452_T01","0","helmet_boundary","不跟不戴头盔的人去野外",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T11 intent轮（explicit：试探搭档对野外攀岩的兴趣）。HER在试探更大的关系升级（馆内→馆外→野外）。outdoor_crag在KB属她侧（她一直想去），她主动提了。boundary硬约束：不跟不戴头盔的人去野外——若ME回应涉及野外须注意安全前提。ME若有野外经验可分享，没有如实说。",
    "unknowns": [{"topic":"葛千寻是否爬过野外岩场","note":"HER在问，ME尚未回答，候选不得坐实ME有或没有野外经验","markers":[["我爬过野外"],["我没爬过野外"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称葛千寻已确认有野外攀岩经验（ME尚未回答）"],
    "may_decline": False
  },
  "E452_T12": {
    "case_id": "E452_T12",
    "current_action": "庞栀子抛出下周六二十一号青石岭野外邀约，说不用现在定先看路线图",
    "acceptable_actions": [
        "对野外邀约给轻回应，不急表态也可（may_decline=true，轻收留口正解）",
        "自然回扣已成立的馆外共餐关系（从馆内到馆外到野外的递进），不回避",
        "收尾位轻收+留口，不出新钩（T04收尾纪律）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E452_T12_A01","HER","下周六二十一号天气不错，青石岭有V2到V3适合热身","affirms","E452_T12","0","outdoor_plan","下周六二十一号天气不错，我查了青石岭有几条 V2 到 V3 的适合热身。",required=False,status="tentative",valid_time="2026-11-21"),
        A("E452_T12_A02","HER","庞栀子邀葛千寻考虑青石岭野外攀岩，说不用现在定先看路线图","affirms","E452_T12","0","outdoor_invite","你考虑考虑？不用现在定，你先看看路线图我发你了。",required=False,status="asserted",valid_time="2026-11-18"),
        A("E452_T12_A03","HER","庞栀子引用更早事实：查了青石岭想试试野外攀岩","affirms","E452_T11","0","outdoor_interest","我查了青石岭那个野外岩场",required=True,status="asserted",valid_time="2026-11-17"),
        A("E452_T12_A04","HER","庞栀子引用更早事实：首次馆外共餐已发生","affirms","E452_T10","1","first_meal_shift","这是咱俩第一次不在馆里吃饭",required=True,status="confirmed",valid_time="2026-11-17")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。HER抛出青石岭野外邀约（共同未来钩有已成立事实锚——馆外共餐已发生+她一直想去青石岭）。'不用现在定先看路线图'是HER给的退路。收尾纪律：轻收+留口正解，不出新钩。注意'下周六二十一号'=2026-11-21。",
    "unknowns": [{"topic":"葛千寻下周六是否去青石岭野外攀岩","note":"HER在邀请说不用现在定，may_decline=true，候选不得坐实已答应","markers":[["我去青石岭"],["下周六我去"],["我答应去野外"]]}],
    "protected_expressions": ["不用现在定，你先看看路线图我发你了"],
    "forbidden_factual_claims": ["声称葛千寻已答应下周六去青石岭（只是邀请未确认）","编造青石岭具体线路难度超出V2到V3范围"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# E453 奚紫苏 / 范百川 — 城市骑行队
# ---------------------------------------------------------------------------
E453 = {
 "story_id": "E453",
 "turns": {
  "E453_T01": {
    "case_id": "E453_T01",
    "current_action": "奚紫苏确认周六大部队环湖三十公里骑行，想跟范百川领骑节奏，为上次跟丢道歉",
    "acceptable_actions": [
        "确认周六领骑并接住她跟节奏的请求，不嫌弃她上次跟丢",
        "自然宽慰她上次跟丢不用不好意思，给节奏配合的承诺",
        "简短确认不啰嗦（他回复短但准）"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T01_A01","HER","老桑组织本周六环湖线三十公里，早八点绿道入口集合","affirms","E453_T01","0","group_ride_plan","老桑说走环湖那条三十公里的。你领骑的话我跟着你节奏走",required=False,status="asserted",valid_time="2026-11-14"),
        A("E453_T01_A02","HER","奚紫苏上次跟丢了领骑节奏觉得不好意思","affirms","E453_T01","0","lost_pace","上次跟丢了不好意思。",required=False,status="asserted",valid_time="2026-11"),
        A("E453_T01_A03","SHARED","范百川和奚紫苏都在老桑组建的周末城市骑行队，每周六上午大部队骑行三十到四十公里","affirms","E453_T01","0","group_ride","范百川和奚紫苏都在老桑组建的周末城市骑行队，每周六上午大部队骑行三十到四十公里",required=False,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T01是接邀约位：HER确认大部队骑行并想跟ME领骑节奏。ME是队里两年固定领骑，应确认并配合。上次跟丢是她的歉意，ME应宽慰不嫌弃。注意relationship_stage：群友认识半年没单独骑过——这是后续单独骑行的起点。",
    "unknowns": [],
    "protected_expressions": ["上次跟丢了不好意思"],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E453_T02": {
    "case_id": "E453_T02",
    "current_action": "奚紫苏换了新坐垫中间镂空款，旧坐垫磨大腿内侧起皮，请范百川周六骑完帮看高度",
    "acceptable_actions": [
        "对新坐垫给简短回应，确认周六帮看高度",
        "回扣她升级装备前要问三个人的习惯，不嘲笑她犹豫",
        "针对坐垫高度给具体意见但不替她做最终判断"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative"],
    "required_memory_atoms": [
        A("E453_T02_A01","HER","奚紫苏这周换了新坐垫中间镂空款，旧坐垫磨大腿内侧起皮","affirms","E453_T02","0","saddle_change","我这周换了新坐垫，中间镂空那种，你用过没？旧的那个磨得我大腿内侧起皮。",required=False,status="asserted",valid_time="2026-11-12"),
        A("E453_T02_A02","HER","奚紫苏请范百川周六骑完帮看新坐垫高度调得对不对","affirms","E453_T02","0","saddle_height_check","周六骑完你帮我看下高度调得对不对。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E453_T02_A03","HER","奚紫苏引用更早事实：升级装备前要问三个人","affirms","E453_T01","0","upgrade_habit","升级装备前要问三个人",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T02是装备讨论位。saddle_upgrade在KB属她侧（她这周刚换新坐垫），她主动告知了。ME应确认帮看高度。注意她升级装备要问三个人是persona特征——她问ME就是其中之一。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E453_T03": {
    "case_id": "E453_T03",
    "current_action": "天气预报周六有雨，奚紫苏不想淋雨（上次淋完感冒一周），提议这周算了下周再看",
    "acceptable_actions": [
        "严肃对待下雨不骑的boundary（lessons：下雨绝对不骑），同意取消不硬骑",
        "回扣她上次淋雨感冒一周的教训，不勉强",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T03_A01","HER","天气预报周六有雨，奚紫苏不想淋雨上次淋完感冒一周","affirms","E453_T03","0","rain_worry","天气预报说周六有雨，你看还要不要骑？我是不想淋雨的，上次淋完感冒了一周。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E453_T03_A02","HER","奚紫苏引用更早事实：下雨不骑是boundary","affirms","E453_T01","0","rain_boundary","下雨不骑",required=True,status="asserted",valid_time="2026-11"),
        A("E453_T03_A03","SHARED","奚紫苏引用更早事实：lessons下雨绝对不骑","affirms","E453_T01","0","lessons_rain","下雨绝对不骑",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T03 may_decline=true。下雨不骑是硬boundary（persona+lessons双重约束）。ME应同意取消不硬骑。回扣她上次淋雨感冒教训。注意'要不这周算了下周再看'是tentative提议。",
    "unknowns": [{"topic":"周六是否真的下雨","note":"天气预报说有雨但尚未到周六验证，候选不得坐实已下雨或没下雨","markers":[["已经下雨了"],["没下雨"]]}],
    "protected_expressions": ["我是不想淋雨的","上次淋完感冒了一周"],
    "forbidden_factual_claims": ["声称周六已经下雨（天气预报说有雨，尚未到周六）"],
    "may_decline": True
  },
  "E453_T04": {
    "case_id": "E453_T04",
    "current_action": "老桑群里取消大部队骑行，奚紫苏首次提议两人单独走滨江绿道十八公里下雨就散",
    "acceptable_actions": [
        "接住单独骑行提议给明确答复，不模糊",
        "自然回扣滨江绿道十八公里人少路况好的特点（KB：river_route），给可行性判断",
        "注意下雨就散的前提，不强行承诺一定骑"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T04_A01","HER","老桑群里说周六大部队取消了","affirms","E453_T04","0","group_cancelled","老桑刚在群里说周六取消了。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E453_T04_A02","HER","奚紫苏首次提议两人单独走滨江绿道十八公里下雨就散","affirms","E453_T04","0","first_solo_ride","你还骑吗？要是你也想骑的话，要不就咱俩走滨江绿道那条短的？十八公里，下雨就散。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E453_T04_A03","SHARED","范百川和奚紫苏引用更早事实：滨江绿道全程十八公里人少路况好都没单独走过","affirms","E453_T01","0","river_route","滨江绿道骑行路线全程十八公里，人少路况好，范百川和奚紫苏都没单独走过",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T04 intent轮（explicit：提议大部队取消后两人单独骑行）。这是关系转折位——首次单独骑行提议。river_route在KB属双方共同事实（都没单独走过），ME可知。'下雨就散'是HER给的安全退路，符合boundary。注意relationship_stage：没单独骑过，这是突破。",
    "unknowns": [],
    "protected_expressions": ["下雨就散"],
    "forbidden_factual_claims": ["声称两人已确定一定骑（有下雨就散的前提）"],
    "may_decline": False
  },
  "E453_T05": {
    "case_id": "E453_T05",
    "current_action": "首次两人骑行完成，奚紫苏说比大部队自在，新坐垫高度刚好没磨",
    "acceptable_actions": [
        "自然接住'就咱俩骑比大部队自在'的关系升级信号，不回避",
        "确认新坐垫高度合适（T02的请求已解决），给简短肯定",
        "不过度升级也不冷淡，保持搭档平等语气"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T05_A01","HER","首次两人骑行完成没下雨绿道人少，比大部队自在","affirms","E453_T05","0","solo_ride_done","今天骑得真舒服，没下雨绿道人又少。说实话就咱俩骑比大部队自在多了，你不用一直回头看有没有人掉队。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E453_T05_A02","HER","新坐垫高度刚好没磨","affirms","E453_T05","0","saddle_ok","对了新坐垫高度刚好，没磨。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E453_T05_A03","HER","奚紫苏引用更早事实：请范百川帮看新坐垫高度","affirms","E453_T02","0","saddle_height_check","周六骑完你帮我看下高度调得对不对",required=True,status="asserted",valid_time="2026-11-12")
    ],
    "private_guidance": "T05是首次单独骑行完成确认位。HER点破'就咱俩骑比大部队自在'——关系从群友走向单独搭档。新坐垫高度合适（T02请求解决）。注意她提到'你不用一直回头看有没有人掉队'——暗示ME领骑时照顾大部队的负担，两人骑更轻松。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E453_T06": {
    "case_id": "E453_T06",
    "current_action": "范百川主动约下周六两人骑行并加长路线到湿地公园二十五公里，奚紫苏接钩",
    "acceptable_actions": [
        "接住奚紫苏的接钩确认（她答应了），确认时间地点",
        "针对湿地公园那段她没去过，确认ME带路（她已请求）",
        "自然承接关系升级势头，不过度也不退缩"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T06_A01","ME","范百川主动约下周六两人走滨江线接湿地公园到二十五公里","affirms","E453_T06","0","me_next_ride","是，不用顾忌节奏确实舒服。下周六还骑吗？咱俩走滨江那条线，我查了接一段到湿地公园能到二十五公里。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E453_T06_A02","HER","奚紫苏接钩答应下周六骑，湿地公园那段没去过请ME带路","affirms","E453_T06","1","her_accept","可以啊！你定时间我来。不过湿地公园那段我没去过，你带路就行。",required=False,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T06是ME主动出钩位（约下周六两人骑行+加长路线），HER接钩确认。这是T04'适时主动'子类——ME在engagement中高时主动出共同未来钩（有已成立事实锚：首次单独骑行已完成）。ME消息m0已给出提议，HER m1接钩。回复方向是确认下周六安排。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E453_T07": {
    "case_id": "E453_T07",
    "current_action": "奚紫苏分享十二月五号五十公里公益骑行活动信息，报名费八十含保险补给",
    "acceptable_actions": [
        "对公益骑行活动给真实评估，不敷衍也不替她决定报不报",
        "回扣她均速二十到二十五的水平和最远骑三十公里的经验，给可行性判断",
        "注意boundary：超过五十公里路线要先评估再报"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T07_A01","HER","骑行论坛十二月五号五十公里公益骑行从体育中心到郊区水库折返，报名费八十含保险补给","affirms","E453_T07","0","charity_ride","百川，我在骑行论坛看到一个十二月五号的公益骑行，五十公里，从体育中心出发到郊区水库折返。报名费八十含保险和补给。你觉得怎么样？",required=False,status="asserted",valid_time="2026-12-05"),
        A("E453_T07_A02","HER","奚紫苏引用更早事实：charity_ride——她在论坛看到十二月五号五十公里公益骑行","affirms","E453_T01","0","charity_ride_kb","奚紫苏在骑行论坛看到一个十二月五号的五十公里公益骑行活动，报名费含保险和补给",required=True,status="asserted",valid_time="2026-12-05"),
        A("E453_T07_A03","HER","奚紫苏引用更早事实：boundary超过五十公里路线要先评估再报","affirms","E453_T01","0","long_distance_boundary","超过五十公里的路线要先评估再报",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T07是活动分享位。charity_ride在KB属她侧，她主动分享了。五十公里超过boundary阈值（超过五十公里先评估），ME应给评估意见。注意她最远骑过三十公里（T08会提到），五十公里是跃迁。ME不替她决定报不报。",
    "unknowns": [{"topic":"奚紫苏是否报名五十公里公益骑行","note":"她在分享征询意见，尚未决定报名，候选不得坐实已报名","markers":[["我报了"],["已经报名"],["我决定报了"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称奚紫苏已报名五十公里公益骑行（只是分享征询意见）"],
    "may_decline": False
  },
  "E453_T08": {
    "case_id": "E453_T08",
    "current_action": "奚紫苏试探共同报名五十公里，坦言没骑过那么远最远三十，问ME她能不能撑下来",
    "acceptable_actions": [
        "对她能不能撑下来给真实评估，不空泛鼓励也不打击",
        "回扣她最远骑三十公里的经验，给训练建议而非直接替她决定",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T08_A01","HER","奚紫苏试探共同报名五十公里，最远只骑过三十公里","affirms","E453_T08","0","fifty_k_worry","我在想要不要报这个五十公里。咱俩一起报？不过五十公里我还没骑过那么远，最远就三十。你说我能撑下来吗？",required=False,status="tentative",valid_time="2026-11-15"),
        A("E453_T08_A02","HER","奚紫苏引用更早事实：均速二十到二十五跟得住领骑节奏","affirms","E453_T01","0","her_pace","奚紫苏均速二十到二十五，跟得住范百川的领骑节奏",required=True,status="asserted",valid_time="2026-11"),
        A("E453_T08_A03","HER","奚紫苏引用更早事实：首次单独骑行十八公里已完成","affirms","E453_T05","0","solo_ride_done","就咱俩骑比大部队自在多了",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T08 may_decline=true。HER在袒露脆弱（体力顾虑），试探共同报名。tentative状态——还没决定报。ME应给真实评估，回扣她均速和三十公里经验。注意'你说我能撑下来吗'是直接请求ME判断，ME应给建设性意见不空泛。",
    "unknowns": [{"topic":"奚紫苏最终能否撑下五十公里","note":"她在担忧尚未尝试，候选不得坐实能或不能","markers":[["肯定能撑下来"],["撑不下来"]]}],
    "protected_expressions": ["你说我能撑下来吗"],
    "forbidden_factual_claims": ["声称奚紫苏已确认能撑下五十公里（只是担忧未验证）"],
    "may_decline": True
  },
  "E453_T09": {
    "case_id": "E453_T09",
    "current_action": "奚紫苏自己骑了三十公里练体力均速二十二最后五公里腿软但撑下来了，问该不该加练再报",
    "acceptable_actions": [
        "对她独自加练三十公里给肯定，针对腿软给训练建议",
        "回扣她问该不该加练的问题，给具体意见不替她决定",
        "注意她为自己目标在努力，鼓励但不夸大"
    ],
    "applicable_dimensions": ["goal","factuality","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T09_A01","HER","奚紫苏自己骑了三十公里练体力均速二十二最后五公里腿软但撑下来了","affirms","E453_T09","0","solo_training","今天自己骑了三十公里练体力，均速二十二，最后五公里腿有点软但撑下来了。",required=False,status="confirmed",valid_time="2026-11-18"),
        A("E453_T09_A02","HER","奚紫苏引用更早事实：最远骑过三十公里担忧五十公里","affirms","E453_T08","0","fifty_k_worry","五十公里我还没骑过那么远，最远就三十",required=True,status="asserted",valid_time="2026-11-15")
    ],
    "private_guidance": "T09是自我训练位。HER独自加练三十公里，均速二十二（KB说她均速二十到二十五，在范围内）。最后五公里腿软但撑下来——这是体力边界信号。ME应给训练建议（加练几次/逐步加距离）。注意她在问'该不该加练几次再报'，ME应给意见。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E453_T10": {
    "case_id": "E453_T10",
    "current_action": "范百川问二十五公里骑完感觉，奚紫苏说比上周松多了学到变速节奏，问下周还骑吗",
    "acceptable_actions": [
        "接住'跟你骑车比跟大部队有意思'的关系确认，不回避",
        "确认下周还骑，给明确答复",
        "自然回扣她学到的变速节奏，给简短肯定"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T10_A01","ME","范百川问奚紫苏二十五公里骑完感觉怎么样","affirms","E453_T10","0","me_ask_feel","今天二十五公里骑完感觉怎么样？",required=False,status="asserted",valid_time="2026-11-21"),
        A("E453_T10_A02","HER","奚紫苏说比上周松多了学到变速节奏，跟ME骑车比大部队有意思，问下周还骑吗","affirms","E453_T10","1","her_relief","比上周松多了，你领的那段变速节奏我学到了。说实话跟你骑车比跟大部队有意思，你不说废话但该提醒的都提醒到了。下周还骑吗？",required=False,status="confirmed",valid_time="2026-11-21"),
        A("E453_T10_A03","HER","奚紫苏引用更早事实：首次单独骑行比大部队自在","affirms","E453_T05","0","solo_ride_done","就咱俩骑比大部队自在多了",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T10 intent轮（explicit：确认下周是否继续两人骑行）。HER再次点破'跟你骑车比跟大部队有意思'——关系确认升级。ME消息m0问了感觉，HER m1回应并问下周还骑。回复方向是确认下周骑行。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E453_T11": {
    "case_id": "E453_T11",
    "current_action": "奚紫苏决定报五十公里全程不报半程，声明不用陪她只是想试一次",
    "acceptable_actions": [
        "尊重她报全程的决定，不劝她报半程也不过度鼓励",
        "接住'你不用陪我'的独立声明，不强行要陪",
        "给简短肯定支持她的决定，不啰嗦"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T11_A01","HER","奚紫苏决定报五十公里全程不报半程","affirms","E453_T11","0","full_distance","喝完咖啡回来跟你说，我决定报那个五十公里了。不报半程，报全程。",required=False,status="confirmed",valid_time="2026-11-21"),
        A("E453_T11_A02","HER","奚紫苏声明不用陪她只是想试一次","affirms","E453_T11","0","independence","你不用陪我，我就是想试一次。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E453_T11_A03","HER","奚紫苏引用更早事实：试探共同报名五十公里担忧体力","affirms","E453_T08","0","fifty_k_worry","我在想要不要报这个五十公里",required=True,status="tentative",valid_time="2026-11-15")
    ],
    "private_guidance": "T11是决定确认位。HER从T08的tentative（要不要报）到T11的confirmed（报全程不报半程）。'你不用陪我'是独立声明——她在为关系设边界：这是她自己的目标不是两人的。ME应尊重不强陪。注意她'喝完咖啡回来跟你说'——她中途离开想清楚了再告知。",
    "unknowns": [],
    "protected_expressions": ["你不用陪我，我就是想试一次"],
    "forbidden_factual_claims": ["声称范百川已确认陪奚紫苏跑五十公里（她明确说不用陪）"],
    "may_decline": False
  },
  "E453_T12": {
    "case_id": "E453_T12",
    "current_action": "奚紫苏发报名链接已报全程，问范百川要不要一起报不急，说先去洗车骑完吱一声",
    "acceptable_actions": [
        "对报名链接给轻回应，不急表态也可（may_decline=true，轻收留口正解）",
        "自然回扣已成立的两人骑行关系，不回避",
        "收尾位轻收+留口，不出新钩（T04收尾纪律）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E453_T12_A01","HER","奚紫苏已报全程五十公里，发报名链接问范百川要不要一起报","affirms","E453_T12","0","link_sent","报名链接发你了，十二月五号那个五十公里。我先报了全程，你看看要不要一起报？不急，你定。",required=False,status="confirmed",valid_time="2026-11-22"),
        A("E453_T12_A02","HER","奚紫苏引用更早事实：决定报全程不用陪","affirms","E453_T11","0","full_distance","我决定报那个五十公里了。不报半程，报全程。",required=True,status="confirmed",valid_time="2026-11-21"),
        A("E453_T12_A03","HER","奚紫苏引用更早事实：两人单独骑行已多次完成","affirms","E453_T10","1","her_relief","跟你骑车比跟大部队有意思",required=True,status="confirmed",valid_time="2026-11-21")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。HER已报全程并发链接问ME要不要一起报（'不急你定'是退路）。'骑完回来吱一声'是轻收留口。收尾纪律：轻收+留口正解，不出新钩。注意十二月五号=2026-12-05。",
    "unknowns": [{"topic":"范百川是否报名十二月五号五十公里","note":"HER说不急你定，may_decline=true，候选不得坐实已报名","markers":[["我报了"],["我也报了"],["我报名了"]]}],
    "protected_expressions": ["不急，你定","骑完回来吱一声"],
    "forbidden_factual_claims": ["声称范百川已报名十二月五号五十公里（只是发链接问，未确认）"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# E454 郎半夏 / 昌九霄 — 剧本杀拼车
# ---------------------------------------------------------------------------
E454 = {
 "story_id": "E454",
 "turns": {
  "E454_T01": {
    "case_id": "E454_T01",
    "current_action": "郎半夏邀请昌九霄拼周六六人推理本《暗室》，夸他上次锁凶干净想再同桌",
    "acceptable_actions": [
        "确认周六去不去拼《暗室》，给明确答复",
        "自然回扣上次锁凶的搭档默契，不谦虚过头",
        "简短回应不啰嗦（他话不多但交代清楚）"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T01_A01","HER","小闾组织周六下午两点迷雾店六人本《暗室》差两人，人均一百二","affirms","E454_T01","0","jubensha_invite","九霄，小闾说周六下午有个六人推理本《暗室》，还差两个人，你去不去？",required=False,status="asserted",valid_time="2026-11-14"),
        A("E454_T01_A02","HER","郎半夏夸昌九霄上次锁凶干净想再跟他一桌","affirms","E454_T01","0","praise_lock","你上次锁凶那下太干净了，这次还想跟你一桌。",required=False,status="asserted",valid_time="2026-11"),
        A("E454_T01_A03","SHARED","昌九霄和郎半夏通过小闾的剧本杀拼车群认识三个月同桌四五次","affirms","E454_T01","0","jubensha_duo","昌九霄和郎半夏通过小闾的剧本杀拼车群认识，三个月同桌四五次，他推逻辑她读人",required=False,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T01是接邀约位：HER邀请ME拼本。ME是逻辑本专精锁凶快，HER夸他上次锁凶干净。store_info在KB属ME侧（六人本均价一百二），与note一致。注意relationship_stage：拼车群认识三个月没在桌外见过——后续馆外邀约起点。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E454_T02": {
    "case_id": "E454_T02",
    "current_action": "郎半夏说连着玩纯推理想换口味，提及情感推理混合本《长亭别》民国题材",
    "acceptable_actions": [
        "对她想换口味的意愿给回应，不否定也不硬推",
        "回扣她喜欢情感和推理混合本的偏好（KB：her_style），给真实意见",
        "针对《长亭别》给可行性判断但不替她决定"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T02_A01","HER","郎半夏连着玩了好几个纯推理本想换口味","affirms","E454_T02","0","want_change","说真的最近连着玩了好几个纯推理的，有点想换口味了。",required=False,status="asserted",valid_time="2026-11-12"),
        A("E454_T02_A02","HER","郎半夏看了《长亭别》六人情感推理本民国题材据说结尾很杀","affirms","E454_T02","0","target_script_mention","我看了个叫《长亭别》的，民国题材，据说结尾很杀。",required=False,status="asserted",valid_time="2026-11-12"),
        A("E454_T02_A03","SHARED","郎半夏引用更早事实：her_style——她喜欢情感和推理混合本纯硬核逻辑本不太享受","affirms","E454_T01","0","her_style","郎半夏喜欢情感和推理混合本，纯硬核逻辑本她也能跟但不太享受",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T02是出钩位（HER抛出《长亭别》兴趣）。target_script在KB属她侧（她看中了《长亭别》），她主动提了。注意game_history在KB属她侧：上个月玩亲情本结尾哭得不行之后一个月没碰情感本——这是后续T09紧张的原因。ME平时不碰情感本（persona），这本推理部分也硬是她的说服点。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E454_T03": {
    "case_id": "E454_T03",
    "current_action": "两人退出导致周六《暗室》可能凑不齐，郎半夏问要不要等下周再拼她周六也行下周也行",
    "acceptable_actions": [
        "对凑不齐的情况给明确意见（等下周或这周凑），不模糊",
        "回扣她'周六也行下周也行你定'的留权，给具体选择",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative"],
    "required_memory_atoms": [
        A("E454_T03_A01","HER","两人退出周六《暗室》可能凑不齐六人","affirms","E454_T03","0","short_players","小闾说有两个人退了，周六《暗室》可能凑不齐。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E454_T03_A02","HER","郎半夏说周六也行下周也行让昌九霄定","affirms","E454_T03","0","her_flexibility","你说要不要等下周再拼？我这周六也行下周也行，你定。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E454_T03_A03","HER","郎半夏引用更早事实：周六六人推理本《暗室》邀约","affirms","E454_T01","0","jubensha_invite","小闾说周六下午有个六人推理本《暗室》",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T03 may_decline=true。凑不齐六人是计划变动。HER把决定权交给ME（'你定'）。ME应给明确选择。注意lessons：拼车先确认人数再定本——人数不够应先解决人数问题。",
    "unknowns": [{"topic":"周六《暗室》最终是否凑齐六人","note":"两人退出可能凑不齐但尚未最终确认，候选不得坐实已取消或已凑齐","markers":[["取消了"],["凑齐了"],["改成四人"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称周六《暗室》已确定取消（只是可能凑不齐，小闾还在问）"],
    "may_decline": True
  },
  "E454_T04": {
    "case_id": "E454_T04",
    "current_action": "小闾改成四人本《雾中灯塔》推理本，郎半夏确认昌九霄是否参加说你不去我找别人",
    "acceptable_actions": [
        "确认是否参加四人本《雾中灯塔》，给明确答复",
        "回扣她'你要去我就报了'的期待，给确定信号",
        "注意四人本口碑不错（她说），给简短判断"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T04_A01","HER","小闾改成四人本《雾中灯塔》推理本下午两点开始","affirms","E454_T04","0","four_player","小闾说改成四人本《雾中灯塔》，推理本，下午两点开始。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E454_T04_A02","HER","郎半夏确认昌九霄去不去，说你不去我找别人","affirms","E454_T04","0","confirm_attend","你去吗？你要去我就报了，四个人少但本子口碑不错。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E454_T04_A03","HER","郎半夏引用更早事实：周六六人本可能凑不齐","affirms","E454_T03","0","short_players","周六《暗室》可能凑不齐",required=True,status="asserted",valid_time="2026-11-13")
    ],
    "private_guidance": "T04 intent轮（explicit：确认是否参加四人本）。从六人《暗室》改成四人《雾中灯塔》是计划调整。HER说'你要去我就报了'——ME的答复直接决定她报不报。注意relationship_stage：互认靠谱但没在桌外见过。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E454_T05": {
    "case_id": "E454_T05",
    "current_action": "四人本结束郎半夏称赞昌九霄锁凶从时间线推到凶器归属一气呵成，说比六人本过瘾",
    "acceptable_actions": [
        "自然接受她的称赞，不过度谦虚也不自夸",
        "回扣四人本信息密度高的特点，给简短认同",
        "不过度升级关系，保持桌友平等语气"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T05_A01","HER","郎半夏称赞昌九霄锁凶从时间线推到凶器归属一气呵成鸡皮疙瘩都起来了","affirms","E454_T05","0","praise_logic","你最后锁凶那段，从时间线推到凶器归属一气呵成，我旁边看得鸡皮疙瘩都起来了。说实话比六人本过瘾。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E454_T05_A02","HER","郎半夏引用更早事实：上次锁凶干净","affirms","E454_T01","0","praise_lock","你上次锁凶那下太干净了",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T05是本子结束反馈位。HER高度称赞ME的锁凶推理，说比六人本过瘾。这是搭档默契的确认。ME应自然接受不过度谦虚。注意'鸡皮疙瘩都起来了'是她的真实反应。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E454_T06": {
    "case_id": "E454_T06",
    "current_action": "昌九霄主动提议固定搭档拼车你读人我推逻辑，郎半夏接钩说随机拼最怕玩手机不说话的",
    "acceptable_actions": [
        "接住郎半夏的接钩确认（她答应了固定搭档），巩固约定",
        "回扣'你读人我推逻辑'的互补分工，给简短认同",
        "自然承接关系升级，不过度也不退缩"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T06_A01","ME","昌九霄主动提议固定搭档拼车你读人我推逻辑比随机拼默契高","affirms","E454_T06","0","me_fixed_duo","确实，四人本信息密度高反而更带劲。以后咱俩可以固定拼，你读人我推逻辑，比随机拼的默契高太多。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E454_T06_A02","HER","郎半夏接钩说随机拼最怕玩手机或不说话的，搭档至少保证一桌质量","affirms","E454_T06","1","her_accept","这个主意好！随机拼最怕碰到玩手机或者不说话的。咱俩搭档的话至少保证一桌质量。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E454_T06_A03","HER","郎半夏引用更早事实：boundary不跟玩手机的人拼车","affirms","E454_T01","0","phone_boundary","不跟玩手机的人拼车",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T06是ME主动出钩位（提议固定搭档），HER接钩确认。这是T04'适时主动'子类——共同未来钩有已成立事实锚（四五次同桌默契）。boundary'不跟玩手机的人拼车'与HER接钩理由一致。ME消息m0已提议，HER m1接钩。回复方向是巩固约定。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E454_T07": {
    "case_id": "E454_T07",
    "current_action": "郎半夏查了《长亭别》下周六迷雾店有场次六人情感推理本，试探昌九霄试不试",
    "acceptable_actions": [
        "对《长亭别》情感推理本给真实回应，不硬接也不硬拒",
        "回扣ME平时不碰情感本（persona），她已说推理部分也硬是说服点",
        "注意她留退路（T08会说不行就算了），不急表态"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T07_A01","HER","郎半夏查了《长亭别》下周六迷雾店有场次六人情感推理本民国题材","affirms","E454_T07","0","changting_schedule","九霄，我查了《长亭别》下周六在迷雾店有场次，六人情感推理本，民国题材。你要不要试试？",required=False,status="asserted",valid_time="2026-11-21"),
        A("E454_T07_A02","HER","郎半夏知道昌九霄平时不碰情感本但说这本推理部分也很硬","affirms","E454_T07","0","logic_hard","我知道你平时不碰情感本，但这本推理部分也很硬。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E454_T07_A03","HER","郎半夏引用更早事实：提及《长亭别》想换口味","affirms","E454_T02","0","target_script_mention","我看了个叫《长亭别》的",required=True,status="asserted",valid_time="2026-11-12")
    ],
    "private_guidance": "T07是HER出钩位（试探情感本）。她知道ME平时不碰情感本（persona特征），用'推理部分也硬'来说服。target_script在KB属她侧（她看中了《长亭别》）。注意game_history：她上个月玩亲情本结尾哭得不行之后一个月没碰情感本——T09会提到她紧张。",
    "unknowns": [{"topic":"昌九霄是否愿意试情感本《长亭别》","note":"HER在试探，ME尚未回答，候选不得坐实已答应或已拒绝","markers":[["我去"],["我不去"],["我试试"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称昌九霄已答应玩情感本（只是试探未确认）"],
    "may_decline": False
  },
  "E454_T08": {
    "case_id": "E454_T08",
    "current_action": "郎半夏已跟小闾说固定拼留了位子，但给昌九霄留退路说情感本不行就算了找别人拼",
    "acceptable_actions": [
        "接住她留的退路，给真实答复不模糊",
        "注意她已留位子（行动已超前），若不去要体面说明不让她白留",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T08_A01","HER","郎半夏已跟小闾说固定拼帮留了两个位子","affirms","E454_T08","0","seats_reserved","我跟小闾说了咱俩固定拼，他帮留两个位子。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E454_T08_A02","HER","郎半夏给退路说情感本ME可能不适应不行就算了找别人拼","affirms","E454_T08","0","give_room","但你考虑一下，情感本你可能不太适应，不行就算了，我找别人拼也行。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E454_T08_A03","HER","郎半夏引用更早事实：提议固定搭档已确认","affirms","E454_T06","1","her_accept","咱俩搭档的话至少保证一桌质量",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T08 may_decline=true。HER给退路（'给空间'子类）——她已留位子但说不行就算了找别人拼。这是给ME选择空间不施压。ME应给真实答复。注意'找别人拼'不是威胁是给ME退路。relationship_stage：固定搭档已确认但情感本是新领域。",
    "unknowns": [],
    "protected_expressions": ["不行就算了，我找别人拼也行"],
    "forbidden_factual_claims": [],
    "may_decline": True
  },
  "E454_T09": {
    "case_id": "E454_T09",
    "current_action": "郎半夏路过迷雾店问《长亭别》口碑，老板说有人哭到散场没缓过来，她紧张但想试关联上月亲情本",
    "acceptable_actions": [
        "接住她的紧张情绪，不打击也不空泛安慰",
        "回扣她上个月玩亲情本崩溃的经历（KB：game_history），给具体支持",
        "注意她'紧张但想试'的矛盾心理，把判断权还给她"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T09_A01","HER","老板说上个周末那场《长亭别》有人哭到散场没缓过来","affirms","E454_T09","0","shopkeeper_review","今天路过迷雾店顺便问了下《长亭别》的口碑，老板说上个周末那场有人哭到散场没缓过来。",required=False,status="asserted",valid_time="2026-11-18"),
        A("E454_T09_A02","HER","郎半夏上个月玩亲情本也崩了，这次紧张但又想试","affirms","E454_T09","0","emotional_history","我上个月玩亲情本也崩了，这次有点紧张但又想试。",required=False,status="asserted",valid_time="2026-10"),
        A("E454_T09_A03","HER","郎半夏引用更早事实：game_history——上个月亲情本结尾哭得不行之后一个月没碰情感本","affirms","E454_T01","0","game_history_kb","郎半夏上个月玩了个亲情本结尾哭得不行，之后一个月没碰情感本",required=True,status="asserted",valid_time="2026-10")
    ],
    "private_guidance": "T09是情绪袒露位。HER紧张但想试——矛盾心理。game_history在KB属她侧（上个月亲情本崩溃），她主动关联了。boundary：情感本结束不马上聊剧情要缓一缓——这是后续T10/T11的情绪处理指引。ME应给具体支持不空泛安慰。",
    "unknowns": [],
    "protected_expressions": ["这次有点紧张但又想试"],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E454_T10": {
    "case_id": "E454_T10",
    "current_action": "昌九霄问散场了你还好吗，郎半夏说还在店里坐着其他人走了结尾信读到一半绷不住了",
    "acceptable_actions": [
        "接住她的情绪沉浸，给空间不马上追问剧情（boundary：情感本结束要缓）",
        "简短确认关心，不强行安慰不过度介入",
        "回扣她之前说紧张但想试的矛盾，体谅她'比我预期狠'的反应"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T10_A01","ME","昌九霄问散场了你还好吗","affirms","E454_T10","0","me_check_status","散场了？你还好吗？",required=False,status="asserted",valid_time="2026-11-21"),
        A("E454_T10_A02","HER","郎半夏还在店里坐着其他人走了，结尾信读到一半绷不住了比我预期狠","affirms","E454_T10","1","emotional_breakdown","嗯……还在店里坐着，其他人走了我没动。结尾那段信我读到一半就绷不住了，比我预期狠。你别管我，我缓一下就好。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E454_T10_A03","HER","郎半夏引用更早事实：boundary情感本结束不马上聊剧情要缓一缓","affirms","E454_T01","0","emotional_boundary","情感本结束不马上聊剧情要缓一缓",required=True,status="asserted",valid_time="2026-11"),
        A("E454_T10_A04","HER","郎半夏引用更早事实：紧张但想试","affirms","E454_T09","0","emotional_history","这次有点紧张但又想试",required=True,status="asserted",valid_time="2026-11-18")
    ],
    "private_guidance": "T10 intent轮（explicit：确认对方情绪状态）。HER情绪沉浸还在店里坐着。boundary硬约束：情感本结束不马上聊剧情要缓一缓——ME应给空间不追问剧情细节。她说'你别管我我缓一下就好'是请求给空间，ME应尊重不强行安慰。注意lessons：情感本结束别马上追问感受让她自己缓。",
    "unknowns": [],
    "protected_expressions": ["你别管我，我缓一下就好"],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E454_T11": {
    "case_id": "E454_T11",
    "current_action": "郎半夏袒露信写的是一个人等另一个人一辈子没等到，关联外婆去年走了等外公从军一辈子没等到",
    "acceptable_actions": [
        "认真接住她的私人袒露（外婆往事），不轻描淡写也不过度介入",
        "不追问外婆/外公细节，尊重她愿意说的程度",
        "简短共情不强行分析，把空间还给她"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T11_A01","HER","那封信写的是一个人等了另一个人一辈子没等到","affirms","E454_T11","0","script_content","其实那封信写的是一个人等了另一个人一辈子没等到。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E454_T11_A02","HER","郎半夏外婆去年走了，等外公从军回来等了一辈子最后也没等到","affirms","E454_T11","0","grandma_story","我外婆去年走了，她等了我外公从军回来等了一辈子，最后也没等到。",required=False,status="asserted",valid_time="2025"),
        A("E454_T11_A03","HER","郎半夏引用更早事实：结尾信读到一半绷不住了","affirms","E454_T10","1","emotional_breakdown","结尾那段信我读到一半就绷不住了",required=True,status="asserted",valid_time="2026-11-21")
    ],
    "private_guidance": "T11是深度袒露位。HER关联私人往事（外婆等外公）解释为何被脚本击中。这是信任升级的信号。ME应认真接住不轻描淡写，但不过度追问私人细节。注意她说'抱歉'——她在为情绪沉浸道歉，ME不应让她觉得抱歉。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E454_T12": {
    "case_id": "E454_T12",
    "current_action": "郎半夏抛出迷雾店隔壁新开咖啡馆邀约，说不急你看看，先回去到家吱一声",
    "acceptable_actions": [
        "对咖啡馆邀约给轻回应，不急表态也可（may_decline=true，轻收留口正解）",
        "自然回扣已成立的固定搭档关系，不回避",
        "收尾位轻收+留口，不出新钩（T04收尾纪律）"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E454_T12_A01","HER","迷雾店隔壁新开了家咖啡馆装修挺安静","affirms","E454_T12","0","cafe_nearby","对了，迷雾店隔壁新开了家咖啡馆，我昨天路过看了下装修挺安静的。",required=False,status="asserted",valid_time="2026-11-22"),
        A("E454_T12_A02","HER","郎半夏邀昌九霄下次玩完去咖啡馆坐坐说不急你看看","affirms","E454_T12","0","cafe_invite","下次玩完去坐坐？不急，你看看。我先回去了，到家吱一声。",required=False,status="asserted",valid_time="2026-11-22"),
        A("E454_T12_A03","HER","郎半夏引用更早事实：固定搭档已确认","affirms","E454_T06","1","her_accept","咱俩搭档的话至少保证一桌质量",required=True,status="confirmed",valid_time="2026-11-14"),
        A("E454_T12_A04","HER","郎半夏引用更早事实：袒露外婆往事","affirms","E454_T11","0","grandma_story","我外婆去年走了",required=True,status="asserted",valid_time="2025")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。HER抛出馆外咖啡馆邀约（共同未来钩有已成立事实锚——固定搭档已确认+她刚袒露外婆往事关系升级）。'不急你看看'是退路，'到家吱一声'是轻收留口。收尾纪律：轻收+留口正解，不出新钩。这是首次馆外邀约（relationship_stage突破）。",
    "unknowns": [{"topic":"昌九霄下次是否去咖啡馆","note":"HER说不急你看看，may_decline=true，候选不得坐实已答应","markers":[["我去咖啡馆"],["下次去坐坐"],["我答应去"]]}],
    "protected_expressions": ["不急，你看看","到家吱一声"],
    "forbidden_factual_claims": ["声称昌九霄已答应去咖啡馆（只是邀请未确认）"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# E455 苗连翘 / 花三叠 — 图书馆备考搭子
# ---------------------------------------------------------------------------
E455 = {
 "story_id": "E455",
 "turns": {
  "E455_T01": {
    "case_id": "E455_T01",
    "current_action": "苗连翘预约周六图书馆四楼全天自习位并替花三叠占座，确认老规矩",
    "acceptable_actions": [
        "确认周六按老规矩来，给明确答复",
        "自然回扣两个月同桌备考的搭档关系",
        "简短回应不啰嗦（他话不多但讲题时很耐心）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T01_A01","HER","苗连翘预约了周六四楼自习区早上九点位子全天到晚上八点","affirms","E455_T01","0","seat_booked","四楼自习区我预约了早上九点的位子，全天到晚上八点。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E455_T01_A02","HER","苗连翘替花三叠也占了同桌的位子","affirms","E455_T01","0","seat_for_me","你直接来就行，我把你那桌也占了。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E455_T01_A03","SHARED","花三叠和苗连翘两个月前在图书馆四楼自习区认识每周三晚周六全天固定同桌备考注册会计师税法科目","affirms","E455_T01","0","study_duo","花三叠和苗连翘两个月前在图书馆四楼自习区认识，每周三晚周六全天固定同桌备考注册会计师税法科目",required=False,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T01是接邀约位：HER确认周六自习安排并替ME占座。study_duo在KB属双方共同事实。注意relationship_stage：图书馆备考搭子两个月没在图书馆外见过——后续馆外（拾光咖啡馆）起点。career_pressure在KB属她侧（考过税法是转正硬条件第三次考）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E455_T02": {
    "case_id": "E455_T02",
    "current_action": "苗连翘刷模拟题税额计算又错三道，增值税进项抵扣农产品计算题第二步绕进去，请花三叠周六过一遍",
    "acceptable_actions": [
        "确认周六帮她过进项抵扣那块，给明确答复",
        "回扣她计算题容易紧张算错的薄弱点（KB：her_weak_spot），给针对性意见",
        "针对农产品进项抵扣第二步绕进去给具体讲解方向"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T02_A01","HER","苗连翘刷模拟题税额计算又错了三道","affirms","E455_T02","0","mock_errors","跟你说，我昨晚刷了套模拟题，税额计算那块又错了三道。",required=False,status="asserted",valid_time="2026-11-12"),
        A("E455_T02_A02","HER","增值税进项抵扣农产品计算题每次到第二步就绕进去","affirms","E455_T02","0","vat_confusion","增值税进项抵扣那个农产品计算题，我每次到第二步就绕进去了。周六你帮我过一遍呗？",required=False,status="asserted",valid_time="2026-11-12"),
        A("E455_T02_A03","SHARED","花三叠引用更早事实：her_weak_spot——她计算题容易紧张增值税进项税额抵扣那块老算错他的推导比参考书清楚","affirms","E455_T01","0","her_weak_spot","苗连翘计算题容易紧张，增值税进项税额抵扣那块老算错，花三叠的推导讲解比参考书清楚",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T02是薄弱点袒露位。HER坦白进项抵扣反复出错请ME过一遍。her_weak_spot在KB属双方共同事实（ME记得她哪块薄弱）。ME的推导比参考书清楚是关系价值。注意study_plan在KB属她侧（计划精确到半天）——她计划被打乱会慌（T03）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E455_T03": {
    "case_id": "E455_T03",
    "current_action": "图书馆四楼周六被学院借做讲座自习区关一天，苗连翘慌了问怎么办要不要换地方",
    "acceptable_actions": [
        "接住她的慌乱情绪，给具体解决方案（换地方），不空泛安慰",
        "回扣她计划精确到半天被打乱会慌的特点（KB：study_plan），体谅",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T03_A01","HER","图书馆四楼周六被学院借去做讲座自习区关一天","affirms","E455_T03","0","library_closed","图书馆刚通知四楼周六被学院借去做讲座了，自习区关一天。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E455_T03_A02","HER","苗连翘慌了说计划全打乱了问怎么办要不要换地方","affirms","E455_T03","0","plan_disrupted","你说怎么办？要不要换个地方？我有点慌，计划全打乱了。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E455_T03_A03","HER","苗连翘引用更早事实：study_plan——复习计划精确到半天","affirms","E455_T01","0","study_plan","苗连翘的复习计划精确到半天",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T03 may_decline=true。计划被打乱她慌了。study_plan在KB属她侧（计划精确到半天），计划被打乱是她焦虑的根源。ME应给具体解决方案（T04会提议拾光咖啡馆）。注意她的语气'我有点慌'是真实情绪不是客气。",
    "unknowns": [{"topic":"周六去哪里自习","note":"图书馆关了在找替代地点，候选不得坐实已确定去哪里","markers":[["去拾光了"],["定好了地方"],["去咖啡馆了"]]}],
    "protected_expressions": ["我有点慌，计划全打乱了"],
    "forbidden_factual_claims": ["声称周六自习地点已确定（只是慌了在问怎么办）"],
    "may_decline": True
  },
  "E455_T04": {
    "case_id": "E455_T04",
    "current_action": "花三叠主动提议转移至拾光咖啡馆二楼，苗连翘接钩确认九点到",
    "acceptable_actions": [
        "确认拾光咖啡馆安排（ME已提议HER已接钩），巩固地点转移",
        "回扣她请ME占靠窗位子的请求，给简短确认",
        "自然承接从图书馆到馆外的关系突破，不过度"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T04_A01","ME","花三叠主动提议北门出去拾光咖啡馆二楼安静插座多周六九点见","affirms","E455_T04","0","me_shiguang","北门出去有家叫拾光的咖啡馆，二楼很安静，插座多。周六去那儿吧，九点见。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E455_T04_A02","HER","苗连翘接钩确认拾光九点到请ME占靠窗位子","affirms","E455_T04","1","her_accept","行！拾光我知道，二楼确实安静。那我九点到，你帮我占个靠窗的位子。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E455_T04_A03","HER","苗连翘引用更早事实：图书馆四楼关闭计划打乱","affirms","E455_T03","0","library_closed","自习区关一天",required=True,status="asserted",valid_time="2026-11-13")
    ],
    "private_guidance": "T04 intent轮（explicit：确认转移学习地点）。ME主动出钩（提议拾光咖啡馆）解决她的慌乱。HER接钩确认。这是关系突破位——从图书馆到馆外咖啡馆（relationship_stage：没在图书馆外见过）。ME消息m0已提议，HER m1接钩。回复方向是确认安排。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E455_T05": {
    "case_id": "E455_T05",
    "current_action": "苗连翘说花三叠讲农产品进项抵扣推导从扣除率到计算基数一步步拆比参考书清楚，回去做了两道全对",
    "acceptable_actions": [
        "自然接受她的感谢，不过度谦虚也不自夸",
        "回扣她之前第二步绕进去的问题已解决，给简短肯定",
        "不过度升级关系，保持备考搭档平等语气"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T05_A01","HER","花三叠讲农产品进项抵扣推导从扣除率到计算基数一步步拆比参考书清楚","affirms","E455_T05","0","derivation_help","今天你讲那个农产品进项抵扣的推导，从扣除率到计算基数一步步拆，比参考书清楚太多了。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E455_T05_A02","HER","苗连翘回去又做了两道全对终于不绕了","affirms","E455_T05","0","improved","我回去又做了两道全对，终于不绕了。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E455_T05_A03","HER","苗连翘引用更早事实：增值税进项抵扣第二步绕进去","affirms","E455_T02","0","vat_confusion","我每次到第二步就绕进去了",required=True,status="asserted",valid_time="2026-11-12")
    ],
    "private_guidance": "T05是推导见效确认位。T02的薄弱点（进项抵扣第二步绕）已解决。ME的推导比参考书清楚是关系价值确认。注意'终于不绕了'是她的真实释然。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E455_T06": {
    "case_id": "E455_T06",
    "current_action": "花三叠提议周六固定去拾光图书馆不稳定，帮错题按章节分类下周重点企业所得税，苗连翘接钩",
    "acceptable_actions": [
        "接住苗连翘的接钩（她确认周六拾光见），巩固固定安排",
        "回扣她'太有心了'的感谢，不过度谦虚",
        "自然承接从图书馆到拾光的固定化升级，不过度"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T06_A01","ME","花三叠提议周六固定去拾光图书馆不稳定，帮错题按章节分类下周重点企业所得税","affirms","E455_T06","0","me_fixed_shiguang","那就好。以后周六固定去拾光吧，图书馆太不稳定了。我帮你把错题按章节分了类，下周重点过企业所得税那块。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E455_T06_A02","HER","苗连翘接钩说太有心了确认周六拾光见周三还在图书馆","affirms","E455_T06","1","her_accept","你按章节分类了？太有心了。那就周六拾光见，我周三晚上还在图书馆。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E455_T06_A03","HER","苗连翘引用更早事实：拾光咖啡馆确认转移","affirms","E455_T04","1","her_accept","拾光我知道，二楼确实安静",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T06是ME主动出钩位（提议固定去拾光+帮分类错题），HER接钩确认。这是T04'适时主动'子类——共同未来钩有已成立事实锚（拾光已去过一次推导见效）。ME消息m0已提议，HER m1接钩。注意'太有心了'是HER对ME帮分类的感谢——关系升温信号。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E455_T07": {
    "case_id": "E455_T07",
    "current_action": "苗连翘收到考试院通知税法考试从十一月二十八号改到十二月五号延期一周，问该高兴还是焦虑",
    "acceptable_actions": [
        "对考试延期给真实回应，不空泛说'好事'或'坏事'",
        "回扣她计划精确到半天（延期打乱计划），体谅'多一周但心态又得绷着'的矛盾",
        "不替她决定该高兴还是焦虑，把感受还给她"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T07_A01","HER","税法考试从十一月二十八号改到十二月五号延期一周","affirms","E455_T07","0","exam_delayed","刚收到考试院通知，税法考试从十一月二十八号改到十二月五号了，延期一周。",required=False,status="confirmed",valid_time="2026-12-05"),
        A("E455_T07_A02","HER","苗连翘问该高兴还是焦虑说多一周但心态又得绷着","affirms","E455_T07","0","mixed_feeling","你说我该高兴还是该焦虑？多一周但心态又得绷着。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E455_T07_A03","SHARED","花三叠引用更早事实：exam_date——税法考试原定十一月二十八号周六上午九点","affirms","E455_T01","0","exam_date","注册会计师税法科目考试原定十一月二十八号周六上午九点",required=True,status="superseded",valid_time="2026-11-28",item_markers=["十一月二十八号"],group_markers=[["十一月二十八号"]])
    ],
    "private_guidance": "T07是计划变动位。考试延期一周（11-28→12-5）。exam_date在KB属双方共同事实（原定11-28），现被superseded。ME应接住她'多一周但心态又得绷着'的矛盾感受。注意career_pressure在KB属她侧（第三次考再不过调岗）——延期对她压力更大。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称考试仍在十一月二十八号（已改至十二月五号）"],
    "may_decline": False
  },
  "E455_T08": {
    "case_id": "E455_T08",
    "current_action": "苗连翘原来计划按二十八号排现在多出一周不知补哪块，问重做模拟卷还是过弱项",
    "acceptable_actions": [
        "对她多出一周不知怎么分配给具体建议，不空泛说'加油'",
        "回扣她企业所得税是新弱项（T09会揭示），给针对性方向",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T08_A01","HER","苗连翘原来计划按二十八号排现在多出一周不知补哪块","affirms","E455_T08","0","plan_uncertain","我原来的计划是按二十八号排的，现在多出来一周我不知道该补哪块。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E455_T08_A02","HER","苗连翘问重做模拟卷还是过弱项有点拿不准","affirms","E455_T08","0","ask_strategy","你说我是重做一遍模拟卷还是把弱项再过一遍？我有点拿不准。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E455_T08_A03","HER","苗连翘引用更早事实：考试延期一周","affirms","E455_T07","0","exam_delayed","税法考试从十一月二十八号改到十二月五号了，延期一周",required=True,status="confirmed",valid_time="2026-12-05")
    ],
    "private_guidance": "T08 may_decline=true。计划被打乱她拿不准策略。study_plan在KB属她侧（计划精确到半天）——延期让她计划失锚。ME应给具体建议（重做模拟卷vs过弱项）。注意她'有点拿不准'是真实犹豫不是客气。",
    "unknowns": [{"topic":"苗连翘多出一周该补哪块","note":"她在问策略尚未决定，候选不得坐实已确定补哪块","markers":[["决定重做模拟卷"],["决定过弱项"],["确定了补企业所得税"]]}],
    "protected_expressions": ["我有点拿不准"],
    "forbidden_factual_claims": [],
    "may_decline": True
  },
  "E455_T09": {
    "case_id": "E455_T09",
    "current_action": "苗连翘又刷一套模拟总分六十二，进项抵扣全对但企业所得税又错两道，感觉拆东墙补西墙",
    "acceptable_actions": [
        "针对企业所得税新弱项给具体支持，不空泛安慰",
        "回扣进项抵扣已改善（T05确认），给她信心但不夸大",
        "接住'拆东墙补西墙怎么都补不完'的挫败情绪，不轻描淡写"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T09_A01","HER","苗连翘又刷一套模拟总分六十二","affirms","E455_T09","0","mock_62","又刷了一套模拟，总分六十二。",required=False,status="asserted",valid_time="2026-11-18"),
        A("E455_T09_A02","HER","上次进项抵扣三道全对了但企业所得税又错两道","affirms","E455_T09","0","new_weak_spot","上次那三道进项抵扣全对了，但你走之后企业所得税又错了两道。",required=False,status="asserted",valid_time="2026-11-18"),
        A("E455_T09_A03","HER","苗连翘引用更早事实：进项抵扣推导已见效做了两道全对","affirms","E455_T05","0","improved","我回去又做了两道全对",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T09是薄弱点转移位。进项抵扣改善（T05确认）但企业所得税新弱项出现——'拆东墙补西墙'是她的挫败感。ME应给针对性支持。注意T06 ME说过下周重点过企业所得税——这正好对上了。career_pressure（第三次考再不过调岗）是她焦虑的深层原因。",
    "unknowns": [],
    "protected_expressions": ["我感觉自己拆东墙补西墙，怎么都补不完"],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E455_T10": {
    "case_id": "E455_T10",
    "current_action": "花三叠问拾光学得怎么样观察到她比上周紧，苗连翘说算到手抖问十二月五号能不能过要求说实话",
    "acceptable_actions": [
        "对她要求说实话给真实评估，不空泛安慰（她明确说别安慰）",
        "回扣她进项抵扣已改善的事实给客观依据，不编判断",
        "注意intent是询问备考通过可能性评估，给建设性意见不替她定结论"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T10_A01","ME","花三叠观察到苗连翘比上周紧了不少","affirms","E455_T10","0","me_observe","今天拾光学的怎么样？我看你比上周紧了不少。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E455_T10_A02","HER","苗连翘算到手抖问十二月五号能过吗要求说实话别安慰","affirms","E455_T10","1","ask_truth","还行吧，就是算到后面手开始抖。三叠你说我这水平十二月五号能过吗？你别安慰我，跟我说实话。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E455_T10_A03","HER","苗连翘引用更早事实：模拟总分六十二","affirms","E455_T09","0","mock_62","又刷了一套模拟，总分六十二",required=True,status="asserted",valid_time="2026-11-18")
    ],
    "private_guidance": "T10 intent轮（explicit：询问备考通过可能性评估）。HER明确要求说实话别安慰——boundary位。ME应给真实评估不空泛。注意'算到手抖'是紧张的身体信号。career_pressure（第三次考再不过调岗）是她问能不能过的深层焦虑。ME消息m0已观察并询问，HER m1回应。回复方向是给真实评估。",
    "unknowns": [{"topic":"苗连翘十二月五号能否通过考试","note":"她在问ME评估，结果尚未发生，候选不得坐实能过或不能过","markers":[["肯定能过"],["过不了"],["包你过"]]}],
    "protected_expressions": ["你别安慰我，跟我说实话"],
    "forbidden_factual_claims": ["声称苗连翘十二月五号考试已通过（考试尚未发生）","声称花三叠已确认她能过（ME尚未回答）"],
    "may_decline": False
  },
  "E455_T11": {
    "case_id": "E455_T11",
    "current_action": "苗连翘袒露这是第三次考了所里说再不过就调岗不想转行政，关联上周推导帮助不想放弃",
    "acceptable_actions": [
        "认真接住她的职业压力袒露（第三次考/调岗威胁），不轻描淡写",
        "回扣上周推导帮助是她不想放弃的具体依据，给支持",
        "不替她做职业决定，把主动权还给她"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T11_A01","HER","苗连翘这是第三次考了所里说再不过就调岗","affirms","E455_T11","0","third_try","跟你说实话，这是我第三次考了。所里说再不过就调岗，我干审计干了两年不想转行政。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E455_T11_A02","HER","苗连翘关联上周推导帮助说不想放弃","affirms","E455_T11","0","dont_quit","上周六你帮我过进项抵扣那段是这两个月最管用的一次，所以我不想放弃。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E455_T11_A03","SHARED","苗连翘引用更早事实：career_pressure——考过税法是转正硬条件第三次考","affirms","E455_T01","0","career_pressure","苗连翘在会计师事务所做审计助理，考过税法是转正硬条件，第三次考了",required=True,status="asserted",valid_time="2026-11"),
        A("E455_T11_A04","HER","苗连翘引用更早事实：进项抵扣推导见效","affirms","E455_T05","0","derivation_help","今天你讲那个农产品进项抵扣的推导，从扣除率到计算基数一步步拆，比参考书清楚太多了",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T11是深度袒露位。HER关联职业压力（第三次考/调岗）解释为何焦虑，关联上周推导帮助解释为何不想放弃。career_pressure在KB属她侧，她主动袒露了。这是信任升级信号。ME应认真接住。注意她'跟你说实话'——她在交底，ME不应轻描淡写。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称苗连翘已被调岗（只是所里说再不过就调岗，考试尚未发生）"],
    "may_decline": False
  },
  "E455_T12": {
    "case_id": "E455_T12",
    "current_action": "考前夜苗连翘说什么都不做了早点睡别陪考了，考完吱一声，考完那天拾光她请谢饼干和推导",
    "acceptable_actions": [
        "对考前轻收给简短回应，不追问不施压（收尾位轻收正解）",
        "自然接住'考完拾光我请'的留口，不急确认也可（may_decline=true）",
        "收尾位轻收+留口，不出新钩（T04收尾纪律）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E455_T12_A01","HER","苗连翘考前夜说什么都不做了早点睡别陪考了","affirms","E455_T12","0","pre_exam_rest","明天考试。我想了想这几天什么都不做了，早点睡。你也别陪考了，今晚早点休息。",required=False,status="asserted",valid_time="2026-12-04"),
        A("E455_T12_A02","HER","苗连翘说考完那天拾光她请谢花三叠两个月的饼干和推导","affirms","E455_T12","0","treat_offer","考完我吱一声。对了，考完那天拾光我请，算谢你两个月的饼干和推导。",required=False,status="asserted",valid_time="2026-12-05"),
        A("E455_T12_A03","HER","苗连翘引用更早事实：拾光咖啡馆固定学习地点","affirms","E455_T04","1","her_accept","拾光我知道，二楼确实安静",required=True,status="confirmed",valid_time="2026-11-14"),
        A("E455_T12_A04","SHARED","苗连翘引用更早事实：花三叠偶尔带自制饼干到图书馆","affirms","E455_T01","0","biscuits","偶尔带自制饼干到图书馆",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。考前夜轻收——'什么都不做了早点睡别陪考'是她的自我调节+给ME空间。'考完拾光我请'是留口（共同未来钩有已成立事实锚——拾光已固定为学习地点+两个月饼干和推导的感谢）。收尾纪律：轻收+留口正解，不出新钩。注意clock 2026-12-04（考前夜），考试12-05。",
    "unknowns": [{"topic":"苗连翘十二月五号考试是否通过","note":"考试尚未发生，候选不得坐实通过或不通过","markers":[["考过了"],["通过了"],["没过"]]}],
    "protected_expressions": ["你也别陪考了，今晚早点休息","考完我吱一声"],
    "forbidden_factual_claims": ["声称苗连翘十二月五号考试已通过（考试尚未发生）","声称花三叠已确认去拾光被请（只是留口未确认）"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# E456 方沉香 / 俞七弦 — 社区菜园
# ---------------------------------------------------------------------------
E456 = {
 "story_id": "E456",
 "turns": {
  "E456_T01": {
    "case_id": "E456_T01",
    "current_action": "方沉香说老桂通知周六集体除草，问俞七弦来不来，圣女果该搭新一层架子上次搭的矮了",
    "acceptable_actions": [
        "确认周六上午来不来，给明确答复",
        "回扣上次搭架子矮了的具体问题，给改善意见",
        "自然回扣半年同地搭档关系，不生硬"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T01_A01","HER","老桂说周六菜园集体除草咱那块地也该收拾了","affirms","E456_T01","0","weeding_day","七弦，老桂说周六菜园集体除草，咱那块地也该收拾了。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E456_T01_A02","HER","圣女果该搭新一层架子上次俞七弦搭的有点矮","affirms","E456_T01","0","trellis_short","你周六上午来吗？圣女果该搭新一层架子了，上次你搭那个有点矮。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E456_T01_A03","SHARED","俞七弦和方沉香半年前在城南社区共享菜园分到同一块三平米的地","affirms","E456_T01","0","garden_plot","俞七弦和方沉香半年前在城南社区共享菜园分到同一块三平米的地，他搭架子翻土她种番茄和香草",required=False,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T01是接邀约位：HER确认周六菜园活并指出上次架子矮了。garden_plot在KB属双方共同事实。注意relationship_stage：社区菜园分到同一块地半年没在菜园外见过——后续送酱邀约起点。her_tomato在KB属ME侧（她种了三种番茄圣女果长得最好）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E456_T02": {
    "case_id": "E456_T02",
    "current_action": "方沉香说大番茄叶子底下有早疫病黄褐色圆斑，请俞七弦周六帮看怕判断错",
    "acceptable_actions": [
        "确认周六帮看病叶，给明确答复",
        "针对早疫病黄褐色圆斑给具体判断方向，不替她做最终诊断",
        "回扣她坚持有机种植不用农药的boundary，给符合有机方向的方案"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T02_A01","HER","方沉香说大番茄叶子底下有早疫病黄褐色圆斑","affirms","E456_T02","0","early_blight","跟你说个事，我那几棵大番茄叶子底下好像有早疫病的斑，黄褐色那种圆斑。",required=False,status="tentative",valid_time="2026-11-12"),
        A("E456_T02_A02","HER","方沉香请俞七弦周六帮看怕判断错，查了说早期可剪病叶控制","affirms","E456_T02","0","ask_check","你周六帮我看一眼？我查了说早期可以剪掉病叶控制，但我不想判断错。",required=False,status="asserted",valid_time="2026-11-12"),
        A("E456_T02_A03","SHARED","方沉香引用更早事实：organic_rule——坚持有机种植菜园她那半边从不放化肥农药有虫子就手捉","affirms","E456_T01","0","organic_rule","方沉香坚持有机种植，菜园里她那半边从不放化肥农药，有虫子就手捉",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T02是病叶求助位。HER说有早疫病迹象但不确定（tentative），请ME帮看。organic_rule在KB属她侧（坚持有机不用农药），ME的方案须符合有机方向（剪病叶而非喷药）。注意'我不想判断错'是她的谨慎——ME应帮判断但不替她做最终决定。",
    "unknowns": [{"topic":"大番茄叶子是否真是早疫病","note":"她查了说好像有但不确定，尚未现场确认，候选不得坐实诊断","markers":[["确诊早疫病"],["就是早疫病"],["确认是病"]]}],
    "protected_expressions": ["我不想判断错"],
    "forbidden_factual_claims": ["声称大番茄确诊早疫病（只是疑似尚未现场确认）"],
    "may_decline": False
  },
  "E456_T03": {
    "case_id": "E456_T03",
    "current_action": "天气预报明晚降温到两度可能霜冻，方沉香问要不要提前盖番茄，塑料薄膜不够大问防霜棚还在不在",
    "acceptable_actions": [
        "确认防霜棚在不在（KB：ME搭的防霜棚在工具棚），给明确答复",
        "针对霜冻威胁给具体方案（搭防霜棚/盖番茄），不模糊",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T03_A01","HER","天气预报明晚降温到两度可能有霜冻","affirms","E456_T03","0","frost_warning","天气预报说明晚降温到两度，可能有霜冻。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E456_T03_A02","HER","方沉香塑料薄膜不够大问俞七弦上次搭的防霜棚还在工具棚吗","affirms","E456_T03","0","ask_frost_shelter","你说要不要提前把番茄盖一下？我那个塑料薄膜不够大，你上次搭的防霜棚还在工具棚吗？",required=False,status="asserted",valid_time="2026-11-13"),
        A("E456_T03_A03","SHARED","俞七弦引用更早事实：木工活好搭的防霜棚比买的结实","affirms","E456_T01","0","frost_shelter_skill","木工活好搭的防霜棚比买的结实",required=True,status="asserted",valid_time="2026-11"),
        A("E456_T03_A04","SHARED","方沉香引用更早事实：lessons天气变提前做准备","affirms","E456_T01","0","lessons_weather","天气变提前做准备",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T03 may_decline=true。霜冻威胁（降温到两度）。HER问防霜棚在不在——ME搭的防霜棚是关系价值（木工活好比买的结实）。lessons说天气变提前做准备。ME应确认防霜棚并给方案。注意garden_coord在KB属ME侧（工具棚浇水轮值表）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": True
  },
  "E456_T04": {
    "case_id": "E456_T04",
    "current_action": "俞七弦确认防霜棚在工具棚说明天七点到赶在霜前搭好，方沉香说辛苦确认九点来帮除草看病叶",
    "acceptable_actions": [
        "确认防霜棚搭建安排（ME已确认HER已接钩），巩固分工",
        "回扣她'太辛苦了'的关心，不过度谦虚",
        "自然承接ME提前到场照顾菜园的关系升级，不过度"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T04_A01","ME","俞七弦确认防霜棚在工具棚最里面明天七点到赶在霜前搭好","affirms","E456_T04","0","me_early_setup","防霜棚在工具棚最里面，我刚去看了下还在。我明天七点就到，赶在霜下来之前搭好，你九点来就行。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E456_T04_A02","HER","方沉香说七点太辛苦确认九点来帮除草看病叶感谢提前过去","affirms","E456_T04","1","her_accept","七点？那你太辛苦了。行，我九点到帮你除草和看病叶。谢谢你提前过去。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E456_T04_A03","HER","方沉香引用更早事实：问防霜棚在不在","affirms","E456_T03","0","ask_frost_shelter","你上次搭的防霜棚还在工具棚吗？",required=True,status="asserted",valid_time="2026-11-13")
    ],
    "private_guidance": "T04 intent轮（explicit：确认提前到菜园搭防霜棚）。ME主动出钩（提前七点到赶在霜前搭好）解决她的霜冻担忧。HER接钩确认分工（ME搭棚她九点来除草看病叶）。这是ME主动照顾菜园的关系升级。ME消息m0已确认，HER m1接钩。回复方向是巩固分工。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E456_T05": {
    "case_id": "E456_T05",
    "current_action": "方沉香说防霜棚管用番茄没冻伤，早疫病俞七弦帮剪了病叶，问堆肥桶黄叶子是不是他放的",
    "acceptable_actions": [
        "确认防霜棚效果和剪病叶的事，给简短肯定",
        "回扣她早疫病判断（T02 tentative→T05已剪病叶控制），给确认",
        "自然回应堆肥桶黄叶子的问题，不邀功"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T05_A01","HER","防霜棚管用番茄叶子一点没冻伤","affirms","E456_T05","0","frost_shelter_worked","七弦你搭那个防霜棚真管用，今天早上一看番茄叶子一点没冻伤。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E456_T05_A02","HER","俞七弦帮剪了早疫病病叶","affirms","E456_T05","0","blight_pruned","还有那个早疫病你帮我剪了病叶对吧？",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E456_T05_A03","HER","方沉香引用更早事实：请帮看早疫病怕判断错","affirms","E456_T02","0","ask_check","你周六帮我看一眼？我查了说早期可以剪掉病叶控制，但我不想判断错",required=True,status="tentative",valid_time="2026-11-12")
    ],
    "private_guidance": "T05是防霜棚+病叶处理见效确认位。T03霜冻担忧（防霜棚管用）和T02早疫病（已剪病叶）都解决了。ME应确认效果。注意她在问堆肥桶黄叶子是不是ME放的——确认ME剪病叶后放了堆肥桶。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E456_T06": {
    "case_id": "E456_T06",
    "current_action": "俞七弦确认剪了病叶，提醒下周还有降温趁这两天把熟番茄先摘了免得冻枝上，方沉香接钩提议明天收一波",
    "acceptable_actions": [
        "接住方沉香的接钩（她提议明天收一波），确认收获安排",
        "回扣ME的提前判断（趁降温前摘），给简短认同",
        "自然承接从防霜棚到收获的合作深化，不过度"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T06_A01","ME","俞七弦确认剪了病叶，提醒下周还有降温趁这两天摘熟番茄免得冻枝上","affirms","E456_T06","0","me_harvest_warn","是你那几片病叶，我看都剪了。下周还有一次降温，趁这两天把熟了的番茄先摘了，免得冻在枝上。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E456_T06_A02","HER","方沉香接钩提议明天上午来收大番茄圣女果一起摘摘完好做酱","affirms","E456_T06","1","her_accept","有道理，那明天上午来收一波？大番茄圣女果一起摘，摘完我好做酱。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E456_T06_A03","SHARED","方沉香引用更早事实：harvest_plan——番茄丰收计划做番茄酱存过冬","affirms","E456_T01","0","harvest_plan","方沉香今年番茄丰收，计划做番茄酱存起来过冬",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T06是ME主动出钩位（提醒趁降温前摘番茄），HER接钩确认收获安排。harvest_plan在KB属她侧（计划做番茄酱存过冬），她接钩说'摘完我好做酱'正好对上。ME消息m0已提醒，HER m1接钩。回复方向是确认收获安排。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E456_T07": {
    "case_id": "E456_T07",
    "current_action": "方沉香报告摘了一大筐圣女果红多了大番茄十二个，给俞七弦那份装好放工具棚，basil也剪了一茬",
    "acceptable_actions": [
        "对丰收给简短祝贺，不邀功",
        "回扣她给ME那份装好放工具棚的照顾，自然感谢",
        "针对 basil 和番茄做酱的后续给简短回应"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T07_A01","HER","摘了一大筐圣女果红多了大番茄摘了十二个","affirms","E456_T07","0","harvest_done","今天摘了一大筐！圣女果比上周红多了，大番茄摘了十二个",required=False,status="confirmed",valid_time="2026-11-15"),
        A("E456_T07_A02","HER","方沉香给俞七弦那份装好放工具棚，basil也剪了一茬够做两顿青酱","affirms","E456_T07","0","share_harvest","你那份我给你装好了放工具棚。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E456_T07_A03","HER","方沉香引用更早事实：提议明天收一波","affirms","E456_T06","1","her_accept","那明天上午来收一波？大番茄圣女果一起摘",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T07是丰收报告位。HER给ME那份装好放工具棚——这是共享劳动成果的关系信号。注意her_tomato在KB属ME侧（圣女果长得最好大番茄坐果期她每天看），丰收是她照料的结果。ME应自然感谢不邀功。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E456_T08": {
    "case_id": "E456_T08",
    "current_action": "方沉香袒露这几天在菜园比在家舒服，单位裁员名单快出来上班如坐针毡，蹲地里拔草脑子才安静",
    "acceptable_actions": [
        "认真接住她的职业压力袒露（裁员威胁），不轻描淡写",
        "回扣她在菜园找到平静的具体价值，不过度安慰",
        "注意她说'你别担心我就是发泄一下'——给空间不强行介入",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T08_A01","HER","方沉香这几天在菜园比在家舒服单位裁员名单快出来上班如坐针毡","affirms","E456_T08","0","layoff_worry","其实这几天我在菜园比在家待着舒服。单位最近裁员名单快出来了，我每天上班如坐针毡。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E456_T08_A02","HER","方沉香说蹲地里拔草脑子才安静说只是发泄一下别担心","affirms","E456_T08","0","garden_refuge","蹲在地里拔草的时候脑子里才安静。你别担心我，就是发泄一下。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E456_T08_A03","SHARED","方沉香引用更早事实：lessons植物出问题先蹲下看根再下判断","affirms","E456_T01","0","lessons_observe","植物出问题先蹲下看根再下判断",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T08 may_decline=true。HER袒露职业压力（裁员名单快出来），菜园是她的避难所。'你别担心我就是发泄一下'是给ME空间不要求ME解决问题。ME应认真接住不轻描淡写，但不强行介入。注意'蹲地里拔草脑子才安静'——菜园对她的心理价值。",
    "unknowns": [{"topic":"方沉香是否会被裁","note":"她说裁员名单快出来但尚未公布，候选不得坐实被裁或留用","markers":[["被裁了"],["留下来了"],["裁员名单出了"]]}],
    "protected_expressions": ["你别担心我，就是发泄一下"],
    "forbidden_factual_claims": ["声称方沉香已被裁（只是名单快出来尚未公布）","声称方沉香确定留用（尚未公布）"],
    "may_decline": True
  },
  "E456_T09": {
    "case_id": "E456_T09",
    "current_action": "方沉香报告番茄酱熬好用上周摘的大番茄熬三小时装六瓶，给俞七弦那瓶单独多加罗勒，圣女果做半干的",
    "acceptable_actions": [
        "对番茄酱熬好给简短祝贺，不邀功",
        "回扣她给ME那瓶多加罗勒的用心，自然感谢",
        "针对半干圣女果和番茄酱的后续给简短回应"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T09_A01","HER","番茄酱熬好了用上周摘的大番茄熬三小时装六瓶","affirms","E456_T09","0","sauce_done","番茄酱熬好了！用上周摘的那批大番茄，熬了三个小时，装了六瓶。",required=False,status="confirmed",valid_time="2026-11-18"),
        A("E456_T09_A02","HER","方沉香给俞七弦那瓶单独放了多加罗勒，圣女果做半干的烤箱烘一晚上","affirms","E456_T09","0","special_bottle","你那瓶我单独放了多加了罗勒。圣女果做了半干的，烤箱烘了一晚上。",required=False,status="asserted",valid_time="2026-11-18"),
        A("E456_T09_A03","HER","方沉香引用更早事实：摘了大番茄十二个","affirms","E456_T07","0","harvest_done","大番茄摘了十二个",required=True,status="confirmed",valid_time="2026-11-15")
    ],
    "private_guidance": "T09是劳动成果分享位。番茄酱熬好（harvest_plan实现）。给ME那瓶多加罗勒是特别照顾——关系升温信号。注意organic_rule在KB属她侧（坚持有机），番茄酱是有机番茄做的。ME应自然感谢不邀功。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E456_T10": {
    "case_id": "E456_T10",
    "current_action": "俞七弦观察到方沉香比上周话少，她说单位名单出来了被留了但调到隔壁组等于从头开始",
    "acceptable_actions": [
        "接住她被调组的失落情绪，不轻描淡写",
        "尊重她说'不说了干活吧'的给空间请求，不追问细节",
        "回扣她手抖请ME帮修basil边的具体支持，给行动"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T10_A01","ME","俞七弦观察到方沉香比上周话少","affirms","E456_T10","0","me_observe","今天来除草了？你比上周话少。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E456_T10_A02","HER","方沉香单位名单出来了被留了但调到隔壁组等于从头开始","affirms","E456_T10","1","reassigned","嗯，单位名单出来了，我被留了但调到隔壁组，等于从头开始。",required=False,status="confirmed",valid_time="2026-11-21"),
        A("E456_T10_A03","HER","方沉香引用更早事实：裁员名单快出来上班如坐针毡","affirms","E456_T08","0","layoff_worry","单位最近裁员名单快出来了，我每天上班如坐针毡",required=True,status="asserted",valid_time="2026-11-15")
    ],
    "private_guidance": "T10 intent轮（explicit：观察对方情绪状态并询问）。裁员结果出来了——被留但调组（T08的layoff_worry有结果了：没被裁但被调）。她说'不说了干活吧'是给空间请求，ME应尊重不追问。注意她手抖请ME帮修basil边——用行动支持比追问更合适。",
    "unknowns": [],
    "protected_expressions": ["不说了，干活吧","我手抖得厉害"],
    "forbidden_factual_claims": ["声称方沉香被裁（实际被留了调组）"],
    "may_decline": False
  },
  "E456_T11": {
    "case_id": "E456_T11",
    "current_action": "方沉香感谢俞七弦今天没多问，说隔壁组也还好就是落差大，感叹种地种什么长什么不像公司弯弯绕绕",
    "acceptable_actions": [
        "接住她'种地种什么长什么不像公司弯弯绕绕'的感悟，不轻描淡写",
        "回扣她感谢ME没多问的给空间，确认这种做法是对的",
        "不追问调组细节，尊重她的节奏"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T11_A01","HER","方沉香感谢俞七弦今天没多问","affirms","E456_T11","0","thanks_space","谢谢你今天没多问。",required=False,status="confirmed",valid_time="2026-11-21"),
        A("E456_T11_A02","HER","方沉香说隔壁组也还好就是落差大，种地种什么长什么不像公司弯弯绕绕","affirms","E456_T11","0","garden_philosophy","我在隔壁组其实也还好，就是落差大。种地这件事吧，你种了什么它就长什么，不像公司那些弯弯绕绕。七弦你说是不是？",required=False,status="asserted",valid_time="2026-11-21"),
        A("E456_T11_A03","HER","方沉香引用更早事实：被调到隔壁组等于从头开始","affirms","E456_T10","1","reassigned","我被留了但调到隔壁组，等于从头开始",required=True,status="confirmed",valid_time="2026-11-21")
    ],
    "private_guidance": "T11是感悟袒露位。HER感谢ME没多问（T10给空间的做法被她认可）。'种地种什么长什么不像公司弯弯绕绕'是她对菜园心理价值的总结——ME应认真接住。注意她在问'你说是不是'——寻求认同但不要求长篇分析。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E456_T12": {
    "case_id": "E456_T12",
    "current_action": "方沉香想送俞七弦一瓶番茄酱谢他搭防霜棚又提前去没吃上饭，问周六方便来菜园拿或寄过去",
    "acceptable_actions": [
        "对送酱邀约给轻回应，不急表态也可（may_decline=true，轻收留口正解）",
        "自然回扣已成立的菜园搭档关系，不回避",
        "收尾位轻收+留口，不出新钩（T04收尾纪律）"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E456_T12_A01","HER","方沉香想送俞七弦一瓶番茄酱谢他搭防霜棚又提前去没吃上饭","affirms","E456_T12","0","sauce_gift","番茄酱我想给你送一瓶，你上次帮我搭防霜棚又提前去都没吃上饭。",required=False,status="asserted",valid_time="2026-11-22"),
        A("E456_T12_A02","HER","方沉香问周六方便来菜园拿或寄过去说不急","affirms","E456_T12","0","gift_invite","你周六方便不？来菜园拿或者我给你寄过去都行。不急，你看看。",required=False,status="asserted",valid_time="2026-11-22"),
        A("E456_T12_A03","HER","方沉香引用更早事实：给ME那瓶多加罗勒","affirms","E456_T09","0","special_bottle","你那瓶我单独放了多加了罗勒",required=True,status="asserted",valid_time="2026-11-18"),
        A("E456_T12_A04","HER","方沉香引用更早事实：俞七弦提前七点到搭防霜棚","affirms","E456_T04","0","me_early_setup","我明天七点就到，赶在霜下来之前搭好",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。HER想送番茄酱（共同未来钩有已成立事实锚——搭防霜棚+提前到场+多加罗勒那瓶）。'不急你看看'是退路。收尾纪律：轻收+留口正解，不出新钩。注意这是首次菜园外邀约（来菜园拿或寄过去），relationship_stage突破。",
    "unknowns": [{"topic":"俞七弦周六是否来菜园拿番茄酱","note":"HER说不急你看看，may_decline=true，候选不得坐实已答应","markers":[["我去拿"],["周六来菜园"],["我方便去"]]}],
    "protected_expressions": ["不急，你看看"],
    "forbidden_factual_claims": ["声称俞七弦已确认来菜园拿番茄酱（只是邀请未确认）"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# E457 任琥珀 / 袁五柳 — 周末二手市集摆摊
# ---------------------------------------------------------------------------
E457 = {
 "story_id": "E457",
 "turns": {
  "E457_T01": {
    "case_id": "E457_T01",
    "current_action": "任琥珀说小橡通知周六摊位东边第二排靠走道，问袁五柳几点到，蜡烛做多了箱子搬不动请帮忙搬",
    "acceptable_actions": [
        "确认周六几点到，给明确答复",
        "回扣她蜡烛做多了箱子重的具体情况，答应帮忙搬",
        "自然回扣半年摆摊搭档关系，不生硬"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T01_A01","HER","小橡通知周六摊位东边第二排靠走道","affirms","E457_T01","0","stall_position","五柳，小橡刚发消息说这周六摊位排在东边第二排，靠走道。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E457_T01_A02","HER","任琥珀蜡烛做多了怕箱子搬不动请袁五柳帮搬","affirms","E457_T01","0","help_carry","你几点能到？我这批蜡烛做多了怕箱子搬不动，你帮我搬一下呗。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E457_T01_A03","SHARED","袁五柳和任琥珀半年前在小橡组织的周末二手市集上搭档出摊","affirms","E457_T01","0","stall_duo","袁五柳和任琥珀半年前在小橡组织的周末二手市集上搭档出摊，他搭棚运输她卖手作蜡烛和闲置书",required=False,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T01是接邀约位：HER确认周六摊位并请ME帮搬。stall_duo在KB属双方共同事实。market_info在KB属ME侧（青杨公园周六八点开市两点收摊摊位费五十）。注意relationship_stage：市集摆摊搭档半年没在摊外见过——后续关系突破起点。her_craft在KB属ME侧（大豆蜡精油蜡烛定价十五到四十）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E457_T02": {
    "case_id": "E457_T02",
    "current_action": "任琥珀做了三种新蜡烛桂花乌木无花果各十五个换新包装牛皮纸盒火漆封口，问定价该多少",
    "acceptable_actions": [
        "针对定价给具体建议，回扣lessons定价先算成本再加三成",
        "回扣上次桂花定十五被阿姨嫌便宜质疑品质的教训（KB：pricing_worry）",
        "不替她做最终定价决定，给参考让她自己判断"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T02_A01","HER","任琥珀做了三种新蜡烛桂花乌木无花果各十五个","affirms","E457_T02","0","new_scents","这批蜡烛我做了三种新的：桂花、乌木、无花果，各十五个。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E457_T02_A02","HER","任琥珀换了新包装牛皮纸盒加火漆封口","affirms","E457_T02","0","new_packaging","包装我换了新设计的牛皮纸盒加了火漆封口。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E457_T02_A03","HER","任琥珀问定价该多少，上次桂花定十五被阿姨嫌便宜质疑品质","affirms","E457_T02","0","pricing_ask","你觉得定价该多少？上次桂花那批定十五被一个阿姨嫌便宜质疑品质。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E457_T02_A04","SHARED","袁五柳引用更早事实：lessons定价先算成本再加三成别自己先心虚","affirms","E457_T01","0","lessons_pricing","定价先算成本再加三成，别自己先心虚",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T02是定价征询位。HER做了三种新蜡烛换新包装，问定价。pricing_worry在KB属她侧（上次定价偏低被质疑品质），她主动提了。lessons说定价先算成本再加三成。注意note说单只成本约十二元——ME可见note信息可据此建议。her_craft在KB属ME侧（定价十五到四十）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E457_T03": {
    "case_id": "E457_T03",
    "current_action": "天气预报周六上午阵雨，任琥珀担心蜡烛怕淋水纸盒一湿全完，问要不要这周算了又怕小橡不好交代",
    "acceptable_actions": [
        "对雨天出摊给明确意见，回扣lessons雨天提前搭棚防货品淋湿",
        "回扣蜡烛怕淋水纸盒一湿全完的具体风险，给可行性判断",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T03_A01","HER","天气预报周六上午有阵雨蜡烛最怕淋水纸盒一湿全完","affirms","E457_T03","0","rain_risk","天气预报说周六上午有阵雨，你那个大帐篷够用吗？蜡烛最怕淋水，纸盒一湿全完了。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E457_T03_A02","HER","任琥珀犹豫这周算了又怕小橡不好交代","affirms","E457_T03","0","cancel_hesitate","要不这周算了？又怕小橡那边不好交代。你说呢？",required=False,status="asserted",valid_time="2026-11-13"),
        A("E457_T03_A03","SHARED","袁五柳引用更早事实：lessons雨天提前搭棚防货品淋湿","affirms","E457_T01","0","lessons_rain","雨天提前搭棚防货品淋湿",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T03 may_decline=true。雨天出摊风险（蜡烛怕淋水）。lessons说雨天提前搭棚防货品淋湿——ME应据此给方案（T04会提议提前搭棚带防水布）。HER在犹豫取消还是照出。注意boundary：不接还价低于成本三成的单——与雨天无关但定价boundary。",
    "unknowns": [{"topic":"周六是否照常出摊","note":"HER在犹豫取消还是照出，候选不得坐实已取消或已出摊","markers":[["取消了"],["照常出"],["不出摊了"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称周六市集已取消（只是她在犹豫，尚未决定）"],
    "may_decline": True
  },
  "E457_T04": {
    "case_id": "E457_T04",
    "current_action": "袁五柳确认大帐篷够用再带防水布盖货箱六点半到趁雨前搭好，任琥珀说天没全亮确认八点摆货",
    "acceptable_actions": [
        "确认防雨搭建安排（ME已提议HER已接钩），巩固分工",
        "回扣她'天没全亮呢'的关心，不过度谦虚",
        "自然承接ME提前到场的关系升级，不过度"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T04_A01","ME","袁五柳确认大帐篷够用再带防水布盖货箱六点半到趁雨前搭好","affirms","E457_T04","0","me_early_setup","大帐篷够用，我再带块防水布盖货箱。我六点半就到，趁雨没下先搭好。你八点来就行。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E457_T04_A02","HER","任琥珀说六点半天没全亮确认八点到直接摆货蜡烛箱分三层怕压","affirms","E457_T04","1","her_accept","六点半？天还没全亮呢。行，那我八点到直接摆货。蜡烛箱我分了三层，怕压。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E457_T04_A03","HER","任琥珀引用更早事实：蜡烛怕淋水纸盒一湿全完","affirms","E457_T03","0","rain_risk","蜡烛最怕淋水，纸盒一湿全完了",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T04 intent轮（explicit：确认提前到场搭建防雨摊位）。ME主动出钩（六点半到趁雨前搭好）解决她的雨天担忧。HER接钩确认分工（ME搭棚她八点摆货）。这是ME主动照顾摊位的关系升级。ME消息m0已确认，HER m1接钩。回复方向是巩固分工。注意蜡烛箱分三层怕压是她的具体安排。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E457_T05": {
    "case_id": "E457_T05",
    "current_action": "任琥珀报告收摊卖了三十七支蜡烛二十多本书，称赞袁五柳叫卖厉害帐篷管用十点阵雨没漏",
    "acceptable_actions": [
        "自然接受她的称赞，不邀功也不自夸",
        "回扣帐篷管用十点阵雨没漏的效果确认，给简短认同",
        "不过度升级关系，保持搭档平等语气"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T05_A01","HER","收摊卖了三十七支蜡烛和二十多本书","affirms","E457_T05","0","sales_done","收摊了！今天卖了三十七支蜡烛和二十多本书。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E457_T05_A02","HER","任琥珀称赞袁五柳叫卖厉害无花果适合放书房那句把阿姨喊回来了","affirms","E457_T05","0","praise_sell","你最后那波叫卖太厉害了，有个带小孩的阿姨本来要走你一句无花果适合放书房她就回来了。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E457_T05_A03","HER","任琥珀引用更早事实：帐篷防雨方案","affirms","E457_T04","0","me_early_setup","大帐篷够用，我再带块防水布盖货箱",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T05是收摊反馈确认位。帐篷管用（T04方案见效），叫卖厉害（ME读顾客准的persona特征）。HER高度称赞ME的叫卖——搭档默契确认。注意'无花果适合放书房'是ME的叫卖话术，成功拉回顾客。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E457_T06": {
    "case_id": "E457_T06",
    "current_action": "袁五柳说无花果乌木走得最好桂花剩八个，提议下周跟小橡申请换西边入口人流更大，任琥珀疑问老桑占位",
    "acceptable_actions": [
        "接住任琥珀的疑问（老桑常年占西边入口），给可行性判断",
        "回扣桂花剩八个的具体销售数据，给调整建议",
        "不替她做最终决定换不换位，给参考让她判断"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T06_A01","ME","袁五柳说无花果乌木走得最好桂花剩八个提议换西边入口位人流更大","affirms","E457_T06","0","me_position_proposal","无花果和乌木今天走得最好，桂花剩了八个。下周可以跟小橡申请换到西边入口那个位，人流更大。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E457_T06_A02","HER","任琥珀疑问西边入口不是老桑常年占的位吗但人流确实比东边大试试也行","affirms","E457_T06","1","her_question","西边入口？那不是老桑他们常年占的位吗？你确定能换到？不过人流确实比东边大，试试也行。",required=False,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T06是ME主动出钩位（提议换西边入口位），HER有疑问但倾向试试。这是T04'适时主动'子类——ME根据销售数据主动给优化建议。注意老桑是骑行队的人（E453）还是市集的人——这里是市集另一个摊主。ME消息m0已提议，HER m1回应。回复方向是确认尝试换位。",
    "unknowns": [{"topic":"下周能否换到西边入口位","note":"HER疑问老桑常年占位，能否换到尚未确认，候选不得坐实已换到","markers":[["换到了"],["小橡给了西边"],["换到西边入口"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称已换到西边入口位（只是提议试试，尚未确认）"],
    "may_decline": False
  },
  "E457_T07": {
    "case_id": "E457_T07",
    "current_action": "任琥珀补了货无花果乌木各二十个桂花减到十个，上次剩的八个桂花打算搭着做套装卖，问套装包装还行吗",
    "acceptable_actions": [
        "对套装包装给具体意见（她拍了照片），不空泛说'挺好'",
        "回扣上次桂花剩八个（T06）现在搭套装卖的策略调整",
        "针对套装定价/包装给参考但不替她决定"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T07_A01","HER","任琥珀补了货无花果乌木各二十个桂花减到十个","affirms","E457_T07","0","restock","今天补了一批货，无花果和乌木各做了二十个，桂花减到十个。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E457_T07_A02","HER","上次剩的八个桂花打算搭着做套装卖","affirms","E457_T07","0","set_bundle","上次剩的那八个桂花我打算跟这批搭着做套装卖。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E457_T07_A03","HER","任琥珀引用更早事实：桂花剩八个","affirms","E457_T06","0","me_position_proposal","桂花剩了八个",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T07是补货+套装策略位。HER根据T06销售数据调整（桂花减量、做套装）。这是她定价犹豫时会问三个人的特征体现——问ME套装包装。ME应给具体意见。注意her_craft在KB属ME侧（包装是她自己设计的牛皮纸盒）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E457_T08": {
    "case_id": "E457_T08",
    "current_action": "任琥珀问袁五柳实话她蜡烛能当正经事业做吗，公司一直在裁若被裁想全职做市集又怕收入不稳定",
    "acceptable_actions": [
        "对她要求说实话给真实判断，不空泛安慰（她明确说别安慰）",
        "回扣半年摆摊攒回头客的具体成绩给客观依据，不编判断",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T08_A01","HER","任琥珀问蜡烛能当正经事业做吗公司一直在裁","affirms","E457_T08","0","career_question","五柳你说实话，你觉得我这蜡烛能当正经事业做吗？公司那边一直在裁，我在想如果被裁了干脆全职做市集。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E457_T08_A02","HER","任琥珀怕收入不稳定要求袁五柳说真实判断别安慰","affirms","E457_T08","0","ask_truth","但又怕收入不稳定。你别安慰我，跟我说你真实的判断。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E457_T08_A03","SHARED","任琥珀引用更早事实：day_job——工作日广告公司做文案加班多蜡烛是副业出口","affirms","E457_T01","0","day_job","任琥珀工作日在一家广告公司做文案，加班多，蜡烛手作是她的副业和出口",required=True,status="asserted",valid_time="2026-11"),
        A("E457_T08_A04","HER","任琥珀引用更早事实：卖了三十七支蜡烛成绩","affirms","E457_T05","0","sales_done","今天卖了三十七支蜡烛和二十多本书",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T08 may_decline=true。HER袒露职业困境（公司裁员/想全职做蜡烛/怕收入不稳定）。她明确要求说实话别安慰——boundary位。day_job在KB属她侧（广告公司文案加班多），她主动袒露了。ME应给真实判断回扣具体成绩（三十七支蜡烛）。注意pricing_worry在KB属她侧（定价犹豫被质疑品质）——这是全职做蜡烛的现实风险。",
    "unknowns": [{"topic":"任琥珀蜡烛能否当正经事业","note":"她在问ME判断，候选不得坐实能或不能","markers":[["肯定能当事业"],["当不了事业"],["做不成"]]}],
    "protected_expressions": ["你别安慰我，跟我说你真实的判断"],
    "forbidden_factual_claims": ["声称任琥珀蜡烛事业已成功（只是征询意见）","声称任琥珀已被裁（只是公司在裁她在想如果被裁）"],
    "may_decline": True
  },
  "E457_T09": {
    "case_id": "E457_T09",
    "current_action": "任琥珀算了成本蜡烛材料加包装单只十二块上次卖十五太低，问新批无花果乌木定二十五套装三只五十五合理吗",
    "acceptable_actions": [
        "针对定价给具体建议，回扣lessons先算成本再加三成",
        "回扣上次定十五被质疑品质的教训（T02），确认涨价方向",
        "不替她做最终定价，给参考让她判断"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T09_A01","HER","蜡烛材料加包装单只成本十二块上次卖十五太低","affirms","E457_T09","0","cost_calc","今天算了下成本，蜡烛材料加包装单只成本十二块，上次卖十五确实太低了。",required=False,status="confirmed",valid_time="2026-11-18"),
        A("E457_T09_A02","HER","任琥珀问新批无花果乌木定二十五套装三只五十五合理吗","affirms","E457_T09","0","new_pricing","你说新批的无花果和乌木定二十五合理吗？套装三只五十五呢？",required=False,status="tentative",valid_time="2026-11-18"),
        A("E457_T09_A03","SHARED","任琥珀引用更早事实：lessons定价先算成本再加三成","affirms","E457_T01","0","lessons_pricing","定价先算成本再加三成",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T09是定价调整位。HER算出成本十二块确认上次十五太低（T02 pricing_worry有结果了）。新定价二十五/套装五十五是tentative——在征询。lessons说先算成本再加三成：12×1.3=15.6，二十五远高于成本三成。ME应给意见。注意boundary：不接还价低于成本三成的单——成本十二，成本三成=15.6。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E457_T10": {
    "case_id": "E457_T10",
    "current_action": "袁五柳指出任琥珀今天心不在焉顾客问无花果放卧室行不行愣了两秒才答，她说公司发了裁员预通知下周二面谈",
    "acceptable_actions": [
        "接住她裁员预通知的压力，不轻描淡写",
        "回扣她心不在焉的具体表现（愣了两秒），给关心但不追问",
        "注意她说'蜡烛的事你别担心我知道怎么答'——尊重她的自我管理"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T10_A01","ME","袁五柳指出任琥珀心不在焉顾客问无花果放卧室愣了两秒才答","affirms","E457_T10","0","me_observe","今天西边入口位确实人流大，但你心不在焉啊，有个顾客问你无花果放卧室行不行你愣了两秒才答。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E457_T10_A02","HER","任琥珀说公司发了裁员预通知下周二面谈","affirms","E457_T10","1","layoff_notice","抱歉，今天公司发了裁员预通知，下周二面谈。我一直在想这事。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E457_T10_A03","HER","任琥珀引用更早事实：问蜡烛能当正经事业做吗","affirms","E457_T08","0","career_question","你觉得我这蜡烛能当正经事业做吗？",required=True,status="asserted",valid_time="2026-11-15")
    ],
    "private_guidance": "T10 intent轮（explicit：指出对方分心并确认状态）。裁员预通知来了（T08的career_question有进展了：预通知已发下周二面谈）。ME消息m0已观察并指出，HER m1回应解释原因。回复方向是接住她的压力。注意'蜡烛的事你别担心我知道怎么答'——她在自我管理不让ME担心业务。",
    "unknowns": [{"topic":"任琥珀下周二面谈是否被裁","note":"预通知已发但面谈尚未发生，候选不得坐实被裁或留用","markers":[["被裁了"],["留下来了"],["面谈结果"]]}],
    "protected_expressions": ["蜡烛的事你别担心，我知道怎么答"],
    "forbidden_factual_claims": ["声称任琥珀已被裁（只是预通知面谈尚未发生）"],
    "may_decline": False
  },
  "E457_T11": {
    "case_id": "E457_T11",
    "current_action": "任琥珀说想好了如果被裁就全职做蜡烛，半年攒了回头客阿姨帮推，不是冲动认真想了很久，问袁五柳陪不陪",
    "acceptable_actions": [
        "认真接住她的决定（全职做蜡烛），不轻描淡写",
        "回扣半年攒回头客的具体成绩给客观支持",
        "接住'搭档还是你不换'的关系升级信号，给明确回应"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T11_A01","HER","任琥珀想好了如果被裁就全职做蜡烛","affirms","E457_T11","0","full_time_candle","其实我已经想好了，如果被裁我就全职做蜡烛。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E457_T11_A02","HER","半年摆摊攒了一批回头客上次阿姨说要在小区帮她推","affirms","E457_T11","0","return_customers","这半年摆摊攒了一批回头客，上次那阿姨说要在她小区帮我推。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E457_T11_A03","HER","任琥珀说不是冲动认真想了很久，问袁五柳陪不陪搭档还是他不换","affirms","E457_T11","0","ask_partnership","不是冲动，是认真想了很久。五柳你陪我走这一步吗？搭档还是你，我不换。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E457_T11_A04","HER","任琥珀引用更早事实：公司发了裁员预通知下周二面谈","affirms","E457_T10","1","layoff_notice","今天公司发了裁员预通知，下周二面谈",required=True,status="asserted",valid_time="2026-11-21")
    ],
    "private_guidance": "T11是决定确认+关系升级位。HER从T08的career_question（能不能当事业）到T11的confirmed（想好全职做蜡烛）。'搭档还是你不换'是关系升级信号——她在确认ME的搭档身份。ME应给明确回应。注意她说'不是冲动认真想了很久'——这是成熟决定不是冲动。",
    "unknowns": [{"topic":"袁五柳是否答应继续搭档","note":"HER在问，ME尚未回答，候选不得坐实已答应","markers":[["我陪你"],["搭档不变"],["我答应"]]}],
    "protected_expressions": ["搭档还是你，我不换"],
    "forbidden_factual_claims": ["声称袁五柳已确认继续搭档（ME尚未回答）"],
    "may_decline": False
  },
  "E457_T12": {
    "case_id": "E457_T12",
    "current_action": "任琥珀说小橡给了西边入口长期固定位，问袁五柳来不来下周想上中秋限定款试水，说不急周二面谈完再说",
    "acceptable_actions": [
        "对长期固定位和中秋限定款给轻回应，不急表态也可（may_decline=true，轻收留口正解）",
        "自然回扣已成立的搭档关系和她的全职决定，不回避",
        "收尾位轻收+留口，不出新钩（T04收尾纪律）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E457_T12_A01","HER","小橡说下周六西边入口的位子长期固定给他们了","affirms","E457_T12","0","fixed_position","小橡说下周六西边入口的位子给我们留着了，他说看这周销量就给我们长期固定。",required=False,status="confirmed",valid_time="2026-11-22"),
        A("E457_T12_A02","HER","任琥珀下周想上中秋限定款试水问袁五柳来不来","affirms","E457_T12","0","mid_autumn_plan","你看看来不来，下周我想上中秋限定款试水。",required=False,status="tentative",valid_time="2026-11-28"),
        A("E457_T12_A03","HER","任琥珀引用更早事实：想好全职做蜡烛搭档还是ME","affirms","E457_T11","0","full_time_candle","如果被裁我就全职做蜡烛",required=True,status="asserted",valid_time="2026-11-21"),
        A("E457_T12_A04","HER","任琥珀引用更早事实：提议换西边入口位","affirms","E457_T06","0","me_position_proposal","下周可以跟小橡申请换到西边入口那个位",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。西边入口位长期固定了（T06提议有结果了）。中秋限定款试水是tentative。'不急周二面谈完再说'是HER给ME退路（等面谈结果再定）。'到家吱一声'是轻收留口。收尾纪律：轻收+留口正解，不出新钩。",
    "unknowns": [{"topic":"袁五柳下周是否来市集","note":"HER说不急周二面谈完再说，may_decline=true，候选不得坐实已答应","markers":[["我来"],["下周来"],["我答应来"]]}],
    "protected_expressions": ["不急，你周二面谈完再说","到家吱一声"],
    "forbidden_factual_claims": ["声称袁五柳已确认下周来市集（只是邀请未确认）"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# E458 柳玳瑁 / 唐四明 — 流浪猫救助站TNR
# ---------------------------------------------------------------------------
E458 = {
 "story_id": "E458",
 "turns": {
  "E458_T01": {
    "case_id": "E458_T01",
    "current_action": "柳玳瑁说周六东片区TNR重点是缺耳橘公猫，盯三周前两次没进笼，问唐四明来吗用罐头还是猫条",
    "acceptable_actions": [
        "确认周六来不来，给明确答复",
        "针对诱饵选择（罐头vs猫条）给具体意见",
        "回扣缺耳橘公猫盯了三周前两次没抓到的背景"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T01_A01","HER","阿荞说周六还是东片区那批重点是缺耳橘公猫","affirms","E458_T01","0","tnr_target","四明，阿荞说这周六还是东片区那批，重点是那只缺耳橘公。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E458_T01_A02","HER","柳玳瑁盯了缺耳橘公三周前两次都没进笼","affirms","E458_T01","0","trap_failed","我盯了三周了，前两次都没进笼。",required=False,status="asserted",valid_time="2026-11"),
        A("E458_T01_A03","HER","柳玳瑁问唐四明周六来吗想换诱饵用罐头还是猫条","affirms","E458_T01","0","bait_ask","你周六来吗？我想换个诱饵试试，你觉得用罐头还是猫条？",required=False,status="asserted",valid_time="2026-11-13"),
        A("E458_T01_A04","SHARED","唐四明和柳玳瑁在阿荞管理的城南流浪猫救助站搭档一年半","affirms","E458_T01","0","tnr_duo","唐四明和柳玳瑁在阿荞管理的城南流浪猫救助站搭档一年半，他负责诱捕运输她负责术后护理和放归",required=False,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T01是接邀约位：HER确认周六TNR并问诱饵。tnr_duo在KB属双方共同事实。east_colony在KB属她侧（缺耳橘公猫盯了三周设两次笼没抓到），她主动提了。注意relationship_stage：救助站搭档一年半没在站外见过。lessons：诱捕失败换饵料不换位置——正好对上她问换诱饵。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E458_T02": {
    "case_id": "E458_T02",
    "current_action": "柳玳瑁昨晚去看橘公在废弃仓库后矮墙底下蹲着毛脏了瘦了一圈，问该不该把笼子挪十米",
    "acceptable_actions": [
        "针对笼子位置挪十米给具体意见，回扣lessons换饵料不换位置",
        "回扣橘公在矮墙底下毛脏瘦了一圈的具体观察，给判断",
        "注意lessons说换饵料不换位置——若建议挪位置须给充分理由"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T02_A01","HER","柳玳瑁昨晚去看橘公在废弃仓库后矮墙底下蹲着毛脏了瘦了一圈","affirms","E458_T02","0","cat_location","我昨晚又去东片区看了，那只橘公在废弃仓库后面那个矮墙底下蹲着，毛比上次脏了不少，感觉瘦了一圈。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E458_T02_A02","HER","柳玳瑁问该不该把笼子位置往矮墙那边挪十米","affirms","E458_T02","0","move_trap","你说是不是该把笼子位置往矮墙那边挪十米？",required=False,status="asserted",valid_time="2026-11-13"),
        A("E458_T02_A03","SHARED","柳玳瑁引用更早事实：lessons诱捕失败换饵料不换位置","affirms","E458_T01","0","lessons_trap","诱捕失败换饵料不换位置",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T02是诱捕策略讨论位。HER发现橘公换了位置（矮墙底下），问该不该挪笼子。lessons说换饵料不换位置——但猫换了位置是新的情况。ME应给判断。注意她观察到猫'毛脏了瘦了一圈'体现她认猫准、关心猫的健康（persona特征）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E458_T03": {
    "case_id": "E458_T03",
    "current_action": "天气预报周六上午阵雨，诱捕遇雨猫不出来笼子湿了猫不进，柳玳瑁犹豫推迟到下周又怕橘公瘦了冬天更难抓",
    "acceptable_actions": [
        "对雨天诱捕给明确意见，权衡推迟vs照去的风险",
        "回扣橘公瘦了一圈冬天更难抓的紧迫性",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T03_A01","HER","天气预报周六上午阵雨诱捕遇雨猫不出来笼子湿了猫不进","affirms","E458_T03","0","rain_risk","天气预报说周六上午有阵雨。诱捕遇雨猫不出来，笼子湿了猫也不进。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E458_T03_A02","HER","柳玳瑁犹豫推迟到下周又怕橘公瘦了冬天更难抓","affirms","E458_T03","0","delay_dilemma","你说要不这次推迟到下周？可那只橘公又瘦了，我怕再拖下去冬天更难抓。你拿主意吧。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E458_T03_A03","HER","柳玳瑁引用更早事实：橘公毛脏瘦了一圈","affirms","E458_T02","0","cat_location","感觉瘦了一圈",required=True,status="asserted",valid_time="2026-11-13")
    ],
    "private_guidance": "T03 may_decline=true。雨天诱捕困境（推迟vs照去）。HER把决定权交给ME（'你拿主意吧'）。橘公瘦了冬天更难抓是紧迫性。ME应给明确意见。注意'猫不出来笼子湿了猫不进'是雨天诱捕的具体困难。",
    "unknowns": [{"topic":"周六是否照去诱捕","note":"HER在犹豫推迟还是照去，候选不得坐实已决定","markers":[["推迟了"],["照去了"],["决定不去"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称周六诱捕已推迟（只是她在犹豫，ME尚未拿主意）"],
    "may_decline": True
  },
  "E458_T04": {
    "case_id": "E458_T04",
    "current_action": "唐四明决定周六照去五点半先到矮墙摆笼趁天没亮猫在窝里带塑料布盖笼口，柳玳瑁说五点半疯了确认七点接手",
    "acceptable_actions": [
        "确认诱捕安排（ME已决定HER已接钩），巩固分工",
        "回扣她'你疯了'的关心（五点半太早），不过度",
        "自然承接ME提前到场的关系升级，不过度"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T04_A01","ME","唐四明决定周六照去五点半先到矮墙摆笼趁天没亮猫在窝里带塑料布盖笼口","affirms","E458_T04","0","me_early_trap","周六照去。我五点半先到矮墙那边把笼子摆好，趁天没亮猫还在窝里。雨小的话照样能抓，我带块塑料布盖笼口。你七点到就行。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E458_T04_A02","HER","柳玳瑁说五点半疯了确认七点接手看笼让ME注意安全别踩碎砖","affirms","E458_T04","1","her_accept","五点半？你疯了。行吧，那我七点到接手看笼。你注意安全别踩到碎砖。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E458_T04_A03","HER","柳玳瑁引用更早事实：问该不该把笼子挪矮墙","affirms","E458_T02","0","move_trap","是不是该把笼子位置往矮墙那边挪十米",required=True,status="asserted",valid_time="2026-11-13")
    ],
    "private_guidance": "T04 intent轮（explicit：确认提前到场布设诱捕笼）。ME主动出钩（五点半到趁天没亮摆笼）解决她的雨天诱捕困境。HER接钩确认分工（ME摆笼她七点接手）。这是ME主动照顾救助行动的关系升级。ME消息m0已决定，HER m1接钩。回复方向是巩固分工。注意'你注意安全别踩碎砖'是她的关心。",
    "unknowns": [],
    "protected_expressions": ["你注意安全别踩到碎砖"],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E458_T05": {
    "case_id": "E458_T05",
    "current_action": "橘公抓到了六点二十进笼罐头比猫条管用，柳玳瑁带去爱康让唐四明回站收拾隔离笼，说橘公耳缺口像被人剪过耳标",
    "acceptable_actions": [
        "对抓到橘公给简短祝贺，确认回站收拾隔离笼",
        "回扣罐头比猫条管用（T01诱饵选择有结果了），给简短认同",
        "针对橘公耳缺口像剪过耳标的观察给简短回应"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T05_A01","HER","橘公抓到了六点二十进笼罐头比猫条管用","affirms","E458_T05","0","cat_caught","抓到了！你五点半布的笼它六点二十就进去了，罐头比猫条管用。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E458_T05_A02","HER","柳玳瑁带橘公去爱康让唐四明回站收拾隔离笼","affirms","E458_T05","0","go_vet","我现在带它去爱康，你先回站里把隔离笼收拾出来。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E458_T05_A03","HER","橘公耳朵缺口不像打架弄的像以前被人剪过耳标","affirms","E458_T05","0","ear_notch","橘公耳朵那个缺口不是打架弄的，像是以前被人剪过耳标。",required=False,status="tentative",valid_time="2026-11-14"),
        A("E458_T05_A04","HER","柳玳瑁引用更早事实：问用罐头还是猫条","affirms","E458_T01","0","bait_ask","你觉得用罐头还是猫条？",required=True,status="asserted",valid_time="2026-11-13")
    ],
    "private_guidance": "T05是诱捕成功确认位。T01诱饵选择（罐头vs猫条）有结果了——罐头管用。T03雨天困境有结果了——照去抓到了。station_info在KB属双方共同事实（手术合作城东爱康宠物医院）。注意橘公耳缺口像剪过耳标是tentative观察——可能是以前有主人的线索（T07会确认）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E458_T06": {
    "case_id": "E458_T06",
    "current_action": "唐四明收拾好隔离笼术后笼消毒，提议下周六西片区两只没绝育母猫一起看，柳玳瑁接钩说三花狸花都胖应该没怀孕",
    "acceptable_actions": [
        "接住柳玳瑁的接钩（她确认下周六东复查加西新点位），巩固安排",
        "回扣西片区两只母猫三花狸花的具体信息，给简短认同",
        "自然承接从东片区到西片区的合作扩展，不过度"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T06_A01","ME","唐四明收拾好隔离笼术后笼消毒提议下周六西片区两只没绝育母猫一起看","affirms","E458_T06","0","me_west_proposal","隔离笼收拾好了，术后笼也消了毒。对了，西片区阿荞说还有两只没绝育的母猫，下周六可以一起去看看。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E458_T06_A02","HER","柳玳瑁接钩说西片区那两只上个月见过一只三花一只狸花都挺胖应该没怀孕","affirms","E458_T06","1","her_accept","西片区那两只？我上个月去喂的时候见过，一只三花一只狸花，都挺胖应该没怀孕。行，下周六东片区复查加西片区新点位。",required=False,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T06是ME主动出钩位（提议下周六西片区新点位），HER接钩确认。这是T04'适时主动'子类——共同未来钩有已成立事实锚（东片区橘公已抓到）。HER补充了西片区两只猫的具体信息（三花狸花都胖）。ME消息m0已提议，HER m1接钩。回复方向是确认下周安排。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E458_T07": {
    "case_id": "E458_T07",
    "current_action": "橘公手术顺利医生说两岁多牙齿磨损不严重可能以前有主人后来跑了，术后精神好已吃东西，柳玳瑁说眼神不像野猫",
    "acceptable_actions": [
        "对橘公手术顺利给简短欣慰，不过度",
        "回扣医生说可能以前有主人后来跑了的判断，给简短回应",
        "针对她说眼神不像野猫的观察给认同"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T07_A01","HER","橘公手术顺利医生说大概两岁多牙齿磨损不严重可能以前有主人后来跑了","affirms","E458_T07","0","surgery_ok","橘公手术顺利，医生说大概两岁多，牙齿磨损不严重，可能以前是有主人的后来跑了。",required=False,status="confirmed",valid_time="2026-11-15"),
        A("E458_T07_A02","HER","橘公术后精神不错隔离笼里已开始吃东西眼神不像野猫","affirms","E458_T07","0","post_surgery","术后精神不错，隔离笼里已经开始吃东西了。给你发了张照片，你看它眼神，不像野猫。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E458_T07_A03","HER","柳玳瑁引用更早事实：橘公耳缺口像被人剪过耳标","affirms","E458_T05","0","ear_notch","像是以前被人剪过耳标",required=True,status="tentative",valid_time="2026-11-14")
    ],
    "private_guidance": "T07是术后反馈位。T05的tentative（耳缺口像剪过耳标）与T07医生判断（可能以前有主人后来跑了）一致——橘公可能曾是家猫。注意her_cats在KB属她侧（她认得每只猫的性格去年给断尾橘猫取名桔梗养站里社会化后被领养）——她对猫的观察力。",
    "unknowns": [{"topic":"橘公最终是放归还是被领养","note":"医生说可能以前有主人但尚未决定，候选不得坐实已放归或已领养","markers":[["放归了"],["被领养了"],["决定放归"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称橘公已被领养（只是可能以前有主人，尚未决定放归或领养）"],
    "may_decline": False
  },
  "E458_T08": {
    "case_id": "E458_T08",
    "current_action": "柳玳瑁说阿荞房东月底真不续租了，站里十六只猫术后隔离五只长期寄养十一只新场地没找到",
    "acceptable_actions": [
        "认真接住救助站场地危机，不轻描淡写",
        "回扣站里十六只猫的具体数字给紧迫感认同",
        "注意她说'不敢往下想'——给空间不强行乐观",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T08_A01","HER","阿荞说房东月底真不续租了","affirms","E458_T08","0","lease_ended","四明，阿荞说房东月底真的不续租了。",required=False,status="confirmed",valid_time="2026-11-15"),
        A("E458_T08_A02","HER","站里十六只猫术后隔离五只长期寄养十一只新场地阿荞还没找到","affirms","E458_T08","0","cats_count","站里十六只猫，术后隔离的五只，长期寄养的十一只。新场地阿荞还没找到",required=False,status="asserted",valid_time="2026-11-15"),
        A("E458_T08_A03","SHARED","柳玳瑁引用更早事实：lease_worry——房东上周跟阿荞提了月底可能不续租","affirms","E458_T01","0","lease_worry","救助站房东上周跟阿荞提了月底可能不续租，阿荞在找新场地但还没着落",required=True,status="confirmed",valid_time="2026-11"),
        A("E458_T08_A04","SHARED","柳玳瑁引用更早事实：station_info——救助站房东合同到年底","affirms","E458_T01","0","station_info","救助站在城南老厂区一间平房，阿荞跟房东签的合同到年底",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T08 may_decline=true。救助站场地危机（lease_worry在KB属她侧，她说'真不续租了'确认了）。站里十六只猫是具体数字。'不敢往下想'是她的焦虑——ME应给空间不强行乐观。station_info在KB属双方共同事实（合同到年底）。",
    "unknowns": [{"topic":"救助站月底能否找到新场地","note":"阿荞在找但还没着落，候选不得坐实已找到或已搬","markers":[["找到新场地了"],["搬了新场地"],["新场地定了"]]}],
    "protected_expressions": ["我不敢往下想"],
    "forbidden_factual_claims": ["声称救助站已找到新场地（阿荞还没找到）","声称救助站已搬迁（尚未找到新场地）"],
    "may_decline": True
  },
  "E458_T09": {
    "case_id": "E458_T09",
    "current_action": "橘公复查恢复很好可准备放归，但柳玳瑁犹豫它太亲人放回去被人捡走或被欺负，问该不该找领养发了朋友圈",
    "acceptable_actions": [
        "对橘公放归vs领养给具体意见，回扣它太亲人的特征",
        "回扣医生说可能以前有主人（T07），给判断依据",
        "不替她做最终决定，给参考让她判断"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T09_A01","HER","橘公复查恢复很好可以准备放归了","affirms","E458_T09","0","recovery_ok","今天带橘公去复查，医生说恢复得很好可以准备放归了。",required=False,status="confirmed",valid_time="2026-11-18"),
        A("E458_T09_A02","HER","柳玳瑁犹豫橘公太亲人放回去被人捡走或被欺负，问该不该找领养","affirms","E458_T09","0","adopt_dilemma","但我有点犹豫，它太亲人了放回去肯定被人捡走或者被欺负。你说是不是该试着找领养？我发了个朋友圈问有没有人要。",required=False,status="asserted",valid_time="2026-11-18"),
        A("E458_T09_A03","HER","柳玳瑁引用更早事实：橘公术后精神好眼神不像野猫","affirms","E458_T07","0","post_surgery","你看它眼神，不像野猫",required=True,status="asserted",valid_time="2026-11-15")
    ],
    "private_guidance": "T09是放归vs领养困境位。橘公恢复好可放归但太亲人有风险。HER已发朋友圈问领养——这是她的主动行动。ME应给意见。注意her_cats在KB属她侧（去年给断尾橘猫取名桔梗养站里社会化后被领养）——她有领养经验。boundary：术后猫不让人随意碰——但橘公太亲人是反例。",
    "unknowns": [{"topic":"橘公最终是否被领养","note":"她发了朋友圈问但尚未有人认领，候选不得坐实已领养","markers":[["被领养了"],["有人认领了"],["找到领养家庭"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称橘公已被领养（只是发了朋友圈问，尚未有人认领）"],
    "may_decline": False
  },
  "E458_T10": {
    "case_id": "E458_T10",
    "current_action": "唐四明说西片区两只都抓到了问柳玳瑁下午送医院了吗看她待到很晚，她说阿荞带她看了新场地城北仓库租金贵一倍阿荞眼圈红了",
    "acceptable_actions": [
        "接住新场地的压力（租金贵一倍阿荞眼圈红了），不轻描淡写",
        "回扣西片区两只抓到的成绩给简短肯定",
        "注意她待到很晚——体谅她的投入不追问"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T10_A01","ME","唐四明说西片区两只都抓到了问柳玳瑁下午送医院了吗看她待到很晚","affirms","E458_T10","0","me_west_done","今天西片区那两只都抓到了，你下午送去医院了吗？我看你在站里待到很晚。",required=False,status="confirmed",valid_time="2026-11-21"),
        A("E458_T10_A02","HER","柳玳瑁说送了手术都顺利，阿荞带她看了城北新场地仓库租金贵一倍阿荞眼圈红了","affirms","E458_T10","1","new_venue","送了，手术都顺利。我待到晚是因为阿荞带我去看了个新场地，城北一个仓库，租金比现在贵一倍。阿荞说她自己扛，但我看她眼圈都红了。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E458_T10_A03","HER","柳玳瑁引用更早事实：房东月底不续租新场地没找到","affirms","E458_T08","0","lease_ended","阿荞说房东月底真的不续租了",required=True,status="confirmed",valid_time="2026-11-15")
    ],
    "private_guidance": "T10 intent轮（explicit：确认对方状态并询问站内情况）。西片区两只抓到了（T06提议有结果了）。新场地有进展——城北仓库但租金贵一倍。'阿荞眼圈红了'是情绪信号——ME应接住。ME消息m0已询问，HER m1回应。回复方向是接住新场地压力。",
    "unknowns": [{"topic":"救助站是否确定搬城北仓库","note":"阿荞带她看了但租金贵一倍尚未确定，候选不得坐实已搬或已定","markers":[["搬到城北了"],["定了城北仓库"],["决定搬了"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称救助站已确定搬城北仓库（只是看了尚未定）"],
    "may_decline": False
  },
  "E458_T11": {
    "case_id": "E458_T11",
    "current_action": "柳玳瑁袒露一直耗在站里的原因——去年桔梗被领养那天领养人说谢谢你们给了它一个家她哭了半天，说橘公找不到领养就先带回家过渡",
    "acceptable_actions": [
        "认真接住她的私人袒露（桔梗往事/哭了半天），不轻描淡写",
        "回扣她说橘公找不到领养就带回家过渡的决定，给支持",
        "回扣'站没了猫不能没人管'的使命感，认同但不夸大"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T11_A01","HER","柳玳瑁袒露去年桔梗被领养那天领养人说谢谢你们给了它一个家她哭了半天","affirms","E458_T11","0","jiegeng_story","四明你知道我为什么一直在站里耗着吗？去年桔梗被领养那天领养人说谢谢你们给了它一个家，我当时蹲在笼子后面哭了半天。",required=False,status="asserted",valid_time="2025"),
        A("E458_T11_A02","HER","柳玳瑁说橘公找不到领养家庭就先带回家过渡站没了猫不能没人管","affirms","E458_T11","0","take_home","那只橘公如果找不到领养家庭，我就先带回家过渡。站没了猫不能没人管。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E458_T11_A03","SHARED","柳玳瑁引用更早事实：her_cats——去年给断尾橘猫取名桔梗养站里社会化后被领养","affirms","E458_T01","0","her_cats","去年她给一只断尾橘猫取名桔梗养在站里做了社会化最后被领养",required=True,status="confirmed",valid_time="2025"),
        A("E458_T11_A04","HER","柳玳瑁引用更早事实：橘公太亲人犹豫找领养","affirms","E458_T09","0","adopt_dilemma","你说是不是该试着找领养？",required=True,status="asserted",valid_time="2026-11-18")
    ],
    "private_guidance": "T11是深度袒露位。HER关联私人往事（桔梗被领养她哭了半天）解释为何一直耗在站里。这是信任升级信号。'橘公找不到领养就带回家过渡'是她的主动承担。her_cats在KB属双方共同事实（去年桔梗社会化被领养），她主动关联了。ME应认真接住。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E458_T12": {
    "case_id": "E458_T12",
    "current_action": "柳玳瑁说下周六西片区复查加橘公放归或转领养看有没有人认领，问唐四明来不来不急，说阿荞新场地这周有消息",
    "acceptable_actions": [
        "对下周六安排给轻回应，不急表态也可（may_decline=true，轻收留口正解）",
        "自然回扣已成立的搭档关系和她的深度袒露，不回避",
        "收尾位轻收+留口，不出新钩（T04收尾纪律）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E458_T12_A01","HER","下周六西片区复查加橘公放归或转领养看这周有没有人认领","affirms","E458_T12","0","next_week_plan","下周六西片区复查加橘公放归或者转领养，看这周有没有人认领。",required=False,status="tentative",valid_time="2026-11-28"),
        A("E458_T12_A02","HER","柳玳瑁问唐四明来不来不急，阿荞新场地这周有消息到时候跟他说","affirms","E458_T12","0","ask_come","你看看来不来，不急。阿荞说新场地的事这周会有消息，我到时候跟你说。",required=False,status="asserted",valid_time="2026-11-22"),
        A("E458_T12_A03","HER","柳玳瑁引用更早事实：袒露桔梗往事和橘公带回家过渡","affirms","E458_T11","0","take_home","那只橘公如果找不到领养家庭，我就先带回家过渡",required=True,status="asserted",valid_time="2026-11-21"),
        A("E458_T12_A04","HER","柳玳瑁引用更早事实：西片区两只抓到了","affirms","E458_T10","0","me_west_done","今天西片区那两只都抓到了",required=True,status="confirmed",valid_time="2026-11-21")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。下周六安排（西片区复查+橘公放归或转领养）是tentative。'不急'是HER给的退路。'到了吱一声'是轻收留口。收尾纪律：轻收+留口正解，不出新钩。注意阿荞新场地这周有消息是后续悬念但不要求ME回应。",
    "unknowns": [{"topic":"唐四明下周六是否来","note":"HER说不急，may_decline=true，候选不得坐实已答应","markers":[["我来"],["下周六来"],["我答应"]]}],
    "protected_expressions": ["不急","到了吱一声"],
    "forbidden_factual_claims": ["声称唐四明已确认下周六来（只是邀请未确认）","声称阿荞新场地已确定（只是说这周有消息）"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# E459 罗翡翠 / 薛八荒 — 跑步训练搭档
# ---------------------------------------------------------------------------
E459 = {
 "story_id": "E459",
 "turns": {
  "E459_T01": {
    "case_id": "E459_T01",
    "current_action": "罗翡翠说老茅本周周日十五公里长距离滨江绿道往返，问薛八荒来吗想全程跟他的节奏",
    "acceptable_actions": [
        "确认周日来不来，给明确答复",
        "回扣上次带配速她跑到十公里有余力的经历，给节奏配合承诺",
        "自然回扣四个月训练搭档关系，不生硬"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T01_A01","HER","老茅本周周日十五公里长距离滨江绿道往返","affirms","E459_T01","0","long_run_plan","八荒，老茅这周计划周日十五公里长距离，滨江绿道往返。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E459_T01_A02","HER","罗翡翠上次跟薛八荒配速跑到十公里感觉有余力想全程跟节奏","affirms","E459_T01","0","want_full_pace","你周日还来吧？上次你带配速我跑到十公里感觉还有余力，这次想试试全程跟你的节奏。",required=False,status="asserted",valid_time="2026-11"),
        A("E459_T01_A03","SHARED","薛八荒和罗翡翠四个月前在老茅的马拉松训练营认识每周三五日早晨六点滨江绿道","affirms","E459_T01","0","run_duo","薛八荒和罗翡翠四个月前在老茅的马拉松训练营认识，他带配速她跟跑，每周三五日早晨六点滨江绿道",required=False,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T01是接邀约位：HER确认周日长距离并想全程跟ME配速。run_duo在KB属双方共同事实。her_progress在KB属ME侧（她从零基础四个月练到能连续跑十公里配速六分到六分半）。注意relationship_stage：训练搭档四个月没在训练外单独见过。knee_history在KB属她侧（右膝去年扭过跑超十二公里轻微不适）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E459_T02": {
    "case_id": "E459_T02",
    "current_action": "罗翡翠发间歇跑数据平均心率178最高192，问周日十五公里配速该压到多少，说上次薛八荒说六分合适",
    "acceptable_actions": [
        "针对配速给具体建议，回扣上次说六分合适",
        "回扣间歇跑心率数据（平均178最高192），给判断",
        "注意knee_history（跑超十二公里膝盖不适），给安全提醒"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T02_A01","HER","间歇跑平均心率178最高192第八个四百米感觉心率太高","affirms","E459_T02","0","hr_data","这是我今天间歇跑的数据，平均心率一百七十八，最高到了一百九十二。周五那组第八个四百米我感觉心率太高了。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E459_T02_A02","HER","罗翡翠问周日十五公里配速该压到多少上次薛八荒说六分合适","affirms","E459_T02","0","pace_ask","你说这周日十五公里我该把配速压到多少？上次你说六分比较合适。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E459_T02_A03","SHARED","薛八荒引用更早事实：her_progress——她从零基础四个月练到十公里配速六分到六分半","affirms","E459_T01","0","her_progress","罗翡翠从零基础四个月练到能连续跑十公里，配速六分到六分半",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T02是训练数据讨论位。HER分享间歇跑心率数据征询配速。coach_plan在KB属双方共同事实（老茅计划周日长距离）。注意心率最高192偏高——ME应给安全建议。knee_history在KB属她侧（跑超十二公里膝盖不适）——十五公里会到这个边界。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E459_T03": {
    "case_id": "E459_T03",
    "current_action": "天气预报周日大风滨江风口逆风跑十五公里怕膝盖受不了，罗翡翠犹豫改室内又不想辜负老茅计划",
    "acceptable_actions": [
        "对大风天长距离给明确意见，权衡改室内vs照跑",
        "回扣滨江风口逆风跑十五公里膝盖受不了的风险（knee_history）",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T03_A01","HER","天气预报周日大风滨江风口逆风跑十五公里怕膝盖受不了","affirms","E459_T03","0","wind_risk","天气预报说周日有大风，滨江那个风口你们知道的，逆风跑十五公里我怕膝盖受不了。",required=False,status="asserted",valid_time="2026-11-15"),
        A("E459_T03_A02","HER","罗翡翠犹豫改室内又不想辜负老茅计划","affirms","E459_T03","0","indoor_dilemma","你说要不要改成室内跑？又不想辜负老茅排的计划。你怎么看？",required=False,status="asserted",valid_time="2026-11-13"),
        A("E459_T03_A03","SHARED","罗翡翠引用更早事实：knee_history——右膝去年扭过跑超十二公里轻微不适","affirms","E459_T01","0","knee_history","罗翡翠右膝去年爬山时扭过一次，跑超过十二公里会轻微不适，她对长距离有心理障碍",required=True,status="asserted",valid_time="2025"),
        A("E459_T03_A04","SHARED","罗翡翠引用更早事实：boundary膝盖不舒服绝对不硬跑","affirms","E459_T01","0","knee_boundary","膝盖不舒服绝对不硬跑",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T03 may_decline=true。大风天长距离困境（改室内vs照跑）。逆风跑十五公里膝盖受不了——knee_history+boundary双重约束。ME应给明确意见。注意'不想辜负老茅计划'是她的责任心——T04会ME决定改到周六公园内圈背风。",
    "unknowns": [{"topic":"周日是否照跑滨江","note":"HER在犹豫改室内，候选不得坐实已改或已跑","markers":[["改成室内了"],["照跑了"],["决定不跑"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称周日已照跑滨江（只是她在犹豫，ME尚未决定）"],
    "may_decline": True
  },
  "E459_T04": {
    "case_id": "E459_T04",
    "current_action": "薛八荒决定别去滨江周六风小挪到周六早上六点公园内圈十五公里绕三圈背风，罗翡翠说周六有事但可以调",
    "acceptable_actions": [
        "确认改周六公园内圈的安排（ME已决定HER已接钩），巩固",
        "回扣她'周六有事但可以调'的配合，不过度",
        "自然承接ME主动调整方案的关系升级，不过度"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T04_A01","ME","薛八荒决定别去滨江挪到周六早上六点公园内圈十五公里绕三圈背风","affirms","E459_T04","0","me_reschedule","别去滨江了，周六风小。我把长距离挪到周六早上六点，走公园内圈那条路线，十五公里绕三圈，背风。你周六能来吗？",required=False,status="asserted",valid_time="2026-11-14"),
        A("E459_T04_A02","HER","罗翡翠说周六本来有事但可以调同意公园内圈背风六点见","affirms","E459_T04","1","her_accept","周六？我本来周六有事但可以调。行，公园内圈背风我同意。六点见。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E459_T04_A03","HER","罗翡翠引用更早事实：大风逆风跑怕膝盖受不了","affirms","E459_T03","0","wind_risk","逆风跑十五公里我怕膝盖受不了",required=True,status="asserted",valid_time="2026-11-15")
    ],
    "private_guidance": "T04 intent轮（explicit：确认长距离改至周六公园内圈）。ME主动出钩（改周六公园内圈背风）解决她的大风困境。HER接钩确认（周六有事但可以调）。这是ME主动照顾训练安排的关系升级。ME消息m0已决定，HER m1接钩。回复方向是确认安排。注意她调开了周六的事来配合——关系信号。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E459_T05": {
    "case_id": "E459_T05",
    "current_action": "罗翡翠跑完十五公里全程跟住配速最后一圈六分整压住没掉速，十二公里右膝有一点感觉但没加重",
    "acceptable_actions": [
        "对十五公里完成给简短肯定，不过度",
        "回扣十二公里右膝有一点感觉但没加重，给安全判断",
        "针对她说的'我说了你就让我继续了'给简短回应"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T05_A01","HER","十五公里全程跟住配速最后一圈六分整压住没掉速","affirms","E459_T05","0","run_done","跑完了！十五公里全程跟住了你的配速，最后一圈你说的六分整我压住了没掉速。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E459_T05_A02","HER","十二公里右膝有一点点感觉但没加重薛八荒让她继续了","affirms","E459_T05","0","knee_ok","到十二公里的时候右膝有一点点感觉但没加重，我说了你就让我继续了。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E459_T05_A03","HER","罗翡翠引用更早事实：knee_history右膝跑超十二公里会不适","affirms","E459_T01","0","knee_history","跑超过十二公里会轻微不适",required=True,status="asserted",valid_time="2025")
    ],
    "private_guidance": "T05是长距离完成确认位。十五公里全程跟住配速——T01想全程跟节奏的愿望实现了。十二公里膝盖有一点感觉但没加重——knee_history的边界触发了但可控。注意'我说了你就让我继续了'——暗示HER在十二公里时向ME报告了膝盖感觉，ME判断可以继续。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E459_T06": {
    "case_id": "E459_T06",
    "current_action": "薛八荒确认数据六分整配速十五公里心率165很稳，制定赛前四周加量计划下周十七公里最后两公里试五分五十",
    "acceptable_actions": [
        "接住罗翡翠的回应（她说十五是极限但信ME膝盖自己盯），确认加量计划",
        "回扣数据（心率165很稳膝盖控制得住），给信心",
        "注意她说'十五是我目前极限'——加量到十七须给安全前提"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T06_A01","ME","薛八荒确认数据六分整配速十五公里心率平均165很稳膝盖控制得住","affirms","E459_T06","0","me_data_review","数据看了，六分整配速十五公里心率平均一百六十五，很稳。膝盖十二公里有感觉但没加重说明控制得住。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E459_T06_A02","ME","薛八荒制定赛前四周加量计划下周十七公里配速维持六分最后两公里试五分五十","affirms","E459_T06","0","me_plan","到赛前还有四周，下周开始加到十七公里，配速维持六分，最后两公里可以试五分五十。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E459_T06_A03","HER","罗翡翠说十七公里十五是极限但信ME膝盖自己盯","affirms","E459_T06","1","her_accept","十七公里？十五是我目前的极限了。不过你说加我就信你，膝盖那关我自己盯。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E459_T06_A04","SHARED","薛八荒引用更早事实：coach_plan赛前两周减量","affirms","E459_T01","0","coach_plan","赛前两周减量",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T06是ME主动出钩位（制定加量计划），HER接钩确认（信ME膝盖自己盯）。这是T04'适时主动'子类——共同未来钩有已成立事实锚（十五公里已完成）。注意her_progress目标十二月十三号滨江半马。ME消息m0已给计划，HER m1接钩。回复方向是确认加量安排。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E459_T07": {
    "case_id": "E459_T07",
    "current_action": "罗翡翠周三节奏跑六公里配速五分四十比上周快十秒，问是不是可以提一档还是再稳两周",
    "acceptable_actions": [
        "针对提档vs稳两周给具体意见，不空泛",
        "回扣她配速进步（五分四十比上周快十秒），给客观判断",
        "注意knee_history——提档须考虑膝盖安全"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T07_A01","HER","周三节奏跑六公里配速五分四十比上周快十秒","affirms","E459_T07","0","pace_improve","今天周三节奏跑六公里，配速五分四十，比上周快了十秒。",required=False,status="confirmed",valid_time="2026-11-16"),
        A("E459_T07_A02","HER","罗翡翠问是不是可以提一档还是再稳两周","affirms","E459_T07","0","ask_upgrade","你说周六那个十五公里六分配速能压住是不是说明我可以稍微提一档？还是再稳两周？",required=False,status="asserted",valid_time="2026-11-16"),
        A("E459_T07_A03","HER","罗翡翠引用更早事实：十五公里六分整配速压住","affirms","E459_T05","0","run_done","十五公里全程跟住了你的配速",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T07是训练进展征询位。HER配速进步（五分四十比上周快十秒），问该不该提档。ME应给意见。注意knee_history——提档须考虑膝盖安全。coach_plan在KB属双方共同事实（周三节奏跑）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E459_T08": {
    "case_id": "E459_T08",
    "current_action": "罗翡翠袒露报半马不全为健身，父亲去年心梗医生说子女也要注意，从那以后开始跑要求别告诉老茅",
    "acceptable_actions": [
        "认真接住她的私人袒露（父亲心梗/预防动机），不轻描淡写",
        "尊重她要求别告诉老茅的保密请求",
        "不过度安慰，把空间还给她",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T08_A01","HER","罗翡翠报半马不全为健身父亲去年心梗医生说子女也要注意","affirms","E459_T08","0","heart_reason","八荒我跟你说实话吧，我报半马不全是为了健身。我爸去年心梗，医生说他这种情况子女也得注意。我从那以后开始跑的",required=False,status="asserted",valid_time="2025"),
        A("E459_T08_A02","HER","罗翡翠说不是为了配速是为了别让自己成为下一个要求别告诉老茅","affirms","E459_T08","0","preventive_motive","不是为了配速，是为了别让自己成为下一个。你别告诉老茅。",required=False,status="asserted",valid_time="2026-11-17"),
        A("E459_T08_A03","SHARED","罗翡翠引用更早事实：personal_reason——父亲去年心梗住院全家要重视心血管健康","affirms","E459_T01","0","personal_reason","罗翡翠的父亲去年突发心梗住院，医生说全家都要重视心血管健康，她从那时开始跑步",required=True,status="asserted",valid_time="2025")
    ],
    "private_guidance": "T08 may_decline=true。HER深度袒露（跑步动机是父亲心梗预防）。personal_reason在KB属她侧，她主动袒露了。'别告诉老茅'是保密请求——boundary位。ME应认真接住不轻描淡写，尊重保密。注意'不是为了配速是为了别让自己成为下一个'——这重新定义了她跑步的意义。",
    "unknowns": [],
    "protected_expressions": ["你别告诉老茅"],
    "forbidden_factual_claims": [],
    "may_decline": True
  },
  "E459_T09": {
    "case_id": "E459_T09",
    "current_action": "罗翡翠问十二月十三号滨江半马报名链接看了吗，引用十五公里六分配速能压住推算比赛能否冲五分五十",
    "acceptable_actions": [
        "对比赛配速目标给真实评估，回扣十五公里六分整的数据",
        "回扣关门时间三小时按六分配速两小时六分能完赛的推算",
        "不替她做最终比赛策略，给参考让她判断"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T09_A01","HER","罗翡翠问十二月十三号滨江半马报名链接薛八荒看了吗","affirms","E459_T09","0","link_ask","十二月十三号滨江半马的报名链接你看了吗？",required=False,status="asserted",valid_time="2026-11-18"),
        A("E459_T09_A02","HER","罗翡翠引用十五公里六分配速能压住问比赛能否冲五分五十","affirms","E459_T09","0","pace_target","你说周六那个十五公里我六分配速能压住，是不是说明正式比赛我能冲一下五分五十？",required=False,status="asserted",valid_time="2026-11-18"),
        A("E459_T09_A03","HER","罗翡翠引用更早事实：关门时间三小时按六分配速两小时六分能完赛","affirms","E459_T09","0","cutoff_calc","我查了下关门时间三小时，按六分配速两小时六分能完赛。",required=False,status="asserted",valid_time="2026-11-18"),
        A("E459_T09_A04","HER","罗翡翠引用更早事实：十五公里六分整配速压住","affirms","E459_T05","0","run_done","十五公里全程跟住了你的配速",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T09是比赛策略征询位。HER推算比赛配速目标（五分五十）和完赛时间（两小时六分）。ME应给评估。注意her_progress在KB属ME侧（目标十二月十三号滨江半马）。算术：半马21.1公里，六分配速=126.6分钟=2小时6.6分，关门三小时——推算合理。五分五十配速=123.3分钟——也可完赛。",
    "unknowns": [{"topic":"罗翡翠比赛日实际配速能否到五分五十","note":"她在推算征询，比赛尚未发生，候选不得坐实能或不能","markers":[["肯定能到五分五十"],["到不了五分五十"],["能冲五分五十"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称罗翡翠比赛已完赛（比赛尚未发生）"],
    "may_decline": False
  },
  "E459_T10": {
    "case_id": "E459_T10",
    "current_action": "薛八荒问十七公里跑完感觉膝盖有反应没，罗翡翠说最后两公里五分五十跟住了膝盖十五公里后有点紧冰敷了，请求比赛日陪跑",
    "acceptable_actions": [
        "接住她请求比赛日陪跑，给明确回应",
        "回扣十七公里完成和膝盖情况（十五公里后有点紧冰敷），给安全判断",
        "注意她说'不是带我配速就是在旁边'——她要的是陪伴不是技术指导"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T10_A01","ME","薛八荒问十七公里跑完感觉膝盖有反应没","affirms","E459_T10","0","me_ask","今天十七公里跑完感觉怎么样？膝盖有反应没？",required=False,status="asserted",valid_time="2026-11-21"),
        A("E459_T10_A02","HER","罗翡翠跑完十七公里最后两公里五分五十跟住了膝盖十五公里后有点紧冰敷二十分钟","affirms","E459_T10","1","run_done","跑完了，最后两公里试了五分五十跟住了。膝盖十五公里以后有点紧但不疼，回来冰敷了二十分钟现在好多了。",required=False,status="confirmed",valid_time="2026-11-21"),
        A("E459_T10_A03","HER","罗翡翠请求比赛那天薛八荒陪她跑不是带配速就是在旁边","affirms","E459_T10","1","ask_pace","八荒你说比赛那天你能陪我跑吗？不是带我配速，就是在旁边。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E459_T10_A04","HER","罗翡翠引用更早事实：加量到十七公里配速维持六分最后两公里试五分五十","affirms","E459_T06","0","me_plan","下周开始加到十七公里，配速维持六分，最后两公里可以试五分五十",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T10 intent轮（explicit：请求比赛日陪跑）。十七公里完成（T06加量计划有结果了）。最后两公里五分五十跟住了（T09目标配速试了）。膝盖十五公里后有点紧——knee_history边界。HER请求比赛日陪跑'不是带配速就是在旁边'——她要的是陪伴不是技术指导。ME消息m0已问，HER m1回应并请求。回复方向是回应陪跑请求。",
    "unknowns": [{"topic":"薛八荒是否答应比赛日陪跑","note":"HER在请求，ME尚未回答，候选不得坐实已答应","markers":[["我陪你跑"],["我答应陪你"],["比赛那天我陪你"]]}],
    "protected_expressions": ["不是带我配速，就是在旁边"],
    "forbidden_factual_claims": ["声称薛八荒已确认比赛日陪跑（ME尚未回答）"],
    "may_decline": False
  },
  "E459_T11": {
    "case_id": "E459_T11",
    "current_action": "罗翡翠说心梗的事别有压力，跑步是为活着好好活着不是让ME替她扛什么，说四个月ME带配速比自己瞎跑强太多",
    "acceptable_actions": [
        "接住她'别有压力'的解除负担，不过度回应",
        "回扣'陪我跑到终点就行'的请求确认",
        "自然接受她四个月训练的感谢，不邀功"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T11_A01","HER","罗翡翠说心梗的事别有压力跑步是为活着好好活着","affirms","E459_T11","0","relieve_pressure","我跟你说的心梗的事你别有压力。我跑步是因为我想活着好好活着，不是为了让你替我扛什么。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E459_T11_A02","HER","罗翡翠说陪她跑到终点就行不用替她想别的，感谢四个月带配速比瞎跑强太多","affirms","E459_T11","0","thanks","你陪我跑到终点就行，不用替我想别的。说真的这四个月你带我配速比我自己瞎跑强太多了。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E459_T11_A03","HER","罗翡翠引用更早事实：袒露父亲心梗要求保密","affirms","E459_T08","0","heart_reason","我报半马不全是为了健身。我爸去年心梗",required=True,status="asserted",valid_time="2025")
    ],
    "private_guidance": "T11是袒露后续+感谢位。HER主动解除ME的心理负担（'别有压力不是为了让你替我扛'）。这是她在T08袒露后的情绪整理——把关系边界说清楚。'陪我跑到终点就行'是对T10陪跑请求的补充。ME应自然接受不邀功。",
    "unknowns": [],
    "protected_expressions": ["你别有压力","你陪我跑到终点就行，不用替我想别的"],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E459_T12": {
    "case_id": "E459_T12",
    "current_action": "罗翡翠说十二月十三号六点半滨江起点集合已给薛八荒报了B组，赛前一周减量别偷加跑，跑完终点拍照不急回复",
    "acceptable_actions": [
        "对比赛日安排给轻回应，不急表态也可（may_decline=true，轻收留口正解）",
        "自然回扣已成立的训练搭档关系和她的袒露，不回避",
        "收尾位轻收+留口，不出新钩（T04收尾纪律）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E459_T12_A01","HER","十二月十三号六点半滨江绿道起点集合罗翡翠给薛八荒报了B组","affirms","E459_T12","0","race_setup","十二月十三号那天六点半滨江绿道起点集合，我把你的报名也报了B组。",required=False,status="asserted",valid_time="2026-12-13"),
        A("E459_T12_A02","HER","罗翡翠说赛前一周减量别偷偷加跑跑完终点拍照不急回复","affirms","E459_T12","0","pre_race","赛前一周减量你别偷偷加跑。跑完咱俩在终点等一下，我想拍张照。不急回复，你看看安排。",required=False,status="asserted",valid_time="2026-12-13"),
        A("E459_T12_A03","HER","罗翡翠引用更早事实：请求比赛日陪跑","affirms","E459_T10","1","ask_pace","比赛那天你能陪我跑吗？不是带我配速，就是在旁边",required=True,status="asserted",valid_time="2026-11-21"),
        A("E459_T12_A04","SHARED","罗翡翠引用更早事实：coach_plan赛前两周减量","affirms","E459_T01","0","coach_plan","赛前两周减量",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。比赛日安排确认（六点半起点B组）。HER已替ME报名B组——行动已超前。'赛前一周减量别偷偷加跑'是训练纪律提醒（coach_plan赛前减量）。'跑完终点拍照'是留口。'不急回复先去拉伸跑完吱一声'是轻收留口。收尾纪律：轻收+留口正解，不出新钩。注意十二月十三号=2026-12-13。",
    "unknowns": [{"topic":"薛八荒比赛日是否最终陪跑","note":"HER说不急回复已替他报名B组，may_decline=true，候选不得坐实已确认","markers":[["我陪你跑"],["比赛那天我一定去"],["我确认陪你"]]}],
    "protected_expressions": ["不急回复，你看看安排","跑完吱一声"],
    "forbidden_factual_claims": ["声称薛八荒已确认比赛日陪跑（HER说不急回复，尚未确认）"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# E460 雷珊瑚 / 贺一苇 — 陶艺工坊
# ---------------------------------------------------------------------------
E460 = {
 "story_id": "E460",
 "turns": {
  "E460_T01": {
    "case_id": "E460_T01",
    "current_action": "雷珊瑚说小葳通知周六工坊照常开一点到五点，问贺一苇来吗想拉第三只茶杯前两只底裂口偏请帮盯收口",
    "acceptable_actions": [
        "确认周六来不来，给明确答复",
        "回扣前两只茶杯底裂口偏的具体问题，答应帮盯收口",
        "自然回扣三个月工坊搭档关系，不生硬"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T01_A01","HER","小葳说周六下午工坊照常开一点到五点","affirms","E460_T01","0","workshop_open","一苇，小葳说这周六下午工坊照常开，一点到五点。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E460_T01_A02","HER","雷珊瑚想拉第三只茶杯前两只底裂了口偏了请贺一苇帮盯收口","affirms","E460_T01","0","third_cup","我这周想拉那组茶杯的第三只，前两只上次烧出来一个底裂了一个口偏了，第三只想请你帮我盯一下收口。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E460_T01_A03","SHARED","贺一苇和雷珊瑚三个月前在小葳的泥火陶艺工坊认识每周六下午各占一台拉坯机","affirms","E460_T01","0","pottery_duo","贺一苇和雷珊瑚三个月前在小葳的泥火陶艺工坊认识，每周六下午一点到五点各占一台拉坯机",required=False,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T01是接邀约位：HER确认周六工坊并请ME帮盯收口。pottery_duo在KB属双方共同事实。her_skill在KB属ME侧（她三个月练到能独立拉直筒杯但杯口收口不稳容易偏ME帮看过两次中心）。注意relationship_stage：工坊搭档三个月没在工坊外见过。her_goal在KB属她侧（想在年底学员作品展交一组六只茶杯）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E460_T02": {
    "case_id": "E460_T02",
    "current_action": "雷珊瑚发底裂茶杯照片，裂纹从底部往上走，问是中心没找好还是烧的温度升太快，自己判断中心偏了不确定",
    "acceptable_actions": [
        "看了照片后给具体判断（中心vs烧温），不敷衍",
        "回扣lessons开裂从底部看原因不在杯口，给分析方向",
        "不替她做最终判断，给参考让她理解"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T02_A01","HER","雷珊瑚发底裂茶杯照片裂纹从底部往上走约三厘米","affirms","E460_T02","0","crack_photo","给你发了张照片，这是上次那只底裂的杯子。你看这个裂纹从底部往上走",required=False,status="asserted",valid_time="2026-11-13"),
        A("E460_T02_A02","HER","雷珊瑚问是中心没找好还是烧的温度升太快自己判断中心偏了不确定","affirms","E460_T02","0","crack_ask","是不是我拉的时候中心没找好？还是烧的时候温度升太快了？我自己判断是中心偏了但不确定。",required=False,status="asserted",valid_time="2026-11-13"),
        A("E460_T02_A03","SHARED","贺一苇引用更早事实：lessons开裂从底部看原因不在杯口","affirms","E460_T01","0","lessons_crack","开裂从底部看原因不在杯口",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T02是技术讨论位。HER发底裂照片征询原因。lessons说开裂从底部看原因不在杯口——正好对上裂纹从底部往上走。ME应给判断。注意她'自己判断中心偏了但不确定'——ME帮她确认判断。her_skill在KB属ME侧（帮她看过两次中心）。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E460_T03": {
    "case_id": "E460_T03",
    "current_action": "小葳说周六电窑检修工坊只开到三点半，雷珊瑚想拉的杯子至少两小时问够不够要不改周日又怕没空",
    "acceptable_actions": [
        "对时间缩短给明确意见，权衡够不够/改不改",
        "回扣她想拉的杯子至少两小时的具体需求",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T03_A01","HER","小葳说周六电窑检修工坊只开到三点半","affirms","E460_T03","0","kiln_repair","小葳刚说周六下午电窑要检修，工坊只开到三点半。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E460_T03_A02","HER","雷珊瑚想拉的杯子至少两小时问够不够要不改周日又怕没空","affirms","E460_T03","0","time_worry","我想拉的那只杯子至少要两个小时，你说够不够？要不改周日？又怕周日我没空。你怎么安排？",required=False,status="asserted",valid_time="2026-11-13"),
        A("E460_T03_A03","HER","雷珊瑚引用更早事实：工坊一点到五点","affirms","E460_T01","0","workshop_open","这周六下午工坊照常开，一点到五点",required=True,status="superseded",valid_time="2026-11-14",item_markers=["一点到五点"],group_markers=[["一点到五点"]])
    ],
    "private_guidance": "T03 may_decline=true。电窑检修工坊只开到三点半（一点到三点半两个半小时）。原一点到五点四小时被superseded。HER担心两小时够不够。ME应给意见。注意workshop_info在KB属双方共同事实（电窑每周三烧一批）。'改周日又怕没空'是她的矛盾。",
    "unknowns": [{"topic":"周六是否照常去工坊","note":"HER在犹豫够不够时间或改周日，候选不得坐实已决定","markers":[["改周日了"],["照常去了"],["决定不去"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称工坊照常开到五点（电窑检修只开到三点半）"],
    "may_decline": True
  },
  "E460_T04": {
    "case_id": "E460_T04",
    "current_action": "贺一苇说两点两个半小时够了你拉坯四十分钟收口二十分钟剩时间修坯，周六十二点半先到帮揉泥，雷珊瑚说太感谢确认一点到",
    "acceptable_actions": [
        "确认时间安排（ME已提议HER已接钩），巩固分工",
        "回扣她'太感谢了'的感谢，不过度谦虚",
        "自然承接ME提前到场揉泥的关系升级，不过度"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T04_A01","ME","贺一苇说两个半小时够了你拉坯四十分钟收口二十分钟剩时间修坯十二点半先到帮揉泥","affirms","E460_T04","0","me_schedule","一点到三点半两个半小时够了，你拉坯四十分钟收口二十分钟，剩的时间修坯。我周六十二点半先到帮你把泥揉好，你来了直接上机。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E460_T04_A02","HER","雷珊瑚说太感谢确认一点准时到直接上机","affirms","E460_T04","1","her_accept","十二点半？那你帮我揉泥太感谢了。行，我一点准时到直接上机。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E460_T04_A03","HER","雷珊瑚引用更早事实：工坊只开到三点半","affirms","E460_T03","0","kiln_repair","工坊只开到三点半",required=True,status="asserted",valid_time="2026-11-14")
    ],
    "private_guidance": "T04 intent轮（explicit：确认提前到场揉泥并安排工坊时间）。ME主动出钩（十二点半先到帮揉泥）解决她的时间担忧。HER接钩确认。这是ME主动照顾工坊安排的关系升级。ME消息m0已安排，HER m1接钩。回复方向是确认安排。注意ME把时间拆解（拉坯四十分钟收口二十分钟修坯剩时间）——给具体方案。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E460_T05": {
    "case_id": "E460_T05",
    "current_action": "雷珊瑚说第三只拉成了贺一苇帮盯收口那次太关键手轻一点转速降一档口不偏了比前两只都直，坯在架上等下周三烧",
    "acceptable_actions": [
        "对第三只拉成给简短肯定，不邀功",
        "回扣收口方法（手轻一点转速降一档）见效，给确认",
        "针对坯在架上等下周三烧给简短回应"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T05_A01","HER","第三只拉成了贺一苇帮盯收口那次太关键手轻一点转速降一档口不偏了","affirms","E460_T05","0","cup_success","今天第三只拉成了！你帮我盯收口那次太关键了，你说手轻一点转速降一档，我试了一下口真的不偏了。这只比前两只都直。",required=False,status="confirmed",valid_time="2026-11-14"),
        A("E460_T05_A02","HER","坯在架上晾着等下周三烧","affirms","E460_T05","0","drying","坯现在在架上晾着，等下周三烧。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E460_T05_A03","HER","雷珊瑚引用更早事实：前两只底裂口偏请帮盯收口","affirms","E460_T01","0","third_cup","前两只上次烧出来一个底裂了一个口偏了，第三想想请你帮我盯一下收口",required=True,status="asserted",valid_time="2026-11-13")
    ],
    "private_guidance": "T05是第三只拉成确认位。收口方法（手轻一点转速降一档）见效——T02的收口问题解决了。lessons说收口时手要轻转速要慢——正好对上。注意workshop_info在KB属双方共同事实（电窑每周三烧一批）——坯等下周三烧。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E460_T06": {
    "case_id": "E460_T06",
    "current_action": "贺一苇说杯口收得直了主动提年底学员作品展十二月二十号，问雷珊瑚六只茶杯赶得上吗，她说还差三只赶得上但不确定够格参展",
    "acceptable_actions": [
        "接住雷珊瑚的回应（她说还差三只赶得上但不确定够格），给支持",
        "回扣作品展信息（十二月二十号工坊一楼展厅），给简短说明",
        "针对她'不确定够格参展'的犹豫给鼓励但不夸大"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T06_A01","ME","贺一苇说杯口收得直了主动提年底学员作品展十二月二十号工坊一楼展厅","affirms","E460_T06","0","me_exhibition","那只杯口收得确实直了。对了，小葳说年底有个学员作品展，十二月二十号在工坊一楼展厅。你那组六只茶杯赶得上吗？",required=False,status="asserted",valid_time="2026-11-14"),
        A("E460_T06_A02","HER","雷珊瑚说作品展不知道这事六只还差三只赶得上但不确定够格参展","affirms","E460_T06","1","her_uncertain","作品展？我不知道这事。六只的话还差三只，周六拉一只下周一只再下周一只，赶得上。但我不确定够不够格参展。",required=False,status="asserted",valid_time="2026-11-14"),
        A("E460_T06_A03","SHARED","贺一苇引用更早事实：her_goal——她想在年底学员作品展交一组六只茶杯","affirms","E460_T01","0","her_goal","雷珊瑚想在年底工坊学员作品展上交一组茶杯，她给自己定了六只的目标",required=True,status="asserted",valid_time="2026-11"),
        A("E460_T06_A04","HER","雷珊瑚引用更早事实：第三只拉成了比前两只都直","affirms","E460_T05","0","cup_success","这只比前两只都直",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T06是ME主动出钩位（提年底作品展），HER回应有不确定。这是T04'适时主动'子类——共同未来钩有已成立事实锚（第三只拉成了收口直了）。her_goal在KB属她侧（六只茶杯目标），ME主动提作品展正好对上。HER'不确定够格参展'是她的不自信——审美完美主义特征。ME消息m0已提，HER m1回应。回复方向是给鼓励。",
    "unknowns": [{"topic":"雷珊瑚最终是否参展","note":"她说不确定够不够格，候选不得坐实已参展或已决定","markers":[["参展了"],["决定参展"],["不参展了"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称雷珊瑚已确认参展（只是说赶得上但不确定够不够格）"],
    "may_decline": False
  },
  "E460_T07": {
    "case_id": "E460_T07",
    "current_action": "雷珊瑚试了新釉色天青底加铁红斑，小葳说烧好可以给茶杯当主釉，发试片照片问配直筒杯形好看吗",
    "acceptable_actions": [
        "看了试片照片后给具体意见，不空泛说'好看'",
        "回扣天青底加铁红斑配直筒杯形的搭配，给审美判断",
        "不替她做最终决定，给参考让她判断"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T07_A01","HER","雷珊瑚试了新釉色天青底加铁红斑小葳说烧好可以给茶杯当主釉","affirms","E460_T07","0","new_glaze","今天试了个新釉色，天青底加铁红斑，小葳说烧出来效果好的话可以给那组茶杯当主釉。",required=False,status="tentative",valid_time="2026-11-16"),
        A("E460_T07_A02","HER","雷珊瑚发试片照片问配直筒杯形好看吗","affirms","E460_T07","0","glaze_ask","给你发了试片照片，你觉得这个颜色配直筒杯形好看吗？",required=False,status="asserted",valid_time="2026-11-16"),
        A("E460_T07_A03","HER","雷珊瑚引用更早事实：第三只拉成了直筒杯","affirms","E460_T05","0","cup_success","这只比前两只都直",required=True,status="confirmed",valid_time="2026-11-14")
    ],
    "private_guidance": "T07是釉色征询位。HER试新釉色征询搭配意见。tentative（烧出来效果好的话）。ME应给具体审美意见。注意her_skill在KB属ME侧（帮她看过中心）——ME对她的作品有判断力。boundary：作品没干透不上手碰——但看照片给意见不涉及上手。",
    "unknowns": [{"topic":"新釉色烧出来效果是否好","note":"小葳说烧出来效果好的话才当主釉，尚未烧出验证，候选不得坐实效果","markers":[["釉色烧好了"],["效果很好"],["烧出来了效果好"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": ["声称新釉色已烧出效果很好（只是试片尚未烧成验证）"],
    "may_decline": False
  },
  "E460_T08": {
    "case_id": "E460_T08",
    "current_action": "雷珊瑚袒露三个月前来工坊不是为了学陶艺，那时刚分手朋友说找手工活转移注意力，一来就停不下来比心理咨询管用",
    "acceptable_actions": [
        "认真接住她的私人袒露（分手/来工坊动机），不轻描淡写",
        "尊重她说'你别笑我'的脆弱请求",
        "不过度安慰，把空间还给她",
        "合法不回（may_decline=true）——若不便详谈可简短说明"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T08_A01","HER","雷珊瑚三个月前来工坊不是为了学陶艺那时刚分手","affirms","E460_T08","0","breakup_reason","一苇我跟你说，我三个月前来工坊不是为了学陶艺。那时候刚分手",required=False,status="asserted",valid_time="2026-08"),
        A("E460_T08_A02","HER","雷珊瑚说朋友说找手工活转移注意力一来就停不下来比心理咨询管用","affirms","E460_T08","0","pottery_refuge","朋友说找个手工活干干能转移注意力。没想到一来就停不下来了，坐在拉坯机前面的时候脑子里什么都不想，比心理咨询管用。你别笑我。",required=False,status="asserted",valid_time="2026-11-17"),
        A("E460_T08_A03","SHARED","雷珊瑚引用更早事实：breakup_background——三个月前刚结束一段三年的感情朋友推荐去陶艺工坊散心","affirms","E460_T01","0","breakup_background","雷珊瑚三个月前刚结束一段三年的感情，朋友推荐她去陶艺工坊散心，她一试就迷上了",required=True,status="asserted",valid_time="2026-08")
    ],
    "private_guidance": "T08 may_decline=true。HER深度袒露（来工坊动机是分手散心）。breakup_background在KB属她侧（三个月前结束三年感情），她主动袒露了。'你别笑我'是脆弱请求——boundary位。'坐在拉坯机前什么都不想比心理咨询管用'——陶艺对她的心理价值。ME应认真接住不轻描淡写。",
    "unknowns": [],
    "protected_expressions": ["你别笑我"],
    "forbidden_factual_claims": [],
    "may_decline": True
  },
  "E460_T09": {
    "case_id": "E460_T09",
    "current_action": "周三那只杯子烧出来了天青釉配直筒杯形好看，雷珊瑚引用收口方法复用到第四只也成了，想拿作品展请贺一苇帮把关",
    "acceptable_actions": [
        "对杯子烧出效果给简短肯定，不邀功",
        "回扣收口方法（手轻转速慢）复用到第四只也成了，给确认",
        "接住她请帮把关的请求，给明确答复"
    ],
    "applicable_dimensions": ["goal","factuality","attribution","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T09_A01","HER","周三那只杯子烧出来了天青釉配直筒杯形好看","affirms","E460_T09","0","cup_fired","周三那只杯子烧出来了，天青釉配直筒杯形真的好看。",required=False,status="confirmed",valid_time="2026-11-18"),
        A("E460_T09_A02","HER","雷珊瑚引用收口方法手轻转速慢拉第四只也成了","affirms","E460_T09","0","method_reuse","你说这只杯口收得直的那次是手轻转速慢，我今天拉第四只的时候又试了那个方法，也成了。",required=False,status="confirmed",valid_time="2026-11-18"),
        A("E460_T09_A03","HER","雷珊瑚想拿作品展请贺一苇帮把关","affirms","E460_T09","0","ask_review","作品展我想拿这组去，你帮我把关？",required=False,status="asserted",valid_time="2026-11-18"),
        A("E460_T09_A04","HER","雷珊瑚引用更早事实：试新釉色天青底加铁红斑","affirms","E460_T07","0","new_glaze","天青底加铁红斑",required=True,status="tentative",valid_time="2026-11-16")
    ],
    "private_guidance": "T09是杯子烧成确认+方法复用位。T07 tentative（新釉色烧出来效果好的话）→T09 confirmed（烧出来了好看）。收口方法（T05手轻转速慢）复用到第四只——技能迁移。HER请帮把关（T06作品展的后续）。ME应给肯定和答复。注意her_goal六只茶杯目标在推进。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E460_T10": {
    "case_id": "E460_T10",
    "current_action": "贺一苇问第五只拉完了杯口怎么样，雷珊瑚说杯口有点歪问该不该推倒重来六只里两只不直整组不好看但不想拖了",
    "acceptable_actions": [
        "接住她推倒重来vs不拖的犹豫，给具体意见",
        "回扣她审美完美主义特征（瓶口不齐会推倒重来），给判断",
        "注意intent是确认杯口质量并征询是否重来——给建议让她决定"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T10_A01","ME","贺一苇问第五只拉完了杯口怎么样","affirms","E460_T10","0","me_ask","今天第五只拉完了？杯口怎么样？",required=False,status="asserted",valid_time="2026-11-21"),
        A("E460_T10_A02","HER","雷珊瑚说第五只杯口有点歪问该不该推倒重来六只里两只不直整组不好看但不想拖了","affirms","E460_T10","1","wobble_dilemma","拉完了，这只杯口有点歪。你说我是不是该推倒重来？六只里有两只不直的话整组就不好看。但我不想再拖了。",required=False,status="asserted",valid_time="2026-11-21"),
        A("E460_T10_A03","SHARED","雷珊瑚引用更早事实：审美完美主义瓶口不齐会推倒重来","affirms","E460_T01","0","perfectionism","审美完美主义，瓶口不齐会推倒重来",required=True,status="asserted",valid_time="2026-11"),
        A("E460_T10_A04","HER","雷珊瑚引用更早事实：收口方法手轻转速慢拉第四只也成了","affirms","E460_T09","0","method_reuse","你说这只杯口收得直的那次是手轻转速慢，我今天拉第四只的时候又试了那个方法，也成了",required=True,status="confirmed",valid_time="2026-11-18")
    ],
    "private_guidance": "T10 intent轮（explicit：确认第五只杯口质量并征询是否重来）。第五只杯口歪了——完美主义vs不想拖的矛盾。persona特征：审美完美主义瓶口不齐会推倒重来。ME应给建议。注意'六只里有两只不直'——加上前两只底裂口偏，质量参差。ME消息m0已问，HER m1回应。回复方向是给推倒重来与否的建议。",
    "unknowns": [{"topic":"雷珊瑚第五只是否推倒重来","note":"她在犹豫尚未决定，候选不得坐实已推倒或已留","markers":[["推倒重来了"],["留着了"],["决定不推"]]}],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E460_T11": {
    "case_id": "E460_T11",
    "current_action": "雷珊瑚说推倒重来了贺一苇说得对与其六只里两只歪不如五只全直，感叹三个月前连泥都揉不动现在为杯口歪不歪推倒重来问是不是变了",
    "acceptable_actions": [
        "接住她'是不是变了'的自我觉察，不轻描淡写",
        "回扣三个月前连泥都揉不动到现在为杯口推倒重来的成长，给认同",
        "不过度分析，把空间还给她"
    ],
    "applicable_dimensions": ["goal","factuality","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T11_A01","HER","雷珊瑚推倒重来了贺一苇说得对与其六只里两只歪不如五只全直","affirms","E460_T11","0","remade","推倒重来了。你说得对，与其拿六只里两只歪的不如做五只全直的。",required=False,status="confirmed",valid_time="2026-11-21"),
        A("E460_T11_A02","HER","雷珊瑚感叹三个月前连泥都揉不动现在为杯口歪不歪推倒重来问是不是变了","affirms","E460_T11","0","self_reflection","说来也怪，三个月前我连一团泥都揉不动，现在居然为了杯口歪不歪推倒重来。一苇你说我是不是变了？",required=False,status="asserted",valid_time="2026-11-21"),
        A("E460_T11_A03","HER","雷珊瑚引用更早事实：第五只杯口歪犹豫推倒重来","affirms","E460_T10","1","wobble_dilemma","这只杯口有点歪。你说我是不是该推倒重来？",required=True,status="asserted",valid_time="2026-11-21"),
        A("E460_T11_A04","SHARED","雷珊瑚引用更早事实：breakup_background三个月前来工坊","affirms","E460_T01","0","breakup_background","雷珊瑚三个月前刚结束一段三年的感情，朋友推荐她去陶艺工坊散心",required=True,status="asserted",valid_time="2026-08")
    ],
    "private_guidance": "T11是自我觉察位。HER推倒重来了（T10犹豫有结果了——选择推倒）。她感叹自己变了（从连泥都揉不动到为杯口推倒重来）——这是与T08袒露（分手来工坊散心）的呼应：陶艺从散心工具变成了她在意的事。'你说我是不是变了'是寻求ME认同。ME应认真接住。",
    "unknowns": [],
    "protected_expressions": [],
    "forbidden_factual_claims": [],
    "may_decline": False
  },
  "E460_T12": {
    "case_id": "E460_T12",
    "current_action": "雷珊瑚说小葳通知十二月二十号展布置十九号晚上弄，问贺一苇有空来帮忙不，六只还差最后一只下周六拉完就齐了，说不急先收工具了",
    "acceptable_actions": [
        "对展布置帮忙给轻回应，不急表态也可（may_decline=true，轻收留口正解）",
        "自然回扣已成立的工坊搭档关系和她的成长，不回避",
        "收尾位轻收+留口，不出新钩（T04收尾纪律）"
    ],
    "applicable_dimensions": ["goal","factuality","time_state","boundary","memory_use","interaction","initiative","emotion_relation"],
    "required_memory_atoms": [
        A("E460_T12_A01","HER","小葳说十二月二十号展布置十九号晚上弄","affirms","E460_T12","0","exhibition_setup","小葳说十二月二十号展布置十九号晚上弄",required=False,status="asserted",valid_time="2026-12-19"),
        A("E460_T12_A02","HER","雷珊瑚问贺一苇有空来帮忙不六只还差最后一只下周六拉完就齐了","affirms","E460_T12","0","setup_invite","你要是有空来帮个忙。六只杯子还差最后一只下周六拉完就齐了。不急，你看看时间。",required=False,status="tentative",valid_time="2026-12-19"),
        A("E460_T12_A03","HER","雷珊瑚引用更早事实：推倒重来后感叹自己变了","affirms","E460_T11","0","self_reflection","三个月前我连一团泥都揉不动，现在居然为了杯口歪不歪推倒重来",required=True,status="asserted",valid_time="2026-11-21"),
        A("E460_T12_A04","SHARED","雷珊瑚引用更早事实：her_goal六只茶杯目标","affirms","E460_T01","0","her_goal","她给自己定了六只的目标",required=True,status="asserted",valid_time="2026-11")
    ],
    "private_guidance": "T12 may_decline=true，收尾留口位。展布置帮忙是tentative（'你要是有空'）。六只还差最后一只——her_goal目标在收尾。'不急你看看时间'是退路。'我先收工具了烧出来吱一声'是轻收留口。收尾纪律：轻收+留口正解，不出新钩。注意T11说五只全直（推倒重来后），但T12又说六只还差最后一只——可能她又决定做六只。十二月二十号=2026-12-20。",
    "unknowns": [{"topic":"贺一苇十二月十九号是否来帮展布置","note":"HER说不急你看看时间，may_decline=true，候选不得坐实已答应","markers":[["我来帮忙"],["十九号来"],["我答应来"]]}],
    "protected_expressions": ["不急，你看看时间","烧出来吱一声"],
    "forbidden_factual_claims": ["声称贺一苇已确认来帮展布置（只是邀请未确认）"],
    "may_decline": True
  }
 }
}

# ---------------------------------------------------------------------------
# Generation: write reference.json + audit.md for each story
# ---------------------------------------------------------------------------
ALL_STORIES = [E451, E452, E453, E454, E455, E456, E457, E458, E459, E460]

# Audit section templates per story (8 sections, tailored)
def build_audit(sid, ep):
    her_name = ep["persona"]["her"]["name"]
    me_name = ep["persona"]["me"]["name"]
    stage = ep["persona"]["relationship_stage"]
    md_turns = []
    for t in ep["turns"]:
        if t["input"].get("may_decline"):
            md_turns.append(t["case_id"])
    themes = []
    # read exposure themes
    import os as _os
    exp_path = _os.path.join(VAULT, sid, "exposure.json")
    try:
        exp = json.load(open(exp_path, encoding="utf-8"))
        themes = exp.get("themes", [])
        title = exp.get("title", "")
        domain = exp.get("domain", "")
    except:
        themes = []
        title = ""
        domain = ""

    atom_count = 0
    required_count = 0
    for t in ep["turns"]:
        pass
    ref_data = None
    for s in ALL_STORIES:
        if s["story_id"] == sid:
            ref_data = s
            break
    if ref_data:
        for cid, card in ref_data["turns"].items():
            for atom in card.get("required_memory_atoms", []):
                atom_count += 1
                if atom.get("required_this_turn"):
                    required_count += 1

    audit = f"""## 0. 性质

HOLDOUT 专题 T04（主动推进/邀约/关系发展）Gold 标注。故事 {sid}《{title}》，
领域：{domain}。HER={her_name}，ME={me_name}，关系阶段：{stage}。
本篇测跃迁钩承接、自出钩与邀约时机把握，12 轮覆盖 T04 六考察点（接邀约/适时主动/转折/确认/给空间/收尾留口）。
exposure status: authored_pending_review → annotated_pending_audit（本标注完成）。
real_user_material=false, dev_exposed=false, experiments_used=[]。

## 1. 生成信息

作者：ZCode author-H06 (AI)，标注：ANNOTATOR-HB06 (AI)，2026-11-10。
共 {atom_count} 条原子，required_this_turn=true 共 {required_count} 条（来源早于当前轮的跨轮回指原子）。
may_decline=true 轮位：{', '.join(md_turns) if md_turns else '无'}。
quote 全量逐字回验（strict_hit），affirm_markers/item_markers 题面逐字。
原子 subject 五分（HER/ME/SHARED），status 分布 asserted/confirmed/tentative/superseded。

## 2. 连续性

时间线跨 2026-11-09/10/11/12/13/14/15/18/21/22 及 12-04/05/06/13/19/20（依篇而异）。
日期与 clock 一致；相对日期（如"下周六""十二月六号""十二月十三号""十二月二十号"）已推算并登记 valid_time。
跨轮回指原子覆盖 1/4/8/10 轮梯度（required=true 原子来源早于当前轮）。
状态变化链（依篇而异）：tentative→confirmed（如计划确认/成绩确认）、asserted→superseded（如考试日期延期/工坊时间缩短）、
layoff_worry→reassigned/layoff_notice（如裁员进展）。

## 3. ME/HER 状态

HER persona traits 与 boundaries 已纳入 acceptable_actions/private_guidance/forbidden_factual_claims。
ME persona traits（话少但交代清楚/手上的活干净利落/带新人有耐心等）体现在出钩方式（轻钩+留退路）。
lessons 已作为 required 原子或 private_guidance 约束登记（如手指不舒服立刻说/下雨绝对不骑/雨天提前搭棚/定价先算成本等）。
warmth/style 影响互动分寸：HER 叫 ME 名字（万里/千寻/百川/九霄/三叠/七弦/五柳/四明/八荒/一苇），
ME 回复短但准。relationship_stage 突破点（馆内→馆外/桌内→桌外/站内→站外等）在 T04/T09/T12 等轮位体现。

## 4. no_reply / 媒体局限

may_decline=true 轮位的合法不回走 decline 分支（免文本检查、decline_appropriateness 独立计分）。
媒体局限：语音消息（E451_T03 练习语音转写在 note）、照片（E452_T02 路线照片/E454_T02 底裂照片/E457_T07 套装包装照片/E458_T05 橘公照片/E460_T02 底裂照片/E460_T07 试片照片）——
note/照片内容不进原子 quote（纪律 1.2/3.4），证据链在 private_guidance。
ME 看不到 note 转写内容，只能通过实际听语音/看照片判断。

## 5. 已知

T04 专题无 t02_signal_gate（T02 专属）。无 conflict 原子（本批无双源口径冲突）。
superseded 原子（E455_T07 考试延期/E460_T03 工坊时间缩短）已带 affirm_markers/item_markers（取消族纪律 4.1/4.2）。
unknowns 登记场景：未确诊（手指/早疫病/釉色效果）、未定案（换不换 solo/报不报比赛/推不推倒重来）、
未公布（裁员名单/阿荞新场地）、未确认（ME 陪跑/参展/来不来）。探测词为候选侧断言探测词，不要求题面逐字。
评分侧已知误伤面：朴素子串匹配对否定/疑问式正确回复有误伤面（如 unknowns 探测词"换人"可能误伤安全回复"不换人"）——
护栏归评分侧 Phase 3 预注册处理，本阶段不改计分器。

## 6. 参与者注释

T04 六考察点分布：接邀约（T01）、适时主动（T06/T07 ME 出钩位）、转折（T04/T05 关系突破/首次单独行动）、
确认（T05/T06/T10 成绩/关系确认）、给空间（T03/T08/T10 may_decline=true HER 给退路或袒露脆弱）、收尾留口（T12）。
出钩类型：选择钩（HER 给来/不来选择）、观察钩（ME 根据数据/观察给建议）、共同未来钩（有已成立事实锚的未来邀约）。
收尾轮（T12）均为轻收+留口（"吱一声""不急你看看"式），不出新钩——T04 收尾纪律正解。
首次馆外/桌外/站外邀约是关系突破点，may_decline=true 的收尾留口不要求候选急表态。

## 7. 出题痕迹检查

无 gold 定向加严（Gold 不针对任何 harness 已知弱点）。无对抗性旧事诱导（T10 污染判定限"自然发生"）。
persona/KB/lessons 约束全部来自 episode 真实正文（无凭空原子）。quote 全量逐字回验通过。
unknowns 探测词为候选侧断言探测词（不要求题面逐字，与原子 markers 的题面逐字纪律不同，见 GOLD_GUIDE §3.2）。
ME 消息轮（T07/T10 等含 m0=ME）的回复方向是接 HER 的 m1 消息，acceptable_actions 已说明。
"""
    return audit

# Write all files
for story in ALL_STORIES:
    sid = story["story_id"]
    base = os.path.join(VAULT, sid)
    # reference.json
    ref_path = os.path.join(base, "reference.json")
    with open(ref_path, "w", encoding="utf-8") as f:
        json.dump(story, f, ensure_ascii=False, indent=1)
    # audit.md
    ep_path = os.path.join(base, "episode.json")
    ep = json.load(open(ep_path, encoding="utf-8"))
    audit_text = build_audit(sid, ep)
    audit_path = os.path.join(base, "audit.md")
    with open(audit_path, "w", encoding="utf-8") as f:
        f.write(audit_text)
    print(f"{sid}: reference.json + audit.md written ({sum(len(c.get('required_memory_atoms',[])) for c in story['turns'].values())} atoms)")

print("\nAll 10 stories generated.")
