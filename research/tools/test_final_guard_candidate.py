#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Offline contract tests for action_policy_r4_guard_final (0 API calls).

Covers:
  T1 module loads through the same importlib path as the runner.
  T2 r2's ambiguous brace sentence is replaced; standard-closure wording appears.
  T3 clean raw reply is returned byte-identically (no formatting rewrite).
  T4 premature top-level brace (AK02_T01) is repaired; analysis survives.
  T5 directions overflow is normalized to 4 and the extra slot is preserved.
  T6 C3/E008 subject counterexample is replaced by a role-tagged quote.
  T7 update with unsupported relationship subject is dropped and logged.
  T8 run_turn wrapper passes the guard gateway and appends notes.
  T9 assets present and non-empty; no model call is made by this test.
"""
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAND = os.path.join(ROOT, "candidates", "action_policy_r4_guard_final")


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main():
    fails = []
    mech = load(os.path.join(CAND, "mechanism", "mechanism.py"), "apr4_test")
    cg = load(os.path.join(CAND, "mechanism", "contract_guard.py"), "apr4_cg_test")

    # T1
    if not getattr(mech, "r2", None):
        fails.append("T1 r2 not loaded")

    # T2
    SIG = dict(hint="收尾", closing=True, refusal=False, busy=False, defer=False,
               positive=False, ambiguous=False, last_her="先这样吧，我去忙了",
               may_decline=True)
    GS = {"effective": True, "closed": False, "status": "ACTIVE", "text": "推进"}
    block = mech.v6g._action_block(SIG, mech.v6g._capabilities(SIG, True), GS)
    if "按标准 JSON 规则闭合各层" not in block:
        fails.append("T2 corrected hygiene missing")
    if "右花括号只能在 analysis 写完之后出现一次" in block:
        fails.append("T2 old false brace sentence still present")

    # T3
    clean = json.dumps({"response": {"recommended": "好", "bad_boy": "好",
                                     "playful": "好", "warm": "好"},
                        "directions": ["a", "b", "c", "d"],
                        "analysis": {"facts": [], "updates": []}},
                       ensure_ascii=False, indent=1)
    fixed, meta, obj = cg.repair_and_guard(clean, "", {})
    if fixed != clean or meta["changed"]:
        fails.append("T3 clean raw was rewritten")

    # T4
    ak02 = os.path.join(ROOT, "logs/p08_s2_live_20261028/candidate_AK02/out/"
                              "turn_01_AK02_T01/reply_raw.txt")
    raw = open(ak02, encoding="utf-8").read()
    fixed, meta, obj = cg.repair_and_guard(raw, "", {})
    if not meta.get("raw_parse_ok") or not isinstance((obj or {}).get("analysis"), dict):
        fails.append("T4 premature brace not repaired")
    if len((obj or {}).get("directions") or []) != 4:
        fails.append("T4 directions not 4 after repair")

    # T5
    five = json.dumps({"response": {"recommended": "好", "bad_boy": "好",
                                    "playful": "好", "warm": "好"},
                       "directions": ["a", "b", "c", "d", "e"],
                       "analysis": {"facts": [], "updates": []}},
                      ensure_ascii=False)
    fixed, meta, obj = cg.repair_and_guard(five, "", {})
    if len((obj or {}).get("directions") or []) != 4:
        fails.append("T5 directions overflow not normalized")
    if meta.get("extra_directions_preserved") != ["e"]:
        fails.append("T5 extra direction not preserved: %r"
                     % (meta.get("extra_directions_preserved"),))

    # T6
    raw_obj = {"response": {"recommended": "好", "bad_boy": "好",
                            "playful": "好", "warm": "好"},
               "directions": ["a", "b", "c", "d"],
               "analysis": {"facts": [{
                   "text": "用户与她是以前同事介绍认识的，不是读书会",
                   "subject": "SHARED",
                   "quote": "不是读书会 是以前同事介绍 你问这个干嘛",
                   "src_ids": ["m1"]}], "updates": []}}
    fixed, meta, obj = cg.repair_and_guard(
        json.dumps(raw_obj, ensure_ascii=False), "",
        {"m0": "HER", "m1": "ME"})
    fact = obj["analysis"]["facts"][0]
    if fact.get("subject") != "ME" or not fact.get("text", "").startswith("我说："):
        fails.append("T6 subject counterexample not replaced: %r" % (fact,))

    # T7
    raw_obj2 = json.loads(json.dumps(raw_obj))
    raw_obj2["analysis"] = {"facts": [], "updates": [{
        "fact_id": "f_0001", "action": "correct",
        "new_text": "我和她是同事介绍认识的",
        "quote": "不是读书会 是以前同事介绍",
        "src_ids": ["m1"]}]}
    fixed, meta, obj = cg.repair_and_guard(
        json.dumps(raw_obj2, ensure_ascii=False), "",
        {"m0": "HER", "m1": "ME"})
    if obj["analysis"]["updates"]:
        fails.append("T7 unsupported relationship update not dropped")
    if not any("relation_update_dropped" in str(x) for x in meta.get("subject_guard") or []):
        fails.append("T7 update drop not logged")

    # T8
    class GW:
        def __init__(self, raw):
            self.raw = raw

        def generate(self, *a, **k):
            return {"text": self.raw}

    def fake_parent(h, ctx, gw):
        r = gw.generate("s", "u")
        return {"reply": r["text"], "notes": "parent"}

    mech._parent_run_turn = fake_parent
    ctx = {"input": {"messages": [
        {"role": "HER", "text": "你上次说认识小林是通过读书会？"},
        {"role": "ME", "text": "不是读书会 是以前同事介绍 你问这个干嘛"}]}}
    out = mech.run_turn({}, ctx, GW(json.dumps(raw_obj, ensure_ascii=False)))
    if "contract_guard=" not in (out.get("notes") or ""):
        fails.append("T8 guard notes missing")
    if len(json.loads(out["reply"]).get("directions") or []) != 4:
        fails.append("T8 reply directions not 4")

    # T9
    for fn in ("core.md", "format.md", "naturalness_check.md", "redline.md"):
        p = os.path.join(CAND, "assets", "system_prompt", fn)
        if not os.path.exists(p) or not open(p, encoding="utf-8").read().strip():
            fails.append("T9 asset missing/empty: %s" % fn)

    if fails:
        print("FAIL %d:" % len(fails))
        for f in fails:
            print("  -", f)
        return 1
    print("FINAL_GUARD_PASS 9/9 (load, hygiene wording, clean passthrough, "
          "premature brace, directions overflow, subject replacement, "
          "update drop, run_turn wiring, assets)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
