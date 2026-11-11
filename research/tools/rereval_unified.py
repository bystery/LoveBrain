# -*- coding: utf-8 -*-
"""统一离线重评 v1（第九波判决 §8-A4，scorer v0.9.2 契约口径）。

对既有 32 条 fork 输出（父臂 dev16 + 候选臂）与 24 条 Pilot 输出统一重评：
- 明细唯一键 = (run_id, candidate, branch, case_id)——修复 v0.9.1 重评的键冲突覆盖；
- 每行带原文 sha256 / 契约 sha256 / scorer 版本，可独立复核；
- 汇总分三段：全分母均分、完整成功率（非 veto 比例）、成功子集均分 + 失败类型表；
- 零真实模型调用；原始日志只读不改。

契约口径：scorer v0.9.2 = 出站提示 string|null（directions null 合法缺省），
与历史运行契约一致；v0.9.1 严格口径的独立复算数字在判决书中，本工具不重复。
"""
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))

import lb_scorer          # noqa: E402
import lb_runner          # noqa: E402

SCORER_VERSION = "v0.9.2"
OUTPUT_VERSION = "unified-v2（独立复核 §5.1：fail_type=解析结果；元数据补齐）"


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

FORK_RUNS = [
    # (run_dir, candidate_dir, episode, 臂名)
    (r"logs/l3b_fork/l3bfork_20261020_115043_pid33764", "candidates/phase0_beta_single",
     "benchmark/episodes/L3B_MINI_FULL.json", "parent_arm"),
    (r"logs/l3b_fork/l3bfork_20261020_175009_pid8776", "candidates/phase1_retr_idf",
     "benchmark/episodes/L3B_MINI_FULL.json", "candidate_arm"),
]
PILOT_RUNS = [
    (r"logs/pilot_r3/baseline_L1", "candidates/round_0_baseline",
     "benchmark/episodes/archive/PILOT_L1_v1__pilot_r3.json", "pilot_baseline"),
    (r"logs/pilot_r3/beta_L1", "candidates/phase0_beta_single",
     "benchmark/episodes/archive/PILOT_L1_v1__pilot_r3.json", "pilot_beta"),
]

def score_row(case, raw, id_map, contract, old_score, old_veto, key):
    """单条重评；坏 JSON 登记具体语法错误类型（第九波判决 §3.4）。
    v2（独立复核 §5.1）：fail_type 直接使用解析结果（msg+col），不再用手写
    run_branch 映射表——减少手写环节，映射表已删除。"""
    try:
        parsed = json.loads(raw)
        parse_err = None
    except json.JSONDecodeError as e:
        parsed = None
        parse_err = "%s (col %d)" % (e.msg, e.colno)
    if parsed is None:
        new_score, new_veto = 0, "json_parse_error"
    else:
        r = lb_scorer.run_case_scoring_safe(case, raw, id_map, contract)
        new_score, new_veto = r["score10"], r["veto"]
    return {
        "key": {"run_branch": key[0], "candidate": key[1], "branch": key[2],
                "case_id": key[3], "arm": key[4]},
        "raw_sha256": _sha(raw.encode("utf-8")),
        "old_score10": old_score, "old_veto": old_veto,
        "new_score10": new_score, "new_veto": new_veto,
        "json_parse_error": parse_err,
        "fail_type": (parse_err if parse_err else new_veto),
    }


def summarize(rows, tag):
    """三段统计：全分母均分 / 完整成功率 / 成功子集均分 + 失败类型。"""
    n = len(rows)
    full = [r["new_score10"] for r in rows]
    ok = [r for r in rows if not r["new_veto"]]
    fails = {}
    for r in rows:
        if r["new_veto"]:
            fails[r["new_veto"]] = fails.get(r["new_veto"], 0) + 1
    return {
        "group": tag, "n": n,
        "mean_all": round(sum(full) / n, 6) if n else None,
        "complete_rate": round(len(ok) / n, 6) if n else None,
        "mean_clean_subset": (round(sum(r["new_score10"] for r in ok) / len(ok), 6)
                              if ok else None),
        "n_clean": len(ok),
        "fail_types": fails,
    }


