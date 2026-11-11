# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 2 · wave4_dev 模板对齐修复工具（fix_wave4_schema_v1）。

背景：首批试制 38 篇由三角色分工作品，episode/reference/timeline/exposure/audit 的
信息与既有 wave 模板等价但形态不同。本工具做确定性形态迁移（不改任何事实内容），
使 validate_story_v2（既有唯一校验器）可通过。

修复项（与 2026-11-08 校验摸底 8 类错误一一对应）：
  A1 intent.status 补 "explicit"（14 处，仅 E301-E304，A1 的 IDEA 指令型 intent）
  A2 required_this_turn 翻转为 false（当原子 source.case_id == 本轮 case_id；
     699 处。旧 wave 既定口径：required 原子必须来自更早轮位；"本轮必须处理"的
     约束由 acceptable_actions/forbidden/private_guidance 语义层承载。原子保留。）
  A3 message_id 按校验器 hit[0] 规则重算（4 处 m0→m1）
  A4 protected_expressions 逐字修复（E314_T04→移至 T03；E315_T03 换弯引号原句）
  A5 timeline.json 迁移到旧 schema：
     {story_id, events:[{event_id, source_turn, description}], turn_clocks,
      message_index:[{case_id,message_id,speaker,quote,ts}], revisions:[]}
     （新 events 的 ts 字段丢弃——时序由 turn_clocks 承载；更正链在原子/卡内）
  A6 runner_episode.json = episode.json 字节副本（旧 wave 惯例）+ exposure.runner_copy
  A7 exposure 补 kind/batch/session/readers（readers=["author","annotator"]）
  A8 unknowns 键名对齐 GOLD_SCHEMA §5：`markers` → 补 `affirm_markers` 同值双键
     （E301-E332 共 159 处；B5/B6 已用规范键，sidecar L204 只读 affirm_markers）
  A9 原子 affirm_markers 扁平表 → 包裹为单词组 [[...]]（同现 AND 语义，GOLD_SCHEMA
     规范形态；扁平会被 _group_hit 按"任一词命中"弱化解释，40 处）
  B  hashes 终算（episode.json/reference.json/timeline.json/audit.md；
     在 audit.md 8 节重构完成后运行——audit.md 哈希需终态）

用法：
  python -I tools/benchmark_v1/fix_wave4_schema_v1.py            # dry-run，只打印计数
  python -I tools/benchmark_v1/fix_wave4_schema_v1.py --apply --phase a
  python -I tools/benchmark_v1/fix_wave4_schema_v1.py --apply --phase b
