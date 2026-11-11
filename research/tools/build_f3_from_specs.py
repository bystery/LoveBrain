# -*- coding: utf-8 -*-
r"""tools/build_f3_from_specs.py — 把 final_20261029 的作者稿(spec.json) 转成
episode.json / reference.json / audit.md，并做机械自检（零模型调用）。

设计纪律：
- 只搬运作者已写定的内容；不替作者新写判据、不润色消息、不改卡片语义。
- 唯一允许的归一化：required_this_turn 由"来源是否为当轮"机械推导
  （当轮新事实=记下来 False；回指旧轮=必须回忆 True），因为该字段的定义就是
  "本轮是否必须从记忆中取回"，作者稿里两种含义混用会与 validate_story_v2 冲突。
- message_id 由 quote 在来源轮消息中的唯一出现位置推导；找不到或多次出现 = 硬错误。
- 自检不过即拒绝写文件（宁可不产出，也不产出坏材料）。

用法：
  set PYTHONIOENCODING=utf-8
  D:\anaconda\python.exe tools\build_f3_from_specs.py --stories N01,N02,K01,K02
  D:\anaconda\python.exe tools\build_f3_from_specs.py --stories N01,N02,K01,K02 --manifest
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'benchmark', 'stories_v2', 'final_20261029')
DIMS = {'goal', 'factuality', 'attribution', 'time_state', 'boundary',
        'memory_use', 'interaction', 'initiative', 'emotion_relation'}
BANNED = ['考察', '观测点', '评分', '与上轮不同', '考点', '本题', '测试点',
          'evaluation point', 'grading', 'rubric']
KB_KEYS = {'me', 'her', 'warmth', 'style', 'stage', 'lessons'}


def sha_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def dt(s):
    return datetime.datetime.strptime(s, '%Y-%m-%d %H:%M')


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def dump(p, obj):
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write('\n')


def audit_spec(sid, spec):
    """返回 (problems, normalized_turns)。不修改输入对象。"""
    probs = []
    turns = spec.get('turns') or []
    if not turns:
        return ['%s: no turns' % sid], []
    if len(turns) not in (12, 24):
        probs.append('%s: turns=%d not 12/24' % (sid, len(turns)))
    kb = spec.get('initial_kb') or {}
    if not isinstance(kb, dict):
        probs.append('%s: initial_kb not dict' % sid)
    else:
        for k, v in kb.items():
            if k not in KB_KEYS:
                probs.append('%s: initial_kb.%s outside allowed keys' % (sid, k))
            elif not isinstance(v, str):
                probs.append('%s: initial_kb.%s not str' % (sid, k))
            elif len(v) > 1000:
                probs.append('%s: initial_kb.%s longer than 1000' % (sid, k))
    if not isinstance(spec.get('persona'), dict):
        probs.append('%s: persona not dict' % sid)

    case_ids = [t.get('case_id') for t in turns]
    if len(case_ids) != len(set(case_ids)):
        probs.append('%s: duplicate case_id' % sid)
    msgs_by_case = {}
    prev_clock = None
    for i, t in enumerate(turns):
        cid = t.get('case_id')
        if not cid:
            probs.append('%s: turn %d missing case_id' % (sid, i))
            continue
        want = '%s_T%02d' % (sid, i + 1)
        if cid != want:
            probs.append('%s: case order %s expected %s' % (sid, cid, want))
        msgs = t.get('messages') or []
        msgs_by_case[cid] = msgs
        if not msgs:
            probs.append('%s: empty messages' % cid)
            continue
        if msgs[-1].get('role') != 'HER':
            probs.append('%s: last message is %s (candidate needs a HER message to answer)'
                         % (cid, msgs[-1].get('role')))
        clock = dt(t.get('clock'))
        if prev_clock and clock < prev_clock:
            probs.append('%s: clock goes backwards' % cid)
        prev_clock = clock
        prev_ts = None
        for j, m in enumerate(msgs):
            if m.get('role') not in ('HER', 'ME'):
                probs.append('%s m%d: bad role %r' % (cid, j, m.get('role')))
            if not isinstance(m.get('text'), str) or not m['text'].strip():
                probs.append('%s m%d: empty text' % (cid, j))
            if set(m.keys()) != {'role', 'text', 'ts'}:
                probs.append('%s m%d: keys %s' % (cid, j, sorted(m.keys())))
            ts = dt(m.get('ts'))
            if ts > clock:
                probs.append('%s m%d: ts after clock' % (cid, j))
            if prev_ts and ts < prev_ts:
                probs.append('%s m%d: ts goes backwards' % (cid, j))
            prev_ts = ts
            for s in m.values():
                if isinstance(s, str):
                    for b in BANNED:
                        if b in s:
                            probs.append('%s m%d: banned word %r' % (cid, j, b))
        note = t.get('note')
        if note:
            for b in BANNED:
                if b in note:
                    probs.append('%s note: banned word %r' % (cid, b))
        it = t.get('intent') or {}
        if not isinstance(it.get('enabled'), bool):
            probs.append('%s: intent.enabled not bool' % cid)
        if not isinstance(it.get('status'), str):
            probs.append('%s: intent.status not str' % cid)
        if not isinstance(it.get('text'), str):
            probs.append('%s: intent.text not str' % cid)
        acts = t.get('acceptable_actions') or []
        if len(acts) < 2:
            probs.append('%s: fewer than 2 acceptable actions' % cid)
        if len(set(acts)) != len(acts):
            probs.append('%s: duplicate acceptable actions' % cid)
        dims = set(t.get('applicable_dimensions') or [])
        if not dims <= DIMS:
            probs.append('%s: unknown dims %s' % (cid, sorted(dims - DIMS)))
        if len(dims) < 3:
            probs.append('%s: fewer than 3 dims' % cid)
        if not str(t.get('current_action') or '').strip():
            probs.append('%s: empty current_action' % cid)
        if not str(t.get('private_guidance') or '').strip():
            probs.append('%s: empty private_guidance' % cid)
        for f in ('unknowns', 'protected_expressions', 'forbidden_factual_claims'):
            if not isinstance(t.get(f), list):
                probs.append('%s: %s not list' % (cid, f))
        her_text = '\n'.join(m['text'] for m in msgs if m.get('role') == 'HER')
        for p in t.get('protected_expressions') or []:
            if p not in her_text:
                probs.append('%s: protected expression not in HER text: %r' % (cid, p))

    # atoms：quote 唯一命中 + message_id 推导 + required_this_turn 归一
    order = {cid: i for i, cid in enumerate(case_ids)}
    for t in turns:
        cid = t.get('case_id')
        for a in t.get('required_memory_atoms') or []:
            src = a.get('source') or {}
            if src.get('kind') == 'message':
                scid = src.get('case_id')
                if scid not in msgs_by_case:
                    probs.append('%s atom %s: unknown source case %r' % (cid, a.get('atom_id'), scid))
                    continue
                if order.get(scid, 10 ** 6) > order.get(cid, -1):
                    probs.append('%s atom %s: source is future' % (cid, a.get('atom_id')))
                q = src.get('quote') or ''
                if not q:
                    probs.append('%s atom %s: empty quote' % (cid, a.get('atom_id')))
                    continue
                hits = [k for k, m in enumerate(msgs_by_case[scid]) if q in m.get('text', '')]
                if not hits:
                    probs.append('%s atom %s: quote not found in %s' % (cid, a.get('atom_id'), scid))
                elif len(hits) > 1:
                    probs.append('%s atom %s: quote ambiguous in %s (m%s)'
                                 % (cid, a.get('atom_id'), scid, hits))
                else:
                    src['message_id'] = 'm%d' % hits[0]
            elif src.get('kind') == 'initial_kb':
                key = src.get('key')
                if key not in kb or (src.get('quote') or '') not in kb.get(key, ''):
                    probs.append('%s atom %s: initial_kb source unresolved' % (cid, a.get('atom_id')))
            else:
                probs.append('%s atom %s: unknown source kind' % (cid, a.get('atom_id')))
            # 归一：定义=本轮是否必须从记忆取回
            a['required_this_turn'] = bool(src.get('case_id') and src.get('case_id') != cid)
    return probs, turns


def make_episode(sid, spec, turns):
    out = []
    for t in turns:
        inp = {'clock': t['clock'], 'may_decline': bool(t.get('may_decline')),
               'intent': t.get('intent') or {'text': '', 'status': 'ACTIVE', 'enabled': False},
               'messages': t['messages']}
        if t.get('note'):
            inp['note'] = t['note']
        out.append({'case_id': t['case_id'], 'turn_type': 'reply',
                    'input': inp, 'gold': {}})
    return {'episode_id': sid, 'persona': spec['persona'],
            'initial_kb': spec['initial_kb'], 'turns': out}


def make_reference(sid, spec, turns):
    card_keys = ('case_id', 'current_action', 'acceptable_actions',
                 'applicable_dimensions', 'required_memory_atoms', 'private_guidance',
                 'unknowns', 'protected_expressions', 'forbidden_factual_claims',
                 'may_decline')
    ref = {}
    for t in turns:
        c = {k: t.get(k) for k in card_keys}
        c['may_decline'] = bool(t.get('may_decline'))
        c['intent'] = t.get('intent')
        ref[t['case_id']] = c
    return {'story_id': sid, 'turns': ref}


def make_audit(sid, spec, turns, ep):
    lines = []
    lines.append('# %s — 作者稿与派生材料审计（final_20261029）' % sid)
    lines.append('')
    lines.append('## 0. 性质与暴露')
    lines.append('')
    lines.append('虚构人物与对话，非真实用户材料。题目由实施 worker（本 session）在 2026-10-29 '
                 '为填补全局复核指出的缺口（长期知识、恢复、正向互动切片、组件价值）新写；'
                 '作者已看过全部候选答卷与旧题，因此本材料**不声称盲化、不声称独立封存**，'
                 '只作为前瞻新材料使用。本文件由 spec.json 机械派生，不含手写判据。')
    lines.append('')
    lines.append('## 1. 生活动机')
    lines.append('')
    lines.append('- 男方：%s' % spec['persona'].get('me', ''))
    lines.append('- 女方：%s' % spec['persona'].get('her', ''))
    lines.append('- 关系温度：%s' % spec['initial_kb'].get('warmth', ''))
    lines.append('- 当前位置：%s' % spec['initial_kb'].get('stage', ''))
    lines.append('')
    lines.append('## 2. 连续性与回扣')
    lines.append('')
    lines.append('逐轮消息与时钟（%d 位）：' % len(turns))
    for t in turns:
        lines.append('- %s @ %s：%s；意图=%s（%s）；可回可不回=%s'
                     % (t['case_id'], t['clock'],
                        ' / '.join('%s:%s' % (m['role'], m['text']) for m in t['messages']),
                        (t.get('intent') or {}).get('text') or '（无）',
                        (t.get('intent') or {}).get('status'),
                        '是' if t.get('may_decline') else '否'))
    lines.append('')
    lines.append('## 3. ME 历史与边界')
    lines.append('')
    me = ['%s: %s' % (t['case_id'], m['text']) for t in turns
          for m in t['messages'] if m['role'] == 'ME']
    lines.extend('- ' + x for x in me)
    if not me:
        lines.append('-（本轮题面没有 ME 消息）')
    lines.append('')
    lines.append('## 4. no_reply / 媒体局限')
    lines.append('')
    decline = [t['case_id'] for t in turns if t.get('may_decline')]
    lines.append('可合法不回的位置：%s。其余位置最后一条消息均为 HER，必须给出可交付回复；'
                 '媒体能力限于文本，题面不出现图片/语音依赖。' % ('、'.join(decline) if decline else '无'))
    lines.append('')
    lines.append('## 5. 参考卡质量')
    lines.append('')
    lines.append('每位含 current_action、不少于 2 条可接受动作、3 个以上适用维度、'
                 '必要记忆原子（quote 逐字来自来源消息，message_id 由出现位置机械推导）、'
                 '受保护表达与禁止事实主张。required_this_turn 统一按“本轮是否必须从记忆取回”归一：'
                 '当轮新事实=False，回指旧轮=True。')
    lines.append('')
    lines.append('## 6. 已知风险')
    lines.append('')
    lines.append('- 作者看过候选答卷，可能无意中把题面写成对既有机制的“舒适区”；'
                 '结论只按本批实测报告，不外推为泛化。')
    lines.append('- 长期故事依赖真实记忆链路；若候选在 T12 前未把事实写入记忆，后段失败属于能力信号，'
                 '不得归因于材料缺陷（除非逐条发现 quote/时点错误）。')
    lines.append('- 判分仍由 AI 裁判辅助；口味与愿不愿发只由用户本人标定，本材料不替代。')
    lines.append('')
    lines.append('## 7. 出口检查')
    lines.append('')
    lines.append('机械自检在生成时执行：角色/时钟/ts 单调、protected 逐字在 HER 文本、'
                 'atom quote 唯一命中、维度与动作数量、禁用词。全部通过才写盘；'
                 '随后由 validate_story_v2 与 build_stage2_grading_cards 复核。')
    lines.append('')
    return '\n'.join(lines)


def build_one(sid):
    spec = load(os.path.join(SRC, sid, 'spec.json'))
    if spec.get('story_id') != sid:
        raise SystemExit('%s: spec.story_id=%r' % (sid, spec.get('story_id')))
    probs, turns = audit_spec(sid, spec)
    if probs:
        for p in probs:
            print('PROBLEM', p)
        raise SystemExit('%s: %d problems; refuse to write' % (sid, len(probs)))
    d = os.path.join(SRC, sid)
    ep = make_episode(sid, spec, turns)
    ref = make_reference(sid, spec, turns)
    dump(os.path.join(d, 'episode.json'), ep)
    dump(os.path.join(d, 'reference.json'), ref)
    with open(os.path.join(d, 'audit.md'), 'w', encoding='utf-8') as f:
        f.write(make_audit(sid, spec, turns, ep))
    print('%s episode=%d ref=%d atoms=%d may_decline=%s'
          % (sid, len(ep['turns']), len(ref['turns']),
             sum(len(t['required_memory_atoms']) for t in turns),
             [t['case_id'] for t in turns if t['may_decline']]))
    return {'story_id': sid, 'turns': len(turns)}


def write_manifest(sids, turn_count, name):
    entries = []
    files = ('episode.json', 'reference.json', 'timeline.json', 'audit.md')
    for sid in sids:
        d = os.path.join(SRC, sid)
        entries.append({'story_id': sid,
                        'files': {n: sha_file(os.path.join(d, n)) for n in files}})
    doc = {'kind': 'story_stage_manifest', 'stage': 'final_20261029',
           'count': len(sids), 'turn_per_story': turn_count,
           'created_at': datetime.datetime.now().isoformat(timespec='seconds'),
           'stories': entries}
    dump(os.path.join(SRC, name), doc)
    print('manifest', name, 'stories=%d turns=%d' % (len(sids), turn_count))


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--stories', default='N01,N02,K01,K02')
    ap.add_argument('--manifest', action='store_true',
                    help='额外写 FINAL_N_MANIFEST.json / FINAL_K_MANIFEST.json（需 timeline 已生成）')
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    sids = [s.strip() for s in a.stories.split(',') if s.strip()]
    for sid in sids:
        build_one(sid)
    if a.manifest:
        n = [s for s in sids if s.startswith('N')]
        k = [s for s in sids if s.startswith('K')]
        if n:
            write_manifest(n, 12, 'FINAL_N_MANIFEST.json')
        if k:
            write_manifest(k, 24, 'FINAL_K_MANIFEST.json')
    return 0


if __name__ == '__main__':
    sys.exit(main())
