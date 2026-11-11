# -*- coding: utf-8 -*-
"""l3b_three_arm_executor.py — L3B 三臂 66 槽执行器 v2（v10 复核执行令：fail-fast 收口）。

同路径原则不变：每个槽位经 lb_runner.run_episode（真实 runner/gateway/checkpoint/
sandbox 路径）执行恰好一轮。dry-run 用 adapter=mock，零真实调用。

v2 与 v1 的差异（v10 独立复核 §3/§4/§5，全量采纳）：
1. **失败即终止整批（fail-fast）**：传输失败 / 网关内部失败 / 配对校验不符 /
   注入断言不符 → 槽 failed 留证 + 立即停批；**禁用"作废失败槽后继续"**——
   --resolve-failed 已移除（传入即报 RECOVERY_DISABLED）；failed 或 occupied
   未决的批次重入一律阻断。不自动新建批次、不隐藏补跑。
2. **checkpoint 前驱断言（生成前）**：turn1>1 时 out_dir 的 checkpoint.json
   必须存在且 turns_done 恰为 turn1-1（缺失/过旧/超前均拒绝，不占槽不生成）；
   turn1==1 时不得有残留 checkpoint。杜绝"checkpoint 停在 t2 却请求 t4 →
   顺带重跑 t3"的隐藏重跑。
3. **额度权威 = 真实网关事件**：批内真实 attempt_started 计数先于生成检查；
   达硬上限后、下一次（第 CAP+1 次）真实请求发出前拒绝。每槽事后用
   (run_id, case_id, attempt_id) 对
   真实 started/终态配对：恰 1 次 started、case 必须等于目标 case、有且仅有
   一个终态、无网关内部失败事件、manifest.audit_errors 为空——任一不符
   即槽 failed 停批。槽状态汇总与真实调用计数在 summary 并列。
4. **汇总分类修正（复核 §4.1）**：解析是否成功独立判断；blank / 非 JSON /
   schema 破损 / 未知字段 / 越权不回 / 动作违规 / scorer 崩溃 / 合法不回
   分列记账——前三类与越权不回一律不算合法收尾。
5. **实际配置锁定（复核 §4.2）**：batch_config 从适配器真实配置源建立
   （LB_API_BASE / LB_API_KEY 是否设置 / LB_MODEL / temperature 实际出站值
   0.7 归一口径）；再次进入批次逐项比对，adapter/端点/模型/温度漂移拒绝；
   修复 slots.config setdefault 不生效（账面空配置）的 bug；真实适配器缺
   端点在首调用前拒绝。params×3 / expr_audit / 三臂方案 版本散列入冻结清单；
   执行器自身散列动态记录入 batch_config。
6. **演练注入改为网关内部失败**（复核 §5 反例矩阵）：--chaos-attempt N 在
   第 N 次 generate 内部（adapter 层）抛 TimeoutError——不再在调用前注入。
   失败产生真实 attempt_failed 事件 → 槽 failed → 停批；再次启动仍阻断。

用法：
  python tools/l3b_three_arm_executor.py --batch-id dryrun_x --adapter mock
  python tools/l3b_three_arm_executor.py --batch-id drill_x --adapter mock \
      --chaos-attempt 13        # 演练：第 13 次网关调用内部超时 → 停批（不可恢复）
  python tools/l3b_three_arm_executor.py --batch-id dryrun_x --adapter mock \
      --selftest-guards         # 反例演练（不跑生成）
  python tools/l3b_three_arm_executor.py --batch-id dryrun_x --summary-only
"""
import argparse
import datetime
import hashlib
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
os.chdir(ROOT)

import lb_runner          # noqa: E402
import lb_scorer          # noqa: E402
from lb_gateway import ADAPTERS, MockAdapter   # noqa: E402

CAP = 66
REPEATS = ("r1", "r2")
# v12 复核 §6-D 参数化改造（2026-10-21）：以下常量原为字面量内嵌，现提为模块级
# 可配置项。**默认值 = 方案 v3 冻结配置**——核心请求/配对/停批逻辑复用（口径见
# v13 复核 §7：参数化后不再称"逐字节等价"；52 槽矩阵与独立演练通过，旧 66 执行
# 环境已封存）。52 槽父子对照由 tools/l3b_parent_child_executor.py 导入本模块并
# 覆盖这些常量实现（复用已验证的 runner/配对/停批守卫，不重写恢复路径）。
BATCH_PREFIX = "l3b_66"          # 批次账目录前缀（run_id 同源）
EXPR_ARM = "A3"                  # 表达块期望=1 的臂
DEP_NOTE_KEY = "candidates/phase1_retr_idf/mechanism/mechanism.py"  # 槽级依赖注记键
DEP_NOTE_ARMS = (EXPR_ARM,)      # 需要槽级依赖注记的臂（检索 base 的直接加载方）
EXPR_WANT = {"A1": 0, "A2": 0, "A3": 1}   # 每臂表达块期望份数
FREEZE_FILES = (                 # freeze_deps 归档清单
    "candidates/phase1_retr_idf/mechanism/mechanism.py",
    "candidates/phase2_expr/mechanism/mechanism.py",
    "candidates/phase2_expr/assets/system_prompt/expression.md",
)
ARMS = {
    "A1": "candidates/phase0_beta_single",
    "A2": "candidates/phase1_retr_idf",
    "A3": "candidates/phase2_expr",
}
EPISODES = {
    "NCA_CORR_INTENT_v1": "benchmark/episodes/NCA_CORR_INTENT_v1.json",
    "NCB_ENTITY_DENSE_v1": "benchmark/episodes/NCB_ENTITY_DENSE_v1.json",
}
ROTATIONS = [("A1", "A2", "A3"), ("A2", "A3", "A1"), ("A3", "A1", "A2")]
EXPR_MARK = "表达与选择策略"

