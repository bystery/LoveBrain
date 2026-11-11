# candidates/phase2_expr — 表达/选择策略候选
# 父版本 = candidates/phase1_retr_idf（检索 v2.2，已冻结）。
# 唯一主变量 = 表达/选择策略：
#   1) system prompt 追加 assets/system_prompt/expression.md（正文视角/经历出处/
#      他人动机/旧事主体 四规则 + 保留调侃暧昧与合法 no_reply）——检索、存储、
#      写回、编排、params 契约全部委托父版本，逐字节一致；
#   2) 生成后确定性词面审计（tools/expr_audit.py 四族检测器）落盘
#      sandbox/expr_trace.jsonl（评估专用，不入快照、不注入请求、不改写回复）。
#
# 冻结声明：检索实现经 base 机制模块直接复用（同一份 mechanism.py 字节），
# 本候选不含检索代码副本——版本漂移在结构上不可能。
# JSON 结构保障：仍未实施（登记为受控未做项，不因本候选宣称）。

import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))          # .../phase2_expr/mechanism
_ROOT = os.path.join(_HERE, "..", "..", "..")               # repo root
_SYS = os.path.join(_ROOT, "arena")
if _SYS not in sys.path:
    sys.path.insert(0, _SYS)

import lb_assembly          # noqa: E402

# ---- 加载父版本（检索 v2.2 冻结实现，唯一事实来源） ----
import importlib.util as _ilu
_BASE_PATH = os.path.join(_ROOT, "candidates", "phase1_retr_idf",
                          "mechanism", "mechanism.py")
_spec = _ilu.spec_from_file_location("phase1_retr_idf_base", _BASE_PATH)
base = _ilu.module_from_spec(_spec)
_spec.loader.exec_module(base)

_EXPR_ASSET = "system_prompt/expression.md"
_EXPR_TRACE_FILE = "expr_trace.jsonl"


# ---- 表达策略注入：作用域限定包装（v9 复核 §3.1 修复） ----
# 旧实现：模块加载即永久替换全局 lb_assembly.build_reply_prompts——同进程
# 第二次加载会把 wrapper 当原函数继续包装（表达块 1→2 份，实验强度漂移）。
# 新实现：run_turn 调用期间临时替换、finally 恢复原函数——无永久全局副作用、
# 不叠加、同进程先后运行的 A1/A2 臂不受影响。_expr_orig 标记防御性捕获真身，
# 即使未来有其他代码也做了包装，本候选也只包装最初原版。
_raw_build = getattr(lb_assembly.build_reply_prompts, "_expr_orig",
                     lb_assembly.build_reply_prompts)


def _build_with_expr(assets_root, case_input):
    out = _raw_build(assets_root, case_input)
    guard = lb_assembly.read_asset(assets_root, _EXPR_ASSET)
    if guard:
        out["system"] = out["system"] + "\n\n---\n\n" + guard
    return out


_build_with_expr._expr_orig = _raw_build


# ---- 生成后词面审计（评估专用；不改写回复、不参与控制流） ----

def _expr_trace_path(h):
    return os.path.join(h["sandbox"], _EXPR_TRACE_FILE)


def _append_expr_trace(h, record):
    try:
        with open(_expr_trace_path(h), "a", encoding="utf-8") as f:
            f.write(json.dumps(record, ensure_ascii=False, default=str) + "\n")
    except OSError:
        pass   # 审计落盘失败不阻断对话（与 retrieval_trace 同政策）


# ---- 生命周期：全部委托父版本；run_turn 包装审计 ----

def init(assets_root, episode_meta, gateway, sandbox):
    return base.init(assets_root, episode_meta, gateway, sandbox)


def set_memory_access(h, enabled):
    base.set_memory_access(h, enabled)


