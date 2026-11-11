# -*- coding: utf-8 -*-
# tools/run_stream_integration_v1.py — 流式分段计时【真实链路】8 次小集成（包D.5）
#
# 目的：在正式 OpenAICompatAdapter + ModelGateway + execute_plan 的真实出站路径上，
# 证明 2 臂 × 4 固定代表位的 stream/stream_options 出站、SSE+usage 回传、分段落盘、
# 消费验证后 first_usable 真实打点。这是【测量集成】，不是性能判决；n=4/臂不判谁更快。
#
# 复用既有执行器 tools/exam_interleave_plan.execute_plan（逐 slot 交错、整批 ensure_batch
# 首发预检、fail-fast、无重试/补配对/第三臂/自动 judge），不另造调度器。
# 派发前硬拒绝：缺授权文件 / authorized!=true / 身份不符 → 零派发。
#
# 用法：
#   D:/anaconda/python.exe tools/run_stream_integration_v1.py --emit-plan    # 仅铺计划
#   D:/anaconda/python.exe tools/run_stream_integration_v1.py --offline       # 零网络演练（替换 HTTP）
#   D:/anaconda/python.exe tools/run_stream_integration_v1.py --live          # 8 次真实生成
import argparse
import copy
import json
import os
import sys
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "integration_preview"))

import exam_interleave_plan as ep
import lb_gateway
import lb_stream_timing
from lb_stream_timing import StreamTimer
import engine_adapter

REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                    "stream_integration_20261025")
AUTH = os.path.join(REFS, "AUTHORIZATION.json")
PLAN = os.path.join(REFS, "INTERLEAVE_PLAN.json")

PINNED_ENDPOINT = "http://127.0.0.1:17770/v1"
PINNED_MODEL = "dsf"
PINNED_TEMPERATURE = 0.7

# 4 个固定代表位：episode(合成单轮、源自冻结真实事件) / case_id / pair 先手
POSITIONS = [
    ("STREAM_E007_T01", "E007_T01", "parent_first", "short_low_information_opener"),
    ("STREAM_E008_T06", "E008_T06", "candidate_first", "multi_message_turn"),
    ("STREAM_E009_T07", "E009_T07", "parent_first", "long_context_decision_turn"),
    ("STREAM_E007_T08", "E007_T08", "candidate_first", "closing_turn"),
]
ARM_MAP = {"parent": "reference_product_v1", "candidate": "v5_purpose_aligned"}
CARDS = "benchmark/dev_expansion_v01/04_GRADING_CARDS_E007_E012.json"
ARM_ORDER = {"parent_first": ("parent", "candidate"),
             "candidate_first": ("candidate", "parent")}


def build_cfg():
    return ep._norm_cfg({
        "batch_id": "BATCH-STREAM-INT-20261025",
        "arm_map": ARM_MAP,
        "episode_order": [{"episode": e, "arm_order": o}
                          for e, _c, o, _k in POSITIONS],
        "cards_path": CARDS,
        "recall_capable": False,
    })


def build_plan(cfg=None):
    """铺 8 次 attempt（2 臂 × 4 位）交错计划，附冻结身份（当前运行时）。"""
    cfg = cfg or build_cfg()
    attempts, seq = [], 0
    for ep_name, case_id, order, kind in POSITIONS:
        for arm in ARM_ORDER[order]:
            seq += 1
            attempts.append({
                "seq": seq, "episode": ep_name, "turn": 1, "case_id": case_id,
                "arm": arm, "pair_key": case_id,
                "within_pair_pos": 1 if arm == ARM_ORDER[order][0] else 2,
                "arm_order_in_ep": order, "position_kind": kind,
            })
    assert len(attempts) == 8, len(attempts)
    frozen = ep.collect_frozen_files(cfg)
    doc = {
        "protocol": "WORKER_EXECUTION 包D.5 — 流式真实链路 8 次小集成（measurement-only）",
        "arm_mapping": cfg["arm_map"],
        "episode_order": [{"episode": e, "arm_order": o} for e, _c, o, _k in POSITIONS],
        "batch": {"batch_id": cfg["batch_id"], "cards_path": CARDS,
                  "recall_capable": False},
        "total_attempts": 8,
        "positions": [{"episode": e, "case_id": c, "kind": k, "arm_order": o}
                      for e, c, o, k in POSITIONS],
        "no_auto_retry": True, "no_makeup_paired_run": True,
        "fail_fast_on_transmission_error": True, "third_arm": False, "auto_judge": False,
        "execution_entry": "arena.isolation.trusted_controller.IsolatedRun (execute_plan mode=live)",
        "api_config_identity": lb_gateway.STREAM_CONFIG_IDENTITY["stream"],
        "child_env": {"LB_STREAM_TIMING": "1", "LB_STREAM_USAGE": "1"},
        "n4_disclaimer": "每臂 n=4 仅证明 stream/usage/分段/消费可用，不作任臂更快的性能结论",
        "frozen": {
            "identity_closure": {
                arm: {"identity_sha256": v["identity_sha256"], "closure": v["closure"],
                      "file_count": len(v["files"]), "files": v["files"]}
                for arm, v in frozen["identity_closure"].items()},
            "config_files": frozen["config_files"],
        },
        "attempts": attempts,
    }
    return doc


