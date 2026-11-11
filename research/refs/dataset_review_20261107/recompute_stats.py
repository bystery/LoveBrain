# -*- coding: utf-8 -*-
"""
recompute_stats.py — 数据集独立审查材料 A 部分：只读统计复算脚本。

用途：对 benchmark/stories_v2 与 refs 下的账本/普查文件做零模型调用、确定性的主数字复算，
供《A_COMPOSITION_AND_PROVENANCE.md》核对。本脚本：
  - 只读：不写、不改、不删任何仓库文件；
  - 纯标准库；
  - 不读取 benchmark/b_line/ 下任何 episode 内容；仅读取其 manifest 元数据文件
    benchmark/b_line/B_LINE_MANIFEST.json（封存纪律允许的元数据）。

运行（仓库根目录 D:\\lovebrain-arena 下）：
    python -I refs/dataset_review_20261107/recompute_stats.py
可选：python -I refs/dataset_review_20261107/recompute_stats.py --json

仓库根定位：本脚本位于 <root>/refs/dataset_review_20261107/，故 root = parents[2]。
"""

import argparse
import json
import os
import sys
from collections import Counter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SV2 = os.path.join(ROOT, "benchmark", "stories_v2")
GS = os.path.join(SV2, "GOLD_SIDECAR")
GHP = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030")

# 库 208 集 = stage2+stage3+stage4+wave1+wave2+wave3+wave3_ext（stage1/stageD/wave1_ext 在 sidecar 但不入库）
LIB_WAVES = ["stage2", "stage3", "stage4", "wave1", "wave2", "wave3", "wave3_ext"]
ALL_WAVES = LIB_WAVES + ["stage1", "stageD", "wave1_ext"]
# sidecar 取消族 status 口径（tools/build_gold_sidecar.py enrichment_policy 明文）
CANCEL_FAMILY = {"cancelled", "canceled", "superseded", "retracted", "voided",
                 "revoked", "invalidated", "negated", "erroneous"}
CANON_SUBJECTS = ["HER", "SHARED", "THIRD", "ME", "PUBLIC"]


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def list_episodes(wave):
    p = os.path.join(SV2, wave)
    if not os.path.isdir(p):
        return []
    return sorted(d for d in os.listdir(p)
                  if os.path.exists(os.path.join(p, d, "episode.json")))


def sec_library():
    """① 真实规模：逐波目录普查 + episode.json 轮数实测。"""
    out = {"library": {}, "non_library": {}}
    lib_eps = lib_turns = 0
    for w in LIB_WAVES:
        eps = list_episodes(w)
        turns = 0
        tcs = Counter()
        for e in eps:
            d = load_json(os.path.join(SV2, w, e, "episode.json"))
            n = len(d.get("turns", []))
            turns += n
            tcs[n] += 1
        out["library"][w] = {"episodes": len(eps), "turns": turns,
                             "turn_count_histogram": dict(sorted(tcs.items()))}
        lib_eps += len(eps)
        lib_turns += turns
    for w in ["stage1", "stageD", "wave1_ext"]:
        eps = list_episodes(w)
        turns = 0
        for e in eps:
            d = load_json(os.path.join(SV2, w, e, "episode.json"))
            turns += len(d.get("turns", []))
        out["non_library"][w] = {"episodes": len(eps), "turns": turns}
    out["totals"] = {
        "library_episodes": lib_eps,
        "library_turns": lib_turns,
        "sidecar_extra_episodes": sum(v["episodes"] for v in out["non_library"].values()),
        "sidecar_extra_turns": sum(v["turns"] for v in out["non_library"].values()),
        "sidecar_episodes_expected": lib_eps + sum(v["episodes"] for v in out["non_library"].values()),
        "sidecar_turns_expected": lib_turns + sum(v["turns"] for v in out["non_library"].values()),
    }
    return out


def sec_gold_index():
    """② Gold sidecar 指标（直接读 GOLD_INDEX.json 汇总）。"""
    d = load_json(os.path.join(GS, "GOLD_INDEX.json"))
    t = d.get("totals", {})
    return {
        "schema_version": d.get("schema_version"),
        "generated": d.get("generated"),
        "totals": {k: t.get(k) for k in
                   ["episodes", "turns", "with_assertions", "empty_gold", "no_card",
                    "atoms", "unknowns", "intent_contracts", "must_not_affirm_atoms",
                    "atoms_with_affirm_markers", "source_conflicts"]},
        "marker_qc": d.get("marker_qc"),
        "qc_sample": {k: v for k, v in (d.get("qc_sample") or {}).items()
                      if k in ("seed", "n", "strict_all_pass", "lenient_all_pass")},
        "per_wave": d.get("per_wave"),
    }


