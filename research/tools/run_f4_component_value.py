# -*- coding: utf-8 -*-
r"""tools/run_f4_component_value.py — F4 组件价值（真实 checkpoint + 反事实请求）。

每题：
  1) on 分支：复制 F3 的真实 checkpoint（K=T24，N=T12），resume 只生成探针轮；
  2) off 分支：取 on 分支同一 system/current prompt，仅删除声明的记忆投影段
     （知识）或动作层段（行动），直接调用同一 dsf，再过同一确定性 guard；
  3) 12 对（6 知识 + 6 行动）交盲评。
共 24 次物理请求（12 on + 12 off），前史由 checkpoint 恢复，不重烧。
"""
import argparse
import concurrent.futures
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import threading
import time
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, 'arena'))
MAT = os.path.join(ROOT, 'benchmark', 'stories_v2', 'final_20261029')
PROBES = os.path.join(MAT, 'f4_probes', 'PROBE_MANIFEST.json')
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')
AID = 'AUTH-F4-LOCAL-20261029-01'
MODEL = 'dsf'
TEMPERATURE = 0.7
ENDPOINT = 'http://127.0.0.1:17770/v1'
CAP = 24
FINAL = 'action_policy_r4_guard_final'


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def dump(p, obj):
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write('\n')


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def remove_block(prompt, start_mark):
    i = prompt.find(start_mark)
    if i < 0:
        return prompt, ''
    j = len(prompt)
    for mark in ('\n【', '\n# '):
        k = prompt.find(mark, i + len(start_mark))
        if k >= 0:
            j = min(j, k + 1)
    return prompt[:i] + prompt[j:], prompt[i:j]


def parse_answer(raw):
    try:
        obj = json.loads(raw)
    except Exception:
        return {'kind': 'format_fail', 'raw_excerpt': raw[:300]}
    if not isinstance(obj, dict):
        return {'kind': 'format_fail', 'raw_excerpt': raw[:300]}
    if str(obj.get('action') or '').lower() == 'no_reply':
        return {'kind': 'no_reply', 'decline_reason': obj.get('decline_reason') or ''}
    resp = obj.get('response') if isinstance(obj.get('response'), dict) else {}
    primary = ''
    for k in ('recommended', 'warm', 'playful', 'bad_boy'):
        v = resp.get(k)
        if isinstance(v, str) and v.strip():
            primary = v.strip()
            break
    backups = []
    for k in ('recommended', 'bad_boy', 'playful', 'warm'):
        v = resp.get(k)
        if isinstance(v, str) and v.strip() and v.strip() != primary:
            backups.append({'slot': 'response.%s' % k, 'text': v.strip()})
    dirs = obj.get('directions')
    if isinstance(dirs, list):
        for i, d in enumerate(dirs):
            if isinstance(d, str) and d.strip():
                backups.append({'slot': 'directions[%d]' % i, 'text': d.strip()})
    if not primary and not backups:
        return {'kind': 'format_fail', 'raw_excerpt': raw[:300]}
    if not primary:
        primary = backups.pop(0)['text']
    return {'kind': 'reply', 'primary': primary, 'backups': backups}


_GUARD = None


def guard_mod():
    global _GUARD
    if _GUARD is None:
        p = os.path.join(ROOT, 'candidates', FINAL, 'mechanism', 'contract_guard.py')
        spec = importlib.util.spec_from_file_location('f4_guard', p)
        _GUARD = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(_GUARD)
    return _GUARD


def roles_for(ep, turn_index):
    roles = {}
    msgs = ep['turns'][turn_index]['input']['messages']
    for i, m in enumerate(msgs):
        roles['m%d' % i] = 'HER' if m.get('role') == 'HER' else 'ME'
    return roles