# 方案 v2（refs/L3B_THREE_ARM_PLAN_20261020.md）冻结散列——v2 补齐实际输入
# 依赖（v10 复核 §4.2：不能只锁十个文件而遗漏 params/审计器/方案本身）。
FROZEN_SHA = {
    "candidates/phase0_beta_single/mechanism/mechanism.py":
        "fc8fdd68abffbc28f70ab85d3069b174e5fd705f2b83620c590383089ee849c3",
    "candidates/phase1_retr_idf/mechanism/mechanism.py":
        "50c095eb7ee021b24831f741d56ea7cad5e30466a11f25217b476ecee49f8727",
    "candidates/phase2_expr/mechanism/mechanism.py":
        "31a1dd72e355257b75b12bb7f5d74cf104abea6084ba904972d2b1908dfd482a",
    "candidates/phase2_expr/assets/system_prompt/expression.md":
        "707128023da1def0177210d120986ef67a3094ee74b40646a7c2399c4214c367",
    "candidates/phase0_beta_single/params.json":
        "e215b71811aee85d7051514c2ff13fdfc12323fbaf8aee75cdc987ebfe0406dc",
    "candidates/phase1_retr_idf/params.json":
        "e215b71811aee85d7051514c2ff13fdfc12323fbaf8aee75cdc987ebfe0406dc",
    "candidates/phase2_expr/params.json":
        "e215b71811aee85d7051514c2ff13fdfc12323fbaf8aee75cdc987ebfe0406dc",
    "arena/lb_scorer.py":
        "678c4e191b20394da444809814ff9f7665d23cb83d03c997543fb89272f7e959",
    "arena/lb_assembly.py":
        "a671c23b529bf3f9d25991c04aa920fa533d43b7164b18db1cf980d877096e1b",
    "arena/lb_runner.py":
        "89568033f74124a032e388d7bee0df2635b2ca97c2031a79b5ae02377cf137de",
    "arena/lb_gateway.py":
        "b0587e6ad137898e6facbed469bf42687f58dc6c1b0bdb9a4c5a6897669f8a35",
    "tools/expr_audit.py":
        "61486cd6a38c763705651139429349c2a24772c5b9ff2d8328537a4fd7a98dcd",
    "benchmark/episodes/NCA_CORR_INTENT_v1.json":
        "018826ba3f7025edd8139cb7b02267d38e096589ecf5f6c9dbe4e43778470e57",
    "benchmark/episodes/NCB_ENTITY_DENSE_v1.json":
        "8fbb29b5fec8f6ab61a0e89d8fe9e121024d482aeb4cda9f125f7b5c6afb62f7",
    "refs/L3B_THREE_ARM_PLAN_20261020.md":
        "d357c9bfb78b34fd6c6f17a5a6e160a4485b41a7fb4577992467bd3c4974145e",
}


def _sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def _now():
    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def check_frozen():
    bad = []
    for rel, want in FROZEN_SHA.items():
        p = os.path.join(ROOT, rel)
        if not os.path.isfile(p):
            bad.append("%s 缺失" % rel)
        elif _sha(p) != want:
            bad.append("%s 散列不符（方案冻结≠工作区）" % rel)
    if bad:
        raise SystemExit("FROZEN_MISMATCH（执行前检查，即停）:\n  "
                         + "\n  ".join(bad))


def batch_dir(batch_id):
    d = os.path.join(ROOT, "logs", "%s_%s" % (BATCH_PREFIX, batch_id))
    os.makedirs(d, exist_ok=True)
    return d


def load_slots(bd):
    p = os.path.join(bd, "slots.json")
    if os.path.isfile(p):
        return json.load(open(p, encoding="utf-8"))
    return {"batch_id": os.path.basename(bd), "config": {}, "slots": [],
            "events": []}


def save_slots(bd, slots):
    with open(os.path.join(bd, "slots.json"), "w", encoding="utf-8") as f:
        json.dump(slots, f, ensure_ascii=False, indent=1)


