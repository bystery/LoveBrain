# candidates/neg_c_service — 负对照 C：客服化人机感（Phase 0.5 判别力测试）
# 设计：机制层正常（有记忆、格式完美），但把每条可发送话术包上客服腔：
#       "感谢您的分享！……请问还有什么可以帮您的吗？"——回复永远主动提问、永远礼貌周到。
#       记忆注入策略与 β 相同（检索式），隔离"风格差"这一个变量。
# 预期：召回/污染探针与 β 同分；分差必须来自 ai_flavor / style_diversity / note_lcs 等
#       自然度维度。若客服腔拿满分 → 评分器对"人机感"无判别力（P0 缺口实锤，须修词表）。

import json
import os
import sys

_ARENA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "arena")
sys.path.insert(0, _ARENA)
import lb_assembly          # noqa: E402
import protocol_public            # noqa: E402

FACTS_FILE = "kv_facts.jsonl"
LCS_MIN = 2
TOPK = 6


def _lcs_len(a, b):
    if not a or not b:
        return 0
    prev = [0] * (len(b) + 1)
    best = 0
    for i in range(1, len(a) + 1):
        cur = [0] * (len(b) + 1)
        for j in range(1, len(b) + 1):
            cur[j] = prev[j - 1] + 1 if a[i - 1] == b[j - 1] else max(cur[j - 1], prev[j])
            best = max(best, cur[j])
        prev = cur
    return best


def _load_facts(h):
    p = os.path.join(h["sandbox"], FACTS_FILE)
    if not os.path.exists(p):
        return []
    with open(p, "r", encoding="utf-8") as f:
        return [json.loads(x) for x in f if x.strip()]


def init(assets_root, episode_meta, gateway, sandbox):
    return {"assets_root": assets_root, "sandbox": sandbox}


def _decline_json(reason):
    return json.dumps({"response": {"recommended": None, "bad_boy": None,
                                    "playful": None, "warm": None},
                       "decline_reason": reason, "directions": [None] * 4,
                       "analysis": {"topic_status": "end", "topic_label": "收尾",
                                    "scene_facts": [], "ongoing": []}},
                      ensure_ascii=False)


def _servicify(t):
    """客服腔包装：感谢开头 + 请示结尾，内容关键词原样保留。"""
    if not isinstance(t, str) or not t.strip():
        return t
    return "感谢您的分享！关于「%s」，我为您整理了贴心建议。请问还有什么可以帮您的吗？" % t


def run_turn(h, ctx, gw):
    ai = ctx["input"]
    if ctx.get("turn_type") == "silent":
        return {"reply": _decline_json("对方已结束话题，不建议再发消息"),
                "notes": "neg_c: silent turn, no model call", "id_map": {}}
    window = "".join(m.get("text", "") for m in ai.get("messages", []) if isinstance(m, dict))
    facts = _load_facts(h)
    retrieved = [f for f in facts if _lcs_len(window, f["text"]) >= LCS_MIN][:TOPK]
    kb = {"scene_chain": "\n".join("- [%s] %s" % (f["clock"], f["text"]) for f in retrieved),
          "ongoing_plan": "", "topic": "", "topic_age_hours": 99}
    ai2 = dict(ai)
    ai2["kb"] = kb
    assembled = lb_assembly.build_reply_prompts(ctx.get("assets_root") or h["assets_root"], ai2)
    res = gw.generate(assembled["system"], assembled["user"], tag="main")
    parsed = protocol_public.extract_json_block(res["text"])
    if parsed:
        resp = parsed.get("response") or {}
        for k in list(resp.keys()):
            resp[k] = _servicify(resp[k])
        dirs = parsed.get("directions")
        if isinstance(dirs, list):
            parsed["directions"] = [_servicify(d) if isinstance(d, str) else d for d in dirs]
        ana = parsed.get("analysis") or {}
        clock = ai.get("clock", "")
        for f in ana.get("scene_facts") or []:
            t = (f.get("text") or "").strip() if isinstance(f, dict) else ""
            if t:
                with open(os.path.join(h["sandbox"], FACTS_FILE), "a", encoding="utf-8") as fo:
                    fo.write(json.dumps({"clock": clock, "text": t}, ensure_ascii=False) + "\n")
        out = json.dumps(parsed, ensure_ascii=False)
    else:
        out = res["text"]
    return {"reply": out, "notes": "neg_c: service-tone wrapper, retrieval memory",
            "id_map": assembled["id_map"]}


def snapshot(h):
    parts = []
    p = os.path.join(h["sandbox"], FACTS_FILE)
    data = open(p, "rb").read() if os.path.exists(p) else b""
    return FACTS_FILE.encode("utf-8") + b"\n" + data + b"\n--SNAP--\n"


def restore(sandbox, snap):
    h = {"assets_root": None, "sandbox": sandbox}
    block = snap.split(b"\n--SNAP--\n")[0]
    nl = block.index(b"\n")
    fn = block[:nl].decode("utf-8")
    with open(os.path.join(sandbox, fn), "wb") as f:
        f.write(block[nl + 1:])
    return h
