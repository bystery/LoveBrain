# lb_epgen.py — Phase 0 长程 Episode 生成器（refs/REFACTOR_PLAN_PHASE0.md §3）
# 用途：生成 220 轮合成长剧，验证考场协议承载力与探针判别力（题库正文生产按用户令暂缓）。
# 剧情结构（全虚构脱敏，女主"小周"，男主"我"，异地→同城的普通朋友线）：
#   t010 事实埋点：她周四要打九价疫苗          → 记忆入库
#   t030 事实埋点：她家猫打翻水杯              → 记忆入库（旧事素材）
#   t060 计划埋点：约周日下午爬山              → ongoing 计划中
#   t080 stale 探针：无关天气轮，考"不得重提水杯"（机制若全量注入旧事即污染）
#   t100 事实更新：周日爬山取消                → ongoing 已取消
#   t140 更新探针：考"取消"这一更新是否被记住（爬山仍可被忆起=更新链路通）
#   t160 召回探针：考 150 轮前"九价"事实召回
#   t200-203 意图对照：同一窗口 × 调情/普通朋友/保持克制/无意图 四变体
#   t210 静默轮：她道晚安，考"不建议发送"（may_decline 契约）
# 每轮窗口只带最近 6 条消息——长程信息必须经机制记忆进入 prompt，这是判别力的来源。
# echo 台本（echo 字段）是测试替身台本，由 runner 持有，候选消毒后不可见。

import datetime
import json
import os
import sys

HER_POOL = ["哈哈哈", "睡了吗", "上课好无聊", "想吃火锅", "新剧更新了",
            "步数一万多", "舍友又在追剧", "图书馆抢座", "奶茶半价", "路灯好亮"]
ME_POOL = ["哈哈", "确实", "我也想", "冲", "早点睡", "哇", "然后呢", "可以的"]
# 池子刻意避开探针事实用字（猫/水/杯/九/价/疫/苗/爬/山/周/日/取/消），防窗口噪声


