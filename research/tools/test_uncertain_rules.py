# -*- coding: utf-8 -*-
"""tools/test_uncertain_rules.py — W16 未定标记规则离线单测（零模型调用）。

覆盖设计 §6 的 6 组正反例：未定→注入 / 已确认→不注入 / 问句无世界动词→不注入 /
超预算→截断 / 预算 CU 上限 / 无未定→空（prompt 与父本等同的前提）。
用法：python tools/test_uncertain_rules.py
"""
import importlib.util
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATH = os.path.join(ROOT, "candidates", "window_expand_b_norecap_uc", "mechanism", "uncertain_rules.py")
spec = importlib.util.spec_from_file_location("unc_test", PATH)
u = importlib.util.module_from_spec(spec)
spec.loader.exec_module(u)

FAILED = []


def check(name, cond, detail=""):
    print(("  OK  " if cond else "  FAIL") + " " + name + ("" if cond else "  <- " + detail))
    if not cond:
        FAILED.append(name)


def ev(text, turn):
    return {"uid": turn, "role": "HER", "text": text, "ts": "", "turn": turn}


def main():
    print("[1] 未定（引号转述/hedge）→ 注入")
    prior = [ev("我舅说「网上买的不保险，去门店换块原装电池」，我有点打鼓。", 3)]
    cur1 = [{"role": "HER", "text": "网上买的到底保不保险？", "ts": ""}]
    block, items, cu = u.build_block(prior, cur1, 4)
    check("检测到引号转述", len(items) >= 1, str(items))
    check("块含逐字原文", "网上买的不保险" in block, block)
    check("块含 T 标注", "（T3）" in block, block)
    hedge_prior = [ev("我舅可能觉得门店贵点，但还没准。", 3)]
    cur2 = [{"role": "HER", "text": "门店那边我舅觉得贵不贵？", "ts": ""}]
    check("检测到 hedge", len(u.find_uncertain(hedge_prior, cur2, 4)) >= 1)

    print("[2] 后续已确认 → 不注入")
    prior = [ev("我舅可能想换到四千六那家门店。", 2), ev("定了，就在四千六那家门店，已经付款。", 3)]
    block, items, cu = u.build_block(prior, [], 4)
    check("确认后关闭", items == [], str(items))

    print("[3] 本轮未确认问句 → 注入")
    cur = [{"role": "HER", "text": "这趟一百六小满转你了没？她要忘了你直接跟我说。", "ts": ""}]
    block, items, cu = u.build_block([], cur, 22)
    check("检测到问句", any("小满转你了没" in i["text"] for i in items), str(items))
    check("标注本轮", "（本轮）" in block, block)

    print("[4] 问句但无世界状态动词 → 不注入")
    cur = [{"role": "HER", "text": "这钱省得省不得？"}]
    block, items, cu = u.build_block([], cur, 14)
    check("无世界动词不触发", items == [], str(items))

    print("[5] 超预算/超条数 → 截断")
    prior = [ev("如果明天那笔一百八的配件到了你先看看。", i) for i in range(1, 7)]
    cur5 = [{"role": "HER", "text": "一百八的配件到了没？", "ts": ""}]
    block, items, cu = u.build_block(prior, cur5, 7)
    check("条数<=3", len(items) <= 3, str(len(items)))
    check("CU<=120", cu <= u.UNCERTAIN_BUDGET_CU, str(cu))

    print("[6] 无未定 → 空块")
    block, items, cu = u.build_block([ev("今天菜市场的菜很新鲜。", 1)], [], 2)
    check("空块", block == "" and items == [] and cu == 0, repr(block))

    print("[7] 低重合确认不误关")
    prior = [ev("如果周末去爬山，可能要早点出发。", 1), ev("我买了双新鞋。", 2)]
    cur7 = [{"role": "HER", "text": "周末爬山几点出发？", "ts": ""}]
    block, items, cu = u.build_block(prior, cur7, 3)
    check("仍视为未定", len(items) >= 1, str(items))

    print("[8] 纯函数：不修改入参")
    prior = [ev("如果明天下雨就改到后天。", 1)]
    snap = [dict(x) for x in prior]
    u.build_block(prior, [], 2)
    check("入参未变", prior == snap, str(prior))

    print("\n%s" % ("ALL PASSED" if not FAILED else "FAILED: %s" % FAILED))
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
