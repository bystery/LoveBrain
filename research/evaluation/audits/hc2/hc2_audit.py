#!/usr/bin/env python -I
# AUDITOR-HC2: independent audit of E431-E460 (30 HOLDOUT stories)
# Reads ONLY from D:/lovebrain-arena_holdout_vault/wave6_holdout/E{431..460}/
# Plus NAME_POOL_V1.json (holdout_assignment) for name consistency.
# Zero model calls. Deterministic.
import json, hashlib, re, os, sys

VAULT = r"D:\lovebrain-arena_holdout_vault\wave6_holdout"
POOL = r"D:\lovebrain-arena\refs\benchmark_v1_20261107\phase3\NAME_POOL_V1.json"
EIDS = [f"E{i}" for i in range(431, 461)]

BANNED = ["考察","观测点","评分","考点","本题","测试点","与上轮不同","evaluation point","grading","rubric","探针","测试用例","设计意图","考察点","评分点"]
AUDIT_SECTIONS = ["## 0.", "## 1.", "## 2.", "## 3.", "## 4.", "## 5.", "## 6.", "## 7."]
CASE_RE = re.compile(r"^E\d+_T\d+$")

def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)

def md5(path):
    with open(path, "rb") as f:
        return hashlib.md5(f.read()).hexdigest()

def dt(s):
    return s  # string compare works for "YYYY-MM-DD HH:MM" lexicographic

def msg_fields(m):
    # normalize both schemas
    role = m.get("role") or m.get("sender")
    text = m.get("text","")
    ts = m.get("ts") or m.get("timestamp")
    return role, text, ts

results = {}  # eid -> {checks:{}, fails:[], warns:[]}

# load name pool
pool = load(POOL)
holdout_assign = pool["holdout_assignment"]
third_party = set(pool["third_party_pool"])

# cross-story name registry: which eid uses which name (protagonist or third party mention)
cross_name_registry = {}  # name -> set(eids)

