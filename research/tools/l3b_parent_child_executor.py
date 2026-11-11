# -*- coding: utf-8 -*-
"""l3b_parent_child_executor.py — 52 槽父子对照执行器（v12 复核 §6-D）。

**配置登记见 refs/L3B_PARENT_CHILD_PLAN_20261021.md（方案 v6）——本文件不是
调用授权，52 次真实生成需用户另行明确授权。**

实现方式：导入 66 槽执行器（tools/l3b_three_arm_executor.py）并覆盖配置常量——
已验证的 runner 路径、请求配对校验、checkpoint 前驱断言、真实网关额度、
fail-fast 停批守卫全部同源复用，**不重写、不新增恢复路径**。

覆盖项（与 v3 默认值的全部差异在此显式列出）：
  CAP            66 → 52
  BATCH_PREFIX   l3b_66 → l3b_52
  ARMS           三臂 → {A3: phase2_expr（父）, F: phase3_facts（子）}
  EPISODES       2 主对照 + 4 开发性单轮正反例（benchmark/episodes/dev/）
  EP_TURNS       NCA=6, NCB=5, 4×dev=1
  EP_REPEATS     主对照 r1/r2；dev 仅 r1（无重复组）
  ROTATIONS      [("A3","F"),("F","A3")] 交错
  EXPR_WANT      A3→1, F→1（子臂继承 expression.md）
  DEP_NOTE_ARMS  A3/F 两臂均记槽级依赖注记（父子都加载 phase1 检索机制）
  FREEZE_FILES   含 phase1/phase2/phase3 机制与两份资产（v13 复核 §7：实际运行的
                 检索依赖纳入冻结）
  FROZEN_SHA     方案 v4 登记表（含 §6B 升级后的 lb_assembly）

用法：
  python tools/l3b_parent_child_executor.py --batch-id dryrun_x --adapter mock
  python tools/l3b_parent_child_executor.py --batch-id drill_x --adapter mock --chaos-attempt 13
  python tools/l3b_parent_child_executor.py --batch-id dryrun_x --adapter mock --selftest-guards
  python tools/l3b_parent_child_executor.py --batch-id dryrun_x --summary-only
"""
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(_HERE)
sys.path.insert(0, _HERE)
sys.path.insert(0, os.path.join(_ROOT, "arena"))
os.chdir(_ROOT)

import importlib.util as _ilu
_SPEC = _ilu.spec_from_file_location(
    "l3b_three_arm_executor_base",
    os.path.join(_HERE, "l3b_three_arm_executor.py"))
T = _ilu.module_from_spec(_SPEC)
_SPEC.loader.exec_module(T)

