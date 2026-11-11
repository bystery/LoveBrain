# -*- coding: utf-8 -*-
r"""tools/test_v6_candidates.py — v6_goal_context / v6_integrated 离线回归（零真实调用）。

覆盖 P05 最小验收表里可在离线装配层证明的部分：
  目标 ACTIVE/ADJUSTED/CLOSED/expiry；无目标不复活；显式拒绝/忙碌/收尾的动作门控；
  含糊多动作；钩子逐字兑现；anchor 属于本轮；analysis 校验；
  事实写入（有效/无效来源/无据引文）；更正/取消/完成/重开；取消不复活；
  暂定与事实分离；静态种子去重与更正；当前窗口不被挤出；快照/恢复；
  memory_off；消融开关；两候选状态不串写；每轮恰好一次模型调用。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\test_v6_candidates.py
"""
import json
import os
import re
import sys
import tempfile
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "arena"))
import lb_runner  # noqa: E402

FAILS = []


def check(name, cond, detail=""):
    print("%s %s %s" % ("PASS" if cond else "FAIL", name, detail))
    if not cond:
        FAILS.append(name)


class FakeGW:
    def __init__(self):
        self.prompts = []
        self.replies = []

    def generate(self, system, user, images=None, tag="main"):
        self.prompts.append({"system": system, "user": user})
        if not self.replies:
            raise RuntimeError("no scripted reply left")
        r = self.replies.pop(0)
        return {"text": json.dumps(r, ensure_ascii=False), "finish": "stop", "usage": None}


def reply(action="承接", signal="中性", goal="无目标", text="嗯", facts=None,
          tent=None, updates=None, hook_kind="无", hook_text="", anchor="m0"):
    return {"response": {"recommended": text, "bad_boy": None, "playful": None, "warm": None},
            "directions": [None, None, None, None],
            "analysis": {"signal": signal, "goal_category": goal, "action_taken": action,
                         "anchor": anchor,
                         "hook": {"kind": hook_kind, "text": hook_text},
                         "facts": facts or [], "tentatives": tent or [],
                         "updates": updates or []}}


def ctx(clock, her, intent=None, may_decline=False, extra=None):
    msgs = [{"role": "HER", "text": her, "ts": clock}]
    if extra:
        msgs.extend(extra)
    return {"input": {"clock": clock, "messages": msgs, "intent": intent,
                      "may_decline": may_decline}, "turn_index": None}


def run(mech, h, gw, c, r):
    gw.replies.append(r)
    return mech.run_turn(h, c, gw)


def block(prompt, header):
    i = prompt.find(header)
    if i < 0:
        return ""
    j = prompt.find("\n\n", i + len(header))
    return prompt[i:j if j > 0 else len(prompt)]


META = {
    "persona": {"me": "29岁，男，南京，产品经理", "her": "28岁，女，南京，编辑"},
    "initial_kb": {"her": "喜欢后摇和现场音乐", "warmth": "共同朋友小周是介绍人",
                   "lessons": "# 旧教训\n别催",
                   "ongoing_plan": [{"name": "周六看展（时间待定）", "status": "进行中"}]},
}


def load(name):
    cand = os.path.join(ROOT, "candidates", name)
    lb_runner.require_candidate_assets(cand)
    mech, origin, ver = lb_runner.load_mechanism(cand)
    assert ver == "v2", (name, ver)
    return cand, mech


def new_handle(cand, mech, gw):
    return mech.init(os.path.join(cand, "assets"), META, gw, tempfile.mkdtemp(prefix="v6t_"))


# ─────────────────────────── v6_goal_context ───────────────────────────
cand_g, g = load("v6_goal_context")
gw = FakeGW()
h = new_handle(cand_g, g, gw)

o = run(g, h, gw, ctx("2026-11-07 20:00", "周末有空吗 要不要去看那个展",
                      {"enabled": True, "text": "想约她周末看展", "status": "ACTIVE"}),
        reply("邀约", "积极", "推进见面", "周六还是周日？", hook_kind="小选择",
              hook_text="周六还是周日"))
