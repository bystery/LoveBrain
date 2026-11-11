# -*- coding: utf-8 -*-
r"""tools/probe_r3_fix9.py — FIX-9 零调用离线自检：菜单级"修复前后"逐位对照。

要证的事（不靠模型采样，纯机制）：
  1) action_policy_r3 能否把"不回"从 3 类不该提供的位点摘掉，同时在 2 类合法不回位点保留；
  2) r3 的动作块文本手术有没有静默失败（父本文案漂移→ _R3_CONTRACT_MISS 必须为空）；
  3) 清单无 '停' 时，合同是否直接要求"正文非空"；有 '停' 时，是否保留 FIX-6 实测通过的
     action=no_reply 写法；检测到成果/求助时，【必须接住】是否出现。

做法：独立加载 r2 与 r3 两条链（每次 importlib 都是全新模块实例，互不污染），
对每个真实题面轮次用**该轮真实输入**（messages/clock/may_decline/intent）调
_signal → _capabilities → _action_block，把两臂的菜单与合同文本并排落盘。
stdout 只打 ASCII（Windows 控制台 GBK 打不了 CJK），中文全部进 UTF-8 JSON。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/probe_r3_fix9.py
"""
import importlib.util
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, ROOT)

OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "deadline_quality_20261028", "PROBE_R3_FIX9_20261028.json")

SILENCE_R2 = ("收尾", "停")   # r2 语义：这两个动作名都=本轮不发任何消息
SILENCE_R3 = ("停",)          # r3 语义：不回只有"停"，"收尾"是发一句短消息


def load_mech(cand):
    p = os.path.join(ROOT, "candidates", cand, "mechanism", "mechanism.py")
    spec = importlib.util.spec_from_file_location("probe_mod_" + cand, p)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def v6_of(mod):
    """取该候选链真正使用的 v6_goal_context 模块对象（被逐层重绑的那一份）。"""
    m = mod
    while hasattr(m, "v6g"):
        inner = m.v6g
        if inner is m:
            break
        m = inner
        if hasattr(m, "v5"):
            return m
    return mod.v6g


def turns_for(episodes):
    rows = []
    for ep in episodes:
        d = json.load(io.open(os.path.join(ROOT, "benchmark", "episodes", ep + ".json"),
                              encoding="utf-8"))
        kb = d.get("initial_kb") or {}
        for t in d["turns"]:
            inp = t["input"]
            rows.append({"episode": ep, "case_id": t["case_id"],
                         "clock": inp.get("clock"), "may_decline": inp.get("may_decline"),
                         "intent": inp.get("intent") or {},
                         "messages": inp.get("messages") or [],
                         "kb_empty_scalar": all(isinstance(v, str) for v in kb.values())})
    return rows


def menu(v6, row, silence_actions):
    gs = v6._goal_state(row["intent"], row["clock"])
    sig = mod_v6_sig(v6, row["messages"], row["may_decline"])
    caps = v6._capabilities(sig, gs["effective"])
    block = v6._action_block(sig, caps, gs)
    her_last = ""
    for m in reversed(row["messages"]):
        if (m.get("role") or "").upper() in ("HER", "PARTNER"):
            her_last = m.get("text") or ""
            break
    return {"hint": sig.get("hint"), "may_decline": bool(sig.get("may_decline")),
            "goal_effective": gs["effective"], "caps": caps,
            "silence_available": any(a in caps for a in silence_actions),
            "stop_in_caps": "停" in caps,
            "waiver": sig.get("waiver"), "result_report": sig.get("result_report"),
            "open_ask": sig.get("open_ask"), "silence_ok": sig.get("silence_ok"),
            "her_last": her_last,
            "block_has_must_reply": "本轮必须回" in block,
            "block_has_nr_write": ("no_reply" in block),
            "block_has_must_catch": "必须接住" in block,
            "block_has_empty_body_rule": "必须完全为空" in block,
            "block_tail": block[-900:]}


def mod_v6_sig(v6, msgs, may_decline):
    cur = [{"role": (m.get("role") or "").upper(), "text": m.get("text") or "",
            "ts": m.get("ts") or ""} for m in msgs]
    return v6._signal(cur, may_decline)


