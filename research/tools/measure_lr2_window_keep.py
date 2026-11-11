# -*- coding: utf-8 -*-
"""G-3: live 批产物窗口保持率确定性测量（零模型调用）。

对 logs/longprobe_lr2_live_*/<arm>_<ep>/out/turn_NN_<case>/calls.json 内实际落盘的
system+user 全文，机械判定该轮"应有的 GT 事实"是否仍在窗内：
  GT 集合 = 故事 reference.json 的 required_memory_atoms（含 quote 与 value）
          + 从 episode.json 台词抽取的数字/日期 token（按轮次累积）。
匹配 = 归一化精确匹配（NFKC + 去空白 + casefold）+ quote/value 原文匹配。
归一化后仍不中的记 miss 并导出清单，供人工复核等义改写（不自动判死）。

用法:
  python tools/measure_lr2_window_keep.py --batch-dir logs/longprobe_lr1_live_20261030 \
      --episodes E061 --out /tmp/lr1_keep_check.json
"""
import argparse
import glob
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ANCHOR_BOUNDARIES = [6, 12, 18, 24, 32, 40]

# 数字/日期 token：数字段可被 CJK 日历单位或日期分隔符粘连（"5月20号"/"6到12个月"/"18:30"）。
_NUM_TOKEN_RE = re.compile(r"\d+(?:[年月日号时分周点:./\-%到~至]\d*)*")


def norm(s):
    if s is None:
        return ""
    s = unicodedata.normalize("NFKC", str(s))
    return re.sub(r"\s+", "", s).casefold()


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def turn_index(case_id):
    m = re.search(r"_T(\d+)$", case_id or "")
    return int(m.group(1)) if m else None


def find_story_files(ep):
    """在 benchmark/stories_v2 各 stage 下定位 <ep>/{episode,reference}.json。"""
    pattern = os.path.join(ROOT, "benchmark", "stories_v2", "*", ep)
    hits = sorted(glob.glob(pattern))
    if not hits:
        raise SystemExit("找不到故事包: benchmark/stories_v2/*/%s" % ep)
    story_dir = hits[0]
    if len(hits) > 1:
        print("[warn] %s 存在于多个 stage，取 %s" % (ep, story_dir))
    return (os.path.join(story_dir, "episode.json"),
            os.path.join(story_dir, "reference.json"))


def build_gt(ep):
    """返回 (dialogue_tokens, atoms)：
    dialogue_tokens: {turn_no: [(token, source_text_snippet)]}
    atoms: {turn_no: [atom_dict]}（按 atom source.case_id 归轮）"""
    ep_path, ref_path = find_story_files(ep)
    episode = load_json(ep_path)
    reference = load_json(ref_path)

    dialogue_tokens = {}
    for t in episode["turns"]:
        no = turn_index(t["case_id"])
        if no is None:
            continue
        toks = []
        for msg in t.get("input", {}).get("messages", []):
            text = msg.get("text", "")
            for m in _NUM_TOKEN_RE.finditer(text):
                toks.append((m.group(0), text[:40]))
        dialogue_tokens[no] = toks

    atoms = {}
    for case_id, rt in reference["turns"].items():
        no = turn_index(case_id)
        if no is None:
            continue
        atoms[no] = list(rt.get("required_memory_atoms", []))
    return dialogue_tokens, atoms


def collect_turn_files(batch_dir, ep, arms):
    """[(arm, turn_no, case_id, calls_json_path)]"""
    rows = []
    for arm in arms:
        arm_dir = os.path.join(batch_dir, "%s_%s" % (arm, ep))
        hits = []
        if os.path.isdir(arm_dir):
            hits = sorted(glob.glob(os.path.join(arm_dir, "out", "turn_*")))
        if not hits:
            # 回退 _reports/EXAM_<arm>/<ep>/turn_* 布局
            hits = sorted(glob.glob(os.path.join(batch_dir, "_reports", "EXAM_%s" % arm,
                                                 ep, "turn_*")))
        for d in hits:
            calls = os.path.join(d, "calls.json")
            m = re.match(r"turn_(\d+)_(.+)$", os.path.basename(d))
            if not (os.path.isfile(calls) and m):
                continue
            rows.append((arm, int(m.group(1)), m.group(2), calls))
    return rows


def turn_haystack(calls_path):
    """单轮全部调用（main 优先）的 system+user 拼接；诊断为什么漏检时不丢候选来源。"""
    entries = load_json(calls_path)
    mains = [c for c in entries if c.get("tag") == "main"]
    use = mains if mains else entries
    parts = []
    for c in use:
        parts.append(c.get("system", "") or "")
        parts.append(c.get("user", "") or "")
    return norm("".join(parts)), len(use)