p = gw.prompts[-1]["user"]
check("v6g T1 goal block", "【当前目标" in p and "想约她周末看展" in p)
check("v6g T1 action block", "本轮可用动作" in p and "邀约：" in p)
check("v6g T1 hook delivered", "hook_delivered=True" in o["notes"], o["notes"][:120])
check("v6g T1 one call", len(gw.prompts) == 1)

o = run(g, h, gw, ctx("2026-11-07 20:10", "最近有点忙 改天吧",
                      {"enabled": True, "text": "想约她周末看展", "status": "ACTIVE"}),
        reply("收尾", "忙碌", "推进见面", "行 等你不忙"))
p = gw.prompts[-1]["user"]
check("v6g busy hides invite", "邀约：" not in p and "她忙/暂缓" in p)

o = run(g, h, gw, ctx("2026-11-07 20:20", "我不想去了",
                      {"enabled": True, "text": "想约她周末看展", "status": "ACTIVE"}),
        reply("收尾", "拒绝", "推进见面", "好 那不去"))
p = gw.prompts[-1]["user"]
check("v6g refusal hides invite/tease/probe",
      "邀约：" not in p and "反抛：" not in p and "轻试探：" not in p)

o = run(g, h, gw, ctx("2026-11-07 22:00", "我先睡了 明天聊", None, may_decline=True),
        reply("收尾", "收尾", "无目标", "晚安"))
p = gw.prompts[-1]["user"]
check("v6g closing no invite", "邀约：" not in p and "收尾：" in p)
check("v6g closing may_decline offers 停", "停：" in p)

o = run(g, h, gw, ctx("2026-11-08 09:00", "早",
                      {"enabled": False, "text": "想约她周末看展", "status": "CLOSED"}),
        reply())
p = gw.prompts[-1]["user"]
check("v6g closed goal not resurrected", "想约她周末看展" not in p)
check("v6g closed plan retained", "上一目标已关闭" in p and "周六看展" in p)

o = run(g, h, gw, ctx("2026-11-08 09:10", "那下周三呢",
                      {"enabled": True, "text": "改成下周三看展", "status": "ADJUSTED"}),
        reply())
p = gw.prompts[-1]["user"]
check("v6g ADJUSTED effective", "改成下周三看展" in p and "本轮已调整" in p)

o = run(g, h, gw, ctx("2026-11-09 09:10", "嗯",
                      {"enabled": True, "text": "过期的目标", "status": "ACTIVE",
                       "expiry": "2026-11-08 00:00", "now": "2026-11-09 09:10"}),
        reply())
p = gw.prompts[-1]["user"]
check("v6g expiry suppresses goal", "过期的目标" not in p and "goal_effective=0" in o["notes"])

o = run(g, h, gw, ctx("2026-11-09 09:20", "在吗",
                      {"enabled": True, "text": "继续聊", "status": "ACTIVE"}),
        reply("承接", hook_kind="观察", hook_text="不存在的钩子", anchor="m9"))
check("v6g hook not delivered detected", "hook_delivered=False" in o["notes"])
check("v6g anchor invalid detected", "anchor_valid=False" in o["notes"])

snap = g.snapshot(h)
h2 = g.restore(tempfile.mkdtemp(prefix="v6t_restore_"), snap)
check("v6g restore events/goal/action",
      len(h2["events"]) == len(h["events"]) and len(h2["goal_history"]) == len(h["goal_history"])
      and len(h2["action_log"]) == len(h["action_log"]))
before = len(h2["events"])
o = run(g, h2, gw, ctx("2026-11-09 09:30", "继续", None), reply())
check("v6g restore continues", len(h2["events"]) == before + 1)
check("v6g restore no goal resurrection", "想约她周末看展" not in gw.prompts[-1]["user"]
      and "改成下周三看展" not in gw.prompts[-1]["user"])

g.set_memory_access(h, False)
o = run(g, h, gw, ctx("2026-11-09 09:40", "继续", None), reply())
p = gw.prompts[-1]["user"]
check("v6g memory_off drops prior history", "memory_off=1" in o["notes"])
check("v6g memory_off keeps seed", "喜欢后摇和现场音乐" in p)

