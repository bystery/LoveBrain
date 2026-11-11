"""tools/show_turn.py — B3 亲读单页（只读，零模型调用）。

把一个（或几个）回合摊成一页：前后文 HER 消息、两臂原始回复、卡面判据、
评分失败项、机制 notes、judge 九维/sever 旗位与调用用量。供主会话逐位亲读。

用法：
  python tools/show_turn.py --batch-dir logs/w18_ucsupp_live_20261103 \
      --episode LRE117 --turns 14,22,34 \
      --cards benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json \
      --review refs/experiment_protocol_20261023/long_probe_20261030/LP_W18_LRE117_NINEDIM_REVIEW.json \
      --out logs/_b3_W18_LRE117.txt
输出为 UTF-8 文件（避免 GBK 控制台乱码），stdout 只打印路径与摘要。
"""
import argparse
import glob
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STYLE = ("recommended", "bad_boy", "playful", "warm")


def parse_turns(spec):
    out = []
    for part in spec.split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            a, b = part.split("-", 1)
            out.extend(range(int(a), int(b) + 1))
        else:
            out.append(int(part))
    return out


def load_json(p, default=None):
    try:
        return json.load(open(p, encoding="utf-8"))
    except (FileNotFoundError, ValueError):
        return default


def turn_dir(batch_dir, arm, ep, n):
    hits = glob.glob(os.path.join(ROOT, batch_dir, "%s_%s" % (arm, ep), "out",
                                  "turn_%02d_*" % n))
    return hits[0] if hits else None


def fmt_reply(tdir):
    if not tdir:
        return "  (missing)"
    raw = ""
    try:
        raw = open(os.path.join(tdir, "reply_raw.txt"), encoding="utf-8").read()
    except FileNotFoundError:
        pass
    lines = []
    try:
        obj = json.loads(raw)
    except ValueError:
        return "  [非 JSON] " + raw[:1200].replace("\n", "\n  ")
    if not isinstance(obj, dict):
        return "  [非对象] " + raw[:800]
    act = obj.get("action")
    if act:
        lines.append("  action=%s" % act)
    resp = obj.get("response")
    if isinstance(resp, dict):
        for k in STYLE:
            v = resp.get(k)
            if v not in (None, ""):
                lines.append("  [%s] %s" % (k, v))
        for k, v in resp.items():
            if k not in STYLE and v not in (None, ""):
                lines.append("  [%s*] %s" % (k, v))
    elif resp is not None:
        lines.append("  response=%r" % (resp,))
    dirs = obj.get("directions")
    if dirs:
        lines.append("  directions=%s" % json.dumps(dirs, ensure_ascii=False))
    ana = obj.get("analysis")
    if ana:
        lines.append("  analysis=%s" % json.dumps(ana, ensure_ascii=False)[:900])
    for k in ("decline_reason", "reason"):
        if obj.get(k):
            lines.append("  %s=%s" % (k, obj[k]))
    return "\n".join(lines) if lines else "  {}"


def fmt_scoring(tdir):
    sc = load_json(os.path.join(tdir, "scoring.json")) if tdir else None
    if not sc:
        return "  (no scoring)"
    out = []
    for c in sc.get("checks") or []:
        if not c.get("pass"):
            out.append("  FAIL %s: %s | %s" % (c.get("id"), c.get("desc"), c.get("evidence")))
    return "\n".join(out) if out else "  all checks pass (score10=%s)" % sc.get("score10")


def fmt_usage(tdir):
    calls = load_json(os.path.join(tdir, "calls.json"), []) if tdir else []
    if not calls:
        return "  (no calls)"
    return "  " + " | ".join("prompt=%s completion=%s lat=%sms" % (
        (c.get("usage") or {}).get("prompt"), (c.get("usage") or {}).get("completion"),
        c.get("latency_ms")) for c in calls)


