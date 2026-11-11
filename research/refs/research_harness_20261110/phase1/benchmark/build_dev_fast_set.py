# -*- coding: utf-8 -*-
"""DEV_FAST_SET v1 构建器（A 线 A2 第一级，2026-11-11）。

选取规则先固化、后执行（宪章 A2-4：不得依据某个候选的输出临时挑位）。
- 完全确定性：无随机数、无候选输出文本参与选位（live CSV 只读 flag/计数列，
  primary_text 列不参与任何决策）。
- 数据源只读：wave4_dev E301–E338 + wave5_selection E339–E378（两池均已开发曝光），
  gold sidecar 仅评估侧引用；不读 benchmark/b_line/、holdout 金库、E401+。
- 零模型 API 调用。

用法：python build_dev_fast_set.py   （输出写到本目录 DEV_FAST_SET.json）
"""
import csv
import hashlib
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]  # D:/lovebrain-arena
OUT = Path(__file__).resolve().parent / "DEV_FAST_SET.json"

W4_CSV = REPO / "refs/benchmark_v1_20261107/phase2/pilot2_merged/PILOT_RESULTS_MERGED.csv"
W5_CSV = REPO / "refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_RESULTS.csv"
MANIFEST = REPO / "refs/benchmark_v1_20261107/phase3/DATASET_V1_FROZEN_MANIFEST.json"
GOLD_W4 = REPO / "benchmark/stories_v2/GOLD_SIDECAR/GOLD_wave4_dev.json"
GOLD_W5 = REPO / "benchmark/stories_v2/GOLD_SIDECAR/GOLD_wave5_selection.json"

SET_ID = "DEV_FAST_SET_v1"
CREATED = "2026-11-11"

# ---------------- R4：专题配额（主专题口径；T01–T04 提高占比 = 53%） ----------------
TOPIC_ORDER = ["T01", "T02", "T03", "T04", "T05", "T06", "T07", "T08", "T09", "T10"]
QUOTA = {"T01": 8, "T02": 8, "T03": 8, "T04": 8, "T05": 6,
         "T06": 6, "T07": 6, "T08": 5, "T09": 3, "T10": 2}
# 每专题×池硬配额（合计 w4=36 / w5=24；满足 w5 单篇上限约束：T01 w5 仅 E339 一篇 → ≤2）
POOL_QUOTA = {
    "T01": {"wave4_dev": 6, "wave5_selection": 2},
    "T02": {"wave4_dev": 5, "wave5_selection": 3},
    "T03": {"wave4_dev": 5, "wave5_selection": 3},
    "T04": {"wave4_dev": 5, "wave5_selection": 3},
    "T05": {"wave4_dev": 3, "wave5_selection": 3},
    "T06": {"wave4_dev": 4, "wave5_selection": 2},
    "T07": {"wave4_dev": 4, "wave5_selection": 2},
    "T08": {"wave4_dev": 2, "wave5_selection": 3},
    "T09": {"wave4_dev": 1, "wave5_selection": 2},
    "T10": {"wave4_dev": 1, "wave5_selection": 1},
}
EP_CAP = 2          # 每篇 episode 全集最多 2 个位（全局）
SMOKE_N = 24
# 结构性假 veto（轻量臂 recommended 键契约已知问题，universal_scorer v1 docstring）：
STRUCTURAL_VETO = "unknown_payload_field"

HARD_SEVERITY = {"severe_error": 3, "cancel_affirm": 3, "veto": 3,
                 "parse": 2, "contract": 2, "error_type": 2}


