"""run_collapse_retry.py — 崩坏轮确定性重试（W21-02 判据冻结实施；判据见
refs/experiment_protocol_20261023/long_probe_20261030/W21_02_NEXT_BATCH_DESIGN_20261103.md）。

外层 mini-batch 架构：冻结执行器 exam_interleave_plan.py 一行不动（只 import 复用）。
  detect   扫描已完成批目录 → 崩坏位清单 RETRY_PLAN.json（零调用）。
           崩坏定义（冻结口径）：reply raw 为空(len(strip)==0) 或 json 解析失败且非空。
           可解析但 schema 违规（style_keys/directions 类 veto）不算崩坏——那是 guard 修复域。
  dispatch 对崩坏位以同题同臂 IsolatedRun 单轮重试：
           同一宿主进程内，turns 0..i-1 用进程内假 urlopen 走状态（回复不入 events，
           走步全部记【演练账本】），turn i 用真网关打（真重试账本，逐次 reserve）；
           打完后 calls.json 的 system+user 与基批该轮【逐字节比对】，不一致标记
           prompt_mismatch 且不计入重试解析。--mock 时真步也走假 urlopen（全零网络，
           端到端演练=L0 载体）。
  merge    合卷：每崩坏位取首个可解析者（base 优先），落 RETRY_RESOLUTION.json。

用法:
  python tools/run_collapse_retry.py detect --batch-dir logs/X [--out <json>]
  python tools/run_collapse_retry.py dispatch --batch-dir logs/X --retry-plan <json> \
      --auth-id AUTH-X --out logs/X_retry [--limit 1] [--mock]
  python tools/run_collapse_retry.py merge --batch-dir logs/X --retry-dir logs/X_retry \
      --retry-plan <json> [--out <json>]
"""
import argparse
import base64
import json
import os
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as epmod                                  # noqa: E402
from isolation.authorization_budget import AuthorizationBudget        # noqa: E402
from isolation.trusted_controller import IsolatedRun                  # noqa: E402

API_BASE = "http://127.0.0.1:17770/v1"
MODEL = "dsf"


