# -*- coding: utf-8 -*-
"""tools/run_c4_batch.py — C4 机制筛选派发（批A=原版 vs goal_action_v1，批B=v5 vs evidence_memory_v1）。

本 docstring 即"承诺清单"，每一条都在下面有对应代码，不做没写过的声明
（上一版此处写着"计划 sha 与冻结计划一致"但代码里没有——正是这种口径差要根除）。
  A. 绑定 AUTHORIZATION_V2.json（旧 AUTHORIZATION.json 从未消费且 real_endpoint 形状非法，已被取代）。
  B. 派发前逐项硬校验，任一不符 → 零真实调用直接退出：
       B1 授权 authorized=true / status=ACTIVE / 批次在 scope.batches；
       B2 计划文件 sha256 == 授权 frozen_bindings[key].plan_sha256（现场重算，不采信文档）；
       B3 计划 total_attempts / batch_id / 两臂配对 与授权登记一致；
       B4 exam_interleave_plan.verify_frozen_identity(plan)：两臂 harness 闭包 +
          本批题面/卡/评分器/执行器全文件重算比对（含 2026-10-26 新增"缺失文件即拒"硬闸）；
       B5 两臂 identity_sha256 == 授权冻结值；四臂目录现状文件哈希集 == 授权冻结集（漂移即拒）；
       B6 额度：AuthorizationBudget 整批预检 ensure_batch(attempts)，不足/已关/账本损坏即拒；
       B7 网关 /health 可达（不可达属环境故障，先恢复再发，不拿真实额度试错）；
       B8 实跑证据目录必须尚不存在（拒绝覆盖历史答卷）。
  C. 执行链 = exam_interleave_plan.execute_plan(mode="live")：逐 slot 交错、串行、
     fail-fast、不重试、不补配对；流式共同身份 LB_STREAM_TIMING=1 / LB_STREAM_USAGE=1，
     数字不与任何非流式历史批合并。
  D. 回收后立刻接 reports_from_batch（EXAM_INDEX + exam_layered_score 分层 + 逐位配对）写 _reports，
     并落 RESULT.json / DONE.json；无批注时语义槽如实 unrated（绝不冒充 pass）。

用法：
  D:\\anaconda\\python.exe tools\\run_c4_batch.py sweep_a_orig_vs_goal --precheck   # 只跑 B1–B8，零真实调用
  D:\\anaconda\\python.exe tools\\run_c4_batch.py sweep_a_orig_vs_goal             # 实跑
  D:\\anaconda\\python.exe tools\\run_c4_batch.py sweep_b_v5_vs_mem
"""
import hashlib
import json
import os
import sys
import traceback
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep                                   # noqa: E402
from isolation.authorization_budget import AuthorizationBudget      # noqa: E402

C4 = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "c4_screen_20261026")
AUTH_FILE = os.path.join(C4, "AUTHORIZATION_V2.json")
ARMS = ("reference_product_v1", "v5_purpose_aligned", "goal_action_v1", "evidence_memory_v1")
HEALTH = "http://127.0.0.1:17770/health"


