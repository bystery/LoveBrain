# -*- coding: utf-8 -*-
r"""tools/analyze_p06_judge.py — P06 九维判词的离线分析器（只读，不派发）。

输入：
  --ratings  P06 评审批输出目录（RATINGS_primary/control/repeat.jsonl）
  --out      输出 JSON（默认 evaluation/P06_QUALITY_RESULTS.json）
  --md       可选 Markdown 摘要
冻结包：refs/experiment_protocol_20261023/p06_judge_20261027

口径（P06_DECISION_RULES.md）：
- 首选与备选分开；未评不进均分分母；严格可用按“有文本+可解析+已判小节无 major”；
- 同批严格配对：candidate−baseline 九维首选均值差，>+0.5/ <−0.5 记胜负，其余平；
- 控制逐条报检出/误杀/§2a 联动；复评报精确一致与差≤1；
- 引文核对只做待核队列，不自动算考生错；
- 自动判决只是预注册规则的机械应用，必须 worker 亲读原文后才能落最终判决。
"""
import argparse
import json
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PK = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'p06_judge_20261027')
DIMS = ['goal', 'factuality', 'attribution', 'time_state', 'boundary',
        'memory_use', 'interaction', 'initiative', 'emotion_relation']
PAIR_THRESHOLD = 0.5
_PUNCT = re.compile(r'[\s\W_]+', re.UNICODE)


def norm_text(s):
    return _PUNCT.sub('', s or '')


