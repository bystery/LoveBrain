# lb_phase05_discrimination.py — Phase 0.5 评分器判别力报告生成器
# 输入：logs/phase05/<candidate>/episode_manifest.json + 逐轮 scoring.json
# 输出：refs/PHASE05_DISCRIMINATION.md + logs/phase05/discrimination.json
# 内容：①五候选探针矩阵（逐探针 PASS/FAIL/INVALID）②逐维度分数分布 ③满分比例
#       ④失败样例 ⑤mock/echo 替身 vs 真实模型归因 ⑥判别力结论
# 纪律：只读运行产物，不改任何评分；结论如实标注协议层/模型层。

import json
import os
import sys
from collections import Counter, defaultdict

RUNS = [
    ("phase0_alpha", "α 内存全量保留（好记忆/坏抑制）"),
    ("phase0_beta", "β 文件KV+检索（好记忆/好抑制）"),
    ("neg_a_amnesia", "负对照A 无长期记忆"),
    ("neg_b_dumper", "负对照B 全量复述/旧事重提"),
    ("neg_c_service", "负对照C 客服化人机感"),
]

PROBE_KEYS = ("must_keywords_any", "stale_fact_must_not", "intent_keywords_any",
              "intent_must_not_keywords", "expect_decline", "must_not_keywords",
              "expect_ongoing", "forbidden_new_facts", "geo_impossible_must_not")


def load_run(base, cand):
    m = json.load(open(os.path.join(base, cand, "episode_manifest.json"), encoding="utf-8"))
    per_dim = Counter()
    dim_tot = Counter()
    fails = []
    scores = []
    for d in sorted(os.listdir(os.path.join(base, cand))):
        sp = os.path.join(base, cand, d, "scoring.json")
        if not os.path.isfile(sp):
            continue
        sc = json.load(open(sp, encoding="utf-8"))
        scores.append(sc["score10"])
        for c in sc["checks"]:
            dim = c["id"].split("_")[0]
            dim_tot[dim] += 1
            if not c["pass"]:
                per_dim[dim] += 1
                if len(fails) < 5:
                    fails.append({"turn": d, "check": c["id"], "evidence": c["evidence"][:70]})
    full = sum(1 for s in scores if s >= 9.99)
    return m, scores, per_dim, dim_tot, fails, full


