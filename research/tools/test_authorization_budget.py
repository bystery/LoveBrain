# -*- coding: utf-8 -*-
"""授权级预算闭环反例（V14.1 §2 收口 / 复核 v3 执行书 §2A 同族覆盖）——
零真实模型调用。

原则：断言【实际派发次数】（CountingAdapter 类级计数），不看拒绝文案。
覆盖：整批预检拒绝 / 新 run 重入不重置 / 真实多进程账本一致 / 并发抢占原子 /
重复 attempt 拒绝 / started 落盘失败零派发 / 关闭授权复活拒绝 /
复核 v3 七个同族绕过（无预算代理派发、关闭后旧实例派发、跨实例重复
attempt、重建放大额度、坏账本清零、陈旧实例预检放行、CLI 省略授权）。
"""
import json
import os
import shutil
import sys
import tempfile
import threading

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))

from lb_gateway import ADAPTERS                      # noqa: E402
from isolation.authorization_budget import (         # noqa: E402
    AuthorizationBudget, BudgetRefused, LedgerCorrupt)
from isolation.trusted_controller import TrustedProxy  # noqa: E402


class CountingAdapter(object):
    """确定性假适配器：类级计数 = 跨所有代理实例的真实派发总数。"""
    name = "counting"
    dispatched = 0

    def generate(self, system, user, payload):
        CountingAdapter.dispatched += 1
        return {"text": "ok", "finish": "stop",
                "usage": {"prompt_tokens": 1, "completion_tokens": 1}}


ADAPTERS["counting"] = CountingAdapter


def _mkbudget(tmp, used, max_attempts=8, auth_id="AUTH-TEST-01"):
    """预置一个已消耗 used 次的授权账本。"""
    path = os.path.join(tmp, "auth_budget", "%s.jsonl" % auth_id)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for i in range(used):
            f.write(json.dumps({
                "kind": "attempt_started", "ts": "t", "pid": 0,
                "authorization_id": auth_id,
                "attempt_id": "prior_g%04d" % (i + 1)}) + "\n")
    return path


def _mkproxy(tmp, budget, tag):
    """新建 TrustedProxy（全新 control/run——模拟新 run 重入）。"""
    ctl = os.path.join(tmp, "ctl_" + tag)
    os.makedirs(ctl, exist_ok=True)
    return TrustedProxy(ctl, "run_" + tag, "cand", "case", 8,
                        adapter_name="counting", run_id="run_" + tag,
                        budget=budget)


def check(name, fn, results):
    try:
        fn()
        results.append((name, True, ""))
        print("PASS %s" % name)
    except Exception as e:
        results.append((name, False, str(e)[:220]))
        print("FAIL %s | %s" % (name, str(e)[:220]))


# ---------- 反例 ----------

def t_batch_precheck_zero_dispatch(tmp):
    """已用 6/8，整批需 6 → 预检拒绝，实际派发 0（不跑到一半再补额）。"""
    before = CountingAdapter.dispatched
    lp = _mkbudget(tmp, used=6)
    b = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    try:
        b.ensure_batch(6, purpose="retry_batch")
        raise AssertionError("ensure_batch 应拒绝")
    except BudgetRefused:
        pass
    assert CountingAdapter.dispatched == before, "预检拒绝后不得有任何派发"
    # 即使绕过预检逐次派发，也只剩 2 次可派
    p = _mkproxy(tmp, b, "bp1")
    ok1, _ = p.generate({"system": "s", "user": "u"})
    ok2, _ = p.generate({"system": "s", "user": "u"})
    ok3, _ = p.generate({"system": "s", "user": "u"})
    assert (ok1, ok2) == (True, True) and ok3 is False, \
        "剩余 2 次应只放行 2 次"
    assert CountingAdapter.dispatched == before + 2, \
        "实际派发应为 2，实得 %d" % (CountingAdapter.dispatched - before)


