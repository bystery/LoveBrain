# -*- coding: utf-8 -*-
r"""tools/run_f4_judge.py — F4 12 对组件价值盲评（hy3 / 0.0 / 非流式）。"""
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
sys.path.insert(0, os.path.join(ROOT, 'tools'))
from run_f1_blind_review import JUDGE_SYSTEM as F1_SYSTEM, parse_json, check_schema  # noqa: E402

OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')
AID = 'AUTH-F4-JUDGE-20261029-01'
MODEL = 'hy3'
TEMPERATURE = 0.0
ENDPOINT = 'http://127.0.0.1:17770/v1'
CAP = 12
MATERIALS = os.path.join(OUT, 'F4_MATERIALS.jsonl')
AUTH_PATH = os.path.join(OUT, 'F4_JUDGE_AUTHORIZATION_20261029.json')
ADDENDUM = """

## 组件消融补充（本批）
- A/B 是同一 harness 的两次生成：一次保留被测组件，一次关闭该组件；不要猜版本、不要猜成本。
- 只依据材料判断哪次更适合发送；两边都合理写 tie/uncertain。
- 若关闭组件导致事实缺失、答非所问、格式无法交付或明显更空泛，按九维如实扣分。
"""
SYSTEM = F1_SYSTEM + ADDENDUM


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def ensure_auth():
    if not os.path.exists(AUTH_PATH):
        doc = {'kind': 'authorization_registration', 'authorization_id': AID,
               'authorized': True, 'status': 'ACTIVE',
               'granted_by': '用户2026-10-29持续实验授权；F4 组件盲评有限目的登记，不新增总帽、不复活旧单。',
               'phase': 'F4-JUDGE', 'budget_ledger': 'logs/auth_budget/%s.jsonl' % AID,
               'real_endpoint': {'api_base': ENDPOINT, 'model': MODEL,
                                 'temperature': TEMPERATURE, 'max_attempts': CAP},
               'api_config': 'api_cfg:openai_compat_stream_false_v1；hy3 单一裁判；非流式。',
               'scope': {'purpose': 'F4 12 对组件价值盲评（每对 1 次）。',
                         'stage': 'F4', 'experiment': 'F4_component_value_blind_review',
                         'allowed_models': [MODEL],
                         'forbidden_models': ['hy4-preview', 'gemini-3.8-flash',
                                              'qwen3.8-flash', 'dsf'],
                         'endpoint': ENDPOINT, 'total_attempts': CAP,
                         'fail_fast': True, 'no_retry': True, 'no_makeup': True,
                         'closed_history_not_reused': True},
               'registered_at': '2026-10-29T10:50:00'}
        with open(AUTH_PATH, 'w', encoding='utf-8') as f:
            json.dump(doc, f, ensure_ascii=False, indent=1)
            f.write('\n')
    return load(AUTH_PATH)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', default='live', choices=('live', 'mock'))
    ap.add_argument('--outdir', default=None)
    ap.add_argument('--concurrency', type=int, default=2)
    ap.add_argument('--timeout-s', type=int, default=300)
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    items = [json.loads(l) for l in open(MATERIALS, encoding='utf-8') if l.strip()]
    if len(items) != 12:
        raise SystemExit('expected 12 items, got %d' % len(items))
    outdir = a.outdir or os.path.join(ROOT, 'logs', 'f4_judge_%s'
                                      % time.strftime('%Y%m%d_%H%M%S'))
    os.makedirs(outdir, exist_ok=True)
    ratings = os.path.join(outdir, 'F4_RATINGS.jsonl')
    budget = None
    if a.mode == 'live':
        ad = ensure_auth()
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
        budget = AuthorizationBudget(os.path.join(ROOT, ad['budget_ledger']),
                                     AID, CAP, scope=ad['scope'])
        try:
            budget.ensure_batch(len(items), purpose='f4_component_judge')
        except BudgetRefused as e:
            raise SystemExit('BUDGET REFUSED: %s' % e)
    from arena.lb_gateway import ADAPTERS, ModelGateway
    tls = threading.local()
    lock = threading.Lock()
    stop = [None]

    def gw():
        g = getattr(tls, 'gw', None)
        if g is None:
            g = ModelGateway(ADAPTERS['openai_compat'](),
                             event_log_path=os.path.join(
                                 outdir, 'GATEWAY_EVENTS_%s.jsonl'
                                 % threading.current_thread().name))
            tls.gw = g
        return g

    def call(i, item):
        uid = item['unit_id']
        attempt_id = 'judge:F4:%s:%d' % (uid, i)
        if budget is not None:
            budget.reserve(attempt_id, purpose='f4_component_judge')
        user = json.dumps(item['payload'], ensure_ascii=False)
        t0 = time.time()
        if a.mode == 'mock':
            scores = {k: 2 for k in ('goal', 'factuality', 'attribution',
                                     'time_state', 'boundary', 'memory_use',
                                     'interaction', 'initiative',
                                     'emotion_relation')}
            raw = json.dumps({'unit_id': uid,
                              'A': {'scores': scores, 'na_reasons': {}, 'claims': [],
                                    'action_fit': 'acceptable', 'major_errors': []},
                              'B': {'scores': scores, 'na_reasons': {}, 'claims': [],
                                    'action_fit': 'acceptable', 'major_errors': []},
                              'verdict': 'tie', 'card_conflict': False,
                              'conflict_note': '', 'uncertainties': ['stub']},
                             ensure_ascii=False)
            usage, err = None, None
        else:
            try:
                res = gw().generate(system=SYSTEM, user=user, model=MODEL,
                                    temperature=TEMPERATURE, tag='f4_judge',
                                    attempt_id=attempt_id, timeout_s=a.timeout_s)
                raw, usage, err = res.get('text') or '', res.get('usage'), None
            except Exception as e:  # noqa: BLE001
                raw, usage, err = '', None, '%s: %s' % (type(e).__name__, e)
        parsed, perr = parse_json(raw)
        schema_ok, why = (False, 'parse failed') if parsed is None \
            else check_schema(parsed, uid)
        rec = {'unit_id': uid, 'attempt_id': attempt_id,
               'transport_ok': err is None, 'error': err,
               'parse_ok': parsed is not None, 'parse_error': perr,
               'schema_ok': schema_ok, 'schema_why': why,
               'raw': raw, 'parsed': parsed, 'usage': usage,
               'latency_ms': int((time.time() - t0) * 1000), 'mode': a.mode,
               'system_sha256': sha(SYSTEM), 'user_sha256': sha(user)}
        if budget is not None:
            try:
                if err is None:
                    budget.record('attempt_completed', attempt_id, usage=usage,
                                  raw_sha256=sha(raw))
                else:
                    budget.record('attempt_failed', attempt_id, error=err[:200])
            except Exception:
                pass
        return rec

    records = []
    if items:
        rec = call(0, items[0])
        records.append(rec)
        with open(ratings, 'a', encoding='utf-8') as f:
            f.write(json.dumps(rec, ensure_ascii=False) + '\n')
        if not rec['transport_ok']:
            stop[0] = 'transport_fail_warmup'
    if not stop[0] and len(items) > 1:
        def run(x):
            i, item = x
            rec = call(i, item)
            with lock:
                with open(ratings, 'a', encoding='utf-8') as f:
                    f.write(json.dumps(rec, ensure_ascii=False) + '\n')
                records.append(rec)
                if not rec['transport_ok']:
                    stop[0] = 'transport_fail'
            return rec
        with concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency) as ex:
            list(ex.map(run, list(enumerate(items[1:], start=1))))
    summary = {'outdir': os.path.relpath(outdir, ROOT).replace('\\', '/'),
               'done': len(records),
               'schema_ok': sum(1 for r in records if r['schema_ok']),
               'transport_ok': sum(1 for r in records if r['transport_ok']),
               'stopped': stop[0], 'system_sha256': sha(SYSTEM)}
    with open(os.path.join(outdir, 'F4_JUDGE_SUMMARY.json'), 'w',
              encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print(json.dumps(summary, ensure_ascii=False))
    return 0 if not stop[0] else 2


if __name__ == '__main__':
    sys.exit(main())
