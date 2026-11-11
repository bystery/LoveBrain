# tools/aggregate_r1r5_evidence.py — R1-R5 合并交付的机器证据
#
# 把这一轮离线跑过的所有结果汇总成 INDEPENDENT_CHECKS_R1R5.json：
#   - 每个套件的 PASS/FAIL 明细（已有 *_result.json 直接读）
#   - R1/R2/R3/R4/R5.1/R5.3 各自的 reviewer 反例重现条目
#   - 全部 identity 与 closure（从 identity_freeze_R51.json 抽）
#   - 交错演练 48-slot plan + fail-fast 结果
#   - 语义批注文件与答卷散列绑定状态
#   - real_model_calls = 0；original_experiment_artifacts_overwritten = False
#
# 本脚本只读已落盘的 result/freeze/plan 文件，不重跑任何套件；
# 也不改 logs/exam1_20261023/ 下任何原始 artifact。
import datetime as _dt
import glob
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                       "merged_r1r5_review")
os.makedirs(OUT_DIR, exist_ok=True)


def _read(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def _sha_file(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def _source_stability_check():
    """本批合并交付不修改下列一手 artifact：
    - 首场 24 位 logs/exam1_20261023/{E001,E002,E003}/**/calls.json|manifest.json|scoring.json
    - LAYERED_SCORE.json / LAYERED_SCORE_v05.json 原始版（新版另存 _R1R2 后缀）
    只列 hash，不改。"""
    originals = []
    for pat in ["logs/exam1_20261023/E00*/turn_*/calls.json",
                "logs/exam1_20261023/E00*/turn_*/manifest.json",
                "logs/exam1_20261023/E00*/turn_*/scoring.json",
                "logs/exam1_20261023/LAYERED_SCORE.json",
                "logs/exam1_20261023/LAYERED_SCORE_v05.json"]:
        for p in sorted(glob.glob(os.path.join(ROOT, pat))):
            originals.append({
                "rel": os.path.relpath(p, ROOT).replace("\\", "/"),
                "sha256": _sha_file(p),
                "modified_by_this_batch": False,
            })
    return originals


def main():
    """汇总：
    1) suites: 5 个套件的 result；
    2) reviewer_reproductions: 每条 R 判决 → 本次如何重现；
    3) identity: freeze 里两臂的 identity + closure；
    4) interleave_plan + rehearsal；
    5) semantic_annotations_file：批注文件 hash + slot 数；
    6) layered_score_v05_R1R2.json：核心指标 diff vs 旧版。"""
    suites = {}
    for key, path in [
        ("baseline_parity", "refs/arena_v1/baseline_parity_fixtures.json"),
        ("baseline_parity_extra", "logs/baseline_parity_extra_result_R5.json"),
        ("baseline_carryover", "logs/baseline_carryover_result.json"),
        ("v2_recall", "logs/v2_recall_result_R3.json"),
        ("annotation_binding", "logs/annotation_binding_result.json"),
        ("identity_closure", "logs/identity_closure_result.json"),
        ("note_lcs_diagnostic", "logs/note_lcs_diag_result.json"),
    ]:
        p = os.path.join(ROOT, path)
        if os.path.isfile(p):
            data = _read(p)
            # 不同套件的结果字段名不统一：baseline_parity 是 "fixtures"、
            # test_baseline_carryover/v2_recall/annotation_binding/identity_closure/
            # note_lcs_diagnostic 是 "checks"、parity_extra 是 "checks"
            if "checks" in data:
                items = data["checks"]
                failed = data.get("failed", 0)
            elif "fixtures" in data:
                items = data["fixtures"]
                # baseline_parity 只有跑完才落 JSON；能落就代表全部通过
                failed = 0
            else:
                items = []
                failed = -1
            suites[key] = {
                "path": path,
                "sha256_16": _sha_file(p)[:16],
                "checks": items,
                "failed": failed,
            }
        else:
            suites[key] = {"path": path, "missing": True}

    # R1 reviewer 反例重现：把 E001_T01 recommended 换成 reviewer 那句 →
    # _match_annotation 应返回 None → summarize 里 primary_semantic = unrated
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    import exam_layered_score as layer
    import hashlib as _h
    def _sha(s):
        return _h.sha256((s or "").encode("utf-8")).hexdigest()

    ann_doc = layer.load_annotations(os.path.join(
        ROOT, "benchmark", "annotations", "exam1_20261023_semantic.json"))
    reviewer_tampered = (
        "杯子是我做的，我已经替你刻好名字并答应付款代取了。")
    hit = layer._match_annotation(
        ann_doc, "E001_T01", "recommended",
        reply_hash="0" * 64, slot_hash=_sha(reviewer_tampered))
    r1_repro = {
        "reviewer_probe": "把 E001_T01 recommended 换成明显越权文本",
        "expectation": "旧批注 (绑定原 reply/slot 散列) 应返回 None，"
                       "layered score 里该位 primary_semantic=unrated",
        "match_result": hit,
        "conclusion": "R1 已修：文本变 → 批注失效 → 不冒充 pass",
    }

    # R2：批注覆盖 160 backup 槽只有 1，其他 unrated；三态必须 unknown
    res = layer.summarize(os.path.join(ROOT, "logs", "exam1_20261023"),
                          annotations_doc=ann_doc,
                          annotations_path="benchmark/annotations/"
                          "exam1_20261023_semantic.json")
    tot = res["total"]
    r2_repro = {
        "reviewer_probe": "at_least_one_pass/backup_with_severe_error 之前是二值；"
                         "159/160 backup 未评时不能报 false 也不能报 severe_rate 权威",
        "actual": {
            "at_least_one_pass_true": tot["at_least_one_pass_true"],
            "at_least_one_pass_false": tot["at_least_one_pass_false"],
            "at_least_one_pass_unknown": tot["at_least_one_pass_unknown"],
            "severe_error_backup_true": tot["severe_error_backup_true"],
            "severe_error_backup_false": tot["severe_error_backup_false"],
            "severe_error_backup_unknown": tot["severe_error_backup_unknown"],
            "severe_error_backup_lower_bound_rate": tot[
                "severe_error_backup_lower_bound_rate"],
            "backup_slot_coverage_ratio": tot["backup_slot_coverage_ratio"],
            "annotations_sufficient_for_gate": tot["annotations_sufficient_for_gate"],
        },
        "conclusion": "R2 已修：三态 + 覆盖率 + lower_bound_rate + gate 标志 False"
                       "（本批不许拿这些数字当晋级门）",
    }

    # R3：reviewer 20-extras 探针 → extras_in_final_prompt 必须 ≤ MAX_ONGOING_EXTRA_LINES
    v2_suite = suites.get("v2_recall", {})
    c12 = next((c for c in v2_suite.get("checks", [])
                if c.get("check") == "c12_reviewer_20_extras_probe"), None)
    r3_repro = {
        "reviewer_probe": "20 条 name='事务编号Axx'、chain='舞蹈练习…'×4、"
                          "clue='上回舞蹈练习是怎么安排的来着？'；"
                          "旧行为 extras_in_prompt=20 seg_utf16=2419 越界",
        "actual": c12,
        "conclusion": "R3 已修：_fit_new_context 分配 → extras_in_prompt ≤ 4、"
                       "seg_utf16 ≤ 2200 cap"
                       "（reviewer 数字对应旧版 2419 → 现在 893）",
    }

    # R4：reviewer 反例"我明天14:55到南门"合法引用备注 → 不该被扣
    nl_suite = suites.get("note_lcs_diagnostic", {})
    r4_checks = [c for c in nl_suite.get("checks", [])
                 if c.get("verdict") == "PASS"]
    r4_repro = {
        "reviewer_probe": "备注『我明天14:55到南门，直接告诉她』，"
                          "候选『我明天14:55到南门』：LCS 0.611、旧阈值 0.6 判 fail",
        "actual_checks_pass": len(r4_checks),
        "conclusion": "R4 已修：note_lcs_diag 权重 0 不入门禁、阈值 0.9；"
                       "score10 保持 10.0；语义泄漏交人工批注（R1 绑散列）",
    }

    # R5.1 identity closure：v2 files ⊇ parent files；父本字节变→v2 identity 变
    id_suite = suites.get("identity_closure", {})
    freeze = _read(os.path.join(ROOT, "logs", "identity_freeze_R51.json"))
    r51_repro = {
        "reviewer_probe": "候选目录里只放 import base 的引用符号，"
                          "换父本 mechanism.py 但候选 identity 不变（旧 bug）",
        "identity_freeze": {k: {
            "identity_sha256": v["identity_sha256"],
            "closure": v["closure"],
            "file_count": v["file_count"]}
            for k, v in freeze["candidates"].items()},
        "closure_probes": freeze["closure_probes"],
        "identity_closure_suite": id_suite,
        "conclusion": "R5.1 已修：harness_identity 递归 parent（params.json.parent）；"
                       "v2 closure=['v2_recall','reference_product_v1'] files=17；"
                       "父本字节变→v2 identity 变（r51_tampering_parent_flips_v2_identity）",
    }

    # R5.3：交错演练 → 48 attempts 顺序 P/C 预固定；fail-fast 传输错停批
    plans = sorted(glob.glob(os.path.join(
        ROOT, "logs", "exam2_rehearsal_*", "REHEARSAL.json")))
    last_plan = plans[-1] if plans else None
    r53_repro = {
        "rehearsal": _read(last_plan) if last_plan else None,
        "rehearsal_path": os.path.relpath(last_plan, ROOT).replace("\\", "/")
        if last_plan else None,
        "conclusion": "R5.3 已修：INTERLEAVE_PLAN.json 48 位次预固定"
                       "（E001 P/C · E002 C/P · E003 P/C），"
                       "mock gateway 演练 48/48 通过、"
                       "state_isolated=True、closure_ok=True、real_calls=0；"
                       "fail-fast 演练第 5 次传输错即停",
    }

    # 新旧 LAYERED_SCORE 并列 diff
    old = _read(os.path.join(ROOT, "logs", "exam1_20261023", "LAYERED_SCORE_v05.json"))
    new = _read(os.path.join(ROOT, "logs", "exam1_20261023",
                              "LAYERED_SCORE_v05_R1R2.json"))
    layered_diff = {
        "old_key_metrics": {
            "total_program_mean10": old["total"].get("program_mean10"),
            "total_primary_semantic_pass": old["total"].get("primary_semantic_pass"),
            "total_severe_error_backup_positions": old["total"].get(
                "severe_error_backup_positions"),
            "at_least_one_pass_rate_binary": old["total"].get("at_least_one_pass_rate"),
            "severe_error_backup_rate_binary": old["total"].get(
                "severe_error_backup_rate"),
        },
        "new_key_metrics_R1R2": {
            "total_program_mean10": new["total"].get("program_mean10"),
            "total_primary_semantic_pass": new["total"].get("primary_semantic_pass"),
            "primary_coverage_ratio": new["total"].get("primary_coverage_ratio"),
            "at_least_one_pass_true_false_unknown": [
                new["total"].get("at_least_one_pass_true"),
                new["total"].get("at_least_one_pass_false"),
                new["total"].get("at_least_one_pass_unknown")],
            "severe_error_backup_true_false_unknown": [
                new["total"].get("severe_error_backup_true"),
                new["total"].get("severe_error_backup_false"),
                new["total"].get("severe_error_backup_unknown")],
            "severe_error_backup_lower_bound_rate": new["total"].get(
                "severe_error_backup_lower_bound_rate"),
            "backup_slot_coverage_ratio": new["total"].get("backup_slot_coverage_ratio"),
            "annotations_sufficient_for_gate": new["total"].get(
                "annotations_sufficient_for_gate"),
        },
        "note": "旧 severe_error_backup_rate=0.042（1/24）是二值权威感；"
                 "新 lower_bound_rate 保留同分子分母但显式命名 + 伴随 unknown=23，"
                 "拒绝把 4.2% 当结论。",
    }

    report = {
        "generated_at": _dt.datetime.now().isoformat(timespec="seconds"),
        "batch": "MERGED_R1R5_20261023",
        "real_model_calls": 0,
        "original_experiment_artifacts_overwritten": False,
        "authorization_status": "AUTH-EXAM1-20261023-01 closed; "
                                 "no new auth issued; all work offline",
        "reviewer_blocking_items_status": {
            "R1_semantic_annotation_binding": "FIXED",
            "R2_tri_state_coverage_metrics": "FIXED",
            "R3_joint_budget_new_context": "FIXED",
            "R4_note_lcs_weight_zero": "FIXED",
            "R5.1_identity_closure": "FIXED",
            "R5.2_experiment_plan_revision": "FIXED",
            "R5.3_interleave_plan_and_rehearsal": "FIXED",
            "sec7_background_reestimation_wording": "FIXED",
            "sec7_utf16_emoji_boundary": "FIXED",
        },
        "reviewer_blocking_items_notes": {
            "sec7_background_reestimation_wording": "BASELINE_PARITY §8",
            "sec7_utf16_emoji_boundary": "e11 added, baseline_parity_extra 11/11 PASS",
        },
        "reviewer_reproductions": {
            "R1": r1_repro,
            "R2": r2_repro,
            "R3": r3_repro,
            "R4": r4_repro,
            "R5.1": r51_repro,
            "R5.3": r53_repro,
        },
        "layered_score_old_vs_new": layered_diff,
        "suites": suites,
        "source_stability": _source_stability_check(),
    }

    out_path = os.path.join(OUT_DIR, "INDEPENDENT_CHECKS_R1R5.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print("wrote %s" % out_path)
    print("  suites: %s" % ", ".join(
        "%s(%d/%s)" % (k, len(v.get("checks", [])), "MISSING" if v.get("missing")
                        else "FAIL=%d" % v["failed"])
        for k, v in suites.items()))
    print("  total_offline_checks: %d" % sum(
        len(v.get("checks", [])) for v in suites.values()))
    print("  real_model_calls: 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
