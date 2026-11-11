# -*- coding: utf-8 -*-
"""tools/knowledge_s3_other_batches.py — 其他已跑批的「检索/实际注入」段（离线，零调用）。

为什么单独做：C4 panel 只覆盖 4 个故事 4 臂；v3 / v4r2 / v5 首批 / P 族新故事
都有已付费的出站 prompt 归档，可以在**不改任何东西**的前提下补出 S3（该用的信息
有没有进请求）与 T08 可恢复的上下文侧数字。

边界（必须随表披露）：
  - 只测 S3 与"T08 仍在上下文"；**S1 存储侧/S2 保真不做**（这些批多数臂没有存储快照）；
  - 各批故事不同、配置身份不同（v3/r2/v5 首批为非流式），**不许跨批拼升级曲线**；
  - 原子来自各自卡集，卡是开发者编写、review_status 仍是 pending。
"""
import json
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import knowledge_stage_scorecard as K                              # noqa: E402

ROOT = K.ROOT
OUT_JSON = os.path.join(ROOT, "evaluation", "KNOWLEDGE_S3_OTHER_BATCHES_V1.json")
OUT_MD = os.path.join(ROOT, "evaluation", "KNOWLEDGE_S3_OTHER_BATCHES_V1.md")

BATCHES = {
    "v3_deletion_live_20261024": {"parent": "reference_product_v1",
                                  "candidate": "v3_system_slim"},
    "r2_ctx_live_20261024": {"parent": "reference_product_v1",
                             "candidate": "v4_intent_context_r2"},
    "v5_ctx_live_20261025": {"parent": "reference_product_v1",
                             "candidate": "v5_purpose_aligned"},
    "newstory96_live_20261026": {"parent": "reference_product_v1",
                                 "candidate": "v5_purpose_aligned"},
}
CARD_FILES = [
    "benchmark/dev_expansion_v01/03_GRADING_CARDS_E004_E006.json",
    "benchmark/dev_expansion_v01/04_GRADING_CARDS_E007_E012.json",
    "benchmark/positive_capability_20261025/02_GRADING_CARDS_P1.json",
]


def load_all_atoms():
    atoms, skipped, per_file = [], 0, {}
    for rel in CARD_FILES:
        p = os.path.join(ROOT, rel)
        if not os.path.isfile(p):
            per_file[rel] = "MISSING"
            continue
        cards = json.load(open(p, encoding="utf-8"))
        n = 0
        for card in cards:
            for ev in card.get("evidence") or []:
                if "message_index_zero_based" not in ev or not ev.get("role"):
                    skipped += 1
                    continue
                cid = ev.get("case_id") or card["case_id"]
                ep, tn = cid.split("_T")
                atoms.append({"atom_id": "%s#m%d" % (cid, ev["message_index_zero_based"]),
                              "episode": ep, "turn": int(tn), "role": ev["role"],
                              "text": ev["text"]})
                n += 1
        per_file[rel] = n
    # 跨文件去重（同一 case 可能被多个卡文件覆盖）
    seen, uniq = set(), []
    for a in atoms:
        if a["atom_id"] in seen:
            continue
        seen.add(a["atom_id"])
        uniq.append(a)
    return uniq, skipped, per_file


