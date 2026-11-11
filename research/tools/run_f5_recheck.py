# -*- coding: utf-8 -*-
r"""tools/run_f5_recheck.py — F5 修复复测（真实调用，新身份，12 次）。

范围（严格对齐 AUTH-F5-RECHECK-20261029-01）：
  - 只跑候选臂、只跑 N01 整条 12 轮（含原失败位 T05），素材用 F3 已冻结副本；
  - 新身份 action_policy_r4_guard_final（F5_CANDIDATE_FREEZE_20261029.json）；
  - 不 resume（整链重跑）、不重试、不补跑；一物理请求一次 attempt。

跑完立刻做零成本核销：每轮 reply_ingest.json 的 kind/top_level_keys、
contract_guard 是否触发 E3、以及 12 条正文与 F3 冻结答案的差异位数。
"""
import argparse
import datetime as dt
import hashlib
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "final_mainline_review_20261029")
STORIES = os.path.join(ROOT, "benchmark", "stories_v2", "final_20261029")
PY = sys.executable


def sha(b):
    return hashlib.sha256(b).hexdigest()


def shalist(path):
    out = []
    for line in open(path, encoding="utf-8"):
        if line.strip():
            out.append(json.loads(line))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", default="live", choices=["live", "mock"])
    ap.add_argument("--story", default="N01")
    ap.add_argument("--aid", default="AUTH-F5-RECHECK-20261029-01")
    ap.add_argument("--tag", default="f5_recheck")
    args = ap.parse_args()

    auth_path = os.path.join(OUT, "F5_RECHECK_AUTHORIZATION_20261029.json")
    auth = json.load(open(auth_path, encoding="utf-8"))
    frozen = json.load(open(os.path.join(OUT, "F5_CANDIDATE_FREEZE_20261029.json"),
                            encoding="utf-8"))
    assert auth["scope"]["identity_sha256"] == frozen["identity_sha256"], "身份与授权单不一致"

    ts = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    out_base = os.path.join(ROOT, "logs", "f5_recheck_%s_%s" % (args.tag, ts))
    os.makedirs(out_base, exist_ok=True)
    ep_dir = os.path.join(out_base, "_episodes")
    os.makedirs(ep_dir, exist_ok=True)

    src_ep = os.path.join(STORIES, args.story, "episode.json")
    dst_ep = os.path.join(ep_dir, "%s_%s.json" % (args.story, args.aid[-6:]))
    with open(src_ep, "rb") as f:
        blob = f.read()
    with open(dst_ep, "wb") as f:
        f.write(blob)
    print("episode copy", dst_ep, sha(blob))

    run_dir = os.path.join(out_base, "_iso_run_candidate_%s" % args.story)
    out_dir = os.path.join(out_base, "candidate_%s" % args.story)
    turns = int(auth["real_endpoint"]["max_attempts"])
    argv = [PY, os.path.join(ROOT, "tools", "iso_run.py"),
            "--candidate", "action_policy_r4_guard_final",
            "--episode", dst_ep, "--out", out_dir, "--run-dir", run_dir,
            "--adapter", "mock" if args.mode == "mock" else "openai_compat",
            "--quota", str(turns), "--stop-after", str(turns)]
    if args.mode == "live":
        argv += ["--authorization", auth_path, "--expect-attempts", str(turns)]
        ep = auth["real_endpoint"]["api_base"].rstrip("/")
        md = auth["real_endpoint"]["model"]
        if os.environ.get("LB_API_BASE", "").rstrip("/") not in ("", ep):
            raise SystemExit("LB_API_BASE 与授权单不一致")
        if os.environ.get("LB_MODEL", "") not in ("", md):
            raise SystemExit("LB_MODEL 与授权单不一致")
        os.environ["LB_API_BASE"] = ep
        os.environ["LB_MODEL"] = md
        os.environ.setdefault("LB_API_KEY", "local")
    print("RUN", " ".join(argv))
    p = subprocess.run(argv, cwd=ROOT)
    print("exit", p.returncode)

    # ---- 零成本核销 -------------------------------------------------------
    turns_done = sorted([d for d in os.listdir(out_dir) if d.startswith("turn_")]) \
        if os.path.isdir(out_dir) else []
    rows = []
    for t in turns_done:
        td = os.path.join(out_dir, t)
        ing = os.path.join(td, "reply_ingest.json")
        call = os.path.join(td, "calls.json")
        rawf = os.path.join(td, "reply_raw.txt")
        rec = {"case_id": t.split("_", 2)[-1]}
        if os.path.isfile(ing):
            d = json.load(open(ing, encoding="utf-8"))
            rec["ingest_kind"] = d.get("kind")
            rec["top_level_keys"] = d.get("top_level_keys")
            rec["note"] = d.get("note")
        if os.path.isfile(call):
            c = json.load(open(call, encoding="utf-8"))[0]
            rec["raw_sha256"] = sha((c.get("raw_text") or "").encode("utf-8"))
            rec["raw_chars"] = len(c.get("raw_text") or "")
        if os.path.isfile(rawf):
            txt = open(rawf, encoding="utf-8").read()
            rec["harness_sha256"] = sha(txt.encode("utf-8"))
            rec["e3_in_notes"] = bool(re.search(r"E3_stray_wrapper_object_removed", txt))
        rows.append(rec)
    ok = [r for r in rows if r.get("ingest_kind") == "json"
          and set(r.get("top_level_keys") or []) >= {"response", "directions", "analysis"}]
    bad = [r for r in rows if r not in ok]
    summary = {
        "kind": "f5_recheck_result", "mode": args.mode, "aid": args.aid,
        "story": args.story, "out_dir": os.path.relpath(out_base, ROOT).replace("\\", "/"),
        "identity_sha256": frozen["identity_sha256"],
        "turns": len(rows), "protocol_ok": len(ok), "protocol_failed": len(bad),
        "protocol_failed_cases": [r["case_id"] for r in bad],
        "iso_run_exit": p.returncode,
        "rows": rows,
    }
    dst = os.path.join(out_base, "F5_RECHECK_RESULT.json")
    json.dump(summary, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps({k: v for k, v in summary.items() if k != "rows"},
                     ensure_ascii=False, sort_keys=True))
    print("WROTE", os.path.relpath(dst, ROOT).replace("\\", "/"))
    return 0 if (p.returncode == 0 and not bad) else 1


if __name__ == "__main__":
    sys.exit(main())
