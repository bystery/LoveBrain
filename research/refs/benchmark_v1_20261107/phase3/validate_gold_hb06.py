"""Gold annotation validator for HOLDOUT T04 (E451-E460).
Self-check: quote strict verify, protected_expressions verbatim in HER msg,
required atoms sourced earlier, may_decline consistency, 8-section audit,
affirm_markers verbatim in episode corpus.

Usage: python -I validate_gold_hb06.py E451
       python -I validate_gold_hb06.py ALL
"""
import json, sys, os, re, hashlib

VAULT = r"D:/lovebrain-arena_holdout_vault/wave6_holdout"
AUDIT_SECTIONS = [
    "## 0. 性质", "## 1. 生成信息", "## 2. 连续性",
    "## 3. ME/HER 状态", "## 4. no_reply / 媒体局限",
    "## 5. 已知", "## 6. 参与者注释", "## 7. 出题痕迹检查",
]

def load(eid):
    base = os.path.join(VAULT, eid)
    ep = json.load(open(os.path.join(base, "episode.json"), encoding="utf-8"))
    try:
        ref = json.load(open(os.path.join(base, "reference.json"), encoding="utf-8"))
    except FileNotFoundError:
        ref = None
    try:
        audit = open(os.path.join(base, "audit.md"), encoding="utf-8").read()
    except FileNotFoundError:
        audit = None
    return ep, ref, audit

def turn_msgs(ep, case_id):
    for t in ep["turns"]:
        if t["case_id"] == case_id:
            return t["input"]["messages"]
    return None

def corpus(ep):
    """all message texts joined per-turn and overall."""
    per_turn = {}
    all_texts = []
    for t in ep["turns"]:
        texts = [m["text"] for m in t["input"]["messages"]]
        per_turn[t["case_id"]] = texts
        all_texts.extend(texts)
    return per_turn, all_texts

def her_text(ep, case_id):
    msgs = turn_msgs(ep, case_id)
    if msgs is None:
        return ""
    return "".join(m["text"] for m in msgs if m.get("role", m.get("sender","")).upper() == "HER")

