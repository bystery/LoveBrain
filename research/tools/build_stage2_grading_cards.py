# -*- coding: utf-8 -*-
r"""tools/build_stage2_grading_cards.py — 从 Stage2 故事的 reference.json 派生评审卡（零调用）。

为什么需要：Stage2（E021—E036，16 故事×12 位）机械验证已全过，但没有评分卡文件，
固定故事两臂对照（S1/S2）就没法把"逐维判据 + 必要事实 + 多答案边界"摊给 worker/裁判。
本工具只做**搬运与核对**，不新写语义：卡面内容与 reference.json 逐字同源。

卡的用途与边界（诚实登记）：
  · 评估侧专用。绝不进入候选可见输入（候选只拿 episode.json 的 messages/intent/note/clock/kb）。
  · 沿用 Stage1 卡 schema 的 10 个字段（与 tools/build_story_v2_stage1.py 逐字同构），
    另加 intent：Stage1 卡丢掉了意图，导致 goal 维度评审时看不到"当前有效目标"到底是
    什么。本批要判目标让位/关闭/推进，所以把 reference 里已有的 intent 原样带上，
    不新造判据。
  · reference 是**同 worker 撰写**的公开开发材料（dev_exposed=true, independent_review=false），
    本工具不把它说成独立封存集。

用法：
  set PYTHONIOENCODING=utf-8
  D:\anaconda\python.exe tools\build_stage2_grading_cards.py \
      --stories E024,E027,E031,E034 \
      --out benchmark/stories_v2/stage2/GRADING_CARDS_E024_E027_E031_E034.json
"""
import argparse
import datetime
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import validate_story_v2 as vs2            # noqa: E402  复用同一套题面/判据工具函数

STAGE2 = os.path.join(ROOT, "benchmark", "stories_v2", "stage2")
STAGE_DIR = [STAGE2]              # 可由 --stage 切换（stage2 / anchors_20261028 …）
CARD_KEYS = ["case_id", "current_action", "acceptable_actions",
             "applicable_dimensions", "required_memory_atoms", "private_guidance",
             "unknowns", "protected_expressions", "forbidden_factual_claims",
             "may_decline"]


