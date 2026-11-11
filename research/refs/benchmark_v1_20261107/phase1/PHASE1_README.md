# Benchmark V1 · Phase 1 交付（PHASE1_README）

> 执行依据：用户外部指导书 2026-11-07（以 `byster/arena@75ab97e` 为起点）。
> 执行日期：2026-11-07。执行者：AI Worker。**当前状态：离线交付完成；live pilot 按规程 BLOCKED（等 Key）。**

## 一句话结论

四组对照系统（DIRECT/COT/LEGACY/CURRENT）已建成并在 mock/capture 管线上全量跑通
（14/14 离线测试全绿、九项 capture 校验全 PASS、236/236 归一化行产出、12/12 盲评位就绪），
数据盘点发现 **160 条旧题复用预算不可信（实测仅 45）** 与 **B 线 E048 谱系泄露（CRITICAL）**；
真实模型 Pilot 因 Key 未配置按指导书规程登记 BLOCKED，配好 Key 后一条命令即可补跑。

## 交付索引

| 类 | 文件 |
|---|---|
| 总览 | 本文件、`SOURCE_AND_SCOPE.md`、`PHASE1_ACCEPTANCE.md` |
| 四组身份 | `ARM_REGISTRY.json`、`BASELINE_IDENTITY_REPORT.md`、`FOUR_ARM_PROTOCOL_V1.md` |
| 数据盘点 | `DATASET_INVENTORY.csv`(208 行)、`DATASET_COMPOSITION_ACTUAL.md`、`TOPIC_COVERAGE_MATRIX.csv`、`DATASET_REUSE_AUDIT.md`、`LINEAGE_LEAKAGE_REPORT.md` |
| Pilot | `PILOT_AUTHORIZATION.json`、`PILOT_CASE_MANIFEST.json`、`PILOT_EXECUTION_REPORT.md`、`PILOT_RESULTS.csv`、`pilot_summary.json`、`PILOT_METRIC_DEFINITIONS.md`、`PILOT_BLIND_REVIEW.md`、`PILOT_BLIND_SELECTION.json`、`CAPTURE_VALIDATION_REPORT.md` |
| API | `API_COMPATIBILITY_REPORT.md`、`LIVE_BLOCKED.json` |
| Phase 2 输入 | `DATASET_V1_ALLOCATION_PLAN.md`、`NEW_STORY_SPEC_V1.md`、`TOPIC_RUBRIC_DRAFT_V1.md`、`SPLIT_AND_CUSTODY_PLAN.md`、`PHASE2_EXECUTION_QUEUE.md` |
| 论文骨架 | `PAPER_V1_OUTLINE.md` |
| 完整性 | `UNTOUCHABLE_HASHES.json`（72 文件基线，pytest 断言背书） |
| 代码 | `tools/benchmark_v1/`（runner/normalize/provider/capture/aggregate/blind/gold_diag/inventory + tests/ + 两个轻量候选） |
| 原始运行产物（本地） | `logs/benchmark_v1/phase1_pilot/`（plan+capture 全产物）、`logs/benchmark_v1/phase1_live_attempt/`（BLOCKED 登记） |

## 复现命令

```bash
cd <仓库根>   # harness-lovebrain/
python -m pytest tools/benchmark_v1/tests -q                      # 14 项离线测试
python -I tools/benchmark_v1/inventory.py                         # 数据盘点复算
python -I tools/benchmark_v1/four_arm_runner.py --mode plan \
  --out logs/benchmark_v1/phase1_pilot \
  --deliver-dir refs/benchmark_v1_20261107/phase1                 # 计划（零网络）
python -I tools/benchmark_v1/four_arm_runner.py --mode capture \
  --out logs/benchmark_v1/phase1_pilot \
  --deliver-dir refs/benchmark_v1_20261107/phase1                 # mock 管线+九项校验
python -I tools/benchmark_v1/aggregate_pilot.py logs/benchmark_v1/phase1_pilot \
  refs/benchmark_v1_20261107/phase1                               # 聚合
python -I tools/benchmark_v1/make_blind_review.py \
  logs/benchmark_v1/phase1_pilot refs/benchmark_v1_20261107/phase1  # 盲评包
# live（Key 就绪后）：
python -I tools/benchmark_v1/check_provider.py refs/benchmark_v1_20261107/phase1
python -I tools/benchmark_v1/four_arm_runner.py --mode live \
  --out logs/benchmark_v1/phase1_live --deliver-dir refs/benchmark_v1_20261107/phase1
```

## 必读的三件事

1. **LEGACY×wave3+ 不兼容**（dict persona 崩溃，r1 代码边界）：如实登记未修；
   Phase 2 二选一（限定素材 / 显式补丁+重注册），见 BASELINE_IDENTITY_REPORT.md。
2. **轻量臂与 harness 评分器**：DIRECT/COT 的 `{action,recommended}` 不在评分器协议键内，
   其 veto 不适用（记录不判死）；跨组客观层=gold_diag_v0（统一、诊断性）。
3. **三件等用户的事**：新 Key（Q0 补跑 live）；B 线 E048 处置裁决（Q2）；
   盲评包填写（12 位，NOT_COLLECTED 未代投）。
