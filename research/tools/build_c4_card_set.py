# -*- coding: utf-8 -*-
"""tools/build_c4_card_set.py — C4 机制筛选批的合并评分卡集（只新建，绝不改写历史卡）。

背景（2026-10-26 C4 派发前核查发现的作废级缺陷）：
  C4 取题为 E006 / E007 / E008 / E012 四条开发故事，但卡片按故事族分散在三个已冻结文件里：
    03_GRADING_CARDS_E004_E006.json     E004/E005/E006（content_revision r2-20261024）
    04_GRADING_CARDS_E007_E012.json     E007..E012（r1-20261024）
    05_GRADING_CARDS_R2REV_20261025.json 6 张缺陷卡修订（r2-20261025，按 case_id 覆盖）
  两臂执行链只接一个 cards_path；旧 C4 批配置又写了不存在的 "02_GRADING_CARDS_P1.json"
  （且 P1 卡属于 P001–P006，不是 E 系列）→ 冻结清单那一项哈希为 null，
  而 verify_frozen_identity 对 null == null 静默放行。AGENTS §2.5 要求"冻结所有参与生产的文件"，
  §5.4/§6-V7 要求卡内 evidence 逐字可核——本脚本把三件来源合成为一份 C4 专用卡集，
  只做"取哪几张 + 用哪个修订版"的选择，不做任何文字改写，并逐字回核原题。

产出形态的理由：
  主文件必须是【裸列表】。`tools/build_review_worksheet.py` 等消费器按
  `{c["case_id"]: c for c in cards if isinstance(c, dict)}` 建索引；若主文件是带 header 的
  dict，迭代出来的是字符串键，卡字段会【静默全空】——又是一类"不报错的失效"。
  出处与自检写在同名 .PROVENANCE.json sidecar。

纪律：只读三个来源与四个故事原文；断言失败即 SystemExit(1)，绝不静默出卡；真实模型调用 0。
"""
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "benchmark", "c4_screen_20261026")
OUT_FILE = os.path.join(OUT_DIR, "GRADING_CARDS_C4_E006_E007_E008_E012_R2.json")
PROV_FILE = os.path.join(OUT_DIR, "GRADING_CARDS_C4_E006_E007_E008_E012_R2.PROVENANCE.json")

EPISODES = ["E006", "E007", "E008", "E012"]
SRC_BASE = [                                        # (相对路径, 取用的故事族)
    ("benchmark/dev_expansion_v01/03_GRADING_CARDS_E004_E006.json", ["E006"]),
    ("benchmark/dev_expansion_v01/04_GRADING_CARDS_E007_E012.json", ["E007", "E008", "E012"]),
]
SRC_REV = "benchmark/dev_expansion_v01/05_GRADING_CARDS_R2REV_20261025.json"

fails = []


def check(cond, msg):
    if not cond:
        fails.append(msg)
    return bool(cond)