# ── 真实路径测量插桩：出站体记录 + 完成即验证后 first_usable 打点 ──────────────
CAPTURED_BODIES = []


def install_outbound_recorder():
    """包一层真实 urlopen：记录每个出站请求体（证明 stream/stream_options），再转发真实网络。"""
    real = urllib.request.urlopen

    def recorder(req, timeout=None, *a, **k):
        try:
            CAPTURED_BODIES.append(json.loads(req.data.decode("utf-8")))
        except Exception:
            CAPTURED_BODIES.append({"_unparsed": True})
        return real(req, timeout=timeout, *a, **k) if timeout is not None else real(req, *a, **k)
    recorder._real = real
    urllib.request.urlopen = recorder
    return real


def make_recording_fake_transport(lines_fn):
    """零网络演练：替身 urlopen —— 记录出站体并返回 SSE（替换 HTTP，真实分支代码路径）。"""
    fake_state = {"calls": 0}

    class _SSEResp:
        def __init__(self, lines):
            self._lines = lines

        def __iter__(self):
            return iter([l.encode("utf-8") if isinstance(l, str) else l for l in self._lines])

        def read(self):
            return b""

        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

    def fake(req, timeout=None, *a, **k):
        body = json.loads(req.data.decode("utf-8"))
        CAPTURED_BODIES.append(body)
        fake_state["calls"] += 1
        return _SSEResp(lines_fn(body, fake_state["calls"]))
    fake.state = fake_state
    return fake


_ORIG_AS_DICT = StreamTimer.as_dict


def install_usable_verifier():
    """在 completion 之后、返回 timing 之前【执行一次真实消费验证】并就地打 first_usable。
    用原型消费器 OutputConsumer.parse 判 response.recommended 是否为有效首选；
    验证完成的那一刻即 mark_usable —— 绝不事后回填，无有效首选则 None。"""
    def patched(self):
        if self.stream_requested and not getattr(self, "_integrated_verified", False):
            parsed = engine_adapter.OutputConsumer.parse(self.buffer or "")
            rec = (parsed.get("slots") or {}).get("recommended")
            valid = isinstance(rec, str) and rec.strip() != ""
            self.mark_usable(valid, reason=None if valid else
                             ("consumer_no_valid_primary:%s" % parsed.get("status")))
            self._integrated_verified = True
            self._consumer_status = parsed.get("status")
        return _ORIG_AS_DICT(self)
    StreamTimer.as_dict = patched


def uninstall_usable_verifier():
    StreamTimer.as_dict = _ORIG_AS_DICT


def _load_auth(rehearsal_ledger=None):
    if not os.path.isfile(AUTH):
        raise SystemExit("缺授权文件 %s —— 拒绝派发（零请求）" % AUTH)
    auth = json.load(open(AUTH, encoding="utf-8"))
    if auth.get("authorized") is not True or auth.get("status") != "ACTIVE":
        raise SystemExit("授权非 authorized=true/ACTIVE —— 拒绝派发")
    if auth["real_endpoint"]["api_base"] != PINNED_ENDPOINT \
            or auth["real_endpoint"]["model"] != PINNED_MODEL \
            or abs(float(auth["real_endpoint"]["temperature"]) - PINNED_TEMPERATURE) > 1e-9:
        raise SystemExit("授权端点/model/temperature 与固定服务不符 —— 拒绝派发")
    if auth.get("api_config_identity") != lb_gateway.STREAM_CONFIG_IDENTITY["stream"]:
        raise SystemExit("授权 api_config_identity 与当前运行时流式身份不符 —— 拒绝派发")
    led = auth.get("budget_ledger")
    if not os.path.isabs(led):
        led = os.path.join(ROOT, led)
    if rehearsal_ledger:
        led = rehearsal_ledger
    auth["budget_ledger"] = led
    return auth


def run(mode, out_base, plan, transport=None):
    os.environ["LB_STREAM_TIMING"] = "1"
    os.environ["LB_STREAM_USAGE"] = "1"
    install_usable_verifier()
    auth = _load_auth(rehearsal_ledger=(out_base + "/_rehearsal_ledger.jsonl")
                      if mode == "offline" else None)
    restore = None
    if mode == "live":
        restore = install_outbound_recorder()
    try:
        res = ep.execute_plan(plan, mode="live", authorization=auth,
                              out_base=out_base, transport=transport,
                              do_guards=False)
    finally:
        if restore is not None:
            urllib.request.urlopen = restore
        uninstall_usable_verifier()
    return res, auth


