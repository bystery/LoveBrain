# -*- coding: utf-8 -*-
r"""tools/test_evidence_memory_static_seeds.py — F3 收尾回归（零模型调用）。

验证 evidence_memory_v1 v1.2 的 _seed_static_facts（静态知识入库）与投影排除：
  ①initial_kb 标量（her/warmth）与 persona.me/her 以 origin=kbin:/persona: 入库，status=confirmed_seed；
  ②投影排除：kbin:/persona: 种子不进 _facts_projection（防与 seed 块重复注入），
    ongoing_plan 种子（origin=initial_kb）仍正常投影；
  ③成员验证（v1.1）不受影响。
运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\test_evidence_memory_static_seeds.py
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECH = os.path.join(HERE, '..', 'candidates', 'evidence_memory_v1', 'mechanism', 'mechanism.py')
spec = importlib.util.spec_from_file_location('evm_f3_test', MECH)
mech = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mech)

FAILS = []


def check(name, cond, detail=''):
    print('%s %s %s' % ('PASS' if cond else 'FAIL', name, detail))
    if not cond:
        FAILS.append(name)


h = {'mem': {'facts': {}, 'tentatives': {}, 'goal_history': []}}
mech._seed_static_facts(h,
                        {'her': '她妹妹在读研，最近准备答辩',
                         'warmth': '共同朋友小林是当初牵线认识的那位'},
                        {'me': '32岁，男，武汉，做医生',
                         'her': '31岁，女，武汉，做老师'})
ks = sorted(h['mem']['facts'].keys())
check('① 静态知识入库（4 条）',
      ks == ['kbin:her', 'kbin:warmth', 'persona:her', 'persona:me'], 'keys=%s' % ks)
check('①b warmth 带 src 标签与状态',
      h['mem']['facts']['kbin:warmth']['origin'] == 'kbin:warmth'
      and h['mem']['facts']['kbin:warmth']['status'] == 'confirmed_seed')

# ② 投影排除
h['mem']['facts'][mech._fact_key('11-18 咖啡见面')] = {
    'text': '11-18 咖啡见面', 'src_uids': [], 'ts': '', 'status': 'confirmed_seed',
    'origin': 'initial_kb', 'plan_status': '进行中'}
body, used, hits, filtered, _ms = mech._facts_projection(h, '咖啡 见面', 500)
check('②a ongoing_plan 种子仍投影', hits == 1 and '咖啡' in body.replace(' ', ''),
      'hits=%d body=%r' % (hits, body[:40]))
body2, _u, hits2, _f, _m2 = mech._facts_projection(h, '小林 牵线 认识', 500)
check('②b kbin/persona 静态种子不重复投影', hits2 == 0, 'hits=%d body=%r' % (hits2, body2[:40]))

# ③ v1.1 成员验证不受影响
a, t, inv, uns = mech._merge_reported(
    h, {'facts': [{'text': '完全无关的内容哦', 'src_ids': [999]}]},
    {101}, 't', {'m0': 101}, {101: '某消息'})
check('③ 成员验证仍生效', inv == 1 and uns == 0, 'inv=%d unsupported=%d' % (inv, uns))

print('---')
if FAILS:
    print('FAILED: %d checks: %s' % (len(FAILS), FAILS))
    sys.exit(1)
print('ALL PASS (5 checks)')
