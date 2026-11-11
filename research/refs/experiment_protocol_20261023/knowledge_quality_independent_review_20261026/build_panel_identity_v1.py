# -*- coding: utf-8 -*-
r"""build_panel_identity_v1.py — PANEL_IDENTITY_V1（冻结提案 §六.1：32 位点×4 臂答案身份清单）。

纯机械组装，零语义、零模型调用：
- 来源 = PRIVATE_BINDINGS.json（node_id/case_id/arm/raw_sha256/case_input_sha256/snapshot_sha256/
  answer_dir/run_id/frozen_plan_path）+ QUALITY_NODES.jsonl（评审用匿名包，文件级散列见 PACKET_MANIFEST）。
- 批次归属：frozen_plan_path/run_id → sweep_a（reference_product_v1 vs goal_action_v1）/
  sweep_b（v5_purpose_aligned vs evidence_memory_v1）；两批两时段混杂如实标注。
- 每份原始出站 prompt 的散列在各自 answer_dir 的 calls.json（指针，不复制不改动）。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe build_panel_identity_v1.py
"""
import json
import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(HERE))))
OUT = os.path.join(HERE, 'PANEL_IDENTITY_V1.json')

SWEEP_BY_ARM = {
    'reference_product_v1': 'sweep_a_orig_vs_goal',
    'goal_action_v1': 'sweep_a_orig_vs_goal',
    'v5_purpose_aligned': 'sweep_b_v5_vs_mem',
    'evidence_memory_v1': 'sweep_b_v5_vs_mem',
}


def main():
    bindings = json.load(open(os.path.join(HERE, 'PRIVATE_BINDINGS.json'), encoding='utf-8'))
    nodes = [json.loads(l) for l in open(os.path.join(HERE, 'QUALITY_NODES.jsonl'), encoding='utf-8')]
    problems = []
    if len(bindings) != 128 or len(nodes) != 128:
        problems.append('bindings=%d nodes=%d（期望 128/128）' % (len(bindings), len(nodes)))
    node_ids = {n['node_id'] for n in nodes}
    bind_ids = {b['node_id'] for b in bindings}
    if node_ids != bind_ids:
        problems.append('节点/绑定不一致 missing=%s extra=%s'
                        % (sorted(node_ids - bind_ids)[:5], sorted(bind_ids - node_ids)[:5]))

    arm_counter = Counter(b['arm'] for b in bindings)
    if dict(arm_counter) != {'reference_product_v1': 32, 'goal_action_v1': 32,
                             'v5_purpose_aligned': 32, 'evidence_memory_v1': 32}:
        problems.append('臂分布异常: %s' % dict(arm_counter))

    sweep_counter = Counter(SWEEP_BY_ARM[b['arm']] for b in bindings)
    if dict(sweep_counter) != {'sweep_a_orig_vs_goal': 64, 'sweep_b_v5_vs_mem': 64}:
        problems.append('批次归属异常: %s' % dict(sweep_counter))

    rows = []
    for b in sorted(bindings, key=lambda x: x['node_id']):
        plan = b.get('frozen_plan_path') or ''
        mismatch = ('sweep_a' not in plan and 'sweep_b' not in plan)
        rows.append({
            'node_id': b['node_id'],
            'case_id': b['case_id'],
            'arm': b['arm'],
            'batch': SWEEP_BY_ARM[b['arm']],
            'frozen_plan_path': plan,
            'frozen_plan_path_has_batch_token': (not mismatch),
            'raw_sha256': b['raw_sha256'],
            'case_input_sha256': b['case_input_sha256'],
            'node_sha256': b.get('node_sha256'),
            'snapshot_sha256': b.get('snapshot_sha256'),
            'answer_dir': b.get('answer_dir'),
            'run_id': b.get('run_id'),
            'harness_identity': b.get('harness_identity'),
        })
    plan_token_bad = [r['node_id'] for r in rows if not r['frozen_plan_path_has_batch_token']]
    if plan_token_bad:
        problems.append('frozen_plan_path 无批次标记的节点（需人工核对归属）: %s' % plan_token_bad[:10])

    if problems:
        print('PANEL_IDENTITY SELF-CHECK FAILED:')
        for p in problems:
            print('  -', p)
        sys.exit(1)

    doc = {
        'kind': 'panel_identity_v1',
        'generated': '2026-10-27',
        'purpose': '冻结提案§六.1：32 位点×4 臂答案身份清单（谁在哪批、输入/输出/状态散列、原始档案指针）',
        'counts': {'nodes': 128, 'cases': 32, 'arms': 4,
                   'by_arm': dict(arm_counter), 'by_batch': dict(sweep_counter)},
        'confound_note': '四臂来自两个时段批次（sweep_a: ref vs goal_action；sweep_b: v5 vs evidence_memory）；'
                         '同批两臂为干净配对，跨批四臂横比带批次/时段混杂，只作描述不作因果排序。',
        'prompt_hash_note': '每份原始出站 prompt 的逐字散列在各 answer_dir 的 calls.json（指针不复制）；'
                            '评审输入身份由 QUALITY_NODES.jsonl 文件级 sha256（PACKET_MANIFEST）固定。',
        'nodes': rows,
    }
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print('OK: PANEL_IDENTITY_V1 128 nodes (32 cases x 4 arms), sweeps %s -> %s'
          % (dict(sweep_counter), OUT))


if __name__ == '__main__':
    main()
