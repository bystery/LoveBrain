# -*- coding: utf-8 -*-
"""judge_swap_v1_2：换序实验语义修正（mainline_review_20261026/REVIEW.md §三.2）。

v1_1 两处实错（已离线复现，不复旧文件）：
  1) user_payload(swapped=True) 生成 {'乙':b,'甲':a}——标签↔内容绑定没换，
     只换了 JSON 序列化顺序；它测的是"出现顺序"，不是位置偏置。
  2) normalize_rec 的 g[sw[k]]=g.pop(k) 原地覆盖：双键时后写键值被先写覆盖
     （甲1乙3 → 只剩甲1），单键时换过去又换回来（等于没换）。

v1_2 语义（供 A3 校准与后续裁判批使用）：
  - swap_payload()：真·绑定交换 {甲:b, 乙:a}，序列化顺序保持甲先乙后。
    此时裁判的 pref=甲 指的是原乙内容，normalize 用 swap_labels 还原坐标。
  - swap_labels()：非破坏性整字典重建，任意键组合（双键/单键/缺键/空）都不丢边。
  - 旧 repeat_swapped 数据的解释工具 reinterpret_v1_1_swapped()：
    旧 payload 绑定未换 → 判词坐标与 primary 相同，禁用转置；
    据此重算的翻转率 = 纯呈现顺序效应 + 噪声，仅供探索。
"""
import json

SW = {'甲': '乙', '乙': '甲'}


def swap_payload(node_evidence, answer_a, answer_b, swapped):
    """构造裁判所见输入。swapped=False: {甲:a,乙:b}；True: {甲:b,乙:a}（绑定交换）。"""
    left, right = (answer_b, answer_a) if swapped else (answer_a, answer_b)
    return json.dumps({'evidence': node_evidence,
                       'answers': {'甲': left, '乙': right}}, ensure_ascii=False)


def swap_labels(d):
    """甲乙标签互换（非破坏）。返回新字典，任意键子集不丢边、不回写原字典。"""
    if not isinstance(d, dict):
        return d
    return {SW.get(k, k): v for k, v in d.items()}


def normalize_v1_2(rec):
    """换序判词 → 正序坐标。claims[].who、goal/action_basis/insufficient_info/
    protocol_anomaly 的甲乙键、pref 全部经 swap_labels；parsed 缺失原样返回。"""
    import copy
    r = copy.deepcopy(rec)
    p = r.get('parsed')
    if not p:
        return r
    for c in p.get('claims') or []:
        if c.get('who') in SW:
            c['who'] = SW[c['who']]
    for key in ('goal', 'insufficient_info', 'protocol_anomaly'):
        if isinstance(p.get(key), dict):
            p[key] = swap_labels(p[key])
            if isinstance(p[key].get('action_basis'), dict):
                p[key]['action_basis'] = swap_labels(p[key]['action_basis'])
    if p.get('pref') in SW:
        p['pref'] = SW[p['pref']]
    return r


def reinterpret_v1_1_swapped(primary_pref, swapped_pref):
    """旧 v1_1 数据解释：payload 绑定未换 → 两轮判词同一坐标系，直接比较。

    返回 ('same'|'flip', 说明)。v1_1 分析器的 57.1% 翻转率把本比较错误地加了
    一次转置（normalize_rec），混入坐标转换误差；重解释不转置。
    """
    if primary_pref == swapped_pref:
        return 'same', '同坐标直接比较：一致'
    return 'flip', '同坐标直接比较：不一致（呈现顺序效应或噪声）'


SELFTEST = [
    # swap_labels：不同分数、不同内容、单键、空、非目标键都不丢边
    ('labels_both', swap_labels({'甲': 1, '乙': 3}), {'乙': 1, '甲': 3}),
    ('labels_one', swap_labels({'甲': 1}), {'乙': 1}),
    ('labels_none', swap_labels({}), {}),
    ('labels_other', swap_labels({'丙': 1, '甲': 2}), {'丙': 1, '乙': 2}),
    ('labels_text', swap_labels({'甲': '内容A', '乙': '内容B'}), {'乙': '内容A', '甲': '内容B'}),
    # swap_payload：绑定交换、顺序稳定
    ('payload_normal', json.loads(swap_payload('ev', 'A', 'B', False))['answers'], {'甲': 'A', '乙': 'B'}),
    ('payload_swapped', json.loads(swap_payload('ev', 'A', 'B', True))['answers'], {'甲': 'B', '乙': 'A'}),
    ('payload_order', list(json.loads(swap_payload('ev', 'A', 'B', True))['answers'].keys()), ['甲', '乙']),
]


def _selftest():
    ok = 0
    for name, got, want in SELFTEST:
        assert got == want, (name, got, want)
        ok += 1
    # 真实形状冒烟：goal 带 action_basis 双侧不丢
    rec = {'parsed': {'pref': '甲', 'goal': {'甲': 3, '乙': 1, 'action_basis': {'甲': 'x', '乙': 'y'}},
                      'insufficient_info': {'乙': '提示不足'}, 'protocol_anomaly': {'甲': 0, '乙': 2},
                      'claims': [{'who': '甲', 'claim': 'c1'}, {'who': '乙', 'claim': 'c2'}]}}
    n = normalize_v1_2(rec)
    assert n['parsed']['pref'] == '乙'
    assert n['parsed']['goal'] == {'乙': 3, '甲': 1, 'action_basis': {'乙': 'x', '甲': 'y'}}
    assert n['parsed']['insufficient_info'] == {'甲': '提示不足'}
    assert n['parsed']['protocol_anomaly'] == {'乙': 0, '甲': 2}
    assert {c['who'] for c in n['parsed']['claims']} == {'乙', '甲'}
    assert rec['parsed']['pref'] == '甲'  # 原记录不被改写
    ok += 1
    print('judge_swap_v1_2 selftest: %d checks OK' % ok)


if __name__ == '__main__':
    _selftest()
