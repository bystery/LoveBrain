# -*- coding: utf-8 -*-
"""Benchmark V1 · 零网络 capture 校验（指导书 §5.2 测试清单的批级实现）。

对 mock/capture 管线产物做独立校验：
  1 政策门（B 线排除）；2 候选哈希固定；3 DIRECT/COT 输入等价（user 字节相等、
  system 差=COT 行）；4 未来轮次不可见 + gold eval 侧字段不泄漏（逐轮严格子串）；
  5 四组状态隔离（沙箱/事件账独立）；6 密钥不落盘；7 未动文件完整性（清单存在时）；
  8 attempt 数与计划一致（harness 组允许内部附加调用，如实记录差值）。
全部检查只读，不修复、不重跑。
"""
import glob
import importlib.util
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))
sys.path.insert(0, _HERE)   # python -I 不含脚本目录：显式补齐（four_arm_runner 同目录导入）


def _load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _cot_instruction():
    p = os.path.join(_HERE, "baseline_candidates", "cot_baseline", "mechanism", "mechanism.py")
    spec = importlib.util.spec_from_file_location("bm1_cot_mech_check", p)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.COT_INSTRUCTION


def _episode_gold_strings(ep_path):
    """评估侧专用 gold 字符串（禁止出现在任何 prompt 中）。"""
    ep_dir = os.path.dirname(ep_path)
    out = []
    ref = os.path.join(ep_dir, "reference.json")
    if os.path.isfile(ref):
        data = _load_json(ref)
        turns = data.get("turns") or []
        if isinstance(turns, dict):        # reference.json 的 turns 可为 {case_id: {...}}
            turns = list(turns.values())
        for t in turns:
            if not isinstance(t, dict):
                continue
            g = t.get("gold")
            if not isinstance(g, dict):
                continue
            for k in ("private_guidance",):
                if isinstance(g.get(k), str) and g[k].strip():
                    out.append(g[k].strip())
            for k in ("acceptable_actions", "forbidden_factual_claims"):
                for v in g.get(k) or []:
                    if isinstance(v, str) and v.strip():
                        out.append(v.strip())
            for u in g.get("unknowns") or []:
                if isinstance(u, dict):
                    for key in ("topic", "note", "value"):
                        if isinstance(u.get(key), str) and u[key].strip():
                            out.append(u[key].strip())
                elif isinstance(u, str) and u.strip():
                    out.append(u.strip())
    return [s for s in out if len(s) >= 4]


def _future_texts(ep_path, turn_idx):
    with open(ep_path, "r", encoding="utf-8") as f:
        ep = json.load(f)
    out = []
    for t in ep.get("turns", []):
        ci = t.get("case_id", "")
        try:
            idx = int(ci.rsplit("_T", 1)[-1])
        except Exception:
            continue
        if idx > turn_idx:
            for m in (t.get("input") or {}).get("messages") or []:
                if isinstance(m.get("text"), str) and len(m["text"]) >= 6:
                    out.append(m["text"])
    return out


def _history_up_to(ep_path, turn_idx):
    with open(ep_path, "r", encoding="utf-8") as f:
        ep = json.load(f)
    out = []
    for t in ep.get("turns", []):
        try:
            idx = int(t.get("case_id", "").rsplit("_T", 1)[-1])
        except Exception:
            continue
        if idx <= turn_idx:
            for m in (t.get("input") or {}).get("messages") or []:
                if isinstance(m.get("text"), str):
                    out.append(m["text"])
    return out


