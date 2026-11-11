# -*- coding: utf-8 -*-
"""test_memory_off.py — 第五波判决 §11-4 验收：--memory-off 关闭全部历史信息访问渠道。

直接驱动 beta 机制 + echo 替身（零网络），同一预置状态（facts 含猫毛过敏/房间边界、
ongoing 含室友养猫、topic/last_clock/initial_kb 非空）各跑一轮：
  memory_on  → 预置事实经检索注入 prompt、ongoing/画像/交流间隔在；
  memory_off → 上述历史渠道全部不可见于 prompt（persona 共同画像保留）。
差异清单逐项断言。"""
import json
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "arena"))
sys.path.insert(0, os.path.join(HERE, "..", "candidates", "phase0_beta_single", "mechanism"))
import lb_gateway  # noqa: E402
import lb_assembly  # noqa: E402
import mechanism as beta  # noqa: E402

FACTS = [
    # 与 L3B 前史一致：过敏事实原文含"房间"等可与 t5 窗口 LCS≥2 的子串
    {"clock": "2026-08-24 20:12", "text": "她对猫毛过敏这你是知道的吧 猫挨不得她的房间"},
    {"clock": "2026-08-24 20:18", "text": "猫要关在她房间门外呗"},
]
ONGOING = [{"item": "室友养猫", "status": "进行中"}]
PERSONA = {"她": "程汐，坐标合肥", "我": "坐标合肥，研一在读"}
INITIAL_KB = {"style": "短句口语", "her": "预置画像测试标记XYZPRESET"}


def run_once(mem_on, tmp):
    sandbox = os.path.join(tmp, "sb_%s" % ("on" if mem_on else "off"))
    os.makedirs(sandbox, exist_ok=True)
    with open(os.path.join(sandbox, "kv_facts.jsonl"), "w", encoding="utf-8") as f:
        for x in FACTS:
            f.write(json.dumps(x, ensure_ascii=False) + "\n")
    with open(os.path.join(sandbox, "kv_ongoing.json"), "w", encoding="utf-8") as f:
        json.dump(ONGOING, f, ensure_ascii=False)
    h = beta.init("candidates/phase0_beta_single/assets",
                  {"persona": PERSONA, "initial_kb": INITIAL_KB}, None, sandbox)
    h["topic"] = "养猫"
    h["topic_started_at"] = "2026-08-24 20:05"
    h["last_clock"] = "2026-10-25 09:00"
    h["hours_since_last_exchange"] = 3.5
    if not mem_on:
        beta.set_memory_access(h, False)
    gw = lb_gateway.ModelGateway(lb_gateway.EchoAdapter())
    ctx = {"input": {"clock": "2026-10-26 12:30",
                     "messages": [{"role": "HER", "text": "对了 室友想把猫放我房间晒太阳",
                                   "ts": "2026-10-26 12:25"},
                                  {"role": "HER", "text": "你说我要不要拦",
                                   "ts": "2026-10-26 12:30"}]},
           "assets_root": "candidates/phase0_beta_single/assets"}
    out = beta.run_turn(h, ctx, gw)
    user = gw.calls[-1]["user"]
    return user, out


def main():
    # 第五波判决 §8 独立反例：topic_age=72 / 真实交流间隔=0.25 → 必须渲染"不到1小时前"，
    # 话题陈旧度另用 72（写新字段、读侧仍用 topic_age 的旧缺陷复现例）
    kb = {"topic": "换手机", "topic_age_hours": 72, "hours_since_last_exchange": 0.25}
    blk = lb_assembly.build_knowledge_block(kb, "", "", 0)
    if "距上次对话：不到1小时前" not in blk:
        print("MEMORY OFF TEST FAIL：hours_since_last_exchange 读侧未接通（反例）")
        sys.exit(1)
    if "72小时前" not in blk:
        print("MEMORY OFF TEST FAIL：话题陈旧度语义丢失")
        sys.exit(1)
    tmp = tempfile.mkdtemp(prefix="lb_memoff_")
    user_on, out_on = run_once(True, tmp)
    user_off, out_off = run_once(False, tmp)
    fails = []
    # memory_on：历史渠道可见
    if "猫毛过敏" not in user_on:
        fails.append("on: 预置事实未注入（检索链路断）")
    if "房间门外" not in user_on:
        fails.append("on: 房间边界事实未注入")
    if "室友养猫" not in user_on:
        fails.append("on: ongoing 未注入")
    if "短句口语" not in user_on:
        fails.append("on: initial_kb.style 未消费（第五波判决 §8：预置资源读侧）")
    if "距上次对话" not in user_on:
        fails.append("on: hours_since_last_exchange 未渲染（第五波判决 §8：读侧接通）")
    # memory_off：历史渠道全部不可见
    for kw, label in (("猫毛过敏", "kv_facts"), ("房间门外", "kv_facts"),
                      ("室友养猫", "kv_ongoing"), ("养猫", "topic 摘要"),
                      ("距上次对话", "last_clock/交流间隔"),
                      ("XYZPRESET", "initial_kb 预置资源")):
        if kw in user_off:
            fails.append("off: 历史渠道泄漏 %s（%s）" % (label, kw))
    if "程汐" not in user_off or "研一" not in user_off:
        fails.append("off: persona 共同画像被误关（应保留）")
    if "memory_access=False" not in (out_off.get("notes") or ""):
        fails.append("off: notes 未落差异声明")
    if fails:
        print("MEMORY OFF TEST FAIL")
        for x in fails:
            print(" -", x)
        sys.exit(1)
    print("MEMORY OFF TEST PASS：memory_on 六渠道全可见；memory_off 全关闭、persona 保留；"
          "initial_kb/交流间隔读侧已接通。")


if __name__ == "__main__":
    main()
