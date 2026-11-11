# -*- coding: utf-8 -*-
"""C1 scoring-defect precheck: unknowns markers that could catch question-form
correct replies, forbidden boundaries, acceptable_actions preset-stance, and
universal-sentence lure positions. Read-only."""
import json, os
WAVE="benchmark/stories_v2/wave5_selection"
SIDS=["E339","E340","E341","E342","E343","E344","E345","E346","E347","E348"]
for sid in SIDS:
    ref=json.load(open(os.path.join(WAVE,sid,"reference.json"),encoding="utf-8"))
    print(f"\n===== {sid} =====")
    # 1. unknowns markers analysis: question-form safety
    risky_markers=[]
    for cid,card in ref["turns"].items():
        for u in card.get("unknowns",[]) or []:
            if not isinstance(u,dict): continue
            mk=u.get("markers") or u.get("affirm_markers") or []
            topic=u.get("topic","")
            # check if marker could be caught by question form (e.g. single short phrase)
            for grp in mk:
                for phrase in grp:
                    if len(phrase)<4:
                        risky_markers.append((cid,topic,phrase,"short<4"))
    if risky_markers:
        print("  SHORT MARKERS (<4 chars, question-form risk):")
        for cid,t,p,reason in risky_markers:
            print(f"    {cid} topic={t!r} phrase={p!r} ({reason})")
    else:
        print("  no short markers (<4) in unknowns")
    # 2. may_decline consistency
    ep=json.load(open(os.path.join(WAVE,sid,"episode.json"),encoding="utf-8"))
    md_ep=[t["case_id"] for t in ep["turns"] if t["input"].get("may_decline")]
    md_card=[cid for cid,card in ref["turns"].items() if card.get("may_decline")]
    if md_ep!=md_card:
        print(f"  MAY_DECLINE MISMATCH: ep={md_ep} card={md_card}")
    else:
        print(f"  may_decline turns: {md_ep}")
    # 3. intent turns consistency
    intent_ep=[t["case_id"] for t in ep["turns"] if t["input"].get("intent")]
    print(f"  intent turns (episode): {intent_ep}")
    # 4. forbidden count per turn
    forb_count=sum(len(card.get("forbidden_factual_claims",[]) or []) for card in ref["turns"].values())
    print(f"  total forbidden_factual_claims: {forb_count}")
    # 5. atom status distribution
    statuses={}
    for card in ref["turns"].values():
        for a in card.get("required_memory_atoms",[]) or []:
            s=a.get("status","?")
            statuses[s]=statuses.get(s,0)+1
    print(f"  atom status dist: {statuses}")
