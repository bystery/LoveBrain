# -*- coding: utf-8 -*-
"""ANNOTATOR-B4 gold strict verifier (stdlib only, run with python -I)."""
import json, os, re, sys

BASE = r"D:/lovebrain-arena/benchmark/stories_v2/wave5_selection"
EIDS = ["E346", "E350", "E354", "E355", "E368"]
DIMS = {"goal", "factuality", "attribution", "time_state", "boundary",
        "memory_use", "interaction", "initiative", "emotion_relation"}
STATES = {"asserted", "confirmed", "tentative", "cancelled", "canceled", "superseded",
          "conflict", "invalidated", "unknown", "erroneous", "fulfilled", "resolved", "retracted",
          "voided", "revoked", "negated"}
FIELDS = ["case_id", "current_action", "acceptable_actions", "applicable_dimensions",
          "required_memory_atoms", "private_guidance", "unknowns",
          "protected_expressions", "forbidden_factual_claims", "may_decline"]

fail = []
warn = []
stats = {}

def tno(cid):
    return int(cid.split("_T")[1])

def check_markers(where, am, src_text, corpus):
    if not isinstance(am, list) or not am or not all(isinstance(g, list) and len(g) >= 1 for g in am):
        fail.append(f"{where}: affirm_markers malformed")
        return
    if not (2 <= len(am) <= 4):
        fail.append(f"{where}: affirm_markers groups={len(am)} (need 2-4)")
    for g in am:
        if len(g) > 3:
            fail.append(f"{where}: group has {len(g)} phrases (max 3)")
        for ph in g:
            if not isinstance(ph, str) or not (2 <= len(ph) <= 12):
                fail.append(f"{where}: phrase len bad: {ph!r}")
            if ph not in corpus:
                fail.append(f"{where}: marker not in corpus: {ph!r}")
            if src_text is not None and ph not in src_text:
                fail.append(f"{where}: marker not in SOURCE msg: {ph!r}")
        if len(g) == 1 and len(g[0]) < 4:
            fail.append(f"{where}: single-phrase group <4 chars: {g[0]!r}")

