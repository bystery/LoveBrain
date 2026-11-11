# S1 CHAMPION BOUNDARY FIX — 外部审计第二轮 P0 修复（2026-11-06）

产品仓 `D:/LoveBrain`（分支 `closeout-20261106`，HEAD=6ed7fbc）。逐条核实后修复，worktree `closeout-s1-tmp`→`D:/wt-s1`（基于 6ed7fbc）编译验证后逐文件回写主仓，worktree 与临时分支已清理，**未 commit**。

## P0-1：Champion OFF 模式仍写事件历史（核实属实，已修）

**核实**：`viewmodel/LoveBrainViewModel.kt` nextRound 提交链原判据只有 `if (championEngine != null)`，不查有效模式；`domain/champion/ChampionEngine.kt` 的 `commitConfirmedTurn` 也不查模式，直接 `eventStore.append`。默认 OFF（用户没开 Champion）时每轮「下一轮」提交都会写 `champion/events/` 事件文件，违反 OFF 零副作用边界。

**修法（双保险）**：
- `ChampionEngine.commitConfirmedTurn`（D:/LoveBrain/app/src/main/java/com/lovebrain/app/domain/champion/ChampionEngine.kt）：入口先过 `effectiveMode(contactId, story) == OFF` 即返回空——不读事件库、不分配 uid、不建目录，零 IO。当前唯一放行档位 = 已接线的 SHADOW；S2/S3 未接线被 `mode()` 收敛为 OFF；未来真正接线后 `mode()` 自然放行，门禁无需再改。
- `LoveBrainViewModel.kt`（:1505 区域，仅动 champion 提交链区段）：提交点前置同一道 `effectiveMode(kbName, kbName) != OFF` 检查，OFF 时连消息时间戳格式化都不做。

**测试**（`app/src/test/.../viewmodel/LoveBrainViewModelChampionCommitBoundaryTest.kt`，真实 ChampionEngine + 真实 FileChampionEventStorage 落临时目录 + 生产形状依赖替身，走完整 generate → nextRound 生产提交链）：
- OFF（默认）：链路正常完成但 `champion/events` 目录不创建、事件库为空；
- SHADOW（正向对照）：同一链路真实入库 HER 消息（turn=1），回复候选不入库；
- SHADOW + scope 未命中：零 IO。

另补 `ChampionEngineTest` 两例：OFF/脏值/SINGLE_STYLE 提交零 IO 且不建目录；scope 白名单命中/未命中两向。

## P0-2：SHADOW 绕过「仅看本轮」（核实属实，已修）

**核实**：`domain/GenerationEngine.kt` :332 的 SHADOW 块不检查 `input.onlyThisRound`，仅看本轮时正式回复不带旧上下文，影子请求却带画像 seed、意图、已提交历史事件——真实模型请求绕过用户上下文选择。

**修法（最小安全方案）**：SHADOW 块条件加 `&& !input.onlyThisRound`，本轮**完全跳过** SHADOW 额外请求，不留"无历史影子"半套路径；下一轮开关恢复后影子照常。位置：D:/LoveBrain/app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt。

**测试**（`app/src/test/.../domain/GenerationEngineChampionShadowScopeTest.kt`，真实 GenerationEngine + 计数网关 + 真实 ChampionEngine）：
- onlyThisRound=true：网关只见 1 次请求（`user-otr`），`shadow_log.jsonl` 一个字节不写；
- onlyThisRound=false（对照）：主回复后照常追加影子请求（共 2 次），日志落盘，且复钉 R2-1 影子路径不写事件库。

## 验证与硬约束

- `compileDebugKotlin` + 80 个目标测试（champion 全包 + 新增两文件 + LikedSchemeRecordingTest + LoveBrainViewModelR2RegressionTest）+ PackageDependency/SingleOwner/StorageBoundary/AppModuleGraph 合同测试全绿。
- 未触碰 BatchImportSheet/ReplyInput/LoveBrainPanelScreen（S2）、PromptBuilder/CorrectionExtractionFilter（S3）、TopicSwitchGate/TopicRecorder/OngoingContextSelector（S4）。
- 回写文件（6 个）：GenerationEngine.kt、ChampionEngine.kt、LoveBrainViewModel.kt（diff 仅 champion 提交链区段）、ChampionEngineTest.kt、两个新测试文件。主仓另有的 LoveBrainPanelScreen.kt、PanelInputFocus*Test.kt 改动属并行轨，本轨未触碰。
