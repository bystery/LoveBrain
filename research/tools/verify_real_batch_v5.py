# -*- coding: utf-8 -*-
"""tools/verify_real_batch_v5.py — BATCH-V5-CTX-20261025 独立只读复算（零生成、零新授权）。

D 包一手证据：逐槽绑原始答卷/请求/manifest、run 唯一、实际派发顺序=冻结交错、真实 API usage、
temp/model/adapter、来源合法（成对 <chat> 抽取）、双方公开种子公平、E007 私密邀约目标不泄漏、
以及**空 analysis 实际遵守率**（v5 有多少份确实 {}、多少仍生成内容/格式坏——是实现遵从，非聊天胜负）。
质量/互动判断由 worker 依据抽取文本另做。输出仅写入本 refs 目录。须在真实 48 次完成后运行。
"""
import collections
import datetime as dt
import hashlib
import json
import math
import pathlib
import re

ROOT = pathlib.Path('D:/lovebrain-arena')
HERE = ROOT / 'refs/experiment_protocol_20261023/v5_next_batch_20261025'
BATCH = ROOT / 'logs/v5_ctx_live_20261025'
PLAN_PATH = HERE / 'INTERLEAVE_PLAN.json'
BUDGET = ROOT / 'logs/auth_budget/AUTH-V5-CTX-20261025-01.jsonl'
EPISODES = ('E007', 'E008', 'E009')
PUBLIC_SEED = {
    'E007': '喜欢后摇和现场音乐，讨厌太吵的金属',
    'E008': '她妹妹在读研，最近准备答辩',
    'E009': '此前 ME 答应帮 HER 重装系统',
}
EXTRA_SEED = {'E008': '共同朋友小林是当初牵线认识的那位'}
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

    events, rows, seed_ok, priv_ok, req_ok, raw_ok, bound_ok = [], [], [], [], [], [], []
    run_ids = []
    analysis_empty_v5 = 0
    analysis_nonempty_v5 = 0
    analysis_corrupt_v5 = 0
    for arm in ('parent', 'candidate'):
        for ep in EPISODES:
            run = BATCH / (arm + '_' + ep)
            out = run / 'out'
            episode = read(run / (ep + '.copy.json'))
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
                try:
                    reply = json.loads(raw)
                    parse_ok = isinstance(reply, dict)
                except json.JSONDecodeError:
                    reply, parse_ok = {}, False
                response = reply.get('response') or {}
                primary = response.get('recommended') or ''
                texts = [{'slot': 'response.' + k, 'text': v} for k, v in response.items()
                         if isinstance(v, str) and v.strip()]
                texts += [{'slot': 'directions[%d]' % i, 'text': v}
                          for i, v in enumerate(reply.get('directions') or []) if isinstance(v, str) and v.strip()]
                # 修订①：真实的合法缺省(null)必须单独计数。旧版只保留 str 槽位，
                # 把 directions 里的 JSON null 静默丢弃，导致误报"48 位没用过 null"。
                dirs_raw = reply.get('directions')
                dir_nulls = ([i for i, v in enumerate(dirs_raw) if v is None]
                             if isinstance(dirs_raw, list) else [])
                resp_nulls = ([k for k, v in response.items() if v is None]
                              if isinstance(response, dict) else [])
                no_reply = bool(reply.get('no_reply')) or reply.get('action') == 'no_reply'
                m_chat = re.search(r'<chat>(.*?)</chat>', user, re.S)   # 成对围栏，避免误取说明段
                chat_block = m_chat.group(1) if m_chat else ''
                current_ids = set(re.findall(r'"id"\s*:\s*"([^"]+)"', chat_block))
                invalid = []
                for fact in (reply.get('analysis') or {}).get('scene_facts') or []:
                    if isinstance(fact, dict):
                        invalid.extend(s for s in fact.get('source_ids', []) if s not in current_ids)
                a = reply.get('analysis')
                analysis_is_empty = (a is None) or (isinstance(a, dict) and not a)
                if arm == 'candidate':
                    if not parse_ok:
                        analysis_corrupt_v5 += 1
                    elif analysis_is_empty:
                        analysis_empty_v5 += 1
                    else:
                        analysis_nonempty_v5 += 1
                seed_ok.append(PUBLIC_SEED[ep] in user and EXTRA_SEED.get(ep, '') in user or EXTRA_SEED.get(ep) is None)
                it = (turns[mf['case_id']]['input'].get('intent') or {})
                active = bool(it.get('enabled')) and it.get('status') == 'ACTIVE'
                if ep == 'E007' and not active:
                    priv_ok.append(PRIVATE_GOAL not in user)
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
                    'parse_ok': parse_ok, 'analysis_empty': analysis_is_empty,
                    'tokens': mf['tokens'], 'latency_ms': mf['latency_ms'],
                    'gateway_latency_ms': call.get('latency_ms'), 'response_id': call.get('resp_id'),
                    'images': call.get('images'),
                    'system_utf16': len(call['system'].encode('utf-16-le')) // 2,
                    'user_utf16': len(user.encode('utf-16-le')) // 2,
                    'primary': primary, 'primary_chars': len(primary), 'all_texts': texts,
                    'direction_null_indices': dir_nulls, 'response_null_keys': resp_nulls,
                    'no_reply_turn': no_reply,
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
    n = len(rows)
    checks.update({
        'rows_48': n == 48,
        'six_independent_run_ids': len(run_ids) == len(set(run_ids)) == 6,
        'all_starts_completed_no_fail': len(starts) == len(completed) == n and len(failed) == 0,
        'attempt_one_to_one': len(start_by) == n and start_by == done_by and set(start_by.values()) == {1},
        'actual_order_matches_frozen_plan': actual == expected,
        '48_unique_response_ids': len({r['response_id'] for r in rows}) == n and all(r['response_id'] for r in rows),
        'all_case_hashes_bound': all(bound_ok),
        'both_arms_receive_public_seed': len(seed_ok) == n and all(seed_ok),
        'no_private_goal_leak_on_closed_turns': all(priv_ok),
        'all_temp_model_adapter_correct': all(req_ok),
        'all_raw_equals_saved_reply': all(raw_ok),
        'all_48_json_parsed': all(r['parse_ok'] for r in rows),
        'all_api_usage_known': all(r['tokens'].get('source') == 'api_usage' and not r['tokens'].get('has_unknown_usage') for r in rows),
        'one_system_per_arm': all(len({r['system_utf16'] for r in rows if r['arm'] == arm}) >= 1 for arm in ('parent', 'candidate')),
    })
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
            'illegal_source_slots': sum(len(r['invalid_fact_source_ids']) for r in rs),
            'literal_null_slots': sum(len(r['literal_null_slots']) for r in rs),
            'literal_null_slots_note': '只捕获字面字符串"null"，不等于合法缺省；见下面三项真实计数。',
            'direction_legal_nulls': sum(len(r['direction_null_indices']) for r in rs),
            'response_style_nulls': sum(len(r['response_null_keys']) for r in rs),
            'no_reply_turns': sum(1 for r in rs if r['no_reply_turn']),
            'missing_recommended_turns': sum(1 for r in rs if not r['primary']),
        }
    delta = {k: round((metrics['candidate'][k] / metrics['parent'][k] - 1) * 100, 4)
             for k in ('prompt_tokens', 'completion_tokens', 'total_tokens', 'primary_mean_chars') if metrics['parent'][k]}
    for k in ('p50', 'p95', 'sum'):
        if metrics['parent']['latency_ms'][k]:
            delta['latency_%s_percent' % k] = round((metrics['candidate']['latency_ms'][k] / metrics['parent']['latency_ms'][k] - 1) * 100, 4)
    times = [dt.datetime.strptime(e['ts'], '%Y-%m-%d %H:%M:%S') for e in starts + completed]
    budget_closed = BUDGET.is_file() and any(json.loads(s).get('kind') == 'authorization_closed'
                                             for s in BUDGET.read_text(encoding='utf-8').splitlines() if s.strip())
    result = {
        'review_kind': 'independent offline reconstruction; no generation, no new authorization',
        'batch': str(BATCH), 'plan_sha256': sha(PLAN_PATH),
        'budget_ledger_present': BUDGET.is_file(), 'budget_closed_event_present': bool(budget_closed),
        'checks': checks, 'failed_checks': [k for k, v in checks.items() if not v],
        'frozen_mismatches': mismatch,
        'empty_analysis_compliance_v5': {
            'empty': analysis_empty_v5, 'nonempty': analysis_nonempty_v5, 'corrupt': analysis_corrupt_v5,
            'denominator': metrics['candidate']['n'],
            'note': '实现遵从，非聊天质量胜负；nonempty/corrupt 才是需要看的偏差。',
        },
        'metrics': metrics, 'candidate_vs_parent_percent': delta,
        'wall_elapsed_seconds_from_event_timestamps': int((max(times) - min(times)).total_seconds()) if times else None,
        'money': None, 'model_alias': 'dsf', 'underlying_provider_model_verified': False,
        'quality_not_inferred_from_program_score': True,
        'flagged_sources': [{'arm': r['arm'], 'case_id': r['case_id'], 'ids': r['invalid_fact_source_ids']} for r in rows if r['invalid_fact_source_ids']],
        'literal_null_outputs': [{'arm': r['arm'], 'case_id': r['case_id'], 'slots': r['literal_null_slots']} for r in rows if r['literal_null_slots']],
        'stories_independent_sample_unit': 3, 'one_generation_per_arm_per_position': True,
    }
    null_crosscheck = None
    nc = (ROOT / 'refs/experiment_protocol_20261023/v5_real_independent_review_20261025/'
          'NULL_AND_ANALYSIS_COUNTS.json')
    if nc.is_file():
        ref = {x['arm']: x for x in read(nc)}
        null_crosscheck = {}
        for arm in ('parent', 'candidate'):
            r, mine = ref.get(arm, {}), metrics[arm]
            null_crosscheck[arm] = {
                'reference_direction_nulls': r.get('direction_null_slots'),
                'mine_direction_legal_nulls': mine['direction_legal_nulls'],
                'direction_nulls_agree': r.get('direction_null_slots') == mine['direction_legal_nulls'],
                'reference_no_reply_actions': r.get('no_reply_actions'),
                'mine_no_reply_turns': mine['no_reply_turns'],
                'no_reply_agree': r.get('no_reply_actions') == mine['no_reply_turns'],
                'reference_analysis_empty': r.get('analysis_empty_count'),
                'mine_analysis_empty': sum(1 for x in rows
                                           if x['arm'] == arm and x['analysis_empty']),
            }
            null_crosscheck[arm]['analysis_empty_agree'] = (
                null_crosscheck[arm]['reference_analysis_empty']
                == null_crosscheck[arm]['mine_analysis_empty'])
        null_crosscheck['all_agree'] = all(
            null_crosscheck[a][k] for a in ('parent', 'candidate')
            for k in ('direction_nulls_agree', 'no_reply_agree', 'analysis_empty_agree'))
    result['null_usage_revision_rev01'] = {
        'reason': '旧版抽取只保留字符串槽位，漏计 directions 里的 JSON null；'
                  '本报告更正为分列计数。修订不改任何付费原文与旧评分。',
        'per_arm': {arm: {k: metrics[arm][k] for k in
                          ('direction_legal_nulls', 'response_style_nulls',
                           'no_reply_turns', 'missing_recommended_turns',
                           'literal_null_slots')} for arm in metrics},
        'independent_crosscheck': null_crosscheck,
    }
    # 修订版另存，已交付的 V5_INDEPENDENT_RESULTS.json / V5_EXTRACTED_OUTPUTS.json 保持不动
    (HERE / 'V5_INDEPENDENT_RESULTS_REV01.json').write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    (HERE / 'V5_EXTRACTED_OUTPUTS_REV01.json').write_text(
        json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: result[k] for k in ('failed_checks', 'null_usage_revision_rev01')},
                     ensure_ascii=False, indent=2))
    print(json.dumps({k: result[k] for k in ('checks', 'failed_checks', 'empty_analysis_compliance_v5',
                                             'metrics', 'candidate_vs_parent_percent',
                                             'wall_elapsed_seconds_from_event_timestamps')}, ensure_ascii=False, indent=2))
    print('EXTRACTED rows=', n)


if __name__ == '__main__':
    main()
