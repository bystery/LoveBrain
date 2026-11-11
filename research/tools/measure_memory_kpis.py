# -*- coding: utf-8 -*-
"""tools/measure_memory_kpis.py — W05-02 知识引擎三项 KPI 机械测量（零模型调用）。

数据源：已完成批次 run 目录的 mechanism_notes.txt（每轮记忆引擎内部计数，双格式）。
  v6_bracket（E3 系臂）：facts_store/added_f/updates_applied/invalid_src 等。
  b0_flat（B0）：writeback=ok recent=True scene_facts=N ongoing=1 turn=N。
真值：benchmark/stories_v2/stage4_src/ 事实表（source.kind=message，case_id Sxxx_Tnn）。
KPI（E3 系臂）：
  Capture  = added_f 总和 / 故事 msg 源事实数
  OverStore= facts_store_end / 事实数（>1 = 噪声存储风险）
  UpdateHit= 更正轮（当轮或次轮）updates_applied>0 的比例
  GuardQ   = invalid_src/unsupported_src（应 0）
KPI（B0）：scene_injection_rate（有累计事实的轮里 scene_facts>0 比例）、writeback ok 率。
Fidelity 内容级：记忆态不落盘 → 登记缺口（需 instrumented 批，另立预登记）。
"""
import glob
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KV_RE = re.compile(r"([a-z_]+)=([0-9]+(?:/[0-9]+(?:CU)?)?|ok|True|False)")

_STAGE4_MAP = {"E061": "E061_thyroid_followup.json", "E062": "E062_lens_deal.json",
               "E063": "E063_driving_license.json", "E064": "E064_cat_neuter.json",
               "E065": "E065_first_landlord.json", "E066": "E066_roadtrip.json",
               "E067": "E067_job_offer.json", "E068": "E068_hospital_care.json"}


def parse_notes(path):
    txt = open(path, encoding="utf-8").read()
    out = {"format": None}
    m = re.search(r"\[([^\]]+)\]", txt)
    if m and "facts_store" in m.group(1):
        out["format"] = "v6_bracket"
        for k, v in KV_RE.findall(m.group(1)):
            v = v.split("/")[0].replace("CU", "")
            try:
                out[k] = int(v)
            except ValueError:
                pass
        return out
    kv = dict(KV_RE.findall(txt))
    if "scene_facts" in kv:
        out["format"] = "b0_flat"
        for k in ("scene_facts", "ongoing"):
            try:
                out[k] = int(kv[k])
            except (KeyError, ValueError):
                pass
        out["writeback_ok"] = (kv.get("writeback") == "ok")
        out["recent"] = (kv.get("recent") == "True")
        try:
            out["turn"] = int(kv.get("turn", 0))
        except ValueError:
            pass
        return out
    return None


def collect(batch_root, arm, story):
    rows = []
    pat = os.path.join(batch_root, "%s_%s" % (arm, story), "out", "turn_*")
    for d in sorted(glob.glob(pat)):
        p = os.path.join(d, "mechanism_notes.txt")
        if os.path.exists(p):
            n = parse_notes(p)
            if n:
                rows.append({"turn_dir": os.path.basename(d), "notes": n})
    return rows


def _turn_no(turn_dir):
    m = re.search(r"turn_(\d+)", turn_dir)
    return int(m.group(1)) if m else -1


def story_fact_ground_truth(story):
    fn = _STAGE4_MAP.get(story)
    facts_at, corr_turns, n_facts = {}, set(), 0
    if fn:
        p = os.path.join(ROOT, "benchmark", "stories_v2", "stage4_src", fn)
        if os.path.exists(p):
            j = json.load(open(p, encoding="utf-8"))
            st = j["stories"][0]
            for f in (st.get("facts") or []):
                src = f.get("source") or {}
                if src.get("kind") == "message":
                    cid = str(src.get("case_id", ""))
                    m = re.search(r"T(\d+)$", cid)
                    if m:
                        t = int(m.group(1))
                        facts_at[t] = facts_at.get(t, 0) + 1
                        n_facts += 1
                    if "更正" in str(f.get("relation", "")):
                        m2 = re.search(r"T(\d+)$", cid)
                        if m2:
                            corr_turns.add(int(m2.group(1)))
    return facts_at, corr_turns, n_facts


