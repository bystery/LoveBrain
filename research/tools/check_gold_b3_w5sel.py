# -*- coding: utf-8 -*-
"""annotator-B3 (W5-SEL phase3 batch2) delivery self-check.
Verifies E344/E345/E349/E353/E367 reference.json + audit.md against
GOLD_ANNOTATION_GUIDE_V1 / ATOMIC_ASSERTION_GUIDE_V1 / SELECTION_ANNOTATOR_BRIEF hard rules.
Run: python -I tools/check_gold_b3_w5sel.py
"""
import json
import os
import re
import sys

BASE = r"D:\lovebrain-arena\benchmark\stories_v2\wave5_selection"
EIDS = ["E344", "E345", "E349", "E353", "E367"]
T02_EIDS = {"E344", "E345"}
REQUIRED_FIELDS = ["case_id", "current_action", "acceptable_actions",
                   "applicable_dimensions", "required_memory_atoms",
                   "private_guidance", "unknowns", "protected_expressions",
                   "forbidden_factual_claims", "may_decline"]
DIMS = {"goal", "factuality", "attribution", "time_state", "boundary",
        "memory_use", "interaction", "initiative", "emotion_relation"}
CANCEL_FAMILY = {"cancelled", "canceled", "superseded", "retracted", "voided",
                 "revoked", "invalidated", "negated", "erroneous"}
STATUS_VOCAB = {"asserted", "confirmed", "tentative", "unknown", "cancelled",
                "canceled", "superseded", "conflict", "invalidated"}
GATE_KEYS = {"signal_rounds", "counter_signal_rounds", "initiative_probe_suitable",
             "retreat_room_required", "overstep_turn", "customer_service_examples",
             "safe_but_flat_examples", "recovery_check_turn"}
AUDIT_SECTIONS = ["## 0. 性质与暴露", "## 1. 生活动机", "## 2. 连续性与回扣",
                  "## 3. ME 历史与边界", "## 4. no_reply / 媒体局限",
                  "## 5. 参考卡质量", "## 6. 已知风险", "## 7. 出口检查"]
MIN_SINGLE_PHRASE_LEN = 4

stats = {}
grand = {"atoms": 0, "required": 0, "unknowns": 0, "forbidden": 0,
         "protected": 0, "quotes": 0, "strict": 0}
fail_count = 0


def flat_markers(groups):
    out = []
    for g in groups or []:
        if not isinstance(g, list) or not (1 <= len(g) <= 3):
            return None
        for p in g:
            if not isinstance(p, str):
                return None
            out.append(p)
    return out


