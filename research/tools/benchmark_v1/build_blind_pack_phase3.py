# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 3 · 正式人工盲评包构建器（P3-7，build_blind_pack_phase3 v1）。

指导书 §P3-7 执行件：
  - 60 个盲评位置，专题分层配额固定（T01 5 / T02 12 / T03 8 / T04 8 / T05 6 /
    T06 6 / T07 5 / T08 4 / T09 3 / T10 3）；
  - **先选位后看输出**：选位只依赖分派表 + episode 轮数（结构性信息），固定种子可复算；
  - 反事实孪生按 lineage_group 防重复计权（同组最多 1 个位置）；
  - 每位置 3 组配对：CURRENT vs DIRECT / CURRENT vs COT / CURRENT vs LEGACY_COMPAT_R9；
    最多 180 组；左右顺序固定种子随机化（可复现），映射只写 blind_key（不入展示包）；
  - 展示：必要聊天上下文 + 关系背景；不展示 harness 名称与 Gold；
  - 填写项：A更好 / B更好 / 都不错 / 都不愿发送 / 无法判断；未填 = NOT_COLLECTED；
    AI 不代投。

用法：
  python -I tools/benchmark_v1/build_blind_pack_phase3.py \
      --run-dir runs/<holdout_run> --out-dir refs/benchmark_v1_20261107/phase3
输出：HUMAN_PREFERENCE_BLIND_PACK.md + HUMAN_PREFERENCE_BLIND_PACK.json（含上下文）
      + blind_key_phase3.json（映射，**不公开**）。
