# -*- coding: utf-8 -*-
"""tools/build_review_worksheet.py — E：从真实答卷生成"可评审工作表"。

对齐 NEXT_MAINLINE_BATCH §E：
  列 = case/arm/实际run/harness/输入散列/输出散列/槽位/原文/事实证据/语义标签/
       互动质量/严重错误/理由；
  - 程序分与互动分【分开】：program_failed_checks 来自 scoring.json（机器），
    语义/互动质量留人评（worker 初审 + 独立复核），**未评审一律留空，绝不预填 pass**；
  - 身份列从真实运行快照取（run_id / case_input_sha256 / raw_reply_sha256 /
    harness_identity / authorization_id / batch_id / attempt 事件），不从计划拷标签；
  - 用户口味票列(pref_vote)留空给用户，worker 不代投；
  - 事实证据(evidence)来自评估侧评审卡 families/evidence，仅登记，不改运行时可见性。

交互维度（仅当当前目标适用才评推进/钩子）：intent_execution 意图执行 /
  continuity 承接 / initiative 主动入口 / tone_teasing 情绪·调侃分寸 /
  usability 自然可用性 / advancement 推进·钩子(适用才评)。合法不回与短句非天然低分。

用法：
  python tools/build_review_worksheet.py --exam <exam目录> --cards <评审卡.json> \
      --out <worksheet.json> [--planned <INTERLEAVE_PLAN.json>]
  --planned 给定时，为该批未跑槽位生成"待实跑"占位行（输出/程序分留空，仍不预填 pass）。
"""
import argparse
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))

DIMS = ["intent_execution", "continuity", "initiative", "tone_teasing",
        "usability", "advancement_if_applicable"]

# 交付失败（格式/解析）——这些不是"正确不回"，是没产出可评文本，必须进分母
PARSE_FAIL_REASONS = {"empty_reply", "no_json_start", "unterminated_json",
                      "json_parse_fail", "response_not_dict"}


def _delivery_status(reason, has_slots):
    """把每轮分成【交付失败】与【可能的正确动作】两类，绝不把没行当成没错误。
    - format_fail：JSON/结构解析失败（交付失败，按 veto 计，仍需留底账）
    - valid_no_reply：JSON 合法但无任何可发文本（合法不回或空输出，人评判定）
    - has_text：抽出了可评文本槽
    """
    if reason in PARSE_FAIL_REASONS:
        return "format_fail"
    if has_slots:
        return "has_text"
    return "valid_no_reply"


def _sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def _load(path, name):
    import importlib.util
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _slots_of(raw_text):
    """复用分层评分的槽位抽取（primary + backups），拿真实原文与散列。"""
    layer = _load(os.path.join(ROOT, "tools", "exam_layered_score.py"), "ws_layer")
    primary, backups, reason = layer._extract_slots(raw_text or "")
    rows = []
    if primary:
        rows.append(("recommended", primary, _sha(primary)))
    for slot_name, slot_text in (backups or []):
        rows.append((slot_name, slot_text, _sha(slot_text)))
    return rows, reason


def worksheet_from_exam(exam_dir, cards_by_case, arm_label):
    idx_path = os.path.join(exam_dir, "EXAM_INDEX.json")
    idx = json.load(open(idx_path, encoding="utf-8")) if os.path.isfile(idx_path) else {}
    import glob
    rows = []
    for d in sorted(glob.glob(os.path.join(exam_dir, "E*", "turn_*"))):
        mf_path = os.path.join(d, "manifest.json")
        if not os.path.isfile(mf_path):
            continue
        mf = json.load(open(mf_path, encoding="utf-8"))
        sc_path = os.path.join(d, "scoring.json")
        sc = json.load(open(sc_path, encoding="utf-8")) if os.path.isfile(sc_path) else {}
        raw = ""
        calls_p = os.path.join(d, "calls.json")
        if os.path.isfile(calls_p):
            calls = json.load(open(calls_p, encoding="utf-8"))
            mains = [c for c in calls if c.get("tag", "main") == "main"]
            raw = mains[0].get("raw_text") or mains[0].get("reply") or "" if mains else ""
        slots, reason = _slots_of(raw)
        card = cards_by_case.get(mf["case_id"], {})
        status = _delivery_status(reason, bool(slots))
        tk = mf.get("tokens") or {}
        # 每轮公共底账字段（身份/交付/程序/资源），确保 parse 失败与不回都在分母里
        common = {
            "arm": arm_label, "case_id": mf["case_id"],
            "run_id": mf.get("run_id"),
            "harness_identity_sha256": idx.get("harness_identity_sha256"),
            "authorization_id": idx.get("authorization_id"),
            "batch_id": idx.get("batch_id") or idx.get("authorization_id"),
            "case_input_sha256": mf.get("case_input_sha256"),
            "raw_reply_sha256": _sha(raw),
            "delivery_status": status,       # has_text / valid_no_reply / format_fail
            "slot_parse": reason,
            "program_score10": sc.get("score10"),
            "program_veto": sc.get("veto"),
            "program_failed_checks": [c["id"] for c in sc.get("checks", [])
                                      if not c.get("pass")],
            "tokens": {"prompt": tk.get("prompt"), "completion": tk.get("completion")},
            "latency_ms": mf.get("latency_ms"),
            "turn_type": mf.get("turn_type"),
            "evidence": card.get("evidence", []),
            "families": card.get("families", []),
            "allowed_behaviors": card.get("allowed_behaviors", []),
            "failure_conditions": card.get("failure_conditions", []),
            # ---- 以下全部人评/待评，绝不预填 ----
            "semantic_label": "",
            "interaction_quality": {k: "" for k in DIMS},
            "severe_error": "",
            "reason": "",
            "pref_vote": "",
        }
        if slots:
            for slot, text, sh in slots:
                row = dict(common)
                row.update({"slot": slot, "slot_text": text,
                            "slot_text_sha256": sh})
                rows.append(row)
        else:
            # parse 失败 / 合法不回 / 空输出：仍出一行逐轮底账，进程序与资源分母
            row = dict(common)
            row.update({"slot": "(no text)", "slot_text": "",
                        "slot_text_sha256": ""})
            rows.append(row)
    return rows, idx