def load_universe():
    """R1：候选宇宙 = 两池全部 reply 位；R2：逐位专题派生 + 难度标签（确定性）。"""
    manifest = json.load(open(MANIFEST, encoding="utf-8"))["episodes"]
    gold = {}
    for f in (GOLD_W4, GOLD_W5):
        d = json.load(open(f, encoding="utf-8"))
        for t in d["turns"]:
            gold[t["case_id"]] = t
    cancel_family = {"revoked", "erroneous", "invalidated", "superseded",
                     "cancelled", "canceled", "voided", "retracted", "negated"}
    active_family = {"asserted", "confirmed", "active", "fulfilled", "resolved"}
    universe = {}
    for pool, lo, hi in [("wave4_dev", 301, 339), ("wave5_selection", 339, 379)]:
        for ep in range(lo, hi):
            eid = "E%d" % ep
            d = json.load(open(REPO / "benchmark/stories_v2" / pool / eid / "episode.json",
                               encoding="utf-8"))
            primary = manifest[eid]["topic"]
            for ti, t in enumerate(d["turns"]):
                cid = t["case_id"]
                g = gold[cid]
                atoms = g.get("assertions") or []
                unknowns = g.get("forbidden", {}).get("unknowns") or []
                intent = t["input"].get("intent")
                explicit_intent = bool(intent and intent.get("status") == "explicit")
                may_decline = bool(t["input"].get("may_decline"))
                has_cancel = any(a.get("status") in cancel_family for a in atoms if isinstance(a, dict))
                has_mna = any(a.get("negation") is True for a in atoms if isinstance(a, dict))
                cross_turn = any(isinstance(a, dict) and (a.get("source") or {}).get("case_id")
                                 not in (None, cid) for a in atoms)
                required = any(isinstance(a, dict) and a.get("required_this_turn")
                               and a.get("status") in active_family for a in atoms)
                topics = [primary]
                if explicit_intent and "T06" not in topics:
                    topics.append("T06")   # 显式意图轮 → 意图契约（TOPIC_RUBRIC §T06①a）
                if (unknowns or has_mna) and "T07" not in topics:
                    topics.append("T07")   # unknown / must-not-affirm → 无据确定化（§T07）
                if has_cancel and "T09" not in topics:
                    topics.append("T09")   # cancelled/superseded 原子 → 状态机（§T09）
                if cross_turn and "T08" not in topics:
                    topics.append("T08")   # 跨轮断言来源 → 记忆回扣（§T08）
                tags = []
                if explicit_intent: tags.append("intent_turn")
                if may_decline: tags.append("may_decline")
                if unknowns: tags.append("unknowns_in_gold")
                if has_mna: tags.append("must_not_affirm_atoms")
                if has_cancel: tags.append("cancel_or_superseded_atoms")
                if cross_turn: tags.append("cross_turn_callback")
                if required: tags.append("required_atoms")
                if manifest[eid]["turns"] > 12: tags.append("long_episode")
                universe[cid] = {
                    "episode_id": eid, "case_id": cid, "turn_no": ti + 1,
                    "source_pool": pool, "primary_topic": primary,
                    "topics": topics, "intent": intent if explicit_intent else None,
                    "may_decline": may_decline, "difficulty_tags": tags,
                    "input_ref": "benchmark/stories_v2/%s/%s/episode.json#turns[%d]" % (pool, eid, ti),
                    "gold_sidecar_ref": "benchmark/stories_v2/GOLD_SIDECAR/%s#case_id=%s" % (
                        GOLD_W4.name if pool == "wave4_dev" else GOLD_W5.name, cid),
                }
    return universe