def _log(slots, kind, detail):
    slots["events"].append({"kind": kind, "ts": _now(), "detail": detail})


def _consumed(slots):
    return len(slots["slots"])   # 每条登记即占额度（occupied/success/failed）


def _slot_key(arm, ep, repeat, turn1):
    return "%s|%s|%s|t%d" % (arm, ep, repeat, turn1)


def _find_slot(slots, key):
    for s in slots["slots"]:
        if s["slot_key"] == key:
            return s
    return None


def freeze_deps(bd):
    """机制/表达资产快照归档（整批期间冻结依赖；清单=FREEZE_FILES 常量）。"""
    fz = os.path.join(batch_dir(bd), "_frozen")
    os.makedirs(fz, exist_ok=True)
    out = {}
    for rel in FREEZE_FILES:
        dst = os.path.join(fz, rel.replace("/", "__"))
        if not os.path.isfile(dst):
            shutil.copy(os.path.join(ROOT, rel), dst)
        out[rel] = _sha(dst)
    return out


# ---------- 实际配置（v10 复核 §4.2：从适配器真实配置源建立，逐项锁定） ----------

def _effective_config(adapter):
    if adapter == "mock":
        return {"adapter": adapter,
                "model_env": os.environ.get("LB_MODEL", "") or None,
                "model_effective": "mock",
                "endpoint_env_name": "LB_API_BASE",
                "endpoint_base": None,
                "key_set": False,
                "temperature_effective": None,
                "temperature_note": "mock 不消费温度",
                "timeout_s_default": 120}
    # openai_compat：与 lb_gateway.OpenAICompatAdapter 实际读取的配置源一致
    return {"adapter": adapter,
            "model_env": os.environ.get("LB_MODEL", "") or None,
            "model_effective": os.environ.get("LB_MODEL") or "gpt-oss-120b",
            "endpoint_env_name": "LB_API_BASE",
            "endpoint_base": (os.environ.get("LB_API_BASE", "").rstrip("/") or None),
            "key_set": bool(os.environ.get("LB_API_KEY")),
            "temperature_effective": 0.7,
            "temperature_note": "适配器归一化缺省 0.7（lb_gateway 出站实际值）",
            "timeout_s_default": 120}


