# -*- coding: utf-8 -*-
"""Phase 3 · 第 1 步对账脚本（P3-0.1/P3-0.2 执行件，2026-11-09）。

做什么（指导书 PHASE3 §P3-0.1"立即执行项"）：
  1. E301–E338 共 38 篇 exposure.json 补登记真实 pilot 消费事件
     （时间戳取自 runs/pilot2_wave4_live_b/c/*/gateway_events.jsonl 实测窗口，
       绝不伪造早于实验的登记时间）；
  2. E335/E336 exposure 字段补全 + SELECTION→DEV 改判登记（P3-0.1/P3-0.2）；
  3. STORY_CUSTODY_LEDGER.json seed 38 篇 wave4 条目（append-only：只新增，
     不改既有条目/事件），补 E063/LRE121 的 pilot2 消费事件（若缺）；
  4. DATASET_V1_ALLOCATION_V3.csv：E125/E042/E065 标记 DIAGNOSTIC_POOL（不删行，
     不撤销旧审核），E335/E336→DEV，157 PENDING 作废 → 新 160 PENDING
     （40 SELECTION E339–E378 + 120 HOLDOUT E401–E520，ID/专题/轮数按指导书固定）；
  5. LINEAGE_SPLIT_PROVISIONAL_V2.csv（E335/E336→dev）。

不做什么：
  - 不改 episode.json / reference.json / timeline.json / audit.md（考题本体零接触）；
  - 不覆盖 phase2 旧分区表（历史版本原地保留）；
  - 不重算任何评分、不读 B 线、不做任何模型调用。

幂等性：全部写入前检查目标状态，重复运行无副作用。
"""
import csv
import datetime
import hashlib
import json
import os
import shutil
import sys

REPO = "D:/lovebrain-arena"
PH3 = os.path.join(REPO, "refs", "benchmark_v1_20261107", "phase3")
PH2 = os.path.join(REPO, "refs", "benchmark_v1_20261107", "phase2")
W4 = os.path.join(REPO, "benchmark", "stories_v2", "wave4_dev")
LEDGER = os.path.join(REPO, "refs", "experiment_protocol_20261023",
                      "global_harness_program_20261030", "STORY_CUSTODY_LEDGER.json")
RECONCILED_AT = "2026-11-09T00:00:00+08:00"
RECONCILE_TAG = "phase3_reconcile_step1_20261109"

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

EVID = json.load(open(os.path.join(PH3, "_pilot_consumption_evidence.json"),
                      encoding="utf-8"))