def sha(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def load(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def her_text(ep, cid):
    return "\n".join(m["text"] for m in vs2.inp_messages(ep, cid)
                     if m.get("role") == "HER")


def build_cards(story_ids):
    cards, errs, per_story = [], [], {}
    for sid in story_ids:
        n_err0 = len(errs)          # 2026-11-02 修复：errs 是全局表，缺件跳过须按本篇增量判
        sdir = os.path.join(STAGE_DIR[0], sid)
        ep_path = os.path.join(ROOT, "benchmark", "episodes", "%s.json" % sid)
        ref_path = os.path.join(sdir, "reference.json")
        exp_path = os.path.join(sdir, "exposure.json")
        for p, nm in ((ep_path, "runner episode"), (ref_path, "reference"),
                      (exp_path, "exposure")):
            if not os.path.isfile(p):
                errs.append("%s: 缺 %s（%s）" % (sid, nm, p))
        if len(errs) > n_err0:
            continue
        ep = load(ep_path)
        ref = load(ref_path)
        exp = load(exp_path)
        cases = [t["case_id"] for t in ep["turns"]]
        rturns = ref.get("turns") or {}
        if set(rturns) != set(cases):
            errs.append("%s: reference 位点集合 != 题面（缺=%s 多=%s）"
                        % (sid, sorted(set(cases) - set(rturns)),
                           sorted(set(rturns) - set(cases))))
            continue
        made = 0
        for cid in cases:
            r = rturns[cid]
            turn = next(t for t in ep["turns"] if t["case_id"] == cid)
            inp = turn.get("input") or {}
            card = {k: r.get(k) for k in CARD_KEYS}
            card["intent"] = r.get("intent")
            # 2026-11-02（W13-03 备卡发现）：wave3 Stage1 卡普遍无 intent 字段而题面有
            # 有效意图 → 从冻结题面逐字回填（不新造判据），卡上标来源供评审核对。
            if not card.get("intent") and inp.get("intent"):
                card["intent"] = inp.get("intent")
                card["intent_source"] = "episode_backfill_20261102"
            # 同规则：题面声明 may_decline 而卡缺失 → 逐字回填（不新造判据）
            if not card.get("may_decline") and inp.get("may_decline"):
                card["may_decline"] = inp.get("may_decline")
                card["may_decline_source"] = "episode_backfill_20261102"
            # ── 核对：卡 ↔ 题面（不是"看起来对"，是逐字回指） ──
            if card.get("case_id") != cid:
                errs.append("%s: 卡 case_id 不符" % cid)
            if bool(card.get("may_decline")) != bool(inp.get("may_decline")):
                errs.append("%s: may_decline 卡=%s 题=%s"
                            % (cid, card.get("may_decline"), inp.get("may_decline")))
            dims = set(card.get("applicable_dimensions") or [])
            if not dims <= vs2.DIMS:
                errs.append("%s: 未知维度 %s" % (cid, sorted(dims - vs2.DIMS)))
            for p in card.get("protected_expressions") or []:
                if p not in her_text(ep, cid):
                    errs.append("%s: protected_expression 不在本轮 HER 原文：%r" % (cid, p))
            ei = inp.get("intent")
            ci = card.get("intent")
            if bool(ei) != bool(ci):
                errs.append("%s: intent 卡/题存在性不一致" % cid)
            elif ei and ci:
                for k in ("text", "status", "enabled"):
                    if ei.get(k) != ci.get(k):
                        errs.append("%s: intent.%s 卡=%r 题=%r"
                                    % (cid, k, ci.get(k), ei.get(k)))
            for atom in card.get("required_memory_atoms") or []:
                src = atom.get("source") or {}
                if src.get("kind") == "message" and src.get("case_id") not in cases:
                    errs.append("%s: atom %s 源位点不存在" % (cid, atom.get("atom_id")))
            cards.append(card)
            made += 1
        per_story[sid] = {
            "positions": made,
            "episode_sha256": sha(ep_path),
            "reference_sha256": sha(ref_path),
            "exposure_sha256": sha(exp_path),
            "declared_hashes_match": {
                "episode.json": exp.get("hashes", {}).get("episode.json")
                                 == sha(os.path.join(sdir, "episode.json")),
                "reference.json": exp.get("hashes", {}).get("reference.json")
                                  == sha(ref_path),
            },
            "dev_exposed": bool(exp.get("dev_exposed")),
            "independent_review": bool(exp.get("independent_review")),
            "author": exp.get("author"),
            "may_decline_turns": exp.get("may_decline_turns"),
        }
    return cards, errs, per_story


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--stories", required=True,
                    help="逗号分隔的故事号，如 E024,E027,E031,E034 或 AK01,…")
    ap.add_argument("--stage", default="stage2",
                    help="benchmark/stories_v2 下的 stage 目录（stage2 / anchors_20261028）")
    ap.add_argument("--out", required=True)
    ap.add_argument("--provenance", default=None)
    a = ap.parse_args(argv)
    STAGE_DIR[0] = os.path.join(ROOT, "benchmark", "stories_v2", a.stage)
    if not os.path.isdir(STAGE_DIR[0]):
        raise SystemExit("stage 目录不存在：%s" % STAGE_DIR[0])
    sids = [s.strip() for s in a.stories.split(",") if s.strip()]
    cards, errs, per_story = build_cards(sids)

    case_ids = [c["case_id"] for c in cards]
    if len(case_ids) != len(set(case_ids)):
        errs.append("case_id 重复")
    doc = {
        "kind": "grading_cards_stories_v2",
        "stage": a.stage,
        "built_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "source": "同故事 reference.json 逐字派生（本工具不新写判据）",
        "schema": CARD_KEYS + ["intent"],
        "schema_note": ("Stage1 十键 + intent（Stage1 卡丢失 intent，goal 维评审看不到"
                        "当前有效目标；此处原样搬运 reference 的 intent，不新造判据）"),
        "use": "评估侧专用：不进入候选可见输入；不声称独立封存",
        "stories": per_story,
        "counts": {"stories": len(per_story), "cards": len(cards)},
        "checks": {"errors": errs, "passed": not errs},
        "cards": cards,
    }
    out = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print("cards=%d stories=%d errors=%d" % (len(cards), len(per_story), len(errs)))
    for e in errs[:20]:
        print("  ERR", e)
    print("wrote", os.path.relpath(out, ROOT).replace("\\", "/"),
          "sha256=", sha(out)[:16], "…")
    if a.provenance:
        prov = {k: v for k, v in doc.items() if k != "cards"}
        prov["cards_sha256"] = sha(out)
        prov["cards_file"] = os.path.relpath(out, ROOT).replace("\\", "/")
        pp = a.provenance if os.path.isabs(a.provenance) else os.path.join(ROOT, a.provenance)
        os.makedirs(os.path.dirname(pp), exist_ok=True)
        with open(pp, "w", encoding="utf-8") as f:
            json.dump(prov, f, ensure_ascii=False, indent=1)
        print("provenance", os.path.relpath(pp, ROOT).replace("\\", "/"))
    return 0 if not errs else 1


if __name__ == "__main__":
    sys.exit(main())
