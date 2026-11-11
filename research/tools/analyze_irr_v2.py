# -*- coding: utf-8 -*-
"""tools/analyze_irr_v2.py — 裁判可靠性分析 V2（修掉 v1 的 7 处口径缺陷；零生成、零新调用）。

输入：run_judge_batch_v1_1.py 产出的 RATINGS_R{1,2,3}.jsonl（含 parsed 判词）+
      evaluation/CALIBRATION_PACKET_V1_1/{REFERENCE_LABELS_V1_1.json, nodes_public.jsonl}。
本工具属可信侧分析器，只在**判词收齐后**运行；它在任何真实判词存在之前写死了下面的
预注册口径（pre-registered），不以判词结果回调。

预注册口径（2026-10-25，先于任何真实判词固定）：
  R1 分母：事实检出 = reference_provenance ∈ {constructed_reference, human_reviewed} 且
     F4 型（statement_type∈{事实,类指常识} ∧ evidence_status=未提供 ∧ stance=确定）的主张。
     disputed 主张计入检出测量（HANDOFF 口径：不计入候选胜负——本工具只测裁判，不产生候选胜负）。
     mechanical_only(1008) 绝不当分母，只作信息列。
  R2 认证线：比率维 min_n=20（分母<20 → "insufficient_evidence"，禁止 0/N 冒充可靠性）；
     信息不足维 min_n=40 个位置（节点×臂）。
     恒报 Clopper-Pearson 95% 区间；点估计过线但区间下界不过线 → "point_pass_ci_fail"。
  R3 逐主张匹配：判词主张 ↔ 参考主张按归一化文本（去零宽/空白）精确或包含匹配；
     F4 命中必须落到具体主张，禁止"整段出现任意 F4"顶替。
  R4 误杀：对参考受保护主张（trusted 非 F4）被任一判词按无据断言四轴标记 → 假阳性；
     不读裁判自报 overkill（v1 缺陷 4）。
  R5 引用有效性：evidence_quote 必须是判词所见 payload（复刻 user_payload）的逐字子串
     （归一化零宽/空白后）。无效引用列清单。
  R6 目标 0–3：类型校验（int 0..3）；跨评审者一致（精确/相邻，声明阈值 精确≥50%/相邻≥85%，
     一致率≠真值）；参考侧无数字金标（goal_reference.goal 是文字描述，包设计=不写唯一标准答案），
     对参考只做接地探索：allowed_actions 覆盖 / forbidden 命中 + 典型错例，不产生数字正确率。
  R7 顺序与重跑分开配对：repeat_swapped（换序）与 repeat_same（同序重测）分别给
     6×6 完整转移矩阵（甲/乙/都行/都不想/说不清/未评），并给 聚合转移（决定→平局等）。
     换序行归一化回正序坐标再比（who/pref 甲乙互换）。
  R8 平局/都不想/说不清/未评分开：都行=有效平局；都不想=双方不愿发；说不清=未知；
     未评=缺判词或解析失败。decisive 统计只算 甲/乙。
  R9 分维度授权输出：fact_detection / free_expression_false_kill / insufficient_info /
     goal_vs_reference / attribution / protocol_separation 各自独立判
     qualified / not_qualified / insufficient_evidence / point_pass_ci_fail。

用法：
  PYTHONIOENCODING=utf-8 python tools/analyze_irr_v2.py --ratings-dir logs/judge_v1_1_live_<tag> \
      [--split dev|validation|all] [--out evaluation/IRR_AGREEMENT_V2_<...>.json]
调试轮只准 --split dev；冻结验证只准对 validation 跑一次（纪律见
JUDGE_QUALITY_THRESHOLDS_DECLARED_V1.md 与 WORKER_EXECUTION 包C）。
"""
import argparse
import json
import math
import os
import re
import sys
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PK = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_V1_1')

MIN_N_RATE = 20          # 事实检出 / 误杀 / 一致率对 认证线
MIN_N_INSUF = 40         # 信息不足 认证线（位置数）
TH_FACT = 0.90           # 事实检出 ≥90%（派发前已声明）
TH_KILL = 0.15           # 误杀 ≤15%（派发前已声明）
TH_INSUF = 0.80          # 信息不足正确 ≥80%（派发前已声明）
TH_ORDER_FLIP = 0.15     # 换序翻转 ≤15%（派发前已声明）
TRUSTED = ('constructed_reference', 'human_reviewed')
ZW = re.compile(r'[\u200b-\u200f\u2060\ufeff]')


def norm(s):
    return ZW.sub('', str(s or '')).replace(' ', '').replace('\u3000', '').strip()


