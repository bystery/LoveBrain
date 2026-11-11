# -*- coding: utf-8 -*-
"""Check t02_signal_gate nine-item completeness for T02 stories."""
import json, os
WAVE="benchmark/stories_v2/wave5_selection"
SIDS_T01=["E339"]
SIDS_T02=["E340","E341","E342","E343","E344","E345","E346"]
SIDS_T03=["E347","E348"]
GATE_FIELDS=["signal_rounds","counter_signal_rounds","initiative_probe_suitable",
             "retreat_room_required","overstep_turn","customer_service_examples",
             "safe_but_flat_examples","recovery_check_turn"]
# nine fields = above 8 + (signal_strength_gradient OR signal_distribution). Check what's present.
for sid in SIDS_T02:
    ref=json.load(open(os.path.join(WAVE,sid,"reference.json"),encoding="utf-8"))
    # gate may be top-level or per-turn
    gate=ref.get("t02_signal_gate")
    if gate is None:
        # check if any turn has it
        found=False
        for cid,card in ref["turns"].items():
            if "t02_signal_gate" in card:
                gate=card["t02_signal_gate"]; found=True
                print(f"{sid}: gate found in turn {cid}")
                break
        if not found:
            print(f"{sid}: NO t02_signal_gate found anywhere")
            continue
    else:
        print(f"{sid}: gate at top-level")
    keys=list(gate.keys())
    print(f"   fields ({len(keys)}): {keys}")
    missing=[f for f in GATE_FIELDS if f not in gate]
    if missing:
        print(f"   MISSING: {missing}")
    # check signal_rounds count
    sr=gate.get("signal_rounds",[])
    print(f"   signal_rounds count={len(sr)} (need >=4)")
    csr=gate.get("counter_signal_rounds",[])
    print(f"   counter_signal_rounds count={len(csr)} (need >=1)")
print()
# Also check exposure primary_topic
for sid in SIDS_T01+SIDS_T02+SIDS_T03:
    exp=json.load(open(os.path.join(WAVE,sid,"exposure.json"),encoding="utf-8"))
    print(f"{sid}: primary_topic={exp.get('primary_topic')} domain={exp.get('domain')} lineage={exp.get('lineage_group_id')}")
