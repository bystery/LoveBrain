# -*- coding: utf-8 -*-
"""test_protocol_public.py — A3 公共协议库拆分的等价证明与边界测试。

三组断言：
  1. lb_scorer 从 protocol_public re-export（同一函数对象，非副本）；
  2. 迁移前快照（refs/arena_v1/protocol_baseline/lb_scorer_pure_snapshot.py）
     与 protocol_public 在有效/无效输出矩阵上逐例输出一致（等价证明）；
  3. 候选 mechanism.py 不再 import lb_scorer（评分权重/gold 不进候选进程）。
"""
import glob
import importlib.util as ilu
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
fails = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else " | " + str(detail)[:200]))
    if not ok:
        fails.append(name)


def load(name, path):
    spec = ilu.spec_from_file_location(name, path)
    mod = ilu.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


import protocol_public as pp          # noqa: E402
import lb_scorer                       # noqa: E402
snap = load("proto_snap", os.path.join(
    ROOT, "refs", "arena_v1", "protocol_baseline", "lb_scorer_pure_snapshot.py"))

# ---- 1. re-export 同一函数对象 ----
for fn in ("normalize_contract", "extract_json_block", "all_reply_texts",
           "output_structure_errors", "analysis_type_errors"):
    check("1/re-export %s 同一对象" % fn, getattr(lb_scorer, fn) is getattr(pp, fn))

# ---- 2. 有效/无效输出矩阵：快照 vs 新实现逐例一致 ----
GOOD_OUT = {
    "action": "reply",
    "response": {"recommended": "今晚早点休息", "bad_boy": None,
                 "playful": "小笨蛋，盖好被子", "warm": "记得喝热水"},
    "directions": ["报备行程，附上明早提醒", None, "问她咖啡要不要续杯", None],
    "analysis": {"topic_status": "same", "topic_label": "睡前闲聊",
                 "scene_facts": [{"text": "她说明晚加班", "source_ids": ["m3"]}],
                 "ongoing": [{"item": "周五一起看电影", "status": "已确认"}]},
}
BAD_OUTS = {
    "directions_int": {**GOOD_OUT, "directions": [1, 2, 3, 4]},
    "directions_obj": {**GOOD_OUT, "directions": [[1], None, "x", None]},
    "sf_text_null": {**GOOD_OUT, "analysis": {**GOOD_OUT["analysis"],
                     "scene_facts": [{"text": None, "source_ids": ["m1"]}]}},
    "sf_text_missing": {**GOOD_OUT, "analysis": {**GOOD_OUT["analysis"],
                        "scene_facts": [{"source_ids": ["m1"]}]}},
    "sf_sids_int": {**GOOD_OUT, "analysis": {**GOOD_OUT["analysis"],
                    "scene_facts": [{"text": "x", "source_ids": [1]}]}},
    "ongoing_item_int": {**GOOD_OUT, "analysis": {**GOOD_OUT["analysis"],
                         "ongoing": [{"item": 5, "status": "ok"}]}},
    "topic_status_int": {**GOOD_OUT, "analysis": {**GOOD_OUT["analysis"],
                         "topic_status": 1}},
    "resp_undeclared": {**GOOD_OUT, "response": {**GOOD_OUT["response"],
                        "secret_style": "夹带文本"}},
    "extra_bad_type": {**GOOD_OUT, "reply": 123},
    "analysis_not_dict": {**GOOD_OUT, "analysis": "not-a-dict"},
}
n_case = 0
mismatch = []
for name, out in [("good", GOOD_OUT)] + list(BAD_OUTS.items()):
    a = sorted(snap.output_structure_errors(out))
    b = sorted(pp.output_structure_errors(out))
    n_case += 1
    if a != b:
        mismatch.append((name, a, b))
    # all_reply_texts 也比对
    if snap.all_reply_texts(out) != pp.all_reply_texts(out):
        mismatch.append((name + "/texts",))
    # analysis_type_errors 单独比对（analysis 非字典时语义一致：返回含类型名的列表）
    ana = out.get("analysis")
    if snap.analysis_type_errors(ana) != pp.analysis_type_errors(ana):
        mismatch.append((name + "/ana",))
