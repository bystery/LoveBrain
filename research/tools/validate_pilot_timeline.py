#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Pilot episode 时间线与输入协议自动校验 v2（判决令 2026-10-18 第四波 §7）。

v2 新增（相对 v1）：
  - 请求时钟跨节点单调（不允许后一请求时间早于前一请求）；
  - case_id 全局唯一、字段类型校验（ts/clock 格式、messages 为 list、intent 为 dict）；
  - 媒体映射：消息内 image 路径必须存在文件且登记进 media.images；
  - gap 区间合法（from < to）；
  - 末条消息必须是 HER（回复请求的输入契约）；
  - 意图/注释文本防泄漏：评估侧标注词（探针/评分/正确策略等）不得混入候选可见意图；
  - --selftest：三个反例（末条改 ME / 请求时钟倒退 / 意图藏标注）必须被捕获，
    反例通过 = 校验器本身不合格（判决令 §7：反例 PASS 即不能称 schema 验收）。

防泄漏词表说明（判决令 §7）：不泛禁单字——"考试"不违规；只拦评估侧专有标注。
"""
import json, os, sys, datetime, copy

TS_FMT = "%Y-%m-%d %H:%M"
# 评估侧专有标注词（出现在候选可见字段 = 泄漏）。不用单字泛禁。
EVAL_MARKERS = ("gold", "探针", "考点", "评分", "正确策略", "隐藏事实", "布景", "F3", "F4", "F6",
                "expect_decline", "source_id",
                "观测点", "诊断观测", "意图较上一轮")   # v9 复核 §4.2：测评语言混入意图


def parse(ts):
    return datetime.datetime.strptime(ts, TS_FMT)


def validate(ep):
    """返回 (errs, warns)。errs 非空 = FAIL。"""
    errs, warns = [], []
    turns = ep.get("turns") or []
    if not isinstance(turns, list) or not turns:
        return ["turns 缺失或为空"], warns
    seen_ids = set()
    prev_last_msg_ts = None      # 上一节点末条消息 ts
    prev_request_clock = None    # 上一节点请求 clock
    for i, t in enumerate(turns):
        if not isinstance(t, dict):
            errs.append("turn[%d] 非法类型" % i)
            continue
        cid = t.get("case_id")
        if not cid:
            errs.append("turn[%d] 缺 case_id" % i)
            continue
        if cid in seen_ids:
            errs.append("%s: case_id 重复" % cid)
        seen_ids.add(cid)
        inp = t.get("input")
        if not isinstance(inp, dict):
            errs.append("%s: input 缺失或非法" % cid)
            continue
        msgs = inp.get("messages")
        if not isinstance(msgs, list) or not msgs:
            errs.append("%s: messages 缺失或为空" % cid)
            continue
        # --- 消息级 ---
        prev_ts = None
        last_role = None
        for m in msgs:
            if not isinstance(m, dict) or "role" not in m or "text" not in m or "ts" not in m:
                errs.append("%s: 消息缺 role/text/ts 字段: %s" % (cid, str(m)[:40]))
                continue
            try:
                ts = parse(m["ts"])
            except Exception:
                errs.append("%s: ts 格式非法 %s" % (cid, m.get("ts")))
                continue
            if prev_ts and ts < prev_ts:
                errs.append("%s: 消息时间倒退 %s(%s)" % (cid, m["text"][:12], m["ts"]))
            prev_ts = ts
            last_role = m["role"]
            if m["role"] not in ("HER", "ME"):
                errs.append("%s: 非法角色 %s" % (cid, m["role"]))
            for w in EVAL_MARKERS:
                if w in m["text"]:
                    errs.append("%s: 候选可见文本含评估标注'%s': %s" % (cid, w, m["text"][:30]))
            img = m.get("image")
            if img:
                if not os.path.exists(img):
                    errs.append("%s: 消息图片资产缺失 %s" % (cid, img))
                if img not in (inp.get("media") or {}).get("images", []):
                    errs.append("%s: 消息图片 %s 未登记进 input.media.images" % (cid, img))
        # --- 节点级 ---
        clock_s = inp.get("clock")
        try:
            clock = parse(clock_s)
        except Exception:
            errs.append("%s: clock 缺失或格式非法 %s" % (cid, clock_s))
            clock = None
        if clock is not None:
            if prev_ts and clock < prev_ts:
                errs.append("%s: request clock(%s) 早于末条消息(%s)" % (cid, clock_s, msgs[-1]["ts"]))
            if prev_request_clock and clock <= prev_request_clock:
                errs.append("%s: 请求时钟未晚于上一请求（%s ← %s）" % (cid, clock_s, prev_request_clock))
            prev_request_clock = clock
        if last_role != "HER":
            errs.append("%s: 末条消息角色=%s（回复请求要求末条为 HER）" % (cid, last_role))
        if prev_last_msg_ts and parse(msgs[0]["ts"]) <= prev_last_msg_ts:
            errs.append("%s: 首条消息未严格晚于上一节点末条" % cid)
        prev_last_msg_ts = prev_ts
        if (inp.get("may_decline")) != ((t.get("meta") or {}).get("may_decline")):
            warns.append("%s: may_decline 双处标注不一致" % cid)
        for ref in ((inp.get("media") or {}).get("images") or []):
            if not os.path.exists(ref):
                errs.append("%s: media 图片资产缺失 %s" % (cid, ref))
            # 第五波判决 §10：双向映射——仅登记、消息未引用 = 图片会静默丢失
            if not any(isinstance(m, dict) and m.get("image") == ref for m in msgs):
                errs.append("%s: media 登记 %s 未被任何消息引用（单向登记）" % (cid, ref))
        intent = inp.get("intent")
        if intent is not None and not isinstance(intent, dict):
            errs.append("%s: intent 非法类型（应为 dict）" % cid)
        elif isinstance(intent, dict) and intent.get("enabled"):
            txt = intent.get("text") or ""
            if not txt:
                errs.append("%s: intent.enabled 但文本为空" % cid)
            for w in EVAL_MARKERS:
                if w in txt:
                    errs.append("%s: 意图混入评估标注'%s'（泄漏到候选）" % (cid, w))
        note = inp.get("note")
        if isinstance(note, str):
            for w in EVAL_MARKERS:
                if w in note:
                    errs.append("%s: 候选可见 note 含评估标注'%s'" % (cid, w))
    # --- gap ---
    for g in ep.get("gaps") or []:
        try:
            g0, g1 = parse(g["from"]), parse(g["to"])
        except Exception:
            errs.append("gap 时间格式非法: %s" % g)
            continue
        if g0 >= g1:
            errs.append("gap 区间非法 from>=to: %s" % g["from"])
        for t in turns:
            try:
                ts0 = parse(t["input"]["messages"][0]["ts"])
                ts1 = parse(t["input"]["messages"][-1]["ts"])
            except Exception:
                continue
            if ts0 < g1 and ts1 > g0:
                errs.append("gap 与 %s 时间重叠" % t["case_id"])
    return errs, warns


# ---------- 反例自检（--selftest） ----------

def _base_ep():
    return {
        "episode_id": "SELFTEST",
        "persona": {"关系": "同学"},
        "gaps": [],
        "turns": [
            {"case_id": "S_t01",
             "input": {"clock": "2026-10-05 21:47",
                       "messages": [{"role": "HER", "text": "在吗", "ts": "2026-10-05 21:41"},
                                    {"role": "HER", "text": "帮我个忙", "ts": "2026-10-05 21:47"}],
                       "intent": {"enabled": True, "text": "正常意图"}},
             "meta": {}},
            {"case_id": "S_t02",
             "input": {"clock": "2026-10-05 22:10",
                       "messages": [{"role": "HER", "text": "好棒 谢啦", "ts": "2026-10-05 22:05"}],
                       "intent": {"enabled": False, "text": ""}},
             "meta": {}},
        ],
    }


def _mutate_last_is_me(ep):
    ep["turns"][0]["input"]["messages"][-1]["role"] = "ME"
    return ep


def _mutate_cross_request_clock_back(ep):
    """第五波判决 §10：独立反例——每个节点内部全部合法（clock ≥ 本节点末条消息、
    首条消息晚于上一节点末条），唯独跨请求时钟倒退（t02 请求 21:49 < t01 请求 21:50）。"""
    ep["turns"][0]["input"]["clock"] = "2026-10-05 21:50"          # 合法：晚于本节点末条 21:47
    ep["turns"][1]["input"]["messages"][0]["ts"] = "2026-10-05 21:48"  # 合法：晚于 t01 末条
    ep["turns"][1]["input"]["clock"] = "2026-10-05 21:49"          # 合法：≥本节点末条；但 < t01 请求
    return ep


def _mutate_intent_marker(ep):
    ep["turns"][0]["input"]["intent"]["text"] = "接她的话 探针 正确策略是顺着损"
    return ep


def _mutate_media_orphan(ep):
    """第五波判决 §10：媒体单向登记——input.media.images 登记了图片，
    消息未携带 image 字段（旧校验器放行 → 实际调用图片静默丢失）。"""
    ep["turns"][0]["input"]["media"] = {
        "images": ["benchmark/media/pilot/L1_t07_phone.png"]}
    return ep


NEGATIVES = [("末条改ME", _mutate_last_is_me),
             ("跨请求时钟倒退（节点内全合法）", _mutate_cross_request_clock_back),
             ("意图藏标注", _mutate_intent_marker),
             ("媒体单向登记", _mutate_media_orphan)]


def selftest():
    ok = True
    # 正例必须 PASS
    errs, _ = validate(_base_ep())
    if errs:
        print("[SELFTEST] 正例被误杀:", errs)
        ok = False
    for name, mut in NEGATIVES:
        ep = mut(_base_ep())
        errs, _ = validate(ep)
        if not errs:
            print("[SELFTEST] 反例未捕获: %s" % name)
            ok = False
        else:
            print("[SELFTEST] 反例已捕获: %s → %s" % (name, errs[0][:60]))
    print("SELFTEST:", "PASS" if ok else "FAIL")
    return ok


def main():
    args = sys.argv[1:]
    if "--selftest" in args:
        args = [a for a in args if a != "--selftest"]
        good = selftest()
        if not args:
            sys.exit(0 if good else 1)
        if not good:
            sys.exit(1)
    ok = True
    for p in args:
        ep = json.load(open(p, encoding="utf-8"))
        errs, warns = validate(ep)
        n_req = len(ep.get("turns") or [])
        n_md = sum(1 for t in ep.get("turns") or [] if (t.get("input") or {}).get("may_decline"))
        print("== %s: 请求位 %d（可不回 %d）gap %d" % (
            ep.get("episode_id"), n_req, n_md, len(ep.get("gaps") or [])))
        for e in errs:
            ok = False
            print("  [ERR]", e)
        for w in warns:
            print("  [WARN]", w)
    print("校验结果:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