def t_new_run_no_reset(tmp):
    """新 run 目录/新代理实例/重启进程 → 预算不重置（8 上限全局累计）。"""
    before = CountingAdapter.dispatched
    lp = _mkbudget(tmp, used=6)
    for tag in ("r1", "r2", "r3"):
        b = AuthorizationBudget(lp, "AUTH-TEST-01", 8)   # 每次全新实例
        p = _mkproxy(tmp, b, tag)
        p.generate({"system": "s", "user": "u"})
    assert CountingAdapter.dispatched == before + 2, \
        "三个新 run 只应共派发 2 次（6+2=8），实得 %d" % (
            CountingAdapter.dispatched - before)
    recs = [json.loads(l) for l in open(lp, encoding="utf-8") if l.strip()]
    started = [r for r in recs if r["kind"] == "attempt_started"]
    assert len(started) == 8, "授权账本 started 应为 8"
    assert len({r["attempt_id"] for r in started}) == 8, "attempt_id 必须全局唯一"


def t_cross_process_ledger(tmp):
    """真实 10 个 OS 进程读同一账本占额（复核 v3：原版只是两个对象不算
    多进程）：已用 4/8 → 恰 4 个进程接受、6 个拒绝，总 started=8。"""
    import subprocess
    lp = _mkbudget(tmp, used=4)
    child = (
        "import sys, os\n"
        "sys.path.insert(0, %r)\n"
        "sys.path.insert(0, %r)\n"
        "from isolation.authorization_budget import (AuthorizationBudget,\n"
        "    BudgetRefused, LedgerCorrupt)\n"
        "aid, lp, tag = sys.argv[1], sys.argv[2], sys.argv[3]\n"
        "try:\n"
        "    b = AuthorizationBudget(lp, aid, 8)\n"
        "    b.reserve('mp_g' + tag)\n"
        "    print('OK')\n"
        "except BudgetRefused:\n"
        "    print('REFUSED')\n"
        "except LedgerCorrupt as e:\n"
        "    print('CORRUPT:', e)\n"
    ) % (os.path.join(ROOT, "arena"), ROOT, )
    results = []
    procs = []
    for i in range(10):
        p = subprocess.Popen(
            [sys.executable, "-B", "-c", child, "AUTH-TEST-01", lp,
             "%04d" % i],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            encoding="utf-8")
        procs.append(p)
    for p in procs:
        out, err = p.communicate(timeout=60)
        results.append(out.strip())
    n_ok = sum(1 for r in results if r == "OK")
    n_ref = sum(1 for r in results if r == "REFUSED")
    assert n_ok == 4 and n_ref == 6, \
        "真实 10 进程应恰 4 接受 6 拒绝，实得 %r" % (results,)
    recs = [json.loads(l) for l in open(lp, encoding="utf-8") if l.strip()]
    started = [r for r in recs if r["kind"] == "attempt_started"]
    assert len(started) == 8, "总 started 应为 8，实得 %d" % len(started)
    assert len({r["attempt_id"] for r in started}) == 8


def t_concurrent_atomic(tmp):
    """并发抢占：已用 4/8，10 线程各占 1 → 恰 4 成功 6 拒绝（边界在并发中途穿越）。"""
    lp = _mkbudget(tmp, used=4)
    ok, bad = [], []
    lock = threading.Lock()

    def worker(i):
        try:
            # 构造也取锁（构造即核账）；锁超时/构造拒绝同样是"未派发"
            b = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
            b.reserve("cc_g%04d" % i)
            with lock:
                ok.append(i)
        except BudgetRefused:
            with lock:
                bad.append(i)

    ts = [threading.Thread(target=worker, args=(i,)) for i in range(10)]
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    assert len(ok) == 4 and len(bad) == 6, \
        "并发占额应恰 4 成功 6 拒绝，实得 ok=%d bad=%d" % (len(ok), len(bad))
    recs = [json.loads(l) for l in open(lp, encoding="utf-8") if l.strip()]
    assert sum(1 for r in recs if r["kind"] == "attempt_started") == 8


