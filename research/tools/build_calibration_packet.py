# -*- coding: utf-8 -*-
"""tools/build_calibration_packet.py — 标定包 v1（零生成，全部复用已付费答卷）。

依据 EVALUATION_MASTER_DRAFT_20261025 §5 与 evaluation/JUDGE_MANUAL_V1.md：
- 节点=同一请求位的两个候选答案（甲/乙匿名对），顺序由固定 seed 决定并登记；
- 类别标签由卡片 families / 缺信息标记 机械推导（不是我手写的胜负）；
- 按【故事】划分 dev / holdout_proxy，防止看完复核结果再回头调尺子当"独立"；
- 附少量"改写控制项"（provenance=authored_control，明确标注非模型答卷）用于检验
  评审者能否识别明显 F4 无据断言、能否不误杀正常新提议；
- 密件 SEALED_KEY.json 保存甲/乙→臂、原文 sha、以及我此前的一次判定记录（标 author_biased）。

真实模型调用 = 0；不改任何付费原文与旧评分。
"""
import hashlib
import json
import os
import random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_V1')
SEED = 20261025
MANUAL_VERSION = 'MANUAL-V1.0'
PACKET_VERSION = 'CALIB-V1.0'

DEV_STORIES = {'E007', 'E008', 'E010'}          # 用于标定/调尺子
HOLDOUT_STORIES = {'E009', 'E012'}              # 手册冻结后才看
SOURCES = [
    ('v5', os.path.join(ROOT, 'refs/experiment_protocol_20261023/v5_next_batch_20261025/'
                               'V5_EXTRACTED_OUTPUTS.json'),
     os.path.join(ROOT, 'logs/v5_ctx_live_20261025')),
    ('r2', os.path.join(ROOT, 'refs/experiment_protocol_20261023/r2_next_batch_20261024/'
                               'R2_EXTRACTED_OUTPUTS.json'),
     os.path.join(ROOT, 'logs/r2_ctx_live_20261024')),
]

MISSING_MARK = ('未给', '未详', '未明说', '题面没给', '未在题面')

# 卡片 families → 评审类别（机械映射，可复核）
FAMILY2CAT = [
    (('第三人', '归属', '主体', '意图归属', '不安到她头上', '不安第三方'), '状态与归属'),
    (('暂定', '改期', '时间变更', '状态回退', '状态推进', '真正落定', '事项', '纠正', '更新',
      '重开', '条件性', '跟随'), '状态与归属'),
    (('事实来源', '事实(不编造)', '不谎称', '不编造', '据画像', '公开事实', '历史一致'), '事实边界'),
    (('调侃', '互损', '暧昧', '反撩', '玩笑', '接梗', '试探', '自我表达', '真实表达',
      '价值观', '回应反问', '给倾向'), '语气与分寸'),
    (('收尾', '不加强钩子', '不过度推进', '不急于', '不施压', '尊重边界', '收回边界',
      '意图关闭', '别劝', '不赌气'), '收尾与停'),
    (('邀约', '推进', '入口', '提议', '协商时间', '应接住', '配合她', '达成', '钩子'), '目标完成'),
]


def category_of(card):
    fams = card.get('families') or []
    amb = (card.get('unresolved_ambiguities') or '') + ' ' + (card.get('contrast_design') or '')
    if any(m in amb for m in MISSING_MARK):
        return '信息不足'
    for names, cat in FAMILY2CAT:
        if any(any(n in f for n in names) for f in fams):
            return cat
    return '表达分歧'


def load_cards(live_dir, ep):
    p = os.path.join(live_dir, 'parent_' + ep, ep + '.copy.json')
    d = json.load(open(p, encoding='utf-8'))
    return {t['case_id']: t for t in d['turns']}, d


def grading_cards():
    out = {}
    for f in ('03_GRADING_CARDS_E004_E006.json', '04_GRADING_CARDS_E007_E012.json'):
        for c in json.load(open(os.path.join(ROOT, 'benchmark', 'dev_expansion_v01', f),
                                encoding='utf-8')):
            out[c['case_id']] = c
    return out


def texts_of(row, keep='both'):
    """返回该行的首选与备选；评审节点只暴露可发送文本，不暴露 analysis/token/评分。"""
    prim = row['primary']
    backs = [s['text'] for s in row['all_texts'] if s['slot'] != 'response.recommended']
    return {'primary': prim, 'backups': backs}