for eid in EIDS:
    d = os.path.join(VAULT, eid)
    res = {"checks": {}, "fails": [], "warns": []}
    try:
        ep = load(os.path.join(d, "episode.json"))
        ref = load(os.path.join(d, "reference.json"))
        tl = load(os.path.join(d, "timeline.json"))
        ex = load(os.path.join(d, "exposure.json"))
        with open(os.path.join(d, "audit.md"), encoding="utf-8") as f:
            audit_md = f.read()
    except Exception as e:
        res["fails"].append(f"FILE_LOAD_ERROR: {e}")
        results[eid] = res
        continue

    # 1. name consistency
    exp_her = ep["persona"]["her"]["name"]
    exp_me = ep["persona"]["me"]["name"]
    pool_entry = holdout_assign.get(eid, {})
    c1 = (exp_her == pool_entry.get("her") and exp_me == pool_entry.get("me"))
    res["checks"]["01_name_consistency"] = c1
    if not c1:
        res["fails"].append(f"NAME_MISMATCH: ep her={exp_her} me={exp_me} vs pool her={pool_entry.get('her')} me={pool_entry.get('me')}")

    # register protagonist names for cross-story check
    for nm in (exp_her, exp_me):
        cross_name_registry.setdefault(nm, set()).add(eid)

    # 2. turns = 12
    nturns = len(ep["turns"])
    c2 = (nturns == 12)
    res["checks"]["02_turns_12"] = c2
    if not c2: res["fails"].append(f"TURNS={nturns} != 12")

    # 3. case_id unique + format
    case_ids = [t["case_id"] for t in ep["turns"]]
    c3 = all(CASE_RE.match(c) and c.startswith(eid+"_") for c in case_ids) and len(set(case_ids))==len(case_ids)
    res["checks"]["03_case_id"] = c3
    if not c3: res["fails"].append(f"CASE_ID_BAD: {case_ids}")

    # 4. turn_type=reply
    c4 = all(t.get("turn_type")=="reply" for t in ep["turns"])
    res["checks"]["04_turn_type_reply"] = c4
    if not c4: res["fails"].append("TURN_TYPE_NOT_REPLY")

    # 5. gold={}
    c5 = all(t.get("gold")=={} for t in ep["turns"])
    res["checks"]["05_gold_empty"] = c5
    if not c5: res["fails"].append("GOLD_NOT_EMPTY")

    # 6. clock monotonic non-decreasing
    clocks = [t["input"]["clock"] for t in ep["turns"]]
    c6 = all(clocks[i] <= clocks[i+1] for i in range(len(clocks)-1))
    res["checks"]["06_clock_monotone"] = c6
    if not c6: res["fails"].append(f"CLOCK_NOT_MONOTONE: {clocks}")

    # ts <= clock per turn & same-turn ts increasing
    ts_clock_ok = True
    for t in ep["turns"]:
        msgs = t["input"]["messages"]
        clk = t["input"]["clock"]
        tss = []
        for m in msgs:
            role,text,ts = msg_fields(m)
            tss.append(ts)
            if ts and ts > clk:
                ts_clock_ok = False
                res["fails"].append(f"{t['case_id']}: ts {ts} > clock {clk}")
        if len(tss)>1 and not all(tss[i] <= tss[i+1] for i in range(len(tss)-1)):
            ts_clock_ok = False
            res["fails"].append(f"{t['case_id']}: same-turn ts not increasing {tss}")
    res["checks"]["06b_ts_clock_ok"] = ts_clock_ok

    # 7. may_decline count >= 2
    md_count = sum(1 for t in ep["turns"] if t["input"].get("may_decline")==True)
    c7 = (md_count >= 2)
    res["checks"]["07_may_decline_ge2"] = c7
    res["checks"]["07_may_decline_count"] = md_count
    if not c7: res["fails"].append(f"MAY_DECLINE={md_count} < 2")

    # 8. intent in 2-4 turns
    intent_turns = [t["case_id"] for t in ep["turns"] if t["input"].get("intent") and t["input"]["intent"].get("enabled")==True]
    c8 = (2 <= len(intent_turns) <= 4)
    res["checks"]["08_intent_2to4"] = c8
    res["checks"]["08_intent_count"] = len(intent_turns)
    if not c8: res["fails"].append(f"INTENT_COUNT={len(intent_turns)} out of [2,4]: {intent_turns}")

    # 9. note in 1-3 turns
    note_turns = [t["case_id"] for t in ep["turns"] if t["input"].get("note")]
    c9 = (1 <= len(note_turns) <= 3)
    res["checks"]["09_note_1to3"] = c9
    res["checks"]["09_note_count"] = len(note_turns)
    if not c9: res["fails"].append(f"NOTE_COUNT={len(note_turns)} out of [1,3]: {note_turns}")

    # 10. banned keywords 0
    banned_hits = []
    all_visible = []
    for t in ep["turns"]:
        for m in t["input"]["messages"]:
            role,text,ts = msg_fields(m)
            all_visible.append(text)
        if t["input"].get("note"): all_visible.append(t["input"]["note"])
        it = t["input"].get("intent")
        if it and it.get("text"): all_visible.append(it["text"])
    joined = "\n".join(all_visible)
    for bw in BANNED:
        if bw in joined:
            banned_hits.append(bw)
    c10 = (len(banned_hits)==0)
    res["checks"]["10_banned_zero"] = c10
    if not c10: res["fails"].append(f"BANNED_HITS: {banned_hits}")

    # 11. quote strict: reference atoms' source.quote must be verbatim in message text or initial_kb
    # build message text lookup by (case_id, message_id) and kb lookup
    msg_by = {}  # (case_id, mid) -> text
    kb_str = ""
    kb_lookup = {}
    if isinstance(ep["initial_kb"]["me"], str):
        kb_str += ep["initial_kb"]["me"] + "\n" + ep["initial_kb"].get("her","") + "\n" + ep["initial_kb"].get("warmth","") + "\n" + ep["initial_kb"].get("style","") + "\n" + ep["initial_kb"].get("lessons","")
    else:
        # list schema
        for side in ("me","her"):
            for entry in ep["initial_kb"].get(side,[]):
                kb_lookup[entry.get("key","")] = entry.get("value","")
                kb_str += entry.get("value","") + "\n"
        for k in ("warmth","style","lessons"):
            v = ep["initial_kb"].get(k)
            if isinstance(v,str):
                kb_str += v + "\n"
    # index messages by (source_case_id, message_id_token) — message_id tokens observed: "M0","m0","0","1","M1",...
    msg_by = {}  # (case_id, token) -> text ; tokens include M0/m0/0 forms
    for t in ep["turns"]:
        tcid = t["case_id"]
        for idx, m in enumerate(t["input"]["messages"]):
            role, text, ts = msg_fields(m)
            for tok in (f"M{idx}", f"m{idx}", str(idx)):
                msg_by[(tcid, tok)] = text

    quote_fail = []
    ref_turns = ref.get("turns", {})
    for cid, rcard in ref_turns.items():
        for atom in rcard.get("required_memory_atoms", []):
            src = atom.get("source", {})
            q = src.get("quote", "")
            if not q:
                continue
            if src.get("kind") == "initial_kb":
                key = src.get("key", "")
                if key and key in kb_lookup:
                    target = kb_lookup[key]
                else:
                    target = kb_str
                if q not in target:
                    quote_fail.append(f"{cid}/{atom.get('id')}: kb quote not found (key={key}): {q[:30]}")
            else:
                # message source: look up by source.case_id + message_id, NOT current cid
                src_cid = src.get("case_id", cid)
                mid = str(src.get("message_id", ""))
                target = msg_by.get((src_cid, mid))
                if target is None:
                    # fall back to index parsing
                    tmatch = next((t for t in ep["turns"] if t["case_id"] == src_cid), None)
                    if tmatch:
                        idx = int(mid) if mid.isdigit() else 0
                        if idx < len(tmatch["input"]["messages"]):
                            target = tmatch["input"]["messages"][idx].get("text", "")
                if target is None or q not in target:
                    quote_fail.append(f"{cid}/{atom.get('id')}: msg quote not found in {src_cid}/{mid}: {q[:30]}")
    c11 = (len(quote_fail)==0)
    res["checks"]["11_quote_strict"] = c11
    res["checks"]["11_quote_count"] = sum(len(rcard.get("required_memory_atoms",[])) for rcard in ref_turns.values())
    res["checks"]["11_quote_fail"] = len(quote_fail)
    if not c11: res["fails"].append(f"QUOTE_FAIL ({len(quote_fail)}): {quote_fail[:5]}")

    # 12. required_this_turn atom source earlier than current turn
    future_fail = []
    for cid, rcard in ref_turns.items():
        cur_turn_idx = int(cid.split("_T")[1])
        for atom in rcard.get("required_memory_atoms", []):
            src = atom.get("source", {})
            src_cid = src.get("case_id","")
            if src.get("kind") == "initial_kb":
                continue  # seed is always earlier
            if src_cid:
                try:
                    src_turn_idx = int(src_cid.split("_T")[1])
                except:
                    continue
                if src_turn_idx > cur_turn_idx:
                    future_fail.append(f"{cid}/{atom.get('id')}: source {src_cid} later than current")
    c12 = (len(future_fail)==0)
    res["checks"]["12_required_source_earlier"] = c12
    if not c12: res["fails"].append(f"FUTURE_SOURCE: {future_fail[:5]}")

    # 13. runner_episode.json hash == episode.json hash
    ep_hash = md5(os.path.join(d, "episode.json"))
    rn_hash = md5(os.path.join(d, "runner_episode.json"))
    c13 = (ep_hash == rn_hash)
    res["checks"]["13_runner_hash_match"] = c13
    if not c13: res["fails"].append(f"HASH_MISMATCH ep={ep_hash} runner={rn_hash}")

    # 14. audit.md 8 sections present
    missing_sec = [s for s in AUDIT_SECTIONS if s not in audit_md]
    c14 = (len(missing_sec)==0)
    res["checks"]["14_audit_md_8sec"] = c14
    if not c14: res["fails"].append(f"AUDIT_MD_MISSING_SECTIONS: {missing_sec}")

    # 15. scene domain uniqueness handled cross-story below
    res["checks"]["15_scene_domain"] = ex.get("domain","")
    res["checks"]["15_primary_topic"] = ex.get("primary_topic","")

    # 16. cross-turn backreference >= 2 (1 short + 1 long >=4 turn distance)
    # Hybrid detection: (a) memory-atom sources from earlier turns;
    # (b) narrative backref = a distinctive 4+ char phrase from an EARLIER turn's HER text
    #     that re-appears (verbatim) in the CURRENT turn's HER text, indicating callback.
    # Also corroborate with audit.md §2 backref declaration (containment, not sole gate).
    turn_texts = {}  # case_id -> full HER text (all HER messages joined)
    for t in ep["turns"]:
        her_txt = " ".join(msg_fields(m)[1] for m in t["input"]["messages"] if (msg_fields(m)[0] or "").lower() in ("her",))
        if not her_txt:
            # some schemas use role values like "HER"; recompute case-insensitively
            her_txt = " ".join(m.get("text","") for m in t["input"]["messages"])
        turn_texts[t["case_id"]] = her_txt

    atom_backrefs = []   # (cur, src, dist)
    for cid, rcard in ref_turns.items():
        cur_turn_idx = int(cid.split("_T")[1])
        for atom in rcard.get("required_memory_atoms", []):
            src = atom.get("source", {})
            src_cid = src.get("case_id","")
            if src.get("kind") == "initial_kb":
                continue
            if src_cid and src_cid != cid:
                try:
                    src_turn_idx = int(src_cid.split("_T")[1])
                except:
                    continue
                dist = cur_turn_idx - src_turn_idx
                if dist >= 1:
                    atom_backrefs.append((cid, src_cid, atom.get("id"), dist))

    # narrative backref: a distinctive 4-char CJK n-gram from an earlier turn verbatim in current turn.
    # Use 4-char sliding windows (not full phrases) to catch partial restatements like
    # "零出资零补偿" (current) matching "一二楼零分摊零补偿也零出资" (earlier).
    import re as _re
    def cjk_ngrams(text, n=4):
        runs = _re.findall(r'[\u4e00-\u9fff]{4,}', text)
        grams = set()
        for run in runs:
            for i in range(len(run) - n + 1):
                grams.add(run[i:i+n])
        return grams
    narrative_backrefs = []  # (cur, src, dist, phrase)
    cids_ordered = [t["case_id"] for t in ep["turns"]]
    # precompute ngrams per turn + global frequency
    ngrams_per = {c: cjk_ngrams(turn_texts[c]) for c in cids_ordered}
    from collections import Counter
    glob_freq = Counter()
    for c in cids_ordered:
        for g in ngrams_per[c]:
            glob_freq[g] += 1
    for i, cur in enumerate(cids_ordered):
        cur_grams = ngrams_per[cur]
        for j in range(i):
            src = cids_ordered[j]
            dist = i - j
            shared = cur_grams & ngrams_per[src]
            # distinctive = shared grams that appear in <=3 turns total (not boilerplate)
            distinctive = [g for g in shared if glob_freq[g] <= 3]
            if distinctive:
                narrative_backrefs.append((cur, src, dist, distinctive[0]))

    # dedupe narrative_backrefs by (cur, src) keeping max distance
    nb_seen = {}
    for cur, src, dist, ph in narrative_backrefs:
        key = (cur, src)
        if key not in nb_seen or dist > nb_seen[key][0]:
            nb_seen[key] = (dist, ph)
    narrative_dedup = [(cur, src, d, ph) for (cur,src),(d,ph) in nb_seen.items()]

    # combine: a backref "exists" for a (cur,src) pair if either atom or narrative
    all_backref_pairs = set()
    backref_dists = []
    for cur, src, aid, dist in atom_backrefs:
        all_backref_pairs.add((cur, src, dist))
        backref_dists.append(dist)
    for cur, src, dist, ph in narrative_dedup:
        all_backref_pairs.add((cur, src, dist))
        backref_dists.append(dist)
    short_count = len(all_backref_pairs)
    long_count = sum(1 for d in backref_dists if d >= 4)
    # also check audit.md §2 mentions backref terms (corroboration)
    audit_has_backref_decl = any(k in audit_md for k in ["回指","回扣","承接","长回指","短回指"])
    c16 = (short_count >= 2 and long_count >= 1)
    res["checks"]["16_backref_pairs"] = short_count
    res["checks"]["16_long_backref"] = long_count
    res["checks"]["16_atom_backrefs"] = len(atom_backrefs)
    res["checks"]["16_narrative_backrefs"] = len(narrative_dedup)
    res["checks"]["16_audit_md_decl_backref"] = audit_has_backref_decl
    res["checks"]["16_cross_turn_backref"] = c16
    if not c16:
        res["fails"].append(f"BACKREF pairs={short_count} long={long_count} (need >=2 pairs & >=1 dist>=4); atom={len(atom_backrefs)} narrative={len(narrative_dedup)}; audit_md_decl={audit_has_backref_decl}")

    # 17. cross-story name pollution: third-party pool names that appear as protagonist names
    # & collect third-party mentions for cross-story overlap analysis later
    # check protagonist names not in third_party pool
    c17 = (exp_her not in third_party and exp_me not in third_party)
    res["checks"]["17_proto_not_thirdparty"] = c17
    if not c17: res["fails"].append("PROTAGONIST_NAME_IN_THIRD_PARTY_POOL")

    # register third-party name mentions in episode visible text (for cross-story pollution)
    # we scan message text + initial_kb for any third_party pool name
    tp_mentions = set()
    scan_text = kb_str + "\n" + "\n".join(all_visible)
    for tpn in third_party:
        if tpn in scan_text:
            tp_mentions.add(tpn)
            cross_name_registry.setdefault(tpn, set()).add(eid)
    res["checks"]["17_third_party_mentions"] = sorted(tp_mentions)

    results[eid] = res

