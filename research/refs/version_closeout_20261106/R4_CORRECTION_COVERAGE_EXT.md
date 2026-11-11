# R4 — 纠正过滤覆盖面核实与扩展（2026-11-07）

## 对象与依据

- 产品：`D:/LoveBrain` 分支 `closeout-20261106`（HEAD=d9ff3db），worktree `D:/wt-r4`（分支 `closeout-r4-tmp`），未 commit，worktree 已清理、改动已复制回主仓。
- 依据：W2 批次（`W2_F33_CORRECTIONS_FLOW_FIX.md`）给提取链加了统一纠正过滤，审计确认 scene.md 路径已过过滤，但 recent.md / memory/raw_chat.md / raw_scene.md / raw_topic.md 仍原样进入提取上下文，旧错误事实可经经验/画像回流。

## 步骤1 核实结论（读取路径清单，改动前基线）

| 路径 | W2 基线状态 | 证据 |
|---|---|---|
| `moment/scene.md` | 已过滤（`correctionFiltered`） | `KnowledgeContextReader.kt:34-35, 67-68` |
| `moment/recent.md` | **未过滤，原样进两路上下文** | 原 `KnowledgeContextReader.kt:36, 69` |
| `memory/raw_chat.md` | **未过滤**（仅 getTopicFullContext） | 原 `KnowledgeContextReader.kt:37` |
| `memory/raw_scene.md` | **未过滤**（仅 getTopicFullContext） | 原 `KnowledgeContextReader.kt:38` |
| `memory/raw_topic.md` | **未过滤**（getTopicFullContext + getVectorContext；`PromptBuilder.kt:509` 的读取属 R3 并行轨，本轮未动） | 原 `KnowledgeContextReader.kt:39, 70` |

根因：corrections 表（`memory/corrections.json`）只持 MemoryRef ID（`PROFILE/LESSON/SCENE:moment/scene.md/ONGOING:…:条目`），recent / raw_* 没有纠正身份，`filterForExtraction` 按文件级 ID 判定不可能命中。

## 步骤2 扩展设计（按事实来源/纠正身份，不做全文字符串替换）

桥接身份：scene.md 的文件级纠正（`SCENE:moment/scene.md`，`MemoryRefPolicy.isCorrected` 同判，含 MUTED 过期恢复）生效时，scene.md 此刻条目化的各条事实原文 = 「已被纠正的条目原文」。全部新逻辑收在 `CorrectionExtractionFilter`（纯函数）+ `KnowledgeContextReader`（接线+记账）：

| # | 函数 | 语义 |
|---|---|---|
| 1 | `correctedSceneFactTexts` | 纠正未生效（未纠正/MUTED 过期）→ 空集；生效 → 返回 scene.md 事实原文精确集合 |
| 2 | `sceneEntryFactTexts` / `factTextOf` | 场景条目行解析（新格式 `事实｜src=…|spk=…|subj=…`、旧格式 `事实⟨ids⟩`、纯文本），剥装饰取原文 |
| 3 | `filterSceneArchive` | **raw_scene / raw_topic 状态段条目级**：只剔除事实原文精确命中的那一条事实；同行相邻事实与 src/spk/subj 装饰逐字保留；一条不剩才整行省略；无命中的行逐字节不变 |
| 4 | `filterChatArchive` | **recent 轮次块 / raw_chat / raw_topic 对话段行级**：只剔除整行 `她：/我： + 已纠正原文`（trim 后全文相等）；「我的想法：」「我（最终回复：…）：」与近似行一律保留 |
| 5 | `KnowledgeContextReader` 四份文件接线 + `logRemovals` | `L.w` 记剔除量（路径+条数，正文不入日志——隐私红线）；raw_topic 走条目级+行级两颗串联 |

诚实登记（宁漏勿误的已知缺口）：corrections 表不持久化纠正时的原文，归档条目自身无纠正身份——**纠正时已过期归档、scene.md 里已不存在的旧事实暂不被剔除**，待归档条目级化（条目级 MemoryRef）后收口。生产数据未被改写：过滤只发生在读侧装配，四个源文件落盘内容逐字节不变。

## 步骤3 测试（15 条新增，全绿）

- `CorrectionExtractionFilterTest`（9→**18**）：事实解析双格式；无纠正/MUTED 过期→空集；纠正生效（WRONG/MUTED 未过期）→事实集合；raw_scene 条目级只剔精确命中+相邻事实装饰逐字保留；整行省略边界；无命中归档逐字节不变+空基准直通；对话行整行精确匹配剔除（块头/marker/想法行/最终回复行/近似行保留）；对话无命中逐字节直通；raw_topic 两颗过滤串联。
- `KnowledgeContextReaderCorrectionTest`（5→**11**）：未纠正四路逐字放行；raw_scene 剔命中事实、相邻事实/条目行保留；recent/raw_chat 剔对话行、块头/相邻行保留；raw_topic 状态段+对话段双路过滤且归档头分节保留；vector 上下文同过滤；WRONG+补正→标注传导且归档副本剔除。

## 硬约束遵守

只改 4 个文件：`CorrectionExtractionFilter.kt`、`KnowledgeContextReader.kt` 及对应两个测试。未触碰 `PromptBuilder.kt`（R3 并行轨在改）、champion 包、TopicRecorder/TopicSwitchGate（R5）、BatchImport/ReplyInput/解析器（R2/R6）。主仓状态核对：除本轨 4 文件与并行轨既有改动外无其他写入。

## 编译与测试证据

- worktree `D:/wt-r4`（local.properties 已拷，JDK 21 `jdk-21.0.3.9-hotspot`）：
  - `:app:compileDebugKotlin` 通过；
  - 新增 15 条 + W2 既有 18 条全绿（`CorrectionExtractionFilterTest` 18、`KnowledgeContextReaderCorrectionTest` 11、`ReflectCorrectionsFilterTest` 4，0 failures）；
  - 回归：`com.lovebrain.app.domain.*` / `domain.port.*` / `domain.prompt.*` / `data.*` 全量 `BUILD SUCCESSFUL`。
- 改动已复制回 `D:/LoveBrain` 对应 4 个路径；未 commit；worktree 与临时分支已删除。

## 验收探针（运行时建议）

1. 对某条场景事实发起 WRONG（如"她在杭州出差"）→ 触发 lessons 提取/向量重估 → `memory/lessons.md` 新批与 vector 依据不得经 raw_scene/recent/raw_chat/raw_topic 出现"杭州"；
2. 日志可见 `F3.3 extraction filter [...]: removed N corrected-fact copies（路径=N）`，正文不落日志；
3. 撤销纠正 / TODAY 跨天 → 四路恢复逐字放行。
