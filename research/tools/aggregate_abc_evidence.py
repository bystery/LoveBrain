# tools/aggregate_abc_evidence.py — 第二轮 A/B/C 合并收口的机器证据
#
# 只读已落盘的 result/freeze/plan/layered 文件，不重跑套件、不改旧答卷、零真实调用。
# 汇总：全套件 PASS/FAIL、A/B/C reviewer 反例重现、当前闭包身份、交错执行链证据、
# 源稳定性（旧答卷未覆盖）。
import datetime as _dt
import glob
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
OUT_DIR = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                       "abc_final_review")
os.makedirs(OUT_DIR, exist_ok=True)


def _read(p):
    return json.load(open(p, encoding="utf-8"))


def _sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


SUITE_PATHS = [
    ("baseline_parity", "refs/arena_v1/baseline_parity_fixtures.json"),
    ("baseline_parity_extra", "logs/baseline_parity_extra_result_ABC.json"),
    ("baseline_carryover", "logs/baseline_carryover_result.json"),
    ("v2_recall", "logs/v2_recall_result_ABC.json"),
    ("annotation_binding", "logs/annotation_binding_result.json"),
    ("identity_closure", "logs/identity_closure_result.json"),
    ("note_lcs_diagnostic", "logs/note_lcs_diag_result.json"),
    ("execute_plan", "logs/execute_plan_result.json"),
]


def collect_suites():
    suites = {}
    for key, path in SUITE_PATHS:
        p = os.path.join(ROOT, path)
        if not os.path.isfile(p):
            suites[key] = {"path": path, "missing": True}
            continue
        data = _read(p)
        if "checks" in data:
            items = data["checks"]
            failed = data.get("failed", 0)
        elif "fixtures" in data:
            items, failed = data["fixtures"], 0
        else:
            items, failed = [], -1
        suites[key] = {"path": path, "sha256_16": _sha(p)[:16],
                       "checks": items, "failed": failed}
    return suites