def collect_attempt_detail(plan, out_base):
    rows = []
    for r in plan["attempts"]:
        base = os.path.join(out_base, "%s_%s" % (r["arm"], r["episode"]))
        td = os.path.join(base, "out", "turn_01_%s" % r["case_id"])
        cf = os.path.join(td, "calls.json")
        entry = {"seq": r["seq"], "position": r["case_id"], "arm": r["arm"],
                 "arm_harness": ARM_MAP[r["arm"]], "position_kind": r["position_kind"],
                 "episode_file": r["episode"]}
        if os.path.isfile(cf):
            calls = json.load(open(cf, encoding="utf-8"))
            main = next((c for c in calls if c.get("tag", "main") == "main"), None) or (calls[0] if calls else {})
            entry["resp_id"] = main.get("resp_id")
            entry["resp_model"] = main.get("resp_model")
            entry["usage"] = main.get("usage")
            entry["timing"] = main.get("timing")
            entry["raw_text"] = main.get("raw_text", "")
            entry["finish"] = main.get("finish")
            entry["latency_ms"] = main.get("latency_ms")
            mf = os.path.join(td, "manifest.json")
            if os.path.isfile(mf):
                entry["case_input_sha256"] = json.load(open(mf, encoding="utf-8")).get("case_input_sha256")
        else:
            entry["error"] = "calls.json missing"
        rows.append(entry)
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--emit-plan", action="store_true")
    ap.add_argument("--offline", action="store_true")
    ap.add_argument("--live", action="store_true")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    cfg = build_cfg()
    plan = build_plan(cfg)
    os.makedirs(REFS, exist_ok=True)
    json.dump(plan, open(PLAN, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("plan: attempts=%d arms=%s positions=%s api_cfg=%s" % (
        plan["total_attempts"], plan["arm_mapping"],
        [p["case_id"] for p in plan["positions"]], plan["api_config_identity"]))
    if args.emit_plan:
        return

    out_base = args.out or os.path.join(
        ROOT, "logs", "stream_int_%s_20261025" % ("live" if args.live else "offline"))

    transport = None
    if args.offline:
        # 零网络：替换 HTTP，返回合法 SSE（含 usage），验证真实分支代码路径
        BODY = ('{"response":{"recommended":"好，那周六见，我提前到门口等你",'
                '"bad_boy":"就这么定了","playful":"x","warm":"y"},'
                '"directions":["a","b",null,"d"],"analysis":{}}')

        def lines_fn(body, n):
            out = [": ping\n", "\n",
                   "data: %s\n\n" % json.dumps({"id": "reh-%d" % n, "model": PINNED_MODEL,
                                                "choices": [{"index": 0, "delta": {"role": "assistant"}}]})]
            out.append("data: %s\n\n" % json.dumps({"id": "reh-%d" % n, "model": PINNED_MODEL,
                                                    "choices": [{"index": 0, "delta": {"content": BODY}}]}))
            out.append("data: %s\n\n" % json.dumps({"id": "reh-%d" % n, "model": PINNED_MODEL,
                                                    "choices": [{"index": 0, "finish_reason": "stop"}]}))
            out.append("data: %s\n\n" % json.dumps({"id": "reh-%d" % n, "model": PINNED_MODEL,
                                                    "usage": {"prompt_tokens": 100 + n,
                                                              "completion_tokens": 20}}))
            out.append("data: [DONE]\n\n")
            return out
        transport = make_recording_fake_transport(lines_fn)

    res, auth = run("live" if args.live else "offline", out_base, plan, transport=transport)
    detail = collect_attempt_detail(plan, out_base)
    summary = {
        "mode": res["mode"], "authorization_id": res.get("authorization_id"),
        "attempts_planned": res["attempts_planned"],
        "attempt_started": res["attempt_started"], "attempt_completed": res["attempt_completed"],
        "attempt_failed": res["attempt_failed"], "stopped": res["stopped"],
        "execution_order_verified": res["execution_order_verified"],
        "dispatch_bijection": res["dispatch_bijection"], "state_isolated": res["state_isolated"],
        "residents": res["residents"], "real_model_calls": res["real_model_calls"],
        "network": res["network"], "api_config_identity": lb_gateway.STREAM_CONFIG_IDENTITY["stream"],
        "captured_bodies": CAPTURED_BODIES, "attempts_detail": detail,
        "out_base": out_base,
    }
    tag = "live" if args.live else "offline"
    outj = os.path.join(REFS, "RUN_%s.json" % tag)
    json.dump(summary, open(outj, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("run done: started=%s completed=%s failed=%s real_model_calls=%s stopped=%s order=%s" % (
        res["attempt_started"], res["attempt_completed"], res["attempt_failed"],
        res["real_model_calls"], res["stopped"], res["execution_order_verified"]))
    print("wrote", outj)


if __name__ == "__main__":
    main()
