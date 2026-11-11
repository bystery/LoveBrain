#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Build F1 blinded materials: 8 controls (16 calls) + 130 history pairs + 12 swaps.

No model calls. Judge payloads never contain arm names, old scores, worker
verdicts, batch ids or candidate version. Private bindings are written separately.
"""
from __future__ import annotations

import hashlib
import json
import os
import random
import sys

ROOT = r"D:/lovebrain-arena"
OUT = os.path.join(ROOT, "refs/experiment_protocol_20261023/final_mainline_review_20261029")
LEDGER = os.path.join(OUT, "ACTION_OUTCOMES_130.jsonl")
CONTROLS = os.path.join(ROOT, "refs/experiment_protocol_20261023/knowledge_quality_independent_review_20261026/QUALITY_CONTROLS_DEV_V1.json")
SCORECARD_DIR = os.path.join(ROOT, "refs/experiment_protocol_20261023/s1_action_live_20261028")

BATCH_SCORECARDS = {
    "S1": "S1_SCORECARD.json",
    "S2": "S2_SCORECARD.json",
    "S2R": "S2R_SCORECARD.json",
    "S3": "S3_SCORECARD.json",
    "D1": "D1_SCORECARD.json",
    "R3A": "R3A_SCORECARD.json",
    "R3B": "R3B_SCORECARD.json",
}

SWAP_PAIRS = [
    "D1:D01_T01",   # worker parent win
    "D1:D01_T05",   # worker parent win
    "D1:D02_T01",   # worker candidate win
    "D1:D02_T04",   # worker candidate win
    "D1:D02_T07",   # worker tie
    "S1:E024_T01",  # worker candidate win
    "S2:AK01_T01",  # worker candidate win
    "S2R:AK09_T01", # no_reply anchor, candidate no_reply
    "R3A:D01_T08",  # no_reply, both allowed
    "D1:D02_T06",   # card conflict / no new HER
    "D1:D02_T08",   # card conflict / ME-final
    "S2:AK02_T01",  # major protocol failure (JSON)
]


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def episode_path(case_id):
    ep = case_id.split("_T")[0]
    for fam in ("stageD", "stage2", "anchors_20261028"):
        p = os.path.join(ROOT, "benchmark/stories_v2", fam, ep, "episode.json")
        if os.path.exists(p):
            return p
    raise SystemExit("episode not found: %s" % case_id)


def card_map_for(batch):
    # all seven batches use one of three frozen card files; resolve from DONE manifests
    done = load(os.path.join(ROOT, "logs", "p08_%s_live_20261028.DONE.json" % batch.lower()))
    return {c["case_id"]: c for c in load(os.path.join(ROOT, done["cards_file"]))["cards"]}


def worker_verdicts():
    out = {}
    for batch, fn in BATCH_SCORECARDS.items():
        p = os.path.join(SCORECARD_DIR, fn)
        d = load(p)
        for pos in d.get("positions") or []:
            cid = pos.get("case_id")
            if cid and pos.get("winner"):
                out["%s:%s" % (batch, cid)] = pos["winner"]
    return out


def reply_payload(turn_dir):
    raw_path = os.path.join(turn_dir, "reply_raw.txt")
    if not os.path.exists(raw_path):
        return {"kind": "format_fail", "error": "reply_raw_missing", "raw_excerpt": ""}
    raw = open(raw_path, encoding="utf-8").read()
    try:
        obj = json.loads(raw)
    except Exception:
        return {"kind": "format_fail", "error": "json_parse_error",
                "raw_excerpt": raw[:300]}
    if not isinstance(obj, dict):
        return {"kind": "format_fail", "error": "top_level_not_object",
                "raw_excerpt": raw[:300]}
    if str(obj.get("action") or "").lower() == "no_reply":
        return {"kind": "no_reply",
                "decline_reason": (obj.get("decline_reason") or "").strip()}
    resp = obj.get("response") if isinstance(obj.get("response"), dict) else {}
    primary = ""
    for k in ("recommended", "warm", "playful", "bad_boy"):
        v = resp.get(k)
        if isinstance(v, str) and v.strip():
            primary = v.strip()
            break
    backups = []
    for k in ("recommended", "bad_boy", "playful", "warm"):
        v = resp.get(k)
        if isinstance(v, str) and v.strip() and v.strip() != primary:
            backups.append({"slot": "response.%s" % k, "text": v.strip()})
    dirs = obj.get("directions")
    if isinstance(dirs, list):
        for i, d in enumerate(dirs):
            if isinstance(d, str) and d.strip():
                backups.append({"slot": "directions[%d]" % i, "text": d.strip()})
    if not primary and not backups:
        return {"kind": "format_fail", "error": "empty_response_no_action",
                "raw_excerpt": raw[:300]}
    if not primary:
        primary = backups.pop(0)["text"]
    return {"kind": "reply", "primary": primary, "backups": backups}


def context_for(case_id):
    ep = load(episode_path(case_id))
    turn = next(t for t in ep["turns"] if t["case_id"] == case_id)
    idx = ep["turns"].index(turn)
    prior = []
    for t in ep["turns"][:idx]:
        for m in (t.get("input") or {}).get("messages") or []:
            prior.append({"role": m.get("role"), "text": m.get("text"), "ts": m.get("ts")})
    prior = prior[-24:]
    inp = turn.get("input") or {}
    return {
        "persona": ep.get("persona") or {},
        "initial_kb": ep.get("initial_kb") or {},
        "prior_history": prior,
        "current_input": {
            "clock": inp.get("clock"),
            "may_decline": bool(inp.get("may_decline")),
            "note": inp.get("note"),
            "intent": inp.get("intent"),
            "messages": inp.get("messages") or [],
        },
    }


def card_for(case_id, cards):
    c = cards[case_id]
    return {
        "current_action": c.get("current_action"),
        "acceptable_actions": c.get("acceptable_actions"),
        "private_guidance": c.get("private_guidance"),
        "unknowns": c.get("unknowns"),
        "protected_expressions": c.get("protected_expressions"),
        "forbidden_factual_claims": c.get("forbidden_factual_claims"),
        "required_memory_atoms": c.get("required_memory_atoms"),
        "may_decline": c.get("may_decline"),
        "intent": c.get("intent"),
    }


def make_pair(unit_id, kind, pair_id, context, card, answer_a, answer_b,
              private, expected=None):
    doc = {
        "unit_id": unit_id,
        "kind": kind,
        "context": context,
        "card": card,
        "answers": {"A": answer_a, "B": answer_b},
        "task": "score both answers on nine dimensions, then choose verdict",
    }
    rec = {
        "unit_id": unit_id,
        "kind": kind,
        "pair_id": pair_id,
        "payload": doc,
        "private": private,
    }
    if expected is not None:
        rec["expected"] = expected
    return rec


def choose_order(unit_id):
    h = hashlib.sha256(("order:" + unit_id).encode("utf-8")).hexdigest()
    return (int(h[:8], 16) % 2) == 0  # True: first answer goes to A


def main():
    ledger = [json.loads(l) for l in open(LEDGER, encoding="utf-8") if l.strip()]
    assert len(ledger) == 130, len(ledger)
    worker = worker_verdicts()
    cards_by_batch = {b: card_map_for(b) for b in BATCH_SCORECARDS}
    items = []
    private = {}

    # ---- 8 controls ----
    controls = {c["control_id"]: c for c in load(CONTROLS)["controls"]}
    control_specs = [
        ("C01_fact_new_proposal", "CTL02", "CTL01"),
        ("C02_subject_attribution", "CTL09", "CTL10"),
        ("C03_time_cancel_state", "CTL16", "CTL15"),
        ("C04_protected_joke", "CTL05", "CTL06"),
        ("C05_should_answer_vs_wrong_focus", "CTL07", "CTL24"),
        ("C06_time_minor", "CTL13", "CTL14"),
        ("C07_positive_memory_hook", "CTL21", "__SYNTH_BAD_HOOK__"),
    ]
    for cid, good_id, bad_id in control_specs:
        good = controls[good_id]
        if bad_id == "__SYNTH_BAD_HOOK__":
            ctx = good["context"]
            bad_text = "行，我看看，最近有个挺闹的场子，周五去不去"
        else:
            bad = controls[bad_id]
            ctx = good["context"]
            bad_text = bad["answer"]
        # remove the "（本轮待评回复）" placeholder from current_input
        ctx = json.loads(json.dumps(ctx, ensure_ascii=False))
        msgs = (ctx.get("current_input") or {}).get("messages") or []
        ctx["current_input"]["messages"] = [
            m for m in msgs if "本轮待评回复" not in (m.get("text") or "")]
        good_text = good["answer"]
        order = choose_order(cid)
        a_text, b_text = (good_text, bad_text) if order else (bad_text, good_text)
        expected_arm = "A" if order else "B"
        rec = make_pair(
            "F1C:%s" % cid, "control", cid,
            {"persona": ctx.get("persona"), "initial_kb": ctx.get("initial_kb"),
             "prior_history": ctx.get("real_history") or [],
             "current_input": ctx.get("current_input")},
            None,
            {"kind": "reply", "primary": a_text, "backups": []},
            {"kind": "reply", "primary": b_text, "backups": []},
            {"good_control": good_id, "bad_control": bad_id,
             "constructed_negative": bad_id == "__SYNTH_BAD_HOOK__"},
            expected={"verdict": expected_arm, "strict": True,
                      "note": "control answer has clear evidence"},
        )
        items.append(rec)
        # positive/reverse order: same two answers, swapped A/B
        rev = make_pair(
            "F1C:%s:R" % cid, "control", cid,
            rec["payload"]["context"], None,
            rec["payload"]["answers"]["B"], rec["payload"]["answers"]["A"],
            {"good_control": good_id, "bad_control": bad_id,
             "constructed_negative": bad_id == "__SYNTH_BAD_HOOK__",
             "reverse_of": rec["unit_id"]},
            expected={"verdict": "B" if expected_arm == "A" else "A",
                      "strict": True, "note": "reverse order of same control"},
        )
        items.append(rev)

    # control 8: actual allowed-no-reply pair (D01_T08 r2 no_reply vs parent reply)
    target = next(r for r in ledger if r["pair_id"] == "D1:D01_T08")
    p_dir = target["sides"]["parent"]
    c_dir = target["sides"]["candidate"]
    # rebuild payloads from paths, because ledger stores text only
    p_turn = os.path.join(ROOT, "logs/p08_d1_live_20261028/parent_D01/out/turn_08_D01_T08")
    c_turn = os.path.join(ROOT, "logs/p08_d1_live_20261028/candidate_D01/out/turn_08_D01_T08")
    p_ans = reply_payload(p_turn)
    c_ans = reply_payload(c_turn)
    ctx = context_for("D01_T08")
    card = card_for("D01_T08", cards_by_batch["D1"])
    order = choose_order("C08_allowed_no_reply")
    a_ans, b_ans = (c_ans, p_ans) if order else (p_ans, c_ans)
    a_arm = "candidate" if order else "parent"
    items.append(make_pair(
        "F1C:C08_allowed_no_reply", "control", "C08_allowed_no_reply",
        ctx, card, a_ans, b_ans,
        {"pair_id": "D1:D01_T08", "a_arm": a_arm,
         "b_arm": "parent" if a_arm == "candidate" else "candidate"},
        expected={"verdict": "tie_or_uncertain", "strict": False,
                  "no_reply_arm": "A" if a_ans["kind"] == "no_reply" else (
                      "B" if b_ans["kind"] == "no_reply" else None),
                  "note": "D01_T08: HER explicitly closes; no_reply is allowed; do not call it a major error"},
    ))
    items.append(make_pair(
        "F1C:C08_allowed_no_reply:R", "control", "C08_allowed_no_reply",
        ctx, card, b_ans, a_ans,
        {"pair_id": "D1:D01_T08", "reverse_of": "F1C:C08_allowed_no_reply",
         "a_arm": "parent" if a_arm == "candidate" else "candidate", "b_arm": a_arm},
        expected={"verdict": "tie_or_uncertain", "strict": False,
                  "no_reply_arm": "B" if a_ans["kind"] == "no_reply" else (
                      "A" if b_ans["kind"] == "no_reply" else None),
                  "note": "reverse order of D01_T08 allowed-no-reply control"},
    ))

    # ---- 130 historical pairs ----
    for rec in ledger:
        pair_id = rec["pair_id"]
        batch = rec["batch"]
        cid = rec["case_id"]
        cards = cards_by_batch[batch]
        p_turn = os.path.join(
            ROOT, "logs/p08_%s_live_20261028" % batch.lower(),
            "parent_%s" % cid.split("_T")[0], "out",
            "turn_%02d_%s" % (int(cid.rsplit("_T", 1)[1]), cid))
        c_turn = os.path.join(
            ROOT, "logs/p08_%s_live_20261028" % batch.lower(),
            "candidate_%s" % cid.split("_T")[0], "out",
            "turn_%02d_%s" % (int(cid.rsplit("_T", 1)[1]), cid))
        p_ans = reply_payload(p_turn)
        c_ans = reply_payload(c_turn)
        order = choose_order(pair_id)
        a_ans, b_ans = (p_ans, c_ans) if order else (c_ans, p_ans)
        a_arm, b_arm = ("parent", "candidate") if order else ("candidate", "parent")
        items.append(make_pair(
            "F1H:%s" % pair_id, "historical", pair_id,
            context_for(cid), card_for(cid, cards), a_ans, b_ans,
            {"pair_id": pair_id, "a_arm": a_arm, "b_arm": b_arm,
             "batch": batch, "case_id": cid,
             "worker_winner": worker.get(pair_id),
             "card_conflict_flags": rec.get("card_conflict_flags") or []},
        ))

    # ---- 12 swaps ----
    by_pair = {r["pair_id"]: r for r in items if r["kind"] == "historical"}
    for pair_id in SWAP_PAIRS:
        base = by_pair.get(pair_id)
        if base is None:
            raise SystemExit("swap base missing: %s" % pair_id)
        p = base["payload"]
        priv = dict(base["private"])
        priv["swap_of"] = base["unit_id"]
        priv["a_arm"], priv["b_arm"] = priv["b_arm"], priv["a_arm"]
        swapped = make_pair(
            "F1S:%s" % pair_id, "swap", pair_id,
            p["context"], p["card"],
            p["answers"]["B"], p["answers"]["A"], priv,
        )
        items.append(swapped)

    # ---- write ----
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "F1_MATERIALS.jsonl"), "w", encoding="utf-8") as f:
        for rec in items:
            f.write(json.dumps(rec, ensure_ascii=False, sort_keys=True) + "\n")
    private = {r["unit_id"]: {"kind": r["kind"], "pair_id": r["pair_id"],
                              **r["private"],
                              "expected": r.get("expected")}
               for r in items}
    with open(os.path.join(OUT, "F1_PRIVATE_BINDINGS.json"), "w", encoding="utf-8") as f:
        json.dump(private, f, ensure_ascii=False, indent=1)
        f.write("\n")
    counts = {}
    for r in items:
        counts[r["kind"]] = counts.get(r["kind"], 0) + 1
    print("items=%d counts=%s" % (len(items), counts))
    print("controls:", [r["unit_id"] for r in items if r["kind"] == "control"])
    print("swaps:", [r["unit_id"] for r in items if r["kind"] == "swap"])
    print("materials_sha256:", sha(open(os.path.join(OUT, "F1_MATERIALS.jsonl"),
                                        encoding="utf-8").read()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