# ---- 方案 v4 配置覆盖（全部差异显式在此；守卫代码零改动） ----
T.CAP = 52
T.BATCH_PREFIX = "l3b_52"
T.EXPR_ARM = "A3"
T.EXPR_WANT = {"A3": 1, "F": 1}
# v13 复核 §7：父子两臂都实际加载 phase1 检索机制 → 纳入冻结/快照/槽级依赖注记
T.DEP_NOTE_ARMS = ("A3", "F")
T.FREEZE_FILES = (
    "candidates/phase1_retr_idf/mechanism/mechanism.py",
    "candidates/phase2_expr/mechanism/mechanism.py",
    "candidates/phase3_facts/mechanism/mechanism.py",
    "candidates/phase2_expr/assets/system_prompt/expression.md",
    "candidates/phase3_facts/assets/system_prompt/facts_evidence.md",
)
T.ARMS = {
    "A3": "candidates/phase2_expr",
    "F": "candidates/phase3_facts",
}
T.EPISODES = {
    "NCA_CORR_INTENT_v1": "benchmark/episodes/NCA_CORR_INTENT_v1.json",
    "NCB_ENTITY_DENSE_v1": "benchmark/episodes/NCB_ENTITY_DENSE_v1.json",
    "DEV_MEETING_FACTS_v1": "benchmark/episodes/dev/DEV_MEETING_FACTS_v1.json",
    "DEV_TWO_BOXES_v1": "benchmark/episodes/dev/DEV_TWO_BOXES_v1.json",
    "DEV_NEW_STATION_v1": "benchmark/episodes/dev/DEV_NEW_STATION_v1.json",
    "DEV_ROOMMATE_ADVICE_v1": "benchmark/episodes/dev/DEV_ROOMMATE_ADVICE_v1.json",
}
T.EP_TURNS = {
    "NCA_CORR_INTENT_v1": 6,
    "NCB_ENTITY_DENSE_v1": 5,
    "DEV_MEETING_FACTS_v1": 1,
    "DEV_TWO_BOXES_v1": 1,
    "DEV_NEW_STATION_v1": 1,
    "DEV_ROOMMATE_ADVICE_v1": 1,
}
T.EP_REPEATS = {
    "NCA_CORR_INTENT_v1": ("r1", "r2"),
    "NCB_ENTITY_DENSE_v1": ("r1", "r2"),
    "DEV_MEETING_FACTS_v1": ("r1",),
    "DEV_TWO_BOXES_v1": ("r1",),
    "DEV_NEW_STATION_v1": ("r1",),
    "DEV_ROOMMATE_ADVICE_v1": ("r1",),
}
T.ROTATIONS = [("A3", "F"), ("F", "A3")]
T.FROZEN_SHA = {
    "candidates/phase1_retr_idf/mechanism/mechanism.py":
        "50c095eb7ee021b24831f741d56ea7cad5e30466a11f25217b476ecee49f8727",
    "candidates/phase2_expr/mechanism/mechanism.py":
        "31a1dd72e355257b75b12bb7f5d74cf104abea6084ba904972d2b1908dfd482a",
    "candidates/phase3_facts/mechanism/mechanism.py":
        "c9f05bddef71c96664bc3aa5d5bb69ea98168aa35e58e3af4c9360a61fef03ed",
    "candidates/phase2_expr/assets/system_prompt/expression.md":
        "707128023da1def0177210d120986ef67a3094ee74b40646a7c2399c4214c367",
    "candidates/phase3_facts/assets/system_prompt/facts_evidence.md":
        "b9516d44f1a04b20bc36add6d841f3fb4c450ee245a013ba22ec080d9f65d1f7",
    "candidates/phase2_expr/params.json":
        "e215b71811aee85d7051514c2ff13fdfc12323fbaf8aee75cdc987ebfe0406dc",
    "candidates/phase3_facts/params.json":
        "e215b71811aee85d7051514c2ff13fdfc12323fbaf8aee75cdc987ebfe0406dc",
    "arena/lb_scorer.py":
        "678c4e191b20394da444809814ff9f7665d23cb83d03c997543fb89272f7e959",
    "arena/lb_assembly.py":
        "e6c4b58900b3197e8583cfcabf2c5f8a6a147f10da3b471a6f3f6f705724425c",
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
    "benchmark/episodes/dev/DEV_MEETING_FACTS_v1.json":
        "cbe5cb6eda859b1f5a6bb0696eefcf57ee82480cf20a4f80a5a19eb6b42819f4",
    "benchmark/episodes/dev/DEV_TWO_BOXES_v1.json":
        "6b68bafdf53a171d843d4c257eb470353c4d330d306ac144ba3dcad5e0852c3f",
    "benchmark/episodes/dev/DEV_NEW_STATION_v1.json":
        "743eeb75a8456b1d4e813f5c80e35fd0c5900ba9dfd9d2fa59d258c42940181d",
    "benchmark/episodes/dev/DEV_ROOMMATE_ADVICE_v1.json":
        "d498b46baedc750ae9b8faa42f60c74b244c090dc33295df23f2d72e8f1730d7",
    "refs/L3B_PARENT_CHILD_PLAN_20261021.md":
        "ec4c9d21b0e80eea60695c6cba7f8dff496f1a26d6d2e531504115e4b3aceb75",
}

# 槽位算术自证：44 主对照 + 8 开发 = 52
_n = sum(T.EP_TURNS[e] * len(T.EP_REPEATS.get(e, T.REPEATS)) * len(T.ARMS)
         for e in T.EPISODES)
assert _n == 52, "52 槽算术失配: %d" % _n

if __name__ == "__main__":
    T.main()
