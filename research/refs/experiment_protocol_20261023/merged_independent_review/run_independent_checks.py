"""Read-only source review, recorded-output checks and offline counterexamples.

No real model adapter is constructed. Existing experiment artifacts are not overwritten.
"""
import contextlib
import hashlib
import importlib.util
import inspect
import io
import json
import os
from pathlib import Path
import sys
import tempfile

sys.dont_write_bytecode = True
sys.stdout.reconfigure(encoding="utf-8")
ROOT = Path(r"D:\lovebrain-arena")
OUT = Path(__file__).resolve().parent


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def candidate_identity(name):
    base = ROOT / "candidates" / name
    entries = {
        str(p.relative_to(ROOT)).replace("\\", "/"): sha(p)
        for p in base.rglob("*")
        if p.is_file() and p.suffix in (".py", ".md", ".json")
    }
    joined = "\n".join(f"{k}:{entries[k]}" for k in sorted(entries))
    return {"identity": hashlib.sha256(joined.encode()).hexdigest(), "files": entries}


before = {
    str(p): sha(p)
    for p in [
        ROOT / "candidates/reference_product_v1/reference_chain.py",
        ROOT / "candidates/reference_product_v1/mechanism/mechanism.py",
        ROOT / "candidates/reference_product_v1/product_memory.py",
        ROOT / "candidates/v2_recall/mechanism/mechanism.py",
        ROOT / "tools/exam_layered_score.py",
        ROOT / "arena/lb_scorer.py",
        ROOT / "arena/protocol_public.py",
    ]
}

extra = load(ROOT / "tools/test_baseline_parity_extra.py", "merged_extra")
rc = extra._load(str(ROOT / "candidates/reference_product_v1/reference_chain.py"), "merged_rc")
parent = extra._load(str(ROOT / "candidates/reference_product_v1/mechanism/mechanism.py"), "merged_parent")
vtests = load(ROOT / "tools/test_v2_recall.py", "merged_vtests")
v2 = vtests.load(vtests.V2_MECH, "merged_v2")
carry = load(ROOT / "tools/test_baseline_carryover.py", "merged_carry")
layer = load(ROOT / "tools/exam_layered_score.py", "merged_layer")

results = []
for fn in extra.CHECKS:
    args = [{"rc": rc, "mech": parent}[p] for p in inspect.signature(fn).parameters]
    try:
        results.append({"suite": "extra", "check": fn.__name__, "status": "PASS", "detail": fn(*args)})
    except Exception as exc:
        results.append({"suite": "extra", "check": fn.__name__, "status": "FAIL", "detail": repr(exc)})
for name in sorted(n for n in vars(carry) if n.startswith("t") and n[1:2].isdigit() and callable(getattr(carry, n))):
    with tempfile.TemporaryDirectory(prefix="carry_", dir=OUT) as tmp:
        assert Path(tmp).resolve().parent == OUT
        try:
            detail = getattr(carry, name)(parent, tmp)
            results.append({"suite": "carryover", "check": name, "status": "PASS", "detail": detail})
        except Exception as exc:
            results.append({"suite": "carryover", "check": name, "status": "FAIL", "detail": repr(exc)})
for fn in vtests.CHECKS:
    try:
        results.append({"suite": "v2", "check": fn.__name__, "status": "PASS", "detail": fn(v2)})
    except Exception as exc:
        results.append({"suite": "v2", "check": fn.__name__, "status": "FAIL", "detail": repr(exc)})


class RecordingGateway:
    def __init__(self):
        self.prompts = []

    def generate(self, system, prompt, **kwargs):
        self.prompts.append(prompt)
        return {"text": json.dumps({
            "response": {"recommended": "好的", "bad_boy": "好", "playful": "行", "warm": "嗯"},
            "directions": [None] * 4,
            "analysis": {"topic_status": "same", "topic_label": "课程", "scene_facts": [], "ongoing": []},
        }, ensure_ascii=False)}