def t_duplicate_attempt(tmp):
    """重复 attempt_id 占额拒绝（消耗只计一次）。"""
    lp = _mkbudget(tmp, used=0)
    b = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    b.reserve("dup_g0001")
    try:
        b.reserve("dup_g0001")
        raise AssertionError("重复占额应拒绝")
    except BudgetRefused:
        pass
    b2 = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    assert b2.used == 1


def t_started_write_fail_zero_dispatch(tmp):
    """预算 started 落盘失败 → 零派发（CountingAdapter 计数不动）。"""
    before = CountingAdapter.dispatched
    bad_parent = os.path.join(tmp, "not_a_dir")
    open(bad_parent, "w").close()           # 父路径是文件 → 账本不可创建
    b = AuthorizationBudget.__new__(AuthorizationBudget)
    b.ledger_path = os.path.join(bad_parent, "auth.jsonl")
    b.authorization_id = "AUTH-TEST-01"
    b.max_attempts = 8
    b.lock_path = b.ledger_path + ".lock"
    b.used, b._attempt_ids, b.closed, b.close_reason = 0, set(), False, None
    p = _mkproxy(tmp, b, "wf")
    ok, body = p.generate({"system": "s", "user": "u"})
    assert ok is False, "落盘失败必须零派发（返回 False）"
    assert CountingAdapter.dispatched == before, \
        "实际派发必须为 0，实得 %d" % (CountingAdapter.dispatched - before)


def t_closed_authorization_no_revival(tmp):
    """授权关闭后：新 run/新实例/修改 status 字段都不得复活。
    （复核 v3 升级：关闭态在构造时即拒绝——连"挂上坏实例"的机会都不给。）"""
    lp = _mkbudget(tmp, used=3)
    b = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    b.close("episode completed")
    try:
        b.ensure_batch(1)
        raise AssertionError("关闭后 ensure_batch 应拒绝")
    except BudgetRefused:
        pass
    p = _mkproxy(tmp, b, "cl")
    ok, _ = p.generate({"system": "s", "user": "u"})
    assert ok is False, "关闭授权下派发必须被拒"
    # 全新实例（模拟重启后）：构造即拒绝（锁内核对关闭态）
    try:
        AuthorizationBudget(lp, "AUTH-TEST-01", 8)
        raise AssertionError("关闭授权的构造应拒绝")
    except BudgetRefused:
        pass
    # 直接 reserve 路径同样拒绝（绕过构造校验的对象）
    bz = _corrupt_budget(lp)
    bz.closed = False      # 模拟旧实例缓存
    try:
        bz.reserve("zombie_g0001")
        raise AssertionError("关闭后 reserve 应拒绝")
    except BudgetRefused:
        pass
    recs = [json.loads(l) for l in open(lp, encoding="utf-8") if l.strip()]
    assert not any(r.get("attempt_id") == "zombie_g0001" for r in recs), \
        "关闭后不得产生新 started"
    # 授权单侧 status=CLOSED 也不得复活（预算层 status 参数语义）
    b3 = AuthorizationBudget(lp, "AUTH-TEST-02", 8, status="CLOSED")
    assert b3.closed


def t_consumption_counts_failures(tmp):
    """失败与未决都计消耗：占额后无退款；record(attempt_failed) 不减 used。"""
    lp = _mkbudget(tmp, used=0)
    b = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    b.reserve("f_g0001")
    b.record("attempt_failed", "f_g0001", error="boom")
    b2 = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    assert b2.used == 1, "失败也计消耗，used 不得回退"


# ---------- 复核 v3 §2A 七个同族反例 ----------

def t_v3_proxy_without_budget_refused(tmp):
    """反例 2：无预算权威的代理（非 mock）必须拒绝派发——入口强制。"""
    before = CountingAdapter.dispatched
    p = _mkproxy(tmp, None, "nobudget")
    ok, body = p.generate({"system": "s", "user": "u"})
    assert ok is False, "无预算权威不得派发"
    assert CountingAdapter.dispatched == before, \
        "实际派发必须为 0，实得 %d" % (CountingAdapter.dispatched - before)
    evs = [r for r in p._events if r.get("kind") == "dispatch_refused_no_budget"]
    assert evs, "拒绝事件必须入代理内存账（flush 后随 run 证据留盘）"


