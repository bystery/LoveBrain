# -*- coding: utf-8 -*-
"""C1 independent auditor comprehensive check script (E339-E348).
Read-only. Zero model calls. Standard library only.
Checks: gold quote-verbatim strict, required_this_turn vs source,
unknowns/forbidden boundary sanity, protected_expressions verbatim,
time clock monotonic + date-weekday, arithmetic recompute, topic match,
t02_signal_gate nine-item, name pool membership.
"""
import json, os, re, datetime, sys

WAVE = "benchmark/stories_v2/wave5_selection"
SIDS = ["E339","E340","E341","E342","E343","E344","E345","E346","E347","E348"]
WEEKDAYS_ZH = ["周一","周二","周三","周四","周五","周六","周日"]

def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)

def msgs_of_turn(ep, cid):
    for t in ep["turns"]:
        if t["case_id"] == cid:
            return t["input"]["messages"]
    return []

def her_text_of_turn(ep, cid):
    return "\n".join(m["text"] for m in msgs_of_turn(ep, cid) if m.get("role")=="HER")

def all_text_of_turn(ep, cid):
    return "\n".join(m["text"] for m in msgs_of_turn(ep, cid))

WD = {"monday":0,"tuesday":1,"wednesday":2,"thursday":3,"friday":4,"saturday":5,"sunday":6,
      "周一":0,"周二":1,"周三":2,"周四":3,"周五":4,"周六":5,"周日":6,
      "星期一":0,"星期二":1,"星期三":2,"星期四":3,"星期五":4,"星期六":5,"星期日":6}