for eid in EIDS:
    d = os.path.join(BASE, eid)
    fails = []
    with open(os.path.join(d, "episode.json"), encoding="utf-8") as f:
        ep = json.load(f)
    with open(os.path.join(d, "reference.json"), encoding="utf-8") as f:
        ref = json.load(f)
    audit_path = os.path.join(d, "audit.md")
    with open(audit_path, encoding="utf-8") as f:
        audit = f.read()

    # corpus = all messages + KB values (for marker verbatim check)
    corpus_parts = []
    msg_by_case = {}
    her_by_case = {}
    for t in ep["turns"]:
        texts = [m.get("text", "") for m in t["input"]["messages"]]
        msg_by_case[t["case_id"]] = texts
        corpus_parts.extend(texts)
        her_by_case[t["case_id"]] = "\n".join(
            m.get("text", "") for m in t["input"]["messages"] if m.get("role") == "HER")
    kb = ep.get("initial_kb") or {}
    corpus_parts.extend(str(v) for v in kb.values())
    corpus = "\n".join(corpus_parts)

    # 1. top-level shape
    if ref.get("story_id") != eid:
        fails.append("story_id mismatch")
    cases = [t["case_id"] for t in ep["turns"]]
    case_index = {c: i for i, c in enumerate(cases)}
    turns = ref.get("turns") or {}
    if sorted(turns.keys()) != sorted(cases):
        fails.append("turn keys != episode case set")

    # 2. T02 gate presence
    if eid in T02_EIDS:
        gate = ref.get("t02_signal_gate")
        if not isinstance(gate, dict):
            fails.append("missing t02_signal_gate")
        else:
            for k in GATE_KEYS:
                if k not in gate:
                    fails.append("gate missing key %s" % k)
            if not gate.get("signal_rounds"):
                fails.append("gate signal_rounds empty")
            if not gate.get("counter_signal_rounds"):
                fails.append("gate counter_signal_rounds empty")
    else:
        if "t02_signal_gate" in ref:
            fails.append("t02_signal_gate present on non-T02 story")

    n_atoms = n_req = n_unk = n_forb = n_prot = 0
    atom_ids = []
    for cid in cases:
        card = turns.get(cid) or {}
        tag = "%s/%s" % (eid, cid)
        for fld in REQUIRED_FIELDS:
            if fld not in card:
                fails.append("%s missing field %s" % (tag, fld))
        if card.get("case_id") != cid:
            fails.append("%s case_id mismatch" % tag)
        if not str(card.get("current_action") or "").strip():
            fails.append("%s empty current_action" % tag)
        aa = card.get("acceptable_actions") or []
        if len(aa) < 2:
            fails.append("%s acceptable_actions<2" % tag)
        if len(set(aa)) != len(aa):
            fails.append("%s duplicate acceptable_actions" % tag)
        dims = card.get("applicable_dimensions") or []
        if len(dims) < 3:
            fails.append("%s dims<3" % tag)
        for dd in dims:
            if dd not in DIMS:
                fails.append("%s bad dim %s" % (tag, dd))
        if not str(card.get("private_guidance") or "").strip():
            fails.append("%s empty private_guidance" % tag)
        ep_decl = (ep["turns"][case_index[cid]]["input"].get("may_decline") is True)
        if bool(card.get("may_decline")) != ep_decl:
            fails.append("%s may_decline != episode" % tag)

        # protected verbatim in current-turn HER message
        for p in card.get("protected_expressions") or []:
            n_prot += 1
            if p not in her_by_case.get(cid, ""):
                fails.append("%s protected not in HER: %r" % (tag, p))

        n_forb += len(card.get("forbidden_factual_claims") or [])

        # unknowns: object form + double key
        for u in card.get("unknowns") or []:
            n_unk += 1
            if not isinstance(u, dict) or "topic" not in u:
                fails.append("%s unknown not object with topic" % tag)
                continue
            mk, am = u.get("markers"), u.get("affirm_markers")
            if mk is None or am is None:
                fails.append("%s unknown missing double key" % tag)
            elif mk != am:
                fails.append("%s unknown double key mismatch" % tag)

        # atoms
        for a in card.get("required_memory_atoms") or []:
            n_atoms += 1
            aid = a.get("atom_id", "?")
            atom_ids.append(aid)
            for f2 in ("atom_id", "subject", "relation", "value", "valid_time",
                       "status", "negation", "source", "required_this_turn"):
                if f2 not in a:
                    fails.append("%s atom %s missing %s" % (tag, aid, f2))
            if a.get("subject") not in {"HER", "ME", "THIRD", "SHARED", "PUBLIC"}:
                fails.append("%s atom %s bad subject" % (tag, aid))
            if a.get("status") not in STATUS_VOCAB:
                fails.append("%s atom %s bad status %r" % (tag, aid, a.get("status")))
            if not isinstance(a.get("negation"), bool):
                fails.append("%s atom %s negation not bool" % (tag, aid))
            vt = str(a.get("valid_time") or "")
            if not re.match(r"^\d{4}-\d{2}(-\d{2})?$", vt):
                fails.append("%s atom %s bad valid_time %r" % (tag, aid, vt))
            if "明天" in vt or "下周" in vt or "下月" in vt:
                fails.append("%s atom %s relative valid_time" % (tag, aid))
            src = a.get("source") or {}
            kind = src.get("kind")
            if kind == "message":
                scid = src.get("case_id")
                if scid not in case_index:
                    fails.append("%s atom %s unknown source case" % (tag, aid))
                else:
                    grand["quotes"] += 1
                    msgs = msg_by_case[scid]
                    hits = [k for k, tx in enumerate(msgs) if src.get("quote", "") in tx]
                    if not hits:
                        fails.append("%s atom %s quote strict MISS" % (tag, aid))
                    else:
                        grand["strict"] += 1
                        want = "m%d" % hits[0]
                        if src.get("message_id") != want:
                            fails.append("%s atom %s message_id %r != %r"
                                         % (tag, aid, src.get("message_id"), want))
                    if case_index[scid] > case_index[cid]:
                        fails.append("%s atom %s source is future" % (tag, aid))
                    want_req = case_index[scid] < case_index[cid]
                    if bool(a.get("required_this_turn")) != want_req:
                        fails.append("%s atom %s required_this_turn=%s but source earlier=%s"
                                     % (tag, aid, a.get("required_this_turn"), want_req))
            elif kind == "initial_kb":
                key = src.get("key")
                if key not in kb:
                    fails.append("%s atom %s kb key missing" % (tag, aid))
                elif src.get("quote") not in kb.get(key, ""):
                    fails.append("%s atom %s kb quote MISS" % (tag, aid))
                if not a.get("required_this_turn"):
                    fails.append("%s atom %s kb atom should be required when needed" % (tag, aid))
            else:
                fails.append("%s atom %s bad source kind %r" % (tag, aid, kind))
            if a.get("required_this_turn"):
                n_req += 1
            # affirm_markers format + verbatim
            amk = a.get("affirm_markers")
            if not amk or not isinstance(amk, list):
                fails.append("%s atom %s affirm_markers missing" % (tag, aid))
            else:
                if not (2 <= len(amk) <= 4):
                    fails.append("%s atom %s marker groups not 2-4" % (tag, aid))
                for g in amk:
                    if not isinstance(g, list) or not (1 <= len(g) <= 3):
                        fails.append("%s atom %s bad marker group size" % (tag, aid))
                    else:
                        for ph in g:
                            L = len(ph)
                            if not (2 <= L <= 12):
                                fails.append("%s atom %s marker len %d out of 2-12: %r"
                                             % (tag, aid, L, ph))
                            if len(g) == 1 and L < MIN_SINGLE_PHRASE_LEN:
                                fails.append("%s atom %s single-phrase <4: %r"
                                             % (tag, aid, ph))
                            if ph not in corpus:
                                fails.append("%s atom %s marker not verbatim: %r"
                                             % (tag, aid, ph))
            # item_markers rules
            if a.get("status") == "cancelled" and not a.get("item_markers"):
                fails.append("%s atom %s cancelled missing item_markers" % (tag, aid))
            for im in a.get("item_markers") or []:
                if not isinstance(im, str) or im not in corpus:
                    fails.append("%s atom %s item_marker not verbatim: %r" % (tag, aid, im))

    dup = [x for x in set(atom_ids) if atom_ids.count(x) > 1]
    if dup:
        fails.append("duplicate atom_id: %s" % dup[:3])

    # audit checks
    for sec in AUDIT_SECTIONS:
        if sec not in audit:
            fails.append("audit missing %s" % sec)
    h2 = re.findall(r"^## ", audit, flags=re.M)
    if len(h2) != 8:
        fails.append("audit '^## ' count=%d != 8" % len(h2))

    stats[eid] = dict(atoms=n_atoms, required=n_req, unknowns=n_unk,
                      forbidden=n_forb, protected=n_prot)
    grand["atoms"] += n_atoms
    grand["required"] += n_req
    grand["unknowns"] += n_unk
    grand["forbidden"] += n_forb
    grand["protected"] += n_prot
    if fails:
        fail_count += len(fails)
        print("== %s FAIL (%d) ==" % (eid, len(fails)))
        for x in fails:
            print("  -", x)
    else:
        print("== %s PASS ==" % eid, stats[eid])

print("\nTOTAL atoms=%d required=%d unknowns=%d forbidden=%d protected=%d" % (
    grand["atoms"], grand["required"], grand["unknowns"], grand["forbidden"], grand["protected"]))
print("QUOTE strict: %d/%d" % (grand["strict"], grand["quotes"]))
print("RESULT:", "ALL PASS" if fail_count == 0 else "FAILURES=%d" % fail_count)
sys.exit(0 if fail_count == 0 else 1)
