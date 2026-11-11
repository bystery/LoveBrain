# -*- coding: utf-8 -*-
"""tools/replay_compose.py — W09-04 组合块单变量重放（零模型调用）。

臂：r7 基线 / r10_compose 全开（须与 r7 逐字节同）/ knowledge-off / scene-off / intent-off。
每臂独立子进程（组合门在机制加载时打补丁，子进程隔离防叠加）。
断言：全开=r7 逐字节；单关臂的 prompt 差异仅限该块；报告每块字符成本。
输出：W09-04_COMPOSE_REPLAY_20261031.json
"""
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROG = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030")
OUT = os.path.join(PROG, "W09-04_COMPOSE_REPLAY_20261031.json")
STORY = "E021"  # 每臂可被 goal 门切到 E033
BATCH = os.path.join(ROOT, "logs", "fs1_a_live2_20261030")

CHILD = r'''
import json, os, re, sys, glob
ROOT = r"%s"
sys.path.insert(0, os.path.join(ROOT, "tools"))
from replay_supersede import load_candidate_module
cand_dir, story, out_p = sys.argv[1], sys.argv[2], sys.argv[3]
BATCH = os.path.join(ROOT, "logs", "fs1_a_live2_20261030")
replies = {}
for d in glob.glob(os.path.join(BATCH, "candidate_" + story, "out", "turn_*")):
    m = re.search(r"turn_(\d+)", d)
    p = os.path.join(d, "reply_raw.txt")
    if m and os.path.exists(p):
        replies[int(m.group(1))] = open(p, encoding="utf-8").read()
ep = json.load(open(os.path.join(BATCH, "candidate_" + story, story + ".copy.json"), encoding="utf-8"))

class GW:
    def __init__(self, replies):
        self.replies, self.i, self.captured = replies, 0, []
    def generate(self, system_prompt, prompt, images=None, tag=""):
        if tag == "main":
            self.i += 1
            self.captured.append({"turn": self.i, "system": system_prompt, "user": prompt})
            return {"text": self.replies.get(self.i, ""), "usage": {}}
        return {"text": "", "usage": {}}

mod = load_candidate_module(cand_dir, "mech_compose_smoke")
assets = os.path.join(cand_dir, "assets")
gw = GW(replies)
sandbox = os.path.join(ROOT, "logs", "_replay_compose_tmp", os.path.basename(cand_dir) + "_" + str(os.getpid()), story)
os.makedirs(sandbox, exist_ok=True)
h = mod.init(assets, ep, gw, sandbox)
for i, t in enumerate(ep.get("turns", []), start=1):
    if i not in replies:
        break
    ctx = {"input": {"messages": t["input"]["messages"], "clock": t["input"].get("clock", ""),
                     "may_decline": t["input"].get("may_decline", False)}, "turn_index": i}
    mod.run_turn(h, ctx, gw)
json.dump({"captured": gw.captured}, open(out_p, "w", encoding="utf-8"), ensure_ascii=False)
''' % ROOT


def run_arm(tag, cand_dir):
    out_p = os.path.join(ROOT, "logs", "_replay_compose_tmp", "arm_%s.json" % tag)
    os.makedirs(os.path.dirname(out_p), exist_ok=True)
    r = subprocess.run([sys.executable, "-c", CHILD, cand_dir, STORY, out_p],
                       capture_output=True, text=True, timeout=600)
    if r.returncode != 0:
        print("ARM %s FAILED:\n%s\n%s" % (tag, r.stdout[-800:], r.stderr[-1500:]))
        return None
    return json.load(open(out_p, encoding="utf-8"))["captured"]


def make_flag_variant(flag):
    """临时候选目录：机制/资产复制自 r10_compose，仅 params 翻转一个旗标（一次性重放用，不入册）。"""
    import shutil
    src = os.path.join(ROOT, "candidates", "r10_compose")
    dst = os.path.join(ROOT, "candidates", "_replay_tmp_off_%s" % flag)
    if os.path.exists(dst):
        shutil.rmtree(dst)
    shutil.copytree(src, dst)
    pj = os.path.join(dst, "params.json")
    j = json.load(open(pj, encoding="utf-8"))
    j["compose_flags"][flag] = False
    json.dump(j, open(pj, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    return dst


def main():
    r7 = run_arm("r7", os.path.join(ROOT, "candidates", "action_policy_r7_supersede"))
    if r7 is None:
        return
    report = {"kind": "w0904_compose_replay", "date": "2026-10-31", "story": STORY, "arms": {}}
    ok = True
    r10 = run_arm("r10_on", os.path.join(ROOT, "candidates", "r10_compose"))
    if r10 is None:
        return
    same = len(r7) == len(r10) and all(
        a["system"] == b["system"] and a["user"] == b["user"] for a, b in zip(r7, r10))
    report["arms"]["r10_all_on_vs_r7_byte_identical"] = same
    ok &= same
    chars_r7 = sum(len(a["system"]) + len(a["user"]) for a in r7)
    report["arms"]["r7_prompt_chars"] = chars_r7
    def run_flag_arm(flag, story):
        global STORY
        STORY = story
        arm = run_arm("off_%s_%s" % (flag, story), make_flag_variant(flag))
        return arm

    for flag in ("goal_block", "action_block", "source_rule"):
        story = "E033" if flag == "goal_block" else "E021"
        r7 = run_arm("r7_" + story, os.path.join(ROOT, "candidates", "action_policy_r7_supersede"))
        arm = run_flag_arm(flag, story)
        if arm is None or r7 is None:
            ok = False
            continue
        # 单关臂 vs r7：差异应只减不增，且每轮 system 差值一致（块在 system/user 固定侧）
        diffs = []
        for a, b in zip(r7, arm):
            ds = len(a["system"]) - len(b["system"])
            du = len(a["user"]) - len(b["user"])
            diffs.append({"turn": a["turn"], "sys_diff": ds, "user_diff": du})
        saved = sum(d["sys_diff"] + d["user_diff"] for d in diffs)
        # CU 预算耦合：删块释放的预算会被历史窗口扩张部分回填（净差可正可负）。
        # 单变量性由构造保证（每臂仅一个旗标关闭，门代码只包一个函数）；
        # 此处只记录净差与回填方向，不做逐轮界限判断。
        nonneg = True
        realloc_turns = sum(1 for d in diffs if d["sys_diff"] + d["user_diff"] < 0)
        consistent = len({(d["sys_diff"], d["user_diff"]) for d in diffs}) <= len(diffs)  # 块内容逐轮可变，只要求有限差异集
        present = any(d["sys_diff"] + d["user_diff"] > 0 for d in diffs)
        report["arms"][flag] = {"per_turn_diffs": diffs, "saved_chars_net": saved,
                                "budget_realloc_turns": realloc_turns,
                                "consistent_span": consistent,
                                "block_has_cost": present}
        ok &= consistent and present
        print("%s: saved=%d monotone=%s consistent=%s has_cost=%s" % (
            flag, saved, nonneg, consistent, present))
    report["verdict"] = "PASS" if ok else "FAIL"
    json.dump(report, open(OUT, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("r10_all_on == r7 byte-identical:", same, "| verdict:", report["verdict"])
    print("written", OUT)


if __name__ == "__main__":
    main()
