# -*- coding: utf-8 -*-
"""tools/build_v5_evidence_review.py — 包E 确定性证据复核（零网络、零新调用、不重跑生成）。

执行书依据：包E"用已冻结 V1.1 与分维度合格/**证据复核**方法重评已有 v5 批 48 份答案的首选及
全部非空备选"（WORKER_EXECUTION §六.1）+ 包C.6"未过维度转证据复核或保留未评"。
本工具产出 E 的**确定性半边**：参考标签里逐主张四轴/severity/槽位 的缺陷与受保护清单、
协议真相、程序分与 veto；语义/行动合格(Q)与个人愿发(W)保持 unknown——等裁判分维度资格
落地后由分析阶段合并，绝不用本工具的机械清单冒充 Q。

分母纪律（与 analyze_irr_v2 同源）：
  - 缺陷=参考已认定：F4 型（事实/类指常识 ∧ 未提供 ∧ 确定）或 冲突型（冲突 ∧ 确定）；
  - trusted 分母只用 constructed_reference+human_reviewed；mechanical_only 只作信息列；
  - disputed 主张带标记列出（计入检出测量，不计候选胜负——本工具根本不产生候选胜负）。
"""
import hashlib
import json
import os
import sys
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PK = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_V1_1')
TRUSTED = ('constructed_reference', 'human_reviewed')


def sha16(p):
    return hashlib.sha256(open(p, 'rb').read()).hexdigest()[:16]


def is_f4(c):
    return (c['statement_type'] in ('事实', '类指常识')
            and c['evidence_status'] == '未提供' and c['stance'] == '确定')


def is_conflict(c):
    return c['evidence_status'] == '冲突' and c['stance'] == '确定'


