#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
AUDITOR-HC4: Independent audit of HOLDOUT stories E491-E520 (30 stories).
Isolation: reads ONLY holdout vault + NAME_POOL_V1.json + exposure/reference/audit.
No dev/selection/logs/candidates/GOLD_SIDECAR.

Source schema variants:
  E491-E504 (T07/T08): message_id="EID_TNN_Mxx" (encodes turn) for turn atoms;
                        message_id="EID_KB" + key for KB atoms.
  E505-E512 (T09):      message_id="m0" + key="EID_TNN" (key is case_id).
  E513-E520 (T10 twin): message_id="EID_TNN_Mxx" (encodes turn).

Turn counts: T07/T08/T10=12, T09(E505-E509)=24, T09(E510-E512)=32.
"""
import json, hashlib, os, re, sys

VAULT = r'D:/lovebrain-arena_holdout_vault/wave6_holdout'
NAME_POOL = r'D:/lovebrain-arena/refs/benchmark_v1_20261107/phase3/NAME_POOL_V1.json'
OUT_JSON = r'D:/lovebrain-arena/refs/benchmark_v1_20261107/phase3/_audit_hc4_results.json'

with open(NAME_POOL, encoding='utf-8') as f:
    namepool = json.load(f)
holdout_names = namepool['holdout_assignment']

EIDS = ['E%d' % n for n in range(491, 521)]

# topic -> expected turn count
def expected_turns(eid):
    n = int(eid[1:])
    if 505 <= n <= 509:
        return 24
    if 510 <= n <= 512:
        return 32
    return 12  # T07(491-496), T08(497-504), T10(513-520)

# T09 long-drama note range is 2-4; others 1-3
def note_range(eid):
    n = int(eid[1:])
    if 505 <= n <= 512:  # T09
        return (2, 4)
    return (1, 3)

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
    texts = {}
    for t in episode['turns']:
        cid = t['case_id']
        msgs = t.get('input', {}).get('messages', [])
        texts[cid] = [m.get('text', '') for m in msgs]
    return texts

def turn_num_from_case_id(cid):
    m = re.match(r'E\d+_T(\d+)', cid)
    return int(m.group(1)) if m else None

def parse_clock_minutes(clock_str):
    s = clock_str.strip()
    m = re.match(r'(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})', s)
    if m:
        return (int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5)))
    m2 = re.match(r'(\d{4})-(\d{2})-(\d{2})', s)
    if m2:
        return (int(m2.group(1)), int(m2.group(2)), int(m2.group(3)), 0, 0)
    return None

def source_case_id(src, current_case_id, eid):
    """Determine which case_id the source message belongs to. Handles 3 schemas."""
    key = src.get('key', '')
    msgid = src.get('message_id', '')
    # KB source: EID_KB
    if re.match(r'^E\d+_KB$', msgid):
        return '__KB__'
    # Schema B/C/T10: message_id encodes turn (EID_TNN_Mxx)
    m = re.match(r'(E\d+_T\d+)_M\d+', msgid)
    if m:
        return m.group(1)
    # Schema T09: key is case_id
    if key and re.match(r'^E\d+_T\d+$', key):
        return key
    # m0 with no usable key -> current turn
    return current_case_id

def source_turn_num(src, current_case_id, eid):
    sc = source_case_id(src, current_case_id, eid)
    if sc == '__KB__':
        return -1  # KB is pre-episode; always earlier
    return turn_num_from_case_id(sc)

def kb_text_for_key(episode, key):
    kb = episode.get('initial_kb', {})
    # keys in initial_kb: me, her, warmth, style, lessons
    return kb.get(key, '')

def is_weekday_correct(date_str, weekday_claim):
    """Verify 2026 date -> weekday. weekday_claim is Chinese like 周三/周六."""
    import datetime
    try:
        y, mo, d = map(int, date_str.split('-'))
        dt = datetime.date(y, mo, d)
        wd = dt.weekday()  # Mon=0
        wd_zh = ['周一','周二','周三','周四','周五','周六','周日'][wd]
        return wd_zh == weekday_claim
    except Exception:
        return None  # can't verify


# ============================================================
all_results = {}
all_scene_domains = {}
all_names = {}
all_episode_hashes = {}

for eid in EIDS:
    fails = []
    checks = {}
    story_dir = os.path.join(VAULT, eid)

    required_files = ['episode.json', 'reference.json', 'audit.md', 'timeline.json', 'exposure.json', 'runner_episode.json']
    for rf in required_files:
        if not os.path.exists(os.path.join(story_dir, rf)):
            fails.append('文件缺失: %s' % rf)

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
        fails.append('名字不一致 HER: episode=%s pool=%s' % (actual_her, expected.get('her', '')))
    if actual_me != expected.get('me', ''):
        fails.append('名字不一致 ME: episode=%s pool=%s' % (actual_me, expected.get('me', '')))
    all_names[eid] = (actual_her, actual_me)
    checks['her'] = actual_her
    checks['me'] = actual_me

    # 2: Turn count
    turns = episode.get('turns', [])
    tc = len(turns)
    exp_tc = expected_turns(eid)
    checks['turn_count'] = tc
    checks['expected_turns'] = exp_tc
    if tc != exp_tc:
        fails.append('轮数错误: %d (应为%d)' % (tc, exp_tc))

    # 3: case_id unique + format
    case_ids = [t.get('case_id', '') for t in turns]
    if len(set(case_ids)) != len(case_ids):
        dups = set(c for c in case_ids if case_ids.count(c) > 1)
        fails.append('case_id 不唯一: %s' % (dups,))
    for i, cid in enumerate(case_ids):
        expected_cid = '%s_T%02d' % (eid, i + 1)
        if cid != expected_cid:
            fails.append('case_id 格式错误 turn%d: got=%s expected=%s' % (i + 1, cid, expected_cid))
            break

    # 4: turn_type=reply
    for i, t in enumerate(turns):
        if t.get('turn_type') != 'reply':
            fails.append('turn_type 非reply turn%d: %s' % (i + 1, t.get('turn_type')))

    # 5: gold={}
    for i, t in enumerate(turns):
        if t.get('gold') != {}:
            fails.append('gold 非空 turn%d: %s' % (i + 1, t.get('gold')))

    # 6: clock monotonic non-decreasing
    clocks = [t.get('input', {}).get('clock', '') for t in turns]
    clock_parsed = [parse_clock_minutes(c) for c in clocks]
    clock_ok = True
    for i in range(1, len(clock_parsed)):
        if clock_parsed[i] is None or clock_parsed[i - 1] is None:
            fails.append('clock 解析失败 turn%d: %s' % (i, clocks[i]))
            clock_ok = False
            break
        if clock_parsed[i] < clock_parsed[i - 1]:
            fails.append('clock 非单调不减 turn%d: %s < %s' % (i + 1, clocks[i], clocks[i - 1]))
            clock_ok = False
            break
    checks['clock_range'] = '%s -> %s' % (clocks[0], clocks[-1]) if clocks else ''
    checks['clock_monotonic'] = clock_ok

    # 7: may_decline >= 2
    md_turns = [i + 1 for i, t in enumerate(turns) if t.get('input', {}).get('may_decline', False)]
    checks['may_decline_turns'] = md_turns
    if len(md_turns) < 2:
        fails.append('may_decline 不足: %d (应>=2)' % len(md_turns))

    # 8: intent rounds 2-4
    intent_turns = [i + 1 for i, t in enumerate(turns) if 'intent' in t.get('input', {})]
    checks['intent_turns'] = intent_turns
    if not (2 <= len(intent_turns) <= 4):
        fails.append('intent 轮数越界: %d (应2-4)' % len(intent_turns))

    # 9: note rounds (range depends on topic)
    note_turns = [i + 1 for i, t in enumerate(turns) if 'note' in t.get('input', {})]
    lo, hi = note_range(eid)
    checks['note_turns'] = note_turns
    if not (lo <= len(note_turns) <= hi):
        fails.append('note 轮数越界: %d (应%d-%d)' % (len(note_turns), lo, hi))

    # 10: Forbidden keywords
    ep_text = json.dumps(episode, ensure_ascii=False)
    found_kw = [kw for kw in FORBIDDEN_KW if kw in ep_text]
    if found_kw:
        fails.append('禁关键词命中: %s' % found_kw)
    checks['forbidden_kw'] = found_kw

    # 11: quote strict - substring match in source turn messages OR KB text
    all_texts = get_all_message_texts(episode)
    ref_turns = reference.get('turns', {})
    quote_fails = []
    ghost_kb_atoms = []
    for case_id, tref in ref_turns.items():
        for atom in tref.get('required_memory_atoms', []):
            src = atom.get('source', {})
            quote = src.get('quote', '')
            if not quote:
                continue
            sc = source_case_id(src, case_id, eid)
            if sc == '__KB__':
                kb_key = src.get('key', '')
                kb_text = kb_text_for_key(episode, kb_key)
                if not kb_text:
                    # key not in KB -> ghost atom
                    ghost_kb_atoms.append('%s/%s: KB key=%r 不存在于initial_kb' % (case_id, atom.get('id', '?'), kb_key))
                    quote_fails.append('%s/%s: quote=%r KB key=%r 不存在' % (case_id, atom.get('id', '?'), quote[:40], kb_key))
                elif quote not in kb_text:
                    quote_fails.append('%s/%s: quote=%r 不在KB[%r]文本' % (case_id, atom.get('id', '?'), quote[:40], kb_key))
            else:
                source_texts = all_texts.get(sc, [])
                if not source_texts:
                    quote_fails.append('%s/%s: source case_id=%r 不存在' % (case_id, atom.get('id', '?'), sc))
                elif not any(quote in st for st in source_texts):
                    quote_fails.append('%s/%s: quote=%r 不在%s文本' % (case_id, atom.get('id', '?'), quote[:40], sc))
    if quote_fails:
        fails.append('quote strict 失败(%d处): %s' % (len(quote_fails), '; '.join(quote_fails[:4])))
    checks['quote_fails'] = len(quote_fails)
    checks['ghost_kb_atoms'] = ghost_kb_atoms

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
            st = source_turn_num(src, case_id, eid)
            if st is None:
                continue
            if st >= cur_turn:
                req_order_fails.append('%s/%s: src_turn %d >= cur_turn %d' % (case_id, atom.get('id', '?'), st, cur_turn))
    if req_order_fails:
        fails.append('required来源不早于当前轮(%d处): %s' % (len(req_order_fails), '; '.join(req_order_fails[:3])))
    checks['req_order_fails'] = len(req_order_fails)

    # 13: runner_episode byte-identical
    ep_hash = sha256_file(os.path.join(story_dir, 'episode.json'))
    run_hash = sha256_file(os.path.join(story_dir, 'runner_episode.json'))
    if ep_hash != run_hash:
        fails.append('runner_episode hash不一致: episode=%s runner=%s' % (ep_hash[:16], run_hash[:16]))
    checks['runner_match'] = (ep_hash == run_hash)
    all_episode_hashes[eid] = ep_hash

    # 14: audit.md 8 sections
    missing_sections = [sec for sec in AUDIT_SECTIONS if sec not in audit_md]
    if missing_sections:
        fails.append('audit.md 缺节: %s' % missing_sections)
    checks['audit_sections'] = len(AUDIT_SECTIONS) - len(missing_sections)

    # 15: scene domain
    domain = exposure.get('domain', '')
    all_scene_domains[eid] = domain
    checks['domain'] = domain
    if not domain:
        fails.append('场景域缺失')

    # 16: cross-turn backref >= 2 (total count of required_this_turn atoms sourced from earlier turn)
    # Spec item 16: 跨轮回指≥2 = total backrefs >= 2.
    # Distance distribution recorded as observation (short d=1 / long d>=4) but not a separate gate.
    backrefs = []
    for case_id, tref in ref_turns.items():
        cur_turn = turn_num_from_case_id(case_id)
        if cur_turn is None:
            continue
        for atom in tref.get('required_memory_atoms', []):
            if not atom.get('required_this_turn'):
                continue
            src = atom.get('source', {})
            st = source_turn_num(src, case_id, eid)
            if st is None or st < 0:
                continue
            dist = cur_turn - st
            if dist >= 1:
                backrefs.append((cur_turn, st, dist))
    short_refs = [b for b in backrefs if b[2] == 1]
    long_refs = [b for b in backrefs if b[2] >= 4]
    checks['total_backrefs'] = len(backrefs)
    checks['short_refs'] = len(short_refs)
    checks['long_refs'] = len(long_refs)
    checks['max_backref_dist'] = max((b[2] for b in backrefs), default=0)
    if len(backrefs) < 2:
        fails.append('跨轮回指不足: 共%d个 (应>=2)' % len(backrefs))

    # T10 twin: exposure-declared turn sets match episode actual
    if exposure.get('primary_topic') == 'T10':
        exp_md = set(exposure.get('may_decline_turns', []))
        exp_intent = set(exposure.get('intent_turns', []))
        exp_note = set(exposure.get('note_turns', []))
        act_md = set('%s_T%02d' % (eid, x) for x in md_turns)
        act_intent = set('%s_T%02d' % (eid, x) for x in intent_turns)
        act_note = set('%s_T%02d' % (eid, x) for x in note_turns)
        if exp_md != act_md:
            fails.append('T10 exposure may_decline_turns不一致: exp=%s act=%s' % (sorted(exp_md), sorted(act_md)))
        if exp_intent != act_intent:
            fails.append('T10 exposure intent_turns不一致: exp=%s act=%s' % (sorted(exp_intent), sorted(act_intent)))
        if exp_note != act_note:
            fails.append('T10 exposure note_turns不一致: exp=%s act=%s' % (sorted(exp_note), sorted(act_note)))
        checks['twin_exposure_md_ok'] = (exp_md == act_md)

    # T09 date verification spot check: sample weekday claims from text vs 2026 calendar
    topic = exposure.get('primary_topic', '')
    checks['topic'] = topic
    if topic == 'T09':
        dv = exposure.get('date_verification', {})
        entries = dv.get('entries', [])
        bad_dates = []
        import datetime
        for ent in entries:
            dt = ent.get('date') or ent.get('result') or ent.get('result_min') or ent.get('date1')
            wd_claim = ent.get('weekday')
            if dt and wd_claim:
                ok = is_weekday_correct(dt, wd_claim)
                if ok is False:
                    bad_dates.append('%s: %s->%s' % (ent.get('claim', '')[:30], dt, wd_claim))
            # check arithmetic entries
            calc = ent.get('calc', '')
            if calc and '=' in calc:
                lhs = calc.split('=')[0]
                try:
                    # evaluate simple arithmetic only digits and +-*/
                    if re.match(r'^[\d+\-*/\s]+$', lhs):
                        val = eval(lhs)
                        rhs = calc.split('=')[1].strip()
                        # don't fail; just note
                except Exception:
                    pass
        if bad_dates:
            fails.append('T09日期验算错误(%d处): %s' % (len(bad_dates), '; '.join(bad_dates[:3])))
        checks['t09_date_entries'] = len(entries)
        checks['t09_bad_dates'] = len(bad_dates)

    all_results[eid] = {'pass': len(fails) == 0, 'fails': fails, 'checks': checks}

# ============================================================
# Cross-story checks
# ============================================================
# 15 cross: scene domain uniqueness (twin group sharing allowed)
# Build lineage_group_id map for domain dedup
group_of = {}
for eid in EIDS:
    r = all_results.get(eid, {})
    # need exposure for lineage_group_id
    exp_path = os.path.join(VAULT, eid, 'exposure.json')
    if os.path.exists(exp_path):
        exp = load_json(exp_path)
        group_of[eid] = exp.get('lineage_group_id', '')
    else:
        group_of[eid] = ''

domain_count = {}
for eid, d in all_scene_domains.items():
    domain_count.setdefault(d, []).append(eid)
# domain shared is OK only if same lineage_group (twin) OR both T10 twins
dup_domains = {}
for d, eids_list in domain_count.items():
    if len(eids_list) > 1 and d:
        # check if all share same lineage group -> twin shared domain (allowed)
        groups = set(group_of.get(e, '') for e in eids_list)
        if len(groups) == 1:
            continue  # twin shared domain, allowed
        dup_domains[d] = eids_list

# 17: cross-story name pollution
all_her = {}
all_me = {}
name_conflicts = []
for eid in EIDS:
    if eid in all_names:
        her, me = all_names[eid]
        if her:
            if her in all_me:
                name_conflicts.append('%s HER=%s 与 %s ME 重名' % (eid, her, all_me[her]))
            all_her.setdefault(her, []).append(eid)
        if me:
            if me in all_her:
                name_conflicts.append('%s ME=%s 与 %s HER 重名' % (eid, me, all_her[me]))
            all_me.setdefault(me, []).append(eid)

her_dup = {n: e for n, e in all_her.items() if len(e) > 1}
me_dup = {n: e for n, e in all_me.items() if len(e) > 1}
for n, e in her_dup.items():
    name_conflicts.append('HER名"%s"出现于多篇: %s' % (n, e))
for n, e in me_dup.items():
    name_conflicts.append('ME名"%s"出现于多篇: %s' % (n, e))

# Apply cross-story fails
for eid in EIDS:
    r = all_results.get(eid, {'pass': False, 'fails': ['story missing'], 'checks': {}})
    domain = all_scene_domains.get(eid, '')
    if domain and domain in dup_domains:
        others = [e for e in dup_domains[domain] if e != eid]
        r['fails'].append('场景域重复: "%s" 也出现于 %s' % (domain, others))
        r['pass'] = False
    for nc in name_conflicts:
        if eid in nc:
            r['fails'].append('跨篇名污染: %s' % nc)
            r['pass'] = False
    all_results[eid] = r

# ============================================================
# Twin isomorphism check (T10: 4 groups)
# ============================================================
TWIN_GROUPS = {
    'LG-T10-G5': ['E513', 'E514'],
    'LG-T10-G6': ['E515', 'E516'],
    'LG-T10-G7': ['E517', 'E518'],
    'LG-T10-G8': ['E519', 'E520'],
}
twin_results = {}

for gname, members in TWIN_GROUPS.items():
    if len(members) != 2:
        twin_results[gname] = {'isomorphic': False, 'reason': 'group size != 2'}
        continue
    e1, e2 = members
    ep1 = load_json(os.path.join(VAULT, e1, 'episode.json'))
    ep2 = load_json(os.path.join(VAULT, e2, 'episode.json'))
    ref1 = load_json(os.path.join(VAULT, e1, 'reference.json'))
    ref2 = load_json(os.path.join(VAULT, e2, 'reference.json'))
    exp1 = load_json(os.path.join(VAULT, e1, 'exposure.json'))
    exp2 = load_json(os.path.join(VAULT, e2, 'exposure.json'))

    diffs = []
    # same turn count
    if len(ep1['turns']) != len(ep2['turns']):
        diffs.append('turn count: %d vs %d' % (len(ep1['turns']), len(ep2['turns'])))
    # same title
    if exp1.get('title') != exp2.get('title'):
        diffs.append('title: %r vs %r' % (exp1.get('title'), exp2.get('title')))
    # same domain (allowed for twins)
    # same primary_topic
    if exp1.get('primary_topic') != exp2.get('primary_topic'):
        diffs.append('primary_topic differs')
    # same lineage_group_id
    if exp1.get('lineage_group_id') != exp2.get('lineage_group_id'):
        diffs.append('lineage_group_id differs')
    # persona traits/boundaries same (structure), names differ (expected)
    p1t = ep1['persona']['her'].get('traits')
    p2t = ep2['persona']['her'].get('traits')
    if p1t != p2t:
        diffs.append('HER traits differ')
    p1m = ep1['persona']['me'].get('traits')
    p2m = ep2['persona']['me'].get('traits')
    if p1m != p2m:
        diffs.append('ME traits differ')
    if ep1['persona'].get('relationship_stage') != ep2['persona'].get('relationship_stage'):
        diffs.append('relationship_stage differs')
    # episode hashes must differ (perturbation exists)
    if all_episode_hashes.get(e1) == all_episode_hashes.get(e2):
        diffs.append('episode identical (no perturbation)')
    # count message text diffs
    text_diff_turns = []
    for i in range(min(len(ep1['turns']), len(ep2['turns']))):
        t1 = ep1['turns'][i]
        t2 = ep2['turns'][i]
        m1 = [m.get('text', '') for m in t1.get('input', {}).get('messages', [])]
        m2 = [m.get('text', '') for m in t2.get('input', {}).get('messages', [])]
        if m1 != m2:
            text_diff_turns.append(i + 1)
    diffs.append('text_diff_turns=%s' % text_diff_turns)
    # KB diff (perturbation should affect KB if dates change)
    kb_same = (ep1.get('initial_kb') == ep2.get('initial_kb'))
    diffs.append('kb_same=%s' % kb_same)
    # reference structure isomorphic: same number of atoms per turn, same ids (modulo eid)
    ref1_turns = ref1.get('turns', {})
    ref2_turns = ref2.get('turns', {})
    if set(ref1_turns.keys()) != set(k.replace(e2, e1) for k in ref2_turns.keys()):
        diffs.append('reference turn keys mismatch')
    # check atom id counts per turn
    atom_count_diffs = []
    for cid in sorted(ref1_turns.keys()):
        cid2 = cid.replace(e1, e2)
        n1 = len(ref1_turns[cid].get('required_memory_atoms', []))
        n2 = len(ref2_turns.get(cid2, {}).get('required_memory_atoms', []))
        if n1 != n2:
            atom_count_diffs.append('%s: %d vs %d' % (cid, n1, n2))
    if atom_count_diffs:
        diffs.append('atom count diffs: %s' % atom_count_diffs[:5])
    # exposure declared turn sets should be identical (mod eid)
    def norm_set(s):
        return set(x.replace(e2, e1) for x in s)
    if norm_set(set(exp1.get('may_decline_turns', []))) != norm_set(set(exp2.get('may_decline_turns', []))):
        diffs.append('may_decline_turns (mod eid) differ')
    if norm_set(set(exp1.get('intent_turns', []))) != norm_set(set(exp2.get('intent_turns', []))):
        diffs.append('intent_turns (mod eid) differ')
    if norm_set(set(exp1.get('note_turns', []))) != norm_set(set(exp2.get('note_turns', []))):
        diffs.append('note_turns (mod eid) differ')

    # isomorphic if only diffs are expected perturbation (text_diff + kb diff)
    structural_diffs = [d for d in diffs if not d.startswith('text_diff') and not d.startswith('kb_same')]
    twin_results[gname] = {
        'members': members,
        'diffs': diffs,
        'isomorphic': len(structural_diffs) == 0,
        'structural_diffs': structural_diffs,
        'text_diff_turns': text_diff_turns,
    }

# ============================================================
output = {
    'per_story': {},
    'cross_story': {
        'dup_domains': dup_domains,
        'name_conflicts': name_conflicts,
        'her_dup': her_dup,
        'me_dup': me_dup,
        'domain_count': domain_count,
        'group_of': group_of,
    },
    'twins': twin_results,
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
    'twins_isomorphic': sum(1 for v in twin_results.values() if v['isomorphic']),
    'twins_total': len(twin_results),
}

print(json.dumps(output, ensure_ascii=False, indent=2))
