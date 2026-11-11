# -*- coding: utf-8 -*-
"""G-1: wave1_ext 续写故事包 -> runner 可消费格式（零模型调用，确定性导出）。

读 benchmark/stories_v2/wave1_ext/<id>/episode.json -> 写 benchmark/episodes/<id>.json
（episode_id 已是 LRE0xx，原样拷贝，key 顺序保持）；
读同目录 reference.json -> 组装 GRADING_CARDS_W12.json，
schema 对齐 benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json
（{"kind":"stage3_grading_cards","cards":[...]}），每 case 仅输出 9 个评审字段；
may_decline/intent 从 episode.json 同 case 的 input 取。

用法:
  python tools/export_lr2_episodes.py --ids LRE060,LRE048
  python tools/export_lr2_episodes.py                # 默认扫 wave1_ext 下全部 LRE*
  python tools/export_lr2_episodes.py --ids E060 \
      --source-dir benchmark/stories_v2/wave1 --dry-run   # 格式烟测，不落盘
"""
import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 评审卡只带这 9 个字段；private_guidance/protected_expressions 不下发给 runner
# （design §4.3 G-1 明确清单；grep 证实 run_max288/exam_interleave_plan/score_longprobe
#  均不消费这两字段，截断无损）。
CARD_FIELDS = (
    "case_id",
    "current_action",
    "acceptable_actions",
    "applicable_dimensions",
    "required_memory_atoms",
    "unknowns",
    "forbidden_factual_claims",
    "may_decline",
    "intent",
)


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def dump_json(obj, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def discover_ids(source_dir):
    ids = []
    for name in sorted(os.listdir(source_dir)):
        if not name.startswith("LRE"):
            continue
        if os.path.isfile(os.path.join(source_dir, name, "episode.json")):
            ids.append(name)
    return ids


def build_cards(story_dir, episode):
    ref = load_json(os.path.join(story_dir, "reference.json"))
    ref_turns = ref["turns"]
    ep_turns = {}
    for t in episode["turns"]:
        ep_turns[t["case_id"]] = t

    missing = sorted(set(ref_turns) - set(ep_turns))
    if missing:
        raise SystemExit("episode.json 缺少 reference 引用的 case: %s" % missing)

    cards = []
    for case_id in sorted(ref_turns):
        r = ref_turns[case_id]
        inp = ep_turns[case_id]["input"]
        card = {
            "case_id": r.get("case_id", case_id),
            "current_action": r.get("current_action", ""),
            "acceptable_actions": r.get("acceptable_actions", []),
            "applicable_dimensions": r.get("applicable_dimensions", []),
            "required_memory_atoms": r.get("required_memory_atoms", []),
            "unknowns": r.get("unknowns", []),
            "forbidden_factual_claims": r.get("forbidden_factual_claims", []),
            "may_decline": inp.get("may_decline", False),
            "intent": inp.get("intent"),
        }
        cards.append({k: card[k] for k in CARD_FIELDS})
    return cards


def _rel(path):
    try:
        return os.path.relpath(path, ROOT)
    except ValueError:  # 跨盘符临时目录（烟测）直接显示绝对路径
        return path


def flatten_persona(persona):
    """wave 系 episode 的 persona 是嵌套 dict（name/traits/boundaries），
    runner 装配链（r1 _seed_facts 等）期望 {"me": str, "her": str}——
    导出副本展平，story 原件不动。"""
    if not isinstance(persona, dict):
        return persona
    if all(isinstance(v, str) for v in persona.values()):
        return persona

    def flat(side):
        if isinstance(side, str):
            return side
        parts = []
        if side.get("name"):
            parts.append(str(side["name"]))
        for k in ("traits", "boundaries"):
            vals = side.get(k) or []
            if isinstance(vals, list) and vals:
                label = "边界" if k == "boundaries" else "特点"
                parts.append("%s：%s" % (label, "；".join(str(v) for v in vals)))
            elif isinstance(vals, str) and vals:
                parts.append(("%s：%s" % ("边界" if k == "boundaries" else "特点", vals)))
        for k, v in side.items():
            if k in ("name", "traits", "boundaries") or not isinstance(v, str):
                continue
            parts.append(v)
        return "，".join(parts)

    out = {}
    for key in ("her", "me"):
        if key in persona:
            out[key] = flat(persona[key])
    for k, v in persona.items():
        if k not in out and isinstance(v, str):
            out[k] = v
    return out


def export_one(sid, source_dir, episodes_dir, out_cards, dry_run):
    story_dir = os.path.join(source_dir, sid)
    episode_path = os.path.join(story_dir, "episode.json")
    episode = load_json(episode_path)

    if episode.get("episode_id") != sid:
        # 目录名与 episode_id 不一致会让 runner 的 case_id 解析错位，直接拒绝
        raise SystemExit(
            "episode_id=%r 与目录名 %r 不一致 (%s)"
            % (episode.get("episode_id"), sid, episode_path)
        )
    if not sid.startswith("LRE"):
        print("[warn] %s 不是 LRE 前缀（烟测模式允许，正式导出要求 LRE0xx）" % sid)

    export_episode = dict(episode)
    export_episode["persona"] = flatten_persona(episode.get("persona"))

    cards = build_cards(story_dir, episode)

    ep_out = os.path.join(episodes_dir, "%s.json" % sid)
    cards_out = os.path.join(out_cards)

    if dry_run:
        print("[dry-run] %s: episode->%s (turns=%d, persona=%s), cards->%s (%d cards)"
              % (sid, _rel(ep_out), len(export_episode["turns"]),
                 type(export_episode.get("persona", {}).get("her")).__name__,
                 _rel(cards_out), len(cards)))
        return {"episode_turns": len(export_episode["turns"]), "cards": len(cards)}

    dump_json(export_episode, ep_out)
    payload = {"kind": "stage3_grading_cards", "cards": cards}
    # 多篇共享一张 W12 卡组；每篇覆盖写会丢前一篇，改为合并去重
    if os.path.isfile(cards_out):
        existing = load_json(cards_out)
        seen = {c["case_id"] for c in existing.get("cards", [])}
        merged = list(existing.get("cards", []))
        merged += [c for c in cards if c["case_id"] not in seen]
        payload["cards"] = sorted(merged, key=lambda c: c["case_id"])
    dump_json(payload, cards_out)
    print("[ok] %s -> %s (turns=%d); cards %d -> %s"
          % (sid, _rel(ep_out), len(episode["turns"]),
             len(payload["cards"]), _rel(cards_out)))
    return {"episode_turns": len(episode["turns"]), "cards": len(cards)}


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--ids", default=None,
                    help="逗号分隔故事 id；缺省扫 source-dir 下全部 LRE* 目录")
    ap.add_argument("--source-dir", default=os.path.join(ROOT, "benchmark", "stories_v2", "wave1_ext"))
    ap.add_argument("--episodes-dir", default=os.path.join(ROOT, "benchmark", "episodes"))
    ap.add_argument("--out-cards",
                    default=os.path.join(ROOT, "benchmark", "stories_v2", "wave1_ext",
                                         "GRADING_CARDS_W12.json"))
    ap.add_argument("--dry-run", action="store_true",
                    help="只校验与打印，不写任何文件（格式烟测用）")
    args = ap.parse_args()

    ids = [s.strip() for s in args.ids.split(",") if s.strip()] if args.ids else None
    if not ids:
        if not os.path.isdir(args.source_dir):
            sys.exit("source-dir 不存在且未给 --ids: %s" % args.source_dir)
        ids = discover_ids(args.source_dir)
        if not ids:
            sys.exit("source-dir 下未发现 LRE* 故事包: %s" % args.source_dir)

    summary = {}
    for sid in ids:
        summary[sid] = export_one(sid, args.source_dir, args.episodes_dir,
                                  args.out_cards, args.dry_run)
    print("[summary] %s" % json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
