# -*- coding: utf-8 -*-
"""tools/c4_four_arm_review_pack.py — C4 四臂逐位审阅材料生成器（离线，零真实调用，零语义预填）。

给谁用：给做初审的我/评审者一份【同一位点四臂并排】的可读材料，每条只填"我看到的事实"，
脚本本身绝不替我判语义（AGENTS §0"语义质量必须由有资格评审+原文共同判"，且 §6-V4 禁静默填空）：
  - 题面侧：本轮 HER 消息、note、clock、intent(enabled/status/是否真注进目标块，判据取生产函数)
  - 卡侧：families/allowed_behaviors/failure_conditions/evidence/content_revision/review_status/split
  - 四臂侧：首选原文 + 备选数 + 程序列(T/P/C/K/veto/full-or-partial) + token + 等待实测
  - 判定侧：全部留空，附允许取值与必填分母提示
输出：
  evaluation/C4_FOUR_ARM_REVIEW_PACK_V1.json   （底账，可重复生成）
  evaluation/C4_FOUR_ARM_REVIEW_PACK_V1.md     （逐位可读审阅页）
"""
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, 'integration_preview'))
sys.path.insert(0, os.path.join(ROOT, 'candidates', 'reference_product_v1'))
from delivery_columns_v2 import full_slot_delivery                     # noqa: E402
from engine_adapter import OutputConsumer                              # noqa: E402
import reference_chain as rc                                           # noqa: E402

SWEEPS = [
    ('sweep_a_orig_vs_goal', 'c4_sweep_a_orig_vs_goal_live_20261026',
     {'parent': 'reference_product_v1', 'candidate': 'goal_action_v1'}),
    ('sweep_b_v5_vs_mem', 'c4_sweep_b_v5_vs_mem_live_20261026',
     {'parent': 'v5_purpose_aligned', 'candidate': 'evidence_memory_v1'}),
]
ARMS = ['reference_product_v1', 'v5_purpose_aligned', 'goal_action_v1', 'evidence_memory_v1']
CARDS = os.path.join(ROOT, 'benchmark', 'c4_screen_20261026',
                     'GRADING_CARDS_C4_E006_E007_E008_E012_R2.json')
COVERAGE = os.path.join(ROOT, 'evaluation', 'C4_INTENT_LAYER_COVERAGE_V1.json')
OUT_JSON = os.path.join(ROOT, 'evaluation', 'C4_FOUR_ARM_REVIEW_PACK_V1.json')
OUT_MD = os.path.join(ROOT, 'evaluation', 'C4_FOUR_ARM_REVIEW_PACK_V1.md')

VERDICT_GUIDE = {
    'goal_relevant': '该位点目标块是否真生效（取 C4_INTENT_LAYER_COVERAGE_V1，脚本已填）',
    'per_arm_fact_read': '每臂填：有据/无据既成断言/漏用关键事实/归属错/状态错/未评（多选用 + 连）',
    'per_arm_action_read': '每臂填：承接/反抛/自我表达/转场/深聊/邀约/收尾/停/越界/施压/未评',
    'pairwise_verdict': '甲乙式相对判断：A明显好 / A略好 / 相当 / 都不行 / 说不清（不许留空当作相当）',
    'would_send_W': '只有用户本人能标；worker 一律填 unknown',
}


def turn_dir_index(base, arm_names):
    idx = {}
    for d in sorted(glob.glob(os.path.join(base, '*', 'out', 'turn_*'))):
        role = os.path.basename(os.path.dirname(os.path.dirname(d))).split('_')[0]
        arm = arm_names.get(role)
        if not arm:
            continue
        rp = os.path.join(d, 'reply_raw.txt')
        if not os.path.isfile(rp):
            continue
        raw = open(rp, encoding='utf-8').read()
        mf = json.load(open(os.path.join(d, 'manifest.json'), encoding='utf-8'))
        sc_p = os.path.join(d, 'scoring.json')
        sc = json.load(open(sc_p, encoding='utf-8')) if os.path.isfile(sc_p) else {}
        js = [c for c in sc.get('checks', []) if c['id'] == 'json_schema_valid']
        parsed = OutputConsumer.parse(raw)
        try:
            obj = json.loads(raw)
        except Exception:                                            # noqa: BLE001
            obj = None
        full, why = (False, 'raw 不可 json.loads') if obj is None else full_slot_delivery(obj)
        cj = os.path.join(d, 'calls.json')
        timing = {}
        if os.path.isfile(cj):
            calls = json.load(open(cj, encoding='utf-8'))
            c0 = calls[0] if isinstance(calls, list) and calls else {}
            timing = c0.get('timing') or {}
        tk = mf.get('tokens') or {}
        idx.setdefault(mf.get('case_id') or os.path.basename(d), {})[arm] = {
            'turn_dir': os.path.relpath(d, ROOT).replace('\\', '/'),
            'primary': parsed['slots'].get('recommended'),
            'backups_n': len(parsed['slots'].get('backups') or []),
            'no_reply': bool(parsed['slots'].get('no_reply')),
            'T': bool(raw.strip()) and mf.get('mechanism_error') is None,
            'P': bool(js) and js[0]['got'] >= js[0]['pts'],
            'C': bool(parsed['slots'].get('recommended')),
            'K': (not mf.get('veto')) and bool(parsed['slots'].get('recommended')),
            'veto': mf.get('veto'),
            'full_slot': full, 'partial_reason': why if not full else '',
            'key_norm_events': len(parsed.get('key_normalization_audit') or []),
            'prompt_tokens': tk.get('prompt'), 'completion_tokens': tk.get('completion'),
            'latency_ms': mf.get('latency_ms'),
            'first_content_ms': timing.get('first_content_ms'),
            'completion_ms': timing.get('completion_ms'),
            'first_usable_ms': timing.get('first_usable_ms'),
        }
    return idx


