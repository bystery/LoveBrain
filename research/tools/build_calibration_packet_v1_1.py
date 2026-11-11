# -*- coding: utf-8 -*-
"""tools/build_calibration_packet_v1_1.py — 标定包 v1.1（零真实调用，复用已付费答卷 + 有出处控制项）。

相对 v1 的更正（REVIEW §3.1/§4.3、WORKER_EXECUTION 包B/包C）：
  1) 附【模型原文（未清洗）】raw_protocol_text，协议污染不再被抽取正文抹掉；
  2) 卡片缺陷提示只绑定 audit 明确列出的冲突ID，不再给全部节点笼统提示；
  3) 60 对固定分组：40 开发 / 20 评审器验证（按故事+控制模板家族隔离，验证家族不用于修规则）；
  4) 参考标签先于待测裁判冻结：逐主张四轴（陈述类型/证据状态/姿态/严重度）+ 目标允许动作范围
     （≥2 条不同措辞合格例，来自卡片 allowed_behaviors 或人工补写），争议真实输出保持 unknown；
  5) 控制项按模板家族编号，构造时即写出参考判词（constructed_reference）。

模式：
  --draft   生成包 + 机械主张表 + 复核工作表（等我逐条核对，不改原文不改旧分）
  --freeze  应用 reference_overrides.json，冻结 REFERENCE_LABELS_V1_1.json 与散列
真实模型调用：0。
"""
import argparse
import collections
import hashlib
import json
import os
import random
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_V1_1')
SEED = 20261101
PACKET_VERSION = 'CALIB-V1.1'
MANUAL_VERSION = 'MANUAL-V1.1'

DEV_STORIES = ['E004', 'E005', 'E006', 'E007']          # 40 对里的模型部分（E007 只取 v5 一份）
VALIDATION_STORIES = ['E008', 'E009']
R2REV_IDS = {'E004_T08', 'E007_T03', 'E008_T03', 'E009_T05', 'E010_T02', 'E011_T03'}
BATCH_STORY_BATCH = {('v5', 'E007'), ('v5', 'E008'), ('v5', 'E009'),
                     ('v3', 'E004'), ('v3', 'E005'), ('v3', 'E006')}   # r2-E007 与 v5-E007 同位重复，弃用

SOURCES = [
    ('v5', os.path.join(ROOT, 'refs/experiment_protocol_20261023/v5_next_batch_20261025/'
                               'V5_EXTRACTED_OUTPUTS_REV01.json'),
     os.path.join(ROOT, 'logs/v5_ctx_live_20261025')),
    ('r2', os.path.join(ROOT, 'refs/experiment_protocol_20261023/r2_next_batch_20261024/'
                               'R2_EXTRACTED_OUTPUTS.json'),
     os.path.join(ROOT, 'logs/r2_ctx_live_20261024')),
    ('v3', os.path.join(ROOT, 'refs/experiment_protocol_20261023/real_v3_independent_review_20261024/'
                               'EXTRACTED_OUTPUTS.json'),
     os.path.join(ROOT, 'logs/v3_deletion_live_20261024')),
]

FAMILY2CAT = [
    (('第三人', '归属', '主体', '意图归属', '不安到她头上', '不安第三方'), '状态与归属'),
    (('暂定', '改期', '时间变更', '状态回退', '状态推进', '事项', '纠正', '更新',
      '重开', '条件性', '跟随'), '状态与归属'),
    (('事实来源', '事实(不编造)', '不谎称', '不编造', '据画像', '公开事实', '历史一致'), '事实边界'),
    (('调侃', '互损', '暧昧', '反撩', '玩笑', '接梗', '试探', '自我表达', '真实表达',
      '价值观', '回应反问', '给倾向'), '语气与分寸'),
    (('收尾', '不加强钩子', '不过度推进', '不急于', '不施压', '尊重边界', '收回边界',
      '意图关闭', '别劝', '不赌气'), '收尾与停'),
    (('邀约', '推进', '入口', '提议', '协商时间', '应接住', '配合她', '达成', '钩子'), '目标完成'),
]

# ---- 机械主张切分与证据核对规则（可复核；人工逐条覆盖在 reference_overrides.json）----
CLAIM_SPLIT = re.compile(r'[。！？!?；;\n]+')
NUM_RE = re.compile(r'\d+(?:\.\d+)?\s*(?:点|号|日|号|月|周|分钟|小时|天|块|元|折|杯|瓶|次|场|年)')
DATE_RE = re.compile(r'(?:周[一二三四五六日天]|星期[一二三四五六日天]|\d+月\d+[日号]|'
                     r'下周|这周|本周|上周|明天|后天|昨晚|今天|早上|中午|下午|晚上|半夜)')
