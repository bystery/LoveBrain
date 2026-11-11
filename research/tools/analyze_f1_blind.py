# -*- coding: utf-8 -*-
r"""tools/analyze_f1_blind.py — F1 盲评结果分析（零调用）。

产出：
- 控制题准确率（正反序各自）、换序一致性；
- 历史 130 对的臂胜负/打平/证据不足、与 worker 旧判的一致率（只作参考，不作金标）；
- 换序 12 对的同臂一致性；
- schema/传输失败、A/B 顺序偏差、重大错计数、延迟与 token。
"""
import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--ratings', required=True)
    ap.add_argument('--bindings', default=os.path.join(OUT, 'F1_PRIVATE_BINDINGS.json'))
    ap.add_argument('--out', default=os.path.join(OUT, 'F1_ANALYSIS.json'))
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    rows = [json.loads(l) for l in open(a.ratings, encoding='utf-8') if l.strip()]
    bind = load(a.bindings)
    by_uid = {r['unit_id']: r for r in rows}

    def verdict_arm(rec, arm_key='verdict'):
        p = rec.get('parsed') or {}
        v = p.get(arm_key)
        if v in ('A', 'B'):
            unit = bind.get(rec['unit_id']) or {}
            return unit.get('a_arm' if v == 'A' else 'b_arm')
        if v in ('tie', 'uncertain'):
            return v
        return 'missing'

    # controls
    controls = []
    for r in rows:
        if not r['unit_id'].startswith('F1C:'):
            continue
        exp = (bind.get(r['unit_id']) or {}).get('expected') or {}
        got = verdict_arm(r)
        if exp.get('verdict') in ('A', 'B'):
            unit = bind.get(r['unit_id']) or {}
            exp_arm = unit.get('a_arm' if exp['verdict'] == 'A' else 'b_arm')
            ok = got == exp_arm
        elif exp.get('verdict') == 'tie_or_uncertain':
            ok = got in ('tie', 'uncertain')
        else:
            ok = None
        controls.append({'unit_id': r['unit_id'], 'expected': exp.get('verdict'),
                         'got': got, 'ok': ok, 'schema_ok': r.get('schema_ok'),
                         'latency_ms': r.get('latency_ms')})
    ctrl_ok = sum(1 for c in controls if c['ok'])
    ctrl_total = sum(1 for c in controls if c['ok'] is not None)

    # historical
    hist = [r for r in rows if r['unit_id'].startswith('F1H:')]
    hist_rows = []
    for r in hist:
        unit = bind.get(r['unit_id']) or {}
        winner = verdict_arm(r)
        hist_rows.append({'unit_id': r['unit_id'],
                          'pair_id': unit.get('pair_id') or r.get('pair_id'),
                          'winner_arm': winner,
                          'worker': unit.get('worker_winner'),
                          'card_conflict': bool((r.get('parsed') or {}).get('card_conflict')),
                          'schema_ok': r.get('schema_ok')})

    def counts(arm_rows):
        c = {'parent': 0, 'candidate': 0, 'tie': 0, 'uncertain': 0, 'missing': 0}
        for x in arm_rows:
            c[x['winner_arm']] = c.get(x['winner_arm'], 0) + 1
        return c

    hist_counts = counts(hist_rows)
    worker_map = {'parent': 'parent', 'candidate': 'candidate',
                  'tie': 'tie', 'uncertain': 'uncertain'}

    def norm_worker(w):
        w = (w or '').strip()
        if w in ('parent', 'candidate', 'tie'):
            return w
        return None
    agree = [x for x in hist_rows if norm_worker(x['worker'])
             and x['winner_arm'] in ('parent', 'candidate', 'tie')]
    agree_n = sum(1 for x in agree if x['winner_arm'] == norm_worker(x['worker']))

    # swaps
    swaps = [r for r in rows if r['unit_id'].startswith('F1S:')]
    swap_rows = []
    for r in swaps:
        unit = bind.get(r['unit_id']) or {}
        base_uid = unit.get('swap_of')
        base = by_uid.get(base_uid)
        got = verdict_arm(r)
        base_arm = verdict_arm(base) if base else 'missing'
        swap_rows.append({'unit_id': r['unit_id'], 'base': base_uid,
                          'arm': got, 'base_arm': base_arm,
                          'consistent': got == base_arm})
    swap_cons = sum(1 for x in swap_rows if x['consistent'])

    # schema/usage
    schema_ok = sum(1 for r in rows if r.get('schema_ok'))
    transport_ok = sum(1 for r in rows if r.get('transport_ok'))
    usage_prompt = sum((r.get('usage') or {}).get('prompt') or 0 for r in rows)
    usage_completion = sum((r.get('usage') or {}).get('completion') or 0 for r in rows)
    lat = sorted(r.get('latency_ms') or 0 for r in rows)

    def pct(n, d):
        return round(100.0 * n / d, 2) if d else None
    doc = {
        'kind': 'f1_blind_analysis', 'ratings': os.path.relpath(a.ratings, ROOT)
        .replace('\\', '/'),
        'rows': len(rows),
        'schema_ok': schema_ok, 'transport_ok': transport_ok,
        'controls': {'total': len(controls), 'scored': ctrl_total,
                     'correct': ctrl_ok, 'accuracy_pct': pct(ctrl_ok, ctrl_total),
                     'rows': controls},
        'historical': {'total': len(hist_rows), 'wins': hist_counts,
                       'worker_agreement': {'n': len(agree),
                                            'agree': agree_n,
                                            'pct': pct(agree_n, len(agree))},
                       'card_conflict': sum(1 for x in hist_rows if x['card_conflict']),
                       'rows': hist_rows},
        'swaps': {'total': len(swap_rows), 'consistent': swap_cons,
                  'pct': pct(swap_cons, len(swap_rows)), 'rows': swap_rows},
        'order_effect': {
            'A_wins': sum(1 for r in rows
                          if (r.get('parsed') or {}).get('verdict') == 'A'),
            'B_wins': sum(1 for r in rows
                          if (r.get('parsed') or {}).get('verdict') == 'B'),
            'ties': sum(1 for r in rows
                        if (r.get('parsed') or {}).get('verdict') == 'tie'),
            'uncertain': sum(1 for r in rows
                             if (r.get('parsed') or {}).get('verdict') == 'uncertain'),
        },
        'usage': {'prompt_tokens': usage_prompt,
                  'completion_tokens': usage_completion},
        'latency_ms': {'p50': lat[len(lat) // 2] if lat else None,
                       'p95': lat[int(len(lat) * 0.95) - 1] if lat else None,
                       'max': max(lat) if lat else None},
    }
    out = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out)
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print('rows=%d schema_ok=%d controls=%d/%d (%s%%) hist=%s swaps=%d/%d'
          % (len(rows), schema_ok, ctrl_ok, ctrl_total,
             doc['controls']['accuracy_pct'], hist_counts, swap_cons, len(swap_rows)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