def t_offline_allowlist_boundary(tmp):
    """离线允许清单边界（本应只在 mock/echo 上放行，且不得被悄悄扩大）：
    - OFFLINE_ADAPTERS 恰为 {mock, echo}——新增适配器默认进预算面（fail-closed）；
    - echo 无预算可派发（确定性离线替身，零上游、零消耗）；
    - 任何不在清单内的适配器无预算仍必须拒绝（由反例 2 覆盖同一断言）。"""
    from lb_gateway import ADAPTERS, OFFLINE_ADAPTERS
    assert OFFLINE_ADAPTERS == frozenset({"mock", "echo"}), \
        "离线允许清单被改动：%s" % sorted(OFFLINE_ADAPTERS)
    assert OFFLINE_ADAPTERS <= set(ADAPTERS), "清单项必须是真实适配器"
    before = CountingAdapter.dispatched
    p = TrustedProxy(os.path.join(tmp, "ctl_echo"), "run_echo", "cand", "case",
                     8, adapter_name="echo", run_id="run_echo", budget=None)
    ok, body = p.generate({"system": "s", "user": "u"})
    assert ok is True, "echo 为离线替身，无预算不得被拒：%s" % body
    assert not [r for r in p._events
                if r.get("kind") == "dispatch_refused_no_budget"], \
        "echo 派发不得记预算拒绝事件"
    assert CountingAdapter.dispatched == before, \
        "echo 是独立适配器，不得调用计数替身"


def t_v3_closed_checked_in_lock(tmp):
    """反例 3：实例 A 创建后授权被（实例 B 的）close 关闭——
    A 的实例缓存仍是 closed=False，reserve 必须在同一把锁内核对权威账本拒绝。"""
    before = CountingAdapter.dispatched
    lp = _mkbudget(tmp, used=2)
    b_a = AuthorizationBudget(lp, "AUTH-TEST-01", 8)     # 旧实例（缓存 closed=False）
    b_b = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    b_b.close("batch closed by trusted closeout")
    assert b_a.closed is False, "旧实例缓存未刷新（这正是要防的绕过面）"
    p = _mkproxy(tmp, b_a, "staleclosed")
    ok, _ = p.generate({"system": "s", "user": "u"})
    assert ok is False, "关闭后旧实例派发必须被拒（锁内核对）"
    assert CountingAdapter.dispatched == before


def t_v3_duplicate_attempt_across_instances(tmp):
    """反例 4：两个实例占用同一 attempt_id → 第二次拒绝（唯一性按权威账本）。"""
    before = CountingAdapter.dispatched
    lp = _mkbudget(tmp, used=0)
    b1 = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    b2 = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    b1.reserve("same_g0001")
    try:
        b2.reserve("same_g0001")
        raise AssertionError("跨实例重复 attempt_id 应拒绝")
    except BudgetRefused:
        pass
    recs = [json.loads(l) for l in open(lp, encoding="utf-8") if l.strip()]
    started = [r for r in recs if r["kind"] == "attempt_started"]
    assert len([r for r in started
                if r["attempt_id"] == "same_g0001"]) == 1, \
        "账本不得出现 2 条相同身份的 started"


def t_v3_max_attempts_immutable(tmp):
    """反例 5：同一授权已耗尽 1/1 → 以 max_attempts=2 重建对象必须被拒
    （额度上限首次使用即持久绑定，重建放大额度不认）。"""
    before = CountingAdapter.dispatched
    lp = _mkbudget(tmp, used=0)
    b1 = AuthorizationBudget(lp, "AUTH-TEST-01", 1)
    p1 = _mkproxy(tmp, b1, "immut1")
    ok, _ = p1.generate({"system": "s", "user": "u"})
    assert ok is True, "1/1 首次派发应成功（并绑定 scope）"
    try:
        AuthorizationBudget(lp, "AUTH-TEST-01", 2)
        raise AssertionError("以放大后的额度上限重建对象应拒绝")
    except BudgetRefused:
        pass
    p2 = _mkproxy(tmp, b1, "immut2")     # 旧实例也不能绕（锁内 scope 核对）
    ok2, _ = p2.generate({"system": "s", "user": "u"})
    assert ok2 is False, "额度耗尽后旧实例继续派发必须被拒"
    assert CountingAdapter.dispatched == before + 1