def measure(batch_dir, episodes, arms):
    report = {"batch_dir": batch_dir, "episodes": {}, "arms": arms}
    for ep in episodes:
        dialogue_tokens, atoms = build_gt(ep)
        rows = collect_turn_files(batch_dir, ep, arms)
        if not rows:
            report["episodes"][ep] = {"error": "batch-dir 内未找到任何 turn 目录"}
            continue
        per_arm = {}
        for arm, no, case_id, calls_path in rows:
            hay, n_calls = turn_haystack(calls_path)
            misses = []
            gt = 0
            hit = 0

            # 历史轮次的 GT 全部参与判定（<= 当前轮）
            for t_no in sorted(atoms):
                if t_no > no:
                    continue
                for atom in atoms[t_no]:
                    gt += 1
                    src = atom.get("source", {}) or {}
                    q = norm(src.get("quote", ""))
                    v = norm(atom.get("value", ""))
                    if (q and q in hay) or (v and v in hay):
                        hit += 1
                    else:
                        misses.append({"kind": "atom", "atom_id": atom.get("atom_id"),
                                       "source_case_id": src.get("case_id"),
                                       "value": atom.get("value"),
                                       "quote": src.get("quote")})

            for t_no in sorted(dialogue_tokens):
                if t_no > no:
                    continue
                for tok, snippet in dialogue_tokens[t_no]:
                    gt += 1
                    if norm(tok) in hay:
                        hit += 1
                    else:
                        misses.append({"kind": "num_date_token", "token": tok,
                                       "source_case_id": "T%02d" % t_no,
                                       "source_text_head": snippet})

            per_arm.setdefault(arm, []).append({
                "turn": no, "case_id": case_id, "calls": n_calls,
                "gt_total": gt, "gt_in_window": hit,
                "keep_rate": round(hit / gt, 4) if gt else None,
                "misses": misses,
            })

        segs = segment_summary(per_arm)
        report["episodes"][ep] = {"per_arm": per_arm, "segments": segs}
    return report


def segment_of(turn_no):
    lo = 1
    for hi in ANCHOR_BOUNDARIES:
        if lo <= turn_no <= hi:
            return (lo, hi)
        lo = hi + 1
    return (lo, None)  # 超出锚点矩阵的轮次仍单独可查


def segment_summary(per_arm):
    out = {}
    for arm, turns in per_arm.items():
        buckets = {}
        for t in turns:
            buckets.setdefault(segment_of(t["turn"]), []).append(t)
        rows = []
        for (lo, hi) in sorted((k for k in buckets), key=lambda b: (b[0], b[1] or 10 ** 6)):
            ts = buckets[(lo, hi)]
            gt_sum = sum(t["gt_total"] for t in ts)
            in_sum = sum(t["gt_in_window"] for t in ts)
            rates = [t["keep_rate"] for t in ts if t["keep_rate"] is not None]
            rows.append({
                "segment": "T%d-%s" % (lo, hi if hi else "+"),
                "turns": len(ts),
                "gt_total_pooled": gt_sum,
                "gt_in_window_pooled": in_sum,
                "keep_rate_pooled": round(in_sum / gt_sum, 4) if gt_sum else None,
                "keep_rate_mean": round(sum(rates) / len(rates), 4) if rates else None,
            })
        out[arm] = rows
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--batch-dir", required=True)
    ap.add_argument("--episodes", default=None,
                    help="逗号分隔；缺省从 batch-dir 下 arm 目录自动推导")
    ap.add_argument("--arms", default="parent,candidate",
                    help="逗号分隔臂名前缀（目录 <arm>_<episode>）")
    ap.add_argument("--out", default=None, help="结果 JSON 输出路径")
    args = ap.parse_args()

    batch_dir = args.batch_dir if os.path.isabs(args.batch_dir) \
        else os.path.join(ROOT, args.batch_dir)
    if not os.path.isdir(batch_dir):
        sys.exit("batch-dir 不存在: %s" % batch_dir)

    arms = [a.strip() for a in args.arms.split(",") if a.strip()]
    if args.episodes:
        episodes = [e.strip() for e in args.episodes.split(",") if e.strip()]
    else:
        eps = set()
        for name in os.listdir(batch_dir):
            for arm in arms:
                if name.startswith(arm + "_"):
                    eps.add(name[len(arm) + 1:])
        episodes = sorted(eps)
        if not episodes:
            sys.exit("未从 batch-dir 推导出 episode，请用 --episodes 指定")

    report = measure(batch_dir, episodes, arms)

    for ep, data in report["episodes"].items():
        if "error" in data:
            print("[%s] %s" % (ep, data["error"]))
            continue
        for arm, segs in data["segments"].items():
            print("[%s/%s]" % (ep, arm))
            for r in segs:
                print("  %-9s turns=%-3d pooled=%s (mean=%s)"
                      % (r["segment"], r["turns"], r["keep_rate_pooled"],
                         r["keep_rate_mean"]))
            last = max(data["per_arm"][arm], key=lambda t: t["turn"])
            print("  final T%02d: %d/%d in window, %d misses"
                  % (last["turn"], last["gt_in_window"], last["gt_total"],
                     len(last["misses"])))

    if args.out:
        out = args.out if os.path.isabs(args.out) else os.path.join(ROOT, args.out)
        os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
        with open(out, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=1)
        print("[written] %s" % out)


if __name__ == "__main__":
    main()
