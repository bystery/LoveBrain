# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 3 · 正式四组运行器薄包装（phase3_runner v1，2026-11-09）。

指导书 P3-2 执行件。复用 four_arm_runner 的既有函数与 lb_runner 生命周期、lb_gateway
账本、capture_and_validate 校验；不重写装配/评分/网关。

修复的三个 Phase 2 缺陷：
  P3-2.1 LEGACY_COMPAT_R9 接线：正式四组 = DIRECT / COT / LEGACY_COMPAT_R9 /
         CURRENT。LEGACY_COMPAT_R9 候选目录 = tools/benchmark_v1/baseline_candidates/
         legacy_compat_r9（薄适配器，资产仍从 candidates/action_policy_r9_slim 加载，
         原候选零改动）；身份哈希双录（适配器 + 原候选）。原 LEGACY 键保留可跑但
         不进正式四组。
  P3-2.2 政策门按 manifest：准入 = 授权文件 episode 清单 × 分派表 split × exposure
         消费状态 × custody lifecycle × B 线排除；**删除 wave4 目录名前缀绕过**；
         HOLDOUT 必须 once-only 授权（auth.kind=holdout_once_only）且开跑前
         experiments_used 为空；任何 episode 读取发生在政策门通过之后。
  P3-2.3 全局预算门：全局共享 reserve 账本（JSONL append + 目录锁），跨进程
         总量控制；请求前 reserve、结果后 register；崩溃后 reserve 保守计费
         （不重复计费、不自动补跑）；到达 cap 停批。

