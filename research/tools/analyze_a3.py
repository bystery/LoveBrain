# -*- coding: utf-8 -*-
"""tools/analyze_a3.py — A3 小校准分析（预登记于批内数据收齐前：2026-10-26 05:50 写就，live 批仍在跑）。

口径全部来自 MANUAL-V2.0-A3 §4 与包 FROZEN_MANIFEST 的 known_limitations：
  1 覆盖：各 kind/session 分母、传输/解析失败、not_rated 逐项、基础设施失败格单列（不进任何分母）；
  2 行动分落区间率（甲/乙分列；甲=设计上的合格侧，乙=缺陷侧）；
  3 fact_risk 检出（乙侧内嵌断言节点）/ 甲侧误旗率（甲按构造无断言，任何旗=误报）；
  4 电池符合率（主张 expected=unfounded*；受保护 expected=true）；
  5 pref 方向符合率（全部节点期望 pref=甲——包已声明的局限，不得外推为无位置偏差）；
  6 SR 重测稳定（同会话同对 同序两轮 pref/action 一致性）；
  7 换序位置效应：swapped 判词经 judge_swap_v1_2.normalize 回正序坐标，与 SR 同序轮比较 same/flip；
  8 三会话两两一致（pref 精确、action 精确/邻档）；
  9 关键率给 Clopper-Pearson 95% CI（公式与 analyze_irr_v2 同源修正版）。
分母纪律：not_rated/解析失败/基础设施格逐项剔除并计数，绝不冒充通过。
"""
import json
import math
import os
import sys
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PK = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_A3_20261026')
sys.path.insert(0, os.path.join(ROOT, 'tools'))
from judge_swap_v1_2 import normalize_v1_2  # noqa: E402  非破坏坐标归一


def clopper(k, n, a=0.05):
    if n <= 0:
        return None
    p = k / n
    lo = 0.0 if k == 0 else _beta_q(a / 2, k, n - k + 1)
    hi = 1.0 if k == n else _beta_q(1 - a / 2, k + 1, n - k)
    return (round(lo, 4), round(hi, 4))


def _beta_q(q, aa, bb):
    """Beta 分位数（二分法，精度 ~1e-12；与 analyze_irr_v2 修正后的 lgamma 口径一致）。"""
    def cdf(x):
        return _betainc(aa, bb, x)

    lo, hi = 0.0, 1.0
    for _ in range(60):
        mid = (lo + hi) / 2
        if cdf(mid) < q:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def _betainc(a, b, x):
    """正则化不完全贝塔 I_x(a,b)，NR betacf 直接式（无递归）。"""
    if x <= 0:
        return 0.0
    if x >= 1:
        return 1.0
    bt = math.exp(math.lgamma(a + b) - math.lgamma(a) - math.lgamma(b)
                  + a * math.log(x) + b * math.log(1 - x))
    if x < (a + 1) / (a + b + 2):
        return bt * _cont_frac(a, b, x) / a
    return 1.0 - bt * _cont_frac(b, a, 1 - x) / b


def _cont_frac(a, b, x, itmax=200, eps=3e-12):
    qab, qap, qam = a + b, a + 1.0, a - 1.0
    c, d = 1.0, 1.0 - qab * x / qap
    if abs(d) < 1e-300:
        d = 1e-300
    d = 1 / d
    h = d
    for m in range(1, itmax + 1):
        m2 = 2 * m
        aa = m * (b - m) * x / ((qam + m2) * (a + m2))
        d = 1 + aa * d
        if abs(d) < 1e-300:
            d = 1e-300
        c = 1 + aa / c
        if abs(c) < 1e-300:
            c = 1e-300
        d = 1 / d
        h *= d * c
        aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
        d = 1 + aa * d
        if abs(d) < 1e-300:
            d = 1e-300
        c = 1 + aa / c
        if abs(c) < 1e-300:
            c = 1e-300
        d = 1 / d
        de = d * c
        h *= de
        if abs(de - 1) < eps:
            break
    return h


GOOD_REL = {'甲better', '乙bad_clear', '乙fab', '乙risky', '乙over'}   # 全部=甲为合格侧（包局限）


