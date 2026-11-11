# -*- coding: utf-8 -*-
"""Phase 3 · 40 锚校准包修正构建器（P3-3.4 执行件，2026-11-09）。

指导书 P3-3.4：A01–A04 旧 T02 锚缺真实输出（旧 T02 三篇弱且不在 pilot2），改用
E305–E308 的**额外非重复轮位**补齐 4 个有效 T02 校准锚点。要求：保留 40 锚总数；
T02 仍 12 锚；全部真实模型回复（pilot2 live）；删除 Mock 内容；盲化 Direct/CURRENT
名称（展示=臂A/臂B，映射单独登记）；0/1/2 行为分 + 愿发选择 + 平局/全不发送；
未填=NOT_COLLECTED；AI 不代投。

实现：薄包装 build_human_review_queue（原工具零改动）：
  1. monkeypatch OLD_T02_POOL → E305–E308（wave4 目录，有 live 真实回复）；
  2. select_anchors 正常跑（T02 短板由 E305–E308 各补 1 锚，source 仍标
     old_exposed_T02）；
  3. 补位锚**非重复轮位替换**：候选位 [3, n//4, n//2+1, 3n//4, n-2] 取与该篇已用
     轮位距离最大者（并列取靠前；无随机源，可复算），source 改标 wave4_t02_extra；
  4. 展示层盲化：锚点内两臂按 (锚序 % 2) 交换命名 臂A/臂B；原始映射只写
     blind_key_anchor_phase3.json（不入展示包）；
  5. 复用原工具 build_anchor_md 渲染每锚详情；json 顶层记 fill_discipline。

用法：
  python -I tools/benchmark_v1/build_human_review_queue_phase3.py \
      --run-dir runs/pilot2_wave4_live_b --out-dir refs/benchmark_v1_20261107/phase3
"""
import argparse
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
import build_human_review_queue as hq  # noqa: E402

REPO = hq.REPO_ROOT
EXTRA_T02 = ("E305", "E306", "E307", "E308")


