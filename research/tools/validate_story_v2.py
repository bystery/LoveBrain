#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Validate story-v2 stage1 packages without model calls.

Checks mechanical visibility, time, source-quote, reference-field and exposure
invariants. Any failure exits nonzero; the JSON report is written for audit.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUNNER_BASE = ROOT  # 可被 --root 覆盖（HOLDOUT 金库等仓库外根）
# Stage 配置默认=Stage1（不加参数时行为与旧版逐字一致）。泛化为显式参数：--stage/--turns/--count。
STAGE = "stage1"
BASE = os.path.join(ROOT, "benchmark", "stories_v2", STAGE)
MANIFEST = os.path.join(BASE, "STAGE1_MANIFEST.json")
OUT = os.path.join(BASE, "STAGE1_VALIDATION.json")
TURN_PER_STORY = 8
EXPECTED_COUNT = 8
REQUIRE_FROZEN_NULL = True   # stage1 冻结前 frozen_at 必为 null；后续 stage 可置 False

DIMS = {"goal", "factuality", "attribution", "time_state", "boundary",
        "memory_use", "interaction", "initiative", "emotion_relation"}
STORY_ORDER = {}  # story_id -> manifest 顺序，用于跨篇引用（pair A→B）来源解析


def sibling_episode_messages(scid):
    """跨篇引用：case_id 属于同批次更早 story 时，从该 story 的 episode.json 取消息。"""
    sid2 = str(scid).rsplit("_T", 1)[0]
    p = os.path.join(BASE, sid2, "episode.json")
    if not os.path.isfile(p):
        return []
    try:
        return inp_messages(load(p), scid)
    except Exception:
        return []
BANNED = ["考察", "观测点", "评分", "与上轮不同", "考点", "本题", "测试点",
          "evaluation point", "grading", "rubric"]
ALLOWED_KB = {"me", "her", "warmth", "style", "stage", "lessons"}


