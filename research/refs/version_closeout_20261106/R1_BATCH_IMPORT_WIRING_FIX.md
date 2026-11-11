# R1 — 批量导入三处接线修复（外部审计核实+修复）

- 日期：2026-11-06
- 基线：D:/LoveBrain 分支 `closeout-20261106`，HEAD=`d9ff3db`
- 工作方式：worktree `closeout-r1-tmp`（D:/wt-r1，已清理）内修改+编译+测试，改完只回写本次改动的 4 个文件，未 commit。
- 硬约束遵守：未改 `BatchTranscriptParser.kt`（R7 轨并行）、未碰 champion 包 / PromptBuilder / KnowledgeContextReader / TopicSwitchGate。

## 审计项 1 — P0 陈旧解析结果（核实：属实）

- **审计声称**：`ui/panel/reply/BatchImportSheet.kt` 将粘贴原文 `raw` 与解析结果 `parsed` 分开保存；用户点解析后再编辑原文，只更新 `raw`，`parsed` 不失效，提交按钮仍按旧结果导入。
- **核实**：属实。原 88 行 `onValueChange = { raw = it }`；`parsed` 只在解析 chip 的 onClick 里被覆盖（原 100-106 行），提交 onClick（原 176-184 行）只读 `entries`（= `parsed.orEmpty()`），原文变更后预览与提交完全脱钩。可复现：粘贴 → 解析 → 逐条指定 → 任意编辑原文 → 「导入 N 条」仍可点且按旧条目提交。
- **修法**（选了侵入更小的"原文变化即失效"方案）：新增 `parsedRaw` 状态记住解析时刻的原文；`onValueChange` 里 `parsedRaw != null && next != parsedRaw` 即清 `parsed`/`warnings`/`parsedRaw`——预览当场收回、提交回到禁用，chip 回到「解析预览」，用户必须重新解析。重新解析时 `parsedRaw = raw` 绑定新版；名字/孤儿归属与移除集合仍由既有解析 onClick 统一重置。未动解析器与提交流程本体。
- **测试**：新增 `BatchImportWiringTest.editingTheRawTextAfterParsingInvalidatesTheStaleParseResult`（JVM/Robolectric）：解析后 pick_speaker 证人 1 份、chip 在「重新解析」档 → 追加编辑原文 → 预览与「重新解析」归零、chip 回「解析预览」→ 重新解析后预览恢复。

## 审计项 2 — P0 无障碍长按无动作（核实：属实）

- **审计声称**：`ui/panel/reply/ReplyInput.kt` 约 730-735 行，普通手势长按打开导入界面，但无障碍语义 `onLongClick` 只 `return true` 未调用 `batchImport()`。
- **核实**：属实。原 735 行 `.semantics { onLongClick(batchImportLabel) { true } }`——TalkBack 用户触发动作时手势路径（`detectTapGestures(onLongPress = { batchImport() })`）与语义路径行为不一致，语义路径什么都不做却报告"已处理"。
- **修法**：语义动作体改为 `{ batchImport(); true }`，两条路径同一行为；未新增/删除任何语义节点（`ComposerAddButtonGatingTest` 的可点节点数判据不受影响，回归通过）。
- **测试**：`theAccessibilityLongClickActionActuallyOpensTheBatchImportSheet`——从语义树按 a11y 资源名（`a11y_batch_import`）定位那颗长按节点，直接调用 TalkBack 执行的那份动作，断言 `imports==1` 且返回 true；反向证人 `withoutBatchImportEntryThereIsNoLongClickSemantics`——`onBatchImport=null` 时树上无该名字的长按入口。注：语义树里其他控件（文本选择等）也声明 OnLongClick，故按 label 过滤而非全树计数。

## 审计项 3 — P0 键盘/焦点接线缺口（核实：属实）

- **审计声称**：`BatchImportSheet` 的 `BasicTextField`（约 200-221 行）没接悬浮窗编辑意图上报（`onInputIntent`），窗口默认 `FLAG_NOT_FOCUSABLE`，IME 弹不出来。
- **核实**：属实。悬浮窗焦点契约（`PanelInputFocusOwner`：PASSIVE=NOT_FOCUSABLE / EDITING=可聚焦）靠 `onInputIntent(inputId)` 边沿触发上报；`PanelTextInput`、`LbFieldInput`、IntentEditorSheet 等都已接入，`BatchPasteField`（原 202-222 行）是一颗裸 `BasicTextField`，无任何上报，宿主 `LoveBrainPanelScreen` 调 `BatchImportSheet` 时也没传。批量导入浮层在面板根部渲染（悬浮窗内），因此粘贴框永远拿不到键盘。
- **修法**：`BatchImportSheet` / `BatchPasteField` 各增 `onInputIntent: (() -> Unit)? = null` 参数，粘贴框容器上按 `PanelTextInput` 同一范式挂边沿触发（`awaitEachGesture { awaitFirstDown(requireUnconsumed = false); onInputIntent() }`，H4：每次手势只报一跳，不抖窗口 flags，不消费事件不挡输入）；宿主接线 `onInputIntent = { onInputIntent("batch_import") }`，进既有 `PanelInputFocusOwner.onInputIntent` → PASSIVE→EDITING 链路。null 默认值与全站惯例一致（普通 Activity 不需要该信号）。
- **测试**：`touchingThePasteFieldReportsAnInputIntent`——点击粘贴框触发 `onInputIntent` 回调，两次独立手势各报一次（边沿触发、不重复、不丢失）。

## 编译与测试证据

- worktree 内 `:app:compileDebugKotlin` 通过。
- `:app:testDebugUnitTest` 全绿：
  - `BatchImportWiringTest`（新增，4/4）
  - `ComposerAddButtonGatingTest`（回归，3/3——验证 ➕ 语义/可点节点合同未被本次改动破坏）
  - `BatchTranscriptParserTest`（回归，31/31——解析器本体零改动）

## 改动文件（已回写主仓对应路径）

| 文件 | 内容 |
|---|---|
| `app/src/main/java/com/lovebrain/app/ui/panel/reply/BatchImportSheet.kt` | 审计 1（parsedRaw 失效）+ 审计 3（onInputIntent 参数与粘贴框接线） |
| `app/src/main/java/com/lovebrain/app/ui/panel/reply/ReplyInput.kt` | 审计 2（onLongClick 语义动作真正调用 batchImport） |
| `app/src/main/java/com/lovebrain/app/ui/panel/LoveBrainPanelScreen.kt` | 审计 3 宿主侧接线（`onInputIntent("batch_import")`） |
| `app/src/test/java/com/lovebrain/app/ui/panel/reply/BatchImportWiringTest.kt` | 新增守卫测试（4 格） |

## 备注

- 测试写法上有两处环境事实记录：compose-ui-test 1.6.8 里 `click`/`swipeUp` 是 `TouchInjectionScope` 的顶层扩展（须显式 import）；`performTextInput` 沿光标追加而非整体替换，故"重输同一版原文不误清预览"一格无法用 SetText 模拟，等值守卫（`next != parsedRaw`）保留为防御性判据，由失效主测试覆盖。
- 未 commit（按任务约束），改动留在主仓工作区，与本轮其他并行轨（R2/R5/R6/R7）的未提交改动共存。