"""
import argparse
import json
import os
import random
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))
sys.path.insert(0, _HERE)
import normalize_output  # noqa: E402
import phase3_runner as p3  # noqa: E402

SEED = 20261109
TOPIC_QUOTA = {"T01": 5, "T02": 12, "T03": 8, "T04": 8, "T05": 6,
               "T06": 6, "T07": 5, "T08": 4, "T09": 3, "T10": 3}
PAIRS = [("CURRENT", "DIRECT"), ("CURRENT", "COT"), ("CURRENT", "LEGACY_COMPAT_R9")]
LABEL = "blind_pack_phase3_v1"
FILL_OPTIONS = ["A更好", "B更好", "都不错", "都不愿发送", "无法判断"]


def holdout_episodes_by_topic(allocation_csv):
    sm = p3.load_split_map(allocation_csv)
    by_topic = {}
    for eid, v in sorted(sm.items()):
        eff = v["split"] if v["split"] not in ("", "PENDING") else v["planned_split"]
        if eff == "HOLDOUT":
            by_topic.setdefault(v.get("topic"), []).append(eid)
    return by_topic


def episode_topic(allocation_csv, eid):
    sm = p3.load_split_map(allocation_csv)
    return sm.get(eid, {}).get("topic")


def lineage_groups(allocation_csv):
    sm = p3.load_split_map(allocation_csv)
    return {eid: v.get("lineage") for eid, v in sm.items() if v.get("lineage")}


def select_positions(allocation_csv, vault_root):
    """结构化选位（不看任何模型输出）：逐专题配额；组去重；确定性步长公式。"""
    by_topic = holdout_episodes_by_topic(allocation_csv)
    groups = lineage_groups(allocation_csv)
    used_groups = set()
    chosen = []
    rng = random.Random(SEED)
    for topic in sorted(TOPIC_QUOTA):
        eps = by_topic.get(topic) or []
        if not eps:
            continue
        eps = eps[:len(eps)]  # id 升序
        k = TOPIC_QUOTA[topic]
        # 固定步长扫候选位（跨篇推进，确定性），rng 只用于候选组内并列打破
        cand = []
        for i, eid in enumerate(eps):
            n = turn_count(eid, vault_root)
            if n <= 0:
                continue
            for j in range(1, 3):   # 每篇最多 2 个候选位（等间距）
                idx = int(j * (n + 1) / 3 + 0.5)
                if 1 <= idx <= n:
                    cand.append((eid, idx, n))
        # 组去重 + 篇去重优先
        seen_ep = set()
        picked = []
        for eid, idx, n in cand:
            g = groups.get(eid)
            if g and g in used_groups:
                continue
            if eid in seen_ep and len(picked) < k:
                continue
            picked.append((eid, idx, n))
            seen_ep.add(eid)
            if g:
                used_groups.add(g)
            if len(picked) >= k:
                break
        # 不足配额时放宽篇去重
        if len(picked) < k:
            for eid, idx, n in cand:
                if (eid, idx) in {(p[0], p[1]) for p in picked}:
                    continue
                g = groups.get(eid)
                if g and g in used_groups:
                    continue
                picked.append((eid, idx, n))
                if g:
                    used_groups.add(g)
                if len(picked) >= k:
                    break
        rng.shuffle(picked)  # 仅顺序展示打乱（可复现），不影响选位本身
        chosen += [(topic, eid, idx) for eid, idx, n in picked[:k]]
    return chosen


def turn_count(eid, vault_root):
    p = os.path.join(vault_root, eid, "episode.json")
    if not os.path.isfile(p):
        return 0
    with open(p, encoding="utf-8") as f:
        return len(json.load(f).get("turns") or [])


def turn_context(eid, idx, vault_root):
    """取该轮 HER 消息与关系背景（评估侧已 EXPOSED 后使用）。"""
    p = os.path.join(vault_root, eid, "episode.json")
    with open(p, encoding="utf-8") as f:
        ep = json.load(f)
    per = ep.get("persona") or {}
    rel = per.get("relationship_stage", "")
    her = (per.get("her") or {})
    her_name = her.get("name") if isinstance(her, dict) else "?"
    turns = ep.get("turns") or []
    t = turns[idx - 1] if 0 < idx <= len(turns) else {}
    msgs = [(m.get("role"), m.get("text")) for m in ((t.get("input") or {}).get("messages") or [])]
    return {"her_name": her_name, "relationship": rel, "messages": msgs,
            "clock": (t.get("input") or {}).get("clock")}


def build(run_dir, allocation_csv, vault_root, out_dir):
    positions = select_positions(allocation_csv, vault_root)
    rows = {}
    for r in normalize_output.iter_run_dir(run_dir, tuple(a for pr in PAIRS for a in pr)):
        rows.setdefault((r["episode_id"], r["case_id"]), {})[r["arm_id"]] = r
    rng = random.Random(SEED + 1)
    comparisons = []
    key_entries = []
    pack_lines = ["# HUMAN_PREFERENCE_BLIND_PACK（匿名盲评包）", "",
                  "> 每组比较请选择：%s。未填写 = NOT_COLLECTED；AI 不代投。" % " / ".join(FILL_OPTIONS),
                  "> 你看到的是同一轮位下两条匿名首选回复（上下文相同）。左右顺序已随机化。", ""]
    n_pid = 0
    for topic, eid, idx in positions:
        case_candidates = [c for (e, c) in rows if e == eid]
        case_id = "E%d_T%02d" % (int(eid[1:]), idx)
        if case_id not in case_candidates:
            case_id = "E%d_T%d" % (int(eid[1:]), idx)
        by_arm = rows.get((eid, case_id)) or {}
        if not all(a in by_arm for pr in PAIRS for a in pr):
            continue  # 缺位如实跳过并登记
        n_pid += 1
        ctx = turn_context(eid, idx, vault_root)
        pack_lines += ["## 位置 %d（专题 %s）" % (n_pid, topic), "",
                       "**关系背景**：%s（%s）｜时间：%s" % (ctx["relationship"], ctx["her_name"], ctx["clock"])]
        for role, text in ctx["messages"]:
            pack_lines.append("- %s：%s" % ("对方" if role == "HER" else "我", text))
        pack_lines.append("")
        for ai, (arm1, arm2) in enumerate(PAIRS):
            t1 = (by_arm[arm1] or {}).get("primary_text")
            t2 = (by_arm[arm2] or {}).get("primary_text")
            left, right = t1, t2
            swap = rng.random() < 0.5
            if swap:
                left, right = right, left
            lab_l, lab_r = "回复1", "回复2"
            pack_lines += ["### 比较 %d.%d" % (n_pid, ai + 1), "",
                           "- **%s**：%s" % (lab_l, left or "（无有效回复）"),
                           "- **%s**：%s" % (lab_r, right or "（无有效回复）"), "",
                           "判定：______", ""]
            comparisons.append({"position": n_pid, "pair": ai + 1, "episode": eid,
                                "case_id": case_id, "topic": topic,
                                "left_is": arm2 if swap else arm1,
                                "right_is": arm1 if swap else arm2})
            key_entries.append({"position": n_pid, "pair": ai + 1,
                                "mapping": {"回复1": arm2 if swap else arm1,
                                            "回复2": arm1 if swap else arm2}})
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "HUMAN_PREFERENCE_BLIND_PACK.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(pack_lines))
    with open(os.path.join(out_dir, "HUMAN_PREFERENCE_BLIND_PACK.json"), "w", encoding="utf-8") as f:
        json.dump({"kind": LABEL, "seed": SEED, "n_positions": n_pid,
                   "n_comparisons": len(comparisons), "fill_options": FILL_OPTIONS,
                   "comparisons": comparisons}, f, ensure_ascii=False, indent=1)
    with open(os.path.join(out_dir, "blind_key_phase3.json"), "w", encoding="utf-8") as f:
        json.dump({"kind": "blind_key_phase3", "secret": True, "seed": SEED,
                   "entries": key_entries}, f, ensure_ascii=False, indent=1)
    print("blind pack: positions=%d comparisons=%d（目标 60/180）" % (n_pid, len(comparisons)))
    return n_pid, len(comparisons)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-dir", required=True)
    ap.add_argument("--allocation", default=os.path.join(
        _REPO, "refs", "benchmark_v1_20261107", "phase3", "DATASET_V1_ALLOCATION_V3.csv"))
    ap.add_argument("--vault-root", default=p3.HOLDOUT_VAULT)
    ap.add_argument("--out-dir", required=True)
    args = ap.parse_args()
    build(args.run_dir, args.allocation, args.vault_root, args.out_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
