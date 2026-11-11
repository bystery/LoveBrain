# HOLDOUT_SEAL_REPORT · Benchmark V1 HOLDOUT 封存报告（Phase 3 Step 3.5，2026-11-10）

## 1. 金库信息

- **物理路径**：`D:/lovebrain-arena_holdout_vault/wave6_holdout/`
- **金库位置**：LoveBrain Arena 仓库之外（`D:/lovebrain-arena/` 之外），不可进入 git
- **故事数**：120 篇（E401–E520）
- **每篇文件**：episode.json / timeline.json / exposure.json / reference.json / audit.md / runner_episode.json

## 2. 封存状态

| 项 | 状态 |
|---|---|
| 作者完成 | 120/120（6 轮 × 2 并发，H01-H12） |
| 标注完成 | 120/120（6 轮 × 2 并发，HB01-HB12） |
| 独立审计 | HC1-HC4 全部完成（30 篇/审计员） |
| 审计修复 | HC2 幽灵原子 10 处已修 + HC1/HC3 跨轮回指 37 原子已补 |
| 机械验证 | quote strict 全 PASS / runner hash 全 match / 8 节 audit 全齐 |
| experiments_used | **0**（全部 120 篇 exposure.experiments_used = []） |
| custody lifecycle | **AUTHORED**（未经实验消费） |
| 仓库内容副本 | **0**（stories_v2 内零 HOLDOUT 目录） |

## 3. 哈希摘要

- **HOLDOUT 在 manifest 中仅存哈希值**，正文不进入仓库
- Layer 1: 每篇 4 文件 SHA-256（episode/reference/timeline/audit）
- Layer 2: 每篇 composite hash（4 文件哈希拼接再哈希）
- Layer 3: 数据集 manifest hash（含全部 composite + 分配表 + 评分器 + ARM 注册表）
- **数据集哈希**：`ed63b6b91d8b3bda2a28afde35900ae1cb114bfe6c304fd0449fbef1a7ef79c3`
- 完整 manifest：`refs/benchmark_v1_20261107/phase3/DATASET_V1_FROZEN_MANIFEST.json`

## 4. 使用规则

- **一次性运行**：HOLDOUT 在 Phase 3 Step 8 只运行一次四组实验（cap 7000）
- 运行后不得按逐题错误修补
- 运行结果计入最终论文，不用于迭代调参
- 违反一次性原则 = 当轮作废

## 5. 审计结论

- HC1 (E401-E430)：16 PASS + 14 FAIL → 修复完成（21 跨轮回指原子补充）
- HC2 (E431-E460)：23 PASS + 7 FAIL → 修复完成（10 幽灵原子修复）
- HC3 (E461-E490)：20 PASS + 10 FAIL → 修复完成（16 跨轮回指原子补充）
- HC4 (E491-E520)：30 PASS + 0 FAIL
- **总修复**：37 跨轮回指原子 + 10 幽灵原子 = 47 处
- **修复后全部 PASS**：quote strict 0 失败 / runner hash 全 match / 距离多样性 d=1+d≥4 全覆盖

## 6. 规模统计

- 120 篇 × 12 轮 = 1,560 请求位（T09 24/32 轮另计）
- 3,707 required_memory_atoms（含修复后新增 37）
- 879 required_this_turn（含修复后新增 37）
- 1,397 protected_expressions
- 1,164 forbidden_factual_claims
- 116 唯一场景域 + 4 孪生组共享域
