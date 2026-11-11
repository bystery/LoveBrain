"""Offline independent review. No production files or old evidence are overwritten."""
import ast
import contextlib
import copy
import hashlib
import importlib.util
import inspect
import io
import json
import os
from pathlib import Path
import shutil
import socket
import sys
import tempfile
import urllib.request

sys.dont_write_bytecode = True
sys.stdout.reconfigure(encoding="utf-8")
ROOT = Path(r"D:\lovebrain-arena")
OUT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "arena"))


def forbidden(*args, **kwargs):
    raise AssertionError("Network prohibited in independent review")


urllib.request.urlopen = forbidden
socket.socket.connect = forbidden


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


files = list((ROOT / "candidates/reference_product_v1").rglob("*.py"))
files += list((ROOT / "candidates/v2_recall").rglob("*.py"))
files += [ROOT / "tools" / n for n in [
    "exam_layered_score.py", "build_semantic_annotations.py", "exam_interleave_plan.py",
    "api_trial.py", "freeze_identity.py", "test_annotation_binding.py",
    "test_identity_closure.py", "test_note_lcs_diagnostic.py",
]]
files += [ROOT / "arena/lb_scorer.py", ROOT / "benchmark/annotations/exam1_20261023_semantic.json"]
before = {str(p): sha(p) for p in files}
results = []


def run(suite, fn, *args):
    try:
        detail = fn(*args)
        results.append({"suite": suite, "check": fn.__name__, "status": "PASS", "detail": detail})
    except Exception as exc:
        results.append({"suite": suite, "check": fn.__name__, "status": "FAIL", "detail": repr(exc)})


# Run all 26 helper fixtures; suppress only the final json.dump to the old evidence path.
helper_path = ROOT / "tools/test_baseline_parity.py"
tree = ast.parse(helper_path.read_text(encoding="utf-8-sig"))
tree.body = [n for n in tree.body if not (
    isinstance(n, ast.Expr) and isinstance(n.value, ast.Call)
    and isinstance(n.value.func, ast.Attribute) and n.value.func.attr == "dump"
    and isinstance(n.value.func.value, ast.Name) and n.value.func.value.id == "json")]
ns = {"__file__": str(helper_path), "__name__": "independent_parity_fixtures"}
with contextlib.redirect_stdout(io.StringIO()):
    exec(compile(tree, str(helper_path), "exec"), ns)
for f in ns["FIXTURES"]:
    results.append({"suite": "baseline_parity", "check": f["id"],
                    "status": "FAIL" if f["id"] in ns["fails"] else "PASS"})

extra = load(ROOT / "tools/test_baseline_parity_extra.py", "r15_extra")
rc = extra._load(str(ROOT / "candidates/reference_product_v1/reference_chain.py"), "r15_rc")
parent = extra._load(extra.MECH, "r15_parent")
for fn in extra.CHECKS:
    run("parity_extra", fn, *[{"rc": rc, "mech": parent}[p] for p in inspect.signature(fn).parameters])
carry = load(ROOT / "tools/test_baseline_carryover.py", "r15_carry")
for name in sorted(n for n in vars(carry) if n.startswith("t") and n[1:2].isdigit() and callable(getattr(carry, n))):
    with tempfile.TemporaryDirectory(prefix="carry_", dir=OUT) as tmp:
        assert Path(tmp).resolve().parent == OUT
        run("carryover", getattr(carry, name), parent, tmp)
vt = load(ROOT / "tools/test_v2_recall.py", "r15_vt")
v2 = vt.load(vt.V2_MECH, "r15_v2")
for fn in vt.CHECKS:
    run("v2_recall", fn, v2)
for name in ("test_annotation_binding", "test_note_lcs_diagnostic"):
    mod = load(ROOT / f"tools/{name}.py", "r15_" + name)
    for fn in mod.CHECKS:
        run(name, fn)

# Identity mutation fixtures run only on an isolated copy, never the actual parent/child.
idt = load(ROOT / "tools/test_identity_closure.py", "r15_idt")
with tempfile.TemporaryDirectory(prefix="identity_", dir=OUT) as tmp:
    assert Path(tmp).resolve().parent == OUT
    td = Path(tmp)
    (td / "tools").mkdir()
    shutil.copy2(ROOT / "tools/api_trial.py", td / "tools/api_trial.py")
    for name in ("reference_product_v1", "v2_recall"):
        shutil.copytree(ROOT / "candidates" / name, td / "candidates" / name,
                        ignore=shutil.ignore_patterns("__pycache__"))
    at_copy = load(td / "tools/api_trial.py", "r15_api_copy")
    idt.ROOT = str(td)
    idt._load_api_trial = lambda: at_copy
    for fn in idt.CHECKS:
        run("identity_closure_isolated_copy", fn)

