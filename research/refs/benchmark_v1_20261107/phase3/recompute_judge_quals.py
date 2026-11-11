# -*- coding: utf-8 -*-
"""Judge 资格诚实复核（P3-3.3 执行件，2026-11-09）。

输入 = W20-03 校准实测（source: refs/experiment_protocol_20261023/long_probe_20261030/
W20_03_JUDGE_CAL_REPORT_20261103.md，数字照录 phase2/JUDGE_CALIBRATION_REPORT §1 表）。
冻结判据（原文）：误杀≤10% 且 漏检≤20% 且 顺序一致率≥80% = full-qualified。
**任一分量未测 = 无法验证 = 不给 full**（不得沿用 phase2 的宽容标注）。

输出：重算资格表 JSON（stdout + 写 JUDGE_QUALS_V2.json）。
"""
import json
import os

# 照录（误杀, 漏检, 顺序一致率；None=未测）
W20_03 = {
    "goal":       {"false_kill": 0.0,   "miss": None,  "order": 0.829},
    "time":       {"false_kill": 0.043, "miss": None,  "order": 0.714},
    "interact":   {"false_kill": 0.0,   "miss": 0.0,   "order": 0.857},
    "emotion":    {"false_kill": 0.0,   "miss": 0.0,   "order": 0.743},
    "factuality": {"false_kill": 0.0,   "miss": None,  "order": 0.743},
    "initiative": {"false_kill": 0.0,   "miss": 0.0,   "order": 0.714},
    "attribution": {"false_kill": None, "miss": None,  "order": 0.457},
    "boundary":   {"false_kill": 0.022, "miss": 0.864, "order": 0.714},
    "memory_use": {"false_kill": 0.0,   "miss": 0.609, "order": 0.514},
}
THRESH = {"false_kill": 0.10, "miss": 0.20, "order": 0.80}


def qualify(rec):
    fails = []
    for k, lim in THRESH.items():
        v = rec.get(k)
        if v is None:
            fails.append("%s未测" % k)
        elif k == "order":          # 一致率门槛：≥lim 才过
            if v < lim:
                fails.append("order=%.3f<%.2f" % (v, lim))
        else:                        # 误杀/漏检上限：≤lim 才过
            if v > lim:
                fails.append("%s=%.3f>%.2f" % (k, v, lim))
    return (not fails), fails


def main():
    out = {"kind": "judge_qualification_v2", "date": "2026-11-09",
           "rule": "误杀≤10% 且 漏检≤20% 且 顺序一致率≥80%；任一分量未测=无法验证=不给 full",
           "source": "W20_03_JUDGE_CAL_REPORT_20261103（数字照录，未重测）",
           "dims": {}}
    for dim, rec in W20_03.items():
        ok, fails = qualify(rec)
        old = {"goal": "full-qualified", "time": "full-qualified", "interact": "full-qualified",
               "emotion": "full-qualified", "factuality": "full-qualified",
               "initiative": "full-qualified", "attribution": "not-testable",
               "boundary": "mechanical-disqualified", "memory_use": "mechanical-disqualified"}[dim]
        if ok:
            new = "full-qualified"
        elif dim in ("boundary", "memory_use", "attribution"):
            new = "disqualified/assistive-only"
        else:
            new = "assistive（未校准，禁判死）"
        out["dims"][dim] = {"w20_03": rec, "old_label": old, "new_label": new,
                            "fail_reasons": fails}
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "JUDGE_QUALS_V2.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("written", path)
    for d, v in out["dims"].items():
        print("%-12s old=%-24s new=%-28s %s" % (d, v["old_label"], v["new_label"],
                                                ";".join(v["fail_reasons"]) or "PASS"))


if __name__ == "__main__":
    main()
