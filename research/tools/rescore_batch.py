#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""rescore_batch.py — 批次级离线重评（FK-SCORE-01 修复后口径，零模型调用）。

背景（W19-01 后半）：arena/isolation/trusted_controller.py 新增模块级
prior_hist_id_map(ep, turn_index)，隔离路径评分把机制渲染的历史标签
m_src[i]（i < prior_real_n，从评估侧原题计数）纳入合法引用集。
本工具对已付费批次的历史答卷按修复后口径离线重评：

  - 题面来源：<batch>/<arm>_<EP>/<EP>.copy.json（即该次运行实际绑定的
    评估侧原题快照；与 out/episode_manifest.json integrity.episode_sha256
    核对后才使用，不回读工作树题面）。
  - id_map 与隔离路径修复后口径逐字一致：直接 import
    lb_runner.canonical_id_map(...) + trusted_controller.prior_hist_id_map(ep, i)，
    不复制逻辑。
  - 评分：lb_scorer.run_case_scoring_safe(turn_eval, reply_raw, id_map, contract)，
    contract 用 lb_runner.load_contract 从 _iso_run/staged/candidates/<name>/
    params.json（运行时快照）解析；解析不到时回退 candidates/<name>/，
    再回退 None（legacy）。
  - 输出：每轮 <turn_dir>/scoring_v05_fixed.json（不改原 scoring.json /
    manifest / calls.json 等任何历史产物）；批次汇总
    <batch>/_reports/RESCORE_SUMMARY_<batch>.json（逐位 diff）。

用法：
  python tools/rescore_batch.py logs/w17b_guard_live_20261102            # 全量
  python tools/rescore_batch.py logs/w17c_merge_live_20261103 --ep E183  # 单篇试通
  python tools/rescore_batch.py <batch> --dry-run                        # 只算不落盘