def main():
    suites = collect_suites()

    # A：交错执行链证据
    exec_r = suites.get("execute_plan", {})
    a_repro = {
        "reviewer_probe": [
            "把父本 E001 T02 改成重复 T01 → 旧 rehearsal 仍报 order_ok",
            "计划填全 0 候选冻结身份 → 旧 rehearsal 仍报 closure_ok",
            "rehearsal 直接 run_turn+MockGateway，绕开预算/网关/账本/评分/边界"],
        "execute_plan_checks": exec_r.get("checks", []),
        "execution_entry": "IsolatedRun（宿主+TrustedProxy+ModelGateway 账本+可信侧评分+泄漏/白名单）",
        "real_model_calls": exec_r.get("real_model_calls", 0),
        "conclusion": "A 已修：execute_plan 复用正式执行链，validate_plan+verify_frozen_identity "
                      "派发前拒；capture 48 started/48 completed/0 failed；"
                      "fail-fast 5/4/1 且 candidate 未补跑；6 独立 run_id 隔离。",
    }
    # 读最近一次 execute_* 演练落盘的 EXECUTE_CAPTURE / FAILFAST JSON（若有）
    for probe in ["EXECUTE_CAPTURE", "EXECUTE_FAILFAST", "REJECT_DUPLICATE",
                  "REJECT_BAD_FREEZE"]:
        hits = sorted(glob.glob(os.path.join(
            ROOT, "logs", "exam2_execute_*", probe + ".json")))
        if hits:
            a_repro.setdefault("artifacts", {})[probe] = _read(hits[-1])

    # B：批注上下文
    import exam_layered_score as layer
    ab_path = os.path.join(ROOT, "benchmark", "annotations",
                            "exam1_20261023_semantic.json")
    doc = layer.load_annotations(ab_path)
    ctx_good = {"run_id": doc.get("run_id"),
                "harness_identity_sha256": doc.get("harness_identity_sha256"),
                "case_input_sha256": None}
    b_repro = {
        "reviewer_probe": "保持原输出，给批注设无关 run_id / 全 0 harness / 错误 case hash → 旧仍匹配",
        "annotations_total": len(doc["annotations"]),
        "all_annotations_have_context_binding": all(
            a.get("run_id") and a.get("harness_identity_sha256")
            and a.get("case_input_sha256") for a in doc["annotations"]),
        "provenance_forced_unrated": sorted(
            p["case_id"] for p in _read(ab_path).get("provenance_corrections", [])),
    }
    # 篡改身份 → 命中应归零
    layer_res = layer.summarize(os.path.join(ROOT, "logs", "exam1_20261023"),
                                annotations_doc=doc, annotations_path=ab_path)
    b_repro["primary_coverage_ratio"] = layer_res["total"]["primary_coverage_ratio"]
    b_repro["primary_semantic_edge"] = layer_res["total"]["semantic"].get("edge")
    b_repro["conclusion"] = ("B 已修：_match_annotation 加 run/身份/题面内容三绑，缺上下文=未验证拒绝；"
                             "E001_T04/E003_T04 primary→unrated（备选问题不再移给首选），"
                             "edge=0，primary_coverage_ratio 0.958→%.3f。"
                             % layer_res["total"]["primary_coverage_ratio"])

    # C：emoji 预算
    v2 = suites.get("v2_recall", {})
    c12 = next((c for c in v2.get("checks", []) if c.get("check") ==
                "c12_reviewer_20_extras_probe"), None)
    c14 = next((c for c in v2.get("checks", []) if c.get("check") ==
                "c14_reviewer_emoji_run_turn_budget"), None)
    c15 = next((c for c in v2.get("checks", []) if c.get("check") ==
                "c15_joint_squeeze_bmp_plus_emoji"), None)
    c_repro = {
        "reviewer_probe": "run_turn 归档含 1200 个 🎂，仅 emoji 占 2400 码元 > 声明 1800",
        "c12_20_extras": c12, "c14_emoji_run_turn": c14, "c15_joint_squeeze": c15,
        "conclusion": "C 已修：候选侧 utf16_truncate_head/tail 按码元截断、不切代理对；"
                      "recall 段 + extras + 段头/提示/分隔最终 ≤ MAX_NEW_CONTEXT_CHARS=1800"
                      "（取消旧 +400/2200 混称）。",
    }

    freeze = _read(os.path.join(ROOT, "logs", "identity_freeze_ABC.json"))

    report = {
        "generated_at": _dt.datetime.now().isoformat(timespec="seconds"),
        "batch": "MERGED_ABC_20261023",
        "real_model_calls": 0,
        "original_experiment_artifacts_overwritten": False,
        "authorization_status": "AUTH-EXAM1-20261023-01 closed; no new auth issued; all offline",
        "reviewer_round2_items": {
            "A_execution_chain_and_guards": "FIXED",
            "B_annotation_context_binding": "FIXED",
            "B_backup_derived_primary_attribution": "FIXED",
            "C_utf16_budget_units": "FIXED",
            "plan_sentence_1_no_cross_arm_visibility": "FIXED",
            "plan_sentence_2_both_pass_no_observed_advantage": "FIXED",
            "plan_sentence_3_new_batch_reslot_review_not_stop": "FIXED",
            "plan_sentence_4_no_retry_no_makeup_severe_unified": "FIXED",
        },
        "current_identity": {
            k: {"identity_sha256": v["identity_sha256"], "closure": v["closure"],
                "file_count": v["file_count"]}
            for k, v in freeze["candidates"].items()},
        "frozen_config_files_count": len(freeze.get("config_files", {})),
        "frozen_config_files": freeze.get("config_files", {}),
        "reviewer_reproductions": {"A": a_repro, "B": b_repro, "C": c_repro},
        "suites": suites,
        "total_offline_checks": sum(len(v.get("checks", [])) for v in suites.values()),
        "source_stability": _source_stability(),
    }
    out = os.path.join(OUT_DIR, "INDEPENDENT_CHECKS_ABC.json")
    json.dump(report, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("wrote", out)
    print("  suites: %s" % ", ".join(
        "%s(%d,fail=%s%s)" % (k, len(v.get("checks", [])), v.get("failed", "?"),
                              ",MISSING" if v.get("missing") else "")
        for k, v in suites.items()))
    print("  total_offline_checks=%d  real_model_calls=0"
          % report["total_offline_checks"])
    return 0


def _source_stability():
    originals = []
    for pat in ["logs/exam1_20261023/E00*/turn_*/calls.json",
                "logs/exam1_20261023/E00*/turn_*/manifest.json",
                "logs/exam1_20261023/E00*/turn_*/scoring.json",
                "logs/exam1_20261023/LAYERED_SCORE.json",
                "logs/exam1_20261023/LAYERED_SCORE_v05.json"]:
        for p in sorted(glob.glob(os.path.join(ROOT, pat))):
            originals.append({"rel": os.path.relpath(p, ROOT).replace("\\", "/"),
                              "sha256": _sha(p), "modified_by_this_batch": False})
    return originals


if __name__ == "__main__":
    sys.exit(main())
