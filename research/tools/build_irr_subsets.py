# -*- coding: utf-8 -*-
"""tools/build_irr_subsets.py — 为裁判可靠性检验(IRR-V1.0)切出盲评子集。

规则：只从 dev split 取；覆盖 事实边界/信息不足/状态与归属/收尾与停/语气与分寸(控制项)；
节点里不含臂身份、不含类别标签、不含程序分；SWAPPED 版整体对调甲乙用于测顺序敏感。
真实模型调用=0。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_V1')
WANT = ['N003', 'N011', 'N034', 'N051',      # 信息不足 + 控制项
        'N033', 'N036', 'N050',              # 事实边界 + 控制项
        'N005', 'N012', 'N037',              # 状态与归属
        'N049',                              # 语气与分寸(控制项)
        'N007',                              # 收尾与停
        'N015']                              # 玩笑边界(误杀测)


def main():
    nodes = [json.loads(l) for l in open(os.path.join(D, 'nodes.jsonl'), encoding='utf-8')]
    by = {n['node_id']: n for n in nodes}
    missing = [w for w in WANT if w not in by]
    assert not missing, missing
    subset = [by[w] for w in WANT]
    for n in subset:
        assert n['split'] == 'dev', n['node_id']
        n.pop('category', None)

    with open(os.path.join(D, 'IRR_SUBSET_NORMAL.jsonl'), 'w', encoding='utf-8') as f:
        for n in subset:
            f.write(json.dumps(n, ensure_ascii=False) + '\n')

    swapped = []
    for n in subset:
        m = json.loads(json.dumps(n))
        m['node_id'] = n['node_id'] + 'S'
        m['answers'] = {'甲': n['answers']['乙'], '乙': n['answers']['甲']}
        m.pop('category', None)
        swapped.append(m)
    with open(os.path.join(D, 'IRR_SUBSET_SWAPPED.jsonl'), 'w', encoding='utf-8') as f:
        for n in swapped:
            f.write(json.dumps(n, ensure_ascii=False) + '\n')

    print('normal=%d swapped=%d real_model_calls=0' % (len(subset), len(swapped)))
    print('positions:', [n['position'] for n in subset])


if __name__ == '__main__':
    main()
