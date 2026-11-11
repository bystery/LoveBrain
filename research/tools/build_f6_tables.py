# -*- coding: utf-8 -*-
r"""tools/build_f6_tables.py — F6 终稿用的汇总表（零调用，只读已落盘证据）。

产出 F6_TABLES.json，包含：
  A. 九维主表：历史 127 对（F1）与新材料 72 对（F3）**分开**，逐维适用 n、双臂均分、差值；
     历史按批次宏平均、新材料按故事宏平均，绝不拼成 202 个独立样本。
  B. 交付/行动结果表：双臂 action_class 分布、协议失败位点、合法不回、卡面冲突。
  C. 候选侧动作切片（钩子/邀约/承接/展开/试探/自我表达）×盲评胜负；对照臂无 analysis，如实标 NR。
  D. 知识链审计：出窗原子、投影命中、语义正确运用（裁判 memory_use 与事实类 major）。
  E. 传输与代价：延迟 p50/p95（生成与裁判分列）、token、金额一律 null（无价目）。
  F. 判决所需的反面证据：major errors 明细、顺序一致性、无效消融计数。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/build_f6_tables.py \
      --out refs/experiment_protocol_20261023/final_mainline_review_20261029/F6_TABLES.json
"""
import argparse
import glob
import json
import os
import statistics
import sys
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                    "final_mainline_review_20261029")
DIMS = ('goal', 'factuality', 'attribution', 'time_state', 'boundary',
        'memory_use', 'interaction', 'initiative', 'emotion_relation')


def jl(p):
    return json.load(open(p, encoding="utf-8"))


def rows_of(path):
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]


def pct(a, b):
    return round(100.0 * a / b, 2) if b else None


def pctl(xs, q):
    if not xs:
        return None
    xs = sorted(xs)
    i = min(len(xs) - 1, int(round(q * (len(xs) - 1))))
    return xs[i]


def dims_table(rows, bindings):
    acc = {arm: {d: [] for d in DIMS} for arm in ("parent", "candidate")}
    dist = {arm: {d: Counter() for d in DIMS} for arm in ("parent", "candidate")}
    wins = Counter()
    major = {"parent": [], "candidate": []}
    fit = {arm: Counter() for arm in ("parent", "candidate")}
    conflicts = []
    for r in rows:
        p = r.get("parsed") or {}
        b = bindings.get(r["unit_id"])
        if not b:
            continue
        for side, arm in (("A", b.get("a_arm")), ("B", b.get("b_arm"))):
            d = p.get(side) or {}
            sc = d.get("scores") or {}
            for k in DIMS:
                v = sc.get(k)
                if isinstance(v, (int, float)):
                    acc[arm][k].append(v)
                    dist[arm][k][str(int(v))] += 1
            for e in (d.get("major_errors") or []):
                major[arm].append({"unit": r["unit_id"], "error": e})
            fit[arm][str(d.get("action_fit"))] += 1
        w = b.get("a_arm") if p.get("verdict") == "A" else \
            (b.get("b_arm") if p.get("verdict") == "B" else str(p.get("verdict")))
        wins[w] += 1
        if p.get("card_conflict"):
            conflicts.append(r["unit_id"])
    table = {}
    for k in DIMS:
        pv, cv = acc["parent"][k], acc["candidate"][k]
        table[k] = {"n_parent": len(pv), "n_candidate": len(cv),
                    "mean_parent": round(statistics.mean(pv), 3) if pv else None,
                    "mean_candidate": round(statistics.mean(cv), 3) if cv else None,
                    "delta": (round(statistics.mean(cv) - statistics.mean(pv), 3)
                              if pv and cv else None),
                    "dist_parent": dict(sorted(dist["parent"][k].items())),
                    "dist_candidate": dict(sorted(dist["candidate"][k].items()))}
    return {"wins": dict(wins), "dims": table,
            "major_errors": {a: len(v) for a, v in major.items()},
            "major_error_detail": major,
            "action_fit": {a: dict(c) for a, c in fit.items()},
            "card_conflicts": conflicts}


