#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""l3b_frozen_fork.py — L3B 冻结分叉实验（第七波判决 §4 收口版，v4）。

取代旧"有前史/无前史"CTRL 设计（第五波判决 §6.3）。

流程（每步落 fork_log.jsonl：事件/时间/pid/散列）：
  P0 冻结点：同一前史（L3B_MINI_FULL t1-t4）跑到 stop_after=4，独立子进程执行；
     或 --freeze-from 复用（复用必须通过冻结源校验，见下）。记录 checkpoint.bin
     sha256 + sandbox 文件清单散列 + 前史运行的完整性锚。
  P1 分叉：每分支**新进程、新目录** restore 同一快照，分支 runner 以
     --run-id <fork_run_id>/<branch> 绑定本次实验身份。
     分支 A（full）= 完整记忆；分支 B（memory_off）= --memory-off。
  P2 后测身份验证：每个后测节点必须**本次执行**——manifest.run_id 属本次分支、
     gateway 事件含本 run 的 attempt 记录；复制来的旧后测产物一律拒绝充数。
  P3 报告：验证全部通过后才写 fork_summary.json（否则拒绝完成态、落失败记录）。

冻结源校验（第七波判决 §4-2，任一不满足 = 在任何真实调用前拒绝并落 rejection.json）：
  - checkpoint.json 存在且 turns_done == 4（= 本实验规定的冻结阶段）；
  - episode_id / candidate 与本实验一致；前史轮 t1-t4 manifest 齐全；
  - 记录冻结源 episode/资产散列与代码版本锚；复用旧前史时如实声明
    "前史执行于旧代码版本，其散列 ≠ 本次评价代码散列"（不冒称同版本）。

交错编排（第七波判决 §9 诊断条件）：--pairs N --order AB,BA,... 预先固定
每对内分支执行次序；每对都从同一冻结点新目录恢复（不沿用上一对后测状态）。
默认 --pairs 1（= 旧 A→B 行为）。16 请求方案 = --pairs 4 --order AB,BA,BA,AB。

用法：
  python tools/l3b_frozen_fork.py --adapter mock                # 离线协议验证
  python tools/l3b_frozen_fork.py --adapter openai_compat       # 有限真实调用（待授权）