def load_evidence(universe):
    """R3：绑定 live 通过/失败证据（只读 flag 列，不读 primary_text）。
    wave4：PILOT_RESULTS_MERGED.csv（DIRECT/CURRENT，2026-11-08）；
    wave5：FOUR_ARM_RESULTS.csv（DIRECT/COT/LEGACY/CURRENT，2026-11-10）。"""
    ev = {cid: {"arms": [], "hard": [], "soft": []} for cid in universe}
    # wave4 长表
    for r in csv.DictReader(open(W4_CSV, encoding="utf-8-sig")):
        cid = r["case_id"]
        if cid not in ev or r["episode_id"] != universe[cid]["episode_id"]:
            continue
        arm, kinds = r["arm_id"], []
        if r["parse_ok"] != "True":
            kinds.append("parse")
        if r["contract_ok"] != "True":
            kinds.append("contract")
        if r["error_type"]:
            kinds.append("error_type")
        veto = r.get("veto")
        if veto and veto != STRUCTURAL_VETO:
            kinds.append("veto")
        if int(r["diag_cancel_affirm"] or 0) > 0:
            kinds.append("cancel_affirm")
        tot, miss = int(r["diag_required_total"] or 0), int(r["diag_required_misses"] or 0)
        if tot > 0 and miss > 0:
            kinds.append("required_miss")
        rec = {"pool": "wave4_dev", "arm": arm, "hard": [k for k in kinds if k != "required_miss"],
               "soft": [k for k in kinds if k == "required_miss"], "action": r["action"]}
        ev[cid]["arms"].append(rec)
    # wave5 宽表
    for r in csv.DictReader(open(W5_CSV, encoding="utf-8-sig")):
        cid = r["case_id"]
        if cid not in ev:
            continue
        for arm in ("DIRECT", "COT", "LEGACY", "CURRENT"):
            kinds = []
            if r[arm + ".parse_ok"] != "True":
                kinds.append("parse")
            if r[arm + ".pcontract_ok"] != "True":
                kinds.append("contract")
            if r[arm + ".severe_error"] == "True":
                kinds.append("severe_error")
            if int(r[arm + ".generic_cancel_affirm"] or 0) > 0:
                kinds.append("cancel_affirm")
            ht = int(r[arm + ".generic_required_hits"] or 0)
            tt = int(r[arm + ".generic_required_total"] or 0)
            soft = tt > 0 and ht < tt
            rec = {"pool": "wave5_selection", "arm": arm, "hard": list(kinds),
                   "soft": ["required_miss"] if soft else [], "action": r[arm + ".action"]}
            ev[cid]["arms"].append(rec)
    # 无 reply 动作且不允许不回 → 软旗
    for cid, e in ev.items():
        for rec in e["arms"]:
            if rec["action"] not in ("reply", "") and not universe[cid]["may_decline"]:
                rec["soft"].append("unexpected_no_reply")
    for cid, e in ev.items():
        e["hard_arms"] = sorted({r["arm"] for r in e["arms"] if r["hard"]})
        e["soft_arms"] = sorted({r["arm"] for r in e["arms"] if not r["hard"] and r["soft"]})
        e["clean"] = all(not r["hard"] and not r["soft"] and r["action"] == "reply" for r in e["arms"])
        e["kinds"] = sorted({k for r in e["arms"] for k in r["hard"]})
        e["soft_kinds"] = sorted({k for r in e["arms"] for k in r["soft"]})
    return ev


def w_rank(u, e):
    """W 槽排序键（升序=优先）：硬失败臂多者优先 → 硬类别严重度 → 波5证据臂多 → 集小轮前。"""
    all_hard = [k for r in e["arms"] for k in r["hard"]]
    sev = max((HARD_SEVERITY.get(k, 1) for k in all_hard), default=0)
    n_w5_hard = len({r["arm"] for r in e["arms"] if r["hard"] and r["pool"] == "wave5_selection"})
    n_w4_hard = len({r["arm"] for r in e["arms"] if r["hard"] and r["pool"] == "wave4_dev"})
    return (-len(e["hard_arms"]), -sev, -n_w5_hard, -n_w4_hard,
            int(u["episode_id"][1:]), u["turn_no"])


def s_rank(u, e, pool_deficit):
    """S 槽排序键（升序=优先）：全臂干净 → 有 required 原子位优先 → 缺口大的池优先 → 集小轮前。"""
    has_req = "required_atoms" in u["difficulty_tags"]
    return (0 if e["clean"] else 1, 0 if has_req else 1, -pool_deficit[u["source_pool"]],
            int(u["episode_id"][1:]), u["turn_no"])


def fill_rank(u, chosen_tagsets, ep_count):
    """填充槽排序键（升序=优先）：标签多样性 → 已选位少的篇优先 → 集小轮前。"""
    seen = set().union(*chosen_tagsets) if chosen_tagsets else set()
    new_tags = len(set(u["difficulty_tags"]) - seen)
    return (-new_tags, ep_count[u["episode_id"]], int(u["episode_id"][1:]), u["turn_no"])