def t_v3_corrupt_ledger_stops_batch(tmp):
    """反例 6：账本坏 JSON 行 → LedgerCorrupt 停批，不得当作未消费清零派发。"""
    before = CountingAdapter.dispatched
    lp = _mkbudget(tmp, used=3)
    with open(lp, "a", encoding="utf-8") as f:
        f.write("{BROKEN JSON LINE\n")
    try:
        AuthorizationBudget(lp, "AUTH-TEST-01", 8)
        raise AssertionError("坏账本构造必须抛 LedgerCorrupt")
    except LedgerCorrupt:
        pass
    p = _mkproxy(tmp, None, "corrupt")
    p.budget = _corrupt_budget(lp)
    ok, _ = p.generate({"system": "s", "user": "u"})
    assert ok is False, "坏账本下派发必须被拒（停批而非清零）"
    assert CountingAdapter.dispatched == before


def _corrupt_budget(lp):
    """绕过构造校验、直接挂坏账本的预算对象（模拟在账本损坏后仍被引用）。"""
    b = AuthorizationBudget.__new__(AuthorizationBudget)
    b.ledger_path = lp
    b.authorization_id = "AUTH-TEST-01"
    b.max_attempts = 8
    b.lock_path = lp + ".lock"
    b.used, b._attempt_ids = 0, set()
    b.closed, b.close_reason, b._scope, b._ledger_scope = False, None, None, None
    return b


def t_v3_ensure_batch_rereads_authority(tmp):
    """反例 7：陈旧实例（缓存 used=0）在其他实例消耗后整批预检必须重读权威账拒绝。"""
    before = CountingAdapter.dispatched
    lp = _mkbudget(tmp, used=0)
    b_stale = AuthorizationBudget(lp, "AUTH-TEST-01", 8)   # 缓存 used=0
    b_other = AuthorizationBudget(lp, "AUTH-TEST-01", 8)
    for i in range(6):
        b_other.reserve("other_g%04d" % (i + 1))
    assert b_stale.used == 0, "陈旧实例缓存未刷新（绕过面）"
    try:
        b_stale.ensure_batch(6, purpose="stale_batch")
        raise AssertionError("陈旧实例预检应按权威账拒绝")
    except BudgetRefused:
        pass
    assert CountingAdapter.dispatched == before


def t_v3_cli_requires_authorization(tmp):
    """反例 1：CLI 非 mock 适配器省略 --authorization → 拒绝启动（exit 2），
    不创建控制器/宿主。"""
    import subprocess
    r = subprocess.run(
        [sys.executable, "-B", "-X", "utf8",
         os.path.join(ROOT, "tools", "iso_run.py"),
         "--episode", "benchmark/episodes/NCA_CORR_INTENT_v1.json",
         "--candidate", "reference_product_v1",
         "--out", os.path.join(tmp, "cli_no_auth"),
         "--adapter", "openai_compat"],
        capture_output=True, text=True, encoding="utf-8", timeout=60,
        cwd=ROOT)
    assert r.returncode == 2, "CLI 无授权必须 exit 2，实得 %d (%s)" % (
        r.returncode, (r.stdout or r.stderr)[:200])
    body = json.loads(r.stdout.strip().splitlines()[-1])
    assert "requires --authorization" in body.get("error", "")
    assert not os.path.isdir(os.path.join(tmp, "cli_no_auth")), \
        "拒绝路径不得创建输出目录（控制器未启动）"


