# -*- coding: utf-8 -*-
r"""tools/test_evidence_memory_membership.py — P04-02 离线回归（零模型调用）。

验证 evidence_memory_v1 v1.1 的 _merge_reported：
  ①成员硬验证：裸 int（不在当前轮）、空 src_ids 一律拒绝（v1.0 曾全部放行入 confirmed 库）；
  ②引用支持度：事实文本与所引消息无子串/低重合 → status=unverified_src（只存不投影）；
  ③有据引用（子串/重合达 0.15）→ confirmed；
  ④投影排除：unverified_src 不进 _facts_projection；
  ⑤tentatives 同样受成员验证；⑥重复报告且有据 → 状态刷新不重复计数。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\test_evidence_memory_membership.py
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECH = os.path.join(HERE, '..', 'candidates', 'evidence_memory_v1', 'mechanism', 'mechanism.py')
spec = importlib.util.spec_from_file_location('evm_mech_test', MECH)
mech = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mech)

FAILS = []


def check(name, cond, detail=''):
    print('%s %s %s' % ('PASS' if cond else 'FAIL', name, detail))
    if not cond:
        FAILS.append(name)


def fresh_h():
    return {"mem": {"facts": {}, "tentatives": {}, "goal_history": []}}


CUR_UIDS = {101, 102}
ALIAS = {'m0': 101, 'm1': 102}
CUR_TEXTS = {101: '不是读书会 是以前同事介绍 你问这个干嘛',
             102: '哦我记岔了'}

# ①成员硬验证：裸 int 999（v1.0 的洞）
h = fresh_h()
a, t, inv, uns = mech._merge_reported(
    h, {"facts": [{"text": "我跟小林是旅游认识", "src_ids": [999]}]},
    CUR_UIDS, '2026-11-10 12:00', ALIAS, CUR_TEXTS)
check('bare-int-999 rejected', inv == 1 and len(h['mem']['facts']) == 0,
      'inv=%d stored=%d' % (inv, len(h['mem']['facts'])))

# ①空 src_ids
h = fresh_h()
a, t, inv, uns = mech._merge_reported(
    h, {"facts": [{"text": "随便什么事实", "src_ids": []}]},
    CUR_UIDS, '2026-11-10 12:00', ALIAS, CUR_TEXTS)
check('empty-src rejected', inv == 1 and len(h['mem']['facts']) == 0,
      'inv=%d stored=%d' % (inv, len(h['mem']['facts'])))

# ③有据引用（规范化子串命中）→ confirmed
h = fresh_h()
a, t, inv, uns = mech._merge_reported(
    h, {"facts": [{"text": "不是读书会 是以前同事介绍", "src_ids": ["m0"]}]},
    CUR_UIDS, '2026-11-10 12:00', ALIAS, CUR_TEXTS)
k = mech._fact_key('不是读书会 是以前同事介绍')
check('supported fact confirmed', a == 1 and h['mem']['facts'].get(k, {}).get('status') == 'confirmed',
      'added_f=%d status=%s' % (a, h['mem']['facts'].get(k, {}).get('status')))

# ②引用存在但内容不受支持 → unverified_src（只存不投影）
h = fresh_h()
a, t, inv, uns = mech._merge_reported(
    h, {"facts": [{"text": "我们去年一起去日本旅游过", "src_ids": ["m0"]}]},
    CUR_UIDS, '2026-11-10 12:00', ALIAS, CUR_TEXTS)
k2 = mech._fact_key('我们去年一起去日本旅游过')
check('unsupported -> unverified_src',
      a == 0 and uns == 1 and h['mem']['facts'].get(k2, {}).get('status') == 'unverified_src',
      'added_f=%d unsupported=%d status=%s' % (a, uns, h['mem']['facts'].get(k2, {}).get('status')))

# ④投影排除：unverified_src 不进 _facts_projection（无信号时只取 confirmed）
body, used, hits, filtered, _ms = mech._facts_projection(h, "", 500)
check('projection excludes unverified_src', hits == 0 and body == '',
      'hits=%d body=%r' % (hits, body[:40]))

# ⑤tentatives 同样受成员验证
h = fresh_h()
a, t, inv, uns = mech._merge_reported(
    h, {"tentatives": [{"text": "周六再看状态", "src_ids": [12345]}]},
    CUR_UIDS, '2026-11-10 12:00', ALIAS, CUR_TEXTS)
check('tentative bare-int rejected', inv == 1 and len(h['mem']['tentatives']) == 0,
      'inv=%d stored=%d' % (inv, len(h['mem']['tentatives'])))

# ⑥重复报告且有据 → 状态刷新、不重复计数
h = fresh_h()
txt = "不是读书会 是以前同事介绍"
mech._merge_reported(h, {"facts": [{"text": txt, "src_ids": ["m0"]}]}, CUR_UIDS, 'c1', ALIAS, CUR_TEXTS)
a2, t2, inv2, uns2 = mech._merge_reported(
    h, {"facts": [{"text": txt, "src_ids": ["m0"]}]}, CUR_UIDS, 'c2', ALIAS, CUR_TEXTS)
check('re-report refreshes without dup', len(h['mem']['facts']) == 1 and a2 == 0,
      'store=%d added_f2=%d' % (len(h['mem']['facts']), a2))

print('---')
if FAILS:
    print('FAILED: %d checks: %s' % (len(FAILS), FAILS))
    sys.exit(1)
print('ALL PASS (7 checks)')
