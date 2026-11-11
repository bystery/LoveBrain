# -*- coding: utf-8 -*-
r"""tools/analyze_quality_panel_v1.py — B3：质量面板判词的逐维分析器（离线，只读）。

输入：评审批输出目录（RATINGS_*.jsonl）+ 冻结输入（QUALITY_NODES / QUALITY_CONTROLS_DEV /
PRIVATE_BINDINGS / QUALITY_PLAN / QUALITY_REFERENCE_CARDS）。
输出：QUALITY_PANEL_ANALYSIS_V1.json（+ 可选 .md 摘要）。

口径（QUALITY_MANUAL_V1 §6 与 WORKER_NEXT_MAINLINE §B3）：
- 控制资格逐维分报：kill 检出率 / protect 误杀率，不合并成"9 维过 N 维"；
- 13 条 repeat_same 只测重测一致（同维精确一致 + 差≤1），不算独立第二意见；
- 判词引文逐字核对：quote 不在答案里 / evidence_quote 不在输入里 → 进待核队列（不算考生错）；
- 严格配对只在本批内（sweep 对应关系来自 PRIVATE_BINDINGS 的 arm）：orig↔goal_action、v5↔evidence_memory；
  四臂横比必须声明批次/时段混杂，不作因果排序；
- 分母如实：transport/parse/schema 失败的 attempt 记 unassessed，不静默消失。

运行：
  set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\analyze_quality_panel_v1.py --outdir <批目录>
只写 --out 指定文件（默认 outdir/QUALITY_PANEL_ANALYSIS_V1.json），不改任何输入。
"""
import argparse
import json
import os
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PK = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                  'knowledge_quality_independent_review_20261026')
DIM_KEYS = ['goal', 'factuality', 'attribution', 'time_state', 'boundary',
            'memory_use', 'interaction', 'initiative', 'emotion_relation']


def load_jsonl(path):
    out = []
    if not os.path.isfile(path):
        return out
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.strip():
                out.append(json.loads(line))
    return out


def answer_text(n):
    return (n['answer']['primary'] or '')


import re as _re
_PUNCT = _re.compile(r'[\s，。！？、；：""''\'\"（）()\[\]{}…·,.!?;:_\-—–]')


def norm_text(s):
    """空白与标点归一（QUALITY_PANEL_MAJOR_REVIEW_V1 §2：空格/标点级改写不算引文违例）。"""
    return _PUNCT.sub('', s or '')


def node_input_text(n):
    ev = n['evidence']
    parts = [json.dumps(ev.get('persona', {}), ensure_ascii=False),
             json.dumps(ev.get('initial_kb', {}), ensure_ascii=False)]
    for h in ev.get('real_history', []):
        parts.append(h.get('text', ''))
    ci = ev.get('current_input', {})
    for m in ci.get('messages', []):
        parts.append(m.get('text', ''))
    if ci.get('note'):
        parts.append(ci['note'])
    return '\n'.join(parts)


