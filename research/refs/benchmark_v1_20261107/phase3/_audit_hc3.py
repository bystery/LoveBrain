#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AUDITOR-HC3: Independent audit of HOLDOUT stories E461-E490 (30 stories, all 12 turns).
Covers 17 audit items + ghost atom (initial_kb) check.

Isolation: reads ONLY holdout vault files + NAME_POOL_V1.json.
  NO dev/selection/logs/candidates/GOLD_SIDECAR reads.

Schema variants discovered in E461-E490:
  A (E461-E470): source.message_id="m0" (lowercase, index into messages list),
                  source.case_id present, no source.key
  B (E471-E480): source.message_id="E471_T01_M0" (encodes turn), source.key=descriptive label,
                  source.case_id present
  C (E481-E490): source.message_id="M0" (uppercase, index into messages list),
                  source.case_id present, no source.key

Quote matching strategy:
  - For kind=message: find the source turn's messages, match quote as substring in any message text.
    Source turn determined by: source.case_id (always present in this batch).
  - For kind=initial_kb: match quote as substring in the initial_kb JSON text of the episode.
    Also verify source.key exists as a top-level key in initial_kb (if key present).
    Ghost atom = initial_kb atom whose quote cannot be found in KB or message texts.
"""
import json, hashlib, os, re, sys

VAULT = r'D:/lovebrain-arena_holdout_vault/wave6_holdout'
NAME_POOL = r'D:/lovebrain-arena/refs/benchmark_v1_20261107/phase3/NAME_POOL_V1.json'

with open(NAME_POOL, encoding='utf-8') as f:
    namepool = json.load(f)
holdout_names = namepool['holdout_assignment']

EIDS = [f'E{n}' for n in range(461, 491)]

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

def load_json(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)

def sha256_file(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()

def get_all_message_texts(episode):
    """Map case_id -> list of message texts."""
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
    In this batch, source.case_id is always present (verified by survey).
    Fall back to parsing message_id if case_id absent.
    """
    cid = src.get('case_id', '')
    if cid and re.match(r'^E\d+_T\d+$', cid):
        return cid
    msgid = src.get('message_id', '')
    m = re.match(r'(E\d+_T\d+)_M\d+', msgid)
    if m:
        return m.group(1)
    return current_case_id

def source_turn_num(src, current_case_id):
    source_cid = source_case_id_from_atom(src, current_case_id)
    return turn_num_from_case_id(source_cid)

def parse_clock_minutes(clock_str):
    s = clock_str.strip()
    m = re.match(r'(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})', s)
    if m:
        return (int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5)))
    m2 = re.match(r'(\d{4})-(\d{2})-(\d{2})', s)
    if m2:
        return (int(m2.group(1)), int(m2.group(2)), int(m2.group(3)), 0, 0)
    return None

# ============================================================
all_results = {}
all_scene_domains = {}
all_names = {}
all_primary_topics = {}

