# -*- coding: utf-8 -*-
"""tools/replay_supersede.py — W05-03 离线重放：F1 已存模型输出回放引擎合并，A/B 原版 vs r7_supersede。

原理：模型回复固定（F1 reply_raw.txt），mock gateway 原样返回；run_turn 的记忆合并路径
（_merge_facts/_merge_tentatives/_apply_updates + r7 的 _supersede_pass）真实执行。
两个 h 状态（原版 E3 链 vs r7 链）吃同一批回复 → 比较终态事实与取代行为。零模型调用。
"""
import glob
import json
import os
import re
import sys
from types import SimpleNamespace

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))

STAGE = os.path.join(ROOT, "logs", "longprobe_f1_live_20261030",
                     "candidate_E061", "_iso_run", "staged")


def load_saved_replies(batch_root, arm, story):
    """按轮序返回 {turn_no: reply_text}。"""
    out = {}
    for d in glob.glob(os.path.join(batch_root, "%s_%s" % (arm, story), "out", "turn_*")):
        m = re.search(r"turn_(\d+)", d)
        p = os.path.join(d, "reply_raw.txt")
        if m and os.path.exists(p):
            out[int(m.group(1))] = open(p, encoding="utf-8").read()
    return out


def load_candidate_module(cand_dir, mod_name):
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        mod_name, os.path.join(cand_dir, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class MockGateway:
    """tag=main 按轮返回已存回复；辅助调用返回空壳。"""

    def __init__(self, replies):
        self.replies = replies
        self.i = 0

    def generate(self, system_prompt, prompt, images=None, tag=""):
        if tag == "main":
            self.i += 1
            text = self.replies.get(self.i, "")
            return {"text": text, "usage": {}}
        return {"text": "", "usage": {}}


def replay(story, cand_dir, mod_name, batch_root):
    mod = load_candidate_module(os.path.join(ROOT, "candidates", cand_dir), mod_name)
    ep = json.load(open(os.path.join(batch_root, "candidate_%s" % story,
                                      "%s.copy.json" % story), encoding="utf-8"))
    replies = load_saved_replies(batch_root, "candidate", story)
    assets = os.path.join(STAGE, "candidates", "reference_product_v1", "assets")
    gw = MockGateway(replies)
    sandbox = os.path.join(ROOT, "logs", "replay_sandbox", mod_name, story)
    os.makedirs(sandbox, exist_ok=True)
    h = mod.init(assets, ep, gw, sandbox)
    for i, t in enumerate(ep.get("turns", []), start=1):
        if i not in replies:
            break
        ctx = {"input": {"messages": t["input"]["messages"], "clock": t["input"].get("clock", ""),
                         "may_decline": t["input"].get("may_decline", False)},
               "turn_index": i}
        mod.run_turn(h, ctx, gw)
    return h


def summarize(h):
    facts = (h.get("mem") or {}).get("facts") or {}
    st = {}
    for fid, f in facts.items():
        st[f.get("status")] = st.get(f.get("status"), 0) + 1
    sup = [f for f in facts.values() if f.get("status") == "superseded"]
    return {"n_facts": len(facts), "status_dist": st,
            "superseded": [{"fid": f.get("fact_id"), "text": (f.get("text") or "")[:50],
                            "by": f.get("superseded_by")} for f in sup]}


if __name__ == "__main__":
    story = sys.argv[1] if len(sys.argv) > 1 else "E067"
    broot = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "logs", "longprobe_f1_live_20261030")
    res = {}
    for label, cand, modn in (("orig(E3)", "action_policy_r4_guard_e3", "replay_orig"),
                              ("r7", "action_policy_r7_supersede", "replay_r7")):
        h = replay(story, cand, modn, broot)
        res[label] = summarize(h)
        print("=" * 70)
        print(label, story, json.dumps(res[label], ensure_ascii=False, indent=1))
    json.dump(res, open(os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                                     "global_harness_program_20261030",
                                     "W05-03_REPLAY_AB_%s.json" % story), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False)
    print("written W05-03_REPLAY_AB_%s.json" % story)
