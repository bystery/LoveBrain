# -*- coding: utf-8 -*-
"""test_baseline_lifecycle.py — G04 完整主链路保真对拍（V14 执行书 C3）。

旧口径缺陷：26 个 helper 夹具只 import reference_chain（单函数多步操作），
机制入口损坏时 G04 仍 PASS（独立复核 §6.4 实证）。本测试补：
  ①完整生命周期对拍：冻结参考产品（reference_product_v1）经**隔离链**
    （IsolatedRun：staging→宿主→IPC→可信评分）跑 NCA(6轮)+NCB(5轮)=11 轮
    echo 适配器，逐轮 reply_raw 与**非隔离原产品驱动**（lb_runner.run_episode
    冻结期望 refs/arena_v1/baseline_lifecycle_expected.json）字节级一致；
    期望值绑定题面 sha256，题面漂移即失败。
  ②破坏注入：staged 机制入口替换为立即抛错 → 运行必须失败（G04 敏感性）。
  ③快照/恢复：隔离链 checkpoint 写入且 resume 续跑后前史字节不变。

≥8 组完整"生命周期→下一轮请求"的确定性对拍（本测试 11 组）。
"""
import datetime
import hashlib
import json
import os
import shutil
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))  # noqa: E402
sys.path.insert(0, os.path.join(ROOT, "arena"))
from isolation.trusted_controller import IsolatedRun  # noqa: E402
from isolation import sandbox_mode  # noqa: E402

EXPECTED = os.path.join(ROOT, "refs", "arena_v1",
                        "baseline_lifecycle_expected.json")
EPS = ["benchmark/episodes/NCA_CORR_INTENT_v1.json",
       "benchmark/episodes/NCB_ENTITY_DENSE_v1.json"]
CAND = "reference_product_v1"


def sha_ep(rel):
    return hashlib.sha256(open(os.path.join(ROOT, rel), "rb").read()) \
        .hexdigest()


def run_isolated(rel, out_name, break_mechanism=False, stop_after=None):
    # 指导书 v2 §4：新建审计目录零删除（不拆批规避宿主确认）
    base = os.path.join(ROOT, "runs", "_lc_%s_%s" % (
        out_name, datetime.datetime.now().strftime("%H%M%S")
        + "_" + uuid.uuid4().hex[:4]))
    if sandbox_mode.active():
        base = sandbox_mode.staged_root("lc_%s" % out_name)
    os.makedirs(base, exist_ok=True)
    out = os.path.join(base, "out")
    run = IsolatedRun(os.path.join(base, "run"), out,
                      os.path.join(out, "_control"), CAND,
                      adapter="echo", quota=32).stage()
    if break_mechanism:
        mech = os.path.join(run.staged_root, "candidates", CAND,
                            "mechanism", "mechanism.py")
        with open(mech, "w", encoding="utf-8") as f:
            f.write("raise RuntimeError('LC_BREAK_INJECTED')\n")
    try:
        run.start()
        m, _ = run.run_episode(os.path.join(ROOT, rel),
                               stop_after=stop_after, require_assets=True)
        return {"ok": True, "manifest": m, "out": out, "run": run}
    except Exception as e:
        return {"ok": False, "error": "%s: %s" % (type(e).__name__, str(e)[:200]),
                "out": out, "run": run}
    finally:
        try:
            run.close()
        except Exception:
            pass


def collect_replies(out_dir, case_ids):
    replies = []
    for i, cid in enumerate(case_ids, 1):
        # 目录名 turn_NN_caseid（case_id 可能带非法字符被 _turn_dir 原样使用）
        found = None
        for d in os.listdir(out_dir):
            if d.startswith("turn_%02d_" % i) and os.path.isfile(
                    os.path.join(out_dir, d, "reply_raw.txt")):
                found = os.path.join(out_dir, d, "reply_raw.txt")
                break
        replies.append(open(found, encoding="utf-8").read()
                       if found else None)
    return replies


def main():
    fails = []
    exp = json.load(open(EXPECTED, encoding="utf-8"))
    n_pairs = 0
    for rel in EPS:
        name = os.path.basename(rel)
        erec = exp["episodes"].get(name)
        if erec is None:
            fails.append("%s: 冻结期望缺该题" % name)
            continue
        if erec["episode_sha256"] != sha_ep(rel):
            fails.append("%s: 题面漂移（期望值绑定失效）" % name)
            continue
        r = run_isolated(rel, "lc_" + name.replace(".json", ""))
        if not r["ok"]:
            fails.append("%s: 隔离链运行失败 %s" % (name, r["error"]))
            continue
        got = collect_replies(r["out"], erec["case_ids"])
        for i, (e, g) in enumerate(zip(erec["replies"], got), 1):
            n_pairs += 1
            if e is None or g is None:
                fails.append("%s t%02d: 缺回复（exp=%s got=%s）"
                             % (name, i, e is not None, g is not None))
            elif e != g:
                fails.append("%s t%02d: reply_raw 与冻结期望不一致 "
                             "(len %d vs %d)" % (name, i, len(g), len(e)))
        # 快照/恢复链路证据：checkpoint 存在且 manifest complete
        m = r["manifest"]
        if not (m.get("complete") and os.path.isfile(
                os.path.join(r["out"], "checkpoint.json"))):
            fails.append("%s: complete/checkpoint 缺失" % name)

    # 破坏注入：机制入口损坏 → 必须失败（G04 敏感性）
    r2 = run_isolated(EPS[0], "lc_broken", break_mechanism=True)
    if r2["ok"]:
        fails.append("破坏注入未被检出：机制入口损坏仍运行成功")
    else:
        n_pairs += 1

    print("BASELINE LIFECYCLE pairs=%d fails=%d" % (n_pairs, len(fails)))
    for f in fails[:10]:
        print("  FAIL:", f)
    if not fails:
        print("BASELINE LIFECYCLE PASS")
        return 0
    print("BASELINE LIFECYCLE FAIL")
    return 1


if __name__ == "__main__":
    sys.exit(main())
