# IDENTITY.md — phase1_retr_idf 候选身份声明

| 项 | 值 |
|---|---|
| 候选名 | phase1_retr_idf |
| 父版本 | candidates/phase0_beta_single |
| 唯一主变量 | 检索策略（LCS≥2 → 混合 IDF 余弦 + 条目级归因激活门 v2.2，2026-10-20 独立复核后收口） |
| 与父版本逐字节一致部分 | assets/（system_prompt/format.md 等 15 文件）、params.json、存储形态、写回政策、单阶段编排、memory-off 语义 |
| 机制差异文件 | mechanism/mechanism.py（检索+追踪+analysis 非 dict 防崩） |
| 历史版本归档 | archived/historical_retr_v1_mechanism_20261020.py（历史真实 run 的机制字节，sha256 `1c85c6b3…98b74`，与 v8 包缺失项对应；不再覆盖已执行候选版本） |
| 契约 | string\|null（directions 元素；出站提示 format.md §directions 原文口径，第九波判决 §3.1 统一） |
| 记忆开关 | 支持（--memory-off / memory_access=False；消融分支 B 用） |
| 追踪 | retrieval_trace.jsonl（评估专用，不入快照、不注入请求） |
| 回归 | tools/test_retr_idf.py + tools/test_trace_robustness.py + 八套既有测试 |
| 状态 | 开发候选——正式晋级冻结；未宣称真人感通过/长期记忆验收/检索泛化准入 |
| 已知限制 | 零字面重叠不可召回、HER/ME 归属不可分、无状态失效机制、G06 族同字实体歧义（详见 MECHANISM.md §1 与 dev nodes 文档） |
