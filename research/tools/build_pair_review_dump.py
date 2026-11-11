# -*- coding: utf-8 -*-
r"""tools/build_pair_review_dump.py — 双臂真实答卷 → 可读配对初审材料 + 评分骨架（零调用）。

为什么需要：交付必须回答"逐维质量、知识使用、主动互动、严重错误、保留还是淘汰"，
而不是只有程序分与 token。程序分（gold 为空时）覆盖不到语义，裁判也不许代填。
本工具把【同一位点的两臂原文 + 该位点的评审卡判据 + 候选实际看到的输入】摊成一页，
并生成评分骨架：所有语义格子留空，由 worker 逐位填证据与分数，绝不预填 pass。

用法：
  set PYTHONIOENCODING=utf-8
  D:\anaconda\python.exe tools\build_pair_review_dump.py \
      --exam-dir logs/p08_s1_live_20261028/_reports \
      --cards benchmark/stories_v2/stage2/GRADING_CARDS_E024_E027_E031_E034.json \
      --episodes E024,E027,E031,E034 \
      --text-out <可读文件> --skeleton-out <骨架JSON>
"""
import argparse
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIMS = ["goal", "factuality", "attribution", "time_state", "boundary",
        "memory_use", "interaction", "initiative", "emotion_relation"]


def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def rel(p):
    try:
        return os.path.relpath(p, ROOT).replace("\\", "/")
    except ValueError:          # 跨盘（C: 临时目录 vs D: 仓库）→ 用绝对路径
        return os.path.abspath(p).replace("\\", "/")


def arm_rows(exam_dir, arm):
    """从 EXAM_<arm> 目录读每轮 manifest+calls，返回 {case_id: {...}}。"""
    out = {}
    root = os.path.join(exam_dir, "EXAM_%s" % arm)
    # 前缀白名单（E*/AK*）会静默跳过其它前缀的新批（实测：D01/D02 泛化批答卷全部"缺答卷"）。
    # 改为按产物发现：任何含 turn_* 子目录的 episode 目录都收，与 exam_layered_score.py
    # 的 manifest_driven_v1 修法一致。
    for d in sorted(glob.glob(os.path.join(root, "*", "turn_*"))):
        mf_p = os.path.join(d, "manifest.json")
        if not os.path.isfile(mf_p):
            continue
        mf = load(mf_p)
        raw = ""
        tk = {"prompt": None, "completion": None, "cache": None}
        cp = os.path.join(d, "calls.json")
        if os.path.isfile(cp):
            calls = load(cp)
            main = next((c for c in calls if c.get("tag", "main") == "main"), None)
            if main:
                raw = main.get("raw_text") or ""
                u = main.get("usage") or {}
                tk = {"prompt": u.get("prompt"), "completion": u.get("completion"),
                      "cache": u.get("cache_hit")}
        sc_p = os.path.join(d, "scoring.json")
        sc = load(sc_p) if os.path.isfile(sc_p) else {}
        out[mf["case_id"]] = {
            "dir": rel(d), "run_id": mf.get("run_id"),
            "case_input_sha256": mf.get("case_input_sha256"),
            "raw_reply_sha256": mf.get("raw_reply_sha256"),
            "raw_text": raw, "score10": sc.get("score10"),
            "veto": sc.get("veto"),
            "failed_checks": [c.get("id") for c in sc.get("checks", [])
                              if not c.get("pass")],
            "tokens": tk, "latency_ms": mf.get("latency_ms"),
        }
    return out


