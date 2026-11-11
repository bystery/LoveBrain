# -*- coding: utf-8 -*-
"""test_scene_assembly.py — infer_scene v12 复核 §6B 整改的正反例测试。

覆盖：
  正例（必须命中）：
    - 跨消息收尾："先不聊了"+"我去赶班车 明天说"（NCA_t06 实测）
                  "先这样"+"明天返程再说"（NCB_t05 实测）
    - 单句收尾回归："睡了"/"我去洗澡了" 仍判收尾
    - 外部坏消息不判争执："唉 我领导说项目黄了/可能要调岗/烦死了"（NCA_t03 实测）→ 认真沟通
  反例（不得误判）：
    - "明天聊什么"含"明天聊"但会话继续 → 不得判收尾
    - 最后一条以问号结尾的"明天说？"式追问 → 不得判收尾
    - "你每次都这样"无第三方词 → 仍判争执（不许过度修正）
    - "跟你说话真烦"无第三方词 → 仍判争执
    - 普通闲聊仍为日常分享
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "arena"))
from lb_assembly import infer_scene  # noqa: E402


def M(pairs):
    return [{"role": r, "text": t} for r, t in pairs]


CASES = [
    # (名称, messages, 期望场景)
    ("跨消息收尾·赶车", M([("HER", "先不聊了"), ("HER", "我去赶班车 明天说")]), "收尾"),
    ("跨消息收尾·返程", M([("HER", "婚礼忙完了 我回酒店了"), ("HER", "先这样 明天返程再说")]), "收尾"),
    ("单句收尾·睡了", M([("HER", "我先睡了 晚安")]), "收尾"),
    ("单句收尾·洗澡", M([("HER", "我去洗澡了")]), "收尾"),
    ("外部坏消息非争执", M([("HER", "唉 我领导说项目黄了"), ("HER", "可能要调岗"), ("HER", "烦死了")]), "认真沟通"),
    ("追问反例·明天聊什么", M([("HER", "先这样"), ("HER", "明天聊什么好啊")]), "日常分享"),
    ("问号反例·明天说？", M([("HER", "明天说？你现在有空吗")]), "主动邀约"),
    ("真争执·你每次都这样", M([("HER", "你每次都这样 说好的一起去又放我鸽子")]), "争执"),
    ("真争执·跟你说话真烦", M([("HER", "跟你说话真烦")]), "争执"),
    ("普通闲聊默认", M([("HER", "周末去了趟良渚 人少景好"), ("HER", "拍了好多图 回头挑几张发你")]), "日常分享"),
    ("收尾优先于邀约词", M([("HER", "周末再约"), ("HER", "我先去忙了 改天再聊")]), "收尾"),
    # v13 复核 §6 同族闭合四例
    ("第三方催促非争执·领导又来催", M([("HER", "领导又来催项目了 真烦")]), "认真沟通"),
    ("礼貌问句可收尾", M([("HER", "我先睡了 明天聊好吗？")]), "收尾"),
    ("晚安后ME重开话题", M([("HER", "晚安"), ("ME", "对了 周末去哪"), ("HER", "去良渚")]), "日常分享"),
    ("收尾后HER补新事重开", M([("HER", "先不聊了"), ("HER", "等等 刚才出结果了 我升职了")]), "日常分享"),
    ("指向ME的又来仍争执", M([("HER", "你又来了 每次都这样")]), "争执"),
]


def main():
    fails = []
    for name, msgs, expect in CASES:
        got = infer_scene(msgs)
        ok = got == expect
        print("%s %-24s got=%-6s expect=%s" % ("PASS" if ok else "FAIL", name, got, expect))
        if not ok:
            fails.append(name)

    # 冻结 episode 回归：66 槽场景标签抽查（整改目标位）
    import json
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    ep = json.load(open(os.path.join(root, "benchmark/episodes/NCA_CORR_INTENT_v1.json"), encoding="utf-8"))
    ncb = json.load(open(os.path.join(root, "benchmark/episodes/NCB_ENTITY_DENSE_v1.json"), encoding="utf-8"))
    real = {}
    for d in (ep, ncb):
        for t in d["turns"]:
            real[t["case_id"]] = infer_scene([{"role": m["role"], "text": m["text"]}
                                              for m in t["input"]["messages"]])
    want = {"NCA_t06": "收尾", "NCB_t05": "收尾", "NCA_t03": "认真沟通"}
    for cid, exp in want.items():
        got = real[cid]
        ok = got == exp
        print("%s 实测槽 %-8s got=%-6s expect=%s" % ("PASS" if ok else "FAIL", cid, got, exp))
        if not ok:
            fails.append(cid)
    print("---")
    print("场景标签全表:", {k: real[k] for k in sorted(real)})
    if fails:
        raise SystemExit("FAIL: %s" % fails)
    print("全部 PASS")


if __name__ == "__main__":
    main()