def sha_file(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def sha_dir(d):
    out = {}
    for root, _ds, files in os.walk(d):
        for f in files:
            if f.endswith(".pyc"):
                continue
            p = os.path.join(root, f)
            rel = "candidates/%s/%s" % (os.path.basename(d),
                                        os.path.relpath(p, d).replace("\\", "/"))
            out[rel] = sha_file(p)
    return out


def gateway_ok():
    try:
        with urllib.request.urlopen(HEALTH, timeout=5) as r:
            return json.loads(r.read().decode("utf-8", "replace")).get("ok") is True
    except Exception:                                               # noqa: BLE001
        return False


def main(key, precheck=False):
    checks = []
    auth = json.load(open(AUTH_FILE, encoding="utf-8"))
    assert auth.get("authorized") is True and auth.get("status") == "ACTIVE", \
        "授权 V2 非 authorized/ACTIVE——零派发"
    assert key in auth["scope"]["batches"], \
        "批次 %s 不在授权 scope.batches(%s)" % (key, auth["scope"]["batches"])
    binding = auth["frozen_bindings"][key]
    checks.append("B1 authorized/status=%s，批次在 scope" % auth["status"])

    plan_path = os.path.join(ROOT, binding["plan_file"].replace("/", os.sep))
    assert os.path.isfile(plan_path), "冻结计划缺失：%s" % plan_path
    now_sha = sha_file(plan_path)
    assert now_sha == binding["plan_sha256"], \
        "计划漂移：授权冻结 %s… 实为 %s…——零派发" % (binding["plan_sha256"][:16], now_sha[:16])
    checks.append("B2 计划 sha256==冻结值 %s…" % now_sha[:16])

    plan = json.load(open(plan_path, encoding="utf-8"))
    assert plan["total_attempts"] == binding["attempts"], \
        "计划次数 %s != 授权登记 %s" % (plan["total_attempts"], binding["attempts"])
    assert plan["batch"]["batch_id"] == binding["batch_id"], "batch_id 与授权登记不符"
    assert plan["arm_mapping"]["parent"] == binding["parent"] \
        and plan["arm_mapping"]["candidate"] == binding["candidate"], "两臂配对与授权不符"
    assert plan["batch"]["cards_path"] == binding["cards_file"], "卡路径与授权登记不符"
    checks.append("B3 attempts=%d batch=%s 臂=%s/%s 卡=%s" % (
        plan["total_attempts"], plan["batch"]["batch_id"],
        plan["arm_mapping"]["parent"], plan["arm_mapping"]["candidate"],
        os.path.basename(binding["cards_file"])))

    ep.verify_frozen_identity(plan)                                  # B4（含缺失硬闸）
    checks.append("B4 harness 闭包 + 题面/卡/评分器/执行器全文件重算一致（无 null 冻结）")

    fz = plan["frozen"]["identity_closure"]
    for arm in ("parent", "candidate"):
        assert fz[arm]["identity_sha256"] == binding["%s_identity_sha256" % arm], \
            "%s 臂身份不符：计划 %s… vs 授权 %s…" % (
                arm, fz[arm]["identity_sha256"][:16],
                binding["%s_identity_sha256" % arm][:16])
    for cand in ARMS:
        frozen = set(auth["arm_identities_frozen"][cand])
        now = set(sha_dir(os.path.join(ROOT, "candidates", cand)).values())
        assert now == frozen, "%s 臂目录漂移(冻结%d文件, 现状%d)——零派发" % (
            cand, len(frozen), len(now))
    checks.append("B5 两臂身份==授权；四臂目录哈希集==授权 %s"
                  % {c: len(auth["arm_identities_frozen"][c]) for c in ARMS})

    ledger = auth["budget_ledger"]
    if not os.path.isabs(ledger):
        ledger = os.path.join(ROOT, ledger)
    pre = AuthorizationBudget(ledger, auth["authorization_id"],
                              auth["real_endpoint"]["max_attempts"],
                              status=auth.get("status"), scope=auth.get("scope"))
    pre.ensure_batch(plan["total_attempts"], purpose="full_batch:live:%s" % key)
    s = pre.summary()
    assert s["remaining"] >= plan["total_attempts"] and not s["closed"], "额度不足或已关"
    checks.append("B6 额度预检 cap=%s used=%s 余=%s 需=%s"
                  % (s["max_attempts"], s["used"], s["remaining"], plan["total_attempts"]))

    assert gateway_ok(), "网关 %s 不可达——先按标准入口恢复，不拿真实额度试错" % HEALTH
    checks.append("B7 网关 health ok")

    live = os.path.join(ROOT, "logs", "c4_%s_live_20261026" % key)
    assert not os.path.isdir(live), "实跑目录已存在，拒绝覆盖：%s" % live
    checks.append("B8 证据目录新起 %s" % os.path.basename(live))

    os.environ["LB_STREAM_TIMING"] = "1"
    os.environ["LB_STREAM_USAGE"] = "1"
    print("C4 派发前校验全部通过：")
    for c in checks:
        print("  [OK]", c)
    if precheck:
        print("PRECHECK ONLY：零真实调用（未构造任何 IsolatedRun，未占额度）。")
        return
    print("→ 真实调用开始 batch=%s attempts=%d ledger=%s model=%s temp=%s stream=on(+usage)"
          % (plan["batch"]["batch_id"], plan["total_attempts"],
             os.path.relpath(ledger, ROOT).replace("\\", "/"),
             auth["real_endpoint"]["model"], auth["real_endpoint"]["temperature"]))
    sys.stdout.flush()

    res = ep.execute_plan(plan, mode="live", authorization=auth, out_base=live)
    json.dump(res, open(live + ".RESULT.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1, default=str)
    rep = ep.reports_from_batch(res, plan, live, out_dir=os.path.join(live, "_reports"))
    json.dump(rep, open(live + ".REPORTS.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1, default=str)
    done = {
        "batch_id": rep["batch_id"],
        "authorization_id": rep.get("authorization_id"),
        "arm_mapping": rep["arm_mapping"],
        "cards_file": plan["batch"]["cards_path"],
        "plan_sha256": now_sha,
        "arm_identity_sha256": {a: fz[a]["identity_sha256"] for a in ("parent", "candidate")},
        "mode": rep["mode"],
        "started": res.get("attempt_started"), "completed": res.get("attempt_completed"),
        "failed": res.get("attempt_failed"), "stopped": res.get("stopped"),
        "order_verified": res.get("execution_order_verified"),
        "dispatch_bijection": res.get("dispatch_bijection"),
        "residents": res.get("residents"),
        "state_isolated_note": ("execute_plan 的 state_isolated 键沿用旧 6-resident 判定式，"
                                "4 故事批恒 false；真实隔离=8 个独立 resident 各自 _iso_run。"
                                "此判据与 96 批一致，本轮不改（改判据=尺子改动，只登记不生效）。"),
        "real_model_calls": res.get("real_model_calls"),
        "turns_parent": rep["turns_parent"], "turns_candidate": rep["turns_candidate"],
        "cost_parent": rep["parent"]["cost"], "cost_candidate": rep["candidate"]["cost"],
        "program_mean10": {"parent": rep["parent"]["program_mean10"],
                           "candidate": rep["candidate"]["program_mean10"]},
        "paired_program_winner_counts": rep["paired_program_winner_counts"],
        "all_semantic_unrated_without_annotations":
            rep["all_semantic_unrated_without_annotations"],
        "stream_identity": "api_cfg:openai_compat_stream_true_v1(include_usage gated)",
        "reports_dir": rep.get("out_dir"),
    }
    json.dump(done, open(live + ".DONE.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("C4 LIVE done:", json.dumps(done, ensure_ascii=False)[:900])


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    precheck = "--precheck" in sys.argv
    k = args[0] if args else ""
    assert k in ("sweep_a_orig_vs_goal", "sweep_b_v5_vs_mem"), \
        "usage: run_c4_batch.py sweep_a_orig_vs_goal|sweep_b_v5_vs_mem [--precheck]"
    try:
        main(k, precheck=precheck)
    except AssertionError as e:
        print("C4 零派发拒绝：校验未过 → %s" % e)
        sys.exit(2)
    except Exception as e:                                            # noqa: BLE001
        print("C4 LIVE ERROR: %s: %s" % (type(e).__name__, e))
        print(traceback.format_exc()[-1500:])
        sys.exit(1)