def farthest_turn(n_turns, used):
    cands = sorted({c for c in (3, max(1, n_turns // 4), n_turns // 2 + 1,
                                max(1, 3 * n_turns // 4), max(1, n_turns - 2)) if 1 <= c <= n_turns})
    return max(cands, key=lambda c: (min(abs(c - u) for u in used), -c))


def main():
    ap = argparse.ArgumentParser(description="Phase3 40 锚校准包（真实回复 + E305-E308 补位 + 盲化）")
    ap.add_argument("--run-dir", required=True)
    ap.add_argument("--out-dir", default=os.path.join(
        REPO, "refs", "benchmark_v1_20261107", "phase3"))
    ap.add_argument("--max-per-episode", type=int, default=2)
    args = ap.parse_args()
    if args.max_per_episode < 1:
        raise SystemExit("--max-per-episode 必须 >= 1")

    # 1. patch 补位池（原工具零改动）
    hq.OLD_T02_POOL = [(e, os.path.join(hq.WAVE4_DIR, e)) for e in EXTRA_T02]
    plan, shortfall = hq.select_anchors(args.max_per_episode)

    # 2. T02 短板（指导书语义=额外非重复轮位）：E305–E308 允许第 3 锚，
    #    轮位=与该篇已用轮距离最远的确定性候选位；source=wave4_t02_extra
    shortfall.pop("T02", None)
    by_ep_used = {}
    for a in plan:
        if a["topic"] == "T02":
            by_ep_used.setdefault(a["episode"], set()).add(a["turn_index"])
    for eid in EXTRA_T02:
        used = by_ep_used.get(eid) or set()
        root = os.path.join(hq.WAVE4_DIR, eid)
        ep = hq.load_json(os.path.join(root, "episode.json"), eid + " episode.json")
        n = len(ep.get("turns") or [])
        plan.append({"topic": "T02", "episode": eid, "turn_index": farthest_turn(n, used),
                     "source": "wave4_t02_extra", "root": root})
        by_ep_used.setdefault(eid, set()).add(plan[-1]["turn_index"])

    # 3. 重排序 + 重编锚号（与原工具同一规则：TOPIC_QUOTA 顺序内 (episode, turn)）
    order = {t: i for i, (t, _) in enumerate(hq.TOPIC_QUOTA)}
    plan.sort(key=lambda a: (order[a["topic"]], a["episode"], a["turn_index"]))
    by_ep = {}
    for a in plan:
        by_ep.setdefault(a["episode"], []).append(a["turn_index"])
    for a in plan:
        a["slot"] = by_ep[a["episode"]].index(a["turn_index"]) + 1
        a["episode_anchors"] = len(by_ep[a["episode"]])
    for i, a in enumerate(plan, 1):
        a["anchor_id"] = "A%02d" % i

    # 3. run_index（真实回复；三 live 分片合并——E301-E319=b、E320-E338=c、old=oldT09）
    run_index = {}
    for part in ("pilot2_wave4_live_b", "pilot2_wave4_live_c", "pilot2_oldT09_live"):
        rd = os.path.join(REPO, "runs", part)
        if os.path.isdir(rd):
            for k, v in hq.build_run_index(rd).items():
                run_index.setdefault(k, {}).update(v)
    os.makedirs(args.out_dir, exist_ok=True)
    md = ["# Benchmark V1 Phase 3 · 人工校准锚点包（40 锚，真实回复，匿名臂）", ""]
    md.append("> 生成：build_human_review_queue_phase3.py（薄包装，确定性公式，无随机源）。")
    md.append("> T02 补位：%s 各 +1 锚（轮位=与该篇已用轮距离最远的确定性候选位）。" % "、".join(EXTRA_T02))
    md.append("> run_dir：%s（两臂文本原样透传，无质量判断）。" % os.path.abspath(args.run_dir))
    md.append("> **匿名纪律：两臂显示为 臂A/臂B；真实映射只写 blind_key_anchor_phase3.json（不入本包）。**")
    md.append("> 填写：判定 PASS/FAIL/UNSURE（T02 对照行为锚 0/1/2 档）＋ 更愿发送（臂A/臂B/都不错/都不愿发送）"
              "＋ 一句理由。未填写 = NOT_COLLECTED；AI 不代投。")
    md.append("> 锚点配额：" + "、".join("%s×%d" % (t, k) for t, k in hq.TOPIC_QUOTA) + "，共 40。")
    md.append("")
    md.append("| 锚点 | 专题 | 位置 | 来源 | 臂A | 臂B |")
    md.append("|---|---|---|---|---|---|")
    json_anchors, dist = [], {}
    key_entries = []
    for i, a in enumerate(plan, 1):
        md_lines, pkg = hq.build_anchor_md(a, run_index, args.max_per_episode)
        # 盲化：pkg.arms_texts 键 DIRECT/CURRENT → 臂A/臂B（按锚序奇偶交换）
        swap = (i % 2 == 0)
        raw = {"臂A": pkg["arms_texts"]["DIRECT"], "臂B": pkg["arms_texts"]["CURRENT"]}
        if swap:
            raw = {"臂A": pkg["arms_texts"]["CURRENT"], "臂B": pkg["arms_texts"]["DIRECT"]}
        avail = {"臂A": pkg["arms_available"]["DIRECT"] if not swap else pkg["arms_available"]["CURRENT"],
                 "臂B": pkg["arms_available"]["CURRENT"] if not swap else pkg["arms_available"]["DIRECT"]}
        key_entries.append({"anchor_id": a["anchor_id"], "case_id": pkg["case_id"],
                            "臂A": "CURRENT" if swap else "DIRECT",
                            "臂B": "DIRECT" if swap else "CURRENT"})
        # 每锚详情节内的两臂文本替换为匿名标签
        sec = "\n".join(md_lines)
        sec = sec.replace("**DIRECT**", "**臂A**").replace("**CURRENT**", "**臂B**")
        if swap:
            # 详情节文本顺序保持（DIRECT 段在前），但标签映射换：DIRECT→臂B
            sec = sec.replace("**臂A**", "__TMP__").replace("**臂B**", "**臂A**").replace("__TMP__", "**臂B**")
        sections_md = sec
        dist[a["topic"]] = dist.get(a["topic"], 0) + 1
        md.append("| %s | %s | %s（%s 第%d轮） | %s | %s | %s |" % (
            a["anchor_id"], a["topic"], pkg["case_id"], a["episode"], a["turn_index"],
            a["source"], "有" if avail["臂A"] else "待", "有" if avail["臂B"] else "待"))
        md.append("")
        md.append(sections_md)
        json_anchors.append({
            "anchor_id": a["anchor_id"], "topic": a["topic"],
            "topic_label": hq.TOPIC_LABELS.get(a["topic"], ""),
            "episode": a["episode"], "case_id": pkg["case_id"],
            "turn_index": a["turn_index"], "turn_count": pkg["turn_count"],
            "source": a["source"],
            "arms_texts": raw, "arms_available": avail,
            "fill_status": "PENDING_USER",
            "fill_options": {"判定": ["PASS", "FAIL", "UNSURE"],
                             "更愿发送": ["臂A", "臂B", "都不错", "都不愿发送"],
                             "行为分_T02": ["0", "1", "2"]},
        })
    with open(os.path.join(args.out_dir, "HUMAN_REVIEW_QUEUE_PHASE3.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md))
    data = {"kind": "benchmark_v1_human_review_queue_phase3", "version": "hrq_v2_phase3",
            "generated_from_run": os.path.abspath(args.run_dir),
            "n_anchors": len(json_anchors), "distribution": dist,
            "fill_discipline": "PENDING_USER=人工填写，工具不代填；未填=NOT_COLLECTED",
            "t02_extra_rule": "E305-E308 各补 1 锚；轮位=候选 [3, n//4, n//2+1, 3n//4, n-2] "
                              "中与该篇已用轮距离最大者（并列取靠前，无随机源）",
            "blinding": "臂A/臂B 按锚序奇偶交换；映射在 blind_key_anchor_phase3.json",
            "anchors": json_anchors, "shortfall": shortfall}
    with open(os.path.join(args.out_dir, "HUMAN_REVIEW_QUEUE_PHASE3.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    with open(os.path.join(args.out_dir, "blind_key_anchor_phase3.json"), "w", encoding="utf-8") as f:
        json.dump({"kind": "blind_key_anchor_phase3", "secret": True,
                   "entries": key_entries}, f, ensure_ascii=False, indent=1)
    n_t02 = dist.get("T02", 0)
    print("phase3 anchor queue: total=%d T02=%d shortfall=%s → %s"
          % (len(json_anchors), n_t02, shortfall, args.out_dir))
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