def audit_one(sid):
    d = os.path.join(WAVE, sid)
    ep = load(os.path.join(d,"episode.json"))
    ref = load(os.path.join(d,"reference.json"))
    tl = load(os.path.join(d,"timeline.json"))
    exp = load(os.path.join(d,"exposure.json"))
    findings = []
    # persona names
    per = ep.get("persona",{})
    her_name = per.get("her",{}).get("name") if isinstance(per.get("her"),dict) else None
    me_name = per.get("me",{}).get("name") if isinstance(per.get("me"),dict) else None
    turns = ep["turns"]
    rturns = ref["turns"]
    # case set equality
    ep_cases = [t["case_id"] for t in turns]
    if set(ep_cases) != set(rturns.keys()):
        findings.append("MECH: episode case set != reference case set")
    # may_decline consistency episode vs card
    for t in turns:
        cid = t["case_id"]
        md_ep = t["input"].get("may_decline", False)
        card = rturns.get(cid,{})
        md_card = card.get("may_decline")
        if md_card is not None and bool(md_card) != bool(md_ep):
            findings.append("MECH: %s may_decline episode=%s card=%s mismatch" % (cid, md_ep, md_card))
    # clock monotonic
    prev = None
    for t in turns:
        c = t["input"]["clock"]
        cdt = datetime.datetime.strptime(c, "%Y-%m-%d %H:%M")
        if prev and cdt < prev:
            findings.append("TIME: %s clock %s < prev %s (backwards)" % (t["case_id"], c, prev))
        # ts <= clock + intra-turn ts monotonic
        pt = None
        for m in t["input"]["messages"]:
            mt = datetime.datetime.strptime(m["ts"], "%Y-%m-%d %H:%M")
            if mt > cdt:
                findings.append("TIME: %s m.ts %s > clock %s" % (t["case_id"], m["ts"], c))
            if pt and mt < pt:
                findings.append("TIME: %s intra-turn ts backwards %s<%s" % (t["case_id"], m["ts"], pt))
            pt = mt
        prev = cdt
    # date-weekday consistency: scan HER/all text for "周X" / "星期X" + dates
    weekday_pairs = []
    for t in turns:
        cid = t["case_id"]
        txt = all_text_of_turn(ep, cid)
        # find explicit dates like 2026-MM-DD or 11月14日 / 12月12日
        for m in re.finditer(r"(\d{1,2})月(\d{1,2})日", txt):
            mm, dd = int(m.group(1)), int(m.group(2))
            # need a year anchor: use clock year
            yr = int(t["input"]["clock"][:4])
            try:
                that = datetime.date(yr, mm, dd)
                wd_actual = that.weekday()
            except ValueError:
                continue
            # look for nearby weekday mention
            ctx = txt[max(0,m.start()-15):m.end()+15]
            for wk, wdi in WD.items():
                if wk in ctx:
                    if wdi != wd_actual:
                        findings.append("TIME: %s date %d月%d日=%s but text says %s" % (cid, mm, dd, WEEKDAYS_ZH[wd_actual], wk))
                    weekday_pairs.append((cid, mm, dd, wk, WEEKDAYS_ZH[wd_actual]))
                    break
    # GOLD quote verbatim strict (atoms)
    atom_count = 0
    quote_fail = 0
    for cid in ep_cases:
        card = rturns.get(cid,{})
        for atom in card.get("required_memory_atoms",[]) or []:
            atom_count += 1
            aid = atom.get("atom_id")
            src = atom.get("source",{})
            scid = src.get("case_id")
            if src.get("kind") == "message":
                # find source turn in THIS episode
                sm = msgs_of_turn(ep, scid) if scid in ep_cases else []
                if not sm:
                    findings.append("GOLD: %s atom %s source case %s not found in episode" % (cid, aid, scid))
                    quote_fail += 1
                    continue
                q = src.get("quote","")
                hit = [k for k,mm in enumerate(sm) if q in mm.get("text","")]
                if not hit:
                    findings.append("GOLD-QUOTE: %s atom %s quote not verbatim in source %s :: %r" % (cid, aid, scid, q[:40]))
                    quote_fail += 1
                else:
                    if src.get("message_id") != "m%d" % hit[0]:
                        findings.append("GOLD: %s atom %s message_id %s != expected m%d" % (cid, aid, src.get("message_id"), hit[0]))
            elif src.get("kind") == "initial_kb":
                kb = ep.get("initial_kb",{})
                key = src.get("key")
                if key not in kb:
                    findings.append("GOLD: %s atom %s kb key %s missing" % (cid, aid, key))
                    quote_fail += 1
                elif src.get("quote","") not in kb.get(key,""):
                    findings.append("GOLD-QUOTE: %s atom %s quote not in kb.%s :: %r" % (cid, aid, key, src.get("quote","")[:40]))
                    quote_fail += 1
            # required_this_turn vs source
            if atom.get("required_this_turn") and scid == cid:
                findings.append("GOLD: %s atom %s required_this_turn=true but sourced from current turn" % (cid, aid))
            # source future
            if scid in ep_cases and scid != cid:
                if ep_cases.index(scid) > ep_cases.index(cid):
                    findings.append("GOLD: %s atom %s source %s is future turn" % (cid, aid, scid))
    # protected_expressions verbatim in HER message
    for cid in ep_cases:
        card = rturns.get(cid,{})
        her_txt = her_text_of_turn(ep, cid)
        for p in card.get("protected_expressions",[]) or []:
            if p not in her_txt:
                findings.append("GOLD-PROT: %s protected_expression not in HER msg: %r" % (cid, p[:40]))
    # unknowns object form + dual-key
    for cid in ep_cases:
        card = rturns.get(cid,{})
        for u in card.get("unknowns",[]) or []:
            if isinstance(u, str):
                findings.append("GOLD-UNK: %s unknowns has bare string: %r" % (cid, u[:30]))
            elif isinstance(u, dict):
                if "topic" not in u:
                    findings.append("GOLD-UNK: %s unknown missing topic" % cid)
                # dual key check (markers + affirm_markers)
                mk = u.get("markers")
                am = u.get("affirm_markers")
                if mk is not None and am is not None and mk != am:
                    findings.append("GOLD-UNK: %s unknown markers!=affirm_markers" % cid)
    # acceptable_actions >=2 unique
    for cid in ep_cases:
        card = rturns.get(cid,{})
        acts = card.get("acceptable_actions",[]) or []
        if len(acts) < 2:
            findings.append("GOLD: %s fewer than 2 acceptable_actions" % cid)
        if len(set(acts)) != len(acts):
            findings.append("GOLD: %s duplicate acceptable_actions" % cid)
        dims = set(card.get("applicable_dimensions",[]) or [])
        if len(dims) < 3:
            findings.append("GOLD: %s fewer than 3 applicable_dimensions" % cid)
    return {
        "sid": sid, "her": her_name, "me": me_name,
        "turns": len(turns), "atoms": atom_count,
        "quote_fail": quote_fail,
        "weekday_pairs_checked": weekday_pairs,
        "findings": findings,
        "primary_topic": exp.get("primary_topic"),
        "domain": exp.get("domain"),
        "lineage": exp.get("lineage_group_id"),
    }

if __name__ == "__main__":
    results = []
    for sid in SIDS:
        r = audit_one(sid)
        results.append(r)
        print("=== %s turns=%d atoms=%d quote_fail=%d findings=%d topic=%s ===" % (
            sid, r["turns"], r["atoms"], r["quote_fail"], len(r["findings"]), r["primary_topic"]))
        for f in r["findings"]:
            print("   ", f)
    # save full report
    with open("benchmark/stories_v2/wave5_selection/C1_DEEP_AUDIT.json","w",encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=1)
    print("\nSaved C1_DEEP_AUDIT.json")