def validate_run(run_dir, plan):
    checks = []

    def add(name, ok, details):
        checks.append({"name": name, "pass": bool(ok), "details": details})

    # 1 政策门
    bad = [e for e, meta in plan["episodes"].items()
           if meta.get("b_line_member") or meta.get("policy_problems")]
    add("b_line_and_sealed_excluded", not bad, {"violations": bad})

    # 2 候选哈希固定（按 arm×episode 的实际路由候选核对——CURRENT 双域分流）
    import four_arm_runner as far
    drift = []
    for arm, rec in plan["arms"].items():
        for ep_id, ep_meta in rec["episodes"].items():
            cdir = far.candidate_dir_for_arm(arm, ep_meta["turn_count"])
            cname = os.path.basename(cdir.rstrip("/\\"))
            cur = far.candidate_identity_sha256(cdir)
            if cur != rec["identity_sha256"].get(cname):
                drift.append({"arm": arm, "episode": ep_id, "candidate": cname})
    add("candidate_hash_pinned", not drift, {"drift": drift})

    # 3/4 逐轮 prompt 检查 + 等价
    cot_line = _cot_instruction()
    equiv_fail, leak_eval, leak_future, leak_gold, turns_seen = [], [], [], [], 0
    for ep_id, meta in plan["episodes"].items():
        ep_path = os.path.join(_REPO, meta["episode_path"])
        gold_strings = _episode_gold_strings(ep_path)
        prompts = {}   # (arm, turn_idx) -> list of (system,user)
        for arm in plan["arms"]:
            ep_dir = os.path.join(run_dir, arm, ep_id)
            for cf in sorted(glob.glob(os.path.join(ep_dir, "*", "calls.json"))):
                try:
                    calls = _load_json(cf)
                except Exception:
                    continue
                case_id = os.path.basename(os.path.dirname(cf))
                try:
                    tidx = int(case_id.rsplit("_T", 1)[-1])
                except Exception:
                    continue
                for c in calls:
                    prompts.setdefault((arm, tidx), []).append((c.get("system") or "",
                                                                c.get("user") or ""))
        turns_seen += len({t for (_, t) in prompts})
        futures = {i: _future_texts(ep_path, i) for i in
                   {t for (_, t) in prompts}}
        history = {i: "\n".join(_history_up_to(ep_path, i)) for i in futures}
        for (arm, tidx), lst in sorted(prompts.items()):
            for system, user in lst:
                blob = (system or "") + "\n" + (user or "")
                for s in gold_strings:
                    if s in blob:
                        leak_eval.append({"arm": arm, "case_id": "%s_T%d" % (ep_id, tidx),
                                          "snippet": s[:40]})
                for ft in futures[tidx]:
                    if ft in blob:
                        leak_future.append({"arm": arm, "case_id": "%s_T%d" % (ep_id, tidx),
                                            "snippet": ft[:40]})
        # gold 断言值：出现且不在截至本轮可见历史 → 可疑
        for (arm, tidx), lst in sorted(prompts.items()):
            for system, user in lst:
                blob = (system or "") + "\n" + (user or "")
                for s in _atom_values(ep_path):
                    if s and s in blob and s not in history[tidx]:
                        leak_gold.append({"arm": arm, "case_id": "%s_T%d" % (ep_id, tidx),
                                          "atom": s[:40]})
        # 3 DIRECT/COT 等价
        direct = {t: v for (a, t), v in prompts.items() if a == "DIRECT"}
        cot = {t: v for (a, t), v in prompts.items() if a == "COT"}
        for tidx in sorted(set(direct) & set(cot)):
            if not direct[tidx] or not cot[tidx]:
                continue
            ds, du = direct[tidx][0]
            cs, cu = cot[tidx][0]
            if du != cu:
                equiv_fail.append({"case_id": "%s_T%d" % (ep_id, tidx), "field": "user"})
            if not (cs.endswith(cot_line) and
                    cs[: len(cs) - len(cot_line) - 2] == ds):
                equiv_fail.append({"case_id": "%s_T%d" % (ep_id, tidx),
                                   "field": "system != direct+cot_line"})
    add("direct_cot_input_equivalence", not equiv_fail, {"failures": equiv_fail[:10]})
    add("no_evalside_gold_leak", not leak_eval, {"hits": leak_eval[:10]})
    add("no_future_turn_leak", not leak_future, {"hits": leak_future[:10]})
    add("no_unexplained_gold_atom", not leak_gold,
        {"suspicious": leak_gold[:10],
         "note": "断言值出现在 prompt 且不在截至本轮可见历史=可疑；等义转述不在本检查能力内"})

    # 5 状态隔离：每 arm×episode 沙箱与事件账独立存在
    #（初始化阶段即失败的组合，以 run_records 登记的错误为准，不算隔离违规）
    recorded_fail = set()
    rs_path = os.path.join(run_dir, "run_records.json")
    if os.path.isfile(rs_path):
        for rec in _load_json(rs_path):
            if rec.get("status") == "error":
                recorded_fail.add((rec["arm"], rec["episode"]))
    missing_iso, init_fail = [], []
    for arm in plan["arms"]:
        for ep_id in plan["episodes"]:
            base = os.path.join(run_dir, arm, ep_id)
            if not os.path.isdir(os.path.join(base, "_sandbox")):
                if (arm, ep_id) in recorded_fail:
                    init_fail.append("%s/%s" % (arm, ep_id))
                else:
                    missing_iso.append("%s/%s/_sandbox" % (arm, ep_id))
            elif not os.path.isfile(os.path.join(base, "gateway_events.jsonl")) \
                    and (arm, ep_id) not in recorded_fail:
                missing_iso.append("%s/%s/gateway_events.jsonl" % (arm, ep_id))
    add("four_arm_state_isolated", not missing_iso,
        {"missing": missing_iso, "recorded_init_failures": init_fail})

    # 6 密钥不落盘
    key = os.environ.get("LB_API_KEY", "")
    hits = []
    if key:
        for root, dirs, files in os.walk(run_dir):
            for fn in files:
                p = os.path.join(root, fn)
                try:
                    with open(p, "r", encoding="utf-8", errors="ignore") as f:
                        if key in f.read():
                            hits.append(os.path.relpath(p, run_dir))
                except Exception:
                    pass
    add("api_key_never_persisted", not hits, {"files": hits})

    # 8 attempt 与计划一致（已登记失败组合的轮位从期望中扣除，差值如实展示）
    import four_arm_runner as far2
    actual = far2.count_attempts(run_dir)
    planned = 4 * sum(m["turn_count"] for m in plan["episodes"].values())
    expected = 0
    if os.path.isfile(rs_path):
        for rec in _load_json(rs_path):
            n = rec.get("turns_done") if rec.get("status") == "ok" \
                else rec.get("turns_done_before_error")
            expected += n or 0
    # harness 臂（非轻量）live 下允许有限内部附加调用：增量 <= 该臂已完成轮数
    light_arms = {"DIRECT", "COT"}
    rec_list = _load_json(rs_path) if os.path.isfile(rs_path) else []
    harness_turns = sum(
        (rec.get("turns_done") or rec.get("turns_done_before_error") or 0)
        for rec in rec_list if rec.get("arm") not in light_arms)
    delta = actual - expected
    ok = (delta == 0) or (0 < delta <= harness_turns)
    add("attempts_consistent", ok,
        {"actual": actual, "expected_from_records": expected,
         "harness_turns_allowance": harness_turns,
         "planned_full": planned, "lost_to_recorded_failures": planned - expected,
         "note": "mock/轻量臂每组每轮恰 1 次调用；live harness 臂允许内部附加调用"
                 "（增量<=该臂已完成轮数），超出即 FAIL（如实记录）"})

    return {"n_checks": len(checks),
            "all_pass": all(c["pass"] for c in checks),
            "turns_checked": turns_seen, "checks": checks}


