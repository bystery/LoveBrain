# -*- coding: utf-8 -*-
r"""tools/run_c_contrast_batch.py — 收口单 C：assertion_tiering 的三领域正反对照批（24 次生成）。

方法（先登记后派发，判据不看答案）：
  对照输入不是新编题，而是 F3 已付费真实位点的**实际出站 prompt**（取自 candidate 臂 calls.json）。
  base 臂 = 冻结 e3 的原始 prompt 逐字节；tier 臂 = 同一 prompt 里把【单 JSON 硬约束】整段
  替换为 e3 文本 + 292 码元的“依据分层”段（r5 加载后 r2._HYGIENE 实测前缀关系成立）。
  因此两臂除这一段外逐字节相同，差异只能来自分层指令本身。
  边界：本批在网关边界拼接，不重跑整链状态与记忆写回；它测的是“同一输入下正文的分层表达”，
  不声称端到端集成验证。

三类各 4 项：
  NEEDS_CHECK 该确认不乱保证：K01_T06/N02_T03/K01_T19/K01_T20（C 重判后确有 3 位点无据断言 + 1 同族）
  PROPOSAL   该推进不退缩：按 unit_id 字典序取卡面含“提/邀/约/钩/下一步”的前 4 位
  EVIDENCED  已确定不反复问：记忆链里“原子在本轮/窗口内可见且被用到”的 candidate 行，取前 4 位
"""
import argparse
import datetime as dt
import hashlib
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import knowledge_probe_precheck as kpp                    # noqa: E402
from lb_gateway import ADAPTERS, ModelGateway             # noqa: E402
from isolation.authorization_budget import (              # noqa: E402
    AuthorizationBudget, validate_authorization_doc, BudgetRefused)

OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "final_mainline_review_20261029")
RB = os.path.join(ROOT, "logs", "f3_newmat_live_restart_20261029")
NC = ["K01_T06", "N02_T03", "K01_T19", "K01_T20"]
CRIT = {
    "NEEDS_CHECK": "外部状态/第三方规则/未敲定条件：应写成“分支+触发条件+谁核实”，"
                   "不得说成既定；但也不得因此不推进",
    "PROPOSAL": "该给提议/钩子的位置：仍要给具体推进；不得退化成“你自己去确认/都行”",
    "EVIDENCED": "有逐字依据的既定值：应直接引用，不得反复反问已知信息",
}


def sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def turn_dir(cid):
    import glob
    sid, n = cid.split("_")[0], int(cid.split("_T")[1])
    for base in (os.path.join(RB, "candidate_%s_resume" % sid),
                 os.path.join(RB, "candidate_%s" % sid)):
        g = sorted(glob.glob(os.path.join(base, "turn_%02d_*" % n)))
        if g:
            return g[0]
    return None


def hitn(r):
    h = r.get("answer_token_hits")
    return len(h or []) if isinstance(h, list) else int(h or 0)


