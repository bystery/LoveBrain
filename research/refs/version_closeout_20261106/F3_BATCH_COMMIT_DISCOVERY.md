# F3 外部审计第三轮：批量导入两改善项（局部最小修改）

- 日期：2026-11-06
- 产品仓：D:/LoveBrain（分支 closeout-20261106，HEAD=8cafeec）
- worktree：D:/wt-f3（detached 8cafeec，已清理）
- 性质：写代码改善（非 commit）；与 F1/F2 平行轨互不触碰（TopicSwitchGate/TopicRecorder/champion 包/OngoingContextSelector 零改动）

## 审计问题 1：非原子批量提交

**痛点**：`LoveBrainViewModel.importBatchMessages()` 逐条投 `ComposerStore.Intent.AddMessage`，几十条导入 = 几十次列表重建 + 几十次 `onContentChanged`（stale 判定钩子），中间态无人消费。

**修法**（不建第二套 Composer，单条路径语义原样保留）：
- `ComposerStore` 新增 `Intent.AddMessages(drafts: List<BatchImportDraft>)` + 私有 reducer `addMessages()`：**一次校验、一次追加、一次通知**。校验与逐条同一判据（空/全空白正文不落列表，有效条目为零 = 零追加零通知）；`onContentChanged` 恰好一次；追加在尾部、顺序即预览顺序，不触碰编辑位/草稿/备注/捕获角色。元素类型用 `BatchImportDraft`（构造期硬挡 IDEA 与空正文），军师备注通道（F1/F3 边界）在类型上进不了批量。
- `importBatchMessages()` 改为单次 `composer.accept(AddMessages(drafts))`。
- KDoc 同步更新（ComposerStore / BatchImportDraft）。

**测试**（新增 `app/src/test/java/com/lovebrain/app/feature/composer/ComposerStoreBatchImportTest.kt`，6 格，全 JVM 无 Robolectric/mockk）：
1. 5 条批量一次落、顺序/角色逐位对齐、`onContentChanged` 差值恰好 1；
2. 空批量零追加零通知（无"清了再还原"中间态）；
3. 单条空白正文 no-op（批量判空的镜像证人）；
4. IDEA/空白草稿构造期即抛 IllegalArgumentException（校验第一道防线在类型上）；
5. 整批投与逐条投最终（角色、正文）序列逐位相同（批量不比逐条多特权）；
6. 批量追加不触碰编辑位/草稿/备注/捕获角色，且恰标一次 stale。

## 审计问题 2：入口藏得太深（➕ 长按不可发现）

**修法**（照抄既有 first-run hint 范式：`roundScopeHintShown` + NoticeBoard 回执格；不加按钮、不加常驻控件、不改输入行信息密度）：
- `SecurePrefs` 新增 `batchImportHintShown` bool（键 `batch_import_hint_seen`），与 `roundScopeHintShown` 同款一次性标记格，不进 SettingsStorePort 端口面。
- `LoveBrainViewModel`：构造期接 `readBatchImportHintShown`/`writeBatchImportHintShown` 一对函数（AppModule 接线，缺省回落 = 读默认/写丢弃、会话内仍只弹一次）；`batchImportHintVisible: StateFlow<Boolean>` 只读出口；`maybeShowBatchImportFirstHint(hintText)`（首次进回复输入面板时由屏上调用，投 `NoticeBoard.Channel.Knowledge` 一次性短提示并落旗标）；`markBatchImportDiscovered()`（只收旗标不投通知）。三条不再提示的判据：提示已给过（落盘）、**长按过 ➕**（面板 `onBatchImport` 同拍 mark）、**完成过一次导入**（`importBatchMessages` 同拍 mark）。
- 文案进 `strings.xml`：`batch_import_first_hint` =「长按「＋」可批量粘贴导入聊天记录」（屏上 `stringResource` 解析后交给 VM——VM 无 Context）。
- `LoveBrainPanelScreen`：回复页槽位在 `composerMode == REPLY` 时 `LaunchedEffect(Unit)` 投一次提示；`onBatchImport` 加 `markBatchImportDiscovered()`。通知位一次只显示一条、到点自散，屏幕无新增常驻节点。

**测试**（新增 `app/src/test/java/com/lovebrain/app/viewmodel/BatchImportFirstHintTest.kt`，7 格，骨架照抄 `RoundScopeFirstHintTest`）：首次投递一次 + 落盘 + 出口翻 false；二次进面板不重弹；长按 mark 后不再弹；完成导入 mark 后不再弹；导入本体照常落真实对话通道（composer.messagesNow==2）；落盘旗标构造期读回后首进面板也不弹；未接落盘口子时会话内仍只弹一次。

## 架构守卫适配

`ViewModelStateOwnershipTest` 台账按其自身规则登记两颗新 VM 状态（`VAR batchImportHintShown` / `FLOW _batchImportHintVisible`），登记理由与 `roundScopeHintShown` 同一条：跨会话一次性记录、盘上主人在 SecurePrefs、放 store 会把纯状态持有者拖成第二个持久化主人。其余三把守卫（UiStringLiteralBudgetTest / PackageDependencyTest / OddShapeOwnershipTest）零适配通过。

## 编译与测试证据

- worktree：`compileDebugKotlin` 绿；`testDebugUnitTest` **全量绿**（BUILD SUCCESSFUL 6m40s，含新增 13 格与全部既有 architecture/data/di/ui.panel/feature.composer/viewmodel 组）。
- 已复制回主仓（10 个文件，diff vs 8cafeec 与 worktree 逐字一致；F1/F2 平行轨文件未被触碰）。不 commit。

## 触碰文件清单

主代码：`ComposerStore.kt`、`BatchImportDraft.kt`（仅 KDoc）、`LoveBrainViewModel.kt`、`SecurePrefs.kt`、`di/AppModule.kt`、`ui/panel/LoveBrainPanelScreen.kt`、`res/values/strings.xml`
测试：`ComposerStoreBatchImportTest.kt`（新）、`BatchImportFirstHintTest.kt`（新）、`ViewModelStateOwnershipTest.kt`（台账登记）
