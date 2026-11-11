"""campaign_tick.py — Butler live-campaign 节律驱动（288h 2026-10-28）

每次看门狗触发调用一次：对 live 队列跑一次非阻塞 tick（对账收取→按闸口派发→原子
checkpoint），并把一行摘要追加进 journal.jsonl（campaign 的真实运行记录单位）。

设计边界：
- 队列空/全在跑/全被闸口拒绝都是合法出口——tick 如实记账，绝不伪造进展；
- 容量墙订单（如 B r3，nextEligibleAt≈2026-11-03T21:32）不入队重试耗 attempts，
  由工单在复位后人工/提示驱动入队；
- 与 run_queue 共用同一把队列锁；只写 live-campaign 自己的目录。

Run: D:/anaconda/python.exe campaign_tick.py
"""
import importlib.util
import json
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
RUN_ROOT = "D:/butler_acceptance/runs/fullflow-20261020-02"
CAMPAIGN = os.path.join(HERE, "live-campaign")
QUEUE = os.path.join(CAMPAIGN, "q.json")
EVIDENCE = os.path.join(CAMPAIGN, "evidence.json")
JOURNAL = os.path.join(CAMPAIGN, "journal.jsonl")
READY_SPECS = os.path.join(CAMPAIGN, "ready-specs.json")
QUEUE_MODULE = os.path.join(RUN_ROOT, "workspaces", "max-v2", "c", "persistent_queue.py")
ADAPTER = os.path.join(RUN_ROOT, "workspaces", "butler-fix", "fix-n3a", "scripts", "worker_adapter.mjs")
PROJECT = os.path.join(RUN_ROOT, "workspaces", "max-v2", "c-integration", "live-project")
CONTROLLER = "ctrl-main-campaign"
BACKEND = "workbuddy"  # 仅全局默认型；queue_runner.AdapterClient.for_order 现按单 payload.backend/project 真实路由（R04/C01, 2026-10-28）

# Step 5 (2026-10-28): authoritative continuation queue + the two worker-verified
# generators that project it into typed orders.
INPUT_QUEUE = os.path.join(RUN_ROOT, "Butler-早晚接续包-20261028", "02-主线接续队列-v2.json")
MAKE_READY_SPECS = os.path.join(
    RUN_ROOT, "workspaces", "max-v2", "gemini-live-20261028", "ord-readyspecs", "make_ready_specs.py")
CAMPAIGN_ORDERS = os.path.join(
    RUN_ROOT, "workspaces", "max-v2", "gemini-live-20261028", "ord-campaign-orders", "campaign_orders.py")

DEV_ENV = dict(os.environ)
DEV_ENV["BUTLER_DATA_DIR"] = os.path.join(RUN_ROOT, "workspaces", "kit-ab-live", "data")
DEV_ENV["BUTLER_PIPE_PATH"] = "//./pipe/butler-kit-ab-1793101852"
DEV_ENV["PYTHONIOENCODING"] = "utf-8"

# R02/G08 (2026-10-28): projection is rebuilt every tick; pool plan is recorded
# in the journal so the hosted loop surfaces "paper-ready but not executable".
ACCEPT_DIR = os.path.join(CAMPAIGN, "accept")
if ACCEPT_DIR not in sys.path:
    sys.path.insert(0, ACCEPT_DIR)


def pool_snapshot():
    try:
        import projection_check as proj
        import pool_planner as planner
        view = proj.build()
        plan = planner.plan(rows=view["rows"])
        return {
            "action": plan.get("action"),
            "executableReady": plan.get("executableReady"),
            "readyNotExecutable": plan.get("readyNotExecutable"),
            "pendingExecutable": plan.get("pendingExecutable"),
            "batch": [x["orderId"] for x in plan.get("batch") or []],
            "enrichCount": len(plan.get("enrichCandidates") or []),
            "claimsDuplicates": len(view["claimsDiagnostics"]["duplicateClaimIds"]),
            "claimsFutureHeartbeats": len(view["claimsDiagnostics"]["futureHeartbeats"]),
        }
    except Exception as exc:  # never block the tick on a read-only projection
        return {"action": "error", "error": repr(exc)}


