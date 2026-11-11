# -*- coding: utf-8 -*-
"""Deterministic output guard for action_policy_r4_guard_final.

This is a harness-side adapter, not a new score parser. It reuses
tools/output_repair.py and tools/normalize_reply_contract.py (vendored verbatim
under mechanism/_vendor/) to:
  1. repair known mechanical JSON families (fence/curly quotes/premature top-level
     brace/style-key aliases/directions overflow) before the parent mechanism
     parses analysis for memory write-back;
  2. apply one conservative evidence-subject check to analysis.facts/updates:
     a relationship claim sourced from a single message must have both-party
     support in its quote; otherwise the stored text is replaced by a role-tagged
     verbatim quote (or the update is dropped). This prevents a model paraphrase
     from silently changing who the fact is about.

No extra model calls. Raw model text remains in calls.json (ModelGateway logs it
before this adapter changes the returned text); every repair/subject change is
appended to mechanism_notes.txt by the candidate wrapper.
"""
from __future__ import annotations

import json
import os
import re
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(_HERE, "_vendor"))
import output_repair  # noqa: E402

RELATION_WORDS = (
    "认识", "介绍", "交往", "在一起", "结婚", "恋爱", "对象", "同事",
    "朋友", "关系", "兄妹", "姐妹", "兄弟", "相亲", "前任", " partner ",
)
BOTH_PARTY_MARKERS = ("我们", "咱们", "咱俩", "一起", "双方", "互相", "俩")
ROLE_LABEL = {"ME": "我说", "HER": "她说"}


def _parse_chat_roles(user_prompt: str) -> dict:
    """Map m0/m1/... aliases to ME/HER from the frozen <chat> block."""
    roles = {}
    if not isinstance(user_prompt, str):
        return roles
    m = re.search(r"<chat>\s*(.*?)\s*</chat>", user_prompt, re.S)
    if not m:
        return roles
    for line in m.group(1).splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            rec = json.loads(line)
        except Exception:  # noqa: BLE001
            continue
        alias = str(rec.get("id") or "")
        speaker = str(rec.get("speaker") or "")
        if alias:
            roles[alias] = "HER" if speaker.upper() == "PARTNER" else "ME"
    return roles


def _single_source_role(src_ids, roles):
    rs = {roles.get(str(s)) for s in (src_ids or []) if roles.get(str(s))}
    return next(iter(rs)) if len(rs) == 1 else None


def _has_both_party_support(quote: str) -> bool:
    q = quote or ""
    if any(w in q for w in BOTH_PARTY_MARKERS):
        return True
    # "我…你…" in one message is direct both-party evidence.
    if "我" in q and "你" in q:
        return True
    return False


def _guard_fact(fact, roles, loss):
    if not isinstance(fact, dict):
        return
    text = (fact.get("text") or "").strip()
    quote = (fact.get("quote") or "").strip()
    subject = (fact.get("subject") or "").strip().upper()
    if not text or subject not in ("ME", "HER", "SHARED"):
        return
    if not any(w in text for w in RELATION_WORDS):
        return
    if _has_both_party_support(quote):
        return
    role = _single_source_role(fact.get("src_ids"), roles)
    original = {"text": text, "subject": subject, "quote": quote,
                "src_ids": list(fact.get("src_ids") or [])}
    if role in ("ME", "HER") and quote:
        fact["text"] = "%s：%s" % (ROLE_LABEL[role], quote)
        fact["subject"] = role
        fact["_subject_guard"] = "relation_claim_replaced_by_role_quote"
        loss.append({"rule": "relation_subject_replaced_by_role_quote",
                     "original": original,
                     "replacement": {"text": fact["text"], "subject": role}})
    else:
        fact["subject"] = "UNKNOWN"
        fact["_subject_guard"] = "relation_subject_unverified"
        loss.append({"rule": "relation_subject_unverified", "original": original})


def _guard_updates(analysis, roles, loss):
    kept = []
    for upd in (analysis.get("updates") or []):
        if not isinstance(upd, dict):
            continue
        if (upd.get("action") or "").strip() != "correct":
            kept.append(upd)
            continue
        text = (upd.get("new_text") or "").strip()
        quote = (upd.get("quote") or "").strip()
        if not any(w in text for w in RELATION_WORDS):
            kept.append(upd)
            continue
        if _has_both_party_support(quote):
            kept.append(upd)
            continue
        loss.append({"rule": "relation_update_dropped_without_both_party_quote",
                     "original": {"fact_id": upd.get("fact_id"), "new_text": text,
                                  "quote": quote, "src_ids": upd.get("src_ids")}})
    analysis["updates"] = kept


def repair_and_guard(raw_text, user_prompt, roles=None):
    """Return (harness_text, meta, obj_or_None)."""
    meta = {"raw_chars": len(raw_text or ""), "changed": False, "rules": []}
    obj, loss, fixed = output_repair.repair(raw_text)
    meta["raw_parse_ok"] = obj is not None
    meta["output_repair_loss"] = loss
    if obj is None:
        meta["rules"].append("unparseable_raw_left_untouched")
        return raw_text, meta, None

    roles = roles if roles is not None else _parse_chat_roles(user_prompt)
    analysis = obj.get("analysis")
    subject_loss = []
    if isinstance(analysis, dict):
        for fact in (analysis.get("facts") or []):
            _guard_fact(fact, roles, subject_loss)
        _guard_updates(analysis, roles, subject_loss)
    if subject_loss:
        meta["subject_guard"] = subject_loss
        meta["rules"].append("evidence_subject_guard")

    # Keep the audit payload out of the returned contract JSON; full text is in
    # meta and in the untouched raw_text logged by ModelGateway.
    extras = obj.pop("_audit_extra_directions", None)
    unmapped = obj.pop("_audit_unmapped", None)
    if extras:
        meta["extra_directions_preserved"] = extras
        meta["rules"].append("directions_overflow")
    if unmapped:
        meta["unmapped_style_values_preserved"] = unmapped
        meta["rules"].append("style_key_conflict")

    # Do not rewrite a clean raw reply just because json.dumps() formatting
    # differs. Only return the canonical text when the object actually changed.
    try:
        raw_obj = json.loads(raw_text)
    except Exception:  # noqa: BLE001
        raw_obj = None
    content_changed = raw_obj is None or raw_obj != obj
    if not content_changed:
        meta["changed"] = False
        return raw_text, meta, obj

    harness_text = json.dumps(obj, ensure_ascii=False)
    meta["harness_chars"] = len(harness_text)
    meta["changed"] = True
    meta["rules"].append("harness_text_rewritten")
    return harness_text, meta, obj


class GuardGateway:
    """Pass-through gateway that records every repair in `sink`."""

    def __init__(self, gateway, user_prompt, roles, sink):
        self._gw = gateway
        self._user_prompt = user_prompt
        self._roles = roles
        self._sink = sink

    def generate(self, *args, **kwargs):
        res = self._gw.generate(*args, **kwargs)
        if not isinstance(res, dict):
            return res
        raw = res.get("text") or ""
        fixed, meta, _obj = repair_and_guard(raw, self._user_prompt, self._roles)
        self._sink.append(meta)
        if fixed != raw:
            res = dict(res)
            res["text"] = fixed
            res["contract_guard"] = meta
        return res
