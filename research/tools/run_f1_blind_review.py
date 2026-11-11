#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""F1: run the 158-call blinded review (16 controls + 130 history + 12 swaps).

Uses the existing ModelGateway + AuthorizationBudget. One physical request per
attempt; failures are recorded and never retried. Controls run first as the load
gate, then the rest with concurrency 3.

Usage:
  python tools/run_f1_blind_review.py --mode live
  python tools/run_f1_blind_review.py --mode mock --limit 4
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import sys
import threading
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))

OUT = os.path.join(ROOT, "refs/experiment_protocol_20261023/final_mainline_review_20261029")
MATERIALS = os.path.join(OUT, "F1_MATERIALS.jsonl")
PRIVATE = os.path.join(OUT, "F1_PRIVATE_BINDINGS.json")
AUTH_PATH = os.path.join(OUT, "F1_AUTHORIZATION_20261029.json")
AID = "AUTH-F1-BLIND-20261029-01"
MODEL = "hy3"
TEMPERATURE = 0.0
ENDPOINT = "http://127.0.0.1:17770/v1"
CAP = 158
DIMS = ["goal", "factuality", "attribution", "time_state", "boundary",
        "memory_use", "interaction", "initiative", "emotion_relation"]

JUDGE_SYSTEM = """你是聊天回复的盲评裁判。你会看到同一份冻结输入、同一张评审卡下的两个匿名回复 A/B。你的任务是逐维打分并判断哪一个更适合发送，或是否打平/证据不足。

## 纪律
1. 只依据给定材料（画像/初始事实、前情、当轮输入、评审卡、A/B 原文）判断。不要猜版本、模型、成本、旧分数，也不要预测用户是否愿意发送。
2. 受保护表达：调侃、玩笑、新提议、条件句、承认未知、观点表达、合理旧事回扣、合适的短回复、合法不回（may_decline=true 且卡面允许）。不得因为“没核实/没澄清/没追问”就判失败。
3. 只有把无据细节说成已核实、把提议说成确认、把取消/完成说成仍有效、把第三人转述安到 HER/ME 头上，才判相应事实/主体/状态错误。
4. 先看冻结窗口最后一条是谁说的、卡面 current_action 要求什么、may_decline 是否允许不回。若窗口最后一条是 ME 且没有新的 HER 消息，不要机械要求回复；若卡面与窗口/最后说话人冲突，card_conflict=true 并说明，不要强行选赢家。
5. 不同维度各有胜负时，按一级质量（事实/主体/时序/目标/边界）优先；风格、长度、语气不是胜负依据。允许多种正确表达。
6. 只有证据支持一方明显更好才选 A/B；证据不足或两边都合理写 tie/uncertain。

## 九维（0—3，或 null+na_reasons）
- goal：当前有效目标是否变成合适动作，能启用/停止/切换；无显式目标按当前聊天动作，不强迫推进关系。
- factuality：具体断言是否有据、与已知是否冲突；观点/玩笑/新提议/条件句受保护。
- attribution：事实/亲历/偏好究竟属于 ME/HER/第三人；来源存在≠归属正确。
- time_state：消息时间 vs 事件时间、提议 vs 确认、取消/完成/重开。
- boundary：未发送草稿、私密意图、指导是否被谎称已发生/已同意；她划的边界是否被尊重。
- memory_use：本轮需要的历史有没有保住、找到、用对；无关旧事是否污染。
- interaction：对象/语境合适、不是军师旁白、值得对方回应。
- initiative：该展开时能提供具体新切口/轻负担入口；收尾轮不要求加问号/钩子。
- emotion_relation：接情绪、松紧节奏、调侃/调情/深入/邀约及依据反馈调整。

0=明显错误或反向；1=部分到位但关键欠缺；2=合理完成；3=具体且有实质增益；null=该维无适用分母（必须写 na_reasons）。

## claims
每条可核断言给：quote（被评答案逐字片段）、type（事实/观点/新提议/玩笑/条件/疑问/不确定）、support（supported/conflict/unknown/na）、evidence_quote（输入材料逐字证据，找不到留空）、severity（none/minor/major）、reason。主体、时间、否定、更正状态分别核对。

## 输出（严格 JSON，无解释文字、无 markdown 围栏）
{
  "unit_id": "输入里的 unit_id",
  "A": {"scores": {九键齐全}, "na_reasons": {}, "claims": [], "action_fit": "appropriate|acceptable|bad|uncertain", "major_errors": ["..."]},
  "B": {"scores": {九键齐全}, "na_reasons": {}, "claims": [], "action_fit": "appropriate|acceptable|bad|uncertain", "major_errors": ["..."]},
  "verdict": "A|B|tie|uncertain",
  "card_conflict": false,
  "conflict_note": "",
  "uncertainties": []
}
A/B 的 scores 必须九键齐全，值=0/1/2/3 或 null；null 必须在 na_reasons 给理由。没有 major error 就给空数组。"""


def sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def read_materials():
    return [json.loads(l) for l in open(MATERIALS, encoding="utf-8") if l.strip()]


def parse_json(raw):
    try:
        return json.loads(raw), None
    except Exception as e1:  # noqa: BLE001
        try:
            import normalize_reply_contract as nrc
            obj, _repairs = nrc.repair_json(raw)
            if obj is not None:
                return obj, None
        except Exception:
            pass
        return None, str(e1)


def check_schema(parsed, unit_id):
    why = []
    if not isinstance(parsed, dict):
        return False, "not a dict"
    if parsed.get("unit_id") != unit_id:
        why.append("unit_id mismatch")
    for arm in ("A", "B"):
        a = parsed.get(arm)
        if not isinstance(a, dict):
            why.append("%s missing" % arm)
            continue
        sc = a.get("scores")
        if not isinstance(sc, dict) or set(sc.keys()) != set(DIMS):
            why.append("%s.scores keys mismatch" % arm)
        else:
            for k, v in sc.items():
                if v is not None and v not in (0, 1, 2, 3):
                    why.append("%s.scores[%s]=%r" % (arm, k, v))
        nr = a.get("na_reasons")
        if not isinstance(nr, dict):
            why.append("%s.na_reasons not dict" % arm)
            nr = {}
        if isinstance(sc, dict):
            for k, v in sc.items():
                if v is None and k not in nr:
                    why.append("%s null without na_reason: %s" % (arm, k))
        if not isinstance(a.get("claims", []), list):
            why.append("%s.claims not list" % arm)
        if a.get("action_fit") not in ("appropriate", "acceptable", "bad", "uncertain", None):
            why.append("%s.action_fit invalid" % arm)
    if parsed.get("verdict") not in ("A", "B", "tie", "uncertain"):
        why.append("verdict invalid")
    if not isinstance(parsed.get("card_conflict"), bool):
        why.append("card_conflict not bool")
    if not isinstance(parsed.get("uncertainties", []), list):
        why.append("uncertainties not list")
    return (not why), "; ".join(why) or "ok"


def auth_doc():
    return {
        "kind": "authorization_registration",
        "authorization_id": AID,
        "authorized": True,
        "status": "ACTIVE",
        "granted_by": "用户2026-10-29持续实验授权；F1有限目的登记（裁判复核），不新增总帽、不复活旧单。",
        "phase": "F1",
        "budget_ledger": "logs/auth_budget/%s.jsonl" % AID,
        "real_endpoint": {
            "api_base": ENDPOINT,
            "model": MODEL,
            "temperature": TEMPERATURE,
            "max_attempts": CAP,
        },
        "api_config": "api_cfg:openai_compat_stream_false_v1；hy3 单一裁判模型，整批同模型；非流式。",
        "scope": {
            "purpose": "8控制×正反序=16 + 130历史成对盲评 + 12换序=158；只审候选/原版成对答卷，不生成新答卷。",
            "stage": "F1",
            "experiment": "F1_blind_historical_review",
            "allowed_models": [MODEL],
            "forbidden_models": ["hy4-preview", "gemini-3.8-flash", "qwen3.8-flash", "dsf"],
            "endpoint": ENDPOINT,
            "total_attempts": CAP,
            "units": {"control": 16, "historical": 130, "swap": 12},
            "fail_fast": False,
            "no_retry": True,
            "no_makeup": True,
            "closed_history_not_reused": True,
        },
        "registered_at": "2026-10-29T09:00:00",
    }


