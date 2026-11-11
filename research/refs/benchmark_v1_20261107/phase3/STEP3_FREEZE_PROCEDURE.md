# STEP3_FREEZE_PROCEDURE · Benchmark V1 冻结程序（Phase 3 第 3 步，2026-11-10）

> 前置门（全部满足才执行冻结）：
> - SELECTION 40 篇五件套齐 + validate 全绿 + sidecar strict 0 失败 + audit C1-C4 全 PASS
> - HOLDOUT 120 篇五件套齐（金库内）+ validate 全绿 + sidecar strict 0 失败 + audit CH1-CH12 全 PASS
> - 分派表 V3 全 240 行 split 定稿（PENDING=0）
> - ARM_REGISTRY_FROZEN.json 哈希已落盘且可复算

## 冻结步骤

### 3.1 计算三层哈希

```bash
python -I tools/benchmark_v1/build_freeze_manifest.py \
  --allocation refs/benchmark_v1_20261107/phase3/DATASET_V1_ALLOCATION_V3.csv \
  --arm-registry refs/benchmark_v1_20261107/phase3/ARM_REGISTRY_FROZEN.json \
  --out refs/benchmark_v1_20261107/phase3/DATASET_V1_FROZEN_MANIFEST.json
```

产出 `DATASET_V1_FROZEN_MANIFEST.json`：
- Layer 1: 每篇 4 文件 SHA-256（episode/reference/timeline/audit）
- Layer 2: 每篇 composite hash（4 文件哈希拼接再哈希）
- Layer 3: 数据集 manifest hash（全部 composite + 分配表 + 评分器 + ARM 注册表）
- HOLDOUT 哈希从金库计算，只有哈希值进入仓库 manifest

### 3.2 回写 exposure.hashes + frozen_at

对每篇故事（DEV/SELECTION 在仓库内，HOLDOUT 在金库内）：
- exposure.hashes = {4 文件 SHA-256}
- exposure.frozen_at = 冻结时间戳

### 3.3 回写分派表 status

- DEV/SELECTION/HOLDOUT 全部 status → FROZEN
- split 列保持不变（DEV/SELECTION/HOLDOUT）
- PENDING 行数 → 0

### 3.4 盖章 EXPERIMENT_PROTOCOL_FROZEN_V1.md

在协议冻结稿末尾追加：
```
## 冻结生效章（2026-11-10）

本协议自此刻起冻结。数据集哈希 = <layer_3_dataset_hash>。
评分器哈希 = <universal_scorer hash>。
ARM 注册表哈希 = <arm_registry hash>。
此后不得修改题目、评分规则、ARM 定义。跑后修改 = 当轮作废。
```

### 3.5 HOLDOUT 封存报告

写 `HOLDOUT_SEAL_REPORT.md`：
- 金库路径、120 篇清单、composite hashes
- 零消费证明（exposure.experiments_used = []，custody lifecycle = AUTHORED）
- 仓库内只有公开 manifest（哈希值），无正文副本
- runner 双份正文反查结果（stories_v2 无 HOLDOUT 副本）

### 3.6 冻结验收

- DATASET_V1_FROZEN_MANIFEST.json 中 missing_episodes = []
- split_counts = {DEV: 80, SELECTION: 40, HOLDOUT: 120}（+ DIAGNOSTIC_POOL 3 不计）
- custody_split_guard --expected-pending 0 → 0 violations
- 全部 exposure.frozen_at 非空
- 测试套件 64+ passed

## 冻结后纪律

- **封板后改题 = 舞弊**
- 评分规则、ARM 定义、题目内容不得修改
- 评分器/统计方法的修改须单独版本化 + 用已有答卷离线重评
- HOLDOUT 在第 8 步一次性运行前保持零消费
