# -*- coding: utf-8 -*-
"""test_expr_candidate.py — phase2_expr 表达候选离线回归（零真实调用）。

A. 表达策略注入：经 run_turn 真实路径发出的 system 含 expression.md 四规则；
   父版本机制同路径不含（主变量隔离）。
B. 检索/写回/快照与父版本逐字节一致（唯一主变量=表达策略）：
   同 sandbox 输入 + 同 REPLY stub → kv_facts/kv_ongoing/snapshot/retrieval_trace
   字节一致；候选额外产出 expr_trace.jsonl，父版本不产出。
C. 运行时词面审计：植入四族缺陷的 REPLY → expr_trace findings 命中 F1/F2/F3/F4；
   干净 REPLY → 0 findings；坏 JSON → parse_ok=False 留痕不崩溃。
D. 检测器同源：候选内联四族（含 F4）与 tools/expr_audit.py 模式+标签一致。
E. no_reply / memory-off 委托父版本行为不回退。
F. 注入幂等（v9 复核 §3.1）：同进程重复加载机制模块 + run_episode 两次，
   每个请求 system 中表达块恰 1 份；全局 build_reply_prompts 无永久副作用。
"""
import importlib.util as _ilu
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for p in ("arena", "tools", os.path.join("candidates", "phase2_expr", "mechanism")):
    sys.path.insert(0, os.path.join(ROOT, p))
os.chdir(ROOT)

sys.path.insert(0, os.path.join(ROOT, "tools"))
import expr_audit            # noqa: E402

_spec = _ilu.spec_from_file_location(
    "expr_mech",
    os.path.join(ROOT, "candidates/phase2_expr/mechanism/mechanism.py"))
expr_mech = _ilu.module_from_spec(_spec)
_spec.loader.exec_module(expr_mech)

_spec1 = _ilu.spec_from_file_location(
    "phase1_mech_direct",
    os.path.join(ROOT, "candidates/phase1_retr_idf/mechanism/mechanism.py"))
phase1 = _ilu.module_from_spec(_spec1)
_spec1.loader.exec_module(phase1)

CAND_ASSETS = os.path.join(ROOT, "candidates/phase2_expr/assets")
P1_ASSETS = os.path.join(ROOT, "candidates/phase1_retr_idf/assets")


class StubGW:
    name = "stub"

    def __init__(self, text):
        self.text = text
        self.systems = []

    def generate(self, system, user, images=None, model=None,
                 temperature=None, timeout_s=120, tag="main"):
        self.systems.append(system)
        return {"text": self.text, "finish": "stop", "usage": None}


def _reply(directions=None, resp=None):
    return json.dumps({
        "response": resp or {"recommended": "好", "bad_boy": "行",
                             "playful": "哈哈", "warm": "嗯"},
        "directions": directions if directions is not None
        else ["好", None, "注意安全", None],
        "analysis": {"topic_status": "new", "topic_label": "日常",
                     "scene_facts": [{"text": "她确认了", "source_ids": ["m0"]}],
                     "ongoing": []}},
        ensure_ascii=False)


BAD_REPLY = _reply(
    directions=["你就说她猫毛会沾你床上，让她自己想清楚。",
                "我一般必加土豆片，你少放点辣。",
                "室友可不管这个，她明明知道你过敏。",
                "她之前不是自己把猫关门外了，这回怎么又松口了"],
    resp={"recommended": "过敏不是小事，直说吧，我陪你搬。",
          "bad_boy": "你就说不行，别硬扛。",
          "playful": "猫：这锅我不背。",
          "warm": "别担心，有我呢。"})
CLEAN_REPLY = _reply(
    directions=["过敏不是小事，直说吧，我陪你搬。",
                "要不这周末我去帮你跟她说？她要是不好意思拒绝，我帮你开口。",
                None, None],
    resp={"recommended": "跟室友好好说一次，别自己憋着。",
          "bad_boy": "再让猫进你房，我就把你俩一起关外面。",
          "playful": "你现在是人猫抢房大战的人质。",
          "warm": "不舒服就说，我帮你想办法跟她讲。"})