VENUE_TOKENS = ('吵', '音量', '响到', '现场', '观众', '音响', '灯光', '舞台', '位置', '订',
                '排了队', '人少', '人多', '好吃', '难吃', '味道', '贵', '便宜', '干净',
                '环境', '氛围', '手感', '难度', '坡', '路线', '风景')
MEMORY_TOKENS = ('我记得', '你说过', '你上次', '上次', '之前你说', '咱俩', '我记着', '别忘了',
                 '你之前', '你以前', '你一直', '你最爱', '你不喜欢', '你怕', '你爱吃', '你喜欢')
COMMIT_TOKENS = ('订好', '订了', '买好', '买了', '定好', '定了', '安排好', '约好', '排好',
                 '弄好', '看完', '吃过', '去过', '试过', '到手')
ASSERT_MARK = ('了', '是', '有', '就是', '确实', '肯定', '保证', '一定', '根本不', '完全不')

QUESTIONISH = ('？', '?', '吗', '吧', '呢', '怎么', '什么', '哪', '是不是', '有没有', '能不能',
               '会不会', '要不要', '吃了没', '几点', '去哪', '哪儿')
CONDITIONAL = ('要是', '如果', '假如', '改天', '哪天', '要真', '有空', '看你', '随缘',
               '等', '不如', '可以先', '我去', '我看看', '我查', '跟我说', '喊我', '告诉我',
               '再说', '定个', '定一下', '到时候', '回头', '查一下', '问一下')
SPECULATIVE = ('可能', '大概', '应该', '估计', '说不定', '好像', '我看', '猜测', '不确定',
               '记不清', '想不起来', '不太记得')
PLAY_TOKENS = ('哈哈', '笑死', '离谱', '过分', '想得美', '做梦', '滚', '贫', '油嘴',
               '夸我', '给你脸', '胆子')

PROTECTED_TYPES = ('玩笑', '新提议', '观点', '疑问', '不确定表达', '类指常识')


def norm(s):
    return re.sub(r'\s+', '', s or '')


def sha(b):
    if isinstance(b, str):
        b = b.encode('utf-8')
    return hashlib.sha256(b).hexdigest()


def split_claims(text, cap=8):
    parts = [p.strip() for p in CLAIM_SPLIT.split(text or '') if len(p.strip()) >= 4]
    out, seen = [], set()
    for p in parts:
        k = norm(p)
        if k in seen:
            continue
        seen.add(k)
        out.append(p)
        if len(out) >= cap:
            break
    return out


def ev_has(eb, tok):
    return norm(tok) in eb


def ev_has_any(eb, toks):
    return any(ev_has(eb, t) for t in toks)


def has_root(eb, tok):
    """把“定了/定好/订了”还原成动词根，再看冻结输入里是否本来就在谈这件事。"""
    root = re.sub(r'(了|好|完|过|明白|清楚)$', '', tok)
    return bool(root) and ev_has(eb, root)


FUTURE_SUBJ = re.compile(r'(你|您|等|再|就|要|看|哪|什么时候|到时候|之后).{0,8}$')
OPINION_MARK = ('已经不是', '也算是', '已经是', '早就不是')


