# -*- coding: utf-8 -*-
"""test_nca_ncb_episodes.py — NCA/NCB 自然案离线链路验证（零真实调用）。

执行令 §6-3：schema/边界/动作/事件账 + 一次完整 mock 回放。
mock 只证明链路（schema→检索→写回→追踪→审计→收尾许可），不证明聊天质量。

A. schema/时间线：validate_pilot_timeline.validate 全过（含时钟单调、末条 HER、
   防泄漏词表、may_decline 双处一致）。
B. 边界：装配出的 system/user 不含 gold 字段（must_keywords/expected_facts_note/
   _predict_failures 不泄漏到候选请求）。
C. 完整 mock 回放（冻结检索 phase1 机制）：ScriptedGW 每轮返回合法 JSON
   （analysis 写回 HER 事实），逐轮断言——回复存在、追踪行存在、
   retrieved_ids 已记账。
D. 观测点（诊断，不作为聊天质量判定）：
   - NCA t4：t3'调岗'事实可被召回（窗口引用旧事属正常）；
   - NCA t5：append-only 下 t3 旧事实与 t4 更正事实并存可召回（预计失败诊断，
     实现失效机制后本断言需随机制更新）；
   - NCB t2：G7812 旧车次事实可被召回（英数 token 通道），D3145 写回前不在库；
   - may_decline 轮：请求中含"允许不回复"材料。
E. 表达候选委托：phase2_expr 在 NCA 全程可回放，expr_trace.jsonl 每轮落盘、
   干净 stub 零 findings。
F. 事件账：logs/nca_ncb_mock_replay_20261020.json（窗口散列/检索/写回/审计逐轮）。
"""
import hashlib
import importlib.util as _ilu
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for p in ("arena", "tools", os.path.join("candidates", "phase1_retr_idf", "mechanism"),
          os.path.join("candidates", "phase2_expr", "mechanism")):
    sys.path.insert(0, os.path.join(ROOT, p))
os.chdir(ROOT)
sys.path.insert(0, os.path.join(ROOT, "tools"))

import validate_pilot_timeline as vpt   # noqa: E402


def _load(name, rel):
    spec = _ilu.spec_from_file_location(name, os.path.join(ROOT, rel))
    m = _ilu.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


phase1 = _load("nca_phase1", "candidates/phase1_retr_idf/mechanism/mechanism.py")
NCA_EP = os.path.join(ROOT, "benchmark/episodes/NCA_CORR_INTENT_v1.json")
NCB_EP = os.path.join(ROOT, "benchmark/episodes/NCB_ENTITY_DENSE_v1.json")
P1_ASSETS = os.path.join(ROOT, "candidates/phase1_retr_idf/assets")
P2_ASSETS = os.path.join(ROOT, "candidates/phase2_expr/assets")


class ScriptedGW:
    """确定性 stub：回复正文=对末条 HER 的固定接话；analysis 写回 HER 事实。"""
    name = "scripted"

    def __init__(self):
        self.last_system = None
        self.last_user = None

    def generate(self, system, user, images=None, model=None,
                 temperature=None, timeout_s=120, tag="main"):
        self.last_system, self.last_user = system, user
        import lb_assembly
        # 从 user 中提取窗口末条 HER 文本（<chat> JSON 行里最后一条 PARTNER）
        her_texts = []
        for ln in user.split("\n"):
            ln = ln.strip()
            if ln.startswith('{"id":"') and '"speaker":"PARTNER"' in ln:
                try:
                    her_texts.append(json.loads(ln)["text"])
                except Exception:
                    pass
        last = "；".join(her_texts) if her_texts else "（无）"
        reply = json.dumps({
            "response": {"recommended": "收到，%s" % last[:20],
                         "bad_boy": "行吧", "playful": "哈哈",
                         "warm": "嗯嗯，没事"},
            "directions": ["好", None, "到时候说", None],
            "analysis": {"topic_status": "new",
                         "topic_label": "剧本话题",
                         "scene_facts": [{"text": "她说：%s" % last,
                                          "source_ids": ["m0"]}],
                         "ongoing": []}},
            ensure_ascii=False)
        return {"text": reply, "finish": "stop", "usage": None}


