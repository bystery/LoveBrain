# W19-03 跨域批设计 — 判据冻结（2026-11-03）

## 背景与定位

把两个单域冠军收成一个**开发集总冠军候选**：≤24 轮域冠军
`window_expand_b_norecap_guard`（W17C MERGE）与 >24 轮域冠军
`window_expand_b_norecap_uc`（W16 KEEP）的 guard+uc 单构建
`window_expand_b_norecap_uc_guard`（父本 = uc，唯一增量 = guard 的确定性
输出契约守卫；不加 suppression，保持单变量）。

- 批次：`BATCH-W19-CROSS-20261103`，双臂同批对拍。
- 题材：W17C 6 篇 12 轮（E157/E164/E170/E183/E191/E212，72 位）+
  W18B 4 篇 40 轮（LRE117/LRE121/LRE146/LRE138，160 位）= 232 请求位，
  双臂 464 attempts（卡合并件 `benchmark/stories_v2/wave3/GRADING_CARDS_W19_CROSS.json`）。
- 批配置：`W19_CROSS.BATCH_CONFIG.json`；执行计划：
  `W19_CROSS.INTERLEAVE_PLAN.json`（冻结执行器 `tools/exam_interleave_plan.py` 生成）。

## 跑前冻结判据（G1–G5，原文照录，不得跑后改）

- G1 每域 severe B3 亲读：candidate ≤ parent，且不新增故障族；
- G2 九维 main-only 最差 ≥−0.3，interact/emotion/initiative 各 ≥−0.2；
- G3 契约：candidate veto ≤ parent+1；空回复 0；
- G4 成本：prompt ≤+5%、0 额外调用；p50/p95 只报告；
- G5 顺序：swap 只作稳定性诊断；若 swap 与非 swap 方向翻转，不得宣布单冠军；
- 两域全过才算开发集总冠军；任一域破门 → 保留双域冠军（guard 管 ≤24、uc 管 >24）。

## 判据解读与记分口径

- "每域" = 12 轮域（6 篇 72 对）与 40 轮域（4 篇 160 对）**分别**过门；
  不得把两域合并平均后宣布通过。
- G1 severe 亲读由主会话 B3 执行（`LP_W19CROSS_B3_READRESULT.json`），
  未校准 judge 不单独判死；G2 九维只作辅助门，机械重评须在 W19-01 修复后的
  评分器上做（FK-SCORE-01）。
- G3 的 veto/空回复计数按双臂全部 464 attempts 记；契约修复只作用于返回文本，
  不改变 prompt 构造，故 G4 同时验证 guard 未引入 prompt 膨胀（≤+5%）与
  额外调用（=0）。
- G5 的 swap 诊断只在计划内 arm_order 交替（parent_first/candidate_first）
  上做故事级方向比较，不新增任何调用。

## 边界声明（原样保留，不得跑后改写）

- 本批是**开发集冠军候选**判定，不是封存验收；
- 未与 `reference_product_v1` 同批对拍；
- 题材全部来自已暴露开发素材（W17C/W18B 复用），泛化结论受限；
- 任一域破门时的处置是保留双域冠军，不修改判据重跑、不换指标到赢。

## 执行顺序约束

live 独占 6 流；授权 `AUTH-W19-CROSS-20261103-01` 由主会话建立并绑定
plan_sha256 / batch_id / cards / attempts / 双臂 identity_sha256；
主会话核对后派发，worker 不自行启动 live。
