# S3 — 外部审计第二轮 P1 双漏口修复：reflect 画像通道 raw_topic 过滤 + 归档后过滤依据持久化（2026-11-07）

## 对象与依据

- 产品：`D:/LoveBrain` 分支 `closeout-20261106`（HEAD=6ed7fbc），worktree `D:/wt-s3`（分支 `closeout-s3-tmp`），未 commit，worktree 已清理、改动已复制回主仓（复制前后均已核对：主仓中 S1/S2 并行轨的未提交改动一概未触碰）。
- 依据：外部审计第二轮指出两个 P1 纠正回流漏口（R4 批次之后的遗留）：
  1. **遗漏 A**：`PromptBuilder.buildReflectUserPrompt()` 画像/经验文件已过纠正过滤，但 `memory/raw_topic.md` 原样进画像更新 prompt——被纠正的旧事实可经话题档案回流进画像重写。
  2. **遗漏 B**：`CorrectionExtractionFilter.correctedSceneFactTexts()` 靠"当前 scene.md 内容"现算"被纠正条目原文"对撞基准；旧事实随话题切换归档进 raw_topic、scene.md 清空后，纠正判据消失，归档里的旧值重新漏回提取材料。

## 遗漏 A 修法（reflect 通道同颗过滤）

- R4 读取侧的 raw_topic 两段串联（`filterSceneArchive` → `filterChatArchive`）从 `KnowledgeContextReader` 私有方法上收为 `CorrectionExtractionFilter.filterTopicArchive()` 公开单一真源；`KnowledgeContextReader` 改为薄转发，reflect 侧调同一颗。
- `buildReflectUserPrompt()`（仅动 reflect 区段）新增：读 `moment/scene.md` → `correctedSceneFactTexts`（含遗漏 B 持久依据）→ `filterTopicArchive(rawTopic, correctedFacts)`，过滤后文本进 `PromptReflectSection`。未纠正时逐字放行（与 R4 行为一致）；谈心分析维持原样读取。
- 谈心（lessons/counseling）与回复装配主函数未动。

## 遗漏 B 修法（持久依据，存储层可达，已修；不欠账）

**探查结论：存储层可达，不必动 `LoveBrainViewModel.kt`。** 纠正产生链是 UI → ViewModel → `KnowledgeRepository.saveCorrection` → `KnowledgeMemoryStore.save/undo`（data 层，既有架构棘轮允许 data→domain.prompt import；`StorageBoundaryOwnershipTest` 对记忆格只禁 File/锁/直接落盘，`storage.read`/`writeTransaction` 本就是它的合法能力）。故按"能则持久化"分支执行：

| # | 位置 | 内容 |
|---|---|---|
| 1 | `KnowledgeMemoryStore.save` | memoryId 命中 scene.md **文件级**纠正（稳定 ID `SCENE:moment/scene.md` 及旧格式 `:hash` 后缀，判据 `isSceneFileCorrection`）时，经 `storage.read` 读当时 scene.md，用 `sceneEntryFactTexts` 取条目原文，与已有依据**取并**（重复纠正累积，旧话题的被纠正原文不因覆盖漏回），落进新文件 `memory/corrected_scene_facts.txt`（一行一条；来自单行条目，无换行转义问题）。与 corrections.json、revision **同一次写事务**，顺序：纠正记录 → 依据 → revision；依据写失败同样不推进 revision。并集为空（scene.md 空、无旧依据）→ 不写文件，纠正本身照常落盘 |
| 2 | `KnowledgeMemoryStore.undo` | 撤销 scene 文件级纠正 = 恢复可信注入 → 同事务写空依据文件；撤销其他纠正不动依据 |
| 3 | `correctedSceneFactTexts` | 新增第 4 参 `persistedFacts`：纠正**当前生效**（`MemoryRefPolicy.isCorrected` 同判，MUTED 过期恢复同判）且持久依据非空 → 优先采用（累积超集）；依据为空 → scene.md 现算降级为**旧数据兜底**；纠正未生效 → 一律空集（撤销即恢复放行） |
| 4 | 读取侧接线 | `KnowledgeContextReader.getTopicFullContext/getVectorContext` 与 `PromptBuilder.buildReflectUserPrompt` 经知识库通用读口读依据文件、`decodePersistedFacts` 解出集合后传入 |