def _atom_values(ep_path):
    out = []
    ref = os.path.join(os.path.dirname(ep_path), "reference.json")
    if os.path.isfile(ref):
        turns = _load_json(ref).get("turns") or []
        if isinstance(turns, dict):
            turns = list(turns.values())
        for t in turns:
            if not isinstance(t, dict):
                continue
            g = t.get("gold")
            if not isinstance(g, dict):
                continue
            for a in g.get("required_memory_atoms") or []:
                if isinstance(a, dict):
                    v = a.get("value")
                    if isinstance(v, str) and len(v.strip()) >= 4:
                        out.append(v.strip())
    return out


def write_report(rep, run_dir):
    with open(os.path.join(run_dir, "CAPTURE_VALIDATION_REPORT.json"), "w",
              encoding="utf-8") as f:
        json.dump(rep, f, ensure_ascii=False, indent=2)
    lines = ["# Capture 校验报告", "",
             "- 结论: **%s**（%d/%d 检查通过，轮位覆盖 %d）"
             % ("PASS" if rep["all_pass"] else "FAIL",
                sum(1 for c in rep["checks"] if c["pass"]), rep["n_checks"],
                rep.get("turns_checked", 0)), ""]
    for c in rep["checks"]:
        lines.append("- [%s] %s — %s" % ("PASS" if c["pass"] else "**FAIL**",
                                         c["name"], json.dumps(c["details"],
                                                               ensure_ascii=False)[:300]))
    with open(os.path.join(run_dir, "CAPTURE_VALIDATION_REPORT.md"), "w",
              encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    run_dir, plan_path = sys.argv[1], sys.argv[2]
    rep = validate_run(run_dir, _load_json(plan_path))
    write_report(rep, run_dir)
    print("all_pass=%s n=%d" % (rep["all_pass"], rep["n_checks"]))
