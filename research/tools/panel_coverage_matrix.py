# -*- coding: utf-8 -*-
"""tools/panel_coverage_matrix.py — 已付费答卷的「臂×位点」覆盖矩阵（离线，零真实调用）。

质量优先裁定的第一步不是再跑，而是先回答：**同题比较到底拼不拼得出来**。
本脚本扫所有实跑批目录（有 DONE.json 或 RESULT.json 的），逐位记录：
  臂名 / case_id / 批次 / 授权单 / 配置身份（流式 include_usage 与否）/ 臂身份 sha /
  出站 prompt 与状态快照是否归档（决定"召回段"和"保留段"能不能离线测）。
输出：
  evaluation/PAID_ANSWER_COVERAGE_V1.json   机器版
  evaluation/PAID_ANSWER_COVERAGE_V1.md     人读版（含每故事的臂数与缺口）
"""
import glob
import json
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGS = os.path.join(ROOT, "logs")
OUT_JSON = os.path.join(ROOT, "evaluation", "PAID_ANSWER_COVERAGE_V1.json")
OUT_MD = os.path.join(ROOT, "evaluation", "PAID_ANSWER_COVERAGE_V1.md")
# 噪声目录判定必须**锚定匹配**：上一版用裸子串，"ep_" 命中 sw**ep_**a，
# 把刚跑完的 C4 两批整个漏掉，差点得出"没有共同 panel"的假结论。
NOISE_RES = [re.compile(r"^_atk2"), re.compile(r"^exam2_execute"), re.compile(r"^ep_"),
             re.compile(r"^l3b_52_t_"), re.compile(r"^l3b52_test"), re.compile(r"_test_"),
             re.compile(r"^_verify_neg"), re.compile(r"^_smoke"),
             re.compile(r"_plan_probe$"), re.compile(r"^c4_plan_probe"),
             re.compile(r"^c4_prep_"), re.compile(r"^_?mock"), re.compile(r"_mock_"),
             re.compile(r"^importer_selftest"), re.compile(r"^api_trial_plan"),
             re.compile(r"^arena_v1_acceptance"), re.compile(r"^a3_judge"),
             re.compile(r"^judge_v1"), re.compile(r"^e007_12_mock"),
             re.compile(r"^_repwire"), re.compile(r"^_rt_probe"), re.compile(r"^_invalid"),
             re.compile(r"^_manifest_probe"), re.compile(r"^_exam2_accept"),
             re.compile(r"^_ep_test_plan"), re.compile(r"^_plan_probe"),
             re.compile(r"rehearsal"), re.compile(r"capture"), re.compile(r"_mock_load")]


def is_noise(name):
    return any(rx.search(name) for rx in NOISE_RES)


def per_key_summary(pk, real_calls):
    """per_key_dispatch 的键是 臂×故事（值=该键派发数），不是逐槽计数，
    所以它只能核"总派发==付费调用数"，不能拿来宣称"每槽恰好一次"。"""
    if not isinstance(pk, dict) or not pk:
        return None
    total = sum(pk.values())
    return {"keys": len(pk), "total_dispatches": total, "max_per_key": max(pk.values()),
            "matches_real_calls": (total == real_calls) if real_calls else None}


def stream_identity(turn_dir):
    p = os.path.join(turn_dir, "calls.json")
    if not os.path.isfile(p):
        return "unknown", False, False, None, None
    try:
        calls = json.load(open(p, encoding="utf-8"))
    except Exception:                                            # noqa: BLE001
        return "unreadable", False, False, None, None
    c = calls[0] if isinstance(calls, list) and calls else (calls if isinstance(calls, dict) else {})
    timing = c.get("timing") or {}
    usage = c.get("usage") or {}
    ident = "stream+usage" if (timing.get("first_content_ms") is not None
                              and usage.get("source") == "api_usage") else \
        ("stream_no_usage" if timing.get("first_content_ms") is not None
         else "non_stream_or_unknown")
    tok = usage.get("total_tokens")
    lat = timing.get("total_ms")
    return (ident, bool(c.get("system")), bool(c.get("user")), tok, lat)


