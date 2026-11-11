#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""W22FULL gate extraction. Semantics (run_lp_review_generic.py):
- non-swap positions: one main-order verdict (dims+severe).
- swap positions (T08/T16/T20/T24): swap-order verdict (dims+severe, arm-normalized)
  + supplement main-order winner (no dims/severe).
G2 dims use main-order verdicts only. G5 = supplement agree field.
Severe ledger merges judge flags with B3 override ledger."""
import json, sys, io, collections
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

PARTIAL = 'refs/experiment_protocol_20261023/long_probe_20261030/LP_W22FULL_PARTIAL.jsonl'
B3 = 'logs/LP_W22FULL_B3_READRESULT.json'
OUT = 'refs/experiment_protocol_20261023/long_probe_20261030/W22FULL_GATES_20261104.json'

STORY = lambda pos: pos.split('_T')[0]
SWAP_SUFFIX = {"T08", "T16", "T20", "T24"}

verdicts = {}      # (pair_id, order) -> verdict
supplements = {}   # pair_id -> supplement rec
total_lines = 0
with open(PARTIAL, encoding='utf-8') as f:
    for line in f:
        total_lines += 1
        r = json.loads(line)
        if r.get('kind') == 'supplement':
            supplements[r['pair_id']] = r
        elif r.get('kind') == 'verdict':
            verdicts[(r['pair_id'], r.get('order', 'main'))] = r['verdict']

positions = set(p for p, _ in verdicts) | set(supplements)
b3 = {p['pos']: p for p in json.load(open(B3, encoding='utf-8'))['positions']}

# ---- severe ledger (B3 override) ----
severe = collections.defaultdict(collections.Counter)
fam = collections.defaultdict(collections.Counter)
detail = []
for pid in sorted(positions):
    pos = pid.split(':')[-1]
    vs = [v for (p, o), v in verdicts.items() if p == pid]
    jc = any(v['severe'].get('candidate') for v in vs)
    jp = any(v['severe'].get('parent') for v in vs)
    b = b3.get(pos)
    if b:
        cc = b['b3'] == 'CONFIRMED' and b['judge'] == 'cand'
        pc = b['b3'] == 'CONFIRMED' and b['judge'] == 'par'
        verdict = 'B3:' + b['b3']
        f = b.get('family') or 'other'
    else:
        cc, pc, verdict, f = jc, jp, 'judge-only', 'other'
    if cc: severe[STORY(pos)]['cand'] += 1; fam[f]['cand'] += 1
    if pc: severe[STORY(pos)]['par'] += 1; fam[f]['par'] += 1
    detail.append({'pos': pos, 'judge_cand': jc, 'judge_par': jp, 'b3': verdict,
                   'family': f if (cc or pc) else None, 'severe_cand': cc, 'severe_par': pc})

# ---- G2: main-order dims (non-swap positions) ----
dims = ['goal', 'fact', 'attrib', 'time', 'boundary', 'memory', 'interact', 'init', 'emotion']
deltas = {d: [] for d in dims}
wins = collections.Counter()
for (pid, order), v in verdicts.items():
    if order != 'main':
        continue
    dc, dp = v['dims']['candidate'], v['dims']['parent']
    w = v.get('winner_cand_view')
    wins[w] += 1
    for d in dims:
        if dc.get(d) is not None and dp.get(d) is not None:
            deltas[d].append(dc[d] - dp[d])
# swap positions: main winner from supplement
for pid, s in supplements.items():
    wins[s.get('main_winner')] += 1
g2 = {d: {'n': len(v), 'mean': round(sum(v) / len(v), 3) if v else None,
          'min': min(v) if v else None} for d, v in deltas.items()}

# ---- G5: swap flips ----
agree = sum(1 for s in supplements.values() if s.get('agree'))
disagree = [s['pair_id'] for s in supplements.values() if not s.get('agree')]

out = {
    'review_lines': total_lines,
    'positions_main_verdicts': sum(1 for (p, o) in verdicts if o == 'main'),
    'positions_swap_verdicts': sum(1 for (p, o) in verdicts if o == 'swap'),
    'supplements': len(supplements),
    'severe_by_story': {k: dict(v) for k, v in sorted(severe.items())},
    'severe_families': {k: dict(v) for k, v in sorted(fam.items())},
    'severe_total': {'cand': sum(v['cand'] for v in severe.values()),
                     'par': sum(v['par'] for v in severe.values())},
    'g2_dims_main_only': g2, 'main_wins': dict(wins),
    'g5_swap': {'agree': agree, 'disagree': len(disagree), 'disagree_list': disagree},
    'detail': detail,
}
json.dump(out, open(OUT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(json.dumps({k: v for k, v in out.items() if k != 'detail'}, ensure_ascii=False, indent=1))
