# tools/test_v3_slim_replay.py — B：v3_system_slim 单变量核验（录制回复重放）
#
# 目的：证明 v3_system_slim 只换了 system 资产，没在装配/记忆写回上"偷改"。
# 把首场 exam1 的逐轮真实模型输出原样喂回【父本】与【v3】两套机制（同一份代码，
# 仅 assets/system_prompt/*.md 不同），断言：
#   1) 每轮 user prompt（模型实际收到的对话体）逐字节相同；
#   2) 三故事写回的沙盒记忆文件（plan.md/raw_topic.md 等）逐字节相同；
#   3) 只有 system 块不同，且 v3 更短（UTF-16 码元）。
#
# 这不证明新生成质量（固定回复=录制回放）；只证明"资产删减"确为单变量、
# 机制与记忆未被包装器改动。质量结论留给 §4 授权内真实 A/B。零网络、零真实调用。
import copy
import glob
import importlib.util
import json
import os
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))

EXAM = os.path.join(ROOT, "logs", "exam1_20261023")
EPISODES = ("E001", "E002", "E003")


def _load(rel_path, name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(ROOT, rel_path))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class Replay:
    """录制回复网关：忽略传入 system/user，按序返回录制的模型原始输出。"""
    def __init__(self, texts):
        self.texts = list(texts)
        self.prompts = []
        self.systems = []

    def generate(self, system, user, images=None, model=None, temperature=None,
                 timeout_s=None, tag="main", attempt_id=None):
        self.systems.append(system)
        self.prompts.append(user)
        return {"text": self.texts.pop(0)}

    def turn_usage(self):
        return {"n_calls": 1}


def recorded_raw_texts(ep):
    out = []
    for cf in sorted(glob.glob(os.path.join(EXAM, ep, "turn_*", "calls.json"))):
        calls = json.load(open(cf, encoding="utf-8"))
        main = next(c for c in calls if c.get("tag", "main") == "main")
        out.append(main.get("raw_text") or "")
    return out


def run_one(mod, name, ep, texts, sandbox):
    episode = json.load(open(os.path.join(ROOT, "benchmark", "episodes",
                                          ep + ".json"), encoding="utf-8"))
    gw = Replay(texts)
    assets = os.path.join(ROOT, "candidates", name, "assets")
    h = mod.init(assets, copy.deepcopy(episode), gw, sandbox)
    for i, t in enumerate(episode["turns"], 1):
        ctx = copy.deepcopy(t)
        ctx["turn_index"] = i
        mod.run_turn(h, ctx, gw)
    files = {}
    for p in sorted(glob.glob(os.path.join(sandbox, "**", "*"), recursive=True)):
        if os.path.isfile(p):
            files[os.path.relpath(p, sandbox).replace("\\", "/")] = \
                open(p, encoding="utf-8").read()
    return gw, files


CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


@check
def replay_user_prompt_and_memory_identical():
    parent = _load("candidates/reference_product_v1/mechanism/mechanism.py", "rp")
    slim = _load("candidates/v3_system_slim/mechanism/mechanism.py", "sl")
    tmp = tempfile.mkdtemp(prefix="v3replay_")
    try:
        n_cases = 0
        prompt_equal = mem_equal = 0
        sys_shorter = 0
        for ep in EPISODES:
            texts = recorded_raw_texts(ep)
            assert len(texts) == 8, "%s 录制回复应 8 轮，实得 %d" % (ep, len(texts))
            pg, pf = run_one(parent, "reference_product_v1", ep, texts,
                             os.path.join(tmp, "parent", ep))
            sg, sf = run_one(slim, "v3_system_slim", ep, texts,
                            os.path.join(tmp, "slim", ep))
            turns = json.load(open(os.path.join(ROOT, "benchmark", "episodes",
                                                ep + ".json"), encoding="utf-8"))["turns"]
            for i, tt in enumerate(turns):
                n_cases += 1
                if pg.prompts[i] == sg.prompts[i]:
                    prompt_equal += 1
                if len(sg.systems[i]) < len(pg.systems[i]):
                    sys_shorter += 1
            if pf == sf:
                mem_equal += 1
        assert prompt_equal == n_cases, \
            "user prompt 有 %d/%d 不一致（资产包装器改了装配?）" % (n_cases - prompt_equal, n_cases)
        assert mem_equal == len(EPISODES), \
            "记忆写回文件有故事与父本不同（包装器偷改状态?）"
        assert sys_shorter == n_cases, \
            "v3 system 未在所有轮更短（%d/%d）" % (sys_shorter, n_cases)
        return ("replay PASS: %d 轮 user prompt 全同、%d 故事记忆写回全同、system 全更短"
                % (n_cases, mem_equal))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


@check
def per_asset_length_report():
    parent = _load("candidates/reference_product_v1/mechanism/mechanism.py", "rp2")
    slim = _load("candidates/v3_system_slim/mechanism/mechanism.py", "sl2")
    names = ("core.md", "naturalness_check.md", "redline.md", "format.md")
    pu = parent.rc.utf16_len
    report = []
    tot_p = tot_s = 0
    for n in names:
        pth = os.path.join(ROOT, "candidates/reference_product_v1/assets/system_prompt", n)
        sth = os.path.join(ROOT, "candidates/v3_system_slim/assets/system_prompt", n)
        pl = pu(open(pth, encoding="utf-8").read())
        sl = pu(open(sth, encoding="utf-8").read())
        report.append((n, pl, sl))
    sysp = pu(parent.build_system_prompt(os.path.join(ROOT, "candidates/reference_product_v1/assets")))
    syss = pu(slim.build_system_prompt(os.path.join(ROOT, "candidates/v3_system_slim/assets")))
    assert syss < sysp, "拼接 system v3(%d) 未短于父本(%d)" % (syss, sysp)
    return ("system UTF-16: parent=%d slim=%d saved=%d(%.1f%%); per-asset=%s"
            % (sysp, syss, sysp - syss, 100 * (sysp - syss) / sysp,
               [(n, p, s) for n, p, s in report]))


def main():
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-40s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-40s %s: %s" % (fn.__name__, type(e).__name__, e))
    out = os.path.join(ROOT, "logs", "v3_slim_replay_result.json")
    json.dump({"checks": results, "failed": failed, "real_model_calls": 0,
               "note": "录制回复重放=单变量核验，非新生成质量"},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("V3 SLIM REPLAY RESULT: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