def group_macro(rows, bindings, keyfn):
    groups = defaultdict(lambda: {"parent": 0, "candidate": 0, "tie": 0,
                                  "uncertain": 0, "n": 0})
    for r in rows:
        p = r.get("parsed") or {}
        b = bindings.get(r["unit_id"])
        if not b:
            continue
        g = keyfn(r)
        if g is None:
            continue
        groups[g]["n"] += 1
        w = b.get("a_arm") if p.get("verdict") == "A" else \
            (b.get("b_arm") if p.get("verdict") == "B" else str(p.get("verdict")))
        if w in ("parent", "candidate"):
            groups[g][w] += 1
        elif w == "tie":
            groups[g]["tie"] += 1
        else:
            groups[g]["uncertain"] += 1
    return {g: {"n": v["n"], "candidate": v["candidate"], "parent": v["parent"],
                "tie": v["tie"], "uncertain": v["uncertain"]}
            for g, v in sorted(groups.items())}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(BASE, "F6_TABLES.json"))
    ap.add_argument("--f3-run", default="logs/f3_newmat_live_restart_20261029")
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    # ---- A 九维主表 -------------------------------------------------------
    f1_rows = rows_of(os.path.join(ROOT, "logs", "f1_blind_review_20261029_094731",
                                   "F1_RATINGS.jsonl"))
    f1_bnd = jl(os.path.join(BASE, "F1_PRIVATE_BINDINGS.json"))
    hist = [r for r in f1_rows if r["kind"] == "historical"]
    ctrl = [r for r in f1_rows if r["kind"] == "control"]
    swaps = [r for r in f1_rows if r["kind"] == "swap"]
    f3_rows = rows_of(os.path.join(ROOT, "logs", "f3_judge_20261029_115709",
                                   "F3_RATINGS.jsonl"))
    f3_bnd = jl(os.path.join(BASE, "F3_PRIVATE_BINDINGS.json"))

    hist_tab = dims_table(hist, f1_bnd)
    new_tab = dims_table(f3_rows, f3_bnd)
    hist_tab["batch_macro"] = group_macro(
        hist, f1_bnd, lambda r: r["unit_id"].split(":")[1])
    new_tab["story_macro"] = group_macro(
        f3_rows, f3_bnd, lambda r: (f3_bnd[r["unit_id"]] or {}).get("story"))

    # 控制组：16 个已知对/错样本，测裁判本身可信度
    ctrl_ok = []
    for r in ctrl:
        b = f1_bnd.get(r["unit_id"]) or {}
        exp = b.get("expected") or {}
        got = (r.get("parsed") or {}).get("verdict")
        want = exp.get("verdict")
        if want == "tie_or_uncertain":
            ok = got in ("tie", "uncertain")      # 与 tools/analyze_f1_blind.py 同一口径
        else:
            ok = want == got
        ctrl_ok.append({"unit_id": r["unit_id"], "expected": want, "got": got, "ok": ok})
    controls = {"n": len(ctrl_ok), "correct": sum(1 for x in ctrl_ok if x["ok"]),
                "accuracy_pct": pct(sum(1 for x in ctrl_ok if x["ok"]), len(ctrl_ok)),
                "criterion": "strict 控制对要求逐字命中预期臂；C08（合法不回）预期 tie_or_uncertain，"
                             "裁判给 tie/uncertain 即算对——与 tools/analyze_f1_blind.py 完全同口径",
                "rows": ctrl_ok}

    # 顺序一致性：F1 12 个换序（在 F1_ANALYSIS 里）与 F5 双顺序（F5_ANALYSIS 里）
    f5 = jl(os.path.join(BASE, "F5_ANALYSIS.json"))
    f1a = jl(os.path.join(BASE, "F1_ANALYSIS.json"))

    # ---- B 交付/行动结果 --------------------------------------------------
    hrows = rows_of(os.path.join(BASE, "ACTION_OUTCOMES_130.jsonl"))
    hcls = {"parent": Counter(), "candidate": Counter()}
    for r in hrows:
        ac = r.get("action_class") or {}
        if isinstance(ac, dict):
            for arm in ("parent", "candidate"):
                hcls[arm][str(ac.get(arm))] += 1
    nrows = rows_of(os.path.join(BASE, "F3_ACTION_OUTCOMES_72.jsonl"))
    ncls = {"parent": Counter(), "candidate": Counter()}
    for r in nrows:
        for arm in ("parent", "candidate"):
            ncls[arm][str((r.get("sides") or {}).get(arm, {}).get("action_class"))] += 1
    hlayer = Counter(str(r.get("action_layer_verdict")) for r in hrows)
    nlayer = Counter()
    for r in nrows:
        pa = (r["sides"]["parent"] or {}).get("action_class")
        ca = (r["sides"]["candidate"] or {}).get("action_class")
        nlayer["both_replied" if pa == ca == "replied" else
               ("candidate_undeliverable" if ca == "format_undeliverable" else
                ("parent_undeliverable" if pa == "format_undeliverable" else
                 "legit_no_reply_either_side" if "no_reply" in (pa or "") + (ca or "")
                 else "other"))] += 1
    hsummary = jl(os.path.join(BASE, "ACTION_OUTCOMES_130_SUMMARY.json"))
    delivery = {
        "historical_130_pairs": {arm: dict(c) for arm, c in hcls.items()},
        "historical_action_layer": dict(hlayer),
        "new_72_pairs": {arm: dict(c) for arm, c in ncls.items()},
        "new_action_layer": dict(nlayer),
        "protocol_failed_positions_new": [
            r["case_id"] for r in nrows
            if (r["sides"]["candidate"] or {}).get("action_class") == "format_undeliverable"],
        "card_conflict_pairs_new": sum(1 for r in nrows if r.get("card_conflict_flags")),
        "card_conflict_pairs_hist": len(hsummary.get("card_conflict_pairs") or []),
        "card_conflict_hist_detail": hsummary.get("card_conflict_pairs"),
    }

    # ---- C 候选侧动作切片（新材料批，去重 K 只数 *_resume）---------------
    base3 = os.path.join(ROOT, a.f3_run.replace("/", os.sep))
    chains = [os.path.join(base3, x) for x in
              ("candidate_N01", "candidate_N02",
               "candidate_K01_resume", "candidate_K02_resume")]
    slice_win = defaultdict(Counter)
    slice_n = Counter()
    unparsed = 0
    # 逐位取盲评胜者（由 bindings 解出臂名）
    winners = {}
    for r in f3_rows:
        p = r.get("parsed") or {}
        b = f3_bnd[r["unit_id"]]
        w = b.get("a_arm") if p.get("verdict") == "A" else \
            (b.get("b_arm") if p.get("verdict") == "B" else str(p.get("verdict")))
        winners[b["case_id"]] = w
    for cd in chains:
        for tdir in sorted(glob.glob(os.path.join(cd, "turn_*"))):
            cid = os.path.basename(tdir).split("_", 2)[-1]
            f = os.path.join(tdir, "reply_raw.txt")
            if not os.path.isfile(f):
                continue
            try:
                o = json.loads(open(f, encoding="utf-8").read())
            except Exception:
                unparsed += 1
                continue
            act = (o.get("analysis") or {}).get("action_taken") or \
                ("no_reply" if str(o.get("action") or "").lower() == "no_reply" else "unlabeled")
            slice_n[act] += 1
            slice_win[act][winners.get(cid, "?")] += 1
    slices = {"candidate_action_slices_new_batch": {
        k: {"n": v, "outcome": dict(slice_win[k])} for k, v in sorted(slice_n.items())},
        "unparseable_answers": unparsed,
        "note": "对照臂（reference_product_v1）返回体没有 analysis.action_taken 字段，"
                "切片只能对候选侧统计；对照侧标 NR。用户是否愿意发出（W）本批未测。"}

    # ---- C2 文本量与"合法不回"机会计数（实测，不引用旧口径）-------------
    send_p = [len((r["sides"]["parent"] or {}).get("text") or "") for r in nrows]
    send_c = [len((r["sides"]["candidate"] or {}).get("text") or "") for r in nrows]
    har_p, har_c = [], []
    for side in ("parent", "candidate"):
        for nm in ("N01", "N02", "K01_resume", "K02_resume"):
            pref = side if side == "parent" else "candidate"
            for f in sorted(glob.glob(os.path.join(base3, "%s_%s" % (pref, nm),
                                                    "turn_*", "reply_raw.txt"))):
                (har_p if side == "parent" else har_c).append(
                    len(open(f, encoding="utf-8").read()))
    md_cases = [r["case_id"] for r in nrows if r.get("may_decline")]
    md_used = [r["case_id"] for r in nrows
               if r.get("may_decline") and
               (r["sides"]["candidate"] or {}).get("action_class") == "legit_no_reply"]
    textvolume = {
        "sendable_text_chars_mean": {"parent": round(statistics.mean(send_p), 1),
                                      "candidate": round(statistics.mean(send_c), 1),
                                      "ratio": round(statistics.mean(send_c) /
                                                     statistics.mean(send_p), 3)},
        "harness_answer_chars_mean": {"parent": round(statistics.mean(har_p), 1),
                                       "candidate": round(statistics.mean(har_c), 1),
                                       "n": [len(har_p), len(har_c)]},
        "may_decline_windows": {"n": len(md_cases), "cases": md_cases,
                                 "candidate_used_no_reply": len(md_used),
                                 "parent_action_classes": dict(Counter(
                                     (r["sides"]["parent"] or {}).get("action_class")
                                     for r in nrows if r.get("may_decline"))),
                                 "candidate_action_classes": dict(Counter(
                                     (r["sides"]["candidate"] or {}).get("action_class")
                                     for r in nrows if r.get("may_decline")))},
        "note": "两臂在 6 个 may_decline 窗口里都选择了回复，"
                "所以'合法不回'能力在本批没有测试机会（NR），不能据此说它被牺牲或被证明。",
    }

    # ---- D 知识链审计 ----------------------------------------------------
    ksum = jl(os.path.join(BASE, "F3_KNOWLEDGE_CHAIN_SUMMARY.json"))
    knowledge = {
        "denominators": {"atoms_required_candidate_side": ksum["candidate_atoms"],
                         "audit_rows": ksum["rows"]},
        "retrieval": {"真出窗原子（来源句不在原文历史且不在当前输入）": ksum["source_out_of_window"],
                      "出窗且被投影段单独保留": ksum["projected_but_out_of_window"],
                      "口径": "只有后者才是长期召回的正证；本批为 0"},
        "capture_fidelity_update": "本批未单独测量（NR）：F3 审计只覆盖 source 是否在窗口与投影命中",
        "use_proxy": {"candidate_memory_use_mean": new_tab["dims"]["memory_use"]["mean_candidate"],
                      "parent_memory_use_mean": new_tab["dims"]["memory_use"]["mean_parent"]},
        "contamination": {"candidate_fact_major_errors":
                          sum(1 for e in new_tab["major_error_detail"]["candidate"]
                              if any(w in str(e["error"]) for w in
                                      ("无据", "违反", "断言", "未经核实", "当作确定", "当作未确认"))) ,
                          "parent_fact_major_errors":
                          sum(1 for e in new_tab["major_error_detail"]["parent"]
                              if any(w in str(e["error"]) for w in
                                      ("无据", "违反", "断言", "未经核实", "当作确定", "当作未确认")))},
        "verdict_note": "K 链优势=近期上下文使用与协议合规；跨窗口长期记忆未被证明。",
    }

    # ---- E 传输与代价 ----------------------------------------------------
    gen_lat, judge_lat = [], []
    for cd in chains:
        for cp in glob.glob(os.path.join(cd, "turn_*", "calls.json")):
            try:
                c = json.load(open(cp, encoding="utf-8"))
            except Exception:
                continue
            if isinstance(c, dict):
                c = [c]
            for x in c:
                if x.get("latency_ms"):
                    gen_lat.append(x["latency_ms"])
    for src in (("logs/f1_blind_review_20261029_094731/F1_RATINGS.jsonl", f1_rows),
                ("logs/f3_judge_20261029_115709/F3_RATINGS.jsonl", f3_rows)):
        for r in src[1]:
            if r.get("latency_ms"):
                judge_lat.append(r["latency_ms"])
    cost = jl(os.path.join(BASE, "F6_COST_INDEX.json"))
    transport = {
        "generation_latency_ms_new_batch": {"n": len(gen_lat),
                                            "p50": pctl(gen_lat, 0.5),
                                            "p95": pctl(gen_lat, 0.95)},
        "judge_latency_ms": {"n": len(judge_lat), "p50": pctl(judge_lat, 0.5),
                             "p95": pctl(judge_lat, 0.95)},
        "protocol": {"model_raw_kept_separate_from_harness_text": True,
                     "raw_不可解析时不编造修复": "F3 一期望行为；F5 的 E3 只动括号并留损",
                     "consumption_evidence": "reply_ingest.json + 动作台账（action_class/text/score10/veto）",
                     "first_usable_consumer_evidence": "NR（产品接入暂停，无真实消费者签收）"},
        "usage_cost": {"attempts": cost["totals"], "tokens": cost["token_grand_total"],
                       "money": None, "money_reason": "网关未提供价目，金额一律 null，不折算"},
    }

    doc = {"kind": "f6_tables", "date": "2026-10-29",
           "sample_rule": "历史 127 对（F1）与新材料 72 对（F3）分别统计，不合并成 202 独立样本；"
                          "同一 case 的多批重复只在其所属批次内计一次",
           "nine_dim_historical_127": hist_tab,
           "nine_dim_new_material_72": new_tab,
           "judge_controls": controls,
           "order_consistency": {"f1_swaps": f1a["swaps"],
                                  "f5_forward_vs_swap": {"agreement_rate": f5["swap"]["agreement_rate"],
                                                           "flipped": f5["swap"]["flipped_cases"]},
                                  "order_effect_hist": f1a["order_effect"]},
           "delivery": delivery,
           "slices": slices,
           "text_volume_and_decline": textvolume,
           "knowledge_chain": knowledge,
           "transport": transport,
           "ablation": {"action": jl(os.path.join(BASE, "F4_ANALYSIS.json"))["action"]["wins"],
                        "knowledge_valid": False,
                        "knowledge_note": jl(os.path.join(BASE, "F4_ANALYSIS.json"))["knowledge"]["note"]},
           "repair": {"defect": "F3:N01_T05 json_schema_broken",
                       "fix_rule": "E3（output_repair 新增）",
                       "replay": jl(os.path.join(BASE, "F5_REPLAY.json"))["summary"],
                       "identities": {"frozen_base": "9b8f4e1eae4c68c0…",
                                       "repaired": jl(os.path.join(BASE, "F5_CANDIDATE_FREEZE_20261029.json"))["identity_sha256"]},
                       "same_arm_redraw": {"forward": f5["wins"], "swap": f5["swap"]["wins"],
                                            "format_fail_case": f5["format_fail_cases"]}},
           }
    dst = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out.replace("/", os.sep))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    json.dump(doc, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("WROTE", os.path.relpath(dst, ROOT).replace("\\", "/"))
    print(json.dumps({"hist_wins": hist_tab["wins"], "new_wins": new_tab["wins"],
                       "controls": controls["accuracy_pct"],
                       "gen_lat": transport["generation_latency_ms_new_batch"],
                       "judge_lat": transport["judge_latency_ms"],
                       "slices": slices["candidate_action_slices_new_batch"]},
                      ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
