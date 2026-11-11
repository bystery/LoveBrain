# -*- coding: utf-8 -*-
"""tools/probe_real_sim.py — 真实负载 sim 恢复门（替代失效的玩具探针）。

教训（2026-10-28）：玩具探针 {"ok":true} 三次 OK 但真实 sim 请求全部 150s 超时——
探针负载与真实负载差两个数量级，失去预测效力。本探针用 build_b3_simulator_request
构建与正式轨迹同构的真实请求（I1 场景+尊重式邀请），150s SLA 不变。
结果 = sim 派发的恢复信号：本探针成功才允许恢复派发。消耗照常记账。
"""
import argparse
import json
import os
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))
os.environ.setdefault("LB_API_BASE", "http://127.0.0.1:17770/v1")

from arena.lb_gateway import ADAPTERS, ModelGateway
from arena.isolation.authorization_budget import AuthorizationBudget


def _ledger_binding(ledger):
    try:
        with open(ledger, encoding="utf-8") as f:
            first = json.loads(f.readline())
        if first.get("kind") == "authorization_scope":
            return int(first["max_attempts"]), first.get("scope") or {}
    except Exception:                                      # noqa: BLE001
        pass
    return 96, {}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--aid", required=True)
    ap.add_argument("--run-tag", default=str(int(time.time())))
    ap.add_argument("--scenario", default="I1")
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    import build_b3_simulator_request as SIM
    scs = json.load(open(os.path.join(ROOT, "benchmark", "interaction_env_20261026",
                                      "scenarios.json"), encoding="utf-8"))["scenarios"]
    sc = next(s for s in scs if s["id"] == a.scenario)
    history = [{"who": "her", "text": sc["opening"]["text"]},
               {"who": "me", "text": "交稿周你先忙，等忙完这阵，哪天你方便一起看个展？你挑时间，我配合。"}]
    req = SIM.build(a.scenario, history, scenario_obj=sc)
    payload_chars = len(req["system"]) + len(req["user"])

    ledger = os.path.join(ROOT, "logs", "auth_budget", a.aid + ".jsonl")
    cap, scope = _ledger_binding(ledger)
    budget = AuthorizationBudget(ledger, a.aid, cap, scope=scope)
    budget.ensure_batch(1, purpose="partner_sim_probe_real")
    ev = os.path.join(ROOT, "logs", "p07_w09_20261028_a",
                      "PROBE_REAL_%s.jsonl" % a.run_tag)
    inner = ModelGateway(ADAPTERS["openai_compat"](), event_log_path=ev)
    aid = "p07:probe_real:%s:sim:1" % a.run_tag
    budget.reserve(aid, purpose="partner_simulation")
    t0 = time.time()
    try:
        res = inner.generate(req["system"], req["user"], model="qwen3.8-flash",
                             temperature=0.7, timeout_s=150, tag="sim", attempt_id=aid)
        text = (res.get("text") or "").strip()
        if not text:
            raise RuntimeError("empty_content_response")
        budget.record("attempt_completed", aid, model="qwen3.8-flash",
                      usage=res.get("usage"))
        obj = None
        try:
            obj = json.loads(text.strip().strip("`").lstrip("json").strip())
        except Exception:
            pass
        ok = bool(isinstance(obj, dict) and (obj.get("reply") or obj.get("state_delta")))
        print("PROBE_REAL_SIM", "OK" if ok else "PARSE_ODD(text=%s)" % text[:60],
              "%dms payload=%dchars" % (int((time.time() - t0) * 1000), payload_chars))
        return 0 if ok else 2
    except Exception as e:
        budget.record("attempt_failed", aid, error=str(e)[:150])
        print("PROBE_REAL_SIM FAIL", str(e)[:120],
              "%dms payload=%dchars" % (int((time.time() - t0) * 1000), payload_chars))
        return 1


if __name__ == "__main__":
    sys.exit(main())
