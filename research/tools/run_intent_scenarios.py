# -*- coding: utf-8 -*-
"""tools/run_intent_scenarios.py — W06-03 意图差额场景实跑器（S1-S5，真实网关）。

每场景 = 同故事两个 intent 变体（variantA/variantB episode 注入版），冠军 r7_supersede
逐轮 run_turn（真实模型调用，预算逐次 reserve/record）。输出：每变体逐轮回复 + goal 块渲染
证据（notes 的 goal_effective/status），供 W06-01 判据（输出行为差异）判读。
"""
import copy
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))

OUT = os.path.join(ROOT, "logs", "w0603_intent_scenarios_20261031")


def load_candidate():
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "r7_scenario", os.path.join(ROOT, "candidates", "action_policy_r7_supersede",
                                    "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def inject_intent(ep, spec):
    """spec: {turn_no(1-based): intent_cfg_or_str} — 深拷贝注入。"""
    ep2 = copy.deepcopy(ep)
    for t in ep2.get("turns", []):
        m = re.search(r"T(\d+)$", str(t.get("case_id", "")))
        if not m:
            continue
        no = int(m.group(1))
        if no in spec:
            if spec[no] is None:
                t["input"].pop("intent", None)
            else:
                t["input"]["intent"] = spec[no]
        else:
            t["input"].pop("intent", None)
    return ep2


import re  # noqa: E402

SCENARIOS = {
    # S1 目标中途关闭：E021 约球位。A=ACTIVE T01-06 后 CLOSED；B=ACTIVE 全程
    "S1_E021": {
        "story": "E021",
        "A": {i: {"enabled": True, "status": "ACTIVE",
                  "text": "本周内和她敲定下次打球的固定场地和时间"} for i in range(1, 7)} | {
              i: {"enabled": True, "status": "CLOSED",
                  "text": "本周内和她敲定下次打球的固定场地和时间"} for i in range(7, 13)},
        "B": {i: {"enabled": True, "status": "ACTIVE",
                  "text": "本周内和她敲定下次打球的固定场地和时间"} for i in range(1, 13)},
    },
    # S2 ADJUSTED 改向：E033 装修预算 35万→37万（T06 起）
    "S2_E033": {
        "story": "E033",
        "A": {i: {"enabled": True, "status": "ADJUSTED" if i >= 6 else "ACTIVE",
                  "text": ("帮她把装修预算控制在 37 万但地板不减配" if i >= 6
                           else "帮她把装修预算控制在 35 万")} for i in range(1, 13)},
        "B": {i: {"enabled": True, "status": "ACTIVE",
                  "text": "帮她把装修预算控制在 35 万"} for i in range(1, 13)},
    },
    # S3 反馈变化：E029 邀约位 T07 起三版——A 正反馈 / B 含糊（对照取含糊；正反馈走 C 组另册）
    "S3_E029": {
        "story": "E029",
        "A": {i: {"enabled": True, "status": "ACTIVE",
                  "text": "约她这周末出来吃饭，她上次反应积极"} for i in range(1, 13)},
        "B": {i: {"enabled": True, "status": "ACTIVE",
                  "text": "约她这周末出来吃饭，她上次反应含糊没给准话"} for i in range(1, 13)},
    },
    # S4 无目标差额：A 无 intent；B 有 intent（检验无块时不发明目标 vs 有块正常推进）
    "S4_E026": {
        "story": "E026",
        "A": {},
        "B": {i: {"enabled": True, "status": "ACTIVE",
                  "text": "认真考虑她的康复科建议并给出可行安排"} for i in range(1, 13)},
    },
    # S5 旧目标复活陷阱：E022 T11 起注入 EXPIRED 同文本
    "S5_E022": {
        "story": "E022",
        "A": {i: {"enabled": True, "status": "EXPIRED" if i >= 11 else "ACTIVE",
                  "text": "落实卷子复盘的时间安排"} for i in range(1, 13)},
        "B": {i: {"enabled": True, "status": "ACTIVE",
                  "text": "落实卷子复盘的时间安排"} for i in range(1, 13)},
    },
}


def run_scenario(mod, name, spec, gw, budget):
    story = spec["story"]
    ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", "%s.json" % story),
                        encoding="utf-8"))
    out = {"scenario": name, "story": story, "variants": {}}
    for vname in ("A", "B"):
        epv = inject_intent(ep, spec[vname])
        assets = os.path.join(STAGE_ASSETS, "")
        sandbox = os.path.join(OUT, name, vname, "sandbox")
        os.makedirs(sandbox, exist_ok=True)
        h = mod.init(assets, epv, gw, sandbox)
        turns_out = []
        for i, t in enumerate(epv.get("turns", []), start=1):
            aid = "%s:%s:%s:T%02d" % (name, vname, story, i)
            try:
                budget.reserve(aid, purpose="w0603_scenario_turn")
            except Exception as e:  # noqa: BLE001
                turns_out.append({"turn": i, "error": "budget:%s" % e})
                break
            inp = {"messages": t["input"]["messages"], "clock": t["input"].get("clock", ""),
                   "may_decline": t["input"].get("may_decline", False),
                   "intent": t["input"].get("intent")}
            ctx = {"input": inp, "turn_index": i}
            r = mod.run_turn(h, ctx, gw)
            raw_reply = r.get("reply") if isinstance(r.get("reply"), str) else ""
            reply = ""
            try:
                obj = json.JSONDecoder().raw_decode(raw_reply[raw_reply.index("{"):])
                reply = (obj[0].get("response") or {}).get("recommended", "")
            except Exception:
                reply = raw_reply[:400]
            turns_out.append({"turn": i, "intent_status": (inp.get("intent") or {}).get("status")
                              if isinstance(inp.get("intent"), dict) else ("NONE" if not inp.get("intent") else "STR"),
                              "reply": reply, "reply_raw_head": raw_reply[:120],
                              "notes_digest": (r.get("notes") or "")[:200]})
            budget.record("attempt_completed", aid)
        out["variants"][vname] = turns_out
    return out


