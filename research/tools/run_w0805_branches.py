# -*- coding: utf-8 -*-
"""tools/run_w0805_branches.py — W08-05 受控连续分支实跑（钩位三档锚注入 vs 自然基线）。

结构：前缀轮（1..hook-1）真实生成一次 → r1 snapshot → 每 register 分支 restore 后
从 hook 轮继续（hook 轮注入锚文本作为 ME 已发消息，续 3 轮真实生成）→ 对比后续质量。
被测：冠军 r7_supersede。真实网关 + 预算逐次记账。
"""
import copy
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))

OUT = os.path.join(ROOT, "logs", "w0805_branches_20261031")
RESULTS_NAME = os.environ.get("W0805_RESULTS", "BRANCH_RESULTS.json")
ANCHORS = json.load(open(os.path.join(
    ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
    "W08-03_FLIRT_ANCHORS_20261031.json"), encoding="utf-8")) if os.path.exists(
    os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                 "W08-03_FLIRT_ANCHORS_20261031.json")) else {}

# 钩位定义：story, hook_turn(注入位), 前缀终点=hook-1, 续跑轮数
POSITIONS = [
    {"pos": "E032_T12_private_transport", "story": "E032", "hook": 12, "tail": 3,
     "anchors": {
         "catch": "接。三月起我周末往市里跑一趟，老位置改你家楼下。",
         "half": "接。不过丑话说前头，我这车只拉熟人和柿饼——你正好都算。",
         "door": "接。你先看看去了市里住哪，通勤的事到时候再说，急不着。",
     }},
    {"pos": "E029_T07_qingdao", "story": "E029", "hook": 7, "tail": 3,
     "anchors": {
         "catch": "九月有年假。你机票都查了，我总不能拖后腿。",
         "half": "有假。这么着，你把日期圈出来，我去把年假批了，AA 那条也照旧。",
         "door": "九月有没有假得看项目放不放人，你先别把票订死。",
     }},
    # --- W08-04 深聊位（replace_m 模式：锚替换 hook+1 轮 input 的 M 行） ---
    {"pos": "D1_E030_T05_argument", "story": "E030", "hook": 5, "tail": 3, "mode": "replace_m",
     "anchors": {
         "good": "54页那段引的是旧数据，我回去翻翻有没有新版，翻得到算你对——但转折确实得服。",
         "flat": "都有道理吧，看书嘛各自有各的感受。",
         "bad": "54页论据弱？那是你没读懂作者的反讽。",
     }},
    {"pos": "D2_E030_T07_vulnerable", "story": "E030", "hook": 7, "tail": 3, "mode": "replace_m",
     "anchors": {
         "good": "台阶是我自己想下的——你那些问题才是真问题，他们问的全在书上。",
         "flat": "没事，谁第一次主讲都紧张，下次就顺了。",
         "bad": "你是不是有点社恐？下期我帮你挡问题。",
     }},
    {"pos": "D3_E029_T05_shift", "story": "E029", "hook": 5, "tail": 3, "mode": "replace_m",
     "anchors": {
         "good": "青岛。人少，海风冷但干净。真想去的话九月最合适，那边的海不晒。",
         "flat": "哈哈海边好啊，那跑量可不轻。",
         "bad": "突然说走就走，你是不是工作上出什么事了？",
     }},
]


