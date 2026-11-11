# -*- coding: utf-8 -*-
"""tools/check_prompt_equivalence.py — 两候选在同一 episode 上的逐轮 prompt 字节等价核对。

用途（W17/W16 设计前置）：证明"某变量在该域不触发"（如 12 轮篇上 3600 扩窗从不生效），
避免把结果归功于实际未生效的机制。只调本地机制、零模型调用（capture gateway 返回固定文本）。

用法：
  python tools/check_prompt_equivalence.py --episode benchmark/episodes/E189.json \
      --cand-a v0_minimal_events --cand-b window_expand_b_norecap
输出：逐轮 sha256 与差异位置；全等退出码 0，有差异 1。
"""
import argparse
import hashlib
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIXED_REPLY = '{"response": {"recommended": "收到，我记下了。"}}'


class CaptureGateway:
    def __init__(self):
        self.calls = []

    def generate(self, system, prompt, images=None, tag="main"):
        self.calls.append({"system": system, "prompt": prompt, "tag": tag})
        return {"text": FIXED_REPLY}


def _load_mech(name):
    path = os.path.join(ROOT, "candidates", name, "mechanism", "mechanism.py")
    if not os.path.exists(path):
        raise SystemExit("candidate mechanism 不存在: %s" % path)
    spec = importlib.util.spec_from_file_location("eq_%s" % name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _sha(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def run(name, episode, tmp_root):
    mech = _load_mech(name)
    gw = CaptureGateway()
    sandbox = os.path.join(tmp_root, name)
    os.makedirs(sandbox, exist_ok=True)
    h = mech.init(os.path.join(ROOT, "candidates", name, "assets"),
                  {"persona": episode.get("persona") or {},
                   "initial_kb": episode.get("initial_kb") or {}}, gw, sandbox)
    outs = []
    for i, turn in enumerate(episode.get("turns") or []):
        ctx = {"input": turn.get("input") or {}, "turn_index": i + 1}
        mech.run_turn(h, ctx, gw)
        call = gw.calls[-1]
        outs.append((call["system"], call["prompt"]))
    return outs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--episode", required=True)
    ap.add_argument("--cand-a", required=True)
    ap.add_argument("--cand-b", required=True)
    ap.add_argument("--out", default=None, help="可选：把逐轮 sha256 写入 JSON（供设计文档附录引用）")
    a = ap.parse_args()

    ep = json.load(open(os.path.join(ROOT, a.episode), encoding="utf-8"))
    tmp = os.path.join(ROOT, "logs", "_prompt_equiv_tmp")
    A = run(a.cand_a, ep, tmp)
    B = run(a.cand_b, ep, tmp)
    if len(A) != len(B):
        print("轮数不同: %d vs %d" % (len(A), len(B)))
        return 1

    diffs, rows = [], []
    for i, ((sa, pa), (sb, pb)) in enumerate(zip(A, B), 1):
        same = (sa == sb and pa == pb)
        rows.append({"turn": i, "a": _sha(sa + "\n" + pa), "b": _sha(sb + "\n" + pb), "same": same})
        if not same:
            spot = 0
            n = min(len(pa), len(pb))
            while spot < n and pa[spot] == pb[spot]:
                spot += 1
            diffs.append({"turn": i, "same_system": sa == sb,
                          "a_len": len(pa), "b_len": len(pb), "first_diff_at": spot,
                          "a_snippet": pa[max(0, spot - 30):spot + 60],
                          "b_snippet": pb[max(0, spot - 30):spot + 60]})
    ep_id = ep.get("episode_id")
    print("%s: %d 轮，%d 轮逐字节相同，%d 轮有差异" % (ep_id, len(rows), sum(r["same"] for r in rows), len(diffs)))
    for d in diffs[:5]:
        print("  T%02d first_diff@%d a_len=%d b_len=%d\n    A: %s\n    B: %s" %
              (d["turn"], d["first_diff_at"], d["a_len"], d["b_len"], d["a_snippet"], d["b_snippet"]))
    if a.out:
        json.dump({"kind": "prompt_equivalence_check", "episode": ep_id,
                   "cand_a": a.cand_a, "cand_b": a.cand_b,
                   "n_turns": len(rows), "n_same": sum(r["same"] for r in rows),
                   "turns": rows, "diffs": diffs},
                  open(os.path.join(ROOT, a.out), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print("report:", a.out)
    return 0 if not diffs else 1


if __name__ == "__main__":
    sys.exit(main())