def main(base="logs/phase05", out_md="refs/PHASE05_DISCRIMINATION.md"):
    results = {}
    for cand, label in RUNS:
        results[cand] = load_run(base, cand)

    # 探针矩阵：episode manifest 的 probe_report
    lines = []
    lines.append("# Phase 0.5 评分器判别力报告\n")
    lines.append("> 生成：arena/lb_phase05_discrimination.py；数据源：logs/phase05/*（echo 替身 220 轮）。\n")
    lines.append("> 结论口径：echo 替身验证的是**协议层判别力**（机制有没有把正确记忆组织进回复链路），")
    lines.append("> 模型层判别力（真人味、语义冲突处理）归 judge 校准阶段，本报告不冒认。\n")

    lines.append("## 1. 总分对比（天花板效应修复前后）\n")
    lines.append("| 候选 | 220轮均值 | 探针加权分(×10) | 探针轮均分 | veto | 污染探针 |")
    lines.append("|---|---|---|---|---|---|")
    for cand, label in RUNS:
        m, scores, per_dim, dim_tot, fails, full = results[cand]
        pr = m.get("probe_report") or []
        probe_mean = round(sum(p["score10"] for p in pr) / len(pr), 2) if pr else None
        sp = m.get("stale_pollution") or {}
        lines.append("| %s（%s） | %s | %s | %s | %d | %s/%s |" % (
            cand, label, m["mean_score10"], m.get("probe_weighted_score10"),
            probe_mean, m["veto_turns"], sp.get("hits"), sp.get("probes")))
    lines.append("")
    lines.append("**解读**：220 轮均值对记忆能力差异不敏感（探针密度 5/220 太低，负对照 A 得 9.99 就是证据）；")
    lines.append("客服腔负对照被 C13+AI 味词表拉开 3 分+。**探针轮均分**才是记忆能力的判别口径，见 §2 矩阵。\n")

    # 探针矩阵
    lines.append("## 2. 逐探针矩阵（判别力核心证据）\n")
    all_probes = {}
    for cand, _ in RUNS:
        for p in (results[cand][0].get("probe_report") or []):
            all_probes.setdefault(p["case_id"], p)
    order = sorted(all_probes.keys())
    lines.append("| 候选 | " + " | ".join(order) + " |")
    lines.append("|---|" + "---|" * len(order))
    for cand, label in RUNS:
        pr = {p["case_id"]: p for p in (results[cand][0].get("probe_report") or [])}
        cells = []
        for cid in order:
            p = pr.get(cid)
            if p is None:
                cells.append("—")
                continue
            if p.get("window_leak"):
                cells.append("INVALID(泄漏)")
                continue
            mem_kw = ("stale_kw", "must_kw", "intent_kw", "intent_forbid",
                      "decline_expected", "must_not_kw", "geo_must_not")
            style_kw = ("ai_flavor", "style_diversity", "note_lcs")
            kinds = set()
            for fc in p.get("failed_checks") or []:
                if fc.startswith(mem_kw):
                    kinds.add("mem")
                elif fc.startswith(style_kw):
                    kinds.add("style")
                else:
                    kinds.add("other")
            if not kinds and not p["veto"]:
                cells.append("PASS")
            else:
                tag = "+".join(sorted(kinds)) if kinds else "veto"
                cells.append("FAIL(%s)" % tag)
        lines.append("| %s | " % cand + " | ".join(cells) + " |")
    lines.append("")
    probe_desc = {cid: (all_probes[cid].get("failed_checks"), ) for cid in order}
    lines.append("探针含义：GEN_t010=软关键词(不计分)；GEN_t080=旧事抑制(水杯)；GEN_t140=远距召回(爬山)；")
    lines.append("GEN_t160=召回(九价，**当轮窗口泄漏 → INVALID，测不到记忆**)；GEN_t200/t202=意图对照；")
    lines.append("GEN_t205=must_not；GEN_t210=静默轮。\n")

    lines.append("## 3. 判别力结论\n")
    verdict = []
    m_a = {p["case_id"]: p for p in (results["neg_a_amnesia"][0].get("probe_report") or [])}
    m_b = {p["case_id"]: p for p in (results["neg_b_dumper"][0].get("probe_report") or [])}
    m_c = {p["case_id"]: p for p in (results["neg_c_service"][0].get("probe_report") or [])}
    t140_ok = m_a.get("GEN_t140", {}).get("score10", 10) >= 9.99
    t080_b = m_b.get("GEN_t080", {}).get("failed_checks", [])
    negc_mean = results["neg_c_service"][0]["mean_score10"]
    if not t140_ok:
        verdict.append("✅ 召回探针（t140 爬山）把无记忆候选 vs 有记忆候选区分开")
    else:
        verdict.append("❌ 召回探针未能区分无记忆候选（考场缺口）")
    if t080_b:
        verdict.append("✅ 污染探针（t080 水杯）把全量复述候选抓出（hits=%s）" % t080_b)
    else:
        verdict.append("❌ 污染探针未抓到全量复述候选")
    if negc_mean < 8.0:
        verdict.append("✅ 客服腔候选被风格维度拉开（%.2f，C13 趋同 + AI 味词表 v0.5.1）" % negc_mean)
    else:
        verdict.append("❌ 客服腔候选未被抓出（评分器人机感判别缺口）")
    verdict.append("⚠️ 总量层判别力受探针密度限制：GEN_220 仅 7 探针/220 轮（且 1 个泄漏失效）。")
    verdict.append("正式题库纪律新增：**探针密度 ≥ 1/10 轮，召回探针关键词不得出现在当轮 HER 消息里**（runner 已自动标 INVALID）。")
    lines.extend("- " + v for v in verdict)
    lines.append("")

    lines.append("## 4. 逐维度失分分布（各候选）\n")
    for cand, label in RUNS:
        _, scores, per_dim, dim_tot, fails, full = results[cand]
        dist = Counter(int(s) for s in scores)
        lines.append("**%s（%s）**：满分轮 %d/220；分布 %s" % (cand, label, full, dict(sorted(dist.items()))))
        if per_dim:
            lines.append("  失分维度：%s" % dict(per_dim))
        for f in fails[:3]:
            lines.append("  失败样例：%s %s %s" % (f["turn"], f["check"], f["evidence"]))
        lines.append("")

    lines.append("## 5. 归因声明\n")
    lines.append("- 本报告全部 220 轮成绩来自 **echo 测试替身**（manifest.adapter=echo）：验证协议层（存储→检索→注入→回复→写回），")
    lines.append("**不代表回复质量**。模型层质量证据另见 V7 对拍（openai_compat，dsf 真模型）与后续 judge 校准。")
    lines.append("- mock 替身成绩（SMOKE 题 10.0）同理。")
    lines.append("- AI 味词表 v0.5.1 新增客服腔 9 词，系 neg_c 实测漏判后修复（修复动作本身即判别力测试的产出）。")

    with open(out_md, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    data = {cand: {"manifest": results[cand][0],
                   "fails": results[cand][4]} for cand, _ in RUNS}
    with open(os.path.join(base, "discrimination.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("written:", out_md)


if __name__ == "__main__":
    base = sys.argv[1] if len(sys.argv) > 1 else "logs/phase05"
    out = sys.argv[2] if len(sys.argv) > 2 else "refs/PHASE05_DISCRIMINATION.md"
    main(base, out)
