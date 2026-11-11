# -*- coding: utf-8 -*-
"""tools/run_a3_judge.py — A3 小校准派发器（172 请求：96主评+16同序复评+16真换序+44电池）。

纪律：--mode mock 先绿再 live；fail-fast（任一传输失败停止提交新任务，不重试不补跑）；
live 用 AUTH-A3-JUDGE-20261026-01（cap 172）；mock 用演练账本零真实调用；
提交顺序=DISPATCH_PLAN_A3.json 冻结顺序（构造期已固定 seed 洗牌）；并发默认 2（授权声明）。
续跑：--resume-outdir 跳过已落盘 attempt（含失败=永不补跑）。
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
import sys
import threading
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, 'arena'))
PLAN_DIR = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'a3_calibration_20261026')
AID = 'AUTH-A3-JUDGE-20261026-01'
MODEL_ALIAS = 'dsf'
TEMPERATURE = 0.7
PINNED_ENDPOINT = 'http://127.0.0.1:17770/v1'


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def stub(entry):
    if entry['kind'].startswith('battery'):
        if entry['kind'] == 'battery_fact':
            return json.dumps({'item': entry['node_ref'], 'verdict': 'unfounded',
                               'reason': 'MOCK-STUB'}, ensure_ascii=False)
        return json.dumps({'item': entry['node_ref'], 'protected': True,
                           'reason': 'MOCK-STUB'}, ensure_ascii=False)
    return json.dumps({
        'node': entry['node_id'],
        'answers': {'甲': {'action_score': 2, 'fact_risk_flags': [], 'protected_hits': []},
                    '乙': {'action_score': 2, 'fact_risk_flags': [], 'protected_hits': []}},
        'pref': 'tie', 'pref_confidence': 'low', 'order_reason_check': True, 'not_rated': [],
    }, ensure_ascii=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', default='mock', choices=('mock', 'live'))
    ap.add_argument('--limit', type=int, default=None)
    ap.add_argument('--concurrency', type=int, default=2)
    ap.add_argument('--timeout-s', type=int, default=240)
    ap.add_argument('--tag', default=None)
    ap.add_argument('--resume-outdir', default=None)
    ap.add_argument('--inject-fail-at', type=int, default=None, help='仅 mock：第 N 个注入传输失败')
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:                                     # noqa: BLE001
        pass

    plan = json.load(open(os.path.join(PLAN_DIR, 'DISPATCH_PLAN_A3.json'), encoding='utf-8'))
    req_path = os.path.join(PLAN_DIR, 'REQUESTS.jsonl')
    got = hashlib.sha256(open(req_path, 'rb').read()).hexdigest()
    assert got == plan['requests_sha256'], 'REQUESTS.jsonl 与冻结计划不匹配，拒绝启动'
    entries = [json.loads(l) for l in open(req_path, encoding='utf-8') if l.strip()]
    assert len(entries) == plan['requests_n'] == 172
    tag = a.tag or time.strftime('%H%M%S')

    if a.resume_outdir:
        outdir = a.resume_outdir
        assert os.path.isdir(outdir)
        done = set()
        rp = os.path.join(outdir, 'RATINGS_A3.jsonl')
        if os.path.exists(rp):
            done = {json.loads(l)['req_id']
                    for l in open(rp, encoding='utf-8') if l.strip()}
        # 首轮冻结派发范围 = 全计划；续跑只跳过已落盘（含失败项永不补跑）
    else:
        outdir = os.path.join(ROOT, 'logs',
                              'a3_judge_%s_%s' % ('live' if a.mode == 'live' else 'mock', tag))
        os.makedirs(outdir, exist_ok=True)
        done = set()

    from arena.isolation.authorization_budget import AuthorizationBudget, BudgetRefused
    if a.mode == 'live':
        os.environ['LB_API_BASE'] = PINNED_ENDPOINT
        os.environ['LB_MODEL'] = MODEL_ALIAS
        os.environ.setdefault('LB_API_KEY', 'local')
        led = os.path.join(ROOT, 'logs', 'auth_budget', AID + '.jsonl')
        auth = json.load(open(os.path.join(PLAN_DIR, 'AUTHORIZATION.json'), encoding='utf-8'))
        budget = AuthorizationBudget(led, authorization_id=AID,
                                     scope=auth['scope'], max_attempts=auth['max_attempts'])
    else:
        rid = '%s-REHEARSAL-%s' % (AID, tag)
        led = os.path.join(ROOT, 'logs', 'auth_budget_rehearsal', rid + '.jsonl')
        os.makedirs(os.path.dirname(led), exist_ok=True)
        budget = AuthorizationBudget(led, rid, 172, scope={'a3_rehearsal': True, 'tag': tag})
    assert a.mode != 'live' or budget.max_attempts == 172, 'live 上限必须等于授权 172'

    todo = [(i, e) for i, e in enumerate(entries) if e['req_id'] not in done]
    if a.limit is not None:
        todo = todo[:a.limit]
    print('A3 JUDGE mode=%s todo=%d done_skip=%d ledger=%s' % (a.mode, len(todo), len(done), led))

    io_lock = threading.Lock()
    tls = threading.local()
    records = []
    stopped = [None]

    def worker_gateway():
        if a.mode != 'live':
            return None
        gw = getattr(tls, 'gw', None)
        if gw is None:
            from arena.lb_gateway import ADAPTERS, ModelGateway
            gw = ModelGateway(ADAPTERS['openai_compat'](),
                              event_log_path=os.path.join(outdir, 'GATEWAY_EVENTS_w%s.jsonl'
                                                          % threading.current_thread().name))
            gw.set_run_context('a3_judge_%s' % tag, 'a3_calibration')
            tls.gw = gw
        return gw

    def dispatch_one(i, entry):
        attempt_id = entry['req_id']
        try:
            with io_lock:
                budget.reserve(attempt_id, purpose='a3_%s' % entry['kind'])
        except BudgetRefused as exc:
            return None, {'reason': 'budget_refused', 'at': i, 'detail': str(exc)}
        t0 = time.time()
        usage, timing, err = None, None, None
        if a.mode == 'mock':
            if a.inject_fail_at is not None and i == a.inject_fail_at:
                raw, ok = 'INJECTED_TRANSPORT_FAIL', False
                err = 'injected (rehearsal fail-fast)'
            else:
                raw, ok = stub(entry), True
        else:
            try:
                res = worker_gateway().generate(system=entry['system'], user=entry['user'],
                                                model=MODEL_ALIAS, temperature=TEMPERATURE,
                                                tag='a3_%s' % entry['kind'], attempt_id=attempt_id,
                                                timeout_s=a.timeout_s)
                raw, ok = res['text'], True
                usage = res.get('usage')
                timing = {'non_stream': True, 'gateway_timing': res.get('timing')}
            except Exception as exc:                     # noqa: BLE001
                raw, ok = '%s: %s' % (type(exc).__name__, exc), False
                err = raw
        parsed, perr = None, None
        if ok:
            try:
                parsed = json.loads(raw)
            except Exception as exc:                     # noqa: BLE001
                perr = '%s' % exc
        rec = {'seq': i, 'req_id': attempt_id, 'kind': entry['kind'], 'session': entry['session'],
               'node_id': entry['node_id'], 'swapped': entry['swapped'],
               'authorization_id': budget.authorization_id,
               'user_sha256': sha(entry['user']), 'raw_sha256': sha(raw),
               'transport_ok': ok, 'parse_ok': parsed is not None, 'parse_error': perr,
               'wall_ms': int((time.time() - t0) * 1000), 'usage': usage, 'timing': timing,
               'error': err, 'raw': raw}
        if parsed is not None:
            rec['parsed'] = parsed
            rec['pref'] = parsed.get('pref')
        try:
            with io_lock:
                if ok:
                    budget.record('attempt_completed', attempt_id, usage=usage,
                                  raw_sha256=rec['raw_sha256'])
                else:
                    budget.record('attempt_failed', attempt_id, error=(err or raw)[:200])
                with open(os.path.join(outdir, 'RATINGS_A3.jsonl'), 'a', encoding='utf-8') as f:
                    f.write(json.dumps(rec, ensure_ascii=False) + '\n')
        except OSError as exc:
            return rec, {'reason': 'ledger_write_fail', 'at': i, 'detail': str(exc)}
        if not ok:
            return rec, {'reason': 'transport_fail_fast', 'at': i, 'attempt_id': attempt_id,
                         'detail': (err or raw)[:200]}
        return rec, None

    if a.concurrency <= 1:
        for i, e in todo:
            rec, stop = dispatch_one(i, e)
            if rec:
                records.append(rec)
            if stop:
                stopped[0] = stop
                break
    else:
        # 渐进提交（冻结顺序不变）：池里最多 concurrency 个在跑，每完成一个再提交下一个；
        # 任一失败 → 停止提交新任务，已在跑的跑完（fail-fast 语义与 204 批一致）
        with concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency) as ex:
            pending = {}
            it = iter(todo)
            for _ in range(a.concurrency):
                nxt = next(it, None)
                if nxt is None or stopped[0]:
                    break
                i, e = nxt
                pending[ex.submit(dispatch_one, i, e)] = i
            while pending:
                done_fut, *_ = concurrent.futures.wait(
                    pending, return_when=concurrent.futures.FIRST_COMPLETED)
                for fut in done_fut:
                    i = pending.pop(fut)
                    rec, stop = fut.result()
                    if rec:
                        records.append(rec)
                    if stop and not stopped[0]:
                        stopped[0] = stop
                if not stopped[0]:
                    nxt = next(it, None)
                    if nxt is not None:
                        i, e = nxt
                        pending[ex.submit(dispatch_one, i, e)] = i

    s = budget.summary()
    summary = {'mode': a.mode, 'tag': tag, 'outdir': outdir,
               'planned': len(todo), 'records': len(records),
               'stopped': stopped[0], 'ledger': s,
               'endpoint': PINNED_ENDPOINT if a.mode == 'live' else None,
               'model': MODEL_ALIAS, 'temperature': TEMPERATURE,
               'system_sha256': plan['system_sha256'], 'requests_sha256': plan['requests_sha256'],
               'real_calls': s['used'] if a.mode == 'live' else 0}
    json.dump(summary, open(os.path.join(outdir, 'SUMMARY.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('A3 done:', json.dumps(summary, ensure_ascii=False)[:400])


def _unused_rid_led(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


if __name__ == '__main__':
    main()
