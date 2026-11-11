# -*- coding: utf-8 -*-
"""tools/test_freeze_missing_guard.py — 冻结缺失硬闸回归测试（零网络，真实模型调用 0）。

要钉住的三件事（2026-10-26 C4 派发前核查暴露的作废级漏洞）：
  G1 评分卡/题面路径写错 → emit_plan 必须拒绝出计划，绝不写 null 哈希冒充已冻结；
  G2 已存在的旧计划若声明某配置为 null（当年静默放行）→ verify_frozen_identity 必须拒；
  G3 计划声明了哈希但文件当下缺失 → 同样必须拒（不许"看不见就当没发生"）；
  G4 正向对照：正确卡集的 C4 批配置能出计划、能过校验（证明闸不误杀）。
"""
import copy
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import exam_interleave_plan as ep                                    # noqa: E402

C4 = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "c4_screen_20261026")
GOOD_CFG = os.path.join(C4, "sweep_a_orig_vs_goal.BATCH_CONFIG.json")
if not os.path.isfile(GOOD_CFG):                    # 允许测试在重排前用旧名跑对照
    GOOD_CFG = os.path.join(C4, "b2_v5_vs_goal.BATCH_CONFIG.json")

fails = []


def case(name, cond, detail=""):
    if cond:
        print("PASS %s %s" % (name, detail))
    else:
        print("FAIL %s %s" % (name, detail))
        fails.append(name)


def raise_plan(fn):
    try:
        fn()
    except ep.PlanError as e:
        return str(e)
    except Exception as e:                                    # noqa: BLE001
        return "@@WRONG_EXC@@ %s: %s" % (type(e).__name__, e)
    return None


def main():
    tmp = tempfile.mkdtemp(prefix="freeze_guard_")

    # ── G4 正向对照先跑：拿不到合法 cfg 就没意义 ──
    good = ep.load_batch_config(GOOD_CFG)
    doc = ep.emit_plan(os.path.join(tmp, "good.INTERLEAVE_PLAN.json"), good)
    case("G4/emit_good_plan", doc["total_attempts"] == 64, "attempts=%s" % doc["total_attempts"])
    nulls = [rel for rel, sha in doc["frozen"]["config_files"].items() if sha is None]
    case("G4/no_null_freeze", not nulls, "nulls=%s" % nulls)
    ep.verify_frozen_identity(doc, good)
    case("G4/verify_ok", True, "重算与声明一致")

    # ── G1 坏卡路径必须拒绝出计划 ──
    bad = copy.deepcopy(good)
    bad["cards_path"] = "benchmark/does_not_exist/99_CARDS.json"
    bad["frozen_extra_files"] = [f if f != good["cards_path"] else bad["cards_path"]
                                 for f in good["frozen_extra_files"]]
    msg = raise_plan(lambda: ep.emit_plan(os.path.join(tmp, "bad.INTERLEAVE_PLAN.json"), bad))
    case("G1/emit_refuses_missing_cards",
         msg is not None and "@@WRONG_EXC@@" not in msg and "99_CARDS" in msg,
         (msg or "未抛错")[:120])
    case("G1/no_plan_file_written",
         not os.path.exists(os.path.join(tmp, "bad.INTERLEAVE_PLAN.json")),
         "拒发即不落盘")

    # ── G2 旧计划声明 null（当年静默放行）→ verify 必须拒 ──
    stale = copy.deepcopy(doc)
    stale["frozen"]["config_files"][good["cards_path"]] = None
    msg = raise_plan(lambda: ep.verify_frozen_identity(stale, good))
    case("G2/verify_refuses_declared_null",
         msg is not None and "@@WRONG_EXC@@" not in msg and good["cards_path"] in msg,
         (msg or "未抛错")[:120])

    # ── G3 当下文件缺失（哈希漂移的极端情形）→ verify 必须拒 ──
    ghost = copy.deepcopy(good)
    ghost["cards_path"] = "benchmark/does_not_exist/98_CARDS.json"
    ghost["frozen_extra_files"] = [f if f != good["cards_path"] else ghost["cards_path"]
                                   for f in good["frozen_extra_files"]]
    msg = raise_plan(lambda: ep.verify_frozen_identity(doc, ghost))
    case("G3/verify_refuses_missing_now",
         msg is not None and "@@WRONG_EXC@@" not in msg and "98_CARDS" in msg,
         (msg or "未抛错")[:120])

    print("----")
    print("FREEZE MISSING GUARD: %s (%d checks, fail=%d, real_model_calls=0)"
          % ("PASS" if not fails else "FAIL", 7, len(fails)))
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