def _first_nonempty(d, key):
    v = d.get(key)
    return v if v else None


def batch_meta(base):
    """合并 DONE / RESULT / INTERLEAVE_PLAN 三处元数据（第一个非空值胜出）。

    为什么要合并：v3 批的 DONE 里没有 arm_mapping，只读 DONE 会把臂名报成
    字面量 "parent"/"candidate"，覆盖矩阵就会少认两个臂——上一版正是如此。
    """
    meta = {"authorization_id": None, "batch_id": None, "arm_map": None,
            "plan_sha256": None, "started": None, "completed": None, "failed": None,
            "real_model_calls": None, "meta_sources": [],
            "execution_order_verified": None, "dispatch_bijection": None,
            "per_key_dispatch": None}
    cands = [base + ".DONE.json", base + ".RESULT.json",
             os.path.join(base, "INTERLEAVE_PLAN.json")]
    for src in cands:
        if not os.path.isfile(src):
            continue
        try:
            d = json.load(open(src, encoding="utf-8"))
        except Exception:                                            # noqa: BLE001
            continue
        if not isinstance(d, dict):
            continue
        meta["meta_sources"].append(os.path.basename(src))
        for k in ("authorization_id", "batch_id", "real_model_calls",
                  "started", "completed", "failed",
                  "execution_order_verified", "dispatch_bijection", "per_key_dispatch"):
            if meta[k] is None and d.get(k) is not None:
                meta[k] = d.get(k)
        if meta["arm_map"] is None:
            meta["arm_map"] = _first_nonempty(d, "arm_mapping")
        if meta["plan_sha256"] is None:
            sh = d.get("plan_sha256") or (d.get("frozen") or {}).get("plan_sha256")
            meta["plan_sha256"] = (sh or "")[:16] or None
    return meta