def sha(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def load(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def dt(s):
    return datetime.datetime.strptime(s, "%Y-%m-%d %H:%M")


def walk_strings(obj):
    if isinstance(obj, str):
        yield obj
    elif isinstance(obj, dict):
        for v in obj.values():
            for x in walk_strings(v):
                yield x
    elif isinstance(obj, list):
        for v in obj:
            for x in walk_strings(v):
                yield x


def validate_story(sid, story_entry, all_cases):
    errs = []
    warns = []
    sdir = os.path.join(BASE, sid)
    paths = {n: os.path.join(sdir, n) for n in
             ("episode.json", "reference.json", "timeline.json",
              "exposure.json", "audit.md")}
    for n, p in paths.items():
        if not os.path.isfile(p):
            errs.append("missing %s" % n)
    if errs:
        return errs, warns, {}

    ep = load(paths["episode.json"])
    ref = load(paths["reference.json"])
    tl = load(paths["timeline.json"])
    exp = load(paths["exposure.json"])

    if ep.get("episode_id") != sid:
        errs.append("episode_id mismatch: %r" % ep.get("episode_id"))
    if set(ep.keys()) != {"episode_id", "persona", "initial_kb", "turns"}:
        errs.append("episode top-level keys unexpected: %s" % sorted(ep.keys()))
    if not isinstance(ep.get("persona"), dict):
        errs.append("persona not dict")
    kb = ep.get("initial_kb")
    if not isinstance(kb, dict):
        errs.append("initial_kb not dict")
    else:
        extra = set(kb) - ALLOWED_KB
        if extra:
            errs.append("initial_kb keys outside allowed scalar profile: %s" % sorted(extra))
        for k, v in kb.items():
            if not isinstance(v, str):
                errs.append("initial_kb.%s is not scalar str: %r" % (k, type(v).__name__))
            elif len(v) > 1000:
                errs.append("initial_kb.%s too long" % k)

    turns = ep.get("turns") or []
    if len(turns) != TURN_PER_STORY:
        errs.append("turns != %d: %d" % (TURN_PER_STORY, len(turns)))
    cases = []
    prev_clock = None
    for i, t in enumerate(turns):
        cid = t.get("case_id")
        cases.append(cid)
        if cid in all_cases:
            errs.append("duplicate case_id across stories: %s" % cid)
        all_cases.add(cid)
        if t.get("turn_type") != "reply":
            errs.append("%s turn_type != reply" % cid)
        if t.get("gold") not in ({}, None):
            errs.append("%s has nonempty gold" % cid)
        inp = t.get("input") or {}
        if set(inp.keys()) - {"messages", "clock", "may_decline", "intent", "note"}:
            errs.append("%s unexpected input keys" % cid)
        if "note" in inp and inp["note"]:
            for b in BANNED:
                if b in inp["note"]:
                    errs.append("%s note contains evaluator word %r" % (cid, b))
        clock = dt(inp.get("clock"))
        if prev_clock and clock < prev_clock:
            errs.append("%s clock goes backwards" % cid)
        prev_clock = clock
        msgs = inp.get("messages") or []
        if not msgs:
            errs.append("%s empty messages" % cid)
        prev_ts = None
        for j, m in enumerate(msgs):
            if m.get("role") not in ("HER", "ME"):
                errs.append("%s m%d bad role %r" % (cid, j, m.get("role")))
            if not isinstance(m.get("text"), str) or not m["text"].strip():
                errs.append("%s m%d empty text" % (cid, j))
            if set(m.keys()) != {"role", "text", "ts"}:
                errs.append("%s m%d keys unexpected" % (cid, j))
            ts = dt(m.get("ts"))
            if ts > clock:
                errs.append("%s m%d ts after clock" % (cid, j))
            if prev_ts and ts < prev_ts:
                errs.append("%s m%d ts goes backwards" % (cid, j))
            prev_ts = ts
        for s in walk_strings(inp):
            for b in BANNED:
                if b in s:
                    errs.append("%s visible input contains evaluator word %r" % (cid, b))
        if inp.get("intent"):
            it = inp["intent"]
            if not isinstance(it.get("enabled"), bool):
                errs.append("%s intent.enabled not bool" % cid)
            if not isinstance(it.get("status"), str):
                errs.append("%s intent.status not str" % cid)
            if not isinstance(it.get("text"), str):
                errs.append("%s intent.text not str" % cid)

    # reference
    if ref.get("story_id") != sid:
        errs.append("reference.story_id mismatch")
    rturns = ref.get("turns") or {}
    if set(rturns.keys()) != set(cases):
        errs.append("reference case set != episode case set")
    case_index = {c: i for i, c in enumerate(cases)}
    for cid in cases:
        r = rturns.get(cid)
        if not isinstance(r, dict):
            errs.append("%s missing reference" % cid)
            continue
        required_fields = ["case_id", "current_action", "acceptable_actions",
                           "applicable_dimensions", "required_memory_atoms",
                           "private_guidance", "unknowns",
                           "protected_expressions",
                           "forbidden_factual_claims"]
        for f in required_fields:
            if f not in r:
                errs.append("%s reference missing field %s" % (cid, f))
        if r.get("case_id") != cid:
            errs.append("%s reference case_id mismatch" % cid)
        if not str(r.get("current_action") or "").strip():
            errs.append("%s empty current_action" % cid)
        acts = r.get("acceptable_actions") or []
        if len(acts) < 2:
            errs.append("%s fewer than 2 acceptable actions" % cid)
        if len(set(acts)) != len(acts):
            errs.append("%s duplicate acceptable actions" % cid)
        dims = set(r.get("applicable_dimensions") or [])
        if not dims <= DIMS:
            errs.append("%s unknown dimensions %s" % (cid, sorted(dims - DIMS)))
        if len(dims) < 3:
            errs.append("%s fewer than 3 applicable dimensions" % cid)
        if not str(r.get("private_guidance") or "").strip():
            errs.append("%s empty private_guidance" % cid)
        for f in ("unknowns", "protected_expressions", "forbidden_factual_claims"):
            if not isinstance(r.get(f), list):
                errs.append("%s %s not list" % (cid, f))
        # protected expressions must appear in a HER message of this turn
        her_text = "\n".join(m["text"] for m in inp_messages(ep, cid)
                             if m.get("role") == "HER")
        for p in r.get("protected_expressions") or []:
            if p not in her_text:
                errs.append("%s protected expression not in HER message: %r" % (cid, p))
        for atom in r.get("required_memory_atoms") or []:
            aid = atom.get("atom_id")
            for f in ("subject", "relation", "value", "valid_time", "status",
                      "negation", "source"):
                if f not in atom:
                    errs.append("%s atom %s missing %s" % (cid, aid, f))
            src = atom.get("source") or {}
            if src.get("kind") == "message":
                scid = src.get("case_id")
                if scid not in case_index:
                    # 跨篇引用：来源属于同批次更早 story（source story 必须先于当前 story 入场）
                    sid2 = str(scid).rsplit("_T", 1)[0]
                    if (sid2 != sid and sid2 in STORY_ORDER
                            and STORY_ORDER.get(sid, len(STORY_ORDER)) > STORY_ORDER[sid2]):
                        pass
                    else:
                        errs.append("%s atom %s unknown source case" % (cid, aid))
                        continue
                elif case_index[scid] > case_index[cid]:
                    errs.append("%s atom %s source is future" % (cid, aid))
                if atom.get("required_this_turn") and scid == cid:
                    errs.append("%s atom %s required but sourced from current turn" % (cid, aid))
                msgs = inp_messages(ep, scid)
                if not msgs:
                    msgs = sibling_episode_messages(scid)
                hit = [k for k, m in enumerate(msgs) if src.get("quote") in m.get("text", "")]
                if not hit:
                    errs.append("%s atom %s quote not in source %s" % (cid, aid, scid))
                elif src.get("message_id") != "m%d" % hit[0]:
                    errs.append("%s atom %s message_id mismatch" % (cid, aid))
            elif src.get("kind") == "initial_kb":
                key = src.get("key")
                if key not in kb:
                    errs.append("%s atom %s initial_kb key missing" % (cid, aid))
                elif src.get("quote") not in kb.get(key, ""):
                    errs.append("%s atom %s quote not in initial_kb" % (cid, aid))
            else:
                errs.append("%s atom %s unknown source kind" % (cid, aid))

    # timeline
    if tl.get("story_id") != sid:
        errs.append("timeline.story_id mismatch")
    if len(tl.get("turn_clocks") or []) != TURN_PER_STORY:
        errs.append("timeline turn_clocks != %d" % TURN_PER_STORY)
    if len(tl.get("message_index") or []) != sum(len(t["input"]["messages"]) for t in turns):
        errs.append("timeline message_index count mismatch")
    for m in tl.get("message_index") or []:
        if m.get("case_id") not in case_index:
            errs.append("timeline message case unknown")
            continue
        src_msgs = inp_messages(ep, m["case_id"])
        idx = int(m.get("message_id", "m-1").lstrip("m"))
        if idx < 0 or idx >= len(src_msgs):
            errs.append("timeline message id out of range")
        else:
            sm = src_msgs[idx]
            if (m.get("speaker"), m.get("quote"), m.get("ts")) != (
                    sm.get("role"), sm.get("text"), sm.get("ts")):
                errs.append("timeline message mismatch %s/%s" % (m.get("case_id"), m.get("message_id")))
    for ev in tl.get("events") or []:
        if ev.get("source_turn") not in case_index:
            errs.append("timeline event source_turn unknown: %r" % ev.get("source_turn"))
    for rv in tl.get("revisions") or []:
        if rv.get("case_id") not in case_index:
            errs.append("timeline revision case unknown")

    # exposure
    for f in ("author", "author_type", "readers", "frozen_at", "experiments_used",
              "dev_exposed", "real_user_material", "hashes", "runner_copy"):
        if f not in exp:
            errs.append("exposure missing %s" % f)
    if exp.get("real_user_material") is not False:
        errs.append("exposure real_user_material must be false")
    if not exp.get("readers"):
        errs.append("exposure readers empty")
    if exp.get("experiments_used") not in (None, []):
        errs.append("exposure experiments_used not empty")
    if REQUIRE_FROZEN_NULL:
        if exp.get("frozen_at") is not None:
            errs.append("exposure frozen_at should be null for %s" % STAGE)
    else:
        fa = exp.get("frozen_at")
        if fa is not None and not str(fa).strip():
            errs.append("exposure frozen_at present but empty")
    for n, h in (exp.get("hashes") or {}).items():
        p = paths.get(n)
        if not p or not os.path.isfile(p):
            errs.append("exposure hash target missing %s" % n)
        elif sha(p) != h:
            errs.append("exposure hash mismatch %s" % n)

    # audit
    audit = open(paths["audit.md"], encoding="utf-8").read()
    for sec in ("## 0. 性质与暴露", "## 1. 生活动机", "## 2. 连续性与回扣",
                "## 3. ME 历史与边界", "## 4. no_reply / 媒体局限",
                "## 5. 参考卡质量", "## 6. 已知风险", "## 7. 出口检查"):
        if sec not in audit:
            errs.append("audit missing section %s" % sec)

    # runner copy（runner_copy 为绝对路径时直接用；否则相对 ROOT/覆盖根解析）
    rc = exp.get("runner_copy", "")
    runner = rc if os.path.isabs(rc) else os.path.join(RUNNER_BASE, rc)
    if not os.path.isfile(runner):
        errs.append("runner copy missing")
    elif load(runner) != ep:
        errs.append("runner copy != canonical episode")

    # manifest hashes
    for n, h in (story_entry.get("files") or {}).items():
        p = paths.get(n)
        if not p or not os.path.isfile(p):
            errs.append("manifest hash target missing %s" % n)
        elif sha(p) != h:
            errs.append("manifest hash mismatch %s" % n)

    return errs, warns, {
        "story_id": sid,
        "turns": len(turns),
        "atoms": sum(len(r.get("required_memory_atoms") or []) for r in rturns.values()),
        "required_atoms": sum(1 for r in rturns.values()
                              for a in r.get("required_memory_atoms") or []
                              if a.get("required_this_turn")),
        "may_decline": [c for c in cases if rturns[c].get("may_decline")],
        "intents": [c for c in cases if rturns[c].get("intent")],
    }


def inp_messages(ep, cid):
    for t in ep["turns"]:
        if t["case_id"] == cid:
            return t["input"]["messages"]
    return []


def main():
    global BASE, MANIFEST, OUT, STAGE, TURN_PER_STORY, EXPECTED_COUNT, REQUIRE_FROZEN_NULL
    ap = argparse.ArgumentParser()
    ap.add_argument("--stage", default="stage1")
    ap.add_argument("--turns", type=int, default=8, help="每故事请求位数(Stage1=8, Stage2自然=12)")
    ap.add_argument("--count", type=int, default=8, help="故事数(Stage1=8)")
    ap.add_argument("--manifest-name", default="STAGE1_MANIFEST.json")
    ap.add_argument("--out-name", default="STAGE1_VALIDATION.json")
    ap.add_argument("--allow-frozen-at", action="store_true",
                    help="后续 stage 允许 exposure.frozen_at 非空(默认 Stage1 必须 null)")
    ap.add_argument("--root", default=None,
                    help="wave 根目录覆盖（默认 <repo>/benchmark/stories_v2；HOLDOUT 金库用）")
    a = ap.parse_args()
    STAGE = a.stage
    TURN_PER_STORY = a.turns
    EXPECTED_COUNT = a.count
    REQUIRE_FROZEN_NULL = not a.allow_frozen_at
    if a.root:
        BASE = os.path.join(a.root, STAGE)
        globals()["RUNNER_BASE"] = a.root
    else:
        BASE = os.path.join(ROOT, "benchmark", "stories_v2", STAGE)
    MANIFEST = os.path.join(BASE, a.manifest_name)
    OUT = os.path.join(BASE, a.out_name)
    if not os.path.isfile(MANIFEST):
        raise SystemExit("manifest missing: %s" % MANIFEST)
    man = load(MANIFEST)
    if man.get("count") != EXPECTED_COUNT or len(man.get("stories") or []) != EXPECTED_COUNT:
        raise SystemExit("manifest count != %d" % EXPECTED_COUNT)
    STORY_ORDER.clear()
    STORY_ORDER.update({e["story_id"]: i for i, e in enumerate(man["stories"])})
    all_cases = set()
    report = {"kind": "story_v2_%s_validation" % STAGE, "date": "2026-10-27",
              "turn_per_story": TURN_PER_STORY, "expected_count": EXPECTED_COUNT,
              "passed": True, "stories": [], "errors": []}
    for entry in man["stories"]:
        sid = entry["story_id"]
        errs, warns, stats = validate_story(sid, entry, all_cases)
        report["stories"].append({"story_id": sid, "errors": errs,
                                  "warnings": warns, "stats": stats})
        if errs:
            report["passed"] = False
            report["errors"].extend(["%s: %s" % (sid, e) for e in errs])
    expected_cases = EXPECTED_COUNT * TURN_PER_STORY
    if len(all_cases) != expected_cases:
        report["passed"] = False
        report["errors"].append("unique cases != %d: %d" % (expected_cases, len(all_cases)))
    report["unique_cases"] = len(all_cases)
    report["stories_passed"] = sum(1 for s in report["stories"] if not s["errors"])
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("stories=%d passed=%d unique_cases=%d errors=%d -> %s" % (
        len(report["stories"]), report["stories_passed"],
        report["unique_cases"], len(report["errors"]), OUT))
    for e in report["errors"][:20]:
        print("FAIL", e)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
