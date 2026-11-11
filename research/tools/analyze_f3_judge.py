# -*- coding: utf-8 -*-
r"""tools/analyze_f3_judge.py — F3 72 对盲评与动作底账的汇总（零调用）。"""
import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')
DIMS = ['goal', 'factuality', 'attribution', 'time_state', 'boundary',
        'memory_use', 'interaction', 'initiative', 'emotion_relation']
POSITIVE = {'N01_T07', 'N01_T08', 'N01_T09', 'N01_T10', 'N01_T11', 'N01_T12',
            'N02_T07', 'N02_T08', 'N02_T09', 'N02_T10', 'N02_T11', 'N02_T12'}


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def arm_of(rec, bind, letter):
    uid = rec['unit_id']
    return (bind.get(uid) or {}).get('a_arm' if letter == 'A' else 'b_arm')


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--ratings', required=True)
    ap.add_argument('--bindings', default=os.path.join(OUT, 'F3_PRIVATE_BINDINGS.json'))
    ap.add_argument('--ledger', default=os.path.join(OUT, 'F3_ACTION_OUTCOMES_72.jsonl'))
    ap.add_argument('--out', default=os.path.join(OUT, 'F3_ANALYSIS.json'))
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    rows = [json.loads(l) for l in open(a.ratings, encoding='utf-8') if l.strip()]
    bind = load(a.bindings)
    ledger = {r['case_id']: r for r in
              (json.loads(l) for l in open(a.ledger, encoding='utf-8') if l.strip())}
    out = []
    for r in rows:
        uid = r['unit_id']
        b = bind.get(uid) or {}
        p = r.get('parsed') or {}
        v = p.get('verdict')
        winner = v if v in ('tie', 'uncertain') else (
            arm_of(r, bind, v) if v in ('A', 'B') else 'missing')
        rec = {'unit_id': uid, 'story': b.get('story'),
               'case_id': b.get('case_id'), 'verdict': v,
               'winner_arm': winner, 'schema_ok': r.get('schema_ok'),
               'card_conflict': bool(p.get('card_conflict')),
               'major_errors': {}, 'action_fit': {}, 'dims': {},
               'claims': {}}
        for letter in ('A', 'B'):
            arm = b.get('a_arm' if letter == 'A' else 'b_arm')
            aa = p.get(letter) or {}
            rec['major_errors'][arm] = aa.get('major_errors') or []
            rec['action_fit'][arm] = aa.get('action_fit')
            sc = aa.get('scores') or {}
            rec['dims'][arm] = {k: sc.get(k) for k in DIMS}
            rec['claims'][arm] = aa.get('claims') or []
        out.append(rec)

    def win_counts(records):
        c = {'parent': 0, 'candidate': 0, 'tie': 0, 'uncertain': 0, 'missing': 0}
        for x in records:
            c[x['winner_arm']] = c.get(x['winner_arm'], 0) + 1
        return c

    def dim_means(records):
        res = {}
        for arm in ('parent', 'candidate'):
            vals = {k: [] for k in DIMS}
            for x in records:
                for k, v in x['dims'].get(arm, {}).items():
                    if isinstance(v, (int, float)):
                        vals[k].append(v)
            res[arm] = {k: (round(sum(v) / len(v), 3) if v else None)
                        for k, v in vals.items()}
        return res

    stories = {}
    for sid in ('N01', 'N02', 'K01', 'K02'):
        rs = [x for x in out if x['story'] == sid]
        stories[sid] = {'pairs': len(rs), 'wins': win_counts(rs),
                        'dims': dim_means(rs),
                        'major_errors': {
                            arm: sum(len(x['major_errors'].get(arm) or [])
                                     for x in rs)
                            for arm in ('parent', 'candidate')},
                        'action_fit': {
                            arm: {k: sum(1 for x in rs
                                         if x['action_fit'].get(arm) == k)
                                  for k in ('appropriate', 'acceptable',
                                            'bad', 'uncertain', None)}
                            for arm in ('parent', 'candidate')}}
    positive = [x for x in out if x['case_id'] in POSITIVE]
    knowledge = [x for x in out if x['story'] in ('K01', 'K02')
                 and int(x['case_id'].rsplit('_T', 1)[1]) >= 13]
    doc = {
        'kind': 'f3_analysis', 'ratings': os.path.relpath(a.ratings, ROOT)
        .replace('\\', '/'),
        'rows': len(out), 'schema_ok': sum(1 for x in out if x['schema_ok']),
        'overall_wins': win_counts(out),
        'stories': stories,
        'positive_slice': {'n': len(positive), 'wins': win_counts(positive),
                           'dims': dim_means(positive),
                           'major_errors': {arm: sum(len(x['major_errors'].get(arm) or [])
                                                     for x in positive)
                                            for arm in ('parent', 'candidate')}},
        'knowledge_slice': {'n': len(knowledge), 'wins': win_counts(knowledge),
                            'dims': dim_means(knowledge),
                            'major_errors': {arm: sum(len(x['major_errors'].get(arm) or [])
                                                      for x in knowledge)
                                             for arm in ('parent', 'candidate')}},
        'card_conflicts': sum(1 for x in out if x['card_conflict']),
        'rows_detail': out,
        'action_ledger_summary': os.path.relpath(a.ledger, ROOT)
        .replace('\\', '/'),
    }
    op = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out)
    with open(op, 'w', encoding='utf-8') as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print(json.dumps({'rows': len(out), 'overall': doc['overall_wins'],
                      'positive': doc['positive_slice']['wins'],
                      'knowledge': doc['knowledge_slice']['wins']},
                     ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())
