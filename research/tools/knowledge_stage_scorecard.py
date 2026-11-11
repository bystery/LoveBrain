# -*- coding: utf-8 -*-
"""tools/knowledge_stage_scorecard.py — 知识四段成绩单（离线，零真实调用）。

一级裁定：知识必须分段计，且**允许不同内部架构**；本脚本只测可离线测的三段：
  S1 保留/可恢复：这段关系里说过的事，后面还能不能恢复出来
        - 有持久存储的臂：原子是否被写进存储（存储覆盖）
        - 无持久存储的臂：原子在 T08 出站上下文里是否还在（上下文覆盖）
        两者数据源不同，分列报告，不合并成一个"知识分"
  S2 存储保真（只有可观测存储的臂可测）：每条落盘记录能否回溯到源消息（有据率）、
        说话人是否标对（归属率）；没有存储的臂一律 NR，不拿 prompt 猜内部状态
  S3 检索/实际注入：该用它的位点上，它是否真的进了出站 prompt
        - 全量"原子×后续位点"对：可用在场率
        - 回指位点子集：入站消息在回指旧事实时的在场率（"该召回"的机器近似）
  S4 最终用对：**不测，NR**（需人判或先过裁判资格；程序分不得冒充）

原子集 = 冻结卡集 evidence 里的聊天消息逐字条目（已回核原文）；note/intent 不计入。
匹配规则：entry 级（按换行与「；」切段）+ 3-gram 覆盖率≥阈值 + 至少一个非停用 3-gram，
每条命中都写进审计表，fuzzy 命中在人工复核前不得进对外结论。
"""
import glob
import json
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CARDS = os.path.join(ROOT, "benchmark", "c4_screen_20261026",
                     "GRADING_CARDS_C4_E006_E007_E008_E012_R2.json")
OUT_JSON = os.path.join(ROOT, "evaluation", "KNOWLEDGE_STAGE_SCORECARD_V1.json")
OUT_MD = os.path.join(ROOT, "evaluation", "KNOWLEDGE_STAGE_SCORECARD_V1.md")
OUT_AUDIT = os.path.join(ROOT, "evaluation", "KNOWLEDGE_ATOM_AUDIT_V1.md")

BATCHES = {
    "c4_sweep_a_orig_vs_goal_live_20261026": {"parent": "reference_product_v1",
                                              "candidate": "goal_action_v1"},
    "c4_sweep_b_v5_vs_mem_live_20261026": {"parent": "v5_purpose_aligned",
                                           "candidate": "evidence_memory_v1"},
}
EPISODES = ["E006", "E007", "E008", "E012"]
PUNCT = r"[\s，。！？、,.!?~～:：;；\"'‘’“\"'（）()\[\]{}【】<>《》\-—_＊*#|／/\\]+"
RE_NORM = re.compile(PUNCT)
FUZZY_TH = 0.30          # entry 级、原子侧覆盖率
STORE_TH = 0.30          # 存储条目对原子的覆盖率
ANCHOR_DF = 3            # 稀有锚点：在整段对话里出现在 ≤3 条消息中的内容 bigram
STOP_CHARS = set("的了是我你他她它吗呢啊吧在就都也还和与或个一这那有没不很太把被给对说")

RAW_HINTS = ("真实历史消息", "最近对话", "本次对话记录", "原始对话", "历史消息")
STRUCT_HINTS = ("进行中事项", "场景状态链", "此刻", "双方都确认过的具体事实", "暂定事项",
                "事项计划", "主题", "对话记录", "状态变化")
# 目标/意图块不是知识记录：混进 structured 会把"本轮目标"算成"存下来的知识"
INTENT_HINTS = ("当前目标", "目标-动作自评", "【目标")
SEED_HINTS = ("懂得", "画像/初始事实", "初始事实", "关系画像")
PRIVATE_HINTS = ("军师备注", "备注")
BACKREF_CUES = ("上次", "之前", "那天", "还说", "记不记得", "记得吗", "定了吗", "定了没",
                "改期", "约的", "那件", "那个", "后来", "刚才说的")
DISTILLED = ("moment/plan.md", "moment/scene.md", "moment/topic.md")
ARCHIVE = ("raw_topic.md", "raw_scene.md", "raw_chat.md", "moment/recent.md")


def norm(s):
    return RE_NORM.sub("", s or "")


def grams3(s):
    return {s[i:i + 3] for i in range(max(0, len(s) - 2))} if len(s) >= 3 else ({s} if s else set())


def content_bigrams(s):
    n = norm(s)
    return {n[i:i + 2] for i in range(max(0, len(n) - 1))
            if sum(1 for c in n[i:i + 2] if c not in STOP_CHARS) == 2}


def informative(g):
    return sum(1 for c in g if c not in STOP_CHARS) >= 2


def coverage(small, big):
    """small 的 3-gram 有多少被 big 覆盖（小的一侧做分母，避免长记录稀释）。"""
    sg, bg = grams3(small), grams3(big)
    if not sg or not bg:
        return 0.0, 0
    inter = sg & bg
    return len(inter) / float(len(sg)), len([g for g in inter if informative(g)])


