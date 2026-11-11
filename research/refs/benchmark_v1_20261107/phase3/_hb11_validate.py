#!/usr/bin/env python -I
"""Reusable gold annotation validator+fixer for HOLDOUT episodes.
Usage: python -I validate_fix_gold_hb11.py <EID> [--fix]
Validates: quote strict, may_decline, required source<current, protected_expressions,
audit 8 sections, runner hash, status field presence.
With --fix: adds missing status fields from relation mapping.
"""
import json, hashlib, sys, os

VAULT = "D:/lovebrain-arena_holdout_vault/wave6_holdout"
RELATION_TO_STATUS = {
    'affirms': 'asserted',
    'superseded': 'superseded',
    'negates': 'cancelled',
    'unknown': 'unknown',
}

def validate(eid, fix=False):
    ep_path = f"{VAULT}/{eid}/episode.json"
    ref_path = f"{VAULT}/{eid}/reference.json"
    audit_path = f"{VAULT}/{eid}/audit.md"
    runner_path = f"{VAULT}/{eid}/runner_episode.json"

    ep = json.load(open(ep_path, encoding='utf-8'))
    ref = json.load(open(ref_path, encoding='utf-8'))

    msg_texts = {}
    case_order = []
    for turn in ep['turns']:
        msg_texts[turn['case_id']] = [m['text'] for m in turn['input']['messages']]
        case_order.append(turn['case_id'])

    # Fix missing status
    fix_count = 0
    if fix:
        for case_id, tg in ref['turns'].items():
            for atom in tg.get('required_memory_atoms', []):
                if 'status' not in atom:
                    rel = atom.get('relation', 'affirms')
                    atom['status'] = RELATION_TO_STATUS.get(rel, 'asserted')
                    fix_count += 1
        if fix_count > 0:
            with open(ref_path, 'w', encoding='utf-8') as f:
                json.dump(ref, f, ensure_ascii=False, indent=1)

    errors = []
    atom_count = 0; req_count = 0; sup_count = 0; tent_count = 0

    for case_id, tg in ref['turns'].items():
        if case_id != tg.get('case_id', case_id):
            errors.append(f'{case_id}: case_id mismatch')
        for atom in tg.get('required_memory_atoms', []):
            atom_count += 1
            if atom.get('required_this_turn'): req_count += 1
            st = atom.get('status', 'MISSING')
            if st == 'superseded': sup_count += 1
            if st == 'tentative': tent_count += 1
            if st == 'MISSING':
                errors.append(f'{atom["id"]}: status MISSING')
                continue

            src = atom['source']
            src_key = src['key']
            quote = src['quote']
            msg_id = src['message_id']

            if src_key not in msg_texts:
                errors.append(f'{atom["id"]}: source {src_key} not in episode')
                continue

            texts = msg_texts[src_key]
            idx = int(msg_id[1:])
            found = False; hit_idx = -1
            for i, t in enumerate(texts):
                if quote in t:
                    found = True; hit_idx = i; break
            if not found:
                errors.append(f'{atom["id"]}: quote NOT FOUND in {src_key}')
            elif hit_idx != idx:
                errors.append(f'{atom["id"]}: msg_id={msg_id} but hit[{hit_idx}]')

            if atom.get('required_this_turn'):
                src_t = int(src_key.split('_T')[1])
                cur_t = int(case_id.split('_T')[1])
                if src_t >= cur_t:
                    errors.append(f'{atom["id"]}: required but src T{src_t} >= cur T{cur_t}')

    # may_decline
    md_err = []
    for turn in ep['turns']:
        cid = turn['case_id']
        ep_md = turn['input'].get('may_decline', False)
        ref_md = ref['turns'][cid].get('may_decline', False)
        if ep_md != ref_md:
            md_err.append(f'{cid}: ep={ep_md} ref={ref_md}')

    # protected_expressions
    pe_err = []
    for case_id, tg in ref['turns'].items():
        ti = case_order.index(case_id)
        her_text = ' '.join([m['text'] for m in ep['turns'][ti]['input']['messages'] if m['role'] == 'HER'])
        for pe in tg.get('protected_expressions', []):
            if pe not in her_text:
                pe_err.append(f'{case_id}: PE "{pe[:20]}" not found')

    # audit sections
    sec_count = 0
    if os.path.exists(audit_path):
        with open(audit_path, encoding='utf-8') as f:
            secs = [l for l in f.read().split('\n') if l.startswith('## ')]
            sec_count = len(secs)

    # runner hash
    hash_match = False
    if os.path.exists(runner_path):
        eh = hashlib.sha256(open(ep_path, 'rb').read()).hexdigest()
        rh = hashlib.sha256(open(runner_path, 'rb').read()).hexdigest()
        hash_match = (eh == rh)

    print(f'=== {eid} === Atoms:{atom_count} Req:{req_count} Sup:{sup_count} Tent:{tent_count}')
    print(f'  QuoteErr:{len(errors)} MDErr:{len(md_err)} PEErr:{len(pe_err)} Sections:{sec_count} Hash:{hash_match} Turns:{len(ref["turns"])}/{len(ep["turns"])}')
    if fix_count: print(f'  Fixed {fix_count} missing status fields')
    if errors:
        for e in errors[:10]: print(f'  ERR: {e}')
    if md_err:
        for e in md_err: print(f'  MD: {e}')
    if pe_err:
        for e in pe_err: print(f'  PE: {e}')
    all_ok = len(errors)==0 and len(md_err)==0 and len(pe_err)==0 and sec_count==8 and hash_match
    print(f'  RESULT: {"PASS" if all_ok else "FAIL"}')
    return all_ok

if __name__ == '__main__':
    eid = sys.argv[1]
    fix = '--fix' in sys.argv
    validate(eid, fix)
