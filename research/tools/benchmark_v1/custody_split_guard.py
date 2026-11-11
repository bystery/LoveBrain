#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""custody_split_guard.py — 正式 split 定稿后的保管×分区强制片检查
（Benchmark V1 Phase 2 §8.1 交付；SPLIT_AND_CUSTODY_PLAN.md §0 谱系组规则统一的
工具与测试补齐件，与 NEW_STORY_SPEC_V1.md 记账粒度条款配套）。

与 lineage_check.py 的分工（同一派生逻辑，两个入口）：
  lineage_check.py       — 谱系组派生 + 跨分区核查；输入 = stories_v2 manifest +
                           显式 episode_id,split 两列 CSV（它读不了带 11 列表头和
                           #QUOTA 脚注行的正式分派表）。
  custody_split_guard.py — 正式分派表（DATASET_V1_FINAL_ALLOCATION.csv）逐批强制片，
                           承接 lineage_check 的 scan_stories_root / build_groups /
                           read_exposure（importlib 按绝对路径加载，规则不分叉）。

检查项：
  G1 group_cross_split      每谱系组内所有成员 split 一致。组成员 = 分派表
                            lineage_group_id 声明组 ∪ manifest source 派生组
                            （母本/续写、CF 变体均适用；派生组可补声明组漏登记，
                            如 E121 无分派行而 LRE121 声明 LIN-E121 单例的情形）。
  G2 twin_cross_split       twin_group 列不跨分区（CF 孪生同分区）。
  G3 holdout_*              split=HOLDOUT 的篇：本体与谱系组全部成员
                            （资格按组算，SPLIT_AND_CUSTODY_PLAN §0 R1）——
                            a) exposure.json：experiments_used 必须为空、dev_exposed
                               必须为 false；
                            b) custody 账本 STORY_CUSTODY_LEDGER.json（append-only
                               只读）：lifecycle 不得为 EXPOSED、consumed_by 必须为空。
                            账本未登记的 HOLDOUT 本体 = 无法证明未消费 → 违规；
                            组内兄弟篇未登记且无 exposure.json → 警告。
                            注：账本 events 含治理事件（generation_isolation_ack /
                            review-fix 等），只有 lifecycle/consumed_by 是消费记录，
                            本工具不把 events 计作消费。
  G4 pending_count_mismatch PENDING 行不参与 G1–G3；数量核对：--expected-pending
                            给定时必须相等（当前临时分派态 = 157；定稿后传 0 或省略）。

输入（全部只读）：正式分派表 CSV（episode_id,kind,...,split,lineage_group_id,twin_group,
... 表头，# 开头行为脚注）、stories_v2 根目录（manifest + exposure.json 元数据两字段）、
custody 账本 JSON。绝不读 episode 正文、B 线目录、GOLD_SIDECAR、logs/、candidates/。