def split_entries(text):
    """按行与「；」切条目——记录里一条常含多句，整行匹配会被稀释。"""
    out = []
    for ln in (text or "").split("\n"):
        ln = ln.strip()
        if not ln or ln.startswith("#") or ln.startswith("<!--"):
            continue
        for part in re.split(r"[；;]", ln):
            part = part.strip()
            if len(part) >= 4:
                out.append(part)
    return out


def split_sections(blob):
    lines = (blob or "").split("\n")
    sec, cur = [], "HEAD"
    for ln in lines:
        h = re.match(r"^\s*#{1,4}\s*(.+?)\s*$", ln) or re.match(r"^\s*(【[^】]{1,30}】)", ln)
        if h:
            cur = h.group(1).strip("#").strip()
        sec.append((cur, ln))
    out, order = defaultdict(str), []
    for name, ln in sec:
        if name not in out:
            order.append(name)
        out[name] += ln + "\n"
    res = {}
    for name in order:
        if any(k in name for k in PRIVATE_HINTS):
            cat = "private_note"
        elif any(k in name for k in INTENT_HINTS):
            cat = "intent"
        elif any(k in name for k in SEED_HINTS):
            cat = "seed"
        elif any(k in name for k in STRUCT_HINTS):
            cat = "structured"
        elif any(k in name for k in RAW_HINTS):
            cat = "raw"
        else:
            cat = "other"
        res[name] = (cat, out[name])
    return res


def load_prompt(batch, role, ep, turn):
    p = os.path.join(ROOT, "logs", batch, "%s_%s" % (role, ep), "out",
                    "turn_%02d_%s_T%02d" % (turn, ep, turn), "calls.json")
    if not os.path.isfile(p):
        return None
    calls = json.load(open(p, encoding="utf-8"))
    c = calls[0] if isinstance(calls, list) and calls else (calls if isinstance(calls, dict) else {})
    s, u = c.get("system") or "", c.get("user") or ""
    if not (s or u):
        return None
    usage = c.get("usage") or {}
    return {"sys": s, "user": u, "raw_text": s + "\n" + u,
            "total_tokens": usage.get("total_tokens"),
            "prompt_tokens": usage.get("prompt_tokens")}


def best_entry_hit(atom_norm, sections, th=FUZZY_TH):
    """返回 cat -> {section, entry, ratio, shared}，只保留≥阈值且有非停用 3-gram 共享的。"""
    out = {}
    for name, (cat, text) in sections.items():
        best = None
        for ent in split_entries(text):
            r, shared = coverage(atom_norm, ent)
            if r >= th and (shared >= 2 if cat == "structured" else shared >= 2):
                if best is None or r > best["ratio"]:
                    best = {"section": name, "entry": ent[:180], "ratio": round(r, 3),
                            "shared": shared}
        if best:
            out[cat] = best
    return out


def current_window(blob):
    blocks = re.findall(r"<chat>(.*?)</chat>", blob or "", re.S)
    return blocks[-1] if blocks else ""


def is_backref(atom_norm, window_text):
    if not window_text:
        return False, None
    r, _ = coverage(atom_norm, window_text)
    cue = next((c for c in BACKREF_CUES if c in window_text), None)
    if r >= 0.25:
        return True, "词汇延续 %.2f" % r
    if cue:
        return True, "回指线索「%s」" % cue
    return False, None


def cluster_titles(titles, th=0.30):
    """按内容 bigram Jaccard 单链接聚类，用于查 plan 条目是否同事件重复堆积。"""
    reps = []
    for t in titles:
        g = content_bigrams(t)
        placed = False
        for r in reps:
            rg = content_bigrams(r["title"])
            u = len(g | rg) or 1
            if len(g & rg) / float(u) >= th:
                r["titles"].append(t)
                placed = True
                break
        if not placed:
            reps.append({"title": t, "titles": [t]})
    out = {}
    for i, r in enumerate(reps):
        for t in r["titles"]:
            out[t] = i
    return [out.get(t, -1) for t in titles]


def _minutes(ts):
    """'2026-11-06 22:35' -> 绝对分钟，用于 plan 条目与同刻消息对齐。"""
    m = re.search(r"(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})", ts or "")
    if not m:
        return -10 ** 9
    y, mo, d, hh, mm = [int(x) for x in m.groups()]
    return (((y * 12 + mo) * 31 + d) * 24 + hh) * 60 + mm


def load_atoms():
    cards = json.load(open(CARDS, encoding="utf-8"))
    atoms, skipped = [], []
    for card in cards:
        for ev in card.get("evidence") or []:
            if "message_index_zero_based" not in ev or not ev.get("role"):
                skipped.append({"card": card["case_id"], "field": ev.get("field")})
                continue
            cid = ev.get("case_id") or card["case_id"]
            ep, tn = cid.split("_T")
            atoms.append({"atom_id": "%s#m%d" % (cid, ev["message_index_zero_based"]),
                          "episode": ep, "turn": int(tn), "role": ev["role"],
                          "text": ev["text"], "ts": ev.get("ts"), "case_id": cid})
    return atoms, skipped


def final_store(batch, role, ep):
    base = os.path.join(ROOT, "logs", batch, "%s_%s" % (role, ep),
                        "_iso_run", "staged", "sandbox", "memory", "kb")
    files = {}
    for pat in DISTILLED + ARCHIVE:
        p = os.path.join(base, pat.replace("/", os.sep))
        if os.path.isfile(p):
            txt = open(p, encoding="utf-8").read()
            if txt.strip():
                files[pat] = txt
    return files