STAGE_ASSETS = None


def main():
    global STAGE_ASSETS
    STAGE_ASSETS = os.path.join(ROOT, "logs", "longprobe_f1_live_20261030",
                                "candidate_E061", "_iso_run", "staged",
                                "candidates", "reference_product_v1", "assets")
    mod = load_candidate()
    sys.path.insert(0, ROOT)
    from arena.lb_gateway import ADAPTERS, ModelGateway  # noqa: E402 — 与批次管线同一真实网关
    from arena.isolation.authorization_budget import AuthorizationBudget  # noqa: E402
    aid = "AUTH-W0603-SCENARIOS2-20261031-01"
    ledger = os.path.join(ROOT, "logs", "auth_budget", aid + ".jsonl")
    budget = AuthorizationBudget(ledger, aid, max_attempts=130, status="ACTIVE",
                                 scope={"scope_id": "w0603_intent_scenarios",
                                        "purpose": "W06-03 S1-S5 intent delta scenario runs, champion r7_supersede, 5 scenarios x 2 variants x <=12 turns, fail-fast",
                                        "automatic_candidate_generations": False,
                                        "endpoint": "http://127.0.0.1:17770/v1", "model_alias": "dsf"})
    ev = os.path.join(OUT, "GATEWAY_EVENTS.jsonl")
    os.makedirs(OUT, exist_ok=True)
    os.environ["LB_API_BASE"] = "http://127.0.0.1:17770/v1"
    os.environ["LB_MODEL"] = "dsf"
    os.environ.setdefault("LB_API_KEY", "local")
    gw = ModelGateway(ADAPTERS["openai_compat"](), event_log_path=ev)
    results = {}
    for name, spec in SCENARIOS.items():
        results[name] = run_scenario(mod, name, spec, gw, budget)
        json.dump(results, open(os.path.join(OUT, "SCENARIOS_RESULTS.json"), "w",
                                encoding="utf-8"), indent=1, ensure_ascii=False)
        print(name, "done", flush=True)
    budget.close(reason="W06-03 scenarios complete")
    print("ALL DONE ->", os.path.join(OUT, "SCENARIOS_RESULTS.json"))


if __name__ == "__main__":
    main()
