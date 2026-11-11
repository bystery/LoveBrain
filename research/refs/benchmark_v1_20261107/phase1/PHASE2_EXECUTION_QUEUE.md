# PHASE2_EXECUTION_QUEUE · 下一阶段可执行队列（Phase 2 输入）

> 排序原则：先修"会导致结论不可信"的问题，再扩量。每项有入口/产出/验收。

| # | 任务 | 入口/方法 | 产出与验收 | 依赖 |
|---|---|---|---|---|
| Q0 | **live pilot 补跑**（用户配置新 Key 后） | 撤销泄露旧 key→USTC 平台重发→`setx LB_API_KEY`→`python -I tools/benchmark_v1/check_provider.py refs/benchmark_v1_20261107/phase1`→OK 后 `four_arm_runner --mode live`（cap 320） | 288+ 位置真实读数；PILOT_RESULTS/报告更新；模型固定 deepseek-flash-2 | 用户 Key |
| Q1 | 主题分类复核：136 条兜底 T01 逐篇归位 | DATASET_INVENTORY.csv 的 T01 low-confidence 行 + manifest themes/卡面/语义审查 | TOPIC_COVERAGE_MATRIX 更新；分类债清零 | 无 |
| Q2 | B 线 E048 泄露裁决与执行 | SPLIT_AND_CUSTODY_PLAN §3 选项 A/B | B 线 v3 manifest 或折扣绑定登记 | 用户拍板 |
| Q3 | 复用旧题 gold 逐篇可信化 | 45 条 reusable 的 partial 项升级或降 N/A | gold_conflict 清账；分母表更新 | Q1 |
| Q4 | 新题 pilot（每新族 4–6 篇） | NEW_STORY_SPEC_V1 + TOPIC_RUBRIC_DRAFT_V1；隔离出题+validate+双臂试跑 | 判别力报告（区分位占比）；达标才扩量 | Q1–Q3 |
| Q5 | severe 人工真值 30–50 轮 | 高危族轮位双通道金标（AI 初判+人工逐字） | judge 校准锚扩到 boundary/memory | 无（可并行） |
| Q6 | judge 逐维校准扩样 | W20-03 锚点法 + Q5 金标 | 全 9 维资格表 v2 | Q5 |
| Q7 | 正向能力量化门设计定稿 | TOPIC_RUBRIC T02–T04 行为锚 + W08 资产 | 进程序断言+judge 双通道的判据 v1 | Q1 |
| Q8 | Benchmark V1 冻结 | 全部前项验收后：题库 sha256+分配表+评分版本三元组 | V1 冻结宣告+Phase 3 实验计划 | Q0–Q7 |
