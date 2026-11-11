# -*- coding: utf-8 -*-
r"""tools/run_quality_panel_v1.py — 质量面板评审批（AUTH-QUALITY-PANEL-20261027-01，cap 192，计划 173）。

复用 tools/run_judge_batch_v1_1.py 的调度骨架（ModelGateway + AuthorizationBudget + 失败停批 +
逐调用落盘），只将题包/schema/scope/计划参数化（WORKER_NEXT_MAINLINE §B1：不重建调度平台）。
- 派发前门禁：QUALITY_PLAN.json 在且输入散列全部吻合（冻结后输入变化即拒）；
- live 需要 LB_ALLOW_QUALITY_PANEL_LIVE=1；评审批冻结非流式身份（LB_STREAM_TIMING 开启即拒）；
- attempt_id='judge:QP:{item_id}:{kind}:{seq}'，全局唯一；失败消耗不退款、停批不补跑；
- 语义/schema 不合法 → 留原文标 schema_ok=False 离线未评，**不重发求满意判词**；
- mock 用演练账本 logs/auth_budget_rehearsal/，绝不占用真实单。

运行：
  set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\run_quality_panel_v1.py --mode mock
  set LB_ALLOW_QUALITY_PANEL_LIVE=1 && D:\anaconda\python.exe tools\run_quality_panel_v1.py --mode live
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

PK = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                  'knowledge_quality_independent_review_20261026')
AID = 'AUTH-QUALITY-PANEL-20261027-01'
CAP = 192
MODEL_ALIAS = 'dsf'
TEMPERATURE = 0.7
PINNED_ENDPOINT = 'http://127.0.0.1:17770/v1'
DIM_KEYS = ['goal', 'factuality', 'attribution', 'time_state', 'boundary',
            'memory_use', 'interaction', 'initiative', 'emotion_relation']

JUDGE_SYSTEM = """你是聊天回复评审者。按所附《QUALITY_MANUAL_V1》对给出的每一条回复（首选与每条非空备选）独立打九维分并逐条标注可核主张。不要猜测答案来自哪个版本，不猜测成本，不预测用户是否愿意发送。
硬性要求：
1) scores 九键齐全（goal/factuality/attribution/time_state/boundary/memory_use/interaction/initiative/emotion_relation），值 0-3 或 null；null 必须同时在 na_reasons 给理由。
2) 输入 backups 里每条非空备选都必须在输出 backups 数组出现恰好一次，slot 名逐字照抄；没有备选就输出空数组。
3) claims 的 quote 与 evidence_quote 必须逐字摘自输入材料；自己找不到证据就写进 uncertainties，不得因此给考生判 0 或编造证据。
4) 受保护表达（调侃/玩笑/新提议/条件句/承认未知/观点表达）不因"没核实/没澄清"判失败；只有把无据细节说成已核实才算事实错误。允许多种正确表达，不要求唯一话术。
5) 严格输出 JSON：无解释文字、无 markdown 代码块。"""


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def load_frozen_inputs():
    plan_doc = json.load(open(os.path.join(PK, 'QUALITY_PLAN.json'), encoding='utf-8'))
    for fname, exp in plan_doc['input_hashes_sha256'].items():
        got = sha256_file(os.path.join(PK, fname))
        if got != exp:
            raise SystemExit('拒绝启动：冻结输入已变化 %s（期望 %s… 实际 %s…）——旧计划只能对旧身份运行'
                             % (fname, exp[:16], got[:16]))
    nodes = [json.loads(l) for l in open(os.path.join(PK, 'QUALITY_NODES.jsonl'), encoding='utf-8')]
    controls = json.load(open(os.path.join(PK, 'QUALITY_CONTROLS_DEV_V1.json'), encoding='utf-8'))['controls']
    cards = json.load(open(os.path.join(PK, 'QUALITY_REFERENCE_CARDS_V1.json'), encoding='utf-8'))['cards']
    bindings = json.load(open(os.path.join(PK, 'PRIVATE_BINDINGS.json'), encoding='utf-8'))
    return plan_doc, nodes, controls, cards, bindings


_CARD_R2_CACHE = None


def _card_r2():
    global _CARD_R2_CACHE
    if _CARD_R2_CACHE is None:
        path = os.path.join(ROOT, 'benchmark', 'c4_screen_20261026',
                            'GRADING_CARDS_C4_E006_E007_E008_E012_R2.json')
        _CARD_R2_CACHE = {x['case_id']: x for x in json.load(open(path, encoding='utf-8'))}
    return _CARD_R2_CACHE


def judge_payload(item, nodes_by_id, controls_by_id, card_by_case, bind_map):
    """构造判词输入（无臂名/无成本/无未来剧情）。"""
    if item['kind'] == 'control':
        c = controls_by_id[item['item_id']]
        body = {'evidence': c['context'],
                'frozen_card': None,
                'reference_card': None,
                'answer': {'primary': c['answer'], 'backups': []}}
    else:
        n = nodes_by_id[item['item_id']]
        case_id = bind_map[n['node_id']]
        raw_card = card_by_case.get(case_id)
        frozen = _card_r2().get(case_id)
        frozen_card = None
        if frozen:
            frozen_card = {'case_id': frozen['case_id'],
                           'families': frozen['families'],
                           'allowed_behaviors': frozen['allowed_behaviors'],
                           'failure_conditions': frozen['failure_conditions'],
                           'review_guidance': frozen.get('review_guidance'),
                           'unresolved_ambiguities': frozen.get('unresolved_ambiguities')}
        backups = [{'slot': b['slot'], 'text': b['text']}
                   for b in n['answer']['backups'] if b.get('text')]
        body = {'evidence': {'persona': n['evidence']['persona'],
                             'initial_kb': n['evidence']['initial_kb'],
                             'real_history': n['evidence']['real_history'],
                             'current_input': n['evidence']['current_input']},
                'frozen_card': frozen_card,
                'reference_card': raw_card,
                'answer': {'primary': n['answer']['primary'], 'backups': backups}}
    return json.dumps(body, ensure_ascii=False)


def check_schema(parsed, item, nodes_by_id, controls_by_id):
    """软校验：结构不合法 → schema_ok=False（离线未评），不是传输失败。"""
    why = []
    if not isinstance(parsed, dict):
        return False, 'not a dict'
    prim = parsed.get('primary')
    if not isinstance(prim, dict):
        return False, 'missing primary'
    sc = prim.get('scores')
    if not isinstance(sc, dict) or set(sc.keys()) != set(DIM_KEYS):
        why.append('primary.scores 九键不符: %s' % sorted((sc or {}).keys()))
    else:
        for k, v in sc.items():
            if v is not None and v not in (0, 1, 2, 3):
                why.append('primary.scores[%s]=%r 越界' % (k, v))
    n_exp = 0
    if item['kind'] == 'control':
        if parsed.get('backups') != []:
            why.append('控制样本 backups 应为空数组')
    else:
        n = nodes_by_id[item['item_id']]
        exp_slots = [b['slot'] for b in n['answer']['backups'] if b.get('text')]
        n_exp = len(exp_slots)
        got = parsed.get('backups')
        if not isinstance(got, list):
            why.append('backups 不是数组')
        else:
            got_slots = [b.get('slot') for b in got if isinstance(b, dict)]
            if sorted(got_slots) != sorted(exp_slots):
                why.append('backups slot 覆盖不符（期望 %d 条，实得 %d 条）' % (n_exp, len(got_slots)))
            for b in got:
                if not isinstance(b, dict):
                    continue
                bsc = b.get('scores')
                if not isinstance(bsc, dict) or set(bsc.keys()) != set(DIM_KEYS):
                    why.append('backup[%s].scores 九键不符' % b.get('slot'))
    if parsed.get('node_id') != item['item_id']:
        why.append('node_id=%r ≠ %r' % (parsed.get('node_id'), item['item_id']))
    return (not why), '; '.join(why) or 'ok'


def stub_response(item, seq, exp_slots):
    """mock 桩判词：schema 完整（按真实 slot 清单回填），内容不核对（只测接线/停批/落盘）。"""
    prim = {'scores': {k: (seq % 4) for k in DIM_KEYS}, 'na_reasons': {},
            'claims': [], 'action_basis': 'stub'}
    backups = [{'slot': s, 'scores': {k: 2 for k in DIM_KEYS},
                'na_reasons': {}, 'claims': []} for s in exp_slots]
    return json.dumps({'node_id': item['item_id'], 'primary': prim,
                       'backups': backups, 'uncertainties': []}, ensure_ascii=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', default='mock', choices=('mock', 'live'))
    ap.add_argument('--limit', type=int, default=None)
    ap.add_argument('--cap', type=int, default=None, help='仅 mock 生效；live 强制 192')
    ap.add_argument('--tag', default=None)
    ap.add_argument('--resume-outdir', default=None,
                    help='续跑：复用输出目录，已落盘/已失败/在跑孤儿不补跑')
    ap.add_argument('--timeout-s', type=int, default=240)
    ap.add_argument('--concurrency', type=int, default=2)
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

    plan_doc, nodes, controls, cards, bindings = load_frozen_inputs()
    nodes_by_id = {n['node_id']: n for n in nodes}
    controls_by_id = {c['control_id']: c for c in controls}
    card_by_case = {c['case_id']: c for c in cards}
    bind_map = {b['node_id']: b['case_id'] for b in bindings}

    plan = plan_doc['dispatch_order']
    if len(plan) != plan_doc['planned_attempts'] or len(plan) != 173:
        raise SystemExit('拒绝启动：计划 %d ≠ 173' % len(plan))
    if a.limit is not None:
        if a.limit <= 0:
            raise SystemExit('--limit 必须为正')
        if a.resume_outdir:
            raise SystemExit('--limit 不能与 --resume-outdir 组合')
        plan = plan[:a.limit]
    cap = CAP if a.mode == 'live' else (a.cap or CAP)
    if len(plan) > cap:
        raise SystemExit('拒绝启动：计划 %d > 上限 %d' % (len(plan), cap))
    if a.mode == 'live':
        if os.environ.get('LB_ALLOW_QUALITY_PANEL_LIVE', '') != '1':
            raise SystemExit('拒绝启动：live 需要 LB_ALLOW_QUALITY_PANEL_LIVE=1')
        if os.environ.get('LB_STREAM_TIMING', '0').strip().lower() in ('1', 'true', 'yes', 'on'):
            raise SystemExit('拒绝启动：评审批冻结非流式身份，不得开启 LB_STREAM_TIMING')
        os.environ['LB_API_BASE'] = PINNED_ENDPOINT
        os.environ['LB_MODEL'] = MODEL_ALIAS
        os.environ.setdefault('LB_API_KEY', 'local')

    auth_doc = json.load(open(os.path.join(PK, 'AUTHORIZATION_QUALITY_PANEL.json'), encoding='utf-8'))
    scope = auth_doc['scope']
    ledger_path = os.path.join(ROOT, 'logs', 'auth_budget', AID + '.jsonl')

    tag = a.tag or time.strftime('%Y%m%d_%H%M%S')
    if a.resume_outdir:
        outdir = os.path.abspath(a.resume_outdir)
        if not os.path.isdir(outdir):
            raise SystemExit('续跑目录不存在: %s' % outdir)
        dp = os.path.join(outdir, 'DISPATCH_PLAN.json')
        prev = json.load(open(dp, encoding='utf-8'))
        full_plan = plan_doc['dispatch_order']
        plan = full_plan[:prev['plan_len']]
        done_ids, failed_ids = set(), set()
        for fn in os.listdir(outdir):
            if fn.startswith('RATINGS_') and fn.endswith('.jsonl'):
                for l in open(os.path.join(outdir, fn), encoding='utf-8'):
                    if l.strip():
                        done_ids.add(json.loads(l)['attempt_id'])
        prev_sum = os.path.join(outdir, 'JUDGE_BATCH_SUMMARY.json')
        if os.path.isfile(prev_sum):
            failed_ids |= set(json.load(open(prev_sum, encoding='utf-8'))
                              .get('stopped', {}).get('failed_attempt_ids') or [])
        if os.path.isfile(ledger_path):
            started_ids, completed_ids, failed_live = set(), set(), set()
            for l in open(ledger_path, encoding='utf-8'):
                if l.strip():
                    r = json.loads(l)
                    k = r.get('kind')
                    if k == 'attempt_started':
                        started_ids.add(r.get('attempt_id'))
                    elif k == 'attempt_completed':
                        completed_ids.add(r.get('attempt_id'))
                    elif k == 'attempt_failed':
                        failed_live.add(r.get('attempt_id'))
            failed_ids |= failed_live
            orphans = {x for x in started_ids - completed_ids - failed_live if x not in done_ids}
        else:
            orphans = set()

        def _prefix(x):
            return x.rsplit(':', 1)[0]
        skipped = {_prefix(x) for x in done_ids | failed_ids | orphans}
        plan = [p for p in plan
                if ('judge:QP:%s:%s' % (p['item_id'], p['kind'])) not in skipped]
        expected_total = prev['plan_len']
        if len(plan) + len(skipped) != expected_total:
            raise SystemExit('续跑去重后 %d + 已派发 %d ≠ 原计划 %d' % (len(plan), len(skipped), expected_total))
        json.dump({'resumed_at_tag': tag, 'skipped': sorted(skipped),
                   'failed_not_refilled': sorted({_prefix(x) for x in failed_ids}),
                   'orphans_not_refilled': sorted({_prefix(x) for x in orphans}),
                   'remaining': len(plan)},
                  open(os.path.join(outdir, 'RESUME_%s.json' % tag), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
    else:
        outdir = os.path.join(ROOT, 'logs', 'quality_panel_v1_%s_%s' % (a.mode, tag))
        os.makedirs(outdir, exist_ok=True)

    manual_text = open(os.path.join(PK, 'QUALITY_MANUAL_V1.md'), encoding='utf-8').read()
    system_full = JUDGE_SYSTEM + '\n\n---\n\n' + manual_text
    cfg = {'system_sha256': sha(system_full), 'model_alias': MODEL_ALIAS,
           'temperature': TEMPERATURE, 'stream': False,
           'api_config_identity': 'api_cfg:openai_compat_stream_false_v1',
           'endpoint': PINNED_ENDPOINT, 'manual': 'QUALITY_MANUAL_V1',
           'plan_sha256': sha256_file(os.path.join(PK, 'QUALITY_PLAN.json')),
           'authorization_id': AID, 'cap': cap,
           'timeout_s': a.timeout_s, 'concurrency': a.concurrency,
           'note': 'QP judge batch: advisory nine-dim development review; no willingness labels'}
    if not a.resume_outdir:
        json.dump({'plan_len': len(plan), 'config': cfg},
                  open(os.path.join(outdir, 'DISPATCH_PLAN.json'), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
    else:
        prev_cfg = json.load(open(os.path.join(outdir, 'DISPATCH_PLAN.json'),
                                  encoding='utf-8')).get('config', {})
        for k in ('system_sha256', 'model_alias', 'temperature', 'stream',
                  'api_config_identity', 'endpoint', 'manual', 'plan_sha256'):
            if prev_cfg.get(k) != cfg.get(k):
                raise SystemExit('拒绝启动：续跑配置身份不符 %s' % k)

    from arena.isolation.authorization_budget import AuthorizationBudget, BudgetRefused
    if a.mode == 'live':
        budget = AuthorizationBudget(ledger_path, AID, cap, scope=scope)
        led = ledger_path
    else:
        rid = AID + '-REHEARSAL-' + tag
        led = os.path.join(ROOT, 'logs', 'auth_budget_rehearsal', rid + '.jsonl')
        budget = AuthorizationBudget(led, rid, cap, scope=dict(scope, rehearsal=True))

    records, stopped = [], None
    io_lock = threading.Lock()
    tls = threading.local()

    def worker_gateway():
        if a.mode != 'live':
            return None
        gw = getattr(tls, 'gw', None)
        if gw is None:
            from arena.lb_gateway import ADAPTERS, ModelGateway
            gw = ModelGateway(ADAPTERS['openai_compat'](),
                              event_log_path=os.path.join(
                                  outdir, 'GATEWAY_EVENTS_w%s.jsonl' % threading.current_thread().name))
            gw.set_run_context('quality_panel_v1_%s' % tag, 'quality_panel_judge')
            tls.gw = gw
        return gw

    def dispatch_one(i, p):
        attempt_id = 'judge:QP:%s:%s:%d' % (p['item_id'], p['kind'], i)
        try:
            with io_lock:
                budget.reserve(attempt_id, purpose='quality_panel_semantic_review')
        except BudgetRefused as exc:
            return None, {'reason': 'budget_refused', 'at': i, 'detail': str(exc)}
        t0 = time.time()
        user = judge_payload(p, nodes_by_id, controls_by_id, card_by_case, bind_map)
        usage, err = None, None
        if a.mode == 'mock':
            exp_slots = [b['slot'] for b in nodes_by_id[p['item_id']]['answer']['backups']
                         if b.get('text')] if p['kind'] != 'control' else []
            raw, ok = stub_response(p, i, exp_slots), True
        else:
            try:
                res = worker_gateway().generate(system=system_full, user=user, model=MODEL_ALIAS,
                                                temperature=TEMPERATURE, tag='qp_judge',
                                                attempt_id=attempt_id, timeout_s=a.timeout_s)
                raw, ok = res['text'], True
                usage = res.get('usage')
            except Exception as exc:                       # noqa: BLE001
                raw, ok = '%s: %s' % (type(exc).__name__, exc), False
                err = raw
        parsed, perr = None, None
        if ok:
            try:
                parsed = json.loads(raw)
            except Exception as exc:                       # noqa: BLE001
                perr = str(exc)
        schema_ok, schema_why = (False, 'parse failed') if parsed is None \
            else check_schema(parsed, p, nodes_by_id, controls_by_id)
        rec = {'seq': i, 'kind': p['kind'], 'item_id': p['item_id'], 'attempt_id': attempt_id,
               'authorization_id': budget.authorization_id,
               'ledger': os.path.relpath(led, ROOT),
               'user_sha256': sha(user), 'raw_sha256': sha(raw),
               'transport_ok': ok, 'parse_ok': parsed is not None, 'parse_error': perr,
               'schema_ok': schema_ok, 'schema_why': schema_why,
               'wall_ms': int((time.time() - t0) * 1000),
               'usage': usage, 'error': err, 'raw': raw}
        if parsed is not None and schema_ok:
            rec['parsed'] = parsed
        try:
            with io_lock:
                if ok:
                    budget.record('attempt_completed', attempt_id,
                                  usage=usage, raw_sha256=rec['raw_sha256'])
                else:
                    budget.record('attempt_failed', attempt_id, error=(err or raw)[:200])
                with open(os.path.join(outdir, 'RATINGS_%s.jsonl' % p['kind']), 'a',
                          encoding='utf-8') as f:
                    f.write(json.dumps(rec, ensure_ascii=False) + '\n')
        except OSError as exc:
            return rec, {'reason': 'ledger_write_fail', 'at': i, 'detail': str(exc)}
        if not ok:
            return rec, {'reason': 'transport_fail_fast', 'at': i, 'attempt_id': attempt_id,
                         'failed_attempt_ids': [attempt_id], 'detail': (err or raw)[:200]}
        return rec, None

    if stopped is None:
        try:
            budget.ensure_batch(len(plan), purpose='quality_panel_batch')
        except BudgetRefused as exc:
            stopped = {'reason': 'budget_refused', 'at': 0, 'detail': str(exc)}

    if stopped is None and a.concurrency <= 1:
        for i, p in enumerate(plan):
            rec, stop_reason = dispatch_one(i, p)
            if rec is not None:
                records.append(rec)
            if stop_reason:
                stopped = stop_reason
                break
    elif stopped is None:
        it = iter(list(enumerate(plan)))
        pending = {}
        ex = concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency)
        stop_submit = False
        try:
            while True:
                while not stop_submit and len(pending) < a.concurrency:
                    try:
                        i, p = next(it)
                    except StopIteration:
                        break
                    pending[ex.submit(dispatch_one, i, p)] = (i, p)
                if not pending:
                    break
                done, _ = concurrent.futures.wait(pending, return_when=concurrent.futures.FIRST_COMPLETED)
                for fut in done:
                    i, p = pending.pop(fut)
                    try:
                        rec, stop_reason = fut.result()
                    except Exception as exc:               # noqa: BLE001
                        rec, stop_reason = None, {'reason': 'worker_exception', 'at': i,
                                                  'detail': '%s: %s' % (type(exc).__name__, exc)}
                    if rec is not None:
                        records.append(rec)
                    if stop_reason and stopped is None:
                        stopped = stop_reason
                        stop_submit = True
        finally:
            ex.shutdown(wait=True)

    summary = {'mode': a.mode, 'attempts': len(records), 'planned': len(plan),
               'transport_fail': sum(1 for r in records if not r['transport_ok']),
               'parse_fail': sum(1 for r in records if r['transport_ok'] and not r['parse_ok']),
               'schema_fail': sum(1 for r in records if r['transport_ok'] and r['parse_ok']
                                  and not r['schema_ok']),
               'stopped': stopped, 'config': cfg, 'outdir': os.path.relpath(outdir, ROOT),
               'resume': bool(a.resume_outdir), 'budget': budget.summary()}
    name = 'JUDGE_BATCH_SUMMARY_RESUME.json' if a.resume_outdir else 'JUDGE_BATCH_SUMMARY.json'
    json.dump(summary, open(os.path.join(outdir, name), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('QUALITY PANEL mode=%s attempts=%d transport_fail=%d parse_fail=%d schema_fail=%d '
          'stopped=%s budget_used=%s'
          % (a.mode, len(records), summary['transport_fail'], summary['parse_fail'],
             summary['schema_fail'], (stopped or {}).get('reason'), summary['budget'].get('used')))
    if stopped is not None:
        sys.exit(2)


if __name__ == '__main__':
    main()
