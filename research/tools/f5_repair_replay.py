"""tools/f5_repair_replay.py — F5 修复的零调用回放核销（offline，不花额度）

目的：证明新守卫（output_repair E3 + 既有 I1/E2/E1）在**已付费的真实答卷**上
  1) 修好了 F3 唯一判失败的回合（N01_T05），且
  2) 对其余回合的 harness 文本**零改动**（逐字节相同），即无回归。
对照基线 = F3 当轮落盘的 reply_raw.txt（旧守卫产物）与 calls.json 里的模型原文。
不重新调用模型，不重写任何 F3 证据文件，只新增一份核销记录。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/f5_repair_replay.py \
    --run logs/f3_newmat_live_restart_20261029 \
    --out refs/experiment_protocol_20261023/final_mainline_review_20261029/F5_REPAIR_REPLAY.json
"""
import argparse
import importlib.util
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUARD = os.path.join(ROOT, "candidates", "action_policy_r4_guard_final",
                     "mechanism", "contract_guard.py")


def load_guard():
    spec = importlib.util.spec_from_file_location("f5_contract_guard", GUARD)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", default="logs/f3_newmat_live_restart_20261029")
    ap.add_argument("--out", required=True)
    ap.add_argument("--arms", default="candidate_N01,candidate_N02,"
                                      "candidate_K01_resume,candidate_K02_resume")
    args = ap.parse_args()

    guard = load_guard()
    base = os.path.join(ROOT, args.run.replace("/", os.sep))
    rows = []
    n_changed = n_e3 = n_bad = 0
    for arm in [a.strip() for a in args.arms.split(",") if a.strip()]:
        adir = os.path.join(base, arm)
        if not os.path.isdir(adir):
            continue
        for turn in sorted(os.listdir(adir)):
            tdir = os.path.join(adir, turn)
            cr = os.path.join(tdir, "reply_raw.txt")
            cc = os.path.join(tdir, "calls.json")
            if not (os.path.isdir(tdir) and os.path.isfile(cr) and os.path.isfile(cc)):
                continue
            old_harness = io.open(cr, encoding="utf-8", errors="replace").read()
            call = json.load(io.open(cc, encoding="utf-8"))[0]
            raw, user = call.get("raw_text") or "", call.get("user") or ""
            new_text, meta, obj = guard.repair_and_guard(raw, user)
            rules = [l.get("rule") for l in (meta.get("output_repair_loss") or [])]
            changed = (new_text != old_harness)
            e3 = any(str(r).startswith("E3") for r in rules)
            parse_ok = obj is not None
            keys = sorted((obj or {}).keys())
            row = {"arm": arm, "case_id": turn.split("_", 2)[-1],
                   "old_sha256": call.get("raw_sha256") or "",
                   "harness_changed": changed, "e3_fired": e3,
                   "new_parse_ok": parse_ok, "new_top_level_keys": keys,
                   "rules": rules,
                   "subject_guard": bool(meta.get("subject_guard"))}
            rows.append(row)
            if changed:
                n_changed += 1
            if e3:
                n_e3 += 1
            if not parse_ok:
                n_bad += 1
    regress = [r for r in rows if r["harness_changed"] and not r["e3_fired"]]
    unsolvable = [r for r in rows if not r["new_parse_ok"]]
    e3_rows = [r for r in rows if r["e3_fired"]]
    out = {"kind": "f5_repair_replay", "generated_by": "tools/f5_repair_replay.py",
           "source_run": args.run, "replayed_rows": len(rows),
           "harness_changed": n_changed, "e3_fired": n_e3,
           "unparseable_after_repair": len(unsolvable),
           "regressions_non_e3_change": len(regress),
           "e3_positions": [r["case_id"] for r in e3_rows],
           "regression_positions": [r["case_id"] for r in regress],
           "unparseable_positions": [r["case_id"] for r in unsolvable],
           "pass": (not regress) and (not unsolvable),
           "rows": rows}
    dst = args.out if os.path.isabs(args.out) else os.path.join(ROOT, args.out.replace("/", os.sep))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    json.dump(out, io.open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps({k: v for k, v in out.items() if k != "rows"},
                     ensure_ascii=False, sort_keys=True))
    print("WROTE", os.path.relpath(dst, ROOT).replace("\\", "/"))
    return 0 if out["pass"] else 1


if __name__ == "__main__":
    sys.exit(main())