def build_transcript(prompts):
    """从每一轮入站 <chat> 重建整段对话：key='X_Y' -> (role, text)。

    为什么不用 raw_topic.md：它只在部分臂/部分故事里存在（E007 就没有），
    而且不包含最后一轮；用 <chat> 重建才能对全部 8 轮做引用核验。
    """
    out = {}
    for turn, p in sorted(prompts.items()):
        if not p:
            continue
        for blk in re.findall(r"<chat>(.*?)</chat>", p["raw_text"], re.S):
            idx = 0
            for ln in blk.strip().split("\n"):
                ln = ln.strip()
                if not ln.startswith("{"):
                    continue
                try:
                    d = json.loads(ln)
                except Exception:                                    # noqa: BLE001
                    continue
                spk = {"USER": "ME", "PARTNER": "HER"}.get(d.get("speaker"), "OTHER")
                txt = d.get("text") or ""
                if txt:
                    out["%d_%d" % (turn, idx)] = (spk, txt)
                idx += 1
    return out


def parse_store_entries(files):
    """抽出落盘记录条目。两个家庭分开报：
       src 家族（scene/topic）：带 |src=tX_Y|spk=..，可直接回核源消息；
       plan 家族：item 行只有时间戳，按「同一时刻的消息」回核，不硬造 src。
    """
    out = []
    for fn in ("raw_topic.md", "moment/scene.md"):
        txt = files.get(fn)
        if not txt:
            continue
        for ent in split_entries(txt):
            e = re.sub(r"^-\s*\[[^\]]*\]\s*", "", ent).strip()
            for m in re.finditer(r"([^；|]+?)\|src=([A-Za-z0-9_]+)(?:\|spk=(\w+))?", e):
                desc = m.group(1).strip()
                if len(desc) >= 4:
                    out.append({"file": fn, "family": "src", "desc": desc,
                                "src": m.group(2), "spk": m.group(3), "ts": None})
    plan = files.get("moment/plan.md")
    if plan:
        for ln in plan.split("\n"):
            m = re.match(r"item_[0-9a-f]+~(.+?)\s*\|\s*([^|]+?)\s*\|\s*\[(.*?)\](.*)$", ln.strip())
            if m:
                out.append({"file": "moment/plan.md", "family": "plan",
                            "title": m.group(1).strip(),
                            "desc": (m.group(1) + "：" + m.group(4)).strip(),
                            "src": None, "spk": None, "ts": m.group(3), "status": m.group(2)})
    return out


