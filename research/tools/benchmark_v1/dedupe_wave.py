# -*- coding: utf-8 -*-
"""wave 级骨架近重扫描（story_skeleton_dedupe 的 wave 扩展薄封装）。

不触碰 W03-06 历史台账：复用 tools/story_skeleton_dedupe.py 的 load_stories/
jaccard 逻辑，把指定 wave 的 STAGE1_MANIFEST.json 追加进扫描范围，结果写到
--out 指定文件（默认 phase2/DEDUPE_<wave>.json）。

用法：
  python -I tools/benchmark_v1/dedupe_wave.py --wave wave4_dev
  python -I tools/benchmark_v1/dedupe_wave.py --wave wave4_dev --isolated
    （--isolated：只扫该 wave，不并旧库——用于隔离观察新批内部近重）
"""
import argparse
import json
import os
import sys
from itertools import combinations

TOOLS = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
REPO = os.path.abspath(os.path.join(TOOLS, ".."))
sys.path.insert(0, TOOLS)

import story_skeleton_dedupe as base  # noqa: E402

PHASE2 = os.path.join(REPO, "refs", "benchmark_v1_20261107", "phase2")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--wave", required=True)
    ap.add_argument("--manifest-name", default="STAGE1_MANIFEST.json")
    ap.add_argument("--isolated", action="store_true", help="只扫该 wave")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    wave_manifest = (args.wave, args.manifest_name)
    if args.isolated:
        base.MANIFESTS = [wave_manifest]
    else:
        base.MANIFESTS = list(base.MANIFESTS) + [wave_manifest]

    stories = base.load_stories()
    clusters = []
    for a, b in combinations(stories, 2):
        if a["domain"] == b["domain"] and base.jac(a["themes"], b["themes"]) >= 0.6:
            for c in clusters:
                if a["id"] in c["ids"] or b["id"] in c["ids"]:
                    c["ids"] |= {a["id"], b["id"]}
                    c["j"] = max(c["j"], base.jac(a["themes"], b["themes"]))
                    break
            else:
                clusters.append({"ids": {a["id"], b["id"]},
                                 "j": round(base.jac(a["themes"], b["themes"]), 2),
                                 "pair": "%s~%s" % (a["id"], b["id"])})
    wave_ids = {s["id"] for s in stories if s.get("stage") == args.wave}
    wave_clusters = [c for c in clusters if wave_ids & set(c["ids"])]
    res = {
        "kind": "wave_skeleton_dedupe", "wave": args.wave, "isolated": args.isolated,
        "total_scanned": len(stories), "wave_stories": len(wave_ids),
        "near_dup_clusters_all": [{"ids": sorted(c["ids"]), "max_jaccard": c["j"]}
                                  for c in clusters],
        "near_dup_clusters_involving_wave": [{"ids": sorted(c["ids"]), "max_jaccard": c["j"]}
                                             for c in wave_clusters],
        "note": "近重判据与 story_skeleton_dedupe 相同：同域且主题 Jaccard>=0.6",
    }
    out = args.out or os.path.join(PHASE2, "DEDUPE_%s.json" % args.wave)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(res, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("scanned=%d wave_stories=%d clusters_all=%d involving_wave=%d -> %s"
          % (len(stories), len(wave_ids), len(clusters), len(wave_clusters), out))


if __name__ == "__main__":
    main()