for eid in EIDS:
    fails = []
    warnings = []
    checks = {}
    story_dir = os.path.join(VAULT, eid)

    required_files = ['episode.json', 'reference.json', 'audit.md', 'timeline.json', 'exposure.json', 'runner_episode.json']
    for rf in required_files:
        if not os.path.exists(os.path.join(story_dir, rf)):
            fails.append(f'文件缺失: {rf}')

    if fails:
        all_results[eid] = {'pass': False, 'fails': fails, 'warnings': warnings, 'checks': checks}
        continue

    episode = load_json(os.path.join(story_dir, 'episode.json'))
    reference = load_json(os.path.join(story_dir, 'reference.json'))
    exposure = load_json(os.path.join(story_dir, 'exposure.json'))

    with open(os.path.join(story_dir, 'audit.md'), encoding='utf-8') as f:
        audit_md = f.read()

    # --- 1: Name consistency ---
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

    # --- 2: Turn count = 12 ---
    turns = episode.get('turns', [])
    turn_count = len(turns)
    checks['turn_count'] = turn_count
    if turn_count != 12:
        fails.append(f'轮数错误: {turn_count} (应为12)')

    # --- 3: case_id unique + format ---
    case_ids = [t.get('case_id', '') for t in turns]
    if len(set(case_ids)) != len(case_ids):
        dups = set(c for c in case_ids if case_ids.count(c) > 1)
        fails.append(f'case_id 不唯一: {dups}')
    for i, cid in enumerate(case_ids):
        expected_cid = f'{eid}_T{i+1:02d}'
        if cid != expected_cid:
            fails.append(f'case_id 格式错误 turn{i+1}: got={cid} expected={expected_cid}')
            break

    # --- 4: turn_type=reply ---
    for i, t in enumerate(turns):
        if t.get('turn_type') != 'reply':
            fails.append(f'turn_type 非reply turn{i+1}: {t.get("turn_type")}')

    # --- 5: gold={} ---
    for i, t in enumerate(turns):
        if t.get('gold') != {}:
            fails.append(f'gold 非空 turn{i+1}: {json.dumps(t.get("gold"),ensure_ascii=False)}')

    # --- 6: clock monotonic non-decreasing ---
    clocks = [t.get('input', {}).get('clock', '') for t in turns]
    clock_parsed = [parse_clock_minutes(c) for c in clocks]
    for i in range(1, len(clock_parsed)):
        if clock_parsed[i] is None or clock_parsed[i-1] is None:
            fails.append(f'clock 解析失败 turn{i}: {clocks[i]}')
            break
        if clock_parsed[i] < clock_parsed[i-1]:
            fails.append(f'clock 非单调不减 turn{i+1}: {clocks[i]} < {clocks[i-1]}')
    checks['clock_range'] = f'{clocks[0]} -> {clocks[-1]}' if clocks else ''

    # --- 7: may_decline >= 2 ---
    md_turns = [i+1 for i, t in enumerate(turns) if t.get('input', {}).get('may_decline', False)]
    checks['may_decline_turns'] = md_turns
    if len(md_turns) < 2:
        fails.append(f'may_decline 不足: {len(md_turns)} (应≥2)')

    # --- 8: intent rounds 2-4 ---
    intent_turns = [i+1 for i, t in enumerate(turns) if 'intent' in t.get('input', {})]
    checks['intent_turns'] = intent_turns
    if not (2 <= len(intent_turns) <= 4):
        fails.append(f'intent 轮数越界: {len(intent_turns)} (应2-4)')

    # --- 9: note rounds 1-3 ---
    note_turns = [i+1 for i, t in enumerate(turns) if 'note' in t.get('input', {})]
    checks['note_turns'] = note_turns
    if not (1 <= len(note_turns) <= 3):
        fails.append(f'note 轮数越界: {len(note_turns)} (应1-3)')

    # --- 10: Forbidden keywords ---
    ep_text = json.dumps(episode, ensure_ascii=False)
    ref_text = json.dumps(reference, ensure_ascii=False)
    found_kw_ep = [kw for kw in FORBIDDEN_KW if kw in ep_text]
    found_kw_ref = [kw for kw in FORBIDDEN_KW if kw in ref_text]
    found_kw = sorted(set(found_kw_ep + found_kw_ref))
    if found_kw:
        fails.append(f'禁关键词命中: {found_kw}')
    checks['forbidden_kw'] = found_kw

    # --- 11: quote strict ---
    all_texts = get_all_message_texts(episode)
    initial_kb = episode.get('initial_kb', {})
    initial_kb_text = json.dumps(initial_kb, ensure_ascii=False)
    ref_turns = reference.get('turns', {})
    quote_fails = []
    ghost_atoms = []
    for case_id, tref in ref_turns.items():
        for atom in tref.get('required_memory_atoms', []):
            src = atom.get('source', {})
            quote = src.get('quote', '')
            if not quote:
                continue
            kind = src.get('kind', 'message')
            source_cid = source_case_id_from_atom(src, case_id)
            source_texts = all_texts.get(source_cid, [])
            if kind == 'initial_kb':
                # Ghost atom check: quote must be findable in KB
                in_kb = quote in initial_kb_text
                in_msgs = any(quote in st for st in source_texts)
                # Also check source.key exists in initial_kb if present
                src_key = src.get('key', '')
                key_in_kb = True
                if src_key:
                    key_in_kb = src_key in initial_kb
                if not in_kb and not in_msgs:
                    ghost_atoms.append(f'{case_id}/{atom.get("id","?")}: initial_kb quote="{quote[:40]}" not found in KB or messages')
                    quote_fails.append(f'{case_id}/{atom.get("id","?")}: ghost atom quote="{quote[:40]}"')
                if src_key and not key_in_kb:
                    ghost_atoms.append(f'{case_id}/{atom.get("id","?")}: initial_kb key="{src_key}" not a top-level KB key')
                    quote_fails.append(f'{case_id}/{atom.get("id","?")}: ghost key="{src_key}"')
            else:
                # message kind: quote must be substring in some message text of source turn
                if not any(quote in st for st in source_texts):
                    quote_fails.append(f'{case_id}/{atom.get("id","?")}: quote="{quote[:40]}" not in {source_cid} msgs')
    if quote_fails:
        fails.append(f'quote strict 失败({len(quote_fails)}处): ' + '; '.join(quote_fails[:4]))
    checks['quote_fails'] = len(quote_fails)
    checks['ghost_atoms'] = ghost_atoms

    # --- 12: required_this_turn source earlier than current turn ---
    req_order_fails = []
    for case_id, tref in ref_turns.items():
        cur_turn = turn_num_from_case_id(case_id)
        if cur_turn is None:
            continue
        for atom in tref.get('required_memory_atoms', []):
            if not atom.get('required_this_turn'):
                continue
            src = atom.get('source', {})
            kind = src.get('kind', 'message')
            if kind == 'initial_kb':
                continue  # initial_kb is pre-conversation, always earlier
            src_turn = source_turn_num(src, case_id)
            if src_turn is None:
                continue
            if src_turn >= cur_turn:
                req_order_fails.append(f'{case_id}/{atom.get("id","?")}: src_turn {src_turn} >= cur_turn {cur_turn}')
    if req_order_fails:
        fails.append(f'required来源不早于当前轮({len(req_order_fails)}处): ' + '; '.join(req_order_fails[:3]))
    checks['req_order_fails'] = len(req_order_fails)

    # --- 13: runner_episode byte-identical ---
    ep_hash = sha256_file(os.path.join(story_dir, 'episode.json'))
    run_hash = sha256_file(os.path.join(story_dir, 'runner_episode.json'))
    if ep_hash != run_hash:
        fails.append(f'runner_episode hash不一致: episode={ep_hash[:16]} runner={run_hash[:16]}')
    checks['runner_match'] = (ep_hash == run_hash)

    # --- 14: audit.md 8 sections ---
    missing_sections = [sec for sec in AUDIT_SECTIONS if sec not in audit_md]
    if missing_sections:
        fails.append(f'audit.md 缺节: {missing_sections}')
    checks['audit_sections_present'] = len(AUDIT_SECTIONS) - len(missing_sections)

    # --- 15: scene domain (uniqueness checked later) ---
    domain = exposure.get('domain', '')
    all_scene_domains[eid] = domain
    checks['domain'] = domain
    if not domain:
        fails.append('场景域缺失')

    # primary_topic for tracking
    topic = exposure.get('primary_topic', '')
    all_primary_topics[eid] = topic
    checks['primary_topic'] = topic

    # --- 16: cross-turn backref >= 2 (1 short d=1 + 1 long d>=4) ---
    backrefs = []
    for case_id, tref in ref_turns.items():
        cur_turn = turn_num_from_case_id(case_id)
        if cur_turn is None:
            continue
        for atom in tref.get('required_memory_atoms', []):
            if not atom.get('required_this_turn'):
                continue
            src = atom.get('source', {})
            kind = src.get('kind', 'message')
            if kind == 'initial_kb':
                continue
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

    all_results[eid] = {'pass': len(fails) == 0, 'fails': fails, 'warnings': warnings, 'checks': checks}

