# -*- coding: utf-8 -*-
r"""tools/merge_cards_for_batch.py — 把跨素材族的卡合并成一份批次卡文件（零调用、只搬运）。

为什么需要：批次的 cards_path 是单一路径，而 R3 复验批同时要用 stageD 的 D01/D02/D03 与
anchors 的 AK09/AK12。手抄卡会引入漂移，所以只做**逐字合并 + 完整性断言**：
  · 每个 case_id 只能出现一次；
  · `--need` 列出的每个位点必须存在（缺一个就非零退出，绝不"静默填空卡"）；
  · 记录每个来源文件的 sha256 与各故事的卡数，供派发前冻结与事后复核。
卡内容不新写、不改写；本工具不生成任何判据。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/merge_cards_for_batch.py \
    --in benchmark/stories_v2/stageD/GRADING_CARDS_D01_D02_D03.json \
    --in benchmark/stories_v2/anchors_20261028/GRADING_CARDS_AK01_AK12.json \
    --only AK09_T01,AK12_T01 --need D01_T01,D02_T08,D03_T06 \
    --out benchmark/stories_v2/stageD/GRADING_CARDS_R3_DECLINE.json
"""
import argparse
import datetime
import hashlib
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def load(p):
    return json.load(io.open(p, encoding="utf-8"))


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="inputs", action="append", required=True)
    ap.add_argument("--only", action="append", default=[],
                    help="按来源文件过滤，格式 路径子串=case_id,case_id（可多次）；"
                         "未匹配到规则的来源文件全量保留")
    ap.add_argument("--need", default="", help="必须存在的 case_id（逗号分隔）")
    ap.add_argument("--out", required=True)
    a = ap.parse_args(argv)

    rules = []
    for spec in a.only:
        if "=" not in spec:
            raise SystemExit("--only 需要 路径子串=cid,cid 形式：%r" % spec)
        needle, ids_s = spec.split("=", 1)
        rules.append((needle, set(x.strip() for x in ids_s.split(",") if x.strip())))
    need = [x.strip() for x in a.need.split(",") if x.strip()]
    cards, sources, per_story = [], [], {}
    for rel in a.inputs:
        p = rel if os.path.isabs(rel) else os.path.join(ROOT, rel)
        doc = load(p)
        rel_norm = os.path.relpath(p, ROOT).replace("\\", "/")
        allow = None
        for needle, ids_s in rules:
            if needle.replace("\\", "/") in rel_norm:
                allow = ids_s
                break
        keep = 0
        for c in doc.get("cards") or []:
            if allow is not None and c["case_id"] not in allow:
                continue
            cards.append(c)
            keep += 1
            sid = c["case_id"].rsplit("_", 1)[0]
            per_story[sid] = per_story.get(sid, 0) + 1
        sources.append({"file": os.path.relpath(p, ROOT).replace("\\", "/"),
                        "sha256": sha(p), "cards_kept": keep,
                        "cards_total": len(doc.get("cards") or [])})
    ids = [c["case_id"] for c in cards]
    dup = sorted({x for x in ids if ids.count(x) > 1})
    missing = [x for x in need if x not in set(ids)]
    errs = []
    if dup:
        errs.append("case_id 重复：%s" % dup)
    if missing:
        errs.append("缺卡：%s" % missing)
    doc = {
        "kind": "grading_cards_stories_v2",
        "stage": "merged_for_batch",
        "built_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "source": "跨素材族逐字合并（tools/merge_cards_for_batch.py；不新写判据）",
        "sources": sources,
        "stories": per_story,
        "counts": {"cards": len(cards)},
        "checks": {"errors": errs, "passed": not errs, "need": need},
        "cards": cards,
    }
    out = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump(doc, io.open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("merged cards=%d stories=%s errors=%d" % (len(cards), per_story, len(errs)))
    for e in errs:
        print("  ERR", e.encode("ascii", "backslashreplace").decode())
    print("wrote", os.path.relpath(out, ROOT).replace("\\", "/"), "sha256=", sha(out)[:16])
    return 0 if not errs else 1


if __name__ == "__main__":
    sys.exit(main())
