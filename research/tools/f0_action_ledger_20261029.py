#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""F0: build the 130-pair action-outcome ledger from frozen live artifacts.

This script only reads existing artifacts. It makes no model calls and writes
new evidence files under final_mainline_review_20261029/.

Key rule: a no_reply is not automatically a failure. Action existence,
card permission, last-speaker role, and deliverability are recorded separately
from semantic quality.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from collections import Counter, defaultdict

ROOT = r"D:/lovebrain-arena"
LIVE = os.path.join(ROOT, "refs/experiment_protocol_20261023/s1_action_live_20261028")
LOGS = os.path.join(ROOT, "logs")
OUT = os.path.join(ROOT, "refs/experiment_protocol_20261023/final_mainline_review_20261029")

BATCHES = [
    ("S1", "S1_ACTION"),
    ("S2", "S2_ANCHORS"),
    ("S2R", "S2R_RETEST"),
    ("S3", "S3_ATTRIB"),
    ("D1", "D1_GENERAL"),
    ("R3A", "R3A_RETEST"),
    ("R3B", "R3B_CONTROL"),
]

CLOSING_RE = re.compile(
    r"(不聊了|不聊|先睡|睡了|躺了|困了|晚安|做饭去|先歇|改天聊|回头聊|拜拜|挂了|去忙|先这样)"
)


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def resolve_episode(ep):
    hits = []
    base = os.path.join(ROOT, "benchmark/stories_v2")
    for fam in ("stageD", "stage2", "anchors_20261028"):
        p = os.path.join(base, fam, ep, "episode.json")
        if os.path.exists(p):
            hits.append(p)
    if len(hits) != 1:
        raise SystemExit("episode resolve failed for %s: %r" % (ep, hits))
    return hits[0]


def parse_reply(raw_text):
    """Return a conservative parse record for one arm/turn."""
    rec = {
        "parse_ok": False,
        "top_keys": [],
        "action": None,
        "decline_reason": None,
        "response_keys": [],
        "style_texts": [],
        "first_text": "",
        "format_note": "",
    }
    try:
        d = json.loads(raw_text)
    except Exception as e:  # noqa: BLE001
        rec["format_note"] = "json_parse_error: %s" % (e.__class__.__name__,)
        return rec
    if not isinstance(d, dict):
        rec["format_note"] = "top_level_not_object"
        return rec
    rec["parse_ok"] = True
    rec["top_keys"] = sorted(d.keys())
    rec["action"] = d.get("action")
    rec["decline_reason"] = d.get("decline_reason")
    resp = d.get("response")
    if isinstance(resp, dict):
        rec["response_keys"] = sorted(resp.keys())
        for k in ("recommended", "warm", "playful", "bad_boy"):
            v = resp.get(k)
            if isinstance(v, str) and v.strip():
                rec["style_texts"].append({"key": k, "text": v.strip()})
        if rec["style_texts"]:
            rec["first_text"] = rec["style_texts"][0]["text"]
    elif isinstance(resp, str):
        rec["response_keys"] = ["<string>"]
        if resp.strip():
            rec["style_texts"] = [{"key": "<string>", "text": resp.strip()}]
            rec["first_text"] = resp.strip()
    else:
        rec["format_note"] = "response_missing_or_not_object"
    return rec


