#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""lineage_check.py — 谱系组隔离规则统一后的机械核查（Benchmark V1 Phase 2 §8.1）。

裁定（2026-11-08，见 refs/benchmark_v1_20261107/phase1/SPLIT_AND_CUSTODY_PLAN.md
头部修订节）：**使用记录逐篇记，独立性资格按谱系组算。** 本工具只读核查三项：

  C1 cross_split                  同一 lineage 组的成员被分区映射分到不同 split
                                  （DEV/SELECTION/HOLDOUT）→ 违规。覆盖
                                  "母本/续写不跨区"（LRE 长剧组）与
                                  "反事实变体不跨区/CF 孪生不拆分区"（CF 组）。
  C2 b_line_related_in_dev_sel    B 线成员所在谱系组（含 B 线成员本体）任一成员
                                  进入 DEV/SELECTION → 违规（规则 R2：B 线关联组
                                  整体退出 dev/selection）。
  C3 holdout_group_consumed       被分到 HOLDOUT 的篇，其所在组（无组则其本体）
                                  任一成员 exposure.json experiments_used 非空
                                  → 违规（组成员被消费 → 整组失去 unseen 资格）。

输入（全部只读）：
  --stories-root      stories_v2 根目录。扫描其一级 wave/stage 子目录中的
                      *MANIFEST*.json（含 stories[] 数组者），从 story 条目的
                      source / source_story / source_id 字段派生谱系边
                      （LRE 长剧续写 kind=long_drama_ext 用 source；
                      CF 孪生用 source_story）；再扫描各 <wave>/<EID>/exposure.json
                      （仅 experiments_used/dev_exposed 两字段）。
  --split-csv         分区映射 CSV：episode_id,split（可有表头；split ∈
                      DEV/SELECTION/HOLDOUT，大小写不敏感）。
  --b-line-manifest   B_LINE_MANIFEST.json（**仅读元数据 episodes 字典的键**，
                      绝不读 benchmark/b_line/ 下任何 episode 正文）。

输出：人类可读摘要；--json 输出完整报告到 stdout。
退出码：0 = 无违规；1 = 有违规；2 = 输入错误。

