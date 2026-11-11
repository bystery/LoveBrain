# -*- coding: utf-8 -*-
"""tools/replay_uc_supp_triggers.py — W18 触发表离线复现（纯本地机制，零模型调用/零网络）。

对 LRE117 / LRE121 / LRE146 / LRE138 逐轮运行：
  - candidate = window_expand_b_norecap_uc_supp（S 压制段 + U 未定段）
  - parent    = window_expand_b_norecap_uc（仅 U 段）
capture gateway 返回固定文本，不触达 dsf 端点；逐轮从 notes 读取
supp_cu / uc_cu / xdedup，并比较两臂出站 prompt 是否逐字节相同。

输出 logs/W18_TRIGGER_REPLAY.json：
  每篇 S 触发数、U 触发数、两段同场数、并集、S 峰值 CU、联合峰值 CU、
  与父本逐字节相同的轮数，以及逐轮明细；
  并与 W18_UNC_SUPPRESS_DESIGN_20261102.md §2.7 表格逐格比对（不一致即 exit 1）。

LRE138 尚未导出到 benchmark/episodes，本工具按 export_lr2_episodes.py 的同一变换
在内存里构造 episode（不写新 episode / 卡文件，避免改动已冻结素材）。

用法：
  python tools/replay_uc_supp_triggers.py [--out logs/W18_TRIGGER_REPLAY.json]
"""
import argparse
import importlib.util
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAND = "window_expand_b_norecap_uc_supp"
PARENT = "window_expand_b_norecap_uc"
FIXED_REPLY = '{"response": {"recommended": "收到，我记下了。"}}'

# 设计 §2.7 表（2026-11-02 冻结，真实 turn 口径）
DESIGN = {
    "LRE117": {"turns": 40, "s": 33, "u": 5, "both": 5, "union": 33,
               "s_peak": 160, "comb_peak": 260, "same": 7},
    "LRE121": {"turns": 40, "s": 36, "u": 4, "both": 4, "union": 36,
               "s_peak": 87, "comb_peak": 196, "same": 4},
    "LRE146": {"turns": 40, "s": 0, "u": 10, "both": 0, "union": 10,
               "s_peak": 0, "comb_peak": 119, "same": 40},
    "LRE138": {"turns": 40, "s": 18, "u": 5, "both": 2, "union": 21,
               "s_peak": 59, "comb_peak": 165, "same": 22},
}
DESIGN_TOTAL = {"turns": 160, "s": 87, "u": 24, "both": 11, "union": 100, "same": 73}


class CaptureGateway:
    """零网络 gateway：记录出站 prompt，返回固定文本。"""

    def __init__(self):
        self.prompts = []

    def generate(self, system, prompt, images=None, tag="main"):
        self.prompts.append(prompt)
        return {"text": FIXED_REPLY}


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _mech(cand_name):
    return _load("w18_replay_%s" % cand_name,
                 os.path.join(ROOT, "candidates", cand_name, "mechanism", "mechanism.py"))


def _episode(sid):
    """读 runner 格式 episode；LRE138 缺导出时按 export 工具同一变换在内存构造。"""
    path = os.path.join(ROOT, "benchmark", "episodes", "%s.json" % sid)
    if os.path.isfile(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f), os.path.relpath(path, ROOT)
    src = os.path.join(ROOT, "benchmark", "stories_v2", "wave3_ext", sid, "episode.json")
    if not os.path.isfile(src):
        raise SystemExit("episode 缺失（benchmark/episodes 与 wave3_ext 均无）: %s" % sid)
    exp = _load("w18_replay_export", os.path.join(ROOT, "tools", "export_lr2_episodes.py"))
    ep = exp.load_json(src)
    ep = dict(ep)
    ep["persona"] = exp.flatten_persona(ep.get("persona"))
    return ep, os.path.relpath(src, ROOT) + "（内存导出，未落盘）"


def _run(cand_name, mod, episode):
    gw = CaptureGateway()
    sandbox = os.path.join(ROOT, "logs", "_w18_replay_tmp", cand_name)
    h = mod.init(os.path.join(ROOT, "candidates", cand_name, "assets"),
                 {"persona": episode.get("persona") or {},
                  "initial_kb": episode.get("initial_kb") or {}}, gw, sandbox)
    rows = []
    for i, turn in enumerate(episode.get("turns") or [], 1):
        ctx = {"input": turn.get("input") or {}, "turn_index": i}
        res = mod.run_turn(h, ctx, gw)
        notes = res.get("notes") or ""
        fields = {k: int(v) for k, v in re.findall(r"(\w+)=(\d+)", notes)}
        rows.append({"turn": i, "prompt": gw.prompts[-1], "notes": notes,
                     "supp_cu": fields.get("supp_cu", 0),
                     "supp_pairs": fields.get("supp_pairs", 0),
                     "uc_cu": fields.get("uc_cu", 0),
                     "uc_items": fields.get("uc_items", 0),
                     "xdedup": fields.get("xdedup", 0),
                     "comb_cu": fields.get("comb_cu", 0),
                     "comb_overflow": fields.get("comb_overflow", 0)})
    return rows