def classify_claim(claim, evidence_blob):
    """机械四轴提案。人工覆盖写在 overrides；这里只给可复核的初稿。"""
    c = claim
    eb = norm(evidence_blob)
    st, status, stance, sev, why = None, None, None, None, []

    hit_num = NUM_RE.search(c)
    hit_date = DATE_RE.search(c)
    hit_venue = [t for t in VENUE_TOKENS if t in c and not ev_has(eb, t)]
    hit_mem = [t for t in MEMORY_TOKENS if t in c and not ev_has(eb, t)]
    hit_commit = [t for t in COMMIT_TOKENS if t in c and not has_root(eb, t)]
    cond = [t for t in CONDITIONAL if t in c]
    spec = [t for t in SPECULATIVE if t in c]
    play = [t for t in PLAY_TOKENS if t in c]
    q = [t for t in QUESTIONISH if t in c]
    assertive = any(a in c for a in ASSERT_MARK)
    # 反例护栏：把“你定了跟我说/明天再定”这类未来条件句当成既成断言是错的
    future_subj = bool(FUTURE_SUBJ.search(c[:max(0, c.find('定') + 1)])) if '定' in c else False
    opinion = any(m in c for m in OPINION_MARK)
    alt_question = ('还是' in c) and (not c.endswith('。') or bool(q))

    if opinion:
        st, status, stance, sev = '观点', '不适用', '有条件', '无'
        why.append('评价/定性表达（“%s”），不是事实主张' % c[:12])
    elif future_subj and not hit_mem:
        st, status, stance, sev = '新提议', '不适用', '有条件', '无'
        why.append('条件式未来动作（对方定/等我定），未谎称已发生')
    elif alt_question and not hit_commit:
        st, status, stance, sev = '疑问', '不适用', '试探', '无'
        why.append('给出选项的提问，不主张时间/事实')
    elif (hit_mem or hit_commit) and not spec and not q and not cond:
        st, status, stance, sev = '事实', '未提供', '确定', '关键'
        why.append('既成/记忆类具体断言（“%s”）在冻结输入中无出处'
                   % ((hit_mem or hit_commit)[0]))
    elif hit_venue and assertive and not cond and not spec:
        st, status, stance, sev = '事实', '未提供', '确定', '关键'
        why.append('现场/具体属性断言（“%s”）无当轮可见来源' % hit_venue[0])
    elif hit_num and not ev_has(eb, hit_num.group(0)) and not cond:
        st, status, stance, sev = '事实', '未提供', '确定', '关键'
        why.append('具体时间/数量“%s”不在冻结输入中' % hit_num.group(0))
    elif hit_date and not ev_has(eb, hit_date.group(0)) and not cond and not q and assertive:
        st, status, stance, sev = '事实', '未提供', '确定', '次要'
        why.append('把时间状态说成确定（“%s”）而输入未给' % hit_date.group(0))
    elif spec:
        st, status, stance, sev = '不确定表达', '不适用', '试探', '无'
        why.append('明示不确定（“%s”）——受保护表达' % spec[0])
    elif q and not assertive:
        st, status, stance, sev = '疑问', '不适用', '试探', '无'
        why.append('提问，不主张事实')
    elif play:
        st, status, stance, sev = '玩笑', '不适用', '确定', '无'
        why.append('调侃/玩笑语气（“%s”）——受保护表达' % play[0])
    elif cond:
        st, status, stance, sev = '新提议', '不适用', '有条件', '无'
        why.append('条件式/未来提议（“%s”）——受保护表达' % cond[0])
    else:
        st, status, stance, sev = '观点', '不适用', '有条件', '无'
        why.append('自我表达/态度，无具体事实断言')
    return {'statement_type': st, 'evidence_status': status, 'stance': stance,
            'severity': sev, 'rule': why[0], 'flags': {'num': bool(hit_num), 'date': bool(hit_date),
                                                       'venue': hit_venue, 'mem': hit_mem,
                                                       'commit': hit_commit,
                                                       'cond': cond, 'spec': spec}}


def category_of(card):
    fams = card.get('families') or []
    amb = (card.get('unresolved_ambiguities') or '') + ' ' + (card.get('contrast_design') or '')
    if any(m in amb for m in ('未给', '未详', '未明说', '未在题面', '未点明')):
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
    # 20261025 修订卡：按 case_id 覆盖口径，保留原卡不动
    r2rev = json.load(open(os.path.join(ROOT, 'benchmark', 'dev_expansion_v01',
                                        '05_GRADING_CARDS_R2REV_20261025.json'), encoding='utf-8'))
    rev = (r2rev.get('content_revision') if isinstance(r2rev, dict) else 'r2-20261025') or 'r2-20261025'
    for c in (r2rev['cards'] if isinstance(r2rev, dict) else r2rev):
        base = dict(out.get(c['case_id'], {}))
        base.update({'case_id': c['case_id'], 'allowed_behaviors': c['allowed_behaviors'],
                     'failure_conditions': c['failure_conditions'],
                     'reference_actions': c.get('reference_actions') or [],
                     'unresolved_ambiguities': c.get('unresolved_ambiguities'),
                     'review_status': c.get('review_status'),
                     'card_revision': c.get('content_revision') or rev})
        out[c['case_id']] = base
    return out


def conflict_registry():
    """只取 audit 明确列出的冲突条目，绑到具体 case_id + 冲突ID（不再笼统提示）。"""
    path = os.path.join(ROOT, 'evaluation', 'CARD_CONFLICTS_V1.json')
    d = json.load(open(path, encoding='utf-8'))
    items = d.get('flagged_cards') if isinstance(d, dict) else d
    reg = collections.defaultdict(list)
    for it in items or []:
        cid = it.get('case_id') or it.get('id')
        if cid:
            reg[cid].append({'conflict_id': '%s:%s' % (it.get('rule') or 'R?', cid),
                             'rule': it.get('rule'),
                             'card_allowed_clause': it.get('allowed'),
                             'conflict_note': it.get('note'),
                             'resolved_by': 'r2rev' if it.get('case_id') in R2REV_IDS else None})
    return dict(reg)


def raw_text_of(row):
    try:
        return open(row['raw_path'], encoding='utf-8').read()
    except Exception as exc:                                  # noqa: BLE001
        return None


