# candidates/action_policy_r7_supersede/mechanism/mechanism.py
#
# Final evidence-backed candidate, 2026-10-29.
# Parent: action_policy_r2 (runtime closure; not copied), which itself forwards
# to action_policy_r1 -> v6_goal_context -> ... -> reference_product_v1.
#
# Why this candidate exists (F0/F2):
#   - The three "over-silence" cells that motivated r3/FIX-9 are not errors:
#     D01_T08 ends with HER's explicit "不聊了我躺了"; D02_T06/D02_T08 end with
#     an already-sent ME message and no new HER turn. r2's no_reply is supported.
#     r3 is therefore not selected; its FIX-9 premise is retracted in
#     JUDGMENT_ERRATA_20261029.json.
#   - r2's _HYGIENE text says "右花括号只能在 analysis 写完之后出现一次", which is
#     wrong for nested JSON (response/directions must close before analysis). This
#     candidate replaces only that sentence with a standard-closure description.
#   - Two real protocol families still reproduce in paid answers: directions has
#     5 slots (6 cells across batches) and AK02_T01 closes the top-level object
#     before analysis. A deterministic guard reuses tools/output_repair.py (vendored)
#     BEFORE the parent parses analysis, so memory write-back sees the repaired
#     analysis. Model raw text remains in calls.json (ModelGateway logs it before
#     the adapter returns). The guard also preserves every extra/alias value in
#     mechanism_notes.txt.
#   - A conservative evidence-subject guard prevents a model paraphrase from
#     changing who a fact is about: relationship claims from a single message
#     without both-party support are replaced by a role-tagged verbatim quote, or
#     the correction is dropped. See C3/E008 counterexample.
#
# Flags (params.json): hygiene_wording_fix, contract_guard_fix, subject_evidence_fix.
# Turning all three off reproduces r2's behavior except for the thin wrapper.
import importlib.util
import json
import os
import re

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)
_ROOT = os.path.dirname(_CAND)
_R2 = os.path.join(_ROOT, "action_policy_r2")  # staging trigger: candidates/action_policy_r2


