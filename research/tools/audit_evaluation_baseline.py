# -*- coding: utf-8 -*-
"""tools/audit_evaluation_baseline.py — 评估基准工作包：卡片一致性与任务覆盖审计（零生成）。

产出 evaluation/CARD_CONFLICTS_V1.json 与终端可读表。只做静态核对+我逐条复核需要的原文，
不重跑任何模型、不改任何已付费证据、不改卡片（修法由我在文档里提案，等你审定后再落地）。

规则（宁可少报，命中必须能被人对着卡片原文复核）：
 R1 无据具体断言许可：allowed 允许把【偏好/画像/职业】推成【现场/音量/设施/路线/时长/日期/既成约定】级别的事实。
 R2 缺信息位未约束"自信否定"：题面本身没给事实，failure 只禁"编造肯定"，没写"无依据的确定否定同样缺据"。
 R3 note 与 allowed 相互拉扯：同一位一边要求"别当成既成事实"，一边允许断言该事实。
 R4 归属口径混浊：把"第三人评价的对象"与"转述的说话人"写成同一件事（判分时无法一致）。
 R5 划分缺陷：split 全为 dev_exposed → 没有 holdout/独立集，"已看过的题改名当未见"随时可能发生。
 R6 评审未冻结：review_status 仍是 authored_*_pending_review。
"""
import collections
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CARDS = [os.path.join('benchmark', 'dev_expansion_v01', f) for f in
         ('03_GRADING_CARDS_E004_E006.json', '04_GRADING_CARDS_E007_E012.json')]
OUT_DIR = os.path.join(ROOT, 'evaluation')

# —— 覆盖轴（对应总流程草案 §6）
AXIS_GOAL = {
    '日常承接': ['话题承接', '情绪承接', '精准承接', '精准承接新信息', '历史承接', '就事论事',
              '相关回忆回扣', '相关锚点回扣(预设知识+当轮消息)', '相关回忆(被激活)', '旧事回扣(正向)',
              '主动回访旧事(正向记忆)', '正向回扣致谢', '见面后日常', '来倾诉宜陪不宜冷', '低信息处理'],
    '转场/钩子': ['主动入口(轻)', '正向推进(新邀约)', '鼓励或提议(不谎称已约)', '自然推进', '话题枯竭'],
    '调侃反撩': ['调侃分寸', '互损基调', '玩笑不谎称已约定', '轻度暧昧分寸', '正向推进(反撩)', '接梗可提新选择',
              '接住试探', '互损'],
    '邀约/推进': ['积极接受或协商时间', '直接发起应接住', '意图启用→主动推进(正向)', '事项状态推进',
              '意图达成(二人)', '她再确认→正向推进(轻松)', '配合她的主导'],
    '深聊/价值': ['价值观深入', '真实表达', '自我表达(不迎合)', '回应反问', '给倾向不代拍板'],
    '收尾/停': ['自然收尾', '自然收尾(她要睡)', '不加强钩子', '收尾轻松', '收尾不逼定', '不过度推进',
             '不施压', '意图关闭不催', '不急于推进', '不急于解决', '尊重边界', '收回边界', '不赌气不逼'],
    '纠正/更新': ['补充信息纠正', '更新认知', '时间变更(不阴阳)', '改期(不阴阳)', '多次改期(稳定)',
              '状态回退', '暂定状态', '暂定状态(不写成既成事实)', '条件性答复', '状态推进到确定',
              '真正落定', '事项完成', '重开新事项', '诚实认领用力过猛', '还主动权'],
    '归属/主体': ['第三人主体', '第三人主体(妹妹)', '归属澄清(第三人评价)', '归属澄清(转述)',
              '不安到她头上', '第三人转述(不安ME头上)', '不安第三方进二人局', '主体明确(她本人)',
              '主体=她妹借她口', '第三人眼光', '意图归属', '意图澄清(不过度解读)', '不过度解读'],
    '不谎称既成': ['不谎称既成事实', '事实来源(不编造)', '事实(不编造)', '不编造经历', '据画像种子事实',
               '公开事实可用', '可公开事实(位置/日程)', '历史一致性', '历史一致', '事实'],
    '媒体/其他': ['按文字分享评', '不回须说明', '求助承接', '退路≠拒绝', '不邀功不表白', '不过度承诺',
              '不越界', '不越权推进', '不被旧目标污染', '跟随意图切换', '跟随切换', '跟随转向',
              '跟随话题切换', '不强行回扣无关', '相关不跑题', '尊重明确意图', '尊重能动性', '不全揽',
              '不得寸进尺', '具体可执行建议', '不空泛', '别劝', '不阴阳', '情绪稳定', '不自伤',
              '情绪', '正向确认', '正面反馈+开放', '日常陪伴意图', '意图切换→跟随(正向)',
              '私下目标可转行动', '不谎称她已同意', '有感受即可', '关键区分', '不擅自组局',
              '据偏好'],
}

