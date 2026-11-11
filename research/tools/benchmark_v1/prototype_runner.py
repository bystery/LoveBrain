# -*- coding: utf-8 -*-
"""RESEARCH-P1 · 论文驱动原型轮位级运行器（W28-05，宪章 C5/C6）。

与 four_arm_runner（整集粒度）互补：本运行器按 DEV_FAST_SET 的**回复位置**（episode+turn）
执行，用于第 1 级 smoke_24 / 第 2 级 60 位快速筛选。复用全部中立基础设施：
  - lb_runner：sanitize_turn（消毒白名单）、load_mechanism/load_contract、
    require_candidate_assets、_exec_turn_v2（评分/calls/manifest 落盘）、integrity_manifest
  - lb_gateway：ModelGateway 强制记账（attempt 事件、usage、时延）
  - four_arm_runner：find_episode_path / candidate_identity_sha256 / count_attempts /
    bm1_mock_light（轻量契约 mock 替身）

轮位回放语义（对历史型机制精确等价）：目标轮之前的每一轮，用 ReplayStub（零网络、
零记账）调用候选自身 run_turn——候选按自己的代码路径把当轮剧本消息累积进可见历史；
目标轮用真实 adapter + _exec_turn_v2 完整执行（评分用评估侧原题快照 deepcopy）。
对"无跨轮生成状态"的机制（本批三原型均属此类），回放结果与整集运行在目标轮完全一致。

模式：plan 零网络（身份/计划）；mock 离线管道验证（bm1_mock_light）；live 真实调用
（前置：授权文件 + LB_API_KEY + provider check OK；全局 attempt cap，超限停批不补跑）。

纪律：任何失败保留原始状态与原因；不重试质量问题；usage/时延/状态/resp_id 全落盘；
候选可见面 = sanitize_turn 白名单，永不接触 gold/未来轮/acceptable_actions。
"""
import argparse
import copy
import datetime
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))
sys.path.insert(0, os.path.join(_REPO, "arena"))
sys.path.insert(0, _HERE)

import lb_gateway  # noqa: E402
import lb_runner  # noqa: E402
from lb_runner import (EP_META_VISIBLE_KEYS, ModelGateway, _exec_turn_v2,  # noqa: E402
                       load_contract, load_mechanism, require_candidate_assets,
                       sanitize_turn, validate_media_mapping, integrity_manifest,
                       _turn_dir)
import four_arm_runner  # noqa: E402  复用 find_episode_path/身份哈希/attempt 计数；顺带注册 bm1_mock_light

RUNNER_VERSION = "prototype_runner_v1"
DEFAULT_DEV_SET = os.path.join(_REPO, "refs", "research_harness_20261110", "phase1",
                               "benchmark", "DEV_FAST_SET.json")
DEFAULT_PROTOS = ("rh_b04_anchored_layout", "rh_b06_tiny_cascade", "rh_b09_extractive_slim")


class ReplayStub:
    """回放替身：零网络、零记账。仅驱动候选按自身代码路径累积可见历史；
    其返回值不会被本批机制用作输出。"""
    name = "replay_stub"

    def generate(self, system, user, payload):
        return {"text": "", "finish": "stop", "usage": None}


lb_gateway.ADAPTERS.setdefault("replay_stub", ReplayStub)


def load_positions(dev_set_path, which):
    with open(dev_set_path, "r", encoding="utf-8") as f:
        dev = json.load(f)
    by_id = {p["position_id"]: p for p in dev["positions"]}
    if which == "smoke_24":
        ids = list(dev["smoke_24"]["position_ids"])
    else:
        ids = [x.strip() for x in which.split(",") if x.strip()]
    bad = [i for i in ids if i not in by_id]
    if bad:
        raise SystemExit("position_id 不在 DEV_FAST_SET: %r" % bad)
    return [by_id[i] for i in ids], dev.get("set_id"), dev.get("sha256")


def resolve_turn_index(ep, case_id, turn_no):
    for i, t in enumerate(ep.get("turns") or []):
        if t.get("case_id") == case_id:
            if turn_no and int(turn_no) != i + 1:
                raise ValueError("turn_no(%s) 与 case_id 轮位(%d)不一致 %s"
                                 % (turn_no, i + 1, case_id))
            return i
    raise ValueError("case_id %s 不在 episode" % case_id)


