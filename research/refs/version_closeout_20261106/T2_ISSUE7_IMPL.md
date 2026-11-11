# T2 · Issue #7「复制粘贴支持多条对话记录」实现记录（2026-11-06）

分支：`closeout-20261106`（D:/LoveBrain）。状态：代码完成，JVM 单测全绿；**未做真机验证**（见文末清单）。

## 一、设计要点

1. **入口**：**长按 ➕**（`AddMessageButton` 外层 48dp 热区盒上的 `pointerInput` 长按 +
   `onLongClick` 无障碍动作）。单行输入框布局、行 1 宽度预算、语义树可点节点数**一寸未动**
   ——长按不声明 OnClick，`ComposerAddButtonGatingTest` 钉的"空草稿不可点 / 一次点击恰好一条"判据原样成立。入口回调 `ReplyInput(onBatchImport = …)`，传 null 即无入口（与「仅看本轮」同一接线惯例）。
2. **浮层**：`BatchImportSheet`（`LbModalSheet`，面板根部渲染，与意图编辑浮层同层）。
   粘贴多行 → 「解析预览」→ 逐条显示 发言人/时间/正文 → 每条用 她/我 两颗 chip 显式指定 → 「导入 N 条」。
3. **发言人归属**：解析器只交出文本里写着的昵称；昵称→她/我 由用户点击指定，**同一昵称一次指定全批生效**；未识别条目按序号单独指定。**没有任何默认 HER 路径**：全部指定前提交键禁用。
4. **提交通路（F1/F3 边界）**：`BatchImportDraft(role, content)` 构造期 `require(role != IDEA)`
   ——想走备注通道直接编译不过。VM `importBatchMessages` 逐条投 `ComposerStore.Intent.AddMessage`，
   与手工输入/自动捕获**同一条** HER/ME 漏斗、同一存储、同一 `GenerationInput.dialogue` 装配路径；
   顺序=预览条目顺序，前后文保持。预览浮层里没有"补充"出口。
5. **未识别行不静默丢弃**：归不到记录的行并成 `speakerName=null` 条目进预览（可指定或 ✕ 移除）；
   跳过的行（无正文发言人、无处归属时间行）逐条写进 `Result.warnings` 在浮层展示。

## 二、解析规则（`BatchTranscriptParser`，纯 Kotlin 无 Android 依赖）

三种格式（可混排）：
- **块格式**：`昵称` / `时间行` / `内容（可多行）`。发言人行用**一行前瞻**确认——下一非空行是时间行才算发言人；孤零零一行绝不猜成发言人（落为未识别条目）。时间行支持 `yyyy-MM-dd HH:mm(:ss)`、`MM-dd HH:mm`、`昨天/今天/周X/上午/下午/凌晨/中午 + HH:mm`。
- **冒号行**：`昵称：内容`。全角冒号一律拆条；半角冒号须后跟空白（保 `https://…`、`比例3:2`）。
  冒号只在**冒号/编号格式开的条目**里当记录分隔符；块格式正文里"地址：…"（未知昵称）归并进当前条。
- **编号转发**：`1. 昵称` / `1. 昵称 2024-01-01 12:00:23` / `1. 昵称：内容`，名字与时间可同行可分行。

时间串仅预览展示，不入库（入库走提交时刻的 `ChatMessage`）。

## 三、改动文件清单

新增：
- `app/src/main/java/com/lovebrain/app/feature/composer/BatchTranscriptParser.kt`（解析器，纯函数）
- `app/src/main/java/com/lovebrain/app/feature/composer/BatchImportDraft.kt`（提交合同，硬挡 IDEA）
- `app/src/main/java/com/lovebrain/app/ui/panel/reply/BatchImportSheet.kt`（粘贴+预览+指定浮层）
- `app/src/test/java/com/lovebrain/app/feature/composer/BatchTranscriptParserTest.kt`（13 格 JVM 单测：三格式、URL/比例保护、未知行保留、不猜发言人、警告、顺序、BatchImportDraft 硬挡）

修改：
- `ui/panel/reply/ReplyInput.kt`：`onBatchImport` 参数 + `AddMessageButton` 长按（pointerInput+onLongClick 语义）
- `ui/panel/LoveBrainPanelScreen.kt`：`PanelSurfaceHolder.batchImportOpen` 决策面、ReplyInput 接线、浮层挂载
- `viewmodel/LoveBrainViewModel.kt`：`importBatchMessages(drafts)` → 逐条 AddMessage
- `res/values/strings.xml`：`batch_import_*` / `a11y_batch_import*` 共 12 键（显示文案全走资源，字面量预算不涨）

## 四、验证状态

- ✅ `compileDebugKotlin` 通过。
- ✅ 全量 `testDebugUnitTest`：**2790 个用例，仅 `OddShapeOwnershipTest` 2 格红**——那是"异形账本/委托壳清单"两本架构账要求新增自画位点显式登记（`BatchImportSheet.kt#BatchImportSheet/#BatchEntryRow/#RemoveButton` 入异形账、`#SpeakerPickChip` 入委托壳，均带主人与缺件说明）；登记后架构测试全绿，其余 2788 格全部通过。
- ✅ 关键守卫复验通过：`ComposerAddButtonGatingTest`（➕ 门控三格）、`UiStringLiteralBudgetTest`（四栏预算不涨——新文案全走资源）、`PackageDependencyTest`、`PanelWiringViewModelHooksTest`。
- ❌ **真机验证未做**（无设备）。以下清单需真实微信样本逐项确认：
  1. 微信聊天多选→复制（块格式）粘贴→解析条数/顺序/发言人与实际一致；
  2. 合并转发→复制（冒号行格式）同上；多行消息（含冒号行正文）不误拆；
  3. 编号合并转发复制（编号格式）同上；
  4. 未识别行在预览中标出、可指定/可移除；未全指定时导入键禁用；
  5. 同一昵称一次指定全批生效；导入后消息按原顺序出现在列表、生成回复时上下文正确；
  6. 导入内容不出现在"我让军师注意"灰字行（F1/F3 探针）；
  7. 长按 ➕ 开浮层、轻点 ➕ 仍是加单条（不互相干扰）；读屏念出"批量导入聊天记录（长按）"。

## 五、已知取舍

- 入口为长按（可发现性弱于可见按钮）：受行 1 宽度预算与语义树守卫约束，本轮不改输入行布局；如需更显式入口，建议下一轮在设置页或空态区加第二入口。
- 编号格式正文行若以 `数字.` 开头会误拆——预览可移除兜底，未做自动合并。
