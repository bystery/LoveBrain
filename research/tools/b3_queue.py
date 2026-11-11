# -*- coding: utf-8 -*-
"""tools/b3_queue.py — W16-05 #3 B3 prescreen 升级：judge 旗标位强制 B3 抽读队列。

背景（W04-03_JUDGE_QUAL_APPENDIX_20261031.md / E026_T05 型盲区）：
judge severe 旗标存在漏检与不对称；B3 亲读 prescreen 原先只扫备选槽的卡面禁断词，
主槽编造位（E026_T05）两边都没抓到。本模块把 judge 旗标位转为强制 B3 队列：

1. severe=True 位（按修正后映射）全部入队，附 note/key_quote/dims 证据；
2. note 引用句核验：note 中引号内文本是否真实出现在对应臂回复里
   （引语幻觉检测——note 引用不存在 = judge 幻觉风险位，优先级升顶）；
3. 卡面 unknowns 交叉：被旗位涉及 unknowns 的按"无据确定化嫌疑"加权；
4. material-defect 位标记（不剔除，交 B3 按双口径裁决）。

零模型调用；纯文本核验。供独立运行与评审脚本收尾调用。
"""
import json
import os
import re


def _quoted_fragments(text):
    """提取 note/quote 中的引号片段（中英引号）。"""
    if not text:
        return []
    frags = []
    for pat in (r"“([^”]{2,60})”", r"「([^」]{2,60})」", r'"([^"]{2,60})"'):
        frags.extend(m.group(1) for m in re.finditer(pat, text))
    return frags


def _load_reply(batch_root, arm, story, cid):
    """读原始回复主槽文本；找不到返回 None（不阻塞队列构建）。"""
    if not batch_root:
        return None, None
    for cand in (
        os.path.join(batch_root, "%s_%s" % (arm, story), "out", "turn_*_%s" % cid, "reply_raw.txt"),
    ):
        import glob
        hits = glob.glob(cand)
        if hits:
            try:
                raw = open(hits[0], encoding="utf-8").read()
            except Exception:
                return None, hits[0]
            try:
                j = json.loads(raw)
                rec = (j.get("response") or {}).get("recommended")
                if isinstance(rec, str):
                    return rec, hits[0]
            except Exception:
                pass
            return raw[:2000], hits[0]
    return None, None


def _card_unknowns(cards_path, cid):
    try:
        cards = json.load(open(cards_path, encoding="utf-8"))
    except Exception:
        return []
    c = cards.get(cid) or {}
    u = c.get("unknowns") or []
    if isinstance(u, str):
        u = [u]
    return [str(x) for x in u]


_EPISODE_CACHE = {}


def _episode_text(story):
    """剧本全文（persona/initial_kb/全部台词），供 note 引语三路匹配。"""
    if story in _EPISODE_CACHE:
        return _EPISODE_CACHE[story]
    txt = ""
    for base in ("benchmark/episodes",):
        p = os.path.join(base, "%s.json" % story)
        if os.path.exists(p):
            try:
                j = json.load(open(p, encoding="utf-8"))
            except Exception:
                break
            parts = [json.dumps(j.get("persona", {}), ensure_ascii=False),
                     json.dumps(j.get("initial_kb", {}), ensure_ascii=False)]
            for t in (j.get("turns") or []):
                for m in ((t.get("input") or {}).get("messages") or []):
                    parts.append(str(m.get("text", "")))
            txt = chr(10).join(parts)
            break
    _EPISODE_CACHE[story] = txt
    return txt


