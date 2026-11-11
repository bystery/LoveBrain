# lb_judge_calib.py — Judge 评审团校准器（Phase 0.5 脚手架 + 真实试运行）
# 原理：用 20 道人评锚点题（每题 5 候选，含 1 个故意埋的 trap 负样本）考 judge，
# 与 anchors_key_v2 密钥对答案，产出：trap 识别率 / 合格漏杀率 / 误杀明细。
# 纪律（refs/JUDGE_CALIB_PLAN.md）：judge 一致率 ≥80% 才允许上岗参与晋级；
# 校准结果只用于判定 judge 可信度，不用于改动锚点或 gold。

import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lb_gateway import ADAPTERS, ModelGateway

CN_NUM = {"①": 1, "②": 2, "③": 3, "④": 4, "⑤": 5}


def parse_anchors(md_path):
    """解析 ANCHORS_20_v2.md → [{qid, context, options:[5]}]"""
    text = open(md_path, encoding="utf-8").read()
    qs = []
    for block in re.split(r"\n## ", text)[1:]:
        qid = block.split("\n", 1)[0].strip()
        body = block.split("\n", 1)[1]
        lines = body.split("\n")
        context = ""
        options = []
        for ln in lines:
            ln = ln.strip()
            m = re.match(r"^([①②③④⑤])「(.*)」$", ln)
            if m:
                options.append(m.group(2))
            elif ln and not ln.startswith(("【", ">", "#")) and not options:
                context += ln
        qs.append({"qid": qid, "context": context, "options": options})
    return qs


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


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--anchors", default="benchmark/anchors/ANCHORS_20_v2.md")
    ap.add_argument("--key", default="benchmark/anchors/anchors_key_v2.json")
    ap.add_argument("--out", default="logs/phase05/judge_calib.json")
    ap.add_argument("--adapter", default="openai_compat")
    ap.add_argument("--limit", type=int, default=0, help="只跑前 N 题（0=全部）")
    args = ap.parse_args()

    qs = parse_anchors(args.anchors)
    if args.limit:
        qs = qs[:args.limit]
    key = json.load(open(args.key, encoding="utf-8"))
    # 第五波判决 §9：每次 HTTP 尝试即刻追加事件日志；逐题证据随结果落盘
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    gw = ModelGateway(ADAPTERS[args.adapter](),
                      event_log_path=os.path.join(os.path.dirname(os.path.abspath(args.out)),
                                                  "gateway_events.jsonl"))

    results = []
    trap_hit = trap_total = ok_hit = ok_total = 0
    for q in qs:
        k = key.get(q["qid"])
        if not k:
            continue
        res = gw.generate("你是严谨的微信话术评审。", judge_prompt(q), tag="judge")
        # 第五波判决 §9：逐题原始响应证据（网关 calls 内存账最后一条）
        call = gw.calls[-1] if gw.calls else {}
        evidence = {"temperature": call.get("temperature"),
                    "resp_model": call.get("resp_model"), "resp_id": call.get("resp_id"),
                    "finish": call.get("finish"), "raw_text": call.get("raw_text")}
        out = None
        try:
            m = re.search(r"\{.*\}", res["text"], re.DOTALL)
            out = json.loads(m.group(0))
        except Exception:
            out = {"_parse_error": res["text"][:200]}
        judge_ok = set(out.get("ok") or [])
        judge_bad = set(out.get("bad") or [])
        exp_trap = set(k.get("trap") or {})
        exp_ok = set(k.get("合格") or {})
        r = {"qid": q["qid"], "judge_ok": sorted(judge_ok), "judge_bad": sorted(judge_bad),
             "exp_trap": sorted(exp_trap), "exp_ok": sorted(exp_ok),
             "trap_caught": sorted(exp_trap & judge_bad),
             "ok_kept": sorted(exp_ok & judge_ok),
             "ok_killed": sorted(exp_ok & judge_bad),
             "trap_kept": sorted(exp_trap & judge_ok),
             "evidence": evidence}
        results.append(r)
        trap_total += len(exp_trap)
        trap_hit += len(r["trap_caught"])
        ok_total += len(exp_ok)
        ok_hit += len(r["ok_kept"])
        print("%s trap_caught=%s ok_killed=%s trap_kept=%s" % (
            q["qid"], r["trap_caught"], r["ok_killed"], r["trap_kept"]))

    summary = {
        "questions": len(results),
        "trap_identification": {"hit": trap_hit, "total": trap_total,
                                "rate": round(trap_hit / trap_total, 3) if trap_total else None},
        "ok_retention": {"hit": ok_hit, "total": ok_total,
                         "rate": round(ok_hit / ok_total, 3) if ok_total else None},
        "verdict_threshold": "judge 上岗线：trap 识别率与合格保留率均 ≥0.80（refs/JUDGE_CALIB_PLAN.md）",
        "results": results,
    }
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(json.dumps({k: summary[k] for k in ("questions", "trap_identification",
                                              "ok_retention")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
