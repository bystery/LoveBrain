# -*- coding: utf-8 -*-
r"""tools/build_evidence_index.py — 截止日主轨的可复核实物索引（零调用，只读已落盘证据）。

§E 要求交付"原始答卷和账本索引 + 逐维/知识/正向能力表 + 失败与分歧 + 资源附表 + 判决"。
本工具把散在 logs/ 与 refs/ 里的东西**现算**成一张索引：
  1) 每个 AUTH-P08-* 账本：逐物理请求事件计数（reserve/start/complete/attempt_failed）、
     实际计费 attempt 数、是否有传输失败；
  2) 每个批次：plan/batch_id、两臂 harness 身份 sha、卡文件 sha、输出目录、答卷份数；
  3) 尺子与执行器的哈希漂移表（同一文件在 S1/S2/S2R/S3/D1 各计划里的登记值 + 现场重算值），
     哪些相同、哪些漂移，一目了然——漂移不是隐瞒项，是披露项；
  4) 各批 SCORECARD 的五列交付、九维均值、配对胜负、资源与严重错误计数。
所有数字都来自文件，不手填；输出 JSON 供人核，Markdown 供读。
"""
import argparse
import collections
import glob
import hashlib
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BATCH_DIR = os.path.join("refs", "experiment_protocol_20261023", "s1_action_live_20261028")


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def load(rel):
    p = os.path.join(ROOT, rel)
    return json.load(io.open(p, encoding="utf-8")) if os.path.isfile(p) else None


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--stages", default="S1,S2,S2R,S3,D1,R3A,R3B")
    ap.add_argument("--live-dirs", default="S1:logs/p08_s1_live_20261028,"
                    "S2:logs/p08_s2_live_20261028,S2R:logs/p08_s2r_live_20261028,"
                    "S3:logs/p08_s3_live_20261028,D1:logs/p08_d1_live_20261028,"
                    "R3A:logs/p08_r3a_live_20261028,R3B:logs/p08_r3b_live_20261028")
    ap.add_argument("--out-json", default=os.path.join(BATCH_DIR, "EVIDENCE_INDEX.json"))
    ap.add_argument("--out-md", default=os.path.join(BATCH_DIR, "EVIDENCE_INDEX.md"))
    a = ap.parse_args(argv)

    stages = [s.strip() for s in a.stages.split(",") if s.strip()]
    live = dict(x.split(":", 1) for x in a.live_dirs.split(",") if ":" in x)
    plan_dir = {"S1": "S1_ACTION", "S2": "S2_ANCHORS", "S2R": "S2R_RETEST",
                "S3": "S3_ATTRIB", "D1": "D1_GENERAL",
                "R3A": "R3A_RETEST", "R3B": "R3B_CONTROL"}

    out = {"kind": "evidence_index_v1", "generated_by": os.path.basename(__file__),
           "batches": [], "ruler_drift": [], "ledgers": []}

    # ── 账本：逐物理请求计数 ──
    grand_attempts = 0
    for st in stages:
        led = glob.glob(os.path.join(ROOT, "logs", "auth_budget", "AUTH-P08-%s-*.jsonl" % st))
        for lp in sorted(led):
            cnt = collections.Counter()
            ids = set()
            for line in io.open(lp, encoding="utf-8"):
                line = line.strip()
                if not line:
                    continue
                try:
                    e = json.loads(line)
                except Exception:                                   # noqa: BLE001
                    cnt["ledger_unparseable_line"] += 1
                    continue
                cnt[e.get("event") or e.get("kind") or "unknown"] += 1
                if e.get("attempt_id"):
                    ids.add(e["attempt_id"])
            grand_attempts += len(ids)
            out["ledgers"].append({
                "stage": st, "authorization_id": os.path.basename(lp).replace(".jsonl", ""),
                "distinct_attempt_ids": len(ids), "events": dict(cnt),
            })

    # ── 批次：身份/卡/答卷数/尺子哈希 ──
    for st in stages:
        plan_rel = "%s/%s/INTERLEAVE_PLAN.json" % (BATCH_DIR, plan_dir.get(st, ""))
        plan = load(plan_rel)
        sc_rel = "%s/%s_SCORECARD.json" % (BATCH_DIR, st)
        sc = load(sc_rel)
        live_dir = live.get(st)
        turns = {}
        if live_dir:
            for arm in ("parent", "candidate"):
                ld = os.path.join(ROOT, live_dir, "_reports", "LAYERED_SCORE_%s.json" % arm)
                if os.path.isfile(ld):
                    d = json.load(io.open(ld, encoding="utf-8"))
                    turns[arm] = len(d.get("turns") or [])
        entry = {
            "stage": st,
            "plan_file": plan_rel.replace("\\", "/") if plan else None,
            "batch_id": (plan or {}).get("batch", {}).get("batch_id"),
            "plan_sha256": sha(os.path.join(ROOT, plan_rel)) if plan else None,
            "total_attempts": (plan or {}).get("total_attempts"),
            "arms": (plan or {}).get("arm_mapping"),
            "identity_closure": {k: v.get("identity_sha256")
                                 for k, v in ((plan or {}).get("frozen", {})
                                              .get("identity_closure", {}) or {}).items()},
            "cards_file": (plan or {}).get("batch", {}).get("cards_path"),
            "live_dir": live_dir, "turns_written": turns,
            "scorecard": sc_rel.replace("\\", "/") if sc else None,
            "five_layer_delivery": (sc or {}).get("five_layer_delivery"),
            "per_dimension_mean": (sc or {}).get("per_dimension_mean"),
            "paired_verdicts": (sc or {}).get("paired_position_verdicts"),
            "severe_error_count": len((sc or {}).get("severe_errors") or []),
            "resources": (sc or {}).get("resources"),
            "cross_arm_delta": (sc or {}).get("cross_arm_delta_candidate_vs_parent"),
            "fairness_audits": sorted(
                os.path.basename(x) for x in
                glob.glob(os.path.join(ROOT, BATCH_DIR, "%s_INPUT_FAIRNESS_*.json" % st))),
        }
        out["batches"].append(entry)

    # ── 尺子/执行器哈希漂移：同一文件在各批计划里的登记值 + 现场重算 ──
    watch = ["arena/lb_scorer.py", "benchmark/rubric.md",
             "arena/isolation/trusted_controller.py", "tools/exam_layered_score.py",
             "tools/exam_interleave_plan.py", "arena/lb_runner.py",
             "arena/protocol_public.py"]
    for f in watch:
        row = {"file": f, "on_disk_now": sha(os.path.join(ROOT, f)), "per_plan": {}}
        for st in stages:
            plan = load("%s/%s/INTERLEAVE_PLAN.json" % (BATCH_DIR, plan_dir.get(st, "")))
            cf = ((plan or {}).get("frozen", {}) or {}).get("config_files", {}) or {}
            if f in cf:
                row["per_plan"][st] = cf[f]
        vals = set(row["per_plan"].values())
        row["identical_across_plans"] = len(vals) <= 1
        row["matches_disk_now"] = all(v == row["on_disk_now"] for v in vals) if vals else None
        out["ruler_drift"].append(row)

    out["grand_total_physical_attempts_registered"] = grand_attempts

    jp = os.path.join(ROOT, a.out_json)
    os.makedirs(os.path.dirname(jp), exist_ok=True)
    json.dump(out, io.open(jp, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    L = ["# 截止日主轨证据索引（自动现算，非手填）", "",
         "本文件由 `tools/build_evidence_index.py` 生成；所有数字来自已落盘的授权账本、"
         "冻结计划、原始答卷与成绩表。零模型调用。", "",
         "## 1. 真实计费与传输", "",
         "| 阶段 | 授权单 | 不同 attempt_id 数 | 传输失败 | 关键事件 |",
         "|---|---|---|---|---|"]
    for x in out["ledgers"]:
        ev = x["events"]
        L.append("| %s | %s | %d | %s | %s |" % (
            x["stage"], x["authorization_id"], x["distinct_attempt_ids"],
            ev.get("attempt_failed", 0),
            ", ".join("%s=%s" % (k, v) for k, v in sorted(ev.items())
                      if k in ("reserve", "attempt_started", "attempt_completed",
                               "attempt_failed", "released"))))
    L.append("")
    L.append("已登记物理请求合计 **%d** 次（本周期管理帽 16,384，不是支出目标）。"
             % grand_attempts)
    L.append("")
    L.append("## 2. 批次身份与答卷数")
    for b in out["batches"]:
        L.append("")
        L.append("### %s — %s" % (b["stage"], b["batch_id"]))
        L.append("")
        L.append("- 计划 `%s`（sha %s），计划请求 %s，实际写出答卷 %s"
                 % (b["plan_file"], (b["plan_sha256"] or "")[:16], b["total_attempts"],
                    b["turns_written"]))
        L.append("- 两臂身份：%s" % json.dumps(b["identity_closure"], ensure_ascii=False))
        L.append("- 卡文件：`%s`" % b["cards_file"])
        L.append("- 输入公平性审计：%s" % ", ".join("`%s`" % x for x in b["fairness_audits"]))
        if b["paired_verdicts"]:
            L.append("- 配对胜负：%s" % json.dumps(b["paired_verdicts"], ensure_ascii=False))
        if b["five_layer_delivery"]:
            for arm, v in b["five_layer_delivery"].items():
                L.append("  - %s：P 失败 %s/%s，C 可发首选 %s，合法静默 %s，严重错 %s"
                         % (arm, v["P_protocol_failures"], v["P_protocol_positions"],
                            v["C_usable_primary"], v["C_legal_no_reply"],
                            b["severe_error_count"]))
        if b["per_dimension_mean"]:
            for arm in ("parent", "candidate"):
                L.append("  - %s 九维均值：%s" % (arm, json.dumps(
                    b["per_dimension_mean"][arm], ensure_ascii=False)))
        if b["cross_arm_delta"]:
            L.append("- 资源差：%s" % json.dumps(b["cross_arm_delta"], ensure_ascii=False))
    L.append("")
    L.append("## 3. 尺子与执行器哈希漂移披露")
    L.append("")
    L.append("| 文件 | 现算 sha256 | 各计划登记 | 是否一致 |")
    L.append("|---|---|---|---|")
    for r in out["ruler_drift"]:
        reg = " / ".join("%s:%s" % (k, v[:12]) for k, v in sorted(r["per_plan"].items())) or "—"
        flag = ("全批一致" if r["identical_across_plans"] and r["matches_disk_now"] else
                ("各批一致但与磁盘现值不同" if r["identical_across_plans"] else "存在漂移"))
        L.append("| `%s` | %s | %s | %s |" % (r["file"], r["on_disk_now"][:16], reg, flag))
    L.append("")
    L.append("## 4. 阅读纪律")
    L.append("")
    L.append("- 传输失败（T 列）≠ 协议失败（P 列）≠ 无可发正文（C 列）≠ 语义差（S 列）；"
             "用户愿不愿发（W 列）本轨一律 unknown，AI 不代投。")
    L.append("- 合法静默（action=no_reply）单列，不记故障、也不给满分。")
    L.append("- 任何跨批比较先看本表：只有卡与尺子同哈希的批次才可配对。")
    io.open(os.path.join(ROOT, a.out_md), "w", encoding="utf-8").write("\n".join(L) + "\n")
    print("EVIDENCE INDEX WRITTEN", a.out_json, "/", a.out_md)
    print("  stages=%s grand_attempts=%d" % (len(out["batches"]), grand_attempts))
    for r in out["ruler_drift"]:
        print("  %-42s identical=%s matches_disk=%s"
              % (r["file"], r["identical_across_plans"], r["matches_disk_now"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