def replay(ep_path, mech, assets_root, sandbox):
    os.makedirs(sandbox, exist_ok=True)
    ep = json.load(open(ep_path, encoding="utf-8"))
    h = mech.init(assets_root, ep, None, sandbox)
    gw = ScriptedGW()
    ledger = []
    for t in ep["turns"]:
        loader = getattr(mech, "_load_facts", None) or mech.base._load_facts
        before_facts = loader(h)
        out = mech.run_turn(h, t, gw)
        after_facts = loader(h)
        trs = []
        tr_p = os.path.join(sandbox, "retrieval_trace.jsonl")
        if os.path.exists(tr_p):
            trs = [json.loads(ln) for ln in open(tr_p, encoding="utf-8")]
        tr = trs[-1] if trs else {}
        ledger.append({
            "case_id": t["case_id"], "clock": t["input"]["clock"],
            "window_sha256": hashlib.sha256(json.dumps(
                t["input"]["messages"], ensure_ascii=False).encode()).hexdigest()[:16],
            "reply_ok": bool(out.get("reply")),
            "retrieved_fact_ids": (tr.get("injected") or {}).get("scene_chain_fact_ids"),
            "facts_n_before": len(before_facts),
            "facts_n_after": len(after_facts),
            "writeback": (tr.get("writeback") or {}),
            "may_decline": bool(t["input"].get("may_decline")),
            "decline_material_reached": ("允许不回复" in (gw.last_user or "")),
            "gold_leak": any(k in (gw.last_user or "") for k in
                             ("must_keywords", "soft_keywords", "expected_facts_note",
                              "_predict_failures", "gold", "观测点", "诊断观测")),
        })
    return ep, h, gw, ledger


