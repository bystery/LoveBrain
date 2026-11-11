# -*- coding: utf-8 -*-
r"""tools/aggregate_s1_scorecard.py — 把 worker 逐位判词 + 真实答卷底账聚成可复核成绩表（零调用）。

输出层次（互不冒充，AGENTS §3.1/交付口径）：
  T 传输层：授权账本真实事件（started/completed/failed）
  P 原始协议层：评分器 veto / json_schema 失败计数
  C 消费后可编辑层：首选是否非空 + 非空备选数量
  S 语义层：worker 逐维 0—3 判词（只打该位点卡片适用维度）
  W 用户愿发：一律 unknown（AI 不代投）
另出：逐维双臂均值、逐故事配对胜负、严重错、资源（真实 usage/时延分位/cache unknown）。

用法：
  set PYTHONIOENCODING=utf-8
  D:\anaconda\python.exe tools\bind_answers.py --skeleton ... --out ..._SCORE_BINDING.json
  D:\anaconda\python.exe tools\aggregate_s1_scorecard.py \
      --skeleton refs/.../S1_SCORE_SKELETON.json \
      --scores refs/.../S1_WORKER_SCORES.py \
      --binding refs/.../S1_SCORE_BINDING.json \
      --ledger logs/auth_budget/AUTH-P08-S1-20261028-01.jsonl \
      --out-json refs/.../S1_SCORECARD.json --out-md refs/.../S1_SCORECARD.md

判词绑定（2026-10-28 复核修复）：判词只能套在它当初判的那批答案上。
  --binding 缺省 → 拒跑（退出码 2）；--allow-unbound 显式降级并在结果里标 UNBOUND。
  绑定文件 digest 自校验失败、或判词文件 BINDING_DIGEST 与绑定不符 → 整批拒收（退出码 2）。
  逐位点比对 run_id / case_input_sha256 / 原始答卷 hash / 首选 hash，不符的位点不评、
  不进均值与胜负、交付层统计也剔除，最后以退出码 3 明示。
"""
import argparse
import collections
import hashlib
import importlib.util
import json
import os
import statistics
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIMS = ["goal", "factuality", "attribution", "time_state", "boundary",
        "memory_use", "interaction", "initiative", "emotion_relation"]


