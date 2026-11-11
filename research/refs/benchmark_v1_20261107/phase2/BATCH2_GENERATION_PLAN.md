# BATCH2_GENERATION_PLAN · Benchmark V1 Phase 2 第二/三/四批生成计划（v2，2026-11-08 配额修正版）

> 性质：批次规划，pilot 结论出来前先行编制。`PHASE2_PILOT_REPORT.md` 盘上未产出——**pilot 结论待补**；
> 结论只触发 §8 三个规范挂点，不改本文配额与结构。输入：`DATASET_V1_FINAL_ALLOCATION.csv`
>（v2 同日配额修正版，本计划以该表为权威）、`OLD_STORY_FINAL_DISPOSITION.md`、
> `PHASE2_ACCEPTANCE.md`、`STORY_GENERATION_PROTOCOL.md` §2、`BATCH1_AUTHOR_BRIEF.md`。
> 指导书 §6.2 映射：第一批=38 篇试制（完成）；第二批=审核修订规范（完成：三审计＋修复＋三份 V1 规范）；
> 本文件批 2a/2b=指导书第三批（补齐 DEV 与 SELECTION）、批 3=第四批（独立生成 HOLDOUT）；
> 第五批（全库扫描冻结）仅 §7 留出口。**v2（2026-11-08）**：逐专题配额已按外部指导书 §1.2/§1.4/§1.5
> 权威数值入表（v1 的"指导书原件不在库内、T03–T08=24/T09=16 按规划基线推定"口径作废，
> 修正登记见分配表表尾 `#VERSION,v2_20261108`）；四类型口径以任务书转述为准。
> 硬约束：240 目标、逐专题权威配额（§1.2：T01=28/T02=36/T03=28/T04=24/T05=24/T06=22/T07=20/T08=18/T09=24/T10=16）、三分区（DEV 80/SELECTION 40/HOLDOUT 120）不动。

## 1. 现状实算（CSV 权威口径，python -I 复核）

- 已占 83：旧 45（全 DEV：ACCEPT 42＋E021 WITH_NA＋E065/LRE121 REPAIR_OVERLAY；按 2026-11-08 **DATRIX-T02-RECLASS** 重分类后计数＝T01 19/T02 0/T03 0/T04 5/T05 2/T06 0/T07 1/T08 3/T09 15/T10 0，E032→T04、E039→T01、E042→T09）＋ 新 38（DEV 36＋SELECTION 2）。
- 权威配额（指导书 §1.2/§1.4/§1.5，2026-11-08 入表）：§1.2 如上行硬约束；§1.4 HOLDOUT＝T01 6/T02 24/T03 20/T04 12/T05 12/T06 12/T07 10/T08 8/T09 8/T10 8＝120；§1.5 新题 DEV 35/SEL 40（T10 按组：DEV 2 组/SEL 2 组/HL 4 组）。
- 分区现状：DEV=81（建 36 席中 **4 席待改判 SELECTION**：T01×2/T08×1/T09×1，按 §1.5 逐专题 DEV 配额 2/4/4/4/4/5/4/3/1/4 落位）；SELECTION=2/40（G2 孪生）；HOLDOUT=0/120。
- PENDING 157 逐专题（v2 闭合解）：T01=5｜T02=32｜T03=24｜T04=15｜T05=18｜T06=18｜T07=15｜T08=11｜**T09=7**｜T10=12（=6 组孪生）；按段拆＝DEV 3（T06 缺 1＋T10 G3 组 2）｜SELECTION 34｜HOLDOUT 120。
- T10 孪生 8 组×2：G1 否定状态→DEV、G2 人物主体→SELECTION 已占；待生成 6 组＝G3→DEV（批 2a）、G4→SELECTION（批 2b）、G5–G8→HOLDOUT（批 3）。

## 2. 批次划分与逐批篇数（终分派建议，冻结批次确认）

| 批 | split | 内容 | 篇数 | episode_id 段 |
|---|---|---|---|---|
| 2a DEV 收口 | DEV | 生成 T10 孪生组 G3（2 篇）＋T06 DEV 缺口 1 篇＋执行 **4 席**新题 DEV→SELECTION 改判（T01×2/T08×1/T09×1，§1.5 闭合） | 3 | E339–E341 |
| 2b SELECTION 补齐 | SELECTION | 生成 G4 孪生 2 篇＋非孪生 32 篇；并入 2a 改判 4 篇后 SELECTION 闭合 | 34 | E342–E375 |
| （缓冲段） | — | 批 2 重做/替换预留，不预发、不开 PENDING 行 | 0 | E376–E400 |
| 3 HOLDOUT | HOLDOUT | 独立会话生成 120 篇（含 G5–G8 四组孪生＋**T09 长程 7 篇＝4×24 轮＋3×32 轮**），生成后 SEALED | 120 | E401–E520 |

