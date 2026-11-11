# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 2 · 新 wave manifest 生成器。

扫描 benchmark/stories_v2/<wave>/<EID>/（episode/exposure/timeline 齐备者），
产出 <wave>/STAGE1_MANIFEST.json（与既有 wave manifest 同构：kind/stage/date/
built_by/count/turn_per_story/stories[story_id,title,themes,domain,conflict_type,
dir,turns,files{sha256},twin_group?,lineage_group_id?]）。
在标注（reference.json）与审计（audit.md）完成后运行，使哈希为终态。
"""
import argparse
import datetime
import hashlib
import json
import os
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def sha(p):
    with open(p, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def build(wave, root=None, turns=None):
    wdir = os.path.join(root, wave) if root else os.path.join(REPO, "benchmark", "stories_v2", wave)
    if not os.path.isdir(wdir):
        raise SystemExit("wave 目录不存在: %s" % wdir)
    stories = []
    for eid in sorted(os.listdir(wdir)):
        edir = os.path.join(wdir, eid)
        if not os.path.isdir(edir) or eid.startswith(("_", "GOLD")):
            continue
        ep_p = os.path.join(edir, "episode.json")
        ref_p = os.path.join(edir, "reference.json")
        tl_p = os.path.join(edir, "timeline.json")
        au_p = os.path.join(edir, "audit.md")
        if not (os.path.isfile(ep_p) and os.path.isfile(ref_p) and os.path.isfile(tl_p)
                and os.path.isfile(au_p)):
            print("SKIP(五件套不齐): %s" % eid)
            continue
        ep = json.load(open(ep_p, encoding="utf-8"))
        ex_p = os.path.join(edir, "exposure.json")
        ex = json.load(open(ex_p, encoding="utf-8")) if os.path.isfile(ex_p) else {}
        item = {
            "story_id": ep["episode_id"],
            "title": ex.get("title", ""),
            "themes": ex.get("themes", []),
            "domain": ex.get("domain", ""),
            "conflict_type": ex.get("conflict_type", ""),
            "dir": (os.path.relpath(edir, root).replace("\\", "/") if root
                    else os.path.relpath(edir, REPO).replace("\\", "/")),
            "turns": len(ep.get("turns") or []),
            "files": {fn: sha(os.path.join(edir, fn))
                      for fn in ("episode.json", "reference.json", "timeline.json",
                                 "exposure.json", "audit.md")
                      if os.path.isfile(os.path.join(edir, fn))},
        }
        for extra in ("twin_group", "lineage_group_id", "primary_topic", "split"):
            if ex.get(extra):
                item[extra] = ex[extra]
        if turns is not None and item["turns"] != turns:
            continue
        stories.append(item)
    manifest = {
        "kind": "story_manifest", "stage": wave,
        "date": datetime.date.today().isoformat(),
        "built_by": "tools/benchmark_v1/build_wave_manifest.py",
        "count": len(stories),
        "turn_per_story": sorted({s["turns"] for s in stories}),
        "stories": stories,
    }
    out = os.path.join(wdir, "STAGE1_MANIFEST.json" if turns is None
                       else "MANIFEST_%dT.json" % turns)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    print("manifest written: %s (count=%d)" % (out, len(stories)))
    return manifest


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--wave", required=True)
    ap.add_argument("--root", default=None,
                    help="wave 根目录覆盖（默认 <repo>/benchmark/stories_v2；HOLDOUT 金库"
                         "为仓库外目录时用此参数）")
    ap.add_argument("--turns", type=int, default=None,
                    help="按轮数过滤（混合轮数 wave 分片建 manifest，如 --turns 12）")
    args = ap.parse_args()
    build(args.wave, root=args.root, turns=args.turns)