# ============================================================
# Cross-story checks
# ============================================================
# 15 cross: scene domain uniqueness
domain_count = {}
for eid, d in all_scene_domains.items():
    domain_count.setdefault(d, []).append(eid)
dup_domains = {d: eids for d, eids in domain_count.items() if len(eids) > 1 and d}

# 17: cross-story name pollution
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

# Also check name pollution against the FULL holdout pool (E401-E520)
# to detect cross-batch contamination (not just within E461-E490)
her_names_in_scope = {}
me_names_in_scope = {}
for eid in EIDS:
    if eid in all_names:
        her, me = all_names[eid]
        if her: her_names_in_scope.setdefault(her, []).append(eid)
        if me: me_names_in_scope.setdefault(me, []).append(eid)

# Check if any E461-E490 name appears as a different role in the full pool
cross_pool_conflicts = []
for eid_check in EIDS:
    if eid_check not in all_names:
        continue
    her, me = all_names[eid_check]
    # Check if her name appears as ME in any other holdout assignment
    for other_eid, other_assign in holdout_names.items():
        if other_eid == eid_check:
            continue
        if her == other_assign.get('me', ''):
            cross_pool_conflicts.append(f'{eid_check} HER={her} == {other_eid} ME')
        if me == other_assign.get('her', ''):
            cross_pool_conflicts.append(f'{eid_check} ME={me} == {other_eid} HER')

# Apply cross-story fails
for eid in EIDS:
    r = all_results.get(eid, {'pass': False, 'fails': ['story missing'], 'warnings': [], 'checks': {}})
    domain = all_scene_domains.get(eid, '')
    if domain and domain in dup_domains:
        others = [e for e in dup_domains[domain] if e != eid]
        r['fails'].append(f'场景域重复: "{domain}" 也出现于 {others}')
        r['pass'] = False
    for nc in name_conflicts:
        if eid in nc:
            r['fails'].append(f'跨篇名污染: {nc}')
            r['pass'] = False
    if r.get('checks'):
        r['checks']['dup_domains'] = [d for d, eids in dup_domains.items() if eid in eids]
        r['checks']['cross_pool_conflicts'] = [c for c in cross_pool_conflicts if eid in c]

# ============================================================
output = {
    'per_story': {},
    'cross_story': {
        'dup_domains': dup_domains,
        'name_conflicts': name_conflicts,
        'cross_pool_conflicts': cross_pool_conflicts,
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
        'checks': r['checks'],
    }

output['summary'] = {
    'total': len(EIDS),
    'pass': sum(1 for v in output['per_story'].values() if v['pass']),
    'fail': sum(1 for v in output['per_story'].values() if not v['pass']),
}

print(json.dumps(output, ensure_ascii=False, indent=2))
