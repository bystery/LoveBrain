# -*- coding: utf-8 -*-
r"""tools/test_candidate_contract_consistency.py — P04-05 离线回归（零模型调用）。

验证 C2/C3 的 system 资产（format.md）与各自 mechanism 的 analysis 消费契约一致：
  ①C3：schema 给 facts/tentatives 键、## analysis 不再"固定输出空对象"、与 mechanism 的
    ANALYSIS_CONTRACT 键集一致（facts/tentatives/src_ids）；
  ②C2：schema 给三字段键、## analysis 不再"固定输出空对象"、与 OUTPUT_CONTRACT 键集一致
    （feedback_signal/goal_category/action_taken）；
  ③v5 资产零改动：仍保留"固定输出空对象 {}"原语义（v5 不消费 analysis，其契约本来就一致）；
  ④改动最小性：C2/C3 format.md 与 v5 原版的差异仅限 analysis 相关行（其他行逐字相同）。
运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\test_candidate_contract_consistency.py
"""
import difflib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
V5 = os.path.join(ROOT, 'candidates', 'v5_purpose_aligned', 'assets', 'system_prompt', 'format.md')
C2 = os.path.join(ROOT, 'candidates', 'goal_action_v1', 'assets', 'system_prompt', 'format.md')
C3 = os.path.join(ROOT, 'candidates', 'evidence_memory_v1', 'assets', 'system_prompt', 'format.md')
C2_MECH = os.path.join(ROOT, 'candidates', 'goal_action_v1', 'mechanism', 'mechanism.py')
C3_MECH = os.path.join(ROOT, 'candidates', 'evidence_memory_v1', 'mechanism', 'mechanism.py')

FAILS = []


def check(name, cond, detail=''):
    print('%s %s %s' % ('PASS' if cond else 'FAIL', name, detail))
    if not cond:
        FAILS.append(name)


v5 = open(V5, encoding='utf-8').read()
c2 = open(C2, encoding='utf-8').read()
c3 = open(C3, encoding='utf-8').read()
c2m = open(C2_MECH, encoding='utf-8').read()
c3m = open(C3_MECH, encoding='utf-8').read()

# ① C3 一致
check('①a C3 schema 有 facts/tentatives 键', '"analysis": {"facts": [], "tentatives": []}' in c3)
check('①b C3 不再固定空对象', '固定输出空对象' not in c3)
check('①c C3 与机制契约键集一致',
      all(k in c3 for k in ('facts', 'tentatives', 'src_ids'))
      and '"facts":[{"text"' in c3m and 'tentatives' in c3m)

# ② C2 一致
check('②a C2 schema 有三字段键',
      '"analysis": {"feedback_signal": "", "goal_category": "", "action_taken": ""}' in c2)
check('②b C2 不再固定空对象', '固定输出空对象' not in c2)
check('②c C2 与机制契约键集一致',
      all(k in c2 for k in ('feedback_signal', 'goal_category', 'action_taken'))
      and all(k in c2m for k in ('feedback_signal', 'goal_category', 'action_taken')))

# ③ v5 零改动
check('③ v5 仍为固定空对象原语义（未动）',
      '固定输出空对象' in v5 and '"analysis": {}' in v5)

# ④ 最小 diff：改前副本与 v5 逐字节相同；改前→改后每个变更行都含 analysis 相关词
def changed_lines(old, new):
    out = []
    for ln in difflib.unified_diff(old.splitlines(), new.splitlines(), lineterm=''):
        if ln[:1] in '+-' and ln[:3] not in ('+++', '---'):
            out.append(ln)
    return out

C2_BAK = C2 + '.bak-20261027-0535-cron15-p0405'
C3_BAK = C3 + '.bak-20261027-0535-cron15-p0405'
c2b = open(C2_BAK, encoding='utf-8').read()
c3b = open(C3_BAK, encoding='utf-8').read()
KEYS = ('analysis', 'topic_status', 'scene_facts', 'ongoing', 'feedback_signal',
        'goal_category', 'action_taken', 'facts', 'tentatives', 'src_ids',
        '记忆提取', '自评与所选动作', '机制会消费', '固定输出空对象', '正文四风格')
check('④a0 C2 改前副本与 v5 逐字节相同', c2b == v5)
check('④b0 C3 改前副本与 v5 逐字节相同', c3b == v5)
d2 = changed_lines(c2b, c2)
d3 = changed_lines(c3b, c3)
check('④a C2 仅 analysis 相关行变更', d2 and all(any(k in ln for k in KEYS) for ln in d2),
      'n=%d sample=%s' % (len(d2), (d2 or [''])[:1]))
check('④b C3 仅 analysis 相关行变更', d3 and all(any(k in ln for k in KEYS) for ln in d3),
      'n=%d sample=%s' % (len(d3), (d3 or [''])[:1]))

print('---')
if FAILS:
    print('FAILED: %d checks: %s' % (len(FAILS), FAILS))
    sys.exit(1)
print('ALL PASS (10 checks)')