def init_config(bd, adapter, chaos_attempt=None):
    bd_path = batch_dir(bd)
    cfg_path = os.path.join(bd_path, "batch_config.json")
    cur = _effective_config(adapter)
    cur["chaos_attempt"] = chaos_attempt
    if os.path.isfile(cfg_path):
        old = json.load(open(cfg_path, encoding="utf-8"))
        diffs = []
        for k in ("adapter", "model_effective", "endpoint_base", "key_set",
                  "temperature_effective", "chaos_attempt"):
            if old.get(k) != cur.get(k):
                diffs.append("%s: %r → %r" % (k, old.get(k), cur.get(k)))
        if diffs:
            raise SystemExit("CONFIG_MISMATCH（批次配置锁定：禁止 mock/真实混用"
                             "或端点/模型/温度漂移；如需新配置请新建批次）:\n  "
                             + "\n  ".join(diffs))
        return old
    if adapter == "openai_compat" and not cur.get("endpoint_base"):
        raise SystemExit("CONFIG_MISSING: LB_API_BASE 未设置"
                         "（真实适配器缺端点，首调用前拒绝）")
    cfg = dict(cur)
    cfg.update({
        "cap": CAP,
        "retry_policy": "none（无重试额度；失败/未决即整批终止，恢复路径已禁用）",
        "failure_policy": "fail-fast（v10 复核 §5：失败或 occupied 即终止整批；"
                          "不作废失败槽继续；不自动新建批次重跑）",
        "frozen_sha": dict(FROZEN_SHA),
        "executor_sha256": _sha(os.path.abspath(__file__)),
        "ts": _now(),
    })
    with open(cfg_path, "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=1)
    return cfg


# ---------- 真实网关事件（额度/配对权威） ----------

def _gateway_event_counts(bd_path):
    """批内真实网关事件计数（attempt_started = 真实调用次数权威）。"""
    started = completed = failed = 0
    runs = os.path.join(bd_path, "runs")
    if os.path.isdir(runs):
        for root, _d, fs in os.walk(runs):
            if "gateway_events.jsonl" in fs:
                with open(os.path.join(root, "gateway_events.jsonl"),
                          encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if not line:
                            continue
                        k = json.loads(line).get("kind")
                        if k == "attempt_started":
                            started += 1
                        elif k == "attempt_completed":
                            completed += 1
                        elif k == "attempt_failed":
                            failed += 1
    return started, completed, failed


_EP_CASES = {}


def _ep_case_id(ep, turn1):
    if ep not in _EP_CASES:
        data = json.load(open(os.path.join(ROOT, EPISODES[ep]), encoding="utf-8"))
        _EP_CASES[ep] = [t.get("case_id") for t in data["turns"]]
    return _EP_CASES[ep][turn1 - 1]


def _verify_slot_attempts(run_dir, run_id, case_id):
    """Require one ordered, identity-matched start/completion for this slot."""
    res = {"ok": True, "errors": [], "started": 0, "completed": 0,
           "failed": 0, "attempt_ids": [], "cases_seen": []}

    def error(message):
        res["errors"].append(message)

    path = os.path.join(run_dir, "gateway_events.jsonl")
    if not os.path.isfile(path):
        error("gateway_events.jsonl missing")
        res["ok"] = False
        return res
    events = []
    try:
        with open(path, encoding="utf-8") as source:
            for line_no, line in enumerate(source, 1):
                if not line.strip():
                    continue
                ev = json.loads(line)
                if not isinstance(ev, dict):
                    error("event line %d is not an object" % line_no)
                    continue
                if ev.get("run_id") == run_id and ev.get("kind") in (
                        "attempt_started", "attempt_completed", "attempt_failed"):
                    events.append(ev)
    except (OSError, ValueError) as exc:
        error("event log unreadable: %s" % type(exc).__name__)

    started_key = None
    terminal_seen = False
    for ev in events:
        kind = ev["kind"]
        aid = ev.get("attempt_id")
        valid_id = (isinstance(aid, (str, int)) and not isinstance(aid, bool)
                    and (not isinstance(aid, str) or bool(aid.strip())))
        key = (run_id, ev.get("case_id"), type(aid).__name__, str(aid))
        if not valid_id:
            error("missing or invalid attempt_id")
        if ev.get("case_id") != case_id:
            error("event case_id does not match target case")
        if kind == "attempt_started":
            res["started"] += 1
            res["attempt_ids"].append(aid)
            res["cases_seen"].append(ev.get("case_id"))
            if started_key is not None:
                error("duplicate start")
            else:
                started_key = key
        else:
            res["completed" if kind == "attempt_completed" else "failed"] += 1
            if started_key is None:
                error("terminal without preceding start")
            elif key != started_key:
                error("terminal identity does not match start")
            if terminal_seen:
                error("duplicate terminal")
            terminal_seen = True

    if res["started"] != 1:
        error("expected exactly one started event")
    if res["completed"] + res["failed"] != 1:
        error("expected exactly one terminal event")
    if res["failed"]:
        error("gateway failure: stop the batch")
    res["ok"] = not res["errors"]
    return res


def _verify_turn_evidence(out_dir, turn1, case_id, run_id):
    """目标轮 manifest 证据：run_id 归属 / case 一致 / 恰 1 次网关调用 / 审计无错。"""
    td = os.path.join(out_dir, "turn_%02d_%s" % (turn1, case_id))
    mp = os.path.join(td, "manifest.json")
    if not os.path.isfile(mp):
        return {"ok": False, "errors": ["manifest.json 缺失（%s）" % td]}
    m = json.load(open(mp, encoding="utf-8"))
    errs = []
    if m.get("run_id") != run_id:
        errs.append("manifest.run_id=%r ≠ 槽 run_id" % m.get("run_id"))
    if m.get("case_id") != case_id:
        errs.append("manifest.case_id=%r ≠ 目标 %r" % (m.get("case_id"), case_id))
    if m.get("gateway_calls") != 1:
        errs.append("gateway_calls=%r ≠ 1" % m.get("gateway_calls"))
    if m.get("audit_errors"):
        errs.append("audit_errors=%r" % (m.get("audit_errors"),))
    return {"ok": not errs, "errors": errs}


# ---------- 演练用 chaos 适配器（v10 复核 §5：网关内部失败，非调用前注入） ----------

class ChaosMockAdapter(MockAdapter):
    """第 N 次全局 generate 在适配器内部抛 TimeoutError（真实 attempt_failed 事件）。"""
    name = "mock_chaos"
    _n = 0              # 类级计数：跨 run_episode 实例累计
    fail_at = None      # 由 --chaos-attempt 设置

    def generate(self, system, user, payload):
        ChaosMockAdapter._n += 1
        if ChaosMockAdapter._n == ChaosMockAdapter.fail_at:
            raise TimeoutError("chaos: 网关内部注入超时（第 %d 次真实调用）"
                               % ChaosMockAdapter._n)
        return MockAdapter.generate(self, system, user, payload)


def slot_out_dir(bd, arm, ep, repeat):
    d = os.path.join(batch_dir(bd), "runs", arm, ep, repeat)
    os.makedirs(d, exist_ok=True)
    return d


def expr_block_counts(out_dir, turn1, case_id):
    """从该轮 calls.json 逐 call 统计 system 中表达块数量（空列表=异常，调用方拒绝）。"""
    cj = os.path.join(out_dir, "turn_%02d_%s" % (turn1, case_id), "calls.json")
    if not os.path.isfile(cj):
        return None
    calls = json.load(open(cj, encoding="utf-8"))
    return [str(c.get("system", "")).count(EXPR_MARK) for c in calls]


def _fail_slot(bd_path, slots, rec, fail_kind, detail, out_dir=None):
    """槽 failed 留证 + 停批（fail-fast：无任何继续路径）。"""
    rec["status"] = "failed"
    rec["fail_kind"] = fail_kind
    rec["fail_detail"] = detail
    rec["ts_terminal"] = _now()
    if out_dir is not None:
        try:
            with open(os.path.join(out_dir, "error.json"), "w",
                      encoding="utf-8") as f:
                json.dump({"slot_key": rec["slot_key"], "fail_kind": fail_kind,
                           "detail": detail, "ts": rec["ts_terminal"]},
                          f, ensure_ascii=False, indent=1)
        except OSError:
            pass
    _log(slots, "slot_failed_stop_batch",
         "%s -> %s: %s" % (rec["slot_key"], fail_kind, detail))
    save_slots(bd_path, slots)
    raise SystemExit("SLOT_FAILED_STOP_BATCH: 槽 %d（%s）%s。"
                     "fail-fast：整批终止，不作废失败槽继续、不自动重跑。"
                     % (rec["slot_no"], rec["slot_key"], detail))


def run_slot(bd, adapter_name, arm, ep, repeat, turn1, chaos_attempt=None):
    """守卫（额度=真实事件/重复/checkpoint 前驱）→ 占槽 → 真实 runner 一轮 → 配对校验。"""
    bd_path = batch_dir(bd)
    slots = load_slots(bd_path)
    key = _slot_key(arm, ep, repeat, turn1)
    real_started, _, _ = _gateway_event_counts(bd_path)

    # ---- 守卫（全部在生成之前；拒绝不占槽不耗额度） ----
    if real_started >= CAP or _consumed(slots) >= CAP:
        _log(slots, "refuse_cap",
             "拒绝：真实 started=%d / 槽登记=%d 达硬上限 %d（第 %d 次请求发出前拒绝）"
             % (real_started, _consumed(slots), CAP, CAP + 1))
        save_slots(bd_path, slots)
        raise SystemExit("CAP_EXCEEDED: 真实调用计数 %d / 槽登记 %d 达硬上限 %d，"
                         "第 %d 次真实请求发出前拒绝" % (real_started,
                                                        _consumed(slots), CAP, CAP + 1))
    if _find_slot(slots, key) is not None:
        _log(slots, "refuse_duplicate", key)
        save_slots(bd_path, slots)
        raise SystemExit("DUPLICATE_SLOT: %s 已登记，禁止重复执行" % key)
    if arm not in ARMS:
        _log(slots, "refuse_arm", key)
        save_slots(bd_path, slots)
        raise SystemExit("UNKNOWN_ARM: %s" % arm)

    # ---- checkpoint 前驱断言（v10 复核 §5-2：缺失/过旧/超前生成前拒绝） ----
    out_dir = slot_out_dir(bd, arm, ep, repeat)
    case_id = _ep_case_id(ep, turn1)
    ck_path = os.path.join(out_dir, "checkpoint.json")
    if turn1 == 1:
        if os.path.isfile(ck_path):
            raise SystemExit("CHECKPOINT_MISMATCH: %s 请求 t1 但 %s 已存在"
                             "（残留/复用，生成前拒绝）" % (key, ck_path))
    else:
        if not os.path.isfile(ck_path):
            raise SystemExit("CHECKPOINT_MISMATCH: %s 缺 checkpoint.json"
                             "（前驱未完成，生成前拒绝）" % key)
        ck = json.load(open(ck_path, encoding="utf-8"))
        done = int(ck.get("turns_done", -1))
        if done != turn1 - 1:
            raise SystemExit("CHECKPOINT_MISMATCH: %s checkpoint.turns_done=%d "
                             "≠ 前一轮 %d（缺失/过旧/超前，生成前拒绝——"
                             "不得顺带重跑历史轮）" % (key, done, turn1 - 1))

    # ---- 占槽（先占后耗） ----
    rec = {"slot_no": len(slots["slots"]) + 1, "slot_key": key,
           "arm": arm, "episode": ep, "repeat": repeat, "turn1": turn1,
           "status": "occupied", "adapter": adapter_name,
           "run_id": "%s_%s_%s_%s_%s_t%d" % (BATCH_PREFIX, bd, arm, ep,
                                             repeat, turn1),
           "out_dir": os.path.relpath(out_dir, ROOT),
           "real_started_before": real_started,
           "ts_occupied": _now()}
    if DEP_NOTE_KEY in FROZEN_SHA and arm in DEP_NOTE_ARMS:
        rec["dep_mechanism_sha256"] = FROZEN_SHA[DEP_NOTE_KEY]
    slots["slots"].append(rec)
    _log(slots, "slot_occupied", key)
    save_slots(bd_path, slots)

    ep_path = os.path.join(ROOT, EPISODES[ep])
    used_adapter = "mock_chaos" if chaos_attempt else adapter_name

    # ---- 真实 runner 路径：checkpoint 已核对恰为前一轮 → 恰好执行本轮 ----
    transport_err = None
    try:
        lb_runner.run_episode(ep_path, ARMS[arm], out_dir, used_adapter,
                              stop_after=turn1,
                              resume=(turn1 > 1),
                              run_id=rec["run_id"])
    except Exception as e:   # 网关内部失败/传输失败等
        transport_err = "%s: %s" % (type(e).__name__, e)

    # ---- 配对校验（成功与失败路径都做；真实事件为权威） ----
    pairing = _verify_slot_attempts(out_dir, rec["run_id"], case_id)
    rec["attempts"] = {"started": pairing["started"], "completed": pairing["completed"],
                       "failed": pairing["failed"],
                       "attempt_ids": pairing["attempt_ids"],
                       "cases_seen": pairing["cases_seen"],
                       "ok": pairing["ok"], "errors": pairing["errors"]}
    if transport_err is not None:
        _fail_slot(bd_path, slots, rec, "gateway_or_transport_failure",
                   transport_err, out_dir)
    if not pairing["ok"]:
        _fail_slot(bd_path, slots, rec, "attempt_pairing_violation",
                   "; ".join(pairing["errors"]), out_dir)
    ev = _verify_turn_evidence(out_dir, turn1, case_id, rec["run_id"])
    if not ev["ok"]:
        _fail_slot(bd_path, slots, rec, "turn_evidence_violation",
                   "; ".join(ev["errors"]), out_dir)

    # ---- 注入断言（每臂表达块期望份数=EXPR_WANT[arm]；空 calls 拒绝） ----
    counts = expr_block_counts(out_dir, turn1, case_id)
    rec["expr_block_counts"] = counts
    want = EXPR_WANT.get(arm, 0)
    if not counts or any(c != want for c in counts):
        _fail_slot(bd_path, slots, rec, "expr_block_violation",
                   "表达块计数 %s ≠ 期望 [%d]（空列表同样拒绝）" % (counts, want),
                   out_dir)

    rec["status"] = "success"
    rec["ts_terminal"] = _now()
    _log(slots, "slot_terminal", "%s -> success (attempt=%s)"
         % (key, pairing["attempt_ids"]))
    save_slots(bd_path, slots)
    return rec


def blocking_slots(slots):
    """fail-fast：failed（无论是否标注 resolution）或 occupied 未决 → 整批阻断。"""
    return [s for s in slots["slots"]
            if s["status"] == "failed" or s["status"] == "occupied"]


def selftest_guards(bd):
    """反例演练（不跑生成）：超额/重复/未知臂。"""
    bd_path = batch_dir(bd)
    slots = load_slots(bd_path)
    results = {}
    # 重复占槽 / 未知臂（探针批次：1 条已终态槽）
    probe_path = bd_path + "_guard_probe"
    os.makedirs(probe_path, exist_ok=True)
    probe = dict(slots)
    probe["slots"] = [{"slot_no": 1, "slot_key": "A1|NCA_CORR_INTENT_v1|r1|t1",
                       "arm": "A1", "episode": "NCA_CORR_INTENT_v1",
                       "repeat": "r1", "turn1": 1, "status": "success"}]
    save_slots(probe_path, probe)
    try:
        run_slot(bd + "_guard_probe", "mock", "A1", "NCA_CORR_INTENT_v1", "r1", 1)
        results["duplicate_refused"] = False
    except SystemExit as e:
        results["duplicate_refused"] = str(e).startswith("DUPLICATE_SLOT")
    try:
        run_slot(bd + "_guard_probe", "mock", "A9", "NCA_CORR_INTENT_v1", "r1", 1)
        results["unknown_arm_refused"] = False
    except SystemExit as e:
        results["unknown_arm_refused"] = str(e).startswith("UNKNOWN_ARM")
    finally:
        shutil.rmtree(probe_path, ignore_errors=True)
    # 超额（在临时账上模拟 66 已占；真实 started=0 → 由槽登记拦截）
    fake = dict(slots)
    fake["slots"] = [{"slot_no": i, "slot_key": "fake|%d" % i, "status": "success"}
                     for i in range(CAP)]
    cap_probe_path = bd_path + "_cap_probe"
    os.makedirs(cap_probe_path, exist_ok=True)
    save_slots(cap_probe_path, fake)
    try:
        run_slot(bd + "_cap_probe", "mock", "A1", "NCA_CORR_INTENT_v1", "r1", 1)
        results["cap_refused"] = False
    except SystemExit as e:
        results["cap_refused"] = str(e).startswith("CAP_EXCEEDED")
    finally:
        shutil.rmtree(cap_probe_path, ignore_errors=True)
    print(json.dumps({"selftest_guards": results}, ensure_ascii=False, indent=1))
    return results


# ---------- 汇总分类（v10 复核 §4.1：解析独立判断，四类不回分列） ----------

def _classify_reply(raw, scoring):
    """返回 (category, meta)。合法收尾仅=解析合格+无 veto+无相冲突正文。"""
    if not raw.strip():
        return "blank", {}                       # 空白输出：不算合法收尾
    parsed = lb_scorer.extract_json_block(raw)   # 解析成功与否独立判断
    if parsed is None:
        return "parse_fail_not_json", {}         # 非 JSON：不算合法收尾
    veto = scoring.get("veto")
    if veto == "json_schema_broken":
        return "schema_broken", {}               # 坏类型/坏嵌套/格式失败
    if veto == "unknown_payload_field":
        return "unknown_field", {}
    if veto == "unauthorized_no_reply":
        return "unauthorized_no_reply", {}       # 越权不回：不算合法收尾
    if veto in ("invalid_action", "action_conflict", "decline_with_text"):
        return "action_violation", {}
    if isinstance(veto, str) and veto.startswith("scorer_crash"):
        return "scorer_crash", {}
    if veto:
        return "other_veto:" + veto, {}
    resp = parsed.get("response") if isinstance(parsed.get("response"), dict) else {}
    slots_txt = [resp.get(k) for k in ("recommended", "bad_boy", "playful", "warm")]
    dirs = parsed.get("directions") if isinstance(parsed.get("directions"), list) else []
    nonnull = [v for v in slots_txt if isinstance(v, str) and v.strip()]
    nonnull += [v for v in dirs if isinstance(v, str) and v.strip()]
    explicit_no_reply = parsed.get("action") == "no_reply"
    if not nonnull:
        # 无可发送正文且无 veto：显式/隐式不回 → 合法收尾（契约或当轮允许由 scorer 保证）
        return "legal_no_reply", {"explicit_no_reply": explicit_no_reply}
    return "reply", {"n_sendable": len(nonnull),
                     "rec_sendable": isinstance(resp.get("recommended"), str)
                     and bool(resp.get("recommended").strip())}


def summarize(bd):
    """主表多列口径：槽状态汇总与真实调用计数并列（v10 复核 §5-3）。"""
    bd_path = batch_dir(bd)
    slots = load_slots(bd_path)
    cats = ("blank", "parse_fail_not_json", "schema_broken", "unknown_field",
            "unauthorized_no_reply", "action_violation", "scorer_crash",
            "legal_no_reply", "reply")
    per_arm = {a: dict(n=0, success=0, failed=0, rec_sendable=0, any_available=0,
                       deliveries=[], **{c: 0 for c in cats},
                       other_veto=0) for a in ARMS}
    pairing_verified = 0
    for s in slots["slots"]:
        st = per_arm[s["arm"]]
        st["n"] += 1
        if s.get("attempts", {}).get("ok") and s["status"] in ("success", "failed"):
            pairing_verified += 1
        if s["status"] == "success":
            st["success"] += 1
        elif s["status"] == "failed":
            st["failed"] += 1
            continue
        if not s.get("out_dir"):
            continue   # 未决/手工账缺证据路径，只计槽状态
        td = os.path.join(ROOT, s["out_dir"],
                          "turn_%02d_%s" % (s["turn1"], _ep_case_id(s["episode"], s["turn1"])))
        sp = os.path.join(td, "scoring.json")
        rp = os.path.join(td, "reply_raw.txt")
        if not (os.path.isfile(sp) and os.path.isfile(rp)):
            continue
        sc = json.load(open(sp, encoding="utf-8"))
        raw = open(rp, encoding="utf-8").read()
        cat, meta = _classify_reply(raw, sc)
        if cat.startswith("other_veto:"):
            st["other_veto"] += 1
        else:
            st[cat] += 1
        if cat == "reply":
            st["deliveries"].append(meta["n_sendable"])
            if meta["rec_sendable"]:
                st["rec_sendable"] += 1
            st["any_available"] += 1
    real_started, real_completed, real_failed = _gateway_event_counts(bd_path)
    failed_keys = [s["slot_key"] for s in slots["slots"] if s["status"] == "failed"]
    occupied_keys = [s["slot_key"] for s in slots["slots"] if s["status"] == "occupied"]
    out = {"batch_id": slots.get("batch_id"), "cap": CAP,
           "slots_registered": _consumed(slots),
           "terminal_slots": sum(1 for s in slots["slots"]
                                 if s["status"] in ("success", "failed")),
           "real_started": real_started, "real_completed": real_completed,
           "real_failed": real_failed,
           "pairing_verified": pairing_verified,
           "verified_complete": (_consumed(slots) == CAP and real_started == CAP
                                 and real_completed == CAP and real_failed == 0
                                 and pairing_verified == CAP),
           "failed_slots": failed_keys, "occupied_slots": occupied_keys,
           "per_arm": per_arm,
           "rotation_log": [s["slot_key"] for s in slots["slots"]],
           "events_tail": slots.get("events", [])[-20:]}
    dest = os.path.join(bd_path, "summary.json")
    json.dump(out, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--batch-id", required=True)
    ap.add_argument("--adapter", default="mock")
    ap.add_argument("--chaos-attempt", type=int, default=None,
                    help="演练：第 N 次真实网关调用在适配器内部抛 TimeoutError"
                         "（真实 attempt_failed 事件 → 停批，不可恢复）")
    ap.add_argument("--resolve-failed", type=int, default=None,
                    help="已禁用（v10 复核 §5：失败即终止整批，不作废失败槽继续）")
    ap.add_argument("--resolution-note", default="")
    ap.add_argument("--selftest-guards", action="store_true")
    ap.add_argument("--summary-only", action="store_true")
    args = ap.parse_args()

    check_frozen()
    bd = args.batch_id
    if args.summary_only:
        print(json.dumps(summarize(bd), ensure_ascii=False, indent=1))
        return
    if args.resolve_failed is not None:
        raise SystemExit("RECOVERY_DISABLED: 作废失败槽继续的路径已禁用"
                         "（v10 复核 §5：失败即终止整批；失败批次不可恢复，"
                         "如需继续请显式新建批次）")

    bd_path = batch_dir(bd)
    slots = load_slots(bd_path)
    cfg = init_config(bd, args.adapter, chaos_attempt=args.chaos_attempt)
    if not slots.get("config"):          # 修复 v1 setdefault 不生效（账面空配置）
        slots["config"] = cfg
        _log(slots, "config_locked",
             "adapter=%s model=%s endpoint_base=%s temperature=%s"
             % (cfg["adapter"], cfg["model_effective"], cfg["endpoint_base"],
                cfg["temperature_effective"]))
        save_slots(bd_path, slots)
    fz = freeze_deps(bd)
    if not slots.get("frozen_snapshot_sha"):
        slots["frozen_snapshot_sha"] = fz
        save_slots(bd_path, slots)

    # ---- fail-fast 阻断：failed（含已"作废"）或 occupied 未决 → 整批终止 ----
    blk = blocking_slots(slots)
    if blk:
        failed = [s["slot_key"] for s in blk if s["status"] == "failed"]
        occupied = [s["slot_key"] for s in blk if s["status"] == "occupied"]
        summarize(bd)
        raise SystemExit("BATCH_ABORTED_TERMINAL: 失败/未决槽存在，整批不可继续"
                         "（fail-fast；不跳过、不猜测、不自动重发）"
                         " failed=%s occupied=%s" % (failed, occupied))

    done_keys = {s["slot_key"] for s in slots["slots"]}
    n_total = sum(EP_TURNS[e] * len(EP_REPEATS.get(e, REPEATS)) * len(ARMS)
                  for e in EPISODES)
    assert n_total == CAP, "槽位算术 %d ≠ %d" % (n_total, CAP)

    chaos_attempt = args.chaos_attempt
    if chaos_attempt is not None:
        ADAPTERS["mock_chaos"] = ChaosMockAdapter
        ChaosMockAdapter.fail_at = chaos_attempt
        ChaosMockAdapter._n = 0

    group_i = 0
    for ep in EPISODES:
        n_turns = EP_TURNS[ep]
        for t1 in range(1, n_turns + 1):
            for rep in EP_REPEATS.get(ep, REPEATS):
                order = ROTATIONS[group_i % len(ROTATIONS)]
                group_i += 1
                for arm in order:
                    key = _slot_key(arm, ep, rep, t1)
                    if key in done_keys:
                        continue   # 已终态槽跳过（仅限成功完成的历史；failed/occupied 已阻断）
                    try:
                        rec = run_slot(bd, args.adapter, arm, ep, rep, t1,
                                       chaos_attempt=chaos_attempt)
                    except SystemExit as e:
                        if str(e).startswith("SLOT_FAILED_STOP_BATCH"):
                            summarize(bd)   # 停批前落盘汇总（真实事件计数并列）
                        raise
                    if rec["status"] == "failed":   # 双保险（run_slot 已 raise）
                        summarize(bd)
                        raise SystemExit(2)
    summary = summarize(bd)
    print(json.dumps({"batch_id": bd,
                      "slots_registered": summary["slots_registered"],
                      "real_started": summary["real_started"],
                      "real_completed": summary["real_completed"],
                      "real_failed": summary["real_failed"],
                      "pairing_verified": summary["pairing_verified"],
                      "verified_complete": summary["verified_complete"],
                      "per_arm": summary["per_arm"]},
                     ensure_ascii=False, indent=1))


# episode 轮数（与题面 turns 一致；check_frozen 已锚定题面散列）
EP_TURNS = {"NCA_CORR_INTENT_v1": 6, "NCB_ENTITY_DENSE_v1": 5}
# 每题材重复组（默认 r1/r2；开发性单轮反例可配 {"r1"}——v12 §6-D 8 槽）
EP_REPEATS = {}


if __name__ == "__main__":
    main()
