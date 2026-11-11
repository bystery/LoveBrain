# -*- coding: utf-8 -*-
r"""build_quality_plan.py — B2 收尾：冻结 QUALITY_PLAN.json（输入散列+派发顺序+配置身份）。

冻结对象：QUALITY_NODES.jsonl / QUALITY_CONTROLS_DEV_V1.json / QUALITY_REFERENCE_CARDS_V1.json /
QUALITY_MANUAL_V1.md / PACKET_MANIFEST.json 的散列；173=32 控制+128 首选+13 固定复评的派发顺序；
非流式配置身份；授权 AUTH-QUALITY-PANEL-20261027-01（cap 192，计划 173，余 19 不是补跑额度）。
冻结后任何输入变化 → 运行器拒绝启动（旧答卷只能对旧身份解释）。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe build_quality_plan.py
"""
import hashlib
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(HERE))))
AID = 'AUTH-QUALITY-PANEL-20261027-01'

INPUTS = ['QUALITY_NODES.jsonl', 'QUALITY_CONTROLS_DEV_V1.json',
          'QUALITY_REFERENCE_CARDS_V1.json', 'QUALITY_MANUAL_V1.md',
          'PACKET_MANIFEST.json', 'AUTHORIZATION_QUALITY_PANEL.json']


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def main():
    nodes = [json.loads(l) for l in open(os.path.join(HERE, 'QUALITY_NODES.jsonl'), encoding='utf-8')]
    controls = json.load(open(os.path.join(HERE, 'QUALITY_CONTROLS_DEV_V1.json'), encoding='utf-8'))
    cards = json.load(open(os.path.join(HERE, 'QUALITY_REFERENCE_CARDS_V1.json'), encoding='utf-8'))
    manifest = json.load(open(os.path.join(HERE, 'PACKET_MANIFEST.json'), encoding='utf-8'))
    bindings = json.load(open(os.path.join(HERE, 'PRIVATE_BINDINGS.json'), encoding='utf-8'))

    problems = []
    if len(nodes) != 128:
        problems.append('节点数 %d != 128' % len(nodes))
    n_backups = sum(len([b for b in n['answer']['backups'] if b.get('text')]) for n in nodes)
    if n_backups != 841:
        problems.append('非空备选 %d != 841' % n_backups)
    if controls.get('count') != 32 or len(controls['controls']) != 32:
        problems.append('控制样本 %d != 32' % len(controls['controls']))
    if cards.get('cards_count') != 32 or len(cards['cards']) != 32:
        problems.append('参考卡 %d != 32' % len(cards['cards']))
    rep_ids = manifest['repeat_node_ids']
    if len(rep_ids) != 13 or any(r not in {n['node_id'] for n in nodes} for r in rep_ids):
        problems.append('复评清单异常: %s' % rep_ids)
    node_ids = [n['node_id'] for n in nodes]
    bind_map = {b['node_id']: b['case_id'] for b in bindings}
    if sorted(bind_map) != sorted(node_ids):
        problems.append('绑定映射与节点不一致')
    missing_cards = sorted({bind_map[i] for i in node_ids} - {c['case_id'] for c in cards['cards']})
    if missing_cards:
        problems.append('参考卡缺案例: %s' % missing_cards)

    # 派发顺序：32 控制（列表序）→ 128 首选（文件序）→ 13 固定复评（manifest 序）
    plan = []
    for c in controls['controls']:
        plan.append({'kind': 'control', 'item_id': c['control_id']})
    for n in nodes:
        plan.append({'kind': 'primary', 'item_id': n['node_id']})
    for r in rep_ids:
        plan.append({'kind': 'repeat_same', 'item_id': r})
    if len(plan) != 173:
        problems.append('计划长度 %d != 173' % len(plan))

    if problems:
        print('FREEZE FAILED:')
        for p in problems:
            print('  -', p)
        raise SystemExit(1)

    hashes = {f: sha256_file(os.path.join(HERE, f)) for f in INPUTS}
    plan_doc = {
        'kind': 'quality_plan_v1',
        'frozen_at': '2026-10-27',
        'authorization_id': AID,
        'ledger': os.path.join(ROOT, 'logs', 'auth_budget', AID + '.jsonl'),
        'cap': 192,
        'planned_attempts': 173,
        'unused_capacity_policy': '19 remaining is not retry permission; not a spending target',
        'config_identity': {
            'endpoint': 'http://127.0.0.1:17770/v1',
            'model_alias': 'dsf',
            'temperature': 0.7,
            'stream': False,
            'api_config_identity': 'api_cfg:openai_compat_stream_false_v1',
            'timeout_s_engineering': 240,
        },
        'input_hashes_sha256': hashes,
        'counts': {'controls': 32, 'primaries': 128, 'repeats': 13, 'total': 173,
                   'nonempty_backups': 841},
        'repeat_node_ids': rep_ids,
        'dispatch_order': plan,
        'notes': [
            'controls are worker-AI authored development controls (not independent sealed set)',
            'semantic reference cards authored by worker; same-AI self-check registered',
            'no human willingness labels anywhere; AI forbidden to fill W',
        ],
    }
    out = os.path.join(HERE, 'QUALITY_PLAN.json')
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(plan_doc, f, ensure_ascii=False, indent=1)
    print('FROZEN: %s (173 attempts planned, cap 192)' % out)


if __name__ == '__main__':
    main()
