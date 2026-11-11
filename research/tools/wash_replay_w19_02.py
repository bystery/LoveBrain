#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""wash_replay_w19_02.py — W17c 六篇 candidate 原始 reply_raw 的守卫侧清洗离线回放
（W19-02，零模型调用）。

流程（对每轮）：
  1. 读原题快照 <arm>/<EP>.copy.json（episode_manifest integrity sha 核对后才用），
     canonical_id_map(本轮 messages) + prior_real_hist_n(ep, i) 得到清洗参数；
  2. 清洗前先按 FK-SCORE-01 修复后合法集（canonical + prior_hist_id_map，直接
     import，不复制逻辑）对未清洗原文重评一次 → unwashed_fixed_veto（对照基线）；
  3. source_id_wash.wash_reply_text 清洗 → washed_text + meta；
  4. 清洗后文本用同一合法集重评 → washed_veto；
  5. 校验正文不变：清洗前后解析对象除 source_ids 外逐字段相等；
  6. 汇总 veto 归零/保留情况，落盘 _reports/W19_02_WASH_REPLAY.json。
不修改任何候选目录、题面、原 scoring.json；清洗只发生在内存中的回放文本。
"""
from __future__ import annotations

import copy
import datetime
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)
if HERE not in sys.path:
    sys.path.insert(0, HERE)

from arena.lb_runner import canonical_id_map, load_contract  # noqa: E402
from arena.isolation.trusted_controller import (  # noqa: E402
    prior_hist_id_map, prior_real_hist_n)
from arena import lb_scorer  # noqa: E402
from source_id_wash import wash_reply_text  # noqa: E402
import rescore_batch  # noqa: E402  复用原题快照解析/契约解析/turn 目录枚举

BATCH = os.path.join(ROOT, "logs", "w17c_merge_live_20261103")
EPS = ["E157", "E164", "E170", "E183", "E191", "E212"]
OUT_REPORT = os.path.join(BATCH, "_reports", "W19_02_WASH_REPLAY.json")


def _full_legal_map(ep, ti):
    """FK-SCORE-01 修复后口径：canonical + 机制渲染历史标签（直接 import）。"""
    turn = ep["turns"][ti]
    id_map = canonical_id_map((turn.get("input") or {}).get("messages"))
    id_map.update(prior_hist_id_map(ep, ti))
    return id_map


def replay_ep(ep_name):
    arm_dir = os.path.join(BATCH, "candidate_" + ep_name)
    summary, _ = rescore_batch.rescore_arm(
        BATCH, "candidate_" + ep_name, dry_run=True)  # 只为 integrity/契约解析
    contract, contract_src = rescore_batch._resolve_contract(
        arm_dir, summary["candidate"])
    ep_path = summary["episode_snapshot"]
    ep = rescore_batch._load_json(os.path.join(ROOT, ep_path))

    turns_out = []
    for ti, case_id, td in rescore_batch._arm_turn_dirs(arm_dir):
        r_path = os.path.join(td, "reply_raw.txt")
        s_path = os.path.join(td, "scoring.json")
        if not (os.path.isfile(r_path) and os.path.isfile(s_path)):
            turns_out.append({"turn": ti + 1, "case_id": case_id,
                              "skipped": "missing scoring/reply"})
            continue
        turn_eval = copy.deepcopy(ep["turns"][ti])
        raw = open(r_path, encoding="utf-8").read()
        id_map = _full_legal_map(ep, ti)
        hist_n = prior_real_hist_n(ep, ti)
        canon_ids = set(canonical_id_map(
            (ep["turns"][ti].get("input") or {}).get("messages")))

        old_veto = rescore_batch._load_json(s_path).get("veto")
        unwashed = lb_scorer.run_case_scoring_safe(turn_eval, raw, id_map,
                                                   contract)
        washed_text, meta = wash_reply_text(raw, canon_ids, hist_n)
        washed = lb_scorer.run_case_scoring_safe(turn_eval, washed_text, id_map,
                                                 contract)

        body_intact = None
        if meta["changed"]:
            body_intact = bool(meta.get("body_intact"))
        elif not meta["unparseable"]:
            body_intact = True    # 未改动 = 逐字节原样返回

        turns_out.append({
            "turn": ti + 1, "case_id": case_id,
            "prior_hist_n": hist_n,
            "canonical_ids": sorted(canon_ids),
            "wash_changed": bool(meta["changed"]),
            "wash_unparseable": bool(meta["unparseable"]),
            "wash_rules": meta["rules"],
            "wash_edits": meta["edits"],
            "body_intact": body_intact,
            "original_run_veto": old_veto,
            "unwashed_fixed_veto": unwashed.get("veto"),
            "unwashed_fixed_score10": unwashed.get("score10"),
            "washed_veto": washed.get("veto"),
            "washed_score10": washed.get("score10"),
            "veto_cleared_by_wash": bool(unwashed.get("veto")
                                         and not washed.get("veto")),
        })
    return {
        "episode_id": ep_name,
        "candidate": summary["candidate"],
        "contract": contract or "legacy",
        "contract_resolved_from": contract_src,
        "integrity_match": summary["integrity_match"],
        "n_turns": len([t for t in turns_out if "washed_veto" in t]),
        "n_wash_changed": sum(1 for t in turns_out if t.get("wash_changed")),
        "turns": turns_out,
    }


def main():
    episodes = [replay_ep(e) for e in EPS]

    def veto_count(key):
        return sum(1 for ep in episodes for t in ep["turns"]
                   if t.get(key))

    cleared = [{"episode": ep["episode_id"], "case_id": t["case_id"],
                "original_run_veto": t["original_run_veto"],
                "unwashed_fixed_veto": t["unwashed_fixed_veto"],
                "washed_veto": t["washed_veto"], "edits": t["wash_edits"]}
               for ep in episodes for t in ep["turns"]
               if t.get("veto_cleared_by_wash")]
    remaining = [{"episode": ep["episode_id"], "case_id": t["case_id"],
                  "washed_veto": t["washed_veto"]}
                 for ep in episodes for t in ep["turns"] if t.get("washed_veto")]
    body_broken = [{"episode": ep["episode_id"], "case_id": t["case_id"]}
                   for ep in episodes for t in ep["turns"]
                   if t.get("body_intact") is False]

    report = {
        "tool": "tools/wash_replay_w19_02.py",
        "wash_module": "tools/source_id_wash.py",
        "unit_tests": "tools/test_source_id_wash.py (ALL PASS)",
        "replayed_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "batch": os.path.basename(BATCH),
        "arms": ["candidate_" + e for e in EPS],
        "zero_model_calls": True,
        "scope_note": "守卫侧 source_ids 清洗离线回放；不修改任何候选目录、"
                      "题面、原 scoring.json；清洗文本不落盘，只用于重评",
        "semantics_note": "m2 类短格式判定依据（W17c E164_T04 实证）：同一 "
                          "analysis 块内 m0 挂本轮 T04 消息（canonical_id_map "
                          "→ m0），m2 挂 T03（2026-11-10 21:05 纯次卡1450/"
                          "单次72.5）= prior 历史第 2 条（0 计）→ 机制渲染 "
                          "格式 m_src[2]。规则：mN 在本轮 canonical 合法集内 "
                          "按字面保留；否则 N < prior_hist_n 改写 m_src[N]；"
                          "越界/非 id 移除并登记（不猜）。",
        "counts": {
            "n_turns_total": sum(ep["n_turns"] for ep in episodes),
            "n_wash_changed": sum(ep["n_wash_changed"] for ep in episodes),
            "original_run_veto_turns": veto_count("original_run_veto"),
            "unwashed_fixed_veto_turns": veto_count("unwashed_fixed_veto"),
            "washed_veto_turns": veto_count("washed_veto"),
            "illegal_source_id_before_wash": sum(
                1 for ep in episodes for t in ep["turns"]
                if (t.get("unwashed_fixed_veto") or "").startswith(
                    "illegal_source_id")),
            "illegal_source_id_after_wash": sum(
                1 for ep in episodes for t in ep["turns"]
                if (t.get("washed_veto") or "").startswith("illegal_source_id")),
        },
        "veto_cleared_by_wash": cleared,
        "veto_remaining_after_wash": remaining,
        "body_intact_violations": body_broken,
        "episodes": episodes,
    }
    os.makedirs(os.path.dirname(OUT_REPORT), exist_ok=True)
    with open(OUT_REPORT, "w", encoding="utf-8", newline="") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print("[wash-replay] 汇总 -> %s" % OUT_REPORT)
    print("[wash-replay] veto: original_run=%d unwashed_fixed=%d washed=%d | "
          "illegal_source_id: %d -> %d | wash_changed=%d | body_violations=%d"
          % (report["counts"]["original_run_veto_turns"],
             report["counts"]["unwashed_fixed_veto_turns"],
             report["counts"]["washed_veto_turns"],
             report["counts"]["illegal_source_id_before_wash"],
             report["counts"]["illegal_source_id_after_wash"],
             report["counts"]["n_wash_changed"],
             len(body_broken)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
