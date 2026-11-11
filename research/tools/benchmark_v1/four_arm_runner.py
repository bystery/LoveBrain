# -*- coding: utf-8 -*-
"""Benchmark V1 · 四组对照运行器（Phase 1）。

四组（指导书 §1.2/§3，身份冻结）：
  DIRECT  — tools/benchmark_v1/baseline_candidates/direct_baseline（Phase 1 新建轻量候选）
  COT     — tools/benchmark_v1/baseline_candidates/cot_baseline（=DIRECT+一行固定思考提示）
  LEGACY  — candidates/action_policy_r9_slim（完整旧 harness 生命周期，非 prompt 替身）
  CURRENT — 双域冠军：故事轮数 ≤24 → window_expand_b_norecap_guard；>24 → window_expand_b_norecap_uc
            （运行前按故事总轮数确定，禁止看到输出后换候选）

模式（指导书 §5：--plan/--capture/--mock 不发任何网络请求；仅 --live + 授权才允许调用）：
  plan    零网络：身份/哈希/轮位计划 → plan.json(+plan_sha256)、ARM_REGISTRY.json、
          PILOT_CASE_MANIFEST.json（含 custody EXPOSED 与 B 线排除核验）
  mock    MockAdapter 全流程（无网络）
  capture = mock + capture_and_validate 独立校验（泄漏/等价/隔离/哈希固定）
  live    openai_compat 真实调用；前置=授权文件+LB_API_KEY+check_provider OK；
          全局 attempt 上限（授权 cap），顺序执行（并发=1），超限停批不补跑

纪律：任何失败保留原始状态与原因；失败故事明确记录缺失轮次，不伪造完整结果；
不重试质量问题；usage/时延/状态码/resp_id 全落盘（lb_gateway 既有账本）。
"""
import argparse
import datetime
import hashlib
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))
sys.path.insert(0, os.path.join(_REPO, "arena"))
sys.path.insert(0, _HERE)

from lb_runner import run_episode  # noqa: E402  复用完整生命周期（指导书 §5.1）
import lb_gateway  # noqa: E402  同一 ADAPTERS dict 引用（运行时注册，不改任何文件）


class _LightMockAdapter:
    """DIRECT/COT 的 mock 替身：返回轻量契约形状 JSON（区别于 harness 形状的
    MockAdapter 内置 JSON）。仅 mock/capture 模式使用；不改 arena 任何文件。"""
    name = "bm1_mock_light"

    def generate(self, system, user, payload):
        import json as _json
        return {"text": _json.dumps({"action": "reply",
                                     "recommended": "（mock）好呀，那就这么定了"},
                                    ensure_ascii=False),
                "finish": "stop", "usage": None}


lb_gateway.ADAPTERS.setdefault("bm1_mock_light", _LightMockAdapter)

ARMS = ("DIRECT", "COT", "LEGACY", "CURRENT")
_ARMS_OVERRIDE = None          # --arms 子集（如 Phase2 pilot 的 DIRECT,CURRENT）
_EPISODES_DIR_OVERRIDE = None  # 新 wave 目录提示（wave4_dev 新题绕过旧 custody 门）


def active_arms():
    return _ARMS_OVERRIDE or ARMS
LEGACY_CAND = "action_policy_r9_slim"
# Phase 3: new-wave stories (wave4_dev/wave5_selection/wave6_holdout) use dict-type
# persona which crashes raw r9_slim. Use the compat adapter instead.
LEGACY_COMPAT_DIR = os.path.join(_HERE, "baseline_candidates", "legacy_compat_r9")
_USE_LEGACY_COMPAT = False     # toggled by --episodes-dir for new-wave stories
CURRENT_GUARD = "window_expand_b_norecap_guard"      # ≤24 轮域冠军
CURRENT_UC = "window_expand_b_norecap_uc"            # >24 轮域冠军
BASELINE_CAND_DIRS = {
    "DIRECT": os.path.join(_HERE, "baseline_candidates", "direct_baseline"),
    "COT": os.path.join(_HERE, "baseline_candidates", "cot_baseline"),
}
RUNNER_VERSION = "four_arm_runner_v1"
DEFAULT_AUTH = os.path.join(_REPO, "refs", "benchmark_v1_20261107", "phase1",
                            "PILOT_AUTHORIZATION.json")
