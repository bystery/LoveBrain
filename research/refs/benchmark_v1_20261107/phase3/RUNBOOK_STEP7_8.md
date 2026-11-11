# RUNBOOK_STEP7_8 · SELECTION / HOLDOUT 正式实验执行手册（Phase 3，2026-11-09）

> 前置门（全部满足才开跑）：
> S1 数据集冻结（DATASET_V1_FROZEN_MANIFEST.json + ARM_REGISTRY_FROZEN.json 哈希落盘）；
> S2 本批 40/120 篇五件套齐 + validate 全绿 + sidecar strict 0 失败 + Marker QC 0 + dedupe 0 意外近重
>    + lineage 0 违例 + guard 0 违例；S3 授权文件登记（SELECTION_AUTHORIZATION.json）；
> S4 capture 模式全绿（四组 mock 管道 + 校验 0 FAIL）。
> HOLDOUT 另加：金库 120 篇就位 + 一次性授权登记 + 政策门预检通过 + once-only 语义确认。

## 第 7 步 SELECTION 四组（cap 2250）

```bash
# 0) 授权登记（开跑前把 SELECTION_AUTHORIZATION_TEMPLATE.json 填 registered_at 后作为正式授权）
# 1) capture（离线全链自检；不消费预算）
python -I tools/benchmark_v1/phase3_runner.py --mode capture \
  --episodes E339,E340,E341,E342,E343,E344,E345,E346,E347,E348,E349,E350,E351,E352,E353,E354,E355,E356,E357,E358,E359,E360,E361,E362,E363,E364,E365,E366,E367,E368,E369,E370,E371,E372,E373,E374,E375,E376,E377,E378 \
  --authorization refs/benchmark_v1_20261107/phase3/SELECTION_AUTHORIZATION.json \
  --out runs/phase3_selection_capture
# 2) live（唯一真实调用入口；全局预算账本自动跨进程计数）
LB_API_KEY=<env-only> python -I tools/benchmark_v1/phase3_runner.py --mode live \
  --episodes E339,E340,E341,E342,E343,E344,E345,E346,E347,E348,E349,E350,E351,E352,E353,E354,E355,E356,E357,E358,E359,E360,E361,E362,E363,E364,E365,E366,E367,E368,E369,E370,E371,E372,E373,E374,E375,E376,E377,E378 \
  --authorization refs/benchmark_v1_20261107/phase3/SELECTION_AUTHORIZATION.json \
  --out runs/phase3_selection_live
# 3) 评分与聚合
python -I tools/benchmark_v1/universal_scorer.py --run-dir runs/phase3_selection_live \
  --arms DIRECT,COT,LEGACY_COMPAT_R9,CURRENT \
  --out-prefix refs/benchmark_v1_20261107/phase3/selection
python -I tools/benchmark_v1/aggregate_four_arm_phase3.py --run-dir runs/phase3_selection_live \
  --out refs/benchmark_v1_20261107/phase3/FOUR_ARM_RESULTS.csv \
  --summary refs/benchmark_v1_20261107/phase3/FOUR_ARM_SUMMARY.json
# 4) 统计（三主比较 Holm；专题切片）
python -I tools/benchmark_v1/stats_phase3.py \
  --scores refs/benchmark_v1_20261107/phase3/selection_universal_scores.csv \
  --metric severe_error --allocation refs/benchmark_v1_20261107/phase3/DATASET_V1_ALLOCATION_V3.csv \
  --out refs/benchmark_v1_20261107/phase3/STATS_SELECTION_severe_error.json
# 5) SELECTION_RUN_REPORT.md：四组运行清单/配对结果/API 消耗/契约通过率/客观错误/
#    judge 辅助读数/人工审核准备包/失败案例/进入 HOLDOUT 的门禁判定
```

## 第 8 步 HOLDOUT 一次性四组（cap 7000）

1. 开跑前 10 项门禁逐项核对（指导书 §P3-6.1），登记 HOLDOUT_AUTHORIZATION.json
   （kind=holdout_once_only，authorized_splits=[HOLDOUT]，episodes=E401..E520，cap=7000）；
2. live：`phase3_runner.py --mode live --episodes E401,...,E520`（runner 从金库读正文，
   并强制 stories_v2 无副本；任何失败如实记录，不重试质量问题，不换题/换模型）；
3. 跑完立即：exposure.experiments_used 追加正式消费事件 + custody lifecycle=EXPOSED
   （不可逆）；登记 HOLDOUT_RUN_REPORT.md；
4. 分析同第 7 步（不改变任何评分规则/不改 prompt——跑后修改=当轮作废）。

## 中途异常处理（预注册）

- 全局预算到限：停批 + 报告缺口位（缺失规则=如实报缺失，不补跑）；
- 瞬态 503/超时：resume 循环扫尾（基础设施重试按授权；每次有 attempt_id 与原因）；
- 单篇执行失败：记录失败轮位，不伪造完整结果；
- 发现政策门问题：停批报告，不得绕过。