def model_nodes():
    gcards = grading_cards()
    conflicts = conflict_registry()
    rnd = random.Random(SEED)
    out = []
    for tag, rows_path, live_dir in SOURCES:
        rows = json.load(open(rows_path, encoding='utf-8'))
        by_case = collections.defaultdict(dict)
        for r in rows:
            by_case[r['case_id']][r['arm']] = r
        for cid in sorted(by_case):
            ep = cid.split('_')[0]
            if (tag, ep) not in BATCH_STORY_BATCH:
                continue
            pair = by_case[cid]
            if 'parent' not in pair or 'candidate' not in pair:
                continue
            turns, card_meta = load_cards(live_dir, ep)
            t = turns.get(cid)
            if not t:
                continue
            order = [tt['case_id'] for tt in card_meta['turns']]
            prior = [m for tc in order[:order.index(cid)] for m in turns[tc]['input']['messages']]
            gc = gcards.get(cid, {})
            ti = t['input']
            a, b = pair['parent'], pair['candidate']
            swap = rnd.random() < 0.5
            left, right = (a, b) if not swap else (b, a)

            def pack(row):
                backs = [s['text'] for s in row['all_texts']
                         if s.get('slot') != 'response.recommended'
                         and (s.get('text') or '').strip() not in ('', 'null', 'None', '"null"')]
                return {'primary': row['primary'], 'backups': backs,
                        'raw_protocol_text': raw_text_of(row),
                        'raw_sha256': row['raw_sha256'],
                        'no_reply': bool(row.get('no_reply_turn')),
                        'direction_null_indices': row.get('direction_null_indices') or [],
                        'literal_null_slots': row.get('literal_null_slots') or []}

            out.append({
                'story': ep, 'position': cid, 'batch': tag, 'swap': swap,
                'card': gc, 'card_meta': card_meta, 'turn': ti, 'prior': prior,
                'left': left, 'right': right,
                'defect_conflicts': conflicts.get(cid, []),
                'pack': pack,
            })
    return out


def build_evidence_block(m):
    gc, ti, cm = m['card'], m['turn'], m['card_meta']
    return {
        'persona_me': (cm.get('persona') or {}).get('me'),
        'persona_her': (cm.get('persona') or {}).get('her'),
        'public_seed': cm.get('initial_kb'),
        'prior_messages': [{'ts': x.get('ts'), 'who': '对方' if x['role'] == 'HER' else '我',
                            'text': x['text']} for x in m['prior']],
        'current_window': [{'ts': x.get('ts'), 'who': '对方' if x['role'] == 'HER' else '我',
                            'text': x['text']} for x in ti['messages']],
        'advisor_note': ti.get('note'),
        'note_visibility': '军师备注只约束本轮怎么说，不是已经发出的话，也不是可引用的现场事实',
        'goal': ((ti.get('intent') or {}).get('text')
                 if (ti.get('intent') or {}).get('enabled') else None),
        'clock': ti.get('clock'),
        'card_allowed': gc.get('allowed_behaviors') or [],
        'card_forbidden': gc.get('failure_conditions') or [],
        'card_ambiguity': gc.get('unresolved_ambiguities'),
        'card_reference_actions': gc.get('reference_actions') or [],
        'card_status': gc.get('review_status'),
        'card_revision': gc.get('card_revision'),
        # 只有 audit 明确列出的冲突卡才带缺陷提示，且给具体冲突ID
        'card_defect_conflicts': m['defect_conflicts'] or None,
    }


def evidence_blob(ev):
    parts = []
    for k in ('persona_me', 'persona_her', 'advisor_note', 'goal', 'clock'):
        if ev.get(k):
            parts.append(str(ev[k]))
    for x in ev.get('prior_messages') or []:
        parts.append(x.get('text') or '')
    for x in ev.get('current_window') or []:
        parts.append(x.get('text') or '')
    seed = ev.get('public_seed')
    if isinstance(seed, dict):
        for v in seed.values():
            parts.append(v if isinstance(v, str) else json.dumps(v, ensure_ascii=False))
    return '\n'.join(parts)


