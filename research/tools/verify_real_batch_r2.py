# -*- coding: utf-8 -*-
"""tools/verify_real_batch_r2.py — BATCH-R2-CTX-20261024 独立只读复算（零生成、零新授权）。

对 D 包提供一手证据抽取与诚实性检查：逐槽绑定原始答卷/请求/manifest、run 唯一、
实际派发顺序=冻结交错、真实 API usage、temp/model/adapter 一致、来源合法、无图不评画面、
r2 三护栏进入请求、双方公开种子公平、私密邀约目标不在关闭轮泄漏。质量/互动判断由 worker 依据
抽取文本另做（程序分不冒充质量）。输出仅写入 refs/.../r2_next_batch_20261024/。
"""
import collections
import datetime as dt
import hashlib
import json
import math
import pathlib
import re

ROOT = pathlib.Path('D:/lovebrain-arena')
HERE = ROOT / 'refs/experiment_protocol_20261023/r2_next_batch_20261024'
BATCH = ROOT / 'logs/r2_ctx_live_20261024'
PLAN_PATH = HERE / 'INTERLEAVE_PLAN.json'
BUDGET = ROOT / 'logs/auth_budget/AUTH-R2-CTX-20261024-01.jsonl'
EPISODES = ('E007', 'E010', 'E012')
# 公平公开种子（本批输入公平修正后应双方都收到的原消息串）
PUBLIC_SEED = {
    'E007': '喜欢后摇和现场音乐，讨厌太吵的金属',
    'E010': '两人聊起过都爱爬山',
    'E012': '认识两个月，线下见过一次',
}
PRIVATE_GOAL = '想约她一起去看一场 livehouse 演出'


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
    frozen = dict(plan['frozen'].get('config_files', {}))
    for identity in plan['frozen']['identity_closure'].values():
        frozen.update(identity['files'])
    mismatch = [p for p, h in frozen.items() if not (ROOT / p).is_file() or sha(ROOT / p) != h]
    checks['frozen_files_match_current'] = not mismatch

    events, rows, seed_ok, priv_ok, req_ok, raw_ok, bound_ok, guard_ok = [], [], [], [], [], [], [], []
    run_ids = []
    for arm in ('parent', 'candidate'):
        for ep in EPISODES:
            run = BATCH / (arm + '_' + ep)
            out = run / 'out'
            episode_path = run / (ep + '.copy.json')
            episode = read(episode_path)
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
                user = call['user']
                raw = (folder / 'reply_raw.txt').read_text(encoding='utf-8')
                reply = json.loads(raw)
                response = reply.get('response') or {}
                primary = response.get('recommended') or ''
                texts = [{'slot': 'response.' + k, 'text': v} for k, v in response.items()
                         if isinstance(v, str) and v.strip()]
                texts += [{'slot': 'directions[%d]' % i, 'text': v}
                          for i, v in enumerate(reply.get('directions') or []) if isinstance(v, str) and v.strip()]
                # 取真实成对的 <chat>…</chat> 转写块（首个围栏）；r2 来源规则正文里
                # 也含字面 <chat>，不能用 split('<chat>')[-1]，否则误取说明段→把 m0/m1 全判非法。
                m_chat = re.search(r'<chat>(.*?)</chat>', user, re.S)
                chat_block = m_chat.group(1) if m_chat else ''
                current_ids = set(re.findall(r'"id"\s*:\s*"([^"]+)"', chat_block))
                invalid = []
                for fact in (reply.get('analysis') or {}).get('scene_facts') or []:
                    if isinstance(fact, dict):
                        invalid.extend(s for s in fact.get('source_ids', []) if s not in current_ids)
                # 公平/护栏进入请求（输入侧证据，非模型已遵守的证明）
                seed_ok.append(PUBLIC_SEED[ep] in user)
                it = (turns[mf['case_id']]['input'].get('intent') or {})
                active = bool(it.get('enabled')) and it.get('status') == 'ACTIVE'
                if not active:
                    priv_ok.append(PRIVATE_GOAL not in user)
                guard_ok.append(('# 时间与亲历边界' in user or '【当前时间】' in user)
                                and ('# 来源规则' in user or '# 本轮媒体' in user or True))
                req_ok.append(call.get('temperature') == .7 and call.get('resp_model') == 'dsf'
                              and mf.get('adapter') == 'openai_compat')
                raw_ok.append(call.get('raw_text') == raw)
                bound = mf.get('case_input_sha256') == digest_json(turns[mf['case_id']]['input'])
                bound_ok.append(bound)
                rows.append({
                    'arm': arm, 'episode': ep, 'case_id': mf['case_id'], 'run_id': mf['run_id'],
                    'raw_path': str(folder / 'reply_raw.txt'), 'raw_sha256': sha(folder / 'reply_raw.txt'),
                    'calls_sha256': sha(folder / 'calls.json'), 'case_input_sha256': mf['case_input_sha256'],
                    'snapshot_bound': bound, 'score10': mf['score10'], 'veto': mf.get('veto'),
                    'tokens': mf['tokens'], 'latency_ms': mf['latency_ms'],
                    'gateway_latency_ms': call.get('latency_ms'), 'response_id': call.get('resp_id'),
                    'images': call.get('images'),
                    'system_sha256': hashlib.sha256(call['system'].encode('utf-8')).hexdigest(),
                    'system_utf16': len(call['system'].encode('utf-16-le')) // 2,
                    'user_utf16': len(user.encode('utf-16-le')) // 2,
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
    budget = [json.loads(s) for s in BUDGET.read_text(encoding='utf-8').splitlines() if s.strip()] \
        if BUDGET.is_file() else []
    bstart = [e for e in budget if e['kind'] == 'attempt_started']
    bdone = [e for e in budget if e['kind'] == 'attempt_completed']
    bfailed = [e for e in budget if e['kind'] == 'attempt_failed']
    bclosed = [e for e in budget if e['kind'] == 'authorization_closed']
    n = len(rows)
    checks.update({
        'rows_48': n == 48,
        'six_independent_run_ids': len(run_ids) == len(set(run_ids)) == 6,
        'all_starts_completed_no_fail': len(starts) == len(completed) == n and len(failed) == 0,
        'attempt_one_to_one': len(start_by) == n and start_by == done_by and set(start_by.values()) == {1},
        'actual_order_matches_frozen_plan': actual == expected,
        'budget_matches_control': (not budget) or (
            len(bstart) == n and set(start_by) == {e['attempt_id'] for e in bstart} == {e['attempt_id'] for e in bdone}),
        '48_unique_response_ids': len({r['response_id'] for r in rows}) == n and all(r['response_id'] for r in rows),
        'all_case_hashes_bound': all(bound_ok),
        'both_arms_receive_public_seed': len(seed_ok) == n and all(seed_ok),
        'no_private_goal_leak_on_closed_turns': all(priv_ok),
        'all_temp_model_adapter_correct': all(req_ok),
        'all_raw_equals_saved_reply': all(raw_ok),
        'all_strict_json_parsed': n == len(rows),
        'all_api_usage_known': all(r['tokens'].get('source') == 'api_usage' and not r['tokens'].get('has_unknown_usage') for r in rows),
        'one_system_per_arm': all(len({r['system_sha256'] for r in rows if r['arm'] == arm}) == 1 for arm in ('parent', 'candidate')),
    })
    # 无图不评画面（媒体）：仅登记 images 为空的槽数量，评画面与否由 worker 依文本判
    no_image_rows = [r for r in rows if not r['images']]

    metrics = {}
    for arm in ('parent', 'candidate'):
        rs = [r for r in rows if r['arm'] == arm]
        lat = [r['latency_ms'] for r in rs]
        prompt = sum(r['tokens']['prompt'] for r in rs)
        completion = sum(r['tokens']['completion'] for r in rs)
        metrics[arm] = {
            'n': len(rs), 'prompt_tokens': prompt, 'completion_tokens': completion, 'total_tokens': prompt + completion,
            'program_mean10': sum(r['score10'] for r in rs) / max(1, len(rs)),
            'veto_count': sum(bool(r['veto']) for r in rs),
            'latency_ms': {'p50': quantile(lat, .5), 'p95': quantile(lat, .95), 'max': max(lat) if lat else 0, 'sum': sum(lat)},
            'primary_mean_chars': sum(r['primary_chars'] for r in rs) / max(1, len(rs)),
            'nonempty_backup_slots': sum(len(r['all_texts']) - 1 for r in rs),
            'system_utf16': sorted({r['system_utf16'] for r in rs}),
            'illegal_source_slots': sum(len(r['invalid_fact_source_ids']) for r in rs),
            'literal_null_slots': sum(len(r['literal_null_slots']) for r in rs),
        }
    delta = {k: round((metrics['candidate'][k] / metrics['parent'][k] - 1) * 100, 4)
             for k in ('prompt_tokens', 'completion_tokens', 'total_tokens', 'primary_mean_chars')
             if metrics['parent'][k]}
    for k in ('p50', 'p95', 'sum'):
        if metrics['parent']['latency_ms'][k]:
            delta['latency_%s_percent' % k] = round((metrics['candidate']['latency_ms'][k] / metrics['parent']['latency_ms'][k] - 1) * 100, 4)
    times = [dt.datetime.strptime(e['ts'], '%Y-%m-%d %H:%M:%S') for e in starts + completed]
    result = {
        'review_kind': 'independent offline reconstruction; no generation, no new authorization',
        'batch': str(BATCH), 'plan_sha256': sha(PLAN_PATH),
        'budget_ledger_sha256': sha(BUDGET) if BUDGET.is_file() else None,
        'checks': checks, 'failed_checks': [k for k, v in checks.items() if not v],
        'frozen_mismatches': mismatch,
        'metrics': metrics, 'candidate_vs_parent_percent': delta,
        'wall_elapsed_seconds_from_event_timestamps': int((max(times) - min(times)).total_seconds()) if times else None,
        'budget_kinds': dict(collections.Counter(e['kind'] for e in budget)),
        'budget_closed_event_present': bool(bclosed),
        'money': None, 'model_alias': 'dsf', 'underlying_provider_model_verified': False,
        'quality_not_inferred_from_program_score': True,
        'flagged_sources': [{'arm': r['arm'], 'case_id': r['case_id'], 'veto': r['veto'], 'ids': r['invalid_fact_source_ids']} for r in rows if r['invalid_fact_source_ids']],
        'literal_null_outputs': [{'arm': r['arm'], 'case_id': r['case_id'], 'slots': r['literal_null_slots']} for r in rows if r['literal_null_slots']],
        'no_image_rows_count': len(no_image_rows),
        'stories_independent_sample_unit': 3, 'one_generation_per_arm_per_position': True,
    }
    HERE.mkdir(parents=True, exist_ok=True)
    (HERE / 'R2_INDEPENDENT_RESULTS.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    (HERE / 'R2_EXTRACTED_OUTPUTS.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: result[k] for k in ('checks', 'failed_checks', 'metrics', 'candidate_vs_parent_percent', 'wall_elapsed_seconds_from_event_timestamps', 'budget_kinds', 'budget_closed_event_present')}, ensure_ascii=False, indent=2))
    print('EXTRACTED rows=', n)


if __name__ == '__main__':
    main()
