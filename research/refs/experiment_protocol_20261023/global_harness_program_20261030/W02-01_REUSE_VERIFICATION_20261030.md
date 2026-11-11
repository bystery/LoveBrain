# W02-01 REUSE_VERIFIED 核验记录（2026-10-30）

- 会话：`main-zcode-20261030-0052`；任务：W02-01（差额盘点原项目意图/补充/知识/上下文/后台机制，复用已有函数溯源）
- 现成交付件：`BASELINE_SCOPE_V2.md`（2026-10-27，根目录）+ `VERSION_LINEAGE_V2.json`

## 验收对照（BASELINE_SCOPE：原生/移植/未覆盖逐项说明）

| 验收要素 | 覆盖情况 |
|---|---|
| 原生局限逐项 | ✅ §3：extractKeywords（括注残留/日期咬字）、extractEventDate（26-11 非法日期）、isIntentEffective（无 ADJUSTED），均有 Kotlin 行号级证据 |
| 移植/增强区分 | ✅ §1 三类基线分立：reference_c4_frozen（6e65…，仅解释旧答卷）/ reference_faithful（7db3a371…，NR 未实跑）/ reference_repaired v1.2（501bc426…，P06 共同参照，须标"已修复/增强"） |
| 未覆盖范围 | ✅ §4：仅前台实验范围（装配/知识/场景/计划/note/chat/时间戳/byBlocks/记忆投影），App 完整服务/UI/网络/通知明确不覆盖，faithful 标 NR |
| 身份/闭包可追溯 | ✅ §2/§5：历史 vs 当前身份对照、C2/C3 声明闭包缺口（32 文件实际闭包）、P06 三绑定（A/B/C）冻结记录；谱系机读在 VERSION_LINEAGE_V2.json |

## 差额注记（本核验新增，供 W02-03 裁决）

1. 该文书止于 P06 绑定（2026-10-27）；其后 P07/P08 新增身份（action_policy_r1/r2/r3、r4_guard 系）未入此表——它们的历史实际臂对照见 `submission_review_20261029_1510/INDEPENDENT_CHECKS.json historical_actual_arms`，不构成对本文书的矛盾。
2. 总书 W02-03 要求"固定忠实前台原版为 B0"：`reference_faithful`（7db3a371…）身份存在但 **NR 未实跑**；B0 用 faithful（需新实跑）还是 repaired v1.2 `501bc426…`（有真实成绩但须标增强）是 W02-03 的 scoped decision，不在本核验代决。
3. `v0_minimal_events`（Bmin 候选）可比范围核验仍是 W02-03/W09-03 前置（RESEARCH_STATE baselines 已标注 verify comparable scope）。

结论：W02-01 按既有交付件 REUSE_VERIFIED，不重做、不改动原文书；以上差额注记原样带入 W02-03。