def ensure_skeleton():
    os.makedirs(os.path.dirname(QUEUE), exist_ok=True)
    os.makedirs(PROJECT, exist_ok=True)
    if not os.path.exists(QUEUE):
        sys.path.insert(0, os.path.join(RUN_ROOT, "workspaces", "max-v2", "c-integration"))
        import queue_runner as qr
        qmod = qr.load_queue_module(qr.DEFAULT_QUEUE_MODULE)
        qr.make_queue_class(qmod)(QUEUE)
        print("[campaign] live queue initialized:", QUEUE)


def _load_module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def ensure_live_orders():
    """Step 5: seed the live campaign queue with REAL typed orders projected from the
    authoritative continuation queue — so the tick operates on a real backlog instead
    of empty-ticking. Idempotent: only seeds when the queue holds no orders yet; the
    generators themselves de-dup by requestId, so re-runs add nothing.

    Honesty guard: seeding only ENLISTS work as visible ready/pending orders. It does
    NOT complete anything. The live tick (queue_runner.classify_execution) then decides
    per order whether it is dispatchable, offline-verifiable, or a not-yet-enriched
    backlog item — offline/model orders without a real executable are never fake-accepted.
    """
    sys.path.insert(0, os.path.join(RUN_ROOT, "workspaces", "max-v2", "c-integration"))
    import queue_runner as qr
    try:
        with open(QUEUE, "r", encoding="utf-8") as f:
            existing = json.load(f).get("orders") or []
    except (ValueError, OSError):
        existing = []
    if existing:
        return {"seeded": False, "existingOrders": len(existing),
                "note": "live queue already populated; skip seed (idempotent)"}

    if not (os.path.exists(INPUT_QUEUE) and os.path.exists(MAKE_READY_SPECS) and os.path.exists(CAMPAIGN_ORDERS)):
        return {"seeded": False, "error": "generator input/module missing",
                "inputQueue": os.path.exists(INPUT_QUEUE),
                "makeReady": os.path.exists(MAKE_READY_SPECS),
                "campaignOrders": os.path.exists(CAMPAIGN_ORDERS)}

    # same F-WAVE-03 collision lock the tick uses — never seed while a tick/run_queue holds the queue
    lock = qr.acquire_queue_lock(QUEUE)
    if lock is None:
        return {"seeded": False, "locked": True, "note": "another runner holds the queue; skip seed this wake"}
    try:
        mrs = _load_module(MAKE_READY_SPECS, "make_ready_specs")
        specs = mrs.generate_ready_specs(INPUT_QUEUE, READY_SPECS)
        co = _load_module(CAMPAIGN_ORDERS, "campaign_orders")
        summ = co.populate_orders(QUEUE, READY_SPECS)
        return {"seeded": True, "specCount": len(specs),
                "added": summ["addedCount"], "skipped": summ["skippedCount"],
                "parked": len(summ["parked"]), "ready": len(summ["ready"]),
                "stats": summ["stats"]}
    finally:
        qr.release_queue_lock(lock)


def reconcile_live_orders():
    """C (2026-10-28): after some orders reach `accepted`, automatically un-park the
    downstream orders whose dependency gates are now satisfied, so the queue advances
    (real successor consumption) instead of staying fixed at 3 ready / 61 pending.

    Uses campaign_orders' own dependency-gate logic (the same rules that parked them).
    No-op when the queue is empty or campaign_orders is unavailable.
    """
    sys.path.insert(0, os.path.join(RUN_ROOT, "workspaces", "max-v2", "c-integration"))
    import queue_runner as qr
    try:
        with open(QUEUE, "r", encoding="utf-8") as f:
            orders = json.load(f).get("orders") or []
    except (ValueError, OSError):
        return {"reconciled": False, "reason": "no queue yet"}
    if not orders or not os.path.exists(CAMPAIGN_ORDERS):
        return {"reconciled": False, "orders": len(orders)}
    lock = qr.acquire_queue_lock(QUEUE)
    if lock is None:
        return {"reconciled": False, "locked": True}
    try:
        co = _load_module(CAMPAIGN_ORDERS, "campaign_orders")
        q = co.get_queue(QUEUE)
        promoted_offline = co.promote_runnable_offline_orders(q)
        promoted_deps = co.reconcile_dependencies(q)
        return {"reconciled": True,
                "promotedOffline": promoted_offline,
                "promotedByDependency": promoted_deps,
                "stats": q.stats() if hasattr(q, "stats") else {}}
    finally:
        qr.release_queue_lock(lock)


