"""Independent v5 readiness check; no real generation and no product edits."""
import collections
import copy
import hashlib
import importlib.util
import json
import pathlib
import socket
import sys
import types

ROOT = pathlib.Path('D:/lovebrain-arena')
HERE = pathlib.Path(__file__).resolve().parent
BATCH_REF = ROOT / 'refs/experiment_protocol_20261023/v5_next_batch_20261025'
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'arena')]


def read(p):
    return json.loads(p.read_text(encoding='utf-8'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    HERE.mkdir(parents=True, exist_ok=True)
    plan = read(BATCH_REF / 'INTERLEAVE_PLAN.json')
    auth = read(BATCH_REF / 'AUTHORIZATION_REQUEST.json')
    files = dict(plan['frozen']['config_files'])
    for v in plan['frozen']['identity_closure'].values():
        files.update(v['files'])
    drift = [p for p, h in files.items() if not (ROOT / p).is_file() or sha(ROOT / p) != h]
    checks = {'frozen_closure_and_files_match': not drift,
              'candidate_identity_bound_to_request': auth['frozen_bindings']['candidate_identity_sha256'] == plan['frozen']['identity_closure']['candidate']['identity_sha256'],
              'request_pending': auth['authorized'] is False and auth['status'] == 'PENDING_USER_APPROVAL',
              'old_r2_authorization_closed': False,
              'new_real_budget_absent': not (ROOT / auth['budget_ledger']).exists()}
    old = [json.loads(s) for s in (ROOT / 'logs/auth_budget/AUTH-R2-CTX-20261024-01.jsonl').read_text(encoding='utf-8').splitlines() if s.strip()]
    kinds = collections.Counter(e['kind'] for e in old)
    checks['old_r2_authorization_closed'] = kinds['attempt_started'] == kinds['attempt_completed'] == 48 and kinds['authorization_closed'] == 1

    # Reuse the submitted fixtures, relocating their scratch writes to this review.
    test_path = ROOT / 'tools/test_v5_purpose_aligned.py'
    src = test_path.read_text(encoding='utf-8').replace('os.path.join(ROOT, "logs", "_v5tmp",', 'os.path.join(str(REVIEW_DIR), "fixtures",')
    fixture = types.ModuleType('review_v5_fixture_helpers')
    fixture.__dict__.update({'__file__': str(test_path), 'REVIEW_DIR': HERE})
    exec(compile(src, str(test_path), 'exec'), fixture.__dict__)
    fixture_results = []
    for fn in fixture.CHECKS:
        try:
            fixture_results.append({'name': fn.__name__, 'ok': True, 'detail': fn()})
        except Exception as e:
            fixture_results.append({'name': fn.__name__, 'ok': False, 'error': str(e)})
    checks['six_v5_fixtures_pass'] = len(fixture_results) == 6 and all(v['ok'] for v in fixture_results)
    spec = importlib.util.spec_from_file_location('review_v5_executor', ROOT / 'tools/exam_interleave_plan.py')
    ep = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(ep)
    original_connect = socket.socket.connect
    def refuse_connect(*a, **kw):
        raise RuntimeError('Independent review forbids real socket connection')
    socket.socket.connect = refuse_connect
    try:
        error = None
        try:
            ep.execute_plan(plan, mode='live', authorization=copy.deepcopy(auth), out_base=str(HERE / 'rejected_live'))
        except Exception as e:
            error = str(e)
        checks['pending_live_rejected_before_dispatch'] = error is not None and ('未授权' in error or 'authorized' in error or '批准' in error)
        capture = ep.execute_plan(plan, mode='capture', out_base=str(HERE / 'capture'), keep_tmp=True)
    finally:
        socket.socket.connect = original_connect
    checks['capture_48_order_verified_zero_real'] = capture['attempt_started'] == capture['attempt_completed'] == 48 and capture['real_model_calls'] == 0 and capture['execution_order_verified'] is True
    checks['six_independent_states'] = capture.get('state_isolated') is True
    rows = []
    for arm in ['parent', 'candidate']:
        for eid in ['E007', 'E008', 'E009']:
            episode = read(ROOT / 'benchmark/episodes' / (eid + '.json'))
            turns = {t['case_id']: t for t in episode['turns']}
            for folder in sorted((HERE / 'capture' / (arm + '_' + eid) / 'out').glob('turn_*')):
                c = read(folder / 'calls.json')[0]
                cid = read(folder / 'manifest.json')['case_id']
                missing = [key for key, value in episode['initial_kb'].items() if isinstance(value, str) and value not in c['user']]
                inp = turns[cid]['input']
                intent = inp.get('intent') or {}
                active = bool(intent.get('enabled')) and intent.get('status') == 'ACTIVE'
                v5_protocol = '固定输出空对象' in c['system'] and '"analysis": {}' in c['system']
                rows.append({'arm': arm, 'case_id': cid, 'seed_missing': missing,
                             'expected_active': active,
                             'active_block_present': '【当前目标(' in c['user'] or '【持续意图' in c['user'],
                             'old_permanent_private_goal_present': '想约她一起去看一场 livehouse 演出' in c['user'],
                             'v5_analysis_empty_instruction': v5_protocol,
                             'user_utf16': len(c['user'].encode('utf-16-le')) // 2,
                             'system_utf16': len(c['system'].encode('utf-16-le')) // 2})
    checks['full_seed_values_in_both_arms'] = len(rows) == 48 and all(not x['seed_missing'] for x in rows)
    checks['goal_on_off_inputs_correct'] = all(x['expected_active'] == x['active_block_present'] and not x['old_permanent_private_goal_present'] for x in rows)
    checks['all_candidate_requests_require_empty_analysis'] = all(x['v5_analysis_empty_instruction'] for x in rows if x['arm'] == 'candidate')
    checks['reference_not_given_new_empty_analysis_protocol'] = not any(x['v5_analysis_empty_instruction'] for x in rows if x['arm'] == 'parent')
    result = {'review_date': '2026-10-25 Asia/Shanghai', 'real_model_calls': 0,
              'checks': checks, 'frozen_drift': drift, 'fixtures': fixture_results,
              'pending_auth_error': error, 'candidate_identity': plan['frozen']['identity_closure']['candidate']['identity_sha256'],
              'rows': rows, 'capture_is_not_model_quality_or_resource_evidence': True}
    (HERE / 'CAPTURE_RESULT.json').write_text(json.dumps(capture, ensure_ascii=False, indent=2), encoding='utf-8')
    (HERE / 'CHECKS.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k not in ['fixtures', 'rows']}, ensure_ascii=False, indent=2))
    assert all(checks.values()), [k for k, v in checks.items() if not v]


if __name__ == '__main__':
    main()