def slots_from_raw(raw):
    """复用分层评分的槽位抽取，保证 primary/备选口径与程序侧一致。"""
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    import exam_layered_score as layer
    primary, backups, reason = layer._extract_slots(raw or "")
    return primary, (backups or []), reason


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--exam-dir", required=True, help="批次的 _reports 目录")
    ap.add_argument("--cards", required=True)
    ap.add_argument("--episodes", required=True)
    ap.add_argument("--arms", default="parent,candidate")
    ap.add_argument("--arm-names", default=None,
                    help="两臂真实 harness 名，逗号分隔（默认从 EXAM_INDEX 读）")
    ap.add_argument("--text-out", required=True)
    ap.add_argument("--skeleton-out", required=True)
    a = ap.parse_args(argv)

    exam_dir = a.exam_dir if os.path.isabs(a.exam_dir) else os.path.join(ROOT, a.exam_dir)
    cards_doc = load(a.cards if os.path.isabs(a.cards) else os.path.join(ROOT, a.cards))
    cards = {c["case_id"]: c for c in cards_doc["cards"]}
    arms = [x.strip() for x in a.arms.split(",")]
    rows = {arm: arm_rows(exam_dir, arm) for arm in arms}
    real_names = {}
    for arm in arms:
        idx_p = os.path.join(exam_dir, "EXAM_%s" % arm, "EXAM_INDEX.json")
        real_names[arm] = (load(idx_p).get("harness") if os.path.isfile(idx_p) else arm)
    if a.arm_names:
        names = [x.strip() for x in a.arm_names.split(",")]
        real_names = dict(zip(arms, names))

    eps = [e.strip() for e in a.episodes.split(",") if e.strip()]
    lines, skeleton = [], {"kind": "pair_review_skeleton_v1",
                           "cards_file": rel(a.cards if os.path.isabs(a.cards)
                                             else os.path.join(ROOT, a.cards)),
                           "exam_dir": rel(exam_dir), "arms": real_names,
                           "scale": "0-3（0反向/严重错 1关键不足 2合理完成 3本情境实质增益）；"
                                    "NA不适用 NR未测 uncertain审过不确定",
                           "positions": []}
    missing = []
    for ep in eps:
        epath = os.path.join(ROOT, "benchmark", "episodes", "%s.json" % ep)
        if not os.path.isfile(epath):
            missing.append(ep)
            continue
        epd = load(epath)
        lines.append("=" * 78)
        lines.append("STORY %s｜%s" % (ep, epd.get("episode_id")))
        for t in epd["turns"]:
            cid = t["case_id"]
            inp = t["input"]
            card = cards.get(cid, {})
            lines.append("-" * 78)
            lines.append("%s clock=%s may_decline=%s intent=%s" % (
                cid, inp.get("clock"), inp.get("may_decline"),
                json.dumps(inp.get("intent"), ensure_ascii=False) if inp.get("intent")
                else "None"))
            for i, m in enumerate(inp.get("messages", [])):
                lines.append("   m%d %s｜%s" % (i, m["role"], m["text"]))
            if inp.get("note"):
                lines.append("   note: " + str(inp["note"]))
            lines.append("   [卡] current_action: %s" % card.get("current_action"))
            lines.append("   [卡] acceptable: %s" % json.dumps(
                card.get("acceptable_actions"), ensure_ascii=False))
            lines.append("   [卡] dims: %s" % json.dumps(
                card.get("applicable_dimensions"), ensure_ascii=False))
            lines.append("   [卡] private_guidance: %s" % card.get("private_guidance"))
            lines.append("   [卡] protected: %s" % json.dumps(
                card.get("protected_expressions"), ensure_ascii=False))
            lines.append("   [卡] forbidden: %s" % json.dumps(
                card.get("forbidden_factual_claims"), ensure_ascii=False))
            lines.append("   [卡] unknowns: %s" % json.dumps(
                card.get("unknowns"), ensure_ascii=False))
            for at in card.get("required_memory_atoms") or []:
                s = at.get("source") or {}
                lines.append("   [卡·事实] %s subj=%s rel=%s val=%s status=%s src=%s:%s"
                             % (at.get("atom_id"), at.get("subject"), at.get("relation"),
                                at.get("value"), at.get("status"), s.get("kind"),
                                s.get("case_id") or s.get("key")))
            pos = {"case_id": cid, "episode": ep, "card_dims":
                   card.get("applicable_dimensions") or [], "arms": {}}
            for arm in arms:
                r = rows[arm].get(cid)
                if not r:
                    lines.append("   [%s] !! 缺该位点答卷" % real_names[arm])
                    pos["arms"][real_names[arm]] = {"present": False}
                    continue
                primary, backups, reason = slots_from_raw(r["raw_text"])
                lines.append("   ── %s ── score10=%s veto=%s failed=%s delivery=%s "
                             "tokens p/c=%s/%s latency_ms=%s" % (
                                 real_names[arm], r["score10"], r["veto"],
                                 r["failed_checks"], reason,
                                 r["tokens"]["prompt"], r["tokens"]["completion"],
                                 r["latency_ms"]))
                lines.append("      [首选] %s" % (primary or "(无)"))
                for slot_name, slot_text in backups:
                    if isinstance(slot_text, str) and slot_text.strip():
                        lines.append("      [备选 %s] %s" % (slot_name, slot_text))
                pos["arms"][real_names[arm]] = {
                    "present": True, "primary": primary,
                    "backups": [[s, t] for s, t in backups],
                    "slot_parse": reason, "raw_text": r["raw_text"],
                    "raw_reply_sha256": r["raw_reply_sha256"],
                    "case_input_sha256": r["case_input_sha256"],
                    "run_id": r["run_id"], "program": {
                        "score10": r["score10"], "veto": r["veto"],
                        "failed_checks": r["failed_checks"]},
                    "tokens": r["tokens"], "latency_ms": r["latency_ms"],
                    "scores": {d: {"score": "", "evidence": "", "note": ""}
                               for d in DIMS},
                    "severe_error": "", "worker_notes": ""}
            skeleton["positions"].append(pos)
    out_t = a.text_out if os.path.isabs(a.text_out) else os.path.join(ROOT, a.text_out)
    out_s = a.skeleton_out if os.path.isabs(a.skeleton_out) else os.path.join(
        ROOT, a.skeleton_out)
    os.makedirs(os.path.dirname(out_t), exist_ok=True)
    os.makedirs(os.path.dirname(out_s), exist_ok=True)
    with open(out_t, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    json.dump(skeleton, open(out_s, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    filled = sum(1 for p in skeleton["positions"]
                 for arm, d in p["arms"].items() if d.get("present"))
    print("positions=%d arm_answers_present=%d missing_episodes=%s"
          % (len(skeleton["positions"]), filled, missing))
    print("text -> %s (%d lines)" % (rel(out_t), len(lines)))
    print("skeleton -> %s" % rel(out_s))
    return 0


if __name__ == "__main__":
    sys.exit(main())
