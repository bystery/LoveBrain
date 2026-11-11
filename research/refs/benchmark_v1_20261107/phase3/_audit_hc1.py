#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AUDITOR-HC1: Independent audit of HOLDOUT stories E401-E430.
Covers 18 audit items per task spec.
Isolation: reads ONLY holdout vault files + NAME_POOL_V1.json. No dev/selection/logs/candidates.

Source schema variants discovered:
  A (E401-E410): source.message_id="m0", source.key="E401_T01" (case_id), quote=verbatim phrase
  B (E411-E420): source.message_id="E411_T02_M00" (encodes turn), source.key=descriptive label, quote=verbatim phrase
  C (E421-E430): source.message_id="E421_T01_M00" (encodes turn), no source.key, quote=short substring

Unified approach: use source.message_id to determine source turn (parse "EID_TNN_Mxx" or fallback to current case_id for "m0" format).
Quote matching: substring match against source turn's message texts.
"""
import json, hashlib, os, re, sys

VAULT = r'D:/lovebrain-arena_holdout_vault/wave6_holdout'
NAME_POOL = r'D:/lovebrain-arena/refs/benchmark_v1_20261107/phase3/NAME_POOL_V1.json'

with open(NAME_POOL, encoding='utf-8') as f:
    namepool = json.load(f)
holdout_names = namepool['holdout_assignment']

EIDS = [f'E{n}' for n in range(401, 431)]

FORBIDDEN_KW = ['暧昧', '心动', '喜欢你', '调情', '撩拨']

AUDIT_SECTIONS = [
    '## 0. 性质',
    '## 1. 生成信息',
    '## 2. 连续性',
    '## 3. ME/HER 状态',
    '## 4. no_reply / 媒体局限',
    '## 5. 已知',
    '## 6. 参与者注释',
    '## 7. 出题痕迹检查',
]

T02_GATE_KEYS = [
    'signal_rounds', 'anti_temptation', 'intensity_gradient',
    'forbidden_keywords', 'cross_ref', 'may_decline',
    'intent', 'scene_unique', 'third_party',
]

def load_json(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)

def sha256_file(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()

def get_all_message_texts(episode):
    texts = {}
    for t in episode['turns']:
        cid = t['case_id']
        msgs = t.get('input', {}).get('messages', [])
        texts[cid] = [m.get('text', '') for m in msgs]
    return texts

def turn_num_from_case_id(cid):
    m = re.match(r'E\d+_T(\d+)', cid)
    return int(m.group(1)) if m else None

def source_case_id_from_atom(src, current_case_id):
    """Determine which case_id the source message belongs to.
    Handles 3 schema variants:
      A: src.key = case_id (e.g. "E401_T01"), src.message_id = "m0"
      B: src.message_id = "E411_T02_M00" (encodes turn), src.key = descriptive label
      C: src.message_id = "E421_T01_M00" (encodes turn), no src.key
    Priority: key-if-case_id > message_id-encoded > current_case_id
    """
    key = src.get('key', '')
    msgid = src.get('message_id', '')
    # Schema A: key is a case_id
    if key and re.match(r'^E\d+_T\d+$', key):
        return key
    # Schema B/C: message_id encodes turn
    m = re.match(r'(E\d+_T\d+)_M\d+', msgid)
    if m:
        return m.group(1)
    # Schema A fallback: message_id is "m0" -> same turn as key (or current if no key)
    if key and re.match(r'^E\d+_T\d+$', key):
        return key
    return current_case_id

def source_turn_num(src, current_case_id):
    """Get source turn number from source dict (handles all schemas)."""
    source_cid = source_case_id_from_atom(src, current_case_id)
    return turn_num_from_case_id(source_cid)

def parse_clock_minutes(clock_str):
    m = re.match(r'(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})', clock_str.strip())
    if m:
        return (int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5)))
    m2 = re.match(r'(\d{4})-(\d{2})-(\d{2})', clock_str.strip())
    if m2:
        return (int(m2.group(1)), int(m2.group(2)), int(m2.group(3)), 0, 0)
    return None

# ============================================================
all_results = {}
all_scene_domains = {}
all_names = {}

for eid in EIDS:
    fails = []
    checks = {}
    story_dir = os.path.join(VAULT, eid)

    required_files = ['episode.json', 'reference.json', 'audit.md', 'timeline.json', 'exposure.json', 'runner_episode.json']
    for rf in required_files:
        if not os.path.exists(os.path.join(story_dir, rf)):
            fails.append(f'文件缺失: {rf}')

    if fails:
        all_results[eid] = {'pass': False, 'fails': fails, 'checks': checks}
        continue

    episode = load_json(os.path.join(story_dir, 'episode.json'))
    reference = load_json(os.path.join(story_dir, 'reference.json'))
    exposure = load_json(os.path.join(story_dir, 'exposure.json'))

    with open(os.path.join(story_dir, 'audit.md'), encoding='utf-8') as f:
        audit_md = f.read()

    # 1: Name consistency
    expected = holdout_names.get(eid, {})
    actual_her = episode.get('persona', {}).get('her', {}).get('name', '')
    actual_me = episode.get('persona', {}).get('me', {}).get('name', '')
    if actual_her != expected.get('her', ''):
        fails.append(f'名字不一致 HER: episode={actual_her} pool={expected.get("her","")}')
    if actual_me != expected.get('me', ''):
        fails.append(f'名字不一致 ME: episode={actual_me} pool={expected.get("me","")}')
    all_names[eid] = (actual_her, actual_me)
    checks['her'] = actual_her
    checks['me'] = actual_me

    # 2: Turn count = 12
    turns = episode.get('turns', [])
    turn_count = len(turns)
    checks['turn_count'] = turn_count
    if turn_count != 12:
        fails.append(f'轮数错误: {turn_count} (应为12)')

    # 3: case_id unique + format
    case_ids = [t.get('case_id', '') for t in turns]
    expected_case_ids = [f'{eid}_T{n:02d}' for n in range(1, 13)]
    if len(set(case_ids)) != len(case_ids):
        dups = set(c for c in case_ids if case_ids.count(c) > 1)
        fails.append(f'case_id 不唯一: {dups}')
    for i, cid in enumerate(case_ids):
        expected_cid = f'{eid}_T{i+1:02d}'
        if cid != expected_cid:
            fails.append(f'case_id 格式错误 turn{i+1}: got={cid} expected={expected_cid}')
            break

    # 4: turn_type=reply
    for i, t in enumerate(turns):
        if t.get('turn_type') != 'reply':
            fails.append(f'turn_type 非reply turn{i+1}: {t.get("turn_type")}')

    # 5: gold={}
    for i, t in enumerate(turns):
        if t.get('gold') != {}:
            fails.append(f'gold 非空 turn{i+1}: {t.get("gold")}')

    # 6: clock monotonic non-decreasing
    clocks = [t.get('input', {}).get('clock', '') for t in turns]
    clock_parsed = [parse_clock_minutes(c) for c in clocks]
    for i in range(1, len(clock_parsed)):
        if clock_parsed[i] is None or clock_parsed[i-1] is None:
            fails.append(f'clock 解析失败 turn{i}: {clocks[i]}')
            break
        if clock_parsed[i] < clock_parsed[i-1]:
            fails.append(f'clock 非单调不减 turn{i+1}: {clocks[i]} < {clocks[i-1]}')
    checks['clock_range'] = f'{clocks[0]} -> {clocks[-1]}' if clocks else ''

    # 7: may_decline >= 2
    md_turns = [i+1 for i, t in enumerate(turns) if t.get('input', {}).get('may_decline', False)]
    checks['may_decline_turns'] = md_turns
    if len(md_turns) < 2:
        fails.append(f'may_decline 不足: {len(md_turns)} (应≥2)')

    # 8: intent rounds 2-4
    intent_turns = [i+1 for i, t in enumerate(turns) if 'intent' in t.get('input', {})]
    checks['intent_turns'] = intent_turns
    if not (2 <= len(intent_turns) <= 4):
        fails.append(f'intent 轮数越界: {len(intent_turns)} (应2-4)')

    # 9: note rounds 1-3
    note_turns = [i+1 for i, t in enumerate(turns) if 'note' in t.get('input', {})]
    checks['note_turns'] = note_turns
    if not (1 <= len(note_turns) <= 3):
        fails.append(f'note 轮数越界: {len(note_turns)} (应1-3)')

    # 10: Forbidden keywords
    ep_text = json.dumps(episode, ensure_ascii=False)
    found_kw = [kw for kw in FORBIDDEN_KW if kw in ep_text]
    if found_kw:
        fails.append(f'禁关键词命中: {found_kw}')
    checks['forbidden_kw'] = found_kw

    # 11: quote strict - substring match in source turn messages
    all_texts = get_all_message_texts(episode)
    ref_turns = reference.get('turns', {})
    quote_fails = []
    for case_id, tref in ref_turns.items():
        for atom in tref.get('required_memory_atoms', []):
            src = atom.get('source', {})
            quote = src.get('quote', '')
            msgid = src.get('message_id', '')
            if not quote:
                continue
            source_cid = source_case_id_from_atom(src, case_id)
            source_texts = all_texts.get(source_cid, [])
            if not any(quote in st for st in source_texts):
                quote_fails.append(f'{case_id}/{atom.get("id","?")}: quote="{quote[:40]}" not in {source_cid}')
    if quote_fails:
        fails.append(f'quote strict 失败({len(quote_fails)}处): ' + '; '.join(quote_fails[:3]))
    checks['quote_fails'] = len(quote_fails)

    # 12: required_this_turn source earlier than current turn
    req_order_fails = []
    for case_id, tref in ref_turns.items():
        cur_turn = turn_num_from_case_id(case_id)
        if cur_turn is None:
            continue
        for atom in tref.get('required_memory_atoms', []):
            if not atom.get('required_this_turn'):
                continue
            src = atom.get('source', {})
            src_turn = source_turn_num(src, case_id)
            if src_turn is None:
                continue
            if src_turn >= cur_turn:
                req_order_fails.append(f'{case_id}/{atom.get("id","?")}: src_turn {src_turn} >= cur_turn {cur_turn}')
    if req_order_fails:
        fails.append(f'required来源不早于当前轮({len(req_order_fails)}处): ' + '; '.join(req_order_fails[:3]))
    checks['req_order_fails'] = len(req_order_fails)

    # 13: runner_episode byte-identical
    ep_hash = sha256_file(os.path.join(story_dir, 'episode.json'))
    run_hash = sha256_file(os.path.join(story_dir, 'runner_episode.json'))
    if ep_hash != run_hash:
        fails.append(f'runner_episode hash不一致: episode={ep_hash[:16]} runner={run_hash[:16]}')
    checks['runner_match'] = (ep_hash == run_hash)

    # 14: audit.md 8 sections
    missing_sections = [sec for sec in AUDIT_SECTIONS if sec not in audit_md]
    if missing_sections:
        fails.append(f'audit.md 缺节: {missing_sections}')
    checks['audit_sections'] = len(AUDIT_SECTIONS) - len(missing_sections)

    # 15: scene domain (uniqueness checked later)
    domain = exposure.get('domain', '')
    all_scene_domains[eid] = domain
    checks['domain'] = domain
    if not domain:
        fails.append('场景域缺失')

    # 16: T02 signal gate for E406-E430
    topic = exposure.get('primary_topic', '')
    checks['topic'] = topic
    if eid >= 'E406':
        sg = reference.get('t02_signal_gate')
        if sg is None:
            fails.append('T02: t02_signal_gate 缺失')
        else:
            missing_gate = [k for k in T02_GATE_KEYS if k not in sg]
            if missing_gate:
                fails.append(f'T02 signal_gate 缺项: {missing_gate}')
            else:
                for s in sg.get('signal_rounds', []):
                    if 'turn' not in s or 'signal' not in s or 'strength' not in s:
                        fails.append(f'T02 signal_rounds 条目不完整: {s}')
                        break
        checks['t02_gate'] = 'present' if sg else 'missing'

    # 17: cross-turn backref >= 2 (1 short d=1 + 1 long d>=4)
    backrefs = []
    for case_id, tref in ref_turns.items():
        cur_turn = turn_num_from_case_id(case_id)
        if cur_turn is None:
            continue
        for atom in tref.get('required_memory_atoms', []):
            if not atom.get('required_this_turn'):
                continue
            src = atom.get('source', {})
            src_turn = source_turn_num(src, case_id)
            if src_turn is None:
                continue
            dist = cur_turn - src_turn
            if dist >= 1:
                backrefs.append((cur_turn, src_turn, dist))
    short_refs = [b for b in backrefs if b[2] == 1]
    long_refs = [b for b in backrefs if b[2] >= 4]
    checks['short_refs'] = len(short_refs)
    checks['long_refs'] = len(long_refs)
    if len(short_refs) < 1:
        fails.append(f'跨轮回指缺短距(d=1): 有{len(short_refs)}个')
    if len(long_refs) < 1:
        fails.append(f'跨轮回指缺长距(d>=4): 有{len(long_refs)}个')

    all_results[eid] = {'pass': len(fails) == 0, 'fails': fails, 'checks': checks}

# ============================================================
# Cross-story checks
# ============================================================
# 15 cross: scene domain uniqueness
domain_count = {}
for eid, d in all_scene_domains.items():
    domain_count.setdefault(d, []).append(eid)
dup_domains = {d: eids for d, eids in domain_count.items() if len(eids) > 1 and d}

# 18: cross-story name pollution
all_her = {}
all_me = {}
name_conflicts = []
for eid in EIDS:
    if eid in all_names:
        her, me = all_names[eid]
        if her:
            if her in all_me:
                name_conflicts.append(f'{eid} HER={her} 与 {all_me[her]} ME 重名')
            all_her.setdefault(her, []).append(eid)
        if me:
            if me in all_her:
                name_conflicts.append(f'{eid} ME={me} 与 {all_her[me]} HER 重名')
            all_me.setdefault(me, []).append(eid)

her_dup = {n: eids for n, eids in all_her.items() if len(eids) > 1}
me_dup = {n: eids for n, eids in all_me.items() if len(eids) > 1}
for n, eids in her_dup.items():
    name_conflicts.append(f'HER名"{n}"出现于多篇: {eids}')
for n, eids in me_dup.items():
    name_conflicts.append(f'ME名"{n}"出现于多篇: {eids}')

# Apply cross-story fails
for eid in EIDS:
    r = all_results.get(eid, {'pass': False, 'fails': ['story missing'], 'checks': {}})
    domain = all_scene_domains.get(eid, '')
    if domain and domain in dup_domains:
        others = [e for e in dup_domains[domain] if e != eid]
        r['fails'].append(f'场景域重复: "{domain}" 也出现于 {others}')
        r['pass'] = False
    for nc in name_conflicts:
        if eid in nc:
            r['fails'].append(f'跨篇名污染: {nc}')
            r['pass'] = False

# ============================================================
output = {
    'per_story': {},
    'cross_story': {
        'dup_domains': dup_domains,
        'name_conflicts': name_conflicts,
        'her_dup': her_dup,
        'me_dup': me_dup,
    },
    'summary': {},
}

for eid in EIDS:
    r = all_results[eid]
    output['per_story'][eid] = {
        'pass': r['pass'],
        'fails': r['fails'],
        'checks': {k: v for k, v in r['checks'].items()},
    }

output['summary'] = {
    'total': len(EIDS),
    'pass': sum(1 for v in output['per_story'].values() if v['pass']),
    'fail': sum(1 for v in output['per_story'].values() if not v['pass']),
}

print(json.dumps(output, ensure_ascii=False, indent=2))
