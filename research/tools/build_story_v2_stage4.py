# -*- coding: utf-8 -*-
"""build_story_v2_stage4.py — W03-04 长知识剧构建器（复用 stage3 数据驱动管线）。

规格来源：benchmark/stories_v2/stage4_src/*.json（24-48 轮长剧，每篇 ≥12 应保留原子、≥3 更新机会）。
产出与 stage3 同构五件套 + episodes 副本；manifest 独立为 STAGE4_MANIFEST.json。零网络零模型调用。
"""
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location(
    "b3", os.path.join(ROOT, "tools", "build_story_v2_stage3.py"))
b3 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b3)

b3.SRC_DIR = os.path.join(ROOT, "benchmark", "stories_v2", "stage4_src")
b3.OUT_BASE = os.path.join(ROOT, "benchmark", "stories_v2", "stage4")
b3.MANIFEST = os.path.join(b3.OUT_BASE, "STAGE4_MANIFEST.json")


def main():
    rc = b3.main()
    # 补充长剧验收口径：每篇应保留原子数与更新机会数
    man = json.load(open(b3.MANIFEST, encoding="utf-8"))
    checks = []
    for entry in man["stories"]:
        sid = entry["story_id"]
        ref = json.load(open(os.path.join(b3.OUT_BASE, sid, "reference.json"),
                             encoding="utf-8"))
        n_required = sum(1 for t in ref["turns"].values()
                         for a in (t.get("required_memory_atoms") or []))
        n_atoms = sum(len(t.get("required_memory_atoms") or [])
                      for t in ref["turns"].values())
        spec_files = [f for f in os.listdir(b3.SRC_DIR)]
        story_spec = None
        for f in spec_files:
            d = json.load(open(os.path.join(b3.SRC_DIR, f), encoding="utf-8"))
            for s in (d["stories"] if isinstance(d, dict) and "stories" in d else [d]):
                if s["public_id"] == sid:
                    story_spec = s
        assert story_spec
        n_keep = len([f for f in story_spec["facts"]
                      if f.get("status") in ("asserted", "tentative")])
        n_updates = len([e for e in story_spec["events"]
                         if e["label"] and ("改" in e["label"] or "后" in e["label"])])
        entry["w0304_check"] = {"facts_total": n_keep, "required_atom_uses": n_required,
                                "atom_mentions": n_atoms,
                                "update_events_detected": n_updates,
                                "note": "≥12应保留原子/≥3更新机会由 spec 设计保证，"
                                        "此字段为自动清点（更新事件按 label 启发式，权威以 spec corrections 为准）"}
        checks.append(entry["w0304_check"])
    json.dump(man, open(b3.MANIFEST, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(b3.MANIFEST, encoding="utf-8"))
    for c in checks:
        print("w0304:", json.dumps(c, ensure_ascii=False))
    return rc


if __name__ == "__main__":
    sys.exit(main())
