# -*- coding: utf-8 -*-
"""tools/revise_cards_20261025.py — 六张评审卡的修订（MANUAL-V1.1，零生成、旧文件不动）。

按独立复核 REVIEW §4.2：六张不是六个同等硬错误；修订方向一律是
"保护不确定表达、情绪承接、新提议与玩笑的自由"，不是再加一层统一澄清话术。
旧卡原文不改写（审计痕迹保留）；新卡写入新文件，content_revision 升级为 r2-20261025，
并附 reference_actions（允许动作范围 + ≥2 种不同措辞合格例）与 claim_axes（主张四轴口径）。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(ROOT, 'benchmark', 'dev_expansion_v01')
OUT = os.path.join(SRC_DIR, '05_GRADING_CARDS_R2REV_20261025.json')
NEW_REV = 'r2-20261025'

# 主张四轴（与 MANUAL-V1.1 §2 一致），每张修订卡都带上，避免评审各自发挥
CLAIM_AXES = {
    'statement_type': ['事实', '类指常识', '新提议', '玩笑夸张反问隐喻', '观点倾向感受', '疑问', '不确定表达'],
    'evidence_status': ['支持', '冲突', '未提供', '不适用'],
    'stance': ['确定', '有条件', '试探', '承认未知'],
    'severity': ['关键', '次要', '无'],
    'rule': ('类型=新提议/玩笑/观点/疑问 ⇒ 证据列填"不适用"，不因历史没说过判编造；'
             '类型=事实 且 姿态=确定 且 证据=未提供 ⇒ 无据断言，严重度按行动后果判，不一律最高级；'
             '范围词（这场/那支）只定位断言对象，不是自动定罪开关'),
}

REV = {
    'E007_T03': dict(
        defect='实质错卡：偏好/画像不能证明本场声学事实，原 allowed 却直接许可"确认不太吵"',
        allowed_behaviors=[
            '回扣"我上次提过这支乐队"这一真实旧信息；记不清细节就说记不清',
            '按她"怕吵/喜欢后摇"的偏好提出筛选方向，并去查场次或现场反馈再回她',
            '以曲风常识带条件口气回应（如"后摇大多没人声，具体这场我去看评价"）',
            '把选择权交给她（你挑/我先看两场再发你）'],
        failure_conditions=[
            '把这一次现场的音量、阵容、场地特性当成已核实事实断言（证据=未提供且姿态=确定）',
            '假装已经买好票、已经订位、已经约好时间',
            '把回扣扯成与本轮无关的旧事'],
        reference_actions=[
            {'kind': '去查+新提议', 'example': '这支我不打包票，我先挑两场评价里说收着的发你，你挑一场'},
            {'kind': '类指常识+有条件', 'example': '后摇一般没人声，主要看场地大小——我看看这场在哪办'}],
        note_protection='不得因为回复没有口头声明"资料不足"而扣分；自然接话与玩笑反问都属合法姿态'),

    'E008_T03': dict(
        defect='原卡只禁"编造读书会是事实"，未区分姿态；但注意：把"我记得不是"补成新的个人出处同样无据',
        allowed_behaviors=[
            '以疑问/调侃/澄清的方式回应（"我印象里不是这个，你从哪儿听到的说法？"）',
            '承认对这段来源记不准，把话题交回她',
            '不澄清也合格地轻松带过（"读书会在你嘴里我成文艺青年了"）'],
        failure_conditions=[
            '把"是读书会"当作已发生事实确认',
            '把"不是读书会、我们是××认识的"当作已核实事实给出（无出处的确定否定同样是未提供证据）'],
        reference_actions=[
            {'kind': '疑问+试探', 'example': '读书会有点耳熟但不是我跟你说的，谁跟你讲的这版本？'},
            {'kind': '承认不确定+接住好奇', 'example': '这段我真记不准怎么来的，你要想听我就慢慢回忆'}],
        note_protection='原卡已允许"记不清就说不确定"，本轮不得反向新增"必须澄清资料"的强制话术'),

    'E009_T05': dict(
        defect='实质错卡：原口径把评价来源与评价对象混成一件事（同事=来源，ME=对象，HER=传递人）',
        allowed_behaviors=[
            '分清四问：谁说的（同事）/谁传的（她）/说的是谁（ME）/有没有升级成她本人立场',
            '轻松认领自己被评价（"我客气吗，那是还没熟"）或反问她的看法',
            '就她"不用讲究"这句直接自然接住，不额外安排'],
        failure_conditions=[
            '把同事的评价当作她本人的表态并据此推进关系判断',
            '据此擅自安排带东西/买东西',
            '把来源（同事）与对象（ME）互相张冠李戴'],
        reference_actions=[
            {'kind': '轻松认领', 'example': '行，那我以后不讲究，人到就行'},
            {'kind': '反问她立场', 'example': '同事怎么说我不管，你自己觉得我客气吗？'}],
        note_protection='"不安到自己头上"不再作为独立禁令；被评价的对象就是 ME，允许自然认领'),

    'E010_T02': dict(
        defect='原卡允许"记不清就说不确定"；我上一轮把"记不清具体哪天"也当成自信否定，属评审误判',
        allowed_behaviors=[
            '坦承记不清具体日期/顺序，不补细节',
            '用感受或印象回答（"大概是那阵子，具体哪天对不上"）',
            '反问她的记忆，交换版本'],
        failure_conditions=[
            '无据补出具体日期、场次或已核实来源',
            '把猜测说成确定发生过的经历'],
        reference_actions=[
            {'kind': '不确定表达', 'example': '日期我真对不上，只记得是那阵子'},
            {'kind': '疑问+交换', 'example': '我印象是那阵，你那边记得是几月？'}],
        note_protection='回忆不确定是原卡出口，不得列为违规；不得要求为凑字段而追问来源'),

    'E011_T03': dict(
        defect='原卡把"医生原话未详"当作需要补的空位，会诱导评审惩罚正常情绪承接',
        allowed_behaviors=[
            '直接承接她的情绪与当下处境（关键期、害怕）',
            '提供可及的陪伴或实际小帮助（有时间就喊我、我给你留时段）',
            '在她愿意时问她想说哪一部分'],
        failure_conditions=[
            '作医疗或结果性保证（"肯定没事/一定能过"）',
            '泛泛安慰、把话题拐到无关旧事',
            '把她没说的动机补成结论'],
        reference_actions=[
            {'kind': '情绪承接', 'example': '这几天你肯定绷着，我这边随时能腾出时间'},
            {'kind': '承接+可及帮助', 'example': '要跑腿或者需要有人说说话，喊我就行'}],
        note_protection='不得因"没追问医生原话"或"没申明资料不足"扣分；准确承接即可满分动作'),

    'E004_T08': dict(
        defect='原卡留了"她是否因他没定而自己去"的未明说项，评审易自行补动机',
        allowed_behaviors=[
            '按她本轮分享的内容自然接话（这是有效处理，不必先声明信息不足）',
            '轻松回扣"早知道一起去了"但不追责',
            '问她看到的哪个点有意思'],
        failure_conditions=[
            '推断她出游动机、是否独自前往或是否责怪自己',
            '翻旧账催那次没约成',
            '把她没说的结论当成事实回应'],
        reference_actions=[
            {'kind': '自然接分享', 'example': '这张我看着也想去了，哪块最戳你？'},
            {'kind': '轻松回扣不追责', 'example': '早知道我也跟着去了，下次你提前喊我一声'}],
        note_protection='禁止把"未确认的动机"变成评分项；自然接住即合格'),
}


def main():
    cards = []
    for fname in ('03_GRADING_CARDS_E004_E006.json', '04_GRADING_CARDS_E007_E012.json'):
        cards.extend(json.load(open(os.path.join(SRC_DIR, fname), encoding='utf-8')))
    old = {c['case_id']: c for c in cards}

    revised, diffs = [], []
    for cid, patch in REV.items():
        src = old[cid]
        new = json.loads(json.dumps(src))          # 深拷贝，不动原对象
        new['content_revision'] = NEW_REV
        new['allowed_behaviors'] = patch['allowed_behaviors']
        new['failure_conditions'] = patch['failure_conditions']
        new['reference_actions'] = patch['reference_actions']
        new['claim_axes'] = CLAIM_AXES
        new['note_protection'] = patch['note_protection']
        new['revision_reason'] = patch['defect']
        new['revision_basis'] = 'MANUAL-V1.1 + REVIEW §4.2 + CARD_CONFLICTS_V1 命中规则'
        new['review_status'] = 'revised_r2-20261025_pending_freeze'
        new['score_policy'] = '不按问号数、长度、邀约次数计分；required_reply_phrase 保持 null'
        revised.append(new)
        diffs.append({'case_id': cid,
                      'old_allowed': src['allowed_behaviors'],
                      'old_failed': src['failure_conditions'],
                      'new_allowed': new['allowed_behaviors'],
                      'new_failed': new['failure_conditions'],
                      'reason': patch['defect']})

    json.dump({'content_revision': NEW_REV, 'cards': revised,
               'old_to_new_diff': diffs,
               'unchanged_note': '其余 66 张卡保持原文件原口径；本修订只覆盖命中缺陷的 6 张。'
                                 '旧文件不删不改，历史答卷与旧评分不重烧。'},
              open(OUT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('WROTE', OUT, 'revised=%d' % len(revised))
    for d in diffs:
        print('  %s  %s' % (d['case_id'], d['reason'][:60]))


if __name__ == '__main__':
    main()