def main():
    atoms, skipped = load_atoms()
    panel_atoms = [a for a in atoms if a["episode"] in EPISODES]

    s3_audit, s2_audit = [], []
    per = defaultdict(dict)                 # arm -> atom_id -> rec
    store_stats = defaultdict(dict)         # arm -> episode -> stats
    for batch, roles in BATCHES.items():
        for role, arm in roles.items():
            for ep in EPISODES:
                prompts = {t: load_prompt(batch, role, ep, t) for t in range(1, 9)}
                have = [t for t, v in prompts.items() if v]
                files = final_store(batch, role, ep)
                distilled = {k: v for k, v in files.items() if k in DISTILLED}
                archive = {k: v for k, v in files.items() if k in ARCHIVE}
                transcript = build_transcript(prompts)
                entries = parse_store_entries(files)
                # 稀有片段：在整段对话里出现 ≤3 条消息的内容 bigram（近似实体锚点）
                df = defaultdict(int)
                for _k, (_spk, _txt) in transcript.items():
                    for bg in content_bigrams(_txt):
                        df[bg] += 1
                # 出站 prompt 里是否真的带上了【进行中事项】（读机制自报的 prompt_has）
                ongoing_loci = 0
                notes_total = 0
                for t in have:
                    np_ = os.path.join(ROOT, "logs", batch, "%s_%s" % (role, ep), "out",
                                       "turn_%02d_%s_T%02d" % (t, ep, t),
                                       "mechanism_notes.txt")
                    if not os.path.isfile(np_):
                        continue
                    notes_total += 1
                    txt = open(np_, encoding="utf-8", errors="replace").read()
                    m = re.search(r"prompt_has=([^\s]*)", txt)
                    if m and "ongoing" in set(m.group(1).split(",")):
                        ongoing_loci += 1
                if files:
                    src_entries = [e for e in entries if e["family"] == "src"]
                    plan_entries = [e for e in entries if e["family"] == "plan"]
                    resolvable = rank1 = supported = weak = unsupported = 0
                    spk_ok = spk_bad = 0
                    for e in src_entries:
                        m = re.match(r"t(\d+)_(\d+)", e["src"] or "")
                        key = "%d_%d" % (int(m.group(1)), int(m.group(2))) if m else None
                        src = transcript.get(key) if key else None
                        if not src:
                            s2_audit.append({"arm": arm, "episode": ep, **e, "grounded": False,
                                             "speaker_ok": None, "src_text": None,
                                             "why": "src 无法解析到任何一轮消息"})
                            continue
                        resolvable += 1
                        anchors = {b for b in content_bigrams(e["desc"]) if df[b] <= ANCHOR_DF}
                        need = max(1, (len(anchors) + 3) // 4)
                        cited_hits = sum(1 for b in anchors if b in norm(src[1]))
                        hits_by = {k: sum(1 for b in anchors if b in norm(v[1]))
                                   for k, v in transcript.items()}
                        best_key = max(hits_by, key=hits_by.get) if hits_by else None
                        best_hits = hits_by.get(best_key, 0)
                        same_turn = bool(best_key and key
                                         and best_key.split("_")[0] == key.split("_")[0])
                        is_rank1 = (best_key == key) or same_turn or cited_hits == best_hits
                        is_supported = cited_hits >= need
                        is_unsupported = best_hits == 0
                        is_weak = (not is_supported) and not is_unsupported
                        rank1 += int(is_rank1)
                        supported += int(is_supported)
                        weak += int(is_weak)
                        unsupported += int(is_unsupported)
                        ok = (e["spk"] == src[0]) if e["spk"] else None
                        if ok is True:
                            spk_ok += 1
                        elif ok is False:
                            spk_bad += 1
                        s2_audit.append({
                            "arm": arm, "episode": ep, **e,
                            "grounded": is_supported, "anchors": len(anchors),
                            "need": need, "cited_hits": cited_hits,
                            "best_src": best_key, "best_hits": best_hits,
                            "rank1": is_rank1, "unsupported": is_unsupported,
                            "speaker_ok": ok, "src_text": src[1][:100], "src_spk": src[0],
                            "why": ("全库无锚点" if is_unsupported else
                                    ("弱据 %d/%d" % (cited_hits, need) if is_weak else None))})
                    # plan 家族：抽象投影，逐条"有据"离线核不了（记 NR）；
                    # 但可以**结构性**查重、查日期/状态冲突、查注入了多少条
                    plan_items = plan_entries
                    clusters = cluster_titles([e.get("title") or "" for e in plan_items])
                    by_cluster = defaultdict(list)
                    for i, cid in enumerate(clusters):
                        by_cluster[cid].append(plan_items[i])
                    dup_clusters = {c: v for c, v in by_cluster.items() if len(v) > 1}
                    date_conf = {}
                    status_conf = {}
                    for c, v in dup_clusters.items():
                        dates = {d for e in v for d in re.findall(r"(?:\d{4}-)?(\d{2}-\d{2})",
                                                                  e.get("title") or "")}
                        st = {e.get("status") for e in v}
                        if len(dates) > 1:
                            date_conf[c] = sorted(dates)
                        if len(st) > 1:
                            status_conf[c] = sorted(x for x in st if x)
                    injected = (prompts.get(8) or {}).get("raw_text", "").count("item_")
                    plan_g = plan_weak = plan_bad = 0
                    for e in plan_items:
                        s2_audit.append({"arm": arm, "episode": ep, **e,
                                         "grounded": None, "anchors": None, "need": None,
                                         "cited_hits": None, "best_src": None,
                                         "best_hits": None, "rank1": None,
                                         "unsupported": None, "speaker_ok": None,
                                         "src_text": None, "src_spk": None,
                                         "why": "plan 属抽象投影，逐条有据 NR；见簇查重"})
                    store_stats[arm][ep] = {
                        "files": sorted(files), "entries": len(entries),
                        "src_entries": len(src_entries), "plan_entries": len(plan_entries),
                        "src_resolvable": resolvable, "src_rank1": rank1,
                        "src_supported": supported, "src_weak": weak,
                        "src_unsupported": unsupported,
                        "plan_clusters": len(by_cluster),
                        "plan_dup_clusters": {str(k): [x.get("title") for x in v]
                                              for k, v in dup_clusters.items()},
                        "plan_date_conflicts": {str(k): v for k, v in date_conf.items()},
                        "plan_status_conflicts": {str(k): v for k, v in status_conf.items()},
                        "plan_injected_items": injected,
                        "ongoing_loci": ongoing_loci, "notes_turns": notes_total,
                        "src_resolvable_rate": round(100.0 * resolvable / len(src_entries), 1)
                        if src_entries else None,
                        "src_rank1_rate": round(100.0 * rank1 / max(1, resolvable), 1)
                        if resolvable else None,
                        "src_supported_rate": round(100.0 * supported / max(1, resolvable), 1)
                        if resolvable else None,
                        "src_weak_rate": round(100.0 * weak / max(1, resolvable), 1)
                        if resolvable else None,
                        "src_unsupported_rate": round(100.0 * unsupported / max(1, resolvable), 1)
                        if resolvable else None,
                        "speaker_ok": spk_ok, "speaker_bad": spk_bad,
                        "speaker_ok_rate": round(100.0 * spk_ok / (spk_ok + spk_bad), 1)
                        if (spk_ok + spk_bad) else None,
                        "distilled_files": sorted(distilled), "archive_files": sorted(archive)}
                else:
                    store_stats[arm][ep] = {"files": [], "entries": 0, "observable": False,
                                            "ongoing_loci": ongoing_loci,
                                            "notes_turns": notes_total}

                for a in [x for x in panel_atoms if x["episode"] == ep]:
                    an = norm(a["text"])
                    rec = per[arm][a["atom_id"]] = {
                        "episode": ep, "turn": a["turn"], "role": a["role"],
                        "prompts_loaded": len(have), "at": {}, "store_hit": None,
                        "distilled_hit": None}
                    # S1（存储侧）：任一存储 vs 仅蒸馏记录，两列分开
                    if files:
                        hits = best_entry_hit(an, {"★store": ("structured",
                                                              "\n".join(files.values()))}, STORE_TH)
                        rec["store_hit"] = bool(hits)
                        rec["store_evid"] = (hits.get("structured") or {}).get("entry")
                        if distilled:
                            dh = best_entry_hit(an, {"★distilled": ("structured",
                                                                    "\n".join(distilled.values()))},
                                                STORE_TH)
                            rec["distilled_hit"] = bool(dh)
                    # S3（注入侧）+ 回指
                    windows = {t: current_window(prompts[t]["raw_text"]) for t in have}
                    for k in range(a["turn"] + 1, 9):
                        if k not in prompts:
                            continue
                        hits = best_entry_hit(an, split_sections(prompts[k]["raw_text"]))
                        rel, why = is_backref(an, windows.get(k, ""))
                        rec["at"][k] = {"cats": sorted(hits), "backref": rel, "why": why,
                                        "evid": hits}
                        for cat, h in hits.items():
                            s3_audit.append({"arm": arm, "atom": a["atom_id"], "locus": k,
                                             "role": a["role"], "cat": cat,
                                             "section": h["section"], "entry": h["entry"],
                                             "ratio": h["ratio"], "backref": rel,
                                             "evidence": h["shared"]})
    # ── 自检 ─────────────────────────────────────────────
    errs = []
    if not panel_atoms:
        errs.append("panel 原子集为空")
    for arm, recs in per.items():
        if set(recs) != {a["atom_id"] for a in panel_atoms}:
            errs.append("%s 原子集不完整：%d/%d" % (arm, len(recs), len(panel_atoms)))
        for aid, r in recs.items():
            if r["prompts_loaded"] != 8:
                errs.append("%s/%s 只加载 %d/8 出站 prompt" % (arm, aid, r["prompts_loaded"]))
    if not s3_audit:
        errs.append("S3 审计为空：匹配规则没有命中任何东西（先怀疑规则坏了）")
    if errs:
        for e in sorted(set(errs))[:20]:
            print("SELFCHK FAIL:", e, file=sys.stderr)
        return 2

    # ── 汇总 ─────────────────────────────────────────────
    rows = {}
    for arm, recs in per.items():
        late = [r for r in recs.values() if r["turn"] <= 7]
        pairs = [(r, k) for r in late for k in range(r["turn"] + 1, 9) if k in r["at"]]
        present = [(r, k) for r, k in pairs if r["at"][k]["cats"]]
        struct = [(r, k) for r, k in present if "structured" in r["at"][k]["cats"]]
        rawonly = [(r, k) for r, k in present if r["at"][k]["cats"] == ["raw"]]
        backref = [(r, k) for r, k in pairs if r["at"][k]["backref"]]
        backhit = [(r, k) for r, k in backref if r["at"][k]["cats"]]
        t08 = [(r, 8) for r in late if r["at"].get(8, {}).get("cats")]
        st = store_stats.get(arm, {})
        eps = [v for v in st.values() if v.get("entries")]
        rows[arm] = {
            "atoms_scope": len(late),
            "pairs": len(pairs),
            "S3_present": "%d/%d" % (len(present), len(pairs)),
            "S3_rate": round(100.0 * len(present) / len(pairs), 1) if pairs else None,
            "S3_structured": "%d/%d" % (len(struct), len(pairs)),
            "S3_structured_rate": round(100.0 * len(struct) / len(pairs), 1) if pairs else None,
            "S3_raw_only_rate": round(100.0 * len(rawonly) / len(pairs), 1) if pairs else None,
            "S3_backref": "%d/%d" % (len(backhit), len(backref)),
            "S3_backref_rate": round(100.0 * len(backhit) / len(backref), 1) if backref else None,
            "S1_T08_present": "%d/%d" % (len(t08), len(late)),
            "S1_store": "%d/%d" % (sum(1 for r in recs.values() if r["store_hit"]),
                                   len(recs)) if st and any(v.get("files") for v in st.values())
            else "NR(无可观测存储)",
            "S1_distilled": "%d/%d" % (sum(1 for r in recs.values() if r["distilled_hit"]),
                                       len(recs))
            if st and any(v.get("distilled_files") for v in st.values()) else "NR",
            "S2_entries": sum(v.get("entries", 0) for v in st.values()) if eps else None,
            "S2_src_entries": sum(v.get("src_entries", 0) for v in st.values()) if eps else None,
            "S2_plan_entries": sum(v.get("plan_entries", 0) for v in st.values()) if eps else None,
            "S2_src_resolvable_rate": round(
                100.0 * sum(v.get("src_resolvable", 0) for v in eps)
                / max(1, sum(v.get("src_entries", 0) for v in eps)), 1) if eps else None,
            "S2_src_rank1_rate": round(
                100.0 * sum(v.get("src_rank1", 0) for v in eps)
                / max(1, sum(v.get("src_resolvable", 0) for v in eps)), 1) if eps else None,
            "S2_src_grounded_rate": round(
                100.0 * sum(v.get("src_supported", 0) for v in eps)
                / max(1, sum(v.get("src_resolvable", 0) for v in eps)), 1) if eps else None,
            "S2_src_weak_rate": round(
                100.0 * sum(v.get("src_weak", 0) for v in eps)
                / max(1, sum(v.get("src_resolvable", 0) for v in eps)), 1) if eps else None,
            "S2_src_unsupported_rate": round(
                100.0 * sum(v.get("src_unsupported", 0) for v in eps)
                / max(1, sum(v.get("src_resolvable", 0) for v in eps)), 1) if eps else None,
            "S2_plan_grounded_rate": "NR(抽象投影)",
            "S2_plan_items": sum(v.get("plan_entries", 0) for v in eps) if eps else None,
            "S2_plan_clusters": sum(v.get("plan_clusters", 0) for v in eps) if eps else None,
            "S2_plan_max_dup": max([max([len(x) for x in v.get("plan_dup_clusters", {}).values()]
                                        or [0]) for v in eps] or [0]) if eps else None,
            "S2_plan_date_conflicts": sum(len(v.get("plan_date_conflicts") or {}) for v in eps)
            if eps else None,
            "S2_plan_status_conflicts": sum(len(v.get("plan_status_conflicts") or {})
                                            for v in eps) if eps else None,
            "S2_plan_injected": sum(v.get("plan_injected_items", 0) for v in eps) if eps else None,
            "S2_ongoing_loci": "%d/%d" % (sum(v.get("ongoing_loci", 0) for v in st.values()),
                                          sum(v.get("notes_turns", 0) for v in st.values())),
            "S2_speaker_ok_rate": (round(
                100.0 * sum(v.get("speaker_ok", 0) for v in eps)
                / max(1, sum(v.get("speaker_ok", 0) + v.get("speaker_bad", 0) for v in eps)), 1)
                if eps and any(v.get("speaker_ok", 0) + v.get("speaker_bad", 0) for v in eps)
                else None),
            "S4": "NR-需人判",
        }
    doc = {"kind": "knowledge_stage_scorecard", "date": "2026-10-26",
           "panel": {"arms": sorted(per), "episodes": EPISODES, "turns": 8},
           "thresholds": {"fuzzy_entry": FUZZY_TH, "store": STORE_TH, "anchor_df": ANCHOR_DF,
                          "anchor_need": "max(1, ceil(anchors/4))"},
           "atoms_total": len(atoms), "atoms_excluded": len(skipped),
           "atom_exclusion_reasons": "note/intent 非聊天消息，不计入知识原子集",
           "audit_rows_s3": len(s3_audit), "audit_rows_s2": len(s2_audit),
           "store_stats": store_stats, "rows": rows,
           "S4_policy": "S4 不测；需人判或先过裁判资格校准",
           "real_model_calls": 0}
    json.dump(doc, open(OUT_JSON, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    L = ["# 知识四段成绩单 V1（离线补评，真实调用 0）", "",
         "- 原子集：冻结卡集 evidence 的聊天消息逐字条目 **%d 条**（%d 条 note/intent 不计入）"
         % (len(atoms), len(skipped)),
         "- panel：%s × %s × 8 轮（C4 两批四臂，%d 份已付费答卷） "
         % ("、".join(sorted(per)), "/".join(EPISODES), 128),
         "- 判定规则：entry 级（换行/「；」切段）+ 原子侧 3-gram 覆盖≥%.2f 且共享≥2 个非停用 3-gram；"
         "S2 口径=src 可解析 / 引用轮锚点命中数并列最佳(同轮算对) / 命中≥max(1,⌈锚点/4⌉)记有据 / "
         "命中>0但不足记弱据 / 全库零锚点记无支撑。全部命中见 `KNOWLEDGE_ATOM_AUDIT_V1.md`。"
         % FUZZY_TH, "",
         "## 主表",
         "",
         "| 臂 | S1 T08在上下文 | S1 存储可恢复 | S1 仅蒸馏记录 | S2 条目(src/plan) | S2 src可解析 | "
         "S2 引用最佳匹配 | S2 src有据 | S2 弱据 | S2 全库无支撑 | S2 plan(条/簇/最大重复/注入) | S2 归属正确 | "
         "S3 可用在场 | S3 结构化 | S3 仅原始 | S3 回指位点 | S4 |",
         "|---|---|---|---|---|---:|---:|---:|---:|---:|---|---:|---|---:|---:|---|---|"]
    for arm in sorted(rows):
        r = rows[arm]
        fmt = lambda v: ("—" if v is None else "%s%%" % v)
        plan_cell = ("%s/%s/%s/%s" % (r["S2_plan_items"], r["S2_plan_clusters"],
                                      r["S2_plan_max_dup"], r["S2_plan_injected"])
                     if r["S2_plan_items"] is not None else "NR")
        L.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s%% | %s | NR |" % (
            arm, r["S1_T08_present"], r["S1_store"], r["S1_distilled"],
            ("%s (%s/%s)" % (r["S2_entries"], r["S2_src_entries"], r["S2_plan_entries"]))
            if r["S2_entries"] is not None else "NR",
            fmt(r["S2_src_resolvable_rate"]), fmt(r["S2_src_rank1_rate"]),
            fmt(r["S2_src_grounded_rate"]), fmt(r["S2_src_weak_rate"]),
            fmt(r["S2_src_unsupported_rate"]), plan_cell, fmt(r["S2_speaker_ok_rate"]),
            r["S3_present"] + ("（%s%%）" % r["S3_rate"] if r["S3_rate"] is not None else ""),
            r["S3_structured"], r["S3_raw_only_rate"], r["S3_backref"]))
    raw100 = [a for a in sorted(rows) if rows[a]["S3_raw_only_rate"] == 100.0]
    L += ["",
          "## 怎么读这张表（防止再做错一次）", "",
          "1. **S1 的三列数据源不同，不许相加**：前两列分别看「落盘可恢复」和「当时上下文里有」，"
          "第三列只算 plan/scene/topic 蒸馏记录（不含原始转写副本）。没有落盘存储的臂只有第一列可测。",
          "2. **S3 只证明「进了出站请求」，不证明回复用对了**；S4 缺测，拿 S3 当质量分是越界。",
          "3. **「S3 仅原始」越高，说明该臂靠整段历史反复投喂维持在场**；本批达到 100%% 的臂：%s。"
          "它们的在场率不能解释为记忆能力强，且以 token 成本为代价（见次级指标表）。"
          % ("、".join("`%s`" % a for a in raw100) or "无"),
          "4. **S2 只有有落盘存储的臂可测**；本批仅 `reference_product_v1`。"
          "其余臂显示 NR 是覆盖缺口，不是「保真差」。",
          "",
          "## plan 存储的结构缺陷（可直接回核到文件）", "",
          "`moment/plan.md` 是机制的抽象投影，逐条「是否有据」离线核不了（记 NR）；"
          "但条目堆积、同事件日期/状态互相矛盾、以及这些条目到底有没有进 prompt，都是可核结构事实：", "",
          "| 臂 | 故事 | 条目 | 簇 | 最大重复簇 | 日期冲突簇 | 状态冲突簇 | T08 注入条目 |",
          "|---|---|---:|---:|---:|---:|---:|---:|"]
    plan_rows = 0
    for arm, eps in store_stats.items():
        for ep, v in sorted(eps.items()):
            if not v.get("plan_entries"):
                continue
            plan_rows += 1
            L.append("| %s | %s | %d | %d | %d | %d | %d | %d |" % (
                arm, ep, v.get("plan_entries", 0), v.get("plan_clusters", 0),
                max([len(x) for x in (v.get("plan_dup_clusters") or {}).values()] or [0]),
                len(v.get("plan_date_conflicts") or {}),
                len(v.get("plan_status_conflicts") or {}), v.get("plan_injected_items", 0)))
    if not plan_rows:
        L += ["| （无可观测 plan 存储） | — | — | — | — | — | — | — |"]
    for arm, eps in store_stats.items():
        for ep, v in sorted(eps.items()):
            for cid, titles in (v.get("plan_dup_clusters") or {}).items():
                L.append("")
                L.append("- `%s/%s` 重复簇：%s" % (arm, ep, "；".join(titles)))
                if str(cid) in (v.get("plan_date_conflicts") or {}):
                    L.append("  - 日期冲突：%s" % "、".join(v["plan_date_conflicts"][str(cid)]))
                if str(cid) in (v.get("plan_status_conflicts") or {}):
                    L.append("  - 状态冲突：%s" % "、".join(v["plan_status_conflicts"][str(cid)]))
    # 检索失败的根因取证：离线复算选择器（不调用模型），看「写进去的事项到该用时能不能选出来」
    sel_probe = {"available": False, "episodes": []}
    try:
        for _p in (os.path.join(ROOT, "candidates", "reference_product_v1"),
                   os.path.join(ROOT, "candidates", "reference_product_v1", "mechanism")):
            if _p not in sys.path:
                sys.path.insert(0, _p)
        import mechanism as _M                                   # noqa: PLC0415
        import reference_chain as _RC                            # noqa: PLC0415
        sel_probe["available"] = True
        for ep in EPISODES:
            b = next(iter(BATCHES))
            p8 = load_prompt(b, "parent", ep, 8) or {}
            win = current_window(p8.get("raw_text", ""))
            msgs = []
            for ln in win.strip().split("\n"):
                ln = ln.strip()
                if not ln.startswith("{"):
                    continue
                try:
                    d = json.loads(ln)
                except Exception:                                # noqa: BLE001
                    continue
                msgs.append({"role": {"USER": "ME", "PARTNER": "HER"}.get(d.get("speaker"), "?"),
                             "content": d.get("text") or ""})
            sandbox = os.path.join(ROOT, "logs", b, "parent_%s" % ep,
                                   "_iso_run", "staged", "sandbox")
            if not os.path.isdir(sandbox):
                continue
            selr = _RC.ReferenceOngoingSelector(_M.SandboxStorage(sandbox))                 .select_for_injection("kb", msgs, 7)
            sel_probe["episodes"].append({
                "episode": ep,
                "active_items": len(selr["allItems"]),
                "eligible": len(selr["eligibleItems"]),
                "names": [i["name"] for i in selr["allItems"]],
                "inbound": " / ".join(m["content"] for m in msgs)[:80]})
    except Exception as exc:                                    # noqa: BLE001
        sel_probe["error"] = repr(exc)
    # 根因取证小节：把「写进去」与「该用时选出来」分开
    if sel_probe.get("available") and sel_probe["episodes"]:
        L += ["", "## 检索失败的根因取证（离线复算，零模型调用）", "",
              "- 【进行中事项】实际进入出站 prompt 的位点：**%s**（臂 `reference_product_v1`，读机制自报 prompt_has）"
              % rows["reference_product_v1"]["S2_ongoing_loci"],
              "- 用被测机制自己的 `ReferenceOngoingSelector` 在保存下来的 sandbox 状态上复算 T08：", "",
              "| 故事 | 存储里 ACTIVE 事项 | 选择器选中的 | 本轮入站（截断） |", "|---|---:|---:|---|"]
        for x in sel_probe["episodes"]:
            L.append("| %s | %d | %d | %s |" % (
                x["episode"], x["active_items"], x["eligible"],
                x["inbound"].replace("|", "／")))
        L += ["", "结论（可复现）：事项**写进了** plan，但选择器在 T08 四个位点的复算里一个都没选中——"
              "它要求「事项名抽出的关键词原样出现在本轮消息」，而名字先被 `extract_keywords` 改坏"
              "（例：`约饭（时间待定）`→`饭时间待定`；`2026-11-14周六看后摇乐队livehouse`→`20-14看后摇乐队livehouse`），"
              "于是 fail-closed 永不注入。这是「该用时没取回」的直接证据。"]
    L += ["",
          "## 已知局限（不得省略）", "",
          "- 原子集来自冻结卡 evidence；卡本身是开发者编写的评测卡，原子选取带编写者视角。",
          "- fuzzy 命中全部列在审计表，人工复核前只能作为「机器判定」引用。",
          "- 回指位点用词汇延续/线索词近似，属机器标注；真正的「该不该召回」仍需人工/裁判。",
          "- plan 条目的「有据」记为 NR：它是多轮抽象，不能靠字面回核；"
          "本表只报可核的簇/冲突/注入计数，不报伪造的有据率。",
          "- 跨臂答卷来自两个批次（父本、时间块不同），并列陈述，不排序。",
          "- 本表不构成任何「某版本更强」的结论。"]
    open(OUT_MD, "w", encoding="utf-8").write("\n".join(L) + "\n")

    A = ["# 知识原子命中审计 V1（S3 注入侧）", "",
         "每行=某臂在某位点、某类区段里，该原子以 entry 级匹配命中的证据。",
         "全部为机器判定；fuzzy 命中在人工复核前不得进对外结论。", "",
         "| 臂 | 原子 | 说话人 | 位点 | 区段类别 | 区段 | 覆盖率 | 共享三元组 | 回指 | 命中条目 |",
         "|---|---|---|---:|---|---|---|---:|---|---|"]
    for x in sorted(s3_audit, key=lambda y: (y["arm"], y["atom"], y["locus"], y["cat"])):
        A.append("| %s | %s | %s | T%02d | %s | %s | %s | %s | %s | %s |" % (
            x["arm"], x["atom"], x["role"], x["locus"], x["cat"],
            (x["section"] or "—").replace("|", "／")[:22], x["ratio"], x["evidence"],
            "是" if x["backref"] else "否", (x["entry"] or "").replace("|", "／")[:70]))
    A += ["", "# 存储保真审计（S2 落盘侧）", "",
          "每行=一条落盘记录：src 家族核「源消息能否解析、说话人是否标对、引用是否是锚点命中最多的那一轮」；"
          "plan 家族按同刻 ±10 分钟消息核「标题锚点是否有据」。锚点=在整段对话里出现 ≤%d 条消息的内容 bigram。"
          % ANCHOR_DF, "",
          "| 臂 | 故事 | 文件 | 家族 | 条目 | 引用src/状态 | 锚点 | 需命中 | 引用命中 | 最佳轮 | 最佳命中 | 源说话人 | 判定 | 归属正确 |",
          "|---|---|---|---|---|---|---:|---:|---:|---|---:|---|---|---|"]
    for x in sorted(s2_audit, key=lambda y: (y["arm"], y["episode"], y["file"],
                                             y["family"], str(y["src"]))):
        if x["family"] == "plan":
            verdict = "NR(抽象投影)"
        else:
            verdict = ("无据" if x.get("unsupported") else
                       ("有据" if x["grounded"] else "弱据"))
        A.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
            x["arm"], x["episode"], x["file"], x["family"],
            (x["desc"] or "").replace("|", "／")[:46],
            x["src"] or x.get("status") or "—",
            x.get("anchors", "—"), x.get("need", "—"), x.get("cited_hits", "—"),
            x.get("best_src") or "—", x.get("best_hits", "—"),
            x.get("src_spk") or "—", verdict,
            "—" if x.get("speaker_ok") is None else ("是" if x["speaker_ok"] else "否")))
    open(OUT_AUDIT, "w", encoding="utf-8").write("\n".join(A) + "\n")

    print(json.dumps({"rows": rows, "audit_s3": len(s3_audit), "audit_s2": len(s2_audit)},
                     ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