def sha256_file(p):
    with open(p, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def part_for(eid):
    if "E301" <= eid <= "E319":
        return "b"
    if "E320" <= eid <= "E338":
        return "c"
    return None


# ---------- 1+2. exposure.json 38 篇 ----------

def fix_exposure():
    changed, skipped = [], []
    for i in range(301, 339):
        eid = "E%d" % i
        p = os.path.join(W4, eid, "exposure.json")
        with open(p, encoding="utf-8") as f:
            ex = json.load(f)
        if ex.get("reconciliation_key") == RECONCILE_TAG:
            skipped.append(eid)
            continue
        p4 = part_for(eid)
        ev = EVID[p4][eid]
        batch = "pilot2_wave4_live_%s_20261108" % p4
        rec = {"experiment": batch, "date": "2026-11-08",
               "window_first_attempt": ev["first"], "window_last_attempt": ev["last"],
               "attempts": ev["attempts"], "arms": ["DIRECT", "CURRENT"],
               "evidence": "runs/%s/{DIRECT,CURRENT}/%s/gateway_events.jsonl" % (batch.replace("_20261108", ""), eid),
               "note": "开发实验曝光：该篇参与 Phase2 真实 pilot（题目冻结后、审计收口前后）；"
                       "本事件于 2026-11-09 从 gateway_events 实数回填，非运行前登记"}
        exp_used = ex.get("experiments_used") or []
        if not any(u.get("experiment") == batch for u in exp_used if isinstance(u, dict)):
            exp_used.append(rec)
        ex["experiments_used"] = exp_used
        if not ex.get("dev_exposed"):
            ex["dev_exposed"] = True
        # 状态推进：三审计（C1/C2/C3）+ FAIL 修复复验已完成（PHASE2_ACCEPTANCE 行5/7/8），
        # 工具未回写状态——此处按事实推进，审计出处随行登记。
        if ex.get("status") == "annotated_pending_audit":
            ex["status"] = "audited_accepted"
            ex["audit_evidence"] = ("refs/benchmark_v1_20261107/phase2/STORY_BATCH_REPORTS/"
                                    "AUDIT_C1_E301-E316.md|AUDIT_C2_E317-E332.md|AUDIT_C3_E333-E338.md")
        # E335/E336 旧 schema 缺字段：从本篇文件与 manifest 补齐（不触碰其他篇）
        if eid in ("E335", "E336"):
            ep = json.load(open(os.path.join(W4, eid, "episode.json"), encoding="utf-8"))
            man = None
            for mf, tc in (("W4_MANIFEST_12T.json", 12), ("W4_MANIFEST_24T.json", 24), ("W4_MANIFEST_32T.json", 32)):
                m = json.load(open(os.path.join(W4, mf), encoding="utf-8"))
                hit = [s for s in m["stories"] if s["story_id"] == eid]
                if hit:
                    man = hit[0]
                    break
            ref = json.load(open(os.path.join(W4, eid, "reference.json"), encoding="utf-8"))
            ex.setdefault("title", (man or {}).get("title", ""))
            ex.setdefault("themes", (man or {}).get("themes", []))
            ex.setdefault("domain", (man or {}).get("domain", ""))
            ex.setdefault("kind", "twin_12t" if (man or {}).get("turns") == 12 else "twin_%dT" % (man or {}).get("turns", 12))
            ex.setdefault("batch", "W4-DEV-20261108")
            ex.setdefault("session", "W4-DEV-A6")
            ex.setdefault("author", ex.get("author", "ZCode bm1-wave4 author-A6 (AI)"))
            ex.setdefault("annotator", "annotator-B6")
            ex.setdefault("readers", ["author", "annotator"])
            ex.setdefault("runner_copy", "benchmark/stories_v2/wave4_dev/%s/runner_episode.json" % eid)
            ex.setdefault("created_at", "2026-11-08")
            ex.setdefault("real_user_material", False)
            ex.setdefault("author_type", "AI")
            ex.setdefault("may_decline_turns",
                          [c for c, t in (ref.get("turns") or {}).items() if t.get("may_decline")])
            hashes = {}
            for fn in ("episode.json", "reference.json", "timeline.json", "audit.md"):
                hashes[fn] = sha256_file(os.path.join(W4, eid, fn))
            ex.setdefault("hashes", hashes)
            ex["split_reclass"] = ("2026-11-09 P3-0.1：v2 分区表列 SELECTION，但本篇已于 2026-11-08 "
                                   "参加真实 pilot（开发曝光）→ 改判 DEV；v2 表保留为历史版本")
        ex["reconciliation_key"] = RECONCILE_TAG
        ex["reconciled_at"] = RECONCILED_AT
        with open(p, "w", encoding="utf-8") as f:
            json.dump(ex, f, ensure_ascii=False, indent=1)
        changed.append(eid)
    return changed, skipped


# ---------- 3. custody ledger seed（append-only） ----------

def seed_ledger():
    with open(LEDGER, encoding="utf-8") as f:
        led = json.load(f)
    stories = led["stories"]
    events = led["events"]
    n_new, n_ev = 0, 0
    for i in range(301, 339):
        eid = "E%d" % i
        if eid in stories:
            continue
        p4 = part_for(eid)
        ev = EVID[p4][eid]
        batch = "pilot2_wave4_live_%s_20261108" % p4
        ex = json.load(open(os.path.join(W4, eid, "exposure.json"), encoding="utf-8"))
        # manifest_episode 哈希取自 W4 manifest（episode.json 本体哈希一致）
        man_ep = None
        for mf in ("W4_MANIFEST_12T.json", "W4_MANIFEST_24T.json", "W4_MANIFEST_32T.json"):
            m = json.load(open(os.path.join(W4, mf), encoding="utf-8"))
            hit = [s for s in m["stories"] if s["story_id"] == eid]
            if hit:
                man_ep = (hit[0].get("files") or {}).get("episode.json")
                break
        h = ex.get("hashes") or {}
        stories[eid] = {
            "stage": "wave4_dev", "title": ex.get("title", ""),
            "themes": ex.get("themes", []), "turns": ex.get("kind", ""),
            "hashes": {"episode": h.get("episode.json"), "reference": h.get("reference.json"),
                       "manifest_episode": man_ep},
            "lifecycle": "EXPOSED",
            "consumed_by": [batch],
            "primary_topic": ex.get("primary_topic", ""),
            "lineage_group_id": ex.get("lineage_group_id", ""),
            "seeded_at": RECONCILED_AT,
            "seed_note": "2026-11-09 phase3 第1步回填：该篇 2026-11-08 已参加真实 pilot，"
                         "消费记录从 gateway_events 实数补登（窗口 %s .. %s，attempts=%d）"
                         % (ev["first"], ev["last"], ev["attempts"]),
        }
        n_new += 1
        events.append({"story": eid, "batch": batch, "arms": "DIRECT,CURRENT",
                       "date": "2026-11-08",
                       "note": "backfilled %s：真实窗口 %s..%s，attempts=%d（gateway_events 实数）"
                               % (RECONCILED_AT, ev["first"], ev["last"], ev["attempts"])})
        n_ev += 1
    # E063/LRE121 pilot2 消费事件补登（若缺）
    for eid in ("E063", "LRE121"):
        ev = EVID["old"][eid]
        batch = "pilot2_oldT09_live_20261108"
        st = stories.get(eid)
        if st is None:
            continue
        cb = st.setdefault("consumed_by", [])
        if batch not in cb:
            cb.append(batch)
            events.append({"story": eid, "batch": batch, "arms": "DIRECT,CURRENT",
                           "date": "2026-11-08",
                           "note": "backfilled %s：真实窗口 %s..%s，attempts=%d（gateway_events 实数）"
                                   % (RECONCILED_AT, ev["first"], ev["last"], ev["attempts"])})
            n_ev += 1
    # 原地备份一次（首次对账时），然后写回
    bak = LEDGER + ".backup_pre_phase3_step1"
    if not os.path.isfile(bak):
        shutil.copyfile(LEDGER, bak)
    with open(LEDGER, "w", encoding="utf-8") as f:
        json.dump(led, f, ensure_ascii=False, indent=1)
    return n_new, n_ev


# ---------- 4. 分区表 V3 ----------

def build_allocation_v3():
    out = os.path.join(PH3, "DATASET_V1_ALLOCATION_V3.csv")
    rows = []
    # 4a. 已建 83 行：读 phase2 v2 表，改判/标记
    with open(os.path.join(PH2, "DATASET_V1_FINAL_ALLOCATION.csv"), encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            eid = (r.get("episode_id") or "").strip()
            if not eid or eid.startswith("#") or eid == "target=240":
                continue
            if r.get("split") not in ("DEV", "SELECTION", "HOLDOUT", ""):
                continue
            if r.get("split") == "" and r.get("status") != "REPAIR_OVERLAY" and r.get("status") not in ("ACCEPT", "ACCEPT_WITH_NA", "annotated_pending_audit", "audited_accepted"):
                continue  # v2 表尾的合计/脚注杂行
            note = r.get("allocation_note", "")
            if eid in ("E125", "E042", "E065"):
                r["status"] = "DIAGNOSTIC_POOL"
                r["allocation_note"] = ("2026-11-09 P3-0.2：移入历史诊断池（supplementary diagnostic，"
                                        "不计入 240 主集）——E125=LIN-E125 与 LRE125 同前缀谱系重复；"
                                        "E042=旧T02弱专项（重分类T09后仍不作T09主代表）；"
                                        "E065=历史算术Gold修订（overlay保留）。原文件与旧审核记录零改动。| 原注记: " + note)
            elif eid in ("E335", "E336"):
                r["split"] = "DEV"
                r["allocation_note"] = ("2026-11-09 P3-0.1：已参加 2026-11-08 真实 pilot（开发曝光）"
                                        "→ SELECTION 改判 DEV；experiments_used 已回填（见 CUSTODY_EXPOSURE_RECONCILIATION.md）| 原注记: " + note)
            r["table_version"] = "v3_20261109"
            rows.append(r)
    # 4b. 新 160 行（PENDING，planned 列按指导书固定）
    plan = []
    def seg(a, b, topic, turns, split):
        for e in range(a, b + 1):
            plan.append(("E%d" % e, topic, turns, split))
    seg(339, 339, "T01", 12, "SELECTION")
    seg(340, 346, "T02", 12, "SELECTION")
    seg(347, 350, "T03", 12, "SELECTION")
    seg(351, 353, "T04", 12, "SELECTION")
    seg(354, 359, "T05", 12, "SELECTION")
    seg(360, 365, "T06", 12, "SELECTION")
    seg(366, 370, "T07", 12, "SELECTION")
    seg(371, 373, "T08", 12, "SELECTION")
    plan.append(("E374", "T09", 24, "SELECTION"))
    plan += [("E375", "T10", 12, "SELECTION"), ("E376", "T10", 12, "SELECTION"),
             ("E377", "T10", 12, "SELECTION"), ("E378", "T10", 12, "SELECTION")]
    seg(401, 405, "T01", 12, "HOLDOUT")
    seg(406, 430, "T02", 12, "HOLDOUT")
    seg(431, 450, "T03", 12, "HOLDOUT")
    seg(451, 462, "T04", 12, "HOLDOUT")
    seg(463, 474, "T05", 12, "HOLDOUT")
    seg(475, 486, "T06", 12, "HOLDOUT")
    seg(487, 496, "T07", 12, "HOLDOUT")
    seg(497, 504, "T08", 12, "HOLDOUT")
    for e in range(505, 510):
        plan.append(("E%d" % e, "T09", 24, "HOLDOUT"))
    for e in range(510, 513):
        plan.append(("E%d" % e, "T09", 32, "HOLDOUT"))
    twin_map = {"E513": ("LG-T10-G5", "T10-G5", "original"), "E514": ("LG-T10-G5", "T10-G5", "counterfactual"),
                "E515": ("LG-T10-G6", "T10-G6", "original"), "E516": ("LG-T10-G6", "T10-G6", "counterfactual"),
                "E517": ("LG-T10-G7", "T10-G7", "original"), "E518": ("LG-T10-G7", "T10-G7", "counterfactual"),
                "E519": ("LG-T10-G8", "T10-G8", "original"), "E520": ("LG-T10-G8", "T10-G8", "counterfactual")}
    for e in range(513, 521):
        plan.append(("E%d" % e, "T10", 12, "HOLDOUT"))
    g_map = {"E375": ("LG-T10-G3", "T10-G3", "original"), "E376": ("LG-T10-G3", "T10-G3", "counterfactual"),
             "E377": ("LG-T10-G4", "T10-G4", "original"), "E378": ("LG-T10-G4", "T10-G4", "counterfactual")}
    twin_type = {"E375": "goal(目标变化)", "E376": "goal(目标变化)",
                 "E377": "time(时间变化)", "E378": "time(时间变化)",
                 "E513": "time(时间变化)", "E514": "time(时间变化)",
                 "E515": "subject(人物归属变化)", "E516": "subject(人物归属变化)",
                 "E517": "negation_status(否定/状态变化)", "E518": "negation_status(否定/状态变化)",
                 "E519": "goal(目标约束变化)", "E520": "goal(目标约束变化)"}
    for eid, topic, turns, split in plan:
        lg, twin, role = g_map.get(eid) or twin_map.get(eid) or ("", "", "")
        lg = "LG-" + eid if not lg else lg
        note = "2026-11-09 P3-0.4 新分派（指导书 §P3-1.1/1.2 固定 ID）"
        if eid in twin_type:
            note += "；反事实孪生组（扰动类型=%s，同组同分区）" % twin_type[eid]
        rows.append({"episode_id": eid, "kind": "new", "primary_topic": topic,
                     "split": "PENDING", "planned_split": split,
                     "lineage_group_id": lg, "twin_group": (twin if twin else ""),
                     "twin_role": role, "turns": turns, "domain": "", "status": "PENDING_NOT_GENERATED",
                     "allocation_note": note,
                     "table_version": "v3_20261109"})
    cols = ["episode_id", "kind", "primary_topic", "split", "planned_split",
            "lineage_group_id", "twin_group", "twin_role", "turns", "domain", "status",
            "allocation_note", "table_version"]
    with open(out, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow(r)
        # 脚注行置于表尾（guard 表头检测要求首行为列头，phase2 v2 同例）
        f.write("# DATASET_V1_ALLOCATION_V3 (2026-11-09) — P3-0 修订版：157 PENDING 作废，新 PENDING=160\n")
        f.write("# 修订理由：P0-1 曝光污染（E335/E336 回 DEV）+ 谱系重复/弱专项/算术Gold（E125/E042/E065 移诊断池）+ 分区重划\n")
        f.write("# 历史版本：phase2/DATASET_V1_FINAL_ALLOCATION.csv（v2_20261108）原地保留\n")
        f.write("# 主集=DEV 80 + SELECTION 40 + HOLDOUT 120 = 240；另 DIAGNOSTIC_POOL 3 篇不计入主集\n")
    return out, rows


# ---------- 5. lineage split V2 ----------

def build_lineage_v2():
    out = os.path.join(PH3, "LINEAGE_SPLIT_PROVISIONAL_V2.csv")
    with open(out, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["episode_id", "split"])
        for i in range(301, 339):
            w.writerow(["E%d" % i, "dev"])
    return out


def main():
    os.makedirs(PH3, exist_ok=True)
    changed, skipped = fix_exposure()
    n_new, n_ev = seed_ledger()
    alloc, rows = build_allocation_v3()
    lin = build_lineage_v2()
    # 闭合自检
    from collections import Counter
    main_rows = [r for r in rows if r.get("status") != "DIAGNOSTIC_POOL"]
    dev = [r for r in main_rows if r["split"] == "DEV"]
    sel = [r for r in main_rows if r["split"] in ("SELECTION", "PENDING") and r.get("planned_split") == "SELECTION"]
    hol = [r for r in main_rows if r["split"] in ("HOLDOUT", "PENDING") and r.get("planned_split") == "HOLDOUT"]
    diag = [r for r in rows if r.get("status") == "DIAGNOSTIC_POOL"]
    print("exposure changed=%d skipped(idempotent)=%d" % (len(changed), len(skipped)))
    print("ledger: new stories=%d new events=%d" % (n_new, n_ev))
    print("allocation v3: rows=%d  DEV=%d SELECTION(planned)=%d HOLDOUT(planned)=%d DIAG=%d"
          % (len(rows), len(dev), len(sel), len(hol), len(diag)))
    dt = Counter(r["primary_topic"] for r in dev)
    st = Counter(r["primary_topic"] for r in sel)
    ht = Counter(r["primary_topic"] for r in hol)
    print("DEV topics:", dict(sorted(dt.items())), "=", sum(dt.values()))
    print("SEL topics:", dict(sorted(st.items())), "=", sum(st.values()))
    print("HOL topics:", dict(sorted(ht.items())), "=", sum(ht.values()))
    assert sum(dt.values()) == 80 and sum(st.values()) == 40 and sum(ht.values()) == 120
    exp_dev = {"T01": 22, "T02": 4, "T03": 4, "T04": 9, "T05": 6, "T06": 4, "T07": 5, "T08": 7, "T09": 15, "T10": 4}
    exp_sel = {"T01": 1, "T02": 7, "T03": 4, "T04": 3, "T05": 6, "T06": 6, "T07": 5, "T08": 3, "T09": 1, "T10": 4}
    exp_hol = {"T01": 5, "T02": 25, "T03": 20, "T04": 12, "T05": 12, "T06": 12, "T07": 10, "T08": 8, "T09": 8, "T10": 8}
    assert dict(dt) == exp_dev, "DEV 逐专题不闭合: %s" % dt
    assert dict(st) == exp_sel, "SEL 逐专题不闭合: %s" % st
    assert dict(ht) == exp_hol, "HOL 逐专题不闭合: %s" % ht
    print("quota closure: OK (P3-0.3 逐格一致)")


if __name__ == "__main__":
    main()