def main():
    cells = []
    batches = []
    for base in sorted(glob.glob(os.path.join(LOGS, "*"))):
        if not os.path.isdir(base):
            continue
        name = os.path.basename(base)
        if is_noise(name):
            continue
        if not (os.path.isfile(base + ".DONE.json") or os.path.isfile(base + ".RESULT.json")):
            continue
        meta = batch_meta(base)
        arm_dirs = [d for d in sorted(glob.glob(os.path.join(base, "*")))
                    if os.path.isdir(os.path.join(d, "out"))]
        n_turns = 0
        per_arm_loci = defaultdict(list)
        unresolved_role = []
        for ad in arm_dirs:
            leaf = os.path.basename(ad)
            m = re.match(r"(parent|candidate)_(\w+)$", leaf)
            role, ep = (m.group(1), m.group(2)) if m else ("?", leaf)
            cand = (meta["arm_map"] or {}).get(role)
            if not cand:
                cand = "%s(%s未解析)" % (role, role)
                unresolved_role.append(leaf)
            # 只看目录存在会把"空壳"算成有快照；必须要求 kb 下有非空文件
            state_dir = os.path.join(ad, "_iso_run", "staged", "sandbox")
            state_archived = any(
                os.path.isfile(f) and os.path.getsize(f) > 0
                for f in glob.glob(os.path.join(state_dir, "memory", "kb", "**", "*"),
                                   recursive=True))
            for td in sorted(glob.glob(os.path.join(ad, "out", "turn_*"))):
                ident, has_sys, has_user, tok, lat = stream_identity(td)
                mfp = os.path.join(td, "manifest.json")
                cid, score10, veto = os.path.basename(td), None, None
                if os.path.isfile(mfp):
                    try:
                        md = json.load(open(mfp, encoding="utf-8"))
                        cid = md.get("case_id") or cid
                        score10 = md.get("score10")
                        veto = md.get("veto")
                    except Exception:                                # noqa: BLE001
                        pass
                n_turns += 1
                per_arm_loci[cand].append(cid)
                cells.append({
                    "batch": name, "authorization_id": meta["authorization_id"],
                    "batch_id": meta["batch_id"], "role": role, "episode": ep,
                    "arm": cand, "case_id": cid, "config_identity": ident,
                    "prompt_archived": has_sys and has_user,
                    "state_archived": state_archived,
                    "total_tokens": tok, "total_ms": lat,
                    "contract_score10": score10, "veto": veto,
                })
        if n_turns:
            p_ok = sum(1 for c in cells if c["batch"] == name and c["prompt_archived"])
            s_ok = sum(1 for c in cells if c["batch"] == name and c["state_archived"])
            batches.append({"dir": "logs/" + name, **meta, "turn_dirs": n_turns,
                            "arms": sorted(per_arm_loci),
                            "loci_per_arm": {a: len(v) for a, v in per_arm_loci.items()},
                            "unresolved_arm_dirs": unresolved_role,
                            "prompt_archived": p_ok, "state_archived": s_ok,
                            "config_identities": sorted(
                                {c["config_identity"] for c in cells if c["batch"] == name})})

    by_locus = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for c in cells:
        by_locus[c["episode"]][c["case_id"]][c["arm"]].append(
            {"batch": c["batch"], "config": c["config_identity"],
             "prompt": c["prompt_archived"], "state": c["state_archived"]})

    episodes = {}
    for ep, loci in sorted(by_locus.items()):
        arms_here = set()
        for rec in loci.values():
            arms_here.update(rec.keys())
        full = [cid for cid, rec in loci.items() if len(rec) >= 4]
        episodes[ep] = {"family": "positive_capability" if ep.startswith("P") else "dev_expansion",
                        "loci": len(loci), "arms": sorted(arms_here),
                        "loci_with_4plus_arms": len(full),
                        "arm_min": min(len(rec) for rec in loci.values()),
                        "arm_max": max(len(rec) for rec in loci.values())}

    # 共同 panel 必须**同族同题**：P 系列与 E 系列是不同故事集，不许跨族算同一道题
    panels = {ep: v for ep, v in episodes.items()
              if v["loci_with_4plus_arms"] == v["loci"] and len(v["arms"]) >= 4
              and v["family"] == "dev_expansion"}
    two_arm_same_task = {}
    for ep, loci in by_locus.items():
        for cid, rec in loci.items():
            two_arm_same_task.setdefault(cid, set()).update(rec.keys())

    # panel 明细：同题拼得出来，但**跨批拼接 = 批次/时间块混淆**，必须写清不是隐藏
    panel_detail = {}
    for ep in sorted(panels):
        for cid in sorted(by_locus[ep]):
            panel_detail[cid] = {}
            for arm, recs in by_locus[ep][cid].items():
                panel_detail[cid][arm] = ["%s/%s" % (r["batch"], r["config"]) for r in recs]
    n_batches_per_locus = defaultdict(int)
    for cid, rec in panel_detail.items():
        bs = {x.split("/")[0] for v in rec.values() for x in v}
        n_batches_per_locus[len(bs)] += 1
    confound = {
        "panel_loci": len(panel_detail),
        "distinct_batches_per_locus_histogram": {str(k): v
                                                for k, v in sorted(n_batches_per_locus.items())},
        "clean_within_batch_pairs": [
            {"batch": b["batch_id"], "pair": sorted(b["arms"]), "loci": b["turn_dirs"] // 2,
             "order_verified": b["execution_order_verified"],
             "dispatch_bijection": b["dispatch_bijection"],
             "per_key_dispatch": per_key_summary(b["per_key_dispatch"], b["real_model_calls"]),
             "config_identities": b["config_identities"],
             "note": "同批两臂、同一时间块、同一配置身份——这是唯一无批次混淆的对照"}
            for b in batches if b["turn_dirs"] // 2 >= 1 and len(b["arms"]) == 2],
        "arm_never_repeated_across_batches": sorted(
            {a for rec in panel_detail.values() for a, v in rec.items()
             if len({x.split("/")[0] for x in v}) > 1}),
    }

    # 自检：上一版的失败模式是"静默漏批 + 硬编码真值"，这里一律 fail-fast
    errs = []
    bad = sorted({c["arm"] for c in cells if "未解析" in c["arm"]})
    if bad:
        errs.append("臂名未解析：%s（arm_mapping 没读到，别用 role 冒充臂名）" % bad)
    calls_sum = sum(b["real_model_calls"] or 0 for b in batches)
    if calls_sum != len(cells):
        errs.append("逐位记录数 %d != 各批 real_model_calls 之和 %d（有批被漏掉或多算）"
                    % (len(cells), calls_sum))
    for cid, rec in panel_detail.items():
        if len(rec) < 4:
            errs.append("panel 位点 %s 只有 %d 臂，不该进 panel" % (cid, len(rec)))
    if not batches:
        errs.append("一个实跑批都没扫到——八成是噪声过滤误伤，检查 NOISE_RES")
    if errs:
        for e in errs:
            print("SELFCHK FAIL:", e, file=sys.stderr)
        return 2

    doc = {
        "kind": "paid_answer_coverage_matrix",
        "date": "2026-10-26",
        "batches": batches,
        "cells": len(cells),
        "arms_seen": sorted({c["arm"] for c in cells}),
        "episodes": episodes,
        "panel_candidates": panels,
        "panel_locus_detail": panel_detail,
        "panel_confound": confound,
        "loci_with_4arms": sorted([cid for cid, a in two_arm_same_task.items() if len(a) >= 4]),
        "loci_total": len(two_arm_same_task),
        "excluded_by_design": {
            "judge_stage_answers": "A3/judge_v1_* 批的单位是「成对判断」不是「臂×位点答卷」，"
                                   "本矩阵不计入同题可比性，只在裁判资格里报告",
            "mock_capture_rehearsal_probe": "零真实调用的夹具/演练目录一律排除",
        },
        "real_model_calls_this_script": 0,
    }
    json.dump(doc, open(OUT_JSON, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    L = ["# 已付费答卷覆盖矩阵 V1（臂×位点，离线生成）", "",
         "- 扫描批数：%d；逐位记录 %d 条；出现过的臂：%s"
         % (len(batches), len(cells), ", ".join(doc["arms_seen"])),
         "- 用途：回答『同一道题上到底有几个版本可比』。跨批不同题**不许拼成一条升级曲线**。",
         "- 生成脚本：`tools/panel_coverage_matrix.py`；本脚本真实模型调用 0 次。", "",
         "| 批次 | 授权单 | 臂(该臂位点数) | 出站prompt归档 | 状态快照归档 | 配置身份 |",
         "|---|---|---|---|---|---|"]
    for b in batches:
        lp = "/".join("%s×%d" % (a, b["loci_per_arm"][a]) for a in b["arms"])
        L.append("| %s | %s | %s | %d/%d | %d/%d | %s |" % (
            b["batch_id"] or b["dir"], (b["authorization_id"] or "—"), lp,
            b["prompt_archived"], b["turn_dirs"], b["state_archived"], b["turn_dirs"],
            ", ".join(b["config_identities"])))
    L += ["", "## 每故事的臂覆盖（同题可比性）", "",
          "| 故事 | 族 | 位点数 | 有答卷的臂 | 臂数=4的位点 | 位点臂数 min–max |",
          "|---|---|---:|---|---:|---|"]
    for ep, v in sorted(episodes.items()):
        L.append("| %s | %s | %d | %s | %d | %d–%d |" % (
            ep, v["family"], v["loci"], ", ".join(v["arms"]),
            v["loci_with_4plus_arms"], v["arm_min"], v["arm_max"]))
    L += ["", "## 共同 panel 的混淆结构（必须随成绩单一并出示）", "",
          "- panel 位点数：%d；每个位点由几个批次拼成：%s"
          % (confound["panel_loci"],
             json.dumps(confound["distinct_batches_per_locus_histogram"], ensure_ascii=False)),
          "- 跨批重复出现过的臂（同臂在不同批次各一份答卷）：%s"
          % ("，".join(confound["arm_never_repeated_across_batches"]) or "（无）"),
          "",
          "| 同批配对对照 | 两臂 | 位点 | 顺序核验 | 派发双射 | Σ派发(键数)==付费调用 | 配置身份 |",
          "|---|---|---:|---|---|---|---|"]
    for p in confound["clean_within_batch_pairs"]:
        pk = p["per_key_dispatch"] or {}
        L.append("| %s | %s | %d | %s | %s | %d（%d 键）==%s | %s |" % (
            p["batch"], " vs ".join(p["pair"]), p["loci"], p["order_verified"],
            p["dispatch_bijection"], pk.get("total_dispatches", -1),
            pk.get("keys", -1), pk.get("matches_real_calls", "—"),
            ",".join(p["config_identities"])))
    p_arms = [len(rec) for rec in panel_detail.values()]
    p_prompt = sum(1 for c in cells if c["episode"] in panels and c["prompt_archived"])
    p_state = sum(1 for c in cells if c["episode"] in panels and c["state_archived"])
    p_cells = sum(1 for c in cells if c["episode"] in panels)
    rep = confound["arm_never_repeated_across_batches"]
    verdict = ("拼得出来：%s 共 %d 个位点、每位点 %d–%d 臂有已付费答卷；"
               "这四个故事上的全部答卷中，出站 prompt 归档 %d/%d（⇒『实际注入』段都可测），"
               "结束时状态快照只有 %d/%d（⇒『保留/存储保真』段只对留下快照的臂可测，其余记 NR）；"
               "『最终用对』与主观意愿段仍需人判。"
               % ("/".join(sorted(panels)), confound["panel_loci"],
                  min(p_arms) if p_arms else 0, max(p_arms) if p_arms else 0,
                  p_prompt, p_cells, p_state, p_cells)) if panels and p_cells else               "拼不出来：没有任何一个故事的每道题都有 ≥4 臂答卷。"
    L += ["", "## 结论（裁定问题的直接回答）", "", "- 同题多臂表：%s" % verdict,
          "- 但该表的跨臂比较带批次混淆：%d 个位点里有 %d 个由 ≥3 批拼成；"
          "有跨批重复答卷的臂只有 %s，可用它估批间漂移，其余臂的跨批差异无法与时间块分离。"
          "因此一级结论优先用**同批配对**列，跨臂合并只作并列描述不作因果排序。"
          % (confound["panel_loci"],
             sum(v for k, v in confound["distinct_batches_per_locus_histogram"].items()
                 if int(k) >= 3),
             "、".join("`%s`" % a for a in rep) or "（无）")]
    L += ["", "## 可直接当作共同 panel 的故事（全部位点都有 ≥4 臂答卷，且属 E 族）", "",
          "，".join("`%s`（%d 位点）" % (e, panels[e]["loci"]) for e in sorted(panels)) or "（无）",
          "",
          "- 四臂齐全位点：%d / %d 个已付费位点（跨全部批次去重）"
          % (len(doc["loci_with_4arms"]), doc["loci_total"]),
          "",
          "> 诚实边界一：同一故事在多个批次重复出现的臂，属于**不同时间块/不同配置身份**的答卷，"
          "并列报告但不合并求均；跨批比较同一臂要先看配置身份是否一致。",
          "> 诚实边界二：臂在某故事存在 ≠ 该臂在该故事**每道题**都有答卷，见 min–max 列。",
          "> 诚实边界三：P 族（positive_capability）与 E 族（dev_expansion）是不同故事集，"
          "不许跨族凑成同一道题的比较。",
          "> 诚实边界四：本表只证明「同一道题上有几份答卷」，不证明任何质量高低。"]
    open(OUT_MD, "w", encoding="utf-8").write("\n".join(L) + "\n")

    print(json.dumps({"batches": len(batches), "cells": len(cells),
                      "arms_seen": doc["arms_seen"],
                      "panel_candidates": {k: v["loci"] for k, v in panels.items()},
                      "loci_with_4arms": "%d/%d" % (len(doc["loci_with_4arms"]),
                                                    doc["loci_total"]),
                      "unresolved": sum(len(b["unresolved_arm_dirs"]) for b in batches)},
                     ensure_ascii=False, indent=1))
    print("wrote", OUT_MD.replace("\\", "/"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