def worksheet_from_plan(plan_path, cards_by_case):
    """未实跑批次：为该批每个 (arm,slot) 生成待实跑占位行；语义/程序分留空。"""
    plan = json.load(open(plan_path, encoding="utf-8"))
    frozen = plan.get("frozen", {}).get("identity_closure", {})
    rows = []
    for a in plan["attempts"]:
        arm = a["arm"]
        cand = plan.get("arm_mapping", {}).get(arm, arm)
        rows.append({
            "arm": arm, "case_id": a["case_id"], "run_id": "",
            "harness_identity_sha256": (frozen.get(arm) or {}).get("identity_sha256"),
            "authorization_id": "", "batch_id": plan.get("batch_id", ""),
            "case_input_sha256": "", "raw_reply_sha256": "",
            "slot": "recommended", "slot_text": "", "slot_text_sha256": "",
            "program_score10": None, "program_veto": None,
            "program_failed_checks": [],
            "evidence": cards_by_case.get(a["case_id"], {}).get("evidence", []),
            "families": cards_by_case.get(a["case_id"], {}).get("families", []),
            "allowed_behaviors": cards_by_case.get(a["case_id"], {}).get("allowed_behaviors", []),
            "failure_conditions": cards_by_case.get(a["case_id"], {}).get("failure_conditions", []),
            "semantic_label": "", "interaction_quality": {k: "" for k in DIMS},
            "severe_error": "", "reason": "", "pref_vote": "",
            "status": "PENDING_REAL_RUN",
        })
    return rows


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--exam", default=None, help="已生成答卷的 exam 目录")
    ap.add_argument("--cards", required=True, help="评审卡 JSON（评估侧）")
    ap.add_argument("--arm", default="candidate", help="exam 对应的臂标签")
    ap.add_argument("--planned", default=None, help="未跑批次 INTERLEAVE_PLAN.json")
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)

    cards = json.load(open(args.cards, encoding="utf-8")) if os.path.isfile(args.cards) else []
    cards_by_case = {c["case_id"]: c for c in cards if isinstance(c, dict)}

    out = {"dimensions": DIMS, "note": "语义/互动质量全留空=未评，绝不预填 pass；程序分独立",
           "rows": []}
    if args.exam:
        rows, _idx = worksheet_from_exam(args.exam, cards_by_case, args.arm)
        out["rows"].extend(rows)
    if args.planned:
        out["rows"].extend(worksheet_from_plan(args.planned, cards_by_case))

    filled_semantic = sum(1 for r in out["rows"] if r.get("semantic_label"))
    assert filled_semantic == 0, "工作表竟预填了 %d 个语义标签" % filled_semantic
    # 交付底账覆盖度：parse 失败/合法不回都必须在分母里，绝不把"没行"当"没错误"
    from collections import Counter
    ds = Counter(r.get("delivery_status", "(planned)") for r in out["rows"])
    exam_cases = sorted({r["case_id"] for r in out["rows"]
                         if r.get("run_id")})            # 实跑（有真实 run_id）覆盖的位次
    out["coverage"] = {
        "rows_total": len(out["rows"]),
        "exam_turns_covered": len(exam_cases),
        "delivery_status_tally": dict(ds),
        "program_denominator_note":
            "format_fail 与 valid_no_reply 均计入程序/资源分母，不静默丢弃",
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    json.dump(out, open(args.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    prog = sum(1 for r in out["rows"] if r.get("program_score10") is not None)
    print("worksheet rows=%d (program-filled=%d, semantic-prefilled=%d must=0) "
          "exam_turns=%d delivery=%s -> %s"
          % (len(out["rows"]), prog, filled_semantic, len(exam_cases),
             dict(ds), os.path.relpath(args.out, ROOT).replace("\\", "/")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
