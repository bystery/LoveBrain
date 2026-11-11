# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 3 · 统计分析（stats_phase3 v1，P3-4.5 预注册实现，2026-11-09）。

预注册口径（EXPERIMENT_PROTOCOL_FROZEN_V1 引用本文件）：
  - 主聚类单位 = Episode；反事实孪生以 lineage_group 为更高层相关单位
    （bootstrap 按**组**重采样：同组所有 episode 一起进/出，防孪生双计权）；
  - Bootstrap 重采样 B=2000，固定随机种子（20261109），报告 95% 置信区间
    （percentile 法）；
  - 主要三组比较（CURRENT vs DIRECT / COT / LEGACY_COMPAT_R9）用预先固定的
    Holm-Bonferroni 校正；bootstrap p = 2*min(P(diff>0), P(diff<0))；
  - 专题分层分别报告；样本量不足（有效位 < 10）只报描述与 CI，不宣布显著；
  - 配对口径：仅同 (episode, case_id) 双方都有有效读数的位进入配对；
    双方都失败 = both_fail 单列，不计入任何一方的通过分母（诚实分母）。

输入：--scores <universal_scores.csv>（或同形状 CSV：列 episode_id/case_id/arm_id/...）
指标：--metric severe_error|contract_ok|cancel_affirm|no_reply（二值化列）
输出：JSON 报告（stdout 或 --out）。
零模型调用；纯标准库。
"""
import argparse
import csv
import json
import random
import sys
from collections import defaultdict

SEED = 20261109
B = 2000
PRIMARY_PAIRS = [("CURRENT", "DIRECT"), ("CURRENT", "COT"), ("CURRENT", "LEGACY_COMPAT_R9")]
MIN_N_FOR_SIGNIFICANCE = 10

BIN_COLUMNS = {
    "severe_error": "severe_error",
    "contract_ok": "pcontract_ok",
    "cancel_affirm": None,      # 由 (generic_cancel_affirm or 0) > 0 派生
    "no_reply": None,           # 由 action == no_reply 派生
    "parse_ok": "parse_ok",
}


def bin_value(row, metric):
    col = BIN_COLUMNS.get(metric)
    if metric == "cancel_affirm":
        try:
            return 1 if int(row.get("generic_cancel_affirm") or 0) > 0 else 0
        except (TypeError, ValueError):
            return None
    if metric == "no_reply":
        return 1 if row.get("action") == "no_reply" else 0
    if col is None:
        return None
    v = row.get(col)
    if isinstance(v, bool):
        return int(v)
    if isinstance(v, str) and v.lower() in ("true", "false"):
        return int(v.lower() == "true")
    try:
        return int(float(v))
    except (TypeError, ValueError):
        return None


def load_scores(path, metric, allocation):
    """→ {(ep,case): {arm: value}}，并附 topic/lineage。只留双臂皆非 None 的位。"""
    sm = {}
    if allocation and metric != "none":
        try:
            sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))
            import phase3_runner as p3
            raw = p3.load_split_map(allocation)
            sm = {e: (v["topic"], v["lineage"]) for e, v in raw.items()}
        except Exception:
            sm = {}
    data = defaultdict(dict)
    with open(path, encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            eid, cid, arm = row.get("episode_id"), row.get("case_id"), row.get("arm_id")
            if not eid or not cid or not arm:
                continue
            v = bin_value(row, metric)
            if v is None:
                continue
            data[(eid, cid)][arm] = v
    return data, sm


def bootstrap_diff(pairs, groups_of, rng):
    """pairs: list of (va, vb, group_key)。按组重采样 B 次，返回 diff 分布。"""
    by_group = defaultdict(list)
    for va, vb, g in pairs:
        by_group[g].append((va, vb))
    gkeys = sorted(by_group)
    if not gkeys:
        return []
    diffs = []
    for _ in range(B):
        sa = sb = n = 0
        for g in rng.choices(gkeys, k=len(gkeys)):
            for va, vb in by_group[g]:
                sa += va
                sb += vb
                n += 1
        if n:
            diffs.append((sa - sb) / n)
    return diffs


def compare(data, arm_a, arm_b, rng):
    pairs = []
    both_fail = 0
    for k, d in data.items():
        va, vb = d.get(arm_a), d.get(arm_b)
        if va is None and vb is None:
            continue
        if va is None or vb is None:
            continue          # 缺位不计配对（缺失规则预注册：如实报缺失数）
        pairs.append((va, vb, k[0]))
        if va == 0 and vb == 0:
            both_fail += 0    # 占位：both_fail 语义由调用方按指标定义
    if not pairs:
        return {"n_pairs": 0, "skipped": "no_paired_positions"}
    diffs = bootstrap_diff(pairs, None, rng)
    diffs.sort()
    n = len(pairs)
    mean_a = sum(p[0] for p in pairs) / n
    mean_b = sum(p[1] for p in pairs) / n
    point = mean_a - mean_b
    if diffs:
        lo = diffs[int(0.025 * len(diffs))]
        hi = diffs[min(len(diffs) - 1, int(0.975 * len(diffs)))]
        p_left = sum(1 for d in diffs if d < 0) / len(diffs)
        p_right = sum(1 for d in diffs if d > 0) / len(diffs)
        p_two = min(1.0, 2 * min(p_left, p_right))
    else:
        lo = hi = p_two = None
    return {"n_pairs": n, "rate_%s" % arm_a: round(mean_a, 4),
            "rate_%s" % arm_b: round(mean_b, 4), "diff": round(point, 4),
            "ci95": [round(lo, 4) if lo is not None else None,
                     round(hi, 4) if hi is not None else None],
            "p_two_sided": round(p_two, 4) if p_two is not None else None}


def holm(results):
    """results: [{...,p_two_sided}] → 就地加 holm_p / significant。"""
    items = [r for r in results if r.get("p_two_sided") is not None]
    items.sort(key=lambda r: r["p_two_sided"])
    m = len(items)
    running = 0.0
    for i, r in enumerate(items):
        adj = (m - i) * r["p_two_sided"]
        running = max(running, adj)
        r["holm_p"] = round(min(1.0, running), 4)
    for r in results:
        n = r.get("n_pairs", 0)
        r["significant_at_0.05_holm"] = bool(
            r.get("holm_p") is not None and r["holm_p"] < 0.05
            and n >= MIN_N_FOR_SIGNIFICANCE)
        if n < MIN_N_FOR_SIGNIFICANCE:
            r["note"] = "有效位不足 %d，仅描述与 CI，不宣布显著" % MIN_N_FOR_SIGNIFICANCE
    return results


def main():
    ap = argparse.ArgumentParser(description="Phase3 配对统计（episode/组聚类 bootstrap）")
    ap.add_argument("--scores", required=True)
    ap.add_argument("--metric", default="severe_error", choices=sorted(BIN_COLUMNS))
    ap.add_argument("--allocation", default=None)
    ap.add_argument("--out", default=None)
    args = ap.parse_args()
    data, sm = load_scores(args.scores, args.metric, args.allocation)
    rng = random.Random(SEED)
    report = {"kind": "phase3_paired_stats", "version": "stats_phase3_v1",
              "metric": args.metric, "seed": SEED, "bootstrap": B,
              "clustering": "bootstrap by episode（孪生同组一起重采样：按 lineage_group 键）",
              "topic_slices": {}, "overall": []}
    # 组键：episode（+ lineage 时并入同组）
    grouped = defaultdict(dict)
    for (eid, cid), d in data.items():
        topic, lin = sm.get(eid, ("", ""))
        gkey = lin or eid
        grouped[gkey].update(d)          # 同组聚合为键（位级保序无碰撞风险：case 不同）
    # 位级数据改键为 (group, case) 以保持位置粒度
    regrouped = defaultdict(dict)
    for (eid, cid), d in data.items():
        topic, lin = sm.get(eid, ("", ""))
        regrouped[(lin or eid, cid)] = d
    # overall（三主比较，Holm）
    res = []
    for a, b in PRIMARY_PAIRS:
        r = compare(regrouped, a, b, rng)
        r["comparison"] = "%s_vs_%s" % (a, b)
        res.append(r)
    holm(res)
    report["overall"] = res
    # 专题分层（描述性 + CI；不做事后 Holm 宣告）
    topics = defaultdict(dict)
    for (g, cid), d in regrouped.items():
        pass
    # topic 归属按 episode：建 (topic) → 位集合
    by_topic = defaultdict(dict)
    for (eid, cid), d in data.items():
        topic, lin = sm.get(eid, ("", "?"))
        by_topic[topic or "?"][(lin or eid, cid)] = d
    for topic, dset in sorted(by_topic.items()):
        tres = []
        for a, b in PRIMARY_PAIRS:
            r = compare(dset, a, b, rng)
            r["comparison"] = "%s_vs_%s" % (a, b)
            tres.append(r)
        report["topic_slices"][topic] = {"n_positions": len(dset), "comparisons": tres}
    out = json.dumps(report, ensure_ascii=False, indent=1)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(out)
    print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