def main():
    gcards = grading_cards()
    rnd = random.Random(SEED)
    nodes, sealed, seq = [], [], 0

    for tag, rows_path, live_dir in SOURCES:
        rows = json.load(open(rows_path, encoding='utf-8'))
        by_case = {}
        for r in rows:
            by_case.setdefault(r['case_id'], {})[r['arm']] = r
        for cid in sorted(by_case):
            pair = by_case[cid]
            if 'parent' not in pair or 'candidate' not in pair:
                continue
            ep = cid.split('_')[0]
            if ep not in DEV_STORIES and ep not in HOLDOUT_STORIES:
                continue
            turns, card_meta = load_cards(live_dir, ep)
            t = turns.get(cid)
            if not t:
                continue
            order = [tt['case_id'] for tt in card_meta['turns']]
            prior = [m for tc in order[:order.index(cid)]
                     for m in turns[tc]['input']['messages']]
            gc = gcards.get(cid, {})
            ti = t['input']
            split = 'dev' if ep in DEV_STORIES else 'holdout_proxy'
            a, b = pair['parent'], pair['candidate']
            swap = rnd.random() < 0.5
            left, right = (a, b) if not swap else (b, a)
            seq += 1
            nid = 'N%03d' % seq
            cat = category_of(gc)
            nodes.append({
                'node_id': nid, 'version': PACKET_VERSION, 'manual': MANUAL_VERSION,
                'split': split, 'story': ep, 'position': cid, 'provenance': 'model_paid',
                'batch': tag,
                'evidence': {
                    'persona_me': card_meta['persona'].get('me'),
                    'persona_her': card_meta['persona'].get('her'),
                    'public_seed': card_meta.get('initial_kb'),
                    'prior_messages': [{'ts': m.get('ts'),
                                        'who': '对方' if m['role'] == 'HER' else '我',
                                        'text': m['text']} for m in prior],
                    'current_window': [{'ts': m.get('ts'),
                                        'who': '对方' if m['role'] == 'HER' else '我',
                                        'text': m['text']} for m in ti['messages']],
                    'advisor_note': ti.get('note'),
                    'note_visibility': '只用来约束本轮怎么说，不是已发出的话',
                    'goal': (ti.get('intent') or {}).get('text')
                            if (ti.get('intent') or {}).get('enabled') else None,
                    'clock': ti.get('clock'),
                    'card_allowed': gc.get('allowed_behaviors') or [],
                    'card_forbidden': gc.get('failure_conditions') or [],
                    'card_ambiguity': gc.get('unresolved_ambiguities'),
                    'card_status': gc.get('review_status'),
                    'card_defect_note': ('该卡已被 audit 标记为口径缺陷；缺陷条款不得当作无据断言的资格证明'
                                         if gc.get('review_status') else None),
                },
                'answers': {'甲': texts_of(left), '乙': texts_of(right)},
            })
            sealed.append({
                'node_id': nid, 'position': cid, 'batch': tag, 'swapped': swap,
                'category': cat,
                '甲': {'arm': left['arm'], 'raw_sha256': left['raw_sha256'],
                       'run_id': left['run_id'], 'program_score10': left['score10'],
                       'veto': left['veto']},
                '乙': {'arm': right['arm'], 'raw_sha256': right['raw_sha256'],
                       'run_id': right['run_id'], 'program_score10': right['score10'],
                       'veto': right['veto']},
            })

    # —— 改写控制项（明确标注非模型答卷；用于检验误杀率与 F4 识别率）
    CONTROLS = [
        ('C01', '语气与分寸', '熟悉朋友互损；当轮目标：接住她的玩笑并创造一起吃饭的机会；尚未约时间。',
         '你要是请吃饭，我可以偷偷给你加练。',
         '谢谢你，我会认真锻炼的。',
         '行，饭我请。加练先欠着，别把吃饭练没了。'),
        ('C02', '事实边界', '同上背景；没有任何订位依据。',
         '你要是请吃饭，我可以偷偷给你加练。',
         '我已经订好今晚七点的位置了。',
         '行，饭我请。加练先欠着，别把吃饭练没了。'),
        ('C03', '信息不足', '题面从未给出我和小林怎么认识（后续剧情另有说法，本轮不可用）。',
         '对了 你上次说认识小林是通过读书会在认识的？',
         '是呀，就那次，你还记得谁带的我？',
         '不是读书会吧？我记着不是这个，你从哪儿听来的说法？'),
        ('C04', '收尾与停', '她明说明天早起要先睡；当前目标：自然收尾。',
         '先睡了 明天还得早起',
         '好，睡吧，明天早起别熬。',
         '这么早睡？那梦里继续聊，记得想我。'),
        ('C05', '事实边界', '她问某场演出的音量；输入只给了"她讨厌太吵"这条偏好。',
         '那场会不会很吵',
         '我还没查这场的现场反馈，按你不喜欢轰耳朵的偏好，我先挑评价里说收着的几场给你看。',
         '不吵，放心，那乐队一贯收着演。'),
        ('C06', '状态与归属', '她转述同事对我的评价；第三人评价不等于她本人的态度。',
         '我同事老说你客气 其实不用那么讲究',
         '那我以后不讲究，人先到就行。',
         '我哪里客气了，那是他们还没跟我熟。'),
    ]
    for nid, cat, ctx, her, jia, yi in CONTROLS:
        seq += 1
        nodes.append({
            'node_id': 'N%03d' % seq, 'version': PACKET_VERSION, 'manual': MANUAL_VERSION,
            'split': 'dev' if nid in ('C01', 'C02', 'C03') else 'holdout_proxy',
            'story': 'CONTROL', 'position': nid, 'provenance': 'authored_control',
            'evidence': {
                'persona_me': None, 'persona_her': None, 'public_seed': None,
                'prior_messages': [], 'current_window': [{'ts': None, 'who': '对方', 'text': her}],
                'advisor_note': ctx, 'note_visibility': '只用来约束本轮怎么说，不是已发出的话',
                'goal': None, 'clock': None, 'card_allowed': [], 'card_forbidden': [],
                'card_ambiguity': None, 'card_status': None,
                'card_defect_note': '控制项为人工改写，不是模型答卷，也不属于任何题库故事。'},
            'answers': {'甲': {'primary': jia, 'backups': []},
                        '乙': {'primary': yi, 'backups': []}},
        })
        sealed.append({'node_id': 'N%03d' % seq, 'position': nid, 'batch': 'control',
                       'swapped': False, 'category': cat,
                       '甲': {'arm': 'authored_control_A'}, '乙': {'arm': 'authored_control_B'}})

    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, 'nodes.jsonl'), 'w', encoding='utf-8') as f:
        for n in nodes:
            f.write(json.dumps(n, ensure_ascii=False) + '\n')
    key = {'packet_version': PACKET_VERSION, 'manual_version': MANUAL_VERSION,
           'seed': SEED, 'real_model_calls': 0,
           'warning': '本文件是密件：评审者不得读取。含臂身份、原文哈希与作者既有判定。',
           'nodes': sealed}
    json.dump(key, open(os.path.join(OUT, 'SEALED_KEY.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)

    cat_of = {s['node_id']: s.get('category') for s in sealed}
    # —— 用户口味标定表（只列匿名成对首选，不含类别与臂身份，不含我的判词）
    pick = [n for n in nodes if n['provenance'] == 'model_paid'
            and cat_of[n['node_id']] in ('语气与分寸', '收尾与停', '目标完成', '表达分歧')]
    sheet = ['# 用户口味标定表 v1（%d 组，只请你裁"你更愿意发哪个"）' % min(18, len(pick)), '',
             '版本 %s / 手册 %s。2026-10-25。全部来自已付费真实答卷，两臂身份已隐去、左右顺序随机。'
             % (PACKET_VERSION, MANUAL_VERSION),
             '你只需要在每组里选：甲 / 乙 / 都行 / 都不想 / 说不清。',
             '**不需要你核事实、不需要你评时间线、不需要你逐句看备选**——那些由证据核对负责。',
             '你的选择会当作口味锚点；AI 只能做经校准的预测，不能代投。', '']
    for i, n in enumerate(pick[:18], 1):
        e = n['evidence']
        sheet += ['---', '', '## %d. %s（%s）' % (i, n['position'], n['story']), '',
                  '她说/现场：' + ' ｜ '.join('%s：%s' % (m['who'], m['text'])
                                          for m in e['current_window']), '']
        if e.get('goal'):
            sheet += ['你这轮想做的事：%s' % e['goal'], '']
        if e.get('advisor_note'):
            sheet += ['背景提醒（不发给她的）：%s' % e['advisor_note'], '']
        sheet += ['', '- **甲**：%s' % n['answers']['甲']['primary'],
                  '- **乙**：%s' % n['answers']['乙']['primary'], '',
                  '你的选择：＿＿（甲 / 乙 / 都行 / 都不想 / 说不清）　'
                  '改哪句都不如直接说"这词我不会用"：＿＿＿＿', '']
    open(os.path.join(ROOT, 'evaluation', 'USER_CALIBRATION_SHEET_V1.md'),
         'w', encoding='utf-8').write('\n'.join(sheet))

    stat = {}
    for n in nodes:
        stat.setdefault(n['split'], {'nodes': 0, 'answers': 0, 'cats': {}})
        stat[n['split']]['nodes'] += 1
        stat[n['split']]['answers'] += 2
        c = stat[n['split']]['cats']
        k = cat_of[n['node_id']]
        c[k] = c.get(k, 0) + 1
    json.dump({'sealed': True, 'categories': cat_of},
              open(os.path.join(OUT, 'SEALED_CATEGORIES.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('nodes=%d  splits=%s' % (len(nodes), json.dumps(stat, ensure_ascii=False)))
    print('sealed=%d  seed=%d  packet=%s manual=%s real_model_calls=0'
          % (len(sealed), SEED, PACKET_VERSION, MANUAL_VERSION))
    print('user sheet items=%d' % min(18, len(pick)))
    print('WROTE', OUT)


if __name__ == '__main__':
    main()
