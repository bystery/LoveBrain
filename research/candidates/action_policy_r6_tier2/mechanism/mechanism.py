# candidates/action_policy_r5_tier/mechanism/mechanism.py
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

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)
_ROOT = os.path.dirname(_CAND)
_R2 = os.path.join(_ROOT, "action_policy_r2")  # staging trigger: candidates/action_policy_r2


def _load_r2():
    spec = importlib.util.spec_from_file_location(
        "apr2_mechanism_for_action_policy_r4_guard_final",
        os.path.join(_R2, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


r2 = _load_r2()
v6g = r2.v6g
v5 = r2.v5
base = r2.base

_FLAGS = {}
try:
    _FLAGS = (json.load(open(os.path.join(_CAND, "params.json"), encoding="utf-8")) or {})
except Exception:  # noqa: BLE001
    _FLAGS = {}
_FLAGS = _FLAGS.get("mechanism_flags") or {}

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
_TIER = '\n\n【依据分层·写正文前自检】落笔前把每条要写进正文的陈述归层：EVIDENCED＝本轮消息或上方记忆里能逐字核到依据，直接说；但记忆里的来源（科普、常识、听说的）只支撑“一般如此”，不许升格成“这次一定如此”，引用时保留出处口吻，当事人这件事没核到的部分照旧进 NEEDS_CHECK。PROPOSAL＝你的提议、偏好、玩笑或下一步，照常推进、可以留钩子，但不写成已经发生。NEEDS_CHECK＝尚未核实的外部状态或第三方规则（别人是否答应、机构条件、房源档期、口头条件是否已生效、你自己能否做到），必须写成“分支＋触发条件＋谁去核实”：说清两种走向各自成立的条件，并给出你或她会去做的那一步。数量、位置、成色、编号、时间点这类具体参数，输入里没有的禁止具名化，用“我帮你看看／你拍给我”接住。NEEDS_CHECK 不等于沉默，也不等于把事推回给对方——仍要给可执行的推进；没有依据就不写成既定，但也不必因此取消提议与钩子。'

if _FLAGS.get("hygiene_wording_fix", True):
    r2._HYGIENE = _FIXED_HYGIENE + (
        _TIER if _FLAGS.get("assertion_tiering", False) else "")

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
