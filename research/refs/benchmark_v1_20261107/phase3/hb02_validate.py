#!/usr/bin/env python -I
"""Gold annotation self-check for HOLDOUT HB02 (ANNOTATOR-HB02).
Usage: python -I hb02_validate.py <EID> [<EID> ...]
Checks: quote strict hit in episode, may_decline consistency, required source earlier turn,
t02_signal_gate nine fields present, audit.md 8 sections, runner_episode byte-identical.
Stdlib only.
"""
import json, sys, os, re, hashlib

VAULT = r'D:/lovebrain-arena_holdout_vault/wave6_holdout'

def load(p):
    with open(p, encoding='utf-8') as f:
        return f.read()

def msg_text(turn_msgs, cid, mid):
    # mid like 'E411_T01_M00' or 'm0'
    m = re.search(r'M(\d+)$', mid) if not mid.startswith('m') else None
    idx = int(m.group(1)) if m else int(mid[1:])
    return turn_msgs[cid][idx]

def check(eid):
    d = os.path.join(VAULT, eid)
    ep_path = os.path.join(d, 'episode.json')
    ref_path = os.path.join(d, 'reference.json')
    aud_path = os.path.join(d, 'audit.md')
    run_path = os.path.join(d, 'runner_episode.json')
    print(f'=== {eid} ===')
    if not os.path.exists(ep_path):
        print('  MISSING episode.json'); return False
    ep = json.loads(load(ep_path))
    turn_msgs = {t['case_id']: [m['text'] for m in t['input'].get('messages', [])] for t in ep['turns']}
    ep_may = {t['case_id']: t['input'].get('may_decline', False) for t in ep['turns']}
    ok_all = True
    if not os.path.exists(ref_path):
        print('  MISSING reference.json'); return False
    ref = json.loads(load(ref_path))
    atom_count = 0; required_count = 0
    quote_fail = []
    for cid, card in ref.get('turns', {}).items():
        for a in card.get('required_memory_atoms', []):
            atom_count += 1
            src = a.get('source', {}); q = src.get('quote', ''); mid = src.get('message_id', '')
            sm = re.match(r'(E\d+_T\d+)_M\d+', mid)
            src_cid = sm.group(1) if sm else cid
            if src_cid not in turn_msgs:
                quote_fail.append((a['id'], 'source turn not in episode', mid)); ok_all=False; continue
            try:
                txt = msg_text(turn_msgs, src_cid, mid)
            except Exception as e:
                quote_fail.append((a['id'], 'msg lookup fail '+str(e), mid)); ok_all=False; continue
            if q not in txt:
                quote_fail.append((a['id'], 'quote NOT strict', q[:30])); ok_all=False
            if a.get('required_this_turn'):
                required_count += 1
                cur = int(cid.split('_T')[1]); s = int(src_cid.split('_T')[1])
                if s >= cur:
                    quote_fail.append((a['id'], 'required sourced from current/later', src_cid+'>='+cid)); ok_all=False
    may_fail = []
    for cid, card in ref.get('turns', {}).items():
        if card.get('may_decline') != ep_may.get(cid):
            may_fail.append((cid, 'ref='+str(card.get('may_decline'))+' ep='+str(ep_may.get(cid)))); ok_all=False
    gate = ref.get('t02_signal_gate', {})
    gate_fields = ['signal_rounds','anti_temptation','intensity_gradient','forbidden_keywords','cross_ref','may_decline','intent','scene_unique','third_party']
    missing_gate = [f for f in gate_fields if f not in gate]
    if missing_gate: ok_all=False
    sec_fail = []
    if os.path.exists(aud_path):
        aud = load(aud_path)
        secs = re.findall(r'^## ', aud, re.M)
        if len(secs) != 8:
            sec_fail.append('sections='+str(len(secs))+' (need 8)'); ok_all=False
        need = ['## 0. 性质','## 1. 生成信息','## 2. 连续性','## 3. ME/HER 状态','## 4. no_reply / 媒体局限','## 5. 已知','## 6. 参与者注释','## 7. 出题痕迹检查']
        for n in need:
            if n not in aud:
                sec_fail.append('missing: '+n); ok_all=False
    else:
        sec_fail.append('audit.md MISSING'); ok_all=False
    runner_ok = 'N/A'
    if os.path.exists(run_path):
        h1 = hashlib.sha256(open(ep_path,'rb').read()).hexdigest()
        h2 = hashlib.sha256(open(run_path,'rb').read()).hexdigest()
        runner_ok = (h1==h2)
        if not runner_ok: ok_all=False
    else:
        runner_ok='MISSING'; ok_all=False
    print('  atoms='+str(atom_count)+' required='+str(required_count))
    print('  quote_strict_fail='+str(len(quote_fail)))
    for f in quote_fail: print('    FAIL '+str(f))
    print('  may_decline_fail='+str(len(may_fail)))
    for f in may_fail: print('    FAIL '+str(f))
    print('  gate_missing='+str(missing_gate))
    print('  audit_fail='+str(sec_fail))
    print('  runner_byte_identical='+str(runner_ok))
    print('  ALL_OK='+str(ok_all))
    return ok_all

if __name__ == '__main__':
    args = sys.argv[1:]
    if not args:
        print('usage: hb02_validate.py <EID> [...]'); sys.exit(2)
    allok = all(check(e) for e in args)
    print('=== done all_ok='+str(allok)+' ===')
    sys.exit(0 if allok else 1)