def main(ratings_path, interim):
    nodes = {n['node_id']: n for n in
             (json.loads(l) for l in open(os.path.join(PK, 'nodes.jsonl'), encoding='utf-8'))}
    claims = {c['id']: c for c in json.load(
        open(os.path.join(PK, 'FACT_CLAIMS_BATTERY.json'), encoding='utf-8'))}
    prot = {e['id']: e for e in json.load(
        open(os.path.join(PK, 'PROTECTED_EXPRESSIONS.json'), encoding='utf-8'))}
    recs = [json.loads(l) for l in open(ratings_path, encoding='utf-8') if l.strip()]

    cov = {'records': len(recs), 'transport_ok': 0, 'parse_ok': 0,
           'infra_failed_cells': [], 'not_rated_entries': Counter()}
    parsed = {}
    for r in recs:
        if not r['transport_ok']:
            if 'ModuleNotFoundError' in (r.get('error') or '') or 'LB_API_BASE' in (r.get('error') or ''):
                cov['infra_failed_cells'].append({'req_id': r['req_id'], 'error': (r.get('error') or '')[:60]})
            continue
        cov['transport_ok'] += 1
        if not r.get('parse_ok'):
            continue
        cov['parse_ok'] += 1
        p = r['parsed']
        if r['kind'] == 'swap':
            # 换序行归一回正序坐标（V1 教训：漏归一全混杂；V1_1 教训：normalize_v1_2
            # 只认 V1.1 旧 schema——pref/goal/claims 换了，V2.0 的 answers.{甲,乙} 没换。
            # 完整归一 = normalize_v1_2 + answers 键对换（行动分/事实旗/受保护随边走）
            p = normalize_v1_2({'parsed': p})['parsed']
            _sw = {'甲': '乙', '乙': '甲'}
            if isinstance(p.get('answers'), dict):
                p['answers'] = {_sw.get(k, k): v for k, v in p['answers'].items()}
            r = dict(r, parsed=p)
        for e in (p.get('not_rated') or []):
            cov['not_rated_entries'][str(e)[:60]] += 1
        parsed[r['req_id']] = r

    # ── 2 行动分落区间 ──
    act = {'甲': Counter(), '乙': Counter()}
    for rid, r in parsed.items():
        e = r['parsed']
        if r['kind'] not in ('primary', 'retest_same', 'swap'):
            continue
        node = nodes[r['node_id']]
        lo, hi = node['reference']['allowed_goal_score_range']
        for side in ('甲', '乙'):
            sc = (e.get('answers') or {}).get(side, {}).get('action_score')
            if not isinstance(sc, int):
                act[side]['missing'] += 1
                continue
            act[side]['n'] += 1
            act[side]['in_range' if lo <= sc <= hi else 'out_range'] += 1

    # ── 3 fact_risk 检出/误旗 ──
    det = Counter()
    for rid, r in parsed.items():
        if r['kind'] not in ('primary', 'retest_same', 'swap'):
            continue
        node = nodes[r['node_id']]
        sides = r['parsed'].get('answers') or {}
        f_jia = (sides.get('甲') or {}).get('fact_risk_flags') or []
        f_yi = (sides.get('乙') or {}).get('fact_risk_flags') or []
        det['甲_clean_nodes_n'] += 1
        if f_jia:
            det['甲_false_flag'] += 1
        if node['reference']['claims_embedded']:
            det['乙_claim_nodes_n'] += 1
            if f_yi:
                det['乙_detected'] += 1

    # ── 4 电池 ──
    bat = Counter()
    for rid, r in parsed.items():
        if r['kind'] == 'battery_fact':
            bat['fact_n'] += 1
            if r['parsed'].get('verdict') == 'unfounded':
                bat['fact_unfounded'] += 1
        elif r['kind'] == 'battery_protected':
            bat['prot_n'] += 1
            if r['parsed'].get('protected') is True:
                bat['prot_true'] += 1

    # ── 5 pref 方向 ──
    pref = Counter()
    for rid, r in parsed.items():
        if r['kind'] in ('primary', 'retest_same', 'swap'):
            pref['n'] += 1
            p = r['parsed'].get('pref')
            pref[str(p)] += 1

    # ── 6 SR 重测稳定 / 7 换序 ──
    same_order = {r['node_id']: r for r in parsed.values() if r['kind'] == 'retest_same'}
    swap_order = {r['node_id']: r for r in parsed.values() if r['kind'] == 'swap'}
    s1 = {r['node_id']: r for r in parsed.values()
          if r['kind'] == 'primary' and r['session'] == 'S1'}
    rt = Counter()
    for nid, rr in same_order.items():
        base = s1.get(nid)
        if not base:
            continue
        rt['n'] += 1
        if base['parsed'].get('pref') == rr['parsed'].get('pref'):
            rt['pref_same'] += 1
        a1 = (base['parsed']['answers']['甲'].get('action_score'))
        a2 = (rr['parsed']['answers']['甲'].get('action_score'))
        if a1 == a2:
            rt['act_甲_exact'] += 1
        elif isinstance(a1, int) and isinstance(a2, int) and abs(a1 - a2) <= 1:
            rt['act_甲_adjacent'] += 1
    sw = Counter()
    swap_detail = []
    for nid, rr in swap_order.items():
        base = same_order.get(nid)
        if not base:
            continue
        sw['n'] += 1
        # 载入阶段已归一到正序坐标，这里直接比较
        norm = rr['parsed']
        pf_base, pf_swap = base['parsed'].get('pref'), norm.get('pref')
        item = {'node': nid, 'same_pref': pf_base, 'swap_pref_normalized': pf_swap}
        if pf_base == pf_swap:
            sw['same'] += 1
        else:
            sw['flip'] += 1
            swap_detail.append(item)

    # ── 8 三会话两两一致 ──
    sess = defaultdict(dict)
    for r in parsed.values():
        if r['kind'] == 'primary':
            sess[r['session']][r['node_id']] = r['parsed']
    agree = {}
    for s_x, s_y in (('S1', 'S2'), ('S1', 'S3'), ('S2', 'S3')):
        c = Counter()
        for nid, px in sess[s_x].items():
            py = sess[s_y].get(nid)
            if not py:
                continue
            c['n'] += 1
            if px.get('pref') == py.get('pref'):
                c['pref_exact'] += 1
            ax = (px.get('answers') or {}).get('甲', {}).get('action_score')
            ay = (py.get('answers') or {}).get('甲', {}).get('action_score')
            if ax == ay:
                c['act_甲_exact'] += 1
            elif isinstance(ax, int) and isinstance(ay, int) and abs(ax - ay) <= 1:
                c['act_甲_adjacent'] += 1
        agree['%s-%s' % (s_x, s_y)] = dict(c)

    def rate(k, n_):
        return None if not n_ else {'k': k, 'n': n_, 'rate': round(k / n_, 4),
                                    'ci95': clopper(k, n_)}

    out = {
        'tool': 'analyze_a3.py V1 (pre-registered 2026-10-26 05:50, batch in flight)',
        'interim': bool(interim),
        'ratings_path': ratings_path,
        'coverage': {**cov, 'not_rated_entries': dict(cov['not_rated_entries'])},
        'action_range': {s: {'n': act[s]['n'], 'in_range': act[s]['in_range'],
                             'in_range_rate': rate(act[s]['in_range'], act[s]['n']),
                             'missing': act[s]['missing']} for s in ('甲', '乙')},
        'fact_risk': dict(det),
        'fact_risk_rates': {'乙_detection': rate(det['乙_detected'], det['乙_claim_nodes_n']),
                            '甲_false_flag': rate(det['甲_false_flag'], det['甲_clean_nodes_n'])},
        'battery': dict(bat),
        'battery_rates': {'fact_unfounded': rate(bat['fact_unfounded'], bat['fact_n']),
                          'protected_true': rate(bat['prot_true'], bat['prot_n'])},
        'pref': dict(pref),
        'retest_vs_S1': dict(rt),
        'swap_vs_sameorder': {**dict(sw), 'flip_detail': swap_detail},
        'pairwise_sessions': agree,
        'expected_pref_note': '全部节点期望 pref=甲（包 known_limitation；pref 符合率不得外推为无位置偏差）',
        'real_model_calls': 0,
    }
    path = os.path.join(ROOT, 'evaluation',
                        'A3_CALIBRATION_ANALYSIS_%s.json' % ('INTERIM' if interim else 'V1_2'))
    json.dump(out, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('WROTE', path)
    print(json.dumps({k: out[k] for k in ('coverage', 'action_range', 'fact_risk_rates',
                                          'battery_rates', 'pref', 'retest_vs_S1',
                                          'swap_vs_sameorder')},
                     ensure_ascii=False, default=str)[:1200])


if __name__ == '__main__':
    interim = '--final' not in sys.argv
    rp = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith('--') \
        else os.path.join(ROOT, 'logs', 'a3_judge_live_live1', 'RATINGS_A3.jsonl')
    main(rp, interim)
