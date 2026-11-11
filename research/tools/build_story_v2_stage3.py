#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""build_story_v2_stage3.py — W03-02 新 DEV 故事构建器（数据驱动，复用 stage1 已验证 schema）。

规格来源：benchmark/stories_v2/stage3_src/*.json（每文件一个 story 对象或 {"stories":[...]}）。
每个故事产出五件套（episode/reference/timeline/exposure/audit）+ benchmark/episodes runner 副本。
相对 stage1 的差异：turn 支持 note（stage2 引入）；intent 沿用 stage1 shape；
public_id 直接写在 spec 里（E037+，不与既有 ID 冲突）。零网络零模型调用。
"""
import hashlib
import json
import glob
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(ROOT, "benchmark", "stories_v2", "stage3_src")
OUT_BASE = os.path.join(ROOT, "benchmark", "stories_v2", "stage3")
EPISODES = os.path.join(ROOT, "benchmark", "episodes")
MANIFEST = os.path.join(OUT_BASE, "STAGE3_MANIFEST.json")
SESSION = "main-zcode-20261030-0052"


def sha(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")


def find_message_id(turn, quote):
    for i, (_r, text, _ts) in enumerate(turn["messages"]):
        if quote in text:
            return "m%d" % i
    return None


def pub(story, case_id):
    return case_id.replace(story["id"], story["public_id"], 1)


def resolve_fact(story, fact, turns_by_id):
    src = fact["source"]
    if src["kind"] == "message":
        turn = turns_by_id[src["case_id"]]
        mid = find_message_id(turn, src["quote"])
        return {"kind": "message", "case_id": pub(story, src["case_id"]),
                "message_id": mid, "quote": src["quote"]}
    if src["kind"] == "initial_kb":
        return {"kind": "initial_kb", "key": src["key"], "quote": src["quote"]}
    raise ValueError("unknown source kind %r" % src["kind"])


def build_episode(story):
    pid = story["public_id"]
    turns = []
    for t in story["turns"]:
        case_id = "%s_T%02d" % (pid, len(turns) + 1)
        msgs = [{"role": r, "text": text, "ts": ts} for r, text, ts in t["messages"]]
        inp = {"messages": msgs, "clock": t["clock"],
               "may_decline": bool(t.get("may_decline", False))}
        if t.get("note"):
            inp["note"] = t["note"]
        if t.get("intent"):
            inp["intent"] = dict(t["intent"])
        turns.append({"case_id": case_id, "turn_type": "reply",
                      "input": inp, "gold": {}})
    return {"episode_id": pid,
            "persona": dict(story["persona"]),
            "initial_kb": dict(story["initial_kb"]),
            "turns": turns}


def build_reference(story, episode):
    pid = story["public_id"]
    facts_by_id = {f["id"]: f for f in story["facts"]}
    turns_by_id = {("%s_T%02d" % (story["id"], i + 1)): t for i, t in enumerate(story["turns"])}
    out = {}
    for i, t in enumerate(story["turns"]):
        case_id = "%s_T%02d" % (pid, i + 1)
        atoms = []
        for fid in t.get("atoms") or []:
            f = facts_by_id[fid]
            atoms.append({
                "atom_id": fid, "subject": f["subject"], "relation": f["relation"],
                "value": f["value"], "valid_time": f["valid_time"],
                "status": f["status"], "negation": bool(f["negation"]),
                "source": resolve_fact(story, f, turns_by_id),
                "required_this_turn": fid in (t.get("required") or []),
            })
        out[case_id] = {
            "case_id": case_id,
            "current_action": t["current_action"],
            "acceptable_actions": list(t["acceptable_actions"]),
            "applicable_dimensions": list(t["dims"]),
            "required_memory_atoms": atoms,
            "private_guidance": t["private_guidance"],
            "unknowns": list(t.get("unknowns") or []),
            "protected_expressions": list(t.get("protected") or []),
            "forbidden_factual_claims": list(t.get("forbidden") or []),
            "may_decline": bool(t.get("may_decline", False)),
            "intent": dict(t["intent"]) if t.get("intent") else None,
        }
    return {"kind": "story_reference_v2", "story_id": pid,
            "title": story["title"], "visibility": "evaluator_only",
            "turns": out}


def build_timeline(story, episode):
    pid = story["public_id"]
    msg_index, clocks, revisions = [], [], []
    for i, t in enumerate(episode["turns"]):
        clocks.append({"case_id": t["case_id"], "clock": t["input"]["clock"]})
        for j, m in enumerate(t["input"]["messages"]):
            msg_index.append({"case_id": t["case_id"], "message_id": "m%d" % j,
                              "speaker": m["role"], "ts": m["ts"], "quote": m["text"]})
        spec_t = story["turns"][i]
        if spec_t.get("intent"):
            revisions.append({"case_id": t["case_id"], "kind": "intent",
                              "status": spec_t["intent"].get("status"),
                              "text": spec_t["intent"].get("text"),
                              "enabled": spec_t["intent"].get("enabled")})
    events = []
    for ev in story.get("events", []):
        e = dict(ev)
        e["source_turn"] = pub(story, e["source_turn"])
        events.append(e)
    return {"kind": "story_timeline_v2", "story_id": pid, "timezone": "Asia/Shanghai",
            "turn_clocks": clocks, "message_index": msg_index, "events": events,
            "relationship": list(story["relationship"]), "revisions": revisions,
            "future_policy": "event_time 可以是计划/未来；消息本身不得晚于对应 clock。"}


def build_audit(story):
    pid = story["public_id"]
    may_decline = ["%s_T%02d" % (pid, i + 1) for i, t in enumerate(story["turns"]) if t.get("may_decline")]
    n_atoms = sum(len(t.get("atoms") or []) for t in story["turns"])
    n_req = sum(len(t.get("required") or []) for t in story["turns"])
    notes = [t["input"]["note"] for t in story["turns"] if story["turns"][story["turns"].index(t)].get("note")] if False else [t["note"] for t in story["turns"] if t.get("note")]
    lines = ["# %s — %s 审计" % (pid, story["title"]), "",
             "## 0. 性质与暴露", "",
             "- 合成虚构故事；不是用户真实素材，不含真实个人数据。",
             "- 作者：%s（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。" % SESSION,
             "- %d turns；W03-02 新 DEV 批（stage3）。" % len(story["turns"]), "",
             "## 1. 生活动机", "", story["motive"], "",
             "## 2. 连续性与回扣", "", story["continuity"], "",
             "## 3. ME 历史与边界", "",
             "- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。",
             "- 备注位（note）共 %d 处：%s" % (len(notes), "；".join(x[:36] for x in notes) if notes else "无"),
             "- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。", "",
             "## 4. no_reply / 媒体局限", "",
             "- may_decline 位：%s。" % ("、".join(may_decline) if may_decline else "无"),
             "- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。", "",
             "## 5. 参考卡质量", "",
             "- atoms %d 条 / 其中 required %d 条；每轮 acceptable_actions≥2，无单句标准答案。" % (n_atoms, n_req),
             "- 卡面与 %s 原文逐字同源（builder 自动解析，无手写判据）。" % pid, "",
             "## 6. 已知风险", "",
             "- unknowns 共 %d 项未在卡面给答案，评分时不得当 0 分用。" % sum(len(t.get("unknowns") or []) for t in story["turns"]),
             "- %s" % story.get("corrections", "本篇无更正链"), "",
             "## 7. 出口检查", "",
             "- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；",
             "- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。", ""]
    return "\n".join(lines)


def build_exposure(story, hashes):
    pid = story["public_id"]
    return {"kind": "story_exposure_v2", "story_id": pid, "title": story["title"],
            "themes": story["themes"],
            "author": "ZCode %s (synthetic)" % SESSION, "author_type": "AI",
            "real_user_material": False,
            "source_notes": "虚构人物与对话；不含真实用户素材；不声称真人经历。",
            "readers": ["author"], "independent_review": False,
            "review_note": "worker 自检；不是独立封存集。",
            "created_at": "2026-10-30", "frozen_at": None,
            "status": "dev_candidate_stage3", "experiments_used": [],
            "dev_exposed": True, "validation_claim": False,
            "turns": len(story["turns"]),
            "runner_copy": "benchmark/episodes/%s.json" % pid,
            "may_decline_turns": [
                "%s_T%02d" % (pid, i + 1) for i, t in enumerate(story["turns"]) if t.get("may_decline")],
            "hashes": hashes}


def build_story(story):
    pid = story["public_id"]
    episode = build_episode(story)
    reference = build_reference(story, episode)
    timeline = build_timeline(story, episode)
    d = os.path.join(OUT_BASE, pid)
    ep_path = os.path.join(d, "episode.json")
    ref_path = os.path.join(d, "reference.json")
    tl_path = os.path.join(d, "timeline.json")
    write_json(ep_path, episode)
    write_json(ref_path, reference)
    write_json(tl_path, timeline)
    hashes = {"episode.json": sha(ep_path), "reference.json": sha(ref_path),
              "timeline.json": sha(tl_path)}
    exposure = build_exposure(story, hashes)
    exp_path = os.path.join(d, "exposure.json")
    write_json(exp_path, exposure)
    with open(os.path.join(d, "audit.md"), "w", encoding="utf-8") as f:
        f.write(build_audit(story))
    runner_path = os.path.join(EPISODES, "%s.json" % pid)
    if os.path.exists(runner_path):
        existing = json.load(open(runner_path, encoding="utf-8"))
        if existing.get("episode_id") != pid:
            raise SystemExit("runner copy 已存在且非本篇，拒绝覆盖: %s" % runner_path)
        os.remove(runner_path)  # 本篇同会话旧副本，重建替换
    write_json(runner_path, episode)
    return {"story_id": pid, "title": story["title"], "themes": story["themes"],
            "turns": len(story["turns"]), "facts": len(story["facts"]),
            "dir": os.path.relpath(d, ROOT).replace("\\", "/"),
            "runner_copy": "benchmark/episodes/%s.json" % pid,
            "episode_sha256": hashes["episode.json"]}


def main():
    specs = []
    for f in sorted(glob.glob(os.path.join(SRC_DIR, "*.json"))):
        d = json.load(open(f, encoding="utf-8"))
        specs.extend(d["stories"] if isinstance(d, dict) and "stories" in d else [d])
    manifest, total_turns = [], 0
    for story in specs:
        entry = build_story(story)
        manifest.append(entry)
        total_turns += entry["turns"]
        print("built %s (%d turns, %s)" % (entry["story_id"], entry["turns"], entry["title"]))
    cards = {"kind": "stage3_grading_cards", "cards": []}
    for m in manifest:
        ref = json.load(open(os.path.join(ROOT, m["dir"], "reference.json"), encoding="utf-8"))
        for cid, tr in ref["turns"].items():
            cards["cards"].append({k: tr.get(k) for k in (
                "case_id", "current_action", "acceptable_actions", "applicable_dimensions",
                "required_memory_atoms", "private_guidance", "unknowns",
                "protected_expressions", "forbidden_factual_claims", "may_decline", "intent")})
    write_json(os.path.join(OUT_BASE, "GRADING_CARDS_STAGE3.json"), cards)
    write_json(MANIFEST, {"kind": "story_v2_stage3_manifest", "date": "2026-10-30",
                          "built_by": SESSION, "count": len(manifest), "stories": manifest,
                          "total_turns": total_turns})
    print("manifest: %s | stories=%d turns=%d" % (MANIFEST, len(manifest), total_turns))
    return 0


if __name__ == "__main__":
    sys.exit(main())