def build(n_turns: int = 220, start_clock="2026-10-20 20:00") -> dict:
    turns = []
    history = []
    clock = datetime.datetime.strptime(start_clock, "%Y-%m-%d %H:%M")

    def her(lines):
        return [{"role": "HER", "text": l} for l in lines]

    def me(lines):
        return [{"role": "ME", "text": l} for l in lines]

    def add_turn(idx, her_lines, window=None, me_lines=None, freeze=False, **kw):
        nonlocal clock
        clock += datetime.timedelta(hours=3)
        if window is None:
            window = history[-6:] + her(her_lines)
        turn = {"case_id": "GEN_t%03d" % idx, "turn_type": kw.get("turn_type", "reply"),
                "input": {"clock": clock.strftime("%Y-%m-%d %H:%M"), "messages": list(window)}}
        for k in ("note", "intent"):
            if kw.get(k):
                turn["input"][k] = kw[k]
        if kw.get("echo"):
            turn["echo"] = kw["echo"]
        if kw.get("gold"):
            turn["gold"] = kw["gold"]
        turns.append(turn)
        if not freeze:
            history.extend(her(her_lines))
            history.extend(me(me_lines if me_lines else [ME_POOL[idx % len(ME_POOL)]]))
        return turn

    def filler(idx, n=1):
        for j in range(n):
            add_turn(idx + j, [HER_POOL[(idx + j) % len(HER_POOL)]])

    i = 1
    while len(turns) < n_turns:
        idx = len(turns) + 1
        if idx == 10:
            add_turn(idx, ["跟你说个事", "我周四要去打九价疫苗，有点怕疼"],
                     me_lines=["多大点事", "勇敢一点"],
                     echo={"facts": [{"text": "她周四要打九价疫苗", "source_ids": []}]},
                     gold={"soft_keywords_any": [["九价", "疫苗"]]})
        elif idx == 30:
            add_turn(idx, ["烦死", "我家猫把水杯弄翻了"],
                     me_lines=["哈哈哈哈", "猫主子干了啥"],
                     echo={"facts": [{"text": "她家猫打翻水杯", "source_ids": []}]})
        elif idx == 50:
            add_turn(idx, ["周末一般干嘛"],
                     note="别提她前任，她刚分手不久", me_lines=["改改代码", "跑跑步"])
        elif idx == 60:
            add_turn(idx, ["对了", "周日下午去爬山吧", "天气该凉快了"],
                     me_lines=["好呀", "周日下午见"],
                     echo={"facts": [{"text": "约了周日下午爬山", "source_ids": []}],
                           "ongoing": [{"item": "周日爬山", "status": "计划中"}]})
        elif idx == 80:
            add_turn(idx, ["今天天气真好"],
                     echo={"keywords": ["水杯"]},
                     gold={"stale_fact_must_not": ["水杯"]})
        elif idx == 100:
            add_turn(idx, ["啊那个", "算了周日爬山不去了，天太热"],
                     me_lines=["好吧", "那改天再约"],
                     echo={"facts": [{"text": "周日爬山计划取消", "source_ids": []}],
                           "ongoing": [{"item": "周日爬山", "status": "已取消"}]})
        elif idx == 140:
            add_turn(idx, ["上次说周日的事怎么了来着"],
                     echo={"keywords": ["爬山"]},
                     gold={"must_keywords_any": [["爬山"]]})
        elif idx == 160:
            add_turn(idx, ["诶问你", "还记得我之前说打九价的事吗"],
                     echo={"keywords": ["九价"]},
                     gold={"must_keywords_any": [["九价"]]})
        elif idx == 200:
            # 意图对照组：同一窗口 × 四意图变体（freeze 保持窗口一致）
            w = history[-6:] + her(["在干嘛呢", "今天好累啊"])
            add_turn(200, None, window=w, freeze=True, intent="调情：适度撩她一下，让她开心",
                     echo={"keywords": ["调情"]},
                     gold={"intent_keywords_any": [["调情"]]})
        elif idx in (201, 202, 203):
            base = turns[-1]["input"]["messages"]      # 与 t200 完全同窗
            if idx == 201:
                add_turn(idx, None, window=base, freeze=True,
                         intent="普通朋友：正常关心就行，别撩",
                         echo={"keywords": []})
            elif idx == 202:
                add_turn(idx, None, window=base, freeze=True,
                         intent="保持克制：礼貌回应即可",
                         echo={"keywords": ["克制"]},
                         gold={"intent_keywords_any": [["克制"]]})
            else:
                add_turn(idx, None, window=base, freeze=True,
                         echo={"keywords": ["调情"]},
                         gold={"must_not_keywords": ["调情"]})
                history.extend(her(["在干嘛呢", "今天好累啊"]))
                history.extend(me(["哈哈刚忙完"]))
        elif idx == 210:
            add_turn(idx, ["不行困了", "我先睡了，晚安"], turn_type="silent",
                     freeze=True,
                     echo={"decline_when": ["晚安"],
                           "decline_reason": "对方已道晚安，不建议再发消息"},
                     gold={"expect_decline": True})
            history.extend(her(["不行困了", "我先睡了，晚安"]))
            history.extend(me(["晚安哈"]))
        else:
            filler(idx)

    return {"episode_id": "PHASE0_GEN_%d" % n_turns,
            "initial_kb": {},
            "note": "Phase 0 协议压测长剧（生成器产物，非正式题库）。空记忆启动，%d 轮。"
                    % n_turns,
            "turns": turns}


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 220
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "benchmark", "episodes",
        "PHASE0_GEN_220.json")
    ep = build(n)
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(ep, f, ensure_ascii=False, indent=1)
    print("episode: %s turns=%d -> %s" % (ep["episode_id"], len(ep["turns"]), out))


if __name__ == "__main__":
    main()
