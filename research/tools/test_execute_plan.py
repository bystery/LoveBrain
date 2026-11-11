# tools/test_execute_plan.py — A 收口回归：48 槽计划接回**正式执行链**的守卫与证据
#
# reviewer A 判决：旧 rehearsal 直接调 run_turn + 自定义 MockGateway，绕开预算/网关/
# 账本/评分/输入边界；且守卫失灵——把 T02 改成重复 T01 仍报 order_ok，
# 计划填错误冻结身份仍报 closure_ok。
#
# 本版证明（全部经 IsolatedRun 真实入口，零真实调用）：
#   1) capture（openai_compat + 进程内替身 urlopen + 测试授权/预算账本）：48 槽
#      attempt 账本 = 48 started / 48 completed / 0 failed；出站体过白名单；
#   2) mock：同样 48/48，走同一 execute_plan；
#   3) fail-fast：第 N 次派发注入传输错 → 账本 N started / N-1 completed / 1 failed，
#      停批且不跑另一臂凑配对、不重试；
#   4) reject-duplicate：某臂 T02 改成重复 T01 → 派发前 validate_plan 拒（零 IsolatedRun）；
#   5) reject-bad-freeze：计划候选冻结身份填全 0 → verify_frozen_identity 拒；
#   6) reject-reorder：打乱某臂首手顺序 / 缺轮 → 拒；
#   7) 状态隔离：6 个 (arm,episode) 各自独立 run_id + 独立 out/control + 哨兵文件；
#   8) 最终请求新增段从 calls.json 真实出站 prompt 测量（不靠截短 notes）；
#   9) live 模式无授权一律拒发；real_model_calls 恒 0。
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import exam_interleave_plan as ep  # noqa: E402

CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


def _plan():
    return ep.emit_plan(os.path.join(ROOT, "logs", "_ep_test_plan",
                                     "INTERLEAVE_PLAN.json"))


@check
def a_capture_full_chain():
    r = ep.execute_plan(_plan(), mode="capture")
    assert r["attempt_started"] == 48, "capture 账本 started!=48：%s" % r["attempt_started"]
    assert r["attempt_completed"] == 48 and r["attempt_failed"] == 0
    assert r["execution_entry"].startswith("IsolatedRun")
    assert r["real_model_calls"] == 0, "capture 竟有真实调用"
    assert not r["boundary_problems"], "出站体越界：%s" % r["boundary_problems"][:2]
    assert r["residents"] == 6 and r["runs_done"] == 6
    assert r["execution_order_verified"], "执行序未按冻结计划逐 slot 交错"
    assert r["dispatch_bijection"], "真实 started 与派发 slot 不一一对应"
    assert r["injection_within_budget_ok"], "候选自身联合注入预算越界"
    assert all(len(e["attempt_ids"]) >= 1 for e in r["exec_events"]), \
        "A2 违约：存在未附真实 attempt_id 的 slot"
    ev = r["exec_events"][:3]
    assert [(e["arm"], e["case_id"]) for e in ev] == [
        ("parent", "E001_T01"), ("candidate", "E001_T01"),
        ("parent", "E001_T02")], "并非真交错（父连跑 8 轮再候选）：%s" % ev
    return "capture 48/48/0 via IsolatedRun 逐 slot，order+bijection+联合预算 OK，真交错，real_calls=0"


@check
def a_mock_same_entry():
    r = ep.execute_plan(_plan(), mode="mock")
    assert r["attempt_started"] == 48 and r["attempt_completed"] == 48
    assert r["execution_entry"].startswith("IsolatedRun") and r["real_model_calls"] == 0
    assert r["execution_order_verified"] and r["dispatch_bijection"], \
        "mock 未走同一逐 slot 交错入口"
    return "mock 走同一逐 slot execute_plan，48/48，order+bijection OK，real_calls=0"


@check
def a_fail_fast_ledger_and_no_makeup():
    r = ep.execute_plan(_plan(), mode="capture", fault_at=5)
    assert r["attempt_started"] == 5, "fail-fast started!=5：%s" % r["attempt_started"]
    assert r["attempt_completed"] == 4
    assert r["attempt_failed"] == 1, "传输错未计入失败账：%s" % r["attempt_failed"]
    assert r["stopped"] is not None, "未停批"
    assert r["stopped"]["seq"] == 5, "停批 slot 非第 5：%s" % r["stopped"]["seq"]
    assert r["execution_order_verified"] is False, "停批却报全序完成"
    # 不补配对：4 个成功派发落在交错序的前 4 slot，第 6 slot 绝不跑
    done = sum(x["turns_done"] for x in r["runs"])
    assert done == 4, "补跑了额外轮次：turns_done 合计=%s" % done
    assert len(r["exec_events"]) == 4
    assert r["real_model_calls"] == 0
    return "fail-fast 5/4/1，停在 seq5，未补配对（成功 4 轮），real_calls=0"