def measure(batch_specs):
    out = {"kind": "memory_engine_kpis_v2", "date": "2026-10-31", "runs": []}
    for label, broot, arms, stories in batch_specs:
        for story in stories:
            for arm in arms:
                rows = collect(broot, arm, story)
                if not rows:
                    continue
                facts_at, corr_turns, n_facts_gt = story_fact_ground_truth(story)
                fmt = rows[0]["notes"].get("format")
                rec = {"batch": label, "story": story, "arm": arm, "notes_format": fmt,
                       "turns_measured": len(rows), "facts_gt_message_source": n_facts_gt,
                       "correction_turns": sorted(corr_turns)}
                if fmt == "v6_bracket":
                    added_f_total = sum(r["notes"].get("added_f", 0) for r in rows)
                    facts_end = rows[-1]["notes"].get("facts_store", 0)
                    hit = 0
                    upd_by_turn = []
                    for t in sorted(corr_turns):
                        window = [r["notes"].get("updates_applied", 0)
                                  for r in rows if _turn_no(r["turn_dir"]) in (t, t + 1)]
                        if any(w > 0 for w in window):
                            hit += 1
                        upd_by_turn.append({"turn": t, "applied_in_window": sum(window)})
                    rec.update({
                        "added_f_total": added_f_total,
                        "facts_store_end": facts_end,
                        "capture_ratio": round(added_f_total / n_facts_gt, 2) if n_facts_gt else None,
                        "overstore_ratio": round(facts_end / n_facts_gt, 2) if n_facts_gt else None,
                        "correction_update_hits": "%d/%d" % (hit, len(corr_turns)) if corr_turns else "n/a",
                        "updates_at_corr": upd_by_turn,
                        "updates_applied_total": sum(r["notes"].get("updates_applied", 0) for r in rows),
                        "updates_rejected_total": sum(r["notes"].get("updates_rejected", 0) for r in rows),
                        "invalid_src_total": sum(r["notes"].get("invalid_src", 0) for r in rows),
                        "unsupported_src_total": sum(r["notes"].get("unsupported_src", 0) for r in rows),
                    })
                else:
                    cum = 0
                    cov = []
                    for r in rows:
                        t = _turn_no(r["turn_dir"])
                        cum += facts_at.get(t, 0)
                        cov.append({"turn": t, "scene_facts": r["notes"].get("scene_facts", 0),
                                    "facts_cum": cum})
                    wb_ok = sum(1 for r in rows if r["notes"].get("writeback_ok"))
                    recent_ok = sum(1 for r in rows if r["notes"].get("recent"))
                    denom = max(1, sum(1 for c in cov if c["facts_cum"] > 0))
                    rec.update({
                        "writeback_ok": "%d/%d" % (wb_ok, len(rows)),
                        "recent_rate": round(recent_ok / len(rows), 2),
                        "scene_injection_rate": round(
                            sum(1 for c in cov if c["scene_facts"] > 0 and c["facts_cum"] > 0) / denom, 2),
                        "scene_coverage": cov,
                    })
                out["runs"].append(rec)
    return out


if __name__ == "__main__":
    specs = [
        ("B1", os.path.join(ROOT, "logs", "longprobe_b1_live_20261030"),
         ("candidate", "parent"), ["E063", "E064", "E065"]),
        ("B2", os.path.join(ROOT, "logs", "longprobe_b2_live_20261030"),
         ("candidate", "parent"), ["E066", "E067", "E068"]),
        ("C1", os.path.join(ROOT, "logs", "longprobe_c1_live_20261030"),
         ("candidate", "parent"), ["E063", "E064"]),
        ("D1", os.path.join(ROOT, "logs", "longprobe_d1_live_20261030"),
         ("candidate", "parent"), ["E061", "E062"]),
        ("E1", os.path.join(ROOT, "logs", "longprobe_e1_live_20261030"),
         ("candidate", "parent"), ["E065", "E066"]),
    ]
    res = measure(specs)
    out = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                       "W05-02_MEMORY_KPI_MEASUREMENT_20261031.json")
    json.dump(res, open(out, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("written", out)
    for b in res["runs"]:
        if b["notes_format"] == "v6_bracket":
            print("%-3s %-5s %-9s cap=%-5s over=%-5s upd=%-4s rej=%-3s guard=%s/%s corr=%s" % (
                b["batch"], b["story"], b["arm"], b["capture_ratio"], b["overstore_ratio"],
                b["updates_applied_total"], b["updates_rejected_total"],
                b["invalid_src_total"], b["unsupported_src_total"], b["correction_update_hits"]))
        else:
            print("%-3s %-5s %-9s wb=%-6s recent=%-4s scene_inj=%s" % (
                b["batch"], b["story"], b["arm"], b["writeback_ok"], b["recent_rate"],
                b["scene_injection_rate"]))
