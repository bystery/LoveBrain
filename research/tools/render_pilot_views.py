#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""双视图渲染器 v2（判决令 2026-10-18 第四波 §7）。

从同一份 episode JSON 渲染两种视图，禁止单独加工：
  A 纯微信记录：跨日显示完整日期、沉默区间（含 gap）显式渲染 —— 供连续阅读审人机感。
  B 结构化考题版：节点号/类型/意图/媒体/评估侧备注/隐藏事实 + 输入边界说明
    （persona/消息 ts/may_decline 为候选可见；meta/gaps/gold 为评估侧）。
v2 修复：gap 实际进入 events（v1 排序键存在但 gap 从未 append，两视图均漏渲染）；
A 视图跨日显示日期；B 视图头补输入边界声明。
"""
import json, sys, os

SPEAKER = {"HER": "她", "ME": "我"}


def _fmt_ts(ts):
    """跨日显示完整日期，同日只显示 HH:MM。"""
    d, hm = ts.split(" ")
    return "%s %s" % (d, hm)


def render(eps):
    A, B = [], []
    for ep in eps:
        persona = ep.get("persona") or {}
        A.append("# %s × %s（%s）\n" % ("我", ep["hero"], ep["relation_stage"]))
        B.append("# %s · 结构化考题版\n\n> 请求位 %d | gap %d\n" % (
            ep["episode_id"], len(ep["turns"]), len(ep.get("gaps", []))))
        B.append("> **输入边界（判决令 §2.1 白名单）**：候选可见 = input{messages(含 ts), clock, "
                 "intent, may_decline, note} + persona + initial_kb；评估侧 = meta/gaps/gold。"
                 "persona 下发内容：" + json.dumps(persona, ensure_ascii=False) + "\n")
        # gap 事件真正进入 events（v1 bug 修复）
        events = [("turn", t) for t in ep["turns"]]
        events += [("gap", g) for g in ep.get("gaps", [])]
        events.sort(key=lambda e: (e[1]["input"]["messages"][0]["ts"]
                                   if e[0] == "turn" else e[1]["from"]))
        last_date = None
        last_ts = None
        for kind, obj in events:
            if kind == "gap":
                A.append("\n—— %s 至 %s（没有消息）——\n" % (obj["from"], obj["to"]))
                B.append("\n## gap · %s → %s\n> 评估侧备注：%s\n" % (obj["from"], obj["to"], obj.get("note", "")))
                last_ts = obj["to"]
                last_date = obj["to"].split(" ")[0]
                continue
            t = obj
            inp = t["input"]
            meta = t.get("meta", {})
            B.append("\n## %s · 请求时间=%s · 类型=%s%s\n" % (
                t["case_id"], inp["clock"], inp.get("type", "normal"),
                " · may_decline" if inp.get("may_decline") else ""))
            if (inp.get("intent") or {}).get("enabled"):
                B.append("> 意图（候选可见）：%s\n" % inp["intent"]["text"])
            media_reg = (inp.get("media") or {}).get("images") or []
            msg_imgs = [m.get("image") for m in inp["messages"]
                        if isinstance(m, dict) and m.get("image")]
            if media_reg or msg_imgs:
                # 第六波判决 §11：B 视图显示媒体登记明细（登记↔引用 双向对照+可见性）
                B.append("> 媒体登记（input.media.images）：%s\n"
                         % json.dumps(media_reg, ensure_ascii=False))
                B.append("> 消息图片引用（进入 image_url 通道）：读图测试位=%s；"
                         "登记↔引用双向校验由 validate_pilot_timeline 强制\n"
                         % ("、".join(m["ts"] for m in inp["messages"]
                                      if isinstance(m, dict) and m.get("image"))))
            if inp.get("may_decline"):
                B.append("> 允许不回（候选可见标记，已注入 prompt）\n")
            if meta.get("delay"):
                B.append("> 延迟：%s\n" % meta["delay"])
            if meta.get("note"):
                B.append("> 评估侧备注（不入候选）：%s\n" % meta["note"])
            if meta.get("hidden"):
                B.append("> 隐藏事实（候选不可见）：%s\n" % meta["hidden"])
            A.append("")
            for m in inp["messages"]:
                ts = m["ts"]
                date = ts.split(" ")[0]
                if date != last_date:
                    A.append("\n—— %s ——\n" % date)
                    last_date = date
                A.append("%s（%s）：%s" % (SPEAKER[m["role"]], ts[11:16], m["text"]))
                B.append("- [%s] %s `%s`%s" % (m["role"], ts, m["text"],
                                               " [图片资产]" if m.get("image") else ""))
                last_ts = ts
            # 请求点与消息末尾的时间差（沉默区间，跨请求也可见）
            if last_ts and inp["clock"] > last_ts:
                A.append("（……直到 %s）" % inp["clock"])
        B.append("\n---\n")
        A.append("\n---\n")
    return "\n".join(A), "\n".join(B)


def main():
    eps = [json.load(open(p, encoding="utf-8")) for p in sys.argv[1:]]
    a, b = render(eps)
    os.makedirs("refs", exist_ok=True)
    open("refs/PILOT_VIEWS_A_clean.md", "w", encoding="utf-8").write(
        "# Pilot 纯微信聊天记录（视图 A，自动生成，无任何考题标注；跨日显示日期与沉默区间）\n\n" + a)
    open("refs/PILOT_VIEWS_B_structured.md", "w", encoding="utf-8").write(
        "# Pilot 结构化考题版（视图 B，自动生成，与视图 A 同源；含输入边界声明）\n\n" + b)
    print("视图 A/B 已生成（%d 个 episode）" % len(eps))


if __name__ == "__main__":
    main()