def prior_real_messages(ep, upto_idx):
    n = 0
    for t in ep["turns"][:upto_idx]:
        for m in (t.get("input") or {}).get("messages") or []:
            if isinstance(m, dict) and m.get("role") in ("HER", "ME"):
                n += 1
    return n


def run_position(arm, cand_dir, pos, ep_cache, mode, adapter_name, out_dir,
                 auth_id=None):
    ep_id = pos["episode_id"]
    case_id = pos["case_id"]
    ep_path = find_episode(ep_id, ep_cache)
    ep = ep_cache[ep_id]["ep"]
    ti = resolve_turn_index(ep, case_id, pos.get("turn_no"))
    turn = ep["turns"][ti]
    pos_dir = os.path.join(out_dir, arm, "%s_T%02d" % (ep_id, ti + 1))
    rec = {"arm": arm, "position_id": pos["position_id"], "episode": ep_id,
           "case_id": case_id, "turn_index": ti + 1, "mode": mode,
           "candidate": os.path.basename(cand_dir.rstrip("/\\"))}
    if os.path.isfile(os.path.join(pos_dir, "position_manifest.json")):
        rec["status"] = "skipped_done"
        return rec
    require_candidate_assets(cand_dir)
    mech, origin, version = load_mechanism(cand_dir)
    if version != "v2":
        raise RuntimeError("原型运行器仅支持 v2 机制契约: %s" % arm)
    contract = load_contract(cand_dir)
    os.makedirs(pos_dir, exist_ok=True)
    sandbox = os.path.join(pos_dir, "_sandbox")
    os.makedirs(sandbox, exist_ok=True)
    assets_root = os.path.join(cand_dir, "assets")
    run_id = "rp1_%s_%s_%s" % (mode, arm, pos["position_id"])

    real_gw = ModelGateway(lb_gateway.ADAPTERS[adapter_name](),
                           event_log_path=os.path.join(pos_dir, "gateway_events.jsonl"))
    real_gw.run_id = run_id
    stub_gw = ModelGateway(lb_gateway.ADAPTERS["replay_stub"](), event_log_path=None)
    ep_meta = {k: ep[k] for k in EP_META_VISIBLE_KEYS if k in ep}

    handle = mech.init(assets_root, ep_meta, stub_gw, sandbox)
    try:
        for i in range(ti):                      # 回放：目标轮之前，零网络
            ctx = sanitize_turn(ep["turns"][i])
            ctx["assets_root"] = assets_root
            ctx["sandbox"] = sandbox
            ctx["turn_index"] = i + 1
            mech.run_turn(handle, ctx, stub_gw)
    except Exception as e:                       # noqa: BLE001
        rec.update({"status": "error", "phase": "replay",
                    "error": "%s: %s" % (type(e).__name__, e),
                    "note": "回放失败，该位置缺失，不伪造结果"})
        return rec

    # 目标轮：真实 adapter + 评估侧原题快照完整执行
    turn_eval = copy.deepcopy(turn)
    turn_dir = _turn_dir(pos_dir, 0, turn)
    real_gw.calls = []
    real_gw.directive = {}
    real_gw.set_run_context(run_id, case_id)
    validate_media_mapping(turn.get("input") or {}, case_id)
    ctx = sanitize_turn(turn)
    ctx["assets_root"] = assets_root
    ctx["sandbox"] = sandbox
    ctx["turn_type"] = turn.get("turn_type", "reply")
    ctx["turn_index"] = ti + 1
    t0 = datetime.datetime.now()
    try:
        m = _exec_turn_v2(turn_eval, ctx, cand_dir, turn_dir, real_gw, mech,
                          handle, contract, prior_real_n=prior_real_messages(ep, ti))
    except Exception as e:                       # noqa: BLE001
        rec.update({"status": "error", "phase": "target_turn",
                    "error": "%s: %s" % (type(e).__name__, e),
                    "note": "目标轮失败，该位置缺失，不伪造结果（calls/error 已由 lb_runner 落盘）"})
        return rec
    e2e_ms = int((datetime.datetime.now() - t0).total_seconds() * 1000)
    manifest = {
        "position_id": pos["position_id"], "episode_id": ep_id, "case_id": case_id,
        "turn_index": ti + 1, "arm": arm, "candidate": rec["candidate"],
        "mechanism": origin, "lifecycle": version, "contract": contract or "legacy",
        "adapter": adapter_name, "mode": mode, "run_id": run_id,
        "authorization": auth_id,
        "replay_turns_no_network": ti, "target_turn_real_calls": m["gateway_calls"],
        "tokens": m["tokens"], "gateway_calls": m["gateway_calls"],
        "e2e_ms": e2e_ms, "latency_ms_run_turn": m["latency_ms"],
        "score10": m["score10"], "veto": m["veto"],
        "mechanism_notes": m.get("mechanism_notes", ""),
        "integrity": integrity_manifest(ep_path, cand_dir),
        "dev_set": {"set_id": load_positions.set_id, "sha256": load_positions.set_sha},
        "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    }
    with open(os.path.join(pos_dir, "position_manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    rec.update({"status": "ok", "e2e_ms": e2e_ms, "tokens": m["tokens"],
                "score10": m["score10"], "veto": m["veto"],
                "notes": manifest["mechanism_notes"]})
    return rec


_EP_CACHE = {}


def find_episode(ep_id, ep_cache):
    if ep_id not in ep_cache:
        p = four_arm_runner.find_episode_path(ep_id)
        with open(p, "r", encoding="utf-8") as f:
            ep_cache[ep_id] = {"path": p, "ep": json.load(f)}
    return ep_cache[ep_id]["path"]


def _pct(vals, q):
    if not vals:
        return None
    s = sorted(vals)
    pos = (len(s) - 1) * q
    lo, hi = int(pos), min(int(pos) + 1, len(s) - 1)
    return int(round(s[lo] + (s[hi] - s[lo]) * (pos - lo)))


def summarize(records, mode, out_dir):
    ok = [r for r in records if r.get("status") == "ok"]
    e2e = [r["e2e_ms"] for r in ok]
    tok_p = sum((r.get("tokens") or {}).get("prompt") or 0 for r in ok)
    tok_c = sum((r.get("tokens") or {}).get("completion") or 0 for r in ok)
    esc = sum(1 for r in ok if "escalated=true" in str(r.get("notes", "")))
    return {"mode": mode, "runner_version": RUNNER_VERSION,
            "n_positions": len(records),
            "ok": len(ok), "error": len(records) - len(ok),
            "tokens_total": {"prompt": tok_p, "completion": tok_c},
            "e2e_ms_p50": _pct(e2e, 0.5), "e2e_ms_p95": _pct(e2e, 0.95),
            "escalated_positions": esc,
            "records": records}


def main():
    ap = argparse.ArgumentParser(description="RESEARCH-P1 prototype position runner")
    ap.add_argument("--mode", required=True, choices=["plan", "mock", "live"])
    ap.add_argument("--positions", default="smoke_24",
                    help="smoke_24 或逗号分隔 position_id")
    ap.add_argument("--arms", default=",".join(DEFAULT_PROTOS))
    ap.add_argument("--dev-set", default=DEFAULT_DEV_SET)
    ap.add_argument("--out", required=True)
    ap.add_argument("--authorization", default=None,
                    help="live 必填：RESEARCH-P1 授权 JSON（cap/model/positions 范围）")
    ap.add_argument("--base-url", default=None)
    args = ap.parse_args()

    positions, set_id, set_sha = load_positions(args.dev_set, args.positions)
    load_positions.set_id, load_positions.set_sha = set_id, set_sha
    arms = [a.strip() for a in args.arms.split(",") if a.strip()]
    cand_dirs = {a: os.path.join(_HERE, "prototype_candidates", a) for a in arms}
    for a, d in cand_dirs.items():
        if not os.path.isdir(d):
            raise SystemExit("未知原型候选: %s (%s)" % (a, d))

    auth_id = None
    cap = None
    adapter_name = None
    if args.mode == "live":
        if not args.authorization:
            raise SystemExit("live 模式必须提供 --authorization")
        with open(args.authorization, "r", encoding="utf-8") as f:
            auth = json.load(f)
        if auth.get("model") != "deepseek-flash-2":
            raise SystemExit("授权模型必须是 deepseek-flash-2")
        if not auth.get("retry_on_quality_failure") is False:
            raise SystemExit("retry_on_quality_failure 必须为 false")
        auth_positions = auth.get("positions")
        if auth_positions and args.positions != "smoke_24":
            extra = set(p["position_id"] for p in positions) - set(
                x.strip() for x in auth_positions.split(","))
            if extra:
                raise SystemExit("position 超出授权范围: %r" % sorted(extra))
        if not os.environ.get("LB_API_KEY"):
            raise SystemExit("LB_API_KEY 未设置——阻止真实调用（fail-closed）")
        import check_provider as cp
        res, _code = cp.run_check()
        cp.write_report(res, args.out)
        if res.get("verdict") != "OK":
            raise SystemExit("provider 检查未通过: %s" % res.get("verdict"))
        auth_id = auth.get("auth_id", os.path.basename(args.authorization))
        cap = auth.get("cap_max_total_api_attempts")
        adapter_name = "openai_compat"
        os.environ.setdefault("LB_API_BASE", args.base_url
                              or "https://api.llm.ustc.edu.cn/v1")
        os.environ.setdefault("LB_MODEL", "deepseek-flash-2")
    else:
        adapter_name = "bm1_mock_light"   # mock 管道验证（含轻量契约形状）
    os.makedirs(args.out, exist_ok=True)

    if args.mode == "plan":
        plan = {"runner_version": RUNNER_VERSION, "mode": "plan",
                "dev_set": {"set_id": set_id, "sha256": set_sha},
                "arms": {a: {"candidate_dir": os.path.relpath(d, _REPO).replace("\\", "/"),
                             "identity_sha256": four_arm_runner.candidate_identity_sha256(d)}
                         for a, d in cand_dirs.items()},
                "positions": [{"position_id": p["position_id"], "episode": p["episode_id"],
                               "case_id": p["case_id"], "turn_no": p.get("turn_no"),
                               "topics": p.get("topics")} for p in positions],
                "planned_real_calls_max": sum(
                    2 if a == "rh_b06_tiny_cascade" else 1 for _ in positions for a in arms),
                "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")}
        with open(os.path.join(args.out, "plan.json"), "w", encoding="utf-8") as f:
            json.dump(plan, f, ensure_ascii=False, indent=2)
        print("plan ok: %d positions × %d arms；planned_real_calls<=%d"
              % (len(positions), len(arms), plan["planned_real_calls_max"]))
        return 0

    records = []
    for pos in positions:                        # 串行：实际并发=1，不超授权
        if cap is not None:
            used = four_arm_runner.count_attempts(args.out)
            if used >= cap:
                with open(os.path.join(args.out, "LIVE_BLOCKED.json"), "w",
                          encoding="utf-8") as f:
                    json.dump({"blocked": True,
                               "reason": "attempt cap 到达（%d/%d）" % (used, cap),
                               "problems": [],
                               "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")},
                              f, ensure_ascii=False, indent=2)
                print("STOP: attempt cap %d/%d" % (used, cap))
                break
        for arm in arms:
            rec = run_position(arm, cand_dirs[arm], pos, _EP_CACHE, args.mode,
                               adapter_name, args.out, auth_id)
            records.append(rec)
            print("  %s/%s -> %s (e2e=%s score10=%s veto=%s)"
                  % (arm, pos["position_id"], rec.get("status"),
                     rec.get("e2e_ms"), rec.get("score10"), rec.get("veto")))
            with open(os.path.join(args.out, "run_records.json"), "w",
                      encoding="utf-8") as f:
                json.dump(records, f, ensure_ascii=False, indent=2)
    summary = summarize(records, args.mode, args.out)
    with open(os.path.join(args.out, "run_summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print("done mode=%s positions=%d ok=%d e2e_p50=%s e2e_p95=%s"
          % (args.mode, summary["n_positions"], summary["ok"],
             summary["e2e_ms_p50"], summary["e2e_ms_p95"]))
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