def load_mod():
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "r7_branch", os.path.join(ROOT, "candidates", "action_policy_r7_supersede",
                                  "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main():
    from arena.lb_gateway import ADAPTERS, ModelGateway  # noqa: E402
    from arena.isolation.authorization_budget import AuthorizationBudget  # noqa: E402
    aid = os.environ.get("W0805_AUTH", "AUTH-W0805-BRANCHES-20261031-01")
    ledger = os.path.join(ROOT, "logs", "auth_budget", aid + ".jsonl")
    budget = AuthorizationBudget(ledger, aid, max_attempts=80, status="ACTIVE",
                                 scope={"scope_id": "w0805_branches",
                                        "purpose": "W08-05 controlled branch runs: 2 positions x (prefix + 3 registers + baseline) x <=3 tail turns, champion r7_supersede, fail-fast",
                                        "automatic_candidate_generations": False,
                                        "endpoint": "http://127.0.0.1:17770/v1", "model_alias": "dsf"})
    os.environ["LB_API_BASE"] = "http://127.0.0.1:17770/v1"
    os.environ["LB_MODEL"] = "dsf"
    os.environ.setdefault("LB_API_KEY", "local")
    os.makedirs(OUT, exist_ok=True)
    ev = os.path.join(OUT, "GATEWAY_EVENTS.jsonl")
    gw = ModelGateway(ADAPTERS["openai_compat"](), event_log_path=ev)
    mod = load_mod()
    STAGE_ASSETS = os.path.join(ROOT, "logs", "longprobe_f1_live_20261030",
                                "candidate_E061", "_iso_run", "staged",
                                "candidates", "reference_product_v1", "assets")

    def gen(h, ctx, tag):
        aid2 = "%s:%s" % (aid, tag)
        budget.reserve(aid2, purpose="w0805_turn")
        r = mod.run_turn(h, ctx, gw)
        budget.record("attempt_completed", aid2)
        raw = r.get("reply") if isinstance(r.get("reply"), str) else ""
        try:
            obj = json.JSONDecoder().raw_decode(raw[raw.index("{"):])
            reply = (obj[0].get("response") or {}).get("recommended", "")
        except Exception:
            reply = raw[:400]
        return reply

    only = os.environ.get("W0805_ONLY")
    results = {}
    for pos in POSITIONS:
        if only and pos["pos"] not in only.split(","):
            continue
        story, hook, tail = pos["story"], pos["hook"], pos["tail"]
        ep = json.load(open(os.path.join(ROOT, "benchmark", "episodes", "%s.json" % story),
                            encoding="utf-8"))
        turns = ep["turns"]
        # 1) 前缀 replay 到 hook-1（真实生成）
        sandbox = os.path.join(OUT, pos["pos"], "prefix_sandbox")
        os.makedirs(sandbox, exist_ok=True)
        h = mod.init(os.path.join(STAGE_ASSETS, ""), ep, gw, sandbox)
        prefix_log = []
        for i in range(1, hook):
            t = turns[i - 1]
            ctx = {"input": {"messages": t["input"]["messages"],
                             "clock": t["input"].get("clock", ""),
                             "may_decline": t["input"].get("may_decline", False),
                             "intent": t["input"].get("intent")},
                   "turn_index": i}
            reply = gen(h, ctx, "%s:prefix:T%02d" % (pos["pos"], i))
            prefix_log.append({"turn": i, "reply": reply})
        snap = mod.snapshot(h)
        her_hook = turns[hook - 1]["input"]["messages"]
        variants = dict(pos["anchors"])
        variants["baseline"] = None
        pos_out = {"prefix": prefix_log, "her_hook": [m.get("text") for m in her_hook],
                   "branches": {}}
        for vname, anchor in variants.items():
            h2 = mod.restore(sandbox, snap)
            branch = {"hook_reply": anchor or ""}
            replace_m = pos.get("mode") == "replace_m"
            # hook 轮不生成：锚文本并入下一轮输入（replace_m=替换 M 行；否则前置 ME 行）
            for j in range(hook + 1, min(hook + 1 + tail, len(turns) + 1)):
                t = turns[j - 1]
                msgs = list(t["input"]["messages"])
                if j == hook + 1 and anchor:
                    if replace_m:
                        msgs = [{"role": ("ME" if m.get("role") == "ME" else m.get("role")),
                                 "text": (anchor if m.get("role") == "ME" else m.get("text"))}
                                for m in msgs]
                    else:
                        msgs = [{"role": "ME", "text": anchor}] + msgs
                ctx = {"input": {"messages": msgs, "clock": t["input"].get("clock", ""),
                                 "may_decline": t["input"].get("may_decline", False),
                                 "intent": t["input"].get("intent")},
                       "turn_index": j}
                reply = gen(h2, ctx, "%s:%s:T%02d" % (pos["pos"], vname, j))
                branch["T%02d" % j] = reply
            pos_out["branches"][vname] = branch
        results[pos["pos"]] = pos_out
        json.dump(results, open(os.path.join(OUT, RESULTS_NAME), "w",
                                encoding="utf-8"), indent=1, ensure_ascii=False)
        print(pos["pos"], "done", flush=True)
    budget.close(reason="W08-05 branches complete")
    print("ALL DONE ->", os.path.join(OUT, RESULTS_NAME))


if __name__ == "__main__":
    main()