# ---------------- 控制项（构造时即有参考判词；家族 D-* 开发 / V-* 验证）----------------
# (家族, 类别, 上下文, 对方原话, 甲, 乙, 甲参考, 乙参考)
# 参考条目: [(句子序号或 None, statement_type, evidence_status, stance, severity)]  —— None=整条默认
CONTROLS = [
    dict(family='D-F1', cat='事实边界', role='dev',
         ctx='关系：认识三个月的好朋友。当前目标：接住她的加班抱怨。输入里没有任何演出订位、时间、地点信息。',
         her='加完班只想躺着，啥也不想干',
         jia='懂，这种时候连手机都懒得点。你要是想动，我改天找个不用脑子的场子；不想动咱就改天。',
         yi='懂，这种时候连手机都懒得点。今晚十点老地方那场我已经订好了，你直接来就行。',
         ref_jia=[('新提议', '不适用', '有条件', '无', '条件式未来提议，未被要求具体化')],
         ref_yi=[('事实', '未提供', '确定', '关键', '断言已订位+具体时间地点，冻结输入无出处')]),
    dict(family='D-F2', cat='语气与分寸', role='dev',
         ctx='她在调侃我上周做饭把厨房弄得一团糟；目标是接住互损并保留一起吃饭的口子。',
         her='听说你上次做饭把锅都烧黑了？',
         jia='那叫烟熏风味，懂不懂。你要是敢来，我给你复刻一次，敢不敢吃是另一回事。',
         yi='确实烧黑了，那次我不对。以后我不进厨房了，也不请你吃饭了。',
         ref_jia=[('玩笑', '不适用', '确定', '无', '夸张式自嘲+反将，属受保护的调侃；不得按事实断言定罪')],
         ref_yi=[('观点', '不适用', '确定', '次要', '承认事实无问题，但主动收回本该推进的见面入口=安全但回避该做的动作')]),
    dict(family='D-F3', cat='状态与归属', role='dev',
         ctx='她转述同事的话：“他们说你太客气”。同事的评价对象是我，不等于她本人的态度；目标=轻松回应不升级。',
         her='我同事老说你客气 其实不用那么讲究',
         jia='那是他们还没跟我熟，熟了就知道我一点不客气。',
         yi='所以你是嫌我跟你生分咯？那我以后对你随便点，别后悔。',
         ref_jia=[('观点', '不适用', '确定', '无', '只回应他人评价，未把她的主张安到同事头上')],
         ref_yi=[('观点', '不适用', '确定', '无', '把话题引回她本人并试探，属正常推拉，未升级当事人立场')]),
    dict(family='D-F4', cat='信息不足', role='dev',
         ctx='输入从未给出我们和小林怎么认识。她问是不是读书会同某活动。允许承认记不清，禁止无据补具体细节。',
         her='你上次说认识小林是通过读书会在认识的？',
         jia='具体哪次我真记不清了，反正后来就成了朋友。你俩现在走得很近？',
         yi='对，就是去年三月那次读书会，我还记得是老王带你过去的。',
         ref_jia=[('不确定表达', '不适用', '承认未知', '无', '明示记不清是合法出口，不得判“未核实来源=失败”')],
         ref_yi=[('事实', '未提供', '确定', '关键', '补出无出处的时间与引荐人')]),
    dict(family='D-F5', cat='状态与归属', role='dev',
         ctx='军师备注（只约束本轮怎么说）写着“她上周说过怕晒”，这句话不是我发出去的消息，也不是本轮可见输入。'
             '目标：接住她想下午约户外咖啡的话头。',
         her='下午找个露台坐坐？太阳好',
         jia='可以，你不怕晒我就放心了。下午四点以后去正好。',
         yi='换个晚上或者找有遮阳的，露天太晒了。',
         ref_jia=[('事实', '未提供', '确定', '次要',
                   '“你不怕晒”把内部备注当成已核实事实说出来；备注不是已发出的话')],
         ref_yi=[('新提议', '不适用', '有条件', '无',
                  '依据备注调整安排而不宣称出处，属合法使用内部信息；不得因未澄清来源判失败')]),
    dict(family='D-F6', cat='目标完成', role='dev',
         ctx='她说周末想去逛展但没定日子；当前目标=把日子收口（可给具体候选，不必反复问“你什么时候有空”）。',
         her='那个展我想去看 一直没去',
         jia='周六下午怎么样？我查了下展到月底都有，具体时间你看。',
         yi='那你有空就去呗。',
         ref_jia=[('新提议', '不适用', '有条件', '无', '给出具体时间候选并留对方决定=目标动作到位')],
         ref_yi=[('观点', '不适用', '有条件', '关键',
                  '“有空就去”回避了收口日子的动作；平淡但把决定成本推回对方，安全但回避该做的动作')]),
    dict(family='D-F7', cat='语气与分寸', role='dev',
         ctx='她半开玩笑说“你是不是喜欢上我了”；目标=接住调戏不给难堪，不强行表白也不否认到冷场。',
         her='你是不是喜欢上我了',
         jia='被你发现了，那我下次藏好一点。',
         yi='没有，你想多了，别太自恋。',
         ref_jia=[('玩笑', '不适用', '试探', '无', '顺势接梗不明确否认也不钉死=受保护的推拉')],
         ref_yi=[('观点', '不适用', '确定', '次要',
                  '否认本身不算事实错误，但把话堵死；按行动合宜性扣分不按事实扣分')]),
    dict(family='D-F8', cat='事实边界', role='dev',
         ctx='输入里没有任何关于她公司新领导的信息；她问我该怎么应对。',
         her='新来的领导老针对我开会点名',
         jia='他点你名是想看你怎么办，你越慌他越来劲。下次直接把你的结论先说出来。',
         yi='他们部门那个新领导我知道，就是看你不顺眼。',
         ref_jia=[('观点', '不适用', '有条件', '无', '对动机的推测用“是想/越…越…”语气，不是已核实事实；不得当成F4击杀')],
         ref_yi=[('事实', '未提供', '确定', '关键', '声称知道该领导且断言其动机，冻结输入无出处')]),

    dict(family='V-F1', cat='语气与分寸', role='validation',
         ctx='她说周末要回老家；目标是接住并留一个后续话题，不施压。',
         her='这周末得回老家一趟 呆两天',
         jia='行啊，回去当两天宝贝闺女。你们那儿赶集不？早市应该挺热闹。',
         yi='你又要走？每次都是你先忙，那我等你好了。',
         ref_jia=[('新提议', '不适用', '试探', '无', '以问句开新话题，非事实主张')],
         ref_yi=[('观点', '未提供', '确定', '关键', '“每次都是你先忙”把单次行程升级成对她行为模式的确定指控，输入无据')]),
    dict(family='V-F2', cat='事实边界', role='validation',
         ctx='她问那家新开的攀岩馆难不难；输入只给了“她刚开始抱石两次”，没有任何该馆线路信息；'
             '备注里写着：我自己也没去过这家馆。',
         her='那家新开的岩馆难不难啊 我有点怕',
         jia='我自己还没去摸过线，不敢给你下结论。第一次去挑最缓的那几条，够你爽就行。',
         yi='不难，那条蓝线特别简单，闭着眼都能上。',
         ref_jia=[('不确定表达', '不适用', '承认未知', '无', '未去过就承认未知，符合边界；不得判“缺少信息没澄清”')],
         ref_yi=[('事实', '未提供', '确定', '关键', '对具体线路难度作无据确定断言')]),
    dict(family='V-F3', cat='状态与归属', role='validation',
         ctx='输入里她只说“下周可能出差”，未给城市与日期；目标是暂定不推进，等她确认。',
         her='下周可能要出差',
         jia='那我先不动，等你落地了告诉我一声，我这边的安排也先留着。',
         yi='出差啊，那就周四晚上约？你周三飞过去也来得及。',
         ref_jia=[('新提议', '不适用', '有条件', '无', '等待对方确认，条件式')],
         ref_yi=[('事实', '冲突', '确定', '关键', '把“可能出差”当成已确定并指定周四/周三，与输入的条件状态冲突')]),
    dict(family='V-F4', cat='收尾与停', role='validation',
         ctx='她说明天早起要先睡；当前目标=自然收尾，不加钩子不赌气。',
         her='先睡了 明天还得早起',
         jia='好，睡吧，明天早起别熬太晚。',
         yi='这么早？行吧，那你睡，梦里记得想我。',
         ref_jia=[('观点', '不适用', '确定', '无', '干净收尾')],
         ref_yi=[('玩笑', '不适用', '确定', '无', '轻度撩话但已收尾；不得仅因带暧昧就判“过度推进”——真正的问题是是否压过了她的收尾信号，此处按无严重度记录')]),
]


