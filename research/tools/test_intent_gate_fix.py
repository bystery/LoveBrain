# -*- coding: utf-8 -*-
r"""tools/test_intent_gate_fix.py — P04-04 离线回归（零模型调用）。

验证 reference_product_v1 v1.2 的 is_intent_effective（基线/v5/C2/C3 共用闸门）：
  ①ACTIVE → 生效；②**ADJUSTED → 生效（本轮修复点：14/46 位点曾被判无效）**；
  ③CLOSED/COMPLETED/EXPIRED/PAUSED → 不生效（终态语义不松动）；
  ④enabled=false / 空text → 不生效；⑤status 缺省 → 按 ACTIVE（v1.0 兼容行为不变）；
  ⑥build_intent_block 对 ADJUSTED 输出【持续意图】块（端到端）。
运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\test_intent_gate_fix.py
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CHAIN = os.path.join(HERE, '..', 'candidates', 'reference_product_v1', 'reference_chain.py')
spec = importlib.util.spec_from_file_location('ref_chain_intent_test', CHAIN)
rc_mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rc_mod)

FAILS = []


def check(name, cond, detail=''):
    print('%s %s %s' % ('PASS' if cond else 'FAIL', name, detail))
    if not cond:
        FAILS.append(name)


def cfg(status, text='和她认真聊观点', enabled=True):
    return {'enabled': enabled, 'text': text, 'status': status}


check('① ACTIVE 生效', rc_mod.is_intent_effective(cfg('ACTIVE')) is True)
check('② ADJUSTED 生效（本轮修复点）', rc_mod.is_intent_effective(cfg('ADJUSTED')) is True,
      'v1.0 曾返回 False（14/46 位点共同缺口）')
check('③a CLOSED 不生效', rc_mod.is_intent_effective(cfg('CLOSED')) is False)
check('③b COMPLETED 不生效', rc_mod.is_intent_effective(cfg('COMPLETED')) is False)
check('③c EXPIRED 不生效', rc_mod.is_intent_effective(cfg('EXPIRED')) is False)
check('③d PAUSED 不生效', rc_mod.is_intent_effective(cfg('PAUSED')) is False)
check('④a enabled=false 不生效', rc_mod.is_intent_effective(cfg('ACTIVE', enabled=False)) is False)
check('④b 空text 不生效', rc_mod.is_intent_effective(cfg('ACTIVE', text='  ')) is False)
check('⑤ status 缺省按 ACTIVE（v1.0 兼容）', rc_mod.is_intent_effective({'enabled': True, 'text': 'x'}) is True)
blk = rc_mod.build_intent_block(cfg('ADJUSTED', text='她松口了 改成陪她挑不吵的场子'))
check('⑥ build_intent_block 注入 ADJUSTED 目标', '持续意图' in blk and '挑不吵的场子' in blk,
      'block=%r' % blk[:40])
blk_closed = rc_mod.build_intent_block(cfg('CLOSED'))
check('⑥b CLOSED 不产生意图块', blk_closed == '')

print('---')
if FAILS:
    print('FAILED: %d checks: %s' % (len(FAILS), FAILS))
    sys.exit(1)
print('ALL PASS (11 checks)')