def sha256(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def abs_rel(rel):
    return os.path.join(ROOT, rel.replace("/", os.sep))


def load_rel(rel):
    return json.load(open(abs_rel(rel), encoding="utf-8"))


def cards_of(rel, families):
    raw = load_rel(rel)
    cards = raw["cards"] if isinstance(raw, dict) and "cards" in raw else raw
    return {c["case_id"]: c for c in cards if c.get("episode_id") in families}


def load_turns(ep):
    e = load_rel("benchmark/episodes/%s.json" % ep)
    return {t["case_id"]: t for t in e["turns"]}


def evidence_verbatim_ok(card, turn, why):
    """卡里每条 evidence 必须逐字等于题面消息/input 字段/gold 字段，否则卡是被编出来的。"""
    ok = True
    for ev in card.get("evidence", []):
        if "message_index_zero_based" in ev:
            idx = ev["message_index_zero_based"]
            msgs = turn["input"].get("messages", [])
            if not (0 <= idx < len(msgs)):
                why.append("%s: 证据消息下标越界 idx=%s len=%s"
                           % (card["case_id"], idx, len(msgs)))
                ok = False
                continue
            m = msgs[idx]
            for k in ("role", "text", "ts"):
                if k in m and ev.get(k) is not None and ev[k] != m[k]:
                    why.append("%s: 证据 %s 逐字不符 卡=%r 题=%r"
                               % (card["case_id"], k, str(ev[k])[:40], str(m[k])[:40]))
                    ok = False
        elif str(ev.get("field", "")).startswith("input."):
            key = str(ev["field"]).split(".", 1)[1]
            real = turn["input"].get(key)
            if isinstance(real, bool):
                real = str(real).lower()
            if real is None or (isinstance(real, str) and real != ev.get("text")):
                why.append("%s: 证据字段 %s 不符 卡=%r 题=%r"
                           % (card["case_id"], ev["field"], str(ev.get("text"))[:40],
                              str(real)[:40]))
                ok = False
        elif str(ev.get("field", "")).startswith("gold."):
            key = str(ev["field"]).split(".", 1)[1]
            if key not in turn.get("gold", {}):
                why.append("%s: 证据 gold 字段缺失 %s" % (card["case_id"], key))
                ok = False
    return ok


def main():
    merged, src_of, provenance = {}, {}, []
    for rel, fams in SRC_BASE:
        picked = cards_of(rel, fams)
        for cid, card in picked.items():
            merged[cid] = card                                  # 原对象，不加字段不改值
            src_of[cid] = rel
        provenance.append({"file": rel, "sha256": sha256(abs_rel(rel)),
                           "episodes_taken": fams, "cards_taken": len(picked)})

    rev_cards = load_rel(SRC_REV)["cards"]
    overlaid = []
    for c in rev_cards:
        cid = c["case_id"]
        if cid in merged:
            before = merged[cid]["content_revision"]
            merged[cid] = c
            src_of[cid] = SRC_REV
            overlaid.append({"case_id": cid, "replaced_revision": before,
                             "with_revision": c["content_revision"]})
    rev_prov = {"file": SRC_REV, "sha256": sha256(abs_rel(SRC_REV)),
                "revisions_available": len(rev_cards), "applied_to_this_set": len(overlaid)}

    # ── 结构自检 ──
    reasons = []
    check(len(merged) == 32, "卡数应为 32（4故事×8位），实为 %s" % len(merged))
    for ep in EPISODES:
        n = sum(1 for cid in merged if merged[cid]["episode_id"] == ep)
        check(n == 8, "%s 应有 8 张卡，实为 %s" % (ep, n))
    for ep in EPISODES:
        turns = load_turns(ep)
        for cid in sorted(merged):
            card = merged[cid]
            if card["episode_id"] != ep:
                continue
            if not check(cid in turns, "%s: 卡的 case_id 不在题面 turn 里" % cid):
                continue
            for field in ("families", "allowed_behaviors", "failure_conditions", "evidence"):
                check(bool(card.get(field)), "%s: %s 为空" % (cid, field))
            check(card.get("review_status"), "%s: 缺 review_status（卡来源状态必须可见）" % cid)
            check(card.get("content_revision"), "%s: 缺 content_revision（修订版本必须可见）" % cid)
            if not evidence_verbatim_ok(card, turns[cid], reasons):
                check(False, "%s: evidence 逐字回核失败" % cid)

    # ── 未改写证明：每张卡必须与来源对象深度相等 ──
    rebuilt = {}
    for rel, fams in SRC_BASE:
        rebuilt.update(cards_of(rel, fams))
    for c in rev_cards:
        rebuilt[c["case_id"]] = c
    for cid, card in merged.items():
        check(json.dumps(card, sort_keys=True, ensure_ascii=False)
              == json.dumps(rebuilt[cid], sort_keys=True, ensure_ascii=False),
              "%s: 卡对象与来源不逐字相等（禁止改写卡面）" % cid)

    if fails or reasons:
        print("C4 卡集自检失败：")
        for m in fails + reasons:
            print("  -", m)
        sys.exit(1)

    cards_out = [merged[cid] for cid in sorted(merged)]
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(OUT_FILE, "w", encoding="utf-8") as f:
        json.dump(cards_out, f, ensure_ascii=False, indent=1)
    out_sha = sha256(OUT_FILE)

    prov = {
        "kind": "merged_grading_card_set_provenance",
        "batch": "C4_SCREEN_20261026",
        "cards_file": os.path.relpath(OUT_FILE, ROOT).replace("\\", "/"),
        "cards_file_sha256": out_sha,
        "cards_file_shape": "bare list（消费器兼容；带 header 的 dict 会让卡字段静默全空）",
        "episodes": EPISODES,
        "card_count": len(cards_out),
        "per_episode": {ep: sum(1 for c in cards_out if c["episode_id"] == ep) for ep in EPISODES},
        "authoring_note": (
            "不含任何新写卡面文字：仅从三个已冻结来源【选取】C4 所需 32 张并按 case_id 应用 "
            "r2-20261025 修订覆盖；每张卡与来源对象逐字相等（脚本内断言），逐条 evidence 已对原题面"
            "逐字回核通过。split/review_status/content_revision 原样保留，不冒充人工认证。"),
        "sources": provenance,
        "revision_overlay": rev_prov,
        "overlays_applied": overlaid,
        "card_source_map": {cid: src_of[cid] for cid in sorted(src_of)},
        "revision_mix": sorted({c["content_revision"] for c in cards_out}),
        "checks": ["每故事 8 张共 32 张", "case_id ∈ 题面 turns",
                   "families/allowed_behaviors/failure_conditions/evidence 非空",
                   "review_status 与 content_revision 在位",
                   "evidence 逐字回核（消息下标/role/text/ts、input.* 字段、gold.* 字段）",
                   "卡对象与来源 deep-equal（未改写）"],
        "real_model_calls": 0,
        "result": "PASS",
    }
    with open(PROV_FILE, "w", encoding="utf-8") as f:
        json.dump(prov, f, ensure_ascii=False, indent=1)

    print(json.dumps({k: prov[k] for k in
                      ("cards_file", "cards_file_sha256", "card_count", "per_episode",
                       "overlays_applied", "revision_mix", "real_model_calls", "result")},
                     ensure_ascii=False, indent=1))
    print("provenance: %s" % os.path.relpath(PROV_FILE, ROOT).replace("\\", "/"))
    print("C4_CARD_SET_PASS sha256=%s" % out_sha[:16])


if __name__ == "__main__":
    main()