def control_nodes():
    out = []
    for i, c in enumerate(CONTROLS):
        out.append({'family': c['family'], 'cat': c['cat'], 'role': c['role'],
                    'idx': i, 'data': c})
    return out


def write_draft():
    os.makedirs(OUT, exist_ok=True)
    nodes, sealed, claims_mech, worksheet = [], [], [], []
    seq = 0

    for m in model_nodes():
        seq += 1
        nid = 'M%03d' % seq
        ev = build_evidence_block(m)
        gc = m['card']
        split = 'dev' if m['story'] in DEV_STORIES else 'validation'
        left_pack, right_pack = m['pack'](m['left']), m['pack'](m['right'])
        a_key, b_key = ('甲', '乙')
        # 匿名左右已由 swap 决定（left/right 已打乱）
        nodes.append({
            'node_id': nid, 'version': PACKET_VERSION, 'manual': MANUAL_VERSION,
            'split': split, 'story': m['story'], 'position': m['position'],
            'provenance': 'model_paid', 'batch': m['batch'],
            'category': category_of(gc),
            'evidence': ev,
            'answers': {a_key: left_pack, b_key: right_pack},
        })
        sealed.append({'node_id': nid, 'position': m['position'], 'batch': m['batch'],
                       'story': m['story'], 'split': split, 'swapped': m['swap'],
                       'category': category_of(gc),
                       '甲': {'arm': m['left']['arm'], 'raw_sha256': m['left']['raw_sha256'],
                              'run_id': m['left']['run_id'], 'program_score10': m['left']['score10'],
                              'veto': m['left']['veto'],
                              'case_input_sha256': m['left']['case_input_sha256']},
                       '乙': {'arm': m['right']['arm'], 'raw_sha256': m['right']['raw_sha256'],
                              'run_id': m['right']['run_id'], 'program_score10': m['right']['score10'],
                              'veto': m['right']['veto'],
                              'case_input_sha256': m['right']['case_input_sha256']}})
        blob = evidence_blob(ev)
        ent = {'node_id': nid, 'split': split, 'answers': {}}
        for who, pack, row in ((a_key, left_pack, m['left']), (b_key, right_pack, m['right'])):
            cl = []
            for j, t in enumerate([pack['primary']] + pack['backups']):
                slot = 'response.recommended' if j == 0 else 'backup[%d]' % j
                if not (t or '').strip():
                    continue
                units = split_claims(t)
                for k, u in enumerate(units):
                    lab = classify_claim(u, blob)
                    cl.append({'slot': slot, 'claim_id': '%s#c%d.%d' % (slot, j, k),
                               'text': u, **lab})
            ent['answers'][who] = cl
            worksheet.append({'node_id': nid, 'who': who, 'evidence_len': len(blob),
                              'claims': [{'id': c['claim_id'], 'text': c['text'],
                                          'type': c['statement_type'], 'ev': c['evidence_status'],
                                          'stance': c['stance'], 'sev': c['severity'],
                                          'rule': c['rule']} for c in cl]})
        claims_mech.append(ent)

    for c in control_nodes():
        seq += 1
        nid = 'C%03d' % (seq)
        d = c['data']
        ev = {'persona_me': None, 'persona_her': None, 'public_seed': None,
              'prior_messages': [], 'current_window': [{'ts': None, 'who': '对方', 'text': d['her']}],
              'advisor_note': d['ctx'],
              'note_visibility': '军师备注只约束本轮怎么说，不是已经发出的话，也不是可引用的现场事实',
              'goal': None, 'clock': None, 'card_allowed': [], 'card_forbidden': [],
              'card_ambiguity': None, 'card_reference_actions': [], 'card_status': None,
              'card_revision': None, 'card_defect_conflicts': None}
        nodes.append({'node_id': nid, 'version': PACKET_VERSION, 'manual': MANUAL_VERSION,
                      'split': 'dev' if d['role'] == 'dev' else 'validation',
                      'story': 'CONTROL:' + d['family'], 'position': nid,
                      'provenance': 'authored_control', 'batch': 'control',
                      'category': d['cat'], 'evidence': ev,
                      'answers': {'甲': {'primary': d['jia'], 'backups': [],
                                         'raw_protocol_text': None, 'no_reply': False,
                                         'direction_null_indices': [], 'literal_null_slots': []},
                                  '乙': {'primary': d['yi'], 'backups': [],
                                         'raw_protocol_text': None, 'no_reply': False,
                                         'direction_null_indices': [], 'literal_null_slots': []}}})
        sealed.append({'node_id': nid, 'position': nid, 'batch': 'control',
                       'story': 'CONTROL:' + d['family'],
                       'split': 'dev' if d['role'] == 'dev' else 'validation',
                       'swapped': False, 'category': d['cat'],
                       '甲': {'arm': 'authored_control_A', 'family': d['family']},
                       '乙': {'arm': 'authored_control_B', 'family': d['family']}})
        blob = evidence_blob(ev)
        ent = {'node_id': nid, 'split': sealed[-1]['split'], 'answers': {}}
        for who, txt, ref in (('甲', d['jia'], d['ref_jia']), ('乙', d['yi'], d['ref_yi'])):
            units = split_claims(txt)
            cl = []
            for k, u in enumerate(units):
                lab = classify_claim(u, blob)
                cl.append({'slot': 'response.recommended', 'claim_id': 'response.recommended#c0.%d' % k,
                           'text': u, **lab})
            ent['answers'][who] = {'claims': cl, 'constructed_reference': ref}
        claims_mech.append(ent)

    counts = collections.Counter((n['split']) for n in nodes)
    if counts['dev'] != 40 or counts['validation'] != 20:
        raise SystemExit('分组数量不符合冻结计划: %s' % dict(counts))

    with open(os.path.join(OUT, 'nodes.jsonl'), 'w', encoding='utf-8') as f:
        for n in nodes:
            f.write(json.dumps(n, ensure_ascii=False) + '\n')
    # 评审者只能读 nodes_public.jsonl：不含 split/story/position/batch/provenance/臂/旧分/散列
    with open(os.path.join(OUT, 'nodes_public.jsonl'), 'w', encoding='utf-8') as f:
        for n in nodes:
            pub = {'node_id': n['node_id'], 'version': n['version'], 'manual': n['manual'],
                   'evidence': dict(n['evidence']), 'answers': {}}
            pub['evidence'].pop('card_revision', None)
            pub['evidence'].pop('card_defect_conflicts', None)
            for who, a in n['answers'].items():
                pub['answers'][who] = {'primary': a['primary'], 'backups': a['backups'],
                                       'raw_protocol_text': a.get('raw_protocol_text'),
                                       'no_reply': a.get('no_reply'),
                                       'direction_null_indices': a.get('direction_null_indices')}
            f.write(json.dumps(pub, ensure_ascii=False) + '\n')
    json.dump({'packet_version': PACKET_VERSION, 'manual_version': MANUAL_VERSION,
               'seed': SEED, 'real_model_calls': 0,
               'split_plan': {'dev': 40, 'validation': 20,
                              'dev_stories': DEV_STORIES, 'validation_stories': VALIDATION_STORIES,
                              'control_families_dev': sorted([x['family'] for x in CONTROLS
                                                              if x['role'] == 'dev']),
                              'control_families_validation': sorted([x['family'] for x in CONTROLS
                                                                     if x['role'] == 'validation']),
                              'note': 'E007 同时有 r2/v5 两份同位答卷，只用 v5 一份，避免同位重复计入'},
               'warning': '密件：评审者不得读取。含臂身份、原文与输入散列、程序分与veto。',
               'nodes': sealed},
              open(os.path.join(OUT, 'SEALED_KEY_V1_1.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    json.dump(claims_mech, open(os.path.join(OUT, 'CLAIMS_MECHANICAL.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    flagged = []
    for ent in claims_mech:
        for who, v in ent['answers'].items():
            for c in (v if isinstance(v, list) else v['claims']):
                if c['evidence_status'] == '未提供':
                    flagged.append({'node_id': ent['node_id'], 'who': who, 'id': c['claim_id'],
                                    'text': c['text'][:60], 'sev': c['severity'],
                                    'rule': c['rule']})
    with open(os.path.join(OUT, 'REVIEW_WORKSHEET.md'), 'w', encoding='utf-8') as f:
        f.write('# 主张机械初稿需人工核对的条目（仅“未提供”命中项）\n\n')
        f.write('共 %d 条。逐条确认为“参考无据断言”或改判；未命中项默认受保护表达，需抽查假阴。\n\n'
                % len(flagged))
        for x in flagged:
            f.write('- `%s/%s` %s ｜严重度=%s ｜依据=%s\n  > %s\n'
                    % (x['node_id'], x['who'], x['id'], x['sev'], x['rule'], x['text']))
    # 假阴抽查：从机械判为“受保护”的主张里固定抽样，逐条人工确认
    pool = []
    for ent in claims_mech:
        for who, v in ent['answers'].items():
            for c in (v if isinstance(v, list) else v['claims']):
                if c['evidence_status'] != '未提供':
                    pool.append({'node_id': ent['node_id'], 'split': ent['split'], 'who': who,
                                 'id': c['claim_id'], 'text': c['text'],
                                 'type': c['statement_type'], 'rule': c['rule']})
    samp_rnd = random.Random(SEED + 7)
    sample = samp_rnd.sample(pool, min(70, len(pool)))
    sample.sort(key=lambda x: (x['node_id'], x['who'], x['id']))
    with open(os.path.join(OUT, 'REVIEW_SAMPLE.md'), 'w', encoding='utf-8') as f:
        f.write('# 受保护主张抽样（%d 条，占受保护池 %d 条的抽样）\n\n'
                '用途：检查机械规则假阴（把无据断言放行）。逐条标记 `keep`（确为受保护/有出处）'
                '或 `unsupported`（应计无据断言）。\n\n' % (len(sample), len(pool)))
        for i, x in enumerate(sample):
            f.write('%d|`%s/%s` %s｜%s｜%s\n  > %s\n'
                    % (i, x['node_id'], x['who'], x['id'], x['split'], x['type'], x['text'][:70]))
    fam2nid = {}
    for n in nodes:
        if n['provenance'] == 'authored_control':
            fam2nid[n['story'].split(':', 1)[1]] = n['node_id']
    json.dump([{'node_id': fam2nid[c['family']], 'family': c['family'], 'role': c['role'],
                'cat': c['cat'], 'jia': c['jia'], 'yi': c['yi'],
                'ref_jia': c['ref_jia'], 'ref_yi': c['ref_yi'], 'ctx': c['ctx'], 'her': c['her']}
               for c in CONTROLS],
              open(os.path.join(OUT, 'CONTROLS_REF.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    json.dump({'nodes': len(nodes), 'dev': counts['dev'], 'validation': counts['validation'],
               'flagged_claims': len(flagged), 'real_model_calls': 0},
              open(os.path.join(OUT, 'DRAFT_SUMMARY.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('DRAFT OK nodes=%d dev=%d validation=%d flagged=%d real_model_calls=0'
          % (len(nodes), counts['dev'], counts['validation'], len(flagged)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', default='draft', choices=('draft', 'freeze'))
    a = ap.parse_args()
    if a.mode == 'draft':
        write_draft()
    else:
        print('freeze 模式在人工核对后由 build_reference_labels_v1_1.py 执行')


if __name__ == '__main__':
    main()
