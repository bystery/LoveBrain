# -*- coding: utf-8 -*-
"""tools/test_judge_batch_v1_1_mock.py — run_judge_batch_v1_1.py 的 mock 验收（零网络）。

验收口径（HANDOFF §5 / WORKER_EXECUTION 包C）：
  T1 计划=204：全量 mock 跑通，plan_len=204、204 条落盘、账本 used=204、attempt_id 全局唯一、
     逐调用字段齐（user_sha256/raw_sha256/transport_ok/parse_ok/wall_ms/usage/parsed）；
  T2 预算拒超：--cap 3 --limit 9 → ensure_batch 首发前拒绝（exit=2），零判词落盘、账本 used=0；
  T3 失败停批：--inject-fail-at 4 --limit 9 → transport_fail_fast，第 4 位之后零派发，
     账本含 1 条 attempt_failed，used=5；
  T4 账本隔离：全部 mock 运行后，真实账本 AUTH-EVAL-JUDGE-20261025-01.jsonl 仍不存在。

运行：set PYTHONIOENCODING=utf-8 && D:\\anaconda\\python.exe D:\\lovebrain-arena\\tools\\test_judge_batch_v1_1_mock.py
"""
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPT = os.path.join(ROOT, 'tools', 'run_judge_batch_v1_1.py')
REAL_LEDGER = os.path.join(ROOT, 'logs', 'auth_budget', 'AUTH-EVAL-JUDGE-20261025-01.jsonl')
PY = sys.executable

RESULTS = []


def check(name, cond, detail=''):
    RESULTS.append((name, bool(cond), detail))
    print('%s %s %s' % ('PASS' if cond else 'FAIL', name, detail))


def run_batch(args):
    env = dict(os.environ)
    env['PYTHONIOENCODING'] = 'utf-8'
    env.pop('LB_ALLOW_LIVE_JUDGE', None)
    r = subprocess.run([PY, SCRIPT] + args, capture_output=True, text=True,
                       encoding='utf-8', env=env, timeout=300, cwd=ROOT)
    return r


def load_summary(outdir):
    return json.load(open(os.path.join(ROOT, outdir, 'JUDGE_BATCH_SUMMARY.json'), encoding='utf-8'))


def ratings_lines(outdir):
    d = os.path.join(ROOT, outdir)
    out = []
    for fn in sorted(os.listdir(d)):
        if fn.startswith('RATINGS_') and fn.endswith('.jsonl'):
            out.extend(json.loads(l) for l in open(os.path.join(d, fn), encoding='utf-8') if l.strip())
    return out


def ledger_records(path):
    if not os.path.isfile(path):
        return None
    return [json.loads(l) for l in open(path, encoding='utf-8') if l.strip()]