def t_v3_authorization_doc_validation(tmp):
    """接线问题 1：授权单严格校验——缺 ID / authorized 非布尔 true /
    未知 status / 非法额度 / 端点或 model 绑定不符 → 全部拒绝；
    合法单 + 匹配出站配置 → 通过并规范化。"""
    from isolation.authorization_budget import validate_authorization_doc
    good = {"authorization_id": "AUTH-OK", "authorized": True,
            "status": "ACTIVE",
            "real_endpoint": {"api_base": "http://127.0.0.1:9/v1",
                              "model": "m1", "temperature": 0.7,
                              "max_attempts": 8}}
    norm = validate_authorization_doc(good)
    assert norm["max_attempts"] == 8 and norm["authorization_id"] == "AUTH-OK"
    # 端点/model 绑定不符拒绝
    for kw in ({"expected_endpoint": "http://other:1/v1"},
               {"expected_model": "m2"}):
        try:
            validate_authorization_doc(good, **kw)
            raise AssertionError("绑定不符应拒绝: %r" % kw)
        except BudgetRefused:
            pass
    def _bad(mutate):
        import copy
        d = copy.deepcopy(good)
        mutate(d)
        try:
            validate_authorization_doc(d)
            raise AssertionError("应拒绝: %r" % d)
        except BudgetRefused:
            pass
    _bad(lambda d: d.pop("authorization_id"))
    _bad(lambda d: d.update({"authorized": "yes"}))
    _bad(lambda d: d.update({"authorized": None}))
    _bad(lambda d: d.update({"status": "UNKNOWN_STATE"}))
    _bad(lambda d: d["real_endpoint"].update({"max_attempts": 0}))
    _bad(lambda d: d["real_endpoint"].update({"max_attempts": "8"}))
    _bad(lambda d: d["real_endpoint"].update({"max_attempts": True}))
    _bad(lambda d: d["real_endpoint"].pop("api_base"))
    _bad(lambda d: d["real_endpoint"].pop("model"))


def main():
    results = []
    tmp_root = tempfile.mkdtemp(prefix="auth_budget_")
    tests = [
        ("batch_precheck_zero_dispatch", t_batch_precheck_zero_dispatch),
        ("new_run_no_reset", t_new_run_no_reset),
        ("cross_process_ledger_real_10proc", t_cross_process_ledger),
        ("concurrent_atomic", t_concurrent_atomic),
        ("duplicate_attempt", t_duplicate_attempt),
        ("started_write_fail_zero_dispatch", t_started_write_fail_zero_dispatch),
        ("closed_authorization_no_revival", t_closed_authorization_no_revival),
        ("consumption_counts_failures", t_consumption_counts_failures),
        # 复核 v3 §2A 七个同族反例
        ("v3_proxy_without_budget_refused", t_v3_proxy_without_budget_refused),
        ("offline_allowlist_boundary", t_offline_allowlist_boundary),
        ("v3_closed_checked_in_lock", t_v3_closed_checked_in_lock),
        ("v3_duplicate_attempt_across_instances",
         t_v3_duplicate_attempt_across_instances),
        ("v3_max_attempts_immutable", t_v3_max_attempts_immutable),
        ("v3_corrupt_ledger_stops_batch", t_v3_corrupt_ledger_stops_batch),
        ("v3_ensure_batch_rereads_authority",
         t_v3_ensure_batch_rereads_authority),
        ("v3_cli_requires_authorization", t_v3_cli_requires_authorization),
        ("v3_authorization_doc_validation", t_v3_authorization_doc_validation),
    ]
    for i, (name, fn) in enumerate(tests):
        tmp = os.path.join(tmp_root, "c%02d" % i)
        os.makedirs(tmp, exist_ok=True)
        check(name, lambda fn=fn, tmp=tmp: fn(tmp), results)
    shutil.rmtree(tmp_root, ignore_errors=True)
    fails = [r for r in results if not r[1]]
    print("authorization_budget_counterexamples: %d/%d PASS"
          % (len(results) - len(fails), len(results)))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
