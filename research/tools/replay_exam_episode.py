# tools/replay_exam_episode.py — 用已录制的原始回复重放一场考试，摊开"每轮真正拼出的提示词 + 记忆文件"
#
# 目的：首场实测暴露的语义失败（E001_T06「上回那句我没记住」）必须分清责任：
#   a) 事实根本没进 prompt  → harness 的记忆投影把事实丢了（harness 缺陷）
#   b) 事实进了 prompt 但模型没用 → 提示结构/强度问题（harness 仍可改机制）
# 做法：读取 logs/<exam>/<EP>/turn_*/calls.json 里的 raw_text 作为"固定回复"，
#       离线重跑同一 episode，逐轮 dump prompt 与 memory/*.md。
# 不联网、不发 API、不计授权额度。
import argparse
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAND = os.path.join(ROOT, "candidates", "reference_product_v1")
MECH_PATH = os.path.join(CAND, "mechanism", "mechanism.py")
ASSETS = os.path.join(CAND, "assets")

if CAND not in sys.path:
    sys.path.insert(0, CAND)


def load_mechanism():
    spec = importlib.util.spec_from_file_location("mech_replay", MECH_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class ReplayGateway:
    """按顺序返回已录制的原始回复文本，并记录本轮真实拼出的 prompt。"""

    def __init__(self, texts):
        self.texts = list(texts)
        self.prompts = []
        self.systems = []

    def generate(self, system, prompt, images=None, tag="main", **_kw):
        self.systems.append(system)
        self.prompts.append(prompt)
        text = self.texts.pop(0) if self.texts else "{}"
        return {"text": text}


def read_recorded(exam_dir, ep_id, turns):
    """取出每轮录制下来的 raw_text（没有就跑不下去，明确报错）。"""
    texts = []
    for i, t in enumerate(turns, 1):
        d = os.path.join(exam_dir, ep_id, "turn_%02d_%s" % (i, t["case_id"]))
        cp = os.path.join(d, "calls.json")
        if not os.path.isfile(cp):
            raise SystemExit("缺少录制调用：%s" % cp)
        calls = json.load(open(cp, encoding="utf-8"))
        main = [c for c in calls if c.get("tag", "main") == "main"]
        if not main:
            raise SystemExit("该轮无 main 调用：%s" % cp)
        texts.append(main[0].get("raw_text") or "")
    return texts


def dump_memory(root, out_dir, tag):
    kb = os.path.join(root, "memory", "kb")
    files = ["moment/recent.md", "moment/scene.md", "moment/plan.md",
             "moment/topic.md", ".meta.json"]
    got = {}
    for rel in files:
        p = os.path.join(kb, rel.replace("/", os.sep))
        got[rel] = open(p, encoding="utf-8").read() if os.path.isfile(p) else None
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "memory_%s.json" % tag), "w",
              encoding="utf-8") as f:
        json.dump(got, f, ensure_ascii=False, indent=1)
    return got


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--exam", required=True, help="考试目录，如 logs/exam1_20261023")
    ap.add_argument("--episode", required=True, help="episode_id，如 E001")
    ap.add_argument("--out", required=True, help="重放证据输出目录")
    args = ap.parse_args(argv)

    exam_dir = os.path.join(ROOT, args.exam) if not os.path.isabs(args.exam) else args.exam
    out_dir = os.path.join(ROOT, args.out) if not os.path.isabs(args.out) else args.out
    ep_path = os.path.join(ROOT, "benchmark", "episodes", "%s.json" % args.episode)
    ep = json.load(open(ep_path, encoding="utf-8"))
    turns = ep["turns"]

    texts = read_recorded(exam_dir, args.episode, turns)
    mech = load_mechanism()
    sandbox = os.path.join(out_dir, "sandbox")
    os.makedirs(sandbox, exist_ok=True)
    gw = ReplayGateway(texts)
    h = mech.init(ASSETS, ep, gw, sandbox)

    report = {"episode_id": args.episode, "turns": []}
    for i, t in enumerate(turns, 1):
        t2 = dict(t)
        t2["turn_index"] = i
        out = mech.run_turn(h, t2, gw)
        prompt = gw.prompts[-1]
        rec = os.path.join(exam_dir, args.episode,
                           "turn_%02d_%s" % (i, t["case_id"]), "calls.json")
        rec_prompt = None
        if os.path.isfile(rec):
            cs = [c for c in json.load(open(rec, encoding="utf-8"))
                  if c.get("tag", "main") == "main"]
            if cs:
                rec_prompt = (cs[0].get("system") or "") + "\n" + (cs[0].get("user") or "")
        mem = dump_memory(sandbox, out_dir, "t%02d" % i)
        row = {
            "turn": i,
            "case_id": t["case_id"],
            "prompt_chars": len(prompt),
            "recorded_prompt_chars": len(rec_prompt) if rec_prompt else None,
            "prompt_sha_equal": None,
            "mem_chars": {k: (len(v) if v else 0) for k, v in mem.items()},
            "prompt_path": None,
        }
        pf = os.path.join(out_dir, "prompt_t%02d_%s.txt" % (i, t["case_id"]))
        with open(pf, "w", encoding="utf-8") as f:
            f.write(prompt)
        row["prompt_path"] = os.path.relpath(pf, ROOT)
        report["turns"].append(row)
        print("T%02d %-10s prompt=%d chars  plan=%d scene=%d recent=%d void" % (
            i, t["case_id"], len(prompt),
            row["mem_chars"]["moment/plan.md"], row["mem_chars"]["moment/scene.md"],
            row["mem_chars"]["moment/recent.md"]))

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "replay_report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print("REPLAY DONE ->", os.path.relpath(out_dir, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
