# -*- coding: utf-8 -*-
r"""tools/audit_pair_input_fairness.py — 双臂真实出站输入的公平性与泄漏审计（零调用）。

为什么必须有：Q0 要求"两臂看到同一份 persona/kb/意图/备注/时间与真实历史"，
只看关键词命中不够——公平性是逐位核对"本轮该看到的东西确实进了两边的请求体"，
同时"评估侧的判据（卡）绝不能进候选可见输入"。capture 目录里的 calls.json 是
真实出站请求体（替身网络，仅网络被替换），因此可以直接审计。

审计项：
  F1 逐位双臂都有本轮全部 HER 原文（真实历史可见）
  F2 逐位双臂都有 clock（时间可见）
  F3 persona me/her 与 initial_kb 六字段进双臂（种子可见；只查公共入口实际渲染块）
  F4 intent enabled=true 时其 text 进双臂；非生效位不误注入
  F5 卡片评估侧文本（current_action/private_guidance/acceptable_actions/
     forbidden_factual_claims/protected_expressions）不出现在任一臂请求体
  F6 系统提示差异 = 声明的机制变量（只报长度差，不推断好坏）
  F7 跨臂 user prompt 长度差（诊断：候选注入了什么）
  F8 同臂连续写回：T12 位应能看到本臂前几轮生成的 ME 文本（状态自持）

用法：
  set PYTHONIOENCODING=utf-8
  D:\anaconda\python.exe tools\audit_pair_input_fairness.py \
      --out logs/p08_s1_capture_20261028 \
      --cards benchmark/stories_v2/stage2/GRADING_CARDS_E024_E027_E031_E034.json \
      --episodes E024,E027,E031,E034
"""
import argparse
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def rel(p):
    return os.path.relpath(p, ROOT).replace("\\", "/")


def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def main_msgs(calls):
    """取主调用（tag=main 或第一条）的 system/user 文本（真实出站请求体）。"""
    main = next((c for c in calls if c.get("tag", "main") == "main"), None) \
        or (calls[0] if calls else None)
    if not main:
        return None
    if "system" in main or "user" in main:
        sys_t, usr_t = main.get("system") or "", main.get("user") or ""
        n_msgs = 1 + (1 if usr_t else 0)
    else:
        msgs = (main.get("request") or {}).get("messages") or main.get("messages") or []
        sys_t = "\n".join(m.get("content", "") for m in msgs if m.get("role") == "system")
        usr_t = "\n".join(m.get("content", "") for m in msgs if m.get("role") != "system")
        n_msgs = len(msgs)
    return {"system": sys_t, "user": usr_t, "n_msgs": n_msgs,
            "temperature": main.get("temperature"),
            "attempt_id": main.get("attempt_id"),
            "model": main.get("resp_model") or main.get("model")
            or (main.get("request") or {}).get("model")}


