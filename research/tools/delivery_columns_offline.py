# -*- coding: utf-8 -*-
"""tools/delivery_columns_offline.py — 交付可靠性四列 + 费用口径（离线重算，真实调用 0）。

宪法口径（AGENTS §完整性能与花费）：传输成功 ≠ 原始协议通过 ≠ 消费后真正可用 ≠ 语义/口味可发。
本工具只算前两列+消费列+程序级语义列，第四列"用户愿不愿原样发"永远不由脚本给。

四列定义
  T  传输成功      —— attempt_completed 且原文非空、无传输错误
  P  原始协议通过  —— 冻结评分器 json_schema_valid 通过（严格按模型原始字节，不做任何容忍）
  C  消费后通过    —— 用接入原型的 OutputConsumer（含可审计键名归一）拿到非空可发送首选
  S  程序级语义可用 —— 无 veto 且首选非空（不代表用户愿发，也不代表人工语义判定通过）

费用：只用真实 api usage；cache 未知就写未知；无可核验单价 ⇒ 金额一律 null，不填零不估。
输出 evaluation/DELIVERY_AND_COST_COLUMNS_V1.json + 终端摘要。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'integration_preview'))
from engine_adapter import OutputConsumer  # noqa: E402

BATCHES = {
    'v5': os.path.join(ROOT, 'logs', 'v5_ctx_live_20261025'),
    'r2': os.path.join(ROOT, 'logs', 'r2_ctx_live_20261024'),
}
ARMS = ('parent', 'candidate')


def scan(tag, base):
    per = {a: {'T': 0, 'P': 0, 'C': 0, 'S': 0, 'n': 0, 'norm_slots': [],
               'failures': []} for a in ARMS}
    usage = {a: {'prompt': 0, 'completion': 0, 'cache_hit': 0, 'cache_miss': 0,
                'unknown_usage': 0, 'calls': 0} for a in ARMS}
    for ep in sorted(os.listdir(base)):
        out = os.path.join(base, ep, 'out')
        if not os.path.isdir(out):
            continue
        for turn in sorted(os.listdir(out)):
            d = os.path.join(out, turn)
            raw_p = os.path.join(d, 'reply_raw.txt')
            if not os.path.isfile(raw_p):
                continue
            arm = ep.split('_')[0]
            if arm not in ARMS:
                continue
            raw = open(raw_p, encoding='utf-8').read()
            mf = json.load(open(os.path.join(d, 'manifest.json'), encoding='utf-8'))
            sc = json.load(open(os.path.join(d, 'scoring.json'), encoding='utf-8'))
            slot = '%s/%s/%s' % (tag, ep, turn)
            st = per[arm]
            st['n'] += 1
            tk = mf.get('tokens') or {}
            usage[arm]['calls'] += int(tk.get('n_calls') or 0)
            for k in ('prompt', 'completion'):
                usage[arm][k] += int(tk.get(k) or 0)
            for k in ('cache_hit', 'cache_miss'):
                if tk.get(k) is None:
                    usage[arm]['unknown_usage'] += 1
                else:
                    usage[arm][k] += int(tk[k])
            # T 传输
            if raw.strip() and mf.get('mechanism_error') is None:
                st['T'] += 1
            # P 原始协议（不容忍）
            js = [c for c in sc.get('checks', []) if c['id'] == 'json_schema_valid']
            p_ok = bool(js) and js[0]['got'] >= js[0]['pts']
            if p_ok:
                st['P'] += 1
            else:
                st['failures'].append({'slot': slot, 'column': 'P', 'veto': mf.get('veto')})
            # C 消费后（用生产侧同一套消费适配）
            parsed = OutputConsumer.parse(raw)
            if parsed['slots'].get('recommended'):
                st['C'] += 1
            else:
                st['failures'].append({'slot': slot, 'column': 'C',
                                       'status': parsed['status'],
                                       'reason': parsed['reason']})
            for a in parsed.get('key_normalization_audit') or []:
                st['norm_slots'].append({'slot': slot, 'original_repr': a['original_repr'],
                                         'normalized': a['normalized'],
                                         'slot_affected': a['slot_affected']})
            # S 程序级语义
            if not mf.get('veto') and parsed['slots'].get('recommended'):
                st['S'] += 1
            elif mf.get('veto'):
                st['failures'].append({'slot': slot, 'column': 'S', 'veto': mf['veto']})
    return per, usage


def main():
    out = {'real_model_calls': 0, 'unit_price_evidence': None, 'money': None,
           'definitions': {'T': '传输成功', 'P': '原始协议通过(不容忍)',
                           'C': '消费后拿到非空可发送首选', 'S': '程序级语义可用(无veto)',
                           'W': '用户原样愿发——不由脚本判定'},
           'batches': {}}
    for tag, base in BATCHES.items():
        if not os.path.isdir(base):
            continue
        per, usage = scan(tag, base)
        blk = {}
        for arm in ARMS:
            st = per[arm]
            n = max(1, st['n'])
            u = usage[arm]
            total = u['prompt'] + u['completion']
            blk[arm] = {
                'n': st['n'], 'T': st['T'], 'P': st['P'], 'C': st['C'], 'S': st['S'],
                'rates_percent': {'T': round(st['T'] / n * 100, 2),
                                  'P': round(st['P'] / n * 100, 2),
                                  'C': round(st['C'] / n * 100, 2),
                                  'S': round(st['S'] / n * 100, 2)},
                'tokens': {'prompt': u['prompt'], 'completion': u['completion'],
                           'total': total, 'calls': u['calls'],
                           'cache_fields_unknown': u['unknown_usage']},
                'tokens_per_usable_result': (round(total / st['S'], 1) if st['S'] else None),
                'key_normalization_applied': st['norm_slots'],
                'failures': st['failures'],
            }
        out['batches'][tag] = blk
    path = os.path.join(ROOT, 'evaluation', 'DELIVERY_AND_COST_COLUMNS_V1.json')
    json.dump(out, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('WROTE', path)
    for tag, blk in out['batches'].items():
        for arm, b in blk.items():
            print('%-4s %-10s n=%d  T=%d P=%d C=%d S=%d  tok=%d  tok/usable=%s  归一次数=%d'
                  % (tag, arm, b['n'], b['T'], b['P'], b['C'], b['S'],
                     b['tokens']['total'], b['tokens_per_usable_result'],
                     len(b['key_normalization_applied'])))
            for f in b['failures']:
                print('      失败 %s' % json.dumps(f, ensure_ascii=False)[:170])


if __name__ == '__main__':
    main()