逐专题闭合校验（已占＋2a＋2b＋3=终态=§1.2 目标，python -I 实算逐行 assert OK）：

| 专题 | 已占 | 2a | 2b | 3 | 终态/目标 | 专题 | 已占 | 2a | 2b | 3 | 终态/目标 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| T01 | 23 | 0 | 0 | 5 | 28/28 | T06 | 4 | 1 | 5 | 12 | 22/22 |
| T02 | 4 | 0 | 5 | 27 | 36/36 | T07 | 5 | 0 | 5 | 10 | 20/20 |
| T03 | 4 | 0 | 4 | 20 | 28/28 | T08 | 7 | 0 | 3 | 8 | 18/18 |
| T04 | 9 | 0 | 4 | 11 | 24/24 | T09 | 17 | 0 | 0 | 7 | 24/24 |
| T05 | 6 | 0 | 6 | 12 | 24/24 | T10 | 4 | 2 | 2 | 8 | 16/16 |

合计 83＋3＋34＋120=240。分派规则（v2）：批 2/3 全部篇目按权威配额逐专题直接落位（§1.5 DEV/SEL＋§1.4 HL），本表即唯一闭合解，**不再等比推分**；重分类对冲（E039 入 T01/E032 入 T04/E042 入 T09 → 三专题 HL 各 −1）与 T02 上调富余 3 落 HL（T02 HL 24→27）净和=0，登记于分配表 `#DECISION_DATRIX-T02-RECLASS`＋`#QUOTA_NOTE_RECLASS_ADJUST`＋`#QUOTA_NOTE_T02_V2`；改判具体篇目（E301–E304 取 2、E329–E332 取 1、E337/E338 取 1）由冻结批次终裁。分区闭合：DEV=45＋32(建留)＋3(2a 生成)=80；SELECTION=2(G2)＋4(改判)＋34(2b 生成)=40；HOLDOUT=120。

## 3. T09 长程轮数分布（v2 权威配额闭合解：新 9＝5×24＋4×32）

- 权威配额（§1.2）下 T09＝24＝旧 15（14 旧强＋E042 重分类入题：24 轮×6＋32 轮×3＋40 轮×5＋E042 8 轮）＋新 9：已建 E337(24 轮)＋E338(32 轮)，**PENDING=7 全 HOLDOUT＝4×24 轮＋3×32 轮（E506–E512）**。§3.3"新 10＝6×24＋4×32"在闭合 9 篇下修正为**新 9＝5×24＋4×32**（24 轮档 −1、32 轮档维持 4；E042 入 T09 占 1 旧席 → 新侧 HL 8→7 对冲，见分配表 `#QUOTA_NOTE_T09_V2`）。
- **批 3 不再是"全 12 轮"**：HOLDOUT 含 T09 长程 7 篇（24/32 轮），v1"批 2a/2b/3 全部 12 轮"表述作废；批 2a/2b 仍全 12 轮。
- rubric T09"长剧长度梯度 12→24/32/40"承载＝DEV 旧股（24×6＋32×3＋40×5）＋新 E337/E338＋批 3 长程 7 篇；SELECTION 侧 T09=1 席由 E337/E338 改判 1 篇承载（建议 E338(32 轮)→SELECTION 提供更强长程判别位、E337(24 轮)留 DEV，冻结批次可换，不影响闭合）。

## 4. T10 剩余 6 组类型分配（四类型×2 组闭合，建议）

类型账本：总 8 组，已用否定状态(G1)＋人物主体(G2) 各 1 → 剩余必为 **时间变化×2、人物主体×1、否定状态×1、目标约束×2**（算术强制，无其他解）。建议落位：

| 组 | split/批 | 类型 | 理由 |
|---|---|---|---|
| G3 | DEV/批 2a | 目标约束 | DEV 是机制迭代场，目标变更跟随最具开发诊断价值；不与 G1 同类型 |
| G4 | SELECTION/批 2b | 时间变化 | 时间扰动最机械可判，适合候选选择集；不与 G2 同类型 |
| G5–G8 | HOLDOUT/批 3 | 时间变化/人物主体/否定状态/目标约束 各 1 组 | 独立集四类型覆盖最大化 |