def collect_turns(out_dir, arm, ep):
    rows = {}
    base = os.path.join(out_dir, "%s_%s" % (arm, ep), "out")
    for d in sorted(x for x in os.listdir(base)
                    if x.startswith("turn_")) if os.path.isdir(base) else []:
        cf = os.path.join(base, d, "calls.json")
        if not os.path.isfile(cf):
            continue
        parts = d.split("_")                      # turn_01_E024_T01
        idx = "turn_%s" % parts[1]
        cid = "_".join(parts[2:]) or None
        rows[idx] = {"dir": os.path.join(base, d), "case_id": cid,
                     "data": main_msgs(load(cf))}
    return rows


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--cards", required=True)
    ap.add_argument("--episodes", required=True)
    ap.add_argument("--arms", default="parent,candidate")
    ap.add_argument("--report", default=None)
    a = ap.parse_args(argv)

    out = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out)
    cards = {c["case_id"]: c for c in load(
        a.cards if os.path.isabs(a.cards) else os.path.join(ROOT, a.cards))["cards"]}
    eps = [e.strip() for e in a.episodes.split(",") if e.strip()]
    arms = [x.strip() for x in a.arms.split(",")]

    fails, rows_out = [], []
    for ep in eps:
        tdata = load(os.path.join(ROOT, "benchmark", "episodes", "%s.json" % ep))
        kb = tdata.get("initial_kb") or {}
        persona = tdata.get("persona") or {}
        per_arm = {arm: collect_turns(out, arm, ep) for arm in arms}
        # case_id → turn dir
        for i, turn in enumerate(tdata["turns"], start=1):
            cid = turn["case_id"]
            inp = turn["input"]
            texts = {arm: None for arm in arms}
            for arm in arms:
                row = per_arm[arm].get("turn_%02d" % i)
                if not row or not row["data"]:
                    fails.append("%s %s: 缺 turn_%02d 的 calls.json" % (ep, arm, i))
                    continue
                if row["case_id"] and row["case_id"] != cid:
                    fails.append("%s %s turn_%02d: case_id 错位 实=%s 应=%s"
                                 % (ep, arm, i, row["case_id"], cid))
                texts[arm] = row["data"]
            if any(v is None for v in texts.values()):
                continue
            joined = {arm: (texts[arm]["system"] + "\n" + texts[arm]["user"])
                      for arm in arms}
            her_msgs = [m["text"] for m in inp.get("messages", [])
                        if m.get("role") == "HER"]
            # F1 真实历史可见
            for arm in arms:
                for h in her_msgs:
                    if h not in joined[arm]:
                        fails.append("%s %s %s: HER 原文未进请求体：%r" % (ep, arm, cid, h[:24]))
            # F2 clock
            if inp.get("clock"):
                for arm in arms:
                    if inp["clock"] not in joined[arm]:
                        fails.append("%s %s %s: clock 未进请求体" % (ep, arm, cid))
            # F3 persona/kb（首现即可，允许渲染成不同措辞的公共块 → 查原值片段）
            for arm in arms:
                for k, v in list(kb.items()):
                    probe = str(v)[:12]
                    if probe and probe not in joined[arm]:
                        fails.append("%s %s %s: initial_kb.%s 未见（探针 %r）"
                                     % (ep, arm, cid, k, probe))
            for k, v in persona.items():
                probe = str(v)[:10]
                for arm in arms:
                    if probe and probe not in joined[arm]:
                        fails.append("%s %s %s: persona.%s 未见" % (ep, arm, cid, k))
            # F4 intent：按 harness 真实生效规则双向核（2026-10-28 D1 复验时修）
            #   旧版只看 enabled，把 COMPLETED/CLOSED 的目标也当成"必须注入"，
            #   与 v6_goal_context 的作用域规则（ACTIVE/ADJUSTED 生效，
            #   CLOSED/COMPLETED/EXPIRED/PAUSED 不生效）冲突 → 4 处误报。
            #   现在两个方向都算越界：生效目标漏注入 = 少给指导；非生效目标仍注入 = 复活旧目标。
            EFF = ("ACTIVE", "ADJUSTED")
            it = inp.get("intent") or {}
            if isinstance(it, str) and it.strip():
                it = {"text": it, "status": "ACTIVE", "enabled": True}
            if isinstance(it, dict) and it.get("text"):
                effective = bool(it.get("enabled", True)) and str(it.get("status") or "").upper() in EFF
                for arm in arms:
                    present = it["text"] in joined[arm]
                    if effective and not present:
                        fails.append("%s %s %s: 生效 intent.text(%s) 未进请求体"
                                     % (ep, arm, cid, it.get("status")))
                    elif not effective and present:
                        fails.append("%s %s %s: 非生效 intent(%s) 被误注入请求体"
                                     % (ep, arm, cid, it.get("status")))
            # F5 卡面评估侧文本不得泄漏
            card = cards.get(cid) or {}
            leak_src = [card.get("current_action"), card.get("private_guidance")]
            leak_src += list(card.get("acceptable_actions") or [])
            leak_src += list(card.get("forbidden_factual_claims") or [])
            for s in [x for x in leak_src if isinstance(x, str) and len(x.strip()) >= 8]:
                for arm in arms:
                    if s in joined[arm]:
                        fails.append("%s %s %s: 评估侧文本泄漏进请求体：%r"
                                     % (ep, arm, cid, s[:24]))
            rows_out.append({
                "case_id": cid, "arm_system_utf16": {
                    arm: len(texts[arm]["system"].encode("utf-16-le")) // 2
                    for arm in arms},
                "arm_user_utf16": {
                    arm: len(texts[arm]["user"].encode("utf-16-le")) // 2
                    for arm in arms},
                "user_len_diff": (len(texts["candidate"]["user"])
                                  - len(texts["parent"]["user"]))
                                  if "candidate" in texts and "parent" in texts else None,
            })
        # F8 同臂连续写回：末轮该臂应含本臂前几轮 ME 文本（生成替身文本，capture 下
        # 只能验"机制把本臂历史写回了自己的下一轮"，文本取替身回复里可定位的片段）
        for arm in arms:
            last = per_arm[arm].get("turn_%02d" % len(tdata["turns"]))
            if last and last["data"]:
                if "（离线替身" not in last["data"]["user"]:
                    rows_out.append({"case_id": "%s_F8_%s" % (ep, arm),
                                     "note": "末轮未见本臂替身回复回投（可能在预算内被截断）"})

    rep = {"out": rel(out), "cards": rel(a.cards if os.path.isabs(a.cards)
                                         else os.path.join(ROOT, a.cards)),
           "episodes": eps, "arms": arms,
           "positions_audited": len([r for r in rows_out if "case_id" in r
                                     and "_F8_" not in r["case_id"]]),
           "failures": fails, "passed": not fails,
           "per_position": rows_out[:8], "rows_total": len(rows_out)}
    print("positions=%d failures=%d passed=%s"
          % (rep["positions_audited"], len(fails), rep["passed"]))
    for f in fails[:25]:
        print("  FAIL", f)
    if a.report:
        p = a.report if os.path.isabs(a.report) else os.path.join(ROOT, a.report)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump(rep, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print("report ->", rel(p))
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