def _summarize(cand_rows, parent_rows):
    both = lambda r: r["supp_cu"] > 0 and r["uc_cu"] > 0
    union = lambda r: r["supp_cu"] > 0 or r["uc_cu"] > 0
    same = [r["turn"] for r, p in zip(cand_rows, parent_rows)
            if r["prompt"] == p["prompt"]]
    comb_peak_row = max(cand_rows, key=lambda r: r["comb_cu"])
    s_peak_row = max(cand_rows, key=lambda r: r["supp_cu"])
    return {
        "turns": len(cand_rows),
        "s": sum(1 for r in cand_rows if r["supp_cu"] > 0),
        "u": sum(1 for r in cand_rows if r["uc_cu"] > 0),
        "both": sum(1 for r in cand_rows if both(r)),
        "union": sum(1 for r in cand_rows if union(r)),
        "s_peak_cu": s_peak_row["supp_cu"],
        "s_peak_turn": s_peak_row["turn"],
        "comb_peak_cu": comb_peak_row["comb_cu"],
        "comb_peak_turn": comb_peak_row["turn"],
        "same": len(same),
        "diff_turns": [r["turn"] for r, p in zip(cand_rows, parent_rows)
                       if r["prompt"] != p["prompt"]],
        "xdedup_total": sum(r["xdedup"] for r in cand_rows),
        "comb_overflow_total": sum(r["comb_overflow"] for r in cand_rows),
    }


def _compare(story_summary):
    out = {}
    mapping = {"turns": "turns", "s": "s", "u": "u", "both": "both",
               "union": "union", "s_peak_cu": "s_peak",
               "comb_peak_cu": "comb_peak", "same": "same"}
    for key, dkey in mapping.items():
        got, want = story_summary[key], DESIGN[story_summary["story"]][dkey]
        out[key] = {"design": want, "actual": got, "match": got == want}
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "logs", "W18_TRIGGER_REPLAY.json"))
    ap.add_argument("--episodes", default="LRE117,LRE121,LRE146,LRE138")
    args = ap.parse_args()

    cand_mod, parent_mod = _mech(CAND), _mech(PARENT)
    stories, mismatches = {}, []
    total = {"turns": 0, "s": 0, "u": 0, "both": 0, "union": 0, "same": 0}
    for sid in [s.strip() for s in args.episodes.split(",") if s.strip()]:
        ep, source = _episode(sid)
        cand_rows = _run(CAND, cand_mod, ep)
        parent_rows = _run(PARENT, parent_mod, ep)
        summary = _summarize(cand_rows, parent_rows)
        summary["story"] = sid
        summary["source"] = source
        summary["per_turn"] = [{"turn": r["turn"], "supp_cu": r["supp_cu"],
                                "supp_pairs": r["supp_pairs"], "uc_cu": r["uc_cu"],
                                "uc_items": r["uc_items"], "xdedup": r["xdedup"],
                                "comb_cu": r["comb_cu"],
                                "same_as_parent": r["prompt"] == p["prompt"]}
                               for r, p in zip(cand_rows, parent_rows)]
        summary["design_comparison"] = _compare(summary)
        for k, v in summary["design_comparison"].items():
            if not v["match"]:
                mismatches.append("%s.%s design=%s actual=%s" %
                                  (sid, k, v["design"], v["actual"]))
        print("%s: S %d/%d, U %d/%d, 同场 %d, 并集 %d, S峰值 %d(T%d), 联合峰值 %d(T%d), "
              "逐字节相同 %d/%d, xdedup=%d, overflow=%d"
              % (sid, summary["s"], DESIGN[sid]["s"], summary["u"], DESIGN[sid]["u"],
                 summary["both"], summary["union"], summary["s_peak_cu"],
                 summary["s_peak_turn"], summary["comb_peak_cu"],
                 summary["comb_peak_turn"], summary["same"], summary["turns"],
                 summary["xdedup_total"], summary["comb_overflow_total"]))
        stories[sid] = summary
        for k in total:
            total[k] += summary[k]

    total["both"] = sum(1 for sid in stories for r in stories[sid]["per_turn"]
                        if r["supp_cu"] > 0 and r["uc_cu"] > 0)
    design_total_match = {k: {"design": v, "actual": total[k], "match": total[k] == v}
                          for k, v in DESIGN_TOTAL.items()}
    for k, v in design_total_match.items():
        if not v["match"]:
            mismatches.append("TOTAL.%s design=%s actual=%s" % (k, v["design"], v["actual"]))

    payload = {
        "kind": "w18_trigger_replay",
        "design_ref": "refs/experiment_protocol_20261023/global_harness_program_20261030/"
                      "W18_UNC_SUPPRESS_DESIGN_20261102.md §2.7",
        "candidate": CAND,
        "parent": PARENT,
        "zero_network": True,
        "turns": total["turns"],
        "stories": stories,
        "totals": total,
        "design_totals": DESIGN_TOTAL,
        "design_totals_comparison": design_total_match,
        "all_match": not mismatches,
        "mismatches": mismatches,
    }
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("合计: S %d/%d, U %d/%d, 同场 %d/%d, 并集 %d/%d, 逐字节相同 %d/%d"
          % (total["s"], DESIGN_TOTAL["s"], total["u"], DESIGN_TOTAL["u"],
             total["both"], DESIGN_TOTAL["both"], total["union"], DESIGN_TOTAL["union"],
             total["same"], DESIGN_TOTAL["same"]))
    print("设计表逐格一致" if not mismatches else "与设计不一致：\n  " + "\n  ".join(mismatches))
    print("report:", os.path.relpath(args.out, ROOT))
    return 0 if not mismatches else 1


if __name__ == "__main__":
    sys.exit(main())