# two handles no cross-write
gw_a, gw_b = FakeGW(), FakeGW()
ha = new_handle(cand_g, g, gw_a)
hb = new_handle(cand_g, g, gw_b)
run(g, ha, gw_a, ctx("2026-11-10 10:00", "你好", None), reply())
check("v6g two handles state isolated", len(ha["events"]) == 1 and len(hb["events"]) == 0)

# ─────────────────────────── v6_integrated ───────────────────────────
cand_i, i = load("v6_integrated")
gw = FakeGW()
h = new_handle(cand_i, i, gw)
plan_fid = h["plan_items"][0]["fid"]
her_fid = h["seed_fids"]["kbin:her"]
check("v6i seeds and plan fid", len(h["mem"]["facts"]) >= 4 and plan_fid.startswith("f_"))

o = run(i, h, gw, ctx("2026-11-07 20:00", "周六那个展我可能去不了 要加班",
                      {"enabled": True, "text": "想约她周末看展", "status": "ACTIVE"}),
        reply("收尾", "忙碌", "推进见面", "行 那先忙",
              facts=[{"text": "她周六要加班", "subject": "HER", "quote": "要加班",
                      "src_ids": ["m0"], "certainty": "confirmed"},
                     {"text": "她喜欢后摇", "subject": "HER", "quote": "我讨厌后摇",
                      "src_ids": ["m0"], "certainty": "confirmed"},
                     {"text": "越权事实", "subject": "HER", "quote": "要加班",
                      "src_ids": ["m9"], "certainty": "confirmed"}],
              updates=[{"fact_id": plan_fid, "action": "cancel", "quote": "去不了",
                        "src_ids": ["m0"]}]))
check("v6i facts added/unsupported/invalid",
      "added_f=2" in o["notes"] and "unsupported_src=1" in o["notes"]
      and "invalid_src=1" in o["notes"], o["notes"][:180])
check("v6i cancel applied", "updates_applied=1" in o["notes"])

o = run(i, h, gw, ctx("2026-11-07 20:20", "不过我下周三应该有空",
                      {"enabled": True, "text": "想约她周末看展", "status": "ADJUSTED"}),
        reply("邀约", "中性", "推进见面", "下周三怎么样",
              facts=[{"text": "她下周三有空", "subject": "HER", "quote": "下周三应该有空",
                      "src_ids": ["m0"], "certainty": "confirmed"}],
              updates=[{"fact_id": plan_fid, "action": "correct", "new_text": "下周三看展",
                        "quote": "下周三", "src_ids": ["m0"]}]))
p2 = gw.prompts[-1]["user"]
check("v6i cancelled not in active plan block", "周六看展" not in block(p2, "【已确认/进行中事项"))
check("v6i recent cancel visible", "已取消" in p2)

o = run(i, h, gw, ctx("2026-11-07 20:30", "下周三几点到 对了 我最近更爱民谣了", None),
        reply("承接", "积极", "无目标", "民谣也好",
              updates=[{"fact_id": her_fid, "action": "correct",
                        "new_text": "喜欢民谣和现场音乐", "quote": "最近更爱民谣了",
                        "src_ids": ["m0"]}]))
p3 = gw.prompts[-1]["user"]
check("v6i corrected plan visible", "下周三看展" in block(p3, "【已确认/进行中事项"))
check("v6i relevant fact projected", "她下周三有空" in p3, p3[:120])

o = run(i, h, gw, ctx("2026-11-07 20:40", "我还说下下周也行", None),
        reply("承接", tent=[{"text": "她下下周也有空", "quote": "下下周也行",
                             "src_ids": ["m0"]}],
              text="那就再看"))
p4 = gw.prompts[-1]["user"]
check("v6i corrected seed visible in next turn", "喜欢民谣和现场音乐" in p4
      and "喜欢后摇和现场音乐" not in p4)

o = run(i, h, gw, ctx("2026-11-07 20:50", "算了", None),
        reply("承接", updates=[
            {"fact_id": "f_9999", "action": "cancel", "quote": "算了", "src_ids": ["m0"]},
            {"fact_id": plan_fid, "action": "complete", "quote": "不存在的话", "src_ids": ["m0"]}]))
