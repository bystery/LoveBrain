# lb_verify_phase0.py — Phase 0 验收断言器（refs/REFACTOR_PLAN_PHASE0.md §6）
# 用法：
#   python arena/lb_verify_phase0.py --episode benchmark/episodes/PHASE0_GEN_220.json \
#       --run logs/phase0/beta_full [--run2 logs/phase0/beta_resume]
# 断言（每条独立 PASS/FAIL，全部证据来自落盘文件）：
#   V2 完整性        turns==声明值 且 complete
#   V3 召回/更新/抑制 探针轮 scoring 判定
#   V4 意图对照      t200/202 命中意图信号、t203 无意图不命中
#   V5 断点等价      run 与 run2 每轮 reply_raw/scoring 字节一致
#   V6a 无冒充      第 i 轮 recommended 文本不出现在任何 >i 轮的网关入参
#   V6b gold 不泄漏  任何网关入参不含 gold 字段名（stale_fact_must_not 等）

import argparse
import json
import os
import sys

GOLD_MARKERS = ['"stale_fact_must_not"', '"must_keywords_any"', '"geo_impossible_must_not"',
                '"intent_keywords_any"', '"forbidden_new_facts"', '"expect_decline"',
                '"mock_response"']


def load_json(p):
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)


def turn_dirs(run_dir):
    ds = []
    for name in sorted(os.listdir(run_dir)):
        if name.startswith("turn_") and os.path.isdir(os.path.join(run_dir, name)):
            ds.append(os.path.join(run_dir, name))
    return ds


def recommended_text(turn_dir):
    p = os.path.join(turn_dir, "reply_raw.txt")
    if not os.path.exists(p):
        return None
    raw = open(p, "r", encoding="utf-8").read()
    import re
    m = re.search(r"\{.*\}", raw, re.DOTALL)
    if not m:
        return raw
    try:
        out = json.loads(m.group(0))
        resp = out.get("response") or {}
        rec = resp.get("recommended")
        return rec if isinstance(rec, str) else None
    except Exception:
        return None


def gw_inputs(turn_dir):
    """该轮所有网关入参文本（user 优先，system 附带）。"""
    texts = []
    p = os.path.join(turn_dir, "calls.json")
    if os.path.exists(p):
        for c in load_json(p):
            texts.append(c.get("user") or "")
            texts.append(c.get("system") or "")
    for fn in ("prompt_user.txt", "prompt_system.txt"):   # v1 路径落盘
        p2 = os.path.join(turn_dir, fn)
        if os.path.exists(p2):
            texts.append(open(p2, "r", encoding="utf-8").read())
    return texts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--episode", required=True)
    ap.add_argument("--run", required=True)
    ap.add_argument("--run2", default=None, help="resume 跑的输出目录（V5 等价断言）")
    args = ap.parse_args()

    ep = load_json(args.episode)
    turns = ep["turns"]
    dirs = turn_dirs(args.run)
    results = []

    def check(vid, desc, ok, evidence=""):
        results.append((vid, desc, bool(ok), evidence))

    # ---- V2 完整性 ----
    mf = load_json(os.path.join(args.run, "episode_manifest.json"))
    check("V2", "episode 完整跑完 %d 轮" % len(turns),
          mf["turns"] == len(turns) and mf.get("complete"),
          "turns=%s complete=%s" % (mf["turns"], mf.get("complete")))

    # ---- 逐轮 gold 泄漏 + 冒充 ----
    leak = []
    impersonate = []
    for i, td in enumerate(dirs):
        inputs = gw_inputs(td)
        for t in inputs:
            for mk in GOLD_MARKERS:
                if mk in t:
                    leak.append((td, mk))
        rec = recommended_text(td)
        if rec:
            for td2 in dirs[i + 1:]:
                if rec in "\n".join(gw_inputs(td2)):
                    impersonate.append((os.path.basename(td), os.path.basename(td2)))
    check("V6b", "gold 字段不出现在任何网关入参", not leak, str(leak[:3]))
    check("V6a", "候选回复不冒充后续已发送消息", not impersonate, str(impersonate[:3]))

    # ---- 探针判定（V3/V4） ----
    def scoring_of(case_id):
        for i, t in enumerate(turns):
            if t.get("case_id") == case_id:
                for td in dirs:
                    if td.endswith("_" + case_id):
                        return load_json(os.path.join(td, "scoring.json"))
        return None

    def probe(case_id, check_prefix, want_pass=True):
        sc = scoring_of(case_id)
        if sc is None:
            return False, "turn dir not found"
        cs = [c for c in sc["checks"] if c["id"].startswith(check_prefix)]
        if not cs:
            return False, "no check %s" % check_prefix
        ok = all(c["pass"] for c in cs) if want_pass else not any(c["pass"] for c in cs)
        return ok, ";".join("%s=%s" % (c["id"], c["pass"]) for c in cs)

    for cid, prefix, want, desc in [
            ("GEN_t160", "must_kw", True, "V3 召回：150 轮前『九价』事实被召回"),
            ("GEN_t140", "must_kw", True, "V3 更新：爬山取消的更新被记住"),
            ("GEN_t080", "stale_kw", True, "V3 抑制：水杯旧事未被重提"),
            ("GEN_t200", "intent_kw", True, "V4 意图对照：调情意图→调情信号"),
            ("GEN_t202", "intent_kw", True, "V4 意图对照：克制意图→克制信号"),
            ("GEN_t210", "decline", True, "V4 静默轮：不建议发送被接受")]:
        ok, ev = probe(cid, prefix, want)
        check(cid, desc, ok, ev)

    # t203 无意图：调情词不得出现（must_not）
    ok, ev = probe("GEN_t203", "must_not_kw", True)
    check("GEN_t203", "V4 意图对照：无意图→调情词零命中", ok, ev)

    # ---- V5 断点等价 ----
    if args.run2:
        dirs2 = turn_dirs(args.run2)
        mismatch = []
        n_cmp = min(len(dirs), len(dirs2))
        for td, td2 in zip(dirs[:n_cmp], dirs2[:n_cmp]):
            for fn in ("reply_raw.txt", "scoring.json"):
                a = open(os.path.join(td, fn), "rb").read()
                b = open(os.path.join(td2, fn), "rb").read()
                if a != b:
                    mismatch.append((os.path.basename(td), fn))
        check("V5", "中断续跑与全程运行逐轮字节一致（reply+scoring）",
              n_cmp == len(dirs) and not mismatch,
              "cmp=%d mismatch=%s" % (n_cmp, mismatch[:3]))

    # ---- 汇总 ----
    n_pass = sum(1 for r in results if r[2])
    print("=" * 72)
    for vid, desc, ok, ev in results:
        print("[%s] %s %s%s" % ("PASS" if ok else "FAIL", vid, desc,
                                ("  | " + ev if ev and not ok else "")))
    print("=" * 72)
    print("TOTAL: %d/%d PASS" % (n_pass, len(results)))
    sys.exit(0 if n_pass == len(results) else 1)


if __name__ == "__main__":
    main()
