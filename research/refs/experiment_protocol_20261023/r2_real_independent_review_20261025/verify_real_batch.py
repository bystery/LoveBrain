"""Independent, read-only reconstruction of the paid batch. No model/network calls.

Only this review directory receives files; paid artifacts and worker reports remain intact.
"""
import collections
import datetime as dt
import hashlib
import json
import math
import pathlib
import re

ROOT = pathlib.Path('D:/lovebrain-arena')
HERE = pathlib.Path(__file__).resolve().parent
BATCH = ROOT / 'logs/r2_ctx_live_20261024'
PLAN_PATH = ROOT / 'refs/experiment_protocol_20261023/r2_next_batch_20261024/INTERLEAVE_PLAN.json'
BUDGET = ROOT / 'logs/auth_budget/AUTH-R2-CTX-20261024-01.jsonl'


def read(p):
    return json.loads(p.read_text(encoding='utf-8'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def digest_json(v):
    return hashlib.sha256(json.dumps(v, ensure_ascii=False, sort_keys=True).encode('utf-8')).hexdigest()


def quantile(v, p):
    v = sorted(v)
    x = (len(v) - 1) * p
    return round(v[math.floor(x)] + (v[math.ceil(x)] - v[math.floor(x)]) * (x - math.floor(x)))


def main():
    plan = read(PLAN_PATH)
    checks = {}
    frozen = dict(plan['frozen']['config_files'])
    for identity in plan['frozen']['identity_closure'].values():
        frozen.update(identity['files'])
    mismatch = [p for p, h in frozen.items() if not (ROOT / p).is_file() or sha(ROOT / p) != h]
    checks['frozen_files_match_current'] = not mismatch
    events, rows, snapshots_ok, seeds_ok, requests_ok, raw_ok = [], [], [], [], [], []
    run_ids = []
    for arm in ('parent', 'candidate'):
        for ep in ('E007', 'E010', 'E012'):
            run = BATCH / (arm + '_' + ep)
            out = run / 'out'
            episode_path = run / (ep + '.copy.json')
            episode = read(episode_path)
            snapshots_ok.append(sha(episode_path) == frozen['benchmark/episodes/' + ep + '.json'])
            turns = {t['case_id']: t for t in episode['turns']}
            identity = read(out / '_control/run_identity.json')
            run_ids.append(identity['run_id'])
            for line in (out / '_control/attempt_ledger.jsonl').read_text(encoding='utf-8').splitlines():
                if line.strip():
                    events.append(dict(json.loads(line), review_arm=arm, review_episode=ep))
            for folder in sorted(out.glob('turn_*')):
                mf = read(folder / 'manifest.json')
                call_list = read(folder / 'calls.json')
                assert isinstance(call_list, list) and len(call_list) == 1, folder
                call = call_list[0]
                raw = (folder / 'reply_raw.txt').read_text(encoding='utf-8')
                reply = json.loads(raw)
                response = reply.get('response') or {}
                primary = response.get('recommended') or ''
                texts = [{'slot': 'response.' + k, 'text': v} for k, v in response.items() if isinstance(v, str) and v.strip()]
                texts += [{'slot': 'directions[%d]' % i, 'text': v} for i, v in enumerate(reply.get('directions') or []) if isinstance(v, str) and v.strip()]
                chat_match = re.search(r'<chat>\s*(.*?)\s*</chat>', call['user'], re.S)
                assert chat_match is not None, folder
                current_ids = set(re.findall(r'"id"\s*:\s*"([^"]+)"', chat_match.group(1)))
                invalid = []
                for fact in (reply.get('analysis') or {}).get('scene_facts') or []:
                    if isinstance(fact, dict):
                        invalid.extend(s for s in fact.get('source_ids', []) if s not in current_ids)
                bound = mf.get('case_input_sha256') == digest_json(turns[mf['case_id']]['input'])
                snapshots_ok.append(bound)
                for value in episode.get('initial_kb', {}).values():
                    if isinstance(value, str):
                        seeds_ok.append(value in call['user'])
                requests_ok.append(call.get('temperature') == .7 and call.get('resp_model') == 'dsf' and mf.get('adapter') == 'openai_compat')
                raw_ok.append(call.get('raw_text') == raw)
                rows.append({
                    'arm': arm, 'episode': ep, 'case_id': mf['case_id'], 'run_id': mf['run_id'],
                    'manifest_path': str(folder / 'manifest.json'),
                    'raw_path': str(folder / 'reply_raw.txt'), 'raw_sha256': sha(folder / 'reply_raw.txt'),
                    'calls_sha256': sha(folder / 'calls.json'), 'case_input_sha256': mf['case_input_sha256'],
                    'snapshot_bound': bound, 'score10': mf['score10'], 'veto': mf.get('veto'),
                    'tokens': mf['tokens'], 'latency_ms': mf['latency_ms'],
                    'gateway_latency_ms': call['latency_ms'], 'response_id': call.get('resp_id'),
                    'images': call.get('images'), 'system_sha256': hashlib.sha256(call['system'].encode('utf-8')).hexdigest(),
                    'system_utf16': len(call['system'].encode('utf-16-le')) // 2,
                    'user_utf16': len(call['user'].encode('utf-16-le')) // 2,
                    'primary': primary, 'primary_chars': len(primary), 'all_texts': texts,
                    'invalid_fact_source_ids': invalid,
                    'literal_null_slots': [x['slot'] for x in texts if x['text'].strip() == 'null'],
                })
    starts = [e for e in events if e['kind'] == 'attempt_started']
    completed = [e for e in events if e['kind'] == 'attempt_completed']
    failed = [e for e in events if e['kind'] == 'attempt_failed']
    ordered = sorted(starts, key=lambda e: e['ts'])
    expected = [(x['arm'], x['episode'], x['case_id']) for x in plan['attempts']]
    actual = [(x['review_arm'], x['review_episode'], x['case_id']) for x in ordered]
    start_by = collections.Counter(e['attempt_id'] for e in starts)
    done_by = collections.Counter(e['attempt_id'] for e in completed)
    budget = [json.loads(s) for s in BUDGET.read_text(encoding='utf-8').splitlines() if s.strip()]
    budget_starts = [e for e in budget if e['kind'] == 'attempt_started']
    budget_done = [e for e in budget if e['kind'] == 'attempt_completed']
    budget_kinds = collections.Counter(e['kind'] for e in budget)
    checks.update({
        '48_rows': len(rows) == 48,
        'six_independent_run_ids': len(run_ids) == len(set(run_ids)) == 6,
        '48_started_48_completed_0_failed': len(starts) == len(completed) == 48 and len(failed) == 0,
        'attempt_one_to_one': len(start_by) == 48 and start_by == done_by and set(start_by.values()) == {1},
        'actual_start_order_matches_frozen_plan': actual == expected,
        'budget_control_attempt_ids_equal': set(start_by) == {e['attempt_id'] for e in budget_starts} == {e['attempt_id'] for e in budget_done},
        'budget_started_equals_cap': len(budget_starts) == 48,
        '48_unique_response_ids': len({r['response_id'] for r in rows}) == 48 and all(r['response_id'] for r in rows),
        'runtime_episodes_and_all_case_hashes_bound': all(snapshots_ok),
        'both_arms_all_public_seed_received': len(seeds_ok) == 48 and all(seeds_ok),
        'all_temperatures_adapter_model_alias_correct': all(requests_ok),
        'all_call_raw_text_equals_saved_reply': all(raw_ok),
        'all_48_strict_json_parsed': len(rows) == 48,
        'all_48_api_usage_known': all(r['tokens'].get('source') == 'api_usage' and not r['tokens'].get('has_unknown_usage') for r in rows),
        'all_48_no_actual_images': all(not r['images'] for r in rows),
        'one_system_per_arm': all(len({r['system_sha256'] for r in rows if r['arm'] == arm}) == 1 for arm in ('parent', 'candidate')),
    })
    metrics = {}
    for arm in ('parent', 'candidate'):
        rs = [r for r in rows if r['arm'] == arm]
        lat = [r['latency_ms'] for r in rs]
        prompt = sum(r['tokens']['prompt'] for r in rs)
        completion = sum(r['tokens']['completion'] for r in rs)
        metrics[arm] = {
            'n': len(rs), 'prompt_tokens': prompt, 'completion_tokens': completion, 'total_tokens': prompt + completion,
            'program_mean10': sum(r['score10'] for r in rs) / len(rs), 'veto_count': sum(bool(r['veto']) for r in rs),
            'latency_ms': {'p50': quantile(lat, .5), 'p95': quantile(lat, .95), 'max': max(lat), 'sum': sum(lat)},
            'primary_mean_chars': sum(r['primary_chars'] for r in rs) / len(rs),
            'nonempty_text_slots': sum(len(r['all_texts']) for r in rs),
            'nonempty_backup_slots': sum(len(r['all_texts']) - 1 for r in rs),
            'system_utf16': sorted({r['system_utf16'] for r in rs}),
        }
    delta = {k: round((metrics['candidate'][k] / metrics['parent'][k] - 1) * 100, 4)
             for k in ('prompt_tokens', 'completion_tokens', 'total_tokens', 'primary_mean_chars')}
    for k in ('p50', 'p95', 'sum'):
        delta['latency_' + k + '_percent'] = round((metrics['candidate']['latency_ms'][k] / metrics['parent']['latency_ms'][k] - 1) * 100, 4)
    times = [dt.datetime.strptime(e['ts'], '%Y-%m-%d %H:%M:%S') for e in starts + completed]
    result = {
        'review_kind': 'independent offline reconstruction; no generation, no new authorization',
        'batch': str(BATCH), 'plan_sha256': sha(PLAN_PATH), 'budget_ledger_sha256': sha(BUDGET),
        'checks': checks, 'frozen_mismatches': mismatch, 'frozen_file_count': len(frozen),
        'metrics': metrics, 'candidate_vs_parent_percent': delta,
        'wall_elapsed_seconds_from_event_timestamps': int((max(times) - min(times)).total_seconds()),
        'budget_kinds': dict(budget_kinds), 'budget_closed_event_present': bool(budget_kinds.get('authorization_closed')),
        'money': None, 'model_alias': 'dsf', 'underlying_provider_model_verified': False,
        'quality_not_inferred_from_program_score': True,
        'flagged_sources': [{'arm': r['arm'], 'case_id': r['case_id'], 'veto': r['veto'], 'ids': r['invalid_fact_source_ids']} for r in rows if r['invalid_fact_source_ids']],
        'literal_null_outputs': [{'arm': r['arm'], 'case_id': r['case_id'], 'slots': r['literal_null_slots']} for r in rows if r['literal_null_slots']],
        'stories_independent_sample_unit': 3, 'one_generation_per_arm_per_position': True,
    }
    HERE.mkdir(parents=True, exist_ok=True)
    (HERE / 'INDEPENDENT_RESULTS.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    (HERE / 'EXTRACTED_OUTPUTS.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
    (HERE / 'ACTUAL_DISPATCH_ORDER.json').write_text(json.dumps(ordered, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    assert all(checks.values()), [k for k, v in checks.items() if not v]


if __name__ == '__main__':
    main()