输出：人类可读摘要；--json 输出完整报告。
退出码：0 = 无违规；1 = 有违规；2 = 输入错误。
纯标准库；零模型调用；不写任何被查对象（只读工具）。
"""
import argparse
import csv
import datetime
import importlib.util
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_ALLOCATION = os.path.join(REPO, "refs", "benchmark_v1_20261107", "phase2",
                                  "DATASET_V1_FINAL_ALLOCATION.csv")
DEFAULT_STORIES_ROOT = os.path.join(REPO, "benchmark", "stories_v2")
DEFAULT_LEDGER = os.path.join(REPO, "refs", "experiment_protocol_20261023",
                              "global_harness_program_20261030",
                              "STORY_CUSTODY_LEDGER.json")

SPLITS = ("DEV", "SELECTION", "HOLDOUT")
PENDING = "PENDING"
ALLOWED_SPLITS = SPLITS + (PENDING,)
REQUIRED_COLS = ("episode_id", "split", "lineage_group_id", "twin_group")

_RULE_G1 = ("谱系组成员 split 一致（母本/续写不跨 DEV/SELECTION/HOLDOUT；"
            "反事实变体不跨分区：同组同区或同组整体不入选）——SPLIT_AND_CUSTODY_PLAN §0")
_RULE_G2 = "CF 孪生 twin_group 不拆分区（同组同区）——SPLIT_AND_CUSTODY_PLAN §0"
_RULE_G3 = ("使用记录逐篇记、unseen 资格按谱系组算：HOLDOUT 篇及其组内成员"
            "experiments_used 须为空、dev_exposed 须为 false、账本不得 EXPOSED/有 consumed_by"
            "——SPLIT_AND_CUSTODY_PLAN §0 R1")


def load_lineage_check():
    """按绝对路径加载同目录 lineage_check.py（python -I 下 sys.path 不含脚本目录，
    importlib 文件加载不受影响），保证组派生规则与 lineage_check 单一来源。"""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "lineage_check.py")
    if not os.path.isfile(path):
        raise ImportError("lineage_check.py 不存在: %s（组派生规则要求与其同源）" % path)
    spec = importlib.util.spec_from_file_location("_lineage_check_for_guard", path)
    mod = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = mod
    spec.loader.exec_module(mod)
    return mod


# ---------- 分派表 ----------

def read_allocation(path):
    """正式分派表 → 行列表。# 开头行为脚注（QUOTA 汇总），空行跳过；
    非#且 split 非法 / 缺 episode_id / 重复 episode_id = 输入错误。"""
    with open(path, "r", encoding="utf-8-sig", newline="") as f:
        raw = list(csv.reader(f))
    if not raw:
        raise ValueError("分派表为空: %s" % path)
    header = [c.strip() for c in raw[0]]
    missing = [c for c in REQUIRED_COLS if c not in header]
    if missing:
        raise ValueError("分派表缺必需列: %s（现有表头 %s）" % (missing, header))
    idx = {c: i for i, c in enumerate(header)}
    rows, seen = [], {}
    for i, r in enumerate(raw[1:], 2):
        cells = [c.strip() for c in r]
        if not any(cells):
            continue
        if cells[0].startswith("#"):
            continue
        if len(cells) < len(header):
            raise ValueError("分派表第 %d 行列数不足且非 # 脚注行: %r" % (i, r))
        sp = cells[idx["split"]].upper()
        if sp not in ALLOWED_SPLITS:
            raise ValueError("分派表第 %d 行 split 非法 %r（允许 %s）"
                             % (i, cells[idx["split"]], "/".join(ALLOWED_SPLITS)))
        eid = cells[idx["episode_id"]]
        if not eid:
            raise ValueError("分派表第 %d 行缺 episode_id" % i)
        if eid in seen:
            raise ValueError("分派表中 %s 重复出现（第 %d 行与第 %d 行）" % (eid, seen[eid], i))
        seen[eid] = i
        rows.append({"episode_id": eid, "split": sp, "row": i,
                     "lineage_group_id": cells[idx["lineage_group_id"]],
                     "twin_group": cells[idx["twin_group"]]})
    return rows


# ---------- 合并谱系组（声明组 ∪ 派生组） ----------

def _union_find():
    parent = {}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra
        return ra

    return find, union


def merged_groups(rows, derived):
    """返回 (groups, group_of, conflicts)。
    groups: {group_label: set(episode_ids)} —— lineage_group_id 声明组与
    manifest 派生组（LIN-*）经 union 合并后的连通分量；标签优先声明组名。
    group_of: {episode_id: group_label}（仅组内成员）。
    conflicts: [str] —— 声明组把同一派生组拆成多个 lineage_group_id 时登记
    （组一致性检查仍按合并后分量执行，冲突仅作警告）。"""
    find, union = _union_find()
    for gid, members in sorted(derived.items()):
        ms = sorted(members)
        for m in ms[1:]:
            union(ms[0], m)
    csv_members = {}
    for r in rows:
        gid = r["lineage_group_id"]
        if gid:
            csv_members.setdefault(gid, set()).add(r["episode_id"])
    for gid, members in sorted(csv_members.items()):
        ms = sorted(members)
        for m in ms[1:]:
            union(ms[0], m)
    comp_members, comp_csv_gids, comp_derived_gids = {}, {}, {}
    for gid, members in sorted(csv_members.items()):
        root = find(next(iter(members)))
        comp_members.setdefault(root, set()).update(members)
        comp_csv_gids.setdefault(root, set()).add(gid)
    for gid, members in sorted(derived.items()):
        root = find(next(iter(members)))
        comp_members.setdefault(root, set()).update(members)
        comp_derived_gids.setdefault(root, set()).add(gid)
    groups, group_of, conflicts = {}, {}, []
    for root, members in sorted(comp_members.items()):
        csv_g = sorted(comp_csv_gids.get(root, ()))
        der_g = sorted(comp_derived_gids.get(root, ()))
        label = csv_g[0] if csv_g else (der_g[0] if der_g else "GRP-%s" % root)
        groups[label] = set(members)
        for m in members:
            group_of[m] = label
        if len(csv_g) > 1:
            conflicts.append("派生组 %s 的成员在分派表中被声明为多个 lineage_group_id "
                             "(%s)，已按合并分量检查" % (der_g[0] if der_g else root,
                                                       " / ".join(csv_g)))
    return groups, group_of, conflicts


