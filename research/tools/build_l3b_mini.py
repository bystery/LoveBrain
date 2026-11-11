# -*- coding: utf-8 -*-
"""build_l3b_mini.py — L3B 最小实跑 episode 构造（第四波判决 §11/§12.6；
第五波判决 §6/§11-4 修订：63 天口径勘误 + 后测分离计分 gold）。

设计（最小覆盖，样例通过不外推长程能力）：
  t1-t3  前置事实段：空状态起步，HER 自然提及 S1（猫毛过敏）+ 房间边界（关门外）
         + 干扰事实（爱徒步）；候选自主决定存储（kv_facts.jsonl 为其自管文件）。
  t4     窗口驱逐段：70 条填充消息把前置事实推出 60 条渲染窗口
         （填充文本不含 猫/过敏/徒步 任何关联词）。注：填充为协议压力样例，
         不冒充自然干扰历史（第五波判决 §6.2——模型已识别为轰炸测试）。
  t5     召回测试（两项分开计分，第五波判决 §11-4）：
         must_kw_0 = 过敏约束使用（过敏/猫毛）；must_kw_1 = 房间边界回忆
         （门外/不让进/房间外——均不在当轮窗口文本中，无 window leak）。
  t6     不应回提观察位：HER 聊午饭——stale_fact_must_not [猫/过敏] 确定性断言。
时间跨距：2026-08-24 → 2026-10-26 = **63 天**（第四波"44 天"系误引旧来源包
09-12→10-26 的另一段跨度，第五波判决 §6.2 勘误）。
分支设计（第五波判决 §11-4）：不再用"有前史/无前史"CTRL episode 充当消融——
同一前史跑到冻结点（t4 末）→ 保存快照+散列 → 新进程/新目录 restore →
分支 A=完整记忆 / 分支 B=--memory-off（关闭全部历史信息访问渠道）。
见 tools/l3b_frozen_fork.py。
"""
import json

FILLERS = []
seeds = ["哈哈哈", "嗯嗯", "真的假的", "笑死", "然后呢", "我也想", "绝了", "行吧", "好家伙",
         "我承认", "有点东西", "就这？", "确实是", "牛皮", "服了", "无语", "周一下再说",
         "哦哦这样", "怪不得", "可以的"]
k = 0
while len(FILLERS) < 70:
    FILLERS.append(seeds[k % len(seeds)] + ("，" + str(k // len(seeds) + 1) if k >= len(seeds) else ""))
    k += 1


def H(text, ts, **kw):
    m = {"role": "HER", "text": text, "ts": ts}
    m.update(kw)
    return m


def M(text, ts):
    return {"role": "ME", "text": text, "ts": ts}


def T(case_id, clock, msgs, **kw):
    t = {"case_id": case_id, "input": {"clock": clock, "messages": msgs,
                                       "type": kw.pop("ttype", "normal")}}
    if kw.pop("may_decline", False):
        t["input"]["may_decline"] = True
    for key in ("intent", "meta", "gold"):
        if key in kw:
            t[key if key != "intent" else "input"].update({}) if False else None
    if "intent" in kw:
        t["input"]["intent"] = {"enabled": True, "text": kw.pop("intent")}
    if "meta" in kw:
        t["meta"] = kw.pop("meta")
    if "gold" in kw:
        t["gold"] = kw.pop("gold")
    assert not kw, kw
    return t


# ---- 前置事实段（8-24，空状态起步） ----
FACT_TURNS = [
    T("L3B_t01", "2026-08-24 20:12", [
        H("我室友今天抱回来一只猫", "2026-08-24 20:05"),
        H("然后就尴尬了", "2026-08-24 20:08"),
        H("我对猫毛过敏这你是知道的吧 挨不得", "2026-08-24 20:12"),
    ], intent="她提起室友养猫的事，陪她聊聊"),
    T("L3B_t02", "2026-08-24 20:20", [
        M("你还过敏这个呢 那咋办", "2026-08-24 20:15"),
        H("关我房间门外呗 让室友自己吸", "2026-08-24 20:18"),
        H("主要是撸不成 只能云吸", "2026-08-24 20:20"),
    ]),
    T("L3B_t03", "2026-08-25 12:40", [
        H("周末去徒步了 山顶风景绝了", "2026-08-25 12:35"),
        H("改天发你照片", "2026-08-25 12:40"),
    ], intent="她分享周末徒步，正常接"),
]

# ---- 窗口驱逐段（10-26，70 条填充） ----
EVICT_TURN = T("L3B_t04", "2026-10-26 12:00", [
    H(txt, "2026-10-26 11:%02d" % (i % 60 if i < 60 else 59))
    for i, txt in enumerate(FILLERS)
], meta={"note": "窗口驱逐段：70 条填充把前置事实推出 60 条渲染窗口；填充文本无 猫/过敏/徒步 关联词。"})

# ---- 召回测试（第五波判决 §11-4：边界回忆与过敏约束分开计分） ----
RECALL_TURN = T("L3B_t05", "2026-10-26 12:30", [
    H("对了 我室友说想把猫放我房间晒太阳", "2026-10-26 12:25"),
    H("你说我要不要拦", "2026-10-26 12:30"),
], intent="帮我看看怎么回",
   gold={"must_keywords_any": [["过敏", "猫毛"], ["门外", "不让进", "房间外", "不进屋"]],
         "expected_facts_note": "must_kw_0=过敏约束使用；must_kw_1=房间边界回忆（8-24 '关我房间门外'）；"
                                "关键词均不在当轮窗口文本中。分支持有记忆与否的结果分开报告，"
                                "关记忆分支未命中属预期消融结果，不作质量扣分。"})
# ---- 不应回提观察位 ----
NORECALL_TURN = T("L3B_t06", "2026-10-26 18:20", [
    H("今天食堂的麻辣香锅排了好长的队", "2026-10-26 18:15"),
    H("不过味道还行", "2026-10-26 18:20"),
], gold={"stale_fact_must_not": ["猫", "过敏"]},
   meta={"note": "不应回提观察位：主动提猫/过敏=过度回提（stale 探针确定性断言）。"})

PERSONA = {"她": "程汐，坐标合肥，上班族", "我": "坐标合肥，研一在读",
           "关系": "师兄聚会上认识的同龄人，认识两个月，偶尔聊天"}

FULL_EP = {"episode_id": "L3B_MINI_FULL", "hero": "程汐",
           "relation_stage": "新识普通朋友",
           "persona": PERSONA, "initial_kb": {}, "gaps": [],
           "turns": FACT_TURNS + [EVICT_TURN, RECALL_TURN, NORECALL_TURN]}

CONTROL_EP = {"episode_id": "L3B_MINI_CTRL", "hero": "程汐",
              "relation_stage": "新识普通朋友",
              "persona": PERSONA, "initial_kb": {}, "gaps": [],
              "turns": [EVICT_TURN, RECALL_TURN, NORECALL_TURN]}

if __name__ == "__main__":
    json.dump(FULL_EP, open("benchmark/episodes/L3B_MINI_FULL.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    json.dump(CONTROL_EP, open("benchmark/episodes/L3B_MINI_CTRL.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("L3B mini episodes written")