CONCRETE = ['现场', '音量', '不吵', '吵', '设施', '路线', '时长', '场地', '阵容', '订好', '订了',
            '已买', '位子', '座位', '店', '场馆']
PREFERENCE_DERIVE = ['偏好', '她喜欢', '据 her', '画像', '职业', '身份', '喜欢']


def card_family_axes(card):
    axes = set()
    for fam in card.get('families', []):
        for ax, names in AXIS_GOAL.items():
            if fam in names:
                axes.add(ax)
    return sorted(axes)


def main():
    cards = []
    for p in CARDS:
        cards.extend(json.load(open(os.path.join(ROOT, p), encoding='utf-8')))

    flags = []
    for c in cards:
        allowed = ' ｜ '.join(c.get('allowed_behaviors') or [])
        fail = ' ｜ '.join(c.get('failure_conditions') or [])
        note = ''
        for ev in c.get('evidence') or []:
            if ev.get('field') == 'input.note':
                note = ev.get('text') or ''
        amb = (c.get('unresolved_ambiguities') or '') + ' ' + (c.get('contrast_design') or '')

        # R1 偏好→具体可核验事实的许可
        if any(k in allowed for k in CONCRETE) and any(k in allowed for k in PREFERENCE_DERIVE):
            flags.append(('R1', c['case_id'], allowed, note, amb.strip()))
        elif '确认' in allowed and any(k in allowed for k in CONCRETE):
            flags.append(('R1', c['case_id'], allowed, note, amb.strip()))

        # R2 题面缺事实，却只禁"编造肯定"
        missing_fact = any(k in amb for k in ('题面未给', '未给真实', '题面没给', '未详', '未明说'))
        if missing_fact and '不确定' not in (allowed + fail) and '澄清' not in (allowed + fail):
            flags.append(('R2', c['case_id'], allowed, note, amb.strip()))
        if missing_fact and '编造' in fail and '确定否定' not in fail and '无依据' not in fail:
            flags.append(('R2b', c['case_id'], allowed, note, fail))

        # R3 note 禁止当既成事实，allowed 却允许断言同一事实
        if any(k in note for k in ('不要假装', '别把话说成已定死', '不谎称', '别当成')):
            if any(k in allowed for k in ('确认', '据')) and any(k in allowed for k in CONCRETE):
                flags.append(('R3', c['case_id'], allowed, note, amb.strip()))

        # R4 归属口径：failure 把"安到头上"的对象写死，而评价对象其实另有其人
        if '转述' in allowed + fail and '不安' in (note + fail).replace('别被安到自己头上', '不安'):
            if ('安到' in fail and '安到' in note and fail != note
                    and 'ME' in fail and 'ME' not in note):
                flags.append(('R4', c['case_id'], allowed, note, fail))

    # R5 / R6 全局缺陷（不是某张卡，而是数据集）
    splits = collections.Counter(c.get('split') for c in cards)
    statuses = collections.Counter(c.get('review_status') for c in cards)
    eps = collections.Counter(c['episode_id'] for c in cards)

    # 覆盖矩阵：轴 × 故事
    cov = {}
    for c in cards:
        for ax in card_family_axes(c):
            cov.setdefault(ax, collections.Counter())[c['episode_id']] += 1
    uncategorized = sorted({f for c in cards for f in c.get('families', [])
                            if not any(f in v for v in AXIS_GOAL.values())})

    out = {
        'generated_by': 'tools/audit_evaluation_baseline.py',
        'real_model_calls': 0,
        'cards_total': len(cards), 'cards_by_episode': dict(eps),
        'splits': dict(splits), 'review_status': dict(statuses),
        'headline_defects': {
            'no_holdout_split': all(k == 'dev_exposed' for k in splits),
            'nothing_frozen': all(str(v).endswith('pending_review') for v in statuses),
            'families_not_in_axis_map': uncategorized,
        },
        'flagged_cards': [{'rule': r, 'case_id': cid, 'allowed': a, 'note': n, 'extra': e}
                          for r, cid, a, n, e in flags],
        'coverage_axis_by_episode': {ax: dict(cnt) for ax, cnt in sorted(cov.items())},
        'axis_totals': {ax: sum(cnt.values()) for ax, cnt in sorted(cov.items())},
    }
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, 'CARD_CONFLICTS_V1.json')
    json.dump(out, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('WROTE', path)
    print('cards=%d splits=%s status=%s' % (len(cards), dict(splits), dict(statuses)))
    print('uncategorized families (%d): %s' % (len(uncategorized), uncategorized))
    print('\n== 命中卡片 ==')
    for f in flags:
        print('- %s %s\n    allowed: %s\n    note: %s\n    extra: %s' % (f[0], f[1], f[2], f[3], f[4]))
    print('\n== 轴 × 故事 覆盖 ==')
    for ax in sorted(cov):
        print('%-12s total=%2d  %s' % (ax, sum(cov[ax].values()), dict(sorted(cov[ax].items()))))


if __name__ == '__main__':
    main()
