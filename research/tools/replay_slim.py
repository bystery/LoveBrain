# -*- coding: utf-8 -*-
"""tools/replay_slim.py — W09-02 同输入重放：证明 r9_slim 改动范围（零模型调用）。

方法：MockGateway 回放 fs1_a_live2 存档回复（reply_raw.txt），分别经
r7（基线）与 r9_slim（瘦身，自带 assets）候选跑完整 init+run_turn；
gateway 逐轮捕获 (system, user) prompt。断言：
  A. 状态零漂移：两候选终态 facts 摘要逐字相同
  B. 范围证明：slim prompt 中预期删除片段消失、信息边界/输出合同锚保留
  C. 每轮 prompt 字符差（瘦身节省量）
输出：W09-02_REPLAY_SLIM_20261031.json
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))

from replay_supersede import load_candidate_module  # noqa: E402

BATCH = os.path.join(ROOT, "logs", "fs1_a_live2_20261030")
STORIES = ["E021", "E033"]
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                   "W09-02_REPLAY_SLIM_20261031.json")
SLIM_DIR = os.path.join(ROOT, "candidates", "action_policy_r9_slim")
BASE_DIR = os.path.join(ROOT, "candidates", "action_policy_r7_supersede")

GONE_SYSTEM = ["不暴露这是 AI 或辅助写的。", "## 期望的可用回复",
               "不否定她的情绪、不冷暴力、不故意激醋、不贬低她自我价值。"]
GONE_USER = ["【回扣有据】凡提到'上次/之前/你说的X'"]
KEPT_SYSTEM = ["可公开事实", "私下目标", "未发送草稿", "JSON Schema（回复）"]
KEPT_USER = ["【单 JSON 硬约束】", "【钩子承接】", "【目标让位】", "【自我状态有据】"]


def load_saved_replies(story):
    out = {}
    for d in glob.glob(os.path.join(BATCH, "candidate_%s" % story, "out", "turn_*")):
        m = re.search(r"turn_(\d+)", d)
        p = os.path.join(d, "reply_raw.txt")
        if m and os.path.exists(p):
            out[int(m.group(1))] = open(p, encoding="utf-8").read()
    return out


class CaptureGateway:
    """tag=main 按调用序返回存档回复并捕获 prompt；其余 tag 空壳。"""

    def __init__(self, replies):
        self.replies = replies
        self.i = 0
        self.captured = []

    def generate(self, system_prompt, prompt, images=None, tag=""):
        if tag == "main":
            self.i += 1
            self.captured.append({"turn": self.i, "system": system_prompt, "user": prompt})
            return {"text": self.replies.get(self.i, ""), "usage": {}}
        return {"text": "", "usage": {}}


def mem_digest(h):
    facts = (h.get("mem") or {}).get("facts") or {}
    return {"n": len(facts),
            "digest": sorted("%s|%s|%s" % (k, v.get("status"), (v.get("text") or "")[:40])
                             for k, v in facts.items())}


def run_candidate(cand_dir, story, replies, ep):
    mod = load_candidate_module(cand_dir, "mech_slim_%s_%s" % (os.path.basename(cand_dir), story))
    assets = os.path.join(cand_dir, "assets")
    gw = CaptureGateway(replies)
    sandbox = os.path.join(ROOT, "logs", "_replay_slim_tmp", os.path.basename(cand_dir), story)
    os.makedirs(sandbox, exist_ok=True)
    h = mod.init(assets, ep, gw, sandbox)
    for i, t in enumerate(ep.get("turns", []), start=1):
        if i not in replies:
            break
        ctx = {"input": {"messages": t["input"]["messages"], "clock": t["input"].get("clock", ""),
                         "may_decline": t["input"].get("may_decline", False)},
               "turn_index": i}
        mod.run_turn(h, ctx, gw)
    return {"captured": gw.captured, "mem": mem_digest(h)}


def main():
    report = {"kind": "w0902_replay_slim", "date": "2026-10-31",
              "batch": "logs/fs1_a_live2_20261030", "stories": {}}
    ok_all = True
    for story in STORIES:
        replies = load_saved_replies(story)
        if not replies:
            print("no saved replies for", story)
            ok_all = False
            continue
        ep = json.load(open(os.path.join(BATCH, "candidate_%s" % story,
                                         "%s.copy.json" % story), encoding="utf-8"))
        base = run_candidate(BASE_DIR, story, replies, ep)
        slim = run_candidate(SLIM_DIR, story, replies, ep)
        res = {"turns_replayed": len(base["captured"]),
               "base_mem": base["mem"], "slim_mem": slim["mem"],
               "state_identical": base["mem"]["digest"] == slim["mem"]["digest"],
               "anchor_checks": {}, "per_turn_delta": []}
        if not res["state_identical"]:
            ok_all = False
        all_sys = "\n".join(c["system"] for c in slim["captured"])
        all_usr = "\n".join(c["user"] for c in slim["captured"])
        for g in GONE_SYSTEM:
            res["anchor_checks"]["gone_system:" + g[:12]] = g not in all_sys
        for g in GONE_USER:
            res["anchor_checks"]["gone_user:" + g[:12]] = g not in all_usr
        for k in KEPT_SYSTEM:
            res["anchor_checks"]["kept_system:" + k[:12]] = k in all_sys
        for k in KEPT_USER:
            res["anchor_checks"]["kept_user:" + k[:12]] = k in all_usr
        res["anchors_ok"] = all(res["anchor_checks"].values())
        if not res["anchors_ok"]:
            ok_all = False
            res["failed_anchors"] = [k for k, v in res["anchor_checks"].items() if not v]
        by_turn_base = {c["turn"]: c for c in base["captured"]}
        for c in slim["captured"]:
            b = by_turn_base.get(c["turn"])
            if b:
                res["per_turn_delta"].append({
                    "turn": c["turn"], "sys_saved": len(b["system"]) - len(c["system"]),
                    "user_saved": len(b["user"]) - len(c["user"])})
        report["stories"][story] = res
        print("%s: turns=%d state_identical=%s anchors_ok=%s" % (
            story, res["turns_replayed"], res["state_identical"], res["anchors_ok"]))
    ts = [x for r in report["stories"].values() for x in r["per_turn_delta"]]
    report["totals"] = {
        "sys_saved_per_turn_mean": round(sum(x["sys_saved"] for x in ts) / max(1, len(ts)), 1),
        "user_saved_per_turn_mean": round(sum(x["user_saved"] for x in ts) / max(1, len(ts)), 1),
        "verdict": "PASS" if ok_all else "FAIL"}
    json.dump(report, open(OUT, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("sys saved/turn=%s user saved/turn=%s verdict=%s" % (
        report["totals"]["sys_saved_per_turn_mean"],
        report["totals"]["user_saved_per_turn_mean"], report["totals"]["verdict"]))
    print("written", OUT)


if __name__ == "__main__":
    main()