def build_queue(review_path, batch_root=None, cards_path=None, material_cells=()):
    """从评审 JSON 构建强制 B3 抽读队列。返回 dict（可 json 落盘）。"""
    r = json.load(open(review_path, encoding="utf-8"))
    tag = r.get("tag") or os.path.basename(review_path).replace("LP_", "").replace("_NINEDIM_REVIEW.json", "")
    stories = r.get("stories") or []
    queue = []
    for v in r.get("verdicts", []):
        sev = v.get("severe") or {}
        flags = []
        for arm in ("candidate", "parent"):
            if sev.get(arm):
                flags.append(arm)
        if not flags:
            continue
        _parts = v["pair_id"].split(":")
        cid = _parts[-1]
        story = next((x for x in _parts if re.match(r"^E\d{3}$", x)), "")
        order = v.get("order") or ("swap" if v.get("swap") else "main")
        note = sev.get("note") or ""
        quote = v.get("key_quote") or ""
        dims_flagged = v.get("dims") or {}
        entry = {
            "pair_id": v["pair_id"], "order": order, "story": story, "cid": cid,
            "flagged_arms": flags,
            "note": note[:400], "key_quote": quote[:200],
            "dims_flagged": {a: {d: (dims_flagged.get(a) or {}).get(d)
                                  for d in ("fact", "time", "attrib", "memory")}
                             for a in flags},
            "material_cell": cid in material_cells,
            "checks": {},
        }
        # 1) note 引语核验（W16-05#3 v2）：空白不敏感三路匹配
        #    被旗臂回复 / 对侧臂回复 / 剧本上下文（persona+kb+全部台词）
        #    只有四处（含 key_quote）都找不到的片段才计 QUOTE-RISK
        PUNCT = chr(65292) + chr(12290) + chr(65281) + chr(65311) + chr(12289) + chr(65307) + chr(65306) + chr(8220) + chr(8221) + chr(12300) + chr(12301) + chr(65288) + chr(65289) + chr(40) + chr(41) + chr(47) + chr(8212) + chr(8230)
        def _norm(t):
            t = re.sub(r"\s+", "", t or "")
            return "".join(ch for ch in t if ch not in PUNCT)
        def _loose(frag, target):
            # 5-gram coverage fallback: >=80% of fragment 5-grams appear in target
            fn = _norm(frag)
            if len(fn) < 5 or not target:
                return False
            grams = [fn[i:i+5] for i in range(len(fn)-4)]
            if not grams:
                return False
            hit = sum(1 for g in grams if g in target)
            return hit / len(grams) >= 0.8
        replies = {}
        for arm in ("candidate", "parent"):
            replies[arm] = _norm(_load_reply(batch_root, arm, story, cid)[0])
        ctx = _norm(_episode_text(story))
        frags = _quoted_fragments(note)
        nowhere, where = [], {}
        for f in frags:
            fn = _norm(f)
            hit = None
            for arm in ("candidate", "parent"):
                if fn and (fn in replies[arm] or _loose(f, replies[arm])):
                    hit = arm + "_reply"
                    break
            if not hit and fn and (fn in ctx or _loose(f, ctx)):
                hit = "episode_context"
            if not hit and fn and (fn in _norm(quote) or _loose(f, _norm(quote))):
                hit = "key_quote"
            where[f] = hit
            if fn and not hit:
                nowhere.append(f)
        entry["checks"]["note_quotes"] = {"fragments": frags[:8], "found_where": where,
                                          "nowhere": nowhere[:6]}
        entry["checks"]["note_quote_hallucination_risk"] = bool(nowhere)
        # FLJUDGE 判决 §7.2：QUOTE-RISK 位=judge 引语幻觉嫌疑，severe 账不得直接计入，
        # 必须亲读 confirm/reject 后才入账（LP_FLJUDGE_B3_READRESULT 3/3 rejected 佐证）。
        entry["severe_accounting"] = "pending_personal_read" if bool(nowhere) else "counted"
        # 2) 卡面 unknowns 交叉（无据确定化嫌疑加权）
        if cards_path:
            unk = _card_unknowns(cards_path, cid)
            entry["checks"]["card_unknowns"] = unk[:4]
            entry["checks"]["unknown_overlap"] = any(
                any(t in note for t in re.findall(r"[一-龥]{2,6}", u)) for u in unk) if unk else False
        # 优先级：引语幻觉 > 双臂旗 > 单臂；material 位降级标注
        pr = 1 if entry["checks"].get("note_quote_hallucination_risk") else (2 if len(flags) > 1 else 3)
        if entry["material_cell"]:
            pr = min(pr, 4)
        entry["priority"] = pr
        queue.append(entry)
    queue.sort(key=lambda e: (e["priority"], e["pair_id"]))
    return {"kind": "b3_read_queue_v2", "tag": tag, "review": review_path,
            "batch_root": batch_root or "", "n_queued": len(queue), "queue": queue}


def summarize(q):
    out = ["B3 read queue [%s]: %d positions" % (q["tag"], q["n_queued"])]
    n_pending = sum(1 for e in q["queue"] if e.get("severe_accounting") == "pending_personal_read")
    out.append("  severe accounting: %d counted / %d pending_personal_read (QUOTE-RISK 不入 severe 账)"
               % (q["n_queued"] - n_pending, n_pending))
    for e in q["queue"]:
        marks = []
        if e["checks"].get("note_quote_hallucination_risk"):
            marks.append("QUOTE-RISK")
        if e["material_cell"]:
            marks.append("MATERIAL")
        if e["checks"].get("unknown_overlap"):
            marks.append("UNKNOWN-OVERLAP")
        out.append("  P%d %s [%s] arms=%s %s" % (e["priority"], e["pair_id"], e["order"],
                                                 "+".join(e["flagged_arms"]), " ".join(marks)))
    return "\n".join(out)


if __name__ == "__main__":
    import sys
    q = build_queue(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None,
                    sys.argv[3] if len(sys.argv) > 3 else None)
    print(summarize(q))
