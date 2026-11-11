# -*- coding: utf-8 -*-
r"""tools/run_f3_judge.py — F3 新材料 72 对盲评（hy3 / 0.0 / 非流式）。

复用 F1 的裁判九维、schema 校验与预算纪律；仅材料与新增长期记忆纪律不同。
一物理请求一 attempt；失败消耗不退款、不重试；resume 跳过已落盘 unit。
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
sys.path.insert(0, os.path.join(ROOT, 'tools'))

from run_f1_blind_review import JUDGE_SYSTEM as F1_SYSTEM, parse_json, check_schema  # noqa: E402

OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')
AID = 'AUTH-F3-JUDGE-20261029-01'
MODEL = 'hy3'
TEMPERATURE = 0.0
ENDPOINT = 'http://127.0.0.1:17770/v1'
CAP = 72
AUTH_PATH = os.path.join(OUT, 'F3_JUDGE_AUTHORIZATION_20261029.json')
MATERIALS = os.path.join(OUT, 'F3_MATERIALS.jsonl')
BINDINGS = os.path.join(OUT, 'F3_PRIVATE_BINDINGS.json')

F3_ADDENDUM = """

## 长期故事补充（本批新材料）
- 输入含完整前史。早期事实若出现在 prior_history，就属于可核证据；答错、张冠李戴、把已更正事实当旧值，按 factuality/attribution/time_state 记 major。
- 当前轮若不需要旧事实，不因“没提旧事”扣分；需要时能自然、正确地用上才是 memory_use 的加分。
- turn_index>12 的答卷来自真实 checkpoint 恢复后的新进程；只评答卷，不推断实现。
- 合法不回：may_decline=true 且卡面 current_action 允许时，no_reply 可以是最优；窗口最后一条是 ME 时不强制回复。
"""
JUDGE_SYSTEM = F1_SYSTEM + F3_ADDENDUM


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def ensure_auth():
    if not os.path.exists(AUTH_PATH):
        doc = {
            'kind': 'authorization_registration',
            'authorization_id': AID,
            'authorized': True,
            'status': 'ACTIVE',
            'granted_by': '用户2026-10-29持续实验授权；F3 新材料盲评有限目的登记，不新增总帽、不复活旧单。',
            'phase': 'F3-JUDGE',
            'budget_ledger': 'logs/auth_budget/%s.jsonl' % AID,
            'real_endpoint': {'api_base': ENDPOINT, 'model': MODEL,
                              'temperature': TEMPERATURE, 'max_attempts': CAP},
            'api_config': 'api_cfg:openai_compat_stream_false_v1；hy3 单一裁判，整批同模型；非流式。',
            'scope': {
                'purpose': 'F3 新材料 72 对盲评（每对 1 次）；只审 F3 答卷，不生成新答卷。',
                'stage': 'F3', 'experiment': 'F3_new_material_blind_review',
                'allowed_models': [MODEL],
                'forbidden_models': ['hy4-preview', 'gemini-3.8-flash',
                                     'qwen3.8-flash', 'dsf'],
                'endpoint': ENDPOINT, 'total_attempts': CAP,
                'fail_fast': True, 'no_retry': True, 'no_makeup': True,
                'closed_history_not_reused': True,
            },
            'registered_at': '2026-10-29T10:10:00',
        }
        with open(AUTH_PATH, 'w', encoding='utf-8') as f:
            json.dump(doc, f, ensure_ascii=False, indent=1)
            f.write('\n')
    return load(AUTH_PATH)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', default='live', choices=('live', 'mock'))
    ap.add_argument('--limit', type=int, default=None)
    ap.add_argument('--concurrency', type=int, default=3)
    ap.add_argument('--resume', default=None)
    ap.add_argument('--timeout-s', type=int, default=300)
    ap.add_argument('--outdir', default=None)
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    items = [json.loads(l) for l in open(MATERIALS, encoding='utf-8') if l.strip()]
    if len(items) != 72:
        raise SystemExit('expected 72 materials, got %d' % len(items))
    if a.limit:
        items = items[:a.limit]
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
        outdir = a.resume or a.outdir or os.path.join(
            ROOT, 'logs', 'f3_judge_%s' % time.strftime('%Y%m%d_%H%M%S'))
        os.makedirs(outdir, exist_ok=True)
        ratings = os.path.join(outdir, 'F3_RATINGS.jsonl')
        done = set()
        if a.resume and os.path.exists(ratings):
            for l in open(ratings, encoding='utf-8'):
                if l.strip():
                    done.add(json.loads(l)['unit_id'])
        plan = [x for x in items if x['unit_id'] not in done]
        budget = AuthorizationBudget(os.path.join(ROOT, ad['budget_ledger']),
                                     AID, CAP, scope=ad['scope'])
        try:
            budget.ensure_batch(len(plan), purpose='f3_blind_review')
        except BudgetRefused as e:
            raise SystemExit('BUDGET REFUSED: %s' % e)
    else:
        outdir = a.outdir or os.path.join(
            ROOT, 'logs', 'f3_judge_mock_%s' % time.strftime('%Y%m%d_%H%M%S'))
        os.makedirs(outdir, exist_ok=True)
        ratings = os.path.join(outdir, 'F3_RATINGS.jsonl')
        plan = items

    from arena.lb_gateway import ADAPTERS, ModelGateway
    io_lock = threading.Lock()
    tls = threading.local()
    records = []
    stop = [None]

    def gateway():
        gw = getattr(tls, 'gw', None)
        if gw is None:
            gw = ModelGateway(ADAPTERS['openai_compat'](),
                              event_log_path=os.path.join(
                                  outdir, 'GATEWAY_EVENTS_%s.jsonl'
                                  % threading.current_thread().name))
            tls.gw = gw
        return gw

    def stub(item):
        from run_f1_blind_review import JUDGE_SYSTEM as _js  # noqa: F401
        scores = {k: 2 for k in ('goal', 'factuality', 'attribution',
                                 'time_state', 'boundary', 'memory_use',
                                 'interaction', 'initiative',
                                 'emotion_relation')}
        return {'unit_id': item['unit_id'],
                'A': {'scores': scores, 'na_reasons': {}, 'claims': [],
                      'action_fit': 'acceptable', 'major_errors': []},
                'B': {'scores': scores, 'na_reasons': {}, 'claims': [],
                      'action_fit': 'acceptable', 'major_errors': []},
                'verdict': 'tie', 'card_conflict': False,
                'conflict_note': '', 'uncertainties': ['stub']}

    def call_one(i, item):
        uid = item['unit_id']
        attempt_id = 'judge:F3:%s:%d' % (uid, i)
        if budget is not None:
            try:
                budget.reserve(attempt_id, purpose='f3_blind_review')
            except Exception as e:  # noqa: BLE001
                return {'unit_id': uid, 'attempt_id': attempt_id,
                        'transport_ok': False, 'error': 'budget: %s' % e,
                        'raw': ''}, 'budget'
        user = json.dumps(item['payload'], ensure_ascii=False)
        t0 = time.time()
        raw, err, usage = '', None, None
        if a.mode == 'mock':
            raw = json.dumps(stub(item), ensure_ascii=False)
        else:
            try:
                gw = gateway()
                gw.set_run_context('f3_judge', uid)
                res = gw.generate(system=JUDGE_SYSTEM, user=user, model=MODEL,
                                  temperature=TEMPERATURE, tag='f3_judge',
                                  attempt_id=attempt_id, timeout_s=a.timeout_s)
                raw = res.get('text') or ''
                usage = res.get('usage')
            except Exception as e:  # noqa: BLE001
                err = '%s: %s' % (type(e).__name__, e)
                raw = err
        latency = int((time.time() - t0) * 1000)
        parsed, perr = parse_json(raw)
        schema_ok, schema_why = (False, 'parse failed') if parsed is None \
            else check_schema(parsed, uid)
        rec = {'unit_id': uid, 'kind': item['kind'], 'attempt_id': attempt_id,
               'transport_ok': err is None, 'error': err,
               'parse_ok': parsed is not None, 'parse_error': perr,
               'schema_ok': schema_ok, 'schema_why': schema_why,
               'raw': raw, 'parsed': parsed, 'usage': usage,
               'latency_ms': latency, 'mode': a.mode,
               'system_sha256': sha(JUDGE_SYSTEM), 'user_sha256': sha(user)}
        if budget is not None:
            try:
                if err is None:
                    budget.record('attempt_completed', attempt_id, usage=usage,
                                  raw_sha256=sha(raw))
                else:
                    budget.record('attempt_failed', attempt_id, error=err[:200])
            except Exception:
                pass
        return rec, None

    # 第一项顺序作为网关/协议热身；失败即停（不把 72 次全打出去才发现断线）
    if plan and stop[0] is None:
        rec, s = call_one(0, plan[0])
        records.append(rec)
        with open(ratings, 'a', encoding='utf-8') as f:
            f.write(json.dumps(rec, ensure_ascii=False) + '\n')
        if s or not rec['transport_ok']:
            stop[0] = s or 'transport_fail_warmup'
        plan = plan[1:]

    if stop[0] is None and a.concurrency <= 1:
        for i, item in enumerate(plan, start=1):
            rec, s = call_one(i, item)
            records.append(rec)
            with open(ratings, 'a', encoding='utf-8') as f:
                f.write(json.dumps(rec, ensure_ascii=False) + '\n')
            if s or not rec['transport_ok']:
                stop[0] = s or 'transport_fail'
                break
    elif stop[0] is None:
        def run(i_item):
            i, item = i_item
            rec, s = call_one(i, item)
            with io_lock:
                with open(ratings, 'a', encoding='utf-8') as f:
                    f.write(json.dumps(rec, ensure_ascii=False) + '\n')
                records.append(rec)
                if not rec['transport_ok']:
                    stop[0] = s or 'transport_fail'
            return rec
        with concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency) as ex:
            list(ex.map(run, list(enumerate(plan, start=1))))

    summary = {
        'outdir': os.path.relpath(outdir, ROOT).replace('\\', '/'),
        'planned': len(items), 'done_now': len(records),
        'schema_ok': sum(1 for r in records if r.get('schema_ok')),
        'transport_ok': sum(1 for r in records if r.get('transport_ok')),
        'stopped': stop[0],
        'system_sha256': sha(JUDGE_SYSTEM),
    }
    with open(os.path.join(outdir, 'F3_JUDGE_SUMMARY.json'), 'w',
              encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print(json.dumps(summary, ensure_ascii=False))
    return 0 if not stop[0] else 2


if __name__ == '__main__':
    sys.exit(main())