def validate(eid):
    print(f"\n========== {eid} ==========")
    ep, ref, audit = load(eid)
    errors = []
    warns = []
    if ref is None:
        print(f"  [SKIP] reference.json missing")
        return [f"{eid}: reference.json missing"], []
    per_turn, all_texts = corpus(ep)
    full_corpus = "\n".join(all_texts)
    ep_cases = {t["case_id"] for t in ep["turns"]}
    ref_turns = ref.get("turns", {})
    ref_cases = set(ref_turns.keys())
    # case set match
    if ep_cases != ref_cases:
        missing = ep_cases - ref_cases
        extra = ref_cases - ep_cases
        if missing:
            errors.append(f"{eid}: ref missing cases {missing}")
        if extra:
            errors.append(f"{eid}: ref extra cases {extra}")
    atom_count = 0
    required_count = 0
    quote_strict_pass = 0
    quote_strict_fail = 0
    prot_pass = 0
    prot_fail = 0
    marker_violations = 0
    for case_id, card in ref_turns.items():
        cur_turn_num = int(case_id.split("_T")[1])
        msgs = per_turn.get(case_id, [])
        her_msg = her_text(ep, case_id)
        # may_decline consistency
        ep_turn = next((t for t in ep["turns"] if t["case_id"] == case_id), None)
        ep_md = ep_turn["input"].get("may_decline", False) if ep_turn else None
        ref_md = card.get("may_decline")
        if ep_md is not None and ref_md is not None and ep_md != ref_md:
            errors.append(f"{eid}/{case_id}: may_decline mismatch ep={ep_md} ref={ref_md}")
        # protected_expressions verbatim in HER msg
        for pe in card.get("protected_expressions", []):
            if pe in her_msg:
                prot_pass += 1
            else:
                prot_fail += 1
                errors.append(f"{eid}/{case_id}: protected_expression NOT in HER msg: {pe[:30]}")
        # atoms
        for atom in card.get("required_memory_atoms", []):
            atom_count += 1
            src = atom.get("source", {})
            src_kind = src.get("kind", "message")
            src_case = src.get("case_id", case_id)
            quote = src.get("quote", "")
            # For initial_kb sourced atoms, check quote against KB text, skip message strict
            if src_kind == "initial_kb":
                # Build KB corpus from initial_kb me + her values (string or list-of-dicts)
                kb_texts = []
                for side in ("me", "her"):
                    val = ep.get("initial_kb", {}).get(side)
                    if isinstance(val, str):
                        kb_texts.append(val)
                    elif isinstance(val, list):
                        for item in val:
                            if isinstance(item, dict):
                                kb_texts.append(item.get("value", ""))
                            elif isinstance(item, str):
                                kb_texts.append(item)
                # warmth/style/lessons are also KB text
                for k in ("warmth", "style", "lessons"):
                    v = ep.get("initial_kb", {}).get(k)
                    if v:
                        kb_texts.append(v)
                kb_corpus = "\n".join(kb_texts)
                # also check persona traits/boundaries
                for side in ("her", "me"):
                    for item in ep.get("persona", {}).get(side, {}).get("traits", []):
                        kb_texts.append(item)
                    for item in ep.get("persona", {}).get(side, {}).get("boundaries", []):
                        kb_texts.append(item)
                kb_corpus = "\n".join(kb_texts)
                if quote and quote in kb_corpus:
                    quote_strict_pass += 1
                else:
                    # KB quote not found in KB corpus — try full episode corpus as fallback
                    if quote and quote in full_corpus:
                        quote_strict_pass += 1
                    else:
                        quote_strict_fail += 1
                        errors.append(f"{eid}/{case_id}/{atom['id']}: kb_quote NOT in KB corpus: {quote[:40]}")
                # required check: KB atoms are always from "earlier" (seed), so always ok
                req = atom.get("required_this_turn", False)
                if req:
                    required_count += 1
                    # KB source is seed (before all turns), so always earlier — no error
            else:
                src_turn_num = int(src_case.split("_T")[1]) if "_T" in src_case else cur_turn_num
                # required_this_turn rule: true iff sourced earlier
                req = atom.get("required_this_turn", False)
                if req:
                    required_count += 1
                    if src_turn_num >= cur_turn_num:
                        errors.append(f"{eid}/{case_id}/{atom['id']}: required but sourced from {src_case} (>= current)")
                # quote strict in source turn messages
                src_msgs = per_turn.get(src_case, [])
                src_text = "".join(src_msgs)
                if quote and quote in src_text:
                    quote_strict_pass += 1
                else:
                    quote_strict_fail += 1
                    errors.append(f"{eid}/{case_id}/{atom['id']}: quote NOT strict in {src_case}: {quote[:40]}")
            # affirm_markers verbatim in corpus
            am = atom.get("affirm_markers", {})
            item_markers = am.get("item_markers", []) if isinstance(am, dict) else []
            group_markers = am.get("group_markers", []) if isinstance(am, dict) else []
            for grp in group_markers:
                # group is a list of phrases; each phrase should be findable verbatim somewhere
                for ph in grp:
                    if ph and ph not in full_corpus:
                        marker_violations += 1
                        errors.append(f"{eid}/{case_id}/{atom['id']}: group_marker '{ph}' not verbatim in corpus")
            for im in item_markers:
                if im and im not in full_corpus:
                    marker_violations += 1
                    errors.append(f"{eid}/{case_id}/{atom['id']}: item_marker '{im}' not verbatim in corpus")
    # audit 8 sections
    if audit is None:
        errors.append(f"{eid}: audit.md missing")
    else:
        for sec in AUDIT_SECTIONS:
            if sec not in audit:
                errors.append(f"{eid}: audit missing section '{sec}'")
        # exactly 8 ## headers
        headers = re.findall(r"^## ", audit, re.MULTILINE)
        if len(headers) != 8:
            warns.append(f"{eid}: audit has {len(headers)} ## headers (expected 8)")
    # runner_episode hash check
    re_path = os.path.join(VAULT, eid, "runner_episode.json")
    ep_path = os.path.join(VAULT, eid, "episode.json")
    if os.path.exists(re_path) and os.path.exists(ep_path):
        h1 = hashlib.sha256(open(re_path, "rb").read()).hexdigest()
        h2 = hashlib.sha256(open(ep_path, "rb").read()).hexdigest()
        if h1 != h2:
            errors.append(f"{eid}: runner_episode.json hash != episode.json hash")
        else:
            print(f"  runner hash MATCH")
    else:
        if not os.path.exists(re_path):
            errors.append(f"{eid}: runner_episode.json missing")
    print(f"  atoms={atom_count} required={required_count} quote_strict={quote_strict_pass}/{quote_strict_pass+quote_strict_fail} prot={prot_pass}/{prot_pass+prot_fail} marker_viol={marker_violations}")
    for e in errors:
        print(f"  [ERR] {e}")
    for w in warns:
        print(f"  [warn] {w}")
    return errors, warns

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("usage: python -I validate_gold_hb06.py E451 | ALL")
        sys.exit(2)
    arg = sys.argv[1]
    if arg == "ALL":
        ids = [f"E{460+i}" for i in range(1,11)]
    else:
        ids = [arg]
    all_err = []
    all_warn = []
    for eid in ids:
        e, w = validate(eid)
        all_err.extend(e)
        all_warn.extend(w)
    print(f"\n===== TOTAL errors={len(all_err)} warns={len(all_warn)} =====")
    if all_err:
        sys.exit(1)
