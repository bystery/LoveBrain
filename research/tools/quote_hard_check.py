"""quote_hard_check.py — 引文逐字硬校验（W20_03_JUDGE_CAL_REPORT_20261103.md §4-1 必选接线）。

确定性、零模型调用。校验评审 JSON 中每条 key_quote 是否在其语料中逐字出现：
  strict = 整句逐字子串命中；loose = 空白归一后子串命中（单列，不冒充严格口径）。
语料 = 该位 episode（persona + initial_kb + turns 输入文本）+ 卡面全部字段 + 双臂 reply_raw.txt。
severe=true 且 strict 未命中 → 附注 severe_unverified（强制 B3 亲读），不改写原 verdict。

用法:
  python tools/quote_hard_check.py --review <LP_<TAG>_NINEDIM_REVIEW.json> \
      --batch-dirs logs/xxx,logs/yyy [--cards <cards.json>] [--out <out.json>]

供评审驱动直接 import：run_check(review_path, batch_dirs, cards_path, out_path)。
"""
import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _ws(s):
    return re.sub(r"\s+", "", s or "")


def _walk_strings(o, out):
    if isinstance(o, dict):
        for v in o.values():
            _walk_strings(v, out)
    elif isinstance(o, list):
        for v in o:
            _walk_strings(v, out)
    elif isinstance(o, str) and len(o) > 3:
        out.append(o)


def _load_cards_index(cards_path):
    idx = {}
    if not cards_path or not os.path.exists(cards_path):
        return idx
    d = json.load(open(cards_path, encoding="utf-8"))
    cards = d.get("cards") if isinstance(d, dict) else d
    if isinstance(cards, list):
        for c in cards:
            if isinstance(c, dict) and c.get("case_id"):
                idx[c["case_id"]] = c
    elif isinstance(cards, dict):
        idx = {k: v for k, v in cards.items() if isinstance(v, dict)}
    return idx


class Corpus:
    """按 (story, turn) 缓存语料；文本带来源标签。"""

    def __init__(self, batch_dirs, cards_index):
        self.batch_dirs = [b.strip().replace("\\", "/") for b in batch_dirs if b.strip()]
        self.cards_index = cards_index
        self._cache = {}

    def get(self, story, turn):
        key = (story, turn)
        if key in self._cache:
            return self._cache[key]
        texts = []  # (label, text)
        ep = None
        for bd in self.batch_dirs:
            p = os.path.join(ROOT, bd, "candidate_%s" % story, "%s.copy.json" % story)
            if os.path.exists(p):
                try:
                    ep = json.load(open(p, encoding="utf-8"))
                except Exception:
                    ep = None
                break
        if ep:
            for k in ("persona", "initial_kb"):
                v = ep.get(k)
                strs = []
                _walk_strings(v, strs)
                texts.extend(("episode_%s" % k, t) for t in strs)
            turns = ep.get("turns") or []
            if 1 <= turn <= len(turns):
                inp = turns[turn - 1].get("input") or {}
                strs = []
                _walk_strings(inp.get("messages"), strs)
                texts.extend(("episode_turn_input", t) for t in strs)
        card = self.cards_index.get("%s_T%02d" % (story, turn))
        if card:
            strs = []
            _walk_strings(card, strs)
            texts.extend(("card", t) for t in strs)
        for bd in self.batch_dirs:
            for arm in ("candidate", "parent"):
                p = os.path.join(ROOT, bd, "%s_%s" % (arm, story), "out",
                                 "turn_%02d_%s_T%02d" % (turn, story, turn),
                                 "reply_raw.txt")
                if os.path.exists(p):
                    texts.append(("reply_%s" % arm[:4],
                                  open(p, encoding="utf-8", errors="replace").read()))
        self._cache[key] = texts
        return texts

    def check(self, story, turn, quote):
        """返回 (strict, loose, where, cls)；cls 仅在未严格命中时分类：
        note_like（judge 注记混入）/ fragments_all_hit（省略号拼句、片段全逐字）/ no_hit。"""
        if not quote or not quote.strip():
            return None, None, None, None
        strict = loose = False
        where = None
        wq = _ws(quote)
        for label, text in self.get(story, turn):
            if not strict and quote in text:
                strict, where = True, label
            if not loose and wq and wq in _ws(text):
                loose = True
                where = where or label
            if strict and loose:
                break
        cls = None
        if not strict:
            if re.search(r"(?i)\b(LEFT|RIGHT)\b", quote) or re.match(r"^[RrcC两双][：:把]", quote):
                cls = "note_like"
            else:
                frags = [f for f in re.split(r"…+|\.{3,}", quote) if len(_ws(f)) >= 6]
                if frags:
                    hits = [any(f in _ws(t) for _, t in self.get(story, turn)) for f in frags]
                    cls = "fragments_all_hit" if all(hits) else "fragments_miss"
                else:
                    cls = "no_hit"
        return strict, loose, where, cls


def run_check(review_path, batch_dirs, cards_path, out_path):
    review = json.load(open(review_path, encoding="utf-8"))
    corpus = Corpus(batch_dirs, _load_cards_index(
        cards_path if cards_path and os.path.isabs(cards_path)
        else os.path.join(ROOT, cards_path) if cards_path else None))
    annotated, summary = [], {"n": 0, "empty_quote": 0, "strict_hit": 0,
                              "loose_hit": 0, "severe_unverified": 0,
                              "class_counts": {"note_like": 0, "fragments_all_hit": 0,
                                               "fragments_miss": 0, "no_hit": 0}}
    halluc = []
    for v in review.get("verdicts", []):
        pid = v.get("pair_id", "")
        parts = pid.split(":")
        entry = dict(v)
        if len(parts) >= 3:
            story, t = parts[1], int(re.findall(r"T(\d+)$", parts[2])[0])
            quote = v.get("key_quote") or ""
            strict, loose, where, cls = corpus.check(story, t, quote)
            if strict is None:
                summary["empty_quote"] += 1
            else:
                summary["n"] += 1
                summary["strict_hit"] += 1 if strict else 0
                summary["loose_hit"] += 1 if loose else 0
                if not strict:
                    summary["class_counts"][cls] = summary["class_counts"].get(cls, 0) + 1
                    halluc.append({"pair_id": pid, "class": cls, "quote": quote[:80], "loose": loose})
            sev = v.get("severe") or {}
            if (sev.get("candidate") or sev.get("parent")) and strict is False:
                entry["severe_unverified"] = True
                summary["severe_unverified"] += 1
            entry["quote_check"] = {"strict": strict, "loose": loose, "where": where, "class": cls}
        annotated.append(entry)
    out = {
        "kind": "quote_hard_check",
        "review": os.path.basename(review_path),
        "batch_dirs": batch_dirs,
        "summary": summary,
        "strict_rate": round(summary["strict_hit"] / summary["n"], 3) if summary["n"] else None,
        "hard_hallucination": halluc,
        "verdicts_annotated": annotated,
    }
    json.dump(out, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--review", required=True)
    ap.add_argument("--batch-dirs", required=True, help="逗号分隔，按序探测语料")
    ap.add_argument("--cards", default=None)
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    out = a.out or a.review.replace("_NINEDIM_REVIEW.json", "_QUOTE_CHECK.json")
    res = run_check(a.review, a.batch_dirs.split(","), a.cards, out)
    print("checked:", res["summary"], "strict_rate:", res["strict_rate"])
    print("out:", out)


if __name__ == "__main__":
    sys.exit(main())