def main():
    import time
    ts_tag = str(int(time.time() * 1000) % 10 ** 10)   # 每次套件运行唯一，避免撞遗留演练账本
    TAG2B = 'rejdyn_' + ts_tag
    TAG3 = 'failfast_' + ts_tag
    TAG5 = 'res_' + ts_tag

    # ---------- T1 全量 204 ----------
    r1 = run_batch(['--mode', 'mock'])
    check('T1 exit=0', r1.returncode == 0, 'rc=%s out=%s' % (r1.returncode, (r1.stdout + r1.stderr)[-300:]))
    m1 = r1.stdout.strip().splitlines()[-1] if r1.stdout.strip() else ''
    outdir1 = 'logs/judge_v1_1_mock_' + m1.split('mode=mock ')[0].split()[-1] if False else None
    # 从 stdout 拿不到 outdir 时扫最新的时间戳 mock 目录（排除 --tag 固定目录）
    import re
    cand = [d for d in os.listdir(os.path.join(ROOT, 'logs'))
            if re.match(r'^judge_v1_1_mock_\d{8}_\d{6}$', d)]
    cand.sort(key=lambda d: os.path.getmtime(os.path.join(ROOT, 'logs', d)))
    outdir1 = os.path.join('logs', cand[-1])
    s1 = load_summary(outdir1)
    check('T1 plan_len=204', s1['planned'] == 204, 'planned=%s' % s1['planned'])
    check('T1 attempts=204', s1['attempts'] == 204, 'attempts=%s' % s1['attempts'])
    check('T1 stopped=None', s1['stopped'] is None, 'stopped=%s' % s1['stopped'])
    recs1 = ratings_lines(outdir1)
    check('T1 落盘 204 条', len(recs1) == 204, 'lines=%d' % len(recs1))
    ids = [r['attempt_id'] for r in recs1]
    check('T1 attempt_id 唯一', len(set(ids)) == 204)
    check('T1 attempt_id 格式 judge:rater:node:kind:seq',
          all(isinstance(x, str) and x.startswith('judge:') for x in ids))
    need = ('user_sha256', 'raw_sha256', 'transport_ok', 'parse_ok', 'wall_ms', 'usage', 'parsed')
    check('T1 逐调用字段齐',
          all(all(k in r for k in need) for r in recs1),
          '缺字段示例=%s' % next((r['seq'] for r in recs1 if any(k not in r for k in need)), None))
    check('T1 parse 全过', all(r['parse_ok'] for r in recs1))
    led1 = ledger_records(os.path.join(ROOT, s1['budget']['ledger']))
    started = [x for x in led1 if x.get('kind') == 'attempt_started']
    completed = [x for x in led1 if x.get('kind') == 'attempt_completed']
    scope = [x for x in led1 if x.get('kind') == 'authorization_scope']
    check('T1 账本 used=204', s1['budget']['used'] == 204 and len(started) == 204)
    check('T1 账本 completed=204', len(completed) == 204)
    check('T1 账本 scope 已绑定', len(scope) == 1 and scope[0]['scope'].get('scope_id') == 'evaluation_judge_calibration_v1_1')
    check('T1 账本与落盘一一对应',
          sorted(x['attempt_id'] for x in started) == sorted(ids))
    check('T1 演练账本目录', 'auth_budget_rehearsal' in s1['budget']['ledger'].replace('\\', '/'))

    # ---------- T2 预算拒超（静态路径：计划 > cap，零派发） ----------
    r2 = run_batch(['--mode', 'mock', '--cap', '3', '--limit', '9', '--tag', 'rej_static'])
    check('T2 exit=2', r2.returncode == 2, 'rc=%s' % r2.returncode)
    outdir2 = os.path.join('logs', 'judge_v1_1_mock_rej_static')
    s2 = load_summary(outdir2)
    check('T2 stopped=budget_refused', (s2['stopped'] or {}).get('reason') == 'budget_refused',
          'stopped=%s' % s2['stopped'])
    check('T2 零判词', len(ratings_lines(outdir2)) == 0)
    led2 = ledger_records(os.path.join(ROOT, s2['budget']['ledger']))
    used2 = 0 if led2 is None else sum(1 for x in led2 if x.get('kind') == 'attempt_started')
    check('T2 静态拒超零占额（账本可不存在）', used2 == 0, 'used=%d' % used2)

    # ---------- T2b 动态拒超（复用同一演练账本，ensure_batch 首发前拒绝） ----------
    args_rej = ['--mode', 'mock', '--cap', '3', '--limit', '3', '--tag', TAG2B]
    rb1 = run_batch(args_rej)
    check('T2b 第一次 exit=0', rb1.returncode == 0, 'rc=%s out=%s' % (rb1.returncode, rb1.stdout[-200:]))
    rb2 = run_batch(args_rej)
    check('T2b 复用已用账本 exit=2', rb2.returncode == 2, 'rc=%s out=%s' % (rb2.returncode, rb2.stdout[-200:]))
    s2b = load_summary(os.path.join('logs', 'judge_v1_1_mock_' + TAG2B))
    check('T2b stopped=budget_refused', (s2b['stopped'] or {}).get('reason') == 'budget_refused',
          'stopped=%s' % s2b['stopped'])
    check('T2b 首轮判词保留 3 条、复用轮零新增',
          len(ratings_lines(os.path.join('logs', 'judge_v1_1_mock_' + TAG2B))) == 3)
    led2b = ledger_records(os.path.join(ROOT, s2b['budget']['ledger']))
    rejected = [x for x in led2b if x.get('kind') == 'batch_rejected']
    used2b = sum(1 for x in led2b if x.get('kind') == 'attempt_started')
    check('T2b 账本 used=3 且有 batch_rejected', used2b == 3 and len(rejected) == 1,
          'used=%d rejected=%d' % (used2b, len(rejected)))

    # ---------- T3 失败停批 ----------
    r3 = run_batch(['--mode', 'mock', '--inject-fail-at', '4', '--limit', '9', '--tag', TAG3])
    check('T3 exit=2', r3.returncode == 2, 'rc=%s' % r3.returncode)
    cand = sorted(d for d in os.listdir(os.path.join(ROOT, 'logs'))
                  if d.startswith('judge_v1_1_mock_'))
    outdir3 = os.path.join('logs', 'judge_v1_1_mock_' + TAG3)
    s3 = load_summary(outdir3)
    check('T3 stopped=transport_fail_fast@4',
          (s3['stopped'] or {}).get('reason') == 'transport_fail_fast' and s3['stopped'].get('at') == 4,
          'stopped=%s' % s3['stopped'])
    recs3 = ratings_lines(outdir3)
    check('T3 落盘=5（0..4，第4位之后零派发）', len(recs3) == 5, 'lines=%d' % len(recs3))
    check('T3 第4位 transport_ok=False 且 parse 跳过',
          recs3[4]['transport_ok'] is False and recs3[4]['parse_ok'] is False)
    led3 = ledger_records(os.path.join(ROOT, s3['budget']['ledger']))
    started3 = [x for x in led3 if x.get('kind') == 'attempt_started']
    failed3 = [x for x in led3 if x.get('kind') == 'attempt_failed']
    check('T3 账本 used=5 且含 1 条 attempt_failed',
          s3['budget']['used'] == 5 and len(started3) == 5 and len(failed3) == 1)

    # ---------- T4 账本隔离 ----------
    # 真实账本可能已因 live 批存在；断言 mock 全程零新增占额
    real_used_before = 0
    if os.path.exists(REAL_LEDGER):
        real_used_before = sum(1 for l in open(REAL_LEDGER, encoding='utf-8')
                               if l.strip() and json.loads(l).get('kind') == 'attempt_started')
    check('T4 前置快照', True, 'real_used_before=%d' % real_used_before)

    # ---------- T5 续跑：失败项永不补跑，剩余计划继续 ----------
    args_r = ['--mode', 'mock', '--inject-fail-at', '4', '--limit', '9', '--tag', TAG5]
    run_batch(args_r)                                   # 第一段：0..4，第4位失败停批
    r5 = run_batch(['--mode', 'mock', '--tag', TAG5, '--resume-outdir',
                    os.path.join(ROOT, 'logs', 'judge_v1_1_mock_' + TAG5)])
    check('T5 续跑 exit=0', r5.returncode == 0, 'rc=%s out=%s' % (r5.returncode, r5.stdout[-200:]))
    outdir5 = os.path.join('logs', 'judge_v1_1_mock_' + TAG5)
    recs5 = ratings_lines(outdir5)
    check('T5 总落盘=9（5+4，失败项未重发）', len(recs5) == 9, 'lines=%d' % len(recs5))
    prefixes = [r['attempt_id'].rsplit(':', 1)[0] for r in recs5]
    check('T5 attempt 前缀无重复', len(set(prefixes)) == len(prefixes))
    s5_orig = json.load(open(os.path.join(ROOT, outdir5, 'JUDGE_BATCH_SUMMARY.json'), encoding='utf-8'))
    check('T5 原停批 summary 保留', (s5_orig.get('stopped') or {}).get('reason') == 'transport_fail_fast')
    s5 = json.load(open(os.path.join(ROOT, outdir5, 'JUDGE_BATCH_SUMMARY_RESUME.json'), encoding='utf-8'))
    check('T5 续跑 summary stopped=None', s5.get('stopped') is None)
    res_notes = [fn for fn in os.listdir(os.path.join(ROOT, outdir5)) if fn.startswith('RESUME_')]
    check('T5 RESUME 记录在位', len(res_notes) >= 1)
    led5 = ledger_records(os.path.join(ROOT, s5['budget']['ledger']))
    used5 = sum(1 for x in led5 if x.get('kind') == 'attempt_started')
    failed5 = [x for x in led5 if x.get('kind') == 'attempt_failed']
    check('T5 账本 used=9 且仍只有 1 条 attempt_failed（失败未重发）',
          used5 == 9 and len(failed5) == 1, 'used=%d failed=%d' % (used5, len(failed5)))
    if os.path.exists(REAL_LEDGER):
        real_used_after = sum(1 for l in open(REAL_LEDGER, encoding='utf-8')
                              if l.strip() and json.loads(l).get('kind') == 'attempt_started')
        check('T4 真实账本 mock 全程零新增占额', real_used_after == real_used_before,
              'before=%d after=%d' % (real_used_before, real_used_after))
    else:
        check('T4 真实账本仍未创建（mock 零占用）', True)

    # ---------- T6 并发派发：无交错损坏、账一一对应 ----------
    TAG6 = 'conc_' + ts_tag
    r6 = run_batch(['--mode', 'mock', '--limit', '24', '--concurrency', '4', '--tag', TAG6])
    check('T6 并发 exit=0', r6.returncode == 0, 'rc=%s out=%s' % (r6.returncode, r6.stdout[-160:]))
    outdir6 = os.path.join('logs', 'judge_v1_1_mock_' + TAG6)
    recs6 = ratings_lines(outdir6)
    check('T6 并发落盘=24 且每行可解析', len(recs6) == 24)
    ids6 = [r['attempt_id'] for r in recs6]
    check('T6 并发 attempt 唯一', len(set(ids6)) == 24)
    s6 = load_summary(outdir6)
    led6 = ledger_records(os.path.join(ROOT, s6['budget']['ledger']))
    st6 = [x['attempt_id'] for x in led6 if x.get('kind') == 'attempt_started']
    check('T6 并发账本一一对应', s6['budget']['used'] == 24 and sorted(st6) == sorted(ids6))

    n_fail = sum(1 for _, ok, _ in RESULTS if not ok)
    print('\nSUMMARY: %d checks, %d failed' % (len(RESULTS), n_fail))
    sys.exit(1 if n_fail else 0)


if __name__ == '__main__':
    main()