probes = {}
with tempfile.TemporaryDirectory(prefix="budget_", dir=OUT) as tmp:
    assert Path(tmp).resolve().parent == OUT
    gw = RecordingGateway()
    h = v2.init(vtests.ASSETS, {"episode_id": "REVIEW_BUDGET", "persona": {}, "initial_kb": {}}, gw, tmp)
    chain = "舞蹈练习安排是周三晚上，提前热身，按原来的节奏练习。" * 4
    rows = [f"id{i}~事务编号A{i:02d} | 进行中 | {chain}" for i in range(20)]
    h["storage"].write_file("kb", "moment/plan.md", "## 进行中\n" + "\n".join(rows))
    h["storage"].write_file("kb", "memory/raw_topic.md", "# [2026-11-02 12:00] 舞蹈练习\n她：舞蹈练习安排是周三晚上，提前热身，按原来的节奏练习。\n")
    messages = [{"role": "HER", "content": "上回舞蹈练习是怎么安排的来着？"}]
    body = v2.recall_body(h, messages, topic_label="")
    selected = h["ongoing"].select_for_injection("kb", messages, 1, effective_intent={}, recall_clue=body)
    extras = selected.get("v2RecallExtras") or []
    rendered = "\n".join(f"{it['name']} | {it.get('status', '')} | {it.get('chain', '')}" for it in extras)
    v2.run_turn(h, {"turn_index": 1, "turn_type": "reply", "input": {
        "messages": [{"role": "HER", "text": messages[0]["content"]}], "clock": "2026-11-02 12:00"}}, gw)
    prompt = gw.prompts[-1]
    probes["new_context_budget"] = {
        "declared_cap": v2.MAX_NEW_CONTEXT_CHARS,
        "extras_n": len(extras),
        "extra_render_utf16": rc.utf16_len(rendered),
        "extras_in_final_prompt": sum(f"事务编号A{i:02d}" in prompt for i in range(20)),
        "final_user_utf16": rc.utf16_len(prompt),
        "bounded": rc.utf16_len(rendered) <= v2.MAX_NEW_CONTEXT_CHARS,
    }

with tempfile.TemporaryDirectory(prefix="labels_", dir=OUT) as tmp:
    assert Path(tmp).resolve().parent == OUT
    d = Path(tmp) / "E001" / "turn_01_E001_T01"
    d.mkdir(parents=True)
    real = ROOT / "logs/exam1_20261023/E001/turn_01_E001_T01"
    for name in ("manifest.json", "scoring.json"):
        (d / name).write_bytes((real / name).read_bytes())
    calls = json.loads((real / "calls.json").read_text(encoding="utf-8"))
    obj = json.loads(calls[0]["raw_text"])
    obj["response"]["recommended"] = "杯子是我做的，我已经替你刻好名字并答应付款代取了。"
    calls[0]["raw_text"] = json.dumps(obj, ensure_ascii=False)
    (d / "calls.json").write_text(json.dumps(calls, ensure_ascii=False), encoding="utf-8")
    summary = layer.summarize(tmp)
    row = summary["turns"][0]
    probes["answer_independent_labels"] = {
        "synthetic_reply": row["primary_text"],
        "primary_label": row["primary_semantic"],
        "label_evidence": row["primary_semantic_note"],
        "backup_with_severe_error": row["backup_with_severe_error"],
        "backup_labels": [r["semantic"] for r in row["backup_semantic_per_slot"]],
        "note": "Only synthetic temporary files were edited. Existing scores were retained deliberately to isolate the semantic label data path, not to rescore the synthetic text.",
    }

actual = json.loads((ROOT / "logs/exam1_20261023/LAYERED_SCORE_v05.json").read_text(encoding="utf-8"))
probes["layered_snapshot"] = {
    "total": actual["total"],
    "nonempty_backups": sum(len(t["backup_semantic_per_slot"]) for t in actual["turns"]),
    "unrated_backups": sum(b["semantic"] == "unrated" for t in actual["turns"] for b in t["backup_semantic_per_slot"]),
    "e003_t04": next(t for t in actual["turns"] if t["case_id"] == "E003_T04"),
}

identities = {n: candidate_identity(n) for n in ("reference_product_v1", "v2_recall")}
v2_paths = set(identities["v2_recall"]["files"])
probes["entrypoint_identity"] = {
    "identities": identities,
    "candidate_identity_covers_parent_source": any("reference_product_v1/" in p for p in v2_paths),
    "method": "Same candidate-tree .py/.md/.json algorithm as tools/api_trial.py::harness_identity; does not import the live runner.",
}

report = {
    "existing_suites": results,
    "suite_counts": {s: {"pass": sum(r["suite"] == s and r["status"] == "PASS" for r in results),
                         "fail": sum(r["suite"] == s and r["status"] == "FAIL" for r in results)}
                     for s in ("extra", "carryover", "v2")},
    "probes": probes,
    "source_hashes_before": before,
    "source_stable": before == {p: sha(p) for p in before},
    "real_model_calls": 0,
    "original_experiment_artifacts_overwritten": False,
}
(OUT / "INDEPENDENT_CHECKS.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"suites": report["suite_counts"], "budget": probes["new_context_budget"],
                  "label_probe": probes["answer_independent_labels"],
                  "identities": {k: v["identity"] for k, v in identities.items()},
                  "source_stable": report["source_stable"], "real_model_calls": 0}, ensure_ascii=False, indent=2))