def main():
    episodes = sys.argv[1:] or ["D01", "D02", "AK09", "AK12"]
    rows = turns_for(episodes)

    m2 = load_mech("action_policy_r2")
    v62 = v6_of(m2)
    m3 = load_mech("action_policy_r3")
    v63 = v6_of(m3)

    out = []
    for row in rows:
        b = menu(v62, row, SILENCE_R2)      # r2 语义：收尾/停 都是不回
        a = menu(v63, row, SILENCE_R3)      # r3 语义：只有 停 是不回
        changed = (b["caps"] != a["caps"]) or (b["silence_available"] != a["silence_available"])
        out.append({"case_id": row["case_id"], "may_decline": row["may_decline"],
                    "hint_r2": b["hint"], "hint_r3": a["hint"],
                    "caps_r2": b["caps"], "caps_r3": a["caps"],
                    "silence_available_r2": b["silence_available"],
                    "silence_available_r3": a["silence_available"],
                    "waiver": a["waiver"], "result_report": a["result_report"],
                    "open_ask": a["open_ask"], "silence_ok": a["silence_ok"],
                    "menu_changed": changed,
                    "must_reply_text_r3": a["block_has_must_reply"],
                    "must_catch_text_r3": a["block_has_must_catch"],
                    "nr_write_text_r3": a["block_has_nr_write"],
                    "legacy_empty_body_text_r3": a["block_has_empty_body_rule"],
                    "her_last": row["messages"][-1].get("text") if row["messages"] else ""})

    misses = {"r3_contract_miss": list(getattr(m3, "_R3_CONTRACT_MISS", [])),
              "r3_caps_miss": list(getattr(m3, "_R3_CAPS_MISS", [])),
              "r3_signal_miss": list(getattr(m3, "_R3_SIGNAL_MISS", [])),
              "r2_contract_miss_in_chain": list(getattr(m3.apr2, "_R2_CONTRACT_MISS", []))}

    # ---- 预登记断言：每张卡的期望（写在这里=先声明再跑，不做事后解释） ----
    # 预登记期望（D03 为定向复验新材料：成果+推后细节、拒绝要接住、委屈无免回 → 不给不回；
    # 明确"别等"、明确"结果出来我再说" → 保留不回）
    expect_no_silence = ["D01_T08", "D02_T08", "D02_T06",
                         "D03_T02", "D03_T03", "D03_T05"]
    expect_silence_kept = ["AK09_T01", "AK12_T01", "D03_T04", "D03_T06"]
    hazard_cells = []            # r2 给了不回语义但 may_decline=false
    for r in out:
        if r["silence_available_r2"] and r["may_decline"] is False:
            hazard_cells.append(r["case_id"])
    checks = []
    by = {r["case_id"]: r for r in out}
    for cid in expect_no_silence:
        r = by.get(cid)
        checks.append({"claim": "%s r3 不再提供不回" % cid,
                       "pass": bool(r) and not r["silence_available_r3"],
                       "detail": (r or {}).get("caps_r3")})
    for cid in expect_silence_kept:
        r = by.get(cid)
        checks.append({"claim": "%s r3 仍提供合法不回（防反向退化）" % cid,
                       "pass": bool(r) and ("停" in (r.get("caps_r3") or [])),
                       "detail": {"caps": (r or {}).get("caps_r3"),
                                  "waiver": (r or {}).get("waiver"),
                                  "result": (r or {}).get("result_report"),
                                  "ask": (r or {}).get("open_ask")}})
    for cid in hazard_cells:
        r = by.get(cid)
        checks.append({"claim": "%s 菜单泄漏已消除（may_decline=false 不给不回）" % cid,
                       "pass": bool(r) and not r["silence_available_r3"],
                       "detail": (r or {}).get("caps_r3")})
    checks.append({"claim": "父本文案手术无静默漂移（_R3_*_MISS 全空）",
                   "pass": not misses["r3_contract_miss"] and not misses["r3_caps_miss"]
                           and not misses["r3_signal_miss"],
                   "detail": misses})
    legacy_hits = [r["case_id"] for r in out if r["legacy_empty_body_text_r3"]]
    checks.append({"claim": "r3 里不再出现 r1 遗留的'正文必须完全为空'文案",
                   "pass": not legacy_hits, "detail": legacy_hits})

    payload = {"kind": "probe_r3_fix9_v1",
               "episodes": episodes,
               "loaded_flags": {"action_policy_r2": getattr(m2, "_FLAGS", None),
                                "action_policy_r3": getattr(m3, "_FLAGS", None),
                                "narrow_active": bool(getattr(m3, "_NARROW", False))},
               "semantics_note": ("silence_available 按各版本真实语义统计：r2 里 '收尾'/'停' 都="
                                  "本轮不发任何消息；r3 里只有 '停' 是不回，'收尾' 是发一句短消息"),
               "positions": out,
               "hazard_cells_r2": hazard_cells,
               "miss_registers": misses,
               "checks": checks,
               "all_ok": all(c["pass"] for c in checks)}
    json.dump(payload, io.open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    print("WROTE", OUT.encode("ascii", "backslashreplace").decode())
    print("positions=%d menu_changed=%d hazard_r2=%d all_ok=%s"
          % (len(out), sum(1 for r in out if r["menu_changed"]),
             len(hazard_cells), payload["all_ok"]))
    for c in checks:
        print("CHECK %s -> %s" % ("PASS" if c["pass"] else "FAIL",
                                  c["claim"].encode("ascii", "backslashreplace").decode()))
    for r in out:
        if r["menu_changed"] or r["case_id"] in expect_silence_kept:
            print("CELL %s md=%s r2=%s r3=%s waiver=%s result=%s ask=%s"
                  % (r["case_id"], r["may_decline"],
                     ",".join(r["caps_r2"]), ",".join(r["caps_r3"]),
                     r["waiver"], r["result_report"], r["open_ask"]))
    return 0 if payload["all_ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
