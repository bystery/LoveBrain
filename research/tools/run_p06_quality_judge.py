# -*- coding: utf-8 -*-
r"""tools/run_p06_quality_judge.py — P06 评审批（AUTH-P06-JUDGE-20261027-01，cap 256）。

复用 tools/run_quality_panel_v1.py 的调度骨架（ModelGateway + AuthorizationBudget + 失败停批 +
逐调用落盘），只把题包/schema/scope/计划参数化。P06 包的节点/控制/复评由
tools/build_p06_judge_packet.py 冻结。

- 派发前门禁：P06_QUALITY_PLAN.json 在且输入散列全部吻合（冻结后输入变化即拒）；
- live 需要 LB_ALLOW_P06_JUDGE_LIVE=1；评审批冻结非流式身份（LB_STREAM_TIMING 开启即拒）；
- attempt_id='judge:P06:{item_id}:{kind}:{seq}'，全局唯一；失败消耗不退款、停批不补跑；
- 语义/schema 不合法 → 留原文标 schema_ok=False 离线未评，**不重发求满意判词**；
- mock 用演练账本 logs/auth_budget_rehearsal/，绝不占用真实单；
- --check 零派发零账本：只验证冻结包、计划长度、每个 item 可解析、schema 期望槽。

运行：
  D:\anaconda\python.exe tools/run_p06_quality_judge.py --check
  D:\anaconda\python.exe tools/run_p06_quality_judge.py --mode mock
  set LB_ALLOW_P06_JUDGE_LIVE=1 && D:\anaconda\python.exe tools/run_p06_quality_judge.py --mode live
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

PK = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'p06_judge_20261027')
AID = 'AUTH-P06-JUDGE-20261027-01'
CAP = 256
MODEL_ALIAS = 'dsf'
TEMPERATURE = 0.7
PINNED_ENDPOINT = 'http://127.0.0.1:17770/v1'
DIM_KEYS = ['goal', 'factuality', 'attribution', 'time_state', 'boundary',
            'memory_use', 'interaction', 'initiative', 'emotion_relation']

JUDGE_SYSTEM = """你是聊天回复评审者。按所附《QUALITY_MANUAL_V1_1》对给出的每一条回复（首选与每条非空备选）独立打九维分并逐条标注可核主张。不要猜测答案来自哪个版本、不猜测成本、不预测用户是否愿意发送。node_id 只是题号，不代表控制/候选/批次。
硬性要求：
1) scores 九键齐全（goal/factuality/attribution/time_state/boundary/memory_use/interaction/initiative/emotion_relation），值 0-3 或 null；null 必须同时在 na_reasons 给理由。
2) 输入 backups 里每条非空备选都必须在输出 backups 数组出现恰好一次，slot 名逐字照抄；没有备选就输出空数组。
3) claims 的 quote 与 evidence_quote 必须逐字摘自输入材料；自己找不到证据就写进 uncertainties，不得因此给考生判 0 或编造证据。
4) 受保护表达（调侃/玩笑/新提议/条件句/承认未知/观点表达/合法不回）不因"没核实/没澄清"判失败；只有把无据细节说成已核实才算事实错误。允许多种正确表达，不要求唯一话术。
5) 严格执行 V1_1 §2a：primary 出现 severity=major 且 support∈{unknown,conflict} 的主张，对应维度必须 ≤1；备选同类主张 ≤2 并在 reason 说明。
6) 严格输出 JSON：无解释文字、无 markdown 代码块。"""


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def load_frozen_inputs():
    plan_doc = json.load(open(os.path.join(PK, 'P06_QUALITY_PLAN.json'), encoding='utf-8'))
    for fname, exp in plan_doc['input_hashes_sha256'].items():
        got = sha256_file(os.path.join(PK, fname))
        if got != exp:
            raise SystemExit('拒绝启动：冻结输入已变化 %s（期望 %s… 实际 %s…）'
                             % (fname, exp[:16], got[:16]))
    nodes = [json.loads(l) for l in open(os.path.join(PK, 'P06_NODES.jsonl'), encoding='utf-8')]
    controls = json.load(open(os.path.join(PK, 'P06_CONTROLS.json'), encoding='utf-8'))['controls']
    bindings = json.load(open(os.path.join(PK, 'P06_PRIVATE_BINDINGS.json'), encoding='utf-8'))
    return plan_doc, nodes, controls, bindings


def judge_payload(item, nodes_by_id, controls_by_id):
    """构造判词输入（无臂名/批次/成本/未来剧情/expectation）。"""
    if item['kind'] == 'control':
        c = controls_by_id[item['item_id']]
        body = {'node_id': item['item_id'],
                'evidence': c['context'],
                'frozen_card': None,
                'reference_card': None,
                'answer': {'primary': c['answer'], 'backups': []}}
    else:
        n = nodes_by_id[item['item_id']]
        backups = [{'slot': b['slot'], 'text': b['text']}
                   for b in n['answer']['backups'] if b.get('text')]
        body = {'node_id': item['item_id'],
                'evidence': n['evidence'],
                'frozen_card': n['evidence']['current_card'],
                'reference_card': n['reference_card'],
                'answer': {'primary': n['answer']['primary'], 'backups': backups}}
    return json.dumps(body, ensure_ascii=False)


def check_schema(parsed, item, nodes_by_id):
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
    if item['kind'] == 'control':
        if parsed.get('backups') != []:
            why.append('控制样本 backups 应为空数组')
    else:
        n = nodes_by_id[item['item_id']]
        exp_slots = [b['slot'] for b in n['answer']['backups'] if b.get('text')]
        got = parsed.get('backups')
        if not isinstance(got, list):
            why.append('backups 不是数组')
        else:
            got_slots = [b.get('slot') for b in got if isinstance(b, dict)]
            if sorted(got_slots) != sorted(exp_slots):
                why.append('backups slot 覆盖不符（期望 %d 条，实得 %d 条）'
                           % (len(exp_slots), len(got_slots)))
            for b in got:
                if not isinstance(b, dict):
                    continue
                bsc = b.get('scores')
                if not isinstance(bsc, dict) or set(bsc.keys()) != set(DIM_KEYS):
                    why.append('backup[%s].scores 九键不符' % b.get('slot'))
                else:
                    for k, v in bsc.items():
                        if v is not None and v not in (0, 1, 2, 3):
                            why.append('backup[%s].scores[%s]=%r 越界' % (b.get('slot'), k, v))
    if parsed.get('node_id') != item['item_id']:
        why.append('node_id=%r ≠ %r' % (parsed.get('node_id'), item['item_id']))
    return (not why), '; '.join(why) or 'ok'


def stub_response(item, seq, exp_slots):
    prim = {'scores': {k: (seq % 4) for k in DIM_KEYS}, 'na_reasons': {},
            'claims': [], 'action_basis': 'stub'}
    backups = [{'slot': s, 'scores': {k: 2 for k in DIM_KEYS},
                'na_reasons': {}, 'claims': []} for s in exp_slots]
    return json.dumps({'node_id': item['item_id'], 'primary': prim,
                       'backups': backups, 'uncertainties': []}, ensure_ascii=False)


def do_check(plan_doc, nodes_by_id, controls_by_id, plan):
    """零派发自检：计划长度、类别、可解析、期望槽、payload 不含私有字段。"""
    problems = []
    if len(plan) != plan_doc['planned_attempts']:
        problems.append('计划长度 %d != planned %d' % (len(plan), plan_doc['planned_attempts']))
    if len(plan) > plan_doc['cap']:
        problems.append('计划 %d > cap %d' % (len(plan), plan_doc['cap']))
    seen = set()
    kinds = {'primary': 0, 'repeat': 0, 'control': 0}
    for p in plan:
        if p['item_id'] in seen:
            problems.append('重复 item_id %s' % p['item_id'])
        seen.add(p['item_id'])
        kinds[p['kind']] = kinds.get(p['kind'], 0) + 1
        if p['kind'] == 'control':
            if p['item_id'] not in controls_by_id:
                problems.append('控制不存在 %s' % p['item_id'])
        else:
            if p['item_id'] not in nodes_by_id:
                problems.append('节点不存在 %s' % p['item_id'])
                continue
            n = nodes_by_id[p['item_id']]
            if not n['answer']['primary'].strip():
                problems.append('节点首选为空 %s' % p['item_id'])
            slots = [b['slot'] for b in n['answer']['backups']]
            if len(slots) != len(set(slots)):
                problems.append('节点槽重复 %s' % p['item_id'])
        body = judge_payload(p, nodes_by_id, controls_by_id)
        for banned in ('"arm"', '"batch"', '"candidate"', 'expectation', 'future'):
            if banned in body:
                problems.append('payload 含私有字段 %s in %s' % (banned, p['item_id']))
    counts = plan_doc.get('counts') or {}
    if kinds['primary'] != counts.get('primary'):
        problems.append('首选计划 %d != 冻结计数 %r' % (kinds['primary'], counts.get('primary')))
    if kinds['repeat'] != counts.get('repeat'):
        problems.append('复评计划 %d != 冻结计数 %r' % (kinds['repeat'], counts.get('repeat')))
    if kinds['control'] != counts.get('control'):
        problems.append('控制计划 %d != 冻结计数 %r' % (kinds['control'], counts.get('control')))
    print('CHECK: planned=%d kinds=%s nodes=%d controls=%d problems=%d'
          % (len(plan), kinds, len(nodes_by_id), len(controls_by_id), len(problems)))
    for x in problems:
        print('  -', x)
    return not problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', default='mock', choices=('check', 'mock', 'live'))
    ap.add_argument('--limit', type=int, default=None)
    ap.add_argument('--cap', type=int, default=None, help='仅 mock 生效；live 强制 256')
    ap.add_argument('--tag', default=None)
    ap.add_argument('--resume-outdir', default=None,
                    help='续跑：复用输出目录，已落盘/已失败/在跑孤儿不补跑')
    ap.add_argument('--timeout-s', type=int, default=240)
    ap.add_argument('--concurrency', type=int, default=2)
    ap.add_argument('--max-fail-streak', type=int, default=4,
                    help='连续传输失败达到该值才停批；孤立失败只记录不补跑')
    ap.add_argument('--retry-transient', type=int, default=5,
                    help='空正文/ConnectionReset 等瞬时故障在同一预留内内联重试次数（不另占额度）')
    ap.add_argument('--retry-sleep', type=float, default=4.0, help='瞬时重试线性退避秒数基数')
    ap.add_argument('--model', default=None,
                    help='裁判模型别名（默认 dsf）；dsf 失效后传 qwen3.8-flash')
    ap.add_argument('--packet', default=None, help='冻结题包目录（默认 p06_judge_20261027）')
    ap.add_argument('--aid', default=None, help='裁判授权 ID（默认 AUTH-P06-JUDGE-20261027-01）')
    a = ap.parse_args()
    global PK, AID, CAP, MODEL_ALIAS
    if a.model:
        MODEL_ALIAS = a.model
    if a.packet:
        PK = a.packet if os.path.isabs(a.packet) else os.path.join(ROOT, a.packet)
    if a.aid:
        AID = a.aid
    if a.cap:
        CAP = a.cap
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

    plan_doc, nodes, controls, bindings = load_frozen_inputs()
    nodes_by_id = {n['_meta']['node_id']: n for n in nodes}
    controls_by_id = {c['node_id']: c for c in controls}
    if len(nodes_by_id) != len(nodes):
        raise SystemExit('节点 ID 重复')
    bind_ids = {b['node_id'] for b in bindings}
    if bind_ids != set(nodes_by_id):
        raise SystemExit('私有绑定与节点 ID 不一致')
    plan = plan_doc['dispatch_order']
    if a.mode == 'check':
        sys.exit(0 if do_check(plan_doc, nodes_by_id, controls_by_id, plan) else 1)

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
        if os.environ.get('LB_ALLOW_P06_JUDGE_LIVE', '') != '1':
            raise SystemExit('拒绝启动：live 需要 LB_ALLOW_P06_JUDGE_LIVE=1')
        if os.environ.get('LB_STREAM_TIMING', '0').strip().lower() in ('1', 'true', 'yes', 'on'):
            raise SystemExit('拒绝启动：评审批冻结非流式身份，不得开启 LB_STREAM_TIMING')
        os.environ['LB_API_BASE'] = PINNED_ENDPOINT
        os.environ['LB_MODEL'] = MODEL_ALIAS
        os.environ.setdefault('LB_API_KEY', 'local')

    auth_doc = json.load(open(os.path.join(PK, 'AUTHORIZATION_P06_JUDGE.json'), encoding='utf-8'))
    from arena.isolation.authorization_budget import (AuthorizationBudget, BudgetRefused,
                                                      validate_authorization_doc)
    validate_authorization_doc(auth_doc, expected_endpoint=PINNED_ENDPOINT,
                               expected_model=MODEL_ALIAS, expected_temperature=TEMPERATURE)
    if auth_doc.get('planned_attempts') != plan_doc['planned_attempts']:
        raise SystemExit('授权 planned_attempts 与计划不符')
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
                if ('judge:P06:%s:%s' % (p['item_id'], p['kind'])) not in skipped]
        expected_total = prev['plan_len']
        if len(plan) + len(skipped) != expected_total:
            raise SystemExit('续跑去重后 %d + 已派发 %d ≠ 原计划 %d'
                             % (len(plan), len(skipped), expected_total))
        json.dump({'resumed_at_tag': tag, 'skipped': sorted(skipped),
                   'failed_not_refilled': sorted({_prefix(x) for x in failed_ids}),
                   'orphans_not_refilled': sorted({_prefix(x) for x in orphans}),
                   'remaining': len(plan)},
                  open(os.path.join(outdir, 'RESUME_%s.json' % tag), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
    else:
        outdir = os.path.join(ROOT, 'logs', 'p06_judge_%s_%s' % (a.mode, tag))
        os.makedirs(outdir, exist_ok=True)

    manual_text = open(os.path.join(PK, 'QUALITY_MANUAL_V1_1.md'), encoding='utf-8').read()
    system_full = JUDGE_SYSTEM + '\n\n---\n\n' + manual_text
    cfg = {'system_sha256': sha(system_full), 'model_alias': MODEL_ALIAS,
           'temperature': TEMPERATURE, 'stream': False,
           'api_config_identity': 'api_cfg:openai_compat_stream_false_v1',
           'endpoint': PINNED_ENDPOINT, 'manual': 'QUALITY_MANUAL_V1_1',
           'packet_plan_sha256': sha256_file(os.path.join(PK, 'P06_QUALITY_PLAN.json')),
           'authorization_id': AID, 'cap': cap,
           'timeout_s': a.timeout_s, 'concurrency': a.concurrency,
           'note': 'P06 judge batch: advisory nine-dim development review; no willingness labels'}
    if not a.resume_outdir:
        json.dump({'plan_len': len(plan), 'config': cfg},
                  open(os.path.join(outdir, 'DISPATCH_PLAN.json'), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
    else:
        prev_cfg = json.load(open(os.path.join(outdir, 'DISPATCH_PLAN.json'),
                                  encoding='utf-8')).get('config', {})
        for k in ('system_sha256', 'model_alias', 'temperature', 'stream',
                  'api_config_identity', 'endpoint', 'manual', 'packet_plan_sha256'):
            if prev_cfg.get(k) != cfg.get(k):
                raise SystemExit('拒绝启动：续跑配置身份不符 %s' % k)

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
            gw.set_run_context('p06_judge_%s' % tag, 'p06_quality_judge')
            tls.gw = gw
        return gw

    def dispatch_one(i, p):
        attempt_id = 'judge:P06:%s:%s:%d' % (p['item_id'], p['kind'], i)
        try:
            with io_lock:
                budget.reserve(attempt_id, purpose='p06_quality_judge_semantic_review')
        except BudgetRefused as exc:
            return None, {'reason': 'budget_refused', 'at': i, 'detail': str(exc)}
        t0 = time.time()
        user = judge_payload(p, nodes_by_id, controls_by_id)
        usage, err = None, None
        if a.mode == 'mock':
            exp_slots = [b['slot'] for b in nodes_by_id[p['item_id']]['answer']['backups']
                         if b.get('text')] if p['kind'] != 'control' else []
            raw, ok = stub_response(p, i, exp_slots), True
        else:
            # 传输层瞬时垃圾（200 空正文 / ConnectionReset / timeout）在同一 attempt 预留内
            # 内联重试至多 a.retry_transient 次（不另占额度，非"重发求满意判词"）；
            # 拿到非空正文即停——非空但 JSON 解析失败属真实裁判结果，不重试。
            attempt_try = 0
            while True:
                attempt_try += 1
                transient = False
                try:
                    res = worker_gateway().generate(system=system_full, user=user, model=MODEL_ALIAS,
                                                    temperature=TEMPERATURE, tag='p06_judge',
                                                    attempt_id=attempt_id, timeout_s=a.timeout_s)
                    raw, ok = res['text'], True
                    usage = res.get('usage')
                    if ok and not (raw or '').strip():
                        raw, ok = 'empty_content_response', False
                        err = raw
                        transient = True
                except Exception as exc:                       # noqa: BLE001
                    raw, ok = '%s: %s' % (type(exc).__name__, exc), False
                    err = raw
                    transient = True
                if ok or attempt_try >= a.retry_transient or a.mode != 'live':
                    break
                # 只重试传输层瞬时故障，带线性退避
                if transient:
                    time.sleep(a.retry_sleep * attempt_try)
        parsed, perr = None, None
        if ok:
            try:
                parsed = json.loads(raw)
            except Exception as exc:                       # noqa: BLE001
                perr = str(exc)
        schema_ok, schema_why = (False, 'parse failed') if parsed is None \
            else check_schema(parsed, p, nodes_by_id)
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
        # 单次 sporadic 失败（空正文 / 孤立 ConnectionReset）只记录、消耗额度、不补跑，
        # 但不再直接停整批——停批判定交给下面的"连续失败 burst"闸（见 dispatch 循环）。
        return rec, None

    fail_streak = {'n': 0, 'last': None}
    streak_lock = threading.Lock()

    def note_outcome(rec):
        """更新连续传输失败计数；返回是否应触发 burst 停批（None 或 stop_reason）。"""
        if rec is None:
            return None
        with streak_lock:
            if rec.get('transport_ok'):
                fail_streak['n'] = 0
                fail_streak['last'] = None
                return None
            fail_streak['n'] += 1
            fail_streak['last'] = rec['attempt_id']
            burst = fail_streak['n'] >= FAIL_STREAK_MAX
            n = fail_streak['n']
        if burst:
            return {'reason': 'transport_fail_burst', 'at': rec['seq'],
                    'attempt_id': rec['attempt_id'],
                    'failed_attempt_ids': [fail_streak['last']],
                    'detail': 'consecutive transport failures=%d (>= %d); upstream likely down'
                              % (n, FAIL_STREAK_MAX)}
        return None

    FAIL_STREAK_MAX = a.max_fail_streak
    if stopped is None:
        try:
            budget.ensure_batch(len(plan), purpose='p06_quality_judge_batch')
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
            burst = note_outcome(rec)
            if burst:
                stopped = burst
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
                    elif rec is not None and stopped is None:
                        burst = note_outcome(rec)
                        if burst:
                            stopped = burst
                            stop_submit = True
        finally:
            ex.shutdown(wait=True)

    walls = sorted(r['wall_ms'] for r in records)
    usage_prompt = sum((r.get('usage') or {}).get('prompt') or 0 for r in records
                       if isinstance(r.get('usage'), dict))
    usage_completion = sum((r.get('usage') or {}).get('completion') or 0 for r in records
                           if isinstance(r.get('usage'), dict))
    usage_unknown = sum(1 for r in records if not isinstance(r.get('usage'), dict)
                        or (r['usage'].get('cache_hit') is None))
    summary = {'mode': a.mode, 'attempts': len(records), 'planned': len(plan),
               'transport_fail': sum(1 for r in records if not r['transport_ok']),
               'parse_fail': sum(1 for r in records if r['transport_ok'] and not r['parse_ok']),
               'schema_fail': sum(1 for r in records if r['transport_ok'] and r['parse_ok']
                                  and not r['schema_ok']),
               'wall_ms_p50': walls[len(walls) // 2] if walls else None,
               'wall_ms_p95': walls[min(len(walls) - 1, int(len(walls) * 0.95))] if walls else None,
               'usage_prompt_sum': usage_prompt, 'usage_completion_sum': usage_completion,
               'usage_unknown_or_cache_null': usage_unknown,
               'stopped': stopped, 'config': cfg, 'outdir': os.path.relpath(outdir, ROOT),
               'resume': bool(a.resume_outdir), 'budget': budget.summary()}
    name = 'JUDGE_BATCH_SUMMARY_RESUME.json' if a.resume_outdir else 'JUDGE_BATCH_SUMMARY.json'
    json.dump(summary, open(os.path.join(outdir, name), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('P06 JUDGE mode=%s attempts=%d transport_fail=%d parse_fail=%d schema_fail=%d '
          'stopped=%s budget_used=%s'
          % (a.mode, len(records), summary['transport_fail'], summary['parse_fail'],
             summary['schema_fail'], (stopped or {}).get('reason'), summary['budget'].get('used')))
    if stopped is not None:
        sys.exit(2)


if __name__ == '__main__':
    main()
