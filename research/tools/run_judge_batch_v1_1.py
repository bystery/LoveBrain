# -*- coding: utf-8 -*-
"""tools/run_judge_batch_v1_1.py — 裁判标定批次（独立评估账本，204 次硬上限，失败停批不补跑）。

2026-10-25 修复版（原版备份 tools/_backup_20261025/run_judge_batch_v1_1.py.pre-fix-20261025，
修复对照见 STATE_MAP_20261025.md §4-C；原版从未成功运行过任何模式）：
  1) AuthorizationBudget 构造改 scope=<dict>（原 authorization_scope= 关键字不存在；
     且 _scope=dict(scope)，传字符串会崩——scope 必须是 dict）；
  2) reserve(attempt_id, purpose=...)，attempt_id='judge:{rater}:{node_id}:{kind}:{seq}' 全局唯一
     （原 reserve(1, note=) 第二次循环必触发 duplicate 占额拒绝）；
     每 attempt 结束落 attempt_completed / attempt_failed（消耗不退款）；
  3) 派发前整批预检 ensure_batch(len(plan))（不足/已关/损坏 → 零派发）；
  4) live 分支真正接线：ModelGateway(ADAPTERS['openai_compat']()) + 网关事件账 GATEWAY_EVENTS.jsonl，
     res['text']→raw、usage/timing 落盘；评审批冻结非流式配置身份（LB_STREAM_TIMING 若开启则拒绝启动）；
  5) mock 模式用演练账本 logs/auth_budget_rehearsal/（绝不占用真实单 AUTH-EVAL-JUDGE-20261025-01）；
  6) summary 取键 used（原 'reserved' 键不存在）；
  7) --cap 仅 mock 生效（演练预算拒超用），live 强制 204；
  8) --inject-fail-at 仅 mock 生效（演练失败停批路径用）。

前置条件（缺一即拒绝启动）：
  1) evaluation/CALIBRATION_PACKET_V1_1/FROZEN_MANIFEST.json 存在（参考/分组/阈值先冻结）；
  2) live 使用独立评估账本 AUTH-EVAL-JUDGE-20261025-01（scope=evaluation_judge_calibration_v1_1），
     不借用性能阶段账本（那个 scope 禁止 judge 调用）；上限 204；需 LB_ALLOW_LIVE_JUDGE=1；
  3) 评审输入只来自 nodes_public.jsonl：无臂名、无旧判词、无参考标签、无散列、无 split；
  4) 三份判词如实命名为 R1/R2/R3 = 同一模型服务、同一 prompt、不同会话，绝不称三个独立模型。

计划（与 WORKER_EXECUTION 包C 一致，共 204 attempts）：
  60 对 × 3 评审者 = 180（正序）+ 12 对正序重跑 + 12 对换序 = 204。
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

PK = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_V1_1')
AID = 'AUTH-EVAL-JUDGE-20261025-01'
CAP = 204
RATERS = ('R1', 'R2', 'R3')
TEMPERATURE = 0.7
MODEL_ALIAS = 'dsf'
PINNED_ENDPOINT = 'http://127.0.0.1:17770/v1'

# scope 必须是 dict（AuthorizationBudget._scope=dict(scope)），且确定性（重建对象时逐字比对）
SCOPE = {
    'scope_id': 'evaluation_judge_calibration_v1_1',
    'purpose': ('judge calibration: 60 pairs x R1/R2/R3 + 12 repeat_same + 12 repeat_swapped '
                '= 204 attempts; non-streaming identity api_cfg:openai_compat_stream_false_v1; '
                'temperature 0.7; model alias dsf; fail fast, no retry, no refill'),
    'automatic_candidate_generations': False,
    'endpoint': PINNED_ENDPOINT,
    'model_alias': MODEL_ALIAS,
    'raters_note': 'R1/R2/R3 = same model service, same prompt, different sessions; NOT three independent models',
}

JUDGE_SYSTEM = """你是聊天回复评审者。只依据给定材料与所附评审手册（MANUAL-V1.1）判断，不要猜测答案来源、版本或成本。
硬性要求：
1) 逐条主张标注四轴：statement_type（事实/类指常识/新提议/玩笑/观点/疑问/不确定表达）、
   evidence_status（支持/冲突/未提供/不适用）、stance（确定/有条件/试探/承认未知）、severity（关键/次要/无），
   并给出该主张原文片段与证据片段（必须逐字摘自输入）。