"""
import argparse
import datetime
import hashlib
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EP = os.path.join(ROOT, "benchmark", "episodes", "L3B_MINI_FULL.json")
FREEZE_AFTER = 4          # 前史 = t1-t4（含窗口驱逐轮）
POSTTEST_CASES = ("L3B_t05", "L3B_t06")
POSTTEST_IDX = {0: "L3B_t05", 1: "L3B_t06"}   # 相对后测起点（turn_05/turn_06）

CHANNELS_CLOSED = ["kv_facts（事实检索库）", "kv_ongoing（进行中事项）",
                   "topic 摘要与话题年龄", "LCS 检索缓存输出",
                   "last_clock / hours_since_last_exchange", "initial_kb 预置资源"]


def _now():
    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def _sandbox_tree(d):
    out = {}
    for root, _, files in os.walk(d):
        for fn in sorted(files):
            p = os.path.join(root, fn)
            out[os.path.relpath(p, d).replace("\\", "/")] = _sha(p)
    return out


def _jload(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


class ForkLog:
    def __init__(self, path):
        self.path = path

    def log(self, event, **kw):
        rec = {"event": event, "ts": _now(), "pid": os.getpid()}
        rec.update(kw)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False, default=str) + "\n")
        print("[fork] %s %s" % (event, kw.get("detail", "")))


def _run_runner(args_list, log, event):
    """独立子进程执行 runner（第六波判决 §6.2：记录实际子进程 pid）。"""
    cmd = [sys.executable, os.path.join(ROOT, "arena", "lb_runner.py")] + args_list
    log.log(event + "_start", detail=" ".join(args_list), cmd=cmd[0])
    p = subprocess.Popen(cmd, cwd=ROOT, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, text=True,
                         encoding="utf-8", errors="replace")
    log.log(event + "_child_pid", pid=p.pid, detail="runner subprocess")
    out, errout = p.communicate()
    if p.returncode != 0:
        raise RuntimeError("%s 失败 rc=%d\nstdout=%s\nstderr=%s"
                           % (event, p.returncode, (out or "")[-2000:],
                              (errout or "")[-2000:]))
    log.log(event + "_child_exit", pid=p.pid, rc=p.returncode)
    return (out or "").strip()


def _reject(run_dir, log, reason, checks):
    """冻结源/身份校验失败：在任何真实调用前拒绝，落 rejection.json（不可静默完成）。"""
    rec = {"rejected": True, "reason": reason, "checks": checks, "ts": _now()}
    with open(os.path.join(run_dir, "rejection.json"), "w", encoding="utf-8") as f:
        json.dump(rec, f, ensure_ascii=False, indent=2)
    log.log("rejected", reason=reason)
    raise SystemExit("冻结源/身份校验失败（已落 rejection.json）: %s" % reason)


def _validate_freeze_source(src, log, run_dir, ep_id, candidate,
                            allow_mismatch=False):
    """第七波判决 §4-2：冻结源必须是本实验规定的冻结阶段（turns_done==4），
    episode/candidate 身份一致、前史产物齐全；返回完整性锚记录。
    allow_mismatch=True（检索对照实验专用）：前史由父版本生成、后测由改进候选执行
    是实验设计本身——放行 candidate 不一致，但必须留痕（不静默）。"""
    checks = {}
    ck_json = os.path.join(src, "checkpoint.json")
    ck_bin = os.path.join(src, "checkpoint.bin")
    if not os.path.isfile(ck_json) or not os.path.isfile(ck_bin):
        _reject(run_dir, log, "冻结源缺 checkpoint.json/bin: %s" % src, {})
    ck = _jload(ck_json)
    checks["turns_done"] = ck.get("turns_done")
    checks["want_turns_done"] = FREEZE_AFTER
    checks["checkpoint_episode_id"] = ck.get("episode_id")
    checks["checkpoint_candidate"] = ck.get("candidate")
    if ck.get("turns_done") != FREEZE_AFTER:
        _reject(run_dir, log,
                "冻结源 turns_done=%s ≠ %s（不是本实验规定的冻结阶段；"
                "旧后测产物不得冒充新实验）" % (ck.get("turns_done"), FREEZE_AFTER), checks)
    if ck.get("episode_id") != ep_id:
        _reject(run_dir, log, "冻结源 episode_id=%r ≠ 本实验 %r"
                % (ck.get("episode_id"), ep_id), checks)
    if ck.get("candidate") != os.path.basename(candidate.rstrip("/\\")):
        if allow_mismatch:
            # 检索对照设计：冻结前史属父版本（同历史通道/事实），后测候选不同 = 主变量。
            # 显式留痕，不冒称同候选。
            checks["candidate_mismatch_allowed"] = {
                "freeze_source_candidate": ck.get("candidate"),
                "posttest_candidate": os.path.basename(candidate.rstrip("/\\")),
                "note": "对照实验设计：前史由父版本形成，后测由改进候选执行（唯一主变量）"}
            log.log("freeze_candidate_mismatch_allowed",
                    freeze_source=ck.get("candidate"),
                    posttest_candidate=os.path.basename(candidate.rstrip("/\\")))
        else:
            _reject(run_dir, log, "冻结源 candidate=%r ≠ 本实验 %r"
                    % (ck.get("candidate"), candidate), checks)
    # 前史轮 t1-t4 manifest 齐全
    for i in range(FREEZE_AFTER):
        td = os.path.join(src, "turn_%02d" % (i + 1))
        if not os.path.isdir(td):
            cands = [d for d in sorted(os.listdir(src))
                     if d.startswith("turn_%02d_" % (i + 1))]
            td = os.path.join(src, cands[0]) if cands else None
        if not td or not os.path.isfile(os.path.join(td, "manifest.json")):
            _reject(run_dir, log, "冻结源前史轮 %d 缺产物" % (i + 1), checks)
        checks["prehistory_turn_%d" % (i + 1)] = "ok"
    # 完整性锚：前史运行落盘的 episode/代码/资产散列（episode_manifest.integrity）
    em_p = os.path.join(src, "episode_manifest.json")
    anchor = {}
    if os.path.isfile(em_p):
        em = _jload(em_p)
        anchor = em.get("integrity") or {}
        checks["freeze_source_integrity_recorded"] = True
    else:
        checks["freeze_source_integrity_recorded"] = False
    log.log("freeze_source_validated", source=src, checks=checks,
            sha256=_sha(ck_bin))
    return {"source": src, "checkpoint_sha256": _sha(ck_bin),
            "integrity": anchor, "checks": checks}


def _verify_posttest_identity(out_b, branch, branch_run_id, log, run_dir):
    """第七波判决 §4-3 + 第八波判决 §3：后测身份验证 = 真实配对拒绝条件（非计数）。

    校验（任一不满足 = 拒绝并落 rejection.json）：
      - manifest 侧：t5/t6 manifest 存在、run_id 属本次分支、audit_errors 为空
        （审计事件写失败不得当作已验证成功通过）；
      - 事件侧：按 run_id 过滤本 run 的 attempt 事件，按 attempt_id 分组——
        每组恰有一条 started 与一条 completed/failed 终态，终态 attempt_id/case_id
        与 started 一致，started 在文件序与时间序上都先于终态；
      - 覆盖：本 run 恰有 len(POSTTEST_IDX) 组，且每组 case_id 恰为 t5/t6 各一次；
        其他 case 的组或 restore-only 记录不得补足数量；多余组同样拒绝；
      - 配对终态为 failed = 失败样本：拒绝并保留失败记录（rejection.json 带事件原文，
        不删除、不悄悄重试）。"""
    problems = []
    ev_p = os.path.join(out_b, "gateway_events.jsonl")
    events = []
    if os.path.isfile(ev_p):
        with open(ev_p, encoding="utf-8") as f:
            for ln_no, ln in enumerate(f, 1):
                ln = ln.strip()
                if not ln:
                    continue
                try:
                    events.append(json.loads(ln))
                except json.JSONDecodeError as e:
                    problems.append("gateway_events.jsonl 第%d行不可解析: %s" % (ln_no, e))

    # ---- manifest 侧：t5/t6 本次执行 + 审计写失败不得当成功 ----
    for i, cid in POSTTEST_IDX.items():
        td = os.path.join(out_b, "turn_%02d_%s" % (4 + i + 1, cid))
        mf_p = os.path.join(td, "manifest.json")
        if not os.path.isfile(mf_p):
            problems.append("%s: 后测 manifest 缺失（未在本次执行？）" % cid)
            continue
        mf = _jload(mf_p)
        if mf.get("run_id") != branch_run_id:
            problems.append("%s: manifest.run_id=%r ≠ 本次 %r（旧产物充数）"
                            % (cid, mf.get("run_id"), branch_run_id))
        if mf.get("audit_errors"):
            problems.append("%s: manifest.audit_errors=%r（审计事件写入失败，"
                            "不得作为已验证成功通过）" % (cid, mf["audit_errors"]))
        if mf.get("gateway_calls") != 1:
            problems.append("%s: manifest.gateway_calls=%r ≠ 1（本方案 t5/t6 各恰一次"
                            "新生成尝试；多阶段调用须另立显式协议，不入本次口径）"
                            % (cid, mf.get("gateway_calls")))

    # ---- 事件侧：按 attempt_id 分组配对 ----
    STARTED, TERM = "attempt_started", ("attempt_completed", "attempt_failed")
    mine = [e for e in events if e.get("run_id") == branch_run_id
            and str(e.get("kind", "")).startswith("attempt")]
    groups = {}
    for idx, e in enumerate(mine):
        aid = e.get("attempt_id")
        groups.setdefault(aid, []).append((idx, e))
    pair_problems = []
    for aid in sorted(groups, key=lambda a: (a is None, a)):
        g = groups[aid]
        kinds = [e.get("kind") for _, e in g]
        started = [e for _, e in g if e.get("kind") == STARTED]
        terms = [e for _, e in g if e.get("kind") in TERM]
        if len(g) != 2 or len(started) != 1 or len(terms) != 1:
            pair_problems.append("attempt_id=%r: 事件种类=%s（应恰 1 started + 1 终态）"
                                 % (aid, kinds))
            continue
        s_idx, s_ev = [(i, e) for i, e in g if e.get("kind") == STARTED][0]
        t_idx, t_ev = [(i, e) for i, e in g if e.get("kind") in TERM][0]
        if s_ev.get("case_id") != t_ev.get("case_id"):
            pair_problems.append(
                "attempt_id=%r: started.case_id=%r ≠ 终态.case_id=%r（跨 case 配对）"
                % (aid, s_ev.get("case_id"), t_ev.get("case_id")))
        if s_idx >= t_idx or str(t_ev.get("ts", "")) < str(s_ev.get("ts", "")):
            pair_problems.append("attempt_id=%r: started 未先于终态（文件序/时间序不合理）" % aid)
        if t_ev.get("kind") == "attempt_failed":
            pair_problems.append(
                "attempt_id=%r case_id=%r: 配对终态=failed（失败样本，按失败处理；"
                "事件原文保留于本记录，不删除、不悄悄重试）" % (aid, s_ev.get("case_id")))
    # 覆盖检查：恰 t5/t6 各一组，其他 case / 多余组不得补足
    case_groups = {}
    for aid, g in groups.items():
        started = [e for _, e in g if e.get("kind") == STARTED]
        if len(started) == 1:
            case_groups.setdefault(started[0].get("case_id"), []).append(aid)
    for cid in POSTTEST_IDX.values():
        n = len(case_groups.get(cid, []))
        if n != 1:
            pair_problems.append("case_id=%s: 本 run attempt 组数=%d ≠ 1"
                                 "（缺失=未本次执行；多个=未按单阶段口径）" % (cid, n))
    for cid, aids in sorted(case_groups.items(), key=lambda kv: str(kv[0])):
        if cid not in POSTTEST_IDX.values():
            pair_problems.append("case_id=%r: 本 run 出现后测范围外 attempt 组 %s"
                                 "（其他 case / restore 记录不得补足）" % (cid, aids))
    problems.extend(pair_problems)

    if problems:
        detail = {"branch": branch, "branch_run_id": branch_run_id,
                  "attempt_events": len(mine), "attempt_groups":
                      {str(a): [e.get("kind") for _, e in g] for a, g in groups.items()},
                  "problems": problems, "raw_attempt_events": mine}
        _reject(run_dir, log, "后测身份验证失败（%s）: %s"
                % (branch, "; ".join(problems)), detail)
    log.log("posttest_identity_verified", branch=branch,
            branch_run_id=branch_run_id,
            attempt_events=len(mine), attempt_pairs=sorted(groups.keys()))
    return len(groups)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--adapter", default="mock",
                    choices=["mock", "openai_compat", "echo"])
    ap.add_argument("--outdir", default=os.path.join(ROOT, "logs", "l3b_fork"))
    ap.add_argument("--candidate", default="phase0_beta_single")
    ap.add_argument("--freeze-from", default=None,
                    help="复用已有冻结点目录（必须通过冻结源校验：turns_done==4 等）")
    ap.add_argument("--run-id", default=None, help="覆盖自动 run_id（调试用）")
    ap.add_argument("--allow-candidate-mismatch", action="store_true",
                    help="对照实验专用：冻结前史由父版本形成、后测由不同候选执行"
                         "（同历史通道/事实；显式留痕，不静默）")
    ap.add_argument("--pairs", type=int, default=1,
                    help="重复对数（每对都从同一冻结点新目录恢复；诊断 16 请求=4）")
    ap.add_argument("--order", default="AB",
                    help="每对内分支执行次序，逗号分隔，对数须等于 pairs（如 AB,BA,BA,AB）")
    args = ap.parse_args()

    order = [x.strip().upper() for x in args.order.split(",") if x.strip()]
    if len(order) != args.pairs or any(set(o) != {"A", "B"} for o in order):
        raise SystemExit("--order 必须给出 pairs 个 AB/BA 组合（如 AB,BA,BA,AB）")

    # ---- 唯一 run_id + 独立不可覆盖目录（第六波判决 §6.1） ----
    run_id = args.run_id or ("l3bfork_%s_pid%d" % (
        datetime.datetime.now().strftime("%Y%m%d_%H%M%S"), os.getpid()))
    run_dir = os.path.join(args.outdir, run_id)
    if os.path.exists(run_dir):
        raise RuntimeError("run 目录已存在，拒绝覆盖（历史证据不可销毁）: %s" % run_dir)
    os.makedirs(run_dir, exist_ok=True)
    index_path = os.path.join(args.outdir, "INDEX.jsonl")
    with open(index_path, "a", encoding="utf-8") as f:
        f.write(json.dumps({"run_id": run_id, "ts": _now(), "pid": os.getpid(),
                            "adapter": args.adapter, "candidate": args.candidate,
                            "pairs": args.pairs, "order": order},
                           ensure_ascii=False) + "\n")
    log = ForkLog(os.path.join(run_dir, "fork_log.jsonl"))
    log.log("experiment_start", run_id=run_id, adapter=args.adapter,
            candidate=args.candidate, pairs=args.pairs, order=order,
            design="freeze-fork v4（第七波判决 §4：冻结源防错+新后测身份锁定）")

    ep_id = _jload(EP).get("episode_id")
    out_freeze = os.path.join(run_dir, "freeze")
    ck_bin = os.path.join(out_freeze, "checkpoint.bin")

    # ---- P0 冻结点 ----
    if args.freeze_from:
        import shutil
        src = os.path.abspath(args.freeze_from)
        freeze_info = _validate_freeze_source(src, log, run_dir, ep_id, args.candidate,
                                              allow_mismatch=args.allow_candidate_mismatch)
        os.makedirs(out_freeze, exist_ok=True)
        shutil.copytree(src, out_freeze, dirs_exist_ok=True,
                        ignore=shutil.ignore_patterns("gateway_events.jsonl"))
        log.log("freeze_reused", detail=src,
                sha256=freeze_info["checkpoint_sha256"],
                # 第七波判决 §4-2：如实声明——复用的前史执行于旧代码版本
                version_note="复用前史：其执行版本见 freeze_source.integrity（旧代码锚），"
                             "与本 fork 的评价代码散列不同属预期，不冒称同版本")
    else:
        _run_runner(["--episode", EP, "--candidate", args.candidate,
                     "--out", out_freeze, "--adapter", args.adapter,
                     "--stop-after", str(FREEZE_AFTER)], log, "prehistory_run")
        freeze_info = _validate_freeze_source(out_freeze, log, run_dir,
                                              ep_id, args.candidate)
        log.log("freeze_point", detail=ck_bin, sha256=_sha(ck_bin),
                sandbox_tree=_sandbox_tree(os.path.join(out_freeze, "_sandbox")))
    if not os.path.isfile(ck_bin):
        _reject(run_dir, log, "冻结点 checkpoint.bin 缺失", {})
    freeze_sha = _sha(ck_bin)
    log.log("freeze_hash_locked", sha256=freeze_sha)

    # ---- P1+P2 分叉（每分支独立新进程、新目录；pairs 支持交错次序） ----
    branch_defs = {"A": ("A_full", []), "B": ("B_memory_off", ["--memory-off"])}
    results = []
    for pair in range(1, args.pairs + 1):
        for bkey in order[pair - 1]:
            name, extra = branch_defs[bkey]
            pname = "%s_p%d" % (name, pair) if args.pairs > 1 else name
            out_b = os.path.join(run_dir, pname)
            if os.path.exists(out_b):
                _reject(run_dir, log, "分支目录已存在: %s" % out_b, {})
            os.makedirs(out_b, exist_ok=True)
            import shutil as _sh
            _sh.copy2(ck_bin, os.path.join(out_b, "checkpoint.bin"))
            _sh.copy2(os.path.join(out_freeze, "checkpoint.json"),
                      os.path.join(out_b, "checkpoint.json"))
            for d in sorted(os.listdir(out_freeze)):
                if d.startswith("turn_"):
                    _sh.copytree(os.path.join(out_freeze, d), os.path.join(out_b, d))
            branch_run_id = "%s/%s" % (run_id, pname)
            log.log("restore_source_staged", branch=pname, pair=pair,
                    source_sha256=freeze_sha,
                    staged_sha256=_sha(os.path.join(out_b, "checkpoint.bin")))
            _run_runner(["--episode", EP, "--candidate", args.candidate,
                         "--out", out_b, "--adapter", args.adapter,
                         "--run-id", branch_run_id, "--resume"] + extra,
                        log, "branch_run_%s" % pname)
            # 后测身份验证：t5/t6 必须本次执行（manifest.run_id 绑定 + attempt 事件）
            n_attempts = _verify_posttest_identity(out_b, pname, branch_run_id,
                                                   log, run_dir)
            log.log("branch_done", branch=pname, pair=pair,
                    sandbox_tree=_sandbox_tree(os.path.join(out_b, "_sandbox")))
            results.append({"pair": pair, "branch_key": bkey, "name": pname,
                            "dir": out_b, "run_id": branch_run_id,
                            "attempt_events": n_attempts})

    # ---- P3 汇总（仅在身份验证全部通过后） ----
    summary = {"design": "freeze-fork v4", "run_id": run_id,
               "adapter": args.adapter, "candidate": args.candidate,
               "pairs": args.pairs, "order": order,
               "freeze": {"sha256": freeze_sha,
                          "source": freeze_info["source"],
                          "source_integrity": freeze_info["integrity"],
                          "reused": bool(args.freeze_from)},
               "channels_closed_B": CHANNELS_CLOSED,
               "posttest_executed_this_run": True,
               "branches": {}}
    for r in results:
        out_b = r["dir"]
        em = _jload(os.path.join(out_b, "episode_manifest.json"))
        if em.get("run_id") != r["run_id"]:
            _reject(run_dir, log, "分支 episode_manifest.run_id 不属本次: %s" % r["name"],
                    {"branch": r["name"]})
        b = {"pair": r["pair"], "resumed_from": em.get("resumed_from"),
             "complete": em.get("complete"), "turns": em.get("turns"),
             "run_id": r["run_id"], "attempt_events": r["attempt_events"],
             "posttest": {}}
        for i, cid in POSTTEST_IDX.items():
            td = os.path.join(out_b, "turn_%02d_%s" % (4 + i + 1, cid))
            sc = _jload(os.path.join(td, "scoring.json"))
            checks = {c["id"]: c for c in sc["checks"]}
            prompt_user = ""
            calls_p = os.path.join(td, "calls.json")
            if os.path.isfile(calls_p):
                calls = _jload(calls_p)
                if calls:
                    prompt_user = calls[0].get("user") or ""
            reply = open(os.path.join(td, "reply_raw.txt"), encoding="utf-8").read()
            b["posttest"][cid] = {
                "score10": sc["score10"], "veto": sc["veto"],
                "allergy_kw_hit": checks.get("must_kw_0", {}).get("pass"),
                "boundary_kw_hit": checks.get("must_kw_1", {}).get("pass"),
                "stale_mentions": [c for c in sc["checks"]
                                   if c["id"].startswith("stale_kw_") and not c["pass"]],
                # 第八波复核：仅子串检查，不证明来自 facts 检索（通道追踪归下轮机制工作）
                "prompt_mentions_allergy": "过敏" in prompt_user,
                "prompt_mentions_boundary": ("门外" in prompt_user
                                             or "不让进" in prompt_user),
                "prompt_mentions_ongoing_cat": "养猫" in prompt_user,
                "reply_excerpt": reply[:200]}
        summary["branches"][r["name"]] = b

    with open(os.path.join(run_dir, "fork_summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    log.log("report_written", detail="fork_summary.json")

    for name in summary["branches"]:
        b = summary["branches"][name]["posttest"]
        t5 = b["L3B_t05"]
        print("%s: t5 score=%s 过敏=%s 边界=%s | prompt含过敏事实=%s 含边界=%s 含ongoing=%s"
              % (name, t5["score10"], t5["allergy_kw_hit"], t5["boundary_kw_hit"],
                 t5["prompt_mentions_allergy"], t5["prompt_mentions_boundary"],
                 t5["prompt_mentions_ongoing_cat"]))
    print("汇总 → %s" % os.path.join(run_dir, "fork_summary.json"))


if __name__ == "__main__":
    main()
