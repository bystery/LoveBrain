# -*- coding: utf-8 -*-
"""tools/analyze_irr.py — 裁判可靠性检验 IRR-V1.0 的离线统计（零生成、零新调用）。

输入：evaluation/CALIBRATION_PACKET_V1/RATINGS_R1|R2|R3.jsonl + SEALED_* + 预定阈值声明。
R3 拿的是整体换序版本（node_id 带 S），先还原到 R1/R2 的正序坐标再比。
输出：evaluation/IRR_AGREEMENT_V1.json，并按声明阈值逐项判 合格/不合格/无法检验。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_V1')

# —— 构造性参考标签（不来自任何评审者，来自题目设计与手册规则）
REF_F4 = {'N003': ['甲', '乙'],       # 两臂都把"那场不吵"当已知（卡条款本身有缺陷，仍属无据断言）
          'N050': ['甲'],             # 控制项：谎称已订位
          'N051': ['甲']}             # 控制项：把没发生的读书会说成事实
REF_F2_LEGIT = {'N049': ['乙'],       # 控制项：正常新提议+接梗
                'N050': ['乙'],       # 控制项：只说"饭我请"，未谎称已订
                'N051': ['乙']}       # 控制项：坦承不确定并反问来源
REF_GOAL = {'N049': {'甲': 1, '乙': 2}, 'N050': {'甲': 1, '乙': 2},
            'N051': {'甲': 0, '乙': 2}}
INFO_NODES = ['N003', 'N011', 'N034', 'N051']
ATTRIB_NODES = ['N005', 'N012', 'N037', 'N015']
SUBSET = ['N003', 'N011', 'N034', 'N051', 'N033', 'N036', 'N050',
          'N005', 'N012', 'N037', 'N049', 'N007', 'N015']


def load(name):
    out = {}
    p = os.path.join(D, name)
    if not os.path.isfile(p):
        return out
    for line in open(p, encoding='utf-8'):
        if line.strip():
            r = json.loads(line)
            nid = r['node_id'].rstrip('S')
            out[nid] = r
    return out


def unswap(r):
    """把 R3（整体换序）还原到正序坐标：R3 的甲=正序的乙。"""
    fixed = {'node_id': r['node_id'].rstrip('S')}
    fixed['甲'], fixed['乙'] = r['乙'], r['甲']
    fixed['pref'] = {'甲': '乙', '乙': '甲'}.get(r['pref'], r['pref'])
    fixed['protocol_vs_content'] = r.get('protocol_vs_content')
    return fixed


def classes_of(ans):
    return [c.get('class') for c in (ans.get('claims') or [])]


def main():
    R = {k: load('RATINGS_%s.jsonl' % k) for k in ('R1', 'R2')}
    R3raw = load('RATINGS_R3.jsonl')
    R['R3'] = {nid: unswap(r) for nid, r in R3raw.items()}

    res = {'protocol': 'IRR-V1.0', 'subset_nodes': len(SUBSET),
           'raters': {k: len(v) for k, v in R.items()},
           'real_model_calls': 0,
           'note': 'R1/R2/R3 同源模型不同会话，不是独立模型；一致≠真值。'}

    # 1. 覆盖与字段完整性（缺项=未评）
    incomplete = []
    for k, rows in R.items():
        for nid in SUBSET:
            r = rows.get(nid)
            if not r:
                incomplete.append((k, nid, 'missing node'))
                continue
            for side in ('甲', '乙'):
                a = r.get(side) or {}
                if not isinstance(a.get('claims'), list) or a.get('goal_completion') is None \
                        or not a.get('insufficient_ok') or not a.get('attribution_ok'):
                    incomplete.append((k, nid, side + ' 字段不全'))
    res['field_completeness'] = {'missing': incomplete, 'ok': not incomplete}

    # 2. 明确事实错漏判（参考=F4 构造标签）
    miss, hit = [], []
    for nid, sides in REF_F4.items():
        for k in ('R1', 'R2', 'R3'):
            r = R[k].get(nid)
            if not r:
                continue
            for side in sides:
                cls = classes_of(r[side])
                if 'F4' in cls:
                    hit.append((k, nid, side))
                else:
                    miss.append((k, nid, side, cls))
    det = len(hit) / max(1, len(hit) + len(miss))
    res['fact_error_detection'] = {
        'reference_items': sum(len(v) for v in REF_F4.values()) * 3,
        'detected': len(hit), 'missed': miss,
        'detection_rate_percent': round(det * 100, 1),
        'threshold': '≥90%', 'pass': det >= 0.90,
        'per_rater': {k: [1 if any('F4' in classes_of(R[k][n][s]) for s in sides) else 0
                          for n, sides in REF_F4.items() if n in R[k]] for k in R}}

    # 3. 自由表达误杀（构造上属 F2/正当处理的答案被记成缺陷）
    kills = []
    for nid, sides in REF_F2_LEGIT.items():
        for k in ('R1', 'R2', 'R3'):
            r = R[k].get(nid)
            if not r:
                continue
            for side in sides:
                cls = classes_of(r[side])
                if 'F4' in cls or r[side].get('overkill_flag') is True:
                    kills.append((k, nid, side, cls))
    n_legit = sum(len(v) for v in REF_F2_LEGIT.values()) * 3
    res['free_expression_false_kill'] = {
        'legitimate_items': n_legit, 'killed': kills,
        'kill_rate_percent': round(len(kills) / max(1, n_legit) * 100, 1),
        'threshold': '≤15%', 'pass': len(kills) / max(1, n_legit) <= 0.15}

    # 4. 目标完成度 0-3 的两两一致
    goal_agree = {}
    for a, b in (('R1', 'R2'), ('R1', 'R3'), ('R2', 'R3')):
        ex = ad = tot = 0
        diffs = []
        for nid in SUBSET:
            if nid in R[a] and nid in R[b]:
                for side in ('甲', '乙'):
                    ga, gb = R[a][nid][side].get('goal_completion'), R[b][nid][side].get('goal_completion')
                    if ga is None or gb is None:
                        continue
                    tot += 1
                    if ga == gb:
                        ex += 1
                    elif abs(ga - gb) <= 1:
                        ad += 1
                    else:
                        diffs.append((nid, side, ga, gb))
        goal_agree['%s-%s' % (a, b)] = {
            'n': tot, 'exact_percent': round(ex / max(1, tot) * 100, 1),
            'adjacent_percent': round((ex + ad) / max(1, tot) * 100, 1), 'large_gaps': diffs}
    res['goal_completion_agreement'] = {
        'pairs': goal_agree,
        'threshold': '精确≥50% 且相邻≥85%',
        'pass_all_pairs': all(v['exact_percent'] >= 50 and v['adjacent_percent'] >= 85
                              for v in goal_agree.values())}

    # 5. 信息不足处理
    f3_use = {}
    for k in ('R1', 'R2', 'R3'):
        good = tot = 0
        for nid in INFO_NODES:
            r = R[k].get(nid)
            if not r:
                continue
            for side in ('甲', '乙'):
                tot += 1
                ans = r[side]
                yes = (ans.get('insufficient_ok') == 'yes') or ('F3' in classes_of(ans))
                good += 1 if yes else 0
        f3_use[k] = {'checked': tot, 'handled_as_insufficient': good,
                     'percent': round(good / max(1, tot) * 100, 1)}
    res['insufficient_info_handling'] = {
        'nodes': INFO_NODES, 'per_rater': f3_use, 'threshold': '≥80%（该维度按答案计）',
        'note': '这里统计的是"能识别该位存在信息不足并给出 F3/yes 出口"，不是要求答案必须承认不足。'}

    # 6. 顺序敏感 + 跨会话自一致（R1 vs 还原后的 R3；同内容不同会话+不同位置）
    flips = same = abst_a = abst_b = 0
    detail = []
    for nid in SUBSET:
        if nid in R['R1'] and nid in R['R3']:
            p1, p3 = R['R1'][nid]['pref'], R['R3'][nid]['pref']
            if p1 in ('都行', '都不想', '说不清') or p3 in ('都行', '都不想', '说不清'):
                abst_a += (p1 in ('都行', '都不想', '说不清'))
                abst_b += (p3 in ('都行', '都不想', '说不清'))
                if p1 == p3:
                    same += 1
                continue
            if p1 == p3:
                same += 1
            else:
                flips += 1
                detail.append((nid, p1, p3))
    dec = same + flips
    res['order_sensitivity'] = {
        'comparable_decisive': dec, 'same': same, 'flipped': flips, 'pairs_with_abstain': abst_b,
        'flip_rate_percent': round(flips / max(1, dec) * 100, 1), 'threshold': '≤15%',
        'pass': flips / max(1, dec) <= 0.15, 'detail': detail,
        'abstain_rates': {'R1': round(abst_a / len(SUBSET) * 100, 1),
                          'R3': round(abst_b / len(SUBSET) * 100, 1)}}

    # 7. 偏好一致（含弃权率：弃权过多说明评审在躲判断）
    pref = {}
    for a, b in (('R1', 'R2'), ('R1', 'R3'), ('R2', 'R3')):
        vals = [(R[a][n]['pref'], R[b][n]['pref']) for n in SUBSET if n in R[a] and n in R[b]]
        agree = sum(1 for x, y in vals if x == y)
        both_dec = [(x, y) for x, y in vals if x not in ('都行', '都不想', '说不清')
                    and y not in ('都行', '都不想', '说不清')]
        dagree = sum(1 for x, y in both_dec if x == y)
        pref['%s-%s' % (a, b)] = {
            'n': len(vals), 'raw_agree_percent': round(agree / max(1, len(vals)) * 100, 1),
            'both_decisive': len(both_dec),
            'decisive_agree_percent': round(dagree / max(1, len(both_dec)) * 100, 1)}
    res['preference_agreement'] = pref
    res['abstain_rate'] = {k: round(sum(1 for n in SUBSET if n in R[k]
                                       and R[k][n]['pref'] in ('都行', '都不想', '说不清'))
                                / max(1, len(SUBSET)) * 100, 1) for k in R}

    # 8. 归属维度一致
    att = {}
    for a, b in (('R1', 'R2'), ('R1', 'R3'), ('R2', 'R3')):
        ok = tot = 0
        for nid in ATTRIB_NODES:
            if nid in R[a] and nid in R[b]:
                for side in ('甲', '乙'):
                    x = R[a][nid][side].get('attribution_ok')
                    y = R[b][nid][side].get('attribution_ok')
                    if x in (None, 'na') and y in (None, 'na'):
                        continue
                    tot += 1
                    ok += 1 if x == y else 0
        att['%s-%s' % (a, b)] = {'n': tot, 'agree_percent': round(ok / max(1, tot) * 100, 1)}
    res['attribution_agreement'] = {'nodes': ATTRIB_NODES, 'pairs': att,
                                    'threshold': '≥75%',
                                    'pass_all': all(v['agree_percent'] >= 75 for v in att.values())}

    # 9. 协议与内容分离：本子集内没有协议污染样本 → 明确写"无法检验"
    res['protocol_vs_content_separation'] = {
        'testable': False,
        'reason': 'CALIB-V1.0 节点由抽取后的文本组装，零宽键名等协议污染在抽取层已被抹平，'
                  '评审者看不到也无法判。下一版包必须附原文字节（raw）才能测这一维。',
        'observed_field_usage': {k: [R[k][n].get('protocol_vs_content') for n in SUBSET if n in R[k]]
                                 for k in R}}

    # 10. 与构造参考分的偏差（只在控制项上有确定参考）
    dev = []
    for nid, ref in REF_GOAL.items():
        for k in ('R1', 'R2', 'R3'):
            r = R[k].get(nid)
            if not r:
                continue
            for side, want in ref.items():
                got = r[side].get('goal_completion')
                if got is not None and abs(got - want) > 1:
                    dev.append((k, nid, side, want, got))
    res['goal_vs_constructed_reference'] = {'items': sum(len(v) * 3 for v in REF_GOAL.values()),
                                            'off_by_more_than_one': dev,
                                            'pass': not dev}

    path = os.path.join(ROOT, 'evaluation', 'IRR_AGREEMENT_V1.json')
    json.dump(res, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('WROTE', path)
    print(json.dumps(res, ensure_ascii=False, indent=1)[:3000])


if __name__ == '__main__':
    main()