def select(mats, kc):
    props = sorted({it["unit_id"].split(":")[1] for it in mats if any(
        w in json.dumps([(it["payload"].get("card") or {}).get("current_action")] +
                        list((it["payload"].get("card") or {}).get("acceptable_actions") or []),
                        ensure_ascii=False)
        for w in ("提", "邀", "约", "钩", "下一步"))} - set(NC))
    ev = sorted({r["test_case"] for r in kc if r["arm"] == "candidate"
                 and (r.get("source_in_raw_history") or r.get("source_in_current"))
                 and hitn(r) > 0} - set(NC) - set(props))
    used = set(NC) | set(props[:4])
    if len(ev) < 4:  # 兜底规则（登记在案）：原子可见但不强求答案逐字命中
        ev += sorted({r["test_case"] for r in kc if r["arm"] == "candidate"
                      and (r.get("source_in_raw_history") or r.get("source_in_current"))
                      } - used - set(ev))
    ev = [x for i, x in enumerate(ev) if x not in ev[:i]]
    sys.stdout.write("buckets NC=%d PROP=%d EV=%d\n" % (len(NC), len(props[:4]), len(ev[:4])))
    return ([("NEEDS_CHECK", x) for x in NC] + [("PROPOSAL", x) for x in props[:4]]
            + [("EVIDENCED", x) for x in ev[:4]])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", default="live", choices=["mock", "live"])
    ap.add_argument("--authorization", default=os.path.join(
        OUT, "C_CONTRAST_AUTHORIZATION_20261029.json"))
    ap.add_argument("--timeout-s", type=int, default=180)
    # 默认 = 原路径，行为逐字不变；重跑/复测时指向新文件，避免覆写原始判据注册件。
    ap.add_argument("--registration-out", default=os.path.join(
        OUT, "F_C_CONTRAST_REGISTRATION.json"))
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    auth = json.load(open(a.authorization, encoding="utf-8"))
    e3 = kpp.load_mod("action_policy_r4_guard_e3")
    r5 = kpp.load_mod("action_policy_r5_tier")
    H3, H5 = e3.r2._HYGIENE, r5.r2._HYGIENE
    assert H5.startswith(H3), "tier 文本不是 e3 文本的超集，方法不成立"
    p = subprocess.run([sys.executable, "-X", "utf8", "-B",
                        os.path.join(ROOT, "tools", "check_identity_drift.py"),
                        "--candidate", "action_policy_r5_tier", "--freeze",
                        os.path.join(OUT, "F_R5_CANDIDATE_FREEZE_20261029.json"), "--flat"],
                       cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    iddoc = json.loads(p.stdout)
    if not iddoc.get("identical") or iddoc["current_identity_sha256"] != auth["scope"]["identity_sha256"]:
        raise SystemExit("r5 identity drift or mismatch: %s" % json.dumps(iddoc)[:300])

    mats = [json.loads(l) for l in open(os.path.join(
        OUT, "F3_MATERIALS.jsonl"), encoding="utf-8") if l.strip()]
    kc = [json.loads(l) for l in open(os.path.join(
        OUT, "F3_KNOWLEDGE_CHAIN.jsonl"), encoding="utf-8") if l.strip()]
    items = select(mats, kc)
    if len(items) != 12:
        raise SystemExit("expected 12 items, got %d" % len(items))

    reg_path = a.registration_out
    reg = {"kind": "c_contrast_registration", "criteria_written_before_dispatch": True,
           "method": "同一付费位点的实际出站 prompt，两臂只差“依据分层”段（前缀关系已断言）",
           "selection_rule": {"NEEDS_CHECK": NC,
                              "PROPOSAL": "卡面含 提/邀/约/钩/下一步 的位点按 unit_id 字典序取 4",
                              "EVIDENCED": "记忆链 candidate 行中原子可见于本轮/窗口且被用到的位点取 4"},
           "criteria": CRIT, "items": []}
    for tier, cid in items:
        td = turn_dir(cid)
        if not td:
            raise SystemExit("missing paid turn dir for %s" % cid)
        raw = json.load(open(os.path.join(td, "calls.json"), encoding="utf-8"))
        if len(raw) != 1:
            raise SystemExit("%s expects a single main call, got %d" % (cid, len(raw)))
        pbase = raw[0]["user"]
        if pbase.count(H3) != 1:
            raise SystemExit("hygiene anchor count != 1 at %s" % cid)
        ptier = pbase.replace(H3, H5)
        reg["items"].append({
            "tier": tier, "case_id": cid,
            "turn_dir": os.path.relpath(td, ROOT).replace("\\", "/"),
            "base_prompt_sha256": sha(pbase), "tier_prompt_sha256": sha(ptier),
            "base_cu": len(pbase.encode("utf-16-le")) // 2,
            "tier_cu": len(ptier.encode("utf-16-le")) // 2,
            "only_diff_len": len(ptier) - len(pbase),
            "criteria": CRIT[tier]})
    json.dump(reg, open(reg_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    reg_sha = sha(open(reg_path, encoding="utf-8").read())

    ts = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    outdir = os.path.join(ROOT, "logs", "c_contrast_%s_%s" % (a.mode, ts))
    os.makedirs(outdir, exist_ok=True)
    ep, md = (auth["real_endpoint"]["api_base"].rstrip("/"), auth["real_endpoint"]["model"])
    tp = float(auth["real_endpoint"]["temperature"])
    budget = None
    if a.mode == "live":
        try:
            validate_authorization_doc(auth, expected_endpoint=ep, expected_model=md,
                                       expected_temperature=tp)
        except BudgetRefused as e:
            raise SystemExit("AUTH REFUSED: %s" % e)
        if os.environ.get("LB_API_BASE", "").rstrip("/") not in ("", ep):
            raise SystemExit("LB_API_BASE 与授权单不一致")
        if os.environ.get("LB_MODEL", "") not in ("", md):
            raise SystemExit("LB_MODEL 与授权单不一致")
        os.environ.update({"LB_API_BASE": ep, "LB_MODEL": md, "LB_STREAM_TIMING": "0"})
        os.environ.setdefault("LB_API_KEY", "local")
        budget = AuthorizationBudget(os.path.join(ROOT, auth["budget_ledger"]),
                                    auth["authorization_id"],
                                    auth["real_endpoint"]["max_attempts"], scope=auth["scope"])
        budget.ensure_batch(len(reg["items"]) * 2, purpose="c_contrast_generation")
        gw = ModelGateway(ADAPTERS["openai_compat"](),
                          event_log_path=os.path.join(outdir, "GATEWAY_EVENTS.jsonl"))
    else:
        gw = ModelGateway(ADAPTERS["mock"](),
                          event_log_path=os.path.join(outdir, "GATEWAY_EVENTS.jsonl"))

    rows = []
    stop = None
    for it in reg["items"]:
        for arm in ("base", "tier"):
            prompt = None
            cell = "%s:%s:%s" % (it["tier"], it["case_id"], arm)
            cdir = os.path.join(outdir, "cell_" + cell.replace(":", "_"))
            os.makedirs(cdir, exist_ok=True)
            attempt_id = "ccontrast:%s:%s" % (it["case_id"], arm)
            rec = {"cell": cell, "tier": it["tier"], "case_id": it["case_id"], "arm": arm,
                   "attempt_id": attempt_id, "criteria": it["criteria"]}
            try:
                raw0 = json.load(open(os.path.join(ROOT, it["turn_dir"], "calls.json"),
                                      encoding="utf-8"))
                pbase = raw0[0]["user"]
                prompt = pbase if arm == "base" else pbase.replace(H3, H5)
                exp = it["base_prompt_sha256"] if arm == "base" else it["tier_prompt_sha256"]
                if sha(prompt) != exp:
                    raise RuntimeError("prompt hash drift before dispatch")
                if budget is not None:
                    budget.reserve(attempt_id, purpose="c_contrast_generation")
                res = gw.generate(auth["scope"].get("system_prompt_ref") or
                                  raw0[0].get("system") or "", prompt,
                                  model=md, temperature=tp, tag="c_contrast",
                                  attempt_id=attempt_id, timeout_s=a.timeout_s)
                text = res.get("text") or ""
                if budget is not None:
                    budget.record("attempt_completed", attempt_id, usage=res.get("usage"),
                                  raw_sha256=sha(text))
                obj = json.loads(text) if text.strip().startswith("{") else None
                prim = ""
                if isinstance(obj, dict):
                    resp = obj.get("response") or {}
                    for k in ("recommended", "warm", "playful", "bad_boy"):
                        if isinstance(resp.get(k), str) and resp[k].strip():
                            prim = resp[k].strip()
                            break
                    dirs = obj.get("directions")
                    if isinstance(dirs, list):
                        prim += " | " + " / ".join(str(x)[:60] for x in dirs if x)
                else:
                    prim = "[UNPARSED]" + text[:120]
                rec.update({"transport_ok": True, "usage": res.get("usage"),
                            "prompt_sha256": sha(prompt), "raw_sha256": sha(text),
                            "primary_and_dirs": prim,
                            "action": (obj or {}).get("action") if isinstance(obj, dict) else None})
                open(os.path.join(cdir, "reply_raw.txt"), "w", encoding="utf-8").write(text)
            except Exception as e:  # noqa: BLE001
                if budget is not None:
                    try:
                        budget.record("attempt_failed", attempt_id, error=str(e)[:180])
                    except Exception:
                        pass
                rec.update({"transport_ok": False,
                            "error": "%s: %s" % (type(e).__name__, e)})
                stop = stop or "fail_fast"
            rows.append(rec)
            open(os.path.join(outdir, "C_CONTRAST_ROWS.jsonl"), "a",
                 encoding="utf-8").write(json.dumps(rec, ensure_ascii=False) + "\n")
            print("%-10s %-9s %-5s ok=%-5s %s" % (
                rec["tier"], rec["case_id"], arm, rec.get("transport_ok"),
                (rec.get("primary_and_dirs") or rec.get("error") or "")[:118]))
            if stop:
                break
        if stop:
            break
    summary = {"kind": "c_contrast_result", "mode": a.mode,
               "authorization_id": auth["authorization_id"],
               "registration_sha256": reg_sha, "identity_sha256": iddoc["current_identity_sha256"],
               "outdir": os.path.relpath(outdir, ROOT).replace("\\", "/"),
               "planned": len(reg["items"]) * 2, "done": len(rows),
               "transport_ok": sum(1 for r in rows if r.get("transport_ok")),
               "stopped": stop, "rows": rows}
    json.dump(summary, open(os.path.join(outdir, "C_CONTRAST_RESULT.json"), "w",
                            encoding="utf-8"), ensure_ascii=False, indent=1)
    print("SUMMARY planned=%d done=%d ok=%d stopped=%s reg=%s" % (
        summary["planned"], summary["done"], summary["transport_ok"], stop, reg_sha[:12]))
    return 0 if (not stop and summary["transport_ok"] == summary["planned"]) else 2


if __name__ == "__main__":
    raise SystemExit(main())
