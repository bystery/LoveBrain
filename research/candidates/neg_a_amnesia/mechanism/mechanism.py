# candidates/neg_a_amnesia — 负对照 A：无长期记忆（Phase 0.5 判别力测试）
# 设计：完全不存记忆，每轮只看当前窗口（kb 全空）。单阶段单次调用。
# 预期：召回探针（爬山/九价等 must_keywords）应 FAIL——历史事实它根本拿不到；
#       污染探针（水杯）应 PASS（不会提旧事，因为什么都不记得）。
# 若本候选在召回探针上仍拿高分 → 考场判别力 FAIL（说明分数不来自记忆能力）。

import json
import os
import sys

_ARENA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "arena")
sys.path.insert(0, _ARENA)
import lb_assembly          # noqa: E402
import protocol_public            # noqa: E402


def init(assets_root, episode_meta, gateway, sandbox):
    return {"assets_root": assets_root, "sandbox": sandbox}


def _decline_json(reason):
    return json.dumps({"response": {"recommended": None, "bad_boy": None,
                                    "playful": None, "warm": None},
                       "decline_reason": reason, "directions": [None] * 4,
                       "analysis": {"topic_status": "end", "topic_label": "收尾",
                                    "scene_facts": [], "ongoing": []}},
                      ensure_ascii=False)


def run_turn(h, ctx, gw):
    ai = ctx["input"]
    if ctx.get("turn_type") == "silent":
        return {"reply": _decline_json("对方已结束话题，不建议再发消息"),
                "notes": "neg_a: silent turn, no model call", "id_map": {}}
    ai2 = dict(ai)
    ai2["kb"] = {}          # 无任何记忆注入
    assembled = lb_assembly.build_reply_prompts(ctx.get("assets_root") or h["assets_root"], ai2)
    res = gw.generate(assembled["system"], assembled["user"], tag="main")
    return {"reply": res["text"], "notes": "neg_a: no memory, single call",
            "id_map": assembled["id_map"]}


def snapshot(h):
    return json.dumps({"v": 1}).encode("utf-8")


def restore(sandbox, snap):
    return {"assets_root": None, "sandbox": sandbox}