def main():
    universe = load_universe()
    ev = load_evidence(universe)
    assert len(universe) == 980, len(universe)

    # ---------------- R5：逐专题选位 ----------------
    chosen = []            # list of (topic, position dict)
    ep_count = Counter()   # episode -> positions chosen (全局 ≤2)
    used = set()
    pool_totals = Counter()
    log = []

    for topic in TOPIC_ORDER:
        quota = QUOTA[topic]
        pq = dict(POOL_QUOTA[topic])
        cands = [u for u in universe.values() if u["primary_topic"] == topic]
        pool_deficit = {p: pq[p] for p in pq}
        picked = []
        tagsets = []

        def take(u, marker, why):
            nonlocal picked
            picked.append((u, marker, why))
            used.add(u["case_id"])
            ep_count[u["episode_id"]] += 1
            pool_totals[u["source_pool"]] += 1
            pool_deficit[u["source_pool"]] -= 1
            tagsets.append(set(u["difficulty_tags"]))

        # W 槽：全专题候选里硬失败证据最强者（不受池配额限制，除非该池配额=0）
        wc = [u for u in cands if u["case_id"] not in used and ep_count[u["episode_id"]] < EP_CAP
              and (ev[u["case_id"]]["hard_arms"] or ev[u["case_id"]]["soft_arms"])]
        wc = [u for u in wc if pq[u["source_pool"]] > 0] or wc
        wc.sort(key=lambda u: w_rank(u, ev[u["case_id"]]))
        if wc:
            u = wc[0]
            e = ev[u["case_id"]]
            marker = "expected_weakness" if e["hard_arms"] else "expected_weakness_soft"
            take(u, marker, "live-fail: hard_arms=%s kinds=%s soft_arms=%s" % (
                e["hard_arms"], e["kinds"], e["soft_arms"]))
        # S 槽：全臂干净 + 缺口大的池优先
        sc = [u for u in cands if u["case_id"] not in used and ep_count[u["episode_id"]] < EP_CAP
              and ev[u["case_id"]]["clean"] and pool_deficit[u["source_pool"]] > 0]
        sc.sort(key=lambda u: s_rank(u, ev[u["case_id"]], pool_deficit))
        if sc:
            u = sc[0]
            take(u, "expected_strength",
                 "live-clean: all arms clean (%d arm records)" % len(ev[u["case_id"]]["arms"]))
        # 填充槽：按池缺口轮转
        while len(picked) < quota:
            fc = []
            for p in sorted(pool_deficit, key=lambda k: (-pool_deficit[k], k != "wave4_dev")):
                if pool_deficit[p] <= 0:
                    continue
                fc = [u for u in cands if u["case_id"] not in used
                      and ep_count[u["episode_id"]] < EP_CAP
                      and u["source_pool"] == p]
                if fc:
                    break
            if not fc:
                break
            fc.sort(key=lambda u: fill_rank(u, tagsets, ep_count))
            u = fc[0]
            take(u, "probe", "fill by R5c (tag-diversity, ep-spread, pool deficit)")
        assert len(picked) == quota, (topic, len(picked))
        for u, marker, why in picked:
            chosen.append((topic, u, marker, why))
        log.append({"topic": topic, "quota": quota,
                    "by_pool": {p: pq[p] - pool_deficit[p] for p in pq}})

    assert len(chosen) == 60, len(chosen)
    assert pool_totals["wave4_dev"] == 36 and pool_totals["wave5_selection"] == 24, pool_totals
    over = [e for e, c in ep_count.items() if c > EP_CAP]
    assert not over, over

    # 每专题 ≥1 强项证据位 + ≥1 弱项证据位（硬或软失败均计入弱项依据，如实标注 soft）
    cov_check = defaultdict(set)
    for topic, u, marker, why in chosen:
        cov_check[topic].add(marker)
    for topic in TOPIC_ORDER:
        assert any(m.startswith("expected_strength") for m in cov_check[topic]), topic
        assert any(m.startswith("expected_weakness") for m in cov_check[topic]), topic

    # ---------------- R6：排序 + position_id + smoke_24 ----------------
    kind_rank = {"expected_weakness": 0, "expected_weakness_soft": 0,
                 "expected_strength": 1, "probe": 2}
    chosen.sort(key=lambda x: (kind_rank[x[2]], x[0], x[1]["episode_id"], x[1]["turn_no"]))
    positions = []
    for i, (topic, u, marker, why) in enumerate(chosen, 1):
        e = ev[u["case_id"]]
        caps = [{"topic": topic, "marker": marker,
                 "evidence": build_evidence(u, e, marker, why)}]
        for dt in u["topics"][1:]:
            caps.append({"topic": dt, "marker": "probe",
                         "evidence": [{"ref": "derived", "detail":
                                       "derived topic by R2 signal (intent/unknowns/cancel/cross-turn); "
                                       "no separate live strength/weakness claim"}]})
        positions.append({
            "position_id": "D60-%03d" % i,
            "episode_id": u["episode_id"],
            "case_id": u["case_id"],
            "turn_no": u["turn_no"],
            "source_pool": u["source_pool"],
            "topics": u["topics"],
            "primary_topic": u["primary_topic"],
            "intent": u["intent"],
            "may_decline": u["may_decline"],
            "difficulty_tags": u["difficulty_tags"],
            "expected_capabilities": caps,
            "gold_sidecar_ref": u["gold_sidecar_ref"],
            "input_ref": u["input_ref"],
            "notes": "",
        })
    # smoke_24 = position_id 排序前 24
    smoke_ids = [p["position_id"] for p in positions[:SMOKE_N]]
    smoke = [p for p in positions if p["position_id"] in smoke_ids]
    assert any(any(c["marker"].startswith("expected_strength") for c in p["expected_capabilities"])
               for p in smoke)
    assert any(any(c["marker"].startswith("expected_weakness") for c in p["expected_capabilities"])
               for p in smoke)
    smoke_topics = sorted({p["primary_topic"] for p in smoke})

    # ---------------- 覆盖汇总 ----------------
    per_topic = {}
    for topic in TOPIC_ORDER:
        rows = [p for p in positions if p["primary_topic"] == topic]
        per_topic[topic] = {
            "quota": QUOTA[topic], "selected": len(rows),
            "by_pool": Counter(p["source_pool"] for p in rows),
            "expected_weakness": sum(1 for p in rows if p["expected_capabilities"][0]["marker"].startswith("expected_weakness")),
            "expected_strength": sum(1 for p in rows if p["expected_capabilities"][0]["marker"] == "expected_strength"),
            "probe": sum(1 for p in rows if p["expected_capabilities"][0]["marker"] == "probe"),
            "position_ids": [p["position_id"] for p in rows],
        }

    # ---------------- 自校验 sha256（canonical：去 sha256 字段、sort_keys、紧凑分隔符） ----------------
    payload = {
        "set_id": SET_ID,
        "version": "1.0.0",
        "created": CREATED,
        "purpose": "A2 第一级：60 位高价值回复位置研发快速评估集（工作正常性/质量比较/明显倒退检测/Token延迟统计）",
        "selection_rule": SELECTION_RULE_DOC,
        "coverage_summary": {
            "total_positions": len(positions),
            "by_pool": dict(pool_totals),
            "per_topic": per_topic,
            "smoke_24_topics": smoke_topics,
        },
        "scorer_pipeline": SCORER_PIPELINE_DOC,
        "leakage_check": LEAKAGE_DOC,
        "smoke_24": {
            "rule": "R6: position_id 升序前 24 位（D60-001..D60-024）；ID 按 "
                    "(kind_rank[weakness=0,strength=1,probe=2], topic, episode, turn) 排序分配，"
                    "故前 24 位 = 10 个专题弱项位 + 10 个专题强项位 + 4 个探针位，覆盖 T01–T10 全部专题，"
                    "含 ≥1 强项与 ≥1 弱项",
            "position_ids": smoke_ids,
            "topics_covered": smoke_topics,
        },
        "evidence_sources": {
            "wave4_dev": {
                "live_run": "runs/pilot2_wave4_live_b/ + runs/pilot2_wave4_live_c/（DIRECT/CURRENT，2026-11-08）",
                "per_case_flags": "refs/benchmark_v1_20261107/phase2/pilot2_merged/PILOT_RESULTS_MERGED.csv",
                "report": "refs/benchmark_v1_20261107/phase2/PHASE2_PILOT_REPORT.md",
            },
            "wave5_selection": {
                "live_run": "runs/selection_v1{,_current,_direct,_fast,_slow}/（四臂，2026-11-10）",
                "per_case_flags": "refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_RESULTS.csv",
                "universal_scores": "refs/benchmark_v1_20261107/phase3/experiments/selection_v1/selection_v1_universal_scores.csv",
            },
            "topic_definitions": "refs/benchmark_v1_20261107/phase1/TOPIC_RUBRIC_DRAFT_V1.md",
            "frozen_manifest": "refs/benchmark_v1_20261107/phase3/DATASET_V1_FROZEN_MANIFEST.json",
        },
        "sha256": "",
        "sha256_scope": "sha256(canonical_json(payload without 'sha256' field; sort_keys=True, "
                        "separators=(',',':'), ensure_ascii=False).encode('utf-8'))",
        "positions": positions,
    }
    canon = json.dumps({k: v for k, v in payload.items() if k != "sha256"},
                       sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    payload["sha256"] = hashlib.sha256(canon.encode("utf-8")).hexdigest()
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("positions:", len(positions), "| pools:", dict(pool_totals))
    print("per_topic:", {t: (per_topic[t]["selected"], per_topic[t]["by_pool"]["wave4_dev"],
                             per_topic[t]["by_pool"]["wave5_selection"],
                             per_topic[t]["expected_weakness"], per_topic[t]["expected_strength"])
                         for t in TOPIC_ORDER})
    print("smoke topics:", smoke_topics, "| smoke:", smoke_ids[:6], "...")
    print("sha256:", payload["sha256"])
    print("weakness markers:", Counter(p["expected_capabilities"][0]["marker"] for p in positions))
    return payload


def build_evidence(u, e, marker, why):
    if u["source_pool"] == "wave4_dev":
        ref = "refs/benchmark_v1_20261107/phase2/pilot2_merged/PILOT_RESULTS_MERGED.csv"
        run = "runs/pilot2_wave4_live_b/ runs/pilot2_wave4_live_c/"
    else:
        ref = "refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_RESULTS.csv"
        run = "runs/selection_v1* (four-arm, 2026-11-10)"
    detail = why
    if marker == "expected_strength":
        detail = "all %d live arm records clean (contract ok in own layer, no severe/cancel-affirm/parse/contract fail)" % len(e["arms"])
    elif marker == "expected_weakness_soft":
        detail = "soft only: no hard failure recorded; required-atom miss flags in arms %s (marker matching is strict; treat as difficulty hint)" % ",".join(e["soft_arms"])
    return [{"ref": ref, "run_dirs": run,
             "arms_fail_hard": e["hard_arms"], "arms_fail_soft": e["soft_arms"],
             "hard_kinds": e["kinds"], "detail": detail}]


SELECTION_RULE_DOC = {
    "rule_version": "selection_rule_v1",
    "fixed_before_selection": True,
    "R1_universe": "wave4_dev E301–E338（488 reply 位）∪ wave5_selection E339–E378（492 reply 位）= 980；"
                   "不读 benchmark/b_line/、D:/lovebrain-arena_holdout_vault/、E401+。",
    "R2_topic_tags": "primary_topic = DATASET_V1_FROZEN_MANIFEST.episodes[*].topic；"
                     "派生：intent.status=explicit → +T06；gold unknowns 非空或 must-not-affirm(negation) 原子 → +T07；"
                     "CANCEL_FAMILY 原子（cancelled/superseded/retracted/negated/…）→ +T09；"
                     "断言 source.case_id ≠ 本轮 → +T08（跨轮回扣）。T01–T05 仅经 primary_topic。",
    "R3_evidence_binding": {
        "wave4_dev": "PILOT_RESULTS_MERGED.csv（DIRECT/CURRENT，2026-11-08）；轻量臂结构性假 veto "
                     "unknown_payload_field（recommended 键契约已知问题）不计失败。",
        "wave5_selection": "FOUR_ARM_RESULTS.csv（DIRECT/COT/LEGACY/CURRENT，2026-11-10）。",
        "hard_fail": "parse 失败 | 契约失败 | error_type | severe_error | cancel_affirm>0 | 真实 veto",
        "soft_fail": "required_total>0 且 hits<total（marker 匹配严格，作难度提示不作硬失败）| "
                     "may_decline=false 时 no_reply",
        "clean": "全部臂记录无硬/软失败且 action=reply",
    },
    "R4_quota": "主专题配额 T01–T04 各 8（合计 53%）、T05/T06/T07 各 6、T08 5、T09 3（=全部三篇长/状态篇）、T10 2，共 60。",
    "R5_slot_order": "每专题依次：(a) W 槽 = 硬失败臂最多者（并列：硬类别严重度 severe/cancel/veto > parse/contract → "
                     "wave5 四臂证据优先 → episode 小 → turn 小），marker=expected_weakness（仅软旗时如实记 "
                     "expected_weakness_soft）；(b) S 槽 = 全臂干净者中 required 原子位优先、池缺口大者优先，"
                     "marker=expected_strength；(c) 其余槽按池缺口轮转填充，难度标签多样性 → episode 分散优先。"
                     "池配额见 POOL_QUOTA（合计 wave4_dev=36 / wave5_selection=24）；每篇 episode 全局 ≤2 位。",
    "R6_order_and_smoke": "position_id 按 (kind_rank, topic, episode, turn) 排序分配 D60-001..D60-060；"
                          "smoke_24 = ID 前 24 位。",
    "R7_blindness": "选位只用 episode/gold 元数据与 live 判定 flag（parse/contract/severe/cancel_affirm/"
                    "required 计数），不读、不比较任何候选输出文本（primary_text 列不参与决策）；"
                    "无随机数，全程可复现。",
}

SCORER_PIPELINE_DOC = {
    "declared_pipeline": "tools/benchmark_v1/universal_scorer.py（universal_scorer_v1）+ normalize_output.py "
                         "(normalize_v1) + semantic_guards_v1 + gold_diag 原子族（CANCEL/ACTIVE_FAMILY）",
    "primary_extraction": "normalize_v1 固定规则抽取 primary_text（不挑备选；harness 组=recommended 槽，"
                          "轻量组=light 契约 recommended 键）",
    "universal_layer_metrics": ["action(reply/no_reply/missing)", "parse_ok", "contract_ok",
                                "generic_cancel_affirm（语义守卫：疑问/否定/条件/引用/过去式提及不算坐实）",
                                "generic_required_hits/misses/total", "severe_error",
                                "latency_ms", "tokens"],
    "product_layer": "LEGACY/CURRENT(LEGACY_COMPAT_R9)：lb_scorer score10/veto 原样透传；"
                     "DIRECT/COT：仅 light 契约（parse_ok/contract_ok），harness 形状 score10/veto 记 "
                     "NOT_APPLICABLE——轻量臂输出无 recommended 顶层键，lb_scorer PROTOCOL_TOP_KEYS 会误触发 "
                     "veto（pilot 实测 DIRECT veto=544/552 为结构假象），universal_scorer v1 已按层隔离",
    "not_covered": "Q1–Q5 语义质量（自然度/逻辑连贯/上下文承接/情境分寸/可发送性）与 T01–T05 行为档位"
                   "不在 universal_scorer 通用层内——需 judge（受 W20-03 资格表限制）+ B3 亲读；"
                   "本集通用层只做确定性筛查与回归对照",
}

LEAKAGE_DOC = {
    "conclusion": "PASS_for_DEV_DIAGNOSTIC（附三条边界声明）",
    "sources_used": "仅 wave4_dev（2026-11-08 pilot2 曝光）与 wave5_selection（2026-11-10 selection_v1 四臂曝光）；"
                    "gold sidecar 为评估侧合法引用（GOLD_SCHEMA 评估侧使用口径）；未读 b_line/holdout 金库/E401+。",
    "no_future_info": "集内只登记 episode+turn 指针与元数据，不内嵌题面正文/gold 文本；候选可见输入边界"
                      "（真实 HER/ME 消息+IDEA+意图+时间戳）由 runner 保证，本集不改输入。",
    "selection_blindness": "选位未使用任何候选输出文本（R7）；live 证据只取判定 flag 与计数。",
    "caveats": [
        "两池均为已开发曝光材料：对曾在 pilot2/selection 上调过的候选，本集不是盲测，只作 DEV 诊断与回归对照；"
        "不得作为独立泛化成绩（详见 COVERAGE_AND_VALIDITY.md 免责声明）。",
        "wave5 每篇 exposure.json 的 experiments_used 仍为空/ authored_pending_review（曝光账本回填滞后），"
        "实际曝光证据在 runs/selection_v1* 与 phase3/experiments/selection_v1/。",
        "STORY_CUSTODY_LEDGER.json 实际位于 refs/experiment_protocol_20261023/global_harness_program_20261030/ "
        "（非仓库根），且未收录 E339–E378（仅 248 篇，E301–E338 有 consumed_by 记录）。",
    ],
}

if __name__ == "__main__":
    main()
    sys.exit(0)