def run_on(probe, out_base, auth_path, timeout_s=300):
    label = probe['probe_id'] + '_on'
    out = os.path.join(out_base, label)
    run_dir = os.path.join(out_base, '_iso_run_' + label)
    shutil.copytree(os.path.join(ROOT, probe['source_out']), out)
    shutil.copytree(os.path.join(ROOT, probe['source_run']), run_dir)
    # 每个分支必须有唯一 run_id：复制出来的 out 仍带原 run_id，多个探针会
    # 在授权账本里撞同一个 attempt_id（g0025...），导致 duplicate 拒派。
    rid = 'iso_f4_%s_%s' % (probe['probe_id'], uuid.uuid4().hex[:8])
    ri_path = os.path.join(out, '_control', 'run_identity.json')
    if os.path.isfile(ri_path):
        ri = load(ri_path)
        ri['fork_run_id'] = rid
        ri['parent_run_id'] = ri.get('run_id')
        ri['run_id'] = rid
        dump(ri_path, ri)
    turns_total = probe['prefix_turns'] + 1
    argv = [sys.executable, os.path.join(ROOT, 'tools', 'iso_run.py'),
            '--episode', os.path.join(ROOT, probe['episode']),
            '--candidate', FINAL, '--out', out, '--run-dir', run_dir,
            '--adapter', 'openai_compat', '--quota', str(turns_total),
            '--authorization', auth_path,
            '--expect-attempts', '1', '--resume']
    env = dict(os.environ, PYTHONIOENCODING='utf-8')
    p = subprocess.run(argv, cwd=ROOT, env=env, capture_output=True,
                       text=True, encoding='utf-8', errors='replace',
                       timeout=turns_total * 180 + 300)
    log = os.path.join(out_base, 'CHAIN_%s.log' % label)
    with open(log, 'a', encoding='utf-8') as f:
        f.write('$ %s\nrc=%s\n--- stdout ---\n%s\n--- stderr ---\n%s\n\n'
                % (' '.join(argv), p.returncode, p.stdout, p.stderr))
    td = os.path.join(out, 'turn_%02d_%s' % (turns_total, probe['probe_id']))
    if p.returncode != 0 or not os.path.isfile(os.path.join(td, 'reply_raw.txt')):
        return {'probe_id': probe['probe_id'], 'channel': probe['channel'],
                'status': 'on_failed', 'rc': p.returncode, 'log': log}
    calls = load(os.path.join(td, 'calls.json'))
    raw = open(os.path.join(td, 'reply_raw.txt'), encoding='utf-8').read()
    return {'probe_id': probe['probe_id'], 'channel': probe['channel'],
            'status': 'on_ok', 'out': out, 'turn_dir': td,
            'system': calls[0].get('system') or '',
            'on_prompt': calls[0].get('user') or '',
            'on_raw': raw, 'on_answer': parse_answer(raw),
            'usage_on': calls[0].get('usage'),
            'latency_on': calls[0].get('latency_ms'),
            'notes': open(os.path.join(td, 'mechanism_notes.txt'), encoding='utf-8').read()
            if os.path.isfile(os.path.join(td, 'mechanism_notes.txt')) else ''}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--run-base', required=True)
    ap.add_argument('--outdir', default=os.path.join(ROOT, 'logs', 'f4_component_20261029'))
    ap.add_argument('--authorization', default=os.path.join(OUT, 'F4_AUTHORIZATION_20261029.json'))
    ap.add_argument('--mode', default='live', choices=('live', 'mock'))
    ap.add_argument('--concurrency', type=int, default=2)
    ap.add_argument('--probes', default=None,
                    help='逗号分隔 probe_id 子集（重跑失败项）')
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    manifest = load(PROBES)
    probes = manifest['probes']
    if a.probes:
        keep = {x.strip() for x in a.probes.split(',') if x.strip()}
        probes = [p for p in probes if p['probe_id'] in keep]
        if len(probes) != len(keep):
            raise SystemExit('unknown probes: %s' % sorted(keep - {p['probe_id'] for p in probes}))
    if os.path.isdir(a.outdir) and os.listdir(a.outdir):
        raise SystemExit('outdir not empty: %s' % a.outdir)
    os.makedirs(a.outdir, exist_ok=True)
    budget = None
    if a.mode == 'live':
        ad = load(a.authorization if os.path.isabs(a.authorization)
                  else os.path.join(ROOT, a.authorization))
        from arena.isolation.authorization_budget import (
            AuthorizationBudget, validate_authorization_doc, BudgetRefused)
        try:
            validate_authorization_doc(ad, expected_endpoint=ENDPOINT,
                                       expected_model=MODEL,
                                       expected_temperature=TEMPERATURE)
        except BudgetRefused as e:
            raise SystemExit('AUTH REFUSED: %s' % e)
        os.environ['LB_API_BASE'] = ENDPOINT
        os.environ['LB_MODEL'] = MODEL
        os.environ.setdefault('LB_API_KEY', 'local')
        os.environ['LB_STREAM_TIMING'] = '0'
        aid = ad['authorization_id']
        cap = ad['real_endpoint']['max_attempts']
        budget = AuthorizationBudget(os.path.join(ROOT, ad['budget_ledger']),
                                     aid, cap, scope=ad['scope'])
        try:
            budget.ensure_batch(len(probes) * 2, purpose='f4_component_value')
        except BudgetRefused as e:
            raise SystemExit('BUDGET REFUSED: %s' % e)
    from arena.lb_gateway import ADAPTERS, ModelGateway
    tls = threading.local()
    lock = threading.Lock()

    def gw():
        g = getattr(tls, 'gw', None)
        if g is None:
            g = ModelGateway(ADAPTERS['openai_compat'](),
                             event_log_path=os.path.join(
                                 a.outdir, 'GATEWAY_EVENTS_%s.jsonl'
                                 % threading.current_thread().name))
            tls.gw = g
        return g

    def one(probe):
        r = run_on(probe, a.outdir, os.path.abspath(
            a.authorization if os.path.isabs(a.authorization)
            else os.path.join(ROOT, a.authorization)))
        if r['status'] != 'on_ok':
            return r
        on_prompt = r.pop('on_prompt')
        system = r.pop('system')
        off_prompt = on_prompt
        removed = []
        if probe['channel'] == 'knowledge':
            for mark in ('【相关已确认事实', '【暂定事项', '【最近状态变更'):
                off_prompt, rm = remove_block(off_prompt, mark)
                if rm:
                    removed.append({'mark': mark, 'chars': len(rm), 'sha256': sha(rm)})
        else:
            off_prompt, rm = remove_block(off_prompt, '【本轮可用动作')
            if rm:
                removed.append({'mark': '【本轮可用动作', 'chars': len(rm),
                                'sha256': sha(rm)})
        if not removed or off_prompt == on_prompt:
            r.update({'status': 'invalid_no_diff', 'removed_blocks': removed})
            return r
        attempt_id = 'f4:%s:off' % probe['probe_id']
        if budget is not None:
            try:
                budget.reserve(attempt_id, purpose='f4_component_value')
            except Exception as e:  # noqa: BLE001
                r.update({'status': 'budget_refused', 'error': str(e)})
                return r
        t0 = time.time()
        if a.mode == 'mock':
            raw_off, usage, err = r['on_raw'], None, None
        else:
            try:
                res = gw().generate(system=system, user=off_prompt, model=MODEL,
                                    temperature=TEMPERATURE, tag='f4_off',
                                    attempt_id=attempt_id, timeout_s=240)
                raw_off, usage, err = res.get('text') or '', res.get('usage'), None
            except Exception as e:  # noqa: BLE001
                raw_off, usage, err = '', None, '%s: %s' % (type(e).__name__, e)
        latency = int((time.time() - t0) * 1000)
        if budget is not None:
            try:
                if err is None:
                    budget.record('attempt_completed', attempt_id, usage=usage,
                                  raw_sha256=sha(raw_off))
                else:
                    budget.record('attempt_failed', attempt_id, error=err[:200])
            except Exception:
                pass
        ep = load(os.path.join(ROOT, probe['episode']))
        roles = roles_for(ep, probe['prefix_turns'])
        harness_off, meta, _ = guard_mod().repair_and_guard(raw_off, off_prompt, roles)
        off_answer = parse_answer(harness_off) if err is None else {
            'kind': 'format_fail', 'raw_excerpt': (err or '')[:300]}
        r.update({'status': 'ok' if err is None else 'failed', 'error': err,
                  'off_prompt': off_prompt, 'off_prompt_sha256': sha(off_prompt),
                  'on_prompt_sha256': sha(on_prompt),
                  'on_prompt_chars': len(on_prompt), 'off_prompt_chars': len(off_prompt),
                  'removed_blocks': removed,
                  'off_raw': raw_off, 'off_harness': harness_off,
                  'off_guard_meta': meta,
                  'off_answer': off_answer,
                  'usage_off': usage, 'latency_off': latency})
        with lock:
            with open(os.path.join(a.outdir, 'F4_RESULT.jsonl'), 'a',
                      encoding='utf-8') as f:
                f.write(json.dumps(r, ensure_ascii=False) + '\n')
        return r

    with concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency) as ex:
        results = list(ex.map(one, probes))
    doc = {'kind': 'f4_component_value', 'mode': a.mode,
           'run_base': a.run_base, 'picks': probes, 'results': results,
           'counts': {s: sum(1 for r in results if r.get('status') == s)
                      for s in set(r.get('status') for r in results)},
           'note': ('真实 checkpoint 恢复 + 同 prompt 反事实；on 走完整候选，'
                    'off 删声明的组件段后同一 dsf 生成并过同一 guard'),
           'finished_at': time.strftime('%Y-%m-%d %H:%M:%S')}
    dump(os.path.join(a.outdir, 'F4_RESULT.json'), doc)
    print(json.dumps({'counts': doc['counts']}, ensure_ascii=False))
    return 0 if doc['counts'].get('ok', 0) == len(probes) else 3


if __name__ == '__main__':
    sys.exit(main())