def side_action_record(turn_dir):
    raw_path = os.path.join(turn_dir, "reply_raw.txt")
    manifest_path = os.path.join(turn_dir, "manifest.json")
    if not os.path.exists(raw_path):
        return {
            "present": False,
            "deliverable": False,
            "action": "missing_artifact",
            "text": "",
            "decline_reason": None,
            "parse_ok": False,
            "format_note": "reply_raw_missing",
        }
    raw = open(raw_path, encoding="utf-8").read()
    rec = parse_reply(raw)
    manifest = load_json(manifest_path) if os.path.exists(manifest_path) else {}
    text = rec["first_text"]
    action = rec["action"]
    if action == "no_reply" or (not text and str(action or "").lower() in ("no_reply", "silence")):
        action_class = "no_reply"
    elif text:
        action_class = "reply"
    else:
        action_class = "format_undeliverable"
    rec.update(
        {
            "present": True,
            "deliverable": bool(text),
            "action": action_class,
            "text": text,
            "raw_action_field": action,
            "reply_raw_sha256": hashlib.sha256(raw.encode("utf-8")).hexdigest(),
            "case_input_sha256": manifest.get("case_input_sha256"),
            "score10": manifest.get("score10"),
            "veto": manifest.get("veto"),
            "run_id": manifest.get("run_id"),
            "raw_chars": len(raw),
            "text_chars": len(text),
        }
    )
    return rec


def classify_side(side, last_role, may_decline, card):
    """Classify action appropriateness only at the action layer."""
    current = card.get("current_action") or ""
    guidance = card.get("private_guidance") or ""
    if side["action"] == "format_undeliverable":
        return "format_undeliverable", "raw JSON has neither usable text nor a documented no_reply"
    if side["action"] == "missing_artifact":
        return "format_undeliverable", "artifact missing"
    if side["action"] == "reply":
        if last_role == "ME":
            return "reply_without_new_her", "frozen window ends with ME; a reply is a follow-up, not a response to a new HER message"
        return "replied", "non-empty sendable text for a HER-final window"
    # no_reply
    if last_role == "ME":
        return "no_new_her_no_reply", "window ends with ME and the arm stayed silent"
    if "可不回" in current or "不必再回" in current:
        return "legit_no_reply", "card explicitly says a reply is not required"
    if may_decline and CLOSING_RE.search(last_her_text(card)):
        return "legit_no_reply", "may_decline + HER closing signal"
    if may_decline:
        return "optional_no_reply", "may_decline true but no explicit closing phrase in the card"
    return "required_reply_missing", "may_decline false and the window ends with HER"


# last_her_text is injected per case via closure below; kept explicit for clarity
_LAST_HER = {"text": ""}


def last_her_text(_card):
    return _LAST_HER["text"]


def verdict_from_classes(parent_cls, cand_cls):
    bad = {"format_undeliverable", "required_reply_missing"}
    good = {"replied", "legit_no_reply", "no_new_her_no_reply"}
    if parent_cls in bad and cand_cls not in bad:
        return "candidate_better"
    if cand_cls in bad and parent_cls not in bad:
        return "parent_better"
    if parent_cls in bad and cand_cls in bad:
        return "both_fail"
    if parent_cls in good and cand_cls in good:
        return "tie_action_layer"
    return "uncertain"


def detect_card_conflict(card, last_role, last_text):
    flags = []
    current = card.get("current_action") or ""
    guidance = card.get("private_guidance") or ""
    acceptable = card.get("acceptable_actions") or []
    may_decline = bool(card.get("may_decline"))
    if ("可不回" in current or "不必再回" in current) and "不能玩消失" in guidance:
        flags.append("card_says_optional_but_guidance_forbids_silence")
    if may_decline and acceptable and all(("回" in a or "答" in a) for a in acceptable) and ("可不回" not in current):
        flags.append("may_decline_true_but_acceptable_only_replies")
    if last_role == "ME" and ("接受她已明确不必再回" in current or "不必再回" in current):
        flags.append("window_ends_with_ME_but_card_frames_her_as_last_speaker")
    return flags


