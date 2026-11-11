#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""F0: evidence-level correction of the three over-silence judgments.

Reads only frozen artifacts. Writes JUDGMENT_ERRATA_20261029.json and
STATE_AND_ERRATA.json. Old scorecards are never rewritten.
"""
from __future__ import annotations

import hashlib
import json
import os
import sys

ROOT = r"D:/lovebrain-arena"
OUT = os.path.join(ROOT, "refs/experiment_protocol_20261023/final_mainline_review_20261029")
LEDGER = os.path.join(OUT, "ACTION_OUTCOMES_130.jsonl")
CARD_PATH = os.path.join(ROOT, "benchmark/stories_v2/stageD/GRADING_CARDS_R3_DECLINE.json")
D1_SCORECARD = os.path.join(ROOT, "refs/experiment_protocol_20261023/s1_action_live_20261028/D1_SCORECARD.json")
R3A_PRED = os.path.join(ROOT, "refs/experiment_protocol_20261023/s1_action_live_20261028/R3A_PREDICTIONS_PREREGISTERED.json")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def episode_path(case_id):
    ep = case_id.split("_T")[0]
    return os.path.join(ROOT, "benchmark/stories_v2/stageD", ep, "episode.json")


def main():
    ledger = {}
    with open(LEDGER, encoding="utf-8") as f:
        for line in f:
            rec = json.loads(line)
            ledger.setdefault(rec["case_id"], []).append(rec)
    cards = {c["case_id"]: c for c in load(CARD_PATH)["cards"]}
    old_errors = {e["case_id"]: e for e in load(D1_SCORECARD)["severe_errors"]}
    preds = load(R3A_PRED)

    entries = []
    for cid in ("D01_T08", "D02_T06", "D02_T08"):
        ep = load(episode_path(cid))
        turn = next(t for t in ep["turns"] if t["case_id"] == cid)
        msgs = turn["input"]["messages"]
        last = msgs[-1]
        card = cards[cid]
        pair_recs = ledger.get(cid, [])
        # Use D1 and R3A as the two directly relevant batch records.
        by_batch = {r["batch"]: r for r in pair_recs}
        d1 = by_batch.get("D1")
        r3a = by_batch.get("R3A")
        entry = {
            "case_id": cid,
            "old_judgment": old_errors.get(cid),
            "old_source": os.path.relpath(D1_SCORECARD, ROOT).replace("\\", "/"),
            "old_source_sha256": sha(D1_SCORECARD),
            "frozen_window": {
                "episode": ep.get("episode_id"),
                "clock": turn["input"].get("clock"),
                "may_decline": turn["input"].get("may_decline"),
                "messages": msgs,
                "last_role": last.get("role"),
                "last_text": last.get("text"),
            },
            "card_evidence": {
                "current_action": card.get("current_action"),
                "private_guidance": card.get("private_guidance"),
                "acceptable_actions": card.get("acceptable_actions"),
                "may_decline": card.get("may_decline"),
                "required_this_turn": [
                    a.get("atom_id") for a in card.get("required_memory_atoms", []) if a.get("required_this_turn")
                ],
                "intent": card.get("intent"),
            },
            "paired_actions": {
                "D1": None if d1 is None else {
                    "parent_arm": d1["parent_arm"],
                    "candidate_arm": d1["candidate_arm"],
                    "parent": {
                        "action": d1["sides"]["parent"]["action"],
                        "text": d1["sides"]["parent"]["text"],
                        "decline_reason": d1["sides"]["parent"]["decline_reason"],
                    },
                    "candidate": {
                        "action": d1["sides"]["candidate"]["action"],
                        "text": d1["sides"]["candidate"]["text"],
                        "decline_reason": d1["sides"]["candidate"]["decline_reason"],
                    },
                },
                "R3A": None if r3a is None else {
                    "parent_arm": r3a["parent_arm"],
                    "candidate_arm": r3a["candidate_arm"],
                    "parent": {
                        "action": r3a["sides"]["parent"]["action"],
                        "text": r3a["sides"]["parent"]["text"],
                        "decline_reason": r3a["sides"]["parent"]["decline_reason"],
                    },
                    "candidate": {
                        "action": r3a["sides"]["candidate"]["action"],
                        "text": r3a["sides"]["candidate"]["text"],
                        "decline_reason": r3a["sides"]["candidate"]["decline_reason"],
                    },
                },
            },
            "finding": "",
            "disposition": "",
            "confidence": "high",
        }
        if cid == "D01_T08":
            entry["finding"] = (
                "Frozen window ends with HER '知道啦谢了不聊了我躺了' after ME already acknowledged the outcome "
                "('成了就好回头那机器别自己垫钱'). The card says COMPLETED, may_decline=true, both required atoms false, "
                "private_guidance says hard-finding a reply would overstep, and current_action explicitly says to accept "
                "that she need not reply. r2's no_reply is supported by the frozen input and the card."
            )
            entry["disposition"] = (
                "RETRACT the 'severe error' label to PENDING/INSUFFICIENT; do not use this cell as evidence that r2 "
                "was over-silent, and do not count r3's reply as a verified fix. Both silence and a short acknowledgement "
                "are within the card; the old inference cherry-picked acceptable_actions while ignoring may_decline, "
                "current_action and the already-sent ME acknowledgement."
            )
        elif cid == "D02_T06":
            entry["finding"] = (
                "The frozen window does NOT end with HER. HER said '先不聊了困了', then ME already replied "
                "'行你先歇着'; the final message is ME. There is no new HER message and no new request. The card itself "
                "is internally inconsistent: current_action says '（可不回）' and may_decline=true, while private_guidance "
                "says '不能玩消失' and acceptable_actions only lists replies."
            )
            entry["disposition"] = (
                "ISOLATE this cell as CARD CONFLICT / NO NEW HER; the old 'severe error' is not established at the action "
                "layer. Keep the old text as historical, but it cannot support FIX-9 or a candidate loss. r3's added reply "
                "is also allowed by the reply examples, but on a ME-final window it is a follow-up, not a correction of a "
                "missed HER turn."
            )
            entry["confidence"] = "high"
        else:  # D02_T08
            entry["finding"] = (
                "Frozen window ends with ME '辛苦晚上早点睡' after HER said '不聊了我给他做饭去'. There is no new HER "
                "message, no new goal and no pending question. Card says COMPLETED, may_decline=true, required atoms false, "
                "and current_action says to accept that she need not reply."
            )
            entry["disposition"] = (
                "RETRACT the 'severe error' label; r2's no_reply is consistent with the window and card. r3's extra "
                "'熄一次火也照样补上了...' is not automatically better and is an extra message on a ME-final window. "
                "Do not use this cell to claim FIX-9 improved anything."
            )
        entries.append(entry)

    errata = {
        "schema": "LOVEBRAIN_JUDGMENT_ERRATA_20261029_V1",
        "created_at": "2026-10-29",
        "scope": "three over-silence cells used by FIX-9 (action_policy_r3) and the D1 scorecard",
        "authority": "frozen episode/card/reply artifacts; old scorecards kept unchanged",
        "old_judgments_retracted": ["D01_T08", "D02_T08"],
        "old_judgments_isolated_as_card_conflict": ["D02_T06"],
        "fix9_claim": {
            "claim_text": "FIX-9 reduced over-silence from 2 to 0 by making r3 reply on D01_T08/D02_T08 (and D02_T06).",
            "status": "NOT_SUPPORTED",
            "reason": (
                "D01_T08/D02_T08 were not over-silence errors; D02_T06 has no new HER message in the frozen window and "
                "the card says 可不回. The premise that acceptable_actions without a no-reply example forbids silence "
                "contradicts may_decline, current_action, private_guidance and the actual last speaker. Therefore the "
                "2->0 count is a relabeling artifact, not a measured improvement."
            ),
            "r3a_preregistered_predictions": preds,
        },
        "entries": entries,
        "ledger": {
            "path": os.path.relpath(LEDGER, ROOT).replace("\\", "/"),
            "sha256": sha(LEDGER),
            "pairs": sum(len(v) for v in ledger.values()),
        },
        "unchanged_old_files": [
            "refs/experiment_protocol_20261023/s1_action_live_20261028/D1_SCORECARD.json",
            "refs/experiment_protocol_20261023/s1_action_live_20261028/D1_SCORECARD.md",
            "logs/p08_d1_live_20261028/",
            "logs/p08_r3a_live_20261028/",
        ],
    }
    with open(os.path.join(OUT, "JUDGMENT_ERRATA_20261029.json"), "w", encoding="utf-8") as f:
        json.dump(errata, f, ensure_ascii=False, indent=1)
        f.write("\n")

    # Candidate selection at F0: r3 is not selected on the strength of FIX-9.
    state = {
        "schema": "LOVEBRAIN_STATE_AND_ERRATA_20261029_V1",
        "date": "2026-10-29",
        "inherited_and_verified": {
            "live_answers": 260,
            "started": 260,
            "completed": 260,
            "paired_observations": 130,
            "unique_case_ids": 82,
            "previously_scored_pairs": 118,
            "previously_excluded_pairs": 12,
            "batches": ["S1", "S2", "S2R", "S3", "D1", "R3A", "R3B"],
            "binding": "seven batches VERIFIED_ALL per INDEPENDENT_RESULTS.json",
            "output_repair_parity": "old_sendable=246 new_sendable=247 regressions=0 invariant_failures=0",
        },
        "new_f0_evidence": {
            "action_ledger": "ACTION_OUTCOMES_130.jsonl",
            "action_ledger_sha256": sha(LEDGER),
            "action_classes_both_arms": {},
            "card_conflict_pairs": 4,
            "no_new_her_pairs": 22,
            "format_undeliverable_cells": 5,
            "format_undeliverable_pairs": ["S2:AK02_T01", "S2:AK09_T01", "S2:AK12_T01", "S3:AK09_T01", "S3:AK12_T01"],
        },
        "errata": {
            "file": "JUDGMENT_ERRATA_20261029.json",
            "retracted": ["D01_T08 severe error", "D02_T08 severe error", "FIX-9 2->0 claim"],
            "isolated": ["D02_T06 card conflict / no new HER"],
        },
        "candidate_selection": {
            "selected_base": "action_policy_r2",
            "not_selected": "action_policy_r3",
            "decision": "r2 remains the evidence-backed base for the final candidate; r3 is retired as a failed side branch unless F1/F3 later produces independent evidence of a net gain.",
            "reasons": [
                "r3's only pre-registered target changes were the three over-silence cells; all three premises fail on frozen input/card evidence.",
                "R3A worker aggregate was 0 candidate wins / 1 parent win / 20 ties (n=21), so r3 has no demonstrated net action advantage.",
                "r3 adds replies on two ME-final windows (D02_T06/D02_T08), which is a follow-up rather than a response to a new HER message.",
                "Selecting by version number or by 'latest' is explicitly forbidden; no new quality evidence supports replacing r2.",
            ],
            "caveat": "F1 blinded review and F3 new materials can still overturn this selection; this is a pre-registered F0 choice, not a final verdict.",
            "final_candidate_identity_rule": "F2 will create a new identity from the selected base; old r2/r3 identities are never renamed or overwritten.",
        },
        "next": ["F1 blinded historical review", "F2 actual harness fixes and freeze", "F3 new materials and long-term knowledge"],
    }
    # Fill action class counts from the ledger itself.
    from collections import Counter
    cc = Counter()
    for recs in ledger.values():
        for rec in recs:
            cc[rec["action_class"]["parent"]] += 1
            cc[rec["action_class"]["candidate"]] += 1
    state["new_f0_evidence"]["action_classes_both_arms"] = dict(sorted(cc.items()))
    with open(os.path.join(OUT, "STATE_AND_ERRATA.json"), "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=1)
        f.write("\n")

    print("wrote JUDGMENT_ERRATA_20261029.json and STATE_AND_ERRATA.json")
    print("entries:", [e["case_id"] for e in entries])
    print("action classes:", dict(sorted(cc.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
