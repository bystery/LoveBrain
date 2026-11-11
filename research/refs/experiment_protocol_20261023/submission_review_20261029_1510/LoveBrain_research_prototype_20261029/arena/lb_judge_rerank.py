# lb_judge_rerank.py — Judge 位置偏差控制实验（Phase 1A-1）
# 动机（refs/PHASE1A_JUDGE_RERANK.md）：首轮校准陷阱 16⑤+5④、①②③零陷阱，
# 合格保留率呈 ①50%/②85%/③70%/④60%/⑤50% 位置差——但位置与内容混杂。
# 本实验：同批 5 候选做随机重排重复评审（固定种子可复现），分离"位置效应"与"内容效应"。
# 纪律：结果只用于判定 judge 可信度与误杀分诊，不用于改动锚点/gold，不用于刷分。
# 产出：logs/phase1a/judge_rerank_run{k}.json（原始）+ judge_rerank.json（汇总）

import json
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lb_gateway import ADAPTERS, ModelGateway
from lb_judge_calib import CN_NUM, parse_anchors

RUNS = 4          # 随机重排次数（run0=原序复用首轮结果，不重跑）
SEED0 = 20261017  # 固定种子，保证可复现
MAX_RETRY = 1     # JSON 解析失败重试（dsf 输出可靠性 ~95%）


def judge_prompt(q):
    opts = "\n".join("%s「%s」" % (k, q["options"][i - 1])
                     for k, i in CN_NUM.items() if i <= len(q["options"]))
    return (
        "你在帮一个男生审微信回复。判断标准只有一个：这句话以他的名义发出去，"
        "他愿不愿意发——要像正常中国年轻人自己说的话，不要客服腔、不要情感博主、"
        "不要强行造梗、不要过度共情。可以接受多条，也可以一条都不行。\n\n"
        "聊天背景：%s\n候选回复：\n%s\n\n"
        "输出 JSON：{\"ok\": [\"①\", ...], \"bad\": [\"④\", ...], "
        "\"bad_reason\": {\"④\": \"一句话原因\"}}。ok=愿意发的，bad=不愿意发的，"
        "每条必须恰好归入其一。" % (q["context"], opts))