def main():
    all_pairs = []
    batch_stats = {}
    for batch, subdir in BATCHES:
        done_path = os.path.join(LOGS, "p08_%s_live_20261028.DONE.json" % batch.lower())
        done = load_json(done_path)
        cards_path = os.path.join(ROOT, done["cards_file"])
        cards = load_json(cards_path)["cards"]
        cards_by_id = {c["case_id"]: c for c in cards}
        arm_map = done["arm_mapping"]  # {'parent': name, 'candidate': name}
        # The refs/.../batch tree is a capture rehearsal with placeholder text.
        # Real live answers live in the out_dir recorded by the DONE manifest.
        batch_root = done["out_dir"]
        if not os.path.isdir(batch_root):
            raise SystemExit("live out_dir missing: %s" % batch_root)
        if "s1_action_live" in batch_root.replace("\\", "/") and "logs" not in batch_root.replace("\\", "/"):
            raise SystemExit("refusing capture tree as live source: %s" % batch_root)
        arms = sorted(d for d in os.listdir(batch_root) if os.path.isdir(os.path.join(batch_root, d)))
        parent_dirs = [d for d in arms if d.startswith("parent_")]
        candidate_dirs = [d for d in arms if d.startswith("candidate_")]
        if len(parent_dirs) != len(candidate_dirs):
            raise SystemExit("arm dir mismatch in %s" % batch)
        pair_count = 0
        for pdir, cdir in zip(sorted(parent_dirs), sorted(candidate_dirs)):
            ep = pdir.split("_", 1)[1]
            if cdir.split("_", 1)[1] != ep:
                raise SystemExit("episode arm mismatch %s %s %s" % (batch, pdir, cdir))
            episode = load_json(resolve_episode(ep))
            turns = {t["case_id"]: t for t in episode["turns"]}
            p_out = os.path.join(batch_root, pdir, "out")
            c_out = os.path.join(batch_root, cdir, "out")
            # turn dir names are turn_01_D01_T01; map by contained case_id from manifest
            def map_turns(outdir):
                m = {}
                for d in os.listdir(outdir):
                    if not d.startswith("turn_"):
                        continue
                    mp = os.path.join(outdir, d, "manifest.json")
                    if os.path.exists(mp):
                        cid = load_json(mp).get("case_id")
                        if cid:
                            m[cid] = os.path.join(outdir, d)
                return m

            p_turns = map_turns(p_out)
            c_turns = map_turns(c_out)
            common = sorted(set(p_turns) & set(c_turns), key=lambda x: (ep, int(x.rsplit("_T", 1)[-1])))
            if not common:
                raise SystemExit("no common turns %s %s" % (pdir, cdir))
            for cid in common:
                card = cards_by_id.get(cid)
                if card is None:
                    raise SystemExit("card missing %s %s" % (batch, cid))
                turn = turns.get(cid)
                if turn is None:
                    raise SystemExit("episode turn missing %s %s" % (batch, cid))
                inp = turn.get("input") or {}
                msgs = inp.get("messages") or []
                last = msgs[-1] if msgs else {"role": None, "text": ""}
                _LAST_HER["text"] = "\n".join(m.get("text", "") for m in msgs if m.get("role") == "HER")
                p_side = side_action_record(p_turns[cid])
                c_side = side_action_record(c_turns[cid])
                for _arm, _side in (("parent", p_side), ("candidate", c_side)):
                    if "离线替身" in (_side.get("text") or "") or "离线替身" in (_side.get("raw_action_field") or ""):
                        raise SystemExit("placeholder answer in live source: %s %s %s" % (batch, cid, _arm))
                last_role = last.get("role")
                p_cls, p_reason = classify_side(p_side, last_role, bool(inp.get("may_decline")), card)
                c_cls, c_reason = classify_side(c_side, last_role, bool(inp.get("may_decline")), card)
                flags = detect_card_conflict(card, last_role, last.get("text", ""))
                rec = {
                    "schema": "ACTION_OUTCOME_130_V1",
                    "batch": batch,
                    "pair_id": "%s:%s" % (batch, cid),
                    "case_id": cid,
                    "episode": ep,
                    "parent_arm": arm_map["parent"],
                    "candidate_arm": arm_map["candidate"],
                    "cards_file": done["cards_file"],
                    "frozen_input": {
                        "clock": inp.get("clock"),
                        "note": inp.get("note"),
                        "intent": inp.get("intent"),
                        "may_decline": bool(inp.get("may_decline")),
                        "last_role": last_role,
                        "last_text": last.get("text", ""),
                        "message_count": len(msgs),
                    },
                    "card": {
                        "current_action": card.get("current_action"),
                        "acceptable_actions": card.get("acceptable_actions"),
                        "private_guidance": card.get("private_guidance"),
                        "may_decline": card.get("may_decline"),
                        "intent": card.get("intent"),
                        "required_this_turn": [
                            a.get("atom_id") for a in card.get("required_memory_atoms", []) if a.get("required_this_turn")
                        ],
                    },
                    "sides": {
                        "parent": p_side,
                        "candidate": c_side,
                    },
                    "action_class": {
                        "parent": p_cls,
                        "candidate": c_cls,
                    },
                    "action_class_reason": {
                        "parent": p_reason,
                        "candidate": c_reason,
                    },
                    "action_layer_verdict": verdict_from_classes(p_cls, c_cls),
                    "card_conflict_flags": flags,
                    "new_her_message": last_role == "HER",
                    "has_note": bool(inp.get("note")),
                    "intent_status": (inp.get("intent") or {}).get("status"),
                }
                all_pairs.append(rec)
                pair_count += 1
        batch_stats[batch] = pair_count
        print("%-4s pairs=%d" % (batch, pair_count))

    if len(all_pairs) != 130:
        raise SystemExit("expected 130 pairs, got %d" % len(all_pairs))

    os.makedirs(OUT, exist_ok=True)
    jsonl_path = os.path.join(OUT, "ACTION_OUTCOMES_130.jsonl")
    with open(jsonl_path, "w", encoding="utf-8", newline="\n") as f:
        for rec in all_pairs:
            f.write(json.dumps(rec, ensure_ascii=False, sort_keys=True) + "\n")

    cls_counter = Counter()
    verdict_counter = Counter()
    for rec in all_pairs:
        cls_counter[rec["action_class"]["parent"]] += 1
        cls_counter[rec["action_class"]["candidate"]] += 1
        verdict_counter[rec["action_layer_verdict"]] += 1
    summary = {
        "schema": "ACTION_LEDGER_SUMMARY_V1",
        "pairs": len(all_pairs),
        "batch_pairs": batch_stats,
        "action_class_counts_both_arms": dict(sorted(cls_counter.items())),
        "action_layer_verdict_counts": dict(sorted(verdict_counter.items())),
        "card_conflict_pairs": [
            {"pair_id": r["pair_id"], "flags": r["card_conflict_flags"], "action": r["action_class"]}
            for r in all_pairs
            if r["card_conflict_flags"]
        ],
        "no_new_her_pairs": [
            {"pair_id": r["pair_id"], "parent": r["action_class"]["parent"], "candidate": r["action_class"]["candidate"]}
            for r in all_pairs
            if not r["new_her_message"]
        ],
        "required_reply_missing": [
            {"pair_id": r["pair_id"], "arm": arm, "side": r["sides"][arm]}
            for r in all_pairs
            for arm in ("parent", "candidate")
            if r["action_class"][arm] == "required_reply_missing"
        ],
        "format_undeliverable": [
            {"pair_id": r["pair_id"], "arm": arm, "side": r["sides"][arm]}
            for r in all_pairs
            for arm in ("parent", "candidate")
            if r["action_class"][arm] == "format_undeliverable"
        ],
        "ledger_sha256": sha256_file(jsonl_path),
        "source": "existing frozen live artifacts only; no model calls",
    }
    with open(os.path.join(OUT, "ACTION_OUTCOMES_130_SUMMARY.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(json.dumps({k: summary[k] for k in ("pairs", "batch_pairs", "action_class_counts_both_arms", "action_layer_verdict_counts")}, ensure_ascii=False, indent=1))
    print("card_conflict_pairs=%d no_new_her_pairs=%d" % (len(summary["card_conflict_pairs"]), len(summary["no_new_her_pairs"])))
    print("wrote", jsonl_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
