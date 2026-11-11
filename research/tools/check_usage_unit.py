#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""check_usage_unit.py — usage.prompt 与字符数单位口径检查（零模型调用）。

结论（2026-11-03 实测 w17c n=144 / w18b n=320）：calls.json 的 usage.prompt /
(len(system)+len(user)) 稳定在 0.72–0.76（p50≈0.73），即 usage.prompt 是上游 API
回报的计量单位（usage-unit），**不是字符数也不是 token**；网关同时记录的
prompt_chars 才是字符口径。口径细则见 GOLD_SCHEMA.md §7 与
refs/experiment_protocol_20261023/global_harness_program_20261030/USAGE_UNIT_NOTE.md。

用法：python tools/check_usage_unit.py logs/w17c_merge_live_20261103 [logs/w18b_...]
成本门规则：同单位配对差有效；绝对值标签今后一律记 usage-unit，不冒充 token/字符。
"""
import glob
import json
import os
import sys


def batch_ratio_stats(batch):
    rs = [c["usage"]["prompt"] / (len(c.get("system") or "") + len(c.get("user") or ""))
          for f in glob.glob(os.path.join(batch, "*", "out", "turn_*", "calls.json"))
          for c in json.load(open(f, encoding="utf-8"))
          if isinstance(c.get("usage", {}).get("prompt"), (int, float))
          and (c.get("system") or c.get("user"))]
    rs.sort()
    n = len(rs)
    return {"batch": os.path.basename(os.path.normpath(batch)), "n_calls": n,
            "p10": round(rs[n // 10], 3), "p50": round(rs[n // 2], 3),
            "p90": round(rs[min(n - 1, int(n * 0.9))], 3),
            "min": round(rs[0], 3), "max": round(rs[-1], 3),
            "unit": "usage.prompt per char (usage-unit/char)"}


if __name__ == "__main__":
    for b in sys.argv[1:]:
        print(json.dumps(batch_ratio_stats(b), ensure_ascii=False))