纯标准库；零模型调用；不写任何被查对象（只读工具）。
"""
import argparse
import csv
import datetime
import json
import os
import re
import sys

SPLITS = ("DEV", "SELECTION", "HOLDOUT")
B_LINE_FORBIDDEN_SPLITS = ("DEV", "SELECTION")
SOURCE_KEYS = ("source", "source_story", "source_id")
# wave1_ext/wave3_ext 部分旧代 manifest（kind=story_v2_wave*_ext_manifest）的条目
# 无 machine 字段 source，母本线索有两代写法：
#   代际一（8 篇，LRE117/119/128/148 型）：标题含 "（E1xx 原位续写 N 轮扩展版）"。
#   代际二（10 篇，LRE121/125/126/… 型）：标题无母本线索，仅剩命名约定
#     LRE<digits> ↔ E<digits>（LINEAGE_LEAKAGE_REPORT.md §1 既定口径：
#     "LRExxx = Exxx 的原位续写"，组号 = LIN-<母本篇 ID>）。
# 确定性派生规则按可信度分三档，逐边登记 derivation 供审计：
#   source_field（manifest machine 字段）> title_pattern（标题正则）> id_pattern（命名约定）。
TITLE_MOTHER_RE = re.compile(r"(E\d+)\s*原位续写")
LRE_ID_RE = re.compile(r"^LRE(\d+)$")


# ---------- stories_v2 只读扫描 ----------

def scan_stories_root(stories_root):
    """返回 (edges, exposure_map, discovered_ids)。
    edges: [{"mother", "member", "manifest", "derivation"}]（谱系边，union 成组；
            derivation ∈ source_field / title_pattern / id_pattern，审计可溯源）
    exposure_map: {episode_id: exposure.json 路径}（存在才登记）
    discovered_ids: manifest 里出现过的全部 story_id（含母本引用）。"""
    edges, exposure_map, discovered = [], {}, set()
    if not os.path.isdir(stories_root):
        raise FileNotFoundError("stories_root 不存在: %s" % stories_root)
    for wave in sorted(os.listdir(stories_root)):
        wdir = os.path.join(stories_root, wave)
        if not os.path.isdir(wdir):
            continue
        # wave/stage 目录名本身不设白名单（b_line 不在 stories_root 下；若在，
        # 本工具也不读其 episode 正文，只认 manifest 与 exposure.json）。
        for fn in sorted(os.listdir(wdir)):
            p = os.path.join(wdir, fn)
            if os.path.isfile(p) and fn.upper().endswith(".JSON") and "MANIFEST" in fn.upper():
                try:
                    with open(p, "r", encoding="utf-8") as f:
                        m = json.load(f)
                except Exception:
                    continue
                stories = m.get("stories") if isinstance(m, dict) else None
                if not isinstance(stories, list):
                    continue
                rel = "%s/%s" % (wave, fn)
                for s in stories:
                    if not isinstance(s, dict):
                        continue
                    sid = s.get("story_id")
                    if not (isinstance(sid, str) and sid):
                        continue
                    discovered.add(sid)
                    edge = None
                    for k in SOURCE_KEYS:
                        src = s.get(k)
                        if isinstance(src, str) and src:
                            edge = {"mother": src, "member": sid, "manifest": rel,
                                    "derivation": "source_field"}
                            break
                    if edge is None:
                        title = s.get("title")
                        tm = TITLE_MOTHER_RE.search(title) if isinstance(title, str) else None
                        if tm:
                            edge = {"mother": tm.group(1), "member": sid, "manifest": rel,
                                    "derivation": "title_pattern"}
                    if edge is None:
                        lm = LRE_ID_RE.match(sid)
                        if lm:
                            edge = {"mother": "E%s" % lm.group(1), "member": sid,
                                    "manifest": rel, "derivation": "id_pattern"}
                    if edge:
                        edges.append(edge)
        # 每篇 exposure.json（只读元数据两字段）
        for eid in sorted(os.listdir(wdir)):
            edir = os.path.join(wdir, eid)
            ep = os.path.join(edir, "exposure.json")
            if os.path.isdir(edir) and os.path.isfile(ep):
                exposure_map[eid] = ep
    return edges, exposure_map, discovered


def build_groups(edges):
    """谱系边 → 组：group_id = LIN-<母本>；链式边（A←B←C 型）用 union 合并。
    返回 {group_id: set(member_ids)}。无边 episode 不产生组（按单篇处理）。"""
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

    for e in edges:
        union(e["mother"], e["member"])
    groups = {}
    for e in edges:
        root = find(e["mother"])
        gid = "LIN-%s" % root
        groups.setdefault(gid, set()).update((e["mother"], e["member"]))
    return groups


def read_exposure(path):
    """只读 experiments_used / dev_exposed 两个字段（不碰其它内容）。"""
    try:
        with open(path, "r", encoding="utf-8") as f:
            x = json.load(f)
    except Exception as e:  # noqa: BLE001
        return {"error": "%s: %s" % (type(e).__name__, e)}
    return {"experiments_used": x.get("experiments_used") or [],
            "dev_exposed": bool(x.get("dev_exposed"))}


# ---------- 分区映射 CSV ----------

def read_split_csv(path):
    """episode_id,split → {episode_id: split(大写)}。首行为表头则跳过。"""
    splits = {}
    with open(path, "r", encoding="utf-8-sig", newline="") as f:
        for i, row in enumerate(csv.reader(f)):
            if not row or not row[0].strip():
                continue
            if i == 0 and [c.strip().lower() for c in row[:2]] == ["episode_id", "split"]:
                continue
            if len(row) < 2:
                raise ValueError("split CSV 第 %d 行缺 split 列: %r" % (i + 1, row))
            eid, sp = row[0].strip(), row[1].strip().upper()
            if sp not in SPLITS:
                raise ValueError("split CSV 第 %d 行 split 非法 %r（允许 %s）"
                                 % (i + 1, row[1], "/".join(SPLITS)))
            if eid in splits and splits[eid] != sp:
                raise ValueError("split CSV 中 %s 重复且冲突: %s vs %s"
                                 % (eid, splits[eid], sp))
            splits[eid] = sp
    return splits


# ---------- 主核查 ----------

def run_check(stories_root, split_csv, b_line_manifest=None):
    edges, exposure_map, discovered = scan_stories_root(stories_root)
    groups = build_groups(edges)
    splits = read_split_csv(split_csv)

    b_line_members = []
    if b_line_manifest:
        with open(b_line_manifest, "r", encoding="utf-8") as f:
            bl = json.load(f)
        eps = bl.get("episodes") or {}
        b_line_members = sorted(eps.keys() if isinstance(eps, dict) else eps)

    ep_group = {}   # episode_id -> group_id
    for gid, members in groups.items():
        for m in members:
            ep_group[m] = gid
    b_related_groups = sorted({ep_group[m] for m in b_line_members if m in ep_group})
    b_line_ungrouped = sorted(m for m in b_line_members if m not in ep_group)

    violations, warnings = [], []

    def add(vtype, group, detail):
        violations.append({"type": vtype, "group": group, "detail": detail})

    # C1 跨分区：组内成员 split 集合 >1 → 违规（母本/续写、CF 孪生均适用）
    for gid in sorted(groups):
        members = sorted(groups[gid])
        seen = {m: splits[m] for m in members if m in splits}
        if len(set(seen.values())) > 1:
            add("cross_split", gid,
                {"assigned": seen,
                 "rule": "母本/续写不跨 DEV/SELECTION/HOLDOUT；反事实变体不跨分区"})

    # C2 B 线关联组（含未入组的 B 线成员本体）不得进 DEV/SELECTION
    for gid in b_related_groups:
        hit = {m: splits[m] for m in sorted(groups[gid])
               if splits.get(m) in B_LINE_FORBIDDEN_SPLITS}
        if hit:
            add("b_line_related_in_dev_selection", gid,
                {"b_line_members": [m for m in sorted(groups[gid]) if m in b_line_members],
                 "assigned": hit,
                 "rule": "B 线关联组整体退出 dev/selection（规则 R2）"})
    for m in b_line_ungrouped:
        if splits.get(m) in B_LINE_FORBIDDEN_SPLITS:
            add("b_line_related_in_dev_selection", "(no-group)",
                {"b_line_members": [m], "assigned": {m: splits[m]},
                 "rule": "B 线成员本体不得进 dev/selection（规则 R2）"})

    # C3 HOLDOUT 组消费检查：组内任一成员 experiments_used 非空 → 违规
    checked_holdout = set()
    for eid in sorted(e for e, sp in splits.items() if sp == "HOLDOUT"):
        gid = ep_group.get(eid)
        members = sorted(groups[gid]) if gid else [eid]
        key = gid or "self:%s" % eid
        if key in checked_holdout:
            continue
        checked_holdout.add(key)
        for m in members:
            xp = exposure_map.get(m)
            if xp is None:
                if m not in discovered and m not in exposure_map:
                    warnings.append("HOLDOUT 组 %s 成员 %s 无 exposure.json（库内未发现，无法核消费）"
                                    % (key, m))
                continue
            info = read_exposure(xp)
            if info.get("error"):
                warnings.append("exposure 读取失败 %s: %s" % (m, info["error"]))
                continue
            if info["experiments_used"]:
                add("holdout_group_consumed", key,
                    {"holdout_episode": eid, "consumed_member": m,
                     "experiments_used": info["experiments_used"],
                     "dev_exposed": info["dev_exposed"],
                     "rule": "组成员被消费→整组失去 unseen 资格（使用记录逐篇记，资格按组算）"})
                break

    for eid in sorted(splits):
        if eid not in discovered and eid not in exposure_map and eid not in b_line_members:
            warnings.append("分区映射中的 %s 在 stories_v2 manifest/exposure 中均未发现" % eid)

    report = {
        "tool": "lineage_check",
        "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "stories_root": stories_root,
        "split_csv": split_csv,
        "b_line_manifest": b_line_manifest or "",
        "lineage_groups": {gid: sorted(m) for gid, m in sorted(groups.items())},
        "lineage_edges": edges,
        "splits": splits,
        "b_line_members": b_line_members,
        "b_line_related_groups": b_related_groups,
        "b_line_ungrouped_members": b_line_ungrouped,
        "violations": violations,
        "warnings": warnings,
        "summary": {"groups": len(groups), "episodes_in_csv": len(splits),
                    "b_line_members": len(b_line_members), "violations": len(violations),
                    "warnings": len(warnings)},
    }
    return report


def main(argv=None):
    # 输出固定 UTF-8（python -I 隔离模式忽略 PYTHONIOENCODING/PYTHONUTF8，
    # Windows 默认 ANSI 代码页会把中文报告写成 GBK；reconfigure 不受 -I 影响）。
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except Exception:  # noqa: BLE001
            pass
    ap = argparse.ArgumentParser(description="谱系组隔离机械核查（只读，纯标准库）")
    ap.add_argument("--stories-root", required=True)
    ap.add_argument("--split-csv", required=True)
    ap.add_argument("--b-line-manifest", default=None)
    ap.add_argument("--json", action="store_true", help="输出完整 JSON 报告")
    args = ap.parse_args(argv)
    try:
        report = run_check(args.stories_root, args.split_csv, args.b_line_manifest)
    except (FileNotFoundError, ValueError) as e:
        print("[lineage_check] 输入错误: %s" % e, file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        s = report["summary"]
        print("lineage_check: 组=%d 篇(CSV)=%d B线成员=%d 违规=%d 警告=%d"
              % (s["groups"], s["episodes_in_csv"], s["b_line_members"],
                 s["violations"], s["warnings"]))
        for v in report["violations"]:
            print("VIOLATION [%s] %s: %s" % (v["type"], v["group"],
                                             json.dumps(v["detail"], ensure_ascii=False)))
        for w in report["warnings"]:
            print("WARNING: %s" % w)
    return 1 if report["violations"] else 0


if __name__ == "__main__":
    sys.exit(main())
