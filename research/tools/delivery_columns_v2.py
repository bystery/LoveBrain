# -*- coding: utf-8 -*-
"""tools/delivery_columns_v2.py — 六列交付/费用口径重算（MANUAL-V1.1 §5，零网络零生成）。

相对 v1 的更正：
  P 原始协议 = 冻结评分器 json_schema_valid（含 directions=4 与必需键），只看原始字节；
  C 消费可提取 = 能否提出非空首选（含可审计键名归一）——不宣称"可发/完整交付"；
  K 程序未否决 = 无 veto；
  Q 语义/行动、W 个人愿发 = 未评/未回收一律 unknown，不填 24；
  部分交付与完整槽位交付分列；合法 null 与 no_reply 记成功动作；
  成本同时给 ÷C 与 ÷K 两个名称正确的口径，金额无单价为 null；
  记录消费器与评分器版本散列、分母定义与缺失数。
"""
import collections
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'integration_preview'))
import engine_adapter                                    # noqa: E402
from engine_adapter import OutputConsumer               # noqa: E402

BATCHES = {'v5': os.path.join(ROOT, 'logs', 'v5_ctx_live_20261025'),
           'r2': os.path.join(ROOT, 'logs', 'r2_ctx_live_20261024'),
           'v3': os.path.join(ROOT, 'logs', 'v3_deletion_live_20261024')}
ARMS = ('parent', 'candidate')
STYLE_SLOTS = ('recommended', 'bad_boy', 'playful', 'warm')


def sha(path):
    return hashlib.sha256(open(path, 'rb').read()).hexdigest()


def versions():
    return {'consumer_module': sha(engine_adapter.__file__),
            'consumer_class': 'OutputConsumer.parse+key_normalization',
            'scorer': '冻结评分器 json_schema_valid（各批 scoring.json 内原判定，不改判）'}


def full_slot_delivery(raw_obj):
    """完整槽位交付 = 四风格均有非空文本 且 directions 四项均非空（null 视为该位留空）。"""
    if not isinstance(raw_obj, dict):
        return False, 'not-object'
    resp = raw_obj.get('response')
    if not isinstance(resp, dict):
        return False, 'response-shape'
    norm = {}
    for k, v in resp.items():
        nk = engine_adapter._norm_key(k) if hasattr(engine_adapter, '_norm_key') else k
        norm[nk] = v
    missing = [s for s in STYLE_SLOTS if not (isinstance(norm.get(s), str) and norm[s].strip())]
    dirs = raw_obj.get('directions')
    if not isinstance(dirs, list) or len(dirs) != 4:
        missing.append('directions_count=%s' % (len(dirs) if isinstance(dirs, list) else type(dirs).__name__))
    else:
        missing += ['directions[%d]' % i for i, v in enumerate(dirs) if v is None or v == '']
    return (not missing), ','.join(missing)


def scan(tag, base):
    st = {a: collections.Counter() for a in ARMS}
    detail = {a: [] for a in ARMS}
    for ep in sorted(os.listdir(base)):
        out = os.path.join(base, ep, 'out')
        if not os.path.isdir(out):
            continue
        arm = ep.split('_')[0]
        if arm not in st:
            continue
        for turn in sorted(os.listdir(out)):
            d = os.path.join(out, turn)
            rp = os.path.join(d, 'reply_raw.txt')
            if not os.path.isfile(rp):
                continue
            raw = open(rp, encoding='utf-8').read()
            mf = json.load(open(os.path.join(d, 'manifest.json'), encoding='utf-8'))
            sc = json.load(open(os.path.join(d, 'scoring.json'), encoding='utf-8'))
            s = st[arm]
            s['n'] += 1
            tk = mf.get('tokens') or {}
            s['prompt'] += int(tk.get('prompt') or 0)
            s['completion'] += int(tk.get('completion') or 0)
            for k in ('cache_hit', 'cache_miss'):
                if tk.get(k) is None:
                    s['cache_unknown'] += 1
            s['lat_sum'] += int(mf.get('latency_ms') or 0)
            if raw.strip() and mf.get('mechanism_error') is None:
                s['T'] += 1
            js = [c for c in sc.get('checks', []) if c['id'] == 'json_schema_valid']
            p_ok = bool(js) and js[0]['got'] >= js[0]['pts']
            if p_ok:
                s['P'] += 1
            parsed = OutputConsumer.parse(raw)
            if parsed['slots'].get('recommended'):
                s['C'] += 1
            audits = parsed.get('key_normalization_audit') or []
            s['key_norm_events'] += len(audits)
            vetoed = bool(mf.get('veto'))
            if not vetoed and parsed['slots'].get('recommended'):
                s['K'] += 1
            full, why = full_slot_delivery(json.loads(raw) if parsed['status'] != 'unconsumable-schema' else None)
            if full:
                s['full_slot'] += 1
            else:
                s['partial_slot'] += 1
            dirs_raw = json.loads(raw).get('directions') if isinstance(
                json.loads(raw), dict) else None
            if isinstance(dirs_raw, list):
                s['direction_nulls'] += sum(1 for v in dirs_raw if v is None)
            if parsed['slots'].get('no_reply') or json.loads(raw).get('action') == 'no_reply':
                s['no_reply_success'] += 1
            if vetoed or audits or not full:
                detail[arm].append({'slot': '%s/%s/%s' % (tag, ep, turn),
                                    'veto': mf.get('veto'), 'P': p_ok,
                                    'C': bool(parsed['slots'].get('recommended')),
                                    'full_slot': full, 'partial_reason': why if not full else '',
                                    'key_norm': [a['original_repr'] for a in audits]})
    return st, detail


