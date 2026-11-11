# candidates/probe_ctx — Phase 0.5 gold 边界动态探针（测试专用，禁用于正式评测）
# 把机制实际收到的 ctx 全量序列化进 notes，验证候选可见边界里没有 gold/台本。
import json, os, sys
_ARENA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "arena")
sys.path.insert(0, _ARENA)

GOLD_MARKERS = ("gold", "stale_fact_must_not", "must_keywords_any", "intent_keywords_any",
                "expect_decline", "geo_impossible_must_not", "mock_response", "echo",
                "design_dna", "answer", "expected_topic")

def init(assets_root, episode_meta, gateway, sandbox):
    return {"assets_root": assets_root, "sandbox": sandbox, "report": []}

def _scan(obj, path="ctx"):
    hits = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            if str(k) in GOLD_MARKERS:
                hits.append(path + "." + str(k))
            hits += _scan(v, path + "." + str(k))
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            hits += _scan(v, "%s[%d]" % (path, i))
    elif isinstance(obj, str):
        for g in GOLD_MARKERS:
            if g in obj:
                hits.append("%s:str~%s" % (path, g))
    return hits

def run_turn(h, ctx, gw):
    h["report"].append({"turn": ctx.get("turn_index"), "keys": sorted(ctx.keys()),
                        "gold_hits": _scan(ctx)})
    return {"reply": json.dumps({"response": {"recommended": "嗯嗯", "bad_boy": "哦",
            "playful": "哈", "warm": "好"}, "directions": [None]*4,
            "analysis": {"topic_status": "same", "topic_label": "探针",
                         "scene_facts": [], "ongoing": []}}, ensure_ascii=False),
            "notes": json.dumps(h["report"][-1], ensure_ascii=False), "id_map": {}}

def snapshot(h):
    return json.dumps(h, ensure_ascii=False).encode("utf-8")

def restore(sandbox, snap):
    h = json.loads(snap.decode("utf-8")); h["sandbox"] = sandbox; h["assets_root"] = None
    return h
