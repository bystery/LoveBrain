# S2 — 批量导入浮层焦点释放修复（审计第二轮 P0，06 续）

日期：2026-11-06　分支：closeout-20261106（基于 6ed7fbc，未 commit）　产品：D:/LoveBrain

## 问题

批量导入浮层只接了"进入编辑"，没接"退出编辑"：

- `BatchPasteField` 触摸 → `onInputIntent("batch_import")` → `PanelInputFocusOwner` 进 EDITING；
- 浮层关闭（遮罩 onDismissRequest / 取消键 / 提交成功 `onSubmit`，三条路径都汇到
  `surface.closeBatchImport()`）时，`LoveBrainPanelScreen.kt` 没有监听 `surface.batchImportOpen`
  并移交焦点；
- `NON_REPORTING_INPUT_IDS` 只含 settings / intent_editor / scheme_adjust，缺 `batch_import`；
- `BatchPasteField` 自身没有 `onFocusChanged(false)` 回报宿主。

⇒ 关闭浮层后宿主仍认为 batch_import 持焦：悬浮窗卡在 EDITING、键盘收不起来。

## 修法（保持最小，复用既有范式，不另建焦点管理器）

1. `ui/panel/LoveBrainPanelScreen.kt`
   - `NON_REPORTING_INPUT_IDS` 增补 `"batch_import"`（3→4 条）。选择"进名单"而非
     "补 onFocusChanged 回报"：BatchPasteField 与 settings/intent_editor/scheme_adjust 同属
     "只有进入编辑信号、没有失焦回报入口"的组件族（它走 pointerInput 边沿触发上报，
     连 `LbFieldInput` 的链路都不一样），与现有输入框 `reply/counseling` 的
     `onFocusChanged` 回报模式不一致；沿用名单模式与同族一致。
   - 新增 `LaunchedEffect(surface.batchImportOpen) { currentHandOffInput() }`：与
     `surface.settingsOpen` 同款——开/关都清一次（打开时主面被盖在下层不该持焦，
     关闭时浮层内粘贴框跟着退场也不该留）。三条关闭路径汇于同一状态位，一处接住即全覆盖；
     走唯一交接出口 `handOffInput()`（hide IME → clearFocus(force=true) → 按
     `onInputFocusChanged(id,false)` 逐颗点名宿主），不拼第二份。
2. `BatchImportSheet.kt`：未改动。它的关闭路径已收敛到 `closeBatchImport`，问题只在宿主侧
   没接关闭信号；粘贴框的进入编辑上报（审计 R1-3）保持原样。

## 测试

- `ui/panel/PanelInputFocusHandoffTest.kt`（源形状尺）：
  - 交接点计数 5→6；名单计数 3→4；
  - 新增 `theBatchImportSheetOpeningAndClosingHandsFocusBackThroughThatExit`：断言
    `LaunchedEffect(surface.batchImportOpen)` 存在且体走唯一出口，配错 key 反向证人。
- `service/PanelInputFocusOwnerTest.kt`（JVM 状态机尺）：
  - 新增 `批量导入浮层关闭后宿主焦点状态回归 PASSIVE`：`onInputIntent("batch_import")` 进
    EDITING → 点名 `onInputFocusChanged("batch_import", false)` → 所有权清空、mode 回
    PASSIVE、窗口 flags 落回带 NOT_FOCUSABLE 的 PASSIVE 那一份。

## 验证

- worktree `closeout-s2-tmp`（D:/wt-s2，local.properties 已复制，JAVA_HOME=jdk-21.0.3.9-hotspot）：
  - `:app:compileDebugKotlin` 通过；
  - `testDebugUnitTest`：`PanelInputFocusHandoffTest` 6/6、`PanelInputFocusOwnerTest` 13/13、
    相关回归 `BatchImportWiringTest` 全绿；
- 文件 diff 校验后复制回主仓（实际为主仓编辑→worktree 编译→identical 校验），
  worktree 与临时分支已删除，主仓留 3 个未提交修改文件。

## 约束遵守

只改 `LoveBrainPanelScreen.kt` + 两个测试；未触碰 `BatchImportSheet.kt`、
LoveBrainViewModel / GenerationEngine / champion（S1）、PromptBuilder / CorrectionExtractionFilter（S3）、
TopicRecorder / OngoingContextSelector（S4）、BatchTranscriptParser。未 commit。
