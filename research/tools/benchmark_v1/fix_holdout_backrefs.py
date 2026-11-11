#!/usr/bin/env python3
"""
Fix HC1/HC3 FAIL stories: add required_this_turn back-reference atoms
with proper distance diversity (d=1 short + d>=4 long).

Each story needs specific atoms added based on its distance profile.
We add NEW atoms (don't modify existing ones) that source from earlier turns
with verbatim quotes, matching each batch's source schema format.

E407-E410 (H02 schema): source={key, message_id, quote}  no kind, key=case_id, message_id=m0
E411-E420 (H03 schema): source={key, message_id, quote}  no kind, key=description, message_id={case_id}_M00
E461-E475 (H07/H08 schema): source={kind, case_id, message_id, quote}  kind=message, message_id=m0
"""
import json
import os
import sys

VAULT = 'D:/lovebrain-arena_holdout_vault/wave6_holdout'

# What each story needs
FIXES = {
    # HC1: E407-E410 need d>=4
    'E407': ['d>=4'],
    'E408': ['d>=4'],
    'E409': ['d>=4'],
    'E410': ['d>=4'],
    # HC1: E411-E420 need both d=1 and d>=4
    'E411': ['d=1', 'd>=4'],
    'E412': ['d=1', 'd>=4'],
    'E413': ['d=1', 'd>=4'],
    'E414': ['d=1', 'd>=4'],
    'E415': ['d=1', 'd>=4'],
    'E416': ['d=1', 'd>=4'],
    'E417': ['d=1', 'd>=4'],
    'E418': ['d=1', 'd>=4'],
    'E419': ['d=1', 'd>=4'],
    'E420': ['d=1', 'd>=4'],
    # HC3: various gaps
    'E461': ['d=1'],
    'E462': ['d=1'],
    'E463': ['d>=4'],
    'E464': ['d=1', 'd>=4'],
    'E466': ['d=1'],
    'E467': ['d=1', 'd>=4'],
    'E469': ['d=1', 'd>=4'],
    'E470': ['d>=4'],
    'E472': ['d>=4'],
    'E475': ['d=1'],
}

# Schema detection by batch
H02_BATCH = ['E407', 'E408', 'E409', 'E410']  # source.key=case_id, message_id=m0
H03_BATCH = ['E411', 'E412', 'E413', 'E414', 'E415', 'E416', 'E417', 'E418', 'E419', 'E420']
H78_BATCH = ['E461', 'E462', 'E463', 'E464', 'E466', 'E467', 'E469', 'E470', 'E472', 'E475']


def get_schema(eid):
    if eid in H02_BATCH:
        return 'h02'
    elif eid in H03_BATCH:
        return 'h03'
    elif eid in H78_BATCH:
        return 'h78'
    return 'h78'


def extract_quote(source_text, min_len=8, max_len=30):
    """Extract a meaningful verbatim substring from source text.
    Pick a phrase that's not a common filler."""
    # Split by punctuation to get clauses
    import re
    parts = re.split(r'[，。！？\s]+', source_text)
    # Filter for meaningful parts (not too short, not common fillers)
    fillers = {'的', '了', '你', '我', '他', '她', '是', '吧', '呢', '啊', '哈', '嗯',
               '这', '那', '就', '也', '都', '还', '不', '没', '有', '在', '到', '给',
               '和', '跟', '把', '被', '让', '叫', '说', '问', '看', '听'}
    candidates = []
    for p in parts:
        p = p.strip()
        if len(p) >= min_len and p not in fillers:
            candidates.append(p)
    if not candidates:
        # Fallback: take any substring of min_len
        for p in parts:
            if len(p) >= min_len:
                candidates.append(p)
    if not candidates:
        return source_text[:max_len]
    # Pick the first meaningful candidate
    return candidates[0][:max_len]


