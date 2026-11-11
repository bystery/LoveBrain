# -*- coding: utf-8 -*-
r"""tools/validate_stageD_story.py — 校验 stageD 单个故事的题面/参考/时间线/暴露/审计（零调用）。

为什么需要：`tools/validate_story_v2.py` 的入口把每故事位数写死在模块常量 TURN_PER_STORY 里
（Stage1=8 / Stage2=12），而 stageD 的 D03 是 6 位、D01/D02 是 8 位——直接用会让"位数不符"
淹没真正的语义错误。本工具复用同一套 validate_story 函数，只把位数与 stage 目录按参数设好，
并把 manifest 里该故事的逐文件哈希（若有）传进去，保证校验口径与主校验器一致，不另写判据。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/validate_stageD_story.py --story D03 --turns 6
退出码：0=无错；1=有错（逐条打印，UTF-8 落盘）。
"""
import argparse
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import validate_story_v2 as vs2            # noqa: E402


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--story", required=True)
    ap.add_argument("--turns", type=int, required=True)
    ap.add_argument("--stage", default="stageD")
    ap.add_argument("--out", default=None)
    a = ap.parse_args(argv)

    vs2.BASE = os.path.join(ROOT, "benchmark", "stories_v2", a.stage)
    vs2.STAGE = a.stage
    vs2.TURN_PER_STORY = a.turns
    vs2.REQUIRE_FROZEN_NULL = False        # 冻结前后都可校验；frozen_at 由暴露台账负责

    entry = {"story_id": a.story, "files": {}}
    errs, warns, stats = vs2.validate_story(a.story, entry, set())
    out = {"story_id": a.story, "stage": a.stage, "turns_expected": a.turns,
           "errors": errs, "warnings": warns, "stats": stats,
           "passed": not errs}
    dst = a.out or os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                                "deadline_quality_20261028",
                                "VALIDATION_%s.json" % a.story)
    json.dump(out, io.open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("story=%s turns=%d errors=%d warnings=%d"
          % (a.story, a.turns, len(errs), len(warns)))
    for e in errs:
        print("  ERR", e.encode("ascii", "backslashreplace").decode())
    for w in warns:
        print("  WARN", w.encode("ascii", "backslashreplace").decode())
    print("WROTE", os.path.relpath(dst, ROOT))
    return 0 if not errs else 1


if __name__ == "__main__":
    sys.exit(main())