def main():
    atoms, skipped, per_file = load_all_atoms()
    rows, detail = {}, []
    for batch, roles in BATCHES.items():
        base = os.path.join(ROOT, "logs", batch)
        if not os.path.isdir(base):
            continue
        for role, arm in roles.items():
            eps = sorted({os.path.basename(d).split("_", 1)[1]
                          for d in os.listdir(base)
                          if d.startswith(role + "_") and os.path.isdir(os.path.join(base, d))})
            if not eps:
                continue
            a_scope = [a for a in atoms if a["episode"] in eps and a["turn"] <= 7]
            pairs = present = struct = rawonly = back = backhit = 0
            t08 = 0
            for ep in eps:
                prompts = {t: K.load_prompt(batch, role, ep, t) for t in range(1, 9)}
                if sum(1 for v in prompts.values() if v) != 8:
                    detail.append({"batch": batch, "arm": arm, "episode": ep,
                                   "error": "prompt 不齐，跳过"})
                    continue
                windows = {t: K.current_window(prompts[t]["raw_text"]) for t in prompts}
                for a in [x for x in a_scope if x["episode"] == ep]:
                    an = K.norm(a["text"])
                    last_hit = False
                    for k in range(a["turn"] + 1, 9):
                        hits = K.best_entry_hit(an, K.split_sections(prompts[k]["raw_text"]))
                        cats = sorted(c for c in hits if c in ("raw", "structured", "seed",
                                                               "other"))
                        pairs += 1
                        present += int(bool(cats))
                        struct += int("structured" in cats)
                        rawonly += int(cats == ["raw"])
                        rel, _why = K.is_backref(an, windows.get(k, ""))
                        if rel:
                            back += 1
                            backhit += int(bool(cats))
                        if k == 8:
                            last_hit = bool(cats)
                    t08 += int(last_hit)
            rows["%s|%s" % (batch, arm)] = {
                "batch": batch, "arm": arm, "episodes": eps,
                "atoms_scope": len(a_scope), "pairs": pairs,
                "S3_present": "%d/%d" % (present, pairs),
                "S3_rate": round(100.0 * present / pairs, 1) if pairs else None,
                "S3_structured": "%d/%d" % (struct, pairs),
                "S3_structured_rate": round(100.0 * struct / pairs, 1) if pairs else None,
                "S3_raw_only_rate": round(100.0 * rawonly / pairs, 1) if pairs else None,
                "S3_backref": "%d/%d" % (backhit, back),
                "S3_backref_rate": round(100.0 * backhit / back, 1) if back else None,
                "S1_T08_context": "%d/%d" % (t08, len(a_scope)),
                "S1_T08_rate": round(100.0 * t08 / len(a_scope), 1) if a_scope else None,
            }
    errs = []
    if not atoms:
        errs.append("原子集为空")
    if not rows:
        errs.append("没有任何批被解析出——检查批名/目录")
    for k, r in rows.items():
        if r["pairs"] == 0:
            errs.append("%s 没有可测对子" % k)
    if errs:
        for e in errs:
            print("SELFCHK FAIL:", e, file=sys.stderr)
        return 2

    doc = {"kind": "knowledge_s3_other_batches", "date": "2026-10-26",
           "scope": "只测 S3 实际注入与 T08 上下文在场；S1 存储侧/S2 保真不做",
           "cards_atoms": per_file, "atoms_total": len(atoms),
           "atoms_excluded": skipped, "rows": rows,
           "warnings": ["跨批故事/配置身份不同，不合并求均，不拼升级曲线",
                        "原子来自开发者卡集，review_status=pending",
                        "fuzzy 命中是机器判定，需人工复核后才能引用"],
           "real_model_calls": 0}
    json.dump(doc, open(OUT_JSON, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    L = ["# 其他已跑批的知识「检索/实际注入」段 V1（离线，零调用）", "",
         "- 原子：%s（合计 %d 条；排除 %d 条 note/intent）"
         % ("；".join("%s=%s" % (os.path.basename(k), v) for k, v in per_file.items()),
            len(atoms), skipped),
         "- 只测 S3 与 T08 在场；**S1 存储侧/S2 保真不做**（这些批多数臂无存储快照）。",
         "- 各批故事与配置身份不同：**不跨批合并、不拼升级曲线**。", "",
         "| 批 | 臂 | 故事 | S3 可用在场 | S3 结构化 | S3 仅原始 | S3 回指位点 | T08 仍在上下文 |",
         "|---|---|---|---|---|---|---|---|"]
    for k in sorted(rows):
        r = rows[k]
        L.append("| %s | %s | %s | %s（%s%%） | %s | %s%% | %s | %s |" % (
            r["batch"], r["arm"], "/".join(r["episodes"]), r["S3_present"], r["S3_rate"],
            r["S3_structured"], r["S3_raw_only_rate"], r["S3_backref"], r["S1_T08_context"]))
    L += ["", "## 读法与边界", "",
          "- 「S3 结构化」= 命中落在 plan/scene/topic/事实记录等压缩段；「仅原始」= 只在原始转写里。",
          "- 高的「仅原始」说明该臂靠整段历史重投维持在场，不等于压缩记忆能力。",
          "- 「T08 仍在上下文」的分母是该臂在该批故事里的原子数（≤7 轮原子），不是全部原子。",
          "- 这些数字只回答「信息有没有进请求」，不回答「回复有没有用对」——后者仍 NR。"]
    open(OUT_MD, "w", encoding="utf-8").write("\n".join(L) + "\n")

    print(json.dumps(rows, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