for eid in EIDS:
    ep = json.load(open(os.path.join(BASE, eid, "episode.json"), encoding="utf-8"))
    with open(os.path.join(BASE, eid, "reference.json"), encoding="utf-8") as f:
        ref = json.load(f)
    ep_turns = {t["case_id"]: t for t in ep["turns"]}
    ep_cases = [t["case_id"] for t in ep["turns"]]
    if ref.get("story_id") != eid:
        fail.append(f"{eid}: story_id mismatch")
    if sorted(ref["turns"].keys()) != sorted(ep_cases):
        fail.append(f"{eid}: case set mismatch: {sorted(ref['turns'].keys())} vs {sorted(ep_cases)}")

    msgs_by, her_by, corpus = {}, {}, ""
    for cid, t in ep_turns.items():
        ms = [m["text"] for m in t["input"]["messages"]]
        msgs_by[cid] = ms
        her_by[cid] = [m["text"] for m in t["input"]["messages"] if m["role"] == "HER"]
        corpus += "".join(ms)
    kb_all = "".join(ep["initial_kb"].values())
    corpus += kb_all

    n_atoms = n_req = n_unk = n_forb = n_prot = 0
    for cid, card in ref["turns"].items():
        w = f"{eid}/{cid}"
        t = ep_turns[cid]
        for k in FIELDS:
            if k not in card:
                fail.append(f"{w}: missing field {k}")
        if card.get("case_id") != cid:
            fail.append(f"{w}: case_id field != key")
        if not str(card.get("current_action", "")).strip():
            fail.append(f"{w}: empty current_action")
        if not str(card.get("private_guidance", "")).strip():
            fail.append(f"{w}: empty private_guidance")
        aa = card.get("acceptable_actions") or []
        if len(aa) < 3 or len(aa) > 4:
            fail.append(f"{w}: acceptable_actions count={len(aa)} (need 3-4)")
        dims = card.get("applicable_dimensions") or []
        if len(dims) < 3:
            fail.append(f"{w}: dims <3")
        for d in dims:
            if d not in DIMS:
                fail.append(f"{w}: bad dim {d}")
        md_ep = bool(t["input"].get("may_decline", False))
        if bool(card.get("may_decline")) != md_ep:
            fail.append(f"{w}: may_decline mismatch card={card.get('may_decline')} ep={md_ep}")

        # protected expressions: verbatim in THIS turn HER messages
        her_all = "\n".join(her_by[cid])
        for p in card.get("protected_expressions") or []:
            n_prot += 1
            if p not in her_all:
                fail.append(f"{w}: protected not in HER msg: {p!r}")

        # unknowns
        for u in card.get("unknowns") or []:
            n_unk += 1
            if not isinstance(u, dict) or "topic" not in u:
                fail.append(f"{w}: unknown not object: {u!r}")
                continue
            mk, amk = u.get("markers"), u.get("affirm_markers")
            if mk != amk:
                fail.append(f"{w}: unknown double-key mismatch: {u['topic']}")
            if not isinstance(mk, list) or not all(isinstance(g, list) and g for g in mk):
                fail.append(f"{w}: unknown markers malformed: {u['topic']}")

        # atoms
        cur = tno(cid)
        for a in card.get("required_memory_atoms") or []:
            n_atoms += 1
            aid = a.get("atom_id", "?")
            aw = f"{w}/{aid}"
            if not aid.startswith(eid + "_T"):
                fail.append(f"{aw}: atom_id prefix bad")
            if a.get("status") not in STATES:
                fail.append(f"{aw}: bad status {a.get('status')}")
            for k in ("subject", "relation", "value", "valid_time", "status", "negation",
                      "source", "required_this_turn", "affirm_markers"):
                if k not in a:
                    fail.append(f"{aw}: atom missing {k}")
            src = a["source"]
            kind = src.get("kind")
            src_text = None
            if kind == "message":
                sc = src["case_id"]
                if sc not in msgs_by:
                    fail.append(f"{aw}: source case missing {sc}")
                    continue
                texts = msgs_by[sc]
                hits = [k2 for k2, x in enumerate(texts) if src["quote"] in x]
                if not hits:
                    fail.append(f"{aw}: quote STRICT MISS: {src['quote'][:30]!r}")
                else:
                    exp = f"m{hits[0]}"
                    if src.get("message_id") != exp:
                        fail.append(f"{aw}: message_id {src.get('message_id')} != {exp}")
                    src_text = texts[hits[0]]
                if tno(sc) > cur:
                    fail.append(f"{aw}: source is future ({sc})")
                if a.get("required_this_turn") and tno(sc) >= cur:
                    fail.append(f"{aw}: required=true but source is current turn ({sc})")
            elif kind == "initial_kb":
                kb = ep["initial_kb"].get(src.get("key"), "")
                if src["quote"] not in kb:
                    fail.append(f"{aw}: KB quote MISS: {src['quote'][:30]!r}")
                src_text = kb
                if a.get("required_this_turn") and tno(cid) < 1:
                    pass
            else:
                fail.append(f"{aw}: bad source kind {kind}")
            check_markers(aw, a.get("affirm_markers"), src_text, corpus)
            # item_markers for cancelled/canceled
            if a.get("status") in ("cancelled", "canceled") and not a.get("item_markers"):
                fail.append(f"{aw}: cancelled atom missing item_markers")
            if a.get("required_this_turn"):
                n_req += 1
        n_forb += len(card.get("forbidden_factual_claims") or [])

    # t02 gate
    if eid == "E346":
        gate = ref.get("t02_signal_gate")
        gkeys = ["signal_rounds", "counter_signal_rounds", "initiative_probe_suitable",
                 "retreat_room_required", "overstep_turn", "customer_service_examples",
                 "safe_but_flat_examples", "recovery_check_turn"]
        if not isinstance(gate, dict):
            fail.append("E346: t02_signal_gate missing")
        else:
            for k in gkeys:
                if k not in gate:
                    fail.append(f"E346: gate missing key {k}")
            for s in gate.get("signal_rounds", []):
                if s.get("strength") not in ("weak", "medium", "strong"):
                    fail.append(f"E346: bad strength {s}")
                q = s.get("signal", "")
                core = q.split("“")[-1].split("”")[0] if "“" in q else q
                # strip leading turn ref text before the quote
                m = re.search(r"“(.+?)”", q)
                if m:
                    core = m.group(1)
                else:
                    core = q
                # signal quote must be verbatim in corpus (allow ellipsis joins)
                parts = [p.strip() for p in core.split("……")]
                for p in parts:
                    if p and p not in corpus:
                        fail.append(f"E346: gate signal excerpt not verbatim: {p[:30]!r}")
            for s in gate.get("counter_signal_rounds", []):
                if s.get("type") not in ("withdraw", "cooldown", "topic_shift"):
                    fail.append(f"E346: bad counter type {s}")

    # audit.md sections
    audit_path = os.path.join(BASE, eid, "audit.md")
    txt = open(audit_path, encoding="utf-8").read()
    heads = re.findall(r"^## ", txt, flags=re.M)
    if len(heads) != 8:
        fail.append(f"{eid}: audit.md sections={len(heads)} (need 8)")

    stats[eid] = dict(atoms=n_atoms, required=n_req, unknowns=n_unk,
                      forbidden=n_forb, protected=n_prot)

print("=== STATS ===")
total = dict(atoms=0, required=0, unknowns=0, forbidden=0, protected=0)
for e, s in stats.items():
    print(e, s)
    for k in total:
        total[k] += s[k]
print("TOTAL", total)
print("=== FAIL ===")
if fail:
    for x in fail:
        print("FAIL:", x)
    print(f"\n{len(fail)} FAILURES")
    sys.exit(1)
else:
    print("0 failures — ALL CHECKS PASS")