layer = load(ROOT / "tools/exam_layered_score.py", "r15_layer")
ann_path = ROOT / "benchmark/annotations/exam1_20261023_semantic.json"
ann = layer.load_annotations(str(ann_path))
summary = layer.summarize(str(ROOT / "logs/exam1_20261023"), ann, str(ann_path))
probes = {"layered_total": summary["total"]}
probes["primary_slot_provenance"] = [
    {k: row[k] for k in ("case_id", "primary_text", "primary_semantic", "primary_semantic_note")}
    for row in summary["turns"] if row["case_id"] in ("E001_T04", "E003_T04")]

a = copy.deepcopy(ann["annotations"][0])
invalid_context = {"run_id": "UNRELATED_RUN", "harness_identity_sha256": "0" * 64,
                   "case_sha256": "f" * 64, "_index": {(a["case_id"], a["slot"]): [a]}}
hit = layer._match_annotation(invalid_context, a["case_id"], a["slot"],
                              a["raw_reply_sha256"], a["slot_text_sha256"])
probes["annotation_context"] = {
    "invalid_run_and_harness_context_accepted": hit is not None,
    "annotation_has_case_content_hash": "case_sha256" in a,
    "note": "Text+slot matching works; run/harness/case provenance is not checked by _match_annotation."}

# Actual final prompt, not only a helper's nominal size.
with tempfile.TemporaryDirectory(prefix="emoji_", dir=OUT) as tmp:
    assert Path(tmp).resolve().parent == OUT
    gw = vt._RecordingGateway()
    h = v2.init(vt.ASSETS, {"episode_id": "EMOJI_BUDGET", "persona": {}, "initial_kb": {}}, gw, tmp)
    raw = "# [2026-11-02] 舞蹈安排\n她：舞蹈安排如下。\n" + "🎂" * 1800
    h["storage"].write_file("kb", "memory/raw_topic.md", raw)
    v2.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
        "messages": [{"role": "HER", "text": "上回舞蹈安排还记得吗"}],
        "clock": "2026-11-02 12:00"}}, gw)
    prompt = gw.prompts[-1]
    probes["emoji_new_context_budget"] = {
        "declared_cap_utf16": v2.MAX_NEW_CONTEXT_CHARS,
        "emoji_count_in_final_request": prompt.count("🎂"),
        "emoji_payload_utf16_in_final_request": prompt.count("🎂") * 2,
        "final_user_utf16": rc.utf16_len(prompt),
        "recall_in_final_request": "【回忆】" in prompt,
        "budget_obeyed_even_excluding_headers": prompt.count("🎂") * 2 <= v2.MAX_NEW_CONTEXT_CHARS,
    }

inter = load(ROOT / "tools/exam_interleave_plan.py", "r15_inter")
doc = {"attempts": inter.build_plan()}
probes["normal_rehearsal"] = inter.rehearse(doc)
probes["failure_rehearsal"] = inter.rehearse_fail_fast(doc)
duplicate = copy.deepcopy(doc)
duplicate["attempts"][2].update(turn=1, case_id="E001_T01")
probes["duplicate_turn_rehearsal"] = inter.rehearse(duplicate)
probes["duplicate_turn_rehearsal"]["mutation"] = "Parent E001 seq3 reruns T01 instead of T02"
bad_freeze = copy.deepcopy(doc)
bad_freeze["identity_closure"] = {"v2_recall": {"identity_sha256": "0" * 64}}
probes["stale_freeze_rehearsal"] = inter.rehearse(bad_freeze)

freeze = load(ROOT / "tools/freeze_identity.py", "r15_freeze")
ids = freeze.freeze(["reference_product_v1", "v2_recall"])
probes["current_identity"] = ids

report = {
    "worker_fixture_results": results,
    "suite_counts": {s: {"pass": sum(r["suite"] == s and r["status"] == "PASS" for r in results),
                         "fail": sum(r["suite"] == s and r["status"] == "FAIL" for r in results)}
                     for s in sorted({r["suite"] for r in results})},
    "independent_probes": probes,
    "source_hashes": before,
    "sources_stable": before == {p: sha(p) for p in before},
    "network_disabled": True, "real_model_calls": 0,
    "original_artifacts_overwritten": False,
    "test_execution_notes": [
        "26 helper assertions executed with only final old-report write suppressed.",
        "6 identity tests executed against a copied candidate tree and the same api_trial identity implementation.",
        "Other existing checks invoked directly without their result-file-writing main functions.",
    ],
}
(OUT / "CHECKS.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"suites": report["suite_counts"],
                  "emoji_budget": probes["emoji_new_context_budget"],
                  "annotation_context": probes["annotation_context"],
                  "duplicate_plan": {k: probes["duplicate_turn_rehearsal"][k] for k in ("all_done", "order_verified", "state_isolated", "closure_ok")},
                  "stale_freeze_accepted": probes["stale_freeze_rehearsal"]["closure_ok"],
                  "identities": {k: v["identity_sha256"] for k, v in ids["candidates"].items()},
                  "sources_stable": report["sources_stable"], "real_model_calls": 0}, ensure_ascii=False, indent=2))
