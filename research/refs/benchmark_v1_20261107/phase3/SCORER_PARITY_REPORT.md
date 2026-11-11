# SCORER_PARITY_REPORT · 评分公平层修复报告（Phase 3 第 4 步，2026-11-09）

> 指导书 P3-3.1（不能继续用 score10 跨四组直接排名）执行件。
> **旧评分器零改动**：`arena/lb_scorer.py` 原样保留（产品契约层，harness 组专用）；
> 通用评分层 = 新工具 `tools/benchmark_v1/universal_scorer.py`（universal_scorer_v1 +
> semantic_guards_v1 + normalize_v1 复用 + gold_diag 逻辑继承）。

## 1. 缺陷回顾（Pilot 实测）

| 指标 | DIRECT | CURRENT | 性质 |
|---|---|---|---|
| score10 均值 | ≈0.14 | ≈9.96 | **结构假象**：轻量臂 `recommended` 顶层键不在 lb_scorer PROTOCOL_TOP_KEYS 白名单 → veto 544/552 全量误触发 |
| scorer_veto | 544/552 | 2/552 | 同上；CURRENT 的 2 位 veto（illegal_source_id）是真实协议违例 |
| diag_required_hit_rate | 0/46 | 0/46 | 关键词共现判分失效 + required 位语义强约束 → 诚实 N/A |

## 2. 修复设计（双层口径）

- **通用层（四组同权，参与跨组质量对照）**：primary_text 抽取（normalize_v1 固定规则，不挑备选）
  → action 判定 → gold 断言确定性检查（gold_diag v0 逻辑 + 语义守卫 v1）→ 严重错误
  （parse/empty/contract + cancel_affirm 违例）。
- **产品契约层（只对适用组统计，不参与跨组对照）**：LEGACY/CURRENT 用 lb_scorer 原样
  （score10/veto 透传）；DIRECT/COT 用 normalize_v1 light 契约；harness 形状 veto 对轻量臂
  记 NOT_APPLICABLE（原因随行）。
- **语义守卫 v1**（C14/C15 误伤修复的第一步，P3-3.2）：疑问句守卫（"你爸妈知道吗？"不得坐实
  "爸妈知情"）、否定守卫（同子句否定词）、条件守卫（如果/要是/假如/万一/就算）、引用守卫
  （引号内转述提及≠坐实）。命中分类：affirm / question_mention / negated_mention /
  hypothetical_mention / quoted_mention，guard_events 逐条落盘供层 2 与 B3 亲读复核。

## 3. 离线回归（Pilot 1,104 位真实答卷重评，零模型调用）

| 分片 | 臂 | n | 契约通过率（自有层） | 严重错误率 | cancel_affirm 违例轮 |
|---|---|---|---|---|---|
| part_b (E301–E319) | DIRECT | 228 | **1.0000** | 0.0000 | 0 |
| part_b | CURRENT | 228 | **0.9956**（2 位真实 veto） | 0.0044 | 0 |
| part_c (E320–E338) | DIRECT | 260 | 0.9962 | 0.0038 | 0 |
| part_c | CURRENT | 260 | 0.9962 | 0.0038 | 0 |
| oldT09 (E063/LRE121) | DIRECT | 64 | 1.0000 | 0.0000 | 0 |
| oldT09 | CURRENT | 64 | 1.0000 | 0.0000 | 0 |

- **score10 不对称消失**：通用层下两臂用同一套可比较指标；产品契约层分数只作 harness 组
  工程能力附注，不再进入跨组排名。
- 轻量臂 veto 从 544/552（98.6% 误触发）→ 0 适用；CURRENT 的 2 位真实协议违例在通用层
  以 severe_error 如实保留（不洗白）。
- 产物：`_parity_b/_c/_old` 前缀的 `*_universal_scores.csv`（逐位）+ `*_universal_summary.json`。

## 4. 版本与回归纪律

- `universal_scorer_v1` 新建（不改旧评分器）；`semantic_guards_v1` 首版；
- 单元测试 21 项（`tests/test_phase3_tools.py`：守卫语义/控制集评估/分层/政策门/预算账本/
  LEGACY_COMPAT_R9 身份分离），全过；全库离线套 60 passed（Fast Gate）；
- 后续任何守卫改动 = 升版本 + 控制集（第 5 步建 ≥100 正负控制）离线重评 + 本报告勘误登记；
- 旧口径可复现：`gold_diag.py` 原样在库，v0 数字可重算。

## 5. 遗留与边界（诚实登记）

1. 语义守卫是**确定性词面规则**，不等于语义理解：反问/修辞肯定（"不是说周六爬山吗"）可能被
   守卫豁免（漏检偏保守——符合"未校准不判死"取向）；边界案例由层 2 judge + B3 亲读兜底，
   第 5 步控制集将量化各类 TP/FP/漏检。
2. required 命中检查保持 v0 口径（标记/value 出现即命中）+ guard_events 辅助线索；严肃的
   "记忆正确使用"测量依赖层 2（Judge 资格复核后）与人工。
3. Judge 资格标签失真（time/emotion/initiative/factuality 顺序一致率 <0.80 标 full）在
   第 5 步 JUDGE_CALIBRATION_V2 修正——本报告不越权改资格。
4. LEGACY_COMPAT_R9 已在 phase3_runner 接线（mock 冒烟通过：wave4 dict-persona 篇完整
   12 轮生命周期），正式实跑在第 7 步。