def main():
    cards = {c['case_id']: c for c in json.load(open(CARDS, encoding='utf-8'))}
    cov = json.load(open(COVERAGE, encoding='utf-8'))
    live = set(cov['loci_goal_live'])
    by_case = {}
    present_arms = set()
    for key, dirname, arm_names in SWEEPS:
        base = os.path.join(ROOT, 'logs', dirname)
        if not os.path.isdir(base):
            continue
        for cid, row in turn_dir_index(base, arm_names).items():
            by_case.setdefault(cid, {}).update(row)
            present_arms.update(row.keys())

    episodes = {}
    for ep in ('E006', 'E007', 'E008', 'E012'):
        e = json.load(open(os.path.join(ROOT, 'benchmark', 'episodes', ep + '.json'),
                           encoding='utf-8'))
        for t in e['turns']:
            episodes[t['case_id']] = (ep, t)

    loci = []
    for cid in sorted(by_case):
        ep, turn = episodes.get(cid, (cid.split('_')[0], {'input': {}, 'case_id': cid}))
        inp = turn.get('input', {})
        intent = inp.get('intent') or {}
        rec = {
            'case_id': cid, 'episode': ep,
            'her_messages': inp.get('messages', []),
            'clock': inp.get('clock'), 'note': inp.get('note'),
            'intent': {'enabled': intent.get('enabled'), 'status': intent.get('status'),
                       'text': intent.get('text'),
                       'goal_block_effective': rc.is_intent_effective(intent),
                       'status_absent_treated_ACTIVE': 'status' not in intent and bool(intent)},
            'card': cards.get(cid),
            'arms': {a: by_case[cid].get(a) for a in ARMS},
            'arms_missing': [a for a in ARMS if not by_case[cid].get(a)],
        }
        rec['goal_live'] = cid in live
        loci.append(rec)

    doc = {
        'kind': 'c4_four_arm_review_pack',
        'batch_pair': [k for k, _d, _a in SWEEPS],
        'cards_file_sha256_note': '与授权 V2 scope.cards 同一文件（32 张，逐字回核过）',
        'arms': ARMS,
        'arms_present_so_far': sorted(present_arms),
        'loci_total_expected': 32,
        'loci_with_any_output': len(loci),
        'loci_goal_live': sorted(live),
        'verdict_field_guide': VERDICT_GUIDE,
        'semantics_prefilled': 0,
        'note': ('本文件不含任何语义判定；primary 为消费器提出的首选原文，'
                 'T/P/C/K 为传输/原始协议/可提取/程序未否决四列，互不冒充。'),
        'loci': loci,
        'real_model_calls': 0,
    }
    json.dump(doc, open(OUT_JSON, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

    L = ['# C4 四臂逐位审阅包 V1（零语义预填）', '',
         '- 位点：32（E006/E007/E008/E012 × 8）；四臂=%s' % ' / '.join(ARMS),
         '- 目标块真生效位点（7）：%s' % ', '.join(sorted(live)),
         '- 判定字段口径：%s' % json.dumps(VERDICT_GUIDE, ensure_ascii=False),
         '']
    for r in loci:
        c = r['card'] or {}
        L.append('## %s  [goal_live=%s]' % (r['case_id'], r['goal_live']))
        for m in r['her_messages'] or []:
            L.append('- HER(%s): %s' % (m.get('ts'), m.get('text')))
        if r['note']:
            L.append('- note（私密，不发送）: %s' % r['note'])
        it = r['intent']
        L.append('- intent: enabled=%s status=%s 目标块生效=%s%s'
                 % (it['enabled'], it['status'], it['goal_block_effective'],
                    '（status 缺省按原版语义视为 ACTIVE）'
                    if it['status_absent_treated_ACTIVE'] else ''))
        L.append('- 卡: families=%s allowed=%s failure=%s rev=%s review=%s'
                 % (c.get('families'), c.get('allowed_behaviors'),
                    c.get('failure_conditions'), c.get('content_revision'),
                    c.get('review_status')))
        for arm in ARMS:
            a = r['arms'].get(arm)
            if not a:
                L.append('  - **%s**: 未跑（缺）' % arm)
                continue
            L.append('  - **%s**: T=%s P=%s C=%s K=%s veto=%s full=%s tok=%s/%s '
                     'lat=%s first_content=%s first_usable=%s backups=%s%s'
                     % (arm, a['T'], a['P'], a['C'], a['K'], a['veto'], a['full_slot'],
                        a['prompt_tokens'], a['completion_tokens'], a['latency_ms'],
                        a['first_content_ms'], a['first_usable_ms'], a['backups_n'],
                        (' | partial: %s' % a['partial_reason']) if a['partial_reason'] else ''))
            L.append('    - 首选: %s' % (json.dumps(a['primary'], ensure_ascii=False)
                                        if a['primary'] is not None else '（无首选/no_reply=%s）'
                                        % a['no_reply']))
        L.append('  - 我的事实读: ')
        L.append('  - 我的动作读: ')
        L.append('  - 位点结论: ')
        L.append('')
    open(OUT_MD, 'w', encoding='utf-8').write('\n'.join(L))

    print(json.dumps({'loci_with_output': len(loci), 'arms_present': sorted(present_arms),
                      'loci_goal_live': len(live), 'semantics_prefilled': 0,
                      'out_json': os.path.relpath(OUT_JSON, ROOT).replace('\\', '/'),
                      'out_md': os.path.relpath(OUT_MD, ROOT).replace('\\', '/')},
                     ensure_ascii=False, indent=1))


if __name__ == '__main__':
    main()