def run_turn(h, ctx, gw):
    # 表达块注入仅在本轮调用期间生效（§3.1 修复：无永久全局副作用）
    prev = lb_assembly.build_reply_prompts
    lb_assembly.build_reply_prompts = _build_with_expr
    try:
        out = base.run_turn(h, ctx, gw)   # 检索/写回/追踪与父版本逐字节一致
    finally:
        lb_assembly.build_reply_prompts = prev
    import protocol_public
    parsed = protocol_public.extract_json_block(out.get("reply") or "")
    findings, parse_ok = [], parsed is not None
    if parse_ok:
        import re as _re
        _FAMILIES = _load_families()
        resp = parsed.get("response")
        texts = []
        if isinstance(resp, dict):
            for k in ("recommended", "bad_boy", "playful", "warm"):
                v = resp.get(k)
                if isinstance(v, str) and v.strip():
                    texts.append(("response." + k, v))
        dirs = parsed.get("directions")
        if isinstance(dirs, list):
            for i, v in enumerate(dirs):
                if isinstance(v, str) and v.strip():
                    texts.append(("directions[%d]" % i, v))
        for field, text in texts:
            for fam, pat, label in _FAMILIES:
                m = _re.search(pat, text)
                if m:
                    findings.append({"family": fam, "field": field,
                                     "match": m.group(0), "label": label})
    _append_expr_trace(h, {"turn": h.get("turn"), "case_id":
                           (ctx.get("case_id") if isinstance(ctx, dict) else ""),
                           "parse_ok": parse_ok,
                           "expr_policy": "expression.md injected",
                           "findings": findings,
                           "n_findings": len(findings)})
    notes = out.get("notes", "") + " | expr_policy=on(expression.md+audit)"
    out["notes"] = notes
    return out


def _load_families():
    """四族检测模式（与 tools/expr_audit.py 同源，含 F4；两侧由回归
    test_expr_candidate.py 断言模式与标签一致）。词面命中=初筛标记非定罪。"""
    return [
        ("F1_advisor_voice", r"你就(说|跟她说|跟她讲|回她|回我?直接)",
         "建议或指令句式—收信人一致性需人工判读"),
        ("F1_advisor_voice", r"你(先|要|得|直接)?跟(她|他)商量", "指导用户与第三方交涉"),
        ("F1_advisor_voice", r"你打算怎么(跟|和|与)(她|他)说", "询问用户策略=军师视角"),
        ("F1_advisor_voice", r"让她(去|自己|先|别|直接|把)", "调度第三方的指令"),
        ("F1_advisor_voice", r"(定时|定期)(让|安排)", "对第三方的调度指令"),
        ("F1_advisor_voice", r"你(就|要|先)?(直接)?(说|讲)(不|就行|就完)", "教用户开口句式"),
        ("F2_me_fabrication", r"我(一般|通常|向来|从来|基本都是?|肯定)(?!说)", "已发生式习惯陈述"),
        ("F2_me_fabrication", r"我(上次|上回|之前)(?!说|提|跟你)", "过往经历回指"),
        ("F2_me_fabrication", r"我只?(吃|去|用|点)[^，。；]{0,8}(的|惯)", "排他式偏好陈述"),
        ("F2_me_fabrication", r"我就(换|撤|不去|走)", "习惯性行为陈述"),
        ("F3_motive_assertion", r"(她|他|室友)可(不管|不会管|哪管)", "第三方态度断言"),
        ("F3_motive_assertion", r"(她|他|室友)(就是|明明|肯定|显然)(是|知道|故意|想)", "动机/知情断言"),
        ("F3_motive_assertion", r"(是|属于)故意", "动机断言"),
        ("F3_motive_assertion", r"(她|他)没(说|提)过.{0,6}(还|却)", "把未说过的话放回对方口中"),
        ("F4_subject_manual_review", r"(之前|上次|上回|那次)[^，。；]{0,20}(她|它|自己)",
         "旧事件回指—主语需人工核对"),
    ]


def snapshot(h):
    # 与父版本一致（expr_trace.jsonl 与 retrieval_trace.jsonl 同政策：不入快照）
    return base.snapshot(h)


def restore(sandbox, snap):
    return base.restore(sandbox, snap)