"""
import argparse
import hashlib
import json
import os
import shutil

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
BASE = os.path.join(REPO, "benchmark", "stories_v2", "wave4_dev")
STATS = {}


def stat(k, n=1):
    STATS[k] = STATS.get(k, 0) + n


def sha(p):
    with open(p, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def episodes():
    out = []
    for eid in sorted(os.listdir(BASE)):
        d = os.path.join(BASE, eid)
        if os.path.isdir(d) and eid[0] == "E" and eid[1:].isdigit():
            out.append(eid)
    return out


def fix_intent(ep):
    n = 0
    for t in ep.get("turns") or []:
        it = (t.get("input") or {}).get("intent")
        if isinstance(it, dict) and "status" not in it:
            it["status"] = "explicit"
            n += 1
    return n


def fix_reference(ref, ep, eid):
    msgs = {t["case_id"]: [m.get("text", "") for m in t["input"].get("messages", [])]
            for t in ep.get("turns") or []}
    n_req = n_mid = n_unk = n_flat = 0
    for cid, card in (ref.get("turns") or {}).items():
        # A8 unknowns 键名对齐（markers -> 补 affirm_markers 双键）
        for u in card.get("unknowns") or []:
            if isinstance(u, dict) and "markers" in u and "affirm_markers" not in u:
                u["affirm_markers"] = u["markers"]
                n_unk += 1
        for a in card.get("required_memory_atoms") or []:
            # A9 扁平 affirm_markers -> 单词组包裹
            v = a.get("affirm_markers")
            if isinstance(v, list) and v and all(isinstance(x, str) for x in v):
                a["affirm_markers"] = [list(v)]
                n_flat += 1
        # A4 protected fix（精确两处）
        prot = card.get("protected_expressions") or []
        if eid == "E314" and cid == "E314_T04":
            ph = "谁跟我提吃的我跟谁急"
            if ph in prot:
                card["protected_expressions"] = [p for p in prot if p != ph]
                t03 = ref["turns"].get("E314_T03")
                if t03 is not None and ph not in (t03.get("protected_expressions") or []):
                    t03.setdefault("protected_expressions", []).append(ph)
                    stat("A4_moved_to_T03")
                stat("A4_removed_from_T04")
        if eid == "E315" and cid == "E315_T03":
            fixed = False
            for i, p in enumerate(prot):
                if "人肉帐篷" in p and p not in msgs.get(cid, [""])[0:1]:
                    prot[i] = "我总不能填个“人肉帐篷”"
                    fixed = True
            if fixed:
                card["protected_expressions"] = prot
                stat("A4_verbatim_fixed")
        for a in card.get("required_memory_atoms") or []:
            src = a.get("source") or {}
            scid = src.get("case_id") or cid
            # A2 required 翻转
            if a.get("required_this_turn") and scid == cid:
                a["required_this_turn"] = False
                n_req += 1
            # A3 message_id 重算（与校验器 hit[0] 规则一致）
            q = src.get("quote") or ""
            sm = msgs.get(scid) or []
            hit = [k for k, m in enumerate(sm) if q in m]
            if hit and src.get("message_id") != "m%d" % hit[0]:
                src["message_id"] = "m%d" % hit[0]
                n_mid += 1
    return n_req, n_mid, n_unk, n_flat


def transform_timeline(tl, ep, eid):
    events = []
    for i, ev in enumerate(tl.get("events") or []):
        events.append({
            "event_id": "EV%02d" % (i + 1),
            "source_turn": ev.get("turn"),
            "description": ev.get("event"),
        })
    turn_clocks = [t["input"]["clock"] for t in ep.get("turns") or []]
    message_index = []
    for t in ep.get("turns") or []:
        for k, m in enumerate(t["input"].get("messages") or []):
            message_index.append({
                "case_id": t["case_id"],
                "message_id": "m%d" % k,
                "speaker": m.get("role"),
                "quote": m.get("text", ""),
                "ts": m.get("ts"),
            })
    return {
        "story_id": tl.get("episode_id") or ep.get("episode_id"),
        "events": events,
        "turn_clocks": turn_clocks,
        "message_index": message_index,
        "revisions": [],
    }


def fix_exposure(ex, eid, nturns):
    if not ex.get("readers"):
        ex["readers"] = ["author", "annotator"]
    ex.setdefault("runner_copy", "benchmark/stories_v2/wave4_dev/%s/runner_episode.json" % eid)
    ex.setdefault("kind", {12: "standard_12t", 24: "long_24t", 32: "long_32t"}.get(nturns, "standard_%dt" % nturns))
    ex.setdefault("batch", "W4-DEV-20261108")
    author = ex.get("author") or ""
    sess = "W4-DEV"
    if "author-" in author:
        sess = "W4-DEV-" + author.split("author-")[1].split(" ")[0]
    ex.setdefault("session", sess)


def phase_a(apply):
    for eid in episodes():
        d = os.path.join(BASE, eid)
        ep = json.load(open(os.path.join(d, "episode.json"), encoding="utf-8"))
        ref = json.load(open(os.path.join(d, "reference.json"), encoding="utf-8"))
        tl = json.load(open(os.path.join(d, "timeline.json"), encoding="utf-8"))
        ex = json.load(open(os.path.join(d, "exposure.json"), encoding="utf-8"))

        n_intent = fix_intent(ep)
        if n_intent:
            stat("A1_intent_status", n_intent)
        n_req, n_mid, n_unk, n_flat = fix_reference(ref, ep, eid)
        if n_req:
            stat("A2_required_flip", n_req)
        if n_mid:
            stat("A3_message_id", n_mid)
        if n_unk:
            stat("A8_unknowns_affirm_markers", n_unk)
        if n_flat:
            stat("A9_flat_affirm_wrapped", n_flat)
        new_tl = transform_timeline(tl, ep, eid)
        stat("A5_timeline_transformed")
        fix_exposure(ex, eid, len(ep.get("turns") or []))
        stat("A7_exposure_fields")

        if apply:
            json.dump(ep, open(os.path.join(d, "episode.json"), "w", encoding="utf-8", newline="\n"),
                      ensure_ascii=False, indent=1)
            json.dump(ref, open(os.path.join(d, "reference.json"), "w", encoding="utf-8", newline="\n"),
                      ensure_ascii=False, indent=1)
            json.dump(new_tl, open(os.path.join(d, "timeline.json"), "w", encoding="utf-8", newline="\n"),
                      ensure_ascii=False, indent=1)
            json.dump(ex, open(os.path.join(d, "exposure.json"), "w", encoding="utf-8", newline="\n"),
                      ensure_ascii=False, indent=1)
            shutil.copyfile(os.path.join(d, "episode.json"), os.path.join(d, "runner_episode.json"))
            stat("A6_runner_copy")
    return STATS


def phase_b(apply):
    """audit.md 重构完成后的哈希终算，写回 exposure.hashes。"""
    for eid in episodes():
        d = os.path.join(BASE, eid)
        ex_path = os.path.join(d, "exposure.json")
        ex = json.load(open(ex_path, encoding="utf-8"))
        hashes = {}
        for fn in ("episode.json", "reference.json", "timeline.json", "audit.md"):
            p = os.path.join(d, fn)
            if os.path.isfile(p):
                hashes[fn] = sha(p)
        ex["hashes"] = hashes
        stat("B_hashes", 1)
        if apply:
            json.dump(ex, open(ex_path, "w", encoding="utf-8", newline="\n"),
                      ensure_ascii=False, indent=1)
    return STATS


def main():
    global BASE
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="默认 dry-run")
    ap.add_argument("--phase", default="a", choices=["a", "b"])
    ap.add_argument("--base", default=None, help="覆盖 wave 目录（测试用）")
    args = ap.parse_args()
    if args.base:
        BASE = os.path.abspath(args.base)
    stats = phase_a(args.apply) if args.phase == "a" else phase_b(args.apply)
    print("phase=%s apply=%s" % (args.phase, args.apply))
    for k, v in sorted(stats.items()):
        print("  %s: %d" % (k, v))
    if not args.apply:
        print("(dry-run：未写任何文件)")


if __name__ == "__main__":
    main()
