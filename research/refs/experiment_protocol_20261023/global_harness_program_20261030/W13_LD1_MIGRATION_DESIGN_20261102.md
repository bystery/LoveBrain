# W13-LD1 新长剧域迁移判别批设计（2026-11-02，判据跑前冻结）

目的：检验 window_expand_b（FK-LR2-01 修复归属，>24 轮域现行冠军）在**非搬家域**新长剧上的迁移性。
素材：wave3_ext 16 篇长剧（8×40 轮 + 8×32 轮，8 域覆盖，全部 SEALED、零暴露）。

## 1. 假设（可证伪）

**H_LD_main**：window_expand_b 在非搬家域长剧上保持 ≥0.85 真实窗口保持率（T25+ 段），
且九维不退化（最差维 ≥−0.5，interact/emotion 短剧面差距不外推到长剧域）。

**H_LD_null**：非搬家域 T25+ 真实保持 <0.80，或保护维任一 ≤−0.5 →
FK-LR2-01 修复的域外迁移失败，登记"扩窗收益域局限"，冠军归属标注条件化。

## 2. 批设计

- 批号 BATCH-LD1-W13-20261102；臂：window_expand_b（candidate）vs r9_slim（parent，锚臂）。
- 故事（已选，2026-11-02 冻结）：**LRE117(40轮,出行拼车)/LRE141(40轮,工作职场)/LRE125(40轮,餐饮聚会)/LRE147(32轮,医疗健康)**——域互不相同，arm_order 平衡（LRE117/LRE125 parent_first，LRE141/LRE147 candidate_first）。
- 规模：152 轮 × 2 臂 = **304 attempts**，cap 340；fail-fast/no-retry/no-makeup。
  导出件已就绪：benchmark/episodes/LRE117|LRE141|LRE125|LRE147.json（flatten persona）+
  benchmark/stories_v2/wave3_ext/GRADING_CARDS_LD1.json（152 卡）。
- 出窗探针保障：素材自带 ≥6 探针轮/篇（T13+ 依赖 T1–T12 原子），机械保持率按
  measure_lr2_window_keep（T25-32/T33-40 分段）+ miss 亲读分类（四分类，artifact=0）。
- 评审：run_lp_review_generic（cards=新长剧合并卡，另册 AUTH-LD1-REVIEW）+ B3 亲读（QUOTE-RISK 不入账）。

## 3. 判据（冻结）

- KEEP（迁移成立）：T25+ 段真实保持率 ≥0.85 且保护维 goal/boundary/init 最差 ≥−0.5
  且 severe(cand) ≤ severe(parent) 且 prompt ≤ parent+15%。
- 淘汰（H_LD_null）：真实保持 <0.80 或保护维 ≤−0.5。
- 不确定带：0.80–0.85 → 报"域迁移不确定"，不补跑。
- 附带记录：interact/emotion diff（对照 CF1 短剧 −0.385/−0.406 观察项——长剧域若收敛
  则支持"差距源于短剧输出契约"假设）。

## 4. 成本

288 attempts ≈ parent ~90 tokens/turn×~7k... 按 FLJUDGE 实测比例（cand ≈ parent×0.58），
预估 parent prompt ~860k、candidate ~500k chars 级；wall-clock 4 residents ≈ 50–70 分钟。

## 5. 状态

设计冻结；素材等待 T3 语义审查收口（R1–R5 + LRE127 单审）后派发。
三件套与 AUTH 在派发前登记（对齐 CF1 工艺：frozen_bindings+G1–G7）。