def clopper_lower(hit, n, alpha=0.05):
    """Clopper-Pearson 95% 下界：解 I_p(hit, n-hit+1) = alpha。"""
    if n <= 0:
        return 0.0
    if hit <= 0:
        return 0.0
    a, b = hit, n - hit + 1
    log_inv_b = math.lgamma(a + b) - math.lgamma(a) - math.lgamma(b)  # log(1/B(a,b))

    def ibeta(x):
        """正则化不完全 beta I_x(a,b)，数值积分（n≤~60，步长足够）。"""
        if x <= 0:
            return 0.0
        if x >= 1:
            return 1.0
        steps = 20000
        total = 0.0
        dt = x / steps
        for i in range(steps):
            t = (i + 0.5) * dt
            total += (t ** (a - 1)) * ((1 - t) ** (b - 1)) * dt
        return total * math.exp(log_inv_b)

    lo, hi = 0.0, 1.0
    for _ in range(60):
        mid = (lo + hi) / 2
        if ibeta(mid) < alpha:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def user_payload(node, swapped):
    """复刻 run_judge_batch_v1_1.user_payload（判词所见输入）。"""
    a, b = node['answers']['甲'], node['answers']['乙']
    left, right = (b, a) if swapped else (a, b)
    lab = ('乙', '甲') if swapped else ('甲', '乙')
    return json.dumps({'evidence': node['evidence'],
                       'answers': {lab[0]: left, lab[1]: right}}, ensure_ascii=False)


def load_ratings(ratings_dir):
    """→ {rater: {'primary': {nid: rec}, 'repeat_same': {...}, 'repeat_swapped': {...}}}"""
    out = {}
    for fn in sorted(os.listdir(ratings_dir)):
        if not (fn.startswith('RATINGS_') and fn.endswith('.jsonl')):
            continue
        rater = fn[len('RATINGS_'):-len('.jsonl')]
        rows = defaultdict(dict)
        for line in open(os.path.join(ratings_dir, fn), encoding='utf-8'):
            if not line.strip():
                continue
            r = json.loads(line)
            rows[r.get('kind') or 'primary'][r['node_id']] = r
        out[rater] = rows
    return out


def normalize_rec(rec):
    """换序判词 → 正序坐标（who/pref/goal/insufficient 的甲乙互换；claims 的 who 互换）。"""
    import copy
    r = copy.deepcopy(rec)
    parsed = r.get('parsed')
    if not parsed:
        return r
    sw = {'甲': '乙', '乙': '甲'}
    for c in parsed.get('claims') or []:
        if c.get('who') in sw:
            c['who'] = sw[c['who']]
    g = parsed.get('goal') or {}
    for k in ('甲', '乙'):
        if k in g:
            g[sw[k]] = g.pop(k)
    ab = g.get('action_basis') or {}
    for k in ('甲', '乙'):
        if k in ab:
            ab[sw[k]] = ab.pop(k)
    ii = parsed.get('insufficient_info') or {}
    for k in ('甲', '乙'):
        if k in ii:
            ii[sw[k]] = ii.pop(k)
    pa = parsed.get('protocol_anomaly') or {}
    for k in ('甲', '乙'):
        if k in pa:
            pa[sw[k]] = pa.pop(k)
    if parsed.get('pref') in sw:
        parsed['pref'] = sw[parsed['pref']]
    return r


def pref_of(rec):
    parsed = rec.get('parsed')
    if not rec.get('transport_ok'):
        return '未评'
    if not rec.get('parse_ok') or not parsed:
        return '未评'
    v = parsed.get('pref')
    return v if v in ('甲', '乙', '都行', '都不想', '说不清') else '未评'


def is_violation_claim(c):
    """判词四轴是否构成'无据断言'标记（MANUAL-V1.1 §2 组合规则）。"""
    return (c.get('statement_type') in ('事实', '类指常识')
            and c.get('evidence_status') == '未提供'
            and c.get('stance') == '确定')


def is_ref_f4(rc):
    """参考主张是否 F4 型（无据断言缺陷）。"""
    return (rc.get('statement_type') in ('事实', '类指常识')
            and rc.get('evidence_status') == '未提供'
            and rc.get('stance') == '确定')


def is_ref_conflict(rc):
    """参考主张是否冲突型缺陷（与输入冲突却当已成立）。"""
    return rc.get('evidence_status') == '冲突' and rc.get('stance') == '确定'


def match_ref_claim(judge_claims, ref_text):
    """逐主张匹配：归一化后精确或包含。返回匹配的判词主张列表。"""
    rt = norm(ref_text)
    if not rt:
        return []
    hits = []
    for c in judge_claims:
        jt = norm(c.get('text'))
        if not jt:
            continue
        if jt == rt or rt in jt or jt in rt:
            hits.append(c)
    return hits