def call_judge(gw, q, temperature=None):
    """第五波判决 §9：所有尝试逐条留证——evidence.attempts 保存每次原始响应
    （此前只留最后一次成功响应，首次坏响应丢失，重试关系不可审计）。
    网关侧每次 HTTP 尝试另经 event_log_path 即刻落盘（若已设置）。"""
    attempts = []
    res = None
    out = None
    for attempt in range(1 + MAX_RETRY):
        res = gw.generate("你是严谨的微信话术评审。", judge_prompt(q),
                          temperature=temperature, tag="judge")
        # 判决令 §9（2026-10-18）：网关证据链随记录落盘——原始响应/出站温度/响应标识。
        call = gw.calls[-1] if gw.calls else {}
        att = {"attempt": attempt,
               "temperature": call.get("temperature"),
               "resp_model": call.get("resp_model"),
               "resp_id": call.get("resp_id"),
               "finish": call.get("finish"),
               "raw_text": call.get("raw_text")}
        attempts.append(att)
        try:
            m = re.search(r"\{.*\}", res["text"], re.DOTALL)
            out = json.loads(m.group(0))
            if (out.get("ok") or out.get("bad")) is not None:
                break
            out = None
        except Exception:
            out = None
    res["evidence"] = {"attempts": attempts, "final_attempt": attempts[-1]["attempt"]
                       if attempts else None}
    if out is None:
        return {"_parse_error": (res["text"][:300] if res else "")}, res, MAX_RETRY
    return out, res, attempts[-1]["attempt"]


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--anchors", default="benchmark/anchors/ANCHORS_20_v2.md")
    ap.add_argument("--key", default="benchmark/anchors/anchors_key_v2.json")
    ap.add_argument("--adapter", default="openai_compat")
    ap.add_argument("--outdir", default="logs/phase1a")
    ap.add_argument("--runs", type=int, default=RUNS)
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args()

    qs = parse_anchors(args.anchors)
    if args.limit:
        qs = qs[:args.limit]
    key = json.load(open(args.key, encoding="utf-8"))
    os.makedirs(args.outdir, exist_ok=True)
    # 第五波判决 §9：每次 HTTP/解析尝试即刻追加事件日志（成功与失败都落盘）
    gw = ModelGateway(ADAPTERS[args.adapter](),
                      event_log_path=os.path.join(args.outdir, "gateway_events.jsonl"))

    all_runs = []  # all_runs[k][qid] = record（选项已映射回原始位置）
    for k in range(args.runs):
        rng = random.Random(SEED0 + k)
        out_path = os.path.join(args.outdir, "judge_rerank_run%d.json" % (k + 1))
        if os.path.exists(out_path):
            print("run%d 已存在，跳过（断点续跑）" % (k + 1))
            all_runs.append(json.load(open(out_path, encoding="utf-8"))["records"])
            continue
        records = {}
        for q in qs:
            kk = key.get(q["qid"])
            if not kk:
                continue
            perm = list(range(len(q["options"])))
            rng.shuffle(perm)  # perm[new_pos] = 原始 index
            pq = {"qid": q["qid"], "context": q["context"],
                  "options": [q["options"][perm[p]] for p in range(len(perm))]}
            out, res, attempt = call_judge(gw, pq)
            # 新位置①-⑤ → 原始选项 id①-⑤（pos2orig[p+1]=perm[p]）
            def to_orig(ids):
                out_ids = []
                for s in ids or []:
                    if s in CN_NUM:
                        out_ids.append("①②③④⑤"[perm[CN_NUM[s] - 1]])
                return out_ids
            rec = {
                "qid": q["qid"], "perm": perm,
                "judge_ok_orig": sorted(set(to_orig(out.get("ok")))),
                "judge_bad_orig": sorted(set(to_orig(out.get("bad")))),
                "exp_trap": sorted(set(kk.get("trap") or [])),
                "exp_ok": sorted(set(kk.get("合格") or [])),
                "raw_ok": out.get("ok"), "raw_bad": out.get("bad"),
                "bad_reason": out.get("bad_reason"),
                "attempt": attempt, "usage": res.get("usage"),
                "evidence": res.get("evidence"),
            }
            rec["trap_caught"] = sorted(set(rec["exp_trap"]) & set(rec["judge_bad_orig"]))
            rec["ok_kept"] = sorted(set(rec["exp_ok"]) & set(rec["judge_ok_orig"]))
            rec["ok_killed"] = sorted(set(rec["exp_ok"]) & set(rec["judge_bad_orig"]))
            rec["trap_kept"] = sorted(set(rec["exp_trap"]) & set(rec["judge_ok_orig"]))
            rec["unassigned"] = sorted(
                {"①②③④⑤"[i] for i in range(len(pq["options"]))}
                - set(rec["judge_ok_orig"]) - set(rec["judge_bad_orig"]))
            records[q["qid"]] = rec
            print("run%d %s ok=%s bad=%s" % (k + 1, q["qid"],
                                             rec["judge_ok_orig"], rec["judge_bad_orig"]))
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump({"run": k + 1, "seed": SEED0 + k, "records": records},
                      f, ensure_ascii=False, indent=2)
        all_runs.append(records)
        print("run%d 完成 → %s" % (k + 1, out_path))

    # ---- 汇总 ----
    qids = sorted(all_runs[0].keys())
    summary = {"runs": args.runs, "seed0": SEED0, "qids": qids, "per_run": [],
               "position_table": {}, "option_stability": [], "trap_by_run": []}
    for k, records in enumerate(all_runs):
        th = sum(len(r["trap_caught"]) for r in records.values())
        tt = sum(len(r["exp_trap"]) for r in records.values())
        oh = sum(len(r["ok_kept"]) for r in records.values())
        ot = sum(len(r["exp_ok"]) for r in records.values())
        un = sum(len(r["unassigned"]) for r in records.values())
        summary["per_run"].append({"run": k + 1, "trap": "%d/%d" % (th, tt),
                                   "ok_retention": "%d/%d" % (oh, ot),
                                   "ok_rate": round(oh / ot, 3) if ot else None,
                                   "unassigned": un})
    # 位置表：首轮（原序）+ 各重排 run 合并，(run, 位置, 身份[合格/陷阱]) → [总数, 被杀数]
    first = json.load(open("logs/phase05/judge_calib.json", encoding="utf-8"))["results"]
    first_map = {r["qid"]: r for r in first}
    for qid in qids:
        perm0 = list(range(5))  # 首轮=原序
        for k, records in enumerate([None] + all_runs):
            if k == 0:
                fr = first_map.get(qid)
                if not fr:
                    continue
                bad_pos = [CN_NUM[s] for s in fr["judge_bad"]]
                for p, s in enumerate("①②③④⑤"):
                    is_ok = s in fr["exp_ok"]
                    is_trap = s in fr["exp_trap"]
                    killed = p + 1 in bad_pos
                    cell = summary["position_table"].setdefault(
                        ("run0", s, "ok" if is_ok else ("trap" if is_trap else "?")), [0, 0])
                    cell[0] += 1
                    if killed:
                        cell[1] += 1
            else:
                rec = records[qid]
                for p, s in enumerate("①②③④⑤"):
                    if p >= len(rec["perm"]):
                        continue
                    is_ok = s in rec["exp_ok"]
                    is_trap = s in rec["exp_trap"]
                    killed = s in rec["judge_bad_orig"]
                    cell = summary["position_table"].setdefault(
                        ("rerun%d" % k, s, "ok" if is_ok else ("trap" if is_trap else "?")),
                        [0, 0])
                    cell[0] += 1
                    if killed:
                        cell[1] += 1
    # 逐选项稳定性：合并首轮+重排（共 5 次评审）
    for qid in qids:
        fr = first_map.get(qid)
        recs = [records[qid] for records in all_runs if qid in records]
        for s in "①②③④⑤":
            is_ok = fr and s in fr["exp_ok"]
            if not is_ok:
                continue
            killed_cnt = 1 if (fr and s in fr["ok_killed"]) else 0
            for r in recs:
                if s in r["ok_killed"]:
                    killed_cnt += 1
            # 选项长度（原序文本）
            opt_text = None
            for q in qs:
                if q["qid"] == qid:
                    idx = "①②③④⑤".index(s)
                    opt_text = q["options"][idx] if idx < len(q["options"]) else None
            summary["option_stability"].append({
                "qid": qid, "opt": s, "killed_in": killed_cnt,
                "of": 1 + len(recs), "len": len(opt_text or ""), "text": opt_text})
    summary["position_table"] = {"|".join(k): v for k, v in summary["position_table"].items()}
    with open(os.path.join(args.outdir, "judge_rerank.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print("\n=== 汇总 ===")
    for pr in summary["per_run"]:
        print(pr)
    ks = [o for o in summary["option_stability"] if o["killed_in"] == o["of"]]
    print("稳定误杀（5/5 全杀）: %d 条" % len(ks))
    flip = [o for o in summary["option_stability"] if 0 < o["killed_in"] < o["of"]]
    print("翻转误杀（部分被杀）: %d 条" % len(flip))


if __name__ == "__main__":
    main()
