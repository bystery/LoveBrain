# -*- coding: utf-8 -*-
"""tools/c4_intent_coverage.py — C4 取题的目标层可 Exercised 覆盖率（离线，零真实调用）。

为什么要这一件：C2（goal_action_v1）的主假设是"目标×反馈信号条件化启用动作能力"。
目标块只有 intent.status=ACTIVE 才被两臂共同闸门 reference_chain.is_intent_effective 放行
（2026-10-26 四纠偏之一：96 批有 14 位 intent.enabled=true 但 status=ADJUSTED → 目标块未注入）。
因此若本批题面里 ACTIVE 位点很少，C2 的目标-动作层在多数位点根本不参与——
拿这种批的聚合差值判"目标动作层没收益"就是误判。本脚本先把覆盖率算死，
判读时必须按"目标存活位点"与"全部位点"两个口径并列报，各给分母。

用法：D:\\anaconda\\python.exe tools\\c4_intent_coverage.py
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "candidates", "reference_product_v1"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import reference_chain as rc                                         # noqa: E402

C4 = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "c4_screen_20261026")
EPISODES = ["E006", "E007", "E008", "E012"]


def is_effective(intent):
    """两臂共用的真实判据（不改尺子，直接调生产函数）。"""
    try:
        return bool(rc.is_intent_effective(intent))
    except Exception:                                               # noqa: BLE001
        return bool(intent and intent.get("enabled")
                    and intent.get("status") == "ACTIVE")


def main():
    per_episode, rows = {}, []
    for ep in EPISODES:
        e = json.load(open(os.path.join(ROOT, "benchmark", "episodes", ep + ".json"),
                           encoding="utf-8"))
        live = 0
        for t in e["turns"]:
            it = t["input"].get("intent") or {}
            eff = is_effective(it)
            live += 1 if eff else 0
            rows.append({"case_id": t["case_id"], "episode": ep,
                         "intent_present": bool(t["input"].get("intent")),
                         "enabled": it.get("enabled"), "status": it.get("status"),
                         "goal_block_effective": eff,
                         "note_present": bool((t["input"].get("note") or "").strip())})
        per_episode[ep] = {"turns": len(e["turns"]), "goal_live": live,
                           "goal_inert": len(e["turns"]) - live}

    total = sum(v["turns"] for v in per_episode.values())
    live_total = sum(v["goal_live"] for v in per_episode.values())
    out = {
        "kind": "c4_intent_layer_coverage",
        "episodes": EPISODES,
        "judged_by": "reference_chain.is_intent_effective（两臂共同闸门，未改判据）",
        "per_episode": per_episode,
        "positions_total": total,
        "positions_goal_live": live_total,
        "positions_goal_inert": total - live_total,
        "coverage_rate": round(live_total / total, 4) if total else None,
        "loci_goal_live": sorted(r["case_id"] for r in rows if r["goal_block_effective"]),
        "rows": rows,
        "reading_rule": ("C2 的目标-动作层只在 goal_block_effective=true 的位点被真正 exercise；"
                         "四臂对比必须并列两个口径：全部 32 位（含惰性位点，会稀释差值）与"
                         "目标存活位点（分母=%d）。不得用聚合差值单独判目标动作层无效。" % live_total),
        "real_model_calls": 0,
    }
    path = os.path.join(ROOT, "evaluation", "C4_INTENT_LAYER_COVERAGE_V1.json")
    json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps({k: out[k] for k in ("per_episode", "positions_total",
                                          "positions_goal_live", "positions_goal_inert",
                                          "coverage_rate", "loci_goal_live")},
                     ensure_ascii=False, indent=1))
    print("wrote", path.replace("\\", "/"))
    if live_total == 0:
        print("WARN: 本批完全没有目标存活位点——C2 主假设不可判读")
        sys.exit(3)


if __name__ == "__main__":
    main()
