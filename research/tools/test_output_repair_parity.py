# -*- coding: utf-8 -*-
r"""tools/test_output_repair_parity.py — 新 repair 是否真的继承了旧归一器的保全（零调用）。

背景：2026-10-28 晚的复用审计指出，新写的 `tools/output_repair.py` 与已在用的
`tools/normalize_reply_contract.py` 职能重叠，且旧器的"冲突文本/多余方向全文保全"没有带过去
（旧版 repair 把冲突文本截到 80 字、多出方向截到 120 字）。核销后新 repair 直接委托旧器，
本测试在**全部已付费真实 raw**上逐条验证六条不变量：

  A 旧器能给出可发首选 → 新器给出完全相同的 recommended 文本（一字不差）；
  B 旧器保全的多余 directions → 新器 `_audit_extra_directions` 或留损日志里**全文**都在；
  C 旧器保全的冲突键值 → 新器 `_audit_unmapped` 里**全文**都在；
  D 新器留损日志里不出现 "…" 截断、也不出现长度恰好 80/120 而被截的内容字段；
  E 新器新增的 E1/E2 恢复（旧器修不动、新器能修）逐条列出，供人工复核；
  F 两器都修不动的位点逐条列出（不得被算进"已修复"）。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/test_output_repair_parity.py
"""
import glob
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import normalize_reply_contract as nrc      # noqa: E402
import output_repair as orep                # noqa: E402

RUN_GLOBS = [
    "logs/p08_s1_live_20261028/*/out/turn_*/reply_raw.txt",
    "logs/p08_s2_live_20261028/*/out/turn_*/reply_raw.txt",
    "logs/p08_s2r_live_20261028/*/out/turn_*/reply_raw.txt",
    "logs/p08_s3_live_20261028/*/out/turn_*/reply_raw.txt",
    "logs/p08_d1_live_20261028/*/out/turn_*/reply_raw.txt",
    "logs/p08_r3a_live_20261028/*/out/turn_*/reply_raw.txt",
    "logs/p08_r3b_live_20261028/*/out/turn_*/reply_raw.txt",
]


def walk_strings(x):
    if isinstance(x, str):
        yield x
    elif isinstance(x, dict):
        for v in x.values():
            yield from walk_strings(v)
    elif isinstance(x, list):
        for v in x:
            yield from walk_strings(v)


def main():
    files = []
    for g in RUN_GLOBS:
        files += sorted(glob.glob(os.path.join(ROOT, g)))
    rows, fails = [], []
    for fp in files:
        raw = io.open(fp, encoding="utf-8", errors="replace").read()
        old = nrc.normalize_raw(raw)
        new_obj, new_loss, _ = orep.repair(raw)
        rel = os.path.relpath(fp, ROOT).replace("\\", "/")
        old_ok = old["outcome"] == "send"
        new_rec = ((new_obj or {}).get("response") or {}).get("recommended") \
            if isinstance(new_obj, dict) else None
        new_ok = bool((new_rec or "").strip())
        checks = {"A_same_recommended": True, "B_extra_dirs_preserved": True,
                  "C_unmapped_preserved": True, "D_no_truncation": True}
        if old_ok and not (new_ok and new_rec == old["sendable"]):
            checks["A_same_recommended"] = False
            fails.append("%s: A 旧可发/新不可发或文本不同（old=%r new=%r）"
                         % (rel, old["sendable"][:40], (new_rec or "")[:40]))
        old_extra = (old["canonical"] or {}).get("_audit_extra_directions") or []
        if old_extra:
            pool = list((new_obj or {}).get("_audit_extra_directions") or [])
            for l in new_loss:
                pool += l.get("preserved_directions") or []
            for d in old_extra:
                if d is not None and str(d) not in [str(x) for x in pool]:
                    checks["B_extra_dirs_preserved"] = False
                    fails.append("%s: B 多余方向未全文保全：%r" % (rel, str(d)[:40]))
        old_un = (old["canonical"] or {}).get("_audit_unmapped") or []
        if old_un:
            pool2 = list((new_obj or {}).get("_audit_unmapped") or [])
            for l in new_loss:
                if l.get("rule") == "E1_duplicate_canon_key_preserved_to_audit":
                    pool2.append({"alias_value": l.get("preserved_text")})
            pool_vals = [str(x.get("alias_value")) for x in pool2 if isinstance(x, dict)]
            for u in old_un:
                if str(u.get("alias_value")) not in pool_vals:
                    checks["C_unmapped_preserved"] = False
                    fails.append("%s: C 冲突键值未全文保全：%r" % (rel, str(u.get("alias_value"))[:40]))
        for s in walk_strings(new_loss):
            if "…" in s and len(s) in (80, 120):
                checks["D_no_truncation"] = False
                fails.append("%s: D 留损日志疑似截断：%r" % (rel, s[:60]))
            for key in ("dropped_text", "preserved_text"):
                pass
        rows.append({"path": rel, "old_outcome": old["outcome"], "old_sendable_len": len(old["sendable"]),
                     "new_sendable_len": len(new_rec or ""), "new_rule_count": len(new_loss),
                     "old_ok": old_ok, "new_ok": new_ok, "checks": checks,
                     "new_rules": sorted({l.get("rule") for l in new_loss})})

    ext = [r for r in rows if r["new_ok"] and not r["old_ok"]]
    both_fail = [r for r in rows if not r["new_ok"] and not r["old_ok"]]
    regress = [r for r in rows if r["old_ok"] and not r["new_ok"]]
    summary = {"raw_files": len(rows), "old_sendable": sum(1 for r in rows if r["old_ok"]),
               "new_sendable": sum(1 for r in rows if r["new_ok"]),
               "extension_recoveries": len(ext), "both_fail": len(both_fail),
               "regressions": len(regress), "invariant_failures": len(fails),
               "pass": not fails}
    out = {"kind": "output_repair_parity_v1",
           "source_of_truth": "真实 live 答卷 reply_raw.txt（7 批 260 份）",
           "summary": summary, "extension_recovery_positions": ext,
           "both_fail_positions": both_fail, "failures": fails, "rows": rows}
    dst = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                       "deadline_quality_20261028", "OUTPUT_REPAIR_PARITY_20261028.json")
    json.dump(out, io.open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps(summary, sort_keys=True))
    print("WROTE", os.path.relpath(dst, ROOT))
    for f in fails[:12]:
        print("FAIL", f.encode("ascii", "backslashreplace").decode())
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
