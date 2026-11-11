# -*- coding: utf-8 -*-
r"""tools/run_knowledge_probe_batch.py — 收口单 B：跨窗口知识探针正式批（最多 24 次真实生成）。

严格复用现有件（不新造调度器、不新造交付口径）：
  - 状态：已付费 F3 候选链 checkpoint（只读恢复）；
  - 夹具轮：tools/knowledge_probe_precheck.py 的 FixtureGW/ctx_for/filler_log（零网络，不入账本）；
  - 出站：arena/lb_gateway.ModelGateway（openai_compat；dsf/0.7；非流式；与 F3 正式批同一份
    api 配置身份 api_cfg:openai_compat_stream_false_v1）；
  - 额度：arena/isolation/authorization_budget.AuthorizationBudget——1 物理请求=1 attempt，
    失败计消耗、不重试、不补跑，批末由 --close 收口；
  - 交付文本：arena/api_candidate.store_reply + tools/build_f3_blind_materials.reply_payload
    （与 F3 盲评同一套"机制交给消费者的文本"口径）。

每格派发前硬断言：机制实际拼出的 user prompt 与前置闸门捕获并冻结的 on_prompt 逐字节相等；
off 格在断言之后才把这段 prompt 换成冻结的 off_prompt（= on 精确删除三段投影）。
不相等即整批停：不派发、不消耗、不改判据。

用法：
  先零成本自检：  D:/anaconda/python.exe -X utf8 tools/run_knowledge_probe_batch.py --mode mock --limit 4
  正式派发：      ... --mode live
"""
import argparse
import datetime as dt
import hashlib
import io
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import api_candidate                                  # noqa: E402
import knowledge_probe_precheck as kpp                # noqa: E402
from build_f3_blind_materials import reply_payload    # noqa: E402
from lb_gateway import ADAPTERS, ModelGateway         # noqa: E402
from isolation.authorization_budget import (          # noqa: E402
    AuthorizationBudget, validate_authorization_doc, BudgetRefused)

OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "final_mainline_review_20261029")
FREEZE = os.path.join(OUT, "F5_CANDIDATE_FREEZE_20261029.json")
PROBES_BY_ID = {p["probe_id"]: p for p in kpp.PROBES}


def sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def sha_file(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


class DriftError(Exception):
    pass


class ArmGateway(object):
    """机制→网关的唯一插入口：先硬断言实际出站 prompt，再按臂选择 on/off。"""

    def __init__(self, inner, arm, on_prompt, off_prompt, model, temperature, sink):
        self.inner = inner
        self.arm = arm
        self.on_prompt = on_prompt
        self.off_prompt = off_prompt
        self.model = model
        self.temperature = temperature
        self.sink = sink

    def generate(self, system, user, images=None, tag=None, attempt_id=None, **kw):
        if user != self.on_prompt:
            raise DriftError("prompt_mechanism_vs_frozen len=%d/%d sha=%s/%s" % (
                kpp_len(user), kpp_len(self.on_prompt),
                sha(user)[:16], sha(self.on_prompt)[:16]))
        sent = self.on_prompt if self.arm == "on" else self.off_prompt
        self.sink["sent_prompt_sha256"] = sha(sent)
        self.sink["sent_prompt_cu"] = kpp_len(sent)
        self.sink["replaced_for_off"] = (self.arm == "off")
        return self.inner.generate(system, sent, images=images, model=self.model,
                                   temperature=self.temperature,
                                   tag="kb_probe", attempt_id=attempt_id)


def kpp_len(text):
    return len((text or "").encode("utf-16-le")) // 2


class LedgerGateway(object):
    """把 ModelGateway 包进授权账本：成功/失败各记一次，失败不退款。"""

    def __init__(self, gw, budget, attempt_id):
        self.gw = gw
        self.budget = budget
        self.attempt_id = attempt_id
        self.usage = None
        self.raw_sha = None

    def generate(self, system, user, images=None, model=None, temperature=None,
                 tag=None, attempt_id=None):
        try:
            res = self.gw.generate(system, user, images=images, model=model,
                                   temperature=temperature, tag=tag,
                                   attempt_id=self.attempt_id)
        except Exception as e:
            self.budget.record("attempt_failed", self.attempt_id,
                               error=("%s: %s" % (type(e).__name__, e))[:200])
            raise
        usage = res.get("usage")
        self.usage = usage
        self.raw_sha = sha(res.get("text"))
        self.budget.record("attempt_completed", self.attempt_id, usage=usage,
                           raw_sha256=self.raw_sha)
        return res


def identity_check(candidate):
    p = subprocess.run(
        [sys.executable, "-X", "utf8", "-B",
         os.path.join(ROOT, "tools", "check_identity_drift.py"),
         "--candidate", candidate, "--freeze", FREEZE, "--flat"],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    try:
        doc = json.loads(p.stdout)
    except Exception:
        raise SystemExit("identity check unreadable: %s %s" %
                         (p.stdout[:200], p.stderr[:200]))
    if not doc.get("identical"):
        raise SystemExit("IDENTITY DRIFT: %s" % json.dumps(
            {k: doc.get(k) for k in
             ("files_added", "files_removed", "files_changed")},
            ensure_ascii=False)[:400])
    return doc


def build_plan(auth, precheck):
    sel = auth["scope"]["selected_probes"]
    order = auth["scope"]["frozen_dispatch_order"]
    expect = []
    for rep in (1, 2):
        for pid in sel:
            expect += ["%s:on:r%d" % (pid, rep), "%s:off:r%d" % (pid, rep)]
    if order != expect:
        raise SystemExit("dispatch order in auth does not match frozen rule")
    by_id = {r["probe_id"]: r for r in precheck["probes"]}
    return [{"cell": c, "probe_id": c.split(":")[0], "arm": c.split(":")[1],
             "rep": int(c.split(":")[2][1:]), "precheck": by_id[c.split(":")[0]]}
            for c in order]


def score(probe, payload):
    """确定性标记：只做字面核验；语义判定（是否真的据记忆行动/是否把旧值当现行）
    由人工逐份读 24 段正文完成，这里不代替人做结论。"""
    texts = []
    if payload.get("primary"):
        texts.append(payload["primary"])
    for b in payload.get("backups") or []:
        texts.append(b.get("text") or "")
    blob = " ".join(texts)
    return {
        "delivered_kind": payload.get("kind"),
        "decline_reason": payload.get("decline_reason") or "",
        "delivered_chars": len(blob),
        "value_forms_present": [v for v in probe["value_forms"] if v in blob],
        "stale_forms_present": [s for s in probe["stale_forms"] if s in blob],
        "topic_forms_present": [t for t in probe["leak_forms"] if t in blob],
        "attribution_target_present": [
            t for t in probe.get("attribution_targets") or [] if t in blob],
        "wrong_subject_present": [
            w for w in probe.get("wrong_subjects") or [] if w in blob],
        "blob": blob,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", default="mock", choices=["mock", "live"])
    ap.add_argument("--authorization",
                    default=os.path.join(OUT, "KB_PROBE_AUTHORIZATION_20261029.json"))
    ap.add_argument("--precheck", default=os.path.join(OUT, "F_B_PRECHECK.json"))
    ap.add_argument("--prompt-dir",
                    default=os.path.join(ROOT, "logs", "kp_precheck_20261029",
                                         "probe_prompts"))
    ap.add_argument("--outdir", default=None)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--close", action="store_true",
                    help="跑完（或明确终止）后关闭本授权额度；关闭后不可复活")
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    auth = json.load(open(a.authorization, encoding="utf-8"))
    aid = auth["authorization_id"]
    cand = auth["scope"]["candidate"]
    iddoc = identity_check(cand)
    if iddoc["current_identity_sha256"] != auth["scope"]["identity_sha256"]:
        raise SystemExit("authorization identity != current identity")

    precheck = json.load(open(a.precheck, encoding="utf-8"))
    if sha_file(a.precheck) != auth["scope"]["precheck_sha256"]:
        raise SystemExit("precheck file changed after authorization (sha mismatch)")

    frozen = {}
    for r in precheck["probes"]:
        if not r["passed"]:
            continue
        tag = "%s_%s" % (r["probe_id"], r["story"])
        onp = open(os.path.join(a.prompt_dir, tag + "_on.txt"), encoding="utf-8").read()
        offp = open(os.path.join(a.prompt_dir, tag + "_off.txt"), encoding="utf-8").read()
        if sha(onp) != r["on_prompt_sha256"] or sha(offp) != r["off_prompt_sha256"]:
            raise SystemExit("frozen prompt bytes differ from precheck record: %s" % tag)
        frozen[r["probe_id"]] = {"on": onp, "off": offp, "rec": r}

    plan = build_plan(auth, precheck)
    if a.limit:
        plan = plan[:a.limit]

    ts = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    outdir = a.outdir or os.path.join(ROOT, "logs", "kb_probe_%s_%s" % (a.mode, ts))
    os.makedirs(outdir, exist_ok=True)

    ep = auth["real_endpoint"]["api_base"].rstrip("/")
    md = auth["real_endpoint"]["model"]
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
        os.environ["LB_API_BASE"] = ep
        os.environ["LB_MODEL"] = md
        os.environ.setdefault("LB_API_KEY", "local")
        os.environ["LB_STREAM_TIMING"] = "0"
        budget = AuthorizationBudget(os.path.join(ROOT, auth["budget_ledger"]), aid,
                                    auth["real_endpoint"]["max_attempts"],
                                    scope=auth["scope"])
        try:
            budget.ensure_batch(len(plan), purpose="kb_probe_generation")
        except BudgetRefused as e:
            raise SystemExit("BUDGET REFUSED: %s" % e)
        inner_gw = ModelGateway(ADAPTERS["openai_compat"](),
                                event_log_path=os.path.join(outdir, "GATEWAY_EVENTS.jsonl"))
    else:
        inner_gw = ModelGateway(ADAPTERS["mock"](),
                                event_log_path=os.path.join(outdir, "GATEWAY_EVENTS.jsonl"))

    mech = kpp.load_mod(cand)
    rows, stop = [], None
    for cell in plan:
        pid, arm, rep = cell["probe_id"], cell["arm"], cell["rep"]
        pr = cell["precheck"]
        probe = PROBES_BY_ID[pid]
        cdir = os.path.join(outdir, "cell_%s" % cell["cell"].replace(":", "_"))
        os.makedirs(cdir, exist_ok=True)
        rec = {"cell": cell["cell"], "probe_id": pid, "arm": arm, "rep": rep,
               "class": probe["class"], "story": probe["story"],
               "fact_id": probe["fact_id"], "domain": probe["domain"],
               "question": probe["question"], "mode": a.mode,
               "fillers_replayed": len(pr["filler_log"])}
        sink = {}
        attempt_id = "kbprobe:%s:%s:r%d" % (pid, arm, rep)
        rec["attempt_id"] = attempt_id
        ledger_gw = None
        try:
            sandbox = os.path.join(cdir, "_sandbox")
            os.makedirs(sandbox, exist_ok=True)
            h = mech.restore(sandbox, open(kpp.PAID_STATES[probe["story"]], "rb").read())
            for fl in pr["filler_log"]:
                mech.run_turn(h, kpp.ctx_for(fl["seq"], fl["clock"], fl["text"]),
                              kpp.FixtureGW())
            want = [fl["text"] for fl in pr["filler_log"]]
            got = [e["text"] for e in h["events"][-len(want):]] if want else []
            if want and got != want:
                raise DriftError("filler_replay_mismatch got=%d want=%d" % (len(got), len(want)))
            if budget is not None:
                budget.reserve(attempt_id, purpose="kb_probe_generation")
                ledger_gw = LedgerGateway(inner_gw, budget, attempt_id)
            step = ledger_gw if ledger_gw is not None else inner_gw
            wrapped = ArmGateway(step, arm, frozen[pid]["on"], frozen[pid]["off"],
                                 md, tp, sink)
            seq = h["turn_count"] + len(pr["filler_log"]) + 1
            out = mech.run_turn(h, kpp.ctx_for(seq, pr["probe_clock"], probe["question"]),
                                wrapped)
            raw = out.get("reply") or ""
            ingest = api_candidate.store_reply(cdir, raw)
            payload = reply_payload(cdir)
            rec.update({"transport_ok": True,
                        "ingest_kind": ingest.get("kind"),
                        "top_level_keys": ingest.get("top_level_keys"),
                        "raw_sha256": sha(raw),
                        "usage": getattr(ledger_gw, "usage", None),
                        "sent_prompt_sha256": sink.get("sent_prompt_sha256"),
                        "sent_prompt_cu": sink.get("sent_prompt_cu"),
                        "off_substituted": sink.get("replaced_for_off"),
                        "harness_notes": (out.get("notes") or "")[:700]})
            rec["score"] = score(probe, payload)
            with open(os.path.join(cdir, "delivered.json"), "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False, indent=1)
        except DriftError as e:
            rec.update({"transport_ok": False, "error": "DRIFT: %s" % e,
                        "consumed_by_this_cell": False})
            stop = "prompt_drift"
        except Exception as e:  # noqa: BLE001
            rec.update({"transport_ok": False,
                        "error": "%s: %s" % (type(e).__name__, e),
                        "consumed_by_this_cell": True})
            stop = stop or "transport_or_budget_fail"
        with open(os.path.join(cdir, "cell.json"), "w", encoding="utf-8") as f:
            json.dump(rec, f, ensure_ascii=False, indent=1)
        rows.append(rec)
        with open(os.path.join(outdir, "KB_PROBE_ROWS.jsonl"), "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
        s = rec.get("score") or {}
        sys.stdout.write("%-20s arm=%-3s ok=%-5s kind=%-11s value=%-10s stale=%-8s err=%s\n" % (
            rec["cell"], rec["arm"], rec.get("transport_ok"),
            s.get("delivered_kind") or rec.get("error", "-")[:11],
            ",".join(s.get("value_forms_present") or []) or "-",
            ",".join(s.get("stale_forms_present") or []) or "-",
            rec.get("error", "-")))
        if stop:
            break

    closed = None
    if a.close and budget is not None:
        budget.close("kb probe batch finished (stopped=%s)" % (stop or "none"))
        tail = []
        lp = os.path.join(ROOT, auth["budget_ledger"])
        if os.path.isfile(lp):
            tail = [json.loads(l) for l in io.open(lp, encoding="utf-8") if l.strip()][-1:]
        closed = {"closed_flag": bool(getattr(budget, "closed", False)),
                  "reason": getattr(budget, "close_reason", None),
                  "ledger_last_event": tail[0].get("kind") if tail else None}

    summary = {
        "kind": "kb_probe_batch_result", "mode": a.mode, "authorization_id": aid,
        "candidate": cand, "identity_sha256": iddoc["current_identity_sha256"],
        "precheck_sha256": sha_file(a.precheck),
        "outdir": os.path.relpath(outdir, ROOT).replace("\\", "/"),
        "planned": len(plan), "done": len(rows),
        "transport_ok": sum(1 for r in rows if r.get("transport_ok")),
        "prompt_drift_guard": "每格派发前逐字节断言机制实拼 prompt==冻结 on_prompt",
        "stopped": stop, "authorization_closed": closed,
        "fixture_turns_not_billed": sum(len(frozen[r["probe_id"]]["rec"]["filler_log"])
                                        for r in rows if r["probe_id"] in frozen),
        "rows": rows,
    }
    with open(os.path.join(outdir, "KB_PROBE_RESULT.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
    sys.stdout.write("SUMMARY mode=%s planned=%d done=%d ok=%d stopped=%s closed=%s\n" % (
        a.mode, summary["planned"], summary["done"], summary["transport_ok"],
        stop, json.dumps(closed, ensure_ascii=False)))
    sys.stdout.write("WROTE %s\n" % os.path.join(outdir, "KB_PROBE_RESULT.json")
                     .replace("\\", "/"))
    return 0 if (not stop and summary["transport_ok"] == len(plan)) else 2


if __name__ == "__main__":
    sys.exit(main())
