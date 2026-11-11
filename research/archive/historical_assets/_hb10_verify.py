#!/usr/bin/env python -I
# Gold quote strict reverify for HOLDOUT HB10 (E491-E500)
# Reads episode.json + reference.json from vault, checks every atom quote verbatim.
import json, sys, os

VAULT = r"D:/lovebrain-arena_holdout_vault/wave6_holdout"

def load(eid):
    ep = json.load(open(os.path.join(VAULT, eid, "episode.json"), encoding="utf-8"))
    ref = json.load(open(os.path.join(VAULT, eid, "reference.json"), encoding="utf-8"))
    return ep, ref

def turn_messages_text(ep, case_id):
    for t in ep["turns"]:
        if t["case_id"] == case_id:
            return [m["text"] for m in t["input"]["messages"]]
    return None

def kb_text(ep, key):
    return ep.get("initial_kb", {}).get(key, "")

def verify(eid):
    ep, ref = load(eid)
    fails = []
    natoms = 0
    nrequired = 0
    for cid, card in ref["turns"].items():
        # may_decline consistency
        ep_turn = next(t for t in ep["turns"] if t["case_id"] == cid)
        md_ref = card.get("may_decline")
        md_ep = ep_turn["input"]["may_decline"]
        if md_ref != md_ep:
            fails.append(f"may_decline mismatch {cid}: ref={md_ref} ep={md_ep}")
        msgs = turn_messages_text(ep, cid)
        for a in card["required_memory_atoms"]:
            natoms += 1
            if a.get("required_this_turn"):
                nrequired += 1
            src = a["source"]
            mid = src["message_id"]
            quote = src["quote"]
            # resolve source text
            if "_KB" in mid or src["key"] in ep.get("initial_kb", {}):
                src_texts = [kb_text(ep, src["key"])]
            else:
                # mid format <EID>_T<NN>_M<NN>
                src_case = mid.rsplit("_M", 1)[0]
                src_texts = turn_messages_text(ep, src_case) or []
                if mid not in [None]:
                    # m index from message_id
                    try:
                        m_idx = int(mid.rsplit("_M",1)[1].lstrip("m"))
                        if m_idx < len(src_texts):
                            src_texts = [src_texts[m_idx]]
                    except Exception:
                        pass
            hit = any(quote in st for st in src_texts)
            if not hit:
                fails.append(f"quote NOT strict {a['id']}: '{quote[:30]}...' mid={mid}")
            # required must source earlier turn
            if a.get("required_this_turn"):
                cur_n = int(cid.split("_T")[1])
                src_case = mid.rsplit("_M",1)[0] if "_M" in mid else src.get("case_id", cid)
                try:
                    src_n = int(src_case.split("_T")[1])
                    if src_n >= cur_n:
                        fails.append(f"required sources current/future {a['id']}: src={src_case} cur={cid}")
                except Exception:
                    fails.append(f"required source parse fail {a['id']}: mid={mid}")
    return natoms, nrequired, fails

if __name__ == "__main__":
    eids = sys.argv[1:] or [f"E49{n}" for n in range(1,10)] + ["E500"]
    allgood = True
    for eid in eids:
        n, nr, fails = verify(eid)
        status = "OK" if not fails else "FAIL"
        if fails: allgood = False
        print(f"{eid}: atoms={n} required={nr} quotes={status}")
        for f in fails:
            print(f"  - {f}")
    print("ALL_OK" if allgood else "HAS_FAILURES")