def sec_atoms():
    """③ 原子级字段分布：从 GOLD_*.json（非 INDEX）逐轮重算，分母=全部 sidecar 轮。"""
    status = Counter()
    subject = Counter()
    neg = req = vt = atoms = 0
    rels = set()
    fields = Counter()
    cf = ng = both = 0
    turns = 0
    card_no_assert = 0
    ic = ik = groups = mayd = 0
    gold_files = sorted(f for f in os.listdir(GS)
                        if f.startswith("GOLD_") and f.endswith(".json")
                        and f not in ("GOLD_INDEX.json", "GOLD_QC_SAMPLE.json"))
    for f in gold_files:
        d = load_json(os.path.join(GS, f))
        for t in d.get("turns", []):
            turns += 1
            if not (t.get("assertions") or []) and not t.get("gold_empty"):
                card_no_assert += 1
            c = t.get("intent_contract") or {}
            if c.get("intent"):
                ic += 1
                kw = c.get("keywords_any") or []
                if kw:
                    ik += 1
                    groups += len(kw)
            if c.get("may_decline"):
                mayd += 1
            for a in (t.get("assertions") or []):
                atoms += 1
                status[a.get("status")] += 1
                subject[a.get("subject")] += 1
                for k in a:
                    fields[k] += 1
                if a.get("negation"):
                    neg += 1
                    ng += 1
                if a.get("required_this_turn"):
                    req += 1
                if a.get("valid_time"):
                    vt += 1
                rels.add(a.get("relation"))
                if a.get("status") in CANCEL_FAMILY:
                    cf += 1
                    if a.get("negation"):
                        both += 1
    canon = {s: subject.get(s, 0) for s in CANON_SUBJECTS}
    return {
        "files_read": gold_files,
        "turns_scanned": turns,
        "card_turns_without_assertions": card_no_assert,
        "atoms": atoms,
        "status_distribution": dict(status.most_common()),
        "cancel_family_atoms": cf,
        "negation_true": neg,
        "overlap_both": both,
        "must_not_affirm_union_recomputed": cf + ng - both,
        "required_this_turn_true": req,
        "valid_time_non_empty": vt,
        "valid_time_coverage": "%d/%d" % (vt, atoms),
        "distinct_relation_strings": len(rels),
        "subject_canonical_five": canon,
        "subject_canonical_sum": sum(canon.values()),
        "subject_free_text_count": atoms - sum(canon.values()),
        "atom_fields_seen": dict(fields),
        "intent_contracts_intent_non_null": ic,
        "intent_turns_with_keywords": ik,
        "intent_keywords_groups": groups,
        "may_decline_true_turns": mayd,
    }


def sec_dims():
    """④ 九维卡面覆盖：208 库篇 reference.json 的 applicable_dimensions，分母=库轮数。"""
    dims = Counter()
    turns = 0
    eps = 0
    for w in LIB_WAVES:
        for e in list_episodes(w):
            eps += 1
            r = load_json(os.path.join(SV2, w, e, "reference.json"))
            for cid, t in r.get("turns", {}).items():
                turns += 1
                for dim in (t.get("applicable_dimensions") or []):
                    dims[dim] += 1
    label_sum = sum(dims.values())
    return {
        "episodes_scanned": eps,
        "reference_turns_scanned": turns,
        "per_dim_counts": dict(dims.most_common()),
        "per_dim_pct_of_turns": {k: round(v / turns * 100, 1) for k, v in dims.most_common()},
        "label_instances_total": label_sum,
        "avg_labels_per_turn": round(label_sum / turns, 2) if turns else None,
        "overlap_note": "一卡可含多维，标签实例总数>轮数；各维百分比分母均为轮数（多维并存故各维之和>100%）",
        "dim_label_map": os.path.join("benchmark", "stories_v2", "wave3", "DIM_LABEL_MAP_20261104.json"),
    }


