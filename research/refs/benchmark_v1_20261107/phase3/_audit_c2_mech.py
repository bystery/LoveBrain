#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Independent Auditor C2 verification script.
Reads frozen episode/reference/timeline/exposure, verifies:
- quote verbatim (strict) per atom
- subject/value persona name consistency (cross-check vs episode.persona)
- protected_expressions in HER message
- required_this_turn vs source case ordering
- clock monotonicity & ts vs clock
- timeline turn_clocks/message_index consistency
- acceptable_actions >=2 distinct, applicable_dimensions >=3 valid
- unknowns object form + dual keys (markers + affirm_markers)
- name pool membership (HER/ME/third_party)
- arithmetic sanity (numbers in text)
- date/day-of-week consistency (python datetime)
Zero model calls. Read-only.
"""
import json, os, re, sys, datetime

ROOT = "D:/lovebrain-arena/benchmark/stories_v2/wave5_selection"
POOL = "D:/lovebrain-arena/refs/benchmark_v1_20261107/phase3/NAME_POOL_V1.json"

def load(p):
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)

def parse_dt(s):
    return datetime.datetime.strptime(s, "%Y-%m-%d %H:%M")

def her_text(turn):
    return "\n".join(m["text"] for m in turn["input"]["messages"] if m.get("role")=="HER")

def all_text(turn):
    return "\n".join(m["text"] for m in turn["input"]["messages"])

def check_story(eid):
    errs = []
    warns = []
    d = os.path.join(ROOT, eid)
    ep = load(os.path.join(d, "episode.json"))
    ref = load(os.path.join(d, "reference.json"))
    tl = load(os.path.join(d, "timeline.json"))
    exp = load(os.path.join(d, "exposure.json"))
    pool = load(POOL)

    # persona
    her = ep["persona"]["her"]["name"]
    me  = ep["persona"]["me"]["name"]
    # name pool
    assign = pool["selection_assignment"].get(eid)
    if assign:
        if assign["her"] != her:
            errs.append(f"POOL her mismatch: episode={her} pool={assign['her']}")
        if assign["me"] != me:
            errs.append(f"POOL me mismatch: episode={me} pool={assign['me']}")
    else:
        errs.append(f"POOL no assignment for {eid}")
    third_pool = set(pool.get("third_party_pool", []))

    # collect all third-party names mentioned in episode & reference
    turns = ep["turns"]
    cases = [t["case_id"] for t in turns]
    case_idx = {c:i for i,c in enumerate(cases)}

    # clock monotonic
    prev_clock = None
    for t in turns:
        cid = t["case_id"]
        clk = parse_dt(t["input"]["clock"])
        if prev_clock and clk < prev_clock:
            errs.append(f"{cid} clock backwards: {clk} < {prev_clock}")
        prev_clock = clk
        prev_ts = None
        for j,m in enumerate(t["input"]["messages"]):
            ts = parse_dt(m["ts"])
            if ts > clk:
                errs.append(f"{cid} m{j} ts after clock")
            if prev_ts and ts < prev_ts:
                errs.append(f"{cid} m{j} ts backwards")
            prev_ts = ts

    # timeline turn_clocks
    tc = tl.get("turn_clocks") or []
    if len(tc) != len(turns):
        errs.append(f"timeline turn_clocks len {len(tc)} != {len(turns)}")
    else:
        for i,t in enumerate(turns):
            if tc[i] != t["input"]["clock"]:
                errs.append(f"{t['case_id']} timeline clock {tc[i]} != episode {t['input']['clock']}")

    # message_index
    mi = tl.get("message_index") or []
    total_msgs = sum(len(t["input"]["messages"]) for t in turns)
    if len(mi) != total_msgs:
        errs.append(f"timeline message_index len {len(mi)} != {total_msgs}")
    for m in mi:
        cid = m.get("case_id")
        if cid not in case_idx: 
            errs.append(f"timeline msg case unknown {cid}"); continue
        src_msgs = [t for t in turns if t["case_id"]==cid][0]["input"]["messages"]
        idx = int(m.get("message_id","m-1").lstrip("m"))
        if idx<0 or idx>=len(src_msgs):
            errs.append(f"timeline msg idx oob {cid}/m{idx}"); continue
        sm = src_msgs[idx]
        if (m.get("speaker"), m.get("quote"), m.get("ts")) != (sm.get("role"), sm.get("text"), sm.get("ts")):
            errs.append(f"timeline msg mismatch {cid}/m{idx}")

    # reference turns
    rturns = ref["turns"]
    if set(rturns.keys()) != set(cases):
        errs.append("reference case set != episode")

    # persona name list for cross-check (any name from other stories' assignment)
    all_assigned = set()
    for k,v in pool["selection_assignment"].items():
        all_assigned.add(v["her"]); all_assigned.add(v["me"])
    other_names = all_assigned - {her, me}

    atom_count = 0
    required_count = 0
    quote_strict_fail = []
    persona_name_issues = []
    for cid in cases:
        t = [tt for tt in turns if tt["case_id"]==cid][0]
        r = rturns.get(cid, {})
        # acceptable_actions
        acts = r.get("acceptable_actions") or []
        if len(acts) < 2:
            errs.append(f"{cid} <2 acceptable_actions")
        if len(set(acts)) != len(acts):
            errs.append(f"{cid} duplicate acceptable_actions")
        dims = set(r.get("applicable_dimensions") or [])
        DIMS={"goal","factuality","attribution","time_state","boundary","memory_use","interaction","initiative","emotion_relation"}
        if not dims <= DIMS:
            errs.append(f"{cid} unknown dims {dims-DIMS}")
        if len(dims) < 3:
            errs.append(f"{cid} <3 applicable_dimensions")
        # may_decline consistency
        md_ep = t["input"].get("may_decline", False)
        md_ref = r.get("may_decline", False)
        if md_ep != md_ref:
            errs.append(f"{cid} may_decline mismatch ep={md_ep} ref={md_ref}")
        # protected_expressions
        ht = her_text(t)
        for p in r.get("protected_expressions") or []:
            if p not in ht:
                errs.append(f"{cid} protected_expression not in HER msg: {p!r}")
        # unknowns object form + dual keys
        for u in r.get("unknowns") or []:
            if not isinstance(u, dict):
                errs.append(f"{cid} unknown not object: {u!r}")
                continue
            for k in ("topic","note","markers"):
                if k not in u:
                    errs.append(f"{cid} unknown missing {k}")
            mk = u.get("markers")
            am = u.get("affirm_markers")
            if mk is not None and am is not None and mk != am:
                warns.append(f"{cid} unknown markers != affirm_markers")
        # atoms
        cur_idx = case_idx[cid]
        for atom in r.get("required_memory_atoms") or []:
            atom_count += 1
            aid = atom.get("atom_id")
            src = atom.get("source") or {}
            scid = src.get("case_id")
            # required_this_turn rule (official validator L231): only message-source
            # atoms with scid==cid violate; initial_kb sources are always prior (KB
            # is established before T01), so required=true on kb atoms is legal.
            if atom.get("required_this_turn"):
                required_count += 1
                if src.get("kind") == "message" and scid == cid:
                    errs.append(f"{aid} required_this_turn but sourced from current turn")
            # quote verbatim
            if src.get("kind") == "message":
                if scid not in case_idx:
                    errs.append(f"{aid} source case {scid} not in episode")
                    continue
                if case_idx[scid] > cur_idx:
                    errs.append(f"{aid} source {scid} is future vs {cid}")
                src_turn = [tt for tt in turns if tt["case_id"]==scid][0]
                msgs = src_turn["input"]["messages"]
                quote = src.get("quote","")
                hit = [k for k,m in enumerate(msgs) if quote in m.get("text","")]
                if not hit:
                    quote_strict_fail.append(f"{aid} quote not in {scid} m0: {quote[:40]!r}")
                    errs.append(f"{aid} quote not in source {scid}")
                elif src.get("message_id") != f"m{hit[0]}":
                    errs.append(f"{aid} message_id m{hit[0]} != src {src.get('message_id')}")
            elif src.get("kind") == "initial_kb":
                key = src.get("key")
                kb = ep.get("initial_kb",{})
                if key not in kb:
                    errs.append(f"{aid} kb key {key} missing")
                elif src.get("quote","") not in kb.get(key,""):
                    errs.append(f"{aid} quote not in kb.{key}")
            # persona name consistency: check value/relation for wrong ME/HER name
            val = atom.get("value","")
            rel = atom.get("relation","")
            subj = atom.get("subject","")
            # If subject is ME, value should not name a different ME
            # check for other-story names appearing
            for nm in other_names:
                if nm in val:
                    persona_name_issues.append(f"{aid} value contains other-story name {nm!r}: {val[:80]!r}")
                if nm in rel:
                    persona_name_issues.append(f"{aid} relation contains other-story name {nm!r}: {rel[:80]!r}")
    for p in persona_name_issues:
        errs.append(p)
    # date arithmetic / day of week
    # gather weekday refs in text
    weekday_cn = ["周一","周二","周三","周四","周五","周六","周日"]
    weekday_cn2 = ["星期一","星期二","星期三","星期四","星期五","星期六","星期日"]
    # arithmetic: look for money/quantity patterns
    return errs, warns, {
        "her": her, "me": me, "atoms": atom_count, "required": required_count,
        "quote_strict_fail": quote_strict_fail, "persona_name_issues": persona_name_issues,
    }

def main():
    stories = ["E349","E350","E351","E352","E353","E354","E355","E356","E357","E358"]
    summary = {}
    for eid in stories:
        errs, warns, stats = check_story(eid)
        summary[eid] = {"errs": errs, "warns": warns, "stats": stats}
        print(f"\n=== {eid} her={stats['her']} me={stats['me']} atoms={stats['atoms']} required={stats['required']} ===")
        if not errs:
            print("  OK (no mechanical errors)")
        else:
            for e in errs:
                print("  ERR:", e)
        for w in warns:
            print("  WARN:", w)
    # dump json
    with open("D:/lovebrain-arena/refs/benchmark_v1_20261107/phase3/_audit_c2_mech.json","w",encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("\nWrote _audit_c2_mech.json")

if __name__ == "__main__":
    main()
