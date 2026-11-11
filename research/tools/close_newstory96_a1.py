# -*- coding: utf-8 -*-
"""tools/close_newstory96_a1.py — 96批 A1 一键收口（M0，零模型调用）。

步骤：
  1. RESULT.json 校验：started/completed/failed、order、fail-fast 未触发；
  2. 逐位双射：96 turn 目录 ↔ 计划 96 位（frozen INTERLEAVE_PLAN.json sha16=cc19ab7daeb3fb05）；
  3. usage 全量=api_usage 抽全量核（不是抽查）；
  4. delivery_columns_v3 --final 终版六列；
  5. AUTH-NEWSTORY-96-20261026-01 按原 scope 关闭；
  6. 收口摘要 JSON（含 14 位 ADJUSTED 单列与改名口径）。
任何一步失败退出码非 0，不写收口文件。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, 'arena', 'isolation'))
sys.path.insert(0, ROOT)

BASE = os.path.join(ROOT, 'logs', 'newstory96_live_20261026')
RESULT = os.path.join(ROOT, 'logs', 'newstory96_live_20261026.RESULT.json')
PLAN = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'newstory_96_20261026',
                    'INTERLEAVE_PLAN.json')
LEDGER = os.path.join(ROOT, 'logs', 'auth_budget', 'AUTH-NEWSTORY-96-20261026-01.jsonl')


def step(n, ok, msg):
    print('[%s] %s %s' % ('PASS' if ok else 'FAIL', n, msg))
    if not ok:
        sys.exit(1)


def main():
    res = json.load(open(RESULT, encoding='utf-8'))
    step(1, res.get('attempt_started') == 96 and res.get('attempt_completed') == 96
         and res.get('attempt_failed') == 0 and res.get('execution_order_verified') is True
         and res.get('dispatch_bijection') is True,
         'RESULT started/completed/failed=%s/%s/%s order=%s bijection=%s'
         % (res.get('attempt_started'), res.get('attempt_completed'), res.get('attempt_failed'),
            res.get('execution_order_verified'), res.get('dispatch_bijection')))
    plan = json.load(open(PLAN, encoding='utf-8'))
    import glob
    turns = glob.glob(os.path.join(BASE, '*', 'out', 'turn_*'))
    step(2, len(turns) == 96, 'turn dirs=%d' % len(turns))
    missing = []
    for it in plan['attempts']:
        want = os.path.join(BASE, '%s_%s' % (it['arm'], it['episode']), 'out',
                            'turn_%02d_%s' % (it['turn'], it['case_id']))
        if not os.path.isdir(want):
            missing.append(want.replace(BASE, ''))
    step(2, not missing, 'plan→dir bijection missing=%s' % (missing[:3] if missing else '0'))
    bad_usage = []
    n_calls = 0
    for cj in glob.glob(os.path.join(BASE, '*', 'out', 'turn_*', 'calls.json')):
        calls = json.load(open(cj, encoding='utf-8'))
        for c in (calls if isinstance(calls, list) else [calls]):
            n_calls += 1
            if (c.get('usage') or {}).get('source') != 'api_usage':
                bad_usage.append(cj)
    step(3, n_calls == 96 and not bad_usage, 'usage api_usage %d/96 calls, bad=%d' % (n_calls, len(bad_usage)))

    import subprocess
    r = subprocess.run([sys.executable, os.path.join(HERE, 'delivery_columns_v3_newstory96.py'), '--final'],
                       capture_output=True, text=True, encoding='utf-8', env=dict(os.environ, PYTHONIOENCODING='utf-8'))
    step(4, r.returncode == 0, 'final columns RC=%d\n%s' % (r.returncode, r.stdout.strip()[-300:]))

    from arena.isolation.authorization_budget import AuthorizationBudget
    recs = [json.loads(l) for l in open(LEDGER, encoding='utf-8') if l.strip()]
    scope0 = [x for x in recs if x['kind'] == 'authorization_scope'][0]
    ab = AuthorizationBudget(LEDGER, authorization_id='AUTH-NEWSTORY-96-20261026-01',
                             max_attempts=scope0['max_attempts'], scope=scope0['scope'])
    if not ab.closed:
        ab.close('96批收口（A1）：96/96 完成 0 失败；实验定性=开发者新编故事迁移/正向能力实验'
                 '（非独立作者封存验证）；14 ADJUSTED 意图位点单列于终版六列；first_usable 未接入=如实 null')
    s = ab.summary()
    step(5, s['closed'] and s['used'] == 96, 'ledger closed=%s used=%s/%s' % (s['closed'], s['used'], s['max_attempts']))

    summary = {
        'doc': 'NEWSTORY96_A1_CLOSURE',
        'batch': 'BATCH-NEWSTORY-96-20261026',
        'result': {k: res.get(k) for k in ('attempt_started', 'attempt_completed', 'attempt_failed',
                                           'execution_order_verified', 'dispatch_bijection', 'real_dispatched')},
        'ledger': s,
        'columns_final': 'evaluation/DELIVERY_AND_COST_COLUMNS_NEWSTORY96_FINAL.json',
        'reframing': '开发者新编故事迁移/正向能力实验；14 位 intent.status=ADJUSTED（两臂闸门只认 ACTIVE，'
                     '目标块未注入）不得用作意图调整机制有效证据；P003 切=note 私定解释不推广为通用规则',
        'performance_note': 'first_usable_ms 全 96 位 null（普通链路未接消费回调，不以离线解析回填）；'
                            'first_preview/completion 见终版六列；D5 8 次专用测量是仅有的可用时间证据',
    }
    out = os.path.join(ROOT, 'evaluation', 'NEWSTORY96_A1_CLOSURE.json')
    json.dump(summary, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('[PASS] 6 closure summary ->', out)


if __name__ == '__main__':
    main()
