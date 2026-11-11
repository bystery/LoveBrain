# -*- coding: utf-8 -*-
"""tools/merge_lp_reviews.py — 把按篇拆分的九维评审报告合并成一份批级报告。

用途：评审并行化后（每篇一个 tag/进程），需要一份与单进程批一致的聚合文件供判决引用。
只读输入、只写 --out；不改任何评审原始文件。

用法：
  python tools/merge_lp_reviews.py \
      --glob "refs/experiment_protocol_20261023/long_probe_20261030/LP_W16_LRE*_NINEDIM_REVIEW.json" \
      --out refs/experiment_protocol_20261023/long_probe_20261030/LP_W16_NINEDIM_REVIEW.json \
      --tag W16
"""
import argparse
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--glob", required=True, dest="pattern")
    ap.add_argument("--out", required=True)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--note", default="")
    a = ap.parse_args()

    files = sorted(glob.glob(os.path.join(ROOT, a.pattern)))
    if not files:
        raise SystemExit("没有匹配到评审文件：%s" % a.pattern)

    dims_keys, verdicts, sups = None, [], []
    sev = {"candidate": 0, "parent": 0}
    call_fail, n_positions = 0, 0
    per_story = {}
    for f in files:
        d = json.load(open(f, encoding="utf-8"))
        dims_keys = dims_keys or list(d["dims"])
        verdicts += d.get("verdicts", [])
        sups += d.get("swap_supplement", [])
        sev["candidate"] += d.get("severe", {}).get("candidate", 0)
        sev["parent"] += d.get("severe", {}).get("parent", 0)
        call_fail += d.get("review_call_quality", {}).get("n_call_fail", 0)
        n_positions += d.get("n_positions", 0)
        for sid in d.get("stories", []):
            per_story[sid] = {"dims": d["dims"], "severe": d["severe"],
                              "swap_agreement": d.get("swap_agreement"), "source": os.path.basename(f)}

    rows = [v for v in verdicts if v.get("winner_cand_view") not in ("UNPARSEABLE", "CALL_FAIL")]
    dims = {}
    for dd in dims_keys:
        diffs = [v["dims"]["candidate"][dd] - v["dims"]["parent"][dd] for v in rows
                 if v["dims"]["candidate"].get(dd) is not None and v["dims"]["parent"].get(dd) is not None]
        cv = [v["dims"]["candidate"][dd] for v in rows if v["dims"]["candidate"].get(dd) is not None]
        pv = [v["dims"]["parent"][dd] for v in rows if v["dims"]["parent"].get(dd) is not None]
        dims[dd] = {"n": len(diffs),
                    "cand_mean": round(sum(cv) / len(cv), 3) if cv else None,
                    "parent_mean": round(sum(pv) / len(pv), 3) if pv else None,
                    "paired_diff_mean": round(sum(diffs) / len(diffs), 3) if diffs else None,
                    "cand_wins": sum(1 for x in diffs if x > 0),
                    "parent_wins": sum(1 for x in diffs if x < 0)}
    disagree = [s for s in sups if not s.get("agree")
                and s.get("main_winner") not in ("UNPARSEABLE", "CALL_FAIL")
                and s.get("swap_winner") not in ("UNPARSEABLE", "CALL_FAIL")]
    report = {"kind": "lp_ninedim_review", "tag": a.tag, "date": None,
              "merged_from": [os.path.basename(f) for f in files],
              "stories": sorted(per_story), "n_positions": n_positions,
              "dims": dims, "severe": sev,
              "swap_agreement": "%d/%d" % (len(sups) - len(disagree), len(sups)),
              "swap_disagreements_true": disagree,
              "verdicts": verdicts, "swap_supplement": sups, "per_story": per_story,
              "review_call_quality": {"n_call_fail": call_fail,
                                      "fail_rate": round(call_fail / max(1, n_positions), 3)},
              "note": a.note or "按篇拆分评审的批级聚合；故事级=探索性读数，severe 需逐位 B3 亲读"}
    out = os.path.join(ROOT, a.out)
    json.dump(report, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    print("merged %d files -> %s" % (len(files), a.out))
    print("positions=%d rows=%d call_fail=%d severe=%s swap=%s" %
          (n_positions, len(rows), call_fail, sev, report["swap_agreement"]))
    for dd, st in dims.items():
        print("  %-9s n=%s diff=%s (cand %s / par %s)" %
              (dd, st["n"], st["paired_diff_mean"], st["cand_mean"], st["parent_mean"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