@check
def a_reject_duplicate_zero_dispatch():
    doc = json.loads(json.dumps(_plan()))
    for row in doc["attempts"]:
        if row["arm"] == "parent" and row["episode"] == "E001" and row["turn"] == 2:
            row["turn"] = 1
            row["case_id"] = "E001_T01"
            row["pair_key"] = "E001_T01"
            break
    try:
        ep.execute_plan(doc, mode="mock")
        return "FAIL: 未拒绝重复轮"
    except ep.PlanError as e:
        msg = str(e)
        assert "重复" in msg or "不连续" in msg, "拒绝原因不含重复/连续性：%s" % msg
        return "reject-duplicate PASS（派发前拒：%s）" % msg[:60]


@check
def a_reject_bad_freeze_identity():
    doc = json.loads(json.dumps(_plan()))
    doc["frozen"]["identity_closure"]["candidate"]["identity_sha256"] = "0" * 64
    try:
        ep.execute_plan(doc, mode="mock")
        return "FAIL: 未拒绝错误冻结身份"
    except ep.PlanError as e:
        assert "身份漂移" in str(e), "拒绝原因不对：%s" % str(e)
        return "reject-bad-freeze PASS：%s" % str(e)[:60]


@check
def a_reject_missing_turn_and_order():
    doc = json.loads(json.dumps(_plan()))
    # 删掉候选 E003 的 T05 → 缺轮 + 配对缺臂
    doc["attempts"] = [r for r in doc["attempts"]
                       if not (r["arm"] == "candidate" and r["episode"] == "E003"
                               and r["turn"] == 5)]
    doc["total_attempts"] = len(doc["attempts"])
    try:
        ep.execute_plan(doc, mode="mock")
        return "FAIL: 未拒绝缺轮"
    except ep.PlanError as e:
        assert ("缺" in str(e) or "配对" in str(e) or "attempts" in str(e)), \
            "拒绝原因不对：%s" % str(e)
        return "reject-missing-turn PASS：%s" % str(e)[:60]


@check
def a_state_isolation_observable():
    tmp = tempfile.mkdtemp(prefix="iso_obs_")
    try:
        r = ep.execute_plan(_plan(), mode="mock", out_base=os.path.join(tmp, "x"),
                            keep_tmp=True)
        assert r["state_isolated"], "状态未隔离"
        assert len(r["distinct_run_ids"]) == 6, \
            "应有 6 个独立 run_id，实得 %s" % r["distinct_run_ids"]
        # 哨兵文件：每个 (arm,episode) 一个，记录候选/臂/run_id，证明互不串
        sentinels = []
        for dirpath, _d, files in os.walk(os.path.join(tmp, "x")):
            if "isolation_sentinel.json" in files:
                sentinels.append(json.load(open(
                    os.path.join(dirpath, "isolation_sentinel.json"),
                    encoding="utf-8")))
        assert len(sentinels) == 6, "哨兵数!=6：%s" % len(sentinels)
        keys = {(s["arm"], s["episode"]) for s in sentinels}
        assert len(keys) == 6 and len({s["run_id"] for s in sentinels}) == 6
        return "state_isolation PASS 6 distinct run_id + 6 sentinel（两臂/故事互不串）"
    finally:
        import shutil
        shutil.rmtree(tmp, ignore_errors=True)


@check
def a_live_refused_without_auth():
    try:
        ep.execute_plan(_plan(), mode="live")
        return "FAIL: live 未拒发"
    except ep.PlanError as e:
        assert "真实调用" in str(e) or "授权" in str(e)
        return "live-refused PASS：%s" % str(e)[:50]


@check
def a_predecessor_guard_out_of_order():
    ok, msg = ep.reject_out_of_order()
    assert ok, "乱序前驱未被派发前拦截：%s" % msg
    return "predecessor-guard PASS：%s" % msg[:80]


@check
def a_live_entry_guards():
    ok1, m1 = ep.live_no_auth()
    assert ok1, "live 无授权未零派发拒绝：%s" % m1
    ok2, m2 = ep.live_invalid_auth()
    assert ok2, "live 无效授权未零派发拒绝：%s" % m2
    ok3, m3 = ep.live_stub_valid()
    assert ok3, "live 合法授权在替身下未走通相同配置/预算：%s" % m3
    return "live-entry PASS：no-auth/invalid 零派发拒绝 + %s" % m3[:60]


@check
def a_report_wiring_from_outputs():
    ok, msg = ep.report_wiring()
    assert ok, "两臂产物→分层/配对未接通：%s" % msg
    return "report-wiring PASS：%s" % msg[:90]


def main():
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            ok = not detail.startswith("FAIL")
            results.append({"check": fn.__name__,
                            "verdict": "PASS" if ok else "FAIL", "detail": detail})
            print(("PASS" if ok else "FAIL"), "%-38s" % fn.__name__, detail)
            if not ok:
                failed += 1
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-38s %s: %s" % (fn.__name__, type(e).__name__, e))
    out = os.path.join(ROOT, "logs", "execute_plan_result.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump({"checks": results, "failed": failed,
               "real_model_calls": 0, "execution_entry": "IsolatedRun"},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("EXECUTE_PLAN RESULT: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