def judge_visible_text(item_id, kind, nodes, ctls, cards_by_case, card_r2, bind_map):
    """判词实际可见的全文（v1_1 修复：含 ts/intent/卡片文本——MAJOR_REVIEW §2 假阳性根因）。"""
    if kind == 'control':
        c = ctls[item_id]
        return json.dumps(c['context'], ensure_ascii=False) + '\n' + (c['answer'] or '')
    n = nodes[item_id]
    case_id = bind_map[item_id]['case_id']
    raw_card = cards_by_case.get(case_id)
    frozen = card_r2.get(case_id)
    frozen_card = None
    if frozen:
        frozen_card = {'case_id': frozen['case_id'], 'families': frozen['families'],
                       'allowed_behaviors': frozen['allowed_behaviors'],
                       'failure_conditions': frozen['failure_conditions'],
                       'review_guidance': frozen.get('review_guidance'),
                       'unresolved_ambiguities': frozen.get('unresolved_ambiguities')}
    body = {'evidence': n['evidence'], 'frozen_card': frozen_card,
            'reference_card': raw_card, 'answer': n['answer']}
    return json.dumps(body, ensure_ascii=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--outdir', required=True, help='评审批输出目录（含 RATINGS_*.jsonl）')
    ap.add_argument('--out', default=None)
    args = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

    outdir = os.path.abspath(args.outdir)
    nodes = {n['node_id']: n for n in load_jsonl(os.path.join(PK, 'QUALITY_NODES.jsonl'))}
    controls = json.load(open(os.path.join(PK, 'QUALITY_CONTROLS_DEV_V1.json'), encoding='utf-8'))['controls']
    ctl_by_id = {c['control_id']: c for c in controls}
    bindings = json.load(open(os.path.join(PK, 'PRIVATE_BINDINGS.json'), encoding='utf-8'))
    bind_map = {b['node_id']: b for b in bindings}
    plan = json.load(open(os.path.join(PK, 'QUALITY_PLAN.json'), encoding='utf-8'))

    recs = load_jsonl(os.path.join(outdir, 'RATINGS_control.jsonl')) + \
        load_jsonl(os.path.join(outdir, 'RATINGS_primary.jsonl')) + \
        load_jsonl(os.path.join(outdir, 'RATINGS_repeat_same.jsonl'))
    repaired_path = os.path.join(outdir, 'RATINGS_REPAIRED.jsonl')
    repaired_used = False
    if os.path.isfile(repaired_path):
        # 离线修复通道（repair_quality_panel_ratings.py）：以修正版 schema 口径为准
        recs = load_jsonl(repaired_path)
        for r in recs:
            r['transport_ok'] = r.get('parse_method') != 'transport_failed'
            r['parse_ok'] = r.get('parse_method') not in ('transport_failed', 'unparseable')
            r['schema_ok'] = bool(r.get('schema_ok_corrected'))
        repaired_used = True
    summary_path = os.path.join(outdir, 'JUDGE_BATCH_SUMMARY.json')
    batch_summary = json.load(open(summary_path, encoding='utf-8')) if os.path.isfile(summary_path) else {}

    # ---- 分母与完整性 ----
    by_item = defaultdict(list)
    accounting = {'records': len(recs), 'transport_fail': 0, 'parse_fail': 0,
                  'schema_fail': 0, 'unassessed': 0, 'assessed': 0}
    for r in recs:
        if not r.get('transport_ok'):
            accounting['transport_fail'] += 1
            accounting['unassessed'] += 1
        elif not r.get('parse_ok'):
            accounting['parse_fail'] += 1
            accounting['unassessed'] += 1
        elif not r.get('schema_ok'):
            accounting['schema_fail'] += 1
            accounting['unassessed'] += 1
        else:
            accounting['assessed'] += 1
            by_item[(r['kind'], r['item_id'])].append(r)

    # ---- 引文逐字核对（v1_1：可见全文重建 + 空白标点归一；raw/normalized 分列）----
    quote_violations = []
    cards_all = json.load(open(os.path.join(PK, 'QUALITY_REFERENCE_CARDS_V1.json'), encoding='utf-8'))['cards']
    cards_by_case = {c['case_id']: c for c in cards_all}
    card_r2 = {x['case_id']: x for x in json.load(open(
        os.path.join(ROOT, 'benchmark', 'c4_screen_20261026',
                     'GRADING_CARDS_C4_E006_E007_E008_E012_R2.json'), encoding='utf-8'))}
    for r in recs:
        if not (r.get('transport_ok') and r.get('parse_ok') and r.get('schema_ok')):
            continue
        p = r.get('parsed') or {}
        item_id = r['item_id']
        if r['kind'] == 'control':
            c = ctl_by_id[item_id]
            ans = c['answer'] or ''
            inp = json.dumps(c['context'], ensure_ascii=False)
        else:
            n = nodes[item_id]
            ans = json.dumps(n['answer'], ensure_ascii=False)
            inp = judge_visible_text(item_id, 'primary', nodes, ctl_by_id,
                                     cards_by_case, card_r2, bind_map)
        hay_raw, hay_norm = inp + '\n' + ans, norm_text(inp + '\n' + ans)
        sections = [('primary', p.get('primary') or {})] + \
                   [('backup:' + (b.get('slot') or '?'), b) for b in (p.get('backups') or [])]
        for where, sec in sections:
            for i, c in enumerate(sec.get('claims') or []):
                q, eq = c.get('quote') or '', c.get('evidence_quote') or ''
                for field, frag in (('quote', q), ('evidence_quote', eq)):
                    if not frag:
                        continue
                    raw_hit = frag in hay_raw
                    norm_hit = norm_text(frag) in hay_norm
                    if not norm_hit:
                        quote_violations.append({'item_id': item_id, 'where': where, 'idx': i,
                                                 'field': field, 'sample': frag[:60],
                                                 'raw_hit': raw_hit,
                                                 'kind': 'normalized_miss'})
                    elif not raw_hit:
                        quote_violations.append({'item_id': item_id, 'where': where, 'idx': i,
                                                 'field': field, 'sample': frag[:60],
                                                 'raw_hit': False,
                                                 'kind': 'whitespace_punct_only'})

    # ---- 控制资格（逐维）----
    def dim_scores(sec):
        sc = (sec or {}).get('scores') or {}
        return {k: sc.get(k) for k in DIM_KEYS}

    ctl_rows = []
    for r in recs:
        if r['kind'] != 'control' or not (r.get('transport_ok') and r.get('parse_ok') and r.get('schema_ok')):
            continue
        exp = ctl_by_id[r['item_id']]['expectation']
        parsed = r['parsed']
        prim = parsed.get('primary') or {}
        sc = dim_scores(prim)
        claims = prim.get('claims') or []
        major_dims = {c.get('severity') and (c.get('type') or '') for c in claims} and \
            {k for k in DIM_KEYS if sc.get(k) == 0}
        hit = None
        if not exp['acceptable']:
            # 应判错：期望维被打 ≤1 分，或出现 major 级 claim（我们无法定位维，用分数低位作代理）
            hit = any(sc.get(d) is not None and sc[d] <= 1 for d in exp['applicable_dims'])
        else:
            # 保护样本：期望维不应被打 0 分，且不应出现"把保护表达判成 major 无据"（用分数代理）
            hit = not any(sc.get(d) == 0 for d in exp['applicable_dims'])
        ctl_rows.append({'control_id': r['item_id'], 'group': ctl_by_id[r['item_id']]['group'],
                         'expected_acceptable': exp['acceptable'],
                         'expected_dims': exp['applicable_dims'],
                         'expected_severity': exp['expected_severity'],
                         'scores': sc, 'claims_n': len(claims),
                         'verdict_hit': hit})
    kill_rows = [x for x in ctl_rows if not x['expected_acceptable']]
    prot_rows = [x for x in ctl_rows if x['expected_acceptable']]
    kill_hit = sum(1 for x in kill_rows if x['verdict_hit'])
    prot_kill = sum(1 for x in prot_rows if not x['verdict_hit'])
    controls_qual = {
        'n_assessed': len(ctl_rows), 'n_expected': 32,
        'kill_identified': kill_hit, 'kill_total': len(kill_rows),
        'protect_false_killed': prot_kill, 'protect_total': len(prot_rows),
        'by_group': {}, 'note': '总分代理口径：verdict 由期望维分数推导（≤1 判错=检出；0 分=误杀）。'
                                '逐维细分与 threshold 对照在 QUALITY_PANEL_QUALIFICATION 里按需展开。'}
    g = defaultdict(lambda: {'n': 0, 'hit': 0})
    for x in ctl_rows:
        g[x['group']]['n'] += 1
        g[x['group']]['hit'] += 1 if x['verdict_hit'] else 0
    controls_qual['by_group'] = {k: dict(v) for k, v in sorted(g.items())}

    # ---- 重测一致（repeat_same vs primary）----
    rep_rows = []
    for r in recs:
        if r['kind'] != 'repeat_same' or not (r.get('transport_ok') and r.get('parse_ok') and r.get('schema_ok')):
            continue
        base = by_item.get(('primary', r['item_id']) or ('primary', r['item_id']), [])
        base = [x for x in recs if x['kind'] == 'primary' and x['item_id'] == r['item_id']
                and x.get('schema_ok') and x.get('parse_ok') and x.get('transport_ok')]
        if not base:
            rep_rows.append({'item_id': r['item_id'], 'comparable': False})
            continue
        b = base[0]['parsed']
        p2 = r['parsed']
        dims_same, dims_within1, dims_cmp = 0, 0, 0
        for d in DIM_KEYS:
            v1 = ((b.get('primary') or {}).get('scores') or {}).get(d)
            v2 = ((p2.get('primary') or {}).get('scores') or {}).get(d)
            if v1 is None and v2 is None:
                continue
            dims_cmp += 1
            if v1 == v2:
                dims_same += 1
            if v1 is not None and v2 is not None and abs(v1 - v2) <= 1:
                dims_within1 += 1
        rep_rows.append({'item_id': r['item_id'], 'comparable': True,
                         'dims_compared': dims_cmp, 'dims_exact': dims_same,
                         'dims_within1': dims_within1})

    # ---- 按臂 × 逐维 ----
    def arm_of(item_id, kind):
        if kind == 'control':
            return None
        return bind_map.get(item_id, {}).get('arm')

    per_arm = defaultdict(lambda: {'n': 0,
                                   'dims': {d: {'sum': 0, 'n': 0, 'na': 0} for d in DIM_KEYS},
                                   'dims_primary': {d: {'sum': 0, 'n': 0, 'na': 0} for d in DIM_KEYS},
                                   'dims_backups': {d: {'sum': 0, 'n': 0, 'na': 0} for d in DIM_KEYS},
                                   'claims': {'total': 0, 'conflict': 0, 'major': 0,
                                              'unsupported': 0}})
    for r in recs:
        if r['kind'] != 'primary' or not (r.get('transport_ok') and r.get('parse_ok') and r.get('schema_ok')):
            continue
        arm = arm_of(r['item_id'], 'primary')
        if not arm:
            continue
        p = r['parsed']
        slot_sections = [('primary', p.get('primary') or {})] + \
                        [(b.get('slot'), b) for b in (p.get('backups') or [])]
        per_arm[arm]['n'] += 1
        for _name, sec in slot_sections:
            bucket = ('dims_primary' if _name == 'primary' else 'dims_backups')
            sc = dim_scores(sec)
            for d in DIM_KEYS:
                v = sc[d]
                for acc in (per_arm[arm]['dims'][d], per_arm[arm][bucket][d]):
                    if v is None:
                        acc['na'] += 1
                    else:
                        acc['sum'] += v
                        acc['n'] += 1
            for c in sec.get('claims') or []:
                per_arm[arm]['claims']['total'] += 1
                if c.get('support') == 'conflict':
                    per_arm[arm]['claims']['conflict'] += 1
                if c.get('severity') == 'major':
                    per_arm[arm]['claims']['major'] += 1
                if c.get('support') == 'unknown' and c.get('stance') == '确定':
                    per_arm[arm]['claims']['unsupported'] += 1

    def _means(acc_map):
        return {d: (round(acc_map[d]['sum'] / acc_map[d]['n'], 3)
                    if acc_map[d]['n'] else None) for d in DIM_KEYS}

    per_arm_out = {}
    for arm, v in per_arm.items():
        per_arm_out[arm] = {
            'n_answers': v['n'],
            'dims_mean': _means(v['dims']),
            'dims_mean_primary': _means(v['dims_primary']),
            'dims_mean_backups': _means(v['dims_backups']),
            'dims_scored_n': {d: v['dims'][d]['n'] for d in DIM_KEYS},
            'dims_na_n': {d: v['dims'][d]['na'] for d in DIM_KEYS},
            'claims': v['claims'],
        }

    # ---- 严格同批配对（orig↔goal_action；v5↔evidence_memory）----
    PAIRS = [('reference_product_v1', 'goal_action_v1'),
             ('v5_purpose_aligned', 'evidence_memory_v1')]
    case_arm_prim = {}
    for r in recs:
        if r['kind'] != 'primary' or not (r.get('transport_ok') and r.get('parse_ok') and r.get('schema_ok')):
            continue
        b = bind_map.get(r['item_id'])
        if b:
            case_arm_prim[(b['case_id'], b['arm'])] = r['parsed']
    pair_rows = []
    story_pair_rows = []
    for a1, a2 in PAIRS:
        wins = losses = ties = incomparable = 0
        story_acc = defaultdict(lambda: {'w': 0, 't': 0, 'l': 0, 'x': 0})
        for case_id in sorted({k[0] for k in case_arm_prim}):
            p1 = case_arm_prim.get((case_id, a1))
            p2 = case_arm_prim.get((case_id, a2))
            story = case_id.split('_')[0]
            if not p1 or not p2:
                incomparable += 1
                story_acc[story]['x'] += 1
                continue
            s1 = dim_scores(p1.get('primary'))
            s2 = dim_scores(p2.get('primary'))
            diff = [s1[d] - s2[d] for d in DIM_KEYS
                    if s1[d] is not None and s2[d] is not None]
            if not diff:
                ties += 1
                story_acc[story]['t'] += 1
                continue
            mean_diff = sum(diff) / len(diff)
            if mean_diff > 0.5:
                wins += 1
                story_acc[story]['w'] += 1
            elif mean_diff < -0.5:
                losses += 1
                story_acc[story]['l'] += 1
            else:
                ties += 1
                story_acc[story]['t'] += 1
        pair_rows.append({'pair': '%s vs %s' % (a1, a2), 'same_batch': True,
                          'wins_a1': wins, 'ties': ties, 'wins_a2': losses,
                          'incomparable': incomparable,
                          'note': '同批两臂配对（逐位交错、同配置身份）；|mean_diff|≤0.5 记平'})
        story_pair_rows.append({'pair': '%s vs %s' % (a1, a2),
                                'by_story': {k: dict(v) for k, v in sorted(story_acc.items())},
                                'note': '故事级聚合（w=a1胜/t=平/l=a2胜/x=不可比）；故事是独立单位，位点不是'})
    four_arm_note = '四臂横比=两批两时段拼接（C4-A: ref/goal；C4-B: v5/mem），带批次/时段混杂，只作描述不作因果排序。'

    result = {
        'kind': 'quality_panel_analysis_v1',
        'outdir': os.path.relpath(outdir, ROOT),
        'ratings_source': 'RATINGS_REPAIRED.jsonl' if repaired_used else 'RATINGS_*.jsonl',
        'plan_sha256_prefix': plan['input_hashes_sha256']['QUALITY_NODES.jsonl'][:16],
        'batch_summary': {k: batch_summary.get(k) for k in
                          ('mode', 'attempts', 'planned', 'transport_fail', 'parse_fail',
                           'schema_fail', 'stopped')},
        'accounting': accounting,
        'controls_qualification': controls_qual,
        'repeat_consistency': {'n': len(rep_rows),
                               'comparable': sum(1 for x in rep_rows if x.get('comparable')),
                               'mean_dims_exact': (round(sum(x['dims_exact'] for x in rep_rows
                                                             if x.get('comparable')) /
                                                     max(1, sum(1 for x in rep_rows if x.get('comparable'))), 2)),
                               'rows': rep_rows},
        'per_arm': per_arm_out,
        'same_batch_pairs': pair_rows,
        'same_batch_pairs_by_story': story_pair_rows,
        'four_arm_note': four_arm_note,
        'quote_violations': quote_violations,
        'quote_violation_note': '判词引文与输入逐字不符 → 待核队列；不算考生错，需人工复核该判词',
        'unknowns': ['用户愿发 W：unknown（模型禁填）',
                     '金额：unknown（无可核对单价）',
                     '资格：控制为 worker-AI 开发控制（非人类标定），九维=AI 辅助读数'],
    }
    out_path = args.out or os.path.join(outdir, 'QUALITY_PANEL_ANALYSIS_V1.json')
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print('ANALYZED %s -> %s' % (os.path.relpath(outdir, ROOT), os.path.relpath(out_path, ROOT)))
    print('accounting=%s kill=%d/%d protect_falsekill=%d/%d quote_violations=%d'
          % (accounting, kill_hit, len(kill_rows), prot_kill, len(prot_rows), len(quote_violations)))


if __name__ == '__main__':
    main()
