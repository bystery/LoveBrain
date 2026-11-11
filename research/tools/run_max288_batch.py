# -*- coding: utf-8 -*-
r"""tools/run_max288_batch.py — MAX-288 V2 P06 薄入口（复用现有 execute_plan/IsolatedRun/预算）。

一条命令完成：冻结计划 + 有限授权 + 输出目录 + 模式（precheck/capture/live/live_stub/mock）。
不重写网关/宿主/评分器；不改候选；不自动重试/补配对/复活旧单。

用法（三条实际可复制命令）：
  # 0) 零派发预检（不构造 IsolatedRun；只为授权账本写 scope 绑定，不占 attempt）
  D:\anaconda\python.exe tools\run_max288_batch.py --plan refs\experiment_protocol_20261023\p06_20261027\P06_A_BASE_VS_V5.INTERLEAVE_PLAN.json --authorization refs\experiment_protocol_20261023\p06_20261027\AUTHORIZATION_P06.json --mode precheck
  # 1) 零网络 capture（替身 urlopen；数量/顺序/出站白名单真实核验）
  D:\anaconda\python.exe tools\run_max288_batch.py --plan ...P06_A...json --mode capture --out logs\p06_A_capture_20261027
  # 2) 真实 live（同一入口，只换真实适配器；需新授权单）
  D:\anaconda\python.exe tools\run_max288_batch.py --plan ...P06_A...json --authorization ...AUTHORIZATION_P06.json --mode live --out logs\p06_A_live_20261027

派发前守卫（任一不符 → 零派发退出）：
  G1 计划文件存在且 sha256 == 授权 frozen_bindings[batch].plan_sha256（现场重算）
  G2 plan.total_attempts / batch_id / arm_mapping / cards_path 与授权登记一致
  G3 exam_interleave_plan.verify_frozen_identity(plan)：两臂闭包+题面/卡/执行器全文件重算
  G4 两臂 identity_sha256 == 授权冻结值（若授权含 arm_identities_frozen，再比目录哈希集）
  G5 授权单合法（authorized/status/real_endpoint 对象）；AuthorizationBudget.ensure_batch
  G6 live/live_stub：网关 /health 可达；输出目录不存在（拒绝覆盖历史答卷）
  G7 授权若含 execution_entry_sha256，本文件自身 sha256 必须一致（执行入口不可漂移）
不是四臂 128：本入口按三批两臂 192 路线使用；跨批横比只作探索。
"""
import argparse
import datetime
import hashlib
import json
import os
import sys
import traceback
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep                                    # noqa: E402
from isolation.authorization_budget import (AuthorizationBudget,     # noqa: E402
                                             BudgetRefused, LedgerCorrupt,
                                             validate_authorization_doc)


