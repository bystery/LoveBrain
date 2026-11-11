# -*- coding: utf-8 -*-
"""tools/delivery_columns_c4_sweeps.py — C4 机制筛选（批A+批B）四臂六列与逐位对照（零网络零生成）。

口径全部复用既有尺子，不新增判据：
  T/P/C/K/Q/W 与 cost_named_two_ways 由 `delivery_columns_v2.scan + block` 给出
  （与 96 批、v5 批同一消费器/评分器口径；改判据=尺子改动，本脚本不动）；
  等待列取每 turn `calls.json[0].timing`：first_content/first_preview/completion 实测，
  `first_usable_ms` 只有真实打点才算——普通链路未接消费回调就如实计 null，不离线回填；
  四臂逐位对照 = 同一 case_id 在【原版 / v5 / 目标动作 C2 / 证据记忆 C3】各自的
  程序列与字节代价并排，胜负留给"有资格评审 + 原文初审"，这里不写结论。

用法：
  D:\\anaconda\\python.exe tools\\delivery_columns_c4_sweeps.py            # 中间态（缺 turn 不报错，如实计数）
  D:\\anaconda\\python.exe tools\\delivery_columns_c4_sweeps.py --final    # 收口门禁：128 turn 不齐则退出码 2
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
from delivery_columns_v2 import scan, block, versions                 # noqa: E402

C4REFS = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'c4_screen_20261026')
SWEEPS = [
    ('sweep_a_orig_vs_goal', 'c4_sweep_a_orig_vs_goal_live_20261026',
     {'parent': 'reference_product_v1', 'candidate': 'goal_action_v1'}),
    ('sweep_b_v5_vs_mem', 'c4_sweep_b_v5_vs_mem_live_20261026',
     {'parent': 'v5_purpose_aligned', 'candidate': 'evidence_memory_v1'}),
]
ARMS_ORDER = ['reference_product_v1', 'v5_purpose_aligned', 'goal_action_v1', 'evidence_memory_v1']
LOCUS_PER_ARM = 32


def wait_and_locus(base, arm_names):
    """逐 turn 读 manifest/calls → 每臂等待分布 + case_id 级四臂明细。"""
    per_wait = collections.defaultdict(lambda: collections.defaultdict(list))
    null_usable = collections.Counter()
    locus = collections.defaultdict(dict)
    for d in sorted(glob.glob(os.path.join(base, '*', 'out', 'turn_*'))):
        arm_key = os.path.basename(os.path.dirname(os.path.dirname(d)))   # parent_E006 等
        arm = arm_names.get(arm_key.split('_')[0])
        if arm is None:
            continue
        mf_p = os.path.join(d, 'manifest.json')
        if not os.path.isfile(mf_p):
            continue
        mf = json.load(open(mf_p, encoding='utf-8'))
        cid = mf.get('case_id') or os.path.basename(d)
        cj = os.path.join(d, 'calls.json')
        timing = {}
        if os.path.isfile(cj):
            calls = json.load(open(cj, encoding='utf-8'))
            c0 = calls[0] if isinstance(calls, list) and calls else (calls if isinstance(calls, dict) else {})
            timing = c0.get('timing') or {}
        for k in ('first_content_ms', 'first_preview_ms', 'completion_ms'):
            v = timing.get(k)
            if isinstance(v, (int, float)):
                per_wait[arm][k].append(v)
        if timing.get('first_usable_ms') is None:
            null_usable[arm] += 1
        sc_p = os.path.join(d, 'scoring.json')
        sc = json.load(open(sc_p, encoding='utf-8')) if os.path.isfile(sc_p) else {}
        js = [c for c in sc.get('checks', []) if c['id'] == 'json_schema_valid']
        tk = mf.get('tokens') or {}
        locus[cid][arm] = {
            'turn_dir': os.path.relpath(d, ROOT).replace('\\', '/'),
            'P_json_schema_valid': bool(js) and js[0]['got'] >= js[0]['pts'],
            'veto': mf.get('veto'),
            'program_score10': sc.get('program_score10', sc.get('program_score')),
            'prompt_cu': tk.get('prompt'), 'completion_cu': tk.get('completion'),
            'latency_ms': mf.get('latency_ms'),
            'first_content_ms': timing.get('first_content_ms'),
            'completion_ms': timing.get('completion_ms'),
            'first_usable_ms': timing.get('first_usable_ms'),
        }
    waits = {}
    for arm, kv in per_wait.items():
        waits[arm] = {}
        for k, vals in kv.items():
            s = sorted(vals)
            waits[arm][k] = {'n': len(s), 'p50': s[len(s) // 2],
                             'p95': s[min(len(s) - 1, int(len(s) * 0.95))], 'max': s[-1]}
        waits[arm]['first_usable_ms_null'] = null_usable[arm]
    return waits, locus


def main():
    final = '--final' in sys.argv
    turn_dirs_total = 0
    arms_columns = {}
    problem_detail = {}
    all_waits, all_locus = {}, collections.defaultdict(dict)
    for key, dirname, arm_names in SWEEPS:
        base = os.path.join(ROOT, 'logs', dirname)
        dirs = glob.glob(os.path.join(base, '*', 'out', 'turn_*'))
        turn_dirs_total += len(dirs)
        if not dirs:
            print('SKIP %s：尚无实跑目录 %s' % (key, dirname))
            continue
        st, detail = scan(key, base)
        for role, arm in arm_names.items():
            arms_columns[arm] = block(st[role])
            arms_columns[arm]['batch'] = key
            problem_detail[arm] = detail[role]
        w, locus = wait_and_locus(base, arm_names)
        all_waits.update(w)
        for cid, row in locus.items():
            all_locus[cid].update(row)

    four = {}
    for cid, row in sorted(all_locus.items()):
        four[cid] = {'arms_present': sorted(row.keys()), 'arms_missing': sorted(
            set(a for a in ARMS_ORDER if a not in row)), 'per_arm': row}
    complete_loci = [cid for cid, v in four.items() if len(v['arms_present']) == 4]

    result = {
        'doc': 'DELIVERY_AND_COST_COLUMNS_C4_SWEEPS_V1',
        'batches': {k: 'logs/%s' % d for k, d, _ in SWEEPS},
        'authorization_id': 'AUTH-C4-SCREEN-20261026-01',
        'identity_note': ('两批同一流式共同身份 api_cfg:openai_compat_stream_true_v1'
                          '(include_usage gated)；不与任何非流式历史批合并数字'),
        'cards_frozen': None,
        'mode': 'final' if final else 'interim(%d turns)' % turn_dirs_total,
        'versions': versions(),
        'expected_turns_per_arm': LOCUS_PER_ARM,
        'expected_turns_total': 128,
        'turns_on_disk': turn_dirs_total,
        'columns_by_arm': arms_columns,
        'wait_by_arm': all_waits,
        'per_locus_four_arms': four,
        'loci_with_all_four_arms': len(complete_loci),
        'problem_detail': problem_detail,
        'semantics_note': ('Q/W 一律 unknown：本脚本不做语义判定；逐位原文初审与故障族归因'
                           '另由离线评审件产出（不烧额度、不改尺子）。'),
    }
    # 卡集与计划身份从授权 V2 直接引，避免手抄
    auth_p = os.path.join(C4REFS, 'AUTHORIZATION_V2.json')
    if os.path.isfile(auth_p):
        a = json.load(open(auth_p, encoding='utf-8'))
        result['cards_frozen'] = {'file': a['scope']['cards'],
                                  'plan_sha256': {k: v['plan_sha256']
                                                  for k, v in a['frozen_bindings'].items()}}

    if final and (turn_dirs_total != 128 or len(complete_loci) != LOCUS_PER_ARM):
        print('FINAL GATE FAIL: turns=%s/128 四臂齐全位点=%s/%s'
              % (turn_dirs_total, len(complete_loci), LOCUS_PER_ARM))
        out = os.path.join(ROOT, 'evaluation', 'DELIVERY_AND_COST_COLUMNS_C4_SWEEPS_GATEFAIL.json')
        json.dump(result, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        print('wrote', out)
        sys.exit(2)

    out = os.path.join(ROOT, 'evaluation',
                       'DELIVERY_AND_COST_COLUMNS_C4_SWEEPS_FINAL.json' if final
                       else 'DELIVERY_AND_COST_COLUMNS_C4_SWEEPS_INTERIM.json')
    json.dump(result, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('turns=%s/128 四臂齐全位点=%s/%s mode=%s' % (
        turn_dirs_total, len(complete_loci), LOCUS_PER_ARM, result['mode']))
    for arm in ARMS_ORDER:
        c = arms_columns.get(arm)
        if not c:
            print('%s: 无数据' % arm)
            continue
        col, tok = c['columns'], c['tokens']
        print('%s: n=%s T/P/C/K=%s/%s/%s/%s full=%s tok=%s ÷C=%s' % (
            arm, c['n'], col['T'], col['P'], col['C'], col['K'],
            c['delivery_completeness']['full_four_styles_four_directions'],
            tok['total'],
            c['cost_named_two_ways']['token_per_extractable_primary']))
    print('wrote', out)


if __name__ == '__main__':
    main()
