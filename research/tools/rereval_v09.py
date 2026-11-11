#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""rereval_v09.py — 对既有运行目录做 scorer v0.9 离线重评（第八波判决 §5）。

用途：旧裁决（历史运行时 manifest 的 score/veto，旧评分版本）与 v0.9 复评
必须分开标注——本工具不修改任何旧 manifest/scoring，只产出独立复评产物。

用法：
  python tools/rereval_v09.py --run logs/pilot_r3/baseline_L1 \
      --episode benchmark/episodes/archive/PILOT_L1_v1__pilot_r3.json \
      --candidate candidates/round_0_baseline \
      --out logs/pilot_r3/rereval_v09_baseline_L1_20261020.json

产物 JSON：每轮 {turn, case_id, historical: {score10, veto}, v09: {score10, veto,
nested/类型证据}} + 汇总（通过数/均分）。全部离线，无任何模型调用。
"""
import argparse
import datetime
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
os.chdir(ROOT)

import lb_runner   # noqa: E402
import lb_scorer   # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True, help="既有运行目录（含 turn_* 子目录）")
    ap.add_argument("--episode", required=True, help="冻结题面 JSON（评分权威快照）")
    ap.add_argument("--candidate", required=True, help="候选目录（决定契约，与原运行一致）")
    ap.add_argument("--out", required=True, help="复评产物 JSON 路径")
    args = ap.parse_args()

    ep = json.load(open(args.episode, encoding="utf-8"))
    case_by_id = {t.get("case_id"): t for t in ep.get("turns", [])}
    contract = lb_runner.load_contract(args.candidate)

    rows = []
    for td in sorted(os.listdir(args.run)):
        if not td.startswith("turn_"):
            continue
        turn_dir = os.path.join(args.run, td)
        reply_p = os.path.join(turn_dir, "reply_raw.txt")
        if not os.path.isfile(reply_p):
            continue
        reply = open(reply_p, encoding="utf-8").read()
        cid = td.split("_", 2)[-1] if td.count("_") >= 2 else td
        case = case_by_id.get(cid)
        if case is None:
            rows.append({"turn_dir": td, "case_id": cid, "error": "冻结题面缺该 case"})
            continue
        id_map = lb_runner.canonical_id_map(
            (case.get("input") or {}).get("messages"))
        hist_p = os.path.join(turn_dir, "manifest.json")
        hist = {}
        if os.path.isfile(hist_p):
            mf = json.load(open(hist_p, encoding="utf-8"))
            hist = {"score10": mf.get("score10"), "veto": mf.get("veto"),
                    "note": "历史运行裁决（运行时评分版本，见 manifest）"}
        r = lb_scorer.run_case_scoring_safe(case, reply, id_map, contract)
        rows.append({"turn_dir": td, "case_id": cid,
                     "historical": hist,
                     "v09": {"score10": r["score10"], "veto": r["veto"],
                             "failed_checks": [c["id"] for c in r["checks"]
                                               if not c["pass"]]}})

    scored = [x for x in rows if "v09" in x]
    summary = {
        "tool": "rereval_v09.py", "scoring_version": "v0.9.1",
        "label": "本次 v0.9.1 独立重评（directions null 修复后；未修改旧 manifest/scoring）",
        "run": os.path.abspath(args.run),
        "episode": os.path.abspath(args.episode),
        "episode_sha256": _sha(args.episode),
        "candidate": args.candidate, "contract": contract,
        "n_turns": len(scored),
        "v09_pass": sum(1 for x in scored if x["v09"]["score10"] > 0
                        and not x["v09"]["veto"]),
        "v09_mean_score10": (round(sum(x["v09"]["score10"] for x in scored)
                                   / len(scored), 4) if scored else None),
        "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    }
    out = {"summary": summary, "turns": rows}
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print("v0.9.1 重评: %s/%s 通过, 均分 %s → %s"
          % (summary["v09_pass"], summary["n_turns"],
             summary["v09_mean_score10"], args.out))
    for x in rows:
        if "v09" in x:
            h = x["historical"]
            print("  %s: 历史=%s/%s → v0.9.1=%s/%s"
                  % (x["case_id"], h.get("score10"), h.get("veto"),
                     x["v09"]["score10"], x["v09"]["veto"]))


def _sha(path):
    import hashlib
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


if __name__ == "__main__":
    main()