def sha_file(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def sha_dir(d):
    out = {}
    for root, _ds, files in os.walk(d):
        for f in files:
            if f.endswith(".pyc"):
                continue
            p = os.path.join(root, f)
            rel = os.path.basename(d) + "/" + os.path.relpath(p, d).replace("\\", "/")
            out[rel] = sha_file(p)
    return out


def health_url(api_base):
    b = (api_base or "").rstrip("/")
    if b.endswith("/v1"):
        b = b[:-3]
    return b + "/health"


def gateway_ok(api_base):
    try:
        with urllib.request.urlopen(health_url(api_base), timeout=5) as r:
            return json.loads(r.read().decode("utf-8", "replace")).get("ok") is True
    except Exception:                                               # noqa: BLE001
        return False


def _has_turn_dirs(out):
    if not os.path.isdir(out):
        return False
    for _root, dirs, _files in os.walk(out):
        if any(d.startswith("turn_") for d in dirs):
            return True
    return False


def find_binding(auth, batch_id):
    for key, b in (auth.get("frozen_bindings") or {}).items():
        if b.get("batch_id") == batch_id:
            return key, b
    raise AssertionError("授权 frozen_bindings 中找不到 batch_id=%s" % batch_id)


def precheck(plan, auth, mode, out):
    checks = []
    batch_id = plan["batch"]["batch_id"]
    key, binding = find_binding(auth, batch_id)
    checks.append("G2 batch=%s binding=%s attempts=%d" % (batch_id, key, plan["total_attempts"]))

    # G1 计划 sha
    plan_path = plan.get("_path")
    if not plan_path or not os.path.isfile(plan_path):
        raise AssertionError("计划路径缺失：%r" % plan_path)
    now_sha = sha_file(plan_path)
    if now_sha != binding.get("plan_sha256"):
        raise AssertionError("G1 计划漂移：授权 %s… 实为 %s…"
                             % (str(binding.get("plan_sha256"))[:16], now_sha[:16]))
    checks.append("G1 plan_sha256=%s…（现场重算）" % now_sha[:16])

    # G2 字段一致
    if plan["total_attempts"] != binding.get("attempts"):
        raise AssertionError("G2 attempts %s != 授权 %s"
                             % (plan["total_attempts"], binding.get("attempts")))
    if plan["batch"]["batch_id"] != binding.get("batch_id"):
        raise AssertionError("G2 batch_id 不符")
    if plan["arm_mapping"].get("parent") != binding.get("parent") \
            or plan["arm_mapping"].get("candidate") != binding.get("candidate"):
        raise AssertionError("G2 两臂配对不符")
    if plan["batch"].get("cards_path") != binding.get("cards_file"):
        raise AssertionError("G2 卡路径不符：%r vs %r"
                             % (plan["batch"].get("cards_path"), binding.get("cards_file")))
    checks.append("G2 两臂=%s/%s cards=%s" % (
        plan["arm_mapping"]["parent"], plan["arm_mapping"]["candidate"],
        os.path.basename(binding["cards_file"])))

    # G3 冻结身份/文件
    ep.verify_frozen_identity(plan)
    checks.append("G3 两臂闭包 + 题面/卡/评分器/执行器全文件重算一致")

    # G4 身份与目录哈希集
    fz = plan["frozen"]["identity_closure"]
    for arm in ("parent", "candidate"):
        want = binding.get("%s_identity_sha256" % arm)
        if fz[arm]["identity_sha256"] != want:
            raise AssertionError("G4 %s 身份不符：计划 %s… vs 授权 %s…"
                                 % (arm, fz[arm]["identity_sha256"][:16],
                                    str(want)[:16]))
    arm_hashes = auth.get("arm_identities_frozen") or {}
    for arm in ("parent", "candidate"):
        cand = plan["arm_mapping"][arm]
        if cand in arm_hashes:
            now = set(sha_dir(os.path.join(ROOT, "candidates", cand)).values())
            frozen = set(arm_hashes[cand])
            if now != frozen:
                raise AssertionError("G4 %s 目录漂移（冻结 %d / 现状 %d）"
                                     % (cand, len(frozen), len(now)))
    checks.append("G4 两臂身份==授权；目录哈希集%s" %
                  ("一致" if arm_hashes else "未登记（只验闭包）"))

    # G5 授权合法性 + 整批预检
    norm = validate_authorization_doc(
        auth, expected_endpoint=ep.PINNED_ENDPOINT, expected_model=ep.PINNED_MODEL,
        expected_temperature=ep.PINNED_TEMPERATURE)
    ledger = auth.get("budget_ledger")
    if not os.path.isabs(ledger):
        ledger = os.path.join(ROOT, ledger)
    pre = AuthorizationBudget(ledger, norm["authorization_id"], norm["max_attempts"],
                              status=norm.get("status"), scope=auth.get("scope"))
    pre.ensure_batch(plan["total_attempts"], purpose="full_batch:%s:%s" % (mode, key))
    s = pre.summary()
    checks.append("G5 授权 %s cap=%s used=%s 余=%s 需=%d"
                  % (norm["authorization_id"], s["max_attempts"], s["used"],
                     s["remaining"], plan["total_attempts"]))

    # G6 网关/输出目录
    if mode in ("live", "live_stub"):
        if not gateway_ok(auth["real_endpoint"]["api_base"]):
            raise AssertionError("G6 网关不可达：%s" % auth["real_endpoint"]["api_base"])
        if _has_turn_dirs(out):
            raise AssertionError("G6 输出目录已有 turn_* 证据，拒绝覆盖：%s" % out)
    checks.append("G6 网关=%s 输出目录=%s" % (
        "ok" if mode in ("live", "live_stub") else "N/A", out))

    # G7 执行入口自校验
    want_entry = auth.get("execution_entry_sha256")
    if want_entry:
        now_entry = sha_file(os.path.abspath(__file__))
        if now_entry != want_entry:
            raise AssertionError("G7 执行入口漂移：授权 %s… 实为 %s…"
                                 % (want_entry[:16], now_entry[:16]))
        checks.append("G7 执行入口 sha256=%s… 一致" % now_entry[:16])
    else:
        checks.append("G7 授权未登记执行入口 sha（本批不强制）")

    for c in checks:
        print("  [OK]", c)
    return {"binding_key": key, "binding": binding, "norm": norm,
            "ledger": ledger, "summary": s, "plan_sha256": now_sha}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", required=True)
    ap.add_argument("--authorization", default=None)
    ap.add_argument("--out", default=None)
    ap.add_argument("--mode", default="precheck",
                    choices=["precheck", "capture", "mock", "live", "live_stub"])
    ap.add_argument("--precheck", action="store_true")
    ap.add_argument("--workers", type=int, default=None,
                    help="宿主级并行派发线程上限（缺省=全部宿主并行）")
    args = ap.parse_args(argv)
    mode = "precheck" if args.precheck else args.mode

    plan_path = os.path.abspath(args.plan)
    plan = json.load(open(plan_path, encoding="utf-8"))
    plan["_path"] = plan_path
    batch = plan["batch"]["batch_id"].replace("BATCH-", "").lower()
    out = os.path.abspath(args.out or os.path.join(
        ROOT, "logs", "p06_%s_%s_%s" % (
            batch, mode, datetime.datetime.now().strftime("%Y%m%d_%H%M%S"))))

    auth = None
    if args.authorization:
        auth = json.load(open(args.authorization, encoding="utf-8"))

    if mode in ("live", "live_stub") and auth is None:
        print("零派发拒绝：%s 必须提供 --authorization（绝不自动冒烟真实调用）" % mode)
        return 2

    if mode in ("precheck", "live", "live_stub"):
        if auth is None:
            print("零派发拒绝：%s 需要 --authorization 才能核对预算/身份" % mode)
            return 2
        try:
            info = precheck(plan, auth, mode, out)
        except (AssertionError, BudgetRefused, LedgerCorrupt, ep.PlanError) as e:
            print("零派发拒绝：%s" % str(e)[:400])
            return 2
        if mode == "precheck":
            print("PRECHECK ONLY：零真实调用（未构造 IsolatedRun，未占 attempt）。")
            print("  plan=%s attempts=%d out=%s" % (
                os.path.basename(plan_path), plan["total_attempts"], out))
            return 0
        print("LIVE/LIVE_STUB 前置守卫通过：继续真实派发（%s）。" % mode)

    # capture/mock：不消费真实授权；capture 用进程内替身 urlopen。
    if mode == "capture":
        # 仍然做冻结/计划校验（不碰授权/预算）
        ep.verify_frozen_identity(plan)
        if _has_turn_dirs(out):
            print("零派发拒绝：capture 输出目录已有 turn_*，换新目录：%s" % out)
            return 2
    if mode == "mock":
        ep.verify_frozen_identity(plan)

    if mode == "live":
        os.environ["LB_STREAM_TIMING"] = "1"
        os.environ["LB_STREAM_USAGE"] = "1"
    os.makedirs(out, exist_ok=True)
    print("→ %s batch=%s attempts=%d out=%s stream=%s" % (
        mode, plan["batch"]["batch_id"], plan["total_attempts"], out,
        "on(+usage)" if mode == "live" else "off"))
    sys.stdout.flush()
    try:
        if mode == "capture":
            res = ep.execute_plan(plan, mode="capture", out_base=out)
        elif mode == "mock":
            res = ep.execute_plan(plan, mode="mock", out_base=out, keep_tmp=True)
        else:
            res = ep.execute_plan(plan, mode=mode, authorization=auth, out_base=out,
                                  workers=args.workers)
    except Exception as e:                                            # noqa: BLE001
        print("%s ERROR: %s: %s" % (mode, type(e).__name__, e))
        print(traceback.format_exc()[-1600:])
        return 1

    plan_sha = sha_file(plan_path)
    json.dump(res, open(out + ".RESULT.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1, default=str)
    rep = ep.reports_from_batch(res, plan, out, out_dir=os.path.join(out, "_reports"))
    json.dump(rep, open(out + ".REPORTS.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1, default=str)

    run_ids = sorted(set(r.get("run_id") for r in res.get("runs") or []))
    done = {
        "batch_id": res.get("batch_id"),
        "authorization_id": res.get("authorization_id"),
        "arm_mapping": res.get("arm_mapping"),
        "cards_file": plan["batch"]["cards_path"],
        "plan_sha256": plan_sha,
        "arm_identity_sha256": {a: plan["frozen"]["identity_closure"][a]["identity_sha256"]
                                for a in ("parent", "candidate")},
        "mode": res.get("mode"),
        "attempts_planned": res.get("attempts_planned"),
        "started": res.get("attempt_started"),
        "completed": res.get("attempt_completed"),
        "failed": res.get("attempt_failed"),
        "stopped": res.get("stopped"),
        "order_verified": res.get("execution_order_verified"),
        "dispatch_bijection": res.get("dispatch_bijection"),
        "residents": res.get("residents"),
        "distinct_run_ids": len(run_ids),
        "state_isolated_real": len(run_ids) == res.get("residents"),
        "turns_parent": rep.get("turns_parent"),
        "turns_candidate": rep.get("turns_candidate"),
        "cost_parent": (rep.get("parent") or {}).get("cost"),
        "cost_candidate": (rep.get("candidate") or {}).get("cost"),
        "program_mean10": {"parent": (rep.get("parent") or {}).get("program_mean10"),
                           "candidate": (rep.get("candidate") or {}).get("program_mean10")},
        "paired_program_winner_counts": rep.get("paired_program_winner_counts"),
        "all_semantic_unrated_without_annotations":
            rep.get("all_semantic_unrated_without_annotations"),
        "real_model_calls": res.get("real_model_calls"),
        "network": res.get("network"),
        "stream_identity": "api_cfg:openai_compat_stream_true_v1(include_usage gated)"
                           if mode in ("live", "live_stub") else "offline",
        "reports_dir": rep.get("out_dir"),
        "out_dir": out,
    }
    json.dump(done, open(out + ".DONE.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("DONE:", json.dumps(done, ensure_ascii=False)[:1100])

    if mode in ("capture", "mock"):
        n = plan["total_attempts"]
        ok = (done["started"] == n and done["completed"] == n and done["failed"] == 0
              and done["stopped"] is None and done["order_verified"]
              and done["dispatch_bijection"] and done["real_model_calls"] == 0
              and done["distinct_run_ids"] == done["residents"]
              and not res.get("boundary_problems"))
        if not ok:
            print("CAPTURE/MOCK 自检失败（见 %s.RESULT.json）" % out)
            return 1
    if mode == "live":
        if done["stopped"] is not None or done["failed"] or done["completed"] != done["started"]:
            print("LIVE 未完整收口：stopped=%s failed=%s completed=%s/%s"
                  % (done["stopped"], done["failed"], done["completed"], done["started"]))
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