def main():
    out_rows, groups = [], []
    contract_sha_cache = {}
    for run_rel, cand_rel, ep_rel, arm in FORK_RUNS + PILOT_RUNS:
        run = os.path.join(ROOT, run_rel)
        cand = os.path.join(ROOT, cand_rel)
        contract = lb_runner.load_contract(cand)
        if cand_rel not in contract_sha_cache:
            p = os.path.join(cand, "params.json")
            contract_sha_cache[cand_rel] = _sha(open(p, "rb").read())[:16]
        ep = json.load(open(os.path.join(ROOT, ep_rel), encoding="utf-8"))
        case_by_id = {t["case_id"]: t for t in ep["turns"]}
        run_id = os.path.basename(run.rstrip("/\\"))
        if "pilot" in arm:
            turn_dirs = sorted(d for d in os.listdir(run)
                               if d.startswith("turn_") and os.path.isdir(os.path.join(run, d)))
            for td in turn_dirs:
                cid = td.split("_", 2)[2] if td.count("_") >= 2 else td
                tdp = os.path.join(run, td)
                raw_p = os.path.join(tdp, "reply_raw.txt")
                mf_p = os.path.join(tdp, "manifest.json")
                if not (os.path.isfile(raw_p) and os.path.isfile(mf_p)):
                    continue
                case = case_by_id.get(cid) or case_by_id.get(td)
                if case is None:
                    continue
                mf = json.load(open(mf_p, encoding="utf-8"))
                id_map = lb_runner.canonical_id_map(case["input"]["messages"])
                raw = open(raw_p, encoding="utf-8").read()
                out_rows.append(score_row(
                    case, raw, id_map, contract,
                    mf.get("score10"), mf.get("veto"),
                    (run_id, cand_rel, td, cid, arm)))
        else:
            branches = sorted(d for d in os.listdir(run)
                              if os.path.isdir(os.path.join(run, d))
                              and not d.startswith(("_", "freeze")))
            for br in branches:
                for td in ("turn_05_L3B_t05", "turn_06_L3B_t06"):
                    tdp = os.path.join(run, br, td)
                    if not os.path.isdir(tdp):
                        continue
                    cid = "L3B_t05" if td.endswith("t05") else "L3B_t06"
                    case = case_by_id[cid]
                    id_map = lb_runner.canonical_id_map(case["input"]["messages"])
                    mf = json.load(open(os.path.join(tdp, "manifest.json"),
                                        encoding="utf-8"))
                    raw = open(os.path.join(tdp, "reply_raw.txt"),
                               encoding="utf-8").read()
                    out_rows.append(score_row(
                        case, raw, id_map, contract,
                        mf.get("score10"), mf.get("veto"),
                        (run_id, cand_rel, br, cid, arm)))

    # 汇总分组：fork 两臂 × (A_t5, B_t5, A_t6, B_t6) + pilot 两候选
    def bucket(row):
        br, cid = row["key"]["branch"], row["key"]["case_id"]
        side = "A" if br.startswith("A") else "B"
        return "%s_%s" % (side, cid.split("_")[-1])

    for _run_rel, cand_rel, _ep, arm in FORK_RUNS:
        for b in ("A_t05", "B_t05", "A_t06", "B_t06"):
            rows = [r for r in out_rows
                    if r["key"]["arm"] == arm and bucket(r) == b]
            if rows:
                groups.append(summarize(rows, "%s/%s" % (arm, b)))
    for _run_rel, cand_rel, _ep, arm in PILOT_RUNS:
        rows = [r for r in out_rows if r["key"]["arm"] == arm]
        if rows:
            groups.append(summarize(rows, arm))

    result = {
        "output_version": OUTPUT_VERSION,
        "scoring_version": SCORER_VERSION,
        "scorer_sha256": _sha(open(os.path.join(ROOT, "arena", "lb_scorer.py"),
                                   "rb").read()),
        "episode_sha256": {ep_rel: _sha(open(os.path.join(ROOT, ep_rel), "rb").read())
                           for _r, _c, ep_rel, _a in FORK_RUNS + PILOT_RUNS},
        "contract_semantics": "directions element = string|null (出站提示契约，第九波判决 §3.1 统一)",
        "contract_sha256": {cand_rel: _sha(open(os.path.join(ROOT, cand_rel,
                                                        "params.json"), "rb").read())
                            for _r, cand_rel, _e, _a in FORK_RUNS + PILOT_RUNS},
        "contract_sha256_16": contract_sha_cache,
        "n_rows": len(out_rows),
        "detail": out_rows,
        "summary": groups,
        "note": ("唯一键 (run_branch, candidate, branch, case_id)；v0.9.1 重评的键冲突"
                 "（父子分支同名互覆）已修复。全分母均分含格式失败 0 分；成功子集均分"
                 "为条件分布，不替代整体可用性（第九波判决 §6-3）。"),
    }
    out_path = os.path.join(ROOT, "logs", "rereval_v092_unified_20261020.json")
    json.dump(result, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("rows:", len(out_rows), "->", os.path.relpath(out_path, ROOT))
    for g in groups:
        print("  %(group)-22s n=%(n)2d mean_all=%(mean_all)s complete=%(complete_rate)s "
              "clean_mean=%(mean_clean_subset)s fails=%(fail_types)s" % g)


if __name__ == "__main__":
    main()
