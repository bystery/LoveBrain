# -*- coding: utf-8 -*-
r"""tools/f3_action_ledger.py — F3 新材料 72 对的动作层底账（零调用）。

分类语义与 F0 的 ACTION_OUTCOMES_130 一致：replied / no_reply / format_undeliverable /
no_new_her_no_reply / legit_no_reply / optional_no_reply / required_reply_missing /
reply_without_new_her；卡片冲突单列，不混入候选胜负。
"""
import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
import f0_action_ledger_20261029 as f0  # noqa: E402

MAT = os.path.join(ROOT, 'benchmark', 'stories_v2', 'final_20261029')
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')
STORIES = ['N01', 'N02', 'K01', 'K02']


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--run-base', required=True)
    ap.add_argument('--stories', default=','.join(STORIES))
    ap.add_argument('--outdir', default=OUT)
    a = ap.parse_args(argv)
    stories = [s.strip() for s in a.stories.split(',') if s.strip()]
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    base = a.run_base if os.path.isabs(a.run_base) else os.path.join(ROOT, a.run_base)
    cards = {c['case_id']: c for c in load(os.path.join(
        MAT, 'GRADING_CARDS_N01_N02_K01_K02.json'))['cards']}
    rows = []
    for sid in stories:
        ep = load(os.path.join(ROOT, 'benchmark', 'episodes', '%s.json' % sid))
        for i, t in enumerate(ep['turns']):
            cid = t['case_id']
            suffix = '_resume' if (i + 1 > 12 and len(ep['turns']) > 12) else ''
            sides = {}
            for arm in ('parent', 'candidate'):
                td = os.path.join(base, '%s_%s%s' % (arm, sid, suffix),
                                  'turn_%02d_%s' % (i + 1, cid))
                sides[arm] = f0.side_action_record(td)
            inp = t.get('input') or {}
            msgs = inp.get('messages') or []
            last = msgs[-1] if msgs else {'role': None, 'text': ''}
            f0._LAST_HER['text'] = '\n'.join(m.get('text', '') for m in msgs
                                             if m.get('role') == 'HER')
            rec = {'pair_id': 'F3:%s' % cid, 'story': sid, 'case_id': cid,
                   'turn': i + 1, 'last_role': last.get('role'),
                   'may_decline': bool(inp.get('may_decline')),
                   'card_conflict_flags': f0.detect_card_conflict(
                       cards[cid], last.get('role'), last.get('text', '')),
                   'sides': {}}
            for arm in ('parent', 'candidate'):
                side = sides[arm]
                cls, reason = f0.classify_side(
                    side, last.get('role'), bool(inp.get('may_decline')), cards[cid])
                rec['sides'][arm] = {
                    'action_class': cls, 'reason': reason,
                    'text': side.get('text'), 'raw_action': side.get('raw_action_field'),
                    'parse_ok': side.get('parse_ok'), 'score10': side.get('score10'),
                    'veto': side.get('veto'), 'run_id': side.get('run_id'),
                    'raw_sha256': side.get('reply_raw_sha256')}
            rows.append(rec)
    outdir = a.outdir if os.path.isabs(a.outdir) else os.path.join(ROOT, a.outdir)
    os.makedirs(outdir, exist_ok=True)
    op = os.path.join(outdir, 'F3_ACTION_OUTCOMES_72.jsonl')
    with open(op, 'w', encoding='utf-8') as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + '\n')
    summary = {'pairs': len(rows), 'by_arm': {}, 'card_conflicts': 0,
               'by_story': {}}
    for arm in ('parent', 'candidate'):
        c = {}
        for r in rows:
            k = r['sides'][arm]['action_class']
            c[k] = c.get(k, 0) + 1
        summary['by_arm'][arm] = c
    for r in rows:
        summary['card_conflicts'] += 1 if r['card_conflict_flags'] else 0
        st = summary['by_story'].setdefault(r['story'], {'pairs': 0,
                                                         'parent': {}, 'candidate': {}})
        st['pairs'] += 1
        for arm in ('parent', 'candidate'):
            k = r['sides'][arm]['action_class']
            st[arm][k] = st[arm].get(k, 0) + 1
    summary_path = os.path.join(outdir, 'F3_ACTION_OUTCOMES_72_SUMMARY.json')
    with open(summary_path, 'w', encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print('pairs=%d conflicts=%d' % (len(rows), summary['card_conflicts']))
    print(json.dumps(summary['by_arm'], ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())