"""
import argparse
import collections
import copy
import datetime
import json
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from arena.lb_runner import canonical_id_map, load_contract  # noqa: E402
from arena.isolation.trusted_controller import prior_hist_id_map  # noqa: E402
from arena import lb_scorer  # noqa: E402
from arena import gold_sidecar  # noqa: E402

TURN_DIR_RE = re.compile(r"^turn_(\d+)_([^/]+)$")

# GOLD_SCORING_DEMO 分层口径：契约层 = 与 gold 无关的检查；gold 事实层 = 消费 gold
# 的全部检查（rubric §2 C3–C15）；未实例化的 gold 相关检查与 confirmed 证据原子
# 记 unrated（rubric §2"gold 缺席诚实登记"，不冒通过、不进分母）。
CONTRACT_CHECK_IDS = {"json_schema_valid", "source_ids_exist",
                      "ai_flavor", "style_diversity", "note_lcs_diag"}


def _layer_split(scoring: dict):
    def _agg(layer):
        if not layer:
            return {"pts_got": 0, "pts_total": 0, "score10": None, "checks": []}
        got = sum(c["got"] for c in layer)
        tot = sum(c["pts"] for c in layer)
        return {"pts_got": got, "pts_total": tot,
                "score10": round(got / tot * 10, 2) if tot else None,
                "checks": [{"id": c["id"], "pass": c["pass"], "pts": c["pts"],
                            "evidence": (c.get("evidence") or "")[:120]}
                           for c in layer]}
    contract = [c for c in scoring.get("checks", []) if c["id"] in CONTRACT_CHECK_IDS]
    fact_gold = [c for c in scoring.get("checks", []) if c["id"] not in CONTRACT_CHECK_IDS]
    return _agg(contract), _agg(fact_gold)


def _load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def _write_json(path, obj):
    with open(path, "w", encoding="utf-8", newline="") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def _resolve_contract(arm_dir: str, candidate_name: str):
    """与运行时一致：lb_runner.load_contract(运行快照候选目录)。"""
    staged = os.path.join(arm_dir, "_iso_run", "staged", "candidates",
                          candidate_name)
    if os.path.isfile(os.path.join(staged, "params.json")):
        return load_contract(staged), os.path.relpath(
            os.path.join(staged, "params.json"), REPO_ROOT)
    repo = os.path.join(REPO_ROOT, "candidates", candidate_name)
    if os.path.isfile(os.path.join(repo, "params.json")):
        return load_contract(repo), os.path.relpath(
            os.path.join(repo, "params.json"), REPO_ROOT)
    return None, None


def _src_ids_evidence(scoring: dict):
    """从 source_ids_exist 检查项抽取 bad_refs 证据（无该检查则 None）。"""
    for c in scoring.get("checks", []):
        if c.get("id") == "source_ids_exist":
            ev = c.get("evidence") or ""
            return [s for s in ev.split(",") if s] if ev else []
    return None


def _arm_turn_dirs(arm_dir: str):
    out_dir = os.path.join(arm_dir, "out")
    if not os.path.isdir(out_dir):
        return []
    dirs = []
    for name in sorted(os.listdir(out_dir)):
        m = TURN_DIR_RE.match(name)
        if m and os.path.isdir(os.path.join(out_dir, name)):
            dirs.append((int(m.group(1)) - 1, m.group(2),
                         os.path.join(out_dir, name)))
    dirs.sort(key=lambda x: x[0])
    return dirs


def rescore_arm(batch_dir: str, arm_dir_name: str, dry_run: bool = False,
                sidecar_dir=None):
    """重评一个 <arm>_<EP> 目录；返回 (episode 汇总, 逐轮 diff 列表)。
    sidecar_dir 提供时（GOLD_SIDECAR v1.0），逐轮尝试加载并映射 gold
    （arena/gold_sidecar.py），评分与 GOLD_SCORING_DEMO 分层数据同步产出；
    sidecar 缺失或 gold_empty 的轮与无 sidecar 完全同口径。"""
    arm_dir = os.path.join(batch_dir, arm_dir_name)
    ep_name = arm_dir_name.split("_", 1)[1]
    ep_path = os.path.join(arm_dir, ep_name + ".copy.json")
    if not os.path.isfile(ep_path):
        # 回退：目录内唯一 *.copy.json
        cands = [f for f in os.listdir(arm_dir) if f.endswith(".copy.json")]
        if len(cands) == 1:
            ep_path = os.path.join(arm_dir, cands[0])
        else:
            raise FileNotFoundError("找不到评估侧原题快照: %s" % arm_dir)

    ep = _load_json(ep_path)
    emf = os.path.join(arm_dir, "out", "episode_manifest.json")
    manifest = _load_json(emf) if os.path.isfile(emf) else {}
    import hashlib
    ep_sha = hashlib.sha256(open(ep_path, "rb").read()).hexdigest()
    integrity_ok = None
    if manifest.get("integrity", {}).get("episode_sha256"):
        integrity_ok = (manifest["integrity"]["episode_sha256"] == ep_sha)
        if not integrity_ok:
            raise RuntimeError(
                "原题快照散列与 episode_manifest 不一致（拒评）: %s" % ep_path)

    candidate_name = manifest.get("candidate", "")
    contract, contract_src = _resolve_contract(arm_dir, candidate_name)
    contract_tag = contract or "legacy"

    episode_id = ep.get("episode_id", ep_name)
    gold_demo_turns = []
    turn_results = []
    diffs = []
    for i, case_id, td in _arm_turn_dirs(arm_dir):
        s_path = os.path.join(td, "scoring.json")
        r_path = os.path.join(td, "reply_raw.txt")
        if not (os.path.isfile(s_path) and os.path.isfile(r_path)):
            turn_results.append({"turn": i + 1, "case_id": case_id,
                                 "skipped": "missing scoring/reply"})
            continue
        # 轮次对位：目录序号 i 必须与原题 turns[i].case_id 一致；不一致则按
        # case_id 精确回查（并记录），仍找不到则跳过并如实记 skipped。
        ti = i
        if ti >= len(ep.get("turns", [])) or \
                ep["turns"][ti].get("case_id") != case_id:
            hit = [k for k, t in enumerate(ep.get("turns", []))
                   if t.get("case_id") == case_id]
            if len(hit) != 1:
                turn_results.append({"turn": i + 1, "case_id": case_id,
                                     "skipped": "case_id 对位失败"})
                continue
            ti = hit[0]
        turn_eval = copy.deepcopy(ep["turns"][ti])
        raw = open(r_path, encoding="utf-8").read()
        id_map = canonical_id_map((turn_eval.get("input") or {}).get("messages"))
        id_map.update(prior_hist_id_map(ep, ti))
        sidecar_meta = None
        score_case = turn_eval
        if sidecar_dir:
            turn_gold = gold_sidecar.find_turn_gold(sidecar_dir, episode_id, case_id)
            score_case, sidecar_meta = gold_sidecar.apply_sidecar(turn_eval, turn_gold)
        new = lb_scorer.run_case_scoring_safe(score_case, raw, id_map, contract)
        # sidecar 口径落独立文件，不覆写历史 FK-SCORE-01 的 scoring_v05_fixed.json
        fixed_name = "scoring_gold_sidecar.json" if sidecar_dir else "scoring_v05_fixed.json"
        if sidecar_dir:
            contract_layer, fact_layer = _layer_split(new)
            gold_demo_turns.append({
                "turn": i + 1, "case_id": case_id,
                "arm": arm_dir_name.split("_", 1)[0],
                "gold_empty": (sidecar_meta or {}).get("gold_empty", True),
                "sidecar_applied": (sidecar_meta or {}).get("sidecar_applied", False),
                "mapped_gold_keys": (sidecar_meta or {}).get("mapped_keys", []),
                "unrated_assertions": (sidecar_meta or {}).get("unrated_assertions", []),
                "unrated_unknowns": (sidecar_meta or {}).get("unrated_unknowns", 0),
                "score10": new.get("score10"), "veto": new.get("veto"),
                "layers": {"contract": contract_layer, "fact_gold": fact_layer},
            })

        old = _load_json(s_path)
        old_veto = old.get("veto")
        new_veto = new.get("veto")
        old_src = _src_ids_evidence(old)
        new_src = _src_ids_evidence(new)
        changed = (old_veto != new_veto or old.get("score10") != new.get("score10"))
        d = {"turn": i + 1, "case_id": case_id, "arm": arm_dir_name.split("_", 1)[0],
             "changed": changed,
             "old_veto": old_veto, "new_veto": new_veto,
             "old_score10": old.get("score10"), "new_score10": new.get("score10"),
             "source_ids_before": old_src, "source_ids_after": new_src}
        if changed:
            d["veto_cleared"] = bool(old_veto and not new_veto)
            d["veto_added"] = bool(new_veto and not old_veto)
            d["veto_kind_changed"] = bool(old_veto and new_veto and
                                          old_veto != new_veto)
        diffs.append(d)
        turn_results.append({"turn": i + 1, "case_id": case_id,
                             "old_score10": old.get("score10"),
                             "new_score10": new.get("score10"),
                             "old_veto": old_veto, "new_veto": new_veto,
                             "changed": changed,
                             "fixed_file": fixed_name})
        if not dry_run:
            _write_json(os.path.join(td, fixed_name), new)

    scored = [t for t in turn_results if "old_score10" in t]
    n = len(scored)
    summary = {
        "arm_dir": arm_dir_name,
        "episode_id": episode_id,
        "episode_snapshot": os.path.relpath(ep_path, REPO_ROOT),
        "episode_sha256_prefix": ep_sha[:16],
        "integrity_match": integrity_ok,
        "candidate": candidate_name,
        "contract": contract_tag,
        "contract_resolved_from": contract_src,
        "n_turns_scored": n,
        "mean_score10_old": round(sum(t["old_score10"] for t in scored) / n, 4) if n else None,
        "mean_score10_new": round(sum(t["new_score10"] for t in scored) / n, 4) if n else None,
        "zero_score_old": sum(1 for t in scored if t["old_score10"] == 0),
        "zero_score_new": sum(1 for t in scored if t["new_score10"] == 0),
        "veto_turns_old": sum(1 for t in scored if t["old_veto"]),
        "veto_turns_new": sum(1 for t in scored if t["new_veto"]),
        "turns": turn_results,
    }
    if sidecar_dir:
        summary["gold_demo_turns"] = gold_demo_turns
    return summary, diffs


def _write_gold_demo(batch_dir: str, episode_summaries: list):
    """GOLD_SCORING_DEMO.json：contract / fact_gold / unrated 三层（rubric §2 诚实登记）。"""
    turns = [t for s in episode_summaries for t in s.get("gold_demo_turns", [])]
    def _agg(layer_name):
        vals = [t["layers"][layer_name] for t in turns if t.get("sidecar_applied")]
        got = sum(v["pts_got"] for v in vals)
        tot = sum(v["pts_total"] for v in vals)
        return {"n_turns_with_checks": sum(1 for v in vals if v["pts_total"]),
                "pts_got": got, "pts_total": tot,
                "score10": round(got / tot * 10, 2) if tot else None}
    demo = {
        "tool": "tools/rescore_batch.py --sidecar-dir",
        "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "batch": os.path.basename(os.path.normpath(batch_dir)),
        "schema": "benchmark/stories_v2/GOLD_SCHEMA.md v1.0",
        "layering_note": "contract=与 gold 无关检查（schema/source_ids/AI味/风格/note 诊断）；"
                         "fact_gold=消费 gold 的事实/意图/unknown/禁词检查；"
                         "unrated=gold_empty 轮与 confirmed 证据原子（诚实登记，不冒通过）",
        "zero_model_calls": True,
        "aggregate": {
            "n_turns": len(turns),
            "n_turns_sidecar_applied": sum(1 for t in turns if t.get("sidecar_applied")),
            "n_turns_gold_empty": sum(1 for t in turns if t.get("gold_empty")),
            "contract_layer": _agg("contract"),
            "fact_gold_layer": _agg("fact_gold"),
            "unrated_assertion_reasons": dict(collections.Counter(
                u.get("reason") for t in turns for u in t.get("unrated_assertions", []))),
            "n_unrated_unknowns_no_signal": sum(
                t.get("unrated_unknowns", 0) for t in turns),
        },
        "turns": turns,
    }
    rep = os.path.join(batch_dir, "_reports")
    os.makedirs(rep, exist_ok=True)
    out = os.path.join(rep, "GOLD_SCORING_DEMO.json")
    _write_json(out, demo)
    print("[rescore] GOLD_SCORING_DEMO -> %s" % out)
    return demo


def rescore_batch(batch_dir: str, only_eps=None, only_arms=None,
                  dry_run: bool = False, sidecar_dir=None):
    batch_name = os.path.basename(os.path.normpath(batch_dir))
    entries = sorted(e for e in os.listdir(batch_dir)
                     if re.match(r"^(candidate|parent)_.+", e)
                     and os.path.isdir(os.path.join(batch_dir, e)))
    if only_arms:
        entries = [e for e in entries if e.split("_", 1)[0] in only_arms]
    if only_eps:
        entries = [e for e in entries if e.split("_", 1)[1] in only_eps]

    all_diffs = []
    episode_summaries = []
    for e in entries:
        s, diffs = rescore_arm(batch_dir, e, dry_run=dry_run,
                               sidecar_dir=sidecar_dir)
        episode_summaries.append(s)
        all_diffs.extend(diffs)
        print("[rescore] %-14s n=%-3d mean %.4f -> %.4f | zero %d -> %d | "
              "changed %d" % (
                  e, s["n_turns_scored"],
                  s["mean_score10_old"] or 0, s["mean_score10_new"] or 0,
                  s["zero_score_old"], s["zero_score_new"],
                  sum(1 for d in diffs if d["changed"])))

    def arm_agg(prefix):
        eps = [s for s in episode_summaries if s["arm_dir"].startswith(prefix + "_")]
        n = sum(s["n_turns_scored"] for s in eps)
        if not n:
            return None
        old = [t["old_score10"] for s in eps for t in s["turns"] if "old_score10" in t]
        new = [t["new_score10"] for s in eps for t in s["turns"] if "old_score10" in t]
        return {"episodes": [s["episode_id"] for s in eps], "n_turns": n,
                "mean_score10_old": round(sum(old) / len(old), 4),
                "mean_score10_new": round(sum(new) / len(new), 4),
                "zero_score_old": sum(1 for v in old if v == 0),
                "zero_score_new": sum(1 for v in new if v == 0)}

    changed = [d for d in all_diffs if d["changed"]]
    unchanged = len(all_diffs) - len(changed)
    batch_summary = {
        "tool": "tools/rescore_batch.py",
        "rescored_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "batch": batch_name,
        "batch_dir": os.path.normpath(batch_dir),
        "scope_note": "FK-SCORE-01 修复后口径离线重评；零模型调用；"
                      "只新增 scoring_v05_fixed.json 与本汇总，不改任何历史产物",
        "id_map_note": "lb_runner.canonical_id_map(turn) + "
                       "trusted_controller.prior_hist_id_map(ep, i)（直接 import）",
        "episodes": episode_summaries,
        "aggregate": {
            "candidate": arm_agg("candidate"),
            "parent": arm_agg("parent"),
            "n_turn_diffs_total": len(all_diffs),
            "n_changed": len(changed),
            "n_unchanged": unchanged,
            "veto_cleared": [d for d in changed if d.get("veto_cleared")],
            "veto_added": [d for d in changed if d.get("veto_added")],
            "veto_kind_changed": [d for d in changed if d.get("veto_kind_changed")],
            "score_only_changed": [d for d in changed
                                   if not d.get("veto_cleared")
                                   and not d.get("veto_added")
                                   and not d.get("veto_kind_changed")],
        },
        "turn_diffs": all_diffs,
    }
    if not dry_run:
        rep = os.path.join(batch_dir, "_reports")
        os.makedirs(rep, exist_ok=True)
        # sidecar 口径另存新文件，不覆盖历史 FK-SCORE-01 汇总（§9 报告不冒充绑定）
        suffix = "_GOLDSIDECAR" if sidecar_dir else ""
        out = os.path.join(rep, "RESCORE_SUMMARY_%s%s.json" % (batch_name, suffix))
        _write_json(out, batch_summary)
        print("[rescore] 汇总 -> %s" % out)
        if sidecar_dir:
            _write_gold_demo(batch_dir, episode_summaries)
    else:
        print("[rescore] dry-run：未落盘")
    return batch_summary


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("batch_dir")
    ap.add_argument("--ep", action="append", default=[],
                    help="只评指定 episode（可多次）")
    ap.add_argument("--arm", action="append", default=[],
                    choices=["candidate", "parent"], help="只评指定臂")
    ap.add_argument("--dry-run", action="store_true",
                    help="只计算与打印，不写任何文件")
    ap.add_argument("--sidecar-dir", default=None,
                    help="GOLD_SIDECAR 目录（GOLD_SCHEMA.md v1.0）；提供即逐轮映射 gold"
                         "并产出 _reports/GOLD_SCORING_DEMO.json；缺省 = 纯契约重评（历史口径）")
    args = ap.parse_args()
    rescore_batch(args.batch_dir, only_eps=set(args.ep) or None,
                  only_arms=set(args.arm) or None, dry_run=args.dry_run,
                  sidecar_dir=args.sidecar_dir)


if __name__ == "__main__":
    main()