def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def load_scores(path):
    spec = importlib.util.spec_from_file_location("worker_scores", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def sha_text(s):
    if s is None:
        return None
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def sha_file_bytes(p):
    if not os.path.isfile(p):
        return None
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def rel_any(p):
    """跨盘时 os.path.relpath 会抛 ValueError（C: 临时目录 vs D: 仓库），退回绝对路径。"""
    try:
        return os.path.relpath(p, ROOT).replace("\\", "/")
    except ValueError:
        return os.path.abspath(p).replace("\\", "/")


def binding_recompute_digest(bd):
    """与 tools/bind_answers.py 完全相同的规范化算法。"""
    payload = {"arms": bd.get("arms"), "exam_dir": bd.get("exam_dir"),
               "positions": bd.get("positions")}
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True,
                     separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def verify_binding(sk, bd, idx, current_identity=None):
    """逐位点 × 逐臂把骨架里的答案/题面/运行身份与绑定对账。

    返回 (rejected: {case_id: [reasons]}, stats)。只认完全相等的字段：
    run_id、case_input_sha256、原始答卷文本 hash、首选文本 hash、磁盘文件 hash；
    另外把绑定里记录的两臂 harness 身份与当前 EXAM_INDEX 再核一遍。
    骨架里没有、绑定里也没有的字段不冒充通过——直接记为理由。
    """
    rejected, stats = {}, collections.Counter()
    arms = sk["arms"]
    if bd.get("arms") != arms:
        return ({cid: ["binding_arms_mismatch"] for cid in idx},
                collections.Counter({"binding_arms_mismatch": len(idx)}))
    if bd.get("exam_dir") != sk["exam_dir"]:
        return ({cid: ["binding_exam_dir_mismatch"] for cid in idx},
                collections.Counter({"binding_exam_dir_mismatch": len(idx)}))
    if current_identity:
        for role in arms:
            bi = (bd.get("role_identity") or {}).get(role) or {}
            ci = current_identity.get(role) or {}
            if (bi.get("harness_identity_sha256") and ci.get("harness_identity_sha256")
                    and bi["harness_identity_sha256"] != ci["harness_identity_sha256"]):
                return ({cid: ["%s_harness_identity_mismatch" % role] for cid in idx},
                        collections.Counter({"harness_identity_mismatch": len(idx)}))
    bpos = bd.get("positions") or {}
    for cid, p in idx.items():
        reasons = []
        rec = bpos.get(cid)
        if not rec:
            reasons.append("position_not_in_binding")
        else:
            for role, arm_name in arms.items():
                ar = (p.get("arms") or {}).get(arm_name) or {}
                br = rec.get(role) or {}
                if not br:
                    reasons.append("%s_not_in_binding" % role)
                    continue
                pairs = (("run_id", ar.get("run_id"), br.get("run_id")),
                         ("case_input_sha256", ar.get("case_input_sha256"),
                          br.get("case_input_sha256")),
                         ("raw_reply_sha256", sha_text(ar.get("raw_text")),
                          br.get("raw_reply_sha256")),
                         ("primary_sha256", sha_text(ar.get("primary")),
                          br.get("primary_sha256")))
                for name, cur, bound in pairs:
                    if name not in br:
                        reasons.append("%s_%s_unbound" % (role, name))
                    elif cur != bound:
                        # None==None 是合法一致（如静默轮的 raw_text 为空）；
                        # 只有"绑定侧没有这个字段"才记为未绑定。
                        reasons.append("%s_%s_mismatch" % (role, name))
                # 答案文件本身：绑定里记了 run_dir 与文件散列时，重新从磁盘核一遍
                if br.get("run_dir") and br.get("raw_file_sha256"):
                    fp = os.path.join(ROOT, br["run_dir"], "reply_raw.txt")
                    cur_file = sha_file_bytes(fp)
                    if cur_file is None:
                        reasons.append("%s_raw_file_missing" % role)
                    elif cur_file != br["raw_file_sha256"]:
                        reasons.append("%s_raw_file_mismatch" % role)
        if reasons:
            rejected[cid] = reasons
            stats["rejected_positions"] += 1
            stats["rejected_cells"] += len(reasons)
        else:
            stats["verified_positions"] += 1
    extra = sorted(set(bpos) - set(idx))
    stats["binding_positions_not_in_skeleton"] = len(extra)
    return rejected, stats


def pct(q, vals):
    if not vals:
        return None
    v = sorted(vals)
    k = max(0, min(len(v) - 1, int(round(q * (len(v) - 1)))))
    return v[k]


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--skeleton", required=True)
    ap.add_argument("--scores", required=True)
    ap.add_argument("--binding", default=None,
                    help="tools/bind_answers.py 产出的答案绑定；缺省且未 --allow-unbound 则拒跑")
    ap.add_argument("--allow-unbound", action="store_true",
                    help="显式降级：不校验答案绑定（结果里标 UNBOUND，不得当已复核交付）")
    ap.add_argument("--ledger", required=True)
    ap.add_argument("--label", default="S1")
    ap.add_argument("--out-json", required=True)
    ap.add_argument("--out-md", required=True)
    a = ap.parse_args(argv)

    sk = load(os.path.abspath(a.skeleton))
    mod = load_scores(os.path.abspath(a.scores))
    arms = list(sk["arms"].values())
    parent, cand = arms[0], arms[1]
    role_harness = {"parent": parent, "candidate": cand}

    # 两臂身份：按 role（parent/candidate）去 EXAM_<role>/EXAM_INDEX.json 取，不再拿 harness 名当目录名。
    ident, role_identity, batch_id, auth_id = {}, {}, None, None
    for role in ("parent", "candidate"):
        ip = os.path.join(ROOT, sk["exam_dir"], "EXAM_%s" % role, "EXAM_INDEX.json")
        if os.path.isfile(ip):
            d = load(ip)
            role_identity[role] = {
                "harness": d.get("harness") or role_harness.get(role),
                "harness_identity_sha256": d.get("harness_identity_sha256"),
                "authorization_id": d.get("authorization_id"),
                "batch_id": d.get("batch_id"),
            }
            ident[role_harness.get(role, role)] = d.get("harness_identity_sha256")
            batch_id = batch_id or d.get("batch_id")
            auth_id = auth_id or d.get("authorization_id")
    sk.setdefault("batch_id", batch_id)

    idx = {p["case_id"]: p for p in sk["positions"]}

    # —— 答案绑定：判词只能套在它当初判的那批答案上 ——
    binding = {"status": "UNBOUND（显式 --allow-unbound，不得当已复核交付）"}
    rejected = {}
    if a.binding:
        bd = load(os.path.abspath(a.binding))
        recomputed = binding_recompute_digest(bd)
        if bd.get("digest_sha256") != recomputed:
            print("BINDING DIGEST MISMATCH: file=%s recomputed=%s —— 绑定文件被改过，整批拒收"
                  % (bd.get("digest_sha256"), recomputed), file=sys.stderr)
            return 2
        declared = getattr(mod, "BINDING_DIGEST", None)
        if declared is not None and declared != recomputed:
            print("SCORES BINDING_DIGEST MISMATCH: scores=%s binding=%s —— 判词写于另一批答案，整批拒收"
                  % (declared, recomputed), file=sys.stderr)
            return 2
        rejected, bstats = verify_binding(sk, bd, idx, role_identity)
        binding = {
            "status": "VERIFIED_PARTIAL" if rejected else "VERIFIED_ALL",
            "binding_file": rel_any(os.path.abspath(a.binding)),
            "digest_sha256": recomputed,
            "scores_declared_digest": declared,
            "verified_positions": bstats["verified_positions"],
            "rejected_positions": len(rejected),
            "rejected_detail": rejected,
            "binding_positions_not_in_skeleton": bstats[
                "binding_positions_not_in_skeleton"],
            "checked_fields": ["run_id", "case_input_sha256",
                               "raw_reply_sha256", "primary_sha256",
                               "raw_file_sha256（磁盘上的 reply_raw.txt）"],
        }
    elif not a.allow_unbound:
        print("缺少 --binding：先跑 tools/bind_answers.py 生成绑定，或用 --allow-unbound 显式降级",
              file=sys.stderr)
        return 2

    seen = set()
    dim_cells = {arm: collections.defaultdict(list) for arm in arms}
    story_cells = collections.defaultdict(lambda: collections.defaultdict(list))
    paired = []
    missing_pos, mismatch_dim = [], []
    for case_id, dims, note in mod.SCORES:
        seen.add(case_id)
        p = idx.get(case_id)
        if not p:
            missing_pos.append(case_id)
            continue
        if case_id in rejected:
            continue                       # 绑定不符：该位点不评，不进任何均值/胜负
        tot = {}
        for arm in arms:
            tot[arm] = 0
            for d, (pv, cv) in dims.items():
                if d not in (p["card_dims"] or []):
                    mismatch_dim.append("%s:%s" % (case_id, d))
                val = pv if arm == parent else cv
                dim_cells[arm][d].append(val)
                story_cells[case_id.split("_")[0]][arm].append(val)
                tot[arm] += val
        paired.append({"case_id": case_id, "parent_total": tot[parent],
                       "candidate_total": tot[cand],
                       "winner": ("candidate" if tot[cand] > tot[parent] else
                                  "parent" if tot[parent] > tot[cand] else "tie"),
                       "note": note})
    unjudged = [c for c in idx if c not in seen]
    unrated_binding = [c for c in idx if c in rejected]

    dim_mean = {arm: {d: round(statistics.fmean(v), 3) for d, v in
                      sorted(dim_cells[arm].items())} for arm in arms}
    dim_n = {arm: {d: len(v) for d, v in sorted(dim_cells[arm].items())}
             for arm in arms}
    story_mean = {ep: {arm: round(statistics.fmean(v), 3) for arm, v in
                       sorted(d.items())} for ep, d in sorted(story_cells.items())}

    # 协议/消费层：从 skeleton 的 program 与 primary 重算（不采信判词文件里的描述）
    layer = {arm: {"positions": 0, "protocol_fail": 0, "no_primary": 0,
                   "legal_no_reply": 0, "empty_without_action": 0,
                   "backups_nonempty": 0, "prompt_tokens": 0, "completion_tokens": 0,
                   "latency_ms": [], "veto_kinds": collections.Counter(),
                   "cache_unknown": 0} for arm in arms}
    for case_id, p in idx.items():
        if case_id in rejected:
            continue                      # 绑定不符的答案不进交付层统计
        for arm, d in p["arms"].items():
            L = layer[arm]
            L["positions"] += 1
            if d["program"]["veto"]:
                L["protocol_fail"] += 1
                L["veto_kinds"][str(d["program"]["veto"])] += 1
            if not (d["primary"] or "").strip():
                L["no_primary"] += 1
                # 空正文有两种：①候选显式 action=no_reply 的合法静默（本轮本来就不该发消息，
                # 交付结果符合设计）；②格式损坏导致无话可发（真故障）。两者绝不合并计。
                raw = d.get("raw_text") or ""
                act = None
                try:
                    act = (json.loads(raw) or {}).get("action")
                except Exception:                                      # noqa: BLE001
                    act = None
                if act == "no_reply":
                    L["legal_no_reply"] += 1
                elif not d["program"]["veto"]:
                    L["empty_without_action"] += 1     # 无 veto 却空正文：异常，单独留证
            L["backups_nonempty"] += sum(1 for s, t in d["backups"]
                                         if isinstance(t, str) and t.strip())
            L["prompt_tokens"] += d["tokens"]["prompt"] or 0
            L["completion_tokens"] += d["tokens"]["completion"] or 0
            if d["latency_ms"]:
                L["latency_ms"].append(d["latency_ms"])
            if d["tokens"].get("cache") is None:
                L["cache_unknown"] += 1

    led = collections.Counter()
    for line in open(os.path.abspath(a.ledger), encoding="utf-8"):
        line = line.strip()
        if line:
            led[json.loads(line)["kind"]] += 1

    wins = collections.Counter(x["winner"] for x in paired)
    tot_tok = {arm: layer[arm]["prompt_tokens"] + layer[arm]["completion_tokens"]
               for arm in arms}
    per_cost = {arm: (round(tot_tok[arm] / max(1, layer[arm]["positions"]), 1))
                for arm in arms}
    delta = {arm: None for arm in arms}
    if parent in tot_tok and cand in tot_tok and tot_tok[parent]:
        delta = {"total_tokens_change":
                 round((tot_tok[cand] - tot_tok[parent]) / tot_tok[parent] * 100, 2),
                 "prompt_change_pct": round(
                     (layer[cand]["prompt_tokens"] - layer[parent]["prompt_tokens"])
                     / layer[parent]["prompt_tokens"] * 100, 2),
                 "completion_change_pct": round(
                     (layer[cand]["completion_tokens"] - layer[parent]["completion_tokens"])
                     / layer[parent]["completion_tokens"] * 100, 2) if layer[parent][
                     "completion_tokens"] else None,
                 "p50_change_pct": round(
                     (pct(.5, layer[cand]["latency_ms"]) - pct(.5, layer[parent]["latency_ms"]))
                     / pct(.5, layer[parent]["latency_ms"]) * 100, 2),
                 "p95_change_pct": round(
                     (pct(.95, layer[cand]["latency_ms"]) - pct(.95, layer[parent]["latency_ms"]))
                     / pct(.95, layer[parent]["latency_ms"]) * 100, 2)}

    out = {
        "kind": "pair_scorecard_v1", "label": a.label,
        "batch_id": sk.get("batch_id"), "arms": sk["arms"],
        "role_identity": role_identity,
        "cards_file": sk["cards_file"], "exam_dir": sk["exam_dir"],
        "judge": "worker 亲读全部首选与非空备选后手填（tools/build_pair_review_dump.py 摊开原文）",
        "scale": sk["scale"],
        "answer_binding": binding,
        "coverage": {"positions_in_answers": len(idx), "positions_judged": len(paired),
                     "unjudged_positions": unjudged, "missing_from_answers": missing_pos,
                     "unrated_due_to_binding": unrated_binding,
                     "dims_scored_off_card": mismatch_dim,
                     "cells_per_arm": sum(len(v) for v in
                                          dim_cells[cand].values())},
        "transport_layer_from_ledger": {"authorization_id": os.path.basename(
            a.ledger).replace(".jsonl", ""), "events": dict(led)},
        "five_layer_delivery": {arm: {
            "T_transport_failed_events": dict(led).get("attempt_failed", 0),
            "P_protocol_positions": layer[arm]["positions"],
            "P_protocol_failures": layer[arm]["protocol_fail"],
            "P_veto_kinds": dict(layer[arm]["veto_kinds"]),
            "C_usable_primary": layer[arm]["positions"] - layer[arm]["no_primary"],
            "C_no_primary": layer[arm]["no_primary"],
            "C_legal_no_reply": layer[arm]["legal_no_reply"],
            "C_empty_without_action": layer[arm]["empty_without_action"],
            "C_note": ("C_usable_primary 不含合法静默：候选显式 action=no_reply 的轮次"
                       "'本轮不发消息'就是交付结果，不计为失败；两者已分列"),
            "C_nonempty_backup_slots": layer[arm]["backups_nonempty"],
            "S_semantic_positions_judged": sum(1 for x in paired),
            "W_user_willing": "unknown（AI 不代投）",
        } for arm in arms},
        "legal_no_reply_review": getattr(mod, "LEGAL_NO_REPLY", {}),
        "over_decline_review": getattr(mod, "OVER_DECLINE", {}),
        "card_tension_note": getattr(mod, "CARD_TENSION_NOTE", ""),
        "backup_risks": getattr(mod, "BACKUP_RISKS", []),
        "paired_position_verdicts": {"wins_candidate": wins["candidate"],
                                     "wins_parent": wins["parent"], "ties": wins["tie"],
                                     "n": len(paired)},
        "per_dimension_mean": {"parent": dim_mean[parent], "candidate": dim_mean[cand]},
        "per_dimension_cells": {"parent": dim_n[parent], "candidate": dim_n[cand]},
        "per_story_mean": story_mean,
        "severe_errors": getattr(mod, "SEVERE_ERRORS", []),
        "protocol_failure_notes": getattr(mod, "PROTOCOL_FAILURE_NOTES", {}),
        "resources": {arm: {
            "prompt_tokens": layer[arm]["prompt_tokens"],
            "completion_tokens": layer[arm]["completion_tokens"],
            "total_tokens": tot_tok[arm],
            "tokens_per_position": per_cost[arm],
            "latency_p50_ms": pct(.5, layer[arm]["latency_ms"]),
            "latency_p95_ms": pct(.95, layer[arm]["latency_ms"]),
            "latency_max_ms": max(layer[arm]["latency_ms"] or [0]),
            "cache_usage_unknown_positions": layer[arm]["cache_unknown"],
            "money": None, "money_reason": "无可核单价，金额恒 null",
        } for arm in arms},
        "cross_arm_delta_candidate_vs_parent": delta,
        "positions": paired,
    }
    oj = os.path.abspath(a.out_json)
    os.makedirs(os.path.dirname(oj), exist_ok=True)
    out["arm_identity_sha256"] = ident
    json.dump(out, open(oj, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    if binding.get("digest_sha256"):
        bind_line = "%s｜digest=%s｜校验位点=%s 拒收=%s" % (
            binding["status"], binding["digest_sha256"],
            binding.get("verified_positions"), binding.get("rejected_positions"))
    else:
        bind_line = binding["status"]
    L = ["# %s 逐维成绩与配对判决（worker 亲读判词聚合，非裁判代填）" % a.label, "",
         "批=%s｜授权=%s（账本事件 %s）" % (out["batch_id"],
                                        out["transport_layer_from_ledger"]["authorization_id"],
                                        out["transport_layer_from_ledger"]["events"]),
         "两臂身份（role→harness / harness_identity_sha256）：%s"
         % json.dumps(role_identity, ensure_ascii=False),
         "答案绑定：%s" % bind_line,
         "", "## 五层交付（互不冒充）", "",
         "| 层 | %s | %s |" % (parent, cand), "|---|---|---|"]
    for arm_key, nm in (("T_transport_failed_events", "T 传输失败事件"),
                        ("P_protocol_failures", "P 原始协议失败位点"),
                        ("C_usable_primary", "C 消费后可编辑首选"),
                        ("C_nonempty_backup_slots", "C 非空备选槽"),
                        ("S_semantic_positions_judged", "S 语义已审位点"),
                        ("W_user_willing", "W 用户愿发")):
        r = out["five_layer_delivery"]
        L.append("| %s | %s | %s |" % (nm, r[parent][arm_key], r[cand][arm_key]))
    L += ["", "## 逐维均值（只打该位点适用维度）", "",
          "| 维度 | %s | %s | 格数(父/候) |" % (parent, cand), "|---|---|---|---|"]
    for d in DIMS:
        pv, cv = dim_mean[parent].get(d), dim_mean[cand].get(d)
        if pv is None and cv is None:
            continue
        L.append("| %s | %s | %s | %s/%s |" % (d, pv, cv, dim_n[parent].get(d, 0),
                                              dim_n[cand].get(d, 0)))
    L += ["", "## 逐故事总分均值", "", "| 故事 | %s | %s |" % (parent, cand),
          "|---|---|---|"]
    for ep, v in story_mean.items():
        L.append("| %s | %s | %s |" % (ep, v[parent], v[cand]))
    L += ["", "## 配对位点判决",
          "", "候选胜 %s ／ 原版胜 %s ／ 平 %s（n=%s）" % (wins["candidate"], wins["parent"],
                                              wins["tie"], len(paired)),
          "", "严重错登记：%s" % (out["severe_errors"] or
                             "无（本批已判 %d 位点逐位亲读，无重大事实/主体/时序反向错误）" % len(paired)),
          "", "## 资源（真实 usage，非字符估计）", "",
          "| 项 | %s | %s |" % (parent, cand), "|---|---|---|"]
    R = out["resources"]
    for key, nm in (("prompt_tokens", "prompt tokens"), ("completion_tokens", "completion tokens"),
                    ("total_tokens", "总 tokens"), ("tokens_per_position", "每位点 tokens"),
                    ("latency_p50_ms", "时延 p50 ms"), ("latency_p95_ms", "时延 p95 ms"),
                    ("latency_max_ms", "时延 max ms"),
                    ("cache_usage_unknown_positions", "cache unknown 位点数")):
        L.append("| %s | %s | %s |" % (nm, R[parent][key], R[cand][key]))
    L.append("| 金额 | null（无可核单价） | null（无可核单价） |")
    L += ["", "跨臂差（候选相对原版）：%s" % json.dumps(
        out["cross_arm_delta_candidate_vs_parent"], ensure_ascii=False),
        "", "## 逐位判词（决定性证据）", "",
        "| 位点 | %s 总 | %s 总 | 胜 | 依据 |" % (parent, cand), "|---|---|---|---|---|"]
    for x in paired:
        L.append("| %s | %s | %s | %s | %s |" % (x["case_id"], x["parent_total"],
                                                x["candidate_total"], x["winner"],
                                                x["note"].replace("|", "／")))
    if out["coverage"]["unjudged_positions"]:
        L += ["", "未审位点（不得当通过）：%s" % ", ".join(out["coverage"]["unjudged_positions"])]
    if out["coverage"]["unrated_due_to_binding"]:
        L += ["", "**绑定不符、已拒评的位点（不得当通过）**：%s"
              % ", ".join(out["coverage"]["unrated_due_to_binding"])]
        for cid in out["coverage"]["unrated_due_to_binding"]:
            L.append("  - %s：%s" % (cid, ", ".join(rejected.get(cid, []))))
    if out["coverage"]["dims_scored_off_card"]:
        L += ["", "打在卡片适用维度之外的格子（登记备查）：%s"
              % ", ".join(out["coverage"]["dims_scored_off_card"][:20])]
    om = os.path.abspath(a.out_md)
    os.makedirs(os.path.dirname(om), exist_ok=True)
    with open(om, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")
    print("judged=%d/%d wins cand/parent/tie=%s/%s/%s severe=%s unjudged=%s off_card_cells=%s "
          "binding=%s rejected=%d"
          % (len(paired), len(idx), wins["candidate"], wins["parent"], wins["tie"],
             len(out["severe_errors"]), len(out["coverage"]["unjudged_positions"]),
             len(out["coverage"]["dims_scored_off_card"]), binding["status"], len(rejected)))
    print("dim_mean parent=", json.dumps(dim_mean[parent], ensure_ascii=False))
    print("dim_mean cand  =", json.dumps(dim_mean[cand], ensure_ascii=False))
    print("delta=", json.dumps(delta, ensure_ascii=False))
    print("-> %s / %s" % (rel_any(oj), rel_any(om)))
    if rejected:
        print("BINDING REJECTED %d position(s): %s"
              % (len(rejected), json.dumps(rejected, ensure_ascii=False)), file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())
