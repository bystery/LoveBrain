#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Build QUALITY_PANEL_SCORECARD_V2 (judge scores + worker overlay + derived).

Source of truth:
  - logs/quality_panel_v1_live_20261027_003723/RATINGS_REPAIRED.jsonl
  - logs/quality_panel_v1_live_20261027_003723/QUALITY_PANEL_ANALYSIS_V1_1.json
  - refs/.../knowledge_quality_independent_review_20261026/PRIVATE_BINDINGS.json

V2 rule: never merge primary and backup scores in the same mean.  The legacy
merged view is carried only as an explicitly-labelled appendix for lineage.
No network, no model calls, no mutation of source data.
"""
import json
import os
import sys
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BATCH = os.path.join(ROOT, "logs", "quality_panel_v1_live_20261027_003723")
RATINGS = os.path.join(BATCH, "RATINGS_REPAIRED.jsonl")
ANALYSIS = os.path.join(BATCH, "QUALITY_PANEL_ANALYSIS_V1_1.json")
BINDINGS = os.path.join(
    ROOT, "refs", "experiment_protocol_20261023",
    "knowledge_quality_independent_review_20261026", "PRIVATE_BINDINGS.json")
KNOWLEDGE = os.path.join(ROOT, "evaluation", "KNOWLEDGE_STAGE_SCORECARD_V1.json")
OUT_JSON = os.path.join(ROOT, "evaluation", "QUALITY_PANEL_SCORECARD_V2.json")
OUT_MD = os.path.join(ROOT, "evaluation", "QUALITY_PANEL_SCORECARD_V2.md")

DIMS = ["goal", "factuality", "attribution", "time_state", "boundary",
        "memory_use", "interaction", "initiative", "emotion_relation"]
DIM_CN = {
    "goal": "goal", "factuality": "fact", "attribution": "attribution",
    "time_state": "time_state", "boundary": "boundary",
    "memory_use": "memory_use", "interaction": "interaction",
    "initiative": "initiative", "emotion_relation": "emotion_relation",
}
ARMS = ["reference_product_v1", "v5_purpose_aligned",
        "goal_action_v1", "evidence_memory_v1"]
ARM_CN = {
    "reference_product_v1": "原版 reference_product_v1",
    "v5_purpose_aligned": "v5_purpose_aligned",
    "goal_action_v1": "C2 goal_action_v1",
    "evidence_memory_v1": "C3 evidence_memory_v1",
}
STORIES = ["E006", "E007", "E008", "E012"]

# Identity lineage: this scorecard judges the C4 answers, produced under the
# historical C4 identities. Current candidate identities differ for ref/v5/C2/C3
# (reference v1.1/v1.2 fixes; C2 format contract fix; C3 v1.1/v1.2 memory fixes).
HIST_ID = {
    "reference_product_v1": "6e65b6bced2c2e3fb8d39ada4ae6c09887e2ea1c277158114eb0605fa7479492",
    "v5_purpose_aligned": "ef85551091a36452bde5d6ff6ac614fefeae5dfc6687ddb691a16e16641e1fbf",
    "goal_action_v1": "de600ff9c0521221036a5bd7a71000ce5ecb362d981733228c029e126370cf59",
    "evidence_memory_v1": "009d2425e13c54171586249813dbd9ebebdf0a2af6d4ba98f668f4d2fae2cb26",
}
CUR_ID = {
    "reference_product_v1": "501bc426977b2e9bcee3a6d83b917bb670642f3d9d4c2e79154634f64cd65b67",
    "v5_purpose_aligned": "7c6b51cec9780049d11cb0aabdfe9c42f65ff1a33d2afe22f5a926034e6eb7ac",
    "goal_action_v1": "fc296dd822fbfd4e97def9cdfd532ecb892ef9467b43b80eeebd88450f2ebcba",
    "evidence_memory_v1": "fddcdb51aa802ca9e133b80c72caa89cd72653e89a1239c869aa15e6e8939897",
}
ID_NOTE = {
    "reference_product_v1": "历史 C4 冻结→当前：IDENTITY.md 修订 + reference_chain.py v1.1 选择器/v1.2 ADJUSTED 生效。",
    "v5_purpose_aligned": "v5 自身文件未变，但闭包内 reference 从历史 6e65 变为 501bc4，因此身份变为 7c6b。",
    "goal_action_v1": "历史 C4→当前：format.md analysis 契约与机制消费一致化（v1.1，P04-05）；机制代码同。",
    "evidence_memory_v1": "历史 C4→当前：v1.1 成员/引文硬校验 + v1.2 静态种子事实（P04-02/03）。",
}


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_jsonl(path):
    rows = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def mean(vals):
    return round(sum(vals) / len(vals), 3) if vals else None


def pct(n, d):
    return round(100.0 * n / d, 1) if d else None


def sec_scores(sec):
    out = {}
    scores = (sec or {}).get("scores") or {}
    for d in DIMS:
        out[d] = scores.get(d)
    return out


def dim_block(sections):
    """sections: list of primary/backup section dicts."""
    vals = {d: [] for d in DIMS}
    na = Counter({d: 0 for d in DIMS})
    for sec in sections:
        sc = sec_scores(sec)
        for d in DIMS:
            v = sc.get(d)
            if v is None:
                na[d] += 1
            else:
                vals[d].append(v)
    return {
        "dims_mean": {d: mean(vals[d]) for d in DIMS},
        "dims_scored_n": {d: len(vals[d]) for d in DIMS},
        "dims_na_n": {d: na[d] for d in DIMS},
    }


def claim_block(sections):
    sev = Counter()
    sup = Counter()
    total = 0
    for sec in sections:
        for c in (sec or {}).get("claims") or []:
            total += 1
            sev[c.get("severity") or "missing"] += 1
            sup[c.get("support") or "missing"] += 1
    return {
        "total": total,
        "by_severity": {k: sev[k] for k in
                        ("none", "minor", "major", "missing")
                        if sev.get(k)},
        "by_support": {k: sup[k] for k in
                       ("supported", "unknown", "conflict", "na", "missing")
                       if sup.get(k)},
    }


def has_sendable_text(answer_dir):
    p = os.path.join(answer_dir, "reply_raw.txt")
    try:
        with open(p, "r", encoding="utf-8-sig") as f:
            obj = json.load(f)
    except Exception:
        return False
    resp = obj.get("response") or {}
    dirs = obj.get("directions") or []
    texts = [v for v in resp.values() if isinstance(v, str) and v.strip()]
    dtexts = [v for v in dirs if isinstance(v, str) and v.strip()]
    return bool(texts or dtexts)


def main():
    ratings = load_jsonl(RATINGS)
    analysis = load_json(ANALYSIS)
    bindings = load_json(BINDINGS)
    knowledge = load_json(KNOWLEDGE)
    bind = {x["node_id"]: x for x in bindings}

    # ---- primary node table -------------------------------------------------
    nodes = []
    for r in ratings:
        if r.get("kind") != "primary":
            continue
        item = r["item_id"]
        if item not in bind:
            raise SystemExit("binding missing for %s" % item)
        b = bind[item]
        parsed = r.get("parsed") if r.get("schema_ok_corrected") else None
        nodes.append({
            "item_id": item,
            "case_id": b["case_id"],
            "story": b["case_id"].split("_")[0],
            "arm": b["arm"],
            "parsed": parsed,
            "has_text": has_sendable_text(b["answer_dir"]),
            "answer_dir": b["answer_dir"],
        })
    nodes.sort(key=lambda x: (x["story"], x["case_id"], x["arm"]))

    primary_nodes = [n for n in nodes if n["parsed"]]
    unassessed = [n for n in nodes if not n["parsed"]]
    if len(nodes) != 128:
        raise SystemExit("planned primary != 128: %d" % len(nodes))
    if len(primary_nodes) != 117:
        raise SystemExit("assessed primary != 117: %d" % len(primary_nodes))
    if len(unassessed) != 11:
        raise SystemExit("unassessed primary != 11: %d" % len(unassessed))

    # ---- primary by arm -----------------------------------------------------
    primary_by_arm = {}
    for arm in ARMS:
        recs = [n for n in nodes if n["arm"] == arm]
        assessed = [n for n in recs if n["parsed"]]
        sections = [n["parsed"].get("primary") or {} for n in assessed]
        major_nodes = sum(
            1 for sec in sections
            if any(c.get("severity") == "major" for c in sec.get("claims") or []))
        block = dim_block(sections)
        block.update({
            "n_planned": len(recs),
            "n_assessed": len(assessed),
            "n_unassessed": len(recs) - len(assessed),
            "primary_major_nodes": major_nodes,
            "primary_major_free": len(assessed) - major_nodes,
            "claims": claim_block(sections),
        })
        primary_by_arm[arm] = block

    # ---- backups by arm -----------------------------------------------------
    backup_by_arm = {}
    for arm in ARMS:
        assessed = [n for n in nodes if n["arm"] == arm and n["parsed"]]
        slots = []
        for n in assessed:
            for b in n["parsed"].get("backups") or []:
                slots.append(b)
        block = dim_block(slots)
        block.update({
            "n_slots": len(slots),
            "slots_with_major": sum(
                1 for s in slots
                if any(c.get("severity") == "major" for c in s.get("claims") or [])),
            "backup_major_claims": sum(
                1 for s in slots for c in s.get("claims") or []
                if c.get("severity") == "major"),
            "claims": claim_block(slots),
        })
        backup_by_arm[arm] = block

    total_slots = sum(backup_by_arm[a]["n_slots"] for a in ARMS)
    if total_slots != 769:
        raise SystemExit("backup slots != 769: %d" % total_slots)

    # ---- story x arm --------------------------------------------------------
    story_arm = {}
    for story in STORIES:
        for arm in ARMS:
            assessed = [n for n in nodes
                        if n["story"] == story and n["arm"] == arm and n["parsed"]]
            sections = [n["parsed"].get("primary") or {} for n in assessed]
            block = dim_block(sections)
            all_vals = [v for d in DIMS for v in
                        ([] if block["dims_mean"][d] is None else [])]
            # unweighted over every non-null (dim, node) cell
            cells = []
            for sec in sections:
                sc = sec_scores(sec)
                cells.extend(v for v in sc.values() if v is not None)
            major_claims = sum(
                1 for sec in sections for c in sec.get("claims") or []
                if c.get("severity") == "major")
            major_nodes = sum(
                1 for sec in sections
                if any(c.get("severity") == "major" for c in sec.get("claims") or []))
            slots = [b for n in assessed for b in n["parsed"].get("backups") or []]
            backup_major = sum(
                1 for s in slots for c in s.get("claims") or []
                if c.get("severity") == "major")
            story_arm["%s|%s" % (story, arm)] = {
                "story": story, "arm": arm,
                "n_assessed": len(assessed),
                "n_planned": 8,
                "dims_mean_unweighted": mean(cells),
                "primary_major_claims": major_claims,
                "primary_major_nodes": major_nodes,
                "backup_slots": len(slots),
                "backup_major_claims": backup_major,
            }

    # ---- same-batch strict pairs -------------------------------------------
    by_case_arm = {(n["case_id"], n["arm"]): n for n in primary_nodes}
    all_cases = sorted(set(n["case_id"] for n in nodes))
    pairs = [
        ("reference_product_v1", "goal_action_v1", "C4 sweep_a (same batch)"),
        ("v5_purpose_aligned", "evidence_memory_v1", "C4 sweep_b (same batch)"),
    ]
    pair_rows = []
    for a, b, label in pairs:
        wins_a = ties = wins_b = incomparable = 0
        by_story = {s: {"w": 0, "t": 0, "l": 0, "x": 0} for s in STORIES}
        comparable = 0
        for case in all_cases:
            na = by_case_arm.get((case, a))
            nb = by_case_arm.get((case, b))
            story = case.split("_")[0]
            if na is None or nb is None:
                incomparable += 1
                by_story[story]["x"] += 1
                continue
            sa = sec_scores(na["parsed"].get("primary") or {})
            sb = sec_scores(nb["parsed"].get("primary") or {})
            diffs = [sa[d] - sb[d] for d in DIMS
                     if sa.get(d) is not None and sb.get(d) is not None]
            if not diffs:
                incomparable += 1
                by_story[story]["x"] += 1
                continue
            comparable += 1
            m = sum(diffs) / len(diffs)
            if m > 0.5:
                wins_a += 1
                by_story[story]["w"] += 1
            elif m < -0.5:
                wins_b += 1
                by_story[story]["l"] += 1
            else:
                ties += 1
                by_story[story]["t"] += 1
        pair_rows.append({
            "pair": "%s vs %s" % (a, b),
            "a": a, "b": b, "label": label,
            "n_planned": len(all_cases),
            "n_comparable": comparable,
            "wins_a": wins_a, "ties": ties, "wins_b": wins_b,
            "incomparable": incomparable,
            "by_story": by_story,
            "note": "同一 C4 批内严格配对；|九维均差|<=0.5 记平。"
                    "跨批横比不作严格比较。",
        })

    # ---- derived indicators -------------------------------------------------
    assessed_items = set(n["item_id"] for n in primary_nodes)
    maj_any_nodes = set()
    maj_primary_nodes = set()
    maj_backup_nodes = set()
    for n in primary_nodes:
        p = n["parsed"]
        pm = any(c.get("severity") == "major"
                 for c in (p.get("primary") or {}).get("claims") or [])
        bm = any(c.get("severity") == "major"
                 for b in p.get("backups") or []
                 for c in b.get("claims") or [])
        if pm:
            maj_primary_nodes.add(n["item_id"])
        if bm:
            maj_backup_nodes.add(n["item_id"])
        if pm or bm:
            maj_any_nodes.add(n["item_id"])
    strict_usable = set(n["item_id"] for n in primary_nodes
                        if n["has_text"] and n["item_id"] not in maj_any_nodes)
    any_text = [n for n in nodes if n["has_text"]]

    derived_by_arm = {}
    for arm in ARMS:
        recs = [n for n in nodes if n["arm"] == arm]
        ass = [n for n in recs if n["parsed"]]
        usable = [n for n in ass if n["item_id"] in strict_usable]
        pmf = [n for n in ass if n["item_id"] not in maj_primary_nodes]
        bmf = [n for n in ass if n["item_id"] not in maj_backup_nodes]
        txt = [n for n in recs if n["has_text"]]
        derived_by_arm[arm] = {
            "planned": len(recs),
            "assessed": len(ass),
            "has_any_sendable_text": len(txt),
            "primary_major_free": len(pmf),
            "backup_major_free": len(bmf),
            "strict_usable": len(usable),
            "strict_usable_rate_assessed": pct(len(usable), len(ass)),
            "strict_usable_rate_planned": pct(len(usable), len(recs)),
        }
    derived = {
        "definitions": {
            "any_sendable_text": "reply_raw 的 response 四个候选或 directions 至少一个非空字符串；只证明有字，不证明质量。",
            "strict_usable": "has_any_sendable_text 且判词可解析 且 primary+backups 全部已判小节均无 major；未评=unknown，不算失败。",
            "primary_major_free": "首选小节无 major 的可评首选数。",
            "backup_major_free": "全部备选小节无 major 的可评首选数。",
        },
        "n_planned": len(nodes),
        "n_assessed": len(primary_nodes),
        "has_any_sendable_text": {
            "n": len(any_text), "n_planned": len(nodes),
            "rate": pct(len(any_text), len(nodes)),
        },
        "strict_usable": {
            "assessed": len(primary_nodes),
            "usable": len(strict_usable),
            "rate_assessed": pct(len(strict_usable), len(primary_nodes)),
            "planned": len(nodes),
            "rate_planned_floor": pct(len(strict_usable), len(nodes)),
            "unassessed_unknown": len(unassessed),
            "unassessed_items": [n["item_id"] for n in unassessed],
        },
        "primary_major_free": {
            "assessed": len(primary_nodes),
            "nodes": len(primary_nodes) - len(maj_primary_nodes),
            "rate": pct(len(primary_nodes) - len(maj_primary_nodes),
                        len(primary_nodes)),
            "items_with_major": sorted(maj_primary_nodes),
        },
        "backup_major_free": {
            "assessed": len(primary_nodes),
            "nodes": len(primary_nodes) - len(maj_backup_nodes),
            "rate": pct(len(primary_nodes) - len(maj_backup_nodes),
                        len(primary_nodes)),
            "items_with_major": sorted(maj_backup_nodes),
        },
        "major_claims_by_section": {
            "primary": sum(1 for n in primary_nodes
                           for c in (n["parsed"].get("primary") or {}
                                     ).get("claims") or []
                           if c.get("severity") == "major"),
            "backup": sum(1 for n in primary_nodes
                          for b in n["parsed"].get("backups") or []
                          for c in b.get("claims") or []
                          if c.get("severity") == "major"),
        },
        "backup_major_slots": [
            {"item_id": n["item_id"], "story": n["story"], "arm": n["arm"],
             "slot": b.get("slot"),
             "major_claims": sum(1 for c in b.get("claims") or []
                                 if c.get("severity") == "major")}
            for n in primary_nodes for b in n["parsed"].get("backups") or []
            if any(c.get("severity") == "major" for c in b.get("claims") or [])
        ],
        "by_arm": derived_by_arm,
    }

    # ---- worker overlay (human-readable review, not machine judge scores) ---
    overlay = {
        "nature": "worker-AI 亲读复核（同一 AI 作者侧的第二双眼睛），不是独立人类金标；"
                  "只登记分歧与漏检，不改历史判词分数。",
        "primary_read": {
            "documents": [
                "evaluation/B3_PRIMARY_READ_E006_V1.md",
                "evaluation/B3_PRIMARY_READ_E007_V1.md",
                "evaluation/B3_PRIMARY_READ_E008_V1.md",
                "evaluation/B3_PRIMARY_READ_E012_V1.md",
            ],
            "stories_read": 4,
            "primary_texts_read": 128,
            "primary_with_verdict_reported_by_B3": 121,
            "primary_with_verdict_recomputed": len(primary_nodes),
            "score_corrections": 0,
            "note": "四片合计 117 条可评首选；B3 分片内的 121 为手工计数，"
                    "V2 按 RATINGS_REPAIRED 重算并已在 corrections 登记。",
        },
        "missed_detections": [
            {
                "item_id": "Q? (C2/E012_T03)",
                "case_id": "E012_T03",
                "arm": "goal_action_v1",
                "severity": "major",
                "finding": "首选“那家我也一直想再去一次”是无据亲历断言（原话只支持“你上次提过”，"
                           "不支持“我也去过”）；判词仅 factuality=2，未标 major。",
                "source": "evaluation/B3_PRIMARY_READ_E012_V1.md §1/§2",
                "effect": "不修改历史判词；登记为 V1_1 下批判的控制/回归位点。",
            },
            {
                "item_id": "Q? (v5/E008_T02)",
                "case_id": "E008_T02",
                "arm": "v5_purpose_aligned",
                "severity": "minor",
                "finding": "用“他”指小林，题面未给小林性别；无据人称具体化，判词未标 minor。",
                "source": "evaluation/B3_PRIMARY_READ_E008_V1.md §1/§2",
                "effect": "登记为 minor 漏检；V1_1 §0-7 覆盖，不修改分数。",
            },
        ],
        "major_review": {
            "source": "evaluation/QUALITY_PANEL_MAJOR_REVIEW_V1.md",
            "control_major_claims": 20,
            "real_arm_major_claims": 10,
            "confirmed_candidate_defects": 9,
            "doubtful": 1,
            "doubtful_entry": {
                "item_id": "Q054",
                "case_id": "E012_T05",
                "arm": "v5_purpose_aligned",
                "where": "backup:directions[3]",
                "quote": "先不说这个了",
                "reason": "备选槽允许弱选项；以“关闭话题”判 major 存疑，待备选风险表统一裁量。",
            },
            "hotspot": "E008_T03（ref+C3 共 8 条 major）",
            "note": "控制 20 条 major 全部落在预期应判错的控制上；真实臂 10 条中 9 条确认为候选缺陷。",
        },
        "quote_review": {
            "source": "evaluation/QUALITY_PANEL_MAJOR_REVIEW_V1.md §2",
            "raw_level_flags": 19,
            "validator_false_positives": 16,
            "whitespace_or_punct_edits": 3,
            "fabrications": 0,
            "residual_analyzer_flags": 5,
            "residual_items": ["Q015", "Q034", "Q044", "Q081", "Q111"],
            "residual_note": "V1_1 分析器仍有 5 条归一化未命中（Q015 卡片文本重建缺口；"
                             "Q034/Q044/Q081 为轻微改写；Q111 待核）。"
                             "均为判词审计队列，不当作考生错误。",
            "corrected_reading": "撤销 V1 “19 处不符=判词不可靠”的读法：19 条中 16 条是分析器"
                                 "输入重建假阳性，3 条是空格/标点级编辑，0 条捏造。",
        },
        "qualitative_findings": [
            {
                "id": "F1",
                "finding": "E006 跨轮回扣分野：C2/C3 在 T05/T07 主动回扣“昨天被当众批评”，"
                           "ref/v5 全故事零回扣。",
                "evidence": "B3 E006 §2；卡把回扣列为 optional_history、无 required atoms，"
                            "memory_use 不计分。",
                "action": "九维未覆盖的正向行为；下一版卡增设 optional 回扣加分观察，不加扣分。",
            },
            {
                "id": "F2",
                "finding": "C3 在 E007_T01 第一轮情绪位塞入隐性邀约（“出来吃口热的，就当逃跑第一站”）。",
                "evidence": "B3 E007 §1；judge goal=1 压分正确。",
                "action": "动作层加“首情绪位不塞邀约”护栏；列 M2 回归。",
            },
            {
                "id": "F3",
                "finding": "E007_T03 首选层四臂均无编造亲历；编造在 C2 备选槽（directions[1]）。",
                "evidence": "B3 E007 §2；major 复核确认。",
                "action": "首选/备选必须分开读，备选 major 不得被首选层均分稀释。",
            },
            {
                "id": "F4",
                "finding": "回扣行为跨臂不稳定：E007_T07 v5 回扣最佳；E006 则 C2/C3 回扣。",
                "evidence": "B3 E006/E007 §2。",
                "action": "不能把“某臂会回扣”当稳定属性；需多故事收尾/转场位采样。",
            },
            {
                "id": "F5",
                "finding": "E008_T03 factuality 梯度 0/1/2/2 与无据确定否定条数 3/2/1/1 精确对应；"
                           "v5 是唯一引用有据事实的臂。",
                "evidence": "B3 E008 §1/§2；4 条 major 落在 f0/f1 两臂。",
                "action": "本热点上判词事实维可信度正例；作为裁判锚例。",
            },
            {
                "id": "F6",
                "finding": "E008_T06 memory_use 对“行动即确认”不敏感：ref/C2 用行动配合得 m=None，"
                           "v5/C3 显式复述得 m=3。",
                "evidence": "B3 E008 §2。",
                "action": "m 均分差不得直接读成“记忆能力差”；口径注记。",
            },
            {
                "id": "F7",
                "finding": "E012_T03 C2 首选“那家我也一直想再去一次”漏标 major（无据亲历）。",
                "evidence": "B3 E012 §1；与控制组 CTL06/08、E007_T03 备选同族。",
                "action": "V1_1 §0-7+§2a 必要性的第三处证据；下批带 CTL33-36 复测。",
            },
            {
                "id": "F8",
                "finding": "判词轻微保守：打趣/计划自述被 factuality=2 压半档（C2/C3 E012_T01、"
                           "ref E012_T02），无 0/1 级错判。",
                "evidence": "B3 E012 §2。",
                "action": "会系统性压低 interaction 高臂的 factuality 均分；V2 注记，不改分。",
            },
        ],
        "positive_anchors": [
            "v5 E012_T04 “话我收回一半”（退边界/不赌气）",
            "v5 E012_T05 “是我推得太急了…去不去、什么时候去你说了算，不去我也不会多想”（认领+还主动权）",
            "C2 E012_T08 “这句我先收着，等你困劲儿过去再说”（接信号不逼定）",
        ],
    }

    # ---- knowledge denominator ---------------------------------------------
    k_rows = {}
    for arm in ARMS:
        r = knowledge["rows"][arm]
        k_rows[arm] = {
            "S1_T08_present": r.get("S1_T08_present"),
            "S1_store": r.get("S1_store"),
            "S1_distilled": r.get("S1_distilled"),
            "S2_entries": r.get("S2_entries"),
            "S2_src_resolvable_rate": r.get("S2_src_resolvable_rate"),
            "S2_src_rank1_rate": r.get("S2_src_rank1_rate"),
            "S2_src_grounded_rate": r.get("S2_src_grounded_rate"),
            "S2_src_weak_rate": r.get("S2_src_weak_rate"),
            "S2_src_unsupported_rate": r.get("S2_src_unsupported_rate"),
            "S2_plan_grounded_rate": r.get("S2_plan_grounded_rate"),
            "S3_present": r.get("S3_present"),
            "S3_structured": r.get("S3_structured"),
            "S3_raw_only_rate": r.get("S3_raw_only_rate"),
            "S3_backref": r.get("S3_backref"),
            "S4": r.get("S4"),
        }
    knowledge_denominator = {
        "source": "evaluation/KNOWLEDGE_STAGE_SCORECARD_V1.json",
        "real_model_calls": knowledge.get("real_model_calls"),
        "atoms_total_chat_messages": knowledge.get("atoms_total"),
        "excluded_note_intent_records": knowledge.get("atoms_excluded"),
        "panel_atoms_in_scope": 40,
        "pairs": 158,
        "storage_observable_arms": ["reference_product_v1"],
        "rows": k_rows,
        "reading_rules": [
            "45 条是本卡集聊天消息原子；38 条是被排除的 note/intent 记录；两者不是同一总体的加减，"
            "不可用 45-38 推 40。",
            "S1 三列数据源不同，不可相加；S1_store 只有 reference_product_v1 可观测。",
            "S3 只证明“进了出站 prompt”，不证明回复用对；S4 为 NR-需人判。",
            "S2 只有有落盘存储的臂可测；本批仅 reference_product_v1，其他 NR=覆盖缺口，不是零分。",
        ],
    }

    # ---- corrections / lineage ---------------------------------------------
    corrections = [
        {
            "item": "B3 分片的首选可评数",
            "original": "B3 E012 分片写四片合计 121 条有判词；E008 分片写 31 条；"
                        "四片分项相加为 118（E006 31 + E007 30 + E008 31 + E012 26）。",
            "corrected": "按 RATINGS_REPAIRED 重算：117 条首选可评；四片为 "
                         "31/30/30/26（E008 缺 Q118 E008_T04、Q104 E008_T05，均为 C2 未评）。",
            "reason": "手工分片计数未按可解析字段重算，且分片间算术不一致；"
                      "V2 以机器分母为准。",
            "sources": ["evaluation/B3_PRIMARY_READ_E008_V1.md",
                        "evaluation/B3_PRIMARY_READ_E012_V1.md",
                        "logs/quality_panel_v1_live_20261027_003723/RATINGS_REPAIRED.jsonl"],
        },
        {
            "item": "重测差≤1 均值",
            "original": "V1 写 7.22/9。",
            "corrected": "按 9 条可比复算 7.11/9（64/9）；逐维精确一致 6.56/9（59/9）不变。",
            "reason": "V1 的分母/求和口径笔误；V2 由 repeat rows 重算。",
            "sources": ["logs/quality_panel_v1_live_20261027_003723/QUALITY_PANEL_ANALYSIS_V1_1.json"],
        },
        {
            "item": "判词引文 19 处不符",
            "original": "V1 把 19 条原始 flag 读成“判词引文不可靠”的证据。",
            "corrected": "19 条中 16 条为分析器输入重建假阳性、3 条为空格/标点级轻微编辑、0 条捏造；"
                         "V1_1 分析器仍有 5 条 residual 审计队列（Q015/Q034/Q044/Q081/Q111）。",
            "reason": "原始 flag 是待核队列，不是判词错误；major 复核已逐条人工对读。",
            "sources": ["evaluation/QUALITY_PANEL_SCORECARD_V1.md",
                        "evaluation/QUALITY_PANEL_MAJOR_REVIEW_V1.md",
                        "logs/quality_panel_v1_live_20261027_003723/QUALITY_PANEL_ANALYSIS_V1_1.json"],
        },
        {
            "item": "首选/备选均分口径",
            "original": "V1 §2 的逐维均分是“首选+非空备选合并段”口径，n 为首选数。",
            "corrected": "V2 把 primary（117）与 backups（769 槽）完全分开；"
                         "合并段仅作为 legacy_merged_view 附录保留，不用于判优。",
            "reason": "备选是弱选项，混入均值会稀释/放大风险，且不同臂备选槽数不同。",
            "sources": ["evaluation/QUALITY_PANEL_SCORECARD_V1.md",
                        "logs/quality_panel_v1_live_20261027_003723/QUALITY_PANEL_ANALYSIS_V1_1.json"],
        },
    ]

    doc = {
        "kind": "quality_panel_scorecard_v2",
        "date": "2026-10-27",
        "generated_by": "tools/build_quality_scorecard_v2.py",
        "status": "judge scores unchanged; denominators and primary/backup split corrected",
        "sources": {
            "ratings": RATINGS.replace("\\", "/"),
            "analysis": ANALYSIS.replace("\\", "/"),
            "bindings": BINDINGS.replace("\\", "/"),
            "knowledge_scorecard": KNOWLEDGE.replace("\\", "/"),
            "judge_manual": "evaluation/QUALITY_MANUAL_V1_1.md",
            "worker_reads": [
                "evaluation/B3_PRIMARY_READ_E006_V1.md",
                "evaluation/B3_PRIMARY_READ_E007_V1.md",
                "evaluation/B3_PRIMARY_READ_E008_V1.md",
                "evaluation/B3_PRIMARY_READ_E012_V1.md",
            ],
            "major_review": "evaluation/QUALITY_PANEL_MAJOR_REVIEW_V1.md",
        },
        "scope": {
            "batch_dir": BATCH.replace("\\", "/"),
            "authorization": "AUTH-QUALITY-PANEL-20261027-01",
            "authorization_status": "CLOSED 173/192; 余 19 不是补跑额度",
            "stories": STORIES,
            "arms": ARMS,
            "four_arm_batches": {
                "sweep_a": "reference_product_v1 vs goal_action_v1",
                "sweep_b": "v5_purpose_aligned vs evidence_memory_v1",
            },
            "judge_identity": "worker-AI 亲笔开发控制 + AI 判词；非独立封存集、非人类标定。",
            "arm_identities": {
                a: {
                    "historical_c4": HIST_ID[a],
                    "current": CUR_ID[a],
                    "changed": HIST_ID[a] != CUR_ID[a],
                    "what_changed": ID_NOTE[a],
                } for a in ARMS
            },
            "identity_warning": "本成绩单判的是 C4 历史答卷（历史身份）；"
                                "当前候选身份已变，不得把本成绩单直接当作当前代码的成绩。"
                                "P06 必须按新身份重跑。",
        },
        "denominators": {
            "attempts_total": 173,
            "primary_planned": len(nodes),
            "primary_assessed": len(primary_nodes),
            "primary_unassessed": len(unassessed),
            "primary_unassessed_nodes": [n["item_id"] for n in unassessed],
            "primary_unassessed_detail": [
                {"item_id": n["item_id"], "case_id": n["case_id"], "arm": n["arm"]}
                for n in unassessed
            ],
            "control_planned": 32,
            "control_assessed": analysis["controls_qualification"]["n_assessed"],
            "repeat_planned": 13,
            "repeat_assessed": analysis["repeat_consistency"]["n"],
            "repeat_unassessed": 3,
            "backup_slots": total_slots,
            "backup_slots_by_arm": {a: backup_by_arm[a]["n_slots"] for a in ARMS},
            "claimed_total_assessed": analysis["accounting"]["assessed"],
            "claimed_total_unassessed": analysis["accounting"]["unassessed"],
        },
        "qualification": {
            "controls": analysis["controls_qualification"],
            "repeat_consistency": {
                "n": analysis["repeat_consistency"]["n"],
                "comparable": analysis["repeat_consistency"]["comparable"],
                "mean_dims_exact": analysis["repeat_consistency"]["mean_dims_exact"],
                "mean_dims_within1": mean([
                    r["dims_within1"] for r in analysis["repeat_consistency"]["rows"]
                    if r.get("comparable")]),
                "rows": analysis["repeat_consistency"]["rows"],
            },
            "quote_audit": overlay["quote_review"],
            "judge_identity": "AI 辅助读数；不得单独决定晋级/淘汰。",
        },
        "judge_scores": {
            "primary": {
                "n": len(primary_nodes),
                "by_arm": primary_by_arm,
                "by_story_arm": story_arm,
                "claims": claim_block([
                    n["parsed"].get("primary") or {} for n in primary_nodes]),
            },
            "backups": {
                "n": total_slots,
                "by_arm": backup_by_arm,
                "claims": claim_block([
                    b for n in primary_nodes
                    for b in n["parsed"].get("backups") or []]),
            },
            "same_batch_pairs": pair_rows,
            "legacy_merged_view": {
                "note": "V1 合并段口径，仅作历史对照；不用于 V2 判优。",
                "source": "QUALITY_PANEL_ANALYSIS_V1_1.json per_arm",
                "by_arm": {
                    a: {
                        "n_answers": analysis["per_arm"][a]["n_answers"],
                        "dims_mean": analysis["per_arm"][a]["dims_mean"],
                        "claims": analysis["per_arm"][a]["claims"],
                    } for a in ARMS
                },
            },
        },
        "worker_review_overlay": overlay,
        "derived_indicators": derived,
        "knowledge_denominator": knowledge_denominator,
        "unknowns": [
            "用户愿发 W：unknown（模型禁填；32 样本表仍未回收）。",
            "金额：unknown（无可核对单价；judge 侧 token 记账在账本）。",
            "泛化：四个故事全部 dev_exposed，本成绩只对开发集成立。",
            "11 条首选未评：unknown，不是失败，也不是零分。",
            "九维资格：AI 辅助读数；重测逐维精确一致 6.56/9，未达“全信”门槛。",
        ],
        "comparability": {
            "four_arm_note": "四臂横比来自 C4 两批（sweep_a ref/C2，sweep_b v5/C3），"
                             "批次/时段混杂；仅同批配对可作严格比较，跨批横比只作探索，"
                             "不得据此晋级/淘汰。",
            "same_batch_only": [p["pair"] for p in pair_rows],
            "not_rankable": "C2 与 C3、ref 与 v5、ref 与 C3 等跨批组合均无同批严格配对。",
        },
        "corrections": corrections,
        "headline": {
            "primary_assessed": "%d/%d" % (len(primary_nodes), len(nodes)),
            "primary_unassessed": len(unassessed),
            "primary_major_free": "%d/%d" % (
                len(primary_nodes) - len(maj_primary_nodes), len(primary_nodes)),
            "strict_usable": "%d/%d assessed" % (len(strict_usable), len(primary_nodes)),
            "strict_usable_rate_assessed": pct(len(strict_usable), len(primary_nodes)),
            "backup_slots": total_slots,
            "backup_major_claims": derived["major_claims_by_section"]["backup"],
            "same_batch_pairs": [
                "%s: %d/%d/%d (a/tie/b), incomparable=%d" % (
                    p["pair"], p["wins_a"], p["ties"], p["wins_b"],
                    p["incomparable"]) for p in pair_rows
            ],
            "winner": "none declared; cross-batch ranking forbidden",
        },
        "evidence_index": [
            BATCH.replace("\\", "/") + "/RATINGS_REPAIRED.jsonl",
            BATCH.replace("\\", "/") + "/QUALITY_PANEL_ANALYSIS_V1_1.json",
            BATCH.replace("\\", "/") + "/JUDGE_BATCH_SUMMARY.json",
            "refs/experiment_protocol_20261023/knowledge_quality_independent_review_20261026/"
            "PRIVATE_BINDINGS.json",
            "evaluation/KNOWLEDGE_STAGE_SCORECARD_V1.json",
        ],
    }

    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    write_markdown(doc)
    print("wrote %s" % OUT_JSON)
    print("wrote %s" % OUT_MD)
    print("primary=%d/%d backups=%d strict_usable=%d/%d pairs=%s" % (
        len(primary_nodes), len(nodes), total_slots, len(strict_usable),
        len(primary_nodes), [p["pair"] for p in pair_rows]))
    return 0


def fmt(v, nd=2):
    if v is None:
        return "—"
    if isinstance(v, float):
        return ("%." + str(nd) + "f") % v
    return str(v)


def fmt_pct(v):
    if v is None:
        return "—"
    return "%.1f%%" % v


def write_markdown(doc):
    d = doc["denominators"]
    q = doc["qualification"]
    ps = doc["judge_scores"]["primary"]
    bs = doc["judge_scores"]["backups"]
    dr = doc["derived_indicators"]
    kd = doc["knowledge_denominator"]
    L = []
    A = L.append

    A("# QUALITY_PANEL_SCORECARD_V2 — 质量成绩单 V2（判词/亲读/派生三层分开）")
    A("")
    A("日期：2026-10-27。对象：`logs/quality_panel_v1_live_20261027_003723`"
      "（AUTH-QUALITY-PANEL-20261027-01，CLOSED 173/192）。")
    A("性质：判词分数**不改**；V2 修分母、拆首选/备选、把 worker 亲读与派生指标分栏。"
      "不宣布胜者，跨批横比只作探索。")
    A("")
    A("> **身份范围（必读）**：本成绩单判的是 C4 历史答卷，四臂历史身份为 "
      "ref `6e65b6bc…`、v5 `ef855510…`、C2 `de600ff9…`、C3 `009d2425…`；"
      "当前候选身份已变（ref `501bc426…`、v5 `7c6b51ce…`、C2 `fc296dd8…`、"
      "C3 `fddcdb51…`），因此本成绩单**不能**直接当作当前代码的成绩，P06 必须按新身份重跑。")
    A("")
    A("## 0. 一句话结论")
    A("")
    A("- 首选可评 **%d/%d**；首选无 major **%s**；备选 **%d** 槽、"
      "备选 major **%d** 条。"
      % (d["primary_assessed"], d["primary_planned"],
         doc["headline"]["primary_major_free"], d["backup_slots"],
         doc["headline"]["backup_major_claims"]))
    A("- 严格可用（有可发文本 + 已判小节无 major）**%d/%d 已评**（%s）；"
      "另有 %d 条未评=unknown，不是失败。"
      % (dr["strict_usable"]["usable"], dr["strict_usable"]["assessed"],
         fmt_pct(dr["strict_usable"]["rate_assessed"]),
         dr["strict_usable"]["unassessed_unknown"]))
    A("- 同批严格配对：%s。" % "；".join(doc["headline"]["same_batch_pairs"]))
    A("- 结论只支持“本轮四臂在四个开发故事上的质量分布与缺陷族”，"
      "不支持跨批排名，也不支持用户愿发 W。")
    A("")

    A("## 1. 分母与未评（如实）")
    A("")
    A("| 项 | 数量 | 说明 |")
    A("|---|---:|---|")
    A("| 全批 attempt | %d | 173/173 派发、0 传输失败、0 停批 |" % d["attempts_total"])
    A("| 首选计划 | %d | 4 故事 × 8 位点 × 4 臂 |" % d["primary_planned"])
    A("| 首选可评 | **%d** | 判词可解析且 schema 通过 |" % d["primary_assessed"])
    A("| 首选未评 | **%d** | %s |" % (d["primary_unassessed"],
                                     "、".join(d["primary_unassessed_nodes"])))
    A("| 开发控制 | %d/%d | 16 应判错 + 16 应保护 |"
      % (d["control_assessed"], d["control_planned"]))
    A("| 固定复评 | %d/%d 可评 | 3 条未评（Q083/Q071/Q107） |"
      % (d["repeat_assessed"], d["repeat_planned"]))
    A("| 已评总数 | %d | 117 首选 + 32 控制 + 10 复评 = 159 |"
      % d["claimed_total_assessed"])
    A("| 备选槽 | **%d** | 仅已评首选展开；按臂 %s |"
      % (d["backup_slots"],
         "、".join("%s=%d" % (ARM_CN[a], d["backup_slots_by_arm"][a])
                   for a in ARMS)))
    A("")
    A("未评明细：")
    A("")
    for x in d["primary_unassessed_detail"]:
        A("- `%s` %s / %s" % (x["item_id"], x["case_id"], x["arm"]))
    A("")
    A("臂身份对照（历史答卷 vs 当前代码）：")
    A("")
    A("| 臂 | C4 历史身份 | 当前身份 | 变化 |")
    A("|---|---|---|---|")
    for arm in ARMS:
        ai = doc["scope"]["arm_identities"][arm]
        A("| %s | `%s` | `%s` | %s |" % (
            ARM_CN[arm], ai["historical_c4"][:12] + "…",
            ai["current"][:12] + "…", ai["what_changed"]))
    A("")
    A("> %s" % doc["scope"]["identity_warning"])
    A("")

    A("## 2. 判词分数（首选，n=%d；不混备选）" % ps["n"])
    A("")
    A("| 臂 | n | " + " | ".join(DIM_CN[x] for x in DIMS) + " | claims(总/major) |")
    A("|---|---:|" + "---:|" * len(DIMS) + "---|")
    for arm in ARMS:
        b = ps["by_arm"][arm]
        A("| %s | %d | " % (ARM_CN[arm], b["n_assessed"]) +
          " | ".join(fmt(b["dims_mean"][x]) for x in DIMS) +
          " | %d / %d |" % (b["claims"]["total"],
                            b["claims"]["by_severity"].get("major", 0)))
    A("")
    A("NA 说明：每维的 `dims_na_n` 见 JSON；time_state 的 NA 最多，"
      "不能把 NA 当 0 分。首选 major 节点：%s。"
      % ("、".join(dr["primary_major_free"]["items_with_major"]) or "无"))
    A("")

    A("## 3. 判词分数（备选，%d 槽；弱选项单列）" % bs["n"])
    A("")
    A("| 臂 | 槽数 | " + " | ".join(DIM_CN[x] for x in DIMS) +
      " | 含 major 槽 | major 条数 |")
    A("|---|---:|" + "---:|" * len(DIMS) + "---:|---:|")
    for arm in ARMS:
        b = bs["by_arm"][arm]
        A("| %s | %d | " % (ARM_CN[arm], b["n_slots"]) +
          " | ".join(fmt(b["dims_mean"][x]) for x in DIMS) +
          " | %d | %d |" % (b["slots_with_major"], b["backup_major_claims"]))
    A("")
    A("备选 major 明细（严重备选错误）：")
    A("")
    A("| 首选 | 故事 | 臂 | 槽 | major 条数 |")
    A("|---|---|---|---|---:|")
    for x in dr["backup_major_slots"]:
        A("| %s | %s | %s | %s | %d |" % (
            x["item_id"], x["story"], ARM_CN[x["arm"]],
            x["slot"], x["major_claims"]))
    A("")
    A("合计 **%d 条 major / %d 个备选槽 / %d 条首选**（%s）；其中 v5 Q054"
      "“先不说这个了”经 major 复核为存疑（备选弱选项边界），其余 5 条为确认缺陷。"
      % (dr["major_claims_by_section"]["backup"],
         len(dr["backup_major_slots"]),
         len(set(x["item_id"] for x in dr["backup_major_slots"])),
         "、".join(sorted(set(x["item_id"] for x in dr["backup_major_slots"])))))
    A("")

    A("## 4. 故事级（首选；均值为九维非空单元的未加权平均）")
    A("")
    A("| 故事 | 臂 | 可评 | 均分 | 首选 major 条/节点 | 备选槽 | 备选 major |")
    A("|---|---|---:|---:|---|---:|---:|")
    for story in STORIES:
        for arm in ARMS:
            b = ps["by_story_arm"]["%s|%s" % (story, arm)]
            A("| %s | %s | %d/8 | %s | %d/%d | %d | %d |" % (
                story, ARM_CN[arm], b["n_assessed"],
                fmt(b["dims_mean_unweighted"]),
                b["primary_major_claims"], b["primary_major_nodes"],
                b["backup_slots"], b["backup_major_claims"]))
    A("")
    A("读法：故事级分母不同（E012 ref 仅 4/8，E008 C2 6/8），不得直接跨臂横比故事均分；"
      "故事级只用于定位缺陷热点。E008_T03 是热点。")
    A("")

    A("## 5. 同批严格配对（首选；全 32 槽为分母）")
    A("")
    A("| 配对 | 可比 | a 胜 | 平 | b 胜 | 不可比 |")
    A("|---|---:|---:|---:|---:|---:|")
    for p in doc["judge_scores"]["same_batch_pairs"]:
        A("| %s | %d | %d | %d | %d | %d |" % (
            p["pair"], p["n_comparable"], p["wins_a"], p["ties"],
            p["wins_b"], p["incomparable"]))
    A("")
    for p in doc["judge_scores"]["same_batch_pairs"]:
        A("- %s 分故事：%s" % (p["pair"], "；".join(
            "%s w/t/l/x=%d/%d/%d/%d" % (s, v["w"], v["t"], v["l"], v["x"])
            for s, v in p["by_story"].items())))
    A("- 跨批组合（ref vs C3、ref vs v5、C2 vs C3、v5 vs C2）没有同批严格配对；"
      "四臂横比来自两批，禁止据此晋级/淘汰。")
    A("")

    A("## 6. worker 亲读 overlay（不是判词分数，也不改分）")
    A("")
    A("### 6.1 覆盖与修正")
    A("")
    A("- 4 个故事、128 条首选原文全部亲读；117 条有判词逐一对读；"
      "**判词分数零修正**。B3 分片手写 121 条，V2 重算为 117 条（见 §10）。")
    A("- 亲读性质：同一 AI 作者侧的第二双眼睛，非独立人类金标。")
    A("")
    A("### 6.2 漏检登记（不改历史判词）")
    A("")
    A("| 位点 | 臂 | 级别 | 亲读发现 |")
    A("|---|---|---|---|")
    for x in doc["worker_review_overlay"]["missed_detections"]:
        A("| %s | %s | %s | %s |" % (
            x["case_id"], ARM_CN[x["arm"]], x["severity"], x["finding"]))
    A("")
    A("### 6.3 major 复核")
    A("")
    mr = doc["worker_review_overlay"]["major_review"]
    A("- 控制 major 20 条：全部落在预期应判错控制上。")
    A("- 真实臂 major 10 条：确认候选缺陷 9 条；存疑 1 条（%s %s `%s`）。"
      % (mr["doubtful_entry"]["case_id"], mr["doubtful_entry"]["arm"],
         mr["doubtful_entry"]["quote"]))
    A("- 热点：%s。" % mr["hotspot"])
    A("")
    A("### 6.4 引文核对")
    A("")
    qr = doc["worker_review_overlay"]["quote_review"]
    A("- 原始 flag 19 条：分析器重建假阳性 16 条、空格/标点级编辑 3 条、捏造 0 条。")
    A("- V1_1 分析器仍有 5 条 residual（%s），列入审计队列，不当作考生错误。"
      % "、".join(qr["residual_items"]))
    A("")
    A("### 6.5 定性发现（不进九维分）")
    A("")
    for f in doc["worker_review_overlay"]["qualitative_findings"]:
        A("- **%s** %s 证据：%s 动作：%s" % (
            f["id"], f["finding"], f["evidence"], f["action"]))
    A("")
    A("正向锚例候选：%s。" % "；".join(doc["worker_review_overlay"]["positive_anchors"]))
    A("")

    A("## 7. 派生指标：至少一个可用率")
    A("")
    A("| 臂 | 计划 | 可评 | 有可发文本 | 首选无 major | 备选无 major | 严格可用/可评 | 严格可用/计划 |")
    A("|---|---:|---:|---:|---:|---:|---:|---:|")
    for arm in ARMS:
        b = dr["by_arm"][arm]
        A("| %s | %d | %d | %d | %d | %d | %d（%s） | %d（%s） |" % (
            ARM_CN[arm], b["planned"], b["assessed"], b["has_any_sendable_text"],
            b["primary_major_free"], b["backup_major_free"], b["strict_usable"],
            fmt_pct(b["strict_usable_rate_assessed"]),
            b["strict_usable"], fmt_pct(b["strict_usable_rate_planned"])))
    A("")
    A("定义：**有可发文本**=response 四候选或 directions 至少一个非空字符串；"
      "**严格可用**=有可发文本 + 判词可解析 + primary/backups 全部已判小节无 major。"
      "未评 11 条不进入分母，按 unknown 处理；因此“严格可用/计划”是下界，不是失败率。")
    A("")
    A("- 全批有可发文本（至少一个可发候选）：**%d/%d（%s）**；"
      "这只证明有字，不证明质量。"
      % (dr["has_any_sendable_text"]["n"], dr["has_any_sendable_text"]["n_planned"],
         fmt_pct(dr["has_any_sendable_text"]["rate"])))
    A("- 首选 major 条数 %d、备选 major 条数 %d；首选 major 节点 %s。"
      % (dr["major_claims_by_section"]["primary"],
         dr["major_claims_by_section"]["backup"],
         "、".join(dr["primary_major_free"]["items_with_major"]) or "无"))
    A("")

    A("## 8. 知识分母（与质量分分开）")
    A("")
    A("来源：`%s`（离线复算，真实调用 %s）。"
      % (kd["source"], kd["real_model_calls"]))
    A("")
    A("- 全卡集聊天消息原子 **%d** 条；被排除的 note/intent 记录 **%d** 条；"
      "本 panel 的迟于 T08 原子作用域 **%d** 条、配对 **%d**。"
      % (kd["atoms_total_chat_messages"], kd["excluded_note_intent_records"],
         kd["panel_atoms_in_scope"], kd["pairs"]))
    A("- 45 与 38 不是同一总体的加减，不能相减推 40；40 是 panel 作用域。")
    A("")
    A("| 臂 | S1 T08 在场 | S1 存储 | S2 条目 | S2 有据% | S3 在场 | S3 结构化 | S3 仅原始% | S4 |")
    A("|---|---|---|---:|---:|---|---:|---:|---|")
    for arm in ARMS:
        r = kd["rows"][arm]
        A("| %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
            ARM_CN[arm], r["S1_T08_present"], r["S1_store"],
            fmt(r["S2_entries"]), fmt_pct(r["S2_src_grounded_rate"]),
            r["S3_present"], r["S3_structured"],
            fmt_pct(r["S3_raw_only_rate"]), r["S4"]))
    A("")
    for rule in kd["reading_rules"]:
        A("- %s" % rule)
    A("")

    A("## 9. unknown 与可比性")
    A("")
    for u in doc["unknowns"]:
        A("- %s" % u)
    A("")
    A("- %s" % doc["comparability"]["four_arm_note"])
    A("- 不可排名组合：%s" % doc["comparability"]["not_rankable"])
    A("")

    A("## 10. 修正与口径（撤销/修正逐条）")
    A("")
    for c in doc["corrections"]:
        A("### %s" % c["item"])
        A("")
        A("- 原文：%s" % c["original"])
        A("- 修正：%s" % c["corrected"])
        A("- 原因：%s" % c["reason"])
        A("- 来源：%s" % "；".join(c["sources"]))
        A("")

    A("## 11. 证据索引")
    A("")
    for p in doc["evidence_index"]:
        A("- `%s`" % p)
    A("")
    A("---")
    A("")
    A("判词分数未改；V2 只改分母、拆首选/备选、并把 worker 亲读与派生指标分栏。"
      "任何晋级/淘汰结论须等 P06 三批同批严格配对与用户 W 标定，不能由本成绩单单独决定。")

    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")


if __name__ == "__main__":
    sys.exit(main())