def _mk_h(sandbox, mem_on=True, assets_root=None):
    os.makedirs(sandbox, exist_ok=True)
    return {"assets_root": assets_root or CAND_ASSETS, "sandbox": sandbox,
            "turn": 0, "topic": "", "topic_age": 99, "persona": {},
            "initial_kb": {}, "memory_access": mem_on, "last_clock": None,
            "topic_started_at": None}


def _run(mech, h, reply, window="你成都那边安排好了吗",
         clock="2026-10-27 10:00"):
    ctx = {"case_id": "T", "input": {"clock": clock, "messages": [
        {"role": "HER", "text": window, "ts": clock}]}}
    return mech.run_turn(h, ctx, StubGW(reply))


EXPR_BLOCK_MARK = "表达与选择策略"


def _count_expr_blocks(system):
    return system.count(EXPR_BLOCK_MARK)


def _read_bytes(p):
    return open(p, "rb").read() if os.path.exists(p) else None


def main():
    fails = []

    # ---- A. 表达策略注入（主变量隔离，经 run_turn 真实路径） ----
    import lb_assembly
    gw_c = StubGW(CLEAN_REPLY)
    expr_mech.run_turn(_mk_h(os.path.join(tempfile.mkdtemp(prefix="exprA_"),
                                          "sb_c")),
                       {"case_id": "T", "input": {"clock": "2026-10-27 10:00",
                        "messages": [{"role": "HER", "text": "在吗",
                                      "ts": "2026-10-27 10:00"}]}}, gw_c)
    sys_cand = gw_c.systems[-1]
    gw_p = StubGW(CLEAN_REPLY)
    phase1.run_turn(_mk_h(os.path.join(tempfile.mkdtemp(prefix="exprA_"),
                                       "sb_p"), assets_root=P1_ASSETS),
                    {"case_id": "T", "input": {"clock": "2026-10-27 10:00",
                     "messages": [{"role": "HER", "text": "在吗",
                                   "ts": "2026-10-27 10:00"}]}}, gw_p)
    sys_p1 = gw_p.systems[-1]
    for marker in ("正文视角", "我的经历必须有出处", "不替他人下断言", "旧事主体对齐",
                   "收信人一致"):
        if marker not in sys_cand:
            fails.append("A1/候选 system 缺表达规则 %r" % marker)
    if "正文视角" in sys_p1:
        fails.append("A2/父版本 system 不应含表达规则（主变量泄漏）")
    if not sys_cand.startswith(sys_p1):
        fails.append("A3/候选 system 应=父版本 system + 表达块追加（前缀不一致）")
    # 允许例（自然建议句）应进入提示：反误杀口径（v9 复核 §3.3）
    if "你就跟她说你对猫毛过敏" not in sys_cand:
        fails.append("A4/规则一缺'允许建议句'正例（反误杀口径未生效）")
    if getattr(lb_assembly.build_reply_prompts, "_expr_orig", None) is not None:
        fails.append("A5/模块加载后全局 build_reply_prompts 不得被永久替换")

    # ---- B. 与父版本逐字节一致（唯一主变量=表达策略） ----
    tmp = tempfile.mkdtemp(prefix="expr_cand_parity_")
    h_p1 = {"assets_root": P1_ASSETS, "sandbox": os.path.join(tmp, "sb_p1"),
            "turn": 0, "topic": "", "topic_age": 99, "persona": {},
            "initial_kb": {}, "memory_access": True, "last_clock": None,
            "topic_started_at": None}
    os.makedirs(h_p1["sandbox"], exist_ok=True)
    h_c = _mk_h(os.path.join(tmp, "sb_cand"))
    with open(os.path.join(h_p1["sandbox"], "kv_facts.jsonl"), "w",
              encoding="utf-8") as f:
        f.write(json.dumps({"clock": "2026-10-26 12:00",
                            "text": "她室友今天抱回来一只猫"},
                           ensure_ascii=False) + "\n")
    import shutil
    shutil.copy(os.path.join(h_p1["sandbox"], "kv_facts.jsonl"),
                os.path.join(h_c["sandbox"], "kv_facts.jsonl"))
    out_p1 = _run(phase1, h_p1, CLEAN_REPLY)
    out_c = _run(expr_mech, h_c, CLEAN_REPLY)
    for fn in ("kv_facts.jsonl", "kv_ongoing.json"):
        if _read_bytes(os.path.join(h_p1["sandbox"], fn)) != \
                _read_bytes(os.path.join(h_c["sandbox"], fn)):
            fails.append("B1/%s 与父版本不一致" % fn)
    if phase1.snapshot(h_p1) != expr_mech.snapshot(h_c):
        fails.append("B2/snapshot 与父版本不一致")
    tr_p1 = _read_bytes(os.path.join(h_p1["sandbox"], "retrieval_trace.jsonl"))
    tr_c = _read_bytes(os.path.join(h_c["sandbox"], "retrieval_trace.jsonl"))
    if tr_p1 != tr_c or tr_c is None:
        fails.append("B3/retrieval_trace 与父版本不一致（检索必须冻结）")
    if not os.path.isfile(os.path.join(h_c["sandbox"], "expr_trace.jsonl")):
        fails.append("B4/候选未产出 expr_trace.jsonl")
    elif os.path.isfile(os.path.join(h_p1["sandbox"], "expr_trace.jsonl")):
        fails.append("B5/父版本不应产出 expr_trace.jsonl")
    if "expr_policy=on" not in out_c["notes"]:
        fails.append("B6/候选 notes 缺 expr_policy 标识")
    if out_p1["reply"] != out_c["reply"]:
        fails.append("B7/回复透传被改动（审计不得改写回复）")

    # ---- C. 运行时词面审计 ----
    h_bad = _mk_h(os.path.join(tmp, "sb_bad"))
    _run(expr_mech, h_bad, BAD_REPLY)
    recs = [json.loads(ln) for ln in
            open(os.path.join(h_bad["sandbox"], "expr_trace.jsonl"),
                 encoding="utf-8")]
    finds = recs[-1]["findings"]
    fams = {f["family"] for f in finds}
    for want in ("F1_advisor_voice", "F2_me_fabrication", "F3_motive_assertion",
                 "F4_subject_manual_review"):
        if want not in fams:
            fails.append("C1/植入缺陷未命中 %s: %s" % (want, sorted(fams)))
    # C4 反误杀（v9 复核 §3.3）：HER 问怎么应对室友时的自然建议句
    # "你就跟她说你对猫毛过敏" 会命中 F1 词面，但标签必须是判读口径而非定罪
    f1_hits = [f for f in finds if f["field"] == "directions[0]"
               and f["family"] == "F1_advisor_voice"]
    if not f1_hits:
        fails.append("C4/F1 词面未命中（初筛失效）")
    else:
        lbl = f1_hits[0]["label"]
        if "收信人一致" not in lbl or "判读" not in lbl:
            fails.append("C4/F1 标签仍为定罪口径: %r" % lbl)
    # 干净回复（含暧昧调侃）不误报
    h_ok = _mk_h(os.path.join(tmp, "sb_ok"))
    _run(expr_mech, h_ok, CLEAN_REPLY)
    recs_ok = [json.loads(ln) for ln in
               open(os.path.join(h_ok["sandbox"], "expr_trace.jsonl"),
                    encoding="utf-8")]
    if recs_ok[-1]["n_findings"] != 0:
        fails.append("C2/干净回复误报: %s" % recs_ok[-1]["findings"])
    # 坏 JSON 留痕不崩溃
    h_bp = _mk_h(os.path.join(tmp, "sb_bp"))
    _run(expr_mech, h_bp, '{"response": {"recommended": "味道还行')  # 截断
    recs_bp = [json.loads(ln) for ln in
               open(os.path.join(h_bp["sandbox"], "expr_trace.jsonl"),
                    encoding="utf-8")]
    if recs_bp[-1]["parse_ok"] is not False:
        fails.append("C3/坏 JSON 应 parse_ok=False 留痕")

    # ---- D. 检测器同源（候选内联 == tools/expr_audit.py，含 F4 与标签） ----
    inline = {(f, p, l) for f, p, l in expr_mech._load_families()}
    tool = {(fam, pat, lbl) for fam, pats in expr_audit.FAMILIES.items()
            for pat, lbl in pats}
    tool.add(("F4_subject_manual_review", expr_audit.F4_HINT.pattern,
              "旧事件回指—主语需人工核对"))
    if inline != tool:
        fails.append("D1/内联检测器与 expr_audit 四族不一致: %s"
                     % (inline ^ tool))
    # 库内调用与 CLI 同结果
    if expr_audit.audit_raw(BAD_REPLY)["n_findings"] == 0:
        fails.append("D2/expr_audit 库内调用未命中植入缺陷")

    # ---- E. memory-off 委托不回退 ----
    h_off = _mk_h(os.path.join(tmp, "sb_off"), mem_on=False)
    trr = {}
    old_w = expr_mech.base._write_trace
    expr_mech.base._write_trace = lambda h, t: trr.update(t)
    try:
        _run(expr_mech, h_off, CLEAN_REPLY)
    finally:
        expr_mech.base._write_trace = old_w
    if trr.get("facts_stored") not in (None, []):
        fails.append("E1/memory-off 仍读取历史")

    # ---- F. 注入幂等 + 同路径双运行（v9 复核 §3.1） ----
    import lb_runner
    ep_path = os.path.join(ROOT, "benchmark/episodes/L3B_MINI_FULL.json")
    tmp2 = tempfile.mkdtemp(prefix="expr_f_")

    def _systems_in_run(out_dir):
        sys_texts = []
        for root, _d, fs in os.walk(out_dir):
            if "calls.json" in fs:
                calls = json.load(open(os.path.join(root, "calls.json"),
                                       encoding="utf-8"))
                for c in calls:
                    if isinstance(c.get("system"), str):
                        sys_texts.append(c["system"])
        return sys_texts

    for rep in (1, 2):
        out_c = os.path.join(tmp2, "cand_run%d" % rep)
        lb_runner.run_episode(ep_path, os.path.join(ROOT, "candidates/phase2_expr"),
                              out_c, "mock", stop_after=2)
        sys_texts = _systems_in_run(out_c)
        if not sys_texts:
            fails.append("F%d/未捕获任何 system（calls.json 结构变化？）" % rep)
        for k, s in enumerate(sys_texts):
            n_blk = _count_expr_blocks(s)
            if n_blk != 1:
                fails.append("F%d/候选请求#%d 表达块=%d（应恰 1 份）" % (rep, k, n_blk))
    # 同进程父版本臂（phase0）在 phase2 加载后运行：必须零表达块（跨臂零污染）
    out_p = os.path.join(tmp2, "parent_run1")
    lb_runner.run_episode(ep_path, os.path.join(ROOT, "candidates/phase0_beta_single"),
                          out_p, "mock", stop_after=2)
    for k, s in enumerate(_systems_in_run(out_p)):
        if _count_expr_blocks(s) != 0:
            fails.append("F3/父版本请求#%d 出现表达块（跨臂污染）" % k)
    # 全局函数已恢复原身
    if getattr(lb_assembly.build_reply_prompts, "_expr_orig", None) is not None:
        fails.append("F4/全部运行结束后全局 build_reply_prompts 未恢复原身")

    if fails:
        print("FAIL %d 项：" % len(fails))
        for f in fails:
            print("  -", f)
        raise SystemExit(1)
    print("PASS：表达策略注入(主变量隔离+反误杀口径) + 检索/写回/快照与父版本逐字节一致 "
          "+ 四族审计命中(含F4)/无误报/坏JSON留痕 + 检测器同源(含标签) + memory-off委托一致 "
          "+ 注入幂等(run_episode双运行各恰1份/父臂零污染/全局无永久副作用)")


if __name__ == "__main__":
    main()
