# -*- coding: utf-8 -*-
"""tools/pipeline_review_watch.py — 边跑边评：某篇两臂都跑完后立刻起该篇九维评审。

背景：批级评审若等整批 live 结束才起，白等 20–30 分钟。本工具按篇监听
`<batch-dir>/<arm>_<EP>/out/turn_*/manifest.json` 的完成数，两臂都齐即拉起
`run_lp_review_generic.py`（每篇独立 tag/授权），最后可选自动合并。

用法：
  python tools/pipeline_review_watch.py --batch-dir logs/w17b_live \
      --episodes E189,E190,E195,E199,E207,E208 \
      --cards benchmark/stories_v2/wave3/GRADING_CARDS_W17.json \
      --tag-prefix W17B --auth-prefix AUTH-W17B-R --date 20261102 \
      --cand-name window_expand_b_norecap_guard --merge-out LP_W17B_NINEDIM_REVIEW.json

只读批目录；仅拉起评审子进程与写一个状态文件 logs/_review_watch_<prefix>.json。
"""
import argparse
import glob
import json
import os
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def expected_turns(ep):
    d = json.load(open(os.path.join(ROOT, "benchmark", "episodes", "%s.json" % ep), encoding="utf-8"))
    return len(d.get("turns") or [])


def done_turns(batch_dir, arm, ep):
    return len(glob.glob(os.path.join(ROOT, batch_dir, "%s_%s" % (arm, ep), "out", "turn_*", "manifest.json")))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--batch-dir", required=True)
    ap.add_argument("--episodes", required=True)
    ap.add_argument("--cards", required=True)
    ap.add_argument("--tag-prefix", required=True)
    ap.add_argument("--auth-prefix", required=True)
    ap.add_argument("--date", required=True)
    ap.add_argument("--cand-name", required=True)
    ap.add_argument("--poll", type=int, default=20)
    ap.add_argument("--merge-out", default=None,
                    help="全部评审结束后自动合并到该文件名（相对 long_probe 目录）")
    a = ap.parse_args()

    eps = [e.strip() for e in a.episodes.split(",") if e.strip()]
    need = {e: expected_turns(e) for e in eps}
    state_path = os.path.join(ROOT, "logs", "_review_watch_%s.json" % a.tag_prefix)
    started, procs = {}, {}
    print("watch: episodes=%s turns=%s" % (eps, need))

    while True:
        for ep in eps:
            if ep in started:
                continue
            if done_turns(a.batch_dir, "parent", ep) >= need[ep] and \
               done_turns(a.batch_dir, "candidate", ep) >= need[ep]:
                tag = "%s_%s" % (a.tag_prefix, ep)
                auth = "%s-%s-%s" % (a.auth_prefix, ep, a.date)
                cmd = [sys.executable, os.path.join(ROOT, "tools", "run_lp_review_generic.py"),
                       "--batch-dir", a.batch_dir, "--stories", ep, "--auth", auth, "--tag", tag,
                       "--cards", a.cards, "--cand-name", a.cand_name]
                log = open(os.path.join(ROOT, "logs", "_review_%s.log" % tag), "w", encoding="utf-8")
                procs[ep] = subprocess.Popen(cmd, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
                started[ep] = time.strftime("%H:%M:%S")
                print("started review %s (pid=%d) at %s" % (tag, procs[ep].pid, started[ep]))
        json.dump({"batch_dir": a.batch_dir, "started": started,
                   "running": [e for e, p in procs.items() if p.poll() is None],
                   "exit": {e: p.returncode for e, p in procs.items() if p.poll() is not None}},
                  open(state_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        if len(started) == len(eps) and all(p.poll() is not None for p in procs.values()):
            break
        time.sleep(a.poll)

    bad = {e: p.returncode for e, p in procs.items() if p.returncode != 0}
    print("all reviews finished; non-zero:", bad or "none")
    if a.merge_out:
        pattern = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030",
                               "LP_%s_*_NINEDIM_REVIEW.json" % a.tag_prefix)
        out = os.path.join("refs", "experiment_protocol_20261023", "long_probe_20261030", a.merge_out)
        r = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "merge_lp_reviews.py"),
                            "--glob", os.path.relpath(pattern, ROOT).replace("\\", "/"),
                            "--out", out, "--tag", a.tag_prefix], cwd=ROOT)
        print("merge exit", r.returncode)
    return 0


if __name__ == "__main__":
    sys.exit(main())