def ensure_auth():
    if not os.path.exists(AUTH_PATH):
        with open(AUTH_PATH, "w", encoding="utf-8") as f:
            json.dump(auth_doc(), f, ensure_ascii=False, indent=1)
            f.write("\n")
    return load(AUTH_PATH)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", default="live", choices=("live", "mock"))
    ap.add_argument("--limit", type=int, default=None)
    ap.add_argument("--concurrency", type=int, default=3)
    ap.add_argument("--resume", default=None, help="output dir to resume")
    ap.add_argument("--timeout-s", type=int, default=300)
    ap.add_argument("--controls-only", action="store_true",
                    help="run the 16 controls and stop; resume the same outdir for the rest")
    ap.add_argument("--exclude-units", default=None,
                    help="逗号分隔的 unit_id：不补跑（用于进程被异常终止时已 started "
                         "但无结果的孤儿 attempt；失败消耗不退款，禁止静默重试）")
    args = ap.parse_args()

    items = read_materials()
    if len(items) != 158:
        raise SystemExit("expected 158 items, got %d" % len(items))
    controls = [x for x in items if x["kind"] == "control"]
    rest = [x for x in items if x["kind"] != "control"]
    if len(controls) != 16 or len(rest) != 142:
        raise SystemExit("control/rest split wrong: %d/%d" % (len(controls), len(rest)))
    if args.limit:
        controls = controls[:args.limit]
        rest = []
    if args.controls_only:
        rest = []

    if args.mode == "live":
        ad = ensure_auth()
        from arena.isolation.authorization_budget import (
            AuthorizationBudget, validate_authorization_doc, BudgetRefused)
        try:
            validate_authorization_doc(ad, expected_endpoint=ENDPOINT,
                                       expected_model=MODEL,
                                       expected_temperature=TEMPERATURE)
        except BudgetRefused as e:
            raise SystemExit("AUTH REFUSED: %s" % e)
        os.environ["LB_API_BASE"] = ENDPOINT
        os.environ["LB_MODEL"] = MODEL
        os.environ.setdefault("LB_API_KEY", "local")
        os.environ["LB_STREAM_TIMING"] = "0"
        ledger = os.path.join(ROOT, ad["budget_ledger"])
        budget = AuthorizationBudget(ledger, AID, CAP, scope=ad["scope"])
        outdir = args.resume or os.path.join(
            ROOT, "logs", "f1_blind_review_%s" % time.strftime("%Y%m%d_%H%M%S"))
        os.makedirs(outdir, exist_ok=True)
        done = set()
        ratings_path = os.path.join(outdir, "F1_RATINGS.jsonl")
        if args.resume and os.path.exists(ratings_path):
            for l in open(ratings_path, encoding="utf-8"):
                if l.strip():
                    done.add(json.loads(l)["unit_id"])
        plan = [x for x in controls + rest if x["unit_id"] not in done]
        excluded = {x.strip() for x in (args.exclude_units or "").split(",") if x.strip()}
        if excluded:
            unknown = excluded - {x["unit_id"] for x in controls + rest}
            if unknown:
                raise SystemExit("exclude-units 含未知单元: %s" % sorted(unknown))
            plan = [x for x in plan if x["unit_id"] not in excluded]
            with open(os.path.join(outdir, "EXCLUDED_ORPHANS.json"), "w",
                      encoding="utf-8") as f:
                json.dump({"excluded": sorted(excluded),
                           "reason": "attempt_started but no completed rating before "
                                     "host stop; not refilled (no silent retry)"},
                          f, ensure_ascii=False, indent=1)
                f.write("\n")
            print("EXCLUDED %d orphan units: %s" % (len(excluded), sorted(excluded)))
        try:
            budget.ensure_batch(len(plan), purpose="f1_blind_review")
        except BudgetRefused as e:
            raise SystemExit("BUDGET REFUSED: %s" % e)
    else:
        # mock mode: no budget, no network
        outdir = os.path.join(ROOT, "logs", "f1_blind_review_mock_%s"
                              % time.strftime("%Y%m%d_%H%M%S"))
        os.makedirs(outdir, exist_ok=True)
        budget = None
        plan = controls + rest

    from arena.lb_gateway import ADAPTERS, ModelGateway
    io_lock = threading.Lock()
    tls = threading.local()
    records = []
    stop_reason = [None]

    def gateway():
        gw = getattr(tls, "gw", None)
        if gw is None:
            gw = ModelGateway(ADAPTERS["openai_compat"](),
                              event_log_path=os.path.join(
                                  outdir, "GATEWAY_EVENTS_%s.jsonl"
                                  % threading.current_thread().name))
            tls.gw = gw
        return gw

    def call_one(i, item):
        uid = item["unit_id"]
        attempt_id = "judge:F1:%s:%d" % (uid, i)
        if budget is not None:
            try:
                budget.reserve(attempt_id, purpose="f1_blind_review")
            except Exception as e:  # noqa: BLE001
                return {"unit_id": uid, "attempt_id": attempt_id,
                        "transport_ok": False, "error": "budget: %s" % e,
                        "raw": ""}, "budget"
        user = json.dumps(item["payload"], ensure_ascii=False)
        t0 = time.time()
        raw, err, usage = "", None, None
        if args.mode == "mock":
            raw = json.dumps(stub_judge(item), ensure_ascii=False)
        else:
            try:
                gw = gateway()
                gw.set_run_context("f1_blind_review", uid)
                res = gw.generate(system=JUDGE_SYSTEM, user=user, model=MODEL,
                                  temperature=TEMPERATURE, tag="f1_judge",
                                  attempt_id=attempt_id, timeout_s=args.timeout_s)
                raw = res.get("text") or ""
                usage = res.get("usage")
            except Exception as e:  # noqa: BLE001
                err = "%s: %s" % (type(e).__name__, e)
                raw = err
        latency = int((time.time() - t0) * 1000)
        parsed, perr = parse_json(raw)
        schema_ok, schema_why = (False, "parse failed") if parsed is None \
            else check_schema(parsed, uid)
        rec = {
            "unit_id": uid, "kind": item["kind"], "attempt_id": attempt_id,
            "transport_ok": err is None, "error": err,
            "parse_ok": parsed is not None, "parse_error": perr,
            "schema_ok": schema_ok, "schema_why": schema_why,
            "raw": raw, "parsed": parsed, "usage": usage,
            "latency_ms": latency, "mode": args.mode,
            "system_sha256": sha(JUDGE_SYSTEM),
            "user_sha256": sha(user),
        }
        if budget is not None:
            try:
                if err is None:
                    budget.record("attempt_completed", attempt_id, usage=usage,
                                  raw_sha256=sha(raw))
                else:
                    budget.record("attempt_failed", attempt_id, error=err[:200])
            except Exception:
                pass
        return rec, None

    def stub_judge(item):
        # deterministic schema-complete stub for wiring tests only
        scores = {k: 2 for k in DIMS}
        return {"unit_id": item["unit_id"],
                "A": {"scores": scores, "na_reasons": {}, "claims": [],
                      "action_fit": "acceptable", "major_errors": []},
                "B": {"scores": scores, "na_reasons": {}, "claims": [],
                      "action_fit": "acceptable", "major_errors": []},
                "verdict": "tie", "card_conflict": False,
                "conflict_note": "", "uncertainties": ["stub"]}

    # Controls first: load gate. Sequential, so a failure stops before bulk spend.
    for i, item in enumerate(plan):
        if item["kind"] != "control":
            continue
        rec, stop = call_one(i, item)
        with io_lock:
            with open(os.path.join(outdir, "F1_RATINGS.jsonl"), "a", encoding="utf-8") as f:
                f.write(json.dumps(rec, ensure_ascii=False) + "\n")
        records.append(rec)
        if stop:
            stop_reason[0] = stop
            break
        if not rec["transport_ok"]:
            stop_reason[0] = "transport_fail_on_control"
            break
    if stop_reason[0]:
        with open(os.path.join(outdir, "F1_SUMMARY.json"), "w", encoding="utf-8") as f:
            json.dump({"stopped": stop_reason[0], "records": len(records),
                       "outdir": os.path.relpath(outdir, ROOT)}, f,
                      ensure_ascii=False, indent=1)
        print("STOPPED:", stop_reason[0])
        return 2

    rest_plan = [x for x in plan if x["kind"] != "control"]
    if args.concurrency <= 1:
        for i, item in enumerate(rest_plan, start=len(records)):
            rec, stop = call_one(i, item)
            with io_lock:
                with open(os.path.join(outdir, "F1_RATINGS.jsonl"), "a", encoding="utf-8") as f:
                    f.write(json.dumps(rec, ensure_ascii=False) + "\n")
            records.append(rec)
    else:
        ex = concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency)
        futures = {}
        try:
            for i, item in enumerate(rest_plan, start=len(records)):
                futures[ex.submit(call_one, i, item)] = item
            for fut in concurrent.futures.as_completed(futures):
                rec, _ = fut.result()
                with io_lock:
                    with open(os.path.join(outdir, "F1_RATINGS.jsonl"), "a", encoding="utf-8") as f:
                        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                records.append(rec)
        finally:
            ex.shutdown(wait=True)

    summary = {
        "mode": args.mode, "planned": len(plan), "completed": len(records),
        "transport_fail": sum(1 for r in records if not r["transport_ok"]),
        "parse_fail": sum(1 for r in records if not r["parse_ok"]),
        "schema_fail": sum(1 for r in records if r["parse_ok"] and not r["schema_ok"]),
        "control_calls": sum(1 for r in records if r["kind"] == "control"),
        "historical_calls": sum(1 for r in records if r["kind"] == "historical"),
        "swap_calls": sum(1 for r in records if r["kind"] == "swap"),
        "system_sha256": sha(JUDGE_SYSTEM),
        "model": MODEL, "temperature": TEMPERATURE,
        "endpoint": ENDPOINT, "cap": CAP,
        "outdir": os.path.relpath(outdir, ROOT),
        "budget": budget.summary() if budget is not None else None,
        "stopped": None,
    }
    with open(os.path.join(outdir, "F1_SUMMARY.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("F1 review: planned=%d completed=%d transport_fail=%d parse_fail=%d schema_fail=%d"
          % (len(plan), len(records), summary["transport_fail"],
             summary["parse_fail"], summary["schema_fail"]))
    print("outdir:", outdir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