闭合校验：否定状态=G1+G7、人物主体=G2+G6、时间变化=G4+G5、目标约束=G3+G8，各 2 组=8 组=16 篇。rubric 五词轮换（time/subject/negation/status/goal）映射：否定状态=negation+status 合并族（G1"否定/取消变体"先例）。组规约：同组两篇同一作者会话产出（保同构）、同批生成同批审；扰动位唯一且自洽（NEW_STORY_SPEC §7.1 负清单）；孪生组不跨分区（guard G2）。

## 5. episode_id 分配（段级；段内逐篇生成时定）

- 批 2a/2b 用 E339 起连续段：E339–E340（G3）、E341（T06 DEV 缺口）、E342–E343（G4）、E344–E375（32 篇非孪生 SELECTION：T02×5/T03×4/T04×4/T05×6/T06×5/T07×5/T08×3）。
- 缓冲段 E376–E400：批 2 重做/替换专用，不预发；启用须在分配表登记新行。
- 批 3（HOLDOUT 专用段，防混）：E401–E520（120 篇）＝普通组 LG-E4xx（E401–E505 按专题 T01×5/T02×27/T03×20/T04×11/T05×12/T06×12/T07×10/T08×8）＋T09 长程 E506–E512（4×24 轮＋3×32 轮）＋孪生组 LG-T10-G5…G8（E513–E520）。
- 原子跨篇引用限于本批段内更早篇（validator STORY_ORDER 规则）；批 2 段与批 3 段互不引用。分配表 v2 已将 157 行 PENDING 预编为本节 E-id（episode_id/primary_topic/lineage_group_id/twin_group/turns 落列，split 列维持 PENDING、终分派写入 allocation_note）；段位启用时逐行回写 domain/status 并核销 PENDING 态，不另起新 id。

## 6. 三角色隔离与开工 ack（每批干净会话）

- 通用（同 STORY_GENERATION_PROTOCOL §1＋BATCH1_AUTHOR_BRIEF §0）：作者禁读 logs/、candidates/、判决与评审文档、GOLD_SIDECAR、其他 episode 正文（唯一结构样例 wave3/E117）；**批 2 起新增明确禁读：批 1 各篇 audit.md 正文与 C1/C2/C3 审计/修复报告（STORY_BATCH_REPORTS/）**。作者可读＝本批作者简报、NEW_STORY_SPEC_V1、TOPIC_RUBRIC_V1 本专题条目、E117 样例。标注禁读运行表现/失败案例/未冻结评分结果；审计只读冻结后材料。同篇 A/B/C 不同会话；每代理 5–10 篇。
- 批 2a/2b：作者/标注/审计全新会话（不得复用批 1 任何会话）；ack 续盘上计数器（GIA-202611xx-ROLE-nn）：AUTHOR-A8…、ANNOTATOR-B7…、AUDITOR-C4…、REPAIRER-R3…。任务书示例"AUTHOR-B1"与盘上 ANNOTATOR-B 系冲突，本计划保持盘上角色字母制，差异登记冻结批次确认。
- 批 3（HOLDOUT）：ack 用 **H 段**（GIA-202611xx-AUTHOR-H01…，ANNOTATOR/AUDITOR/REPAIRER 同段 H 编号）；H 段会话从未担任且此后不得担任任何 DEV 系角色；**HOLDOUT 作者与 DEV 作者（批 1/2a/2b）物理不同会话为硬条件**；禁读 DEV/SELECTION 全部正文与判词。标注/审计亦 H 段独立会话。生命周期：生成→标注→审计→SEALED→only_once，任何按题回修=当轮变异作废（PHASE3_PREREGISTRATION 已预注册）。

## 7. 验收联动（每批同一机械链，全程零模型调用）

fix_wave4_schema_v1 落盘 schema 为模板（新篇一次成型，不做事后翻转）→ `validate_story_v2` ×3（按篇 turns 配置：12T／批 3 T09 长程 24T/32T，errors=0）→ `dedupe_wave`（near-dup=0；孪生组预期内近重如实登记）→ `build_gold_sidecar`＋Marker QC verbatim_violations=0 → `build_wave_manifest`＋三层哈希（篇级/manifest/sidecar）→ `lineage_check --split-csv`（0 违例）→ `story_custody` 注册（批 2a/2b 入池；批 3 全 120 篇 SEALED）→ `custody_split_guard`（G1 组不跨分区/G2 孪生不跨分区/G3 HOLDOUT 未消费/G4 PENDING 计数）。收口件：批 2a＝改判 4 席落表＋guard 全绿；批 2b＝DEV 80/SEL 40 闭合＋guard `--expected-pending 120`；批 3＝guard `--expected-pending 0`＋**HOLDOUT_SEAL_REPORT**（现无现成工具，登记为批 3 交付件：guard G3 全绿＋custody lifecycle=SEALED×120＋GOLD_INDEX/manifest 哈希对账汇总，落 phase2/）；随后进入第五批全库扫描冻结（不在本文件范围）。