def main():
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    sk = json.load(open(os.path.join(PK, 'SEALED_KEY_V1_1.json'), encoding='utf-8'))
    rl = json.load(open(os.path.join(PK, 'REFERENCE_LABELS_V1_1.json'), encoding='utf-8'))
    ref = {n['node_id']: n for n in rl['nodes']}
    v5 = [n for n in sk['nodes'] if n['batch'] == 'v5']
    assert len(v5) == 24, 'v5 节点数 %d ≠ 24' % len(v5)

    res = {
        'protocol': 'V5-EVIDENCE-REVIEW-V1',
        'created': '2026-10-25',
        'zero_model_calls': True,
        'sources': {
            'SEALED_KEY_V1_1.json': sha16(os.path.join(PK, 'SEALED_KEY_V1_1.json')),
            'REFERENCE_LABELS_V1_1.json': sha16(os.path.join(PK, 'REFERENCE_LABELS_V1_1.json')),
        },
        'scope_note': 'v5 批 24 位置 × 2 臂 = 48 份答案；首选(response.recommended)+全部非空备选(backup[1..7])'
                      '按参考主张槽位逐条复核。不重跑生成、不计 harness 收益。',
        'denominator_discipline': 'trusted=constructed_reference+human_reviewed；mechanical_only 只作信息列；'
                                  'disputed 带标记列出（不计候选胜负）。Q/W=unknown 不冒充。',
        'per_answer': [],
        'per_story': {},
        'per_axis_defects': defaultdict(lambda: Counter()),
        'aggregates': {},
    }

    arm_stats = {arm: {'trusted_viol': 0, 'trusted_conflict': 0, 'trusted_protected': 0,
                       'mechanical_viol': 0, 'mechanical_conflict': 0, 'mechanical_protected': 0,
                       'disputed_in_defects': 0,
                       'proto_invisible_key': 0, 'proto_slots_missing': 0,
                       'proto_nonstring': 0, 'proto_directions_bad': 0,
                       'program_score10_sum': 0.0, 'veto_n': 0}
                 for arm in ('parent', 'candidate')}

    for n in v5:
        r = ref[n['node_id']]
        entry = {'node_id': n['node_id'], 'position': n['position'], 'story': n['story'],
                 'split': n['split'], 'arms': {}}
        for lab in ('甲', '乙'):
            arm_name = n[lab]['arm']            # parent / candidate（按节点字段，勿全局假设）
            a = r['answers'][lab]
            slots = defaultdict(lambda: {'trusted_viol': [], 'trusted_conflict': [],
                                         'trusted_protected_n': 0,
                                         'mech_viol': 0, 'mech_conflict': 0, 'mech_protected_n': 0})
            for c in a['claims']:
                fam = c['slot'].split('#')[0]
                s = slots[fam]
                defect = is_f4(c) or is_conflict(c)
                key = 'trusted' if c['reference_provenance'] in TRUSTED else 'mech'
                if is_f4(c):
                    bucket = s['trusted_viol'] if key == 'trusted' else None
                    if key == 'trusted':
                        bucket.append({'claim_id': c['claim_id'], 'text': c['text'],
                                       'severity': c['severity'], 'disputed': bool(c.get('disputed')),
                                       'provenance': c['reference_provenance']})
                        arm_stats[arm_name]['trusted_viol'] += 1
                    else:
                        s['mech_viol'] += 1
                        arm_stats[arm_name]['mechanical_viol'] += 1
                    if defect and c.get('disputed'):
                        arm_stats[arm_name]['disputed_in_defects'] += 1
                elif is_conflict(c):
                    if key == 'trusted':
                        s['trusted_conflict'].append({'claim_id': c['claim_id'], 'text': c['text'],
                                                      'severity': c['severity'],
                                                      'disputed': bool(c.get('disputed')),
                                                      'provenance': c['reference_provenance']})
                        arm_stats[arm_name]['trusted_conflict'] += 1
                    else:
                        s['mech_conflict'] += 1
                        arm_stats[arm_name]['mechanical_conflict'] += 1
                    if c.get('disputed'):
                        arm_stats[arm_name]['disputed_in_defects'] += 1
                else:
                    if key == 'trusted':
                        s['trusted_protected_n'] += 1
                        arm_stats[arm_name]['trusted_protected'] += 1
                    else:
                        s['mech_protected_n'] += 1
                        arm_stats[arm_name]['mechanical_protected'] += 1
                if defect:
                    res['per_axis_defects'][c['statement_type']][c['evidence_status'] + '/' + c['stance']] += 1
            t = a.get('protocol_truth') or {}
            proto = {'raw_present': t.get('raw_present'), 'invisible_key_chars': t.get('invisible_key_chars'),
                     'style_slots_missing': t.get('style_slots_missing'),
                     'directions_len': t.get('directions_len'),
                     'directions_null_count': t.get('directions_null_count'),
                     'response_nonstring_values': t.get('response_nonstring_values')}
            if t.get('raw_present'):
                if t.get('invisible_key_chars'):
                    arm_stats[arm_name]['proto_invisible_key'] += 1
                if t.get('style_slots_missing'):
                    arm_stats[arm_name]['proto_slots_missing'] += 1
                if t.get('response_nonstring_values'):
                    arm_stats[arm_name]['proto_nonstring'] += 1
                if t.get('directions_len') not in (None, 4):
                    arm_stats[arm_name]['proto_directions_bad'] += 1
            entry['arms'][arm_name] = {
                'label': lab,
                'program_score10': n[lab].get('program_score10'),
                'veto': n[lab].get('veto'),
                'protocol_truth': proto,
                'slots': {k: v for k, v in sorted(slots.items())},
                'unknown': {'Q_semantic': 'unknown（待裁判分维度资格）',
                            'W_user_willing': 'unknown（USER_CALIBRATION_SHEET_V1 未回收）'}}
            arm_stats[arm_name]['program_score10_sum'] += float(n[lab].get('program_score10') or 0)
            arm_stats[arm_name]['veto_n'] += 1 if n[lab].get('veto') else 0
        res['per_answer'].append(entry)
        st = res['per_story'].setdefault(n['story'], {'nodes': 0, 'severe_trusted_viol': {'parent': 0, 'candidate': 0}})
        st['nodes'] += 1
        for arm_name in ('parent', 'candidate'):
            e = entry['arms'][arm_name]
            sv = sum(len(v['trusted_viol']) + len(v['trusted_conflict']) for v in e['slots'].values())
            st['severe_trusted_viol'][arm_name] += sv
    res['per_axis_defects'] = {k: dict(v) for k, v in res['per_axis_defects'].items()}

    # 汇总：分母与未知覆盖（每臂各 24 答案）
    res['aggregates'] = {
        'answers_per_arm': 24,
        'arms': {arm: dict(v) for arm, v in arm_stats.items()},
        'unknown_coverage': {
            'Q_semantic': 'unknown — 语义/行动合格需裁判分维度资格（204 批运行中）',
            'W_user_willing': 'unknown — 用户口味表 16 组未回收',
            'cost_per_Q': 'unknown', 'cost_per_W': 'unknown',
            'amount': 'null（无可核对单价）'},
        'note': '本工具只列参考已认定的缺陷/保护清单与协议真相，不产生候选胜负；'
                '严重风险按 trusted 主张计，mechanical_only 未逐条人工核对仅作信息列。'}
    out = os.path.join(ROOT, 'evaluation', 'V5_EVIDENCE_REVIEW_V1.json')
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(res, f, ensure_ascii=False, indent=1, default=dict)
    print('WROTE', out)
    print('per-arm trusted defects: parent viol=%d conflict=%d | candidate viol=%d conflict=%d'
          % (arm_stats['parent']['trusted_viol'], arm_stats['parent']['trusted_conflict'],
             arm_stats['candidate']['trusted_viol'], arm_stats['candidate']['trusted_conflict']))
    print('protocol anomalies: parent key/miss/str/dir = %d/%d/%d/%d | candidate = %d/%d/%d/%d'
          % (arm_stats['parent']['proto_invisible_key'], arm_stats['parent']['proto_slots_missing'],
             arm_stats['parent']['proto_nonstring'], arm_stats['parent']['proto_directions_bad'],
             arm_stats['candidate']['proto_invisible_key'], arm_stats['candidate']['proto_slots_missing'],
             arm_stats['candidate']['proto_nonstring'], arm_stats['candidate']['proto_directions_bad']))


if __name__ == '__main__':
    main()