# 15. scene domain uniqueness across 30
domains = {eid: results[eid]["checks"].get("15_scene_domain","") for eid in EIDS}
domain_dup = {}
for eid, dm in domains.items():
    domain_dup.setdefault(dm, []).append(eid)
for dm, eids in domain_dup.items():
    if len(eids) > 1 and dm:
        # flag as warning, not auto-fail (domain string may repeat if sub-scene differs)
        for e in eids:
            results[e]["warns"].append(f"DOMAIN_DUP: '{dm}' shared by {eids}")

# 17b. cross-story name pollution: protagonist name appearing in >1 story
proto_pollution = {}
for nm, eids in cross_name_registry.items():
    if len(eids) > 1:
        # third-party pool names CAN appear across stories (allowed low-freq reuse)
        # but protagonist names (HER/ME) must be unique to one story
        is_proto = any(nm in (holdout_assign[e]["her"], holdout_assign[e]["me"]) for e in eids)
        proto_pollution[nm] = (sorted(eids), is_proto)

# output summary
print("="*70)
print("AUDITOR-HC2 E431-E460 AUDIT RESULTS")
print("="*70)
npass = 0
nfail = 0
for eid in EIDS:
    res = results[eid]
    status = "PASS" if (len(res["fails"])==0) else "FAIL"
    if status=="PASS": npass+=1
    else: nfail+=1
    checks = res["checks"]
    print(f"\n--- {eid} | {status} | topic={checks.get('15_primary_topic')} domain={checks.get('15_scene_domain')}")
    print(f"    her/me=... turns={checks.get('02_turns_12')} case_id={checks.get('03_case_id')} reply={checks.get('04_turn_type_reply')} gold={checks.get('05_gold_empty')}")
    print(f"    clock_mono={checks.get('06_clock_monotone')} ts_clock={checks.get('06b_ts_clock_ok')} may_decline={checks.get('07_may_decline_count')}(ge2={checks.get('07_may_decline_ge2')})")
    print(f"    intent={checks.get('08_intent_count')}(ok={checks.get('08_intent_2to4')}) note={checks.get('09_note_count')}(ok={checks.get('09_note_1to3')}) banned={checks.get('10_banned_zero')}")
    print(f"    quote_strict={checks.get('11_quote_strict')}({checks.get('11_quote_count')} atoms,{checks.get('11_quote_fail')} fail) req_source_earlier={checks.get('12_required_source_earlier')}")
    print(f"    runner_hash={checks.get('13_runner_hash_match')} audit8sec={checks.get('14_audit_md_8sec')} backref={checks.get('16_backref_count')}(long={checks.get('16_long_backref_count')},ok={checks.get('16_cross_turn_backref')})")
    print(f"    proto_not_tp={checks.get('17_proto_not_thirdparty')} tp_mentions={checks.get('17_third_party_mentions')}")
    if res["fails"]:
        for ff in res["fails"]:
            print(f"    FAIL: {ff}")
    if res["warns"]:
        for ww in res["warns"]:
            print(f"    WARN: {ww}")