check("2/结构矩阵 %d 例快照与实现一致" % n_case, not mismatch, mismatch[:3])

# extract_json_block 矩阵
JSON_CASES = [
    ('前缀 ```json\n{"a": 1}\n``` 后缀', {"a": 1}),
    ('{"action":"reply","directions":[null]}', {"action": "reply", "directions": [None]}),
    ('完全没有 json', None),
    ('{"bad": ', None),
    ('嵌套 {"a": {"b": [1,2]}} 尾注', {"a": {"b": [1, 2]}}),
]
jm = []
for text, want in JSON_CASES:
    if snap.extract_json_block(text) != pp.extract_json_block(text):
        jm.append(text[:30])
    if pp.extract_json_block(text) != want:
        jm.append(("want", text[:30], pp.extract_json_block(text)))
check("2/json 矩阵 %d 例一致且符合预期" % len(JSON_CASES), not jm, jm)

# normalize_contract 矩阵
cm = []
if snap.normalize_contract(None) != pp.normalize_contract(None):
    cm.append("legacy")
for c in ({"style_keys": ["a", "b"], "directions": 4, "extra_text_fields": ["reply"],
           "may_decline": True},
          {"directions": 8}):
    try:
        a = snap.normalize_contract(c)
    except ValueError as e:
        a = ("ValueError", str(e))
    try:
        b = pp.normalize_contract(c)
    except ValueError as e:
        b = ("ValueError", str(e))
    if a != b:
        cm.append(c)
try:
    pp.normalize_contract({"style_keys": ["a"], "directions": 2})
    cm.append("min8-not-raised")
except ValueError:
    pass
check("2/契约矩阵一致 + <8 拒绝", not cm, cm)

# ---- 3. 候选侧不再 import lb_scorer ----
# （下划线开头目录 = 非生产攻击夹具，如 _attack_fixture 的 A4 探针**故意**尝试
# import lb_scorer 以证明 staged 树内无评分器——不算泄漏，R3 收口登记）
leaks = []
for f in glob.glob(os.path.join(ROOT, "candidates", "*", "mechanism", "mechanism.py")):
    if "_attack_fixture" in f.replace("\\", "/").split("/"):
        continue
    src = open(f, encoding="utf-8").read()
    # 2026-11-03：只查代码级引用——剥离注释（整行 # 与行尾 # 后）再查子串；
    # 历史候选注释里提及"arena/lb_scorer.py"属文档说明不是评分泄漏（此前误报）。
    code = "\n".join(ln.split("#", 1)[0] for ln in src.splitlines())
    if "lb_scorer" in code:
        leaks.append(os.path.relpath(f, ROOT))
check("3/候选 mechanism 零 lb_scorer 引用", not leaks, leaks)

# protocol_public 自身不含评分资产（代码级：不导入 lb_scorer/scorer，
# 不含权重表/词表/AI_FLAVOR 等评分常量；注释里出现 gold/rubric 字样不算）
psrc = open(os.path.join(ROOT, "arena", "protocol_public.py"), encoding="utf-8").read()
code_lines = [ln for ln in psrc.splitlines()
              if ln.strip() and not ln.strip().startswith("#")]
bad = [w for w in ("import lb_scorer", "import scorer", "AI_FLAVOR_WORDS",
                   "STYLE_SIM_MAX", "lcs_ratio", "WEIGHT", "run_case_scoring")
       if any(w in ln for ln in code_lines)]
check("3/protocol_public 无评分资产", not bad, bad)

print("== %s ==" % ("ALL PASS" if not fails else "FAILED: %s" % fails))
sys.exit(1 if fails else 0)
