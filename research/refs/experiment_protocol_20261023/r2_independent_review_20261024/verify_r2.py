"""Focused independent review. No real generation; writes only this directory.

Capture uses the existing executor's fake HTTP transport, plus a socket-connect
deny guard in the controller process. Candidate/paid artifacts are read only.
"""
import collections
import copy
import hashlib
import importlib.util
import json
import pathlib
import socket
import sys

ROOT = pathlib.Path('D:/lovebrain-arena')
HERE = pathlib.Path(__file__).resolve().parent
REF = ROOT / 'refs/experiment_protocol_20261023/r2_next_batch_20261024'
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'arena')]


def load(p, name):
    spec = importlib.util.spec_from_file_location(name, p)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def read(p):
    return json.loads(p.read_text(encoding='utf-8'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    HERE.mkdir(parents=True, exist_ok=True)
    plan = read(REF / 'INTERLEAVE_PLAN.json')
    auth = read(REF / 'AUTHORIZATION_REQUEST.json')
    checks, issues = {}, []
    files = dict(plan['frozen']['config_files'])
    for x in plan['frozen']['identity_closure'].values():
        files.update(x['files'])
    drift = [p for p, h in files.items() if not (ROOT / p).exists() or sha(ROOT / p) != h]
    checks['frozen_files_match'] = not drift
    checks['expected_arms_and_stories'] = (plan['arm_mapping'] == {'parent': 'reference_product_v1', 'candidate': 'v4_intent_context_r2'} and [x['episode'] for x in plan['episode_order']] == ['E007', 'E010', 'E012'])
    checks['request_is_pending_not_authorized'] = auth['authorized'] is False and auth['status'] == 'PENDING_USER_APPROVAL'
    budget = [json.loads(x) for x in (ROOT / 'logs/auth_budget/AUTH-V3-DELETION-20261024-01.jsonl').read_text(encoding='utf-8').splitlines() if x.strip()]
    counts = collections.Counter(x['kind'] for x in budget)
    checks['old_budget_closed_at_48'] = counts['authorization_closed'] == 1 and counts['attempt_started'] == counts['attempt_completed'] == 48
    checks['new_real_budget_absent'] = not (ROOT / auth['budget_ledger']).exists()
    old = read(ROOT / 'refs/experiment_protocol_20261023/real_v3_independent_review_20261024/PAIRED_CONCLUSION.json')
    old_rows = {x['case_id']: x for x in old['rows']}
    bound = []
    for row in old['rows']:
        cid = row['case_id']; ep = cid.split('_')[0]
        for arm, key in [('parent', 'parent'), ('candidate', 'candidate')]:
            folder = next((ROOT / 'logs/v3_deletion_live_20261024' / (arm + '_' + ep) / 'out').glob('turn_*_' + cid))
            mf = read(folder / 'manifest.json')
            bound.append(sha(folder / 'reply_raw.txt') == row[key + '_raw_sha256'] and mf['case_input_sha256'] == row['case_input_sha256'])
    checks['old_24_pairs_raw_and_input_hashes_bound'] = len(bound) == 48 and all(bound)
    issues.append({'id': 'OLD_PAIRING_SIDE_ERRORS', 'evidence': {cid: {k: old_rows[cid].get(k) for k in ['supports', 'rationale', 'parent_veto', 'candidate_veto']} for cid in ['E004_T08', 'E006_T02', 'E004_T07']}, 'impact': 'support counts not accepted; original paid artifacts remain usable'})
    mech = ROOT / 'candidates/v4_intent_context_r2/mechanism/mechanism.py'
    text = mech.read_text(encoding='utf-8')
    issues.append({'id': 'TIME_SCOPE_WORDING', 'present': '属于它出现那天的日期' in text, 'impact': 'message timestamp is not necessarily the date referred to by the event; rewrite before live'})
    fixture = load(ROOT / 'tools/test_v4_intent_context_r2.py', 'review_r2_fixtures')
    fixture._h = lambda: fixture.r2.init(str(ROOT / 'candidates/v4_intent_context_r2/assets'), {'episode_id': 'X', 'persona': {'me': '本人', 'her': '对方'}, 'initial_kb': {'ongoing_plan': [{'name': '周六livehouse', 'status': '暂定'}]}}, None, str(HERE / 'fixtures'))
    fixture_results = []
    for fn in fixture.CHECKS:
        try:
            fixture_results.append({'name': fn.__name__, 'ok': True, 'detail': fn()})
        except Exception as e:
            fixture_results.append({'name': fn.__name__, 'ok': False, 'error': str(e)})
    checks['r2_fixtures_7'] = len(fixture_results) == 7 and all(x['ok'] for x in fixture_results)
    epmod = load(ROOT / 'tools/exam_interleave_plan.py', 'review_r2_executor')
    def forbidden(*args, **kwargs):
        raise RuntimeError('Independent review forbids real socket connection')
    connect = socket.socket.connect
    socket.socket.connect = forbidden
    try:
        rejected = None
        try:
            epmod.execute_plan(plan, mode='live', authorization=copy.deepcopy(auth), out_base=str(HERE / 'rejected_live'))
        except Exception as e:
            rejected = type(e).__name__ + ': ' + str(e)
        checks['pending_authorization_rejected'] = rejected is not None and ('未授权' in rejected or 'authorized' in rejected or '批准' in rejected)
        result = epmod.execute_plan(plan, mode='capture', out_base=str(HERE / 'capture'), keep_tmp=True)
    finally:
        socket.socket.connect = connect
    (HERE / 'CAPTURE_RESULT.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    checks['same_path_capture_48'] = result.get('attempt_started') == result.get('attempt_completed') == 48 and result.get('real_model_calls') == 0 and result.get('execution_order_verified') is True
    seed_results = []
    for arm in ['parent', 'candidate']:
        for eid in ['E007', 'E010', 'E012']:
            episode = read(ROOT / 'benchmark/episodes' / (eid + '.json'))
            for folder in sorted((HERE / 'capture' / (arm + '_' + eid) / 'out').glob('turn_*')):
                calls = read(folder / 'calls.json'); u = calls[0]['user']
                cid = read(folder / 'manifest.json')['case_id']
                missing = [k for k, v in episode['initial_kb'].items() if isinstance(v, str) and v not in u]
                seed_results.append({'arm': arm, 'case_id': cid, 'missing_seed': missing, 'permanent_private_goal_present': '想约她一起去看一场 livehouse 演出' in u})
    checks['actual_captured_48_seed_values_fair'] = len(seed_results) == 48 and all(not x['missing_seed'] and not x['permanent_private_goal_present'] for x in seed_results)
    out = {'real_model_calls': 0, 'checks': checks, 'frozen_drift': drift, 'old_authorization_events': dict(counts), 'candidate_identity': plan['frozen']['identity_closure']['candidate']['identity_sha256'], 'fixture_results': fixture_results, 'pending_auth_rejection': rejected, 'seed_results': seed_results, 'issues': issues, 'semantic_quality_not_proven_by_capture': True}
    (HERE / 'CHECKS.json').write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in out.items() if k not in ['fixture_results', 'seed_results', 'issues']}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