模式：plan / mock / capture / live（语义同 four_arm_runner；live 需授权+LB_API_KEY）。
不重试质量问题；失败故事如实记录缺失。
"""
import argparse
import datetime
import json
import os
import sys
import time
import uuid

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))
sys.path.insert(0, os.path.join(_REPO, "arena"))
sys.path.insert(0, _HERE)

import four_arm_runner as far  # noqa: E402  复用其函数与 ADAPTERS 注册
from lb_runner import run_episode  # noqa: E402

PH3 = os.path.join(_REPO, "refs", "benchmark_v1_20261107", "phase3")
RUNNER_VERSION = "phase3_runner_v1"
FORMAL_ARMS = ("DIRECT", "COT", "LEGACY_COMPAT_R9", "CURRENT")

HOLDOUT_VAULT = "D:/lovebrain-arena_holdout_vault/wave6_holdout"

LEGACY_COMPAT_DIR = os.path.join(_HERE, "baseline_candidates", "legacy_compat_r9")
LEGACY_ORIG_DIR = os.path.join(_REPO, "candidates", far.LEGACY_CAND)


# ---------- 候选解析（覆盖 four_arm_runner 的轻量臂 + 兼容历史组） ----------

def candidate_dir_for_arm(arm, turn_count):
    if arm in ("DIRECT", "COT"):
        return far.BASELINE_CAND_DIRS[arm]
    if arm == "LEGACY_COMPAT_R9":
        return LEGACY_COMPAT_DIR
    if arm == "CURRENT":
        return os.path.join(_REPO, "candidates", far.resolve_current_candidate(turn_count))
    if arm == "LEGACY":          # 非正式组：保留原身份可跑（不进正式四组）
        return LEGACY_ORIG_DIR
    raise ValueError("未知组别 %r" % arm)


# ---------- 全局预算账本（P3-2.3） ----------

class GlobalAttemptLedger:
    """全局共享 reserve 账本：JSONL append-only + 目录锁。

    reserve 行 = 消费记账单位（请求派发前先记账）；register 行 = 结果回填
    （status/model/latency/tokens）。崩溃恢复语义：reserve 已记账即视为已消费
    （保守口径，不重复计费也不免费重试）；register 只是补充观测，不影响余额。
    """

    def __init__(self, ledger_path):
        self.path = ledger_path
        self.lock_dir = ledger_path + ".lock"

    def _lock(self, timeout_s=10.0):
        end = time.time() + timeout_s
        while True:
            try:
                os.mkdir(self.lock_dir)
                return
            except FileExistsError:
                if time.time() > end:
                    raise RuntimeError("预算账本锁超时（%.1fs）— 检查残留 %s"
                                       % (timeout_s, self.lock_dir))
                time.sleep(0.05)

    def _unlock(self):
        try:
            os.rmdir(self.lock_dir)
        except OSError:
            pass

    def _append(self, obj):
        with open(self.path, "a", encoding="utf-8") as f:
            f.write(json.dumps(obj, ensure_ascii=False) + "\n")
            f.flush()
            os.fsync(f.fileno())

    def count_reserves(self, batch=None):
        n = 0
        if not os.path.isfile(self.path):
            return 0
        with open(self.path, encoding="utf-8") as f:
            for line in f:
                if '"reserve"' not in line:
                    continue
                if batch is not None and ('"batch": "%s"' % batch) not in line:
                    continue
                n += 1
        return n

    def reserve(self, batch, planned):
        self._lock()
        try:
            used = self.count_reserves()
            rec = {"kind": "reserve", "batch": batch,
                   "reserve_uid": uuid.uuid4().hex, "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")}
            self._append(rec)
            return used + 1
        finally:
            self._unlock()

    def register(self, reserve_uid, **fields):
        self._lock()
        try:
            rec = {"kind": "register", "reserve_uid": reserve_uid,
                   "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")}
            rec.update(fields)
            self._append(rec)
        finally:
            self._unlock()


# ---------- 政策门（P3-2.2） ----------

def load_split_map(allocation_csv):
    import csv
    splits = {}
    with open(allocation_csv, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            eid = (r.get("episode_id") or "").strip()
            if not eid:
                continue
            splits[eid] = {"split": (r.get("split") or "").upper(),
                           "planned_split": (r.get("planned_split") or "").upper(),
                           "status": (r.get("status") or "").upper(),
                           "topic": (r.get("primary_topic") or "").upper(),
                           "lineage": (r.get("lineage_group_id") or "")}
    return splits


def exposure_state(eid):
    """读该篇 exposure.json 的消费状态（只读两个元数据字段，不读 episode 正文）。"""
    sv = os.path.join(_REPO, "benchmark", "stories_v2")
    for wave in sorted(os.listdir(sv)):
        wdir = os.path.join(sv, wave)
        if not os.path.isdir(wdir) or wave.startswith("_") or wave in ("GOLD_SIDECAR", "_templates"):
            continue
        p = os.path.join(wdir, eid, "exposure.json")
        if os.path.isfile(p):
            with open(p, encoding="utf-8") as f:
                ex = json.load(f)
            return {"wave": wave, "dev_exposed": bool(ex.get("dev_exposed")),
                    "experiments_used": ex.get("experiments_used") or []}
    return None


def policy_gate(episodes, auth, split_map, b_line):
    """在读取任何 episode 正文之前执行。返回 problems 列表（空=通过）。"""
    problems = []
    kind = auth.get("kind", "")
    authorized_splits = set(auth.get("authorized_splits") or [])
    for eid in episodes:
        if eid in b_line:
            problems.append("%s: B_LINE_SEALED" % eid)
        if eid not in split_map:
            problems.append("%s: 不在正式分派表（v3）" % eid)
            continue
        sm = split_map[eid]
        eff = sm["split"] if sm["split"] not in ("", "PENDING") else sm["planned_split"]
        if eff not in authorized_splits:
            problems.append("%s: split=%s 不在授权 splits %s" % (eid, eff, sorted(authorized_splits)))
        ex = exposure_state(eid)
        if ex is None:
            problems.append("%s: 无 exposure.json（未注册题目）" % eid)
            continue
        used = ex["experiments_used"]
        if kind == "holdout_once_only":
            if used:
                problems.append("%s: HOLDOUT 已有 %d 条消费记录（once-only 违约风险）" % (eid, len(used)))
            if ex["dev_exposed"]:
                problems.append("%s: HOLDOUT dev_exposed=true" % eid)
        else:
            # DEV/SELECTION 正式实验：开跑前不允许已有正式消费（pilot 开发曝光除外，
            # 该事实已由第 1 步对账登记并在分派表反映——SELECTION 40 篇全新无消费）。
            formal = [u for u in used if isinstance(u, dict) and
                      str(u.get("experiment", "")).startswith("phase3_formal")]
            if formal:
                problems.append("%s: 已有 phase3 正式消费记录 %d 条" % (eid, len(formal)))
    return problems


# ---------- 运行 ----------

def episode_path_for(eid, auth):
    """episode 定位：HOLDOUT once-only → 金库（仓库外）；其余 → stories_v2。
    HOLDOUT 金库篇若在 stories_v2 出现副本 = 违规（双份正文禁令）。"""
    if auth.get("kind") == "holdout_once_only":
        p = os.path.join(HOLDOUT_VAULT, eid, "episode.json")
        if not os.path.isfile(p):
            raise FileNotFoundError("HOLDOUT 金库中无 %s（%s）" % (eid, p))
        sv_copy = None
        try:
            far.find_episode_path(eid)
            sv_copy = True
        except FileNotFoundError:
            sv_copy = False
        if sv_copy:
            raise RuntimeError("%s 同时存在于 stories_v2 与金库（双份正文违规）" % eid)
        return p
    return far.find_episode_path(eid)


def run_all(mode, episodes, run_dir, auth_path, ledger_path, allocation_csv,
            base_url=None, model="deepseek-flash-2", deliver_dir=None):
    auth = far.load_authorization(auth_path)
    problems = []
    split_map = load_split_map(allocation_csv)
    b_line = far.b_line_episode_ids()
    # arms 校验：授权 arms 必须是正式四组的子集
    if not set(auth["arms"]) <= set(FORMAL_ARMS):
        problems.append("授权 arms 含非正式组: %r" % (auth["arms"],))
    problems += ["命令行 episodes 超出授权范围: %r" % (sorted(set(episodes) - set(auth["episodes"])),)] \
        if not set(episodes) <= set(auth["episodes"]) else []
    problems += policy_gate(episodes, auth, split_map, b_line)
    if mode == "live" and not os.environ.get("LB_API_KEY"):
        problems.append("LB_API_KEY 未设置")
    if mode == "live":
        import check_provider as cp
        res, code = cp.run_check()
        cp.write_report(res, run_dir)
        if res["verdict"] != "OK":
            problems.append("provider 检查未通过: %s" % res["verdict"])
    if problems:
        far._write_blocked(run_dir, "phase3 政策门/预检未通过", problems)
        print("LIVE BLOCKED:")
        for p in problems:
            print(" -", p)
        return 3

    ledger = GlobalAttemptLedger(ledger_path)
    cap = auth["cap_max_total_api_attempts"]
    batch = auth.get("batch_id") or os.path.basename(run_dir)
    os.makedirs(run_dir, exist_ok=True)
    # plan（复用 four_arm_runner.make_plan 的轮位计算，但用本 runner 的候选解析）
    plan = {"runner_version": RUNNER_VERSION, "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "mode": mode, "arms": {}, "episodes": {}, "auth": {"batch": batch, "cap": cap,
            "kind": auth.get("kind", ""), "model": auth["model"]}}
    ep_turns = {}
    for eid in episodes:
        path = episode_path_for(eid, auth)     # 政策门已过，此时才读正文
        with open(path, encoding="utf-8") as f:
            n = len(json.load(f).get("turns") or [])
        ep_turns[eid] = (path, n)
        plan["episodes"][eid] = {"episode_path": os.path.relpath(path, _REPO).replace("\\", "/"),
                                 "turn_count": n}
    for arm in auth["arms"]:
        rec = {"candidate_dir": os.path.relpath(candidate_dir_for_arm(arm, 12), _REPO).replace("\\", "/"),
               "identity_sha256": {}}
        for eid in episodes:
            cdir = candidate_dir_for_arm(arm, ep_turns[eid][1])
            rec["identity_sha256"][os.path.basename(cdir.rstrip("/\\"))] = far.candidate_identity_sha256(cdir)
        plan["arms"][arm] = rec
    with open(os.path.join(run_dir, "plan.json"), "w", encoding="utf-8") as f:
        json.dump(plan, f, ensure_ascii=False, indent=2)
    if mode == "plan":
        print("plan ok: %d episodes × %d arms" % (len(episodes), len(auth["arms"])))
        return 0

    if mode == "live":
        os.environ.setdefault("LB_API_BASE", base_url or "https://api.llm.ustc.edu.cn/v1")
        os.environ.setdefault("LB_MODEL", model)
        adapter = "openai_compat"
    else:
        adapter = None

    # 轮换：arm i 从 episodes[i % n] 起跑
    n = len(episodes)
    records = []
    for ai, arm in enumerate(auth["arms"]):
        ep_order = episodes[ai % n:] + episodes[:ai % n]
        for eid in ep_order:
            path, n_turns = ep_turns[eid]
            if mode == "live":
                used = ledger.count_reserves(batch=batch)
                if used >= cap:
                    far._write_blocked(run_dir, "全局预算到达（%d/%d）" % (used, cap), [])
                    print("STOP: 全局预算 %d/%d" % (used, cap))
                    break
                if used + n_turns > cap:
                    far._write_blocked(run_dir, "剩余额度不足完成下一篇（%d+%d>%d）" % (used, n_turns, cap), [])
                    print("STOP: 剩余额度不足")
                    break
            out = os.path.join(run_dir, arm, eid)
            run_id = "ph3_%s_%s_%s" % (mode, arm, eid)
            rec = {"arm": arm, "episode": eid, "mode": mode, "run_id": run_id,
                   "candidate": os.path.basename(candidate_dir_for_arm(arm, n_turns).rstrip("/\\"))}
            uid = None
            try:
                if mode == "live":
                    used_after = ledger.reserve(batch=batch)
                    uid = "reserved"
                arm_adapter = adapter or ("bm1_mock_light" if arm in ("DIRECT", "COT") else "mock")
                res = run_episode(path, candidate_dir_for_arm(arm, n_turns), out, arm_adapter,
                                  run_id=run_id, resume=True)
                done = None
                ck = os.path.join(out, "checkpoint.json")
                if os.path.isfile(ck):
                    try:
                        with open(ck, encoding="utf-8") as f:
                            done = json.load(f).get("turns_done")
                    except Exception:
                        done = None
                rec.update({"status": "ok", "turns_done": done})
            except Exception as e:  # noqa: BLE001
                rec.update({"status": "error", "error": "%s: %s" % (type(e).__name__, e),
                            "note": "该故事自失败轮起缺失，不伪造完整结果；不自动换题/换模型/重试"})
            if uid:
                ledger.register("reserved", arm=arm, episode=eid, status=rec.get("status"))
            records.append(rec)
            with open(os.path.join(run_dir, "run_records.json"), "w", encoding="utf-8") as f:
                json.dump(records, f, ensure_ascii=False, indent=2)
        else:
            continue
        break
    summary = {"mode": mode, "reserves_total": ledger.count_reserves(batch=batch),
               "arms_selected": list(auth["arms"]), "records": records}
    with open(os.path.join(run_dir, "run_summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    if mode == "capture":
        import capture_and_validate
        rep = capture_and_validate.validate_run(run_dir, plan)
        capture_and_validate.write_report(rep, run_dir)
        print("capture validation: %d checks, %d FAIL"
              % (rep["n_checks"], sum(1 for c in rep["checks"] if not c["pass"])))
    print("done mode=%s reserves=%d records=%d" % (mode, summary["reserves_total"], len(records)))
    return 0


def main():
    ap = argparse.ArgumentParser(description="Benchmark V1 Phase 3 正式四组运行器（薄包装）")
    ap.add_argument("--mode", required=True, choices=["plan", "capture", "mock", "live"])
    ap.add_argument("--episodes", required=True)
    ap.add_argument("--authorization", required=True, help="Phase 3 授权 JSON（含 batch_id/cap/kind/authorized_splits）")
    ap.add_argument("--allocation", default=os.path.join(PH3, "DATASET_V1_ALLOCATION_V3.csv"))
    ap.add_argument("--ledger", default=os.path.join(PH3, "GLOBAL_ATTEMPT_LEDGER.jsonl"))
    ap.add_argument("--out", required=True)
    ap.add_argument("--base-url", default=None)
    ap.add_argument("--model", default="deepseek-flash-2")
    args = ap.parse_args()
    eps = [e.strip() for e in args.episodes.split(",") if e.strip()]
    if not eps:
        raise SystemExit("episodes 为空")
    return run_all(args.mode, eps, args.out, args.authorization, args.ledger,
                   args.allocation, base_url=args.base_url, model=args.model)


if __name__ == "__main__":
    sys.exit(main() or 0)
