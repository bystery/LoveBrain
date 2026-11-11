# -*- coding: utf-8 -*-
r"""tools/analyze_f5_judge.py — F5 同臂重抽盲评聚合（含顺序复测一致性）。

输入：正向材料绑定 + 正向评分 +（可选）互换顺序绑定 + 互换评分。
输出：
  - 双臂胜负（frozen / repaired / tie / uncertain）
  - 两次顺序的逐位一致性（同结论比例，位置偏好证据）
  - format_fail 位的处理是否符合预期（不可交付侧应判负）
  - 九维均分、major_errors 计数、card_conflict、schema/transport 完整率
注意：A/B 是同一 harness 身份的两次独立抽样，本表回答"重跑有没有系统性变差"，
      不是组件收益；自动分只作复核线索，不作最终裁决。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/analyze_f5_judge.py \
    --bindings refs/.../F5_PRIVATE_BINDINGS.json --ratings logs/f5_judge_.../F5_RATINGS.jsonl \
    --bindings-swap refs/.../F5_PRIVATE_BINDINGS_SWAP.json --ratings-swap logs/f5_judge_swap_.../F5_RATINGS.jsonl \
    --out refs/.../F5_ANALYSIS.json
"""
import argparse
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIMS = ('goal', 'factuality', 'attribution', 'time_state', 'boundary',
        'memory_use', 'interaction', 'initiative', 'emotion_relation')


def load_rows(p):
    return [json.loads(l) for l in open(p, encoding='utf-8') if l.strip()]


def arm_of(bind, verdict):
    if verdict == 'A':
        return bind['a_arm']
    if verdict == 'B':
        return bind['b_arm']
    return verdict          # tie / uncertain


def agg(rows, bindings):
    wins = {}
    dims = {arm: {d: [0, 0] for d in DIMS} for arm in ('frozen', 'repaired')}
    major = {'frozen': 0, 'repaired': 0}
    fit = {arm: {} for arm in ('frozen', 'repaired')}
    conflicts = []
    per_case = {}
    for r in rows:
        p = r.get('parsed') or {}
        b = bindings[r['unit_id']]
        arm = arm_of(b, p.get('verdict'))
        wins[arm] = wins.get(arm, 0) + 1
        per_case[r['unit_id']] = {'verdict': p.get('verdict'), 'winner_arm': arm,
                                  'card_conflict': bool(p.get('card_conflict')),
                                  'schema_ok': bool(r.get('schema_ok')),
                                  'answer_kinds': b.get('answer_kinds')}
        if p.get('card_conflict'):
            conflicts.append(r['unit_id'])
        for side, name in (('A', b['a_arm']), ('B', b['b_arm'])):
            d = p.get(side) or {}
            sc = d.get('scores') or {}
            for k in DIMS:
                v = sc.get(k)
                if isinstance(v, (int, float)):
                    dims[name][k][0] += v
                    dims[name][k][1] += 1
            major[name] += len(d.get('major_errors') or [])
            f = d.get('action_fit') or 'null'
            fit[name][f] = fit[name].get(f, 0) + 1
    return {'wins': wins,
            'dims_mean': {arm: {k: (round(acc[0] / acc[1], 3) if acc[1] else None)
                                for k, acc in dd.items()}
                          for arm, dd in (('frozen', dims['frozen']),
                                          ('repaired', dims['repaired']))},
            'major_errors': major, 'action_fit': fit,
            'card_conflicts': conflicts, 'per_case': per_case}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--bindings', required=True)
    ap.add_argument('--ratings', required=True)
    ap.add_argument('--bindings-swap', default=None)
    ap.add_argument('--ratings-swap', default=None)
    ap.add_argument('--out', required=True)
    a = ap.parse_args(argv)
    return _run(a)


def _run(a):
    bnd = json.load(open(a.bindings, encoding='utf-8'))
    rows = load_rows(a.ratings)
    main_agg = agg(rows, bnd)
    doc = {'kind': 'f5_analysis',
           'note': '同臂重抽对比（不是组件消融）：A/B 为同一身份两次独立实跑。',
           'ratings': os.path.relpath(a.ratings, ROOT).replace('\\', '/'),
           'n': len(rows),
           'schema_ok': sum(1 for r in rows if r.get('schema_ok')),
           'transport_ok': sum(1 for r in rows if r.get('transport_ok')),
           'wins': main_agg['wins'],
           'dims_mean': main_agg['dims_mean'],
           'major_errors': main_agg['major_errors'],
           'action_fit': main_agg['action_fit'],
           'card_conflicts': main_agg['card_conflicts'],
           'per_case': main_agg['per_case']}
    # format_fail 位是否判给可交付一侧
    ff = [u for u, v in doc['per_case'].items()
          if 'format_fail' in (v['answer_kinds'] or {}).values()]
    doc['format_fail_cases'] = {}
    for u in ff:
        kinds = doc['per_case'][u]['answer_kinds']
        loser_arm = None
        for side in ('A', 'B'):
            if kinds.get(side) == 'format_fail':
                loser_arm = bnd[u]['a_arm'] if side == 'A' else bnd[u]['b_arm']
        doc['format_fail_cases'][u] = {
            'undeliverable_arm': loser_arm,
            'winner_arm': doc['per_case'][u]['winner_arm'],
            'judge_penalized_undeliverable': doc['per_case'][u]['winner_arm'] != loser_arm}
    if a.ratings_swap and a.bindings_swap:
        bs = json.load(open(a.bindings_swap, encoding='utf-8'))
        rs = load_rows(a.ratings_swap)
        sw = agg(rs, bs)
        agree = sum(1 for u in doc['per_case']
                    if u in sw['per_case'] and
                    doc['per_case'][u]['winner_arm'] == sw['per_case'][u]['winner_arm'])
        doc['swap'] = {'ratings': os.path.relpath(a.ratings_swap, ROOT).replace('\\', '/'),
                       'n': len(rs),
                       'schema_ok': sum(1 for r in rs if r.get('schema_ok')),
                       'transport_ok': sum(1 for r in rs if r.get('transport_ok')),
                       'wins': sw['wins'],
                       'agreement_with_forward': agree,
                       'agreement_rate': round(agree / max(1, len(rs)), 3),
                       'flipped_cases': [u for u in sw['per_case']
                                          if u in doc['per_case'] and
                                          doc['per_case'][u]['winner_arm'] !=
                                          sw['per_case'][u]['winner_arm']]}
    dst = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out.replace('/', os.sep))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    json.dump(doc, open(dst, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(json.dumps({k: v for k, v in doc.items()
                      if k in ('n', 'schema_ok', 'transport_ok', 'wins', 'major_errors',
                               'card_conflicts', 'format_fail_cases', 'swap')},
                     ensure_ascii=False, indent=1, sort_keys=True))
    print('WROTE', os.path.relpath(dst, ROOT).replace('\\', '/'))
    return 0


if __name__ == '__main__':
    import sys
    sys.exit(main())
