# -*- coding: utf-8 -*-
r"""tools/build_f4_blind_materials.py — 把 F4 的 12 个组件对做成盲评材料。"""
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
import build_f3_blind_materials as b3  # noqa: E402

MAT = os.path.join(ROOT, 'benchmark', 'stories_v2', 'final_20261029')
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def main(argv=None):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--result', required=True)
    ap.add_argument('--outdir', default=OUT)
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    rs = load(a.result)['results']
    rs = [r for r in rs if r.get('status') == 'ok']
    if len(rs) != 12:
        raise SystemExit('expected 12 ok results, got %d' % len(rs))
    probes = {p['probe_id']: p for p in load(os.path.join(
        MAT, 'f4_probes', 'PROBE_MANIFEST.json'))['probes']}
    items, bind = [], {}
    for i, r in enumerate(rs):
        probe = probes[r['probe_id']]
        sid = probe['story']
        ep = load(os.path.join(ROOT, probe['episode']))
        idx = len(ep['turns']) - 1
        ctx = b3.context_for(ep, idx)
        card = b3.card_for(load(os.path.join(MAT, 'f4_probes', r['probe_id'],
                                             'reference.json'))['turns'][r['probe_id']])
        on_ans = r['on_answer']
        off_ans = r['off_answer']
        on_a = (i % 2 == 0)
        a_ans, b_ans = (on_ans, off_ans) if on_a else (off_ans, on_ans)
        uid = 'F4:%s' % r['probe_id']
        items.append({'unit_id': uid, 'kind': 'component',
                      'pair_id': r['probe_id'],
                      'payload': {'unit_id': uid, 'context': ctx, 'card': card,
                                  'answers': {'A': a_ans, 'B': b_ans},
                                  'task': ('A/B 是同一 harness 的两次生成：一次保留被测组件，'
                                           '一次关闭该组件。按九维打分并判 A/B/tie/uncertain；'
                                           '不猜版本，不预测用户愿不愿发。')}})
        bind[uid] = {'channel': r['channel'], 'story': sid, 'case_id': r['probe_id'],
                     'a_side': 'on' if on_a else 'off',
                     'b_side': 'off' if on_a else 'on',
                     'signal': r.get('signal'),
                     'removed_blocks': r.get('removed_blocks'),
                     'on_prompt_sha256': r.get('on_prompt_sha256'),
                     'off_prompt_sha256': r.get('off_prompt_sha256')}
    outdir = a.outdir if os.path.isabs(a.outdir) else os.path.join(ROOT, a.outdir)
    os.makedirs(outdir, exist_ok=True)
    mp = os.path.join(outdir, 'F4_MATERIALS.jsonl')
    bp = os.path.join(outdir, 'F4_PRIVATE_BINDINGS.json')
    with open(mp, 'w', encoding='utf-8') as f:
        for x in items:
            f.write(json.dumps(x, ensure_ascii=False, sort_keys=True) + '\n')
    with open(bp, 'w', encoding='utf-8') as f:
        json.dump(bind, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print('items=%d materials_sha=%s' % (len(items), sha(open(mp, encoding='utf-8').read())))
    print('on_A=%d off_A=%d' % (sum(1 for v in bind.values() if v['a_side'] == 'on'),
                                sum(1 for v in bind.values() if v['a_side'] == 'off')))
    return 0


if __name__ == '__main__':
    sys.exit(main())