PILOT_EPISODES_DEFAULT = ["E032", "E037", "E191", "LRE117"]


# ---------- 候选解析与身份 ----------

def resolve_current_candidate(turn_count):
    """CURRENT 双域分流：运行前按故事总轮数确定（指导书 §1.2）。"""
    return CURRENT_GUARD if turn_count <= 24 else CURRENT_UC


def candidate_dir_for_arm(arm, turn_count):
    if arm == "DIRECT":
        return BASELINE_CAND_DIRS["DIRECT"]
    if arm == "COT":
        return BASELINE_CAND_DIRS["COT"]
    if arm == "LEGACY":
        if _USE_LEGACY_COMPAT:
            return LEGACY_COMPAT_DIR
        return os.path.join(_REPO, "candidates", LEGACY_CAND)
    if arm == "CURRENT":
        return os.path.join(_REPO, "candidates", resolve_current_candidate(turn_count))
    raise ValueError("未知组别 %r" % arm)


def candidate_identity_sha256(cand_dir):
    """候选完整依赖闭包哈希：目录内全部文本/配置文件（排除 __pycache__/*.pyc）的
    (relpath, sha256) 有序清单再整体 sha256。"""
    entries = []
    for root, dirs, files in os.walk(cand_dir):
        dirs[:] = [d for d in dirs if d != "__pycache__"]
        for fn in sorted(files):
            if fn.endswith(".pyc"):
                continue
            p = os.path.join(root, fn)
            rel = os.path.relpath(p, cand_dir).replace("\\", "/")
            with open(p, "rb") as f:
                entries.append("%s %s" % (rel, hashlib.sha256(f.read()).hexdigest()))
    blob = "\n".join(sorted(entries)).encode("utf-8")
    return hashlib.sha256(blob).hexdigest()


# ---------- 题目解析与政策门 ----------

def find_episode_path(episode_id):
    # If episodes-dir override is set, check it first
    if _EPISODES_DIR_OVERRIDE:
        # Check vault for wave6_holdout
        if _EPISODES_DIR_OVERRIDE == "wave6_holdout":
            vault = os.environ.get("HOLDOUT_VAULT",
                                   "D:/lovebrain-arena_holdout_vault/wave6_holdout")
            cand = os.path.join(vault, episode_id, "episode.json")
            if os.path.isfile(cand):
                return cand
        # Check stories_v2/<wave>/
        sv = os.path.join(_REPO, "benchmark", "stories_v2")
        wdir = os.path.join(sv, _EPISODES_DIR_OVERRIDE)
        if os.path.isdir(wdir):
            cand = os.path.join(wdir, episode_id, "episode.json")
            if os.path.isfile(cand):
                return cand
    # Fallback: search all waves in stories_v2
    sv = os.path.join(_REPO, "benchmark", "stories_v2")
    for wave in sorted(os.listdir(sv)):
        wdir = os.path.join(sv, wave)
        if not os.path.isdir(wdir) or wave.startswith("_") or wave == "GOLD_SIDECAR":
            continue
        cand = os.path.join(wdir, episode_id, "episode.json")
        if os.path.isfile(cand):
            return cand
    raise FileNotFoundError("episode %s 不在 stories_v2 或 vault（不含 b_line，按政策封存）" % episode_id)


def b_line_episode_ids():
    p = os.path.join(_REPO, "benchmark", "b_line", "B_LINE_MANIFEST.json")
    with open(p, "r", encoding="utf-8") as f:
        return set(json.load(f).get("episodes", {}).keys())


def custody_lifecycle(episode_id):
    p = os.path.join(_REPO, "refs", "experiment_protocol_20261023",
                     "global_harness_program_20261030", "STORY_CUSTODY_LEDGER.json")
    with open(p, "r", encoding="utf-8") as f:
        led = json.load(f)
    item = (led.get("stories") or {}).get(episode_id)
    return item.get("lifecycle") if isinstance(item, dict) else None


def pilot_policy_check(episode_id):
    """Phase 1 pilot 政策：B 线禁用；只允许 custody 账本中 EXPOSED 的旧题。"""
    problems = []
    if episode_id in b_line_episode_ids():
        problems.append("B_LINE_SEALED")
    life = custody_lifecycle(episode_id)
    if life != "EXPOSED":
        problems.append("custody=%s(需 EXPOSED)" % life)
    return problems


