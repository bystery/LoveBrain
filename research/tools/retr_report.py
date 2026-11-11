#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""retr_report.py — 来源追踪报告 v2（第九波判决 §8-B3：词面重合提示，不冒认采用）。

用法：
  python tools/retr_report.py --run logs/l3b_fork/<run_id> [--branch A_full_p1]

读取分支 _sandbox/retrieval_trace.jsonl（由 phase1_retr_idf 机制产出）+ turn_*/reply_raw.txt，
输出逐轮来源追踪表：
  已存储(facts_stored) → 被检索(facts_scored: score/selected/filtered_reason)
  → 进入请求(injected: scene_chain_fact_ids + ongoing 全量 + topic)
  → 词面重合提示(overlap，预登记规则见下)。

v2 修正（第九波判决 §6-1）：
  ①v1 的 bigram 复用检索停用字集合——"过"被剥离导致"过敏"消失，并产生
    "毛敏""敏靠"等原文不连续的伪组合。v2 改为对【原文字符序列】取 bigram
    （不停用字过滤、不跨字拼接），命中的必是原文真实相邻二字。
  ②术语从"采用"改为"词面重合提示"：词面命中只描述共享字面，不能证明
    内容来自哪个通道，更不能推断因果采用。ongoing 与目标 fact 同时在场
    （父版本 ongoing 全量注入）时，若同一概念两通道都有，标"通道不可辨识"。
  ③语义使用（是否真的用了该信息）属人工/AI 语义判读，本工具不自动判定。
该指标只做描述统计，不判对错、不进评分。
"""
import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _raw_bigrams(text):
    """原文连续二元组（CJK 连续段内；不剥离任何字、不跨段拼接）。"""
    segs = []
    cur = []
    for ch in text:
        if "\u4e00" <= ch <= "\u9fff":
            cur.append(ch)
        elif cur:
            segs.append("".join(cur))
            cur = []
    if cur:
        segs.append("".join(cur))
    return {s[i] + s[i + 1] for s in segs for i in range(len(s) - 1)}


def _reply_texts(reply):
    out = []
    resp = (reply.get("response") or {}) if isinstance(reply, dict) else {}
    for k in ("recommended", "bad_boy", "playful", "warm"):
        v = resp.get(k)
        if isinstance(v, str) and v.strip():
            out.append(("response.%s" % k, v))
    dirs = reply.get("directions") if isinstance(reply, dict) else None
    if isinstance(dirs, list):
        for i, d in enumerate(dirs):
            if isinstance(d, str) and d.strip():
                out.append(("directions[%d]" % i, d))
    return out


def _overlap_label(fact_text, reply_blob, other_channel_texts):
    """词面重合提示 + 通道可辨识性。

    返回 (label, hits)：
    - 无命中 → ("", [])
    - 有命中且其他通道（ongoing 等）同样有命中 → ("重合(通道不可辨识)", hits)
    - 有命中且其他通道无 → ("重合(仅facts通道在场)", hits)——仍只是词面提示，
      不能证明"内容来自 fact 而非模型自生成"。
    """
    fb = _raw_bigrams(fact_text)
    hits = sorted(b for b in fb if b in reply_blob)
    if not hits:
        return "", []
    # 通道可辨识性：其他通道文本（ongoing 等）与 fact 共享的 bigram 中，
    # 只要有一个也出现在回复，就无法区分回复内容来自哪个通道。
    ambiguous = False
    fact_bset = fb
    reply_bigrams = None  # 惰性
    for t in other_channel_texts:
        shared = _raw_bigrams(t) & fact_bset
        # 共享 bigram 中只要有一个也出现在回复，就无法区分来源
        for b in shared:
            if reply_bigrams is None:
                reply_bigrams = _raw_bigrams(reply_blob)
            if b in reply_bigrams:
                ambiguous = True
                break
        if ambiguous:
            break
    if ambiguous:
        return "重合(通道不可辨识)", hits
    return "重合", hits


def report_branch(branch_dir):
    trace_p = os.path.join(branch_dir, "_sandbox", "retrieval_trace.jsonl")
    if not os.path.isfile(trace_p):
        print("  （无 retrieval_trace.jsonl——父版本分支或追踪缺失）")
        return
    traces = [json.loads(ln) for ln in open(trace_p, encoding="utf-8") if ln.strip()]
    for tr in traces:
        cid = tr.get("case_id", "?")
        print("== turn %s (%s) memory_access=%s" % (tr.get("turn"), cid, tr.get("memory_access")))
        inj = tr.get("injected") or {}
        inj_ids = set(inj.get("scene_chain_fact_ids") or [])
        reply = {}
        turn_dirs = [d for d in os.listdir(branch_dir) if d.startswith("turn_")]
        for td in turn_dirs:
            if td.rsplit("_", 1)[-1] == cid or cid in td:
                rp = os.path.join(branch_dir, td, "reply_raw.txt")
                if os.path.isfile(rp):
                    try:
                        import lb_scorer
                        reply = lb_scorer.extract_json_block(
                            open(rp, encoding="utf-8").read()) or {}
                    except Exception:
                        reply = {}
                break
        texts = _reply_texts(reply)
        reply_blob = " ".join(t for _, t in texts)
        scored = {r["fact_id"]: r for r in (tr.get("retrieval") or {}).get("facts_scored", [])}
        stored = {f["fact_id"]: f for f in tr.get("facts_stored") or []}
        ongoing_items = [it for it in (inj.get("ongoing_all") or []) if isinstance(it, str)]
        for fid, f in sorted(stored.items()):
            sc = scored.get(fid, {})
            sel = sc.get("selected", fid in inj_ids)
            ovl = ""
            if sel and texts:
                label, hits = _overlap_label(f.get("text", ""), reply_blob, ongoing_items)
                if label:
                    ovl = "%s[%s]" % (label, ",".join(hits[:4]))
                else:
                    ovl = "进入未重合"
            print("   fact[%d] sel=%s score=%s %s | %s %s"
                  % (fid, "Y" if sel else "N", sc.get("score", "-"),
                     sc.get("filtered_reason") or "", f.get("text", "")[:36], ovl))
        for it in ongoing_items:
            fb = _raw_bigrams(it)
            hit = sorted(b for b in fb if b in reply_blob)
            print("   ongoing[%s] 全量注入 %s" % (
                it, "重合[%s]" % ",".join(hit[:4]) if hit else "进入未重合"))
        if inj.get("topic"):
            print("   topic[%s] 注入" % inj["topic"])
    print()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--branch", default=None, help="只报某分支；缺省=全部")
    args = ap.parse_args()
    run_dir = args.run
    branches = ([args.branch] if args.branch
                else sorted(d for d in os.listdir(run_dir)
                            if os.path.isdir(os.path.join(run_dir, d))
                            and not d.startswith(("_", "freeze"))))
    sys.path.insert(0, os.path.join(ROOT, "arena"))
    for b in branches:
        print("####", b)
        report_branch(os.path.join(run_dir, b))


if __name__ == "__main__":
    main()