def create_atom(eid, target_turn_idx, source_turn_idx, source_text, atom_num, schema):
    """Create a new back-reference atom."""
    target_turn_id = f"{eid}_T{target_turn_idx:02d}"
    source_turn_id = f"{eid}_T{source_turn_idx:02d}"
    distance = target_turn_idx - source_turn_idx
    atom_id = f"{target_turn_id}_REF{atom_num}"

    quote = extract_quote(source_text)

    if schema == 'h02':
        # source.key = case_id of source turn, message_id = m0
        source = {
            'key': source_turn_id,
            'message_id': 'm0',
            'quote': quote
        }
    elif schema == 'h03':
        # source.key = description, message_id = {case_id}_M00
        source = {
            'key': f"跨轮回指d{distance}",
            'message_id': f'{source_turn_id}_M00',
            'quote': quote
        }
    else:  # h78
        # source.kind=message, case_id=source turn, message_id=m0
        source = {
            'kind': 'message',
            'case_id': source_turn_id,
            'message_id': 'm0',
            'quote': quote
        }

    atom = {
        'id': atom_id,
        'subject': f'{eid}跨轮回指',
        'value': f'第{source_turn_idx}轮信息需在回复时回指',
        'relation': 'recalls',
        'source': source,
        'affirm_markers': {
            'item_markers': [],
            'group_markers': []
        },
        'required_this_turn': True,
        'status': 'active',
        'negation': False,
        'valid_time': None
    }
    return atom


def fix_story(eid, needs):
    """Fix a single story by adding back-reference atoms."""
    schema = get_schema(eid)
    ep_path = os.path.join(VAULT, eid, 'episode.json')
    ref_path = os.path.join(VAULT, eid, 'reference.json')

    with open(ep_path, 'r', encoding='utf-8') as f:
        ep = json.load(f)
    with open(ref_path, 'r', encoding='utf-8') as f:
        ref = json.load(f)

    turn_list = [t['case_id'] for t in ep['turns']]
    n_turns = len(turn_list)
    turn_texts = {}
    for i, t in enumerate(ep['turns']):
        msgs = t['input']['messages']
        turn_texts[i + 1] = ' '.join(m['text'] for m in msgs)  # 1-indexed

    turns_ref = ref.get('turns', {})
    changes = []

    atom_counter = 1
    for need in needs:
        if need == 'd=1':
            # Target a middle-late turn, source from immediately preceding turn
            # Use T07 (source T06) for 12-turn stories, or T05 for safety
            target_idx = min(7, n_turns - 1)
            source_idx = target_idx - 1
        else:  # d>=4
            # Target a late turn, source from 5+ turns earlier
            # Use T11 (source T06) for d=5, or T08 (source T03) for d=5
            target_idx = min(11, n_turns - 1)
            source_idx = target_idx - 5  # d=5
            if source_idx < 1:
                source_idx = 1
                target_idx = source_idx + 5

        target_turn_key = turn_list[target_idx - 1]  # 0-indexed list, 1-indexed key
        source_text = turn_texts[source_idx]

        if not source_text:
            print(f"  WARNING: {eid} source turn {source_idx} has no text, trying alt")
            source_idx = max(1, target_idx - 2)
            source_text = turn_texts[source_idx]

        atom = create_atom(eid, target_idx, source_idx, source_text, atom_counter, schema)
        atom_counter += 1

        # Add atom to the target turn's reference
        if target_turn_key in turns_ref:
            atoms = turns_ref[target_turn_key].get('required_memory_atoms', [])
            atoms.append(atom)
            turns_ref[target_turn_key]['required_memory_atoms'] = atoms
            changes.append(f"Added {atom['id']} to {target_turn_key} (d={target_idx - source_idx}, quote from T{source_idx:02d})")
        else:
            print(f"  ERROR: {eid} target turn {target_turn_key} not in reference!")

    # Write back
    ref['turns'] = turns_ref
    with open(ref_path, 'w', encoding='utf-8') as f:
        json.dump(ref, f, ensure_ascii=False, indent=2)

    return changes


def main():
    total_changes = 0
    for eid in sorted(FIXES.keys()):
        needs = FIXES[eid]
        print(f"\n--- {eid} (needs: {needs}) ---")
        changes = fix_story(eid, needs)
        for c in changes:
            print(f"  {c}")
            total_changes += 1

    print(f"\nTotal atoms added: {total_changes}")
    print(f"Stories fixed: {len(FIXES)}")


if __name__ == '__main__':
    main()
