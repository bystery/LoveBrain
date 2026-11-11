# candidates/neg_b_dumper — 负对照 B：全量记忆复述（Phase 0.5 判别力测试）
# 设计：把听到的一切全存全注入——每条消息文本 + 每条 analysis 事实都进 scene_chain，
#       无检索、无遗忘、无相关性控制。单阶段调用。
# 预期：召回探针 PASS（什么都注入了），污染探针（水杯）应 FAIL——旧事被原文搬进 prompt，
#       模型/echo 顺势复述 = 记忆污染。这正是"全量保留"架构的原罪。
# 若本候选污染探针仍 PASS → 考场对"旧事重提"无判别力。

import json
import os
import sys

_ARENA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "arena")
sys.path.insert(0, _ARENA)
import lb_assembly          # noqa: E402
import protocol_public            # noqa: E402


def init(assets_root, episode_meta, gateway, sandbox):
    return {"assets_root": assets_root, "sandbox": sandbox, "facts": []}


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
                "notes": "neg_b: silent turn, no model call", "id_map": {}}
    # 全量收集：本窗口每条 HER 消息文本都当作"事实"全量注入
    for m in ai.get("messages", []):
        if isinstance(m, dict) and m.get("role") == "HER" and m.get("text"):
            h["facts"].append({"clock": ai.get("clock", ""), "text": m["text"]})
    kb = {"scene_chain": "\n".join("- [%s] %s" % (f["clock"], f["text"])
                                   for f in h["facts"]),
          "ongoing_plan": "", "topic": "", "topic_age_hours": 99}
    ai2 = dict(ai)
    ai2["kb"] = kb
    assembled = lb_assembly.build_reply_prompts(ctx.get("assets_root") or h["assets_root"], ai2)
    res = gw.generate(assembled["system"], assembled["user"], tag="main")
    parsed = protocol_public.extract_json_block(res["text"])
    if parsed and isinstance(parsed.get("analysis"), dict):
        for f in parsed["analysis"].get("scene_facts") or []:
            t = (f.get("text") or "").strip() if isinstance(f, dict) else ""
            if t:
                h["facts"].append({"clock": ai.get("clock", ""), "text": t})
    return {"reply": res["text"], "notes": "neg_b: full dump, %d facts injected" % len(h["facts"]),
            "id_map": assembled["id_map"]}


def snapshot(h):
    return json.dumps(h, ensure_ascii=False).encode("utf-8")


def restore(sandbox, snap):
    h = json.loads(snap.decode("utf-8"))
    h["sandbox"] = sandbox
    h["assets_root"] = None
    return h