# ---------- 主检查 ----------

def run_guard(allocation_csv, stories_root, ledger_path=None, expected_pending=None):
    rows = read_allocation(allocation_csv)
    splits = {r["episode_id"]: r["split"] for r in rows if r["split"] != PENDING}
    pending = [r["episode_id"] for r in rows if r["split"] == PENDING]

    lc = load_lineage_check()
    edges, exposure_map, discovered = lc.scan_stories_root(stories_root)
    derived = lc.build_groups(edges)
    groups, group_of, conflicts = merged_groups(rows, derived)
    for c in conflicts:
        warnings.append(c)

    ledger_stories = {}
    ledger_loaded = False
    if ledger_path:
        if not os.path.isfile(ledger_path):
            raise FileNotFoundError("custody 账本不存在: %s" % ledger_path)
        with open(ledger_path, "r", encoding="utf-8") as f:
            led = json.load(f)
        ledger_stories = led.get("stories") or {}
        ledger_loaded = True

    violations, warnings = [], []

    def add(vtype, group, episode, detail):
        violations.append({"type": vtype, "group": group, "episode": episode,
                           "detail": detail})

    # G1 组内 split 一致（PENDING 行已排除在 splits 外，不参与比较）
    pending_set = set(pending)
    for label in sorted(groups):
        members = sorted(groups[label])
        assigned = {m: splits[m] for m in members if m in splits}
        if len(set(assigned.values())) > 1:
            add("group_cross_split", label, "",
                {"assigned": assigned, "episodes": members, "rule": _RULE_G1})
        pend_in = sorted(m for m in members if m in pending_set)
        if pend_in and assigned:
            warnings.append("组 %s 仍有未定稿成员 %s（PENDING 不参与检查，定稿前勿冻结）"
                            % (label, " ".join(pend_in)))

    # G2 twin_group 不跨分区
    twin_groups = {}
    for r in rows:
        if r["twin_group"]:
            twin_groups.setdefault(r["twin_group"], set()).add(r["episode_id"])
    for tg in sorted(twin_groups):
        members = sorted(twin_groups[tg])
        assigned = {m: splits[m] for m in members if m in splits}
        if len(set(assigned.values())) > 1:
            add("twin_cross_split", "TWIN-%s" % tg, "",
                {"assigned": assigned, "episodes": members, "rule": _RULE_G2})

    # G3 HOLDOUT：本体 + 谱系组全体成员的未消费证明（资格按组算）
    for eid in sorted(e for e, sp in splits.items() if sp == "HOLDOUT"):
        label = group_of.get(eid)
        members = sorted(groups[label]) if label else [eid]
        for m in members:
            xp = exposure_map.get(m)
            if xp is not None:
                info = lc.read_exposure(xp)
                if info.get("error"):
                    warnings.append("exposure 读取失败 %s: %s" % (m, info["error"]))
                else:
                    if info["experiments_used"]:
                        add("holdout_group_consumed_exposure", label or "(no-group)", eid,
                            {"member": m, "experiments_used": info["experiments_used"],
                             "dev_exposed": info["dev_exposed"], "rule": _RULE_G3})
                    if info["dev_exposed"]:
                        add("holdout_dev_exposed", label or "(no-group)", eid,
                            {"member": m, "experiments_used": info["experiments_used"],
                             "rule": _RULE_G3})
            if not ledger_loaded:
                if xp is None:
                    warnings.append("HOLDOUT 组 %s 成员 %s 无 exposure.json 且未提供账本，"
                                    "无法核消费" % (label or "(no-group)", m))
                continue
            st = ledger_stories.get(m)
            if st is None:
                if m == eid:
                    add("holdout_unregistered_in_custody_ledger", label or "(no-group)", eid,
                        {"member": m, "rule": "HOLDOUT 本体未登记于 custody 账本，"
                                              "无法证明未消费（先 story_custody.py seed 再定稿）"})
                elif xp is None:
                    warnings.append("HOLDOUT 组 %s 成员 %s 未登记于 custody 账本且无 "
                                    "exposure.json，无法核消费" % (label or "(no-group)", m))
                continue
            if st.get("lifecycle") == "EXPOSED":
                add("holdout_group_consumed_ledger", label or "(no-group)", eid,
                    {"member": m, "lifecycle": st.get("lifecycle"),
                     "consumed_by": st.get("consumed_by") or [], "rule": _RULE_G3})
            elif st.get("consumed_by"):
                add("holdout_group_consumed_ledger", label or "(no-group)", eid,
                    {"member": m, "lifecycle": st.get("lifecycle"),
                     "consumed_by": st.get("consumed_by") or [], "rule": _RULE_G3})

    # G4 PENDING 数量核对（不参与 G1–G3）
    if expected_pending is not None and len(pending) != expected_pending:
        add("pending_count_mismatch", "(allocation)", "",
            {"pending_actual": len(pending), "pending_expected": expected_pending,
             "rule": "PENDING 行数量核对（当前临时分派态应为 157；定稿后传 0 或省略）"})

    # 提示（非违规）：DEV/SELECTION 篇未登记账本（新题待 seed，不算违规）
    unreg = sorted(e for e, sp in splits.items()
                   if sp in ("DEV", "SELECTION") and e not in ledger_stories)
    for e in unreg:
        warnings.append("DEV/SELECTION 篇 %s 未登记于 custody 账本（新题待 seed，不影响本表）" % e)

    report = {
        "tool": "custody_split_guard",
        "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "allocation": allocation_csv,
        "stories_root": stories_root,
        "ledger": ledger_path or "",
        "expected_pending": expected_pending,
        "lineage_groups": {k: sorted(v) for k, v in sorted(groups.items())},
        "twin_groups": {k: sorted(v) for k, v in sorted(twin_groups.items())},
        "splits": splits,
        "pending": sorted(pending),
        "violations": sorted(violations, key=lambda v: (v["type"], v["group"], v["episode"])),
        "warnings": warnings,
        "summary": {"rows": len(rows), "assigned": len(splits), "pending": len(pending),
                    "groups": len(groups), "twin_groups": len(twin_groups),
                    "unregistered_dev_selection": len(unreg),
                    "violations": len(violations), "warnings": len(warnings)},
    }
    return report


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except Exception:  # noqa: BLE001
            pass
    ap = argparse.ArgumentParser(description="正式 split 定稿后的保管×分区强制片检查（只读，纯标准库）")
    ap.add_argument("--allocation", default=DEFAULT_ALLOCATION,
                    help="正式分派表 CSV（默认 phase2 DATASET_V1_FINAL_ALLOCATION.csv）")
    ap.add_argument("--stories-root", default=DEFAULT_STORIES_ROOT)
    ap.add_argument("--ledger", default=DEFAULT_LEDGER,
                    help="STORY_CUSTODY_LEDGER.json（append-only 只读；传空字符串跳过账本核对）")
    ap.add_argument("--expected-pending", type=int, default=None,
                    help="PENDING 行数核对目标（当前临时分派态 157；不给则只报告）")
    ap.add_argument("--json", action="store_true", help="输出完整 JSON 报告")
    args = ap.parse_args(argv)
    try:
        report = run_guard(args.allocation, args.stories_root,
                           args.ledger or None, args.expected_pending)
    except (FileNotFoundError, ValueError, ImportError, json.JSONDecodeError) as e:
        print("[custody_split_guard] 输入错误: %s" % e, file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        s = report["summary"]
        print("custody_split_guard: 篇(分派表)=%d 已定稿=%d PENDING=%d 组=%d 孪生组=%d "
              "违规=%d 警告=%d"
              % (s["rows"], s["assigned"], s["pending"], s["groups"], s["twin_groups"],
                 s["violations"], s["warnings"]))
        for v in report["violations"]:
            print("VIOLATION [%s] group=%s episode=%s: %s"
                  % (v["type"], v["group"], v["episode"],
                     json.dumps(v["detail"], ensure_ascii=False)))
        for w in report["warnings"]:
            print("WARNING: %s" % w)
    return 1 if report["violations"] else 0


if __name__ == "__main__":
    sys.exit(main())
