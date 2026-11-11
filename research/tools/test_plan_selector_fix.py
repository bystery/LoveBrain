# -*- coding: utf-8 -*-
r"""tools/test_plan_selector_fix.py — P04-01 离线回归（零模型调用）。

验证 reference_product_v1 v1.1 的 extract_keywords / extract_event_date 修订：
  正例（此前全坏，来自 QUALITY_FIRST_MAIN_REPORT §5.2 实测根因）：
    ①"约饭（时间待定）" → ["约饭"]（v1.0 = ["饭时间待定"]，前导"约"被剥+括注内容混入）
    ②"2026-11-14周六看后摇乐队livehouse" → ["看后摇乐队livehouse"]（v1.0 = ["20-14看后摇…"]）
  反例保护（不得修坏）：
    ③"周末咖啡" → ["周末咖啡"]；④"计划下周三聚会" 仍剥"计划"；
    ⑤extract_event_date("2026-11-14…") = 2026-11-14（v1.0 = 2026-26-11 非法月）；
    ⑥端到端：compute_relevance_signals 用"约饭（时间待定）"对真实消息"这周六想约饭吗" → 证据命中。
运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\test_plan_selector_fix.py
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CHAIN = os.path.join(HERE, '..', 'candidates', 'reference_product_v1', 'reference_chain.py')
spec = importlib.util.spec_from_file_location('ref_chain_test', CHAIN)
rc_mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rc_mod)

FAILS = []


def check(name, cond, detail=''):
    print('%s %s %s' % ('PASS' if cond else 'FAIL', name, detail))
    if not cond:
        FAILS.append(name)


# ①约饭（时间待定）
kw = rc_mod.extract_keywords('约饭（时间待定）')
check('① 约饭（时间待定） -> 约饭', kw == ['约饭'], 'got %r (v1.0=[\'饭时间待定\'])' % kw)

# ② 全日期+周六
kw2 = rc_mod.extract_keywords('2026-11-14周六看后摇乐队livehouse')
check('② 2026-11-14周六看后摇… -> 看后摇乐队livehouse', kw2 == ['看后摇乐队livehouse'],
      'got %r' % kw2)

# ③ 保护：无括注普通条目
kw3 = rc_mod.extract_keywords('周末咖啡')
check('③ 周末咖啡 不受影响', kw3 == ['周末咖啡'], 'got %r' % kw3)

# ④ 保护：计划 前缀仍剥
kw4 = rc_mod.extract_keywords('计划下周三聚会')
check('④ 计划前缀仍剥', '聚会' in ''.join(kw4) and not any(k.startswith('计划') for k in kw4),
      'got %r' % kw4)

# ⑤ extract_event_date 全日期不再产出非法月
d1 = rc_mod.extract_event_date('2026-11-14周六看后摇乐队livehouse')
check('⑤ event_date 全日期 = 2026-11-14', d1 == '2026-11-14', 'got %r (v1.0=2026-26-11)' % d1)
d2 = rc_mod.extract_event_date('11-18 咖啡见面')
check('⑤b event_date 月-日 = 2026-11-18', d2 == '2026-11-18', 'got %r' % d2)

# ⑥ 端到端相关信号：修复后 约饭 能命中真实消息
sig = rc_mod.compute_relevance_signals({'name': '约饭（时间待定）'},
                                       '这周六想约饭吗 你定地方', '', '')
check('⑥ 端到端 realMessageEvidence', sig['realMessageEvidence'] is True, 'got %r' % sig)

# ⑦ 保护：无关消息不误召回（fail-closed 设计不被破坏）
sig2 = rc_mod.compute_relevance_signals({'name': '约饭（时间待定）'},
                                        '我周末要加班 烦死了', '', '')
check('⑦ 无关消息不误召回', sig2['realMessageEvidence'] is False, 'got %r' % sig2)

print('---')
if FAILS:
    print('FAILED: %d checks: %s' % (len(FAILS), FAILS))
    sys.exit(1)
print('ALL PASS (8 checks)')