# ---------- attempt 记账 ----------

def count_attempts(run_dir):
    """全 run 目录 attempt_started 事件总数（lb_gateway 每次派发前硬落账）。"""
    n = 0
    for root, dirs, files in os.walk(run_dir):
        if "gateway_events.jsonl" in files:
            with open(os.path.join(root, "gateway_events.jsonl"), "r",
                      encoding="utf-8") as f:
                for line in f:
                    if '"attempt_started"' in line:
                        n += 1
    return n


def _plan_positions(episode_path):
    with open(episode_path, "r", encoding="utf-8") as f:
        ep = json.load(f)
    return ep, len(ep.get("turns") or [])


# ---------- 模式实现 ----------

def make_plan(episodes, deliver_dir=None):
    b_line = b_line_episode_ids()
    plan = {"runner_version": RUNNER_VERSION,
            "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "arms": {}, "episodes": {}, "policy": {}}
    wave4_mode = bool(_EPISODES_DIR_OVERRIDE and _EPISODES_DIR_OVERRIDE.startswith("wave4"))
    new_wave_mode = bool(_EPISODES_DIR_OVERRIDE and _EPISODES_DIR_OVERRIDE.startswith(("wave4", "wave5", "wave6")))
    for ep_id in episodes:
        problems = pilot_policy_check(ep_id)
        path = find_episode_path(ep_id)
        _, n_turns = _plan_positions(path)
        plan["episodes"][ep_id] = {
            "episode_path": os.path.relpath(path, _REPO).replace("\\", "/"),
            "turn_count": n_turns, "custody_lifecycle": custody_lifecycle(ep_id),
            "b_line_member": ep_id in b_line, "policy_problems": problems}
        if problems:
            # new-wave 新题（wave4_dev/wave5_selection/wave6_holdout）不受旧 custody 账本约束
            # （与 main() run 模式豁免同口径：以 exposure/manifest 政策门代替——b_line_member 必须为 False）。
            # 豁免后 meta.policy_problems 归零并标 policy_waived；原始问题保留在
            # plan["policy"] 作审计痕迹（capture 校验只拦未豁免的残留问题）。
            if new_wave_mode and not ep_id in b_line:
                plan["policy"][ep_id] = {"waived": True, "reason": "%s 新题豁免（exposure 政策门代替 custody 账本）" % (_EPISODES_DIR_OVERRIDE or "wave4_dev"), "problems": problems}
                plan["episodes"][ep_id]["policy_problems"] = []
                plan["episodes"][ep_id]["policy_waived"] = True
            else:
                raise RuntimeError("pilot 政策门未过 %s: %s" % (ep_id, problems))
    for arm in active_arms():
        arm_rec = {"candidate": None, "candidate_dir": None,
                   "identity_sha256": {}, "episodes": {}}
        for ep_id in episodes:
            n_turns = plan["episodes"][ep_id]["turn_count"]
            cdir = candidate_dir_for_arm(arm, n_turns)
            arm_rec["candidate"] = os.path.basename(cdir.rstrip("/\\"))
            arm_rec["candidate_dir"] = os.path.relpath(cdir, _REPO).replace("\\", "/")
            arm_rec["identity_sha256"][os.path.basename(cdir.rstrip("/\\"))] = \
                candidate_identity_sha256(cdir)
            arm_rec["episodes"][ep_id] = {
                "turn_count": n_turns,
                "planned_model_positions": n_turns,
                "current_routing": (resolve_current_candidate(n_turns)
                                    if arm == "CURRENT" else None)}
        plan["arms"][arm] = arm_rec
    plan["plan_sha256"] = hashlib.sha256(
        json.dumps(plan, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()
    # 轮换声明：arm i 从 episodes[i % n] 起跑（顺序轮换、不破坏单臂内连续状态）
    n = len(episodes)
    plan["rotation"] = {arm: episodes[(i % n):] + episodes[:(i % n)]
                        for i, arm in enumerate(ARMS)}
    return plan


def write_plan(plan, run_dir, deliver_dir=None):
    os.makedirs(run_dir, exist_ok=True)
    with open(os.path.join(run_dir, "plan.json"), "w", encoding="utf-8") as f:
        json.dump(plan, f, ensure_ascii=False, indent=2)
    if deliver_dir:
        os.makedirs(deliver_dir, exist_ok=True)
        registry = {
            "registry_version": "arm_registry_v1",
            "generated_at": plan["generated_at"],
            "reference_commit_intent": "byster/arena@75ab97e (工作起点)",
            "arms": {},
            "optional_fifth": {"arm_id": "REFERENCE",
                               "candidate": "reference_product_v1",
                               "note": "原版产品参考组：Phase 1 不入主对照，先核保真边界"
                                       "（refs/arena_v1/BASELINE_PARITY.md），二/三阶段再定"},
        }
        _ARM_META = {
            "DIRECT": {"name": "Direct", "source": "Phase1 新建轻量适配器"},
            "COT": {"name": "CoT-style", "source": "Phase1 新建轻量适配器"},
            "LEGACY": {"name": "Historical Harness", "source": "candidates/" + LEGACY_CAND},
            "CURRENT": {"name": "LoveBrain Harness", "source": "双域冠军（按轮数分流）",
                        "routing": {"<=24": CURRENT_GUARD, ">24": CURRENT_UC}},
        }
        for arm in active_arms():
            rec = dict(_ARM_META.get(arm, {"name": arm, "source": ""}))
            rec["candidate_dir"] = plan["arms"][arm]["candidate_dir"]
            rec["identity_sha256"] = plan["arms"][arm]["identity_sha256"]
            registry["arms"][arm] = rec
        with open(os.path.join(deliver_dir, "ARM_REGISTRY.json"), "w", encoding="utf-8") as f:
            json.dump(registry, f, ensure_ascii=False, indent=2)
        manifest = {
            "manifest_version": "pilot_case_manifest_v1",
            "episodes": plan["episodes"],
            "rotation": plan["rotation"],
            "planned_positions": {"per_arm": sum(e["turn_count"] for e in
                                                 plan["episodes"].values()),
                                  "four_arm_total": len(active_arms()) * sum(e["turn_count"] for e in
                                                            plan["episodes"].values()),
                                  "active_arms": list(active_arms()),
                                  "note": "含连接冒烟与可能的内部附加调用时实际 attempts 可更高，"
                                          "上限=授权 cap，超额停批"},
        }
        with open(os.path.join(deliver_dir, "PILOT_CASE_MANIFEST.json"), "w",
                  encoding="utf-8") as f:
            json.dump(manifest, f, ensure_ascii=False, indent=2)


def load_authorization(path):
    with open(path, "r", encoding="utf-8") as f:
        auth = json.load(f)
    req = {"scope", "cap_max_total_api_attempts", "model", "temperature",
           "max_concurrent_requests", "retry_on_quality_failure", "arms", "episodes"}
    missing = req - set(auth.keys())
    if missing:
        raise RuntimeError("授权文件缺字段: %s" % missing)
    if auth["model"] != "deepseek-flash-2":
        raise RuntimeError("授权模型必须是 deepseek-flash-2（禁止静默换模型）: %r" % auth["model"])
    mc = auth["max_concurrent_requests"]
    if not isinstance(mc, int) or mc < 1:
        raise RuntimeError("max_concurrent_requests 必须为 >=1 整数: %r" % (mc,))
    # Phase 2 pilot：以授权文件声明为准（PILOT2_AUTHORIZATION=4；Phase 1 口径=1）。
    # 运行器本身串行执行（实际并发=1，不超授权），字段仅登记授权允许值。
    if auth["retry_on_quality_failure"]:
        raise RuntimeError("retry_on_quality_failure 必须为 false")
    return auth


def live_precheck(auth, episodes, run_dir):
    problems = []
    # Phase 2 pilot：命令行 arms/episodes 允许是授权范围的子集（同一授权分批消费 cap）；
    # 授权 arms 必须仍是运行器已知臂的子集。
    if not set(auth["arms"]) <= set(ARMS):
        problems.append("授权 arms 含未知臂: %r" % (auth["arms"],))
    if not set(episodes) <= set(auth["episodes"]):
        problems.append("命令行 episodes 超出授权范围: %r"
                        % (sorted(set(episodes) - set(auth["episodes"])),))
    if not os.environ.get("LB_API_KEY"):
        problems.append("LB_API_KEY 未设置（用户须撤销泄露旧 key 并配置新 key）")
    try:
        import check_provider as cp
        res, code = cp.run_check()
        cp.write_report(res, run_dir)
        if res["verdict"] != "OK":
            problems.append("provider 检查未通过: %s（%s）" % (res["verdict"], res.get("reason")))
    except Exception as e:  # noqa: BLE001
        problems.append("provider 检查异常: %s: %s" % (type(e).__name__, e))
    return problems


def run_all(mode, episodes, run_dir, deliver_dir=None, auth_path=None,
            base_url=None, model="deepseek-flash-2"):
    plan = make_plan(episodes, deliver_dir=None)
    write_plan(plan, run_dir, deliver_dir)
    if mode == "plan":
        print("plan ok: %d episodes × %d arms；plan_sha256=%s"
              % (len(episodes), len(ARMS), plan["plan_sha256"][:16]))
        return 0
    if mode == "live":
        auth = load_authorization(auth_path or DEFAULT_AUTH)
        problems = live_precheck(auth, episodes, run_dir)
        if problems:
            _write_blocked(run_dir, "live precheck 未通过", problems)
            print("LIVE BLOCKED:"); [print(" -", p) for p in problems]
            return 3
        os.environ.setdefault("LB_API_BASE", base_url or "https://api.llm.ustc.edu.cn/v1")
        os.environ.setdefault("LB_MODEL", model)
        adapter = "openai_compat"
    else:
        adapter = None       # capture 与 mock 同为离线管道；capture 追加独立校验
        # DIRECT/COT 用轻量契约 mock 替身，harness 组用原生 MockAdapter（形状各自匹配）
    cap = (auth or {}).get("cap_max_total_api_attempts") if mode == "live" else None
    records = []

    # Build all (arm, ep_id) work items — interleaved across arms so all arms
    # make progress simultaneously (round-robin: DIRECT/E339, COT/E339, LEGACY/E339,
    # CURRENT/E339, DIRECT/E340, COT/E340, ...)
    arm_list = list(active_arms())
    arm_ep_lists = {arm: list(plan["rotation"][arm]) for arm in arm_list}
    work_items = []
    max_eps = max(len(lst) for lst in arm_ep_lists.values()) if arm_ep_lists else 0
    for i in range(max_eps):
        for arm in arm_list:
            if i < len(arm_ep_lists[arm]):
                work_items.append((arm, arm_ep_lists[arm][i]))

    # Determine concurrency: use authorization's max_concurrent_requests for live
    max_workers = 1
    if mode == "live" and auth:
        max_workers = min(auth.get("max_concurrent_requests", 1), 20)
    elif mode != "live":
        max_workers = 1  # mock/capture are fast, no need for parallelism

    print("Running %d work items with max_workers=%d" % (len(work_items), max_workers))

    if max_workers <= 1:
        # Serial mode (original behavior)
        for arm, ep_id in work_items:
            if mode == "live" and cap is not None:
                used = count_attempts(run_dir)
                if used >= cap:
                    _write_blocked(run_dir, "attempt cap 到达（%d/%d）" % (used, cap), [])
                    print("STOP: attempt cap %d/%d" % (used, cap))
                    break
                planned = plan["arms"][arm]["episodes"][ep_id]["planned_model_positions"]
                if used + planned > cap:
                    _write_blocked(run_dir, "剩余额度不足完成下一篇（%d 已用 + %d 计划 > %d cap）"
                                   % (used, planned, cap), [])
                    print("STOP: 剩余额度不足")
                    break
            ep_path = find_episode_path(ep_id)
            out = os.path.join(run_dir, arm, ep_id)
            run_id = "bm1_%s_%s_%s" % (mode, arm, ep_id)
            rec = {"arm": arm, "episode": ep_id, "mode": mode, "run_id": run_id,
                   "candidate": plan["arms"][arm]["candidate"]}
            try:
                arm_adapter = adapter or (
                    "bm1_mock_light" if arm in ("DIRECT", "COT") else "mock")
                res = run_episode(ep_path, candidate_dir_for_arm(
                    arm, plan["episodes"][ep_id]["turn_count"]), out, arm_adapter,
                    run_id=run_id, resume=True)
                done = None
                ck = os.path.join(out, "checkpoint.json")
                if os.path.isfile(ck):
                    try:
                        with open(ck, "r", encoding="utf-8") as f:
                            done = json.load(f).get("turns_done")
                    except Exception:
                        done = None
                rec.update({"status": "ok", "turns_done": done})
            except Exception as e:  # noqa: BLE001
                ck = os.path.join(out, "checkpoint.json")
                done = None
                if os.path.isfile(ck):
                    try:
                        with open(ck, "r", encoding="utf-8") as f:
                            done = json.load(f).get("turns_done")
                    except Exception:
                        done = None
                rec.update({"status": "error", "error": "%s: %s" % (type(e).__name__, e),
                            "turns_done_before_error": done,
                            "note": "该故事自失败轮起缺失，不伪造完整结果"})
            records.append(rec)
            print("  done: %s/%s -> %s (turns=%s)" % (arm, ep_id, rec["status"], rec.get("turns_done")))
    else:
        # Concurrent mode using ThreadPoolExecutor
        from concurrent.futures import ThreadPoolExecutor, as_completed
        import threading

        cap_lock = threading.Lock()
        submit_lock = threading.Lock()  # throttle submissions to avoid 429 bursts

        def run_one(arm, ep_id):
            ep_path = find_episode_path(ep_id)
            out = os.path.join(run_dir, arm, ep_id)
            run_id = "bm1_%s_%s_%s" % (mode, arm, ep_id)
            rec = {"arm": arm, "episode": ep_id, "mode": mode, "run_id": run_id,
                   "candidate": plan["arms"][arm]["candidate"]}
            # Retry on 429 with exponential backoff
            max_retries = 3
            for attempt in range(max_retries + 1):
                try:
                    arm_adapter = adapter or (
                        "bm1_mock_light" if arm in ("DIRECT", "COT") else "mock")
                    res = run_episode(ep_path, candidate_dir_for_arm(
                        arm, plan["episodes"][ep_id]["turn_count"]), out, arm_adapter,
                        run_id=run_id, resume=True)
                    done = None
                    ck = os.path.join(out, "checkpoint.json")
                    if os.path.isfile(ck):
                        try:
                            with open(ck, "r", encoding="utf-8") as f:
                                done = json.load(f).get("turns_done")
                        except Exception:
                            done = None
                    rec.update({"status": "ok", "turns_done": done})
                    break
                except Exception as e:  # noqa: BLE001
                    err_str = "%s: %s" % (type(e).__name__, e)
                    if "429" in err_str and attempt < max_retries:
                        # Exponential backoff: 30s, 60s, 120s
                        import time as _time
                        backoff = 30 * (2 ** attempt)
                        print("  429 retry %d/%d for %s/%s, backing off %ds" %
                              (attempt + 1, max_retries, arm, ep_id, backoff))
                        _time.sleep(backoff)
                        continue
                    ck = os.path.join(out, "checkpoint.json")
                    done = None
                    if os.path.isfile(ck):
                        try:
                            with open(ck, "r", encoding="utf-8") as f:
                                done = json.load(f).get("turns_done")
                        except Exception:
                            done = None
                    rec.update({"status": "error", "error": err_str,
                                "turns_done_before_error": done,
                                "note": "该故事自失败轮起缺失，不伪造完整结果"})
                    break
            return rec

        with ThreadPoolExecutor(max_workers=max_workers) as pool:
            futures = {}
            # Submit in batches to avoid overwhelming the API
            batch_size = max_workers  # only keep max_workers tasks in flight
            pending = list(work_items)
            while pending or futures:
                # Fill up to batch_size in-flight tasks
                while pending and len(futures) < batch_size:
                    arm, ep_id = pending.pop(0)
                    if mode == "live" and cap is not None:
                        with cap_lock:
                            used = count_attempts(run_dir)
                            if used >= cap:
                                _write_blocked(run_dir, "attempt cap 到达（%d/%d）" % (used, cap), [])
                                print("STOP: attempt cap %d/%d" % (used, cap))
                                pending.clear()
                                break
                    f = pool.submit(run_one, arm, ep_id)
                    futures[f] = (arm, ep_id)
                    print("  submit: %s/%s" % (arm, ep_id))

                if not futures:
                    break
                # Wait for at least one to complete
                from concurrent.futures import wait, FIRST_COMPLETED
                done_set, _ = wait(futures, return_when=FIRST_COMPLETED)
                for f in done_set:
                    arm, ep_id = futures.pop(f)
                    rec = f.result()
                    records.append(rec)
                    print("  done: %s/%s -> %s (turns=%s)" % (arm, ep_id, rec["status"], rec.get("turns_done")))
                # Periodically write run_records so progress isn't lost on kill
                with cap_lock:
                    with open(os.path.join(run_dir, "run_records.json"), "w",
                              encoding="utf-8") as rf:
                        json.dump(records, rf, ensure_ascii=False, indent=2)
            # Drain any remaining
            for f in list(futures):
                arm, ep_id = futures.pop(f)
                rec = f.result()
                records.append(rec)
                print("  done: %s/%s -> %s (turns=%s)" % (arm, ep_id, rec["status"], rec.get("turns_done")))

    with open(os.path.join(run_dir, "run_records.json"), "w",
                      encoding="utf-8") as f:
        json.dump(records, f, ensure_ascii=False, indent=2)
    summary = {"mode": mode, "attempts_total": count_attempts(run_dir),
               "arms_selected": list(active_arms()),
               "records": records}
    with open(os.path.join(run_dir, "run_summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    if mode == "capture":
        import capture_and_validate
        rep = capture_and_validate.validate_run(run_dir, plan)
        capture_and_validate.write_report(rep, run_dir)
        print("capture validation: %d checks, %d FAIL"
              % (rep["n_checks"], sum(1 for c in rep["checks"] if not c["pass"])))
    print("done mode=%s attempts=%d records=%d" % (mode, summary["attempts_total"], len(records)))
    return 0


def _write_blocked(run_dir, reason, problems):
    with open(os.path.join(run_dir, "LIVE_BLOCKED.json"), "w", encoding="utf-8") as f:
        json.dump({"blocked": True, "reason": reason, "problems": problems,
                   "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")},
                  f, ensure_ascii=False, indent=2)


def main():
    ap = argparse.ArgumentParser(description="Benchmark V1 four-arm runner")
    ap.add_argument("--mode", required=True, choices=["plan", "capture", "mock", "live"])
    ap.add_argument("--episodes", default=",".join(PILOT_EPISODES_DEFAULT))
    ap.add_argument("--arms", default=None,
                    help="逗号分隔子集（如 DIRECT,CURRENT）；Phase2 pilot 按指导书 §10.2 用两臂对照")
    ap.add_argument("--out", required=True)
    ap.add_argument("--deliver-dir", default=None,
                    help="同时写出 ARM_REGISTRY.json / PILOT_CASE_MANIFEST.json 的交付目录")
    ap.add_argument("--authorization", default=DEFAULT_AUTH)
    ap.add_argument("--base-url", default=None)
    ap.add_argument("--episodes-dir", default=None,
                    help="wave 目录名（如 wave4_dev）：episodes 在该目录下解析，用于新题 pilot")
    args = ap.parse_args()
    global _EPISODES_DIR_OVERRIDE, _USE_LEGACY_COMPAT
    _EPISODES_DIR_OVERRIDE = args.episodes_dir
    # Phase 3: new-wave stories use dict-type persona → must use LEGACY_COMPAT_R9
    if args.episodes_dir and args.episodes_dir.startswith(("wave4", "wave5", "wave6")):
        _USE_LEGACY_COMPAT = True
    eps = [e.strip() for e in args.episodes.split(",") if e.strip()]
    if not eps:
        raise SystemExit("episodes 为空")
    if args.arms:
        sel = [a.strip().upper() for a in args.arms.split(",") if a.strip()]
        bad = [a for a in sel if a not in ARMS]
        if bad:
            raise SystemExit("未知组别: %r" % bad)
        global _ARMS_OVERRIDE
        _ARMS_OVERRIDE = sel
    for e in eps:
        problems = pilot_policy_check(e)
        if problems and args.mode in ("capture", "mock", "live"):
            # new-wave 新题不受旧 custody 账本约束（以 exposure/manifest 政策门代替）
            if not (_EPISODES_DIR_OVERRIDE and _EPISODES_DIR_OVERRIDE.startswith(("wave4", "wave5", "wave6"))):
                raise SystemExit("pilot 政策门未过 %s: %s" % (e, problems))
    return run_all(args.mode, eps, args.out, args.deliver_dir, args.authorization,
                   args.base_url)


if __name__ == "__main__":
    sys.exit(main() or 0)