def block(s):
    n = max(1, s['n'])
    total = s['prompt'] + s['completion']
    return {
        'n': s['n'],
        'columns': {'T': s['T'], 'P': s['P'], 'C': s['C'], 'K': s['K'],
                    'Q': 'unknown', 'W': 'unknown'},
        'denominator_definitions': {
            'T': '账本completed且原文非空无传输错误 / n',
            'P': '冻结评分器 json_schema_valid（必需键+directions=4）原始字节 / n',
            'C': '消费器能提出非空首选 / n',
            'K': '无veto且有首选 / n',
            'Q': '需有资格评审维度，本轮未授 ⇒ unknown（不填 n）',
            'W': '用户愿发标签未回收 ⇒ unknown'},
        'rates_percent': {k: round(s[k] / n * 100, 2) for k in ('T', 'P', 'C', 'K')},
        'delivery_completeness': {'full_four_styles_four_directions': s['full_slot'],
                                  'partial_or_missing_slot': s['partial_slot']},
        'legal_blank_success_actions': {'direction_nulls': s['direction_nulls'],
                                       'no_reply_turns': s['no_reply_success']},
        'key_normalization_events': s['key_norm_events'],
        'tokens': {'prompt': s['prompt'], 'completion': s['completion'], 'total': total,
                   'cache_fields_unknown': s['cache_unknown']},
        'cost_named_two_ways': {
            'token_per_extractable_primary': (round(total / s['C'], 3) if s['C'] else None),
            'token_per_program_not_vetoed_position': (round(total / s['K'], 3) if s['K'] else None),
            'token_per_semantically_passed': 'unknown',
            'token_per_user_would_send': 'unknown'},
        'money': None,
        'money_reason': '无可核验单价；不折算金额，不把 token 降幅当金额降幅',
        'wall_completion_ms_sum': s['lat_sum'],
    }


def main():
    out = {'protocol': 'COLUMNS-V2 / MANUAL-V1.1 §5', 'real_model_calls': 0,
           'tool_versions': versions(), 'batches': {}}
    for tag, base in BATCHES.items():
        if not os.path.isdir(base):
            continue
        st, detail = scan(tag, base)
        out['batches'][tag] = {a: block(st[a]) for a in ARMS}
        out['batches'][tag]['_exception_detail'] = {a: detail[a] for a in ARMS}
    path = os.path.join(ROOT, 'evaluation', 'DELIVERY_AND_COST_COLUMNS_V2.json')
    json.dump(out, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('WROTE', path)
    for tag, blk in out['batches'].items():
        for arm in ARMS:
            b = blk[arm]
            c = b['columns']
            print('%-3s %-10s n=%2d  T=%s P=%s C=%s K=%s Q=%s W=%s  full-slot=%s  '
                  'dir-null=%d  tok/C=%s  tok/K=%s' % (
                      tag, arm, b['n'], c['T'], c['P'], c['C'], c['K'], c['Q'], c['W'],
                      b['delivery_completeness']['full_four_styles_four_directions'],
                      b['legal_blank_success_actions']['direction_nulls'],
                      b['cost_named_two_ways']['token_per_extractable_primary'],
                      b['cost_named_two_ways']['token_per_program_not_vetoed_position']))
        for arm in ARMS:
            for e in blk['_exception_detail'][arm]:
                print('    %s %s' % (arm, json.dumps(e, ensure_ascii=False)[:200]))


if __name__ == '__main__':
    main()
