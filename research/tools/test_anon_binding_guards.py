# -*- coding: utf-8 -*-
"""test_anon_binding_guards.py — v13 复核 §7 防复发断言自测。

复现独立复核的两个攻击（改动 episode 说话人 / 改动意图仍放行），证明 v3 生成器
现在会拒绝；另覆盖 clock 缺失与回复双侧绑定不一致两个新守卫。零文件写入。
"""
import copy
import importlib.util
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(_HERE)
sys.path.insert(0, os.path.join(_ROOT, "tools"))
sys.path.insert(0, os.path.join(_ROOT, "arena"))

spec = importlib.util.spec_from_file_location(
    "gag", os.path.join(_ROOT, "tools", "gen_anon_groups.py"))
gag = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gag)

fails = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  " + str(detail)))
    if not ok:
        fails.append(name)


def must_reject(name, mutate):
    ep = copy.deepcopy(gag.EPISODES["NCA"])
    mutate(ep)
    old = gag.EPISODES["NCA"]
    gag.EPISODES["NCA"] = ep
    try:
        gag._assert_binding("NCA", "NCA_t01", "A1", "r1")
        check(name, False, "未拒绝")
    except SystemExit as e:
        check(name, True, str(e).strip()[:80])
    finally:
        gag.EPISODES["NCA"] = old


# 1. 改动 episode 说话人 → 必须拒绝（v2 曾放行）
must_reject("1/说话人改动被拒",
            lambda ep: ep["turns"][0]["input"]["messages"][0].update(role="ME"))

# 2. 改动 episode 意图 → 必须拒绝（v2 曾放行）
must_reject("2/意图改动被拒",
            lambda ep: ep["turns"][0]["input"]["intent"].update(text="完全不同的意图"))

# 3. episode 意图文本与请求头不匹配（enabled 但 text 不同）→ 拒绝
must_reject("3/意图启用但文本不符被拒",
            lambda ep: ep["turns"][0]["input"]["intent"].update(text="她分享周末出行 帮我看看怎么回 "))

# 4. clock 缺失（episode clock 改掉）→ 拒绝（v2 曾跳过）
must_reject("4/clock 不一致被拒",
            lambda ep: ep["turns"][0]["input"].update(clock="1999-01-01 00:00"))

# 4b. may_decline 许可翻转 → 拒绝（v16 复核 §6：NCA_t06 true→false 曾放行）
def _must_reject_case(name, case_id, mutate):
    ep = copy.deepcopy(gag.EPISODES["NCA"])
    for t in ep["turns"]:
        if t["case_id"] == case_id:
            mutate(t["input"])
            break
    old = gag.EPISODES["NCA"]
    gag.EPISODES["NCA"] = ep
    try:
        gag._assert_binding("NCA", case_id, "A1", "r1")
        check(name, False, "未拒绝")
    except SystemExit as e:
        check(name, True, str(e).strip()[:80])
    finally:
        gag.EPISODES["NCA"] = old


_must_reject_case("4b/may_decline true→false 被拒", "NCA_t06",
                  lambda inp: inp.update(may_decline=False))
_must_reject_case("4b/may_decline false→true 被拒", "NCA_t01",
                  lambda inp: inp.update(may_decline=True))

# 5. 回复双侧绑定：原始 reply_raw 解析 = 派生账（正例必须通过）
try:
    rec = gag._raw_recommended("A1", "NCA_CORR_INTENT_v1", "r1", "NCA_t01")
    check("5/回复双侧绑定一致（正例）", bool(rec and rec.strip()))
except SystemExit as e:
    check("5/回复双侧绑定一致（正例）", False, e)

# 6. 篡改派生账条目 → _raw_recommended 必须拒绝
old = gag.recs[("A1", "NCA", "r1", "NCA_t01")]
gag.recs[("A1", "NCA", "r1", "NCA_t01")] = old + "！"
try:
    gag._raw_recommended("A1", "NCA_CORR_INTENT_v1", "r1", "NCA_t01")
    check("6/派生账篡改被拒", False, "未拒绝")
except SystemExit:
    check("6/派生账篡改被拒", True)
finally:
    gag.recs[("A1", "NCA", "r1", "NCA_t01")] = old

print("---")
print("全部 PASS（匿名材料绑定守卫）" if not fails else "FAIL: %s" % fails)
sys.exit(0 if not fails else 1)