def main():
    fails = []

    # ---- A. schema/时间线 ----
    for ep_path in (NCA_EP, NCB_EP):
        ep = json.load(open(ep_path, encoding="utf-8"))
        errs, warns = vpt.validate(ep)
        if errs:
            fails.append("A/%s: %s" % (ep["episode_id"], errs))
        if warns:
            fails.append("A/%s: 警告未清 %s" % (ep["episode_id"], warns))

    # ---- C/D. 完整 mock 回放（冻结检索） ----
    tmp = tempfile.mkdtemp(prefix="nca_ncb_replay_")
    all_ledger = {}
    for ep_path in (NCA_EP, NCB_EP):
        ep = json.load(open(ep_path, encoding="utf-8"))
        sb = os.path.join(tmp, ep["episode_id"])
        ep2, h, gw, ledger = replay(ep_path, phase1, P1_ASSETS, sb)
        all_ledger[ep["episode_id"]] = ledger
        for row in ledger:
            if not row["reply_ok"]:
                fails.append("C/%s: 无回复" % row["case_id"])
            if row["retrieved_fact_ids"] is None:
                fails.append("C/%s: 追踪缺失（retrieved 未记账）" % row["case_id"])
            if row["gold_leak"]:
                fails.append("B/%s: gold 泄漏进候选请求" % row["case_id"])
            if row["may_decline"] and not row["decline_material_reached"]:
                fails.append("D/%s: may_decline 材料未到达请求" % row["case_id"])
        if len(ledger) != len(ep["turns"]):
            fails.append("C/%s: 回放轮数不符" % ep["episode_id"])

    def _facts_by_text(sb, sub):
        return [i for i, f in enumerate(phase1._load_facts({"sandbox": sb}))
                if sub in f.get("text", "")]

    def _facts_by_text_m(mech, sb, sub):
        loader = getattr(mech, "_load_facts", None) or mech.base._load_facts
        return [i for i, f in enumerate(loader({"sandbox": sb}))
                if sub in f.get("text", "")]

    # D1: NCA t4 召回 t3 调岗事实（窗口引用旧事，正常）
    nca_sb = os.path.join(tmp, "NCA_CORR_INTENT_v1")
    led_nca = {r["case_id"]: r for r in all_ledger["NCA_CORR_INTENT_v1"]}
    dia = _facts_by_text(nca_sb, "调岗")
    if not dia:
        fails.append("D1/NCA: stub 未写回调岗事实，回放夹具失效")
    elif dia[0] not in (led_nca["NCA_t04"]["retrieved_fact_ids"] or []):
        fails.append("D1/NCA_t04: 调岗事实未被召回（窗口引用旧事应可召回）")
    # D2: NCA t5 append-only 诊断——纠正后旧事实仍可被召回（未实现失效机制，登记）
    zheng = _facts_by_text(nca_sb, "换项目组")
    if not zheng:
        fails.append("D2/NCA: stub 未写回更正事实")
    else:
        got5 = led_nca["NCA_t05"]["retrieved_fact_ids"] or []
        if dia[0] not in got5:
            fails.append("D2/NCA_t05: 纠正后旧'调岗'事实未被召回——若已实现失效机制，"
                         "本诊断断言需随机制更新（登记于 MECHANISM）")
    # D3: NCB t2 G7812 token 召回；D3145 事实在 t2 写回时才入库（写回前不可检索）
    ncb_sb = os.path.join(tmp, "NCB_ENTITY_DENSE_v1")
    led_ncb = {r["case_id"]: r for r in all_ledger["NCB_ENTITY_DENSE_v1"]}
    g_fact = _facts_by_text(ncb_sb, "G7812")
    if not g_fact:
        fails.append("D3/NCB: stub 未写回 G7812 事实")
    else:
        if g_fact[0] not in (led_ncb["NCB_t02"]["retrieved_fact_ids"] or []):
            fails.append("D3/NCB_t02: G7812 事实未被召回（token 通道）")
    d_fact = _facts_by_text(ncb_sb, "D3145")
    if not d_fact:
        fails.append("D3/NCB: stub 未写回 D3145 事实")
    elif d_fact[0] < led_ncb["NCB_t02"]["facts_n_before"]:
        fails.append("D3/NCB: D3145 事实在 t2 之前已在库（夹具污染）")
    elif d_fact[0] in (led_ncb["NCB_t02"]["retrieved_fact_ids"] or []):
        fails.append("D3/NCB_t02: D3145 写回前被检索（时序异常）")

    # ---- E. 表达候选委托（NCA 全程） ----
    expr_mech = _load("nca_expr", "candidates/phase2_expr/mechanism/mechanism.py")
    sb2 = os.path.join(tmp, "NCA_expr")
    _ep, _h, _gw, ledger2 = replay(NCA_EP, expr_mech, P2_ASSETS, sb2)
    if len(ledger2) != 6:
        fails.append("E1/表达候选 NCA 回放轮数不符")
    etr = os.path.join(sb2, "expr_trace.jsonl")
    if not os.path.exists(etr):
        fails.append("E2/表达候选未产出 expr_trace.jsonl")
    else:
        recs = [json.loads(ln) for ln in open(etr, encoding="utf-8")]
        if len(recs) != 6:
            fails.append("E2/expr_trace 行数=%d ≠ 6" % len(recs))
        if any(r["n_findings"] != 0 for r in recs):
            fails.append("E3/干净 stub 出现审计 findings: %s"
                         % [r["findings"] for r in recs if r["n_findings"]])
    # 表达候选与父版本检索逐字节一致（同一窗口序列）
    tr1 = open(os.path.join(nca_sb, "retrieval_trace.jsonl"), encoding="utf-8").read()
    tr2 = open(os.path.join(sb2, "retrieval_trace.jsonl"), encoding="utf-8").read()
    if tr1 != tr2:
        fails.append("E4/表达候选检索追踪与父版本不一致（检索未冻结）")

    # ---- F. 事件账落盘 ----
    out = {
        "scope": "NCA/NCB 完整 mock 回放事件账（零真实调用；mock 只证明链路不证明聊天质量）",
        "mechanism": "candidates/phase1_retr_idf (检索 v2.2 冻结) + phase2_expr 委托复核",
        "predicted_diagnostics": {
            "NCA_t05": "append-only：t3 调岗旧事实与 t4 更正事实并存可召回（未实现失效机制，登记）",
            "NCB_t02": "append-only：G7812 旧车次事实可召回，判读关注是否以 D3145 为准",
        },
        "ledger": all_ledger,
    }
    dest = os.path.join(ROOT, "logs", "nca_ncb_mock_replay_20261020.json")
    json.dump(out, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    if fails:
        print("FAIL %d 项：" % len(fails))
        for f in fails:
            print("  -", f)
        raise SystemExit(1)
    print("PASS：NCA/NCB schema/时间线 + 边界无gold泄漏 + 完整mock回放(11轮) + "
          "观测点记账(调岗并存/D3145纠正) + 表达候选委托一致 + 事件账落盘")


if __name__ == "__main__":
    main()