def sec_domains():
    """⑤ 域分布：184 标准篇（dedupe 普查）+ 显式 domain 字段的 manifest 覆盖（152 篇）。"""
    dd = load_json(os.path.join(GHP, "W03-06_SKELETON_DEDUPE_20261031.json"))
    dc = dd.get("domain_coverage", {})
    dom_counts = {k: len(v) for k, v in dc.items()}
    n_manifest = 0
    manifest_dom = Counter()
    manifest_files = {
        "wave1": os.path.join(SV2, "wave1", "STAGE1_MANIFEST.json"),
        "wave2": os.path.join(SV2, "wave2", "STAGE1_MANIFEST.json"),
        "wave3": os.path.join(SV2, "wave3", "STAGE1_MANIFEST.json"),
    }
    for w, p in manifest_files.items():
        m = load_json(p)
        for s in m.get("stories", []):
            n_manifest += 1
            manifest_dom[s.get("domain")] += 1
    return {
        "dedupe_total_standard": dd.get("total"),
        "dedupe_near_dup_clusters": dd.get("near_dup_clusters"),
        "domain_coverage_184": dom_counts,
        "domain_coverage_sum": sum(dom_counts.values()),
        "wave_manifests_with_explicit_domain": n_manifest,
        "wave_manifest_domain_counts": dict(manifest_dom),
        "sources": [
            os.path.join("refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                         "W03-06_SKELETON_DEDUPE_20261031.json"),
            "benchmark/stories_v2/{wave1,wave2,wave3}/STAGE1_MANIFEST.json",
        ],
        "note": "stage2/stage3/stage4 的 32 篇无显式 domain 字段，由 tools/story_skeleton_dedupe.py 按 "
                "W03-06_DOMAIN_NORMALIZATION_20261102.json 映射+关键词规则派生进 184 普查口径",
    }


def sec_custody():
    """⑥ 保管账：注册/生命周期/事件/补录。账本在 refs 下，允许读取。"""
    led = load_json(os.path.join(GHP, "STORY_CUSTODY_LEDGER.json"))
    stories = led.get("stories", {})
    lc = Counter(v.get("lifecycle") for v in stories.values())
    combo = Counter((v.get("stage"), v.get("lifecycle")) for v in stories.values())
    events = led.get("events", [])
    special = Counter(e.get("event") for e in events if e.get("event"))
    backfill = [e for e in events if "backfill" in json.dumps(e, ensure_ascii=False)]
    return {
        "registered_stories": len(stories),
        "lifecycle": dict(lc),
        "stage_x_lifecycle": {"%s/%s" % k: v for k, v in sorted(combo.items(), key=str)},
        "events_total": len(events),
        "named_events": dict(special),
        "backfill_exposure_events": len(backfill),
        "backfill_distinct_stories": len(set(e.get("story") for e in backfill)),
        "stories_with_consumed_by": sum(1 for v in stories.values() if v.get("consumed_by")),
        "source": os.path.join("refs", "experiment_protocol_20261023",
                               "global_harness_program_20261030", "STORY_CUSTODY_LEDGER.json"),
    }


def sec_exposure():
    """⑦ exposure.json 普查：author_type / dev_exposed / independent_review / real_user_material / frozen_at。"""
    at = Counter()
    de = Counter()
    ie = Counter()
    ru = Counter()
    frozen = 0
    n = 0
    for w in ALL_WAVES:
        for e in list_episodes(w):
            p = os.path.join(SV2, w, e, "exposure.json")
            if not os.path.exists(p):
                continue
            n += 1
            d = load_json(p)
            at[d.get("author_type")] += 1
            de[bool(d.get("dev_exposed"))] += 1
            ie[bool(d.get("independent_review"))] += 1
            ru[bool(d.get("real_user_material"))] += 1
            if d.get("frozen_at"):
                frozen += 1
    return {
        "exposure_files": n,
        "author_type": {str(k): v for k, v in at.most_common()},
        "dev_exposed": {"true": de[True], "false": de[False]},
        "independent_review": {"true": ie[True], "false": ie[False]},
        "real_user_material": {"true": ru[True], "false": ru[False]},
        "frozen_at_non_null": frozen,
    }


def sec_b_line():
    """⑧ B 线：仅 manifest 元数据；不进入该目录任何 episode。"""
    mf = os.path.join(ROOT, "benchmark", "b_line", "B_LINE_MANIFEST.json")
    if not os.path.exists(mf):
        return {"error": "manifest missing", "episodes_read": 0}
    d = load_json(mf)
    comp = d.get("composition", {})
    return {
        "version": d.get("version"),
        "date": d.get("date"),
        "count": d.get("count"),
        "composition_standard": len(comp.get("standard_12turn", [])),
        "composition_long_drama": len(comp.get("long_drama", [])),
        "composition_counterfactual": len(comp.get("counterfactual", [])),
        "integrity": d.get("integrity"),
        "supersedes": d.get("supersedes"),
        "episodes_read": 0,
        "note": "只读 manifest 元数据；episode 内容未读取（封存盲测集）",
    }


def main():
    # Windows 控制台默认代码页可能是 GBK，强制 UTF-8 输出避免管道/重定向乱码
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description="LoveBrain 数据集审查只读统计复算")
    ap.add_argument("--json", action="store_true", help="以 JSON 输出全部统计")
    args = ap.parse_args()

    result = {
        "root": ROOT,
        "library": sec_library(),
        "gold_index": sec_gold_index(),
        "atoms": sec_atoms(),
        "nine_dim_coverage": sec_dims(),
        "domains": sec_domains(),
        "custody": sec_custody(),
        "exposure": sec_exposure(),
        "b_line_manifest_only": sec_b_line(),
    }

    # 恒等式核对
    lib = result["library"]["totals"]
    gi = result["gold_index"]["totals"]
    identity = {
        "I1_sidecar_episodes=208+8+3+2=221":
            (lib["library_episodes"] + lib["sidecar_extra_episodes"] == 221)
            and (gi["episodes"] == 221),
        "I2_sidecar_turns=2944+64+22+72=3102":
            (lib["library_turns"] + lib["sidecar_extra_turns"] == 3102)
            and (gi["turns"] == 3102),
        "I3_with_assertions+empty_gold+card_without_assertions=turns":
            (gi["with_assertions"] + gi["empty_gold"]
             + result["atoms"]["card_turns_without_assertions"] == gi["turns"]),
        "I4_must_not_affirm=cancel_family∪negation":
            result["atoms"]["must_not_affirm_union_recomputed"] == gi["must_not_affirm_atoms"],
        "I5_atoms_index_vs_recount":
            gi["atoms"] == result["atoms"]["atoms"],
        "I6_domain_184_sum": result["domains"]["domain_coverage_sum"] == 184,
        "I7_wave_manifest_domain_152": result["domains"]["wave_manifests_with_explicit_domain"] == 152,
        "I8_custody_210=S161+E49":
            result["custody"]["registered_stories"] == 210
            and result["custody"]["lifecycle"].get("SEALED") == 161
            and result["custody"]["lifecycle"].get("EXPOSED") == 49,
    }
    result["identity_checks"] = identity

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=1))
        return

    # ---- 文本摘要 ----
    out = sys.stdout
    def w(s=""):
        out.write(s + "\n")

    w("=== recompute_stats 只读复算（root=%s）===" % ROOT)
    w()
    w("[1] 库规模（目录实测 episode.json）")
    for wv, v in result["library"]["library"].items():
        w("  %-10s episodes=%-3d turns=%-5d 轮数直方图=%s" % (wv, v["episodes"], v["turns"], v["turn_count_histogram"]))
    w("  库合计 episodes=%d turns=%d" % (lib["library_episodes"], lib["library_turns"]))
    for wv, v in result["library"]["non_library"].items():
        w("  [sidecar-only] %-10s episodes=%d turns=%d" % (wv, v["episodes"], v["turns"]))
    w()
    w("[2] GOLD_INDEX.json（%s generated=%s）" % (result["gold_index"]["schema_version"], result["gold_index"]["generated"]))
    for k, v in result["gold_index"]["totals"].items():
        w("  %s=%s" % (k, v))
    w("  marker_qc=%s" % result["gold_index"]["marker_qc"])
    w("  qc_sample=%s" % result["gold_index"]["qc_sample"])
    w("  per_wave:")
    for wv, v in result["gold_index"]["per_wave"].items():
        w("    %-10s ep=%-3d turns=%-5d atoms=%-5d with_assert=%-4d empty_gold=%d" %
          (wv, v["episodes"], v["turns"], v["atoms"], v["with_assertions"], v["empty_gold"]))
    w()
    a = result["atoms"]
    w("[3] 原子级复算（分母=%d 轮 / %d 原子）" % (a["turns_scanned"], a["atoms"]))
    w("  无断言但有卡轮=%d（with_assertions+empty_gold+该类=总轮数）" % a["card_turns_without_assertions"])
    w("  status 分布: %s" % a["status_distribution"])
    w("  取消族=%d  negation=true=%d  交集=%d  并集(must_not_affirm)=%d" %
      (a["cancel_family_atoms"], a["negation_true"], a["overlap_both"], a["must_not_affirm_union_recomputed"]))
    w("  required_this_turn=true=%d  valid_time 非空=%s  relation 去重=%d 种"
      % (a["required_this_turn_true"], a["valid_time_coverage"], a["distinct_relation_strings"]))
    w("  subject 规范五类=%s 合计=%d 自由文本其余=%d" %
      (a["subject_canonical_five"], a["subject_canonical_sum"], a["subject_free_text_count"]))
    w("  intent_contract(intent 非空)=%d  有 keywords 轮=%d  关键词组=%d  may_decline=true 轮=%d" %
      (a["intent_contracts_intent_non_null"], a["intent_turns_with_keywords"],
       a["intent_keywords_groups"], a["may_decline_true_turns"]))
    w()
    d9 = result["nine_dim_coverage"]
    w("[4] 九维卡面覆盖（分母=%d 库轮 / %d 篇 reference.json）" % (d9["reference_turns_scanned"], d9["episodes_scanned"]))
    for k, v in d9["per_dim_counts"].items():
        w("  %-16s %5d (%.1f%% 轮)" % (k, v, d9["per_dim_pct_of_turns"][k]))
    w("  标签实例合计=%d（一卡多维，故各维之和>100%%）；映射文件=%s" % (d9["label_instances_total"], d9["dim_label_map"]))
    w()
    dm = result["domains"]
    w("[5] 域分布（184 标准篇，出处 W03-06_SKELETON_DEDUPE_20261031.json）")
    for k, v in dm["domain_coverage_184"].items():
        w("  %-6s %d" % (k, v))
    w("  合计=%d  near_dup_clusters=%s" % (dm["domain_coverage_sum"], dm["dedupe_near_dup_clusters"]))
    w("  wave manifest 显式 domain 篇数=%d（wave1/2/3 主 manifest）" % dm["wave_manifests_with_explicit_domain"])
    w()
    c = result["custody"]
    w("[6] 保管账（STORY_CUSTODY_LEDGER.json）")
    w("  registered=%d  %s" % (c["registered_stories"], c["lifecycle"]))
    w("  分波: %s" % c["stage_x_lifecycle"])
    w("  events=%d  backfill exposure 事件=%d（涉及 %d 篇）  有 consumed_by 的篇=%d" %
      (c["events_total"], c["backfill_exposure_events"], c["backfill_distinct_stories"], c["stories_with_consumed_by"]))
    w("  具名事件: %s" % c["named_events"])
    w()
    e = result["exposure"]
    w("[7] exposure.json 普查（n=%d）" % e["exposure_files"])
    w("  author_type: %s" % e["author_type"])
    w("  dev_exposed true/false = %d/%d  independent_review false=%d  real_user_material false=%d  frozen_at 非空=%d" %
      (e["dev_exposed"]["true"], e["dev_exposed"]["false"], e["independent_review"]["false"],
       e["real_user_material"]["false"], e["frozen_at_non_null"]))
    w()
    b = result["b_line_manifest_only"]
    w("[8] B 线（仅 manifest 元数据，episode 零读取）")
    w("  version=%s date=%s count=%s（标准%d+长剧%d+CF%d） integrity=%s" %
      (b["version"], b["date"], b["count"], b["composition_standard"],
       b["composition_long_drama"], b["composition_counterfactual"], b["integrity"]))
    w("  supersedes=%s" % b["supersedes"])
    w()
    w("[9] 恒等式核对")
    for k, v in identity.items():
        w("  %s : %s" % (k, "PASS" if v else "FAIL"))


if __name__ == "__main__":
    main()