## 8. pilot 结论联动点（仅三个规范挂点；配额/批次结构/分区一律不动）

`PHASE2_PILOT_REPORT.md` 交付后只允许触发：
1. **T02 行为锚措辞**（TOPIC_RUBRIC_V1 §0.5 0/1/2 档判据措辞微调）；
2. **万能句清单扩充**（T05/T06 万能句诱惑位所依据的万能句族清单追加）；
3. **万能句/词族识别率工具词表更新**（judge interact 负对照识别词表，按 pilot 误伤/漏检证据）。
三者均须版本化＋已有答卷离线重评（rubric §7 纪律）；均不触发本文件任何数量与结构。

## 9. 风险登记（开工前处理或冻结批次裁决）

1. **跨作者人名重名 12 组**（C1/C2 审计报告清单）：批 2a/2b 开工前由冻结批次统一改名（episode/timeline/message_index 同步＋validate/哈希/manifest 全链复跑）；批 3 简报启用唯一人名池并在 validate 加跨篇重名检查（OLD_STORY_FINAL_DISPOSITION §6-4 建议）。
2. **T02 +3 富余落点——已裁决（2026-11-08）**：T02＝36 全新题（3 旧题经 DATRIX-T02-RECLASS 移出、旧题额度不复用，总 240 由新题补齐）；富余 3（§1.5 基准 SEL5＋HL24 之上）落 HOLDOUT＝T02 HL 24→27——唯一同时保持 §1.5 SEL=40 与 §1.4 HL=120 段级闭合的落点；对冲账：T01/T04/T09 HL 各 −1（重分类入题压力），净和=0。登记于分配表 `#DECISION_DATRIX-T02-RECLASS`＋`#QUOTA_NOTE_T02_V2`＋`#QUOTA_NOTE_RECLASS_ADJUST`；冻结批次复核。
3. **DEV 超员——已按 §1.5 闭合（2026-11-08）**：逐专题 DEV 配额（T01 2/T08 3/T09 1 等）入表后改判＝**4 席**（T01×2/T08×1/T09×1），DEV PENDING 3 席（T06 缺 1＋T10 G3 组 2）由批 2a 生成，DEV=80 闭合；v1"回收 3 席"推定与 #QUOTA_NOTE_DEV"超 1 席待回收"口径作废；改判篇目冻结批次终裁。
4. **domain 两口径**：旧题 12 域粗粒度 vs 新题逐篇场景细粒度并存（CSV 实测两套词表）；冻结批次归一（建议 12 域为主口径、场景标签降为注记）；归一前新篇 domain 沿用批 1 写法。
5. **HOLDOUT 长程——已消解（2026-11-08）**：T09 权威配额 24 下批 3 含长程 7 篇（4×24＋3×32，§3），v1"批 3 全 12 轮""HOLDOUT 无 24+ 轮剧集"表述作废；批 3 简报须按篇标注 12/24/32 轮。
6. **pilot 消费边界**：pilot2 三进程已消费 38 新题＋E063/LRE121（开发暴露，不影响 DEV/SELECTION 资格）；批 3 生成至冻结期间任何 pilot/候选禁触 HOLDOUT（guard G3 强制）。
7. **本次配额修正溯源**：初版分配表推定配额与指导书 §1.2 不一致（v1 表尾 #QUOTA_NOTE_SOURCE 自认"T03–T08=24 推定、T09=16 由存量闭合、指导书原件不在库内"）；2026-11-08 按 §1.2/§1.4/§1.5 权威配额重算 PENDING（总数 157 不变、逐专题重分布 T01 −1/T02 +3/T03 +4/T04 −1/T06 −2/T07 −4/T08 −6/T09 +7）并全文同步本计划 v2；版本记录见分配表表尾 `#VERSION,v2_20261108`；引用 v1 数字的外部材料以 v2 为准。

## 10. 顺序与依赖

批 2a/2b 可并行开工（2b 收口承接 2a 改判）；批 3 前置＝风险 1 人名池冻结＋批 2a/2b 审计收口；批 3 收口（HOLDOUT_SEAL_REPORT）→ 第五批全库扫描冻结 → Phase 2 DONE 判定（PHASE2_ACCEPTANCE 刷新）。本计划开工不依赖 pilot 结论（§8 挂点独立生效）。
