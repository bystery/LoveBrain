# lb_judge_2x2.py — Judge 判决不稳定归因实验（Phase 1A-1 修订，大牛 2026-10-17 晚指令）
# 目的：分离 采样噪声(temperature) 与 位置效应 两个混杂变量。
#   实验1（2×2）：{固定顺序(原序), 变化顺序(重排1排列)} × {temp 0.0, temp 0.7} × 3 次重复
#   实验2（五位置均衡轮换）：Latin 方 5 个排列（第 k 轮：选项 i 放位置 (i+k)%5），
#        每个选项恰好经历 ①②③④⑤ 各一次；temp=0 意在压低采样方差（第六波判决 §10：
#        不能宣称"消除噪声/纯测位置"——同温度重复不一致已实测存在）。
# 服务端温度接受度：请求体显式携带 temperature；但参数接受度不能由一次输出是否一致判定，
#   同名义温度下的重复翻转（fixed_t0 25/100）只能记"相同名义温度条件下观察到判决翻转"，
#   不据此推断服务端是否按低温执行（如实报告，不冒认）。
# 纪律：不改锚点/gold/prompt；结果只用于归因与协议设计。

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lb_gateway import ADAPTERS, ModelGateway
from lb_judge_calib import CN_NUM, parse_anchors
from lb_judge_rerank import call_judge

CIRC = "①②③④⑤"


def to_orig(ids, perm):
    out = []
    for s in ids or []:
        if s in CN_NUM and CN_NUM[s] - 1 < len(perm):
            out.append(CIRC[perm[CN_NUM[s] - 1]])
    return sorted(set(out))


def run_condition(gw, qs, key, cond_name, perm_fn, temp, reps, outdir):
    """perm_fn(k) -> perm list（perm[p]=原始index）；返回记录 dict。"""
    out_path = os.path.join(outdir, "judge_2x2_%s.json" % cond_name)
    if os.path.exists(out_path):
        print("%s 已存在，跳过" % cond_name)
        return json.load(open(out_path, encoding="utf-8"))
    records = []
    for rep in range(reps):
        for q in qs:
            kk = key.get(q["qid"])
            if not kk:
                continue
            perm = perm_fn(rep)
            pq = {"qid": q["qid"], "context": q["context"],
                  "options": [q["options"][perm[p]] for p in range(len(perm))]}
            out, res, attempt = call_judge(gw, pq, temperature=temp)
            rec = {
                "cond": cond_name, "rep": rep, "qid": q["qid"], "perm": perm,
                "temp": temp,
                "judge_ok": to_orig(out.get("ok"), perm),
                "judge_bad": to_orig(out.get("bad"), perm),
                "exp_ok": sorted(set(kk.get("合格") or [])),
                "exp_trap": sorted(set(kk.get("trap") or [])),
                "attempt": attempt,
                "evidence": res.get("evidence"),
                "usage": res.get("usage"),
            }
            records.append(rec)
        print("%s rep%d 完成（%d 题）" % (cond_name, rep, len(qs)))
    json.dump({"cond": cond_name, "temp": temp, "reps": reps, "records": records},
              open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print("%s → %s" % (cond_name, out_path))
    return json.load(open(out_path, encoding="utf-8"))


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--anchors", default="benchmark/anchors/ANCHORS_20_v2.md")
    ap.add_argument("--key", default="benchmark/anchors/anchors_key_v2.json")
    ap.add_argument("--adapter", default="openai_compat")
    ap.add_argument("--outdir", default="logs/phase1a")
    args = ap.parse_args()

    qs = parse_anchors(args.anchors)
    key = json.load(open(args.key, encoding="utf-8"))
    os.makedirs(args.outdir, exist_ok=True)
    # 第五波判决 §9：每次 HTTP/解析尝试即刻追加事件日志
    gw = ModelGateway(ADAPTERS[args.adapter](),
                      event_log_path=os.path.join(args.outdir, "gateway_events.jsonl"))

    # 温度实证：首轮/重排实验经网关发出的是 "temperature": null（键存在、值为 None，
    # adapter .get(key, 0.7) 返回 None）→ 服务端默认温度实际取值未知。
    # 本实验两档温度显式传 0.0 / 0.7，请求体确定携带；
    # 服务端是否接受由 fixed_t0 三次重复的一致性经验判定（完全一致=按低温执行）。
    first = qs[0]
    perm0 = list(range(5))
    pq = {"qid": first["qid"], "context": first["context"],
          "options": [first["options"][perm0[p]] for p in range(5)]}
    out, res, _ = call_judge(gw, pq, temperature=0.0)

    identity = lambda rep: list(range(5))
    # 重排1 的排列（变化顺序条件，取自已存档 run1）
    run1 = json.load(open(os.path.join(args.outdir, "judge_rerank_run1.json"),
                          encoding="utf-8"))["records"]
    perm1_map = {qid: rec["perm"] for qid, rec in run1.items()}

    def perm1_for(qid):
        return lambda rep: perm1_map[qid]

    def rot(k):
        # 选项 i 放位置 (i+k)%5 → 位置 p 放选项 (p-k)%5
        return lambda rep: [(p - k) % 5 for p in range(5)]

    conds = [
        ("fixed_t0", identity, 0.0, 3),
        ("fixed_t07", identity, 0.7, 3),
        ("perm1_t0", None, 0.0, 3),      # perm 按题取 run1 排列
        ("perm1_t07", None, 0.7, 3),
    ] + [("rot%d" % k, rot(k), 0.0, 1) for k in range(5)]

    all_conds = {}
    for name, fn, temp, reps in conds:
        if fn is None:  # perm1：每题固定用 run1 的排列
            recs = {"records": []}
            for rep in range(reps):
                for q in qs:
                    kk = key.get(q["qid"])
                    if not kk:
                        continue
                    perm = perm1_map[q["qid"]]
                    pq2 = {"qid": q["qid"], "context": q["context"],
                           "options": [q["options"][perm[p]] for p in range(len(perm))]}
                    out2, res2, att = call_judge(gw, pq2, temperature=temp)
                    recs["records"].append({
                        "cond": name, "rep": rep, "qid": q["qid"], "perm": perm,
                        "temp": temp,
                        "judge_ok": to_orig(out2.get("ok"), perm),
                        "judge_bad": to_orig(out2.get("bad"), perm),
                        "exp_ok": sorted(set(kk.get("合格") or [])),
                        "exp_trap": sorted(set(kk.get("trap") or [])),
                        "attempt": att, "usage": res2.get("usage"),
                        "evidence": res2.get("evidence")})
                print("%s rep%d 完成" % (name, rep))
            p = os.path.join(args.outdir, "judge_2x2_%s.json" % name)
            json.dump(recs, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
            print("%s → %s" % (name, p))
            all_conds[name] = recs
        else:
            all_conds[name] = run_condition(gw, qs, key, name, fn, temp, reps,
                                            args.outdir)
    print("\n全部条件完成：%s" % sorted(all_conds))


if __name__ == "__main__":
    main()