p5a = gw.prompts[-1]["user"]
check("v6i tentative block", "【暂定事项" in p5a and "她下下周也有空" in p5a)
check("v6i tentative not in confirmed facts",
      "她下下周也有空" not in block(p5a, "【相关已确认事实"))
check("v6i bad updates rejected", "updates_rejected=unknown_fid:f_9999,unsupported_update:%s(quote)"
      % plan_fid in o["notes"], o["notes"][:220])

# 当前窗口永不被挤出
h3 = new_handle(cand_i, i, FakeGW())
h3["events"] = [{"uid": 1000 + k, "role": "HER", "text": "旧消息%d" % k,
                 "ts": "2026-01-01 00:00"} for k in range(200)]
gw3 = FakeGW()
h3["events"] = [{"uid": 1000 + k, "role": "HER", "text": "旧消息%d" % k,
                 "ts": "2026-01-01 00:00"} for k in range(200)]
o = run(i, h3, gw3, ctx("2026-11-11 11:00", "当前窗口这句话必须在", None),
        reply())
p5 = gw3.prompts[-1]["user"]
check("v6i current window intact", "当前窗口这句话必须在" in p5)
_m = re.search(r"history_cu=(\d+)", o["notes"])
check("v6i history budget bounded", bool(_m) and int(_m.group(1)) <= 1400,
      o["notes"][:160])
check("v6i current block after history", p5.find("# 本轮输入") > p5.find("# 【真实历史消息"))

# snapshot/restore
snap = i.snapshot(h)
h4 = i.restore(tempfile.mkdtemp(prefix="v6i_restore_"), snap)
check("v6i restore memory/plan/updates",
      len(h4["mem"]["facts"]) == len(h["mem"]["facts"])
      and len(h4["mem"]["updates"]) == len(h["mem"]["updates"])
      and len(h4["plan_items"]) == len(h["plan_items"]))
gw4 = FakeGW()
o = run(i, h4, gw4, ctx("2026-11-07 21:00", "下周三见", None), reply())
check("v6i restore continues with plan/fact",
      "下周三看展" in gw4.prompts[-1]["user"] and "她下周三有空" in gw4.prompts[-1]["user"])

# memory off
i.set_memory_access(h4, False)
o = run(i, h4, gw4, ctx("2026-11-07 21:10", "继续", None), reply())
p6 = gw4.prompts[-1]["user"]
check("v6i memory_off disables projections", "mem_on=0" in o["notes"]
      and "【相关已确认事实" not in p6)
check("v6i memory_off keeps seed/plan", "下周三看展" in p6 and "喜欢民谣和现场音乐" in p6)

# ablation flags
orig_flags = i._flags
i._flags = lambda: {"action_layer": False, "evidence_memory": False}
try:
    cand_i2, i2 = load("v6_integrated")
    i2._flags = i._flags
    gw5 = FakeGW()
    h5 = new_handle(cand_i2, i2, gw5)
    o = run(i2, h5, gw5, ctx("2026-11-12 12:00", "你好",
                             {"enabled": True, "text": "目标", "status": "ACTIVE"}), reply())
    p7 = gw5.prompts[-1]["user"]
    check("v6i ablation both off = v5 base", "本轮可用动作" not in p7
          and "【相关已确认事实" not in p7 and "[f_" not in p7)
    check("v6i ablation keeps target", "目标" in p7)
finally:
    i._flags = orig_flags

# two handles no cross-write
gw_a, gw_b = FakeGW(), FakeGW()
ha = new_handle(cand_i, i, gw_a)
hb = new_handle(cand_i, i, gw_b)
before_b = len(hb["mem"]["facts"])
run(i, ha, gw_a, ctx("2026-11-13 13:00", "我下周三有空",
                     {"enabled": True, "text": "约", "status": "ACTIVE"}),
    reply("邀约", facts=[{"text": "她下周三有空", "subject": "HER",
                          "quote": "下周三有空", "src_ids": ["m0"],
                          "certainty": "confirmed"}]))