**诚实登记的残余缺口（不变更本轮结论"已修"）**：① 本功能上线前保存的纠正没有持久依据，若其 scene.md 已被归档清空，只能按宁漏勿误放行（兜底路径原样）；② 归档条目仍无条目级纠正身份，非 scene 源（如 ONGOING）的旧值回流不在本格射程；③ 依据文件按精确全文匹配，跨话题恰好同文的正确事实理论上存在误剔可能（概率极低，与 R4 的宁漏勿误口径一致，此处方向相反、量级更小）。生产数据未被改写：写入只发生在纠正保存/撤销的既有事务点，过滤仍只发生在读侧装配。

## 硬约束遵守

- 改动文件（8 个）：`domain/PromptBuilder.kt`（仅 reflect 区段）、`domain/prompt/CorrectionExtractionFilter.kt`、`domain/KnowledgeContextReader.kt`、`data/KnowledgeMemoryStore.kt`（遗漏 B 存储层持久化点，任务书"能则持久化"明确授权）+ 4 个对应测试。
- **未触碰**：`LoveBrainViewModel.kt`（S1 唯一作者，核实无需动它）、GenerationEngine/champion 包、TopicRecorder/TopicSwitchGate/OngoingContextSelector、BatchImport/ReplyInput/LoveBrainPanelScreen。主仓里并行轨的未提交改动（GenerationEngine/ChampionEngine/ViewModel/PanelScreen 等）逐字节原样。
- 架构棘轮全绿：`PackageDependencyTest`（data→domain.prompt 不在禁单）、`StorageBoundaryOwnershipTest`（记忆格零存储能力三条规则未破：无 `File(`、无锁、无直接落盘）。

## 编译与测试（worktree D:/wt-s3，全绿后回写主仓）

- `compileDebugKotlin` 通过；`JAVA_HOME=jdk-21.0.3.9-hotspot`，`local.properties` 已就位。
- 测试三组全绿（88 条含既有回归）：
  - **W2**：`ReflectCorrectionsFilterTest`（4→7，新增：raw_topic 无纠正逐字进 reflect；scene 生效纠正→归档旧值剔除+相邻事实/对话行/归档标题保留；归档清空后持久依据仍挡旧值）、`CorrectionExtractionFilterTest`（18→25）。
  - **R4 回归**：`KnowledgeContextReaderCorrectionTest`（11→13，新增：持久依据在 scene.md 归档清空后仍过滤 raw_topic/raw_scene 两路上下文；无依据且 scene 空→宁漏勿误一字不动）、既有 R4 断言逐条未动全绿。
  - **S3**：`CorrectionExtractionFilterTest` 新增（filterTopicArchive 与手动串联逐字一致+空基准直通；持久依据归档后存活/未纠正不算数/优先于现算/空则兜底；isSceneFileCorrection 稳定+legacy hash+反例；encode/decode 往返）；`KnowledgeMemoryStoreTest`（6→12，新增：scene 纠正同事务持久原文+三写顺序；legacy hash ID 同持久；非 scene 纠正不写依据且顺序与旧实现一致；空 scene 不写文件；重复纠正累积不覆盖；撤销清空依据；撤销非 scene 不清空）。
  - 护栏：`MemoryCorrectionTest`、`StorageBoundaryOwnershipTest`、`PackageDependencyTest`、`PromptByteFreezeBaselineTest` 全绿。
- 过程记录：首轮 1 条断言写错（把手动串联的剔除量只算了 chat 段），修正后全绿；非产品代码问题。

## 版本分离登记

- 考场/评分/题目/API：零变化。本批只动提取链读侧装配与纠正持久化点。
- harness 版本：`memory/corrected_scene_facts.txt` 是新增落盘文件，仅由 `KnowledgeMemoryStore` 读写；旧库无此文件时走现算兜底，升级无迁移步骤。