def _load_json(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def _turn_no(turn_dir):
    return int(turn_dir.split("_")[1])


def _base_calls(base_dir, arm, story, turn_dir):
    p = os.path.join(ROOT, base_dir, "%s_%s" % (arm, story), "out", turn_dir, "calls.json")
    try:
        return _load_json(p)
    except Exception:
        return None


def _iso_auth_doc(auth_id, ledger, batch_need=1, max_attempts=None):
    return {"authorization_id": auth_id, "max_attempts": max_attempts or batch_need,
            "api_base": API_BASE, "model": MODEL, "temperature": 0.7,
            "status": "ACTIVE", "budget_ledger": ledger, "batch_need": batch_need}


def _mk_budget(ledger, auth_id, cap):
    os.makedirs(os.path.dirname(ledger), exist_ok=True)
    # scope 用默认（=authorization_id 派生）：begin_run 内部构造的 budget 与本对象
    # 必须在同一账本上范围一致（scope 首绑不可变）；重试的目的声明由冻结判据文档承担。
    return AuthorizationBudget(ledger, auth_id, cap, status="ACTIVE")


def dispatch_one(base_dir, pos, cand, out_base, auth_id, ledger, cap, mock=False,
                 api_base=API_BASE, model=MODEL):
    """单个崩坏位的重试：状态走查（演练）→ 真单轮 → prompt 逐字节校验。"""
    # 与冻结执行器同源的环境钉死（LB_API_BASE/LB_MODEL 取其 PINNED 常量）
    os.environ["LB_API_BASE"] = getattr(epmod, "PINNED_ENDPOINT", api_base)
    os.environ["LB_MODEL"] = getattr(epmod, "PINNED_MODEL", model)
    # 流式旗一律不设：走查假响应不可迭代（流式客户端会炸），真步走非流式
    # （W20-03 judge cal 先例；usage 照记，口径见 USAGE_UNIT_NOTE）
    os.environ.pop("LB_STREAM_TIMING", None)
    os.environ.pop("LB_STREAM_USAGE", None)
    story, arm, turn_dir = pos["story"], pos["arm"], pos["turn_dir"]
    i = _turn_no(turn_dir) - 1
    ep_src = os.path.join(ROOT, "benchmark", "episodes", story + ".json")
    # 每崩坏位独立目录/独立演练账本（new 模式拒绝复用有证据的输出目录；
    # 演练账本首绑 cap 不可变——共享会撞绑定位）
    base = os.path.join(out_base, "%s_%s_%s" % (arm, story, turn_dir))
    out = os.path.join(base, "out")
    run_dir = os.path.join(base, "_iso_run")
    control = os.path.join(out, "_control")
    os.makedirs(base, exist_ok=True)
    ep_copy = os.path.join(base, "%s.copy.json" % story)
    if not os.path.exists(ep_copy):
        import shutil
        shutil.copyfile(ep_src, ep_copy)
    turns = _load_json(ep_copy).get("turns") or []
    iso_auth = _iso_auth_doc(auth_id, ledger, batch_need=1, max_attempts=cap)
    iso = IsolatedRun(run_dir, out, control, cand, adapter="openai_compat",
                      quota=max(len(turns), i + 2), resume=False,
                      authorization=iso_auth)
    iso.stage()
    iso.start()
    walk_auth_id = "%s-WALK-%s" % (auth_id, turn_dir)
    walk_ledger = ledger.replace(".jsonl", ".%s.walk-rehearsal.jsonl" % turn_dir)
    walk_budget = _mk_budget(walk_ledger, walk_auth_id,
                             cap=max((i + 1) * 8, 8))  # 演练账本零网络，cap 从宽（proxy 每轮多次记账）
    real_budget = _mk_budget(ledger, auth_id, cap=cap)
    state = None
    try:
        state = iso.begin_run(ep_copy, resume=False, require_assets=True)
        # 预算挂载：走查步挂演练账本，真步挂重试账本。
        # 注意 TrustedProxy 在 begin_run 时捕获 budget（proxy.budget=self.budget），
        # 必须双挂载（iso.budget + iso._proxy.budget），否则走查 attempt 会记进主账本。
        iso.authorization = _iso_auth_doc(walk_auth_id, walk_ledger, batch_need=i)
        iso.budget = walk_budget
        iso._proxy.budget = walk_budget
        real_urlopen = urllib.request.urlopen
        captured = []
        if i > 0 or mock:
            urllib.request.urlopen = epmod.make_fake_urlopen(captured)
        try:
            for t in range(i):  # 状态走查：0..i-1，零网络、演练记账
                iso.step_one_turn(state, expect_turn=t + 1)
        finally:
            urllib.request.urlopen = real_urlopen
        # 真步：换挂重试账本；--mock 则保持假 urlopen（全零网络演练）
        iso.authorization = _iso_auth_doc(auth_id, ledger, batch_need=1, max_attempts=cap)
        iso.budget = real_budget
        iso._proxy.budget = real_budget
        if mock:
            urllib.request.urlopen = epmod.make_fake_urlopen(captured)
        try:
            manifest = iso.step_one_turn(state, expect_turn=i + 1)
        finally:
            urllib.request.urlopen = real_urlopen
        # prompt 逐字节校验：重试轮 calls vs 基批同轮 calls
        base_calls = _base_calls(base_dir, arm, story, turn_dir)
        new_calls = _load_json(os.path.join(
            out, sorted(x for x in os.listdir(out) if x.startswith("turn_"))[-1],
            "calls.json"))
        b = (base_calls or [{}])[0]
        n = (new_calls or [{}])[0]
        same = (b.get("user") == n.get("user")) and (b.get("system") == n.get("system"))
        return {"ok": True, "manifest_case_id": manifest.get("case_id"),
                "prompt_match": bool(same),
                "base_prompt_chars": b.get("prompt_chars"),
                "retry_prompt_chars": n.get("prompt_chars")}
    finally:
        try:
            iso.finish_run(state)
        except Exception:
            pass
        try:
            iso.close()
        except Exception:
            pass


def merge(base_dir, retry_dir, retry_plan_path, out=None):
    plan = _load_json(retry_plan_path)
    res = {"kind": "collapse_retry_resolution", "rule": "first parseable wins (base first)",
           "positions": []}
    for pos in plan["collapse_positions"]:
        story, arm, turn_dir = pos["story"], pos["arm"], pos["turn_dir"]
        entry = {"story": story, "arm": arm, "turn_dir": turn_dir}
        for label, root in (("base", base_dir), ("retry", retry_dir)):
            p = os.path.join(ROOT, root, "%s_%s_%s" % (arm, story, turn_dir), "out", turn_dir, "reply_raw.txt")
            try:
                raw = open(p, encoding="utf-8", errors="replace").read()
            except Exception:
                raw = ""
            parseable = False
            if raw.strip():
                try:
                    json.loads(raw)
                    parseable = True
                except Exception:
                    parseable = False
            entry[label] = {"len": len(raw.strip()), "parseable": parseable}
        entry["resolved_from"] = ("base" if entry["base"]["parseable"]
                                  else "retry" if entry["retry"]["parseable"]
                                  else "unresolved")
        res["positions"].append(entry)
    out = out or os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                              "long_probe_20261030",
                              os.path.basename(base_dir.rstrip("/")) + "_RETRY_RESOLUTION.json")
    json.dump(res, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    from collections import Counter
    c = Counter(e["resolved_from"] for e in res["positions"])
    print("merge:", dict(c), "| out:", out)
    return res


def _turn_dirs(batch_out, story, arm):
    d = os.path.join(ROOT, batch_out, "%s_%s" % (arm, story), "out")
    if not os.path.isdir(d):
        return []
    return sorted(x for x in os.listdir(d) if x.startswith("turn_"))


def detect(batch_dir, stories=None):
    """扫描批目录，返回崩坏位清单与分类统计（零调用）。"""
    base = os.path.join(ROOT, batch_dir)
    arms = ("parent", "candidate")
    if stories is None:
        stories = sorted({
            dn.split("_", 1)[1] for dn in os.listdir(base)
            if os.path.isdir(os.path.join(base, dn)) and
            dn.split("_", 1)[0] in arms})
    positions, stats = [], {"parent": {"n": 0, "empty": 0, "unparseable": 0},
                            "candidate": {"n": 0, "empty": 0, "unparseable": 0}}
    for story in stories:
        for arm in arms:
            for tn in _turn_dirs(batch_dir, story, arm):
                tdir = os.path.join(base, "%s_%s" % (arm, story), "out", tn)
                rec = {"batch_dir": batch_dir, "story": story, "arm": arm,
                       "turn_dir": tn, "raw_len": None, "cls": None,
                       "scoring_veto": None, "json_parseable": None}
                try:
                    raw = open(os.path.join(tdir, "reply_raw.txt"),
                               encoding="utf-8", errors="replace").read()
                except Exception:
                    raw = ""
                stripped = raw.strip()
                rec["raw_len"] = len(stripped)
                parseable = None
                if stripped:
                    try:
                        json.loads(raw)
                        parseable = True
                    except Exception:
                        parseable = False
                rec["json_parseable"] = parseable
                try:
                    sc = json.load(open(os.path.join(tdir, "scoring.json"),
                                        encoding="utf-8"))
                    rec["scoring_veto"] = sc.get("veto")
                except Exception:
                    pass
                if len(stripped) == 0:
                    rec["cls"] = "empty"
                    stats[arm]["empty"] += 1
                elif parseable is False:
                    rec["cls"] = "unparseable"
                    stats[arm]["unparseable"] += 1
                positions.append(rec)
                stats[arm]["n"] += 1
    plan = {
        "kind": "collapse_retry_plan",
        "definition": "empty OR (non-empty AND json unparseable)；可解析但 schema 违规不在重试域（guard 修复域）",
        "batch_dir": batch_dir,
        "stories": stories,
        "stats": stats,
        "collapse_positions": [p for p in positions if p["cls"]],
        "all_positions": positions,
    }
    return plan


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    d = sub.add_parser("detect")
    d.add_argument("--batch-dir", required=True)
    d.add_argument("--stories", default=None, help="逗号分隔；缺省=自动发现")
    d.add_argument("--out", default=None)
    dp = sub.add_parser("dispatch")
    dp.add_argument("--batch-dir", required=True)
    dp.add_argument("--retry-plan", required=True)
    dp.add_argument("--auth-id", required=True)
    dp.add_argument("--out", required=True, help="重试批输出目录")
    dp.add_argument("--cap", type=int, required=True, help="重试授权总头寸（预注册 ≤20%% base）")
    dp.add_argument("--limit", type=int, default=None, help="本次最多重试位数（默认全部）")
    dp.add_argument("--candidates", default=None,
                    help="臂→候选映射 parent=X,candidate=Y；缺省=基批 RESULT.json 的 arm_mapping")
    dp.add_argument("--mock", action="store_true", help="全零网络演练（真步也走假 urlopen）")
    mg = sub.add_parser("merge")
    mg.add_argument("--batch-dir", required=True)
    mg.add_argument("--retry-dir", required=True)
    mg.add_argument("--retry-plan", required=True)
    mg.add_argument("--out", default=None)
    a = ap.parse_args()
    if a.cmd == "detect":
        stories = a.stories.split(",") if a.stories else None
        plan = detect(a.batch_dir, stories)
        out = a.out or os.path.join(
            ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030",
            os.path.basename(a.batch_dir.rstrip("/")) + "_RETRY_PLAN.json")
        json.dump(plan, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        s = plan["stats"]
        print("detect: parent empty=%d unparseable=%d / n=%d | candidate empty=%d unparseable=%d / n=%d"
              % (s["parent"]["empty"], s["parent"]["unparseable"], s["parent"]["n"],
                 s["candidate"]["empty"], s["candidate"]["unparseable"], s["candidate"]["n"]))
        print("out:", out)
    elif a.cmd == "dispatch":
        plan = _load_json(a.retry_plan)
        arm_map = {}
        if a.candidates:
            for kv in a.candidates.split(","):
                k, v = kv.split("=", 1)
                arm_map[k] = v
        else:
            res_p = os.path.join(ROOT, a.batch_dir.rstrip("/")) + ".RESULT.json"
            am = _load_json(res_p).get("arm_mapping") or {}
            arm_map = {v: k for k, v in am.items()}  # arm_mapping: parent/candidate→名字? 存名字
            arm_map = am  # arm_mapping 直接是 {parent:名, candidate:名}
        ledger = os.path.join(ROOT, "logs", "auth_budget", a.auth_id + ".jsonl")
        os.makedirs(a.out, exist_ok=True)
        positions = plan["collapse_positions"]
        if a.limit:
            positions = positions[:a.limit]
        results = []
        for pos in positions:
            cand = arm_map.get(pos["arm"])
            if not cand:
                results.append({**pos, "error": "no candidate for arm %s" % pos["arm"]})
                continue
            try:
                r = dispatch_one(a.batch_dir, pos, cand, a.out, a.auth_id,
                                 ledger, a.cap, mock=a.mock)
                results.append({**pos, **r})
                print("dispatch %s %s %s: %s prompt_match=%s" % (
                    pos["arm"][:4], pos["story"], pos["turn_dir"],
                    "OK" if r.get("ok") else "FAIL", r.get("prompt_match")))
            except Exception as e:  # noqa: BLE001 — 单位失败不拖垮整批，如实记录
                results.append({**pos, "error": "%s: %s" % (type(e).__name__, str(e)[:200])})
                print("dispatch %s %s %s: ERROR %s" % (
                    pos["arm"][:4], pos["story"], pos["turn_dir"], str(e)[:120]))
        out = os.path.join(a.out, "DISPATCH_RESULT.json")
        json.dump({"kind": "collapse_retry_dispatch", "mock": a.mock,
                   "auth_id": a.auth_id, "results": results},
                  open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        ok = sum(1 for r in results if r.get("ok") and r.get("prompt_match"))
        print("dispatch done: %d/%d ok+prompt_match | out: %s" % (ok, len(results), out))
    elif a.cmd == "merge":
        merge(a.batch_dir, a.retry_dir, a.retry_plan, a.out)


if __name__ == "__main__":
    sys.exit(main())
