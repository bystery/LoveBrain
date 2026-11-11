# Benchmark V1 · Phase 2 交付（PHASE2_README）

> 状态：**进行中（数据建设主体完成，live pilot 运行中）**。指导书 §14 完整验收
> 以"全部批次+pilot+推送"为前提；逐批如实更新，不虚报。详细判定矩阵见
> `PHASE2_ACCEPTANCE.md`（总判定 PARTIAL）。

## 已确立的基础（2026-11-08 快照）

| 件 | 状态 |
|---|---|
| Provider（USTC deepseek-flash-2） | ✅ OK（check_provider verdict=OK；Key 仅经 env/本地文件，仓库零落盘） |
| 45 篇旧题逐篇审核 | ✅（42 ACCEPT / 1 WITH_NA / 2 OVERLAY / 0 REJECT；93 冲突全裁决；处置见 OLD_STORY_FINAL_DISPOSITION.md） |
| 谱系组规则统一 + lineage_check + 测试 | ✅（0 违例 / 0 B 线成员；pytest 23/23） |
| LEGACY_COMPAT_R9 | ✅（str persona 逐字节等价；ARM_REGISTRY 身份哈希 e09cd877…） |
| 首批试制 38 篇（E301–E338） | ✅ 全部完成（488 轮；T01–T10 全覆盖；T10 孪生 2 组；T09 24/32 轮） |
| Gold 标注（角色 B，B1–B6） | ✅ 38 篇 / 1,268 卡（标注时点口径）/ 1,350 原子，quote 逐字全 PASS |
| 独立审计（角色 C，C1–C3）+ 修复（R1/R2/MarkerFix） | ✅ FAIL 全清（含 E325 CRITICAL 算术链反向重锚）；quote 独立复验全量 0 失配 |
| 模板对齐修复链 | ✅ fix_wave4_schema_v1 apply（699 required 翻转/38 timeline/38 runner 副本/159 unknowns 键等）+ timeline 7 处同步 + audit.md 八节重构 38/38 |
| 机械校验链 | ✅ validate ×3 全绿（488 case 0 错误）；dedupe 222 篇 0 意外近重；sidecar Marker QC 0（43→0）；lineage 0 违例；三层哈希对账全绿 |
| V1 评估规范定稿 | ✅ TOPIC_RUBRIC_V1 / GOLD_ANNOTATION_GUIDE_V1 / ATOMIC_ASSERTION_GUIDE_V1（65 条首批实证教训） |
| 四张分配/盘点/覆盖表 | ✅ 草稿基座（240 行分配：83 实建+157 PENDING；83 行盘点含 2,610 quote 独立复验 0 失败） |
| QC 交付件 | ✅ GOLD_QC_REPORT.md / MARKER_QC_REPORT.json / DEDUPE_REPORT.json |
| live pilot（DIRECT vs CURRENT，cap1500） | 🔄 三进程并行（part_b E301-319 / part_c E320-338 / oldT09 E063+LRE121），cap 记账按三目录求和；LRE121 长上下文超时经 LB_API_TIMEOUT_S 环境旋钮（默认 120 不变）解决 |
| 40 个人工审核锚点 | ✅ 工具就绪（build_human_review_queue.py，mock 自测过）；live 完成后 --run-dir 填真实回复；用户未填=NOT_COLLECTED |
| PHASE2_PILOT_REPORT / 差异分析 | ⏳ 待 live 完成（merge_pilot_parts.py 已备并自测） |
| PHASE2_ACCEPTANCE | ✅ 骨架（总判定 PARTIAL：DONE 16 / PARTIAL 8 / PENDING 4 / BLOCKED 0），Pilot 行现场刷新 |
| PHASE3_PREREGISTRATION | ✅（G1–G7 判据、Bootstrap by episode 预注册） |
| GitHub 推送 | ⏳ 最终收口步骤 |

## 角色与文件

- 协议：`STORY_GENERATION_PROTOCOL.md`；作者简报 `BATCH1_AUTHOR_BRIEF.md`；标注简报 `ANNOTATOR_BRIEF.md`
- 隔离账本：`GENERATION_ISOLATION_LEDGER.json`（7 作者+6 标注+3 审计+2 修复 ack）
- 新题：`benchmark/stories_v2/wave4_dev/`（E301–E338 五件套+runner 副本+manifest+哈希）
- 交付报告归档：`STORY_BATCH_REPORTS/`（BATCH1_AGENT_REPORTS_DIGEST.md + 各审计/修复报告）

## 完成定义（对照指导书 §14）

Phase 2 宣告 DONE 的剩余条件：live pilot 完成 + PHASE2_PILOT_REPORT.md（差异/判别力）
+ 锚点包填充真实回复 + PHASE2_ACCEPTANCE Pilot 行刷新 + GitHub 推送。157 篇 PENDING
属 §6.2 的第二批/三批/四批工作，不阻塞本批收口。