check("v6i two handles state isolated",
      len(ha["events"]) == 1 and len(hb["events"]) == 0
      and len(hb["mem"]["facts"]) == before_b)

check("v6i one call per turn", len(gw.prompts) == 5)

# 含糊/积极门控（独立 handle）
gw6 = FakeGW()
h6 = new_handle(cand_g, g, gw6)
run(g, h6, gw6, ctx("2026-11-14 14:00", "嗯",
                    {"enabled": True, "text": "想约她", "status": "ACTIVE"}),
    reply("反抛", "含糊", "推进见面", "那你说说看"))
p_amb = gw6.prompts[-1]["user"]
check("v6g ambiguous gives multi-action",
      "反抛：" in p_amb and "轻试探：" in p_amb and "转场：" in p_amb
      and "不自动降温" in p_amb and "邀约：" not in p_amb)
run(g, h6, gw6, ctx("2026-11-14 14:10", "好啊",
                    {"enabled": True, "text": "想约她", "status": "ACTIVE"}),
    reply("邀约", "积极", "推进见面", "那周六见"))
p_pos = gw6.prompts[-1]["user"]
check("v6g positive allows proactive invite", "邀约：" in p_pos and "积极信号" in p_pos)

# 第三人/否定/事件时间/无关事实（独立 handle）
gw7 = FakeGW()
h7 = new_handle(cand_i, i, gw7)
o = run(i, h7, gw7, ctx("2026-11-15 10:00", "小林说那个展不错 但我不想去",
                        None, extra=[{"role": "HER", "text": "我最近喜欢猫",
                                      "ts": "2026-11-15 10:00"}]),
        reply("承接", facts=[
            {"text": "小林觉得那个展不错", "subject": "THIRD", "quote": "小林说那个展不错",
             "src_ids": ["m0"], "certainty": "confirmed", "event_time": "2026-11-25"},
            {"text": "她不想去", "subject": "HER", "quote": "我不想去",
             "src_ids": ["m0"], "certainty": "confirmed"},
            {"text": "她喜欢猫", "subject": "HER", "quote": "我最近喜欢猫",
             "src_ids": ["m1"], "certainty": "confirmed"}]))
third = [f for f in h7["mem"]["facts"].values()
         if f.get("origin") == "reported" and "小林" in f.get("text", "")]
neg = [f for f in h7["mem"]["facts"].values()
       if f.get("origin") == "reported" and f.get("text") == "她不想去"]
check("v6i third-party subject stored as THIRD",
      bool(third) and third[0]["subject"] == "THIRD")
check("v6i negation fact stored as HER (not dropped)",
      bool(neg) and neg[0]["subject"] == "HER" and "不想" in neg[0]["text"])
line_body, _cu, _hits, _filt, _ms = i._facts_projection(
    h7, "下周三 那个展", 500, "2026-11-15 10:10")
check("v6i event_time displayed and third-party label",
      "事件@2026-11-25" in line_body and "第三人：" in line_body)
line2, _cu2, _h2, filt2, _ms2 = i._facts_projection(
    h7, "下周三", 500, "2026-11-15 10:10")
check("v6i irrelevant fact filtered", "她喜欢猫" not in line2 and filt2 >= 1)

# 资产/机制契约键集一致
fmt_g = open(os.path.join(cand_g, "assets", "system_prompt", "format.md"),
             encoding="utf-8").read()
fmt_i = open(os.path.join(cand_i, "assets", "system_prompt", "format.md"),
             encoding="utf-8").read()
check("v6g format has action keys",
      all(k in fmt_g for k in ("signal", "goal_category", "action_taken", "anchor", "hook")))
check("v6i format has merged keys",
      all(k in fmt_i for k in ("signal", "goal_category", "action_taken", "anchor", "hook",
                               "facts", "tentatives", "updates", "fact_id")))
check("v6i core has memory section", "记忆与事实" in open(
    os.path.join(cand_i, "assets", "system_prompt", "core.md"), encoding="utf-8").read())

print("---")
if FAILS:
    print("FAILED: %d checks: %s" % (len(FAILS), FAILS))
    sys.exit(1)
print("ALL PASS")