def run_tick():
    cmd = [sys.executable, os.path.join(HERE, "queue_runner.py"), "--tick", "--json",
           "--queue", QUEUE, "--project", PROJECT, "--controller", CONTROLLER,
           "--backend", BACKEND, "--queue-module", QUEUE_MODULE, "--evidence", EVIDENCE,
           "--resume-timeout", "0", "--poll-interval", "0", "--timeout", "5"]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, env=DEV_ENV, timeout=180)
    except (subprocess.TimeoutExpired, OSError) as exc:
        # C03：宿主/daemon 离线 -> 明确标 delivery_pending，业务不被停止、不静默
        return {"error": "tick subprocess failed: {!r}".format(exc), "deliveryPending": True}
    # stdout 末尾是多行 pretty JSON summary（log 行混在前面）——从尾部累积拼装到能解析为止
    out = r.stdout.strip().splitlines()
    acc = []
    for line in reversed(out):
        acc.insert(0, line)
        try:
            v = json.loads("\n".join(acc))
        except ValueError:
            continue
        if isinstance(v, dict) and "dispatched" in v:
            return v
        # 解析成功但不是 summary（嵌套碎片）——继续向前累积
    # 无法取回 summary：视作投递未确认（宿主/daemon 可能离线），标 delivery_pending
    return {"error": "no json summary", "rc": r.returncode,
            "stderr": r.stderr[-300:], "deliveryPending": True}


def _wake_id():
    return "wake-{}-{}".format(time.strftime("%Y%m%d%H%M%S"), os.getpid())


def journal_append(entry):
    with open(JOURNAL, "a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def make_error_entry(started, wake_id, seed, rec, s):
    """C03: a wake that could not complete delivery/consumption is recorded as
    delivery_pending; never faked as consumed."""
    return {
        "ts": started, "kind": "tick", "wakeId": wake_id,
        "deliveryPending": True, "consumeId": None, "seed": seed, "reconcile": rec,
        "error": s.get("error"), "rc": s.get("rc"), "stderrTail": s.get("stderr"),
    }


def make_tick_entry(started, wake_id, seed, rec, s, pool_plan=None):
    consumed = [(x.get("orderId"), x.get("outcome")) for x in (s.get("dispatched") or [])]
    resumed = len(s.get("resumed") or [])
    backlog = len(s.get("backlog") or [])
    unenriched = len(s.get("unenrichedModel") or [])
    # consumeId 只在真实消费发生时存在（log/heartbeat 不是消费证明——C03 验收）
    consume_id = ("consume-{}-{}".format(time.strftime("%Y%m%d%H%M%S"), os.getpid())
                  if consumed or resumed else None)
    locked = bool(s.get("locked"))
    return {
        "ts": started,
        "kind": "tick",
        "wakeId": wake_id,
        "deliveryPending": False,
        "consumeId": consume_id,
        "seed": seed,
        "reconcile": rec,
        "consumed": consumed,
        "resumed": resumed,
        # Step 5 honest enumeration — real connected backlog the tick refused to fake-complete
        "backlog": backlog,
        "unenrichedModel": unenriched,
        # busy 合并：另一 tick 持锁，本唤醒并入等待，不重复派发（C03：busy 合并）
        "busyMerged": locked,
        # 无变化不刷用户通知（C03：无变化不刷用户通知）
        "noChange": (not consumed) and resumed == 0 and not locked,
        "inflight": len(s.get("inflight") or []),
        "stats": s.get("stats"),
        "checkpointAt": (s.get("checkpoint") and "written") or None,
        "poolPlan": pool_plan if pool_plan is not None else pool_snapshot(),
    }


def main():
    ensure_skeleton()
    started = time.strftime("%Y-%m-%dT%H:%M:%S")
    wake_id = _wake_id()
    seed = ensure_live_orders()  # Step 5: connect the real typed queue before ticking
    rec = reconcile_live_orders()  # C: un-park downstream whose deps are now accepted
    s = run_tick()
    if s.get("error"):
        # delivery_pending：本次唤醒未能完成投递/消费确认；如实记账，不伪造消费
        entry = make_error_entry(started, wake_id, seed, rec, s)
        journal_append(entry)
        print(json.dumps(entry, ensure_ascii=False))
        return 0

    entry = make_tick_entry(started, wake_id, seed, rec, s)
    journal_append(entry)
    print(json.dumps(entry, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
