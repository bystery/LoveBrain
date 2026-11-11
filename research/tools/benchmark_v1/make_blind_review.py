# -*- coding: utf-8 -*-
"""Benchmark V1 · 用户盲评包生成（指导书 §6.5）。

固定抽取 12 个位置（E032×4、E037×2、E191×3、LRE117×3），规则=每篇按时间顺序
等间距（公式 floor(j*(n+1)/(k+1)+0.5)，并列取靠前），避免挑选对某组有利的轮次。
每位置呈现最多 4 条匿名回复（固定种子 20261107 打乱，R1..R4）。
映射表只写运行目录 blind_key.json（不入交付目录）；人类偏好未收集 = NOT_COLLECTED，
Worker 不代投。
"""
import json
import os
import random
import sys
import zlib

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
import normalize_output  # noqa: E402
from four_arm_runner import find_episode_path, _REPO  # noqa: E402

SEED = 20261107
PLAN_SELECTION = {"E032": 4, "E037": 2, "E191": 3, "LRE117": 3}
ARMS = ("DIRECT", "COT", "LEGACY", "CURRENT")


def select_positions(episode_id, k):
    ep_path = find_episode_path(episode_id)
    with open(ep_path, "r", encoding="utf-8") as f:
        n = len(json.load(f).get("turns") or [])
    idxs = []
    for j in range(1, k + 1):
        idxs.append(int(j * (n + 1) / (k + 1) + 0.5))
    return sorted(set(idxs)), n


def build(run_dir, deliver_dir):
    rows = {}
    for r in normalize_output.iter_run_dir(run_dir, ARMS):
        rows.setdefault((r["episode_id"], r["case_id"]), {})[r["arm_id"]] = r
    selection = {}
    pack = ["# Pilot 用户盲评包（匿名）", "",
            "> 规则：每个位置请选出**最愿意实际发送**的一条（可多选“都不错”）；"
            "若全部不愿发送请整体标注；可写一句理由。未填写项 = NOT_COLLECTED。",
            "> 匿名标签 R1–R4 与组别的对应关系不在本文件内（防偏好污染）。", ""]
    for ep_id, k in PLAN_SELECTION.items():
        idxs, n = select_positions(ep_id, k)
        for ti in idxs:
            case_id = "%s_T%02d" % (ep_id, ti) if "%s_T%02d" % (ep_id, ti) in \
                {c for (e, c) in rows} else "%s_T%d" % (ep_id, ti)
            by_arm = rows.get((ep_id, case_id)) or {}
            options = []
            for arm in ARMS:
                r = by_arm.get(arm)
                if r and r.get("action") == "reply" and \
                        isinstance(r.get("primary_text"), str) and r["primary_text"].strip():
                    options.append((arm, r["primary_text"].strip()))
            if len(options) < 2:
                selection[case_id] = {"status": "SKIPPED_INSUFFICIENT_ARMS",
                                      "usable_arms": [a for a, _ in options]}
                continue
            rng = random.Random(SEED + zlib.crc32(case_id.encode("utf-8")))
            arms_shuffled = [a for a, _ in options]
            rng.shuffle(arms_shuffled)
            label_map = {"R%d" % (i + 1): a for i, a in enumerate(arms_shuffled)}
            selection[case_id] = {"labels": label_map, "status": "PENDING_USER",
                                  "user_choice": "NOT_COLLECTED",
                                  "multi_ok": "NOT_COLLECTED",
                                  "none_ok": "NOT_COLLECTED", "reason": "NOT_COLLECTED"}
            ep_path = find_episode_path(ep_id)
            with open(ep_path, "r", encoding="utf-8") as f:
                ep = json.load(f)
            turn = ep["turns"][ti - 1]
            msgs = (turn.get("input") or {}).get("messages") or []
            ctx_lines = ["[%s] %s" % (m.get("role"), m.get("text")) for m in msgs]
            note = (turn.get("input") or {}).get("note") or ""
            intent = (turn.get("input") or {}).get("intent") or ""
            pack.append("## %s（第 %d/%d 轮）" % (case_id, ti, n))
            pack.append("")
            pack.append("**本轮真实聊天：**")
            for cl in ctx_lines:
                pack.append("- %s" % cl)
            if note:
                pack.append("- 我的备注：%s" % note)
            if intent and not isinstance(intent, dict):
                pack.append("- 我的意图：%s" % intent)
            elif isinstance(intent, dict) and intent.get("enabled") and intent.get("text"):
                pack.append("- 我的意图：%s" % intent["text"])
            pack.append("")
            for lab in sorted(label_map):
                arm = label_map[lab]
                text = dict(options)[arm]
                pack.append("- **%s**: %s" % (lab, text.replace("\n", " ")))
            pack.append("")
            pack.append("**我的选择**：____　**多条不错**：____　**全部不愿发送**：☐　"
                        "**理由**：____")
            pack.append("")
    os.makedirs(deliver_dir, exist_ok=True)
    with open(os.path.join(deliver_dir, "PILOT_BLIND_REVIEW.md"), "w",
              encoding="utf-8", newline="\n") as f:
        f.write("\n".join(pack) + "\n")
    with open(os.path.join(deliver_dir, "PILOT_BLIND_SELECTION.json"), "w",
              encoding="utf-8") as f:
        json.dump({c: {k: v for k, v in s.items() if k != "labels"}
                   for c, s in selection.items()}, f, ensure_ascii=False, indent=2)
    # 身份映射只进运行目录（不入交付/GitHub）
    with open(os.path.join(run_dir, "blind_key.json"), "w", encoding="utf-8") as f:
        json.dump({"seed": SEED, "positions": {c: s.get("labels") for c, s in
                                               selection.items() if s.get("labels")}},
                  f, ensure_ascii=False, indent=2)
    return selection


def main():
    run_dir, deliver_dir = sys.argv[1], sys.argv[2]
    sel = build(run_dir, deliver_dir)
    n_ok = sum(1 for s in sel.values() if s["status"] == "PENDING_USER")
    print("blind pack: %d positions usable / %d planned" % (n_ok, len(sel)))


if __name__ == "__main__":
    main()