def fmt_verdict(review, case_id):
    if not review:
        return "  (no review file)"
    out = []
    for v in review.get("verdicts") or []:
        if v.get("pair_id", "").endswith(":" + case_id):
            out.append("  main winner=%s severe=%s dims_cand=%s dims_par=%s\n  quote=%s" % (
                v.get("winner_cand_view"), json.dumps(v.get("severe"), ensure_ascii=False),
                json.dumps((v.get("dims") or {}).get("candidate"), ensure_ascii=False),
                json.dumps((v.get("dims") or {}).get("parent"), ensure_ascii=False),
                v.get("key_quote")))
    for v in review.get("swap_supplement") or []:
        if v.get("pair_id", "").endswith(":" + case_id):
            out.append("  swap winner=%s agree=%s\n  quote=%s" % (
                v.get("swap_winner"), v.get("agree"), v.get("quote")))
    return "\n".join(out) if out else "  (no verdict for %s)" % case_id


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--batch-dir", required=True)
    ap.add_argument("--episode", required=True)
    ap.add_argument("--turns", required=True)
    ap.add_argument("--cards", default=None)
    ap.add_argument("--review", default=None)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    ep = a.episode
    epd = load_json(os.path.join(ROOT, "benchmark", "episodes", "%s.json" % ep))
    cards = {}
    if a.cards:
        for c in (load_json(a.cards) or {}).get("cards") or []:
            cards[c.get("case_id")] = c
    review = load_json(a.review) if a.review else None
    turns = {t.get("case_id"): t for t in epd.get("turns") or []}
    lines = ["# B3 read page: %s @ %s" % (ep, a.batch_dir),
             "persona=%s" % json.dumps(epd.get("persona"), ensure_ascii=False),
             "initial_kb=%s" % json.dumps(epd.get("initial_kb"), ensure_ascii=False), ""]
    for n in parse_turns(a.turns):
        cid = "%s_T%02d" % (ep, n)
        t = turns.get(cid) or {}
        lines.append("=" * 70)
        lines.append("## %s  clock=%s may_decline=%s" % (
            cid, (t.get("input") or {}).get("clock"), (t.get("input") or {}).get("may_decline")))
        for lo in range(max(1, n - 2), n + 1):
            prev = turns.get("%s_T%02d" % (ep, lo)) or {}
            tag = "TARGET" if lo == n else "prev"
            for m in (prev.get("input") or {}).get("messages") or []:
                lines.append("[%s %s %s] %s" % (tag, m.get("role"), m.get("ts"), m.get("text")))
            if lo < n:
                for arm in ("parent", "candidate"):
                    lines.append("  <%s reply>" % arm)
                    lines.append(fmt_reply(turn_dir(a.batch_dir, arm, ep, lo)))
            else:
                for arm in ("parent", "candidate"):
                    td = turn_dir(a.batch_dir, arm, ep, lo)
                    lines.append("  <%s reply>" % arm)
                    lines.append(fmt_reply(td))
                    lines.append("  <%s scoring>" % arm)
                    lines.append(fmt_scoring(td))
                    lines.append("  <%s usage>" % arm)
                    lines.append(fmt_usage(td))
                    notes = ""
                    if td:
                        try:
                            notes = open(os.path.join(td, "mechanism_notes.txt"),
                                         encoding="utf-8").read().strip()
                        except FileNotFoundError:
                            pass
                    if notes:
                        lines.append("  <%s notes> %s" % (arm, notes))
        card = cards.get(cid)
        if card:
            lines.append("  <card>")
            for k in ("current_action", "acceptable_actions", "applicable_dimensions",
                      "required_memory_atoms", "private_guidance", "unknowns",
                      "protected_expressions", "forbidden_factual_claims", "may_decline", "intent"):
                if k in card:
                    lines.append("   %s=%s" % (k, json.dumps(card[k], ensure_ascii=False)))
        if t.get("feedback"):
            lines.append("  <feedback> %s" % json.dumps(t["feedback"], ensure_ascii=False))
        if t.get("gold"):
            lines.append("  <gold> %s" % json.dumps(t["gold"], ensure_ascii=False))
        lines.append("  <judge>")
        lines.append(fmt_verdict(review, cid))
        lines.append("")
    with open(os.path.join(ROOT, a.out), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))
    print("wrote %s (%d turns)" % (a.out, len(parse_turns(a.turns))))


if __name__ == "__main__":
    main()