2) 调侃、新提议、条件句、承认未知、观点表达是受保护表达；不得因为"没核实来源/没澄清"就判失败。
3) 目标完成度给 0-3：3=动作到位且无关键无据断言；2=平淡但正确；1=安全但回避该做的动作；0=该回未回或做相反动作。
4) 输入信息确实不足时，问/不问/试探/承认未知都可以是对的；只有把无据细节说成已核实才算错。
5) 输出严格 JSON，不要任何解释性文字、不要 markdown 代码块。
输出 schema：
{"claims":[{"who":"甲|乙","text":"主张原文逐字","statement_type":"...","evidence_status":"...",
  "stance":"...","severity":"...","evidence_quote":"输入中的逐字片段"}],
 "goal":{"甲":0-3,"乙":0-3,"action_basis":{"甲":"这轮该做的动作","乙":"..."}},"insufficient_info":{"甲":"ok|wrong|na","乙":"ok|wrong|na"},
 "protocol_anomaly":{"甲":"none|key_contamination|missing_slot|directions_invalid|unparseable",
  "乙":"..."},
 "pref":"甲|乙|都行|都不想|说不清", "pref_basis":"依据手册的哪一条（不要写个人口味）"}
"""


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def load_nodes():
    return [json.loads(l) for l in open(os.path.join(PK, 'nodes_public.jsonl'), encoding='utf-8')]


def user_payload(node, swapped):
    a, b = node['answers']['甲'], node['answers']['乙']
    left, right = (b, a) if swapped else (a, b)
    lab = ('乙', '甲') if swapped else ('甲', '乙')
    body = {'evidence': node['evidence'],
            'answers': {lab[0]: left, lab[1]: right}}
    return json.dumps(body, ensure_ascii=False)


def build_plan(nodes):
    """180 正序 + 12 正序重跑 + 12 换序；重复抽样用固定 seed 选择并在派发前冻结。"""
    import random
    rnd = random.Random(20261102)
    ids = [n['node_id'] for n in nodes]
    rep_same = sorted(rnd.sample(ids, 12))
    rep_swap = sorted(rnd.sample(ids, 12))
    plan = []
    for r in RATERS:
        for n in nodes:
            plan.append({'rater': r, 'node_id': n['node_id'], 'swapped': False, 'kind': 'primary'})
    for n in nodes:
        if n['node_id'] in rep_same:
            plan.append({'rater': RATERS[0], 'node_id': n['node_id'], 'swapped': False, 'kind': 'repeat_same'})
    for n in nodes:
        if n['node_id'] in rep_swap:
            plan.append({'rater': RATERS[0], 'node_id': n['node_id'], 'swapped': True, 'kind': 'repeat_swapped'})
    return plan, {'repeat_same': rep_same, 'repeat_swapped': rep_swap}


def judge_user(node, swapped):
    return ("按手册评审下面这一对回答（甲/乙标签已随机化，顺序见输入）。只输出 JSON。\n\n"
            + user_payload(node, swapped))


def stub_response(node, swapped, rater, seq):
    """mock 模式桩判词：结构完整，内容故意不核对，只测接线/停批/落盘。"""
    who_l, who_r = ('乙', '甲') if swapped else ('甲', '乙')
    return json.dumps({'claims': [{'who': who_l, 'text': (node['answers'][who_l]['primary'] or '')[:12],
                                   'statement_type': '观点', 'evidence_status': '不适用',
                                   'stance': '有条件', 'severity': '无',
                                   'evidence_quote': (str(node['evidence'].get('current_window')) or '')[:12]},
                                  {'who': who_r, 'text': (node['answers'][who_r]['primary'] or '')[:12],
                                   'statement_type': '事实', 'evidence_status': '未提供',
                                   'stance': '确定', 'severity': '关键',
                                   'evidence_quote': 'NOT-A-REAL-QUOTE'}],
                       'goal': {who_l: 2, who_r: 1, 'action_basis': {who_l: '接住', who_r: '推进'}},
                       'insufficient_info': {who_l: 'ok', who_r: 'na'},
                       'protocol_anomaly': {who_l: 'none', who_r: 'none'},
                       'pref': who_l if (seq % 3) else '说不清',
                       'pref_basis': '手册 §3'}, ensure_ascii=False)


def make_budget(mode, cap, tag):
    """live=真实单（logs/auth_budget/）；mock=演练单（logs/auth_budget_rehearsal/，绝不占用真实单）。"""
    from arena.isolation.authorization_budget import AuthorizationBudget
    if mode == 'live':
        led = os.path.join(ROOT, 'logs', 'auth_budget', AID + '.jsonl')
        return AuthorizationBudget(led, AID, cap, scope=SCOPE), led
    rid = '%s-REHEARSAL-%s' % (AID, tag)
    led = os.path.join(ROOT, 'logs', 'auth_budget_rehearsal', rid + '.jsonl')
    return AuthorizationBudget(led, rid, cap, scope=dict(SCOPE, rehearsal=True)), led


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', default='mock', choices=('mock', 'live'))
    ap.add_argument('--limit', type=int, default=None, help='只跑前 N 个 attempt（用于 dry-run）')
    ap.add_argument('--cap', type=int, default=None,
                    help='仅 mock 生效：演练账本上限（演练预算拒超用）；live 强制 204')
    ap.add_argument('--inject-fail-at', type=int, default=None,
                    help='仅 mock 生效：第 N 个 attempt 注入传输失败（演练失败停批用）')
    ap.add_argument('--tag', default=None,
                    help='固定输出目录/演练账本标签（默认时间戳；mock 复用同一演练账本可演练动态拒超）')
    ap.add_argument('--resume-outdir', default=None,
                    help='续跑模式：复用已有输出目录，跳过其中已落盘的 attempt（含失败项=永不补跑），'
                         '同一账本继续派发剩余计划；不重写 DISPATCH_PLAN.json')
    ap.add_argument('--timeout-s', type=int, default=120,
                    help='单次调用的 HTTP 超时（工程参数，记入 cfg.timeout_s；不影响配置身份）')
    ap.add_argument('--concurrency', type=int, default=1,
                    help='并发派发数（默认 1=串行。请求相互独立、无会话状态；账本/落盘经进程内锁+文件锁保护；'
                         'fail-fast 语义保留：任一失败后不再提交新任务，已在跑的跑完）')
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

    frozen = os.path.join(PK, 'FROZEN_MANIFEST.json')
    if not os.path.exists(frozen):
        raise SystemExit('拒绝启动：评价方法未冻结（缺 FROZEN_MANIFEST.json）')

    if a.mode == 'live':
        if os.environ.get('LB_ALLOW_LIVE_JUDGE', '') != '1':
            raise SystemExit('拒绝启动：live 模式需要 LB_ALLOW_LIVE_JUDGE=1')
        if os.environ.get('LB_STREAM_TIMING', '0').strip().lower() in ('1', 'true', 'yes', 'on'):
            raise SystemExit('拒绝启动：评审批冻结为非流式配置身份，不得开启 LB_STREAM_TIMING')
        cap = CAP
    else:
        cap = a.cap if a.cap else CAP
    if a.limit is not None and a.limit <= 0:
        raise SystemExit('--limit 必须为正')

    nodes_list = load_nodes()
    if len(nodes_list) != 60:
        raise SystemExit('拒绝启动：nodes_public.jsonl 节点数 %d ≠ 60（输入被改动）' % len(nodes_list))
    nodes = {n['node_id']: n for n in nodes_list}
    plan, sampling = build_plan(nodes_list)
    if len(plan) != CAP:
        raise SystemExit('拒绝启动：计划 %d ≠ 硬上限 %d（输入被改动）' % (len(plan), CAP))
    if a.limit:
        plan = plan[:a.limit]
    if len(plan) > cap:
        # 走 ensure_batch 前的静态拒绝路径：等价于预算拒超，落 summary 再退出
        early = {'reason': 'budget_refused', 'at': 0,
                 'detail': '计划 %d > 上限 %d（--limit 与 --cap 组合）' % (len(plan), cap)}
    else:
        early = None

    tag = a.tag or time.strftime('%Y%m%d_%H%M%S')
    if a.resume_outdir:
        if a.limit:
            raise SystemExit('拒绝启动：--resume-outdir 不能与 --limit 组合（计划不可再截断）')
        if a.inject_fail_at is not None:
            raise SystemExit('拒绝启动：--resume-outdir 不能与 --inject-fail-at 组合')
        outdir = os.path.abspath(a.resume_outdir)
        if not os.path.isdir(outdir):
            raise SystemExit('拒绝启动：--resume-outdir 目录不存在: %s' % outdir)
        dp = os.path.join(outdir, 'DISPATCH_PLAN.json')
        if not os.path.isfile(dp):
            raise SystemExit('拒绝启动：续跑目录缺 DISPATCH_PLAN.json（不是本脚本的批）')
        prev = json.load(open(dp, encoding='utf-8'))
        if not isinstance(prev.get('plan_len'), int) or prev.get('plan_len') <= 0:
            raise SystemExit('拒绝启动：续跑目录 DISPATCH_PLAN.plan_len 非法: %r' % prev.get('plan_len'))
        # 已派发集合 = RATINGS 已落盘 attempt ∪ 已知失败 attempt（失败项永不补跑）。
        # 失败项双源：①上一段 summary 的 failed_attempt_ids；②live 账本的 attempt_failed 记录。
        done_ids = set()
        for fn in os.listdir(outdir):
            if fn.startswith('RATINGS_') and fn.endswith('.jsonl'):
                for l in open(os.path.join(outdir, fn), encoding='utf-8'):
                    if l.strip():
                        done_ids.add(json.loads(l)['attempt_id'])
        failed_ids = set()
        orphan_ids = set()          # in-flight 孤儿（live 才从账本识别）
        prev_summary_p = os.path.join(outdir, 'JUDGE_BATCH_SUMMARY.json')
        if os.path.isfile(prev_summary_p):
            failed_ids |= set(json.load(open(prev_summary_p, encoding='utf-8'))
                              .get('stopped', {}).get('failed_attempt_ids') or [])
        if a.mode == 'live':
            led_live = os.path.join(ROOT, 'logs', 'auth_budget', AID + '.jsonl')
            if os.path.isfile(led_live):
                started_ids, completed_ids, failed_live = set(), set(), set()
                for l in open(led_live, encoding='utf-8'):
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
                # in-flight 孤儿：started 但无 completed/failed 且 RATINGS 无记录
                # （进程被杀时在跑的 attempt）——额度已扣、判词未落盘；不补发（重发会被账本拒）
                orphan_ids = {x for x in started_ids - completed_ids - failed_live
                              if x not in done_ids}
        def _prefix(x):
            return x.rsplit(':', 1)[0]
        # 续跑范围 = 原已派发计划（DISPATCH_PLAN.plan_len 项，冻结顺序的前 N 项），不是整个 204
        plan = plan[:prev['plan_len']]
        orphan_prefixes = {_prefix(x) for x in orphan_ids}
        done_prefixes = {_prefix(x) for x in done_ids}
        failed_prefixes = {_prefix(x) for x in failed_ids}
        skipped = done_prefixes | failed_prefixes | orphan_prefixes
        plan = [p for p in plan
                if ('judge:%s:%s:%s' % (p['rater'], p['node_id'], p['kind'])) not in skipped]
        expected_total = prev.get('plan_len') or CAP
        if len(plan) + len(skipped) != expected_total:
            raise SystemExit('拒绝启动：续跑去重后 %d + 已派发 %d ≠ 原计划 %d（计划被改动）'
                             % (len(plan), len(skipped), expected_total))
        resume_note = {'resumed_at_tag': tag,
                       'skipped_dispatched': sorted(skipped),
                       'of_which_failed_not_refilled': sorted(failed_prefixes),
                       'of_which_inflight_orphans_not_refilled': sorted(orphan_prefixes),
                       'remaining_plan': len(plan)}
        json.dump(resume_note,
                  open(os.path.join(outdir, 'RESUME_%s.json' % tag), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
    else:
        outdir = os.path.join(ROOT, 'logs', 'judge_v1_1_%s_%s' % (a.mode, tag))
        os.makedirs(outdir, exist_ok=True)

    # 端点固定（与 exam_interleave_plan 同款）：live 在派发前固定 env；mock 不触网
    if a.mode == 'live':
        os.environ['LB_API_BASE'] = PINNED_ENDPOINT
        os.environ['LB_MODEL'] = MODEL_ALIAS
        os.environ.setdefault('LB_API_KEY', 'local')

    cfg = {'system_sha256': sha(JUDGE_SYSTEM), 'model_alias': MODEL_ALIAS,
           'temperature': TEMPERATURE, 'stream': False,
           'api_config_identity': 'api_cfg:openai_compat_stream_false_v1',
           'endpoint': PINNED_ENDPOINT,
           'manual': 'MANUAL-V1.1', 'packet': 'CALIB-V1.1',
           'frozen_manifest_sha256': sha(open(frozen, encoding='utf-8').read()),
           'raters': 'R1/R2/R3 = 同一模型服务同一 prompt 的不同会话，不是三个独立模型',
           'cap': cap, 'timeout_s': a.timeout_s, 'concurrency': a.concurrency}
    if not a.resume_outdir:
        json.dump({'plan_len': len(plan), 'sampling': sampling, 'config': cfg},
                  open(os.path.join(outdir, 'DISPATCH_PLAN.json'), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
    else:
        prev_cfg = json.load(open(os.path.join(outdir, 'DISPATCH_PLAN.json'),
                                  encoding='utf-8')).get('config', {})
        for k in ('system_sha256', 'model_alias', 'temperature', 'stream',
                  'api_config_identity', 'endpoint', 'manual', 'packet'):
            if prev_cfg.get(k) != cfg.get(k):
                raise SystemExit('拒绝启动：续跑配置身份不符 %s: %r vs %r'
                                 % (k, prev_cfg.get(k), cfg.get(k)))

    budget, led = make_budget(a.mode, cap, tag)
    from arena.isolation.authorization_budget import BudgetRefused

    records, stopped = [], early

    manual_text = open(os.path.join(ROOT, 'evaluation', 'JUDGE_MANUAL_V1_1.md'), encoding='utf-8').read()
    system_full = JUDGE_SYSTEM + '\n\n---\n\n' + manual_text

    if stopped is None:
        # 整批预检：不足/已关/损坏在首发前拒绝（不得先跑到一半）
        try:
            budget.ensure_batch(len(plan), purpose='judge_calibration_v1_1_batch')
        except BudgetRefused as exc:
            stopped = {'reason': 'budget_refused', 'at': 0, 'detail': str(exc)}

    # ---- 派发单元（串行/并发共用）----
    io_lock = threading.Lock()          # 进程内串行化：budget reserve/record、RATINGS 落盘
    tls = threading.local()             # 每 worker 独立网关实例（事件账分文件，无共享写）

    def worker_gateway():
        if a.mode != 'live':
            return None
        gw_local = getattr(tls, 'gw', None)
        if gw_local is None:
            from arena.lb_gateway import ADAPTERS, ModelGateway
            gw_local = ModelGateway(
                ADAPTERS['openai_compat'](),
                event_log_path=os.path.join(outdir, 'GATEWAY_EVENTS_w%s.jsonl'
                                            % threading.current_thread().name))
            gw_local.set_run_context('judge_v1_1_%s' % tag, 'judge_batch')
            tls.gw = gw_local
        return gw_local

    def dispatch_one(i, p):
        """返回 (rec|None, stop_reason|None)。stop_reason 非空 → 不再提交新任务（fail-fast）。"""
        node = nodes[p['node_id']]
        user = judge_user(node, p['swapped'])
        attempt_id = 'judge:%s:%s:%s:%d' % (p['rater'], p['node_id'], p['kind'], i)
        try:
            with io_lock:
                budget.reserve(attempt_id, purpose='judge_calibration_v1_1')
        except BudgetRefused as exc:
            return None, {'reason': 'budget_refused', 'at': i, 'detail': str(exc)}
        t0 = time.time()
        usage, timing, err = None, None, None
        if a.mode == 'mock':
            if a.inject_fail_at is not None and i == a.inject_fail_at:
                raw, ok = 'INJECTED_TRANSPORT_FAIL', False
                err = 'injected (rehearsal fail-fast path)'
            else:
                raw, ok = stub_response(node, p['swapped'], p['rater'], i), True
        else:
            try:
                res = worker_gateway().generate(system=system_full, user=user, model=MODEL_ALIAS,
                                                temperature=TEMPERATURE, tag='judge',
                                                attempt_id=attempt_id, timeout_s=a.timeout_s)
                raw, ok = res['text'], True
                usage = res.get('usage')
                timing = {'non_stream': True,
                          'first_content_ms': None,
                          'first_content_reason': 'judge batch frozen non-streaming identity',
                          'gateway_timing': res.get('timing')}
            except Exception as exc:                       # noqa: BLE001
                raw, ok = '%s: %s' % (type(exc).__name__, exc), False
                err = raw
                usage, timing = None, {'non_stream': True, 'first_content_ms': None,
                                       'first_content_reason': 'error before usable response'}
        parsed, perr = None, None
        if ok:
            try:
                parsed = json.loads(raw)
            except Exception as exc:                       # noqa: BLE001
                perr = '%s' % exc
        rec = {'seq': i, 'rater': p['rater'], 'node_id': p['node_id'], 'swapped': p['swapped'],
               'kind': p['kind'], 'attempt_id': attempt_id,
               'authorization_id': budget.authorization_id, 'ledger': os.path.relpath(led, ROOT),
               'user_sha256': sha(user), 'raw_sha256': sha(raw), 'transport_ok': ok,
               'parse_ok': parsed is not None, 'parse_error': perr,
               'wall_ms': int((time.time() - t0) * 1000),
               'usage': usage, 'timing': timing, 'error': err, 'raw': raw}
        if parsed is not None:
            rec['parsed'] = parsed
            rec['pref'] = parsed.get('pref')
            rec['claims_n'] = len(parsed.get('claims') or [])
        try:
            with io_lock:
                if ok:
                    budget.record('attempt_completed', attempt_id,
                                  usage=usage, raw_sha256=rec['raw_sha256'])
                else:
                    budget.record('attempt_failed', attempt_id, error=(err or raw)[:200])
                with open(os.path.join(outdir, 'RATINGS_%s.jsonl' % p['rater']), 'a',
                          encoding='utf-8') as f:
                    f.write(json.dumps(rec, ensure_ascii=False) + '\n')
        except OSError as exc:
            return rec, {'reason': 'ledger_write_fail', 'at': i, 'detail': str(exc)}
        if not ok:
            return rec, {'reason': 'transport_fail_fast', 'at': i,
                         'attempt_id': attempt_id,
                         'failed_attempt_ids': [attempt_id],
                         'detail': (err or raw)[:200]}
        return rec, None

    if stopped is None and a.concurrency <= 1:
        for i, p in enumerate(plan):
            rec, stop_reason = dispatch_one(i, p)
            if rec is not None:
                records.append(rec)
            if stop_reason:
                stopped = stop_reason
                break
    elif stopped is None:
        # 并发驱动：渐进提交（冻结顺序不变），任一失败 → 停止提交新任务，在跑的跑完
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
                done, _ = concurrent.futures.wait(
                    pending, return_when=concurrent.futures.FIRST_COMPLETED)
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
               'stopped': stopped, 'config': cfg, 'outdir': os.path.relpath(outdir, ROOT),
               'resume': bool(a.resume_outdir),
               'budget': budget.summary()}
    summary_name = 'JUDGE_BATCH_SUMMARY_RESUME.json' if a.resume_outdir else 'JUDGE_BATCH_SUMMARY.json'
    json.dump(summary, open(os.path.join(outdir, summary_name), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('JUDGE BATCH mode=%s attempts=%d transport_fail=%d parse_fail=%d stopped=%s budget_used=%s'
          % (a.mode, len(records), summary['transport_fail'], summary['parse_fail'],
             (stopped or {}).get('reason'), summary['budget'].get('used')))
    if stopped is not None:
        sys.exit(2)


if __name__ == '__main__':
    main()
