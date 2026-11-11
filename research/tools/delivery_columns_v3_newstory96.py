# -*- coding: utf-8 -*-
"""tools/delivery_columns_v3_newstory96.py — 96批新故事（流式身份）六列口径（零网络零生成）。

与 v2 的差异（MANUAL-V1.1 §5 + mainline_review_20261026）：
  - 新批 base=logs/newstory96_live_20261026，身份=流式 include_usage（STREAM_CONFIG_IDENTITY），
    与历史非流式各批分开成列，不合并；
  - 等待列来自 calls.json[].timing：first_content_ms / first_preview_ms / completion_ms；
    first_usable_ms 本批普通链路未接消费回调 → 如实计 null（不以离线解析时间回填）；
  - 14 个 intent.status=ADJUSTED 位点单列（两臂 is_intent_effective 只认 ACTIVE，目标块未注入；
    这些位点能力可评，但不得作为"意图调整机制有效"的证据）；
  - Q/W 未评/未回收一律 unknown；成本给 ÷C 与 ÷K 两个口径，金额无单价 null。
用法：
  python tools/delivery_columns_v3_newstory96.py [--final]
  --final 要求 96 turn 目录齐全，否则退出码 2（供收口门禁）。
"""
import collections
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, 'integration_preview'))
from delivery_columns_v2 import scan, full_slot_delivery, versions          # noqa: E402
from engine_adapter import OutputConsumer                                    # noqa: E402

BASE = os.path.join(ROOT, 'logs', 'newstory96_live_20261026')
EPISODES = ['P00%d' % i for i in range(1, 7)]
ARMS = ('parent', 'candidate')
ADJUSTED_LOCI_EXPECTED = 14          # INDEPENDENT_RESULTS.enabled_but_ineffective_intents


def wait_columns():
    """逐 turn 读 calls.json[0].timing → 每臂等待分布（p50/p95/max 朴素取序）。"""
    per = {a: collections.defaultdict(list) for a in ARMS}
    null_usable = collections.Counter()
    for ep in EPISODES:
        for arm in ARMS:
            for d in sorted(glob.glob(os.path.join(BASE, '%s_%s' % (arm, ep), 'out', 'turn_*'))):
                cj = os.path.join(d, 'calls.json')
                if not os.path.isfile(cj):
                    continue
                calls = json.load(open(cj, encoding='utf-8'))
                t = (calls[0] if isinstance(calls, list) else calls).get('timing') or {}
                for k in ('first_content_ms', 'first_preview_ms', 'completion_ms'):
                    v = t.get(k)
                    if isinstance(v, (int, float)):
                        per[arm][k].append(v)
                if t.get('first_usable_ms') is None:
                    null_usable[arm] += 1
    out = {}
    for arm in ARMS:
        out[arm] = {}
        for k, vals in per[arm].items():
            if not vals:
                out[arm][k] = {'n': 0}
                continue
            s = sorted(vals)
            out[arm][k] = {'n': len(s), 'p50': s[len(s) // 2], 'p95': s[min(len(s) - 1, int(len(s) * 0.95))],
                           'max': s[-1]}
        out[arm]['first_usable_ms_null'] = null_usable[arm]
    return out


def adjusted_loci():
    """从 INDEPENDENT_RESULTS 取 14 位点并对照本批 turn 目录存在性。"""
    p = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'mainline_review_20261026',
                     'INDEPENDENT_RESULTS.json')
    d = json.load(open(p, encoding='utf-8'))
    loci = d.get('enabled_but_ineffective_intents') or []
    on_disk = {os.path.basename(x) for x in glob.glob(os.path.join(BASE, '*', 'out', 'turn_*'))}
    for x in loci:
        cid = x.get('case_id')                      # P001_T03
        x['turn_dir_present'] = any(cid in t for t in on_disk)
    return loci


def main():
    turn_dirs = glob.glob(os.path.join(BASE, '*', 'out', 'turn_*'))
    final = '--final' in sys.argv
    if final and len(turn_dirs) != 96:
        print('FINAL GATE FAIL: %d/96 turn dirs' % len(turn_dirs))
        sys.exit(2)
    st, detail = scan('newstory96_stream', BASE)
    waits = wait_columns()
    loci = adjusted_loci()
    result = {
        'doc': 'DELIVERY_AND_COST_COLUMNS_NEWSTORY96_STREAM_V1',
        'batch': 'BATCH-NEWSTORY-96-20261026',
        'identity_note': '流式 include_usage 身份，独立成列，不与历史非流式合并',
        'mode': 'final' if final else 'interim(%d turns)' % len(turn_dirs),
        'versions': versions(),
        'columns': {}, 'wait': waits, 'adjusted_intent_loci': {
            'count': len(loci),
            'expected': ADJUSTED_LOCI_EXPECTED,
            'note': 'intent.status=ADJUSTED 而两臂闸门只认 ACTIVE → 目标块未注入；'
                    '两臂共同输入缺口；这些位点不得用作意图调整机制有效的证据',
            'loci': loci},
        'problem_detail': detail,
    }
    for arm in ARMS:
        s = st[arm]
        n = s['n']
        row = {'n': n, 'T': s['T'], 'P': s['P'], 'C': s['C'], 'K': s['K'],
               'Q': 'unknown', 'W': 'unknown',
               'full_slot': s['full_slot'], 'partial_slot': s['partial_slot'],
               'no_reply_success': s['no_reply_success'],
               'tokens_prompt': s['prompt'], 'tokens_completion': s['completion'],
               'cache_unknown_calls': s['cache_unknown']}
        row['token_per_C'] = round((s['prompt'] + s['completion']) / s['C'], 1) if s['C'] else None
        row['token_per_K'] = round((s['prompt'] + s['completion']) / s['K'], 1) if s['K'] else None
        result['columns'][arm] = row
    out = os.path.join(ROOT, 'evaluation', 'DELIVERY_AND_COST_COLUMNS_NEWSTORY96_INTERIM.json'
                       if not final else 'DELIVERY_AND_COST_COLUMNS_NEWSTORY96_FINAL.json')
    json.dump(result, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('turns=%d mode=%s' % (len(turn_dirs), result['mode']))
    for arm in ARMS:
        c = result['columns'][arm]
        print('%s: n=%s T/P/C/K=%s/%s/%s/%s full=%s tok÷C=%s' %
              (arm, c['n'], c['T'], c['P'], c['C'], c['K'], c['full_slot'], c['token_per_C']))
    print('adjusted loci: %d/%d turn_dir_present=%s' %
          (len(loci), ADJUSTED_LOCI_EXPECTED, sum(1 for x in loci if x['turn_dir_present'])))
    print('wrote', out)


if __name__ == '__main__':
    main()