def transition(pairs):
    """pairs: [(first, second)] → 6×6 矩阵 + 聚合转移。"""
    cats = ['甲', '乙', '都行', '都不想', '说不清', '未评']
    m = {a: {b: 0 for b in cats} for a in cats}
    for x, y in pairs:
        m.setdefault(x, {b: 0 for b in cats})
        m[x][y] = m[x].get(y, 0) + 1

    def agg(v):
        return {'decisive': v in ('甲', '乙'), 'tie': v == '都行',
                'unwilling': v == '都不想', 'unknown': v == '说不清', 'unrated': v == '未评'}
    ag = Counter()
    for x, y in pairs:
        ax = [k for k, on in agg(x).items() if on][0]
        ay = [k for k, on in agg(y).items() if on][0]
        ag['%s->%s' % (ax, ay)] += 1
    return {'matrix': m, 'aggregate': dict(ag), 'n_pairs': len(pairs)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ratings-dir', required=True)
    ap.add_argument('--split', default='all', choices=('dev', 'validation', 'all'))
    ap.add_argument('--out', default=None)
    ap.add_argument('--no-write', dest='write', action='store_false', default=True,
                    help='只打印报告，不写 JSON 文件')
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

    refdoc = json.load(open(os.path.join(PK, 'REFERENCE_LABELS_V1_1.json'), encoding='utf-8'))
    ref = {n['node_id']: n for n in refdoc['nodes']}
    pub = {json.loads(l)['node_id']: json.loads(l)
           for l in open(os.path.join(PK, 'nodes_public.jsonl'), encoding='utf-8') if l.strip()}

    R = load_ratings(a.ratings_dir)
    raters = sorted(R.keys())
    res = {'protocol': 'IRR-V2', 'ratings_dir': os.path.relpath(a.ratings_dir, ROOT),
           'split': a.split, 'raters': raters,
           'pre_registered': {'min_n_rate': MIN_N_RATE,
                              'min_n_insufficient_positions': MIN_N_INSUF,
                              'thresholds': {'fact_detection': '≥90%', 'false_kill': '≤15%',
                                             'insufficient_ok': '≥80%', 'order_flip': '≤15%',
                                             'goal_agreement': '精确≥50% 且相邻≥85%'},
                              'note': '口径在看到任何真实判词之前固定（见文件头 R1–R9）。'},
           'raters_note': 'R1/R2/R3 = 同一模型服务同一 prompt 的不同会话，不是三个独立模型；'
                          '一致率≠真值。本工具只测裁判，不产生候选胜负。'}

    def in_split(nid):
        if a.split == 'all':
            return True
        return ref.get(nid, {}).get('split') == a.split

    # ---------- 覆盖与字段 ----------
    cover = {}
    for rater in raters:
        prim = {nid: r for nid, r in R[rater]['primary'].items() if in_split(nid)}
        ok = sum(1 for r in prim.values() if r.get('transport_ok') and r.get('parse_ok'))
        cover[rater] = {'primary_nodes': len(prim), 'parsed_ok': ok,
                        'unrated': len(prim) - ok,
                        'repeat_same': len([n for n in R[rater]['repeat_same'] if in_split(n)]),
                        'repeat_swapped': len([n for n in R[rater]['repeat_swapped'] if in_split(n)])}
    res['coverage'] = cover

    # ---------- R8 偏好分布（分列） ----------
    dist = {}
    for rater in raters:
        c = Counter(pref_of(r) for nid, r in R[rater]['primary'].items() if in_split(nid))
        dist[rater] = dict(c)
    res['pref_distribution'] = dist

    # ---------- R3/R4 事实检出与误杀（逐主张，trusted 分母） ----------
    fact = {'ref_f4_trusted': 0, 'per_rater': {}, 'detail': [], 'disputed_in_denominator': 0,
            'not_rated_note': '判词缺失/传输失败的主张记 not_rated，不入 hit/miss 分母（未评≠漏检）'}
    kill = {'ref_protected_trusted': 0, 'per_rater': {}, 'detail': [], 'mechanical_only_informational': 0,
            'not_rated_note': '同 fact_detection'}
    conflict = {'ref_conflict_trusted': 0, 'per_rater': {}, 'detail': []}
    quote_bad = {'per_rater': defaultdict(list), 'n_invalid': 0, 'n_checked': 0}
    judge_claims_total = Counter()
    for rater in raters:
        f_hit = f_miss = nr = 0
        k_n = k_hit = k_nr = 0
        cf_n = cf_hit = 0
        for nid, node in ref.items():
            if not in_split(nid):
                continue
            rec = R[rater]['primary'].get(nid)
            if rec is None or not rec.get('transport_ok'):
                # 判词缺失/传输失败：该节点全部 trusted 主张记 not_rated，不入分母
                for arm in ('甲', '乙'):
                    for rc in node['answers'][arm]['claims']:
                        if rc['reference_provenance'] not in TRUSTED:
                            continue
                        if is_ref_f4(rc):
                            nr += 1
                        elif is_ref_conflict(rc):
                            pass
                        else:
                            k_nr += 1
                continue
            rec = R[rater]['primary'].get(nid)
            parsed = (rec or {}).get('parsed') if rec else None
            claims = (parsed or {}).get('claims') or []
            judge_claims_total[rater] += len(claims)
            # 引用有效性（R5）：逐字子串核对
            if parsed:
                seen = user_payload(pub[nid], bool(rec.get('swapped')))
                seen_n = norm(seen)
                for c in claims:
                    q = norm(c.get('evidence_quote'))
                    if not q:
                        continue
                    quote_bad['n_checked'] += 1
                    if q not in seen_n:
                        quote_bad['n_invalid'] += 1
                        quote_bad['per_rater'][rater].append(
                            {'node_id': nid, 'quote': str(c.get('evidence_quote'))[:60]})
            for arm in ('甲', '乙'):
                arm_claims = [c for c in claims if c.get('who') == arm]
                for rc in node['answers'][arm]['claims']:
                    viol_f4 = (rc['statement_type'] in ('事实', '类指常识')
                               and rc['evidence_status'] == '未提供' and rc['stance'] == '确定')
                    viol_conflict = (rc['evidence_status'] == '冲突' and rc['stance'] == '确定')
                    trusted = rc['reference_provenance'] in TRUSTED
                    if not trusted:
                        if rc['reference_provenance'] == 'mechanical_only':
                            kill['mechanical_only_informational'] += 1
                        continue
                    matches = match_ref_claim(arm_claims, rc['text'])
                    marked = [c for c in matches if is_violation_claim(c)]
                    if viol_f4:
                        fact['ref_f4_trusted'] += 1
                        if rc.get('disputed'):
                            fact['disputed_in_denominator'] += 1
                        if marked:
                            f_hit += 1
                        else:
                            f_miss += 1
                            fact['detail'].append({'rater': rater, 'node_id': nid, 'arm': arm,
                                                   'ref_text': rc['text'][:50],
                                                   'disputed': bool(rc.get('disputed'))})
                    elif viol_conflict:
                        # 参考已认定的答案缺陷（与输入冲突却当已成立）——判词标注是正确行为，
                        # 不算误杀；单列检出计数（探索性，F4 阈值不含此项）。
                        conflict['ref_conflict_trusted'] += 1
                        cf_n += 1
                        if marked:
                            cf_hit += 1
                        else:
                            conflict['detail'].append({'rater': rater, 'node_id': nid, 'arm': arm,
                                                       'ref_text': rc['text'][:50]})
                    else:
                        kill['ref_protected_trusted'] += 1
                        k_n += 1
                        if marked:
                            k_hit += 1
                            kill['detail'].append({'rater': rater, 'node_id': nid, 'arm': arm,
                                                   'ref_text': rc['text'][:50],
                                                   'disputed': bool(rc.get('disputed')),
                                                   'protected_axes': [rc['statement_type'],
                                                                      rc['evidence_status'],
                                                                      rc['stance'], rc['severity']]})
        fn = f_hit + f_miss
        fact['per_rater'][rater] = {
            'hit': f_hit, 'miss': f_miss, 'not_rated': nr, 'n': fn,
            'rate': round(f_hit / max(1, fn), 4),
            'min_n': MIN_N_RATE,
            'verdict': 'insufficient_evidence' if fn < MIN_N_RATE
            else ('qualified' if f_hit / fn >= TH_FACT else 'not_qualified'),
            'note': '各评审者分母独立列出（不把同一主张×3 当独立事实）；not_rated 不入分母'}
        kill['per_rater'][rater] = {
            'killed': k_hit, 'protected_n': k_n, 'not_rated': k_nr,
            'rate': round(k_hit / max(1, k_n), 4),
            'min_n': MIN_N_RATE,
            'verdict': 'insufficient_evidence' if k_n < MIN_N_RATE
            else ('qualified' if k_hit / k_n <= TH_KILL else 'not_qualified')}
        conflict['per_rater'][rater] = {'n': cf_n, 'hit': cf_hit,
                                        'rate': round(cf_hit / max(1, cf_n), 4)}
    res['fact_detection'] = fact
    res['free_expression_false_kill'] = kill
    res['ref_conflict_claims_detection'] = conflict
    res['quote_validity'] = {'n_checked': quote_bad['n_checked'],
                             'n_invalid': quote_bad['n_invalid'],
                             'per_rater_invalid': {k: len(v) for k, v in quote_bad['per_rater'].items()},
                             'examples': quote_bad['per_rater']}

    # ---------- 汇总维度资格（跨评审者合并测量，分母=trusted 主张数） ----------
    def qualify(rate, n, th, min_n, higher_better):
        if n < min_n:
            return 'insufficient_evidence'
        ci_lo = clopper_lower(int(round(rate * n)) if higher_better else int(round((1 - rate) * n)),
                              n)
        point_ok = (rate >= th) if higher_better else (rate <= th)
        ci_ok = (ci_lo >= th) if higher_better else (ci_lo <= th)
        if point_ok and ci_ok:
            return 'qualified'
        if point_ok:
            return 'point_pass_ci_fail'
        return 'not_qualified'

    agg_fact_n = sum(v['n'] for v in fact['per_rater'].values())
    agg_fact_hit = sum(v['hit'] for v in fact['per_rater'].values())
    agg_kill_n = sum(v['protected_n'] for v in kill['per_rater'].values())
    agg_kill = sum(v['killed'] for v in kill['per_rater'].values())
    fact_rate = agg_fact_hit / max(1, agg_fact_n)
    kill_rate = agg_kill / max(1, agg_kill_n)
    res['fact_detection']['aggregate'] = {
        'n': agg_fact_n, 'hit': agg_fact_hit, 'rate': round(fact_rate, 4),
        'ci95_lower': round(clopper_lower(agg_fact_hit, agg_fact_n), 4),
        'threshold': '≥90%', 'min_n': MIN_N_RATE,
        'informational': True,
        'note': '并池汇总仅作信息列：同一主张×3 评审者不是独立事实（WORKER_EXECUTION 包C.4）',
        'verdict': qualify(fact_rate, agg_fact_n, TH_FACT, MIN_N_RATE, True)}
    kill_keep = agg_kill_n - agg_kill  # 受保护主张未被误杀数
    kill_ci_upper = 1 - clopper_lower(kill_keep, agg_kill_n) if agg_kill_n else 1.0
    if agg_kill_n < MIN_N_RATE:
        kill_verdict = 'insufficient_evidence'
    elif kill_rate <= TH_KILL and kill_ci_upper <= TH_KILL:
        kill_verdict = 'qualified'
    elif kill_rate <= TH_KILL:
        kill_verdict = 'point_pass_ci_fail'
    else:
        kill_verdict = 'not_qualified'
    res['free_expression_false_kill']['aggregate'] = {
        'n': agg_kill_n, 'killed': agg_kill, 'rate': round(kill_rate, 4),
        'ci95_upper': round(kill_ci_upper, 4),
        'threshold': '≤15%', 'min_n': MIN_N_RATE,
        'verdict': kill_verdict}

    # ---------- R8 信息不足（位置分母，ok/wrong/na 分列） ----------
    insuf = {'positions': 0, 'per_rater': {}, 'detail_wrong': []}
    for rater in raters:
        ok = wrong = na = miss = 0
        for nid, node in ref.items():
            if not in_split(nid):
                continue
            if not (node.get('insufficient_reference') or {}).get('has_ambiguity'):
                continue
            rec = R[rater]['primary'].get(nid)
            parsed = (rec or {}).get('parsed')
            ii = (parsed or {}).get('insufficient_info') or {}
            for arm in ('甲', '乙'):
                insuf['positions'] += 1
                v = ii.get(arm)
                if not rec or not rec.get('transport_ok'):
                    miss += 1
                elif v == 'ok':
                    ok += 1
                elif v == 'wrong':
                    wrong += 1
                    insuf['detail_wrong'].append({'rater': rater, 'node_id': nid, 'arm': arm})
                elif v == 'na':
                    na += 1
                else:
                    miss += 1
        n_all = ok + wrong + na + miss
        insuf['per_rater'][rater] = {
            'ok': ok, 'wrong': wrong, 'na': na, 'missing': miss, 'n': n_all,
            'ok_rate_incl_na_missing': round(ok / max(1, n_all), 4),
            'ok_rate_excl_na_missing': round(ok / max(1, ok + wrong), 4)}
    allr = [v for v in insuf['per_rater'].values()]
    ok_all = sum(v['ok'] for v in allr)
    n_all = sum(v['n'] for v in allr)
    insuf['aggregate'] = {'positions': n_all, 'ok': ok_all,
                          'ok_rate': round(ok_all / max(1, n_all), 4),
                          'threshold': '≥80%', 'min_n': MIN_N_INSUF,
                          'verdict': 'insufficient_evidence' if n_all < MIN_N_INSUF
                          else ('qualified' if ok_all / n_all >= TH_INSUF else 'not_qualified'),
                          'note': 'na=评审者未作信息不足判定（计入分母并单列，不洗成通过）；'
                                  'wrong=把无据细节当已核实处理。'}
    res['insufficient_info'] = insuf

    # ---------- R6 目标：类型校验 + 跨评审一致 + 对参考 ----------
    goal = {'invalid_scores': [], 'agreement_pairs': {}, 'vs_reference': None,
            'action_range': {'raters': {}}}
    gvals = {}
    for rater in raters:
        vals = {}
        for nid, node in ref.items():
            if not in_split(nid):
                continue
            rec = R[rater]['primary'].get(nid)
            parsed = (rec or {}).get('parsed')
            g = (parsed or {}).get('goal') or {}
            for arm in ('甲', '乙'):
                v = g.get(arm)
                if v is None or not rec or not rec.get('transport_ok'):
                    continue
                if not isinstance(v, int) or isinstance(v, bool) or not (0 <= v <= 3):
                    goal['invalid_scores'].append({'rater': rater, 'node_id': nid,
                                                   'arm': arm, 'value': v})
                    continue
                vals[(nid, arm)] = v
        gvals[rater] = vals
    for i in range(len(raters)):
        for j in range(i + 1, len(raters)):
            ra, rb = raters[i], raters[j]
            common = sorted(set(gvals[ra]) & set(gvals[rb]))
            ex = sum(1 for k in common if gvals[ra][k] == gvals[rb][k])
            ad = sum(1 for k in common if abs(gvals[ra][k] - gvals[rb][k]) == 1)
            goal['agreement_pairs']['%s-%s' % (ra, rb)] = {
                'n': len(common), 'exact': ex, 'adjacent_within1': ex + ad,
                'exact_percent': round(ex / max(1, len(common)), 4),
                'adjacent_percent': round((ex + ad) / max(1, len(common)), 4)}

    # 参考侧没有数字金标（goal_reference.goal 是目标文字描述，数字锚点在 score_anchors；
    # 包设计=不写唯一标准答案）。因此：可认证指标=跨评审者一致率（已声明阈值）；
    # 对参考只做接地探索（允许动作范围覆盖 / 禁止动作命中），不产生数字正确率。
    goal['vs_reference_numeric'] = {
        'available': False,
        'reason': '参考侧 goal_reference.goal 为目标文字描述（6 个 dev 节点），无数值金标；'
                  '数字正确率不可测，不冒充。接地探索见 action_range / forbidden_hits。'}
    # 允许动作范围 + 禁止动作（探索性接地：覆盖率/命中 + 典型错例，不设合格线）
    ar_nodes = [(nid, node) for nid, node in ref.items() if in_split(nid)
                if (node.get('goal_reference') or {}).get('allowed_actions')
                or (node.get('goal_reference') or {}).get('forbidden')]
    ar_rows = []
    fb_rows = []
    for rater in raters:
        covered = in_range_total = 0
        fb_hit = fb_total = 0
        for nid, node in ar_nodes:
            rec = R[rater]['primary'].get(nid)
            g = ((rec or {}).get('parsed') or {}).get('goal') or {}
            ab = g.get('action_basis') or {}
            for arm in ('甲', '乙'):
                basis = norm(ab.get(arm))
                if not basis:
                    continue
                allowed = [norm(x) for x in (node['goal_reference'].get('allowed_actions') or [])]
                forbidden = [norm(x) for x in (node['goal_reference'].get('forbidden') or [])]
                if allowed:
                    in_range_total += 1
                    hit = any(x and (x in basis or basis in x) for x in allowed)
                    covered += 1 if hit else 0
                    if not hit and len(ar_rows) < 30:
                        ar_rows.append({'rater': rater, 'node_id': nid, 'arm': arm,
                                        'action_basis': basis[:60],
                                        'allowed': node['goal_reference'].get('allowed_actions')})
                if forbidden:
                    fb_total += 1
                    fhit = any(x and (x in basis or basis in x) for x in forbidden)
                    fb_hit += 1 if fhit else 0
                    if fhit and len(fb_rows) < 20:
                        fb_rows.append({'rater': rater, 'node_id': nid, 'arm': arm,
                                        'action_basis': basis[:60],
                                        'forbidden': node['goal_reference'].get('forbidden')})
        goal['action_range']['raters'][rater] = {
            'nodes_with_goal_reference': len(ar_nodes),
            'in_range': covered, 'in_range_total': in_range_total,
            'note': '探索性覆盖（简单包含匹配），不构成资格'}
        goal['action_range'].setdefault('forbidden', {})[rater] = {
            'forbidden_hit': fb_hit, 'forbidden_total': fb_total,
            'note': 'action_basis 落入参考禁止动作=疑似目标违规（探索性，需证据复核确认）'}
    goal['action_range']['typical_out_of_range'] = ar_rows[:15]
    goal['action_range']['typical_forbidden_hits'] = fb_rows[:15]
    res['goal'] = goal

    # ---------- 归属维度：无参考标签 → 资格必为 insufficient_evidence；一致性仅探索 ----------
    attrib_nodes = [nid for nid, n in ref.items() if in_split(nid) and n.get('category') == '状态与归属']
    att_pairs = {}
    for i in range(len(raters)):
        for j in range(i + 1, len(raters)):
            ra, rb = raters[i], raters[j]
            common = matched = 0
            sev_agree = 0
            for nid in attrib_nodes:
                ca = [c for c in ((R[ra]['primary'].get(nid) or {}).get('parsed') or {}).get('claims') or []]
                cb = [c for c in ((R[rb]['primary'].get(nid) or {}).get('parsed') or {}).get('claims') or []]
                for c in ca:
                    m = match_ref_claim(cb, c.get('text'))
                    if m:
                        common += 1
                        if c.get('severity') == m[0].get('severity'):
                            sev_agree += 1
            att_pairs['%s-%s' % (ra, rb)] = {
                'matched_claims': common, 'severity_agree': sev_agree,
                'note': '参考侧无逐主张归属标签 → 资格=insufficient_evidence（一致率仅探索）'}
    res['attribution'] = {'nodes': len(attrib_nodes), 'pairs': att_pairs,
                          'verdict': 'insufficient_evidence',
                          'reason': '本包参考标签不含逐主张归属金标；如需资格须新构造归属参考。'}

    # ---------- 协议与内容分离（探索性计数） ----------
    # protocol_truth 为结构化 dict：anomaly ⇔ invisible_key_chars>0 / style_slots_missing 非空 /
    # response_nonstring_values 非空 / directions_len ∉ {None,4}；control_node_no_raw → 不可测，跳过。
    def truth_anomaly(t):
        if not isinstance(t, dict):
            return None
        if not t.get('raw_present'):
            return None  # 控制节点无原始字节，协议维不可测
        return bool(t.get('invisible_key_chars') or t.get('style_slots_missing')
                    or t.get('response_nonstring_values')
                    or (t.get('directions_len') not in (None, 4)))

    proto = {'per_rater': {}, 'note': '探索性：protocol_truth 结构化字段 vs 判词 protocol_anomaly；'
                                      '无原始字节的控制节点跳过。'}
    for rater in raters:
        agree = tot = 0
        detail = []
        for nid, node in ref.items():
            if not in_split(nid):
                continue
            rec = R[rater]['primary'].get(nid)
            pa = ((rec or {}).get('parsed') or {}).get('protocol_anomaly') or {}
            for arm in ('甲', '乙'):
                t_anom = truth_anomaly(node['answers'][arm].get('protocol_truth'))
                if t_anom is None:
                    continue
                tot += 1
                j = pa.get(arm)
                j_anom = j not in (None, 'none')
                hit = t_anom == j_anom
                agree += 1 if hit else 0
                if not hit:
                    detail.append({'node_id': nid, 'arm': arm,
                                   'ref_truth_anomaly': t_anom, 'judge': j})
        proto['per_rater'][rater] = {'n': tot, 'agree': agree,
                                     'rate': round(agree / max(1, tot), 4), 'detail': detail[:20]}
    res['protocol_separation'] = proto

    # ---------- R7 顺序与重跑（完整转移矩阵，分开配对） ----------
    def pref_pairs(rater, kind, normalize_swapped):
        prim = {nid: r for nid, r in R[rater]['primary'].items() if in_split(nid)}
        rep = {nid: r for nid, r in R[rater][kind].items() if in_split(nid)}
        pairs = []
        for nid in sorted(set(prim) & set(rep)):
            r1, r2 = prim[nid], rep[nid]
            if normalize_swapped:
                r2 = normalize_rec(r2)
            pairs.append((pref_of(r1), pref_of(r2)))
        return pairs

    seq = {}
    for rater in raters:
        if R[rater]['repeat_same']:
            seq['%s_repeat_same' % rater] = transition(pref_pairs(rater, 'repeat_same', False))
        if R[rater]['repeat_swapped']:
            seq['%s_repeat_swapped' % rater] = transition(pref_pairs(rater, 'repeat_swapped', True))
    for i in range(len(raters)):
        for j in range(i + 1, len(raters)):
            ra, rb = raters[i], raters[j]
            prim_a = {nid: r for nid, r in R[ra]['primary'].items() if in_split(nid)}
            prim_b = {nid: r for nid, r in R[rb]['primary'].items() if in_split(nid)}
            pairs = [(pref_of(prim_a[nid]), pref_of(prim_b[nid]))
                     for nid in sorted(set(prim_a) & set(prim_b))]
            seq['%s-%s_cross' % (ra, rb)] = transition(pairs)
    # 换序翻转率（decisive 对，≤15% 已声明）
    flip_rows = []
    for rater in raters:
        key = '%s_repeat_swapped' % rater
        if key in seq:
            m = seq[key]['matrix']
            dec_pairs = [(x, y) for x in ('甲', '乙') for y, n in m[x].items()
                         if y in ('甲', '乙') for _ in range(n)]
            same = sum(1 for x, y in dec_pairs if x == y)
            flip_rows.append({'rater': rater, 'decisive_n': len(dec_pairs), 'same': same,
                              'flips': len(dec_pairs) - same,
                              'flip_rate': round((len(dec_pairs) - same) / max(1, len(dec_pairs)), 4),
                              'threshold': '≤15%',
                              'verdict': 'insufficient_evidence' if len(dec_pairs) < MIN_N_RATE
                              else ('qualified' if (len(dec_pairs) - same) / max(1, len(dec_pairs)) <= TH_ORDER_FLIP
                                    else 'not_qualified')})
    res['order_and_retest'] = seq
    res['order_flip_verdicts'] = flip_rows

    # ---------- 分维度资格汇总（按评审者分母；全过才算 qualified） ----------
    def dim_verdict(per_rater_verdicts):
        vs = list(per_rater_verdicts.values())
        if any(v == 'insufficient_evidence' for v in vs):
            return 'insufficient_evidence'
        if all(v == 'qualified' for v in vs):
            return 'qualified'
        return 'not_qualified'

    # 目标一致率资格（声明阈值：精确≥50% 且相邻≥85%；n≥20/对）
    goal_pair_verdicts = {}
    for pair, v in goal['agreement_pairs'].items():
        if v['n'] < MIN_N_RATE:
            goal_pair_verdicts[pair] = 'insufficient_evidence'
        elif v['exact_percent'] >= 0.50 and v['adjacent_percent'] >= 0.85:
            goal_pair_verdicts[pair] = 'qualified'
        else:
            goal_pair_verdicts[pair] = 'not_qualified'
    goal['agreement_verdicts'] = goal_pair_verdicts
    goal['agreement_note'] = '一致率≠真值；通过只说明本手册下评审者稳定，不说明候选好坏。'

    res['qualification'] = {
        'fact_detection': dim_verdict({k: v['verdict'] for k, v in fact['per_rater'].items()}),
        'free_expression_false_kill': dim_verdict({k: v['verdict'] for k, v in kill['per_rater'].items()}),
        'insufficient_info': dim_verdict({k: (
            'insufficient_evidence' if v['n'] < MIN_N_INSUF
            else ('qualified' if v['ok_rate_incl_na_missing'] >= TH_INSUF else 'not_qualified'))
            for k, v in insuf['per_rater'].items()}),
        'goal_agreement': dim_verdict(goal_pair_verdicts),
        'goal_vs_reference_numeric': 'not_available_no_numeric_reference',
        'attribution': res['attribution']['verdict'],
        'order_sensitivity': (flip_rows[0]['verdict'] if flip_rows else 'not_measured'),
        'per_rater_detail': {
            'fact_detection': {k: v['verdict'] for k, v in fact['per_rater'].items()},
            'free_expression_false_kill': {k: v['verdict'] for k, v in kill['per_rater'].items()},
            'insufficient_info': {k: (
                'insufficient_evidence' if v['n'] < MIN_N_INSUF
                else ('qualified' if v['ok_rate_incl_na_missing'] >= TH_INSUF else 'not_qualified'))
                for k, v in insuf['per_rater'].items()},
            'goal_agreement': goal_pair_verdicts},
        'note': 'qualified 维才可 AI 辅助批量复核；not_qualified/insufficient_evidence 维度转证据复核或保留未评，'
                '不阻塞资源/协议结论，也不得偷过质量晋级。资格按评审者分母计（不是主张×3 并池）。'}
    res['judge_claims_total'] = dict(judge_claims_total)

    out = a.out or os.path.join(ROOT, 'evaluation',
                                'IRR_AGREEMENT_V2_%s.json' % ('all' if a.split == 'all' else a.split))
    payload = json.dumps(res, ensure_ascii=False, indent=1)
    print(payload[:6000])
    if a.write:
        with open(out, 'w', encoding='utf-8') as f:
            f.write(payload + '\n')
        print('WROTE', out)
    return res


if __name__ == '__main__':
    main()
