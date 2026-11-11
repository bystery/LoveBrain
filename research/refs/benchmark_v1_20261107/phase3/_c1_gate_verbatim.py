# -*- coding: utf-8 -*-
"""Verify t02_signal_gate signal/counter excerpts are verbatim substrings of episode messages."""
import json, os
WAVE="benchmark/stories_v2/wave5_selection"
SIDS_T02=["E340","E341","E342","E343","E344","E345","E346"]
for sid in SIDS_T02:
    ref=json.load(open(os.path.join(WAVE,sid,"reference.json"),encoding="utf-8"))
    ep=json.load(open(os.path.join(WAVE,sid,"episode.json"),encoding="utf-8"))
    gate=ref.get("t02_signal_gate",{})
    # build text per turn
    turn_text={}
    for t in ep["turns"]:
        turn_text[t["case_id"]]="\n".join(m["text"] for m in t["input"]["messages"])
    fails=[]
    for sr in gate.get("signal_rounds",[]) or []:
        cid=sr.get("turn","")
        sig=sr.get("signal","")
        if cid in turn_text and sig and sig not in turn_text[cid]:
            fails.append(("signal",cid,sig[:50]))
    for cs in gate.get("counter_signal_rounds",[]) or []:
        cid=cs.get("turn","")
        sig=cs.get("signal","")
        if cid in turn_text and sig and sig not in turn_text[cid]:
            fails.append(("counter",cid,sig[:50]))
    # check examples are illustrative (not required verbatim - they're candidate-side examples)
    print(f"{sid}: signal_rounds={len(gate.get('signal_rounds',[]))} counter={len(gate.get('counter_signal_rounds',[]))} verbatim_fails={len(fails)}")
    for kind,cid,sig in fails:
        print(f"   FAIL {kind} {cid}: {sig!r}")
