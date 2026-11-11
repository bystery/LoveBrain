# -*- coding: utf-8 -*-
r"""tools/measure_protocol_repair_offline.py — P（原始协议）与 C（消费后可交付）分列实测（零调用）。

对 S1（96 份）与 S2（24 份）两臂全部**已付费原始答卷**：
  1) 读评分器已记的 program.veto（原始协议层，不改记）；
  2) 用 tools/output_repair.repair() 试归一，记录每条留损；
  3) 把归一后的对象重新送评分器（只读离线），看它是否已具备"能发给用户"的结构；
  4) 对 R4（空正文+全 null=不回但没写 action）单独分列：这类**不允许**由消费侧代补，
     只登记为"生成侧合同缺口"，交付结论仍是"本轮无可发正文"。
输出 JSON：每臂每故障族计数 + 逐位明细。绝不写回任何原始答卷。

用法：
  D:\anaconda\python.exe tools\measure_protocol_repair_offline.py ^
     --skeletons refs\...\S1_SCORE_SKELETON.json refs\...\S2_SCORE_SKELETON.json ^
     --out refs\...\PROTOCOL_REPAIR_OFFLINE_20261028.json
"""
import argparse
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "arena"))
import lb_runner                                                    # noqa: E402
import lb_scorer                                                    # noqa: E402
import output_repair                                                # noqa: E402

EP_DIRS = [os.path.join(ROOT, "benchmark", "episodes"),
           os.path.join(ROOT, "benchmark", "stories_v2", "anchors_20261028")]


def turn_of(case_id):
    sid = case_id.split("_")[0]
    for d in EP_DIRS:
        p = os.path.join(d, sid + ".json")
        if os.path.isfile(p):
            ep = json.load(io.open(p, encoding="utf-8"))
            for t in ep.get("turns") or []:
                if t.get("case_id") == case_id:
                    return t
    return None


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--skeletons", nargs="+", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args(argv)

    rows = []
    for skel_path in a.skeletons:
        batch = os.path.basename(skel_path).split("_")[0]
        sk = json.load(io.open(skel_path, encoding="utf-8"))
        for pos in sk["positions"]:
            for arm, d in pos["arms"].items():
                raw = d.get("raw_text") or ""
                veto = (d.get("program") or {}).get("veto")
                if not veto:
                    continue
                turn = turn_of(pos["case_id"])
                obj, loss, fixed_json = output_repair.repair(raw)
                rescore = None
                if fixed_json and turn is not None:
                    case = {"case_id": pos["case_id"],
                            "turn_type": turn.get("turn_type", "reply"),
                            "input": turn["input"], "gold": turn.get("gold") or {}}
                    try:
                        r = lb_scorer.run_case_scoring(case, fixed_json, {}, None)
                        rescore = {"score10": r.get("score10"), "veto": r.get("veto")}
                    except Exception as e:                          # noqa: BLE001
                        rescore = {"error": str(e)[:160]}
                rules = [x.get("rule") for x in loss]
                rows.append({
                    "batch": batch, "case_id": pos["case_id"], "arm": arm,
                    "raw_protocol_veto": veto,
                    "raw_chars": len(raw),
                    "loss_rules": rules,
                    "loss_log": loss,
                    "sendable_after_repair": sum(
                        1 for k in output_repair.CANON
                        if isinstance((obj or {}).get("response", {}).get(k), str)
                        and (obj or {}).get("response", {}).get(k, "").strip())
                    if obj else 0,
                    "offline_rescore_after_repair": rescore,
                    "classification": (
                        "R4_generator_side_only" if "R4_no_reply_expression_missing" in rules
                        else "consumer_recoverable" if (rescore or {}).get("veto") is None
                        else "unrecoverable"),
                })

    agg = {}
    for r in rows:
        k = (r["batch"], r["arm"], r["classification"])
        agg[k] = agg.get(k, 0) + 1
    out = {
        "kind": "protocol_repair_offline_v1",
        "zero_network": True,
        "note": "P 列（原始协议 veto）按评分器既有记账不改；本表只加测'消费侧能否交付'。"
                "R4 类刻意不由消费侧代补 action：不回必须由生成侧显式表达。",
        "counts": [{"batch": b, "arm": arm, "class": c, "n": n}
                   for (b, arm, c), n in sorted(agg.items())],
        "positions_total_paid": sum(
            len(json.load(io.open(p, encoding="utf-8"))["positions"]) * 2 for p in a.skeletons),
        "protocol_failed_positions": len(rows),
        "rows": rows,
    }
    json.dump(out, io.open(a.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("REPAIR OFFLINE WRITTEN", a.out, "failed=", len(rows),
          "of", out["positions_total_paid"])
    for c in out["counts"]:
        print("  ", c["batch"], c["arm"], c["class"], c["n"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