print(f"\n{'='*70}")
print(f"SUMMARY: {npass} PASS + {nfail} FAIL / {len(EIDS)} total")
print(f"{'='*70}")

# domain dup report
print("\n--- DOMAIN DUPLICATION ---")
for dm, eids in sorted(domain_dup.items()):
    tag = "DUP" if len(eids)>1 else "UNIQ"
    print(f"  [{tag}] '{dm}': {eids}")

# cross-story name pollution
print("\n--- CROSS-STORY NAME REGISTRY (names in >1 story) ---")
for nm, (eids, is_proto) in sorted(proto_pollution.items()):
    tag = "PROTO-POLLUTION" if is_proto else "thirdparty-reuse(ok)"
    print(f"  [{tag}] '{nm}': {eids}")

# save machine-readable
out = {"pass": npass, "fail": nfail, "per_episode": {}}
for eid in EIDS:
    out["per_episode"][eid] = {
        "status": "PASS" if len(results[eid]["fails"])==0 else "FAIL",
        "fails": results[eid]["fails"],
        "warns": results[eid]["warns"],
        "checks": results[eid]["checks"],
    }
out["domain_dup"] = {dm: eids for dm, eids in domain_dup.items() if len(eids)>1}
out["cross_name_pollution"] = {nm: {"eids": eids, "is_protagonist": is_proto} for nm,(eids,is_proto) in proto_pollution.items()}
with open(r"D:\lovebrain-arena\_audit_hc2\hc2_results.json", "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print("\nMachine-readable saved to D:/lovebrain-arena/_audit_hc2/hc2_results.json")