def _load_r2():
    spec = importlib.util.spec_from_file_location(
        "apr2_mechanism_for_action_policy_r7_supersede",
        os.path.join(_R2, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


r2 = _load_r2()

# --- W05-03: supersession pass (engine-side correction downgrade) ---
r1mod = getattr(r2, "apr1", None)


_SUPERSEDE_CUES = ("实际是", "其实", "看错", "看岔", "改成", "更正", "换成", "改为",
                   "延期", "延至", "作废", "改到", "改为", "不对", "写错", "记错")


_PUNCT_SET = set(chr(65292) + chr(12290) + chr(65281) + chr(65311) + chr(12289)
                 + chr(65307) + chr(65306) + chr(8220) + chr(8221) + chr(12300)
                 + chr(12301) + chr(65288) + chr(65289) + chr(40) + chr(41)
                 + chr(12298) + chr(12299) + chr(60) + chr(62) + chr(32) + chr(9)
                 + chr(10) + chr(13) + chr(45) + chr(8212) + chr(8230) + chr(183) + chr(43))


def _norm_cn(t):
    return "".join(ch for ch in (t or "") if ch not in _PUNCT_SET)


def _bigram_ov(a, b):
    an, bn = _norm_cn(a), _norm_cn(b)
    if len(an) < 2 or len(bn) < 2:
        return 0.0
    ag = {an[i:i + 2] for i in range(len(an) - 1)}
    bg = {bn[i:i + 2] for i in range(len(bn) - 1)}
    return len(ag & bg) / max(1, len(ag))


def _digits(t):
    return set(re.findall(r"\d+(?:\.\d+)?", re.sub(r"\s+", "", t or "")))


def _digit_conflict(old_t, new_t):
    # 真更正会改值：旧值数字集不完全包含于新值。互补场景（链条规格=轮胎规格）数字全同 → 非矛盾。
    od, nd = _digits(old_t), _digits(new_t)
    if not od:
        return False
    return not od.issubset(nd)


def _supersede_pass(h, clock):
    """Newly confirmed reported facts that contradict older confirmed reported
    facts (same subject, bigram overlap, correction cue) downgrade the old fact
    to superseded. Seeds (kbin:/persona:) are never downgraded. Logged in updates."""
    mem = h.get("mem") or {}
    facts = mem.get("facts") or {}
    if not facts:
        return 0
    # newest-first confirmed reported facts from this turn
    neweds = [f for f in facts.values()
              if f.get("status") == "confirmed" and f.get("origin") == "reported"
              and (f.get("ts") or "") == (clock or "")]
    n = 0
    for nf in neweds:
        nt = nf.get("text") or ""
        if not any(c in nt for c in _SUPERSEDE_CUES):
            continue
        for of in facts.values():
            if of is nf or of.get("status") != "confirmed" or of.get("origin") != "reported":
                continue
            if (of.get("ts") or "") >= (nf.get("ts") or ""):
                continue
            ov = _bigram_ov(nt, of.get("text") or "")
            same_subject = (nf.get("subject") == of.get("subject")
                            and (of.get("subject") or "UNKNOWN") != "UNKNOWN")
            if not ((ov >= 0.42) or (ov >= 0.30 and same_subject)):
                continue
            if not _digit_conflict(of.get("text") or "", nt):
                continue
            if r1mod is not None and hasattr(r1mod, "_archive_version"):
                r1mod._archive_version(of, of.get("status"), of.get("text"), clock,
                                       reason="auto_supersede", uids=list(nf.get("uids") or []),
                                       quote=(nf.get("quotes") or [""])[-1] if nf.get("quotes") else "")
            of["status"] = "superseded"
            of["superseded_by"] = nf.get("fact_id") or ""
            mem.setdefault("updates", []).append({
                "fact_id": of.get("fact_id"), "action": "auto_supersede",
                "text": nf.get("text"), "old_text": of.get("text"), "ts": clock})
            n += 1
            if len(mem.get("updates") or []) > 100:
                del mem["updates"][:-100]
            break  # one old fact per new fact
    return n


_FLAGS = {}
try:
    _FLAGS = (json.load(open(os.path.join(_CAND, "params.json"), encoding="utf-8")) or {})
except Exception:  # noqa: BLE001
    _FLAGS = {}
_FLAGS = _FLAGS.get("mechanism_flags") or {}

if r1mod is not None and _FLAGS.get("supersede_contradiction"):
    _orig_merge = r1mod._merge_facts

    def _merge_facts_sup(h, analysis, cur_uids, cur_texts, alias_map, clock):
        res = _orig_merge(h, analysis, cur_uids, cur_texts, alias_map, clock)
        try:
            _supersede_pass(h, clock)
        except Exception:
            pass
        return res

    r1mod._merge_facts = _merge_facts_sup

v6g = r2.v6g
v5 = r2.v5
base = r2.base

# ---------------------------------------------------------------------------
# 1) Correct the ambiguous brace sentence in r2's injected hygiene block.
#    r2._r2_action_block_hy looks up r2._HYGIENE at call time, so replacing the
#    module global is enough; no r2 file is modified.
# ---------------------------------------------------------------------------
_FIXED_HYGIENE = (
    "\n\n【单 JSON 硬约束】① 整条回复只有一个 JSON 对象；按标准 JSON 规则闭合各层："
    "response、directions 等内层对象在 analysis 之前按需闭合，最外层右花括号只在整条对象末尾出现一次；"
    "② response 的四个键名逐字照抄 recommended/bad_boy/playful/warm，键名里不得有不可见字符、空格或改写；"
    "③ directions 固定四槽（跟进/展开/表达/转向），每槽一句可发正文或 null，"
    "不多给第五槽——想多说的内容并进对应槽或删除；④ 除 action/decline_reason/"
    "response/directions/analysis 外不写任何顶层键。"
)
if _FLAGS.get("hygiene_wording_fix", True):
    r2._HYGIENE = _FIXED_HYGIENE

# ---------------------------------------------------------------------------
# 2) Deterministic output guard + subject-evidence guard.
# ---------------------------------------------------------------------------
_guard = None
if _FLAGS.get("contract_guard_fix", True) or _FLAGS.get("subject_evidence_fix", True):
    _spec = importlib.util.spec_from_file_location(
        "apr4_contract_guard", os.path.join(_HERE, "contract_guard.py"))
    _guard = importlib.util.module_from_spec(_spec)
    _spec.loader.exec_module(_guard)

if _guard is not None:
    _parent_run_turn = r2.run_turn

    def run_turn(h, ctx, gw):  # noqa: F811
        inp = (ctx or {}).get("input") or {}
        msgs = inp.get("messages") or []
        roles = {}
        for i, m in enumerate(msgs):
            if isinstance(m, dict):
                roles["m%d" % i] = "HER" if m.get("role") == "HER" else "ME"
        # The parent builds the prompt internally; parse roles from the message
        # list directly (m0.. in the same order as <chat>). user_prompt is only
        # used as a fallback by contract_guard when roles are not supplied.
        sink = []
        guard_gw = _guard.GuardGateway(gw, "", roles, sink)
        out = _parent_run_turn(h, ctx, guard_gw)
        if _FLAGS.get("mem_dump"):
            try:
                _mem = h.get("mem") or {}
                _df = {}
                for _k, _f in (_mem.get("facts") or {}).items():
                    _df[_k] = {"t": (_f.get("text") or "")[:80], "st": _f.get("status"), "ts": _f.get("ts"), "rel": _f.get("relation")}
                _du = [{"a": u.get("action"), "fid": u.get("fact_id"), "t": (u.get("text") or "")[:60], "old": (u.get("old_text") or "")[:60]} for u in (_mem.get("updates") or [])]
                _md = json.dumps({"n_f": len(_df), "facts": _df, "updates": _du}, ensure_ascii=False, separators=(",", ":"))
                out = dict(out)
                out["notes"] = (out.get("notes") or "") + " memdump=" + _md
            except Exception:
                pass
        if sink:
            out = dict(out)
            extra = " contract_guard=" + json.dumps(sink, ensure_ascii=False)
            out["notes"] = (out.get("notes") or "") + extra
            out["contract_guard"] = sink
        return out
else:
    run_turn = r2.run_turn

# ---------------------------------------------------------------------------
# 3) Forward the rest of the mechanism interface unchanged.
# ---------------------------------------------------------------------------
init = r2.init
snapshot = r2.snapshot
restore = r2.restore
set_memory_access = r2.set_memory_access
build_system_prompt = r2.build_system_prompt
