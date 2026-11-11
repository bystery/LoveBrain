# R3 — 进行中事项筛选补入冻结军师备注与有效持续意图（外部审计 P1）

**分支** closeout-20261106 @ d9ff3db，未 commit。worktree closeout-r3-tmp → D:/wt-r3，已回写主仓并清理。

## 一、核实结论：审计属实

- `PromptBuilder.kt` 旧 317-318 行：`val (_, directive) = splitMessages(messages)` 从消息正文重新推导 IDEA/军师内容。但生产冻结路径 `GenerationInput.toChatMessages()`（model/GenerationInput.kt:109-120）**只产生 HER/ME，从不产生 Role.IDEA** → `directive` 恒为 null，军师备注从未进入事项筛选。
- `selectOngoingForInjection` 的 `effectiveIntent` 参数已存在但调用点从未传（用默认空 `IntentConfig`）→ `persistentIntentRelevant` 在生产路径上恒 false。
- 判定为真缺陷：`selectOngoingForInjection` / `SelectionContext` / `OngoingContextSelector.computeRelevanceSignals` 三个信号（消息证据 / 备注相关 / 意图相关）机制早已就位，唯独 PromptBuilder 这一处接线断了——上一轮审计指出的正是这处，未修。

## 二、修复（最小范围，仅 PromptBuilder.kt）

1. `buildReplyUserPromptWithRefs`（现 252 行）：把冻结的 `userHint` 与 `intentConfig` 随链下传给 `buildKnowledgeSection`。
2. `buildKnowledgeSection`（现 298-334 行）：新增 `userHint` / `intentConfig` 参数；删除 `splitMessages(messages)` 重推导，改为 `frozenDirective = userHint.trim().takeIf{...}?.let{ ReplyDirective(it) }`，与 `intentConfig` 一并作为一等输入传 `selectOngoingForInjection`。真实 HER/ME 消息仍随 `messages` 作为独立证据信号（F1.1 边界：备注不是消息，不从正文猜 IDEA）。
3. 未动 OngoingContextSelector.kt、buildReflectUserPrompt、champion 包、TopicRecorder、BatchImport/ReplyInput、KnowledgeContextReader、TopicSwitchGate。`buildKnowledgeInsertion` 旧入口与 `buildCoreKnowledgeSubset`（谈心，无冻结备注入参）默认参数下行为不变。

## 三、字节冻结基线影响

零漂移。逐 probe 核对：冻结表中所有 userHint/意图文本均不含计划项关键词（默认 PLAN 只有"看展"），含 IDEA 角色的 probe 其 IDEA 文本也不命中；`reply/plan-injected` 的消息本身已命中"看展"（eligible 集合不变）。实测：`PromptByteFreezeBaselineTest` 8 个测试全绿，105 行冻结读数逐字节未动。

## 四、新增单测（纯 JVM）

`app/src/test/java/com/lovebrain/app/domain/prompt/PromptBuilderOngoingDirectiveIntentTest.kt`（5 个，全绿）：
1. 冻结军师备注 + 有效意图（消息不含事项词）→ 事项进入注入。
2. 无备注无意图的同一对话 → 不注入（负对照，证明注入来自冻结输入而非消息重猜）。
3. 过期意图（`IntentStatus.EXPIRED`，`isIntentEffective` status 防线）→ 不授权注入。
4. 关闭意图（enabled=false）→ 不授权注入。
5. 备注不被当消息：正文在 prompt 中只出现一次（军师备注段，不进 `<chat>` 围栏）；预置 `lastEvidenceTurn=1` 的冷却文件在备注点名注入后保持 1、不被推进到本轮 3。

## 五、验证

- `compileDebugKotlin` 通过；`testDebugUnitTest` 定向跑 OngoingDirectiveIntent(5)/OngoingContextSelector/AdvisorNote/ByteFreezeBaseline 四类全绿（BUILD SUCCESSFUL）。
- 主仓现状：仅 PromptBuilder.kt 修改 + 新测试文件为本轨产物；其余工作区改动（ci.yml、TopicRecorder、TopicSwitchGate 等）属并行轨，未触碰。