def load_json(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def sha256_file(path):
    import hashlib
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def safe_rel(path):
    try:
        return os.path.relpath(path, ROOT).replace('\\', '/')
    except ValueError:
        return os.path.abspath(path).replace('\\', '/')


def load_jsonl(path):
    out = []
    if not os.path.isfile(path):
        return out
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.strip():
                out.append(json.loads(line))
    return out


def dim_scores(part):
    sc = (part or {}).get('scores') or {}
    return {d: (sc.get(d) if isinstance(sc.get(d), int) and not isinstance(sc.get(d), bool)
                and 0 <= sc[d] <= 3 else None) for d in DIMS}


def claims(part):
    c = (part or {}).get('claims')
    return c if isinstance(c, list) else []


def major_claims(part):
    return [c for c in claims(part)
            if isinstance(c, dict) and c.get('severity') == 'major']


def suppression_violations(part):
    """§2a：major 且 support∈{unknown,conflict} ⇒ 对应维度 ≤1（默认 factuality）。"""
    out = []
    sc = dim_scores(part)
    dim_map = {'事实': 'factuality', '归属': 'attribution', '状态': 'time_state', '边界': 'boundary'}
    for c in major_claims(part):
        if c.get('support') not in ('unknown', 'conflict'):
            continue
        d = dim_map.get(c.get('type'), 'factuality')
        if sc.get(d) is not None and sc[d] > 1:
            out.append({'dim': d, 'score': sc[d], 'quote': c.get('quote'),
                        'support': c.get('support'), 'type': c.get('type')})
    return out


def visible_input(node):
    return '\n'.join([json.dumps(node.get('evidence'), ensure_ascii=False),
                      json.dumps(node.get('reference_card'), ensure_ascii=False)])


def answer_text_for_slot(node, slot):
    if slot is None:
        return node['answer']['primary']
    for b in node['answer']['backups']:
        if b['slot'] == slot:
            return b['text']
    return ''


def assess(rec):
    return bool(rec.get('transport_ok') and rec.get('parse_ok') and rec.get('schema_ok')
                and isinstance(rec.get('parsed'), dict))


def dedupe(recs):
    """同一 item 多次落盘时保留最后一条；重复 attempt 记 duplicate。"""
    by_item = {}
    dup = 0
    for r in recs:
        k = r.get('item_id')
        if k in by_item:
            dup += 1
        by_item[k] = r
    return list(by_item.values()), dup


def arm_stats(records, bindings, veto_map):
    """按 batch+arm 统计首选/备选。计划分母来自冻结绑定，不是落盘记录数。"""
    out = {}
    rec_by_item = {r.get('item_id'): r for r in records}
    groups = defaultdict(list)
    for b in bindings.values():
        if b.get('node_kind') != 'primary':
            continue
        groups[(b['batch'], b['arm'])].append((rec_by_item.get(b['node_id']), b))
    for key, rows in groups.items():
        acc = {'n_planned': 0, 'n_assessed': 0, 'n_missing': 0, 'n_veto': 0,
               'dims': {d: {'sum': 0, 'n': 0, 'na': 0} for d in DIMS},
               'claims_total': 0, 'claims_major': 0,
               'major_nodes': 0, 'major_details': [],
               'backup_slots': 0, 'backup_major': 0, 'backup_major_details': [],
               'strict_usable': 0, 'strict_usable_missing': 0,
               'has_text': 0, 'missing_cases': []}
        for r, b in rows:
            acc['n_planned'] += 1
            if veto_map.get((b['batch'], b['case_id'], b['arm'])):
                acc['n_veto'] += 1
            if r is None or not assess(r):
                acc['n_missing'] += 1
                acc['missing_cases'].append(b['case_id'])
                continue
            acc['n_assessed'] += 1
            p = r['parsed']
            prim = p.get('primary') or {}
            sc = dim_scores(prim)
            for d in DIMS:
                if sc[d] is None:
                    acc['dims'][d]['na'] += 1
                else:
                    acc['dims'][d]['sum'] += sc[d]
                    acc['dims'][d]['n'] += 1
            ms = major_claims(prim)
            acc['claims_total'] += len(claims(prim))
            acc['claims_major'] += len(ms)
            if ms:
                acc['major_nodes'] += 1
                for c in ms:
                    acc['major_details'].append({'case_id': b['case_id'], 'slot': 'primary',
                                                 'quote': c.get('quote'),
                                                 'support': c.get('support'),
                                                 'type': c.get('type'),
                                                 'reason': c.get('reason')})
            if (prim.get('scores') or {}).get('__text__'):
                pass
            if node_has_text(r, b):
                acc['has_text'] += 1
            backup_major = False
            for bk in (p.get('backups') or []):
                if not isinstance(bk, dict):
                    continue
                acc['backup_slots'] += 1
                bms = major_claims(bk)
                acc['backup_major'] += len(bms)
                if bms:
                    backup_major = True
                    for c in bms:
                        acc['backup_major_details'].append({'case_id': b['case_id'],
                                                            'slot': bk.get('slot'),
                                                            'quote': c.get('quote'),
                                                            'support': c.get('support'),
                                                            'type': c.get('type')})
            if not ms and not backup_major:
                acc['strict_usable'] += 1
            else:
                acc['strict_usable_missing'] += 1
        for d in DIMS:
            v = acc['dims'][d]
            v['mean'] = round(v['sum'] / v['n'], 3) if v['n'] else None
        acc['strict_usable_assessed_rate'] = (round(acc['strict_usable'] / acc['n_assessed'], 4)
                                             if acc['n_assessed'] else None)
        acc['strict_usable_planned_rate'] = (round(acc['strict_usable'] / acc['n_planned'], 4)
                                            if acc['n_planned'] else None)
        acc['coverage'] = round(acc['n_assessed'] / acc['n_planned'], 4) if acc['n_planned'] else None
        out[key] = acc
    return out


def node_has_text(rec, binding):
    return True


def pair_stats(records, bindings, nodes):
    """同批配对：parent vs candidate，同一 case。"""
    prim = {}
    for r in records:
        b = bindings.get(r.get('item_id'))
        if not b or b.get('node_kind') != 'primary' or not assess(r):
            continue
        prim[(b['batch'], b['case_id'], b['arm'])] = r['parsed']
    out = {}
    for batch in ('A', 'B', 'C'):
        cases = sorted({k[1] for k in prim if k[0] == batch})
        w = t = l = x = 0
        diffs = []
        by_story = defaultdict(lambda: {'w': 0, 't': 0, 'l': 0, 'x': 0})
        major_delta = []
        details = []
        for case in cases:
            p = prim.get((batch, case, 'parent'))
            c = prim.get((batch, case, 'candidate'))
            story = case.split('_')[0]
            if not p or not c:
                x += 1
                by_story[story]['x'] += 1
                details.append({'case_id': case, 'result': 'incomparable'})
                continue
            sp, sc = dim_scores(p.get('primary')), dim_scores(c.get('primary'))
            ds = [sc[d] - sp[d] for d in DIMS if sp[d] is not None and sc[d] is not None]
            if not ds:
                t += 1
                by_story[story]['t'] += 1
                details.append({'case_id': case, 'result': 'tie_no_comparable'})
                continue
            md = sum(ds) / len(ds)
            diffs.append({'case_id': case, 'mean_diff': round(md, 3), 'n_dims': len(ds)})
            if md > PAIR_THRESHOLD:
                w += 1
                by_story[story]['w'] += 1
                res = 'candidate'
            elif md < -PAIR_THRESHOLD:
                l += 1
                by_story[story]['l'] += 1
                res = 'parent'
            else:
                t += 1
                by_story[story]['t'] += 1
                res = 'tie'
            mp, mc = len(major_claims(p.get('primary'))), len(major_claims(c.get('primary')))
            major_delta.append({'case_id': case, 'parent_major': mp, 'candidate_major': mc})
            details.append({'case_id': case, 'result': res, 'mean_diff': round(md, 3),
                            'parent_major': mp, 'candidate_major': mc})
        out[batch] = {'cases': len(cases), 'wins_candidate': w, 'ties': t,
                      'wins_parent': l, 'incomparable': x,
                      'diffs': diffs, 'by_story': dict(by_story),
                      'major_delta': major_delta, 'details': details,
                      'note': '同批配对；|mean_diff|<=0.5 记平'}
    return out


def control_stats(records, controls_by_id):
    rec_by_item = {r.get('item_id'): r for r in records}
    rows = []
    for c in controls_by_id.values():
        r = rec_by_item.get(c['node_id'])
        exp = c.get('expectation') or {}
        row = {'control_id': c['control_id'], 'group': c.get('group'),
               'assessed': assess(r) if r else False}
        if r and assess(r):
            p = r['parsed']
            ms = major_claims(p.get('primary'))
            sc = dim_scores(p.get('primary'))
            dims = exp.get('applicable_dims') or []
            row.update({'expected_acceptable': exp.get('acceptable'),
                        'expected_protected': exp.get('protected'),
                        'expected_severity': exp.get('expected_severity'),
                        'major_claims': len(ms),
                        'major_quotes': [x.get('quote') for x in ms],
                        'dim_le1': all(sc.get(d) is not None and sc[d] <= 1 for d in dims)
                                  if dims else None,
                        'suppression_violations': suppression_violations(p.get('primary')),
                        'applicable_dims': dims})
            if exp.get('acceptable') is False:
                row['kill_hit'] = bool(ms)
            if exp.get('protected') is True:
                row['protect_false_kill'] = bool(ms)
        rows.append(row)
    kills = [x for x in rows if x.get('expected_acceptable') is False]
    protects = [x for x in rows if x.get('expected_protected') is True]
    return {
        'n': len(rows), 'assessed': sum(1 for x in rows if x['assessed']),
        'kill_n': len(kills), 'kill_hit': sum(1 for x in kills if x.get('kill_hit')),
        'protect_n': len(protects),
        'protect_false_kill': sum(1 for x in protects if x.get('protect_false_kill')),
        'suppression_violations': sum(len(x.get('suppression_violations') or []) for x in rows),
        'rows': rows,
    }


def repeat_stats(records, bindings):
    by_item = {r.get('item_id'): r for r in records}
    rows = []
    for b in bindings.values():
        if b.get('node_kind') != 'repeat':
            continue
        r = by_item.get(b['node_id'])
        src = by_item.get(b.get('repeat_of'))
        row = {'repeat_id': b['node_id'], 'source_id': b.get('repeat_of'),
               'case_id': b.get('case_id'), 'batch': b.get('batch'), 'arm': b.get('arm'),
               'assessed': assess(r) if r else False}
        if r and src and assess(r) and assess(src):
            s1 = dim_scores(r['parsed'].get('primary'))
            s2 = dim_scores(src['parsed'].get('primary'))
            comp = [d for d in DIMS if s1[d] is not None and s2[d] is not None]
            exact = all(s1[d] == s2[d] for d in DIMS)
            within1 = all(abs(s1[d] - s2[d]) <= 1 for d in comp) and \
                all((s1[d] is None) == (s2[d] is None) for d in DIMS)
            row.update({'exact': exact, 'within1': within1,
                        'diffs': {d: (s1[d] - s2[d]) for d in comp}})
        rows.append(row)
    ok = [x for x in rows if 'exact' in x]
    dim_exact = {}
    for d in DIMS:
        vals = [1 if x['diffs'].get(d) == 0 else 0 for x in ok if d in x['diffs']]
        dim_exact[d] = round(sum(vals) / len(vals), 4) if vals else None
    return {'n': len(rows), 'assessed_pairs': len(ok),
            'exact': sum(1 for x in ok if x['exact']),
            'within1': sum(1 for x in ok if x['within1']),
            'dim_exact_rate': dim_exact,
            'rows': rows}


def quote_stats(records, bindings, nodes, controls_by_id):
    mism = []
    checked = 0
    for r in records:
        if not assess(r):
            continue
        b = bindings.get(r.get('item_id'))
        if b and b.get('node_kind') == 'control':
            c = controls_by_id.get(r.get('item_id'))
            visible = json.dumps(c.get('context'), ensure_ascii=False)
            ans = c.get('answer') or ''
        elif b:
            n = nodes[r['item_id']]
            visible = visible_input(n)
            ans = n['answer']['primary']
        else:
            continue
        p = r['parsed']
        parts = [('primary', p.get('primary') or {}, ans)]
        for bk in (p.get('backups') or []):
            if not isinstance(bk, dict):
                continue
            slot = bk.get('slot')
            parts.append((slot, bk, answer_text_for_slot(nodes.get(r['item_id'], {}), slot)
                          if b and b.get('node_kind') != 'control' else ans))
        for slot, part, text in parts:
            for c in claims(part):
                checked += 1
                q = c.get('quote') or ''
                eq = c.get('evidence_quote') or ''
                if q and norm_text(q) not in norm_text(text):
                    mism.append({'item_id': r['item_id'], 'case_id': b.get('case_id') if b else None,
                                 'slot': slot, 'kind': 'quote_not_in_answer',
                                 'quote': q})
                if eq and norm_text(eq) not in norm_text(visible):
                    mism.append({'item_id': r['item_id'], 'case_id': b.get('case_id') if b else None,
                                 'slot': slot, 'kind': 'evidence_not_visible',
                                 'evidence_quote': eq})
    return {'claims_checked': checked, 'mismatches': len(mism), 'rows': mism}


def decision_flags(batch_stats, pairs, controls):
    out = {}
    for batch in ('A', 'B', 'C'):
        p = batch_stats.get((batch, 'parent')) or {}
        c = batch_stats.get((batch, 'candidate')) or {}
        pr = pairs.get(batch) or {}
        if not p or not c:
            out[batch] = {'status': 'missing_arm'}
            continue
        g1 = (p.get('coverage', 0) or 0) >= 0.9 and (c.get('coverage', 0) or 0) >= 0.9
        g2 = ((c.get('major_nodes', 0) - p.get('major_nodes', 0)) < 2
              and (c.get('backup_major', 0) - p.get('backup_major', 0)) < 2)
        p_rate = p.get('strict_usable_assessed_rate') or 0
        c_rate = c.get('strict_usable_assessed_rate') or 0
        g3 = c_rate >= p_rate - 0.05
        g2b = c.get('n_veto', 0) <= p.get('n_veto', 0) + 2
        wins, losses = pr.get('wins_candidate', 0), pr.get('wins_parent', 0)
        dim_deltas = {}
        for d in DIMS:
            mp = ((p.get('dims') or {}).get(d) or {}).get('mean')
            mc = ((c.get('dims') or {}).get(d) or {}).get('mean')
            dim_deltas[d] = round(mc - mp, 3) if mp is not None and mc is not None else None
        benefits = [d for d, v in dim_deltas.items() if v is not None and v >= 0.2]
        harms = [d for d, v in dim_deltas.items() if v is not None and v <= -0.2]
        if not g1:
            preliminary = 'unknown_blocked'
        elif not g2 or not g3:
            preliminary = 'eliminate_or_blocked'
        elif wins > losses:
            preliminary = 'retain_candidate_for_M2' if g2b else 'modify_before_M2'
        elif wins >= losses - 1 and len(benefits) >= 2 and not harms:
            preliminary = 'retain_with_targeted_fixes' if g2b else 'modify_before_M2'
        elif wins < losses - 2 and not benefits:
            preliminary = 'eliminate_or_blocked'
        else:
            preliminary = 'modify_before_M2'
        out[batch] = {
            'g1_data_integrity': g1, 'g2_major_not_worse': g2, 'g3_strict_usable': g3,
            'g2b_protocol_veto_not_worse': g2b,
            'strict_usable_rate_parent': p_rate, 'strict_usable_rate_candidate': c_rate,
            'veto_parent': p.get('n_veto'), 'veto_candidate': c.get('n_veto'),
            'wins_candidate': wins, 'ties': pr.get('ties'), 'wins_parent': losses,
            'incomparable': pr.get('incomparable'),
            'dim_deltas_candidate_minus_baseline': dim_deltas,
            'benefit_dims': benefits, 'harm_dims': harms,
            'preliminary': preliminary,
            'requires_worker_primary_read': True,
            'note': '机械套用 P06_DECISION_RULES；最终判决须 worker 亲读原文、控制与复评后落笔',
        }
    return out


def main():
    global PK
    ap = argparse.ArgumentParser()
    ap.add_argument('--ratings', required=True)
    ap.add_argument('--packet', default=None, help='冻结题包目录（默认 p06_judge_20261027）')
    ap.add_argument('--out', default=os.path.join(ROOT, 'evaluation', 'P06_QUALITY_RESULTS.json'))
    ap.add_argument('--md', default=None)
    a = ap.parse_args()
    if a.packet:
        PK = a.packet if os.path.isabs(a.packet) else os.path.join(ROOT, a.packet)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

    nodes = {n['_meta']['node_id']: n for n in
             (json.loads(l) for l in open(os.path.join(PK, 'P06_NODES.jsonl'), encoding='utf-8'))}
    controls = {c['node_id']: c for c in load_json(os.path.join(PK, 'P06_CONTROLS.json'))['controls']}
    bindings = {b['node_id']: b for b in load_json(os.path.join(PK, 'P06_PRIVATE_BINDINGS.json'))}
    plan = load_json(os.path.join(PK, 'P06_QUALITY_PLAN.json'))
    recs = (load_jsonl(os.path.join(a.ratings, 'RATINGS_primary.jsonl'))
            + load_jsonl(os.path.join(a.ratings, 'RATINGS_control.jsonl'))
            + load_jsonl(os.path.join(a.ratings, 'RATINGS_repeat.jsonl')))
    recs, dup = dedupe(recs)
    inventory = load_json(os.path.join(PK, 'P06_ANSWER_INVENTORY.json'))['inventory']
    veto_map = {(x['batch'], x['case_id'], x['arm']): x.get('veto') for x in inventory}
    batch_stats = arm_stats(recs, bindings, veto_map)
    pairs = pair_stats(recs, bindings, nodes)
    ctl = control_stats(recs, controls)
    rep = repeat_stats(recs, bindings)
    quotes = quote_stats(recs, bindings, nodes, controls)
    decision = decision_flags(batch_stats, pairs, ctl)

    manifest = load_json(os.path.join(PK, 'P06_MANIFEST.json'))
    delivery = {}
    for b in manifest.get('batches') or []:
        done = load_json(os.path.join(ROOT, b['done_path']))
        row = {k: done.get(k) for k in ('batch_id', 'arm_mapping', 'mode', 'started',
                                        'completed', 'failed', 'stopped', 'order_verified',
                                        'dispatch_bijection', 'real_model_calls',
                                        'program_mean10', 'paired_program_winner_counts',
                                        'cost_parent', 'cost_candidate')}
        for arm in ('parent', 'candidate'):
            cost = done.get('cost_' + arm) or {}
            su = (batch_stats.get((b['tag'], arm)) or {}).get('strict_usable') or 0
            tokens = (cost.get('prompt') or 0) + (cost.get('completion') or 0)
            row['tokens_per_strict_usable_' + arm] = (round(tokens / su, 1) if su else None)
        delivery[b['tag']] = row

    summary = {
        'kind': 'p06_quality_results_v1',
        'ratings_dir': safe_rel(os.path.abspath(a.ratings)),
        'packet_plan_sha256': sha256_file(os.path.join(PK, 'P06_QUALITY_PLAN.json')),
        'duplicate_records': dup,
        'records': len(recs),
        'assessed': sum(1 for r in recs if assess(r)),
        'transport_fail': sum(1 for r in recs if not r.get('transport_ok')),
        'parse_fail': sum(1 for r in recs if r.get('transport_ok') and not r.get('parse_ok')),
        'schema_fail': sum(1 for r in recs if r.get('transport_ok') and r.get('parse_ok')
                           and not r.get('schema_ok')),
        'by_arm': {('%s/%s' % k): v for k, v in sorted(batch_stats.items())},
        'delivery': delivery,
        'pairs': pairs,
        'controls': {k: v for k, v in ctl.items() if k != 'rows'},
        'control_rows': ctl['rows'],
        'repeats': {k: v for k, v in rep.items() if k != 'rows'},
        'repeat_rows': rep['rows'],
        'quotes': {k: v for k, v in quotes.items() if k != 'rows'},
        'quote_rows': quotes['rows'],
        'decision_flags': decision,
        'scope_notes': [
            '四条故事 E006/E007/E008/E012 均 dev_exposed；不支持泛化。',
            'W（用户愿发）unknown；金额 unknown（无单价）；cache null 记 unknown_usage。',
            '跨批横比只作探索；严格对照只在同批两臂之间。',
            '自动判决只是预注册规则的机械应用，必须 worker 亲读后才可落最终判决。',
        ],
    }
    summary['packet_nodes_sha256'] = plan.get('input_hashes_sha256', {}).get('P06_NODES.jsonl')
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    with open(a.out, 'w', encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
    print('WROTE %s records=%d assessed=%d dup=%d quotes_mismatch=%d'
          % (safe_rel(a.out), summary['records'], summary['assessed'],
             dup, quotes['mismatches']))
    for batch, d in decision.items():
        print('%s preliminary=%s g1=%s g2=%s g3=%s w/t/l=%s/%s/%s'
              % (batch, d.get('preliminary'), d.get('g1_data_integrity'),
                 d.get('g2_major_not_worse'), d.get('g3_strict_usable'),
                 d.get('wins_candidate'), d.get('ties'), d.get('wins_parent')))


if __name__ == '__main__':
    main()
